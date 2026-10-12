package com.app.plugin.firbase

import android.os.Bundle
import android.util.Log
import com.app.plugin.app.AppPluginManager
import com.app.plugin.logevent.EventNames
import com.app.plugin.until.LogUtil
import com.app.plugin.until.PreUtil
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.Firebase
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.analytics
import com.google.firebase.messaging.FirebaseMessaging

public class FirHelper private constructor() {
    public var ThresholdTroas: Double = 0.04
    public var ThresholdIdxTroas: Int = 2
    public var ThresholdTierTroas: DoubleArray = doubleArrayOf(0.1, 0.04, 0.01)

    public var ThresholdTopAdRev: DoubleArray = doubleArrayOf(0.094, 0.132, 0.184, 0.270, 0.468)

    public companion object {
        public val instance: FirHelper by lazy { FirHelper() }
    }

    private var firCg: FirHelperConfig? = null

    init {
        // Không lấy token FCM ở đây: `getToken()` bỏ qua cờ auto-init nên sẽ tạo token trước consent.
        // Token được lấy sau consent, qua [fetchFcmTokenAfterConsent] (AdSdkStartup, task analytics).
        firCg = FirHelperConfig()
    }

    /**
     * Lấy token FCM và log (đã che). Chỉ gọi khi tầng consent cho phép FCM (`FcmConsent`), vì lời
     * gọi này tạo token dù auto-init đang tắt. Mọi lỗi được log, không ném.
     */
    public fun fetchFcmTokenAfterConsent(): Unit = fetchFcmTokenAfterConsent {}

    /**
     * Như [fetchFcmTokenAfterConsent]; [onDone] chạy đúng một lần khi lần lấy kết thúc (xong, lỗi, hoặc ném ngay), để
     * `FcmConsent.Follower` chỉ xoá token sau khi lần lấy đang bay đã xong (Task I6).
     */
    internal fun fetchFcmTokenAfterConsent(onDone: () -> Unit) {
        if (!FirebasePresence.available()) return onDone()
        try {
            FirebaseMessaging.getInstance().token.addOnCompleteListener(OnCompleteListener { task ->
                try {
                    if (!task.isSuccessful) {
                        LogUtil.logE("Fir Fetching FCM registration token failed: ${task.exception}")
                        return@OnCompleteListener
                    }
                    Log.d(LogUtil.TAG, SensitiveLogSanitizer.fcmTokenEvent("fetched", task.result))
                } finally {
                    onDone()
                }
            })
        } catch (e: Exception) {
            LogUtil.logE("FCM token fetch failed: ${e.message}")
            onDone()
        }
    }

    public fun fetchRemoteCf(onFetchSuccess: () -> Unit) {
        firCg?.fetchRemoteConfig {
            onFetchSuccess.invoke()
        }
    }

    public fun fetchFireStore(onFetchSuccess: () -> Unit) {
        firCg?.fetchCfFireStore {
            onFetchSuccess.invoke()
        }
    }

    public fun setFetchCgListenner(listenner: FirHelperConfigListenner) {
        firCg?.setFetchCgListenner(listenner)
    }

    public fun logEvent(eventName: String) {
        logEvent(eventName, null)
    }

    public fun logEvent(eventName: String, params: Bundle?) {
        LogUtil.logD("Fir logEvent ev=$eventName")
        if (!FirebasePresence.available()) return
        try {
            var firebaseAnalytics = Firebase.analytics
            firebaseAnalytics.logEvent(eventName, params)
        } catch (e: Exception) {
            LogUtil.logE("logEvent $eventName failed: ${e.message}")
        }
    }

