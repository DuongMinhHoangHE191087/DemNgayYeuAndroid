package com.app.plugin.license

import android.app.Activity
import android.content.Context
import android.util.Log
import com.app.plugin.SdkKillSwitch
import com.app.plugin.ads.AdSdkStartup
import com.app.plugin.ads.AdsKillSwitchTest
import com.app.plugin.app.MetaSdkStarter
import com.app.plugin.consent.ConsentManager
import com.app.plugin.consent.ConsentState
import com.app.plugin.consent.ConsentTier
import com.app.plugin.consent.FcmConsent
import com.app.plugin.consent.contextWithTcf
import com.app.plugin.license.LicenseGate.Verdict
import com.app.plugin.until.LogUtil
import com.appsflyer.AppsFlyerLib
import com.google.android.gms.tasks.Task
import com.google.android.ump.ConsentInformation
import com.google.android.ump.UserMessagingPlatform
import com.google.firebase.Firebase
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.analytics
import com.google.firebase.messaging.FirebaseMessaging
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.contains
import org.mockito.ArgumentMatchers.eq
import org.mockito.MockedStatic
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * SA1 L5, L9, L14: hiệu lực verdict ở `AdSdkStartup.startAdNetworks` và `AdsHelper.isDisableAds`, và main không chờ
 * verdict. Mạng thật không khởi tạo được trong JVM: `networkStarters` ghi lại lời gọi (như `AdSdkStartupOrderTest`);
 * luồng test đóng vai main (static mock của Mockito chỉ có trên luồng tạo nó).
 */
class LicenseStartupTest {
    private val fcmExecutor = FcmConsent.executor
    private val started = mutableListOf<String>()

    @Before fun setUp() {
        ConsentState.resetForTest()
        AdSdkStartup.resetForTest()
        SdkKillSwitch.current = SdkKillSwitch.NONE
        FcmConsent.executor = Executor { it.run() }
        AdSdkStartup.networkStarters = AdSdkStartup.NetworkStarters(
            admob = { started += "admob" }, meta = { started += "meta" }, max = { started += "max" },
        )
    }

    @After fun tearDown() {
        FcmConsent.executor = fcmExecutor
        MetaSdkStarter.resetForTest()
        AdSdkStartup.resetForTest()
        LicenseGate.resetForTest()
        ConsentState.resetForTest()
        SdkKillSwitch.current = SdkKillSwitch.NONE
    }

    /** UMP đã có câu trả lời (cổng mở ngay khi request); Firebase, FCM, AppsFlyer là mock; `Log` tĩnh. */
    private class Sinks(val fa: FirebaseAnalytics, val log: MockedStatic<Log>)

