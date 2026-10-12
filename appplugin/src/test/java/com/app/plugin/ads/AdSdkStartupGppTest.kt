package com.app.plugin.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.app.plugin.ads.adsfb.AdsFbMy
import com.app.plugin.app.MetaSdkStarter
import com.app.plugin.brain.HeldExecutor
import com.app.plugin.consent.ConsentManager
import com.app.plugin.consent.ConsentRules
import com.app.plugin.consent.ConsentState
import com.app.plugin.consent.ConsentTier
import com.app.plugin.consent.FcmConsent
import com.app.plugin.consent.GPP_US_OK
import com.app.plugin.consent.GPP_US_OPT_OUT
import com.app.plugin.consent.applicationWithTcf
import com.app.plugin.consent.contextWithTcf
import com.app.plugin.until.LogUtil
import com.appsflyer.AppsFlyerLib
import com.facebook.FacebookSdk
import com.facebook.ads.AdSettings
import com.facebook.ads.AudienceNetworkAds
import com.google.android.gms.tasks.Task
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.FirebaseAnalytics.ConsentStatus
import com.google.firebase.analytics.FirebaseAnalytics.ConsentType
import com.google.firebase.messaging.FirebaseMessaging
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.contains
import org.mockito.ArgumentMatchers.eq
import org.mockito.MockedStatic
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.util.concurrent.Executor

/**
 * 1.6.0 Task 8a (O2): người Mỹ đổi lựa chọn GPP qua Privacy options mà tầng không đổi (vẫn `GRANTED`) —
 * `ConsentState.onChange` không bắn, nên listener UMP (`ConsentManager.addConsentUpdatedListener`) của
 * [AdSdkStartup] đánh giá lại GPP và chạy lại `applyTier`: Consent Mode, rồi mạng đã khởi tạo. Tầng không đổi.
 */
class AdSdkStartupGppTest {
    private val fcmExecutor = FcmConsent.executor
    private val metaThread = HeldExecutor()

    @Before fun setUp() {
        ConsentState.resetForTest()
        AdSdkStartup.resetForTest()
        FcmConsent.executor = Executor { it.run() }
        AdSdkStartup.networkStarters = AdSdkStartup.NetworkStarters(admob = {}, meta = {}, max = {})
    }

    @After fun tearDown() {
        FcmConsent.executor = fcmExecutor
        AdSdkStartup.resetForTest()
        ConsentState.resetForTest()
    }

    private class Sinks(
        val fa: FirebaseAnalytics,
        val fb: MockedStatic<FacebookSdk>,
        val log: MockedStatic<Log>,
        val an: MockedStatic<AdSettings>,
    )

