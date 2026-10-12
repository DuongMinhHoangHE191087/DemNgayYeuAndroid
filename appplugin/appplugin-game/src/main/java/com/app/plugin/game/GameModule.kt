package com.app.plugin.game

import com.app.plugin.brain.BrainSignalListener
import com.app.plugin.crm.CrmBridge
import com.app.plugin.firbase.RcSectionRegistry
import com.app.plugin.iap.BattlePass
import com.app.plugin.iap.PurchaseRouter
import com.app.plugin.iap.RecurringGrants
import com.app.plugin.offerwall.OfferwallManager

/**
 * Điểm vào mã game (2.0.0 C1d, D16): core dò lớp này bằng tên cố định `RcSectionRegistry.GAME_MODULE_CLASS` (keep rule
 * trong `appplugin-game/consumer-rules.pro`) đúng một lần rồi gọi [install]. Ở `appplugin-game` từ 2.1.0 G1; gọi core qua SPI.
 * [install] chỉ đăng ký hàm: 0 I/O, 0 gửi (R-P1). Mỗi khoá RC riêng vì nhịp đổi khác nhau; cấu hình hỏng → giữ bản đang chạy.
 */
internal object GameModule {
    fun install() {
        // Thứ tự đăng ký = thứ tự áp và thứ tự 5 dòng `… (RemoteConfig):`, như 5 lời gọi trực tiếp cũ trong FirHelperConfig.
        // Routing mua hàng đa kênh (doc 23 mục C.2): sản phẩm nào đi Play Billing, sản phẩm nào ra web shop.
        RcSectionRegistry.register("purchase_routing_android", "PurchaseRouter") { PurchaseRouter.apply(it).report }
        // Battle pass / season pass (doc 23 mục C.3): nhịp mùa.
        RcSectionRegistry.register("battle_pass_android", "BattlePass") { BattlePass.apply(it).report }
        // Cầu nối VIP ↔ virtual currency (doc 19): nhịp gói bán.
        RcSectionRegistry.register("recurring_grants_android", "RecurringGrants") { RecurringGrants.apply(it).report }
        // Offerwall (doc 20, doc 23 mục C.4): bật/tắt tường + provider được phép; nhịp vendor.
        RcSectionRegistry.register("offerwall_config_android", "OfferwallManager") { OfferwallManager.apply(it).report }
        // Cầu nối engagement ra CRM (doc 23 mục E.3): chưa nạp hoặc hỏng → TẮT.
        RcSectionRegistry.register("crm_bridge_android", "CrmBridge") { CrmBridge.apply(it).report }
        BrainSignalListener.installed = CrmSignals
    }

    /** Hai tín hiệu brain → `CrmBridge`, đúng như lời gọi trực tiếp cũ trong `AdsBrain` (gate consent/config trong bridge). */
    private object CrmSignals : BrainSignalListener {
        override fun onFirstPurchase(playerId: String, usd: Double, isHoldout: Boolean) =
            CrmBridge.emitFirstPurchase(playerId, usd, isHoldout)

        override fun onSegmentChange(playerId: String, from: String, to: String, isHoldout: Boolean) =
            CrmBridge.emitSegmentChange(playerId, from, to, isHoldout)
    }
}
