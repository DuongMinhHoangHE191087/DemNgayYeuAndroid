package com.example.alarm.plan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderTextsTest {

  private val vi = ReminderTextArgs(
    title = "Sinh nhật",
    dateText = "14/02/2026",
    daysBefore = 3,
    partnerName = "Linh",
    loveDays = 400,
    details = "Mua hoa"
  )

  private val ascii = ReminderTextArgs(
    title = "Anniversary",
    dateText = "14/02/2026",
    daysBefore = 3,
    partnerName = "Linh",
    loveDays = 400,
    details = "Buy flowers"
  )

  private val diacritics = "àáảãạăằắẳẵặâầấẩẫậèéẻẽẹêềếểễệìíỉĩịòóỏõọôồốổỗộơờớởỡợùúủũụưừứửữựỳýỷỹỵđ"

  private val loveKinds = listOf(
    ReminderKind.LOVE_MILESTONE_THREE_DAYS,
    ReminderKind.LOVE_MILESTONE_ONE_DAY,
    ReminderKind.LOVE_MILESTONE_DAY_OF
  )

  private fun assertText(
    kind: ReminderKind,
    isEnglish: Boolean,
    args: ReminderTextArgs,
    title: String,
    body: String
  ) {
    val text = ReminderTexts.of(kind, isEnglish, args)
    assertEquals(title, text.title)
    assertEquals(body, text.body)
  }

  @Test
  fun viAdvanceTextMatchesAndroidLiteral() {
    assertText(
      ReminderKind.ADVANCE, false, vi,
      "🔔 Sắp Đến: Sinh nhật",
      "Còn 3 ngày nữa là đến ngày kỷ niệm 'Sinh nhật' (14/02/2026). Đừng quên chuẩn bị điều bất ngờ cho người ấy nhé! 💕"
    )
  }

  @Test
  fun viDayOfTextMatchesAndroidLiteral() {
    assertText(
      ReminderKind.DAY_OF, false, vi,
      "🎉 Hôm Nay: Sinh nhật!",
      "Hôm nay là ngày kỷ niệm đặc biệt 'Sinh nhật' (14/02/2026)! Chúc hai bạn một ngày ngập tràn ngọt ngào và yêu thương! ❤️✨"
    )
  }

  @Test
  fun viUserMilestoneTextMatchesAndroidLiteral() {
    assertText(
      ReminderKind.USER_MILESTONE, false, vi,
      "⭐ Cột Mốc Tình Yêu: Sinh nhật",
      "Cột mốc ý nghĩa 'Sinh nhật' (14/02/2026) đang đến rất gần! Cùng người ấy lưu lại khoảnh khắc này nhé! 💖"
    )
  }

  @Test
  fun viCoupleAnniversaryTextMatchesAndroidLiteral() {
    assertText(
      ReminderKind.COUPLE_ANNIVERSARY, false, vi.copy(title = "Kỷ niệm 1 năm", loveDays = 400),
      "💑 Kỷ Niệm Ngày Yêu Nhau: Kỷ niệm 1 năm ❤️",
      "Chúc mừng ngày kỷ niệm chính thức yêu nhau của hai bạn! Chặng đường 400 ngày yêu thương ngọt ngào! 🎉🌹"
    )
  }

  @Test
  fun viLoveMilestoneTextsMatchAndroidLiterals() {
    val args = vi.copy(title = "100 ngày yêu nhau")
    assertText(
      ReminderKind.LOVE_MILESTONE_DAY_OF, false, args,
      "🎉 Hôm Nay: 100 ngày yêu nhau! ❤️",
      "Hôm nay là 100 ngày yêu nhau (14/02/2026) của bạn và Linh! Chúc hai bạn có một ngày kỷ niệm thật hạnh phúc và ngập tràn yêu thương! 🥂✨"
    )
    assertText(
      ReminderKind.LOVE_MILESTONE_ONE_DAY, false, args,
      "🌹 Ngày Mai: 100 ngày yêu nhau!",
      "Ngày mai là 100 ngày yêu nhau! Hãy sẵn sàng đón một ngày thật lãng mạn bên Linh nhé! 💕"
    )
    assertText(
      ReminderKind.LOVE_MILESTONE_THREE_DAYS, false, args,
      "🔔 Sắp Đến: 100 ngày yêu nhau!",
      "Còn 3 ngày nữa là đến cột mốc 100 ngày yêu nhau (14/02/2026)! Đừng quên chuẩn bị một món quà hoặc kế hoạch hẹn hò bất ngờ cho Linh nhé! 🎁💖"
    )
  }

  @Test
  fun enTextsAreExactTranslations() {
    assertText(
      ReminderKind.ADVANCE, true, ascii,
      "🔔 Coming Up: Anniversary",
      "Only 3 days left until your anniversary 'Anniversary' (14/02/2026). Don't forget to plan a surprise for your special someone! 💕"
    )
    assertText(
      ReminderKind.DAY_OF, true, ascii,
      "🎉 Today: Anniversary!",
      "Today is your special anniversary 'Anniversary' (14/02/2026)! Wishing you both a day full of sweetness and love! ❤️✨"
    )
    assertText(
      ReminderKind.USER_MILESTONE, true, ascii,
      "⭐ Love Milestone: Anniversary",
      "The meaningful milestone 'Anniversary' (14/02/2026) is almost here! Capture this moment together with your special someone! 💖"
    )
    assertText(
      ReminderKind.COUPLE_ANNIVERSARY, true, ascii,
      "💑 Our Love Anniversary: Anniversary ❤️",
      "Congratulations on your official love anniversary! A journey of 400 sweet days of love! 🎉🌹"
    )
    assertText(
      ReminderKind.LOVE_MILESTONE_DAY_OF, true, ascii,
      "🎉 Today: Anniversary! ❤️",
      "Today is Anniversary (14/02/2026) for you and Linh! Wishing you both a truly happy anniversary full of love! 🥂✨"
    )
    assertText(
      ReminderKind.LOVE_MILESTONE_ONE_DAY, true, ascii,
      "🌹 Tomorrow: Anniversary!",
      "Tomorrow is Anniversary! Get ready for a truly romantic day with Linh! 💕"
    )
    assertText(
      ReminderKind.LOVE_MILESTONE_THREE_DAYS, true, ascii,
      "🔔 Coming Up: Anniversary!",
      "Only 3 days left until the milestone Anniversary (14/02/2026)! Don't forget to prepare a gift or a surprise date plan for Linh! 🎁💖"
    )
  }

  @Test
  fun enTextsContainNoVietnameseDiacritics() {
    for (kind in ReminderKind.values()) {
      val text = ReminderTexts.of(kind, true, ascii)
      assertFalse(text.title, text.title.lowercase().any { it in diacritics })
      assertFalse(text.body, text.body.lowercase().any { it in diacritics })
    }
  }

  @Test
  fun everyKindHasNonBlankTextInBothLanguages() {
    assertEquals(8, ReminderKind.values().size)
    for (kind in ReminderKind.values()) {
      for (isEnglish in listOf(false, true)) {
        val text = ReminderTexts.of(kind, isEnglish, vi)
        assertTrue("$kind title", text.title.isNotBlank())
        assertTrue("$kind body", text.body.isNotBlank())
      }
    }
  }

  @Test
  fun customKeepsUserTitleAndDetailsInBothLanguages() {
    val args = ReminderTextArgs(title = "Tiêu đề riêng", details = "Nội dung riêng")
    val expected = ReminderText("Tiêu đề riêng", "Nội dung riêng")
    assertEquals(expected, ReminderTexts.of(ReminderKind.CUSTOM, false, args))
    assertEquals(expected, ReminderTexts.of(ReminderKind.CUSTOM, true, args))
  }

  @Test
  fun enPluralisesDays() {
    val one = ReminderTexts.of(ReminderKind.ADVANCE, true, ascii.copy(daysBefore = 1)).body
    assertTrue(one, one.startsWith("Only 1 day left until"))
    val three = ReminderTexts.of(ReminderKind.ADVANCE, true, ascii.copy(daysBefore = 3)).body
    assertTrue(three, three.startsWith("Only 3 days left until"))
    val zero = ReminderTexts.of(ReminderKind.ADVANCE, true, ascii.copy(daysBefore = 0)).body
    assertTrue(zero, zero.startsWith("Only 0 days left until"))
    val oneSweet = ReminderTexts.of(ReminderKind.COUPLE_ANNIVERSARY, true, ascii.copy(loveDays = 1)).body
    assertTrue(oneSweet, oneSweet.contains("A journey of 1 sweet day of love!"))
    val manySweet = ReminderTexts.of(ReminderKind.COUPLE_ANNIVERSARY, true, ascii.copy(loveDays = 400)).body
    assertTrue(manySweet, manySweet.contains("A journey of 400 sweet days of love!"))
    val zeroSweet = ReminderTexts.of(ReminderKind.COUPLE_ANNIVERSARY, true, ascii.copy(loveDays = 0)).body
    assertTrue(zeroSweet, zeroSweet.contains("A journey of 0 sweet days of love!"))
    val viOne = ReminderTexts.of(ReminderKind.ADVANCE, false, vi.copy(daysBefore = 1)).body
    assertTrue(viOne, viOne.startsWith("Còn 1 ngày nữa"))
  }

  @Test
  fun blankPartnerNameFallsBackToDefaultWord() {
    for (kind in loveKinds) {
      for (blank in listOf("", "   ")) {
        assertTrue(ReminderTexts.of(kind, false, vi.copy(partnerName = blank)).body.contains("người ấy"))
        assertTrue(ReminderTexts.of(kind, true, ascii.copy(partnerName = blank)).body.contains("your partner"))
      }
    }
    assertText(
      ReminderKind.LOVE_MILESTONE_ONE_DAY, false, vi.copy(partnerName = "Linh"),
      "🌹 Ngày Mai: Sinh nhật!",
      "Ngày mai là Sinh nhật! Hãy sẵn sàng đón một ngày thật lãng mạn bên Linh nhé! 💕"
    )
    assertText(
      ReminderKind.LOVE_MILESTONE_ONE_DAY, false, vi.copy(partnerName = " Linh "),
      "🌹 Ngày Mai: Sinh nhật!",
      "Ngày mai là Sinh nhật! Hãy sẵn sàng đón một ngày thật lãng mạn bên  Linh  nhé! 💕"
    )
    assertText(
      ReminderKind.LOVE_MILESTONE_ONE_DAY, true, ascii.copy(partnerName = " Linh "),
      "🌹 Tomorrow: Anniversary!",
      "Tomorrow is Anniversary! Get ready for a truly romantic day with  Linh ! 💕"
    )
  }

  @Test
  fun argsAreInterpolatedVerbatim() {
    val title = "A'B \"C\" {D} \$E %s 100%"
    val args = vi.copy(title = title)
    val vietnamese = ReminderTexts.of(ReminderKind.ADVANCE, false, args)
    assertEquals("🔔 Sắp Đến: " + title, vietnamese.title)
    assertTrue(vietnamese.body.contains(title))
    val english = ReminderTexts.of(ReminderKind.ADVANCE, true, args)
    assertEquals("🔔 Coming Up: " + title, english.title)
    assertTrue(english.body.contains(title))
  }

  @Test
  fun argsDefaultToEmptyAndZero() {
    val args = ReminderTextArgs()
    assertEquals("", args.title)
    assertEquals("", args.dateText)
    assertEquals(0, args.daysBefore)
    assertEquals("", args.partnerName)
    assertEquals(0, args.loveDays)
    assertEquals("", args.details)
  }

  @Test
  fun redactedTextExistsInBothLanguages() {
    assertEquals(ReminderText("Nhắc hẹn từ InLove", "Mở InLove để xem chi tiết"), ReminderTexts.redacted(false))
    assertEquals(ReminderText("Reminder from InLove", "Open InLove for details"), ReminderTexts.redacted(true))
    assertNotEquals(ReminderTexts.redacted(false), ReminderTexts.redacted(true))
  }

  @Test
  fun redactedTextHasNoUserContent() {
    val forbidden = listOf("Linh", "Sinh nhật", "Anniversary", "Mua hoa")
    for (isEnglish in listOf(false, true)) {
      val text = ReminderTexts.redacted(isEnglish)
      for (part in listOf(text.title, text.body)) {
        assertFalse(part, part.any { it.isDigit() })
        for (word in forbidden) {
          assertFalse(part, part.contains(word))
        }
      }
    }
  }
}
