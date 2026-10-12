package com.app.plugin.until

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Điều 8.4. SDK đọc resource của app bằng `AppUtil.getResource*` (getIdentifier) nên shrinkResources
 * không thấy tham chiếu. Đo 2026-09-24: 8 ad unit → 0 khi bật shrinkResources mà không có keep.xml.
 * Test này quét đúng các lời gọi đó (kể cả tên động trong `ResIdDefault`) và bắt keep.xml phủ hết.
 */
class KeepXmlTest {
    private val keepFile = File("src/main/res/raw/appplugin_keep.xml")

    private fun patterns(): List<Regex> {
        val keep = Regex("tools:keep=\"([^\"]+)\"").find(keepFile.readText())
            ?: error("appplugin_keep.xml không có tools:keep")
        return keep.groupValues[1].split(',')
            .map { it.trim().removePrefix("@") }
            .map { Regex("^" + it.replace(".", "\\.").replace("*", ".*") + "$") }
    }

    private val literalCall =
        Regex("\\bgetResource(String|Int|Ids)\\(\\s*[^,\\n]+,\\s*\"([A-Za-z0-9_]+)\"")
    private val arrayCall = Regex(   // 1.9.0 T1: string-array đọc bằng getIdentifier("<tên>", "array", …)
        "\\bgetIdentifier\\(\\s*\"([A-Za-z0-9_]+)\"\\s*,\\s*\"array\"")

    private fun namesReadByCode(): Set<String> {
        val names = mutableSetOf<String>()
        File("src/main/java").walkTopDown().filter { it.extension == "kt" }.forEach { f ->
            literalCall.findAll(f.readText()).forEach { m ->
                val type = if (m.groupValues[1] == "Int") "integer" else "string"
                names += "$type/${m.groupValues[2]}"
            }
            arrayCall.findAll(f.readText()).forEach { names += "array/${it.groupValues[1]}" }
        }
        // Tên động: AdsBase.loadIdsDefault() đọc getKeyForResDf(), mọi giá trị nằm trong ResIdDefault.
        Regex("\"([A-Za-z0-9_]+)\"").findAll(File("src/main/java/com/app/plugin/ads/ResIdDefault.kt").readText())
            .forEach { names += "string/${it.groupValues[1]}" }
        return names
    }

    @Test
    fun `keep xml ton tai va co tools keep`() {
        assertTrue("Không thấy " + keepFile.path, keepFile.exists())
        val ps = patterns()
        assertTrue("tools:keep chỉ có ${ps.size} mẫu, cần >= 10: $ps", ps.size >= 10)
    }

    @Test
    fun `moi resource SDK doc bang ten deu duoc keep`() {
        val ps = patterns()
        val missing = namesReadByCode().filter { n -> ps.none { it.matches(n) } }.sorted()
        assertTrue("Thiếu trong appplugin_keep.xml:\n" + missing.joinToString("\n"), missing.isEmpty())
    }

    @Test
    fun `scanner quet duoc cac ten da biet`() {
        val scanned = namesReadByCode()
        val expected = listOf(
            "string/flagTest",
            "string/af_app_id",
            "string/admax_devKey",
            "integer/ads_cf_open_type",
            "string/admob_banner",
            "string/adfb_banner",
            "string/admax_banner",
        )
        val notFound = expected.filterNot { it in scanned }
        assertTrue(
            "Scanner không thấy: $notFound\nTập đã quét:\n" + scanned.sorted().joinToString("\n"),
            notFound.isEmpty(),
        )
    }

    // B13 (1.9.0 T1): string-array đọc bằng tên phải được quét, và keep.xml phủ kiểu `array`.
    @Test
    fun `scanner thay string-array ads_bidding_partners va keep phu kieu array`() {
        assertTrue("Scanner không thấy array/ads_bidding_partners", "array/ads_bidding_partners" in namesReadByCode())
        assertTrue("appplugin_keep.xml thiếu mẫu @array/ads_*", patterns().any { it.matches("array/ads_bidding_partners") })
    }
}
