package com.app.plugin.brain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Guardrail cho hành động chào bán (`IAP_OFFER`).
 *
 * Cơ sở: doc 29 mục 5.3 xếp hạng những gì thực sự dịch được từ ngành gambling sang F2P.
 * Mục số 4 — **"session/spend velocity circuit breaker: tạm dừng offer targeting khi phát hiện
 * deposit-frequency spike trong phiên"** — là thứ được xây ở đây, vì doc 29 mục 5.2 xác định
 * deposit-frequency spike là *"marker mạnh nhất và dễ đo nhất trong IAP"*.
 *
 * Cũng cơ sở đó, thứ **không** được xây: risk scoring 3 màu kiểu Playscan (mục 5.3 số 5 —
 * false positive cao, người chơi phản ứng tiêu cực khi bị gán nhãn, và **không có bằng chứng
 * giảm harm**). Không có `vulnerabilityScore` nào trong hệ thống này.
 */
class IapGuardrailTest {

    private val offer = ActionCandidate(ActionMode.IAP_OFFER, "offer_starter_pack")
    private val interstitial = ActionCandidate(ActionMode.INTERSTITIAL, "inter")

    private fun st(
        id: String = "p",
        purchasesSession: Int = 0,
        offersSession: Int = 0,
        minutesSinceOffer: Int = 999,
        failStreak: Int = 0,
        purchaseCount: Int = 0,
        daysSincePurchase: Int = -1
    ) = PlayerState(
        playerId = id,
        failStreakCurrent = failStreak,
        purchaseCountLifetime = purchaseCount,
        daysSinceLastPurchase = daysSincePurchase,
        purchasesThisSession = purchasesSession,
        iapOffersThisSession = offersSession,
        minutesSinceLastIapOffer = minutesSinceOffer
    )

    // ═══ Circuit breaker: chi dồn dập trong một phiên ═══

    @Test
    fun `chi don dap trong mot phien thi ngat chao ban`() {
        val g = GuardrailEngine()
        val v = g.evaluate(offer, st(purchasesSession = 3), 0, 999)
        assertNotNull("3 luot mua trong 1 phien phai ngat offer targeting", v)
        assertEquals("IAP_VELOCITY_BREAKER", v!!.code)
    }

    @Test
    fun `circuit breaker la rang buoc TUYET DOI khong phai ngan sach`() {
        // Đây là điểm then chốt. Nếu xếp vào RESOURCE, nó thành một khoản phạt mềm mà
        // bandit có thể "mua lại" bằng đủ doanh thu — và doanh thu từ nhóm chi dồn dập
        // thì luôn đủ lớn để mua. Bảo vệ người dễ tổn thương không được phép có giá.
        val g = GuardrailEngine()
        assertEquals(
            GuardrailKind.HARD,
            g.evaluate(offer, st(purchasesSession = 5), 0, 999)!!.kind
        )
    }

    @Test
    fun `mua it trong phien thi van duoc chao ban`() {
        val g = GuardrailEngine()
        assertNull(g.evaluate(offer, st(purchasesSession = 1), 0, 999))
    }

    @Test
    fun `circuit breaker chi ap cho chao ban khong ap cho quang cao`() {
        // Cô lập state theo loại action (doc 27 mục B.2). Người vừa mua nhiều không phải
        // lý do để chặn quảng cáo — đó là việc của PAYER_PROTECTION, với ngưỡng riêng.
        val g = GuardrailEngine()
        assertNull(
            "chi don dap khong duoc dung de chan quang cao",
            g.evaluate(interstitial, st(purchasesSession = 5), 0, 999)
        )
    }

    // ═══ Không kiếm tiền trên sự bế tắc ═══

    @Test
    fun `khong chao ban khi nguoi choi dang be tac sau`() {
        // doc 29 mục 10 câu hỏi Q4: cơ chế có tăng cường độ theo mức độ dễ tổn thương không.
        // Mức sẵn lòng trả tỉ lệ thuận với độ dài chuỗi thua, nên chào bán càng lúc bế tắc
        // càng "hiệu quả" — và đó chính xác là lý do không được làm.
        val g = GuardrailEngine()
        val v = g.evaluate(offer, st(failStreak = 6), 0, 999)
        assertEquals("ETH_FRUSTRATION_EXPLOIT", v?.code)
        assertEquals(GuardrailKind.HARD, v!!.kind)
    }

