package com.app.plugin.consent

import android.content.Context
import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/**
 * "Do not consent" trên form UMP cho IABTCF_PurposeConsents = 0000000000 nhưng ConsentManager vẫn
 * dịch OBTAINED thành GRANTED (đo 2026-09-24). Tier phải đọc từ TCF theo purpose.
 * Luật theo yêu cầu CMP của Google cho AdMob: quảng cáo không cá nhân hoá cần purpose 1 + vendor 755 +
 * purpose 2,7,9,10 (consent hoặc LI); quảng cáo cá nhân hoá cần thêm purpose 3 và 4.
 */
class TcfConsentTest {
    private val googleOk = "0".repeat(754) + "1"

    private fun signals(purposes: String, li: String = "0000000000", vendors: String = googleOk, gdpr: Int? = 1) =
        TcfSignals(gdprApplies = gdpr, purposeConsents = purposes, purposeLegitimateInterests = li, vendorConsents = vendors)

    @Test fun `dong y het thi GRANTED`() =
        assertEquals(ConsentTier.GRANTED, TcfConsent.tierOf(signals("1111111111")))

    @Test fun `khong purpose 3 va 4 thi PARTIAL`() =
        assertEquals(ConsentTier.PARTIAL, TcfConsent.tierOf(signals("1100001011")))

    @Test fun `purpose 2 7 9 10 qua legitimate interest van du cho PARTIAL`() =
        assertEquals(ConsentTier.PARTIAL, TcfConsent.tierOf(signals("1000000000", li = "0100001011")))

    @Test fun `tu choi het thi DENIED`() =
        assertEquals(ConsentTier.DENIED, TcfConsent.tierOf(signals("0000000000")))

    @Test fun `khong co vendor Google thi DENIED`() =
        assertEquals(ConsentTier.DENIED, TcfConsent.tierOf(signals("1111111111", vendors = "1")))

    @Test fun `ngoai pham vi GDPR thi GRANTED`() =
        assertEquals(ConsentTier.GRANTED, TcfConsent.tierOf(signals("0000000000", gdpr = 0)))

    @Test fun `chua co IABTCF thi UNKNOWN`() =
        assertEquals(ConsentTier.UNKNOWN, TcfConsent.tierOf(signals("", gdpr = null)))

    // ═══ Hiến pháp Điều 13: tín hiệu thiếu hay hỏng không bao giờ mở rộng quyền ═══

    @Test fun `GDPR ap dung ma chuoi purpose rong thi DENIED chu khong GRANTED`() =
        assertEquals(ConsentTier.DENIED, TcfConsent.tierOf(signals("", li = "", vendors = "")))

    @Test fun `chuoi purpose co ky tu la thi khong tinh la dong y`() =
        assertEquals(ConsentTier.DENIED, TcfConsent.tierOf(signals("x111111111")))

    // ═══ Đọc SharedPreferences ═══

    @Test
    fun `doc dung cac khoa IABTCF ma UMP ghi`() {
        val prefs = mock(SharedPreferences::class.java)
        `when`(prefs.contains("IABTCF_gdprApplies")).thenReturn(true)
        `when`(prefs.getInt("IABTCF_gdprApplies", 0)).thenReturn(1)
        `when`(prefs.getString("IABTCF_PurposeConsents", "")).thenReturn("0000000000")
        `when`(prefs.getString("IABTCF_PurposeLegitimateInterests", "")).thenReturn("0100001011")
        `when`(prefs.getString("IABTCF_VendorConsents", "")).thenReturn(googleOk)

        assertEquals(
            TcfSignals(gdprApplies = 1, purposeConsents = "0000000000", purposeLegitimateInterests = "0100001011", vendorConsents = googleOk),
            TcfConsent.read(prefs)
        )
    }

