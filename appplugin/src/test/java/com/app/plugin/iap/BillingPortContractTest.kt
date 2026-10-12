package com.app.plugin.iap

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.ProductDetailsResponseListener
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryProductDetailsResult
import com.app.plugin.SdkKillSwitch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.RETURNS_SELF
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.io.File

/** 1.8.0 Task 4 (§Hợp đồng A): `IapHelper` chỉ còn state machine, mọi lệnh store đi qua `BillingPort`. */
class BillingPortContractTest {
    private val iapDir = File("src/main/java/com/app/plugin/iap")

    @Before fun setUp() {
        SdkKillSwitch.current = SdkKillSwitch.NONE
        IapHelper.setProducts()
        Entitlements.clearAll()
        SubscriptionLifecycle.clear()
        useDirectBillingExecutor()
    }

    @After fun tearDown() {
        IapHelper.replacePort(null)
        IapHelper.setProducts()
        @Suppress("UNCHECKED_CAST")
        (IapHelper::class.java.declaredFields.firstOrNull { it.name == "productCache" }?.apply { isAccessible = true }
            ?.get(null) as MutableMap<String, IapProduct>?)?.clear()
        Entitlements.clearAll()
        SubscriptionLifecycle.clear()
    }

    /** P1 (nguồn, đỏ trên mã cũ: `expected:<0> but was:<11>`): kiểu Play Billing chỉ còn trong `PlayBillingPort`. */
    @Test fun `P1 IapHelper khong import com android billingclient`() {
        val n = File(iapDir, "IapHelper.kt").readLines().count { it.startsWith("import com.android.billingclient") }
        assertEquals(0, n)
    }

    /** P2 (nguồn, đặc tả): port là chi tiết nội bộ — mọi lớp implement nó và mọi khai báo của 3 file port là `internal`. */
    @Test fun `P2 moi lop implement BillingPort la internal`() {
        val impl = Regex("""^(\s*)([a-z ]*?)(class|object)\s+(\w+)[^\n{]*:\s*[^\n{]*\bBillingPort\b""", RegexOption.MULTILINE)
        val found = File("src/main/java").walkTopDown().filter { it.isFile && it.extension == "kt" }
            .flatMap { f -> impl.findAll(f.readText()).map { f.name to it } }.toList()
        assertTrue("không thấy PlayBillingPort: $found", found.any { it.second.groupValues[4] == "PlayBillingPort" })
        val notInternal = found.filter { (_, m) -> "internal" !in m.groupValues[2] && "private" !in m.groupValues[2] }
        assertTrue("lớp implement BillingPort không internal: ${notInternal.map { it.second.value }}", notInternal.isEmpty())
        val topLevel = Regex("""^(?!internal |private |package |import |[\s/*})])(.+)$""", RegexOption.MULTILINE)
        val loose = listOf("BillingPort.kt", "PlayBillingPort.kt", "BillingPorts.kt").flatMap { name ->
            topLevel.findAll(File(iapDir, name).readText()).map { "$name: ${it.value}" }.toList()
        }
        assertTrue("khai báo top-level không internal: $loose", loose.isEmpty())
    }

    /** P3 (đặc tả): `replacePort` thay port không cần reflection; restore đi qua port giả, ack qua port. */
    @Test fun `P3 replacePort(FakeBillingPort) restore dung port gia, khong reflection`() {
        val fake = FakeBillingPort()
        fake.owned[IapProductType.INAPP] = listOf(
            PortPurchase(listOf("premium"), "GPA.1", "tok1", 1L, IapPurchaseState.PURCHASED, false, false, 1, false)
        )
        // D18: sub bị treo (`isSuspended`) = không cấp, dù state PURCHASED.
        fake.owned[IapProductType.SUBS] = listOf(
            PortPurchase(listOf("vip"), "GPA.2", "tok2", 2L, IapPurchaseState.PURCHASED, true, true, 1, true)
        )
        IapHelper.replacePort(fake)
        var got: Set<String>? = null

        IapHelper.restorePurchases { got = it }

        assertEquals(setOf("premium"), got)
        assertTrue("quyền lợi cấp từ port giả", Entitlements.isActive("premium"))
        assertFalse("sub bị treo không được cấp", Entitlements.isActive("vip"))
        assertEquals(listOf("queryOwned:INAPP", "acknowledge:tok1", "queryOwned:SUBS"), fake.calls)
    }

