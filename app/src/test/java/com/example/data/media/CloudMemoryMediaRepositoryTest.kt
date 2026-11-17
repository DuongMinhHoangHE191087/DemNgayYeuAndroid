package com.example.data.media

import com.example.data.cloud.CloudFunctionsCode
import com.example.data.cloud.CloudFunctionsException
import com.example.data.cloud.fakes.FakeCloudFunctionsClient
import com.example.data.media.fakes.FakeMediaUploader
import com.example.domain.media.MEMORY_PUBLIC_ID_PREFIX
import com.example.domain.media.MediaKind
import com.example.domain.media.MemoryMediaFile
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudMemoryMediaRepositoryTest {

  private val functions = FakeCloudFunctionsClient()
  private val uploader = FakeMediaUploader()
  private var now = 1_000_000L
  private val repo = CloudMemoryMediaRepository(functions, uploader) { now }

  private val memoryId = "m1"
  private val publicId = MEMORY_PUBLIC_ID_PREFIX + memoryId
  private val media = MemoryMediaFile("/tmp/a.jpg", MediaKind.IMAGE, "IMAGE/JPEG", 2048L, 0)

  private val putUrl = "https://acc.r2.cloudflarestorage.com/inlove-media/$publicId?X-Amz-Signature=sig"

  private fun enqueueSigned() = functions.enqueue(
    mapOf(
      "uploadUrl" to putUrl,
      "publicId" to publicId,
      "headers" to mapOf("content-type" to "image/jpeg", "content-length" to 2048, "if-none-match" to "*"),
    ),
  )

  private fun enqueueConfirmed() = functions.enqueue(mapOf("publicId" to publicId))

  private fun error(code: CloudFunctionsCode) = CloudFunctionsException(code, null)

  private fun confirmCalls() = functions.calls.count { it.name == "confirmMemoryUpload" }

  @Test
  fun upload_signThenUploadThenConfirm_inOrder() = runTest {
    var callsSeenByUploader: List<String>? = null
    uploader.onUpload = { callsSeenByUploader = functions.calls.map { it.name } }
    enqueueSigned()
    enqueueConfirmed()

    val result = repo.upload("rel1", memoryId, media)

    assertEquals(publicId, result)
    assertEquals(listOf("signMemoryUpload", "confirmMemoryUpload"), functions.calls.map { it.name })
    assertEquals(
      mapOf(
        "relationshipId" to "rel1",
        "memoryId" to memoryId,
        "kind" to "IMAGE",
        "sizeBytes" to 2048L,
        "durationSeconds" to 0,
        "mimeType" to "image/jpeg",
      ),
      functions.calls[0].data,
    )
    assertEquals(mapOf("relationshipId" to "rel1", "memoryId" to memoryId), functions.calls[1].data)
    assertEquals(1, uploader.calls.size)
    assertEquals(putUrl, uploader.calls[0].uploadUrl)
    assertEquals(
      mapOf("content-type" to "image/jpeg", "content-length" to "2048", "if-none-match" to "*"),
      uploader.calls[0].headers,
    )
    assertEquals(media, uploader.calls[0].media)
    // Lúc uploader chạy mới có sign; confirm chỉ đến sau đó.
    assertEquals(listOf("signMemoryUpload"), callsSeenByUploader)
  }

  @Test
  fun upload_uploaderFailure_neverCallsConfirm() = runTest {
    enqueueSigned()
    uploader.failure = IOException("Tải lên thất bại (HTTP 500)")

    val e = runCatching { repo.upload("rel1", memoryId, media) }.exceptionOrNull()

    assertTrue(e is IOException)
    assertEquals("Tải lên thất bại (HTTP 500)", e?.message)
    assertEquals(listOf("signMemoryUpload"), functions.calls.map { it.name })
  }

  @Test
  fun upload_confirmRetriesOnNotFoundThenSucceeds() = runTest {
    enqueueSigned()
    functions.enqueueError(error(CloudFunctionsCode.NOT_FOUND))
    functions.enqueueError(error(CloudFunctionsCode.UNAVAILABLE))
    enqueueConfirmed()

    val start = testScheduler.currentTime
    assertEquals(publicId, repo.upload("rel1", memoryId, media))

    assertEquals(3, confirmCalls())
    // backoff 1_500 * attempt: 1_500 sau lần 1, 3_000 sau lần 2.
    assertEquals(4_500L, testScheduler.currentTime - start)
  }

  @Test
  fun upload_confirmGivesUpAfterFiveAttempts() = runTest {
    enqueueSigned()
    repeat(5) { functions.enqueueError(error(CloudFunctionsCode.NOT_FOUND)) }

    val start = testScheduler.currentTime
    val e = runCatching { repo.upload("rel1", memoryId, media) }.exceptionOrNull()

    assertTrue(e is CloudFunctionsException)
    assertEquals(CloudFunctionsCode.NOT_FOUND, (e as CloudFunctionsException).code)
    assertEquals(5, confirmCalls())
    // 1_500 + 3_000 + 4_500 + 6_000, lần thứ 5 ném ngay không chờ.
    assertEquals(15_000L, testScheduler.currentTime - start)
  }

  @Test
  fun upload_confirmDoesNotRetryOnPermissionDenied() = runTest {
    enqueueSigned()
    functions.enqueueError(error(CloudFunctionsCode.PERMISSION_DENIED))

    val e = runCatching { repo.upload("rel1", memoryId, media) }.exceptionOrNull()

    assertEquals(CloudFunctionsCode.PERMISSION_DENIED, (e as CloudFunctionsException).code)
    assertEquals(1, confirmCalls())
  }

  @Test
  fun upload_confirmRetriesOnlyNotFoundAndUnavailable() = runTest {
    listOf(
      CloudFunctionsCode.INTERNAL,
      CloudFunctionsCode.DEADLINE_EXCEEDED,
      CloudFunctionsCode.UNAUTHENTICATED,
      CloudFunctionsCode.FAILED_PRECONDITION,
      CloudFunctionsCode.RESOURCE_EXHAUSTED,
    ).forEach { code ->
      val before = confirmCalls()
      enqueueSigned()
      functions.enqueueError(error(code))

      val e = runCatching { repo.upload("rel1", memoryId, media) }.exceptionOrNull()

      assertEquals(code, (e as CloudFunctionsException).code)
      assertEquals("$code must not be retried", 1, confirmCalls() - before)
    }
  }

  @Test
  fun deliveryUrl_isCachedUntilRefreshMargin() = runTest {
    val expires = now + 600_000L
    fun delivery(url: String) = mapOf("url" to url, "expiresAtMillis" to expires)
    functions.enqueue(delivery("https://acc.r2.cloudflarestorage.com/inlove-media/first.jpg?X-Amz-Signature=a"))
    functions.enqueue(delivery("https://acc.r2.cloudflarestorage.com/inlove-media/second.jpg?X-Amz-Signature=b"))

    assertEquals("https://acc.r2.cloudflarestorage.com/inlove-media/first.jpg?X-Amz-Signature=a", repo.deliveryUrl("rel1", memoryId))
    assertEquals(mapOf("relationshipId" to "rel1", "memoryId" to memoryId), functions.calls[0].data)
    assertEquals("getMemoryMediaUrl", functions.calls[0].name)

    // Còn đúng 1 ms trước mốc làm mới (expires - 60_000): vẫn dùng cache.
    now = expires - 60_001L
    assertEquals("https://acc.r2.cloudflarestorage.com/inlove-media/first.jpg?X-Amz-Signature=a", repo.deliveryUrl("rel1", memoryId))
    assertEquals(1, functions.calls.size)

    // Tới mốc làm mới: gọi lại máy chủ.
    now = expires - 60_000L
    assertEquals("https://acc.r2.cloudflarestorage.com/inlove-media/second.jpg?X-Amz-Signature=b", repo.deliveryUrl("rel1", memoryId))
    assertEquals(2, functions.calls.size)
  }

  @Test
  fun deliveryUrl_cacheIsPerMemory() = runTest {
    val expires = now + 600_000L
    val urlA = "https://acc.r2.cloudflarestorage.com/inlove-media/a.jpg?X-Amz-Signature=a"
    val urlB = "https://acc.r2.cloudflarestorage.com/inlove-media/b.jpg?X-Amz-Signature=b"
    functions.enqueue(mapOf("url" to urlA, "expiresAtMillis" to expires))
    functions.enqueue(mapOf("url" to urlB, "expiresAtMillis" to expires))

    assertEquals(urlA, repo.deliveryUrl("rel1", "mA"))
    assertEquals(urlB, repo.deliveryUrl("rel1", "mB"))
    // Lần hai mỗi kỷ niệm lấy đúng URL của mình từ cache, không gọi thêm máy chủ.
    assertEquals(urlA, repo.deliveryUrl("rel1", "mA"))
    assertEquals(urlB, repo.deliveryUrl("rel1", "mB"))
    assertEquals(2, functions.calls.size)
    assertEquals(mapOf("relationshipId" to "rel1", "memoryId" to "mB"), functions.calls[1].data)
  }

  @Test
  fun upload_videoPayload_carriesKindDurationSizeAndMime() = runTest {
    enqueueSigned()
    enqueueConfirmed()
    val video = MemoryMediaFile("/tmp/v.mp4", MediaKind.VIDEO, "Video/MP4", 5_000_000L, 12)

    repo.upload("rel1", memoryId, video)

    val data = functions.calls[0].data!!
    assertEquals("VIDEO", data["kind"])
    assertEquals(12, data["durationSeconds"])
    assertEquals(5_000_000L, data["sizeBytes"])
    assertEquals("video/mp4", data["mimeType"])
    assertEquals(video, uploader.calls.single().media)
  }

  @Test
  fun upload_signFailure_neverUploadsOrConfirms() = runTest {
    functions.enqueueError(error(CloudFunctionsCode.RESOURCE_EXHAUSTED))

    val e = runCatching { repo.upload("rel1", memoryId, media) }.exceptionOrNull()

    assertEquals(CloudFunctionsCode.RESOURCE_EXHAUSTED, (e as CloudFunctionsException).code)
    assertEquals(listOf("signMemoryUpload"), functions.calls.map { it.name })
    assertTrue(uploader.calls.isEmpty())
  }

  @Test
  fun upload_signedUrlOutsideObjectStore_isRejectedBeforeAnyUpload() = runTest {
    functions.enqueue(
      mapOf("uploadUrl" to "https://evil.example.com/upload", "publicId" to publicId, "headers" to mapOf("content-type" to "image/jpeg")),
    )

    val e = runCatching { repo.upload("rel1", memoryId, media) }.exceptionOrNull()

    assertTrue(e is IOException)
    assertTrue(uploader.calls.isEmpty())
    assertEquals(1, functions.calls.size)
  }
}