    @Test
    fun `chua co khoa gdprApplies thi gdprApplies la null`() {
        val prefs = mock(SharedPreferences::class.java)
        `when`(prefs.contains("IABTCF_gdprApplies")).thenReturn(false)

        val s = TcfConsent.read(prefs)

        assertEquals(null, s.gdprApplies)
        assertEquals(ConsentTier.UNKNOWN, TcfConsent.tierOf(s))
    }

    @Test
    fun `khoa sai kieu thi coi nhu chua co TCF chu khong crash`() {
        val prefs = mock(SharedPreferences::class.java)
        `when`(prefs.contains(anyString())).thenReturn(true)
        `when`(prefs.getInt(anyString(), anyInt())).thenThrow(ClassCastException("String cannot be cast to Integer"))

        assertEquals(ConsentTier.UNKNOWN, TcfConsent.tierOf(TcfConsent.read(prefs)))
    }

    // ═══ gdprApplies: "không biết" không phải "không áp dụng" ═══

    private fun contextWith(prefs: SharedPreferences): Context {
        val context = mock(Context::class.java)
        `when`(context.packageName).thenReturn("com.example.app")
        `when`(context.getSharedPreferences(anyString(), anyInt())).thenReturn(prefs)
        return context
    }

    private fun contextWithGdpr(gdpr: Int?): Context {
        val prefs = mock(SharedPreferences::class.java)
        `when`(prefs.contains("IABTCF_gdprApplies")).thenReturn(gdpr != null)
        if (gdpr != null) `when`(prefs.getInt("IABTCF_gdprApplies", 0)).thenReturn(gdpr)
        return contextWith(prefs)
    }

    @Test fun `chua co khoa gdprApplies thi gdprApplies(context) la null chu khong phai false`() =
        assertNull(TcfConsent.gdprApplies(contextWithGdpr(null)))

    @Test fun `gdprApplies bang 0 thi false`() =
        assertEquals(false, TcfConsent.gdprApplies(contextWithGdpr(0)))

    @Test fun `gdprApplies bang 1 thi true`() =
        assertEquals(true, TcfConsent.gdprApplies(contextWithGdpr(1)))

    @Test
    fun `gdprApplies sai kieu thi null chu khong phai false`() {
        val prefs = mock(SharedPreferences::class.java)
        `when`(prefs.contains(anyString())).thenReturn(true)
        `when`(prefs.getInt(anyString(), anyInt())).thenThrow(ClassCastException("String cannot be cast to Integer"))

        assertNull(TcfConsent.gdprApplies(contextWith(prefs)))
    }

    // ═══ Ghép với trạng thái UMP ═══

    @Test fun `OBTAINED thi TCF quyet dinh du UMP cho request ads`() =
        assertEquals(ConsentTier.DENIED, TcfConsent.resolve(umpObtained = true, umpTier = ConsentTier.GRANTED, tcfTier = ConsentTier.DENIED))

    @Test fun `OBTAINED ma khong doc duoc TCF thi khong bao gio GRANTED`() =
        assertEquals(ConsentTier.UNKNOWN, TcfConsent.resolve(umpObtained = true, umpTier = ConsentTier.GRANTED, tcfTier = ConsentTier.UNKNOWN))

    @Test fun `OBTAINED khong TCF va UMP khong cho request ads thi DENIED`() =
        assertEquals(ConsentTier.DENIED, TcfConsent.resolve(umpObtained = true, umpTier = ConsentTier.DENIED, tcfTier = ConsentTier.UNKNOWN))

    @Test fun `NOT_REQUIRED giu tier cua UMP`() =
        assertEquals(ConsentTier.GRANTED, TcfConsent.resolve(umpObtained = false, umpTier = ConsentTier.GRANTED, tcfTier = ConsentTier.UNKNOWN))

    @Test fun `REQUIRED chua tra loi thi TCF cu khong mo rong quyen`() =
        assertEquals(ConsentTier.DENIED, TcfConsent.resolve(umpObtained = false, umpTier = ConsentTier.DENIED, tcfTier = ConsentTier.GRANTED))
}
