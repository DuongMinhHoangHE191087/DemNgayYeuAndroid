package com.app.plugin.app

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 1.6.4 Task 4: `onCreate` ghi thời gian main thread vào `SdkHealth.initMs` sau `initPlugin()`. */
class AppPluginBaseInitMsContractTest {
    private val source = File("src/main/java/com/app/plugin/app/AppPluginBase.kt")
    private fun lines(): List<String> { assertTrue("Không thấy ${source.absolutePath}", source.isFile); return source.readLines() }
    private fun first(lines: List<String>, re: Regex, from: Int = 0): Int =
        lines.withIndex().drop(from).indexOfFirst { (_, l) -> val c = l.trim(); !c.startsWith("//") && !c.startsWith("*") && re.containsMatchIn(c) }.let { if (it < 0) -1 else it + from }

    @Test fun `onCreate ghi SdkHealth initMs sau initPlugin`() {
        val l = lines()
        val onCreate = first(l, Regex("""override fun onCreate\(\)"""))
        val call = first(l, Regex("""^initPlugin\(\)$"""), onCreate)
        val measure = first(l, Regex("""^val mainThreadMs = SystemClock\.elapsedRealtime\(\) - startedAtMs$"""), onCreate)
        val set = first(l, Regex("""SdkHealth\.initMs = mainThreadMs$"""), onCreate)
        assertTrue(
            "onCreate=$onCreate initPlugin()=$call mainThreadMs=$measure SdkHealth.initMs=mainThreadMs@$set",
            onCreate in 0 until call && call < measure && measure < set
        )
    }
}
