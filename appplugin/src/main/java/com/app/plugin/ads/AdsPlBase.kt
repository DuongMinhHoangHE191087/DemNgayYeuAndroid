package com.app.plugin.ads

import android.app.Activity
import android.content.Context
import com.app.plugin.ads.core.UnitGate
import com.app.plugin.app.AppPluginManager
import com.app.plugin.firbase.FirHelper
import com.app.plugin.logevent.EventNames
import com.app.plugin.until.Constants
import com.app.plugin.until.LogUtil

/**
 * Google: app open ad hết hạn sau 4 giờ kể từ lúc load; hiện quảng cáo cũ hơn thì không được tính.
 * Mặc định (và trần) của `app_open_max_age_minutes` trong `ads_policy_android` ([AdsPolicy.appOpenMaxAgeMinutes]).
 */
internal const val APP_OPEN_MAX_AGE_MS = 4 * 60 * 60 * 1000L

/** Nhãn `error` của `onLoadFailed` khi mạng của placement sẽ không khởi tạo trong phiên (consent, P1). */
private const val NETWORK_NOT_STARTED = "network_not_started"

/** Nhãn `error` của `onLoadFailed` khi lượt bị bỏ qua vì unit vừa lỗi còn trong backoff (2.4.4 INFLIGHT). */
private const val UNIT_BACKOFF = "unit_backoff"

public abstract class AdsPlBase(context: Context, adBase: AdsBase, type: Int, pl: String) {
    protected var adBase: AdsBase

    protected var adsType: Int = 0
    protected var mContext: Context
    protected var mActivity: Activity? = null
    protected lateinit var adsPlacementEcmp: AdsPlacementEcmpBase
    protected var showPl: String = ""
    protected var getPl: String = ""
    protected var loadPl: String = ""

    /** T6 (D48): quảng cáo → placement lượt show/attach của nó (khoá yếu); monitor = map, chờ: show/attach + paid/click (main), không I/O. */
    private val turnPlByAd = java.util.WeakHashMap<Any, String>()
    /** Placement của lượt [ad] được hiện (paid/click đọc qua đây, không đọc [showPl]/[getPl] lúc callback); [live] khi chưa có. */
    internal fun turnPl(ad: Any?, live: String): String = ad?.let { synchronized(turnPlByAd) { turnPlByAd[it] } } ?: live

    /**
     * Nhãn của lớp placement trong log và tên event (`ads_pl_<eventTag>_<adsType>`, [EventNames.adsPlActivityNull]):
     * `<mạng>_<định dạng>`, snake_case, ví dụ `mob_full`, `fb_bn`, `max_open`. Hằng chữ, không lấy từ tên lớp: R8
     * của app đổi tên lớp SDK, event thành `ads_a0`. Khai bằng getter (`get() = "…"`), không backing field, vì
     * constructor của lớp cha (`onInit`) chạy trước khi field của lớp con được gán. `EventNameContractTest` kiểm
     * mọi lớp placement khai nó, đúng dạng, không trùng.
     */
    protected abstract val eventTag: String

    /** Nhãn log `<eventTag>_<adsType>` cho lớp khác trong SDK (vd `AdsEcmps`), thay tên lớp mà R8 đổi (review E M8). */
    internal val logTag: String get() = "${eventTag}_$adsType"

    /**
     * Tuổi tối đa (ms) của quảng cáo đã load; quá tuổi là chưa load: [show] không hiện, [load] tải mới,
     * [hasLoadedAd] và [hasFreshAdForReuse] là `false`. `null` (mặc định: banner, rect, NtCl) là không hết hạn;
     * native inline ([AdsPlBaseNative]) không ghi đè thì dùng [AdsPolicy.fullscreenMaxAgeMillis] (mặc định 60 phút,
     * 2.4.3 NATAGE, xem [loadedAdWithinMaxAge]); app open AdMob ghi đè bằng [AdsPolicy.appOpenMaxAgeMillis] (mặc định [APP_OPEN_MAX_AGE_MS]; MAX app
     * open miễn, Task I1), interstitial,
     * rewarded và native full bằng [AdsPolicy.fullscreenMaxAgeMillis] (mặc định 60 phút), trừ MAX interstitial và
     * rewarded (MAX tự hết hạn và nạp lại trong cùng đối tượng, review E I1). Lớp ghi đè phải lấy quảng
     * cáo qua [loadedAdWithinMaxAge] ở mọi chỗ (show, `getAdForShow`, dựng sẵn), không gọi thẳng
     * `adEcmps?.getAdLoaded()`. Khác cửa sổ tái dùng 1 giờ của [AdsEcmp.isFreshForReuse].
     */
    protected open val loadedAdMaxAgeMs: Long? get() = null

