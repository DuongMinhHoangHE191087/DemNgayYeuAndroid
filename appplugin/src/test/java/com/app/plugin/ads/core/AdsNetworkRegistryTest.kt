package com.app.plugin.ads.core

import com.app.plugin.ads.AdsBase
import com.app.plugin.ads.AdsBaseBanner
import com.app.plugin.ads.AdsBaseFull
import com.app.plugin.ads.AdsBaseGift
import com.app.plugin.ads.AdsBaseNative
import com.app.plugin.ads.AdsIDDef
import com.app.plugin.ads.adsfb.AdsFbMy
import com.app.plugin.ads.adsmax.AdsMaxMy
import com.app.plugin.ads.adsmob.AdsMobMy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.withSettings

/**
 * 1.5.0 Task 13: "Kiểm tương đương thêm" của brief. `AdsMobMy`/`AdsMaxMy`/`AdsFbMy` là singleton thật, nhưng map
 * mạng của chúng chỉ được nạp trong `initAd(context)` — không unit test nào trong module gọi hàm đó (cần GMA thật),
 * nên trong môi trường test chúng luôn rỗng: `build` không ném và trả về mọi map/list rỗng ("mạng giả không có
 * quảng cáo").
 *
 * 1.5.0 Task 14 bước A (review Task 13 m2): test thứ tự đặt 24 mock có tên vào map của ba singleton và khoá thứ tự
 * 24 lần `add` của `initAdsHelper` cũ — thứ tự [AdsNetworkRegistry.all] quyết định thứ tự
 * `releaseActivity`/`clearActivity`/`checkLoadAfterGetCf` của façade.
 */
class AdsNetworkRegistryTest {
    @Test
    fun `build khong nem va tra ve moi map rong khi khong mang nao co quang cao`() {
        val r: AdsNetworkRegistry = AdsNetworkRegistry.build()

        assertTrue(r.banners.isEmpty())
        assertTrue(r.ntCls.isEmpty())
        assertTrue(r.rects.isEmpty())
        assertTrue(r.natives.isEmpty())
        assertTrue(r.fulls.isEmpty())
        assertTrue(r.gifts.isEmpty())
        assertTrue(r.all.isEmpty())
    }

    private inline fun <reified T : AdsBase> ad(name: String): T = mock(T::class.java, withSettings().name(name))

    /** Sáu map mạng của một singleton, theo tên field (dùng chung cho chụp/khôi phục). */
    private val slots = listOf("bannerAds", "ntClAds", "rectAds", "nativeAds", "fullAds", "giftAds")

    private fun fieldOf(owner: Any, name: String) =
        owner.javaClass.getDeclaredField(name).apply { isAccessible = true }

    private fun snapshot(owner: Any): Map<String, Any?> = slots.associateWith { fieldOf(owner, it).get(owner) }

    private fun restore(owner: Any, saved: Map<String, Any?>) =
        saved.forEach { (name, value) -> fieldOf(owner, name).set(owner, value) }

