package com.app.plugin.ads

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.app.plugin.BuildConfig
import com.app.plugin.SdkKillSwitch
import com.app.plugin.ads.adsfb.AdsFbMy
import com.app.plugin.ads.adsmob.AdsMobMy
import com.app.plugin.ads.core.AdPreloader
import com.app.plugin.ads.core.BiddingPartners
import com.app.plugin.app.MetaSdkStarter
import com.app.plugin.appsflyer.AppsflyerHelper
import com.app.plugin.attribution.InstallReferrerHelper
import com.app.plugin.consent.AnalyticsConsent
import com.app.plugin.consent.ConsentManager
import com.app.plugin.consent.ConsentRules
import com.app.plugin.consent.ConsentState
import com.app.plugin.consent.ConsentTier
import com.app.plugin.consent.FcmConsent
import com.app.plugin.consent.GppConsent
import com.app.plugin.consent.GppSignals
import com.app.plugin.consent.TcfConsent
import com.app.plugin.firbase.FirHelper
import com.app.plugin.license.LicenseGate
import com.app.plugin.until.LogUtil

/**
 * Nơi duy nhất khởi tạo SDK của các mạng quảng cáo, qua [AdSdkStartGate]: app yêu cầu, và UMP
 * cho phép. Ai gọi, gọi lúc nào cũng được: gọi trước consent (Speaker Clean gọi ngay ở `onCreate`
 * của Activity) thì khởi tạo được hoãn tới khi UMP trả lời.
 *
 * Các việc tách riêng vì app bật riêng:
 * - mạng quảng cáo: MobileAds (kéo theo adapter mediation), Meta Audience Network, AppLovin MAX,
 *   qua `AdsMobMy.startSdkInit`. Meta chỉ khởi tạo khi tầng cho phép ([ConsentRules.tierAllows], như AppsFlyer; xem
 *   [metaRefusal]): không cho `DENIED`, không cho `UNKNOWN` trong vùng GDPR; tầng cho phép sau đó trong process thì
 *   khởi tạo lúc đó;
 * - Meta SDK (app events), qua `AppPluginBase.initMetaSdkAfterConsent`; cùng cổng với Meta AN, khởi tạo khi tầng cho
 *   phép sau đó ([MetaSdkStarter.followTier]);
 * - analytics: Consent Mode v2 của Firebase Analytics, FCM (auto-init và token, chỉ khi tầng cho
 *   phép) và AppsFlyer (chỉ khi tầng cho phép; stop khi rút consent), qua `AppPluginManager.initPlugin`.
 *   Riêng Consent Mode và FCM theo mỗi lần tầng đổi ngay từ lúc request, không chờ cổng.
 *
 * `AppPluginBase.onCreate` không khởi tạo mạng nào.
 *
 * Kill switch `ads` ([SdkKillSwitch]) chặn khởi tạo mạng; dù khởi tạo hay bị chặn, [startAdNetworks] gửi
 * một event `sdk_health` ([SdkHealth]). Phiên đã bỏ khởi tạo mạng giữ quảng cáo tắt tới lần mở kế
 * ([adNetworksKilled]), kể cả khi kill được gỡ giữa phiên.
 *
 * Load đến trước consent được giữ trong hàng chờ của từng mạng tới khi mạng khởi tạo. Nếu kill tới trong
 * chính phiên đó (lần fetch đầu tiên, trước câu trả lời UMP), mạng không bao giờ khởi tạo và các load đã
 * giữ ấy không bao giờ chạy; watchdog của slot bỏ chúng sau 60 s (báo `load timeout`, Task I2), lượt giữ đã bỏ
 * không chạy nữa. Không thả chúng ra: thả
 * là để mạng tự khởi tạo và load quảng cáo trong lúc kill đang bật. Meta bị bỏ qua vì consent thì khác: hàng chờ của
 * nó đóng lại (`SdkReadyQueue.close`), load fb đang giữ và load đến sau báo `onLoadFailed` ngay, không chạy.
 */
internal object AdSdkStartup {

    private class Task(val name: String, val start: (Context) -> Unit) {
        @Volatile
        var gate = AdSdkStartGate()   // chỉ resetForTest thay
    }

    private val adNetworks = Task("ad networks") { startAdNetworks(it) }
    private val metaCore = Task("meta sdk") { MetaSdkStarter.start(it) }
    private val analytics = Task("analytics") { startAnalytics(it) }
    private val tasks = listOf(adNetworks, metaCore, analytics)

