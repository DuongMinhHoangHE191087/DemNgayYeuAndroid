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
import com.applovin.mediation.ads.MaxInterstitialAd
import java.util.concurrent.atomic.AtomicBoolean

internal class AdsMaxPlInters(context: Context, adsBase: AdsBase, type: Int, pl: String) : AdsPlBaseFull(context, adsBase, type, pl) {
    override val eventTag: String get() = "max_full"

    // Không ghi đè loadedAdMaxAgeMs: miễn hạn `fullscreen_max_age_minutes` (review E I1). MAX tự hết hạn và nạp lại
    // trong cùng đối tượng `MaxInterstitialAd` (`MaxFullscreenAdImpl.onAdExpired`, applovin-sdk 13.6.2), nên mốc load
    // của SDK (`AdsEcmp.loadedAtMillis`, chỉ đặt lại khi `adObject` đổi) không bao giờ mới lại; hạn của SDK chỉ
    // từ chối nhầm quảng cáo MAX còn tươi. MAX native full vẫn áp hạn (MAX chỉ báo `onNativeAdExpired`, không nạp lại).

    var interstitialAd: MaxInterstitialAd? = null
    var ecmpShow: AdsEcmp? = null

    override fun onInit() {
    }

    /** Mục waterfall mà đối tượng create-once đang gắn (unit id + instance AdsEcmp); listener chụp nó lúc dựng. */
    private var boundEcmp: AdsEcmp? = null

    /**
     * Cờ của listener đối tượng đang gắn: dựng lại thì hạ cờ cũ, callback load đã xếp hàng của đối tượng cũ (MAX giao
     * trước khi gỡ listener) bị bỏ. Listener đọc cờ chụp theo đối tượng, không đọc field (không thêm accessor vào dump).
     */
    private var boundLive: AtomicBoolean? = null

    /** [AdsPlacementEcmpBase.loadGeneration] lúc gửi request gần nhất; listener của đối tượng đang gắn đọc từ field. */
    private var loadGen = 0

