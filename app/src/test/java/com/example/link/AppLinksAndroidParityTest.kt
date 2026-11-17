package com.example.link

import com.example.ui.util.ProfileUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** AppLinks (chung) phai ra cung ma voi ProfileUtils.extractCoupleCode tren moi dang link duoc chap nhan. */
class AppLinksAndroidParityTest {
  @Test
  fun parseAgreesWithExtractCoupleCodeOnAllLinkShapes() {
    val accepted = listOf(
      "https://inlove.app/pair?code=LOVE-9966",
      "inlove://pair/LOVE-9966",
      "inlove://pair?code=LOVE-9966",
      "https://inlove.app/pair?code=LOVE%2D9966",
      "https://inlove.app/pair?ref=share&code=love-ab12&x=1",
      "HTTPS://InLove.App/Pair?CODE=love-Ab12",
      "inlove://PAIR/love-9966",
      "https://inlove.app/pair/AAAA?code=BBBB",
      "https://inlove.app/pair?x=1?code=love-9966",
      "https://inlove.app/pair/love-9966",
      "  https://inlove.app/pair?code=love-ab12 \n",
      ProfileUtils.generatePairLink("LOVE-9966"),
    )
    for (x in accepted) {
      val parsed = AppLinks.parse(x) as DeepLink.Pair
      assertEquals(ProfileUtils.extractCoupleCode(x), parsed.code, x)
    }
  }

  @Test
  fun rejectedShapesAreNotPairLinks() {
    // Cac ca golden ma extractCoupleCode van tra ma (fallback) nhung la link khong hop le: parse phai null.
    for (x in listOf("LOVE-9966", "code=LOVE-9966", "my_code-12", "!!! ??? ###", "", " \t\n  ")) {
      assertNull(AppLinks.parse(x), x)
    }
    // Duong dan thua: extractCoupleCode lay ma, parse tu choi (khong du tin cay).
    assertNull(AppLinks.parse("https://inlove.app/pair/love-9966/extra?x=1"))
  }

  @Test
  fun pairLinkEqualsGeneratePairLink() {
    for (c in listOf("LOVE-9966", "LOVE-AB12", "my_code-12", "")) {
      assertEquals(ProfileUtils.generatePairLink(c), AppLinks.pairLink(c))
    }
  }
}
