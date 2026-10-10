package com.example.data.media

import java.io.IOException
import org.junit.Test

class FirebaseMemoryMediaParseTest {

  private val publicId = "inlove_mem_11111111-2222-3333-4444-555555555555"

  private fun signed(
    uploadUrl: String = "https://api.cloudinary.com/v1_1/demo/image/upload",
    publicIdValue: Any? = publicId,
  ): Map<String, Any?> = mapOf(
    "publicId" to publicIdValue,
    "uploadUrl" to uploadUrl,
    "fields" to mapOf("public_id" to publicId, "timestamp" to "1700000000", "signature" to "abc", "api_key" to "123"),
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
  fun parseSignedUpload_readsFieldsAsStrings() {
    val parsed = parseSignedUpload(signed(), publicId)

    assert(parsed.uploadUrl == "https://api.cloudinary.com/v1_1/demo/image/upload")
    assert(parsed.fields["timestamp"] == "1700000000")
    assert(parsed.fields["public_id"] == publicId)
  }

  @Test
  fun parseSignedUpload_rejectsOtherHostsAndPlainHttp() {
    assertRejected { parseSignedUpload(signed(uploadUrl = "http://api.cloudinary.com/v1_1/demo/image/upload"), publicId) }
    assertRejected { parseSignedUpload(signed(uploadUrl = "https://evil.example.com/upload"), publicId) }
    assertRejected { parseSignedUpload(signed(uploadUrl = "https://api.cloudinary.com.evil.example/upload"), publicId) }
  }

  @Test
  fun parseSignedUpload_rejectsPublicIdMismatchAndMissingFields() {
    assertRejected { parseSignedUpload(signed(publicIdValue = "inlove_mem_other"), publicId) }
    assertRejected { parseSignedUpload(signed().filterKeys { it != "fields" }, publicId) }
  }

  @Test
  fun parseConfirmed_requiresTheSamePublicId() {
    assert(parseConfirmed(mapOf("publicId" to publicId, "bytes" to 1234L), publicId) == publicId)
    assertRejected { parseConfirmed(mapOf("publicId" to "inlove_mem_other"), publicId) }
  }

  @Test
  fun parseDelivery_acceptsCloudinaryHostsOnly() {
    val parsed = parseDelivery(
      mapOf("url" to "https://res.cloudinary.com/demo/image/authenticated/x.jpg", "expiresAtMillis" to 1_700_000_900_000L),
    )

    assert(parsed.expiresAtMillis == 1_700_000_900_000L)
    assertRejected { parseDelivery(mapOf("url" to "http://res.cloudinary.com/x", "expiresAtMillis" to 1L)) }
    assertRejected { parseDelivery(mapOf("url" to "https://cloudinary.com.evil.example/x", "expiresAtMillis" to 1L)) }
    assertRejected { parseDelivery(mapOf("url" to "https://res.cloudinary.com/x")) }
    assertRejected { parseDelivery(mapOf("url" to "https://res.cloudinary.com/x", "expiresAtMillis" to "soon")) }
  }

  @Test
  fun parseDelivery_acceptsNumericExpiryFromAnySdkNumberType() {
    val parsed = parseDelivery(mapOf("url" to "https://res.cloudinary.com/x", "expiresAtMillis" to 1_700_000_900_000.0))

    assert(parsed.expiresAtMillis == 1_700_000_900_000L)
  }
}
