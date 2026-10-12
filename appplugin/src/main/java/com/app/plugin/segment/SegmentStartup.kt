package com.app.plugin.segment

import com.app.plugin.until.LogUtil
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * Khởi động bất đồng bộ của segment (2.2.0 SEG fix 1, D27-220): engine đọc ~36 khoá Remote Config, lần đọc đầu có thể
 * chờ cache config đã kích hoạt trên đĩa (ANR đã gặp, `FirHelperConfig.fetchRemoteConfig`) nên phần nạp chạy trên một
 * luồng nền, không bao giờ trong `initPlugin` trên main. Trước khi nạp xong engine giữ mặc định đã biên dịch (guardrail
 * 0 = tắt), `SegmentHooks.blocksFullscreen` trả false, và event tới sớm xếp hàng trong RAM (trần [QUEUE_CAP], bỏ cũ nhất)
 * rồi phát lại đúng thứ tự trên luồng nạp. Chưa [start] (SDK chưa init) thì event bị bỏ như trước init.
 */
internal object SegmentStartup {
    const val QUEUE_CAP = 100
    private const val IDLE = 0
    private const val STARTING = 1
    private const val LOADED = 2

    private val lock = Any()
    private val queue = ArrayDeque<() -> Unit>()
    @Volatile private var state = IDLE
    private val defaultExecutor: Executor by lazy {
        Executors.newSingleThreadExecutor { r -> Thread(r, "appplugin-segment").apply { isDaemon = true } }
    }

    /** Luồng nạp; null = luồng nền riêng của segment (test thay). */
    @Volatile internal var executor: Executor? = null

    val isLoaded: Boolean get() = state == LOADED

    val queued: Int get() = synchronized(lock) { queue.size }

    /** Một lần mỗi process: chạy [load] trên luồng nạp, rồi phát lại hàng đợi và mở cổng. */
    fun start(load: () -> Unit) {
        synchronized(lock) {
            if (state != IDLE) return
            state = STARTING
        }
        (executor ?: defaultExecutor).execute {
            try {
                load()
            } catch (e: Throwable) {
                LogUtil.logE("segment: init failed: ${e.message}")
            } finally {
                drain() // luôn mở cổng: không bao giờ kẹt ở STARTING
            }
        }
    }

    /** Đang nạp: xếp [action] (đầy thì bỏ cái cũ nhất) và trả true; còn lại trả false, không làm gì. */
    fun queueIfStarting(action: () -> Unit): Boolean = synchronized(lock) {
        if (state != STARTING) return false
        if (queue.size >= QUEUE_CAP) queue.removeFirst()
        queue.addLast(action)
        true
    }

    /** Đã nạp: chạy ngay; đang nạp: xếp hàng; chưa start: bỏ. */
    fun runOrQueue(action: () -> Unit) {
        if (!queueIfStarting(action) && isLoaded) action()
    }

    private fun drain() {
        while (true) {
            val next = synchronized(lock) { queue.removeFirstOrNull() ?: run { state = LOADED; null } } ?: return
            try {
                next()
            } catch (e: Throwable) {
                LogUtil.logE("segment: replay failed: ${e.message}")
            }
        }
    }

    internal fun resetForTest() = synchronized(lock) {
        queue.clear()
        state = IDLE
    }
}
