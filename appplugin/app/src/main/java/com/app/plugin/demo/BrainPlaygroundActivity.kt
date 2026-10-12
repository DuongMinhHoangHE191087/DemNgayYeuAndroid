package com.app.plugin.demo

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.app.plugin.brain.ActionCandidate
import com.app.plugin.brain.ActionMode
import com.app.plugin.brain.BrainDebugView
import com.app.plugin.brain.DecisionEngine
import com.app.plugin.brain.DecisionPoint
import com.app.plugin.brain.DecisionRequest
import com.app.plugin.brain.PlayerState

/**
 * Sân thử (playground) cho Monetization Brain.
 *
 * Mục đích: tạo hành vi giả lập bằng tay, rồi QUAN SÁT TRỰC TIẾP hệ thống phân loại mình vào
 * segment nào và đang chạy kịch bản personalization nào. Không có màn hình này thì không thể
 * phân biệt "personalization chạy đúng" với "personalization không chạy" — cả hai đều trông
 * giống nhau từ bên ngoài.
 *
 * Cách dùng để thấy segment dịch chuyển rõ nhất:
 *  - Bấm "Từ chối rewarded" 4-5 lần + "Mua IAP" → belief nghiêng về HIGH_SKILL_LOW_TOLERANCE
 *    → kịch bản chuyển sang "Bảo vệ & chuyển đổi", trần interstitial tụt xuống 2.
 *  - Ngược lại, bấm "Nhận rewarded" nhiều lần → nghiêng về AD_FORWARD, trần lên 6.
 *  - Bấm "Thua màn" liên tục + "Qua 1 ngày không chơi" → LOW_SKILL_HIGH_CHURN_RISK
 *    → "Giữ chân trước hết", trần tụt còn 1.
 */
class BrainPlaygroundActivity : Activity() {

    private val engine = DecisionEngine.instance

    /** Trạng thái player giả lập — trong sản phẩm thật, feature pipeline sẽ điền chỗ này. */
    private var state = PlayerState(playerId = "demo_player")

    private lateinit var debugView: BrainDebugView
    private lateinit var decisionLog: TextView

    // Đếm cục bộ để suy ra tỉ lệ, mô phỏng việc feature pipeline tổng hợp từ event stream.
    private var levelAttempts = 0
    private var levelFails = 0
    private var rewardedOffered = 0
    private var rewardedAccepted = 0
    private var adsThisSession = 0

