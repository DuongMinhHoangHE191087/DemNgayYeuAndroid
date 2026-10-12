package com.app.plugin.paywall

import com.app.plugin.brain.AdsBrain
import com.app.plugin.brain.BrainPolicy
import com.app.plugin.brain.HoldoutManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Paywall: cấu hình từ xa + A/B + **holdout native**.
 *
 * ═══ RANH GIỚI: SDK KHÔNG VẼ GIAO DIỆN ═══
 *
 * `PaywallManager` trả về *nên bán gì, theo thứ tự nào, với biến thể nào* — app tự vẽ. Đây là
 * cùng ranh giới mà `AdsBrain` đã theo: brain trả lời "có nên", caller thực thi.
 *
 * Vì sao không vẽ hộ: giao diện là thứ đặc thù nhất của từng game (font, khung, hoạt ảnh,
 * ngôn ngữ thiết kế). Một thư viện vẽ paywall sẽ hoặc trông lạc lõng, hoặc phải mang theo một
 * hệ thống theming đủ lớn để trở thành gánh nặng lớn hơn chính tính năng nó cung cấp.
 *
 * ═══ HOLDOUT NATIVE LÀ KHOẢNG TRỐNG ĐÃ XÁC MINH ═══
 *
 * doc 19 mục 5: **không vendor paywall lớn nào** (RevenueCat, Adapty, Superwall, Apphud,
 * Qonversion) có holdout native. Họ đo được biến thể A so với biến thể B, nhưng không đo được
 * *"có paywall so với không có paywall"* — tức không trả lời được câu hỏi giá trị tuyệt đối.
 */
class PaywallTest {

    private val cfgJson = """
        {
          "version": "pw_v1",
          "placements": {
            "shop": {
              "variants": [
                {"id": "control", "weight": 50, "products": ["coin_small", "coin_big"]},
                {"id": "reordered", "weight": 50, "products": ["coin_big", "coin_small"]}
              ]
            },
            "no_ads": {
              "variants": [
                {"id": "only", "weight": 100, "products": ["premium_monthly"], "highlight": "premium_monthly"}
              ]
            }
          }
        }
    """.trimIndent()

    @Before
    fun setUp() {
        PaywallManager.clear()
        HoldoutManager.holdoutFraction = 0.0
        BrainPolicy.clear()
        // paywallFor() nay hoi brain: phai dam bao brain tat de test nay chi do luong
        // paywall thuan tuy (test nao rieng se tu bat brain khi can).
        AdsBrain.enabled = false
    }

    @After
    fun tearDown() {
        PaywallManager.clear()
        HoldoutManager.holdoutFraction = 0.05
        BrainPolicy.clear()
        AdsBrain.enabled = false
    }

    // ═══ Nạp cấu hình ═══

    @Test
    fun `chua nap cau hinh thi tra null chu khong tra paywall rong`() {
        // null = "SDK không có ý kiến, app dùng luồng cũ". Một paywall rỗng thì app sẽ vẽ ra
        // một màn hình trống — tệ hơn hẳn việc không vẽ gì.
        assertNull(PaywallManager.paywallFor("shop", "p1"))
    }

    @Test
    fun `nap duoc cau hinh va tra ve bien the`() {
        assertTrue(PaywallManager.apply(cfgJson).applied)
        val pw = PaywallManager.paywallFor("shop", "p1")
        assertNotNull(pw)
        assertEquals("shop", pw!!.placement)
        assertEquals(2, pw.products.size)
    }

    @Test
    fun `placement khong khai bao tra null`() {
        PaywallManager.apply(cfgJson)
        assertNull(PaywallManager.paywallFor("khong_ton_tai", "p1"))
    }

    @Test
    fun `json hong giu nguyen cau hinh dang chay`() {
        PaywallManager.apply(cfgJson)
        val r = PaywallManager.apply("{ khong phai json")
        assertFalse(r.applied)
        assertNotNull("cau hinh cu phai con", PaywallManager.paywallFor("shop", "p1"))
        assertEquals("pw_v1", PaywallManager.activeVersion())
    }

    // ═══ Phân biến thể A/B ═══

    @Test
    fun `phan bien the tat dinh theo player`() {
        PaywallManager.apply(cfgJson)
        val first = PaywallManager.paywallFor("shop", "p_stable")!!.variantId
        repeat(30) {
            assertEquals("cung player phai luon cung bien the",
                first, PaywallManager.paywallFor("shop", "p_stable")!!.variantId)
        }
    }

    @Test
    fun `hai bien the deu duoc dung tren dan so`() {
        PaywallManager.apply(cfgJson)
        val seen = (1..400).mapNotNull { PaywallManager.paywallFor("shop", "p$it")?.variantId }.toSet()
        assertEquals("A/B 50-50 ma chi thay mot bien the -> phan chia hong",
            setOf("control", "reordered"), seen)
    }

