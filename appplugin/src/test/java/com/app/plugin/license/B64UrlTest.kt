package com.app.plugin.license

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Base64
import kotlin.random.Random

/** 2.2.0 MIN24 (D9-220): codec base64url Kotlin thuần thay `java.util.Base64` (API 26) trong [LicenseToken]. */
class B64UrlTest {
    @Test fun `B64U1 vecto RFC 4648 muc 10 dang url khong dem, bang url, 1000 vong ngau nhien seed co dinh`() {
        val rfc = listOf("" to "", "f" to "Zg", "fo" to "Zm8", "foo" to "Zm9v", "foob" to "Zm9vYg", "fooba" to "Zm9vYmE",
            "foobar" to "Zm9vYmFy")
        for ((plain, enc) in rfc) {
            assertEquals("encode($plain)", enc, B64Url.encode(plain.toByteArray(Charsets.US_ASCII)))
            assertEquals("decode($enc)", plain, String(B64Url.decode(enc)!!, Charsets.US_ASCII))
        }
        // Bảng §5: 62 → '-', 63 → '_' (bảng chuẩn §4 là '+', '/').
        val url = byteArrayOf(0xfb.toByte(), 0xff.toByte(), 0xbf.toByte())
        assertEquals("-_-_", B64Url.encode(url))
        assertArrayEquals(url, B64Url.decode("-_-_"))
        // Nhận đệm đúng của RFC (`Zg==`, `Zm8=`); token giấy phép vẫn từ chối đệm ở LicenseToken (chính tắc).
        assertEquals("f", String(B64Url.decode("Zg==")!!, Charsets.US_ASCII))
        assertEquals("fo", String(B64Url.decode("Zm8=")!!, Charsets.US_ASCII))
        val rnd = Random(220_024)
        repeat(1000) {
            val bytes = rnd.nextBytes(rnd.nextInt(0, 97))
            val enc = B64Url.encode(bytes)
            assertEquals("đối chiếu JDK", Base64.getUrlEncoder().withoutPadding().encodeToString(bytes), enc)
            assertArrayEquals("vòng ${bytes.size} byte", bytes, B64Url.decode(enc))
        }
    }

    @Test fun `B64U2 ky tu ngoai bang, do dai 4k+1, dem sai thi null va token malformed`() {
        val bad = listOf(
            "Zm9v+A", "Zm/v", "Zm9v YQ", "Zm9é", "Zm9v\n", "Zm.v",  // ngoài bảng url (gồm bảng chuẩn `+`, `/`)
            "A", "Zm9vY", "Zm9vYmFyZ",                                  // độ dài 4k+1: không byte nào ghép được
            "Zg=", "Zg===", "Z===", "Zm9v=", "Zm9v====", "====", "=Zg", "Zg==Zg", "Z=g=", // đệm sai số/sai chỗ
        )
        for (s in bad) assertNull("decode(\"$s\") phải null", B64Url.decode(s))
        val sig = B64Url.encode(ByteArray(64))
        assertNull("payload 4k+1 → malformed", LicenseToken.parse("ALP1.Zm9vY.$sig"))
        assertEquals(LicenseGate.Verdict.Invalid("malformed"), LicenseGate.evaluate("ALP1.Zm9vY.$sig", "p", { emptyList() }, 0L))
    }
}
