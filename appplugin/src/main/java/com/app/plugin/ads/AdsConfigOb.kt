package com.app.plugin.ads

import android.content.Context
import com.app.plugin.until.AppUtil
import com.app.plugin.until.PreUtil

public class AdsConfigOb(context: Context, country: String = "default") {
    public companion object {
        public var BannerStepDefault: String = "cir:${AdsIDDef.AdmobBanner}"
        public var RectStepDefault: String = "cir:${AdsIDDef.AdmobRect}"
        public var ClStepDefault: String = "cir:${AdsIDDef.AdmobNtCl}"
        public var NativeStepDefault: String = "cir:${AdsIDDef.AdmobNative}"
        public var FullStepDefault: String = "cir:${AdsIDDef.AdmobFullInters}"
        public var Full2StepDefault: String = "cir:${AdsIDDef.AdmobFullNt}#re:${AdsIDDef.MaxFullNt}"
        public var GiftStepDefault: String = "cir:${AdsIDDef.AdmobRewarded}#re:${AdsIDDef.MaxRewarded}"
    }

    public var mContext: Context? = null
    public var countryCode: String = "default"
    public var maskAdsStatus: Int = 255

    //OpenAd
    public var openFirstCfChange:Boolean = false
    public var openAdFirstType: Int = 3
    public var openAdFirstImg: Int = 0
    public var openAdtype:Int = 2
    public var openAdFlagImg:Int = 0
    public var openAdShowAt: Int = 1
    public var openAdDeltaTime:Int = 1
    public var openAdFlagFull2:Int = 3

    //banner
    public var bannerStep: String = BannerStepDefault
    public var bannerStepCicle: MutableList<Int> = mutableListOf()
    public var bannerStepRe: MutableList<Int> = mutableListOf()
    public var bannerTRefresh: Int = 60
    public var bannerDeltaTime: Int = 50

    //Rect
    public var rectStep: String = RectStepDefault
    public var rectStepList: MutableList<Int> = mutableListOf()

    //collapse
    public var clStep: String = ClStepDefault
    public var clStepList: MutableList<Int> = mutableListOf()
    public var clDeltaTime: Int = 10

    //native
    public var nativeStep:String = NativeStepDefault
    public var nativeStepList: MutableList<Int> = mutableListOf()

    //full
    public var fullStep: String = FullStepDefault
    public var fullTotalOfDay: Int = 200
    public var fullStartTime: Int = 0
    public var fullSesstionTime: Int = 0
    public var fullDeltaTime: Int = 30
    public var fullStepCicle: MutableList<Int> = mutableListOf()
    public var fullStepRe: MutableList<Int> = mutableListOf()

    //full2
    public var full2FlagShow:Int = 3
    public var full2DeltaTime: Int = 10
    public var full2Step: String = Full2StepDefault
    public var full2StepCicle: MutableList<Int> = mutableListOf()
    public var full2StepRe: MutableList<Int> = mutableListOf()

    //rewared
    public var giftStep: String = GiftStepDefault
    public var giftTotalOfDay: Int = 200
    public var giftDeltaTime: Int = 30
    public var giftStepCicle: MutableList<Int> = mutableListOf()
    public var giftStepRe: MutableList<Int> = mutableListOf()

    init {
        mContext = context
        countryCode = country
    }

