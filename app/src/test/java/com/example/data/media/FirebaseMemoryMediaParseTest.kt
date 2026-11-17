package com.example.data.media

import java.io.IOException
import org.junit.Test

class FirebaseMemoryMediaParseTest {

  private val publicId = "inlove_mem_11111111-2222-3333-4444-555555555555"

  private val goodUploadUrl = "https://acc.r2.cloudflarestorage.com/inlove-media/$publicId?X-Amz-Signature=abc"

  private fun signed(
    uploadUrl: String = goodUploadUrl,
    publicIdValue: Any? = publicId,
  ): Map<String, Any?> = mapOf(
    "publicId" to publicIdValue,
    "uploadUrl" to uploadUrl,
    "headers" to mapOf("content-type" to "image/jpeg", "content-length" to 2048, "if-none-match" to "*"),
    "expiresAtMillis" to 1_700_000_000_000L,
  )

  private inline fun assertRejected(block: () -> Unit) {
    try {
      block()
      assert(false) { "expected the response to be rejected" }
    } catch (e: IOException) {
      // đúng: phản hồi không hợp lệ bị coi là lỗi
    }
  }

  @Test
  fun parseSignedUpload_readsHeadersAsStrings() {
    val parsed = parseSignedUpload(signed(), publicId)

    assert(parsed.uploadUrl == goodUploadUrl)
    assert(parsed.headers["content-length"] == "2048")
    assert(parsed.headers["if-none-match"] == "*")
    assert(parsed.headers["content-type"] == "image/jpeg")
  }

  @Test
  fun parseSignedUpload_acceptsUppercaseHostAndReturnsItUnchanged() {
    val upper = "https://ACC.R2.CLOUDFLARESTORAGE.COM/inlove-media/$publicId?X-Amz-Signature=abc"

    val parsed = parseSignedUpload(signed(uploadUrl = upper), publicId)

    assert(parsed.uploadUrl == upper)
  }

  @Test
  fun parseSignedUpload_rejectsNonR2HostsPlainHttpAndTrickyUrls() {
    listOf(
      "https://api.cloudinary.com/v1_1/demo/image/upload",
      "http://acc.r2.cloudflarestorage.com/inlove-media/x?X-Amz-Signature=abc",
      "https://evil.example.com/upload",
      "https://evil.com\\@acc.r2.cloudflarestorage.com/x",
      "https://acc.r2.cloudflarestorage.com.evil.example/x",
      "https://acc.r2.cloudflarestorage.com./x",
      "https://acc.r2.cloudflarestorage.com:8443/x",
      "https://r2.cloudflarestorage.com/x",
    ).forEach { url ->
      assertRejected { parseSignedUpload(signed(uploadUrl = url), publicId) }
    }
  }

  @Test
  fun parseSignedUpload_rejectsPublicIdMismatchMissingHeadersAndOldFields() {
    assertRejected { parseSignedUpload(signed(publicIdValue = "inlove_mem_other"), publicId) }
    assertRejected { parseSignedUpload(signed().filterKeys { it != "headers" }, publicId) }
    assertRejected {
      parseSignedUpload(
        signed().filterKeys { it != "headers" } + ("fields" to mapOf("signature" to "abc")),
        publicId,
      )
    }
  }

  @Test
  fun parseConfirmed_requiresTheSamePublicId() {
    assert(parseConfirmed(mapOf("publicId" to publicId, "bytes" to 1234L), publicId) == publicId)
    assertRejected { parseConfirmed(mapOf("publicId" to "inlove_mem_other"), publicId) }
  }

  @Test
  fun parseDelivery_acceptsR2HostsOnly() {
    val url = "https://acc.r2.cloudflarestorage.com/inlove-media/x.jpg?X-Amz-Expires=900&X-Amz-Signature=abc"
    val parsed = parseDelivery(mapOf("url" to url, "expiresAtMillis" to 1_700_000_900_000L))

    assert(parsed.url == url)
    assert(parsed.expiresAtMillis == 1_700_000_900_000L)
    assertRejected { parseDelivery(mapOf("url" to "http://acc.r2.cloudflarestorage.com/x", "expiresAtMillis" to 1L)) }
    assertRejected {
      parseDelivery(mapOf("url" to "https://res.cloudinary.com/demo/image/authenticated/x.jpg", "expiresAtMillis" to 1L))
    }
    assertRejected { parseDelivery(mapOf("url" to "https://acc.r2.cloudflarestorage.com.evil.example/x", "expiresAtMillis" to 1L)) }
    assertRejected { parseDelivery(mapOf("url" to url)) }
    assertRejected { parseDelivery(mapOf("url" to url, "expiresAtMillis" to "soon")) }
  }

  @Test
  fun parseDelivery_acceptsNumericExpiryFromAnySdkNumberType() {
    val parsed = parseDelivery(
      mapOf("url" to "https://acc.r2.cloudflarestorage.com/inlove-media/x.jpg?X-Amz-Signature=abc", "expiresAtMillis" to 1_700_000_900_000.0),
    )

    assert(parsed.expiresAtMillis == 1_700_000_900_000L)
  }
}
