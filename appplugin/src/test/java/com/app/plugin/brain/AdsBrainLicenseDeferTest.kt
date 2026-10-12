package com.app.plugin.brain

import android.content.Context
import com.app.plugin.AppPluginInternalApi
import com.app.plugin.consent.ConsentState
import com.app.plugin.consent.ConsentTier
import com.app.plugin.license.Ed25519TestSigner
import com.app.plugin.license.LicenseGate
import com.app.plugin.logevent.EventNames
import com.app.plugin.logevent.ServerEventCapture
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 2.2.0 LIC (D30-220): cold start gọi `AdsBrain.onSessionStart()` TRƯỚC khi `LicenseGate` bắt đầu (AppPluginBase.kt:138/:142),
 * nên verdict lúc đó luôn PENDING. Tín hiệu không phải quyết định (`experiment_exposure`, `onFirstPurchase`) được GIỮ (≤ 8,
 * bỏ cũ nhất) tới verdict đầu: VALID → phát theo thứ tự, INVALID → bỏ. Verdict đi qua đường thật (`LicenseGate.start`, khoá
 * RFC 8032 TEST 1 của `Ed25519TestSigner`).
 */
@OptIn(AppPluginInternalApi::class)
class AdsBrainLicenseDeferTest {
    private val firsts = CopyOnWriteArrayList<String>()

    @Before fun setUp() {
        ConsentState.resetForTest()
        ConsentState.setTier(ConsentTier.GRANTED)
        BrainPolicy.clear()
        HoldoutManager.holdoutFraction = 0.0
        AdsBrain.enabled = true
        clearHeld()
        PlayerIdSource.resetForTest()
        LicenseGate.resetForTest()   // PENDING, gate chưa bắt đầu = đúng thứ tự cold start
        LicenseGate.keys = { Ed25519TestSigner.KEYS }
        LicenseGate.clock = { Ed25519TestSigner.NOW_MS }
        LicenseGate.certsProvider = { listOf(Ed25519TestSigner.CERT) }
        BrainSignalListener.installed = object : BrainSignalListener {
            override fun onFirstPurchase(playerId: String, usd: Double, isHoldout: Boolean) { firsts += playerId }
            override fun onSegmentChange(playerId: String, from: String, to: String, isHoldout: Boolean) = Unit
        }
    }

    @After fun tearDown() {
        clearHeld()
        PlayerIdSource.resetForTest()
        BrainSignalListener.installed = null
        LicenseGate.resetForTest()
        ConsentState.resetForTest()
        AdsBrain.enabled = false
        HoldoutManager.holdoutFraction = 0.05
    }

    /** Hàng đợi riêng của AdsBrain (private): rò từ lớp khác không được lẫn vào phép đếm. */
    private fun heldField() = AdsBrain::class.java.getDeclaredField("heldSignals").apply { isAccessible = true }

    private fun clearHeld() = runCatching { (heldField().get(AdsBrain) as MutableCollection<*>).clear() }

    private fun gate(token: String): LicenseGate.Verdict {
        val ctx = mock(Context::class.java).also { `when`(it.packageName).thenReturn(Ed25519TestSigner.PKG) }
        LicenseGate.start(ctx, token)
        return LicenseGate.await()
    }

    private fun exposures(server: ServerEventCapture, n: Int) =
        server.awaitRequests(EventNames.EXPERIMENT_EXPOSURE, n).map { it.getAsJsonObject("userProperty").get("user_id").asString }

    @Test fun `L25 cold start - phien truoc gate, roi VALID - dung mot exposure, phat sau VALID`() {
        ServerEventCapture().use { server ->
            val id = "p_defer_ok_${System.nanoTime()}"
            AdsBrain.onSessionStart(id)
            assertEquals("PENDING: chua phat", 0, exposures(server, 1).size)
            assertTrue(gate(Ed25519TestSigner.token()) is LicenseGate.Verdict.Valid)
            assertEquals("VALID: dung mot hang membership cua phien", listOf(id), exposures(server, 2))
        }
    }