    /** Nhãn `ad_show_fail_reason` khi quảng cáo quá [loadedAdMaxAgeMs] bị bỏ; app open ghi đè `app_open_expired`. */
    protected open val loadedAdExpiredReason: String get() = Constants.AdShowFailReason.AD_EXPIRED

    /**
     * Quảng cáo đã load còn trong [loadedAdMaxAgeMs] (native inline không ghi đè: [AdsPolicy.fullscreenMaxAgeMillis],
     * 2.4.3 NATAGE); quảng cáo quá tuổi bị bỏ trong lúc tìm (xem [AdsEcmps.getAdLoaded]). Lúc placement đang hiện thì không xét tuổi: bỏ (với MAX là `destroy()`)
     * quảng cáo đang trên màn hình làm mất callback đóng, app không nhận `onAdDismissed`.
     */
    protected fun loadedAdWithinMaxAge(): AdsEcmp? {
        val maxAgeMs = if (adsPlacementEcmp.isShowing) null else loadedAdMaxAgeMs ?: nativeMaxAgeMs()
        return adsPlacementEcmp.adEcmps?.getAdLoaded(maxAgeMs, expiredReason = loadedAdExpiredReason)
    }

    /** 2.4.3 NATAGE: native inline ([AdsPlBaseNative]) dùng hạn toàn màn hình; lớp khác không hạn (`null`). */
    private fun nativeMaxAgeMs(): Long? = if (this is AdsPlBaseNative) AdsPolicy.current.fullscreenMaxAgeMillis() else null

    protected abstract fun onInit()

    /**
     * Gửi request load tới mạng. Hợp đồng bắt buộc với lớp con (Task I review Minor 1): phải đặt
     * `adsPlacementEcmp.isLoading = true` trước khi gửi request, để [isStaleLoadCallback] còn phân biệt được callback
     * của lượt đang chờ với callback muộn của lượt đã bỏ. [isStaleLoadCallback] bỏ callback khi generation đã đổi
     * (watchdog của slot đã bỏ lượt đó, hoặc lượt kế đã bắt đầu) hoặc khi slot đang rảnh (`isLoading == false`, vd MAX
     * tự nạp lại sau khi hết hạn mà không có request nào chờ). Lớp con quên đặt `isLoading` sẽ âm thầm mất callback
     * cho tới khi watchdog 60/90 s can thiệp.
     */
    protected abstract fun tryLoad()

    /**
     * Giữ lần load này tới khi SDK của mạng khởi tạo xong, tức sau consent (xem `AdSdkStartGate`).
     * Load của Meta hay MAX mà chạy trước đó thì SDK của mạng tự khởi tạo, vượt qua cổng consent.
     *
     * Mạng sẽ không khởi tạo trong phiên (hàng chờ đã đóng, `SdkReadyQueue.close`: Meta với `DENIED` trong vùng
     * GDPR, P1) thì load báo fail đúng một lần, ngay hoặc lúc hàng đóng, thay vì chờ mãi.
     *
     * @return `true` khi đã giữ lại hoặc đã báo fail; `tryLoad()` phải return ngay.
     */
    internal fun holdUntilReady(queue: SdkReadyQueue): Boolean {
        if (queue.isReady) return false
        adsPlacementEcmp.isLoading = true
        LogUtil.logD("ads ${eventTag}_$adsType load held until the network starts")
        // 2.5.0 PRIO (Fable C1a): hẹn fail không vào lại cổng — tiêu dấu nhường (nếu có) rồi fail.
        queue.runWhenReady(unlessLoadAbandoned { tryLoad() }, sameTurn { UnitGate.takeYield(this); failLoadNetworkNotStarted() })
        return true
    }

