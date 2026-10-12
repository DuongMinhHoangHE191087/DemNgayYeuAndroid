package com.app.plugin.firbase

import android.content.Context
import android.content.res.Resources
import android.os.Bundle
import android.util.Log
import com.app.plugin.ads.AdRevenueRouting
import com.app.plugin.ads.AdsCfOverrides
import com.app.plugin.appsflyer.AppsflyerHelper
import com.app.plugin.consent.ConsentTier
import com.app.plugin.consent.contextWithTcf
import com.app.plugin.logevent.EventNames
import com.app.plugin.logevent.ServerEventCapture
import com.app.plugin.until.LogUtil
import com.appsflyer.AFAdRevenueData
import com.appsflyer.AppsFlyerLib
import com.appsflyer.MediationNetwork
import com.google.android.gms.ads.AdValue
import com.google.firebase.Firebase
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.analytics
import com.google.gson.JsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.contains
import org.mockito.ArgumentMatchers.eq
import org.mockito.MockedStatic
import org.mockito.Mockito.atLeast
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockConstruction
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.mockingDetails
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

/**
 * Doanh thu v2 + khoá đối soát (plan 1.7.0 Task 3, R1–R5; R6 = đường MAX, review R-3 I1): một paid callback sinh **một** `imp_id`, dùng chung cho
 * Firebase `ad_impression` (param `imp_id`), dòng `rev af:` của AppsFlyer, `ad_show` và `ad_revenue` lên server;
 * `value_micros` là Long nguyên bản của mạng, `precision_type` đi thẳng từ `AdValue`.
 *
 * Harness như `FirHelperAdRevenueRoutingTest` (Firebase/AppsFlyer/`Log` giả trên luồng test, AppsFlyer start với tầng
 * GRANTED) + [ServerEventCapture] (event server dựng trên IO) + `Bundle` giả để đọc param Firebase.
 */
class PaidEventTest {
    private val uuid = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")

    @After fun tearDown() {
        AdRevenueRouting.admobLinkedToFirebase = false
        PlacementPaidEvent.resetForTest()
        AdsCfOverrides.resetForTest()
        AppsflyerHelper.instance.resetForTest()
    }

    private class Sinks(
        val server: ServerEventCapture,
        val firebase: FirebaseAnalytics,
        val appsFlyer: AppsFlyerLib,
        val log: MockedStatic<Log>,
    )

    private fun paid(action: () -> Unit, check: Sinks.() -> Unit) {
        val firebase = mock(FirebaseAnalytics::class.java)
        val appsFlyer = mock(AppsFlyerLib::class.java)
        ServerEventCapture().use { server ->
            mockStatic(Class.forName("com.google.firebase.analytics.AnalyticsKt")).use { analyticsKt ->
                analyticsKt.`when`<FirebaseAnalytics> { Firebase.analytics }.thenReturn(firebase)
                mockStatic(AppsFlyerLib::class.java).use { af ->
                    af.`when`<AppsFlyerLib> { AppsFlyerLib.getInstance() }.thenReturn(appsFlyer)
                    mockStatic(Log::class.java).use { log ->
                        mockConstruction(Bundle::class.java).use {
                            AppsflyerHelper.instance.startAfterConsent(
                                contextWithTcf(gdpr = 1, purposes = "1111111111", afAppId = "devKey"),
                                ConsentTier.GRANTED,
                                gdprApplies = true,
                            )
                            action()
                            Sinks(server, firebase, appsFlyer, log).check()
                        }
                    }
                }
            }
        }
    }

    private fun adValue(micros: Long, currency: String, precision: Int): AdValue = mock(AdValue::class.java).also {
        `when`(it.valueMicros).thenReturn(micros)
        `when`(it.currencyCode).thenReturn(currency)
        `when`(it.precisionType).thenReturn(precision)
    }

    private fun admob(v: AdValue) = PaidEvent.admob("full_home", "full_home", "interstitial", UNIT, ADMOB_NET, v)

    private fun JsonObject.imp(): String = get("imp_id")?.asString ?: "<none>"

    /** Giá trị param chuỗi [key] của mỗi Firebase `ad_impression` (Bundle giả ghi mọi `putString`). */
    private fun Sinks.firebaseParam(key: String): List<String> = mockingDetails(firebase).invocations
        .filter { it.method.name == "logEvent" && it.arguments[0] == FirebaseAnalytics.Event.AD_IMPRESSION }
        .flatMap { inv ->
            mockingDetails(inv.arguments[1]).invocations
                .filter { it.method.name == "putString" && it.arguments[0] == key }
                .map { it.arguments[1] as String }
        }

