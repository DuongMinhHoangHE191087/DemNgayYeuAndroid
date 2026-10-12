package com.app.plugin.ads.adsmax

import android.content.Context
import com.app.plugin.ads.AdsBase
import com.app.plugin.ads.AdsEcmp
import com.app.plugin.ads.AdsHelper
import com.app.plugin.ads.AdsPlBaseFull
import com.app.plugin.ads.AdsShowCb
import com.app.plugin.firbase.PaidEvent
import com.app.plugin.logevent.LogEventServer
import com.app.plugin.until.Constants
import com.app.plugin.until.LogUtil
import com.applovin.mediation.MaxAd
import com.applovin.mediation.MaxAdListener
import com.applovin.mediation.MaxError
import com.applovin.mediation.ads.MaxAppOpenAd

internal class AdsMaxPlOpen(context: Context, adsBase: AdsBase, type: Int, pl: String) : AdsPlBaseFull(context, adsBase, type, pl) {
    override val eventTag: String get() = "max_open"

    var ecmpShow:AdsEcmp? = null

    // Không ghi đè loadedAdMaxAgeMs (Task I1, như FE1 cho MAX interstitial/rewarded): `MaxAppOpenAd` bọc
    // `MaxFullscreenAdImpl` (định dạng APP_OPEN), tự hết hạn và nạp lại trong cùng đối tượng (`onAdExpired` →
    // `MediationServiceImpl.loadAd`, applovin-sdk 13.6.2), nên mốc load của SDK (`AdsEcmp.loadedAtMillis`, chỉ đặt lại
    // khi `adObject` đổi) không mới lại; hạn 4 giờ của SDK chỉ từ chối nhầm quảng cáo MAX còn tươi. AdMob app open vẫn
    // áp `app_open_max_age_minutes`. Không còn nhánh hết tuổi; [destroyAd] giữ cho lúc đóng (Task J3) và quảng cáo muộn
    // bị bỏ ([keepLateAd]).

    override fun onInit() {
    }

    override fun tryLoad() {
        if (holdUntilReady(AdsMaxMy.instance.readyQueue)) return
        loadPl = adsPlacementEcmp.getPl
        val ecmpLoad = adsPlacementEcmp.adEcmps?.getEcmpForLoad()
        if (ecmpLoad != null) {
            if (!ecmpLoad.isLoaded && !ecmpLoad.isLoading) {
                LogUtil.logD("ads full open max$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad id=${ecmpLoad.adsId}")
                adsPlacementEcmp.isLoading = true
                adsPlacementEcmp.isHasLoad = true
                ecmpLoad.isLoading = true
                ecmpLoad.isLoaded = false
                val loadGen = adsPlacementEcmp.loadGeneration

                var appOpenAd:MaxAppOpenAd = MaxAppOpenAd(ecmpLoad.adsId, mContext)
                appOpenAd.setListener(object : MaxAdListener {
                    override fun onAdLoaded(p0: MaxAd) {
                        if (isStaleLoadCallback(loadGen)) { keepLateAd(ecmpLoad, appOpenAd); return }
                        LogUtil.logD("ads full open max$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad ok id=${ecmpLoad.adsId}")
                        adsPlacementEcmp.isLoading = false
                        adsPlacementEcmp.isloaded = true
                        ecmpLoad.isLoading = false
                        ecmpLoad.isLoaded = true
                        ecmpLoad.adObject = appOpenAd
                        adsPlacementEcmp.adsLoadCb?.onLoaded(
                            loadPl, adsPlacementEcmp.originPl, appOpenAd.adUnitId
                        )
                        AdsHelper.instance.onAdLoaded(loadPl, adsPlacementEcmp.originPl, "opened", "applovin", ecmpLoad.adsId, p0.networkName ?: "source_null")
                        onFinishLoad()
                    }

                    override fun onAdLoadFailed(p0: String, adError: MaxError) {
                        // Slot rảnh: MAX tự nạp lại lỗi trên đối tượng đang giữ → bỏ và destroy() nó (Task J review IMP-2).
                        if (isStaleLoadCallback(loadGen)) { dropHeldAdOnIdleLoadFailure(ecmpLoad, appOpenAd); return }
                        LogUtil.logD("ads full open max$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad fail id=${ecmpLoad.adsId} err=${adError.code}")
                        ecmpLoad.isLoading = false
                        ecmpLoad.adObject = null
                        adsPlacementEcmp.adEcmps?.let { it.idxCurrEcpm++ }
                        AdsHelper.instance.onAdLoadFail(loadPl, adsPlacementEcmp.originPl, "opened", "applovin", ecmpLoad.adsId, "${adError.code}")
                        if (!adsPlacementEcmp.flag4Load && adsPlacementEcmp.adEcmps?.idxCurrEcpm ?: 0 < adsPlacementEcmp.adEcmps?.list?.size ?: 0) {
                            tryLoad()
                        } else {
                            if (!adsPlacementEcmp.flag4Load) {
                                adsPlacementEcmp.isLoading = false
                                adsPlacementEcmp.isloaded = false
                                adsPlacementEcmp.adsLoadCb?.onLoadFailed(
                                    loadPl,
                                    adsPlacementEcmp.originPl,
                                    "",
                                    "" + adError.code + " " + adError.message
                                )
                            }
                            onFinishLoad()
                        }
                    }

                    override fun onAdDisplayed(ad: MaxAd) {
                        LogUtil.logD("ads full open max$adsType $loadPl ${adsPlacementEcmp.originPl} onAdDisplayed")
                        adsPlacementEcmp.adsShowCb?.onAdShowed(
                            showPl,
                            adsPlacementEcmp.originPl,
                            ad.adUnitId, 0
                        )
                    }

                    override fun onAdHidden(ad: MaxAd) {
                        LogUtil.logD("ads full open max$adsType $loadPl ${adsPlacementEcmp.originPl} onAdHidden")
                        ecmpShow?.isLoaded = false
                        ecmpShow?.adObject = null
                        adsPlacementEcmp.isShowing = false
                        // Task J3 (như Meta I3): mỗi lần load tạo `MaxAppOpenAd` mới, đối tượng đã hiện xong không dùng
                        // lại; `destroy()` để MAX giải phóng nó, không chỉ bỏ tham chiếu. Chỉ ở đây, sau khi đã thôi hiện.
                        destroyAd(appOpenAd)
                        adsPlacementEcmp.adsShowCb?.onAdDismissed(
                            showPl, adsPlacementEcmp.originPl, ad.adUnitId
                        )
                    }

                    override fun onAdClicked(ad: MaxAd) {
                        LogUtil.logD("ads full open max$adsType $loadPl ${adsPlacementEcmp.originPl} onclick")
                        adsPlacementEcmp.adsShowCb?.onAdClick(
                            showPl, adsPlacementEcmp.originPl, ad.adUnitId
                        )
                        AdsHelper.instance.onclickAd()
                    }

                    override fun onAdDisplayFailed(ad: MaxAd, adError: MaxError) {
                        LogUtil.logD("ads full open max$adsType $loadPl ${adsPlacementEcmp.originPl} onshow fail")
                        ecmpShow?.isLoaded = false
                        ecmpShow?.adObject = null
                        adsPlacementEcmp.isShowing = false
                        adsPlacementEcmp.adsShowCb?.onAdShowFail(
                            showPl,
                            adsPlacementEcmp.originPl,
                            ad.adUnitId,
                            "${adError.code}-${adError.message}"
                        )
                    }

                })
                appOpenAd.setRevenueListener {
                    PaidEvent.max(
                        showPl, adsPlacementEcmp.originPl, "opened", it.adUnitId, it.networkName, it.revenue, "USD"
                    )
                }
                appOpenAd.loadAd()
                AdsHelper.instance.onAdLoadStart(loadPl, adsPlacementEcmp.originPl, "opened", "applovin", ecmpLoad.adsId)
            } else {
                if (ecmpLoad.isLoaded) {
                    LogUtil.logD("ads full open max$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad has loaded")
                    adsPlacementEcmp.adsLoadCb?.onLoaded(
                        loadPl, adsPlacementEcmp.originPl, ecmpLoad.adsId
                    )
                } else {
                    LogUtil.logD("ads full open max$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad is loading")
                    adsPlacementEcmp.adsLoadCb?.onLoadFailed(
                        loadPl, adsPlacementEcmp.originPl, "", "isloading"
                    )
                }
            }
        } else {
            LogUtil.logD("ads full open max$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad ecmpLoad null")
            adsPlacementEcmp.adsLoadCb?.onLoadFailed(
                loadPl, adsPlacementEcmp.originPl, "", "ecmpLoad null"
            )
        }
    }

