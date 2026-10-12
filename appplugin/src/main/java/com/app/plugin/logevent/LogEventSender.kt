package com.app.plugin.logevent

import com.app.plugin.until.LogUtil
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.concurrent.atomic.AtomicLong

/**
 * Bơm [LogEventQueue] qua Retrofit (gate G6).
 *
 * - Mỗi item báo `onOutcome` ĐÚNG MỘT LẦN: [EventOutcome.ACCEPTED] khi server trả 2xx,
 *   [EventOutcome.REJECTED] khi server từ chối (4xx), [EventOutcome.DROPPED] khi bị bỏ (tràn hàng, hết lượt
 *   thử lại). Item còn được thử lại thì chưa báo gì.
 * - Mọi nhánh bỏ event đều `logE` (Điều 7); lỗi còn thử lại là `logW`.
 * - 4xx cố định (trừ 408, 429) không thử lại: gửi lại cùng body cũng sẽ bị từ chối. `onOutcome` là [RejectionCodeSink]
 *   thì được báo mã trước `REJECTED` (DurableEvents: chỉ 400/401/413 xoá bản đĩa, phán quyết R-9).
 * - Lỗi thử lại có `Retry-After` (giây; 429 của backend T9): cổng đóng ít nhất chừng đó (trần [MAX_RETRY_AFTER_S]).
 * - Body của response luôn được đóng, cả 2xx lẫn lỗi; body 2xx giao [ServerSignals] trước khi đóng (1.8.0 T10).
 *
 * [transport] tạo `Call` từ JSON; [schedule] chạy việc sau `delayMs` (production: coroutine `delay`).
 * Không hàm nào ở đây ném ra ngoài: event là telemetry, không được làm hỏng app.
 */
