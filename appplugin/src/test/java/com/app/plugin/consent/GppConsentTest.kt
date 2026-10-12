package com.app.plugin.consent

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.app.plugin.until.LogUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.contains
import org.mockito.ArgumentMatchers.eq
import org.mockito.ArgumentMatchers.nullable
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.`when`

/**
 * 1.6.0 Task 7 (O2): giải mã `IABGPP_HDR_GppString`/`IABGPP_GppSID` (GPP 1.1) cho US National (7) và
 * CA/VA/CO/UT/CT (8–12). Vector (2) dựng bằng encoder nhỏ trong test, theo layout chép độc lập với decoder
 * (đặc tả IAB GPP, Sections/US*); vector thật của UMP chốt ở Task 8 trên emulator.
 */
class GppConsentTest {

    @Before fun logOn() = LogUtil.setFlagLog(1)

    // ═══ (1) Vector đặc tả IAB ═══

    /**
     * `DBABLA~BVVqAAEABAA.QA` (ví dụ US National trong đặc tả IAB GPP). Header: Type 3, Version 1, section [7].
     * Giải mã đọc được (chỉ ghi lại, không khẳng định — chốt bằng vector emulator ở Task 8): core Version = 1,
     * các notice = 1, `SaleOptOut = 2`, `SharingOptOut = 2`, `TargetedAdvertisingOptOut = 2` (không opt-out);
     * sub-section GPC `QA` → SubsectionType 1, `Gpc = 0`.
     */
    @Test fun `vector dac ta DBABLA cho section 7`() {
        val s = GppConsent.decode("DBABLA~BVVqAAEABAA.QA", null)

        assertNotNull("DBABLA~BVVqAAEABAA.QA phải giải mã được", s)
        assertEquals(listOf(7), s!!.sectionIds)
        assertEquals("DBABLA~BVVqAAEABAA.QA", s.raw)
    }

    /**
     * Vector thật UMP 4.0.0 (emulator API 28, demo `force_us`, UMP `NOT_REQUIRED`, không hiện form — Task 8,
     * `DOC/testing/t160-t8-n2-probe.log`): `IABGPP_HDR_GppString = DBABL~BAAAAAAAAg`, `IABGPP_GppSID = 7`. Header chỉ 5
     * ký tự (30 bit, không pad như vector đặc tả `DBABLA`). Core: Version 1, mọi trường 0 (không áp dụng) → không opt-out.
     */
    @Test fun `vector that UMP 4_0_0 force_us qua read`() {
        val prefs = mock(SharedPreferences::class.java)
        `when`(prefs.getString("IABGPP_HDR_GppString", null)).thenReturn("DBABL~BAAAAAAAAg")
        `when`(prefs.getString("IABGPP_GppSID", null)).thenReturn("7")

        val s = GppConsent.read(contextWith(prefs))

        assertNotNull("vector UMP thật phải giải mã được", s)
        assertEquals(listOf(7), s!!.sectionIds)
        assertEquals(false, s.saleOptOut)
        assertEquals(false, s.sharingOptOut)
        assertEquals(false, s.targetedAdsOptOut)
        assertEquals(false, ConsentRules.usOptOut(s))
        assertEquals("gpp=ok", ConsentRules.gppLabel(s))
    }

    // ═══ (2) Vector tự dựng: từng section 7–12, từng trường quyết định, giá trị 0/1/2 ═══

    @Test fun `moi truong opt-out cua section 7 den 12 chi bang 1 moi la opt-out`() {
        for ((id, layout) in LAYOUTS) for (field in layout.filter { it in DECISIVE }) for (v in 0..2) {
            val gpp = header(listOf(id)) + "~" + core(id, mapOf(field to v))
            val s = GppConsent.decode(gpp, null)

            assertNotNull("section $id, $field=$v: phải giải mã được ($gpp)", s)
            val expect = v == 1
            assertEquals("section $id, $field=$v → saleOptOut", field == SALE && expect, s!!.saleOptOut)
            assertEquals("section $id, $field=$v → sharingOptOut", field == SHARING && expect, s.sharingOptOut)
            assertEquals("section $id, $field=$v → targetedAdsOptOut", field == TARGETED && expect, s.targetedAdsOptOut)
            assertEquals("section $id, $field=$v → usOptOut", expect, s.usOptOut)
            assertEquals(listOf(id), s.sectionIds)
        }
    }

