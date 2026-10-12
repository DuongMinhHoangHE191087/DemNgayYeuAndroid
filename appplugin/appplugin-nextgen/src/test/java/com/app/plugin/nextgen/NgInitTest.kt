package com.app.plugin.nextgen

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import com.app.plugin.SdkKillSwitch
import com.app.plugin.ads.AdSdkStartup
import com.app.plugin.ads.MainFront
import com.app.plugin.ads.adsmob.AdsMobMy
import com.app.plugin.ads.consent.AdRequestFactory
import com.app.plugin.consent.ConsentState
import com.app.plugin.consent.ConsentTier
import com.app.plugin.consent.FcmConsent
import com.app.plugin.consent.contextWithTcf
import com.app.plugin.until.LogUtil
import com.google.android.libraries.ads.mobile.sdk.common.RequestConfiguration
import com.google.android.libraries.ads.mobile.sdk.initialization.InitializationConfig
import com.google.android.libraries.ads.mobile.sdk.initialization.InitializationStatus
import com.google.android.libraries.ads.mobile.sdk.initialization.OnAdapterInitializationCompleteListener
import com.google.android.ump.ConsentInformation
import com.google.android.ump.UserMessagingPlatform
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockConstruction
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

/**
 * 2.4.0 NG1 (Hợp đồng B "NG1 init", D4/D9-240): init GMA Next-Gen trên luồng `appplugin-gma-init`, hàng đợi giữ khi quá
 * 8 s (trần 16), chạy đúng một lần khi init xong, bỏ khi init lỗi; năm dòng `gma:` nguyên văn, `Log.i` (luật 26/28).
 * Seam: đồng hồ, hẹn giờ, executor, `MobileAds.initialize` giả; main = FIFO thủ công qua [MainFront] (2.4.4 INITCB).
 */
class NgInitTest {
    private val logs = ArrayList<String>()
    private val timeouts = ArrayList<Pair<Runnable, Long>>()
    private val inits = ArrayList<Triple<Context, InitializationConfig, OnAdapterInitializationCompleteListener>>()
    private val posted = ArrayDeque<Runnable>()
    private var now = 1_000L

    @After fun tearDown() {
        MainFront.resetForTest()
        ConsentState.resetForTest()
        AdSdkStartup.resetForTest()
        SdkKillSwitch.current = SdkKillSwitch.NONE
    }

    /** AdsMobMy mới, mọi seam giả; executor chạy ngay trên luồng test; main = hàng [posted]. */
    private fun my(appId: String? = APP_ID, initFails: Throwable? = null) = AdsMobMy().apply {
        initExecutor = Executor { it.run() }
        scheduleTimeout = { r, ms -> timeouts += r to ms }
        initializer = { c, cfg, l -> initFails?.let { throw it }; inits += Triple(c, cfg, l) }
        appIdReader = { appId }
        testDeviceIds = { listOf("DEV1", "DEV2") }
        clock = { now }
        logSink = { logs += it }
        MainFront.isMain = { false }
        MainFront.poster = { r -> posted.addLast(r) }
    }

    private fun drainMain() { while (posted.isNotEmpty()) posted.removeFirst().run() }

    private fun complete(ms: Long) {
        now += ms
        inits.single().third.onAdapterInitializationComplete(mock(InitializationStatus::class.java))
    }

    @Test
    fun `NI1 MobileAds initialize chay tren luong appplugin-gma-init, khong tren luong goi`() {
        val seen = ArrayList<String>()
        val done = CountDownLatch(1)
        val m = AdsMobMy().apply {
            scheduleTimeout = { _, _ -> }
            appIdReader = { APP_ID }
            testDeviceIds = { emptyList() }
            logSink = { }
            initializer = { _, _, _ -> seen += Thread.currentThread().name; done.countDown() }
        }
        m.startNetwork(mock(Context::class.java))
        assertTrue("initialize phải được gọi", done.await(5, TimeUnit.SECONDS))
        assertEquals(listOf("appplugin-gma-init"), seen)
        assertNotEquals(Thread.currentThread().name, seen.single())
    }

