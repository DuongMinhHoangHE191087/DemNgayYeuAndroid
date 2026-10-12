package com.app.plugin.firbase

import android.util.Log
import com.app.plugin.BuildConfig
import com.app.plugin.SdkKillSwitch
import com.app.plugin.ads.AdsHelper
import com.app.plugin.ads.AdsIDDef
import com.app.plugin.ads.AdsPolicy
import com.app.plugin.ads.PlacementSource
import com.app.plugin.ads.adsmob.AdsMobMy
import com.app.plugin.ads.core.BiddingPartners
import com.app.plugin.app.AppPluginManager
import com.app.plugin.logevent.EventNames
import com.app.plugin.logevent.LogEventControl
import com.app.plugin.logevent.LogEventServer
import com.app.plugin.logevent.ScreenName
import com.app.plugin.until.LogUtil
import com.google.firebase.Firebase
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfigSettings
import com.google.firebase.remoteconfig.remoteConfig
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

public class FirHelperConfig {

    private val remoteConfig: FirebaseRemoteConfig by lazy { Firebase.remoteConfig }
    private val fireStore: FirebaseFirestore by lazy { Firebase.firestore }
    private val configExecutor by lazy { java.util.concurrent.Executors.newSingleThreadExecutor() }
    private val mainHandler by lazy { android.os.Handler(android.os.Looper.getMainLooper()) }
    private var fetchCgCb: FirHelperConfigListenner? = null

    public fun setFetchCgListenner(listenner: FirHelperConfigListenner) {
        fetchCgCb = listenner
    }

    public fun fetchRemoteConfig(onFetchSuccess: () -> Unit) {
        val configSettings = remoteConfigSettings {
            // NEW-05 FIX: 10s chỉ dùng cho debug. Production dùng 3600s (1 giờ)
            // để tránh vượt quota Firebase Remote Config → config bị throttle
            minimumFetchIntervalInSeconds = if (BuildConfig.DEBUG) 10L else 3600L
        }
        LogUtil.logD("fir fetchConfig")
        remoteConfig.setConfigSettingsAsync(configSettings)
        // The values are read and JSON-parsed on a background thread: getString() can block on
        // the activated-config disk cache, and together with the Gson parsing this was a
        // main-thread ANR on slow devices (Crashlytics, speakerclean 1.0.18). Applying them still happens
        // on the main thread, in the original order, because it mutates ad state that only
        // the main thread touches.
        remoteConfig.fetchAndActivate().addOnCompleteListener(configExecutor) { task ->
            val tfeted: Long = System.currentTimeMillis() - AdsHelper.instance.timeStartFet
            if (task.isSuccessful) runCatching { LogEventControl.applyRemote(AppPluginManager.instance.getContext(), remoteConfig.getString(LogEventControl.REMOTE_KEY)) }.onFailure { LogUtil.logE("logevent_control: áp lỗi: ${it.message}") }
            // 1.9.0 T1 (Hợp đồng A): bidding partner trên luồng config, luôn áp (cả chuỗi rỗng) và lưu; RC chỉ thu hẹp resource.
            if (task.isSuccessful) runCatching { BiddingPartners.applyRemote(AppPluginManager.instance.getContext(), remoteConfig.getString(BiddingPartners.REMOTE_KEY)) }.onFailure { LogUtil.logE("ads bidding: áp lỗi: ${it.message}") }
            val snapshot = if (task.isSuccessful) {
                try {
                    readRemoteCf()
                } catch (e: Exception) {
                    LogUtil.logD("FirHelperConfig: reading RemoteConfig failed: ${e.message}")
                    null
                }
            } else {
                null
            }
            mainHandler.post { onRemoteConfigFetched(tfeted, snapshot, onFetchSuccess) }
        }
    }

    private fun onRemoteConfigFetched(tfeted: Long, snapshot: RemoteCfSnapshot?, onFetchSuccess: () -> Unit) {
        Log.d("AppPlugin", "fir fet config t=${tfeted}")
        FirHelper.instance.logEvent(EventNames.remoteConfigFetchBucket(tfeted))
        if (snapshot != null) {
            fetchCgCb?.onFetchConfig(this)
            applyRemoteCf(snapshot)
            LogEventServer.loadFetchConfigTime(1, ScreenName.name)
            onFetchSuccess.invoke()
        } else {
            LogEventServer.loadFetchConfigTime(0, ScreenName.name)
            onFetchSuccess.invoke()
        }
    }

