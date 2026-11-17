package com.example.data.repository

import com.example.data.db.InLoveDao
import com.example.data.model.AnniversaryDateEntity
import com.example.data.model.OnlineRelationshipEntity
import com.example.data.model.RelationshipStatus
import com.example.data.model.SharedMemoryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Unpairing keeps every row in Room and only hides it. A relationship-scoped row is visible while its
 * relationship is the current one, or a terminated one with the SAME two people (re-pairing the same
 * person brings it back). Single, or paired with someone new, hides the ex's rows. Rows without a
 * relationshipId (solo / private) are always visible.
 */
internal fun visibleRelationshipIds(rels: List<OnlineRelationshipEntity>): Set<String> {
  val current = rels.filter { it.status != RelationshipStatus.TERMINATED }
  if (current.isEmpty()) return emptySet()
  val pairs = current.map { setOf(it.user1, it.user2) }.toSet()
  return rels.filter { it.status != RelationshipStatus.TERMINATED || setOf(it.user1, it.user2) in pairs }
    .map { it.relationshipId }.toSet()
}

@JvmName("visibleMemoriesIn")
internal fun List<SharedMemoryEntity>.visibleIn(ids: Set<String>) =
  filter { it.relationshipId == null || it.relationshipId in ids }

internal fun List<AnniversaryDateEntity>.visibleIn(ids: Set<String>) =
  filter { it.relationshipId == null || it.relationshipId in ids }

internal fun InLoveDao.visibleIds(): Flow<Set<String>> = observeRelationships().map { visibleRelationshipIds(it) }

internal suspend fun InLoveDao.visibleAnniversaryDatesList(): List<AnniversaryDateEntity> =
  getAnniversaryDatesList().visibleIn(visibleRelationshipIds(getAllRelationshipsSync()))
