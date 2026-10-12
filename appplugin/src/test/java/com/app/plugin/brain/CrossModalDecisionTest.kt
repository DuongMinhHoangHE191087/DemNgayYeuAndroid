package com.app.plugin.brain

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Quyết định cross-modal thật sự — cùng MỘT điểm quyết định, brain chọn giữa
 * NO_ACTION / REWARDED_AD / IAP_OFFER theo propensity trả tiền (doc 23 mục E.3,
 * Phase 4: "đây là điểm khác mọi vendor").
 *
 * ═══ TÍNH CHẤT TRUNG TÂM ═══
 *
 * 1. Tắt brain hoặc ngoài rollout → trả baseline của game, không ép về NO_ACTION.
 * 2. Holdout nhận baseline với xác suất 1.0 và được đánh cờ.
 * 3. Arm bị game cấm (allow*=false) không bao giờ xuất hiện trong kết quả.
 * 4. Guardrail đạo đức vẫn là filter CỨNG trong không gian đa arm: bế tắc không chào
 *    bán, circuit breaker phiên không chào bán.
 * 5. Feedback qua đúng đường ống treo: onPurchase/onRewardedOffered chốt đúng quyết định.
 */
class CrossModalDecisionTest {
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

    // ═══ Tắt = hành vi cũ ═══

    @Test
    fun `tat brain tra baseline mac dinh`() {
        AdsBrain.enabled = false
        AdsBrain.onSessionStart("x_off_${System.nanoTime()}")
        val d = AdsBrain.chooseCrossModal(DecisionPoint.LEVEL_FAIL)
        assertEquals(ActionMode.NO_ACTION, d.action)
        assertFalse(d.isHoldout)
    }

    @Test
    fun `tat brain tra baseline khac NO_ACTION cua game`() {
        // Game có hành vi mặc định riêng (ví dụ luôn mời booster khi thua) — tắt brain
        // phải giữ đúng hành vi đó, không được ép về NO_ACTION.
        AdsBrain.enabled = false
        AdsBrain.onSessionStart("x_off2_${System.nanoTime()}")
        val d = AdsBrain.chooseCrossModal(DecisionPoint.LEVEL_FAIL, baseline = ActionMode.REWARDED_AD)
        assertEquals(ActionMode.REWARDED_AD, d.action)
    }

    @Test
    fun `ngoai rollout tra baseline cua game`() {
        AdsBrain.enabled = true
        AdsBrain.rolloutFraction = 0.0
        AdsBrain.onSessionStart("x_noroll_${System.nanoTime()}")
        val d = AdsBrain.chooseCrossModal(DecisionPoint.CURRENCY_LOW, baseline = ActionMode.IAP_OFFER)
        assertEquals(ActionMode.IAP_OFFER, d.action)
        assertFalse("ngoai rollout khong phai holdout", d.isHoldout)
    }

    @Test
    fun `ca hai arm bi cam thi tra baseline khong hoi engine`() {
        // Không có quyết định nào để ra — không ghi quyết định một arm vào log OPE.
        AdsBrain.enabled = true
        AdsBrain.onSessionStart("x_noarm_${System.nanoTime()}")
        val d = AdsBrain.chooseCrossModal(
            DecisionPoint.LEVEL_FAIL,
            allowRewarded = false, allowIapOffer = false,
            baseline = ActionMode.REWARDED_AD
        )
        assertEquals(ActionMode.REWARDED_AD, d.action)
        assertFalse(d.isHoldout)
    }

    // ═══ Holdout ═══

    @Test
    fun `holdout nhan baseline voi xac suat 1 va duoc danh co`() {
        AdsBrain.enabled = true
        HoldoutManager.holdoutFraction = 1.0
        AdsBrain.onSessionStart("x_hold_${System.nanoTime()}")
        repeat(10) {
            val d = AdsBrain.chooseCrossModal(DecisionPoint.LEVEL_FAIL, baseline = ActionMode.IAP_OFFER)
            assertEquals("holdout phai nhan baseline", ActionMode.IAP_OFFER, d.action)
            assertTrue(d.isHoldout)
        }
    }

    // ═══ Tập arm ═══

    @Test
    fun `arm bi cam khong bao gio xuat hien trong ket qua`() {
        AdsBrain.enabled = true
        AdsBrain.onSessionStart("x_subset_${System.nanoTime()}")
        val seen = mutableSetOf<ActionMode>()
        repeat(60) {
            seen += AdsBrain.chooseCrossModal(DecisionPoint.LEVEL_FAIL, allowRewarded = false).action
        }
        assertTrue(
            "chi duoc chua NO_ACTION hoac IAP_OFFER: $seen",
            seen.all { it == ActionMode.NO_ACTION || it == ActionMode.IAP_OFFER }
        )
    }

