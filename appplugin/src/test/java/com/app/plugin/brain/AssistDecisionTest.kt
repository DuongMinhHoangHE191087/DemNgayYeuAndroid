package com.app.plugin.brain

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Full action space — hai mode cuối của action space gốc (doc 01) được nối dây thật:
 * FREE_ACTION (tặng hint/boost miễn phí) và GAMEPLAY_CONFIG (giảm độ khó) qua cổng
 * [AdsBrain.chooseAssist] (doc 23 Phase 5, doc 24 mục C, doc 58).
 *
 * ═══ TÍNH CHẤT TRUNG TÂM ═══
 *
 * 1. Tắt brain hoặc ngoài rollout → trả baseline của game, không ép về NO_ACTION.
 * 2. Holdout nhận baseline với xác suất 1.0 và được đánh cờ.
 * 3. Loại hỗ trợ bị game cấm (allow*=false) không bao giờ xuất hiện trong kết quả.
 * 4. KHÔNG BAO GIỜ có IAP_OFFER trong không gian này — cứu người bế tắc bằng thứ
 *    phải trả tiền là khai thác frustration (ràng buộc đạo đức cốt lõi, doc 29).
 * 5. Trần ASSIST_SESSION_CAP chặn cả hai mode hỗ trợ khi quá ngân sách phiên,
 *    nhưng KHÔNG chặn NO_ACTION; artifact chỉ được kẹp trần này xuống, không nâng
 *    vượt HARD_MAX_ASSISTS_PER_SESSION.
 * 6. Khoá click-fraud (bảo vệ kho quảng cáo) KHÔNG chặn quà tặng / chỉnh độ khó.
 * 7. Feedback qua đúng đường ống treo: onAssistShown/onAssistUsed chốt đúng quyết định.
 */
