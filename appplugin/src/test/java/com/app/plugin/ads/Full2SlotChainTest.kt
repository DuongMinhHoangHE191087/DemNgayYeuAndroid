package com.app.plugin.ads

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import com.app.plugin.app.AppPluginManager
import com.app.plugin.brain.LicenseTestRule
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockingDetails
import org.mockito.Mockito.`when`

/**
 * 2.4.2 FIXED-b (Hợp đồng A, sign-off đk 2): chuỗi interstitial → Full2 qua `AdsHelper` thật, mạng native full là
 * [AdsBaseFull] thật (không ghi đè `getAdsForPl`/`showAd`/`loadAd`), slot là mock [AdsPlBase] trong `mapAdsPl`. Full2
 * `full_2_<pl>` chọn slot 3 bậc của SLOT (`full_2_<pl>` → `full_2_default` → `full_default`) cho cả load
 * (`beginFull2Transition`) lẫn show (dismiss của cha), và slot hiện dưới tên `full_2_<pl>` (tên `ad_show`/paid của slot).
 */
class Full2SlotChainTest {
    @get:Rule val license = LicenseTestRule()

    /** Interstitial có quảng cáo sẵn; dismiss bằng tay. */
    private class Inter(context: Context) : AdsBaseFull(AdsIDDef.AdmobFullInters, context) {
        override val eventTag: String get() = "t242_fxb_inter"
        private val pl = mock(AdsPlBase::class.java).also { `when`(it.hasLoadedAd()).thenReturn(true) }
        private var showing: Pair<String, AdsShowCb>? = null
        override fun getNewAdsPl(placement: String): AdsPlBase? = null
        override fun getKeyForDataSave(): String = "t242_fxb_inter_data"
        override fun getKeyForResDf(): String = "t242_fxb_inter_res"
        override fun getAdsForPl(placement: String): AdsPlBase = pl
        override fun loadAd(placement: String, adsLoadCb: AdsLoadInnerCb) = Unit
        override fun showAd(placement: String, adsShowCb: AdsShowCb, flagShow: Int): Boolean {
            showing = placement to adsShowCb
            return true
        }
        fun dismiss() = showing!!.let { (p, cb) -> showing = null; cb.onAdDismissed(p, p, "t") }
    }

    /** Native full thật: chỉ slot trong `mapAdsPl` quyết định đường load/show. */
    private class NtFull(context: Context) : AdsBaseFull(AdsIDDef.AdmobFullNt, context) {
        override val eventTag: String get() = "t242_fxb_ntfull"
        override fun getNewAdsPl(placement: String): AdsPlBase? = null
        override fun getKeyForDataSave(): String = "t242_fxb_ntfull_data"
        override fun getKeyForResDf(): String = "t242_fxb_ntfull_res"
    }

    private class Cb : AdsShowCb {
        override fun onAdShowFail(placement: String?, originPl: String?, adId: String?, err: String?) = Unit
        override fun onAdShowed(placement: String?, originPl: String?, adId: String?, flag: Int) = Unit
    }

    /** Slot có quảng cáo sẵn, `show` hiện được. */
    private fun slot(): AdsPlBase = mock(AdsPlBase::class.java).also {
        `when`(it.hasLoadedAd()).thenReturn(true)
        `when`(it.holdsLoadedAd()).thenReturn(true)
        `when`(it.show(anyString(), any(), anyInt())).thenReturn(true)
    }

    private val sessionField = AppPluginManager::class.java.getDeclaredField("sessionCount").apply { isAccessible = true }
    private var savedSession = 0

    @Before fun setUp() {
        savedSession = sessionField.get(AppPluginManager.instance) as Int
        sessionField.set(AppPluginManager.instance, 2)
    }

    @After fun tearDown() { sessionField.set(AppPluginManager.instance, savedSession) }

    /** Phiên 2, host resume, `full2FlagShow` 1: interstitial `full_<pl>` hiện rồi đóng; Full2 từ [slots] của native full. */
    private fun chain(pl: String, vararg slots: Pair<String, AdsPlBase>) = MainHandlerTimers.run(honorRemoveAll = true) { _ ->
        val h = AdsKillSwitchTest.newAdsHelper()
        h.currConfig.apply {
            full2FlagShow = 1
            fullStepCicle.clear(); fullStepCicle.add(AdsIDDef.AdmobFullInters); fullStepRe.clear()
            full2StepCicle.clear(); full2StepCicle.add(AdsIDDef.AdmobFullNt); full2StepRe.clear()
        }
        val ctx = context()
        val inter = Inter(ctx)
        val nt = NtFull(ctx).apply { mapAdsPl = mutableMapOf(*slots) }
        set(h, "listAdsFull", mutableMapOf<Int, AdsBaseFull>(AdsIDDef.AdmobFullInters to inter, AdsIDDef.AdmobFullNt to nt))
        set(h, "listAdsAll", mutableListOf<AdsBase>())
        set(h, "mActivity", mock(Activity::class.java))
        (get(h, "fullScreenAdPolicy") as FullScreenAdPolicy).onHostActivityResumed()
        assertTrue(h.showInterstitial(pl, 0, Cb(), isDeltaTime = false))
        inter.dismiss()
    }

    @Test fun `FX9 ntfull co full_default va full_2_default, full_home khong slot rieng - Full2 load va show tu full_2_default ten full_2_home`() {
        val def = slot()
        val f2 = slot()

        chain("home", PlacementDefault.PLFullDefault to def, PlacementDefault.PLFull2Default to f2)

        assertEquals(
            "load (beginFull2Transition) vào slot full_2_default; full_2_default kéo theo khi cha nạp lại full_default",
            listOf("full_2_home", "full_2_default"), calls(f2, "load"),
        )
        assertEquals("show dưới tên full_2_home từ slot full_2_default", listOf("full_2_home"), calls(f2, "show"))
        assertEquals("slot full_default không dùng cho Full2", emptyList<String>(), calls(def, "load") + calls(def, "show"))
    }

    @Test fun `FX10 ntfull chi full_2_default va full_2_x (nhu IM) - full_y noi tu full_2_default, khong not pl config`() {
        val f2 = slot()
        val own = slot()

        chain("y", PlacementDefault.PLFull2Default to f2, "full_2_x" to own)

        assertEquals("load full_2_y vào slot full_2_default (không not pl config)", listOf("full_2_y", "full_2_default"), calls(f2, "load"))
        assertEquals("show full_2_y từ slot full_2_default", listOf("full_2_y"), calls(f2, "show"))
        assertEquals("slot full_2_x không dính", emptyList<String>(), calls(own, "load") + calls(own, "show"))
    }

    private companion object {
        /** Placement (tham số đầu) của mọi lời gọi [method] trên slot mock. */
        fun calls(slot: AdsPlBase, method: String): List<String> =
            mockingDetails(slot).invocations.filter { it.method.name == method }.map { it.arguments[0] as String }

        fun context(): Context = mock(Context::class.java).also { c ->
            val res = mock(Resources::class.java)
            `when`(res.getIdentifier(anyString(), anyString(), anyString())).thenReturn(0)
            `when`(c.resources).thenReturn(res)
            `when`(c.getSharedPreferences(anyString(), anyInt())).thenReturn(mock(SharedPreferences::class.java))
        }

        fun set(helper: AdsHelper, name: String, value: Any?) {
            AdsHelper::class.java.getDeclaredField(name).apply { isAccessible = true }.set(helper, value)
        }

        fun get(helper: AdsHelper, name: String): Any? =
            AdsHelper::class.java.getDeclaredField(name).apply { isAccessible = true }.get(helper)
    }
}
