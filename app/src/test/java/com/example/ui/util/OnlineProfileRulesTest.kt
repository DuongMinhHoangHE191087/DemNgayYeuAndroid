package com.example.ui.util

import com.example.data.model.OnlineUserEntity
import com.example.data.repository.OnlineCoupleRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineProfileRulesTest {

  @Test
  fun guestAndBlankUidAreNoProfile() {
    assertNull(profileUidOf(null))
    assertNull(profileUidOf(""))
    assertNull(profileUidOf(OnlineCoupleRepository.GUEST_UID))
    assertEquals("u1", profileUidOf("u1"))
  }

  @Test
  fun displayAvatarUrlDropsNullBlankAndTheStockPhoto() {
    assertEquals("", displayAvatarUrl(null))
    assertEquals("", displayAvatarUrl(""))
    assertEquals("", displayAvatarUrl("  "))
    assertEquals("", displayAvatarUrl("https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=200"))
    assertEquals(
      "",
      displayAvatarUrl("https://images.unsplash.com/photo-1534528741775-53994a69daeb?q=80&w=600&auto=format&fit=crop"),
    )
  }

  @Test
  fun displayAvatarUrlKeepsRealPhotos() {
    assertEquals("https://example.com/a.jpg", displayAvatarUrl("https://example.com/a.jpg"))
    assertEquals("content://media/external/images/1", displayAvatarUrl("content://media/external/images/1"))
    assertEquals("file:///data/user/0/app/files/a.jpg", displayAvatarUrl("file:///data/user/0/app/files/a.jpg"))
  }

  @Test
  fun roomOwnsCardOnlyWithoutAnOnlineProfile() {
    assertTrue(roomOwnsCard(null))
    assertTrue(roomOwnsCard(""))
    assertTrue(roomOwnsCard(OnlineCoupleRepository.GUEST_UID))
    assertFalse(roomOwnsCard("uid_1"))
  }

  @Test
  fun firstArrivalIsNotAReplacement() {
    assertFalse(isProfileChanged(applied = null, uid = "u1"))
  }

  @Test
  fun sameProfileIsNotAReplacement() {
    assertFalse(isProfileChanged(applied = "u1", uid = "u1"))
  }

  @Test
  fun anotherProfileReplacesTheApplied() {
    assertTrue(isProfileChanged(applied = "u1", uid = "u2"))
  }

  @Test
  fun vanishedProfileIsAChange() {
    assertTrue(isProfileChanged(applied = "u1", uid = null))
  }

  @Test
  fun blankFieldOfTheSameProfileClears() {
    assertEquals("", profileFieldAfter(current = "Minh", incoming = "", isBlank = true, sameProfile = true))
  }

  @Test
  fun blankFieldOnFirstArrivalKeepsWhatTheCardShows() {
    assertEquals("Minh", profileFieldAfter(current = "Minh", incoming = "", isBlank = true, sameProfile = false))
  }

  @Test
  fun filledFieldAlwaysApplies() {
    assertEquals("An", profileFieldAfter(current = "Minh", incoming = "An", isBlank = false, sameProfile = false))
  }

  @Test
  fun legacyRowTakesTheCardName() {
    val legacy = OnlineUserEntity(uid = "u1", displayName = "Bạn", coupleCode = "", isProfileSetup = false)
    val filled = legacy.withCardFallback(name = "An", birth = "", avatar = "")
    assertEquals("An", filled.effectiveDisplayName)
    assertTrue(filled.isProfileSetup)
  }

  @Test
  fun setUpRowIsNeverOverridden() {
    val row = OnlineUserEntity(uid = "u1", displayName = "Minh", coupleCode = "", isProfileSetup = true)
    assertEquals(row, row.withCardFallback(name = "An", birth = "", avatar = ""))
  }

  @Test
  fun blankNameLeavesTheRowUnchanged() {
    val legacy = OnlineUserEntity(uid = "u1", displayName = "Bạn", coupleCode = "", isProfileSetup = false)
    assertEquals(legacy, legacy.withCardFallback(name = "", birth = "", avatar = ""))
    assertEquals(legacy, legacy.withCardFallback(name = "  ", birth = "", avatar = ""))
  }

  @Test
  fun birthAndAvatarFillOnlyWhenBlank() {
    val blank = OnlineUserEntity(uid = "u1", coupleCode = "")
    val filled = blank.withCardFallback(name = "An", birth = "1990-01-02", avatar = "https://a.jpg")
    assertEquals("1990-01-02", filled.birthDate)
    assertEquals("https://a.jpg", filled.avatarUrl)

    val own = OnlineUserEntity(uid = "u1", coupleCode = "", birthDate = "1985-05-05", avatarUrl = "https://own.jpg")
    val kept = own.withCardFallback(name = "An", birth = "1990-01-02", avatar = "https://a.jpg")
    assertEquals("1985-05-05", kept.birthDate)
    assertEquals("https://own.jpg", kept.avatarUrl)
  }

  @Test
  fun nameHelpersUsePlaceholderOnlyWhenBlank() {
    assertEquals("Bạn", displayMeName("", isEnglish = false))
    assertEquals("You", displayMeName("", isEnglish = true))
    assertEquals("An", displayMeName("An", isEnglish = true))
    assertEquals("Người ấy", displayPartnerName("", isEnglish = false))
    assertEquals("Partner", displayPartnerName("", isEnglish = true))
    assertEquals("Minh", displayPartnerName("Minh", isEnglish = false))
  }
}
