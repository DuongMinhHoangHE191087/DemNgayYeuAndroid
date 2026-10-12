package com.app.plugin.ads

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AdDeliveryDeviceTest {
    private class Network(context: Context) : AdsBaseNative(0, context) {
        lateinit var completion: AdsLoadInnerCb
        var requests=0
        override fun getKeyForDataSave()="audit_test_cache"
        override fun getKeyForResDf()="audit_test_defaults"
        override fun getNewAdsPl(placement:String):AdsPlBase?=null
        override fun loadAd(placement:String,adsLoadCb:AdsLoadInnerCb) {requests++;completion=adsLoadCb}
        override fun getAdForShow(placement:String,adsShowCb:AdsShowCb):Any?=null
    }
    private class Native(context:Context,network:Network) : AdsPlBaseNative(context,network,0,"nt_language") {
        override fun onInit()=Unit
        override fun tryLoad()=Unit
        override fun show(placement:String,adsShowCb:AdsShowCb?,flagShow:Int)=false
        override fun populateNativeAdView(ecmp:AdsEcmp):View=FrameLayout(mContext)
    }
    private fun withHost(test:(AdsHelper,Network,Native,FrameLayout)->Unit) {
        ActivityScenario.launch(AdLifecycleTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val parent=FrameLayout(activity);activity.setContentView(parent)
                val helper=AdsHelper::class.java.getDeclaredConstructor().apply {isAccessible=true}.newInstance()
                helper.currConfig=AdsConfigOb(activity).apply {nativeStepList.add(0)}
                helper.placementsConfigManager=AdsPlacementConfigManager()
                val network=Network(activity)
                AdsHelper::class.java.getDeclaredField("listAdsNative").apply {isAccessible=true}.set(helper,mutableMapOf(0 to network))
                // ActivityScenario is resumed; decor is attached. Parent attachment is checked below.
                assertTrue(parent.isAttachedToWindow)
                test(helper,network,Native(activity,network),parent)
            }
        }
    }
    @Test fun preloadThenShowAttachesExactlyOnceToRealAndroidView() = withHost { helper,network,owner,parent ->
        helper.loadNative4Show("language",null);helper.showNative("language",parent,null)
        val item=AdsEcmp(AdsEcmps(owner,AdsPlacementEcmpBase(owner))).apply {adsId="synthetic";isLoaded=true;adObject=Any()}
        repeat(2) {network.completion.onLoaded("nt_language","nt_language","synthetic",item,owner)}
        assertEquals(1,parent.childCount);assertEquals(1,network.requests);assertFalse(item.isLoaded)
    }
    @Test fun hiddenViewDoesNotReceiveLateCompletion() = withHost { helper,network,owner,parent ->
        helper.loadNative4Show("language",null);helper.showNative("language",parent,null);helper.hideNative(parent,true)
        val item=AdsEcmp(AdsEcmps(owner,AdsPlacementEcmpBase(owner))).apply {adsId="synthetic";isLoaded=true;adObject=Any()}
        network.completion.onLoaded("nt_language","nt_language","synthetic",item,owner)
        assertEquals(0,parent.childCount);assertTrue(item.isLoaded);assertNotNull(item.adObject)
    }
}
