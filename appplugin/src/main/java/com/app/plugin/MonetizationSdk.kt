package com.app.plugin

import android.content.Context
import com.app.plugin.ads.AdSdkStartup
import com.app.plugin.brain.AdsBrain
import com.app.plugin.brain.BrainPolicy
import com.app.plugin.brain.DecisionLogger
import com.app.plugin.brain.HoldoutManager
import com.app.plugin.brain.PlayerIdSource
import com.app.plugin.consent.ConsentRules
import com.app.plugin.consent.ConsentState
import com.app.plugin.consent.ConsentTier
import com.app.plugin.iap.Entitlements
import com.app.plugin.iap.IapHelper
import com.app.plugin.until.LocalDataReset
import com.app.plugin.until.LogUtil

/**
 * Điểm vào duy nhất cho studio tích hợp.
 *
 * ═══ VẤN ĐỀ NÓ GIẢI QUYẾT ═══
 *
 * Trước lớp này, bật đầy đủ SDK cần gọi bốn thứ nằm ở bốn package khác nhau, **theo đúng một
 * thứ tự** mà không có gì gợi ý:
 *
 * - `Entitlements.setRemoveAdsProducts()` phải chạy **trước** `IapHelper.init()`, vì `init()`
 *   tự khôi phục giao dịch ngay khi kết nối. Sai thứ tự thì lượt khôi phục đầu tiên tính ra
 *   `hasRemoveAds() = false` cho một subscriber thật, và họ thấy quảng cáo tới lần đồng bộ sau.
 * - `AdsBrain.rolloutFraction` phải đặt **trước** `AdsBrain.enabled`. Ngược lại sẽ có một
 *   khoảng brain chạy ở 100% dân số — trên app thật, khoảng đó đủ phục vụ hàng nghìn quyết định.
 *
 * Cả hai lỗi đều **không báo gì cả**, chỉ làm hành vi lệch đi. Thứ tự như vậy thuộc về code,
 * không thuộc về tài liệu.
 *
 * ═══ CÁCH DÙNG ═══
 *
 * ```kotlin
 * class MyApp : AppPluginBase() {          // vòng đời, session, decision log: có sẵn
 *     override fun onCreate() {
 *         super.onCreate()
 *         MonetizationSdk.configure(this) {
 *             brainEnabled = true
 *             brainRolloutFraction = 0.05          // mở dần, đừng bật thẳng 100%
 *
 *             inappProducts = listOf("coin_pack")
 *             subsProducts = listOf("premium_monthly")
 *             consumableProducts = setOf("coin_pack")
 *             removeAdsProducts = setOf("premium_monthly")
 *         }
 *     }
 * }
 * ```
 *
 * `configure()` trả về một báo cáo tự kiểm tra — in nó ra khi debug. Nó bắt được những cấu
 * hình "im lặng không làm gì" (brain bật mà rollout bằng 0), id gõ nhầm, và những chỗ bỏ sót
 * thường gặp.
 */
public object MonetizationSdk {

    /** Phiên bản của lớp tích hợp — xuất hiện trong báo cáo để đối chiếu khi hỗ trợ studio. */
    public const val VERSION: String = BuildConfig.SDK_VERSION

    public class Config {
        /** Bật Monetization Brain. Mặc định TẮT: hành vi y hệt khi chưa có brain. */
        public var brainEnabled: Boolean = false

        /**
         * Tỉ lệ dân số brain điều khiển. Mặc định 1.0 — nhưng quy trình đúng là mở dần từ
         * 0.05 (doc 38 mục F). Điều chỉnh được từ xa qua policy artifact mà không cần release.
         */
        public var brainRolloutFraction: Double = 1.0

        /** Id người chơi của app. Bỏ trống thì SDK dùng id ổn định phạm vi thiết bị của nó. */
        public var playerId: String? = null

        public var inappProducts: List<String> = emptyList()
        public var subsProducts: List<String> = emptyList()

        /**
         * Sản phẩm mua lại được. **App phải tự khai** — SDK không đoán, vì đoán sai theo hướng
         * consume sẽ xoá vĩnh viễn quyền lợi người dùng đã trả tiền.
         */
        public var consumableProducts: Set<String> = emptySet()

