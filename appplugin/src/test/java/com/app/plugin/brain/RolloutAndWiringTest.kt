package com.app.plugin.brain

import android.content.SharedPreferences
import com.app.plugin.logevent.EventNames
import com.app.plugin.logevent.ServerEventCapture
import com.app.plugin.logevent.inMemoryPrefs
import com.app.plugin.until.PreferenceHelper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Hai thứ cần có TRƯỚC khi cắm brain vào luồng quảng cáo thật:
 *
 * 1. **Staged rollout** — bật brain cho 5% dân số trước, không phải 100% (doc 38 mục F.2).
 *    Không có nó thì `enabled = true` là một công tắc all-or-nothing, và cách duy nhất để
 *    lùi lại là release app mới.
 * 2. **Player ID ổn định** — brain học theo player. ID đổi mỗi lần mở app thì belief không
 *    bao giờ tích luỹ, và phân nhóm holdout/rollout nhảy lung tung khiến phép đo vô nghĩa.
 */
class RolloutAndWiringTest {
    @get:Rule val license = LicenseTestRule()

    /** "Đĩa" của K2–K6: prefs trong bộ nhớ, sống qua cold start giả ([coldStart]). */
    private val store = mutableMapOf<String, Any?>()
    private var previousPrefs: SharedPreferences? = null
    private var prefsInstalled = false

    @Before
    fun setUp() {
        PlayerIdSource.resetForTest()   // onSessionStart() đọc PlayerIdSource: id app của test khác không được rò vào
        AdsBrain.enabled = false
        AdsBrain.rolloutFraction = 1.0
        HoldoutManager.holdoutFraction = 0.0
        BrainPolicy.clear()
    }

    @After
    fun tearDown() {
        PlayerIdSource.resetForTest()
        AdsBrain.enabled = false
        AdsBrain.rolloutFraction = 1.0
        HoldoutManager.holdoutFraction = 0.05
        BrainPolicy.clear()
        DecisionEngine.instance.guardrails.maxInterstitialPerSession = 6
        BrainPolicy.pendingExplorationFloor = null
        DecisionLogger.clearForwardersForTest()
        if (prefsInstalled) PreferenceHelper.getInstance().preferences = previousPrefs
    }

    private fun usePrefs() {
        previousPrefs = PreferenceHelper.getInstance().preferences
        PreferenceHelper.getInstance().preferences = inMemoryPrefs(store)
        prefsInstalled = true
    }

    /**
     * Như process mới: bộ nhớ brain sạch, prefs ([store]) giữ. Engine mới dựng = lần chạm `DecisionEngine` lúc
     * `initPlugin`; sau đó app chạy `MonetizationSdk.configure` (brain bật, rollout app = 1.0).
     */
    private fun coldStart(): DecisionEngine {
        BrainPolicy.clear()
        BrainPolicy.pendingExplorationFloor = null
        return DecisionEngine().also {
            AdsBrain.enabled = true
            AdsBrain.rolloutFraction = 1.0
        }
    }

    /** Mọi `brain_decision` của [playerId] (forwarder của `DecisionLogger` = đường của hàng `brain_decision`). */
    private fun captureDecisions(playerId: String): MutableList<DecisionRequest> {
        val seen = java.util.Collections.synchronizedList(mutableListOf<DecisionRequest>())
        DecisionLogger.registerForwarder { req, _ -> if (req.playerId == playerId) seen += req }
        return seen
    }

    /**
     * Brain đứng ngoài: 20 lần hỏi interstitial đều về luật tĩnh (`true`) dù trần guardrail là 1, rewarded và IAP/paywall
     * cũng `true`, 0 `brain_decision`. [killed]: kill switch của artifact → cả `experiment_exposure` cũng 0 (R-T5s I1).
     */
    private fun assertStaticPolicy(playerId: String, what: String, killed: Boolean = false) = ServerEventCapture().use { server ->
        val decisions = captureDecisions(playerId)
        AdsBrain.onSessionStart(playerId)
        DecisionEngine.instance.guardrails.maxInterstitialPerSession = 1
        repeat(20) { i ->
            assertTrue("$what phai tra ve policy tinh, nhung goi #$i bi tu choi", AdsBrain.shouldShowInterstitial())
            AdsBrain.onInterstitialShown()
        }
        repeat(5) { i ->
            assertTrue("$what: rewarded phai ve policy tinh, goi #$i", AdsBrain.shouldOfferRewarded())
            assertTrue("$what: IAP/paywall phai ve policy tinh, goi #$i", AdsBrain.shouldOfferIap())
        }
        assertEquals("$what: so brain_decision", 0, decisions.size)
        if (killed) {
            val exposures = server.awaitRequests(EventNames.EXPERIMENT_EXPOSURE, 1, settleMs = 100L).filter {
                it.getAsJsonObject("userProperty").get("user_id").asString == playerId
            }
            assertEquals("$what: so experiment_exposure", 0, exposures.size)
        }
    }

    // ═══ Player ID ═══