    /**
     * [block] của một lượt load được hẹn chạy sau (lượt giữ chờ mạng khởi tạo), bọc để không chạy nếu watchdog của
     * slot đã bỏ lượt đó trước khi tới giờ (Task I2, [AdsPlacementEcmpBase.loadGeneration]): lượt đó đã báo
     * `onLoadFailed` một lần, chạy tiếp là gửi request không ai chờ (hoặc callback thứ hai).
     *
     * 2.5.0 PRIO (Fable C1a): lượt này đã nhường unit cho lượt mức cao hơn khi còn giữ chờ ([UnitGate.takeYield], tiêu
     * một lần) ⇒ thay [block] (hẹn `tryLoad`) bằng vào lại cổng (`startTurn`, không bypass), xếp sau lượt mới.
     */
    internal fun unlessLoadAbandoned(block: () -> Unit): () -> Unit = sameTurn {
        val yieldedPl = UnitGate.takeYield(this)
        if (yieldedPl != null) startTurn(yieldedPl, bypass = false) else block()
    }

    /** [block] chỉ chạy khi generation của slot chưa đổi (watchdog chưa bỏ lượt, chưa có lượt mới). */
    private fun sameTurn(block: () -> Unit): () -> Unit {
        val generation = adsPlacementEcmp.loadGeneration
        return {
            if (generation == adsPlacementEcmp.loadGeneration) {
                block()
            } else {
                LogUtil.logD("ads ${logTag} $loadPl ${adsPlacementEcmp.originPl} held load dropped: abandoned by load timeout")
            }
        }
    }

    /**
     * Callback load của mạng có phải của một lượt đã bỏ không (Task I review I-2, M1). [generation] là
     * [AdsPlacementEcmpBase.loadGeneration] mà listener chụp lúc gửi request. Bỏ khi generation đã đổi (watchdog của
     * slot đã bỏ lượt đó rồi slot bắt đầu lượt khác) hoặc slot đang rảnh (không request nào chờ: MAX tự nạp lại sau
     * khi hết hạn, app open MAX hết hạn khi không có Activity). Listener thấy `true` thì `return` ngay: không chạm
     * trạng thái slot/waterfall, không gọi `adsLoadCb`; quảng cáo tới muộn đưa qua [keepLateAd], lỗi lúc slot rảnh trên
     * đối tượng đang giữ (MAX tự nạp lại lỗi) qua [dropHeldAdOnIdleLoadFailure].
     */
    internal fun isStaleLoadCallback(generation: Int): Boolean {
        val current = adsPlacementEcmp.loadGeneration
        if (generation == current && adsPlacementEcmp.isLoading) return false
        LogUtil.logD(
            "ads $logTag $loadPl ${adsPlacementEcmp.originPl} late load callback dropped: generation $generation now $current isLoading=${adsPlacementEcmp.isLoading}"
        )
        return true
    }

    /**
     * Quảng cáo của lượt đã bỏ tới muộn ([isStaleLoadCallback]): mục [ecmp] đang rảnh và trống thì giữ làm quảng cáo
     * đã load (Task I2: lần load sau trả nó ngay, không request mới; tuổi tính từ lúc tới); mạng nạp lại trong cùng đối
     * tượng (MAX) thì đã giữ sẵn; còn lại (mục đang có request mới, hoặc đã có quảng cáo khác) thì [destroyAd].
     * Task K mục 2: chỉ giữ quảng cáo sẵn sàng hiện ([isLoadedAdReady]); đối tượng MAX `isReady == false` (app open đã
     * destroy, interstitial vừa tự nạp lại lỗi) không bao giờ được giữ lại, không dựa vào việc MAX gỡ listener.
     */
    internal fun keepLateAd(ecmp: AdsEcmp, ad: Any?) {
        ad ?: return
        when {
            ecmp.adObject === ad -> Unit
            !ecmp.isLoading && ecmp.adObject == null && isLoadedAdReady(ad) -> {
                ecmp.adObject = ad
                ecmp.isLoaded = true
                LogUtil.logD("ads $logTag $loadPl ${adsPlacementEcmp.originPl} late ad kept id=${ecmp.adsId}")
            }
            else -> {
                destroyAd(ad)
                LogUtil.logD("ads $logTag $loadPl ${adsPlacementEcmp.originPl} late ad dropped id=${ecmp.adsId}")
            }
        }
    }

