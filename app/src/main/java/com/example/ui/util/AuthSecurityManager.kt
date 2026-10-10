package com.example.ui.util

import java.security.MessageDigest
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import java.security.SecureRandom
import java.util.Locale

enum class PasswordStrengthLevel {
  VERY_WEAK,
  WEAK,
  MEDIUM,
  STRONG,
  VERY_STRONG
}

data class PasswordStrength(
  val level: PasswordStrengthLevel,
  val score: Float, // 0.0f to 1.0f
  val label: String,
  val colorHex: Long,
  val missingRequirements: List<String>
)

data class LockoutStatus(
  val isLocked: Boolean,
  val remainingSeconds: Long,
  val remainingAttempts: Int
)

object AuthSecurityManager {

  const val MAX_FAILED_ATTEMPTS = 5
  const val LOCKOUT_DURATION_MILLIS = 3 * 60 * 1000L // 3 minutes lockout
  private const val HASH_ITERATIONS = 2048
  private const val PIN_V2_ITERATIONS = 120_000
  const val PIN_V2_PREFIX = "v2\$"

  private val secureRandom = SecureRandom()

  val SECURITY_QUESTIONS = listOf(
    "Nơi đầu tiên hai bạn gặp gỡ hoặc hẹn hò?",
    "Tên thú cưng đầu tiên hoặc con vật yêu thích của hai bạn?",
    "Bài hát đặc biệt gắn liền với câu chuyện tình yêu của hai bạn?",
    "Món ăn yêu thích nhất của người ấy?",
    "Biệt danh bí mật mà bạn đặt cho người ấy?"
  )

  /**
   * Generates a cryptographically secure 16-byte random salt.
   */
  fun generateSalt(): String {
    val bytes = ByteArray(16)
    secureRandom.nextBytes(bytes)
    return bytes.toHexString()
  }

  /**
   * Generates a cryptographically secure 32-byte session token.
   */
  fun generateSessionToken(): String {
    val bytes = ByteArray(32)
    secureRandom.nextBytes(bytes)
    return bytes.toHexString()
  }

  /**
   * Generates a 6-digit numeric OTP code for 2-factor / password recovery.
   */
  fun generateOtpCode(): String {
    val num = secureRandom.nextInt(900000) + 100000
    return num.toString()
  }

  /**
   * Hashes a password with salt using SHA-256 and multiple iterations.
   */
  fun hashPassword(password: String, salt: String): String {
    val md = MessageDigest.getInstance("SHA-256")
    var combined = "$salt:$password".toByteArray(Charsets.UTF_8)
    for (i in 0 until HASH_ITERATIONS) {
      md.reset()
      md.update(combined)
      combined = md.digest()
    }
    return combined.toHexString()
  }

  /**
   * Hashes a security question answer (case-insensitive and trimmed).
   */
  fun hashSecurityAnswer(answer: String, salt: String): String {
    val normalized = answer.trim().lowercase(Locale.ROOT)
    return hashPassword(normalized, salt)
  }

  /**
   * Hashes a numeric PIN with salt to prevent storing plaintext PIN in Room database.
   */
  fun hashPin(pin: String, salt: String): String {
    return hashPassword("PIN_${pin.trim()}", salt)
  }

  /**
   * Slow PIN hash (PBKDF2, 120k iterations) with a version prefix. HmacSHA1 variant on purpose:
   * the HmacSHA256 variant only exists from API 26 and minSdk is 24, and the algorithm must not
   * change when the OS is upgraded or every stored PIN would stop matching.
   */
  fun hashPinV2(pin: String, salt: String): String {
    val spec = PBEKeySpec(pin.trim().toCharArray(), salt.toByteArray(Charsets.UTF_8), PIN_V2_ITERATIONS, 256)
    try {
      return PIN_V2_PREFIX + SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded.toHexString()
    } finally {
      spec.clearPassword()
    }
  }

  fun isPinV2(stored: String) = stored.startsWith(PIN_V2_PREFIX)

