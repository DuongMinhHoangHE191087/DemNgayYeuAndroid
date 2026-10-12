package com.app.plugin.brain

import com.app.plugin.consent.ConsentState
import com.app.plugin.consent.ConsentTier
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class AdsBrainTest {
    @get:Rule val license = LicenseTestRule()

    @Before
    fun setUp() {
        AdsBrain.enabled = false
        HoldoutManager.holdoutFraction = 0.0
        BrainPolicy.clear()
        // #7 (DOC/66): UNKNOWN không còn được profiling/cá nhân hoá; test logic brain như người đã đồng ý.
        ConsentState.resetForTest()
        ConsentState.setTier(ConsentTier.GRANTED)
    }

    @After
    fun tearDown() {
        ConsentState.resetForTest()
        AdsBrain.enabled = false
        HoldoutManager.holdoutFraction = 0.05
        // DecisionEngine.instance là singleton dùng chung giữa các test — trả ngưỡng
        // về mặc định, nếu không thì test sau chạy với cấu hình của test trước.
        DecisionEngine.instance.guardrails.maxInterstitialPerSession = 6
    }

    // ═══ Tính chất quan trọng nhất: tắt = không đổi hành vi ═══

    @Test
    fun `mac dinh phai TAT`() {
        assertFalse(
            "bat brain la quyet dinh kinh doanh, khong duoc la he qua phu cua viec cap nhat SDK",
            AdsBrain.enabled
        )
    }

    @Test
    fun `khi tat thi luon cho phep hien quang cao`() {
        AdsBrain.enabled = false
        AdsBrain.onSessionStart("p_off_${System.nanoTime()}")
        // Dù trạng thái thế nào, tắt thì phải trả true để luồng cũ chạy nguyên vẹn.
        repeat(30) {
            assertTrue("tat brain phai giu hanh vi cu", AdsBrain.shouldShowInterstitial())
            AdsBrain.onInterstitialShown()
        }
        assertTrue(AdsBrain.shouldOfferRewarded())
    }

    @Test
    fun `khi tat thi khong hoc gi ca`() {
        AdsBrain.enabled = false
        val id = "p_nolearn_${System.nanoTime()}"
        AdsBrain.onSessionStart(id)
        repeat(10) { AdsBrain.onLevelFail() }
        assertTrue(
            "tat brain thi belief phai dung yen",
            DecisionEngine.instance.beliefFor(id).isStillPrior()
        )
    }

    // ═══ Khi bật ═══

    @Test
    fun `khi bat thi brain that su quyet dinh`() {
        AdsBrain.enabled = true
        val id = "p_on_${System.nanoTime()}"
        AdsBrain.onSessionStart(id)
        DecisionEngine.instance.guardrails.maxInterstitialPerSession = 2

        var shown = 0
        repeat(25) {
            if (AdsBrain.shouldShowInterstitial()) {
                shown++
                AdsBrain.onInterstitialShown()
            } else {
                AdsBrain.onAdSkipped()
            }
        }
        assertTrue("tran phai duoc ton trong, da hien $shown", shown <= 2)
        assertTrue("phai hien it nhat mot lan", shown >= 1)
    }

    @Test
    fun `khi bat thi tin hieu hanh vi lam belief dich chuyen`() {
        AdsBrain.enabled = true
        val id = "p_learn_${System.nanoTime()}"
        AdsBrain.onSessionStart(id)
        AdsBrain.onSessionStart(id)   // đủ 2 phiên để vượt ngưỡng support

        repeat(12) { AdsBrain.onLevelFail() }
        assertFalse(
            "thua lien tuc phai lam belief roi khoi prior",
            DecisionEngine.instance.beliefFor(id).isStillPrior()
        )
    }

    @Test
    fun `guardrail dao duc chan rewarded khi chua tung that bai`() {
        AdsBrain.enabled = true
        val id = "p_eth_${System.nanoTime()}"
        AdsBrain.onSessionStart(id)
        // Chưa fail lần nào -> ETH_MANUFACTURED_FAIL phải chặn (doc 14).
        assertFalse(
            "khong duoc moi rewarded khi chua co that bai that",
            AdsBrain.shouldOfferRewarded()
        )
    }

    @Test
    fun `sau khi that bai that thi rewarded khong con bi guardrail chan`() {
        // Chỉ khẳng định điều TẤT ĐỊNH: guardrail đạo đức không còn loại arm rewarded.
        // KHÔNG khẳng định bandit sẽ chọn nó — lựa chọn là ngẫu nhiên và phụ thuộc
        // posterior đã học, nên assert vào đó sẽ tạo test giòn (flaky).
        val g = GuardrailEngine()
        val rewarded = ActionCandidate(ActionMode.REWARDED_AD, "rewarded")
        val id = "p_eth2_${System.nanoTime()}"

        assertTrue(
            "chua that bai -> phai bi chan",
            g.evaluate(rewarded, PlayerState(id, failStreakCurrent = 0), 0, 999) != null
        )
        assertTrue(
            "da that bai that -> khong con bi chan",
            g.evaluate(rewarded, PlayerState(id, failStreakCurrent = 2), 0, 999) == null
        )
    }

    // ═══ Quản lý trạng thái ═══

    @Test
    fun `doi player thi khong mang so lieu nguoi truoc sang`() {
        AdsBrain.enabled = true
        val a = "p_a_${System.nanoTime()}"
        AdsBrain.onSessionStart(a)
        repeat(8) { AdsBrain.onLevelFail() }
        assertTrue(AdsBrain.currentState().failRateLast30m > 0.5)

        val b = "p_b_${System.nanoTime()}"
        AdsBrain.onSessionStart(b)
        assertEquals("player moi phai bat dau sach", b, AdsBrain.currentState().playerId)
        assertEquals(0.0, AdsBrain.currentState().failRateLast30m, 1e-9)
        assertEquals(0, AdsBrain.currentState().purchaseCountLifetime)
    }

    @Test
    fun `phien moi cap lai bo dem quang cao`() {
        AdsBrain.enabled = true
        val id = "p_sess_${System.nanoTime()}"
        AdsBrain.onSessionStart(id)

        AdsBrain.shouldShowInterstitial()
        AdsBrain.onInterstitialShown()
        AdsBrain.shouldShowInterstitial()
        AdsBrain.onInterstitialShown()
        assertEquals("bo dem phai tang theo so lan hien", 2, AdsBrain.adsShownThisSession())

        AdsBrain.onSessionStart(id)   // phiên mới
        // Kiểm tra BẤT BIẾN tất định (bộ đếm về 0), không phải hệ quả ngẫu nhiên
        // (bandit có chọn interstitial hay không) — assert vào lựa chọn ngẫu nhiên
        // tạo test giòn, đã gặp thật ở 2/5 lần chạy.
        assertEquals("phien moi phai cap lai bo dem", 0, AdsBrain.adsShownThisSession())
    }

    @Test
    fun `tin hieu mua hang duoc ghi nhan`() {
        AdsBrain.enabled = true
        val id = "p_buy_${System.nanoTime()}"
        AdsBrain.onSessionStart(id)
        AdsBrain.onPurchase(4.99)
        AdsBrain.onPurchase(9.99)
        assertEquals(2, AdsBrain.currentState().purchaseCountLifetime)
        assertEquals(14.98, AdsBrain.currentState().purchaseValueLifetimeUsd, 1e-9)
        assertEquals(0, AdsBrain.currentState().daysSinceLastPurchase)
    }

    @Test
    fun `ti le nhan rewarded duoc tong hop dung`() {
        AdsBrain.enabled = true
        AdsBrain.onSessionStart("p_rr_${System.nanoTime()}")
        AdsBrain.onRewardedOffered(accepted = true)
        AdsBrain.onRewardedOffered(accepted = true)
        AdsBrain.onRewardedOffered(accepted = false)
        AdsBrain.onRewardedOffered(accepted = false)
        assertEquals(4, AdsBrain.currentState().rewardedOpportunitiesRecent)
        assertEquals(0.5, AdsBrain.currentState().rewardedAcceptRateRecent, 1e-9)
    }

    // ═══ O9 (plan 1.6.0 Task 1): mỗi quyết định mới bắt đầu với pendingHadValue = false ═══

    /** Bật brain (rollout 1, không holdout, player mới) và trả reward của mọi feedback brain chốt trong [block]. */
    private fun capturedRewards(block: () -> Unit): List<Double?> {
        AdsBrain.enabled = true
        AdsBrain.rolloutFraction = 1.0
        AdsBrain.onSessionStart("p_o9_${System.nanoTime()}")
        val rewards = mutableListOf<Double?>()
        DecisionLogger.registerFeedbackForwarder { _, _, _, r -> rewards += r }
        try {
            block()
        } finally {
            DecisionLogger.clearForwardersForTest()
        }
        return rewards
    }

    @Test
    fun `onInterstitialShown khong co quyet dinh treo - quyet dinh ke tiep chot reward estimate(false, true), khong thua huong luot hien cu`() {
        val rewards = capturedRewards {
            // Không có quyết định treo (người ngoài rollout đã hiện, hoặc app tự gọi): cờ giá trị không được sống sót.
            AdsBrain.onInterstitialShown()
            AdsBrain.shouldShowInterstitial() // P1: không gọi onInterstitialShown/onAdSkipped sau nó
            AdsBrain.shouldShowInterstitial() // chốt P1
        }

        assertEquals("đúng một feedback: P1", 1, rewards.size)
        assertEquals(
            "P1 không giao giá trị nào — reward không được thừa hưởng lượt hiện trước quyết định (O9)",
            RewardModel.estimate(false, true), rewards.single()!!, 1e-9
        )
    }

    @Test
    fun `quyet dinh roi onInterstitialShown - lan chot sau van nhan estimate(true, true)`() {
        val rewards = capturedRewards {
            AdsBrain.shouldShowInterstitial() // P1
            AdsBrain.onInterstitialShown()    // P1 đã hiện
            AdsBrain.shouldShowInterstitial() // chốt P1
        }

        assertEquals("đúng một feedback: P1", 1, rewards.size)
        assertEquals(
            "lượt hiện SAU quyết định là giá trị của chính quyết định đó",
            RewardModel.estimate(true, true), rewards.single()!!, 1e-9
        )
    }

    @Test
    fun `ca 5 diem quyet dinh mo quyet dinh voi pendingHadValue = false`() {
        val sites: List<Pair<String, () -> Unit>> = listOf(
            "shouldShowInterstitial" to { AdsBrain.shouldShowInterstitial(); Unit },
            "shouldOfferRewarded" to { AdsBrain.shouldOfferRewarded(); Unit },
            "shouldOfferIap" to { AdsBrain.shouldOfferIap(); Unit },
            "chooseCrossModal" to { AdsBrain.chooseCrossModal(DecisionPoint.LEVEL_FAIL); Unit },
            "chooseAssist" to { AdsBrain.chooseAssist(DecisionPoint.LEVEL_FAIL_STREAK); Unit },
        )
        for ((site, decide) in sites) {
            val rewards = capturedRewards {
                AdsBrain.onInterstitialShown() // không có quyết định treo
                decide()
                AdsBrain.onPlayerContinued()   // chốt quyết định của site
            }
            assertEquals("$site: đúng một feedback", 1, rewards.size)
            assertEquals("$site: không thừa hưởng cờ cũ (O9)", RewardModel.estimate(false, true), rewards.single()!!, 1e-9)
        }
    }

    @Test
    fun `bao outcome nhieu lan khong lam hong gi`() {
        AdsBrain.enabled = true
        AdsBrain.onSessionStart("p_dup_${System.nanoTime()}")
        AdsBrain.shouldShowInterstitial()
        AdsBrain.onInterstitialShown()
        AdsBrain.onAdSkipped()   // không còn quyết định treo — phải bỏ qua im lặng
        AdsBrain.onAdSkipped()
    }
}
