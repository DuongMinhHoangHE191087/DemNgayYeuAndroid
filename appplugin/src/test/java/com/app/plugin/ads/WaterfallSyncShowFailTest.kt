package com.app.plugin.ads

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import com.app.plugin.ads.adsfb.AdsFbPlNtFull
import com.app.plugin.ads.adsmob.AdsMobPlNtFull
import com.app.plugin.ads.adsmob.AdsMobPlrewarded
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/**
 * Final review Important 1 (2026-09-24): mạng có quảng cáo mà không hiện được (native full trên host
 * đã huỷ, `Dialog.show()` ném BadToken, rewarded khi chưa có Activity...) gọi `onAdShowFail` ĐỒNG BỘ
 * trong `showAd()` rồi trả `false`. Vòng app open / interstitial / rewarded bắt đầu bộ đếm ở -2, nên
 * lỗi đầu tiên tới app ngay, rồi vòng đi tiếp và mạng sau vẫn hiện: app thấy fail → showed →
 * dismissed (doc 64 D quay lại).
 *
 * Hợp đồng (CHANGELOG 1.3.1): app không nhận `onAdShowFail` khi vòng còn mạng khác; không mạng nào
 * hiện được thì app nhận đúng một `onAdShowFail`, ở cuối vòng. Placement show trực tiếp, ngoài vòng,
 * vẫn báo đúng một `onAdShowFail` như trước.
 *
 * Dùng lớp thật [AdsMobPlNtFull] / [AdsFbPlNtFull] / [AdsMobPlrewarded]; chỉ mạng hiện được là giả.
 */
class WaterfallSyncShowFailTest {

    /** Ghi mọi callback app nhận, theo thứ tự. */
    private class RecordingShowCb : AdsGiftShowCb {
        val events = mutableListOf<String>()
        override fun onAdShowFail(placement: String?, originPl: String?, adId: String?, err: String?) {
            events += "fail:$err"
        }
        override fun onAdShowed(placement: String?, originPl: String?, adId: String?, flag: Int) {
            events += "showed"
        }
        override fun onAdDismissed(placement: String?, originPl: String?, adId: String?) {
            events += "dismissed"
        }
    }

    /** Mạng thật với đúng một placement thật; load bị chặn vì SDK mạng không chạy trong JVM test. */
    private class PlNetwork(context: Context, type: Int, private val pl: AdsPlBase) : AdsBaseFull(type, context) {
        override val eventTag: String get() = "test_net"
        override fun getNewAdsPl(placement: String): AdsPlBase? = null
        override fun getKeyForDataSave(): String = "test_pl_${adsType}_data"
        override fun getKeyForResDf(): String = "test_pl_${adsType}_res"
        override fun getAdsForPl(placement: String): AdsPlBase = pl
        override fun loadAd(placement: String, adsLoadCb: AdsLoadInnerCb) = Unit
    }

    private class GiftPlNetwork(context: Context, type: Int, private val pl: AdsPlBase) : AdsBaseGift(type, context) {
        override val eventTag: String get() = "test_net"
        override fun getNewAdsPl(placement: String): AdsPlBase? = null
        override fun getKeyForDataSave(): String = "test_giftpl_${adsType}_data"
        override fun getKeyForResDf(): String = "test_giftpl_${adsType}_res"
        override fun getAdsForPl(placement: String): AdsPlBase = pl
        override fun loadAd(placement: String, adsLoadCb: AdsLoadInnerCb) = Unit
    }

    /** Mạng có quảng cáo sẵn: hiện được, báo onAdShowed; [dismiss] như người dùng bấm đóng. */
    private class ShowingNetwork(context: Context, type: Int) : AdsBaseFull(type, context) {
        override val eventTag: String get() = "test_net"
        private var showing: AdsShowCb? = null
        override fun getNewAdsPl(placement: String): AdsPlBase? = null
        override fun getKeyForDataSave(): String = "test_showing_${adsType}_data"
        override fun getKeyForResDf(): String = "test_showing_${adsType}_res"
        override fun loadAd(placement: String, adsLoadCb: AdsLoadInnerCb) = Unit
        override fun showAd(placement: String, adsShowCb: AdsShowCb, flagShow: Int): Boolean {
            showing = adsShowCb
            adsShowCb.onAdShowed(placement, placement, "test", flagShow)
            return true
        }
        fun dismiss() = showing!!.onAdDismissed("full_home", "full_home", "test")
    }

