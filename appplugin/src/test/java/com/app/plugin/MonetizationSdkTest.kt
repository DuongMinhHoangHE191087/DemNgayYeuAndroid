package com.app.plugin

import com.app.plugin.ads.AdSdkStartup
import com.app.plugin.iap.resetIapHelperForTest
import com.app.plugin.ads.RemoveAdsMask
import com.app.plugin.brain.AdsBrain
import com.app.plugin.brain.BrainPolicy
import com.app.plugin.brain.HoldoutManager
import com.app.plugin.brain.PlayerIdSource
import com.app.plugin.consent.ConsentState
import com.app.plugin.consent.ConsentTier
import com.app.plugin.consent.GPP_US_OK
import com.app.plugin.consent.GPP_US_OPT_OUT
import com.app.plugin.consent.GppConsent
import com.app.plugin.iap.Entitlements
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Điểm vào duy nhất cho studio tích hợp.
 *
 * Vấn đề nó giải quyết: trước khi có nó, một studio phải tự biết **thứ tự** gọi bốn thứ nằm
 * ở bốn package khác nhau. Thứ tự đó không hiển nhiên và sai thì không có gì báo lỗi — chỉ
 * là hành vi lệch đi một cách im lặng. Thứ tự thuộc về code, không thuộc về tài liệu.
 */
class MonetizationSdkTest {

    @Before
    fun setUp() {
        resetIapHelperForTest()
        MonetizationSdk.resetForTest()
        AdsBrain.enabled = false
        AdsBrain.rolloutFraction = 1.0
        HoldoutManager.holdoutFraction = 0.0
        Entitlements.clearAll()
        BrainPolicy.clear()
        ConsentState.resetForTest()
    }

    @After
    fun tearDown() {
        resetIapHelperForTest()
        MonetizationSdk.resetForTest()
        AdsBrain.enabled = false
        AdsBrain.rolloutFraction = 1.0
        HoldoutManager.holdoutFraction = 0.05
        Entitlements.clearAll()
        BrainPolicy.clear()
        ConsentState.resetForTest()
    }

    // ═══ Kill switch (Task 16, fix wave 1 Minor 3) ═══

    @Test
    fun `kill switch iap thi bao cao ghi billing bi kill switch, khong phai da khoi dong`() {
        SdkKillSwitch.current = SdkKillSwitch(iap = true)
        try {
            val r = MonetizationSdk.configure(org.mockito.Mockito.mock(android.content.Context::class.java)) {
                inappProducts = listOf("coin_pack")
            }

            assertFalse(r.billingStarted)
            assertTrue(r.warnings.toString(), r.warnings.any { it.contains("billing bị kill switch") })
            assertFalse(r.describe().contains("billing đã khởi động"))
        } finally {
            SdkKillSwitch.current = SdkKillSwitch.NONE
        }
    }

    // ═══ Mặc định an toàn ═══

    @Test
    fun `khong cau hinh gi thi brain van TAT`() {
        MonetizationSdk.applyForTest(MonetizationSdk.Config())
        assertFalse("bat brain phai la quyet dinh co y thuc", AdsBrain.enabled)
    }

    @Test
    fun `cau hinh rong khong lam vo thu gi`() {
        val r = MonetizationSdk.applyForTest(MonetizationSdk.Config())
        assertTrue(r.describe().isNotBlank())
    }

    // ═══ Thứ tự áp dụng — lý do chính lớp này tồn tại ═══

    @Test
    fun `rollout duoc dat TRUOC khi bat brain`() {
        // Nếu bật `enabled` trước rồi mới hạ `rolloutFraction`, sẽ có một khoảng thời gian
        // brain chạy ở 100% dân số. Trên một app thật, khoảng đó đủ để phục vụ hàng nghìn
        // quyết định mà lẽ ra không được phép.
        val order = MonetizationSdk.applyForTest(
            MonetizationSdk.Config().apply { brainEnabled = true; brainRolloutFraction = 0.05 }
        ).appliedOrder
        assertTrue(
            "rollout phai duoc dat truoc enabled, thuc te: $order",
            order.indexOf("brain.rolloutFraction") < order.indexOf("brain.enabled")
        )
    }

