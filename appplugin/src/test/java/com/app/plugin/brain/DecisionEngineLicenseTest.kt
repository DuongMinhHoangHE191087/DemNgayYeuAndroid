package com.app.plugin.brain

import com.app.plugin.AppPluginInternalApi
import com.app.plugin.consent.ConsentState
import com.app.plugin.consent.ConsentTier
import com.app.plugin.license.LicenseGate
import com.app.plugin.logevent.EventNames
import com.app.plugin.logevent.ServerEventCapture
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 2.2.0 LIC (D3/D16-220, hợp đồng §C): `DecisionEngine.decide` và `AdsBrain` chỉ quyết khi verdict giấy phép là `Valid`.
 * INVALID và PENDING: NO_ACTION đầu tiên, propensity 1.0, guardrail `license_<nhãn>`, 0 decision log, 0 bản ghi inspector,
 * `feedback` no-op; `AdsBrain` về luật tĩnh. VALID: như trước (L21, đặc tả). Fix 1 (D28-220): feedback của quyết định bị chặn
 * bị bỏ qua cả khi verdict đã VALID (L22); chưa VALID: 0 `experiment_exposure`, 0 `onFirstPurchase` (L23, L24).
 */
@OptIn(AppPluginInternalApi::class)
class DecisionEngineLicenseTest {
    private val decisions = CopyOnWriteArrayList<String>()
    private val feedbacks = CopyOnWriteArrayList<String>()

    @Before fun setUp() {
        ConsentState.resetForTest()
        ConsentState.setTier(ConsentTier.GRANTED)
        BrainPolicy.clear()
        AdsBrain.enabled = false
        HoldoutManager.holdoutFraction = 0.0
        BrainInspector.clearHistory()
        DecisionLogger.registerForwarder { req, _ -> decisions += req.playerId }
        DecisionLogger.registerFeedbackForwarder { id, _, _, _ -> feedbacks += id }
    }

    @After fun tearDown() {
        DecisionLogger.clearForwardersForTest()   // gỡ 2 forwarder của setUp (m6; như DecisionLogOffMainTest)
        BrainSignalListener.installed = null
        LicenseGate.resetForTest()
        ConsentState.resetForTest()
        AdsBrain.enabled = false
        HoldoutManager.holdoutFraction = 0.05
        DecisionEngine.instance.guardrails.maxInterstitialPerSession = 6
    }

    private fun candidates() = listOf(
        ActionCandidate(ActionMode.INTERSTITIAL, "inter", isBaseline = true),
        ActionCandidate(ActionMode.NO_ACTION, "no_a"),
        ActionCandidate(ActionMode.NO_ACTION, "no_b"),
    )

    private fun assertUnlicensed(label: String) {
        val id = "p_lic_${System.nanoTime()}"
        val e = DecisionEngine(policyVersion = "lic_v1")
        val req = DecisionRequest(id, DecisionPoint.INTERSTITIAL_OPPORTUNITY)
        val r = e.decide(req, PlayerState(id), candidates())
        assertEquals("NO_ACTION dau tien", "no_a", r.chosenAction.actionId)
        assertEquals(1.0, r.chosenActionProbability, 0.0)
        assertEquals(listOf("license_$label"), r.guardrailsApplied)
        assertEquals("0 decision log", 0, decisions.count { it == id })
        assertTrue("0 ban ghi inspector", BrainInspector.recentDecisions().isEmpty())
        e.feedback(req, r, 1.0)
        assertEquals("feedback no-op", 0, feedbacks.count { it == r.decisionId })
        assertEquals("bandit khong hoc", 0.0, e.observationCount(req.decisionPoint, LatentPlayerType.values()[0], "no_a"), 0.0)
    }

    @Test fun `L18 INVALID thi decide NO_ACTION, 0 decision log, feedback no-op`() {
        LicenseGate.resetForTest(LicenseGate.Verdict.Invalid("sig"))
        assertUnlicensed("INVALID:sig")
    }

    @Test fun `L19 PENDING thi decide nhu INVALID`() {
        LicenseGate.resetForTest()
        assertUnlicensed("PENDING")
    }

    @Test fun `L20 AdsBrain PENDING khong quyet - luat tinh, 0 decision log`() {
        LicenseGate.resetForTest()
        AdsBrain.enabled = true
        val id = "p_lic_ads_${System.nanoTime()}"
        AdsBrain.onSessionStart(id)
        DecisionEngine.instance.guardrails.maxInterstitialPerSession = 2
        repeat(10) {
            assertTrue("PENDING: brain khong chan", AdsBrain.shouldShowInterstitial())
            AdsBrain.onInterstitialShown()
        }
        assertEquals("0 decision log", 0, decisions.count { it == id })
    }

