package com.app.plugin.brain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Guardrail bảo vệ payer vừa mua phải nhận diện payer bằng **số lượt mua**, không bằng tổng
 * tiền quy đổi.
 *
 * Vì sao điều này quan trọng khi có billing thật: giá store về theo tiền tệ địa phương, và
 * `purchaseValueLifetimeUsd` chỉ được cộng khi biết tỉ giá (xem `FxRates`). Một người trả
 * 9,99 EUR trên máy chưa nạp tỉ giá EUR sẽ có tổng USD bằng 0 — nếu guardrail nhìn vào tổng
 * tiền, người vừa trả tiền đó tiếp tục bị dội quảng cáo như người chưa bao giờ trả đồng nào.
 */
class PayerProtectionTest {

    private fun adCandidate() = ActionCandidate(ActionMode.INTERSTITIAL, "interstitial")

    @Test
    fun `payer vua mua duoc bao ve ke ca khi chua quy doi duoc tien te`() {
        val g = GuardrailEngine()
        val state = PlayerState(
            "p_eur",
            purchaseCountLifetime = 1,
            purchaseValueLifetimeUsd = 0.0,   // chưa biết tỉ giá EUR
            daysSinceLastPurchase = 0
        )
        val violation = g.evaluate(adCandidate(), state, 0, 999)
        assertNotNull("nguoi vua tra tien phai duoc giam ad pressure", violation)
        assertEquals("PAYER_PROTECTION_RECENT", violation!!.code)
    }

    @Test
    fun `payer vua mua co quy doi duoc van duoc bao ve`() {
        val g = GuardrailEngine()
        val state = PlayerState(
            "p_usd",
            purchaseCountLifetime = 1,
            purchaseValueLifetimeUsd = 4.99,
            daysSinceLastPurchase = 1
        )
        assertEquals("PAYER_PROTECTION_RECENT", g.evaluate(adCandidate(), state, 0, 999)?.code)
    }

    @Test
    fun `nguoi chua tung mua khong kich hoat guardrail nay`() {
        val g = GuardrailEngine()
        val state = PlayerState("p_free", purchaseCountLifetime = 0, daysSinceLastPurchase = -1)
        assertNull(g.evaluate(adCandidate(), state, 0, 999))
    }

    @Test
    fun `payer mua da lau thi khong con trong cua so bao ve`() {
        val g = GuardrailEngine()
        val state = PlayerState(
            "p_old",
            purchaseCountLifetime = 3,
            purchaseValueLifetimeUsd = 20.0,
            daysSinceLastPurchase = 9
        )
        assertNull("bao ve la ngan han, khong phai vinh vien", g.evaluate(adCandidate(), state, 0, 999))
    }
}
