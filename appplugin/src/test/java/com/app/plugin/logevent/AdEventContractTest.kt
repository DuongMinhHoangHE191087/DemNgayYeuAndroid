package com.app.plugin.logevent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Hợp đồng event v2 (plan 1.7.0, bảng "Hợp đồng event v2"; T1 C1, SF): tên event và tập key bắt buộc của `eventData`
 * cho 11 event mà [AdEventLog] dựng; mọi key là snake_case ≤ 40 ký tự (cùng luật tên của `EventNameContractTest`).
 */
class AdEventContractTest {
    private val snakeCase = Regex("^[a-z][a-z0-9_]{0,39}$")
    private val loadKeys = setOf("ad_format", "mediation", "placement", "ad_unit", "request_id", "screen_name", "is_sdk_init", "slot")

    private val required: Map<String, Set<String>> = mapOf(
        "ad_request" to loadKeys,
        "ad_fill" to loadKeys + setOf("latency_ms", "ad_source"),
        "ad_no_fill" to loadKeys + setOf("latency_ms", "error_code", "reason"),
        "ad_show_attempt" to setOf("ad_format", "placement", "ready", "screen_name"),
        "ad_show" to setOf("ad_format", "mediation", "ad_source", "placement", "ad_unit", "imp_id"),
        "ad_show_fail" to setOf("ad_format", "placement", "reason", "is_action", "screen_name"),
        "ad_click" to setOf("ad_format", "placement", "ad_unit"),
        "ad_revenue" to setOf(
            "ad_format", "mediation", "ad_source", "ad_unit", "placement", "value_micros", "currency",
            "precision_type", "imp_id", "screen_name"
        ),
        "consent_form_shown" to setOf("gdpr_applies", "form_kind"),
        "consent_choice" to setOf("tier", "gdpr_applies", "gpp_sections", "latency_ms", "form_kind"),
        "session_end" to setOf("duration_ms", "ads_shown_json", "screens", "fg_index"),
    )

    @Test fun `C1 hop dong - moi key eventData cua 11 event la snake_case va du tap bat buoc`() {
        val r = AdEventLog.LoadRequest("interstitial", "admob", "full_home", "id-1", "req-1", "home", 1)
        val built: Map<String, Map<String, Any>> = mapOf(
            EventNames.AD_REQUEST to AdEventLog.requestData(r),
            EventNames.AD_FILL to AdEventLog.fillData(r, 12L, "admob"),
            EventNames.AD_NO_FILL to AdEventLog.noFillData(r, 12L, "3", "no fill"),
            EventNames.AD_SHOW_ATTEMPT to AdEventLog.showAttemptData("interstitial", "full_home", 1, "home"),
            EventNames.AD_SHOW to AdEventLog.showData("interstitial", "admob", "admob", "full_home", "id-1", "imp-1"),
            EventNames.AD_SHOW_FAIL to AdEventLog.showFailData("fullscreen", "full_home", "cf_not", 2, "home"),
            EventNames.AD_CLICK to AdEventLog.clickData("interstitial", "full_home", "id-1"),
            EventNames.AD_REVENUE to AdEventLog.revenueData(
                "interstitial", "admob", "admob", "id-1", "full_home", 1_234L, "USD", 3, "imp-1", "home"
            ),
            EventNames.CONSENT_FORM_SHOWN to AdEventLog.consentFormShownData(1, "initial"),
            EventNames.CONSENT_CHOICE to AdEventLog.consentChoiceData("GRANTED", 1, "2_7", 900L, "initial"),
            EventNames.SESSION_END to AdEventLog.sessionEndData(60_000L, "{\"interstitial\":1}", 3, 1),
        )
        assertEquals("tên event v2", required.keys, built.keys)
        val violations = built.flatMap { (name, data) ->
            data.keys.filterNot(snakeCase::matches).map { "$name: key sai luật '$it'" } +
                (required.getValue(name) - data.keys).map { "$name: thiếu '$it'" }
        }
        assertTrue("hợp đồng event v2 vỡ:\n${violations.joinToString("\n")}", violations.isEmpty())
        assertEquals("reason ≤ 100 ký tự", 100, (AdEventLog.noFillData(r, 1L, "3", "x".repeat(150))["reason"] as String).length)
    }

    @Test fun `SF logAdShowFailWithReason phat ad_show_fail`() {
        ServerEventCapture().use { s ->
            LogEventServer.logAdShowFailWithReason("full_home", "cf_not")
            val sf = s.await("ad_show_fail", 1)
            assertEquals("ad_show_fail v2: ${s.allRequests().map { it.get("eventName") }}", 1, sf.size)
            val data = sf.single()
            assertEquals("full_home", data.get("placement").asString)
            assertEquals("cf_not", data.get("reason").asString)
            assertEquals("fullscreen", data.get("ad_format").asString)
            assertTrue("is_action/screen_name: $data", data.has("is_action") && data.has("screen_name") && !data.has("isAction"))
            assertEquals("tên v1 không còn phát", 0, s.await("ad_show_fail_reason", 0, settleMs = 0).size)
        }
    }
}
