package com.example.domain.media

import java.io.File
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Test

class MemoryMediaTest {

  private val mb = 1024L * 1024

  @Test
  fun validate_videoNeedsAtLeastOneSecond() {
    assert(validateMemoryMedia(MediaKind.VIDEO, "video/mp4", mb, 0) != null) { "duration 0 means the metadata failed; reject it" }
    assert(validateMemoryMedia(MediaKind.VIDEO, "video/mp4", mb, 1) == null)
  }

  @Test
  fun validate_videoMaxSixtySeconds() {
    assert(validateMemoryMedia(MediaKind.VIDEO, "video/mp4", mb, 60) == null)
    assert(validateMemoryMedia(MediaKind.VIDEO, "video/mp4", mb, 61) != null)
  }

  @Test
  fun validate_sizeLimitsAreInclusive() {
    assert(validateMemoryMedia(MediaKind.IMAGE, "image/jpeg", 10 * mb, 0) == null)
    assert(validateMemoryMedia(MediaKind.IMAGE, "image/jpeg", 10 * mb + 1, 0) != null)
    assert(validateMemoryMedia(MediaKind.VIDEO, "video/mp4", 50 * mb, 5) == null)
    assert(validateMemoryMedia(MediaKind.VIDEO, "video/mp4", 50 * mb + 1, 5) != null)
    assert(validateMemoryMedia(MediaKind.IMAGE, "image/jpeg", 0, 0) != null) { "an empty file is never uploaded" }
  }

  @Test
  fun validate_rejectsUnknownMimeAndKindMismatch() {
    assert(validateMemoryMedia(MediaKind.IMAGE, "image/gif", mb, 0) != null)
    assert(validateMemoryMedia(MediaKind.VIDEO, "video/avi", mb, 5) != null)
    assert(validateMemoryMedia(MediaKind.IMAGE, "video/mp4", mb, 0) != null)
    assert(validateMemoryMedia(MediaKind.IMAGE, "", mb, 0) != null)
  }

  @Test
  fun validate_mimeIsCaseInsensitive() {
    assert(validateMemoryMedia(MediaKind.IMAGE, "IMAGE/JPEG", mb, 0) == null)
  }

  @Test
  fun validate_messagesQuoteTheRealLimits() {
    val gif = validateMemoryMedia(MediaKind.IMAGE, "image/gif", mb, 0)
    assert(gif == "Định dạng ảnh chưa được hỗ trợ (JPG, PNG, WEBP, HEIC).") { "got: $gif" }
    val bigPhoto = validateMemoryMedia(MediaKind.IMAGE, "image/jpeg", 10 * mb + 1, 0)
    assert(bigPhoto == "Ảnh vượt quá 10 MB (10.0 MB). Vui lòng chọn ảnh nhẹ hơn.") { "got: $bigPhoto" }
    val avi = validateMemoryMedia(MediaKind.VIDEO, "video/avi", mb, 5)
    assert(avi == "Định dạng video chưa được hỗ trợ (MP4, MOV, WEBM).") { "got: $avi" }
    val bigVideo = validateMemoryMedia(MediaKind.VIDEO, "video/mp4", 50 * mb + 1, 5)
    assert(bigVideo == "Video vượt quá 50 MB (50.0 MB).") { "got: $bigVideo" }
  }

  @Test
  fun formatExtensionOf_matchesServerFormatTable() {
    assert(formatExtensionOf("image/jpeg") == "jpg")
    assert(formatExtensionOf("IMAGE/HEIF") == "heic")
    assert(formatExtensionOf("video/quicktime") == "mov")
    assert(formatExtensionOf("image/gif") == null)
  }

  private val photo = MemoryMediaFile(File("a.jpg"), MediaKind.IMAGE, "image/jpeg", mb)

  private class FakeRepository(
    private val uploadResult: () -> String = { "inlove_mem_m1" },
    private val urlResult: () -> String = { "https://res.cloudinary.com/demo/x" },
  ) : MemoryMediaRepository {
    var uploads = 0

    override suspend fun upload(relationshipId: String, memoryId: String, media: MemoryMediaFile): String {
      uploads++
      return uploadResult()
    }

    override suspend fun deliveryUrl(relationshipId: String, memoryId: String): String = urlResult()
  }

  @Test
  fun upload_withoutSharing_skipsRepository() = runBlocking {
    val repo = FakeRepository()

    val result = UploadMemoryMediaUseCase(repo)(shareWithCouple = false, relationshipId = "rel", memoryId = "m1", media = photo)

    assert(result.isSuccess && result.getOrNull() == null)
    assert(repo.uploads == 0)
  }

  @Test
  fun upload_withoutRelationshipOrMedia_skipsRepository() = runBlocking {
    val repo = FakeRepository()
    val useCase = UploadMemoryMediaUseCase(repo)

    assert(useCase(true, null, "m1", photo).getOrNull() == null)
    assert(useCase(true, " ", "m1", photo).getOrNull() == null)
    assert(useCase(true, "rel", "m1", null).getOrNull() == null)
    assert(repo.uploads == 0)
  }

  @Test
  fun upload_shared_returnsPublicIdFromRepository() = runBlocking {
    val repo = FakeRepository()

    val result = UploadMemoryMediaUseCase(repo)(true, "rel", "m1", photo)

    assert(result.getOrNull() == "inlove_mem_m1")
    assert(repo.uploads == 1)
  }

  @Test
  fun upload_repositoryFailure_isReturnedAsFailure() = runBlocking {
    val repo = FakeRepository(uploadResult = { throw IOException("mất mạng") })

    val result = UploadMemoryMediaUseCase(repo)(true, "rel", "m1", photo)

    assert(result.isFailure)
  }

  @Test
  fun upload_cancellation_isRethrownNotWrapped() {
    val repo = FakeRepository(uploadResult = { throw CancellationException("huỷ") })
    try {
      runBlocking { UploadMemoryMediaUseCase(repo)(true, "rel", "m1", photo) }
      assert(false) { "cancellation must propagate, not become a Result" }
    } catch (e: CancellationException) {
      // đúng: coroutine bị huỷ được ném tiếp
    }
  }

  @Test
  fun resolve_returnsDeliveryUrl() = runBlocking {
    val result = ResolveMemoryMediaUrlUseCase(FakeRepository())(currentRelationshipId = "rel", relationshipId = "rel", memoryId = "m1")

    assert(result.getOrNull() == "https://res.cloudinary.com/demo/x")
  }

  @Test
  fun resolve_repositoryFailure_isReturnedAsFailure() = runBlocking {
    val result = ResolveMemoryMediaUrlUseCase(FakeRepository(urlResult = { throw IOException("x") }))(currentRelationshipId = "rel", relationshipId = "rel", memoryId = "m1")

    assert(result.isFailure)
  }
}
