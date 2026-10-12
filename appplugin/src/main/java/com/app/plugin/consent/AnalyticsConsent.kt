package com.app.plugin.consent

import android.content.Context
import android.util.Log
import com.app.plugin.until.LogUtil
import com.app.plugin.until.PreferenceHelper
import com.google.firebase.analytics.FirebaseAnalytics
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Consent Mode v2 cho Firebase Analytics, theo tầng consent (Điều 13.3, doc 60 E).
 *
 * Manifest của SDK đặt mặc định cả bốn loại là denied; [apply] đặt giá trị thật mỗi lần tầng đổi (listener
 * đăng ký từ `AdSdkStartup.requestAnalytics`, tức từ `AppPluginManager.initPlugin`, nên theo mọi
 * `ConsentState.setTier`, kể cả khi app tự gọi, cả trước khi cổng mở) và thêm một lần khi cổng `analytics`
 * mở (sau khi UMP trả lời). Firebase giữ giá trị này qua các phiên.
 *
 * **Ai sở hữu thu thập** (`setAnalyticsCollectionEnabled`): SDK chỉ TẮT khi tầng từ chối (xem
 * [DISABLE_COLLECTION_WHEN_DENIED]) và ghi cờ [KEY_COLLECTION_DISABLED_BY_SDK] vào `PreferenceHelper`; SDK chỉ
 * BẬT LẠI khi cờ đó còn, tức chính nó đã tắt. App tự tắt thu thập (công tắc "chia sẻ dữ liệu sử dụng")
 * thì SDK không bật lại ở các lần mở app sau.
 *
 * **Đây là consumer đầu tiên siết `UNKNOWN`** ([ConsentTier.UNKNOWN] không bao giờ là đồng ý): trong
 * vùng GDPR, hoặc khi không biết GDPR có áp dụng không, `UNKNOWN` từ chối cả bốn loại như `DENIED`.
 * Chỉ `IABTCF_gdprApplies = 0` (tức `gdprApplies == false`) mới cấp hết mà không xét tầng, trừ
 * `DENIED`: lời từ chối thật luôn thắng, vì `0` có thể là giá trị cũ còn lại khi người dùng đã vào EEA.
 */
internal object AnalyticsConsent {

    /**
     * Người dùng trong vùng GDPR từ chối (hoặc chưa có câu trả lời dùng được) thì tắt hẳn thu thập:
     * `setAnalyticsCollectionEnabled(false)`, không gửi gì lên Firebase.
     *
     * Đánh đổi: mất `ad_impression`, `sdk_health` và mọi sự kiện của người EEA từ chối — số liệu
     * doanh thu quảng cáo trên Firebase thiếu đúng phần đó.
     *
     * Đổi thành `false` thì chỉ còn Consent Mode denied, SDK không tắt thu thập: Firebase gửi ping không
     * định danh (không cookie, không advertising id) và Google mô hình hoá phần thiếu. Chủ dự án chưa
     * chốt: đây là quyết định B#6 trong addendum của Task 9 (`task-9-addendum.md`), theo dõi ở DOC/65 §7 #10
     * (không phải §7 #6 — mục đó là chuỗi Full2). Giữ theo plan; đổi là sửa đúng một dòng này.
     */
    internal const val DISABLE_COLLECTION_WHEN_DENIED = true

    /** Khoá `PreferenceHelper`: có (giá trị `true`) khi chính SDK đã tắt thu thập và chưa bật lại. */
    internal const val KEY_COLLECTION_DISABLED_BY_SDK = "analytics_collection_disabled_by_sdk"

    /**
     * Bản trong bộ nhớ của cờ: đủ cho process hiện tại khi `PreferenceHelper` chưa init (app không kế thừa
     * `AppPluginBase` và không tự init nó). Khi đó cờ không sống qua process.
     */
    @Volatile
    private var disabledBySdkInProcess = false

    /** Dòng "PreferenceHelper not initialized" chỉ ghi một lần mỗi process. */
    private val prefsMissingLogged = AtomicBoolean(false)

    data class Settings(
        val analyticsStorage: Boolean,
        val adStorage: Boolean,
        val adUserData: Boolean,
        val adPersonalization: Boolean
    )

    private val GRANT_ALL = Settings(true, true, true, true)
    private val PARTIAL = Settings(true, true, false, false)
    private val DENY_ALL = Settings(false, false, false, false)

    /**
     * @param gdprApplies `TcfConsent.gdprApplies`: `null` (khoá vắng hoặc đọc lỗi) xử lý như `true`,
     *   vì "không biết" không bao giờ là lý do cấp quyền (Điều 13). `false` cấp hết, trừ khi tầng là
     *   `DENIED`.
     */
    fun settingsFor(tier: ConsentTier, gdprApplies: Boolean?): Settings = settingsFor(tier, gdprApplies, usOptOut = false)

