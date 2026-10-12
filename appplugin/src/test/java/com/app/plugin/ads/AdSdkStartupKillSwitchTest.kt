package com.app.plugin.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.app.plugin.SdkKillSwitch
import com.app.plugin.consent.ConsentManager
import com.app.plugin.consent.ConsentState
import com.app.plugin.consent.contextWithTcf
import com.app.plugin.logevent.ServerEventCapture
import com.app.plugin.until.Constants
import com.app.plugin.until.LogUtil
import com.facebook.ads.AudienceNetworkAds
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.google.firebase.Firebase
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.analytics
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
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

/**
 * Task 16 (doc 60 G, gate G7): kill switch `ads` chặn cả khởi tạo mạng quảng cáo (addendum A2), và
 * `sdk_health` đo từ lúc app request mạng, không từ lúc cổng consent mở (addendum A1).
 *
 * Các test đi đường kill: mạng thật (MobileAds chạy trên coroutine IO) không khởi tạo được trong JVM,
 * còn `sdk_health` của đường kill dùng chung phép đo với đường thường.
 *
 * Fix wave 1, Important 1: kill bị gỡ giữa một phiên đã bỏ khởi tạo mạng thì quảng cáo vẫn tắt (nhãn kill)
 * tới lần mở kế, thay vì để load lọt qua cổng rồi bị giữ mãi trong hàng chờ của mạng chưa khởi tạo.
 */
class AdSdkStartupKillSwitchTest {
    @Before fun setUp() {
        ConsentState.resetForTest()
        AdSdkStartup.resetForTest()
        SdkKillSwitch.current = SdkKillSwitch.NONE
    }

    @After fun tearDown() {
        AdSdkStartup.resetForTest()
        ConsentState.resetForTest()
        SdkKillSwitch.current = SdkKillSwitch.NONE
    }

    private class Sinks(val fa: FirebaseAnalytics, val meta: MockedStatic<AudienceNetworkAds>, val log: MockedStatic<Log>)

