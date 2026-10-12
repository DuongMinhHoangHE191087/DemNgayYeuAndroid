package com.app.plugin.iap

import android.app.Activity

/**
 * Port giả cho test (1.8.0 Task 4): trả lời đồng bộ trên luồng gọi, ghi lại mọi lệnh vào [calls].
 * Dữ liệu store đặt trước qua [offers]/[owned]; không cần mock `BillingClient`.
 * 1.8.0 Task 5: [threads] = tên luồng của mỗi lệnh (cùng thứ tự [calls]); [queryCodes] = mã lần lượt của `queryProducts`
 * (hết danh sách thì OK); [ackCode] = mã của `acknowledge`; [consumeCode] = mã của `consume` (2.0.0 B1, m6).
 */
internal class FakeBillingPort(var isUsable: Boolean = true) : BillingPort {
    val calls: MutableList<String> = java.util.concurrent.CopyOnWriteArrayList()
    val threads: MutableList<String> = java.util.concurrent.CopyOnWriteArrayList()
    val queryCodes: MutableList<Int> = java.util.concurrent.CopyOnWriteArrayList()
    var ackCode = BillingPort.OK
    var consumeCode = BillingPort.OK
    /** `true`: callback của `consume` giữ trong [heldConsumes] (như Play trả sau), test tự gọi. */
    var holdConsume = false
    val heldConsumes: MutableList<() -> Unit> = java.util.concurrent.CopyOnWriteArrayList()
    val offers = mutableMapOf<IapProductType, List<PortOffer>>()
    val owned = mutableMapOf<IapProductType, List<PortPurchase>>()
    var ownedOk = true
    var setup: PortSetup? = null
    /** `null` = mở được luồng mua (không gọi `onLaunch`); khác = (mã, message) báo không mở được. */
    var launchFailure: Pair<Int, String>? = null

    private fun record(call: String) {
        threads += Thread.currentThread().name
        calls += call
    }

    override fun connect(onSetup: (PortSetup) -> Unit) {
        record("connect")
        setup?.let(onSetup)
    }

    override fun usable(): Boolean = isUsable

    override fun queryProducts(type: IapProductType, ids: List<String>, onDone: (code: Int, List<PortOffer>, message: String) -> Unit) {
        record("queryProducts:$type")
        val code = if (queryCodes.isEmpty()) BillingPort.OK else queryCodes.removeAt(0)
        onDone(code, if (code == BillingPort.OK) offers[type].orEmpty().filter { it.productId in ids } else emptyList(), "")
    }

    override fun launch(activity: Activity, productId: String, offerToken: String, onLaunch: (code: Int, message: String) -> Unit) {
        record("launch:$productId")
        launchFailure?.let { (code, message) -> onLaunch(code, message) }
    }

    override fun queryOwned(type: IapProductType, onDone: (ok: Boolean, List<PortPurchase>, message: String) -> Unit) {
        record("queryOwned:$type")
        onDone(ownedOk, if (ownedOk) owned[type].orEmpty() else emptyList(), "")
    }

    override fun acknowledge(token: String, onDone: (code: Int, message: String) -> Unit) {
        record("acknowledge:$token")
        onDone(ackCode, "")
    }

    override fun consume(token: String, onDone: (code: Int, message: String) -> Unit) {
        record("consume:$token")
        val code = consumeCode
        if (holdConsume) heldConsumes += { onDone(code, "") } else onDone(code, "")
    }

    override fun showInAppMessages(activity: Activity, onResult: (subscriptionUpdated: Boolean) -> Unit) {
        record("showInAppMessages")
        onResult(false)
    }

    override fun end() {
        record("end")
    }
}

/** 1.8.0 Task 5: lệnh port + xử lý của `IapHelper` chạy đồng bộ trên luồng test (executor trực tiếp thay "appplugin-billing"). */
internal fun useDirectBillingExecutor() =
    IapHelper::class.java.getDeclaredField("billingExecutor").apply { isAccessible = true }.set(null, java.util.concurrent.Executor { it.run() })

/**
 * §4.3 (1.8.0): đưa `IapHelper` về trạng thái chưa `init` — port null, chưa lên lịch kết nối, executor trực tiếp — để test gọi
 * `MonetizationSdk.configure`/`IapHelper.init` không rò `connectScheduled`/`port` sang lớp test khác qua luồng billing thật
 * (flake `MonetizationSdkTest` "kill switch iap" khi máy tải nặng).
 */
internal fun resetIapHelperForTest() {
    IapHelper.replacePort(null)
    (IapHelper::class.java.getDeclaredField("connectScheduled").apply { isAccessible = true }.get(null) as java.util.concurrent.atomic.AtomicBoolean).set(false)
    useDirectBillingExecutor()
}
