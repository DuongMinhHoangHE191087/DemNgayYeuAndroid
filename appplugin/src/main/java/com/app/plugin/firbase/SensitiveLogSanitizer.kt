package com.app.plugin.firbase

/**
 * Chuỗi log thay cho giá trị nhạy cảm. FCM registration token đủ để gửi push tới đúng máy đó,
 * và logcat đọc được qua bug report hay công cụ của bên thứ ba, nên không bao giờ ghi nguyên.
 * Lấy từ Speaker Clean và Invoice Maker.
 */
internal object SensitiveLogSanitizer {
    fun fcmTokenEvent(event: String, token: String?): String =
        "FCM token $event ${if (token.isNullOrEmpty()) "[empty]" else "[redacted]"}"
}
