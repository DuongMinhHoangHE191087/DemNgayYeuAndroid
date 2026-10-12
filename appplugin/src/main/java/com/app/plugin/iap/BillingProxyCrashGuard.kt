package com.app.plugin.iap

import android.os.Looper
import android.os.Process
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.exitProcess

/**
 * Chặn crash đã biết của Play Billing 9.0.0 (1.8.0 Task 11, D8/F9; port từ `BillingProxyCrashGuard` của Speaker Clean).
 *
 * Process chết khi `PendingIntent` của luồng mua còn bay (kill giữa lượt mua), người dùng quay lại qua Recents: Android
 * dựng lại `ProxyBillingActivity` từ task cũ mà không có extra `PendingIntent`, và `onCreate` của nó ném
 * `NullPointerException` (`PendingIntent.getIntentSender()` trên null) trong `performLaunchActivity`, trước mọi mã của
 * app/SDK. Tiền không mất: lượt khôi phục kế (`IapHelper.restorePurchases`) đồng bộ lại từ Play; chỉ còn cú crash.
 *
 * Cách chặn: handler mặc định bọc handler trước đó (Crashlytics…). Chỉ nuốt đúng NPE đó trên main ([shouldSwallow]) rồi
 * chạy lại `Looper.loop()` (hàng đợi chưa bị động tới); mọi ngoại lệ khác chuyển nguyên cho handler trước. Trần
 * [MAX_PER_PROCESS] lượt mỗi process: mỗi lượt chặn thêm một khung `Looper.loop()` vào stack.
 *
 * Cài một lần mỗi process, trên main ở `AppPluginBase.onCreate` (D29: trước mọi `ProxyBillingActivity` dựng lại);
 * `BillingPorts.select` (`IapHelper.init`, luồng "appplugin-billing") gọi lại làm dự phòng — idempotent.
 */
internal object BillingProxyCrashGuard {
    const val PROXY_ACTIVITY = "com.android.billingclient.api.ProxyBillingActivity"
    const val MAX_PER_PROCESS = 5
    private const val MAX_CAUSES = 16
    private const val TAG = "AppPlugin"

    private val installed = AtomicBoolean(false)
    private val swallowed = AtomicInteger(0)

    /**
     * Quyết định thuần: nuốt khi và chỉ khi ngoại lệ tới trên main, mới chặn [count] < [MAX_PER_PROCESS] lượt, và
     * [throwable] (hoặc một cause của nó) là `NullPointerException` có khung trong [PROXY_ACTIVITY].
     */
    fun shouldSwallow(isMain: Boolean, throwable: Throwable, count: Int): Boolean =
        isMain && count < MAX_PER_PROCESS && isProxyNpe(throwable)

    private fun isProxyNpe(throwable: Throwable): Boolean {
        var current: Throwable? = throwable
        var depth = 0
        while (current != null && depth++ < MAX_CAUSES) {
            if (current is NullPointerException && current.stackTrace.any { it.className == PROXY_ACTIVITY }) return true
            current = current.cause.takeIf { it !== current }
        }
        return false
    }

    /** Một lần mỗi process; bọc handler mặc định đang có. */
    fun install() {
        if (!installed.compareAndSet(false, true)) return
        Thread.setDefaultUncaughtExceptionHandler(Handler(Thread.getDefaultUncaughtExceptionHandler()))
    }

    private class Handler(private val previous: Thread.UncaughtExceptionHandler?) : Thread.UncaughtExceptionHandler {
        override fun uncaughtException(thread: Thread, throwable: Throwable) {
            val isMain = Looper.getMainLooper()?.thread === thread
            if (shouldSwallow(isMain, throwable, swallowed.get())) {
                val n = swallowed.incrementAndGet()
                Log.e(TAG, "billing: chặn crash ProxyBillingActivity ($n/$MAX_PER_PROCESS)", throwable)
                Looper.loop() // không trả về: main tiếp tục xử lý hàng đợi thay vì chết
                return
            }
            if (previous != null) {
                previous.uncaughtException(thread, throwable)
            } else {
                Process.killProcess(Process.myPid())
                exitProcess(10)
            }
        }
    }

    /** Test: như process mới (handler đã cài giữ nguyên — test tự khôi phục handler của nó). */
    internal fun resetForTest() {
        installed.set(false)
        swallowed.set(0)
    }
}
