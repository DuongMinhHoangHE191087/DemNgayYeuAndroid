package com.app.plugin.logevent

import com.app.plugin.BuildConfig
import com.app.plugin.consent.ConsentState
import com.google.gson.JsonParser
import java.util.UUID

/**
 * Envelope v2 của body event (1.7.0 Task 1, plan "Hợp đồng event v2"): thêm vào cấp cao nhất của JSON v1 (giữ
 * `sequenceId`, `eventName`, `eventData`, `ts`, `userProperty`) các trường `event_id`, `schema_version` = 2,
 * `sdk_version`, `package_name` (backend không tin nó cho `app_id`) và `consent_tier` lúc dựng.
 *
 * Gọi một chỗ, lúc dựng body trên IO (`LogEventTransport.buildRequestJson`), không lúc gửi: lần gửi lại mang đúng body
 * cũ, nên cùng `event_id` và server khử trùng được. `EventRequest`/`UserProperty` (public) không thêm field.
 */
internal object EventEnvelope {
    const val SCHEMA_VERSION = 2

    fun newEventId(): String = UUID.randomUUID().toString()

    fun wrap(json: String, eventId: String): String {
        val body = JsonParser.parseString(json).asJsonObject
        body.addProperty("event_id", eventId)
        body.addProperty("schema_version", SCHEMA_VERSION)
        body.addProperty("sdk_version", BuildConfig.SDK_VERSION)
        body.addProperty("package_name", LogEventServerHelper.currentPackageName())
        body.addProperty("consent_tier", ConsentState.tier().name)
        return body.toString()
    }
}
