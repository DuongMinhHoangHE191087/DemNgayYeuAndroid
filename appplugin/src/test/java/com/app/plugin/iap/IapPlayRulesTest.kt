package com.app.plugin.iap

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PurchasesResponseListener
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.ProductDetailsResponseListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryProductDetailsResult
import com.app.plugin.SdkKillSwitch
import com.app.plugin.consent.ConsentState
import com.app.plugin.consent.ConsentTier
import com.app.plugin.consent.contextWithTcf
import com.app.plugin.brain.DecisionLogQueue
import com.app.plugin.logevent.DurableEvents
import com.app.plugin.logevent.HeldCall
import com.app.plugin.logevent.LogEventTransport
import com.app.plugin.logevent.LogService
import com.app.plugin.logevent.ServerEventCapture
import com.app.plugin.logevent.TelemetryConsentGate
import com.app.plugin.logevent.inMemoryPrefs
import com.app.plugin.until.LogUtil
import com.app.plugin.until.PreferenceHelper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.contains
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.RETURNS_SELF
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import com.google.gson.JsonParser
import okhttp3.RequestBody
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * 1.8.0 Task 5 — luật Play trong state machine (D4, D5, D11, D13/D15, D18, D20 C2, D22), qua [FakeBillingPort] (và
 * `PlayBillingPort` trên `BillingClient` mock cho I8). Nguồn đỏ: `dry180/keep/DryRun180RedTest.kt` (mục I), viết lại
 * trên port của T4. Luồng billing = executor trực tiếp (đồng bộ), trừ I9 dùng luồng "appplugin-billing" thật.
 */
class IapPlayRulesTest {
    private val store: MutableMap<String, Any?> = java.util.Collections.synchronizedMap(mutableMapOf())
    private val helper = PreferenceHelper.getInstance()
    private var previousPrefs: android.content.SharedPreferences? = null
    private fun field(name: String) = IapHelper::class.java.declaredFields.firstOrNull { it.name == name }?.apply { isAccessible = true }

    private fun setExecutor(e: Executor) = field("billingExecutor")?.set(null, e)

    private fun resetLedger() = PurchaseLedger.resetForTest()

    @Before fun setUp() {
        SdkKillSwitch.current = SdkKillSwitch.NONE
        previousPrefs = helper.preferences
        helper.preferences = inMemoryPrefs(store) // "đĩa" của sổ token: sống qua newProcessForTest
        ConsentState.resetForTest()
        TelemetryConsentGate.resetForTest()
        Entitlements.clearAll()
        SubscriptionLifecycle.clear()
        IapHelper.setProducts()
        resetLedger()
        setExecutor(Executor { it.run() })
    }

    @After fun tearDown() {
        IapHelper.replacePort(null)
        IapHelper.setProducts()
        IapHelper.purchaseListener = null
        @Suppress("UNCHECKED_CAST")
        (field("productCache")?.get(null) as MutableMap<String, IapProduct>?)?.clear()
        PurchaseFlow.resetForTest()
        (field("connectScheduled")?.get(null) as AtomicBoolean?)?.set(false)
        setExecutor(Executors.newSingleThreadExecutor { r -> Thread(r, "appplugin-billing").apply { isDaemon = true } })
        resetLedger()
        BillingThread.timer = ScheduledBillingTimer(BillingThread.executor)
        TelemetryConsentGate.resetForTest()
        ConsentState.resetForTest()
        helper.preferences = previousPrefs
        Entitlements.clearAll()
        SubscriptionLifecycle.clear()
        SdkKillSwitch.current = SdkKillSwitch.NONE
    }

    /** Đồng hồ giả của luồng billing: ghi độ trễ, chạy việc hẹn theo thứ tự khi [runAll]. */
    private class FakeBillingTimer : BillingTimer {
        var now = 0L
        val delays = mutableListOf<Long>()
        private val tasks = ArrayDeque<Pair<Long, Runnable>>()
        override fun nowMs(): Long = now
        override fun schedule(delayMs: Long, r: Runnable) {
            delays += delayMs
            tasks.addLast(now + delayMs to r)
        }
        fun runAll() {
            while (tasks.isNotEmpty()) tasks.removeFirst().let { (at, r) -> now = at; r.run() }
        }
    }

    private fun purchase(pid: String, state: IapPurchaseState, token: String, ack: Boolean = false, auto: Boolean = true) =
        PortPurchase(listOf(pid), if (state == IapPurchaseState.PENDING) "" else "GPA.$token", token, PURCHASE_TS, state, ack, auto, 1, false)

    private fun portOwning(subs: List<PortPurchase> = emptyList(), inapp: List<PortPurchase> = emptyList()) = FakeBillingPort().also {
        it.owned[IapProductType.SUBS] = subs
        it.owned[IapProductType.INAPP] = inapp
        IapHelper.replacePort(it)
    }

    private fun updated(code: Int, vararg p: PortPurchase) = IapHelper.onPortPurchasesUpdated(code, p.toList(), "")

    /** SUBS `pro_sub`: offer "otk" (tag `trial7`) = trial 0 đ rồi base 4,99 USD; cache như `toPortOffer` của T4. */
    private fun trialOffer() = PortOffer(
        "pro_sub", IapProductType.SUBS, 4_990_000L, "USD", "4,99 US$", "t", "d",
        listOf(PortOfferPhase("otk", "trial7", listOf(PortPhase(0L, "USD", 1), PortPhase(4_990_000L, "USD", 0))))
    )

    private fun coinOffer() = PortOffer("coin", IapProductType.INAPP, 1_000_000L, "USD", "1 US$", "Coin", "", emptyList())

