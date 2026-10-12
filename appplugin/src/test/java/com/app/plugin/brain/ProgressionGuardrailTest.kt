package com.app.plugin.brain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Guardrail theo tiến độ người chơi — chắt lọc từ teardown một game puzzle production
 * (Ghidra/IL2CPP export, 28 key Remote Config giá trị thật).
 *
 * Đây là những cơ chế đã chạy trên dân số thật, không phải suy luận thiết kế. Điểm chung của
 * chúng: **bảo vệ khoảnh khắc mong manh**. Người mới chưa gắn bó, người vừa thua đang bực, và
 * người vừa mở lại app chưa kịp vào nhịp — ba thời điểm mà một quảng cáo có giá trị tức thì
 * gần như nhau nhưng chi phí retention chênh nhau rất xa.
 */
class ProgressionGuardrailTest {

    private val inter = ActionCandidate(ActionMode.INTERSTITIAL, "inter")

    private fun st(level: Int = 99, id: String = "p") =
        PlayerState(id, currentLevel = level, minutesSinceLastIapOffer = -1)

    // ═══ 1. Cửa sổ giữ chân người mới ═══

    @Test
    fun `nguoi choi moi duoc mien quang cao cho toi nguong level`() {
        // Game tham chiếu: `inter_show_level = 10` — chín màn đầu hoàn toàn sạch interstitial.
        // D1 retention phụ thuộc rất nhiều vào con số này.
        val g = GuardrailEngine()
        g.minLevelForInterstitial = 10
        assertEquals("NEW_PLAYER_GRACE", g.evaluate(inter, st(level = 3), 0, 999)?.code)
        assertNull(g.evaluate(inter, st(level = 10), 0, 999))
    }

    @Test
    fun `mac dinh khong co cua so mien — giu nguyen hanh vi cu`() {
        val g = GuardrailEngine()
        assertEquals(0, g.minLevelForInterstitial)
        assertNull(g.evaluate(inter, st(level = 1), 0, 999))
    }

    // ═══ 2. Nhánh thua được tha lâu hơn nhánh thắng ═══

    @Test
    fun `sau khi thua thi nguong level cao hon`() {
        // Game tham chiếu: thắng mở ở level 10, thua mở ở level 22 — tha thêm 12 màn.
        // Lý do không phải kỹ thuật mà là tâm lý: người vừa thua đã bực, chồng thêm quảng
        // cáo là đẩy họ rời game. Cùng một logic với RewardModel của ta.
        val g = GuardrailEngine()
        g.minLevelForInterstitial = 10
        g.minLevelForInterstitialAfterLoss = 22

        assertNull("thang o level 15 thi duoc hien",
            g.evaluate(inter, st(level = 15), 0, 999, decisionPoint = DecisionPoint.LEVEL_COMPLETE))
        assertEquals("thua o level 15 thi chua",
            "NEW_PLAYER_GRACE",
            g.evaluate(inter, st(level = 15), 0, 999, decisionPoint = DecisionPoint.LEVEL_FAIL)?.code)
        assertNull("thua o level 25 thi duoc",
            g.evaluate(inter, st(level = 25), 0, 999, decisionPoint = DecisionPoint.LEVEL_FAIL))
    }

    @Test
    fun `khong khai bao nguong thua thi dung chung nguong thang`() {
        val g = GuardrailEngine()
        g.minLevelForInterstitial = 10
        assertNull(g.evaluate(inter, st(level = 12), 0, 999, decisionPoint = DecisionPoint.LEVEL_FAIL))
    }

    // ═══ 3. Vừa xem rewarded xong ═══

    @Test
    fun `vua xem rewarded thi hoan interstitial`() {
        // Game tham chiếu tách hẳn `inter_capping_after_reward` khỏi `inter_capping`. Người
        // vừa tự nguyện xem một quảng cáo dài không nên bị chồng ngay một quảng cáo ép buộc.
        val g = GuardrailEngine()
        g.minMinutesAfterRewarded = 2
        val s = st(); s.minutesSinceLastRewarded = 0
        assertEquals("INTER_AFTER_REWARDED_COOLDOWN", g.evaluate(inter, s, 0, 999)?.code)
        s.minutesSinceLastRewarded = 5
        assertNull(g.evaluate(inter, s, 0, 999))
    }

    @Test
    fun `chua tung xem rewarded thi khong bi hoan`() {
        // -1 nghĩa là "chưa từng", phải hiểu là vô cùng lâu. Nhầm chỗ này sẽ chặn interstitial
        // của mọi người chơi chưa bao giờ xem rewarded — tức phần lớn dân số.
        val g = GuardrailEngine()
        g.minMinutesAfterRewarded = 2
        assertNull(g.evaluate(inter, st(), 0, 999))
    }

    // ═══ 4. Vừa mở lại app ═══

    @Test
    fun `bo qua N interstitial dau sau khi mo lai app`() {
        // Game tham chiếu: `SkipFirstInterCountOnReopen`. Mở lại app là lúc người chơi đang
        // quyết định có ở lại hay không.
        val g = GuardrailEngine()
        g.skipFirstInterstitialsOnReopen = 2
        val s = st(); s.interstitialOpportunitiesThisSession = 0
        assertEquals("REOPEN_GRACE", g.evaluate(inter, s, 0, 999)?.code)
        s.interstitialOpportunitiesThisSession = 2
        assertNull(g.evaluate(inter, s, 0, 999))
    }

    // ═══ 5. Trần chào bán theo NGÀY, không chỉ theo phiên ═══

    @Test
    fun `tran chao ban theo ngay`() {
        // Game tham chiếu: `noads_popup_config = {ShowPerDay: 2, GapSeconds: 300}`. Trần theo
        // phiên là chưa đủ — người mở app 10 lần một ngày sẽ nhận 10 lần trần phiên.
        val g = GuardrailEngine()
        g.maxIapOffers24h = 2
        val offer = ActionCandidate(ActionMode.IAP_OFFER, "offer")
        val s = st(); s.iapOffers24h = 2
        assertEquals("IAP_OFFER_24H_CAP", g.evaluate(offer, s, 0, 999)?.code)
        s.iapOffers24h = 1
        assertNull(g.evaluate(offer, s, 0, 999))
    }

    // ═══ Thứ tự & tính chất ═══

    @Test
    fun `cua so mien nguoi moi la rang buoc TAI NGUYEN khong phai dao duc`() {
        // Nó là một lựa chọn kinh doanh có thể điều chỉnh, không phải lằn ranh đạo đức.
        // Gắn nhãn HARD cho thứ có thể thương lượng sẽ làm loãng ý nghĩa của nhãn HARD.
        val g = GuardrailEngine()
        g.minLevelForInterstitial = 10
        assertEquals(GuardrailKind.RESOURCE, g.evaluate(inter, st(level = 1), 0, 999)!!.kind)
    }

    @Test
    fun `cac guardrail moi khong ap cho rewarded`() {
        // Rewarded là người chơi tự chọn. Cô lập state theo loại action (doc 27 mục B.2).
        val g = GuardrailEngine()
        g.minLevelForInterstitial = 10
        g.skipFirstInterstitialsOnReopen = 5
        val rw = ActionCandidate(ActionMode.REWARDED_AD, "rw")
        val s = PlayerState("p", currentLevel = 1, failStreakCurrent = 2)
        assertNull(g.evaluate(rw, s, 0, 999))
    }
}