    @Test fun `L21 VALID thi decide nhu truoc - log 1, inspector 1, khong guardrail license`() {
        LicenseGate.resetForTest(LicenseGate.Verdict.Valid("test", Long.MAX_VALUE / 2))
        HoldoutManager.holdoutFraction = 1.0   // nhánh đối chứng tất định: baseline, propensity 1.0
        val id = "p_lic_ok_${System.nanoTime()}"
        val r = DecisionEngine(policyVersion = "lic_v1").decide(
            DecisionRequest(id, DecisionPoint.INTERSTITIAL_OPPORTUNITY), PlayerState(id), candidates()
        )
        assertEquals("inter", r.chosenAction.actionId)
        assertTrue(r.isHoldout)
        assertTrue(r.guardrailsApplied.none { it.startsWith("license_") })
        assertEquals(1, decisions.count { it == id })
        assertEquals(1, BrainInspector.recentDecisions().size)
        HoldoutManager.holdoutFraction = 0.0
        AdsBrain.enabled = true
        val ads = "p_lic_ads_ok_${System.nanoTime()}"
        AdsBrain.onSessionStart(ads)
        AdsBrain.shouldShowInterstitial()
        assertTrue("VALID: AdsBrain quyet", decisions.count { it == ads } >= 1)
    }

    @Test fun `L22 feedback cua quyet dinh bi chan van bi bo qua sau khi verdict thanh VALID`() {
        LicenseGate.resetForTest()
        val id = "p_lic_fb_${System.nanoTime()}"
        val e = DecisionEngine(policyVersion = "lic_v1")
        val req = DecisionRequest(id, DecisionPoint.INTERSTITIAL_OPPORTUNITY)
        val r = e.decide(req, PlayerState(id), candidates())
        LicenseGate.resetForTest(LicenseGate.Verdict.Valid("test", Long.MAX_VALUE / 2))
        e.feedback(req, r, 1.0)
        assertEquals("0 brain_feedback cho id khong co brain_decision", 0, feedbacks.count { it == r.decisionId })
        assertTrue("bandit khong hoc NO_ACTION tat dinh", LatentPlayerType.values().all { e.observationCount(req.decisionPoint, it, "no_a") == 0.0 })
    }

    @Test fun `L23 chua VALID thi 0 experiment_exposure (INVALID va PENDING), VALID thi 1`() {
        AdsBrain.enabled = true
        ServerEventCapture().use { server ->
            LicenseGate.resetForTest(LicenseGate.Verdict.Invalid("sig"))
            AdsBrain.onSessionStart("p_lic_exp_bad_${System.nanoTime()}")
            LicenseGate.resetForTest()
            AdsBrain.onSessionStart("p_lic_exp_pending_${System.nanoTime()}")
            assertEquals("INVALID + PENDING: 0 exposure", 0, server.await(EventNames.EXPERIMENT_EXPOSURE, 1).size)
            LicenseGate.resetForTest(LicenseGate.Verdict.Valid("test", Long.MAX_VALUE / 2))
            AdsBrain.onSessionStart("p_lic_exp_ok_${System.nanoTime()}")
            assertEquals("VALID: exposure cua phien (doi chung khong rong)", 1, server.await(EventNames.EXPERIMENT_EXPOSURE, 1).size)
        }
    }

    @Test fun `L24 chua VALID thi 0 onFirstPurchase nhung dem mua van cap nhat, VALID thi phat`() {
        val firsts = CopyOnWriteArrayList<String>()
        BrainSignalListener.installed = object : BrainSignalListener {
            override fun onFirstPurchase(playerId: String, usd: Double, isHoldout: Boolean) { firsts += playerId }
            override fun onSegmentChange(playerId: String, from: String, to: String, isHoldout: Boolean) = Unit
        }
        for (v in listOf(LicenseGate.Verdict.Invalid("sig"), LicenseGate.Verdict.Pending)) {
            LicenseGate.resetForTest(v)
            val id = "p_lic_buy_${v.javaClass.simpleName}_${System.nanoTime()}"
            AdsBrain.onSessionStart(id)
            AdsBrain.onPurchase(1.99)
            assertEquals("$v: 0 onFirstPurchase", 0, firsts.count { it == id })
            assertEquals("$v: dem mua ngoai brain van cap nhat", 1, AdsBrain.currentState().purchaseCountLifetime)
        }
        LicenseGate.resetForTest(LicenseGate.Verdict.Valid("test", Long.MAX_VALUE / 2))
        val ok = "p_lic_buy_ok_${System.nanoTime()}"
        AdsBrain.onSessionStart(ok)
        AdsBrain.onPurchase(1.99)
        assertEquals("VALID: onFirstPurchase phat", 1, firsts.count { it == ok })
    }
}