    public fun fetchCfFireStore(onFetchSuccess: () -> Unit) {
        fireStore.collection("ads_config")
            .document("ads_config")
            .get()
            .addOnSuccessListener { documents ->
                parserCfWithFireStore(documents)
                onFetchSuccess.invoke()
                LogEventServer.loadFireStoreTime(1, ScreenName.name)
            }
            .addOnFailureListener {
                onFetchSuccess.invoke()
                LogEventServer.loadFireStoreTime(0, ScreenName.name)
            }
    }

    private fun parserCfWithFireStore(documents: DocumentSnapshot) {
        val gson = Gson()
        val mapType = object : TypeToken<Map<String, Any>>() {}.type

        // NEW-04 FIX: dùng hàm chung thay vì duplicate logic
        val adsRegionString = documents.getString("ads_region_android")
        if (adsRegionString?.isNotEmpty() == true) {
            try {
                val map: Map<String, Any> = gson.fromJson(adsRegionString, mapType)
                if (map.isNotEmpty()) applyAdsRegionConfig(map)
            } catch (e: Exception) {
                LogUtil.logD("FirHelperConfig: Failed to parse ads_region_android (FireStore): ${e.message}")
            }
        }
        val ads2Region2String = documents.getString("ads_region2_android")
        if (ads2Region2String?.isNotEmpty() == true) {
            try {
                val map: Map<String, Any> = gson.fromJson(ads2Region2String, mapType)
                if (map.isNotEmpty()) applyAdsRegion2Config(map)
            } catch (e: Exception) {
                LogUtil.logD("FirHelperConfig: Failed to parse ads_region2_android (FireStore): ${e.message}")
            }
        }
        val adsIdsPlString = documents.getString("cf_ids_placement")
        if (adsIdsPlString?.isNotEmpty() == true) {
            try {
                val map: Map<String, Any> = gson.fromJson(adsIdsPlString, mapType)
                if (map.isNotEmpty()) applyIdsPlacementConfig(map)
            } catch (e: Exception) {
                LogUtil.logD("FirHelperConfig: Failed to parse cf_ids_placement (FireStore): ${e.message}")
            }
        }
        // 2.4.2 SWITCH (D1-242): Firestore là nguồn riêng; rỗng/vắng chỉ áp khi chính nguồn này đã đặt gì đó.
        val adsConfigPlString = documents.getString("cf_ads_placement").orEmpty()
        val fsSwitches = AdsHelper.instance.takeIf { it.hasPlacementConfig() }?.placementsConfigManager?.switches
        if (adsConfigPlString.isNotEmpty() || fsSwitches?.hasRecord(PlacementSource.FIRESTORE) == true) {
            AdsHelper.instance.appContext()?.let { fsSwitches?.apply(it, PlacementSource.FIRESTORE, adsConfigPlString) }
        }
        AdsHelper.instance.saveConfig()
        AdsHelper.instance.checkAfterLoadIdsConfig()
    }

    /**
     * RemoteConfig values, read (and the ad maps parsed) off the main thread; see
     * [fetchRemoteConfig]. The monetization keys stay raw strings: their managers parse and
     * swap state in one apply() call, and that still happens on the main thread.
     */
    private class RemoteCfSnapshot(
        val adsRegion: Map<String, Any>?,
        val adsRegion2: Map<String, Any>?,
        val idsPlacement: Map<String, Any>?,
        val adsPlacement: String,
        val brainPolicy: String,
        val paywall: String,
        val subscriptionLifecycle: String,
        /** Section game (2.0.0 C1d) theo thứ tự đăng ký, kèm giá trị RC đã đọc; `null` = không đọc. */
        val gameSections: List<Pair<RcSectionRegistry.Section, String>>?,
        val sdkKill: String,
        val adsPolicy: String
    )

