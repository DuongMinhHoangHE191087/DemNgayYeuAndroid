package com.example

import com.example.ui.util.AuthSecurityManager
import com.example.ui.util.PasswordStrengthLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthSecurityTest {

  @Test
  fun generateSalt_returns32CharacterHexString() {
    val salt1 = AuthSecurityManager.generateSalt()
    val salt2 = AuthSecurityManager.generateSalt()

    assertEquals(32, salt1.length)
    assertEquals(32, salt2.length)
    assertNotEquals(salt1, salt2)
  }

  @Test
  fun hashPassword_isDeterministicWithSameSalt() {
    val salt = AuthSecurityManager.generateSalt()
    val hash1 = AuthSecurityManager.hashPassword("Secret@123", salt)
    val hash2 = AuthSecurityManager.hashPassword("Secret@123", salt)

    assertEquals(hash1, hash2)
    assertEquals(64, hash1.length) // SHA-256 in hex is 64 chars
  }

  @Test
  fun hashPassword_differsWithDifferentSalt() {
    val salt1 = AuthSecurityManager.generateSalt()
    val salt2 = AuthSecurityManager.generateSalt()
    val hash1 = AuthSecurityManager.hashPassword("Secret@123", salt1)
    val hash2 = AuthSecurityManager.hashPassword("Secret@123", salt2)

    assertNotEquals(hash1, hash2)
  }

  @Test
  fun emailValidation_worksCorrectly() {
    assertTrue(AuthSecurityManager.isValidEmail("hoang.inlove@gmail.com"))
    assertTrue(AuthSecurityManager.isValidEmail("khanhlinh@domain.vn"))

    assertFalse(AuthSecurityManager.isValidEmail(""))
    assertFalse(AuthSecurityManager.isValidEmail("invalid-email"))
    assertFalse(AuthSecurityManager.isValidEmail("@gmail.com"))
    assertFalse(AuthSecurityManager.isValidEmail("test@"))
  }

  @Test
  fun evaluatePasswordStrength_identifiesLevels() {
    val weak = AuthSecurityManager.evaluatePasswordStrength("123456")
    assertTrue(weak.level == PasswordStrengthLevel.VERY_WEAK || weak.level == PasswordStrengthLevel.WEAK)

    val strong = AuthSecurityManager.evaluatePasswordStrength("InLove@2026#Secret")
    assertTrue(strong.level == PasswordStrengthLevel.STRONG || strong.level == PasswordStrengthLevel.VERY_STRONG)
    assertTrue(strong.missingRequirements.isEmpty())
  }

  @Test
  fun checkLockoutStatus_handlesActiveLockout() {
    val futureLockout = System.currentTimeMillis() + 60000L
    val lockedStatus = AuthSecurityManager.checkLockoutStatus(5, futureLockout)
    assertTrue(lockedStatus.isLocked)
    assertTrue(lockedStatus.remainingSeconds > 0)

    val normalStatus = AuthSecurityManager.checkLockoutStatus(2, 0L)
    assertFalse(normalStatus.isLocked)
    assertEquals(3, normalStatus.remainingAttempts)
  }

  @Test
  fun maskEmail_masksProperly() {
    val masked = AuthSecurityManager.maskEmail("hoang@gmail.com")
    assertEquals("h***g@gmail.com", masked)
  }

  @Test
  fun hashPin_producesDeterministicHash() {
    val salt = AuthSecurityManager.generateSalt()
    val pinHash1 = AuthSecurityManager.hashPin("1234", salt)
    val pinHash2 = AuthSecurityManager.hashPin("1234", salt)
    val pinHashOther = AuthSecurityManager.hashPin("5678", salt)

    assertEquals(pinHash1, pinHash2)
    assertNotEquals(pinHash1, pinHashOther)
    assertEquals(64, pinHash1.length)
  }
}
