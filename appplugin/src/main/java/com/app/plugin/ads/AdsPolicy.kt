package com.app.plugin.ads

import android.content.Context
import com.app.plugin.until.Constants.AdShowFailReason
import com.app.plugin.until.LogUtil
import com.app.plugin.until.PreUtil
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.math.BigDecimal

/**
 * Luật tần suất quảng cáo toàn màn hình của SDK, một chỗ (doc 62 F), cấu hình bằng khoá Remote Config
 * [REMOTE_KEY] = `ads_policy_android` (JSON có `schema_version`). Nối vào `AdsHelper` bằng
 * `AdsHelper.applyPolicy`; policy đang áp là [current].
 *
 * **Mặc định là hành vi 1.3.1**, vì chủ dự án chưa chốt DOC/65 §7 #1, #2 (áp tạm): không chặn phiên đầu, app
 * open ở mọi phiên, khoảng cách interstitial là `fullDeltatime` của `ads_region_android` (30 s khi chưa đặt),
 * cooldown N màn hình là giá trị app đặt bằng `AdsHelper.setPostAdScreenCooldown` (mặc định 0 = tắt), cooldown
 * sau click 5 s, app open quá 4 giờ bị bỏ. Ngoài hành vi 1.3.1 (1.4.0, D5): interstitial, rewarded và native full
 * quá 60 phút bị bỏ. Luật doc 62 F bật bằng Remote Config:
 * `{"schema_version":1,"no_interstitial_first_session":true,"app_open_from_session":2,"interstitial_min_gap_seconds":240}`.
 *
 * Trường `null` là khoá vắng trong JSON: giữ nguồn cũ của luật đó (xem từng trường), không ghi đè.
 *
 * Parse từng trường như `SdkKillSwitch.parse`: trường sai kiểu bị bỏ, trường ngoài khoảng bị kẹp, khoá lạ bị
 * bỏ, mỗi trường hợp một logE; JSON hỏng, không phải object hay `schema_version` khác [SCHEMA_VERSION] thì
 * [DEFAULT] và logE (Điều 7: không bao giờ hỏng câm, và lỗi cấu hình quay về hành vi cũ).
 */
