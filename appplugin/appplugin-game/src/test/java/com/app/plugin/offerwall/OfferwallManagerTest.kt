package com.app.plugin.offerwall

import com.app.plugin.consent.ConsentState
import com.app.plugin.consent.ConsentTier
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Lớp adapter offerwall — bề mặt monetization thứ tư sau IAP, interstitial, rewarded
 * (doc 20, doc 23 mục C.4).
 *
 * ═══ TÍNH CHẤT TRUNG TÂM ═══
 *
 * 1. Provider là interface do game implement — SDK không phụ thuộc vendor SDK.
 * 2. Đóng tường ≠ hoàn thành offer: show() chỉ ghi impression, thưởng về qua reportCompletion.
 * 3. SDK không trao thưởng — chống trùng (provider, transaction) rồi trả dữ liệu cho game.
 * 4. Gate: consent DENIED chặn, UNKNOWN KHÔNG chặn, childDirected chặn (COPPA).
 * 5. Kill switch chặn lượt MỞ mới, không quỵt thưởng của việc đã hoàn thành.
 */
class OfferwallManagerTest {

    /** Adapter giả — không đụng vendor SDK thật. */
    private class FakeProvider(
        override val providerId: String,
        private var available: Boolean = true
    ) : OfferwallManager.OfferwallProvider {
        var showCount = 0
        var lastCloseCallback: (() -> Unit)? = null
        override fun isAvailable(): Boolean = available
        override fun show(onWallClosed: () -> Unit) {
            showCount++
            lastCloseCallback = onWallClosed
        }
        fun setAvailable(v: Boolean) { available = v }
    }

    companion object {
        fun json(version: String = "ow_v1", enabled: Boolean = true, providers: String = ""): String = """
            {
              "version": "$version",
              "enabled": $enabled$providers
            }
        """.trimIndent()
    }

    @Before
    fun setUp() {
        OfferwallManager.clear()
        ConsentState.resetForTest()
    }

    @After
    fun tearDown() {
        OfferwallManager.clear()
        ConsentState.resetForTest()
    }

    // ═══ Cấu hình ═══

    @Test
    fun `chua nap cau hinh thi khong mo duoc tuong`() {
        assertEquals("chưa nạp", OfferwallManager.activeVersion())
        assertTrue(OfferwallManager.availableProviders().isEmpty())
        val r = OfferwallManager.show("revu")
        assertFalse(r.shown)
        assertEquals("chưa nạp cấu hình", r.report)
    }

    @Test
    fun `apply thanh cong khong danh sach thi cho phep moi provider da dang ky`() {
        val r = OfferwallManager.apply(json())
        assertTrue(r.applied)
        assertEquals("ow_v1", OfferwallManager.activeVersion())
        assertTrue(r.report.contains("cho phép mọi provider đã đăng ký"))
    }

    @Test
    fun `apply thieu version bi tu choi`() {
        // Không có version thì không biết đang chạy bản nào — từ chối, giữ "chưa nạp".
        val r = OfferwallManager.apply("""{"enabled": true}""")
        assertFalse(r.applied)
        assertEquals("chưa nạp", OfferwallManager.activeVersion())
        assertTrue(r.report.contains("thiếu trường version"))
    }

    @Test
    fun `apply JSON hong giu nguyen ban dang chay`() {
        OfferwallManager.apply(json())
        val r = OfferwallManager.apply("{json hỏng")
        assertFalse(r.applied)
        assertEquals("ow_v1", OfferwallManager.activeVersion())
        assertTrue(r.report.startsWith("từ chối"))
    }

    @Test
    fun `kill switch tat chan mo tuong va danh sach kha dung`() {
        OfferwallManager.apply(json(enabled = false))
        OfferwallManager.register(FakeProvider("revu"))
        assertTrue(OfferwallManager.availableProviders().isEmpty())
        val r = OfferwallManager.show("revu")
        assertFalse(r.shown)
        assertEquals("tính năng đang tắt", r.report)
    }

    // ═══ Đăng ký provider ═══

    @Test
    fun `register trung id thay the adapter cu`() {
        val old = FakeProvider("revu")
        val neu = FakeProvider("revu")
        assertTrue(OfferwallManager.register(old))
        assertTrue(OfferwallManager.register(neu))
        OfferwallManager.apply(json())
        assertEquals(listOf<OfferwallManager.OfferwallProvider>(neu), OfferwallManager.availableProviders())
    }

    @Test
    fun `register id trong bi tu choi`() {
        assertFalse(OfferwallManager.register(FakeProvider("   ")))
    }

    @Test
    fun `unregister loai provider khoi danh sach`() {
        OfferwallManager.apply(json())
        OfferwallManager.register(FakeProvider("revu"))
        OfferwallManager.unregister("revu")
        assertTrue(OfferwallManager.availableProviders().isEmpty())
    }

    @Test
    fun `availableProviders loc theo allowlist va kha dung`() {
        val allowed = FakeProvider("revu")
        val notAllowed = FakeProvider("adjoe")
        val unavailable = FakeProvider("torox", available = false)
        OfferwallManager.register(allowed)
        OfferwallManager.register(notAllowed)
        OfferwallManager.register(unavailable)
        OfferwallManager.apply(json(providers = """, "providers": ["revu", "torox"]"""))
        // adjoe ngoài allow-list, torox trong allow-list nhưng vendor chưa sẵn sàng.
        assertEquals(listOf<OfferwallManager.OfferwallProvider>(allowed), OfferwallManager.availableProviders())
    }

    // ═══ Mở tường ═══

