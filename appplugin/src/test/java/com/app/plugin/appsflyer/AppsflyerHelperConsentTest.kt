package com.app.plugin.appsflyer

import android.app.Activity
import android.content.Context
import android.util.Log
import com.app.plugin.consent.ConsentState
import com.app.plugin.consent.ConsentTier
import com.app.plugin.consent.GPP_US_OK
import com.app.plugin.consent.GPP_US_OPT_OUT
import com.app.plugin.consent.contextWithTcf
import com.app.plugin.until.LogUtil
import com.appsflyer.AppsFlyerConsent
import com.appsflyer.AppsFlyerConversionListener
import com.appsflyer.AppsFlyerLib
import com.appsflyer.attribution.AppsFlyerRequestListener
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.contains
import org.mockito.ArgumentMatchers.eq
import org.mockito.ArgumentMatchers.same
import org.mockito.ArgumentMatchers.startsWith
import org.mockito.ArgumentCaptor
import org.mockito.MockedStatic
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.mockingDetails
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify

/**
 * AppsFlyer chỉ khởi động sau khi UMP trả lời, với [AppsFlyerConsent] theo tầng consent; đổi tầng
 * sau đó (thu hồi qua privacy options) phải tới AppsFlyer (Task 9, phụ lục A1, A2).
 */
class AppsflyerHelperConsentTest {
    @Before fun setUp() = ConsentState.resetForTest()
    @After fun tearDown() = ConsentState.resetForTest()

    private fun gdpr(dataUsage: Boolean, personalization: Boolean, storage: Boolean) =
        AppsFlyerConsent(true, dataUsage, personalization, storage)

    private val nonGdpr = AppsFlyerConsent(false, null, null, null)

    // ═══ Ánh xạ tầng → AppsFlyerConsent ═══

    @Test fun `ngoai GDPR ma tang khong phai DENIED thi AppsFlyer coi la nguoi dung khong thuoc GDPR`() {
        assertEquals(nonGdpr, AppsflyerHelper.consentFor(ConsentTier.UNKNOWN, gdprApplies = false))
        assertEquals(nonGdpr, AppsflyerHelper.consentFor(ConsentTier.PARTIAL, gdprApplies = false))
        assertEquals(nonGdpr, AppsflyerHelper.consentFor(ConsentTier.GRANTED, gdprApplies = false))
    }

    /**
     * Fix wave 2, Minor 6: `IABTCF_gdprApplies = 0` có thể là giá trị cũ còn lại khi người dùng đã vào
     * EEA; lời từ chối thật thắng nó, và AppsFlyer nhận người dùng GDPR không đồng ý gì.
     */
    @Test fun `DENIED thang IABTCF_gdprApplies = 0 thi AppsFlyer nhan tu choi`() =
        assertEquals(gdpr(false, false, false), AppsflyerHelper.consentFor(ConsentTier.DENIED, gdprApplies = false))

    @Test fun `GRANTED trong GDPR thi dong y het`() =
        assertEquals(gdpr(true, true, true), AppsflyerHelper.consentFor(ConsentTier.GRANTED, gdprApplies = true))

    @Test fun `PARTIAL trong GDPR thi dung du lieu va luu tru, khong ca nhan hoa`() =
        assertEquals(gdpr(true, false, true), AppsflyerHelper.consentFor(ConsentTier.PARTIAL, gdprApplies = true))

    @Test fun `DENIED va UNKNOWN trong GDPR thi khong dung du lieu`() {
        assertEquals(gdpr(false, false, false), AppsflyerHelper.consentFor(ConsentTier.DENIED, gdprApplies = true))
        assertEquals(gdpr(false, false, false), AppsflyerHelper.consentFor(ConsentTier.UNKNOWN, gdprApplies = true))
    }

    /** Phụ lục A1: `isUserSubjectToGDPR = gdprApplies != false`; không biết không bao giờ là lý do cấp. */
    @Test fun `khong biet GDPR co ap dung khong thi coi la thuoc GDPR`() {
        assertEquals(gdpr(false, false, false), AppsflyerHelper.consentFor(ConsentTier.UNKNOWN, gdprApplies = null))
        assertEquals(gdpr(true, true, true), AppsflyerHelper.consentFor(ConsentTier.GRANTED, gdprApplies = null))
    }

    // ═══ startAfterConsent ═══

    private fun withAppsFlyer(block: (AppsFlyerLib, MockedStatic<Log>) -> Unit) {
        val af = mock(AppsFlyerLib::class.java)
        mockStatic(AppsFlyerLib::class.java).use { lib ->
            lib.`when`<AppsFlyerLib> { AppsFlyerLib.getInstance() }.thenReturn(af)
            mockStatic(Log::class.java).use { log -> block(af, log) }
        }
    }

    @Test fun `start dat consent truoc init va start, goi lai la no-op`() = withAppsFlyer { af, _ ->
        val helper = AppsflyerHelper()
        val context = contextWithTcf(gdpr = 1, purposes = "1111111111", afAppId = "devKey")

        helper.startAfterConsent(context, ConsentTier.GRANTED, gdprApplies = true)
        helper.startAfterConsent(context, ConsentTier.PARTIAL, gdprApplies = true)

        val order = inOrder(af)
        order.verify(af).setConsentData(gdpr(true, true, true))
        order.verify(af).init(eq("devKey"), same<AppsFlyerConversionListener>(helper), any(Context::class.java))
        order.verify(af).start(any(Context::class.java), eq("devKey"), any(AppsFlyerRequestListener::class.java))
        verify(af, times(1)).setConsentData(any())
        verify(af, times(1)).init(anyString(), any(), any(Context::class.java))
        verify(af, times(1)).start(any(Context::class.java), any(), any())
    }

    /**
     * Review C I1: consent mới tới AppsFlyer đang chạy qua [AppsflyerHelper.followTier] (`applyTier`), không qua
     * listener riêng. Tầng vẫn cho phép (ngoài GDPR) thì chỉ đổi consent, không stop.
     */
    @Test fun `doi tang sau khi start thi consent moi toi AppsFlyer qua followTier`() = withAppsFlyer { af, _ ->
        val helper = AppsflyerHelper()
        val context = contextWithTcf(gdpr = 1, purposes = "1111111111", afAppId = "devKey")
        helper.startAfterConsent(context, ConsentTier.GRANTED, gdprApplies = true)

        helper.followTier(context, ConsentTier.UNKNOWN, gdprApplies = false, activity = null)   // ngoài GDPR: vẫn chạy
        helper.followTier(context, ConsentTier.PARTIAL, gdprApplies = true, activity = null)

        val order = inOrder(af)
        order.verify(af).setConsentData(gdpr(true, true, true))
        order.verify(af).setConsentData(nonGdpr)
        order.verify(af).setConsentData(gdpr(true, false, true))
        verify(af, times(1)).start(any(Context::class.java), any(), any())
        verify(af, never()).stop(anyBoolean(), any(Context::class.java))
    }