    /**
     * `onAdLoadFailed` tới khi slot rảnh ([isStaleLoadCallback] `true` mà không có request nào chờ) từ chính đối tượng
     * mục [ecmp] đang giữ (Task J review IMP-2): MAX hết hạn rồi tự nạp lại trong cùng đối tượng và lỗi (no fill, offline,
     * app open thiếu Activity); MAX không tự thử lại nên đối tượng sẽ không bao giờ `isReady()` nữa. Giữ nó thì
     * [holdsLoadedAd] `true` mãi: `AdsHelper.dropStaleLoadedFlag` coi mạng là bận, không nạp lại, và
     * `isInterstitialReadyToShow` `false` cả phiên trong khi `isInterstitialLoaded` `true` — app theo mẫu ready → show /
     * !loaded → load / chờ sẽ chờ tới hết process. Nay bỏ đối tượng khỏi mục (đặt lại như sau `onAdLoadFailed` thường:
     * `isLoaded = false`, `adObject = null`) và [destroyAd] nó (no-op với MAX interstitial/rewarded create-once — lần
     * `loadAd()` sau dùng lại; `destroy()` với MAX app open — lần load sau tạo đối tượng mới). Không đẩy waterfall, không
     * `tryLoad`, không callback: lần `dropStaleLoadedFlag`/[load] kế thấy mục trống và gửi `loadAd()` thật. Có request
     * đang chờ ([AdsPlacementEcmpBase.isLoading]) thì callback của lượt đó tự xử lý; đối tượng khác thì không đụng.
     *
     * @return `true` khi đã bỏ đối tượng.
     */
    internal fun dropHeldAdOnIdleLoadFailure(ecmp: AdsEcmp, ad: Any?): Boolean {
        if (ad == null || adsPlacementEcmp.isLoading || ecmp.adObject !== ad) return false
        ecmp.isLoaded = false
        ecmp.adObject = null
        destroyAd(ad)
        LogUtil.logD("ads $logTag $loadPl ${adsPlacementEcmp.originPl} held ad dropped: self-reload failed id=${ecmp.adsId}")
        return true
    }

    /**
     * Mạng của placement này sẽ không khởi tạo trong phiên: bỏ trạng thái đang load và báo `onLoadFailed` một lần,
     * để waterfall đi tiếp sang mạng sau. Không dựng quảng cáo nào (dựng là để SDK của mạng tự khởi tạo).
     */
    private fun failLoadNetworkNotStarted() {
        LogUtil.logE("ads ${eventTag}_$adsType $loadPl load failed: network not started (consent)")
        adsPlacementEcmp.isLoading = false
        adsPlacementEcmp.adsLoadCb?.onLoadFailed(loadPl, adsPlacementEcmp.originPl, "", NETWORK_NOT_STARTED)
        onFinishLoad()
    }

    public open fun load(placement: String, adsLoadCb: AdsLoadInnerCb?) {
        if (!adsPlacementEcmp.isLoading) {
            if (mActivity != null) {
                setLoadPlacement(placement)
                adsPlacementEcmp.adsLoadCb = adsLoadCb
                // Lối tắt "has loaded" xét *còn giữ* quảng cáo (như [holdsLoadedAd]), không xét sẵn sàng như
                // [hasLoadedAd]/[isLoadedAdReady]: MAX đang tự nạp lại trong cùng đối tượng (`isReady() == false`) vẫn
                // là đã load — gửi `loadAd()` lúc đó là request thừa trên đối tượng đang nạp; app "chờ" (Task J4). Đối
                // tượng MAX nạp lại lỗi không ở lại đây ([dropHeldAdOnIdleLoadFailure]), nên lối tắt này không lặp mãi.
                releaseUnloadedItemRefs()
                val adsEcmp = getAdLoaded()
                if (adsEcmp != null) {
                    LogUtil.logD("ads ${eventTag}_$adsType $placement ${adsPlacementEcmp.originPl} tryLoad has loaded")
                    adsLoadCb?.onLoaded(
                        loadPl, adsPlacementEcmp.originPl, adsEcmp.adsId, adsEcmp, this
                    )
                    return
                }
                startTurn(placement, bypass = false)
            } else {
                LogUtil.logD("ads ${eventTag}_$adsType ${placement} ${adsPlacementEcmp.originPl} load is activity null")
                FirHelper.instance.logEvent(EventNames.adsPlActivityNull(eventTag, adsType))
                adsLoadCb?.onLoadFailed(placement, adsPlacementEcmp.originPl, "", "isActivitynull")
            }
        } else if (adsLoadCb != null && adsLoadCb !== UnitGate.original(adsPlacementEcmp.adsLoadCb)) {
            // 2.4.0 CARRY-ADS #17: slot đang load → gắn callback vào lượt (trước: báo "isloading"); các callback nhận kết quả thật
            // theo thứ tự gắn. Lượt kế (slot rảnh) gán callback mới, chuỗi cũ bỏ.
            LogUtil.logD("ads ${eventTag}_$adsType ${placement} ${adsPlacementEcmp.originPl} load is loading, attaching callback")
            adsPlacementEcmp.adsLoadCb = adsPlacementEcmp.adsLoadCb?.let { chainLoadCb(it, adsLoadCb) } ?: adsLoadCb
        }
    }