  /** Constant-time comparison for hashes and secrets. */
  fun secureEquals(a: String, b: String): Boolean =
    MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))

  /** Accepts v2 hashes, v1 salted-SHA256 hashes and legacy plaintext 4-digit PINs. */
  fun verifyPin(pin: String, salt: String, stored: String): Boolean = when {
    stored.isEmpty() -> false
    isPinV2(stored) -> secureEquals(hashPinV2(pin, salt), stored)
    stored.length == 4 -> secureEquals(pin.trim(), stored)
    else -> secureEquals(hashPin(pin, salt), stored)
  }

  /**
   * Validates standard email address syntax.
   */
  fun isValidEmail(email: String): Boolean {
    val trimmed = email.trim()
    if (trimmed.isEmpty() || trimmed.length > 254) return false
    val emailRegex = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\$".toRegex()
    return emailRegex.matches(trimmed)
  }

  /**
   * Evaluates password strength with detailed security feedback.
   */
  fun evaluatePasswordStrength(password: String): PasswordStrength {
    if (password.isEmpty()) {
      return PasswordStrength(
        level = PasswordStrengthLevel.VERY_WEAK,
        score = 0f,
        label = "Chưa nhập",
        colorHex = 0xFFB0BEC5,
        missingRequirements = listOf("Tối thiểu 8 ký tự", "Chữ hoa (A-Z)", "Chữ thường (a-z)", "Chữ số (0-9)", "Ký tự đặc biệt (@, #, $...)")
      )
    }

    val missing = mutableListOf<String>()
    var criteriaMet = 0

    if (password.length >= 8) {
      criteriaMet++
    } else {
      missing.add("Cần ít nhất 8 ký tự (hiện có ${password.length})")
    }

    if (password.any { it.isUpperCase() }) {
      criteriaMet++
    } else {
      missing.add("Thêm ít nhất 1 chữ hoa (A-Z)")
    }

    if (password.any { it.isLowerCase() }) {
      criteriaMet++
    } else {
      missing.add("Thêm ít nhất 1 chữ thường (a-z)")
    }

    if (password.any { it.isDigit() }) {
      criteriaMet++
    } else {
      missing.add("Thêm ít nhất 1 chữ số (0-9)")
    }

    val specialChars = "!@#$%^&*()_+-=[]{}|;':\",.<>?/~`"
    if (password.any { it in specialChars }) {
      criteriaMet++
    } else {
      missing.add("Thêm ký tự đặc biệt (!, @, #, $, %...)")
    }

    val score = (criteriaMet / 5f).coerceIn(0f, 1f)

    val (level, label, color) = when {
      criteriaMet <= 1 -> Triple(PasswordStrengthLevel.VERY_WEAK, "Rất yếu", 0xFFE53935)
      criteriaMet == 2 -> Triple(PasswordStrengthLevel.WEAK, "Yếu", 0xFFFF7043)
      criteriaMet == 3 -> Triple(PasswordStrengthLevel.MEDIUM, "Trung bình", 0xFFFFA726)
      criteriaMet == 4 -> Triple(PasswordStrengthLevel.STRONG, "Mạnh", 0xFF66BB6A)
      else -> Triple(PasswordStrengthLevel.VERY_STRONG, "Rất mạnh & An toàn", 0xFF8E24AA)
    }

    return PasswordStrength(
      level = level,
      score = score,
      label = label,
      colorHex = color,
      missingRequirements = missing
    )
  }

  /**
   * Checks whether an account is currently locked out due to failed attempts.
   */
  fun checkLockoutStatus(failedAttempts: Int, lockoutUntil: Long): LockoutStatus {
    val now = System.currentTimeMillis()
    if (lockoutUntil > now) {
      val remainingSeconds = ((lockoutUntil - now) / 1000).coerceAtLeast(1)
      return LockoutStatus(
        isLocked = true,
        remainingSeconds = remainingSeconds,
        remainingAttempts = 0
      )
    }
    val remainingAttempts = (MAX_FAILED_ATTEMPTS - failedAttempts).coerceAtLeast(0)
    return LockoutStatus(
      isLocked = false,
      remainingSeconds = 0,
      remainingAttempts = remainingAttempts
    )
  }

  /**
   * Masks email for privacy (e.g. h***g@gmail.com).
   */
  fun maskEmail(email: String): String {
    val atIndex = email.indexOf('@')
    if (atIndex <= 1) return email
    val namePart = email.substring(0, atIndex)
    val domainPart = email.substring(atIndex)
    val maskedName = if (namePart.length <= 2) {
      "${namePart.first()}*"
    } else {
      "${namePart.first()}${"*".repeat(namePart.length - 2)}${namePart.last()}"
    }
    return "$maskedName$domainPart"
  }

  private fun ByteArray.toHexString(): String {
    val sb = StringBuilder(this.size * 2)
    for (b in this) {
      sb.append(String.format("%02x", b))
    }
    return sb.toString()
  }
}
