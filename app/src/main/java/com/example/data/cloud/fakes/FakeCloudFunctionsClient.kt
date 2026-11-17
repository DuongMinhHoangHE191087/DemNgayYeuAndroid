package com.example.data.cloud.fakes

import com.example.data.cloud.CloudFunctionsClient
import com.example.data.cloud.CloudFunctionsException

/** Client giả cho test: ghi lại lời gọi, trả kết quả hoặc lỗi đã xếp hàng theo thứ tự. */
class FakeCloudFunctionsClient : CloudFunctionsClient {
  data class Call(val name: String, val data: Map<String, Any?>?)

  val calls = mutableListOf<Call>()
  private val queue = ArrayDeque<Result<Map<String, Any?>>>()

  fun enqueue(result: Map<String, Any?>) {
    queue.addLast(Result.success(result))
  }

  fun enqueueError(error: CloudFunctionsException) {
    queue.addLast(Result.failure(error))
  }

  override suspend fun call(name: String, data: Map<String, Any?>?): Map<String, Any?> {
    calls += Call(name, data)
    // Hàng đợi rỗng là lỗi test, không trả giá trị giả để khỏi che bug.
    val next = queue.removeFirstOrNull() ?: throw IllegalStateException("FakeCloudFunctionsClient: chưa xếp kết quả cho lời gọi '$name'")
    return next.getOrThrow()
  }
}
