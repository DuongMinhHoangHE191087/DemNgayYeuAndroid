package com.example.golden

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Bản sao nguyên văn của `GiftRanker` Android cũ (D-04a), dùng làm oracle cho GV-016.
 * Dùng bản sao thay vì gọi mã thật để chế độ VERIFY không đổi khi D-13 sửa mã thật. Xoá ở D-35.
 * Chỉ thêm tham số trọng số (mặc định 3, 1, 2, 0) cho test đột biến; logic còn lại giữ y nguyên.
 * Từ U-03 oracle theo luật mới: quà AI không còn điểm cộng (trọng số ai mặc định 0).
 * Dùng data class riêng của test, không phụ thuộc `GiftIdeaEntity`, để D-13/D-35 không làm hỏng oracle.
 * D-04b thêm bản sao nguyên văn `MemorySyncAdapter.toFirestoreMap` (cùng `safeSharedPhotoUri`, `safePublicId`)
 * và `AnniversarySyncAdapter.toFirestoreMap` cho GV-025 và GV-026; xoá cùng oracle ở D-35 (D-20a/b/c sửa mã thật).
 */
object LegacyGiftSyncOracle {
  data class OracleGift(
    val id: Long,
    val title: String,
    val tag: String,
    val category: String,
    val description: String,
    val targetInterests: String,
    val suggestedOccasion: String,
    val isAiGenerated: Boolean,
  )

  /** Điểm cộng: khớp phần tử targetInterests, khớp chuỗi con trong văn bản, hợp dịp, quà AI. */
  data class Weights(val target: Int = 3, val text: Int = 1, val occasion: Int = 2, val ai: Int = 0)

  // "bất kỳ" viết bằng escape để editor không đổi dạng NFC/NFD.
  private const val BAT_KY = "b\u1ea5t k\u1ef3"
  // Cầu nối kỷ niệm 1 năm, khớp GiftRanker; viết bằng escape như BAT_KY.
  private const val ANNIVERSARY = "k\u1ef7 ni\u1ec7m ng\u00e0y y\u00eau"
  private const val FIRST_ANNIVERSARY = "k\u1ef7 ni\u1ec7m 1 n\u0103m"

  private fun normalize(interests: List<String>) = interests.map { it.trim().lowercase() }.filter { it.isNotEmpty() }

  private fun targets(g: OracleGift) = g.targetInterests.lowercase().split(',').map { it.trim() }

  private fun text(g: OracleGift) = "${g.title} ${g.tag} ${g.category} ${g.description}".lowercase()

  private fun occasionFits(g: OracleGift, occ: String): Boolean =
    occ.isNotEmpty() && g.suggestedOccasion.lowercase().let {
      it == occ || it.contains(BAT_KY) || it.contains("any") || (occ == FIRST_ANNIVERSARY && it == ANNIVERSARY)
    }

  fun matchesAnyInterest(g: OracleGift, interests: List<String>): Boolean {
    val target = targets(g)
    val text = text(g)
    return normalize(interests).any { it in target || text.contains(it) }
  }

  fun fitsOccasion(g: OracleGift, occasion: String): Boolean = occasionFits(g, occasion.trim().lowercase())

  fun rank(ideas: List<OracleGift>, interests: List<String>, occasion: String = "", weights: Weights = Weights()): List<OracleGift> {
    val keys = normalize(interests)
    val occ = occasion.trim().lowercase()
    fun score(g: OracleGift): Int {
      val target = targets(g)
      val text = text(g)
      var s = keys.sumOf { k -> (if (k in target) weights.target else 0) + (if (text.contains(k)) weights.text else 0) }
      if (occasionFits(g, occ)) s += weights.occasion
      if (g.isAiGenerated) s += weights.ai
      return s
    }
    return ideas.sortedByDescending { score(it) } // sortedBy ổn định
  }

