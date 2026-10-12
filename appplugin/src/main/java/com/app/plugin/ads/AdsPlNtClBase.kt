package com.app.plugin.ads

import android.content.Context
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.RelativeLayout
import com.app.plugin.until.LogUtil

internal abstract class AdsPlNtClBase(context: Context, adBase: AdsBase, type: Int, pl: String) :
    AdsPlBaseBn(context, adBase, type, pl) {

    protected var mDensity: Float = 0.0f
    private var mWidthScr: Int = 0
    private var mHeightScr: Int = 0

    protected var mTShowBtClose: Int = 1

    protected var loBtClose: RelativeLayout? = null
    protected var btClose: Button? = null

    /** 1.9.0 Task 3: host giữ ≤ 1 view — gỡ view cũ của host, rồi gắn qua [BannerAttach] (gỡ cha cũ của [adView]). */
    public open fun addToParentView(adView: View) {
        val parentView = AdsHelper.instance.createAndAddNtClParentView() ?: return
        getInfoView()
        val layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, (45 * mHeightScr / 100))
        layoutParams.gravity = Gravity.BOTTOM or Gravity.END or Gravity.CENTER_HORIZONTAL
        layoutParams.bottomMargin = 0
        adView.layoutParams = layoutParams
        parentView.removeAllViews()
        BannerAttach.attach(parentView, adView)
        LogUtil.logD("ntcl attach children=${parentView.childCount}")
    }

    protected fun getInfoView() {
        if (mDensity <= 0) {
            val dpmt: DisplayMetrics = mContext.getResources().getDisplayMetrics()
            mDensity = dpmt.density
            mWidthScr = dpmt.widthPixels
            mHeightScr = dpmt.heightPixels
        }
    }

    public fun afterClick() {
        if (btClose != null) {
            val lp = btClose!!.layoutParams as? RelativeLayout.LayoutParams
            lp?.width = (24 * mDensity).toInt()
            lp?.height = (24 * mDensity).toInt()
            btClose!!.layoutParams = lp
        }
    }
}