package com.example.data.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class OkHttpMediaUploaderTest {

  @get:Rule val tmp = TemporaryFolder()

  private val url = "https://acc.r2.cloudflarestorage.com/inlove-media/inlove_mem_x" +
    "?X-Amz-Credential=AK%2F2026%2Fauto%2Fs3%2Faws4_request" +
    "&X-Amz-SignedHeaders=content-length%3Bcontent-type%3Bhost%3Bif-none-match&X-Amz-Signature=abc"

  private fun file(): File = tmp.newFile("r2.jpg").also { it.writeBytes(ByteArray(1234) { i -> i.toByte() }) }

  @Test
  fun buildPutRequest_putsRawFileWithSignedHeaders() {
    val f = file()
    val req = buildPutRequest(
      url,
      mapOf("content-type" to "image/jpeg", "content-length" to f.length().toString(), "if-none-match" to "*"),
      f,
    )
    assertEquals("PUT", req.method)
    assertEquals("image/jpeg", req.body!!.contentType().toString())
    assertEquals(f.length(), req.body!!.contentLength())
    assertEquals("*", req.header("If-None-Match"))
    assertNull(req.header("Content-Type"))
    assertNull(req.header("Content-Length"))
    assertEquals(url, req.url.toString())
  }

  @Test
  fun buildPutRequest_rejectsBadHeaders() {
    val f = file()
    val len = f.length().toString()
    assertThrows(IOException::class.java) {
      buildPutRequest(url, mapOf("content-type" to "image/jpeg", "content-length" to (len + "1")), f)
    }
    assertThrows(IOException::class.java) { buildPutRequest(url, mapOf("content-length" to len), f) }
    assertThrows(IOException::class.java) { buildPutRequest(url, mapOf("content-type" to "image/jpeg"), f) }
  }

  @Test
  fun isUploadAccepted_treats2xxAnd412AsDone() {
    listOf(200, 204, 412).forEach { assertTrue(it.toString(), isUploadAccepted(it, conditional = true)) }
    listOf(301, 302, 307, 403, 500).forEach { assertFalse(it.toString(), isUploadAccepted(it, conditional = true)) }
  }

  @Test
  fun isUploadAccepted_unconditional412IsRejected() {
    assertFalse(isUploadAccepted(412, conditional = false))
    assertTrue(isUploadAccepted(200, conditional = false))
    assertFalse(isUploadAccepted(302, conditional = false))
  }

  // Không có MockWebServer trong test deps: kiểm cấu hình client; 3xx không được theo nên rơi vào nhánh từ chối (IOException).
  @Test
  fun uploadClient_doesNotFollowRedirects() {
    val client = buildUploadClient()
    assertFalse(client.followRedirects)
    assertFalse(client.followSslRedirects)
  }
}
