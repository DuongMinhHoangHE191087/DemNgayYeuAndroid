package com.app.plugin.nextgen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * 2.4.0 NG4 (R-NG0 m3): layout hiệu lực của nextgen (override `src/main/res` + nguồn chung đã chép
 * `build/generated/appplugin-shared/res`, cwd = appplugin-nextgen/) còn nhắc lớp native Legacy `com.google.android.gms.ads.nativead.`
 * = đúng [EXPECTED]: rỗng từ NG5a (3 layout Full2 đã override).
 */
class NgLayoutCensusTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun `LC1 layout nextgen con lop native Legacy dung danh sach con lai`() {
        val gen = File(GENERATED)
        assertTrue("res chung chưa sinh hoặc sai cwd: ${gen.absoluteFile}", gen.isDirectory && (gen.list()?.isNotEmpty() == true))
        assertEquals(EXPECTED, NgLayoutCensus.legacyLayouts(listOf(File(OVERRIDE), gen)))
    }

    @Test fun `LC2 override thay ban chung, chi xet thu muc layout`() {
        val shared = tmp.newFolder("shared"); val over = tmp.newFolder("over")
        fun put(root: File, rel: String, legacy: Boolean) = File(root, rel).run {
            parentFile.mkdirs(); writeText(if (legacy) "<com.google.android.gms.ads.nativead.NativeAdView/>" else "<FrameLayout/>")
        }
        put(shared, "layout/a.xml", true); put(shared, "layout-land/b.xml", true); put(shared, "values/c.xml", true)
        put(over, "layout/a.xml", false)
        assertEquals(setOf("layout-land/b.xml"), NgLayoutCensus.legacyLayouts(listOf(over, shared)))
    }

    private companion object {
        const val OVERRIDE = "src/main/res"
        const val GENERATED = "build/generated/appplugin-shared/res"
        val EXPECTED = emptySet<String>() // NG5a: 3 layout Full2 đã override
    }
}

internal object NgLayoutCensus {
    private const val LEGACY = "com.google.android.gms.ads.nativead."

    /** Đường dẫn `<thư mục layout…>/x.xml` hiệu lực (gốc đầu tiên có file thắng) còn nhắc lớp native Legacy. */
    fun legacyLayouts(roots: List<File>): Set<String> {
        val effective = LinkedHashMap<String, File>()
        roots.filter { it.isDirectory }.forEach { root ->
            root.listFiles { f -> f.isDirectory && f.name.startsWith("layout") }.orEmpty().forEach { dir ->
                dir.listFiles { f -> f.isFile && f.extension == "xml" }.orEmpty().forEach { effective.putIfAbsent("${dir.name}/${it.name}", it) }
            }
        }
        return effective.filterValues { LEGACY in it.readText() }.keys
    }
}
