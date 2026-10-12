package com.app.plugin.brain

import org.mockito.ArgumentMatchers.anyString
import com.app.plugin.logevent.inMemoryPrefs
import com.app.plugin.until.PreferenceHelper
import com.app.plugin.MonetizationSdk
import android.util.Log
import com.app.plugin.SdkKillSwitch
import com.app.plugin.consent.ConsentState
import com.app.plugin.consent.ConsentTier
import com.app.plugin.firbase.FirHelperConfig
import com.app.plugin.until.LogUtil
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.contains
import org.mockito.ArgumentMatchers.eq
import org.mockito.ArgumentMatchers.startsWith
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.times
import java.lang.reflect.InvocationTargetException

class BrainPolicyTest {

    @Before
    fun setUp() {
        BrainPolicy.clear()
        // #7 (DOC/66): UNKNOWN không còn được profiling/cá nhân hoá; test logic brain như người đã đồng ý.
        ConsentState.resetForTest()
        ConsentState.setTier(ConsentTier.GRANTED)
    }

    @After
    fun tearDown() {
        ConsentState.resetForTest()
        BrainPolicy.clear()
        // Trả ClickFraudGuard về mặc định vì nó là singleton dùng chung giữa các test.
        ClickFraudGuard.maxClicksPerWindow = 6
        ClickFraudGuard.windowMs = 300_000L
        ClickFraudGuard.lockoutMs = 1_800_000L
    }

    // ═══ Tính chất an toàn: artifact không bao giờ được nới lỏng vượt trần cứng ═══

    @Test
    fun `artifact khong the nang so quang cao vuot tran cung`() {
        val g = GuardrailEngine()
        val json = """
            {
              "version": "evil_v1",
              "guardrails": { "max_interstitial_per_session": 9999, "max_ads_24h": 100000 }
            }
        """.trimIndent()
        val r = BrainPolicy.apply(json, g)

        assertTrue(r.applied)
        assertEquals(
            "phai kep ve tran cung",
            BrainPolicy.HARD_MAX_INTERSTITIAL_PER_SESSION, g.maxInterstitialPerSession
        )
        assertEquals(BrainPolicy.HARD_MAX_ADS_24H, g.maxAds24h)
        assertTrue("phai bao cao viec da kep", r.report.contains("kẹp"))
    }

    @Test
    fun `artifact khong the rut ngan cooldown duoi san cung`() {
        val g = GuardrailEngine()
        BrainPolicy.apply(
            """{"version":"v","guardrails":{"min_minutes_between_interstitial":0}}""", g
        )
        assertEquals(
            "cooldown ngan hon = hung hang hon -> phai kep can duoi",
            BrainPolicy.HARD_MIN_COOLDOWN_MINUTES, g.minMinutesBetweenInterstitial
        )
    }

    @Test
    fun `artifact khong the lam yeu chong click-fraud`() {
        BrainPolicy.apply("""{"version":"v","click_fraud":{"max_clicks_per_window":500}}""")
        assertEquals(
            BrainPolicy.HARD_MAX_CLICKS_PER_WINDOW, ClickFraudGuard.maxClicksPerWindow
        )
    }

    @Test
    fun `artifact duoc phep siet chat hon tran cung`() {
        val g = GuardrailEngine()
        BrainPolicy.apply(
            """{"version":"v","guardrails":{"max_interstitial_per_session":1,"max_ads_24h":5}}""", g
        )
        assertEquals("siet chat hon thi duoc chap nhan nguyen ven", 1, g.maxInterstitialPerSession)
        assertEquals(5, g.maxAds24h)
    }

    @Test
    fun `tran kich ban tu artifact cung bi kep`() {
        val g = GuardrailEngine()
        g.maxInterstitialPerSession = BrainPolicy.HARD_MAX_INTERSTITIAL_PER_SESSION
        BrainPolicy.apply(
            """{"version":"v","scenarios":{"RETENTION_FIRST":{"interstitial_cap":9999}}}""", g
        )
        assertEquals(
            BrainPolicy.HARD_MAX_INTERSTITIAL_PER_SESSION,
            g.effectiveInterstitialCap(PersonalizationScenario.RETENTION_FIRST)
        )
    }

