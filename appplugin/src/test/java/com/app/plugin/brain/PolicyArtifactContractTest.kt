package com.app.plugin.brain

import com.app.plugin.iap.Entitlements
import com.app.plugin.iap.FxRates
import org.junit.After
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Hợp đồng giữa SDK và backend: file `DOC/policy_artifact_example.json` là **bản mẫu chuẩn**
 * mà training service sẽ sinh ra.
 *
 * ═══ VÌ SAO TEST NÀY TỒN TẠI ═══
 *
 * Một schema viết trong tài liệu sẽ trôi khỏi parser — và đã trôi thật: doc 35 bản đầu không
 * hề nhắc tới `experiment`, `reward`, `iap`, `fx_rates`, `remove_ads_products` dù `BrainPolicy`
 * đã đọc cả năm khối đó. Hậu quả của kiểu trôi này rất khó chịu: backend sinh artifact theo
 * tài liệu, client **âm thầm bỏ qua** những khối nó không biết, và không ai thấy lỗi ở đâu cả
 * vì mọi thứ vẫn "chạy".
 *
 * Test này khoá lại điều đó bằng cách nạp chính file tài liệu và kiểm tra từng giá trị thực sự
 * đi tới đúng đích. File mẫu hỏng hoặc parser đổi mà quên cập nhật mẫu → test đỏ ngay.
 */
class PolicyArtifactContractTest {

    private lateinit var guardrails: GuardrailEngine

    /**
     * Thư mục làm việc của unit test không được đảm bảo, nên thử vài vị trí thay vì giả định
     * một đường dẫn. Không tìm thấy thì FAIL — file mẫu biến mất cũng là một lỗi thật, không
     * phải lý do để bỏ qua test.
     */
    private fun artifactFile(): File {
        val candidates = listOf(
            "DOC/policy_artifact_example.json",
            "../DOC/policy_artifact_example.json",
            "../../DOC/policy_artifact_example.json"
        )
        return candidates.map(::File).firstOrNull { it.exists() }
            ?: throw AssertionError(
                "khong tim thay DOC/policy_artifact_example.json (cwd=${File(".").absolutePath})"
            )
    }

    @Before
    fun setUp() {
        guardrails = GuardrailEngine()
        BrainPolicy.clear()
        FxRates.reset()
        Entitlements.clearAll()
        RewardModel.reset()
    }

    @After
    fun tearDown() {
        // Artifact chạm vào nhiều singleton dùng chung; không trả về mặc định thì test sau
        // chạy với cấu hình của test này.
        BrainPolicy.clear()
        FxRates.reset()
        Entitlements.clearAll()
        RewardModel.reset()
        HoldoutManager.holdoutFraction = 0.05
        HoldoutManager.salt = "brain_holdout_v1"
        AdsBrain.rolloutFraction = 1.0
        ClickFraudGuard.maxClicksPerWindow = 6
    }

    private fun applyExample(): BrainPolicy.ApplyResult =
        BrainPolicy.apply(artifactFile().readText(), guardrails)

    @Test
    fun `ban mau chuan phai duoc chap nhan`() {
        val r = applyExample()
        assertTrue("ban mau trong tai lieu phai ap duoc: ${r.report}", r.applied)
        assertEquals("policy_2026_08_24_reference", BrainPolicy.activeVersion())
        assertTrue(BrainPolicy.isEnabled())
    }

    @Test
    fun `ban mau khong bi kep gia tri nao`() {
        // Bản mẫu là thứ đội vận hành sẽ copy. Nếu chính nó vượt trần cứng thì mọi người
        // sẽ học theo một cấu hình sai ngay từ ngày đầu.
        val r = applyExample()
        assertTrue(
            "ban mau chuan khong duoc chua gia tri bi kep hay bi bo qua: ${r.report}",
            !r.report.contains("vượt trần") &&
                !r.report.contains("dưới sàn") &&
                !r.report.contains("bỏ qua")
        )
    }

