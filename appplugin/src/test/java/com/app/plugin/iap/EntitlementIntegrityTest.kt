package com.app.plugin.iap

import android.util.Log
import com.app.plugin.ads.RemoveAdsMask
import com.app.plugin.consent.ConsentState
import com.app.plugin.logevent.TelemetryConsentGate
import com.app.plugin.logevent.inMemoryPrefs
import com.app.plugin.until.LogUtil
import com.app.plugin.until.PreferenceHelper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.contains
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.times
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.crypto.spec.SecretKeySpec

/**
 * 1.8.0 Task 6 — toàn vẹn quyền lợi (D10, #31). H6 là test nguồn đỏ trên mã cũ; H1–H5, H7 là đặc tả (lớp mới), cặp với H6.
 * JVM không có AndroidKeyStore: H1/H2/H3/H5/H7 dùng [MacKey] giả tiêm qua `EntitlementIntegrity.key`; H4 chứng minh fallback.
 * Không có main looper nên `BillingThread.offMain` chạy ký/kiểm ngay trên luồng gọi; H5 thay nó bằng luồng billing thật.
 */
class EntitlementIntegrityTest {
    private val store: MutableMap<String, Any?> = java.util.Collections.synchronizedMap(mutableMapOf())
    private val helper = PreferenceHelper.getInstance()
    private var previousPrefs: android.content.SharedPreferences? = null
    private val previousOffMain = BillingThread.offMain

    /** Khoá HMAC cố định thay khoá Keystore của một "máy". */
    private class TestKey(seed: String) : MacKey {
        private val key = SecretKeySpec(seed.toByteArray(), EntitlementIntegrity.ALGO)
        override fun mac(data: ByteArray): ByteArray = EntitlementIntegrity.hmac(key, data)
    }

    private val keyA = TestKey("may-A-khoa-keystore")
    private val keyB = TestKey("may-B-khoa-keystore")

    private fun setBillingExecutor(e: java.util.concurrent.Executor) =
        IapHelper::class.java.getDeclaredField("billingExecutor").apply { isAccessible = true }.set(null, e)

    private fun record() = store["iap_entitlements_v1"] as String

    private fun portOwning(subs: List<PortPurchase> = emptyList()) = FakeBillingPort().also {
        it.owned[IapProductType.SUBS] = subs
        it.owned[IapProductType.INAPP] = emptyList()
        IapHelper.replacePort(it)
    }

    @Before fun setUp() {
        previousPrefs = helper.preferences
        helper.preferences = inMemoryPrefs(store)
        ConsentState.resetForTest()
        TelemetryConsentGate.resetForTest()
        Entitlements.clearAll()
        SubscriptionLifecycle.clear()
        IapHelper.setProducts()
        PurchaseLedger.resetForTest()
        useDirectBillingExecutor()
    }

    @After fun tearDown() {
        IapHelper.replacePort(null)
        BillingThread.offMain = previousOffMain
        setBillingExecutor(BillingThread.executor)
        EntitlementIntegrity.key = null
        PurchaseLedger.resetForTest()
        TelemetryConsentGate.resetForTest()
        ConsentState.resetForTest()
        helper.preferences = null
        Entitlements.clearAll()
        helper.preferences = previousPrefs
        SubscriptionLifecycle.clear()
    }

    @Test fun `H1 ghi co MAC, doc lai Verified`() {
        EntitlementIntegrity.key = keyA
        Entitlements.setRemoveAdsMasks(mapOf("lite" to RemoveAdsMask.INTERRUPTIVE))
        Entitlements.grant("pro")
        Entitlements.grant("lite")
        assertTrue(record(), Regex("""v1\|lite,pro\|lite:${RemoveAdsMask.INTERRUPTIVE}\|[0-9a-f]{64}""").matches(record()))

        store["entitlements_remove_ads_masks"] = "lite:${RemoveAdsMask.ALL}" // khoá mask chưa ký bị sửa
        mockStatic(Log::class.java).use { log ->
            Entitlements.reloadForTest()
            log.verify { Log.e(eq(LogUtil.TAG), contains("result=Verified")) }
        }
        assertEquals(setOf("lite", "pro"), Entitlements.activeProductIds())
        assertEquals("mask lay tu ban co chu ky", RemoveAdsMask.INTERRUPTIVE, Entitlements.removeAdsMask())
    }

