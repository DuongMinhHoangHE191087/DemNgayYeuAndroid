package com.app.plugin.ads.core

import org.mockito.ArgumentMatchers.argThat
import com.app.plugin.until.AppUtil
import com.app.plugin.ads.SdkHealth
import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.app.plugin.SdkKillSwitch
import com.app.plugin.ads.AdSdkStartup
import com.app.plugin.consent.ConsentManager
import com.app.plugin.consent.ConsentState
import com.app.plugin.consent.contextWithTcf
import com.app.plugin.logevent.inMemoryPrefs
import com.app.plugin.until.LogUtil
import com.google.android.ump.ConsentInformation
import com.google.android.ump.UserMessagingPlatform
import com.google.firebase.Firebase
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.analytics
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.contains
import org.mockito.ArgumentMatchers.eq
import org.mockito.ArgumentMatchers.startsWith
import org.mockito.MockedStatic
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.mockingDetails
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.`when`
import java.util.Collections

/**
 * 1.9.0 Task 1 (Hợp đồng A, D1/D19/D23): tập đóng, `meta` mặc định, resource và RC reject-whole, RC chỉ thu hẹp,
 * presence một lần mỗi process, persist/restore, `sdk_health partners=`. Trên classpath test: adapter facebook (SDK
 * `implementation`) và pangle (`testImplementation`); mintegral/unity/vungle/applovin vắng (`compileOnly`).
 */
class BiddingPartnersTest {
    private val rcPrefix = "ads bidding: ${BiddingPartners.REMOTE_KEY} rejected"

    @Before fun setUp() {
        BiddingPartners.resetForTest()
        LogUtil.setFlagLog(1)
    }

    @After fun tearDown() = BiddingPartners.resetForTest()

    private fun withLog(block: (MockedStatic<Log>) -> Unit) = mockStatic(Log::class.java).use(block)

    /** Context có resource `ads_bidding_partners` = [resource] (`null` = app không khai) và prefs [prefs]. */
    private fun context(resource: Array<String>?, prefs: SharedPreferences? = null): Context {
        val ctx = contextWithTcf(gdpr = 0, purposes = "1111111111")
        if (resource != null) {
            `when`(ctx.resources.getIdentifier("ads_bidding_partners", "array", "com.example.app")).thenReturn(RES_ID)
            `when`(ctx.resources.getStringArray(RES_ID)).thenReturn(resource)
        }
        if (prefs != null) `when`(ctx.getSharedPreferences(anyString(), anyInt())).thenReturn(prefs)
        return ctx
    }

    private fun rc(vararg partners: String) =
        """{"schema_version":1,"partners":[${partners.joinToString(",") { "\"$it\"" }}]}"""

    // B1
    @Test
    fun `mac dinh la meta khi app khong khai resource va khong co RC`() = withLog { log ->
        assertEquals(setOf("meta"), BiddingPartners.parseResource(null))
        assertNull(BiddingPartners.parseRemote(""))
        assertEquals(listOf("meta"), BiddingPartners.check(context(null)))
        log.verify({ Log.e(anyString(), anyString()) }, never())
    }

    // B2
    @Test
    fun `RC chi thu hep resource, khong bao gio noi rong`() {
        assertEquals(setOf("meta", "pangle"), BiddingPartners.parseRemote(rc("meta", "pangle")))
        assertEquals(setOf("meta"), BiddingPartners.effective(setOf("meta", "pangle"), setOf("meta")))
        assertEquals(setOf("meta"), BiddingPartners.effective(setOf("meta"), setOf("meta", "pangle")))
        assertEquals(emptySet<String>(), BiddingPartners.effective(setOf("meta"), setOf("pangle")))
        assertEquals(setOf("meta", "pangle"), BiddingPartners.effective(setOf("meta", "pangle"), null))
        BiddingPartners.applyRemote(null, rc("pangle"))
        assertEquals(listOf("pangle"), BiddingPartners.check(context(arrayOf("meta", "pangle"))))
    }

    // B3
    @Test
    fun `RC co ten la thi bo ca gia tri, giu resource, mot logE`() = withLog { log ->
        BiddingPartners.applyRemote(null, rc("meta", "ironsource"))
        log.verify({ Log.e(eq(LogUtil.TAG), startsWith(rcPrefix)) }, times(1))
        assertEquals(listOf("meta", "pangle"), BiddingPartners.check(context(arrayOf("meta", "pangle"))))
    }

    // B3b
    @Test
    fun `resource co ten la, trung hoac rong thi bo ca resource ve meta, moi lan mot logE`() = withLog { log ->
        val bad = listOf(arrayOf("meta", "foo"), arrayOf("pangle", "pangle"), arrayOf("meta", ""), arrayOf())
        for (names in bad) assertEquals(names.toList().toString(), setOf("meta"), BiddingPartners.parseResource(names))
        log.verify({ Log.e(eq(LogUtil.TAG), startsWith("ads bidding: resource ads_bidding_partners ignored")) }, times(4))
        assertEquals(listOf("meta"), BiddingPartners.check(context(arrayOf("meta", "Pangle"))))
    }

