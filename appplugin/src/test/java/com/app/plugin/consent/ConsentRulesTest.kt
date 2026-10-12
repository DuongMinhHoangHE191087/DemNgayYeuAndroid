package com.app.plugin.consent

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Đợt sửa cuối Phase B, mục 8: luật "ngoài GDPR thì cấp hết, trừ DENIED" từng chép ở ba consumer
 * (Consent Mode, FCM, AppsFlyer). Một hàm chung để chúng không lệch nhau.
 */
class ConsentRulesTest {
    @Test fun `chi IABTCF_gdprApplies = 0 va tang khong phai DENIED moi cap het`() {
        for (tier in ConsentTier.values()) {
            assertEquals("gdprApplies=false, $tier", tier != ConsentTier.DENIED, ConsentRules.outsideGdprGrantsAll(tier, false))
            assertEquals("gdprApplies=true, $tier", false, ConsentRules.outsideGdprGrantsAll(tier, true))
            assertEquals("gdprApplies=null (khong biet), $tier", false, ConsentRules.outsideGdprGrantsAll(tier, null))
        }
    }

    /**
     * Review C Minor 5: "tầng cho phép" dùng chung (FCM, AppsFlyer, start muộn của Meta AN): GRANTED/PARTIAL ở mọi
     * `gdprApplies`; UNKNOWN chỉ khi `IABTCF_gdprApplies = 0` đọc được rõ ràng; DENIED không bao giờ.
     */
    @Test fun `tierAllows cho GRANTED PARTIAL, hoac ngoai GDPR tru DENIED`() {
        for (gdpr in listOf(true, false, null)) {
            assertEquals("GRANTED/$gdpr", true, ConsentRules.tierAllows(ConsentTier.GRANTED, gdpr))
            assertEquals("PARTIAL/$gdpr", true, ConsentRules.tierAllows(ConsentTier.PARTIAL, gdpr))
            assertEquals("DENIED/$gdpr", false, ConsentRules.tierAllows(ConsentTier.DENIED, gdpr))
            assertEquals("UNKNOWN/$gdpr", gdpr == false, ConsentRules.tierAllows(ConsentTier.UNKNOWN, gdpr))
        }
    }

    /** Log từ chối của cổng Meta (AN và app events): vắng `IABTCF_gdprApplies` tính là trong GDPR. */
    @Test fun `scopeLabel ghi tang va trong hay ngoai GDPR`() {
        assertEquals("consent tier=UNKNOWN in GDPR scope", ConsentRules.scopeLabel(ConsentTier.UNKNOWN, true))
        assertEquals("consent tier=UNKNOWN in GDPR scope", ConsentRules.scopeLabel(ConsentTier.UNKNOWN, null))
        assertEquals("consent tier=DENIED outside GDPR scope", ConsentRules.scopeLabel(ConsentTier.DENIED, false))
    }

    /** 1.6.0 Task 7: opt-out bang Mỹ đọc từ GPP; không có GPP thì không opt-out. */
    @Test fun `usOptOut va gppLabel theo GppSignals`() {
        val optOut = GppSignals(listOf(7), saleOptOut = true, sharingOptOut = false, targetedAdsOptOut = false, gpc = null, raw = "x")
        val ok = GppSignals(listOf(7), saleOptOut = false, sharingOptOut = false, targetedAdsOptOut = false, gpc = true, raw = "x")

        assertEquals(false, ConsentRules.usOptOut(null))
        assertEquals(true, ConsentRules.usOptOut(optOut))
        assertEquals(true, ConsentRules.usOptOut(optOut.copy(saleOptOut = false, sharingOptOut = true)))
        assertEquals(true, ConsentRules.usOptOut(optOut.copy(saleOptOut = false, targetedAdsOptOut = true)))
        assertEquals("GPC không quyết định", false, ConsentRules.usOptOut(ok))
        assertEquals("gpp=none", ConsentRules.gppLabel(null))
        assertEquals("gpp=optOut", ConsentRules.gppLabel(optOut))
        assertEquals("gpp=ok", ConsentRules.gppLabel(ok))
    }

    /**
     * 1.6.0 Task 7 — tài liệu hoá: `tierAllows` không nhận GPP (Task 8 không được thêm tham số — facade), nên có GPP
     * (opt-out hay không) bảng vẫn như cũ. Không bắt được hồi quy do GPP gây ra; các test tier/FCM ở trên mới ghim luật.
     */
    @Test fun `tierAllows khong doi khi co GPP`() {
        val gpps = listOf(null, GppSignals(listOf(7), true, true, true, true, "x"), GppSignals(listOf(8), false, false, false, null, "y"))
        for (gpp in gpps) for (tier in ConsentTier.values()) for (gdpr in listOf(true, false, null)) {
            val expect = tier == ConsentTier.GRANTED || tier == ConsentTier.PARTIAL || (gdpr == false && tier != ConsentTier.DENIED)
            assertEquals("$tier/$gdpr/${ConsentRules.gppLabel(gpp)}", expect, ConsentRules.tierAllows(tier, gdpr))
        }
    }

    @Test fun `FCM dung dung luat tierAllows`() {
        for (tier in ConsentTier.values()) for (gdpr in listOf(true, false, null)) {
            assertEquals("$tier/$gdpr", ConsentRules.tierAllows(tier, gdpr), FcmConsent.allows(tier, gdpr))
        }
    }

    /** Ba consumer dùng đúng luật chung: ngoài GDPR cấp hết, DENIED thắng `0`. */
    @Test fun `ba consumer theo cung luat`() {
        for (tier in ConsentTier.values()) for (gdpr in listOf(true, false, null)) {
            if (!ConsentRules.outsideGdprGrantsAll(tier, gdpr)) continue
            assertEquals("Consent Mode $tier/$gdpr", AnalyticsConsent.Settings(true, true, true, true), AnalyticsConsent.settingsFor(tier, gdpr))
            assertEquals("FCM $tier/$gdpr", true, FcmConsent.allows(tier, gdpr))
            assertEquals(
                "AppsFlyer $tier/$gdpr: ngoai GDPR",
                false,
                com.app.plugin.appsflyer.AppsflyerHelper.consentFor(tier, gdpr).isUserSubjectToGDPR
            )
        }
    }
}
