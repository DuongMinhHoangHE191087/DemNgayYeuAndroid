package com.example.shared.widget

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class WidgetSnapshotContractTest {

    @Test
    fun widgetSnapshot_roundTripsJsonAndAppliesRedaction() {
        val snapshot = CoupleWidgetSnapshot(
            version = 1,
            coupleTitle = "Hoàng & Minh Anh",
            partner1Name = "Hoàng",
            partner2Name = "Minh Anh",
            loveDays = 520,
            startDateIso = "2025-05-01",
            nextOccasionTitle = "Thất Tịch",
            nextOccasionDaysRemaining = 7,
            redacted = true,
            updatedAtEpochMs = 1770000000000L,
        )
        val encoded = CoupleWidgetSnapshotJson.encode(snapshot)
        val decoded = CoupleWidgetSnapshotJson.decodeOrNull(encoded)
        assertNotNull(decoded)
        assertEquals(snapshot, decoded)

        val redacted = decoded.withPrivacyRedaction(isEnglish = false)
        assertEquals("Bạn", redacted.partner1Name)
        assertEquals("Người ấy", redacted.partner2Name)
        assertEquals("Dịp đặc biệt sắp tới", redacted.nextOccasionTitle)

        assertNull(CoupleWidgetSnapshotJson.decodeOrNull("{invalid"))
        assertEquals(1, CoupleWidgetSnapshotJson.decodeOrDefault("invalid").version)
    }
}