    // B4
    @Test
    fun `RC co ten trung thi bo ca gia tri`() = withLog { log ->
        assertNull(BiddingPartners.parseRemote(rc("meta", "pangle", "meta")))
        log.verify({ Log.e(eq(LogUtil.TAG), startsWith(rcPrefix)) }, times(1))
    }

    // B5
    @Test
    fun `RC schema_version khac 1 thi bo ca gia tri`() = withLog { log ->
        val bad = listOf(
            """{"schema_version":2,"partners":["meta"]}""",
            """{"schema_version":"1","partners":["meta"]}""",
            """{"partners":["meta"]}""",
        )
        for (raw in bad) assertNull(raw, BiddingPartners.parseRemote(raw))
        log.verify({ Log.e(eq(LogUtil.TAG), startsWith(rcPrefix)) }, times(3))
    }

    // B6
    @Test
    fun `RC co khoa la hoac sai dang thi bo ca gia tri`() = withLog { log ->
        val bad = listOf(
            """{"schema_version":1,"partners":["meta"],"by_tier":{}}""",
            """["meta"]""",
            """{"schema_version":1,"partners":[]}""",
            """{"schema_version":1,"partners":"meta"}""",
            """{"schema_version":1,"partners":[1]}""",
            "{not json",
        )
        for (raw in bad) assertNull(raw, BiddingPartners.parseRemote(raw))
        log.verify({ Log.e(eq(LogUtil.TAG), startsWith(rcPrefix)) }, times(bad.size))
    }

    // B7
    @Test
    fun `adapter vang thi dung mot dong log moi process, du hoi nhieu lan`() = withLog { log ->
        val ctx = context(arrayOf("meta", "unity", "pangle"))
        repeat(3) { assertEquals(listOf("meta", "pangle"), BiddingPartners.check(ctx)) }
        log.verify({ Log.e(LogUtil.TAG, "ads bidding: unity enabled but adapter absent, skipped") }, times(1))
        log.verify({ Log.e(eq(LogUtil.TAG), contains("adapter absent")) }, times(1))
        log.verify({ Log.d(LogUtil.TAG, "ads bidding: pangle present") }, times(1))
    }

    // B8
    @Test
    fun `RC hop le duoc luu bang apply va lan mo ke doc lai`() {
        val store = Collections.synchronizedMap(mutableMapOf<String, Any?>())
        val prefs = inMemoryPrefs(store)
        BiddingPartners.applyRemote(context(null, prefs), rc("meta"))
        assertEquals(rc("meta"), store[BiddingPartners.PREF_KEY])
        val editorCalls = mockingDetails(prefs.edit()).invocations.map { it.method.name }
        assertEquals("apply(), không commit()", listOf(1, 0), listOf(editorCalls.count { it == "apply" }, editorCalls.count { it == "commit" }))

        BiddingPartners.resetForTest()   // process mới
        assertEquals(setOf("meta"), BiddingPartners.currentRemote(context(null, prefs)))
        assertEquals(listOf("meta"), BiddingPartners.check(context(arrayOf("meta", "pangle"), prefs)))
    }

