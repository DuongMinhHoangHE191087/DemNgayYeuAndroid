package com.app.plugin.ads

import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.SharedPreferences
import android.content.res.Resources
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.app.plugin.ads.adsmob.AdsMobMy
import com.app.plugin.ads.adsmob.AdsMobNtFullView
import com.app.plugin.ads.adsmob.AdsMobPlNtFull
import com.app.plugin.logevent.ServerEventCapture
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdValue
import com.google.android.gms.ads.OnPaidEventListener
import com.google.android.gms.ads.ResponseInfo
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.google.firebase.Firebase
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.analytics
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Answers
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
import org.mockito.Mockito.withSettings
import org.mockito.Mockito.`when`

/**
 * 2.0.0 C1a (D1, D48, R-T6 m1): ba site T6 của native full AdMob (paid, click show, click get) mang placement của **lượt
 * hiện quảng cáo đó** ([AdsPlBase.turnPl]), không phải `showPl`/`getPl` của slot lúc callback tới (1.9.0 chọn theo
 * `flagShow`). Kịch bản: X hiện/lấy ở lượt 1, slot load Y và hiện/lấy ở lượt 2, callback muộn của X tới sau đó.
 */
class NtFullTurnPlacementTest {

    private class RecordingShowCb : AdsShowCb {
        val clicks = mutableListOf<String?>()
        override fun onAdClick(placement: String?, originPl: String?, adId: String?) {
            clicks += placement
        }
        val impressions = mutableListOf<String?>()
        override fun onAdImpression(placement: String?, originPl: String?, adId: String?) { impressions += placement }
    }

    private class NoLoadCb : AdsLoadInnerCb {
        override fun onLoaded(placement: String, originPl: String, adId: String, ecmp: AdsEcmp?, adspl: AdsPlBase?) = Unit
        override fun onLoadFailed(placement: String, originPl: String, adId: String, error: String) = Unit
    }

    /** Quảng cáo X của lượt 1, listener (AdLoader) của lượt nạp ra nó và các dialog đã dựng (lượt show). */
    private class FirstTurn(val ad: NativeAd, val listener: AdListener, val dialogs: List<AdsMobNtFullView>)

    private lateinit var context: Context
    private val activity = mock(Activity::class.java)
    private val unit = "ca-app-pub-3940256099942544/c1a"

    private fun admobQueue(): SdkReadyQueue =
        AdsMobMy::class.java.getDeclaredField("readyQueue").apply { isAccessible = true }.get(AdsMobMy.instance) as SdkReadyQueue

    @Before
    fun setUp() {
        context = mock(Context::class.java)
        val resources = mock(Resources::class.java)
        `when`(resources.getIdentifier(anyString(), anyString(), anyString())).thenReturn(0)
        `when`(context.resources).thenReturn(resources)
        `when`(context.getSharedPreferences(anyString(), anyInt())).thenReturn(mock(SharedPreferences::class.java))
        admobQueue().resetForTest(); admobQueue().markReady()
    }

    @After
    fun tearDown() = admobQueue().resetForTest()

    private fun nativeAd(): NativeAd = mock(NativeAd::class.java).also {
        `when`(it.responseInfo).thenReturn(mock(ResponseInfo::class.java))
    }

    /**
     * Hai lượt trên cùng slot native full AdMob: lượt 1 nạp X ở `<p>_home`, lượt 2 nạp Y ở `<p>_settings`; [get] = đường
     * get (`getAdForShow`, như `NativeController`), ngược lại đường show (dialog, đóng X trước lượt 2). [check] nhận X.
     * 2.1.0 P3: lượt 2 đi đường [secondGet] với callback [cb2] (mặc định cùng đường, cùng callback với lượt 1).
     */
    private fun twoNtFullTurns(
        get: Boolean, cb: RecordingShowCb, secondGet: Boolean = get, cb2: RecordingShowCb = cb, check: (FirstTurn) -> Unit
    ) = MainHandlerTimers.run { _ ->
        val natives = mutableListOf<NativeAd.OnNativeAdLoadedListener>()
        val listeners = mutableListOf<AdListener>()
        mockConstruction(AdRequest.Builder::class.java) { b, _ -> `when`(b.build()).thenReturn(mock(AdRequest::class.java)) }.use {
            mockConstruction(AdLoader.Builder::class.java, withSettings().defaultAnswer(Answers.RETURNS_SELF)) { b, _ ->
                `when`(b.forNativeAd(any())).thenAnswer { natives += it.arguments[0] as NativeAd.OnNativeAdLoadedListener; b }
                `when`(b.withAdListener(any())).thenAnswer { listeners += it.arguments[0] as AdListener; b }
                `when`(b.build()).thenReturn(mock(AdLoader::class.java))
            }.use {
                mockConstruction(AdsMobNtFullView::class.java).use { dialogs ->
                    mockStatic(LayoutInflater::class.java).use { inflaters ->
                        val inflater = detachedInflater()
                        inflaters.`when`<LayoutInflater> { LayoutInflater.from(any()) }.thenReturn(inflater)
                        val pl = AdsMobPlNtFull(context, mock(AdsBase::class.java), AdsIDDef.AdmobFullNt, "full_nt_default")
                        pl.setActivity(activity)
                        pl.parserEcpms(unit)
                        val prefix = if (get) "nt" else "full"
                        val prefix2 = if (secondGet) "nt" else "full"
                        val x = nativeAd()
                        pl.load("${prefix}_home", NoLoadCb())
                        natives[0].onNativeAdLoaded(x)
                        turn(pl, "${prefix}_home", cb, get)
                        if (!get) {
                            val dismiss = ArgumentCaptor.forClass(DialogInterface.OnDismissListener::class.java)
                            verify(dialogs.constructed()[0]).setOnDismissListener(dismiss.capture())
                            dismiss.value.onDismiss(dialogs.constructed()[0])
                        }
                        pl.load("${prefix2}_settings", NoLoadCb())
                        assertEquals("lượt 2 gửi request mới", 2, natives.size)
                        natives[1].onNativeAdLoaded(nativeAd())
                        turn(pl, "${prefix2}_settings", cb2, secondGet)
                        check(FirstTurn(x, listeners[0], dialogs.constructed()))
                    }
                }
            }
        }
    }

