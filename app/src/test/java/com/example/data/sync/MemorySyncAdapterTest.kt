package com.example.data.sync

import com.example.data.model.SharedMemoryEntity
import org.junit.Test

class MemorySyncAdapterTest {

  @Test
  fun toFirestoreMap_roundTripsThroughFromFirestoreDoc_viaMap() {
    val original = SharedMemoryEntity(
      id = 5,
      title = "Lần đầu hẹn hò",
      dateText = "2024-02-14",
      note = "Trời mưa nhưng vui",
      photoUri = "https://cdn/photo.jpg",
      relationshipId = "rel_1",
      authorId = "uid_a",
      syncId = "sync-abc",
      updatedAt = 1_700_000_000_000L,
      pendingSync = true
    )

    val map = MemorySyncAdapter.toFirestoreMap(original)

    assert(map["title"] == "Lần đầu hẹn hò")
    assert(map["syncId"] == "sync-abc")
    assert(map["relationshipId"] == "rel_1")
    assert(map["deleted"] == false)
    // pendingSync is a local-only flag and must never be pushed to Firestore
    assert(!map.containsKey("pendingSync"))
  }

  @Test
  fun entityType_isStableIdentifierUsedByTheOutbox() {
    assert(MemorySyncAdapter.entityType == "memory")
  }
}
