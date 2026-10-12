package com.app.plugin.consent

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import com.app.plugin.logevent.ConsentEvents
import com.app.plugin.until.AppUtil
import com.app.plugin.until.LogUtil
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Lớp bọc Google User Messaging Platform — CMP cho EEA/UK.
 *
 * ═══ VÌ SAO BẮT BUỘC ═══
 *
 * Google yêu cầu publisher phục vụ quảng cáo cho người dùng EEA/UK dùng một CMP được chứng
 * nhận. Thị trường mục tiêu của sản phẩm này là US & EU (doc 22), nên thiếu lớp này nghĩa là
 * ở đúng nửa thị trường quan trọng nhất, SDK chưa dùng được hợp lệ.
 *
 * ═══ RANH GIỚI ═══
 *
 * Lớp này chỉ **chạy luồng UMP và dịch kết quả** thành `ConsentTier`. Nó không tự quyết định
 * chính sách — quyết định "được profiling hay không" nằm ở `ConsentState`, nơi kiểm chứng
 * được bằng unit test thuần mà không cần `Activity`.
 *
 * ═══ KHÔNG TỰ ĐỘNG CHẠY ═══
 *
 * `MonetizationSdk` không tự gọi hàm này. Lý do: luồng UMP cần một `Activity` đang hiển thị
 * và có thể mở một dialog toàn màn hình. Tự bật nó trong `Application.onCreate()` sẽ khiến
 * form consent nhảy ra ở thời điểm mà studio không kiểm soát được — thường là đè lên màn hình
 * splash của họ. Studio phải gọi từ Activity của mình, ở thời điểm họ chọn.
 *
 * ═══ MỘT LUỒNG MỖI PROCESS ═══
 *
 * [gatherConsent] và [request] đi qua cùng một [ConsentGate]: gọi lại khi Activity được tạo lại
 * không mở form thứ hai, callback chỉ được xếp hàng và chạy khi luồng đầu xong. Cách cổng mở
 * nằm ở [ConsentFormWatchdog].
 *
 * Từ 1.2.0 đây là `ConsentManager` duy nhất; lớp `com.app.plugin.ads.consent.ConsentManager`
 * của hai app fork chỉ còn là lớp chuyển tiếp về đây.
 */
public object ConsentManager {

    @Volatile
    private var info: ConsentInformation? = null

    @Volatile
    private var gate = ConsentGate()

    private val formRetried = AtomicBoolean(false)

    /**
     * Activity gần nhất app gọi luồng consent, để hiện form trên Activity còn sống khi Activity cũ
     * bị tạo lại (xoay màn hình, dark mode, ngôn ngữ) lúc form đang hiện hoặc đang tải.
     */
    @Volatile
    private var latestActivity: WeakReference<Activity>? = null

    /** Lần hiện form hiện tại. Callback của lần đã bị thay thế chỉ cập nhật tầng consent. */
    private val formAttempt = AtomicInteger(0)

    /** Watchdog của lần hiện form đang chạy, để kiểm tra ngay khi có Activity mới gọi vào. */
    @Volatile
    private var activeFormCheck: Runnable? = null

    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    private val updateListeners = CopyOnWriteArrayList<() -> Unit>()

    /** Application context, để đọc `IABTCF_*` lúc dịch tầng ([tierFor]). Gán ở mọi lối vào có Activity. */
    @Volatile
    private var appContext: Context? = null

    /**
     * Thiết bị test để ép hiện form consent EEA khi phát triển. Để trống thì dùng string
     * `admob_test_device` của app (nhiều id cách nhau bằng dấu phẩy) khi `flagTest` bật (xem
     * `AppUtil.isTestFlagOn`).
     */
    @Volatile
    public var debugTestDeviceHashedIds: List<String> = emptyList()

    /** Ép mô phỏng khu vực EEA khi debug — chỉ có tác dụng trên thiết bị đã khai ở trên. */
    @Volatile
    public var debugForceEea: Boolean = false

