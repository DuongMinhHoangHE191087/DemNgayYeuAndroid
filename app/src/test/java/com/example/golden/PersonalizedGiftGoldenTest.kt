package com.example.golden

import com.example.data.model.GiftIdeaEntity
import com.example.domain.content.GiftCatalog
import com.example.domain.usecase.GetPersonalizedGiftSuggestionsUseCase
import com.example.domain.usecase.PersonalizedGiftSuggestion
import com.example.domain.usecase.UpcomingOccasion
import java.io.File
import java.text.Normalizer
import java.time.LocalDate
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * GV-029: hàm thuần thực sự chọn gợi ý quà hiển thị, `GetPersonalizedGiftSuggestionsUseCase` (chọn tối đa
 * `MAX_RESULTS` = 3 quà, dịp trong `OCCASION_WINDOW_DAYS` = 30 ngày). Hàm đã nhận `today` nên không cần seam.
 *
 * `expected` = `occasion` (từ `nearestUpcomingOccasion`) + `results` (từng mục: id, cờ khớp, lý do, lời chuẩn bị,
 * nhãn giá, chữ của `gift`), lấy từ lời gọi thật. Quy ước D-53: `androidLegacy` + `divergence` chỉ điền khi hành vi
 * Android hiện tại khác `expected`. Riêng nhóm khoá sở thích NFD (D-04a, D-13 commit 1): `GiftRanker` hiện KHÔNG chuẩn
 * hoá Unicode; `expected` = kết quả trên bản NFC của quà lẫn khoá sở thích (chữ của `gift` luôn lấy từ ý tưởng gốc),
 * còn kết quả trên đầu vào thô vào `androidLegacy` khi khác. Chuỗi `input` giữ NGUYÊN điểm mã.
 *
 * Mỗi ý tưởng có `remoteId` cố định `golden-<id>` (không trùng seed) vì mặc định là ngẫu nhiên.
 *
 * Tết dùng bảng âm lịch phủ 2025..2040 (hành vi Android hiện có, không "sửa"). Giữ `today` trong 2026..2028, trừ ĐÚNG
 * MỘT ca cố ý ngoài bảng (`tet-out-of-table-2041`): khi bổ sung bảng cho năm đó, ca này phải được cập nhật có chủ ý.
 *
 * Phần lớn ca dùng `today` = 2026-09-10 (không dịp nào trong 30 ngày: 20/10 cách 40 ngày) để tự kiểm.
 */
class PersonalizedGiftGoldenTest {
  private val goldenName = "GV-029-gift-personalized.json"
  private val goldenDir = File("../shared/src/commonTest/resources/golden")
  private lateinit var previousLocale: Locale

  // Ghim tr-TR: kết quả phải y hệt Locale.ROOT, chứng minh không phụ thuộc locale máy.
  @Before fun pinLocale() {
    previousLocale = Locale.getDefault()
    Locale.setDefault(Locale.forLanguageTag("tr-TR"))
  }

  @After fun restoreLocale() {
    Locale.setDefault(previousLocale)
  }

  private val useCase = GetPersonalizedGiftSuggestionsUseCase

  private fun obj(vararg pairs: Pair<String, Any?>): LinkedHashMap<String, Any?> = linkedMapOf(*pairs)

  private fun d(s: String): LocalDate = LocalDate.parse(s)

  private fun nfc(s: String) = Normalizer.normalize(s, Normalizer.Form.NFC)

  private fun nfd(s: String) = Normalizer.normalize(s, Normalizer.Form.NFD)

  private class Idea(
    val id: Long,
    val title: String,
    val category: String,
    val tag: String,
    val description: String,
    val priceRange: String,
    val suggestedOccasion: String,
    val targetInterests: String,
    val isAiGenerated: Boolean,
  )

  private class GCase(
    val id: String,
    val ideas: List<Idea>,
    val today: LocalDate,
    val birthdays: List<LocalDate>,
    val loveStart: LocalDate?,
    val region: String,
    val isEnglish: Boolean,
    val interests: List<String>,
    val budgetMaxVnd: Long?,
  )

  private fun g(
    id: Long,
    title: String = "Qua $id",
    category: String = "Quà lãng mạn",
    tag: String = "Kỷ niệm",
    desc: String = "",
    price: String = "",
    occ: String = "",
    target: String = "",
    ai: Boolean = false,
  ) = Idea(id, title, category, tag, desc, price, occ, target, ai)

  private val today0 = d("2026-09-10")

  private fun c(
    id: String,
    ideas: List<Idea>,
    today: LocalDate = today0,
    birthdays: List<LocalDate> = emptyList(),
    loveStart: LocalDate? = null,
    region: String = "",
    en: Boolean = false,
    interests: List<String> = emptyList(),
    budget: Long? = null,
  ) = GCase(id, ideas, today, birthdays, loveStart, region, en, interests, budget)

  // ===== Bộ ý tưởng dùng lại =====

  // Mỗi khoảng giá một ý tưởng (giảm dần), cộng khoảng rỗng và khoảng lạ; tất cả hợp sở thích "budget" nên cùng điểm.
  private fun budgetIdeas() = listOf(
    g(1, price = ">1tr", target = "budget"),
    g(2, price = "500k-1tr", target = "budget"),
    g(3, price = "200k-500k", target = "budget"),
    g(4, price = "<200k", target = "budget"),
    g(5, price = "", target = "budget"),
    g(6, price = "100k-200k", target = "budget"),
  )

