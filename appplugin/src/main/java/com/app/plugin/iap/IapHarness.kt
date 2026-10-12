package com.app.plugin.iap

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.app.plugin.BuildConfig
import com.app.plugin.SdkKillSwitch
import com.app.plugin.consent.ConsentState
import com.app.plugin.consent.ConsentTier
import com.app.plugin.logevent.LogEventServerHelper
import com.app.plugin.paywall.PaywallManager
import com.app.plugin.until.AppUtil
import java.lang.ref.WeakReference
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicInteger

/**
 * Harness billing G12 (1.8.0 Task 8, §Hợp đồng B, D8): chạy một kịch bản của [IapScripts] **qua state machine thật** của
 * `IapHelper` — chỉ lớp store bị thay bằng [ScriptedBillingPort].
 *
 * **Arm khi và chỉ khi đủ 3 khoá**, xét theo thứ tự, thiếu khoá nào thì dừng ở đó (không đọc intent, không tạo port,
 * không luồng/ghi đĩa nào):
 * 1. `AppUtil.isTestFlagOn(context)` (string `flagTest` ∈ {"1","90351"}; app release có `flagTest` 0);
 * 2. meta-data [META_KEY] = `true` trong manifest (chỉ `app/src/utility/AndroidManifest.xml` của demo);
 * 3. intent của activity có extra [EXTRA_SCRIPT] với tên ∈ [IapScripts] (tên lạ → không arm, 1 dòng log).
 * Khoá 1 và 2 là hằng của process: đọc một lần. Kill switch `iap` (`sdk_kill_android`) → không arm.
 *
 * Arm → trên luồng "appplugin-billing": `IapHelper.replacePort(port kịch bản)` rồi `end()` port cũ (Play), log
 * `iap harness: armed script=<tên>`, áp catalog/cấu hình của script rồi chạy: BUY = paywall demo (nếu có) + `purchase`,
 * RESTORE = `restorePurchases`, OBSERVE = đọc mask; xong → `iap harness: <tên> done` (T16 chờ dòng này).
 */
internal object IapHarness {
    const val META_KEY = "com.app.plugin.IAP_HARNESS"
    const val EXTRA_SCRIPT = "appplugin_iap_script"
    const val IN_APP_MESSAGES_LINE = "iap harness: in-app-messages call"
    private const val TAG = "AppPlugin"
    private const val CONSENT_POLL_MS = 500L
    private const val CONSENT_POLL_MAX = 240

    /** Khoá 1 + 2 của process: `null` = chưa đọc. */
    @Volatile private var processKeys: Boolean? = null

    /** Script đang chạy trong process này (`null` = harness không arm). */
    @Volatile var armed: IapScripts.Script? = null
        private set

    /** Port kịch bản đã giao cho `IapHelper` — `BillingPorts.select` trả lại nó nếu `IapHelper.init` tới sau khi arm. */
    @Volatile var port: BillingPort? = null
        private set

    /** Luồng billing (`BillingThread.executor`); test thay. `null` = mặc định, không chạm `BillingThread` khi không arm. */
    @Volatile internal var executor: Executor? = null
    @Volatile internal var logSink: (String) -> Unit = { Log.i(TAG, it) }
    private val steps = AtomicInteger(0)

    /** `AppPluginBase.onActivityCreated` (main). App không đủ khoá 1 + 2: sau lần đầu chỉ còn một lần đọc biến. */
    fun onActivityCreated(activity: Activity) {
        if (processKeys == false || armed != null) return
        try {
            val script = select(activity) ?: return
            arm(activity, script)
        } catch (e: Exception) {
            Log.e(TAG, "iap harness: không arm được: ${e.message}")
        }
    }

    /** D2: `showInAppMessages` im dưới harness trừ khi script cho phép; harness không arm → không cấm. */
    fun inAppMessagesAllowed(): Boolean = armed?.inAppMessages ?: true

    fun log(line: String) = logSink(line)

    fun step(name: String, text: String) = log("iap harness: $name step=${steps.incrementAndGet()} $text")

    private fun select(activity: Activity): IapScripts.Script? {
        val keys = processKeys ?: (AppUtil.isTestFlagOn(activity) && manifestKeyOn(activity)).also { processKeys = it }
        if (!keys) return null
        val name = activity.intent?.getStringExtra(EXTRA_SCRIPT) ?: return null
        val script = IapScripts.find(name)
        if (script == null) {
            log("iap harness: script lạ '$name' — không arm")
            return null
        }
        if (SdkKillSwitch.current.killsIap(BuildConfig.SDK_VERSION)) {
            log("iap harness: kill switch iap — không arm script=$name")
            return null
        }
        return script
    }

    private fun manifestKeyOn(ctx: Context): Boolean =
        ctx.packageManager.getApplicationInfo(ctx.packageName, PackageManager.GET_META_DATA).metaData?.getBoolean(META_KEY, false) == true

    private fun arm(activity: Activity, script: IapScripts.Script) {
        armed = script
        val act = WeakReference(activity)
        (executor ?: BillingThread.executor).execute {
            try {
                if (script.scripted) swap(script)
                log("iap harness: armed script=${script.name}")
                run(script, act)
            } catch (e: Exception) {
                step(script.name, "lỗi ${e.message}")
                done(script)
            }
        }
    }

