package com.app.plugin.iap

import android.os.Handler
import android.os.Looper
import com.android.billingclient.api.BillingClient
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Seam đồng hồ của luồng billing (1.8.0 Task 5): lượt thử lại query giá chạy theo đồng hồ này, test thay bằng đồng hồ
 * giả (không `Thread.sleep`). Bản thật hẹn việc trên chính executor "appplugin-billing" ([BillingThread]).
 */
internal interface BillingTimer {
    fun nowMs(): Long

    /** Chạy [r] sau [delayMs] trên luồng billing. */
    fun schedule(delayMs: Long, r: Runnable)

    companion object {
        /** Mã store đáng thử lại (tạm thời). `BILLING_UNAVAILABLE` (3) không nằm đây: dừng hẳn trong process (F7). */
        val RETRYABLE: Set<Int> = setOf(
            BillingClient.BillingResponseCode.SERVICE_DISCONNECTED,
            BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
            BillingClient.BillingResponseCode.ERROR,
            BillingClient.BillingResponseCode.NETWORK_ERROR
        )

        private const val MAX_RETRIES = 5

        /** Lượt thử lại thứ [attempt] (từ 1): 1-2-4-8-16 s, rồi `null` (dừng). */
        fun retryDelayMs(attempt: Int): Long? = if (attempt in 1..MAX_RETRIES) 1_000L shl (attempt - 1) else null
    }
}

/** Đồng hồ thật: đơn điệu, hẹn việc trên [scheduler] (luồng billing). */
internal class ScheduledBillingTimer(private val scheduler: ScheduledExecutorService) : BillingTimer {
    override fun nowMs(): Long = System.nanoTime() / 1_000_000

    override fun schedule(delayMs: Long, r: Runnable) {
        scheduler.schedule(r, delayMs, TimeUnit.MILLISECONDS)
    }
}

/**
 * Luồng của `IapHelper` (D11, F7): một luồng nền đơn "appplugin-billing" (daemon) cho mọi lệnh store và mọi xử lý kết
 * quả; callback cho app về main như trước 1.8.0 (thư viện Play gọi listener trên main).
 */
internal object BillingThread {
    val executor: ScheduledExecutorService =
        java.util.concurrent.Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "appplugin-billing").apply { isDaemon = true } }

    /** Test thay bằng đồng hồ giả. */
    @Volatile var timer: BillingTimer = ScheduledBillingTimer(executor)

    /** Đưa callback của app về main. Không có main looper (test JVM) thì chạy ngay trên luồng gọi. */
    fun postMain(r: Runnable) {
        val looper = Looper.getMainLooper()
        if (looper == null) r.run() else Handler(looper).post(r)
    }

    /** T6 `Entitlements`: ký/kiểm Keystore + ghi đĩa luôn xếp lên luồng billing (ngoài monitor); không main looper (JVM) thì chạy ngay. */
    @Volatile var offMain: java.util.concurrent.Executor =
        java.util.concurrent.Executor { r -> if (Looper.getMainLooper() == null) r.run() else executor.execute(r) }
}
