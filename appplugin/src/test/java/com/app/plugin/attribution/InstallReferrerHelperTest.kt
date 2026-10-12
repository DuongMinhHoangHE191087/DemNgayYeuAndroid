package com.app.plugin.attribution

import android.content.Context
import android.os.Bundle
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import com.android.installreferrer.api.ReferrerDetails
import com.app.plugin.ads.AdSdkStartup
import com.app.plugin.consent.ConsentState
import com.app.plugin.consent.ConsentTier
import com.app.plugin.consent.FcmConsent
import com.app.plugin.consent.GPP_US_OPT_OUT
import com.app.plugin.consent.contextWithTcf
import com.app.plugin.logevent.inMemoryPrefs
import com.google.android.gms.tasks.Task
import com.google.android.ump.ConsentInformation
import com.google.android.ump.UserMessagingPlatform
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.messaging.FirebaseMessaging
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockConstruction
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.`when`
import java.util.concurrent.Executor

/**
 * 2.4.2 REFERRER (Hợp đồng D, quyết định (vi); gap hàng 39): SDK tự đọc Install Referrer từ `AdSdkStartup.applyTier`,
 * chỉ khi Consent Mode cho `analytics_storage`, một lần mỗi process, trên luồng nền; `gclid` + `raw_referrer` chỉ gửi
 * khi `ad_user_data` được cho. Điểm nối: [InstallReferrerHelper.executor] (giữ Runnable / chạy đồng bộ) và
 * [InstallReferrerHelper.startRead] (đếm lần bắt đầu đọc); đường IPC thật dùng `InstallReferrerClient` giả (mockStatic).
 */
class InstallReferrerHelperTest {
    private val savedExecutor = InstallReferrerHelper.executor
    private val savedStartRead = InstallReferrerHelper.startRead
    private val savedFcmExecutor = FcmConsent.executor
    private val held = mutableListOf<Runnable>()
    private var reads = 0

    @Before fun setUp() {
        ConsentState.resetForTest()
        AdSdkStartup.resetForTest()
        InstallReferrerHelper.resetForTest()
        FcmConsent.executor = Executor { it.run() }
        InstallReferrerHelper.executor = Executor { held += it }
        InstallReferrerHelper.startRead = { reads++ }
    }

    @After fun tearDown() {
        InstallReferrerHelper.executor = savedExecutor
        InstallReferrerHelper.startRead = savedStartRead
        FcmConsent.executor = savedFcmExecutor
        AdSdkStartup.resetForTest()
        ConsentState.resetForTest()
        InstallReferrerHelper.resetForTest()
    }

    private fun runHeld() {
        while (held.isNotEmpty()) held.removeAt(0).run()
    }

    /** UMP chưa trả lời (cổng `analytics` chờ), Firebase/FCM giả: chỉ listener tầng của `requestAnalytics` chạy `applyTier`. */
    private fun withStartup(context: Context, block: () -> Unit) {
        val ci = mock(ConsentInformation::class.java)
        `when`(ci.consentStatus).thenReturn(ConsentInformation.ConsentStatus.REQUIRED)
        val fcm = mock(FirebaseMessaging::class.java)
        @Suppress("UNCHECKED_CAST")
        `when`(fcm.token).thenReturn(mock(Task::class.java) as Task<String>)
        mockStatic(UserMessagingPlatform::class.java).use { ump ->
            ump.`when`<ConsentInformation> { UserMessagingPlatform.getConsentInformation(any(Context::class.java)) }.thenReturn(ci)
            mockStatic(FirebaseAnalytics::class.java).use { fa ->
                fa.`when`<FirebaseAnalytics> { FirebaseAnalytics.getInstance(any()) }.thenReturn(mock(FirebaseAnalytics::class.java))
                mockStatic(FirebaseMessaging::class.java).use { msg ->
                    msg.`when`<FirebaseMessaging> { FirebaseMessaging.getInstance() }.thenReturn(fcm)
                    AdSdkStartup.requestAnalytics(context)
                    block()
                }
            }
        }
    }

