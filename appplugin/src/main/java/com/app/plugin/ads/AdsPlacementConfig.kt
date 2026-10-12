package com.app.plugin.ads

import android.os.Handler
import android.os.Looper
import com.app.plugin.until.LogUtil

public class AdsPlacementConfig {
    public var placement: String = "default"
    public var flagShow: Int = 2
    public var maxShow: Int = 100
    public var delTime: Int = 5
    public var timeShow: Long = 0
    public var countshow: Int = 0
    public var typeAd: Int = 0

    // Watchdog: nếu 1 request load không bao giờ trả callback (network treo, SDK lỗi...),
    // isLoading sẽ kẹt = true mãi và chặn mọi lần load/show sau đó.
    // Sau LOAD_TIMEOUT_MS tự ép isLoading=false + báo fail để luồng có thể thử lại.
    // Sau khi đã báo "load timeout", callback của lượt đó tới muộn (mạng callback sau 90 s, hoặc watchdog của slot hết
    // hạn sau watchdog này khi nhiều mạng cùng treo) không được tới app lần hai (Task I2): watchdog tăng [loadCycle].
    // 8 vòng waterfall của `AdsHelper` chỉ gọi [cbLoad] khi lượt của mình còn hiện hành; vòng banner gọi theo
    // [isLoadCallbackCurrent] — lượt còn hiện hành, hoặc [cbLoad] được gán lại sau `load timeout` (1.5.0 Task 15).
    private val loadTimeoutHandler = Handler(Looper.getMainLooper())
    private val loadTimeoutRunnable = Runnable {
        if (isLoading) {
            LogUtil.logE("ads placement $placement load timeout after $LOAD_TIMEOUT_MS ms")
            val cb = cbLoad
            isLoading = false
            loadCycle++
            cb?.onLoadFailed(placement, placement, "", "load timeout")
        }
    }

    /**
     * Lượt load hiện tại của placement (Task I review C1). Tăng mỗi lần một lượt bắt đầu ([isLoading] bật) và mỗi lần
     * watchdog bỏ lượt. Vòng waterfall của `AdsHelper` chụp giá trị lúc bắt đầu; callback nào thấy giá trị đã đổi là
     * của lượt đã bỏ (vd slot mạng giữ chờ khởi tạo hết hạn sau khi placement đã báo `load timeout` và app đã load
     * lại) và không được chạm [isLoading]/[cbLoad] của lượt mới — cùng cách `BannerLoadGuard` của banner.
     */
    internal var loadCycle: Int = 0
        private set

    public var isLoading: Boolean = false
        set(value) {
            field = value
            loadTimeoutHandler.removeCallbacks(loadTimeoutRunnable)
            if (value) {
                loadCycle++
                loadTimeoutHandler.postDelayed(loadTimeoutRunnable, LOAD_TIMEOUT_MS)
            }
        }
    public var isLoaded: Boolean = false
    public var isShowing: Boolean = false

    /**
     * Callback load của app cho placement. Không tự chặn: vòng waterfall gọi nó chỉ khi lượt đã chụp còn là [loadCycle]
     * hiện tại (1.5.0 Task 15 bỏ getter-null cũ — đọc ra `null` sau `load timeout` — mà trước đó vòng banner dựa vào).
     * Setter ghi [loadCycle] lúc gán (mọi lần gán, kể cả app gán thẳng qua setter public) cho [isLoadCallbackCurrent].
     */
    public var cbLoad:AdsLoadCb? = null
        set(value) {
            field = value
            cbLoadCycle = loadCycle
        }

    /** [loadCycle] lúc [cbLoad] được gán lần gần nhất (1.5.0 Task 15 bước B). */
    private var cbLoadCycle: Int = 0

    /**
     * Vòng banner được gọi [cbLoad] không (1.5.0 Task 15 bước B, tương đương getter-null cũ): lượt [cycle] chụp lúc
     * vòng bắt đầu còn hiện hành, hoặc [cbLoad] được gán sau lần `load timeout` gần nhất (callback mới nhận kết quả muộn
     * của vòng đang chạy). Gán trước `load timeout` thì không: callback đó đã nhận `load timeout`.
     */
    internal fun isLoadCallbackCurrent(cycle: Int): Boolean = cycle == loadCycle || cbLoadCycle == loadCycle
    public var cbShow:AdsShowCb? = null

    public companion object {
        public const val LOAD_TIMEOUT_MS: Long = 90_000L
    }
}