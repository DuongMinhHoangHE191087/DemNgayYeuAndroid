package com.app.plugin.ads.core

import android.content.Context
import android.os.Looper
import android.util.Log
import com.app.plugin.until.LogUtil
import com.app.plugin.until.PreUtil
import com.google.gson.JsonParser
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import kotlin.concurrent.thread

/**
 * Registry bidding partner (1.9.0 Task 1, Hợp đồng A; D1, D3 option A, D19, D23).
 *
 * Client không loại được adapter đã có trong bản dựng khỏi phiên đấu giá (GMA khởi tạo mọi adapter trên classpath):
 * registry chỉ là **hiện diện lúc build + nhãn**. Tập đóng [ADAPTERS]; `meta` mặc định. Nguồn:
 * - resource app `ads_bidding_partners` (string-array): tên lạ, trùng, rỗng (hoặc mảng rỗng) → bỏ **cả** resource → `{meta}`,
 *   một logE (D23);
 * - Remote Config [REMOTE_KEY] = `{"schema_version":1,"partners":[…]}`: object, `schema_version` 1, `partners` mảng
 *   chuỗi không rỗng ⊆ tập, không trùng, không khoá khác; sai bất kỳ → bỏ cả giá trị, giữ resource, một logE
 *   (lý do giữ tên sai đầu tiên và số lượng, cả dòng ≤ 512 ký tự — 2.1.0 P2).
 *   Hiệu lực = RC ∩ resource (RC chỉ thu hẹp). [PREF_KEY] lưu bản thô (`SharedPreferences.apply()`, như
 *   `AdsPolicy.save`) khi RC hợp lệ, **`""` khi RC bị từ chối** (R-T1 m6) và khi khoá bị gỡ.
 *
 * Bước khởi động ([startCheck], trong task "ad networks" của `AdSdkStartup`, trước `starters.admob`, ngoài main):
 * `Class.forName(<adapter>, false, cl)` **một lần cho cả tập đóng** [ADAPTERS], đúng một dòng
 * `ads adapters on classpath: <p>=present|absent …`; rồi mỗi partner bật: có → `ads bidding: <p> present`, vắng →
 * `ads bidding: <p> enabled but adapter absent, skipped` (một lần mỗi process). Không tham chiếu kiểu partner nào (D3 A).
 */
internal object BiddingPartners {
    const val REMOTE_KEY = "ads_bidding_partners_android"
    internal const val PREF_KEY = "mem_ads_bidding_partners_android"
    private const val KEY_SCHEMA = "schema_version"
    private const val KEY_PARTNERS = "partners"
    private const val MAX_REASON = 200   // logE ≤ 512 ký tự dù mảng partners dài tuỳ ý (R-T1 m5)
    private val DEFAULT = setOf("meta")

    /** Tập đóng → lớp adapter GMA (đo trên AAR). Thứ tự = thứ tự log và `partners=` của `sdk_health`. */
    val ADAPTERS: Map<String, String> = linkedMapOf(
        "meta" to "com.google.ads.mediation.facebook.FacebookMediationAdapter",
        "pangle" to "com.google.ads.mediation.pangle.PangleMediationAdapter",
        "mintegral" to "com.google.ads.mediation.mintegral.MintegralMediationAdapter",
        "unity" to "com.google.ads.mediation.unity.UnityMediationAdapter",
        "vungle" to "com.google.ads.mediation.vungle.VungleMediationAdapter",
        "applovin" to "com.google.ads.mediation.applovin.AppLovinMediationAdapter",
    )

    /** RC đã áp trong phiên (`null` = chưa áp, đọc bản lưu); [Remote.partners] `null` = không thu hẹp. */
    private class Remote(val partners: Set<String>?)

    @Volatile
    private var remote: Remote? = null

    /** Kết quả presence, một lần mỗi process (log vắng đúng một dòng). */
    @Volatile
    private var present: List<String>? = null

    /** Luồng nền (một lần mỗi process) của [startCheck] khi được gọi trên main. */
    private val background = Executor { r -> thread(name = "appplugin-bidding", isDaemon = true) { r.run() } }

    /** Resource [names] (`null` = app không khai) → tập partner; sai → `{meta}` + một logE. */
    fun parseResource(names: Array<String>?): Set<String> {
        if (names == null) return DEFAULT
        val reason = when {
            names.isEmpty() -> "empty"
            names.any { it !in ADAPTERS } -> names.filter { it !in ADAPTERS }.let { "unknown name '${it.first()}' (${it.size} unknown of ${names.size})" }
            names.toSet().size != names.size -> "duplicate name '${firstDuplicate(names.asList())}'"
            else -> return names.toSet()
        }
        LogUtil.logE("ads bidding: resource ads_bidding_partners ignored (${reason.take(MAX_REASON)}), using meta")
        return DEFAULT
    }

    /** Giá trị RC thô → tập thu hẹp; `null` khi rỗng (không thu hẹp) hoặc bị bỏ (một logE). */
    fun parseRemote(raw: String?): Set<String>? {
        if (raw.isNullOrBlank()) return null
        return try {
            remotePartners(raw)
        } catch (e: Exception) {
            LogUtil.logE("ads bidding: $REMOTE_KEY rejected (${e.message?.take(MAX_REASON)}), keeping resource")
            null
        }
    }

    /** Tên đầu tiên xuất hiện lần thứ hai (chỉ gọi khi có trùng). */
    private fun firstDuplicate(names: List<String?>): String? = names.groupBy { it }.entries.first { it.value.size > 1 }.key

