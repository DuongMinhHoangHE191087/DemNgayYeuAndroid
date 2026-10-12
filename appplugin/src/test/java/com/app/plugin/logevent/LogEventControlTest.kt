package com.app.plugin.logevent

import android.util.Log
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mockStatic

/** `logevent_control` (1.7.0 Task 7): parse, không bao giờ lấy mẫu tiền/phễu/brain/iap, tất định, tỉ lệ, tắt, `sample_rate`. */
class LogEventControlTest {
    private val realUserIdSource = LogEventServerHelper.userIdSource

    @Before fun setUp() = LogEventControl.resetForTest()

    @After fun tearDown() {
        LogEventServerHelper.userIdSource = realUserIdSource
        LogEventControl.resetForTest()
    }

    @Test fun `C1 parse clamp version va JSON hong`() {
        mockStatic(Log::class.java).use {
            val ok = LogEventControl.parse("""{"version":1,"enabled":true,"default_sample":1.5,"sample":{"a":0.1,"b":-2,"c":3}}""")
            assertEquals(1.0, ok.defaultSample, 0.0)
            assertEquals(mapOf("a" to 0.1, "b" to 0.0, "c" to 1.0), ok.sample)
            assertTrue(ok.enabled)
            val off = LogEventControl.parse("""{"version":1,"enabled":false}""")
            assertFalse(off.enabled)
            assertTrue("thiếu enabled → true", LogEventControl.parse("""{"version":1,"sample":{"x":0.1}}""").enabled)
            assertEquals("enabled không phải boolean → mặc định", LogEventControl.Config(), LogEventControl.parse("""{"version":1,"enabled":1}"""))
            assertEquals("NaN → 1.0", 1.0, LogEventControl.parse("""{"version":1,"default_sample":NaN}""").defaultSample, 0.0)
            LogEventControl.applyRemote(null, """{"version":1,"enabled":false}""")
            assertFalse("applyRemote áp ngay", LogEventControl.current.enabled)
            LogEventControl.applyRemote(null, "")
            assertEquals("chuỗi rỗng gỡ về mặc định", LogEventControl.Config(), LogEventControl.current)
            val store = java.util.concurrent.ConcurrentHashMap<String, Any?>()
            val ctx = org.mockito.Mockito.mock(android.content.Context::class.java)
            org.mockito.Mockito.`when`(ctx.getSharedPreferences(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(inMemoryPrefs(store))
            LogEventControl.applyRemote(ctx, """{"version":1,"enabled":false}""")
            assertEquals("lưu cho lần mở kế", """{"version":1,"enabled":false}""", store[LogEventControl.PREF_KEY])
            LogEventControl.resetForTest()
            LogEventControl.restore(ctx)
            assertFalse("restore áp bản đã lưu", LogEventControl.current.enabled)
            val def = LogEventControl.Config()
            assertEquals("version 2 → mặc định", def, LogEventControl.parse("""{"version":2,"enabled":false,"default_sample":0}"""))
            assertEquals("thiếu version → mặc định", def, LogEventControl.parse("""{"enabled":false}"""))
            assertEquals("JSON hỏng → mặc định", def, LogEventControl.parse("{not json"))
            assertEquals("rỗng → mặc định", def, LogEventControl.parse(""))
        }
    }

    @Test fun `C2b sampledOutCount dem event bi lay mau va dong stats in sampled=`() {
        LogEventControl.current = LogEventControl.Config(defaultSample = 0.0)
        assertEquals(0L, LogEventControl.sampledOutCount.get())
        repeat(3) { assertNull(LogEventControl.gate("screen_view", emptyMap(), "user-$it")) }
        assertNotNull("ad_revenue không bị lấy mẫu", LogEventControl.gate("ad_revenue", emptyMap(), "user-1"))
        assertEquals("chỉ event bị lấy mẫu được đếm", 3L, LogEventControl.sampledOutCount.get())
        LogEventControl.resetForTest()
        LogEventControl.current = LogEventControl.Config(defaultSample = 0.5)
        val kept = (0 until 200).count { LogEventControl.gate("screen_view", emptyMap(), "user-$it") != null }
        assertTrue("rate 0.5 giữ một phần: $kept", kept in 1..199)
        assertEquals("rate 0.5: chỉ event bị bỏ được đếm, event giữ thì không", (200 - kept).toLong(), LogEventControl.sampledOutCount.get())
        val stats = java.io.File("src/main/java/com/app/plugin/logevent/SessionTracker.kt").readText()
        assertTrue("dòng stats có sampled=", "sampled=\${LogEventControl.sampledOutCount.get()}" in stats)
        LogEventControl.resetForTest()
        assertEquals("reset về 0", 0L, LogEventControl.sampledOutCount.get())
    }

    @Test fun `C2 ad_revenue khong bao gio bi lay mau`() {
        val cfg = LogEventControl.Config(defaultSample = 0.0, sample = mapOf("ad_revenue" to 0.0, "brain_decision" to 0.0))
        LogEventControl.current = cfg
        val required = DurableEvents.DURABLE + setOf("brain_decision", "brain_feedback", "iap_purchase")
        assertTrue("NEVER_SAMPLE ⊇ DURABLE ∪ {brain, iap}", LogEventControl.NEVER_SAMPLE.containsAll(required))
        assertTrue(EventNames.AD_REVENUE in LogEventControl.NEVER_SAMPLE)
        for (name in LogEventControl.NEVER_SAMPLE) {
            assertEquals(name, 1.0, LogEventControl.rateFor(cfg, name), 0.0)
            repeat(200) { i ->
                val out = LogEventControl.gate(name, mapOf("k" to 1), "user-$i")
                assertNotNull("$name user-$i", out)
                assertFalse("$name không mang sample_rate", out!!.containsKey("sample_rate"))
            }
        }
        assertNull("event thường với default_sample 0 bị bỏ hết", LogEventControl.gate("screen_view", emptyMap(), "user-1"))
    }

    @Test fun `C3 cung user cung event - quyet dinh on dinh qua 1000 lan`() {
        for (u in listOf("u1", "u2", "u3", "u4")) {
            val first = LogEventControl.keeps(0.5, u, "screen_view")
            repeat(1000) { assertEquals("$u lần $it", first, LogEventControl.keeps(0.5, u, "screen_view")) }
        }
        val cfg = LogEventControl.Config(defaultSample = 0.3)
        LogEventControl.current = cfg
        for (i in 0 until 300) {
            val u = "user-$i"
            val chain = listOf("ad_request", "ad_fill", "ad_show", "screen_view").map { LogEventControl.gate(it, emptyMap(), u) != null }
            assertEquals("$u: cả chuỗi cùng giữ hoặc cùng bỏ", 1, chain.toSet().size)
            if (LogEventControl.keeps(0.1, u, "ad_fill")) assertTrue("lồng: giữ ở 0.1 thì giữ ở 0.5", LogEventControl.keeps(0.5, u, "ad_fill"))
        }
        val outcomes = (0 until 200).map { LogEventControl.keeps(0.5, "user-$it", "screen_view") }.toSet()
        assertEquals("rate 0.5 phải có cả giữ lẫn bỏ", setOf(true, false), outcomes)
    }

    @Test fun `C4 rate 0_25 tren 10000 user - 2300 den 2700 giu`() {
        val kept = (0 until 10_000).count { LogEventControl.keeps(0.25, "user-$it", "screen_view") }
        assertTrue("giữ $kept", kept in 2300..2700)
        assertEquals(0, (0 until 1000).count { LogEventControl.keeps(0.0, "user-$it", "screen_view") })
        assertEquals(1000, (0 until 1000).count { LogEventControl.keeps(1.0, "user-$it", "screen_view") })
    }

    @Test fun `C5 enabled false - DROPPED, decision log giu dia`() {
        ServerEventCapture().use { s ->
            LogEventControl.current = LogEventControl.Config(enabled = false)
            val results = mutableListOf<Boolean>()
            val outcomes = mutableListOf<EventOutcome>()
            mockStatic(Log::class.java).use {
                LogEventServer.logBrainDecision(
                    decisionId = "d_1", decisionPoint = "interstitial", chosenActionId = "show",
                    chosenActionMode = "explore", chosenActionProbability = 0.5, eligibleActions = "show,skip",
                    guardrailsApplied = "", segment = "s", segmentConfidence = 1.0, scenario = "x",
                    isHoldout = 0, policyVersion = "p1", onResult = { results += it }
                )
                LogEventTransport.sendEvent("screen_view", mapOf("a" to 1), { outcomes += it })
            }
            Thread.sleep(150)
            assertEquals("onResult(false) → bản ghi ở lại trên đĩa", listOf(false), results)
            assertEquals(listOf(EventOutcome.DROPPED), outcomes)
            assertEquals(0, s.allRequests().size)
            assertEquals(0, LogEventServer.pendingEventsForTest())
            // Replay bền / xả hàng giữ đi qua submitNow: enabled=false cũng phải chặn (R-7 I2).
            LogEventTransport.submitNow(LogEventQueue.Item("ad_revenue", "{}", { outcomes += it }))
            assertEquals(listOf(EventOutcome.DROPPED, EventOutcome.DROPPED), outcomes)
            assertEquals(0, s.allRequests().size)
            assertEquals(0, LogEventServer.pendingEventsForTest())
        }
    }

    @Test fun `C6 sample_rate trong eventData khi rate nho hon 1`() {
        ServerEventCapture().use { s ->
            LogEventControl.current = LogEventControl.Config(sample = mapOf("sampled_evt" to 0.5))
            val user = (0 until 1000).map { "user-$it" }.first { LogEventControl.keeps(0.5, it, "sampled_evt") }
            LogEventServerHelper.userIdSource = { user }
            LogEventTransport.sendEvent("sampled_evt", mapOf("a" to 1), null)
            LogEventTransport.sendEvent("plain_evt", mapOf("a" to 1), null)
            val sampled = s.await("sampled_evt", 1)
            val plain = s.await("plain_evt", 1)
            assertEquals(1, sampled.size)
            assertTrue("có sample_rate", sampled[0].has("sample_rate"))
            assertEquals(0.5, sampled[0].get("sample_rate").asDouble, 0.0)
            assertEquals(1, sampled[0].get("a").asInt)
            assertEquals(1, plain.size)
            assertFalse("rate 1 không mang sample_rate", plain[0].has("sample_rate"))
        }
    }
}
