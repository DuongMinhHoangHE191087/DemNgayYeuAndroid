package com.app.plugin.ads

import android.app.Activity
import android.content.Context
import com.app.plugin.consent.ConsentManager
import com.app.plugin.consent.ConsentState
import com.app.plugin.consent.ConsentTier
import com.app.plugin.consent.FcmConsent
import com.app.plugin.consent.contextWithTcf
import com.appsflyer.AppsFlyerConsent
import com.appsflyer.AppsFlyerLib
import com.appsflyer.attribution.AppsFlyerRequestListener
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.google.android.gms.tasks.Task
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.FirebaseAnalytics.ConsentStatus
import com.google.firebase.analytics.FirebaseAnalytics.ConsentType
import com.google.firebase.messaging.FirebaseMessaging
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.eq
import org.mockito.ArgumentMatchers.same
import org.mockito.MockedStatic
import org.mockito.Mockito.atLeastOnce
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockingDetails
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import java.util.concurrent.Executor

/**
 * Task 9: Firebase Analytics (Consent Mode v2), FCM và AppsFlyer chạy trong task `analytics` của
 * [AdSdkStartup], tức chỉ sau khi UMP trả lời. FCM (auto-init và token) còn chờ tới khi tầng cho
 * phép: PARTIAL/GRANTED, hoặc ngoài GDPR (fix F1).
 *
 * Fix wave 2: Consent Mode và FCM theo tầng từ lúc `requestAnalytics`, kể cả khi cổng chưa mở, để
 * một lần thu hẹp (UMP hết hạn, REQUIRED lại) tới Firebase/FCM ngay; AppsFlyer start trên Activity
 * của luồng consent.
 *
 * `AdSdkStartup.resetForTest` mở lại cổng một-lần giữa các test, và từ Task 16 (addendum A9) đưa cả
 * `AppsflyerHelper.instance` (cờ `started`) và Activity của luồng consent về như
 * process mới. Test không khai `af_app_id` thì AppsFlyer tắt, không chạm `AppsFlyerLib`.
 */
class AdSdkStartupAnalyticsTest {
    private val fcmExecutor = FcmConsent.executor

    @Before fun setUp() {
        ConsentState.resetForTest()
        AdSdkStartup.resetForTest()   // cổng một-lần, Follower FCM, listener tầng (fix wave 2, Minor 8)
        // FCM chạy trên luồng nền của FcmConsent (DOC/66 D1); ở đây chạy thẳng trên luồng test, vì `mockStatic`
        // chỉ có hiệu lực trên luồng tạo nó. Luồng nền và thứ tự: FcmConsentTest.
        FcmConsent.executor = Executor { it.run() }
    }

    @After fun tearDown() {
        FcmConsent.executor = fcmExecutor
        AdSdkStartup.resetForTest()
        ConsentState.resetForTest()
    }

    private class Sdks(
        val fa: FirebaseAnalytics,
        val fcm: FirebaseMessaging,
        val af: AppsFlyerLib,
        val firebase: MockedStatic<FirebaseAnalytics>,
        val messaging: MockedStatic<FirebaseMessaging>,
        val appsFlyer: MockedStatic<AppsFlyerLib>,
    )

    /** UMP trả [ci]; Firebase Analytics, FCM, AppsFlyer là mock. Dọn tầng và `ConsentManager` sau [block]. */
    private fun withSdks(ci: ConsentInformation, activity: Activity, block: (Sdks) -> Unit) {
        val fa = mock(FirebaseAnalytics::class.java)
        val fcm = mock(FirebaseMessaging::class.java)
        @Suppress("UNCHECKED_CAST")
        val tokenTask = mock(Task::class.java) as Task<String>
        `when`(fcm.token).thenReturn(tokenTask)
        val af = mock(AppsFlyerLib::class.java)
        mockStatic(UserMessagingPlatform::class.java).use { ump ->
            ump.`when`<ConsentInformation> { UserMessagingPlatform.getConsentInformation(any(Context::class.java)) }
                .thenReturn(ci)
            mockStatic(FirebaseAnalytics::class.java).use { firebase ->
                firebase.`when`<FirebaseAnalytics> { FirebaseAnalytics.getInstance(any()) }.thenReturn(fa)
                mockStatic(FirebaseMessaging::class.java).use { messaging ->
                    messaging.`when`<FirebaseMessaging> { FirebaseMessaging.getInstance() }.thenReturn(fcm)
                    mockStatic(AppsFlyerLib::class.java).use { appsFlyer ->
                        appsFlyer.`when`<AppsFlyerLib> { AppsFlyerLib.getInstance() }.thenReturn(af)
                        try {
                            block(Sdks(fa, fcm, af, firebase, messaging, appsFlyer))
                        } finally {
                            // Bỏ listener của test này trước, để reset (đặt UNKNOWN) không gọi lại chúng.
                            ConsentState.resetForTest()
                            ConsentManager.reset(activity)   // bỏ `info` giả khỏi object dùng chung
                        }
                    }
                }
            }
        }
    }

