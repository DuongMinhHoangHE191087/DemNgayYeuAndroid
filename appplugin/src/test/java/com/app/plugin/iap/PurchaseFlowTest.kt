package com.app.plugin.iap

import android.app.Activity
import com.app.plugin.SdkKillSwitch
import com.app.plugin.logevent.inMemoryPrefs
import com.app.plugin.until.PreferenceHelper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 1.9.0 Task 11 (R-2 m1): một kết quả purchases-updated chỉ tiêu callback của luồng mua đang mở ([PurchaseFlow]) khi nó
 * thuộc sản phẩm của luồng. Luồng billing = executor trực tiếp, port giả [FakeBillingPort], "đĩa" = prefs trong bộ nhớ.
 */
class PurchaseFlowTest {
    private val store: MutableMap<String, Any?> = java.util.Collections.synchronizedMap(mutableMapOf())
    private val helper = PreferenceHelper.getInstance()
    private var previousPrefs: android.content.SharedPreferences? = null
    private val fromFlow = CopyOnWriteArrayList<IapResult>()
    private val fromListener = CopyOnWriteArrayList<IapResult>()

    private fun offer(pid: String) = PortOffer(pid, IapProductType.INAPP, 1_000_000L, "USD", "1 US$", pid, "", emptyList())

    private fun purchased(pid: String, token: String) =
        PortPurchase(listOf(pid), "GPA.$token", token, 1_700_000_000_000L, IapPurchaseState.PURCHASED, true, false, 1, false)

    private fun productsOf(results: List<IapResult>) = results.map { (it as IapResult.Success).purchase.productId }

    @Before fun setUp() {
        SdkKillSwitch.current = SdkKillSwitch.NONE
        previousPrefs = helper.preferences
        helper.preferences = inMemoryPrefs(store)
        Entitlements.clearAll()
        SubscriptionLifecycle.clear()
        PurchaseLedger.resetForTest()
        PurchaseFlow.resetForTest()
        useDirectBillingExecutor()
        val port = FakeBillingPort()
        port.offers[IapProductType.INAPP] = listOf(offer("coin"), offer("gem"))
        IapHelper.replacePort(port)
        IapHelper.setProducts(inapp = listOf("coin", "gem"))
        IapHelper.queryProducts()
        IapHelper.purchaseListener = { fromListener += it }
    }

    @After fun tearDown() {
        IapHelper.purchaseListener = null
        IapHelper.replacePort(null)
        IapHelper.setProducts()
        @Suppress("UNCHECKED_CAST")
        (IapHelper::class.java.declaredFields.first { it.name == "productCache" }.apply { isAccessible = true }
            .get(null) as MutableMap<String, IapProduct>).clear()
        PurchaseFlow.resetForTest()
        PurchaseLedger.resetForTest()
        Entitlements.clearAll()
        SubscriptionLifecycle.clear()
        helper.preferences = previousPrefs
    }

    /** Đỏ trên mã cũ: giao dịch `gem` (PENDING hoàn tất ngoài luồng) tiêu callback của luồng `coin`. */
    @Test fun `F1 purchases-updated cua san pham khac khong tieu callback cua luong dang mo`() {
        assertTrue(IapHelper.purchase(mock(Activity::class.java), "coin") { fromFlow += it })
        IapHelper.onPortPurchasesUpdated(BillingPort.OK, listOf(purchased("gem", "tokG")), "")
        assertEquals("callback của luồng coin nhận giao dịch khác", emptyList<String>(), productsOf(fromFlow))
        assertEquals("listener vẫn nhận gem", listOf("gem"), productsOf(fromListener))

        IapHelper.onPortPurchasesUpdated(BillingPort.OK, listOf(purchased("coin", "tokC")), "")
        assertEquals("kết quả của luồng về callback", listOf("coin"), productsOf(fromFlow))
    }

    /** Đặc tả (xanh trên mã cũ): cùng sản phẩm thì tiêu callback và đóng luồng; luồng kế mở được. */
    @Test fun `F2 purchases-updated cung san pham tieu callback va dong luong`() {
        assertTrue(IapHelper.purchase(mock(Activity::class.java), "coin") { fromFlow += it })
        IapHelper.onPortPurchasesUpdated(BillingPort.OK, listOf(purchased("coin", "tokC")), "")
        assertEquals(listOf("coin"), productsOf(fromFlow))
        val next = mutableListOf<IapResult>()
        assertTrue("luồng đã đóng: purchase kế mở được", IapHelper.purchase(mock(Activity::class.java), "gem") { next += it })
        assertEquals("không FLOW_IN_PROGRESS", emptyList<IapResult>(), next)
    }

    /** R-T11 I3: một update có cả giao dịch ngoài luồng (gem) lẫn của luồng (coin): callback chỉ nhận coin, listener nhận cả hai. */
    @Test fun `F9 mot update co ca san pham luong va san pham khac callback chi nhan san pham cua luong`() {
        assertTrue(IapHelper.purchase(mock(Activity::class.java), "coin") { fromFlow += it })
        IapHelper.onPortPurchasesUpdated(BillingPort.OK, listOf(purchased("gem", "tokG"), purchased("coin", "tokC")), "")
        assertEquals("callback chỉ nhận giao dịch của luồng", listOf("coin"), productsOf(fromFlow))
        assertEquals("listener nhận cả hai", listOf("gem", "coin"), productsOf(fromListener))
    }
}
