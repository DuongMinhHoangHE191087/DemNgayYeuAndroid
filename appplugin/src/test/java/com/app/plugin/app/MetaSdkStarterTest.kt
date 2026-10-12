package com.app.plugin.app

import android.app.Application
import android.util.Log
import com.app.plugin.brain.HeldExecutor
import com.app.plugin.consent.ConsentState
import com.app.plugin.consent.ConsentTier
import com.app.plugin.consent.GPP_US_OK
import com.app.plugin.consent.GPP_US_OPT_OUT
import com.app.plugin.consent.GppConsent
import com.app.plugin.consent.applicationWithTcf
import com.app.plugin.until.LogUtil
import com.facebook.FacebookSdk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.contains
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mockStatic
import org.mockito.MockedStatic
import org.mockito.Mockito.never
import org.mockito.Mockito.times

/**
 * Meta SDK (app events, `facebook-core`) theo consent. Review C "Out of scope", làm theo nguyên tắc P1 (F6): người
 * `DENIED` trong vùng GDPR (`IABTCF_gdprApplies` khác `0` hoặc vắng) không khởi tạo `FacebookSdk` và không
 * `activateApp`; tầng cho phép sau đó trong process (`ConsentRules.tierAllows`, không phải `UNKNOWN`) thì khởi tạo
 * lúc đó, một lần. Unit test không có `facebook-core`: [MetaSdkStarter.facebookCoreAvailable] và
 * [MetaSdkStarter.initializer] được thay.
 */
class MetaSdkStarterTest {
    private val inits = mutableListOf<Application>()
    private val metaThread = HeldExecutor()

    @Before fun setUp() {
        ConsentState.resetForTest()
        MetaSdkStarter.resetForTest()
        MetaSdkStarter.facebookCoreAvailable = { true }
        MetaSdkStarter.initializer = { inits += it }
        MetaSdkStarter.executor = metaThread
    }

    @After fun tearDown() {
        MetaSdkStarter.resetForTest()
        ConsentState.resetForTest()
    }

    @Test
    fun `a missing or placeholder app id is not a configured app`() {
        assertFalse(MetaSdkStarter.isConfiguredAppId(""))
        assertFalse(MetaSdkStarter.isConfiguredAppId("  "))
        assertFalse(MetaSdkStarter.isConfiguredAppId("FB_APP_ID"))
        assertFalse(MetaSdkStarter.isConfiguredAppId("FB_APP_ID_PLACEHOLDER"))
    }

    @Test
    fun `a numeric app id is a configured app`() {
        assertTrue(MetaSdkStarter.isConfiguredAppId("1234567890123456"))
    }

    // ═══ F6: DENIED trong vùng GDPR thì không khởi tạo ═══

    @Test
    fun `DENIED trong GDPR thi khong khoi tao Meta SDK va logE`() = mockStatic(Log::class.java).use { log ->
        ConsentState.setTier(ConsentTier.DENIED)

        MetaSdkStarter.start(applicationWithTcf(gdpr = 1, purposes = "0000000000"))

        assertEquals(emptyList<Application>(), inits)
        log.verify({ Log.e(LogUtil.TAG, "meta sdk: not started, consent tier=DENIED in GDPR scope") }, times(1))
    }

    /** `IABTCF_gdprApplies` vắng (không biết) không bao giờ là lý do cấp: như trong GDPR. */
    @Test
    fun `DENIED khi khong biet GDPR thi cung khong khoi tao`() {
        ConsentState.setTier(ConsentTier.DENIED)

        MetaSdkStarter.start(applicationWithTcf(gdpr = null, purposes = ""))

        assertEquals(0, inits.size)
    }

    /**
     * FX: cổng dùng `ConsentRules.tierAllows` như AppsFlyer; `DENIED` thắng `IABTCF_gdprApplies = 0` (0 có thể còn lại
     * từ trước khi người dùng vào EEA). Trước FX: `DENIED` với 0 vẫn khởi tạo.
     */
    @Test
    fun `DENIED voi gdprApplies 0 thi khong khoi tao - DENIED thang 0`() = mockStatic(Log::class.java).use { log ->
        ConsentState.setTier(ConsentTier.DENIED)

        MetaSdkStarter.start(applicationWithTcf(gdpr = 0, purposes = ""))

        assertEquals(0, inits.size)
        log.verify({ Log.e(LogUtil.TAG, "meta sdk: not started, consent tier=DENIED outside GDPR scope") }, times(1))
    }

    // ═══ FX: UNKNOWN trong vùng GDPR bị từ chối ở cổng (Điều 13) ═══

