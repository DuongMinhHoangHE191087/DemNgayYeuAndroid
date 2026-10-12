package com.app.plugin.logevent

/**
 * Hàng đợi event gửi lên server của SDK — gate G6 (doc 65): trần [maxPending] (500), tối đa
 * [maxInFlight] (4) request đang bay, backoff 1→60 s, đầy thì bỏ bản ghi CŨ NHẤT.
 *
 * Chỉ giữ trạng thái; gửi và gọi `onOutcome` là việc của [LogEventSender]. Hàm nào bỏ một item thì trả
 * item/kết quả đó về cho người gọi, để `onOutcome` chạy ngoài khoá.
 *
 * ═══ BACKOFF ═══
 *
 * Lỗi thử lại được (mạng, 5xx, 408, 429) đưa item về ĐẦU hàng và đóng cổng của cả hàng tới
 * `now + backoffFor(max(số lần item này lỗi, số lỗi liên tiếp của hàng))`. Hai thành phần vì:
 * - item lỗi nhiều lần thì chờ lâu dần dù giữa chừng có request khác thành công;
 * - mất mạng lâu thì hàng lùi tới 60 s một lần thử, kể cả khi từng item bị bỏ sau [maxAttempts] lần.
 * Cổng chỉ mở sớm khi có request thành công (mạng đã về). Event mới vào hàng KHÔNG mở cổng: nó nằm
 * sau item đang chờ backoff.
 *
 * Đồng hồ mặc định là [monotonicNowMs] (`System.nanoTime`), không phải giờ tường: người chơi chỉnh giờ lùi
 * X thì cổng tính theo giờ tường sẽ đóng thêm X. Đồng hồ tiêm vào mà lùi thì hạn của cổng lùi theo (xem
 * [now]), nên cổng không bao giờ đóng lâu hơn backoff của nó. Timestamp trong JSON event vẫn là giờ tường.
 *
 * ═══ LUỒNG ═══
 *
 * Gọi từ nhiều luồng (coroutine IO của `LogEventServer`, luồng nền của `DecisionLogger`, callback
 * Retrofit trên main). Mọi hàm `@Synchronized` trên chính đối tượng: mỗi thao tác O(1), giữ khoá rất
 * ngắn, và các trường (hàng, số đang bay, cổng backoff) phải đổi cùng nhau nên một khoá đơn giản hơn
 * `ConcurrentLinkedDeque` + `AtomicInteger` rời nhau.
 */