    @Test fun `RF1 tang GRANTED - applyTier bat dau doc dung 1 lan, goi lai trong process 0, da captured 0`() {
        val context = contextWithTcf(gdpr = 1, purposes = "1111111111")
        withStartup(context) {
            ConsentState.setTier(ConsentTier.GRANTED)
            runHeld()
            assertEquals("GRANTED → applyTier → đọc đúng 1 lần", 1, reads)

            ConsentState.setTier(ConsentTier.PARTIAL)   // applyTier lần nữa trong cùng process
            runHeld()
            assertEquals("gọi lại trong process: không đọc thêm", 1, reads)

            InstallReferrerHelper.resetForTest()   // process mới, lượt cài đã đọc xong
            `when`(context.getSharedPreferences("App_prefer_file", Context.MODE_PRIVATE).getInt(InstallReferrerHelper.KEY_CAPTURED, 0))
                .thenReturn(1)
            ConsentState.setTier(ConsentTier.GRANTED)
            runHeld()
            assertEquals("install_referrer_captured = 1: không đọc", 1, reads)
        }
    }

    @Test fun `RF4 viec doc chay tren executor nen, khong trong loi goi applyTier`() {
        withStartup(contextWithTcf(gdpr = 1, purposes = "1111111111")) {
            ConsentState.setTier(ConsentTier.GRANTED)
            assertEquals("trong lời gọi applyTier: chưa đọc", 0, reads)
            assertEquals("đúng một việc xếp lên executor", 1, held.size)
            runHeld()
            assertEquals(1, reads)
        }
    }

    @Test fun `RF3 DENIED va UNKNOWN voi gdprApplies true hoac null - khong bao gio doc`() {
        InstallReferrerHelper.executor = Executor { it.run() }
        for (gdpr in listOf(1, null)) {
            for (tier in listOf(ConsentTier.DENIED, ConsentTier.UNKNOWN)) {
                ConsentState.resetForTest()
                ConsentState.setTier(tier)
                InstallReferrerHelper.captureAfterConsent(contextWithTcf(gdpr = gdpr, purposes = "1111111111"))
                assertEquals("$tier, gdprApplies=$gdpr: 0 lần đọc", 0, reads)
            }
        }
        ConsentState.resetForTest()
        ConsentState.setTier(ConsentTier.DENIED)   // DENIED thắng gdprApplies = 0 (review m5)
        InstallReferrerHelper.captureAfterConsent(contextWithTcf(gdpr = 0, purposes = "1111111111"))
        assertEquals("DENIED, gdprApplies=0: 0 lần đọc", 0, reads)
    }

    /** `App_prefer_file` của lần [readEvent] gần nhất (trong bộ nhớ). */
    private val appPrefs = mutableMapOf<String, Any?>()

    /**
     * Đọc thật qua [InstallReferrerHelper.captureOnce] với Play giả trả [referrer]; trả các cặp `putString` của mọi Bundle.
     * [beforeFinish] chạy giữa lúc mở IPC và callback của Play (tầng đổi trong lúc chờ).
     */
    private fun readEvent(
        tier: ConsentTier, referrer: String, gdpr: Int? = 1, gpp: String? = null, beforeFinish: () -> Unit = {},
    ): Map<String, String?> {
        InstallReferrerHelper.executor = Executor { it.run() }
        InstallReferrerHelper.startRead = savedStartRead
        ConsentState.setTier(tier)
        val details = mock(ReferrerDetails::class.java).also { `when`(it.installReferrer).thenReturn(referrer) }
        val client = mock(InstallReferrerClient::class.java).also { `when`(it.installReferrer).thenReturn(details) }
        doAnswer { inv ->
            beforeFinish()
            inv.getArgument<InstallReferrerStateListener>(0).onInstallReferrerSetupFinished(InstallReferrerClient.InstallReferrerResponse.OK)
            null
        }.`when`(client).startConnection(any())
        val builder = mock(InstallReferrerClient.Builder::class.java).also { `when`(it.build()).thenReturn(client) }
        val puts = mutableMapOf<String, String?>()
        mockStatic(InstallReferrerClient::class.java).use { irc ->
            irc.`when`<InstallReferrerClient.Builder> { InstallReferrerClient.newBuilder(any()) }.thenReturn(builder)
            mockConstruction(Bundle::class.java) { b, _ ->
                `when`(b.putString(anyString(), any())).thenAnswer { puts[it.getArgument(0)] = it.getArgument(1); null }
            }.use {
                val context = contextWithTcf(gdpr = gdpr, purposes = "1111111111", gpp = { gpp })
                `when`(context.getSharedPreferences(eq("App_prefer_file"), anyInt())).thenReturn(inMemoryPrefs(appPrefs))
                InstallReferrerHelper.captureAfterConsent(context)
            }
        }
        return puts
    }

