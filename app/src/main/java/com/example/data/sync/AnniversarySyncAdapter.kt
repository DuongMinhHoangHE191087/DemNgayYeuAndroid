package com.example.data.sync

import com.example.data.model.AnniversaryDateEntity
import com.google.firebase.firestore.DocumentSnapshot

object AnniversarySyncAdapter : EntitySyncAdapter<AnniversaryDateEntity> {

  override val entityType: String = "anniversary"

  override fun collectionPath(relationshipId: String): String = "relationships/$relationshipId/anniversaries"

  override fun toFirestoreMap(entity: AnniversaryDateEntity): Map<String, Any?> = mapOf(
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

  override fun fromFirestoreDoc(doc: DocumentSnapshot): AnniversaryDateEntity? =
    doc.data?.let { fromFirestoreMap(it) }

  /** Map thuần (từ `doc.data`) -> entity; ép kiểu an toàn, sai kiểu = mặc định của trường thiếu (xem MemorySyncAdapter.fromFirestoreMap). */
  internal fun fromFirestoreMap(data: Map<String, Any?>, now: Long = System.currentTimeMillis()): AnniversaryDateEntity? {
    val syncId = data.optString("syncId") ?: return null
    val title = data.optString("title") ?: return null
    return AnniversaryDateEntity(
      syncId = syncId,
      title = title,
      dateText = data.optString("dateText") ?: "",
      type = data.optString("type") ?: "LOVE",
      description = data.optString("description") ?: "",
      isAnnual = data.optBool("isAnnual") ?: true,
      notificationEnabled = data.optBool("notificationEnabled") ?: true,
      reminderDaysBefore = (data.optLong("reminderDaysBefore") ?: 3L).toInt(),
      createdAt = data.optLong("createdAt") ?: now,
      relationshipId = data.optString("relationshipId"),
      updatedAt = data.optLong("updatedAt") ?: 0L,
      deleted = data.optBool("deleted") ?: false,
      pendingSync = false
    )
  }
}
