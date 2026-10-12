package com.app.plugin.iap

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Vong doi subscription: phan loai trang thai, dunning cadence, win-back (doc 23 muc C.3).
 *
 * ═══ TINH CHAT TRUNG TAM ═══
 *
 * 1. Trang thai chi duoc suy ra tu su that: 2 su that cua store (purchaseState, isAutoRenewing)
 *    + 1 su that cuc bo (lich su "da tung co sub"). Khong co su that thi khong ket luan (NONE).
 * 2. LAPSED chi duoc suy ra khi observe DAY DU (complete=true) — mot luot cap nhat nhanh
 *    (complete=false) khong duoc bien moi san pham vang mat thanh churn.
 * 3. Episode grace ket thuc thi lich nhac dunning dat lai — khong bao gio nhac lai lich cu.
 */
class SubscriptionLifecycleTest {

    private fun snap(
        pid: String,
        state: IapPurchaseState = IapPurchaseState.PURCHASED,
        autoRenew: Boolean = true
    ) = SubscriptionLifecycle.SubSnapshot(pid, state, autoRenew)

    /**
     * 1.8.0 Task 5 (D4): PENDING không còn dẫn vào grace; IN_GRACE_PERIOD chỉ tới từ tín hiệu server (T10). Các test của
     * episode grace/dunning vào grace qua `applyState` (private) — cùng đường chuyển trạng thái, cùng event, cùng lịch sử.
     */
    private fun grace(pid: String): List<SubscriptionLifecycle.Transition> {
        val t = mutableListOf<SubscriptionLifecycle.Transition>()
        SubscriptionLifecycle::class.java.getDeclaredMethod(
            "applyState", String::class.java, SubState::class.java, MutableList::class.java
        ).apply { isAccessible = true }.invoke(SubscriptionLifecycle, pid, SubState.IN_GRACE_PERIOD, t)
        return t
    }

    @Before
    fun setUp() {
        SubscriptionLifecycle.clear()
    }

    @After
    fun tearDown() {
        SubscriptionLifecycle.clear()
        SubscriptionServerState.clock = System::currentTimeMillis
    }

    private val day = 24 * 3_600_000L
    private val t0 = 1_800_000_000_000L

    /** Đồng hồ cố định cho cửa sổ 7 ngày và lúc client đổi trạng thái (D27). */
    private fun at(ms: Long) { SubscriptionServerState.clock = { ms } }

    // ═══ Trang thai ═══

    @Test
    fun `purchased va auto renew la ACTIVE`() {
        SubscriptionLifecycle.observe(listOf(snap("vip_monthly")))
        assertEquals(SubState.ACTIVE, SubscriptionLifecycle.stateFor("vip_monthly"))
    }

    @Test
    fun `purchased nhung khong auto renew la CANCELLED`() {
        SubscriptionLifecycle.observe(listOf(snap("vip_monthly", autoRenew = false)))
        assertEquals(SubState.CANCELLED, SubscriptionLifecycle.stateFor("vip_monthly"))
    }

    /** 1.8.0 Task 5 (D4, đảo nghĩa): giao dịch chờ trả tiền không phải grace — không đổi trạng thái. */
    @Test
    fun `pending khong doi trang thai (D4)`() {
        SubscriptionLifecycle.observe(
            listOf(snap("vip_monthly", state = IapPurchaseState.PENDING, autoRenew = false))
        )
        assertEquals(SubState.NONE, SubscriptionLifecycle.stateFor("vip_monthly"))
    }

    @Test
    fun `san pham chua tung thay la NONE`() {
        SubscriptionLifecycle.observe(listOf(snap("vip_monthly")))
        assertEquals(SubState.NONE, SubscriptionLifecycle.stateFor("san_pham_la"))
    }

    @Test
    fun `unspecified khong du su that de phan loai`() {
        SubscriptionLifecycle.observe(listOf(snap("vip_monthly", state = IapPurchaseState.UNSPECIFIED)))
        assertEquals(SubState.NONE, SubscriptionLifecycle.stateFor("vip_monthly"))
    }

