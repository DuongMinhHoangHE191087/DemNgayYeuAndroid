package com.app.plugin.until

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.app.plugin.SdkKillSwitch
import com.app.plugin.ads.AdsPolicy
import com.app.plugin.ads.PlacementSource
import com.app.plugin.ads.PlacementSwitchState
import com.app.plugin.ads.core.BiddingPartners
import com.app.plugin.attribution.InstallReferrerHelper
import com.app.plugin.brain.BrainPolicyStore
import com.app.plugin.brain.BrainStorage
import com.app.plugin.consent.AnalyticsConsent
import com.app.plugin.consent.FcmConsent
import com.app.plugin.iap.BillingThread
import com.app.plugin.iap.PurchaseLedger
import com.app.plugin.license.LicenseCache
import com.app.plugin.logevent.DurableEvents
import com.app.plugin.logevent.LogEventControl
import com.app.plugin.segment.SegmentStartup
import com.app.plugin.segment.UserSegmentEngine
import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * 2.4.2 CLEAR (Hợp đồng D, quyết định (v)): phần việc của `MonetizationSdk.clearLocalData` — nút "Xoá dữ liệu" của app
 * (Play data safety). Trên [executor] (luồng nền, người gọi trả về ngay): xoá mọi khoá của `App_prefer_file`
 * ([PreUtil.PREFF_FILE]) và `XgameHCM-BaseAndroid` (`PreferenceHelper`) trừ [keep]; làm rỗng hàng đợi event bền
 * (`files/appplugin_events`); xoá cache verdict giấy phép `appplugin_license` (2.4.3, lần mở sau xác minh đầy đủ một lần); bỏ cache trong RAM để dữ liệu đã xoá không bị ghi lại (sổ `PurchaseLedger` trên luồng
 * billing, player id của `BrainStorage`); rồi nạp lại `UserSegmentEngine` dưới `recordLock` của nó. Không chạm `appplugin_policy`,
 * `<pkg>_preferences` (IABTCF/GPP: đổi qua `showPrivacyOptions`) hay file của UMP/Firebase/AppsFlyer/AppLovin (app tự
 * `resetAnalyticsData`). Không như `PreferenceHelper.clearAll()` của fork: fork xoá một file, kể cả quyền đã mua.
 */
internal object LocalDataReset {
    private const val TAG = "AppPlugin"

    /** File của `PreferenceHelper` (`PreferenceHelper.kt:12`). */
    private const val PREFERENCE_HELPER_FILE = "XgameHCM-BaseAndroid"

    /**
     * Khoá giữ lại. Hằng `private` ở nguồn được chép chữ (test so với hằng nguồn bằng reflection):
     * (a) quyền đã mua — `Entitlements.kt:33-37` (4 khoá), `AdsHelper.kt:39-40` (mask gỡ quảng cáo, cờ cũ);
     * (b) consent — cờ tắt thu thập của SDK, việc xoá token FCM đang chờ, tầng consent đã lưu (`AppPluginBase.kt:51`);
     * (c) bản lưu cấu hình từ xa/kill/policy SDK đọc lúc mở — `cf_ads_placement` (bản gộp + bản ghi theo nguồn RC/Firestore
     * của `PlacementSwitchState`, ba khoá đi cùng nhau, D8-SC), `ads_policy`,
     * `sdk_kill`, `logevent_control`, `ads_bidding_partners`, policy brain (cộng hai tiền tố ở [KEEP_PREFIXES]);
     * (d) cờ một-lần chống thu lại — Install Referrer.
     */
    internal val KEEP_KEYS: Set<String> = setOf(
        "iap_active_products", "iap_remove_ads_products", "entitlements_remove_ads_masks", "iap_entitlements_v1",
        "ads_hp_remove_ad_mask", "ads_hp_remove_ad",
        AnalyticsConsent.KEY_COLLECTION_DISABLED_BY_SDK, FcmConsent.KEY_DELETE_PENDING, "consent_tier",
        PlacementSwitchState.KEY_MERGED, PlacementSource.REMOTE_CONFIG.prefKey, PlacementSource.FIRESTORE.prefKey,
        AdsPolicy.PREF_KEY, SdkKillSwitch.PREF_KEY, LogEventControl.PREF_KEY,
        BiddingPartners.PREF_KEY, BrainPolicyStore.PREF_KEY,
        InstallReferrerHelper.KEY_CAPTURED,
    )

