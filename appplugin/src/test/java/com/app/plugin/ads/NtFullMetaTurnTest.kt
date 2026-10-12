package com.app.plugin.ads

import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.SharedPreferences
import android.content.res.Resources
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import com.app.plugin.R
import com.app.plugin.ads.adsfb.AdsFbMy
import com.app.plugin.ads.adsfb.AdsFbNtFullView
import com.app.plugin.ads.adsfb.AdsFbPlNtFull
import com.app.plugin.logevent.ServerEventCapture
import com.facebook.ads.MediaView
import com.facebook.ads.NativeAd
import com.facebook.ads.NativeAdBase
import com.facebook.ads.NativeAdListener
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockConstruction
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

/**
 * 2.2.0 PSH (N07, R-P3, D17-220, Q-A): native full Meta (`AdsFbPlNtFull`) — click (`onAdClicked`) và impression
 * (`onLoggingImpression`, kèm doanh thu `PaidEvent.metaDirect`) tới muộn của quảng cáo lượt trước đi tới callback của
 * **đường (show/get) của chính lượt đó**, không theo `ecmpLoad.flagShow` hiện hành của slot — như click native full
 * AdMob ở 2.1.0 P3 (`NtFullTurnPlacementTest` C1). Kịch bản: X lấy/hiện ở lượt 1, slot nạp Y và hiện/lấy ở lượt 2 theo
 * đường kia, callback muộn của X tới sau đó.
 */
class NtFullMetaTurnTest {

    private class RecordingShowCb : AdsShowCb {
        val clicks = mutableListOf<String?>()
        val impressions = mutableListOf<String?>()
        override fun onAdClick(placement: String?, originPl: String?, adId: String?) { clicks += placement }
        override fun onAdImpression(placement: String?, originPl: String?, adId: String?) { impressions += placement }
    }

    private class NoLoadCb : AdsLoadInnerCb {
        override fun onLoaded(placement: String, originPl: String, adId: String, ecmp: AdsEcmp?, adspl: AdsPlBase?) = Unit
        override fun onLoadFailed(placement: String, originPl: String, adId: String, error: String) = Unit
    }

    /** Quảng cáo X của lượt 1, listener của lượt nạp ra nó và các dialog đã dựng (lượt show). */
    private class FirstTurn(val ad: NativeAd, val listener: NativeAdListener, val dialogs: List<AdsFbNtFullView>)

    private lateinit var context: Context
    private val activity = mock(Activity::class.java)

    @Before
    fun setUp() {
        context = mock(Context::class.java)
        val resources = mock(Resources::class.java)
        `when`(resources.getIdentifier(anyString(), anyString(), anyString())).thenReturn(0)
        `when`(context.resources).thenReturn(resources)
        `when`(context.getSharedPreferences(anyString(), anyInt())).thenReturn(mock(SharedPreferences::class.java))
        AdsFbMy.instance.readyQueue.resetForTest(); AdsFbMy.instance.readyQueue.markReady()
    }

    @After
    fun tearDown() = AdsFbMy.instance.readyQueue.resetForTest()

    /**
     * Hai lượt trên cùng slot native full Meta: lượt 1 nạp X ở `<p>_home` theo đường [get] (get = `getAdForShow`, như
     * `NativeController`; show = dialog, đóng X trước lượt 2), lượt 2 nạp Y ở `<p>_settings` theo đường [secondGet] với
     * callback [cb2]. [check] nhận X.
     */
    private fun twoMetaTurns(
        get: Boolean, cb: RecordingShowCb, secondGet: Boolean, cb2: RecordingShowCb, check: (FirstTurn) -> Unit
    ) = MainHandlerTimers.run { _ ->
        val listeners = mutableListOf<NativeAdListener>()
        mockConstruction(NativeAd::class.java) { ad, _ ->
            val builder = mock(NativeAdBase.NativeAdLoadConfigBuilder::class.java)
            `when`(ad.buildLoadAdConfig()).thenReturn(builder)
            `when`(builder.withAdListener(any())).thenAnswer { listeners += it.arguments[0] as NativeAdListener; builder }
        }.use { built ->
            mockConstruction(AdsFbNtFullView::class.java).use { dialogs ->
                mockStatic(LayoutInflater::class.java).use { inflaters ->
                    mockStatic(NativeAdBase.NativeComponentTag::class.java).use { _ -> // tagView chạy dynamic loader của Meta
                        val inflater = detachedInflater()
                        inflaters.`when`<LayoutInflater> { LayoutInflater.from(any()) }.thenReturn(inflater)
                        val pl = AdsFbPlNtFull(context, mock(AdsBase::class.java), AdsIDDef.FBFullNt, "full_nt_default")
                        pl.setActivity(activity)
                        pl.parserEcpms("fb_psh_unit")
                        pl.load("${prefix(get)}_home", NoLoadCb())
                        val x = built.constructed()[0]
                        listeners[0].onAdLoaded(x)
                        turn(pl, "${prefix(get)}_home", cb, get)
                        if (!get) {
                            val dismiss = ArgumentCaptor.forClass(DialogInterface.OnDismissListener::class.java)
                            verify(dialogs.constructed()[0]).setOnDismissListener(dismiss.capture())
                            dismiss.value.onDismiss(dialogs.constructed()[0])
                        }
                        pl.load("${prefix(secondGet)}_settings", NoLoadCb())
                        assertEquals("lượt 2 dựng quảng cáo mới", 2, built.constructed().size)
                        listeners[1].onAdLoaded(built.constructed()[1])
                        turn(pl, "${prefix(secondGet)}_settings", cb2, secondGet)
                        check(FirstTurn(x, listeners[0], dialogs.constructed()))
                    }
                }
            }
        }
    }