    // ═══ LAPSED — phat hien churn ═══

    @Test
    fun `tung co sub ma store khong tra ve nua la LAPSED`() {
        SubscriptionLifecycle.observe(listOf(snap("vip_monthly")))
        // Lan dong bo ke tiep: store khong con tra ve sub nao.
        SubscriptionLifecycle.observe(emptyList())
        assertEquals(SubState.LAPSED, SubscriptionLifecycle.stateFor("vip_monthly"))
    }

    @Test
    fun `chua tung co sub thi vang mat van la NONE`() {
        SubscriptionLifecycle.observe(emptyList())
        assertEquals(SubState.NONE, SubscriptionLifecycle.stateFor("vip_monthly"))
    }

    @Test
    fun `lich su mot khi da vao thi khong ra`() {
        SubscriptionLifecycle.observe(listOf(snap("vip_monthly")))
        SubscriptionLifecycle.observe(emptyList()) // → LAPSED
        SubscriptionLifecycle.observe(emptyList()) // van LAPSED, khong roi ve NONE
        assertEquals(SubState.LAPSED, SubscriptionLifecycle.stateFor("vip_monthly"))
        assertTrue(SubscriptionLifecycle.isWinBackEligible("vip_monthly"))
    }

    @Test
    fun `observe khong day du khong suy ra LAPSED cho san pham vang mat`() {
        SubscriptionLifecycle.observe(listOf(snap("vip_monthly"), snap("vip_yearly")))
        // Cap nhat nhanh chi co 1 san pham (vi du ngay sau luot mua) — san pham kia khong
        // duoc bi bien thanh churn.
        SubscriptionLifecycle.observe(listOf(snap("vip_monthly")), complete = false)
        assertEquals(SubState.ACTIVE, SubscriptionLifecycle.stateFor("vip_yearly"))
    }

    // ═══ Chuyen doi ═══

    @Test
    fun `chuyen doi duoc tra ve dung cap from to`() {
        val t1 = SubscriptionLifecycle.observe(listOf(snap("vip_monthly")))
        assertEquals(1, t1.size)
        assertEquals(SubState.NONE, t1[0].from)
        assertEquals(SubState.ACTIVE, t1[0].to)

        val t2 = SubscriptionLifecycle.observe(listOf(snap("vip_monthly")))
        assertTrue("trang thai khong doi thi khong co chuyen doi", t2.isEmpty())
    }

    @Test
    fun `grace period tu server giu qua restore client, server hoi phuc phat hai chuyen doi`() {
        // D27 (R-10): grace chỉ tới từ server (D4); Play báo sub trong grace như PURCHASED + autoRenewing.
        at(t0)
        SubscriptionLifecycle.observe(listOf(snap("vip_monthly")))
        at(t0 + 1_000)
        assertTrue(SubscriptionServerState.applyServer("vip_monthly", "GRACE", t0 + 500))
        assertEquals(SubState.IN_GRACE_PERIOD, SubscriptionLifecycle.stateFor("vip_monthly"))

        val restore = SubscriptionLifecycle.observe(listOf(snap("vip_monthly")))
        assertTrue("restore client không kết thúc grace server còn tươi", restore.isEmpty())
        assertEquals(SubState.IN_GRACE_PERIOD, SubscriptionLifecycle.stateFor("vip_monthly"))

        assertTrue(SubscriptionServerState.applyServer("vip_monthly", "RECOVERED", t0 + 2_000))
        assertEquals(SubState.ACTIVE, SubscriptionLifecycle.stateFor("vip_monthly"))
        assertTrue(SubscriptionLifecycle.observe(listOf(snap("vip_monthly"))).isEmpty())
    }

