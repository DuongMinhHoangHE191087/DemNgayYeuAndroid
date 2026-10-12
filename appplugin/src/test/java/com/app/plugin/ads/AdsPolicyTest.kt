package com.app.plugin.ads

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.app.plugin.logevent.LogEventServerHelper
import com.app.plugin.until.Constants.AdShowFailReason
import com.app.plugin.until.LogUtil
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.ArgumentMatchers.contains
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

/**
 * Doc 62 F: luật tần suất nằm ở một chỗ, khoá Remote Config `ads_policy_android`. Chủ dự án chưa chốt
 * DOC/65 §7 #1, #2, nên **mặc định tái tạo đúng hành vi 1.3.1** (Điều 7.3: Remote Config chưa về hay
 * hỏng thì dùng mặc định đã định); các giá trị doc 62 F chỉ bật khi JSON ghi rõ.
 */
class AdsPolicyTest {

    /** Ví dụ Remote Config ghi trong CHANGELOG và DOC/62 F để bật đúng luật doc 62. */
    private val doc62 = """{"schema_version":1,"no_interstitial_first_session":true,"app_open_from_session":2,""" +
        """"interstitial_min_gap_seconds":240}"""

    @After
    fun tearDown() {
        AdsPolicy.current = AdsPolicy.DEFAULT
        LogUtil.setFlagLog(1)
        country("")
    }

    /** Đặt country của `LogEventServerHelper` (field private, `init` cần Context thật). */
    private fun country(code: String) {
        LogEventServerHelper::class.java.getDeclaredField("country").apply { isAccessible = true }
            .set(LogEventServerHelper, code)
    }

    // ═══ Mặc định = hành vi 1.3.1 ═══

    @Test fun `khong co config hay config hong thi dung mac dinh`() {
        for (raw in listOf(null, "", "   ", "{ rac", "[1,2]", "42", "null")) {
            assertEquals("raw=$raw", AdsPolicy.DEFAULT, AdsPolicy.parse(raw))
        }
    }

    @Test fun `mac dinh tai tao hanh vi 1_3_1`() {
        val p = AdsPolicy.DEFAULT
        // Không có luật phiên: interstitial và app open ở mọi phiên, kể cả khi chưa biết phiên (0).
        for (session in 0..3) {
            assertNull("interstitial phiên $session", p.interstitialVerdict(session, secondsSinceLast = 10_000, legacyMinGapSeconds = 30))
            assertNull("app open phiên $session", p.appOpenVerdict(session))
        }
        // Khoảng cách là `fullDeltatime` cũ của ads_region_android (AdsConfigOb mặc định 30 s), chặn khi <=.
        assertEquals(30, p.minGapSeconds(legacyMinGapSeconds = 30))
        assertEquals(AdShowFailReason.POLICY_MIN_GAP, p.interstitialVerdict(2, secondsSinceLast = 30, legacyMinGapSeconds = 30))
        assertNull(p.interstitialVerdict(2, secondsSinceLast = 31, legacyMinGapSeconds = 30))
        assertEquals(AdShowFailReason.POLICY_MIN_GAP, p.interstitialVerdict(2, secondsSinceLast = 45, legacyMinGapSeconds = 60))
        // Không ghi đè cooldown N màn hình của app; cooldown sau click 5 s; app open quá 4 giờ bị bỏ.
        assertNull(p.screensBeforeInterstitial)
        assertEquals(FullScreenAdPolicy.DEFAULT_CLICK_RETURN_COOLDOWN_MILLIS, p.postClickCooldownMillis())
        assertEquals(APP_OPEN_MAX_AGE_MS, p.appOpenMaxAgeMillis())
        assertEquals(240, p.appOpenMaxAgeMinutes)
    }

    // ═══ Luật doc 62 F, bật bằng Remote Config ═══

    @Test fun `vi du doc 62 - phien dau khong interstitial`() {
        val p = AdsPolicy.parse(doc62)
        assertEquals(AdShowFailReason.POLICY_FIRST_SESSION, p.interstitialVerdict(1, secondsSinceLast = 9_999, legacyMinGapSeconds = 30))
        assertEquals("chưa biết phiên cũng là phiên đầu", AdShowFailReason.POLICY_FIRST_SESSION, p.interstitialVerdict(0, 9_999, 30))
    }

