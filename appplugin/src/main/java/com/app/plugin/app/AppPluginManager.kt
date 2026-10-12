package com.app.plugin.app

import android.app.Activity
import android.content.Context
import com.app.plugin.BuildConfig
import com.app.plugin.SdkKillSwitch
import com.app.plugin.ads.AdRevenueRouting
import com.app.plugin.ads.AdSdkStartup
import com.app.plugin.ads.AdsHelper
import com.app.plugin.ads.AppResumeIF
import com.app.plugin.consent.FcmConsent
import com.app.plugin.firbase.FirHelper
import com.app.plugin.firbase.PlacementPaidEvent
import com.app.plugin.until.LogUtil
import com.app.plugin.until.PreUtil
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics

public class AppPluginManager private constructor() {
    public companion object {
        public val instance: AppPluginManager by lazy { AppPluginManager() }
    }

    private val listAppResume: MutableList<AppResumeIF> = mutableListOf()

    private var mContext: Context? = null
    private var mainActivity: Activity? = null
    public var isMainActivityDestroy: Boolean = false
    public var isDebugMode: Boolean = false
    private var verInstall: String = "Unknown"
    private var sessionCount: Int = 0
    private var tSesstionStart: Long = 0
    private var tAppStart: Long = 0

    public fun initPlugin(context: Context, isDebug: Boolean = false) {
        mContext = context
        // Trước mọi quảng cáo: app khai `ads_admob_firebase_linked` = 1 thì SDK không ghi thêm
        // ad_impression cho AdMob lên Firebase (doc 62 mục 2.3). Đọc lỗi → chưa liên kết (Điều 7).
        AdRevenueRouting.admobLinkedToFirebase = AdRevenueRouting.readAdmobLinked(context)
        PlacementPaidEvent.enabled = PlacementPaidEvent.read(context) // 2.5.1 PLEVT: ad_placement_impression khi đã liên kết
        this.isDebugMode = isDebug
        sessionCount = PreUtil.getInt(context, "mem_app_session_count", 0)
        sessionCount++
        PreUtil.setInt(context, "mem_app_session_count", sessionCount)
        // NEW-06 FIX: dùng getLong/setLong thay vì getInt/setInt để tránh truncate Long → Int
        // tAppStart là Unix timestamp (giây) ~1.7 tỷ, vẫn trong range Int nhưng là time bomb
        tAppStart = PreUtil.getLong(context, "mem_app_tstart", -1L)
        tSesstionStart = System.currentTimeMillis() / 1000
        if (tAppStart <= 0) {
            tAppStart = tSesstionStart
            PreUtil.setLong(context, "mem_app_tstart", tAppStart)
        }
        try {
            FirebaseApp.initializeApp(context)
        } catch (e: Exception) {
            LogUtil.logE("Firebase initialization failed: ${e.message}")
        }
        // 2.3.0 SECBC: policy brain qua /policy (Play Integrity), mặc định tắt; chỉ nghe verdict giấy phép, 0 I/O trên main.
        com.app.plugin.brain.policy.PolicyClient.install(context)
        tagCrashlyticsVersion()
        // Kill switch của lần áp Remote Config gần nhất, trước mọi bước khởi tạo mạng quảng cáo và
        // billing: file prefs đã nạp ở trên, nên đây là đọc bộ nhớ. Tự bắt lỗi và log.
        SdkKillSwitch.restore(context)
        com.app.plugin.logevent.LogEventControl.restore(context)
        // Trước mọi thứ khác dùng Firebase: tầng khôi phục chưa cho phép FCM thì tắt auto-init, vì cờ
        // auto-init đã lưu (bản ≤ 1.3.x lưu `true`) thắng cờ manifest. Tự bắt lỗi và log.
        FcmConsent.holdAutoInitUntilConsent(context)
        try {
            // AppsFlyer start khi UMP trả lời (ngay đây nếu câu trả lời đã có từ phiên trước). Consent
            // Mode của Firebase Analytics và FCM theo tầng từ đây (listener tầng), kể cả trước khi cổng
            // mở, và thêm một lần khi cổng mở. Tới lần đầu đó manifest SDK giữ Consent Mode denied và
            // FCM không auto-init.
            AdSdkStartup.requestAnalytics(context)
        } catch (e: Exception) {
            LogUtil.logE("Analytics/attribution start request failed: ${e.message}")
        }
        try {
            AdsHelper.instance.initAdsHelper(context)
        } catch (e: Exception) {
            LogUtil.logE("Ads helper init failed: ${e.message}")
        }
        try {
            // 2.2.0 SEG: segment người dùng; RC + prefs nạp trên luồng nền, SESSION_START mỗi foreground (SessionTracker).
            com.app.plugin.segment.SegmentHooks.init(context)
        } catch (e: Exception) {
            LogUtil.logE("Segment init failed: ${e.message}")
        }
        try {
            // Bật độ bền cho decision log của Monetization Brain: bản ghi xuống đĩa trước khi
            // gửi, và được replay khi app mở lại. Thiếu bước này thì log mất khi offline —
            // mà mất không ngẫu nhiên nên làm lệch off-policy evaluation (doc 31 mục 6).
            com.app.plugin.brain.DecisionLogger.attachQueueForApp(context)
            // 1.7.0 T6: event tiền + phễu sống qua kill (thư mục riêng; đọc field, không chạm đĩa trên main).
            com.app.plugin.logevent.DurableEvents.attach(context)
            // Mở app = phiên mới. Phải báo cho brain: bộ đếm quảng cáo mỗi phiên và ngân
            // sách Lagrangian đều được cấp lại tại đây. Không gọi thì trần "mỗi phiên"
            // thực chất trở thành trần "trọn đời cài đặt".
            com.app.plugin.brain.AdsBrain.onSessionStart()
        } catch (e: Exception) {
            LogUtil.logE("Brain decision log queue init failed: ${e.message}")
        }
        try {
            // Mỗi lần app mở: ưu tiên fetch Remote Config (nhanh, nhẹ) trước,
            // sau đó fetch Firestore (dữ liệu JSON lớn hơn) để đảm bảo currConfig
            // luôn phản ánh giá trị mới nhất từ Firebase. Nếu giá trị không đổi,
            // các hàm parser trong FirHelperConfig sẽ tự bỏ qua (no-op).
            FirHelper.instance.fetchRemoteCf {
                FirHelper.instance.fetchFireStore {}
            }
        } catch (e: Exception) {
            LogUtil.logE("Fetch Firebase config on app open failed: ${e.message}")
        }
    }

