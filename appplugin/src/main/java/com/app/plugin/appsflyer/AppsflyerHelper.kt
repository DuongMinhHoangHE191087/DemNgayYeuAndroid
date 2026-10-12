package com.app.plugin.appsflyer

import android.app.Activity
import android.content.Context
import android.util.Log
import com.app.plugin.ads.AdsUtil
import com.app.plugin.until.AppUtil
import com.app.plugin.until.LogUtil
import com.app.plugin.app.AppPluginManager
import com.app.plugin.consent.ConsentRules
import com.app.plugin.consent.ConsentTier
import com.appsflyer.AFAdRevenueData
import com.appsflyer.AFInAppEventParameterName
import com.appsflyer.AFInAppEventType
import com.appsflyer.AdRevenueScheme
import com.appsflyer.AppsFlyerConsent
import com.appsflyer.AppsFlyerConversionListener
import com.appsflyer.AppsFlyerLib
import com.appsflyer.MediationNetwork
import com.appsflyer.attribution.AppsFlyerRequestListener
import java.util.Currency
import java.util.EnumSet
import java.util.concurrent.atomic.AtomicBoolean


/**
 * AppsFlyer theo consent: start sau cổng analytics ([startAfterConsent]), theo tầng mỗi lần nó đổi ([followTier]).
 *
 * Khoá: mọi hàm đổi trạng thái chạy trong khoá của helper (`@Synchronized`). `ConsentState.setTier` giữ khoá của nó khi
 * gọi listener tầng (`AdSdkStartup.applyTier` → [followTier]), nên thứ tự khoá duy nhất là `ConsentState` → helper.
 * Helper không bao giờ gọi `ConsentState` (không đăng ký listener riêng, không đọc qua hàm `@Synchronized` của nó):
 * chiều ngược lại là deadlock khi app dùng CMP khác gọi `ConsentState.setTier` từ luồng nền đúng lúc cổng mở trên main
 * (review C I1).
 */
internal class AppsflyerHelper : AppsFlyerConversionListener {
    public companion object {
        public val instance: AppsflyerHelper by lazy { AppsflyerHelper() }

        /**
         * Consent đưa cho AppsFlyer (6.16.x, 4 trường): `isUserSubjectToGDPR`,
         * `hasConsentForDataUsage`, `hasConsentForAdsPersonalization`, `hasConsentForAdStorage`.
         *
         * `gdprApplies == null` (không biết) xử lý như có GDPR, nên `UNKNOWN` không cấp gì (Điều 13).
         * Chỉ `IABTCF_gdprApplies = 0` mới là người dùng ngoài GDPR, và không thắng `DENIED`: lời từ chối
         * thật đi dưới dạng người dùng GDPR không đồng ý gì, vì `0` có thể là giá trị cũ còn lại khi
         * người dùng đã vào EEA.
         */
        internal fun consentFor(tier: ConsentTier, gdprApplies: Boolean?): AppsFlyerConsent {
            if (ConsentRules.outsideGdprGrantsAll(tier, gdprApplies)) return AppsFlyerConsent(false, null, null, null)
            val usable = tier == ConsentTier.GRANTED || tier == ConsentTier.PARTIAL
            return AppsFlyerConsent(true, usable, tier == ConsentTier.GRANTED, usable)
        }

        private fun describe(c: AppsFlyerConsent) =
            "isUserSubjectToGDPR=${c.isUserSubjectToGDPR} hasConsentForDataUsage=${c.hasConsentForDataUsage} " +
                "hasConsentForAdsPersonalization=${c.hasConsentForAdsPersonalization} " +
                "hasConsentForAdStorage=${c.hasConsentForAdStorage}"
    }

    /**
     * AppsFlyer được chạy không (#12, DOC/66): tầng `GRANTED`/`PARTIAL`, hoặc ngoài GDPR (`IABTCF_gdprApplies = 0`)
     * mà tầng không phải `DENIED` ([ConsentRules.outsideGdprGrantsAll]).
     *
     * - `DENIED` không bao giờ, kể cả khi `IABTCF_gdprApplies = 0`: tài liệu AppsFlyer không nói consent `false`
     *   ngăn thu thập (`setConsentData` là tín hiệu DMA cho Google), còn `stop` thì có ("the SDK stops sending any
     *   data"). Người từ chối không có launch nào tới AppsFlyer, và không doanh thu nào: [logAdRevenue]/[logIapPurchase]
     *   chỉ gọi `AppsFlyerLib` khi AppsFlyer đang chạy (review C Minor 1).
     * - `UNKNOWN` trong GDPR hoặc không biết (`gdprApplies != false`) chưa được chạy: UNKNOWN không bao giờ là đồng ý
     *   (Điều 13). Cổng analytics chỉ xét `canRequestAds()` của UMP, không xét tầng, nên có thể mở khi tầng còn
     *   UNKNOWN (UMP `OBTAINED` mà thiếu `IABTCF_*`, hoặc tầng chưa lưu và UMP chưa cập nhật); AppsFlyer start khi
     *   tầng thành cho phép ([followTier]).
     *
     * Cũng là cổng dừng: đang chạy mà hàm này thành false thì [followTier] `stop(true)` (review cuối I1).
     */
    private fun mayStart(tier: ConsentTier, gdprApplies: Boolean?): Boolean = ConsentRules.tierAllows(tier, gdprApplies)

