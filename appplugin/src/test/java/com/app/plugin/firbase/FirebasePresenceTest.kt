package com.app.plugin.firbase

import android.util.Log
import com.app.plugin.ads.AdRevenueRouting
import com.app.plugin.appsflyer.AppsflyerHelper
import com.app.plugin.brain.HeldExecutor
import com.app.plugin.consent.ConsentState
import com.app.plugin.consent.ConsentTier
import com.app.plugin.consent.FcmConsent
import com.app.plugin.consent.contextWithTcf
import com.app.plugin.until.LogUtil
import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.gms.tasks.Task
import com.google.firebase.Firebase
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.analytics
import com.google.firebase.messaging.FirebaseMessaging
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.mockingDetails
import org.mockito.Mockito.`when`
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

/**
 * 2.3.0 ROB3 (ROB-2 nhẹ, D21-IM): process không có FirebaseApp (bản debug không google-services của Invoice Maker) thì
 * SDK bỏ mọi nhánh Analytics/FCM với đúng một dòng W, thay vì một dòng E mỗi event (`im/e-im-report.md` #9, #15).
 * Kịch bản: 3 `FirHelper.logEvent` + 1 paid event AdMob (`ad_impression`) + FCM qua tầng consent giả (giữ auto-init
 * lúc mở app, GRANTED → bật + lấy token, DENIED → tắt + xoá token: `FcmConsent.kt` hold/update/fetch/delete).
 */
class FirebasePresenceTest {
    private val realExecutor = FcmConsent.executor
    private val realDelayed = FcmConsent.delayed
    private val fcmThread = HeldExecutor()
    private val missing = IllegalStateException("Default FirebaseApp is not initialized in this process x. Make sure to call FirebaseApp.initializeApp(Context) first.")

    @Before fun setUp() {
        FirebasePresence.resetForTest()
        ConsentState.resetForTest()
        FcmConsent.resetForTest()
        FcmConsent.executor = fcmThread
        FcmConsent.delayed = { _, _ -> }
    }

    @After fun tearDown() {
        FcmConsent.executor = realExecutor
        FcmConsent.delayed = realDelayed
        FcmConsent.resetForTest()
        ConsentState.resetForTest()
        FirebasePresence.resetForTest()
        AdRevenueRouting.admobLinkedToFirebase = false
        AppsflyerHelper.instance.resetForTest()
    }

    /** Lời gọi Firebase và log của một lượt kịch bản (seam đếm: `getInstance`/`Firebase.analytics` giả). */
    private class Run(val firebaseCalls: Int, val events: List<String>, val fcm: List<String>, val errors: Int, val warns: Int)

    private fun scenario(hasFirebase: Boolean): Run {
        FirebasePresence.probe = { hasFirebase }
        val analytics = mock(FirebaseAnalytics::class.java)
        val fcm = mock(FirebaseMessaging::class.java)
        @Suppress("UNCHECKED_CAST") val token = mock(Task::class.java) as Task<String>
        `when`(token.isSuccessful).thenReturn(true)
        `when`(token.result).thenReturn("tok")
        doAnswer { inv -> inv.getArgument<OnCompleteListener<String>>(0).onComplete(token); token }
            .`when`(token).addOnCompleteListener(any<OnCompleteListener<String>>())
        `when`(fcm.token).thenReturn(token)
        @Suppress("UNCHECKED_CAST") val delete = mock(Task::class.java) as Task<Void>
        `when`(delete.addOnCompleteListener(any<OnCompleteListener<Void>>())).thenReturn(delete)
        `when`(fcm.deleteToken()).thenReturn(delete)
        val calls = AtomicInteger()
        mockStatic(Class.forName("com.google.firebase.analytics.AnalyticsKt")).use { kt ->
            kt.`when`<FirebaseAnalytics> { Firebase.analytics }.thenAnswer { calls.incrementAndGet(); if (hasFirebase) analytics else throw missing }
            mockStatic(FirebaseMessaging::class.java).use { messaging ->
                messaging.`when`<FirebaseMessaging> { FirebaseMessaging.getInstance() }
                    .thenAnswer { calls.incrementAndGet(); if (hasFirebase) fcm else throw missing }
                mockStatic(Log::class.java).use { log ->
                    val errors = AtomicInteger()
                    val warns = AtomicInteger()
                    log.`when`<Int> { Log.e(anyString(), anyString()) }.thenAnswer { errors.incrementAndGet(); 0 }
                    log.`when`<Int> { Log.w(LogUtil.TAG, FirebasePresence.MISSING) }.thenAnswer { warns.incrementAndGet(); 0 }
                    val helper = FirHelper.instance
                    helper.logEvent("rob3_a")
                    helper.logEvent("rob3_b")
                    helper.logEvent("rob3_c", null)
                    PaidEvent.admob("full_home", "full_home", "interstitial", "unit", "com.google.ads.mediation.admob.AdMobAdapter", 1_000L, "USD", 1)
                    val ctx = contextWithTcf(gdpr = 1, purposes = "")
                    FcmConsent.holdAutoInitUntilConsent(ctx)
                    fcmThread.runAll()
                    val follower = FcmConsent.Follower { done -> helper.fetchFcmTokenAfterConsent(done) }
                    ConsentState.setTier(ConsentTier.GRANTED)
                    follower.follow(ctx)
                    fcmThread.runAll()
                    ConsentState.setTier(ConsentTier.DENIED)
                    follower.follow(ctx)
                    fcmThread.runAll()
                    val events = mockingDetails(analytics).invocations.filter { it.method.name == "logEvent" }.map { it.arguments[0] as String }
                    val fcmCalls = mockingDetails(fcm).invocations.map { it.method.name + it.arguments.joinToString(",", "(", ")") }
                    return Run(calls.get(), events, fcmCalls, errors.get(), warns.get())
                }
            }
        }
    }