    /** Phiên bản SDK trên mọi crash (custom key `appplugin_version`, doc 60 G). Lỗi thì log, không ném. */
    internal fun tagCrashlyticsVersion() {
        try {
            FirebaseCrashlytics.getInstance().setCustomKey("appplugin_version", BuildConfig.SDK_VERSION)
        } catch (e: Exception) {
            LogUtil.logE("Crashlytics appplugin_version failed: ${e.message}")
        }
    }

    public fun clearMainActivity() {
        mainActivity = null
        isMainActivityDestroy = true
    }

    public fun getContext(): Context? {
        return mContext
    }

    public fun registerMyAppLifecycle(myIfLifecycle: AppResumeIF?) {
        if (myIfLifecycle != null) {
            listAppResume.add(myIfLifecycle)
        }
    }

    public fun unRegisterMyAppLifecycle(myIfLifecycle: AppResumeIF?): Boolean {
        if (myIfLifecycle != null) {
            return listAppResume.remove(myIfLifecycle)
        }
        return true
    }

    public fun getlistAppLifeCB(): List<AppResumeIF> {
        // OBS-02 FIX: trả về snapshot (toList()) thay vì reference nội bộ
        // để tránh ConcurrentModificationException khi callback trong vòng lặp
        // gọi unRegisterMyAppLifecycle() → modify listAppResume trong khi đang iterate
        return listAppResume.toList()
    }

    public fun setMainActivity(activity: Activity) {
        mainActivity = activity
        AdsHelper.instance.setMainActivity(activity)
    }

    public fun setMainActivityStatus(isDestroy: Boolean) {
        isMainActivityDestroy = isDestroy
    }

    public fun checkMainActivity(activity: Activity): Boolean {
        if (mainActivity?.equals(activity) == true) {
            return true
        } else {
            return false
        }
    }

    @Deprecated("Use getMainActivityName instead", ReplaceWith("getMainActivityName()"))
    public fun getMainActitivyName(): String {
        return getMainActivityName()
    }

    public fun getMainActivityName(): String {
        return mainActivity?.localClassName ?: "null"
    }

    public fun getSessionApp(): Int {
        return sessionCount
    }

    public fun getVerInstall(): String {
        return verInstall
    }

    public fun getTimeSesstionStart(): Long {
        return tSesstionStart
    }

    public fun getTimeStart(): Long {
        return tAppStart
    }
}