class AssistDecisionTest {
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
        AdsBrain.rolloutFraction = 1.0
        HoldoutManager.holdoutFraction = 0.05
        BrainPolicy.clear()
        DecisionEngine.instance.guardrails.maxAssistsPerSession = 3
        // Singleton dùng chung giữa các test — trả về mặc định.
        ClickFraudGuard.maxClicksPerWindow = 6
        ClickFraudGuard.windowMs = 300_000L
        ClickFraudGuard.lockoutMs = 1_800_000L
    }

    // ═══ Tắt = hành vi cũ ═══

    @Test
    fun `tat brain tra baseline mac dinh`() {
        AdsBrain.enabled = false
        AdsBrain.onSessionStart("a_off_${System.nanoTime()}")
        val d = AdsBrain.chooseAssist(DecisionPoint.LEVEL_FAIL)
        assertEquals(ActionMode.NO_ACTION, d.action)
        assertFalse(d.isHoldout)
    }

    @Test
    fun `tat brain tra baseline khac NO_ACTION cua game`() {
        // Game có hành vi mặc định riêng (ví dụ luôn tặng hint khi thua) — tắt brain
        // phải giữ đúng hành vi đó, không được ép về NO_ACTION.
        AdsBrain.enabled = false
        AdsBrain.onSessionStart("a_off2_${System.nanoTime()}")
        val d = AdsBrain.chooseAssist(DecisionPoint.LEVEL_FAIL, baseline = ActionMode.FREE_ACTION)
        assertEquals(ActionMode.FREE_ACTION, d.action)
    }

    @Test
    fun `ngoai rollout tra baseline cua game`() {
        AdsBrain.enabled = true
        AdsBrain.rolloutFraction = 0.0
        AdsBrain.onSessionStart("a_noroll_${System.nanoTime()}")
        val d = AdsBrain.chooseAssist(DecisionPoint.CURRENCY_LOW, baseline = ActionMode.GAMEPLAY_CONFIG)
        assertEquals(ActionMode.GAMEPLAY_CONFIG, d.action)
        assertFalse("ngoai rollout khong phai holdout", d.isHoldout)
    }

    @Test
    fun `ca hai loai ho tro bi cam thi tra baseline khong hoi engine`() {
        // Không có quyết định nào để ra — không ghi quyết định một arm vào log OPE.
        AdsBrain.enabled = true
        AdsBrain.onSessionStart("a_noarm_${System.nanoTime()}")
        val d = AdsBrain.chooseAssist(
            DecisionPoint.LEVEL_FAIL,
            allowFreeGift = false, allowDifficultyTuning = false,
            baseline = ActionMode.GAMEPLAY_CONFIG
        )
        assertEquals(ActionMode.GAMEPLAY_CONFIG, d.action)
        assertFalse(d.isHoldout)
    }

    // ═══ Holdout ═══

    @Test
    fun `holdout nhan baseline voi xac suat 1 va duoc danh co`() {
        AdsBrain.enabled = true
        HoldoutManager.holdoutFraction = 1.0
        AdsBrain.onSessionStart("a_hold_${System.nanoTime()}")
        repeat(10) {
            val d = AdsBrain.chooseAssist(DecisionPoint.LEVEL_FAIL, baseline = ActionMode.FREE_ACTION)
            assertEquals("holdout phai nhan baseline", ActionMode.FREE_ACTION, d.action)
            assertTrue(d.isHoldout)
        }
    }

    // ═══ Tập arm ═══

    @Test
    fun `loai ho tro bi cam khong bao gio xuat hien trong ket qua`() {
        AdsBrain.enabled = true
        AdsBrain.onSessionStart("a_subset_${System.nanoTime()}")
        val seen = mutableSetOf<ActionMode>()
        repeat(60) {
            seen += AdsBrain.chooseAssist(DecisionPoint.LEVEL_FAIL, allowFreeGift = false).action
        }
        assertTrue(
            "chi duoc chua NO_ACTION hoac GAMEPLAY_CONFIG: $seen",
            seen.all { it == ActionMode.NO_ACTION || it == ActionMode.GAMEPLAY_CONFIG }
        )
        val seen2 = mutableSetOf<ActionMode>()
        repeat(60) {
            seen2 += AdsBrain.chooseAssist(DecisionPoint.LEVEL_FAIL, allowDifficultyTuning = false).action
        }
        assertTrue(
            "chi duoc chua NO_ACTION hoac FREE_ACTION: $seen2",
            seen2.all { it == ActionMode.NO_ACTION || it == ActionMode.FREE_ACTION }
        )
    }

    @Test
    fun `khong gian ho tro khong bao gio chua IAP_OFFER`() {
        // Ràng buộc đạo đức cốt lõi: cứu người bế tắc bằng thứ PHẢI TRẢ TIỀN là khai thác
        // frustration. IAP_OFFER không tồn tại dưới dạng candidate ở cổng này — kể cả khi
        // đang bế tắc sâu, nơi đáng lẽ "bán rescue" hấp dẫn nhất.
        AdsBrain.enabled = true
        AdsBrain.onSessionStart("a_noiap_${System.nanoTime()}")
        repeat(6) { AdsBrain.onLevelFail() }
        val seen = mutableSetOf<ActionMode>()
        repeat(60) { seen += AdsBrain.chooseAssist(DecisionPoint.LEVEL_FAIL_STREAK).action }
        assertFalse("ho tro khong duoc ban: $seen", ActionMode.IAP_OFFER in seen)
        assertFalse("ho tro khong duoc la ad: $seen", ActionMode.REWARDED_AD in seen)
        assertFalse("ho tro khong duoc la ad: $seen", ActionMode.INTERSTITIAL in seen)
    }

    @Test
    fun `brain dang hoat dong co the chon ho tro that`() {
        // Sanity: với ngân sách còn đủ, trong 60 lượt phải thấy ít nhất một hỗ trợ thật —
        // nếu không cổng này chỉ trả NO_ACTION và Phase 5 vô nghĩa.
        AdsBrain.enabled = true
        AdsBrain.onSessionStart("a_live_${System.nanoTime()}")
        AdsBrain.currentState().assistsThisSession = -1000 // không bao giờ chạm trần
        val seen = mutableSetOf<ActionMode>()
        repeat(60) { seen += AdsBrain.chooseAssist(DecisionPoint.LEVEL_FAIL).action }
        assertNotEquals(
            "60 luot phai thay it nhat mot ho tro that: $seen",
            setOf(ActionMode.NO_ACTION), seen
        )
    }

    // ═══ Trần hỗ trợ phiên (ASSIST_SESSION_CAP) ═══

    @Test
    fun `vuot ngan sach ho tro phien chi tra NO_ACTION`() {
        AdsBrain.enabled = true
        AdsBrain.onSessionStart("a_cap_${System.nanoTime()}")
        AdsBrain.currentState().assistsThisSession =
            DecisionEngine.instance.guardrails.maxAssistsPerSession
        val seen = mutableSetOf<ActionMode>()
        repeat(60) { seen += AdsBrain.chooseAssist(DecisionPoint.LEVEL_FAIL).action }
        assertEquals(
            "cham tran ho tro thi chi con NO_ACTION: $seen",
            setOf(ActionMode.NO_ACTION), seen
        )
    }

    @Test
    fun `duoi ngan sach ho tro khong bi chan`() {
        // Ngay dưới trần: hỗ trợ vẫn được phép — trần chặn ở biên, không chặn sớm.
        AdsBrain.enabled = true
        AdsBrain.onSessionStart("a_undercap_${System.nanoTime()}")
        AdsBrain.currentState().assistsThisSession =
            DecisionEngine.instance.guardrails.maxAssistsPerSession - 1
        val seen = mutableSetOf<ActionMode>()
        repeat(60) { seen += AdsBrain.chooseAssist(DecisionPoint.CURRENCY_LOW).action }
        assertNotEquals(
            "ngay duoi tran van duoc ho tro: $seen",
            setOf(ActionMode.NO_ACTION), seen
        )
    }

    // ═══ Trần từ xa qua policy artifact — clamp-only ═══

    @Test
    fun `artifact khong the nang tran ho tro vuot tran cung`() {
        val g = DecisionEngine.instance.guardrails
        val r = BrainPolicy.apply(
            """{"version":"v","gameplay":{"max_assists_per_session":9999}}""", g
        )
        assertTrue(r.applied)
        assertEquals(
            "phai kep ve tran cung",
            BrainPolicy.HARD_MAX_ASSISTS_PER_SESSION, g.maxAssistsPerSession
        )
        assertTrue("phai bao cao viec da kep", r.report.contains("kẹp"))
    }

    @Test
    fun `tran ho tro tu artifact hop le duoc ap dung`() {
        val g = DecisionEngine.instance.guardrails
        BrainPolicy.apply(
            """{"version":"v","gameplay":{"max_assists_per_session":5}}""", g
        )
        assertEquals(5, g.maxAssistsPerSession)
    }

    // ═══ Click-fraud: khoá bảo vệ kho quảng cáo, không cấm tặng quà ═══

    @Test
    fun `khoa click-fraud khong chan ho tro gameplay`() {
        AdsBrain.enabled = true
        val pid = "a_fraud_${System.nanoTime()}"
        AdsBrain.onSessionStart(pid)
        ClickFraudGuard.maxClicksPerWindow = 2
        repeat(5) { ClickFraudGuard.registerClick(pid) }
        assertTrue("pid phai dang bi khoa ad", ClickFraudGuard.isLocked(pid))

        // Không được ném lỗi, và trong 60 lượt vẫn phải thấy lựa chọn hợp lệ
        // (NO_ACTION luôn hợp lệ; hỗ trợ thật phải xuất hiện vì miễn trừ).
        AdsBrain.currentState().assistsThisSession = -1000
        val seen = mutableSetOf<ActionMode>()
        repeat(60) { seen += AdsBrain.chooseAssist(DecisionPoint.LEVEL_FAIL).action }
        assertTrue(
            "khoa ad khong duoc chan ho tro: $seen",
            seen.any { it == ActionMode.FREE_ACTION || it == ActionMode.GAMEPLAY_CONFIG }
        )
    }

    // ═══ Feedback qua đường ống treo ═══

    @Test
    fun `onAssistShown tang bo dem dau vao cua tran`() {
        AdsBrain.enabled = true
        AdsBrain.onSessionStart("a_shown_${System.nanoTime()}")
        AdsBrain.chooseAssist(DecisionPoint.LEVEL_FAIL)
        AdsBrain.onAssistShown()
        AdsBrain.onAssistShown()
        assertEquals(2, AdsBrain.currentState().assistsThisSession)
    }

    @Test
    fun `onAssistShown khi brain tat khong tang bo dem`() {
        AdsBrain.enabled = false
        AdsBrain.onSessionStart("a_shownoff_${System.nanoTime()}")
        AdsBrain.onAssistShown()
        assertEquals(0, AdsBrain.currentState().assistsThisSession)
    }

    @Test
    fun `onSessionStart dat lai bo dem ho tro`() {
        AdsBrain.enabled = true
        val pid = "a_reset_${System.nanoTime()}"
        AdsBrain.onSessionStart(pid)
        AdsBrain.onAssistShown()
        AdsBrain.onAssistShown()
        AdsBrain.onSessionStart(pid)
        assertEquals("phien moi phai dem lai tu 0", 0, AdsBrain.currentState().assistsThisSession)
    }

    @Test
    fun `onAssistUsed true chot ngay khong nem loi`() {
        AdsBrain.enabled = true
        AdsBrain.onSessionStart("a_used_${System.nanoTime()}")
        var picked = false
        repeat(60) {
            if (!picked && AdsBrain.chooseAssist(DecisionPoint.LEVEL_FAIL).action != ActionMode.NO_ACTION) {
                picked = true
                AdsBrain.onAssistShown()
                AdsBrain.onAssistUsed(true) // chốt ngay như onRewardedOffered(true)
            }
        }
        assertTrue("60 luot phai co it nhat mot ho tro duoc chon", picked)
    }

    @Test
    fun `onAssistUsed false cho tin hieu o lai that`() {
        // Bị phớt lờ KHÔNG chốt âm ngay — quyết định treo được lượt hỏi kế tiếp chốt
        // (cùng đường với onAdSkipped). Không được ném lỗi qua cả hai lượt.
        AdsBrain.enabled = true
        AdsBrain.onSessionStart("a_ignored_${System.nanoTime()}")
        AdsBrain.chooseAssist(DecisionPoint.LEVEL_FAIL)
        AdsBrain.onAssistShown()
        AdsBrain.onAssistUsed(false)
        AdsBrain.chooseAssist(DecisionPoint.LEVEL_FAIL) // chốt quyết định treo của lượt trước
        AdsBrain.onLevelWin()
    }
}