    init { AdPreloader.registerRemote() }   // 2.3.0 PRELOAD: RC `ads_preload_android`, trước fetch của `initPlugin`

    // UMP đọc câu trả lời đã lưu trên luồng nền sau lần gọi đầu: ngay lúc mở app nó còn báo
    // UNKNOWN, khoảng 0,2 s sau mới báo đúng, còn lần cập nhật qua mạng mất 1–2,5 s (đo trên
    // emulator API 37). Chỉ chờ listener thì người dùng đã trả lời ở phiên trước phải chờ cả lần
    // cập nhật đó. Hỏi lại dày trong vài giây đầu; đọc canRequestAds() chỉ là đọc bộ nhớ.
    private const val RECHECK_INTERVAL_MS = 100L
    private const val RECHECK_WINDOW_MS = 3_000L

    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var recheckUntilMs = 0L

    private val onConsentUpdated: () -> Unit = { recheck("consent allows ad requests") }

    private val recheckTick = object : Runnable {
        override fun run() {
            val stillWaiting = recheck("consent on record")
            if (stillWaiting && System.currentTimeMillis() < recheckUntilMs) {
                mainHandler.postDelayed(this, RECHECK_INTERVAL_MS)
            }
        }
    }

    /** @return `true` khi còn việc đang chờ consent. */
    private fun recheck(reason: String): Boolean {
        val context = appContext ?: return false
        val canStart = ConsentManager.canStartAdSdks(context)
        for (task in tasks) {
            if (task.gate.onConsentChanged(canStart)) {
                LogUtil.logD("${task.name}: $reason, starting")
                task.start(context)
            }
        }
        return tasks.any { it.gate.isWaitingForConsent }
    }

    /** Đồng hồ của `since_request_ms` trong `sdk_health`; test thay, [resetForTest] trả về đồng hồ thật. */
    @Volatile
    internal var nowMs: () -> Long = { System.currentTimeMillis() }

    /** Lần đầu app request mạng quảng cáo (trước cổng consent), theo [nowMs]; 0 khi chưa. */
    @Volatile
    private var adNetworksRequestedAtMs = 0L

    /**
     * Phiên này đã bỏ khởi tạo mạng vì kill switch `ads`. Không bao giờ về `false` trong process (trừ
     * [resetForTest]): mạng chưa khởi tạo thì mọi load sẽ bị giữ mãi, nên `AdsHelper` coi quảng cáo còn bị
     * kill tới lần mở kế dù khoá đã gỡ.
     */
    @Volatile
    internal var adNetworksKilled: Boolean = false
        private set

    /** Lúc các mạng quảng cáo bắt đầu khởi tạo; 0 khi chưa (đang chờ app yêu cầu hoặc chờ consent). */
    @Volatile
    var adNetworksStartedAtMs: Long = 0L
        private set

    fun requestAdNetworks(context: Context) = request(adNetworks, context)

    fun requestMetaSdk(context: Context) = request(metaCore, context)

    /**
     * Consent Mode và FCM theo tầng từ ngay lúc này, kể cả khi cổng chưa mở: một lần thu hẹp (UMP báo
     * REQUIRED lại, hết hạn, thu hồi) phải tới Firebase/FCM ngay, không chờ cổng. Listener đăng ký
     * trước khi đọc tầng hay cổng, để không lỡ một `setTier` xen giữa. AppsFlyer vẫn chờ cổng.
     */
    fun requestAnalytics(context: Context) {
        val app = context.applicationContext ?: context
        followTier(app)
        request(analytics, context)
        gppContext = app
        ConsentManager.addConsentUpdatedListener(onUmpUpdated)   // sau listener cổng của request: cổng mở chạy trước
    }

    private fun request(task: Task, context: Context) {
        if (task === adNetworks && adNetworksRequestedAtMs == 0L) adNetworksRequestedAtMs = nowMs()
        val app = context.applicationContext ?: context
        appContext = app
        ConsentManager.addConsentUpdatedListener(onConsentUpdated)
        if (task.gate.request(ConsentManager.canStartAdSdks(app))) {
            LogUtil.logD("${task.name}: consent on record, starting")
            task.start(app)
        } else if (task.gate.isWaitingForConsent) {
            LogUtil.logD("${task.name}: waiting for the consent answer before init " +
                "(UMP status ${ConsentManager.consentStatusForLog(app)})")
            recheckUntilMs = System.currentTimeMillis() + RECHECK_WINDOW_MS
            mainHandler.removeCallbacks(recheckTick)
            mainHandler.postDelayed(recheckTick, RECHECK_INTERVAL_MS)
        }
    }

