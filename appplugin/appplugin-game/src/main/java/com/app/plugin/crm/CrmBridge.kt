package com.app.plugin.crm

import com.app.plugin.consent.ConsentState
import com.app.plugin.until.LogUtil

/**
 * Cầu nối tín hiệu engagement ra CRM — "Webhook ra Braze/OneSignal" (doc 23 mục E.3,
 * Phase 4).
 *
 * ═══ VÌ SAO LỚP NÀY TỒN TẠI ═══
 *
 * Brain hiểu người chơi (segment, khuynh hướng chi tiêu), còn CRM (Braze, OneSignal,
 * Clevertap...) là kênh nói chuyện với họ. Không có cầu nối, hai hệ thống mù nhau: CRM
 * gửi cùng một chiến dịch cho người sắp rời bỏ và người sắp trả tiền. Lớp này phát các
 * tín hiệu engagement ĐÃ CHUẨN HOÁ từ brain ra ngoài qua adapter do game implement —
 * SDK KHÔNG chứa dependency vendor CRM nào (bài học ironSource, doc 20 A.4: nhúng vendor
 * SDK vào thư viện biến nó thành nô lệ lịch release vendor).
 *
 * ═══ TÍN HIỆU — CHỈ PHÁT THỨ CÓ DỮ LIỆU THẬT ═══
 *
 * - [SignalKind.SEGMENT_CHANGE]: latent player type thay đổi (ví dụ CASUAL_STEADY →
 *   LOW_SKILL_HIGH_CHURN_RISK) — phát khi `DecisionEngine.observe` thực sự làm belief
 *   dịch chuyển.
 * - [SignalKind.FIRST_PURCHASE]: lượt mua đầu đời — thời điểm chuyển đổi quan trọng nhất
 *   với mọi CRM.
 *
 * Cố ý KHÔNG phát "churn risk theo ngày vắng mặt": `daysSinceLastSession` hiện chưa được
 * persist qua phiên nên chưa có dữ liệu thật — phát tín hiệu bịa còn tệ hơn không phát.
 *
 * ═══ GATE CONSENT ═══
 *
 * Tín hiệu engagement là dữ liệu cá nhân hoá (GDPR coi là profiling). Vì vậy chỉ phát khi
 * `ConsentState.allowsPersonalization()`: DENIED chặn, và UNKNOWN cũng chặn (quyết định #7, DOC/66:
 * chưa có câu trả lời thì chưa được cấp). Lớp này không có `Context` để đọc `IABTCF_gdprApplies`,
 * nên dùng bản không tham số: UNKNOWN chặn cả ngoài GDPR, tới câu trả lời đầu của UMP (UMP trả
 * `NOT_REQUIRED` → GRANTED, tầng được lưu qua phiên). `childDirected == true` → chặn luôn kể cả
 * GRANTED: COPPA, và CRM cho trẻ em bị cấm ở hầu hết khu vực pháp lý.
 *
 * ═══ MẶC ĐỊNH TẮT ═══
 *
 * Chưa nạp cấu hình hoặc `enabled=false` → không phát gì. Dữ liệu engagement chảy RA
 * ngoài SDK là hành vi cần bật lên có chủ đích, không được là hệ quả phụ của việc nâng
 * phiên bản — cùng tinh thần `AdsBrain.enabled = false` mặc định.
 *
 * ═══ HOLDOUT ═══
 *
 * Mỗi tín hiệu mang cờ [EngagementSignal.isHoldout]. Nhóm đối chứng tồn tại để đo giá trị
 * của cá nhân hoá; nếu CRM vô tình dội chiến dịch vào họ, phép đo incrementality hỏng.
 * Cờ này để studio LOẠI nhóm đối chứng khỏi chiến dịch CRM — cùng một hook dùng chung
 * cho UA lẫn monetization (doc 23 Phase 4).
 */
public object CrmBridge {

    private const val TAG = "CrmBridge"

    public data class ApplyResult(val applied: Boolean, val report: String)

    /** Loại tín hiệu engagement. Chỉ liệt kê thứ SDK có dữ liệu thật — xem KDoc lớp. */
    public enum class SignalKind { SEGMENT_CHANGE, FIRST_PURCHASE }

    /**
     * Tín hiệu engagement đã chuẩn hoá — cùng hình dạng cho mọi vendor CRM.
     *
     * @param detail nội dung cụ thể của tín hiệu, dạng "key=value" dễ parse:
     *   SEGMENT_CHANGE → "CASUAL_STEADY->LOW_SKILL_HIGH_CHURN_RISK",
     *   FIRST_PURCHASE → "usd=4.99".
     * @param isHoldout người nhận có thuộc nhóm đối chứng không — studio dùng để loại
     *   nhóm này khỏi chiến dịch, giữ phép đo incrementality sạch.
     */
    public data class EngagementSignal(
        val kind: SignalKind,
        val playerId: String,
        val detail: String,
        val isHoldout: Boolean,
        val atMs: Long = System.currentTimeMillis()
    )

    /** Adapter do game implement, bọc MỘT vendor CRM (Braze, OneSignal, Clevertap...). */
    public interface CrmSink {
        public val sinkId: String

        /**
         * Gửi tín hiệu tới vendor. Trả true nếu đã nhận vào hàng đợi gửi. Ngoại lệ từ
         * vendor SDK không được ném ra ngoài — bridge sẽ tự bọc try/catch, nhưng adapter
         * nên tự xử lý lỗi mạng/lỗi SDK của mình.
         */
        public fun deliver(signal: EngagementSignal): Boolean
    }