internal class LogEventQueue(
    private val maxPending: Int = 500,
    private val maxInFlight: Int = 4,
    private val baseBackoffMs: Long = 1_000L,
    private val maxBackoffMs: Long = 60_000L,
    /** Số lần THỬ LẠI tối đa của một item; lỗi lần thứ `maxAttempts + 1` thì bỏ. */
    private val maxAttempts: Int = 5,
    private val nowMs: () -> Long = ::monotonicNowMs
) {
    /**
     * Một event chờ gửi. [body] là JSON đã dựng sẵn (sequence id, timestamp cố định qua các lần thử
     * lại). [onOutcome] được gọi đúng một lần với kết quả cuối ([EventOutcome]).
     */
    data class Item(
        val name: String,
        val body: String,
        val onOutcome: ((EventOutcome) -> Unit)? = null,
        /** Số lần đã gửi lỗi. */
        val attempts: Int = 0
    )

    /** Kết quả của [nextOrWait]: một lần đọc đồng hồ, dưới một khoá. */
    sealed interface Next {
        /** Gửi [item] ngay; nó đã được tính là đang bay. */
        data class Send(val item: Item) : Next

        /**
         * Chưa gửi được gì. [ms] > 0: cổng backoff mở sau [ms], người gọi phải hẹn lần bơm kế. `0`: không
         * cần hẹn — hàng rỗng, hoặc đủ chỗ bay (request đang bay trả lời thì tự bơm tiếp).
         */
        data class Wait(val ms: Long) : Next
    }

    /** Kết quả của [onFailure]. */
    sealed interface FailureOutcome {
        /** Item về đầu hàng; hàng chờ [delayMs] trước lần gửi kế. [attempt]: số lần item này đã lỗi. */
        data class Retry(val delayMs: Long, val attempt: Int) : FailureOutcome

        /** Item bị bỏ: hết lượt thử lại, hoặc hàng đã đầy ([queueFull]) nên item — cũ nhất — nhường chỗ. */
        data class Dropped(val attempts: Int, val queueFull: Boolean = false) : FailureOutcome
    }

    private val pending = ArrayDeque<Item>()
    private var inFlightCount = 0
    private var droppedCount = 0L
    private var failureStreak = 0
    /** Hạn mở cổng backoff theo [nowMs]; [GATE_OPEN] khi cổng mở (đồng hồ đơn điệu có thể âm, nên không dùng 0). */
    private var retryNotBeforeMs = GATE_OPEN
    private var lastNowMs = Long.MIN_VALUE

    /** Trần số item chờ (không tính item đang bay). */
    val capacity: Int get() = maxPending

    val pendingCount: Int
        @Synchronized get() = pending.size

    /** Số item còn thêm được mà không đẩy item cũ nhất ra. */
    val headroom: Int
        @Synchronized get() = maxPending - pending.size

    val inFlight: Int
        @Synchronized get() = inFlightCount

    /** Tổng số event bị bỏ: tràn hàng, hết lượt thử, bị server từ chối (4xx). */
    val dropped: Long
        @Synchronized get() = droppedCount

    /** Thêm vào cuối hàng. @return item cũ nhất bị bỏ để nhường chỗ khi hàng đầy, không thì `null`. */
    @Synchronized
    fun offer(item: Item): Item? {
        val evicted = if (pending.size >= maxPending) pending.removeFirst() else null
        if (evicted != null) droppedCount++
        pending.addLast(item)
        return evicted
    }

    /**
     * Item kế tiếp để gửi, hoặc thời gian phải chờ. Gộp "lấy item" và "còn chờ bao lâu" vào một lần đọc đồng
     * hồ dưới một khoá: tách ra thì cổng có thể mở đúng giữa hai lần đọc — lần đầu thấy đóng, lần sau thấy
     * chờ 0 — và không ai hẹn lần bơm kế.
     */
    @Synchronized
    fun nextOrWait(): Next {
        if (pending.isEmpty() || inFlightCount >= maxInFlight) return Next.Wait(0L)
        val t = now()
        if (t < retryNotBeforeMs) return Next.Wait(retryNotBeforeMs - t)
        inFlightCount++
        return Next.Send(pending.removeFirst())
    }

    /** Server đã nhận: nhả chỗ, mở cổng backoff. */
    @Synchronized
    fun onSuccess() {
        releaseSlot()
        failureStreak = 0
        retryNotBeforeMs = GATE_OPEN
    }

    /** Server từ chối hẳn (4xx trừ 408, 429): nhả chỗ, bỏ item, không đụng cổng backoff. */
    @Synchronized
    fun onRejected() {
        releaseSlot()
        droppedCount++
    }

    /** Lỗi thử lại được. Xem "BACKOFF" ở đầu lớp. */
    @Synchronized
    fun onFailure(item: Item): FailureOutcome = onFailure(item, 0L)

    /** Như [onFailure]; cổng đóng ít nhất [minDelayMs] (server báo `Retry-After`, phán quyết R-9 của 1.7.0 T6). */
    @Synchronized
    internal fun onFailure(item: Item, minDelayMs: Long): FailureOutcome {
        releaseSlot()
        failureStreak = minOf(failureStreak + 1, MAX_SHIFT + 1)   // quá mức này backoff đã ở trần shift
        val attempts = item.attempts + 1
        val delayMs = maxOf(backoffFor(maxOf(attempts, failureStreak)), minDelayMs)
        val t = now()   // đọc trước: now() có thể dời hạn cổng (đồng hồ lùi), maxOf phải thấy hạn đã dời
        retryNotBeforeMs = maxOf(retryNotBeforeMs, t + delayMs)
        return when {
            attempts > maxAttempts -> drop(FailureOutcome.Dropped(attempts))
            pending.size >= maxPending -> drop(FailureOutcome.Dropped(attempts, queueFull = true))
            else -> {
                pending.addFirst(item.copy(attempts = attempts))
                FailureOutcome.Retry(delayMs, attempts)
            }
        }
    }

    /** Backoff của lần lỗi thứ [attempt]: 1 s, 2 s, 4 s, … tới trần [maxBackoffMs]. */
    fun backoffFor(attempt: Int): Long =
        minOf(maxBackoffMs, baseBackoffMs shl (attempt - 1).coerceIn(0, MAX_SHIFT))

    /** Đọc đồng hồ (trong khoá). Đồng hồ lùi thì dời hạn cổng lùi đúng bằng khoảng đó. */
    private fun now(): Long {
        val t = nowMs()
        if (t < lastNowMs && retryNotBeforeMs != GATE_OPEN) retryNotBeforeMs -= lastNowMs - t
        lastNowMs = t
        return t
    }

    private fun releaseSlot() {
        if (inFlightCount > 0) inFlightCount--
    }

    private fun drop(outcome: FailureOutcome.Dropped): FailureOutcome.Dropped {
        droppedCount++
        return outcome
    }

    private companion object {
        /** 1 s << 16 ≈ 18 giờ: đủ vượt mọi trần hợp lý mà không tràn `Long`. */
        const val MAX_SHIFT = 16
        const val GATE_OPEN = Long.MIN_VALUE
    }
}

/** Kết quả cuối của một event gửi lên server của SDK. */
internal enum class EventOutcome {
    /** Server trả 2xx. */
    ACCEPTED,

    /** Chính event bị từ chối: server trả 4xx (trừ 408, 429), hoặc không dựng được body. Gửi lại vô ích. */
    REJECTED,

    /** Bị bỏ vì lý do tạm thời: chưa init, kill switch, tràn hàng, hết lượt thử lại. Gửi lại sau có thể được. */
    DROPPED
}

/** Đồng hồ đơn điệu tính bằng ms (không theo giờ hệ thống), mặc định của hàng đợi và bộ gửi event. */
internal fun monotonicNowMs(): Long = System.nanoTime() / 1_000_000