    @Test
    fun `playerId cua app ghi vao nguon user_id TRUOC khi bao brain`() {
        // `AdsBrain.onSessionStart(id)` phát `experiment_exposure` với bucket tính theo id; body event đọc
        // `user_id` từ PlayerIdSource. Ghi sau thì bản ghi đó có thể mang id của SDK, lệch với bucket của nó
        // (reconcile.py lớp 1, #13).
        try {
            val order = MonetizationSdk.applyForTest(
                MonetizationSdk.Config().apply { playerId = "acct-1" }
            ).appliedOrder
            assertTrue(
                "playerId.override phai truoc brain.onSessionStart, thuc te: $order",
                order.indexOf("playerId.override") in 0 until order.indexOf("brain.onSessionStart")
            )
            assertEquals("acct-1", PlayerIdSource.override)
        } finally {
            PlayerIdSource.override = null
        }
    }

    @Test
    fun `quyen loi duoc khai bao TRUOC khi khoi dong billing`() {
        // `IapHelper.init()` kết nối rồi tự gọi `restorePurchases()`. Nếu danh sách sản phẩm
        // tắt quảng cáo chưa được khai báo lúc đó, lượt khôi phục đầu tiên sẽ tính ra
        // `hasRemoveAds() = false` cho một subscriber thật — họ thấy quảng cáo cho tới lần
        // đồng bộ sau.
        val order = MonetizationSdk.applyForTest(
            MonetizationSdk.Config().apply {
                removeAdsProducts = setOf("premium_monthly")
                subsProducts = listOf("premium_monthly")
            },
            startBilling = true
        ).appliedOrder
        assertTrue(
            "entitlements phai truoc iap.init, thuc te: $order",
            order.indexOf("entitlements.removeAdsProducts") < order.indexOf("iap.init")
        )
    }

    @Test
    fun `khong khai bao san pham thi khong khoi dong billing`() {
        // Mở kết nối billing cho một app không bán gì chỉ là lãng phí.
        val order = MonetizationSdk.applyForTest(
            MonetizationSdk.Config(), startBilling = true
        ).appliedOrder
        assertFalse("khong co san pham ma van init billing", order.contains("iap.init"))
    }

    // ═══ Giá trị thực sự được áp ═══

    @Test
    fun `cau hinh brain di toi dung dich`() {
        MonetizationSdk.applyForTest(
            MonetizationSdk.Config().apply { brainEnabled = true; brainRolloutFraction = 0.2 }
        )
        assertTrue(AdsBrain.enabled)
        assertEquals(0.2, AdsBrain.rolloutFraction, 1e-9)
    }

    @Test
    fun `san pham tat quang cao di toi dung dich`() {
        MonetizationSdk.applyForTest(
            MonetizationSdk.Config().apply { removeAdsProducts = setOf("vip") }
        )
        Entitlements.grant("vip")
        assertTrue(Entitlements.hasRemoveAds())
    }

    @Test
    fun `mask theo san pham di toi dung dich va duoc ap truoc billing`() {
        // Cùng lý do với removeAdsProducts: lượt khôi phục đầu tiên của IapHelper.init() phải
        // tính ra đúng INTERRUPTIVE cho gói rẻ, không phải ALL rồi mới hạ xuống.
        val order = MonetizationSdk.applyForTest(
            MonetizationSdk.Config().apply {
                subsProducts = listOf("no_ads_lite")
                removeAdsMasks = mapOf("no_ads_lite" to RemoveAdsMask.INTERRUPTIVE)
            },
            startBilling = true
        ).appliedOrder
        assertTrue(
            "mask phai truoc iap.init, thuc te: $order",
            order.indexOf("entitlements.removeAdsMasks") in 0 until order.indexOf("iap.init")
        )
        Entitlements.grant("no_ads_lite")
        assertEquals(RemoveAdsMask.INTERRUPTIVE, Entitlements.removeAdsMask())
    }

    // ═══ Tự kiểm tra — thứ studio cần nhất khi mới thả SDK vào ═══