    /** AppsFlyer đang chạy: đã start, và chưa bị [followTier] stop vì rút consent. */
    @Volatile
    private var started = false

    /** Cổng analytics đã mở: [startAfterConsent] đã được gọi. Trước đó [followTier] không làm gì. */
    @Volatile
    private var gateOpened = false

    /** SDK đã gọi `stop(true)` vì rút consent: lần cấp lại phải `stop(false)` trước khi start. */
    @Volatile
    private var stoppedForConsent = false

    /** `AppsFlyerLib.init` đã chạy trong process: start lại sau `stop(false)` không init lần hai. */
    @Volatile
    private var initialized = false

    /**
     * Các tầng đã có dòng "appsflyer not started" trong process: một dòng mỗi tầng bị từ chối, không lặp mỗi lần hỏi
     * lại (UNKNOWN ở cổng rồi DENIED → hai dòng). Chỉ đọc/ghi trong khoá của helper.
     */
    private val notStartedLogged: MutableSet<ConsentTier> = EnumSet.noneOf(ConsentTier::class.java)

    /** Tầng gần nhất helper được báo ([startAfterConsent], [followTier]); `null` trước lần đầu. Cho dòng log drop. */
    @Volatile
    private var lastTier: ConsentTier? = null

    /** Dòng "appsflyer event dropped" đã log trong process. */
    private val droppedLogged = AtomicBoolean(false)

    /** Bộ lọc partner do SDK đặt trong process này (GPP opt-out lúc start); gỡ ở lần start kế khi GPP có mà không opt-out. */
    @Volatile
    private var sharingFilterApplied = false

    /**
     * Khởi động AppsFlyer sau khi consent đã có câu trả lời (task `analytics` của `AdSdkStartup`), nếu tầng cho
     * phép ([mayStart]). Tầng chưa cho phép (`DENIED`, hoặc `UNKNOWN` trong GDPR) thì không `setConsentData`, không
     * `init`, không `start`, log `appsflyer not started: …` một lần mỗi tầng; [followTier] start sau nếu tầng
     * thành cho phép. Đang chạy thì gọi lại là no-op. Consent được đặt trước `init`/`start`, nên sự kiện launch đầu
     * tiên đã mang nó.
     *
     * [activity]: Activity đang chạy khi start trễ (sau khi người dùng trả lời form, Activity đã
     * resume). Hướng dẫn start trễ của AppsFlyer là `start(activity)`: với application context,
     * AppsFlyer chỉ thấy lần foreground qua lifecycle callback đăng ký lúc start, nên launch có thể chờ
     * tới lần background→foreground kế — mất attribution của người vừa đồng ý. Không có Activity (start
     * trong `Application.onCreate` với câu trả lời cũ, trước khi Activity nào lên) thì dùng app context
     * như tích hợp chuẩn, và log lỗi phòng khi đã có Activity resume mà SDK không biết.
     *
     * Từ đây mỗi lần tầng consent đổi (thu hồi qua privacy options), consent mới tới AppsFlyer qua [followTier]
     * (`AdSdkStartup.applyTier`); helper không đăng ký listener riêng với `ConsentState` (thứ tự khoá, xem lớp). Lỗi
     * thì log, không ném: AppsFlyer không được làm hỏng luồng mở app.
     */
    @Synchronized
    internal fun startAfterConsent(
        context: Context,
        tier: ConsentTier,
        gdprApplies: Boolean?,
        activity: Activity? = null,
    ) {
        lastTier = tier
        if (started) return
        gateOpened = true
        if (!mayStart(tier, gdprApplies)) {
            logNotStarted(tier, gdprApplies)
            return
        }
        try {
            val app = context.applicationContext ?: context
            val idApp = AppUtil.getResourceString(app, "af_app_id")
            if (idApp.isEmpty()) {
                // Game chưa khai báo AppsFlyer (không có string resource `af_app_id`).
                // AppsFlyerLib.init("") ném IllegalArgumentException và làm crash app lúc khởi
                // động — SDK drop-in không được phép đòi tài nguyên mà host có thể không có.
                LogUtil.logE("af_app_id missing — AppsFlyer disabled (add string resource af_app_id to enable)")
                return
            }
            val consent = consentFor(tier, gdprApplies)
            val af = AppsFlyerLib.getInstance()
            af.setConsentData(consent)
            if (!initialized) {
                af.init(idApp, this, app)
                initialized = true
            }
            sharingFilterApplied = AppsflyerGppFilter.apply(af, app, tier, sharingFilterApplied)
            val mode = if (activity != null) "activity" else "appContext"
            if (activity == null) {
                LogUtil.logE(
                    "appsflyer start without activity — launch may be deferred until the next " +
                        "foreground if an Activity is already resumed"
                )
            }
            af.start(activity ?: app, idApp, launchListener(mode))
            started = true
            // Log thẳng, không qua cờ LogUtil: lần mở app có câu trả lời cũ chạy trong
            // Application.onCreate, trước khi cờ log được bật; cần thấy dòng này khi kiểm trên máy.
            Log.d(
                LogUtil.TAG,
                "appsflyer started after consent: ${ConsentRules.scopeLabel(tier, gdprApplies)} ${describe(consent)} start=$mode",
            )
        } catch (e: Exception) {
            LogUtil.logE("AppsFlyer start after consent failed: ${e.message}")
        }
    }

