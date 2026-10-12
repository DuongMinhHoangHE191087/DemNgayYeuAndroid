package com.app.plugin.ads.consent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdRequestFactoryTest {
    @Test
    fun `outside GDPR requests stay personalized`() {
        assertTrue(AdRequestFactory.isPersonalizationAllowed(gdprApplies = null, purposeConsents = null))
        assertTrue(AdRequestFactory.isPersonalizationAllowed(gdprApplies = 0, purposeConsents = "0000"))
    }

    @Test
    fun `under GDPR purpose 1 decides personalization`() {
        assertTrue(AdRequestFactory.isPersonalizationAllowed(gdprApplies = 1, purposeConsents = "1101"))
        assertFalse(AdRequestFactory.isPersonalizationAllowed(gdprApplies = 1, purposeConsents = "0111"))
    }

    @Test
    fun `under GDPR a missing or empty consent string is not consent`() {
        assertFalse(AdRequestFactory.isPersonalizationAllowed(gdprApplies = 1, purposeConsents = null))
        assertFalse(AdRequestFactory.isPersonalizationAllowed(gdprApplies = 1, purposeConsents = ""))
    }
}
