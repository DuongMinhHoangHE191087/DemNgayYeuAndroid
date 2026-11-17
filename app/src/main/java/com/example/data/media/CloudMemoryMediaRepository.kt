package com.example.data.media

import com.example.data.cloud.AndroidCloudFunctionsClient
import com.example.data.cloud.CloudFunctionsClient
import com.example.data.cloud.CloudFunctionsCode
import com.example.data.cloud.CloudFunctionsException
import com.example.domain.media.MEMORY_PUBLIC_ID_PREFIX
import com.example.domain.media.MemoryMediaFile
import com.example.domain.media.MemoryMediaRepository
import kotlinx.coroutines.delay
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.IOException
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Ảnh/video kỷ niệm: máy chủ cấp chỗ và xác nhận, file đi thẳng lên kho đối tượng riêng tư (Cloudflare R2) bằng URL ký số một lần.
 * Không có API key hay secret trên máy; chỉ có URL ký số ngắn hạn do máy chủ cấp.
 */
class CloudMemoryMediaRepository(
  private val functions: CloudFunctionsClient = AndroidCloudFunctionsClient(),
  private val uploader: MediaUploader = OkHttpMediaUploader(),
  private val clock: () -> Long = System::currentTimeMillis,
) : MemoryMediaRepository {

  // ponytail: cache không giới hạn, mỗi kỷ niệm đã xem một mục nhỏ; thêm LRU nếu số lượng lớn.
  private val urlCache = ConcurrentHashMap<String, CachedUrl>()

  override suspend fun upload(relationshipId: String, memoryId: String, media: MemoryMediaFile): String {
    val publicId = MEMORY_PUBLIC_ID_PREFIX + memoryId
    val signed = parseSignedUpload(
      functions.call(
        "signMemoryUpload",
        mapOf(
          "relationshipId" to relationshipId,
          "memoryId" to memoryId,
          "kind" to media.kind.name,
          "sizeBytes" to media.sizeBytes,
          "durationSeconds" to media.durationSeconds,
          "mimeType" to media.mimeType.lowercase(Locale.ROOT),
        ),
      ),
      publicId,
    )
    uploader.upload(signed.uploadUrl, signed.headers, media)
    return confirmWithRetry(relationshipId, memoryId, publicId)
  }

  override suspend fun deliveryUrl(relationshipId: String, memoryId: String): String {
    val now = clock()
    urlCache[memoryId]?.takeIf { it.refreshAtMillis > now }?.let { return it.url }
    val fresh = parseDelivery(functions.call("getMemoryMediaUrl", mapOf("relationshipId" to relationshipId, "memoryId" to memoryId)))
    urlCache[memoryId] = CachedUrl(fresh.url, fresh.expiresAtMillis - URL_REFRESH_MARGIN_MS)
    return fresh.url
  }

  private suspend fun confirmWithRetry(relationshipId: String, memoryId: String, publicId: String): String {
    val payload = mapOf("relationshipId" to relationshipId, "memoryId" to memoryId)
    // Xác nhận có thể gặp lỗi tạm thời hoặc chưa thấy tệp ngay, nên thử lại vài lần trước khi bỏ cuộc.
    for (attempt in 1..CONFIRM_ATTEMPTS) {
      try {
        return parseConfirmed(functions.call("confirmMemoryUpload", payload), publicId)
      } catch (e: CloudFunctionsException) {
        val retryable = e.code == CloudFunctionsCode.NOT_FOUND || e.code == CloudFunctionsCode.UNAVAILABLE
        if (!retryable || attempt == CONFIRM_ATTEMPTS) throw e
        delay(CONFIRM_BACKOFF_MS * attempt)
      }
    }
    error("unreachable: vòng lặp luôn return hoặc throw")
  }

  private class CachedUrl(val url: String, val refreshAtMillis: Long)

  private companion object {
    const val CONFIRM_ATTEMPTS = 5
    const val CONFIRM_BACKOFF_MS = 1_500L
    const val URL_REFRESH_MARGIN_MS = 60_000L
  }
}

internal data class SignedUpload(val uploadUrl: String, val headers: Map<String, String>)

internal data class Delivery(val url: String, val expiresAtMillis: Long)

internal fun asStringMap(data: Any?): Map<String, Any?>? =
  (data as? Map<*, *>)?.entries?.associate { (key, value) -> key.toString() to value }

/** Chỉ chấp nhận URL tải lên https cổng 443 trên *.r2.cloudflarestorage.com; phản hồi khác bị coi là lỗi. */
internal fun parseSignedUpload(data: Map<String, Any?>, expectedPublicId: String): SignedUpload {
  val uploadUrl = r2UrlOrNull(data["uploadUrl"] as? String)
  val headers = data["headers"] as? Map<*, *>
  if (data["publicId"] != expectedPublicId || headers == null || uploadUrl == null) throw IOException(BAD_RESPONSE)
  return SignedUpload(
    uploadUrl = uploadUrl,
    headers = headers.entries.associate { (key, value) -> key.toString() to value.toString() },
  )
}

internal fun parseConfirmed(data: Map<String, Any?>, expectedPublicId: String): String {
  if (data["publicId"] != expectedPublicId) throw IOException(BAD_RESPONSE)
  return expectedPublicId
}

/** URL phát/hiển thị phải là https cổng 443 trên *.r2.cloudflarestorage.com, và có thời hạn từ máy chủ. */
internal fun parseDelivery(data: Map<String, Any?>): Delivery {
  val url = r2UrlOrNull(data["url"] as? String)
  val expiresAtMillis = (data["expiresAtMillis"] as? Number)?.toLong()
  if (url == null || expiresAtMillis == null) throw IOException(BAD_RESPONSE)
  return Delivery(url, expiresAtMillis)
}

// Trả lại đúng chuỗi gốc (chữ ký phủ lên cả URL); chỉ nhận https cổng 443 trên *.r2.cloudflarestorage.com.
// ponytail: không ghim account id trên máy; URL do máy chủ ký nên đủ, ghim thêm nếu có nhu cầu chống máy chủ giả.
private fun r2UrlOrNull(raw: String?): String? = raw?.toHttpUrlOrNull()
  ?.takeIf { it.isHttps && it.port == 443 && it.host.endsWith(".r2.cloudflarestorage.com") }?.let { raw }

private const val BAD_RESPONSE = "Phản hồi không hợp lệ từ máy chủ"
