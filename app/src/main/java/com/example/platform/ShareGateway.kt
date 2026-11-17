package com.example.platform

/** Cầu nối chia sẻ văn bản qua hộp chia sẻ của hệ điều hành. */
interface ShareGateway {
  fun share(text: String, chooserTitle: String? = null)
}