    @Test fun `vi du doc 62 - cach 240 s, thang fullDeltatime cu`() {
        val p = AdsPolicy.parse(doc62)
        assertEquals(240, p.minGapSeconds(legacyMinGapSeconds = 30))
        assertEquals(AdShowFailReason.POLICY_MIN_GAP, p.interstitialVerdict(2, secondsSinceLast = 239, legacyMinGapSeconds = 30))
        assertEquals(AdShowFailReason.POLICY_MIN_GAP, p.interstitialVerdict(2, secondsSinceLast = 240, legacyMinGapSeconds = 30))
        assertNull(p.interstitialVerdict(2, secondsSinceLast = 241, legacyMinGapSeconds = 30))
        assertNull("khoảng cách 0 của policy thắng fullDeltatime 30",
            AdsPolicy.parse("""{"interstitial_min_gap_seconds":0}""").interstitialVerdict(2, 1, legacyMinGapSeconds = 30))
    }

    @Test fun `vi du doc 62 - app open tu phien thu hai`() {
        val p = AdsPolicy.parse(doc62)
        assertEquals(AdShowFailReason.POLICY_APP_OPEN_SESSION, p.appOpenVerdict(sessionCount = 1))
        assertNull(p.appOpenVerdict(sessionCount = 2))
        assertNull(p.appOpenVerdict(sessionCount = 7))
    }

    // ═══ Parse từng trường (như SdkKillSwitch.parse) ═══

    @Test fun `truong thieu giu mac dinh, truong co thi doc`() {
        val p = AdsPolicy.parse("""{"schema_version":1,"interstitial_min_gap_seconds":30,"post_click_cooldown_seconds":8}""")
        assertEquals(30, p.interstitialMinGapSeconds)
        assertEquals(8_000L, p.postClickCooldownMillis())
        assertEquals(AdsPolicy.DEFAULT.appOpenFromSession, p.appOpenFromSession)
        assertEquals(AdsPolicy.DEFAULT.noInterstitialFirstSession, p.noInterstitialFirstSession)
        assertNull(p.screensBeforeInterstitial)
        assertEquals(AdsPolicy.DEFAULT.appOpenMaxAgeMinutes, p.appOpenMaxAgeMinutes)
    }

    @Test fun `thieu schema_version thi coi la 1`() =
        assertEquals(60, AdsPolicy.parse("""{"interstitial_min_gap_seconds":60}""").interstitialMinGapSeconds)

    @Test fun `so dang chuoi hay so thuc nguyen duoc doc, so le va null bi bo`() {
        assertEquals(60, AdsPolicy.parse("""{"interstitial_min_gap_seconds":"60"}""").interstitialMinGapSeconds)
        assertEquals(60, AdsPolicy.parse("""{"interstitial_min_gap_seconds":60.0}""").interstitialMinGapSeconds)
        assertNull(AdsPolicy.parse("""{"interstitial_min_gap_seconds":60.5}""").interstitialMinGapSeconds)
        assertNull(AdsPolicy.parse("""{"interstitial_min_gap_seconds":null}""").interstitialMinGapSeconds)
        assertEquals(true, AdsPolicy.parse("""{"no_interstitial_first_session":"true"}""").noInterstitialFirstSession)
    }

    @Test fun `gia tri ngoai khoang bi kep lai va log`() {
        mockStatic(Log::class.java).use { log ->
            val p = AdsPolicy.parse(
                """{"interstitial_min_gap_seconds":-5,"app_open_from_session":0,"app_open_max_age_minutes":99999,""" +
                    """"post_click_cooldown_seconds":900,"screens_before_interstitial":51}"""
            )
            assertEquals(0, p.interstitialMinGapSeconds)
            assertEquals(1, p.appOpenFromSession)
            // Google: app open hết hạn sau 4 giờ; cho dài hơn chỉ hiện quảng cáo không được tính.
            assertEquals(240, p.appOpenMaxAgeMinutes)
            assertEquals(60, p.postClickCooldownSeconds)
            assertEquals(50, p.screensBeforeInterstitial)
            log.verify({ Log.e(eq(LogUtil.TAG), contains("ngoài")) }, times(5))
        }
        assertEquals(3600, AdsPolicy.parse("""{"interstitial_min_gap_seconds":99999}""").interstitialMinGapSeconds)
        assertEquals(100, AdsPolicy.parse("""{"app_open_from_session":1000}""").appOpenFromSession)
        assertEquals(1, AdsPolicy.parse("""{"app_open_max_age_minutes":0}""").appOpenMaxAgeMinutes)
    }

