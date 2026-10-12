package com.app.plugin.iap

import android.app.Activity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock

/**
 * Router mua hang da kenh (doc 23 muc C.2).
 *
 * ═══ TINH CHAT TRUNG TAM: MOI HU HONG DEU ROI VE PLAY STORE ═══
 *
 * Cac test doc di doc lai mot cam ket: bat ky dieu kien nao thieu (rule, enabled, url https,
 * quoc gia, cau hinh hong) thi ket qua luon la PLAY_STORE — kenh luon hoat dong, hanh vi truoc
 * khi co router. Router khong duoc phep tao ra mot duong mua khong hoan tat duoc.
 */
class PurchaseRouterTest {

    private val cfgOk = """
        {
          "version": "routing_v1",
          "web_shop": {
            "enabled": true,
            "url_template": "https://shop.example.com/checkout?product={product_id}&user={user_id}",
            "eligible_countries": ["US", "DE"]
          },
          "rules": [
            {"product_id": "coin_pack_1", "channel": "web_shop"},
            {"product_id": "vip_pass", "channel": "play_store"},
            {"product_id": "coin_pack_1", "channel": "play_store"}
          ]
        }
    """.trimIndent()

    // Provider mac dinh doc tu LogEventServerHelper; tra lai sau moi test de test khac (cung JVM) thay nguon that.
    private val defaultCountryProvider = PurchaseRouter.countryProvider
    private val defaultUserIdProvider = PurchaseRouter.userIdProvider

    @Before
    fun setUp() {
        PurchaseRouter.clear()
        Entitlements.clearAll()
        PurchaseRouter.countryProvider = { "US" }
        PurchaseRouter.userIdProvider = { "user_1" }
    }

    @After
    fun tearDown() {
        PurchaseRouter.clear()
        Entitlements.clearAll()
        PurchaseRouter.countryProvider = defaultCountryProvider
        PurchaseRouter.userIdProvider = defaultUserIdProvider
    }

    // ═══ Trang thai mac dinh ═══

    @Test
    fun `chua nap cau hinh thi moi san pham di play store`() {
        assertEquals(PurchaseChannel.PLAY_STORE, PurchaseRouter.route("coin_pack_1"))
        assertEquals("chưa nạp", PurchaseRouter.activeVersion())
    }

    // ═══ Tu choi cau hinh hong — giu nguyen ban dang chay ═══

    @Test
    fun `cau hinh rong bi tu choi`() {
        assertFalse(PurchaseRouter.apply(null).applied)
        assertFalse(PurchaseRouter.apply("   ").applied)
    }

    @Test
    fun `cau hinh hong bi tu choi va giu nguyen ban cu`() {
        assertTrue(PurchaseRouter.apply(cfgOk).applied)
        val r = PurchaseRouter.apply("{json hong")
        assertFalse(r.applied)
        assertEquals("ban cu van con hieu luc", "routing_v1", PurchaseRouter.activeVersion())
        assertEquals(PurchaseChannel.WEB_SHOP, PurchaseRouter.route("coin_pack_1"))
    }

    @Test
    fun `thieu truong version bi tu choi`() {
        assertFalse(PurchaseRouter.apply("""{"rules": []}""").applied)
    }

    // ═══ Rule matching ═══

    @Test
    fun `rule dau tien khop thang`() {
        PurchaseRouter.apply(cfgOk)
        // coin_pack_1 khai bao 2 lan (web_shop truoc, play_store sau) — rule dau thang.
        assertEquals(PurchaseChannel.WEB_SHOP, PurchaseRouter.route("coin_pack_1"))
    }

    @Test
    fun `san pham khong co rule di play store`() {
        PurchaseRouter.apply(cfgOk)
        assertEquals(PurchaseChannel.PLAY_STORE, PurchaseRouter.route("san_pham_la"))
    }

    @Test
    fun `rule play store tuong minh di play store`() {
        PurchaseRouter.apply(cfgOk)
        assertEquals(PurchaseChannel.PLAY_STORE, PurchaseRouter.route("vip_pass"))
    }

    @Test
    fun `channel khong hop le bi bo qua co ghi chu`() {
        val r = PurchaseRouter.apply(
            """
            {
              "version": "routing_v2",
              "web_shop": {"enabled": true, "url_template": "https://s.example.com/{product_id}"},
              "rules": [{"product_id": "coin_pack_1", "channel": "crypto"}]
            }
            """.trimIndent()
        )
        assertTrue(r.applied)
        assertTrue("phai canh bao rule hong", r.report.contains("không hợp lệ"))
        assertEquals(PurchaseChannel.PLAY_STORE, PurchaseRouter.route("coin_pack_1"))
    }

    // ═══ Cac dieu kien an toan cua WEB_SHOP ═══

    @Test
    fun `web shop tat thi rule web_shop roi ve play store`() {
        PurchaseRouter.apply(
            """
            {
              "version": "routing_v3",
              "web_shop": {"enabled": false, "url_template": "https://s.example.com/{product_id}"},
              "rules": [{"product_id": "coin_pack_1", "channel": "web_shop"}]
            }
            """.trimIndent()
        )
        assertEquals(PurchaseChannel.PLAY_STORE, PurchaseRouter.route("coin_pack_1"))
    }

    @Test
    fun `url template rong thi web shop khong dung duoc`() {
        PurchaseRouter.apply(
            """
            {
              "version": "routing_v4",
              "web_shop": {"enabled": true, "url_template": "  "},
              "rules": [{"product_id": "coin_pack_1", "channel": "web_shop"}]
            }
            """.trimIndent()
        )
        assertEquals(PurchaseChannel.PLAY_STORE, PurchaseRouter.route("coin_pack_1"))
    }

