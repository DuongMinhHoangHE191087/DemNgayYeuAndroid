package com.example.platform

/** Không làm gì: Android không có lớp che màn hình. */
class NoopPrivacyCoverGateway : PrivacyCoverGateway {
  override fun setCoverEnabled(enabled: Boolean) {}
}