    // ═══ D4: PENDING không bao giờ cấp, không thành IN_GRACE_PERIOD ═══

    @Test fun `I1 PENDING sub khong cap quyen loi`() {
        portOwning(subs = listOf(purchase("pro_sub", IapPurchaseState.PENDING, "tokP")))
        IapHelper.restorePurchases()
        assertEquals("owned=${Entitlements.activeProductIds()}", emptySet<String>(), Entitlements.activeProductIds())
    }

    @Test fun `I2 PENDING sub khong thanh IN_GRACE_PERIOD`() {
        portOwning(subs = listOf(purchase("pro_sub", IapPurchaseState.PENDING, "tokP")))
        IapHelper.restorePurchases()
        assertNotEquals(SubState.IN_GRACE_PERIOD, SubscriptionLifecycle.stateFor("pro_sub"))
    }

    /** Đặc tả (xanh trên mã cũ — giữ làm hồi quy): PENDING rồi PURCHASED cùng token; `order_id` của lần PURCHASED. */
    @Test fun `I3 pending roi PURCHASED grant 1 va iap_purchase 1 (order_id tu lan PURCHASED)`() {
        portOwning()
        ServerEventCapture().use { cap ->
            updated(BillingPort.OK, purchase("pro_sub", IapPurchaseState.PENDING, "tokQ"))
            assertFalse("PENDING khong grant", Entitlements.isActive("pro_sub"))
            updated(BillingPort.OK, purchase("pro_sub", IapPurchaseState.PURCHASED, "tokQ", ack = true))
            assertTrue("PURCHASED grant", Entitlements.isActive("pro_sub"))
            val e = cap.await("iap_purchase", 1)
            assertEquals(1, e.size)
            assertEquals("GPA.tokQ", e.first().get("order_id").asString)
        }
    }

    // ═══ D5 / D13 / D15: iap_purchase đúng một lần theo token ═══

    @Test fun `I4 restore thay PURCHASED chua ack ack 1 va iap_purchase 1`() {
        val port = portOwning(subs = listOf(purchase("pro_sub", IapPurchaseState.PURCHASED, "tokR")))
        ServerEventCapture().use { cap ->
            IapHelper.restorePurchases()
            assertEquals("ack", 1, port.calls.count { it == "acknowledge:tokR" })
            assertEquals("iap_purchase", 1, cap.await("iap_purchase", 1, 100).size)
        }
    }

    /** Cặp với I4 (đỏ trên mã cũ cùng nguyên nhân); phần khử trùng theo token là đặc tả. */
    @Test fun `I5 restore hai lan cung token iap_purchase van 1`() {
        portOwning(subs = listOf(purchase("pro_sub", IapPurchaseState.PURCHASED, "tokS", ack = true)))
        ServerEventCapture().use { cap ->
            IapHelper.restorePurchases()
            IapHelper.restorePurchases()
            assertEquals("iap_purchase", 1, cap.await("iap_purchase", 1, 100).size)
        }
    }

    /** F4/D5: trial báo pha thực thu (0 đ), `phase` = trial, `offer_tag` của offer đã mua. */
    @Test fun `I6 trial price_micros 0, phase trial, offer_tag`() {
        val port = portOwning()
        port.offers[IapProductType.SUBS] = listOf(trialOffer())
        IapHelper.setProducts(subs = listOf("pro_sub"))
        IapHelper.queryProducts()
        ServerEventCapture().use { cap ->
            updated(BillingPort.OK, purchase("pro_sub", IapPurchaseState.PURCHASED, "tokT", ack = true))
            val e = cap.await("iap_purchase", 1).first()
            assertEquals("price_micros (trial phai 0)", 0L, e.get("price_micros").asLong)
            assertEquals("trial", e.get("phase").asString)
            assertEquals("trial7", e.get("offer_tag").asString)
        }
    }

    /** Đặc tả (seam `onSetup`): kết nối xong → query giá TRƯỚC, khôi phục SAU; giao dịch khôi phục mang giá thật. */
    @Test fun `I7 restore luc ket noi chay sau khi co gia`() {
        val port = portOwning(inapp = listOf(purchase("coin", IapPurchaseState.PURCHASED, "tokC")))
        port.offers[IapProductType.INAPP] = listOf(coinOffer())
        IapHelper.setProducts(inapp = listOf("coin"))
        ServerEventCapture().use { cap ->
            IapHelper::class.java.getDeclaredMethod("onSetup", PortSetup::class.java).apply { isAccessible = true }
                .invoke(IapHelper, PortSetup.CONNECTED)
            val iQuery = port.calls.indexOf("queryProducts:INAPP")
            val iOwned = port.calls.indexOf("queryOwned:INAPP")
            assertTrue("query gia truoc restore: ${port.calls}", iQuery in 0 until iOwned)
            val e = cap.await("iap_purchase", 1).single()
            assertEquals(1_000_000L, e.get("price_micros").asLong)
            assertEquals("inapp", e.get("phase").asString)
        }
    }

