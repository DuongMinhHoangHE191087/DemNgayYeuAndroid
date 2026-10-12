package com.app.plugin.brain

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Bản ghi phân nhóm thí nghiệm `experiment_exposure` (doc 23 Phase 4 mục 3) — mẫu số chung
 * của mọi phép đo incrementality: mỗi phiên một hàng cho MỌI player, kể cả nhóm đối chứng
 * và người ngoài rollout.
 *
 * ═══ NGUYÊN TẮC KIỂM TRA ═══
 *
 * 1. Phân nhóm tất định: cùng (salt, playerId) luôn cho cùng một bản ghi — qua mọi lần gọi.
 * 2. Holdout và rollout ĐỘC LẬP thống kê — bài toán dịch pha từng bị unit test bắt (xem
 *    `bucketWith` trong HoldoutManager): trong nhóm holdout vẫn phải có đúng tỉ lệ rollout.
 * 3. `saltVersion` đi kèm bản ghi: đổi muối là đổi chu kỳ đo, thiếu nó thì join nhầm chu kỳ.
 * 4. Phát qua đường onSessionStart an toàn kể cả khi LogEventServer chưa khởi tạo (drop).
 */
class ExperimentExposureTest {

    @Before
    fun setUp() {
        HoldoutManager.holdoutFraction = 0.05
        HoldoutManager.salt = "brain_holdout_v1"
        AdsBrain.enabled = false
        AdsBrain.rolloutFraction = 1.0
    }

    @After
    fun tearDown() {
        HoldoutManager.holdoutFraction = 0.05
        HoldoutManager.salt = "brain_holdout_v1"
        AdsBrain.enabled = false
        AdsBrain.rolloutFraction = 1.0
    }

    // ═══ Tất định ═══

    @Test
    fun `cung player cho cung ban ghi qua moi lan goi`() {
        val a = HoldoutManager.exposureRecord("p_123", rolloutFraction = 0.5)
        val b = HoldoutManager.exposureRecord("p_123", rolloutFraction = 0.5)
        assertEquals(a, b)
        assertTrue("bucket phai nam trong khong gian bam", a.bucket in 0 until 10_000)
    }

    @Test
    fun `ban ghi khop voi trang thai that cua HoldoutManager`() {
        HoldoutManager.holdoutFraction = 0.5
        repeat(50) {
            val id = "p_kiemtra_$it"
            val rec = HoldoutManager.exposureRecord(id, rolloutFraction = 0.3)
            assertEquals(HoldoutManager.isHoldout(id), rec.isHoldout)
            assertEquals(HoldoutManager.isInRollout(id, 0.3), rec.inRollout)
            assertEquals(HoldoutManager.bucket(id), rec.bucket)
        }
    }

    @Test
    fun `holdout fraction 0 va 1 cho ket qua tuyen doi`() {
        HoldoutManager.holdoutFraction = 0.0
        assertFalse(HoldoutManager.exposureRecord("bat_ky_ai", 1.0).isHoldout)
        HoldoutManager.holdoutFraction = 1.0
        assertTrue(HoldoutManager.exposureRecord("bat_ky_ai", 1.0).isHoldout)
    }

    // ═══ Độc lập holdout ↔ rollout (bẫy dịch pha) ═══

    @Test
    fun `trong nhom holdout van co dung ti le nam trong rollout`() {
        // Nếu rollout là hàm dịch pha của bucket holdout (lỗi bản đầu), nhóm holdout sẽ
        // tương quan hoàn toàn với rollout — không thể chạy hai thí nghiệm song song.
        HoldoutManager.holdoutFraction = 0.1
        val holdoutIds = (0 until 5000).map { "p_doclap_$it" }
            .filter { HoldoutManager.isHoldout(it) }
        assertTrue("mau phai du lon de ket luan", holdoutIds.size > 300)
        val inRollout = holdoutIds.count {
            HoldoutManager.exposureRecord(it, rolloutFraction = 0.5).inRollout
        }
        val ratio = inRollout.toDouble() / holdoutIds.size
        assertTrue("ti le trong rollout cua nhom holdout phai ~50%, thuc te $ratio",
            ratio > 0.35 && ratio < 0.65)
    }

    @Test
    fun `rollout 0 va 1 cho ket qua tuyen doi`() {
        val rec0 = HoldoutManager.exposureRecord("p_roll", rolloutFraction = 0.0)
        val rec1 = HoldoutManager.exposureRecord("p_roll", rolloutFraction = 1.0)
        assertFalse(rec0.inRollout)
        assertTrue(rec1.inRollout)
    }

    // ═══ Salt — chu kỳ đo ═══

    @Test
    fun `saltVersion di kem ban ghi va doi muoi doi phan nhom`() {
        val before = HoldoutManager.exposureRecord("p_muoi", 1.0)
        assertEquals("brain_holdout_v1", before.saltVersion)
        HoldoutManager.salt = "brain_holdout_v2"
        val after = HoldoutManager.exposureRecord("p_muoi", 1.0)
        assertEquals("brain_holdout_v2", after.saltVersion)
        // Không bắt buộc bucket đổi cho MỌI player, nhưng với một id cụ thể hash khác muối
        // gần như chắc chắn cho bucket khác — kiểm tra để phát hiện salt không được dùng.
        assertTrue("doi muoi phai phan nhom lai", before.bucket != after.bucket)
    }

    // ═══ Định danh thí nghiệm ═══

    @Test
    fun `experimentId mac dinh la monetization_brain va cho phep dat ten khac`() {
        assertEquals("monetization_brain",
            HoldoutManager.exposureRecord("p_exp", 1.0).experimentId)
        assertEquals("paywall_pricing",
            HoldoutManager.exposureRecord("p_exp", 1.0, experimentId = "paywall_pricing").experimentId)
    }

    // ═══ Wiring qua onSessionStart ═══

    @Test
    fun `onSessionStart phat exposure an toan ke ca khi LogEventServer chua khoi tao`() {
        // LogEventServer chưa init trong unit test — event bị drop chứ KHÔNG được ném lỗi
        // vào luồng game. Test này xanh nghĩa là wiring phát exposure không phá vỡ khởi
        // động phiên trên máy dev chưa nối backend.
        AdsBrain.enabled = true
        AdsBrain.rolloutFraction = 0.5
        AdsBrain.onSessionStart("p_exposure_${System.nanoTime()}")
    }

    @Test
    fun `brain tat thi khong co thi nghiem nao dang chay de ghi exposure`() {
        // Không assert được mạng trong unit test, nhưng logic gate phải đúng hướng:
        // exposure chỉ phát khi enabled — brain tắt thì không có thí nghiệm, ghi membership
        // lúc đó chỉ gây nhiễu dữ liệu. (Call site trong onSessionStart gate bằng `enabled`.)
        AdsBrain.enabled = false
        AdsBrain.onSessionStart("p_tat_${System.nanoTime()}")
    }
}
