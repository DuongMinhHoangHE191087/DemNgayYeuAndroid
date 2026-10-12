package com.app.plugin.firbase

import android.content.Context
import com.app.plugin.logevent.EventNames
import com.app.plugin.until.AppUtil
import com.app.plugin.until.LogUtil
import com.google.firebase.Firebase
import com.google.firebase.analytics.FirebaseAnalytics.Param
import com.google.firebase.analytics.analytics
import com.google.firebase.analytics.logEvent

/**
 * 2.5.1 PLEVT: AdMob đã liên kết (`ads_admob_firebase_linked` = 1, `AdRevenueRouting` bỏ `ad_impression`) và resource
 * `ads_cf_placement_event` = 1 thì [PaidEvent] ghi `ad_placement_impression`; tiền ở `ad_value`, không bao giờ `value` (GA4 không cộng
 * doanh thu, không đếm đôi). Chỉ resource, RC `ads_cf_overrides` không đặt được; vắng/khác 1/đọc lỗi = tắt. Đọc một lần ở `initPlugin`.
 */
internal object PlacementPaidEvent {
    private const val RES = "ads_cf_placement_event"
    private const val PARAM_VALUE_MAX = 100
    @Volatile var enabled: Boolean = false

    fun read(context: Context): Boolean = try {
        AppUtil.getResourceInt(context, RES) == 1
    } catch (e: Exception) {
        LogUtil.logE("$RES read failed, ad_placement_impression stays off: ${e.message}")
        false
    }

    fun log(
        platform: String, adUnit: String, format: String, adSource: String, value: Double, currency: String, impId: String,
        placement: String,
    ) {
        if (!FirebasePresence.available()) return
        try {
            Firebase.analytics.logEvent(EventNames.AD_PLACEMENT_IMPRESSION) {
                if (placement.isNotEmpty()) param("ad_placement", placement.take(PARAM_VALUE_MAX))
                param(Param.AD_UNIT_NAME, adUnit)
                param(Param.AD_FORMAT, format)
                param(Param.AD_SOURCE, adSource)
                param(Param.AD_PLATFORM, platform)
                param("imp_id", impId)
                param("ad_value", value)
                param(Param.CURRENCY, currency)
            }
        } catch (e: Exception) {
            LogUtil.logE("PaidEvent ad_placement_impression $platform failed: ${e.message}")
        }
    }
    fun resetForTest() { enabled = false }
}
