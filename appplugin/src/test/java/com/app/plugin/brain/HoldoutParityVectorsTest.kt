package com.app.plugin.brain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Khoá vector băm FNV-1a của [HoldoutManager.bucketWith] vào hằng số công khai.
 *
 * Job đối soát bucket phía server (doc 57 mục B.2 quyết định 2, backend `reconcile.py`)
 * tái tạo ĐÚNG từng bit của hàm này bằng Python để suy lại bucket từ (salt, user_id).
 * Đổi thuật toán ở đây không chỉ là refactor: nó làm mọi hàng exposure đã thu thập
 * trông như "hỏng" khi server đối soát, và đổi cả chu kỳ đo của mọi thí nghiệm đang chạy.
 * Bộ vector này (đã xác nhận chéo giữa Kotlin và Python) khiến thay đổi đó không thể
 * xảy ra trong im lặng.
 */
class HoldoutParityVectorsTest {
    @Test
    fun `vector bam lien ngon ngu voi backend doi soat`() {
        assertEquals(6924, HoldoutManager.bucketWith("brain_holdout_v1", "user_1"))
        assertEquals(9781, HoldoutManager.bucketWith("brain_holdout_v1", "user_2"))
        assertEquals(5350, HoldoutManager.bucketWith("brain_holdout_v1", "player-abc-123"))
        assertEquals(735, HoldoutManager.bucketWith("brain_holdout_v2", "user_1"))
        assertEquals(7683, HoldoutManager.bucketWith("brain_holdout_v1|rollout", "user_1"))
        assertEquals(7074, HoldoutManager.bucketWith("paywall_variant", "user_1"))
    }
}
