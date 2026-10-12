package com.app.plugin.ads.core

import android.os.Handler
import android.os.Looper
import com.app.plugin.ads.AdsLoadCb
import com.app.plugin.ads.AdsPlacementConfig
import com.app.plugin.until.LogUtil
import java.util.concurrent.atomic.AtomicReference

/**
 * Gọi tới mọi callback của cùng một lượt load, mỗi cái đúng một lần. O7 (a) (1.6.0 Task 5): lời gọi load thứ hai lúc
 * placement đang load gắn callback vào lượt đang chạy thay vì im lặng. Khử trùng chỉ ở [LoadCallbacks.holds] (theo `===`):
 * hai object khác nhau — kể cả `equals` nhau — là hai request (review Task 5 I1). Một target ném không chặn target khác;
 * giao đủ rồi ném lại lỗi đầu tiên (lỗi sau gắn `addSuppressed`), như khi chỉ có một callback (review Task 5 I3).
 */
internal class FanOutLoadCb(targets: List<AdsLoadCb>) : AdsLoadCb {
    val targets: List<AdsLoadCb> = targets.toList()

    override fun onLoaded(placement: String, originPl: String, adId: String) =
        deliver { it.onLoaded(placement, originPl, adId) }

    override fun onLoadFailed(placement: String, originPl: String, adId: String, error: String) =
        deliver { it.onLoadFailed(placement, originPl, adId, error) }

    private inline fun deliver(call: (AdsLoadCb) -> Unit) {
        var first: Throwable? = null
        for (t in targets) {
            try {
                call(t)
            } catch (e: Throwable) {
                val f = first
                if (f == null) first = e else f.addSuppressed(e)
            }
        }
        first?.let { throw it }
    }
}

/**
 * Callback của lượt đang chạy lúc host bị huỷ (review Task 4 I1): chuyển đúng một callback kết thúc rồi bỏ tham chiếu tới
 * callback của app (thường giữ Activity đã huỷ); lời gọi sau đó (onLoaded muộn của banner, GMA refresh rect) bị bỏ như
 * callback rảnh của Task 4. `getAndSet(null)`: một lần giao kể cả khi hai luồng gọi cùng lúc (review Task 5 M2).
 */
internal class OneShotLoadCb(inner: AdsLoadCb) : AdsLoadCb {
    private val inner = AtomicReference<AdsLoadCb?>(inner)

    fun holds(cb: AdsLoadCb): Boolean = inner.get()?.let { LoadCallbacks.holds(it, cb) } ?: false

    override fun onLoaded(placement: String, originPl: String, adId: String) {
        inner.getAndSet(null)?.onLoaded(placement, originPl, adId)
    }

    override fun onLoadFailed(placement: String, originPl: String, adId: String, error: String) {
        inner.getAndSet(null)?.onLoadFailed(placement, originPl, adId, error)
    }
}

/**
 * Gắn callback vào lượt đang chạy (O7 (a)). Nằm trong `internal object`: hàm top-level `internal` không mangle, sinh khối
 * `LoadCallbacksKt` → facade FAIL (review Task 0 I2).
 */
internal object LoadCallbacks {
    /**
     * Gắn [cb] vào lượt đang chạy của [plcf]: rỗng → gán; đã có (kể cả trong wrapper) → không đổi; khác → fan-out phẳng.
     * Setter của `cbLoad` ghi lượt (1.5.0 Task 15) nên `isLoadCallbackCurrent` vẫn đúng.
     */
    fun attachLoadCallback(plcf: AdsPlacementConfig, cb: AdsLoadCb?) {
        if (cb == null) return
        val cur = plcf.cbLoad
        plcf.cbLoad = when {
            cur == null -> cb
            holds(cur, cb) -> return
            cur is FanOutLoadCb -> FanOutLoadCb(cur.targets + cb)
            else -> FanOutLoadCb(listOf(cur, cb))
        }
    }

    /** [cur] có chuyển tới [cb] không — nhìn xuyên fan-out và wrapper một lần (`OneShotLoadCb` đã chuyển thì không). */
    fun holds(cur: AdsLoadCb?, cb: AdsLoadCb): Boolean =
        cur === cb || (cur is OneShotLoadCb && cur.holds(cb)) || (cur is FanOutLoadCb && cur.targets.any { holds(it, cb) })
}

/**
 * Báo `cf not` đồng bộ đúng một lần; lời gọi lồng (app load lại ngay trong `onLoadFailed` của cùng placement) được post
 * sang vòng kế để không đệ quy đồng bộ. Lời gọi đã post đi lại qua [fail]: lời gọi lồng trong nó lại được post, nên vòng
 * lặp của app thành một callback mỗi vòng looper.
 */
internal class CfNotDispatcher(private val tag: String) {
    private val dispatching = java.util.Collections.synchronizedSet(HashSet<String>())
    private val main = Handler(Looper.getMainLooper())

    fun fail(placement: String, realPl: String, cb: AdsLoadCb?) {
        if (cb == null) return
        if (!dispatching.add(realPl)) {
            LogUtil.logD("$tag $realPl cf not re-entrant: callback posted")
            main.post { fail(placement, realPl, cb) }
            return
        }
        try {
            cb.onLoadFailed(placement, realPl, "", "cf not")
        } finally {
            dispatching.remove(realPl)
        }
    }
}
