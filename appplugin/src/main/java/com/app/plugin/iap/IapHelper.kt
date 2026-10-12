package com.app.plugin.iap

import android.app.Activity
import android.content.Context
import com.app.plugin.BuildConfig
import com.app.plugin.SdkKillSwitch
import com.app.plugin.logevent.IapEventLog
import com.app.plugin.until.LogUtil
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor

/**
 * Lớp bọc Play Billing — đối xứng với `AdsHelper` ở lớp IAA.
 *
 * ═══ VỊ TRÍ TRONG SẢN PHẨM ═══
 *
 * Đây là mảnh mở khoá phần lớn roadmap doc 23: trước nó, `ActionMode.IAP_OFFER` là một enum
 * rỗng vì SDK không có cách nào biết ai đã mua gì. Có nó rồi thì ba việc chạy được:
 *
 * 1. **Tín hiệu payer vào brain** — `AdsBrain.onPurchase()` được gọi tự động, belief dịch
 *    sang nhóm payer, và guardrail giảm áp lực quảng cáo cho người vừa trả tiền.
 * 2. **Doanh thu theo kênh** (doc 26 mục C.2) — event `iap_purchase` có `price_micros`,
 *    `currency_code`, `channel`; ARPDAU tính được cả hai vế IAP + IAA.
 * 3. **Quyền lợi nối sang lớp quảng cáo** — `Entitlements` tắt quảng cáo cho subscriber.
 *
 * ═══ NHỮNG GÌ LỚP NÀY CỐ TÌNH KHÔNG LÀM ═══
 *
 * - **Không xác thực biên lai phía server.** Chưa có backend (doc 25 mục E). Vì vậy quyền lợi
 *   ở đây chỉ nên dùng cho `removeAds`, không dùng để cấp vật phẩm có giá trị quy đổi.
 * - **Không tự `consume` sản phẩm.** SDK không biết sản phẩm nào là consumable; đoán sai theo
 *   hướng consume sẽ xoá mất quyền lợi vĩnh viễn của người dùng. Mặc định là acknowledge
 *   (giữ quyền lợi); app tự khai báo consumable qua `setConsumableProducts()`.
 * - **Không dựng paywall.** Đó là Phase 1-2 của doc 23, một lớp riêng.
 *
 * Lời gọi store đi qua [BillingPort] (1.8.0 Task 4): lớp này chỉ còn state machine, không import Play Billing.
 * Luồng (1.8.0 Task 5, D11/F7): mọi lệnh port và mọi xử lý kết quả chạy trên "appplugin-billing"; callback của port chỉ
 * giao việc; callback cho app (`onDone`, `callback`, [purchaseListener]) về main như trước.
 */
public object IapHelper {
    /** D22: mã của `purchase()` thứ hai khi luồng mua trước còn mở (mã âm của SDK như -1). */
    private const val FLOW_IN_PROGRESS = -2

    /** Ghi trên luồng "appplugin-billing", đọc trên main (`isReady`, `purchase`, `restorePurchases`). */
    @Volatile private var port: BillingPort? = null
    private val connectScheduled = java.util.concurrent.atomic.AtomicBoolean(false)
    /** Luồng nền đơn "appplugin-billing" (daemon, [BillingThread]); test thay bằng executor tự điều khiển. */
    @Volatile private var billingExecutor: Executor = BillingThread.executor
    private var appContext: Context? = null

    /** Thông tin giá đã lấy được, khoá theo productId (đọc main, ghi luồng billing). [offerCache]: offer để tính pha thu. */
    private val productCache: MutableMap<String, IapProduct> = ConcurrentHashMap()
    private val offerCache: MutableMap<String, PortOffer> = ConcurrentHashMap()

    @Volatile private var inappIds: List<String> = emptyList()
    @Volatile private var subsIds: List<String> = emptyList()
    @Volatile private var consumableIds: Set<String> = emptySet()

    /** Bước ghi sổ (2.0.0 B1, [PurchaseRecorder]): đọc giá/khai báo của object này lúc gọi. */
    private val recorder = PurchaseRecorder(
        productOf = { productCache[it] }, offerOf = { offerCache[it] }, isDeclared = { it in inappIds || it in subsIds },
        consumableIds = { consumableIds }, billing = ::billing
    )

    /** Nhận mọi giao dịch, kể cả giao dịch đến ngoài luồng mua (khôi phục, mua ở máy khác). */
    @Volatile public var purchaseListener: ((IapResult) -> Unit)? = null

    /** Gọi store được chưa: đã `startConnection`, không đang kết nối, không `BILLING_UNAVAILABLE` (`BillingPort.usable`). */
    public fun isReady(): Boolean = port?.usable() == true

