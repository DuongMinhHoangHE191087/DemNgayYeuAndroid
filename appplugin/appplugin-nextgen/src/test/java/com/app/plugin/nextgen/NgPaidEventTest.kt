package com.app.plugin.nextgen

import android.os.Bundle
import android.util.Log
import com.app.plugin.ads.AdRevenueRouting
import com.app.plugin.appsflyer.AppsflyerHelper
import com.app.plugin.consent.ConsentTier
import com.app.plugin.consent.contextWithTcf
import com.app.plugin.firbase.PaidEvent
import com.app.plugin.firbase.PlacementPaidEvent
import com.app.plugin.logevent.EventNames
import com.app.plugin.logevent.ServerEventCapture
import com.appsflyer.AppsFlyerLib
import com.google.android.libraries.ads.mobile.sdk.common.AdValue
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.MediationAdError
import com.google.android.libraries.ads.mobile.sdk.common.PrecisionType
import com.google.android.libraries.ads.mobile.sdk.common.ResponseInfo
import com.google.firebase.Firebase
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.analytics
import com.google.gson.JsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockConstruction
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.mockingDetails
import org.mockito.Mockito.`when`

/**
 * 2.4.0 NG3 (Hợp đồng B, [S24]–[S26]) — bản nextgen của phần GMA trong `PaidEventTest`: `onAdPaid(AdValue)` của Next-Gen
 * → `ad_revenue` với `precision_type` 0–3 theo enum (NF2), `value_micros` Long nguyên bản, currency; `ad_source` = ánh xạ
 * của `ResponseInfo.getAdapterClassName()` (NF3); mã show-fail = mã `mediationAdError` nếu có (NF4, phần PaidEvent).
 */
class NgPaidEventTest {
    @After fun tearDown() {
        AdRevenueRouting.admobLinkedToFirebase = false
        PlacementPaidEvent.resetForTest()
        AppsflyerHelper.instance.resetForTest()
    }

    /** Firebase giả của lượt [paid] gần nhất (2.5.1 NF5: đọc param của `ad_impression`). */
    private var firebase: FirebaseAnalytics = mock(FirebaseAnalytics::class.java)

    /** Harness như `PaidEventTest`: Firebase/AppsFlyer/`Log`/`Bundle` giả, AppsFlyer bắt đầu với tầng GRANTED. */
    private fun paid(action: () -> Unit, check: ServerEventCapture.() -> Unit) {
        firebase = mock(FirebaseAnalytics::class.java)
        ServerEventCapture().use { server ->
            mockStatic(Class.forName("com.google.firebase.analytics.AnalyticsKt")).use { analyticsKt ->
                analyticsKt.`when`<FirebaseAnalytics> { Firebase.analytics }.thenReturn(firebase)
                mockStatic(AppsFlyerLib::class.java).use { af ->
                    af.`when`<AppsFlyerLib> { AppsFlyerLib.getInstance() }.thenReturn(mock(AppsFlyerLib::class.java))
                    mockStatic(Log::class.java).use {
                        mockConstruction(Bundle::class.java).use {
                            AppsflyerHelper.instance.startAfterConsent(
                                contextWithTcf(gdpr = 1, purposes = "1111111111", afAppId = "devKey"), ConsentTier.GRANTED, gdprApplies = true,
                            )
                            action()
                            server.check()
                        }
                    }
                }
            }
        }
    }

    private fun info(adapter: String?): ResponseInfo = mock(ResponseInfo::class.java).also { `when`(it.adapterClassName).thenReturn(adapter) }

    private fun admob(v: AdValue, net: String = "admob") = PaidEvent.admob("full_home", "full_home", "interstitial", UNIT, net, v)

    /** r1 (R-L' M-1): Full2 trên slot chung — showPl ≠ originPl, khoá "placement đang hiện, không phải slot" ở bản override NG. */
    private fun full2(v: AdValue) = PaidEvent.admob("full_2_home", "full_default", "interstitial", UNIT, "admob", v)

    private fun List<JsonObject>.ints(key: String) = map { it.get(key).asInt }

    /** Giá trị param chuỗi [key] của mỗi Firebase [event] (Bundle giả ghi mọi `putString`). */
    private fun firebaseParam(key: String, event: String = FirebaseAnalytics.Event.AD_IMPRESSION): List<String> =
        mockingDetails(firebase).invocations
            .filter { it.method.name == "logEvent" && it.arguments[0] == event }
            .flatMap { inv ->
                mockingDetails(inv.arguments[1]).invocations
                    .filter { it.method.name == "putString" && it.arguments[0] == key }
                    .map { it.arguments[1] as String }
            }

