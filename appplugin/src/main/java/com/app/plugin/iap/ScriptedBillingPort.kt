package com.app.plugin.iap

import android.app.Activity
import java.util.concurrent.ConcurrentHashMap

/**
 * Port kịch bản của harness G12 (1.8.0 Task 8, §Hợp đồng B): trả lời store theo một hàng của [IapScripts], tất định,
 * không gọi Play. Chỉ `BillingPorts.harness` tạo lớp này, và chỉ sau khi [IapHarness] thấy đủ 3 khoá.
 *
 * Có trạng thái như store thật: giao dịch PURCHASED đã giao qua purchases-updated nằm lại trong `queryOwned`, `acknowledge`
 * đánh dấu đã ack (lần sau `queryOwned` trả acknowledged), `consume` xoá. Mỗi lệnh ghi một dòng
 * `iap harness: <tên> step=<k> <kết quả>`; mỗi `showInAppMessages` ghi đúng dòng `iap harness: in-app-messages call`
 * (T16 đếm dòng này, D2). `acknowledge` ghi `first=true|false`: hai lượt khôi phục chồng nhau (foreground + harness)
 * đều ack như Play cho phép; lượt đầu mới là ack thật.
 *
 * Luồng: như `PlayBillingPort` — lệnh chạy trên luồng gọi (IapHelper gọi trên "appplugin-billing"), purchases-updated tới
 * trên main như thư viện Play (`BillingThread.postMain`), trễ theo đồng hồ của luồng billing (`BillingThread.timer`).
 */
internal class ScriptedBillingPort(
    private val script: IapScripts.Script,
    private val clock: () -> Long = System::currentTimeMillis,
) : BillingPort {
    private val owned = ConcurrentHashMap<String, PortPurchase>()
    private val acked: MutableSet<String> = ConcurrentHashMap.newKeySet()

    init {
        script.owned?.let { isAcked ->
            owned[script.token] = purchase(script.productId, IapPurchaseState.PURCHASED)
            if (isAcked) acked += script.token
        }
    }

    private fun step(text: String) = IapHarness.step(script.name, text)

    private fun purchase(productId: String, state: IapPurchaseState) = PortPurchase(
        products = listOf(productId),
        orderId = if (state == IapPurchaseState.PURCHASED) "GPA.${script.token}" else "",
        token = script.token,
        timeMs = clock(),
        state = state,
        acknowledged = script.token in acked,
        autoRenewing = IapScripts.typeOf(productId) == IapProductType.SUBS,
        quantity = 1,
        suspended = false
    )

    override fun connect(onSetup: (PortSetup) -> Unit) {
        step("connect CONNECTED")
        onSetup(PortSetup.CONNECTED)
    }

    override fun usable(): Boolean = true

    override fun queryProducts(type: IapProductType, ids: List<String>, onDone: (code: Int, List<PortOffer>, message: String) -> Unit) {
        val list = IapScripts.offers(type).filter { it.productId in ids }
        step("queryProducts $type n=${list.size}")
        onDone(BillingPort.OK, list, "")
    }

    override fun launch(activity: Activity, productId: String, offerToken: String, onLaunch: (code: Int, message: String) -> Unit) {
        if (script.updates.isEmpty()) {
            step("launch $productId no-callback")
            return
        }
        step("launch $productId")
        for (u in script.updates) BillingThread.timer.schedule(u.afterMs, Runnable { deliver(productId, u) })
    }

    private fun deliver(productId: String, u: IapScripts.Update) {
        val list = u.state?.let { listOf(purchase(productId, it)) }
        list?.filter { it.state == IapPurchaseState.PURCHASED }?.forEach { owned[it.token] = it }
        step("purchases-updated code=${u.code} state=${u.state ?: "-"}")
        BillingThread.postMain { IapHelper.onPortPurchasesUpdated(u.code, list, "") }
    }

    override fun queryOwned(type: IapProductType, onDone: (ok: Boolean, List<PortPurchase>, message: String) -> Unit) {
        if (!script.ownedOk) {
            step("queryOwned $type ok=false")
            onDone(false, emptyList(), "harness: store error")
            return
        }
        val list = owned.values.filter { IapScripts.typeOf(it.products.first()) == type }
            .map { it.copy(acknowledged = it.token in acked) }
        step("queryOwned $type n=${list.size}")
        onDone(true, list, "")
    }

    override fun acknowledge(token: String, onDone: (code: Int, message: String) -> Unit) {
        val first = acked.add(token)
        step("acknowledge token=$token first=$first")
        onDone(BillingPort.OK, "")
    }

    override fun consume(token: String, onDone: (code: Int, message: String) -> Unit) {
        owned.remove(token)
        step("consume token=$token")
        onDone(BillingPort.OK, "")
    }

    override fun showInAppMessages(activity: Activity, onResult: (subscriptionUpdated: Boolean) -> Unit) {
        IapHarness.log(IapHarness.IN_APP_MESSAGES_LINE)
        onResult(false)
    }

    override fun end() = step("end")
}