    // B12
    @Test
    fun `sdk_health networks mang partners co adapter theo thu tu tap dong`() {
        ConsentState.resetForTest()
        AdSdkStartup.resetForTest()
        SdkKillSwitch.current = SdkKillSwitch.NONE
        AdSdkStartup.networkStarters = AdSdkStartup.NetworkStarters(admob = {}, meta = {}, max = {})
        val ctx = context(arrayOf("pangle", "vungle", "meta"))
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
                    withLog { log ->
                        AdSdkStartup.requestAdNetworks(ctx)
                        log.verify({ Log.d(eq(LogUtil.TAG), contains(",partners=meta,pangle,")) }, times(1))
                        log.verify({ Log.d(eq(LogUtil.TAG), contains("networks=admob,")) }, times(1))
                    }
                }
            }
        } finally {
            ConsentManager.reset(activity)
            AdSdkStartup.resetForTest()
            ConsentState.resetForTest()
        }
    }

    private companion object {
        const val RES_ID = 0x7f030001
    }

    // S2 (R-T1 m4): nhãn mạng của adapter ngoài tập 13 mẫu giữ nguyên chữ gốc (không còn hạ chữ như 1.8.0); chartboost/fyber nhận đúng
    @Test
    fun `S2 chartboost va fyber ra dung nhan, nhan la giu chu goc`() {
        val expected = linkedMapOf(
            "com.google.ads.mediation.chartboost.ChartboostMediationAdapter" to "chartboost",
            "com.google.ads.mediation.fyber.FyberMediationAdapter" to "fyber",
            "DT Exchange" to "fyber",
            "com.google.ads.mediation.mytarget.MyTargetMediationAdapter" to "mytarget",
            "com.google.ads.mediation.inmobi.InMobiMediationAdapter" to "com.google.ads.mediation.inmobi.InMobiMediationAdapter",
        )
        for ((cls, label) in expected) assertEquals(cls, label, AppUtil.extractAdNetwork(cls))
    }

    // S3 (R-T1 m6): RC bị từ chối thì bản lưu là chuỗi rỗng (không lưu nguyên, không logE lại mỗi lần mở app)
    @Test
    fun `S3 RC bi tu choi thi gia tri luu la rong`() = withLog { _ ->
        val store = Collections.synchronizedMap(mutableMapOf<String, Any?>())
        val prefs = inMemoryPrefs(store)
        BiddingPartners.applyRemote(context(null, prefs), rc("meta", "ironsource"))
        assertEquals("", store[BiddingPartners.PREF_KEY])
        BiddingPartners.applyRemote(context(null, prefs), rc("pangle"))
        assertEquals(rc("pangle"), store[BiddingPartners.PREF_KEY])
        BiddingPartners.applyRemote(context(null, prefs), "")
        assertEquals("giá trị gỡ khoá cũng lưu", "", store[BiddingPartners.PREF_KEY])
    }

    // S4 (R-T1 m5): logE từ chối không lặp cả mảng partners
    @Test
    fun `S4 logE tu choi RC va resource khong qua 512 ky tu`() = withLog { log ->
        val huge = Array(10_000) { "x$it" }
        assertNull(BiddingPartners.parseRemote(rc(*huge)))
        assertEquals(setOf("meta"), BiddingPartners.parseResource(huge))
        log.verify({ Log.e(eq(LogUtil.TAG), argThat { it.length <= 512 && it.startsWith(rcPrefix) && it.contains("rejected") }) }, times(1))
        log.verify({ Log.e(eq(LogUtil.TAG), argThat { it.length <= 512 && it.startsWith("ads bidding: resource ads_bidding_partners ignored") }) }, times(1))
    }

    // S4b (R-S1 m1, M22): cắt 512 ký tự không được làm mất số lượng bị từ chối lẫn tên sai đầu tiên
    @Test
    fun `S4b logE tu choi giu ten sai dau tien va so luong trong 512 ky tu`() = withLog { log ->
        val bad = arrayOf("meta", "pangle") + Array(10_000) { "x$it" }   // 10000 sai trong 10002 tên: số lượng ≠ tổng
        assertEquals(setOf("meta"), BiddingPartners.parseResource(bad))
        log.verify({ Log.e(eq(LogUtil.TAG), argThat { it.length <= 512 && it.contains("'x0'") && it.contains("10000 unknown") }) }, times(1))
        // RC: thông điệp cũ in cả mảng rồi cắt 200 ký tự, mất số lượng sai.
        val late = arrayOf("meta", "pangle") + Array(5_000) { "zz$it" }
        assertNull(BiddingPartners.parseRemote(rc(*late)))
        log.verify({ Log.e(eq(LogUtil.TAG), argThat { it.length <= 512 && it.startsWith(rcPrefix) && it.contains("'zz0'") && it.contains("5000 unknown") }) }, times(1))
    }

    // S8 (D50): `sdk_health partners=` (kể cả rỗng) và một dòng presence cho cả tập đóng, kể cả partner không bật
    @Test
    fun `S8 sdk_health partners va dong presence tap dong`() = withLog { log ->
        assertEquals("admob,max,partners=meta,pangle", SdkHealth.withPartners("admob,max", listOf("meta", "pangle")))
        assertEquals("admob,partners=", SdkHealth.withPartners("admob", emptyList()))
        assertEquals(listOf("meta"), BiddingPartners.check(context(null)))
        val line = "ads adapters on classpath: meta=present pangle=present mintegral=absent unity=absent vungle=absent applovin=absent"
        log.verify({ Log.d(LogUtil.TAG, line) }, times(1))
        BiddingPartners.check(context(null))
        log.verify({ Log.d(eq(LogUtil.TAG), startsWith("ads adapters on classpath: ")) }, times(1))
    }

    // S1 (R-T1 m1): presence qua hàm nhỏ; fixture có khởi tạo tĩnh đặt cờ, `initialize=true` làm cờ bật
    @Test
    fun `S1 adapterPresent dung lop co that va khong chay khoi tao tinh`() {
        val loader = BiddingPartnersTest::class.java.classLoader
        assertTrue(BiddingPartners.adapterPresent("com.app.plugin.ads.core.BiddingInitProbe", loader))
        assertFalse("không được khởi tạo lớp adapter", BiddingProbeFlag.initialized)
        assertFalse(BiddingPartners.adapterPresent("com.app.plugin.ads.core.BiddingNoSuchAdapter", loader))
    }
}

/** Cờ tách khỏi [BiddingInitProbe] để đọc nó không khởi tạo lớp đó. */
internal object BiddingProbeFlag {
    @Volatile var initialized = false
}

/** Fixture S1: khởi tạo tĩnh (`<clinit>`) bật cờ. */
internal object BiddingInitProbe {
    init {
        BiddingProbeFlag.initialized = true
    }
}
