package com.app.plugin.iap

import com.app.plugin.until.PreferenceHelper

/**
 * Trạng thái gốc của subscription theo server (1.8.0 Task 10, plan "Hợp đồng C", D9): mỗi sản phẩm giữ trạng thái RTDN
 * mới nhất đã áp và `event_ms` của nó. Nhãn công khai đi qua [SubscriptionLifecycle.applyServerLabel]; trạng thái gốc
 * (ON_HOLD, PAUSED, REVOKED, EXPIRED đều thành `LAPSED` công khai) chỉ đọc nội bộ qua [stateFor] (T11, D2).
 *
 * - **Chỉ đặt nhãn** (D9): không bao giờ gọi `Entitlements`; quyền lợi vẫn chỉ đổi theo Play trên máy.
 * - Một tín hiệu có `event_ms` ≤ lần áp cuối của cùng sản phẩm bị bỏ: server trả lại cùng tín hiệu ở mỗi `/push` trong
 *   7 ngày. Mốc lưu qua [PreferenceHelper] (`apply()`, như [SubscriptionLifecycle]) để process sau không áp lại.
 * - Không giữ monitor khi gọi sang [SubscriptionLifecycle] (#31): lời gọi tới từ một luồng nền duy nhất (`ServerSignals`).
 * - D27 (R-10): cũng giữ trạng thái client quan sát cuối (Play trên máy) và lúc nó đổi; nhãn server còn trong cửa sổ
 *   [WINDOW_MS] và mới hơn lần đổi đó thắng một lượt restore PURCHASED của client ([serverLabelWins]).
 * - Khi `subscription_lifecycle.enabled=false`, trạng thái gốc và mốc vẫn được ghi (chỉ nhãn bị bỏ): bật lại không áp lại
 *   cùng tín hiệu.
 */
internal object SubscriptionServerState {
    private const val KEY = "sub_server_states"
    private const val KEY_CLIENT = "sub_server_client"

    /** Cửa sổ tín hiệu của hợp đồng C: 7 ngày. */
    const val WINDOW_MS = 7L * 24 * 3600 * 1000

    /** Đồng hồ (ms epoch) cho cửa sổ và lúc client đổi trạng thái; test thay. */
    @Volatile internal var clock: () -> Long = System::currentTimeMillis

    /** Mười trạng thái của hợp đồng C → nhãn công khai. Trạng thái ngoài bảng là "lạ": bỏ. */
    private val LABELS: Map<String, SubState> = mapOf(
        "GRACE" to SubState.IN_GRACE_PERIOD,
        "ON_HOLD" to SubState.LAPSED,
        "PAUSED" to SubState.LAPSED,
        "REVOKED" to SubState.LAPSED,
        "EXPIRED" to SubState.LAPSED,
        "CANCELED" to SubState.CANCELLED,
        "RECOVERED" to SubState.ACTIVE,
        "RENEWED" to SubState.ACTIVE,
        "RESTARTED" to SubState.ACTIVE,
        "PURCHASED" to SubState.ACTIVE
    )

    private data class Entry(val state: String, val eventMs: Long)

    @Volatile private var entries: Map<String, Entry> = load(KEY) { it in LABELS }

    /** D27/D28: trạng thái client quan sát cuối (tên [SubState]) và lúc nó đổi, theo sản phẩm. */
    @Volatile private var client: Map<String, Entry> = load(KEY_CLIENT) { runCatching { SubState.valueOf(it) }.isSuccess }

    /** Nhãn công khai của trạng thái server [state]; `null` = trạng thái lạ. */
    fun labelFor(state: String): SubState? = LABELS[state]

    /** Trạng thái gốc mới nhất đã áp cho [productId] (vd. `ON_HOLD`); `null` khi server chưa báo gì. */
    fun stateFor(productId: String): String? = entries[productId]?.state

    /** `event_ms` của tín hiệu áp cuối cho [productId]; `0` khi chưa có. */
    fun lastAppliedMs(productId: String): Long = entries[productId]?.eventMs ?: 0L

    /** Nhãn server áp cuối và `event_ms` của nó khi `nowMs - event_ms` ≤ [WINDOW_MS]; ngoài cửa sổ → `null` (D27 b). */
    fun freshLabel(productId: String, nowMs: Long = clock()): Pair<SubState, Long>? {
        val e = entries[productId] ?: return null
        val label = LABELS[e.state] ?: return null
        return if (nowMs - e.eventMs <= WINDOW_MS) label to e.eventMs else null
    }

    /** Ghi một quan sát client (Play trên máy); mốc thời gian chỉ đổi khi trạng thái client đổi. */
    @Synchronized
    fun onClientObserved(productId: String, state: SubState) {
        if (client[productId]?.state == state.name) return
        client = client + (productId to Entry(state.name, clock()))
        PreferenceHelper.getInstance().setValue(KEY_CLIENT, encode(client))
    }

    /** Trạng thái client quan sát cuối; `NONE` khi chưa có (D28: nguồn sở hữu của `RecurringGrants`). */
    fun clientStateFor(productId: String): SubState = client[productId]?.let { SubState.valueOf(it.state) } ?: SubState.NONE

    /** D27: nhãn server còn tươi, mới hơn lần đổi cuối của client và khác [clientLabel] → giữ nhãn server. */
    fun serverLabelWins(productId: String, clientLabel: SubState): Boolean {
        val (label, eventMs) = freshLabel(productId) ?: return false
        return eventMs > (client[productId]?.eventMs ?: 0L) && label != clientLabel
    }

    /**
     * Áp một tín hiệu đã qua kiểm dạng: trạng thái lạ, [productId] rỗng, hoặc [eventMs] ≤ lần áp cuối → `false`, không
     * đổi gì. Ngược lại ghi trạng thái gốc rồi đặt nhãn (event `subscription_state` `source=server` khi nhãn đổi).
     */
    fun applyServer(productId: String, state: String, eventMs: Long): Boolean {
        val label = LABELS[state] ?: return false
        if (productId.isBlank() || eventMs <= 0L) return false
        synchronized(this) {
            if (eventMs <= lastAppliedMs(productId)) return false
            entries = entries + (productId to Entry(state, eventMs))
            PreferenceHelper.getInstance().setValue(KEY, encode(entries))
        }
        SubscriptionLifecycle.applyServerLabel(productId, label)
        return true
    }

    /** Test: xoá trạng thái trong bộ nhớ và bản lưu. */
    @Synchronized
    fun clear() {
        entries = emptyMap()
        client = emptyMap()
        PreferenceHelper.getInstance().remove(KEY)
        PreferenceHelper.getInstance().remove(KEY_CLIENT)
    }

    private const val SEP = "|"
    private const val FS = ":"

    private fun encode(m: Map<String, Entry>): String = m.entries.joinToString(SEP) { "${it.key}$FS${it.value.state}$FS${it.value.eventMs}" }

    private fun load(key: String, validState: (String) -> Boolean): Map<String, Entry> =
        PreferenceHelper.getInstance().get(key, "").split(SEP).mapNotNull { part ->
            val f = part.split(FS)
            val ms = f.getOrNull(2)?.toLongOrNull()
            if (f.size == 3 && f[0].isNotBlank() && validState(f[1]) && ms != null) f[0] to Entry(f[1], ms) else null
        }.toMap()
}