    private fun startAdNetworks(context: Context) {
        val kill = SdkKillSwitch.current
        if (kill.killsAds(BuildConfig.SDK_VERSION)) {
            // Chỉ tắt (Điều 13): không mạng nào khởi tạo, load/show bị AdsHelper.isDisableAds chặn.
            LogUtil.logE("kill switch: ads — không khởi tạo mạng quảng cáo")
            adNetworksKilled = true
            reportHealth(context, SdkHealth.NETWORKS_KILLED, kill)
            return
        }
        // SA1 (§C): giấy phép INVALID → không mạng nào (LicenseGate đã logE một lần; AdsHelper.isDisableAds chặn load/show).
        // PENDING → chờ verdict trên luồng riêng (≤ 2 s, main không chờ) rồi chạy lại hàm này trên main.
        if (!LicenseGate.allowsAdNetworks(context) { startAdNetworks(context) }) {
            if (LicenseGate.blocksAds) reportHealth(context, LicenseGate.HEALTH_NETWORKS, kill)   // một lần: INVALID hoặc lượt chạy lại
            return
        }
        adNetworksStartedAtMs = System.currentTimeMillis()
        val startedAt = nowMs()
        val starters = networkStarters
        // 1.9.0 T1 (Hợp đồng A): registry partner ngoài main, trước admob; `sdk_health` chờ nó để mang `partners=`.
        val partners = BiddingPartners.startCheck(context)
        starters.admob(context)
        val metaStarted = startMetaUnlessRefused(context, starters)
        if (AdsHelper.isMaxSdkAvailable) {
            starters.max(context)
        }
        val networks = SdkHealth.networks(AdsHelper.isMaxSdkAvailable, meta = metaStarted)
        partners.thenAccept { reportHealth(context, SdkHealth.withPartners(networks, it), kill, startedAt) }
        // 2.3.0 PRELOAD (D15-230): chỉ tới đây khi consent cho phép, kill không chặn, giấy phép VALID; một lần/process.
        AdPreloader.schedule()
    }

    /**
     * Meta Audience Network không có trong GVL mà dựa vào Additional Consent (`IABTCF_AddtlConsent`), SDK chưa đọc
     * chuỗi đó (P1, DOC/66). Chỉ khởi tạo khi tầng cho phép ([ConsentRules.tierAllows]: `GRANTED`/`PARTIAL`, hoặc ngoài
     * GDPR), như AppsFlyer: `DENIED` (thắng `IABTCF_gdprApplies = 0`) và `UNKNOWN` trong GDPR hoặc không biết thì không
     * (FX, Điều 13: UNKNOWN không bao giờ là đồng ý).
     *
     * @return mô tả tầng cho log từ chối (`consent tier=<tầng> in|outside GDPR scope`), hoặc `null` khi tầng cho phép.
     */
    private fun metaRefusal(context: Context): String? {
        val tier = ConsentState.tier()
        val gdprApplies = TcfConsent.gdprApplies(context)
        if (ConsentRules.tierAllows(tier, gdprApplies)) return null
        return ConsentRules.scopeLabel(tier, gdprApplies)
    }

    /** Meta bị bỏ qua lúc khởi tạo mạng vì consent; [applyTier] khởi tạo nó khi tầng cho phép. Khoá: [metaLock]. */
    private var metaHeldForConsent = false

    private val metaLock = Any()

    /**
     * Khởi tạo Meta, trừ khi consent chặn ([metaRefusal]). Bị chặn thì đóng hàng chờ của Meta: load fb đang giữ và
     * load đến sau báo fail ngay (waterfall đi tiếp), không chờ một mạng không khởi tạo.
     *
     * Quyết định và đóng hàng là một bước trong [metaLock] (review C Minor 4): một lần start muộn trên luồng khác
     * ([startMetaIfNowAllowed]) chỉ chạy trước cả hai hoặc sau cả hai, không chen giữa để rồi bị `close()` đóng lại hàng
     * đã mở. Placement được báo fail ngoài khoá ([SdkReadyQueue.closeDeferred]).
     *
     * @return `true` khi đã yêu cầu khởi tạo Meta.
     */
    private fun startMetaUnlessRefused(context: Context, starters: NetworkStarters): Boolean {
        var notifyRefused: () -> Unit = {}
        val refusal = synchronized(metaLock) {
            metaRefusal(context).also {
                metaHeldForConsent = it != null
                if (it != null) notifyRefused = AdsFbMy.instance.readyQueue.closeDeferred()
            }
        }
        if (refusal == null) {
            starters.meta(context)
            return true
        }
        LogUtil.logE("meta an: not started, $refusal")
        notifyRefused()
        return false
    }

