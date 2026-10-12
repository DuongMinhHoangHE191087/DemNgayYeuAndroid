package com.app.plugin.ads.adsmob

import android.app.Activity
import android.os.Build
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.RelativeLayout
import androidx.annotation.RequiresApi
import com.app.plugin.ads.BannerAttach

/**
 * Lớp phủ đen vùng safe-area (tai thỏ) sau dialog native full AdMob. 2.0.0 C1a (D1): chuyển nguyên văn từ
 * `AdsMobPlNtFull` (trường + `addBg4Ad`/`hideBlackSafeAreaView`); mỗi placement giữ một thể hiện.
 */
internal class AdsMobNtFullBackground {
    private var parentBlackSafeAreaView: ViewGroup? = null
    private var blackSafeTop: View? = null
    private var blackSafeBottom: View? = null
    private var blackSafeLeft: View? = null
    private var blackSafeRight: View? = null
    private var sSafeTop = -1
    private var sSafeBottom: Int = -1
    private var sSafeLeft: Int = -1
    private var sSafeRight: Int = -1

    @RequiresApi(api = Build.VERSION_CODES.P)
    fun addBg4Ad(ac: Activity?) {
        if (sSafeTop < 0) {
            val lp = ac!!.window.attributes
            lp.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            val windowInsets = ac.window.decorView.rootView.rootWindowInsets
            if (windowInsets != null) {
                val displayCutout = windowInsets.displayCutout
                if (displayCutout != null) {
                    sSafeTop = displayCutout.safeInsetTop
                    sSafeBottom = displayCutout.safeInsetBottom
                    sSafeLeft = displayCutout.safeInsetLeft
                    sSafeRight = displayCutout.safeInsetRight
                    Log.d("mysdk", "top=$sSafeTop")
                    Log.d("mysdk", "bottom=$sSafeBottom")
                    Log.d("mysdk", "left=$sSafeLeft")
                    Log.d("mysdk", "right=$sSafeRight")
                }
            }
        }
        if (parentBlackSafeAreaView == null && ac != null) {
            parentBlackSafeAreaView = RelativeLayout(ac)
            val bpl = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
            ac.addContentView(parentBlackSafeAreaView, bpl)
        }
        if (parentBlackSafeAreaView != null) {
            parentBlackSafeAreaView!!.visibility = View.VISIBLE
            if (sSafeTop > 0) {
                if (blackSafeTop == null) {
                    blackSafeTop = View(ac)
                    val cpr =
                        RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, sSafeTop)
                    cpr.addRule(
                        RelativeLayout.ALIGN_PARENT_TOP, RelativeLayout.TRUE
                    )
                    parentBlackSafeAreaView!!.addView(blackSafeTop, cpr)
                    blackSafeTop!!.setBackgroundColor(-0x1000000)
                }
                blackSafeTop!!.visibility = View.VISIBLE
            } else {
                if (blackSafeTop != null) {
                    blackSafeTop!!.visibility = View.INVISIBLE
                }
            }
            if (sSafeBottom > 0) {
                if (blackSafeBottom == null) {
                    blackSafeBottom = View(ac)
                    val cpr = RelativeLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, sSafeBottom
                    )
                    cpr.addRule(
                        RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE
                    )
                    parentBlackSafeAreaView!!.addView(blackSafeBottom, cpr)
                    blackSafeBottom!!.setBackgroundColor(-0x1000000)
                }
                blackSafeBottom!!.visibility = View.VISIBLE
            } else {
                if (blackSafeBottom != null) {
                    blackSafeBottom!!.visibility = View.INVISIBLE
                }
            }
            if (sSafeLeft > 0) {
                if (blackSafeLeft == null) {
                    blackSafeLeft = View(ac)
                    val cpr =
                        RelativeLayout.LayoutParams(sSafeLeft, ViewGroup.LayoutParams.MATCH_PARENT)
                    cpr.addRule(
                        RelativeLayout.ALIGN_PARENT_LEFT, RelativeLayout.TRUE
                    )
                    parentBlackSafeAreaView!!.addView(blackSafeLeft, cpr)
                    blackSafeLeft!!.setBackgroundColor(-0x1000000)
                }
                blackSafeLeft!!.visibility = View.VISIBLE
            } else {
                if (blackSafeLeft != null) {
                    blackSafeLeft!!.visibility = View.INVISIBLE
                }
            }
            if (sSafeRight > 0) {
                if (blackSafeRight == null) {
                    blackSafeRight = View(ac)
                    val cpr =
                        RelativeLayout.LayoutParams(sSafeRight, ViewGroup.LayoutParams.MATCH_PARENT)
                    cpr.addRule(
                        RelativeLayout.ALIGN_PARENT_RIGHT, RelativeLayout.TRUE
                    )
                    parentBlackSafeAreaView!!.addView(blackSafeRight, cpr)
                    blackSafeRight!!.setBackgroundColor(-0x1000000)
                }
                blackSafeRight!!.visibility = View.VISIBLE
            } else {
                if (blackSafeRight != null) {
                    blackSafeRight!!.visibility = View.INVISIBLE
                }
            }
        }
    }

    /**
     * Gỡ lớp phủ safe-area khỏi content của host và bỏ mọi tham chiếu tới nó: lớp phủ được dựng
     * bằng context của Activity, giữ lại qua nhiều lần hiện thì giữ luôn Activity đầu tiên (heap
     * 2026-09-24) và sau khi xoay màn hình addBg4Ad dùng lại view của Activity đã chết. Lần hiện
     * sau addBg4Ad dựng lại trên host hiện tại.
     */
    fun hideBlackSafeAreaView() {
        val overlay = parentBlackSafeAreaView ?: return
        overlay.visibility = View.INVISIBLE
        BannerAttach.detach(overlay)
        parentBlackSafeAreaView = null
        blackSafeTop = null
        blackSafeBottom = null
        blackSafeLeft = null
        blackSafeRight = null
    }
}