    @Test
    fun `trong so 0 thi bien the khong bao gio duoc chon`() {
        PaywallManager.apply("""
            {"version":"v","placements":{"shop":{"variants":[
              {"id":"song","weight":100,"products":["a"]},
              {"id":"chet","weight":0,"products":["b"]}]}}}
        """.trimIndent())
        val seen = (1..300).mapNotNull { PaywallManager.paywallFor("shop", "p$it")?.variantId }.toSet()
        assertEquals(setOf("song"), seen)
    }

    @Test
    fun `phan bien the doc lap voi phan nhom holdout`() {
        // Dùng chung không gian băm sẽ khiến mọi người trong holdout rơi vào cùng một biến
        // thể — hai thí nghiệm tương quan hoàn toàn và không chạy song song được.
        // Holdout paywall 1.8.0 (Task 9, D14): không gian băm riêng `paywall_holdout_v2`, 30 %.
        PaywallManager.apply(cfgJson)
        val inHoldout = (1..2000).map { "p$it" }.filter { HoldoutManager.bucketWith("paywall_holdout_v2", it) < 3000 }
        assertTrue("can du mau", inHoldout.size > 100)
        val variants = inHoldout.mapNotNull { PaywallManager.variantIdFor("shop", it) }.toSet()
        assertEquals("trong nhom holdout phai co ca hai bien the", 2, variants.size)
    }

    // ═══ Holdout native — khoảng trống của thị trường ═══

    // 1.8.0 Task 9 (D6): holdout paywall là khối `holdout` của schema v2, mặc định 0 — cấu hình v1 (không
    // `schema_version`) không giữ ai lại, kể cả khi holdout của brain đang bật; muốn holdout thì khai `fraction`.
    private val cfgV2Holdout = """
        {"schema_version":2,"version":"pw_v2","holdout":{"fraction":1.0},
         "placements":{"shop":{"variants":[{"id":"control","weight":100,"products":["coin_small"]}]}}}
    """.trimIndent()

    @Test
    fun `nhom holdout KHONG nhan paywall`() {
        // Đây là điều không vendor nào làm: đo "có paywall so với KHÔNG có paywall", chứ
        // không chỉ đo biến thể A so với biến thể B.
        PaywallManager.apply(cfgV2Holdout)
        assertNull("holdout phai khong thay paywall", PaywallManager.paywallFor("shop", "p1"))
        PaywallManager.apply(cfgJson)
        HoldoutManager.holdoutFraction = 1.0
        assertNotNull("v1: holdout paywall mac dinh 0, khong theo holdout brain", PaywallManager.paywallFor("shop", "p1"))
    }

    @Test
    fun `holdout duoc ghi nhan de phan tich, khong im lang bien mat`() {
        PaywallManager.apply(cfgV2Holdout)
        PaywallManager.paywallFor("shop", "p_ho")
        assertEquals(1, PaywallManager.holdoutSkipCount())
    }

    // ═══ Thuộc tính hiển thị ═══

    @Test
    fun `thu tu san pham theo dung bien the`() {
        // Thứ tự là biến số của thí nghiệm — đảo nó đi là huỷ chính thí nghiệm.
        PaywallManager.apply(cfgJson)
        val control = PaywallManager.paywallFor("shop", "p_c")
        val reordered = PaywallManager.paywallFor("shop", "p_r")
        for (pw in listOfNotNull(control, reordered)) {
            val expected = if (pw.variantId == "control") listOf("coin_small", "coin_big")
                           else listOf("coin_big", "coin_small")
            assertEquals(expected, pw.products)
        }
    }

    @Test
    fun `san pham noi bat duoc truyen qua`() {
        PaywallManager.apply(cfgJson)
        assertEquals("premium_monthly", PaywallManager.paywallFor("no_ads", "p1")!!.highlight)
    }

    @Test
    fun `khong khai bao noi bat thi la null`() {
        PaywallManager.apply(cfgJson)
        assertNull(PaywallManager.paywallFor("shop", "p1")!!.highlight)
    }

    // ═══ Cấu hình sai ═══

    @Test
    fun `bien the khong co san pham bi bo qua`() {
        PaywallManager.apply("""
            {"version":"v","placements":{"shop":{"variants":[
              {"id":"rong","weight":100,"products":[]},
              {"id":"co_hang","weight":100,"products":["a"]}]}}}
        """.trimIndent())
        val seen = (1..200).mapNotNull { PaywallManager.paywallFor("shop", "p$it")?.variantId }.toSet()
        assertEquals("bien the rong se ve ra man hinh trong", setOf("co_hang"), seen)
    }

    @Test
    fun `placement khong con bien the hop le nao tra null`() {
        PaywallManager.apply("""
            {"version":"v","placements":{"shop":{"variants":[{"id":"rong","weight":100,"products":[]}]}}}
        """.trimIndent())
        assertNull(PaywallManager.paywallFor("shop", "p1"))
    }

    @Test
    fun `thieu version thi tu choi`() {
        assertFalse(PaywallManager.apply("""{"placements":{}}""").applied)
    }
}
