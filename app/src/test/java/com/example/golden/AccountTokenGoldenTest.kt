package com.example.golden

import com.example.data.db.AccountDataVault
import java.io.File
import java.text.Normalizer
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/**
 * GV-022: scope và token của tài khoản, ghi qua hai hàm cũ chưa đổi `AccountDataVault.scopeOf` và `tokenOf`.
 * Token đặt tên file stash `filesDir/vault/<token>.db`, nên đổi kết quả là làm mồ côi dữ liệu đã lưu.
 * JUnit thuần JVM, không Robolectric: hai hàm nằm trong companion và không cần Context.
 */
// Test ghi golden (matchesCommitted) phải chạy trước test đếm ca, để GOLDEN_UPDATE=1 chạy một lượt là đủ.
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class AccountTokenGoldenTest {
  private val goldenDir = File("../shared/src/commonTest/resources/golden")
  private val goldenName = "GV-022-account-token.json"
  private lateinit var previousLocale: Locale

  @Before fun pinLocale() {
    previousLocale = Locale.getDefault()
    Locale.setDefault(Locale.ROOT)
  }

  @After fun restoreLocale() {
    Locale.setDefault(previousLocale)
  }

  private fun obj(vararg pairs: Pair<String, Any?>): LinkedHashMap<String, Any?> = linkedMapOf(*pairs)

  // 20 email. Chữ tiếng Việt, U+0130, ß và emoji dùng escape Unicode để NFC và NFD không bị editor đổi.
  private val emails: List<Pair<String, String?>> = listOf(
    "ascii-lowercase" to "alice@example.com",
    "ascii-uppercase" to "BOB@EXAMPLE.COM",
    "ascii-mixed-case-padded" to "  Alice.Smith@Example.COM  ",
    "ascii-tab-newline-padded" to "\tcarol@example.com\n",
    "nbsp-padded" to "\u00a0erin@example.com\u00a0",
    "plus-tag-kept" to "alice+gift@example.com",
    "dots-not-canonicalized" to "A.L.I.C.E@gmail.com",
    "vietnamese-nfc" to "Nguy\u1ec5n.An@Example.vn",
    "vietnamese-nfd" to "Nguye\u0302\u0303n.An@Example.vn",
    "vietnamese-nfc-uppercase" to "NGUY\u1ec4N@EXAMPLE.VN",
    "vietnamese-nfd-uppercase" to "NGUYE\u0302\u0303N@EXAMPLE.VN",
    "turkish-dotted-capital-i-u0130" to "\u0130brahim@Example.TR",
    "german-sharp-s" to "STRA\u00dfE@Example.de",
    "cyrillic-uppercase" to "\u0410\u043b\u0438\u0441@Example.ru",
    "emoji-local-part" to "love\ud83d\udc96@Example.com",
    "zero-width-space-kept" to "\u200bfrank@example.com",
    "empty-string" to "",
    "null-email" to null,
    "blank-only" to "   ",
    "whitespace-only-mixed" to "\t \u00a0\u3000\n",
  )

  @Test
  fun accountTokenGolden_matchesCommitted() {
    assertEquals(20, emails.size)
    assertEquals(emails.size, emails.map { it.first }.toSet().size)
    val cases = emails.map { (id, email) ->
      val scope = AccountDataVault.scopeOf(email)
      obj(
        "id" to id,
        "input" to obj("email" to email),
        "expected" to obj("scope" to scope, "token" to AccountDataVault.tokenOf(scope)),
        "androidLegacy" to null,
        "divergence" to null,
        "optional" to false,
      )
    }
    GoldenWriter().check(goldenName, obj("schema" to 1L, "cases" to cases))
  }

  @Test
  fun accountTokenGolden_nfcAndNfdGiveDifferentTokens() {
    val nfc = "Nguy\u1ec5n@Example.vn"
    val nfd = "Nguye\u0302\u0303n@Example.vn"
    assertEquals(nfc, Normalizer.normalize(nfd, Normalizer.Form.NFC))
    assertNotEquals(nfc, nfd)
    // Quyết định: scopeOf không chuẩn hoá Unicode, vì chuẩn hoá sẽ làm mồ côi các stash filesDir/vault/<token>.db đã có.
    val tokenNfc = AccountDataVault.tokenOf(AccountDataVault.scopeOf(nfc))
    val tokenNfd = AccountDataVault.tokenOf(AccountDataVault.scopeOf(nfd))
    assertNotEquals(tokenNfc, tokenNfd)
  }

  @Test
  fun accountTokenGolden_everyTokenIs32LowerHex() {
    val hex = Regex("[0-9a-f]{32}")
    for ((id, email) in emails) {
      val token = AccountDataVault.tokenOf(AccountDataVault.scopeOf(email))
      assertTrue("$id: token không khớp [0-9a-f]{32}: $token", hex.matches(token))
    }
  }

  @Test
  fun accountTokenGolden_matchesCommittedCaseCount() {
    val file = File(goldenDir, goldenName)
    if (!file.isFile) throw AssertionError("Thiếu golden $goldenName tại ${file.path} (chạy với GOLDEN_UPDATE=1)")
    // Chỉ lấy `id` cấp ca: dòng thụt đúng 6 dấu cách trong khung cases.
    val ids = Regex("(?m)^ {6}\"id\": \"([^\"]+)\"").findAll(file.readText(Charsets.UTF_8)).map { it.groupValues[1] }.toList()
    assertEquals(20, ids.size)
    assertEquals(ids.size, ids.toSet().size)
  }
}
