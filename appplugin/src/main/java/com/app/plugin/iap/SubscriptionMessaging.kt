package com.app.plugin.iap

import android.app.Activity
import com.app.plugin.until.LogUtil
import com.app.plugin.until.PreferenceHelper
import java.lang.ref.WeakReference
import java.util.concurrent.Executor

/**
 * Hộp thư Play tự động (1.8.0 Task 11, D2, D27): ở lần vào foreground (`AppPluginBase.onStart`), SDK gọi
 * `BillingClient.showInAppMessages` (`TRANSACTIONAL`) để Play nhắc người dùng sửa thanh toán khi sub đang grace/hold.
 * Không API public (D1).
 *
 * Gọi khi có ít nhất một sản phẩm cần nhắc ([triggers]) mà lần gọi trước cho sản phẩm đó đã ≥ [MIN_GAP_MS] (24 h):
 * - nhãn `IN_GRACE_PERIOD` ([SubscriptionLifecycle.stateFor]);
 * - trạng thái server gốc `GRACE`/`ON_HOLD` còn tươi ([SubscriptionServerState.freshLabel], cửa sổ 7 ngày, D27) — hold
 *   thì Play không trả sub về nên nhãn client là LAPSED, vẫn phải nhắc;
 * - sub `suspended` ở lượt `queryOwned(SUBS)` thành công gần nhất (port Play, [onOwnedSubs] từ `BillingPorts`).
 * Và: `in_app_messages.enabled` bật ([SubscriptionLifecycle.inAppMessagesEnabled], mặc định bật); harness G12 không cấm
 * ([IapHarness.inAppMessagesAllowed]); port gọi store được.
 *
 * Luồng: quyết định + đọc/ghi prefs trên "appplugin-billing"; chỉ lời gọi Play (UI, `@UiThread`) về main — ngoại lệ duy
 * nhất của D2. Kết quả `SUBSCRIPTION_STATUS_UPDATED` → `IapHelper.restorePurchases()`.
 */
internal object SubscriptionMessaging {
    const val MIN_GAP_MS = 24L * 3600 * 1000
    private const val KEY_LAST = "sub_inapp_msg_last"
    private val SERVER_STATES = setOf("GRACE", "ON_HOLD")
    private const val SEP = "|"
    private const val FS = ":"

    /** Luồng billing (`BillingThread.executor`); test thay. `null` = mặc định. */
    @Volatile internal var executor: Executor? = null

    /** Đồng hồ ms epoch (trần 24 h, cửa sổ D27); test thay. */
    @Volatile internal var clock: () -> Long = System::currentTimeMillis

    /**
     * Khối `in_app_messages` của `subscription_lifecycle_android` (R-11 m3): vắng khối hoặc vắng `enabled` → bật (D2);
     * `enabled` boolean → giá trị đó; sai dạng (khối không phải object, `enabled` không phải boolean) → **tắt** (fail-closed).
     */
    fun enabledIn(block: Any?): Boolean {
        if (block == null) return true
        val enabled = (block as? Map<*, *> ?: return false)["enabled"] ?: return true
        return enabled as? Boolean ?: false
    }

    /** Sub `suspended` ở lượt `queryOwned(SUBS)` thành công gần nhất (1.8.0: port Play chưa bật opt-in → luôn rỗng, D29). */
    @Volatile private var suspendedSubs: Set<String> = emptySet()

    /** `BillingPorts` (port Play, luồng của thư viện): chỉ ghi nhận, không việc gì khác. */
    fun onOwnedSubs(purchases: List<PortPurchase>) {
        suspendedSubs = purchases.filter { it.suspended }.flatMap { it.products }.filter { it.isNotBlank() }.toSet()
    }

    /** `AppPluginBase.onStart` (main): chỉ xếp việc lên luồng billing; app chưa dựng port billing → không làm gì. */
    fun onForeground(activity: Activity?) {
        if (activity == null || BillingPorts.active == null) return
        val ref = WeakReference(activity)
        try {
            (executor ?: BillingThread.executor).execute {
                try { check(ref) } catch (e: Exception) { LogUtil.logE("in-app messages: ${e.message}") }
            }
        } catch (e: Exception) {
            LogUtil.logE("in-app messages not scheduled: ${e.message}")
        }
    }

    /** Luồng billing. Ghi mốc trước khi đưa lời gọi về main: hai foreground liền nhau không gọi hai lần. */
    private fun check(ref: WeakReference<Activity>) {
        if (!SubscriptionLifecycle.inAppMessagesEnabled() || !IapHarness.inAppMessagesAllowed()) return
        val port = BillingPorts.active ?: return
        if (!port.usable()) return
        val now = clock()
        val last = loadLast()
        val due = triggers(now).filter { pid -> last[pid]?.let { now - it >= MIN_GAP_MS || now < it } ?: true }
        if (due.isEmpty()) return
        PreferenceHelper.getInstance().setValue(KEY_LAST, (last + due.associateWith { now }).entries.joinToString(SEP) { "${it.key}$FS${it.value}" })
        LogUtil.logD("in-app messages: gọi Play cho ${due.sorted()}")
        BillingThread.postMain { show(port, ref) }
    }

    /** Sản phẩm cần nhắc lúc [now] (xem KDoc lớp). */
    internal fun triggers(now: Long): Set<String> {
        val out = suspendedSubs.toMutableSet()
        for (pid in SubscriptionLifecycle.knownProductIds()) { // nhãn server cũng đi qua SubscriptionLifecycle (T10)
            val grace = SubscriptionLifecycle.stateFor(pid) == SubState.IN_GRACE_PERIOD
            val server = SubscriptionServerState.stateFor(pid) in SERVER_STATES && SubscriptionServerState.freshLabel(pid, now) != null
            if (grace || server) out += pid
        }
        return out
    }

    /** Main: lời gọi Play (UI). */
    private fun show(port: BillingPort, ref: WeakReference<Activity>) {
        val activity = ref.get()
        if (activity == null || activity.isFinishing || activity.isDestroyed) return
        try {
            port.showInAppMessages(activity) { updated -> if (updated) IapHelper.restorePurchases() }
        } catch (e: Exception) {
            LogUtil.logE("in-app messages failed: ${e.message}")
        }
    }

    private fun loadLast(): Map<String, Long> =
        PreferenceHelper.getInstance().get(KEY_LAST, "").split(SEP).mapNotNull { part ->
            val i = part.lastIndexOf(FS)
            val ms = if (i > 0) part.substring(i + 1).toLongOrNull() else null
            if (ms != null) part.substring(0, i) to ms else null
        }.toMap()

    /** Test: như cài mới. */
    internal fun resetForTest() {
        executor = null
        clock = System::currentTimeMillis
        suspendedSubs = emptySet()
        PreferenceHelper.getInstance().remove(KEY_LAST)
    }
}
