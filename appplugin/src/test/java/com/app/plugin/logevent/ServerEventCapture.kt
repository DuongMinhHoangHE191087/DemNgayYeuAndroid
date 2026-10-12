package com.app.plugin.logevent

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.RequestBody
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Callback
import retrofit2.Response
import okio.Buffer
import retrofit2.Call
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Bắt các event `LogEventServer` gửi tới server, ở đúng chỗ Retrofit nhận body: `logService` thay bằng bản
 * giữ request (reflection, như `LogEventServerQueueTest`), hàng đợi reset trước và sau. Event được dựng
 * trên `Dispatchers.IO`, nên [await] chờ có hạn rồi chờ thêm một chút để bắt cả event thừa.
 *
 * Dùng với `use { }`: [close] trả `logService` về chưa init, như các test khác mong đợi.
 */
internal class ServerEventCapture : AutoCloseable {
    private val logServiceField = LogEventTransport::class.java.getDeclaredField("logService").apply { isAccessible = true }
    private val bodies = CopyOnWriteArrayList<String>()

    /** Tên luồng của mỗi lần gọi `logActionsToServer`, theo thứ tự gọi. */
    val threads = CopyOnWriteArrayList<String>()

    /** Chạy trên luồng gửi trước khi body được ghi nhận (test chặn một lần gửi để giữ luồng đó lại). */
    @Volatile
    var beforeSend: ((String) -> Unit)? = null

    /**
     * `true`: mỗi lần gửi được trả lời 200 ngay khi enqueue, để hàng đợi (một request bay tại một thời điểm) không kẹt sau
     * request đầu và test bắn nhiều event bắt được hết (1.8.0 Task 15 R3). Mặc định `false`: giữ như trước.
     */
    @Volatile
    var autoAck: Boolean = false

    init {
        LogEventServer.resetSenderForTest()
        logServiceField.set(null, object : LogService {
            override fun logActionsToServer(requestBody: RequestBody): Call<ResponseBody> {
                val json = Buffer().also { requestBody.writeTo(it) }.readUtf8()
                threads += Thread.currentThread().name
                beforeSend?.invoke(json)
                bodies += json
                return if (autoAck) AckCall(json) else HeldCall(json)
            }
        })
    }

    /** `eventData` của các event [eventName] đã tới server: chờ tới khi có [count] cái (tối đa 5 s), rồi [settleMs]. */
    fun await(eventName: String, count: Int, settleMs: Long = 200L): List<JsonObject> =
        awaitRequests(eventName, count, settleMs).map { it.getAsJsonObject("eventData") }

    /** Như [await] nhưng trả cả body (`eventData`, `userProperty`, `sequenceId`, `ts`). */
    fun awaitRequests(eventName: String, count: Int, settleMs: Long = 200L): List<JsonObject> {
        val deadline = System.currentTimeMillis() + 5_000L
        while (requestsNamed(eventName).size < count && System.currentTimeMillis() < deadline) Thread.sleep(5)
        Thread.sleep(settleMs)
        return requestsNamed(eventName)
    }

    /**
     * Các event `ad_show_fail` (v2, 1.7.0) đã tới server, dạng `placement to reason`. Là danh sách (đếm được
     * trùng), sắp theo placement: event dựng song song trên IO nên thứ tự tới không cố định.
     */
    fun showFails(count: Int): List<Pair<String, String>> =
        await("ad_show_fail", count)
            .map { it.get("placement").asString to it.get("reason").asString }
            .sortedBy { it.first }

    /** Mọi request đã tới server theo thứ tự gọi `logActionsToServer` (không lọc tên, không chờ). */
    fun allRequests(): List<JsonObject> = bodies.map { JsonParser.parseString(it).asJsonObject }

    private fun requestsNamed(eventName: String): List<JsonObject> = bodies
        .map { JsonParser.parseString(it).asJsonObject }
        .filter { it.get("eventName").asString == eventName }

    override fun close() {
        LogEventTestIsolation.cancelScheduledWork()
        logServiceField.set(null, null)
        LogEventServer.resetSenderForTest()
    }
}

/** [HeldCall] trả lời 200 ngay lúc enqueue ([ServerEventCapture.autoAck]). */
private class AckCall(body: String) : Call<ResponseBody> by HeldCall(body) {
    override fun enqueue(callback: Callback<ResponseBody>) = callback.onResponse(this, Response.success("ok".toResponseBody()))
}
