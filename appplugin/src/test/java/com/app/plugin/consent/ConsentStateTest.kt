package com.app.plugin.consent

import android.util.Log
import com.app.plugin.until.LogUtil
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.contains
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mockStatic

/**
 * Trạng thái consent và cách nó chi phối personalization.
 *
 * Cơ sở: doc 13 mục E xác định personalization **là profiling** theo GDPR, và mục F đặc tả
 * ba tầng phục vụ. Ba tầng đó phải là kiểu dữ liệu trong code chứ không phải một biến boolean
 * — boolean không phân biệt được "người dùng đã TỪ CHỐI" với "chưa ai hỏi".
 *
 * Phân biệt đó là toàn bộ vấn đề. Gộp hai trường hợp lại buộc ta chọn một trong hai điều tệ:
 * hoặc coi mọi người là đã từ chối (tắt personalization cho cả những studio ngoài EEA chưa
 * từng cần consent), hoặc coi mọi người là đã đồng ý (làm chính xác điều GDPR cấm).
 */
class ConsentStateTest {

    @Before fun setUp() = ConsentState.resetForTest()
    @After fun tearDown() = ConsentState.resetForTest()

    // ═══ Mặc định ═══

    @Test
    fun `chua cau hinh thi la UNKNOWN chu khong phai DENIED`() {
        assertEquals(ConsentTier.UNKNOWN, ConsentState.tier())
    }

    // ═══ #7 (DOC/66): UNKNOWN chỉ cho khi đọc được rõ IABTCF_gdprApplies = 0 ═══

    /**
     * Bảng 4 tầng × 3 giá trị `gdprApplies` (`true`, `false`, `null` = khoá vắng hoặc đọc lỗi). `DENIED` luôn
     * thắng; `PARTIAL` giữ như trước (không profiling, có cá nhân hoá); `UNKNOWN` chỉ cho khi `gdprApplies ==
     * false` — `null` không bao giờ là lý do cấp quyền (GDPR Recital 32: im lặng không phải là đồng ý).
     */
    @Test
    fun `bang tang x gdprApplies cho profiling va ca nhan hoa`() {
        data class Row(val tier: ConsentTier, val gdpr: Boolean?, val profiling: Boolean, val personalization: Boolean)
        val table = listOf(
            Row(ConsentTier.DENIED, true, profiling = false, personalization = false),
            Row(ConsentTier.DENIED, false, profiling = false, personalization = false),
            Row(ConsentTier.DENIED, null, profiling = false, personalization = false),
            Row(ConsentTier.GRANTED, true, profiling = true, personalization = true),
            Row(ConsentTier.GRANTED, false, profiling = true, personalization = true),
            Row(ConsentTier.GRANTED, null, profiling = true, personalization = true),
            Row(ConsentTier.PARTIAL, true, profiling = false, personalization = true),
            Row(ConsentTier.PARTIAL, false, profiling = false, personalization = true),
            Row(ConsentTier.PARTIAL, null, profiling = false, personalization = true),
            Row(ConsentTier.UNKNOWN, true, profiling = false, personalization = false),
            Row(ConsentTier.UNKNOWN, false, profiling = true, personalization = true),
            Row(ConsentTier.UNKNOWN, null, profiling = false, personalization = false),
        )
        for (row in table) {
            ConsentState.resetForTest()
            ConsentState.setTier(row.tier)
            assertEquals("profiling ${row.tier} gdprApplies=${row.gdpr}", row.profiling, ConsentState.allowsProfiling(row.gdpr))
            assertEquals(
                "personalization ${row.tier} gdprApplies=${row.gdpr}",
                row.personalization,
                ConsentState.allowsPersonalization(row.gdpr)
            )
        }
    }

    @Test
    fun `ban khong tham so coi UNKNOWN la chua duoc cap`() {
        // Ba nơi gọi (DecisionEngine.observe, PersonalizationScenario.resolve, CrmBridge.emit) không có Context để
        // đọc IABTCF_gdprApplies: bản không tham số bảo thủ, như gdprApplies không biết.
        assertEquals(ConsentTier.UNKNOWN, ConsentState.tier())
        assertFalse(ConsentState.allowsProfiling())
        assertFalse(ConsentState.allowsPersonalization())
    }

