package com.app.plugin.ads

import android.app.Activity
import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.mock

class AdsLifecycleRegressionTest {
    private val first = "ca-app-pub-3940256099942544/first"
    private val second = "ca-app-pub-3940256099942544/second"

    private class Probe : AdsPlBase(mock(Context::class.java), mock(AdsBase::class.java), 0, "full_test") {
        var requests = 0
        override val eventTag: String get() = "test_probe"
        val state get() = adsPlacementEcmp
        override fun onInit() = Unit
        override fun tryLoad() {
            requests++
            state.isLoading = true
            state.isHasLoad = true
            state.adEcmps!!.getEcmpForLoad()!!.isLoading = true
        }
        override fun show(placement: String, adsShowCb: AdsShowCb?, flagShow: Int) = false
        fun complete() {
            state.adEcmps!!.getEcmpForLoad()!!.isLoading = false
            state.isLoading = false
            onFinishLoad()
        }
    }
    private class Callback : AdsLoadInnerCb {
        var success = 0
        var failure = ""
        var item: AdsEcmp? = null
        var owner: AdsPlBase? = null
        override fun onLoaded(placement: String, originPl: String, adId: String, ecmp: AdsEcmp?, adspl: AdsPlBase?) {
            success++; item = ecmp; owner = adspl
        }
        override fun onLoadFailed(placement: String, originPl: String, adId: String, error: String) { failure = error }
    }
    private fun probe() = Probe().apply { setActivity(mock(Activity::class.java)); parserEcpms("$first;$second") }

    @Test fun finishingAnOldCallbackCannotUnlockAReentrantNewRequest() {
        val p = probe(); p.load("full_test", Callback())
        p.state.adEcmps!!.list.first().isLoading = false
        p.state.isLoading = false
        p.load("full_test", Callback())
        p.onFinishLoad()
        assertTrue(p.state.isLoading)
        assertEquals(2, p.requests)
    }
    @Test fun cacheHitDeliversTheAdAndItsOwnerWithoutAnotherRequest() {
        val p = probe()
        val ad = p.state.adEcmps!!.list.first().apply { isLoaded = true; adObject = Any() }
        val cb = Callback(); p.load("nt_test", cb)
        assertEquals(1, cb.success); assertSame(ad, cb.item); assertSame(p, cb.owner); assertEquals(0, p.requests)
    }
    @Test fun emptyCachedObjectMustNotReportSuccessOrBlockFreshLoad() {
        val p = probe(); p.state.adEcmps!!.list.first().isLoaded = true
        val cb = Callback(); p.load("nt_test", cb)
        assertEquals(0, cb.success); assertEquals(1, p.requests)
        assertFalse(p.state.adEcmps!!.list.first().isLoaded)
    }
    @Test fun identicalConfigWhileLoadingDoesNotCancelFallback() {
        val p = probe(); p.load("full_test", Callback()); val inFlight = p.state.adEcmps!!.list.first()
        p.parserEcpms("$first;$second")
        assertFalse(p.state.flag4Load); assertSame(inFlight, p.state.adEcmps!!.getEcmpForLoad())
    }
    @Test fun changedConfigIsAppliedAfterTheCurrentRequestFinishes() {
        val p = probe(); p.load("full_test", Callback()); val inFlight = p.state.adEcmps!!.list.first()
        p.parserEcpms("$second;ca-app-pub-3940256099942544/new")
        assertSame(inFlight, p.state.adEcmps!!.getEcmpForLoad()); assertFalse(p.state.flag4Load)
        p.complete()
        assertEquals(second, p.state.adEcmps!!.list.first().adsId)
        val cb = Callback(); p.load("full_test", cb)
        assertEquals(2, p.requests); assertEquals("", cb.failure)
    }
    @Test fun latestDeferredConfigWinsAndNoAutomaticDuplicateRequest() {
        val p = probe(); p.load("full_test", Callback())
        p.parserEcpms(second); p.parserEcpms(first); p.complete()
        assertEquals(listOf(first), p.state.adEcmps!!.list.map { it.adsId }); assertEquals(1, p.requests)
    }
    @Test fun repeatedLoadedConfigIsIdempotentAndKeepsObject() {
        val p = probe(); val item = p.state.adEcmps!!.list.first().apply { isLoaded=true;adObject=Any() }
        assertFalse(p.state.adEcmps!!.listFromData("$first;$second"))
        assertSame(item, p.state.adEcmps!!.list.first())
    }
    @Test fun idsAreTrimmedDeduplicatedAndDefaultMembershipIsCorrect() {
        val p = probe(); p.setIdsDf(" $first ; $second ")
        p.parserEcpms(" $second ; ;$second;$first ")
        assertEquals(listOf(second, first), p.state.adEcmps!!.list.map { it.adsId })
        assertTrue(p.state.adEcmps!!.list.all { it.isDefault })
    }
}