    /** Dạng cố định của RC; sai bất kỳ → ném, lý do ở message. */
    private fun remotePartners(raw: String): Set<String> {
        val element = JsonParser.parseString(raw)
        require(element.isJsonObject) { "not an object" }
        val o = element.asJsonObject
        o.keySet().firstOrNull { it != KEY_SCHEMA && it != KEY_PARTNERS }?.let { throw IllegalArgumentException("unknown key '$it'") }
        val schema = o.get(KEY_SCHEMA)
        require(schema != null && schema.isJsonPrimitive && schema.asJsonPrimitive.isNumber && schema.asString == "1") {
            "schema_version $schema, only 1"
        }
        val partners = o.get(KEY_PARTNERS)
        require(partners != null && partners.isJsonArray && partners.asJsonArray.size() > 0) { "partners not a non-empty array" }
        val names = partners.asJsonArray.map { if (it.isJsonPrimitive && it.asJsonPrimitive.isString) it.asString else null }
        val unknown = names.filter { it == null || it !in ADAPTERS }
        require(unknown.isEmpty()) { "unknown partner '${unknown.first()}' (${unknown.size} unknown of ${names.size})" }
        require(names.toSet().size == names.size) { "duplicate partner '${firstDuplicate(names)}'" }
        return names.filterNotNull().toSet()
    }

    /** Hiệu lực: RC chỉ thu hẹp resource (D1). */
    fun effective(resource: Set<String>, remote: Set<String>?): Set<String> =
        if (remote == null) resource else resource.filter { it in remote }.toSet()

    /** Áp RC (`FirHelperConfig`, luồng config): kiểm, giữ trong bộ nhớ, lưu cho lần mở kế; bản bị từ chối lưu `""` (R-T1 m6). Lỗi: logE, không ném. */
    fun applyRemote(context: Context?, raw: String) {
        val partners = parseRemote(raw)
        remote = Remote(partners)
        val toSave = if (partners == null && raw.isNotBlank()) "" else raw
        if (context == null) {
            LogUtil.logE("ads bidding: no context, $REMOTE_KEY not saved for next start")
            return
        }
        try {
            PreUtil.setString(context, PREF_KEY, toSave)
        } catch (e: Exception) {
            LogUtil.logE("ads bidding: cannot save $REMOTE_KEY: ${e.message}")
        }
    }

    /** RC của phiên này, không thì bản đã lưu ở lần áp gần nhất (đã kiểm lại). */
    internal fun currentRemote(context: Context): Set<String>? {
        remote?.let { return it.partners }
        val raw = try {
            PreUtil.getString(context, PREF_KEY, "")
        } catch (e: Exception) {
            LogUtil.logE("ads bidding: cannot read saved $REMOTE_KEY: ${e.message}")
            ""
        }
        return parseRemote(raw).also { remote = Remote(it) }
    }

    private fun readResource(context: Context): Array<String>? = try {
        val res = context.resources
        val id = res.getIdentifier("ads_bidding_partners", "array", context.packageName)
        if (id == 0) null else res.getStringArray(id)
    } catch (e: Exception) {
        LogUtil.logD("ads bidding: resource ads_bidding_partners not readable: ${e.message}")
        null
    }

    /** Lớp adapter [name] có trong bản dựng; `initialize=false` để không chạy khởi tạo tĩnh của adapter (R-T1 m1). */
    internal fun adapterPresent(name: String, loader: ClassLoader?): Boolean = try {
        Class.forName(name, false, loader)
        true
    } catch (e: ClassNotFoundException) {
        false
    } catch (e: LinkageError) {
        false
    }

    /**
     * Partner bật và có adapter trong bản dựng; một lần mỗi process. Vắng → đúng một dòng log; có → `<p> present`.
     */
    internal fun check(context: Context): List<String> {
        present?.let { return it }
        // Đọc resource/prefs ngoài khoá (#31); khoá chỉ bọc dò lớp + log, để hai lời gọi không log hai lần.
        val enabled = effective(parseResource(readResource(context)), currentRemote(context))
        synchronized(this) {
            present?.let { return it }
            val loader = BiddingPartners::class.java.classLoader
            val onClasspath = ADAPTERS.mapValues { adapterPresent(it.value, loader) }
            // Dòng presence cả tập đóng, kể cả partner không bật: thấy adapter trên classpath mà registry không xét (D50).
            Log.d(LogUtil.TAG, "ads adapters on classpath: " + onClasspath.entries.joinToString(" ") { "${it.key}=${if (it.value) "present" else "absent"}" })
            val found = ADAPTERS.keys.filter { it in enabled }.filter { p ->
                val ok = onClasspath.getValue(p)
                if (ok) Log.d(LogUtil.TAG, "ads bidding: $p present")
                else LogUtil.logE("ads bidding: $p enabled but adapter absent, skipped")
                ok
            }
            return found.also { present = it }
        }
    }

    /**
     * Chạy [check] ngoài main: trên main thì chuyển sang [background], luồng khác thì chạy tại chỗ. Không bao giờ
     * hỏng: lỗi → danh sách rỗng + logE.
     */
    fun startCheck(context: Context): CompletableFuture<List<String>> {
        val app = context.applicationContext ?: context
        val task = {
            try {
                check(app)
            } catch (e: Exception) {
                LogUtil.logE("ads bidding: presence check failed: ${e.message}")
                emptyList()
            }
        }
        if (Looper.getMainLooper()?.isCurrentThread != true) return CompletableFuture.completedFuture(task())
        return CompletableFuture.supplyAsync(task, background)
    }

    @JvmSynthetic
    internal fun resetForTest() {
        remote = null
        present = null
    }
}