    /**
     * Meta đã bị bỏ qua vì consent mà tầng giờ cho phép (đồng ý lại qua privacy options, trong cùng process): khởi
     * tạo lúc này, một lần. "Cho phép" là [ConsentRules.tierAllows] (`GRANTED`/`PARTIAL`, hoặc ngoài GDPR), không phải
     * "không còn DENIED": `UNKNOWN` không bao giờ là đồng ý (Điều 13; review C Minor 5), Meta chờ tiếp. Hàng chờ mở lại
     * trong [metaLock], trước khi khởi tạo, để load đến trong lúc khởi tạo được giữ như bình thường. Lỗi thì log, không
     * ném (chạy trong listener tầng).
     */
    private fun startMetaIfNowAllowed(context: Context) {
        try {
            val start = synchronized(metaLock) {
                val tier = ConsentState.tier()
                (metaHeldForConsent && ConsentRules.tierAllows(tier, TcfConsent.gdprApplies(context))).also {
                    if (it) {
                        metaHeldForConsent = false
                        AdsFbMy.instance.readyQueue.reopen()
                    }
                }
            }
            if (!start) return
            Log.d(LogUtil.TAG, "meta an: consent now allows it, starting (tier=${ConsentState.tier()})")
            networkStarters.meta(context)
        } catch (e: Exception) {
            LogUtil.logE("meta an: start after consent change failed: ${e.message}")
        }
    }

    /** Lời gọi khởi tạo của từng mạng quảng cáo. */
    internal class NetworkStarters(
        val admob: (Context) -> Unit = { AdsMobMy.instance.startNetwork(it) },
        val meta: (Context) -> Unit = { AdsFbMy.instance.startNetwork(it) },
        val max: (Context) -> Unit = { MaxBridge.get()?.startNetwork(it) },
    )

    /**
     * Khởi tạo mạng thật; test thay (MobileAds chạy trên coroutine IO, không khởi tạo được trong JVM), [resetForTest]
     * trả về bản thật.
     */
    @Volatile
    internal var networkStarters = NetworkStarters()

    /**
     * `sdk_health`, một lần mỗi process (cổng một-lần). Log thẳng, không qua cờ LogUtil, để kiểm trên máy
     * (một dòng mỗi process, không dữ liệu cá nhân). Lỗi thì log, không ném (Điều 7).
     */
    private fun reportHealth(context: Context, networks: String, kill: SdkKillSwitch, at: Long = nowMs()) {
        try {
            val requestedAt = adNetworksRequestedAtMs
            val params = SdkHealth.withBuild(SdkHealth.withLicense(SdkHealth.withInitMs(SdkHealth.params(
                sdkVersion = BuildConfig.SDK_VERSION,
                consentTier = ConsentState.tier(),
                gdprApplies = TcfConsent.gdprApplies(context),
                networks = networks,
                sinceRequestMs = if (requestedAt > 0L) (at - requestedAt).coerceAtLeast(0L) else -1L,
                kill = kill.label(BuildConfig.SDK_VERSION),
            )), LicenseGate.label))
            Log.d(LogUtil.TAG, "${SdkHealth.EVENT} $params")
            FirHelper.instance.logEvent(SdkHealth.EVENT, SdkHealth.toBundle(params))
        } catch (e: Exception) {
            LogUtil.logE("${SdkHealth.EVENT} failed: ${e.message}")
        }
    }

    /**
     * FCM theo tầng: token lấy lần đầu tầng cho phép; rút consent thì xoá token, và lần cấp lại lấy token mới (xem
     * [FcmConsent.Follower]).
     */
    @Volatile
    private var fcm = newFcmFollower()

    private fun newFcmFollower() = FcmConsent.Follower { done -> FirHelper.instance.fetchFcmTokenAfterConsent(done) }

    /** Listener tầng của [requestAnalytics]; một cho cả process. */
    @Volatile
    private var tierListener: ((ConsentTier) -> Unit)? = null