    @Test
    fun `NI2 qua 8 s chua init thi giu hang doi, khong chay, log init timeout`() {
        val m = my()
        val ran = ArrayList<Int>()
        repeat(3) { i -> m.runWhenSdkReady { ran += i } }
        m.startNetwork(mock(Context::class.java))
        assertEquals(8_000L, timeouts.single().second)
        timeouts.single().first.run()
        drainMain()
        assertEquals("0 lời gọi chạy lúc quá hạn", emptyList<Int>(), ran)
        assertEquals(false, m.isSdkInitialized)
        assertEquals(listOf("gma: init timeout, giữ hàng đợi"), logs)
    }

    @Test
    fun `NI2b 17 loi goi khi chua init giu 16 dau, tu choi loi goi moi, log queue full drop 1`() {
        val m = my()
        val ran = ArrayList<Int>()
        repeat(17) { i -> m.runWhenSdkReady { ran += i } }
        assertEquals(listOf("gma: init queue full, drop 1"), logs)
        m.startNetwork(mock(Context::class.java)); complete(5); drainMain()
        assertEquals("giữ preload/splash đã xếp trước (R-NG1 I2)", (0 until 16).toList(), ran)
        assertTrue(logs.contains("gma: init done, run 16"))
    }

    @Test
    fun `NI6 init xong chay hang doi dung 1 lan theo thu tu tren main, sau do chay ngay`() {
        val m = my()
        val ran = ArrayList<String>()
        repeat(16) { i -> m.runWhenSdkReady { ran += "q$i" } }
        m.startNetwork(mock(Context::class.java))
        timeouts.single().first.run()            // quá hạn trước khi xong: vẫn giữ
        complete(42)
        assertEquals("chạy trên main (poster), không trên luồng SDK", emptyList<String>(), ran)
        inits.single().third.onAdapterInitializationComplete(mock(InitializationStatus::class.java))   // callback lặp
        drainMain()
        assertEquals((0 until 16).map { "q$it" }, ran)
        m.runWhenSdkReady { ran += "after" }
        assertEquals("sau init chạy ngay", "after", ran.last())
        assertEquals(17, ran.size)
        assertEquals("ready đúng 1 dòng/process (R-NG1 m1)", listOf("gma: init timeout, giữ hàng đợi", "gma: ready 42ms", "gma: init done, run 16"), logs)
        assertTrue(m.isSdkInitialized)
    }

    @Test
    fun `NI3 init loi thi bo hang doi, log init failed ma, loi goi sau khong chay`() {
        val m = my(initFails = IllegalStateException("boom"))
        val ran = ArrayList<Int>()
        m.runWhenSdkReady { ran += 1 }
        m.startNetwork(mock(Context::class.java))
        m.runWhenSdkReady { ran += 2 }
        drainMain()
        assertEquals(emptyList<Int>(), ran)
        assertEquals(listOf("gma: init failed (IllegalStateException)"), logs)
        assertFalse("init lỗi: SDK không sẵn sàng (R-NG1 m2)", m.isSdkInitialized)
        val noId = my(appId = null)
        noId.startNetwork(mock(Context::class.java))
        assertEquals("thiếu APPLICATION_ID = lỗi init, 0 initialize", "gma: init failed (IllegalStateException)", logs.last())
        assertTrue(inits.isEmpty())
    }

    @Test
    fun `NI4 InitializationConfig mang appId meta-data, test device va rating MA`() {
        my().startNetwork(mock(Context::class.java))
        val cfg = inits.single().second
        assertEquals(APP_ID, cfg.applicationId)
        val rc = cfg.requestConfiguration
        assertTrue("test device của app (SDK tự thêm id emulator): ${rc.testDeviceIds}", rc.testDeviceIds.containsAll(listOf("DEV1", "DEV2")))
        assertEquals(RequestConfiguration.MaxAdContentRating.MAX_AD_CONTENT_RATING_MA, rc.maxAdContentRating)
    }

