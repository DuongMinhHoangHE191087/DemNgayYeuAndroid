package com.app.plugin.ads

import android.os.Handler
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.isNull
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mockConstruction
import org.mockito.Mockito.`when`

/**
 * Đồng hồ ảo cho mọi `Handler` dựng trong [MainHandlerTimers.run]: `postDelayed` xếp runnable theo mốc hết hạn,
 * `post` xếp ngay; test [advanceTo] một mốc (ms) để chạy đúng thứ tự các hẹn giờ tới mốc đó, hoặc [runPosted] để
 * chạy các `post` chưa chạy. Dùng cho watchdog của slot (60 s), của placement (90 s) và các `Handler.post` của
 * vòng waterfall trong `AdsHelper` (Task I review C1, I-1, I-2).
 */
internal class MainHandlerTimers {
    private class Due(val runnable: Runnable, val atMs: Long, val seq: Long, val owner: Handler? = null)

    private val queue = mutableListOf<Due>()
    private var seq = 0L
    var nowMs = 0L
        private set

    val pendingDelays: List<Long> get() = queue.filter { it.atMs > nowMs }.map { it.atMs - nowMs }

    /** Số runnable đang chờ, kể cả `post` (hẹn 0 ms) mà [pendingDelays] không đếm. */
    val pendingCount: Int get() = queue.size

    fun schedule(runnable: Runnable, delayMs: Long, owner: Handler? = null) {
        queue += Due(runnable, nowMs + delayMs, seq++, owner)
    }

    fun cancel(runnable: Runnable) {
        queue.removeIf { it.runnable === runnable }
    }

    /** `removeCallbacksAndMessages(null)` của [owner]: bỏ mọi runnable handler đó đã xếp. */
    fun cancelAll(owner: Handler) {
        queue.removeIf { it.owner === owner }
    }

    /** Chạy mọi hẹn giờ có mốc <= [atMs], theo mốc rồi theo thứ tự xếp; runnable xếp thêm trong lúc chạy cũng được xét. */
    fun advanceTo(atMs: Long) {
        require(atMs >= nowMs)
        while (true) {
            val next = queue.filter { it.atMs <= atMs }.minWithOrNull(compareBy({ it.atMs }, { it.seq })) ?: break
            queue.remove(next)
            nowMs = maxOf(nowMs, next.atMs)
            next.runnable.run()
        }
        nowMs = atMs
    }

    /** Chạy các `post` (hẹn 0 ms) đang chờ, không đổi giờ. */
    fun runPosted() = advanceTo(nowMs)

    companion object {
        /**
         * [honorRemoveAll] = true: `removeCallbacksAndMessages(null)` bỏ thật các runnable của đúng handler đó (test
         * banner 1.5.0 Task 6 đếm hẹn giờ còn lại sau `hideBanner`). Mặc định giữ hành vi cũ (no-op) cho test sẵn có.
         */
        fun <T> run(honorRemoveAll: Boolean = false, block: (MainHandlerTimers) -> T): T {
            val timers = MainHandlerTimers()
            com.app.plugin.ads.core.UnitGate.resetForTest { timers.nowMs } // 2.4.4: cổng unit dùng đồng hồ ảo, handler mới
            return mockConstruction(Handler::class.java) { handler, _ ->
                `when`(handler.postDelayed(any(Runnable::class.java), anyLong())).thenAnswer {
                    timers.schedule(it.arguments[0] as Runnable, it.arguments[1] as Long, handler)
                    true
                }
                `when`(handler.post(any(Runnable::class.java))).thenAnswer {
                    timers.schedule(it.arguments[0] as Runnable, 0L, handler)
                    true
                }
                doAnswer { inv -> timers.cancel(inv.arguments[0] as Runnable); null }
                    .`when`(handler).removeCallbacks(any(Runnable::class.java))
                if (honorRemoveAll) {
                    doAnswer { timers.cancelAll(handler); null }
                        .`when`(handler).removeCallbacksAndMessages(isNull())
                }
            }.use { block(timers) }
        }
    }
}
