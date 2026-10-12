package com.app.plugin.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.app.plugin.SdkKillSwitch
import com.app.plugin.ads.adsfb.AdsFbMy
import com.app.plugin.ads.adsfb.AdsFbPlInters
import com.app.plugin.app.MetaSdkStarter
import com.app.plugin.appsflyer.AppsflyerHelper
import com.app.plugin.brain.HeldExecutor
import com.app.plugin.consent.ConsentManager
import com.app.plugin.consent.ConsentState
import com.app.plugin.consent.ConsentTier
import com.app.plugin.consent.FcmConsent
import com.app.plugin.consent.applicationWithTcf
import com.app.plugin.consent.contextWithTcf
import com.appsflyer.AppsFlyerLib
import com.google.android.gms.tasks.Task
import com.google.android.ump.ConsentInformation
import com.google.android.ump.UserMessagingPlatform
import com.google.firebase.Firebase
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.analytics
import com.google.firebase.messaging.FirebaseMessaging
import com.app.plugin.until.LogUtil
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.contains
import org.mockito.ArgumentMatchers.eq
import org.mockito.MockedStatic
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.`when`
import java.util.concurrent.Executor

/**
 * P1 (DOC/66 mục 5): Meta Audience Network không có trong GVL mà dựa vào Additional Consent
 * (`IABTCF_AddtlConsent`), SDK chưa đọc chuỗi đó. Người `DENIED` trong vùng GDPR (`gdprApplies != false`) thì
 * SDK không khởi tạo Meta AN; placement Meta báo load fail ngay (waterfall đi tiếp) thay vì chờ mãi; tầng thành cho
 * phép sau đó trong cùng process thì khởi tạo Meta lúc đó, một lần.
 *
 * Mạng thật không khởi tạo được trong JVM (MobileAds chạy trên coroutine IO): test thay
 * [AdSdkStartup.networkStarters] bằng bản ghi lại lời gọi.
 */
