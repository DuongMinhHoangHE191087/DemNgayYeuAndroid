package com.app.plugin.license

import android.content.Context
import com.app.plugin.license.Ed25519TestSigner.CERT
import com.app.plugin.license.Ed25519TestSigner.NOW_MS
import com.app.plugin.license.Ed25519TestSigner.PKG
import com.app.plugin.license.Ed25519TestSigner.payload
import com.app.plugin.license.Ed25519TestSigner.token
import com.app.plugin.license.LicenseGate.Verdict
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.time.Instant
import java.util.TimeZone

/** SA1 L6–L8, L10, L11, L13: luật verdict của [LicenseGate] với khoá RFC 8032 TEST 1 (`kid` = `t1`). */
class LicenseGateTest {
    @Before fun setUp() {
        LicenseGate.resetForTest()
        LicenseGate.keys = { Ed25519TestSigner.KEYS }
        LicenseGate.clock = { NOW_MS }
        LicenseGate.certsProvider = { listOf(CERT) }
    }

    @After fun tearDown() = LicenseGate.resetForTest()

    private fun eval(raw: String, certs: List<String> = listOf(CERT)) = LicenseGate.evaluate(raw, PKG, { certs }, NOW_MS)

    private fun context(): Context = mock(Context::class.java).also { `when`(it.packageName).thenReturn(PKG) }

    @Test fun `L6 token dung ky bang khoa TEST 1 thi VALID, qua luong appplugin-license`() {
        LicenseGate.start(context(), token())
        assertEquals(Verdict.Valid(Ed25519TestSigner.KID, Ed25519TestSigner.EXP), LicenseGate.await())
        assertTrue("cert hoa thường không quan trọng", eval(token(), listOf(CERT.uppercase())) is Verdict.Valid)
    }

    @Test fun `L6b token demo dev1 cua SA2 VALID voi khoa dev1 trong LicenseKeys truoc exp, INVALID expired sau exp`() {
        LicenseGate.keys = { LicenseKeys.all() }
        val demo = "ALP1.eyJ2IjoxLCJraWQiOiJkZXYxIiwicGtnIjoidGVzdC5zZGsuemQiLCJjZXJ0cyI6WyI0MWQwNmNiNDYyOWNmMzM5MjBkOTY2YzUx" +
            "NDQ3YjI3MGVmNzgyNzVmNmNhZmI0YTljYWZmYTRmNjY1ZTQ4YmIyIl0sImV4cCI6MTgyMjQ5OTI1NiwiZmVhdCI6WyJhZHMiLCJicmFpbiJdfQ." +
            "Y1eVa1odgcucGzmQfxuEk6heNyjrBBFYTiiYkkwwi0N1JEwv0pJVfmRqy_DKvdKlMMTL1PdlWSBbGInHhLsGAQ"
        val exp = 1_822_499_256L
        fun at(ms: Long) = LicenseGate.evaluate(demo, "test.sdk.zd", { listOf(CERT) }, ms)
        assertEquals(Verdict.Valid("dev1", exp), at(NOW_MS))
        assertEquals(Verdict.Valid("dev1", exp), at(exp * 1000 - 1000))
        assertEquals(Verdict.Invalid("expired"), at(exp * 1000))
        assertEquals(Verdict.Invalid("pkg"), LicenseGate.evaluate(demo, PKG, { listOf(CERT) }, NOW_MS))
    }

    @Test fun `L7 pkg sai thi INVALID pkg`() {
        assertEquals(Verdict.Invalid("pkg"), eval(token(payload(pkg = "com.other.app"))))
    }

    @Test fun `L8 khong cert nao khop thi INVALID cert`() {
        assertEquals(Verdict.Invalid("cert"), eval(token(), listOf("00".repeat(32))))
        assertEquals(Verdict.Invalid("cert"), eval(token(), emptyList()))
    }

    @Test fun `L10 het han thi INVALID expired, khong an han`() {
        assertEquals(Verdict.Invalid("expired"), eval(token(payload(exp = NOW_MS / 1000))))
        assertEquals(Verdict.Invalid("expired"), eval(token(payload(exp = NOW_MS / 1000 - 1))))
        assertTrue(eval(token(payload(exp = NOW_MS / 1000 + 1))) is Verdict.Valid)
    }

    @Test fun `L11 kid la thi INVALID kid, chu ky sai thi INVALID sig, thieu tinh nang thi INVALID feat`() {
        assertEquals(Verdict.Invalid("kid"), eval(token(payload(kid = "k9"))))
        val t = token()
        val tampered = t.substringBeforeLast('.') + "." + Ed25519TestSigner.b64url(ByteArray(64))
        assertEquals(Verdict.Invalid("sig"), eval(tampered))
        assertEquals(Verdict.Invalid("feat"), eval(token(payload(feat = listOf("ads")))))
        assertEquals(Verdict.Invalid("missing"), eval(" "))
    }

    @Test fun `L13 ngoai le trong xac minh thi INVALID, fail-closed, khong nem`() {
        LicenseGate.certsProvider = { throw SecurityException("PackageManager died") }
        LicenseGate.start(context(), token())
        assertEquals(Verdict.Invalid("error"), LicenseGate.await())
        assertTrue(LicenseGate.blocksAds)
        assertEquals("INVALID:error", LicenseGate.label)
        // Đọc bundle meta-data lỗi ở AppPluginBase (chuỗi canh) → error, không phải missing.
        LicenseGate.resetForTest()
        LicenseGate.start(context(), LicenseGate.READ_FAILED)
        assertEquals(Verdict.Invalid("error"), LicenseGate.await())
    }

    @Test fun `MS2d expDate la ngay UTC du mui gio may la Asia Ho_Chi_Minh, khop dinh dang Instant cu`() {
        val saved = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"))
            assertEquals("1970-01-01", LicenseGate.expDate(86_399L))   // 23:59:59 UTC = 06:59:59 ngày 02 ở +07
            assertEquals("1970-01-02", LicenseGate.expDate(86_400L))
            for (sec in listOf(0L, 61_199L, 1_822_499_256L, Ed25519TestSigner.EXP, 4_102_444_799L)) {
                assertEquals("exp=$sec", Instant.ofEpochSecond(sec).toString().take(10), LicenseGate.expDate(sec))
            }
        } finally {
            TimeZone.setDefault(saved)
        }
    }

    @Test fun `L15 census - moi accessor seam cua LicenseGate la synthetic, khoa dev1 la ban sao`() {
        val mangled = LicenseGate::class.java.declaredMethods.filter { it.name.contains("\$appplugin") }
        val names = mangled.map { it.name }.toSet()
        for (seam in listOf("certsProvider", "clock", "keys", "mainPoster")) {
            val cap = seam.replaceFirstChar { it.uppercase() }
            assertTrue("set$cap\$appplugin phải có", "set$cap\$appplugin" in names)
            assertTrue("get$cap\$appplugin phải có", "get$cap\$appplugin" in names)
        }
        val open = mangled.filter { !it.isSynthetic }.map { it.name }
        assertEquals("accessor/hàm internal không synthetic (javac của app gọi được): $open", emptyList<String>(), open)
        // Mảng khoá không dùng chung: sửa bản trả về không đổi khoá dev1 lần sau.
        LicenseKeys.all().getValue("dev1").fill(0)
        assertEquals(0xa6.toByte(), LicenseKeys.all().getValue("dev1")[0])
    }
}