    // ═══ Review cuối I1: dừng khi tầng không còn cho phép (không chỉ DENIED) ═══

    /**
     * GRANTED → UNKNOWN trong process (`ConsentManager.reset()`, hoặc trả lời lại mà `IABTCF_*` không đọc được): FCM
     * xoá token, Meta ghi `false`; AppsFlyer cũng phải `stop(true)` đúng một lần, không chạy tiếp với consent toàn false.
     */
    @Test fun `GRANTED roi UNKNOWN trong GDPR thi stop(true) dung mot lan va log kem tang`() = withAppsFlyer { af, log ->
        val helper = AppsflyerHelper()
        helper.startAfterConsent(grantedCtx, ConsentTier.GRANTED, gdprApplies = true)

        helper.followTier(grantedCtx, ConsentTier.UNKNOWN, gdprApplies = true, activity = null)
        helper.followTier(grantedCtx, ConsentTier.UNKNOWN, gdprApplies = true, activity = null)

        verify(af, times(1)).stop(eq(true), any(Context::class.java))
        verify(af, never()).stop(eq(false), any(Context::class.java))
        verify(af).setConsentData(gdpr(false, false, false))   // từ chối trước khi stop
        log.verify({ Log.d(LogUtil.TAG, "appsflyer stopped: consent withdrawn (consent tier=UNKNOWN in GDPR scope)") }, times(1))
    }

    @Test fun `GRANTED roi UNKNOWN khi khong biet GDPR thi stop(true) dung mot lan`() = withAppsFlyer { af, _ ->
        val helper = AppsflyerHelper()
        helper.startAfterConsent(grantedCtx, ConsentTier.GRANTED, gdprApplies = null)

        helper.followTier(grantedCtx, ConsentTier.UNKNOWN, gdprApplies = null, activity = null)

        verify(af, times(1)).stop(eq(true), any(Context::class.java))
    }

    @Test fun `GRANTED roi UNKNOWN ngoai GDPR thi khong stop, chi doi consent`() = withAppsFlyer { af, _ ->
        val helper = AppsflyerHelper()
        helper.startAfterConsent(grantedCtx, ConsentTier.GRANTED, gdprApplies = false)

        helper.followTier(grantedCtx, ConsentTier.UNKNOWN, gdprApplies = false, activity = null)

        verify(af, never()).stop(anyBoolean(), any(Context::class.java))
        verify(af, times(2)).setConsentData(nonGdpr)
    }

    /** Review 1.6.1 Task 5: dòng `consent updated` ghi `outside` khi `gdprApplies == false` (không chỉ `in`). */
    @Test fun `UNKNOWN ngoai GDPR, consent updated ghi outside`() = withAppsFlyer { _, log ->
        val helper = AppsflyerHelper()
        helper.startAfterConsent(grantedCtx, ConsentTier.GRANTED, gdprApplies = false)

        helper.followTier(grantedCtx, ConsentTier.UNKNOWN, gdprApplies = false, activity = null)

        log.verify {
            Log.d(eq(LogUtil.TAG), startsWith("appsflyer consent updated: consent tier=UNKNOWN outside GDPR scope "))
        }
    }

    /** 1.6.3 Task 4 (review 1.6.2 R-C): ba dòng còn lại cũng ghi `outside` khi gdprApplies == false. */
    @Test fun `DENIED ngoai GDPR, not started ghi outside`() = withAppsFlyer { af, log ->
        AppsflyerHelper().startAfterConsent(deniedCtx, ConsentTier.DENIED, gdprApplies = false)

        verify(af, never()).start(any(Context::class.java), anyString(), any(AppsFlyerRequestListener::class.java))
        log.verify({ Log.d(LogUtil.TAG, "appsflyer not started: consent tier=DENIED outside GDPR scope (gdprApplies=false)") }, times(1))
    }

    @Test fun `GRANTED roi DENIED ngoai GDPR, stopped ghi outside`() = withAppsFlyer { af, log ->
        val helper = AppsflyerHelper()
        helper.startAfterConsent(grantedCtx, ConsentTier.GRANTED, gdprApplies = false)

        helper.followTier(grantedCtx, ConsentTier.DENIED, gdprApplies = false, activity = null)

        verify(af, times(1)).stop(eq(true), any(Context::class.java))
        log.verify({ Log.d(LogUtil.TAG, "appsflyer stopped: consent withdrawn (consent tier=DENIED outside GDPR scope)") }, times(1))
    }

    @Test fun `GRANTED roi DENIED roi GRANTED ngoai GDPR, resumed ghi outside`() = withAppsFlyer { af, log ->
        val helper = AppsflyerHelper()
        helper.startAfterConsent(grantedCtx, ConsentTier.GRANTED, gdprApplies = false)
        helper.followTier(grantedCtx, ConsentTier.DENIED, gdprApplies = false, activity = null)

        helper.followTier(grantedCtx, ConsentTier.GRANTED, gdprApplies = false, activity = null)

        verify(af, times(1)).stop(eq(false), any(Context::class.java))
        verify(af, times(2)).start(any(Context::class.java), eq("devKey"), any(AppsFlyerRequestListener::class.java))
        log.verify({ Log.d(LogUtil.TAG, "appsflyer resumed: consent granted again (consent tier=GRANTED outside GDPR scope)") }, times(1))
    }

    /** 1.6.3 Task 4 fix (review R-C I1): đường `followTier` -> `logNotStarted` cũng ghi `outside` khi gdprApplies == false. */
    @Test fun `UNKNOWN roi DENIED ngoai GDPR, followTier not started ghi outside`() = withAppsFlyer { af, log ->
        val helper = AppsflyerHelper()
        helper.startAfterConsent(deniedCtx, ConsentTier.UNKNOWN, gdprApplies = true)

        helper.followTier(deniedCtx, ConsentTier.DENIED, gdprApplies = false, activity = null)

        verifyNotStarted(af)
        log.verify({ Log.d(LogUtil.TAG, "appsflyer not started: consent tier=DENIED outside GDPR scope (gdprApplies=false)") }, times(1))
    }

