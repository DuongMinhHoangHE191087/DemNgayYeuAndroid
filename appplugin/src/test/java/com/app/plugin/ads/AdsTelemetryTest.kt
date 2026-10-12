package com.app.plugin.ads

import com.app.plugin.logevent.ServerEventCapture
import com.google.firebase.Firebase
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.analytics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.mockingDetails

/**
 * Test đặc tả vùng telemetry load/impression của [AdsHelper] (plan 1.5.0 Task 1, KB-2): viết và chạy xanh trên mã
 * CŨ trước khi chuyển sang `ads.core.AdsTelemetry`, làm lưới cho bước chuyển nguyên văn.
 *
 * Server: [ServerEventCapture] (fake `LogService`, như `AdsKillSwitchTest`). Firebase: `Firebase.analytics` là
 * extension property, trên JVM là static `AnalyticsKt.getAnalytics(Firebase)` — mock như
 * `FirHelperAdRevenueRoutingTest`; `FirHelper.logEvent` chạy đồng bộ trên luồng test nên static mock có hiệu lực.
 */
class AdsTelemetryTest {
    private lateinit var helper: AdsHelper

    @Before
    fun setUp() {
        helper = AdsKillSwitchTest.newAdsHelper()
    }

    /** Chạy [action] với Firebase giả, trả tên các event Firebase đã ghi theo thứ tự. */
    private fun firebaseEvents(action: () -> Unit): List<String> {
        val firebase = mock(FirebaseAnalytics::class.java)
        mockStatic(Class.forName("com.google.firebase.analytics.AnalyticsKt")).use { analyticsKt ->
            analyticsKt.`when`<FirebaseAnalytics> { Firebase.analytics }.thenReturn(firebase)
            action()
        }
        return mockingDetails(firebase).invocations
            .filter { it.method.name == "logEvent" }
            .map { it.arguments[0] as String }
    }

    @Test fun `start roi loaded cung khoa - mot ad_fill co latency, request da dong nen loaded lan hai khong phat them`() {
        ServerEventCapture().use { server ->
            val fb = firebaseEvents {
                helper.onAdLoadStart("full_home", "full_home", "interstitial", "admob", "id-1")
                helper.onAdLoaded("full_home", "full_home", "interstitial", "admob", "id-1", "AdMobAdapter")
                helper.onAdLoaded("full_home", "full_home", "interstitial", "admob", "id-1", "AdMobAdapter")
            }

            assertEquals(listOf("aad_lst_full_home_gg_int", "aad_led_full_home_gg_int", "aad_led_full_home_gg_int"), fb)
            assertEquals(1, server.await("ad_request", 1).size)
            val loads = server.await("ad_fill", 1)
            assertEquals("lần loaded thứ hai của request đã đóng không phát (1.8.0 Task 15): $loads", 1, loads.size)
            assertTrue("latency_ms đo được: ${loads[0]}", loads[0].get("latency_ms").asLong >= 0L)
            assertEquals("full_home", loads[0].get("placement").asString)
            assertEquals("admob", loads[0].get("mediation").asString)
        }
    }

    @Test fun `load fail khong co start - ad_no_fill latency -1`() {
        ServerEventCapture().use { server ->
            val fb = firebaseEvents {
                helper.onAdLoadFail("nt_home", "nt_home", "native", "applovin", "id-2", "no fill")
            }

            assertEquals(listOf("aad_lfa_nt_home_max_native"), fb)
            val loads = server.await("ad_no_fill", 1)
            assertEquals(1, loads.size)
            assertEquals(-1L, loads[0].get("latency_ms").asLong)
            assertEquals("max", loads[0].get("mediation").asString)
        }
    }

    @Test fun `impression - show_ads_full cho interstitial va show_ads_banner cho rect, khong phat ad_show server (PaidEvent phat)`() {
        ServerEventCapture().use { server ->
            val fb = firebaseEvents {
                helper.onAdImpression("full_home", "full_home", "interstitial", "admob", "id-3", "AdMobAdapter")
                helper.onAdImpression("rect_home", "rect_home", "rect", "admob", "id-4", "AdMobAdapter")
            }

            assertEquals(
                listOf(
                    "aad_imp_full_home_gg_int", "show_ads_full", "show_ads_total",
                    "aad_imp_rect_home_gg_rect", "show_ads_banner", "show_ads_total",
                ),
                fb
            )
            // 1.7.0 Task 3: server `ad_show` đi cùng paid event (`PaidEvent`, có `imp_id`), không từ impression.
            assertEquals(0, server.await("ad_show", 0).size)
        }
    }

    @Test fun `ten event Firebase rut gon - banner admob thanh gg_bn`() {
        val fb = firebaseEvents {
            helper.onAdLoaded("bn_home", "bn_home", "banner", "admob", "id-5", "AdMobAdapter")
        }

        assertEquals(listOf("aad_led_bn_home_gg_bn"), fb)
    }
}
