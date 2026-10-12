package com.app.plugin.brain

import java.util.concurrent.Executor

/**
 * Luồng nền "đứng yên" cho `DecisionLogger.ioExecutor`: giữ Runnable lại, chỉ chạy (trên luồng test) khi
 * test gọi [runAll]. [execute] gọi được từ luồng bất kỳ: callback kết quả của `LogEventServer` chạy trên
 * `Dispatchers.IO` và đẩy việc (vd. "replay next") vào đây. Việc như thế có thể tới trong lúc [runAll] còn
 * chạy và bị chạy luôn trong lần đó, nên [size] không cho biết nó đã tới hay chưa; chờ theo [submitted].
 */
internal class HeldExecutor : Executor {
    private val lock = Any()
    private val held = ArrayDeque<Runnable>()
    private var submittedCount = 0

    /** Số việc đang giữ, chưa chạy. */
    val size: Int get() = synchronized(lock) { held.size }

    /** Số việc đã nhận từ đầu, kể cả việc đã chạy. */
    val submitted: Int get() = synchronized(lock) { submittedCount }

    override fun execute(command: Runnable) {
        synchronized(lock) {
            held.addLast(command)
            submittedCount++
        }
    }

    /**
     * Chạy mọi việc đang giữ, kể cả việc chúng đẩy thêm vào trong lúc chạy. [pauseAfterEachMs] > 0: dừng sau mỗi
     * việc, như luồng test bị hệ điều hành tạm dừng — việc luồng khác đẩy vào trong lúc đó cũng bị chạy luôn ở đây.
     */
    fun runAll(pauseAfterEachMs: Long = 0) {
        while (true) {
            val next = synchronized(lock) { held.removeFirstOrNull() } ?: return
            next.run()
            if (pauseAfterEachMs > 0) Thread.sleep(pauseAfterEachMs)
        }
    }
}

/**
 * 1.8.0 F-P1 (D31): ghi hàng đợi và gửi sống của sink mặc định chạy trên `DecisionLogger.ioExecutor`; test cần chúng xong
 * ngay trong `log()` thì dùng executor trực tiếp (như `useDirectBillingExecutor` của T5). Trả executor cũ để trả lại.
 */
internal fun useDirectDecisionLogExecutor(): Executor =
    DecisionLogger.ioExecutor.also { DecisionLogger.ioExecutor = Executor { it.run() } }
