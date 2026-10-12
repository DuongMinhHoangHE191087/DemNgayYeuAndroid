package com.app.plugin.ads

import android.content.Context
import android.view.View
import android.view.ViewGroup
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*

class AdsDeliveryRegressionTest {
    private val dummyCallback = object : AdsLoadInnerCb {
        override fun onLoaded(placement: String, originPl: String, adId: String, ecmp: AdsEcmp?, adspl: AdsPlBase?) = Unit
        override fun onLoadFailed(placement: String, originPl: String, adId: String, error: String) = Unit
    }
    private lateinit var helper: AdsHelper
    private lateinit var network: AdsBaseNative
    private lateinit var pending: AdsLoadInnerCb
    private lateinit var owner: AdsPlBaseNative
    private lateinit var item: AdsEcmp
    private lateinit var parent: ViewGroup
    private lateinit var rendered: View
    private fun field(name: String, value: Any?) {
        AdsHelper::class.java.getDeclaredField(name).apply { isAccessible = true }.set(helper, value)
    }
    @Before fun setup() {
        helper = AdsHelper::class.java.getDeclaredConstructor().apply { isAccessible=true }.newInstance()
        helper.currConfig = AdsConfigOb(mock(Context::class.java)).apply { nativeStepList.add(0) }
        helper.placementsConfigManager = AdsPlacementConfigManager()
        network = mock(AdsBaseNative::class.java)
        field("listAdsNative", mutableMapOf(0 to network))
        doAnswer { pending=it.getArgument(1); null }.`when`(network).loadAd((eq("nt_language") ?: "nt_language"), (any(AdsLoadInnerCb::class.java) ?: dummyCallback))
        parent=mock(ViewGroup::class.java); `when`(parent.isAttachedToWindow).thenReturn(true)
        rendered=mock(View::class.java)
        owner=mock(AdsPlBaseNative::class.java)
        item=AdsEcmp(AdsEcmps(owner, AdsPlacementEcmpBase(owner))).apply { adsId="test";isLoaded=true;adObject=Any() }
        `when`(owner.populateNativeAdView(item)).thenReturn(rendered)
    }
    @Test fun preloadCompletionDeliversPendingNativeWithoutSecondNetworkRequest() {
        helper.loadNative4Show("language", null)
        helper.showNative("language", parent, null)
        pending.onLoaded("nt_language", "nt_language", "test", item, owner)
        verify(parent).addView(rendered)
        verify(network, times(1)).loadAd((eq("nt_language") ?: "nt_language"), (any(AdsLoadInnerCb::class.java) ?: dummyCallback))
        assertNull(item.adObject)
    }
    @Test fun hiddenPendingViewMustNotReceiveLateAd() {
        helper.loadNative4Show("language", null); helper.showNative("language",parent,null)
        helper.hideNative(parent, true)
        pending.onLoaded("nt_language","nt_language","test",item,owner)
        verify(parent,never()).addView(rendered); assertNotNull(item.adObject)
    }
    @Test fun detachedPendingViewMustNotConsumeCachedAd() {
        helper.loadNative4Show("language",null);helper.showNative("language",parent,null)
        `when`(parent.isAttachedToWindow).thenReturn(false)
        pending.onLoaded("nt_language","nt_language","test",item,owner)
        verify(parent,never()).addView(rendered);assertNotNull(item.adObject)
    }
    @Test fun duplicateCompletionCannotAttachAdTwice() {
        helper.loadNative4Show("language",null);helper.showNative("language",parent,null)
        repeat(2) {pending.onLoaded("nt_language","nt_language","test",item,owner)}
        verify(parent,times(1)).addView(rendered)
    }
    @Test fun bannerShowAfterHideRestartsRefreshState() {
        field("listAdsBn", mutableMapOf<Int,AdsBaseBanner>())
        helper.hideBanner();helper.showBanner("home",parent,false,null,null)
        assertTrue(helper.bnFlagRefresh >= 0)
    }
}