    /** Luồng billing: port cũ (Play) `end()` sau khi `IapHelper` đã cầm port kịch bản. */
    private fun swap(script: IapScripts.Script) {
        val old = BillingPorts.active
        val scripted = BillingPorts.harness(script)
        port = scripted
        IapHelper.replacePort(scripted)
        if (old !== scripted) old?.end()
    }

    private fun run(s: IapScripts.Script, act: WeakReference<Activity>) {
        s.lifecycleConfig?.let { step(s.name, "lifecycle applied=${SubscriptionLifecycle.apply(it).applied}") }
        if (s.action == IapScripts.Action.OBSERVE) {
            step(s.name, "mask=${Entitlements.removeAdsMask()} active=${Entitlements.activeProductIds().sorted()}")
            done(s)
            return
        }
        IapHelper.setProducts(inapp = listOf(IapScripts.INAPP_ID), subs = listOf(IapScripts.SUB_ID))
        Entitlements.setRemoveAdsMasks(IapScripts.MASKS)
        if (s.viaPaywall) PaywallManager.apply(IapScripts.PAYWALL_CONFIG)
        IapHelper.queryProducts { products ->
            step(s.name, "products n=${products.size}")
            when {
                s.action == IapScripts.Action.RESTORE -> restore(s)
                s.waitConsent -> awaitConsent(s, act, 0)
                else -> buy(s, act)
            }
        }
    }

    private fun restore(s: IapScripts.Script) = IapHelper.restorePurchases { owned ->
        step(s.name, "restore owned=${owned.sorted()} mask=${Entitlements.removeAdsMask()}")
        done(s)
    }

    /** Script 11: chờ người thử chọn consent (T16 chạm "Do not consent") rồi mới mua. */
    private fun awaitConsent(s: IapScripts.Script, act: WeakReference<Activity>, attempt: Int) {
        val tier = ConsentState.tier()
        if (tier != ConsentTier.UNKNOWN || attempt >= CONSENT_POLL_MAX) {
            step(s.name, "consent tier=$tier")
            BillingThread.postMain { buy(s, act) }
            return
        }
        BillingThread.timer.schedule(CONSENT_POLL_MS, Runnable { awaitConsent(s, act, attempt + 1) })
    }

    /** Main: paywall demo (mở phiên paywall như app thật) rồi `IapHelper.purchase`; kết quả đọc qua `purchaseListener`. */
    private fun buy(s: IapScripts.Script, act: WeakReference<Activity>) {
        val activity = act.get()
        if (activity == null) {
            step(s.name, "activity đã huỷ")
            done(s)
            return
        }
        if (s.viaPaywall) showPaywall(s)
        listen(s)
        val launched = IapHelper.purchase(activity, s.productId)
        step(s.name, "purchase ${s.productId} launched=$launched")
        if (!launched || s.updates.isEmpty()) done(s)
    }

    private fun showPaywall(s: IapScripts.Script) {
        val uid = runCatching { LogEventServerHelper.currentUserId() }.getOrNull().orEmpty().ifBlank { "g12" }
        val decided = PaywallManager.paywallFor(IapScripts.PAYWALL_PLACEMENT, uid)
        // Brain có thể từ chối (bandit): harness vẫn cần một lượt hiện để đo phễu → biến thể cố định của cấu hình demo.
        val paywall = decided ?: PaywallManager.Paywall(
            IapScripts.PAYWALL_PLACEMENT, IapScripts.PAYWALL_VARIANT, listOf(s.productId), s.productId, IapScripts.PAYWALL_VERSION
        )
        PaywallManager.onPaywallShown(paywall)
        step(s.name, "paywall shown variant=${paywall.variantId} decided=${decided != null}")
    }

    /** Nối vào `purchaseListener` của app (giữ listener cũ) tới kết quả cuối; PENDING chưa phải cuối (script 4). */
    private fun listen(s: IapScripts.Script) {
        val previous = IapHelper.purchaseListener
        IapHelper.purchaseListener = { result ->
            previous?.invoke(result)
            step(s.name, "result ${label(result)}")
            if (result !is IapResult.Pending) {
                IapHelper.purchaseListener = previous
                done(s)
            }
        }
    }

    private fun label(r: IapResult): String = when (r) {
        is IapResult.Success -> "success product=${r.purchase.productId} mask=${Entitlements.removeAdsMask()}"
        is IapResult.Pending -> "pending active=${Entitlements.activeProductIds().sorted()}"
        IapResult.UserCanceled -> "cancel"
        IapResult.AlreadyOwned -> "already_owned"
        is IapResult.Failed -> "failed code=${r.code}"
        else -> r.javaClass.simpleName
    }

    private fun done(s: IapScripts.Script) = log("iap harness: ${s.name} done")

    /** Test: như process mới. */
    internal fun resetForTest() {
        processKeys = null
        armed = null
        port = null
        steps.set(0)
        executor = null
        logSink = { Log.i(TAG, it) }
    }
}
