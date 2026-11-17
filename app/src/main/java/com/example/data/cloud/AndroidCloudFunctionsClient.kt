package com.example.data.cloud

import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

class AndroidCloudFunctionsClient(private val region: String = CLOUD_FUNCTIONS_REGION) : CloudFunctionsClient {
  // Lười để dựng được trong test mà không cần Firebase.
  private val functions by lazy { FirebaseFunctions.getInstance(region) }

  override suspend fun call(name: String, data: Map<String, Any?>?): Map<String, Any?> = executeCallable {
    val callable = functions.getHttpsCallable(name)
    (if (data == null) callable.call() else callable.call(data)).await().data
  }
}

/** Timeout + đổi lỗi tách riêng để test với timeout ngắn mà không cần Firebase. */
internal suspend fun executeCallable(
  timeoutMs: Long = CALLABLE_TIMEOUT_MS,
  block: suspend () -> Any?,
): Map<String, Any?> = try {
  // withTimeoutOrNull chỉ trả null khi CHÍNH timeout này nổ; timeout hay huỷ của bên gọi vẫn thoát ra nguyên vẹn
  // (bắt TimeoutCancellationException sẽ nuốt nhầm timeout của bên gọi). Hộp Box vì data của callable có thể là null.
  (withTimeoutOrNull(timeoutMs) { Box(block()) } ?: throw CloudFunctionsException(CloudFunctionsCode.DEADLINE_EXCEEDED, null))
    .value.toResultMap()
} catch (e: FirebaseFunctionsException) {
  throw e.toCloudFunctionsException()
}

private class Box(val value: Any?)

// Kiểu khác map (số, chuỗi, danh sách) bọc vào "data" để không mất dữ liệu.
private fun Any?.toResultMap(): Map<String, Any?> = when (this) {
  null -> emptyMap()
  is Map<*, *> -> entries.associate { (k, v) -> k.toString() to v }
  else -> mapOf("data" to this)
}

fun FirebaseFunctionsException.toCloudFunctionsException(): CloudFunctionsException =
  CloudFunctionsException(mapFunctionsCode(code), readFunctionsReason(details), this)

// `when` đầy đủ, không else: thư viện thêm mã mới thì biên dịch báo thiếu nhánh.
internal fun mapFunctionsCode(code: FirebaseFunctionsException.Code): CloudFunctionsCode = when (code) {
  FirebaseFunctionsException.Code.OK -> CloudFunctionsCode.OK
  FirebaseFunctionsException.Code.CANCELLED -> CloudFunctionsCode.CANCELLED
  FirebaseFunctionsException.Code.UNKNOWN -> CloudFunctionsCode.UNKNOWN
  FirebaseFunctionsException.Code.INVALID_ARGUMENT -> CloudFunctionsCode.INVALID_ARGUMENT
  FirebaseFunctionsException.Code.DEADLINE_EXCEEDED -> CloudFunctionsCode.DEADLINE_EXCEEDED
  FirebaseFunctionsException.Code.NOT_FOUND -> CloudFunctionsCode.NOT_FOUND
  FirebaseFunctionsException.Code.ALREADY_EXISTS -> CloudFunctionsCode.ALREADY_EXISTS
  FirebaseFunctionsException.Code.PERMISSION_DENIED -> CloudFunctionsCode.PERMISSION_DENIED
  FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED -> CloudFunctionsCode.RESOURCE_EXHAUSTED
  FirebaseFunctionsException.Code.FAILED_PRECONDITION -> CloudFunctionsCode.FAILED_PRECONDITION
  FirebaseFunctionsException.Code.ABORTED -> CloudFunctionsCode.ABORTED
  FirebaseFunctionsException.Code.OUT_OF_RANGE -> CloudFunctionsCode.OUT_OF_RANGE
  FirebaseFunctionsException.Code.UNIMPLEMENTED -> CloudFunctionsCode.UNIMPLEMENTED
  FirebaseFunctionsException.Code.INTERNAL -> CloudFunctionsCode.INTERNAL
  FirebaseFunctionsException.Code.UNAVAILABLE -> CloudFunctionsCode.UNAVAILABLE
  FirebaseFunctionsException.Code.DATA_LOSS -> CloudFunctionsCode.DATA_LOSS
  FirebaseFunctionsException.Code.UNAUTHENTICATED -> CloudFunctionsCode.UNAUTHENTICATED
}

// Máy chủ trả details dạng map có "reason"; dạng khác thì coi như không có lý do.
internal fun readFunctionsReason(details: Any?): String? = (details as? Map<*, *>)?.get("reason") as? String