        /** Sản phẩm tắt quảng cáo. Bỏ sót ở đây = subscriber vẫn thấy quảng cáo dù đã trả tiền. */
        public var removeAdsProducts: Set<String> = emptySet()

        /** Mask gỡ quảng cáo theo sản phẩm (RemoveAdsMask.*). Sản phẩm trong removeAdsProducts
         *  không có ở đây gỡ ALL. */
        public var removeAdsMasks: Map<String, Int> = emptyMap()

        /**
         * App có hướng tới trẻ em không (COPPA). `null` = chưa khai báo, SDK không đụng vào
         * cấu hình tương ứng của mạng quảng cáo.
         *
         * Đây là TUYÊN BỐ PHÁP LÝ của studio. SDK không có thẩm quyền đặt mặc định thay bạn,
         * kể cả một mặc định trông có vẻ vô hại.
         */
        public var childDirected: Boolean? = null

        /** Người dùng dưới tuổi đồng ý (GDPR). `null` = chưa khai báo — xem `childDirected`. */
        public var underAgeOfConsent: Boolean? = null

        /**
         * Xác nhận studio đã tự lo consent bằng cách khác (CMP riêng, hoặc app không phục vụ
         * EEA/UK). Đặt `true` để tắt cảnh báo consent trong báo cáo tự kiểm tra.
         *
         * Cờ này KHÔNG thay đổi hành vi gì — nó chỉ tắt một dòng cảnh báo. Cố ý như vậy: một
         * cờ vừa tắt cảnh báo vừa đổi hành vi sẽ khiến người ta bật nó để cho yên chuyện.
         */
        public var consentHandledExternally: Boolean = false
    }

    public data class Report(
        val version: String,
        val brainEnabled: Boolean,
        val brainRolloutFraction: Double,
        val holdoutFraction: Double,
        val policyVersion: String,
        val consentTier: ConsentTier,
        val productCount: Int,
        val billingStarted: Boolean,
        /** Số decision log chờ gửi; `-1` khi hàng đợi còn đang được đếm trên luồng nền. */
        val queuedDecisionLogs: Int,
        val warnings: List<String>,
        /** Thứ tự các bước đã áp — để test khoá lại thứ tự, và để chẩn đoán khi hỗ trợ. */
        val appliedOrder: List<String>
    ) {
        public fun describe(): String = buildString {
            appendLine("╔══ MONETIZATION SDK $version ══")
            appendLine("║ brain      : " + if (brainEnabled) "BẬT" else "TẮT")
            if (brainEnabled) {
                appendLine("║ rollout    : ${"%.0f".format(brainRolloutFraction * 100)}% dân số")
                appendLine("║ holdout    : ${"%.0f".format(holdoutFraction * 100)}% (nhóm đối chứng)")
            }
            appendLine("║ policy     : $policyVersion")
            appendLine("║ consent    : $consentTier" + when (consentTier) {
                ConsentTier.UNKNOWN -> "  — chưa có câu trả lời dùng được (chưa chạy luồng hoặc thiếu IABTCF)"
                ConsentTier.DENIED -> "  → KHÔNG cá nhân hoá, guardrail vẫn chạy"
                ConsentTier.PARTIAL -> "  → dùng hồ sơ cũ, không tích luỹ thêm"
                ConsentTier.GRANTED -> ""
            })
            appendLine("║ sản phẩm   : $productCount" + if (billingStarted) " (billing đã khởi động)" else "")
            appendLine(
                "║ log chờ gửi: " +
                    if (queuedDecisionLogs < 0) "đang đếm" else "$queuedDecisionLogs bản ghi"
            )
            // Nói thẳng, không giấu: studio phải biết là chưa ai học từ log của họ.
            appendLine("║ backend    : hàng đợi decision log tích tại máy và gửi tới endpoint")
            appendLine("║              hiện tại. CHƯA có training service — brain học cục bộ")
            appendLine("║              trên từng thiết bị, chưa tổng hợp toàn cục.")
            if (warnings.isEmpty()) {
                appendLine("║ ✓ không có cảnh báo cấu hình")
            } else {
                appendLine("╠══ CẢNH BÁO ══")
                warnings.forEach { appendLine("║ ⚠ $it") }
            }
            append("╚══════════════════════")
        }
    }

