package com.app.plugin.ads

import com.app.plugin.ads.core.OneShotLoadCb
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

public class AdsPlacementConfigManager {

    private var plConfigs: MutableMap<String, AdsPlacementConfig> = mutableMapOf()

    /** 2.4.2 SWITCH (D1-242): công tắc theo nguồn (Remote Config, Firestore) của `cf_ads_placement`. */
    internal val switches: PlacementSwitchState = PlacementSwitchState(this)

    /** Entry đúng tên [placement], không tạo (khác [getConfig]); `null` khi chưa có. */
    internal fun peekConfig(placement: String): AdsPlacementConfig? = plConfigs[placement]

    public fun getConfig(placement: String, flagDf: Int): AdsPlacementConfig? {
        if (plConfigs.containsKey(placement)) {
            return plConfigs[placement]
        } else {
            val cf = AdsPlacementConfig()
            cf.placement = placement
            cf.flagShow = flagDf
            plConfigs[placement] = cf
            return cf
        }
    }

    /**
     * Host bị huỷ (Task I5, G9): bỏ `cbShow` của mọi placement không đang hiện — callback của app thường giữ Activity
     * cũ, và không lượt show nào còn chờ gọi nó (vòng show chạy đồng bộ; lượt hiện xong đã callback). Placement đang
     * hiện (vd interstitial GMA sống qua xoay) giữ callback để dismiss vẫn tới app. Trả số callback đã bỏ.
     */
    internal fun releaseIdleShowCallbacks(): Int {
        var released = 0
        for (cf in plConfigs.values) {
            if (!cf.isShowing && cf.cbShow != null) {
                cf.cbShow = null
                released++
            }
        }
        return released
    }

    /**
     * Host bị huỷ (1.6.0 Task 4, G9 bước rect): bỏ `cbLoad` của mọi placement không đang load — callback load của app
     * thường giữ Activity cũ (heap Task 3: `rect_default.cbLoad → DemoLoadCb.this$0`). Placement đang load giữ callback để
     * request đang chờ vẫn nhận đúng một callback kết thúc. Gán qua setter (`cbLoadCycle` = lượt hiện tại; vô hại vì
     * callback là `null`). Trả số callback đã bỏ.
     */
    internal fun releaseIdleLoadCallbacks(): Int {
        var released = 0
        for (cf in plConfigs.values) {
            if (!cf.isLoading && cf.cbLoad != null) {
                cf.cbLoad = null
                released++
            }
        }
        return released
    }

    /**
     * Host bị huỷ (1.6.0 Task 5, review Task 4 I1): bọc `cbLoad` của placement đang load để lượt đó chuyển đúng một callback
     * kết thúc rồi bỏ tham chiếu. Gán qua setter: `cbLoadCycle` = lượt đang chạy — vòng đang chạy vẫn hiện hành; sau
     * `load timeout` (`loadCycle` tăng) `onLoaded` muộn vẫn bị chặn như trước. Trả số callback đã bọc.
     */
    internal fun wrapInFlightLoadCallbacks(): Int {
        var wrapped = 0
        for (cf in plConfigs.values) {
            val cur = cf.cbLoad ?: continue
            if (cf.isLoading && cur !is OneShotLoadCb) {
                cf.cbLoad = OneShotLoadCb(cur)
                wrapped++
            }
        }
        return wrapped
    }

    public fun parserConfig(data: String) {
        if (data.length > 5) {
            val gson = Gson()
            val mapType = object : TypeToken<Map<String, Any>>() {}.type
            val mapAdsCf: Map<String, Any> = try {
                gson.fromJson(data, mapType)
            } catch (e: Exception) {
                return
            }
            if (mapAdsCf.isNotEmpty()) {
                for (plcf in mapAdsCf) {
                    var arrkey = plcf.key.split(",")
                    val mapcf:Map<String, Any> = plcf.value as? Map<String, Any> ?: continue
                    for (ikey in arrkey) {
                        var adcf: AdsPlacementConfig?
                        if (plConfigs.containsKey(ikey)) {
                            adcf = plConfigs[ikey]
                        } else {
                            adcf = AdsPlacementConfig()
                            plConfigs[ikey] = adcf
                            adcf.placement = ikey
                        }
                        if (mapcf.containsKey("flag")) {
                            adcf?.flagShow = (mapcf["flag"].toString().toDoubleOrNull()?:1).toInt()
                        }
                    }
                }
            }
        }
    }
}