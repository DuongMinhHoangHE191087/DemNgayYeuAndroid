package com.app.plugin.iap

import com.app.plugin.ads.AdsHelper
import com.app.plugin.ads.RemoveAdsMask
import com.app.plugin.until.LogUtil
import com.app.plugin.until.PreferenceHelper

/**
 * Quyền lợi người chơi đang sở hữu — và cây cầu nối chúng sang lớp quảng cáo.
 *
 * ═══ VÌ SAO LỚP NÀY TỒN TẠI ═══
 *
 * Doc 27 mục B.3 ghi lại một anti-pattern lấy từ teardown app đối thủ: **subscriber vẫn thấy
 * quảng cáo**. Nguyên nhân ở đó không phải ai cố tình, mà vì trạng thái mua hàng và cờ tắt
 * quảng cáo là hai thứ rời rạc, do hai đoạn code khác nhau quản, và chúng lệch nhau. Ở đây
 * chỉ có MỘT nguồn: mọi thay đổi quyền lợi đều đi qua `recompute()`, và `recompute()` là chỗ
 * duy nhất gọi `AdsHelper.setRemoveAdsMask()`.
 *
 * ═══ GIỚI HẠN PHẢI NÓI RÕ ═══
 *
 * Đây là trạng thái **phía client**, tin vào những gì Play Billing trả về trên máy. Nó KHÔNG
 * phải nguồn sự thật chống gian lận. Doc 23 mục C.2 yêu cầu server-side entitlement là nguồn
 * sự thật duy nhất — thứ đó chưa tồn tại (chưa có backend). Với `removeAds` thì rủi ro chấp
 * nhận được: hậu quả tệ nhất của việc bị qua mặt là một người xem ít quảng cáo hơn. **Không
 * được dùng lớp này để cấp phát vật phẩm có giá trị quy đổi** cho tới khi có xác thực
 * biên lai phía server.
 *
 * ═══ TOÀN VẸN (1.8.0 Task 6, D10) ═══ Lưu bản ghi có chữ ký HMAC khoá Keystore ([EntitlementIntegrity]); kiểm trên luồng
 * "appplugin-billing" lúc khởi tạo, áp mask khi xong. Ký + ghi đĩa ngoài monitor (#31); `active` copy-on-write.
 */
public object Entitlements {

    private const val KEY_ACTIVE = "iap_active_products"
    private const val KEY_REMOVE_ADS_IDS = "iap_remove_ads_products"
    private const val SEP = "|"
    private const val KEY_REMOVE_ADS_MASKS = "entitlements_remove_ads_masks"
    private const val KEY_SIGNED = "iap_entitlements_v1"

    /** Ngăn id với mask trong một mục đã lưu: `id:mask|id:mask`. */
    private const val MASK_SEP = ':'

    /** Đệm trong process, bất biến (thay cả tập khi ghi). Rỗng tới khi bản đã lưu kiểm xong. */
    @Volatile private var active: Set<String> = emptySet()
    @Volatile private var removeAdsProducts: Set<String> = loadSet(KEY_REMOVE_ADS_IDS)
    @Volatile private var removeAdsMasks: Map<String, Int> = loadMasks()

    /** [pending]: ghi trước khi kiểm xong, phát lại lên tập đã kiểm. Chưa [verified] thì không đẩy mask sang AdsHelper. */
    @Volatile private var verified = false
    @Volatile private var pending: ((Set<String>) -> Set<String>)? = null
    @Volatile private var masksSet = false

    init { BillingThread.offMain.execute { verifyStored() } }

    // ═══ Đọc ═══

    /** Có sản phẩm đang sở hữu gỡ ít nhất một định dạng. Gói rẻ (INTERRUPTIVE) cũng tính. */
    public fun hasRemoveAds(): Boolean = removeAdsMask() != RemoveAdsMask.NONE

    /**
     * OR mask của mọi sản phẩm đang sở hữu (Điều 13.4). Sản phẩm có mask riêng dùng đúng mask đó,
     * kể cả khi nó cũng nằm trong `removeAdsProducts`. Sản phẩm chỉ nằm trong `removeAdsProducts`
     * gỡ [RemoveAdsMask.ALL] như trước. Sản phẩm không nằm ở đâu góp [RemoveAdsMask.NONE].
     *
     * Không khoá, giống [hasRemoveAds] trước đây: ghi đi qua `@Synchronized`, đọc lấy tham chiếu
     * `@Volatile` một lần.
     */
    public fun removeAdsMask(): Int {
        val masks = removeAdsMasks
        val products = removeAdsProducts
        return active.fold(RemoveAdsMask.NONE) { acc, id ->
            acc or (masks[id] ?: if (id in products) RemoveAdsMask.ALL else RemoveAdsMask.NONE)
        }
    }