    override fun tryLoad() {
        if (holdUntilReady(AdsMaxMy.instance.readyQueue)) return
        loadPl = adsPlacementEcmp.getPl
        val ecmpLoad = adsPlacementEcmp.adEcmps?.getEcmpForLoad()
        if (ecmpLoad != null) {
            if (!ecmpLoad.isLoaded && !ecmpLoad.isLoading) {
                LogUtil.logD("ads full max$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad id=${ecmpLoad.adsId}")
                adsPlacementEcmp.isLoading = true
                adsPlacementEcmp.isHasLoad = true
                ecmpLoad.isLoading = true
                ecmpLoad.isLoaded = false

                if (interstitialAd == null || boundEcmp !== ecmpLoad) {
                    if (interstitialAd != null && adsPlacementEcmp.isShowing) {
                        // Đang hiện bằng đối tượng cũ: không dựng lại giữa lúc hiện (mất callback đóng). Lượt load này vẫn đi với
                        // đối tượng cũ; lượt sau (sau dismiss) dựng lại. Log để thấy trên máy.
                        LogUtil.logD("ads full max$adsType $loadPl ${adsPlacementEcmp.originPl} rebind deferred: showing id=${boundEcmp?.adsId} → ${ecmpLoad.adsId}")
                    } else {
                        // Mục khác (parserEcpms đổi unit, id bỏ rồi thêm lại, hoặc bước waterfall sau): đối tượng cũ request unit
                        // cũ và listener của nó ghi vào mục cũ → gỡ listener, destroy, dựng cho mục này (final review Minor 7).
                        interstitialAd?.let { old -> old.setListener(null); old.setRevenueListener(null); old.destroy() }
                        boundLive?.set(false)
                        val live = AtomicBoolean(true).also { boundLive = it }
                        val lateTag = "ads full max$adsType ${adsPlacementEcmp.originPl}"
                        boundEcmp = ecmpLoad
                        interstitialAd = MaxInterstitialAd(ecmpLoad.adsId, mContext)
                        interstitialAd?.setListener(object : MaxAdListener {
                            override fun onAdLoaded(ad: MaxAd) {
                                // Callback đã xếp hàng của đối tượng đã thay (trước khi gỡ listener): không thuộc request đang chờ.
                                if (!live.get()) { LogUtil.logD("$lateTag late load callback of replaced object dropped"); return }
                                if (isStaleLoadCallback(loadGen)) { keepLateAd(ecmpLoad, interstitialAd); return }
                                LogUtil.logD("ads full max$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad ok id=${ecmpLoad.adsId}")
                                adsPlacementEcmp.isLoading = false
                                adsPlacementEcmp.isloaded = true
                                ecmpLoad.isLoading = false
                                ecmpLoad.isLoaded = true
                                ecmpLoad.adObject = interstitialAd
                                adsPlacementEcmp.adsLoadCb?.onLoaded(
                                    loadPl, adsPlacementEcmp.originPl, ad.adUnitId
                                )
                                AdsHelper.instance.onAdLoaded(loadPl, adsPlacementEcmp.originPl, "interstitial", "applovin", ecmpLoad.adsId, ad.networkName ?: "source_null")
                                onFinishLoad()
                            }

                            override fun onAdLoadFailed(p0: String, adError: MaxError) {
                                if (!live.get()) { LogUtil.logD("$lateTag late load callback of replaced object dropped"); return }
                                // Slot rảnh: MAX tự nạp lại lỗi trên đối tượng đang giữ → bỏ nó (Task J review IMP-2), không callback.
                                if (isStaleLoadCallback(loadGen)) { dropHeldAdOnIdleLoadFailure(ecmpLoad, interstitialAd); return }
                                LogUtil.logD("ads full max$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad fail id=${ecmpLoad.adsId} err=${adError.code}")
                                ecmpLoad.isLoading = false
                                ecmpLoad.adObject = null
                                adsPlacementEcmp.adEcmps?.let { it.idxCurrEcpm++ }
                                AdsHelper.instance.onAdLoadFail(loadPl, adsPlacementEcmp.originPl, "interstitial", "applovin", ecmpLoad.adsId, "${adError.code}")
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
                                LogUtil.logD("ads full max$adsType $loadPl ${adsPlacementEcmp.originPl} onshowed")
                                adsPlacementEcmp.adsShowCb?.onAdShowed(
                                    showPl, adsPlacementEcmp.originPl, ad.adUnitId, 0
                                )
                            }

                            override fun onAdHidden(ad: MaxAd) {
                                LogUtil.logD("ads full max$adsType $showPl ${adsPlacementEcmp.originPl} ondissmiss")
                                ecmpShow?.isLoaded = false
                                ecmpShow?.adObject = null
                                adsPlacementEcmp.isShowing = false
                                adsPlacementEcmp.adsShowCb?.onAdDismissed(
                                    showPl, adsPlacementEcmp.originPl, ad.adUnitId
                                )
                            }

                            override fun onAdClicked(ad: MaxAd) {
                                LogUtil.logD("ads full max$adsType $showPl ${adsPlacementEcmp.originPl} onclick")
                                adsPlacementEcmp.adsShowCb?.onAdClick(
                                    showPl, adsPlacementEcmp.originPl, ad.adUnitId
                                )
                                AdsHelper.instance.onclickAd()
                            }

                            override fun onAdDisplayFailed(ad: MaxAd, adError: MaxError) {
                                LogUtil.logD("ads full max$adsType $showPl ${adsPlacementEcmp.originPl} onshow fail")
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
                        interstitialAd?.setRevenueListener {
                            PaidEvent.max(
                                showPl, adsPlacementEcmp.originPl, "interstitial", it.adUnitId, it.networkName, it.revenue, "USD"
                            )
                        }
                    }
                }

                // Load the first ad
                loadGen = adsPlacementEcmp.loadGeneration
                interstitialAd?.loadAd()
                AdsHelper.instance.onAdLoadStart(loadPl, adsPlacementEcmp.originPl, "interstitial", "applovin", ecmpLoad.adsId)
            } else {
                if (ecmpLoad.isLoaded) {
                    LogUtil.logD("ads full max$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad has loaded")
                    adsPlacementEcmp.adsLoadCb?.onLoaded(
                        loadPl, adsPlacementEcmp.originPl, ecmpLoad.adsId
                    )
                } else {
                    LogUtil.logD("ads full max$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad is loading")
                    adsPlacementEcmp.adsLoadCb?.onLoadFailed(
                        loadPl, adsPlacementEcmp.originPl, "", "isloading"
                    )
                }
            }
        } else {
            LogUtil.logD("ads full max$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad ecmpLoad null")
            adsPlacementEcmp.adsLoadCb?.onLoadFailed(
                loadPl, adsPlacementEcmp.originPl, "", "ecmpLoad null"
            )
        }
    }

    /** Task J4: `hasLoadedAd` theo `isReady()` như `show` (Task I4); MAX đang tự nạp lại thì chưa sẵn sàng, không bỏ quảng cáo. */
    override fun isLoadedAdReady(ad: Any): Boolean = (ad as? MaxInterstitialAd)?.isReady ?: true

    override fun show(placement: String, adsShowCb: AdsShowCb?, flagShow: Int): Boolean {
        ecmpShow = loadedAdWithinMaxAge()
        if (ecmpShow != null && interstitialAd != null) {
            if (mActivity == null) {
                LogUtil.logD("ads full max$adsType $placement ${adsPlacementEcmp.originPl} show activity null")
                return false
            }
            if (interstitialAd?.isReady != true) {
                // Task I4: MAX đang tự nạp lại (sau hết hạn) hoặc nạp lại lỗi; showAd lúc này chỉ ra display fail.
                LogUtil.logD("ads full max$adsType $placement ${adsPlacementEcmp.originPl} show not ready")
                LogEventServer.logAdShowFailWithReason(placement, Constants.AdShowFailReason.SHOW_NOT_LOADED)
                return false
            }
            LogUtil.logD("ads full max$adsType $placement ${adsPlacementEcmp.originPl} show")
            setShowPlacement(placement)
            adsPlacementEcmp.isloaded = false
            adsPlacementEcmp.isShowing = true
            adsPlacementEcmp.adsShowCb = adsShowCb
            interstitialAd?.showAd(mActivity!!)
            return true
        } else {
            LogUtil.logD("ads full max$adsType $placement ${adsPlacementEcmp.originPl} show not loaded")
            return false
        }
    }
}