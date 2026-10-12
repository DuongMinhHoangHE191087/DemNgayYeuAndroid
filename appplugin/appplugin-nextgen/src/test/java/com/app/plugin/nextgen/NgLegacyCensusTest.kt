package com.app.plugin.nextgen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * 2.4.0 NG0 (Hợp đồng A, R6): file nguồn nextgen còn `import com.google.android.gms.ads.` (Legacy) = đúng danh sách
 * legacy-remaining.txt. Nguồn hiệu lực = override (src/main/java) + nguồn chung đã chép (build/generated/appplugin-shared/java);
 * thư mục làm việc = appplugin-nextgen/ (P26). Mỗi NG xoá dòng của nó; NG5b: rỗng.
 */
class NgLegacyCensusTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun `NL1 census nguon nextgen khop legacy-remaining txt`() {
        val gen = File(GENERATED)
        val kt = gen.walkTopDown().count { it.isFile && it.extension == "kt" }
        assertTrue("nguồn chung chưa sinh hoặc sai cwd: ${gen.absoluteFile} ($kt kt)", kt >= MIN_SHARED_KT)
        val census = NgCensus.legacyFiles(listOf(File(OVERRIDE), gen))
        assertEquals(NgCensus.expected(File(LIST)), census)
    }

    @Test
    fun `NL2 dong thua trong danh sach lam lech`() {
        val src = tmp.newFolder("java")
        File(src, "a").mkdirs(); File(src, "a/A.kt").writeText("package a\n\nimport com.google.android.gms.ads.AdView\n")
        File(src, "a/B.kt").writeText("package a\n// import com.google.android.gms.ads.AdView\n")
        val list = tmp.newFile("list.txt").apply { writeText("# c\na/A.kt\na/B.kt\n") }
        assertEquals(setOf("a/A.kt"), NgCensus.legacyFiles(listOf(src)))
        assertEquals(setOf("a/A.kt", "a/B.kt"), NgCensus.expected(list))
    }

    @Test
    fun `NL3 override thay file chung, thu muc vang khong tinh`() {
        val shared = tmp.newFolder("shared"); val over = tmp.newFolder("over")
        File(shared, "x").mkdirs(); File(shared, "x/C.kt").writeText("import com.google.android.gms.ads.nativead.NativeAd\n")
        File(over, "y").mkdirs(); File(over, "y/D.kt").writeText("import com.google.android.gms.ads.MobileAds\n")
        assertEquals(setOf("x/C.kt", "y/D.kt"), NgCensus.legacyFiles(listOf(over, shared, File(tmp.root, "absent"))))
    }

    private companion object {
        const val OVERRIDE = "src/main/java"
        const val GENERATED = "build/generated/appplugin-shared/java"
        const val LIST = "legacy-remaining.txt"
        const val MIN_SHARED_KT = 150
    }
}

internal object NgCensus {
    private const val LEGACY_IMPORT = "import com.google.android.gms.ads."

    /** File .kt (đường dẫn tương đối gốc java) có dòng bắt đầu bằng import Legacy, trên mọi gốc có mặt. */
    fun legacyFiles(roots: List<File>): Set<String> = roots.filter { it.isDirectory }.flatMap { root ->
        root.walkTopDown().filter { f -> f.isFile && f.extension == "kt" && f.readLines().any { it.trim().startsWith(LEGACY_IMPORT) } }
            .map { it.relativeTo(root).invariantSeparatorsPath }.toList()
    }.toSet()

    /** Dòng không rỗng, không bắt đầu bằng '#'; thiếu file = lỗi (fail-closed). */
    fun expected(list: File): Set<String> {
        check(list.isFile) { "thiếu ${list.absoluteFile}" }
        return list.readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.toSet()
    }
}