    @Test
    fun `khoi guardrails di toi dung dich`() {
        applyExample()
        assertEquals(4, guardrails.maxInterstitialPerSession)
        assertEquals(5, guardrails.minMinutesBetweenInterstitial)
        assertEquals(30, guardrails.maxAds24h)
        assertEquals(0.7, guardrails.churnRiskProtectionThreshold, 1e-9)
    }

    @Test
    fun `khoi iap di toi dung dich`() {
        applyExample()
        assertEquals(2, guardrails.maxIapOffersPerSession)
        assertEquals(15, guardrails.minMinutesBetweenIapOffers)
        assertEquals(2, guardrails.maxPurchasesPerSessionBeforeBreak)
        assertEquals(4, guardrails.frustrationStreakThreshold)
    }

    @Test
    fun `khoi gameplay di toi dung dich`() {
        applyExample()
        assertEquals(4, guardrails.maxAssistsPerSession)
    }

    @Test
    fun `khoi progression di toi dung dich`() {
        applyExample()
        assertEquals(10, guardrails.minLevelForInterstitial)
        assertEquals(22, guardrails.minLevelForInterstitialAfterLoss)
        assertEquals(2, guardrails.minMinutesAfterRewarded)
        assertEquals(1, guardrails.skipFirstInterstitialsOnReopen)
        assertEquals(2, guardrails.maxIapOffers24h)
    }

    @Test
    fun `khoi experiment di toi dung dich`() {
        applyExample()
        assertEquals(0.05, HoldoutManager.holdoutFraction, 1e-9)
        assertEquals("brain_holdout_v1", HoldoutManager.salt)
        assertEquals(0.1, BrainPolicy.rolloutOverride()!!, 1e-9)
        assertEquals(0.03, BrainPolicy.pendingExplorationFloor!!, 1e-9)
    }

    @Test
    fun `khoi reward di toi dung dich va giu tinh chat chong suy bien`() {
        applyExample()
        assertEquals(0.3, RewardModel.weightImmediateValue, 1e-9)
        assertEquals(0.7, RewardModel.weightContinuation, 1e-9)
        // Tính chất bắt buộc (doc 39): "hiện ad rồi mất người chơi" phải luôn bị chấm THẤP HƠN
        // "im lặng mà giữ được người". Bản mẫu vi phạm điều này là bản mẫu dạy sai.
        assertTrue(
            "trong so trong ban mau lam mat tinh chat chong suy bien",
            RewardModel.estimate(true, false) < RewardModel.estimate(false, true)
        )
    }

    @Test
    fun `khoi fx_rates di toi dung dich`() {
        applyExample()
        assertEquals(4, FxRates.knownCurrencyCount())
        assertEquals(10.79, FxRates.toUsd(9_990_000L, "EUR")!!, 0.01)
    }

    @Test
    fun `khoi remove_ads_products di toi dung dich`() {
        applyExample()
        Entitlements.grant("premium_yearly")
        assertTrue("san pham trong ban mau phai tat duoc quang cao", Entitlements.hasRemoveAds())
    }

    @Test
    fun `khoi scenarios va likelihood_profiles di toi dung dich`() {
        applyExample()
        assertEquals(2, BrainPolicy.scenarioCap(PersonalizationScenario.PROTECT_AND_CONVERT))
        assertEquals(1, BrainPolicy.scenarioCap(PersonalizationScenario.RETENTION_FIRST))
        assertEquals(0.4, BrainPolicy.minConfidence(), 1e-9)
        for (type in LatentPlayerType.values()) {
            assertTrue("thieu ho so cho $type", BrainPolicy.profileFor(type) != null)
        }
    }