    /** Test + harness G12 (D8) thay port; `null` = về trạng thái chưa có port (dọn dẹp của test). */
    internal fun replacePort(port: BillingPort?) {
        this.port = port
    }

    public fun productFor(productId: String): IapProduct? = productCache[productId]

    public fun products(): List<IapProduct> = productCache.values.toList()

    private fun billing(task: () -> Unit) {
        try {
            billingExecutor.execute {
                try { task() } catch (e: Exception) { LogUtil.logE("IapHelper billing task failed: ${e.message}") }
            }
        } catch (e: Exception) {
            LogUtil.logE("IapHelper billing task not scheduled: ${e.message}")
        }
    }

    private fun onMain(task: () -> Unit) = BillingThread.postMain { task() }

    // ═══ Khởi tạo ═══

    /**
     * Khai báo sản phẩm. Nên gọi TRƯỚC `init()` để lượt query giá đầu tiên có dữ liệu; gọi
     * sau cũng được, chỉ cần gọi lại `queryProducts()`.
     *
     * `consumable` là danh sách app phải tự khai — xem lý do ở KDoc của object.
     */
    public fun setProducts(
        inapp: List<String> = emptyList(),
        subs: List<String> = emptyList(),
        consumable: Set<String> = emptySet()
    ) {
        inappIds = inapp
        subsIds = subs
        consumableIds = consumable
    }

    /**
     * Kết nối tới Play Billing (`enableAutoServiceReconnection()`, không vòng retry tự viết).
     *
     * Kill switch `iap` (`sdk_kill_android`) thì không kết nối: mua trả lỗi "chưa sẵn sàng",
     * `restorePurchases` trả quyền lợi đã lưu. Chỉ tắt, không thu hồi gì (Điều 13).
     *
     * Dựng `BillingClient` và `startConnection` chạy trên luồng nền "appplugin-billing", không trên luồng gọi:
     * cả hai đi qua binder (`PackageManager`, `bindService`) và từng chặn main lúc cold start (#11 Samsung 1.6.3).
     *
     * @return `true` khi đã lên lịch (hoặc đang/đã kết nối) — kể cả khi kill switch `iap` tới sau lúc đó;
     *   `false` khi kill switch `iap` bật trước khi lên lịch, hoặc executor từ chối việc (cả hai đã log).
     *   Lỗi dựng `BillingClient` xảy ra trên luồng nền: chỉ còn ở log (`IapHelper init failed`).
     */
    public fun init(context: Context): Boolean {
        // Đã lên lịch/kết nối trước: kill tới giữa phiên không ngắt billing đang chạy (có hiệu lực từ lần mở kế),
        // nên lần init/configure sau không được báo "bị kill" khi billing vẫn kết nối.
        if (port != null || connectScheduled.get()) {
            if (SdkKillSwitch.current.killsIap(BuildConfig.SDK_VERSION)) {
                LogUtil.logD("kill switch: iap bật khi billing đã kết nối; áp từ lần mở kế")
            }
            return true
        }
        if (SdkKillSwitch.current.killsIap(BuildConfig.SDK_VERSION)) {
            LogUtil.logE("kill switch: iap — không kết nối billing")
            return false
        }
        val app = context.applicationContext
        appContext = app
        if (!connectScheduled.compareAndSet(false, true)) return true
        return try {
            billingExecutor.execute { connectPort(app) }
            true
        } catch (e: Exception) {
            connectScheduled.set(false)
            LogUtil.logE("IapHelper init failed: ${e.message}")
            false
        }
    }

    /** Chạy trên luồng "appplugin-billing": port dựng `BillingClient` rồi `startConnection` (lỗi dựng: port log). */
    private fun connectPort(app: Context) {
        val p = BillingPorts.select(app)
        port = p
        p.connect { setup -> billing { onSetup(setup) } }
    }

    /** Luồng billing. FAILED/UNAVAILABLE: port đã log (UNAVAILABLE: một dòng, port thôi gọi store trong process). */
    private fun onSetup(setup: PortSetup) {
        when (setup) {
            PortSetup.CONNECTED -> {
                LogUtil.logD("IapHelper connected")
                // Đồng bộ ngay khi kết nối (mua ở máy khác, huỷ ngoài app, cài lại app) — SAU khi có giá (I7): giao dịch
                // khôi phục được báo với giá và pha thu đúng.
                queryAll { restoreNow(null) }
            }
            PortSetup.STILL_CONNECTING ->
                LogUtil.logD("IapHelper reconnect overlapped the first connect, waiting")
            is PortSetup.UNAVAILABLE, is PortSetup.FAILED -> Unit
        }
    }