    @Test fun `header nhieu section giai ma dung id theo delta Fibonacci`() {
        val ids = listOf(7, 8, 9, 10, 11, 12)
        val gpp = header(ids) + ids.joinToString("") { "~" + core(it, emptyMap()) }

        assertEquals(ids, GppConsent.decode(gpp, null)?.sectionIds)
    }

    @Test fun `header section dang range cung giai ma duoc`() {
        // Entry range: IsARange=1, start = 7 (Fibonacci), end = start + 2 → [7, 8, 9].
        val bits = "000011" + "000001" + bin(1, 12) + "1" + fib(7) + fib(2)
        val gpp = pack(bits) + listOf(7, 8, 9).joinToString("") { "~" + core(it, emptyMap()) }

        assertEquals(listOf(7, 8, 9), GppConsent.decode(gpp, null)?.sectionIds)

        // Range rồi một id đơn (review Task 7 m1): delta của entry sau tính từ cuối range → [7, 8, 10].
        val bits2 = "000011" + "000001" + bin(2, 12) + "1" + fib(7) + fib(1) + "0" + fib(2)
        val gpp2 = pack(bits2) + listOf(7, 8, 10).joinToString("") { "~" + core(it, emptyMap()) }
        assertEquals(listOf(7, 8, 10), GppConsent.decode(gpp2, null)?.sectionIds)
    }

    /** Review Task 7 I2: OR giữa các section áp dụng — section sau không opt-out không xoá opt-out của section trước. */
    @Test fun `opt-out o section dau van tinh khi section sau khong opt-out`() {
        val gpp = header(listOf(7, 8)) + "~" + core(7, mapOf(SALE to 1)) + "~" + core(8, emptyMap())
        val s = GppConsent.decode(gpp, null)

        assertEquals("OR giữa các section áp dụng", true, s?.saleOptOut)
        assertEquals(true, s?.usOptOut)
    }

    // ═══ GPC: chỉ ghi, không quyết định ═══

    @Test fun `GPC doc tu sub-section, khong quyet dinh opt-out`() {
        val base = header(listOf(7)) + "~" + core(7, emptyMap())

        assertNull("không có sub-section GPC", GppConsent.decode(base, null)?.gpc)
        assertEquals(false, GppConsent.decode("$base.QA", null)?.gpc)
        val on = GppConsent.decode("$base.YA", null)
        assertEquals(true, on?.gpc)
        assertEquals("GPC = 1 không làm usOptOut", false, on?.usOptOut)
        // Review Task 7 m3: sub-section type khác 1 không phải GPC; nhiều section thì một GPC = 1 là đủ.
        assertNull("sub-section không phải type 1 thì không phải GPC", GppConsent.decode("$base.oA", null)?.gpc)
        val two = header(listOf(7, 8)) + "~" + core(7, emptyMap()) + ".YA~" + core(8, emptyMap()) + ".QA"
        assertEquals(true, GppConsent.decode(two, null)?.gpc)
    }

    // ═══ (3) IABGPP_GppSID: `_` và `,` ═══

    @Test fun `GppSID 7_8 va 7,8 cho cung ket qua va loc section ap dung`() {
        val gpp = header(listOf(7, 8, 9)) + "~" + core(7, emptyMap()) + "~" + core(8, emptyMap()) + "~" +
            core(9, mapOf(SALE to 1))

        val underscore = GppConsent.decode(gpp, "7_8")
        val comma = GppConsent.decode(gpp, "7,8")

        assertNotNull(underscore)
        assertEquals("GppSID '7_8' và '7,8' phải cho cùng kết quả", comma, underscore)
        assertEquals("section 9 không nằm trong GppSID: opt-out của nó không tính", false, underscore!!.usOptOut)
        assertEquals("không có GppSID thì xét mọi section trong header", true, GppConsent.decode(gpp, null)?.usOptOut)
        assertNull("GppSID chỉ có section ngoài 7–12 thì không phải người Mỹ", GppConsent.decode(gpp, "2"))
    }