    @Test
    fun `tran kich ban tu artifact duoc ap dung khi hop le`() {
        val g = GuardrailEngine()
        g.maxInterstitialPerSession = 8
        // Mặc định AD_FORWARD là 6; artifact hạ xuống 3.
        BrainPolicy.apply(
            """{"version":"v","scenarios":{"AD_FORWARD":{"interstitial_cap":3}}}""", g
        )
        assertEquals(3, g.effectiveInterstitialCap(PersonalizationScenario.AD_FORWARD))
    }

    // ═══ Artifact hỏng: giữ nguyên cái đang chạy, không crash ═══

    @Test
    fun `artifact hong khong lam hong policy dang chay`() {
        val g = GuardrailEngine()
        BrainPolicy.apply("""{"version":"good_v1","guardrails":{"max_ads_24h":7}}""", g)
        assertEquals("good_v1", BrainPolicy.activeVersion())

        val badInputs = listOf(
            null, "", "   ", "không phải json", "{", "[]",
            """{"guardrails":{}}"""  // thiếu version
        )
        for (bad in badInputs) {
            val r = BrainPolicy.apply(bad, g)
            assertFalse("input '$bad' phai bi tu choi", r.applied)
            assertEquals("phai giu nguyen version dang chay", "good_v1", BrainPolicy.activeVersion())
            assertEquals("khong duoc thay doi nguong", 7, g.maxAds24h)
        }
    }

    @Test
    fun `truong la va kieu sai duoc bo qua thay vi lam hong ca artifact`() {
        val g = GuardrailEngine()
        val json = """
            {
              "version": "v2",
              "guardrails": { "max_ads_24h": 9, "truong_khong_ton_tai": 123 },
              "scenarios": { "KICH_BAN_LA": { "interstitial_cap": 2 } },
              "likelihood_profiles": { "TYPE_LA": { "fail_rate": 0.5 } }
            }
        """.trimIndent()
        val r = BrainPolicy.apply(json, g)
        assertTrue(r.applied)
        assertEquals(9, g.maxAds24h)
        assertTrue("phai bao cao da bo qua cai la", r.report.contains("bỏ qua"))
    }

    // ═══ Kill switch ═══

    @Test
    fun `kill switch buoc moi player ve kich ban trung tinh`() {
        val belief = BeliefState("u")
        belief.posterior[LatentPlayerType.LOW_SKILL_HIGH_CHURN_RISK] = 0.90
        belief.posterior[LatentPlayerType.HIGH_SKILL_LOW_TOLERANCE] = 0.04
        belief.posterior[LatentPlayerType.HIGH_SKILL_HIGH_TOLERANCE] = 0.03
        belief.posterior[LatentPlayerType.CASUAL_STEADY] = 0.03

        // Bình thường: tin cậy 0.90 -> kịch bản chuyên biệt.
        assertEquals(PersonalizationScenario.RETENTION_FIRST, PersonalizationScenario.resolve(belief))

        BrainPolicy.apply("""{"version":"panic","enabled":false}""")
        assertFalse(BrainPolicy.isEnabled())
        assertEquals(
            "kill switch phai vo hieu hoa personalization ngay lap tuc",
            PersonalizationScenario.BALANCED_HYBRID, PersonalizationScenario.resolve(belief)
        )
    }

    // ═══ Calibrate hồ sơ likelihood từ xa ═══

