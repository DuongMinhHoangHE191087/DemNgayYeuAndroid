package com.app.plugin.iap

import android.content.Context

/**
 * Chọn port cho `IapHelper` (1.8.0 Task 4). Play là mặc định; harness G12 (Task 8) là nhánh duy nhất khác, chỉ khi
 * [IapHarness] đã arm (đủ 3 khoá). T11: cài guard crash `ProxyBillingActivity` (dự phòng idempotent, D29) và bọc
 * port Play để [SubscriptionMessaging] thấy sub `suspended` ở lượt khôi phục gần nhất.
 */
internal object BillingPorts {
    /** Port giao cho `IapHelper` gần nhất: harness `end()` nó khi thay bằng port kịch bản. */
    @Volatile var active: BillingPort? = null

    /** `IapHelper.init` → luồng "appplugin-billing". Guard đã cài trên main ở `AppPluginBase.onCreate`; đây là dự phòng. */
    fun select(ctx: Context): BillingPort {
        BillingProxyCrashGuard.install()
        return (IapHarness.port ?: observed(PlayBillingPort(ctx))).also { active = it }
    }

    /** Chỗ duy nhất tạo [ScriptedBillingPort] (A7). */
    fun harness(script: IapScripts.Script): BillingPort = ScriptedBillingPort(script).also { active = it }

    /** Port chuyển nguyên mọi lệnh; `queryOwned(SUBS)` thành công thì báo danh sách cho [SubscriptionMessaging] trước. */
    fun observed(port: BillingPort): BillingPort = Observed(port)

    private class Observed(private val inner: BillingPort) : BillingPort by inner {
        override fun queryOwned(type: IapProductType, onDone: (ok: Boolean, List<PortPurchase>, message: String) -> Unit) =
            inner.queryOwned(type) { ok, purchases, message ->
                if (ok && type == IapProductType.SUBS) SubscriptionMessaging.onOwnedSubs(purchases)
                onDone(ok, purchases, message)
            }
    }
}