    @Test
    fun `nhan server qua 7 ngay thi quan sat client thang`() {
        at(t0)
        SubscriptionLifecycle.observe(listOf(snap("vip_monthly")))
        assertTrue(SubscriptionServerState.applyServer("vip_monthly", "GRACE", t0 + 1_000))
        at(t0 + 1_000 + 8 * day)
        val t = SubscriptionLifecycle.observe(listOf(snap("vip_monthly")))
        assertEquals("nhãn server hết cửa sổ: client thắng", SubState.ACTIVE, SubscriptionLifecycle.stateFor("vip_monthly"))
        assertEquals(listOf(SubState.IN_GRACE_PERIOD to SubState.ACTIVE), t.map { it.from to it.to })
    }

    @Test
    fun `store khong tra ve sub thi LAPSED du nhan server con tuoi`() {
        at(t0)
        SubscriptionLifecycle.observe(listOf(snap("vip_monthly")))
        assertTrue(SubscriptionServerState.applyServer("vip_monthly", "GRACE", t0 + 1_000))
        at(t0 + 2_000)
        SubscriptionLifecycle.observe(emptyList(), complete = true)
        assertEquals(SubState.LAPSED, SubscriptionLifecycle.stateFor("vip_monthly"))
    }

    @Test
    fun `ON_HOLD tu server giu LAPSED qua restore client`() {
        at(t0)
        SubscriptionLifecycle.observe(listOf(snap("vip_monthly")))
        assertTrue(SubscriptionServerState.applyServer("vip_monthly", "ON_HOLD", t0 + 1_000))
        at(t0 + 2_000)
        assertTrue(SubscriptionLifecycle.observe(listOf(snap("vip_monthly"))).isEmpty())
        assertEquals(SubState.LAPSED, SubscriptionLifecycle.stateFor("vip_monthly"))
        assertEquals("ON_HOLD", SubscriptionServerState.stateFor("vip_monthly"))
    }

    @Test
    fun `tat enabled thi nhan server cung bi bo`() {
        assertTrue(SubscriptionLifecycle.apply("""{"version": "sl_off", "enabled": false}""").applied)
        assertTrue("trạng thái gốc vẫn ghi", SubscriptionServerState.applyServer("vip_monthly", "GRACE", t0))
        assertTrue(SubscriptionLifecycle.apply("""{"version": "sl_on", "enabled": true}""").applied)
        assertEquals("nhãn không đặt khi đang tắt", SubState.NONE, SubscriptionLifecycle.stateFor("vip_monthly"))
        assertFalse("bật lại không áp lại cùng tín hiệu", SubscriptionServerState.applyServer("vip_monthly", "GRACE", t0))
    }

    @Test
    fun `huy roi churn la hai chuyen doi rieng`() {
        SubscriptionLifecycle.observe(listOf(snap("vip_monthly")))
        val t1 = SubscriptionLifecycle.observe(listOf(snap("vip_monthly", autoRenew = false)))
        assertEquals(SubState.CANCELLED, t1[0].to)
        val t2 = SubscriptionLifecycle.observe(emptyList())
        assertEquals(SubState.CANCELLED, t2[0].from)
        assertEquals(SubState.LAPSED, t2[0].to)
    }

    @Test
    fun `product id rong bi bo qua`() {
        val t = SubscriptionLifecycle.observe(listOf(snap("  ")))
        assertTrue(t.isEmpty())
    }

    // ═══ Win-back ═══

    @Test
    fun `win back chi danh cho nguoi da churn`() {
        SubscriptionLifecycle.observe(listOf(snap("vip_monthly")))
        assertFalse(SubscriptionLifecycle.isWinBackEligible("vip_monthly"))
        SubscriptionLifecycle.observe(emptyList())
        assertTrue(SubscriptionLifecycle.isWinBackEligible("vip_monthly"))
    }

    @Test
    fun `winBackEligibleProductIds chi chua san pham LAPSED`() {
        SubscriptionLifecycle.observe(listOf(snap("vip_monthly"), snap("vip_yearly")))
        SubscriptionLifecycle.observe(listOf(snap("vip_yearly")))
        assertEquals(setOf("vip_monthly"), SubscriptionLifecycle.winBackEligibleProductIds())
    }

