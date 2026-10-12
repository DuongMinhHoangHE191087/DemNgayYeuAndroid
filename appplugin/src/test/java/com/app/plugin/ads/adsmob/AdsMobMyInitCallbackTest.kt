package com.app.plugin.ads.adsmob

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2.4.4 INITCB (Hợp đồng C, quyết định (c)) — Legacy: callback `MobileAds.initialize` đã do GMA gọi trên main (logcat
 * tid = pid 8/8, `v244/tools/initcb.py`) ⇒ hàng load đang giữ nhả qua `MainFront.run` (chạy tại chỗ), không xếp cuối hàng
 * main bằng `main.post` (chờ khung hình). Hẹn 8 s, thả sớm FASTSTART-C và dòng `complete after` giữ nguyên. Đọc nguồn
 * như FC2 (`AdsMobMyEarlyReleaseTest`): MobileAds chạy trên coroutine IO, không chạy trong JVM test.
 */
class AdsMobMyInitCallbackTest {
    private val body: String = File("src/main/java/com/app/plugin/ads/adsmob/AdsMobMy.kt").readText()
        .substringAfter("internal fun startNetwork(").substringBefore("\n    public fun ")
    private val listener: String = body.substringAfter("MobileAds.initialize(appContext) {").substringBefore("earlyRelease()")

    @Test fun `IC1 callback init khong main_post markReady, nha qua MainFront_run`() {
        assertFalse("callback init đã chạy trên main (logcat tid=pid): không được xếp markReady cuối hàng bằng main.post",
            Regex("""main\.post\s*\{\s*readyQueue\.markReady\(\)""").containsMatchIn(listener))
        assertEquals("callback init nhả hàng đúng một lần qua MainFront.run { readyQueue.markReady() }", 1,
            Regex("""MainFront\.run\s*\{\s*readyQueue\.markReady\(\)\s*}""").findAll(listener).count())
        assertFalse("callback init không xếp gì lên main bằng post/postDelayed (bọc MainFront trong main.post vẫn là cuối hàng)",
            Regex("""\bmain\.post""").containsMatchIn(listener))
        assertTrue("nhả sau dòng complete after (thứ tự giữ)",
            listener.indexOf("ads admob initialize: complete after") in 0 until listener.indexOf("MainFront.run"))
    }

    @Test fun `IC3 hen 8 s va tha som FASTSTART-C giu nguyen, ngoai callback init`() {
        val outside = body.replace(listener, "")
        assertEquals("hẹn SDK_INIT_TIMEOUT_MS bằng main.postDelayed giữ (ngoài callback)", 1,
            Regex("""main\.postDelayed\(\{[^}]*not complete after \${'$'}SDK_INIT_TIMEOUT_MS ms""").findAll(outside).count())
        assertEquals("thả sớm FASTSTART-C bằng main.postDelayed giữ", 1,
            Regex("""main\.postDelayed\(\{\s*if \(!readyQueue\.isReady\) \{\s*Log\.i\(LogUtil\.TAG, "ads admob initialize: early release""")
                .findAll(outside).count())
        assertFalse("MainFront chỉ trong callback init", outside.contains("MainFront"))
    }
}
