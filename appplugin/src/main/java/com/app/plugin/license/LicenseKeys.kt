package com.app.plugin.license

/**
 * Khoá công khai Ed25519 theo `kid` (hợp đồng §C). Khoá riêng không bao giờ ở repo (D6). `dev1` dùng ở 2.1.0 tới khi
 * chủ dự án keygen `k1` (O5). Xoay khoá = thêm một mục `kid` mới ở bản sau; mục cũ giữ tới khi token cuối của nó hết
 * hạn (D6, v1.1). `kid` không có ở đây → `INVALID (kid)`.
 */
internal object LicenseKeys {
    private val HEX: Map<String, String> = mapOf(
        "dev1" to "a6dac8ebd0ac4becd6dc0e67335b9d92e13f529760a6585d430fd2dfb6d036e7",
    )

    /** kid → 32 byte, bản sao mới mỗi lần gọi (mảng không dùng chung để sửa được); hex hỏng bị bỏ → `INVALID (kid)`. */
    fun all(): Map<String, ByteArray> = HEX.mapNotNull { (kid, hex) -> hexToBytes(hex)?.let { kid to it } }.toMap()

    fun hexToBytes(hex: String): ByteArray? {
        if (hex.length != 64 || hex.any { Character.digit(it, 16) < 0 }) return null
        return ByteArray(32) { ((Character.digit(hex[2 * it], 16) shl 4) or Character.digit(hex[2 * it + 1], 16)).toByte() }
    }
}