    private fun turn(pl: AdsMobPlNtFull, placement: String, cb: RecordingShowCb, get: Boolean) {
        if (get) assertNotNull("get $placement", pl.getAdForShow(placement, cb))
        else assertTrue("show $placement", pl.show(placement, cb, 0))
    }

    /** `AdsMobNtFullBinder` trên JVM: inflate trả một view có `NativeAdView` để container gắn. */
    private fun detachedInflater(): LayoutInflater {
        val adView = mock(NativeAdView::class.java)
        `when`(adView.findViewById<View>(anyInt())).thenReturn(mock(MediaView::class.java))
        val root = mock(View::class.java)
        `when`(root.findViewById<View>(anyInt())).thenReturn(adView)
        val inflater = mock(LayoutInflater::class.java)
        `when`(inflater.inflate(anyInt(), any<ViewGroup>(), anyBoolean())).thenReturn(root)
        return inflater
    }

    @Test
    fun `N1 paid muon cua NtFull luot truoc mang placement luot do`() =
        twoNtFullTurns(get = false, cb = RecordingShowCb()) { first ->
            val paid = ArgumentCaptor.forClass(OnPaidEventListener::class.java)
            verify(first.ad).setOnPaidEventListener(paid.capture())
            val adValue = mock(AdValue::class.java)
            `when`(adValue.valueMicros).thenReturn(1_000L)
            `when`(adValue.currencyCode).thenReturn("USD")
            ServerEventCapture().use { server ->
                mockStatic(Class.forName("com.google.firebase.analytics.AnalyticsKt")).use { analyticsKt ->
                    analyticsKt.`when`<FirebaseAnalytics> { Firebase.analytics }.thenReturn(mock(FirebaseAnalytics::class.java))
                    paid.value.onPaidEvent(adValue)        // paid muộn của X, sau khi slot đã hiện Y
                }
                val rev = server.await("ad_revenue", 1).single()
                assertEquals("N1: paid muộn của X phải mang placement lượt X (full_home): $rev", "full_home", rev.get("placement").asString)
            }
        }

    @Test
    fun `N2 click muon cua NtFull duong show mang placement luot do`() {
        val cb = RecordingShowCb()
        twoNtFullTurns(get = false, cb = cb) { first -> first.listener.onAdClicked() }
        assertEquals("N2: click muộn của X (show) phải mang placement lượt X", listOf<String?>("full_home"), cb.clicks)
    }

    @Test
    fun `N3 click muon cua NtFull duong get mang placement luot do`() {
        val cb = RecordingShowCb()
        twoNtFullTurns(get = true, cb = cb) { first -> first.listener.onAdClicked() }
        assertEquals("N3: click muộn của X (get) phải mang placement lượt get của X", listOf<String?>("nt_home"), cb.clicks)
    }

    /**
     * 2.1.0 P3 (M11, R-C1a m2): click muộn của X đi tới callback của **lượt X** (đường show hoặc get của chính X), không
     * theo `flagShow` hiện hành của slot. Chiều 1: X lấy ở đường get, Y hiện ở đường show → click X tới callback get, không
     * tới callback show của Y, và không `afterClick` dialog của Y. Chiều 2: X hiện (show), Y lấy (get) → callback show.
     */
    @Test
    fun `C1 click muon cua luot truoc toi callback cua luot do, khong theo flagShow hien hanh`() {
        val getX = RecordingShowCb()
        val showY = RecordingShowCb()
        var dialogY: AdsMobNtFullView? = null
        twoNtFullTurns(get = true, cb = getX, secondGet = false, cb2 = showY) { first ->
            dialogY = first.dialogs.single()
            first.listener.onAdClicked()                   // click muộn của X (get), sau khi slot đã hiện Y (show)
        }
        assertEquals("C1: click muộn của X (get) tới callback get của lượt X", listOf<String?>("nt_home"), getX.clicks)
        assertEquals("C1: callback show của lượt Y không nhận click của X", emptyList<String?>(), showY.clicks)
        verify(dialogY!!, never()).afterClick()

        val showX = RecordingShowCb()
        val getY = RecordingShowCb()
        twoNtFullTurns(get = false, cb = showX, secondGet = true, cb2 = getY) { first -> first.listener.onAdClicked() }
        assertEquals("C1: click muộn của X (show) tới callback show của lượt X", listOf<String?>("full_home"), showX.clicks)
        assertEquals("C1: callback get của lượt Y không nhận click của X", emptyList<String?>(), getY.clicks)
    }

    @Test // 2.2.0 PSH (N07, Q-A): impression muộn của X tới callback của lượt X, như click ở C1 (cũ: theo flagShow hiện hành)
    fun `C5 impression muon cua NtFull AdMob luot truoc toi callback cua luot do`() {
        val (getX, showY) = RecordingShowCb() to RecordingShowCb()
        twoNtFullTurns(get = true, cb = getX, secondGet = false, cb2 = showY) { first -> first.listener.onAdImpression() }
        assertEquals("C5: impression muộn của X (get) tới callback get của lượt X", listOf<String?>("nt_home"), getX.impressions)
        assertEquals("C5: callback show của lượt Y không nhận impression của X", emptyList<String?>(), showY.impressions)
    }
}
