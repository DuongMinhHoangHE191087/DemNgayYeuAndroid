package com.app.plugin.ads.core

import com.app.plugin.ads.AdsBase
import com.app.plugin.ads.AdsHelper
import com.app.plugin.ads.AdsBaseBanner
import com.app.plugin.ads.AdsBaseFull
import com.app.plugin.ads.AdsBaseGift
import com.app.plugin.ads.AdsBaseNative
import com.app.plugin.ads.AdsIDDef
import com.app.plugin.ads.MaxBridge
import com.app.plugin.ads.adsfb.AdsFbMy
import com.app.plugin.ads.adsmob.AdsMobMy
import com.app.plugin.until.LogUtil

/**
 * Vòng dựng mạng của [com.app.plugin.ads.AdsHelper.initAdsHelper] (1.5.0 Task 13, G8). Chỉ đổi chủ sở hữu mã: thân
 * vòng giữ nguyên, chỉ đổi tên map/list (`listAdsBn`→[banners], `listAdsNtCl`→[ntCls], `listAdsRect`→[rects],
 * `listAdsNative`→[natives], `listAdsFull`→[fulls], `listAdsGift`→[gifts], `listAdsAll`→[all]). Thứ tự 24 lần
 * `add` giữ nguyên: thứ tự [all] quyết định thứ tự `releaseActivity`/`clearActivity`/`checkLoadAfterGetCf` của
 * façade — không đổi.
 *
 * 1.6.4 F2-MAX: không có SDK AppLovin ([AdsHelper.isMaxSdkAvailable] `false`, app tiện ích) thì không chạm lớp MAX
 * nào (đọc `AdsMaxMy.instance` là khởi tạo lớp đó; ART API 26 kiểm lại nó và log "Rejecting re-init" vì lambda
 * `startNetwork` implements lớp AppLovin vắng mặt); từ 2.0.0 C1c qua seam [MaxBridge.get] (`null`, không dò impl).
 * Các `getAds*` MAX khi đó vốn trả `null` (initAd không chạy), nên map/list và thứ tự [all] như cũ; loại MAX
 * trong config không có mạng → controller bỏ qua.
 */