    @Test fun `GRANTED roi UNKNOWN roi GRANTED thi stop(true), stop(false) roi start lai`() = withAppsFlyer { af, log ->
        val helper = AppsflyerHelper()
        helper.startAfterConsent(grantedCtx, ConsentTier.GRANTED, gdprApplies = true)
        helper.followTier(grantedCtx, ConsentTier.UNKNOWN, gdprApplies = true, activity = null)

        helper.followTier(grantedCtx, ConsentTier.GRANTED, gdprApplies = true, activity = null)

        val order = inOrder(af)
        order.verify(af).stop(eq(true), any(Context::class.java))
        order.verify(af).stop(eq(false), any(Context::class.java))
        order.verify(af).setConsentData(gdpr(true, true, true))
        order.verify(af).start(any(Context::class.java), eq("devKey"), any(AppsFlyerRequestListener::class.java))
        verify(af, times(2)).start(any(Context::class.java), eq("devKey"), any(AppsFlyerRequestListener::class.java))
        verify(af, times(1)).init(anyString(), any(), any(Context::class.java))
        log.verify({ Log.d(LogUtil.TAG, "appsflyer resumed: consent granted again (consent tier=GRANTED in GDPR scope)") }, times(1))
    }

    /** Review C I1 (b): GRANTED → PARTIAL sau khi start: `setConsentData` được gọi lại qua `followTier`, không start lại. */
    @Test fun `GRANTED roi PARTIAL sau khi start thi setConsentData lai qua followTier`() = withAppsFlyer { af, log ->
        val helper = AppsflyerHelper()
        helper.startAfterConsent(grantedCtx, ConsentTier.GRANTED, gdprApplies = true)

        helper.followTier(grantedCtx, ConsentTier.PARTIAL, gdprApplies = true, activity = null)

        val order = inOrder(af)
        order.verify(af).setConsentData(gdpr(true, true, true))
        order.verify(af).setConsentData(gdpr(true, false, true))
        verify(af, times(2)).setConsentData(any())
        verify(af, times(1)).start(any(Context::class.java), any(), any())
        log.verify { Log.d(eq(LogUtil.TAG), contains("appsflyer consent updated: consent tier=PARTIAL in GDPR scope")) }
    }

    /**
     * Review C I1 (a): `startAfterConsent` không đăng ký listener nào với `ConsentState` (listener đó đòi khoá của
     * `ConsentState` trong khi giữ khoá helper). `setTier` sau start không tự tới AppsFlyer: chỉ `followTier` đưa.
     */
    @Test fun `startAfterConsent khong dang ky listener tang, setTier khong tu toi AppsFlyer`() = withAppsFlyer { af, _ ->
        val helper = AppsflyerHelper()
        helper.startAfterConsent(grantedCtx, ConsentTier.GRANTED, gdprApplies = true)

        ConsentState.setTier(ConsentTier.PARTIAL)
        ConsentState.setTier(ConsentTier.DENIED)

        verify(af, times(1)).setConsentData(any())   // chỉ lần start
        verify(af, never()).stop(anyBoolean(), any(Context::class.java))
    }

    /**
     * Review C I1 (a), hợp đồng nguồn: `AppsflyerHelper` không gọi hàm nào của `ConsentState` (mọi hàm của helper
     * chạy trong khoá helper; `ConsentState.setTier` giữ khoá của nó khi gọi listener → `applyTier` → `followTier`).
     */
    @Test fun `AppsflyerHelper khong goi ConsentState (hop dong nguon, khong dao thu tu khoa)`() {
        // 1.6.4 Task 2: bộ lọc partner GPP tách sang AppsflyerGppFilter.kt, vẫn chạy trong khoá helper → cùng hợp đồng.
        val calls = listOf("AppsflyerHelper.kt", "AppsflyerGppFilter.kt").flatMap { name ->
            val file = java.io.File("src/main/java/com/app/plugin/appsflyer/$name")
            assertTrue("Không thấy ${file.path}: hợp đồng nguồn sẽ xanh rỗng", file.isFile)
            file.readText()
                .replace(Regex("/\\*[\\s\\S]*?\\*/"), "")   // bỏ KDoc/khối chú thích
                .lines().map { it.substringBefore("//") }
                .filterNot { it.trimStart().startsWith("import ") }
                .filter { it.contains("ConsentState.") }.map { "$name: $it" }
        }
        assertEquals("AppsflyerHelper gọi ConsentState: $calls", emptyList<String>(), calls)
    }

    /**
     * Review C I1 (c), hai luồng: luồng khác giữ khoá `ConsentState` (đang chạy listener tầng, như `setTier` của app
     * dùng CMP khác trên luồng nền) và chờ khoá helper (`followTier`), trong lúc luồng test mở cổng
     * (`startAfterConsent`, giữ khoá helper). Trước sửa `startAfterConsent` đòi khoá `ConsentState` (`onChange`) khi giữ
     * khoá helper → đảo thứ tự khoá, treo (ở đây listener chỉ chờ có hạn 2 s rồi bỏ, nên test đỏ thay vì treo cả suite).
     * Sau sửa cả hai xong trong ≤ 2 s.
     */
    @Test fun `hai luong mo cong va setTier cung luc thi ca hai xong trong 2 s (truoc sua treo do dao thu tu khoa)`() =
        withAppsFlyer { af, _ ->
            val helper = AppsflyerHelper()
            val inConsentLock = java.util.concurrent.CountDownLatch(1)
            val startReturned = java.util.concurrent.CountDownLatch(1)
            val followed = java.util.concurrent.CountDownLatch(1)
            val timedOut = java.util.concurrent.atomic.AtomicBoolean(false)
            val failure = java.util.concurrent.atomic.AtomicReference<Throwable?>(null)
            // AdSdkStartup.applyTier: listener tầng (chạy trong khoá ConsentState) gọi followTier (khoá helper).
            ConsentState.onChange { tier ->
                inConsentLock.countDown()
                if (!startReturned.await(2, java.util.concurrent.TimeUnit.SECONDS)) {
                    timedOut.set(true)   // startAfterConsent kẹt chờ khoá ConsentState: bỏ, để không treo thật
                    return@onChange
                }
                helper.followTier(grantedCtx, tier, gdprApplies = true, activity = null)
            }
            val other = Thread {
                try {
                    // mockStatic chỉ có hiệu lực trên luồng tạo nó: luồng này đăng ký bản của nó, cùng mock `af`.
                    mockStatic(AppsFlyerLib::class.java).use { lib ->
                        lib.`when`<AppsFlyerLib> { AppsFlyerLib.getInstance() }.thenReturn(af)
                        ConsentState.setTier(ConsentTier.PARTIAL)
                    }
                    followed.countDown()
                } catch (t: Throwable) {
                    failure.set(t)
                }
            }.apply { isDaemon = true; start() }
            assertTrue("luồng kia vào khoá ConsentState", inConsentLock.await(2, java.util.concurrent.TimeUnit.SECONDS))

            val t0 = System.nanoTime()
            helper.startAfterConsent(grantedCtx, ConsentTier.GRANTED, gdprApplies = true)
            startReturned.countDown()
            val startMs = (System.nanoTime() - t0) / 1_000_000

            assertTrue("setTier + followTier xong trong 2 s", followed.await(2, java.util.concurrent.TimeUnit.SECONDS))
            other.join(2_000)
            assertEquals(null, failure.get())
            assertFalse("startAfterConsent không được chờ khoá ConsentState (mất ${startMs} ms)", timedOut.get())
            assertTrue("startAfterConsent xong ngay (mất ${startMs} ms)", startMs < 2_000)
            verify(af).setConsentData(gdpr(true, false, true))   // followTier(PARTIAL) trên luồng kia
        }