    /**
     * R-2 I1: `AppPluginBase.onStart` khôi phục ngay khi `isReady()` (lúc CONNECTED, trước khi `queryAll` xong). Giao dịch
     * của sản phẩm đã khai báo mà chưa có giá → lấy giá trước rồi mới ghi sổ (sổ khử trùng theo token: ghi 0 là mãi mãi 0).
     */
    @Test fun `I15 restore truoc khi co gia query gia truoc, price_micros dung`() {
        val port = portOwning(inapp = listOf(purchase("coin", IapPurchaseState.PURCHASED, "tokE", ack = true)))
        port.offers[IapProductType.INAPP] = listOf(coinOffer())
        IapHelper.setProducts(inapp = listOf("coin"))
        ServerEventCapture().use { cap ->
            IapHelper.restorePurchases()
            val e = cap.await("iap_purchase", 1).single()
            assertEquals(1_000_000L, e.get("price_micros").asLong)
            assertEquals("inapp", e.get("phase").asString)
            val iQuery = port.calls.indexOf("queryProducts:INAPP")
            assertTrue("query gia sau queryOwned, truoc khi ghi so: ${port.calls}", iQuery > port.calls.indexOf("queryOwned:SUBS"))
        }
    }

    // ═══ F7 / D11: BILLING_UNAVAILABLE, luồng billing ═══

    /** Port Play thật trên client mock: `startConnection` trả BILLING_UNAVAILABLE (3), như API 26/28 không có Play. */
    @Test fun `I8 BILLING_UNAVAILABLE usable false, 0 lenh store them, 1 dong log`() {
        val client = mock(BillingClient::class.java)
        `when`(client.connectionState).thenReturn(BillingClient.ConnectionState.DISCONNECTED)
        doAnswer { inv ->
            inv.getArgument<BillingClientStateListener>(0).onBillingSetupFinished(
                BillingResult.newBuilder().setResponseCode(BillingClient.BillingResponseCode.BILLING_UNAVAILABLE).build()
            )
            null
        }.`when`(client).startConnection(any(BillingClientStateListener::class.java))
        val builder = mock(BillingClient.Builder::class.java, RETURNS_SELF).also { `when`(it.build()).thenReturn(client) }
        val ctx = mock(Context::class.java).also { `when`(it.applicationContext).thenReturn(it) }
        mockStatic(Log::class.java).use { log ->
            mockStatic(BillingClient::class.java).use { b ->
                b.`when`<BillingClient.Builder> { BillingClient.newBuilder(any()) }.thenReturn(builder)
                assertTrue(IapHelper.init(ctx))
            }
            assertFalse("usable sau UNAVAILABLE", IapHelper.isReady())
            IapHelper.restorePurchases()
            IapHelper.restorePurchases()
            verify(client, never()).queryPurchasesAsync(any(QueryPurchasesParams::class.java), any(PurchasesResponseListener::class.java))
            log.verify({ Log.e(eq(LogUtil.TAG), contains("IapHelper unavailable: 3")) }, times(1))
        }
    }

    @Test fun `I9 restorePurchases goi tu main moi lenh port tren appplugin-billing`() {
        setExecutor(Executors.newSingleThreadExecutor { r -> Thread(r, "appplugin-billing").apply { isDaemon = true } })
        val port = portOwning()
        Thread({ IapHelper.restorePurchases() }, "main-sim").apply { start(); join() }
        val deadline = System.currentTimeMillis() + 3_000
        while (port.calls.count { it.startsWith("queryOwned") } < 2 && System.currentTimeMillis() < deadline) Thread.sleep(5)
        val threads = port.calls.indices.filter { port.calls[it].startsWith("queryOwned") }.map { port.threads[it] }
        assertEquals("threads=$threads", listOf("appplugin-billing", "appplugin-billing"), threads)
    }

