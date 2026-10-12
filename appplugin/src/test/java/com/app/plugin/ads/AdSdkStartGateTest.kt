package com.app.plugin.ads

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdSdkStartGateTest {
    @Test
    fun `consent on record starts nothing until the app asks`() {
        val gate = AdSdkStartGate()

        assertFalse(gate.onConsentChanged(canRequestAds = true))
        assertFalse(gate.isStarted)
    }

    @Test
    fun `a returning user whose consent is on record starts as soon as the app asks`() {
        val gate = AdSdkStartGate()

        assertTrue(gate.request(canRequestAds = true))
        assertTrue(gate.isStarted)
    }

    @Test
    fun `a first launch waits for the consent answer and then starts once`() {
        val gate = AdSdkStartGate()

        assertFalse(gate.request(canRequestAds = false))
        assertTrue(gate.isWaitingForConsent)
        assertFalse(gate.onConsentChanged(canRequestAds = false))

        assertTrue(gate.onConsentChanged(canRequestAds = true))
        assertFalse(gate.isWaitingForConsent)
        assertFalse(gate.onConsentChanged(canRequestAds = true))
        assertFalse(gate.request(canRequestAds = true))
    }

    @Test
    fun `asking again after the start does not start the networks twice`() {
        val gate = AdSdkStartGate()
        gate.request(canRequestAds = true)

        assertFalse(gate.request(canRequestAds = true))
    }
}