    // ═══ (4) Chuỗi hỏng → null + một dòng logE ═══

    @Test fun `chuoi hong thi null va dung mot dong logE`() {
        val core7 = core(7, emptyMap())
        val broken = mapOf(
            "ký tự ngoài base64url" to "DBAB!A~$core7",
            "khoảng trắng" to "not a gpp string",
            "header không phải type 3" to "CBABLA~$core7",
            "thiếu section so với header" to "DBABLA",
            "core segment ngắn hơn trường quyết định" to "DBABLA~BV",
            "section rỗng" to "DBABLA~",
            "thừa section so với header" to "DBABLA~$core7~$core7",
            "header cụt" to "DB",
        )
        for ((why, gpp) in broken) mockStatic(Log::class.java).use { log ->
            assertNull("$why: '$gpp' phải là null", GppConsent.decode(gpp, null))
            log.verify({ Log.e(eq(LogUtil.TAG), contains("GppConsent")) }, times(1))
        }
    }

    /**
     * Review Task 7 n1 (review Task 6 m-1: biên đúng từng section, không chỉ overshoot rộng): cận cộng dồn của
     * header — mỗi range tự nó ≤ `maxIds` (số section chuỗi thực có) nhưng **cộng dồn** hai range lại vượt `maxIds`
     * đúng 1 phải bị chặn ngay ở range thứ hai (`decodeHeader`), không chờ tới check tổng số section ở
     * `decodeOrThrow`. `maxIds = 3` (3 section thật trong chuỗi): range 1–2 (2 id) rồi range 3–4 (2 id) — cộng dồn
     * 4 = `maxIds + 1`, sát biên (khác overshoot rộng 6 vs 3 trước đây, để lộ đột biến lệch-một trên chính cận
     * `maxIds - ids.size`).
     */
    @Test fun `header hai range moi range duoi maxIds nhung cong don vuot thi null va dung mot dong logE`() {
        val bits = "000011" + "000001" + bin(2, 12) +
            "1" + fib(1) + fib(1) +   // range 1: start=1, span=1 → [1,2] (2 id, ≤ maxIds)
            "1" + fib(1) + fib(1)     // range 2: start=3, span=1 → [3,4] (2 id, ≤ maxIds, cộng dồn 4 = maxIds + 1)
        val gpp = pack(bits) + listOf(7, 8, 9).joinToString("") { "~" + core(it, emptyMap()) }   // maxIds = 3

        mockStatic(Log::class.java).use { log ->
            val s = GppConsent.decode(gpp, null)

            assertNull("header đòi 4 section trong khi chuỗi chỉ có 3 phải là null", s)
            log.verify({ Log.e(eq(LogUtil.TAG), contains("GppConsent")) }, times(1))
            // "more sections than the string has": đúng chỗ chặn là range thứ hai trong decodeHeader (cận cộng
            // dồn `maxIds - ids.size`). Không có assert này thì check tổng số section ở decodeOrThrow (`parts.size
            // == ids.size + 1`) cũng vô tình ra null + một dòng logE chứa "GppConsent" khi cận cộng dồn bị đột biến
            // bỏ đi — chặn muộn tình cờ đúng kết quả sẽ lọt qua assert phía trên. Sát biên (cộng dồn = maxIds + 1,
            // review Task 6 m-1) nên bắt được cả đột biến H (`<= maxIds - ids.size` → `<= maxIds`) lẫn lệch-một R1
            // (`<= maxIds - ids.size` → `<= maxIds - ids.size + 1`) — overshoot rộng trước đây chỉ bắt được H.
            log.verify({ Log.e(eq(LogUtil.TAG), contains("more sections than the string has")) }, times(1))
        }
    }