    @Volatile
    private var lastReport: Report? = null

    /** Báo cáo của lần `configure()` gần nhất; null nếu chưa gọi. */
    public fun lastReport(): Report? = lastReport

    /**
     * Cấu hình SDK. Gọi trong `Application.onCreate()`, **sau** `super.onCreate()` của
     * `AppPluginBase` — lớp đó khởi tạo lưu trữ, pipeline log và vòng đời phiên.
     */
    @JvmStatic
    public fun configure(context: Context, block: Config.() -> Unit): Report {
        val config = Config().apply(block)
        return apply(config, context = context, startBilling = true)
    }

    /**
     * Xoá dữ liệu cục bộ SDK đã ghi (nút "Xoá dữ liệu" của app, Play data safety). Trả về ngay; trên luồng nền xoá mọi khoá
     * của `App_prefer_file` và `XgameHCM-BaseAndroid` **trừ** quyền đã mua, cờ do consent, bản lưu cấu hình từ xa/giấy
     * phép/kill/policy và cờ một-lần của Install Referrer; làm rỗng hàng đợi event bền; bỏ cache trong RAM (sổ mua, player
     * id); nạp lại segment. Không chạm consent IAB/GPP (đổi qua `showPrivacyOptions`) hay dữ liệu Firebase/AppsFlyer/
     * AppLovin (app tự gọi `resetAnalyticsData`).
     *
     * Sau khi gọi, máy được coi như cài mới với SDK: player id mới (`user_id` telemetry, lần mở sau gửi lại `first_open`
     * vì `TIME_OPEN` bị xoá); số phiên về 0 nên luật phiên đầu của `ads_policy_android` áp lại; mốc guardrail toàn màn
     * hình và cửa sổ ẩn banner reset; trạng thái subscription (`sub_lifecycle_*`, `sub_server_*`) dựng lại từ Play/server;
     * sổ `iap_purchase` bị xoá nên lần khôi phục mua phát lại `iap_purchase` (server khử trùng theo purchase token).
     *
     * Còn sót tới khi khởi động lại process: thống kê bandit và belief của brain đang trong RAM (gồm belief dưới player id
     * do app cấp) và cửa sổ eCPM của segment (`seg_ecpm_window`, `EcpmBucketResolver.kt:28,63`) có thể được ghi lại ở lần
     * cập nhật kế tiếp; app muốn xoá sạch nên khởi động lại process sau khi gọi.
     */
    @JvmStatic
    public fun clearLocalData(context: Context): Unit = LocalDataReset.clear(context)

    // ═══ Nội bộ ═══

