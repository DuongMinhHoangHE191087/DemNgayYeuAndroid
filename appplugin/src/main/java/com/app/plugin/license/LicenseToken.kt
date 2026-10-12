package com.app.plugin.license

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * Token giấy phép đã tách, chưa xác minh (hợp đồng §C, plan 2.1.0): `ALP1.<b64url(payload)>.<b64url(sig64)>`, payload
 * JSON UTF-8 `{"v":1,"kid","pkg","certs":[sha256 hex thường của DER],"exp":<unix s>,"feat":["ads","brain"]}`. Chữ ký
 * Ed25519 phủ đúng byte ASCII [signedPart] = `ALP1.<b64url(payload)>`.
 *
 * [parse] chỉ kiểm dạng: sai dạng ở bất kỳ bước nào → `null` (verdict `INVALID (malformed)`); khoá, chữ ký, gói,
 * cert, hạn và tính năng do [LicenseGate] xét.
 */
internal class LicenseToken(
    val kid: String,
    val pkg: String,
    val certs: List<String>,
    val expSec: Long,
    val feat: List<String>,
    val signedPart: ByteArray,
    val signature: ByteArray,
) {
    internal companion object {
        const val PREFIX: String = "ALP1"
        const val VERSION: Int = 1
        private const val SIG_BYTES = 64

        /** @return token đã tách, hoặc `null` khi [raw] sai dạng (thiếu phần, b64url hỏng, JSON thiếu/sai khoá). */
        fun parse(raw: String): LicenseToken? {
            val parts = raw.split('.')
            if (parts.size != 3 || parts[0] != PREFIX || parts[1].isEmpty() || parts[2].isEmpty()) return null
            val payload = b64url(parts[1]) ?: return null
            val sig = b64url(parts[2])?.takeIf { it.size == SIG_BYTES } ?: return null
            val json = try {
                JsonParser.parseString(String(payload, Charsets.UTF_8)).takeIf { it.isJsonObject }?.asJsonObject
            } catch (e: Exception) {
                null
            } ?: return null
            return fromJson(json, "${parts[0]}.${parts[1]}".toByteArray(Charsets.US_ASCII), sig)
        }

        private fun fromJson(o: JsonObject, signedPart: ByteArray, sig: ByteArray): LicenseToken? {
            if (int(o, "v") != VERSION) return null
            val kid = string(o, "kid") ?: return null
            val pkg = string(o, "pkg") ?: return null
            val certs = strings(o, "certs") ?: return null
            val exp = long(o, "exp") ?: return null
            val feat = strings(o, "feat") ?: return null
            return LicenseToken(kid, pkg, certs, exp, feat, signedPart, sig)
        }

        private val B64URL = Regex("^[A-Za-z0-9_-]+$")

        /** b64url chính tắc (R-SA2 I1): chỉ bảng `[A-Za-z0-9_-]`, không đệm, mã hoá lại ra đúng [s] (bit thừa = 0). */
        private fun b64url(s: String): ByteArray? {
            if (!B64URL.matches(s)) return null
            val bytes = B64Url.decode(s) ?: return null // 2.2.0 MIN24: codec Kotlin thuần (Base64 của JDK cần API 26)
            return bytes.takeIf { B64Url.encode(it) == s }
        }

        private fun prim(o: JsonObject, key: String) = o.get(key)?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive

        private fun string(o: JsonObject, key: String): String? =
            prim(o, key)?.takeIf { it.isString }?.asString?.takeIf { it.isNotEmpty() }

        private fun long(o: JsonObject, key: String): Long? = try {
            prim(o, key)?.takeIf { it.isNumber }?.asBigDecimal?.longValueExact()
        } catch (e: ArithmeticException) {
            null
        }

        private fun int(o: JsonObject, key: String): Int? = long(o, key)?.takeIf { it in 0..Int.MAX_VALUE }?.toInt()

        private fun strings(o: JsonObject, key: String): List<String>? {
            val arr: JsonArray = o.get(key)?.takeIf { it.isJsonArray }?.asJsonArray ?: return null
            val out = arr.map { e -> e.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString ?: return null }
            return out.takeIf { it.isNotEmpty() }
        }
    }
}
