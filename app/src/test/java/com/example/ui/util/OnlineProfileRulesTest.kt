package com.example.ui.util

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
}