    private fun Sinks.firebaseImpIds(): List<String> = firebaseParam("imp_id")

    private fun Sinks.firebaseImpressions(): Int = mockingDetails(firebase).invocations
        .count { it.method.name == "logEvent" && it.arguments[0] == FirebaseAnalytics.Event.AD_IMPRESSION }

    /** Các dòng `Log.i("AppPlugin", "rev af: …")` đã ghi trên luồng test. */
    private fun Sinks.revAfLines(): List<String> {
        val captor = ArgumentCaptor.forClass(String::class.java)
        log.verify({ Log.i(eq("AppPlugin"), captor.capture()) }, atLeast(0))
        return captor.allValues.filter { it.startsWith("rev af:") }
    }

    @Test fun `R1 admob paid event - ad_revenue co value_micros bang AdValue, precision_type, imp_id`() {
        // 1001 micros: đi qua double (1001 / 1e6 * 1e6 = 1000.99…) rồi cắt thì thành 1000 — M1.
        paid({ admob(adValue(1_001L, "EUR", 3)) }) {
            val rev = server.await("ad_revenue", 1).single()
            assertEquals("value_micros giữ nguyên AdValue (Long): $rev", "1001", rev.get("value_micros").asString)
            assertEquals("EUR", rev.get("currency").asString)
            assertEquals("precision_type từ AdValue (PRECISE = 3): $rev", 3, rev.get("precision_type").asInt)
            assertTrue("imp_id là UUID: $rev", uuid.matches(rev.imp()))
            assertEquals("admob", rev.get("mediation").asString)
            assertEquals("admob", rev.get("ad_source").asString)
            assertEquals(UNIT, rev.get("ad_unit").asString)
            assertEquals("full_home", rev.get("placement").asString)
            assertEquals("interstitial", rev.get("ad_format").asString)
        }
    }

    @Test fun `R2 ad_show va ad_revenue cung imp_id, moi luot mot imp_id moi`() {
        paid({ admob(adValue(2_500L, "USD", 1)); admob(adValue(3_700L, "USD", 1)) }) {
            val shows = server.await("ad_show", 2)
            val revs = server.await("ad_revenue", 2)
            assertEquals("một ad_show mỗi paid callback: $shows", 2, shows.size)
            val showIds = shows.map { it.imp() }.toSet()
            val revIds = revs.map { it.imp() }.toSet()
            assertTrue("imp_id là UUID: show=$showIds rev=$revIds", (showIds + revIds).all(uuid::matches))
            assertEquals("mỗi lượt một imp_id mới: $revIds", 2, revIds.size)
            assertEquals("ad_show và ad_revenue cùng imp_id", revIds, showIds)
        }
    }

    @Test fun `R3 Meta direct - value_micros 0, currency rong khong bi doi thanh USD`() {
        paid({ PaidEvent.metaDirect("full_home", "full_home", "interstitial", "fb-unit", "facebook") }) {
            val rev = server.await("ad_revenue", 1).single()
            assertEquals("0", rev.get("value_micros").asString)
            assertEquals("currency rỗng, không đổi thành USD: $rev", "", rev.get("currency").asString)
            assertEquals(-1, rev.get("precision_type").asInt)
            assertEquals("meta_direct", rev.get("mediation").asString)
            assertTrue("imp_id là UUID: $rev", uuid.matches(rev.imp()))
            assertEquals("ad_show cùng imp_id", listOf(rev.imp()), server.await("ad_show", 1).map { it.imp() })
            // Meta direct không có doanh thu thật: không gửi AppsFlyer, không ghi Firebase ad_impression (như v1).
            verify(appsFlyer, never()).logAdRevenue(any(), any())
            assertEquals(0, firebaseImpressions())
        }
    }

    @Test fun `R4 AppsFlyer nhan cung imp_id (dong rev af)`() {
        paid({ admob(adValue(4_200L, "USD", 1)) }) {
            val imp = server.await("ad_revenue", 1).single().imp()
            verify(appsFlyer, times(1)).logAdRevenue(any(), any())
            assertEquals(listOf("rev af: imp=$imp started=1"), revAfLines())
        }
    }

    @Test fun `R5 Firebase ad_impression co param imp_id khi AdRevenueRouting cho phep`() {
        AdRevenueRouting.admobLinkedToFirebase = false
        paid({ admob(adValue(5_300L, "USD", 1)) }) {
            val imp = server.await("ad_revenue", 1).single().imp()
            assertEquals("Firebase ad_impression mang imp_id của ad_revenue", listOf(imp), firebaseImpIds())
        }
    }

