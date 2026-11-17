package com.example.data.media

import com.example.domain.media.MemoryMediaFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

private const val BAD_UPLOAD = "Tải lên thất bại (phiên tải lên không hợp lệ)"

/** Tải tệp bằng HTTP PUT lên URL ký số bằng OkHttp (timeout 120 s cho video lớn). */
class OkHttpMediaUploader : MediaUploader {

  private val http = buildUploadClient()

  override suspend fun upload(uploadUrl: String, headers: Map<String, String>, media: MemoryMediaFile) =
    withContext(Dispatchers.IO) {
      val request = buildPutRequest(uploadUrl, headers, File(media.path))
      val conditional = headers.keys.any { it.equals("if-none-match", ignoreCase = true) }
      http.newCall(request).execute().use { response ->
        if (!isUploadAccepted(response.code, conditional)) throw IOException("Tải lên thất bại (HTTP ${response.code})")
      }
    }
}

// Không theo redirect: URL ký số chỉ hợp lệ cho đúng host/đường dẫn đã ký; 3xx bị coi là lỗi.
internal fun buildUploadClient(): OkHttpClient = OkHttpClient.Builder()
  .followRedirects(false)
  .followSslRedirects(false)
  .writeTimeout(120, TimeUnit.SECONDS)
  .readTimeout(120, TimeUnit.SECONDS)
  .build()

internal fun buildPutRequest(uploadUrl: String, headers: Map<String, String>, file: File): Request {
  val contentType = headers["content-type"] ?: throw IOException(BAD_UPLOAD)
  if (headers["content-length"] != file.length().toString()) throw IOException(BAD_UPLOAD)
  val builder = Request.Builder().url(uploadUrl).put(file.asRequestBody(contentType.toMediaType()))
  headers.filterKeys { it != "content-type" && it != "content-length" }.forEach { (k, v) -> builder.header(k, v) }
  return builder.build()
}

// 2xx là xong; 412 chỉ coi là thành công khi yêu cầu có If-None-Match (tệp đã nằm trên kho từ lần gửi trước).
internal fun isUploadAccepted(code: Int, conditional: Boolean): Boolean =
  code in 200..299 || (conditional && code == 412)