    /**
     * Log thẳng (không qua cờ LogUtil, như dòng "started"), một lần mỗi tầng bị từ chối trong process (review C Minor
     * 6/7): dòng `consent tier=DENIED` luôn có khi người dùng tới DENIED, kể cả khi cổng đã mở lúc UNKNOWN. Gọi trong
     * khoá của helper.
     */
    private fun logNotStarted(tier: ConsentTier, gdprApplies: Boolean?) {
        if (!notStartedLogged.add(tier)) return
        Log.d(LogUtil.TAG, "appsflyer not started: ${ConsentRules.scopeLabel(tier, gdprApplies)} (gdprApplies=$gdprApplies)")
    }

    /**
     * Doanh thu/IAP bị bỏ vì AppsFlyer không chạy (chưa start vì tầng, hoặc đã stop vì rút consent): log thẳng một lần
     * mỗi process (Điều 7, không câm; log của AppsFlyer đặt `LogLevel.NONE`). Không qua `LogUtil.logD`: ở mức log mặc
     * định nó câm, và dòng một-lần sẽ bị tiêu mất trước khi app bật log.
     */
    private fun logDropped() {
        if (!droppedLogged.compareAndSet(false, true)) return
        Log.d(LogUtil.TAG, "appsflyer event dropped: not started (tier=${lastTier ?: "none"})")
    }

    /**
     * Theo tầng consent mỗi lần nó đổi (#12, DOC/66): `AdSdkStartup.applyTier` gọi, sau Consent Mode và FCM. Chỉ có
     * việc khi cổng analytics đã mở ([startAfterConsent] đã được gọi); trước đó AppsFlyer vẫn chờ cổng. Đây là đường
     * duy nhất đưa tầng mới tới AppsFlyer (review C I1: không còn listener riêng của helper).
     * - Đang chạy mà tầng không còn cho phép ([mayStart] false: `DENIED`, hoặc `UNKNOWN` trong GDPR/không biết — rút
     *   consent qua privacy options, `ConsentManager.reset()`, trả lời lại mà `IABTCF_*` không đọc được): consent từ
     *   chối trước (nếu `stop` lỗi thì AppsFlyer vẫn giữ lời từ chối), rồi `stop(true)` đúng một lần — AppsFlyer ngừng
     *   gửi mọi dữ liệu. Cổng dừng trùng cổng start, như FCM và Meta (review cuối I1).
     * - Đang chạy mà tầng vẫn cho phép (`GRANTED`/`PARTIAL`, hoặc `UNKNOWN` ngoài GDPR): chỉ đổi consent
     *   ([updateConsent], `setConsentData`). Đã stop thì không đổi consent: AppsFlyer đã dừng, không có gì để báo.
     * - Chưa chạy (bỏ qua ở cổng vì tầng chưa cho phép, hoặc đã stop vì rút consent) mà tầng thành cho phép
     *   ([mayStart]): `stop(false)` nếu chính SDK đã stop, rồi start theo [startAfterConsent] (trên [activity] nếu có).
     *   Tầng vẫn không cho phép: log `appsflyer not started: …` nếu tầng này chưa có dòng.
     *
     * Lỗi thì log, không ném: AppsFlyer không được làm hỏng listener tầng.
     */
    @Synchronized
    internal fun followTier(context: Context, tier: ConsentTier, gdprApplies: Boolean?, activity: Activity?) {
        lastTier = tier
        if (!gateOpened) return
        try {
            val app = context.applicationContext ?: context
            if (started && !mayStart(tier, gdprApplies)) {
                updateConsent(tier, gdprApplies)
                AppsFlyerLib.getInstance().stop(true, app)
                started = false
                stoppedForConsent = true
                Log.d(LogUtil.TAG, "appsflyer stopped: consent withdrawn (${ConsentRules.scopeLabel(tier, gdprApplies)})")
            } else if (started) {
                updateConsent(tier, gdprApplies)
            } else if (mayStart(tier, gdprApplies)) {
                if (stoppedForConsent) {
                    AppsFlyerLib.getInstance().stop(false, app)
                    stoppedForConsent = false
                    Log.d(LogUtil.TAG, "appsflyer resumed: consent granted again (${ConsentRules.scopeLabel(tier, gdprApplies)})")
                }
                startAfterConsent(context, tier, gdprApplies, activity)
            } else {
                logNotStarted(tier, gdprApplies)   // chưa chạy, tầng vẫn không cho phép: một dòng mỗi tầng
            }
        } catch (e: Exception) {
            LogUtil.logE("AppsFlyer follow tier failed: ${e.message}")
        }
    }

