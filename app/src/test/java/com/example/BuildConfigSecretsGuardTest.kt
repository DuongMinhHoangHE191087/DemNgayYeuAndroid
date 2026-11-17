package com.example

import org.junit.Assert.assertTrue
import org.junit.Test

/** Khoá máy chủ chỉ nằm ở firebase/functions và .env, không được vào APK qua BuildConfig. */
class BuildConfigSecretsGuardTest {
  private val forbidden = listOf("SECRET", "PASSWORD", "SMTP", "CLOUDINARY", "R2_", "PRIVATE_KEY", "API_SECRET")

  @Test
  fun buildConfigHasNoServerSecretFields() {
    val names = BuildConfig::class.java.declaredFields.map { it.name }
    assertTrue("BuildConfig phải có field (kiểm tra đúng lớp)", names.isNotEmpty())
    val leaked = names.filter { n -> forbidden.any { n.uppercase().contains(it) } }
    assertTrue("BuildConfig lộ khoá máy chủ: $leaked", leaked.isEmpty())
  }
}
