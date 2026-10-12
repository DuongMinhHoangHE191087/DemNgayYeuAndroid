package com.app.plugin.ads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 2.0.0 C3c P3: mọi chỗ ĐĂNG KÝ listener GMA trong thư mục `ads/adsmob` đi qua `MainPost.` (một dòng mỗi chỗ).
 * Số chỗ cố định = 30 (đo lúc dispatch); thêm chỗ đăng ký mới phải bọc và nâng số này có chủ ý.
 */
class MainPostCensusTest {
    private val objSite = Regex(
        "object\\s*:\\s*(?:[A-Za-z]+\\.)*(AdListener|FullScreenContentCallback|VideoLifecycleCallbacks|InterstitialAdLoadCallback|" +
            "RewardedAdLoadCallback|RewardedInterstitialAdLoadCallback|AppOpenAdLoadCallback)\\(\\)"
    )
    private val files = File("src/main/java/com/app/plugin/ads/adsmob").listFiles { f -> f.name.endsWith(".kt") }!!.sortedBy { it.name }

    @Test
    fun `P3 moi cho dang ky listener adsmob di qua MainPost`() {
        var wrapped = 0
        val bad = ArrayList<String>()
        for (f in files) {
            val t = f.readText()
            for (m in objSite.findAll(t)) {
                if (Regex("MainPost\\.\\w+\\($").containsMatchIn(t.substring(0, m.range.first).trimEnd())) wrapped++
                else bad.add("${f.name}: ${m.value}")
            }
            for (m in Regex("\\.forNativeAd\\b").findAll(t)) {
                if (t.substring(m.range.last + 1).trimStart().startsWith("(MainPost.nativeLoaded")) wrapped++ else bad.add("${f.name}: forNativeAd")
            }
            for (m in Regex("\\.setOnPaidEventListener\\b").findAll(t)) {
                if (t.substring(m.range.last + 1).trimStart().startsWith("(MainPost.paid")) wrapped++ else bad.add("${f.name}: setOnPaidEventListener")
            }
            for (m in Regex("\\bonPaidEventListener\\s*=").findAll(t)) {
                if (t.substring(m.range.last + 1).trimStart().startsWith("MainPost.paid")) wrapped++ else bad.add("${f.name}: onPaidEventListener")
            }
            wrapped += Regex("\\bMainPost\\.earned\\s*\\{").findAll(t).count()
            for (m in Regex("\\bOnUserEarnedRewardListener\\s*\\{|\\bOnPaidEventListener\\s*\\{").findAll(t)) {
                bad.add("${f.name}: ${m.value} (phải qua MainPost.earned/paid)")
            }
        }
        assertTrue("chỗ đăng ký chưa qua MainPost: $bad", bad.isEmpty())
        assertEquals("số chỗ đăng ký đã bọc", SITES, wrapped)
    }

    companion object {
        const val SITES = 30
    }
}
