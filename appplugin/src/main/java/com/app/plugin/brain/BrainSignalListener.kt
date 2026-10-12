package com.app.plugin.brain

import com.app.plugin.AppPluginInternalApi
import com.app.plugin.firbase.RcSectionRegistry

/**
 * Seam brain → game (2.0.0 C1d, D1/D16, plan §Hợp đồng C): `AdsBrain` báo hai tín hiệu engagement qua giao diện này thay
 * vì gọi thẳng `CrmBridge`. Luồng: không hứa luồng chính; tín hiệu bị giữ chờ giấy phép (2.2.0 LIC) chạy trên luồng
 * quyết verdict (`appplugin-license` hoặc `-wait`), không phải luồng của người gọi. `GameModule.install()` đặt
 * [installed]; vắng → tín hiệu không đi đâu. Gate consent/config
 * vẫn nằm trong cầu nối CRM phía game (R-P1). 2.1.0 G1: public cho `appplugin-game`, chỉ qua opt-in [AppPluginInternalApi].
 */
@AppPluginInternalApi
public interface BrainSignalListener {
    /** Lượt mua đầu đời của người chơi (gọi một lần, sau khi state đã cập nhật). */
    public fun onFirstPurchase(playerId: String, usd: Double, isHoldout: Boolean)

    /** Loại người chơi có xác suất cao nhất đổi từ [from] sang [to]. */
    public fun onSegmentChange(playerId: String, from: String, to: String, isHoldout: Boolean)

    public companion object {
        /** Listener do `GameModule` cài; `null` khi mã game vắng. */
        @Volatile public var installed: BrainSignalListener? = null

        /** Listener hiện tại; dò `GameModule` (một lần mỗi process) nếu tín hiệu tới trước init RC. */
        internal fun current(): BrainSignalListener? {
            RcSectionRegistry.ensureGameModule()
            return installed
        }
    }
}