    @Test fun `thieu af_app_id thi khong start, doi tang khong cham AppsFlyer va log loi`() = withAppsFlyer { af, log ->
        val helper = AppsflyerHelper()
        val noAppId = contextWithTcf(gdpr = 0, purposes = "")

        helper.startAfterConsent(noAppId, ConsentTier.GRANTED, gdprApplies = false)
        helper.followTier(noAppId, ConsentTier.DENIED, gdprApplies = false, activity = null)

        verify(af, never()).setConsentData(any())
        verify(af, never()).start(any(Context::class.java), any(), any())
        log.verify { Log.e(eq(LogUtil.TAG), contains("af_app_id missing")) }
    }

    @Test fun `AppsFlyer nem loi luc start thi log loi, khong nem ra ngoai`() = withAppsFlyer { af, log ->
        doThrow(IllegalStateException("af boom")).`when`(af)
            .start(any(Context::class.java), any(), any(AppsFlyerRequestListener::class.java))
        val helper = AppsflyerHelper()

        helper.startAfterConsent(
            contextWithTcf(gdpr = 1, purposes = "1111111111", afAppId = "devKey"),
            ConsentTier.GRANTED,
            gdprApplies = true,
        )

        log.verify { Log.e(eq(LogUtil.TAG), contains("AppsFlyer start after consent failed")) }
    }

    @Test fun `cap nhat consent loi thi log loi, khong nem ra ngoai`() = withAppsFlyer { af, log ->
        val helper = AppsflyerHelper()
        helper.startAfterConsent(
            contextWithTcf(gdpr = 1, purposes = "1111111111", afAppId = "devKey"),
            ConsentTier.GRANTED,
            gdprApplies = true,
        )
        doThrow(IllegalStateException("af boom")).`when`(af).setConsentData(any())

        helper.followTier(grantedCtx, ConsentTier.PARTIAL, gdprApplies = true, activity = null)

        log.verify { Log.e(eq(LogUtil.TAG), contains("AppsFlyer consent update failed")) }
    }

    // ═══ #12 (DOC/66): không start cho DENIED; stop(true) khi rút consent, stop(false) + start khi cấp lại ═══

    private val deniedCtx: Context get() = contextWithTcf(gdpr = 1, purposes = "0000000000", afAppId = "devKey")

    private fun verifyNotStarted(af: AppsFlyerLib) {
        verify(af, never()).setConsentData(any())
        verify(af, never()).init(anyString(), any(), any(Context::class.java))
        verify(af, never()).start(any(Context::class.java), any(), any())
    }

    @Test fun `DENIED thi khong setConsentData, khong init, khong start, log mot lan moi process`() =
        withAppsFlyer { af, log ->
            val helper = AppsflyerHelper()

            helper.startAfterConsent(deniedCtx, ConsentTier.DENIED, gdprApplies = true)
            helper.startAfterConsent(deniedCtx, ConsentTier.DENIED, gdprApplies = true)   // hỏi lại: không log lại

            verifyNotStarted(af)
            log.verify({ Log.d(LogUtil.TAG, "appsflyer not started: consent tier=DENIED in GDPR scope (gdprApplies=true)") }, times(1))
        }

    /**
     * Review C Minor 6/7 (F5): một dòng cho mỗi tầng bị từ chối trong process, để dòng `tier=DENIED` luôn có khi
     * người dùng tới DENIED: cổng mở lúc UNKNOWN rồi tầng thành DENIED → 2 dòng, không lặp.
     */
    @Test fun `tu choi UNKNOWN o cong roi DENIED thi log hai dong, moi tang mot lan`() = withAppsFlyer { af, log ->
        val helper = AppsflyerHelper()

        helper.startAfterConsent(deniedCtx, ConsentTier.UNKNOWN, gdprApplies = true)
        helper.startAfterConsent(deniedCtx, ConsentTier.UNKNOWN, gdprApplies = true)
        helper.followTier(deniedCtx, ConsentTier.DENIED, gdprApplies = true, activity = null)
        helper.followTier(deniedCtx, ConsentTier.DENIED, gdprApplies = true, activity = null)

        verifyNotStarted(af)
        log.verify({ Log.d(LogUtil.TAG, "appsflyer not started: consent tier=UNKNOWN in GDPR scope (gdprApplies=true)") }, times(1))
        log.verify({ Log.d(LogUtil.TAG, "appsflyer not started: consent tier=DENIED in GDPR scope (gdprApplies=true)") }, times(1))
    }

    // ═══ Review C Minor 1 (F2): revenue/IAP chỉ tới AppsFlyer khi đã start ═══

    private fun AppsflyerHelper.sendRevenueAndIap() {
        logAdRevenue(0, "admob", "ca-app-pub-x/1", "interstitial", 0.01, "USD")
        logIapPurchase("sku_remove_ads", 1.99, "USD")
        logAdRevenue(1, "applovin", "max-unit", "banner", 0.002, "USD")
    }

    @Test fun `chua start (DENIED o cong) thi revenue va IAP khong goi AppsFlyerLib, log mot lan`() {
        val af = mock(AppsFlyerLib::class.java)
        mockStatic(AppsFlyerLib::class.java).use { lib ->
            lib.`when`<AppsFlyerLib> { AppsFlyerLib.getInstance() }.thenReturn(af)
            mockStatic(Log::class.java).use { log ->
                val helper = AppsflyerHelper()
                helper.startAfterConsent(deniedCtx, ConsentTier.DENIED, gdprApplies = true)

                helper.sendRevenueAndIap()

                lib.verify({ AppsFlyerLib.getInstance() }, never())
                log.verify({ Log.d(LogUtil.TAG, "appsflyer event dropped: not started (tier=DENIED)") }, times(1))
                log.verify({ Log.d(eq(LogUtil.TAG), contains("appsflyer event dropped")) }, times(1))
            }
        }
    }

