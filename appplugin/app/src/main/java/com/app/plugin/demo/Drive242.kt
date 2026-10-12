package com.app.plugin.demo

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.app.plugin.MonetizationSdk
import com.app.plugin.ads.AdsHelper
import com.app.plugin.ads.AdsLoadCb
import com.app.plugin.ads.AdsShowCb
import com.app.plugin.ads.PlacementDefault
import com.app.plugin.ads.adsmob.AdsMobMy

/**
 * 2.4.2 DEMO242: drive `--es drive <action>` cho hành vi mới (chỉ API public). Mỗi dòng log `Log.i` bắt đầu
 * `AppPluginDemo drive242 <action> ...` (Task R grep). Chỉ chạy khi `flagTest` (cổng ở MainActivity.runDrive).
 */
internal object Drive242 {
    private const val TAG = "AppPluginDemo"
    private const val OPEN_TEST_ID = "ca-app-pub-3940256099942544/9257395921"
    private const val ADMOB_FULL_OPEN = 100 // = AdsIDDef.AdmobFullOpen (AdsIDDef là internal trong SDK)
    private const val DEFAULT_SLOT = "full_default"
    private const val FIXED_START_TIME = 600
    private const val LOAD_WAIT_MS = 15_000L
    private val main = Handler(Looper.getMainLooper())

    fun run(activity: Activity, action: String) {
        when (action) {
            "slot_own_empty" -> slotOwnEmpty()
            "fixed_intro3" -> fixedIntro3()
            "switch_off" -> switchPlacement(activity, """{"full_clean_done":{"flag":-1}}""")
            "switch_clear" -> switchPlacement(activity, "")
            "clear_data" -> clearData(activity)
            else -> say(action, "unknown action")
        }
    }

    private fun say(action: String, msg: String) { Log.i(TAG, "drive242 $action $msg") }

    private fun slotList() = mapOf("list" to OPEN_TEST_ID)

    /**
     * Slot mặc định có quảng cáo, slot riêng `full_splash` rỗng: `showOpenAd` phải hiện từ slot mặc định (SLOT).
     * Bước 1 chờ lượt nạp khởi động của slot riêng xong (tránh đua), bước 2 bỏ slot riêng, nạp vào slot mặc định,
     * bước 3 tạo lại slot riêng rỗng bằng `parSerConfig` rồi `showOpenAd`.
     */
    private fun slotOwnEmpty() {
        val net = AdsMobMy.instance.getAdsFull(ADMOB_FULL_OPEN)
        if (net == null) { say("slot_own_empty", "no open network"); return }
        say("slot_own_empty", "step1: settle startup load of own slot")
        AdsHelper.instance.loadOpenAdForThisTurn(PlacementDefault.PLFullSplash, loadCb("slot_own_empty") {
            net.mapAdsPl.remove(PlacementDefault.PLFullSplash) // bỏ slot riêng cũ (đã nạp), tạo lại rỗng ở bước 3
            net.parSerConfig(mapOf(DEFAULT_SLOT to slotList()), false)
            say("slot_own_empty", "step2: own removed, default slot ready, loading full_splash into default")
            AdsHelper.instance.loadOpenAdForThisTurn(PlacementDefault.PLFullSplash, loadCb("slot_own_empty") { originPl ->
                net.parSerConfig(mapOf(PlacementDefault.PLFullSplash to slotList()), false)
                say("slot_own_empty", "step3: loaded originPl=$originPl ownRecreatedEmpty=true")
                val shown = AdsHelper.instance.showOpenAd(PlacementDefault.PLFullSplash, cb("slot_own_empty"))
                say("slot_own_empty", "showOpenAd returned=$shown")
            })
        })
    }

    private fun loadCb(label: String, onLoaded: (String) -> Unit) = object : AdsLoadCb {
        override fun onLoaded(placement: String, originPl: String, adId: String) = onLoaded(originPl)
        override fun onLoadFailed(placement: String, originPl: String, adId: String, error: String) {
            say(label, "load failed: $error")
        }
    }

