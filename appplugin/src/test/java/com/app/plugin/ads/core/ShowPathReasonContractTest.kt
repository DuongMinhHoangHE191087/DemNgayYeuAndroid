package com.app.plugin.ads.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 1.7.0 Task 2, hợp đồng nguồn của chuỗi show (quét `src/main`, không chạy mã):
 * - K1 (D9): mỗi `override fun onAdClick` trong `ads/core` gửi `ad_click` (`AdEventLog.click(`), và có ít nhất 8; T3b: mỗi
 *   `ad_click` mang placement gọi show (slot chỉ là chỗ lùi), và 24 lời gọi `PaidEvent` của mạng truyền `showPl`/`getPl`/`pl`.
 * - K2 (D11, G6 "reason 100 %"): trong thân `showInterstitial`/`showRewarded` không còn `return false` nào mà 6 dòng
 *   trước đó không báo lý do (`logAdShowFailWithReason` hoặc `reportDisabledShow`), và lý do là hằng
 *   `Constants.AdShowFailReason.*` hoặc nhãn của cổng (không phải chuỗi tự do, kể cả truyền theo vị trí). R-2 I2: cùng test
 *   này, mỗi `return` trước `logAdStartAction(` của `showInterstitial`/`showOpenAd`/`showNative` (lượt bị chặn) có
 *   `reportBlockedAttempt(` trong 8 dòng trước đó (`ad_show_attempt` của lượt đi tiếp phát ở `logAdStartAction`).
 */
class ShowPathReasonContractTest {