    @Test
    fun `build giu dung thu tu 24 lan add cua initAdsHelper cu va dung khoa tung map`() {
        val mob = AdsMobMy.instance
        val max = AdsMaxMy.instance
        val fb = AdsFbMy.instance
        val saved = listOf(mob, max, fb).associateWith { snapshot(it) }
        try {
            val mobBn = ad<AdsBaseBanner>("mobBn"); val maxBn = ad<AdsBaseBanner>("maxBn"); val fbBn = ad<AdsBaseBanner>("fbBn")
            val mobCl = ad<AdsBaseBanner>("mobCl"); val maxCl = ad<AdsBaseBanner>("maxCl"); val fbCl = ad<AdsBaseBanner>("fbCl")
            val mobRc = ad<AdsBaseBanner>("mobRc"); val maxRc = ad<AdsBaseBanner>("maxRc"); val fbRc = ad<AdsBaseBanner>("fbRc")
            val mobNt = ad<AdsBaseNative>("mobNt"); val maxNt = ad<AdsBaseNative>("maxNt"); val fbNt = ad<AdsBaseNative>("fbNt")
            val mobFi = ad<AdsBaseFull>("mobFi"); val mobFl = ad<AdsBaseFull>("mobFl")
            val mobFo = ad<AdsBaseFull>("mobFo"); val mobFn = ad<AdsBaseFull>("mobFn")
            val maxFi = ad<AdsBaseFull>("maxFi"); val maxFn = ad<AdsBaseFull>("maxFn"); val maxFo = ad<AdsBaseFull>("maxFo")
            val fbFi = ad<AdsBaseFull>("fbFi"); val fbFn = ad<AdsBaseFull>("fbFn")
            val mobRw = ad<AdsBaseGift>("mobRw"); val maxRw = ad<AdsBaseGift>("maxRw"); val fbRw = ad<AdsBaseGift>("fbRw")

            // AdsMobMy/AdsMaxMy: field `var` public → gán thẳng.
            mob.bannerAds = mutableMapOf(AdsIDDef.AdmobBanner to mobBn)
            mob.ntClAds = mutableMapOf(AdsIDDef.AdmobNtCl to mobCl)
            mob.rectAds = mutableMapOf(AdsIDDef.AdmobRect to mobRc)
            mob.nativeAds = mutableMapOf(AdsIDDef.AdmobNative to mobNt)
            // Thứ tự chèn trong map nguồn cố ý khác thứ tự add: build phải theo chuỗi getAds*, không theo map.
            mob.fullAds = mutableMapOf(
                AdsIDDef.AdmobFullNt to mobFn, AdsIDDef.AdmobFullOpen to mobFo,
                AdsIDDef.AdmobFullLow to mobFl, AdsIDDef.AdmobFullInters to mobFi,
            )
            mob.giftAds = mutableMapOf(AdsIDDef.AdmobRewarded to mobRw)

            max.bannerAds = mutableMapOf(AdsIDDef.MaxBanner to maxBn)
            max.ntClAds = mutableMapOf(AdsIDDef.MaxNtCl to maxCl)
            max.rectAds = mutableMapOf(AdsIDDef.MaxRect to maxRc)
            max.nativeAds = mutableMapOf(AdsIDDef.MaxNative to maxNt)
            max.fullAds = mutableMapOf(
                AdsIDDef.MaxFullOpen to maxFo, AdsIDDef.MaxFullNt to maxFn, AdsIDDef.MaxFullInters to maxFi,
            )
            max.giftAds = mutableMapOf(AdsIDDef.MaxRewarded to maxRw)

            // AdsFbMy: field `private` → gán bằng reflection.
            fieldOf(fb, "bannerAds").set(fb, mutableMapOf(AdsIDDef.FBBanner to fbBn))
            fieldOf(fb, "ntClAds").set(fb, mutableMapOf(AdsIDDef.FBNtCl to fbCl))
            fieldOf(fb, "rectAds").set(fb, mutableMapOf(AdsIDDef.FBRect to fbRc))
            fieldOf(fb, "nativeAds").set(fb, mutableMapOf(AdsIDDef.FBNative to fbNt))
            fieldOf(fb, "fullAds").set(fb, mutableMapOf(AdsIDDef.FBFullNt to fbFn, AdsIDDef.FBFullInters to fbFi))
            fieldOf(fb, "giftAds").set(fb, mutableMapOf(AdsIDDef.FBRewarded to fbRw))

            val r = AdsNetworkRegistry.build()

            // banner, NtCl, rect, native: AdMob → MAX → Meta; full: AdMob Inters, Low, Open, Nt → MAX Inters, Nt,
            // Open → Meta Inters, Nt; gift: AdMob → MAX → Meta.
            val expectedOrder = listOf(
                mobBn, maxBn, fbBn, mobCl, maxCl, fbCl, mobRc, maxRc, fbRc, mobNt, maxNt, fbNt,
                mobFi, mobFl, mobFo, mobFn, maxFi, maxFn, maxFo, fbFi, fbFn,
                mobRw, maxRw, fbRw,
            )
            assertEquals(24, r.all.size)
            assertEquals(expectedOrder.map { it.toString() }, r.all.map { it.toString() })
            assertEquals(
                mapOf(AdsIDDef.AdmobBanner to mobBn, AdsIDDef.MaxBanner to maxBn, AdsIDDef.FBBanner to fbBn),
                r.banners,
            )
            assertEquals(
                mapOf(AdsIDDef.AdmobNtCl to mobCl, AdsIDDef.MaxNtCl to maxCl, AdsIDDef.FBNtCl to fbCl),
                r.ntCls,
            )
            assertEquals(
                mapOf(AdsIDDef.AdmobRect to mobRc, AdsIDDef.MaxRect to maxRc, AdsIDDef.FBRect to fbRc),
                r.rects,
            )
            assertEquals(
                mapOf(AdsIDDef.AdmobNative to mobNt, AdsIDDef.MaxNative to maxNt, AdsIDDef.FBNative to fbNt),
                r.natives,
            )
            assertEquals(
                mapOf(
                    AdsIDDef.AdmobFullInters to mobFi, AdsIDDef.AdmobFullLow to mobFl,
                    AdsIDDef.AdmobFullOpen to mobFo, AdsIDDef.AdmobFullNt to mobFn,
                    AdsIDDef.MaxFullInters to maxFi, AdsIDDef.MaxFullNt to maxFn, AdsIDDef.MaxFullOpen to maxFo,
                    AdsIDDef.FBFullInters to fbFi, AdsIDDef.FBFullNt to fbFn,
                ),
                r.fulls,
            )
            assertEquals(
                mapOf(AdsIDDef.AdmobRewarded to mobRw, AdsIDDef.MaxRewarded to maxRw, AdsIDDef.FBRewarded to fbRw),
                r.gifts,
            )
        } finally {
            saved.forEach { (owner, s) -> restore(owner, s) }
        }
    }

    /** 1.9.0 Task 2 W15: rewarded interstitial AdMob vào `gifts` (khoá `AdmobRwi`) và cuối `all`, sau rewarded. */
    @Test
    fun `W15 build dua AdmobRwi vao gifts va all`() {
        val mob = AdsMobMy.instance
        val saved = snapshot(mob)
        try {
            val mobRw = ad<AdsBaseGift>("mobRw"); val mobRwi = ad<AdsBaseGift>("mobRwi")
            mob.giftAds = mutableMapOf(AdsIDDef.AdmobRwi to mobRwi, AdsIDDef.AdmobRewarded to mobRw)

            val r = AdsNetworkRegistry.build()

            assertEquals(mapOf(AdsIDDef.AdmobRewarded to mobRw, AdsIDDef.AdmobRwi to mobRwi), r.gifts)
            assertEquals(listOf("mobRw", "mobRwi"), r.all.map { it.toString() })
        } finally {
            restore(mob, saved)
        }
    }
}