    @Test fun `RF2 PARTIAL - event khong co gclid va raw_referrer, GRANTED co`() {
        val referrer = "utm_source=google&utm_medium=cpc&utm_campaign=spring%20sale&gclid=Cj0abc"

        val partial = readEvent(ConsentTier.PARTIAL, referrer)
        assertEquals("PARTIAL vẫn gửi UTM", "spring sale", partial["utm_campaign"])
        assertFalse("PARTIAL (ad_user_data từ chối): không gclid — $partial", partial.containsKey("gclid"))
        assertFalse("PARTIAL: không raw_referrer — $partial", partial.containsKey("raw_referrer"))

        ConsentState.resetForTest()
        InstallReferrerHelper.resetForTest()
        appPrefs.clear()   // lượt cài mới: chưa captured
        val granted = readEvent(ConsentTier.GRANTED, referrer)
        assertEquals("GRANTED: gclid", "Cj0abc", granted["gclid"])
        assertEquals("GRANTED: raw_referrer", referrer, granted["raw_referrer"])
    }

    @Test fun `RF2 eventParams - ham thuan loc theo ad_user_data, parse khong dung android Uri`() {
        val ref = "utm_source=a%2Bb&utm_source=second&gclid=X&junk&=v&utm_term=t+1"

        val full = InstallReferrerHelper.eventParams(ref, adUserData = true)
        assertEquals("a+b", full["utm_source"])   // giá trị đầu, đã giải mã
        assertEquals("t 1", full["utm_term"])
        assertEquals("", full["utm_content"])
        assertEquals("X", full["gclid"])
        assertEquals(ref, full["raw_referrer"])

        val partial = InstallReferrerHelper.eventParams(ref, adUserData = false)
        assertTrue(partial.keys.containsAll(listOf("utm_source", "utm_medium", "utm_campaign", "utm_content", "utm_term")))
        assertFalse("ad_user_data từ chối: không gclid, raw_referrer — $partial", partial.containsKey("gclid") || partial.containsKey("raw_referrer"))
    }

    @Test fun `RF5 I4 - chuoi referrer tho khong luu xuong prefs, PARTIAL lan GRANTED`() {
        val referrer = "utm_source=google&gclid=Cj0abc"
        for (tier in listOf(ConsentTier.PARTIAL, ConsentTier.GRANTED)) {
            ConsentState.resetForTest()
            InstallReferrerHelper.resetForTest()
            appPrefs.clear()
            readEvent(tier, referrer)
            assertEquals("$tier: đã đọc xong", 1, appPrefs["install_referrer_captured"])
            assertFalse("$tier: install_referrer_url không ghi (không ai đọc) — $appPrefs", appPrefs.containsKey("install_referrer_url"))
        }
    }

    @Test fun `RF6 tang thu hep giua luc mo IPC va callback - khong gui, captured van 0`() {
        val puts = readEvent(ConsentTier.GRANTED, "utm_source=google&gclid=Cj0abc") { ConsentState.setTier(ConsentTier.DENIED) }

        assertEquals("không event — $puts", emptyMap<String, String?>(), puts)
        assertFalse("captured vẫn 0: lần mở sau đọc lại", appPrefs.containsKey("install_referrer_captured"))
    }

    @Test fun `RF7 GPP opt-out My ngoai GDPR - van gui UTM, khong gclid`() {
        val puts = readEvent(ConsentTier.GRANTED, "utm_source=google&gclid=Cj0abc", gdpr = 0, gpp = GPP_US_OPT_OUT)

        assertEquals("google", puts["utm_source"])
        assertFalse("US opt-out: ad_user_data denied → không gclid — $puts", puts.containsKey("gclid") || puts.containsKey("raw_referrer"))
    }
}