    @Test fun `R6 MAX paid event - value_micros lam tron khong cat, mediation max, AppsFlyer nhan APPLOVIN_MAX cung imp_id`() {
        // R-3 I1: 0.001001 USD · 1e6 = 1000.99… trong double — cắt thì thành 1000, làm tròn mới giữ 1001.
        paid({ PaidEvent.max("full_home", "full_home", "interstitial", "max-unit", "AppLovin", 0.001001, "USD") }) {
            val rev = server.await("ad_revenue", 1).single()
            assertEquals("value_micros làm tròn, không cắt: $rev", "1001", rev.get("value_micros").asString)
            assertEquals("max", rev.get("mediation").asString)
            assertEquals("applovin", rev.get("ad_source").asString)
            assertEquals("USD", rev.get("currency").asString)
            assertEquals(-1, rev.get("precision_type").asInt)
            val imp = rev.imp()
            assertTrue("imp_id là UUID: $rev", uuid.matches(imp))
            assertEquals("ad_show cùng imp_id", listOf(imp), server.await("ad_show", 1).map { it.imp() })
            assertEquals(listOf("rev af: imp=$imp started=1"), revAfLines())
            val af = ArgumentCaptor.forClass(AFAdRevenueData::class.java)
            verify(appsFlyer, times(1)).logAdRevenue(af.capture(), any())
            assertEquals("AppsFlyer: doanh thu MAX không bị ghi là AdMob", MediationNetwork.APPLOVIN_MAX, af.value.mediationNetwork)
            assertEquals("Firebase ad_impression mang imp_id", listOf(imp), firebaseImpIds())
            assertEquals(listOf("applovin"), firebaseParam(FirebaseAnalytics.Param.AD_PLATFORM))
        }
    }

    @Test fun `R7 slot dung chung - show tu full_home qua slot full_default, ad_show va ad_revenue mang full_home, mot imp_id cho 4 dich`() {
        // T3b (R-FBE I1): placement = placement gọi show (như `ad_show_attempt`), không phải slot mạng (`originPl`).
        paid({ PaidEvent.admob("full_home", "full_default", "interstitial", UNIT, ADMOB_NET, adValue(6_100L, "USD", 1)) }) {
            val rev = server.await("ad_revenue", 1).single()
            val show = server.await("ad_show", 1).single()
            assertEquals("ad_revenue.placement: $rev", "full_home", rev.get("placement").asString)
            assertEquals("ad_show.placement: $show", "full_home", show.get("placement").asString)
            val imp = rev.imp()
            assertTrue("imp_id là UUID: $rev", uuid.matches(imp))
            assertEquals("ad_show cùng imp_id", imp, show.imp())
            assertEquals("Firebase ad_impression cùng imp_id", listOf(imp), firebaseImpIds())
            assertEquals("AppsFlyer cùng imp_id", listOf("rev af: imp=$imp started=1"), revAfLines())
        }
    }

    @Test fun `R8 Full2 tren slot full_default - ad_show va ad_revenue mang full_2_home`() {
        paid({ PaidEvent.max("full_2_home", "full_default", "interstitial", "max-unit", "AppLovin", 0.0042, "USD") }) {
            val rev = server.await("ad_revenue", 1).single()
            val show = server.await("ad_show", 1).single()
            assertEquals("ad_revenue.placement Full2: $rev", "full_2_home", rev.get("placement").asString)
            assertEquals("ad_show.placement Full2: $show", "full_2_home", show.get("placement").asString)
            assertEquals(rev.imp(), show.imp())
        }
    }

    @Test fun `R9 chua co placement goi show thi placement lui ve slot`() {
        paid({ PaidEvent.metaDirect("", "bn_default", "banner", "fb-unit", "facebook") }) {
            val rev = server.await("ad_revenue", 1).single()
            assertEquals("showPl rỗng → slot: $rev", "bn_default", rev.get("placement").asString)
            assertEquals("bn_default", server.await("ad_show", 1).single().get("placement").asString)
        }
    }

    /** Khoá `putString` + `putDouble` của mỗi Firebase `ad_impression`. */
    private fun Sinks.firebaseKeys(): Set<String> = mockingDetails(firebase).invocations
        .filter { it.method.name == "logEvent" && it.arguments[0] == FirebaseAnalytics.Event.AD_IMPRESSION }
        .flatMap { inv ->
            mockingDetails(inv.arguments[1]).invocations
                .filter { it.method.name == "putString" || it.method.name == "putDouble" }
                .map { it.arguments[0] as String }
        }.toSet()