    /** P4 (đặc tả): `PlayBillingPort` không tự chuyển luồng — `startConnection` chạy trên luồng gọi `connect`. */
    @Test fun `P4 PlayBillingPort connect goi startConnection tren luong goi`() {
        val client = mock(BillingClient::class.java)
        var seen: String? = null
        doAnswer { seen = Thread.currentThread().name; null }
            .`when`(client).startConnection(any(BillingClientStateListener::class.java))
        val port = PlayBillingPort(client)

        val caller = Thread({ port.connect { } }, "t4-p4-caller")
        caller.start()
        caller.join(5_000)

        assertEquals("t4-p4-caller", seen)
        verify(client, times(1)).startConnection(any(BillingClientStateListener::class.java))
        assertTrue("đã startConnection, DISCONNECTED: sẵn sàng", port.usable())
    }

    /** P5 (đặc tả): có giá sản phẩm nhưng port chưa usable → `Failed(-1, …)` như mã cũ, không mở luồng mua. */
    @Test fun `P5 purchase that bai khi port chua usable Failed(-1) nhu cu`() {
        val fake = FakeBillingPort()
        fake.offers[IapProductType.INAPP] =
            listOf(PortOffer("coin", IapProductType.INAPP, 1_000_000L, "USD", "1 $", "Coin", "", emptyList()))
        IapHelper.setProducts(inapp = listOf("coin"))
        IapHelper.replacePort(fake)
        IapHelper.queryProducts()
        assertNotNull("giá đã vào cache qua port", IapHelper.productFor("coin"))
        fake.isUsable = false
        var result: IapResult? = null

        val launched = IapHelper.purchase(mock(Activity::class.java), "coin") { result = it }

        assertFalse(launched)
        assertEquals(IapResult.Failed(-1, "billing chưa sẵn sàng hoặc chưa có giá sản phẩm"), result)
        assertFalse("không mở luồng mua: ${fake.calls}", fake.calls.any { it.startsWith("launch") })
    }

    // ═══ D20 (follow-up T4): hành vi trước T4 giữ nguyên — debugMessage của store và thứ tự pendingFlowCallback ═══

    private fun result(code: Int, message: String = ""): BillingResult =
        BillingResult.newBuilder().setResponseCode(code).setDebugMessage(message).build()

    /**
     * Port Play thật (đường `connect` dựng `BillingClient` qua builder mock) trên `client` CONNECTED, sẵn sàng; giá
     * "coin" (INAPP) trong cache. `queryProductDetailsAsync` trả `queryResult` + một `ProductDetails` giả;
     * `launchBillingFlow` chạy `launch(listener)` và trả kết quả của nó. Trả listener giao dịch của thư viện.
     */
    private fun playPortReady(
        client: BillingClient,
        queryCode: Int,
        queryMessage: String,
        launch: (PurchasesUpdatedListener) -> BillingResult,
    ): PurchasesUpdatedListener {
        var listener: PurchasesUpdatedListener? = null
        val builder = mock(BillingClient.Builder::class.java, RETURNS_SELF)
        doAnswer { inv -> listener = inv.getArgument(0); builder }.`when`(builder).setListener(any(PurchasesUpdatedListener::class.java))
        `when`(builder.build()).thenReturn(client)
        `when`(client.connectionState).thenReturn(BillingClient.ConnectionState.CONNECTED)
        val details = mock(ProductDetails::class.java)
        val queryResult = mock(QueryProductDetailsResult::class.java)
        `when`(queryResult.productDetailsList).thenReturn(if (queryCode == BillingClient.BillingResponseCode.OK) listOf(details) else emptyList())
        doAnswer { inv ->
            inv.getArgument<ProductDetailsResponseListener>(1).onProductDetailsResponse(result(queryCode, queryMessage), queryResult)
            null
        }.`when`(client).queryProductDetailsAsync(any(QueryProductDetailsParams::class.java), any(ProductDetailsResponseListener::class.java))
        doAnswer { launch(checkNotNull(listener)) }
            .`when`(client).launchBillingFlow(any(Activity::class.java), any(BillingFlowParams::class.java))
        val context = mock(Context::class.java).also { `when`(it.applicationContext).thenReturn(it) }
        mockStatic(BillingClient::class.java).use { billing ->
            billing.`when`<BillingClient.Builder> { BillingClient.newBuilder(any()) }.thenReturn(builder)
            val port = PlayBillingPort(context)
            port.connect { }
            IapHelper.replacePort(port)
        }
        @Suppress("UNCHECKED_CAST")
        (IapHelper::class.java.declaredFields.first { it.name == "productCache" }.apply { isAccessible = true }
            .get(null) as MutableMap<String, IapProduct>)["coin"] = IapProduct("coin", IapProductType.INAPP, "1 $", 1_000_000L, "USD")
        return checkNotNull(listener)
    }

