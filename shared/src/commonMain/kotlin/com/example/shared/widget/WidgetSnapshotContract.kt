package com.example.shared.widget

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Shared cross-platform snapshot contract consumed by Android Glance / AppWidget
 * and iOS WidgetKit (App Group JSON container).
 */
@Serializable
data class CoupleWidgetSnapshot(
    val version: Int = CURRENT_VERSION,
    val coupleTitle: String = "",
    val partner1Name: String = "",
    val partner2Name: String = "",
    val loveDays: Int = 0,
    val startDateIso: String = "",
    val nextOccasionTitle: String = "",
    val nextOccasionDaysRemaining: Int? = null,
    val redacted: Boolean = false,
    val updatedAtEpochMs: Long = 0L,
) {
    companion object {
        const val CURRENT_VERSION: Int = 1
    }

    fun withPrivacyRedaction(isEnglish: Boolean): CoupleWidgetSnapshot =
        if (!redacted) this
        else copy(
            coupleTitle = if (isEnglish) "InLove" else "InLove",
            partner1Name = if (isEnglish) "You" else "Bạn",
            partner2Name = if (isEnglish) "Partner" else "Người ấy",
            nextOccasionTitle = if (isEnglish) "Upcoming special day" else "Dịp đặc biệt sắp tới",
        )
}

object CoupleWidgetSnapshotJson {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    fun encode(snapshot: CoupleWidgetSnapshot): String =
        json.encodeToString(CoupleWidgetSnapshot.serializer(), snapshot)

    fun decodeOrNull(raw: String): CoupleWidgetSnapshot? =
        if (raw.isBlank()) null else runCatching {
            json.decodeFromString(CoupleWidgetSnapshot.serializer(), raw)
        }.getOrNull()

    fun decodeOrDefault(raw: String): CoupleWidgetSnapshot =
        decodeOrNull(raw) ?: CoupleWidgetSnapshot()
}