class AdSdkStartupMetaConsentTest {
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
        val resources = context.resources
        val activity = mock(Activity::class.java)
        `when`(activity.applicationContext).thenReturn(context)
        `when`(activity.resources).thenReturn(resources)
        return activity
    }

    private val deniedInGdpr: Context get() = contextWithTcf(gdpr = 1, purposes = "0000000000")

    private val maxNetworks = if (AdsHelper.isMaxSdkAvailable) ",max" else ""

    // ═══ Khởi tạo mạng theo tầng ═══

    @Test
    fun `DENIED trong GDPR thi khong khoi tao Meta, sdk_health khong co meta, logE`() {
        ConsentState.setTier(ConsentTier.DENIED)
        withSinks(deniedInGdpr) { log ->
            AdSdkStartup.requestAdNetworks(deniedInGdpr)

            assertEquals(listOf("admob") + (if (AdsHelper.isMaxSdkAvailable) listOf("max") else emptyList()), started)
            log.verify { Log.e(LogUtil.TAG, "meta an: not started, consent tier=DENIED in GDPR scope") }
            log.verify { Log.d(eq(LogUtil.TAG), contains("networks=admob$maxNetworks,")) }
        }
    }

    /** `IABTCF_gdprApplies` vắng (không biết) không bao giờ là lý do cấp: như trong GDPR. */
    @Test
    fun `DENIED khi khong biet GDPR thi cung khong khoi tao Meta`() {
        ConsentState.setTier(ConsentTier.DENIED)
        val context = contextWithTcf(gdpr = null, purposes = "")
        withSinks(context) {
            AdSdkStartup.requestAdNetworks(context)

            assertEquals(0, started.count { it == "meta" })
        }
    }

    @Test
    fun `GRANTED thi khoi tao Meta mot lan, sdk_health co meta`() {
        ConsentState.setTier(ConsentTier.GRANTED)
        val context = contextWithTcf(gdpr = 1, purposes = "1111111111")
        withSinks(context) { log ->
            AdSdkStartup.requestAdNetworks(context)
            AdSdkStartup.requestAdNetworks(context)

            assertEquals(1, started.count { it == "meta" })
            log.verify { Log.d(eq(LogUtil.TAG), contains("networks=admob,meta$maxNetworks,")) }
            log.verify({ Log.e(eq(LogUtil.TAG), contains("meta an: not started")) }, never())
        }
    }

    /**
     * FX: cổng Meta dùng `ConsentRules.tierAllows` như AppsFlyer. `DENIED` thắng `IABTCF_gdprApplies = 0` (giá trị 0 có
     * thể còn lại từ trước khi người dùng vào EEA; `DENIED` là câu trả lời thật), nên không khởi tạo. Trước FX: luật P1
     * chỉ xét `DENIED` trong GDPR, `DENIED` với 0 vẫn khởi tạo.
     */
    @Test
    fun `DENIED voi gdprApplies 0 thi khong khoi tao Meta - DENIED thang 0 nhu AppsFlyer`() {
        ConsentState.setTier(ConsentTier.DENIED)
        val context = contextWithTcf(gdpr = 0, purposes = "")
        withSinks(context) { log ->
            AdSdkStartup.requestAdNetworks(context)

            assertEquals(0, started.count { it == "meta" })
            log.verify { Log.e(LogUtil.TAG, "meta an: not started, consent tier=DENIED outside GDPR scope") }
        }
    }

    // ═══ FX: UNKNOWN trong vùng GDPR bị từ chối ở cổng như AppsFlyer (ConsentRules.tierAllows, Điều 13) ═══

    /** UNKNOWN không bao giờ là đồng ý: trong GDPR ([gdpr] = 1) hay không biết (null) thì không khởi tạo Meta AN. */
    private fun assertUnknownRefusesMetaAn(gdpr: Int?) {
        ConsentState.setTier(ConsentTier.UNKNOWN)
        val context = contextWithTcf(gdpr = gdpr, purposes = "")
        val held = LoadEvents()
        withSinks(context) { log ->
            fbInters().load("full_home", held)   // trước cổng: giữ trong hàng chờ của Meta

            AdSdkStartup.requestAdNetworks(context)

            assertEquals("gdpr=$gdpr", listOf("admob") + (if (AdsHelper.isMaxSdkAvailable) listOf("max") else emptyList()), started)
            assertEquals("gdpr=$gdpr: load fb đang giữ báo fail ngay", listOf("failed:$NETWORK_NOT_STARTED"), held.events)
            log.verify { Log.e(LogUtil.TAG, "meta an: not started, consent tier=UNKNOWN in GDPR scope") }
            log.verify { Log.d(eq(LogUtil.TAG), contains("networks=admob$maxNetworks,")) }
        }
    }

    @Test
    fun `UNKNOWN trong GDPR thi khong khoi tao Meta AN, load fb dang giu bao fail`() = assertUnknownRefusesMetaAn(1)

    @Test
    fun `UNKNOWN khi khong biet GDPR thi khong khoi tao Meta AN, load fb dang giu bao fail`() = assertUnknownRefusesMetaAn(null)

    @Test
    fun `UNKNOWN ngoai GDPR thi khoi tao Meta AN`() {
        ConsentState.setTier(ConsentTier.UNKNOWN)
        val context = contextWithTcf(gdpr = 0, purposes = "")
        withSinks(context) {
            AdSdkStartup.requestAdNetworks(context)

            assertEquals(1, started.count { it == "meta" })
        }
    }

    @Test
    fun `UNKNOWN trong GDPR roi GRANTED trong cung process thi khoi tao Meta AN mot lan`() {
        ConsentState.setTier(ConsentTier.UNKNOWN)
        val context = contextWithTcf(gdpr = 1, purposes = "")
        withSinks(context) {
            AdSdkStartup.requestAnalytics(context)   // listener tầng
            AdSdkStartup.requestAdNetworks(context)
            assertEquals(0, started.count { it == "meta" })

            ConsentState.setTier(ConsentTier.GRANTED)
            ConsentState.setTier(ConsentTier.PARTIAL)

            assertEquals(1, started.count { it == "meta" })
        }
    }

    // ═══ Không giữ load mãi ═══

    private class LoadEvents : AdsLoadInnerCb {
        val events = mutableListOf<String>()
        override fun onLoaded(placement: String, originPl: String, adId: String, ecmp: AdsEcmp?, adspl: AdsPlBase?) {
            events += "loaded"
        }

        override fun onLoadFailed(placement: String, originPl: String, adId: String, error: String) {
            events += "failed:$error"
        }
    }

    private fun fbInters(): AdsFbPlInters =
        AdsFbPlInters(mock(Context::class.java), mock(AdsBase::class.java), AdsIDDef.FBFullInters, "full_default").apply {
            setActivity(mock(Activity::class.java))
        }

    @Test
    fun `Meta bi bo qua thi load fb dang giu va load sau do bao fail ngay dung mot lan, khong treo`() {
        ConsentState.setTier(ConsentTier.DENIED)
        val held = LoadEvents()
        val later = LoadEvents()
        withSinks(deniedInGdpr) { log ->
            val pl = fbInters()
            pl.load("full_home", held)   // trước câu trả lời: giữ trong hàng chờ của Meta
            assertEquals(emptyList<String>(), held.events)

            AdSdkStartup.requestAdNetworks(deniedInGdpr)   // Meta bị bỏ qua
            pl.load("full_home", later)   // sau đó: báo fail ngay, không "isloading"

            assertEquals(listOf("failed:$NETWORK_NOT_STARTED"), held.events)
            assertEquals(listOf("failed:$NETWORK_NOT_STARTED"), later.events)
            log.verify({ Log.e(eq(LogUtil.TAG), contains("load failed: network not started")) }, times(2))
        }
    }

    // ═══ Tầng cho phép sau đó trong cùng process ═══

    @Test
    fun `DENIED roi GRANTED trong cung process thi khoi tao Meta luc do, dung mot lan, load fb lai duoc giu`() {
        ConsentState.setTier(ConsentTier.DENIED)
        withSinks(deniedInGdpr) {
            AdSdkStartup.requestAnalytics(deniedInGdpr)   // AppPluginManager.initPlugin: listener tầng
            AdSdkStartup.requestAdNetworks(deniedInGdpr)
            assertEquals(0, started.count { it == "meta" })

            ConsentState.setTier(ConsentTier.GRANTED)   // đồng ý lại qua privacy options
            ConsentState.setTier(ConsentTier.PARTIAL)

            assertEquals(1, started.count { it == "meta" })
            assertEquals(1, started.count { it == "admob" })
            // Meta đang khởi tạo: load mới lại được giữ tới khi mạng sẵn sàng, không báo fail.
            val events = LoadEvents()
            fbInters().load("full_home", events)
            assertTrue(events.events.isEmpty())
        }
    }

    @Test
    fun `tang doi truoc khi mang quang cao khoi tao thi khong khoi tao Meta som`() {
        ConsentState.setTier(ConsentTier.DENIED)
        withSinks(deniedInGdpr) {
            AdSdkStartup.requestAnalytics(deniedInGdpr)   // chưa ai request mạng quảng cáo

            ConsentState.setTier(ConsentTier.GRANTED)

            assertEquals(emptyList<String>(), started)
        }
    }

    // ═══ Review C Minor 5 (F4): start muộn chỉ với tầng cho phép ═══

    /**
     * UNKNOWN (sau `ConsentManager.reset`, hay câu trả lời mà IABTCF không đọc được) không bao giờ là đồng ý (Điều 13):
     * Meta đã bỏ qua vì DENIED thì chưa khởi tạo; tầng cho phép sau đó thì khởi tạo, một lần.
     */
    @Test
    fun `DENIED roi UNKNOWN trong GDPR thi khong khoi tao Meta, GRANTED sau do thi khoi tao mot lan`() {
        ConsentState.setTier(ConsentTier.DENIED)
        withSinks(deniedInGdpr) {
            AdSdkStartup.requestAnalytics(deniedInGdpr)
            AdSdkStartup.requestAdNetworks(deniedInGdpr)

            ConsentState.setTier(ConsentTier.UNKNOWN)
            assertEquals(0, started.count { it == "meta" })

            ConsentState.setTier(ConsentTier.GRANTED)
            ConsentState.setTier(ConsentTier.UNKNOWN)
            ConsentState.setTier(ConsentTier.PARTIAL)
            assertEquals(1, started.count { it == "meta" })
        }
    }

    @Test
    fun `DENIED roi PARTIAL thi khoi tao Meta mot lan`() {
        ConsentState.setTier(ConsentTier.DENIED)
        withSinks(deniedInGdpr) {
            AdSdkStartup.requestAnalytics(deniedInGdpr)
            AdSdkStartup.requestAdNetworks(deniedInGdpr)

            ConsentState.setTier(ConsentTier.PARTIAL)

            assertEquals(1, started.count { it == "meta" })
        }
    }

    // ═══ Review C Minor 4 (F4): quyết định bỏ Meta và đóng hàng chờ là một bước (trong metaLock) ═══

    /**
     * Tầng thành cho phép (trên luồng khác, `setTier` của app) chen vào sau quyết định bỏ Meta. Trước sửa `close()` chạy
     * ngoài `metaLock`, sau dòng logE: lần start muộn mở lại hàng và khởi tạo Meta, rồi `close()` đóng lại → load fb báo
     * `network_not_started` trong khi Meta đang khởi tạo. Chỗ chen được mô phỏng xác định bằng chính dòng logE (trước
     * sửa nó nằm giữa quyết định và `close()`); sau sửa `close()` xong trong khoá trước khi bất kỳ ai start muộn được.
     */
    @Test
    fun `tang cho phep chen sau quyet dinh bo Meta thi hang cho khong bi dong lai sau khi Meta da khoi tao`() {
        ConsentState.setTier(ConsentTier.DENIED)
        withSinks(deniedInGdpr) { log ->
            AdSdkStartup.requestAnalytics(deniedInGdpr)   // listener tầng
            log.`when`<Int> { Log.e(LogUtil.TAG, "meta an: not started, consent tier=DENIED in GDPR scope") }
                .thenAnswer {
                    ConsentState.setTier(ConsentTier.GRANTED)   // đồng ý lại, chen vào đây
                    0
                }

            AdSdkStartup.requestAdNetworks(deniedInGdpr)

            assertEquals(1, started.count { it == "meta" })
            assertFalse("hàng chờ Meta không bị đóng lại sau khi Meta đã khởi tạo", AdsFbMy.instance.readyQueue.isClosed)
            val events = LoadEvents()
            fbInters().load("full_home", events)
            assertTrue("load fb được giữ tới khi Meta sẵn sàng, không fail: ${events.events}", events.events.isEmpty())
        }
    }

    // ═══ F6: Meta SDK (app events) theo cùng luật, nối vào applyTier ═══

    /**
     * `AppPluginBase.initMetaSdkAfterConsent` → task `meta sdk` → `MetaSdkStarter.start`: DENIED trong GDPR thì không
     * khởi tạo `FacebookSdk`/`activateApp`; listener tầng (`applyTier` → `MetaSdkStarter.followTier`) khởi tạo khi tầng
     * cho phép, một lần; UNKNOWN thì chưa.
     */
    @Test
    fun `Meta app events DENIED trong GDPR thi khong khoi tao, tang cho phep sau do qua applyTier thi mot lan`() {
        val inits = mutableListOf<android.app.Application>()
        fakeMetaCore(inits)
        ConsentState.setTier(ConsentTier.DENIED)
        val app = applicationWithTcf(gdpr = 1, purposes = "0000000000")
        withSinks(app) { log ->
            AdSdkStartup.requestAnalytics(app)   // AppPluginManager.initPlugin: listener tầng
            AdSdkStartup.requestMetaSdk(app)
            assertEquals(0, inits.size)
            log.verify { Log.e(LogUtil.TAG, "meta sdk: not started, consent tier=DENIED in GDPR scope") }

            ConsentState.setTier(ConsentTier.UNKNOWN)
            assertEquals(0, inits.size)

            ConsentState.setTier(ConsentTier.GRANTED)
            ConsentState.setTier(ConsentTier.PARTIAL)
            assertEquals(listOf(app), inits)
        }
    }

    /** FX: Meta SDK (app events) cùng cổng `tierAllows`: UNKNOWN trong GDPR/không biết thì không; ngoài GDPR thì có. */
    @Test
    fun `Meta app events UNKNOWN trong GDPR hoac khong biet thi khong khoi tao, ngoai GDPR thi khoi tao`() {
        val inits = mutableListOf<android.app.Application>()
        ConsentState.setTier(ConsentTier.UNKNOWN)
        for (gdpr in listOf(1, null)) {
            fakeMetaCore(inits)
            MetaSdkStarter.start(applicationWithTcf(gdpr = gdpr, purposes = ""))
            assertEquals("gdpr=$gdpr", 0, inits.size)
        }

        fakeMetaCore(inits)
        val outside = applicationWithTcf(gdpr = 0, purposes = "")
        MetaSdkStarter.start(outside)
        assertEquals(listOf(outside), inits)
    }

    /**
     * Meta SDK như process mới, có facebook-core, khởi tạo ghi vào [inits]; việc cờ auto-init (review N1) giữ trên một
     * executor không chạy (test cờ ở `MetaSdkStarterTest`), để không có luồng nền gọi facebook-core thật.
     */
    private fun fakeMetaCore(inits: MutableList<android.app.Application>) {
        MetaSdkStarter.resetForTest()
        MetaSdkStarter.facebookCoreAvailable = { true }
        MetaSdkStarter.initializer = { inits += it }
        MetaSdkStarter.executor = HeldExecutor()
    }

    private companion object {
        /** Nhãn `error` của load bị từ chối vì mạng không khởi tạo (`AdsPlBase`). */
        const val NETWORK_NOT_STARTED = "network_not_started"
    }
}