    @Test fun `L26 cold start roi INVALID - 0 exposure, hang doi rong`() {
        ServerEventCapture().use { server ->
            AdsBrain.onSessionStart("p_defer_bad_${System.nanoTime()}")
            assertTrue(gate("ALP1.bad.bad") is LicenseGate.Verdict.Invalid)
            assertEquals(0, exposures(server, 1).size)
            assertEquals("INVALID: bo het", 0, (heldField().get(AdsBrain) as Collection<*>).size)
        }
    }

    @Test fun `L27 mua dau doi luc PENDING - onFirstPurchase sau VALID, dem mua khong doi`() {
        val id = "p_defer_buy_${System.nanoTime()}"
        AdsBrain.onSessionStart(id)
        AdsBrain.onPurchase(1.99)
        assertEquals("PENDING: chua phat", 0, firsts.count { it == id })
        assertTrue(gate(Ed25519TestSigner.token()) is LicenseGate.Verdict.Valid)
        val deadline = System.currentTimeMillis() + 5_000L
        while (firsts.none { it == id } && System.currentTimeMillis() < deadline) Thread.sleep(5)
        assertEquals("VALID: dung mot onFirstPurchase", 1, firsts.count { it == id })
        assertEquals("dem mua khong doi", 1, AdsBrain.currentState().purchaseCountLifetime)
    }

    @Test fun `L28 PENDING mai - khong phat, giu toi da 8 (bo cu nhat), VALID phat dung 8`() {
        ServerEventCapture().use { server ->
            server.autoAck = true   // loạt 8 > 4 request đang bay (`LogEventQueue.maxInFlight`): Call giả trả 200 ngay
            val id = "p_defer_many_${System.nanoTime()}"
            repeat(10) { AdsBrain.onSessionStart(id) }   // 10 phiên của cùng người (resume) khi verdict chưa có
            assertEquals("PENDING: 0 phat", 0, exposures(server, 1).size)
            assertEquals("tran 8", 8, (heldField().get(AdsBrain) as Collection<*>).size)
            assertTrue(gate(Ed25519TestSigner.token()) is LicenseGate.Verdict.Valid)
            assertEquals("VALID: dung 8 hang (2 cu nhat da bo)", List(8) { id }, exposures(server, 9))
        }
    }

    @Test fun `L29 cold start id SDK roi configure id app luc PENDING - VALID chi phat hang cua id app (user_id khop bucket)`() {
        ServerEventCapture().use { server ->
            AdsBrain.onSessionStart("p_defer_sdk_${System.nanoTime()}")   // cold start: id do SDK quản
            val app = "p_defer_app_${System.nanoTime()}"
            AdsBrain.onSessionStart(app)                                  // `configure { playerId }` trước verdict
            assertTrue(gate(Ed25519TestSigner.token()) is LicenseGate.Verdict.Valid)
            val rows = server.awaitRequests(EventNames.EXPERIMENT_EXPOSURE, 2)
            assertEquals(listOf(app), rows.map { it.getAsJsonObject("userProperty").get("user_id").asString })
            assertEquals(HoldoutManager.bucket(app), rows.single().getAsJsonObject("eventData").get("holdout_bucket").asInt)
        }
    }

    @Test fun `L30 tran 8 bo CU NHAT, VALID phat theo dung thu tu giu`() {
        val ids = (1..10).map { "p_defer_ord_${it}_${System.nanoTime()}" }
        ids.forEach { AdsBrain.onSessionStart(it); AdsBrain.onPurchase(0.99) }   // 20 tín hiệu: exposure_i, purchase_i xen kẽ
        assertTrue(gate(Ed25519TestSigner.token()) is LicenseGate.Verdict.Valid)
        val deadline = System.currentTimeMillis() + 5_000L
        while (firsts.size < 4 && System.currentTimeMillis() < deadline) Thread.sleep(5)
        // 8 giữ lại = exposure/purchase của người 7..10; listener đồng bộ → thứ tự gọi = thứ tự giữ.
        assertEquals(ids.takeLast(4), firsts.filter { it.startsWith("p_defer_ord_") })
    }
}
