package com.app.plugin.brain

import java.util.concurrent.ConcurrentHashMap

/**
 * Chống click-fraud tự động, chắt lọc từ pattern đã quan sát production trong bản teardown
 * "All Document Reader" (Solara) — `config_prevent_ad_click`: >6 click ads trong 300s thì khoá
 * toàn bộ ad 1800s. Đây là pattern tốt đáng học: bảo vệ tài khoản AdMob/mediation khỏi invalid
 * traffic mà không cần chờ network phát hiện — chặn ngay tại client trước khi request được gửi.
 *
 * Theo pattern `AdTracker.kt` đã có sẵn trong repo (object + ConcurrentHashMap in-memory).
 * Default 6/300s/1800s là giá trị đã quan sát chạy production thật — dùng làm điểm khởi đầu
 * hợp lý, KHÔNG phải giá trị bắt buộc; production nên nạp từ policy artifact khi có.
 */
internal object ClickFraudGuard {

    var maxClicksPerWindow: Int = 6
    var windowMs: Long = 300_000L
    var lockoutMs: Long = 1_800_000L

    private data class ClickWindow(var windowStartMs: Long, var count: Int)

    private val clickState = ConcurrentHashMap<String, ClickWindow>()
    private val lockedUntilMs = ConcurrentHashMap<String, Long>()

    /** Gọi mỗi khi user click vào một ad (không phải mỗi impression). */
    fun registerClick(playerId: String, nowMs: Long = System.currentTimeMillis()) {
        val window = clickState.getOrPut(playerId) { ClickWindow(nowMs, 0) }
        synchronized(window) {
            if (nowMs - window.windowStartMs > windowMs) {
                window.windowStartMs = nowMs
                window.count = 0
            }
            window.count += 1
            if (window.count > maxClicksPerWindow) {
                lockedUntilMs[playerId] = nowMs + lockoutMs
            }
        }
    }

    fun isLocked(playerId: String, nowMs: Long = System.currentTimeMillis()): Boolean {
        val until = lockedUntilMs[playerId] ?: return false
        return nowMs < until
    }
}