    /**
     * 2.4.4 INFLIGHT: đầu lượt hỏi [UnitGate] (slot `mob_*`/`fb_*` không phải [AdsPlBaseBn]; công tắc
     * `ads_cf_unit_gate_off`): bắt đầu ở id nó trả, vào hàng chờ (`isLoading = true`, hẹn bọc [unlessLoadAbandoned]) hoặc
     * bỏ qua (`unit_backoff`, 0 request). [bypass]: hết trần chờ, gửi không xét đặt chỗ từ id đầu không backoff.
     */
    private fun startTurn(placement: String, bypass: Boolean) {
        val ids = adsPlacementEcmp.adEcmps?.list?.map { it.adsId }.orEmpty()
        if (ids.isEmpty() || !isUnitGated()) return beginTurn(0, null, ids)
        if (bypass) return beginTurn(UnitGate.firstFree(ids), placement, ids)
        val generation = adsPlacementEcmp.loadGeneration
        val again = sameTurn { startTurn(placement, bypass = false) }
        val force = sameTurn { startTurn(placement, bypass = true) } // Fable C2: trần 10 s vẫn bypass như 2.4.4
        val live = { generation == adsPlacementEcmp.loadGeneration && isLoadingAd }
        when (val idx = UnitGate.admit(this, placement, adsPlacementEcmp.originPl, ids, live) { if (it) force() else again() }) {
            UnitGate.PARKED -> adsPlacementEcmp.isLoading = true
            UnitGate.SKIPPED -> skipTurn(placement)
            else -> beginTurn(idx, placement, ids)
        }
    }

    /** Bắt đầu lượt ở id [idx]; [placement] khác `null` ⇒ đặt chỗ unit và bọc callback để nhả ở kết quả đầu. */
    private fun beginTurn(idx: Int, placement: String?, ids: List<String>) {
        adsPlacementEcmp.adEcmps?.idxCurrEcpm = idx
        adsPlacementEcmp.loadGeneration++ // lượt mới: callback còn lại của lượt trước là muộn, listener bỏ
        if (placement != null) {
            adsPlacementEcmp.isLoading = false // người chờ được nhả: về tiền điều kiện tryLoad của 2.4.3 (slot rảnh)
            val generation = adsPlacementEcmp.loadGeneration
            adsPlacementEcmp.adsLoadCb = UnitGate.hold(
                this, placement, adsPlacementEcmp.originPl, ids, idx,
                { generation == adsPlacementEcmp.loadGeneration && isLoadingAd }, adsPlacementEcmp.adsLoadCb,
            )
        }
        tryLoad()
    }

    /** Mọi id của lượt đang backoff do chính slot + placement này vừa lỗi: báo fail ngay, 0 request. */
    private fun skipTurn(placement: String) {
        val wasParked = adsPlacementEcmp.isLoading
        if (wasParked) adsPlacementEcmp.isLoading = false
        adsPlacementEcmp.adsLoadCb?.onLoadFailed(placement, adsPlacementEcmp.originPl, "", UNIT_BACKOFF)
        if (wasParked) onFinishLoad()
    }

    private fun isUnitGated(): Boolean =
        this !is AdsPlBaseBn && (eventTag.startsWith("mob_") || eventTag.startsWith("fb_")) && !UnitGate.isOff(mContext)