  // Chỉ có dịp lễ cố định mới làm ý tưởng hợp (không đặt sở thích).
  private fun regionIdeas() = listOf(
    g(1, occ = "20/10"),
    g(2, occ = "8/3"),
    g(3, occ = "Valentine"),
    g(4, occ = "Giáng Sinh"),
    g(5, occ = "Tết Nguyên Đán"),
    g(6, occ = "Bất kỳ dịp nào"),
    g(7, occ = "Sinh nhật"),
  )

  private fun occIdeas() = listOf(
    g(1, occ = "Sinh nhật"),
    g(2, occ = "Kỷ niệm ngày yêu"),
    g(3, occ = "Kỷ niệm 1 năm"),
    g(4, occ = "Valentine"),
    g(5, occ = "Bất kỳ dịp nào"),
  )

  private fun tetIdeas() = listOf(
    g(1, occ = "Tết Nguyên Đán"),
    g(2, occ = "Tết"),
    g(3, occ = "Bất kỳ dịp nào"),
    g(4, occ = "Valentine"),
  )

  private fun prepIdeas() = listOf(
    g(1, target = "xyz", occ = "Sinh nhật", price = "200k-500k", tag = "Kỷ niệm", desc = "Mo ta mot"),
    g(2, target = "xyz", price = "<200k", tag = "Hẹn hò", desc = "Mo ta hai"),
  )

  private fun prepCase(label: String, days: Int?, en: Boolean) = c(
    "prep-$label-${if (en) "en" else "vi"}",
    prepIdeas(),
    birthdays = if (days == null) emptyList() else listOf(today0.plusDays(days.toLong()).withYear(1995)),
    en = en,
    interests = listOf("xyz"),
  )

  private fun reasonCase(en: Boolean) = c(
    "reason-three-variants-${if (en) "en" else "vi"}",
    listOf(
      g(1, target = "xyz"),
      g(2, occ = "Sinh nhật"),
      g(3, target = "xyz", occ = "Sinh nhật"),
      g(4, title = "Hoa"),
    ),
    birthdays = listOf(d("1995-09-15")),
    en = en,
    interests = listOf("xyz"),
  )

  private val nfdSach = nfd("Sách hay")
  private val nfdDocSach = nfd("đọc sách")

