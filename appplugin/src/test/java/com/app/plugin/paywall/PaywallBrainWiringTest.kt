package com.app.plugin.paywall

import com.app.plugin.brain.AdsBrain
import com.app.plugin.brain.BrainPolicy
import com.app.plugin.brain.DecisionLogger
import com.app.plugin.brain.DecisionPoint
import com.app.plugin.brain.DecisionRequest
import com.app.plugin.brain.DecisionResponse
import com.app.plugin.brain.HoldoutManager
import com.app.plugin.brain.LicenseTestRule
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Paywall hỏi brain trước khi hiện — mảnh ghép cross-modal cuối cùng (doc 23 mục E.3,
 * doc 49 mục E.2 đã chốt).
 *
 * Cùng quy ước với `CrossModalTest`: assert vào tính chất TẤT ĐỊNH (thứ tự kiểm tra,
 * guardrail chặn) chứ không assert vào lựa chọn ngẫu nhiên của bandit; chỗ nào bắt buộc đi
 * qua lựa chọn ngẫu nhiên thì lặp tới khi đạt.
 */
class PaywallBrainWiringTest {
    @get:Rule val license = LicenseTestRule()

    private val cfgJson = """
        {
          "version": "pw_wiring",
          "placements": {
            "shop": {
              "variants": [{"id": "v1", "weight": 100, "products": ["coin_small"]}]
            },
            "currency": {
              "decision_point": "CURRENCY_LOW",
              "variants": [{"id": "v1", "weight": 100, "products": ["gem_small"]}]
            }
          }
        }
    """.trimIndent()

    private val decisions = mutableListOf<Pair<DecisionRequest, DecisionResponse>>()

    @Before
    fun setUp() {
        PaywallManager.clear()
        HoldoutManager.holdoutFraction = 0.0
        BrainPolicy.clear()
        AdsBrain.enabled = false
        AdsBrain.rolloutFraction = 1.0
        DecisionLogger.clearForwardersForTest()
        decisions.clear()
        DecisionLogger.registerForwarder { req, res -> decisions.add(req to res) }
    }

    @After
    fun tearDown() {
        PaywallManager.clear()
        HoldoutManager.holdoutFraction = 0.05
        BrainPolicy.clear()
        AdsBrain.enabled = false
        AdsBrain.rolloutFraction = 1.0
        // Trả DecisionLogger về trạng thái chuẩn (sink mặc định) cho các lớp test khác.
        DecisionLogger.clearForwardersForTest()
        DecisionLogger.installDefaultSink()
    }

    // ═══ Khi brain tắt: hành vi cũ nguyên vẹn ═══

    @Test
    fun `brain tat thi paywall chay nhu truoc khi co wiring`() {
        AdsBrain.onSessionStart("p_off_${System.nanoTime()}")
        PaywallManager.apply(cfgJson)
        assertNotNull(PaywallManager.paywallFor("shop", "p_off"))
        assertTrue("brain tat khong duoc tao quyet dinh nao", decisions.isEmpty())
    }

    // ═══ Thứ tự: nhóm đối chứng paywall kiểm tra TRƯỚC brain ═══

    @Test
    fun `nhom doi chung khong nhan paywall va khong ton quyet dinh bandit nao`() {
        // Nếu kiểm tra brain trước, mỗi lần hỏi paywall của người holdout sẽ ăn một quyết
        // định bandit (và một bản ghi decision log) rồi mới vứt đi — vừa tốn vừa làm bẩn
        // dữ liệu OPE vì propensity của nhóm treatment bị pha bởi quyết định không phục vụ ai.
        AdsBrain.enabled = true
        AdsBrain.onSessionStart("p_ho_${System.nanoTime()}")
        // 1.8.0 Task 9 (D6/D14): holdout paywall là khối `holdout` riêng của schema v2 (mặc định 0).
        PaywallManager.apply(cfgJson.replace("\"version\": \"pw_wiring\",",
            "\"schema_version\": 2, \"version\": \"pw_wiring\", \"holdout\": {\"fraction\": 1.0},"))

        assertNull(PaywallManager.paywallFor("shop", "p_ho"))
        assertEquals(1, PaywallManager.holdoutSkipCount())
        assertTrue("khong duoc ton quyet dinh nao cho nguoi holdout", decisions.isEmpty())
    }

    // ═══ Brain gate: paywall chịu CÙNG bộ guardrail với mọi luồng chào bán IAP ═══

    @Test
    fun `brain tu choi vi van toc mua thi paywall tra null`() {
        AdsBrain.enabled = true
        AdsBrain.onSessionStart("p_vel_${System.nanoTime()}")
        PaywallManager.apply(cfgJson)
        repeat(3) { AdsBrain.onPurchase(4.99) }   // chạm IAP_VELOCITY_BREAKER (doc 29 mục 5.3)
        assertNull("brain chan thi paywall khong duoc hien", PaywallManager.paywallFor("shop", "p_vel"))
    }