    @Test
    fun `url http bi chan va co canh bao`() {
        val r = PurchaseRouter.apply(
            """
            {
              "version": "routing_v5",
              "web_shop": {"enabled": true, "url_template": "http://shop.example.com/{product_id}"},
              "rules": [{"product_id": "coin_pack_1", "channel": "web_shop"}]
            }
            """.trimIndent()
        )
        assertTrue(r.applied)
        assertTrue("phai canh bao url khong https", r.report.contains("https"))
        assertEquals("dan nguoi dung mang the tin dung toi http la lo hong bao mat",
            PurchaseChannel.PLAY_STORE, PurchaseRouter.route("coin_pack_1"))
    }

    @Test
    fun `khong co khoi web_shop thi moi rule web_shop roi ve play store`() {
        val r = PurchaseRouter.apply(
            """
            {
              "version": "routing_v6",
              "rules": [{"product_id": "coin_pack_1", "channel": "web_shop"}]
            }
            """.trimIndent()
        )
        assertTrue(r.applied)
        assertEquals(PurchaseChannel.PLAY_STORE, PurchaseRouter.route("coin_pack_1"))
    }

    // ═══ Eligible countries ═══

    @Test
    fun `quoc gia trong danh sach thi duoc ra web shop`() {
        PurchaseRouter.apply(cfgOk)
        PurchaseRouter.countryProvider = { "DE" }
        assertEquals(PurchaseChannel.WEB_SHOP, PurchaseRouter.route("coin_pack_1"))
    }

    @Test
    fun `quoc gia ngoai danh sach thi ve play store`() {
        PurchaseRouter.apply(cfgOk)
        PurchaseRouter.countryProvider = { "VN" }
        assertEquals(PurchaseChannel.PLAY_STORE, PurchaseRouter.route("coin_pack_1"))
    }

    @Test
    fun `khong xac dinh duoc quoc gia thi khong dan ra kenh gioi han`() {
        PurchaseRouter.apply(cfgOk)
        PurchaseRouter.countryProvider = { "DEFAULT" }
        assertEquals(PurchaseChannel.PLAY_STORE, PurchaseRouter.route("coin_pack_1"))
    }

    @Test
    fun `danh sach rong la khong gioi han quoc gia`() {
        PurchaseRouter.apply(
            """
            {
              "version": "routing_v7",
              "web_shop": {"enabled": true, "url_template": "https://s.example.com/{product_id}"},
              "rules": [{"product_id": "coin_pack_1", "channel": "web_shop"}]
            }
            """.trimIndent()
        )
        PurchaseRouter.countryProvider = { "DEFAULT" }
        assertEquals(PurchaseChannel.WEB_SHOP, PurchaseRouter.route("coin_pack_1"))
    }

    @Test
    fun `quoc gia duoc chuan hoa truoc khi so khop`() {
        PurchaseRouter.apply(cfgOk)
        PurchaseRouter.countryProvider = { "  us  " }
        assertEquals(PurchaseChannel.WEB_SHOP, PurchaseRouter.route("coin_pack_1"))
    }

    // ═══ Dung URL checkout ═══

    @Test
    fun `url checkout thay the product va user co encode`() {
        PurchaseRouter.apply(cfgOk)
        PurchaseRouter.userIdProvider = { "user id&1" }
        val url = PurchaseRouter.checkoutUrlFor("coin_pack_1")
        assertEquals(
            "https://shop.example.com/checkout?product=coin_pack_1&user=user+id%261",
            url
        )
    }

    @Test
    fun `san pham khong di web shop thi khong co url checkout`() {
        PurchaseRouter.apply(cfgOk)
        assertNull(PurchaseRouter.checkoutUrlFor("vip_pass"))
        assertNull(PurchaseRouter.checkoutUrlFor("san_pham_la"))
    }

    // ═══ purchase() — API duy nhất ═══

    @Test
    fun `purchase kenh web shop tra ve WebCheckoutOpened`() {
        PurchaseRouter.apply(cfgOk)
        val activity = mock(Activity::class.java)
        var result: IapResult? = null
        val ok = PurchaseRouter.purchase(activity, "coin_pack_1") { result = it }
        assertTrue(ok)
        val r = result
        assertTrue("phai tra WebCheckoutOpened, nhan: $r", r is IapResult.WebCheckoutOpened)
        r as IapResult.WebCheckoutOpened
        assertEquals("coin_pack_1", r.productId)
        assertTrue(r.url.startsWith("https://shop.example.com/checkout?product=coin_pack_1"))
    }

    // ═══ grantFromServer ═══

    @Test
    fun `grantFromServer cap quyen loi cho giao dich da xac thuc`() {
        val ok = PurchaseRouter.grantFromServer(
            productId = "coin_pack_1",
            orderId = "WS-ORDER-1",
            priceMicros = 4_990_000L,
            currencyCode = "USD"
        )
        assertTrue(ok)
        assertTrue(Entitlements.isActive("coin_pack_1"))
    }

    @Test
    fun `grantFromServer tu choi tham so rong`() {
        assertFalse(PurchaseRouter.grantFromServer("", "WS-ORDER-2", 1_000_000L, "USD"))
        assertFalse(PurchaseRouter.grantFromServer("coin_pack_1", "  ", 1_000_000L, "USD"))
        assertFalse("khong cap gi khi tu choi", Entitlements.isActive("coin_pack_1"))
    }
}
