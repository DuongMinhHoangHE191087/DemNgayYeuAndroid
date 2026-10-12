package com.app.plugin.brain

import android.util.Log
import com.app.plugin.MonetizationSdk
import com.app.plugin.logevent.EventNames
import com.app.plugin.logevent.LogEventServer
import com.app.plugin.logevent.ServerEventCapture
import com.app.plugin.until.LogUtil
import com.google.gson.JsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentMatchers.contains
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.times

/**
 * Review D (#13): `user_id` telemetry == id brain phân nhóm, trên mọi đường vào phiên.
 *
 * - I2 (FD1): resume sau ≥ 30 phút ở nền (`AppPluginBase.onStart` → `AdsBrain.onSessionStart()`) giữ id app đã
 *   `configure`; trước sửa brain về `BrainStorage.playerId()` (đổi bucket, đổi holdout) trong khi `user_id` vẫn là id app.
 * - I1 (FD2): app theo DOC/38 §B bước 2 gọi thẳng `AdsBrain.onSessionStart(id)` (không `configure`): id đó cũng là
 *   nguồn `user_id`; trước sửa brain phân nhóm theo id app còn `user_id` là UUID của SDK trên mọi hàng.
 *
 * `experiment_exposure` tới server được bắt bằng [ServerEventCapture]; body dựng trên IO nên chờ có hạn.
 */
class SessionPlayerIdTest {
    @get:Rule val license = LicenseTestRule()

    @Before fun setUp() = reset()
    @After fun tearDown() = reset()

    private fun reset() {
        PlayerIdSource.resetForTest()
        MonetizationSdk.resetForTest()
        AdsBrain.enabled = false
        AdsBrain.rolloutFraction = 1.0
        HoldoutManager.holdoutFraction = 0.05
        HoldoutManager.salt = "brain_holdout_v1"
        BrainPolicy.clear()
    }

    private fun configure(id: String, brainEnabled: Boolean = false) =
        MonetizationSdk.applyForTest(MonetizationSdk.Config().apply {
            playerId = id
            this.brainEnabled = brainEnabled
        })

    private val JsonObject.userId: String get() = getAsJsonObject("userProperty").get("user_id").asString
    private val JsonObject.bucket: Int get() = getAsJsonObject("eventData").get("holdout_bucket").asInt

    // ═══ FD1 (review D I2): resume giữ id đã configure ═══

    @Test
    fun `resume sau configure thi onSessionStart khong tham so giu id cua app`() {
        configure("app-1")

        AdsBrain.onSessionStart()   // AppPluginBase.onStart sau ≥ 30 phút ở nền

        assertEquals("app-1", AdsBrain.currentState().playerId)
    }

    @Test
    fun `resume sau configure thi experiment_exposure mang id cua app va bucket cua chinh id do`() {
        ServerEventCapture().use { server ->
            configure("app-1", brainEnabled = true)   // exposure #1: phiên của configure
            AdsBrain.onSessionStart()                  // exposure #2: resume

            val rows = server.awaitRequests(EventNames.EXPERIMENT_EXPOSURE, 2)

            assertEquals(2, rows.size)
            for (row in rows) {
                assertEquals("user_id của exposure", "app-1", row.userId)
                assertEquals("bucket = bucket của user_id (reconcile lớp 1)", HoldoutManager.bucket("app-1"), row.bucket)
            }
        }
    }

    // ═══ FD2 (review D I1): app gọi thẳng onSessionStart(id) ═══

    @Test
    fun `app goi thang onSessionStart(id) khong configure thi user_id trong body la id do`() {
        ServerEventCapture().use { server ->
            AdsBrain.onSessionStart("app-2")
            LogEventServer.logEvent("sau_session", "home")

            val row = server.awaitRequests(EventNames.BEHAVIOR, 1)
                .single { it.getAsJsonObject("eventData").get("id_item").asString == "sau_session" }

            assertEquals("app-2", row.userId)
            assertEquals("app-2", PlayerIdSource.current())
        }
    }

    @Test
    fun `app goi thang onSessionStart(id) roi resume thi van id do`() {
        AdsBrain.onSessionStart("app-2")

        AdsBrain.onSessionStart()

        assertEquals("app-2", AdsBrain.currentState().playerId)
    }

    @Test
    fun `app goi thang onSessionStart(id) khi brain bat thi exposure mang id do va bucket cua no`() {
        ServerEventCapture().use { server ->
            AdsBrain.enabled = true
            AdsBrain.onSessionStart("app-2")

            val row = server.awaitRequests(EventNames.EXPERIMENT_EXPOSURE, 1).single()

            assertEquals("app-2", row.userId)
            assertEquals(HoldoutManager.bucket("app-2"), row.bucket)
        }
    }

    /**
     * Id rỗng không phải id (cùng điều kiện với `configure`): brain và `user_id` dùng id đang có hiệu lực, không phân
     * nhóm theo chuỗi rỗng, và lỗi được log (không câm).
     */
    @Test
    fun `onSessionStart voi id rong thi dung id dang co hieu luc va logE`() = mockStatic(Log::class.java).use { log ->
        AdsBrain.onSessionStart("   ")

        assertEquals(BrainStorage.playerId(), AdsBrain.currentState().playerId)
        assertEquals(null, PlayerIdSource.override)
        log.verify { Log.e(eq(LogUtil.TAG), contains("AdsBrain.onSessionStart: playerId rỗng")) }
    }

    @Test
    fun `khong configure thi onSessionStart khong tham so dung id cua SDK va khong ghi override`() {
        AdsBrain.onSessionStart()

        assertEquals(BrainStorage.playerId(), AdsBrain.currentState().playerId)
        assertEquals(null, PlayerIdSource.override)
    }

    // ═══ Review N2: id có ký tự phân tách/xuống dòng làm hỏng dòng decision log (trường 0 `d_<id>_…` và trường 13) ═══

    /** Id chứa U+001F, `\n` hoặc `\r` không phải id dùng được (như id rỗng): log lỗi, giữ id đang có hiệu lực. */
    @Test
    fun `onSessionStart voi id co ky tu phan tach hoac xuong dong thi logE va giu id dang co`() =
        mockStatic(Log::class.java).use { log ->
            for (bad in listOf("a\u001Fb", "a\nb", "a\rb")) {
                AdsBrain.onSessionStart(bad)

                assertEquals(BrainStorage.playerId(), AdsBrain.currentState().playerId)
                assertEquals(null, PlayerIdSource.override)
            }
            log.verify({ Log.e(eq(LogUtil.TAG), contains("AdsBrain.onSessionStart: playerId")) }, times(3))
        }

    @Test
    fun `configure voi id co ky tu xuong dong thi khong ghi override va logE`() = mockStatic(Log::class.java).use { log ->
        configure("app-1")
        configure("x\ny")

        assertEquals("app-1", PlayerIdSource.override)
        assertEquals("app-1", AdsBrain.currentState().playerId)
        log.verify { Log.e(eq(LogUtil.TAG), contains("configure: playerId")) }
    }
}