    /**
     * Như process mới, chỉ cho test (`AdSdkStartup.resetForTest`): chưa start, cổng chưa mở, chưa stop/init, chưa đặt bộ
     * lọc partner, dòng "not started"/"event dropped" chưa log.
     */
    @JvmSynthetic
    @Synchronized
    internal fun resetForTest() {
        started = false
        gateOpened = false
        stoppedForConsent = false
        initialized = false
        notStartedLogged.clear()
        lastTier = null
        droppedLogged.set(false)
        sharingFilterApplied = false
    }

    /**
     * Kết quả request launch (sự kiện đầu tiên sau start). Lỗi (dev key sai, mạng) không được câm
     * (Điều 7); dòng thành công/lỗi cũng là bằng chứng trên máy rằng launch đã đi ngay sau consent —
     * log riêng của AppsFlyer mặc định tắt (`LogLevel.NONE`).
     */
    private fun launchListener(mode: String) = object : AppsFlyerRequestListener {
        override fun onSuccess() {
            Log.d(LogUtil.TAG, "appsflyer launch request sent (start=$mode)")
        }

        override fun onError(code: Int, description: String) {
            LogUtil.logE("appsflyer launch request failed: code=$code $description (start=$mode)")
        }
    }

    /** Đưa consent của tầng mới cho AppsFlyer đang chạy. Lỗi thì log, không ném. */
    private fun updateConsent(tier: ConsentTier, gdprApplies: Boolean?) {
        try {
            val consent = consentFor(tier, gdprApplies)
            AppsFlyerLib.getInstance().setConsentData(consent)
            Log.d(LogUtil.TAG, "appsflyer consent updated: ${ConsentRules.scopeLabel(tier, gdprApplies)} ${describe(consent)}")
        } catch (e: Exception) {
            LogUtil.logE("AppsFlyer consent update failed: ${e.message}")
        }
    }

    override fun onConversionDataSuccess(conversionDataMap: MutableMap<String, Any>?) {
        if (conversionDataMap != null) {
            Log.d(LogUtil.TAG, "af onConversionDataSuccess")
            for (attr in conversionDataMap) {
                Log.d(LogUtil.TAG, "af onConversionDataSuccess attribute:${attr.key}=${attr.value}")
            }
            val status: String = conversionDataMap["af_status"]?.toString() ?: ""
            if (status == "Organic") {
                // Business logic for Organic conversion goes here.
            } else {
                // Business logic for Non-organic conversion goes here.
            }
        } else {
            Log.d(LogUtil.TAG, "af onConversionDataSuccess but map null")
        }
    }

    override fun onConversionDataFail(p0: String?) {
        Log.d(LogUtil.TAG, "af onConversionDataFail=$p0")
    }

    override fun onAppOpenAttribution(p0: MutableMap<String, String>?) {
        Log.d(LogUtil.TAG, "af onAppOpenAttribution")
    }

    override fun onAttributionFailure(p0: String?) {
        Log.d(LogUtil.TAG, "af onAttributionFailure=$p0")
    }