    private fun readRemoteCf(): RemoteCfSnapshot {
        RcSectionRegistry.ensureGameModule() // 2.0.0 C1d: dò GameModule một lần, sau init RC, trên luồng config
        val gson = Gson()
        val mapType = object : TypeToken<Map<String, Any>>() {}.type
        fun parseMap(key: String): Map<String, Any>? {
            val raw = remoteConfig.getString(key)
            if (raw.isEmpty()) return null
            return try {
                val map: Map<String, Any> = gson.fromJson(raw, mapType)
                map.takeIf { it.isNotEmpty() }
            } catch (e: Exception) {
                LogUtil.logD("FirHelperConfig: Failed to parse $key (RemoteConfig): ${e.message}")
                null
            }
        }
        return RemoteCfSnapshot(
            adsRegion = parseMap("ads_region_android"),
            adsRegion2 = parseMap("ads_region2_android"),
            idsPlacement = parseMap("cf_ids_placement"),
            adsPlacement = remoteConfig.getString("cf_ads_placement"),
            brainPolicy = remoteConfig.getString("brain_policy_android"),
            paywall = remoteConfig.getString("paywall_config_android"),
            subscriptionLifecycle = remoteConfig.getString("subscription_lifecycle_android"),
            gameSections = RcSectionRegistry.sections().map { it to remoteConfig.getString(it.key) },
            sdkKill = remoteConfig.getString(SdkKillSwitch.REMOTE_KEY),
            adsPolicy = remoteConfig.getString(AdsPolicy.REMOTE_KEY)
        )
    }

    private fun applyRemoteCf(snapshot: RemoteCfSnapshot) {
        // Kill switch trước mọi thứ, để lượt reload quảng cáo cuối hàm đã thấy nó. Luôn áp, kể cả chuỗi
        // rỗng: khoá bị gỡ trên console là gỡ kill. Được lưu cho lần mở kế (SdkKillSwitch.restore).
        SdkKillSwitch.applyRemote(AppPluginManager.instance.getContext(), snapshot.sdkKill)
        // Luật tần suất (doc 62 F): cũng luôn áp, kể cả chuỗi rỗng (khoá bị gỡ là về mặc định = hành vi cũ), và
        // được lưu cho lần mở kế (AdsHelper.restorePolicy). JSON hỏng → mặc định + logE (AdsPolicy.parse).
        AdsHelper.instance.applyRemotePolicy(snapshot.adsPolicy)
        // NEW-04 FIX: dùng hàm chung thay vì duplicate logic
        snapshot.adsRegion?.let { applyGuarded("ads_region_android") { applyAdsRegionConfig(it) } }
        snapshot.adsRegion2?.let { applyGuarded("ads_region2_android") { applyAdsRegion2Config(it) } }
        snapshot.idsPlacement?.let { applyGuarded("cf_ids_placement") { applyIdsPlacementConfig(it) } }
        // 2.4.2 SWITCH (D1-242): RC rỗng chỉ áp khi chính RC đã đặt gì đó (không gọi mỗi lần fetch, không xoá công tắc Firestore).
        val rcSwitches = AdsHelper.instance.takeIf { it.hasPlacementConfig() }?.placementsConfigManager?.switches
        if (snapshot.adsPlacement.isNotEmpty() || rcSwitches?.hasRecord(PlacementSource.REMOTE_CONFIG) == true) {
            AdsHelper.instance.parserAdsConfig(snapshot.adsPlacement)
        }
        // Policy artifact cho Monetization Brain. Artifact hỏng chỉ khiến brain giữ nguyên
        // policy đang chạy — không ảnh hưởng luồng ads phía trên. Qua `applyPolicy` (1.9.0 T5s):
        // sàn khám phá tới bandit, bản được chấp nhận được lưu cho cold start sau.
        // 2.3.0 SECBC (Q3): policy `/policy` đã áp trong process thắng RC tới hết process (một dòng `policy: rc_skipped`).
        // Kiểm + áp dưới khoá `PolicyClient` (I-1, D24-230): loại trừ với lần áp của `/policy`.
        if (snapshot.brainPolicy.isNotEmpty()) synchronized(com.app.plugin.brain.policy.PolicyClient) {
            if (com.app.plugin.brain.policy.PolicyClient.remoteConfigAllowed()) {
                val result = com.app.plugin.brain.DecisionEngine.instance.applyPolicy(snapshot.brainPolicy)
                LogUtil.logD("BrainPolicy (RemoteConfig): ${result.report}")
            }
        }
        // Cấu hình paywall. Tách khoá riêng khỏi brain_policy_android vì hai thứ có nhịp thay
        // đổi rất khác nhau: paywall đổi theo chiến dịch/sự kiện (hàng tuần), policy brain đổi
        // theo chu kỳ huấn luyện. Gộp chung buộc phải đẩy lại cả hai mỗi lần sửa một cái.
        if (snapshot.paywall.isNotEmpty()) {
            val r = com.app.plugin.paywall.PaywallManager.apply(snapshot.paywall)
            LogUtil.logD("Paywall (RemoteConfig): ${r.report}")
        }
        // Vòng đời subscription — dunning/win-back (doc 23 mục C.3). Khoá riêng: nhịp đổi của
        // nó là nhịp vận hành subscription (cadence nhắc, công tắc win-back), không trùng nhịp
        // routing (điều khoản phí store) lẫn paywall (chiến dịch). Cấu hình hỏng thì giữ bản cũ.
        if (snapshot.subscriptionLifecycle.isNotEmpty()) {
            val r = com.app.plugin.iap.SubscriptionLifecycle.apply(snapshot.subscriptionLifecycle)
            LogUtil.logD("SubscriptionLifecycle (RemoteConfig): ${r.report}")
        }
        // 2.0.0 C1d: 5 section game (routing, battle pass, recurring grants, offerwall, CRM) qua registry, theo thứ tự
        // GameModule đăng ký (= thứ tự cũ); core không gọi lớp game. GameModule vắng → 0 section.
        snapshot.gameSections?.forEach { (section, raw) ->
            if (raw.isNotEmpty()) LogUtil.logD("${section.tag} (RemoteConfig): ${section.apply(raw)}")
        }
        AdsHelper.instance.saveConfig()
        // Ad đã load/đang load bằng id mặc định trong code (vì RemoteConfig chưa kịp về) →
        // khi config về tới đây, reload lại bằng id mới từ Firebase nếu id đã đổi.
        AdsHelper.instance.checkAfterLoadIdsConfig()
    }