    /** `requestConsentInfoUpdate` trả thành công ngay; [onUpdate] chạy trước khi báo thành công. */
    private fun umpAnsweringUpdate(ci: ConsentInformation, onUpdate: () -> Unit = {}) {
        doAnswer { inv ->
            onUpdate()
            inv.getArgument<ConsentInformation.OnConsentInfoUpdateSuccessListener>(2).onConsentInfoUpdateSuccess()
            null
        }.`when`(ci).requestConsentInfoUpdate(
            any(Activity::class.java),
            any(ConsentRequestParameters::class.java),
            any(ConsentInformation.OnConsentInfoUpdateSuccessListener::class.java),
            any(ConsentInformation.OnConsentInfoUpdateFailureListener::class.java)
        )
    }

    private fun activityOf(context: Context): Activity {
        val resources = context.resources
        val activity = mock(Activity::class.java)
        `when`(activity.applicationContext).thenReturn(context)
        `when`(activity.resources).thenReturn(resources)
        return activity
    }

    @Test
    fun `truoc cau tra loi UMP khong cham Firebase, FCM, AppsFlyer, sau Do not consent thi chay voi consent tu choi`() {
        // Cài mới ở EEA: form chưa trả lời (REQUIRED). Người dùng bấm "Do not consent": UMP OBTAINED,
        // canRequestAds() = true, TCF gdprApplies = 1 và PurposeConsents toàn 0.
        var answered = false
        val ci = mock(ConsentInformation::class.java)
        `when`(ci.canRequestAds()).thenAnswer { answered }
        `when`(ci.consentStatus).thenAnswer {
            if (answered) ConsentInformation.ConsentStatus.OBTAINED else ConsentInformation.ConsentStatus.REQUIRED
        }
        umpAnsweringUpdate(ci) { answered = true }
        val context = contextWithTcf(gdpr = 1, purposes = "0000000000", afAppId = "devKey")
        val activity = activityOf(context)

        withSdks(ci, activity) { sdks ->
            val (fa, fcm, af) = Triple(sdks.fa, sdks.fcm, sdks.af)
            // AppPluginManager.initPlugin lúc mở app, trước câu trả lời.
            AdSdkStartup.requestAnalytics(context)

            sdks.firebase.verify({ FirebaseAnalytics.getInstance(any()) }, never())
            sdks.messaging.verify({ FirebaseMessaging.getInstance() }, never())
            sdks.appsFlyer.verify({ AppsFlyerLib.getInstance() }, never())
            verifyNoInteractions(fa, fcm, af)

            // UMP trả lời: người dùng đã bấm "Do not consent". Tầng đổi (listener tầng) rồi cổng mở
            // (startAnalytics), nên Consent Mode và FCM có thể được đặt hai lần, cùng giá trị.
            ConsentManager.updateConsentInfo(activity) {}

            verify(fa, atLeastOnce()).setConsent(DENIED_ALL)
            verify(fa, atLeastOnce()).setAnalyticsCollectionEnabled(false)
            verify(fa, never()).setAnalyticsCollectionEnabled(true)
            // Từ chối trong GDPR: FCM không auto-init, không token (fix F1).
            verify(fcm, atLeastOnce()).isAutoInitEnabled = false
            verify(fcm, never()).isAutoInitEnabled = true
            verify(fcm, never()).token
            // #12 (DOC/66): DENIED thì AppsFlyer không start, không nhận consent nào.
            verify(af, never()).setConsentData(any())
            verify(af, never()).start(any(Context::class.java), any(), any())

            // Đổi ý qua privacy options: đồng ý hết, rồi thu hẹp về PARTIAL. Fix wave 2, Minor 3:
            // Consent Mode đặt lại theo đúng tầng mới mỗi lần.
            ConsentState.setTier(ConsentTier.GRANTED)
            verify(fa).setConsent(GRANTED_ALL)
            // AppsFlyer start lúc tầng cho phép (listener tầng → applyTier). Fix wave 2, Important 2: trên Activity của
            // luồng consent, không phải app context, để launch đi ngay.
            verify(af).setConsentData(AppsFlyerConsent(true, true, true, true))
            verify(af).start(same(activity), eq("devKey"), any(AppsFlyerRequestListener::class.java))
            ConsentState.setTier(ConsentTier.PARTIAL)
            verify(fa).setConsent(PARTIAL_ONLY)
            verify(af).setConsentData(AppsFlyerConsent(true, true, false, true))
            verify(af, times(1)).start(any(Context::class.java), any(), any())
            verify(af, never()).stop(anyBoolean(), any(Context::class.java))

            // Mục 6 (phương án a): SDK bật lại đúng một lần vì chính nó đã tắt lúc DENIED; PARTIAL không gọi nữa.
            verify(fa, times(1)).setAnalyticsCollectionEnabled(true)
            verify(fcm, times(2)).isAutoInitEnabled = true   // GRANTED, rồi PARTIAL
            verify(fcm, times(1)).token   // đúng một lần cho cả process
        }
    }