internal class AdsNetworkRegistry(
    val banners: MutableMap<Int, AdsBaseBanner>,
    val ntCls: MutableMap<Int, AdsBaseBanner>,
    val rects: MutableMap<Int, AdsBaseBanner>,
    val natives: MutableMap<Int, AdsBaseNative>,
    val fulls: MutableMap<Int, AdsBaseFull>,
    val gifts: MutableMap<Int, AdsBaseGift>,
    val all: MutableList<AdsBase>,
) {
    companion object {
        fun build(): AdsNetworkRegistry {
            val banners: MutableMap<Int, AdsBaseBanner> = mutableMapOf()
            val ntCls: MutableMap<Int, AdsBaseBanner> = mutableMapOf()
            val rects: MutableMap<Int, AdsBaseBanner> = mutableMapOf()
            val natives: MutableMap<Int, AdsBaseNative> = mutableMapOf()
            val fulls: MutableMap<Int, AdsBaseFull> = mutableMapOf()
            val gifts: MutableMap<Int, AdsBaseGift> = mutableMapOf()
            val all: MutableList<AdsBase> = mutableListOf()
            val max: MaxBridge.Networks? = MaxBridge.get()?.networks()
            if (max == null) {
                LogUtil.logD(
                    "ads max: không có SDK AppLovin, không đăng ký mạng MAX (loại 6/60/600): placement config trỏ tới MAX bị bỏ qua"
                )
            }

            var adbn: AdsBaseBanner? = AdsMobMy.instance.getAdsBanner(AdsIDDef.AdmobBanner)
            if (adbn != null) {
                banners[AdsIDDef.AdmobBanner] = adbn
                all.add(adbn)
            }
            adbn = max?.getAdsBanner(AdsIDDef.MaxBanner)
            if (adbn != null) {
                banners[AdsIDDef.MaxBanner] = adbn
                all.add(adbn)
            }
            adbn = AdsFbMy.instance.getAdsBanner(AdsIDDef.FBBanner)
            if (adbn != null) {
                banners[AdsIDDef.FBBanner] = adbn
                all.add(adbn)
            }

            var adntcl: AdsBaseBanner? = AdsMobMy.instance.getAdsNtCl(AdsIDDef.AdmobNtCl)
            if (adntcl != null) {
                ntCls[AdsIDDef.AdmobNtCl] = adntcl
                all.add(adntcl)
            }
            adntcl = max?.getAdsNtCl(AdsIDDef.MaxNtCl)
            if (adntcl != null) {
                ntCls[AdsIDDef.MaxNtCl] = adntcl
                all.add(adntcl)
            }
            adntcl = AdsFbMy.instance.getAdsNtCl(AdsIDDef.FBNtCl)
            if (adntcl != null) {
                ntCls[AdsIDDef.FBNtCl] = adntcl
                all.add(adntcl)
            }

            var rect: AdsBaseBanner? = AdsMobMy.instance.getAdsRect(AdsIDDef.AdmobRect)
            if (rect != null) {
                rects[AdsIDDef.AdmobRect] = rect
                all.add(rect)
            }
            rect = max?.getAdsRect(AdsIDDef.MaxRect)
            if (rect != null) {
                rects[AdsIDDef.MaxRect] = rect
                all.add(rect)
            }
            rect = AdsFbMy.instance.getAdsRect(AdsIDDef.FBRect)
            if (rect != null) {
                rects[AdsIDDef.FBRect] = rect
                all.add(rect)
            }

            var nativeAd: AdsBaseNative? = AdsMobMy.instance.getAdsNative(AdsIDDef.AdmobNative)
            if (nativeAd != null) {
                natives[AdsIDDef.AdmobNative] = nativeAd
                all.add(nativeAd)
            }
            nativeAd = max?.getAdsNative(AdsIDDef.MaxNative)
            if (nativeAd != null) {
                natives[AdsIDDef.MaxNative] = nativeAd
                all.add(nativeAd)
            }
            nativeAd = AdsFbMy.instance.getAdsNative(AdsIDDef.FBNative)
            if (nativeAd != null) {
                natives[AdsIDDef.FBNative] = nativeAd
                all.add(nativeAd)
            }

            var adfull: AdsBaseFull? = AdsMobMy.instance.getAdsFull(AdsIDDef.AdmobFullInters)
            if (adfull != null) {
                fulls[AdsIDDef.AdmobFullInters] = adfull
                all.add(adfull)
            }
            adfull = AdsMobMy.instance.getAdsFull(AdsIDDef.AdmobFullLow)
            if (adfull != null) {
                fulls[AdsIDDef.AdmobFullLow] = adfull
                all.add(adfull)
            }
            adfull = AdsMobMy.instance.getAdsFull(AdsIDDef.AdmobFullOpen)
            if (adfull != null) {
                fulls[AdsIDDef.AdmobFullOpen] = adfull
                all.add(adfull)
            }
            adfull = AdsMobMy.instance.getAdsFull(AdsIDDef.AdmobFullNt)
            if (adfull != null) {
                fulls[AdsIDDef.AdmobFullNt] = adfull
                all.add(adfull)
            }

            adfull = max?.getAdsFull(AdsIDDef.MaxFullInters)
            if (adfull != null) {
                fulls[AdsIDDef.MaxFullInters] = adfull
                all.add(adfull)
            }
            adfull = max?.getAdsFull(AdsIDDef.MaxFullNt)
            if (adfull != null) {
                fulls[AdsIDDef.MaxFullNt] = adfull
                all.add(adfull)
            }
            adfull = max?.getAdsFull(AdsIDDef.MaxFullOpen)
            if (adfull != null) {
                fulls[AdsIDDef.MaxFullOpen] = adfull
                all.add(adfull)
            }

            adfull = AdsFbMy.instance.getAdsFull(AdsIDDef.FBFullInters)
            if (adfull != null) {
                fulls[AdsIDDef.FBFullInters] = adfull
                all.add(adfull)
            }
            adfull = AdsFbMy.instance.getAdsFull(AdsIDDef.FBFullNt)
            if (adfull != null) {
                fulls[AdsIDDef.FBFullNt] = adfull
                all.add(adfull)
            }

            var adgift: AdsBaseGift? = AdsMobMy.instance.getAdsGift(AdsIDDef.AdmobRewarded)
            if (adgift != null) {
                gifts[AdsIDDef.AdmobRewarded] = adgift
                all.add(adgift)
            }
            adgift = max?.getAdsGift(AdsIDDef.MaxRewarded)
            if (adgift != null) {
                gifts[AdsIDDef.MaxRewarded] = adgift
                all.add(adgift)
            }
            adgift = AdsFbMy.instance.getAdsGift(AdsIDDef.FBRewarded)
            if (adgift != null) {
                gifts[AdsIDDef.FBRewarded] = adgift
                all.add(adgift)
            }
            // 1.9.0 Task 2: rewarded interstitial (AdMob) vào `gifts` + `all` sau 24 lần add cũ (vòng đời Activity kế thừa).
            adgift = AdsMobMy.instance.getAdsGift(AdsIDDef.AdmobRwi)
            if (adgift != null) {
                gifts[AdsIDDef.AdmobRwi] = adgift
                all.add(adgift)
            }

            return AdsNetworkRegistry(banners, ntCls, rects, natives, fulls, gifts, all)
        }
    }
}