    /** `MaxAppOpenAd.destroy()` (applovin-sdk 13.6.2): khi đóng (Task J3) và khi quảng cáo muộn bị bỏ ([keepLateAd]). */
    override fun destroyAd(ad: Any?): Boolean {
        (ad as? MaxAppOpenAd)?.destroy() ?: return false
        return true
    }

    /** Task J4: `hasLoadedAd` theo `isReady()` như `show` (Task I4); MAX đang tự nạp lại thì chưa sẵn sàng, không bỏ quảng cáo. */
    override fun isLoadedAdReady(ad: Any): Boolean = (ad as? MaxAppOpenAd)?.isReady ?: true

    override fun show(placement: String, adsShowCb: AdsShowCb?, flagShow: Int): Boolean {
        ecmpShow = loadedAdWithinMaxAge()
        if (ecmpShow != null) {
            val readyAd = ecmpShow?.adObject as? MaxAppOpenAd
            if (readyAd != null && !readyAd.isReady) {
                // Task I4: MAX đang tự nạp lại (sau hết hạn) hoặc nạp lại lỗi; showAd lúc này chỉ ra display fail.
                LogUtil.logD("ads full open max$adsType $placement ${adsPlacementEcmp.originPl} show not ready")
                LogEventServer.logAdShowFailWithReason(placement, Constants.AdShowFailReason.SHOW_NOT_LOADED)
                return false
            }
            LogUtil.logD("ads full open max$adsType $placement ${adsPlacementEcmp.originPl} show")
            setShowPlacement(placement)
            adsPlacementEcmp.isloaded = false
            adsPlacementEcmp.isShowing = true
            adsPlacementEcmp.adsShowCb = adsShowCb
            val ad = ecmpShow?.adObject as? MaxAppOpenAd
            if (ad == null) {
                LogUtil.logD("ads full open max$adsType $placement ${adsPlacementEcmp.originPl} show ad null")
                ecmpShow?.isLoaded = false
                ecmpShow?.adObject = null
                adsPlacementEcmp.isShowing = false
                adsPlacementEcmp.adsShowCb?.onAdShowFail(
                    showPl, adsPlacementEcmp.originPl, ecmpShow?.adsId, "ad null"
                )
                return false
            }
            ad.showAd()
            return true
        } else {
            LogUtil.logD("ads full open max$adsType $placement ${adsPlacementEcmp.originPl} show not loaded")
            return false
        }
    }
}