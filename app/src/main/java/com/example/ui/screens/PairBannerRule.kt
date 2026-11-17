package com.example.ui.screens

import com.example.data.model.OnlineStatus

/**
 * Banner "Ghép đôi cùng người ấy" chỉ hiện khi chưa ghép đôi xong VÀ chưa nhập tên người ấy,
 * và chỉ sau khi trạng thái ghép đôi đã nạp xong ([loaded]) để khỏi chớp banner lúc khởi động lạnh.
 */
internal fun shouldShowPairBanner(status: String?, partnerName: String, loaded: Boolean): Boolean =
  loaded && status != OnlineStatus.COUPLED && partnerName.isBlank()