    @Test fun `stop vi rut consent thi revenue khong toi AppsFlyer nua`() = withAppsFlyer { af, log ->
        val helper = AppsflyerHelper()
        helper.startAfterConsent(grantedCtx, ConsentTier.GRANTED, gdprApplies = true)
        helper.logAdRevenue(0, "admob", "ca-app-pub-x/1", "interstitial", 0.01, "USD")
        verify(af, times(1)).logAdRevenue(any(), any())

        helper.followTier(grantedCtx, ConsentTier.DENIED, gdprApplies = true, activity = null)
        helper.sendRevenueAndIap()

        verify(af, times(1)).logAdRevenue(any(), any())
        verify(af, never()).logEvent(any(Context::class.java), anyString(), any())
        log.verify({ Log.d(LogUtil.TAG, "appsflyer event dropped: not started (tier=DENIED)") }, times(1))
    }

    @Test fun `da start thi revenue toi AppsFlyer, khong log drop`() = withAppsFlyer { af, log ->
        val helper = AppsflyerHelper()
        helper.startAfterConsent(grantedCtx, ConsentTier.GRANTED, gdprApplies = true)

        helper.logAdRevenue(0, "admob", "ca-app-pub-x/1", "interstitial", 0.01, "USD")

        verify(af, times(1)).logAdRevenue(any(), any())
        log.verify({ Log.d(eq(LogUtil.TAG), contains("appsflyer event dropped")) }, never())
    }

    /** `IABTCF_gdprApplies = 0` không thắng `DENIED` (Minor 6): vẫn không start. */
    @Test fun `DENIED ma IABTCF_gdprApplies = 0 thi van khong start`() = withAppsFlyer { af, _ ->
        AppsflyerHelper().startAfterConsent(deniedCtx, ConsentTier.DENIED, gdprApplies = false)

        verifyNotStarted(af)
    }

    /** `UNKNOWN` không bao giờ là đồng ý: trong GDPR hoặc không biết thì chưa start; chỉ `IABTCF_gdprApplies = 0` mới cho. */
    @Test fun `UNKNOWN trong GDPR hoac khong biet GDPR thi khong start, ngoai GDPR thi start`() = withAppsFlyer { af, _ ->
        AppsflyerHelper().startAfterConsent(deniedCtx, ConsentTier.UNKNOWN, gdprApplies = true)
        AppsflyerHelper().startAfterConsent(deniedCtx, ConsentTier.UNKNOWN, gdprApplies = null)
        verifyNotStarted(af)

        AppsflyerHelper().startAfterConsent(deniedCtx, ConsentTier.UNKNOWN, gdprApplies = false)

        verify(af).setConsentData(nonGdpr)
        verify(af, times(1)).start(any(Context::class.java), eq("devKey"), any(AppsFlyerRequestListener::class.java))
    }

    @Test fun `GRANTED thi start dung mot lan`() = withAppsFlyer { af, _ ->
        val helper = AppsflyerHelper()

        helper.startAfterConsent(grantedCtx, ConsentTier.GRANTED, gdprApplies = true)
        helper.followTier(grantedCtx, ConsentTier.PARTIAL, gdprApplies = true, activity = null)

        verify(af, times(1)).start(any(Context::class.java), eq("devKey"), any(AppsFlyerRequestListener::class.java))
        verify(af, never()).stop(anyBoolean(), any(Context::class.java))
    }

    @Test fun `GRANTED roi DENIED thi stop(true) dung mot lan va log`() = withAppsFlyer { af, log ->
        val helper = AppsflyerHelper()
        helper.startAfterConsent(grantedCtx, ConsentTier.GRANTED, gdprApplies = true)

        helper.followTier(grantedCtx, ConsentTier.DENIED, gdprApplies = true, activity = null)
        helper.followTier(grantedCtx, ConsentTier.DENIED, gdprApplies = true, activity = null)

        verify(af, times(1)).stop(eq(true), any(Context::class.java))
        verify(af, never()).stop(eq(false), any(Context::class.java))
        log.verify({ Log.d(LogUtil.TAG, "appsflyer stopped: consent withdrawn (consent tier=DENIED in GDPR scope)") }, times(1))
    }

    @Test fun `rut consent roi cap lai thi stop(false) roi start lai, init mot lan`() = withAppsFlyer { af, _ ->
        val helper = AppsflyerHelper()
        val activity = mock(Activity::class.java)
        helper.startAfterConsent(grantedCtx, ConsentTier.GRANTED, gdprApplies = true)
        helper.followTier(grantedCtx, ConsentTier.DENIED, gdprApplies = true, activity = null)

        helper.followTier(grantedCtx, ConsentTier.GRANTED, gdprApplies = true, activity = activity)

        val order = inOrder(af)
        order.verify(af).stop(eq(true), any(Context::class.java))
        order.verify(af).stop(eq(false), any(Context::class.java))
        order.verify(af).setConsentData(gdpr(true, true, true))
        order.verify(af).start(same(activity), eq("devKey"), any(AppsFlyerRequestListener::class.java))
        verify(af, times(2)).start(any(Context::class.java), eq("devKey"), any(AppsFlyerRequestListener::class.java))
        verify(af, times(1)).init(anyString(), any(), any(Context::class.java))
    }

    /** Bỏ qua ở cổng vì DENIED, rồi đồng ý qua form lại trong cùng process: start lúc đó, không stop nào (chưa từng stop). */
    @Test fun `DENIED o cong roi GRANTED thi start mot lan, khong goi stop`() = withAppsFlyer { af, _ ->
        val helper = AppsflyerHelper()
        helper.startAfterConsent(deniedCtx, ConsentTier.DENIED, gdprApplies = true)

        helper.followTier(deniedCtx, ConsentTier.GRANTED, gdprApplies = true, activity = null)
        helper.followTier(deniedCtx, ConsentTier.PARTIAL, gdprApplies = true, activity = null)

        verify(af).setConsentData(gdpr(true, true, true))
        verify(af, times(1)).start(any(Context::class.java), eq("devKey"), any(AppsFlyerRequestListener::class.java))
        verify(af, never()).stop(anyBoolean(), any(Context::class.java))
    }

