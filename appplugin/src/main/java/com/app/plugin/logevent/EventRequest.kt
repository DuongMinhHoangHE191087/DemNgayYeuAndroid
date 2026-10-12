package com.app.plugin.logevent

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody


internal data class EventRequest(
    val sequenceId: Int,
    val eventName: String,
    val eventData: Map<String, Any>,
    @SerializedName("ts") val timestamp: Long,
    val userProperty: UserProperty
)

internal data class UserProperty(
    @SerializedName("user_id") val userId: String,
    @SerializedName("app_name") val appName: String,
    @SerializedName("app_version") val appVersion: String,
    val language: String,
    val country: String,
    @SerializedName("device_info") val deviceInfo: String,
    @SerializedName("event_date") val eventDate: Int,
    @SerializedName("session_number") val sessionNumber: Int,
    @SerializedName("session_id") val sessionId: String,
    @SerializedName("install_day") val installDay: Int,
    @SerializedName("retention_day") val retentionDay: Int,
    @SerializedName("local_hour") val localHour: Int,
    @SerializedName("local_weekday") val localWeekday: String,
    val platform: String,
    @SerializedName("ab_test") val abTest: Int,
)

internal fun createRequestBody(
    sequenceId: Int,
    eventName: String,
    eventData: Map<String, Any>,
    timestamp: Long,
    userProperty: UserProperty
): okhttp3.RequestBody =
    createRequestJson(sequenceId, eventName, eventData, timestamp, userProperty)
        .toRequestBody(JSON_MEDIA_TYPE)

/** Media type của body event, dùng chung cho `createRequestBody` và bộ gửi của `LogEventServer`. */
internal val JSON_MEDIA_TYPE = "application/json".toMediaType()

/**
 * JSON của một event. Hàng đợi event giữ chuỗi này (không giữ `RequestBody`) và chỉ dựng
 * `RequestBody` lúc gửi, nên gửi lại được nhiều lần.
 */
internal fun createRequestJson(
    sequenceId: Int,
    eventName: String,
    eventData: Map<String, Any>,
    timestamp: Long,
    userProperty: UserProperty
): String {
    val eventRequest = EventRequest(sequenceId, eventName, eventData, timestamp, userProperty)
    val jsonString = Gson().toJson(eventRequest)
    Logs.d("Log event server jsonString - $jsonString")
    return jsonString
}