    /** `fullStartTime = 600` (đặt SAU khi nạp xong, vì nạp cũng bị cửa sổ `fullStartTime / 3` chặn): lượt thường bị chặn, lượt `fixedMoment` hiện rồi nối Full2. */
    private fun fixedIntro3() {
        val ads = AdsHelper.instance
        ads.currConfig.fullStartTime = 0
        ads.currConfig.full2FlagShow = 1
        enableIntro3(ads)
        say("fixed_intro3", "full2FlagShow=1, loading full_intro_3 + Full2")
        ads.loadInterstitialForThisTurn(PlacementDefault.PLFullIntro3, 0, loadCb("fixed_intro3") { originPl ->
            say("fixed_intro3", "loaded originPl=$originPl")
            // Full2 nạp song song; chờ để lớp che hiện được ngay khi nối.
            main.postDelayed({ fixedShows(ads) }, LOAD_WAIT_MS)
        })
    }

    /** RC của project demo có thể đặt `full_intro_3` flag -1 (SWITCH tôn trọng); driver bật lại để đo FIXED, ghi rõ trạng thái trước. */
    private fun enableIntro3(ads: AdsHelper) {
        val before = ads.isPlacementEnabled(PlacementDefault.PLFullIntro3)
        ads.parserAdsConfig("""{"full_intro_3":{"flag":1},"intro_3":{"flag":1}}""")
        say("fixed_intro3", "isPlacementEnabled(full_intro_3) before=$before after=${ads.isPlacementEnabled(PlacementDefault.PLFullIntro3)}")
    }

    private fun fixedShows(ads: AdsHelper) {
        enableIntro3(ads)
        // RC fetch có thể ghi đè currConfig giữa lúc nạp và show: đặt lại ngay trước khi show.
        ads.currConfig.fullStartTime = FIXED_START_TIME
        ads.currConfig.full2FlagShow = 1
        val ready = ads.isInterstitialReadyToShow(PlacementDefault.PLFullIntro3, 0, true, false)
        val readyFixed = ads.isInterstitialReadyToShow(PlacementDefault.PLFullIntro3, 0, true, true)
        say("fixed_intro3", "fullStartTime=$FIXED_START_TIME full2FlagShow=${ads.currConfig.full2FlagShow} ready normal=$ready fixed=$readyFixed")
        val normal = ads.showInterstitial(PlacementDefault.PLFullIntro3, 0, cb("fixed_intro3 normal"))
        say("fixed_intro3", "normal show returned=$normal")
        val fixed = ads.showInterstitial(PlacementDefault.PLFullIntro3, 0, cb("fixed_intro3 fixed"), true, true)
        say("fixed_intro3", "fixedMoment show returned=$fixed")
    }

    private fun switchPlacement(activity: Activity, json: String) {
        val action = if (json.isEmpty()) "switch_clear" else "switch_off"
        val ads = AdsHelper.instance
        ads.parserAdsConfig(json)
        say(action, "parserAdsConfig json='$json' isPlacementEnabled(full_clean_done)=${ads.isPlacementEnabled("full_clean_done")}")
        ads.loadInterstitialForThisTurn("clean_done", 0, object : AdsLoadCb {
            override fun onLoaded(placement: String, originPl: String, adId: String) =
                say(action, "load clean_done loaded originPl=$originPl")
            override fun onLoadFailed(placement: String, originPl: String, adId: String, error: String) =
                say(action, "load clean_done failed: $error")
        })
        val shown = ads.showInterstitial("clean_done", 0, cb(action), false)
        say(action, "show clean_done returned=$shown enabled=${ads.isPlacementEnabled("full_clean_done")}")
    }

    private fun clearData(activity: Activity) {
        MonetizationSdk.clearLocalData(activity.applicationContext)
        say("clear_data", "clearLocalData called")
    }

    private fun cb(label: String) = object : AdsShowCb {
        override fun onAdShowed(placement: String?, originPl: String?, adId: String?, flag: Int) = say(label, "showed pl=$placement origin=$originPl")
        override fun onAdImpression(placement: String?, originPl: String?, adId: String?) = say(label, "impression pl=$placement")
        override fun onAdDismissed(placement: String?, originPl: String?, adId: String?) = say(label, "dismissed pl=$placement")
        override fun onAdShowFail(placement: String?, originPl: String?, adId: String?, err: String?) = say(label, "show fail pl=$placement: $err")
    }
}
