package com.app.plugin.consent

/**
 * Cổng một-lần cho luồng consent: chỉ người gọi đầu tiên khởi động luồng UMP, mọi callback
 * đến trước khi xong được xếp hàng, callback đến sau khi xong chạy ngay.
 *
 * Lấy từ hai app fork (Speaker Clean, Invoice Maker), nơi nó giải quyết lỗi mở hai form
 * consent chồng nhau khi Activity được tạo lại giữa chừng.
 */
internal class ConsentGate {
    private val lock = Any()
    private var hasStarted = false
    private var isFinished = false
    private val pendingCallbacks: MutableList<() -> Unit> = mutableListOf()

    /** @return `true` khi người gọi này phải khởi động luồng UMP (chỉ đúng một lần). */
    fun start(onDone: () -> Unit): Boolean {
        val alreadyFinished: Boolean
        val shouldStart: Boolean
        synchronized(lock) {
            alreadyFinished = isFinished
            if (!alreadyFinished) {
                pendingCallbacks.add(onDone)
            }
            shouldStart = !hasStarted
            if (shouldStart) {
                hasStarted = true
            }
        }
        if (alreadyFinished) {
            onDone()
        }
        return shouldStart
    }

    /** Mở cổng: chạy mọi callback đang chờ đúng một lần. Gọi lại là no-op. */
    fun finish() {
        val callbacks: List<() -> Unit>
        synchronized(lock) {
            if (isFinished) return
            isFinished = true
            callbacks = pendingCallbacks.toList()
            pendingCallbacks.clear()
        }
        callbacks.forEach { it() }
    }

    val isOpen: Boolean
        get() = synchronized(lock) { isFinished }
}