  private val cases: List<GCase> = listOf(
    // Ngân sách: cùng một bộ ý tưởng; thấy được ranh giới nhờ cắt còn 3 mục theo thứ tự nhập.
    c("budget-0", budgetIdeas(), interests = listOf("budget"), budget = 0),
    c("budget-199999", budgetIdeas(), interests = listOf("budget"), budget = 199_999),
    c("budget-200000", budgetIdeas(), interests = listOf("budget"), budget = 200_000),
    c("budget-499999", budgetIdeas(), interests = listOf("budget"), budget = 499_999),
    c("budget-500000", budgetIdeas(), interests = listOf("budget"), budget = 500_000),
    c("budget-999999", budgetIdeas(), interests = listOf("budget"), budget = 999_999),
    c("budget-1000000", budgetIdeas(), interests = listOf("budget"), budget = 1_000_000),
    c("budget-null-unlimited", budgetIdeas(), interests = listOf("budget"), budget = null),
    c("budget-negative", budgetIdeas(), interests = listOf("budget"), budget = -1),

    // Vùng: chỉ "INTL" chính xác bỏ 8/3, 20/10 và Tết; rỗng, VN và chuỗi lạ cư xử như VN.
    c("region-empty-20-10", regionIdeas(), today = d("2026-10-15"), region = ""),
    c("region-vn-20-10", regionIdeas(), today = d("2026-10-15"), region = "VN"),
    c("region-intl-20-10-skipped", regionIdeas(), today = d("2026-10-15"), region = "INTL"),
    c("region-lowercase-intl-acts-as-vn", regionIdeas(), today = d("2026-10-15"), region = "intl"),
    c("region-unknown-acts-as-vn", regionIdeas(), today = d("2026-10-15"), region = "Europe"),
    c("region-empty-8-3", regionIdeas(), today = d("2027-02-20"), region = ""),
    c("region-intl-8-3-skipped", regionIdeas(), today = d("2027-02-20"), region = "INTL"),

    // Cửa sổ dịp: đúng 0, 30 và 31 ngày (31 ra "không có dịp").
    c("occasion-0-days", occIdeas(), birthdays = listOf(d("1995-09-10"))),
    c("occasion-30-days", occIdeas(), birthdays = listOf(d("1995-10-10"))),
    c("occasion-31-days-none", occIdeas(), birthdays = listOf(d("1995-10-11"))),

    // Tết: bảng âm lịch.
    c("tet-vn-17-days", tetIdeas(), today = d("2027-01-20")),
    c("tet-intl-falls-to-valentine", tetIdeas(), today = d("2027-01-20"), region = "INTL"),
    c("tet-30-days", tetIdeas(), today = d("2027-01-07")),
    c("tet-31-days-none", tetIdeas(), today = d("2027-01-06")),
    c("tet-out-of-table-2041", tetIdeas(), today = d("2041-01-30")),

    // Hoà ngày và thứ tự ứng viên: sinh nhật (theo thứ tự danh sách), kỷ niệm, lễ cố định, Tết.
    c("tie-birthday-beats-anniversary", occIdeas(), birthdays = listOf(d("1995-09-20")), loveStart = d("2020-09-20")),
    c("tie-two-birthdays-same-day", occIdeas(), birthdays = listOf(d("1995-09-20"), d("1996-09-20"))),
    c("nearer-birthday-wins-regardless-of-order", occIdeas(), birthdays = listOf(d("1995-09-25"), d("1995-09-15"))),
    c("anniversary-nearer-than-birthday", occIdeas(), birthdays = listOf(d("1995-09-25")), loveStart = d("2020-09-15")),
    c("tie-birthday-beats-fixed-20-10", occIdeas(), today = d("2026-10-15"), birthdays = listOf(d("1995-10-20"))),
    c("tie-anniversary-beats-fixed-20-10", occIdeas(), today = d("2026-10-15"), loveStart = d("2020-10-20")),
    c("tie-anniversary-beats-tet", occIdeas() + tetIdeas().map { g(it.id + 10, occ = it.suggestedOccasion) }, today = d("2027-01-20"), loveStart = d("2020-02-06")),

    // Lễ cố định khác.
    c("fixed-valentine-9-days", occIdeas(), today = d("2028-02-05")),
    c("fixed-christmas-eve-nearest", listOf(g(1, occ = "Giáng Sinh"), g(2, occ = "Bất kỳ dịp nào")), today = d("2026-12-10")),
    c("fixed-christmas-day-0-days", listOf(g(1, occ = "Giáng Sinh"), g(2, occ = "Bất kỳ dịp nào")), today = d("2026-12-25")),

    // 29-02 ở năm nhuận và năm thường (kẹp về 28-02).
    c("leap-birthday-in-leap-year", occIdeas(), today = d("2028-02-20"), birthdays = listOf(d("2000-02-29"))),
    c("leap-birthday-in-common-year-clamped", occIdeas(), today = d("2027-02-20"), birthdays = listOf(d("2000-02-29"))),
    c("leap-birthday-common-year-today-is-28", occIdeas(), today = d("2027-02-28"), birthdays = listOf(d("2000-02-29"))),
    c("leap-lovestart-in-leap-year", occIdeas(), today = d("2028-02-20"), loveStart = d("2024-02-29")),
    c("leap-lovestart-in-common-year-clamped", occIdeas(), today = d("2027-02-20"), loveStart = d("2024-02-29")),

    // Tên kỷ niệm: đúng 1 năm so với các năm sau (và ngày yêu ở tương lai cùng năm).
    c("anniversary-exactly-1-year", occIdeas(), loveStart = d("2025-09-25")),
    c("anniversary-1-year-today", occIdeas(), loveStart = d("2025-09-10")),
    c("anniversary-later-years", occIdeas(), loveStart = d("2023-09-25")),
    c("anniversary-start-in-future-same-year", occIdeas(), loveStart = d("2026-09-25")),

    // Cắt còn 3 mục và thứ tự ổn định khi hoà điểm (id không theo thứ tự).
    c("stable-ties-ids-7-3-9-1-5", listOf(g(7, target = "xyz"), g(3, target = "xyz"), g(9, target = "xyz"), g(1, target = "xyz"), g(5, target = "xyz")), interests = listOf("xyz")),
    c(
      "cut-to-three-of-seven-eligible",
      listOf(g(4, target = "xyz"), g(8, target = "xyz"), g(2, target = "xyz"), g(6, target = "xyz"), g(1, target = "xyz"), g(3, target = "xyz"), g(5, target = "xyz")),
      interests = listOf("xyz"),
    ),
    c(
      "cut-to-three-by-score",
      listOf(
        g(1, title = "xyz"),
        g(2, target = "xyz"),
        g(3, title = "xyz", target = "xyz"),
        g(4, title = "xyz", ai = true),
        g(5, target = "xyz"),
        g(6, title = "Hoa"),
      ),
      interests = listOf("xyz"),
    ),
    c("fewer-than-three-eligible", listOf(g(2, target = "xyz"), g(1, title = "Hoa"), g(3, ai = true)), interests = listOf("xyz")),

    // Mở rộng khoá sở thích.
    c(
      "interest-coffee-expands-vi-en",
      listOf(g(1, title = "Ly cà phê đôi"), g(2, title = "Coffee mug"), g(3, desc = "Voucher cafe"), g(4, title = "Sách hay")),
      interests = listOf("Coffee"),
    ),
    c(
      "interest-coffee-expanded-keys-match-target",
      listOf(g(1, target = "cafe"), g(2, target = "cà phê"), g(3, target = "coffee"), g(4, target = "coffee beans")),
      interests = listOf("coffee"),
    ),
    c(
      "interest-unknown-key-kept",
      listOf(g(1, target = "lego"), g(2, title = "Bộ Lego"), g(3, title = "Gấu bông")),
      interests = listOf("lego"),
    ),
    c("interest-blank-keys-dropped", listOf(g(1, ai = true), g(2, title = "Hoa"), g(3, target = "")), interests = listOf("", "   ", "\t")),
    c(
      "interest-blank-mixed-with-real-key",
      listOf(g(1, title = "Hoa"), g(2, target = "music")),
      interests = listOf("", " MUSIC ", "  "),
    ),
    c(
      "interest-case-and-trim-expands",
      listOf(g(1, desc = "Đĩa âm nhạc"), g(2, title = "Vé xem phim"), g(3, title = "Movie night"), g(4, target = "cinema"), g(5, title = "Hoa")),
      interests = listOf(" Music ", "CINEMA", "Cinema"),
    ),
    c(
      "interest-keyword-table-vi-1",
      listOf(g(1, title = "Chuyến du lịch"), g(2, title = "Gói công nghệ"), g(3, title = "Xe đạp đôi"), g(4, title = "Hoa")),
      interests = listOf("travel", "technology", "cycling"),
    ),
    c(
      "interest-keyword-table-vi-2",
      listOf(g(1, title = "Áo thời trang"), g(2, title = "Đĩa âm nhạc"), g(3, title = "Vé điện ảnh"), g(4, title = "Hoa")),
      interests = listOf("fashion", "music", "cinema"),
    ),
    c(
      "interest-keyword-table-vi-3",
      listOf(g(1, title = "Lớp nấu ăn"), g(2, title = "Tập sách"), g(3, title = "Đêm chơi game"), g(4, title = "Hoa")),
      interests = listOf("cooking", "books", "gaming"),
    ),
    c(
      "interest-keyword-table-en-1",
      listOf(g(1, title = "Travel kit"), g(2, title = "Technology set"), g(3, title = "Bike lights"), g(4, title = "Hoa")),
      interests = listOf("travel", "technology", "cycling"),
      en = true,
    ),
    c(
      "interest-keyword-table-en-2",
      listOf(g(1, title = "Fashion bag"), g(2, title = "Music box"), g(3, title = "Movie night"), g(4, title = "Hoa")),
      interests = listOf("fashion", "music", "cinema"),
      en = true,
    ),
    c(
      "interest-keyword-table-en-3",
      listOf(g(1, title = "Cooking class"), g(2, title = "Books set"), g(3, title = "Board game night"), g(4, title = "Hoa")),
      interests = listOf("cooking", "books", "gaming"),
      en = true,
    ),

    // Ý tưởng "bất kỳ dịp nào" (chuỗi "bất kỳ" hoặc "any"; "Company" cũng chứa "any").
    c(
      "occasion-any-vi-and-en",
      listOf(g(1, occ = "Bất kỳ dịp nào"), g(2, occ = "Any occasion"), g(3, occ = "Sinh nhật 18"), g(4, occ = "Valentine")),
      birthdays = listOf(d("1995-09-15")),
    ),
    c(
      "occasion-any-substring-and-case",
      listOf(g(1, occ = "Company event"), g(2, occ = "ANY"), g(3, occ = "bất kỳ dịp nào"), g(4, occ = "Sinh nhật 18")),
      birthdays = listOf(d("1995-09-15")),
    ),
    c(
      "occasion-none-any-ideas-excluded",
      listOf(g(1, occ = "Bất kỳ dịp nào"), g(2, occ = "Any occasion"), g(3, occ = "Sinh nhật")),
    ),

    // NFD so với NFC. Mã Android chưa chuẩn hoá, nên ca khác nhau mang androidLegacy + divergence.
    c(
      "nfd-idea-text-vs-nfc-keyword",
      listOf(g(1, title = nfdSach), g(2, title = "Sách hay")),
      interests = listOf("books"),
    ),
    c(
      "nfd-interest-key-vs-nfc-idea",
      listOf(g(1, target = "đọc sách"), g(2, desc = "Thích đọc sách"), g(3, title = "Hoa")),
      interests = listOf(nfdDocSach),
    ),
    c(
      "nfd-both-sides-decomposed",
      listOf(g(1, target = nfdDocSach), g(2, title = "Hoa")),
      interests = listOf(nfdDocSach),
    ),
    c(
      "nfc-both-sides",
      listOf(g(1, target = "đọc sách"), g(2, title = "Hoa")),
      interests = listOf("đọc sách"),
    ),

    // Vòng qua năm mới: ngày đã qua trong năm nay nhảy sang năm sau nhưng vẫn trong cửa sổ 30 ngày.
    c("year-wrap-birthday-jan-5", occIdeas(), today = d("2026-12-27"), birthdays = listOf(d("1995-01-05"))),
    c("year-wrap-first-anniversary", occIdeas(), today = d("2026-12-27"), loveStart = d("2026-01-05")),

    // Điểm dịp (+2) và AI (+1) cạnh tranh với điểm sở thích: đổi trọng số nào cũng đổi thứ tự.
    c(
      "score-occasion-and-ai-weights",
      listOf(
        g(1, title = "xyz"),
        g(2, occ = "Sinh nhật"),
        g(3, occ = "Sinh nhật", ai = true),
        g(4, title = "xyz", ai = true),
        g(5, title = "xyz", target = "xyz"),
      ),
      birthdays = listOf(d("1995-09-15")),
      interests = listOf("xyz"),
    ),

    // Danh sách nhãn đối tượng: tách theo dấu phẩy, cắt khoảng trắng, không phân biệt hoa/thường, khớp nguyên nhãn.
    c(
      "target-list-comma-split-trim-and-case",
      listOf(
        g(1, target = "lego,coffee"),
        g(2, target = "  coffee  ,  Music "),
        g(3, target = "coffee beans, tea"),
        g(4, target = "COFFEE"),
        g(5, target = "a;coffee"),
      ),
      interests = listOf("coffee"),
    ),

    // Chữ của quà gồm title, tag, category, description nối bằng dấu cách (không khớp xuyên trường).
    c(
      "text-fields-and-field-separator",
      listOf(
        g(1, title = "xy", tag = "z"),
        g(2, tag = "xy", category = "z"),
        g(3, category = "xy", desc = "z"),
        g(4, tag = "xyz"),
        g(5, category = "xyz"),
        g(6, desc = "xyz"),
      ),
      interests = listOf("xyz"),
    ),

    // Ba dạng lý do ở hai ngôn ngữ.
    reasonCase(false),
    reasonCase(true),

    // 5 dải lời chuẩn bị x 2 ngôn ngữ (null, 0, 1..3, 4..14, 15..30), cộng các ranh giới 3/4 và 14/15.
    prepCase("null", null, false),
    prepCase("null", null, true),
    prepCase("0", 0, false),
    prepCase("0", 0, true),
    prepCase("2", 2, false),
    prepCase("2", 2, true),
    prepCase("10", 10, false),
    prepCase("10", 10, true),
    prepCase("25", 25, false),
    prepCase("25", 25, true),
    prepCase("3", 3, false),
    prepCase("4", 4, true),
    prepCase("14", 14, false),
    prepCase("15", 15, true),
  )

