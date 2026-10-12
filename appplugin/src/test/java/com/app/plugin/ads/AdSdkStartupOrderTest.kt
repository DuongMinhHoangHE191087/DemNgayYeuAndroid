package com.app.plugin.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.app.plugin.SdkKillSwitch
import com.app.plugin.app.MetaSdkStarter
import com.app.plugin.consent.ConsentManager
import com.app.plugin.consent.ConsentState
import com.app.plugin.consent.ConsentTier
import com.app.plugin.consent.FcmConsent
import com.app.plugin.consent.contextWithTcf
import com.app.plugin.until.LogUtil
import com.appsflyer.AppsFlyerLib
import com.google.android.gms.tasks.Task
import com.google.android.ump.ConsentInformation
import com.google.android.ump.UserMessagingPlatform
import com.google.firebase.Firebase
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.analytics
import com.google.firebase.messaging.FirebaseMessaging
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.contains
import org.mockito.ArgumentMatchers.eq
import org.mockito.MockedStatic
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.`when`
import java.util.concurrent.Executor

/**
 * 2.0.0 C1c (M4, golden): seam [MaxBridge] không đổi thứ tự bước khởi tạo mạng của [AdSdkStartup]: AdMob → Meta AN
 * (tầng cho phép) → MAX (có SDK AppLovin), và nhãn `networks=` của `sdk_health` theo đúng thứ tự đó. Mạng thật không
 * khởi tạo được trong JVM: [AdSdkStartup.networkStarters] được thay bằng bản ghi lại lời gọi (như
 * `AdSdkStartupMetaConsentTest`).
 */
class AdSdkStartupOrderTest {
    private val fcmExecutor = FcmConsent.executor
    private val started = mutableListOf<String>()

    @Before fun setUp() {
        ConsentState.resetForTest()
        AdSdkStartup.resetForTest()
        SdkKillSwitch.current = SdkKillSwitch.NONE
        FcmConsent.executor = Executor { it.run() }
        AdSdkStartup.networkStarters = AdSdkStartup.NetworkStarters(
            admob = { started += "admob" },
            meta = { started += "meta" },
            max = { started += "max" },
        )
    }

    @After fun tearDown() {
        FcmConsent.executor = fcmExecutor
        MetaSdkStarter.resetForTest()
        AdSdkStartup.resetForTest()
        ConsentState.resetForTest()
        SdkKillSwitch.current = SdkKillSwitch.NONE
    }

    /** UMP đã có câu trả lời (cổng mở ngay khi request); Firebase, FCM, AppsFlyer là mock; `android.util.Log` ghi lại. */
    private fun withSinks(context: Context, block: (MockedStatic<Log>) -> Unit) {
        val ci = mock(ConsentInformation::class.java)
        `when`(ci.canRequestAds()).thenReturn(true)
        val fa = mock(FirebaseAnalytics::class.java)
        val fcm = mock(FirebaseMessaging::class.java)
        @Suppress("UNCHECKED_CAST")
        val task = mock(Task::class.java) as Task<String>
        `when`(fcm.token).thenReturn(task)
        mockStatic(UserMessagingPlatform::class.java).use { ump ->
            ump.`when`<ConsentInformation> { UserMessagingPlatform.getConsentInformation(any(Context::class.java)) }
                .thenReturn(ci)
            mockStatic(Class.forName("com.google.firebase.analytics.AnalyticsKt")).use { analyticsKt ->
                analyticsKt.`when`<FirebaseAnalytics> { Firebase.analytics }.thenReturn(fa)
                mockStatic(FirebaseAnalytics::class.java).use { firebase ->
                    firebase.`when`<FirebaseAnalytics> { FirebaseAnalytics.getInstance(any()) }.thenReturn(fa)
                    mockStatic(FirebaseMessaging::class.java).use { messaging ->
                        messaging.`when`<FirebaseMessaging> { FirebaseMessaging.getInstance() }.thenReturn(fcm)
                        mockStatic(AppsFlyerLib::class.java).use { appsFlyer ->
                            appsFlyer.`when`<AppsFlyerLib> { AppsFlyerLib.getInstance() }
                                .thenReturn(mock(AppsFlyerLib::class.java))
                            mockStatic(Log::class.java).use { log ->
                                try {
                                    block(log)
                                } finally {
                                    ConsentState.resetForTest()
                                    ConsentManager.reset(activityOf(context))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun activityOf(context: Context): Activity {
        val resources = context.resources   // đọc trước: gọi mock bên trong `when(...)` làm hỏng stubbing
        val activity = mock(Activity::class.java)
        `when`(activity.applicationContext).thenReturn(context)
        `when`(activity.resources).thenReturn(resources)
        return activity
    }

    @Test
    fun `M4 thu tu buoc khoi tao mang AdMob - Meta - MAX va nhan sdk_health giu nguyen`() {
        assertTrue("classpath test phải có SDK AppLovin (golden gồm MAX)", AdsHelper.isMaxSdkAvailable)
        ConsentState.setTier(ConsentTier.GRANTED)
        val context = contextWithTcf(gdpr = 1, purposes = "1111111111")
        withSinks(context) { log ->
            AdSdkStartup.requestAdNetworks(context)
            AdSdkStartup.requestAdNetworks(context)

            assertEquals("thứ tự khởi tạo mạng (một lần mỗi process)", listOf("admob", "meta", "max"), started)
            log.verify { Log.d(eq(LogUtil.TAG), contains("networks=admob,meta,max,")) }
        }
    }
}
