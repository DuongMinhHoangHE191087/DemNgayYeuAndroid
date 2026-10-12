package com.app.plugin.iap

import com.app.plugin.logevent.IapEventLog
import com.app.plugin.until.PreferenceHelper
import java.net.URLEncoder

/**
 * Vòng đời subscription phía client — dunning, win-back và phân loại trạng thái
 * (doc 23 mục C.3, doc 19 mục B.7/B.8).
 *
 * ═══ VÌ SAO LỚP NÀY TỒN TẠI ═══
 *
 * Play Billing cho biết HAI sự thật về một subscription (purchaseState và isAutoRenewing),
 * nhưng không trả lời câu hỏi nào trong số những câu hỏi vận hành quan trọng:
 *
 * - Người này đang trả tiền bình thường, hay thanh toán vừa thất bại và Play đang retry?
 * - Họ đã huỷ (nhưng còn quyền lợi tới hết kỳ), hay đã churn hẳn?
 * - Có nên nhắc họ sửa phương thức thanh toán LẦN NỮA không, hay nhắc thế là spam?
 * - Ai đủ điều kiện nhận offer win-back?
 *
 * Lớp này ghép hai sự thật của store với MỘT sự thật cục bộ (lịch sử "đã từng có sub"
 * trên máy này) để trả lời bốn câu hỏi đó. Google Play **không có win-back offer native**
 * (doc 19 mục B.8 — Apple có từ WWDC 2024, Google thì developer phải tự làm) — phát hiện
 * người đã churn chính là mảnh Google để trống.
 *
 * ═══ NĂM TRẠNG THÁI ═══
 *
 * ```text
 * NONE            chưa từng có sub nào trên máy này
 * ACTIVE          PURCHASED + autoRenewing — trả tiền bình thường
 * CANCELLED       PURCHASED + KHÔNG autoRenewing — đã huỷ, còn quyền lợi tới hết kỳ
 * IN_GRACE_PERIOD thanh toán thất bại, Play đang retry (tới 30 ngày), VẪN còn quyền lợi — chỉ từ tín hiệu server
 * PENDING (D4, 1.8.0): giao dịch chờ trả tiền — KHÔNG đổi trạng thái, không phải grace (client không thấy grace)
 * LAPSED          từng có sub (lịch sử) nhưng store không còn trả về — mất quyền lợi
 * ```
 *
 * ═══ RANH GIỚI ═══
 *
 * SDK không vẽ thông báo dunning, không mở Play Store, không tự phát offer win-back.
 * Lớp này TRẢ DỮ LIỆU (trạng thái, "đã đến lượt nhắc chưa", URI quản lý sub, cờ win-back);
 * app quyết định làm gì với chúng — cùng ranh giới "SDK không vẽ hộ" của paywall và router.
 *
 * ═══ GIỚI HẠN PHẢI NÓI RÕ ═══
 *
 * - Lịch sử "từng có sub" sống trên máy: xoá app là mất, và người churn quay lại cài đặt
 *   sẽ bị phân loại là NONE thay vì LAPSED. Nguồn sự thật đúng là phía server (theo
 *   user_id) — chưa có backend.
 * - Prepaid plan không phân biệt được với sub đã huỷ (cả hai đều PURCHASED + không
 *   autoRenewing).
 * - Lượt observe đầu tiên sau cài đặt phát chuyển đổi NONE→ACTIVE dù sub có thể đã tồn tại
 *   nhiều năm — sự thật cấp máy, không phải cấp tài khoản.
 * - Lịch retry thanh toán là việc của Play trên server; lớp này chỉ phân loại kết quả, không
 *   can thiệp và không nhìn thấy lịch đó.
 */
public object SubscriptionLifecycle {

    private const val KEY_STATES = "sub_lifecycle_states"
    private const val KEY_HISTORY = "sub_lifecycle_history"
    private const val KEY_DUNNING = "sub_lifecycle_dunning"
    private const val SEP = "|"

    /** Lịch nhắc dunning mặc định: lượt mở app thứ 1, 2 và 4 trong grace period. */
    private val DEFAULT_CADENCE = listOf(1, 2, 4)

    /** Một snapshot subscription do store trả về — chỉ hai sự thật Play Billing cho biết. */
    public data class SubSnapshot(
        val productId: String,
        val state: IapPurchaseState,
        val isAutoRenewing: Boolean
    )

    /** Một chuyển đổi trạng thái vừa xảy ra (đã phát event nếu bật). */
    public data class Transition(val productId: String, val from: SubState, val to: SubState)

