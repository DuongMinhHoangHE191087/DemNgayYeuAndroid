package com.app.plugin.ads.consent

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import com.google.android.libraries.ads.mobile.sdk.banner.AdSize
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdRequest
import com.google.android.libraries.ads.mobile.sdk.common.BaseRequestBuilder
import com.google.android.libraries.ads.mobile.sdk.common.VideoOptions
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdRequest
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest as NgAdRequest

/**
 * Nơi duy nhất dựng request AdMob, để mọi request đọc cùng một tín hiệu consent (bản Legacy: xem tài liệu ở nguồn gốc).
 * Khi GDPR áp dụng mà purpose 1 không được đồng ý, request gắn `npa=1`: Legacy qua extras của `AdMobAdapter`, GMA Next-Gen
 * qua `setGoogleExtrasBundle` [S22] (cùng khoá `npa`). 2.4.0 NG1 (Hợp đồng B): builder Next-Gen [request], [banner],
 * [native] nhận ad unit id ([S10][S13][S17]); NG5b: `build` Legacy đã xoá.
 */
internal object AdRequestFactory {

    private const val KEY_GDPR_APPLIES = "IABTCF_gdprApplies"
    private const val KEY_PURPOSE_CONSENTS = "IABTCF_PurposeConsents"

    /** Interstitial, app open, rewarded, rewarded interstitial: `AdRequest.Builder(adUnitId)` [S17]–[S20]. */
    internal fun request(context: Context, adUnitId: String): NgAdRequest = npa(context, NgAdRequest.Builder(adUnitId)).build()

    /** Banner/rect: `BannerAdRequest.Builder(adUnitId, size)` [S10]; [extras] (vd. `collapsible`) gộp cùng `npa` [S12]. */
    internal fun banner(context: Context, adUnitId: String, size: AdSize, extras: Bundle? = null): BannerAdRequest =
        npa(context, BannerAdRequest.Builder(adUnitId, size), extras).build()

    /** Native: `NativeAdRequest.Builder(adUnitId, listOf(NATIVE))` [S13]; [video]/[ratio] = tuỳ chọn native (NG5b, R-NG4 m5). */
    internal fun native(
        context: Context, adUnitId: String, video: VideoOptions? = null, ratio: NativeAd.NativeMediaAspectRatio? = null,
    ): NativeAdRequest {
        val builder = NativeAdRequest.Builder(adUnitId, listOf(NativeAd.NativeAdType.NATIVE))
        video?.let { builder.setVideoOptions(it) }
        ratio?.let { builder.setMediaAspectRatio(it) }
        return npa(context, builder).build()
    }

    /** Một Bundle extras Google cho request: [extras] của định dạng + `npa=1` khi không được cá nhân hoá. */
    private fun <T : BaseRequestBuilder<T>> npa(context: Context, builder: T, extras: Bundle? = null): T {
        val personalized = canRequestPersonalizedAds(context)
        if (personalized && extras == null) return builder
        val bundle = Bundle()
        extras?.let { bundle.putAll(it) }
        if (!personalized) bundle.putString("npa", "1")
        return builder.setGoogleExtrasBundle(bundle)
    }

    public fun canRequestPersonalizedAds(context: Context): Boolean = try {
        val prefs = context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE)
        isPersonalizationAllowed(readGdprApplies(prefs), prefs.getString(KEY_PURPOSE_CONSENTS, null))
    } catch (e: Exception) {
        true
    }

    /**
     * @param gdprApplies giá trị `IABTCF_gdprApplies` (1 = áp dụng), `null` khi CMP chưa ghi.
     * @param purposeConsents chuỗi bit `IABTCF_PurposeConsents`, ký tự đầu là purpose 1.
     */
    internal fun isPersonalizationAllowed(gdprApplies: Int?, purposeConsents: String?): Boolean {
        if (gdprApplies != 1) return true
        return purposeConsents?.firstOrNull() == '1'
    }

    // Spec TCF lưu gdprApplies dạng Int; vài CMP cũ ghi dạng String.
    private fun readGdprApplies(prefs: SharedPreferences): Int? {
        if (!prefs.contains(KEY_GDPR_APPLIES)) return null
        return try {
            prefs.getInt(KEY_GDPR_APPLIES, 0)
        } catch (e: ClassCastException) {
            prefs.getString(KEY_GDPR_APPLIES, null)?.toIntOrNull()
        }
    }
}
