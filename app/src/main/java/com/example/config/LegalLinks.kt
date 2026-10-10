package com.example.config

import com.example.BuildConfig

/**
 * Public legal/support URLs. The domain comes from `LEGAL_BASE_URL` (.env / build) so the owner can
 * point the app at the real controlled HTTPS site; the three pages below must exist before release
 * (Play requires a working privacy policy and an account-deletion page).
 */
object LegalLinks {
  private val base: String get() = BuildConfig.LEGAL_BASE_URL.trimEnd('/')
  val privacyPolicy: String get() = "$base/privacy"
  val termsOfService: String get() = "$base/terms"
  val accountDeletion: String get() = "$base/delete-account"
  const val MANAGE_SUBSCRIPTIONS = "https://play.google.com/store/account/subscriptions?package=com.aistudio.inlove.kmrv"
}