    @Test
    fun `ban khong tham so giu nguyen cac tang da co cau tra loi`() {
        for ((tier, profiling, personalization) in listOf(
            Triple(ConsentTier.DENIED, false, false),
            Triple(ConsentTier.PARTIAL, false, true),
            Triple(ConsentTier.GRANTED, true, true),
        )) {
            ConsentState.setTier(tier)
            assertEquals("profiling $tier", profiling, ConsentState.allowsProfiling())
            assertEquals("personalization $tier", personalization, ConsentState.allowsPersonalization())
        }
    }

    // ═══ Ba tầng của doc 13 mục F ═══

    @Test
    fun `DENIED chan profiling va personalization`() {
        ConsentState.setTier(ConsentTier.DENIED)
        assertFalse("profiling can consent (doc 13 muc E)", ConsentState.allowsProfiling())
        assertFalse(ConsentState.allowsPersonalization())
    }

    @Test
    fun `PARTIAL cho phep personalization nhung khong profiling`() {
        // doc 13 mục F: PARTIAL = chỉ dùng feature đã được consent. Ở mức triển khai hiện
        // tại ta chưa có per-feature masking, nên xử lý bảo thủ: không tích luỹ hồ sơ mới,
        // vẫn phục vụ theo hồ sơ đã có.
        ConsentState.setTier(ConsentTier.PARTIAL)
        assertFalse(ConsentState.allowsProfiling())
        assertTrue(ConsentState.allowsPersonalization())
    }

    @Test
    fun `GRANTED cho phep tat ca`() {
        ConsentState.setTier(ConsentTier.GRANTED)
        assertTrue(ConsentState.allowsProfiling())
        assertTrue(ConsentState.allowsPersonalization())
    }

    // ═══ Guardrail KHÔNG BAO GIỜ bị consent tắt ═══

    @Test
    fun `guardrail van chay o moi tang consent`() {
        // Guardrail chỉ BẢO VỆ người chơi — chặn quảng cáo quá dày, chặn chào bán lúc bế tắc.
        // Không có diễn giải nào của GDPR khiến việc bảo vệ người dùng cần sự cho phép của họ.
        for (t in ConsentTier.values()) {
            ConsentState.setTier(t)
            assertTrue("guardrail phai chay o tang $t", ConsentState.allowsGuardrails())
        }
    }

    @Test
    fun `ghi decision log van duoc phep khi tu choi`() {
        // doc 13 mục F: "NO_CONSENT → ... vẫn log decision (log không chứa PII)".
        // Decision log của ta không chứa PII — chỉ có id giả lập, action và propensity.
        ConsentState.setTier(ConsentTier.DENIED)
        assertTrue(ConsentState.allowsDecisionLogging())
    }

    // ═══ Đổi consent là sự kiện hạng nhất ═══

    @Test
    fun `doi consent duoc ghi nhan de goi ben ngoai phan ung`() {
        // doc 13 mục F: "Consent change là event hạng nhất: đổi consent → invalidate profile
        // cache và đổi tier serving ngay." Không có móc này thì hồ sơ cũ tiếp tục được dùng
        // sau khi người dùng đã rút lại sự đồng ý.
        val seen = mutableListOf<ConsentTier>()
        ConsentState.onChange { seen.add(it) }
        ConsentState.setTier(ConsentTier.GRANTED)
        ConsentState.setTier(ConsentTier.DENIED)
        assertEquals(listOf(ConsentTier.GRANTED, ConsentTier.DENIED), seen)
    }

    @Test
    fun `dat lai cung mot tang khong ban su kien thua`() {
        val seen = mutableListOf<ConsentTier>()
        ConsentState.onChange { seen.add(it) }
        ConsentState.setTier(ConsentTier.GRANTED)
        ConsentState.setTier(ConsentTier.GRANTED)
        assertEquals(1, seen.size)
    }

    // ═══ Lưu và khôi phục tầng qua phiên ═══

