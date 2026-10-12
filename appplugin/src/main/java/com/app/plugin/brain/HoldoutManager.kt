package com.app.plugin.brain

/**
 * Holdout (nhóm đối chứng) — đo xem TOÀN BỘ brain có thực sự tạo ra giá trị hay không.
 *
 * ═══ VÌ SAO PHẢI CÓ TỪ NGÀY ĐẦU ═══
 *
 * Không có holdout thì không có cách nào trả lời câu hỏi quan trọng nhất: *"personalization
 * có tốt hơn không làm gì không?"* Mọi con số ARPDAU tăng đều có thể do mùa vụ, do bản cập
 * nhật game, do chiến dịch UA — chứ không phải do brain.
 *
 * Đây là thứ **không thể bổ sung hồi tố**: muốn so sánh giai đoạn trước/sau khi bật brain thì
 * phải chấp nhận mọi yếu tố khác cũng đã thay đổi. Chỉ nhóm đối chứng chạy SONG SONG mới cho
 * so sánh sạch. Doc 32 gọi đây là món rẻ nhất khi làm sớm và đắt nhất khi làm muộn.
 *
 * Doc 19 còn chỉ ra: **không vendor paywall lớn nào (RevenueCat, Adapty, Superwall, Apphud,
 * Qonversion) có holdout native.** Đây là khoảng trống cạnh tranh, không chỉ là vệ sinh kỹ thuật.
 *
 * ═══ THIẾT KẾ ═══
 *
 * Phân nhóm **tất định theo hash** chứ không random: cùng một `playerId` luôn rơi vào cùng
 * nhóm qua mọi phiên, mọi lần cài lại, không cần lưu trữ gì. Random-per-call sẽ khiến một
 * player lúc thì treatment lúc thì control — làm hỏng hoàn toàn phép đo.
 */
internal object HoldoutManager {

    private const val BUCKET_COUNT = 10_000

    /**
     * Tỉ lệ giữ lại làm đối chứng. 0.05 = 5%.
     *
     * Đánh đổi: holdout lớn thì đo chính xác hơn nhưng "bỏ lỡ" giá trị trên nhiều user hơn.
     * 1–5% là khoảng thông dụng (doc 32). Đặt 0.0 để tắt hoàn toàn.
     */
    public var holdoutFraction: Double = 0.05

    /**
     * Muối băm. Đổi giá trị này = phân nhóm lại toàn bộ dân số.
     *
     * CẢNH BÁO: chỉ đổi khi bắt đầu một chu kỳ đo mới. Đổi giữa chừng làm mất tính so sánh
     * của dữ liệu đang thu, vì một số player sẽ nhảy nhóm.
     */
    public var salt: String = "brain_holdout_v1"

    /**
     * Bucket ổn định trong [0, 10000). Dùng chung cho cả holdout lẫn staged rollout.
     *
     * Dùng FNV-1a 32-bit thay vì `String.hashCode()`: hashCode của JVM không được đảm bảo
     * ổn định giữa các phiên bản/nền tảng, mà phân nhóm thì phải giống nhau ở client và
     * server khi đối soát log.
     */
    public fun bucket(playerId: String): Int = bucketWith(salt, playerId)

    /**
     * Bucket theo một không gian băm riêng. Hai `namespace` khác nhau cho ra hai phân nhóm
     * **độc lập thống kê** trên cùng tập player.
     *
     * Đây là cách duy nhất đúng để chạy nhiều cơ chế phân nhóm song song. Cách sai (từng
     * dùng ở bản đầu và bị unit test bắt được): lấy cùng một bucket rồi dịch pha
     * `(bucket + N) % 10000`. Dịch pha chỉ là một hàm tất định của chính bucket đó, nên hai
     * nhóm vẫn tương quan hoàn toàn — với holdout 10% và rollout 50%, mọi player holdout
     * đều rơi ra ngoài rollout, khiến không thể chạy đồng thời hai thí nghiệm.
     */
    public fun bucketWith(namespace: String, playerId: String): Int {
        var hash = -0x7ee3623b // FNV offset basis 2166136261 dưới dạng Int có dấu
        val input = "$namespace|$playerId"
        for (ch in input) {
            hash = hash xor ch.code
            hash *= 0x01000193 // FNV prime 16777619
        }
        // Bỏ dấu rồi lấy dư; tránh Math.abs(Int.MIN_VALUE) vẫn âm.
        return ((hash.toLong() and 0xFFFFFFFFL) % BUCKET_COUNT).toInt()
    }

    /** Player này có thuộc nhóm đối chứng không? */
    public fun isHoldout(playerId: String): Boolean {
        if (holdoutFraction <= 0.0) return false
        if (holdoutFraction >= 1.0) return true
        return bucket(playerId) < (holdoutFraction * BUCKET_COUNT).toInt()
    }

    /**
     * Hỗ trợ staged rollout: chỉ bật brain cho `fraction` dân số.
     *
     * Dùng **không gian băm riêng** (`salt` + hậu tố) chứ không phải dịch pha từ bucket
     * holdout — xem giải thích ở `bucketWith`. Nhờ vậy holdout và rollout độc lập, có thể
     * chạy đồng thời: trong nhóm holdout vẫn có đúng `fraction` tỉ lệ nằm trong rollout.
     */
    public fun isInRollout(playerId: String, fraction: Double): Boolean {
        if (fraction >= 1.0) return true
        if (fraction <= 0.0) return false
        return bucketWith("$salt|rollout", playerId) < (fraction * BUCKET_COUNT).toInt()
    }

    /**
     * Bản ghi phân nhóm của MỘT player tại thời điểm hiện tại — đủ dữ liệu để đối soát
     * membership trong log mà không phụ thuộc trạng thái máy nào khác.
     *
     * `saltVersion` đi kèm vì đổi muối = phân nhóm lại toàn bộ dân số: hàng exposure thiếu
     * salt thì không biết thuộc chu kỳ đo nào, và join với dữ liệu chu kỳ khác sẽ cho ra
     * kết quả tăng trưởng giả.
     */
    public data class ExposureRecord(
        val experimentId: String,
        val bucket: Int,
        val isHoldout: Boolean,
        val inRollout: Boolean,
        val saltVersion: String
    )

    /**
     * Chụp toàn bộ trạng thái phân nhóm của một player — đầu vào của event
     * `experiment_exposure` (doc 23 Phase 4 mục 3).
     *
     * Gọi hàm này KHÔNG làm thay đổi trạng thái nào: phân nhóm là hàm tất định của
     * (salt, playerId), chụp lại chỉ là ghi nhận.
     */
    public fun exposureRecord(
        playerId: String,
        rolloutFraction: Double,
        experimentId: String = "monetization_brain"
    ): ExposureRecord = ExposureRecord(
        experimentId = experimentId,
        bucket = bucket(playerId),
        isHoldout = isHoldout(playerId),
        inRollout = isInRollout(playerId, rolloutFraction),
        saltVersion = salt
    )
}