    @Test
    fun `dang be tac thi khong chon IAP_OFFER trong khong gian da arm`() {
        // ETH_FRUSTRATION_EXPLOIT là filter cứng với cổng nhị phân — vẫn phải cứng khi
        // gộp arm: không được kiếm tiền trên sự bế tắc dù arm kia là rewarded.
        AdsBrain.enabled = true
        AdsBrain.onSessionStart("x_frust_${System.nanoTime()}")
        repeat(6) { AdsBrain.onLevelFail() }
        val seen = mutableSetOf<ActionMode>()
        repeat(40) { seen += AdsBrain.chooseCrossModal(DecisionPoint.LEVEL_FAIL).action }
        assertFalse("be tac khong duoc chao mua: $seen", ActionMode.IAP_OFFER in seen)
    }

    @Test
    fun `circuit breaker phien chan IAP_OFFER ca trong quyet dinh gop`() {
        AdsBrain.enabled = true
        AdsBrain.onSessionStart("x_breaker_${System.nanoTime()}")
        // Circuit breaker mặc định ngắt ở 3 lượt mua trong một phiên (doc 29 mục 5.3).
        repeat(3) { AdsBrain.onPurchase(1.99) }
        val seen = mutableSetOf<ActionMode>()
        repeat(40) { seen += AdsBrain.chooseCrossModal(DecisionPoint.SHOP_OPEN).action }
        assertFalse("3 luot mua trong phien phai ngat chao ban: $seen", ActionMode.IAP_OFFER in seen)
    }

    // ═══ Feedback qua đường ống treo ═══

    @Test
    fun `chon IAP_OFFER roi mua thi ghi cong cho dung quyet dinh do`() {
        AdsBrain.enabled = true
        AdsBrain.onSessionStart("x_credit_${System.nanoTime()}")
        var offered = false
        repeat(80) {
            if (!offered && AdsBrain.chooseCrossModal(DecisionPoint.CURRENCY_LOW).action == ActionMode.IAP_OFFER) {
                offered = true
                AdsBrain.onIapOfferShown()
            }
        }
        assertTrue("80 luot phai co it nhat mot IAP_OFFER", offered)
        // Không được ném lỗi và phải chốt được quyết định treo.
        AdsBrain.onPurchase(9.99)
        assertEquals(1, AdsBrain.currentState().purchaseCountLifetime)
    }

    @Test
    fun `chon REWARDED_AD roi nguoi choi xem thi feedback chot`() {
        AdsBrain.enabled = true
        AdsBrain.onSessionStart("x_reward_${System.nanoTime()}")
        // ETH_MANUFACTURED_FAIL (doc 14): rewarded chỉ được mời sau fail THẬT — phải có
        // ít nhất một lần thua thì arm REWARDED_AD mới qua được guardrail.
        AdsBrain.onLevelFail()
        var picked = false
        repeat(80) {
            if (!picked && AdsBrain.chooseCrossModal(DecisionPoint.CURRENCY_LOW).action == ActionMode.REWARDED_AD) {
                picked = true
            } else {
                // onLevelFail() reset chuỗi thua về 0 mỗi lần arm khác được chọn và
                // resolvePending chạy tiếp — giữ chuỗi thua thật để arm tiếp tục hợp lệ.
                AdsBrain.onLevelFail()
            }
        }
        assertTrue("80 luot phai co it nhat mot REWARDED_AD", picked)
        // Không được ném lỗi — quyết định treo mang mode REWARDED_AD chốt qua đây.
        AdsBrain.onRewardedOffered(true)
    }

    @Test
    fun `diem quyet dinh khac nhau duoc hoi rieng`() {
        // SHOP_OPEN và CURRENCY_LOW khác nhau về mặt đạo đức — cả hai đều phải hoạt động
        // qua cùng một API mà không vỡ guardrail nào.
        AdsBrain.enabled = true
        AdsBrain.onSessionStart("x_points_${System.nanoTime()}")
        repeat(5) {
            AdsBrain.chooseCrossModal(DecisionPoint.SHOP_OPEN)
            AdsBrain.chooseCrossModal(DecisionPoint.CURRENCY_LOW)
            AdsBrain.chooseCrossModal(DecisionPoint.RETURN_AFTER_INACTIVITY)
        }
    }
}
