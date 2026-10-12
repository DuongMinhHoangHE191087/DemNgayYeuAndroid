package com.app.plugin.ads

import android.content.Context
import com.app.plugin.until.LogUtil

internal abstract class AdsPlBaseBn(context: Context, adBase: AdsBase, type: Int, pl: String) :
    AdsPlBase(context, adBase, type, pl) {
    public open fun hideAd(flagHide:Int) {}

    /**
     * Response đầu tiên tới muộn của một request banner/rect đã bị bỏ ([isStaleLoadCallback], Task J1). [loaded]:
     * đưa [view] qua [keepLateAd] (mục rảnh và trống thì giữ làm quảng cáo đã load; không thì destroy). Lỗi: destroy
     * [view] (MaxAdView tự retry, AdView của GMA/Meta có thể refresh — không để view không ai giữ tiếp tục callback).
     *
     * @return `true` khi [view] không được giữ: listener bỏ mọi callback sau của view đó (refresh/retry); `false` khi
     *   view đã thành quảng cáo đã load của mục, refresh của nó đi đường "refresh"/"not turn" như view thường.
     */
    /**
     * Final review Minor 2: view đã lấy ra hiện (`getAdForShow` bỏ `adObject`) tự refresh lúc slot rảnh thì nhánh
     * "refresh"/"not turn" gán nó vào mục trống mà không đặt `isLoaded` (`adObject = A`, `isLoaded = false`). Tham chiếu
     * đó không sở hữu A — helper giữ A trong container của app — và không ai dùng (`getAdLoaded` bỏ qua mục chưa load),
     * nhưng nếu còn ở mục khi app load lại thì `getAdLoaded` báo `LOAD_FAIL_BUT_ADS_NOT_NULL` giả, và view B của request
     * mới tới muộn sau watchdog bị [keepLateAd] destroy vì mục "đã có" A. Lúc [load] bắt đầu (slot rảnh) bỏ tham chiếu
     * đó khỏi mục; **không** destroy A (đang hiện, vòng đời do helper/app quản). Trong lúc request chờ, refresh không gán
     * vào mục (Task M mục 2), nên mục của request giữ trống tới khi request kết thúc.
     */
    override fun releaseUnloadedItemRefs() {
        adsPlacementEcmp.adEcmps?.list?.forEach { item ->
            if (!item.isLoaded && !item.isLoading && item.adObject != null) {
                LogUtil.logD("ads $logTag ${adsPlacementEcmp.originPl} refreshed view ref released before new load id=${item.adsId}")
                item.adObject = null
            }
        }
    }

    /**
     * Mục đã load để `getAdForShow` của rect lấy ra hiện (final review Minor 6, 1.5.0 Task 15). Trước khi xét, bỏ tham
     * chiếu view refresh đang "ngồi" mục ([releaseUnloadedItemRefs], như lúc [load] bắt đầu): view đã lấy ra hiện tự
     * refresh lúc slot rảnh gán nó vào mục trống mà không đặt `isLoaded`, và `AdsEcmps.getAdLoaded` gặp mục đó thì phát
     * `LOAD_FAIL_BUT_ADS_NOT_NULL` giả. Không destroy view đó (đang hiện, vòng đời do helper/app quản).
     */
    internal fun loadedAdForShow(): AdsEcmp? {
        releaseUnloadedItemRefs()
        return adsPlacementEcmp.adEcmps?.getAdLoaded()
    }

    internal fun orphanLateView(ecmp: AdsEcmp, view: Any?, loaded: Boolean): Boolean {
        if (loaded) {
            keepLateAd(ecmp, view)
            return ecmp.adObject !== view
        }
        destroyAd(view)
        return true
    }
}
