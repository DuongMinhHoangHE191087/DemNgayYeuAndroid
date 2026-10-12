package com.app.plugin.brain

import com.app.plugin.license.LicenseGate
import org.junit.rules.ExternalResource

/**
 * 2.2.0 LIC (D16-220): brain chỉ quyết khi giấy phép `Valid`; JVM không có token nên verdict khởi đầu `Pending`.
 * Lớp test logic brain dùng `@get:Rule val license = LicenseTestRule()`: `Valid` trước mỗi test, process mới sau.
 * Bản sao ở `appplugin-game/src/test` (như `ServerEventCapture`): lớp test của gốc không vào classpath test của game.
 */
class LicenseTestRule : ExternalResource() {
    override fun before() {
        clearHeldSignals()
        LicenseGate.resetForTest(LicenseGate.Verdict.Valid("test", Long.MAX_VALUE / 2))
    }

    override fun after() {
        LicenseGate.resetForTest()
    }

    /** Hàng đợi tín hiệu giữ của AdsBrain là trạng thái process (private): xoá bằng reflection để không rò giữa các lớp test (m15). */
    private fun clearHeldSignals() {
        try {
            val held = AdsBrain::class.java.getDeclaredField(HELD_FIELD).apply { isAccessible = true }.get(AdsBrain)
            (held as MutableCollection<*>).clear()
        } catch (e: NoSuchFieldException) {
            throw AssertionError("LicenseTestRule: AdsBrain không còn trường '$HELD_FIELD' (đã đổi tên?) — sửa HELD_FIELD", e)
        } catch (e: IllegalAccessException) {
            throw AssertionError("LicenseTestRule: không đọc được AdsBrain.$HELD_FIELD bằng reflection", e)
        }
    }

    private companion object {
        const val HELD_FIELD = "heldSignals"
    }
}