    public data class ApplyResult(val applied: Boolean, val report: String)

    // ═══ Trạng thái cấu hình ═══

    @Volatile private var version: String = "chưa nạp"
    @Volatile private var enabled: Boolean = true
    @Volatile private var dunningCadence: List<Int> = DEFAULT_CADENCE
    @Volatile private var winBackEnabled: Boolean = true
    @Volatile private var inAppMessages: Boolean = true

    // ═══ Trạng thái vòng đời (đệm trong process + persist qua PreferenceHelper) ═══

    @Volatile private var states: MutableMap<String, SubState> = loadStates()
    @Volatile private var history: MutableSet<String> = loadSet(KEY_HISTORY)
    @Volatile private var dunningCounts: MutableMap<String, Int> = loadCounts()

    // ═══ Cấu hình từ xa ═══

    /**
     * Nạp cấu hình khoá `subscription_lifecycle_android`. Cùng nguyên tắc với paywall và
     * router: JSON hỏng thì GIỮ NGUYÊN bản đang chạy, không rơi về mặc định.
     *
     * ```json
     * {
     *   "version": "sub_lifecycle_v1",
     *   "enabled": true,
     *   "dunning_cadence": [1, 2, 4],
     *   "win_back_enabled": true,
     *   "in_app_messages": {"enabled": true}
     * }
     * ```
     *
     * `enabled=false` là công tắc tắt khẩn cấp: ngừng phân loại, ngừng event, ngừng nhắc —
     * app cư xử như thể tính năng không tồn tại. `dunning_cadence` rỗng = cố ý không nhắc.
     */
    public fun apply(json: String?): ApplyResult {
        if (json.isNullOrBlank()) return reject("cấu hình rỗng")
        return try {
            val root = com.google.gson.Gson().fromJson(json, Map::class.java) ?: return reject("JSON null")
            val ver = root["version"] as? String
            if (ver.isNullOrBlank()) return reject("thiếu trường version")

            val notes = mutableListOf<String>()
            var newEnabled = true
            var newCadence = DEFAULT_CADENCE
            var newWinBack = true

            (root["enabled"] as? Boolean)?.let { newEnabled = it }

            val rawCadence = root["dunning_cadence"]
            if (rawCadence != null) {
                if (rawCadence !is List<*>) return reject("dunning_cadence không phải danh sách")
                val parsed = mutableListOf<Int>()
                for (item in rawCadence) {
                    val n = (item as? Number)?.toInt()
                        ?: return reject("dunning_cadence chứa phần tử không phải số: $item")
                    if (n >= 1) parsed.add(n) else notes.add("bỏ qua lượt nhắc <$n")
                }
                newCadence = parsed.sorted().distinct()
                if (newCadence.isEmpty()) notes.add("cadence rỗng — sẽ không nhắc dunning")
            }

            (root["win_back_enabled"] as? Boolean)?.let { newWinBack = it }
            val newInApp = SubscriptionMessaging.enabledIn(root["in_app_messages"]) // R-11 m3: sai dạng → tắt

            version = ver
            enabled = newEnabled
            dunningCadence = newCadence
            winBackEnabled = newWinBack
            inAppMessages = newInApp
            val noteStr = if (notes.isEmpty()) "" else "; " + notes.joinToString("; ")
            ApplyResult(
                true,
                "áp $ver: enabled=$newEnabled, cadence=$newCadence, win_back=$newWinBack, in_app_messages=$newInApp$noteStr"
            )
        } catch (e: Exception) {
            reject("JSON hỏng: ${e.message}")
        }
    }

    private fun reject(reason: String): ApplyResult {
        // Giữ nguyên bản đang chạy — nguyên tắc an toàn chung của mọi khối cấu hình từ xa.
        return ApplyResult(false, "từ chối ($reason) — giữ nguyên bản $version")
    }

    public fun activeVersion(): String = version

    // ═══ Quan sát — đầu vào duy nhất của máy trạng thái ═══

