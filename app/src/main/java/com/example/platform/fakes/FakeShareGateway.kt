package com.example.platform.fakes

import com.example.platform.ShareGateway

/** Cổng chia sẻ giả cho test: ghi lại mọi lần chia sẻ theo thứ tự. */
class FakeShareGateway : ShareGateway {
  data class Share(val text: String, val chooserTitle: String?)

  val shares = mutableListOf<Share>()

  override fun share(text: String, chooserTitle: String?) {
    shares += Share(text, chooserTitle)
  }
}
