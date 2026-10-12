package com.app.plugin.license

import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64

/**
 * Ký token giấy phép trong test (chỉ `src/test`, không phụ thuộc SA2): Ed25519 của JDK host (JVM 21) với khoá
 * RFC 8032 §7.1 TEST 1 — đồng thời là phép đối chiếu chéo với [Ed25519] thuần Kotlin.
 */
internal object Ed25519TestSigner {
    const val TEST1_SEED: String = "9d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60"
    const val TEST1_PUB: String = "d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a"
    const val KID: String = "t1"
    const val PKG: String = "com.example.app"
    const val CERT: String = "41d06cb4629cf33920d966c51447b270ef78275f6cafb4a9caffa4f665e48bb2"
    const val EXP: Long = 2_000_000_000L   // 2033-05-18
    const val NOW_MS: Long = 1_790_000_000_000L   // 2026-09-21

    /** Bộ khoá cho seam [LicenseGate.keys]: chỉ `t1` = TEST 1. */
    val KEYS: Map<String, ByteArray> = mapOf(KID to hex(TEST1_PUB))

    /** Tiền tố PKCS#8 DER của khoá riêng Ed25519 (RFC 8410): 16 byte rồi 32 byte seed. */
    private const val PKCS8_PREFIX = "302e020100300506032b657004220420"

    fun hex(s: String): ByteArray = ByteArray(s.length / 2) { s.substring(2 * it, 2 * it + 2).toInt(16).toByte() }

    fun sign(msg: ByteArray, seedHex: String = TEST1_SEED): ByteArray {
        val key = KeyFactory.getInstance("Ed25519").generatePrivate(PKCS8EncodedKeySpec(hex(PKCS8_PREFIX + seedHex)))
        return Signature.getInstance("Ed25519").run { initSign(key); update(msg); sign() }
    }

    fun b64url(b: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(b)

    fun payload(
        kid: String = KID,
        pkg: String = PKG,
        certs: List<String> = listOf(CERT),
        exp: Long = EXP,
        feat: List<String> = listOf("ads", "brain"),
    ): String {
        fun arr(l: List<String>) = l.joinToString(",", "[", "]") { "\"$it\"" }
        return """{"v":1,"kid":"$kid","pkg":"$pkg","certs":${arr(certs)},"exp":$exp,"feat":${arr(feat)}}"""
    }

    /** `ALP1.<b64url(payload)>.<b64url(sig)>`, ký trên byte ASCII `ALP1.<b64url(payload)>`. */
    fun token(payloadJson: String = payload()): String {
        val signed = "ALP1." + b64url(payloadJson.toByteArray(Charsets.UTF_8))
        return signed + "." + b64url(sign(signed.toByteArray(Charsets.US_ASCII)))
    }
}
