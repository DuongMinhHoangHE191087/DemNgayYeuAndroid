package com.app.plugin.brain

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ComfortIndexTest {

    @Before fun setUp() { RewardModel.reset(); BrainPolicy.clear() }
    @After  fun tearDown() { RewardModel.reset(); BrainPolicy.clear() }

    // ═══ Tính chất quan trọng nhất: không được giả vờ biết ═══

    @Test
    fun `luon bao cao do phu, khong tra ve con so tran`() {
        val r = ComfortIndex.compute(PlayerState("u"))
        assertTrue("do phu phai trong (0,1]", r.coverage > 0.0 && r.coverage <= 1.0)
        assertTrue("phai liet ke thanh phan thieu", r.componentsMissing.isNotEmpty())
    }

    @Test
    fun `do phu hien tai chua dat nguong dang tin`() {
        val r = ComfortIndex.compute(PlayerState("u"))
        assertFalse(
            "chi 2/5 thanh phan co du lieu -> KHONG duoc coi la ket luan",
            r.isTrustworthy()
        )
        assertTrue("mo ta phai canh bao ro", r.describe().contains("CHƯA ĐỦ TIN CẬY"))
    }

    @Test
    fun `liet ke ro ly do tung thanh phan bi thieu`() {
        val missing = ComfortIndex.compute(PlayerState("u")).componentsMissing
        assertTrue(missing.any { it.contains("organic_purchase_probability") })
        assertTrue(missing.any { it.contains("refund_rate") })
        assertTrue(missing.any { it.contains("post_purchase_retention_delta") })
        assertTrue("phai noi ly do, khong chi ten", missing.all { it.contains("(") })
    }

    // ═══ Hướng biến thiên ═══

    @Test
    fun `quang cao cang nhieu thi comfort cang giam`() {
        val light = ComfortIndex.compute(PlayerState("u", adImpressions24h = 2), adCapPerDay = 40)
        val heavy = ComfortIndex.compute(PlayerState("u", adImpressions24h = 38), adCapPerDay = 40)
        assertTrue("${light.value} phai > ${heavy.value}", light.value > heavy.value)
    }

    @Test
    fun `nghi cang lau thi comfort cang giam`() {
        val active = ComfortIndex.compute(PlayerState("u", daysSinceLastSession = 0))
        val gone = ComfortIndex.compute(PlayerState("u", daysSinceLastSession = 10))
        assertTrue("${active.value} phai > ${gone.value}", active.value > gone.value)
    }

    @Test
    fun `gia tri luon nam trong khoang 0 den 1`() {
        val cases = listOf(
            PlayerState("u", adImpressions24h = 0, daysSinceLastSession = 0),
            PlayerState("u", adImpressions24h = 9999, daysSinceLastSession = 999),
            PlayerState("u", adImpressions24h = -5, daysSinceLastSession = -1)
        )
        for (s in cases) {
            val v = ComfortIndex.compute(s).value
            assertTrue("value=$v ngoai khoang", v in 0.0..1.0)
        }
    }

    @Test
    fun `adCap bang 0 khong gay chia cho 0`() {
        val r = ComfortIndex.compute(PlayerState("u", adImpressions24h = 5), adCapPerDay = 0)
        assertFalse(r.value.isNaN())
    }

    // ═══ Tách khỏi mức hung hăng của chính sách ═══

    @Test
    fun `guardrail no nhieu KHONG lam giam ComfortIndex`() {
        val e = DecisionEngine()
        e.guardrails.maxInterstitialPerSession = 0
        val s = PlayerState("u_agg_${System.nanoTime()}")
        val before = ComfortIndex.compute(s).value
        // Kích cho guardrail nổ nhiều lần.
        repeat(10) {
            e.guardrails.evaluate(
                ActionCandidate(ActionMode.INTERSTITIAL, "i"), s, 99, 999
            )
        }
        assertTrue("guardrail no la tin hieu ve CHINH SACH, khong phai ve nguoi choi",
            ComfortIndex.compute(s).value == before)
        assertTrue("nhung phai bao cao duoc rieng",
            ComfortIndex.policyAggressiveness(e).isNotEmpty())
    }

    // ═══ Trọng số reward trong policy artifact ═══

    @Test
    fun `artifact chinh duoc trong so reward`() {
        val e = DecisionEngine()
        val r = e.applyPolicy(
            """{"version":"rw","reward":{"weight_immediate_value":0.3,"weight_continuation":0.7}}"""
        )
        assertTrue(r.applied)
        assertEquals(0.3, RewardModel.weightImmediateValue, 1e-9)
        assertEquals(0.7, RewardModel.weightContinuation, 1e-9)
    }

    @Test
    fun `artifact lam mat tinh chat chong suy bien thi phai CANH BAO`() {
        val e = DecisionEngine()
        val r = e.applyPolicy(
            """{"version":"greedy","reward":{"weight_immediate_value":0.95,"weight_continuation":0.05}}"""
        )
        assertTrue(r.applied)   // vẫn áp — đây là quyết định của người vận hành
        assertTrue(
            "nhung PHAI canh bao ro rang: ${r.report}",
            r.report.contains("chống suy biến") || r.report.contains("hung hăng")
        )
    }

    @Test
    fun `trong so hop ly thi khong canh bao`() {
        val e = DecisionEngine()
        val r = e.applyPolicy(
            """{"version":"ok","reward":{"weight_immediate_value":0.4,"weight_continuation":0.6}}"""
        )
        assertFalse(r.report.contains("chống suy biến"))
    }
}
