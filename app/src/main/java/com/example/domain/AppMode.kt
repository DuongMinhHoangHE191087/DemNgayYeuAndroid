package com.example.domain

/** Chế độ dùng app, hiển thị cho người dùng: khách / đã đăng nhập / đã ghép đôi, kèm online/offline. */
enum class AccountMode { GUEST, SIGNED_IN, PAIRED }

object AppMode {
  fun resolve(signedIn: Boolean, paired: Boolean): AccountMode = when {
    !signedIn -> AccountMode.GUEST // ghép đôi cần tài khoản; khách luôn là chế độ cục bộ
    paired -> AccountMode.PAIRED
    else -> AccountMode.SIGNED_IN
  }

  fun describe(mode: AccountMode, online: Boolean, english: Boolean): String {
    val base = when (mode) {
      AccountMode.GUEST -> if (english) "Guest — data stays on this device" else "Khách — dữ liệu chỉ lưu trên máy này"
      AccountMode.SIGNED_IN -> if (english) "Signed in — not paired yet" else "Đã đăng nhập — chưa ghép đôi"
      AccountMode.PAIRED -> if (english) "Paired with your partner" else "Đã ghép đôi với người ấy"
    }
    val net = when {
      mode == AccountMode.GUEST -> ""
      online -> if (english) " · Online" else " · Trực tuyến"
      else -> if (english) " · Offline, will sync later" else " · Ngoại tuyến, sẽ đồng bộ sau"
    }
    return base + net
  }
}