    /** Đặc tả (seam [BillingTimer]): store lỗi tạm thời → thử lại 1, 2, 4, 8, 16 s rồi dừng hẳn. */
    @Test fun `I10 query SERVICE_UNAVAILABLE thu lai 1,2,4,8,16 s roi dung`() {
        val timer = FakeBillingTimer()
        BillingThread.timer = timer
        val port = portOwning()
        repeat(10) { port.queryCodes += BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE }
        IapHelper.setProducts(subs = listOf("pro_sub"))
        IapHelper.queryProducts()
        timer.runAll()
        assertEquals(listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L), timer.delays)
        assertEquals("1 lan dau + 5 lan thu lai", 6, port.calls.count { it == "queryProducts:SUBS" })
    }

    // ═══ iap_result cho mọi kết cục; D22 một luồng mua ═══

    @Test fun `I11 iap_result cancel pending failed success already_owned, D22 mot luong mua`() {
        val port = portOwning()
        port.offers[IapProductType.INAPP] = listOf(coinOffer())
        IapHelper.setProducts(inapp = listOf("coin"))
        IapHelper.queryProducts()
        ServerEventCapture().use { cap ->
            cap.autoAck = true // 8 event: không để hàng đợi (4 request bay) kẹt sau request đầu
            updated(BillingPort.USER_CANCELED)
            val first = cap.await("iap_result", 1)
            assertEquals("iap_result", 1, first.size)
            assertEquals("cancel", first.first().get("outcome").asString)

            updated(BillingPort.OK, purchase("coin", IapPurchaseState.PENDING, "tokA"))
            updated(BillingPort.ITEM_ALREADY_OWNED)
            IapHelper.onPortPurchasesUpdated(6, null, "Loi")
            updated(BillingPort.OK, purchase("coin", IapPurchaseState.PURCHASED, "tokB", ack = true))

            // D22: luồng thứ hai trong lúc luồng đầu còn mở → Failed(FLOW_IN_PROGRESS) ngay, không lệnh store.
            val a = mutableListOf<IapResult>()
            val b = mutableListOf<IapResult>()
            assertTrue(IapHelper.purchase(mock(Activity::class.java), "coin") { a += it })
            assertFalse(IapHelper.purchase(mock(Activity::class.java), "coin") { b += it })
            assertEquals(listOf<IapResult>(IapResult.Failed(-2, "đang có luồng mua")), b)
            assertEquals("một lần mở luồng mua", 1, port.calls.count { it == "launch:coin" })
            updated(BillingPort.USER_CANCELED)
            assertEquals("kết quả về callback đầu", listOf<IapResult>(IapResult.UserCanceled), a)
            assertEquals(1, b.size)

            // D20 C2 → T5: store OK mà không có sản phẩm lúc mua → ITEM_UNAVAILABLE (4), không còn Failed(0).
            val c = mutableListOf<IapResult>()
            IapHelper.replacePort(playPortWithoutDetails())
            assertTrue(IapHelper.purchase(mock(Activity::class.java), "coin") { c += it })
            assertEquals(listOf<IapResult>(IapResult.Failed(BillingClient.BillingResponseCode.ITEM_UNAVAILABLE, "khong co")), c)

            val results = cap.await("iap_result", 8)
            val outcomes = results.map { it.get("outcome").asString }.sorted()
            assertEquals(listOf("already_owned", "cancel", "cancel", "failed", "failed", "failed", "pending", "success"), outcomes)
            assertTrue("iap_result ITEM_UNAVAILABLE", results.any { it.get("response_code").asInt == BillingClient.BillingResponseCode.ITEM_UNAVAILABLE })
        }
    }

    /** Port Play trên client mock sẵn sàng: query giá lúc mua trả OK nhưng danh sách rỗng. */
    private fun playPortWithoutDetails(): BillingPort {
        val client = mock(BillingClient::class.java)
        `when`(client.connectionState).thenReturn(BillingClient.ConnectionState.CONNECTED)
        val empty = mock(QueryProductDetailsResult::class.java).also { `when`(it.productDetailsList).thenReturn(emptyList()) }
        doAnswer { inv ->
            inv.getArgument<ProductDetailsResponseListener>(1).onProductDetailsResponse(
                BillingResult.newBuilder().setResponseCode(BillingClient.BillingResponseCode.OK).setDebugMessage("khong co").build(), empty
            )
            null
        }.`when`(client).queryProductDetailsAsync(any(QueryProductDetailsParams::class.java), any(ProductDetailsResponseListener::class.java))
        return PlayBillingPort(client).also { it.connect { } }
    }

    @Test fun `I12 ack loi iap_result ack_failed, restore ke ack lai`() {
        val p = purchase("pro_sub", IapPurchaseState.PURCHASED, "tokU")
        val port = portOwning(subs = listOf(p))
        port.ackCode = 6
        ServerEventCapture().use { cap ->
            updated(BillingPort.OK, p)
            val e = cap.await("iap_result", 2)
            assertTrue("ack_failed co trong iap_result: ${e.map { it.get("outcome")?.asString }}", e.any { it.get("outcome")?.asString == "ack_failed" })
            port.ackCode = BillingPort.OK
            IapHelper.restorePurchases()
            assertEquals("restore ack lai", 2, port.calls.count { it == "acknowledge:tokU" })
        }
    }

    // ═══ D15: hai dấu `granted`/`reported`, sống qua kill, consent ═══

    private fun awaitReported(token: String) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!PurchaseLedger.isReported(token) && System.currentTimeMillis() < deadline) Thread.sleep(5)
    }

    /** Đặc tả (seam [PurchaseLedger]): kill sau `granted`, trước 2xx → cold start phát lại đúng 1, `ts` gốc, cùng token. */
    @Test fun `I13 granted va chua reported qua kill cold start phat lai iap_purchase 1, ts goc, cung token`() {
        val p = purchase("pro_sub", IapPurchaseState.PURCHASED, "tokK", ack = true)
        portOwning(subs = listOf(p))
        ConsentState.setTier(ConsentTier.GRANTED)
        ServerEventCapture().use { cap -> // server không trả lời: event đã đi nhưng chưa 2xx, rồi process chết
            updated(BillingPort.OK, p)
            assertEquals(1, cap.await("iap_purchase", 1).size)
        }
        assertTrue(PurchaseLedger.isGranted("tokK"))
        assertFalse(PurchaseLedger.isReported("tokK"))

        PurchaseLedger.newProcessForTest()
        ServerEventCapture().use { cap ->
            cap.autoAck = true
            IapHelper.restorePurchases()
            val r = cap.awaitRequests("iap_purchase", 1)
            assertEquals(1, r.size)
            assertEquals(PURCHASE_TS, r.first().get("ts").asLong)
            assertEquals("tokK", r.first().getAsJsonObject("eventData").get("purchase_token").asString)
            awaitReported("tokK")
            assertTrue("2xx -> reported", PurchaseLedger.isReported("tokK"))
            IapHelper.restorePurchases()
            assertEquals("da reported: khong phat nua", 1, cap.await("iap_purchase", 1).size)
        }
    }

    /** Process mới: vòng DENIED của cổng mất theo process cũ; sổ đọc lại từ prefs; tầng [tier]. */
    private fun newProcess(tier: ConsentTier, ctx: Context) {
        TelemetryConsentGate.resetForTest()
        ConsentState.resetForTest()
        PurchaseLedger.newProcessForTest()
        ConsentState.setTier(tier)
        TelemetryConsentGate.install(ctx)
    }

    private fun deniedHeldNamed(name: String): Int {
        val ring = TelemetryConsentGate::class.java.getDeclaredField("deniedHeld").apply { isAccessible = true }.get(TelemetryConsentGate)
        return synchronized(TelemetryConsentGate) { (ring as Collection<*>).count { (it as Pair<*, *>).first.toString().contains(name) } }
    }

    /** Đặc tả (D15, R-P1): DENIED → không gửi, token `!reported`; process sau GRANTED phát lại đúng 1. */
    @Test fun `I14 DENIED khong gui, token chua reported, process sau GRANTED phat lai 1`() {
        // Process 1 DENIED: mua xong, 0 egress. Process 2 vẫn DENIED: không phát lại. Process 3 GRANTED: đúng 1.
        val p = purchase("pro_sub", IapPurchaseState.PURCHASED, "tokD", ack = true)
        portOwning(subs = listOf(p))
        val ctx = contextWithTcf(gdpr = 1, purposes = "")
        ConsentState.setTier(ConsentTier.DENIED)
        TelemetryConsentGate.install(ctx)
        ServerEventCapture().use { cap ->
            cap.autoAck = true
            updated(BillingPort.OK, p)
            IapHelper.restorePurchases() // cùng process, vẫn DENIED: không phát lại
            Thread.sleep(300)
            assertEquals("0 egress sau DENIED", 0, cap.allRequests().count { it.get("eventName").asString == "iap_purchase" })
        }
        assertTrue(Entitlements.isActive("pro_sub"))
        assertFalse(PurchaseLedger.isReported("tokD"))

        newProcess(ConsentTier.DENIED, ctx) // process 2, vẫn DENIED: sổ không phát lại (không vào cả vòng DENIED của cổng)
        ServerEventCapture().use { cap ->
            cap.autoAck = true
            IapHelper.restorePurchases()
            Thread.sleep(300)
            assertEquals("0 egress", 0, cap.allRequests().count { it.get("eventName").asString == "iap_purchase" })
            assertEquals("vong DENIED khong co iap_purchase", 0, deniedHeldNamed("iap_purchase"))
        }
        assertFalse(PurchaseLedger.isReported("tokD"))

        newProcess(ConsentTier.GRANTED, ctx) // process 3, GRANTED: phát lại đúng 1
        ServerEventCapture().use { cap ->
            cap.autoAck = true
            IapHelper.restorePurchases()
            val r = cap.awaitRequests("iap_purchase", 1)
            assertEquals(1, r.size)
            assertEquals(PURCHASE_TS, r.first().get("ts").asLong)
            assertEquals("tokD", r.first().getAsJsonObject("eventData").get("purchase_token").asString)
        }
    }

    // ═══ 1.9.0 Task 11: không ghi giá 0 (R-2 m4), 4xx dừng phát lại (F1a m2), dòng log thành công (Task R #12) ═══

    /** R-2 m4: sản phẩm đã khai, lượt lấy giá lỗi tạm thời → token chưa vào sổ (ghi 0 là mãi 0); lần sau có giá thì ghi đúng. */
    @Test fun `F3 token da khai chua co gia khong ghi price_micros 0, lan sau ghi dung gia`() {
        BillingThread.timer = FakeBillingTimer() // lượt thử lại của query giá không chạy
        val port = portOwning(inapp = listOf(purchase("coin", IapPurchaseState.PURCHASED, "tokZ", ack = true)))
        port.offers[IapProductType.INAPP] = listOf(coinOffer())
        port.queryCodes += BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE
        IapHelper.setProducts(inapp = listOf("coin"))
        ServerEventCapture().use { cap ->
            cap.autoAck = true
            IapHelper.restorePurchases()
            assertFalse("token đã khai chưa có giá vào sổ với price_micros 0", PurchaseLedger.isGranted("tokZ"))
            IapHelper.restorePurchases()
            val e = cap.await("iap_purchase", 1).single()
            assertEquals(1_000_000L, e.get("price_micros").asLong)
        }
    }

    /**
     * D44 (R-T11 I2): consumable đã khai mà lấy giá lỗi bị consume ngay vòng này, Play không trả lại → ghi sổ ngay với giá
     * đang biết (0 khi không biết) + một logE; non-consumable chưa giá vẫn chưa ghi (D43(2)), vòng sau ghi với giá.
     */
    @Test fun `F8 consumable chua co gia ghi 0 mot lan kem logE, non-consumable chua gia van cho vong sau`() {
        BillingThread.timer = FakeBillingTimer()
        val gem = purchase("gem", IapPurchaseState.PURCHASED, "tokN", ack = true)
        val port = portOwning(inapp = listOf(purchase("coin", IapPurchaseState.PURCHASED, "tokQ"), gem))
        port.offers[IapProductType.INAPP] = listOf(coinOffer(), PortOffer("gem", IapProductType.INAPP, 2_000_000L, "USD", "2 US$", "Gem", "", emptyList()))
        port.queryCodes += BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE
        IapHelper.setProducts(inapp = listOf("coin", "gem"), consumable = setOf("coin"))
        ServerEventCapture().use { cap ->
            cap.autoAck = true
            mockStatic(Log::class.java).use { log ->
                IapHelper.restorePurchases()
                assertTrue("consume:tokQ ${port.calls}", "consume:tokQ" in port.calls)
                assertTrue("consumable chưa giá phải vào sổ (Play không trả lại)", PurchaseLedger.isGranted("tokQ"))
                assertFalse("non-consumable chưa giá chưa vào sổ", PurchaseLedger.isGranted("tokN"))
                log.verify({ Log.e(LogUtil.TAG, "IapHelper restore: [coin] chưa có giá, ghi 0 (consumable)") }, times(1))
            }
            port.owned[IapProductType.INAPP] = listOf(gem) // token đã consume: Play không trả nữa
            IapHelper.restorePurchases()
            val rows = cap.await("iap_purchase", 2).associate { it.get("product_id").asString to it.get("price_micros").asLong }
            assertEquals(mapOf("coin" to 0L, "gem" to 2_000_000L), rows)
        }
    }

    /** Server trả [code] cho mọi request (dọn dẹp như [ServerEventCapture]); đếm request `iap_purchase`. */
    private class ServerAnswering(code: Int) : AutoCloseable {
        private val capture = ServerEventCapture()
        val iapPurchases = AtomicInteger(0)

        init {
            LogEventTransport::class.java.getDeclaredField("logService").apply { isAccessible = true }.set(null, object : LogService {
                override fun logActionsToServer(requestBody: RequestBody): Call<ResponseBody> {
                    val json = Buffer().also { requestBody.writeTo(it) }.readUtf8()
                    if (JsonParser.parseString(json).asJsonObject.get("eventName").asString == "iap_purchase") iapPurchases.incrementAndGet()
                    return object : Call<ResponseBody> by HeldCall(json) {
                        override fun enqueue(callback: Callback<ResponseBody>) =
                            callback.onResponse(this, Response.error(code, "err".toResponseBody()))
                    }
                }
            })
        }

        fun awaitIapPurchase() {
            val deadline = System.currentTimeMillis() + 5_000
            while (iapPurchases.get() == 0 && System.currentTimeMillis() < deadline) Thread.sleep(5)
        }

        override fun close() = capture.close()
    }

    /** F1a m2: server từ chối hẳn (4xx ngoài 408/429) → `reported`, process sau không phát lại. */
    @Test fun `F4 4xx vinh vien danh dau reported, process sau khong phat lai`() {
        val p = purchase("pro_sub", IapPurchaseState.PURCHASED, "tokF", ack = true)
        portOwning(subs = listOf(p))
        ConsentState.setTier(ConsentTier.GRANTED)
        ServerAnswering(400).use { srv ->
            updated(BillingPort.OK, p)
            srv.awaitIapPurchase()
            assertEquals(1, srv.iapPurchases.get())
            awaitReported("tokF")
        }
        assertTrue("4xx vĩnh viễn -> reported", PurchaseLedger.isReported("tokF"))
        // R-T11 m4 (2.0.0 B1): bỏ khẳng định "0 request sau 300 ms" (theo thời gian); dấu `reported` sống qua kill là điều kiện
        // đủ để `reemitPending` bỏ qua token (lọc `!reported`).
        PurchaseLedger.newProcessForTest()
        assertTrue("process sau: vẫn reported", PurchaseLedger.isReported("tokF"))
        IapHelper.restorePurchases() // executor billing đồng bộ: reemitPending đã chạy khi trả về
        val emitted = PurchaseLedger::class.java.getDeclaredField("emittedThisProcess").apply { isAccessible = true }.get(null) as Set<*>
        assertFalse("process sau: không phát lại token reported", "tokF" in emitted)
    }

    /** Đặc tả (xanh trên mã cũ): 5xx là lỗi tạm thời — không `reported`, process sau phát lại đúng 1. */
    @Test fun `F5 5xx khong danh dau reported, process sau phat lai`() {
        val p = purchase("pro_sub", IapPurchaseState.PURCHASED, "tokV", ack = true)
        portOwning(subs = listOf(p))
        ConsentState.setTier(ConsentTier.GRANTED)
        ServerAnswering(503).use { srv ->
            updated(BillingPort.OK, p)
            srv.awaitIapPurchase()
            Thread.sleep(200)
        }
        assertFalse("5xx -> chưa reported", PurchaseLedger.isReported("tokV"))
        PurchaseLedger.newProcessForTest()
        ServerEventCapture().use { cap ->
            cap.autoAck = true
            IapHelper.restorePurchases()
            assertEquals("process sau: phát lại", 1, cap.awaitRequests("iap_purchase", 1).size)
            awaitReported("tokV")
            assertTrue(PurchaseLedger.isReported("tokV"))
        }
    }

    // ═══ 2.0.0 B1: 401 tạm thời (T11 m3), listener không ghi giá 0 (R-T11 m2), consume lỗi (R-T11 m6) ═══

    /** Tập bền thật trên thư mục tạm, luồng đĩa = luồng gọi: `iap_purchase` đi qua `Tracked` của tập bền như trong app. */
    private fun withDurable(block: (disk: () -> List<String>) -> Unit) {
        val dir = java.nio.file.Files.createTempDirectory("b1-durable").toFile()
        val q = DecisionLogQueue(java.io.File(dir, "appplugin_events"), DurableEvents.MAX_RECORDS)
        val io = DurableEvents.ioExecutor
        DurableEvents.ioExecutor = Executor { it.run() }
        DurableEvents.attach(q, null)
        try {
            block { q.peekAll().orEmpty() }
        } finally {
            DurableEvents.resetForTest()
            DurableEvents.ioExecutor = io
            dir.deleteRecursively()
        }
    }

    /** Chờ (tối đa 5 s) tới khi sender báo xong thêm [n] kết cục REJECTED/DROPPED kể từ [before]. */
    private fun awaitOutcomes(before: Long, n: Int = 1) {
        val deadline = System.currentTimeMillis() + 5_000
        while (outcomes() < before + n && System.currentTimeMillis() < deadline) Thread.sleep(5)
        assertTrue("sender chưa báo kết cục", outcomes() >= before + n)
    }

    private fun outcomes() = LogEventTransport.rejectedCount.get() + LogEventTransport.droppedCount.get()

    /**
     * 2.5.1 B2FLAKY: một lượt mua sinh hai bản bền (`iap_purchase`, `iap_result`) nhưng [awaitOutcomes] chỉ chờ một kết cục;
     * chờ (tối đa 5 s, poll 5 ms như [awaitReported]) tới khi [disk] rỗng, gọi trước khi đóng server mỗi vòng.
     */
    private fun awaitDiskEmpty(disk: () -> List<String>) {
        val deadline = System.currentTimeMillis() + 5_000
        while (disk().isNotEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(5)
    }

    /**
     * 2.5.1 B2FLAKY r1 (R-L' L-3): [awaitDiskEmpty] thoát ngay khi đĩa rỗng, kể cả khi `iap_result` chưa kịp ghi đĩa (ghi
     * muộn hơn cả vòng `iap_purchase`). Chờ (tối đa 5 s) tới khi đủ [atLeast] bản bền bị từ chối vĩnh viễn — đếm tăng khi
     * bản đó đã ghi, gửi và được xếp xoá (`DurableEvents.onOutcome`), nên sau đó đĩa rỗng nghĩa là cả hai đã rời đĩa.
     */
    private fun awaitDurableRejected(atLeast: Long) {
        val deadline = System.currentTimeMillis() + 5_000
        while (DurableEvents.permanentRejectedCount.get() < atLeast && System.currentTimeMillis() < deadline) Thread.sleep(5)
    }

    private fun gemOffer() = PortOffer("gem", IapProductType.INAPP, 2_000_000L, "USD", "2 US$", "Gem", "", emptyList())

    /** T11 m3 (F1b #1): 401 là tạm thời — bản bền ở lại đĩa, sổ không `reported`, process sau sổ phát lại đúng 1. */
    @Test fun `B1 401 tam thoi ban ben o lai va so phat lai o process sau`() {
        val p = purchase("pro_sub", IapPurchaseState.PURCHASED, "tokA", ack = true)
        portOwning(subs = listOf(p))
        ConsentState.setTier(ConsentTier.GRANTED)
        withDurable { disk ->
            ServerAnswering(401).use { srv ->
                val before = outcomes()
                updated(BillingPort.OK, p)
                srv.awaitIapPurchase()
                awaitOutcomes(before)
            }
            assertEquals("401: bản bền iap_purchase ở lại đĩa", 1, disk().count { it.startsWith("iap_purchase\u001F") })
        }
        PurchaseLedger.newProcessForTest()
        assertFalse("401 -> chưa reported", PurchaseLedger.isReported("tokA"))
        ServerEventCapture().use { cap ->
            cap.autoAck = true
            IapHelper.restorePurchases()
            val r = cap.awaitRequests("iap_purchase", 1)
            assertEquals("process sau: sổ phát lại đúng 1", 1, r.size)
            assertEquals("tokA", r.single().getAsJsonObject("eventData").get("purchase_token").asString)
            awaitReported("tokA")
            assertTrue(PurchaseLedger.isReported("tokA"))
        }
    }

    /** Đặc tả (xanh trên mã cũ): 400 và 413 vẫn vĩnh viễn — bản bền rời đĩa, đếm, sổ `reported`. */
    @Test fun `B2 400 va 413 van vinh vien roi dia va reported`() {
        val a = purchase("pro_sub", IapPurchaseState.PURCHASED, "tok400", ack = true)
        val b = purchase("coin", IapPurchaseState.PURCHASED, "tok413", ack = true)
        portOwning(subs = listOf(a), inapp = listOf(b))
        ConsentState.setTier(ConsentTier.GRANTED)
        withDurable { disk ->
            for ((code, p) in listOf(400 to a, 413 to b)) ServerAnswering(code).use { srv ->
                val before = outcomes()
                val rejected = DurableEvents.permanentRejectedCount.get()
                updated(BillingPort.OK, p)
                srv.awaitIapPurchase()
                awaitOutcomes(before)
                awaitReported(p.token)
                awaitDurableRejected(rejected + 2) // 2.5.1 B2FLAKY r1: cả iap_result ghi đĩa muộn (R-L' L-3)
                awaitDiskEmpty(disk) // 2.5.1 B2FLAKY: iap_result là kết cục thứ hai, chờ trước khi đóng server
            }
            assertEquals("400/413: bản bền rời đĩa (iap_purchase + iap_result)", emptyList<String>(), disk())
        }
        assertTrue("400 -> reported", PurchaseLedger.isReported("tok400"))
        assertTrue("413 -> reported", PurchaseLedger.isReported("tok413"))
    }

    /** R-T11 m2: đường listener, sản phẩm đã khai chưa có giá → chưa ghi sổ (không `price_micros 0`); khôi phục sau ghi đúng giá. */
    @Test fun `B3 listener san pham da khai chua co gia khong ghi price_micros 0`() {
        BillingThread.timer = FakeBillingTimer()
        val gem = purchase("gem", IapPurchaseState.PURCHASED, "tokL")
        val port = portOwning(inapp = listOf(gem))
        port.offers[IapProductType.INAPP] = listOf(gemOffer())
        IapHelper.setProducts(inapp = listOf("gem"))
        ServerEventCapture().use { cap ->
            cap.autoAck = true
            mockStatic(Log::class.java).use { log ->
                updated(BillingPort.OK, gem)
                assertTrue("quyền lợi vẫn cấp", Entitlements.isActive("gem"))
                assertFalse("listener: chưa có giá vào sổ với price_micros 0", PurchaseLedger.isGranted("tokL"))
                log.verify({ Log.e(LogUtil.TAG, "IapHelper listener: [gem] chưa có giá, chưa ghi sổ") }, times(1))
            }
            IapHelper.restorePurchases()
            val e = cap.await("iap_purchase", 1).single()
            assertEquals(2_000_000L, e.get("price_micros").asLong)
        }
    }

    /** Đặc tả (xanh trên mã cũ): đường listener, sản phẩm có giá → vào sổ ngay với giá thật. */
    @Test fun `B4 listener san pham co gia ghi so voi gia that`() {
        val port = portOwning()
        port.offers[IapProductType.INAPP] = listOf(gemOffer())
        IapHelper.setProducts(inapp = listOf("gem"))
        IapHelper.queryProducts()
        ServerEventCapture().use { cap ->
            cap.autoAck = true
            updated(BillingPort.OK, purchase("gem", IapPurchaseState.PURCHASED, "tokP"))
            assertTrue(PurchaseLedger.isGranted("tokP"))
            assertEquals(2_000_000L, cap.await("iap_purchase", 1).single().get("price_micros").asLong)
        }
    }

    /** R-T11 m6: consume lỗi với consumable chưa giá → chưa vào sổ ở 0; Play trả lại token, vòng sau có giá thì ghi đúng giá. */
    @Test fun `B5 consume loi voi consumable chua gia khong de hang o gia 0`() {
        BillingThread.timer = FakeBillingTimer()
        val port = portOwning(inapp = listOf(purchase("coin", IapPurchaseState.PURCHASED, "tokM")))
        port.offers[IapProductType.INAPP] = listOf(coinOffer())
        port.queryCodes += BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE
        port.consumeCode = BillingClient.BillingResponseCode.ERROR
        IapHelper.setProducts(inapp = listOf("coin"), consumable = setOf("coin"))
        ServerEventCapture().use { cap ->
            cap.autoAck = true
            IapHelper.restorePurchases()
            assertTrue("consume:tokM ${port.calls}", "consume:tokM" in port.calls)
            assertFalse("consume lỗi: consumable chưa giá không vào sổ ở 0", PurchaseLedger.isGranted("tokM"))
            port.consumeCode = BillingPort.OK
            IapHelper.restorePurchases() // Play trả lại token chưa consume; lượt này lấy được giá
            val e = cap.await("iap_purchase", 1).single()
            assertEquals(1_000_000L, e.get("price_micros").asLong)

            // Đường listener, kết quả consume tới SAU quyết định ghi (như luồng billing thật): chờ, OK thì ghi 0 kèm logE.
            @Suppress("UNCHECKED_CAST")
            (field("productCache")?.get(null) as MutableMap<String, IapProduct>?)?.clear()
            port.holdConsume = true
            mockStatic(Log::class.java).use { log ->
                updated(BillingPort.OK, purchase("coin", IapPurchaseState.PURCHASED, "tokW"))
                assertFalse("chờ kết quả consume", PurchaseLedger.isGranted("tokW"))
                port.heldConsumes.removeAt(0).invoke()
                assertTrue("consume OK: consumable chưa giá vào sổ", PurchaseLedger.isGranted("tokW"))
                log.verify({ Log.e(LogUtil.TAG, "IapHelper listener: [coin] chưa có giá, ghi 0 (consumable)") }, times(1))
            }
            port.consumeCode = BillingClient.BillingResponseCode.ERROR
            updated(BillingPort.OK, purchase("coin", IapPurchaseState.PURCHASED, "tokX"))
            port.heldConsumes.removeAt(0).invoke()
            assertFalse("listener, consume lỗi sau quyết định: không vào sổ ở 0", PurchaseLedger.isGranted("tokX"))
        }
    }

    /** Task R #12 (info gap 1.8.0): query giá thành công ghi `IapHelper products query code=<mã> n=<số sản phẩm> type=<loại>`. */
    @Test fun `F6 query gia thanh cong ghi dong IapHelper products query code n`() {
        val client = mock(BillingClient::class.java)
        `when`(client.connectionState).thenReturn(BillingClient.ConnectionState.CONNECTED)
        val once = mock(ProductDetails.OneTimePurchaseOfferDetails::class.java)
        `when`(once.priceAmountMicros).thenReturn(1_000_000L)
        `when`(once.priceCurrencyCode).thenReturn("USD")
        `when`(once.formattedPrice).thenReturn("1 US$")
        val details = mock(ProductDetails::class.java)
        `when`(details.productId).thenReturn("coin")
        `when`(details.title).thenReturn("Coin")
        `when`(details.description).thenReturn("")
        `when`(details.oneTimePurchaseOfferDetails).thenReturn(once)
        val found = mock(QueryProductDetailsResult::class.java).also { `when`(it.productDetailsList).thenReturn(listOf(details)) }
        doAnswer { inv ->
            inv.getArgument<ProductDetailsResponseListener>(1).onProductDetailsResponse(
                BillingResult.newBuilder().setResponseCode(BillingClient.BillingResponseCode.OK).build(), found
            )
            null
        }.`when`(client).queryProductDetailsAsync(any(QueryProductDetailsParams::class.java), any(ProductDetailsResponseListener::class.java))
        LogUtil.setFlagLog(3)
        try {
            mockStatic(Log::class.java).use { log ->
                var n = -1
                PlayBillingPort(client).also { it.connect { } }.queryProducts(IapProductType.INAPP, listOf("coin")) { _, offers, _ -> n = offers.size }
                assertEquals(1, n)
                log.verify({ Log.d(LogUtil.TAG, "IapHelper products query code=0 n=1 type=INAPP") }, times(1)) // R-T11 m5
            }
        } finally {
            LogUtil.setFlagLog(1)
        }
    }

    private companion object {
        const val PURCHASE_TS = 1_700_000_000_000L
    }
}