    // Same failure handling the parse-and-apply blocks had when they were one try/catch.
    private inline fun applyGuarded(key: String, apply: () -> Unit) {
        try {
            apply()
        } catch (e: Exception) {
            LogUtil.logD("FirHelperConfig: Failed to apply $key (RemoteConfig): ${e.message}")
        }
    }

    /**
     * NEW-04 FIX: Hàm parse chung cho ads_region_android — dùng cho cả FireStore và RemoteConfig.
     * Thay thế ~120 dòng code duplicate giữa 2 parser.
     */
    private fun applyAdsRegionConfig(map: Map<String, Any>) {
        val cfg = AdsHelper.instance.currConfig
        (map["maskAdsStatus"]?.toString()?.toDoubleOrNull())?.let {
            cfg.maskAdsStatus = it.toInt()
        }
        // open
        map["openShowFirstType"]?.toString()?.toDoubleOrNull()?.toInt()?.let { newVal ->
            if (cfg.openAdFirstType != newVal) cfg.openFirstCfChange = true
            cfg.openAdFirstType = newVal
        }
        (map["openFirstImg"]?.toString()?.toDoubleOrNull())?.let { cfg.openAdFirstImg = it.toInt() }
        (map["openShowType"]?.toString()?.toDoubleOrNull())?.let { cfg.openAdtype = it.toInt() }
        (map["openFlagimg"]?.toString()?.toDoubleOrNull())?.let { cfg.openAdFlagImg = it.toInt() }
        (map["openShowAt"]?.toString()?.toDoubleOrNull())?.let { cfg.openAdShowAt = it.toInt() }
        (map["openDeltatime"]?.toString()?.toDoubleOrNull() ?: 15.0).let {
            if (map.containsKey("openDeltatime")) cfg.openAdDeltaTime = it.toInt()
        }
        (map["openFlagFull2"]?.toString()?.toDoubleOrNull())?.let { cfg.openAdFlagFull2 = it.toInt() }
        // banner
        map["step_show_banner"]?.toString()?.let { cfg.bannerStep = it; cfg.parserBannerStep() }
        map["step_show_rect"]?.toString()?.let { cfg.rectStep = it; cfg.parserRectStep() }
        map["step_show_cl"]?.toString()?.let { cfg.clStep = it; cfg.parserCollapseStep() }
        map["step_show_native"]?.toString()?.let { cfg.nativeStep = it; cfg.parserNativeStep() }
        // full
        (map["fullTotalOfday"]?.toString()?.toDoubleOrNull())?.let { cfg.fullTotalOfDay = it.toInt() }
        (map["fullTimeStart"]?.toString()?.toDoubleOrNull())?.let { cfg.fullStartTime = it.toInt() }
        (map["fullSessionStart"]?.toString()?.toDoubleOrNull())?.let { cfg.fullSesstionTime = it.toInt() }
        (map["fullDeltatime"]?.toString()?.toDoubleOrNull())?.let { cfg.fullDeltaTime = it.toInt() }
        (map["bannerDeltaTime"]?.toString()?.toDoubleOrNull())?.let { cfg.bannerDeltaTime = it.toInt() }
        (map["clDeltaTime"]?.toString()?.toDoubleOrNull())?.let { cfg.clDeltaTime = it.toInt() }
        map["step_show_full"]?.toString()?.let { cfg.fullStep = it; cfg.parserFullStep() }
        // gift
        (map["giftTotalOfday"]?.toString()?.toDoubleOrNull())?.let { cfg.giftTotalOfDay = it.toInt() }
        (map["giftDeltatime"]?.toString()?.toDoubleOrNull())?.let { cfg.giftDeltaTime = it.toInt() }
        map["step_show_gift"]?.toString()?.let { cfg.giftStep = it; cfg.parserGiftStep() }
    }

