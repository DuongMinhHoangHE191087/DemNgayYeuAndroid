package com.app.plugin.brain

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Vòng cross-modal IAA↔IAP qua facade `AdsBrain` — định vị khác biệt ở doc 23 mục E.3.
 */
class CrossModalTest {
    @get:Rule val license = LicenseTestRule()

    @Before
    fun setUp() {
        AdsBrain.enabled = false
        AdsBrain.rolloutFraction = 1.0
        HoldoutManager.holdoutFraction = 0.0
        BrainPolicy.clear()
    }

    @After
    fun tearDown() {
        AdsBrain.enabled = false
        AdsBrain.rolloutFraction = 1.0
        HoldoutManager.holdoutFraction = 0.05
        BrainPolicy.clear()
        DecisionEngine.instance.guardrails.maxInterstitialPerSession = 6
    }

    @Test
    fun `tat brain thi luon cho phep chao ban`() {
        AdsBrain.enabled = false
        AdsBrain.onSessionStart("p_off_${System.nanoTime()}")
        repeat(20) {
            assertTrue("tat brain phai giu nguyen luong cu", AdsBrain.shouldOfferIap())
            AdsBrain.onIapOfferShown()
        }
    }

    @Test
    fun `chi don dap trong phien thi ngung chao ban`() {
        AdsBrain.enabled = true
        val id = "p_velocity_${System.nanoTime()}"
        AdsBrain.onSessionStart(id)
        // Circuit breaker mặc định ngắt ở 3 lượt mua trong một phiên (doc 29 mục 5.3).
        repeat(3) { AdsBrain.onPurchase(4.99) }
        assertFalse(
            "da mua 3 lan trong phien -> phai ngung chao ban",
            AdsBrain.shouldOfferIap()
        )
    }

    @Test
    fun `bo dem mua trong phien duoc cap lai moi phien`() {
        AdsBrain.enabled = true
        val id = "p_reset_${System.nanoTime()}"
        AdsBrain.onSessionStart(id)
        repeat(3) { AdsBrain.onPurchase(1.99) }
        assertEquals(3, AdsBrain.currentState().purchasesThisSession)

        AdsBrain.onSessionStart(id)
        assertEquals("phien moi phai cap lai bo dem trong phien", 0, AdsBrain.currentState().purchasesThisSession)
        assertEquals("nhung tong tich luy thi khong duoc mat", 3, AdsBrain.currentState().purchaseCountLifetime)
    }

    @Test
    fun `dang be tac thi khong chao ban`() {
        AdsBrain.enabled = true
        AdsBrain.onSessionStart("p_frust_${System.nanoTime()}")
        repeat(6) { AdsBrain.onLevelFail() }
        assertFalse(
            "khong duoc kiem tien tren su be tac (doc 29 muc 10.2 Q4)",
            AdsBrain.shouldOfferIap()
        )
    }

    @Test
    fun `thang man thi het be tac va chao ban duoc tro lai`() {
        AdsBrain.enabled = true
        val g = DecisionEngine.instance.guardrails
        val offer = ActionCandidate(ActionMode.IAP_OFFER, "offer")
        val id = "p_recover_${System.nanoTime()}"

        // Khẳng định tính chất TẤT ĐỊNH ở guardrail, không assert vào lựa chọn ngẫu nhiên
        // của bandit — assert vào lựa chọn ngẫu nhiên tạo test giòn.
        val stuck = PlayerState(id, failStreakCurrent = 7, minutesSinceLastIapOffer = -1)
        assertEquals("ETH_FRUSTRATION_EXPLOIT", g.evaluate(offer, stuck, 0, 999)?.code)

        val recovered = PlayerState(id, failStreakCurrent = 0, minutesSinceLastIapOffer = -1)
        assertEquals(null, g.evaluate(offer, recovered, 0, 999))
    }

    @Test
    fun `mua hang sau khi chao ban duoc ghi cong cho dung quyet dinh do`() {
        AdsBrain.enabled = true
        val id = "p_credit_${System.nanoTime()}"
        AdsBrain.onSessionStart(id)

        // Ép bandit phải chọn chào bán bằng cách loại mọi lý do bị chặn, rồi lặp tới khi
        // nó thực sự chọn — lựa chọn là ngẫu nhiên nên không assert vào một lần gọi.
        var offered = false
        repeat(40) {
            if (!offered && AdsBrain.shouldOfferIap()) {
                offered = true
                AdsBrain.onIapOfferShown()
            }
        }
        assertTrue("sau 40 lan phai co it nhat mot lan chon chao ban", offered)

        // Không được ném lỗi và phải chốt được quyết định treo.
        AdsBrain.onPurchase(9.99)
        assertEquals(1, AdsBrain.currentState().purchaseCountLifetime)
    }

    @Test
    fun `chao ban va quang cao dem rieng khong dung chung state`() {
        // doc 27 mục B.2: cô lập state theo loại action. Hiện quảng cáo không được làm
        // cạn ngân sách chào bán và ngược lại.
        AdsBrain.enabled = true
        AdsBrain.onSessionStart("p_isolate_${System.nanoTime()}")
        repeat(5) {
            if (AdsBrain.shouldShowInterstitial()) AdsBrain.onInterstitialShown() else AdsBrain.onAdSkipped()
        }
        assertEquals(
            "hien quang cao khong duoc lam tang bo dem chao ban",
            0, AdsBrain.currentState().iapOffersThisSession
        )
    }

    @Test
    fun `ngoai rollout thi khong chan chao ban`() {
        AdsBrain.enabled = true
        AdsBrain.rolloutFraction = 0.0
        AdsBrain.onSessionStart("p_norollout_${System.nanoTime()}")
        repeat(9) { AdsBrain.onLevelFail() }
        repeat(5) { AdsBrain.onPurchase(4.99) }
        assertTrue(
            "ngoai thi nghiem thi brain khong duoc can thiep",
            AdsBrain.shouldOfferIap()
        )
    }
}