    @Test
    fun `ho so likelihood day du duoc ap dung va lam doi ket qua phan loai`() {
        val s = PlayerState(
            playerId = "u", sessionsLifetime = 10,
            failRateLast30m = 0.85, daysSinceLastSession = 9
        )
        val before = LikelihoodEstimator.estimate(s)[LatentPlayerType.CASUAL_STEADY]!!

        // Đổi hồ sơ CASUAL_STEADY để nó khớp hẳn với hành vi trên.
        val json = """
            {
              "version": "recalibrated",
              "likelihood_profiles": {
                "HIGH_SKILL_LOW_TOLERANCE":  {"fail_rate":0.1,"rewarded_accept":0.2,"payer_propensity":0.7,"churn_tendency":0.2},
                "HIGH_SKILL_HIGH_TOLERANCE": {"fail_rate":0.1,"rewarded_accept":0.8,"payer_propensity":0.2,"churn_tendency":0.2},
                "LOW_SKILL_HIGH_CHURN_RISK": {"fail_rate":0.2,"rewarded_accept":0.5,"payer_propensity":0.2,"churn_tendency":0.2},
                "CASUAL_STEADY":             {"fail_rate":0.85,"rewarded_accept":0.5,"payer_propensity":0.1,"churn_tendency":0.9}
              }
            }
        """.trimIndent()
        assertTrue(BrainPolicy.apply(json).applied)

        val after = LikelihoodEstimator.estimate(s)[LatentPlayerType.CASUAL_STEADY]!!
        assertNotEquals("ho so moi phai lam doi likelihood", before, after)
        assertTrue("hanh vi gio khop CASUAL_STEADY nen likelihood phai tang", after > before)
    }

    @Test
    fun `ho so thieu type thi bo qua toan bo thay vi tron lan`() {
        val s = PlayerState(playerId = "u", sessionsLifetime = 10, failRateLast30m = 0.5)
        val before = LikelihoodEstimator.estimate(s)

        val r = BrainPolicy.apply(
            """
            {"version":"partial","likelihood_profiles":{
              "CASUAL_STEADY":{"fail_rate":0.9,"rewarded_accept":0.5,"payer_propensity":0.1,"churn_tendency":0.9}
            }}
            """.trimIndent()
        )
        assertTrue(r.applied)
        assertTrue("phai canh bao ve viec thieu type", r.report.contains("thiếu type"))

        val after = LikelihoodEstimator.estimate(s)
        assertEquals(
            "tron ho so moi voi cu tao so sanh khap khieng -> phai giu nguyen toan bo",
            before, after
        )
    }

    // ═══ Ngưỡng tin cậy điều chỉnh từ xa ═══

    @Test
    fun `min_confidence co the chinh tu artifact`() {
        val belief = BeliefState("u")
        belief.posterior[LatentPlayerType.LOW_SKILL_HIGH_CHURN_RISK] = 0.30
        belief.posterior[LatentPlayerType.HIGH_SKILL_LOW_TOLERANCE] = 0.25
        belief.posterior[LatentPlayerType.HIGH_SKILL_HIGH_TOLERANCE] = 0.25
        belief.posterior[LatentPlayerType.CASUAL_STEADY] = 0.20

        // Mặc định 0.40 -> 0.30 chưa đủ -> trung tính.
        assertEquals(PersonalizationScenario.BALANCED_HYBRID, PersonalizationScenario.resolve(belief))

        // Hạ ngưỡng xuống 0.28 -> đủ tin cậy.
        BrainPolicy.apply("""{"version":"v","min_confidence":0.28}""")
        assertEquals(PersonalizationScenario.RETENTION_FIRST, PersonalizationScenario.resolve(belief))
    }

    @Test
    fun `clear dua he thong ve mac dinh trong code`() {
        BrainPolicy.apply("""{"version":"v","min_confidence":0.9,"enabled":false}""")
        BrainPolicy.clear()
        assertEquals("built-in-default", BrainPolicy.activeVersion())
        assertTrue(BrainPolicy.isEnabled())
        assertEquals(PersonalizationScenario.MIN_CONFIDENCE, BrainPolicy.minConfidence(), 1e-9)
    }

    // ═══ 1.9.0 Task 5s (D7): đường Remote Config và dòng log version ═══

