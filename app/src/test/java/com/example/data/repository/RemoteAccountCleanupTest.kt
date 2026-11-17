package com.example.data.repository

import com.example.data.cloud.CloudFunctionsClient
import com.example.data.cloud.CloudFunctionsCode
import com.example.data.cloud.CloudFunctionsException
import com.example.data.cloud.fakes.FakeCloudFunctionsClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** FirebaseRemoteAccountCleanup.deleteCloudData: mọi lỗi callable thành kết quả/thông báo tiếng Việt, huỷ coroutine thì ném lại. */
class RemoteAccountCleanupTest {

  private val uid = "uid_1"
  private val failedPrefix = "Chưa gửi được yêu cầu xoá"

  private fun cleanup(client: CloudFunctionsClient, current: String? = uid) =
    FirebaseRemoteAccountCleanup(client, currentUid = { current })

  private fun throwing(t: Throwable) = object : CloudFunctionsClient {
    override suspend fun call(name: String, data: Map<String, Any?>?): Map<String, Any?> = throw t
  }

  // Bắt bên trong runBlocking: CancellationException thoát ra ngoài sẽ bị runBlocking nuốt như một lần huỷ.
  private fun thrownBy(client: CloudFunctionsClient): Throwable = runBlocking {
    try {
      cleanup(client).deleteCloudData(uid, "AAAA-1111")
      throw AssertionError("expected deleteCloudData to throw")
    } catch (e: Throwable) {
      e
    }
  }

  @Test
  fun matchingUid_callsRequestAccountDeletionOnceWithNoData() {
    val fake = FakeCloudFunctionsClient().apply { enqueue(emptyMap()) }

    runBlocking { cleanup(fake).deleteCloudData(uid, "AAAA-1111") }

    assertEquals(1, fake.calls.size)
    assertEquals("requestAccountDeletion", fake.calls.single().name)
    assertNull(fake.calls.single().data)
  }

  @Test
  fun uidMismatch_throwsIllegalStateAndCallsNothing() {
    val fake = FakeCloudFunctionsClient()

    try {
      runBlocking { cleanup(fake, current = "other").deleteCloudData(uid, "AAAA-1111") }
      fail("expected IllegalStateException")
    } catch (_: IllegalStateException) {
      // expected
    }

    assertTrue(fake.calls.isEmpty())
  }

  @Test
  fun recentLoginRequired_mapsToRecentLoginRequiredException() {
    val fake = FakeCloudFunctionsClient().apply {
      enqueueError(CloudFunctionsException(CloudFunctionsCode.FAILED_PRECONDITION, "recent-login-required"))
    }

    assertTrue(thrownBy(fake) is RecentLoginRequiredException)
  }

  @Test
  fun otherCloudErrors_mapToVietnameseIllegalStateWithCause() {
    listOf(CloudFunctionsCode.UNAVAILABLE, CloudFunctionsCode.DEADLINE_EXCEEDED).forEach { code ->
      val original = CloudFunctionsException(code, null)
      val fake = FakeCloudFunctionsClient().apply { enqueueError(original) }

      val e = thrownBy(fake)

      assertTrue("$code gave $e", e is IllegalStateException)
      assertTrue("$code gave: ${e.message}", e.message.orEmpty().startsWith(failedPrefix))
      assertSame(original, e.cause)
    }
  }

  @Test
  fun failedPreconditionWithOtherReason_isNotRecentLogin() {
    val fake = FakeCloudFunctionsClient().apply {
      enqueueError(CloudFunctionsException(CloudFunctionsCode.FAILED_PRECONDITION, "something-else"))
    }

    val e = thrownBy(fake)

    assertTrue("gave $e", e is IllegalStateException)
    assertTrue(e.message.orEmpty().startsWith(failedPrefix))
  }

  @Test
  fun ioException_mapsToIllegalStateWithCause() {
    val io = java.io.IOException("net")

    val e = thrownBy(throwing(io))

    assertTrue("gave $e", e is IllegalStateException)
    assertTrue(e.message.orEmpty().startsWith(failedPrefix))
    assertSame(io, e.cause)
  }

  @Test
  fun cancellation_propagatesUnchanged() {
    val e = thrownBy(throwing(kotlinx.coroutines.CancellationException("x")))

    assertTrue("gave $e", e is kotlinx.coroutines.CancellationException)
    // CancellationException tự nó là IllegalStateException; phải là nguyên bản, không bị bọc bằng thông báo "Chưa gửi được...".
    assertEquals("x", e.message)
  }
}
