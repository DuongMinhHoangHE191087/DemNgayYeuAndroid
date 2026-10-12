package com.app.plugin.ads.consent

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import com.google.ads.mediation.admob.AdMobAdapter
import com.google.android.gms.ads.AdRequest

/**
 * Nơi duy nhất dựng `AdRequest` cho AdMob, để mọi request đọc cùng một tín hiệu consent.
 *
 * UMP `ConsentStatus.OBTAINED` chỉ nghĩa là "người dùng đã trả lời form": cả Đồng ý lẫn Không
 * đồng ý đều ra OBTAINED, và `canRequestAds()` đều trả `true`. Tín hiệu đúng là chuỗi IAB TCF v2
 * mà UMP ghi vào SharedPreferences mặc định sau khi form đóng: khi GDPR áp dụng mà purpose 1
 * ("lưu và/hoặc truy cập thông tin trên thiết bị") không được đồng ý, request gắn `npa=1`.
 *
 * GMA vốn đã đọc chuỗi TCF; `npa=1` ở đây là lớp bảo vệ thêm, chỉ bớt cá nhân hoá, không bao
 * giờ thêm. Mediation network khác đọc TCF qua adapter của chúng, không đọc `npa`.
 *
 * Lấy từ Invoice Maker; bản đó chỉ áp cho 4/8 định dạng, ở đây áp cho cả 8.
 */
internal object AdRequestFactory {

    private const val KEY_GDPR_APPLIES = "IABTCF_gdprApplies"
    private const val KEY_PURPOSE_CONSENTS = "IABTCF_PurposeConsents"

    internal fun build(context: Context): AdRequest {
        val builder = AdRequest.Builder()
        if (!canRequestPersonalizedAds(context)) {
            builder.addNetworkExtrasBundle(
                AdMobAdapter::class.java,
                Bundle().apply { putString("npa", "1") },
            )
        }
        return builder.build()
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