    /**
     * Fix wave 2, Important 1. Phiên trước GRANTED (Firebase đã lưu granted + collection on, FCM
     * auto-init on). Phiên này UMP báo REQUIRED (hết hạn, chính sách đổi): `canRequestAds()` = false
     * nên cổng analytics không mở, nhưng tầng hạ về DENIED và phải tới Firebase, FCM ngay.
     */
    @Test
    fun `cong chua mo ma tang thu hep thi Firebase va FCM theo ngay, khong token, khong AppsFlyer`() {
        ConsentState.setTier(ConsentTier.GRANTED)   // AppPluginBase khôi phục tầng phiên trước
        val ci = mock(ConsentInformation::class.java)
        `when`(ci.canRequestAds()).thenReturn(false)
        `when`(ci.consentStatus).thenReturn(ConsentInformation.ConsentStatus.REQUIRED)
        umpAnsweringUpdate(ci)
        val context = contextWithTcf(gdpr = 1, purposes = "1111111111")
        val activity = activityOf(context)

        withSdks(ci, activity) { sdks ->
            AdSdkStartup.requestAnalytics(context)
            verifyNoInteractions(sdks.fa, sdks.fcm, sdks.af)   // chưa đổi gì: chưa chạm SDK nào

            ConsentManager.updateConsentInfo(activity) {}

            assertEquals(ConsentTier.DENIED, ConsentState.tier())
            verify(sdks.fa).setConsent(DENIED_ALL)
            verify(sdks.fa).setAnalyticsCollectionEnabled(false)
            verify(sdks.fcm).isAutoInitEnabled = false
            verify(sdks.fcm, never()).isAutoInitEnabled = true
            verify(sdks.fcm, never()).token
            // Cổng vẫn đóng: AppsFlyer chưa start.
            sdks.appsFlyer.verify({ AppsFlyerLib.getInstance() }, never())
        }
    }

    /** Fix wave 2, Minor 8: sau `resetForTest` cổng mở lại được và listener tầng cũ đã gỡ. */
    @Test
    fun `resetForTest mo lai cong va go listener tang cu`() {
        val ci = mock(ConsentInformation::class.java)
        `when`(ci.canRequestAds()).thenReturn(true)   // câu trả lời đã có: cổng mở ngay khi request
        val context = contextWithTcf(gdpr = 1, purposes = "1111111111")   // không af_app_id
        val activity = activityOf(context)

        withSdks(ci, activity) { sdks ->
            AdSdkStartup.requestAnalytics(context)
            verify(sdks.fa, times(1)).setConsent(DENIED_ALL)   // tầng UNKNOWN trong GDPR

            AdSdkStartup.resetForTest()
            ConsentState.setTier(ConsentTier.GRANTED)
            verify(sdks.fa, never()).setConsent(GRANTED_ALL)   // listener của lần trước đã gỡ

            AdSdkStartup.requestAnalytics(context)
            verify(sdks.fa, times(1)).setConsent(GRANTED_ALL)   // cổng mới mở lại, chạy lại
            verify(sdks.fcm, times(1)).token   // Follower mới: token lấy lại một lần
        }
    }

