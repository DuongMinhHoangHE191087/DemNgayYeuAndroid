package com.app.plugin.ads.adsmob

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import android.util.Log
import com.app.plugin.ads.AdsBase
import com.app.plugin.ads.AdsEcmp
import com.app.plugin.ads.AdsIDDef
import com.app.plugin.ads.AdsLoadInnerCb
import com.app.plugin.ads.AdsPlBase
import com.app.plugin.ads.GmaLoadContext
import com.app.plugin.ads.MainHandlerTimers
import com.app.plugin.ads.SdkReadyQueue
import com.app.plugin.until.LogUtil
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.rewarded.RewardedAd
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.mockito.Answers
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.contains
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockConstruction
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.withSettings
import org.mockito.Mockito.`when`

/**
 * Task N (DOC/65 §7 #18 phương án b, G9): với `gma_load_app_context` tắt (mặc định), sáu chỗ nạp GMA truyền Activity
 * như cũ; bật thì truyền `applicationContext`. Cờ đọc lúc mỗi lần nạp. Cổng `activity null` giữ nguyên dù cờ bật.
 */
class GmaLoadContextPlacementTest {

    private class NoopCb : AdsLoadInnerCb {
        val events = mutableListOf<String>()
        override fun onLoaded(placement: String, originPl: String, adId: String, ecmp: AdsEcmp?, adspl: AdsPlBase?) {
            events += "loaded"
        }
        override fun onLoadFailed(placement: String, originPl: String, adId: String, error: String) {
            events += "fail:$error"
        }
    }

    private val unit = "ca-app-pub-3940256099942544/unit"
    private val flagOn = """{"schema_version":1,"gma_load_app_context":true}"""

    private lateinit var context: Context
    private lateinit var appContext: Context
    private val activity = mock(Activity::class.java)

    private fun admobQueue(): SdkReadyQueue =
        AdsMobMy::class.java.getDeclaredField("readyQueue").apply { isAccessible = true }.get(AdsMobMy.instance) as SdkReadyQueue

    @Before
    fun setUp() {
        context = mock(Context::class.java)
        appContext = mock(Context::class.java)
        val resources = mock(Resources::class.java)
        `when`(resources.getIdentifier(anyString(), anyString(), anyString())).thenReturn(0)
        `when`(context.resources).thenReturn(resources)
        `when`(context.applicationContext).thenReturn(appContext)
        `when`(context.getSharedPreferences(anyString(), anyInt())).thenReturn(mock(SharedPreferences::class.java))
        admobQueue().resetForTest(); admobQueue().markReady()
        GmaLoadContext.apply(null)
    }

    @After
    fun tearDown() {
        admobQueue().resetForTest()
        GmaLoadContext.apply(null)
        LogUtil.setFlagLog(1)
    }

    /** Chạy một lượt load của [pl] với `AdRequest.Builder` giả; trả các context GMA nhận, theo thứ tự. */
    private fun <T> withAdRequest(block: () -> T): T = MainHandlerTimers.run {
        mockConstruction(AdRequest.Builder::class.java) { b, _ -> `when`(b.build()).thenReturn(mock(AdRequest::class.java)) }
            .use { block() }
    }

    private fun loadOnce(pl: AdsPlBase) {
        pl.setActivity(activity)
        pl.parserEcpms(unit)
        pl.load("pl_home", NoopCb())
    }

    // ═══ Full-screen: InterstitialAd / AppOpenAd / RewardedAd .load(context, …) ═══

    private fun intersContexts(flag: String?): List<Any?> = withAdRequest {
        GmaLoadContext.apply(flag)
        val seen = mutableListOf<Any?>()
        mockStatic(InterstitialAd::class.java).use { s ->
            s.`when`<Unit> { InterstitialAd.load(any(Context::class.java), anyString(), any(AdRequest::class.java), any()) }
                .thenAnswer { seen += it.arguments[0]; null }
            loadOnce(AdsMobPlInters(context, mock(AdsBase::class.java), AdsIDDef.AdmobFullInters, "full_default"))
        }
        seen
    }

    private fun openContexts(flag: String?): List<Any?> = withAdRequest {
        GmaLoadContext.apply(flag)
        val seen = mutableListOf<Any?>()
        mockStatic(AppOpenAd::class.java).use { s ->
            s.`when`<Unit> { AppOpenAd.load(any(Context::class.java), anyString(), any(AdRequest::class.java), any()) }
                .thenAnswer { seen += it.arguments[0]; null }
            loadOnce(AdsMobPlOpen(context, mock(AdsBase::class.java), AdsIDDef.AdmobFullOpen, "open_default"))
        }
        seen
    }

    private fun rewardedContexts(flag: String?): List<Any?> = withAdRequest {
        GmaLoadContext.apply(flag)
        val seen = mutableListOf<Any?>()
        mockStatic(RewardedAd::class.java).use { s ->
            s.`when`<Unit> { RewardedAd.load(any(Context::class.java), anyString(), any(AdRequest::class.java), any()) }
                .thenAnswer { seen += it.arguments[0]; null }
            loadOnce(AdsMobPlrewarded(context, mock(AdsBase::class.java), AdsIDDef.AdmobRewarded, "gift_default"))
        }
        seen
    }

