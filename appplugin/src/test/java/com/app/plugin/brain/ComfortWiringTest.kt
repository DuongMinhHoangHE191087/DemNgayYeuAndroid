package com.app.plugin.brain

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * ComfortIndex phải THỰC SỰ được tính ở đâu đó. Trước bản này nó tồn tại nhưng không có
 * call site nào — một North Star Metric không được tính ở đâu thì trên thực tế là chưa có.
 */
class ComfortWiringTest {
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
        HoldoutManager.holdoutFraction = 0.05
        BrainPolicy.clear()
    }

    @Test
    fun `facade tinh duoc comfort cho player hien tai`() {
        AdsBrain.enabled = true
        AdsBrain.onSessionStart("p_comfort_${System.nanoTime()}")
        val r = AdsBrain.comfort()
        assertTrue("gia tri phai nam trong 0..1", r.value in 0.0..1.0)
    }

    @Test
    fun `van bao cao dung la CHUA du tin cay`() {
        // Tính chất quan trọng nhất của chỉ số này lúc này: nó phải tự khai báo là chưa đủ
        // tin cậy. 3/5 thành phần còn thiếu, và một con số độ phủ 40% trình bày như kết luận
        // chắc chắn còn nguy hiểm hơn không có chỉ số nào.
        AdsBrain.enabled = true
        AdsBrain.onSessionStart("p_cov_${System.nanoTime()}")
        val r = AdsBrain.comfort()
        assertEquals(0.4, r.coverage, 1e-9)
        assertFalse("chua duoc phep tuyen bo dang tin", r.isTrustworthy())
        assertEquals(3, r.componentsMissing.size)
    }

    @Test
    fun `quang cao cang nhieu thi comfort cang giam`() {
        AdsBrain.enabled = true
        val id = "p_fatigue_${System.nanoTime()}"
        AdsBrain.onSessionStart(id)
        val before = AdsBrain.comfort().value
        repeat(20) { AdsBrain.onInterstitialShown() }
        val after = AdsBrain.comfort().value
        assertTrue("hien 20 quang cao ma comfort khong giam thi chi so vo nghia", after < before)
    }

    @Test
    fun `ket thuc phien khong nem loi khi pipeline chua san sang`() {
        // Trong unit test `LogEventServer` chưa init. Chỉ số quan sát không bao giờ được
        // phép làm hỏng vòng đời phiên.
        AdsBrain.enabled = true
        AdsBrain.onSessionStart("p_end_${System.nanoTime()}")
        AdsBrain.onSessionEnd()
    }

    @Test
    fun `muc hung hang cua chinh sach bao cao rieng khong gop vao comfort`() {
        // Đính chính doc 26 mục C.4: guardrail nổ nhiều là tín hiệu về sức khoẻ CHÍNH SÁCH,
        // không phải về trải nghiệm người chơi (họ đã được bảo vệ nên comfort không đổi).
        val engine = DecisionEngine.instance
        val state = PlayerState("p_agg_${System.nanoTime()}")
        val before = ComfortIndex.compute(state).value
        engine.guardrails.evaluate(
            ActionCandidate(ActionMode.IAP_OFFER, "o"),
            PlayerState("p_x", failStreakCurrent = 9), 0, 999
        )
        assertEquals("guardrail no khong duoc lam doi comfort", before, ComfortIndex.compute(state).value, 1e-9)
        assertTrue(ComfortIndex.policyAggressiveness(engine).isNotEmpty())
    }
}
