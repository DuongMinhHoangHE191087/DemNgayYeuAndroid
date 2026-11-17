package com.example.link

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AppLinksTest {
  private fun pair(code: String) = DeepLink.Pair(code)

  @Test
  fun tabLinksParseCaseInsensitively() {
    assertEquals(DeepLink.Tab(DeepLinkTab.Home), AppLinks.parse("inlove://tab/home"))
    assertEquals(DeepLink.Tab(DeepLinkTab.Memories), AppLinks.parse("inlove://tab/MEMORIES"))
    assertEquals(DeepLink.Tab(DeepLinkTab.Calendar), AppLinks.parse("inlove://tab/CaLeNdAr"))
    assertEquals(DeepLink.Tab(DeepLinkTab.Gifts), AppLinks.parse("INLOVE://Tab/gIFTS"))
    assertEquals(DeepLink.Tab(DeepLinkTab.Settings), AppLinks.parse("inlove://TAB/Settings"))
  }

  @Test
  fun tabOrderIsTabIndex() {
    assertEquals(listOf("Home", "Memories", "Calendar", "Gifts", "Settings"), DeepLinkTab.entries.map { it.name })
  }

  @Test
  fun unknownTabIsRejected() {
    assertNull(AppLinks.parse("inlove://tab/profile"))
    assertNull(AppLinks.parse("inlove://tab/homes"))
    assertNull(AppLinks.parse("inlove://tab/"))
    assertNull(AppLinks.parse("inlove://tab"))
    assertNull(AppLinks.parse("inlove://tab/home/memories"))
    assertNull(AppLinks.parse("inlove://home"))
  }

  @Test
  fun tabLinkShapes() {
    assertEquals(DeepLink.Tab(DeepLinkTab.Gifts), AppLinks.parse("inlove://tab/gifts/"))
    assertEquals(DeepLink.Tab(DeepLinkTab.Gifts), AppLinks.parse("inlove://tab/gifts?x=1"))
    assertEquals(DeepLink.Tab(DeepLinkTab.Gifts), AppLinks.parse("inlove://tab/gifts#frag"))
    assertEquals(DeepLink.Tab(DeepLinkTab.Gifts), AppLinks.parse("inlove://tab/gifts/?x=1#frag"))
    assertNull(AppLinks.parse("inlove://tab/gifts//"))
    // Tab chi co o scheme inlove, khong co tren https.
    assertNull(AppLinks.parse("https://inlove.app/tab/home"))
  }

  @Test
  fun tabLinkOnlyOnCustomScheme() {
    for (s in listOf("https://tab/home", "http://tab/home", "evil://tab/home", "inlove.app://tab/home")) {
      assertNull(AppLinks.parse(s), s)
    }
  }

  @Test
  fun tabAndPairAuthorityMustMatchExactly() {
    val shapes = listOf(
      "inlove://tabs/home",
      "inlove://tabx/home",
      "inlove://tab.evil/home",
      "inlove://xtab/home",
      "inlove://pairs/LOVE-9966",
      "inlove://pair.evil/LOVE-9966",
      "inlove://pairx?code=LOVE-9966",
      "inlove://xpair/LOVE-9966",
    )
    for (s in shapes) assertNull(AppLinks.parse(s), s)
  }

  @Test
  fun nonAsciiLookalikeTabNameIsRejected() {
    // U+017F (long s) bi equals(ignoreCase) cua JVM coi la 's'; Kotlin/Native thi khong. Phai tu choi o moi nen tang.
    val longS = Char(0x17F)
    assertNull(AppLinks.parse("inlove://tab/${longS}ettings"))
  }

  @Test
  fun pairQueryLinkParsesAndUppercases() {
    assertEquals(pair("LOVE-AB12"), AppLinks.parse("https://inlove.app/pair?code=love-ab12"))
    assertEquals(pair("LOVE-AB12"), AppLinks.parse("HTTPS://InLove.App/Pair?CODE=love-Ab12"))
    assertEquals(pair("LOVE-AB12"), AppLinks.parse("https://inlove.app/pair?ref=share&code=love-ab12&x=1"))
    assertEquals(pair("LOVE-AB12"), AppLinks.parse("https://inlove.app/pair/?code=love-ab12"))
  }

  @Test
  fun pairPathLinkParses() {
    assertEquals(pair("LOVE-9966"), AppLinks.parse("https://inlove.app/pair/love-9966"))
    assertEquals(pair("LOVE-9966"), AppLinks.parse("https://inlove.app/pair/LOVE-9966/"))
    assertEquals(pair("MY_CODE-12"), AppLinks.parse("https://inlove.app/pair/my_code-12"))
  }

  @Test
  fun customSchemePairLinkParses() {
    assertEquals(pair("LOVE-9966"), AppLinks.parse("inlove://pair?code=love-9966"))
    assertEquals(pair("LOVE-9966"), AppLinks.parse("inlove://pair/love-9966"))
    assertEquals(pair("LOVE-9966"), AppLinks.parse("inlove://PAIR/love-9966"))
    assertEquals(pair("LOVE-9966"), AppLinks.parse("inlove://pair/love-9966/"))
  }

  @Test
  fun foreignHostIsRejected() {
    val shapes = listOf(
      "http://inlove.app/pair?code=LOVE-AB12",
      "https://www.inlove.app/pair?code=LOVE-AB12",
      "https://inlove.app.evil.com/pair?code=LOVE-AB12",
      "https://evil.inlove.app/pair?code=LOVE-AB12",
      "https://inlove.app@evil.com/pair?code=LOVE-AB12",
      "https://evil.com@inlove.app/pair?code=LOVE-AB12",
      "https://evil.com/?u=https://inlove.app/pair?code=LOVE-AB12",
      "https://evil.com/pair?code=LOVE-AB12",
      "https://inlove.app:8080/pair?code=LOVE-AB12",
      "https://inlove.app:443/pair?code=LOVE-AB12",
      "https://inlove.app./pair?code=LOVE-AB12",
      "ftp://inlove.app/pair?code=LOVE-AB12",
      "evil://pair?code=LOVE-AB12",
      "inlove://evil/pair?code=LOVE-AB12",
      "inlove://evil.com/pair/LOVE-AB12",
      "inlove.app/pair?code=LOVE-AB12",
      "//inlove.app/pair?code=LOVE-AB12",
      "javascript://inlove.app/pair?code=LOVE-AB12",
    )
    for (s in shapes) assertNull(AppLinks.parse(s), s)
  }

  @Test
  fun blankCodeIsRejected() {
    assertNull(AppLinks.parse("https://inlove.app/pair"))
    assertNull(AppLinks.parse("https://inlove.app/pair?code="))
    assertNull(AppLinks.parse("https://inlove.app/pair/"))
    assertNull(AppLinks.parse("https://inlove.app/pair?code=&x=1"))
    assertNull(AppLinks.parse("https://inlove.app"))
    assertNull(AppLinks.parse("https://inlove.app/"))
    assertNull(AppLinks.parse("inlove://pair"))
    assertNull(AppLinks.parse("inlove://pair/"))
    assertNull(AppLinks.parse("inlove://pair?code="))
    assertNull(AppLinks.parse("https://inlove.app/pair?code=%"))
    assertNull(AppLinks.parse(""))
    assertNull(AppLinks.parse("   \t\n "))
  }

  @Test
  fun rawCodeAndGarbageAreRejected() {
    // Khac extractCoupleCode: khong co fallback "bo ky tu la", parse tra null.
    assertNull(AppLinks.parse("LOVE-9966"))
    assertNull(AppLinks.parse("code=LOVE-9966"))
    assertNull(AppLinks.parse("!!! ??? ###"))
  }

  @Test
  fun queryWinsOverPathCode() {
    assertEquals(pair("BBBB"), AppLinks.parse("https://inlove.app/pair/AAAA?code=BBBB"))
    assertEquals(pair("BBBB"), AppLinks.parse("inlove://pair/AAAA?code=BBBB"))
    // Query rong thi roi ve path, giong extractCoupleCode.
    assertEquals(pair("AAAA"), AppLinks.parse("https://inlove.app/pair/AAAA?code="))
  }

  @Test
  fun extraPathSegmentsAreRejected() {
    assertNull(AppLinks.parse("https://inlove.app/pair/LOVE-9966/extra"))
    assertNull(AppLinks.parse("https://inlove.app/pair/LOVE-9966/extra?x=1"))
    assertNull(AppLinks.parse("https://inlove.app/pair/LOVE-9966//"))
    assertNull(AppLinks.parse("https://inlove.app/other/LOVE-9966"))
    assertNull(AppLinks.parse("https://inlove.app/other?code=LOVE-9966"))
    assertNull(AppLinks.parse("https://inlove.app/x/pair/LOVE-9966"))
    assertNull(AppLinks.parse("inlove://pair/LOVE-9966/extra"))
  }

  @Test
  fun fragmentAndQueryAreIgnoredForPathDecision() {
    assertEquals(pair("LOVE-9966"), AppLinks.parse("https://inlove.app/pair/love-9966#top"))
    assertEquals(pair("LOVE-9966"), AppLinks.parse("https://inlove.app/pair?code=love-9966#top"))
    assertEquals(pair("LOVE-9966"), AppLinks.parse("https://inlove.app/pair/love-9966?utm=x"))
    // Code nam trong fragment khong tinh.
    assertNull(AppLinks.parse("https://inlove.app/pair#code=LOVE-9966"))
    assertNull(AppLinks.parse("https://inlove.app/pair#?code=LOVE-9966"))
    // Fragment cat truoc: '/extra' trong fragment khong lam hong path.
    assertEquals(pair("LOVE-9966"), AppLinks.parse("https://inlove.app/pair/love-9966#/extra"))
  }

  @Test
  fun queryKeyWithoutValueIsNotACode() {
    assertNull(AppLinks.parse("https://inlove.app/pair?code"))
    assertNull(AppLinks.parse("https://inlove.app/pair?x=1&code"))
    assertNull(AppLinks.parse("inlove://pair?code&x=1"))
    // Khong co gia tri thi roi ve path.
    assertEquals(pair("AAAA"), AppLinks.parse("https://inlove.app/pair/AAAA?code"))
  }

  @Test
  fun secondQuestionMarkStartsAParam() {
    // Giong regex Android `[?&]code=`: '?' thu hai cung mo mot tham so.
    assertEquals(pair("LOVE-9966"), AppLinks.parse("https://inlove.app/pair?x=1?code=love-9966"))
  }

  @Test
  fun ampersandWithoutQuestionMarkIsNotAQuery() {
    // Co y chat hon extractCoupleCode (no se lay BBBB): '&' chi la dau phan tach sau '?'; khong co '?' thi '&code=' nam trong path.
    assertEquals(pair("AAAA"), AppLinks.parse("https://inlove.app/pair/AAAA&code=BBBB"))
    assertNull(AppLinks.parse("https://inlove.app/pair&code=BBBB"))
  }

  @Test
  fun percentEncodedCodeIsNotDecoded() {
    // Giong Android extractCoupleCode: regex dung o '%', khong giai ma %2D.
    assertEquals(pair("LOVE"), AppLinks.parse("https://inlove.app/pair?code=LOVE%2D9966"))
  }

  @Test
  fun surroundingWhitespaceIsTrimmed() {
    assertEquals(pair("LOVE-9966"), AppLinks.parse("  \thttps://inlove.app/pair?code=love-9966\n "))
    assertEquals(DeepLink.Tab(DeepLinkTab.Home), AppLinks.parse(" inlove://tab/home "))
  }

  @Test
  fun pairLinkMatchesAndroidFormat() {
    assertEquals("https://inlove.app/pair?code=LOVE-AB12", AppLinks.pairLink("LOVE-AB12"))
  }

  @Test
  fun tabLinkFormat() {
    assertEquals("inlove://tab/home", AppLinks.tabLink(DeepLinkTab.Home))
    assertEquals("inlove://tab/settings", AppLinks.tabLink(DeepLinkTab.Settings))
  }

  @Test
  fun roundTrips() {
    for (t in DeepLinkTab.entries) assertEquals(DeepLink.Tab(t), AppLinks.parse(AppLinks.tabLink(t)))
    assertEquals(pair("LOVE-AB12"), AppLinks.parse(AppLinks.pairLink("LOVE-AB12")))
  }
}
