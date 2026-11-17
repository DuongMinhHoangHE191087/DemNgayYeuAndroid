package com.example.data.cloud

// Hợp đồng gọi Cloud Functions, cố ý không import com.google.firebase.* để iOS dùng chung được.

/** 17 mã lỗi chuẩn của callable, trùng tên với FirebaseFunctionsException.Code. */
enum class CloudFunctionsCode {
  OK,
  CANCELLED,
  UNKNOWN,
  INVALID_ARGUMENT,
  DEADLINE_EXCEEDED,
  NOT_FOUND,
  ALREADY_EXISTS,
  PERMISSION_DENIED,
  RESOURCE_EXHAUSTED,
  FAILED_PRECONDITION,
  ABORTED,
  OUT_OF_RANGE,
  UNIMPLEMENTED,
  INTERNAL,
  UNAVAILABLE,
  DATA_LOSS,
  UNAUTHENTICATED,
}

/** Lỗi từ callable; `reason` lấy từ details.reason của máy chủ (vd `recent-login-required`), có thể null. */
class CloudFunctionsException(
  val code: CloudFunctionsCode,
  val reason: String?,
  cause: Throwable? = null,
) : Exception("Cloud Functions lỗi $code" + (reason?.let { " ($it)" } ?: ""), cause)

interface CloudFunctionsClient {
  /** Gọi callable `name`; lỗi của callable (kể cả quá hạn) là [CloudFunctionsException], huỷ coroutine thì ném CancellationException. */
  suspend fun call(name: String, data: Map<String, Any?>? = null): Map<String, Any?>
}
