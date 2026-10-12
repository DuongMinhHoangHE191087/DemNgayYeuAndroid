package com.app.plugin.license

/**
 * base64url RFC 4648 §5 bằng Kotlin thuần (2.2.0 MIN24, D9-220): thay `java.util.Base64` (API 26) để SDK chạy
 * minSdk 24; `android.util.Base64` trả mặc định trên JVM nên 18 test giấy phép không dùng được. [encode] không đệm.
 */
internal object B64Url {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
    private val INDEX = IntArray(128) { -1 }.also { t -> ALPHABET.forEachIndexed { i, c -> t[c.code] = i } }

    fun encode(bytes: ByteArray): String {
        val sb = StringBuilder((bytes.size * 4 + 2) / 3)
        for (i in bytes.indices step 3) {
            val n = minOf(3, bytes.size - i)
            var v = 0
            for (k in 0 until 3) v = (v shl 8) or (if (k < n) bytes[i + k].toInt() and 0xFF else 0)
            for (k in 0..n) sb.append(ALPHABET[(v shr (18 - 6 * k)) and 0x3F])
        }
        return sb.toString()
    }

    /** `null` khi có ký tự ngoài bảng, độ dài 4k+1 hoặc đệm `=` sai chỗ/sai số; nhận đệm đúng (`Zg==`, `Zm8=`). */
    fun decode(s: String): ByteArray? {
        val body = s.trimEnd('=')
        val pad = s.length - body.length
        if (body.length % 4 == 1 || (pad > 0 && (pad > 2 || s.length % 4 != 0))) return null
        val out = ByteArray(body.length * 6 / 8)
        var acc = 0
        var bits = 0
        var o = 0
        for (c in body) {
            val d = if (c.code < 128) INDEX[c.code] else -1
            if (d < 0) return null
            acc = ((acc shl 6) or d) and 0x3FFF
            bits += 6
            if (bits >= 8) { bits -= 8; out[o++] = (acc shr bits).toByte() }
        }
        return out
    }
}