    @Test
    fun `brain tu choi vi be tac thi paywall tra null`() {
        AdsBrain.enabled = true
        AdsBrain.onSessionStart("p_fru_${System.nanoTime()}")
        PaywallManager.apply(cfgJson)
        repeat(6) { AdsBrain.onLevelFail() }      // chạm ETH_FRUSTRATION_EXPLOIT (doc 29 mục 10.2)
        assertNull("khong duoc kiem tien tren su be tac", PaywallManager.paywallFor("shop", "p_fru"))
    }

    @Test
    fun `ngoai rollout thi brain khong can thiep paywall`() {
        AdsBrain.enabled = true
        AdsBrain.rolloutFraction = 0.0
        AdsBrain.onSessionStart("p_noroll_${System.nanoTime()}")
        PaywallManager.apply(cfgJson)
        // Kể cả trạng thái mà bình thường brain sẽ chặn — ngoài thí nghiệm thì không can thiệp.
        repeat(6) { AdsBrain.onLevelFail() }
        repeat(3) { AdsBrain.onPurchase(4.99) }
        assertNotNull("ngoai thi nghiem thi giu nguyen luong cu", PaywallManager.paywallFor("shop", "p_noroll"))
    }

    // ═══ Decision point theo placement ═══

    @Test
    fun `placement khai bao decision_point nao thi brain hoc decision point do`() {
        // SHOP_OPEN và CURRENCY_LOW khác nhau về mặt đạo đức (tự mở cửa hàng vs bị dụ lúc
        // hết tài nguyên) nên phải được học riêng — cấu hình placement quyết định ngữ cảnh.
        AdsBrain.enabled = true
        AdsBrain.onSessionStart("p_dp_${System.nanoTime()}")
        PaywallManager.apply(cfgJson)

        PaywallManager.paywallFor("currency", "p_dp")   // kết quả bandit không quan trọng
        assertTrue("phai co quyet dinh duoc tao", decisions.isNotEmpty())
        assertEquals(DecisionPoint.CURRENCY_LOW, decisions.last().first.decisionPoint)

        decisions.clear()
        PaywallManager.paywallFor("shop", "p_dp")
        assertEquals(
            "placement khong khai bao thi mac dinh SHOP_OPEN",
            DecisionPoint.SHOP_OPEN, decisions.last().first.decisionPoint
        )
    }

    @Test
    fun `decision_point khong hop le thi canh bao va dung SHOP_OPEN`() {
        val r = PaywallManager.apply("""
            {"version":"v","placements":{"shop":{"decision_point":"NOT_A_POINT","variants":[
              {"id":"v1","weight":100,"products":["a"]}]}}}
        """.trimIndent())
        assertTrue("truong tuy chon sai khong duoc lam mat ca paywall", r.applied)
        assertTrue("bao cao apply phai nhac canh bao", r.report.contains("decision_point"))

        AdsBrain.enabled = true
        AdsBrain.onSessionStart("p_bad_${System.nanoTime()}")
        PaywallManager.paywallFor("shop", "p_bad")
        assertEquals(DecisionPoint.SHOP_OPEN, decisions.last().first.decisionPoint)
    }

    // ═══ onPaywallShown khép vòng guardrail ═══

    @Test
    fun `paywall hien ra phai duoc dem vao guardrail cua brain`() {
        // Nếu không, paywall là cửa sau lách qua chính bộ bảo vệ vừa chặn nó: brain chặn
        // chào bán vì quá tần suất, app vẽ paywall thẳng tay, và bộ đếm không hề biết.
        AdsBrain.enabled = true
        AdsBrain.onSessionStart("p_shown_${System.nanoTime()}")
        PaywallManager.apply(cfgJson)

        var pw: PaywallManager.Paywall? = null
        repeat(40) {                                  // bandit ngẫu nhiên: lặp tới khi được chọn
            if (pw == null) pw = PaywallManager.paywallFor("shop", "p_shown")
        }
        assertNotNull("sau 40 lan phai co it nhat mot lan duoc chao ban", pw)

        val before = AdsBrain.currentState().iapOffersThisSession
        PaywallManager.onPaywallShown(pw!!)
        assertEquals("luot hien paywall phai nhich bo dem chao ban",
            before + 1, AdsBrain.currentState().iapOffersThisSession)
    }

    @Test
    fun `brain tat thi onPaywallShown khong nhich bo dem nao`() {
        AdsBrain.onSessionStart("p_off2_${System.nanoTime()}")
        PaywallManager.apply(cfgJson)
        val pw = PaywallManager.paywallFor("shop", "p_off2")!!
        PaywallManager.onPaywallShown(pw)
        assertEquals("brain tat = moi bo dem giu nguyen", 0, AdsBrain.currentState().iapOffersThisSession)
    }
}