    // ═══ Giá sản phẩm ═══

    public fun queryProducts(onDone: ((List<IapProduct>) -> Unit)? = null) {
        billing { queryAll { onDone?.let { cb -> val list = products(); onMain { cb(list) } } } }
    }

    private fun queryAll(then: () -> Unit) =
        queryFor(IapProductType.INAPP, inappIds, 0) { queryFor(IapProductType.SUBS, subsIds, 0, then) }

    /**
     * Luồng billing. Store lỗi tạm thời (`BillingTimer.RETRYABLE`) → thử lại sau 1-2-4-8-16 s rồi dừng; [onDone] chỉ chờ
     * lượt đầu ([attempt] 0). Port trả danh sách rỗng khi lỗi.
     */
    private fun queryFor(type: IapProductType, ids: List<String>, attempt: Int, onDone: (() -> Unit)?) {
        val port = port
        if (port == null || ids.isEmpty()) { onDone?.invoke(); return }
        port.queryProducts(type, ids) { code, offers, _ ->
            billing {
                for (offer in offers) {
                    offerCache[offer.productId] = offer
                    productCache[offer.productId] = productOf(offer)
                }
                if (code in BillingTimer.RETRYABLE) {
                    val delay = BillingTimer.retryDelayMs(attempt + 1)
                    if (delay == null) LogUtil.logE("IapHelper queryProducts($type): hết lượt thử lại ($code)")
                    else BillingThread.timer.schedule(delay, Runnable { billing { queryFor(type, ids, attempt + 1, null) } })
                }
                onDone?.invoke()
            }
        }
    }

    /** SUBS: `offerToken` = offer đầu (như `toIapProduct` cũ); INAPP không có offer → "". */
    private fun productOf(offer: PortOffer): IapProduct = IapProduct(
        productId = offer.productId,
        type = offer.type,
        formattedPrice = offer.formatted,
        priceMicros = offer.basePriceMicros,
        currencyCode = offer.currency,
        title = offer.title,
        description = offer.description,
        offerToken = offer.offers.firstOrNull()?.token ?: ""
    )

    // ═══ Mua ═══

    /**
     * Mở luồng mua của Play. Kết quả về qua `callback` **và** qua `purchaseListener` (trên main).
     *
     * Trả `false` ngay nếu chưa sẵn sàng (chưa kết nối, chưa có giá) hay đang có luồng mua khác (D22) — app cần biết để
     * hiện thông báo thay vì im lặng không có gì xảy ra.
     */
    public fun purchase(activity: Activity, productId: String, callback: ((IapResult) -> Unit)? = null): Boolean {
        val port = port
        val product = productCache[productId]
        if (port == null || !port.usable() || product == null) {
            LogUtil.logE("IapHelper purchase($productId): chưa sẵn sàng")
            callback?.invoke(IapResult.Failed(-1, "billing chưa sẵn sàng hoặc chưa có giá sản phẩm"))
            logResult(IapResult.Failed(-1, ""), productId)
            return false
        }
        val flow: (IapResult) -> Unit = callback ?: {}
        if (!PurchaseFlow.open(productId, flow)) { // D20/D22: [PurchaseFlow]
            val busy = IapResult.Failed(FLOW_IN_PROGRESS, "đang có luồng mua")
            callback?.invoke(busy)
            logResult(busy, productId)
            return false
        }
        IapEventLog.onPurchaseLaunched(productId)
        billing {
            try {
                port.launch(activity, productId, product.offerToken) { code, message -> billing { onLaunchFailed(flow, productId, code, message) } }
            } catch (e: Exception) {
                LogUtil.logE("IapHelper purchase($productId) crashed: ${e.message}")
                onLaunchFailed(flow, productId, -1, e.message ?: "unknown")
            }
        }
        return true
    }

    private fun onLaunchFailed(flow: (IapResult) -> Unit, productId: String, code: Int, message: String) {
        PurchaseFlow.closeIf(flow)
        val failed = IapResult.Failed(code, message)
        logResult(failed, productId)
        onMain { try { flow(failed) } catch (e: Exception) { LogUtil.logE("IapHelper cb: ${e.message}") } }
    }

    /** Đồng bộ lại quyền lợi từ store. Gọi khi app vào foreground hoặc người dùng bấm "khôi phục". */
    public fun restorePurchases(onDone: ((Set<String>) -> Unit)? = null) {
        val port = port
        if (port == null || !port.usable()) { onDone?.invoke(Entitlements.activeProductIds()); return }
        billing { restoreNow(onDone) }
    }

