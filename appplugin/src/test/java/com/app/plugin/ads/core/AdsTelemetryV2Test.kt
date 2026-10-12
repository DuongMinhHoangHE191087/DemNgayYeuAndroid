package com.app.plugin.ads.core

import android.os.Handler
import com.app.plugin.ads.AdsEcmp
import com.app.plugin.ads.AdsHelper
import com.app.plugin.ads.AdsKillSwitchTest
import com.app.plugin.ads.AdsPlBase
import com.app.plugin.ads.AdsPlacementEcmpBase
import com.app.plugin.logevent.ServerEventCapture
import com.google.firebase.Firebase
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.analytics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockConstruction
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.`when`

/**
 * Chuỗi load v2 (plan 1.7.0 Task 1, A1–A4): `ad_request` → `ad_fill` | `ad_no_fill`, cùng `request_id`, `latency_ms`,
 * `error_code` từ tham số `error` của mạng, và timeout của watchdog slot đóng request bằng `ad_no_fill(error_code =
 * timeout)` đúng một lần. Harness như `AdsTelemetryTest` (helper qua `AdsKillSwitchTest.newAdsHelper()`, Firebase giả).
 */
class AdsTelemetryV2Test {
    private lateinit var helper: AdsHelper
    private val uuid = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")

    @Before fun setUp() {
        helper = AdsKillSwitchTest.newAdsHelper()
    }

    private fun fb(action: () -> Unit) {
        val firebase = mock(FirebaseAnalytics::class.java)
        mockStatic(Class.forName("com.google.firebase.analytics.AnalyticsKt")).use { k ->
            k.`when`<FirebaseAnalytics> { Firebase.analytics }.thenReturn(firebase)
            action()
        }
    }

    private fun start() = helper.onAdLoadStart("full_home", "full_home", "interstitial", "admob", "id-1")
    private fun loaded() = helper.onAdLoaded("full_home", "full_home", "interstitial", "admob", "id-1", "AdMobAdapter")
    private fun failed(error: String) = helper.onAdLoadFail("full_home", "full_home", "interstitial", "admob", "id-1", error)

    @Test fun `A1 onAdLoadStart phat ad_request co request_id va mediation admob, khong co ad_platform null`() {
        ServerEventCapture().use { s ->
            fb { start(); loaded() }
            val req = s.await("ad_request", 1)
            val fill = s.await("ad_fill", 1)
            assertTrue("ad_request=$req ad_fill=$fill", req.size == 1 && fill.size == 1)
            val r = req.single()
            assertTrue("request_id UUID: $r", uuid.matches(r.get("request_id").asString))
            assertEquals("admob", r.get("mediation").asString)
            assertEquals("full_home", r.get("placement").asString)
            assertEquals("id-1", r.get("ad_unit").asString)
            assertTrue("không còn ad_platform: $r", !r.has("ad_platform"))
        }
    }

    @Test fun `A2 onAdLoaded cung request_id, latency_ms lon hon hoac bang 0, ad_source`() {
        ServerEventCapture().use { s ->
            fb { start(); loaded() }
            val req = s.await("ad_request", 1)
            val fill = s.await("ad_fill", 1)
            assertTrue("ad_request=$req ad_fill=$fill", req.size == 1 && fill.size == 1)
            val f = fill.single()
            assertEquals("request_id của fill = của request", req.single().get("request_id").asString, f.get("request_id")?.asString)
            assertTrue("latency_ms >= 0: $f", f.get("latency_ms").asLong >= 0L)
            assertEquals("admob", f.get("ad_source").asString)
        }
    }

    @Test fun `A3 onAdLoadFail mang error_code tu tham so error`() {
        ServerEventCapture().use { s ->
            fb { start(); failed("3") }
            val nf = s.await("ad_no_fill", 1)
            assertEquals("ad_no_fill.error_code: $nf", "3", nf.firstOrNull()?.get("error_code")?.asString)
            assertTrue("latency_ms >= 0: $nf", nf.single().get("latency_ms").asLong >= 0L)
            assertEquals("3", nf.single().get("reason").asString)
        }
    }

    @Test fun `A4 onLoadTimeout phat ad_no_fill error_code=timeout dung mot lan, response muon khong phat them`() {
        val timers = mutableListOf<Runnable>()
        mockConstruction(Handler::class.java) { handler, _ ->
            `when`(handler.postDelayed(any(Runnable::class.java), anyLong())).thenAnswer { timers += it.arguments[0] as Runnable; true }
            doAnswer { inv -> timers.remove(inv.arguments[0]); null }.`when`(handler).removeCallbacks(any(Runnable::class.java))
        }.use {
            val slot = AdsPlacementEcmpBase(mock(AdsPlBase::class.java)).apply { originPl = "full_home" }
            val ecmps = checkNotNull(slot.adEcmps)
            ecmps.list += AdsEcmp(ecmps).apply { adsId = "id-1"; isLoading = true }
            ServerEventCapture().use { s ->
                fb {
                    start()
                    slot.isLoading = true
                    timers.toList().forEach { it.run() }   // watchdog slot hết hạn (60 s): `onLoadTimeout`
                    loaded()                                 // response muộn của request đã đóng
                    failed("3")
                }
                val nf = s.await("ad_no_fill", 1)
                assertEquals("ad_no_fill: $nf", listOf("timeout"), nf.map { it.get("error_code").asString })
                assertEquals("response muộn không phát ad_fill", 0, s.await("ad_fill", 0, settleMs = 0).size)
                assertEquals(
                    "ad_no_fill cùng request_id với ad_request",
                    s.await("ad_request", 1).single().get("request_id").asString, nf.single().get("request_id").asString
                )
            }
        }
    }

    // ── 1.8.0 Task 15 (carry R-1, T3b) ─────────────────────────────────────────────────────────────

    @Test fun `R3 close() tu nhieu luong chi phat 1 ad_no_fill moi request`() {
        val rounds = 100
        ServerEventCapture().use { s ->
            s.autoAck = true
            fb {
                repeat(rounds) {
                    start()
                    val go = CountDownLatch(1)
                    val threads = (1..4).map { Thread { go.await(); failed("3") }.apply { start() } }
                    go.countDown()
                    threads.forEach { it.join() }
                }
            }
            val nf = s.await("ad_no_fill", rounds)
            assertEquals("mỗi request đúng 1 ad_no_fill (không có bản request_id rỗng): ${nf.size}", rounds, nf.size)
            assertTrue("mọi ad_no_fill mang request_id của request", nf.all { it.get("request_id").asString.isNotEmpty() })
        }
    }

    @Test fun `R5 ad_request ad_fill ad_no_fill co slot la slot mang`() {
        ServerEventCapture().use { s ->
            fb {
                start(); loaded()
                start(); failed("3")
            }
            val all = s.await("ad_request", 2) + s.await("ad_fill", 1) + s.await("ad_no_fill", 1)
            assertEquals("đủ 4 event: $all", 4, all.size)
            assertEquals("slot của cả 4: $all", listOf("full_home"), all.map { it.get("slot")?.asString }.distinct())
        }
    }

    @Test fun `R7 response khong co start van phat ad_no_fill request_id rong latency -1`() {
        ServerEventCapture().use { s ->
            fb { failed("3") }
            val nf = s.await("ad_no_fill", 1)
            assertEquals("ad_no_fill: $nf", 1, nf.size)
            assertEquals("request_id rỗng: $nf", "", nf.single().get("request_id").asString)
            assertEquals("latency_ms -1: $nf", -1L, nf.single().get("latency_ms").asLong)
        }
    }
}
