package com.app.plugin.nextgen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * 2.4.0 NG0 (Hợp đồng A, P24): override nextgen có kiểm soát. Thư mục làm việc = appplugin-nextgen/ (P26), gốc = "..".
 * FAIL khi: override (file ở src/main/{java,res}, hoặc test ở src/test/java trùng đường dẫn test chung) không có dòng
 * trong OVERRIDES.txt, dòng không có file hoặc không có lý do, override > bản legacy cùng tên + 40 dòng, file bất kỳ
 * trong appplugin-nextgen/ (trừ build/) > 800 dòng, thiếu OVERRIDES.txt (fail-closed).
 */
class NgOverridesTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun `NO1 cay that appplugin-nextgen khong vi pham`() {
        val module = File(".")
        assertTrue("cwd phải là appplugin-nextgen/: ${module.absoluteFile}", File("OVERRIDES.txt").isFile && File("../settings.gradle.kts").isFile)
        assertEquals(emptyList<String>(), NgOverrides.violations(module, File("..")))
    }

    @Test
    fun `NO2 override khong khai trong OVERRIDES txt la vi pham`() {
        val (m, l) = fixture()
        write(l, "src/main/java/a/A.kt", 10); write(m, "src/main/java/a/A.kt", 10)
        assertHas(NgOverrides.violations(m, l), "không có dòng trong OVERRIDES.txt: src/main/java/a/A.kt")
    }

    @Test
    fun `NO3 dong khong co file hoac khong co ly do la vi pham`() {
        val (m, l) = fixture("src/main/java/a/Gone.kt # đã xoá", "src/main/res/layout/x.xml")
        write(m, "src/main/res/layout/x.xml", 3)
        val v = NgOverrides.violations(m, l)
        assertHas(v, "dòng không có file: src/main/java/a/Gone.kt")
        assertHas(v, "dòng không có lý do: src/main/res/layout/x.xml")
    }

    @Test
    fun `NO4 override vuot legacy cong 40 dong la vi pham, bang 40 thi dat`() {
        val (m, l) = fixture("src/main/java/a/A.kt # NG1", "src/test/java/a/ATest.kt # NG1")
        write(l, "src/main/java/a/A.kt", 100); write(m, "src/main/java/a/A.kt", 141)
        write(l, "src/test/java/a/ATest.kt", 10); write(m, "src/test/java/a/ATest.kt", 50)
        assertEquals(listOf("override > legacy + 40: src/main/java/a/A.kt (141 > 100 + 40)"), NgOverrides.violations(m, l))
    }

    @Test
    fun `NO5 file trong module qua 800 dong la vi pham, build bi bo qua`() {
        val (m, l) = fixture("src/main/java/a/New.kt # chỉ nextgen")
        write(m, "src/main/java/a/New.kt", 801); write(m, "build/generated/Big.kt", 5000); write(m, "shared-test-exclude.txt", 800)
        assertEquals(listOf("file > 800 dòng: src/main/java/a/New.kt (801)"), NgOverrides.violations(m, l))
    }

    @Test
    fun `NO6 thieu OVERRIDES txt la vi pham`() {
        val m = tmp.newFolder("m2"); val l = tmp.newFolder("l2")
        assertHas(NgOverrides.violations(m, l), "thiếu OVERRIDES.txt")
    }

    private fun fixture(vararg lines: String): Pair<File, File> {
        val m = tmp.newFolder("m"); val l = tmp.newFolder("l")
        File(m, "OVERRIDES.txt").writeText("# đầu\n" + lines.joinToString("") { "$it\n" })
        return m to l
    }

    private fun write(root: File, rel: String, lines: Int) =
        File(root, rel).run { parentFile.mkdirs(); writeText((1..lines).joinToString("") { "l$it\n" }) }

    private fun assertHas(v: List<String>, item: String) = assertTrue("thiếu '$item' trong $v", item in v)
}

/** Kiểm OVERRIDES.txt của module nextgen [module] so với cây gốc [legacy] (cả hai là thư mục gốc module). */
internal object NgOverrides {
    private const val SLACK = 40
    private const val MAX_LINES = 800
    private val TEXT = setOf("kt", "kts", "java", "xml", "txt", "pro", "md", "json", "properties") // R-NG0 m2: chỉ file chữ
    private val MAIN = listOf("src/main/java", "src/main/res")
    private const val TEST = "src/test/java"

    fun violations(module: File, legacy: File): List<String> {
        val list = File(module, "OVERRIDES.txt")
        if (!list.isFile) return listOf("thiếu OVERRIDES.txt")
        val out = mutableListOf<String>()
        val entries = list.readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
        val declared = entries.map { it.substringBefore('#').trim() }.toSet()
        entries.filter { it.substringAfter('#', "").isBlank() }.forEach { out += "dòng không có lý do: ${it.substringBefore('#').trim()}" }
        val actual = overrides(module, legacy)
        (actual - declared).sorted().forEach { out += "không có dòng trong OVERRIDES.txt: $it" }
        (declared - actual).sorted().forEach { out += "dòng không có file: $it" }
        actual.sorted().forEach { rel ->
            val old = File(legacy, rel)
            if (old.isFile) {
                val n = lines(File(module, rel)); val base = lines(old)
                if (n > base + SLACK) out += "override > legacy + $SLACK: $rel ($n > $base + $SLACK)"
            }
        }
        files(module, ".").filterNot { it == "build" || it.startsWith("build/") || it.substringAfterLast('.') !in TEXT }.sorted().forEach { rel ->
            val n = lines(File(module, rel))
            if (n > MAX_LINES) out += "file > $MAX_LINES dòng: $rel ($n)"
        }
        return out
    }

    /** src/main/{java,res} (mọi file) + test nextgen trùng đường dẫn test chung, đường dẫn tương đối module. */
    private fun overrides(module: File, legacy: File): Set<String> =
        MAIN.flatMap { d -> files(module, d) }.toSet() +
            files(module, TEST).filter { File(legacy, it).isFile }

    private fun files(root: File, dir: String): List<String> {
        val base = File(root, dir)
        if (!base.isDirectory) return emptyList()
        return base.walkTopDown().filter { it.isFile }.map { it.relativeTo(root).invariantSeparatorsPath }.toList()
    }

    private fun lines(f: File): Int = f.readLines().size
}