    @Test fun `H2 sua prefs tay Invalid thi rong va log, khong revoke tren Play`() {
        EntitlementIntegrity.key = keyA
        Entitlements.setRemoveAdsProducts(setOf("pro"))
        Entitlements.grant("pro")
        store["iap_entitlements_v1"] = record().replace("v1|pro|", "v1|pro,vip|")
        val port = portOwning()

        mockStatic(Log::class.java).use { log ->
            Entitlements.reloadForTest()
            log.verify { Log.e(eq(LogUtil.TAG), contains("result=Invalid")) }
        }
        assertEquals(emptySet<String>(), Entitlements.activeProductIds())
        assertFalse(Entitlements.hasRemoveAds())
        assertEquals("chi hoi lai Play, khong consume/ack", listOf("queryOwned:INAPP", "queryOwned:SUBS"), port.calls)
    }

    @Test fun `H3 ban cu khong ky Legacy 1 lan roi ky lai`() {
        EntitlementIntegrity.key = keyA
        store["iap_active_products"] = "pro|lite"
        mockStatic(Log::class.java).use { log ->
            Entitlements.reloadForTest()
            assertEquals("nguoi dang tra tien khong mat quyen loi khi nang cap", setOf("pro", "lite"), Entitlements.activeProductIds())
            Entitlements.reloadForTest()
            log.verify({ Log.e(eq(LogUtil.TAG), contains("result=Legacy")) }, times(1))
            log.verify({ Log.e(eq(LogUtil.TAG), contains("result=Verified")) }, times(1))
        }
        assertFalse("khoa cu chua ky da xoa", store.containsKey("iap_active_products"))
        assertTrue(record(), record().startsWith("v1|lite,pro|"))
        assertEquals(setOf("pro", "lite"), Entitlements.activeProductIds())
    }

    @Test fun `H4 Keystore nem loi thi ProcessRandomMacKey va log`() {
        mockStatic(Log::class.java).use { log ->
            val k = EntitlementIntegrity.selectKey { throw java.security.KeyStoreException("no AndroidKeyStore") }
            assertTrue("fallback: $k", k is ProcessRandomMacKey)
            // JVM không có provider AndroidKeyStore: đường thật (KeystoreMacKey) cũng rơi về fallback.
            assertTrue(EntitlementIntegrity.selectKey { KeystoreMacKey() } is ProcessRandomMacKey)
            log.verify({ Log.e(eq(LogUtil.TAG), contains("ProcessRandomMacKey")) }, times(2))
        }
        EntitlementIntegrity.key = ProcessRandomMacKey()
        val rec = EntitlementIntegrity.sign(setOf("pro"), emptyMap())!!
        assertTrue("cung process: Verified", EntitlementIntegrity.check(rec, emptySet()) is StoredEntitlements.Verified)
        EntitlementIntegrity.key = ProcessRandomMacKey() // process sau: khoá khác
        assertSame(StoredEntitlements.Invalid, EntitlementIntegrity.check(rec, emptySet()))
    }