    @Test
    fun `mua lai lap tuc ra khoi LAPSED`() {
        SubscriptionLifecycle.observe(listOf(snap("vip_monthly")))
        SubscriptionLifecycle.observe(emptyList()) // → LAPSED
        SubscriptionLifecycle.observe(listOf(snap("vip_monthly")), complete = false)
        assertEquals(SubState.ACTIVE, SubscriptionLifecycle.stateFor("vip_monthly"))
        assertFalse("nguoi vua tai dang ky khong con la win-back target",
            SubscriptionLifecycle.isWinBackEligible("vip_monthly"))
    }

    // ═══ Dunning cadence ═══

    @Test
    fun `dunning chi dung luot theo mac dinh 1 2 4`() {
        grace("vip_monthly")
        assertTrue("luot 1", SubscriptionLifecycle.dunningNoticeDue("vip_monthly"))
        assertTrue("luot 2", SubscriptionLifecycle.dunningNoticeDue("vip_monthly"))
        assertFalse("luot 3", SubscriptionLifecycle.dunningNoticeDue("vip_monthly"))
        assertTrue("luot 4", SubscriptionLifecycle.dunningNoticeDue("vip_monthly"))
        assertFalse("luot 5 — het lich, khong nhac mai", SubscriptionLifecycle.dunningNoticeDue("vip_monthly"))
    }

    @Test
    fun `dunning khong nhac khi khong trong grace period`() {
        SubscriptionLifecycle.observe(listOf(snap("vip_monthly"))) // ACTIVE
        assertFalse(SubscriptionLifecycle.dunningNoticeDue("vip_monthly"))
    }

    @Test
    fun `lich nhac dat lai khi episode grace ket thuc`() {
        grace("vip_monthly")
        assertTrue(SubscriptionLifecycle.dunningNoticeDue("vip_monthly"))
        // Hoi phuc...
        SubscriptionLifecycle.observe(listOf(snap("vip_monthly")))
        // ...roi fail lai — episode moi, lich dem lai tu 1.
        grace("vip_monthly")
        assertTrue("episode moi phai nhac ngay luot 1", SubscriptionLifecycle.dunningNoticeDue("vip_monthly"))
    }

    // ═══ Cau hinh ═══

    @Test
    fun `chua nap cau hinh van chay voi mac dinh`() {
        assertEquals("chưa nạp", SubscriptionLifecycle.activeVersion())
        val t = SubscriptionLifecycle.observe(listOf(snap("vip_monthly")))
        assertEquals(1, t.size)
    }

    @Test
    fun `cau hinh rong hoac hong bi tu choi`() {
        assertFalse(SubscriptionLifecycle.apply(null).applied)
        assertFalse(SubscriptionLifecycle.apply("   ").applied)
        assertFalse(SubscriptionLifecycle.apply("{json hong").applied)
        assertFalse(SubscriptionLifecycle.apply("""{"enabled": true}""").applied)
    }

    @Test
    fun `cau hinh hong giu nguyen ban dang chay`() {
        assertTrue(SubscriptionLifecycle.apply(
            """{"version": "sl_v1", "enabled": true}"""
        ).applied)
        val r = SubscriptionLifecycle.apply("{hong")
        assertFalse(r.applied)
        assertTrue("report phai noi ro ly do tu choi", r.report.contains("từ chối"))
        assertEquals("sl_v1", SubscriptionLifecycle.activeVersion())
    }

    @Test
    fun `tat enabled thi ngung phan loai va ngung moi thu`() {
        SubscriptionLifecycle.observe(listOf(snap("vip_monthly")))
        assertTrue(SubscriptionLifecycle.apply(
            """{"version": "sl_v2", "enabled": false}"""
        ).applied)
        // observe khong con tac dung, doc tra ve NONE nhu the tinh nang khong ton tai.
        val t = SubscriptionLifecycle.observe(
            listOf(snap("vip_monthly", state = IapPurchaseState.PENDING, autoRenew = false))
        )
        assertTrue(t.isEmpty())
        assertEquals(SubState.NONE, SubscriptionLifecycle.stateFor("vip_monthly"))
        assertFalse(SubscriptionLifecycle.dunningNoticeDue("vip_monthly"))
        assertTrue(SubscriptionLifecycle.winBackEligibleProductIds().isEmpty())
    }

