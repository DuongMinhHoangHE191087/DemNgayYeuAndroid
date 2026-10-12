package com.app.plugin.ads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * P12 (1.9.0 Task 4, D6): bảng nước → tier của SDK ([AdsPolicyTiers.COUNTRY_TIER]) và bảng backend
 * `backend/sql/seed/country_tier.csv` là một nguồn: lệch một nước là đỏ. Đọc CSV lúc test từ repo (đường dẫn tương
 * đối thư mục module); thiếu file thì fail to, không bỏ qua.
 */
class CountryTierParityTest {

    private val csv = File("backend/sql/seed/country_tier.csv")

    private fun csvRows(): Map<String, String> {
        assertTrue("thiếu ${csv.absolutePath}: test phải chạy với thư mục module làm cwd", csv.isFile)
        val rows = csv.readLines().map { it.trim() }.filter { it.isNotEmpty() }
        assertEquals("country,tier", rows.first())
        return rows.drop(1).associate { line ->
            val (country, tier) = line.split(',').map { it.trim() }
            country to tier
        }
    }

    @Test fun `P12 bang tier cua SDK bang country_tier csv`() {
        val fromCsv = csvRows()
        assertEquals(37, fromCsv.size)
        assertEquals(fromCsv, AdsPolicyTiers.COUNTRY_TIER)
        fromCsv.forEach { (country, tier) -> assertEquals(country, tier, AdsPolicyTiers.tierOf(country)) }
        assertEquals("OTHER", AdsPolicyTiers.tierOf("ZZ"))
    }
}
