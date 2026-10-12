package com.app.plugin.license

import com.app.plugin.license.Ed25519TestSigner.b64url
import com.app.plugin.license.Ed25519TestSigner.payload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Base64

/** SA1 L12: token sai dạng ở mọi bước tách → `null` → `INVALID (malformed)`. */
class LicenseTokenTest {
    private val sig = b64url(ByteArray(64))

    private fun tok(json: String) = "ALP1." + b64url(json.toByteArray()) + "." + sig

    @Test fun `L12 dang hong - thieu dau cham, b64 sai, JSON thieu khoa - la INVALID malformed`() {
        assertNotNull("đối chứng: dạng đúng tách được", LicenseToken.parse(tok(payload())))
        val bad = listOf(
            "ALP1.bad.bad",                                   // smoke `-PlicenseToken=ALP1.bad.bad`: chữ ký không đủ 64 byte
            "ALP1" + b64url(payload().toByteArray()) + sig,   // thiếu dấu chấm
            "ALP1." + b64url(payload().toByteArray()),        // thiếu phần chữ ký
            "ALP2." + b64url(payload().toByteArray()) + "." + sig,
            "ALP1.\u0000*.$sig",                              // b64url sai
            "ALP1." + b64url(payload().toByteArray()) + ".a+b/",
            tok("not json"),
            tok("[1,2]"),
            tok(payload().replace("\"kid\":\"t1\",", "")),    // JSON thiếu khoá kid
            tok(payload().replace("\"v\":1", "\"v\":2")),
            tok(payload().replace("\"exp\":2000000000", "\"exp\":\"x\"")),
            tok(payload(certs = emptyList())),
            tok(payload(feat = emptyList())),
            // R-SA2 I1: b64url phải chính tắc — bảng [A-Za-z0-9_-], không đệm, mã hoá lại ra đúng chuỗi.
            tok(payload()).dropLast(1) + "B",                 // đổi ký tự cuối chữ ký: chỉ đổi bit thừa (giải mã lỏng vẫn ra 64 byte 0)
            tok(payload()) + "**",
            tok(payload()) + "==",                            // đệm `=` (64 byte → 86 ký tự + "==")
            "ALP1." + Base64.getUrlEncoder().encodeToString(payload(kid = "t12").toByteArray()) + "." + sig,   // payload có "=="
            "ALP1." + b64url(payload().toByteArray()) + "." + b64url(ByteArray(64) { -1 }).replace('_', '/'),  // bảng chuẩn `/`
        )
        for (b in bad) {
            assertNull("phải hỏng: $b", LicenseToken.parse(b))
            assertEquals(LicenseGate.Verdict.Invalid("malformed"),
                LicenseGate.evaluate(b, Ed25519TestSigner.PKG, { listOf(Ed25519TestSigner.CERT) }, Ed25519TestSigner.NOW_MS))
        }
        // Token demo dev1 (SA2) với ký tự cuối đổi trong cùng nhóm bit thừa ('Q' → 'R'): giải mã lỏng ra đúng chữ ký → phải malformed.
        val demo = DEMO_DEV1
        assertEquals(LicenseGate.Verdict.Valid("dev1", 1_822_499_256L),
            LicenseGate.evaluate(demo, "test.sdk.zd", { listOf(Ed25519TestSigner.CERT) }, Ed25519TestSigner.NOW_MS))
        assertEquals(LicenseGate.Verdict.Invalid("malformed"),
            LicenseGate.evaluate(demo.dropLast(1) + "R", "test.sdk.zd", { listOf(Ed25519TestSigner.CERT) }, Ed25519TestSigner.NOW_MS))
    }

    private companion object {
        const val DEMO_DEV1: String = "ALP1.eyJ2IjoxLCJraWQiOiJkZXYxIiwicGtnIjoidGVzdC5zZGsuemQiLCJjZXJ0cyI6WyI0MWQwNmNiNDYyOWNmMzM5MjBkOTY2YzUx" +
            "NDQ3YjI3MGVmNzgyNzVmNmNhZmI0YTljYWZmYTRmNjY1ZTQ4YmIyIl0sImV4cCI6MTgyMjQ5OTI1NiwiZmVhdCI6WyJhZHMiLCJicmFpbiJdfQ." +
            "Y1eVa1odgcucGzmQfxuEk6heNyjrBBFYTiiYkkwwi0N1JEwv0pJVfmRqy_DKvdKlMMTL1PdlWSBbGInHhLsGAQ"
    }
}