    @Test
    fun `NI5 consent chua tra loi thi 0 init, co consent thi init qua AdSdkStartup chung`() {
        val m = AdsMobMy.instance
        val saved = listOf(m.initExecutor, m.scheduleTimeout, m.initializer, m.appIdReader, m.testDeviceIds, m.logSink)
        val fcm = FcmConsent.executor
        try {
            m.initExecutor = Executor { it.run() }; m.scheduleTimeout = { _, _ -> }; m.appIdReader = { APP_ID }
            m.testDeviceIds = { emptyList() }; m.logSink = { }; m.initializer = { c, cfg, l -> inits += Triple(c, cfg, l) }
            FcmConsent.executor = Executor { }
            AdSdkStartup.networkStarters = AdSdkStartup.NetworkStarters(meta = { }, max = { })   // admob = mặc định → AdsMobMy.instance
            ConsentState.setTier(ConsentTier.GRANTED)
            val context = contextWithTcf(gdpr = 1, purposes = "1111111111")
            assertEquals("chưa có câu trả lời UMP: 0 init", 0, withUmp(context, canRequestAds = false))
            AdSdkStartup.resetForTest(); AdSdkStartup.networkStarters = AdSdkStartup.NetworkStarters(meta = { }, max = { })
            assertEquals("consent cho phép: init đúng 1 lần", 1, withUmp(context, canRequestAds = true))
        } finally {
            @Suppress("UNCHECKED_CAST") run {
                m.initExecutor = saved[0] as Executor?; m.scheduleTimeout = saved[1] as (Runnable, Long) -> Unit
                m.initializer = saved[2] as (Context, InitializationConfig, OnAdapterInitializationCompleteListener) -> Unit
                m.appIdReader = saved[3] as (Context) -> String?; m.testDeviceIds = saved[4] as (Context) -> List<String>
                m.logSink = saved[5] as (String) -> Unit
            }
            FcmConsent.executor = fcm
        }
    }

    private fun withUmp(context: Context, canRequestAds: Boolean): Int {
        val ci = mock(ConsentInformation::class.java)
        `when`(ci.canRequestAds()).thenReturn(canRequestAds)
        mockStatic(UserMessagingPlatform::class.java).use { ump ->
            ump.`when`<ConsentInformation> { UserMessagingPlatform.getConsentInformation(any(Context::class.java)) }.thenReturn(ci)
            mockStatic(Log::class.java).use { AdSdkStartup.requestAdNetworks(context) }
        }
        return inits.size
    }

    @Test
    fun `NL1 moi dong gma di Log i voi TAG, khong Log d`() {
        val lines = listOf("gma: init timeout, giữ hàng đợi", "gma: init queue full, drop 3", "gma: init done, run 16",
            "gma: ready 42ms", "gma: init failed (IllegalStateException)")
        mockStatic(Log::class.java).use { log ->
            val sink = AdsMobMy().logSink
            lines.forEach { sink(it) }
            lines.forEach { line -> log.verify({ Log.i(LogUtil.TAG, line) }, times(1)) }
            log.verify({ Log.d(anyString(), anyString()) }, never())
        }
    }

    @Test
    fun `NA1 khong ca nhan hoa thi request Next-Gen mang extras Google npa=1, ca nhan hoa thi khong`() {
        val denied = tcf(purposes = "0111111111")
        mockConstruction(Bundle::class.java).use { bundles ->
            val req = AdRequestFactory.request(denied, "ca-app-pub-x/1")
            val npa = bundles.constructed().filter { b -> runCatching { verify(b).putString("npa", "1") }.isSuccess }
            assertEquals("đúng một Bundle mang npa=1", 1, npa.size)
            assertTrue("Bundle npa là extras Google của request", req.googleExtrasBundle === npa.single())
        }
        val granted = tcf(purposes = "1111111111")
        mockConstruction(Bundle::class.java).use { bundles ->
            AdRequestFactory.native(granted, "ca-app-pub-x/2")
            bundles.constructed().forEach { b -> verify(b, never()).putString("npa", "1") }
        }
    }

    /** Prefs TCF như UMP ghi, đọc đúng như `AdRequestFactory.canRequestPersonalizedAds` (mặc định `null`). */
    private fun tcf(purposes: String): Context {
        val prefs = mock(SharedPreferences::class.java)
        `when`(prefs.contains("IABTCF_gdprApplies")).thenReturn(true)
        `when`(prefs.getInt("IABTCF_gdprApplies", 0)).thenReturn(1)
        `when`(prefs.getString("IABTCF_PurposeConsents", null)).thenReturn(purposes)
        return mock(Context::class.java).also {
            `when`(it.packageName).thenReturn("com.example.app")
            `when`(it.getSharedPreferences("com.example.app_preferences", Context.MODE_PRIVATE)).thenReturn(prefs)
        }
    }

    private companion object {
        const val APP_ID = "ca-app-pub-0000000000000000~0000000000"
    }
}