    /**
     * Đăng ký listener tầng nếu nó chưa nằm trong [ConsentState] (theo đăng ký thật, không theo cờ riêng:
     * `ConsentState.resetForTest` gỡ nó mà không qua đây).
     *
     * Cạnh khoá (review N4): giữ monitor của `AdSdkStartup` khi gọi `ConsentState.hasOnChange`/`onChange` (lấy monitor
     * của `ConsentState`), tức `AdSdkStartup` → `ConsentState`. Không có cạnh ngược vì [applyTier] (chạy trong khoá của
     * `ConsentState`) không gọi hàm `@Synchronized` nào của `AdSdkStartup`; thêm một lời gọi như vậy là tạo deadlock.
     */
    @Synchronized
    private fun followTier(app: Context) {
        tierListener?.let { if (ConsentState.hasOnChange(it)) return }
        val listener: (ConsentTier) -> Unit = { applyTier(app) }
        tierListener = listener
        ConsentState.onChange(listener)
    }

    /**
     * GPP (`IABGPP_*`) của lần [applyTier] gần nhất; `null` khi không có GPP hoặc chưa áp lần nào. Chỉ đọc bộ nhớ: báo
     * cáo của `MonetizationSdk` dùng nó mà không đọc prefs trên main. Chỉ [applyTier] ghi (test đặt thẳng).
     */
    @Volatile
    internal var appliedGpp: GppSignals? = null

    /** Context của [requestAnalytics], cho [onUmpUpdated]; `null` khi analytics chưa được yêu cầu. */
    @Volatile
    private var gppContext: Context? = null

    /**
     * Listener UMP ([ConsentManager.addConsentUpdatedListener], main thread): người Mỹ đổi lựa chọn GPP qua Privacy
     * options mà tầng không đổi (vẫn `GRANTED`) thì `ConsentState.onChange` không bắn — đánh giá lại GPP ở đây.
     */
    private val onUmpUpdated: () -> Unit = { gppContext?.let { reapplyIfGppChanged(it) } }

    /**
     * Đọc lại GPP (luồng listener UMP, như `IABTCF_*` trong `ConsentManager.updateTier`); khác lần [applyTier] gần nhất
     * thì chạy lại [applyTier] với GPP mới. Tầng đổi thì listener tầng đã chạy [applyTier] trước (trong `setTier`), GPP
     * trùng: không chạy hai lần. Chưa áp lần nào và không có GPP: không có gì để áp (cổng mở sẽ chạy [applyTier]). Lỗi
     * thì log, không ném.
     */
    private fun reapplyIfGppChanged(context: Context) {
        try {
            val gpp = GppConsent.read(context)
            if (gpp == appliedGpp) return
            Log.d(LogUtil.TAG, "consent updated, tier unchanged: ${ConsentRules.gppLabel(gpp)}, applying again")
            applyTier(context, gpp)
        } catch (e: Exception) {
            LogUtil.logE("consent: GPP re-evaluation failed: ${e.message}")
        }
    }

    private fun applyTier(context: Context) = applyTier(context, GppConsent.read(context))

    /**
     * Consent Mode, FCM rồi AppsFlyer theo tầng hiện tại. Consent Mode và FCM chỉ cấp khi tầng cho phép nên chạy
     * được trước khi cổng mở; AppsFlyer ([AppsflyerHelper.followTier]) chỉ có việc sau khi cổng đã mở: stop khi tầng
     * thành `DENIED`, start khi tầng thành cho phép (#12), đổi consent khi đang chạy (đường duy nhất, review C I1).
     * Chạy trong khoá của `ConsentState` (listener tầng): không bước nào được gọi ngược vào `ConsentState` trong khi giữ
     * khoá riêng của nó. Rồi GPP [gpp] (đọc một lần, trên luồng listener như `IABTCF_*`) cho mạng đã khởi tạo
     * ([applyGppToStartedNetworks]). Rồi Meta AN và Meta SDK (app events), nếu đã bị bỏ qua vì
     * consent và tầng giờ cho phép (P1) — lúc khởi tạo chúng tự đọc GPP. Cuối cùng Install Referrer (2.4.2): chỉ xếp
     * việc lên luồng nền, consent xét ở đó ([InstallReferrerHelper.captureAfterConsent]). Mỗi bước tự bắt lỗi và log (Điều 7).
     */
    private fun applyTier(context: Context, gpp: GppSignals?) {
        appliedGpp = gpp
        AnalyticsConsent.apply(context, gpp)
        fcm.follow(context)
        AppsflyerHelper.instance.followTier(
            context,
            ConsentState.tier(),
            TcfConsent.gdprApplies(context),
            ConsentManager.liveConsentActivity(),
        )
        applyGppToStartedNetworks(context, gpp)
        startMetaIfNowAllowed(context)
        MetaSdkStarter.followTier()
        InstallReferrerHelper.captureAfterConsent(context)
    }

