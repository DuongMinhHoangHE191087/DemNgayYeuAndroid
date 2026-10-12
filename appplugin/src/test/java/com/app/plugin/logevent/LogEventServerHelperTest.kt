package com.app.plugin.logevent

import com.app.plugin.MonetizationSdk
import com.app.plugin.iap.resetIapHelperForTest
import com.app.plugin.brain.BrainStorage
import com.app.plugin.brain.PlayerIdSource
import com.google.gson.JsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * `user_id` của telemetry server = player id đang có hiệu lực của brain (#13, DOC/66 mục 4): id app truyền qua
 * `MonetizationSdk.configure { playerId }` nếu có, nếu không thì `BrainStorage.playerId()`. Backend giả định
 * `user_id == playerId` (`backend/app/reconcile.py` lớp 1 tính lại bucket theo `user_id`, `metrics.py` nhóm holdout
 * theo `user_id`); trước đây SDK gửi ANDROID_ID nên đối soát lệch gần như mọi hàng
 * (`.superpowers/sdd/research-startup-telemetry.md` mục B3). Web shop (`PurchaseRouter`) dùng cùng id.
 *
 * Unit test không gọi `LogEventServerHelper.init` (cần `Context` thật): trường khác của `userProperty` rỗng, còn
 * `user_id` phải có vì nó được đọc lúc dựng body, không lúc `init`.
 */
class LogEventServerHelperTest {

    @Before
    fun setUp() {
        resetIapHelperForTest()
        PlayerIdSource.override = null
        MonetizationSdk.resetForTest()
    }

    @After
    fun tearDown() {
        resetIapHelperForTest()
        PlayerIdSource.override = null
        MonetizationSdk.resetForTest()
    }

    private fun configurePlayerId(id: String) {
        MonetizationSdk.applyForTest(MonetizationSdk.Config().apply { playerId = id })
    }

    /** `userProperty.user_id` của event `behavior` có `id_item` = [item]. */
    private fun userIdOf(requests: List<JsonObject>, item: String): String {
        val request = requests.single { it.getAsJsonObject("eventData").get("id_item").asString == item }
        return request.getAsJsonObject("userProperty").get("user_id").asString
    }

    @Test
    fun `app khong truyen playerId thi user_id trong body la BrainStorage playerId`() {
        ServerEventCapture().use { server ->
            LogEventServer.logEvent("khong_configure", "home")

            val requests = server.awaitRequests(EventNames.BEHAVIOR, 1)

            val expected = BrainStorage.playerId()
            assertTrue("BrainStorage.playerId() phải là id không rỗng", expected.isNotBlank())
            assertEquals(expected, userIdOf(requests, "khong_configure"))
        }
    }

    @Test
    fun `configure playerId sau init thi event dung sau do mang id cua app`() {
        ServerEventCapture().use { server ->
            LogEventServer.logEvent("truoc_configure", "home")
            // Chờ body của event đầu dựng xong: body dựng trên IO, dựng muộn sẽ đọc id mới.
            server.awaitRequests(EventNames.BEHAVIOR, 1)

            configurePlayerId("acct-42")
            LogEventServer.logEvent("sau_configure", "home")
            val requests = server.awaitRequests(EventNames.BEHAVIOR, 2)

            assertEquals(BrainStorage.playerId(), userIdOf(requests, "truoc_configure"))
            assertEquals("acct-42", userIdOf(requests, "sau_configure"))
        }
    }

    @Test
    fun `playerId rong trong configure thi van dung BrainStorage playerId`() {
        configurePlayerId("   ")

        assertEquals(BrainStorage.playerId(), LogEventServerHelper.generateGlobalPropertiesData().userId)
        assertEquals(BrainStorage.playerId(), LogEventServerHelper.currentUserId())
    }

    @Test
    fun `src main khong con doc ANDROID_ID`() {
        val mainSrc = File("src/main/java")
        // Chạy sai thư mục làm việc thì phép quét rỗng và test PASS giả: dừng ngay.
        assertTrue("Không thấy ${mainSrc.absolutePath} (test phải chạy từ gốc module)", mainSrc.isDirectory)
        val forbidden = Regex("""\bANDROID_ID\b|Settings\.Secure\b""")

        val hits = mainSrc.walkTopDown()
            .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { i, line ->
                    val code = line.trim()
                    val isComment = code.startsWith("//") || code.startsWith("*") || code.startsWith("/*")
                    if (!isComment && forbidden.containsMatchIn(code)) "${file.relativeTo(mainSrc)}:${i + 1}: $code" else null
                }
            }
            .toList()

        assertTrue("src/main còn đọc ANDROID_ID / Settings.Secure (#13):\n" + hits.joinToString("\n"), hits.isEmpty())
    }
}
