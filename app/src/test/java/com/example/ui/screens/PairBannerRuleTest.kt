package com.example.ui.screens

import com.example.data.model.OnlineStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Kiểm tra quy tắc hiện banner "Ghép đôi cùng người ấy" trên trang chủ. */
class PairBannerRuleTest {

  // Đã ghép đôi thì không hiện banner, dù chưa có tên người ấy.
  @Test
  fun coupled_withBlankPartnerName_hidesBanner() {
    assertFalse(shouldShowPairBanner(OnlineStatus.COUPLED, "", loaded = true))
  }

  // Đã ghép đôi và có tên người ấy thì không hiện banner.
  @Test
  fun coupled_withPartnerName_hidesBanner() {
    assertFalse(shouldShowPairBanner(OnlineStatus.COUPLED, "Lan", loaded = true))
  }

  // Chưa ghép đôi nhưng đã nhập tên người ấy thì không hiện banner.
  @Test
  fun single_withPartnerName_hidesBanner() {
    assertFalse(shouldShowPairBanner(OnlineStatus.SINGLE, "Lan", loaded = true))
  }

  // Chưa ghép đôi và chưa nhập tên người ấy thì hiện banner.
  @Test
  fun single_withBlankPartnerName_showsBanner() {
    assertTrue(shouldShowPairBanner(OnlineStatus.SINGLE, "", loaded = true))
  }

  // Trạng thái null và chưa có tên người ấy thì vẫn hiện banner.
  @Test
  fun nullStatus_withBlankPartnerName_showsBanner() {
    assertTrue(shouldShowPairBanner(null, "", loaded = true))
  }

  // Tên chỉ toàn khoảng trắng được coi là chưa nhập, nên vẫn hiện banner.
  @Test
  fun single_withWhitespaceOnlyPartnerName_showsBanner() {
    assertTrue(shouldShowPairBanner(OnlineStatus.SINGLE, "   ", loaded = true))
  }

  // Chưa nạp xong trạng thái ghép đôi thì không bao giờ hiện banner, dù status/tên người ấy là gì.
  @Test
  fun notLoaded_neverShows() {
    assertFalse(shouldShowPairBanner(OnlineStatus.SINGLE, "", loaded = false))
    assertFalse(shouldShowPairBanner(OnlineStatus.COUPLED, "", loaded = false))
    assertFalse(shouldShowPairBanner(null, "", loaded = false))
  }
}