    /** `AdsPolicy` dựng thẳng trong code không qua parse: tuổi 0 phút sẽ bỏ mọi app open, quá 4 giờ thì vô ích. */
    @Test fun `tuoi app open duoc kep ca khi dung AdsPolicy truc tiep`() {
        assertEquals(60_000L, AdsPolicy(appOpenMaxAgeMinutes = 0).appOpenMaxAgeMillis())
        assertEquals(APP_OPEN_MAX_AGE_MS, AdsPolicy(appOpenMaxAgeMinutes = 9_999).appOpenMaxAgeMillis())
    }

    // ═══ D5 (DOC/66): interstitial, rewarded, native full hết hạn sau 60 phút ═══

    /** GMA: quảng cáo toàn màn hình hết hạn 1 giờ sau khi load; mặc định 60 phút, không cần Remote Config. */
    @Test fun `tuoi toi da quang cao toan man hinh mac dinh 60 phut`() {
        assertEquals(60, AdsPolicy.DEFAULT_FULLSCREEN_MAX_AGE_MINUTES)
        assertEquals(60, AdsPolicy.DEFAULT.fullscreenMaxAgeMinutes)
        assertEquals(60 * 60_000L, AdsPolicy.DEFAULT.fullscreenMaxAgeMillis())
        assertEquals("khoá vắng giữ mặc định", 60, AdsPolicy.parse("""{"app_open_max_age_minutes":30}""").fullscreenMaxAgeMinutes)
    }

    @Test fun `fullscreen_max_age_minutes duoc doc, so chuoi va so thuc nguyen cung duoc`() {
        assertEquals(30, AdsPolicy.parse("""{"fullscreen_max_age_minutes":30}""").fullscreenMaxAgeMinutes)
        assertEquals(45, AdsPolicy.parse("""{"fullscreen_max_age_minutes":"45"}""").fullscreenMaxAgeMinutes)
        assertEquals(90, AdsPolicy.parse("""{"schema_version":1,"fullscreen_max_age_minutes":90.0}""").fullscreenMaxAgeMinutes)
        assertEquals(30 * 60_000L, AdsPolicy.parse("""{"fullscreen_max_age_minutes":30}""").fullscreenMaxAgeMillis())
    }

    @Test fun `fullscreen_max_age_minutes ngoai khoang bi kep 1 den 240, co log`() {
        mockStatic(Log::class.java).use { log ->
            assertEquals(1, AdsPolicy.parse("""{"fullscreen_max_age_minutes":0}""").fullscreenMaxAgeMinutes)
            assertEquals(240, AdsPolicy.parse("""{"fullscreen_max_age_minutes":99999}""").fullscreenMaxAgeMinutes)
            log.verify({ Log.e(eq(LogUtil.TAG), contains("'fullscreen_max_age_minutes'")) }, times(2))
        }
    }

    @Test fun `fullscreen_max_age_minutes sai kieu thi mac dinh 60, co log, truong khac van doc`() {
        mockStatic(Log::class.java).use { log ->
            val p = AdsPolicy.parse("""{"fullscreen_max_age_minutes":"mot gio","app_open_from_session":2}""")
            assertEquals(60, p.fullscreenMaxAgeMinutes)
            assertEquals(2, p.appOpenFromSession)
            log.verify({ Log.e(eq(LogUtil.TAG), contains("'fullscreen_max_age_minutes'")) }, times(1))
            log.verify({ Log.e(eq(LogUtil.TAG), contains("khoá lạ")) }, never())
        }
    }

    /** `AdsPolicy` dựng thẳng trong code không qua parse: 0 phút sẽ bỏ mọi quảng cáo toàn màn hình. */
    @Test fun `tuoi quang cao toan man hinh duoc kep ca khi dung AdsPolicy truc tiep`() {
        assertEquals(60_000L, AdsPolicy(fullscreenMaxAgeMinutes = 0).fullscreenMaxAgeMillis())
        assertEquals(240 * 60_000L, AdsPolicy(fullscreenMaxAgeMinutes = 9_999).fullscreenMaxAgeMillis())
    }