    @Test
    fun `that bai binh thuong khong chan chao ban`() {
        // Chào bán sau một vài lần thua là mẫu thiết kế game hoàn toàn bình thường.
        // Ranh giới nằm ở CƯỜNG ĐỘ tăng theo mức bế tắc, không ở việc có thua hay không.
        val g = GuardrailEngine()
        assertNull(g.evaluate(offer, st(failStreak = 2), 0, 999))
    }

    // ═══ Trần tần suất chào bán ═══

    @Test
    fun `vuot tran chao ban moi phien thi dung`() {
        val g = GuardrailEngine()
        assertEquals(
            "IAP_OFFER_SESSION_CAP",
            g.evaluate(offer, st(offersSession = g.maxIapOffersPerSession), 0, 999)?.code
        )
    }

    @Test
    fun `chua du cooldown giua hai lan chao ban`() {
        val g = GuardrailEngine()
        assertEquals(
            "IAP_OFFER_COOLDOWN",
            g.evaluate(offer, st(minutesSinceOffer = 0), 0, 999)?.code
        )
    }

    @Test
    fun `chua tung chao ban thi khong bi cooldown chan`() {
        // -1 nghĩa là "chưa từng", phải hiểu là vô cùng lâu chứ không phải 0 phút trước.
        // Nhầm chỗ này sẽ chặn đúng lần chào bán ĐẦU TIÊN của mọi phiên.
        val g = GuardrailEngine()
        assertNull(g.evaluate(offer, st(minutesSinceOffer = -1), 0, 999))
    }

    // ═══ Thứ tự ưu tiên ═══

    @Test
    fun `rang buoc dao duc duoc kiem tra truoc rang buoc tai nguyen`() {
        // Một người vừa bế tắc vừa vượt trần phiên phải được báo mã ĐẠO ĐỨC, vì đó là mã
        // cần xuất hiện trong audit. Báo mã tài nguyên sẽ giấu mất tín hiệu quan trọng hơn.
        val g = GuardrailEngine()
        val v = g.evaluate(offer, st(failStreak = 8, offersSession = 99, minutesSinceOffer = 0), 0, 999)
        assertEquals("ETH_FRUSTRATION_EXPLOIT", v?.code)
    }

    @Test
    fun `guardrail chao ban duoc dem de dua vao ComfortIndex`() {
        val g = GuardrailEngine()
        g.evaluate(offer, st(purchasesSession = 9), 0, 999)
        g.evaluate(offer, st(purchasesSession = 9), 0, 999)
        assertEquals(2, g.preventedCount("IAP_VELOCITY_BREAKER"))
    }

    // ═══ Cấu hình từ xa ═══

    @Test
    fun `artifact dat duoc nguong chao ban`() {
        val g = GuardrailEngine()
        BrainPolicy.apply(
            """{"version":"v","iap":{"max_offers_per_session":2,"min_minutes_between_offers":30,
               "max_purchases_per_session_before_break":2}}""",
            g
        )
        assertEquals(2, g.maxIapOffersPerSession)
        assertEquals(30, g.minMinutesBetweenIapOffers)
        assertEquals(2, g.maxPurchasesPerSessionBeforeBreak)
        BrainPolicy.clear()
    }

    @Test
    fun `artifact khong noi long duoc nguong bao ve`() {
        // Cùng nguyên tắc với mọi ngưỡng khác: artifact chỉ được làm hệ thống AN TOÀN HƠN.
        // Đẩy circuit breaker lên 500 lượt mua/phiên là vô hiệu hoá nó.
        val g = GuardrailEngine()
        BrainPolicy.apply(
            """{"version":"v","iap":{"max_offers_per_session":500,
               "min_minutes_between_offers":0,"max_purchases_per_session_before_break":500}}""",
            g
        )
        assertEquals(
            BrainPolicy.HARD_MAX_IAP_OFFERS_PER_SESSION, g.maxIapOffersPerSession
        )
        assertEquals(
            BrainPolicy.HARD_MIN_IAP_OFFER_COOLDOWN_MINUTES, g.minMinutesBetweenIapOffers
        )
        assertEquals(
            BrainPolicy.HARD_MAX_PURCHASES_BEFORE_BREAK, g.maxPurchasesPerSessionBeforeBreak
        )
        BrainPolicy.clear()
    }
}