    private fun prefix(get: Boolean) = if (get) "nt" else "full"

    private fun turn(pl: AdsFbPlNtFull, placement: String, cb: RecordingShowCb, get: Boolean) {
        if (get) assertNotNull("get $placement", pl.getAdForShow(placement, cb))
        else assertTrue("show $placement", pl.show(placement, cb, 0))
    }

    /** `populateNativeAdView` trên JVM: layout tách rời, mỗi id trả đúng kiểu view; không AdChoices. */
    private fun detachedInflater(): LayoutInflater {
        val layout = mock(View::class.java)
        `when`(layout.findViewById<View>(anyInt())).thenAnswer {
            when (it.arguments[0] as Int) {
                R.id.fb_ad_choices_container -> null
                R.id.fb_native_ad_icon, R.id.fb_native_ad_media -> mock(MediaView::class.java)
                R.id.fb_native_ad_call_to_action -> mock(Button::class.java)
                else -> mock(TextView::class.java)
            }
        }
        val inflater = mock(LayoutInflater::class.java)
        `when`(inflater.inflate(anyInt(), any<ViewGroup>(), anyBoolean())).thenReturn(layout)
        return inflater
    }

    /** Impression muộn của X (kèm doanh thu Meta trực tiếp) dưới bắt sự kiện server. */
    private fun lateImpression(first: FirstTurn) = ServerEventCapture().use { first.listener.onLoggingImpression(first.ad) }

    @Test
    fun `C4b impression muon cua Meta NtFull luot truoc toi callback cua luot do, khong theo flagShow hien hanh`() {
        val getX = RecordingShowCb()
        val showY = RecordingShowCb()
        twoMetaTurns(get = true, cb = getX, secondGet = false, cb2 = showY, check = ::lateImpression)
        assertEquals("C4b: impression muộn của X (get) tới callback get của lượt X", listOf<String?>("nt_home"), getX.impressions)
        assertEquals("C4b: callback show của lượt Y không nhận impression của X", emptyList<String?>(), showY.impressions)

        val showX = RecordingShowCb()
        val getY = RecordingShowCb()
        twoMetaTurns(get = false, cb = showX, secondGet = true, cb2 = getY, check = ::lateImpression)
        assertEquals("C4b: impression muộn của X (show) tới callback show của lượt X", listOf<String?>("full_home"), showX.impressions)
        assertEquals("C4b: callback get của lượt Y không nhận impression của X", emptyList<String?>(), getY.impressions)
    }

    @Test
    fun `C4 click muon cua Meta NtFull luot truoc toi callback cua luot do, khong afterClick dialog cua luot sau`() {
        val getX = RecordingShowCb()
        val showY = RecordingShowCb()
        var dialogY: AdsFbNtFullView? = null
        twoMetaTurns(get = true, cb = getX, secondGet = false, cb2 = showY) { first ->
            dialogY = first.dialogs.single()
            first.listener.onAdClicked(first.ad)           // click muộn của X (get), sau khi slot đã hiện Y (show)
        }
        assertEquals("C4: click muộn của X (get) tới callback get của lượt X", listOf<String?>("nt_home"), getX.clicks)
        assertEquals("C4: callback show của lượt Y không nhận click của X", emptyList<String?>(), showY.clicks)
        verify(dialogY!!, never()).afterClick()

        val showX = RecordingShowCb()
        val getY = RecordingShowCb()
        twoMetaTurns(get = false, cb = showX, secondGet = true, cb2 = getY) { first -> first.listener.onAdClicked(first.ad) }
        assertEquals("C4: click muộn của X (show) tới callback show của lượt X", listOf<String?>("full_home"), showX.clicks)
        assertEquals("C4: callback get của lượt Y không nhận click của X", emptyList<String?>(), getY.clicks)
    }
}