    /**
     * Doanh thu quảng cáo gửi về AppsFlyer, chỉ khi AppsFlyer đang chạy (đã start theo tầng, chưa stop vì rút consent);
     * không thì bỏ và log một lần ([logDropped]).
     */
    public fun logAdRevenue(
        mediation: Int,
        net: String,
        adUnitId: String,
        adType: String,
        adrev: Double,
        currency: String
    ) {
        if (!started) {
            logDropped()
            return
        }
        val appsflyer = AppsFlyerLib.getInstance()
        var menet: MediationNetwork = MediationNetwork.GOOGLE_ADMOB
        if (mediation == 1) {
            menet = MediationNetwork.APPLOVIN_MAX
        }
        val adRevenueData = AFAdRevenueData(
            net, menet, currency, adrev
        )
        val additionalParameters: MutableMap<String, Any> = HashMap()
        //additionalParameters[AdRevenueScheme.COUNTRY] = "US"
        additionalParameters[AdRevenueScheme.AD_UNIT] = adUnitId
        additionalParameters[AdRevenueScheme.AD_TYPE] = adType
        //additionalParameters[AdRevenueScheme.PLACEMENT] = "place"
        appsflyer.logAdRevenue(adRevenueData, additionalParameters)
    }

    /** Như trên, kèm khoá đối soát [impId] = `imp_id` của `ad_revenue` server (1.7.0 Task 3); dòng `rev af:` cho H6a. */
    internal fun logAdRevenue(mediation: Int, net: String, adUnitId: String, adType: String, adrev: Double, currency: String, impId: String) {
        Log.i("AppPlugin", "rev af: imp=$impId started=${if (started) 1 else 0}")
        logAdRevenue(mediation, net, adUnitId, adType, adrev, currency)
    }

    /**
     * Doanh thu IAP gửi về AppsFlyer.
     *
     * ═══ VÌ SAO HÀM NÀY QUAN TRỌNG HƠN VẺ NGOÀI ═══
     *
     * Trước bản này nó là một thân hàm RỖNG và không ai gọi. Hệ quả: AppsFlyer chỉ nhìn thấy
     * doanh thu quảng cáo, không thấy một đồng IAP nào. Mọi con số ROAS mà chiến dịch UA dùng
     * để tối ưu đều thiếu hẳn một vế — và với game có IAP mạnh, đó là vế lớn hơn.
     *
     * Kiểu sai này không bao giờ báo lỗi: chiến dịch vẫn chạy, dashboard vẫn có số, chỉ là
     * thuật toán đấu giá đang tối ưu trên một nửa sự thật.
     *
     * ═══ GỬI NGUYÊN BẢN, KHÔNG QUY ĐỔI ═══
     *
     * `revenue` + `currency` đi thẳng theo đơn vị của store. AppsFlyer tự quy đổi bằng tỉ giá
     * của họ theo đúng ngày giao dịch — chính xác hơn bất kỳ bảng tỉ giá nào ta nhúng vào
     * client (cùng lý do đã nêu ở `com.app.plugin.iap.FxRates`).
     *
     * Chỉ gửi khi AppsFlyer đang chạy, như [logAdRevenue]: người từ chối không có doanh thu nào tới AppsFlyer.
     */
    public fun logIapPurchase(
        productId: String,
        revenue: Double,
        currency: String,
        quantity: Int = 1,
        isSubscription: Boolean = false
    ) {
        if (!started) {
            logDropped()
            return
        }
        try {
            val context = AppPluginManager.instance.getContext() ?: run {
                LogUtil.logE("af logIapPurchase: chua co context")
                return
            }
            val params: MutableMap<String, Any> = HashMap()
            params[AFInAppEventParameterName.REVENUE] = revenue
            params[AFInAppEventParameterName.CURRENCY] = currency
            params[AFInAppEventParameterName.CONTENT_ID] = productId
            params[AFInAppEventParameterName.QUANTITY] = quantity
            // Subscription dùng event riêng: AppsFlyer và các mạng UA xử lý doanh thu định kỳ
            // khác doanh thu một lần. Gộp làm một sẽ khiến LTV dự báo bị lệch.
            val event = if (isSubscription) AFInAppEventType.SUBSCRIBE else AFInAppEventType.PURCHASE
            AppsFlyerLib.getInstance().logEvent(context, event, params)
        } catch (e: Exception) {
            // Báo cáo doanh thu không bao giờ được làm hỏng luồng mua hàng của người dùng.
            LogUtil.logE("af logIapPurchase failed: ${e.message}")
        }
    }

    @Deprecated(
        "Thân hàm rỗng, không ai gọi. Dùng logIapPurchase().",
        ReplaceWith("logIapPurchase(productId, revenue, currency)")
    )
    public fun logIAp() {
    }
}