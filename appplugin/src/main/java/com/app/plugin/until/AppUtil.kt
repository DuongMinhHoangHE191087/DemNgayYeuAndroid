package com.app.plugin.until

import android.content.Context

// getIdentifier is the point here: the plugin reads resources the host app defines by name
// (it cannot see the app's R class), and returns the default when the app has none.
@android.annotation.SuppressLint("DiscouragedApi")
public class AppUtil {
    public companion object {
        /** Giá trị của string `flagTest` bật chế độ test (log, test device, form consent EEA). */
        private val TEST_FLAG_VALUES = setOf("1", "90351")

        public fun isTestFlagOn(context: Context): Boolean =
            getResourceString(context, "flagTest") in TEST_FLAG_VALUES

        public fun getResourceString(context: Context, key: String, vaDef:String = ""): String {
            var re = vaDef
            val resId = context.resources.getIdentifier(key, "string", context.packageName)
            if (resId != 0) {
                re = context.resources.getString(resId)
            }
            return re
        }

        /**
         * Danh sách id thiết bị test trong một string resource, cách nhau bằng dấu phẩy, để máy
         * của người test thứ hai cũng nhận quảng cáo test. Trước 1.3.0 cả chuỗi bị coi là một id,
         * nên máy đó nhận quảng cáo thật từ bản dev (lấy từ Invoice Maker).
         */
        public fun getResourceIds(context: Context, key: String): List<String> =
            splitIds(getResourceString(context, key))

        internal fun splitIds(raw: String): List<String> =
            raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }

        /** 2.5.0 RCSW: khoá RC `ads_cf_overrides` có mặt (allowlist 4 tên, `AdsCfOverrides`) thắng resource app. */
        public fun getResourceInt(context: Context, key: String): Int {
            com.app.plugin.ads.AdsCfOverrides.valueOf(key)?.let { return it }
            var re = 0
            val resId = context.resources.getIdentifier(key, "integer", context.packageName)
            if (resId != 0) {
                re = context.resources.getInteger(resId)
            }
            return re
        }

        public fun extractAdNetwork(source: String): String {
            val adSource = source.lowercase()
            return when {
                "pangle" in adSource -> "pangle"
                "applovin" in adSource || "applovin_exchange" in adSource -> "applovin"
                "chartboost" in adSource -> "chartboost"
                "facebook" in adSource -> "facebook"
                "fyber" in adSource || "dt exchange" in adSource -> "fyber"
                "mytarget" in adSource || "vk ad network" in adSource -> "mytarget"
                "unity" in adSource -> "unity"
                "vungle" in adSource || "liftoff" in adSource -> "vungle"
                "ironsource" in adSource -> "ironsource"
                "mintegral" in adSource -> "mintegral"
                "google ad manager" in adSource -> "gam"
                "admob" in adSource -> "admob"
                "bigo" in adSource || "bıgo" in adSource -> "bigo"
                else -> source
            }
        }
    }
}