  // ===== Dựng entity và kết quả =====

  private fun entity(i: Idea) = GiftIdeaEntity(
    id = i.id,
    title = i.title,
    category = i.category,
    badgeText = "",
    tag = i.tag,
    description = i.description,
    imageUrl = "",
    isAiGenerated = i.isAiGenerated,
    targetInterests = i.targetInterests,
    suggestedOccasion = i.suggestedOccasion,
    priceRange = i.priceRange,
    remoteId = "golden-${i.id}",
  )

  // Chuẩn hoá NFC chữ của quà; KHÔNG đụng `suggestedOccasion` (chuỗi dịp không chuẩn hoá, như GV-016).
  private fun nfcEntity(e: GiftIdeaEntity) = e.copy(
    title = nfc(e.title),
    category = nfc(e.category),
    tag = nfc(e.tag),
    description = nfc(e.description),
    targetInterests = nfc(e.targetInterests),
  )

  private fun real(c: GCase): List<PersonalizedGiftSuggestion> = useCase(
    c.ideas.map(::entity), LinkedHashSet(c.interests), c.budgetMaxVnd, c.birthdays, c.loveStart, c.today, c.isEnglish, c.region,
  )

  private fun occasionOf(c: GCase): UpcomingOccasion? = useCase.nearestUpcomingOccasion(c.today, c.birthdays, c.loveStart, c.region)

