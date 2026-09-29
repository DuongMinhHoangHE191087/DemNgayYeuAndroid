package com.example.data.sync

import com.google.firebase.firestore.DocumentSnapshot

/**
 * Maps one Room entity type to/from its Firestore document shape. Kept deliberately
 * non-generic-reflection (no `.toObject()`): several Room entities in this codebase have
 * non-null fields without defaults, so Firestore's POJO deserializer cannot always
 * synthesize a no-arg constructor for them. Manual field reads are also the pattern
 * already used elsewhere in this codebase (InLoveRepository's Firestore preset fetches).
 */
interface EntitySyncAdapter<T> {
  /** Stable identifier stored in SyncOutboxEntity.entityType and used to dispatch in SyncWorker/SyncCoordinator. */
  val entityType: String

  /** Firestore collection this entity type lives in, scoped to one couple's relationship. */
  fun collectionPath(relationshipId: String): String

  /** Local entity -> Firestore field map. Local-only bookkeeping fields (e.g. pendingSync) are excluded. */
  fun toFirestoreMap(entity: T): Map<String, Any?>

  /** Firestore document -> local entity, or null if required fields are missing (corrupt/partial doc). */
  fun fromFirestoreDoc(doc: DocumentSnapshot): T?
}
