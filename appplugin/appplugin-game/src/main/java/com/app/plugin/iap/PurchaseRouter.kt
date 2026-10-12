package com.app.plugin.iap

import android.app.Activity
import com.app.plugin.firbase.GameHost
import com.app.plugin.logevent.LogEventServer
import com.app.plugin.until.LogUtil
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.net.URLEncoder

/**
 * Router mua hàng đa kênh — doc 23 mục C.2, doc 18 mục 6.1.
 *
 * ═══ MỘT API DUY NHẤT, KÊNH DO CẤU HÌNH QUYẾT ĐỊNH ═══
 *
 * App gọi `PurchaseRouter.purchase(activity, productId)` và KHÔNG cần biết lệnh đó đi Play
 * Billing hay ra web shop: kênh chọn theo rule từ Remote Config (khoá
 * `purchase_routing_android`), ẩn hoàn toàn khỏi game logic. Đây là yêu cầu nguyên văn của
 * doc 23 mục C.2.
 *
 * ═══ HƯỚNG AN TOÀN — ROUTER KHÔNG BAO GIỜ TẠO ĐƯỜNG MUA KHÔNG HOÀN TẤT ĐƯỢC ═══
 *
 * WEB_SHOP chỉ được chọn khi TẤT CẢ các điều kiện sau đúng, sai bất kỳ điều kiện nào thì rơi
 * về PLAY_STORE (kênh luôn hoạt động, hành vi trước khi có router):
 *
 *   1. rule của sản phẩm nói `web_shop`;
 *   2. khối `web_shop` có `enabled = true`;
 *   3. `url_template` khác rỗng và **bắt buộc https** — dẫn người dùng mang thẻ tín dụng tới
 *      một URL http là lỗ hổng bảo mật, không phải cấu hình nhầm cần bao dung;
 *   4. quốc gia của thiết bị thuộc `eligible_countries` (danh sách rỗng = studio không giới
 *      hạn; quốc gia KHÔNG XÁC ĐỊNH được + danh sách khác rỗng → Play Store, vì thà mất một
 *      lượt routing còn hơn dẫn nhầm người dùng vào kênh không phục vụ nước họ).
 *
 * Cấu hình hỏng thì giữ nguyên cấu hình đang chạy — cùng nguyên tắc với `PaywallManager` và
 * `BrainPolicy`.
 *
 * ═══ ENTITLEMENT PHÍA WEB SHOP KHÔNG TỰ CẤP ═══
 *
 * Mua qua Play Billing được cấp quyền lợi từ kết quả store trả về trên máy (đủ an toàn cho
 * cờ tắt quảng cáo — xem giới hạn trong `Entitlements`). Mua qua web shop thì KHÁC HẲN: kết
 * quả thanh toán chỉ studio và vendor (Stripe/Xsolla/Appcharge...) biết, máy không thấy gì.
 * Vì vậy luồng đúng là: vendor webhook → server của studio xác thực → studio gọi
 * `grantFromServer()`. Tự cấp entitlement phía client từ dữ liệu chưa xác thực là gian lận
 * tầm thường (doc 18 mục 6.5: server-side entitlement là nguồn sự thật duy nhất).
 *
 * ═══ ROUTER KHÔNG HỎI BRAIN — CỐ Ý ═══
 *
 * `AdsBrain.shouldOfferIap()` chặn CHÀO MỜI (paywall, popup bán hàng) lúc bế tắc hay spam —
 * còn ở đây người dùng ĐÃ CHỦ ĐỘNG bấm mua. Chặn một lệnh mua do người dùng khởi xướng không
 * bảo vệ ai, chỉ phá doanh thu. Paywall hỏi brain vì paywall LÀ lời chào mời; router thực thi
 * một ý định đã có.
 *
 * ═══ RANH GIỚI UI ═══
 *
 * Cùng nguyên tắc "SDK không vẽ hộ" của paywall: với web shop, SDK dựng URL checkout rồi trả
 * về qua `IapResult.WebCheckoutOpened` — app tự chọn cách mở (Custom Tab, WebView, trình duyệt
 * ngoài) vì đó là quyết định UX của app. SDK không tự mở cửa sổ trình duyệt.
 */
public object PurchaseRouter {

    /** Một rule routing: sản phẩm này đi kênh nào. Rule đầu tiên khớp thắng, như khai báo. */
    public data class Rule(val productId: String, val channel: PurchaseChannel)

    public data class WebShopConfig(
        val enabled: Boolean,
        val urlTemplate: String,
        /** Rỗng = không giới hạn quốc gia. Khác rỗng = phải chứa quốc gia của thiết bị. */
        val eligibleCountries: Set<String>,
        /** false khi template không phải https — khi đó WEB_SHOP không bao giờ được chọn. */
        val urlOk: Boolean
    )

    public data class ApplyResult(val applied: Boolean, val report: String)

    private val WEB_SHOP_OFF = WebShopConfig(
        enabled = false, urlTemplate = "", eligibleCountries = emptySet(), urlOk = false
    )