    private val coreDir = File("src/main/java/com/app/plugin/ads/core")
    private val reportWindow = 6
    private val reportCalls = listOf("logAdShowFailWithReason", "reportDisabledShow")
    private val freeTextReason = Regex("""reason\s*=\s*"|logAdShowFailWithReason\([^)]*"""")
    private val attemptWindow = 8

    @Test fun `K1 moi onAdClick trong ads core gui AdEventLog click`() {
        val sources = coreDir.listFiles { f -> f.name.endsWith(".kt") }!!.map { it.readText() }
        val overrides = sources.sumOf { Regex("""override fun onAdClick\b""").findAll(it).count() }
        val clicks = sources.sumOf { Regex("""AdEventLog\.click\(""").findAll(it).count() }
        assertTrue("override fun onAdClick = $overrides (D9: 8)", overrides >= 8)
        assertEquals("AdEventLog.click( = $clicks, override fun onAdClick = $overrides", overrides, clicks)
        // T3b (R-3b I1a): mọi `ad_click` mang placement gọi show (tham số đầu của `onAdClick`), slot mạng chỉ là chỗ lùi.
        val showFirst = Regex("""AdEventLog\.click\("[a-z_]+", placement\?\.takeIf \{ it\.isNotEmpty\(\) \} \?: originPl \?: """)
        val clickLines = sources.flatMap { it.lines() }.filter { it.contains("AdEventLog.click(") }
        assertEquals("ad_click placement = placement gọi show: ${clickLines.filterNot { showFirst.containsMatchIn(it) }}",
            clicks, clickLines.count { showFirst.containsMatchIn(it) })
        // T3b (R-3b I1b): 24 lời gọi paid event của mạng truyền placement gọi show/get, không phải slot (`originPl`).
        // 1.9.0 T6: tham số đầu có thể là `turnPl(<quảng cáo>, showPl|getPl)` (placement của lượt hiện quảng cáo đó).
        val paid = Regex("""PaidEvent\.(admob|max|metaDirect)\(\s*(turnPl\([A-Za-z_]+, (?:showPl|getPl)\)|[A-Za-z_.]+)\s*,\s*([A-Za-z_.]+)\s*,""")
        val networkSrc = listOf("adsmob", "adsmax", "adsfb").flatMap { d ->
            File("src/main/java/com/app/plugin/ads/$d").listFiles { f -> f.name.endsWith(".kt") }!!.map { it.name to it.readText() }
        }
        val calls = networkSrc.flatMap { (name, src) -> paid.findAll(src).map { Triple(name, it.groupValues[2], it.groupValues[3]) } }
        assertEquals("số lời gọi PaidEvent của mạng (AdMob 9, MAX 8, Meta 8)", 25, calls.size)
        val wrong = calls.filterNot { (_, first, second) ->
            (first in setOf("showPl", "getPl", "pl") || first.startsWith("turnPl(")) && second == "adsPlacementEcmp.originPl"
        }
        assertEquals("PaidEvent(<showPl|getPl|pl>, adsPlacementEcmp.originPl, …)", emptyList<Triple<String, String, String>>(), wrong)
        // `pl` (NtFull) = showPl, hoặc getPl khi flagShow != 0.
        networkSrc.filter { (_, src) -> paid.findAll(src).any { it.groupValues[2] == "pl" } }.forEach { (name, src) ->
            assertTrue("$name: pl phải là showPl/getPl", src.contains("var pl = showPl") && src.contains("pl = getPl"))
        }
    }

    @Test fun `K2 moi return false cua showInterstitial va showRewarded co ly do trong 6 dong truoc`() {
        val silent = listOf(
            "InterstitialController.kt" to "showInterstitial",
            "RewardedController.kt" to "showRewarded",
        ).flatMap { (file, fn) -> silentReturns(File(coreDir, file).readLines(), fn).map { "$file:$it" } }
        assertEquals("return false không báo lý do (${silent.size}): $silent", emptyList<String>(), silent)

        val blocked = listOf(
            "InterstitialController.kt" to "showInterstitial",
            "AppOpenController.kt" to "showOpenAd",
            "NativeController.kt" to "showNative",
        ).map { (file, fn) -> blockedReturns(File(coreDir, file).readLines(), fn).map { (line, ok) -> "$file:$line" to ok } }.flatten()
        assertTrue("return trước logAdStartAction quét được = ${blocked.size} (8 + 11 + 4)", blocked.size >= 23)
        val noAttempt = blocked.filter { !it.second }.map { it.first }
        assertEquals("return bị chặn không có reportBlockedAttempt (${noAttempt.size}): $noAttempt", emptyList<String>(), noAttempt)
    }

    /**
     * Các `return` của lượt bị chặn trong thân [fn]: mọi `return`/`return false` trong thân (quét cặp ngoặc từ dòng `fun fn(`)
     * trừ các `return` nằm trong khối chứa `logAdStartAction(` sau dòng đó (lượt đi tiếp đã phát `ad_show_attempt`). Trả số dòng
     * (1-based) và có `reportBlockedAttempt(` trong [attemptWindow] dòng trước hay không; cửa sổ đó cũng không được có lý do
     * tự do.
     */
    private fun blockedReturns(lines: List<String>, fn: String): List<Pair<Int, Boolean>> {
        val start = lines.indexOfFirst { it.contains("fun $fn(") }
        assertTrue("không thấy fun $fn(", start >= 0)
        // Độ sâu ngoặc trước mỗi dòng và độ sâu thấp nhất trong dòng (`} else {` đóng khối rồi mới mở khối mới).
        val depthBefore = mutableListOf<Int>()
        val lowest = mutableListOf<Int>()
        var depth = 0
        var opened = false
        var bodyEnd = lines.size - 1
        for (i in start until lines.size) {
            depthBefore += depth
            var low = depth
            for (c in lines[i]) {
                if (c == '{') { depth++; opened = true }
                if (c == '}') { depth--; low = minOf(low, depth) }
            }
            lowest += low
            if (opened && depth == 0) { bodyEnd = i; break }
        }
        val action = (start..bodyEnd).firstOrNull { lines[it].contains("logAdStartAction(") }
        assertTrue("không thấy logAdStartAction( trong $fn", action != null)
        val proceedEnd = (action!! + 1..bodyEnd).firstOrNull { lowest[it - start] < depthBefore[action - start] } ?: bodyEnd
        val isReturn = { t: String -> t == "return" || t == "return false" }
        return (start..bodyEnd).filter { isReturn(lines[it].trim()) && it !in action until proceedEnd }.map { i ->
            val window = lines.subList(maxOf(start, i - attemptWindow), i)
            window.forEach { assertTrue("lý do tự do trước dòng ${i + 1}: $it", !freeTextReason.containsMatchIn(it)) }
            i + 1 to window.any { it.contains("reportBlockedAttempt(") }
        }
    }

    /** Số dòng (1-based) của các `return false` trong thân [fn] (quét cặp ngoặc từ dòng `fun fn(`) không có báo lý do. */
    private fun silentReturns(lines: List<String>, fn: String): List<Int> {
        val start = lines.indexOfFirst { it.contains("fun $fn(") }
        assertTrue("không thấy fun $fn(", start >= 0)
        var depth = 0
        var opened = false
        val found = mutableListOf<Int>()
        for (i in start until lines.size) {
            val line = lines[i]
            if (line.trim() == "return false") {
                val window = lines.subList(maxOf(start, i - reportWindow), i)
                val reported = window.filter { w -> reportCalls.any { w.contains(it) } }
                if (reported.isEmpty()) found += i + 1
                window.forEach { assertTrue("lý do tự do trước dòng ${i + 1}: $it", !freeTextReason.containsMatchIn(it)) }
            }
            depth += line.count { it == '{' } - line.count { it == '}' }
            if (line.contains('{')) opened = true
            if (opened && depth == 0) break
        }
        return found
    }
}
