package com.app.plugin.ads

import android.os.Handler
import android.os.Looper
import com.app.plugin.logevent.AdEventLog
import com.app.plugin.until.LogUtil

public open class AdsPlacementEcmpBase(adPl: AdsPlBase) {
    public var adPl:AdsPlBase
    public var originPl: String = ""
    public var getPl: String = ""

    /**
     * Ngắn hơn watchdog của placement ([AdsPlacementConfig.LOAD_TIMEOUT_MS], 90 s) để fail của một mạng treo tới
     * helper khi lượt của placement còn chạy và waterfall còn đi tiếp sang mạng sau. Mạng thật tự callback theo
     * timeout request của nó trước mốc này; watchdog chỉ bắt mạng không bao giờ callback (vd lượt giữ chờ khởi tạo).
     */
    private val slotLoadTimeoutMs = 60_000L
    private val loadWatchdog: Handler by lazy { Handler(Looper.getMainLooper()) }
    private val loadTimeoutRunnable = Runnable { onLoadTimeout() }

    /**
     * Slot đang có một request tới mạng (hoặc đang giữ chờ mạng khởi tạo, [AdsPlBase.holdUntilReady]).
     *
     * Watchdog cấp slot (Task I2, cùng mẫu với [AdsPlacementConfig.isLoading]): mỗi lần bật, hẹn
     * [slotLoadTimeoutMs]; mọi lần đặt lại (callback load/fail của mạng hạ cờ, hoặc request kế bật lại) huỷ hẹn cũ.
     * Mạng không bao giờ callback thì hết hạn [onLoadTimeout] báo fail một lần; trước đây slot kẹt "đang load" mãi,
     * `dropStaleLoadedFlag` giữ cờ đã load của placement và placement không nạp lại.
     */
    public var isLoading: Boolean = false
        set(value) {
            field = value
            loadWatchdog.removeCallbacks(loadTimeoutRunnable)
            if (value) loadWatchdog.postDelayed(loadTimeoutRunnable, slotLoadTimeoutMs)
        }
    public var isloaded: Boolean = false
    public var isShowing: Boolean = false
    public var countLoad: Int = 0
    public var tLoad: Long = 0
    public var flag4Load: Boolean = false
    public var isHasLoad: Boolean = false

    public var adEcmps: AdsEcmps? = null
    /**
     * Callback của lượt load đang chạy. [onLoadTimeout] đặt về `null` ngay lúc báo fail cho lượt đó (1.5.0 Task 15,
     * thay getter-null cũ), tới khi lượt kế gán callback của nó ([AdsPlBase.load]): callback tới muộn của lượt đã bỏ
     * không tới ai (không `onLoaded`/`onLoadFailed` thứ hai).
     *
     * Task I review Minor 3, Task J1: response đầu tiên của mọi listener không tới đây với lượt đã bỏ (23 lớp mạng có
     * generation/idle guard, [AdsPlBase.isStaleLoadCallback] trả về sớm trước khi gọi `adsLoadCb`). Lớp chặn này cho
     * đường auto-refresh của banner/rect (nhánh "refresh"/"not turn", đa callback theo thiết kế) cố ý không qua guard
     * đó: view đang hiện refresh trong cửa sổ từ [onLoadTimeout] tới request kế thì không tới callback của lượt đã báo
     * `load timeout`.
     */
    public var adsLoadCb: AdsLoadInnerCb? = null
    public var adsShowCb: AdsShowCb? = null


    init {
        this.adPl = adPl
        initData()
    }

    /**
     * Mã lượt load của slot: tăng mỗi lần watchdog bỏ một lượt và mỗi lần [AdsPlBase.load] bắt đầu lượt mới. Lượt giữ
     * chờ mạng khởi tạo đã hẹn trước đó thì không chạy nữa ([AdsPlBase.unlessLoadAbandoned]); listener của mạng chụp
     * giá trị lúc gửi request và bỏ callback nếu đã đổi ([AdsPlBase.isStaleLoadCallback], Task I review I-2).
     */
    internal var loadGeneration: Int = 0
        internal set

    /**
     * Hết hạn khi slot vẫn "đang load": hạ cờ (slot và mục waterfall), dồn chỉ số waterfall tới cuối để fail muộn của
     * request đó không gửi request sang id sau, báo `onLoadFailed(..., "load timeout")` đúng một lần qua callback hiện
     * có (waterfall của helper đi tiếp); callback muộn bị chặn vì [adsLoadCb] đã về `null` trước khi báo. Quảng cáo
     * tới muộn vẫn được mạng lưu vào mục của nó như mọi lần load (còn dùng được, tính tuổi từ lúc tới): lần load sau trả
     * nó ngay, không request mới.
     */
    private fun onLoadTimeout() {
        if (!isLoading) return
        LogUtil.logE("ads ${adPl.logTag} $getPl $originPl load timeout after $slotLoadTimeoutMs ms")
        val cb = adsLoadCb
        val openIds = adEcmps?.list?.filter { it.isLoading }?.map { it.adsId }.orEmpty()
        loadGeneration++
        isLoading = false
        isloaded = false
        AdEventLog.slotLoadTimeout(originPl, openIds) // 1.7.0 T1: `ad_no_fill(error_code=timeout)` cho request đang mở
        adEcmps?.let { ecmps ->
            ecmps.list.forEach { it.isLoading = false }
            ecmps.idxCurrEcpm = ecmps.list.size
        }
        adsLoadCb = null // lượt đã bỏ: refresh/callback muộn không tới cb này; lượt kế ([AdsPlBase.load]) gán cb mới
        cb?.onLoadFailed(getPl, originPl, "", "load timeout") // trùng nhãn watchdog của placement
        adPl.onFinishLoad()
    }

    private fun initData() {
        adEcmps = AdsEcmps(adPl, this)
    }

    private var pendingConfig: String? = null

    public fun parserEcpms(data: String) {
        if (data.length <= 5 || data.split(";").none { it.isNotBlank() }) return
        // Do not mutate the waterfall index under an outstanding network callback.
        if (isLoading) {
            pendingConfig = data
            return
        }
        adEcmps?.listFromData(data)
        flag4Load = false
    }

    public fun applyPendingConfig() {
        val data = pendingConfig ?: return
        pendingConfig = null
        adEcmps?.listFromData(data)
        flag4Load = false
    }

    public fun dataToString(): String {
        // NEW-02 FIX: adEcmps không null nhưng dùng ?: "" để an toàn
        return "$originPl,${adEcmps?.listIdsToString() ?: ""}"
    }
}