    /**
     * Áp cấu hình theo đúng thứ tự an toàn. Tách khỏi `configure()` để test khoá được thứ tự
     * mà không cần `Context` thật.
     */
    private fun apply(config: Config, context: Context?, startBilling: Boolean): Report {
        val order = mutableListOf<String>()

        // 1. Quyền lợi TRƯỚC billing — xem KDoc của object.
        if (config.removeAdsProducts.isNotEmpty()) {
            Entitlements.setRemoveAdsProducts(config.removeAdsProducts)
            order += "entitlements.removeAdsProducts"
        }
        if (config.removeAdsMasks.isNotEmpty()) {
            Entitlements.setRemoveAdsMasks(config.removeAdsMasks)
            order += "entitlements.removeAdsMasks"
        }

        // 2. Khai báo sản phẩm.
        val hasProducts = config.inappProducts.isNotEmpty() || config.subsProducts.isNotEmpty()
        if (hasProducts) {
            IapHelper.setProducts(
                inapp = config.inappProducts,
                subs = config.subsProducts,
                consumable = config.consumableProducts
            )
            order += "iap.setProducts"
        }

        // 3. Kết nối billing — chỉ khi app thật sự bán gì đó.
        var billingStarted = false
        var billingKilled = false
        if (hasProducts && startBilling) {
            if (context != null) {
                try {
                    billingStarted = IapHelper.init(context)
                    billingKilled = !billingStarted && SdkKillSwitch.current.killsIap(BuildConfig.SDK_VERSION)
                } catch (e: Exception) {
                    LogUtil.logE("MonetizationSdk: khoi dong billing that bai: ${e.message}")
                }
            }
            order += "iap.init"
        }

        // 3b. Tuyên bố pháp lý của studio về đối tượng người dùng. Đặt TRƯỚC khi mạng quảng
        // cáo được cấu hình, vì `AdsMobMy.initAd()` đọc hai giá trị này lúc khởi tạo.
        config.childDirected?.let { ConsentState.childDirected = it; order += "consent.childDirected" }
        config.underAgeOfConsent?.let { ConsentState.underAgeOfConsent = it; order += "consent.underAgeOfConsent" }

        // 4. Rollout TRƯỚC enabled — xem KDoc của object.
        AdsBrain.rolloutFraction = config.brainRolloutFraction.coerceIn(0.0, 1.0)
        order += "brain.rolloutFraction"
        AdsBrain.enabled = config.brainEnabled
        order += "brain.enabled"

        // 5. Player id của app (nếu có) — tốt hơn id của SDK vì nó sống qua cả lần cài lại app.
        // Ghi vào PlayerIdSource TRƯỚC khi báo brain: `user_id` telemetry và URL web shop đọc id từ đó (#13).
        // Id chứa U+001F/xuống dòng làm hỏng hàng đợi decision log (review N2): bỏ qua, có log.
        config.playerId?.takeIf { it.isNotBlank() && !PlayerIdSource.isUsable(it) }?.let {
            LogUtil.logE("MonetizationSdk.configure: playerId chứa U+001F/xuống dòng — bỏ qua, giữ id đang có hiệu lực")
        }
        config.playerId?.takeIf { PlayerIdSource.isUsable(it) }?.let {
            PlayerIdSource.setFromApp(it)   // bộ nhớ + prefs: lần mở sau session_start mang id này (review cuối I2)
            order += "playerId.override"
            AdsBrain.onSessionStart(it)
            order += "brain.onSessionStart"
        }

        val report = Report(
            version = VERSION,
            brainEnabled = AdsBrain.enabled,
            brainRolloutFraction = BrainPolicy.rolloutOverride() ?: AdsBrain.rolloutFraction,   // hiệu lực: artifact thắng rollout app đặt
            holdoutFraction = HoldoutManager.holdoutFraction,
            policyVersion = BrainPolicy.activeVersion(),
            consentTier = ConsentState.tier(),
            productCount = config.inappProducts.size + config.subsProducts.size,
            billingStarted = billingStarted,
            queuedDecisionLogs = DecisionLogger.queuedCount(),
            warnings = checkConfig(config) + listOfNotNull(
                ("billing bị kill switch `sdk_kill_android` (iap): không kết nối Play Billing, mua hàng trả " +
                    "lỗi, quyền lợi đã lưu giữ nguyên; bật lại khi gỡ khoá và mở lại app").takeIf { billingKilled },
                gppOptOutWarning()
            ),
            appliedOrder = order
        )
        lastReport = report
        return report
    }

    /**
     * Người dùng Mỹ đã opt-out bán/chia sẻ dữ liệu (GPP) và SDK đang áp: số liệu attribution/remarketing của người này
     * sẽ thiếu — nói ra để studio không tưởng là lỗi tích hợp. Đọc GPP đã áp lần gần nhất ([AdSdkStartup.appliedGpp],
     * bộ nhớ), không đọc prefs: `configure` chạy trong `Application.onCreate`. Chưa áp lần nào (cổng consent chưa mở)
     * thì không có dòng này.
     */
    private fun gppOptOutWarning(): String? {
        val gpp = AdSdkStartup.appliedGpp ?: return null
        if (!ConsentRules.usOptOut(gpp)) return null
        return "GPP: US opt-out đang áp (sections=${gpp.sectionIds}, sale=${gpp.saleOptOut}, " +
            "sharing=${gpp.sharingOptOut}, targeted=${gpp.targetedAdsOptOut}) — Firebase Consent Mode " +
            "ad_user_data/ad_personalization denied, AppsFlyer không chia sẻ với partner, MAX doNotSell, Meta LDU. " +
            "Tầng consent không đổi, quảng cáo vẫn hiện. Đây là lựa chọn của người dùng, không phải lỗi cấu hình."
    }