    private class ShowingGiftNetwork(context: Context, type: Int) : AdsBaseGift(type, context) {
        override val eventTag: String get() = "test_net"
        private var showing: AdsShowCb? = null
        override fun getNewAdsPl(placement: String): AdsPlBase? = null
        override fun getKeyForDataSave(): String = "test_showing_gift_data"
        override fun getKeyForResDf(): String = "test_showing_gift_res"
        override fun loadAd(placement: String, adsLoadCb: AdsLoadInnerCb) = Unit
        override fun showAd(placement: String, adsShowCb: AdsShowCb, flagShow: Int): Boolean {
            showing = adsShowCb
            adsShowCb.onAdShowed(placement, placement, "test", flagShow)
            return true
        }
        fun dismiss() = showing!!.onAdDismissed("gift_home", "gift_home", "test")
    }

    /** Mạng báo onAdShowFail ngay trong showAd() mà vẫn trả true. */
    private class FailsButReturnsTrueNetwork(context: Context, type: Int) : AdsBaseFull(type, context) {
        override val eventTag: String get() = "test_net"
        override fun getNewAdsPl(placement: String): AdsPlBase? = null
        override fun getKeyForDataSave(): String = "test_failtrue_data"
        override fun getKeyForResDf(): String = "test_failtrue_res"
        override fun loadAd(placement: String, adsLoadCb: AdsLoadInnerCb) = Unit
        override fun showAd(placement: String, adsShowCb: AdsShowCb, flagShow: Int): Boolean {
            adsShowCb.onAdShowFail(placement, placement, "test", "sync fail")
            return true
        }
    }

    private lateinit var context: Context
    private lateinit var helper: AdsHelper

    private fun setField(name: String, value: Any?) {
        AdsHelper::class.java.getDeclaredField(name).apply { isAccessible = true }.set(helper, value)
    }

    private fun slot(pl: AdsPlBase) =
        AdsPlBase::class.java.getDeclaredField("adsPlacementEcmp").apply { isAccessible = true }.get(pl)
            as AdsPlacementEcmpBase

    /** Đặt một quảng cáo đã load vào placement thật, như sau onAdLoaded của mạng. */
    private fun giveLoadedAd(pl: AdsPlBase, ad: Any) {
        val ecmps = slot(pl).adEcmps!!
        ecmps.list.add(AdsEcmp(ecmps).apply { adsId = "unit-${pl.javaClass.simpleName}"; isLoaded = true; adObject = ad })
    }

    private fun deadHost(): Activity = mock(Activity::class.java).also { `when`(it.isDestroyed).thenReturn(true) }

    /** Native full AdMob thật, có quảng cáo, host đã huỷ (xoay màn hình giữa load và show). */
    private fun mobNtFullOnDeadHost(): AdsMobPlNtFull =
        AdsMobPlNtFull(context, mock(AdsBase::class.java), AdsIDDef.AdmobFullNt, "full_default").apply {
            giveLoadedAd(this, mock(com.google.android.gms.ads.nativead.NativeAd::class.java))
            setActivity(deadHost())
        }

    private fun fbNtFullOnDeadHost(): AdsFbPlNtFull =
        AdsFbPlNtFull(context, mock(AdsBase::class.java), AdsIDDef.FBFullNt, "full_default").apply {
            giveLoadedAd(this, mock(com.facebook.ads.NativeAd::class.java))
            setActivity(deadHost())
        }

    @Before
    fun setUp() {
        context = mock(Context::class.java)
        val resources = mock(Resources::class.java)
        `when`(resources.getIdentifier(anyString(), anyString(), anyString())).thenReturn(0)
        `when`(context.resources).thenReturn(resources)
        `when`(context.getSharedPreferences(anyString(), anyInt())).thenReturn(mock(SharedPreferences::class.java))
        helper = AdsHelper::class.java.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
        helper.currConfig = AdsConfigOb(mock(Context::class.java)).apply {
            full2FlagShow = 0
            giftDeltaTime = 0
        }
        helper.placementsConfigManager = AdsPlacementConfigManager()
    }

    @Test
    fun `interstitial - native full host da huy dung truoc mang hien duoc - app khong nhan onAdShowFail`() {
        val showing = ShowingNetwork(context, AdsIDDef.AdmobFullLow)
        setField(
            "listAdsFull",
            mutableMapOf<Int, AdsBaseFull>(
                AdsIDDef.AdmobFullNt to PlNetwork(context, AdsIDDef.AdmobFullNt, mobNtFullOnDeadHost()),
                AdsIDDef.AdmobFullLow to showing,
            )
        )
        val cb = RecordingShowCb()

        // flagImage 3: AdmobFullNt, FBFullNt, MaxFullNt rồi AdmobFullLow.
        val shown = helper.showInterstitial("home", 3, cb, isDeltaTime = false)
        showing.dismiss()

        assertTrue("mạng sau phải hiện", shown)
        assertEquals(listOf("showed", "dismissed"), cb.events)
        assertFalse("cờ full-screen phải về 0 sau dismiss", helper.isFullShowing())
    }