    /**
     * NEW-04 FIX: Hàm parse chung cho ads_region2_android.
     */
    private fun applyAdsRegion2Config(map: Map<String, Any>) {
        val cfg = AdsHelper.instance.currConfig
        (map["full2TypeShow"]?.toString()?.toDoubleOrNull())?.let { cfg.full2FlagShow = it.toInt() }
        (map["full2Deltatime"]?.toString()?.toDoubleOrNull())?.let { cfg.full2DeltaTime = it.toInt() }
        map["full2Step"]?.toString()?.let { cfg.full2Step = it; cfg.parserFull2Step() }
    }

    /**
     * NEW-04 FIX: Hàm parse chung cho cf_ids_placement.
     */
    private fun applyIdsPlacementConfig(mapAdsIds: Map<String, Any>) {
        mapAdsIds.forEach { (cfkey, cfVa) ->
            val mapcf = cfVa as? Map<String, Any> ?: return@forEach
            when (cfkey) {
                "cf_openad" -> AdsMobMy.instance.getAdsFull(AdsIDDef.AdmobFullOpen)?.parSerConfig(mapcf, true)
                "cf_banner" -> AdsMobMy.instance.getAdsBanner(AdsIDDef.AdmobBanner)?.parSerConfig(mapcf, true)
                "cf_banner_rect" -> AdsMobMy.instance.getAdsRect(AdsIDDef.AdmobRect)?.parSerConfig(mapcf, true)
                "cf_native" -> AdsMobMy.instance.getAdsNative(AdsIDDef.AdmobNative)?.parSerConfig(mapcf, true)
                "cf_native_cl" -> AdsMobMy.instance.getAdsNtCl(AdsIDDef.AdmobNtCl)?.parSerConfig(mapcf, true)
                "cf_nativefull" -> AdsMobMy.instance.getAdsFull(AdsIDDef.AdmobFullNt)?.parSerConfig(mapcf, true)
                "cf_full" -> AdsMobMy.instance.getAdsFull(AdsIDDef.AdmobFullInters)?.parSerConfig(mapcf, true)
                "cf_gift" -> AdsMobMy.instance.getAdsGift(AdsIDDef.AdmobRewarded)?.parSerConfig(mapcf, true)
                // "cf_banner_collapse", "cf_native_bn", "cf_multintfull", "cf_nativeicfull" → reserved, no-op
            }
        }
    }

    public fun hasKey(key: String): Boolean {
        val fva = remoteConfig.getValue(key)
        return (fva.source == FirebaseRemoteConfig.VALUE_SOURCE_REMOTE)
    }

    public fun getBoolean(key: String): Boolean {
        return remoteConfig.getBoolean(key)
    }

    public fun getLong(key: String): Long {
        return remoteConfig.getLong(key)
    }

    public fun getDouble(key: String): Double {
        return remoteConfig.getDouble(key)
    }

    public fun getString(key: String, defVa: String): String {
        val value = remoteConfig.getString(key)
        return if (value.isEmpty()) defVa else value
    }
}