    /**
     * Bắt những cấu hình sai mà hệ thống KHÔNG tự báo lỗi — loại nguy hiểm nhất, vì mọi thứ
     * trông như đang chạy.
     */
    private fun checkConfig(c: Config): List<String> {
        val w = mutableListOf<String>()

        if (c.brainEnabled && c.brainRolloutFraction <= 0.0) {
            w += "brainEnabled = true nhưng brainRolloutFraction = 0 → brain không điều khiển " +
                "player nào. Đây là một no-op im lặng, không có dấu hiệu gì để chẩn đoán."
        }

        val declared = (c.inappProducts + c.subsProducts).toSet()

        for (id in c.consumableProducts - declared) {
            w += "consumableProducts chứa '$id' không có trong inappProducts/subsProducts — " +
                "nhiều khả năng gõ nhầm id. Sản phẩm sẽ không bao giờ được consume và " +
                "người dùng không mua lại được."
        }

        for (id in c.removeAdsProducts - declared) {
            w += "removeAdsProducts chứa '$id' không có trong danh sách sản phẩm đang bán — " +
                "kiểm tra lại id."
        }

        for (id in c.removeAdsMasks.keys - declared) {
            w += "removeAdsMasks chứa '$id' không có trong danh sách sản phẩm — kiểm tra lại id."
        }

        if (c.subsProducts.isNotEmpty() && c.removeAdsProducts.isEmpty() && c.removeAdsMasks.isEmpty()) {
            w += "có subscription nhưng không khai báo removeAdsProducts/removeAdsMasks — subscriber sẽ vẫn " +
                "thấy quảng cáo. Nếu đó là chủ đích thì bỏ qua cảnh báo này."
        }

        if (!c.consentHandledExternally && ConsentState.tier() == ConsentTier.UNKNOWN) {
            w += "chưa có câu trả lời dùng được (chưa chạy luồng hoặc thiếu IABTCF). Google " +
                "yêu cầu CMP được chứng nhận khi phục vụ quảng cáo cho người dùng EEA/UK — " +
                "gọi ConsentManager.request(activity) từ " +
                "Activity đầu tiên. Từ 1.3.0 mạng quảng cáo chỉ khởi tạo khi UMP cho phép, nên " +
                "không có luồng này thì lần mở đầu tiên không có quảng cáo nào. Nếu app không " +
                "phục vụ EEA/UK hoặc đã có CMP riêng, đặt consentHandledExternally = true để " +
                "tắt cảnh báo này."
        }

        if (c.childDirected == null) {
            w += "chưa khai báo childDirected. SDK KHÔNG tự đặt cờ COPPA thay bạn — đây là " +
                "tuyên bố pháp lý, không phải mặc định kỹ thuật. Khai báo true/false tuỳ app."
        }

        if (c.inappProducts.isNotEmpty() && c.consumableProducts.isEmpty()) {
            w += "có sản phẩm in-app nhưng không khai báo consumableProducts — nếu có gói tiêu " +
                "hao (coin, gem) thì người dùng chỉ mua được đúng một lần."
        }

        return w
    }

    // ═══ Chỉ dùng cho test ═══
    //
    // `internal` một mình là chưa đủ: Kotlin biên dịch nó thành phương thức public có tên
    // bị mangle (`applyForTest$appplugin_release`), nên từ Java vẫn gọi được. `@JvmSynthetic`
    // xoá nó khỏi API mà Java nhìn thấy. Với một thư viện thì đây không phải chuyện nhỏ —
    // mọi thứ studio gọi được đều trở thành thứ ta phải giữ tương thích.

    @JvmSynthetic
    internal fun applyForTest(config: Config, startBilling: Boolean = false): Report =
        apply(config, context = null, startBilling = startBilling)

    @JvmSynthetic
    internal fun resetForTest() {
        lastReport = null
        PlayerIdSource.resetForTest()   // id app của `configure` trước không rò sang test sau
    }
}
