package com.app.plugin.ads

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Trao thưởng của quảng cáo rewarded tối đa một lần cho mỗi lần show.
 *
 * Tạo mới ở mỗi lần show, thay cho cờ `AdsEcmp.flagShow` dùng chung: cờ đó nằm trên đối tượng
 * waterfall được tái sử dụng giữa các lần show, nên chỉ cần một nhánh quên xoá là lần show sau
 * được thưởng mà người dùng không xem hết. Lấy từ Speaker Clean.
 */
internal class RewardDeliveryGate {
    private val earned = AtomicBoolean(false)
    private val delivered = AtomicBoolean(false)

    fun markEarned() {
        earned.set(true)
    }

    /** `true` đúng một lần, và chỉ sau [markEarned]. */
    fun takeReward(): Boolean = earned.get() && delivered.compareAndSet(false, true)
}