    /** Điều 7: một trường sai kiểu chỉ bỏ trường đó (có log), không làm rơi các trường hợp lệ. */
    @Test fun `truong sai kieu chi bo truong do, co log`() {
        mockStatic(Log::class.java).use { log ->
            val p = AdsPolicy.parse(
                """{"no_interstitial_first_session":"yes","interstitial_min_gap_seconds":"abc",""" +
                    """"screens_before_interstitial":true,"app_open_from_session":2}"""
            )
            assertEquals(false, p.noInterstitialFirstSession)
            assertNull(p.interstitialMinGapSeconds)
            assertNull(p.screensBeforeInterstitial)
            assertEquals(2, p.appOpenFromSession)
            log.verify({ Log.e(eq(LogUtil.TAG), contains("'no_interstitial_first_session'")) }, times(1))
            log.verify({ Log.e(eq(LogUtil.TAG), contains("'interstitial_min_gap_seconds'")) }, times(1))
            log.verify({ Log.e(eq(LogUtil.TAG), contains("'screens_before_interstitial'")) }, times(1))
        }
    }

    @Test fun `schema la thi dung mac dinh, co log`() {
        mockStatic(Log::class.java).use { log ->
            assertEquals(AdsPolicy.DEFAULT, AdsPolicy.parse("""{"schema_version":2,"interstitial_min_gap_seconds":1}"""))
            assertEquals(AdsPolicy.DEFAULT, AdsPolicy.parse("""{"schema_version":"x","interstitial_min_gap_seconds":1}"""))
            log.verify({ Log.e(eq(LogUtil.TAG), contains("schema")) }, times(2))
        }
    }

    @Test fun `json hong thi log`() {
        mockStatic(Log::class.java).use { log ->
            assertEquals(AdsPolicy.DEFAULT, AdsPolicy.parse("{ rac"))
            log.verify({ Log.e(eq(LogUtil.TAG), contains(AdsPolicy.REMOTE_KEY)) }, times(1))
        }
    }

    /** Khoá gõ sai (ví dụ thiếu chữ s) không được im lặng bị bỏ: ops phải thấy trong log. */
    @Test fun `khoa la duoc log va bo qua`() {
        mockStatic(Log::class.java).use { log ->
            val p = AdsPolicy.parse("""{"interstitial_min_gap_second":10,"app_open_from_session":3}""")
            assertNull(p.interstitialMinGapSeconds)
            assertEquals(3, p.appOpenFromSession)
            log.verify({ Log.e(eq(LogUtil.TAG), contains("'interstitial_min_gap_second'")) }, times(1))
        }
    }

    @Test fun `json hop le khong log loi`() {
        mockStatic(Log::class.java).use { log ->
            AdsPolicy.parse(doc62)
            log.verify({ Log.e(anyString(), anyString()) }, never())
        }
    }

    // ═══ by_tier (1.9.0 Task 4, hợp đồng B): tier theo country, khối sai bỏ cả khối ═══

    private fun tiered(vararg blocks: String, top: String = """"interstitial_min_gap_seconds":240""") =
        """{"schema_version":1,$top,"by_tier":{${blocks.joinToString(",")}}}"""

    private val t1Gap300 = """"T1":{"interstitial_min_gap_seconds":300}"""

    /** Một khối `by_tier` sai: top-level giữ nguyên (240), đúng một logE, kể cả khi có tier hợp lệ cạnh nó. */
    private fun assertBlockRejected(vararg blocks: String) {
        country("US")
        mockStatic(Log::class.java).use { log ->
            assertEquals(240, AdsPolicy.parse(tiered(*blocks)).interstitialMinGapSeconds)
            log.verify({ Log.e(eq(LogUtil.TAG), anyString()) }, times(1))
        }
    }

    @Test fun `P1 by_tier T1 ap cho country US`() {
        country("US")
        assertEquals(300, AdsPolicy.parse(tiered(t1Gap300)).interstitialMinGapSeconds)
    }

    @Test fun `P2 country VN dung tier VN`() {
        country("VN")
        val p = AdsPolicy.parse(tiered(t1Gap300, """"VN":{"interstitial_min_gap_seconds":60,"app_open_from_session":3}"""))
        assertEquals(60, p.interstitialMinGapSeconds)
        assertEquals(3, p.appOpenFromSession)
    }