  private fun ids(c: GCase): List<Long> = real(c).map { it.idea.id }

  private fun caseById(id: String): GCase = cases.first { it.id == id }

  private fun giftMap(g: GiftCatalog.LocalizedGift) = obj(
    "categoryKey" to g.categoryKey,
    "title" to g.title,
    "badge" to g.badge,
    "tag" to g.tag,
    "description" to g.description,
    "detailsSnippet" to g.detailsSnippet,
    "actionText" to g.actionText,
    "isSeed" to g.isSeed,
  )

  // normalized = true: hàm chạy trên bản NFC của quà và khoá sở thích; chữ `gift` luôn lấy lại từ ý tưởng gốc.
  private fun outcome(c: GCase, normalized: Boolean): Map<String, Any?> {
    val raw = c.ideas.map(::entity)
    val rawById = raw.associateBy { it.id }
    val used = if (normalized) raw.map(::nfcEntity) else raw
    val interests = LinkedHashSet(if (normalized) c.interests.map(::nfc) else c.interests)
    val results = useCase(used, interests, c.budgetMaxVnd, c.birthdays, c.loveStart, c.today, c.isEnglish, c.region)
    val occ = occasionOf(c)
    return obj(
      "occasion" to occ?.let { obj("name" to it.name, "date" to it.date.toString(), "daysUntil" to it.daysUntil.toLong()) },
      "results" to results.map { r ->
        obj(
          "id" to r.idea.id,
          "matchesInterest" to r.matchesInterest,
          "fitsOccasion" to r.fitsOccasion,
          "reason" to r.reason,
          "preparation" to r.preparation,
          "priceLabel" to r.priceLabel,
          "gift" to giftMap(GiftCatalog.localized(rawById.getValue(r.idea.id), c.isEnglish)),
        )
      },
    )
  }

  private fun ideaMap(i: Idea) = obj(
    "id" to i.id,
    "title" to i.title,
    "category" to i.category,
    "tag" to i.tag,
    "description" to i.description,
    "priceRange" to i.priceRange,
    "suggestedOccasion" to i.suggestedOccasion,
    "targetInterests" to i.targetInterests,
    "isAiGenerated" to i.isAiGenerated,
  )

  private val divergenceLabel = "interest-key-not-nfc-normalised"

  private fun caseMaps(): List<Map<String, Any?>> = cases.map { c ->
    val rawRun = outcome(c, normalized = false)
    val expected = outcome(c, normalized = true)
    val diverges = rawRun != expected
    obj(
      "id" to c.id,
      "input" to obj(
        "today" to c.today.toString(),
        "birthdays" to c.birthdays.map { it.toString() },
        "loveStart" to c.loveStart?.toString(),
        "region" to c.region,
        "isEnglish" to c.isEnglish,
        "interests" to c.interests,
        "budgetMaxVnd" to c.budgetMaxVnd,
        "ideas" to c.ideas.map(::ideaMap),
      ),
      "expected" to expected,
      "androidLegacy" to if (diverges) rawRun else null,
      "divergence" to if (diverges) divergenceLabel else null,
      "optional" to false,
    )
  }

  private val divergentIds = listOf("nfd-idea-text-vs-nfc-keyword", "nfd-interest-key-vs-nfc-idea")

  @Test
  fun personalizedGiftGolden_matchesCommitted() {
    val maps = caseMaps()
    val ids = maps.map { it["id"] as String }
    assertEquals(ids.size, ids.toSet().size)
    assertTrue("Chỉ có ${ids.size} ca", ids.size >= 40)
    // Mỗi ca dùng id ý tưởng duy nhất, nếu không tra theo id trong `outcome` bị lẫn.
    for (c in cases) assertEquals("${c.id}: id ý tưởng trùng", c.ideas.size, c.ideas.map { it.id }.toSet().size)

    GoldenWriter().check(goldenName, obj("schema" to 1L, "cases" to maps))

    val file = File(goldenDir, goldenName)
    assertTrue("Thiếu golden $goldenName", file.isFile)
    val text = file.readText(Charsets.UTF_8).replace("\r\n", "\n")
    // Chỉ lấy `id` cấp ca: dòng thụt đúng 6 dấu cách trong khung cases.
    val committedIds = Regex("(?m)^ {6}\"id\": \"([^\"]+)\"").findAll(text).map { it.groupValues[1] }.toList()
    assertEquals(ids, committedIds)
    assertEquals(ids.size, Regex("(?m)^ {6}\"divergence\": ").findAll(text).count())
    assertEquals(divergentIds.size, Regex("(?m)^ {6}\"divergence\": \"").findAll(text).count())
    assertEquals(ids.size, Regex("(?m)^ {6}\"optional\": false").findAll(text).count())
  }