    /**
     * Nạp danh sách subscription store vừa trả về và cập nhật trạng thái.
     *
     * `complete = true` (mặc định, dùng cho kết quả `queryPurchasesAsync` đầy đủ): sản phẩm
     * TỪNG có trong lịch sử mà KHÔNG còn trong danh sách được coi là LAPSED — mất quyền lợi.
     * `complete = false` (cập nhật nhanh một sản phẩm ngay sau lượt mua): chỉ cập nhật các
     * sản phẩm có mặt, KHÔNG suy ra LAPSED cho sản phẩm vắng mặt — nếu không một lượt mua
     * duy nhất sẽ biến mọi sub khác thành churn.
     *
     * Trả về danh sách chuyển đổi vừa xảy ra (cũng là danh sách event đã phát).
     */
    @Synchronized
    public fun observe(snapshots: List<SubSnapshot>, complete: Boolean = true): List<Transition> {
        if (!enabled) return emptyList()
        val transitions = mutableListOf<Transition>()

        for (s in snapshots) {
            if (s.productId.isBlank()) continue
            val newState = when {
                s.state == IapPurchaseState.PENDING -> null // D4: chưa trả tiền ≠ grace; grace chỉ từ server (T10)
                s.state == IapPurchaseState.PURCHASED && s.isAutoRenewing -> SubState.ACTIVE
                s.state == IapPurchaseState.PURCHASED -> SubState.CANCELLED
                else -> null // UNSPECIFIED — không đủ sự thật để phân loại
            } ?: continue
            // D27: nhãn server ≤ 7 ngày, mới hơn lần đổi cuối của client, khác nhãn này → giữ (Play báo grace như PURCHASED).
            if (SubscriptionServerState.serverLabelWins(s.productId, newState)) continue
            applyState(s.productId, newState, transitions)
        }

        if (complete) {
            // Từng có sub mà store không còn trả về = mất quyền lợi (hết grace → account hold,
            // hết hạn, hoặc refund). Đây là tín hiệu win-back.
            for (pid in history.toList()) {
                if (snapshots.none { it.productId == pid }) {
                    applyState(pid, SubState.LAPSED, transitions)
                }
            }
        }
        return transitions
    }

    /**
     * Nhãn từ tín hiệu server (1.8.0 T10, D9; gọi từ [SubscriptionServerState]): cùng đường [applyState], event
     * `source=server`. Chỉ đặt nhãn — không bao giờ grant/revoke quyền lợi. Tắt (`enabled=false`) thì bỏ như [observe].
     */
    @Synchronized
    internal fun applyServerLabel(productId: String, label: SubState): Transition? {
        if (!enabled || productId.isBlank()) return null
        return mutableListOf<Transition>().also { applyState(productId, label, it, IapEventLog.SOURCE_SERVER) }.firstOrNull()
    }

    private fun applyState(productId: String, newState: SubState, transitions: MutableList<Transition>) =
        applyState(productId, newState, transitions, IapEventLog.SOURCE_CLIENT)

    private fun applyState(productId: String, newState: SubState, transitions: MutableList<Transition>, source: String) {
        val oldState = states[productId] ?: SubState.NONE
        if (source == IapEventLog.SOURCE_CLIENT) SubscriptionServerState.onClientObserved(productId, newState) // D27/D28
        // Từng được quyền lợi (kể cả đang trong grace period) thì vào lịch sử — lịch sử này
        // là nền của phát hiện churn, và một khi đã vào thì không bao giờ ra.
        if (newState != SubState.LAPSED) {
            if (history.add(productId)) persist(KEY_HISTORY, history.joinToString(SEP))
        }
        if (newState != SubState.IN_GRACE_PERIOD && oldState == SubState.IN_GRACE_PERIOD) {
            // Episode grace kết thúc (hồi phục, hold, hay churn) — lịch nhắc bắt đầu lại từ đầu.
            dunningCounts.remove(productId)
            persistCounts()
        }
        if (oldState == newState) return
        states[productId] = newState
        persist(KEY_STATES, states.entries.joinToString(SEP) { "${it.key}:${it.value.name}" })
        transitions.add(Transition(productId, oldState, newState))
        // `subscription_state` + `source` (hợp đồng E); lỗi gửi không được làm hỏng luồng đồng bộ mua hàng.
        runCatching { IapEventLog.subscriptionState(productId, oldState.name, newState.name, version, source) }
    }

    // ═══ Đọc ═══

    /** Trạng thái Play quan sát trên máy (không bao giờ nhãn server, D28); `NONE` nếu chưa thấy hoặc tính năng tắt. */
    internal fun clientStateFor(productId: String): SubState =
        if (enabled) SubscriptionServerState.clientStateFor(productId) else SubState.NONE
    /** T11 (D2): `in_app_messages.enabled` (mặc định bật) và cả tính năng bật; sản phẩm từng thấy (nhãn client/server). */
    internal fun inAppMessagesEnabled(): Boolean = enabled && inAppMessages
    internal fun knownProductIds(): Set<String> = synchronized(this) { states.keys + history }