    @Test
    fun `interstitial - moi mang deu fail - app nhan dung mot onAdShowFail o cuoi vong`() {
        setField(
            "listAdsFull",
            mutableMapOf<Int, AdsBaseFull>(
                AdsIDDef.AdmobFullNt to PlNetwork(context, AdsIDDef.AdmobFullNt, mobNtFullOnDeadHost()),
                AdsIDDef.FBFullNt to PlNetwork(context, AdsIDDef.FBFullNt, fbNtFullOnDeadHost()),
            )
        )
        val cb = RecordingShowCb()

        val shown = helper.showInterstitial("home", 3, cb, isDeltaTime = false)

        assertFalse(shown)
        assertEquals(listOf("fail:show_when_act_null"), cb.events)
        assertFalse("không hiện thì cờ full-screen phải là 0", helper.isFullShowing())
    }

    @Test
    fun `app open - native full host da huy dung truoc mang hien duoc - app khong nhan onAdShowFail`() {
        helper.currConfig.apply {
            openAdFirstType = 1
            openAdFirstImg = 2
            openAdtype = 1
            openAdFlagImg = 2
            fullStepCicle.clear()
            fullStepCicle.add(AdsIDDef.AdmobFullLow)
            fullStepRe.clear()
        }
        val showing = ShowingNetwork(context, AdsIDDef.AdmobFullLow)
        setField(
            "listAdsFull",
            mutableMapOf<Int, AdsBaseFull>(
                AdsIDDef.AdmobFullNt to PlNetwork(context, AdsIDDef.AdmobFullNt, mobNtFullOnDeadHost()),
                AdsIDDef.AdmobFullLow to showing,
            )
        )
        val cb = RecordingShowCb()

        // openAdFirstImg 2: AdmobFullNt, FBFullNt, MaxFullNt rồi fullStepCicle.
        val shown = helper.showOpenAd(PlacementDefault.PLFullSplash, cb)
        showing.dismiss()

        assertTrue("mạng sau phải hiện", shown)
        assertEquals(listOf("showed", "dismissed"), cb.events)
        assertFalse("cờ full-screen phải về 0 sau dismiss", helper.isFullShowing())
    }

    @Test
    fun `rewarded - mang dau fail dong bo, mang sau hien duoc - app khong nhan onAdShowFail`() {
        helper.currConfig.giftStepCicle.apply {
            clear()
            add(AdsIDDef.AdmobRewarded)
            add(AdsIDDef.MaxRewarded)
        }
        helper.currConfig.giftStepRe.clear()
        // Rewarded AdMob thật, có quảng cáo, chưa có Activity: show() báo "activity null" rồi trả false.
        val mobGift = AdsMobPlrewarded(context, mock(AdsBase::class.java), AdsIDDef.AdmobRewarded, "gift_default")
        giveLoadedAd(mobGift, mock(com.google.android.gms.ads.rewarded.RewardedAd::class.java))
        val showing = ShowingGiftNetwork(context, AdsIDDef.MaxRewarded)
        setField(
            "listAdsGift",
            mutableMapOf<Int, AdsBaseGift>(
                AdsIDDef.AdmobRewarded to GiftPlNetwork(context, AdsIDDef.AdmobRewarded, mobGift),
                AdsIDDef.MaxRewarded to showing,
            )
        )
        val cb = RecordingShowCb()

        val shown = helper.showRewarded("home", cb)
        showing.dismiss()

        assertTrue("mạng sau phải hiện", shown)
        assertEquals(listOf("showed", "dismissed"), cb.events)
        assertFalse("cờ full-screen phải về 0 sau dismiss", helper.isFullShowing())
    }

    @Test
    fun `mang bao fail dong bo ma van tra true - app nhan dung mot onAdShowFail, co full-screen ve 0`() {
        setField(
            "listAdsFull",
            mutableMapOf<Int, AdsBaseFull>(AdsIDDef.AdmobFullLow to FailsButReturnsTrueNetwork(context, AdsIDDef.AdmobFullLow))
        )
        val cb = RecordingShowCb()

        helper.showInterstitial("home", 2, cb, isDeltaTime = false)

        assertEquals(listOf("fail:sync fail"), cb.events)
        assertFalse("không có dismiss nào sẽ tới: cờ full-screen không được kẹt ở 1", helper.isFullShowing())
    }

    @Test
    fun `native full show truc tiep ngoai vong - van dung mot onAdShowFail`() {
        val network = PlNetwork(context, AdsIDDef.AdmobFullNt, mobNtFullOnDeadHost())
        val cb = RecordingShowCb()

        val shown = network.showAd("full_home", cb)

        assertFalse(shown)
        assertEquals(listOf("fail:show_when_act_null"), cb.events)
    }
}