public data class AdsPolicy(
    val schemaVersion: Int = SCHEMA_VERSION,
    /** `true`: phiên đầu (lần mở app thứ nhất, hoặc chưa biết phiên) không có interstitial. */
    val noInterstitialFirstSession: Boolean = false,
    /** App open (trừ `full_splash`) chỉ hiện từ lần mở app thứ N; 1 là mọi phiên. */
    val appOpenFromSession: Int = 1,
    /**
     * Khoảng cách tối thiểu giữa hai interstitial, giây, tính từ lúc interstitial trước hiện hoặc đóng; lượt
     * chưa qua **hơn** N giây bị chặn (`<=`, như `fullDeltatime` cũ). `null`: `fullDeltatime` của
     * `ads_region_android`.
     */
    val interstitialMinGapSeconds: Int? = null,
    /**
     * Số lần chuyển màn hình (`AdsHelper.recordScreenChange`) sau một quảng cáo toàn màn hình trước khi
     * interstitial, app open tự động và banner hiện lại. `null`: giá trị app đặt bằng
     * `AdsHelper.setPostAdScreenCooldown` (mặc định 0 = tắt).
     */
    val screensBeforeInterstitial: Int? = null,
    /** Sau khi người dùng click quảng cáo, Full2 tự động bị chặn trong N giây. `null`: 5 s. */
    val postClickCooldownSeconds: Int? = null,
    /**
     * App open AdMob đã load quá N phút bị bỏ và tải lại ở lần load kế (Google: hết hạn sau 4 giờ). MAX app open miễn:
     * MAX tự hết hạn và nạp lại quảng cáo của nó (Task I1).
     */
    val appOpenMaxAgeMinutes: Int = DEFAULT_APP_OPEN_MAX_AGE_MINUTES,
    /**
     * Interstitial, rewarded và native full (AdMob, Meta; MAX chỉ native full) đã load quá N phút bị bỏ (`ad_expired`)
     * và tải lại ở lần load kế (GMA: quảng cáo hết hạn sau 1 giờ). Khoá `fullscreen_max_age_minutes`, 1–240. MAX
     * interstitial và rewarded miễn: MAX tự hết hạn và nạp lại quảng cáo của nó (review E I1).
     */
    val fullscreenMaxAgeMinutes: Int = DEFAULT_FULLSCREEN_MAX_AGE_MINUTES,
) {
    /** Khoảng cách interstitial có hiệu lực: của policy, không thì [legacyMinGapSeconds] (`fullDeltatime`). */
    public fun minGapSeconds(legacyMinGapSeconds: Int): Int = interstitialMinGapSeconds ?: legacyMinGapSeconds

    /**
     * Lý do chặn một interstitial (hằng `AdShowFailReason`), hoặc `null` nếu luật tần suất cho phép.
     *
     * @param sessionCount lần mở app thứ mấy (`AppPluginManager.getSessionApp`, 0 khi chưa biết).
     * @param secondsSinceLast giây từ lần interstitial trước hiện hoặc đóng (`AdsHelper.fullTimeShow`).
     * @param legacyMinGapSeconds `fullDeltatime` hiện tại, dùng khi policy không đặt khoảng cách.
     */
    public fun interstitialVerdict(sessionCount: Int, secondsSinceLast: Long, legacyMinGapSeconds: Int): String? = when {
        noInterstitialFirstSession && sessionCount <= 1 -> AdShowFailReason.POLICY_FIRST_SESSION
        secondsSinceLast <= minGapSeconds(legacyMinGapSeconds) -> AdShowFailReason.POLICY_MIN_GAP
        else -> null
    }

    /** Lý do chặn một app open theo phiên, hoặc `null`. `appOpenFromSession` 1 không bao giờ chặn. */
    public fun appOpenVerdict(sessionCount: Int): String? =
        if (appOpenFromSession > 1 && sessionCount < appOpenFromSession) AdShowFailReason.POLICY_APP_OPEN_SESSION else null

    public fun postClickCooldownMillis(): Long =
        postClickCooldownSeconds?.let { it * 1_000L } ?: FullScreenAdPolicy.DEFAULT_CLICK_RETURN_COOLDOWN_MILLIS

    /** Kẹp lại cả ở đây: `AdsPolicy` dựng thẳng trong code (không qua [parse]) với 0 phút sẽ bỏ mọi app open. */
    public fun appOpenMaxAgeMillis(): Long = appOpenMaxAgeMinutes.coerceIn(1, DEFAULT_APP_OPEN_MAX_AGE_MINUTES) * 60_000L

    /** Kẹp lại cả ở đây (1–240 phút): `AdsPolicy` dựng thẳng trong code với 0 phút sẽ bỏ mọi quảng cáo toàn màn hình. */
    public fun fullscreenMaxAgeMillis(): Long =
        fullscreenMaxAgeMinutes.coerceIn(1, MAX_FULLSCREEN_MAX_AGE_MINUTES) * 60_000L

    public companion object {
        public const val REMOTE_KEY: String = "ads_policy_android"

        /** Khoá lưu giá trị thô đã áp gần nhất, trong file prefs của `PreUtil`. */
        internal const val PREF_KEY = "mem_ads_policy_android"

        public const val SCHEMA_VERSION: Int = 1

        /** 240 phút, từ [APP_OPEN_MAX_AGE_MS] (Task 11). Cũng là trần: dài hơn chỉ hiện quảng cáo Google không tính. */
        public const val DEFAULT_APP_OPEN_MAX_AGE_MINUTES: Int = (APP_OPEN_MAX_AGE_MS / 60_000L).toInt()

        /**
         * 60 phút: GMA *"Because ads expire after an hour, you should clear this cache and reload with new ads every
         * hour"* (developers.google.com/admob/android/interstitial). Áp cho interstitial, rewarded, native full.
         */
        public const val DEFAULT_FULLSCREEN_MAX_AGE_MINUTES: Int = 60

        /**
         * Trần của `fullscreen_max_age_minutes`, cùng trần với app open (240). Từ 61 phút trở lên đã có thể hiện quảng
         * cáo GMA coi là hết hạn; 61–240 là núm lùi có chủ ý (nới hạn từ xa nếu hạn 60 phút gây hại), không phải giá
         * trị khuyên dùng (review E M7). Không giá trị nào tắt hẳn hạn như 1.3.1.
         */
        private const val MAX_FULLSCREEN_MAX_AGE_MINUTES = DEFAULT_APP_OPEN_MAX_AGE_MINUTES

        public val DEFAULT: AdsPolicy = AdsPolicy()

        /**
         * Policy đang áp, đọc từ mọi luồng. Chỉ ghi qua `AdsHelper.applyPolicy` (main thread), để cooldown sau click
         * và cooldown N màn hình đi cùng; ghi thẳng thì hai luật đó lệch.
         */
        @Volatile
        public var current: AdsPolicy = DEFAULT
            internal set

        private const val KEY_SCHEMA = "schema_version"
        private const val KEY_FIRST_SESSION = "no_interstitial_first_session"
        private const val KEY_APP_OPEN_FROM = "app_open_from_session"
        private const val KEY_MIN_GAP = "interstitial_min_gap_seconds"
        private const val KEY_SCREENS = "screens_before_interstitial"
        private const val KEY_CLICK_COOLDOWN = "post_click_cooldown_seconds"
        private const val KEY_APP_OPEN_MAX_AGE = "app_open_max_age_minutes"
        private const val KEY_FULLSCREEN_MAX_AGE = "fullscreen_max_age_minutes"
        /** 7 trường v1: cũng là tập trường được phép trong một tier của `by_tier` ([AdsPolicyTiers]). */
        internal val FIELD_KEYS = setOf(
            KEY_FIRST_SESSION, KEY_APP_OPEN_FROM, KEY_MIN_GAP, KEY_SCREENS, KEY_CLICK_COOLDOWN,
            KEY_APP_OPEN_MAX_AGE, KEY_FULLSCREEN_MAX_AGE,
        )
        private val KNOWN_KEYS = FIELD_KEYS + setOf(
            KEY_SCHEMA, AdsPolicyTiers.KEY,
            // Đọc riêng bởi GmaLoadContext, AdsCfOverrides (không phải trường của AdsPolicy public), không phải khoá lạ.
            GmaLoadContext.KEY, AdsCfOverrides.KEY,
        )

        public fun parse(json: String?): AdsPolicy {
            if (json.isNullOrBlank()) return DEFAULT
            val o = try {
                JsonParser.parseString(json).asJsonObject
            } catch (e: Exception) {
                LogUtil.logE("ads policy: $REMOTE_KEY hỏng, dùng mặc định (hành vi cũ): ${e.message}")
                return DEFAULT
            }
            val schema = o.get(KEY_SCHEMA)
            if (schema != null && !isSupportedSchema(schema)) {
                LogUtil.logE("ads policy: $REMOTE_KEY schema_version $schema không hỗ trợ (chỉ $SCHEMA_VERSION), dùng mặc định")
                return DEFAULT
            }
            o.keySet().filterNot { it in KNOWN_KEYS }.forEach {
                LogUtil.logE("ads policy: $REMOTE_KEY có khoá lạ '$it', bỏ qua")
            }
            return AdsPolicyTiers.resolve(o, fieldsOf(o, DEFAULT))
        }

        /** 7 trường v1 của [o] đè lên [base]; trường vắng hay sai giữ giá trị của [base]. Dùng cho top-level và từng tier. */
        internal fun fieldsOf(o: JsonObject, base: AdsPolicy): AdsPolicy = base.copy(
            noInterstitialFirstSession = flag(o, KEY_FIRST_SESSION) ?: base.noInterstitialFirstSession,
            appOpenFromSession = intField(o, KEY_APP_OPEN_FROM, 1..100) ?: base.appOpenFromSession,
            interstitialMinGapSeconds = intField(o, KEY_MIN_GAP, 0..3600) ?: base.interstitialMinGapSeconds,
            screensBeforeInterstitial = intField(o, KEY_SCREENS, 0..50) ?: base.screensBeforeInterstitial,
            postClickCooldownSeconds = intField(o, KEY_CLICK_COOLDOWN, 0..60) ?: base.postClickCooldownSeconds,
            appOpenMaxAgeMinutes = intField(o, KEY_APP_OPEN_MAX_AGE, 1..DEFAULT_APP_OPEN_MAX_AGE_MINUTES)
                ?: base.appOpenMaxAgeMinutes,
            fullscreenMaxAgeMinutes = intField(o, KEY_FULLSCREEN_MAX_AGE, 1..MAX_FULLSCREEN_MAX_AGE_MINUTES)
                ?: base.fullscreenMaxAgeMinutes,
        )

        /**
         * Số nguyên (JSON number, hoặc chuỗi số như Remote Config hay ghi; `60.0` được, `60.5` thì không), kẹp
         * vào [range]. Sai kiểu thì `null` (trường bị bỏ); ngoài khoảng thì kẹp; mỗi trường hợp một logE.
         */
        private fun intField(o: JsonObject, name: String, range: IntRange): Int? {
            val e = o.get(name) ?: return null
            val n = wholeNumber(e)
            if (n == null) {
                LogUtil.logE("ads policy: '$name' = $e không phải số nguyên, bỏ qua trường này")
                return null
            }
            val v = n.coerceIn(range.first.toLong(), range.last.toLong()).toInt()
            if (v.toLong() != n) {
                LogUtil.logE("ads policy: '$name' = $n ngoài [${range.first}, ${range.last}], dùng $v")
            }
            return v
        }

        private fun wholeNumber(e: JsonElement): Long? {
            if (!e.isJsonPrimitive || e.asJsonPrimitive.isBoolean) return null
            return try {
                BigDecimal(e.asString.trim()).longValueExact()
            } catch (ex: ArithmeticException) {
                null
            } catch (ex: NumberFormatException) {
                null
            }
        }

        /**
         * `true` nếu [e] là số nguyên (JSON number hay chuỗi số, `1.0` được) bằng [SCHEMA_VERSION]. Dùng chung bởi
         * [parse] và `GmaLoadContext.parse`, để hai chỗ không lệch nhau nếu [SCHEMA_VERSION] hay tập giá trị chấp
         * nhận đổi (Task N review M1).
         */
        internal fun isSupportedSchema(e: JsonElement): Boolean = wholeNumber(e) == SCHEMA_VERSION.toLong()

        /** `true`/`false` (JSON boolean, hoặc chuỗi "true"/"false"). Giá trị khác bị bỏ, logE, và là `null`. */
        private fun flag(o: JsonObject, name: String): Boolean? {
            val e = o.get(name) ?: return null
            if (e.isJsonPrimitive) {
                val p = e.asJsonPrimitive
                if (p.isBoolean) return p.asBoolean
                if (p.isString && (p.asString.equals("true", true) || p.asString.equals("false", true))) {
                    return p.asString.equals("true", true)
                }
            }
            LogUtil.logE("ads policy: '$name' = $e không phải true/false, bỏ qua trường này")
            return null
        }

        /**
         * Lúc mở app (`AdsHelper.initAdsHelper`), trước khi Remote Config về: bản thô đã lưu ở lần áp gần nhất,
         * đã parse. File prefs đã được `AppPluginManager.initPlugin` nạp từ trước, nên đây là đọc bộ nhớ. Đọc lỗi
         * thì [DEFAULT] (hành vi cũ) và logE.
         */
        internal fun restore(context: Context): AdsPolicy = parse(restoreRaw(context))

        /**
         * Bản thô đã lưu (chuỗi rỗng khi chưa có hoặc đọc lỗi, kèm logE), để `AdsHelper.restorePolicy` đưa cùng một
         * chuỗi cho [parse] và `GmaLoadContext.apply` mà không đọc prefs hai lần.
         */
        internal fun restoreRaw(context: Context): String = try {
            PreUtil.getString(context, PREF_KEY, "")
        } catch (e: Exception) {
            LogUtil.logE("ads policy: không đọc được $REMOTE_KEY đã lưu, dùng mặc định: ${e.message}")
            ""
        }.also { AdsPolicyTiers.remember(it) }

        /** Lưu bản thô [raw] vừa áp cho lần mở kế. Chuỗi rỗng (khoá bị gỡ) cũng lưu. Lỗi thì logE, không ném. */
        internal fun save(context: Context?, raw: String) {
            AdsPolicyTiers.remember(raw)
            if (context == null) {
                LogUtil.logE("ads policy: chưa có context, không lưu được $REMOTE_KEY cho lần mở kế")
                return
            }
            try {
                PreUtil.setString(context, PREF_KEY, raw)
            } catch (e: Exception) {
                LogUtil.logE("ads policy: không lưu được $REMOTE_KEY cho lần mở kế: ${e.message}")
            }
        }
    }
}