    /** (c) theo tiền tố: `AdsConfigOb` (`cfcurr_ads_*`), id quảng cáo theo placement của mỗi mạng (`plmem_*`, `getKeyForDataSave`). */
    internal val KEEP_PREFIXES: List<String> = listOf("cfcurr_", "plmem_")

    private val worker: ExecutorService by lazy {
        Executors.newSingleThreadExecutor { r -> Thread(r, "appplugin-local-reset").apply { isDaemon = true } }
    }

    /** Luồng nền của việc xoá (một luồng daemon, tạo lần đầu có việc). Test thay bằng executor đồng bộ. */
    @Volatile
    internal var executor: Executor = Executor { worker.execute(it) }

    fun keep(key: String): Boolean = key in KEEP_KEYS || KEEP_PREFIXES.any { key.startsWith(it) }

    /** Xếp việc xoá lên [executor] rồi trả về ngay. */
    fun clear(context: Context) {
        val app = context.applicationContext ?: context
        executor.execute { run(app) }
    }

    /** Mỗi bước tự bắt lỗi và log: một bước hỏng không chặn bước sau. */
    private fun run(app: Context) {
        val removed = step("prefs") {
            listOf(PreUtil.PREFF_FILE, PREFERENCE_HELPER_FILE).sumOf { clearPrefs(app.getSharedPreferences(it, Context.MODE_PRIVATE)) }
        }
        step("license") { LicenseCache.clear(app) }   // 2.4.3 LCACHE: lần mở sau xác minh giấy phép đầy đủ một lần
        step("events") { DurableEvents.clearAll(File(app.applicationInfo.dataDir, "files")) }
        step("memory") {
            BillingThread.offMain.execute { PurchaseLedger.forget() }
            BrainStorage.forgetPlayerId()
        }
        step("segment") { if (!SegmentStartup.queueIfStarting { resetSegment(app) }) resetSegment(app) }
        Log.i(TAG, "local data cleared: ${removed ?: "?"} keys removed, kept purchases, consent, remote config")
    }

    /**
     * Dưới `recordLock` (I3): một `recordEventNow` chen sau bước prefs đã ghi lại state cũ, nên xoá lại khoá `seg_*` rồi
     * nạp lại; engine đang nạp lúc khởi động thì việc này xếp sau lượt nạp ([SegmentStartup.queueIfStarting]).
     */
    private fun resetSegment(app: Context) {
        val engine = UserSegmentEngine.instance
        synchronized(engine.recordLock) {
            val prefs = app.getSharedPreferences(PreUtil.PREFF_FILE, Context.MODE_PRIVATE)
            val editor = prefs.edit()
            prefs.all.keys.filter { it.startsWith("seg_") }.forEach { editor.remove(it) }
            editor.commit()
            engine.init(app)
        }
    }

    private fun clearPrefs(prefs: SharedPreferences): Int {
        val doomed = prefs.all.keys.filterNot(::keep)
        if (doomed.isEmpty()) return 0
        val editor = prefs.edit()
        doomed.forEach { editor.remove(it) }
        if (!editor.commit()) Log.e(TAG, "local data reset: prefs commit failed")
        return doomed.size
    }

    private fun <T> step(name: String, block: () -> T): T? = try {
        block()
    } catch (e: Throwable) {
        Log.e(TAG, "local data reset: $name failed", e)
        null
    }
}