    /**
     * Có thể yêu cầu quảng cáo chưa.
     *
     * Trả `true` khi UMP chưa được khởi tạo — cố ý. Studio chưa cấu hình consent (phần lớn là
     * ngoài EEA) không được tự nhiên mất sạch quảng cáo vì nâng phiên bản SDK. Cảnh báo về
     * việc chưa cấu hình nằm ở báo cáo tự kiểm tra của `MonetizationSdk`, chỗ người ta đọc
     * được, thay vì ở một sự cố im lặng ngoài production.
     */
    public fun canRequestAds(): Boolean = info?.canRequestAds() ?: true

    /**
     * Có được khởi tạo SDK quảng cáo chưa: câu trả lời UMP đã lưu, kể cả từ phiên trước.
     *
     * Khác [canRequestAds] ở chỗ UMP chưa có câu trả lời thì là `false`. Cài mới ở EEA chưa trả
     * lời form, nên khởi tạo lúc đó là khởi tạo trước consent. `AdSdkStartGate` dùng hàm này.
     */
    public fun canStartAdSdks(context: Context): Boolean = try {
        UserMessagingPlatform.getConsentInformation(context).canRequestAds()
    } catch (e: Throwable) {
        LogUtil.logE("ConsentManager.canStartAdSdks failed: ${e.message}")
        false
    }

    /** Trạng thái UMP đã lưu, chỉ để ghi log chẩn đoán. */
    internal fun consentStatusForLog(context: Context): String = try {
        statusName(UserMessagingPlatform.getConsentInformation(context).consentStatus)
    } catch (e: Throwable) {
        "error: ${e.message}"
    }

    /**
     * UMP đã có một kết quả dùng được: người dùng đã trả lời form, hoặc khu vực không cần
     * consent. App dùng cờ này để chỉ bật thu thập analytics khi đã có quyết định; hết giờ chờ
     * mà form vẫn REQUIRED thì chưa có quyết định nào.
     */
    public fun isConsentResolved(context: Context): Boolean = try {
        when (UserMessagingPlatform.getConsentInformation(context).consentStatus) {
            ConsentInformation.ConsentStatus.OBTAINED,
            ConsentInformation.ConsentStatus.NOT_REQUIRED -> true
            else -> false
        }
    } catch (e: Throwable) {
        LogUtil.logE("ConsentManager.isConsentResolved failed, treated as unresolved: ${e.message}")
        false
    }

    /** Form "tuỳ chọn quyền riêng tư" có bắt buộc phải hiện không (yêu cầu của GDPR). */
    public fun isPrivacyOptionsRequired(): Boolean =
        info?.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    /**
     * Như [isPrivacyOptionsRequired] nhưng đọc thẳng trạng thái UMP đã lưu, nên đúng cả khi
     * process này chưa chạy luồng consent (ví dụ màn Settings mở từ deep link).
     */
    public fun isPrivacyOptionsRequired(context: Context): Boolean = try {
        UserMessagingPlatform.getConsentInformation(context).privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
    } catch (e: Throwable) {
        LogUtil.logE("ConsentManager.isPrivacyOptionsRequired failed, treated as not required: ${e.message}")
        false
    }

    /**
     * Chạy luồng consent một lần cho cả process: cập nhật thông tin, hiện form nếu cần, rồi
     * dịch kết quả sang `ConsentState`.
     *
     * @param onDone luôn được gọi đúng một lần trên main thread, kể cả khi lỗi hay hết giờ.
     *   Không gọi lại là cách chắc chắn nhất để treo màn hình splash của studio.
     */
    public fun gatherConsent(activity: Activity, onDone: () -> Unit) {
        appContext = activity.applicationContext
        latestActivity = WeakReference(activity)
        val currentGate = gate
        if (currentGate.start(onDone)) {
            startGathering(activity, currentGate)
        } else {
            // Luồng đang chạy, có thể trên một Activity vừa bị huỷ: kiểm tra ngay thay vì đợi
            // chu kỳ 5 s của watchdog, để form hiện lại trên Activity này không trễ.
            activeFormCheck?.let { check ->
                mainHandler.removeCallbacks(check)
                mainHandler.post(check)
            }
        }
    }

    /**
     * Như [gatherConsent], kèm tầng consent đã dịch.
     *
     * @param onDone luôn được gọi đúng một lần, kể cả khi lỗi.
     */
    public fun request(activity: Activity, onDone: (ConsentTier) -> Unit = {}) {
        gatherConsent(activity) { onDone(ConsentState.tier()) }
    }

