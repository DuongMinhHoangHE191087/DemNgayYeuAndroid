package com.app.plugin.until

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.res.Configuration
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.LayoutRes
import androidx.core.view.doOnLayout

/**
 * Forces native full ad dialogs to render in portrait orientation visually,
 * regardless of the host activity's actual orientation.
 *
 * Strategy: when the activity is landscape, inflate the layout from a portrait-configured
 * Context (so layout-land/ qualifiers are bypassed) and apply a 90-degree View.rotation
 * to the inflated tree so it visually fills the landscape window as if it were portrait.
 *
 * Activity orientation is left untouched, so this does not interfere with screen-level
 * orientation locks elsewhere in the app (e.g. Compose LockOrientation on Home).
 */
internal object NtFullPortraitHelper {

    fun createPortraitContext(base: Context): Context {
        val newConfig = Configuration(base.resources.configuration).apply {
            orientation = Configuration.ORIENTATION_PORTRAIT
            if (screenWidthDp > screenHeightDp) {
                val tmp = screenWidthDp
                screenWidthDp = screenHeightDp
                screenHeightDp = tmp
            }
        }
        return base.createConfigurationContext(newConfig)
    }

    /**
     * Inflate [layoutId] and attach to [dialog] so it always appears portrait.
     *
     * @return the portrait-configured Context used to inflate the layout. Callers that
     * need to inflate additional sub-views (e.g. MAX's MaxNativeAdView) should reuse it
     * so layout-land qualifiers are bypassed there too.
     */
    fun setPortraitContentView(dialog: Dialog, @LayoutRes layoutId: Int): Context {
        val baseContext = dialog.context
        val portraitContext = createPortraitContext(baseContext)
        val content = LayoutInflater.from(portraitContext).inflate(layoutId, null)

        val activity = baseContext as? Activity
        val isLandscape =
            activity?.resources?.configuration?.orientation == Configuration.ORIENTATION_LANDSCAPE

        if (!isLandscape) {
            dialog.setContentView(content)
            return portraitContext
        }

        val wrapper = FrameLayout(baseContext).apply {
            clipChildren = false
            clipToPadding = false
        }

        dialog.setContentView(
            wrapper,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        // Choose rotation direction based on physical screen rotation so the ad reads upright.
        // activity is non-null here: isLandscape is only true when activity != null (see above).
        val contentRotation: Float = when (activity!!.windowManager.defaultDisplay.rotation) {
            Surface.ROTATION_90 -> -90f
            Surface.ROTATION_270 -> 90f
            else -> 90f
        }

        // Size the (portrait) content from the wrapper's actual laid-out size — not from
        // displayMetrics. displayMetrics excludes navigation bar / cutout insets while the
        // dialog window may include them, and that mismatch produced asymmetric padding
        // (visible blank strip on one side) when the device was landscape.
        wrapper.doOnLayout { laidOutWrapper ->
            val portraitW = laidOutWrapper.height
            val portraitH = laidOutWrapper.width
            val params = FrameLayout.LayoutParams(portraitW, portraitH).apply {
                gravity = Gravity.CENTER
            }
            wrapper.addView(content, params)
            content.rotation = contentRotation
        }
        return portraitContext
    }
}