    @Test fun `P3 country DEFAULT hay la dung tier OTHER`() {
        val raw = tiered(t1Gap300, """"OTHER":{"interstitial_min_gap_seconds":500}""")
        for (c in listOf("DEFAULT", "", "ZZ", "br")) {
            country(c)
            assertEquals("country=$c", 500, AdsPolicy.parse(raw).interstitialMinGapSeconds)
        }
    }

    @Test fun `P4 tier la bo ca khoi, tier hop le canh no cung bi bo (dac ta)`() =
        assertBlockRejected(t1Gap300, """"T9":{"interstitial_min_gap_seconds":10}""")

    @Test fun `P5 truong la trong tier bo ca khoi, ke ca gma_load_app_context (dac ta)`() {
        assertBlockRejected(t1Gap300, """"T2":{"gma_load_app_context":true}""")
        assertBlockRejected(t1Gap300, """"T2":{"interstitial_min_gap_second":10}""")
    }

    @Test fun `P6 by_tier long hay schema_version trong tier bo ca khoi (dac ta)`() {
        assertBlockRejected(t1Gap300, """"T2":{"by_tier":{}}""")
        assertBlockRejected(t1Gap300, """"T2":{"schema_version":1}""")
    }

    /** P13 (I1): tier hay `by_tier` không phải object: bỏ cả khối, không ném, đúng một logE. */
    @Test fun `P13 by_tier hay tier khong phai object bo ca khoi, khong nem`() {
        assertBlockRejected(t1Gap300, """"T2":5""")
        assertBlockRejected(t1Gap300, """"T2":null""")
        for (v in listOf("[]", "null", "5", "\"x\"")) {
            country("US")
            mockStatic(Log::class.java).use { log ->
                val raw = """{"schema_version":1,"interstitial_min_gap_seconds":240,"by_tier":$v}"""
                assertEquals("by_tier=$v", 240, AdsPolicy.parse(raw).interstitialMinGapSeconds)
                log.verify({ Log.e(eq(LogUtil.TAG), anyString()) }, times(1))
            }
        }
    }

    @Test fun `P7 gia tri trong tier bi kep nhu top-level`() {
        country("US")
        val p = AdsPolicy.parse(tiered(""""T1":{"interstitial_min_gap_seconds":99999,"screens_before_interstitial":-5,"app_open_max_age_minutes":0,"fullscreen_max_age_minutes":9999}"""))
        assertEquals(3600, p.interstitialMinGapSeconds)
        assertEquals(0, p.screensBeforeInterstitial)
        assertEquals(1, p.appOpenMaxAgeMinutes)
        assertEquals(240, p.fullscreenMaxAgeMinutes)
    }

    @Test fun `P8 schema_version 2 co by_tier van la mac dinh (dac ta, D6)`() {
        country("US")
        assertEquals(AdsPolicy.DEFAULT, AdsPolicy.parse(tiered(t1Gap300).replace("\"schema_version\":1", "\"schema_version\":2")))
    }

    @Test fun `P9 tier thieu la top-level, truong vang trong tier giu top-level`() {
        country("PL")   // T2, khối chỉ có T1
        assertEquals(240, AdsPolicy.parse(tiered(t1Gap300)).interstitialMinGapSeconds)
        country("US")
        val p = AdsPolicy.parse(tiered(t1Gap300, top = """"interstitial_min_gap_seconds":240,"app_open_from_session":4"""))
        assertEquals(300, p.interstitialMinGapSeconds)
        assertEquals(4, p.appOpenFromSession)
    }

    @Test fun `P11 log ghi tier va nguon`() {
        LogUtil.setFlagLog(3)
        mockStatic(Log::class.java).use { log ->
            country("US")
            AdsPolicy.parse(tiered(t1Gap300))
            log.verify({ Log.d(eq(LogUtil.TAG), contains("ads policy schema=1 tier=T1 source=by_tier")) }, times(1))
            country("PL")
            AdsPolicy.parse(tiered(t1Gap300))
            log.verify({ Log.d(eq(LogUtil.TAG), contains("ads policy schema=1 tier=T2 source=top")) }, times(1))
        }
    }