    /**
     * Chỉ cập nhật thông tin consent, không hiện form: cùng tham số với [gatherConsent] (test
     * device, debug geography, tuổi đồng ý). App dùng nó khi muốn tự quyết lúc hiện form, ví dụ
     * đọc [isPrivacyOptionsRequired] trước khi dựng màn Settings.
     *
     * Cập nhật xong thì báo cho các listener của [addConsentUpdatedListener], nên SDK quảng cáo
     * đang chờ consent sẽ khởi tạo ngay nếu UMP cho phép. Lỗi mạng thì chỉ dịch lại tầng từ bộ nhớ
     * đệm trên máy ([refreshTierFromCache]), không báo listener.
     *
     * @param onDone luôn được gọi đúng một lần trên main thread, kể cả khi lỗi hay hết giờ.
     */
    public fun updateConsentInfo(activity: Activity, onDone: () -> Unit) {
        appContext = activity.applicationContext
        latestActivity = WeakReference(activity)
        val called = AtomicBoolean(false)
        val finish = {
            if (called.compareAndSet(false, true)) onDone()
        }
        val timeout = Runnable {
            LogUtil.logE("ConsentManager: info update timed out")
            finish()
        }
        mainHandler.postDelayed(timeout, ConsentFormWatchdog.INFO_UPDATE_TIMEOUT_MS)
        try {
            val ci = UserMessagingPlatform.getConsentInformation(activity)
            info = ci
            ci.requestConsentInfoUpdate(
                activity, buildParams(activity),
                {
                    mainHandler.removeCallbacks(timeout)
                    // Về muộn sau timeout vẫn cập nhật tầng và báo listener: câu trả lời vẫn đúng.
                    updateTier(ci)
                    finish()
                },
                { error ->
                    mainHandler.removeCallbacks(timeout)
                    LogUtil.logE("Consent info update failed: ${error.errorCode} ${error.message}")
                    refreshTierFromCache(ci)
                    finish()
                }
            )
        } catch (e: Exception) {
            mainHandler.removeCallbacks(timeout)
            LogUtil.logE("ConsentManager.updateConsentInfo crashed: ${e.message}")
            mainHandler.post { finish() }
        }
    }

    /** Mở lại form để người dùng đổi lựa chọn. GDPR yêu cầu rút lại sự đồng ý phải dễ như cho. */
    public fun showPrivacyOptions(activity: Activity, onDone: (ConsentTier) -> Unit = {}) {
        // Màn Settings có thể mở form này trong process chưa chạy gatherConsent.
        appContext = activity.applicationContext
        try {
            if (isPrivacyOptionsRequired(activity)) ConsentEvents.shown(appContext, ConsentEvents.PRIVACY_OPTIONS)
            UserMessagingPlatform.showPrivacyOptionsForm(activity) { error ->
                if (error != null) LogUtil.logE("Privacy options error: ${error.message}")
                val ci = info ?: UserMessagingPlatform.getConsentInformation(activity)
                updateTier(ci)
                onDone(ConsentState.tier())
            }
        } catch (e: Exception) {
            LogUtil.logE("ConsentManager.showPrivacyOptions crashed: ${e.message}")
            onDone(ConsentState.tier())
        }
    }

    /**
     * Được gọi trên main thread mỗi khi một form consent đóng (lần đầu, lần thử lại, hay form
     * tuỳ chọn quyền riêng tư). `AdsHelper` dùng nó để tải lại banner đã thất bại vì chưa có
     * consent, nhờ vậy lớp này không phải biết gì về quảng cáo.
     */
    public fun addConsentUpdatedListener(listener: () -> Unit) {
        updateListeners.addIfAbsent(listener)
    }

    public fun removeConsentUpdatedListener(listener: () -> Unit) {
        updateListeners.remove(listener)
    }