    /**
     * Task 16, addendum A9: `resetForTest` đưa AppsFlyer về chưa start (start lại được), gỡ listener tầng (của
     * `AdSdkStartup`, đường duy nhất tới AppsFlyer từ review C I1), và quên Activity của luồng consent.
     */
    @Test
    fun `resetForTest dua AppsFlyer ve chua start, go listener tang, quen Activity consent`() {
        val ci = mock(ConsentInformation::class.java)
        `when`(ci.canRequestAds()).thenReturn(true)
        `when`(ci.consentStatus).thenReturn(ConsentInformation.ConsentStatus.OBTAINED)
        umpAnsweringUpdate(ci)
        val context = contextWithTcf(gdpr = 1, purposes = "1111111111", afAppId = "devKey")
        val activity = activityOf(context)

        withSdks(ci, activity) { sdks ->
            ConsentManager.updateConsentInfo(activity) {}   // luồng consent chạy trên Activity này
            AdSdkStartup.requestAnalytics(context)
            verify(sdks.af, times(1)).start(same(activity), eq("devKey"), any(AppsFlyerRequestListener::class.java))
            assertSame(activity, ConsentManager.liveConsentActivity())
            val consentUpdates = mockingDetails(sdks.af).invocations.count { it.method.name == "setConsentData" }

            AdSdkStartup.resetForTest()

            assertNull("Activity của luồng consent bị quên", ConsentManager.liveConsentActivity())
            ConsentState.setTier(ConsentTier.PARTIAL)
            assertEquals(
                "listener tầng đã gỡ: đổi tầng không tới AppsFlyer",
                consentUpdates,
                mockingDetails(sdks.af).invocations.count { it.method.name == "setConsentData" }
            )
            AdSdkStartup.requestAnalytics(context)
            verify(sdks.af, times(2)).start(any(Context::class.java), eq("devKey"), any(AppsFlyerRequestListener::class.java))
        }
    }

    /**
     * #12 (DOC/66): rút consent sau khi AppsFlyer đã start → `stop(true)` đúng một lần (listener tầng → `applyTier`
     * → `AppsflyerHelper.followTier`, sau FCM); cấp lại → `stop(false)` rồi start lại.
     */
    @Test
    fun `rut consent sau khi AppsFlyer da start thi stop(true), cap lai thi stop(false) roi start`() {
        val ci = mock(ConsentInformation::class.java)
        `when`(ci.canRequestAds()).thenReturn(true)
        `when`(ci.consentStatus).thenReturn(ConsentInformation.ConsentStatus.OBTAINED)
        umpAnsweringUpdate(ci)
        val context = contextWithTcf(gdpr = 1, purposes = "1111111111", afAppId = "devKey")
        val activity = activityOf(context)

        withSdks(ci, activity) { sdks ->
            ConsentManager.updateConsentInfo(activity) {}   // GRANTED từ TCF
            AdSdkStartup.requestAnalytics(context)
            verify(sdks.af, times(1)).start(same(activity), eq("devKey"), any(AppsFlyerRequestListener::class.java))

            ConsentState.setTier(ConsentTier.DENIED)   // rút qua privacy options
            ConsentState.setTier(ConsentTier.UNKNOWN)
            ConsentState.setTier(ConsentTier.DENIED)
            verify(sdks.af, times(1)).stop(eq(true), any(Context::class.java))

            ConsentState.setTier(ConsentTier.GRANTED)   // cấp lại

            val order = inOrder(sdks.af)
            order.verify(sdks.af).stop(eq(true), any(Context::class.java))
            order.verify(sdks.af).stop(eq(false), any(Context::class.java))
            order.verify(sdks.af).start(same(activity), eq("devKey"), any(AppsFlyerRequestListener::class.java))
            verify(sdks.af, times(2)).start(any(Context::class.java), eq("devKey"), any(AppsFlyerRequestListener::class.java))
            verify(sdks.af, times(1)).stop(eq(false), any(Context::class.java))
        }
    }

    /**
     * Task 16, addendum A9: listener tầng theo đăng ký thật trong `ConsentState`, không theo cờ riêng.
     * `ConsentState.resetForTest` gỡ nó mà không qua `AdSdkStartup`: request kế tiếp phải đăng ký lại.
     */
    @Test
    fun `ConsentState resetForTest go listener tang thi requestAnalytics ke tiep dang ky lai`() {
        val ci = mock(ConsentInformation::class.java)
        `when`(ci.canRequestAds()).thenReturn(true)
        val context = contextWithTcf(gdpr = 1, purposes = "1111111111")   // không af_app_id
        val activity = activityOf(context)

        withSdks(ci, activity) { sdks ->
            AdSdkStartup.requestAnalytics(context)
            ConsentState.resetForTest()   // gỡ mọi listener tầng, AdSdkStartup không biết

            AdSdkStartup.requestAnalytics(context)
            ConsentState.setTier(ConsentTier.GRANTED)

            verify(sdks.fa, times(1)).setConsent(GRANTED_ALL)
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

        val DENIED_ALL = map(false, false, false, false)
        val GRANTED_ALL = map(true, true, true, true)
        val PARTIAL_ONLY = map(true, true, false, false)
    }
}
