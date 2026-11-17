package com.example.data.cloud

import com.example.data.cloud.fakes.FakeCloudFunctionsClient
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.functions.FirebaseFunctionsException.Code
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Cần Robolectric vì Code.values() và FirebaseFunctionsException chạy khởi tạo tĩnh dùng android.util.SparseArray.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CloudFunctionsErrorMappingTest {

  // Hàm tạo của FirebaseFunctionsException là internal trong Kotlin (bytecode vẫn public), nên dựng bằng reflection.
  private fun firebaseException(message: String, code: Code, details: Any?): FirebaseFunctionsException =
    FirebaseFunctionsException::class.java
      .getDeclaredConstructor(String::class.java, Code::class.java, Any::class.java)
      .newInstance(message, code, details)

  @Test
  fun mapsAllSeventeenCodes() {
    val codes = Code.values().toList()
    assertEquals("FirebaseFunctionsException.Code phải có đúng 17 mã", 17, codes.size)
    assertEquals("CloudFunctionsCode phải có đúng 17 mã", 17, CloudFunctionsCode.values().size)

    // Mỗi mã ra đúng mã cùng tên, và không hai mã nào trùng đích (song ánh).
    val mapped = codes.map { mapFunctionsCode(it) }
    codes.zip(mapped).forEach { (src, dst) -> assertEquals("sai ánh xạ cho $src", src.name, dst.name) }
    assertEquals("ánh xạ phải là song ánh", 17, mapped.toSet().size)

    // Bảng tường minh để đổi chéo hai nhánh cũng đỏ.
    val table = mapOf(
      Code.OK to CloudFunctionsCode.OK,
      Code.CANCELLED to CloudFunctionsCode.CANCELLED,
      Code.UNKNOWN to CloudFunctionsCode.UNKNOWN,
      Code.INVALID_ARGUMENT to CloudFunctionsCode.INVALID_ARGUMENT,
      Code.DEADLINE_EXCEEDED to CloudFunctionsCode.DEADLINE_EXCEEDED,
      Code.NOT_FOUND to CloudFunctionsCode.NOT_FOUND,
      Code.ALREADY_EXISTS to CloudFunctionsCode.ALREADY_EXISTS,
      Code.PERMISSION_DENIED to CloudFunctionsCode.PERMISSION_DENIED,
      Code.RESOURCE_EXHAUSTED to CloudFunctionsCode.RESOURCE_EXHAUSTED,
      Code.FAILED_PRECONDITION to CloudFunctionsCode.FAILED_PRECONDITION,
      Code.ABORTED to CloudFunctionsCode.ABORTED,
      Code.OUT_OF_RANGE to CloudFunctionsCode.OUT_OF_RANGE,
      Code.UNIMPLEMENTED to CloudFunctionsCode.UNIMPLEMENTED,
      Code.INTERNAL to CloudFunctionsCode.INTERNAL,
      Code.UNAVAILABLE to CloudFunctionsCode.UNAVAILABLE,
      Code.DATA_LOSS to CloudFunctionsCode.DATA_LOSS,
      Code.UNAUTHENTICATED to CloudFunctionsCode.UNAUTHENTICATED,
    )
    table.forEach { (src, dst) -> assertEquals("sai ánh xạ cho $src", dst, mapFunctionsCode(src)) }
  }

  @Test
  fun readsDetailsReason() {
    assertEquals("recent-login-required", readFunctionsReason(mapOf("reason" to "recent-login-required")))
    assertNull("thiếu reason thì null", readFunctionsReason(mapOf("other" to "x")))
    assertNull("reason không phải chuỗi thì null", readFunctionsReason(mapOf("reason" to 42)))
  }

  @Test
  fun nonMapDetailsGivesNullReason() {
    assertNull(readFunctionsReason(null))
    assertNull(readFunctionsReason("recent-login-required"))
    assertNull(readFunctionsReason(7))
    assertNull(readFunctionsReason(listOf("reason", "recent-login-required")))
  }

  @Test
  fun timeoutBecomesDeadlineExceeded() = runBlocking {
    try {
      executeCallable(timeoutMs = 50) { delay(5_000); null }
      fail("phải ném CloudFunctionsException")
    } catch (e: CloudFunctionsException) {
      assertEquals(CloudFunctionsCode.DEADLINE_EXCEEDED, e.code)
      assertNull(e.reason)
    }
  }

  @Test
  fun parentCancellationIsNotWrapped() = runBlocking {
    val started = CompletableDeferred<Unit>()
    var thrown: Throwable? = null
    val job = launch {
      try {
        executeCallable { started.complete(Unit); delay(5_000); null }
      } catch (e: Throwable) {
        thrown = e
        throw e
      }
    }
    withTimeout(5_000) { started.await() } // nếu block không bao giờ chạy thì đỏ chứ không treo
    job.cancel()
    job.join()
    assertTrue("huỷ từ cha phải vẫn là CancellationException, không phải ${thrown}", thrown is CancellationException)
    assertTrue("huỷ từ cha không được bọc thành CloudFunctionsException", thrown !is CloudFunctionsException)
  }

  @Test
  fun callerTimeoutIsNotWrapped() = runBlocking {
    try {
      withTimeout(50) { executeCallable { delay(5_000); null } }
      fail("timeout của bên gọi phải thoát ra nguyên vẹn")
    } catch (e: TimeoutCancellationException) {
      // đúng: chỉ timeout của chính executeCallable mới đổi thành DEADLINE_EXCEEDED
    }
  }

  @Test
  fun firebaseExceptionBecomesCloudFunctionsExceptionWithCodeAndReason() = runBlocking {
    val original = firebaseException("x", Code.FAILED_PRECONDITION, mapOf("reason" to "recent-login-required"))
    try {
      executeCallable { throw original }
      fail("phải ném CloudFunctionsException")
    } catch (e: CloudFunctionsException) {
      assertEquals(CloudFunctionsCode.FAILED_PRECONDITION, e.code)
      assertEquals("recent-login-required", e.reason)
      assertSame("giữ lỗi gốc làm cause", original, e.cause)
    }
    val noDetails = firebaseException("y", Code.UNAVAILABLE, null).toCloudFunctionsException()
    assertEquals(CloudFunctionsCode.UNAVAILABLE, noDetails.code)
    assertNull(noDetails.reason)
  }

  @Test
  fun otherExceptionsPropagateUnchanged() = runBlocking {
    val boom = IllegalStateException("boom")
    try {
      executeCallable { throw boom }
      fail("phải ném lại lỗi gốc")
    } catch (e: IllegalStateException) {
      // kotlinx.coroutines có thể sao chép lỗi khi đi qua ranh giới coroutine (cùng kiểu và message, cause là bản gốc).
      assertEquals("boom", e.message)
      assertTrue("phải là lỗi gốc hoặc bản sao có cause là lỗi gốc", e === boom || e.cause === boom)
    }
  }

  @Test
  fun resultShapesAreConvertedWithoutLosingData() = runBlocking {
    assertEquals(emptyMap<String, Any?>(), executeCallable { null })
    assertEquals(mapOf("a" to 1, "7" to null), executeCallable { mapOf("a" to 1, 7 to null) })
    assertEquals(mapOf("data" to 5), executeCallable { 5 })
    assertEquals(mapOf("data" to "ok"), executeCallable { "ok" })
    assertEquals(mapOf("data" to listOf(1, 2)), executeCallable { listOf(1, 2) })
  }

  @Test
  fun fakeRecordsCallsAndReplaysQueueInOrder() = runBlocking {
    val fake = FakeCloudFunctionsClient()
    val err = CloudFunctionsException(CloudFunctionsCode.FAILED_PRECONDITION, "recent-login-required")
    fake.enqueue(mapOf("ok" to true))
    fake.enqueueError(err)

    assertEquals(mapOf("ok" to true), fake.call("first", mapOf("x" to 1)))
    try {
      fake.call("second")
      fail("phải ném lỗi đã xếp hàng")
    } catch (e: CloudFunctionsException) {
      assertSame(err, e)
    }
    assertEquals(
      listOf(FakeCloudFunctionsClient.Call("first", mapOf("x" to 1)), FakeCloudFunctionsClient.Call("second", null)),
      fake.calls,
    )
  }

  @Test
  fun fakeReplaysResultsInEnqueueOrder() = runBlocking {
    val fake = FakeCloudFunctionsClient()
    fake.enqueue(mapOf("n" to 1))
    fake.enqueue(mapOf("n" to 2))
    fake.enqueue(mapOf("n" to 3))
    assertEquals(listOf(1, 2, 3), (1..3).map { fake.call("c").getValue("n") })
  }

  @Test
  fun fakeEmptyQueueThrowsClearError() = runBlocking {
    try {
      FakeCloudFunctionsClient().call("nothing")
      fail("hàng đợi rỗng phải ném IllegalStateException")
    } catch (e: IllegalStateException) {
      assertTrue(e.message.orEmpty().contains("nothing"))
    }
  }
}