    public data class BridgeConfig(
        val version: String,
        val enabled: Boolean,
        /** Rỗng = cho phép mọi sink đã đăng ký. */
        val sinks: Set<String>
    )

    @Volatile
    private var config: BridgeConfig? = null

    private val sinks = LinkedHashMap<String, CrmSink>()

    // ═══ Cấu hình ═══

    /**
     * Nạp cấu hình từ Remote Config (khoá `crm_bridge_android`).
     *
     * ```json
     * {
     *   "version": "crm_v1",
     *   "enabled": true,
     *   "sinks": ["braze"]
     * }
     * ```
     *
     * `sinks` vắng mặt hoặc rỗng = cho phép mọi sink đã đăng ký. JSON hỏng → giữ bản
     * đang chạy; thiếu `version` → từ chối.
     */
    public fun apply(json: String?): ApplyResult {
        if (json.isNullOrBlank()) return reject("cấu hình rỗng")
        return try {
            val root = com.google.gson.Gson().fromJson(json, Map::class.java) ?: return reject("JSON null")
            val ver = root["version"] as? String
            if (ver.isNullOrBlank()) return reject("thiếu trường version")
            val enabled = root["enabled"] != false
            val allow = mutableSetOf<String>()
            (root["sinks"] as? List<*>)?.forEach { s ->
                (s as? String)?.takeIf { it.isNotBlank() }?.let { allow.add(it.trim()) }
            }
            config = BridgeConfig(ver, enabled, allow)
            ApplyResult(
                true,
                "áp $ver: ${if (enabled) "bật" else "TẮT"}, " +
                    if (allow.isEmpty()) "cho phép mọi sink đã đăng ký" else "chỉ [${allow.joinToString(", ")}]"
            )
        } catch (e: Exception) {
            reject("JSON hỏng: ${e.message}")
        }
    }

    private fun reject(reason: String): ApplyResult {
        val cur = config
        return ApplyResult(false, "từ chối ($reason) — giữ nguyên bản ${cur?.version ?: "chưa nạp"}")
    }

    public fun activeVersion(): String = config?.version ?: "chưa nạp"

    // ═══ Đăng ký sink ═══

    /** Đăng ký (hoặc THAY — cùng `sinkId` thì adapter mới thế chỗ cũ). Id rỗng bị từ chối. */
    @Synchronized
    public fun register(sink: CrmSink): Boolean {
        if (sink.sinkId.isBlank()) return false
        sinks[sink.sinkId.trim()] = sink
        return true
    }

    @Synchronized
    public fun unregister(sinkId: String) {
        sinks.remove(sinkId.trim())
    }

    // ═══ Phát tín hiệu ═══

    /**
     * Phát một tín hiệu qua mọi sink được phép. Đây là API công khai để game có thể phát
     * tín hiệu từ nguồn riêng của mình qua CÙNG một cổng gate consent — nhưng hai tín
     * hiệu chuẩn ([SignalKind]) do SDK tự phát.
     *
     * @return mô tả kết quả để chẩn đoán: lý do bị chặn, hoặc số sink đã giao. Im lặng
     *   không phát là cách nhanh nhất để studio tưởng cầu nối hỏng.
     */
    @Synchronized
    public fun emit(signal: EngagementSignal): String {
        val cfg = config ?: return "chưa nạp cấu hình — không phát tín hiệu"
        if (!cfg.enabled) return "cầu nối đang tắt — không phát tín hiệu"
        if (ConsentState.childDirected == true)
            return "app hướng tới trẻ em — tín hiệu CRM bị chặn (COPPA)"
        // Không có Context để đọc IABTCF_gdprApplies: bản không tham số, UNKNOWN là chưa được cấp (#7).
        if (!ConsentState.allowsPersonalization())
            return "consent chưa cho phép (tầng ${ConsentState.tier()}) — không phát tín hiệu CRM"

        val candidates = sinks.values.filter { cfg.sinks.isEmpty() || it.sinkId in cfg.sinks }
        if (candidates.isEmpty()) return "không có sink nào được phép"

        var delivered = 0
        for (sink in candidates) {
            try {
                if (sink.deliver(signal)) delivered++
            } catch (e: Exception) {
                // Adapter vendor không được làm hỏng luồng của SDK.
                LogUtil.logE("$TAG: sink ${sink.sinkId} ném lỗi: ${e.message}")
            }
        }
        return "đã phát ${signal.kind} tới $delivered/${candidates.size} sink"
    }

    /** SDK gọi khi segment dịch chuyển. Game thường không cần gọi trực tiếp. */
    public fun emitSegmentChange(playerId: String, from: String, to: String, isHoldout: Boolean) {
        emit(EngagementSignal(SignalKind.SEGMENT_CHANGE, playerId, "$from->$to", isHoldout))
    }

    /** SDK gọi ở lượt mua đầu đời của người chơi. Game thường không cần gọi trực tiếp. */
    public fun emitFirstPurchase(playerId: String, usd: Double, isHoldout: Boolean) {
        emit(EngagementSignal(SignalKind.FIRST_PURCHASE, playerId, "usd=$usd", isHoldout))
    }

    // ═══ Reset ═══

    @Synchronized
    public fun clear() {
        config = null
        sinks.clear()
    }
}