    /**
     * Như [settingsFor] hai tham số, cộng opt-out theo luật bang Mỹ (GPP, [ConsentRules.usOptOut]): [usOptOut] thì
     * `ad_user_data`/`ad_personalization` denied, `analytics_storage`/`ad_storage` giữ theo tầng. Tầng không đổi, và
     * [usOptOut] không bao giờ cấp thêm gì (1.6.0 Task 8a, O2).
     */
    internal fun settingsFor(tier: ConsentTier, gdprApplies: Boolean?, usOptOut: Boolean): Settings {
        val byTier = when {
            ConsentRules.outsideGdprGrantsAll(tier, gdprApplies) -> GRANT_ALL
            tier == ConsentTier.GRANTED -> GRANT_ALL
            tier == ConsentTier.PARTIAL -> PARTIAL
            else -> DENY_ALL
        }
        return if (usOptOut) byTier.copy(adUserData = false, adPersonalization = false) else byTier
    }

    /**
     * Đặt theo tầng hiện tại, `IABTCF_gdprApplies` và GPP (`IABGPP_*`, [GppConsent]). Gọi mỗi khi tầng đổi. Lỗi thì
     * log, không ném.
     */
    fun apply(context: Context) = apply(context, GppConsent.read(context))

    /** Như [apply] một tham số, với GPP đã đọc sẵn ([gpp], `null` = không có GPP): `AdSdkStartup.applyTier` đọc một lần. */
    internal fun apply(context: Context, gpp: GppSignals?) {
        try {
            val s = settingsFor(ConsentState.tier(), TcfConsent.gdprApplies(context), ConsentRules.usOptOut(gpp))
            val collection = applySettings(FirebaseAnalytics.getInstance(context), s, DISABLE_COLLECTION_WHEN_DENIED)
            // Log thẳng, không qua cờ LogUtil: lần mở app có câu trả lời cũ chạy trong
            // Application.onCreate, trước khi cờ log được bật; cần thấy dòng này khi kiểm trên máy.
            Log.d(LogUtil.TAG, "analytics consent applied: $s, collection=$collection ${ConsentRules.gppLabel(gpp)}")
        } catch (e: Exception) {
            LogUtil.logE("analytics consent apply failed: ${e.message}")
        }
    }

    /**
     * Consent trước, rồi mới bật/tắt thu thập: lần bật lại đã mang consent mới.
     *
     * Tắt: ghi cờ TRƯỚC rồi mới tắt — process chết giữa hai bước thì lần sau vẫn bật lại được. Bật lại: bật
     * rồi mới xoá cờ — lỗi giữa chừng thì lần sau thử bật lại lần nữa.
     *
     * @return việc đã làm với thu thập, cho dòng log: `disabled`, `re-enabled` hoặc `app-owned` (không đụng).
     */
    internal fun applySettings(fa: FirebaseAnalytics, s: Settings, disableWhenDenied: Boolean): String {
        fa.setConsent(
            mapOf(
                FirebaseAnalytics.ConsentType.ANALYTICS_STORAGE to status(s.analyticsStorage),
                FirebaseAnalytics.ConsentType.AD_STORAGE to status(s.adStorage),
                FirebaseAnalytics.ConsentType.AD_USER_DATA to status(s.adUserData),
                FirebaseAnalytics.ConsentType.AD_PERSONALIZATION to status(s.adPersonalization)
            )
        )
        return when {
            disableWhenDenied && !s.analyticsStorage -> {
                markDisabledBySdk(true)
                fa.setAnalyticsCollectionEnabled(false)
                "disabled"
            }
            disabledBySdk() -> {
                fa.setAnalyticsCollectionEnabled(true)
                markDisabledBySdk(false)
                "re-enabled"
            }
            else -> "app-owned"   // SDK không tắt thì không bật: công tắc của app (nếu có) giữ nguyên
        }
    }

    private fun disabledBySdk(): Boolean =
        disabledBySdkInProcess || PreferenceHelper.getInstance().get(KEY_COLLECTION_DISABLED_BY_SDK, false)

    private fun markDisabledBySdk(disabled: Boolean) {
        disabledBySdkInProcess = disabled
        val prefs = PreferenceHelper.getInstance()
        if (prefs.preferences == null) {
            if (prefsMissingLogged.compareAndSet(false, true)) {
                LogUtil.logE(
                    "analytics collection: PreferenceHelper not initialized, " +
                        "'$KEY_COLLECTION_DISABLED_BY_SDK' kept in memory only (lost on process restart)"
                )
            }
            return
        }
        if (disabled) prefs.setValue(KEY_COLLECTION_DISABLED_BY_SDK, true) else prefs.remove(KEY_COLLECTION_DISABLED_BY_SDK)
    }

    /** Như process mới: quên cờ trong bộ nhớ (cờ trong `PreferenceHelper` giữ nguyên, như trên đĩa). Chỉ cho test. */
    @JvmSynthetic
    internal fun resetForTest() {
        disabledBySdkInProcess = false
        prefsMissingLogged.set(false)
    }

    private fun status(granted: Boolean) =
        if (granted) FirebaseAnalytics.ConsentStatus.GRANTED else FirebaseAnalytics.ConsentStatus.DENIED
}