    /**
     * v1, giữ chữ ký: uỷ quyền [PaidEvent.admob] với `value_micros` = round([adRev]·1e6), `precision_type` -1 (SDK gọi
     * `PaidEvent` thẳng với `AdValue`; đường này cho app gọi tay, vẫn có `imp_id`).
     */
    public fun logAdRevAdmob(
        placement: String,
        originPl: String,
        adFormat: String,
        adId: String,
        adNet: String,
        adRev: Double,
        adCurrency: String
    ) {
        PaidEvent.admob(placement, originPl, adFormat, adId, adNet, PaidEvent.micros(adRev), adCurrency, PaidEvent.PRECISION_NONE)
    }

    /** v1, giữ chữ ký: uỷ quyền [PaidEvent.max]. */
    public fun logAdRevMax(
        placement: String,
        originPl: String,
        adFormat: String,
        adId: String,
        adNet: String,
        adRev: Double,
        adCurrency: String
    ) {
        PaidEvent.max(placement, originPl, adFormat, adId, adNet, adRev, adCurrency)
    }

    /** v1, giữ chữ ký: uỷ quyền [PaidEvent.metaDirect]; Meta direct không có doanh thu nên [adRev], [adCurrency] bị bỏ. */
    @Suppress("UNUSED_PARAMETER")
    public fun logAdRevFb(
        placement: String,
        originPl: String,
        adFormat: String,
        adId: String,
        adNet: String,
        adRev: Double,
        adCurrency: String
    ) {
        PaidEvent.metaDirect(placement, originPl, adFormat, adId, adNet)
    }

    /** tROAS + mốc LTV/ngày trên Firebase; gọi từ [PaidEvent] (AdMob, MAX). */
    internal fun logAdTroas(currVa: Double) {
        synchronized(this) {
            if (AppPluginManager.instance.getContext() != null) {
                var preTroas = PreUtil.getFloat(
                    AppPluginManager.instance.getContext()!!, "mem_troas_curr_va", 0.0f
                )
                if ((preTroas + currVa) >= ThresholdTroas) {
                    PreUtil.setFloat(
                        AppPluginManager.instance.getContext()!!, "mem_troas_curr_va", 0.0f
                    )
                    var bundle = Bundle()
                    bundle.putDouble(FirebaseAnalytics.Param.VALUE, preTroas + currVa)
                    bundle.putString(FirebaseAnalytics.Param.CURRENCY, "USD")
                    logEvent(EventNames.DAILY_ADREVENUE, bundle)
                } else {
                    PreUtil.setFloat(
                        AppPluginManager.instance.getContext()!!,
                        "mem_troas_curr_va",
                        (preTroas + currVa).toFloat()
                    )
                }
                var preTop = PreUtil.getFloat(
                    AppPluginManager.instance.getContext()!!, "mem_topad_curr_va", 0.0f
                )
                var currTop = preTop + currVa
                PreUtil.setFloat(
                    AppPluginManager.instance.getContext()!!, "mem_topad_curr_va", currTop.toFloat()
                )
                logAdTopRev(preTop.toDouble(), currTop)
            }
        }
    }

    private fun logAdTopRev(preVa: Double, currVa: Double) {
        for (i in 0..4) {
            if (preVa < ThresholdTopAdRev[i] && currVa >= ThresholdTopAdRev[i]) {
                val bundle = Bundle()
                bundle.putDouble(FirebaseAnalytics.Param.VALUE, ThresholdTopAdRev[i])
                bundle.putString(FirebaseAnalytics.Param.CURRENCY, "USD")
                var CustomEventName: String?
                when (i) {
                    0 -> {
                        CustomEventName = EventNames.AD_LTV_ONE_DAY_TOP_50
                    }

                    1 -> {
                        CustomEventName = EventNames.AD_LTV_ONE_DAY_TOP_40
                    }

                    2 -> {
                        CustomEventName = EventNames.AD_LTV_ONE_DAY_TOP_30
                    }

                    3 -> {
                        CustomEventName = EventNames.AD_LTV_ONE_DAY_TOP_20
                    }

                    else -> CustomEventName = EventNames.AD_LTV_ONE_DAY_TOP_10
                }
                logEvent(CustomEventName, bundle)
            }
        }
    }
}