package com.app.plugin.app

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AppPluginBaseStartupTimingContractTest {
    private val source = File("src/main/java/com/app/plugin/app/AppPluginBase.kt")
    private fun lines(): List<String> { assertTrue("Không thấy ${source.absolutePath}", source.isFile); return source.readLines() }
    private fun first(lines: List<String>, re: Regex, from: Int = 0): Int =
        lines.withIndex().drop(from).indexOfFirst { (_, l) -> val c = l.trim(); !c.startsWith("//") && !c.startsWith("*") && re.containsMatchIn(c) }.let { if (it < 0) -1 else it + from }

    // R1 (1.6.3 Task 1, review cuối 1.6.2 §4.2): initPlugin in một dòng `initPlugin ms:` đủ 5 khoá, sau sessionStart.
    @Test fun `initPlugin log initPlugin ms du 5 buoc sau sessionStart`() {
        val l = lines()
        val start = first(l, Regex("""private fun initPlugin\("""))
        val sessionStart = first(l, Regex("""\bLogEventServer\.sessionStart\(\)"""), start)
        val log = first(l, Regex("""Log\.d\("AppPlugin", "initPlugin ms: manager=\$\{.+\} meta=\$\{.+\} helper\+gate=\$\{.+\} logevent=\$\{.+\} total=\$\{.+\}"\)"""), start)
        assertTrue("không thấy dòng initPlugin ms: trong initPlugin (start=$start log=$log)", start >= 0 && log > start)
        assertTrue("initPlugin ms phải in sau sessionStart (sessionStart=$sessionStart log=$log)", sessionStart in 0 until log)
        assertTrue("dòng phải nằm trong initPlugin", l.subList(start + 1, log).none { it.contains(" fun ") })
    }

    // R2 (đặc tả giữ): dòng main-thread ms vẫn in trong onCreate, sau lời gọi initPlugin().
    @Test fun `onCreate van log main-thread ms sau initPlugin`() {
        val l = lines()
        val onCreate = first(l, Regex("""override fun onCreate\(\)"""))
        val call = first(l, Regex("""^initPlugin\(\)$"""), onCreate)
        val ms = first(l, Regex("""AppPluginBase\.onCreate main-thread ms=\$\{"""), onCreate)
        assertTrue("onCreate=$onCreate initPlugin()=$call main-thread=$ms", onCreate in 0 until call && call < ms)
    }

    // R3 (1.6.4 Task 3, T1-R1): ranh giới bước đúng chỗ — mỗi `val tN` nằm giữa đúng hai lời gọi, không thì ms quy sai bước.
    @Test fun `ranh gioi buoc initPlugin dung thu tu`() {
        val l = lines()
        val start = first(l, Regex("""private fun initPlugin\("""))
        val marks = listOf(
            "val t0 = SystemClock.elapsedRealtime()", "AppPluginManager.instance.initPlugin(", "val t1 = SystemClock.elapsedRealtime()",
            "dataBucketConfig()", "val t2 = SystemClock.elapsedRealtime()", "LogEventServerHelper.init(", "TelemetryConsentGate.install(",
            "val t3 = SystemClock.elapsedRealtime()", "LogEventServer.init(", "LogEventServer.sessionStart()", "val t4 = SystemClock.elapsedRealtime()",
        )
        val at = marks.map { m -> first(l, Regex(Regex.escape(m)), start) }
        assertTrue("thứ tự ranh giới bước sai: ${marks.zip(at)}", start >= 0 && at.all { it > start } && at.zipWithNext().all { (a, b) -> a < b })
    }
}