    @Test
    fun `bat lai sau khi tat thi phan loai lai binh thuong`() {
        assertTrue(SubscriptionLifecycle.apply("""{"version": "sl_v3", "enabled": false}""").applied)
        assertTrue(SubscriptionLifecycle.apply("""{"version": "sl_v4", "enabled": true}""").applied)
        val t = SubscriptionLifecycle.observe(listOf(snap("vip_monthly")))
        assertEquals(1, t.size)
    }

    @Test
    fun `cadence rong la co y khong nhac`() {
        val r = SubscriptionLifecycle.apply(
            """{"version": "sl_v5", "dunning_cadence": []}"""
        )
        assertTrue(r.applied)
        assertTrue("phai chu thich cadence rong", r.report.contains("cadence rỗng"))
        grace("vip_monthly")
        assertFalse(SubscriptionLifecycle.dunningNoticeDue("vip_monthly"))
    }

    @Test
    fun `cadence tuy chinh duoc ton trong`() {
        assertTrue(SubscriptionLifecycle.apply(
            """{"version": "sl_v6", "dunning_cadence": [3, 1]}"""
        ).applied)
        grace("vip_monthly")
        assertTrue("luot 1 (da sort)", SubscriptionLifecycle.dunningNoticeDue("vip_monthly"))
        assertFalse("luot 2", SubscriptionLifecycle.dunningNoticeDue("vip_monthly"))
        assertTrue("luot 3", SubscriptionLifecycle.dunningNoticeDue("vip_monthly"))
    }

    @Test
    fun `cadence chua so bi tu choi`() {
        assertFalse(SubscriptionLifecycle.apply(
            """{"version": "sl_v7", "dunning_cadence": [1, "hai"]}"""
        ).applied)
    }

    @Test
    fun `tat win back thi LAPSED van duoc phan loai nhung khong con la target`() {
        assertTrue(SubscriptionLifecycle.apply(
            """{"version": "sl_v8", "win_back_enabled": false}"""
        ).applied)
        SubscriptionLifecycle.observe(listOf(snap("vip_monthly")))
        SubscriptionLifecycle.observe(emptyList())
        assertEquals(SubState.LAPSED, SubscriptionLifecycle.stateFor("vip_monthly"))
        assertFalse(SubscriptionLifecycle.isWinBackEligible("vip_monthly"))
        assertTrue(SubscriptionLifecycle.winBackEligibleProductIds().isEmpty())
    }

    // ═══ URI quan ly subscription ═══

    @Test
    fun `management uri dung dinh dang va encode tham so`() {
        val uri = SubscriptionLifecycle.managementUriFor("vip monthly+", "com.example.app")
        assertEquals(
            "https://play.google.com/store/account/subscriptions" +
                "?sku=vip+monthly%2B&package=com.example.app",
            uri
        )
    }

    @Test
    fun `management uri tu choi tham so rong`() {
        assertNull(SubscriptionLifecycle.managementUriFor("", "com.example.app"))
        assertNull(SubscriptionLifecycle.managementUriFor("vip_monthly", " "))
    }

    // ═══ Reset ═══

    @Test
    fun `clear xoa trang thai lich su va bo dem dem`() {
        SubscriptionLifecycle.observe(listOf(snap("vip_monthly")))
        SubscriptionLifecycle.clear()
        assertEquals(SubState.NONE, SubscriptionLifecycle.stateFor("vip_monthly"))
        assertFalse(SubscriptionLifecycle.isWinBackEligible("vip_monthly"))
        assertEquals("chưa nạp", SubscriptionLifecycle.activeVersion())
    }
}
