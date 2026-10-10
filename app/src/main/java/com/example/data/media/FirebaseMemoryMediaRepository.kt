package com.example.data.media

import com.example.domain.media.MEMORY_PUBLIC_ID_PREFIX
import com.example.domain.media.MemoryMediaFile
import com.example.domain.media.MemoryMediaRepository
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.IOException
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Ảnh/video kỷ niệm: máy chủ cấp chỗ và xác nhận, file đi thẳng lên Cloudinary bằng chữ ký một lần.
 * Không có API key hay secret trên máy; chỉ có URL ký số ngắn hạn do máy chủ cấp.
 */
class FirebaseMemoryMediaRepository : MemoryMediaRepository {

  private val functions by lazy { FirebaseFunctions.getInstance(REGION) }
  private val http = OkHttpClient.Builder()
    .writeTimeout(120, TimeUnit.SECONDS)
    .readTimeout(120, TimeUnit.SECONDS)
    .build()
  // ponytail: cache không giới hạn, mỗi kỷ niệm đã xem một mục nhỏ; thêm LRU nếu số lượng lớn.
  private val urlCache = ConcurrentHashMap<String, CachedUrl>()

  override suspend fun upload(relationshipId: String, memoryId: String, media: MemoryMediaFile): String {
    val publicId = MEMORY_PUBLIC_ID_PREFIX + memoryId
    val signed = parseSignedUpload(
      call(
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
    postToCloudinary(signed, media)
    return confirmWithRetry(relationshipId, memoryId, publicId)
  }

  override suspend fun deliveryUrl(relationshipId: String, memoryId: String): String {
    val now = System.currentTimeMillis()
    urlCache[memoryId]?.takeIf { it.refreshAtMillis > now }?.let { return it.url }
    val fresh = parseDelivery(call("getMemoryMediaUrl", mapOf("relationshipId" to relationshipId, "memoryId" to memoryId)))
    urlCache[memoryId] = CachedUrl(fresh.url, fresh.expiresAtMillis - URL_REFRESH_MARGIN_MS)
    return fresh.url
  }

  private suspend fun call(name: String, payload: Map<String, Any?>): Map<String, Any?> {
    val data = functions.getHttpsCallable(name).call(payload).await().data
    return asStringMap(data) ?: throw IOException(BAD_RESPONSE)
  }

  private suspend fun postToCloudinary(signed: SignedUpload, media: MemoryMediaFile) = withContext(Dispatchers.IO) {
    val body = MultipartBody.Builder().setType(MultipartBody.FORM).apply {
      signed.fields.forEach { (name, value) -> addFormDataPart(name, value) }
      addFormDataPart("file", media.file.name, media.file.asRequestBody(media.mimeType.lowercase(Locale.ROOT).toMediaType()))
    }.build()
    http.newCall(Request.Builder().url(signed.uploadUrl).post(body).build()).execute().use { response ->
      if (!response.isSuccessful) throw IOException("Tải lên thất bại (HTTP ${response.code})")
    }
  }

  private suspend fun confirmWithRetry(relationshipId: String, memoryId: String, publicId: String): String {
    val payload = mapOf("relationshipId" to relationshipId, "memoryId" to memoryId)
    // Cloudinary có thể chưa hiện tệp ngay sau khi tải lên, nên thử lại vài lần trước khi bỏ cuộc.
    for (attempt in 1..CONFIRM_ATTEMPTS) {
      try {
        return parseConfirmed(call("confirmMemoryUpload", payload), publicId)
      } catch (e: FirebaseFunctionsException) {
        val retryable = e.code == FirebaseFunctionsException.Code.NOT_FOUND ||
          e.code == FirebaseFunctionsException.Code.UNAVAILABLE
        if (!retryable || attempt == CONFIRM_ATTEMPTS) throw e
        delay(CONFIRM_BACKOFF_MS * attempt)
      }
    }
    error("unreachable: vòng lặp luôn return hoặc throw")
  }

  private class CachedUrl(val url: String, val refreshAtMillis: Long)

  private companion object {
    const val REGION = "asia-southeast1"
    const val CONFIRM_ATTEMPTS = 5
    const val CONFIRM_BACKOFF_MS = 1_500L
    const val URL_REFRESH_MARGIN_MS = 60_000L
  }
}

internal data class SignedUpload(val uploadUrl: String, val fields: Map<String, String>)

internal data class Delivery(val url: String, val expiresAtMillis: Long)

internal fun asStringMap(data: Any?): Map<String, Any?>? =
  (data as? Map<*, *>)?.entries?.associate { (key, value) -> key.toString() to value }

/** Chỉ chấp nhận URL tải lên trỏ về api.cloudinary.com qua https; phản hồi khác bị coi là lỗi. */
internal fun parseSignedUpload(data: Map<String, Any?>, expectedPublicId: String): SignedUpload {
  val uploadUrl = data["uploadUrl"] as? String
  val fields = data["fields"] as? Map<*, *>
  if (data["publicId"] != expectedPublicId || fields == null) throw IOException(BAD_RESPONSE)
  if (httpsHostOf(uploadUrl) != "api.cloudinary.com") throw IOException(BAD_RESPONSE)
  return SignedUpload(
    uploadUrl = uploadUrl!!,
    fields = fields.entries.associate { (key, value) -> key.toString() to value.toString() },
  )
}

internal fun parseConfirmed(data: Map<String, Any?>, expectedPublicId: String): String {
  if (data["publicId"] != expectedPublicId) throw IOException(BAD_RESPONSE)
  return expectedPublicId
}

/** URL phát/hiển thị phải trỏ về cloudinary.com qua https, và có thời hạn từ máy chủ. */
internal fun parseDelivery(data: Map<String, Any?>): Delivery {
  val url = data["url"] as? String
  val expiresAtMillis = (data["expiresAtMillis"] as? Number)?.toLong()
  val host = httpsHostOf(url)
  if (url == null || expiresAtMillis == null || host == null) throw IOException(BAD_RESPONSE)
  if (host != "cloudinary.com" && !host.endsWith(".cloudinary.com")) throw IOException(BAD_RESPONSE)
  return Delivery(url, expiresAtMillis)
}

private fun httpsHostOf(raw: String?): String? = raw?.toHttpUrlOrNull()?.takeIf { it.isHttps }?.host

private const val BAD_RESPONSE = "Phản hồi không hợp lệ từ máy chủ"