internal class LogEventSender(
    private val queue: LogEventQueue,
    private val transport: (json: String) -> Call<ResponseBody>,
    private val schedule: (delayMs: Long, block: () -> Unit) -> Unit,
    /** Chỉ dùng để so hạn các lần hẹn bơm với nhau; đơn điệu như đồng hồ của [LogEventQueue]. */
    private val nowMs: () -> Long = ::monotonicNowMs
) {
    private val evictions = AtomicLong(0)

    private val wakeupLock = Any()

    /** Hạn của lần hẹn gửi lại đang chờ; [NO_WAKEUP] khi không có. */
    private var wakeupAtMs = NO_WAKEUP

    val pendingCount: Int get() = queue.pendingCount

    /** Tổng số event bị bỏ (tràn hàng, 4xx, hết lượt thử). */
    val droppedCount: Long get() = queue.dropped

    /** Số event còn nhận thêm được mà không đẩy event cũ nhất ra. */
    val headroom: Int get() = queue.headroom

    /**
     * Kiểm lúc GỬI (1.7.0 P-1, R-TF3 §2): mỗi lần gửi và mỗi lượt thức dậy gửi lại hỏi lại; `false` → item DROPPED + đếm, 0
     * I/O (kill switch, `logevent_control.enabled=false`, consent DENIED sau khi item đã qua cổng). Mặc định: luôn gửi.
     */
    @Volatile
    internal var allowed: () -> Boolean = { true }
    @Volatile private var blockLogged = false

    /** `onOutcome` muốn biết mã HTTP của lần bị từ chối hẳn; được gọi ngay trước `REJECTED`. */
    internal interface RejectionCodeSink {
        fun onRejectedHttp(code: Int)
    }

    fun submit(item: LogEventQueue.Item) {
        queue.offer(item)?.let(::onEvicted)
        pump()
    }

    /** Gửi mọi item được phép (còn chỗ bay, cổng backoff mở); cổng đóng thì hẹn lần bơm kế. */
    private fun pump() {
        while (true) {
            when (val next = queue.nextOrWait()) {
                is LogEventQueue.Next.Send -> send(next.item)
                is LogEventQueue.Next.Wait -> {
                    if (next.ms > 0) armWakeup(next.ms)
                    return
                }
            }
        }
    }

    private fun send(item: LogEventQueue.Item) {
        if (!allowed()) return drop(item)
        blockLogged = false
        try {
            transport(item.body).enqueue(ResultCallback(item))
        } catch (e: Exception) {
            // Không pump ở đây: vòng lặp của pump() đang chạy và sẽ gặp cổng backoff vừa đóng.
            onRetryable(item, "send threw ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private inner class ResultCallback(private val item: LogEventQueue.Item) : Callback<ResponseBody> {
        override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
            val code = response.code()
            // Hợp đồng C (1.8.0 T10): `signals` của 2xx đọc có giới hạn TRƯỚC khi đóng body; áp trên luồng nền.
            if (response.isSuccessful) ServerSignals.onPushResponse(ServerSignals.read(response.body()))
            closeBodies(response)
            when {
                response.isSuccessful -> {
                    queue.onSuccess()
                    Logs.d("Success log to server: ${item.name} #status: $code")
                    report(item, EventOutcome.ACCEPTED)
                }
                isPermanentRejection(code) -> {
                    queue.onRejected()
                    LogUtil.logE("event dropped: http $code (${item.name})")
                    runCatching { (item.onOutcome as? RejectionCodeSink)?.onRejectedHttp(code) }
                    report(item, EventOutcome.REJECTED)
                }
                else -> onRetryable(item, "http $code", retryAfterMs(response))
            }
            pump()
        }

        override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
            onRetryable(item, "${t.javaClass.simpleName}: ${t.message}")
            pump()
        }
    }

    private fun onRetryable(item: LogEventQueue.Item, cause: String, minDelayMs: Long = 0L) {
        when (val outcome = queue.onFailure(item, minDelayMs)) {
            is LogEventQueue.FailureOutcome.Retry -> LogUtil.logW(
                "event send failed ($cause), attempt ${outcome.attempt}, retry in ${outcome.delayMs} ms: ${item.name}"
            )
            is LogEventQueue.FailureOutcome.Dropped -> {
                val why = if (outcome.queueFull) "queue full while retrying" else "last: $cause"
                LogUtil.logE("event dropped: ${item.name} after ${outcome.attempts} attempts ($why)")
                report(item, EventOutcome.DROPPED)
            }
        }
    }

    /** Bị chặn lúc gửi: nhả chỗ bay, đếm bỏ ([LogEventQueue.onRejected]), DROPPED — bản bền nằm lại trên đĩa. Log một lần mỗi đợt. */
    private fun drop(item: LogEventQueue.Item) {
        queue.onRejected()
        if (!blockLogged) LogUtil.logE("event dropped at send: kill switch / logevent_control / consent DENIED (${item.name}), logged once per stretch")
        blockLogged = true
        report(item, EventOutcome.DROPPED)
    }

    private fun onEvicted(item: LogEventQueue.Item) {
        val n = evictions.incrementAndGet()
        // Mất mạng lâu thì mỗi event mới đẩy một event cũ ra: log lần đầu rồi mỗi 100 lần, không spam.
        if (n == 1L || n % EVICTION_LOG_EVERY == 0L) {
            LogUtil.logE("event queue full (${queue.capacity}): dropped oldest (${item.name}), total $n")
        }
        report(item, EventOutcome.DROPPED)
    }

    /** Hẹn một lần bơm sau [waitMs]; đã có lần hẹn không muộn hơn thì thôi (lần đó bơm rồi tự hẹn tiếp). */
    private fun armWakeup(waitMs: Long) {
        val at = nowMs() + waitMs
        synchronized(wakeupLock) {
            if (wakeupAtMs <= at) return
            wakeupAtMs = at
        }
        try {
            schedule(waitMs) { onWakeup(at) }
        } catch (e: Exception) {
            clearWakeup(at)
            LogUtil.logE("event queue: retry not scheduled (${e.message}); resumes on next event")
        }
    }

    private fun onWakeup(at: Long) {
        clearWakeup(at)
        pump()
    }

    private fun clearWakeup(at: Long) {
        synchronized(wakeupLock) {
            if (wakeupAtMs == at) wakeupAtMs = NO_WAKEUP
        }
    }

    private fun report(item: LogEventQueue.Item, outcome: EventOutcome) {
        try {
            item.onOutcome?.invoke(outcome)
        } catch (e: Exception) {
            LogUtil.logE("event onOutcome failed (${item.name}): ${e.message}")
        }
    }

    private fun closeBodies(response: Response<ResponseBody>) {
        try {
            response.body()?.close()
            response.errorBody()?.close()
        } catch (e: Exception) {
            LogUtil.logW("event response close failed: ${e.message}")
        }
    }

    private companion object {
        const val NO_WAKEUP = Long.MAX_VALUE
        const val EVICTION_LOG_EVERY = 100L

        /** 4xx là lỗi của chính request (sai body, sai khoá): gửi lại vô ích. Trừ 408 (timeout), 429 (quá tải). */
        fun isPermanentRejection(code: Int) = code in 400..499 && code != 408 && code != 429

        /** Trần của `Retry-After`: header hỏng/quá lớn không được khoá telemetry cả ngày. */
        private const val MAX_RETRY_AFTER_S = 600L

        /** `Retry-After` dạng giây (dạng ngày HTTP bỏ qua) → ms; không có = 0. */
        fun retryAfterMs(response: Response<*>): Long =
            response.headers()["Retry-After"]?.trim()?.toLongOrNull()?.takeIf { it > 0 }?.let { minOf(it, MAX_RETRY_AFTER_S) * 1_000L } ?: 0L
    }
}
