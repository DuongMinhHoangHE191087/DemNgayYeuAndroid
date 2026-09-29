package com.example.data.sync

import com.example.data.model.AnniversaryDateEntity
import org.junit.Test

class AnniversarySyncAdapterTest {

  @Test
  fun toFirestoreMap_includesAllSyncedFields() {
    val original = AnniversaryDateEntity(
      id = 9,
      title = "Ngày cầu hôn",
      dateText = "2025-12-24",
      type = "PROPOSAL",
      relationshipId = "rel_1",
      syncId = "sync-xyz",
      updatedAt = 1_700_000_001_000L
    )

    val map = AnniversarySyncAdapter.toFirestoreMap(original)

    assert(map["title"] == "Ngày cầu hôn")
    assert(map["type"] == "PROPOSAL")
    assert(map["syncId"] == "sync-xyz")
    assert(map["relationshipId"] == "rel_1")
  }

  @Test
  fun entityType_isStableIdentifierUsedByTheOutbox() {
    assert(AnniversarySyncAdapter.entityType == "anniversary")
  }
}
