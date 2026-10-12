package com.app.plugin.license

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.app.plugin.license.Ed25519TestSigner.CERT
import com.app.plugin.license.Ed25519TestSigner.EXP
import com.app.plugin.license.Ed25519TestSigner.KID
import com.app.plugin.license.Ed25519TestSigner.NOW_MS
import com.app.plugin.license.Ed25519TestSigner.PKG
import com.app.plugin.license.Ed25519TestSigner.payload
import com.app.plugin.license.Ed25519TestSigner.token
import com.app.plugin.license.LicenseGate.Verdict
import com.app.plugin.logevent.inMemoryPrefs
import com.app.plugin.until.LogUtil
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.`when`
import java.util.Collections

/**
 * 2.4.3 LCACHE (Hợp đồng A, quyết định (a)): verdict VALID được nhớ ở prefs riêng `appplugin_license` khoá `v1`, khoá băm
 * (token, gói, cert, khoá công khai, bản SDK); trúng ⇔ khoá bằng và `expSec > clock()/1000`; lỗi bất kỳ ⇒ verify đầy đủ;
 * không cache INVALID. Harness như `LicenseGateTest` (khoá RFC 8032 TEST 1), prefs trong bộ nhớ (`InMemoryPrefs.kt`).
 */
class LicenseCacheTest {
    private val store: MutableMap<String, Any?> = Collections.synchronizedMap(mutableMapOf())
    private var certs: () -> List<String> = { listOf(CERT) }
    private var keys: Map<String, ByteArray> = Ed25519TestSigner.KEYS
    private var now = NOW_MS
    private var certReads = 0

    @Before fun setUp() = LicenseGate.resetForTest()

    @After fun tearDown() = LicenseGate.resetForTest()

    private fun context(pkg: String = PKG, prefs: SharedPreferences? = inMemoryPrefs(store)): Context =
        mock(Context::class.java).also {
            `when`(it.packageName).thenReturn(pkg)
            `when`(it.getSharedPreferences(eq(FILE), anyInt())).thenReturn(prefs)
        }

    /** Một "process" mới: verdict qua đường thật (`start` → luồng `appplugin-license` → `await`). */
    private fun gate(raw: String = token(), ctx: Context = context()): Verdict {
        LicenseGate.resetForTest()
        LicenseGate.keys = { keys }
        LicenseGate.clock = { now }
        LicenseGate.certsProvider = { certReads++; certs() }
        LicenseGate.start(ctx, raw)
        return LicenseGate.await()
    }

    private fun entry(): String? = store[ENTRY] as String?

    /** Đặt lại phần `expSec` của mục đã lưu: verdict mang số này ⇔ đi đường cache (không parse, không Ed25519). */
    private fun forgeExp(exp: Long) {
        val e = checkNotNull(entry()) { "chưa có mục cache, store=$store" }
        store[ENTRY] = e.substringBeforeLast('|') + "|" + exp
    }

    private fun tampered(): String = token().substringBeforeLast('.') + "." + Ed25519TestSigner.b64url(ByteArray(64))

    @Test fun `LC1 VALID day du thi ghi muc cache vao appplugin_license, cert doc mot lan`() {
        assertEquals(Verdict.Valid(KID, EXP), gate())
        val e = entry()
        assertTrue("cache entry phải được ghi sau VALID đầy đủ, store=$store", e != null)
        assertTrue("mục = <khoá sha-256 hex>|<kid>|<expSec>: $e", Regex("[0-9a-f]{64}\\|$KID\\|$EXP").matches(e!!))
        assertEquals("cert đọc đúng một lần (evaluate dùng lại danh sách)", 1, certReads)
    }

    @Test fun `LC2 process moi voi muc co san thi VALID tu cache, khong parse khong Ed25519`() {
        gate()
        forgeExp(EXP + 100)
        assertEquals(Verdict.Valid(KID, EXP + 100), gate())
        assertEquals("trúng cache vẫn đọc cert (là đầu vào khoá), một lần", 2, certReads)
    }

