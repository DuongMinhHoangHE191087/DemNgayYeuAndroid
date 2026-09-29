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

  override fun fromFirestoreDoc(doc: DocumentSnapshot): AnniversaryDateEntity? {
    val syncId = doc.getString("syncId") ?: return null
    val title = doc.getString("title") ?: return null
    return AnniversaryDateEntity(
      syncId = syncId,
      title = title,
      dateText = doc.getString("dateText") ?: "",
      type = doc.getString("type") ?: "LOVE",
      description = doc.getString("description") ?: "",
      isAnnual = doc.getBoolean("isAnnual") ?: true,
      notificationEnabled = doc.getBoolean("notificationEnabled") ?: true,
      reminderDaysBefore = (doc.getLong("reminderDaysBefore") ?: 3L).toInt(),
      createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
      relationshipId = doc.getString("relationshipId"),
      updatedAt = doc.getLong("updatedAt") ?: 0L,
      deleted = doc.getBoolean("deleted") ?: false,
      pendingSync = false
    )
  }
}
