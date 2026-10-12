package com.app.plugin.demo

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.app.plugin.ads.AdsGiftShowCb
import com.app.plugin.ads.AdsHelper
import com.app.plugin.ads.AdsLoadCb
import com.app.plugin.ads.PlacementDefault

/**
 * Phần demo của 1.9.0 Task 10 (tách khỏi MainActivity để giữ trần dòng): rewarded interstitial (`rwi_default`), banner
 * collapsible (`bn_collapsible`, khai ở `ads_collapsible_placements`) và NtCl show/hide ×3. Các nút này là điểm chạm của
 * `tools/lib/measure_gate.sh` ("Show Rewarded Interstitial") và `tools/lib/banners_gate.sh` (hai nút còn lại); chữ nút là hợp đồng với tool.
 */
private const val TAG = "AppPluginDemo"
private const val RWI = "rwi_default"
private const val BN_COLLAPSIBLE = "bn_collapsible"
private const val NTCL_ROUNDS = 3
private const val NTCL_STEP_MS = 3_000L

/** Nạp sẵn rewarded interstitial cho lượt đầu (như App Open ở `initAppPlugin`): cổng đo chạm Show đúng một lần có quảng cáo. */
internal fun preloadRwi() {
    AdsHelper.instance.loadRewardedForThisTurn(RWI)
}

// Hàm click của nút "Show Rewarded Interstitial"; MainActivity.runDrive (`--es drive show_rwi`, 2.4.0 FIX-GATE 4) gọi đúng hàm này.
internal fun clickShowRwi(status: TextView) {
    fun say(msg: String) { Log.i(TAG, msg); status.text = msg }
    val shown = AdsHelper.instance.showRewarded(RWI, object : AdsGiftShowCb {
        override fun onAdShowed(placement: String?, originPl: String?, adId: String?, flag: Int) = say("[rwi] showed pl=$placement")
        override fun onAdDismissed(placement: String?, originPl: String?, adId: String?) = say("[rwi] dismissed pl=$placement")
        override fun onAdShowFail(placement: String?, originPl: String?, adId: String?, err: String?) = say("[rwi] show fail pl=$placement: $err")
        override fun onAdRewarded(placement: String?, originPl: String?, adId: String?) = say("[rwi] REWARDED pl=$placement")
    })
    if (!shown) say("[rwi] show returned false (chưa nạp hoặc bị chặn; SDK nạp lại cho lượt sau)")
}

internal fun addRwiBannerSections(root: LinearLayout, bannerContainer: FrameLayout, status: TextView) {
    val ctx = root.context
    val main = Handler(Looper.getMainLooper())
    fun say(msg: String) { Log.i(TAG, msg); status.text = msg }

    root.addView(TextView(ctx).apply { text = "\n--- REWARDED INTERSTITIAL ---" })
    root.addView(Button(ctx).apply {
        text = "Load Rewarded Interstitial"
        setOnClickListener {
            AdsHelper.instance.loadRewardedForThisTurn(RWI, object : AdsLoadCb {
                override fun onLoaded(placement: String, originPl: String, adId: String) = say("[rwi load] loaded pl=$originPl")
                override fun onLoadFailed(placement: String, originPl: String, adId: String, error: String) = say("[rwi load] fail pl=$originPl: $error")
            })
        }
    })
    root.addView(Button(ctx).apply {
        text = "Show Rewarded Interstitial"
        setOnClickListener { clickShowRwi(status) }
    })

    // Banner collapsible: cùng container với banner thường; request đầu của slot mang extras collapsible=bottom (CollapsibleBanner).
    root.addView(Button(ctx).apply {
        text = "Show Banner Collapsible"
        setOnClickListener {
            AdsHelper.instance.showBanner(BN_COLLAPSIBLE, bannerContainer, isCollapse = false, cbLoad = null, cbShow = null)
        }
    })
    // NtCl show/hide ×3: mỗi vòng show (isDeltaTime = false) rồi hide sau NTCL_STEP_MS; log 'ntcl attach children=…' do SDK ghi (AdsPlNtClBase).
    root.addView(Button(ctx).apply {
        text = "NtCl show/hide x3"
        setOnClickListener {
            say("ntcl show/hide x3 start")
            for (i in 0 until NTCL_ROUNDS) {
                main.postDelayed({
                    AdsHelper.instance.showBanner(PlacementDefault.PLBnDefault, bannerContainer, isCollapse = true, cbLoad = null, cbShow = null, isDeltaTime = false)
                }, i * 2 * NTCL_STEP_MS)
                main.postDelayed({ AdsHelper.instance.hideNativeCl() }, (i * 2 + 1) * NTCL_STEP_MS)
            }
        }
    })
}