    /** Trước khi cổng mở (chưa gọi startAfterConsent), đổi tầng không được start AppsFlyer: nó vẫn chờ cổng. */
    @Test fun `followTier truoc khi cong mo thi khong cham AppsFlyer`() {
        val af = mock(AppsFlyerLib::class.java)
        mockStatic(AppsFlyerLib::class.java).use { lib ->
            lib.`when`<AppsFlyerLib> { AppsFlyerLib.getInstance() }.thenReturn(af)
            mockStatic(Log::class.java).use {
                AppsflyerHelper().followTier(grantedCtx, ConsentTier.GRANTED, gdprApplies = true, activity = null)

                lib.verify({ AppsFlyerLib.getInstance() }, never())
            }
        }
    }

    @Test fun `stop loi thi logE, khong nem ra ngoai`() = withAppsFlyer { af, log ->
        doThrow(IllegalStateException("af stop boom")).`when`(af).stop(anyBoolean(), any(Context::class.java))
        val helper = AppsflyerHelper()
        helper.startAfterConsent(grantedCtx, ConsentTier.GRANTED, gdprApplies = true)

        helper.followTier(grantedCtx, ConsentTier.DENIED, gdprApplies = true, activity = null)

        log.verify { Log.e(eq(LogUtil.TAG), contains("AppsFlyer follow tier failed: af stop boom")) }
    }

    // ═══ Fix wave 2, Important 2: start trên Activity đang chạy ═══

    private val grantedCtx: Context get() = contextWithTcf(gdpr = 1, purposes = "1111111111", afAppId = "devKey")

    @Test fun `co Activity thi start tren Activity, init voi app context`() = withAppsFlyer { af, log ->
        val helper = AppsflyerHelper()
        val context = grantedCtx
        val activity = mock(Activity::class.java)

        helper.startAfterConsent(context, ConsentTier.GRANTED, gdprApplies = true, activity = activity)

        verify(af).init(eq("devKey"), same<AppsFlyerConversionListener>(helper), same(context))
        verify(af).start(same(activity), eq("devKey"), any(AppsFlyerRequestListener::class.java))
        verify(af, times(1)).start(any(Context::class.java), any(), any())
        log.verify({ Log.e(eq(LogUtil.TAG), contains("appsflyer start without activity")) }, never())
    }

    @Test fun `khong co Activity thi start voi app context va log loi`() = withAppsFlyer { af, log ->
        val helper = AppsflyerHelper()
        val context = grantedCtx

        helper.startAfterConsent(context, ConsentTier.GRANTED, gdprApplies = true)

        verify(af).start(same(context), eq("devKey"), any(AppsFlyerRequestListener::class.java))
        log.verify { Log.e(eq(LogUtil.TAG), contains("appsflyer start without activity")) }
    }

    /** Kết quả request launch được log: lỗi (khoá sai, mạng) không câm (Điều 7); thành công là bằng chứng trên máy. */
    @Test fun `ket qua request launch duoc log`() = withAppsFlyer { af, log ->
        val helper = AppsflyerHelper()
        helper.startAfterConsent(grantedCtx, ConsentTier.GRANTED, gdprApplies = true, activity = mock(Activity::class.java))
        val listener = ArgumentCaptor.forClass(AppsFlyerRequestListener::class.java)
        verify(af).start(any(Context::class.java), eq("devKey"), listener.capture())

        listener.value.onError(50, "Status code failure 403")
        listener.value.onSuccess()

        log.verify { Log.e(eq(LogUtil.TAG), contains("appsflyer launch request failed: code=50 Status code failure 403")) }
        log.verify { Log.d(eq(LogUtil.TAG), contains("appsflyer launch request sent (start=activity)")) }
    }

    // ═══ GPP: opt-out theo luật bang Mỹ → bộ lọc chia sẻ trước start (1.6.0 Task 8a, O2) ═══

    private fun usCtx(gpp: String?) = contextWithTcf(gdpr = 0, purposes = "", afAppId = "devKey", gpp = { gpp })

    @Test fun `US opt-out thi setSharingFilterForPartners("all") truoc start, log sharing filter`() = withAppsFlyer { af, log ->
        val helper = AppsflyerHelper()

        helper.startAfterConsent(usCtx(GPP_US_OPT_OUT), ConsentTier.UNKNOWN, gdprApplies = false)

        val order = inOrder(af)
        order.verify(af).setSharingFilterForPartners("all")
        order.verify(af).start(any(Context::class.java), eq("devKey"), any(AppsFlyerRequestListener::class.java))
        verify(af, times(1)).setSharingFilterForPartners("all")
        verify(af).setConsentData(nonGdpr)   // AppsFlyerConsent (DMA/EU) không đổi vì GPP
        log.verify { Log.d(eq(LogUtil.TAG), contains("appsflyer sharing filter: all partners (gpp=optOut)")) }
    }

    @Test fun `GPP khong opt-out hoac khong co GPP thi khong dat bo loc`() = withAppsFlyer { af, _ ->
        AppsflyerHelper().startAfterConsent(usCtx(GPP_US_OK), ConsentTier.GRANTED, gdprApplies = false)
        AppsflyerHelper().startAfterConsent(usCtx(null), ConsentTier.GRANTED, gdprApplies = false)

        verify(af, times(2)).start(any(Context::class.java), eq("devKey"), any(AppsFlyerRequestListener::class.java))
        verify(af, never()).setSharingFilterForPartners("all")
    }

    /** Opt-out bang Mỹ không mở cổng tầng: `DENIED` vẫn không start, không đặt gì (luật `tierAllows` không đổi). */
    @Test fun `DENIED va US opt-out thi van khong start, khong dat bo loc`() = withAppsFlyer { af, _ ->
        AppsflyerHelper().startAfterConsent(usCtx(GPP_US_OPT_OUT), ConsentTier.DENIED, gdprApplies = false)

        verifyNotStarted(af)
        verify(af, never()).setSharingFilterForPartners("all")
    }

    @Test fun `start lai sau khi rut consent thi dat bo loc lai truoc start moi`() = withAppsFlyer { af, _ ->
        val helper = AppsflyerHelper()
        val context = contextWithTcf(gdpr = 1, purposes = "1111111111", afAppId = "devKey", gpp = { GPP_US_OPT_OUT })
        helper.startAfterConsent(context, ConsentTier.GRANTED, gdprApplies = true)
        helper.followTier(context, ConsentTier.DENIED, gdprApplies = true, activity = null)

        helper.followTier(context, ConsentTier.GRANTED, gdprApplies = true, activity = null)

        verify(af, times(2)).setSharingFilterForPartners("all")
        verify(af, times(2)).start(any(Context::class.java), eq("devKey"), any(AppsFlyerRequestListener::class.java))
    }

    // ═══ 1.6.1 Task 5 (T8a m8): gỡ bộ lọc partner do SDK đặt; nhãn in|outside GDPR scope (T8b) ═══