    /** Xoá trạng thái consent — chỉ dùng khi phát triển. Cho phép chạy lại luồng consent. */
    public fun reset(context: Context) {
        try {
            UserMessagingPlatform.getConsentInformation(context).reset()
            info = null
            gate = ConsentGate()
            formRetried.set(false)
            formAttempt.incrementAndGet()
            activeFormCheck = null
            ConsentState.setTier(ConsentTier.UNKNOWN)
        } catch (e: Exception) {
            LogUtil.logE("ConsentManager.reset crashed: ${e.message}")
        }
    }

    private fun startGathering(activity: Activity, gate: ConsentGate) {
        val startedAt = System.currentTimeMillis()
        // Timeout chỉ canh cuộc gọi mạng; nó bị huỷ ngay khi cuộc gọi về (xem ConsentFormWatchdog).
        val infoTimeout = Runnable {
            LogUtil.logE("ConsentManager: info update timed out, releasing gate")
            gate.finish()
        }
        mainHandler.postDelayed(infoTimeout, ConsentFormWatchdog.INFO_UPDATE_TIMEOUT_MS)
        try {
            val ci = UserMessagingPlatform.getConsentInformation(activity)
            info = ci
            ci.requestConsentInfoUpdate(
                activity, buildParams(activity),
                {
                    mainHandler.removeCallbacks(infoTimeout)
                    // Cập nhật về muộn (mạng chậm) có thể rơi vào lúc Activity ban đầu đã bị tạo lại.
                    val host = liveHost(activity)
                    if (host == null) {
                        updateTier(ci)
                        gate.finish()
                    } else {
                        showFormIfRequired(host, ci, gate, startedAt)
                    }
                },
                { error ->
                    mainHandler.removeCallbacks(infoTimeout)
                    LogUtil.logE("Consent info update failed: ${error.errorCode} ${error.message}")
                    // Không lấy được thông tin KHÔNG có nghĩa là bị từ chối: dịch lại tầng từ bộ nhớ
                    // đệm trên máy như updateConsentInfo, không suy diễn một lời từ chối chưa ai nói.
                    refreshTierFromCache(ci)
                    gate.finish()
                }
            )
        } catch (e: Exception) {
            mainHandler.removeCallbacks(infoTimeout)
            LogUtil.logE("ConsentManager.gatherConsent crashed: ${e.message}")
            gate.finish()
        }
    }