    @Volatile private var rules: List<Rule> = emptyList()
    @Volatile private var webShop: WebShopConfig = WEB_SHOP_OFF
    @Volatile private var version: String = "chưa nạp"

    /**
     * Nguồn quốc gia / định danh người dùng. Mặc định đọc từ `LogEventServerHelper` — CÙNG
     * nguồn với cột `country`/`user_id` trong mọi event, để quyết định routing và dữ liệu đo
     * không bao giờ lệch nhau. Test thay được hai provider này.
     */
    internal var countryProvider: () -> String = { GameHost.country() }
    internal var userIdProvider: () -> String = { GameHost.userId() }

    public fun activeVersion(): String = version

    public fun clear() {
        rules = emptyList()
        webShop = WEB_SHOP_OFF
        version = "chưa nạp"
    }

    // ═══ Nạp cấu hình ═══

    /**
     * Parse + apply. Không bao giờ ném ra ngoài: cấu hình hỏng chỉ dẫn tới việc giữ nguyên
     * cấu hình đang chạy — cùng nguyên tắc với `PaywallManager`.
     *
     * Hình dạng:
     * ```json
     * {
     *   "version": "routing_v1",
     *   "web_shop": {
     *     "enabled": true,
     *     "url_template": "https://shop.example.com/checkout?product={product_id}&user={user_id}",
     *     "eligible_countries": ["US", "DE"]
     *   },
     *   "rules": [
     *     {"product_id": "coin_pack_1", "channel": "web_shop"},
     *     {"product_id": "vip_pass", "channel": "play_store"}
     *   ]
     * }
     * ```
     */
    @Suppress("UNCHECKED_CAST")
    public fun apply(json: String?): ApplyResult {
        if (json.isNullOrBlank()) return reject("cấu hình rỗng")
        return try {
            val root: Map<String, Any> = Gson().fromJson(
                json, object : TypeToken<Map<String, Any>>() {}.type
            ) ?: return reject("JSON không parse được thành object")

            val ver = root["version"]?.toString() ?: return reject("thiếu trường 'version'")
            val notes = mutableListOf<String>()

            // Khối web_shop là TUỲ CHỌN: không có nó thì mọi rule "web_shop" rơi về Play
            // Store — cấu hình thiếu không được phép tạo ra đường mua hỏng.
            var shop = WEB_SHOP_OFF
            (root["web_shop"] as? Map<String, Any>)?.let { block ->
                val template = block["url_template"]?.toString()?.trim() ?: ""
                val urlOk = template.startsWith("https://")
                if (template.isNotEmpty() && !urlOk) {
                    notes.add("url_template KHÔNG phải https → web_shop không bao giờ được chọn")
                }
                val countries = (block["eligible_countries"] as? List<Any>)
                    ?.mapNotNull { it?.toString()?.trim()?.uppercase()?.takeIf(String::isNotEmpty) }
                    ?.toSet()
                    ?: emptySet()
                shop = WebShopConfig(
                    enabled = block["enabled"]?.toString()?.toBooleanStrictOrNull() == true,
                    urlTemplate = template,
                    eligibleCountries = countries,
                    urlOk = urlOk
                )
            } ?: notes.add("không có khối 'web_shop' → mọi rule web_shop rơi về play_store")

            val parsedRules = mutableListOf<Rule>()
            (root["rules"] as? List<Any>)?.forEach { item ->
                val m = item as? Map<String, Any> ?: return@forEach
                val pid = m["product_id"]?.toString()?.trim() ?: return@forEach
                if (pid.isEmpty()) return@forEach
                val channel = when (m["channel"]?.toString()?.lowercase()) {
                    "web_shop" -> PurchaseChannel.WEB_SHOP
                    "play_store" -> PurchaseChannel.PLAY_STORE
                    else -> {
                        notes.add("rule '$pid': channel không hợp lệ → bỏ qua")
                        return@forEach
                    }
                }
                parsedRules.add(Rule(pid, channel))
            } ?: notes.add("không có 'rules' → mọi sản phẩm đi play_store")

            rules = parsedRules
            webShop = shop
            version = ver
            val report = buildString {
                append("đã nạp '$ver', ${parsedRules.size} rule")
                append(", web_shop=${if (webShopUsable()) "sẵn sàng" else "TẮT/chưa dùng được"}")
                if (notes.isNotEmpty()) append("\n  ⚠ ${notes.joinToString("\n  ⚠ ")}")
            }
            LogUtil.logD("PurchaseRouter: $report")
            ApplyResult(true, report)
        } catch (e: Exception) {
            reject("lỗi parse: ${e.message}")
        }
    }

    private fun reject(reason: String): ApplyResult {
        // Cố ý KHÔNG xoá cấu hình đang chạy: bản mới hỏng thì giữ bản cũ đang chạy tốt.
        val msg = "TỪ CHỐI cấu hình routing ($reason) — giữ nguyên '$version'"
        LogUtil.logE(msg)
        return ApplyResult(false, msg)
    }

    // ═══ Quyết định ═══

