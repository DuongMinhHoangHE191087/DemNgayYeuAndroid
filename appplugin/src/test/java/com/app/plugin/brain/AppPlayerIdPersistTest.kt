package com.app.plugin.brain

import android.content.SharedPreferences
import com.app.plugin.iap.resetIapHelperForTest
import android.util.Log
import com.app.plugin.MonetizationSdk
import com.app.plugin.logevent.EventNames
import com.app.plugin.logevent.LogEventServer
import com.app.plugin.logevent.ServerEventCapture
import com.app.plugin.until.Constants
import com.app.plugin.until.PreferenceHelper
import com.google.gson.JsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic

/**
 * Review cuối I2: `user_id` của `session_start`/`first_open` ổn định với app truyền playerId.
 *
 * `AppPluginBase.initPlugin` gửi `session_start` (và `first_open`) trong `super.onCreate()`, trước khi app kịp gọi
 * `MonetizationSdk.configure { playerId }`; body dựng bất đồng bộ. Khi override chỉ ở bộ nhớ, mỗi cold start của app
 * truyền id mang UUID cài đặt hay id app tuỳ race → backend thấy hai user mỗi máy. Sửa: id hợp lệ cuối cùng app truyền
 * được lưu (`brain_app_player_id`), và `PlayerIdSource.current()` = override bộ nhớ → id app đã lưu → UUID của SDK.
 *
 * "Process mới" = reset bộ nhớ ([PlayerIdSource.resetForTest], `MonetizationSdk.resetForTest`), prefs giữ nguyên.
 */
class AppPlayerIdPersistTest {

    private val store = mutableMapOf<String, Any?>()
    private var previousPrefs: SharedPreferences? = null

    @Before fun setUp() {
        resetIapHelperForTest()
        previousPrefs = PreferenceHelper.getInstance().preferences
        PreferenceHelper.getInstance().preferences = inMemoryPrefs(store)
        newProcess()
    }

    @After fun tearDown() {
        resetIapHelperForTest()
        newProcess()
        PreferenceHelper.getInstance().preferences = previousPrefs
    }

    /** Như process mới: bộ nhớ sạch, prefs ([store]) giữ. `lastTimestamp = 0` để `session_start` được gửi. */
    private fun newProcess() {
        PlayerIdSource.resetForTest()
        MonetizationSdk.resetForTest()
        AdsBrain.enabled = false
        LogEventServer::class.java.getDeclaredField("lastTimestamp").apply { isAccessible = true }.setLong(null, 0L)
    }

    private fun configure(id: String) =
        MonetizationSdk.applyForTest(MonetizationSdk.Config().apply { playerId = id })

    private val JsonObject.userId: String get() = getAsJsonObject("userProperty").get("user_id").asString

    @Test
    fun `process sau chua configure thi session_start va first_open mang id app da luu`() {
        configure("app-9")   // process 1
        newProcess()         // process 2: initPlugin gửi session_start trước configure
        store[Constants.Common.TIME_OPEN] = 1

        ServerEventCapture().use { server ->
            LogEventServer.sessionStart()

            assertEquals("app-9", server.awaitRequests(EventNames.SESSION_START, 1).single().userId)
            assertEquals("app-9", server.awaitRequests(EventNames.FIRST_OPEN, 1).single().userId)
        }
        assertEquals("app-9", PlayerIdSource.current())
    }

    @Test
    fun `app goi thang onSessionStart(id) thi id do cung duoc luu cho process sau`() {
        AdsBrain.onSessionStart("app-7")
        newProcess()

        assertEquals("app-7", PlayerIdSource.current())
        AdsBrain.onSessionStart()   // mở app: brain phân nhóm theo cùng id với user_id
        assertEquals("app-7", AdsBrain.currentState().playerId)
    }

    @Test
    fun `app doi id (dang nhap tai khoan khac) thi id moi ghi de`() {
        configure("app-9")
        configure("app-10")
        newProcess()

        assertEquals("app-10", PlayerIdSource.current())
    }

    @Test
    fun `id khong hop le khong duoc luu, giu id da luu`() = mockStatic(Log::class.java).use {
        configure("app-9")
        configure("bad\nid")
        AdsBrain.onSessionStart("   ")
        newProcess()

        assertEquals("app-9", PlayerIdSource.current())
    }

    @Test
    fun `override bo nho thang id da luu`() {
        configure("app-9")
        newProcess()

        PlayerIdSource.override = "app-mem"

        assertEquals("app-mem", PlayerIdSource.current())
    }

    @Test
    fun `app khong bao gio configure thi session_start mang UUID cua SDK nhu cu`() {
        ServerEventCapture().use { server ->
            LogEventServer.sessionStart()

            val userId = server.awaitRequests(EventNames.SESSION_START, 1).single().userId
            assertEquals(BrainStorage.playerId(), userId)
        }
        assertFalse("không lưu id app khi app không truyền", store.containsKey("brain_app_player_id"))
        assertNotEquals("", PlayerIdSource.current())
    }

    /** Prefs trong bộ nhớ: getter đọc [store] (hoặc mặc định), mọi `put*`/`remove` của editor ghi thẳng vào [store]. */
    private fun inMemoryPrefs(store: MutableMap<String, Any?>): SharedPreferences {
        val editor = mock(SharedPreferences.Editor::class.java) { inv ->
            val name = inv.method.name
            when {
                name.startsWith("put") -> store[inv.getArgument(0)] = inv.getArgument<Any?>(1)
                name == "remove" -> store.remove(inv.getArgument<String>(0))
                name == "clear" -> store.clear()
            }
            when (inv.method.returnType) {
                SharedPreferences.Editor::class.java -> inv.mock
                java.lang.Boolean.TYPE -> true   // commit()
                else -> null
            }
        }
        return mock(SharedPreferences::class.java) { inv ->
            when (inv.method.name) {
                "edit" -> editor
                "contains" -> store.containsKey(inv.getArgument(0))
                "getString", "getInt", "getBoolean", "getFloat", "getLong" ->
                    store[inv.getArgument(0)] ?: inv.getArgument<Any?>(1)
                else -> null
            }
        }
    }
}
