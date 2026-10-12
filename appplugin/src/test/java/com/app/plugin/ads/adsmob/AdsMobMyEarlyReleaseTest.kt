package com.app.plugin.ads.adsmob

import java.io.File
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2.4.3 FASTSTART-C (Hợp đồng B phần C): resource app `ads_cf_admob_init_wait_ms` (vắng/0 = tắt, như 2.4.2) thả hàng load
 * AdMob đang giữ N ms (kẹp 1..8000) sau khi `MobileAds.initialize(…)` đã được gọi. Hàm kẹp là `private` (dump Δ 0) nên
 * đọc bằng reflection; thứ tự trong `startNetwork` kiểm bằng đọc nguồn (MobileAds chạy trên coroutine IO, không chạy
 * trong JVM test). Chỉ Legacy: Next-Gen ném khi load trước init (`appplugin-nextgen/shared-test-exclude.txt`).
 */
class AdsMobMyEarlyReleaseTest {
    private fun delayFn(): Method? = runCatching {
        AdsMobMy::class.java.getDeclaredMethod("earlyReleaseDelayMs", Int::class.javaPrimitiveType)
    }.getOrNull()

    private fun delay(raw: Int): Long? = (delayFn() ?: throw AssertionError("AdsMobMy.earlyReleaseDelayMs vắng")).let { it.isAccessible = true; it.invoke(AdsMobMy(), raw) as Long? }

    @Test fun `FC0 ham thuan earlyReleaseDelayMs co mat va private`() {
        val m = delayFn()
        assertNotNull("AdsMobMy.earlyReleaseDelayMs (private, FS-c) phải có", m)
        assertTrue("earlyReleaseDelayMs phải private (dump Δ 0)", Modifier.isPrivate(m!!.modifiers))
    }

    @Test fun `FC1 bang kep - 0 va am tat, 1 den 8000 giu, tren 8000 ve 8000`() {
        val got = listOf(Int.MIN_VALUE, -1, 0, 1, 250, 8_000, 8_001, 9_000, Int.MAX_VALUE).map { it to delay(it) }
        val want = listOf(Int.MIN_VALUE to null, -1 to null, 0 to null, 1 to 1L, 250 to 250L, 8_000 to 8_000L,
            8_001 to 8_000L, 9_000 to 8_000L, Int.MAX_VALUE to 8_000L)
        assertEquals("≤ 0 ⇒ null (tắt, chờ callback như cũ); 1..8000 giữ; > 8000 ⇒ 8000", want, got)
    }

    @Test fun `FC2 nguon - tha som nam sau setRequestConfiguration va MobileAds_initialize trong coroutine IO`() {
        val src = File("src/main/java/com/app/plugin/ads/adsmob/AdsMobMy.kt").readText()
        val body = src.substringAfter("internal fun startNetwork(").substringBefore("\n    public fun ")
        val launch = body.indexOf("backgroundScope.launch {")
        val setRc = body.indexOf("MobileAds.setRequestConfiguration(")
        val init = body.indexOf("MobileAds.initialize(")
        val read = body.indexOf("\"ads_cf_admob_init_wait_ms\"")
        val release = body.indexOf("early release after")
        val call = body.indexOf("earlyRelease()", init)
        assertTrue("đủ mốc trong startNetwork: launch=$launch setRc=$setRc init=$init read=$read release=$release call=$call",
            listOf(launch, setRc, init, read, release, call).all { it >= 0 })
        assertTrue("setRequestConfiguration trước initialize, trong coroutine", setRc in (launch + 1) until init)
        assertTrue("lời gọi thả sớm sau MobileAds.initialize (test device áp trước mọi request)", call > init && call > setRc)
        assertEquals("đúng một lời gọi thả sớm", 1, Regex("""\bearlyRelease\(\)""").findAll(body).count())
        val seg = body.substring(launch, call)   // R-LB M1: ở cấp 1 của launch — không ngoài coroutine, không trong callback init
        assertEquals("earlyRelease() ở cấp 1 của launch (không ngoài coroutine, không trong callback init)",
            1, seg.count { it == '{' } - seg.count { it == '}' })
        assertTrue("đọc resource trong khối thả sớm (luồng IO, sau initialize), không trên luồng gọi",
            body.indexOf("val earlyRelease") < read && read < release)
        assertTrue("dòng log thả sớm là Log.i (luật 26)",
            Regex("""Log\.i\(LogUtil\.TAG, "ads admob initialize: early release after \$\w+ ms \(ads_cf_admob_init_wait_ms\)"\)""")
                .containsMatchIn(body))
    }
}
