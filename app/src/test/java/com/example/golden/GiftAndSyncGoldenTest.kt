package com.example.golden

import com.example.data.model.AnniversaryDateEntity
import com.example.data.model.GiftIdeaEntity
import com.example.data.model.SharedMemoryEntity
import com.example.data.sync.AnniversarySyncAdapter
import com.example.data.sync.MemorySyncAdapter
import com.example.domain.GiftRanker
import com.example.golden.LegacyGiftSyncOracle.OracleAnniversary
import com.example.golden.LegacyGiftSyncOracle.OracleGift
import com.example.golden.LegacyGiftSyncOracle.OracleMemory
import com.example.golden.LegacyGiftSyncOracle.Weights
import java.text.Normalizer
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/**
 * GV-016: xếp hạng quà của `GiftRanker` Android cũ, ghi từ oracle `LegacyGiftSyncOracle` (D-04a).
 * `expected` là kết quả oracle khi sở thích và chữ của quà đã chuẩn hoá NFC (quyết định D-13 commit 1);
 * nếu oracle trên đầu vào thô cho kết quả khác thì ghi thêm `androidLegacy` + `divergence`.
 * Chuỗi dịp KHÔNG chuẩn hoá. Nửa đồng bộ (D-04b): GV-025 memory và GV-026 anniversary, ghi từ bản sao oracle của
 * `toFirestoreMap`; `expected` = map oracle, trừ hai ca URL (userinfo, IDN) có `androidLegacy` + `divergence`.
 * Chữ tiếng Việt và ký tự đặc biệt dùng escape Unicode để NFC và NFD không bị editor đổi.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class GiftAndSyncGoldenTest {
  private val zone = "Asia/Ho_Chi_Minh"
  private val rankerGoldenName = "GV-016-gift-ranker.json"
  private lateinit var previousLocale: Locale

  // Ghim tr-TR: kết quả phải y hệt Locale.ROOT, chứng minh không phụ thuộc locale máy.
  @Before fun pinLocale() {
    previousLocale = Locale.getDefault()
    Locale.setDefault(Locale.forLanguageTag("tr-TR"))
  }

  @After fun restoreLocale() {
    Locale.setDefault(previousLocale)
  }

  private fun obj(vararg pairs: Pair<String, Any?>): LinkedHashMap<String, Any?> = linkedMapOf(*pairs)

  private class GiftCase(
    val id: String,
    val ideas: List<OracleGift>,
    val interests: List<String>,
    val occasion: String,
    val optional: Boolean,
  )

  private data class Result(val rankedIds: List<Long>, val matchesAnyInterestIds: List<Long>, val fitsOccasionIds: List<Long>) {
    fun toMap(): Map<String, Any?> = linkedMapOf<String, Any?>(
      "rankedIds" to rankedIds,
      "matchesAnyInterestIds" to matchesAnyInterestIds,
      "fitsOccasionIds" to fitsOccasionIds,
    )
  }

  private fun g(
    id: Long,
    title: String = "",
    tag: String = "",
    category: String = "",
    description: String = "",
    target: String = "",
    occ: String = "",
    ai: Boolean = false,
  ) = OracleGift(id, title, tag, category, description, target, occ, ai)

  private fun c(id: String, ideas: List<OracleGift>, interests: List<String>, occasion: String = "", optional: Boolean = false) =
    GiftCase(id, ideas, interests, occasion, optional)

  private val sinhNhat = "Sinh nhật"
  private val tet = "Tết"
  private val batKy = "Bất kỳ dịp nào"
  // Cùng chữ "Bất kỳ dịp nào" ở dạng NFD: không khớp vì chuỗi dịp không chuẩn hoá.
  private val batKyNfd = "Ba\u0302\u0301t ky\u0300 di\u0323p na\u0300o"
  private val docSachNfc = "đọc sách"
  private val docSachNfd = "\u0111o\u0323c sa\u0301ch"
  private val gauBong = "gấu bông"

  // 100 quà tất định (không random): id là hoán vị của 1..100, điểm hoà rất nhiều.
  private fun largeList(): List<OracleGift> = (1..100).map { i ->
    g(
      ((i * 37) % 101).toLong(),
      title = "Qua so $i",
      tag = "tag${i % 4}",
      category = "nhom${i % 5}",
      description = "Mo ta $i",
      target = when {
        i % 4 == 0 -> "music,tech"
        i % 7 == 0 -> "art"
        else -> ""
      },
      occ = when (i % 3) {
        0 -> ""
        1 -> sinhNhat
        else -> batKy
      },
      ai = i % 5 == 0,
    )
  }

  private val cases: List<GiftCase> = listOf(
    // 16 tên P8.
    c(
      "stable-ties",
      listOf(g(5, title = "Hoa"), g(3, title = "Hop xyz"), g(9, title = "Sach"), g(1, title = "Den xyz"), g(7, title = "Non")),
      listOf("xyz"),
    ),
    c(
      "nfc-vs-nfd-vietnamese-interest",
      listOf(
        g(10, title = "Bo qua tang", target = docSachNfc),
        g(20, title = "Ke sach", target = docSachNfd),
        g(30, description = "Thich $docSachNfd"),
      ),
      listOf(docSachNfd),
    ),
    c(
      "d-with-stroke-uppercase",
      listOf(g(1, title = "Sach"), g(2, target = "đồ chơi"), g(3, title = "Bộ ĐỒ CHƠI lắp ráp")),
      listOf("ĐỒ CHƠI"),
    ),
    c(
      "empty-interests",
      listOf(g(1, title = "Hoa"), g(2, title = "Thiep", ai = true), g(3, title = "Banh", occ = sinhNhat)),
      emptyList(),
      sinhNhat,
    ),
    c(
      "interests-case-variants-count-twice",
      listOf(g(1, target = "gaming"), g(2, title = "Gaming chair", occ = sinhNhat, ai = true), g(3, title = "Hoa")),
      listOf("Gaming", "gaming", "GAMING"),
      sinhNhat,
    ),
    c(
      "target-interests-trailing-commas",
      listOf(g(4, target = ",,"), g(1, target = "music,"), g(3, target = "art,,"), g(2, target = ",music"), g(5, target = "music,,art")),
      listOf("music"),
    ),
    c(
      "occasion-bat-ky",
      listOf(g(1, occ = sinhNhat), g(2, occ = batKy), g(3, occ = "Valentine")),
      emptyList(),
      "Valentine",
    ),
    c(
      "occasion-bat-ky-nfd-does-not-match",
      listOf(g(1, occ = batKyNfd), g(2, occ = batKy), g(3, occ = "Valentine")),
      emptyList(),
      sinhNhat,
    ),
    c(
      "occasion-any",
      listOf(g(1, occ = "Any occasion"), g(2, occ = "Company event"), g(3, occ = "Valentine"), g(4, occ = "ANY")),
      emptyList(),
      tet,
    ),
    c(
      "occasion-exact",
      listOf(
        g(4, occ = "Valentine"),
        g(3, occ = "$sinhNhat 18"),
        g(2, occ = sinhNhat.uppercase(Locale.ROOT)),
        g(1, occ = sinhNhat.lowercase()),
      ),
      emptyList(),
      "  Sinh Nhật ",
    ),
    c(
      "empty-occasion",
      listOf(g(2, occ = ""), g(1, occ = batKy), g(3, occ = sinhNhat)),
      emptyList(),
      "",
    ),
    c(
      "ai-generated-plus-1",
      listOf(g(1, title = "Non"), g(2, title = "Den", ai = true), g(3, title = "Hoa hong"), g(4, title = "Hoa lan", ai = true)),
      listOf("hoa"),
    ),
    c(
      "turkish-dotted-I",
      listOf(
        g(1, title = "kiz"),
        g(2, title = "kız"),
        g(3, target = "KIZ"),
        g(4, description = "Hediye İZMİR"),
        g(5, title = "izmir"),
      ),
      listOf("KIZ", "İZMİR"),
      optional = true,
    ),
    c(
      "greek-final-sigma",
      listOf(
        g(1, title = "οδος"),
        g(2, title = "οδοσ"),
        g(3, target = "ΟΔΟΣ"),
      ),
      listOf("ΟΔΟΣ"),
      optional = true,
    ),
    c(
      "german-sharp-s-capital",
      listOf(g(1, title = "Straße"), g(2, title = "STRASSE"), g(3, target = "STRAẞE")),
      listOf("STRAẞE"),
      optional = true,
    ),
    c("large-list-100-ideas-stable-order", largeList(), listOf("Music", "art"), " $sinhNhat"),

    // Ca tự thêm.
    c(
      "target-element-exact-not-substring",
      listOf(g(1, target = "musical"), g(2, target = "music"), g(3, title = "music box")),
      listOf("music"),
    ),
    c(
      "multiple-interests-accumulate",
      listOf(
        g(1, title = "lego va cafe"),
        g(2, target = "lego"),
        g(3, title = "lego sach cafe"),
        g(4, target = "lego,cafe"),
      ),
      listOf("lego", "sach", "cafe"),
    ),
    c(
      "interest-whitespace-trimmed",
      listOf(g(1, title = "Hoa"), g(2, title = "Gấu bông ngủ"), g(3, target = " $gauBong ")),
      listOf("  $gauBong  ", "\t", ""),
    ),
    c(
      "target-elements-trimmed",
      listOf(g(1, target = " music , art "), g(2, target = "artist"), g(3, target = "art,music")),
      listOf("art"),
    ),
    c(
      "occasion-case-insensitive",
      listOf(g(1, occ = "Valentine"), g(2, occ = "noel"), g(3, occ = "Noel"), g(4, occ = "NOEL ")),
      emptyList(),
      "NOEL",
    ),
    c(
      "no-match-keeps-input-order",
      listOf(g(9, title = "A"), g(8, title = "B"), g(7, title = "C"), g(6, title = "D"), g(5, title = "E")),
      listOf("zzz"),
      "none",
    ),
    c(
      "blank-interests-ignored",
      listOf(g(1, title = "A"), g(2, ai = true), g(3, target = "")),
      listOf("", "   ", "\t"),
    ),
    c(
      "blank-occasion-whitespace",
      listOf(g(1, occ = batKy), g(2, occ = ""), g(3, occ = "Any")),
      emptyList(),
      "   ",
    ),

    // Mỗi trọng số 3, 1, 2, 0: một ca đổi thứ tự khi tăng, một ca đổi thứ tự khi giảm một đơn vị.
    c(
      "weight-target-up",
      listOf(g(1, occ = sinhNhat, ai = true), g(2, target = "xyz")),
      listOf("xyz"),
      sinhNhat,
    ),
    c(
      "weight-target-down",
      listOf(g(1, occ = sinhNhat), g(2, target = "xyz")),
      listOf("xyz"),
      sinhNhat,
    ),
    c(
      "weight-text-up",
      listOf(g(1, ai = true), g(2, title = "xyz")),
      listOf("xyz"),
    ),
    c(
      "weight-text-down",
      listOf(g(1, title = "Hoa"), g(2, title = "xyz")),
      listOf("xyz"),
    ),
    c(
      "weight-occasion-up",
      listOf(g(1, title = "xyz", ai = true), g(2, occ = tet)),
      listOf("xyz"),
      tet,
    ),
    c(
      "weight-occasion-down",
      listOf(g(1, title = "xyz"), g(2, occ = tet)),
      listOf("xyz"),
      tet,
    ),
    c(
      "weight-ai-up",
      listOf(g(1, title = "xyz"), g(2, ai = true)),
      listOf("xyz"),
    ),
    c(
      "weight-ai-down",
      listOf(g(1, title = "Hoa"), g(2, ai = true)),
      listOf("xyz"),
    ),
    // Quà AI không còn điểm cộng (U-03) nên "target+1" cần ca riêng: id 1 = 1 (chữ) + 2 (dịp) = 3 hoà với id 2 = 3 (nhãn)
    // và giữ thứ tự nhập; nhãn tăng lên 4 thì id 2 lên đầu.
    c(
      "weight-target-up-vs-occasion-and-text",
      listOf(g(1, title = "xyz", occ = sinhNhat), g(2, target = "xyz")),
      listOf("xyz"),
      sinhNhat,
    ),
  )

  // ===== D-04b: GV-025 (memory) và GV-026 (anniversary) =====
  private val memoryGoldenName = "GV-025-memory-sync-map.json"
  private val anniversaryGoldenName = "GV-026-anniversary-sync-map.json"
  private val receiveCaseId = "receive-missing-keys-use-defaults"
  private val ownPublicId = "inlove_mem_mem-001"
  private val emoji = "\uD83D\uDC96"
  // note-emoji-and-nfd: "Bất kỳ" ở dạng NFD cộng emoji (escape để editor không đổi dạng).
  private val noteEmojiNfd = "$batKyNfd $emoji"

  // Ca đẩy lên: entity gửi + tên + divergence. `expected` = map của oracle, trừ khi có divergence (photoUri bị bỏ).
  private class SyncCase<T>(val id: String, val entity: T, val divergence: String? = null)

  private fun mem(
    syncId: String = "mem-001",
    title: String = "Chuyen di Da Lat",
    dateText: String = "2024-02-14",
    note: String = "ghi chu",
    photoUri: String = "https://img.example/p.jpg",
    location: String = "Da Lat",
    isFavorite: Boolean = false,
    anniversaryTitle: String = "Ky niem",
    createdAt: Long = 1_700_000_000_000L,
    relationshipId: String? = "rel-1",
    authorId: String = "uid-1",
    authorName: String = "An",
    mediaType: String = "IMAGE",
    videoUri: String? = null,
    cloudinaryPublicId: String? = null,
    fileSizeFormatted: String = "1.2 MB",
    durationSeconds: Int = 0,
    privacyLevel: String = "COUPLE_ONLY",
    updatedAt: Long = 1_700_000_100_000L,
    deleted: Boolean = false,
  ) = OracleMemory(
    syncId, title, dateText, note, photoUri, location, isFavorite, anniversaryTitle, createdAt, relationshipId,
    authorId, authorName, mediaType, videoUri, cloudinaryPublicId, fileSizeFormatted, durationSeconds,
    privacyLevel, updatedAt, deleted,
  )

  private fun ann(
    syncId: String = "ann-001",
    title: String = "Ky niem 1 nam",
    dateText: String = "2023-05-20",
    type: String = "LOVE",
    description: String = "mo ta",
    isAnnual: Boolean = true,
    notificationEnabled: Boolean = true,
    reminderDaysBefore: Int = 3,
    createdAt: Long = 1_700_000_000_000L,
    relationshipId: String? = "rel-1",
    updatedAt: Long = 1_700_000_100_000L,
    deleted: Boolean = false,
  ) = OracleAnniversary(
    syncId, title, dateText, type, description, isAnnual, notificationEnabled, reminderDaysBefore,
    createdAt, relationshipId, updatedAt, deleted,
  )

  private fun memCase(id: String, divergence: String? = null, m: OracleMemory) = SyncCase(id, m, divergence)

  private val memoryCases: List<SyncCase<OracleMemory>> = listOf(
    memCase(
      "all-fields-populated",
      m = mem(
        note = "Ghi chu day du",
        isFavorite = true,
        mediaType = "VIDEO",
        videoUri = "https://img.example/v.mp4",
        cloudinaryPublicId = ownPublicId,
        durationSeconds = 12,
        privacyLevel = "PRIVATE",
      ),
    ),
    memCase("deleted-clears-photo-and-public-id", m = mem(deleted = true, cloudinaryPublicId = ownPublicId)),
    memCase("photo-https-kept", m = mem(photoUri = "https://img.example/p.jpg")),
    memCase("photo-http-dropped", m = mem(photoUri = "http://img.example/p.jpg")),
    memCase("photo-file-uri-dropped", m = mem(photoUri = "file:///storage/emulated/0/p.jpg")),
    memCase("photo-content-uri-dropped", m = mem(photoUri = "content://media/external/images/media/1")),
    memCase("photo-bare-path-dropped", m = mem(photoUri = "/storage/emulated/0/p.jpg")),
    memCase("photo-cloudinary-host-dropped", m = mem(photoUri = "https://cloudinary.com/p.jpg")),
    memCase("photo-cloudinary-subdomain-dropped", m = mem(photoUri = "https://res.cloudinary.com/demo/image/upload/p.jpg")),
    memCase("photo-lookalike-host-notcloudinary-kept", m = mem(photoUri = "https://notcloudinary.com/p.jpg")),
    memCase("photo-uppercase-scheme-kept-raw", m = mem(photoUri = "HTTPS://img.example/P.jpg")),
    // Mã cũ vẫn xuất URL có userinfo / host IDN; đích mới (danh sách trắng nghiêm, D-20c) bỏ chúng.
    memCase("photo-with-userinfo", "url-userinfo-dropped", mem(photoUri = "https://user:pw@host/p.jpg")),
    memCase("photo-idn-host", "url-idn-host-dropped", mem(photoUri = "https://b\u00fccher.example/p.jpg")),
    memCase("public-id-matches-prefix-and-sync-id", m = mem(cloudinaryPublicId = ownPublicId)),
    memCase("public-id-of-other-memory-dropped", m = mem(cloudinaryPublicId = "inlove_mem_mem-002")),
    memCase("public-id-null", m = mem(cloudinaryPublicId = null)),
    memCase("video-uri-always-empty", m = mem(mediaType = "VIDEO", videoUri = "https://img.example/v.mp4")),
    memCase("created-at-above-int-range", m = mem(createdAt = 4_102_444_800_000L, updatedAt = 4_102_444_900_000L)),
    memCase("duration-seconds-int", m = mem(mediaType = "VIDEO", durationSeconds = 3600)),
    memCase("note-emoji-and-nfd", m = mem(title = docSachNfd, note = noteEmojiNfd)),
  )

  private val annivCases: List<SyncCase<OracleAnniversary>> = listOf(
    SyncCase(
      "all-fields-populated",
      ann(type = "WEDDING", description = "Mo ta day du", isAnnual = true, notificationEnabled = true, reminderDaysBefore = 7),
    ),
    SyncCase("is-annual-false", ann(isAnnual = false)),
    SyncCase("notification-disabled", ann(notificationEnabled = false)),
    SyncCase("reminder-days-0", ann(reminderDaysBefore = 0)),
    SyncCase("reminder-days-3-default", ann(reminderDaysBefore = 3)),
    SyncCase("reminder-days-int-max", ann(reminderDaysBefore = Int.MAX_VALUE)),
    SyncCase("deleted-true", ann(deleted = true)),
    SyncCase("created-at-above-int-range", ann(createdAt = 4_102_444_800_000L)),
    SyncCase("updated-at-zero", ann(updatedAt = 0L)),
    SyncCase("title-emoji", ann(title = "Sinh nhat $emoji")),
    SyncCase("title-nfd", ann(title = docSachNfd)),
    SyncCase("relationship-id-null", ann(relationshipId = null)),
    SyncCase("description-empty", ann(description = "")),
  )

  private val memoryP8Names = listOf(
    "all-fields-populated",
    "deleted-clears-photo-and-public-id",
    "photo-https-kept",
    "photo-http-dropped",
    "photo-file-uri-dropped",
    "photo-content-uri-dropped",
    "photo-bare-path-dropped",
    "photo-cloudinary-host-dropped",
    "photo-cloudinary-subdomain-dropped",
    "photo-lookalike-host-notcloudinary-kept",
    "photo-uppercase-scheme-kept-raw",
    "photo-with-userinfo",
    "photo-idn-host",
    "public-id-matches-prefix-and-sync-id",
    "public-id-of-other-memory-dropped",
    "public-id-null",
    "video-uri-always-empty",
    "created-at-above-int-range",
    "duration-seconds-int",
    "note-emoji-and-nfd",
  )

  private val anniversaryP8Names = listOf(
    "all-fields-populated",
    "is-annual-false",
    "notification-disabled",
    "reminder-days-0",
    "reminder-days-3-default",
    "reminder-days-int-max",
    "deleted-true",
    "created-at-above-int-range",
    "updated-at-zero",
    "title-emoji",
    "title-nfd",
    "relationship-id-null",
    "description-empty",
    "receive-missing-keys-use-defaults",
  )

  // Giá trị mặc định của fromFirestoreDoc khi thiếu khoá; "NOW" là token cho createdAt (System.currentTimeMillis).
  private val memoryReceiveDefaults = obj(
    "syncId" to "mem-recv",
    "title" to "Tieu de",
    "dateText" to "",
    "note" to "",
    "photoUri" to "",
    "location" to "",
    "isFavorite" to false,
    "anniversaryTitle" to "Kỷ Niệm Ngày Yêu",
    "createdAt" to "NOW",
    "relationshipId" to null,
    "authorId" to "",
    "authorName" to "Bạn",
    "mediaType" to "IMAGE",
    "videoUri" to null,
    "cloudinaryPublicId" to null,
    "fileSizeFormatted" to "",
    "durationSeconds" to 0L,
    "privacyLevel" to "COUPLE_ONLY",
    "updatedAt" to 0L,
    "deleted" to false,
    "pendingSync" to false,
  )

  private val anniversaryReceiveDefaults = obj(
    "syncId" to "ann-recv",
    "title" to "Tieu de",
    "dateText" to "",
    "type" to "LOVE",
    "description" to "",
    "isAnnual" to true,
    "notificationEnabled" to true,
    "reminderDaysBefore" to 3L,
    "createdAt" to "NOW",
    "relationshipId" to null,
    "updatedAt" to 0L,
    "deleted" to false,
    "pendingSync" to false,
  )

  private fun typeOf(v: Any?): String = when (v) {
    null -> "null"
    is String -> "string"
    is Boolean -> "boolean"
    is Long, is Int -> "integer"
    else -> throw AssertionError("Kiểu runtime không hỗ trợ: ${v::class.java.name}")
  }

  private fun typesOf(m: Map<String, Any?>): Map<String, Any?> = m.mapValues { typeOf(it.value) }

  // GoldenWriter không nhận Int: đổi sang Long.
  private fun golden(m: Map<String, Any?>): Map<String, Any?> = m.mapValues { (it.value as? Int)?.toLong() ?: it.value }

  private fun memoryInput(m: OracleMemory) = obj(
    "syncId" to m.syncId,
    "title" to m.title,
    "dateText" to m.dateText,
    "note" to m.note,
    "photoUri" to m.photoUri,
    "location" to m.location,
    "isFavorite" to m.isFavorite,
    "anniversaryTitle" to m.anniversaryTitle,
    "createdAt" to m.createdAt,
    "relationshipId" to m.relationshipId,
    "authorId" to m.authorId,
    "authorName" to m.authorName,
    "mediaType" to m.mediaType,
    "videoUri" to m.videoUri,
    "cloudinaryPublicId" to m.cloudinaryPublicId,
    "fileSizeFormatted" to m.fileSizeFormatted,
    "durationSeconds" to m.durationSeconds.toLong(),
    "privacyLevel" to m.privacyLevel,
    "updatedAt" to m.updatedAt,
    "deleted" to m.deleted,
  )

  private fun anniversaryInput(a: OracleAnniversary) = obj(
    "syncId" to a.syncId,
    "title" to a.title,
    "dateText" to a.dateText,
    "type" to a.type,
    "description" to a.description,
    "isAnnual" to a.isAnnual,
    "notificationEnabled" to a.notificationEnabled,
    "reminderDaysBefore" to a.reminderDaysBefore.toLong(),
    "createdAt" to a.createdAt,
    "relationshipId" to a.relationshipId,
    "updatedAt" to a.updatedAt,
    "deleted" to a.deleted,
  )

  private fun realMemoryMap(m: OracleMemory): Map<String, Any?> = MemorySyncAdapter.toFirestoreMap(
    SharedMemoryEntity(
      syncId = m.syncId,
      title = m.title,
      dateText = m.dateText,
      note = m.note,
      photoUri = m.photoUri,
      location = m.location,
      isFavorite = m.isFavorite,
      anniversaryTitle = m.anniversaryTitle,
      createdAt = m.createdAt,
      relationshipId = m.relationshipId,
      authorId = m.authorId,
      authorName = m.authorName,
      mediaType = m.mediaType,
      videoUri = m.videoUri,
      cloudinaryPublicId = m.cloudinaryPublicId,
      fileSizeFormatted = m.fileSizeFormatted,
      durationSeconds = m.durationSeconds,
      privacyLevel = m.privacyLevel,
      updatedAt = m.updatedAt,
      deleted = m.deleted,
    ),
  )

  private fun realAnniversaryMap(a: OracleAnniversary): Map<String, Any?> = AnniversarySyncAdapter.toFirestoreMap(
    AnniversaryDateEntity(
      syncId = a.syncId,
      title = a.title,
      dateText = a.dateText,
      type = a.type,
      description = a.description,
      isAnnual = a.isAnnual,
      notificationEnabled = a.notificationEnabled,
      reminderDaysBefore = a.reminderDaysBefore,
      createdAt = a.createdAt,
      relationshipId = a.relationshipId,
      updatedAt = a.updatedAt,
      deleted = a.deleted,
    ),
  )

  private fun <T> pushCaseMaps(
    cases: List<SyncCase<T>>,
    input: (T) -> Map<String, Any?>,
    oracle: (T) -> Map<String, Any?>,
  ): List<Map<String, Any?>> = cases.map { c ->
    val raw = oracle(c.entity)
    val legacy = golden(raw)
    val diverges = c.divergence != null
    // Đích mới bỏ URL có userinfo / IDN: chỉ photoUri khác mã cũ.
    val expected = if (diverges) LinkedHashMap(legacy).also { it["photoUri"] = "" } else legacy
    obj(
      "id" to c.id,
      "input" to input(c.entity),
      "expected" to expected,
      "types" to typesOf(raw),
      "androidLegacy" to if (diverges) legacy else null,
      "divergence" to c.divergence,
      "optional" to false,
    )
  }

  private fun receiveCaseMap(syncId: String, expected: Map<String, Any?>) = obj(
    "id" to receiveCaseId,
    "input" to obj("doc" to obj("syncId" to syncId, "title" to "Tieu de")),
    "expected" to expected,
    "types" to obj("syncId" to "string", "title" to "string"),
    "androidLegacy" to null,
    "divergence" to null,
    "optional" to false,
  )

  private fun memoryCaseMaps(): List<Map<String, Any?>> =
    pushCaseMaps(memoryCases, ::memoryInput, LegacyGiftSyncOracle::memoryToFirestoreMap) +
      receiveCaseMap("mem-recv", memoryReceiveDefaults)

  private fun anniversaryCaseMaps(): List<Map<String, Any?>> =
    pushCaseMaps(annivCases, ::anniversaryInput, LegacyGiftSyncOracle::anniversaryToFirestoreMap) +
      receiveCaseMap("ann-recv", anniversaryReceiveDefaults)

  @Test
  fun memorySyncGolden_matchesCommitted() {
    GoldenWriter().check(memoryGoldenName, obj("schema" to 1L, "zone" to zone, "cases" to memoryCaseMaps()))
  }

  @Test
  fun anniversarySyncGolden_matchesCommitted() {
    GoldenWriter().check(anniversaryGoldenName, obj("schema" to 1L, "zone" to zone, "cases" to anniversaryCaseMaps()))
  }

  @Test
  fun syncGolden_coversEveryP8CaseName() {
    val memoryIds = memoryCaseMaps().map { it["id"] as String }
    assertEquals(21, memoryIds.size)
    assertEquals(memoryIds.size, memoryIds.toSet().size)
    assertEquals(20, memoryP8Names.size)
    for (name in memoryP8Names + receiveCaseId) assertTrue("GV-025 thiếu ca $name", name in memoryIds)

    val anniversaryIds = anniversaryCaseMaps().map { it["id"] as String }
    assertEquals(14, anniversaryIds.size)
    assertEquals(anniversaryIds.size, anniversaryIds.toSet().size)
    assertEquals(14, anniversaryP8Names.size)
    for (name in anniversaryP8Names) assertTrue("GV-026 thiếu ca $name", name in anniversaryIds)
  }

  // `types` phải khớp lớp runtime của giá trị oracle VÀ của mã thật (Int/Long -> integer).
  @Test
  fun syncGolden_typesMatchRuntimeClasses() {
    for (c in memoryCases) {
      val raw = LegacyGiftSyncOracle.memoryToFirestoreMap(c.entity)
      assertEquals("memory ${c.id}: 20 khoá", 20, raw.size)
      assertEquals("memory ${c.id}: types oracle", typesOf(raw), typesOf(golden(raw)))
      assertEquals("memory ${c.id}: types mã thật", typesOf(raw), typesOf(realMemoryMap(c.entity)))
    }
    for (c in annivCases) {
      val raw = LegacyGiftSyncOracle.anniversaryToFirestoreMap(c.entity)
      assertEquals("anniversary ${c.id}: 12 khoá", 12, raw.size)
      assertEquals("anniversary ${c.id}: types mã thật", typesOf(raw), typesOf(realAnniversaryMap(c.entity)))
    }
    for (case in memoryCaseMaps() + anniversaryCaseMaps()) {
      if (case["id"] == receiveCaseId && (case["input"] as Map<*, *>).containsKey("doc")) {
        val doc = (case["input"] as Map<*, *>)["doc"] as Map<*, *>
        assertEquals(case["types"], doc.mapValues { typeOf(it.value) })
        continue
      }
      @Suppress("UNCHECKED_CAST")
      val expected = case["expected"] as Map<String, Any?>
      assertEquals("${case["id"]}: types theo expected", case["types"], typesOf(expected))
    }
  }

  @Test
  fun syncGolden_nfdFixturesAreReallyDecomposed() {
    for (s in listOf(noteEmojiNfd, docSachNfd)) {
      val composed = nfc(s)
      assertTrue("fixture NFD không được bằng NFC: $s", s != composed && s.length > composed.length)
      assertTrue("fixture thiếu dấu rời U+0300..U+036F", s.codePoints().anyMatch { it in 0x0300..0x036f || it == 0x0323 })
    }
    // Emoji ngoài BMP: một code point, hai UTF-16 unit.
    assertEquals(0x1f496, emoji.codePointAt(0))
  }

  @Test
  fun syncRealCode_agreesWithExpectedOrAndroidLegacy() {
    fun check(kind: String, maps: List<Map<String, Any?>>, real: (Int) -> Map<String, Any?>, keyCount: Int) {
      val pushed = maps.filter { it["id"] != receiveCaseId }
      pushed.forEachIndexed { i, case ->
        val id = case["id"]
        assertEquals("$kind $id: divergence và androidLegacy phải đi cùng nhau", case["divergence"] != null, case["androidLegacy"] != null)
        @Suppress("UNCHECKED_CAST")
        val expected = case["expected"] as Map<String, Any?>
        assertEquals("$kind $id: số khoá expected", keyCount, expected.size)
        val actual = golden(real(i))
        val legacy = case["androidLegacy"]
        assertTrue(
          "$kind $id: mã thật $actual không bằng expected $expected hay androidLegacy $legacy",
          actual == expected || actual == legacy,
        )
      }
      // Ca divergence phải thật sự khác mã cũ, và chỉ có đúng các ca đã định.
      for (case in pushed.filter { it["divergence"] != null }) assertTrue(case["expected"] != case["androidLegacy"])
    }
    check("memory", memoryCaseMaps(), { realMemoryMap(memoryCases[it].entity) }, 20)
    check("anniversary", anniversaryCaseMaps(), { realAnniversaryMap(annivCases[it].entity) }, 12)
    assertEquals(
      listOf("photo-with-userinfo" to "url-userinfo-dropped", "photo-idn-host" to "url-idn-host-dropped"),
      memoryCaseMaps().filter { it["divergence"] != null }.map { it["id"] to it["divergence"] },
    )
    assertTrue(anniversaryCaseMaps().none { it["divergence"] != null })
  }

  private val p8Names = listOf(
    "stable-ties",
    "nfc-vs-nfd-vietnamese-interest",
    "d-with-stroke-uppercase",
    "empty-interests",
    "interests-case-variants-count-twice",
    "target-interests-trailing-commas",
    "occasion-bat-ky",
    "occasion-bat-ky-nfd-does-not-match",
    "occasion-any",
    "occasion-exact",
    "empty-occasion",
    "ai-generated-plus-1",
    "turkish-dotted-I",
    "greek-final-sigma",
    "german-sharp-s-capital",
    "large-list-100-ideas-stable-order",
  )

  private fun nfc(s: String) = Normalizer.normalize(s, Normalizer.Form.NFC)

  // Chuẩn hoá NFC sở thích và chữ của quà; KHÔNG đụng `occasion` và `suggestedOccasion`.
  private fun nfcCase(c: GiftCase) = GiftCase(
    c.id,
    c.ideas.map {
      it.copy(
        title = nfc(it.title),
        tag = nfc(it.tag),
        category = nfc(it.category),
        description = nfc(it.description),
        targetInterests = nfc(it.targetInterests),
      )
    },
    c.interests.map(::nfc),
    c.occasion,
    c.optional,
  )

  private fun run(c: GiftCase, weights: Weights = Weights()): Result = Result(
    LegacyGiftSyncOracle.rank(c.ideas, c.interests, c.occasion, weights).map { it.id },
    c.ideas.filter { LegacyGiftSyncOracle.matchesAnyInterest(it, c.interests) }.map { it.id },
    c.ideas.filter { LegacyGiftSyncOracle.fitsOccasion(it, c.occasion) }.map { it.id },
  )

  private fun ideaMap(x: OracleGift) = obj(
    "id" to x.id,
    "title" to x.title,
    "tag" to x.tag,
    "category" to x.category,
    "description" to x.description,
    "targetInterests" to x.targetInterests,
    "suggestedOccasion" to x.suggestedOccasion,
    "isAiGenerated" to x.isAiGenerated,
  )

  private fun caseMaps(): List<Map<String, Any?>> = cases.map { c ->
    val raw = run(c)
    val normalized = run(nfcCase(c))
    val diverges = raw != normalized
    obj(
      "id" to c.id,
      "input" to obj("ideas" to c.ideas.map(::ideaMap), "interests" to c.interests, "occasion" to c.occasion),
      "expected" to normalized.toMap(),
      "androidLegacy" to if (diverges) raw.toMap() else null,
      "divergence" to if (diverges) "nfc-interest-match" else null,
      "optional" to c.optional,
    )
  }

  @Test
  fun giftGolden_matchesCommitted() {
    GoldenWriter().check(rankerGoldenName, obj("schema" to 1L, "zone" to zone, "cases" to caseMaps()))
  }

  @Test
  fun giftGolden_coversEveryP8CaseName() {
    assertEquals(16, p8Names.size)
    val ids = cases.map { it.id }
    assertEquals(ids.size, ids.toSet().size)
    for (name in p8Names) assertTrue("Thiếu ca P8 $name", name in ids)
  }

  @Test
  fun giftGolden_hasAtLeast30Sets() {
    assertTrue("Chỉ có ${cases.size} bộ", cases.size >= 30)
    assertTrue(cases.first { it.id == "large-list-100-ideas-stable-order" }.ideas.size == 100)
  }

  // Hai chuỗi NFD phải thật sự là dạng tách dấu (không bị editor gộp lại), nếu không ca NFC/NFD trong golden mất ý nghĩa.
  @Test
  fun giftGolden_nfdFixturesAreReallyDecomposed() {
    assertTrue(batKyNfd != batKy && nfc(batKyNfd) == batKy)
    assertTrue(docSachNfd != docSachNfc && nfc(docSachNfd) == docSachNfc)
  }

  @Test
  fun giftGolden_divergenceHasBothKeys() {
    for (case in caseMaps()) {
      val hasDivergence = case["divergence"] != null
      val hasLegacy = case["androidLegacy"] != null
      assertEquals("${case["id"]}: divergence và androidLegacy phải đi cùng nhau", hasDivergence, hasLegacy)
    }
    // Chuỗi dịp không chuẩn hoá nên NFD "bất kỳ" không có divergence; sở thích NFD thì có.
    val byId = caseMaps().associateBy { it["id"] }
    assertEquals(null, byId.getValue("occasion-bat-ky-nfd-does-not-match")["divergence"])
    assertNotNull(byId.getValue("nfc-vs-nfd-vietnamese-interest")["divergence"])
  }

  @Test
  fun giftGolden_eachWeightChangeIsDetected() {
    val base = Weights()
    val variants = listOf(
      "target+1" to base.copy(target = base.target + 1),
      "target-1" to base.copy(target = base.target - 1),
      "text+1" to base.copy(text = base.text + 1),
      "text-1" to base.copy(text = base.text - 1),
      "occasion+1" to base.copy(occasion = base.occasion + 1),
      "occasion-1" to base.copy(occasion = base.occasion - 1),
      "ai+1" to base.copy(ai = base.ai + 1),
      "ai-1" to base.copy(ai = base.ai - 1),
    )
    assertEquals(8, variants.size)
    val expectedCases = cases.map(::nfcCase)
    val undetected = variants.filter { (_, w) ->
      expectedCases.none { c ->
        LegacyGiftSyncOracle.rank(c.ideas, c.interests, c.occasion, w).map { it.id } != run(c).rankedIds
      }
    }.map { it.first }
    assertTrue("Biến thể trọng số không bị ca nào phát hiện: $undetected", undetected.isEmpty())
  }

  @Test
  fun giftRealCode_agreesWithExpectedOrAndroidLegacy() {
    for (case in cases) {
      assertEquals("${case.id}: sở thích trùng nguyên văn bị Set gộp", case.interests.size, case.interests.toSet().size)
      val entities = case.ideas.map {
        GiftIdeaEntity(
          id = it.id,
          title = it.title,
          category = it.category,
          badgeText = "",
          tag = it.tag,
          description = it.description,
          imageUrl = "",
          isAiGenerated = it.isAiGenerated,
          targetInterests = it.targetInterests,
          suggestedOccasion = it.suggestedOccasion,
        )
      }
      val interests = LinkedHashSet(case.interests)
      val real = Result(
        GiftRanker.rank(entities, interests, case.occasion).map { it.id },
        entities.filter { GiftRanker.matchesAnyInterest(it, interests) }.map { it.id },
        entities.filter { GiftRanker.fitsOccasion(it, case.occasion) }.map { it.id },
      )
      val expected = run(nfcCase(case))
      val legacy = run(case)
      assertTrue("${case.id}: mã thật $real không bằng expected $expected hay androidLegacy $legacy", real == expected || real == legacy)
    }
  }
}
