package com.app.plugin.ads

import android.app.Activity
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import com.app.plugin.until.LogUtil
import java.lang.ref.WeakReference

/**
 * Cover used for the interstitial/rewarded -> Full2 hand-off when the app registers no
 * [Full2TransitionListener] of its own.
 *
 * Without any cover the host's own screen shows for the frames between the parent ad closing and
 * the Full2 dialog appearing, which reads as two separate ads. speakerclean draws this cover in
 * Compose; apps without one (LAM2026) got the flash. This is the same behaviour as a plain View:
 * black to match the native-full dialog, and touch-consuming so nothing underneath is tapped
 * mid hand-off.
 *
 * It must never lock the app: besides the SDK's own transition timeouts, it clears itself
 * [maxVisibleWhileResumedMillis] after the host is back in front - the clock does not run while
 * the parent ad covers the host, since that can legitimately last much longer.
 */
internal class Full2DefaultCover(
    private val maxVisibleWhileResumedMillis: Long = MAX_VISIBLE_WHILE_RESUMED_MS,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var coverView: View? = null
    private var coverParent: WeakReference<ViewGroup>? = null
    private val deadline = Runnable {
        LogUtil.logD("full2 default cover deadline reached while host resumed; clearing")
        hide()
    }

    fun show(activity: Activity?, hostResumed: Boolean) {
        if (coverView != null) {
            if (hostResumed) armDeadline()
            return
        }
        if (activity == null || activity.isFinishing || activity.isDestroyed) return
        val parent = activity.window?.decorView as? ViewGroup ?: return
        try {
            val view = View(activity).apply {
                setBackgroundColor(Color.BLACK)
                isClickable = true
                isFocusable = true
            }
            parent.addView(
                view,
                ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            )
            coverView = view
            coverParent = WeakReference(parent)
            if (hostResumed) armDeadline()
        } catch (e: Throwable) {
            // A cover is cosmetic; failing to add one must never cost the ad or crash the host.
            LogUtil.logD("full2 default cover skipped: ${e.message}")
        }
    }

    fun hide() {
        handler.removeCallbacks(deadline)
        val view = coverView ?: return
        coverView = null
        runCatching { coverParent?.get()?.removeView(view) }
        coverParent = null
    }

    fun onHostResumed() {
        if (coverView != null) armDeadline()
    }

    fun onHostPaused() {
        handler.removeCallbacks(deadline)
    }

    private fun armDeadline() {
        handler.removeCallbacks(deadline)
        handler.postDelayed(deadline, maxVisibleWhileResumedMillis)
    }

    companion object {
        /** Same value speakerclean uses for its Compose cover (FULL2_COVER_MAX_VISIBLE_MS). */
        const val MAX_VISIBLE_WHILE_RESUMED_MS = 2_000L
    }
}