    /** Review Task 7 I1: range Fibonacci khổng lồ trong header (291 ký tự) từng mở rộng ra hàng chục triệu id → OOM. */
    @Test fun `header range khong lo thi null va mot dong logE, khong OOM`() {
        val hugeRange = "1" + fib(1) + "0".repeat(28) + "11" // mỗi entry: range start +1, span 832040
        val gpp = pack("000011" + "000001" + bin(50, 12) + hugeRange.repeat(50)) + "~" + core(7, emptyMap())
        mockStatic(Log::class.java).use { log ->
            assertNull(GppConsent.decode(gpp, null))
            log.verify({ Log.e(eq(LogUtil.TAG), contains("GppConsent")) }, times(1))
        }
    }

    @Test fun `GppSID sai dang thi logE va xet moi section trong header`() {
        val gpp = header(listOf(7)) + "~" + core(7, mapOf(SALE to 1))
        mockStatic(Log::class.java).use { log ->
            assertEquals(true, GppConsent.decode(gpp, "seven")?.saleOptOut)
            log.verify({ Log.e(eq(LogUtil.TAG), contains("GppConsent")) }, times(1))
        }
    }

    // ═══ (5) Không có khoá → null, không log ═══

    @Test fun `khong co khoa IABGPP thi null va khong log`() {
        val prefs = mock(SharedPreferences::class.java)
        mockStatic(Log::class.java).use { log ->
            assertNull(GppConsent.read(prefs))
            assertNull(GppConsent.read(contextWith(prefs)))
            assertNull("chuỗi rỗng coi như vắng", GppConsent.decode("", "7"))
            log.verify({ Log.e(anyString(), anyString()) }, never())
        }
    }

    @Test fun `doc dung cac khoa IABGPP ma UMP ghi`() {
        val gpp = header(listOf(7)) + "~" + core(7, mapOf(SHARING to 1))
        val prefs = mock(SharedPreferences::class.java)
        `when`(prefs.getString("IABGPP_HDR_GppString", null)).thenReturn(gpp)
        `when`(prefs.getString("IABGPP_GppSID", null)).thenReturn("7")

        val s = GppConsent.read(contextWith(prefs))

        assertEquals(GppSignals(listOf(7), false, true, false, null, gpp), s)
        assertEquals(true, s!!.usOptOut)
    }

    @Test fun `khoa sai kieu thi null va logE chu khong crash`() {
        val prefs = mock(SharedPreferences::class.java)
        `when`(prefs.getString(anyString(), eq(null))).thenThrow(ClassCastException("Integer cannot be cast to String"))
        mockStatic(Log::class.java).use { log ->
            assertNull(GppConsent.read(prefs))
            log.verify({ Log.e(eq(LogUtil.TAG), contains("GppConsent")) }, times(1))
        }
    }

    @Test fun `khong mo duoc preferences thi null va logE`() {
        val context = mock(Context::class.java)
        `when`(context.packageName).thenReturn("com.example.app")
        `when`(context.getSharedPreferences(anyString(), anyInt())).thenThrow(IllegalStateException("locked"))
        mockStatic(Log::class.java).use { log ->
            assertNull(GppConsent.read(context))
            log.verify({ Log.e(eq(LogUtil.TAG), contains("GppConsent")) }, times(1))
        }
    }

    // ═══ (6) Section ngoài 7–12 bỏ qua ═══

    @Test fun `section ngoai 7 den 12 bo qua, khong nem, khong log`() {
        mockStatic(Log::class.java).use { log ->
            // Ví dụ đặc tả: TCF EU v2 (2) + US Privacy cũ (6), không có section US nào của Task 7.
            assertNull(GppConsent.decode("DBACNY~CPXxRfAPXxRfAAfKABENB-CgAAAAAAAAAAYgAAAAAAAA~1YNN", null))
            assertNull("header hợp lệ không có section nào", GppConsent.decode("DBAA", null))
            log.verify({ Log.e(anyString(), anyString()) }, never())
        }
        val gpp = header(listOf(2, 6, 7)) + "~CPXxRfAPXxRfAAfKABENB-CgAAAAAAAAAAYgAAAAAAAA~1YNN~" +
            core(7, mapOf(TARGETED to 1))
        val s = GppConsent.decode(gpp, "2_6_7")

        assertEquals(listOf(2, 6, 7), s?.sectionIds)
        assertEquals(true, s?.targetedAdsOptOut)
    }