    /** UMP trả [ci] (OBTAINED, cập nhật thành công ngay); Firebase, FCM, AppsFlyer, Meta là mock. */
    private fun withSinks(ci: ConsentInformation, activity: Activity, block: (Sinks) -> Unit) {
        val fa = mock(FirebaseAnalytics::class.java)
        val fcm = mock(FirebaseMessaging::class.java)
        @Suppress("UNCHECKED_CAST")
        val tokenTask = mock(Task::class.java) as Task<String>
        `when`(fcm.token).thenReturn(tokenTask)
        mockStatic(UserMessagingPlatform::class.java).use { ump ->
            ump.`when`<ConsentInformation> { UserMessagingPlatform.getConsentInformation(any(Context::class.java)) }
                .thenReturn(ci)
            mockStatic(FirebaseAnalytics::class.java).use { firebase ->
                firebase.`when`<FirebaseAnalytics> { FirebaseAnalytics.getInstance(any()) }.thenReturn(fa)
                mockStatic(FirebaseMessaging::class.java).use { messaging ->
                    messaging.`when`<FirebaseMessaging> { FirebaseMessaging.getInstance() }.thenReturn(fcm)
                    mockStatic(AppsFlyerLib::class.java).use { appsFlyer ->
                        appsFlyer.`when`<AppsFlyerLib> { AppsFlyerLib.getInstance() }
                            .thenReturn(mock(AppsFlyerLib::class.java))
                        mockStatic(AdSettings::class.java).use { an ->
                            mockStatic(FacebookSdk::class.java).use { fb ->
                                mockStatic(Log::class.java).use { log ->
                                    try {
                                        block(Sinks(fa, fb, log, an))
                                    } finally {
                                        ConsentState.resetForTest()
                                        ConsentManager.reset(activity)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun obtainedUmp(): ConsentInformation {
        val ci = mock(ConsentInformation::class.java)
        `when`(ci.canRequestAds()).thenReturn(true)
        `when`(ci.consentStatus).thenReturn(ConsentInformation.ConsentStatus.OBTAINED)
        doAnswer { inv ->
            inv.getArgument<ConsentInformation.OnConsentInfoUpdateSuccessListener>(2).onConsentInfoUpdateSuccess()
            null
        }.`when`(ci).requestConsentInfoUpdate(
            any(Activity::class.java),
            any(ConsentRequestParameters::class.java),
            any(ConsentInformation.OnConsentInfoUpdateSuccessListener::class.java),
            any(ConsentInformation.OnConsentInfoUpdateFailureListener::class.java)
        )
        return ci
    }

    private fun activityOf(context: Context): Activity {
        val resources = context.resources
        val activity = mock(Activity::class.java)
        `when`(activity.applicationContext).thenReturn(context)
        `when`(activity.resources).thenReturn(resources)
        return activity
    }

    @Test
    fun `UMP cap nhat, tang GRANTED khong doi, GPP thanh opt-out thi applyTier chay lai, khong setTier`() {
        var gpp: String? = GPP_US_OK
        val app = applicationWithTcf(gdpr = 1, purposes = "1111111111", gpp = { gpp })
        val activity = activityOf(app)
        MetaSdkStarter.facebookCoreAvailable = { true }
        MetaSdkStarter.initializer = {}
        MetaSdkStarter.executor = metaThread

        withSinks(obtainedUmp(), activity) { s ->
            ConsentManager.updateConsentInfo(activity) {}   // GRANTED từ TCF
            AdSdkStartup.requestAnalytics(app)               // cổng mở: applyTier với GPP "ok"
            MetaSdkStarter.start(app)                        // Meta SDK đã khởi tạo trong process
            verify(s.fa, times(1)).setConsent(map(true, true, true, true))
            val tierChanges = mutableListOf<ConsentTier>()
            ConsentState.onChange { tierChanges += it }

            gpp = GPP_US_OPT_OUT   // người dùng chọn "Do not sell or share" qua Privacy options
            ConsentManager.updateConsentInfo(activity) {}

            assertEquals("tầng không đổi", ConsentTier.GRANTED, ConsentState.tier())
            assertEquals("không có setTier mới", emptyList<ConsentTier>(), tierChanges)
            assertTrue("tierAllows không đổi", ConsentRules.tierAllows(ConsentState.tier(), true))
            verify(s.fa, times(1)).setConsent(map(true, true, false, false))
            assertEquals(true, AdSdkStartup.appliedGpp?.usOptOut)
            s.log.verify { Log.d(eq(LogUtil.TAG), contains("consent updated, tier unchanged: gpp=optOut")) }
            metaThread.runAll()
            s.fb.verify({ FacebookSdk.setDataProcessingOptions(eq(arrayOf("LDU")), eq(0), eq(0)) }, times(1))

            // Lần cập nhật UMP kế, GPP như cũ: không áp lại.
            ConsentManager.updateConsentInfo(activity) {}
            verify(s.fa, times(1)).setConsent(map(true, true, false, false))
            metaThread.runAll()
            s.fb.verify({ FacebookSdk.setDataProcessingOptions(any(), anyInt(), anyInt()) }, times(1))
        }
    }

    /** Tầng đổi: listener tầng đã chạy `applyTier` (GPP mới) trong `setTier`; listener UMP không chạy lần hai. */
    @Test
    fun `tang doi cung luc GPP doi thi applyTier chi chay mot lan cho GPP moi`() {
        var gpp: String? = null
        val ctx = contextWithTcf(gdpr = 1, purposes = "1111111111", gpp = { gpp })
        val activity = activityOf(ctx)

        withSinks(obtainedUmp(), activity) { s ->
            AdSdkStartup.requestAnalytics(ctx)   // UNKNOWN trong GDPR: denied hết
            gpp = GPP_US_OPT_OUT

            ConsentManager.updateConsentInfo(activity) {}   // → GRANTED (listener tầng) + GPP opt-out

            assertEquals(ConsentTier.GRANTED, ConsentState.tier())
            verify(s.fa, times(1)).setConsent(map(true, true, false, false))
            s.log.verify({ Log.d(eq(LogUtil.TAG), contains("consent updated, tier unchanged")) }, never())
        }
    }

    /**
     * Review Task 8a m1: GPP áp lại giữa phiên cho Meta AN **đã khởi tạo** (`applyGppToStartedNetworks` →
     * `AdsFbMy.followGpp`), kể cả khi AN đã được khởi tạo trước (adapter GMA: `isInitialized` = true) và khi tầng
     * thành `DENIED` (opt-out bang Mỹ không xét `tierAllows`). Đỏ khi: bỏ `AdsFbMy.instance.followGpp(gpp)`; gác bước
     * này bằng `tierAllows`; đặt `networkStarted = true` sau nhánh `isInitialized` return sớm.
     */
    @Test
    fun `GPP thanh opt-out giua phien thi Meta AN da khoi tao nhan LDU, ke ca khi tang DENIED`() {
        var gpp: String? = GPP_US_OK
        val app = applicationWithTcf(gdpr = 1, purposes = "1111111111", gpp = { gpp })
        val activity = activityOf(app)
        AdSdkStartup.networkStarters = AdSdkStartup.NetworkStarters(
            admob = {},
            meta = { AdsFbMy.instance.startNetwork(it) },
            max = {},
        )

        withSinks(obtainedUmp(), activity) { s ->
            mockStatic(AudienceNetworkAds::class.java).use { audience ->
                // AN đã khởi tạo trong process (adapter GMA Meta khởi tạo trước SDK).
                audience.`when`<Boolean> { AudienceNetworkAds.isInitialized(any()) }.thenReturn(true)
                ConsentManager.updateConsentInfo(activity) {}   // GRANTED từ TCF
                AdSdkStartup.requestAnalytics(app)
                AdSdkStartup.requestAdNetworks(app)              // AN start, GPP "ok" → LDU tắt
                s.an.verify({ AdSettings.setDataProcessingOptions(eq(arrayOf<String>())) }, times(1))
                s.an.verify({ AdSettings.setDataProcessingOptions(any(), anyInt(), anyInt()) }, never())

                gpp = GPP_US_OPT_OUT
                ConsentState.setTier(ConsentTier.DENIED)         // listener tầng → applyTier với GPP opt-out

                assertEquals(ConsentTier.DENIED, ConsentState.tier())
                s.an.verify({ AdSettings.setDataProcessingOptions(eq(arrayOf("LDU")), eq(0), eq(0)) }, times(1))
            }
        }
    }

    private companion object {
        fun map(storage: Boolean, ads: Boolean, userData: Boolean, personalization: Boolean) = mapOf(
            ConsentType.ANALYTICS_STORAGE to status(storage),
            ConsentType.AD_STORAGE to status(ads),
            ConsentType.AD_USER_DATA to status(userData),
            ConsentType.AD_PERSONALIZATION to status(personalization),
        )

        fun status(granted: Boolean) = if (granted) ConsentStatus.GRANTED else ConsentStatus.DENIED
    }
}