    /** Các lần gọi `setSharingFilterForPartners` (vararg) trên mock, mỗi phần tử là danh sách partner đã truyền. */
    private fun AppsFlyerLib.partnerFilterCalls(): List<List<Any?>> =
        mockingDetails(this).invocations.filter { it.method.name == "setSharingFilterForPartners" }.map { it.arguments.toList() }

    /** KB-2 C1: opt-out lúc start → lọc mọi partner một lần (`"all"`), không gọi API deprecated. */
    @Test fun `GPP opt-out luc start thi loc het partner mot lan qua setSharingFilterForPartners("all"), khong goi API deprecated`() =
        withAppsFlyer { af, _ ->
            AppsflyerHelper().startAfterConsent(usCtx(GPP_US_OPT_OUT), ConsentTier.GRANTED, gdprApplies = false)

            verify(af, times(1)).setSharingFilterForPartners("all")
            assertEquals(listOf(listOf("all")), af.partnerFilterCalls())
            @Suppress("DEPRECATION") verify(af, never()).setSharingFilterForAllPartners()
        }

    /** KB-2 C2: GPP vắng → SDK không gọi bộ lọc nào (không ghi cờ giả, như MAX/Meta). */
    @Test fun `GPP vang thi khong goi bo loc nao`() = withAppsFlyer { af, _ ->
        AppsflyerHelper().startAfterConsent(usCtx(null), ConsentTier.GRANTED, gdprApplies = false)

        verify(af, times(1)).start(any(Context::class.java), eq("devKey"), any(AppsFlyerRequestListener::class.java))
        verify(af, never()).setSharingFilterForPartners("all")
        assertEquals(emptyList<List<Any?>>(), af.partnerFilterCalls())
    }

    /** R1: opt-out → rút → cấp lại với GPP ok trong cùng process: gỡ bộ lọc SDK đã đặt (gọi rỗng) trước start mới. */
    @Test fun `rut roi cap lai voi GPP khong opt-out thi go bo loc SDK da dat truoc start moi`() = withAppsFlyer { af, log ->
        var gpp: String? = GPP_US_OPT_OUT
        val context = contextWithTcf(gdpr = 1, purposes = "1111111111", afAppId = "devKey", gpp = { gpp })
        val helper = AppsflyerHelper()
        helper.startAfterConsent(context, ConsentTier.GRANTED, gdprApplies = true)
        helper.followTier(context, ConsentTier.DENIED, gdprApplies = true, activity = null)
        gpp = GPP_US_OK

        helper.followTier(context, ConsentTier.GRANTED, gdprApplies = true, activity = null)

        assertEquals("đặt \"all\" rồi gỡ đúng một lần (rỗng)", listOf(listOf("all"), emptyList()), af.partnerFilterCalls())
        val order = inOrder(af)
        order.verify(af).setSharingFilterForPartners("all")
        order.verify(af).start(any(Context::class.java), eq("devKey"), any(AppsFlyerRequestListener::class.java))
        order.verify(af).setSharingFilterForPartners()
        order.verify(af).start(any(Context::class.java), eq("devKey"), any(AppsFlyerRequestListener::class.java))
        verify(af, times(1)).setSharingFilterForPartners("all")
        log.verify { Log.d(eq(LogUtil.TAG), contains("appsflyer sharing filter: cleared (gpp=ok)")) }
    }

    /** R2: như R1 nhưng GPP lần hai vắng → không gỡ (không biết người dùng thôi opt-out). */
    @Test fun `rut roi cap lai khi GPP vang thi khong go bo loc`() = withAppsFlyer { af, _ ->
        var gpp: String? = GPP_US_OPT_OUT
        val context = contextWithTcf(gdpr = 1, purposes = "1111111111", afAppId = "devKey", gpp = { gpp })
        val helper = AppsflyerHelper()
        helper.startAfterConsent(context, ConsentTier.GRANTED, gdprApplies = true)
        helper.followTier(context, ConsentTier.DENIED, gdprApplies = true, activity = null)
        gpp = null

        helper.followTier(context, ConsentTier.GRANTED, gdprApplies = true, activity = null)

        verify(af, times(2)).start(any(Context::class.java), eq("devKey"), any(AppsFlyerRequestListener::class.java))
        assertEquals(listOf(listOf("all")), af.partnerFilterCalls())
    }

    /** R1 (Task 6, C + GRANTED): opt-out rồi UMP viết lại chuỗi không còn section US, tầng GRANTED → gỡ; hai vòng chỉ gỡ đúng một lần. */
    @Test fun `rut roi cap lai khi header GPP khong con section US va GRANTED thi go bo loc mot lan`() = withAppsFlyer { af, log ->
        var gpp: String? = GPP_US_OPT_OUT
        val context = contextWithTcf(gdpr = 1, purposes = "1111111111", afAppId = "devKey", gpp = { gpp })
        val helper = AppsflyerHelper()
        helper.startAfterConsent(context, ConsentTier.GRANTED, gdprApplies = true)
        helper.followTier(context, ConsentTier.DENIED, gdprApplies = true, activity = null)
        gpp = "DBABM~CPtest"
        helper.followTier(context, ConsentTier.GRANTED, gdprApplies = true, activity = null)
        helper.followTier(context, ConsentTier.DENIED, gdprApplies = true, activity = null)   // vòng 2: không còn gì để gỡ
        helper.followTier(context, ConsentTier.GRANTED, gdprApplies = true, activity = null)

        assertEquals(listOf(listOf("all"), emptyList()), af.partnerFilterCalls())
        log.verify({ Log.d(eq(LogUtil.TAG), contains("appsflyer sharing filter: cleared (gpp=none, header không còn section US, tier=GRANTED)")) }, times(1))
    }

    /** R2 (ca Task 5 1.6.2): SID = -1 nhưng header còn [7] opt-out → KHÔNG gỡ (đọc pháp lý chặt). */
    @Test fun `rut roi cap lai khi chi SID thanh -1 thi khong go bo loc`() = withAppsFlyer { af, _ ->
        var sid: String? = "7"
        val context = contextWithTcf(gdpr = 1, purposes = "1111111111", afAppId = "devKey", gpp = { GPP_US_OPT_OUT }, gppSid = { sid })
        val helper = AppsflyerHelper()
        helper.startAfterConsent(context, ConsentTier.GRANTED, gdprApplies = true)
        helper.followTier(context, ConsentTier.DENIED, gdprApplies = true, activity = null)
        sid = "-1"

        helper.followTier(context, ConsentTier.GRANTED, gdprApplies = true, activity = null)

        assertEquals(listOf(listOf("all")), af.partnerFilterCalls())
    }

