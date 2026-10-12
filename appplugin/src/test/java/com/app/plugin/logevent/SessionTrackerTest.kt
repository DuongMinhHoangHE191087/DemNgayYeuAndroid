package com.app.plugin.logevent

import com.google.gson.JsonParser
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * `session_end` (1.7.0 Task 5): `SessionTracker` đếm một lần foreground, `onBackground` gửi event rồi reset. Đồng hồ giả
 * (`nowMs`) để `duration_ms` đúng từng ms; event đi qua `ServerEventCapture` (cổng consent thường). T4 quét nguồn như
 * `LogEventInitOrderContractTest`.
 */
class SessionTrackerTest {
    private var clock = 0L

    @Before fun setUp() {
        SessionTracker.resetForTest()
        clock = 1_000L
        SessionTracker.nowMs = { clock }
    }

    @After fun tearDown() = SessionTracker.resetForTest()

    @Test fun `T1 ON_STOP phat session_end duration_ms tu ON_START`() {
        ServerEventCapture().use { server ->
            SessionTracker.onActivityStarted()   // app callback tới TRƯỚC ProcessLifecycle ON_START
            SessionTracker.onForeground()
            SessionTracker.onActivityStarted()
            clock += 42_500L
            SessionTracker.onBackground()

            val sent = server.await("session_end", 1)
            assertEquals("một session_end", 1, sent.size)
            val d = sent.single()
            assertEquals(42_500L, d.get("duration_ms").asLong)
            assertEquals(2, d.get("screens").asInt)
            assertEquals(1, d.get("fg_index").asInt)
            assertEquals("{}", d.get("ads_shown_json").asString)

            SessionTracker.onBackground()   // không có ON_START kèm theo: không gửi lần hai
            Thread.sleep(200)
            assertEquals("không gửi hai lần", 1, server.await("session_end", 1).size)
        }
    }

    @Test fun `T2 ads_shown_json dem theo dinh dang trong phien`() {
        ServerEventCapture().use { server ->
            SessionTracker.onForeground()
            AdEventLog.show("interstitial", "admob", "admob", "full_x", "unit", "imp-1")   // móc trong AdEventLog.show
            SessionTracker.onAdShown("banner")
            SessionTracker.onAdShown("interstitial")
            SessionTracker.onBackground()

            val sent = server.await("session_end", 1)
            assertEquals("một session_end", 1, sent.size)
            val json = JsonParser.parseString(sent.single().get("ads_shown_json").asString).asJsonObject
            assertEquals(mapOf("interstitial" to 2, "banner" to 1), json.entrySet().associate { it.key to it.value.asInt })
        }
    }

    @Test fun `T3 hai lan foreground fg_index 1 roi 2, bo dem reset`() {
        ServerEventCapture().use { server ->
            SessionTracker.onForeground()
            SessionTracker.onActivityStarted()
            SessionTracker.onAdShown("interstitial")
            SessionTracker.onBackground()
            clock += 10L
            SessionTracker.onForeground()
            SessionTracker.onBackground()

            val sent = server.await("session_end", 2)
            assertEquals("hai session_end", 2, sent.size)
            val all = sent.sortedBy { it.get("fg_index").asInt }
            assertEquals(listOf(1, 2), all.map { it.get("fg_index").asInt })
            assertEquals(1, all[0].get("screens").asInt)
            assertEquals(0, all[1].get("screens").asInt)
            assertEquals("{}", all[1].get("ads_shown_json").asString)
        }
    }

    @Test fun `T5 duration_ms do bang dong ho don dieu, khong phai gio he thong`() {
        val src = File("src/main/java/com/app/plugin/logevent/SessionTracker.kt")
        assertTrue("Không thấy ${src.absolutePath}", src.isFile)
        val code = src.readLines().filter { l -> l.trim().let { !it.startsWith("//") && !it.startsWith("*") && !it.startsWith("/*") } }
        assertTrue("nowMs mặc định phải là SystemClock.elapsedRealtime()", code.any { "SystemClock.elapsedRealtime()" in it })
        assertTrue("không dùng System.currentTimeMillis() cho duration_ms", code.none { "currentTimeMillis" in it })
    }

    @Test fun `T4 AppPluginBase onStop goi SessionTracker onBackground truoc AdsBrain onSessionEnd`() {
        val src = File("src/main/java/com/app/plugin/app/AppPluginBase.kt")
        assertTrue("Không thấy ${src.absolutePath} (test phải chạy từ gốc module)", src.isFile)
        val lines = src.readLines()
        fun line(re: Regex) = lines.indexOfFirst { l -> l.trim().let { !it.startsWith("//") && !it.startsWith("*") && re.containsMatchIn(it) } }
        val onStop = line(Regex("""override fun onStop\("""))
        val tracker = line(Regex("""SessionTracker\.onBackground\("""))
        val brain = line(Regex("""AdsBrain\.onSessionEnd\("""))
        assertTrue("thiếu onStop", onStop >= 0)
        assertTrue("onStop không gọi SessionTracker.onBackground", tracker > onStop)
        assertTrue("onStop không gọi AdsBrain.onSessionEnd", brain > onStop)
        assertTrue("SessionTracker.onBackground (dòng ${tracker + 1}) phải đứng trước AdsBrain.onSessionEnd (dòng ${brain + 1})", tracker < brain)
    }
}
