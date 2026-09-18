package com.example

import com.example.ui.components.DatePickerPresets
import com.example.ui.components.DatePickerUtils
import com.example.ui.util.ProfileUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DatePickerTest {

  @Test
  fun testDateParsingAndFormatting_RoundTrip() {
    val dateStr = "18/12/2022"
    val millis = DatePickerUtils.parseDateToUtcMillis(dateStr)

    assertNotNull("Parsed millis should not be null", millis)
    val formatted = DatePickerUtils.formatUtcMillisToDate(millis!!)
    assertEquals("Formatted date must match original date string", dateStr, formatted)
  }

  @Test
  fun testDatePresets_RelationshipStartDatePresets() {
    val presets = DatePickerPresets.relationshipStartDatePresets()
    assertTrue("Should have multiple start date presets", presets.size >= 5)

    val labels = presets.map { it.first }
    assertTrue("Should contain 'Hôm nay'", labels.contains("Hôm nay"))
    assertTrue("Should contain '1 tháng trước'", labels.contains("1 tháng trước"))
    assertTrue("Should contain '3 năm trước'", labels.contains("3 năm trước"))
  }

  @Test
  fun testDatePresets_UpcomingAnniversaryPresets() {
    val presets = DatePickerPresets.upcomingAnniversaryPresets()
    assertTrue("Should have multiple upcoming anniversary presets", presets.size >= 4)

    val labels = presets.map { it.first }
    assertTrue("Should contain 'Hôm nay'", labels.contains("Hôm nay"))
    assertTrue("Should contain '+1 tháng'", labels.contains("+1 tháng"))
    assertTrue("Should contain '+100 ngày'", labels.contains("+100 ngày"))
    assertTrue("Should contain 'Tròn 1 năm'", labels.contains("Tròn 1 năm"))
  }

  @Test
  fun testFriendlyDateDescription() {
    val pastDesc = DatePickerUtils.getFriendlyDateDescription("18/12/2022")
    assertTrue("Past date description should mention elapsed days", pastDesc.contains("ngày yêu"))

    val futureDesc = DatePickerUtils.getFriendlyDateDescription("31/12/2099")
    assertTrue("Future date description should mention days remaining", futureDesc.contains("Còn") || futureDesc.contains("ngày"))
  }

  @Test
  fun testLoveDaysCalculationWithPickedDate() {
    val days = ProfileUtils.calculateLoveDays("18/12/2022")
    assertTrue("Love days from 2022 should be greater than 365", days > 365)
  }
}
