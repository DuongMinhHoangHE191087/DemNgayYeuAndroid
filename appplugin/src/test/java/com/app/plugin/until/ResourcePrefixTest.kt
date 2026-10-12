package com.app.plugin.until

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 2.0.0 C3a (D4): mọi resource SDK khai bắt đầu bằng `appplugin_` (lint `ResourceName` không thấy font,
 * nên RP1 tự kiểm file trong `res/`), và mọi `R.<loại>.<tên>` / `getIdentifier` SDK đọc hoặc mang tiền tố đó
 * hoặc là khoá app-contract (danh sách `tools:keep` của `appplugin_keep.xml`: app khai, SDK đọc bằng tên).
 * View id (`R.id.*`) không phải resource file nên không áp tiền tố.
 *
 * 2.1.0 P2 (M36/M38, R-C3a m1/m2): tên SDK đọc bằng chuỗi (`getIdentifier`, `getResource*`, `ResIdDefault`) chỉ nhận
 * đúng danh sách app-contract (khoá mang tiền tố `appplugin_` KHÔNG qua); bộ quét thêm `res/color`, `integer-array`,
 * `<item type=…>`, `declare-styleable` và `com.app.plugin.R.` đầy đủ. RP3–RP5 chạy bộ quét trên dữ liệu giả.
 */
class ResourcePrefixTest {
    private val prefix = "appplugin_"
    private val resDir = File("src/main/res")
    private val valueTag = Regex("<(color|dimen|string|style|integer|bool|array|string-array|integer-array|plurals|attr|declare-styleable)\\s+name=\"([^\"]+)\"")
    private val itemType = Regex("<item\\b[^>]*\\btype=\"([a-z]+)\"[^>]*\\bname=\"([^\"]+)\"|<item\\b[^>]*\\bname=\"([^\"]+)\"[^>]*\\btype=\"([a-z]+)\"")
    private val fileTypes = setOf("layout", "drawable", "mipmap", "font", "xml", "raw", "anim", "menu", "navigation", "animator", "color")
    private val rRef = Regex("(?<![\\w.])(?:com\\.app\\.plugin\\.)?R\\.([a-z]+)\\.([A-Za-z0-9_]+)")
    private val identifier = Regex("\\bgetIdentifier\\(\\s*\"([A-Za-z0-9_]+)\"\\s*,\\s*\"([a-z]+)\"")
    private val literal = Regex("\\bgetResource(String|Int|Ids)\\(\\s*[^,\\n]+,\\s*\"([A-Za-z0-9_]+)\"")

