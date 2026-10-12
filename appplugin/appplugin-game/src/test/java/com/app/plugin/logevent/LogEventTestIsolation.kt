package com.app.plugin.logevent

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job

/**
 * Cô lập [LogEventTransport] giữa các test (1.7.0 TF3). `resetSenderForTest` thay sender nhưng KHÔNG dừng việc đã hẹn của
 * sender cũ: hẹn bơm lại sau backoff (429/5xx/offline) là một coroutine `delay` trong `requestScope`; tới hạn nó bơm hàng
 * cũ và gửi qua `logService` TĨNH, tức vào service giả của test đang chạy lúc đó (bằng chứng: `DurableEventsTest` để lại 3
 * event `ad_revenue` chờ backoff, ~1 s sau chúng tới `LogEventServerQueueTest`). Test nào có request thất bại/429 phải gọi
 * [cancelScheduledWork] ở `tearDown`, TRƯỚC khi gỡ service giả và reset sender. Chỉ dùng reflection, không đổi mã sản phẩm.
 */
internal object LogEventTestIsolation {
    private val requestJob: Job by lazy {
        val f = LogEventTransport::class.java.getDeclaredField("requestScope").apply { isAccessible = true }
        (f.get(null) as CoroutineScope).coroutineContext[Job]!!
    }

    /** Số việc đang dựng hoặc đang hẹn (backoff) trong `requestScope`. */
    fun scheduledWork(): Int = requestJob.children.count()

    /** Huỷ mọi việc đang dựng hoặc đang hẹn; trả số việc đã huỷ. Việc đã qua `delay` và đang chạy thì không dừng được. */
    fun cancelScheduledWork(): Int = requestJob.children.toList().onEach { it.cancel() }.size
}
