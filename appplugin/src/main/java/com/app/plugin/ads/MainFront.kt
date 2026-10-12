package com.app.plugin.ads

import android.os.Handler
import android.os.Looper
import android.os.Message

/**
 * 2.4.4 INITCB (Hợp đồng C, quyết định (c)): chạy việc trên main **không** xếp cuối hàng. Đang ở main ⇒ chạy ngay; khác ⇒
 * `Message` async gửi `sendMessageAtFrontOfQueue` trên `Handler(mainLooper)` — vượt hàng đợi và sync barrier của khung
 * hình, vẫn trên main (mẫu FASTSTART-A `LicenseGate.frontPoster`). Dùng chung hai module; `MainPost` (FIFO) không đổi.
 */
internal object MainFront {
    private val mainCheck: () -> Boolean = { Looper.myLooper() === Looper.getMainLooper() }
    private val frontPoster: (Runnable) -> Unit = { r ->
        val h = Handler(Looper.getMainLooper())
        h.sendMessageAtFrontOfQueue(Message.obtain(h, r).apply { isAsynchronous = true })
    }

    /** Seam test: mặc định so [Looper.myLooper] với main looper. */
    @Volatile internal var isMain: () -> Boolean = mainCheck

    /** Seam test: mặc định message async đầu hàng main. */
    @Volatile internal var poster: (Runnable) -> Unit = frontPoster

    internal fun resetForTest(): Unit {
        isMain = mainCheck
        poster = frontPoster
    }

    internal fun run(block: () -> Unit): Unit {
        if (isMain()) block() else poster(Runnable { block() })
    }
}
