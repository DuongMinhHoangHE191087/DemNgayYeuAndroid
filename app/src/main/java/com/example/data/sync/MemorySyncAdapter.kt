package com.example.data.sync

import com.example.data.model.SharedMemoryEntity
import com.example.domain.media.MEMORY_PUBLIC_ID_PREFIX
import com.google.firebase.firestore.DocumentSnapshot
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object MemorySyncAdapter : EntitySyncAdapter<SharedMemoryEntity> {

  override val entityType: String = "memory"

  override fun collectionPath(relationshipId: String): String = "relationships/$relationshipId/memories"

  // Chỉ gửi những gì người ấy được phép thấy. URI trên máy không bao giờ rời máy; ảnh/video đã lên đám mây
  // đi qua cloudinaryPublicId và getMemoryMediaUrl (URL ký số, có hạn), không bao giờ đi qua link public.
  override fun toFirestoreMap(entity: SharedMemoryEntity): Map<String, Any?> = mapOf(
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

  override fun fromFirestoreDoc(doc: DocumentSnapshot): SharedMemoryEntity? {
    val syncId = doc.getString("syncId") ?: return null
    val title = doc.getString("title") ?: return null
    return SharedMemoryEntity(
      syncId = syncId,
      title = title,
      dateText = doc.getString("dateText") ?: "",
      note = doc.getString("note") ?: "",
      photoUri = safeSharedPhotoUri(doc.getString("photoUri")),
      location = doc.getString("location") ?: "",
      isFavorite = doc.getBoolean("isFavorite") ?: false,
      anniversaryTitle = doc.getString("anniversaryTitle") ?: "Kỷ Niệm Ngày Yêu",
      createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
      relationshipId = doc.getString("relationshipId"),
      authorId = doc.getString("authorId") ?: "",
      authorName = doc.getString("authorName") ?: "Bạn",
      mediaType = doc.getString("mediaType") ?: "IMAGE",
      // Video không đi qua URI: máy nhận xin URL phát từ máy chủ bằng cloudinaryPublicId.
      videoUri = null,
      cloudinaryPublicId = safePublicId(syncId, doc.getString("cloudinaryPublicId")).ifBlank { null },
      fileSizeFormatted = doc.getString("fileSizeFormatted") ?: "",
      durationSeconds = (doc.getLong("durationSeconds") ?: 0L).toInt(),
      privacyLevel = doc.getString("privacyLevel") ?: "COUPLE_ONLY",
      updatedAt = doc.getLong("updatedAt") ?: 0L,
      deleted = doc.getBoolean("deleted") ?: false,
      pendingSync = false
    )
  }

  // Chỉ giữ URL https không phải link Cloudinary: link public cũ không được rò ra ngoài.
  internal fun safeSharedPhotoUri(raw: String?): String {
    if (raw.isNullOrBlank()) return ""
    val url = raw.toHttpUrlOrNull() ?: return "" // file://, content:// và đường dẫn trần chỉ tồn tại trên máy
    val cloudinary = url.host == "cloudinary.com" || url.host.endsWith(".cloudinary.com")
    return if (url.isHttps && !cloudinary) raw else ""
  }

  // publicId chỉ hợp lệ khi gắn với đúng mã kỷ niệm (tên tài nguyên được máy chủ ràng buộc theo memoryId).
  internal fun safePublicId(syncId: String, raw: String?): String =
    if (raw != null && raw == MEMORY_PUBLIC_ID_PREFIX + syncId) raw else ""
}