    // ═══ Lưu cho lần mở kế (như SdkKillSwitch) ═══

    @Test fun `luu ban tho, lan mo ke khoi phuc tu ban luu`() {
        val (context, editor) = prefsContext(saved = null)
        AdsPolicy.save(context, doc62)
        verify(editor).putString(AdsPolicy.PREF_KEY, doc62)

        assertEquals(AdsPolicy.parse(doc62), AdsPolicy.restore(prefsContext(saved = doc62).first))
        assertEquals(AdsPolicy.DEFAULT, AdsPolicy.restore(prefsContext(saved = null).first))
    }

    /** Điều 7: đọc hay lưu lỗi thì về mặc định (hành vi cũ), không ném, có log. */
    @Test fun `doc hay luu loi thi mac dinh, khong nem, co log`() {
        val context = mock(Context::class.java)
        `when`(context.getSharedPreferences(anyString(), anyInt())).thenThrow(IllegalStateException("disk"))
        mockStatic(Log::class.java).use { log ->
            assertEquals(AdsPolicy.DEFAULT, AdsPolicy.restore(context))
            AdsPolicy.save(context, doc62)
            AdsPolicy.save(null, doc62)
            log.verify({ Log.e(eq(LogUtil.TAG), contains(AdsPolicy.REMOTE_KEY)) }, times(3))
        }
    }

    private fun prefsContext(saved: String?): Pair<Context, SharedPreferences.Editor> {
        val editor = mock(SharedPreferences.Editor::class.java)
        `when`(editor.putString(anyString(), anyString())).thenReturn(editor)
        val prefs = mock(SharedPreferences::class.java)
        `when`(prefs.edit()).thenReturn(editor)
        `when`(prefs.getString(AdsPolicy.PREF_KEY, "")).thenReturn(saved ?: "")
        val context = mock(Context::class.java)
        `when`(context.getSharedPreferences(anyString(), anyInt())).thenReturn(prefs)
        return context to editor
    }

    // S7 (R-T4-2 a): khối by_tier sai chỉ logE một lần dù restore trước rồi country biết sau (nhiều lần)
    @Test fun `S7 logE by_tier mot lan qua restore va ap lai khi biet country`() {
        LogUtil.setFlagLog(1)
        val defaultApplier = AdsPolicyTiers.applier
        val raw = """{"schema_version":1,"by_tier":{"T8":{}}}"""
        try {
            AdsPolicyTiers.applier = {}
            country("")
            mockStatic(Log::class.java).use { log ->
                AdsPolicy.restore(prefsContext(saved = raw).first)
                country("US")
                AdsPolicyTiers.onCountryKnown()
                AdsPolicyTiers.onCountryKnown()
                log.verify({ Log.e(eq(LogUtil.TAG), contains("by_tier sai")) }, times(1))
            }
        } finally {
            AdsPolicyTiers.applier = defaultApplier
            AdsPolicyTiers.remember("")
        }
    }

    // S7b (R-T4-2 a, M23): logE cấp trường cũng chỉ một lần mỗi trường qua restore rồi áp lại khi biết country,
    // và bản áp lại vẫn mang đúng giá trị by_tier của tier mới.
    @Test fun `S7b logE tung truong mot lan qua restore va ap lai khi biet country`() {
        LogUtil.setFlagLog(1)
        val defaultApplier = AdsPolicyTiers.applier
        val raw = """{"schema_version":1,"screens_before_interstitial":"abc","by_tier":{"T1":{"interstitial_min_gap_seconds":5}}}"""
        val applied = mutableListOf<AdsPolicy>()
        try {
            AdsPolicyTiers.applier = { applied += it }
            country("")
            mockStatic(Log::class.java).use { log ->
                AdsPolicy.restore(prefsContext(saved = raw).first)
                country("US")
                AdsPolicyTiers.onCountryKnown()
                AdsPolicyTiers.onCountryKnown()
                log.verify({ Log.e(eq(LogUtil.TAG), contains("'screens_before_interstitial'")) }, times(1))
            }
            assertEquals(listOf(5, 5), applied.map { it.interstitialMinGapSeconds })
        } finally {
            AdsPolicyTiers.applier = defaultApplier
            AdsPolicyTiers.remember("")
        }
    }
}
