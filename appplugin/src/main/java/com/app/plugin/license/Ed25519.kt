package com.app.plugin.license

import java.math.BigInteger
import java.security.MessageDigest

/**
 * Xác minh chữ ký Ed25519 (PureEdDSA, RFC 8032 §5.1.7) bằng `BigInteger` + SHA-512 — Android dưới API 33 không có
 * Ed25519 nền tảng, SDK không thêm phụ thuộc (D2). Chỉ xác minh, không ký; dùng `BigInteger.valueOf(2)` vì
 * `BigInteger.TWO` chỉ có từ API 33. Thuần CPU, không I/O: chạy trên luồng `appplugin-license` ([LicenseGate]).
 * Vector RFC 8032 §7.1 TEST 1–3 khoá ở `Ed25519VectorTest`.
 */
internal object Ed25519 {
    private val TWO: BigInteger = BigInteger.valueOf(2)
    private val P: BigInteger = BigInteger.ONE.shiftLeft(255).subtract(BigInteger.valueOf(19))
    private val Q: BigInteger = BigInteger.ONE.shiftLeft(252).add(BigInteger("27742317777372353535851937790883648493"))
    private val D: BigInteger = BigInteger.valueOf(-121665).multiply(BigInteger.valueOf(121666).modInverse(P)).mod(P)
    private val SQRT_M1: BigInteger = TWO.modPow(P.subtract(BigInteger.ONE).shiftRight(2), P)

    /** Điểm trên đường cong Edwards, toạ độ mở rộng (X:Y:Z:T), x = X/Z, y = Y/Z, x·y = T/Z. */
    private class Pt(val x: BigInteger, val y: BigInteger, val z: BigInteger, val t: BigInteger)

    private val IDENTITY = Pt(BigInteger.ZERO, BigInteger.ONE, BigInteger.ONE, BigInteger.ZERO)

    private val BASE: Pt by lazy {
        val y = BigInteger.valueOf(4).multiply(BigInteger.valueOf(5).modInverse(P)).mod(P)
        val x = checkNotNull(recoverX(y, 0)) { "Ed25519 base point" }
        Pt(x, y, BigInteger.ONE, x.multiply(y).mod(P))
    }

    private fun add(a: Pt, b: Pt): Pt {
        val aa = a.y.subtract(a.x).multiply(b.y.subtract(b.x)).mod(P)
        val bb = a.y.add(a.x).multiply(b.y.add(b.x)).mod(P)
        val cc = TWO.multiply(a.t).multiply(b.t).multiply(D).mod(P)
        val dd = TWO.multiply(a.z).multiply(b.z).mod(P)
        val e = bb.subtract(aa)
        val f = dd.subtract(cc)
        val g = dd.add(cc)
        val h = bb.add(aa)
        return Pt(e.multiply(f).mod(P), g.multiply(h).mod(P), f.multiply(g).mod(P), e.multiply(h).mod(P))
    }

    private fun mul(s: BigInteger, p0: Pt): Pt {
        var q = IDENTITY
        var p = p0
        for (i in 0 until s.bitLength()) {
            if (s.testBit(i)) q = add(q, p)
            p = add(p, p)
        }
        return q
    }

    /** RFC 8032 §5.1.3 bước 2–4; `null` khi y ≥ p hoặc không có căn bậc hai. */
    private fun recoverX(y: BigInteger, sign: Int): BigInteger? {
        if (y >= P) return null
        val y2 = y.multiply(y)
        val x2 = y2.subtract(BigInteger.ONE).multiply(D.multiply(y2).add(BigInteger.ONE).modInverse(P)).mod(P)
        if (x2.signum() == 0) return if (sign == 1) null else BigInteger.ZERO
        var x = x2.modPow(P.add(BigInteger.valueOf(3)).shiftRight(3), P)
        if (x.multiply(x).subtract(x2).mod(P).signum() != 0) x = x.multiply(SQRT_M1).mod(P)
        if (x.multiply(x).subtract(x2).mod(P).signum() != 0) return null
        if (x.testBit(0) != (sign == 1)) x = P.subtract(x)
        return x
    }

    private fun decompress(s: ByteArray): Pt? {
        val raw = le(s)
        val y = raw.clearBit(255)
        val x = recoverX(y, if (raw.testBit(255)) 1 else 0) ?: return null
        return Pt(x, y, BigInteger.ONE, x.multiply(y).mod(P))
    }

    private fun sameAs(a: Pt, b: Pt): Boolean =
        a.x.multiply(b.z).subtract(b.x.multiply(a.z)).mod(P).signum() == 0 &&
            a.y.multiply(b.z).subtract(b.y.multiply(a.z)).mod(P).signum() == 0

    private fun le(b: ByteArray): BigInteger = BigInteger(1, b.reversedArray())

    /**
     * @return `true` khi [sig] (64 byte, R ‖ S) là chữ ký Ed25519 hợp lệ của [msg] theo khoá công khai [pub] (32 byte).
     *   Sai độ dài, điểm không giải nén được hay S ≥ L đều là `false` (RFC 8032 §5.1.7 bước 1).
     */
    fun verify(pub: ByteArray, msg: ByteArray, sig: ByteArray): Boolean {
        if (pub.size != 32 || sig.size != 64) return false
        val a = decompress(pub) ?: return false
        val r = decompress(sig.copyOfRange(0, 32)) ?: return false
        val s = le(sig.copyOfRange(32, 64))
        if (s >= Q) return false
        val md = MessageDigest.getInstance("SHA-512")
        md.update(sig, 0, 32)
        md.update(pub)
        md.update(msg)
        val h = le(md.digest()).mod(Q)
        return sameAs(mul(s, BASE), add(r, mul(h, a)))
    }
}
