package com.example.data.repository

import com.example.domain.media.MEMORY_PUBLIC_ID_PREFIX
import com.example.domain.media.MediaKind
import com.example.domain.media.MemoryMediaFile
import com.example.domain.media.MemoryMediaRepository
import com.example.domain.media.ResolveMemoryMediaUrlUseCase
import com.example.domain.media.SaveSharedMemoryOutcome
import com.example.domain.media.SaveSharedMemoryUseCase
import com.example.domain.media.UploadMemoryMediaUseCase
import com.example.domain.media.validateMemoryMedia
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Quyền riêng tư của kỷ niệm: riêng tư không bao giờ tải lên, không xem được ảnh của cặp đôi khác,
// lỗi tải lên không ghi kỷ niệm, và ảnh của cặp đôi mình phát được qua URL có hạn.
class MemoryPrivacyTest {

  @Test
  fun privateMemory_withPhoto_neverUploads_andIsStoredWithoutPublicId() = runBlocking {
    val h = SaveHarness()
    assertEquals(SaveSharedMemoryOutcome.Saved(unpaired = false), h.save(wantsShare = false, media = photo()))
    assertTrue("Kỷ niệm riêng tư không được tải lên", h.repo.uploads.isEmpty())
    assertFalse(h.stored.single().shareWithCouple)
    assertNull(h.stored.single().publicId)
  }

  @Test
  fun unpairedShare_isKeptOnDevice_andNeverUploads() = runBlocking {
    val h = SaveHarness()
    assertEquals(SaveSharedMemoryOutcome.Saved(unpaired = true), h.save(relationshipId = null, media = photo()))
    assertTrue("Chưa ghép đôi thì không có gì để tải lên", h.repo.uploads.isEmpty())
    assertFalse(h.stored.single().shareWithCouple)
    assertNull(h.stored.single().publicId)
  }

  @Test
  fun signedOutShare_isRefused_withoutUploadOrStore() = runBlocking {
    val h = SaveHarness()
    assertEquals(SaveSharedMemoryOutcome.NotSignedIn, h.save(signedIn = false, media = photo()))
    assertTrue(h.repo.uploads.isEmpty())
    assertTrue(h.stored.isEmpty())
  }

  @Test
  fun invalidMedia_isRefused_beforeAnyUpload() = runBlocking {
    val h = SaveHarness()
    val tooLong = MemoryMediaFile(File("clip.mp4"), MediaKind.VIDEO, "video/mp4", 5_000_000L, durationSeconds = 61)
    val expected = checkNotNull(validateMemoryMedia(MediaKind.VIDEO, "video/mp4", 5_000_000L, 61))
    assertEquals(SaveSharedMemoryOutcome.InvalidMedia(expected), h.save(media = tooLong))
    assertTrue(h.repo.uploads.isEmpty())
    assertTrue(h.stored.isEmpty())
  }

  @Test
  fun uploadFailure_neverCallsStore_andReportsUploadFailed() = runBlocking {
    val h = SaveHarness(uploadFails = true)
    assertEquals(SaveSharedMemoryOutcome.UploadFailed, h.save(media = photo()))
    assertEquals("Phải thử tải lên đúng một lần", 1, h.repo.uploads.size)
    assertTrue("Tải lên lỗi thì kỷ niệm không được ghi", h.stored.isEmpty())
  }

  @Test
  fun sharedUpload_storesThePublicIdBoundToTheSameMemoryId() = runBlocking {
    val h = SaveHarness()
    assertEquals(SaveSharedMemoryOutcome.Saved(unpaired = false), h.save(media = photo()))
    val stored = h.stored.single()
    assertTrue(stored.shareWithCouple)
    assertEquals(listOf("rel_1" to stored.memoryId), h.repo.uploads)
    assertEquals(MEMORY_PUBLIC_ID_PREFIX + stored.memoryId, stored.publicId)
  }