    @Test fun `LC3 doi tung thanh phan khoa thi truot, verdict = evaluate, VALID thi ghi lai`() {
        gate()
        forgeExp(EXP + 100)
        // token khác (exp khác) → evaluate VALID mới, mục ghi lại theo khoá mới
        assertEquals(Verdict.Valid(KID, EXP + 5), gate(raw = token(payload(exp = EXP + 5))))
        assertTrue("mục ghi lại sau VALID: ${entry()}", entry()!!.endsWith("|$KID|${EXP + 5}"))
        gate()
        forgeExp(EXP + 100)
        assertEquals("gói khác", Verdict.Invalid("pkg"), gate(ctx = context(pkg = "com.other.app")))
        assertEquals("cert khác", Verdict.Invalid("cert"), run { certs = { listOf("00".repeat(32)) }; gate() })
        certs = { listOf(CERT) }
        assertEquals("cache còn nguyên khi verdict INVALID", Verdict.Valid(KID, EXP + 100), gate())
        keys = mapOf(KID to ByteArray(32) { 7 })
        assertEquals("khoá công khai của kid đổi", Verdict.Invalid("sig"), gate())
        keys = Ed25519TestSigner.KEYS + ("t2" to ByteArray(32))
        assertEquals("thêm khoá → khoá cache đổi, verify đầy đủ", Verdict.Valid(KID, EXP), gate())
    }

    @Test fun `LC3c ban SDK khac thi truot - khoa gom SDK_VERSION`() {
        val field = LicenseCache::class.java.getDeclaredField("sdkVersion").apply { isAccessible = true }
        val saved = field.get(null)
        assertEquals("seam mặc định = BuildConfig.SDK_VERSION", com.app.plugin.BuildConfig.SDK_VERSION, saved)
        gate()
        forgeExp(EXP + 100)
        try {
            field.set(null, "0.0.0-lc3c")
            assertEquals("bản SDK khác: verify đầy đủ, không dùng mục cũ", Verdict.Valid(KID, EXP), gate())
            assertTrue("mục ghi lại theo khoá mới: ${entry()}", entry()!!.endsWith("|$KID|$EXP"))
        } finally {
            field.set(null, saved)
        }
    }

    @Test fun `LC4 clock vuot expSec thi INVALID expired du khoa trung`() {
        gate()
        now = EXP * 1000 - 1000
        forgeExp(EXP)
        assertEquals("trước exp: trúng", Verdict.Valid(KID, EXP), gate())
        now = EXP * 1000
        assertEquals(Verdict.Invalid("expired"), gate())
        now = EXP * 1000 + 86_400_000
        assertEquals(Verdict.Invalid("expired"), gate())
    }

    @Test fun `LC5 muc hong, prefs nem, prefs null thi verify day du, khong bao gio VALID khi loi`() {
        for (bad in listOf("garbage", "|$KID|$EXP", "x".repeat(64) + "|$KID|abc")) {
            store[ENTRY] = bad
            assertEquals("mục hỏng '$bad' + token đúng", Verdict.Valid(KID, EXP), gate())
            assertTrue("ghi lại mục đúng sau VALID: ${entry()}", entry()!!.endsWith("|$KID|$EXP"))
            store[ENTRY] = bad
            assertEquals("mục hỏng '$bad' + chữ ký sai", Verdict.Invalid("sig"), gate(raw = tampered()))
        }
        gate()
        val good = entry()!!
        store[ENTRY] = good.substringBefore('|') + "||$EXP"
        assertEquals("kid rỗng", Verdict.Invalid("sig"), gate(raw = tampered()))
        store[ENTRY] = good.substringBeforeLast('|') + "|abc"
        assertEquals("expSec không phải số", Verdict.Invalid("sig"), gate(raw = tampered()))
        val throwing = mock(SharedPreferences::class.java) { throw IllegalStateException("prefs hỏng") }
        assertEquals("prefs ném + token đúng", Verdict.Valid(KID, EXP), gate(ctx = context(prefs = throwing)))
        assertEquals("prefs ném + chữ ký sai", Verdict.Invalid("sig"), gate(raw = tampered(), ctx = context(prefs = throwing)))
        assertEquals("prefs null + token đúng", Verdict.Valid(KID, EXP), gate(ctx = context(prefs = null)))
        val boom = mock(Context::class.java).also {
            `when`(it.packageName).thenReturn(PKG)
            `when`(it.getSharedPreferences(anyString(), anyInt())).thenThrow(SecurityException("no prefs"))
        }
        assertEquals("getSharedPreferences ném + chữ ký sai", Verdict.Invalid("sig"), gate(raw = tampered(), ctx = boom))
        assertEquals("getSharedPreferences ném + token đúng", Verdict.Valid(KID, EXP), gate(ctx = boom))
    }

    @Test fun `LC6 INVALID khong ghi muc cache nao`() {
        assertEquals(Verdict.Invalid("sig"), gate(raw = tampered()))
        assertEquals(Verdict.Invalid("kid"), gate(raw = token(payload(kid = "k9"))))
        assertEquals(Verdict.Invalid("feat"), gate(raw = token(payload(feat = listOf("ads")))))
        certs = { listOf("00".repeat(32)) }
        assertEquals(Verdict.Invalid("cert"), gate())
        assertEquals(Verdict.Invalid("missing"), gate(raw = " "))
        assertEquals("0 mục ghi sau INVALID", emptyMap<String, Any?>(), HashMap(store))
    }

