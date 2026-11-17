package com.example.data.media

import com.example.data.cloud.CloudFunctionsCode
import com.example.data.cloud.CloudFunctionsException
import com.example.data.cloud.fakes.FakeCloudFunctionsClient
import com.example.data.media.fakes.FakeMediaUploader
import com.example.domain.media.MediaKind
import com.example.domain.media.MemoryMediaFile
import java.io.File
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AvatarUploaderTest {

  @get:Rule
  val tmp = TemporaryFolder()

  private val functions = FakeCloudFunctionsClient()
  private val media = FakeMediaUploader()
  private val avatarUploader = AvatarUploader(functions, media)

  private val publicId = "inlove_avt_uid1"
  private val putUrl = "https://acct.r2.cloudflarestorage.com/avatars/inlove_avt_uid1?X-Amz-Signature=abc"
  private val publicUrl = "https://cdn.example.com/inlove_avt_uid1?v=1700000000000"

  private fun jpegFile(): File = tmp.newFile("a.jpg").apply { writeBytes(byteArrayOf(1, 2, 3, 4, 5)) }

  private fun signedHeaders(file: File) = mapOf(
    "content-type" to "image/jpeg",
    "content-length" to file.length().toString(),
    "if-none-match" to "*",
    "cache-control" to "public, max-age=31536000, immutable",
  )

  private fun reply(
    file: File,
    uploadUrl: String = putUrl,
    publicId: String = this.publicId,
    publicUrl: String = this.publicUrl,
  ) = mapOf(
    "uploadUrl" to uploadUrl,
    "headers" to signedHeaders(file),
    "publicId" to publicId,
    "publicUrl" to publicUrl,
  )

  /** Chạy upload, trả lỗi ném ra (null nếu không lỗi). */
  private fun uploadError(file: File): Throwable? =
    runCatching { runBlocking { avatarUploader.upload(file, "uid1") } }.exceptionOrNull()

  @Test
  fun upload_happyPath_signsThenPutsWithEverySignedHeader() {
    val file = jpegFile()
    functions.enqueue(reply(file))

    val result = runBlocking { avatarUploader.upload(file, "uid1") }

    assertEquals(publicUrl, result)
    assertEquals(1, functions.calls.size)
    assertEquals("signAvatarUpload", functions.calls[0].name)
    assertEquals(mapOf("contentType" to "image/jpeg", "contentLength" to file.length()), functions.calls[0].data)
    assertTrue(functions.calls[0].data!!["contentLength"] is Long)
    val put = media.calls.single()
    assertEquals(putUrl, put.uploadUrl)
    assertEquals(signedHeaders(file), put.headers)
    assertEquals("public, max-age=31536000, immutable", put.headers["cache-control"])
    assertEquals(MemoryMediaFile(file.path, MediaKind.IMAGE, "image/jpeg", file.length()), put.media)
  }

  @Test
  fun upload_foreignPublicId_isRejectedBeforePut() {
    val file = jpegFile()
    functions.enqueue(reply(file, publicId = "inlove_mem_x"))

    assertTrue(uploadError(file) is IOException)
    assertTrue(media.calls.isEmpty())
  }

  @Test
  fun upload_publicIdOfAnotherUid_isRejectedBeforePut() {
    val file = jpegFile()
    functions.enqueue(reply(file, publicId = "inlove_avt_uid2", publicUrl = "https://cdn.example.com/inlove_avt_uid2?v=1"))

    assertTrue(uploadError(file) is IOException)
    assertTrue(media.calls.isEmpty())
  }

  @Test
  fun upload_publicIdOfSameUid_returnsPublicUrl() {
    val file = jpegFile()
    functions.enqueue(reply(file))

    assertEquals(publicUrl, runBlocking { avatarUploader.upload(file, "uid1") })
    assertEquals(1, media.calls.size)
  }

  @Test
  fun upload_publicIdWithBadChars_isRejectedBeforePut() {
    val file = jpegFile()
    functions.enqueue(reply(file, publicId = "inlove_avt_a/b"))

    assertTrue(uploadError(file) is IOException)
    assertTrue(media.calls.isEmpty())
  }

  @Test
  fun upload_nonHttpsPublicUrl_isRejectedBeforePut() {
    val file = jpegFile()
    functions.enqueue(reply(file, publicUrl = "http://cdn.example.com/inlove_avt_uid1"))

    assertTrue(uploadError(file) is IOException)
    assertTrue(media.calls.isEmpty())
  }

  @Test
  fun upload_publicUrlPathNotEndingWithPublicId_isRejectedBeforePut() {
    val file = jpegFile()
    functions.enqueue(reply(file, publicUrl = "https://cdn.example.com/inlove_avt_other?v=1"))

    assertTrue(uploadError(file) is IOException)
    assertTrue(media.calls.isEmpty())
  }

  @Test
  fun upload_uploadUrlOutsideR2_isRejectedBeforePut() {
    val file = jpegFile()
    functions.enqueue(reply(file, uploadUrl = "https://evil.example.com/x"))

    assertTrue(uploadError(file) is IOException)
    assertTrue(media.calls.isEmpty())
  }

  @Test
  fun upload_cloudFunctionsException_propagatesWithoutPut() {
    val file = jpegFile()
    functions.enqueueError(CloudFunctionsException(CloudFunctionsCode.RESOURCE_EXHAUSTED, null))

    val e = uploadError(file)

    assertEquals(CloudFunctionsCode.RESOURCE_EXHAUSTED, (e as CloudFunctionsException).code)
    assertTrue(media.calls.isEmpty())
  }

  @Test
  fun upload_uploaderFailure_propagates() {
    val file = jpegFile()
    functions.enqueue(reply(file))
    media.failure = IOException("mất mạng")

    val e = uploadError(file)

    assertTrue(e is IOException)
    assertEquals("mất mạng", e?.message)
    assertEquals(1, media.calls.size)
  }
}