    /** R3 (GRANTED bắt buộc): header hết US nhưng tầng PARTIAL → không gỡ. */
    @Test fun `header GPP khong con section US nhung tang PARTIAL thi khong go bo loc`() = withAppsFlyer { af, _ ->
        var gpp: String? = GPP_US_OPT_OUT
        val context = contextWithTcf(gdpr = 1, purposes = "1111111111", afAppId = "devKey", gpp = { gpp })
        val helper = AppsflyerHelper()
        helper.startAfterConsent(context, ConsentTier.GRANTED, gdprApplies = true)
        helper.followTier(context, ConsentTier.DENIED, gdprApplies = true, activity = null)
        gpp = "DBABM~CPtest"

        helper.followTier(context, ConsentTier.PARTIAL, gdprApplies = true, activity = null)

        verify(af, times(2)).start(any(Context::class.java), eq("devKey"), any(AppsFlyerRequestListener::class.java))
        assertEquals(listOf(listOf("all")), af.partnerFilterCalls())
    }

    /** Đặc tả (review R-F Minor 3, hành vi 1.6.1 giữ nguyên): section US giải mã "không opt-out" thì gỡ cả khi tầng PARTIAL. */
    @Test fun `rut roi cap lai voi GPP khong opt-out o tang PARTIAL thi van go bo loc (1_6_1)`() = withAppsFlyer { af, _ ->
        var gpp: String? = GPP_US_OPT_OUT
        val context = contextWithTcf(gdpr = 1, purposes = "1111111111", afAppId = "devKey", gpp = { gpp })
        val helper = AppsflyerHelper()
        helper.startAfterConsent(context, ConsentTier.GRANTED, gdprApplies = true)
        helper.followTier(context, ConsentTier.DENIED, gdprApplies = true, activity = null)
        gpp = GPP_US_OK

        helper.followTier(context, ConsentTier.PARTIAL, gdprApplies = true, activity = null)

        verify(af, times(2)).start(any(Context::class.java), eq("devKey"), any(AppsFlyerRequestListener::class.java))
        assertEquals(listOf(listOf("all"), emptyList()), af.partnerFilterCalls())
    }

    /** Đặc tả (review R-F Minor 3, ô sai kép): SID = -1, header còn [7] opt-out, tầng PARTIAL → không gỡ. */
    @Test fun `chi SID thanh -1 va tang PARTIAL thi khong go bo loc`() = withAppsFlyer { af, _ ->
        var sid: String? = "7"
        val context = contextWithTcf(gdpr = 1, purposes = "1111111111", afAppId = "devKey", gpp = { GPP_US_OPT_OUT }, gppSid = { sid })
        val helper = AppsflyerHelper()
        helper.startAfterConsent(context, ConsentTier.GRANTED, gdprApplies = true)
        helper.followTier(context, ConsentTier.DENIED, gdprApplies = true, activity = null)
        sid = "-1"

        helper.followTier(context, ConsentTier.PARTIAL, gdprApplies = true, activity = null)

        verify(af, times(2)).start(any(Context::class.java), eq("devKey"), any(AppsFlyerRequestListener::class.java))
        assertEquals(listOf(listOf("all")), af.partnerFilterCalls())
    }

    /** R3: GPP ok từ đầu, SDK chưa từng lọc → không gọi rỗng thừa, kể cả khi start lại. */
    @Test fun `GPP khong opt-out tu dau thi khong goi go bo loc thua`() = withAppsFlyer { af, _ ->
        val context = contextWithTcf(gdpr = 1, purposes = "1111111111", afAppId = "devKey", gpp = { GPP_US_OK })
        val helper = AppsflyerHelper()
        helper.startAfterConsent(context, ConsentTier.GRANTED, gdprApplies = true)
        helper.followTier(context, ConsentTier.DENIED, gdprApplies = true, activity = null)

        helper.followTier(context, ConsentTier.GRANTED, gdprApplies = true, activity = null)

        verify(af, times(2)).start(any(Context::class.java), eq("devKey"), any(AppsFlyerRequestListener::class.java))
        assertEquals(emptyList<List<Any?>>(), af.partnerFilterCalls())
    }

    /** R4 (T8b): lần mở đầu ngoài GDPR tầng còn UNKNOWN là đúng thiết kế; dòng log ghi rõ "outside GDPR scope". */
    @Test fun `start ngoai GDPR khi tang UNKNOWN thi log ghi outside GDPR scope`() = withAppsFlyer { _, log ->
        AppsflyerHelper().startAfterConsent(usCtx(null), ConsentTier.UNKNOWN, gdprApplies = false)

        log.verify {
            Log.d(
                eq(LogUtil.TAG),
                contains("appsflyer started after consent: consent tier=UNKNOWN outside GDPR scope isUserSubjectToGDPR=false"),
            )
        }
    }

    /** R5: `resetForTest` như process mới — cờ bộ lọc cũng về false, start lại với GPP ok không gỡ gì. */
    @Test fun `resetForTest xoa co bo loc, start lai voi GPP ok thi khong go`() = withAppsFlyer { af, _ ->
        val helper = AppsflyerHelper()
        helper.startAfterConsent(usCtx(GPP_US_OPT_OUT), ConsentTier.GRANTED, gdprApplies = false)

        helper.resetForTest()
        helper.startAfterConsent(usCtx(GPP_US_OK), ConsentTier.GRANTED, gdprApplies = false)

        verify(af, times(2)).start(any(Context::class.java), eq("devKey"), any(AppsFlyerRequestListener::class.java))
        assertEquals(listOf(listOf("all")), af.partnerFilterCalls())
    }

    /** Hai vòng rút–cấp lại với GPP ok sau một lần opt-out: gỡ đúng một lần (cờ hạ sau khi gỡ). */
    @Test fun `hai vong rut cap lai voi GPP ok chi go bo loc mot lan`() = withAppsFlyer { af, _ ->
        var gpp: String? = GPP_US_OPT_OUT
        val context = contextWithTcf(gdpr = 1, purposes = "1111111111", afAppId = "devKey", gpp = { gpp })
        val helper = AppsflyerHelper()
        helper.startAfterConsent(context, ConsentTier.GRANTED, gdprApplies = true)
        gpp = GPP_US_OK
        repeat(2) {
            helper.followTier(context, ConsentTier.DENIED, gdprApplies = true, activity = null)
            helper.followTier(context, ConsentTier.GRANTED, gdprApplies = true, activity = null)
        }

        verify(af, times(3)).start(any(Context::class.java), eq("devKey"), any(AppsFlyerRequestListener::class.java))
        assertEquals(listOf(listOf("all"), emptyList()), af.partnerFilterCalls())
    }
}