    @Test fun `FB1 khong co FirebaseApp - 0 dong E, 1 dong W, 0 loi goi Firebase`() {
        val run = scenario(hasFirebase = false)
        assertEquals("dòng E (cũ: mỗi event một dòng 'Default FirebaseApp is not initialized')", 0, run.errors)
        assertEquals("lời gọi Firebase.analytics/FirebaseMessaging.getInstance (seam đếm)", 0, run.firebaseCalls)
        assertEquals("dòng W '${FirebasePresence.MISSING}'", 1, run.warns)
    }

    @Test fun `FB2 co FirebaseApp - loi goi Firebase y nhu cu, 0 dong W`() {
        val run = scenario(hasFirebase = true)
        assertEquals(listOf("rob3_a", "rob3_b", "rob3_c"), run.events.filter { it.startsWith("rob3_") })
        assertEquals("ad_impression AdMob: ${run.events}", 1, run.events.count { it == FirebaseAnalytics.Event.AD_IMPRESSION })
        assertEquals(
            "FCM: giữ lúc mở app → bật + token (GRANTED) → tắt + xoá (DENIED)",
            listOf("setAutoInitEnabled(false)", "setAutoInitEnabled(true)", "getToken()", "setAutoInitEnabled(false)", "deleteToken()"),
            run.fcm,
        )
        assertEquals("dòng W", 0, run.warns)
    }

    @Test fun `FB3 ba luong hoi dong thoi - W dung mot lan, vang thi hoi lai moi lan`() {
        val probes = AtomicInteger()
        val warns = Collections.synchronizedList(mutableListOf<String>())
        FirebasePresence.probe = { probes.incrementAndGet(); Thread.sleep(20); false }
        FirebasePresence.warn = { warns += it }
        val start = CountDownLatch(1)
        val results = Collections.synchronizedList(mutableListOf<Boolean>())
        val threads = List(3) { Thread { start.await(); results += FirebasePresence.available() } }
        threads.forEach(Thread::start)
        start.countDown()
        threads.forEach { it.join(5_000) }
        assertEquals("kết quả mỗi luồng", listOf(false, false, false), results.toList())
        assertEquals("lần hỏi thứ 4 vẫn vắng", false, FirebasePresence.available())
        assertEquals("dòng W (một lần mỗi process)", listOf(FirebasePresence.MISSING), warns.toList())
        assertEquals("'vắng' không ghi nhớ: probe chạy mỗi lần hỏi", 4, probes.get())
    }

    /** Review R-ROB3 I2: app khởi tạo Firebase sau `super.onCreate()` lấy lại Analytics/FCM của SDK từ lần gọi kế. */
    @Test fun `FB4 vang roi co sau khi app init Firebase - co tu lan goi ke, W mot lan`() {
        var initialised = false
        val probes = AtomicInteger()
        val warns = mutableListOf<String>()
        FirebasePresence.probe = { probes.incrementAndGet(); initialised }
        FirebasePresence.warn = { warns += it }
        assertEquals("trước khi app init Firebase", false, FirebasePresence.available())
        initialised = true
        assertEquals("sau khi app init Firebase", true, FirebasePresence.available())
        initialised = false
        assertEquals("'có' được ghi nhớ", true, FirebasePresence.available())
        assertEquals("probe: vắng, có, rồi không hỏi lại", 2, probes.get())
        assertEquals("dòng W", listOf(FirebasePresence.MISSING), warns)
    }

    @Test fun `FB5 probe nem - coi nhu co, W toi da mot lan moi process`() {
        val warns = mutableListOf<String>()
        FirebasePresence.probe = { throw IllegalStateException("boom") }
        FirebasePresence.warn = { warns += it }
        assertEquals("probe ném: đường cũ (coi như có)", listOf(true, true, true), List(3) { FirebasePresence.available() })
        assertEquals("dòng W: ${warns}", 1, warns.size)
    }
}