    // ═══ (7) 1.6.3 Task 6: header còn section US 7–12 không (quyết định chủ dự án C + GRANTED) ═══

    private fun hasUs(p: SharedPreferences): Boolean? = try {
        GppConsent::class.java.getDeclaredMethod("headerHasUsSection\$appplugin", SharedPreferences::class.java).invoke(GppConsent, p) as Boolean?
    } catch (e: NoSuchMethodException) { fail("GppConsent.headerHasUsSection chưa có (1.6.3 Task 6): $e"); null }
    private fun prefsWithGpp(gpp: String?): SharedPreferences = mock(SharedPreferences::class.java).also {
        `when`(it.getString(eq("IABGPP_HDR_GppString"), nullable(String::class.java))).thenReturn(gpp)
    }
    @Test fun `headerHasUsSection true khi header co 7, false khi chi co 2, null khi vang hoac hong`() {
        assertEquals(true, hasUs(prefsWithGpp("DBABL~BAAAAAAAAg")))
        assertEquals(false, hasUs(prefsWithGpp("DBABM~CPtest")))
        assertEquals(null, hasUs(prefsWithGpp(null)))
        assertEquals(null, hasUs(prefsWithGpp("!!!~x")))
        assertEquals("header có US nhưng section hỏng: không quyết (fail-closed)", null, hasUs(prefsWithGpp("DBABL~!!")))
    }
    @Test fun `headerHasUsSection khong xet GppSID`() {   // SID = -1, header [7] → vẫn true
        val prefs = prefsWithGpp("DBABL~BAAAAAAAAg"); `when`(prefs.getString(eq("IABGPP_GppSID"), nullable(String::class.java))).thenReturn("-1")
        assertEquals(true, hasUs(prefs))
    }

    // ═══ Encoder cho test: 6 bit/ký tự base64url, pad 0 ═══

    private fun contextWith(prefs: SharedPreferences): Context {
        val context = mock(Context::class.java)
        `when`(context.packageName).thenReturn("com.example.app")
        `when`(context.getSharedPreferences(anyString(), anyInt())).thenReturn(prefs)
        return context
    }

    private fun bin(v: Int, width: Int) = Integer.toBinaryString(v).padStart(width, '0')

    private fun pack(bits: String): String =
        bits.padEnd((bits.length + 5) / 6 * 6, '0').chunked(6).joinToString("") { B64[it.toInt(2)].toString() }

    /** Fibonacci (Zeckendorf), bit thấp trước (1, 2, 3, 5, 8…), kết thúc bằng `1` thêm vào → hai bit `1` liền. */
    private fun fib(n: Int): String {
        val f = mutableListOf(1, 2)
        while (f.last() + f[f.size - 2] <= n) f += f.last() + f[f.size - 2]
        val out = CharArray(f.size) { '0' }
        var rest = n
        for (i in f.indices.reversed()) if (f[i] <= rest) { out[i] = '1'; rest -= f[i] }
        return String(out).trimEnd('0') + "1"
    }

    /** Header GPP: Type 3, Version 1, danh sách id (mỗi entry một id, delta Fibonacci). */
    private fun header(ids: List<Int>): String {
        var prev = 0
        val entries = ids.joinToString("") { id -> ("0" + fib(id - prev)).also { prev = id } }
        return pack("000011" + "000001" + bin(ids.size, 12) + entries)
    }

    /** Core segment: Version 1, notice = 1, opt-out = 2 trừ [set], rồi 18 bit 0 (trường sau, không đọc). */
    private fun core(id: Int, set: Map<String, Int>): String {
        val fields = LAYOUTS.getValue(id).joinToString("") { name -> bin(set[name] ?: if (name in DECISIVE) 2 else 1, 2) }
        return pack(bin(1, 6) + fields + "0".repeat(18))
    }