    /** Hai callback của cùng lượt: [first] rồi [then], mỗi kết quả một lần mỗi cái; [first] ném thì [then] vẫn nhận. */
    private fun chainLoadCb(first: AdsLoadInnerCb, then: AdsLoadInnerCb): AdsLoadInnerCb = object : AdsLoadInnerCb {
        override fun onLoaded(placement: String, originPl: String, adId: String, ecmp: AdsEcmp?, adspl: AdsPlBase?) =
            try { first.onLoaded(placement, originPl, adId, ecmp, adspl) } finally { then.onLoaded(placement, originPl, adId, ecmp, adspl) }
        override fun onLoadFailed(placement: String, originPl: String, adId: String, error: String) =
            try { first.onLoadFailed(placement, originPl, adId, error) } finally { then.onLoadFailed(placement, originPl, adId, error) }
    }

    /**
     * Chạy trong [load] khi slot rảnh, trước khi xét quảng cáo đã load và trước request mới (final review Minor 2).
     * Mặc định không làm gì; banner/rect ([AdsPlBaseBn]) bỏ tham chiếu view refresh đang "ngồi" mục mà không phải quảng
     * cáo đã load.
     */
    internal open fun releaseUnloadedItemRefs() {}

    private fun getAdLoaded(): AdsEcmp? {
        adsPlacementEcmp.adEcmps?.list?.forEach {
            if (it.isLoaded && it.adObject == null) it.isLoaded = false
        }
        return loadedAdWithinMaxAge()
    }

    /** Slot đang tải (kể cả đang giữ chờ mạng khởi tạo, [holdUntilReady]); [load] lúc này trả `"isloading"`. */
    internal val isLoadingAd: Boolean get() = adsPlacementEcmp.isLoading

    internal fun hasFreshAdForReuse(): Boolean =
        !adsPlacementEcmp.isShowing && !adsPlacementEcmp.isLoading &&
            loadedAdWithinMaxAge()?.isFreshForReuse() == true

    /**
     * Placement có quảng cáo đã load, còn dùng được (trong [loadedAdMaxAgeMs]) và sẵn sàng hiện ([isLoadedAdReady]).
     *
     * **Chỉ gọi trên main thread**, và đây không phải phép đọc thuần: quảng cáo quá tuổi (app open quá 4 giờ;
     * interstitial, rewarded, native full, native inline quá 60 phút) bị bỏ ngay trong lời gọi này — kèm [destroyAd]
     * của lớp (native: `destroy()`), log và `ad_show_fail_reason` = [loadedAdExpiredReason]
     * (`app_open_expired`, `ad_expired`); mục đánh dấu đã load mà mất `adObject` được đặt lại là chưa load.
     * Placement đang hiện thì không xét tuổi (xem [loadedAdWithinMaxAge]). MAX interstitial/rewarded/app open đang tự
     * nạp lại (`isReady() == false`, Task J4) trả `false` mà không bỏ quảng cáo; xem [holdsLoadedAd].
     */
    public fun hasLoadedAd(): Boolean {
        val ad = getAdLoaded()?.adObject ?: return false
        return isLoadedAdReady(ad)
    }

    /**
     * Quảng cáo đã load [ad] có sẵn sàng hiện không (Task J4). Mặc định `true`; MAX interstitial, rewarded, app open
     * ghi đè bằng `isReady()` của đối tượng (`false` khi MAX đang tự nạp lại sau hết hạn, hoặc nạp lại lỗi), để
     * [hasLoadedAd] và `show` (Task I4) cùng kết luận. Chỉ là phép đọc: không bỏ, không destroy, không nạp lại.
     */
    internal open fun isLoadedAdReady(ad: Any): Boolean = true

    /**
     * Placement còn giữ một quảng cáo đã load (trong [loadedAdMaxAgeMs]), sẵn sàng hay đang được mạng nạp lại trong
     * cùng đối tượng (MAX, [isLoadedAdReady] `false`). `AdsHelper.dropStaleLoadedFlag` dùng nó thay [hasLoadedAd] để
     * mạng đang nạp lại được coi là bận: giữ cờ đã load của placement, không nạp lại thừa (Task J4). Cùng tác dụng phụ
     * bỏ quảng cáo quá tuổi như [hasLoadedAd].
     */
    internal fun holdsLoadedAd(): Boolean = getAdLoaded()?.adObject != null