    @Test
    fun `tu kiem tra canh bao khi mask khai cho san pham khong co trong danh sach ban`() {
        val r = MonetizationSdk.applyForTest(
            MonetizationSdk.Config().apply {
                subsProducts = listOf("no_ads_lite")
                removeAdsMasks = mapOf("no_ads_lit" to RemoveAdsMask.INTERRUPTIVE)
            }
        )
        assertTrue(r.warnings.any { "removeAdsMasks" in it && "no_ads_lit" in it })
    }

    @Test
    fun `sub chi khai mask khong bi canh bao la quen tat quang cao`() {
        val r = MonetizationSdk.applyForTest(
            MonetizationSdk.Config().apply {
                subsProducts = listOf("no_ads_lite")
                removeAdsMasks = mapOf("no_ads_lite" to RemoveAdsMask.INTERRUPTIVE)
                childDirected = false
                consentHandledExternally = true
            }
        )
        assertTrue("cau hinh dung ma van canh bao: ${r.warnings}", r.warnings.isEmpty())
    }

    @Test
    fun `tu kiem tra canh bao khi bat brain ma rollout bang khong`() {
        // Đây là cấu hình "im lặng không làm gì": brain bật mà không điều khiển ai. Không có
        // dấu hiệu nào để chẩn đoán nếu không ai nói ra.
        val r = MonetizationSdk.applyForTest(
            MonetizationSdk.Config().apply { brainEnabled = true; brainRolloutFraction = 0.0 }
        )
        assertTrue(r.warnings.any { "rollout" in it.lowercase() })
    }

    @Test
    fun `tu kiem tra canh bao khi khai bao san pham ma quen khai bao tat quang cao`() {
        val r = MonetizationSdk.applyForTest(
            MonetizationSdk.Config().apply { subsProducts = listOf("premium_monthly") }
        )
        assertTrue(
            "sub ma khong co san pham nao tat quang cao thuong la bo sot",
            r.warnings.any { "remove" in it.lowercase() || "quảng cáo" in it.lowercase() }
        )
    }

    @Test
    fun `tu kiem tra canh bao khi khai bao consumable khong co trong danh sach san pham`() {
        // Gõ nhầm id ở đây nghĩa là sản phẩm không bao giờ được consume, và người dùng
        // không mua lại được — một lỗi rất khó truy từ báo cáo của người chơi.
        val r = MonetizationSdk.applyForTest(
            MonetizationSdk.Config().apply {
                inappProducts = listOf("coin_pack")
                consumableProducts = setOf("coin_pak")
            }
        )
        assertTrue(r.warnings.any { "coin_pak" in it })
    }

    @Test
    fun `tu kiem tra canh bao khi san pham tat quang cao khong co trong danh sach ban`() {
        val r = MonetizationSdk.applyForTest(
            MonetizationSdk.Config().apply {
                subsProducts = listOf("premium_monthly")
                removeAdsProducts = setOf("premium_yearly")
            }
        )
        assertTrue(r.warnings.any { "premium_yearly" in it })
    }

    @Test
    fun `khong co canh bao gia khi cau hinh dung`() {
        val r = MonetizationSdk.applyForTest(
            MonetizationSdk.Config().apply {
                brainEnabled = true
                brainRolloutFraction = 0.05
                inappProducts = listOf("coin_pack")
                subsProducts = listOf("premium_monthly")
                consumableProducts = setOf("coin_pack")
                removeAdsProducts = setOf("premium_monthly")
                childDirected = false
                consentHandledExternally = true
            }
        )
        assertTrue("cau hinh dung ma van canh bao: ${r.warnings}", r.warnings.isEmpty())
    }

    @Test
    fun `bao cao doc duoc va neu ro trang thai brain`() {
        val text = MonetizationSdk.applyForTest(
            MonetizationSdk.Config().apply { brainEnabled = true; brainRolloutFraction = 0.05 }
        ).describe()
        assertTrue(text.contains("brain"))
        assertTrue(text.contains("5") || text.contains("0.05"))
    }