    @Test
    fun `player id on dinh trong cung mot process`() {
        // Trong unit test SharedPreferences trả về giá trị mặc định (isReturnDefaultValues),
        // tức storage là no-op. ID vẫn phải ổn định — nếu chỉ dựa vào đĩa thì mỗi lần gọi
        // sinh một UUID mới, và lỗi này chỉ lộ ra trên thiết bị chưa kịp ghi prefs.
        val a = BrainStorage.playerId()
        val b = BrainStorage.playerId()
        assertEquals("player id phai on dinh giua cac lan goi", a, b)
    }

    @Test
    fun `player id khong rong`() {
        assertTrue("player id rong lam hong toan bo phan nhom", BrainStorage.playerId().isNotBlank())
    }

    @Test
    fun `onSessionStart khong tham so dung player id cua storage`() {
        AdsBrain.onSessionStart()
        assertEquals(BrainStorage.playerId(), AdsBrain.currentState().playerId)
    }

    // ═══ Staged rollout ═══

    @Test
    fun `mac dinh rollout la toan bo`() {
        // Công tắc an toàn là `enabled`, không phải `rolloutFraction`. Đặt rollout mặc định
        // bằng 0 sẽ tạo ra một no-op im lặng: bật brain lên mà không có gì xảy ra, không có
        // dấu hiệu gì để chẩn đoán. Một công tắc rõ ràng tốt hơn hai công tắc mờ.
        assertEquals(1.0, AdsBrain.rolloutFraction, 1e-9)
    }

    @Test
    fun `ngoai rollout thi brain im lang hoan toan`() {
        AdsBrain.enabled = true
        AdsBrain.rolloutFraction = 0.0
        val id = "p_norollout_${System.nanoTime()}"
        AdsBrain.onSessionStart(id)
        DecisionEngine.instance.guardrails.maxInterstitialPerSession = 1

        // Trần là 1, nhưng ngoài rollout thì trần đó không được áp — hành vi phải y hệt
        // lúc brain còn tắt.
        repeat(20) {
            assertTrue("ngoai rollout phai giu nguyen luong cu", AdsBrain.shouldShowInterstitial())
            AdsBrain.onInterstitialShown()
        }
        repeat(10) { AdsBrain.onLevelFail() }
        assertTrue(
            "ngoai rollout thi khong duoc hoc gi",
            DecisionEngine.instance.beliefFor(id).isStillPrior()
        )
    }

    @Test
    fun `trong rollout thi brain quyet dinh nhu binh thuong`() {
        AdsBrain.enabled = true
        AdsBrain.rolloutFraction = 1.0
        AdsBrain.onSessionStart("p_inrollout_${System.nanoTime()}")
        DecisionEngine.instance.guardrails.maxInterstitialPerSession = 2

        var shown = 0
        repeat(25) {
            if (AdsBrain.shouldShowInterstitial()) {
                shown++; AdsBrain.onInterstitialShown()
            } else AdsBrain.onAdSkipped()
        }
        assertTrue("trong rollout thi tran phai duoc ton trong, da hien $shown", shown <= 2)
    }

    @Test
    fun `phan nhom rollout la tat dinh theo player`() {
        // Cùng player -> luôn cùng nhóm. Random-per-call sẽ khiến một người lúc trong lúc
        // ngoài thí nghiệm, làm hỏng hoàn toàn phép so sánh treatment/holdout.
        val id = "p_stable_${System.nanoTime()}"
        val first = HoldoutManager.isInRollout(id, 0.5)
        repeat(50) {
            assertEquals("phan nhom phai tat dinh", first, HoldoutManager.isInRollout(id, 0.5))
        }
    }

    @Test
    fun `rollout mot phan khong bao gom tat ca player`() {
        // Với 5% thì trong 400 player phải có người ở ngoài — nếu tất cả đều "trong rollout"
        // thì cổng staged rollout thực chất không hoạt động.
        val inRollout = (1..400).count { HoldoutManager.isInRollout("p_dist_$it", 0.05) }
        assertTrue("rollout 5% ma $inRollout/400 nam trong -> cong khong hoat dong", inRollout < 400)
        assertNotEquals("rollout 5% khong duoc loai het moi nguoi", 400, inRollout)
    }

    // ═══ Điều khiển rollout từ xa (không cần release app) ═══

    @Test
    fun `artifact dat duoc rollout fraction`() {
        BrainPolicy.apply("""{"version":"v1","experiment":{"rollout_fraction":0.1}}""")
        assertEquals(0.1, BrainPolicy.rolloutOverride()!!, 1e-9)
    }

    @Test
    fun `artifact co the ha rollout ve 0 de lui khan cap`() {
        AdsBrain.rolloutFraction = 1.0
        BrainPolicy.apply("""{"version":"panic","experiment":{"rollout_fraction":0}}""")
        assertEquals("phai lui duoc ve 0 ma khong can release app", 0.0, BrainPolicy.rolloutOverride()!!, 1e-9)
    }