    private fun showFormIfRequired(
        activity: Activity,
        ci: ConsentInformation,
        gate: ConsentGate,
        startedAt: Long,
    ) {
        val attempt = formAttempt.incrementAndGet()
        val callbackFired = AtomicBoolean(false)
        val formCheck = object : Runnable {
            private var silentChecks = 0

            override fun run() {
                if (formAttempt.get() != attempt) return
                val gone = activity.isFinishing || activity.isDestroyed
                val newer = if (gone) newerLiveActivity(activity) else null
                val action = ConsentFormWatchdog.onFormCheck(
                    callbackFired = callbackFired.get(),
                    activityGone = gone,
                    activityHasFocus = activity.hasWindowFocus(),
                    silentChecks = silentChecks,
                    newerActivityAlive = newer != null,
                    stillRequired = isRequired(ci),
                )
                when (action) {
                    ConsentFormWatchdog.Action.STOP -> Unit
                    ConsentFormWatchdog.Action.RELEASE -> {
                        // Không giữ Runnable này (nó giữ Activity đã huỷ) sau khi luồng kết thúc.
                        if (activeFormCheck === this) activeFormCheck = null
                        gate.finish()
                    }
                    ConsentFormWatchdog.Action.SHOW_ON_NEWER_ACTIVITY -> {
                        LogUtil.logE("ConsentManager: form lost with its activity, showing it on the new one")
                        showFormIfRequired(newer ?: return, ci, gate, startedAt)
                    }
                    ConsentFormWatchdog.Action.KEEP_WAITING -> recheck()
                    ConsentFormWatchdog.Action.RELEASE_AND_WAIT -> {
                        LogUtil.logE("ConsentManager: no form on screen yet, releasing gate")
                        silentChecks++
                        gate.finish()
                        recheck()
                    }
                    ConsentFormWatchdog.Action.RETRY -> {
                        LogUtil.logE("ConsentManager: form callback missing and no form on screen")
                        scheduleFormRetry(activity, ci, 0L)
                    }
                }
            }

            private fun recheck() {
                mainHandler.postDelayed(this, ConsentFormWatchdog.FORM_CHECK_INTERVAL_MS)
            }
        }
        activeFormCheck = formCheck
        mainHandler.postDelayed(formCheck, ConsentFormWatchdog.FORM_CHECK_INTERVAL_MS)
        try {
            if (isRequired(ci)) ConsentEvents.shown(appContext, ConsentEvents.INITIAL)
            UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                if (formAttempt.get() != attempt) {
                    // Form này đã được hiện lại trên Activity mới. Mở cổng ở đây thì app vẽ đè
                    // lên form mới trước khi người dùng trả lời.
                    updateTier(ci)
                    return@loadAndShowConsentFormIfRequired
                }
                if (formError != null) {
                    LogUtil.logE("Consent form error: ${formError.errorCode} ${formError.message}")
                }
                if (formError != null && (activity.isFinishing || activity.isDestroyed) && isRequired(ci)) {
                    // UMP báo "Activity is destroyed" khi Activity bị tạo lại lúc form đang hiện,
                    // trước khi Activity mới kịp gọi gatherConsent. Chưa mở cổng: watchdog chuyển
                    // form sang Activity mới khi nó gọi vào, hoặc mở cổng ở lần kiểm tra kế nếu
                    // không có Activity mới nào.
                    LogUtil.logE("ConsentManager: form closed with its activity, waiting for the recreated one")
                    return@loadAndShowConsentFormIfRequired
                }
                callbackFired.set(true)
                mainHandler.removeCallbacks(formCheck)
                activeFormCheck = null
                updateTier(ci)
                gate.finish()
                val elapsed = System.currentTimeMillis() - startedAt
                if (ConsentFormWatchdog.shouldRetryAfterCallback(elapsed, isRequired(ci))) {
                    scheduleFormRetry(activity, ci, ConsentFormWatchdog.FORM_RETRY_DELAY_MS)
                }
            }
        } catch (e: Exception) {
            LogUtil.logE("ConsentManager.showForm crashed: ${e.message}")
            mainHandler.removeCallbacks(formCheck)
            activeFormCheck = null
            gate.finish()
        }
    }

    /**
     * Activity app vừa dùng cho luồng consent ([gatherConsent], [updateConsentInfo]) nếu còn sống, không
     * thì `null`. Cổng analytics của `AdSdkStartup` mở từ chính câu trả lời của luồng này, nên lúc đó
     * Activity này đang ở foreground; AppsFlyer start trên nó (`AppsflyerHelper.startAfterConsent`).
     */
    internal fun liveConsentActivity(): Activity? =
        latestActivity?.get()?.takeIf { !it.isFinishing && !it.isDestroyed }

    /** Quên Activity của luồng consent, như process mới. Chỉ cho test (`AdSdkStartup.resetForTest`). */
    @JvmSynthetic
    internal fun forgetActivityForTest() {
        latestActivity = null
    }

    /** [activity] nếu còn sống, không thì Activity mới hơn còn sống, không thì `null`. */
    private fun liveHost(activity: Activity): Activity? =
        if (!activity.isFinishing && !activity.isDestroyed) activity else newerLiveActivity(activity)

    private fun newerLiveActivity(current: Activity): Activity? {
        val latest = latestActivity?.get() ?: return null
        if (latest === current || latest.isFinishing || latest.isDestroyed) return null
        return latest
    }

    /** Thử hiện lại form đúng một lần mỗi process, chỉ khi chắc chắn không có form nào đang hiện. */
    private fun scheduleFormRetry(activity: Activity, ci: ConsentInformation, delayMs: Long) {
        if (!isRequired(ci) || !formRetried.compareAndSet(false, true)) return
        mainHandler.postDelayed({
            val host = liveHost(activity) ?: return@postDelayed
            if (!isRequired(ci)) return@postDelayed
            // Form thật là Dialog ở window riêng nên không nằm dưới decorView; nó giữ focus.
            // Kiểm tra WebView bắt trường hợp form nhúng thẳng vào Activity.
            if (!host.hasWindowFocus() || hasWebViewInHierarchy(host)) {
                LogUtil.logE("ConsentManager: form retry skipped, a form is already on screen")
                return@postDelayed
            }
            LogUtil.logE("ConsentManager: consent still REQUIRED, showing the form again")
            try {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(host) { formError ->
                    if (formError != null) LogUtil.logE("Consent form retry error: ${formError.message}")
                    updateTier(ci)
                }
            } catch (e: Exception) {
                LogUtil.logE("ConsentManager form retry crashed: ${e.message}")
            }
        }, delayMs)
    }

    private fun buildParams(activity: Activity): ConsentRequestParameters {
        val builder = ConsentRequestParameters.Builder()
        ConsentState.underAgeOfConsent?.let { builder.setTagForUnderAgeOfConsent(it) }
        val testIds = debugTestDeviceHashedIds.ifEmpty {
            if (AppUtil.isTestFlagOn(activity)) {
                AppUtil.getResourceIds(activity, "admob_test_device")
            } else {
                emptyList()
            }
        }
        if (testIds.isNotEmpty()) {
            val dbg = ConsentDebugSettings.Builder(activity).apply {
                testIds.forEach { addTestDeviceHashedId(it) }
                if (debugForceEea) {
                    setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
                }
                // Áp dụng SAU debugForceEea: nếu cả hai cùng đặt, property thắng (gọi
                // setDebugGeography sau cùng ghi đè giá trị trước đó trên cùng builder). Dùng để
                // thăm dò form US-state của UMP sớm (Task 8-0, 1.6.0) mà không cần bản release.
                debugGeographyFor(System.getProperty("appplugin.debug_geography"))?.let {
                    setDebugGeography(it)
                }
            }.build()
            builder.setConsentDebugSettings(dbg)
        }
        return builder.build()
    }

    /**
     * Map giá trị system property `appplugin.debug_geography` sang hằng số UMP: `"us"` →
     * US-state, `"other"` → khu vực khác có luật riêng, giá trị khác (kể cả `null`) → không ép.
     * `internal` để dump API chỉ thêm một dòng `…$appplugin` (không lộ API public mới).
     */
    internal fun debugGeographyFor(value: String?): Int? = when (value) {
        "us" -> ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_REGULATED_US_STATE
        "other" -> ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_OTHER
        else -> null
    }

    private fun isRequired(ci: ConsentInformation): Boolean = try {
        ci.consentStatus == ConsentInformation.ConsentStatus.REQUIRED
    } catch (e: Throwable) {
        LogUtil.logE("ConsentManager.isRequired: cannot read UMP consentStatus, treated as not REQUIRED: ${e.message}")
        false
    }

    private fun hasWebViewInHierarchy(activity: Activity): Boolean {
        val root = activity.window?.decorView as? ViewGroup ?: return false
        fun walk(view: View): Boolean {
            if (view is WebView) return true
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    if (walk(view.getChildAt(i))) return true
                }
            }
            return false
        }
        return walk(root)
    }

    private fun updateTier(ci: ConsentInformation) {
        ConsentState.setTier(tierFor(ci, appContext))
        // Sau setTier: tầng mới trong event và ở cổng telemetry (Q2). Còn REQUIRED = form đóng mà chưa trả lời.
        if (!isRequired(ci)) ConsentEvents.choice(ConsentState.tier(), appContext)
        updateListeners.forEach { listener ->
            try {
                listener()
            } catch (e: Exception) {
                LogUtil.logE("ConsentManager listener crashed: ${e.message}")
            }
        }
    }

    /**
     * Nhánh lỗi mạng của [updateConsentInfo] và [gatherConsent]: dịch lại tầng từ trạng thái UMP đã
     * lưu và `IABTCF_*` (cả hai nằm trên máy), để tầng khôi phục từ phiên trước không sống cả phiên
     * khi mạng hỏng.
     *
     * Không báo các listener của [addConsentUpdatedListener]: UMP không cập nhật gì. Bộ nhớ đệm không
     * có câu trả lời (UNKNOWN) thì giữ `DENIED`/`PARTIAL`: mạng hỏng không phải lý do bỏ một lời từ
     * chối đã có. Nhưng không giữ `GRANTED`: không còn gì trên máy xác nhận nó, nên hạ về `UNKNOWN`
     * (chưa được cấp, Điều 13).
     */
    private fun refreshTierFromCache(ci: ConsentInformation) {
        val cached = tierFor(ci, appContext)
        if (cached != ConsentTier.UNKNOWN) {
            ConsentState.setTier(cached)
            return
        }
        val current = ConsentState.tier()
        if (current == ConsentTier.GRANTED) {
            LogUtil.logE("ConsentManager: no cached consent answer after failed update, dropping GRANTED to UNKNOWN")
            ConsentState.setTier(ConsentTier.UNKNOWN)
        } else {
            LogUtil.logE("ConsentManager: no cached consent answer after failed update, keeping tier=$current")
        }
    }

    /**
     * Tầng consent từ trạng thái UMP cộng tín hiệu TCF.
     *
     * UMP `OBTAINED` chỉ cho biết form đã được trả lời, KHÔNG cho biết người dùng đồng ý purpose
     * nào: bấm "Do not consent" cũng là `OBTAINED` với `canRequestAds() = true` (đo 2026-09-24).
     * Khi đó tầng lấy từ `IABTCF_*` ([TcfConsent.tierOf]). `OBTAINED` mà không đọc được TCF
     * (thiếu [context], chưa có khoá, sai kiểu) thì không bao giờ là GRANTED (Hiến pháp Điều 13).
     * Trạng thái khác giữ cách dịch của [translate] ([TcfConsent.resolve]).
     */
    internal fun tierFor(ci: ConsentInformation, context: Context?): ConsentTier {
        val status = try {
            statusName(ci.consentStatus)
        } catch (e: Throwable) {
            LogUtil.logE("ConsentManager: cannot read UMP consentStatus, not treated as OBTAINED: ${e.message}")
            "error: ${e.message}"
        }
        val obtained = status == "OBTAINED"
        val fromTcf = if (context != null) {
            TcfConsent.tierOf(TcfConsent.read(context))
        } else {
            if (obtained) LogUtil.logE("ConsentManager: no context to read IABTCF signals")
            ConsentTier.UNKNOWN
        }
        val tier = TcfConsent.resolve(obtained, translate(ci), fromTcf)
        if (obtained && fromTcf == ConsentTier.UNKNOWN) {
            LogUtil.logE("ConsentManager: UMP OBTAINED but IABTCF signals missing, tier=$tier (never GRANTED)")
        }
        LogUtil.logD("consent tier=$tier (ump=$status, tcf=$fromTcf)")
        return tier
    }

    private fun statusName(status: Int): String = when (status) {
        ConsentInformation.ConsentStatus.OBTAINED -> "OBTAINED"
        ConsentInformation.ConsentStatus.NOT_REQUIRED -> "NOT_REQUIRED"
        ConsentInformation.ConsentStatus.REQUIRED -> "REQUIRED"
        else -> "UNKNOWN"
    }

    /**
     * Dịch riêng trạng thái UMP sang `ConsentTier`, chưa xét TCF — [tierFor] ghép hai phần.
     *
     * `NOT_REQUIRED` (ngoài EEA) map sang `GRANTED` chứ không phải `UNKNOWN`: ở đó không có
     * nghĩa vụ consent nào, nên đây là một câu trả lời DỨT KHOÁT là "được phép", khác hẳn
     * với "chưa ai hỏi".
     *
     * `OBTAINED` ở đây chỉ phân biệt theo `canRequestAds()`; [tierFor] thay nó bằng tầng TCF.
     */
    private fun translate(ci: ConsentInformation): ConsentTier = try {
        when (ci.consentStatus) {
            ConsentInformation.ConsentStatus.NOT_REQUIRED -> ConsentTier.GRANTED
            ConsentInformation.ConsentStatus.OBTAINED ->
                if (ci.canRequestAds()) ConsentTier.GRANTED else ConsentTier.DENIED
            ConsentInformation.ConsentStatus.REQUIRED -> ConsentTier.DENIED
            else -> ConsentTier.UNKNOWN
        }
    } catch (e: Exception) {
        LogUtil.logE("ConsentManager.translate: cannot read UMP status, tier=UNKNOWN: ${e.message}")
        ConsentTier.UNKNOWN
    }
}
