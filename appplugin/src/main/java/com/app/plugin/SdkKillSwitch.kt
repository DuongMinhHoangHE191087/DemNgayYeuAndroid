package com.app.plugin

import android.content.Context
import com.app.plugin.until.LogUtil
import com.app.plugin.until.PreUtil
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * Kill switch của ops (doc 60 G, gate G7), từ Remote Config [REMOTE_KEY]:
 * `{"ads":true,"iap":false,"logevent":false,"below_version":"1.4.1"}`.
 *
 * - `ads`: không khởi tạo mạng quảng cáo (`AdSdkStartup`), không load, không show (`AdsHelper.isDisableAds`).
 * - `iap`: không kết nối Play Billing (`IapHelper.init`); quyền lợi đã lưu giữ nguyên.
 * - `logevent`: không gửi event lên server của SDK (`LogEventServer`); decision log giữ bản ghi trên đĩa.
 * - `below_version`: bản SDK nhỏ hơn ngưỡng này tắt cả ba. Ngưỡng phải là số chấm số (`1.4.1`); sai dạng
 *   thì bỏ và logE. Bản pre-release (`1.4.1-rc1`) đứng dưới bản chính cùng số.
 *
 * Chỉ tắt, không bao giờ cấp gì (Điều 13): JSON hỏng, khoá vắng hay lỗi đọc đều là [NONE].
 *
 * Độ trễ: giá trị fetch được áp ngay khi Remote Config về (chặn load/show từ lúc đó) và được lưu; lúc mở
 * app, [restore] áp bản đã lưu trước khi mạng quảng cáo và billing khởi tạo. Nên tắt khởi tạo mạng và
 * billing có hiệu lực từ **lần mở kế tiếp** sau khi máy nhận được khoá; gỡ khoá cũng vậy (phiên đã bị tắt
 * khởi tạo mạng không khởi tạo lại giữa chừng, và quảng cáo của nó vẫn tắt tới lần mở kế, xem
 * `AdSdkStartup.adNetworksKilled`).
 *
 * Phiên bản so sánh luôn là `BuildConfig.SDK_VERSION` (`MonetizationSdk.VERSION` chỉ là alias của nó).
 */