    /**
     * #31: ký + ghi chạy trên "luồng billing" (executor riêng thay `BillingThread.offMain`) sau khi `grant` nhả monitor; lượt ký
     * treo trên latch mà monitor `Entitlements` vẫn rảnh — một lời gọi `@Synchronized` khác (main) xong ngay.
     */
    @Test fun `H5 sign va persist khong chay trong monitor Entitlements`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        EntitlementIntegrity.key = object : MacKey {
            override fun mac(data: ByteArray): ByteArray {
                entered.countDown()
                release.await(5, TimeUnit.SECONDS)
                return keyA.mac(data)
            }
        }
        val billing = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "appplugin-billing").apply { isDaemon = true } }
        BillingThread.offMain = billing
        val writer = Thread { Entitlements.grant("pro") }.apply { start() }
        try {
            assertTrue("luot ky da bat dau", entered.await(5, TimeUnit.SECONDS))
            val main = Thread { Entitlements.setRemoveAdsProducts(setOf("pro")) }.apply { start(); join(2_000) }
            assertFalse("monitor Entitlements bi giu trong luc ky", main.isAlive)
            assertTrue("bo nho cap truoc khi ky", Entitlements.isActive("pro"))
            assertEquals(RemoveAdsMask.ALL, Entitlements.removeAdsMask())
        } finally {
            release.countDown()
            writer.join(5_000)
            billing.shutdown()
            billing.awaitTermination(5, TimeUnit.SECONDS)
        }
        assertTrue(record(), record().startsWith("v1|pro|"))
    }

    @Test fun `H7 backup sang may khac Invalid roi restore Play cap lai`() {
        EntitlementIntegrity.key = keyA
        Entitlements.grant("pro_sub")
        val backup = HashMap(store)
        store.clear()
        store.putAll(backup) // máy B: prefs khôi phục từ backup, khoá Keystore thì không theo
        EntitlementIntegrity.key = keyB
        val port = portOwning(listOf(PortPurchase(listOf("pro_sub"), "GPA.b", "tokB", 1L, IapPurchaseState.PURCHASED, true, true, 1, false)))

        mockStatic(Log::class.java).use { log ->
            Entitlements.reloadForTest()
            assertEquals("Play cap lai dung tap dang so huu", setOf("pro_sub"), Entitlements.activeProductIds())
            Entitlements.reloadForTest()
            log.verify({ Log.e(eq(LogUtil.TAG), contains("result=Invalid")) }, times(1))
            log.verify({ Log.e(eq(LogUtil.TAG), contains("result=Verified")) }, times(1))
        }
        assertEquals(listOf("queryOwned:INAPP", "queryOwned:SUBS"), port.calls)
        assertEquals(setOf("pro_sub"), Entitlements.activeProductIds())
    }

    /**
     * R-6 I1: cold start — trạng thái object mới, lượt kiểm còn xếp hàng trên luồng billing — một `grant` tới trước (vd
     * `PurchaseRouter` trên main) không được làm mất tập đã ký: nó được phát lại lên tập đã kiểm, bản ghi mới giữ cả hai.
     */
    @Test fun `H8 grant truoc khi verify xong khong mat quyen loi da ky`() {
        EntitlementIntegrity.key = keyA
        Entitlements.setRemoveAdsProducts(setOf("remove_ads_sub"))
        Entitlements.grant("remove_ads_sub") // process 1: đã ký xuống đĩa
        val fresh = mapOf("active" to emptySet<String>(), "dirty" to false, "verified" to false, "masksSet" to false, "pending" to null)
        for ((name, value) in fresh) Entitlements::class.java.declaredFields.firstOrNull { it.name == name }
            ?.apply { isAccessible = true }?.set(Entitlements, value) // process 2: chưa kiểm
        Entitlements.grant("gems_pack")
        Entitlements::class.java.getDeclaredMethod("verifyStored").apply { isAccessible = true }.invoke(Entitlements)
        assertEquals(setOf("remove_ads_sub", "gems_pack"), Entitlements.activeProductIds())
        assertTrue("nguoi dang tra tien van tat quang cao", Entitlements.hasRemoveAds())
        assertTrue(record(), record().startsWith("v1|gems_pack,remove_ads_sub|"))
    }

    /** R-6 m2: phần masks nằm trong MAC — sửa `lite:INTERRUPTIVE` thành ALL trong bản ghi → Invalid, rỗng, hỏi lại Play. */
    @Test fun `H9 sua masks trong prefs Invalid`() {
        EntitlementIntegrity.key = keyA
        Entitlements.setRemoveAdsMasks(mapOf("lite" to RemoveAdsMask.INTERRUPTIVE))
        Entitlements.grant("lite")
        store["iap_entitlements_v1"] = record().replace("|lite:${RemoveAdsMask.INTERRUPTIVE}|", "|lite:${RemoveAdsMask.ALL}|")
        val port = portOwning()
        mockStatic(Log::class.java).use { log ->
            Entitlements.reloadForTest()
            log.verify { Log.e(eq(LogUtil.TAG), contains("result=Invalid")) }
        }
        assertEquals(emptySet<String>(), Entitlements.activeProductIds())
        assertEquals(RemoveAdsMask.NONE, Entitlements.removeAdsMask())
        assertEquals(listOf("queryOwned:INAPP", "queryOwned:SUBS"), port.calls)
    }

    /** Nguồn DRY `keep/DryRun180RedTest.kt`: `active` sửa tại chỗ trong khi `isActive()/activeProductIds()` đọc không khoá. */
    @Test fun `H6 field active kieu Set bat bien`() {
        val src = java.io.File("src/main/java/com/app/plugin/iap/Entitlements.kt").readText()
        assertFalse("Entitlements co MutableSet", Regex("""active\w*\s*[:=][^\n]*Mutable|mutableSetOf""").containsMatchIn(src))
    }
}