  @Test
  fun textOnlyShare_storesWithoutAnyUpload() = runBlocking {
    val h = SaveHarness()
    assertEquals(SaveSharedMemoryOutcome.Saved(unpaired = false), h.save(media = null))
    assertTrue("Kỷ niệm chỉ có chữ không cần tải lên", h.repo.uploads.isEmpty())
    assertTrue(h.stored.single().shareWithCouple)
    assertNull(h.stored.single().publicId)
  }

  @Test
  fun storeFailure_isReportedAsSaveFailed() = runBlocking {
    val h = SaveHarness(storeError = IllegalStateException("Room đầy"))
    assertEquals(SaveSharedMemoryOutcome.SaveFailed, h.save())
  }

  @Test
  fun cancelledSave_isRethrown_notReportedAsSaveFailed() {
    val h = SaveHarness(storeError = CancellationException("huỷ"))
    var outcome: SaveSharedMemoryOutcome? = null
    val thrown = runCatching { runBlocking { outcome = h.save() } }.exceptionOrNull()
    assertTrue("Huỷ lưu phải được ném tiếp, không đổi thành lỗi", thrown is CancellationException)
    assertNull(outcome)
  }

  @Test
  fun resolve_anotherCoupleOrNoCouple_isRefused_withoutAnyRequest() = runBlocking {
    val repo = FakeMemoryMediaRepository()
    val resolve = ResolveMemoryMediaUrlUseCase(repo)
    assertTrue(resolve(currentRelationshipId = "rel_1", relationshipId = "rel_other", memoryId = "mem_1").isFailure)
    assertTrue(resolve(currentRelationshipId = null, relationshipId = "rel_1", memoryId = "mem_1").isFailure)
    assertTrue("Không được gửi yêu cầu cho kỷ niệm của cặp đôi khác", repo.deliveries.isEmpty())
  }

  @Test
  fun resolve_ownCouple_returnsDeliveryUrl_forRemotePlayback() = runBlocking {
    val repo = FakeMemoryMediaRepository()
    val url = ResolveMemoryMediaUrlUseCase(repo)(currentRelationshipId = "rel_1", relationshipId = "rel_1", memoryId = "mem_1").getOrThrow()
    assertTrue(url.startsWith("https://res.cloudinary.com/"))
    assertEquals(listOf("rel_1" to "mem_1"), repo.deliveries)
  }
}

private data class Stored(val memoryId: String, val shareWithCouple: Boolean, val publicId: String?)

private fun photo() = MemoryMediaFile(File("photo.jpg"), MediaKind.IMAGE, "image/jpeg", 2_000_000L)

private class SaveHarness(
  uploadFails: Boolean = false,
  private val storeError: Throwable? = null,
) {
  val repo = FakeMemoryMediaRepository(uploadFails = uploadFails)
  val stored = mutableListOf<Stored>()
  private val useCase = SaveSharedMemoryUseCase(UploadMemoryMediaUseCase(repo))

  suspend fun save(
    signedIn: Boolean = true,
    relationshipId: String? = "rel_1",
    wantsShare: Boolean = true,
    media: MemoryMediaFile? = null,
  ): SaveSharedMemoryOutcome = useCase(signedIn, relationshipId, wantsShare, media) { memoryId, shareWithCouple, publicId ->
    storeError?.let { throw it }
    stored += Stored(memoryId, shareWithCouple, publicId)
  }
}

private class FakeMemoryMediaRepository(private val uploadFails: Boolean = false) : MemoryMediaRepository {
  val uploads = mutableListOf<Pair<String, String>>()
  val deliveries = mutableListOf<Pair<String, String>>()

  override suspend fun upload(relationshipId: String, memoryId: String, media: MemoryMediaFile): String {
    uploads += relationshipId to memoryId
    if (uploadFails) throw IOException("mất mạng")
    return MEMORY_PUBLIC_ID_PREFIX + memoryId
  }

  override suspend fun deliveryUrl(relationshipId: String, memoryId: String): String {
    deliveries += relationshipId to memoryId
    return "https://res.cloudinary.com/demo/image/upload/$memoryId.jpg"
  }
}