    /** Builder của `BillingFlowParams` giả: `ProductDetails` mock không qua được kiểm tra của builder thật. */
    private fun <T> withFlowParams(block: () -> T): T {
        val pdpBuilder = mock(BillingFlowParams.ProductDetailsParams.Builder::class.java, RETURNS_SELF)
        `when`(pdpBuilder.build()).thenReturn(mock(BillingFlowParams.ProductDetailsParams::class.java))
        val flowBuilder = mock(BillingFlowParams.Builder::class.java, RETURNS_SELF)
        `when`(flowBuilder.build()).thenReturn(mock(BillingFlowParams::class.java))
        return mockStatic(BillingFlowParams.ProductDetailsParams::class.java).use { pdp ->
            pdp.`when`<BillingFlowParams.ProductDetailsParams.Builder> { BillingFlowParams.ProductDetailsParams.newBuilder() }.thenReturn(pdpBuilder)
            mockStatic(BillingFlowParams::class.java).use { flow ->
                flow.`when`<BillingFlowParams.Builder> { BillingFlowParams.newBuilder() }.thenReturn(flowBuilder)
                block()
            }
        }
    }

    /** D20a (đỏ trên e1343f0: message "store trả mã 6"): lỗi store mang đúng `debugMessage` của Play như trước T4. */
    @Test fun `D20a loi store giu debugMessage cua Play trong IapResult Failed`() {
        val listener = playPortReady(mock(BillingClient::class.java), BillingClient.BillingResponseCode.ERROR, "Loi store gia") { result(0) }
        var fromPurchase: IapResult? = null
        val fromListener = mutableListOf<IapResult>()
        IapHelper.purchaseListener = { fromListener += it }
        try {
            IapHelper.purchase(mock(Activity::class.java), "coin") { fromPurchase = it }
            listener.onPurchasesUpdated(result(BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE, "Dich vu tam ngung"), null)
        } finally {
            IapHelper.purchaseListener = null
        }

        assertEquals(IapResult.Failed(BillingClient.BillingResponseCode.ERROR, "Loi store gia"), fromPurchase)
        assertEquals(listOf(IapResult.Failed(BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE, "Dich vu tam ngung")), fromListener)
    }

    /** D20b (đỏ trên e1343f0: callback không nhận gì): kết quả mua tới ngay trong `launchBillingFlow` vẫn về callback. */
    @Test fun `D20b ket qua mua toi dong bo trong launch van ve callback`() {
        playPortReady(mock(BillingClient::class.java), BillingClient.BillingResponseCode.OK, "") { l ->
            l.onPurchasesUpdated(result(BillingClient.BillingResponseCode.USER_CANCELED), null)
            result(BillingClient.BillingResponseCode.OK)
        }
        val got = mutableListOf<IapResult>()

        val launched = withFlowParams { IapHelper.purchase(mock(Activity::class.java), "coin") { got += it } }

        assertTrue(launched)
        assertEquals(listOf<IapResult>(IapResult.UserCanceled), got)
    }
}