    public fun isActive(productId: String): Boolean = productId in active

    public fun activeProductIds(): Set<String> = active.toSet()

    // ═══ Cấu hình ═══

    /**
     * Khai báo sản phẩm nào tắt quảng cáo. Thường đến từ Remote Config để đổi được mà không
     * cần release app.
     *
     * Tính lại ngay sau khi đổi: người đang sở hữu `vip_pass` mà hôm nay `vip_pass` mới được
     * xếp vào nhóm tắt quảng cáo thì phải có hiệu lực lập tức, không đợi tới lần mua kế tiếp.
     */
    @Synchronized
    public fun setRemoveAdsProducts(productIds: Set<String>) {
        removeAdsProducts = productIds
        PreferenceHelper.getInstance().setValue(KEY_REMOVE_ADS_IDS, productIds.joinToString(SEP))
        recompute()
    }

    /**
     * Mask theo sản phẩm (Điều 13.4): gói rẻ gỡ [RemoveAdsMask.INTERRUPTIVE] (rewarded và native
     * inline vẫn còn), gói Pro gỡ [RemoveAdsMask.ALL]. Sản phẩm trong `removeAdsProducts` mà không
     * có ở đây mặc định ALL — hành vi cũ giữ nguyên. Thay toàn bộ map cũ và tính lại ngay.
     *
     * Id rỗng hoặc chứa `|`/`:` không ghi được theo định dạng `id:mask|…` nên bị bỏ, có log. Bit
     * ngoài [RemoveAdsMask.ALL] bị cắt, có log.
     */
    @Synchronized
    public fun setRemoveAdsMasks(masks: Map<String, Int>) {
        removeAdsMasks = validMasks(masks)
        masksSet = true
        PreferenceHelper.getInstance().setValue(
            KEY_REMOVE_ADS_MASKS,
            removeAdsMasks.entries.joinToString(SEP) { "${it.key}$MASK_SEP${it.value}" }
        )
        recompute()
        persist()
    }

    // ═══ Ghi ═══

    @Synchronized
    public fun grant(productId: String): Unit = write { it + productId }

    @Synchronized
    public fun revoke(productId: String): Unit = write { it - productId }

    /**
     * Đặt lại toàn bộ quyền lợi theo đúng những gì store vừa trả về.
     *
     * THAY THẾ chứ không cộng dồn — đây là điểm mấu chốt. `queryPurchasesAsync` trả về tập
     * đang sở hữu tại thời điểm này; một subscription đã huỷ đơn giản là **không còn trong
     * danh sách**. Nếu chỉ cộng thêm, sub đã huỷ sẽ có hiệu lực vĩnh viễn trên máy đó và
     * người dùng được tắt quảng cáo miễn phí mãi mãi.
     */
    @Synchronized
    public fun sync(ownedProductIds: Set<String>): Unit = write { ownedProductIds.toSet() }

    /** Xoá sạch — dùng cho nút reset khi test. */
    @Synchronized
    public fun clearAll() {
        active = emptySet(); pending = null
        removeAdsProducts = emptySet()
        removeAdsMasks = emptyMap()
        PreferenceHelper.getInstance().run { listOf(KEY_ACTIVE, KEY_SIGNED, KEY_REMOVE_ADS_IDS, KEY_REMOVE_ADS_MASKS).forEach { remove(it) } }
        recompute()
    }

    // ═══ Nội bộ ═══

    /** Trong monitor: copy-on-write + mask, rồi xếp ký + ghi đĩa ra luồng billing. Chưa kiểm xong: nhớ [change] để phát lại. */
    private fun write(change: (Set<String>) -> Set<String>) {
        val next = change(active)
        if (!verified) pending = pending?.let { p -> { s: Set<String> -> change(p(s)) } } ?: change
        if (next != active) { active = next; recompute(); if (verified) persist() }
    }

    /** Xếp lên luồng billing: ký trạng thái mới nhất rồi ghi — chạy sau khi người gọi nhả monitor, không Keystore trên main. */
    private fun persist() = BillingThread.offMain.execute {
        val (ids, masks) = synchronized(this) { active to removeAdsMasks }
        val record = EntitlementIntegrity.sign(ids, masks) ?: return@execute
        PreferenceHelper.getInstance().run { setValue(KEY_SIGNED, record); remove(KEY_ACTIVE) }
    }

