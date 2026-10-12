package com.app.plugin.ads

import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*

class AdsOptimizationPolicyTest {
    @Test fun bannerDefaultsToAdaptiveAndSixtySecondsWithBoundedRefresh() {
        val policy=AdsOptimizationPolicy()
        assertTrue(policy.adaptiveBanner)
        assertEquals(60,policy.bannerRefreshSeconds)
        policy.bannerRefreshSeconds=1;assertEquals(30,policy.bannerRefreshSeconds)
        policy.bannerRefreshSeconds=1000;assertEquals(150,policy.bannerRefreshSeconds)
        policy.bannerRefreshSeconds=0;assertEquals(0,policy.bannerRefreshSeconds)
    }
    @Test fun reuseRequiresExplicitCompatibleSourceAndUnconsumedFreshAd() {
        val owner=mock(AdsPlBase::class.java)
        val item=AdsEcmp(AdsEcmps(owner,AdsPlacementEcmpBase(owner)))
        item.adObject=Any();item.isLoaded=true
        val now=item.loadedAtMillis
        assertTrue(item.isFreshForReuse(now))
        assertFalse(item.isFreshForReuse(now+3_600_000))
        item.isLoaded=false;assertFalse(item.isFreshForReuse(now))
        item.isLoaded=true;item.adObject=null;assertFalse(item.isFreshForReuse(now))
    }
    @Test fun reuseUsesTheDonorObjectWithTargetAttributionAndNeverLoads() {
        val network=mock(AdsBaseNative::class.java, CALLS_REAL_METHODS)
        val donor=mock(AdsPlBase::class.java)
        val callback=mock(AdsShowCb::class.java)
        val result=Any()
        network.mapAdsPl=mutableMapOf("nt_language" to donor)
        `when`(donor.hasFreshAdForReuse()).thenReturn(true)
        `when`(donor.getAdForShow("nt_home",callback)).thenReturn(result)
        assertSame(result,network.getReusableAd("nt_language","nt_home",callback))
        `when`(donor.hasFreshAdForReuse()).thenReturn(false)
        assertNull(network.getReusableAd("nt_language","nt_home",callback))
        assertNull(network.getReusableAd("nt_missing","nt_home",callback))
        verify(donor,times(1)).getAdForShow("nt_home",callback)
    }
    @Test fun reuseConfigurationDoesNotAllowSelfReferenceOrDuplicates() {
        val policy=AdsOptimizationPolicy()
        policy.setNativeReuseSources("nt_home",listOf("nt_home","nt_language","nt_language"))
        assertEquals(listOf("nt_language"),policy.nativeReuseSources("nt_home"))
        policy.setNativeReuseSources("nt_home",emptyList())
        assertTrue(policy.nativeReuseSources("nt_home").isEmpty())
    }
}
