package com.app.plugin.ads

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Hàng chờ của một mạng quảng cáo: lệnh load đến trước khi SDK của mạng đó khởi tạo xong được
 * giữ lại, rồi chạy một lần theo thứ tự đến khi [markReady]. Lệnh đến sau đó chạy ngay.
 *
 * AdMob đã chạy cơ chế này từ trước (`AdsMobMy.runWhenSdkReady`). Từ 1.3.0 Meta và AppLovin MAX
 * dùng cùng lớp này, vì cả ba mạng chỉ khởi tạo sau consent (xem [AdSdkStartGate]): load của
 * Meta hay MAX mà chạy trước đó thì chính SDK của mạng sẽ tự khởi tạo, vượt qua cổng consent.
 *
 * Mạng sẽ không khởi tạo trong phiên vì consent (P1, DOC/66: Meta AN với `DENIED` trong vùng GDPR) thì
 * `AdSdkStartup` gọi [close]: load đang giữ có `onClosed` được báo, load đến sau báo ngay, để placement báo
 * fail và waterfall đi tiếp thay vì chờ mãi. [reopen] khi mạng được khởi tạo sau đó.
 */
internal class SdkReadyQueue {
    private val lock = Any()

    /** Một load đang giữ: [action] khi mạng sẵn sàng, hoặc [onClosed] (nếu có) khi mạng sẽ không khởi tạo. */
    private class Held(val action: () -> Unit, val onClosed: (() -> Unit)?)

    private val pending: MutableList<Held> = mutableListOf()

    @Volatile
    var isReady: Boolean = false
        private set

    /** Mạng sẽ không khởi tạo (đã [close], chưa [reopen]/[markReady]). */
    @Volatile
    var isClosed: Boolean = false
        private set

    /**
     * Chạy [action] khi mạng sẵn sàng. Không có đường báo fail: hàng đã [close] thì [action] vẫn được giữ tới
     * [markReady]. Hàng có thể bị đóng thì dùng bản hai tham số.
     */
    fun runWhenReady(action: () -> Unit) = enqueue(Held(action, onClosed = null))

    /**
     * Như [runWhenReady], nhưng hàng đã [close] (hoặc đóng khi load còn giữ) thì gọi [onClosed] thay vì giữ mãi.
     * Mỗi load nhận đúng một trong hai: [action] hoặc [onClosed].
     */
    fun runWhenReady(action: () -> Unit, onClosed: () -> Unit) = enqueue(Held(action, onClosed))

    private fun enqueue(held: Held) {
        val runNow: (() -> Unit)? = synchronized(lock) {
            when {
                isReady -> held.action
                isClosed && held.onClosed != null -> held.onClosed
                else -> {
                    pending.add(held)
                    null
                }
            }
        }
        runNow?.invoke()
    }

    /**
     * Mạng sẽ không khởi tạo: báo `onClosed` cho mọi load đang giữ có nó (mỗi load một lần, ngoài khoá), load đến
     * sau báo ngay. Đã sẵn sàng hoặc đã đóng thì no-op.
     */
    fun close() = closeDeferred().invoke()

    /**
     * Như [close], nhưng chưa báo: hàng đóng ngay, các `onClosed` của load đang giữ được trả về thành một việc, chạy
     * đúng một lần dù gọi mấy lần. Cho người gọi quyết định và đóng trong cùng khoá của mình, rồi báo placement ngoài
     * khoá đó (`AdSdkStartup`, review C Minor 4): không gọi callback của placement khi đang giữ khoá.
     */
    fun closeDeferred(): () -> Unit {
        val refused: List<Held>
        synchronized(lock) {
            if (isReady || isClosed) return {}
            isClosed = true
            refused = pending.filter { it.onClosed != null }
            pending.removeAll(refused)
        }
        val notified = AtomicBoolean(false)
        return { if (notified.compareAndSet(false, true)) refused.forEach { it.onClosed?.invoke() } }
    }

    /** Mạng được khởi tạo sau khi đã [close]: load lại được giữ tới [markReady]. */
    fun reopen() {
        synchronized(lock) { isClosed = false }
    }

    /** Như lúc mới tạo: chưa sẵn sàng, chưa đóng, không load nào đang giữ. Chỉ cho test (`AdSdkStartup.resetForTest`). */
    @JvmSynthetic
    internal fun resetForTest() {
        synchronized(lock) {
            isReady = false
            isClosed = false
            pending.clear()
        }
    }

    /** Gọi lại là no-op. Các lệnh chạy ngoài khoá, nên lệnh nào xếp thêm lệnh khác vẫn chạy được. */
    fun markReady() {
        val actions: List<() -> Unit>
        synchronized(lock) {
            if (isReady) return
            isReady = true
            isClosed = false
            actions = pending.map { it.action }
            pending.clear()
        }
        actions.forEach { it() }
    }
}