    @Test
    fun `UNKNOWN trong GDPR thi khong khoi tao, logE, GRANTED sau do thi followTier khoi tao mot lan`() =
        mockStatic(Log::class.java).use { log ->
            ConsentState.setTier(ConsentTier.UNKNOWN)
            val app = applicationWithTcf(gdpr = 1, purposes = "")

            MetaSdkStarter.start(app)
            assertEquals(0, inits.size)
            log.verify({ Log.e(LogUtil.TAG, "meta sdk: not started, consent tier=UNKNOWN in GDPR scope") }, times(1))

            ConsentState.setTier(ConsentTier.GRANTED)
            MetaSdkStarter.followTier()
            assertEquals(listOf(app), inits)
        }

    @Test
    fun `UNKNOWN khi khong biet GDPR thi khong khoi tao`() {
        ConsentState.setTier(ConsentTier.UNKNOWN)

        MetaSdkStarter.start(applicationWithTcf(gdpr = null, purposes = ""))

        assertEquals(0, inits.size)
    }

    @Test
    fun `UNKNOWN ngoai GDPR thi khoi tao`() {
        ConsentState.setTier(ConsentTier.UNKNOWN)

        MetaSdkStarter.start(applicationWithTcf(gdpr = 0, purposes = ""))

        assertEquals(1, inits.size)
    }

    @Test
    fun `GRANTED thi khoi tao dung mot lan`() {
        ConsentState.setTier(ConsentTier.GRANTED)
        val app = applicationWithTcf(gdpr = 1, purposes = "1111111111")

        MetaSdkStarter.start(app)
        MetaSdkStarter.start(app)

        assertEquals(listOf(app), inits)
    }

    // ═══ F6: tầng cho phép sau đó trong cùng process ═══

    @Test
    fun `bo qua vi DENIED roi tang cho phep thi followTier khoi tao mot lan, UNKNOWN thi chua`() =
        mockStatic(Log::class.java).use { log ->
            ConsentState.setTier(ConsentTier.DENIED)
            val app = applicationWithTcf(gdpr = 1, purposes = "0000000000")
            MetaSdkStarter.start(app)

            ConsentState.setTier(ConsentTier.UNKNOWN)   // UNKNOWN không bao giờ là đồng ý (Điều 13)
            MetaSdkStarter.followTier()
            assertEquals(0, inits.size)

            ConsentState.setTier(ConsentTier.PARTIAL)
            MetaSdkStarter.followTier()
            MetaSdkStarter.followTier()

            assertEquals(listOf(app), inits)
            log.verify({ Log.d(eq(LogUtil.TAG), contains("meta sdk: consent now allows it, starting (tier=PARTIAL)")) }, times(1))
        }

    /** Chưa từng bị bỏ qua (app không gọi `initMetaSdkAfterConsent`, hay cổng chưa mở): đổi tầng không khởi tạo gì. */
    @Test
    fun `followTier khi chua tung bi bo qua thi khong khoi tao`() {
        ConsentState.setTier(ConsentTier.GRANTED)

        MetaSdkStarter.followTier()

        assertEquals(0, inits.size)
    }

    /** Không có `facebook-core` thì bỏ qua trước khi xét consent: không có dòng "not started" gây nhiễu. */
    @Test
    fun `thieu facebook-core thi khong khoi tao va khong log tu choi`() = mockStatic(Log::class.java).use { log ->
        MetaSdkStarter.facebookCoreAvailable = { false }
        ConsentState.setTier(ConsentTier.DENIED)

        MetaSdkStarter.start(applicationWithTcf(gdpr = 1, purposes = "0000000000"))
        ConsentState.setTier(ConsentTier.GRANTED)
        MetaSdkStarter.followTier()

        assertEquals(0, inits.size)
        log.verify({ Log.e(eq(LogUtil.TAG), contains("meta sdk: not started")) }, never())
    }

    // ═══ Review N1: facebook-core lưu cờ auto-init trên máy; từ chối thì ghi `false` đè ═══

    /**
     * Initializer thật (không phải seam ghi lại), `FacebookSdk` là mock tĩnh, [metaThread] giữ việc tới khi test chạy.
     * `AppEventsLogger.activateApp` đi qua `Companion` (lời gọi instance, mock tĩnh không chặn được), nên dùng
     * `fullyInitialize()` — bước ngay trước nó — để đếm lần khởi tạo. `isInitialized()` trả `true` cho `MetaSdkStarter`
     * (cờ được lưu) và `false` khi `activateApp` thật hỏi: nó ném ngay, không chạy phần thật của facebook-core.
     */
    private fun withRealFacebookCore(block: (MockedStatic<FacebookSdk>, MockedStatic<Log>) -> Unit) {
        MetaSdkStarter.resetForTest()
        MetaSdkStarter.facebookCoreAvailable = { true }
        MetaSdkStarter.executor = metaThread
        mockStatic(FacebookSdk::class.java).use { fb ->
            mockStatic(Log::class.java).use { log ->
                fb.`when`<Boolean> { FacebookSdk.isInitialized() }.thenAnswer {
                    Thread.currentThread().stackTrace.none { it.className.startsWith("com.facebook.appevents") }
                }
                block(fb, log)
            }
        }
    }

