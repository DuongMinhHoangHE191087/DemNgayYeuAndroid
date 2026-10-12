package com.app.plugin.ads

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Cổng G8 (DOC/65 §3): `AdsHelper.kt` ≤ 1.500 dòng ở mức 1.5, ≤ 800 ở 2.0. Trần [MAX_LINES] là bánh cóc: mỗi task
 * tách của plan 1.5.0 hạ trần xuống mục tiêu của task TRƯỚC khi chuyển mã (test đỏ), chuyển xong test xanh. Không bao
 * giờ nâng trần quá số đo hiện tại + 10; không @Ignore.
 */
class AdsHelperSizeGateTest {
    @Test
    fun `AdsHelper_kt khong vuot tran G8 hien hanh`() {
        val lines = File("src/main/java/com/app/plugin/ads/AdsHelper.kt").readLines().size
        assertTrue("AdsHelper.kt = $lines dòng > trần $MAX_LINES (DOC/65 G8; plan 1.5.0 hạ trần theo từng task)", lines <= MAX_LINES)
    }

    companion object {
        const val MAX_LINES = 700
    }
}