    private companion object {
        const val B64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
        const val SALE = "SaleOptOut"
        const val SHARING = "SharingOptOut"
        const val TARGETED = "TargetedAdvertisingOptOut"
        val DECISIVE = setOf(SALE, SHARING, TARGETED)
        val VA = listOf("SharingNotice", "SaleOptOutNotice", "TargetedAdvertisingOptOutNotice", SALE, TARGETED)

        /** Layout đầu core segment theo đặc tả IAB GPP (Sections/US*), chép độc lập với decoder. */
        val LAYOUTS = mapOf(
            7 to listOf(
                "SharingNotice", "SaleOptOutNotice", "SharingOptOutNotice", "TargetedAdvertisingOptOutNotice",
                "SensitiveDataProcessingOptOutNotice", "SensitiveDataLimitUseNotice", SALE, SHARING, TARGETED,
            ),
            8 to listOf("SaleOptOutNotice", "SharingOptOutNotice", "SensitiveDataLimitUseNotice", SALE, SHARING),
            9 to VA,
            10 to VA,
            11 to listOf(
                "SharingNotice", "SaleOptOutNotice", "TargetedAdvertisingOptOutNotice",
                "SensitiveDataProcessingOptOutNotice", SALE, TARGETED,
            ),
            12 to VA,
        )
    }
}

/**
 * 1.6.0 Task 8b: vector thật UMP 4.0.0 trên emulator-5570 (AVD Pixel_8_Pro, API 37), demo debug `--ez force_us true`,
 * form "My data preferences" mở qua nút "Privacy options" của demo (`DOC/testing/t160-t8-emu-us.log`). Vector lúc mở
 * app (`DBABL~BAAAAAAAAg`) trùng vector API 28 đã chốt ở trên nên không thêm lại. Giải mã tay (6 bit/ký tự, core
 * US National v1): ký tự 2–3 `VQ` = notice Sharing/Sale/SharingOptOut/TargetedAds = 1; ký tự 4 là ba trường quyết
 * định Sale/Sharing/Targeted: `V` = 010101 → 1/1/1 (opt-out), `q` = 101010 → 2/2/2 (không opt-out); `g` =
 * MspaCoveredTransaction 2.
 */
class GppConsentEmulatorVectorTest {

    @Before fun logOn() = LogUtil.setFlagLog(1)

    @Test fun `vector that Don't sell or share la opt-out ca ba truong`() {
        val s = GppConsent.read(contextWith("DBABL~BVQVAAAAAg", "7"))

        assertNotNull("vector opt-out thật phải giải mã được", s)
        assertEquals(listOf(7), s!!.sectionIds)
        assertEquals(true, s.saleOptOut)
        assertEquals(true, s.sharingOptOut)
        assertEquals(true, s.targetedAdsOptOut)
        assertEquals(true, ConsentRules.usOptOut(s))
        assertEquals("gpp=optOut", ConsentRules.gppLabel(s))
    }

    @Test fun `vector that Allow the sale or sharing khong opt-out`() {
        val s = GppConsent.read(contextWith("DBABL~BVQqAAAAAg", "7"))

        assertNotNull("vector allow thật phải giải mã được", s)
        assertEquals(listOf(7), s!!.sectionIds)
        assertEquals(false, s.saleOptOut)
        assertEquals(false, s.sharingOptOut)
        assertEquals(false, s.targetedAdsOptOut)
        assertEquals(false, ConsentRules.usOptOut(s))
        assertEquals("gpp=ok", ConsentRules.gppLabel(s))
    }

    private fun contextWith(gpp: String, sid: String): Context {
        val prefs = mock(SharedPreferences::class.java)
        `when`(prefs.getString("IABGPP_HDR_GppString", null)).thenReturn(gpp)
        `when`(prefs.getString("IABGPP_GppSID", null)).thenReturn(sid)
        val context = mock(Context::class.java)
        `when`(context.packageName).thenReturn("com.example.app")
        `when`(context.getSharedPreferences(anyString(), anyInt())).thenReturn(prefs)
        return context
    }
}
