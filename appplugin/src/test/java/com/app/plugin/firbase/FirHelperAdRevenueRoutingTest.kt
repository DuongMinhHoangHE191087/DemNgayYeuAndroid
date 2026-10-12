package com.app.plugin.firbase

import android.util.Log
import com.app.plugin.ads.AdRevenueRouting
import com.app.plugin.appsflyer.AppsflyerHelper
import com.app.plugin.consent.ConsentTier
import com.app.plugin.consent.contextWithTcf
import com.appsflyer.AppsFlyerLib
import com.google.firebase.Firebase
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.analytics
import org.junit.After
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.contains
import org.mockito.ArgumentMatchers.eq
import org.mockito.ArgumentMatchers.isNull
import org.mockito.MockedStatic
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify

/**
 * Doc 62 mục 2.3, phần nối dây: cờ [AdRevenueRouting.admobLinkedToFirebase] chỉ chặn
 * `ad_impression` mà [FirHelper.logAdRevAdmob] gửi lên Firebase. AppsFlyer, sự kiện hiển thị của
 * `AdsHelper.onAdImpression` và `LogEventServer.logAdRevenue` không phụ thuộc liên kết, nên vẫn chạy.
 *
 * `Firebase.analytics` là extension property, trên JVM là static `AnalyticsKt.getAnalytics(Firebase)`;
 * Kotlin không gọi được file facade theo tên nên lấy lớp bằng `Class.forName`. Mọi static mock ở đây
 * chỉ có hiệu lực trên luồng test, và `logAdRevAdmob` chạy đồng bộ trên luồng đó.
 *
 * AppsFlyer chỉ nhận doanh thu khi đã start (review C Minor 1): test start `AppsflyerHelper.instance` với tầng
 * GRANTED trước, và đưa nó về chưa start sau.
 */
class FirHelperAdRevenueRoutingTest {
    @After fun tearDown() {
        AdRevenueRouting.admobLinkedToFirebase = false
        AppsflyerHelper.instance.resetForTest()
    }

    private class Sinks(val firebase: FirebaseAnalytics, val appsFlyer: AppsFlyerLib, val log: MockedStatic<Log>)

    private fun logAdmobRevenue(linked: Boolean, check: Sinks.() -> Unit) {
        AdRevenueRouting.admobLinkedToFirebase = linked
        val firebase = mock(FirebaseAnalytics::class.java)
        val appsFlyer = mock(AppsFlyerLib::class.java)
        mockStatic(Class.forName("com.google.firebase.analytics.AnalyticsKt")).use { analyticsKt ->
            analyticsKt.`when`<FirebaseAnalytics> { Firebase.analytics }.thenReturn(firebase)
            mockStatic(AppsFlyerLib::class.java).use { af ->
                af.`when`<AppsFlyerLib> { AppsFlyerLib.getInstance() }.thenReturn(appsFlyer)
                mockStatic(Log::class.java).use { log ->
                    AppsflyerHelper.instance.startAfterConsent(
                        contextWithTcf(gdpr = 1, purposes = "1111111111", afAppId = "devKey"),
                        ConsentTier.GRANTED,
                        gdprApplies = true,
                    )
                    FirHelper.instance.logAdRevAdmob(
                        placement = "full_home",
                        originPl = "full_home",
                        adFormat = "interstitial",
                        adId = "ca-app-pub-3940256099942544/1033173712",
                        adNet = "com.google.ads.mediation.admob.AdMobAdapter",
                        adRev = 0.0123,
                        adCurrency = "USD",
                    )
                    Sinks(firebase, appsFlyer, log).check()
                }
            }
        }
    }

    /** Các đích không phụ thuộc liên kết AdMob ↔ Firebase: phải chạy ở cả hai nhánh. */
    private fun Sinks.assertOtherSinksRan() {
        verify(appsFlyer, times(1)).logAdRevenue(any(), any())
        // AdsHelper.onAdImpression ghi các sự kiện hiển thị qua FirHelper.logEvent(name, null).
        verify(firebase, times(1)).logEvent(eq("show_ads_total"), isNull())
        // LogEventServer chưa init trong unit test, nên logAdRevenue để lại đúng dòng này.
        log.verify { Log.d(eq("LogEventSerVer"), contains("dropping event: ad_revenue")) }
    }

    @Test fun `chua lien ket thi SDK ghi ad_impression cho admob`() = logAdmobRevenue(linked = false) {
        verify(firebase, times(1)).logEvent(eq(FirebaseAnalytics.Event.AD_IMPRESSION), any())
        assertOtherSinksRan()
    }

    @Test fun `da lien ket thi khong ghi ad_impression, AppsFlyer AdsHelper va server van chay`() =
        logAdmobRevenue(linked = true) {
            verify(firebase, never()).logEvent(eq(FirebaseAnalytics.Event.AD_IMPRESSION), any())
            assertOtherSinksRan()
        }
}