    @Test fun `R10 admob - ad_impression mang ad_placement bang ad_revenue placement, 7 khoa cu giu`() {
        // 2.5.1 PLACEMENT (Hợp đồng A): cùng biến `placement` của server, thêm đúng một khoá vào ad_impression.
        paid({ admob(adValue(7_100L, "USD", 1)) }) {
            val rev = server.await("ad_revenue", 1).single()
            assertEquals(listOf("full_home"), firebaseParam("ad_placement"))
            assertEquals("bằng ad_revenue.placement: $rev", listOf(rev.get("placement").asString), firebaseParam("ad_placement"))
            assertEquals(
                setOf(
                    FirebaseAnalytics.Param.AD_PLATFORM, FirebaseAnalytics.Param.AD_UNIT_NAME, FirebaseAnalytics.Param.AD_FORMAT,
                    FirebaseAnalytics.Param.AD_SOURCE, FirebaseAnalytics.Param.VALUE, FirebaseAnalytics.Param.CURRENCY, "imp_id",
                    "ad_placement",
                ),
                firebaseKeys(),
            )
        }
    }

    @Test fun `R11 Full2 tren slot full_default - ad_placement la full_2_home, khong phai slot`() {
        paid({ PaidEvent.max("full_2_home", "full_default", "interstitial", "max-unit", "AppLovin", 0.0042, "USD") }) {
            assertEquals(listOf("full_2_home"), firebaseParam("ad_placement"))
        }
    }

    @Test fun `R12 chua co placement goi show - ad_placement lui ve slot`() {
        paid({ PaidEvent.admob("", "nt_default", "native", UNIT, ADMOB_NET, adValue(800L, "USD", 1)) }) {
            assertEquals(listOf("nt_default"), firebaseParam("ad_placement"))
        }
    }

    @Test fun `R13 placement dai 120 ky tu - ad_placement cat con 100 ky tu dau`() {
        val long = "nt_" + "x".repeat(117)
        paid({ PaidEvent.admob(long, long, "native", UNIT, ADMOB_NET, adValue(900L, "USD", 1)) }) {
            assertEquals(listOf(long.take(100)), firebaseParam("ad_placement"))
        }
    }

    /** Số lần Firebase `logEvent([name])`. */
    private fun Sinks.events(name: String): Int = mockingDetails(firebase).invocations
        .count { it.method.name == "logEvent" && it.arguments[0] == name }

    /** Khoá → giá trị `putString`/`putDouble` của mọi Firebase `logEvent([name])`. */
    private fun Sinks.eventPuts(name: String): Map<String, Any?> = mockingDetails(firebase).invocations
        .filter { it.method.name == "logEvent" && it.arguments[0] == name }
        .flatMap { inv ->
            mockingDetails(inv.arguments[1]).invocations
                .filter { it.method.name == "putString" || it.method.name == "putDouble" }
                .map { it.arguments[0] as String to it.arguments[1] }
        }.toMap()

    @Test fun `R14 AdMob da lien ket va bat ads_cf_placement_event - 1 ad_placement_impression, 0 ad_impression, khong co khoa value`() {
        // 2.5.1 PLEVT (Hợp đồng A): tiền ở `ad_value`, không bao giờ `value` (GA4 không cộng doanh thu, không đếm đôi).
        AdRevenueRouting.admobLinkedToFirebase = true
        PlacementPaidEvent.enabled = true
        // r1 (R-L' M-1): Full2 trên slot chung — showPl ≠ originPl, khoá "placement đang hiện, không phải slot".
        paid({ PaidEvent.admob("full_2_home", "full_default", "interstitial", UNIT, ADMOB_NET, adValue(7_200L, "USD", 1)) }) {
            val rev = server.await("ad_revenue", 1).single()
            assertEquals("số ad_placement_impression", 1, events(EventNames.AD_PLACEMENT_IMPRESSION))
            assertEquals("ad_placement_impression", EventNames.AD_PLACEMENT_IMPRESSION)
            assertEquals("liên kết ⇒ 0 ad_impression của SDK", 0, firebaseImpressions())
            val p = eventPuts(EventNames.AD_PLACEMENT_IMPRESSION)
            assertEquals("full_2_home", p["ad_placement"])
            assertEquals("imp_id = ad_revenue.imp_id", rev.imp(), p["imp_id"])
            assertFalse("khoá value không có: $p", FirebaseAnalytics.Param.VALUE in p)
            assertEquals("tiền ở ad_value: $p", 0.0072, p["ad_value"] as? Double ?: Double.NaN, 0.0)
            assertEquals(
                setOf(
                    "ad_placement", FirebaseAnalytics.Param.AD_UNIT_NAME, FirebaseAnalytics.Param.AD_FORMAT, FirebaseAnalytics.Param.AD_SOURCE,
                    FirebaseAnalytics.Param.AD_PLATFORM, "imp_id", "ad_value", FirebaseAnalytics.Param.CURRENCY,
                ),
                p.keys,
            )
            assertEquals("USD", p[FirebaseAnalytics.Param.CURRENCY])
            assertEquals("admob", p[FirebaseAnalytics.Param.AD_PLATFORM])
        }
    }

