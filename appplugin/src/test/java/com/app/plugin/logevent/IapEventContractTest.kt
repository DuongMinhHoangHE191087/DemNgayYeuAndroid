package com.app.plugin.logevent

import com.app.plugin.iap.PaywallSession
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Hợp đồng event IAP/paywall v2 (plan 1.8.0, "Hợp đồng E"; T7 C1–C6, D5, D7): tên event và tập key bắt buộc của
 * `eventData` cho bảy event mà [IapEventLog] dựng; key snake_case ≤ 40 ký tự (cùng luật của `EventNameContractTest`);
 * sáu event mới vào `DURABLE` và `NEVER_SAMPLE`; không có `usd` (D3).
 */
class IapEventContractTest {
    private val snakeCase = Regex("^[a-z][a-z0-9_]{0,39}$")
    private val funnelNames = listOf(
        "iap_result", "paywall_impression", "paywall_holdout_skip", "paywall_skip", "paywall_choice", "subscription_state"
    )

    @Before fun setUp() { PaywallSession.close() }

    @After fun tearDown() {
        PaywallSession.close()
        PaywallSession.clock = System::currentTimeMillis
    }

    private fun assertSnake(name: String, d: Map<String, Any>) =
        assertTrue("$name: key sai luật ${d.keys.filterNot(snakeCase::matches)}", d.keys.all(snakeCase::matches))

    private fun assertHas(name: String, d: Map<String, Any>, keys: Set<String>) =
        assertTrue("$name thieu: ${keys - d.keys}", d.keys.containsAll(keys))

    @Test fun `C1 iap_purchase co phase, offer_tag, paywall_session_id, placement, variant_id`() {
        val old = setOf(
            "product_id", "order_id", "price_micros", "currency_code", "quantity", "channel", "is_subscription",
            "purchase_token", "screen_name"
        )
        val v2 = setOf("phase", "offer_tag", "paywall_session_id", "placement", "variant_id")
        val out = IapEventLog.iapPurchaseData(
            "vip", "GPA.1", 0L, "USD", 1, "PLAY_STORE", true, "tok", IapEventLog.PHASE_TRIAL, "", null, "home"
        )
        assertHas("iap_purchase", out, old + v2)
        assertSnake("iap_purchase", out)
        assertFalse("D3: không usd", "usd" in out.keys)
        assertEquals("trial thu 0", 0L, out["price_micros"])
        assertEquals("ngoài paywall: rỗng", listOf("", "", ""), listOf("paywall_session_id", "placement", "variant_id").map { out[it] })
        // Phiên paywall: dữ liệu vào; hết hạn sau 30 phút thì không dính nhầm.
        var now = 1_000L
        PaywallSession.clock = { now }
        val s = PaywallSession.open("shop", "b", "sess-1")
        val inPw = IapEventLog.iapPurchaseData("vip", "o", 5L, "USD", 1, "PLAY_STORE", true, "t", IapEventLog.PHASE_BASE, "x", s, "home")
        assertEquals(listOf("sess-1", "shop", "b"), listOf("paywall_session_id", "placement", "variant_id").map { inPw[it] })
        assertNotNull(PaywallSession.current())
        now += PaywallSession.TTL_MS + 1
        assertNull("hết hạn", PaywallSession.current())
    }

    @Test fun `C2 iap_result du param`() {
        val d = IapEventLog.iapResultData(IapEventLog.OUTCOME_ACK_FAILED, 6, "vip", "sess-1")
        assertHas("iap_result", d, setOf("outcome", "response_code", "product_id", "paywall_session_id"))
        assertSnake("iap_result", d)
        assertEquals("ack_failed", d["outcome"])
        assertEquals(
            setOf("success", "cancel", "pending", "failed", "already_owned", "ack_failed"),
            setOf(
                IapEventLog.OUTCOME_SUCCESS, IapEventLog.OUTCOME_CANCEL, IapEventLog.OUTCOME_PENDING,
                IapEventLog.OUTCOME_FAILED, IapEventLog.OUTCOME_ALREADY_OWNED, IapEventLog.OUTCOME_ACK_FAILED
            )
        )
    }

    @Test fun `C3 paywall_skip, choice, impression, holdout_skip du param`() {
        val skip = IapEventLog.paywallSkipData("shop", IapEventLog.SKIP_SESSION_CAP)
        val choice = IapEventLog.paywallChoiceData("sess-1", "shop", "b", "vip")
        val imp = IapEventLog.paywallImpressionData("shop", "b", 2, "pw_v1", "sess-1", 2, "home")
        val hold = IapEventLog.paywallHoldoutSkipData("shop", "b", "pw_v1", "sess-1", "home")
        assertHas("paywall_skip", skip, setOf("placement", "reason"))
        assertHas("paywall_choice", choice, setOf("paywall_session_id", "placement", "variant_id", "product_id"))
        assertHas("paywall_impression", imp, setOf("placement", "variant_id", "product_count", "paywall_version", "paywall_session_id", "schema_version_cfg"))
        assertHas("paywall_holdout_skip", hold, setOf("placement", "counterfactual_variant_id", "paywall_version", "paywall_session_id"))
        listOf("skip" to skip, "choice" to choice, "imp" to imp, "hold" to hold).forEach { (n, d) -> assertSnake(n, d) }
        assertEquals(setOf("session_cap", "brain", "no_config"), setOf(IapEventLog.SKIP_SESSION_CAP, IapEventLog.SKIP_BRAIN, IapEventLog.SKIP_NO_CONFIG))
        assertFalse("không phiên paywall: không phát paywall_choice", IapEventLog.onPurchaseLaunched("vip"))
    }

    @Test fun `C4 6 ten funnel trong DURABLE va NEVER_SAMPLE`() {
        assertEquals("DURABLE thieu ${funnelNames.filterNot { it in DurableEvents.DURABLE }}", true, funnelNames.all { it in DurableEvents.DURABLE })
        assertEquals("NEVER_SAMPLE thieu ${funnelNames.filterNot { it in LogEventControl.NEVER_SAMPLE }}", true, funnelNames.all { it in LogEventControl.NEVER_SAMPLE })
        assertTrue("iap_purchase vẫn bền và không lấy mẫu", EventNames.IAP_PURCHASE in DurableEvents.DURABLE && EventNames.IAP_PURCHASE in LogEventControl.NEVER_SAMPLE)
    }

    @Test fun `C5 ten moi trong EventNames`() {
        assertEquals("iap_result", EventNames.IAP_RESULT)
        assertEquals("paywall_skip", EventNames.PAYWALL_SKIP)
        assertEquals("paywall_choice", EventNames.PAYWALL_CHOICE)
        val text = File("src/main/java/com/app/plugin/logevent/EventNames.kt").readText()
        for (n in listOf("iap_result", "paywall_skip", "paywall_choice")) assertTrue("EventNames thieu \"$n\"", "\"$n\"" in text)
        val src = File("src/main/java/com/app/plugin/logevent/IapEventLog.kt").readText()
        for (n in funnelNames + "iap_purchase") assertFalse("literal \"$n\" ngoài EventNames", "\"$n\"" in src)
    }

    @Test fun `C6 subscription_state co source`() {
        val d = IapEventLog.subscriptionStateData("vip", "ACTIVE", "IN_GRACE_PERIOD", "v1", IapEventLog.SOURCE_SERVER, "home")
        assertHas("subscription_state", d, setOf("product_id", "from_state", "to_state", "lifecycle_version", "source"))
        assertSnake("subscription_state", d)
        assertEquals("server", d["source"])
        assertEquals("client", IapEventLog.SOURCE_CLIENT)
    }
}