    @Test
    fun `rollout fraction bi kep trong khoang hop le`() {
        BrainPolicy.apply("""{"version":"v","experiment":{"rollout_fraction":5}}""")
        assertEquals(1.0, BrainPolicy.rolloutOverride()!!, 1e-9)
        BrainPolicy.apply("""{"version":"v","experiment":{"rollout_fraction":-3}}""")
        assertEquals(0.0, BrainPolicy.rolloutOverride()!!, 1e-9)
    }

    @Test
    fun `artifact khong nhac toi rollout thi giu nguyen`() {
        AdsBrain.rolloutFraction = 0.25
        BrainPolicy.apply("""{"version":"v","guardrails":{"max_ads_24h":7}}""")
        assertEquals("khong nhac toi thi khong duoc dong vao", 0.25, AdsBrain.rolloutFraction, 1e-9)
    }

    @Test
    fun `artifact hong khong lam thay doi rollout`() {
        AdsBrain.rolloutFraction = 0.25
        BrainPolicy.apply("""{ khong phai json """)
        assertEquals(0.25, AdsBrain.rolloutFraction, 1e-9)
    }

    // ═══ Tính chất bao trùm: tắt vẫn phải là tắt ═══

    @Test
    fun `tat brain thi rollout khong con y nghia`() {
        AdsBrain.enabled = false
        AdsBrain.rolloutFraction = 1.0
        AdsBrain.onSessionStart("p_offrollout_${System.nanoTime()}")
        DecisionEngine.instance.guardrails.maxInterstitialPerSession = 1
        repeat(10) {
            assertTrue(AdsBrain.shouldShowInterstitial())
            AdsBrain.onInterstitialShown()
        }
    }

    @Test
    fun `holdout va rollout doc lap nhau`() {
        // Hai không gian băm riêng: trong nhóm holdout vẫn phải có người nằm trong rollout.
        // Nếu phụ thuộc nhau thì không thể vừa chạy staged rollout vừa đo holdout.
        HoldoutManager.holdoutFraction = 0.2
        val holdoutPlayers = (1..2000).map { "p_ind_$it" }.filter { HoldoutManager.isHoldout(it) }
        assertTrue("can co du player holdout de kiem tra", holdoutPlayers.size > 20)
        val alsoInRollout = holdoutPlayers.count { HoldoutManager.isInRollout(it, 0.5) }
        assertTrue(
            "holdout va rollout phai doc lap, nhung $alsoInRollout/${holdoutPlayers.size} nam trong rollout",
            alsoInRollout > 0 && alsoInRollout < holdoutPlayers.size
        )
    }

    // ═══ 1.9.0 Task 5s (D7): kill switch thật, artifact giữ qua cold start ═══

    @Test
    fun `K1 enabled false trong artifact thi interstitial ve policy tinh va khong co brain_decision`() {
        AdsBrain.enabled = true
        DecisionEngine().applyPolicy("""{"version":"kill-k1","enabled":false}""")
        assertStaticPolicy("p_k1_${System.nanoTime()}", "kill switch", killed = true)
    }

    @Test
    fun `K2 rollout_fraction 0 cua artifact giu tu cold start du app dat rollout 1`() {
        usePrefs()
        DecisionEngine().applyPolicy("""{"version":"r0-k2","experiment":{"rollout_fraction":0}}""")
        coldStart()
        assertStaticPolicy("p_k2_${System.nanoTime()}", "rollout 0 sau cold start")
    }

    @Test
    fun `K3 artifact da chap nhan duoc khoi phuc luc cold start truoc quyet dinh dau tien`() {
        usePrefs()
        DecisionEngine().applyPolicy(
            """{"version":"v-k3","guardrails":{"max_interstitial_per_session":2},"experiment":{"exploration_floor":0.2}}"""
        )
        val engine = coldStart()
        assertEquals("artifact da chap nhan phai duoc khoi phuc", "v-k3", BrainPolicy.activeVersion())
        assertEquals(2, engine.guardrails.maxInterstitialPerSession)
        assertEquals(0.2, engine.bandit.explorationFloor, 1e-9)
    }

    @Test
    fun `K4 artifact bi tu choi khong duoc luu, cold start giu ban chap nhan cuoi`() {
        usePrefs()
        val engine = DecisionEngine()
        assertTrue(engine.applyPolicy("""{"version":"good-k4"}""").applied)
        assertFalse(engine.applyPolicy("""{"enabled":false}""").applied)   // thiếu version
        assertFalse(engine.applyPolicy("""{ khong phai json """).applied)
        coldStart()
        assertEquals("ban bi tu choi khong duoc thay ban da luu", "good-k4", BrainPolicy.activeVersion())
        assertTrue(BrainPolicy.isEnabled())
    }

    @Test
    fun `K6 kill switch giu qua cold start khi app bat lai brain`() {
        usePrefs()
        DecisionEngine().applyPolicy("""{"version":"kill-k6","enabled":false}""")
        coldStart()
        assertEquals("kill-k6", BrainPolicy.activeVersion())
        assertStaticPolicy("p_k6_${System.nanoTime()}", "kill switch sau cold start", killed = true)
    }
}