    /** Luồng billing: INAPP rồi SUBS; token mới vào sổ SAU khi quyền lợi được cấp (D15). */
    private fun restoreNow(onDone: ((Set<String>) -> Unit)?) {
        val port = port ?: return
        val owned = mutableSetOf<String>()
        val subSnapshots = mutableListOf<SubscriptionLifecycle.SubSnapshot>()
        val found = mutableListOf<Pair<PortPurchase, Boolean>>()
        queryOwned(port, IapProductType.INAPP, owned, null, found) { inappOk ->
            queryOwned(port, IapProductType.SUBS, owned, subSnapshots, found) { subsOk ->
                // Store lỗi ở một lượt thì `owned` thiếu, không phải "không sở hữu gì": sync(∅) sẽ tước
                // remove-ads của người đã trả tiền tới lần đồng bộ sau. Giữ nguyên quyền lợi đang có.
                if (!(inappOk && subsOk)) {
                    LogUtil.logE("IapHelper restore: store lỗi, giữ nguyên quyền lợi hiện có")
                    onDone?.let { cb -> val ents = Entitlements.activeProductIds(); onMain { cb(ents) } }
                    return@queryOwned
                }
                LogUtil.logD("IapHelper restore OK owned=${owned.size}")
                // `sync` THAY THẾ toàn bộ: sub đã huỷ không còn trong danh sách store trả về,
                // và phải mất hiệu lực trên máy — xem KDoc của Entitlements.sync.
                Entitlements.sync(owned)
                try {
                    // Vòng đời subscription (doc 23 C.3); danh sách store đầy đủ → complete=true (vắng mặt = churn).
                    SubscriptionLifecycle.observe(subSnapshots)
                } catch (e: Exception) {
                    LogUtil.logE("IapHelper lifecycle observe failed: ${e.message}")
                }
                // D5/D15: token sổ chưa biết vào sổ một lần, sau lượt lấy giá nếu cần ([PurchaseRecorder.recordRestored]).
                val finish: () -> Unit = {
                    recorder.recordRestored(found, appContext)
                    onDone?.let { cb -> onMain { cb(owned) } }
                }
                if (found.any { (p, _) -> recorder.needsPrice(p) }) queryAll(finish) else finish()
            }
        }
    }

    /** Luồng billing (callback port chỉ giao việc). `ok` = false khi store lỗi (port trả danh sách rỗng). */
    private fun queryOwned(
        port: BillingPort,
        type: IapProductType,
        into: MutableSet<String>,
        subSnapshots: MutableList<SubscriptionLifecycle.SubSnapshot>?,
        found: MutableList<Pair<PortPurchase, Boolean>>,
        onDone: (ok: Boolean) -> Unit
    ) {
        port.queryOwned(type) { ok, purchases, _ ->
            billing {
                for (p in purchases) {
                    val state = stateOf(p)
                    // D4/D18: chỉ PURCHASED (không treo) mới cấp; PENDING (kể cả sub) và `suspended` không bao giờ.
                    if (state == IapPurchaseState.PURCHASED) {
                        into.addAll(p.products)
                        // Biên lai chưa acknowledge trong 3 ngày sẽ bị Google tự hoàn tiền; khôi phục là cơ hội cuối.
                        acknowledgeIfNeeded(p)
                        found += p to (type == IapProductType.SUBS) // sổ khử trùng theo token (PurchaseLedger.record)
                    }
                    val pid = p.products.firstOrNull()
                    if (subSnapshots != null && !pid.isNullOrBlank()) {
                        subSnapshots.add(SubscriptionLifecycle.SubSnapshot(productId = pid, state = state, isAutoRenewing = p.autoRenewing))
                    }
                }
                onDone(ok)
            }
        }
    }

    /** D18: giao dịch bị treo (`suspended`) = như PENDING, và không bao giờ cấp. */
    private fun stateOf(p: PortPurchase): IapPurchaseState = if (p.suspended) IapPurchaseState.PENDING else p.state

    // ═══ Xử lý giao dịch ═══

    /**
     * Listener giao dịch của port (Play: `PurchasesUpdatedListener`, luồng main): chỉ lấy callback của luồng mua đang mở
     * ([PurchaseFlow.take]) rồi giao phần xử lý cho luồng billing.
     */
    internal fun onPortPurchasesUpdated(code: Int, purchases: List<PortPurchase>?, message: String) {
        val (cb, flowProduct) = PurchaseFlow.take(code, purchases)
        billing { onUpdated(code, purchases, message, cb, flowProduct) }
    }