internal data class SdkKillSwitch(
    val ads: Boolean = false,
    val iap: Boolean = false,
    val logevent: Boolean = false,
    val belowVersion: String = ""
) {
    private fun versionKilled(v: String) = belowVersion.isNotBlank() && isBelow(v, belowVersion)
    fun killsAds(v: String) = ads || versionKilled(v)
    fun killsIap(v: String) = iap || versionKilled(v)
    fun killsLogevent(v: String) = logevent || versionKilled(v)

    /**
     * Giá trị param `kill` của `sdk_health`: `none`, `version`, hoặc các module bị tắt theo thứ tự `ads,iap,logevent`
     * nối bằng dấu phẩy (ví dụ `ads,iap`). Dạng này là hợp đồng với dashboard, khoá ở `SdkHealthTest`.
     */
    fun label(v: String): String {
        if (versionKilled(v)) return "version"
        val modules = listOfNotNull("ads".takeIf { ads }, "iap".takeIf { iap }, "logevent".takeIf { logevent })
        return if (modules.isEmpty()) "none" else modules.joinToString(",")
    }

    companion object {
        const val REMOTE_KEY = "sdk_kill_android"

        /** Khoá lưu giá trị thô đã áp gần nhất, trong file prefs của `PreUtil`. */
        const val PREF_KEY = "mem_sdk_kill_android"

        val NONE = SdkKillSwitch()

        /** Ngưỡng `below_version` hợp lệ, và phần số của phiên bản SDK. */
        private val NUMERIC_VERSION = Regex("""\d+(\.\d+)*""")

        @Volatile
        var current: SdkKillSwitch = NONE

        /**
         * Đọc từng trường riêng: một trường sai kiểu chỉ bỏ trường đó (logE), không làm mất các trường hợp lệ —
         * `below_version: null` không được làm rơi một `ads: true` đúng lúc ops cần tắt khẩn. JSON hỏng hay
         * không phải object thì [NONE].
         */
        fun parse(json: String?): SdkKillSwitch {
            if (json.isNullOrBlank()) return NONE
            val o = try {
                JsonParser.parseString(json).asJsonObject
            } catch (e: Exception) {
                LogUtil.logE("kill switch: $REMOTE_KEY hỏng, bỏ qua: ${e.message}")
                return NONE
            }
            return SdkKillSwitch(
                ads = flag(o, "ads"),
                iap = flag(o, "iap"),
                logevent = flag(o, "logevent"),
                belowVersion = validFloor(floorText(o))
            )
        }

        /**
         * `true`/`false` (JSON boolean, hoặc chuỗi "true"/"false" như Gson vẫn đọc). Vắng là `false`; giá trị
         * khác (`1`, `"yes"`, `null`, object…) bị bỏ, logE, và là `false`: chỉ tắt khi ops ghi rõ `true`.
         */
        private fun flag(o: JsonObject, name: String): Boolean {
            val e = o.get(name) ?: return false
            if (e.isJsonPrimitive) {
                val p = e.asJsonPrimitive
                if (p.isBoolean) return p.asBoolean
                if (p.isString && (p.asString.equals("true", true) || p.asString.equals("false", true))) {
                    return p.asString.equals("true", true)
                }
            }
            LogUtil.logE("kill switch: '$name' = $e không phải true/false, bỏ qua trường này")
            return false
        }

        /** Chữ của `below_version` (chuỗi, hoặc số giữ nguyên chữ); kiểu khác bị bỏ, logE. */
        private fun floorText(o: JsonObject): String {
            val e = o.get("below_version") ?: return ""
            if (e.isJsonPrimitive && !e.asJsonPrimitive.isBoolean) return e.asString.trim()
            LogUtil.logE("kill switch: 'below_version' = $e không phải chuỗi phiên bản, bỏ qua trường này")
            return ""
        }

        private fun validFloor(floor: String): String {
            if (floor.isEmpty() || NUMERIC_VERSION.matches(floor)) return floor
            LogUtil.logE("kill switch: below_version '$floor' không phải phiên bản số (vd 1.4.1), bỏ qua")
            return ""
        }

        /**
         * [version] (phiên bản SDK) nhỏ hơn [floor] (chỉ số chấm số). Của [version] chỉ so phần số trước `-` hay
         * khoảng trắng; bằng nhau mà có hậu tố `-…` (pre-release) thì coi là nhỏ hơn. Không đọc được là `false`.
         */
        fun isBelow(version: String, floor: String): Boolean {
            val b = numericParts(floor) ?: return false
            val v = version.trim()
            val core = v.takeWhile { it != '-' && !it.isWhitespace() }
            val a = numericParts(core) ?: return false
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
                if (x != y) return x < y
            }
            return v.length > core.length && v[core.length] == '-'
        }

        private fun numericParts(s: String): List<Int>? {
            if (!NUMERIC_VERSION.matches(s)) return null
            return s.split('.').map { it.toIntOrNull() ?: return null }
        }

        /**
         * Lúc mở app (`AppPluginManager.initPlugin`), trước khi mạng quảng cáo và billing khởi tạo: áp bản
         * đã lưu ở lần áp Remote Config gần nhất. File prefs đã được `initPlugin` nạp từ trước, nên đây là
         * đọc bộ nhớ, không đọc đĩa trên main thread. Lỗi thì không tắt gì và log (Điều 7, 13).
         */
        fun restore(context: Context) {
            val raw = try {
                PreUtil.getString(context, PREF_KEY, "")
            } catch (e: Exception) {
                LogUtil.logE("kill switch: không đọc được bản đã lưu, không tắt gì: ${e.message}")
                current = NONE
                return
            }
            install(raw, "bản lưu của phiên trước")
        }

        /**
         * Giá trị [raw] của [REMOTE_KEY] vừa fetch (`FirHelperConfig.applyRemoteCf`): áp cho phiên này và lưu
         * cho lần mở kế tiếp. Khoá vắng (chuỗi rỗng) là gỡ kill. Lưu lỗi thì vẫn áp, và log.
         */
        fun applyRemote(context: Context?, raw: String) {
            val wasKilled = current.killsLogevent(BuildConfig.SDK_VERSION)
            install(raw, "Remote Config")
            // 1.7.0 P-1: gỡ kill `logevent` giữa phiên → bản bền (DurableEvents) bị chặn lúc gửi replay ngay.
            if (wasKilled && !current.killsLogevent(BuildConfig.SDK_VERSION)) com.app.plugin.logevent.DurableEvents.onTransportReady()
            if (context == null) {
                LogUtil.logE("kill switch: chưa có context, không lưu được $REMOTE_KEY cho lần mở kế")
                return
            }
            try {
                PreUtil.setString(context, PREF_KEY, raw)
            } catch (e: Exception) {
                LogUtil.logE("kill switch: không lưu được $REMOTE_KEY cho lần mở kế: ${e.message}")
            }
        }

        private fun install(raw: String, source: String) {
            val previous = current
            val next = parse(raw)
            current = next
            val version = BuildConfig.SDK_VERSION
            val label = next.label(version)
            when {
                label != "none" -> LogUtil.logE("kill switch: $REMOTE_KEY từ $source = $raw → tắt $label (SDK $version)")
                next != NONE -> LogUtil.logD("kill switch: $REMOTE_KEY từ $source = $raw không tắt gì của SDK $version")
                previous.label(version) != "none" ->
                    LogUtil.logE("kill switch: gỡ ($source); khởi tạo mạng và billing trở lại từ lần mở kế")
            }
        }
    }
}