    public abstract fun show(placement: String, adsShowCb: AdsShowCb?, flagShow: Int = 0): Boolean

    /**
     * Build whatever this placement can build before it is asked to show.
     *
     * Called while a parent fullscreen ad still covers the screen and a chained show is already
     * decided, so expensive view work happens behind that cover instead of in the gap after it
     * closes. No-op for formats the network renders itself - only the ones this app inflates.
     */
    public open fun prepareChainedShow() {}

    /**
     * Throw away anything [prepareChainedShow] built that will not now be shown. A chain can be
     * armed and then blocked (suppression, remove-ads, no fill); without this the pre-built view
     * survives holding its native ad, and the next chain would show it against an ad that may
     * since have been destroyed.
     */
    public open fun cancelChainedShow() {}

    public open fun getAdForShow(placement: String, adsShowCb: AdsShowCb?): Any? {
        return null
    }

    public open fun setTickAdHasShow(ecmp: AdsEcmp?): Boolean {
        return false
    }

    public open fun destroyAd(ad: Any?): Boolean {
        return false
    }

    init {
        mContext = context
        adsType = type
        this.adBase = adBase
        initData(pl)
        this.onInit()
    }

    private fun initData(pl: String) {
        adsPlacementEcmp = AdsPlacementEcmpBase(this)
        adsPlacementEcmp.originPl = pl
    }

    public fun setActivity(ac: Activity) {
        mActivity = ac
    }

    public fun setIdsDf(df: String) {
        adsPlacementEcmp.adEcmps?.setDefaultIds(df)
    }

    public fun parserEcpms(data: String) {
        adsPlacementEcmp.parserEcpms(data)
    }

    public fun adsPlEcmpToStringData(): String {
        return adsPlacementEcmp.dataToString()
    }

    protected fun setLoadPlacement(pl: String) {
        loadPl = pl
        adsPlacementEcmp.getPl = pl
    }

    public fun getLoadPlacement(): String {
        return loadPl
    }

    public fun setShowPlacement(pl: String) {
        showPl = pl
        adsPlacementEcmp.getPl = pl
        recordTurn(pl)
    }

    public fun setGetPlacement(pl: String) {
        getPl = pl
        adsPlacementEcmp.getPl = pl
        recordTurn(pl)
    }

    private fun recordTurn(pl: String) = adsPlacementEcmp.adEcmps?.list?.mapNotNull { it.adObject }
        ?.let { ads -> synchronized(turnPlByAd) { ads.forEach { turnPlByAd[it] = pl } } }

    public fun checkLoadAfterGetCf() {
        if ((System.currentTimeMillis() / 1000 - AppPluginManager.instance.getTimeSesstionStart()) > 8
            && adsPlacementEcmp.originPl == "full_splash"
        ) {
            return
        }

        if (((System.currentTimeMillis() / 1000 - AppPluginManager.instance.getTimeSesstionStart()) > 11
                    && adsPlacementEcmp.originPl == "nt_language") ||
            ((System.currentTimeMillis() / 1000 - AppPluginManager.instance.getTimeSesstionStart()) > 11
                    && adsPlacementEcmp.originPl == "nt_language_2")
        ) {
            return
        }

        if (adsPlacementEcmp.flag4Load && !adsPlacementEcmp.isLoading) {
            LogUtil.logD("ads AdsPlBase$adsType ${adsPlacementEcmp.originPl} checkLoadAfterGetCf")
            adsPlacementEcmp.flag4Load = false
            load(loadPl, adsPlacementEcmp.adsLoadCb)
        }
    }

    public fun onFinishLoad() {
        if (adsPlacementEcmp.isLoading) return
        adsPlacementEcmp.flag4Load = false
        adsPlacementEcmp.applyPendingConfig()
    }

    /**
     * Lượt 1 khi host chết (xem [AdsBase.releaseActivity]): chỉ bỏ Activity. Không dismiss, không
     * callback, và cố ý không `open`, để không lớp con nào gắn việc phát sự kiện vào lượt này.
     * Sau đó [load] rơi vào nhánh `mActivity == null` (log + onLoadFailed), không dựng quảng cáo.
     */
    public fun releaseActivity() {
        mActivity = null
    }

    public open fun clearActivity() {
        mActivity = null
    }
}
