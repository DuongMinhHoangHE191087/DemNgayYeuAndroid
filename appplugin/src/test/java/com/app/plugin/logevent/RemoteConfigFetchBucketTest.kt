package com.app.plugin.logevent

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * D4 (DOC/66): thời gian fetch Remote Config (ms) → một trong 8 event `fir_rm_fet_*`, đúng như chuỗi if/else cũ
 * của `FirHelperConfig`: mỗi mốc gồm cả biên trên (`<=`), dải 6–8 s mang tên `8s`, trên 8 s là `9s`.
 */
class RemoteConfigFetchBucketTest {

    private fun bucket(ms: Long) = EventNames.remoteConfigFetchBucket(ms)

    @Test
    fun `bien 1 s`() {
        assertEquals("fir_rm_fet_1s", bucket(1000))
        assertEquals("fir_rm_fet_2s", bucket(1001))
    }

    @Test
    fun `bien 8 s va tren 8 s`() {
        assertEquals("fir_rm_fet_8s", bucket(8000))
        assertEquals("fir_rm_fet_9s", bucket(8001))
        assertEquals("fir_rm_fet_9s", bucket(20000))
    }

    @Test
    fun `khong co 7s - dai 6 den 8 s la 8s`() {
        assertEquals("fir_rm_fet_6s", bucket(6000))
        assertEquals("fir_rm_fet_8s", bucket(6001))
        assertEquals("fir_rm_fet_8s", bucket(7000))
    }

    @Test
    fun `moi moc giua`() {
        assertEquals("fir_rm_fet_2s", bucket(2000))
        assertEquals("fir_rm_fet_3s", bucket(3000))
        assertEquals("fir_rm_fet_4s", bucket(4000))
        assertEquals("fir_rm_fet_5s", bucket(5000))
        assertEquals("fir_rm_fet_5s", bucket(4001))
    }

    /** Giờ hệ thống bị lùi giữa lúc bắt đầu và lúc xong làm thời gian âm: vẫn vào mốc nhỏ nhất như code cũ. */
    @Test
    fun `thoi gian 0 hay am la 1s`() {
        assertEquals("fir_rm_fet_1s", bucket(0))
        assertEquals("fir_rm_fet_1s", bucket(-5))
    }

    @Test
    fun `ket qua luon la hang trong EventNames`() {
        val consts = setOf(
            EventNames.FIR_RM_FET_1S, EventNames.FIR_RM_FET_2S, EventNames.FIR_RM_FET_3S, EventNames.FIR_RM_FET_4S,
            EventNames.FIR_RM_FET_5S, EventNames.FIR_RM_FET_6S, EventNames.FIR_RM_FET_8S, EventNames.FIR_RM_FET_9S,
        )
        val seen = (-1L..10_000L step 250).map(::bucket).toSet() + bucket(Long.MAX_VALUE)
        assertEquals(consts, seen)
    }
}
