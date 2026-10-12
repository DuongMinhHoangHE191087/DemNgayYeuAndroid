package com.app.plugin.ads

import android.app.Activity
import android.app.Dialog
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.Window
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.app.plugin.R
import com.app.plugin.until.LogUtil

/**
 * Màn intro của rewarded interstitial (1.9.0 Task 2, hợp đồng §C): đếm ngược [COUNTDOWN_SECONDS] giây rồi quảng cáo
 * hiện ([Listener.onContinue]); nút "Bỏ qua" hoặc nút Back = [Listener.onSkip]. SDK tự vẽ — lựa chọn an toàn, không trích
 * chính sách AdMob. Chuỗi trong `appplugin_rwi_strings.xml` (mặc định tiếng Anh, `values-vi` tiếng Việt; app ghi đè theo tên).
 *
 * Vòng đời (không giữ Activity quá host): đóng khi host bị huỷ — `AdsMobRwi.clearActivity` ([dismissActive]) hoặc
 * `ON_DESTROY` của host là [LifecycleOwner] → [Listener.onHostGone]. Host dừng (`ON_STOP`) thì đếm ngược tạm dừng, chạy
 * lại ở `ON_START`. Mọi lối ra đi qua [finish] đúng một lần: gỡ hẹn giờ, gỡ observer, bỏ tham chiếu [active].
 * Chỉ dùng trên main thread.
 */
internal class RwiIntroDialog private constructor(private val activity: Activity, private val listener: Listener) {

    interface Listener { fun onContinue(); fun onSkip(); fun onHostGone() }

    private enum class Outcome { CONTINUE, SKIP, HOST_GONE }

    private val handler = Handler(Looper.getMainLooper())
    private var dialog: Dialog? = null
    private var countdown: TextView? = null
    private var remaining = COUNTDOWN_SECONDS
    private var finished = false
    private var paused = false

    private val tick: Runnable = object : Runnable {
        override fun run() {
            if (finished || paused) return
            remaining--
            if (remaining <= 0) return finish(Outcome.CONTINUE)
            updateCountdown()
            handler.postDelayed(this, TICK_MS)
        }
    }

    private val hostObserver = LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_DESTROY -> finish(Outcome.HOST_GONE)
            Lifecycle.Event.ON_STOP -> { paused = true; handler.removeCallbacks(tick) }
            Lifecycle.Event.ON_START -> if (paused && !finished) { paused = false; handler.postDelayed(tick, TICK_MS) }
            else -> Unit
        }
    }

    private fun open(): Boolean {
        if (activity.isFinishing || activity.isDestroyed) {
            LogUtil.logD("ads rwi intro: host finishing, not shown")
            return false
        }
        val d = try { build().also { it.show() } } catch (e: RuntimeException) { // BadTokenException: cửa sổ host đã mất
            LogUtil.logE("ads rwi intro: show failed ${e.javaClass.simpleName}")
            return false
        }
        dialog = d
        active = this
        (activity as? LifecycleOwner)?.lifecycle?.addObserver(hostObserver)
        handler.postDelayed(tick, TICK_MS)
        LogUtil.logD("ads rwi intro shown countdown=${COUNTDOWN_SECONDS}s")
        return true
    }

    private fun build(): Dialog {
        val pad = (PADDING_DP * activity.resources.displayMetrics.density).toInt()
        val title = TextView(activity).apply {
            text = activity.getString(R.string.appplugin_rwi_title)
            textSize = TITLE_SP
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        val counter = TextView(activity).apply { gravity = Gravity.CENTER; setPadding(0, pad / 2, 0, pad / 2) }
        countdown = counter
        val skip = Button(activity).apply {
            text = activity.getString(R.string.appplugin_rwi_skip)
            setOnClickListener { finish(Outcome.SKIP) }
        }
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            listOf(title, counter, skip).forEach { addView(it) }
        }
        updateCountdown()
        return Dialog(activity).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(root)
            setCancelable(true)
            setCanceledOnTouchOutside(false)
            setOnCancelListener { finish(Outcome.SKIP) }
        }
    }

    private fun updateCountdown() {
        countdown?.text = activity.getString(R.string.appplugin_rwi_countdown, remaining)
    }

    private fun finish(outcome: Outcome) {
        if (finished) return
        finished = true
        handler.removeCallbacks(tick)
        (activity as? LifecycleOwner)?.lifecycle?.removeObserver(hostObserver)
        if (active === this) active = null
        dialog?.let { d ->
            d.setOnCancelListener(null)
            try { d.dismiss() } catch (e: RuntimeException) { LogUtil.logE("ads rwi intro: dismiss ${e.javaClass.simpleName}") }
        }
        dialog = null; countdown = null
        LogUtil.logD("ads rwi intro closed: $outcome")
        when (outcome) {
            Outcome.CONTINUE -> listener.onContinue()
            Outcome.SKIP -> listener.onSkip()
            Outcome.HOST_GONE -> listener.onHostGone()
        }
    }

    companion object {
        const val COUNTDOWN_SECONDS = 5
        private const val TICK_MS = 1_000L
        private const val PADDING_DP = 24
        private const val TITLE_SP = 18f

        /** Intro đang mở (tối đa một); `null` sau mọi lối ra. */
        @Volatile private var active: RwiIntroDialog? = null

        val isShowing: Boolean get() = active != null

        /** Mở intro trên [activity]; `false` khi host đang kết thúc hoặc cửa sổ không mở được (không callback nào). */
        fun present(activity: Activity, listener: Listener): Boolean = RwiIntroDialog(activity, listener).open()

        /** Host bị huỷ (`clearActivity`): đóng intro đang mở → [Listener.onHostGone]; không có intro thì không làm gì. */
        fun dismissActive() {
            active?.finish(Outcome.HOST_GONE)
        }
    }
}
