package com.example.data.media

import com.example.data.model.CoupleProfileEntity
import com.example.data.model.OnlineRelationshipEntity
import com.example.data.model.OnlineUserEntity
import com.example.data.model.SharedMemoryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class MemoryBackupTest {

  @get:Rule
  val tmp = TemporaryFolder()

  private fun memory(
    syncId: String = "s1",
    createdAt: Long = 100L,
    relationshipId: String? = null,
    updatedAt: Long = 555L
  ) = SharedMemoryEntity(
    title = "Chuyen di", dateText = "2026-01-01", note = "ghi chu", photoUri = "media/a.jpg",
    videoUri = "media/v.mp4", relationshipId = relationshipId, syncId = syncId,
    createdAt = createdAt, updatedAt = updatedAt, authorName = "Minh"
  )

  private val profile = CoupleProfileEntity(
    partner1Name = "Minh", partner1Birthday = "", partner1ProfilePicture = "",
    partner2Name = "Lan", partner2Birthday = "", partner2ProfilePicture = ""
  )
  private val me = OnlineUserEntity(uid = "me", displayName = "Minh", coupleCode = "C1", isCurrentUser = true)
  private val partner = OnlineUserEntity(uid = "p", displayName = "Hoa", coupleCode = "C2")
  private val rel = OnlineRelationshipEntity(relationshipId = "r1", user1 = "me", user2 = "p", startDate = 1L)

  private fun snap(m: List<SharedMemoryEntity>, p: CoupleProfileEntity? = profile) =
    MemoryBackup.snapshot(m, p, listOf(rel), listOf(me, partner), me, nowMs = 9_000L)

  @Test
  fun partnerName_usesTheOtherUserOfTheRelationship() {
    val e = snap(listOf(memory(relationshipId = "r1"))).memories.single()
    assertEquals("Hoa", e.partnerName)
  }

  @Test
  fun partnerName_otherSideWhenMeIsUser2() {
    val flipped = rel.copy(user1 = "p", user2 = "me")
    val e = MemoryBackup.snapshot(listOf(memory(relationshipId = "r1")), profile, listOf(flipped), listOf(me, partner), me, 1L)
      .memories.single()
    assertEquals("Hoa", e.partnerName)
  }

  @Test
  fun partnerName_fallsBackToPartner2Name_whenNoRelationshipRow() {
    assertEquals("Lan", snap(listOf(memory(relationshipId = null))).memories.single().partnerName)
    assertEquals("Lan", snap(listOf(memory(relationshipId = "missing"))).memories.single().partnerName)
  }

  @Test
  fun partnerName_isEmpty_withoutProfileAndRelationship() {
    assertEquals("", snap(listOf(memory()), p = null).memories.single().partnerName)
  }

  @Test
  fun blankSyncId_isWrittenAsLegacyCreatedAt() {
    val e = snap(listOf(memory(syncId = "", createdAt = 4242L))).memories.single()
    assertEquals("legacy_4242", e.syncId)
  }

  @Test
  fun jsonRoundTrip_keepsFieldsIncludingUpdatedAt() {
    val file = snap(listOf(memory(updatedAt = 777L, relationshipId = "r1")))
    MemoryBackup.writeManifest(tmp.root, file)
    val back = MemoryBackup.decode(File(tmp.root, "media/${MediaPath.BACKUP_MANIFEST}").readText())
    assertEquals(file, back)
    assertEquals(777L, back!!.memories.single().updatedAt)
    assertEquals(9_000L, back.savedAt)
  }

  @Test
  fun manifest_neverContainsSecrets() {
    MemoryBackup.writeManifest(tmp.root, snap(listOf(memory())))
    val text = File(tmp.root, "media/${MediaPath.BACKUP_MANIFEST}").readText().lowercase()
    listOf("password", "token", "pin", "email").forEach { assertFalse(it, text.contains("\"$it")) }
  }

  @Test
  fun garbage_decodesToNull() {
    assertNull(MemoryBackup.decode("not json {{"))
    assertNull(MemoryBackup.decode(""))
    assertNull(MemoryBackup.decode("{\"memories\":\"oops\"}"))
  }

  @Test
  fun emptyList_deletesTheManifest() {
    MemoryBackup.writeManifest(tmp.root, snap(listOf(memory())))
    val target = File(tmp.root, "media/${MediaPath.BACKUP_MANIFEST}")
    assertTrue(target.isFile)
    MemoryBackup.deleteManifest(tmp.root)
    assertFalse(target.exists())
  }

  @Test
  fun write_leavesNoTmpFile_andReplacesAnExistingManifest() {
    MemoryBackup.writeManifest(tmp.root, snap(listOf(memory(syncId = "a"))))
    MemoryBackup.writeManifest(tmp.root, snap(listOf(memory(syncId = "b"))))
    val dir = File(tmp.root, "media")
    assertEquals(listOf(MediaPath.BACKUP_MANIFEST), dir.list()!!.toList())
    val back = MemoryBackup.decode(File(dir, MediaPath.BACKUP_MANIFEST).readText())
    assertEquals("b", back!!.memories.single().syncId)
  }
}