  @Test
  fun personalizedGiftGolden_divergenceOnlyOnNfdCases() {
    val maps = caseMaps()
    assertEquals(divergentIds, maps.filter { it["divergence"] != null }.map { it["id"] })
    for (m in maps) {
      assertEquals("${m["id"]}: divergence và androidLegacy phải đi cùng nhau", m["divergence"] != null, m["androidLegacy"] != null)
      if (m["divergence"] != null) assertTrue(m["expected"] != m["androidLegacy"])
    }
    // Hai ca NFD còn lại không lệch: cả hai phía cùng tách dấu, hoặc cùng NFC.
    val byId = maps.associateBy { it["id"] }
    assertNull(byId.getValue("nfd-both-sides-decomposed")["androidLegacy"])
    assertNull(byId.getValue("nfc-both-sides")["androidLegacy"])
  }

  // Hàm thật chạy trên đầu vào thô phải bằng `expected` hoặc `androidLegacy`.
  @Test
  fun personalizedGiftRealCode_agreesWithExpectedOrAndroidLegacy() {
    for (m in caseMaps()) {
      val c = caseById(m["id"] as String)
      val actual = outcome(c, normalized = false)
      assertTrue("${c.id}: mã thật không bằng expected hay androidLegacy", actual == m["expected"] || actual == m["androidLegacy"])
      val androidIds = (actual["results"] as List<*>).map { (it as Map<*, *>)["id"] }
      assertEquals(real(c).map { it.idea.id }, androidIds)
    }
  }

  @Test
  fun personalizedGiftGolden_nfdFixturesAreReallyDecomposed() {
    for (s in listOf(nfdSach, nfdDocSach)) {
      assertTrue("fixture NFD không được bằng NFC: $s", s != nfc(s) && s.length > nfc(s).length)
      assertTrue("fixture thiếu dấu rời U+0300..U+036F", s.codePoints().anyMatch { it in 0x0300..0x036f })
    }
    assertEquals("Sách hay", nfc(nfdSach))
    assertEquals("đọc sách", nfc(nfdDocSach))
  }

  // 5 dải lời chuẩn bị phải xuất hiện ở cả hai ngôn ngữ (10 tổ hợp).
  @Test
  fun personalizedGiftGolden_coversEveryPreparationBandInBothLanguages() {
    fun band(days: Long?): String = when {
      days == null -> "null"
      days == 0L -> "0"
      days <= 3 -> "1-3"
      days <= 14 -> "4-14"
      else -> "15-30"
    }
    val seen = mutableSetOf<Pair<String, Boolean>>()
    for (c in cases) {
      val results = real(c)
      if (results.isEmpty()) continue
      seen += band(occasionOf(c)?.daysUntil?.toLong()) to c.isEnglish
    }
    for (b in listOf("null", "0", "1-3", "4-14", "15-30")) {
      for (en in listOf(false, true)) assertTrue("Thiếu dải $b (en=$en)", (b to en) in seen)
    }
    // Mỗi dải cho ra một lời khác nhau trong từng ngôn ngữ.
    for (en in listOf(false, true)) {
      val texts = listOf("null", "0", "2", "10", "25").map { caseById("prep-$it-${if (en) "en" else "vi"}").let { c -> real(c).first().preparation } }
      assertEquals(5, texts.toSet().size)
    }
  }

  // ===== Khẳng định viết tay (không lặp lại hàm) =====

  @Test
  fun hand_occasionAt30DaysIsAcceptedAnd31IsNot() {
    assertEquals(UpcomingOccasion("Sinh nhật", d("2026-09-10"), 0), occasionOf(caseById("occasion-0-days")))
    assertEquals(UpcomingOccasion("Sinh nhật", d("2026-10-10"), 30), occasionOf(caseById("occasion-30-days")))
    assertNull(occasionOf(caseById("occasion-31-days-none")))
    assertEquals(listOf(1L, 5L), ids(caseById("occasion-30-days")))
    assertEquals(listOf(5L), ids(caseById("occasion-31-days-none")))
    assertEquals(30, GetPersonalizedGiftSuggestionsUseCase.OCCASION_WINDOW_DAYS)
    // Tết cũng đúng cửa sổ: 2027-02-06 cách 30 ngày vào, cách 31 ngày thì không.
    assertEquals(UpcomingOccasion("Tết Nguyên Đán", d("2027-02-06"), 30), occasionOf(caseById("tet-30-days")))
    assertEquals(null, occasionOf(caseById("tet-31-days-none")))
    assertEquals(listOf(3L), ids(caseById("tet-31-days-none")))
    assertEquals(listOf(1L, 2L), ids(caseById("occasion-none-any-ideas-excluded")))
  }

  @Test
  fun hand_atMostThreeResultsWhenFiveOrMoreEligible() {
    assertEquals(3, GetPersonalizedGiftSuggestionsUseCase.MAX_RESULTS)
    assertEquals(listOf(4L, 8L, 2L), ids(caseById("cut-to-three-of-seven-eligible")))
    assertEquals(3, ids(caseById("budget-null-unlimited")).size)
    // Điểm: 3 (title+target)=4, 2 và 5 (target)=3, 4 (title+AI)=2, 1 (title)=1.
    assertEquals(listOf(3L, 2L, 5L), ids(caseById("cut-to-three-by-score")))
    assertEquals(listOf(2L), ids(caseById("fewer-than-three-eligible")))
  }

