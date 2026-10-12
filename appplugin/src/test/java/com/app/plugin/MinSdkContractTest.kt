package com.app.plugin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 2.2.0 MIN24 (D2/D9-220): SDK `minSdk 24`. Số nằm ở đúng ba chỗ — `build.gradle.kts` (module gốc),
 * `gradle/module-defaults.gradle` (`appplugin-game`, `appplugin-max`) và `app/build.gradle.kts` (demo, mọi flavour) —
 * và mã `src/main` của ba module SDK không dùng API 26 mà lint `NewApi` từng bắt (`java.util.Base64`, `java.time`).
 * Cổng đầy đủ = lint `NewApi` 0; test này giữ census chạy được trên JVM.
 */
class MinSdkContractTest {
    @Test
    fun `MS1 minSdk 24 o ca ba noi va src main khong dung java util Base64 hay java time`() {
        val sites = listOf("build.gradle.kts", "gradle/module-defaults.gradle", "app/build.gradle.kts")
        for (path in sites) {
            val f = File(path)
            assertTrue("Không thấy ${f.absolutePath}: test chạy sai thư mục làm việc", f.isFile)
            val decl = f.readLines().map { it.trim() }.filter { it.startsWith("minSdk") }
            assertEquals("$path: minSdk", listOf("minSdk = 24"), decl)
        }
        val roots = listOf("src/main/java", "appplugin-game/src/main/java", "appplugin-max/src/main")
        val files = roots.map(::File).filter { it.isDirectory }
            .flatMap { r -> r.walkTopDown().filter { it.isFile && (it.extension == "kt" || it.extension == "java") }.toList() }
        assertTrue("Chỉ quét ${files.size} file nguồn — sai gốc?", files.size >= MIN_FILES)
        val banned = Regex("""\bjava\.util\.Base64\b|\bjava\.time\.""")
        val code = { line: String -> line.trim().let { !it.startsWith("*") && !it.startsWith("/*") && !it.startsWith("//") } }
        val hits = files.filter { f -> f.readLines().any { code(it) && banned.containsMatchIn(it) } }.map { it.path }
        assertEquals("API 26 trong src/main (lint NewApi ở minSdk 24)", emptyList<String>(), hits)
    }

    private companion object {
        const val MIN_FILES = 150
    }
}