    /** (loại, tên) mọi resource khai trong [dir]: file trong `res/<loại>[-qualifier]/` và `name=` trong `values*`. */
    private fun declared(dir: File = resDir): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        dir.listFiles { f -> f.isDirectory }!!.forEach { d ->
            val type = d.name.substringBefore('-')
            d.listFiles { f -> f.isFile }!!.forEach { f ->
                when {
                    type == "values" -> {
                        val text = f.readText()
                        valueTag.findAll(text).forEach { out += it.groupValues[1] to it.groupValues[2] }
                        itemType.findAll(text).forEach {
                            out += if (it.groupValues[1].isNotEmpty()) it.groupValues[1] to it.groupValues[2] else it.groupValues[4] to it.groupValues[3]
                        }
                    }
                    type in fileTypes -> out += type to f.name.substringBefore('.')
                }
            }
        }
        return out
    }

    private fun contractPatterns(): List<Regex> {
        val keep = Regex("tools:keep=\"([^\"]+)\"").find(File("src/main/res/raw/appplugin_keep.xml").readText())
            ?: error("appplugin_keep.xml không có tools:keep")
        return keep.groupValues[1].split(',').map { it.trim().removePrefix("@") }
            .map { Regex("^" + it.replace(".", "\\.").replace("*", ".*") + "$") }
    }

    /** Tên đọc bằng chuỗi trong [text] (`getIdentifier`, `getResource*`): chỉ nhận khoá app-contract, tiền tố không cứu. */
    private fun dynamicViolations(text: String, where: String, contract: List<Regex>): List<String> {
        val reads = identifier.findAll(text).map { it.groupValues[2] to it.groupValues[1] } +
            literal.findAll(text).map { (if (it.groupValues[1] == "Int") "integer" else "string") to it.groupValues[2] }
        return reads.filter { (type, name) -> contract.none { it.matches("$type/$name") } }.map { (t, n) -> "$t/$n ($where)" }.toList()
    }

    /** `R.<loại>.<tên>` (kể cả `com.app.plugin.R.`) trong [text]: tiền tố hoặc khoá app-contract; `R.id` được miễn. */
    private fun refViolations(text: String, where: String, contract: List<Regex>): List<String> =
        rRef.findAll(text).filter { it.groupValues[1] != "id" }
            .filterNot { it.groupValues[2].startsWith(prefix) || contract.any { c -> c.matches("${it.groupValues[1]}/${it.groupValues[2]}") } }
            .map { "${it.groupValues[1]}/${it.groupValues[2]} ($where)" }.toList()

    @Test
    fun `RP1 moi resource SDK khai ke ca font bat dau bang appplugin_`() {
        val all = declared()
        assertTrue("Không quét được resource nào", all.size >= 90)
        val bad = all.filterNot { it.second.startsWith(prefix) }.map { "${it.first}/${it.second}" }.distinct().sorted()
        assertTrue("${bad.size} resource không có tiền tố $prefix:\n" + bad.joinToString("\n"), bad.isEmpty())
    }

    @Test
    fun `RP2 moi R loai ten SDK doc co tien to hoac app-contract, ten doc bang chuoi chi nhan app-contract`() {
        val contract = contractPatterns()
        val bad = mutableListOf<String>()
        File("src/main/java").walkTopDown().filter { it.extension == "kt" }.forEach { f ->
            val text = f.readText()
            bad += refViolations(text, f.name, contract) + dynamicViolations(text, f.name, contract)
        }
        // Tên động: AdsBase.loadIdsDefault() đọc mọi giá trị của ResIdDefault bằng getResourceString (khoá app-contract).
        Regex("\"([A-Za-z0-9_]+)\"").findAll(File("src/main/java/com/app/plugin/ads/ResIdDefault.kt").readText())
            .forEach { if (contract.none { c -> c.matches("string/${it.groupValues[1]}") }) bad += "string/${it.groupValues[1]} (ResIdDefault.kt)" }
        assertTrue("Tên SDK đọc không phải khoá app-contract (hoặc R. không tiền tố):\n" + bad.sorted().joinToString("\n"), bad.isEmpty())
    }

    // RP3 (M36 m1): khoá app-contract do APP khai; tên mang tiền tố của SDK bị đọc bằng chuỗi vẫn là lỗi (cũ: lọt qua startsWith).
    @Test
    fun `RP3 ten doc bang chuoi mang tien to appplugin_ ma khong thuoc contract bi bat`() {
        val contract = contractPatterns()
        val text = """res.getIdentifier("appplugin_admob_banner", "string", pkg); AppUtil.getResourceString(ctx, "appplugin_admob_banner")"""
        assertEquals(2, dynamicViolations(text, "Fake.kt", contract).size)
        assertTrue(dynamicViolations("""res.getIdentifier("admob_banner", "string", pkg)""", "Fake.kt", contract).isEmpty())
    }

    // RP4 (M36 m2/M38): bộ quét khai báo thấy res/color, integer-array, item type=, declare-styleable.
    @Test
    fun `RP4 bo quet khai bao thay color integer-array item type va declare-styleable`() {
        val dir = File(System.getProperty("java.io.tmpdir"), "rp4_" + System.nanoTime()).apply { mkdirs() }
        try {
            File(dir, "color").mkdirs()
            File(dir, "color/tint_sel.xml").writeText("<selector/>")
            File(dir, "values").mkdirs()
            File(dir, "values/v.xml").writeText(
                "<resources><integer-array name=\"ia\"/><item type=\"id\" name=\"item_a\"/><item name=\"item_b\" type=\"dimen\"/>" +
                    "<declare-styleable name=\"sty\"/></resources>"
            )
            val found = declared(dir).toSet()
            assertEquals(
                setOf("color" to "tint_sel", "integer-array" to "ia", "id" to "item_a", "dimen" to "item_b", "declare-styleable" to "sty"),
                found
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    // RP5 (M36 m2): R. đầy đủ `com.app.plugin.R.` cũng bị kiểm; R.id miễn.
    @Test
    fun `RP5 bo quet R thay ca com app plugin R day du`() {
        val contract = contractPatterns()
        val text = "val a = com.app.plugin.R.string.admob_x; val b = R.layout.bad_layout; val c = R.id.anything; val d = com.app.plugin.R.layout.appplugin_ok"
        val bad = refViolations(text, "Fake.kt", contract).sorted()
        assertEquals(listOf("layout/bad_layout (Fake.kt)"), bad)   // admob_x là khoá app-contract (string/admob_*): hợp lệ
        assertEquals(listOf("string/unknown_x (Fake.kt)"), refViolations("com.app.plugin.R.string.unknown_x", "Fake.kt", contract))
    }
}