  @Test
  fun hand_budgetBoundaries() {
    assertEquals(listOf(4L, 5L, 6L), ids(caseById("budget-0")))
    assertEquals(listOf(4L, 5L, 6L), ids(caseById("budget-199999")))
    assertEquals(listOf(3L, 4L, 5L), ids(caseById("budget-200000")))
    assertEquals(listOf(3L, 4L, 5L), ids(caseById("budget-499999")))
    assertEquals(listOf(2L, 3L, 4L), ids(caseById("budget-500000")))
    assertEquals(listOf(2L, 3L, 4L), ids(caseById("budget-999999")))
    assertEquals(listOf(1L, 2L, 3L), ids(caseById("budget-1000000")))
    assertEquals(listOf(1L, 2L, 3L), ids(caseById("budget-null-unlimited")))
    // Ngân sách âm loại mọi khoảng giá đã biết, chỉ giữ khoảng rỗng và lạ.
    assertEquals(listOf(5L, 6L), ids(caseById("budget-negative")))
  }

  @Test
  fun hand_stableOrderOnTies() {
    assertEquals(listOf(7L, 3L, 9L), ids(caseById("stable-ties-ids-7-3-9-1-5")))
  }

  @Test
  fun hand_birthdayWinsTieBeforeAnniversary() {
    val tie = caseById("tie-birthday-beats-anniversary")
    assertEquals(UpcomingOccasion("Sinh nhật", d("2026-09-20"), 10), occasionOf(tie))
    assertEquals(UpcomingOccasion("Sinh nhật", d("2026-09-15"), 5), occasionOf(caseById("nearer-birthday-wins-regardless-of-order")))
    assertEquals(UpcomingOccasion("Kỷ niệm ngày yêu", d("2026-09-15"), 5), occasionOf(caseById("anniversary-nearer-than-birthday")))
    assertEquals("Sinh nhật", occasionOf(caseById("tie-birthday-beats-fixed-20-10"))?.name)
    assertEquals("Kỷ niệm ngày yêu", occasionOf(caseById("tie-anniversary-beats-fixed-20-10"))?.name)
    assertEquals("Kỷ niệm ngày yêu", occasionOf(caseById("tie-anniversary-beats-tet"))?.name)
  }

  @Test
  fun hand_leapDayClampsToFeb28InCommonYear() {
    assertEquals(UpcomingOccasion("Sinh nhật", d("2028-02-29"), 9), occasionOf(caseById("leap-birthday-in-leap-year")))
    assertEquals(UpcomingOccasion("Sinh nhật", d("2027-02-28"), 8), occasionOf(caseById("leap-birthday-in-common-year-clamped")))
    assertEquals(UpcomingOccasion("Sinh nhật", d("2027-02-28"), 0), occasionOf(caseById("leap-birthday-common-year-today-is-28")))
    assertEquals(UpcomingOccasion("Kỷ niệm ngày yêu", d("2028-02-29"), 9), occasionOf(caseById("leap-lovestart-in-leap-year")))
    assertEquals(UpcomingOccasion("Kỷ niệm ngày yêu", d("2027-02-28"), 8), occasionOf(caseById("leap-lovestart-in-common-year-clamped")))
  }

  @Test
  fun hand_anniversaryNames() {
    assertEquals(UpcomingOccasion("Kỷ niệm 1 năm", d("2026-09-25"), 15), occasionOf(caseById("anniversary-exactly-1-year")))
    assertEquals(UpcomingOccasion("Kỷ niệm 1 năm", d("2026-09-10"), 0), occasionOf(caseById("anniversary-1-year-today")))
    assertEquals(UpcomingOccasion("Kỷ niệm ngày yêu", d("2026-09-25"), 15), occasionOf(caseById("anniversary-later-years")))
    // Ngày bắt đầu ở tương lai cùng năm: chênh năm 0 nên mang tên "Kỷ niệm ngày yêu".
    assertEquals("Kỷ niệm ngày yêu", occasionOf(caseById("anniversary-start-in-future-same-year"))?.name)
  }

  @Test
  fun hand_regionOnlyExactIntlSkipsVietnamOnlyOccasions() {
    assertEquals(UpcomingOccasion("20/10", d("2026-10-20"), 5), occasionOf(caseById("region-empty-20-10")))
    assertEquals(UpcomingOccasion("20/10", d("2026-10-20"), 5), occasionOf(caseById("region-vn-20-10")))
    assertNull(occasionOf(caseById("region-intl-20-10-skipped")))
    assertEquals("20/10", occasionOf(caseById("region-lowercase-intl-acts-as-vn"))?.name)
    assertEquals("20/10", occasionOf(caseById("region-unknown-acts-as-vn"))?.name)
    assertEquals("8/3", occasionOf(caseById("region-empty-8-3"))?.name)
    assertNull(occasionOf(caseById("region-intl-8-3-skipped")))
    assertEquals(listOf(6L), ids(caseById("region-intl-8-3-skipped")))
    assertEquals(listOf(1L, 6L), ids(caseById("region-vn-20-10")))
    assertEquals(listOf(6L), ids(caseById("region-intl-20-10-skipped")))
  }

  @Test
  fun hand_tetTableAndOutOfTable() {
    assertEquals(UpcomingOccasion("Tết Nguyên Đán", d("2027-02-06"), 17), occasionOf(caseById("tet-vn-17-days")))
    assertEquals(UpcomingOccasion("Valentine", d("2027-02-14"), 25), occasionOf(caseById("tet-intl-falls-to-valentine")))
    // Ngoài bảng (2041): Tết không xuất hiện, rơi về Valentine.
    assertEquals(UpcomingOccasion("Valentine", d("2041-02-14"), 15), occasionOf(caseById("tet-out-of-table-2041")))
  }