    private fun MockedStatic<FacebookSdk>.verifyFlags(enabled: Boolean, count: Int) {
        verify({ FacebookSdk.setAutoInitEnabled(enabled) }, times(count))
        verify({ FacebookSdk.setAutoLogAppEventsEnabled(enabled) }, times(count))
        verify({ FacebookSdk.setAdvertiserIDCollectionEnabled(enabled) }, times(count))
    }

    /**
     * Máy nâng cấp từ ≤ 1.3.x (hay từng cho phép): cờ `true` đã lưu làm `FacebookInitProvider` tự khởi tạo và log app
     * events lúc mở app, trước cổng consent. Từ chối thì ghi `false` cho cả ba cờ, trên luồng nền; không `activateApp`.
     */
    @Test
    fun `DENIED trong GDPR thi ghi false cho ba co auto-init tren executor, khong activateApp`() =
        withRealFacebookCore { fb, log ->
            ConsentState.setTier(ConsentTier.DENIED)

            MetaSdkStarter.start(applicationWithTcf(gdpr = 1, purposes = "0000000000"))
            fb.verify({ FacebookSdk.setAutoInitEnabled(false) }, never())   // chưa chạy trên luồng gọi
            metaThread.runAll()

            fb.verifyFlags(enabled = false, count = 1)
            fb.verify({ FacebookSdk.setAutoInitEnabled(true) }, never())
            fb.verify({ FacebookSdk.fullyInitialize() }, never())   // initializer (fullyInitialize → activateApp) không chạy
            log.verify({ Log.d(LogUtil.TAG, "meta sdk: auto-init disabled (tier=DENIED)") }, times(1))
        }

    @Test
    fun `GRANTED thi ghi true cho co auto-init va khoi tao`() = withRealFacebookCore { fb, _ ->
        ConsentState.setTier(ConsentTier.GRANTED)
        val app = applicationWithTcf(gdpr = 1, purposes = "1111111111")

        MetaSdkStarter.start(app)
        metaThread.runAll()

        fb.verify({ FacebookSdk.setAutoInitEnabled(true) }, times(1))
        fb.verify({ FacebookSdk.setAutoLogAppEventsEnabled(true) }, times(1))
        fb.verify({ FacebookSdk.setAutoInitEnabled(false) }, never())
        fb.verify({ FacebookSdk.fullyInitialize() }, times(1))
    }

    /**
     * Meta đã chạy trong process rồi tầng chuyển sang từ chối: lần này không gỡ được (provider đã theo dõi), nhưng ghi
     * `false` để lần mở kế không tự khởi tạo. Một lần cho mỗi lần chuyển; cho phép lại thì ghi `true` lại.
     */
    @Test
    fun `dang chay roi rut consent thi ghi false mot lan, cho phep lai thi ghi true lai`() =
        withRealFacebookCore { fb, _ ->
            ConsentState.setTier(ConsentTier.GRANTED)
            val app = applicationWithTcf(gdpr = 1, purposes = "1111111111")
            MetaSdkStarter.start(app)
            metaThread.runAll()

            ConsentState.setTier(ConsentTier.DENIED)
            MetaSdkStarter.followTier()
            MetaSdkStarter.followTier()
            metaThread.runAll()
            fb.verify({ FacebookSdk.setAutoInitEnabled(false) }, times(1))
            fb.verify({ FacebookSdk.setAutoLogAppEventsEnabled(false) }, times(1))
            // Lần khởi tạo cũng ghi false (purpose 1 của mock TCF không cho cá nhân hoá), cộng lần tắt là 2.
            fb.verify({ FacebookSdk.setAdvertiserIDCollectionEnabled(false) }, times(2))

            ConsentState.setTier(ConsentTier.GRANTED)
            MetaSdkStarter.followTier()
            metaThread.runAll()
            fb.verify({ FacebookSdk.setAutoInitEnabled(true) }, times(2))
            fb.verify({ FacebookSdk.fullyInitialize() }, times(1))   // không khởi tạo (activate) hai lần
        }

