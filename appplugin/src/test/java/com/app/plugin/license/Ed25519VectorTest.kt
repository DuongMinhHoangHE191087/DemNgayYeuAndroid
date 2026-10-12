package com.app.plugin.license

import com.app.plugin.license.Ed25519TestSigner.hex
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** SA1 L1–L4: [Ed25519.verify] theo RFC 8032 §7.1 TEST 1/2/3 (khoá công khai, thông điệp, chữ ký nguyên văn). */
class Ed25519VectorTest {
    private val test1 = Triple(Ed25519TestSigner.TEST1_PUB, "",
        "e5564300c360ac729086e2cc806e828a84877f1eb8e5d974d873e065224901555fb8821590a33bacc61e39701cf9b46bd25bf5f0595bbe24655141438e7a100b")
    private val test2 = Triple("3d4017c3e843895a92b70aa74d1b7ebc9c982ccf2ec4968cc0cd55f12af4660c", "72",
        "92a009a9f0d4cab8720e820b5f642540a2b27b5416503f8fb3762223ebdb69da085ac1e43e15996e458f3613d0f11d8c387b2eaeb4302aeeb00d291612bb0c00")
    private val test3 = Triple("fc51cd8e6218a1a38da47ed00230f0580816ed13ba3303ac5deb911548908025", "af82",
        "6291d657deec24024827e69c3abe01a30ce548a284743a445e3680d7db5ac3ac18ff9b538d16f290ae67f760984dc6594a7c15e9716ed28dc027beceea1ec40a")

    private fun verify(v: Triple<String, String, String>) = Ed25519.verify(hex(v.first), hex(v.second), hex(v.third))

    @Test fun `L1 RFC 8032 TEST 1 verify dung`() = assertTrue(verify(test1))

    @Test fun `L2 RFC 8032 TEST 2 verify dung`() = assertTrue(verify(test2))

    @Test fun `L3 RFC 8032 TEST 3 verify dung, va khop chu ky JDK cua TEST 1`() {
        assertTrue(verify(test3))
        // Đối chiếu chéo: JDK ký TEST 1 ra đúng chữ ký RFC (signer của test token là Ed25519 chuẩn).
        assertTrue(Ed25519TestSigner.sign(ByteArray(0)).contentEquals(hex(test1.third)))
    }

    @Test fun `L4 sua 1 bit chu ky hoac thong diep thi verify false`() {
        for (v in listOf(test1, test2, test3)) {
            for (i in listOf(0, 10, 31, 32, 63)) {
                val bad = hex(v.third)
                bad[i] = (bad[i].toInt() xor 1).toByte()
                assertFalse("bit lật ở byte $i", Ed25519.verify(hex(v.first), hex(v.second), bad))
            }
            assertFalse("thông điệp thêm 1 byte", Ed25519.verify(hex(v.first), hex(v.second) + byteArrayOf(1), hex(v.third)))
        }
        assertFalse("sai độ dài", Ed25519.verify(hex(test1.first), ByteArray(0), ByteArray(63)))
    }
}