    /**
     * Áp một snapshot Remote Config chỉ có `brain_policy_android` qua đúng hàm áp của `FirHelperConfig` (mọi khoá khác
     * rỗng). Phần đuôi hàm (reload quảng cáo của `AdsHelper`) có thể lỗi trong unit test — bước brain đã chạy trước đó.
     */
    private fun applyRemoteBrainPolicy(json: String) {
        val snapshotClass = Class.forName("com.app.plugin.firbase.FirHelperConfig\$RemoteCfSnapshot")
        val ctor = snapshotClass.declaredConstructors.single().apply { isAccessible = true }
        val names = snapshotClass.declaredFields.filter { !java.lang.reflect.Modifier.isStatic(it.modifiers) }.map { it.name }
        val args = ctor.parameterTypes.mapIndexed { i, t ->
            when {
                names[i] == "brainPolicy" -> json
                t == String::class.java -> ""
                else -> null
            }
        }
        val apply = FirHelperConfig::class.java.getDeclaredMethod("applyRemoteCf", snapshotClass).apply { isAccessible = true }
        try {
            apply.invoke(FirHelperConfig(), ctor.newInstance(*args.toTypedArray()))
        } catch (e: InvocationTargetException) {
            // đuôi hàm (AdsHelper.saveConfig/checkAfterLoadIdsConfig) không có context trong unit test
        } finally {
            SdkKillSwitch.current = SdkKillSwitch.NONE
        }
    }

    @Test
    fun `K5 RC brain_policy_android di qua DecisionEngine applyPolicy nen bandit nhan exploration_floor`() {
        val bandit = DecisionEngine.instance.bandit
        bandit.explorationFloor = 0.03
        BrainPolicy.pendingExplorationFloor = null
        try {
            applyRemoteBrainPolicy("""{"version":"rc-k5","experiment":{"exploration_floor":0.2}}""")
            assertEquals("rc-k5", BrainPolicy.activeVersion())
            assertEquals("RC dat exploration_floor thi bandit phai nhan (khong bi bo o FirHelperConfig)",
                0.2, bandit.explorationFloor, 1e-9)
        } finally {
            bandit.explorationFloor = 0.03
            BrainPolicy.pendingExplorationFloor = null
        }
    }

    @Test
    fun `K7 artifact duoc chap nhan log dung mot dong BrainPolicy da nap version, bi tu choi thi khong`() {
        mockStatic(Log::class.java).use { log ->
            val engine = DecisionEngine()
            assertTrue(engine.applyPolicy("""{"version":"v-k7","enabled":false}""").applied)
            assertFalse(engine.applyPolicy("""{"enabled":true}""").applied)
            log.verify({ Log.i(eq(LogUtil.TAG), startsWith("BrainPolicy: đã nạp 'v-k7'")) }, times(1))
            log.verify({ Log.i(eq(LogUtil.TAG), contains("đã nạp")) }, times(1))
        }
    }

    // S5 (R-T5s m3): artifact đã lưu mà giờ bị từ chối (hỏng, thiếu version) thì đúng một logE, rồi dùng mặc định
    @Test
    fun `S5 khoi phuc artifact bi tu choi log dung mot logE`() {
        val store: MutableMap<String, Any?> = java.util.Collections.synchronizedMap(mutableMapOf())
        val previous = PreferenceHelper.getInstance().preferences
        PreferenceHelper.getInstance().preferences = inMemoryPrefs(store)
        LogUtil.setFlagLog(1)
        try {
            for (saved in listOf("{ rac", """{"enabled":false}""")) {
                store[BrainPolicyStore.PREF_KEY] = saved
                mockStatic(Log::class.java).use { log ->
                    DecisionEngine()
                    log.verify({ Log.e(anyString(), anyString()) }, times(1))
                    log.verify({ Log.e(eq(LogUtil.TAG), contains("saved artifact rejected")) }, times(1))
                }
                assertEquals("built-in-default", BrainPolicy.activeVersion())
            }
        } finally {
            PreferenceHelper.getInstance().preferences = previous
        }
    }

    // S6 (R-T5s m5): báo cáo configure in rollout hiệu lực (artifact thắng rollout app đặt)
    @Test
    fun `S6 bao cao configure in rollout hieu luc cua artifact`() {
        BrainPolicy.apply("""{"version":"s6","experiment":{"rollout_fraction":0}}""")
        try {
            val r = MonetizationSdk.applyForTest(MonetizationSdk.Config().apply { brainEnabled = true; brainRolloutFraction = 0.5 })
            assertEquals(0.0, r.brainRolloutFraction, 1e-9)
            assertTrue(r.describe(), r.describe().contains("rollout    : 0%"))
        } finally {
            AdsBrain.enabled = false
            AdsBrain.rolloutFraction = 1.0
        }
    }
}
