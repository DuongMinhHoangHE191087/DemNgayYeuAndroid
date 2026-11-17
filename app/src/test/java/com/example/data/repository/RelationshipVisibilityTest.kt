package com.example.data.repository

import com.example.data.model.OnlineRelationshipEntity
import com.example.data.model.RelationshipStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class RelationshipVisibilityTest {
  private fun rel(id: String, a: String, b: String, status: String) =
    OnlineRelationshipEntity(
      relationshipId = id, user1 = a, user2 = b, startDate = 0L, startDateText = "",
      status = status, breakupRequestedBy = null, breakupRequestedAt = null,
      createdAt = 0L, terminatedAt = null, updatedAt = 0L, pendingSync = false
    )

  @Test fun singleHidesEveryRelationship() {
    assertEquals(emptySet<String>(), visibleRelationshipIds(listOf(rel("r1", "me", "ex", RelationshipStatus.TERMINATED))))
    assertEquals(emptySet<String>(), visibleRelationshipIds(emptyList()))
  }

  @Test fun samePartnerBringsOldRowsBack() {
    val ids = visibleRelationshipIds(listOf(
      rel("r1", "me", "ex", RelationshipStatus.TERMINATED),
      rel("r2", "ex", "me", RelationshipStatus.ACTIVE)
    ))
    assertEquals(setOf("r1", "r2"), ids)
  }

  @Test fun newPartnerNeverSeesTheExsRows() {
    val ids = visibleRelationshipIds(listOf(
      rel("r1", "me", "ex", RelationshipStatus.TERMINATED),
      rel("r2", "me", "new", RelationshipStatus.ACTIVE)
    ))
    assertEquals(setOf("r2"), ids)
  }
}
