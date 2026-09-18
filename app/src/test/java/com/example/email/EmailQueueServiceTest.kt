package com.example.email

import com.example.data.email.EmailQueueService
import com.example.data.email.OtpPurpose
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Comprehensive Unit Tests for EmailQueueService following /kotlin-testing standards.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EmailQueueServiceTest {

  private lateinit var emailService: EmailQueueService

  @Before
  fun setUp() {
    emailService = EmailQueueService.getInstance(null)
    emailService.clearAll()
  }

  @After
  fun tearDown() {
    emailService.clearAll()
  }

  @Test
  fun generateOtpCode_returns6DigitsNumericString() {
    val code = emailService.generateOtpCode()
    assertEquals(6, code.length)
    assertTrue(code.all { it.isDigit() })
    val numeric = code.toInt()
    assertTrue(numeric in 100_000..999_999)
  }

  @Test
  fun enqueueVerificationEmail_enqueuesTaskAndReturnsOtp() = runBlocking {
    val email = "test.couple@inlove.app"
    val result = emailService.enqueueVerificationEmail(email, OtpPurpose.REGISTRATION)

    assertTrue(result.isSuccess)
    val otp = result.getOrNull()
    assertNotNull(otp)
    assertEquals(6, otp!!.length)

    // Verify stored active OTP matches
    val activeOtp = emailService.getActiveOtpForTesting(email)
    assertEquals(otp, activeOtp)
  }

  @Test
  fun enqueueVerificationEmail_enforcesCooldownRateLimit() = runBlocking {
    val email = "rate.limit@inlove.app"

    // 1st request succeeds
    val res1 = emailService.enqueueVerificationEmail(email, OtpPurpose.REGISTRATION)
    assertTrue(res1.isSuccess)

    // 2nd request within 60s must fail due to cooldown
    val res2 = emailService.enqueueVerificationEmail(email, OtpPurpose.REGISTRATION)
    assertTrue(res2.isFailure)
    assertTrue(res2.exceptionOrNull()?.message?.contains("Vui lòng đợi") == true)

    // Cooldown seconds should be between 1 and 60
    val cooldown = emailService.getCooldownSeconds(email)
    assertTrue(cooldown in 1..60)
  }

  @Test
  fun verifyOtp_succeedsWithCorrectCode() = runBlocking {
    val email = "verify.success@inlove.app"
    val otp = emailService.enqueueVerificationEmail(email, OtpPurpose.REGISTRATION).getOrThrow()

    val (isSuccess, message) = emailService.verifyOtp(email, otp)
    assertTrue(isSuccess)
    assertTrue(message.contains("thành công"))

    // OTP should be consumed and no longer valid for replay attacks
    val replayAttempt = emailService.verifyOtp(email, otp)
    assertFalse(replayAttempt.first)
  }

  @Test
  fun verifyOtp_failsWithWrongCode_andEnforcesMaxAttempts() = runBlocking {
    val email = "verify.wrong@inlove.app"
    val realOtp = emailService.enqueueVerificationEmail(email, OtpPurpose.REGISTRATION).getOrThrow()

    // 1st wrong attempt
    val attempt1 = emailService.verifyOtp(email, "000000")
    assertFalse(attempt1.first)
    assertTrue(attempt1.second.contains("2 lần thử"))

    // 2nd wrong attempt
    val attempt2 = emailService.verifyOtp(email, "111111")
    assertFalse(attempt2.first)
    assertTrue(attempt2.second.contains("1 lần thử"))

    // 3rd wrong attempt - should lock/revoke OTP
    val attempt3 = emailService.verifyOtp(email, "222222")
    assertFalse(attempt3.first)
    assertTrue(attempt3.second.contains("quá 3 lần") || attempt3.second.contains("hủy"))
  }
}
