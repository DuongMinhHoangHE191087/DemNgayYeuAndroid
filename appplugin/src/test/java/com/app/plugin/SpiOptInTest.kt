package com.app.plugin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 2.1.0 G1 (hợp đồng B, Q1 = SPI): đúng 3 điểm public mà `appplugin-game` dùng mang `@AppPluginInternalApi` (opt-in mức
 * ERROR) — `RcSectionRegistry.register`, `BrainSignalListener`, `GameHost` — và `RcSectionRegistry` không lộ gì khác.
 * Đọc nguồn: bỏ annotation thì mọi thứ vẫn biên dịch (gốc + `-game` đều opt-in), chỉ test này đỏ.
 */
class SpiOptInTest {
    private val src = File("src/main/java/com/app/plugin")

    private fun lines(rel: String): List<String> {
        val f = File(src, rel)
        assertTrue("Không thấy ${f.absolutePath}: sai thư mục làm việc", f.isFile)
        return f.readLines().map { it.trim() }
    }

    /** Dòng ngay trước khai báo [decl] (bỏ dòng trống) phải là `@AppPluginInternalApi`. */
    private fun assertAnnotated(rel: String, decl: String) {
        val l = lines(rel)
        val i = l.indexOfFirst { it.startsWith(decl) }
        assertTrue("$rel: không thấy '$decl'", i > 0)
        assertEquals("$rel: '$decl' thiếu @AppPluginInternalApi ngay phía trên", "@AppPluginInternalApi", l[i - 1])
    }

    @Test
    fun `SPI2 register, BrainSignalListener, GameHost khai AppPluginInternalApi muc ERROR, RcSectionRegistry chi lo register`() {
        assertTrue(
            "AppPluginInternalApi phải là @RequiresOptIn(level = ERROR)",
            lines("AppPluginInternalApi.kt").any { it.startsWith("@RequiresOptIn(level = RequiresOptIn.Level.ERROR") }
        )
        assertAnnotated("firbase/RcSectionRegistry.kt", "public fun register(")
        assertAnnotated("brain/BrainSignalListener.kt", "public interface BrainSignalListener")
        assertAnnotated("firbase/GameHost.kt", "public object GameHost")
        val publicMembers = lines("firbase/RcSectionRegistry.kt")
            .filter { Regex("""^(@\w+\s+)*public (fun|val|var|class|const|object)\b""").containsMatchIn(it) && !it.startsWith("public object RcSectionRegistry") }
        assertEquals("RcSectionRegistry chỉ được lộ register(): $publicMembers", 1, publicMembers.size)
    }
}