    @Test
    fun `moi khoi trong ban mau deu duoc parser doc toi`() {
        // Chốt chặn cuối chống trôi: liệt kê khoá cấp cao nhất của file mẫu và đối chiếu với
        // danh sách khoá mà BrainPolicy thực sự xử lý. Thêm khoá vào mẫu mà quên viết parser
        // (hoặc ngược lại) sẽ bị bắt ngay tại đây.
        val known = setOf(
            "version", "enabled", "guardrails", "iap", "gameplay", "progression", "click_fraud",
            "experiment", "reward", "scenarios", "likelihood_profiles", "fx_rates",
            "remove_ads_products", "min_confidence"
        )
        val text = artifactFile().readText()
        val topLevelKeys = Regex("""^\s{2}"([a-z_]+)"\s*:""", RegexOption.MULTILINE)
            .findAll(text).map { it.groupValues[1] }.toSet()
        val unknown = topLevelKeys - known
        assertTrue("ban mau co khoa parser khong doc: $unknown", unknown.isEmpty())
        assertEquals("ban mau phai the hien DU moi khoi parser ho tro", known, topLevelKeys)
    }

    // ═══ 1.9.0 Task 5s, §Hợp đồng E: artifact calibrate do backend (T9) sinh ═══

    /** Fixture chung `backend/tests/fixtures/brain_policy_calibrated.json` (T9 sinh); thiếu thì FAIL, không bỏ qua. */
    private fun calibratedFixture(): File = listOf(
        "backend/tests/fixtures/brain_policy_calibrated.json",
        "../backend/tests/fixtures/brain_policy_calibrated.json",
        "../../backend/tests/fixtures/brain_policy_calibrated.json"
    ).map(::File).firstOrNull { it.exists() }
        ?: throw AssertionError("khong tim thay backend/tests/fixtures/brain_policy_calibrated.json (cwd=${File(".").absolutePath})")

    @Test
    fun `K8 artifact calibrate duoc chap nhan va likelihood_profiles toi LikelihoodEstimator`() {
        val text = calibratedFixture().readText()
        val root = JsonParser.parseString(text).asJsonObject
        val r = BrainPolicy.apply(text, guardrails)
        assertTrue("artifact calibrate phai ap duoc: ${r.report}", r.applied)
        assertEquals(root.get("version").asString, BrainPolicy.activeVersion())
        assertTrue(BrainPolicy.activeVersion().startsWith("calib-"))
        val profiles = root.getAsJsonObject("likelihood_profiles")
        for (type in LatentPlayerType.values()) {
            val want = profiles.getAsJsonObject(type.name)
            val got = BrainPolicy.profileFor(type) ?: throw AssertionError("thieu ho so $type")
            assertEquals(want.get("fail_rate").asDouble, got.failRate, 1e-9)
            assertEquals(want.get("rewarded_accept").asDouble, got.rewardedAcceptRate, 1e-9)
            assertEquals(want.get("payer_propensity").asDouble, got.payerPropensity, 1e-9)
            assertEquals(want.get("churn_tendency").asDouble, got.churnTendency, 1e-9)
        }
        // Tới LikelihoodEstimator: cùng artifact, đổi một `mu` → likelihood của type đó đổi theo.
        val state = PlayerState("p_k8").apply { sessionsLifetime = 3; failRateLast30m = 0.9 }
        val before = LikelihoodEstimator.estimate(state)
        (profiles.getAsJsonObject("CASUAL_STEADY") as JsonObject).addProperty("fail_rate", 0.9)
        assertTrue(BrainPolicy.apply(root.toString(), guardrails).applied)
        val after = LikelihoodEstimator.estimate(state)
        assertNotEquals(before[LatentPlayerType.CASUAL_STEADY]!!, after[LatentPlayerType.CASUAL_STEADY]!!, 1e-6)
        assertEquals(before[LatentPlayerType.HIGH_SKILL_LOW_TOLERANCE]!!, after[LatentPlayerType.HIGH_SKILL_LOW_TOLERANCE]!!, 1e-12)
    }
}