    private class MemoryStore : ConsentState.TierStore {
        var saved: String? = null
        override fun save(name: String) { saved = name }
        override fun load(): String? = saved
    }

    @Test
    fun `tier duoc luu khi doi va khoi phuc khi mo lai`() {
        val store = MemoryStore()
        ConsentState.installStore(store)
        ConsentState.setTier(ConsentTier.DENIED)
        assertEquals("DENIED", store.saved)

        ConsentState.resetForTest()          // giả lập process mới
        ConsentState.installStore(store)
        ConsentState.restore()
        assertEquals(ConsentTier.DENIED, ConsentState.tier())
    }

    @Test
    fun `gia tri la trong store thi giu UNKNOWN`() {
        val store = MemoryStore().apply { saved = "rac" }
        ConsentState.installStore(store)
        ConsentState.restore()
        assertEquals(ConsentTier.UNKNOWN, ConsentState.tier())
    }

    @Test
    fun `store hong khi luu thi tier van doi va nguoi nghe van duoc bao`() {
        ConsentState.installStore(object : ConsentState.TierStore {
            override fun save(name: String) = throw IllegalStateException("disk full")
            override fun load(): String? = null
        })
        val seen = mutableListOf<ConsentTier>()
        ConsentState.onChange { seen.add(it) }

        ConsentState.setTier(ConsentTier.DENIED)

        assertEquals(ConsentTier.DENIED, ConsentState.tier())
        assertEquals(listOf(ConsentTier.DENIED), seen)
    }

    @Test
    fun `store hong khi doc thi giu UNKNOWN chu khong crash`() {
        ConsentState.installStore(object : ConsentState.TierStore {
            override fun save(name: String) = Unit
            override fun load(): String? = throw IllegalStateException("prefs not ready")
        })
        ConsentState.restore()
        assertEquals(ConsentTier.UNKNOWN, ConsentState.tier())
    }

    @Test
    fun `restore sau khi da co tier thi khong ghi de`() {
        // UMP trả lời trước khi restore() chạy: câu trả lời mới hơn tầng đã lưu của phiên trước.
        val store = MemoryStore()
        ConsentState.installStore(store)
        ConsentState.setTier(ConsentTier.DENIED)
        store.saved = "GRANTED"
        val seen = mutableListOf<ConsentTier>()
        ConsentState.onChange { seen.add(it) }

        ConsentState.restore()

        assertEquals(ConsentTier.DENIED, ConsentState.tier())
        assertEquals(emptyList<ConsentTier>(), seen)
    }

    @Test
    fun `nguoi nghe hong thi log loi va nguoi nghe khac van duoc bao`() {
        val seen = mutableListOf<ConsentTier>()
        ConsentState.onChange { throw IllegalStateException("listener boom") }
        ConsentState.onChange { seen.add(it) }

        mockStatic(Log::class.java).use { log ->
            ConsentState.setTier(ConsentTier.DENIED)

            log.verify { Log.e(eq(LogUtil.TAG), contains("listener boom")) }
        }
        assertEquals(listOf(ConsentTier.DENIED), seen)
    }

    @Test
    fun `chua cai store thi restore khong lam gi`() {
        ConsentState.restore()
        assertEquals(ConsentTier.UNKNOWN, ConsentState.tier())
    }

    // ═══ Đối tượng trẻ em ═══

    @Test
    fun `mac dinh KHONG khang dinh gi ve doi tuong tre em`() {
        // SDK không được tự tuyên bố thay studio rằng app không hướng tới trẻ em — đó là
        // tuyên bố pháp lý, không phải giá trị mặc định kỹ thuật. `null` = chưa khai báo,
        // và SDK sẽ không đụng vào cấu hình tương ứng của mạng quảng cáo.
        assertEquals(null, ConsentState.childDirected)
        assertEquals(null, ConsentState.underAgeOfConsent)
    }

    @Test
    fun `studio khai bao duoc doi tuong tre em`() {
        ConsentState.childDirected = true
        ConsentState.underAgeOfConsent = true
        assertEquals(true, ConsentState.childDirected)
        assertEquals(true, ConsentState.underAgeOfConsent)
    }
}