  /** Các trường của `SharedMemoryEntity` mà `toFirestoreMap` đọc (cloudinaryUrl, id, pendingSync... không được gửi). */
  data class OracleMemory(
    val syncId: String,
    val title: String,
    val dateText: String,
    val note: String,
    val photoUri: String,
    val location: String,
    val isFavorite: Boolean,
    val anniversaryTitle: String,
    val createdAt: Long,
    val relationshipId: String?,
    val authorId: String,
    val authorName: String,
    val mediaType: String,
    val videoUri: String?,
    val cloudinaryPublicId: String?,
    val fileSizeFormatted: String,
    val durationSeconds: Int,
    val privacyLevel: String,
    val updatedAt: Long,
    val deleted: Boolean,
  )

  /** Các trường của `AnniversaryDateEntity` mà `toFirestoreMap` đọc (id, daysRemaining, pendingSync không được gửi). */
  data class OracleAnniversary(
    val syncId: String,
    val title: String,
    val dateText: String,
    val type: String,
    val description: String,
    val isAnnual: Boolean,
    val notificationEnabled: Boolean,
    val reminderDaysBefore: Int,
    val createdAt: Long,
    val relationshipId: String?,
    val updatedAt: Long,
    val deleted: Boolean,
  )

  private const val MEMORY_PUBLIC_ID_PREFIX = "inlove_mem_"

  // Nguyên văn MemorySyncAdapter.toFirestoreMap (chỉ đổi kiểu tham số sang OracleMemory).
  fun memoryToFirestoreMap(entity: OracleMemory): Map<String, Any?> = mapOf(
    "syncId" to entity.syncId,
    "title" to entity.title,
    "dateText" to entity.dateText,
    "note" to entity.note,
    "photoUri" to (if (entity.deleted) "" else safeSharedPhotoUri(entity.photoUri)),
    "location" to entity.location,
    "isFavorite" to entity.isFavorite,
    "anniversaryTitle" to entity.anniversaryTitle,
    "createdAt" to entity.createdAt,
    "relationshipId" to entity.relationshipId,
    "authorId" to entity.authorId,
    "authorName" to entity.authorName,
    "mediaType" to entity.mediaType,
    "videoUri" to "",
    "cloudinaryPublicId" to (if (entity.deleted) "" else safePublicId(entity.syncId, entity.cloudinaryPublicId)),
    "fileSizeFormatted" to entity.fileSizeFormatted,
    "durationSeconds" to entity.durationSeconds,
    "privacyLevel" to entity.privacyLevel,
    "updatedAt" to entity.updatedAt,
    "deleted" to entity.deleted
  )

  // Nguyên văn MemorySyncAdapter.safeSharedPhotoUri.
  fun safeSharedPhotoUri(raw: String?): String {
    if (raw.isNullOrBlank()) return ""
    val url = raw.toHttpUrlOrNull() ?: return "" // file://, content:// và đường dẫn trần chỉ tồn tại trên máy
    val cloudinary = url.host == "cloudinary.com" || url.host.endsWith(".cloudinary.com")
    return if (url.isHttps && !cloudinary) raw else ""
  }

  // Nguyên văn MemorySyncAdapter.safePublicId.
  fun safePublicId(syncId: String, raw: String?): String =
    if (raw != null && raw == MEMORY_PUBLIC_ID_PREFIX + syncId) raw else ""

  // Nguyên văn AnniversarySyncAdapter.toFirestoreMap (chỉ đổi kiểu tham số sang OracleAnniversary).
  fun anniversaryToFirestoreMap(entity: OracleAnniversary): Map<String, Any?> = mapOf(
    "syncId" to entity.syncId,
    "title" to entity.title,
    "dateText" to entity.dateText,
    "type" to entity.type,
    "description" to entity.description,
    "isAnnual" to entity.isAnnual,
    "notificationEnabled" to entity.notificationEnabled,
    "reminderDaysBefore" to entity.reminderDaysBefore,
    "createdAt" to entity.createdAt,
    "relationshipId" to entity.relationshipId,
    "updatedAt" to entity.updatedAt,
    "deleted" to entity.deleted
  )
}