  @Test
  fun hand_preparationTextAndReasons() {
    assertEquals("Dịp này là hôm nay, hãy chuẩn bị ngay.", real(caseById("prep-0-vi")).first().preparation)
    assertEquals("The occasion is today, so get it ready right away.", real(caseById("prep-0-en")).first().preparation)
    assertEquals("Chưa có dịp nào gần, bạn có thể chuẩn bị bất cứ lúc nào.", real(caseById("prep-null-vi")).first().preparation)
    assertEquals("Only a few days left, so order or buy it now.", real(caseById("prep-2-en")).first().preparation)
    assertEquals("Còn khoảng hai tuần, đủ thời gian đặt và gói quà.", real(caseById("prep-14-vi")).first().preparation)
    assertEquals("Còn gần một tháng, thong thả chọn và chuẩn bị.", real(caseById("prep-25-vi")).first().preparation)
    assertEquals("Chỉ còn vài ngày, nên đặt hoặc mua ngay.", real(caseById("prep-3-vi")).first().preparation)
    assertEquals("About two weeks left, enough time to order and wrap it.", real(caseById("prep-4-en")).first().preparation)
    assertEquals("Almost a month left, so take your time to choose and prepare.", real(caseById("prep-15-en")).first().preparation)

    val vi = real(caseById("reason-three-variants-vi"))
    assertEquals(listOf(3L, 1L, 2L), vi.map { it.idea.id })
    assertEquals(
      listOf("Hợp sở thích chung của hai bạn và dịp sắp tới.", "Hợp sở thích chung của hai bạn.", "Hợp dịp sắp tới của hai bạn."),
      vi.map { it.reason },
    )
    assertEquals(
      listOf("Fits your shared interests and the coming occasion.", "Fits your shared interests.", "Fits the coming occasion."),
      real(caseById("reason-three-variants-en")).map { it.reason },
    )
  }

  @Test
  fun hand_yearWrapScoreWeightsTargetListAndTextFields() {
    assertEquals(UpcomingOccasion("Sinh nhật", d("2027-01-05"), 9), occasionOf(caseById("year-wrap-birthday-jan-5")))
    assertEquals(UpcomingOccasion("Kỷ niệm 1 năm", d("2027-01-05"), 9), occasionOf(caseById("year-wrap-first-anniversary")))
    // Điểm: 5 = 3 (nhãn) + 1 (chữ) = 4; 2 và 3 cùng = 2 (dịp, không có điểm AI) nên hoà và giữ thứ tự nhập; 1 và 4 cùng = 1.
    assertEquals(listOf(5L, 2L, 3L), ids(caseById("score-occasion-and-ai-weights")))
    // Nhãn khớp nguyên nhãn đã cắt và hạ chữ thường; "coffee beans" và "a;coffee" không khớp.
    assertEquals(listOf(1L, 2L, 4L), ids(caseById("target-list-comma-split-trim-and-case")))
    // Chỉ chữ nằm trọn trong MỘT trường mới khớp; "xy" + "z" ở hai trường kề nhau thì không.
    assertEquals(listOf(4L, 5L, 6L), ids(caseById("text-fields-and-field-separator")))
  }

  @Test
  fun hand_interestExpansionAndNfd() {
    assertEquals(setOf("coffee", "cà phê", "cafe"), useCase.interestKeywords(setOf("Coffee")))
    assertEquals(setOf("lego"), useCase.interestKeywords(setOf("lego", "", "   ")))
    assertEquals(listOf(1L, 2L, 3L), ids(caseById("interest-coffee-expands-vi-en")))
    assertEquals(emptyList<Long>(), ids(caseById("interest-blank-keys-dropped")))
    // Mã Android chưa chuẩn hoá: ý tưởng NFD không khớp khoá NFC; mục tiêu NFC thì khớp cả hai.
    assertEquals(listOf(2L), ids(caseById("nfd-idea-text-vs-nfc-keyword")))
    assertEquals(emptyList<Long>(), ids(caseById("nfd-interest-key-vs-nfc-idea")))
    assertEquals(listOf(1L), ids(caseById("nfd-both-sides-decomposed")))
    // expected = kết quả sau khi chuẩn hoá NFC: cả hai quà đều khớp "sách".
    val expected = caseMaps().first { it["id"] == "nfd-idea-text-vs-nfc-keyword" }["expected"] as Map<*, *>
    val expectedIds = (expected["results"] as List<*>).map { (it as Map<*, *>)["id"] }
    assertEquals(listOf<Any?>(1L, 2L), expectedIds)
  }

  // `gift` trong golden được dựng lại từ GiftCatalog (xem outcome), nên dây nối `gift` của use case phải được ghim riêng.
  @Test
  fun hand_resultGiftIsLocalizedFromTheReturnedIdeaInTheRequestedLanguage() {
    var englishWithTranslatedTag = 0
    for (c in cases) for (r in real(c)) {
      assertEquals("${c.id}: gift của ý tưởng ${r.idea.id}", GiftCatalog.localized(r.idea, c.isEnglish), r.gift)
      if (c.isEnglish && r.idea.tag == "Kỷ niệm") {
        assertEquals("Memories", r.gift.tag)
        englishWithTranslatedTag++
      }
    }
    assertTrue("Không ca tiếng Anh nào có nhãn cần dịch", englishWithTranslatedTag > 0)
    val vi = real(caseById("prep-14-vi")).first().gift.tag
    assertEquals("Kỷ niệm", vi)
  }
}