    // mContext là var public, có thể bị đặt null. Khi đó đọc/ghi cấu hình là no-op thay vì
    // crash vì `mContext!!` như trước 1.3.0 (lấy từ Invoice Maker).
    public fun loadFromMem() {
        val ctx = mContext ?: return
        maskAdsStatus = PreUtil.getInt(ctx, "cfcurr_ads_mask", 0xffffff)
        //Open
        val openft = AppUtil.getResourceInt(ctx, "ads_cf_open_first_type")
        val openfimg = AppUtil.getResourceInt(ctx, "ads_cf_open_first_img")
        val openrt = AppUtil.getResourceInt(ctx, "ads_cf_open_type")
        val openrimg = AppUtil.getResourceInt(ctx, "ads_cf_open_img")
        val openfu2 = AppUtil.getResourceInt(ctx, "ads_cf_open_full2")
        // BUG-08 FIX: openAdShowAt dùng resource riêng "ads_cf_open_show_at" (default 0)
        // Trước đây dùng nhầm openrimg (= image type) làm default cho showAt → sai ngữ nghĩa
        val openrShowAt = AppUtil.getResourceInt(ctx, "ads_cf_open_show_at")
        openAdFirstType = PreUtil.getInt(ctx, "cfcurr_ads_openfirsttype", openft)
        openAdFirstImg = PreUtil.getInt(ctx, "cfcurr_ads_openfirstimgtype", openfimg)
        openAdtype = PreUtil.getInt(ctx, "cfcurr_ads_opentype", openrt)
        openAdShowAt = PreUtil.getInt(ctx, "cfcurr_ads_openat", openrShowAt)
        openAdDeltaTime = PreUtil.getInt(ctx, "cfcurr_ads_opendeltatime", 15)
        openAdFlagImg = PreUtil.getInt(ctx, "cfcurr_ads_openflagimg", 0)
        openAdFlagFull2 = PreUtil.getInt(ctx, "cfcurr_ads_openflagfull2", openfu2)

        //banner
        val bnstep = AppUtil.getResourceString(ctx, "ads_cf_bn_step", BannerStepDefault)
        bannerTRefresh = PreUtil.getInt(ctx, "cfcurr_ads_bntrefresh", 60)
        bannerStep = PreUtil.getString(ctx, "cfcurr_ads_bnstep", bnstep)
        parserBannerStep()

        //Rect
        rectStep = PreUtil.getString(ctx, "cfcurr_ads_rectstep", RectStepDefault)
        parserRectStep()

        //Collapse
        clStep = PreUtil.getString(ctx, "cfcurr_ads_clstep", ClStepDefault)
        parserCollapseStep()

        //Native
        val nativestep = AppUtil.getResourceString(ctx, "ads_cf_native_step", NativeStepDefault)
        nativeStep = PreUtil.getString(ctx, "cfcurr_ads_nativestep", nativestep)
        parserNativeStep()

        //full
        val fullstep = AppUtil.getResourceString(ctx, "ads_cf_full_step", FullStepDefault)
        fullTotalOfDay = PreUtil.getInt(ctx, "cfcurr_ads_fulltotoal", 200)
        fullStartTime = PreUtil.getInt(ctx, "cfcurr_ads_fullstarttime", 0)
        fullSesstionTime = PreUtil.getInt(ctx, "cfcurr_ads_fullsesstime", 0)
        fullDeltaTime = PreUtil.getInt(ctx, "cfcurr_ads_fulldeltatime", 30)
        fullStep = PreUtil.getString(ctx, "cfcurr_ads_fullstep", fullstep)
        parserFullStep()

        //full2
        full2FlagShow = PreUtil.getInt(ctx, "cfcurr_ads_full2flag", 3)
        full2DeltaTime = PreUtil.getInt(ctx, "cfcurr_ads_full2deltatime", 10)
        full2Step = PreUtil.getString(ctx, "cfcurr_ads_full2step", Full2StepDefault)
        parserFull2Step()

        //gift
        val giftstep = AppUtil.getResourceString(ctx, "ads_cf_gift_step", GiftStepDefault)
        giftTotalOfDay = PreUtil.getInt(ctx, "cfcurr_ads_gifttotoal", 200)
        giftDeltaTime = PreUtil.getInt(ctx, "cfcurr_ads_giftdeltatime", 30)
        giftStep = PreUtil.getString(ctx, "cfcurr_ads_giftstep", giftstep)
        parserGiftStep()

    }

    public fun saveCurr() {
        val ctx = mContext ?: return
        PreUtil.setInt(ctx, "cfcurr_ads_mask", maskAdsStatus)
        saveOpenAd()
        saveBannerCurr()
        saveRectCurr()
        saveClCurr()
        saveNativeCurr()
        saveFullCurr()
        saveFull2Curr()
        saveGiftCurr()
    }

    public fun saveOpenAd() {
        val ctx = mContext ?: return
        PreUtil.setInt(ctx, "cfcurr_ads_openfirsttype", openAdFirstType)
        PreUtil.setInt(ctx, "cfcurr_ads_openfirstimgtype", openAdFirstImg)
        PreUtil.setInt(ctx, "cfcurr_ads_opentype", openAdtype)
        PreUtil.setInt(ctx, "cfcurr_ads_openat", openAdShowAt)
        PreUtil.setInt(ctx, "cfcurr_ads_opendeltatime", openAdDeltaTime)
        PreUtil.setInt(ctx, "cfcurr_ads_openflagimg", openAdFlagImg)
        PreUtil.setInt(ctx, "cfcurr_ads_openflagfull2", openAdFlagFull2)
    }

    public fun saveBannerCurr() {
        val ctx = mContext ?: return
        PreUtil.setInt(ctx, "cfcurr_ads_bntrefresh", bannerTRefresh)
        PreUtil.setString(ctx, "cfcurr_ads_bnstep", bannerStep)
    }

