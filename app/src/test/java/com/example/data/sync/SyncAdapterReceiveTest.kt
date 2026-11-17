package com.example.data.sync

import com.example.data.model.RelationshipStatus
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The receive side works on plain Maps (doc.data), so a wrong-typed remote field can never throw:
 * it takes the same default a missing key takes. These defaults mirror `memoryReceiveDefaults` /
 * `anniversaryReceiveDefaults` in GiftAndSyncGoldenTest (a data table there; this is the guard that runs the real code).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncAdapterReceiveTest {

  private val now = 1_700_000_000_000L

  @Test
  fun memory_missingOptionalKeys_giveGoldenDefaults() {
    val m = MemorySyncAdapter.fromFirestoreMap(mapOf("syncId" to "mem-recv", "title" to "Tieu de"), now)!!
    assertEquals("mem-recv", m.syncId)
    assertEquals("Tieu de", m.title)
    assertEquals("", m.dateText)
    assertEquals("", m.note)
    assertEquals("", m.photoUri)
    assertEquals("", m.location)
    assertFalse(m.isFavorite)
    assertEquals("Kỷ Niệm Ngày Yêu", m.anniversaryTitle)
    assertEquals(now, m.createdAt)
    assertNull(m.relationshipId)
    assertEquals("", m.authorId)
    assertEquals("Bạn", m.authorName)
    assertEquals("IMAGE", m.mediaType)
    assertNull(m.videoUri)
    assertNull(m.cloudinaryPublicId)
    assertEquals("", m.fileSizeFormatted)
    assertEquals(0, m.durationSeconds)
    assertEquals("COUPLE_ONLY", m.privacyLevel)
    assertEquals(0L, m.updatedAt)
    assertFalse(m.deleted)
    assertFalse(m.pendingSync)
  }

  @Test
  fun memory_wrongTypedOptionalFields_fallBackToDefaults_neverThrow() {
    val m = MemorySyncAdapter.fromFirestoreMap(
      mapOf(
        "syncId" to "mem-recv", "title" to "Tieu de",
        "durationSeconds" to "abc", "createdAt" to true, "isFavorite" to "yes", "note" to 5L,
        "updatedAt" to listOf(1), "relationshipId" to 7L, "deleted" to 1L
      ),
      now
    )!!
    assertEquals(0, m.durationSeconds)
    assertEquals(now, m.createdAt)
    assertFalse(m.isFavorite)
    assertEquals("", m.note)
    assertEquals(0L, m.updatedAt)
    assertNull(m.relationshipId)
    assertFalse(m.deleted)
  }

  @Test
  fun memory_wellTypedValues_arePreserved_andNumbersAcceptAnyNumberType() {
    val m = MemorySyncAdapter.fromFirestoreMap(
      mapOf(
        "syncId" to "s", "title" to "t", "durationSeconds" to 42, "createdAt" to 123L, "updatedAt" to 9.0,
        "isFavorite" to true, "relationshipId" to "r1", "deleted" to true
      ),
      now
    )!!
    assertEquals(42, m.durationSeconds)
    assertEquals(123L, m.createdAt)
    assertEquals(9L, m.updatedAt)
    assertTrue(m.isFavorite)
    assertEquals("r1", m.relationshipId)
    assertTrue(m.deleted)
  }

  @Test
  fun memory_missingOrWrongTypedRequiredFields_giveNull() {
    assertNull(MemorySyncAdapter.fromFirestoreMap(mapOf("title" to "t")))
    assertNull(MemorySyncAdapter.fromFirestoreMap(mapOf("syncId" to "s")))
    assertNull(MemorySyncAdapter.fromFirestoreMap(mapOf("syncId" to 1L, "title" to "t")))
    assertNull(MemorySyncAdapter.fromFirestoreMap(mapOf("syncId" to "s", "title" to listOf("t"))))
  }

  @Test
  fun anniversary_missingOptionalKeys_giveGoldenDefaults() {
    val a = AnniversarySyncAdapter.fromFirestoreMap(mapOf("syncId" to "ann-recv", "title" to "Tieu de"), now)!!
    assertEquals("", a.dateText)
    assertEquals("LOVE", a.type)
    assertEquals("", a.description)
    assertTrue(a.isAnnual)
    assertTrue(a.notificationEnabled)
    assertEquals(3, a.reminderDaysBefore)
    assertEquals(now, a.createdAt)
    assertNull(a.relationshipId)
    assertEquals(0L, a.updatedAt)
    assertFalse(a.deleted)
    assertFalse(a.pendingSync)
  }

  @Test
  fun anniversary_wrongTypedOptionalFields_fallBackToDefaults_neverThrow() {
    val a = AnniversarySyncAdapter.fromFirestoreMap(
      mapOf(
        "syncId" to "ann-recv", "title" to "Tieu de",
        "reminderDaysBefore" to "abc", "createdAt" to true, "isAnnual" to "no", "notificationEnabled" to 0L
      ),
      now
    )!!
    assertEquals(3, a.reminderDaysBefore)
    assertEquals(now, a.createdAt)
    assertTrue(a.isAnnual)
    assertTrue(a.notificationEnabled)
  }

  @Test
  fun anniversary_missingRequiredFields_giveNull() {
    assertNull(AnniversarySyncAdapter.fromFirestoreMap(mapOf("title" to "t")))
    assertNull(AnniversarySyncAdapter.fromFirestoreMap(mapOf("syncId" to "s", "title" to 5L)))
  }

  @Test
  fun relationshipFromMap_wellTyped_buildsTheOldEntity() {
    val r = relationshipFromMap(
      "r1",
      mapOf(
        "user1" to "a", "user2" to "b", "status" to "PENDING_BREAKUP", "startDate" to 5L, "startDateText" to "2026-01-01",
        "breakupRequestedBy" to "a", "breakupRequestedAt" to 6L, "createdAt" to 7L, "terminatedAt" to 8L
      ),
      now
    )!!
    assertEquals("r1", r.relationshipId)
    assertEquals("a", r.user1)
    assertEquals("b", r.user2)
    assertEquals(RelationshipStatus.PENDING_BREAKUP, r.status)
    assertEquals(5L, r.startDate)
    assertEquals("2026-01-01", r.startDateText)
    assertEquals("a", r.breakupRequestedBy)
    assertEquals(6L, r.breakupRequestedAt)
    assertEquals(7L, r.createdAt)
    assertEquals(8L, r.terminatedAt)
    assertEquals(now, r.updatedAt)
    assertFalse(r.pendingSync)
  }

  @Test
  fun relationshipFromMap_missingOptionalFields_takeTodaysDefaults() {
    val r = relationshipFromMap("r1", mapOf("user1" to "a", "user2" to "b"), now)!!
    assertEquals(RelationshipStatus.TERMINATED, r.status) // a missing status was always TERMINATED
    assertEquals(0L, r.startDate)
    assertEquals("", r.startDateText)
    assertNull(r.breakupRequestedBy)
    assertNull(r.breakupRequestedAt)
    assertEquals(0L, r.createdAt)
    assertNull(r.terminatedAt)
    // wrong-typed optional fields behave like missing ones
    val w = relationshipFromMap("r1", mapOf("user1" to "a", "user2" to "b", "startDate" to "x", "createdAt" to true), now)!!
    assertEquals(0L, w.startDate)
    assertEquals(0L, w.createdAt)
  }

  @Test
  fun relationshipFromMap_badRequiredFieldsOrStatus_giveNull() {
    assertNull(relationshipFromMap("r", mapOf("user2" to "b", "status" to "ACTIVE")))
    assertNull(relationshipFromMap("r", mapOf("user1" to "a", "status" to "ACTIVE")))
    assertNull(relationshipFromMap("r", mapOf("user1" to listOf("a"), "user2" to "b", "status" to "ACTIVE")))
    assertNull(relationshipFromMap("r", mapOf("user1" to "a", "user2" to "b", "status" to 1L)))
    assertNull(relationshipFromMap("r", mapOf("user1" to "a", "user2" to "b", "status" to "SOMETHING_NEW")))
  }

  @Test
  fun inviteFromMap_wellTyped_buildsTheOldEntity_andWrongTypesFallBack() {
    val i = inviteFromMap(
      "inv1",
      mapOf(
        "senderUid" to "u1", "senderName" to "An", "senderAge" to 30L, "targetCoupleCode" to "CODE",
        "targetUid" to "u2", "proposedStartDate" to 99L, "status" to "ACCEPTED", "createdAt" to 3L
      ),
      now
    )!!
    assertEquals("inv1", i.inviteId)
    assertEquals("u1", i.senderUid)
    assertEquals("An", i.senderName)
    assertEquals(30, i.senderAge)
    assertEquals("CODE", i.targetCoupleCode)
    assertEquals("u2", i.targetUid)
    assertEquals(99L, i.proposedStartDate)
    assertEquals("ACCEPTED", i.status)
    assertEquals(3L, i.createdAt)
    assertEquals(now, i.updatedAt)

    val d = inviteFromMap(
      "inv2", mapOf("senderUid" to "u1", "senderName" to listOf("x"), "senderAge" to "old", "status" to 5L, "targetUid" to 1L), now
    )!!
    assertEquals("Vô danh", d.senderName)
    assertEquals(0, d.senderAge)
    assertEquals("PENDING", d.status)
    assertNull(d.targetUid)
  }

  @Test
  fun inviteFromMap_missingOrWrongTypedSenderUid_givesNull() {
    assertNull(inviteFromMap("i", mapOf("senderName" to "An")))
    assertNull(inviteFromMap("i", mapOf("senderUid" to 5L)))
    assertNotNull(inviteFromMap("i", mapOf("senderUid" to "u")))
  }

  @Test
  fun guarded_doesNotLetOrdinaryFailuresEscape() {
    var after = false
    guarded("boom") { error("boom") }
    guarded("npe") { throw IllegalStateException("x") }
    after = true
    assertTrue(after)
  }

  @Test
  fun guarded_rethrowsCancellation() {
    try {
      guarded("cancel") { throw CancellationException("cancelled") }
      fail("CancellationException must escape guarded")
    } catch (e: CancellationException) {
      assertEquals("cancelled", e.message)
    }
  }
}