    @Test
    fun `bao cao neu ro rang chua co backend`() {
        // Studio phải biết ngay: hàng đợi decision log tích lại và không ai học từ nó cho tới
        // khi có backend. Giấu chuyện này là để họ tưởng brain đang tự cải thiện.
        val text = MonetizationSdk.applyForTest(MonetizationSdk.Config()).describe()
        assertTrue("bao cao phai noi ro trang thai backend", text.lowercase().contains("backend"))
    }

    @Test
    fun `bao cao noi dang dem khi hang doi decision log chua dem xong`() {
        // Task 15: DecisionLogger đếm hàng đợi trên luồng nền, `queuedCount()` là -1 tới khi đếm xong.
        // Báo cáo không được in "-1 bản ghi" cho studio.
        val r = MonetizationSdk.applyForTest(MonetizationSdk.Config())
        val counting = r.copy(queuedDecisionLogs = -1).describe().lines().single { "log chờ gửi" in it }
        assertTrue("phai noi dang dem, thuc te: $counting", counting.contains("đang đếm"))
        assertFalse("khong duoc in -1, thuc te: $counting", counting.contains("-1"))

        val counted = r.copy(queuedDecisionLogs = 3).describe().lines().single { "log chờ gửi" in it }
        assertTrue("da dem xong thi in so ban ghi, thuc te: $counted", counted.contains("3 bản ghi"))
    }

    // ═══ Consent ═══

    @Test
    fun `canh bao khi chua chay luong consent`() {
        val r = MonetizationSdk.applyForTest(MonetizationSdk.Config())
        assertTrue(r.warnings.any { "consent" in it.lowercase() })
    }

    @Test
    fun `tat duoc canh bao consent khi studio tu lo`() {
        val r = MonetizationSdk.applyForTest(
            MonetizationSdk.Config().apply { consentHandledExternally = true; childDirected = false }
        )
        assertFalse(r.warnings.any { "consent" in it.lowercase() })
    }

    @Test
    fun `canh bao khi chua khai bao doi tuong tre em`() {
        // SDK không tự đặt cờ COPPA thay studio, nên phải nói rõ là chưa ai khai.
        val r = MonetizationSdk.applyForTest(MonetizationSdk.Config())
        assertTrue(r.warnings.any { "coppa" in it.lowercase() || "childDirected" in it })
    }

    @Test
    fun `khai bao tre em di toi dung dich`() {
        MonetizationSdk.applyForTest(
            MonetizationSdk.Config().apply { childDirected = true; underAgeOfConsent = true }
        )
        assertEquals(true, ConsentState.childDirected)
        assertEquals(true, ConsentState.underAgeOfConsent)
    }

    @Test
    fun `bao cao neu ro tang consent`() {
        ConsentState.setTier(ConsentTier.DENIED)
        val text = MonetizationSdk.applyForTest(MonetizationSdk.Config()).describe()
        assertTrue(text.contains("DENIED"))
        assertTrue("phai noi ro guardrail van chay", text.contains("guardrail"))
    }

    // ═══ GPP: báo cáo nói rõ khi opt-out bang Mỹ đang áp (1.6.0 Task 8a, O2) ═══

    private fun reportWithAppliedGpp(gpp: String?): MonetizationSdk.Report {
        AdSdkStartup.appliedGpp = GppConsent.decode(gpp, "7")
        try {
            return MonetizationSdk.applyForTest(MonetizationSdk.Config().apply { childDirected = false })
        } finally {
            AdSdkStartup.appliedGpp = null
        }
    }

    @Test
    fun `GPP US opt-out dang ap thi bao cao co canh bao GPP`() {
        val r = reportWithAppliedGpp(GPP_US_OPT_OUT)

        assertTrue("thiếu cảnh báo GPP: ${r.warnings}", r.warnings.any { it.startsWith("GPP: US opt-out đang áp") })
    }

    @Test
    fun `GPP khong opt-out hoac khong co GPP thi khong co canh bao GPP`() {
        assertFalse(reportWithAppliedGpp(GPP_US_OK).warnings.any { it.startsWith("GPP:") })
        assertFalse(reportWithAppliedGpp(null).warnings.any { it.startsWith("GPP:") })
    }
}
