package com.app.plugin.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 2.1.0 G1 (D9, hợp đồng B): `-Xfriend-paths` (thấy `internal` của gốc) chỉ được nằm trên biên dịch unit-test của
 * `appplugin-game`, trỏ classes.jar debug của gốc. Trên main/release mã game chỉ được thấy API public + SPI opt-in —
 * cờ friend lọt vào main thì AAR phát hành gọi tên mangle `…$appplugin` của gốc (phải ghim cùng bản, F3).
 */
class GameModuleNoFriendMainTest {
    @Test
    fun `NF1 co friend chi trong khoi task KotlinCompile ten chua UnitTest, tro classes jar debug`() {
        val f = File("build.gradle.kts")
        assertTrue("Không thấy ${f.absolutePath}: sai thư mục làm việc", f.isFile)
        val l = f.readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("//") }
        val friend = l.withIndex().filter { "friend-paths" in it.value || "friendPaths" in it.value }
        assertEquals("cờ friend phải xuất hiện đúng 1 lần: ${friend.map { it.value }}", 1, friend.size)
        assertEquals("cờ friend phải nằm ngay trong if (name.contains(\"UnitTest\"))", "if (name.contains(\"UnitTest\")) {", l[friend[0].index - 1])
        assertEquals("khối if phải nằm trong tasks.withType<KotlinCompile>", true, l[friend[0].index - 2].startsWith("tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach"))
        val jar = l.single { it.startsWith("val coreDebugClassesJar") }
        assertTrue("friend-path phải là classes.jar debug của gốc: $jar", "compile_library_classes_jar/debug/bundleLibCompileToJarDebug/classes.jar" in jar)
        assertEquals("freeCompilerArgs chỉ ở dòng cờ friend", 1, l.count { "freeCompilerArgs" in it })
    }
}
