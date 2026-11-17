package com.example.platform

/** Bật hoặc tắt lớp che nội dung khi app ra nền; Android không có tính năng này. */
interface PrivacyCoverGateway {
  fun setCoverEnabled(enabled: Boolean)
}