    /**
     * Kênh cho sản phẩm này. Thuần hàm số của cấu hình — không heuristic: heuristic (phí,
     * chuyển đổi, sở thích người dùng) là việc của brain khi có dữ liệu, router chỉ thực thi
     * rule studio đã khai báo.
     */
    public fun route(productId: String): PurchaseChannel {
        val rule = rules.firstOrNull { it.productId == productId } ?: return PurchaseChannel.PLAY_STORE
        if (rule.channel == PurchaseChannel.WEB_SHOP && webShopUsable()) {
            return PurchaseChannel.WEB_SHOP
        }
        return PurchaseChannel.PLAY_STORE
    }

    /**
     * API duy nhất cho game logic: mua sản phẩm, kênh do cấu hình chọn.
     *
     * - PLAY_STORE: chuyển thẳng cho `IapHelper.purchase`, hành vi nguyên vẹn như trước router.
     * - WEB_SHOP: dựng URL checkout, ghi event `web_checkout_opened`, trả
     *   `IapResult.WebCheckoutOpened(url)` qua callback. App mở URL theo cách của mình; quyền
     *   lợi chỉ cấp sau `grantFromServer()`.
     *
     * @return false chỉ khi không xử lý được gì (PLAY_STORE nhưng billing chưa sẵn sàng).
     */
    public fun purchase(
        activity: Activity,
        productId: String,
        callback: ((IapResult) -> Unit)? = null
    ): Boolean {
        return when (route(productId)) {
            PurchaseChannel.PLAY_STORE -> IapHelper.purchase(activity, productId, callback)
            PurchaseChannel.WEB_SHOP -> {
                val url = checkoutUrlFor(productId)
                if (url == null) {
                    // Về lý thuyết không xảy ra — route() chỉ chọn WEB_SHOP khi dựng được URL.
                    // Nhưng nếu xảy ra thì rơi về Play Store thay vì đưa app một kết quả chết.
                    return IapHelper.purchase(activity, productId, callback)
                }
                try {
                    LogEventServer.logWebCheckoutOpened(productId, version)
                } catch (e: Exception) {
                    LogUtil.logE("Web checkout log failed: ${e.message}")
                }
                callback?.invoke(IapResult.WebCheckoutOpened(productId, url))
                true
            }
        }
    }

    /**
     * URL checkout web shop cho sản phẩm, hoặc `null` khi web shop không dùng được.
     * Thay `{product_id}` và `{user_id}` trong template, giá trị được URL-encode — product id
     * hợp lệ của Play không chứa ký tự lạ, nhưng user id thì SDK không kiểm soát được.
     */
    public fun checkoutUrlFor(productId: String): String? {
        if (route(productId) != PurchaseChannel.WEB_SHOP) return null
        return webShop.urlTemplate
            .replace("{product_id}", encode(productId))
            .replace("{user_id}", encode(userIdProvider()))
    }

    /**
     * Cấp quyền lợi cho một giao dịch web shop ĐÃ ĐƯỢC SERVER CỦA STUDIO XÁC THỰC
     * (thường từ webhook của vendor thanh toán).
     *
     * CẢNH BÁO: gọi hàm này với dữ liệu chưa xác thực (ví dụ từ deep link do client tự khai)
     * là mở cửa gian lận — bất kỳ ai cũng có thể tự cấp vật phẩm. SDK KHÔNG xác thực giúp:
     * nó không có khóa bí mật của vendor, và mọi xác thực phía client đều giả lập được.
     *
     * Webhook gửi lại (retry) thì an toàn: bản ghi `iap_purchase` có dedup theo `order_id`
     * phía backend, và `Entitlements.grant` trùng lặp là no-op.
     *
     * @return false khi tham số bắt buộc rỗng — không cấp gì cả.
     */
    public fun grantFromServer(
        productId: String,
        orderId: String,
        priceMicros: Long,
        currencyCode: String,
        quantity: Int = 1,
        isSubscription: Boolean = false
    ): Boolean {
        if (productId.isBlank() || orderId.isBlank()) return false
        Entitlements.grant(productId)
        try {
            LogEventServer.logIapPurchase(
                productId = productId,
                orderId = orderId,
                priceMicros = priceMicros,
                currencyCode = currencyCode,
                quantity = quantity,
                channel = PurchaseChannel.WEB_SHOP.name,
                isSubscription = isSubscription
            )
        } catch (e: Exception) {
            LogUtil.logE("Web shop purchase log failed: ${e.message}")
        }
        return true
    }

    // ═══ Nội bộ ═══

    private fun webShopUsable(): Boolean =
        webShop.enabled && webShop.urlTemplate.isNotEmpty() && webShop.urlOk && countryAllowed()

    private fun countryAllowed(): Boolean {
        if (webShop.eligibleCountries.isEmpty()) return true
        val c = try {
            countryProvider().trim().uppercase()
        } catch (e: Exception) {
            ""
        }
        // Không biết quốc gia thì KHÔNG dẫn ra kênh giới hạn quốc gia — xem doc lớp.
        return c.isNotEmpty() && c != "DEFAULT" && c in webShop.eligibleCountries
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}