    private fun onUpdated(code: Int, purchases: List<PortPurchase>?, message: String, cb: ((IapResult) -> Unit)?, flowProduct: String) {
        when (code) {
            BillingPort.OK -> {
                if (purchases.isNullOrEmpty()) {
                    dispatch(IapResult.Failed(code, "OK nhưng không có giao dịch"), cb, flowProduct)
                    return
                }
                for (p in purchases) handlePurchase(p, cb.takeIf { flowProduct in p.products }) // callback đúng sản phẩm
            }
            BillingPort.USER_CANCELED -> dispatch(IapResult.UserCanceled, cb, flowProduct)
            BillingPort.ITEM_ALREADY_OWNED -> {
                // Thường là quyền lợi có mà máy chưa biết. Đồng bộ lại thay vì chỉ báo lỗi.
                restorePurchases()
                dispatch(IapResult.AlreadyOwned, cb, flowProduct)
            }
            else -> dispatch(IapResult.Failed(code, message), cb, flowProduct)
        }
    }

    private fun handlePurchase(p: PortPurchase, cb: ((IapResult) -> Unit)?) {
        val productId = p.products.firstOrNull() ?: return
        val cached = productCache[productId]
        val purchase = IapPurchase(
            productId = productId,
            orderId = p.orderId,
            purchaseToken = p.token,
            purchaseTimeMs = p.timeMs,
            state = stateOf(p),
            isAcknowledged = p.acknowledged,
            isAutoRenewing = p.autoRenewing,
            quantity = p.quantity,
            priceMicros = cached?.priceMicros ?: 0L,
            currencyCode = cached?.currencyCode ?: ""
        )

        if (purchase.state != IapPurchaseState.PURCHASED) {
            // Giao dịch chờ duyệt: TUYỆT ĐỐI không cấp quyền lợi và không tính doanh thu.
            // Nó có thể không bao giờ hoàn tất.
            dispatch(IapResult.Pending(purchase), cb, productId)
            return
        }

        acknowledgeIfNeeded(p)
        Entitlements.grant(productId)
        if (cached?.type == IapProductType.SUBS) {
            // Win-back phải ra khỏi LAPSED ngay, không đợi lần restore kế tiếp. complete=false: chỉ sản phẩm này.
            try {
                SubscriptionLifecycle.observe(
                    listOf(SubscriptionLifecycle.SubSnapshot(productId, purchase.state, purchase.isAutoRenewing)),
                    complete = false
                )
            } catch (e: Exception) {
                LogUtil.logE("IapHelper lifecycle fast-path failed: ${e.message}")
            }
        }
        recorder.recordUpdated(p, cached?.type == IapProductType.SUBS) // R-T11 m2: chưa có giá → luật của khôi phục
        dispatch(IapResult.Success(purchase), cb, productId)
    }

    private fun acknowledgeIfNeeded(p: PortPurchase) {
        val port = port ?: return
        val productId = p.products.firstOrNull() ?: return
        try {
            if (productId in consumableIds) {
                // Consumable: consume để mua lại được. Chỉ làm khi app đã KHAI BÁO rõ —
                // đoán sai theo hướng này sẽ xoá vĩnh viễn quyền lợi người dùng đã trả tiền.
                port.consume(p.token) { code, _ ->
                    if (code != BillingPort.OK) LogUtil.logE("IapHelper consume failed: $code")
                    billing { recorder.onConsumed(p, code == BillingPort.OK) } // R-T11 m6: consumable chưa giá chờ kết quả
                }
            } else if (!p.acknowledged) {
                port.acknowledge(p.token) { code, _ ->
                    if (code != BillingPort.OK) {
                        LogUtil.logE("IapHelper acknowledge failed: $code")
                        // Lần khôi phục kế thấy giao dịch còn chưa ack và ack lại (Play hoàn tiền sau 3 ngày).
                        IapEventLog.iapResult(IapEventLog.OUTCOME_ACK_FAILED, code, productId)
                    }
                }
            }
        } catch (e: Exception) {
            LogUtil.logE("IapHelper acknowledge/consume crashed: ${e.message}")
        }
    }

    private fun logResult(result: IapResult, productId: String) = PurchaseLedger.logResult(result, productId)

    private fun dispatch(result: IapResult, cb: ((IapResult) -> Unit)?, productId: String) {
        logResult(result, productId)
        onMain {
            try { cb?.invoke(result) } catch (e: Exception) { LogUtil.logE("IapHelper cb: ${e.message}") }
            try { purchaseListener?.invoke(result) } catch (e: Exception) { LogUtil.logE("IapHelper listener: ${e.message}") }
        }
    }
}
