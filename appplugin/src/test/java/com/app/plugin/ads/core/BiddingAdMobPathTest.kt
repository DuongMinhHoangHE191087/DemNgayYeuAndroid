package com.app.plugin.ads.core

import android.app.Activity
import android.content.Context
import android.util.Log
import com.app.plugin.SdkKillSwitch
import com.app.plugin.ads.AdSdkStartup
import com.app.plugin.ads.consent.AdRequestFactory
import com.app.plugin.consent.ConsentManager
import com.app.plugin.consent.ConsentState
import com.app.plugin.consent.contextWithTcf
import com.app.plugin.logevent.AdEventLog
import com.app.plugin.until.LogUtil
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentInformation
import com.google.android.ump.UserMessagingPlatform
import com.google.firebase.Firebase
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.analytics
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockConstruction
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.mockingDetails
import org.mockito.Mockito.times
import org.mockito.Mockito.`when`

/**
 * 1.9.0 Task 1 (B11, plan-inputs §b "`pangle` on does not touch the AdMob path"): adapter pangle có trên classpath test;
 * registry `{meta, pangle}` so với `{meta}` cho cùng (1) lời gọi `AdRequest.Builder` của `AdRequestFactory.build` (extras),
 * (2) `MobileAds`: đúng một `initialize`, không `setRequestConfiguration`/lời gọi nào khác, (3) danh sách unit và map
 * placement → mạng, (4) tham số `ad_request`; (5) registry log `pangle present`.
 */
class BiddingAdMobPathTest {
    @Before fun setUp() {
        ConsentState.resetForTest()
        AdSdkStartup.resetForTest()
        SdkKillSwitch.current = SdkKillSwitch.NONE
        LogUtil.setFlagLog(1)
    }

    @After fun tearDown() {
        AdSdkStartup.resetForTest()
        ConsentState.resetForTest()
    }

    private data class AdMobPath(
        val builderCalls: List<String>,
        val mobileAdsCalls: List<String>,
        val adUnits: List<String>,
        val adRequest: Map<String, Any>,
        val logs: List<String>,
    )

    private fun run(resource: Array<String>): AdMobPath {
        AdSdkStartup.resetForTest()
        // GDPR áp dụng, purpose 1 từ chối: AdRequestFactory gắn extras npa=1 cho AdMobAdapter (đường có extras).
        val ctx = contextWithTcf(gdpr = 1, purposes = "0000000000")
        `when`(ctx.resources.getIdentifier("ads_bidding_partners", "array", "com.example.app")).thenReturn(RES_ID)
        `when`(ctx.resources.getStringArray(RES_ID)).thenReturn(resource)
        val ci = mock(ConsentInformation::class.java)
        `when`(ci.canRequestAds()).thenReturn(true)
        val activity = mock(Activity::class.java)
        `when`(activity.applicationContext).thenReturn(ctx)
        val fa = mock(FirebaseAnalytics::class.java)
        try {
            mockStatic(UserMessagingPlatform::class.java).use { ump ->
                ump.`when`<ConsentInformation> { UserMessagingPlatform.getConsentInformation(any(Context::class.java)) }
                    .thenReturn(ci)
                mockStatic(Class.forName("com.google.firebase.analytics.AnalyticsKt")).use { analyticsKt ->
                    analyticsKt.`when`<FirebaseAnalytics> { Firebase.analytics }.thenReturn(fa)
                    mockStatic(MobileAds::class.java).use { mobileAds ->
                        mockStatic(Log::class.java).use { log ->
                            val logs = mutableListOf<String>()
                            log.`when`<Int> { Log.d(anyString(), anyString()) }.thenAnswer { logs += it.getArgument<String>(1); 0 }
                            log.`when`<Int> { Log.e(anyString(), anyString()) }.thenAnswer { logs += it.getArgument<String>(1); 0 }
                            // Bản giả của AdsMobMy.startNetwork (MobileAds chạy trên coroutine IO, không chạy trong JVM).
                            AdSdkStartup.networkStarters = AdSdkStartup.NetworkStarters(
                                admob = { MobileAds.initialize(it) }, meta = {}, max = {},
                            )
                            AdSdkStartup.requestAdNetworks(ctx)
                            mobileAds.verify({ MobileAds.initialize(any(Context::class.java)) }, times(1))
                            mobileAds.verifyNoMoreInteractions()
                            val builderCalls = mockConstruction(AdRequest.Builder::class.java) { b, _ ->
                                `when`(b.build()).thenReturn(mock(AdRequest::class.java))
                            }.use { built ->
                                AdRequestFactory.build(ctx)
                                built.constructed().flatMap { b -> mockingDetails(b).invocations.map { describe(it.method.name, it.arguments) } }
                            }
                            val request = AdEventLog.LoadRequest("interstitial", "admob", "full_home", "unit-1", "rid-1", "home", 1)
                            return AdMobPath(
                                builderCalls = builderCalls,
                                mobileAdsCalls = listOf("initialize"),
                                adUnits = AdsNetworkRegistry.build().let { r ->
                                    listOf(r.banners, r.ntCls, r.rects, r.natives, r.fulls, r.gifts).map { it.keys.sorted().toString() }
                                },
                                adRequest = AdEventLog.requestData(request, "full_home"),
                                logs = logs.filter { it.startsWith("ads bidding:") },
                            )
                        }
                    }
                }
            }
        } finally {
            ConsentManager.reset(activity)
        }
    }

    private fun describe(name: String, args: Array<Any?>): String =
        name + args.joinToString(",", "(", ")") { (it as? Class<*>)?.name ?: it?.javaClass?.simpleName.toString() }

    // B11
    @Test
    fun `bat pangle khong doi duong AdMob - extras, MobileAds, unit, ad_request giong het`() {
        val metaOnly = run(arrayOf("meta"))
        val withPangle = run(arrayOf("meta", "pangle"))

        assertTrue(
            "AdRequestFactory phải gắn extras npa cho AdMobAdapter: ${metaOnly.builderCalls}",
            metaOnly.builderCalls.any { it.startsWith("addNetworkExtrasBundle(com.google.ads.mediation.admob.AdMobAdapter") },
        )
        assertEquals("extras AdRequest", metaOnly.builderCalls, withPangle.builderCalls)
        assertEquals("MobileAds", metaOnly.mobileAdsCalls, withPangle.mobileAdsCalls)
        assertEquals("ad unit / placement → mạng", metaOnly.adUnits, withPangle.adUnits)
        assertEquals("tham số ad_request", metaOnly.adRequest, withPangle.adRequest)
        assertEquals(listOf("ads bidding: meta present"), metaOnly.logs)
        assertEquals(listOf("ads bidding: meta present", "ads bidding: pangle present"), withPangle.logs)
    }

    private companion object {
        const val RES_ID = 0x7f030002
    }
}