    // ═══ Native: AdLoader.Builder(context, unit) ═══

    private fun nativeContexts(flag: String?, make: () -> AdsPlBase): List<Any?> = withAdRequest {
        GmaLoadContext.apply(flag)
        val seen = mutableListOf<Any?>()
        val loader = mock(AdLoader::class.java)
        mockConstruction(AdLoader.Builder::class.java, withSettings().defaultAnswer(Answers.RETURNS_SELF)) { b, c ->
            seen += c.arguments()[0]
            `when`(b.build()).thenReturn(loader)
        }.use { loadOnce(make()) }
        seen
    }

    private fun nt() = AdsMobPlNt(context, mock(AdsBase::class.java), AdsIDDef.AdmobNative, "nt_default")
    private fun ntCl() = AdsMobPlNtCl(context, mock(AdsBase::class.java), AdsIDDef.AdmobNtCl, "ntcl_default")
    private fun ntFull() = AdsMobPlNtFull(context, mock(AdsBase::class.java), AdsIDDef.AdmobFullNt, "full_nt_default")

    private fun assertActivity(seen: List<Any?>) {
        assertEquals("đúng một request", 1, seen.size)
        assertSame("cờ tắt: nạp bằng Activity như cũ", activity, seen[0])
    }

    private fun assertAppContext(seen: List<Any?>) {
        assertEquals("đúng một request", 1, seen.size)
        assertSame("cờ bật: nạp bằng applicationContext", appContext, seen[0])
    }

    @Test fun `Inters - co tat nap bang Activity`() = assertActivity(intersContexts(null))
    @Test fun `Inters - co bat nap bang applicationContext`() = assertAppContext(intersContexts(flagOn))
    @Test fun `Open - co tat nap bang Activity`() = assertActivity(openContexts(null))
    @Test fun `Open - co bat nap bang applicationContext`() = assertAppContext(openContexts(flagOn))
    @Test fun `Rewarded - co tat nap bang Activity`() = assertActivity(rewardedContexts(null))
    @Test fun `Rewarded - co bat nap bang applicationContext`() = assertAppContext(rewardedContexts(flagOn))
    @Test fun `Nt - co tat nap bang Activity`() = assertActivity(nativeContexts(null, ::nt))
    @Test fun `Nt - co bat nap bang applicationContext`() = assertAppContext(nativeContexts(flagOn, ::nt))
    @Test fun `NtCl - co tat nap bang Activity`() = assertActivity(nativeContexts(null, ::ntCl))
    @Test fun `NtCl - co bat nap bang applicationContext`() = assertAppContext(nativeContexts(flagOn, ::ntCl))
    @Test fun `NtFull - co tat nap bang Activity`() = assertActivity(nativeContexts(null, ::ntFull))
    @Test fun `NtFull - co bat nap bang applicationContext`() = assertAppContext(nativeContexts(flagOn, ::ntFull))

    @Test fun `co tat thi khong co dong log context, bat thi moi lan nap mot dong`() {
        LogUtil.setFlagLog(3)
        mockStatic(Log::class.java).use { log ->
            intersContexts(null)
            log.verify({ Log.d(eq(LogUtil.TAG), contains("ads gma load context=application")) }, never())
            intersContexts(flagOn)
            nativeContexts(flagOn, ::ntCl)
            log.verify({ Log.d(eq(LogUtil.TAG), contains("ads gma load context=application")) }, times(2))
        }
    }

    @Test fun `Remote Config doi giua phien thi lan nap sau theo gia tri moi`() {
        assertAppContext(intersContexts(flagOn))
        assertActivity(intersContexts("""{"gma_load_app_context":false}"""))
    }

    /** Activity bị bỏ khi lượt nạp đang chờ SDK sẵn sàng: cờ bật vẫn trả `activity null`, không nạp bằng app context. */
    @Test fun `cong activity null giu nguyen khi co bat`() = withAdRequest {
        GmaLoadContext.apply(flagOn)
        admobQueue().resetForTest()                     // SDK chưa sẵn sàng: tryLoad xếp hàng
        mockStatic(InterstitialAd::class.java).use { s ->
            val pl = AdsMobPlInters(context, mock(AdsBase::class.java), AdsIDDef.AdmobFullInters, "full_default")
            pl.setActivity(activity)
            pl.parserEcpms(unit)
            val cb = NoopCb()
            pl.load("pl_home", cb)
            pl.releaseActivity()
            admobQueue().markReady()
            assertEquals(listOf("fail:activity null"), cb.events)
            s.verify({ InterstitialAd.load(any(Context::class.java), anyString(), any(AdRequest::class.java), any()) }, never())
        }
    }
}