    /** Trạng thái hiện tại của một sản phẩm. `NONE` nếu chưa từng thấy hoặc tính năng tắt. */
    public fun stateFor(productId: String): SubState {
        if (!enabled) return SubState.NONE
        return states[productId] ?: SubState.NONE
    }

    /**
     * Đã đến lượt hiện thông báo dunning cho sản phẩm này chưa. App gọi mỗi lần vào
     * foreground; SDK đếm lượt và chỉ trả `true` ở những lượt đúng lịch (mặc định 1, 2, 4).
     * Chỉ `true` khi đang IN_GRACE_PERIOD — đúng lúc Play cần người dùng sửa phương thức
     * thanh toán. Lịch đếm tự đặt lại khi episode grace kết thúc.
     */
    public fun dunningNoticeDue(productId: String): Boolean {
        if (!enabled || dunningCadence.isEmpty()) return false
        if (stateFor(productId) != SubState.IN_GRACE_PERIOD) return false
        val n = (dunningCounts[productId] ?: 0) + 1
        dunningCounts[productId] = n
        persistCounts()
        return n in dunningCadence
    }

    /**
     * Người này đủ điều kiện nhận offer win-back cho sản phẩm: TỪNG trả tiền, hiện KHÔNG còn
     * quyền lợi. Google Play không có win-back native (doc 19 B.8) — cờ này chính là mảnh
     * developer phải tự làm.
     */
    public fun isWinBackEligible(productId: String): Boolean =
        enabled && winBackEnabled && stateFor(productId) == SubState.LAPSED

    public fun winBackEligibleProductIds(): Set<String> =
        if (!enabled || !winBackEnabled) emptySet()
        else states.filterValues { it == SubState.LAPSED }.keys

    /**
     * Deep link chuẩn của Google mở màn hình quản lý subscription — nơi người dùng sửa phương
     * thức thanh toán trong grace period. SDK trả URI; app tự mở (cùng ranh giới không vẽ hộ).
     */
    public fun managementUriFor(productId: String, packageName: String): String? {
        if (productId.isBlank() || packageName.isBlank()) return null
        return "https://play.google.com/store/account/subscriptions" +
            "?sku=${URLEncoder.encode(productId, "UTF-8")}" +
            "&package=${URLEncoder.encode(packageName, "UTF-8")}"
    }

    // ═══ Reset (test) ═══

    @Synchronized
    public fun clear() {
        states = mutableMapOf()
        history = mutableSetOf()
        dunningCounts = mutableMapOf()
        version = "chưa nạp"
        enabled = true
        dunningCadence = DEFAULT_CADENCE
        winBackEnabled = true
        inAppMessages = true
        SubscriptionServerState.clear()
        PreferenceHelper.getInstance().remove(KEY_STATES)
        PreferenceHelper.getInstance().remove(KEY_HISTORY)
        PreferenceHelper.getInstance().remove(KEY_DUNNING)
    }

    // ═══ Persist ═══

    private fun persist(key: String, value: String) {
        PreferenceHelper.getInstance().setValue(key, value)
    }

    private fun persistCounts() {
        persist(KEY_DUNNING, dunningCounts.entries.joinToString(SEP) { "${it.key}:${it.value}" })
    }

    private fun loadStates(): MutableMap<String, SubState> {
        val out = mutableMapOf<String, SubState>()
        for (part in PreferenceHelper.getInstance().get(KEY_STATES, "").split(SEP)) {
            val i = part.lastIndexOf(':')
            if (i <= 0) continue
            val state = try {
                SubState.valueOf(part.substring(i + 1))
            } catch (e: IllegalArgumentException) {
                null
            }
            if (state != null) out[part.substring(0, i)] = state
        }
        return out
    }

    private fun loadCounts(): MutableMap<String, Int> {
        val out = mutableMapOf<String, Int>()
        for (part in PreferenceHelper.getInstance().get(KEY_DUNNING, "").split(SEP)) {
            val i = part.lastIndexOf(':')
            if (i <= 0) continue
            val n = part.substring(i + 1).toIntOrNull()
            if (n != null && n >= 0) out[part.substring(0, i)] = n
        }
        return out
    }

    private fun loadSet(key: String): MutableSet<String> =
        PreferenceHelper.getInstance().get(key, "")
            .split(SEP)
            .filter { it.isNotBlank() }
            .toMutableSet()
}

/** Năm trạng thái của một subscription trên máy này — xem KDoc của [SubscriptionLifecycle]. */
public enum class SubState {
    NONE,
    ACTIVE,
    CANCELLED,
    IN_GRACE_PERIOD,
    LAPSED
}