    /** Bị từ chối lúc mở rồi tầng cho phép: `false` rồi `true` tới facebook-core đúng thứ tự trên cùng hàng đợi. */
    @Test
    fun `bi tu choi roi cho phep thi false truoc true sau tren cung hang doi`() =
        withRealFacebookCore { fb, _ ->
            ConsentState.setTier(ConsentTier.DENIED)
            val app = applicationWithTcf(gdpr = 1, purposes = "1111111111")
            MetaSdkStarter.start(app)
            ConsentState.setTier(ConsentTier.GRANTED)
            MetaSdkStarter.followTier()

            val order = mutableListOf<Boolean>()
            fb.`when`<Unit> { FacebookSdk.setAutoInitEnabled(anyBoolean()) }
                .thenAnswer { order += it.getArgument<Boolean>(0); null }
            metaThread.runAll()

            assertEquals(listOf(false, true), order)
            fb.verify({ FacebookSdk.fullyInitialize() }, times(1))
        }

    // ═══ GPP: opt-out theo luật bang Mỹ → Limited Data Use trước fullyInitialize (1.6.0 Task 8a, O2) ═══

    /** Ghi thứ tự `setDataProcessingOptions`/`fullyInitialize` của facebook-core (mock tĩnh) vào danh sách trả về. */
    private fun MockedStatic<FacebookSdk>.recordGppOrder(): MutableList<String> {
        val calls = mutableListOf<String>()
        `when`<Unit> { FacebookSdk.setDataProcessingOptions(any(), anyInt(), anyInt()) }.thenAnswer {
            calls += "dpo(${it.getArgument<Array<String>>(0).toList()}, ${it.getArgument<Int>(1)}, ${it.getArgument<Int>(2)})"
            null
        }
        `when`<Unit> { FacebookSdk.setDataProcessingOptions(any()) }.thenAnswer {
            calls += "dpo(${it.getArgument<Array<String>>(0).toList()})"
            null
        }
        `when`<Unit> { FacebookSdk.fullyInitialize() }.thenAnswer { calls += "fullyInitialize"; null }
        return calls
    }

    @Test
    fun `GPP opt-out thi LDU truoc fullyInitialize, tren executor`() = withRealFacebookCore { fb, log ->
        val calls = fb.recordGppOrder()
        ConsentState.setTier(ConsentTier.GRANTED)

        MetaSdkStarter.start(applicationWithTcf(gdpr = 0, purposes = "", gpp = { GPP_US_OPT_OUT }))
        assertEquals("chưa chạy trên luồng gọi", emptyList<String>(), calls)
        metaThread.runAll()

        assertEquals("LDU phải trước fullyInitialize", listOf("dpo([LDU], 0, 0)", "fullyInitialize"), calls)
        log.verify { Log.d(eq(LogUtil.TAG), contains("meta sdk: LDU on (gpp=optOut)")) }
    }

    @Test
    fun `GPP khong opt-out thi tat LDU ro rang truoc fullyInitialize, khong GPP thi khong goi`() =
        withRealFacebookCore { fb, _ ->
            val calls = fb.recordGppOrder()
            ConsentState.setTier(ConsentTier.GRANTED)

            MetaSdkStarter.start(applicationWithTcf(gdpr = 0, purposes = "", gpp = { GPP_US_OK }))
            metaThread.runAll()
            assertEquals(listOf("dpo([])", "fullyInitialize"), calls)

            MetaSdkStarter.resetForTest()
            MetaSdkStarter.facebookCoreAvailable = { true }
            MetaSdkStarter.executor = metaThread
            calls.clear()
            MetaSdkStarter.start(applicationWithTcf(gdpr = 0, purposes = ""))
            metaThread.runAll()
            assertEquals(listOf("fullyInitialize"), calls)
        }

    @Test
    fun `followGpp sau khoi tao thi xep LDU len executor, chua khoi tao thi khong`() = withRealFacebookCore { fb, _ ->
        val calls = fb.recordGppOrder()
        MetaSdkStarter.followGpp(GppConsent.decode(GPP_US_OPT_OUT, "7"))
        metaThread.runAll()
        assertEquals("chưa khởi tạo: không gọi", emptyList<String>(), calls)

        ConsentState.setTier(ConsentTier.GRANTED)
        MetaSdkStarter.start(applicationWithTcf(gdpr = 0, purposes = "", gpp = { GPP_US_OK }))
        MetaSdkStarter.followGpp(GppConsent.decode(GPP_US_OPT_OUT, "7"))
        MetaSdkStarter.followGpp(null)
        metaThread.runAll()

        assertEquals(listOf("dpo([])", "fullyInitialize", "dpo([LDU], 0, 0)"), calls)
    }
}