    // Giữ quyết định gần nhất để gửi phản hồi outcome về cho bandit.
    private var lastRequest: DecisionRequest? = null
    private var lastDecision: com.app.plugin.brain.DecisionResponse? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 48, 24, 24)
            setBackgroundColor(Color.parseColor("#0B0D11"))
        }

        debugView = BrainDebugView(this)
        root.addView(debugView)

        root.addView(sectionLabel("MÔ PHỎNG HÀNH VI"))
        root.addView(row(
            button("Thua màn") { onLevelFail() },
            button("Thắng màn") { onLevelWin() }
        ))
        root.addView(row(
            button("Nhận rewarded") { onRewarded(accepted = true) },
            button("Từ chối rewarded") { onRewarded(accepted = false) }
        ))
        root.addView(row(
            button("Mua IAP \$4.99") { onPurchase(4.99) },
            button("Qua 1 ngày nghỉ") { onDayPassed() }
        ))

        root.addView(sectionLabel("HỎI BRAIN QUYẾT ĐỊNH"))
        root.addView(button("Điểm interstitial — nên hiện không?") { askInterstitial() })
        root.addView(button("Điểm rewarded — nên mời không?") { askRewarded() })

        root.addView(sectionLabel("PHẢN HỒI KẾT QUẢ (để bandit học)"))
        root.addView(row(
            button("User chấp nhận ✓") { sendFeedback(1.0) },
            button("User bỏ qua ✗") { sendFeedback(0.0) }
        ))

        root.addView(sectionLabel("KẾT QUẢ QUYẾT ĐỊNH"))
        decisionLog = TextView(this).apply {
            setTextColor(Color.parseColor("#F2C94C"))
            textSize = 11f
            typeface = android.graphics.Typeface.MONOSPACE
            text = "(chưa có quyết định nào)"
        }
        root.addView(decisionLog)

        root.addView(sectionLabel("POLICY ARTIFACT (mô phỏng Remote Config)"))
        root.addView(row(
            button("Áp policy siết chặt") { applySamplePolicy(STRICT_POLICY) },
            button("Áp policy nới lỏng\n(sẽ bị kẹp)") { applySamplePolicy(TOO_LOOSE_POLICY) }
        ))
        root.addView(row(
            button("Bật KILL SWITCH") { applySamplePolicy(KILL_SWITCH_POLICY) },
            button("Xoá policy") {
                com.app.plugin.brain.BrainPolicy.clear()
                debugView.refresh()
                Toast.makeText(this, "Đã về mặc định trong code", Toast.LENGTH_SHORT).show()
            }
        ))

        root.addView(sectionLabel("TIỆN ÍCH"))
        root.addView(row(
            button("Phiên mới") { onNewSession() },
            button("Xoá học lại từ đầu") { onResetAll() }
        ))

        setContentView(ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0B0D11"))
            addView(root)
        })

        debugView.bind(engine) { state }
        debugView.startAutoRefresh(800L)
    }

    // ---------- Mô phỏng hành vi ----------

    private fun onLevelFail() {
        levelAttempts++
        levelFails++
        state.failStreakCurrent += 1
        state.currentLevel = maxOf(state.currentLevel, 1)
        recomputeRates()
        pushObservation("thua màn (streak=${state.failStreakCurrent})")
    }

    private fun onLevelWin() {
        levelAttempts++
        state.failStreakCurrent = 0
        state.currentLevel += 1
        recomputeRates()
        pushObservation("thắng màn (level=${state.currentLevel})")
    }

    private fun onRewarded(accepted: Boolean) {
        rewardedOffered++
        if (accepted) {
            rewardedAccepted++
            state.adImpressions24h += 1
            state.minutesSinceLastAd = 0
        }
        recomputeRates()
        pushObservation(if (accepted) "nhận rewarded" else "từ chối rewarded")
    }

    private fun onPurchase(usd: Double) {
        state.purchaseCountLifetime += 1
        state.purchaseValueLifetimeUsd += usd
        state.daysSinceLastPurchase = 0
        recomputeRates()
        pushObservation("mua IAP \$$usd (tổng ${state.purchaseCountLifetime} lần)")
    }

    private fun onDayPassed() {
        state.daysSinceLastSession += 1
        if (state.daysSinceLastPurchase >= 0) state.daysSinceLastPurchase += 1
        recomputeRates()
        pushObservation("qua 1 ngày không chơi (${state.daysSinceLastSession} ngày)")
    }

    private fun onNewSession() {
        state.sessionsLifetime += 1
        state.daysSinceLastSession = 0
        state.sessionMinutesCurrent = 0
        adsThisSession = 0
        recomputeRates()
        pushObservation("bắt đầu phiên mới (#${state.sessionsLifetime})")
    }

    private fun onResetAll() {
        engine.resetPlayer(state.playerId)   // xoá cả bộ nhớ lẫn storage
        state = PlayerState(playerId = "demo_player")
        levelAttempts = 0; levelFails = 0
        rewardedOffered = 0; rewardedAccepted = 0
        adsThisSession = 0
        com.app.plugin.brain.BrainInspector.clearHistory()
        lastRequest = null; lastDecision = null
        decisionLog.text = "(đã reset — belief quay về prior đồng đều)"
        debugView.refresh()
        Toast.makeText(this, "Đã reset belief về prior", Toast.LENGTH_SHORT).show()
    }

    /** Tổng hợp lại các tỉ lệ — mô phỏng công việc của feature pipeline trong sản phẩm thật. */
    private fun recomputeRates() {
        state.failRateLast30m = if (levelAttempts > 0) levelFails.toDouble() / levelAttempts else 0.0
        state.rewardedOpportunitiesRecent = rewardedOffered
        state.rewardedAcceptRateRecent =
            if (rewardedOffered > 0) rewardedAccepted.toDouble() / rewardedOffered else 0.0
        if (state.sessionsLifetime == 0) state.sessionsLifetime = 1
    }

    /** Nạp quan sát vào brain — đây chính là chỗ belief dịch chuyển. */
    private fun pushObservation(what: String) {
        val updated = engine.observe(state)
        debugView.refresh()
        if (!updated) {
            Toast.makeText(this, "$what — chưa đủ dữ liệu để cập nhật belief", Toast.LENGTH_SHORT).show()
        }
    }

    // ---------- Hỏi brain ----------

    private fun askInterstitial() {
        val request = DecisionRequest(state.playerId, DecisionPoint.INTERSTITIAL_OPPORTUNITY)
        val candidates = listOf(
            ActionCandidate(ActionMode.NO_ACTION, "no_action"),
            ActionCandidate(ActionMode.INTERSTITIAL, "interstitial_default")
        )
        val d = engine.decide(
            request, state, candidates,
            adsShownThisSession = adsThisSession,
            minutesSinceLastInterstitial = Int.MAX_VALUE
        )
        if (d.chosenAction.mode == ActionMode.INTERSTITIAL) adsThisSession++
        lastRequest = request; lastDecision = d
        showDecision(d)
    }

    private fun askRewarded() {
        val request = DecisionRequest(state.playerId, DecisionPoint.REWARDED_OPPORTUNITY)
        val candidates = listOf(
            ActionCandidate(ActionMode.NO_ACTION, "no_action"),
            ActionCandidate(ActionMode.REWARDED_AD, "rewarded_revive"),
            ActionCandidate(ActionMode.FREE_ACTION, "free_rescue")
        )
        val d = engine.decide(request, state, candidates, adsShownThisSession = adsThisSession)
        lastRequest = request; lastDecision = d
        showDecision(d)
    }

    /**
     * Gửi outcome về brain để bandit cập nhật posterior.
     *
     * Trong sản phẩm thật, reward KHÔNG phải nhị phân 0/1 như ở đây — nó là công thức đầy đủ
     * ở doc 11 (revenue tức thời + Δ LTV − rủi ro churn...). Nhị phân chỉ đủ cho playground.
     */
    private fun sendFeedback(reward: Double) {
        val req = lastRequest
        val res = lastDecision
        if (req == null || res == null) {
            Toast.makeText(this, "Chưa có quyết định nào để phản hồi", Toast.LENGTH_SHORT).show()
            return
        }
        engine.feedback(req, res, reward)
        val n = res.segment?.let {
            engine.observationCount(req.decisionPoint, it, res.chosenAction.actionId)
        } ?: 0.0
        Toast.makeText(
            this,
            "Đã ghi nhận (reward=$reward). Arm này đã có %.1f quan sát.".format(n),
            Toast.LENGTH_SHORT
        ).show()
        debugView.refresh()
    }

    private fun showDecision(d: com.app.plugin.brain.DecisionResponse) {
        decisionLog.text = buildString {
            appendLine("→ CHỌN: ${d.chosenAction.mode} (${d.chosenAction.actionId})")
            // 2.2.0 LIC: chưa VALID thì engine trả guardrail `license_<nhãn>`; không có = brain đã quyết = VALID.
            appendLine("  license: ${d.guardrailsApplied.firstOrNull { it.startsWith("license_") }?.removePrefix("license_") ?: "VALID"}")
            appendLine("  xác suất chọn (propensity) : ${"%.3f".format(d.chosenActionProbability)}")
            appendLine("  segment lúc quyết định     : ${d.segment} (${"%.0f%%".format(d.segmentConfidence * 100)})")
            appendLine("  kịch bản                   : ${d.scenario?.displayName}")
            appendLine("  arm đủ điều kiện           : ${d.eligibleActionIds.joinToString(", ")}")
            if (d.guardrailsApplied.isNotEmpty()) {
                appendLine("  ⛔ guardrail đã chặn        : ${d.guardrailsApplied.joinToString(", ")}")
            }
            append("  decision_id                : ${d.decisionId}")
        }
        debugView.refresh()
    }

    // ---------- Policy artifact ----------

    private fun applySamplePolicy(json: String) {
        val result = engine.applyPolicy(json)
        decisionLog.text = "POLICY: ${result.report}"
        debugView.refresh()
    }

    // ---------- Helper dựng UI ----------

    private fun sectionLabel(text: String) = TextView(this).apply {
        this.text = "\n$text"
        setTextColor(Color.parseColor("#7FD1B9"))
        textSize = 12f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
    }

    private fun button(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        textSize = 12f
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    /** Xếp các nút thành một hàng ngang, chia đều bề rộng (width=0 + weight là cách đúng). */
    private fun row(vararg children: android.view.View) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        children.forEach { child ->
            child.layoutParams = LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
            )
            addView(child)
        }
    }

    override fun onDestroy() {
        debugView.stopAutoRefresh()
        super.onDestroy()
    }

    companion object {
        /** Policy hợp lệ, siết chặt hơn mặc định — mọi giá trị được áp nguyên vẹn. */
        private val STRICT_POLICY = """
            {
              "version": "demo_strict_v1",
              "enabled": true,
              "guardrails": {
                "max_interstitial_per_session": 2,
                "min_minutes_between_interstitial": 5,
                "max_ads_24h": 12,
                "churn_risk_threshold": 0.5
              },
              "scenarios": {
                "AD_FORWARD": { "interstitial_cap": 3 },
                "BALANCED_HYBRID": { "interstitial_cap": 2 }
              },
              "min_confidence": 0.35
            }
        """.trimIndent()

        /**
         * Policy cố tình nới lỏng quá mức — dùng để CHỨNG MINH cơ chế kẹp an toàn hoạt động.
         * Sau khi áp, xem báo cáo: mọi giá trị vượt trần đều bị kẹp lại và ghi rõ lý do.
         */
        private val TOO_LOOSE_POLICY = """
            {
              "version": "demo_too_loose_v1",
              "guardrails": {
                "max_interstitial_per_session": 9999,
                "min_minutes_between_interstitial": 0,
                "max_ads_24h": 100000
              },
              "click_fraud": { "max_clicks_per_window": 500 }
            }
        """.trimIndent()

        /** Tắt khẩn cấp toàn bộ personalization — mọi player về kịch bản trung tính. */
        private val KILL_SWITCH_POLICY = """
            { "version": "demo_kill_switch", "enabled": false }
        """.trimIndent()
    }
}
