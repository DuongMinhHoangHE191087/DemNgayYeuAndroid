package com.app.plugin.logevent

import android.os.SystemClock
import android.util.Log
import com.google.gson.JsonObject

/**
 * Bộ đếm một lần foreground cho event `session_end` (1.7.0 Task 5). Trên luồng main chỉ đếm; event dựng và gửi trên IO
 * như mọi event (`AdEventLog.sessionEnd` → `LogEventTransport.sendEvent`), đi qua cổng consent thường: sau `DENIED`
 * không byte nào ra. `onBackground` gửi `session_end` rồi reset bộ đếm; `fg_index` đếm lần foreground trong process
 * và không reset. Sau mỗi `session_end` in một dòng `logevent stats` (số mỗi process, dùng đối soát H6a). Dòng đó là ảnh
 * chụp lúc ON_STOP, TRƯỚC khi `session_end` này và event của brain phía sau được dựng/nhận: `built`/`accepted` chưa gồm
 * chúng, nên người đọc phải lấy dòng muộn hơn (T13 `_stat` lấy max các dòng pha A và B). `duration_ms` đo bằng đồng hồ
 * đơn điệu (`SystemClock.elapsedRealtime`) để đổi giờ hệ thống giữa phiên không làm hỏng nó.
 */
internal object SessionTracker {
    private const val TAG = "AppPlugin"
    private val lock = Any()
    private var fgIndex = 0
    private var started = false
    private var startedAtMs = 0L
    private var screens = 0
    private var adsShown = LinkedHashMap<String, Int>()

    /** Đồng hồ; test thay. */
    @Volatile
    internal var nowMs: () -> Long = { SystemClock.elapsedRealtime() }

    fun onForeground() {
        synchronized(lock) {
            fgIndex++
            started = true
            startedAtMs = nowMs()
        }
        com.app.plugin.segment.SegmentHooks.onSessionStart() // 2.2.0 SEG: SESSION_START mỗi foreground như fork
    }

    fun onActivityStarted() = synchronized(lock) { screens++ }

    fun onAdShown(format: String) = synchronized(lock) { adsShown[format] = (adsShown[format] ?: 0) + 1 }

    fun onBackground() {
        val snap = synchronized(lock) {
            if (!started) return
            val s = Snapshot(nowMs() - startedAtMs, adsJson(adsShown), screens, fgIndex)
            started = false
            screens = 0
            adsShown = LinkedHashMap()
            s
        }
        AdEventLog.sessionEnd(snap.durationMs, snap.adsShownJson, snap.screens, snap.fgIndex)
        logStats()
    }

    private class Snapshot(val durationMs: Long, val adsShownJson: String, val screens: Int, val fgIndex: Int)

    private fun adsJson(counts: Map<String, Int>): String =
        JsonObject().also { o -> counts.forEach { (k, v) -> o.addProperty(k, v) } }.toString()

    private fun logStats() {
        Log.i(
            TAG,
            "logevent stats session=${LogEventServerHelper.currentSessionId()} built=${LogEventTransport.builtCount.get()} " +
                "accepted=${LogEventTransport.acceptedCount.get()} rejected=${LogEventTransport.rejectedCount.get()} " +
                "dropped=${LogEventTransport.droppedCount.get()} sampled=${LogEventControl.sampledOutCount.get()} durable_stored=${DurableEvents.storedCount.get()} " +
                "durable_replayed=${DurableEvents.replayedCount.get()}"
        )
    }

    internal fun resetForTest() = synchronized(lock) {
        fgIndex = 0
        started = false
        startedAtMs = 0L
        screens = 0
        adsShown = LinkedHashMap()
        nowMs = { SystemClock.elapsedRealtime() }
    }
}