    @Test
    fun `show mo duoc khi moi gate qua`() {
        OfferwallManager.apply(json())
        val p = FakeProvider("revu")
        OfferwallManager.register(p)
        var closed = false
        val r = OfferwallManager.show("revu") { closed = true }
        assertTrue(r.shown)
        assertEquals(1, p.showCount)
        p.lastCloseCallback!!.invoke()
        assertTrue(closed)
    }

    @Test
    fun `show provider chua dang ky bi chan voi ly do ro rang`() {
        OfferwallManager.apply(json())
        val r = OfferwallManager.show("adjoe")
        assertFalse(r.shown)
        assertEquals("chưa đăng ký provider adjoe", r.report)
    }

    @Test
    fun `show provider khong kha dung bi chan`() {
        OfferwallManager.apply(json())
        val p = FakeProvider("revu", available = false)
        OfferwallManager.register(p)
        val r = OfferwallManager.show("revu")
        assertFalse(r.shown)
        assertEquals("provider revu không khả dụng", r.report)
        assertEquals(0, p.showCount)
    }

    // ═══ Gate consent ═══

    @Test
    fun `consent DENIED chan mo tuong`() {
        // Vendor SDK thu thập dữ liệu thiết bị cho fraud/analytics — cần consent (GDPR).
        OfferwallManager.apply(json())
        OfferwallManager.register(FakeProvider("revu"))
        ConsentState.setTier(ConsentTier.DENIED)
        val r = OfferwallManager.show("revu")
        assertFalse(r.shown)
        assertEquals("consent bị từ chối — không mở offerwall", r.report)
    }

    @Test
    fun `consent UNKNOWN khong chan vi chua hoi khac voi tu choi`() {
        OfferwallManager.apply(json())
        val p = FakeProvider("revu")
        OfferwallManager.register(p)
        assertTrue(OfferwallManager.show("revu").shown)
        assertEquals(1, p.showCount)
    }

    @Test
    fun `app huong toi tre em chan ca khi consent GRANTED`() {
        // COPPA: tuyên bố pháp lý của studio, SDK tôn trọng tuyệt đối.
        OfferwallManager.apply(json())
        OfferwallManager.register(FakeProvider("revu"))
        ConsentState.setTier(ConsentTier.GRANTED)
        ConsentState.childDirected = true
        val r = OfferwallManager.show("revu")
        assertFalse(r.shown)
        assertEquals("app hướng tới trẻ em — offerwall bị chặn (COPPA)", r.report)
    }

    @Test
    fun `allowlist chan provider ngoai danh sach`() {
        OfferwallManager.apply(json(providers = """, "providers": ["revu"]"""))
        OfferwallManager.register(FakeProvider("adjoe"))
        val r = OfferwallManager.show("adjoe")
        assertFalse(r.shown)
        assertEquals("provider adjoe không nằm trong danh sách cho phép", r.report)
    }

    // ═══ Hoàn thành offer ═══

    @Test
    fun `completion trung transaction bi tu choi`() {
        OfferwallManager.apply(json())
        val first = OfferwallManager.reportCompletion("revu", 120, "tx_001")
        assertTrue(first.accepted)
        assertEquals(120L, first.currencyEarned)
        val dup = OfferwallManager.reportCompletion("revu", 120, "tx_001")
        assertFalse(dup.accepted)
        assertTrue(dup.report.contains("trùng transaction"))
        // Nhưng cùng tx của provider KHÁC là giao dịch khác — không được gộp.
        assertTrue(OfferwallManager.reportCompletion("adjoe", 80, "tx_001").accepted)
    }

    @Test
    fun `completion khong co transaction van duoc nhan nhung canh bao`() {
        // Mất thưởng thật tệ hơn trùng thưởng — nhưng phải nói rõ là không chống trùng được.
        val r = OfferwallManager.reportCompletion("revu", 50, null)
        assertTrue(r.accepted)
        assertEquals(50L, r.currencyEarned)
        assertTrue(r.report.contains("không chống trùng được"))
    }

    @Test
    fun `completion du lieu sai bi tu choi`() {
        assertFalse(OfferwallManager.reportCompletion("", 10, "tx").accepted)
        assertFalse(OfferwallManager.reportCompletion("revu", -1, "tx").accepted)
        assertEquals("currency âm", OfferwallManager.reportCompletion("revu", -1, "tx").report)
    }

    @Test
    fun `tinh nang tat van ghi nhan viec da hoan thanh`() {
        // Kill switch chặn lượt MỞ mới — từ chối trả công cho việc đã làm xong là quỵt người chơi.
        OfferwallManager.apply(json())
        assertTrue(OfferwallManager.reportCompletion("revu", 100, "tx_before").accepted)
        OfferwallManager.apply(json(enabled = false))
        assertFalse(OfferwallManager.show("revu").shown)
        val r = OfferwallManager.reportCompletion("revu", 100, "tx_after_shutdown")
        assertTrue(r.accepted)
        assertEquals(100L, r.currencyEarned)
    }

    @Test
    fun `clear dat lai trang thai`() {
        OfferwallManager.apply(json())
        OfferwallManager.register(FakeProvider("revu"))
        OfferwallManager.reportCompletion("revu", 10, "tx_1")
        OfferwallManager.clear()
        assertEquals("chưa nạp", OfferwallManager.activeVersion())
        assertTrue(OfferwallManager.availableProviders().isEmpty())
        // seenTx đã xoá — transaction cũ nhận lại được (cùng hạng rủi ro xoá app, chấp nhận).
        assertTrue(OfferwallManager.reportCompletion("revu", 10, "tx_1").accepted)
    }
}