    /** Luồng billing: kiểm rồi áp. Legacy → ký lại; Invalid → rỗng + khôi phục từ Play; NOTHING → không đụng AdsHelper. */
    private fun verifyStored() {
        val t0 = System.nanoTime()
        val stored = EntitlementIntegrity.check(PreferenceHelper.getInstance().get(KEY_SIGNED, ""), loadSet(KEY_ACTIVE))
        LogUtil.logE("ent verify ms=${(System.nanoTime() - t0) / 1_000_000} result=${stored.result}")
        val replayed = synchronized(this) {
            val p = pending
            active = p?.invoke(stored.ids) ?: stored.ids; pending = null; verified = true
            if (!masksSet) stored.masks?.let { removeAdsMasks = it }
            if (p != null || stored !== EntitlementIntegrity.NOTHING) recompute()
            p != null
        }
        if (replayed || stored !is StoredEntitlements.Verified) persist()
        if (stored is StoredEntitlements.Invalid) IapHelper.restorePurchases()
    }

    /**
     * Chỗ DUY NHẤT nối quyền lợi sang lớp quảng cáo. Mọi đường dẫn thay đổi quyền lợi đều
     * phải đi qua đây, nếu không hai trạng thái lại lệch nhau đúng như doc 27 mô tả.
     */
    private fun recompute() {
        if (!verified) return // mask đã lưu của AdsHelper giữ nguyên tới khi tập đã kiểm được áp
        try {
            AdsHelper.instance.setRemoveAdsMask(removeAdsMask())
        } catch (e: Exception) {
            // AdsHelper chưa init (hoặc đang chạy unit test) — không được để việc này làm
            // hỏng luồng mua hàng. Lần sync kế tiếp sẽ áp lại.
        }
    }

    private fun loadSet(key: String): Set<String> =
        PreferenceHelper.getInstance().get(key, "").split(SEP).filter { it.isNotBlank() }.toSet()

    private fun validMasks(masks: Map<String, Int>): Map<String, Int> {
        val out = LinkedHashMap<String, Int>()
        for ((id, mask) in masks) {
            if (id.isBlank() || SEP in id || MASK_SEP in id) {
                LogUtil.logE(
                    "Entitlements: bỏ mask của id '$id' — id rỗng hoặc chứa '$SEP'/'$MASK_SEP' không lưu được"
                )
                continue
            }
            val clean = RemoveAdsMask.sanitize(mask)
            if (clean != mask) {
                LogUtil.logE("Entitlements: mask $mask của '$id' có bit ngoài phạm vi, dùng $clean")
            }
            out[id] = clean
        }
        return out
    }

    /**
     * Đọc `id:mask|id:mask` đã lưu. Mục hỏng (không phải số, thiếu id, ngoài 0..ALL) được log và
     * bỏ giá trị (Điều 7). Đọc được id thì id đó nhận [RemoveAdsMask.NONE] chứ không rơi về ALL
     * của `removeAdsProducts`: không biết mask đã mua là gì thì không được nới rộng (Điều 13.4).
     * `-1` là hỏng chứ không phải ALL, dù `sanitize(-1) = ALL`. Lần `setRemoveAdsMasks` kế tiếp
     * (bước 1 của `MonetizationSdk.configure`) ghi đè.
     */
    private fun loadMasks(): Map<String, Int> {
        val out = LinkedHashMap<String, Int>()
        for (entry in PreferenceHelper.getInstance().get(KEY_REMOVE_ADS_MASKS, "").split(SEP)) {
            if (entry.isEmpty()) continue
            val parts = entry.split(MASK_SEP)
            val id = parts[0]
            val mask = parts.getOrNull(1)?.toIntOrNull()
                ?.takeIf { parts.size == 2 && it == RemoveAdsMask.sanitize(it) }
            if (id.isNotBlank() && mask != null) {
                out[id] = mask
                continue
            }
            val fallback = if (id.isNotBlank()) ", '$id' không gỡ gì tới lần setRemoveAdsMasks kế tiếp" else ""
            LogUtil.logE("Entitlements: mục mask gỡ quảng cáo đã lưu bị hỏng '$entry' — bỏ qua$fallback")
            if (id.isNotBlank()) out[id] = RemoveAdsMask.NONE
        }
        return out
    }

    // ═══ Chỉ dùng cho test ═══

    /** Đọc lại cả ba trạng thái từ `PreferenceHelper` và kiểm chữ ký, như lúc object khởi tạo trong process mới. */
    @JvmSynthetic
    @Synchronized
    internal fun reloadForTest() {
        active = emptySet(); verified = false; pending = null; masksSet = false
        removeAdsProducts = loadSet(KEY_REMOVE_ADS_IDS)
        removeAdsMasks = loadMasks()
        verifyStored()
    }
}
