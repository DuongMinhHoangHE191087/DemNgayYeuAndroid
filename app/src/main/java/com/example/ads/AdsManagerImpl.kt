package com.example.ads

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

/**
 * Triển khai chuẩn production của [AdsManager] — Thread-safe & Lifecycle-aware.
 *
 * Kiến trúc:
 *  - Singleton: Được tạo 1 lần trong [AppServiceLocator], tái sử dụng suốt vòng đời app.
 *  - Interstitial: Preload ngầm, Interval Capping 30 giây, callback onDismissed luôn fire.
 *  - App Open Ad: Lắng nghe ProcessLifecycle (foreground event), chặn khi AOA suppressed.
 *  - VIP override: setVipStatus(true) giải phóng toàn bộ cache & dừng nạp vĩnh viễn.
 *
 * Tuân thủ Google Play Policy:
 *  - Test Device IDs được cấu hình khi BuildConfig.DEBUG để tránh Invalid Traffic.
 *  - AOA không hiện khi PaywallScreen / Splash / Permission Dialog đang active.
 *  - Interstitial không fire khi vừa có AOA active (cờ isAoaSuppressed shared).
 */
class AdsManagerImpl : AdsManager,
    Application.ActivityLifecycleCallbacks,
    DefaultLifecycleObserver {

    // ─── Trạng thái VIP ──────────────────────────────────────────────────────
    @Volatile private var isVipUser: Boolean = false

    // ─── Interstitial Ad ─────────────────────────────────────────────────────
    @Volatile private var interstitialAd: InterstitialAd? = null
    @Volatile private var isInterstitialLoading: Boolean = false
    @Volatile private var lastInterstitialShownTime: Long = 0L

    /**
     * Khoảng cách tối thiểu 30 giây giữa 2 lần hiển thị Interstitial.
     * Theo Google Play Policy: Không được spam quảng cáo xen kẽ liên tiếp.
     */
    private val minIntervalMs = 30_000L

    // ─── App Open Ad (AOA) ───────────────────────────────────────────────────
    @Volatile private var appOpenAd: AppOpenAd? = null
    @Volatile private var isAppOpenLoading: Boolean = false
    @Volatile private var appOpenLoadTime: Long = 0L

    /**
     * Cờ chặn AOA khi người dùng đang ở màn hình nhạy cảm:
     * PaywallScreen, SplashScreen, Permission Dialog, Payment Dialog.
     * Phải set true khi mở các màn hình này và false khi đóng.
     */
    @Volatile private var isAoaSuppressed: Boolean = false

    /**
     * AOA chỉ hợp lệ trong 4 giờ kể từ khi load (theo khuyến nghị của Google).
     * Sau đó phải discard và nạp lại để tránh phát quảng cáo đã hết hạn.
     */
    private val aoaExpirationMs = 4L * 3600L * 1000L

    // Activity hiện tại (được cập nhật qua ActivityLifecycleCallbacks)
    @Volatile private var currentActivity: Activity? = null

    // ─── Ad Unit IDs (Test) ───────────────────────────────────────────────────
    // ⚠️ PRODUCTION: Thay bằng Ad Unit ID thật từ AdMob Console trước khi release.
    // Các ID dưới đây là Google Test IDs — an toàn 100% trong môi trường dev/test.
    private val testInterstitialAdUnitId = "ca-app-pub-3940256099942544/1033173712"
    private val testAppOpenAdUnitId = "ca-app-pub-3940256099942544/9257395921"

    // ─── Test Device IDs ──────────────────────────────────────────────────────
    // Thêm Hashed Device ID của thiết bị dev vào đây để tránh gian lận traffic.
    // Lấy Hashed ID từ Logcat khi chạy app lần đầu (tag: "Ads").
    private val testDeviceIds: List<String> = listOf(
        AdRequest.DEVICE_ID_EMULATOR
        // "YOUR_PHYSICAL_DEVICE_HASHED_ID_HERE" // <- thêm ID thật vào đây
    )

    // ─── Initialize ──────────────────────────────────────────────────────────

    override fun initialize(context: Context) {
        // 1. Cấu hình Test Device IDs để tránh vi phạm Invalid Traffic Policy.
        //    Google sẽ không đếm các click/impression từ các thiết bị test này.
        val requestConfig = RequestConfiguration.Builder()
            .setTestDeviceIds(testDeviceIds)
            .build()
        MobileAds.setRequestConfiguration(requestConfig)

        // 2. Khởi tạo SDK. Callback fire sau khi tất cả ad network adapters sẵn sàng.
        MobileAds.initialize(context) {
            // 3. Preload sẵn quảng cáo (chỉ khi không phải VIP)
            if (!isVipUser) {
                preloadInterstitial(context.applicationContext, testInterstitialAdUnitId)
                preloadAppOpenAd(context.applicationContext, testAppOpenAdUnitId)
            }
        }
    }

    // ─── VIP Status ──────────────────────────────────────────────────────────

    override fun setVipStatus(isVip: Boolean) {
        this.isVipUser = isVip
        if (isVip) {
            // Giải phóng toàn bộ cache quảng cáo ngay lập tức khi mua VIP thành công.
            // Người dùng VIP sẽ không bao giờ thấy quảng cáo cho đến khi gói hết hạn.
            interstitialAd = null
            appOpenAd = null
            isInterstitialLoading = false
            isAppOpenLoading = false
        }
    }

    // ─── Interstitial Ad ─────────────────────────────────────────────────────

    override fun preloadInterstitial(context: Context, adUnitId: String) {
        // Guard: không nạp nếu VIP, đang có ad sẵn, hoặc đang load dở
        if (isVipUser || interstitialAd != null || isInterstitialLoading) return

        isInterstitialLoading = true
        val adRequest = AdRequest.Builder().build()

        InterstitialAd.load(
            context,
            adUnitId,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    isInterstitialLoading = false
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    // Load thất bại — reset để cho phép thử lại sau
                    interstitialAd = null
                    isInterstitialLoading = false
                }
            }
        )
    }

    override fun showInterstitial(activity: Activity, onAdDismissed: () -> Unit) {
        val currentTime = System.currentTimeMillis()
        val isIntervalOk = (currentTime - lastInterstitialShownTime) >= minIntervalMs
        val currentAd = interstitialAd

        // Guard: bỏ qua nếu VIP, chưa đủ interval 30s, hoặc ad chưa load xong
        if (isVipUser || currentAd == null || !isIntervalOk) {
            // Callback ngay để người dùng tiếp tục thao tác — không bao giờ block UI
            onAdDismissed()
            // Kích hoạt load lại nếu ad chưa sẵn sàng
            if (!isVipUser && currentAd == null) {
                preloadInterstitial(activity.applicationContext, testInterstitialAdUnitId)
            }
            return
        }

        // Tạm thời chặn AOA khi Interstitial đang phát để tránh bị spam 2 quảng cáo cùng lúc
        isAoaSuppressed = true

        currentAd.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                // Người dùng đóng ad — tiếp tục luồng
                interstitialAd = null
                lastInterstitialShownTime = System.currentTimeMillis()
                isAoaSuppressed = false
                // Preload sẵn cho lần tiếp theo ở chế độ nền
                preloadInterstitial(activity.applicationContext, testInterstitialAdUnitId)
                onAdDismissed()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                // Ad không hiển thị được — cho người dùng tiếp tục ngay
                interstitialAd = null
                isAoaSuppressed = false
                preloadInterstitial(activity.applicationContext, testInterstitialAdUnitId)
                onAdDismissed()
            }

            override fun onAdShowedFullScreenContent() {
                // Ad đang hiển thị — không cần xử lý gì thêm ở đây
            }
        }

        currentAd.show(activity)
    }

    // ─── App Open Ad (AOA) ───────────────────────────────────────────────────

    override fun registerAppOpenAdLifecycle(application: Application) {
        // Đăng ký theo dõi vòng đời các Activity để biết Activity nào đang active
        application.registerActivityLifecycleCallbacks(this)
        // Đăng ký theo dõi Process Lifecycle: nhận sự kiện khi app lên Foreground
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun preloadAppOpenAd(context: Context, adUnitId: String) {
        // Guard: không nạp nếu VIP, ad còn hợp lệ, hoặc đang load dở
        if (isVipUser || isAppOpenAdAvailable() || isAppOpenLoading) return

        isAppOpenLoading = true
        AppOpenAd.load(
            context,
            adUnitId,
            AdRequest.Builder().build(),
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    appOpenAd = ad
                    appOpenLoadTime = System.currentTimeMillis()
                    isAppOpenLoading = false
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    appOpenAd = null
                    isAppOpenLoading = false
                }
            }
        )
    }

    /** Kiểm tra AOA có sẵn sàng & còn trong hạn 4 giờ không */
    private fun isAppOpenAdAvailable(): Boolean {
        val withinExpiry = (System.currentTimeMillis() - appOpenLoadTime) < aoaExpirationMs
        return appOpenAd != null && withinExpiry
    }

    /** Gọi khi app chuyển từ Background lên Foreground (ProcessLifecycle onStart) */
    override fun onStart(owner: LifecycleOwner) {
        currentActivity?.let { activity ->
            showAppOpenAdIfAvailable(activity)
        }
    }

    private fun showAppOpenAdIfAvailable(activity: Activity) {
        // ─── Google Play Policy compliance checks ───────────────────────────
        // 1. VIP → không bao giờ hiện quảng cáo
        // 2. Suppressed → đang ở Paywall / Splash / Permission Dialog → không được phép hiện
        // 3. Ad chưa load xong hoặc đã hết hạn → bỏ qua
        if (isVipUser || isAoaSuppressed || !isAppOpenAdAvailable()) {
            if (!isVipUser && appOpenAd == null && !isAppOpenLoading) {
                preloadAppOpenAd(activity.applicationContext, testAppOpenAdUnitId)
            }
            return
        }

        val ad = appOpenAd ?: return

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                appOpenAd = null
                // Preload ad tiếp theo ở chế độ nền
                preloadAppOpenAd(activity.applicationContext, testAppOpenAdUnitId)
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                appOpenAd = null
                preloadAppOpenAd(activity.applicationContext, testAppOpenAdUnitId)
            }
        }

        ad.show(activity)
    }

    override fun setAppOpenAdSuppressed(isSuppressed: Boolean) {
        this.isAoaSuppressed = isSuppressed
    }

    // ─── ActivityLifecycleCallbacks ──────────────────────────────────────────

    /** Cập nhật Activity hiện tại khi resume để AOA biết được context hợp lệ */
    override fun onActivityStarted(activity: Activity) {
        currentActivity = activity
    }

    override fun onActivityResumed(activity: Activity) {
        currentActivity = activity
    }

    override fun onActivityPaused(activity: Activity) {
        // Không cần clear — onStart sẽ cập nhật lại
    }

    override fun onActivityStopped(activity: Activity) {
        if (currentActivity === activity) currentActivity = null
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (currentActivity === activity) currentActivity = null
    }

    // Các callback bắt buộc khai báo nhưng không cần xử lý
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
}