    @Test fun `NF2 precision enum sang Int dung bang Legacy, value_micros Long nguyen ban, currency`() {
        val types = listOf(PrecisionType.UNKNOWN, PrecisionType.ESTIMATED, PrecisionType.PUBLISHER_PROVIDED, PrecisionType.PRECISE)
        val revs = types.mapIndexed { i, t ->
            var rev: JsonObject? = null
            paid({ admob(AdValue(t, 1_001L + i, "EUR")) }) { rev = await("ad_revenue", 1).single() }
            requireNotNull(rev)
        }
        assertEquals(listOf(0, 1, 2, 3), revs.ints("precision_type"))
        assertEquals("Long, không qua double", listOf("1001", "1002", "1003", "1004"), revs.map { it.get("value_micros").asString })
        assertEquals(List(4) { "EUR" }, revs.map { it.get("currency").asString })
        assertEquals(List(4) { "admob" }, revs.map { it.get("mediation").asString })
        assertEquals(PaidEvent.PRECISION_NONE, PaidEvent.precision(null))
    }

    @Test fun `NF3 ad_network tu getAdapterClassName qua cung bo anh xa`() {
        assertEquals("facebook", PaidEvent.adNetwork(info("com.google.ads.mediation.facebook.FacebookMediationAdapter")))
        assertEquals("AdMob tự phục vụ (U6, đo ở NG6 #48)", "admob", PaidEvent.adNetwork(info("com.google.ads.mediation.admob.AdMobAdapter")))
        assertEquals("pangle", PaidEvent.adNetwork(info("com.google.ads.mediation.pangle.PangleMediationAdapter")))
        assertEquals("source_null", PaidEvent.adNetwork(info(null)))
        assertEquals("source_null", PaidEvent.adNetwork(null))
        paid({ admob(AdValue(PrecisionType.ESTIMATED, 5L, "USD"), PaidEvent.adNetwork(info("com.google.ads.mediation.facebook.FacebookMediationAdapter"))) }) {
            assertEquals("facebook", await("ad_revenue", 1).single().get("ad_source").asString)
        }
    }

    @Test fun `NF4 ma show-fail la ma mediationAdError neu co, khong thi ma loi SDK`() {
        val withMediation = FullScreenContentError(
            FullScreenContentError.ErrorCode.MEDIATION_SHOW_ERROR, "adapter failed", MediationAdError(1001, "meta: no fill", "com.facebook.ads")
        )
        val plain = FullScreenContentError(FullScreenContentError.ErrorCode.APP_NOT_FOREGROUND, "background", null)
        assertEquals("1001", PaidEvent.showFailCode(withMediation))
        assertEquals("${FullScreenContentError.ErrorCode.APP_NOT_FOREGROUND.value}", PaidEvent.showFailCode(plain))
    }

    @Test fun `NF5 Next-Gen - ad_impression mang ad_placement bang ad_revenue placement`() {
        // 2.5.1 PLACEMENT: `PaidEvent` NG là bản override, phải sửa song song Legacy (M4).
        paid({ full2(AdValue(PrecisionType.PRECISE, 7_100L, "USD")) }) {
            val rev = await("ad_revenue", 1).single()
            assertEquals(listOf("full_2_home"), firebaseParam("ad_placement"))
            assertEquals("bằng ad_revenue.placement: $rev", listOf(rev.get("placement").asString), firebaseParam("ad_placement"))
            assertEquals("ad_impression giữ imp_id", listOf(rev.get("imp_id").asString), firebaseParam("imp_id"))
        }
    }

    @Test fun `NF6 Next-Gen - AdMob da lien ket + bat - 1 ad_placement_impression mang ad_placement, khong co khoa value`() {
        // 2.5.1 PLEVT: `PaidEvent` NG là bản override, phải gọi `PlacementPaidEvent` song song Legacy.
        AdRevenueRouting.admobLinkedToFirebase = true
        PlacementPaidEvent.enabled = true
        paid({ full2(AdValue(PrecisionType.PRECISE, 7_200L, "USD")) }) {
            val rev = await("ad_revenue", 1).single()
            val plevt = EventNames.AD_PLACEMENT_IMPRESSION
            assertEquals("số ad_placement_impression", 1, events(plevt))
            assertEquals("liên kết ⇒ 0 ad_impression của SDK", 0, events(FirebaseAnalytics.Event.AD_IMPRESSION))
            assertEquals(listOf("full_2_home"), firebaseParam("ad_placement", plevt))
            assertEquals(listOf(rev.get("imp_id").asString), firebaseParam("imp_id", plevt))
            assertFalse("khoá value không có", FirebaseAnalytics.Param.VALUE in putKeys(plevt))
            assertEquals(true, "ad_value" in putKeys(plevt))
        }
    }

    private fun events(name: String): Int =
        mockingDetails(firebase).invocations.count { it.method.name == "logEvent" && it.arguments[0] == name }

    private fun putKeys(name: String): Set<String> = mockingDetails(firebase).invocations
        .filter { it.method.name == "logEvent" && it.arguments[0] == name }
        .flatMap { inv -> mockingDetails(inv.arguments[1]).invocations.filter { it.method.name.startsWith("put") }.map { it.arguments[0] as String } }
        .toSet()

    private companion object {
        const val UNIT = "ca-app-pub-3940256099942544/1033173712"
    }
}