    /** Đặc tả (xanh trên mã cũ/stub Đ1): đỏ của nó là đột biến M3 (bỏ kiểm `enabled`). */
    @Test fun `R15 da lien ket, ads_cf_placement_event tat (mac dinh) - 0 ad_impression, 0 ad_placement_impression`() {
        AdRevenueRouting.admobLinkedToFirebase = true
        paid({ admob(adValue(7_300L, "USD", 1)) }) {
            server.await("ad_revenue", 1)
            assertEquals(0, firebaseImpressions())
            assertEquals(0, events(EventNames.AD_PLACEMENT_IMPRESSION))
        }
    }

    /** Đặc tả (xanh trên mã cũ/stub Đ1): đỏ của nó là đột biến M1 (ghi cả khi chưa liên kết). */
    @Test fun `R16 chua lien ket + bat - ad_impression co ad_placement, khong ad_placement_impression, MAX khong bao gio ra event moi`() {
        PlacementPaidEvent.enabled = true
        paid({ admob(adValue(7_400L, "USD", 1)) }) {
            server.await("ad_revenue", 1)
            assertEquals(listOf("full_home"), firebaseParam("ad_placement"))
            assertEquals(0, events(EventNames.AD_PLACEMENT_IMPRESSION))
        }
        AdRevenueRouting.admobLinkedToFirebase = true
        paid({ PaidEvent.max("full_2_home", "full_default", "interstitial", "max-unit", "AppLovin", 0.0042, "USD") }) {
            server.await("ad_revenue", 1)
            assertEquals("MAX: AdRevenueRouting luôn cho ghi", listOf("full_2_home"), firebaseParam("ad_placement"))
            assertEquals(0, events(EventNames.AD_PLACEMENT_IMPRESSION))
        }
    }

    private fun resContext(resId: Int, value: () -> Int): Context {
        val res = mock(Resources::class.java)
        val context = mock(Context::class.java)
        `when`(context.packageName).thenReturn(PKG)
        `when`(context.resources).thenReturn(res)
        `when`(res.getIdentifier("ads_cf_placement_event", "integer", PKG)).thenReturn(resId)
        `when`(res.getInteger(resId)).thenAnswer { value() }
        return context
    }

    @Test fun `R17 read - resource bang 1 la bat, vang hay khac 1 la tat, doc loi la tat va 1 Log e`() {
        assertTrue("resource ads_cf_placement_event = 1 ⇒ bật", PlacementPaidEvent.read(resContext(RES_ID) { 1 }))
        assertFalse("vắng ⇒ tắt", PlacementPaidEvent.read(resContext(0) { 1 }))
        assertFalse("khác 1 ⇒ tắt", PlacementPaidEvent.read(resContext(RES_ID) { 2 }))
        mockStatic(Log::class.java).use { log ->
            val broken = mock(Context::class.java)
            val res = mock(Resources::class.java)
            `when`(broken.packageName).thenReturn(PKG)
            `when`(broken.resources).thenReturn(res)
            `when`(res.getIdentifier(any(), any(), any())).thenThrow(Resources.NotFoundException("boom"))
            assertFalse("đọc lỗi ⇒ tắt", PlacementPaidEvent.read(broken))
            log.verify({ Log.e(eq(LogUtil.TAG), contains("ads_cf_placement_event")) }, times(1))
        }
        // r1 (R-L' L-1): chỉ resource — RC `ads_cf_overrides` không đặt được (allowlist 4 tên, `AdsCfOverrides.ALLOWED`).
        mockStatic(Log::class.java).use {
            AdsCfOverrides.apply("""{"schema_version":1,"ads_cf_overrides":{"ads_cf_placement_event":1}}""")
            assertEquals("RC không nhận tên ngoài allowlist", null, AdsCfOverrides.valueOf("ads_cf_placement_event"))
            assertFalse("RC = 1, app không khai ⇒ vẫn tắt", PlacementPaidEvent.read(resContext(0) { 1 }))
        }
    }

    private companion object {
        const val PKG = "com.example.game"
        const val RES_ID = 0x7f0b0002
        const val UNIT = "ca-app-pub-3940256099942544/1033173712"
        const val ADMOB_NET = "com.google.ads.mediation.admob.AdMobAdapter"
    }
}