    /** UMP trả [ci]; Firebase Analytics và Meta Audience Network là mock; `android.util.Log` ghi lại. */
    private fun withSinks(ci: ConsentInformation, activity: Activity, block: (Sinks) -> Unit) {
        val fa = mock(FirebaseAnalytics::class.java)
        mockStatic(UserMessagingPlatform::class.java).use { ump ->
            ump.`when`<ConsentInformation> { UserMessagingPlatform.getConsentInformation(any(Context::class.java)) }
                .thenReturn(ci)
            mockStatic(Class.forName("com.google.firebase.analytics.AnalyticsKt")).use { analyticsKt ->
                analyticsKt.`when`<FirebaseAnalytics> { Firebase.analytics }.thenReturn(fa)
                mockStatic(AudienceNetworkAds::class.java).use { meta ->
                    mockStatic(Log::class.java).use { log ->
                        try {
                            block(Sinks(fa, meta, log))
                        } finally {
                            ConsentState.resetForTest()
                            ConsentManager.reset(activity)
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

    @Test
    fun `kill ads thi cong mo ma khong khoi tao mang nao, sdk_health bao networks=killed kill=ads`() {
        SdkKillSwitch.current = SdkKillSwitch(ads = true)
        val ci = mock(ConsentInformation::class.java)
        `when`(ci.canRequestAds()).thenReturn(true)   // câu trả lời đã có: cổng mở ngay khi request
        val context = contextWithTcf(gdpr = 1, purposes = "1111111111")

        withSinks(ci, activityOf(context)) { s ->
            AdSdkStartup.requestAdNetworks(context)

            s.meta.verifyNoInteractions()   // Meta Audience Network không khởi tạo
            assertEquals("mạng không bắt đầu khởi tạo", 0L, AdSdkStartup.adNetworksStartedAtMs)
            s.log.verify { Log.e(eq(LogUtil.TAG), eq("kill switch: ads — không khởi tạo mạng quảng cáo")) }
            verify(s.fa, times(1)).logEvent(eq("sdk_health"), any())
            s.log.verify { Log.d(eq(LogUtil.TAG), contains("networks=killed")) }
            s.log.verify { Log.d(eq(LogUtil.TAG), contains("kill=ads")) }
            s.log.verify { Log.d(eq(LogUtil.TAG), contains("consent_gdpr=true")) }
        }
    }

    @Test
    fun `kill go giua phien sau khi da bo khoi tao mang thi quang cao van tat toi lan mo ke, load bi tu choi o cong`() {
        SdkKillSwitch.current = SdkKillSwitch(ads = true)   // SdkKillSwitch.restore lúc mở app: bản lưu còn kill
        val ci = mock(ConsentInformation::class.java)
        `when`(ci.canRequestAds()).thenReturn(true)
        val context = contextWithTcf(gdpr = 1, purposes = "1111111111")
        val helper = AdsKillSwitchTest.newAdsHelper()
        val kill = Constants.AdShowFailReason.SHOW_BLOCKED_KILL_SWITCH

        withSinks(ci, activityOf(context)) { s ->
            AdSdkStartup.requestAdNetworks(context)   // bỏ khởi tạo mạng

            SdkKillSwitch.applyRemote(null, "")   // fetch trong phiên: ops đã gỡ khoá trên console
            assertEquals(SdkKillSwitch.NONE, SdkKillSwitch.current)

            assertTrue("vẫn tắt tới lần mở kế", helper.isDisableAds(4))
            val loads = mutableListOf<String>()
            helper.loadInterstitialForThisTurn("home", 0, object : AdsLoadCb {
                override fun onLoaded(placement: String, originPl: String, adId: String) { loads += "loaded" }
                override fun onLoadFailed(placement: String, originPl: String, adId: String, error: String) {
                    loads += "failed:$error"
                }
            })
            // Bị từ chối ngay ở cổng, có callback: không lượt load nào vào SdkReadyQueue của mạng chưa khởi tạo.
            assertEquals(listOf("failed:$kill"), loads)
            ServerEventCapture().use { server ->
                assertFalse(helper.showInterstitial("home", 0, object : AdsShowCb {
                    override fun onAdShowFail(placement: String?, originPl: String?, adId: String?, err: String?) =
                        throw AssertionError("interstitial bị chặn đồng bộ không callback")
                }, isDeltaTime = false))
                assertEquals(listOf("full_home" to kill), server.showFails(1))
            }
            s.log.verify(
                { Log.e(eq(LogUtil.TAG), eq("kill switch: kill removed mid-session; ads stay disabled until next open")) },
                times(1)
            )
            assertTrue("phiên này đã bỏ khởi tạo mạng", AdSdkStartup.adNetworksKilled)
        }
    }

    @Test
    fun `since_request_ms tinh tu lan request dau tien, khong tu luc cong consent mo`() {
        SdkKillSwitch.current = SdkKillSwitch(belowVersion = "999.0.0")   // mọi bản hiện có đều dưới ngưỡng
        var now = 1_000_000L
        AdSdkStartup.nowMs = { now }
        // Cài mới ở EEA: form chưa trả lời, cổng đóng tới khi UMP trả lời.
        var answered = false
        val ci = mock(ConsentInformation::class.java)
        `when`(ci.canRequestAds()).thenAnswer { answered }
        `when`(ci.consentStatus).thenAnswer {
            if (answered) ConsentInformation.ConsentStatus.OBTAINED else ConsentInformation.ConsentStatus.REQUIRED
        }
        doAnswer { inv ->
            answered = true
            inv.getArgument<ConsentInformation.OnConsentInfoUpdateSuccessListener>(2).onConsentInfoUpdateSuccess()
            null
        }.`when`(ci).requestConsentInfoUpdate(
            any(Activity::class.java),
            any(ConsentRequestParameters::class.java),
            any(ConsentInformation.OnConsentInfoUpdateSuccessListener::class.java),
            any(ConsentInformation.OnConsentInfoUpdateFailureListener::class.java)
        )
        val context = contextWithTcf(gdpr = 1, purposes = "0000000000")
        val activity = activityOf(context)

        withSinks(ci, activity) { s ->
            AdSdkStartup.requestAdNetworks(context)   // Activity.onCreate: cổng đóng
            now += 100
            AdSdkStartup.requestAdNetworks(context)   // gọi lại: không đổi mốc request
            verify(s.fa, never()).logEvent(eq("sdk_health"), any())

            now += 150
            ConsentManager.updateConsentInfo(activity) {}   // UMP trả lời 250 ms sau request đầu: cổng mở

            verify(s.fa, times(1)).logEvent(eq("sdk_health"), any())
            s.log.verify { Log.d(eq(LogUtil.TAG), contains("since_request_ms=250")) }
            s.log.verify { Log.d(eq(LogUtil.TAG), contains("kill=version")) }
        }
    }

    // 1.6.4 Task 4: sdk_health mang init_ms (AppPluginBase.onCreate main-thread ms); -1 khi chưa đo.
    private fun setInitMs(value: Long) {
        try {
            SdkHealth::class.java.getDeclaredField("initMs").apply { isAccessible = true }.setLong(SdkHealth, value)
        } catch (e: NoSuchFieldException) {
            org.junit.Assert.fail("SdkHealth.initMs chưa có (1.6.4 Task 4): $e")
        }
    }

    private fun healthLogWithInitMs(expected: String) {
        SdkKillSwitch.current = SdkKillSwitch(ads = true)
        val ci = mock(ConsentInformation::class.java)
        `when`(ci.canRequestAds()).thenReturn(true)
        val context = contextWithTcf(gdpr = 1, purposes = "1111111111")
        withSinks(ci, activityOf(context)) { s ->
            AdSdkStartup.requestAdNetworks(context)
            s.log.verify { Log.d(eq("AppPlugin"), contains(expected)) }
        }
    }

    @Test
    fun `sdk_health mang init_ms, mac dinh -1 khi AppPluginBase chua do`() {
        healthLogWithInitMs("init_ms=-1")
    }

    @Test
    fun `sdk_health mang init_ms da do`() {
        setInitMs(123L)
        try {
            healthLogWithInitMs("init_ms=123")
        } finally {
            setInitMs(-1L)
        }
    }
}