    @Test fun `LC7 cert nem thi bo cache, ly do INVALID giu nguyen chu`() {
        certs = { throw SecurityException("PackageManager died") }
        assertEquals(Verdict.Invalid("malformed"), gate(raw = "ALP1.khong-phai-token"))
        assertEquals("= L13", Verdict.Invalid("error"), gate())
        assertEquals("0 mục ghi", emptyMap<String, Any?>(), HashMap(store))
    }

    @Test fun `LC3b muc cua token khac khong mo khoa token nay`() {
        gate(raw = token(payload(exp = EXP + 5)))
        val other = entry()!!
        assertNotEquals(other.substringBefore('|'), run { gate(); entry()!!.substringBefore('|') })
        store[ENTRY] = other
        assertEquals("token sai chữ ký không dựa được vào mục của token khác", Verdict.Invalid("sig"), gate(raw = tampered()))
    }

    private companion object {
        const val FILE = LicenseCache.FILE
        const val ENTRY = "v1"
    }

    /** Dòng `Log.i` (luật 26) khi [LicenseCache.resolve] chạy trên luồng test (static mock theo luồng); `Log.d` thì hỏng. */
    private fun logLines(launchNs: Long = System.nanoTime(), token: () -> String?): Pair<Verdict, List<String>> {
        LicenseGate.keys = { keys }
        LicenseGate.clock = { now }
        LicenseGate.certsProvider = { certs() }
        val lines = mutableListOf<String>()
        val v = mockStatic(Log::class.java) { inv ->
            check(inv.method.name != "d") { "dòng license phải là Log.i" }
            if (inv.method.name == "i") lines += "${inv.arguments[0]} ${inv.arguments[1]}"
            if (inv.method.returnType == java.lang.Boolean.TYPE) false else 0
        }.use { LicenseCache.resolve(context(), launchNs, token) }
        return v to lines
    }

    private fun logLines(raw: String) = logLines(token = { raw })

    @Test fun `LC2b dong log source=cache khi trung, source=verify khi truot, rong hay INVALID - mot dong, Log_i`() {
        val (first, l1) = logLines(token())
        assertEquals(Verdict.Valid(KID, EXP), first)
        assertEquals(1, l1.size)
        assertTrue(l1[0], Regex("${LogUtil.TAG} license: source=verify ms=\\d+ since_start=\\d+ cert=\\d+").matches(l1[0]))
        val (second, l2) = logLines(token())
        assertEquals(Verdict.Valid(KID, EXP), second)
        assertEquals(1, l2.size)
        assertTrue(l2[0], Regex("${LogUtil.TAG} license: source=cache ms=\\d+ since_start=\\d+ cert=\\d+").matches(l2[0]))
        for (raw in listOf(" ", tampered(), "ALP1.x")) {
            val (_, l) = logLines(raw)
            assertEquals("token '$raw'", 1, l.size)
            assertTrue(l[0], l[0].startsWith("${LogUtil.TAG} license: source=verify ms="))
        }
        val (err, le) = logLines(token = { throw IllegalStateException("meta-data") })
        assertEquals(Verdict.Invalid("error"), err)
        assertTrue("đọc token ném vẫn có dòng verify: $le", le.single().startsWith("${LogUtil.TAG} license: source=verify ms="))
    }

    @Test fun `LC2c truong log - since_start tinh tu launch, cert la wall doc cert, ms bao cert`() {
        certs = { Thread.sleep(40); listOf(CERT) }
        val (v, lines) = logLines(launchNs = System.nanoTime() - 5_000_000_000L, token = { token() })
        assertEquals(Verdict.Valid(KID, EXP), v)
        val m = Regex("${LogUtil.TAG} license: source=verify ms=(\\d+) since_start=(\\d+) cert=(\\d+)").matchEntire(lines.single())
        val (ms, since, cert) = checkNotNull(m) { "chuỗi hợp đồng: ${lines.single()}" }.destructured.toList().map { it.toLong() }
        assertTrue("cert ≥ 40 (đọc cert ngủ 40 ms): $cert", cert >= 40)
        assertTrue("ms ≥ cert: $ms vs $cert", ms >= cert)
        assertTrue("since_start ≥ 5000 (launch 5 s trước): $since", since >= 5000)
        val (_, blank) = logLines(" ")
        assertTrue("token rỗng: không đọc cert ⇒ cert=0: $blank", blank.single().endsWith(" cert=0"))
    }
}
