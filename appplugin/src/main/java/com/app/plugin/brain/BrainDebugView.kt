package com.app.plugin.brain

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * View debug hiển thị TRỰC TIẾP segment và kịch bản personalization đang áp dụng.
 *
 * Mục đích: khi test thủ công, người test phải thấy được hệ thống đang phân loại mình vào đâu
 * và đang chạy chiến lược nào — nếu không thì không có cách nào phân biệt "personalization
 * đang chạy đúng" với "personalization không chạy gì cả" (đúng lỗi đã từng xảy ra: belief
 * đứng nguyên ở prior mà nhìn bên ngoài vẫn thấy hệ thống hoạt động bình thường).
 *
 * CHỈ DÙNG CHO DEBUG — không nhúng vào build release. Cách dùng:
 *
 * ```kotlin
 * val debug = BrainDebugView(context)
 * container.addView(debug)
 * debug.bind(DecisionEngine.instance) { currentPlayerState }
 * debug.startAutoRefresh()   // nhớ gọi stopAutoRefresh() trong onDestroy()
 * ```
 */
public class BrainDebugView(context: Context) : LinearLayout(context) {

    private val textView: TextView
    private var engine: DecisionEngine? = null
    private var stateProvider: (() -> PlayerState)? = null

    private val handler = Handler(Looper.getMainLooper())
    private var refreshIntervalMs = 1000L
    private var autoRefreshing = false

    private val refreshTask = object : Runnable {
        override fun run() {
            refresh()
            if (autoRefreshing) handler.postDelayed(this, refreshIntervalMs)
        }
    }

    init {
        orientation = VERTICAL
        setBackgroundColor(Color.parseColor("#111318"))
        setPadding(16, 16, 16, 16)

        addView(TextView(context).apply {
            text = "BRAIN DEBUG"
            setTextColor(Color.parseColor("#7FD1B9"))
            textSize = 13f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            gravity = Gravity.CENTER_HORIZONTAL
        })

        textView = TextView(context).apply {
            setTextColor(Color.parseColor("#D6DEEB"))
            textSize = 10f
            typeface = Typeface.MONOSPACE
            setHorizontallyScrolling(true)
            text = "Chưa bind engine — gọi bind(engine) { playerState }"
        }

        val scroll = ScrollView(context).apply {
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 700)
            addView(textView)
        }
        addView(scroll)
    }

    /**
     * @param stateProvider trả về PlayerState hiện tại. Dùng lambda thay vì truyền thẳng object
     *   để view luôn đọc trạng thái mới nhất, không giữ tham chiếu cũ đã lỗi thời.
     */
    public fun bind(engine: DecisionEngine, stateProvider: () -> PlayerState) {
        this.engine = engine
        this.stateProvider = stateProvider
        refresh()
    }

    public fun refresh() {
        val e = engine
        val provider = stateProvider
        if (e == null || provider == null) return
        textView.text = try {
            BrainInspector.format(BrainInspector.snapshot(e, provider()))
        } catch (ex: Exception) {
            "Lỗi khi dựng snapshot: ${ex.message}"
        }
    }

    public fun startAutoRefresh(intervalMs: Long = 1000L) {
        refreshIntervalMs = intervalMs
        if (autoRefreshing) return
        autoRefreshing = true
        handler.post(refreshTask)
    }

    public fun stopAutoRefresh() {
        autoRefreshing = false
        handler.removeCallbacks(refreshTask)
    }

    override fun onDetachedFromWindow() {
        // Nếu quên gọi stopAutoRefresh() thủ công thì vẫn không rò rỉ handler.
        stopAutoRefresh()
        super.onDetachedFromWindow()
    }
}
