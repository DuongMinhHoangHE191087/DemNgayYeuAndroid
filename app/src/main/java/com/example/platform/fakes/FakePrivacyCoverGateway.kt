package com.example.platform.fakes

import com.example.platform.PrivacyCoverGateway

/** Cổng che màn hình giả cho test: ghi lại mọi lần gọi, kể cả giá trị lặp lại. */
class FakePrivacyCoverGateway : PrivacyCoverGateway {
  val calls = mutableListOf<Boolean>()

  override fun setCoverEnabled(enabled: Boolean) {
    calls += enabled
  }
}
