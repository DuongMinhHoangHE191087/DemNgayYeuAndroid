package com.example.data.sync

import com.example.data.model.SharedMemoryEntity
import com.google.firebase.firestore.DocumentSnapshot

object MemorySyncAdapter : EntitySyncAdapter<SharedMemoryEntity> {

  override val entityType: String = "memory"

  override fun collectionPath(relationshipId: String): String = "relationships/$relationshipId/memories"

  override fun toFirestoreMap(entity: SharedMemoryEntity): Map<String, Any?> = mapOf(
    "syncId" to entity.syncId,
    "title" to entity.title,
    "dateText" to entity.dateText,
    "note" to entity.note,
    "photoUri" to entity.photoUri,
    "location" to entity.location,
    "isFavorite" to entity.isFavorite,
    "anniversaryTitle" to entity.anniversaryTitle,
    "createdAt" to entity.createdAt,
    "relationshipId" to entity.relationshipId,
    "authorId" to entity.authorId,
    "authorName" to entity.authorName,
    "mediaType" to entity.mediaType,
    "videoUri" to entity.videoUri,
    "cloudinaryPublicId" to entity.cloudinaryPublicId,
    "cloudinaryUrl" to entity.cloudinaryUrl,
    "isCloudinaryStored" to entity.isCloudinaryStored,
    "fileSizeFormatted" to entity.fileSizeFormatted,
    "durationSeconds" to entity.durationSeconds,
    "privacyLevel" to entity.privacyLevel,
    "updatedAt" to entity.updatedAt,
    "deleted" to entity.deleted
  )

  override fun fromFirestoreDoc(doc: DocumentSnapshot): SharedMemoryEntity? {
    val syncId = doc.getString("syncId") ?: return null
    val title = doc.getString("title") ?: return null
    val photoUri = doc.getString("photoUri") ?: return null
    return SharedMemoryEntity(
      syncId = syncId,
      title = title,
      dateText = doc.getString("dateText") ?: "",
      note = doc.getString("note") ?: "",
      photoUri = photoUri,
      location = doc.getString("location") ?: "",
      isFavorite = doc.getBoolean("isFavorite") ?: false,
      anniversaryTitle = doc.getString("anniversaryTitle") ?: "Kỷ Niệm Ngày Yêu",
      createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
      relationshipId = doc.getString("relationshipId"),
      authorId = doc.getString("authorId") ?: "",
      authorName = doc.getString("authorName") ?: "Bạn",
      mediaType = doc.getString("mediaType") ?: "IMAGE",
      videoUri = doc.getString("videoUri"),
      cloudinaryPublicId = doc.getString("cloudinaryPublicId"),
      cloudinaryUrl = doc.getString("cloudinaryUrl"),
      isCloudinaryStored = doc.getBoolean("isCloudinaryStored") ?: true,
      fileSizeFormatted = doc.getString("fileSizeFormatted") ?: "",
      durationSeconds = (doc.getLong("durationSeconds") ?: 0L).toInt(),
      privacyLevel = doc.getString("privacyLevel") ?: "COUPLE_ONLY",
      updatedAt = doc.getLong("updatedAt") ?: 0L,
      deleted = doc.getBoolean("deleted") ?: false,
      pendingSync = false
    )
  }
}