    private fun withSinks(context: Context, canRequestAds: Boolean = true, block: (Sinks) -> Unit) {
        val ci = mock(ConsentInformation::class.java)
        `when`(ci.canRequestAds()).thenReturn(canRequestAds)
        val fa = mock(FirebaseAnalytics::class.java)
        val fcm = mock(FirebaseMessaging::class.java)
        @Suppress("UNCHECKED_CAST")
        val task = mock(Task::class.java) as Task<String>
        `when`(fcm.token).thenReturn(task)
        mockStatic(UserMessagingPlatform::class.java).use { ump ->
            ump.`when`<ConsentInformation> { UserMessagingPlatform.getConsentInformation(any(Context::class.java)) }.thenReturn(ci)
            mockStatic(Class.forName("com.google.firebase.analytics.AnalyticsKt")).use { analyticsKt ->
                analyticsKt.`when`<FirebaseAnalytics> { Firebase.analytics }.thenReturn(fa)
                mockStatic(FirebaseAnalytics::class.java).use { firebase ->
                    firebase.`when`<FirebaseAnalytics> { FirebaseAnalytics.getInstance(any()) }.thenReturn(fa)
                    mockStatic(FirebaseMessaging::class.java).use { messaging ->
                        messaging.`when`<FirebaseMessaging> { FirebaseMessaging.getInstance() }.thenReturn(fcm)
                        mockStatic(AppsFlyerLib::class.java).use { appsFlyer ->
                            appsFlyer.`when`<AppsFlyerLib> { AppsFlyerLib.getInstance() }.thenReturn(mock(AppsFlyerLib::class.java))
                            mockStatic(Log::class.java).use { log ->
                                try {
                                    block(Sinks(fa, log))
                                } finally {
                                    ConsentState.resetForTest()
                                    ConsentManager.reset(activityOf(context))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun activityOf(context: Context): Activity {
        val resources = context.resources
        val activity = mock(Activity::class.java)
        `when`(activity.applicationContext).thenReturn(context)
        `when`(activity.resources).thenReturn(resources)
        return activity
    }

    @Test fun `L5 verdict INVALID thi startAdNetworks khong goi starters admob`() {
        LicenseGate.resetForTest(Verdict.Invalid("sig"))
        val context = contextWithTcf(gdpr = 1, purposes = "1111111111")
        withSinks(context) { AdSdkStartup.requestAdNetworks(context) }
        assertEquals("không mạng nào khởi tạo khi INVALID", emptyList<String>(), started)
        assertEquals(0L, AdSdkStartup.adNetworksStartedAtMs)
    }

    @Test fun `L16 INVALID gui dung mot sdk_health networks=unlicensed license=INVALID-sig`() {
        LicenseGate.resetForTest(Verdict.Invalid("sig"))
        val context = contextWithTcf(gdpr = 1, purposes = "1111111111")
        withSinks(context) { s ->
            AdSdkStartup.requestAdNetworks(context)
            AdSdkStartup.requestAdNetworks(context)
            verify(s.fa, times(1)).logEvent(eq("sdk_health"), any())
            s.log.verify { Log.d(eq(LogUtil.TAG), contains("networks=unlicensed")) }
            s.log.verify { Log.d(eq(LogUtil.TAG), contains("license=INVALID:sig")) }
        }
        assertEquals(emptyList<String>(), started)
    }

    @Test fun `L17 R-P1 VALID nhung consent DENIED thi khong mang nao khoi tao`() {
        LicenseGate.resetForTest(Verdict.Valid("t1", Ed25519TestSigner.EXP))
        ConsentState.setTier(ConsentTier.DENIED)
        val context = contextWithTcf(gdpr = 1, purposes = "0000000000")
        withSinks(context, canRequestAds = false) { s ->
            AdSdkStartup.requestAdNetworks(context)
            verify(s.fa, never()).logEvent(eq("sdk_health"), any())
        }
        assertEquals("giấy phép không mở thứ consent đóng", emptyList<String>(), started)
        assertEquals(0L, AdSdkStartup.adNetworksStartedAtMs)
    }

    @Test fun `L9 isDisableAds true khi INVALID, false khi VALID va PENDING`() {
        val helper = AdsKillSwitchTest.newAdsHelper()
        for (v in listOf(Verdict.Valid("t1", 1L), Verdict.Pending)) {
            LicenseGate.resetForTest(v)
            for (flag in 0..6) assertFalse("$v: flag $flag", helper.isDisableAds(flag))
        }
        LicenseGate.resetForTest(Verdict.Invalid("expired"))
        for (flag in 0..6) assertTrue("INVALID: flag $flag", helper.isDisableAds(flag))
    }

    @Test fun `L14 main khong cho - PENDING thi task mang cho tren luong rieng roi chay lai tren main`() {
        LicenseGate.resetForTest()
        val release = CountDownLatch(1)
        LicenseGate.keys = { Ed25519TestSigner.KEYS }
        LicenseGate.clock = { Ed25519TestSigner.NOW_MS }
        LicenseGate.certsProvider = { release.await(5, TimeUnit.SECONDS); listOf(Ed25519TestSigner.CERT) }
        val posted = LinkedBlockingQueue<Runnable>()
        LicenseGate.mainPoster = { posted += it }
        val context = contextWithTcf(gdpr = 1, purposes = "1111111111")
        val token = Ed25519TestSigner.token()
        val main = Thread.currentThread().name
        withSinks(context) { s ->
            LicenseGate.start(context, token)   // bước `meta` của AppPluginBase.onCreate: trả về ngay, không chờ
            AdSdkStartup.requestAdNetworks(context)
            assertEquals("verdict còn PENDING", Verdict.Pending, LicenseGate.verdict)
            assertEquals("chưa mạng nào khởi tạo", emptyList<String>(), started)
            release.countDown()
            val rerun = posted.poll(5, TimeUnit.SECONDS)
            assertTrue("task mạng phải được chạy lại trên main sau verdict", rerun != null)
            rerun!!.run()   // main looper
            s.log.verify { Log.d(eq(LogUtil.TAG), contains("license=VALID")) }
        }
        assertFalse("main (luồng $main) không bao giờ await: ${LicenseGate.awaitThreads}", main in LicenseGate.awaitThreads)
        assertEquals(listOf("${LicenseGate.THREAD_NAME}-wait"), LicenseGate.awaitThreads.toList())
        assertTrue(LicenseGate.verdict is Verdict.Valid)
        assertTrue("admob khởi tạo sau verdict VALID", "admob" in started)
    }
}