    public fun saveRectCurr() {
        val ctx = mContext ?: return
        PreUtil.setString(ctx, "cfcurr_ads_rectstep", rectStep)
    }

    public fun saveClCurr() {
        val ctx = mContext ?: return
        PreUtil.setString(ctx, "cfcurr_ads_clstep", clStep)
    }

    public fun saveNativeCurr() {
        val ctx = mContext ?: return
        PreUtil.setString(ctx, "cfcurr_ads_nativestep", nativeStep)
    }

    public fun saveFullCurr() {
        val ctx = mContext ?: return
        PreUtil.setInt(ctx, "cfcurr_ads_fulltotoal", fullTotalOfDay)
        PreUtil.setInt(ctx, "cfcurr_ads_fullstarttime", fullStartTime)
        PreUtil.setInt(ctx, "cfcurr_ads_fullsesstime", fullSesstionTime)
        PreUtil.setInt(ctx, "cfcurr_ads_fulldeltatime", fullDeltaTime)
        PreUtil.setString(ctx, "cfcurr_ads_fullstep", fullStep)
    }

    public fun saveFull2Curr() {
        val ctx = mContext ?: return
        PreUtil.setInt(ctx, "cfcurr_ads_full2flag", full2FlagShow)
        PreUtil.setInt(ctx, "cfcurr_ads_full2deltatime", full2DeltaTime)
        PreUtil.setString(ctx, "cfcurr_ads_full2step", full2Step)
    }

    public fun saveGiftCurr() {
        val ctx = mContext ?: return
        PreUtil.setInt(ctx, "cfcurr_ads_gifttotoal", giftTotalOfDay)
        PreUtil.setInt(ctx, "cfcurr_ads_giftdeltatime", giftDeltaTime)
        PreUtil.setString(ctx, "cfcurr_ads_giftstep", giftStep)
    }

    public fun coppyFromOther(other: AdsConfigOb?) {
        if (other != null) {
            maskAdsStatus = other.maskAdsStatus

            //banner
            bannerStep = other.bannerStep
            if (!bannerStep.startsWith("cir")) {
                bannerStep = BannerStepDefault
            }
            parserBannerStep()

            //full
            fullTotalOfDay = other.fullTotalOfDay
            fullStartTime = other.fullStartTime
            fullSesstionTime = other.fullSesstionTime
            fullDeltaTime = other.fullDeltaTime
            fullStep = other.fullStep
            if (!fullStep.startsWith("cir")) {
                fullStep = FullStepDefault
            }
            parserFullStep()

            //gift
            giftTotalOfDay = other.giftTotalOfDay
            giftStep = other.giftStep
            if (!giftStep.startsWith("cir")) {
                giftStep = GiftStepDefault
            }
            parserGiftStep()
        }
    }

    public fun parserBannerStep() {
        parserStep(bannerStep, bannerStepCicle, bannerStepRe)
    }

    public fun parserRectStep() {
        parserStep(rectStep, rectStepList, null)
    }

    public fun parserCollapseStep() {
        parserStep(clStep, clStepList, null)
    }

    public fun parserNativeStep() {
        parserStep(nativeStep, nativeStepList, null)
    }

    public fun parserFullStep() {
        parserStep(fullStep, fullStepCicle, fullStepRe)
    }

    public fun parserFull2Step() {
        parserStep(full2Step, full2StepCicle, full2StepRe)
    }

    public fun parserGiftStep() {
        parserStep(giftStep, giftStepCicle, giftStepRe)
    }

    public fun parserStep(steps: String, listCir: MutableList<Int>, listre: MutableList<Int>?) {
        if (steps.isEmpty()) {
            return
        }
        val typeSteps = steps.split("#")
        if (typeSteps.isNotEmpty()) {
            for (itemType in typeSteps) {
                if (itemType.startsWith("cir:")) {
                    listCir.clear()
                    val cirStep = itemType.substring(4)
                    val cirList = cirStep.split(",")
                    for (itemCir in cirList) {
                        val va = itemCir.toIntOrNull()
                        if (va != null) {
                            listCir.add(va)
                        }
                    }
                } else if (itemType.startsWith("re:")) {
                    if (listre != null) {
                        listre.clear()
                        val reStep = itemType.substring(3)
                        val reList = reStep.split(",")
                        for (itemre in reList) {
                            val va = itemre.toIntOrNull()
                            if (va != null) {
                                listre.add(va)
                            }
                        }
                    }
                }
            }
        }
    }
}