    /**
     * GPP cho MAX, Meta AN và Meta SDK đã khởi tạo (`setDoNotSell`, `setDataProcessingOptions` gọi lại được). Không có
     * GPP thì không gọi gì. Không xét tầng: opt-out bang Mỹ không phụ thuộc `tierAllows`. AppsFlyer không ở đây: bộ lọc
     * chỉ áp lúc start ([AppsflyerHelper.startAfterConsent]).
     */
    private fun applyGppToStartedNetworks(context: Context, gpp: GppSignals?) {
        if (gpp == null) return
        MaxBridge.get()?.followGpp(context, gpp)   // null = không có SDK AppLovin (F2-MAX)
        AdsFbMy.instance.followGpp(gpp)
        MetaSdkStarter.followGpp(gpp)
    }

    /**
     * Cổng mở: đặt Consent Mode và FCM theo tầng (tầng có thể không đổi từ lúc request, nên listener
     * chưa chạy), rồi start AppsFlyer nếu tầng cho phép (`AppsflyerHelper.mayStart`). Chạy trong
     * `Application.onCreate` hoặc trên main looper, không được ném.
     *
     * AppsFlyer nhận Activity của luồng consent ([ConsentManager.liveConsentActivity]): cổng mở từ câu
     * trả lời của chính luồng đó (`updateTier` → listener → [recheck]), nên Activity ấy đang chạy.
     * Không chọn `AdsHelper`: nó chỉ có Activity khi app kế thừa `AppPluginBase`. Mở trong
     * `Application.onCreate` (câu trả lời cũ) thì chưa có Activity nào: AppsFlyer dùng app context.
     */
    private fun startAnalytics(context: Context) {
        applyTier(context)
        AppsflyerHelper.instance.startAfterConsent(
            context,
            ConsentState.tier(),
            TcfConsent.gdprApplies(context),
            ConsentManager.liveConsentActivity(),
        )
    }

    /**
     * Đưa object về như lúc process mới mở: cổng một-lần của mọi task, Follower FCM (token lấy lại
     * được), listener tầng (gỡ khỏi `ConsentState`), listener UMP và cửa sổ hỏi lại, mốc request và
     * đồng hồ của `sdk_health`; cùng Meta SDK (chưa khởi tạo, không giữ chờ), AppsFlyer (chưa start) và Activity của luồng
     * consent mà task analytics đưa cho AppsFlyer. Chỉ cho test.
     */
    @JvmSynthetic
    @Synchronized
    internal fun resetForTest() {
        tasks.forEach { it.gate = AdSdkStartGate() }
        fcm = newFcmFollower()
        FcmConsent.resetForTest()   // làn token là trạng thái process (Task J2): việc treo của test trước không chặn test sau
        AnalyticsConsent.resetForTest()
        com.app.plugin.ads.core.UnitGate.resetForTest() // 2.4.4 INFLIGHT: trạng thái tĩnh của cổng unit
        tierListener?.let { ConsentState.removeOnChange(it) }
        tierListener = null
        ConsentManager.removeConsentUpdatedListener(onConsentUpdated)
        ConsentManager.removeConsentUpdatedListener(onUmpUpdated)
        gppContext = null
        appliedGpp = null
        mainHandler.removeCallbacks(recheckTick)
        appContext = null
        recheckUntilMs = 0L
        adNetworksStartedAtMs = 0L
        adNetworksRequestedAtMs = 0L
        adNetworksKilled = false
        nowMs = { System.currentTimeMillis() }
        networkStarters = NetworkStarters()
        synchronized(metaLock) { metaHeldForConsent = false }
        AdsFbMy.instance.readyQueue.resetForTest()
        AdsFbMy.instance.resetForTest()
        MaxBridge.get()?.resetForTest()
        MetaSdkStarter.resetForTest()
        AppsflyerHelper.instance.resetForTest()
        ConsentManager.forgetActivityForTest()
        BiddingPartners.resetForTest()
        LicenseGate.resetForTest(LicenseGate.Verdict.Valid("test", 0L))   // SA1: test mạng mặc định có giấy phép
        AdPreloader.resetForTest()
        AdsCfOverrides.resetForTest()   // 2.5.0 RCSW: giá trị RC ads_cf_overrides là trạng thái process
    }
}
