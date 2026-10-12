package com.app.plugin.logevent

import com.app.plugin.iap.SubscriptionServerState
import com.app.plugin.until.LogUtil
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.ResponseBody
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * `signals` trong phản hồi 200 của `/push` (1.8.0 Task 10, plan "Hợp đồng C", D9), dạng cố định:
 * `{"ok":true,"accepted":n,"rejected":m,"signals":[{"v":1,"type":"sub_state","product_id":"…","state":"GRACE|…",
 * "event_ms":<long>}]}`, ≤ 5 phần tử. Mỗi tín hiệu hợp lệ đi qua [SubscriptionServerState.applyServer]: **chỉ đặt nhãn
 * vòng đời, không bao giờ grant/revoke quyền lợi**.
 *
 * - [LogEventSender] gọi [read] rồi [onPushResponse] trên nhánh 2xx, TRƯỚC khi đóng body: [read] đọc tối đa
 *   [MAX_BYTES] + 1 byte (Retrofit đã đệm body, không I/O mạng). Quá [MAX_BYTES] → bỏ cả khối, log một dòng.
 * - Retrofit 2.9 trên Android gọi callback trên main: parse + áp chạy trên [executor] (một luồng nền), không trên main.
 * - Bỏ (không lỗi): JSON hỏng, `signals` không phải mảng hoặc > [MAX_SIGNALS] phần tử (sai dạng: bỏ cả khối), `v` ≠ 1,
 *   `type` ≠ `sub_state`, `state` lạ, `product_id` rỗng, `event_ms` không phải số nguyên dương hoặc ≤ lần áp cuối.
 * - Không hàm nào ném ra ngoài: telemetry không được làm hỏng app.
 */
internal object ServerSignals {
    const val MAX_BYTES = 4_096
    const val MAX_SIGNALS = 5
    private const val TYPE_SUB_STATE = "sub_state"

    private val worker: Executor by lazy {
        Executors.newSingleThreadExecutor { r -> Thread(r, "appplugin-signals").apply { isDaemon = true } }
    }

    /** Nơi parse + áp; test thay bằng bản chạy đồng bộ. */
    @Volatile
    internal var executor: Executor = Executor { r -> worker.execute(r) }

    /** Tối đa [MAX_BYTES] + 1 byte đầu của [body] (không đóng body); `null` khi không có body hoặc đọc lỗi. */
    fun read(body: ResponseBody?): ByteArray? {
        if (body == null) return null
        return try {
            val out = ByteArray(MAX_BYTES + 1)
            val input = body.byteStream()
            var n = 0
            while (n < out.size) {
                val r = input.read(out, n, out.size - n)
                if (r < 0) break
                n += r
            }
            out.copyOf(n)
        } catch (e: Exception) {
            LogUtil.logW("signals: không đọc được body /push (${e.javaClass.simpleName})")
            null
        }
    }

    /** Body 2xx của `/push` ([read]); quá [MAX_BYTES] → bỏ cả khối. Parse + áp trên [executor]. */
    fun onPushResponse(bodyBytes: ByteArray?) {
        if (bodyBytes == null || bodyBytes.isEmpty()) return
        if (bodyBytes.size > MAX_BYTES) {
            LogUtil.logE("signals bỏ: phản hồi /push > $MAX_BYTES byte")
            return
        }
        try {
            executor.execute { apply(bodyBytes) }
        } catch (e: Exception) {
            LogUtil.logW("signals: không hẹn được việc áp (${e.message})")
        }
    }

    /** Parse và áp; trả số tín hiệu đã áp (test đọc). */
    internal fun apply(bodyBytes: ByteArray): Int = try {
        val signals = JsonParser.parseString(String(bodyBytes, Charsets.UTF_8)).asJsonObject.get("signals")
        when {
            signals == null || !signals.isJsonArray -> 0
            signals.asJsonArray.size() > MAX_SIGNALS -> {
                LogUtil.logE("signals bỏ: ${signals.asJsonArray.size()} phần tử > $MAX_SIGNALS")
                0
            }
            else -> signals.asJsonArray.count { el -> applyOne(el) }
        }
    } catch (e: Exception) {
        LogUtil.logW("signals bỏ: phản hồi /push không đúng dạng (${e.javaClass.simpleName})")
        0
    }

    private fun applyOne(el: JsonElement): Boolean {
        val o = el as? JsonObject ?: return false
        if (o.get("v")?.toString() != "1" || text(o, "type") != TYPE_SUB_STATE) return false
        val productId = text(o, "product_id") ?: return false
        val state = text(o, "state") ?: return false
        val eventMs = o.get("event_ms")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.toString()?.toLongOrNull() ?: return false
        return SubscriptionServerState.applyServer(productId, state, eventMs)
    }

    private fun text(o: JsonObject, key: String): String? =
        o.get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
}
