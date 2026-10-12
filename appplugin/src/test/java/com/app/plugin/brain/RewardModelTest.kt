package com.app.plugin.brain

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class RewardModelTest {
    @get:Rule val license = LicenseTestRule()

    @Before fun setUp() { RewardModel.reset(); AdsBrain.enabled = false; HoldoutManager.holdoutFraction = 0.0 }
    @After  fun tearDown() { RewardModel.reset(); AdsBrain.enabled = false; HoldoutManager.holdoutFraction = 0.05 }

    // ═══ Tính chất chống suy biến — quan trọng nhất ═══

    @Test
    fun `hien quang cao roi mat nguoi choi phai TE HON khong lam gi`() {
        val adThenQuit = RewardModel.estimate(actionHadValue = true, playerContinued = false)
        val nothingThenStay = RewardModel.estimate(actionHadValue = false, playerContinued = true)
        assertTrue(
            "khong co tinh chat nay thi bandit khong bao gio hoc duoc cach kiem che: " +
                "ad+quit=$adThenQuit vs im lang+o lai=$nothingThenStay",
            adThenQuit < nothingThenStay
        )
    }

    @Test
    fun `reward khong duoc phu thuoc DUY NHAT vao viec hanh dong da xay ra`() {
        // Đây chính là lỗi tautological của bản đầu: action = 1.0 bất kể hệ quả.
        val good = RewardModel.estimate(actionHadValue = true, playerContinued = true)
        val bad = RewardModel.estimate(actionHadValue = true, playerContinued = false)
        assertTrue("cung mot hanh dong phai cho reward khac nhau tuy he qua", good > bad)
    }

    @Test
    fun `thu tu uu tien bon tinh huong dung nhu thiet ke`() {
        val adStay = RewardModel.estimate(true, true)
        val noneStay = RewardModel.estimate(false, true)
        val adQuit = RewardModel.estimate(true, false)
        val noneQuit = RewardModel.estimate(false, false)
        assertTrue("$adStay > $noneStay", adStay > noneStay)
        assertTrue("$noneStay > $adQuit", noneStay > adQuit)
        assertTrue("$adQuit > $noneQuit", adQuit > noneQuit)
        assertEquals("mat nguoi ma chang duoc gi = 0", 0.0, noneQuit, 1e-9)
    }

    @Test
    fun `reward luon nam trong khoang 0 den 1`() {
        for (v in listOf(true, false)) for (c in listOf(true, false, null)) {
            val r = RewardModel.estimate(v, c)
            assertTrue("reward=$r ngoai khoang", r in 0.0..1.0)
        }
    }

    @Test
    fun `chua biet ket qua thi trung tinh, khong doan bua`() {
        val unknown = RewardModel.estimate(true, null)
        val stay = RewardModel.estimate(true, true)
        val quit = RewardModel.estimate(true, false)
        assertTrue("chua biet phai nam giua hai thai cuc", unknown in quit..stay)
    }

    // ═══ Tích hợp qua AdsBrain ═══

    private fun freshEngineBandit(): DecisionEngine = DecisionEngine()

    @Test
    fun `AdsBrain khong chot reward ngay luc hien quang cao`() {
        AdsBrain.enabled = true
        val id = "p_pend_${System.nanoTime()}"
        AdsBrain.onSessionStart(id)
        AdsBrain.shouldShowInterstitial()
        AdsBrain.onInterstitialShown()

        // Đếm TỔNG trên mọi arm: bandit có thể chọn no_action hoặc interstitial tuỳ
        // posterior đã học, nên đếm riêng một arm sẽ cho test giòn.
        fun totalObservations(): Double {
            var t = 0.0
            for (type in LatentPlayerType.values()) {
                for (arm in listOf("interstitial", "no_action")) {
                    t += DecisionEngine.instance.observationCount(
                        DecisionPoint.INTERSTITIAL_OPPORTUNITY, type, arm)
                }
            }
            return t
        }

        val beforeContinue = totalObservations()   // chưa có tín hiệu downstream
        AdsBrain.onLevelWin()                      // tín hiệu tiếp tục chơi -> giờ mới chốt
        val after = totalObservations()

        assertTrue(
            "reward chi duoc chot khi co tin hieu downstream: $beforeContinue -> $after",
            after > beforeContinue
        )
    }

    @Test
    fun `ket thuc phien chot quyet dinh treo la nguoi choi da roi di`() {
        AdsBrain.enabled = true
        val id = "p_end_${System.nanoTime()}"
        AdsBrain.onSessionStart(id)
        AdsBrain.shouldShowInterstitial()
        AdsBrain.onInterstitialShown()
        AdsBrain.onSessionEnd()   // không được ném, phải chốt gọn

        // Gọi lại khi không còn gì treo cũng phải an toàn.
        AdsBrain.onSessionEnd()
    }

    @Test
    fun `phien moi chot not quyet dinh con treo cua phien cu`() {
        AdsBrain.enabled = true
        val id = "p_carry_${System.nanoTime()}"
        AdsBrain.onSessionStart(id)
        AdsBrain.shouldShowInterstitial()
        AdsBrain.onInterstitialShown()
        AdsBrain.onSessionStart(id)   // không được bỏ quên quyết định treo
        AdsBrain.onLevelWin()
    }

    @Test
    fun `trong so co the dieu chinh va anh huong ket qua`() {
        RewardModel.weightImmediateValue = 0.9
        RewardModel.weightContinuation = 0.1
        // Khi doanh thu được coi trọng hơn hẳn giữ chân, thứ tự đảo lại —
        // đây là núm để cân đối, và cũng là chỗ dễ lạm dụng nhất.
        assertTrue(
            "voi trong so lech han ve doanh thu, ad+quit vuot im lang+o lai",
            RewardModel.estimate(true, false) > RewardModel.estimate(false, true)
        )
    }
}
