package com.example.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.cloud.CloudFunctionsClient
import com.example.data.cloud.CloudFunctionsCode
import com.example.data.cloud.CloudFunctionsException
import com.example.data.cloud.fakes.FakeCloudFunctionsClient
import com.example.data.db.AppDatabase
import com.example.data.db.InLoveDao
import com.example.data.model.CoupleProfileEntity
import com.example.data.model.OnlineInviteEntity
import com.example.data.model.OnlineRelationshipEntity
import com.example.data.model.OnlineStatus
import com.example.data.model.OnlineUserEntity
import com.example.data.model.RelationshipStatus
import com.example.ui.util.DEFAULT_LOVE_TITLE
import com.example.ui.util.ProfileUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** acceptSetLoveInvite gọi callable acceptCoupleInvite qua CloudFunctionsClient; lỗi thì không ghi gì cục bộ. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OnlineCoupleAcceptInviteTest {

  private val inviteId = "inv_accept_1"
  private val rejectedMessage = "Không thể chấp nhận lời mời này"
  private val retryMessage = "Chưa kết nối được máy chủ"

  private fun withRepo(functions: CloudFunctionsClient?, block: suspend (OnlineCoupleRepository, AppDatabase) -> Unit) =
    withRepoOf({ dao, context, scope -> OnlineCoupleRepository(dao, context, scope, useFirestore = false, functions = functions) }, block = block)

  /** Dựng DB + repo (uid_b nhận lời mời của uid_a) bằng [create], chạy [block], rồi dọn. */
  private fun withRepoOf(
    create: (InLoveDao, android.content.Context, CoroutineScope) -> OnlineCoupleRepository,
    senderRow: Boolean = true,
    invite: OnlineInviteEntity = OnlineInviteEntity(
      inviteId = inviteId, senderUid = "uid_a", senderCoupleCode = "AAAA-1111",
      targetCoupleCode = "BBBB-2222", targetUid = "uid_b", status = "PENDING"
    ),
    seed: suspend (InLoveDao) -> Unit = {},
    block: suspend (OnlineCoupleRepository, AppDatabase) -> Unit
  ) = runBlocking {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())
    val repo = create(db.inLoveDao(), context, scope)
    if (senderRow) {
      db.inLoveDao().insertOnlineUser(
        OnlineUserEntity(uid = "uid_a", coupleCode = "AAAA-1111", isCurrentUser = false, status = OnlineStatus.PENDING_INVITE)
      )
    }
    db.inLoveDao().insertOnlineUser(OnlineUserEntity(uid = "uid_b", coupleCode = "BBBB-2222", isCurrentUser = true))
    repo.setCurrentUserId("uid_b")
    db.inLoveDao().insertOnlineInvite(invite)
    seed(db.inLoveDao())
    try {
      block(repo, db)
    } finally {
      scope.coroutineContext.job.cancelAndJoin()
      db.close()
    }
  }

  private suspend fun assertNothingWritten(db: AppDatabase) {
    assertNull("no relationship row may be written", db.inLoveDao().getActiveRelationshipForUser("uid_b"))
    assertNotEquals(OnlineStatus.COUPLED, db.inLoveDao().getOnlineUserByUidSync("uid_a")?.status)
    assertNotEquals(OnlineStatus.COUPLED, db.inLoveDao().getOnlineUserByUidSync("uid_b")?.status)
    assertNotNull("invite must stay so the user can retry", db.inLoveDao().getInviteByIdSync(inviteId))
  }

  @Test
  fun acceptInvite_callsAcceptCoupleInviteWithInviteIdAndStartDate() {
    val fake = FakeCloudFunctionsClient().apply { enqueue(emptyMap()) }
    withRepo(fake) { repo, _ ->
      val startMillis = 1_700_000_000_000L
      repo.acceptSetLoveInvite(inviteId, confirmedStartDateMillis = startMillis)

      assertEquals("expected exactly one callable call, got ${fake.calls.size}", 1, fake.calls.size)
      val call = fake.calls.single()
      assertEquals("acceptCoupleInvite", call.name)
      assertEquals(inviteId, call.data?.get("inviteId"))
      assertEquals(startMillis, call.data?.get("startDateMillis"))
      assertEquals(ProfileUtils.formatDate(startMillis), call.data?.get("startDateText"))
    }
  }

  @Test
  fun acceptInvite_success_relationshipIdEqualsInviteId() {
    val fake = FakeCloudFunctionsClient().apply { enqueue(emptyMap()) }
    withRepo(fake) { repo, db ->
      val (ok, _) = repo.acceptSetLoveInvite(inviteId, confirmedStartDateMillis = 1_700_000_000_000L)

      assertTrue(ok)
      val rel = db.inLoveDao().getActiveRelationshipForUser("uid_b")
      assertNotNull(rel)
      assertEquals(inviteId, rel!!.relationshipId)
      assertEquals(RelationshipStatus.ACTIVE, rel.status)
      assertFalse("server already confirmed, so no pending sync", rel.pendingSync)
      assertEquals("state flow must expose the new relationship", inviteId, repo.activeRelationship.value?.relationshipId)
      assertNull(db.inLoveDao().getInviteByIdSync(inviteId))
      assertEquals(OnlineStatus.COUPLED, db.inLoveDao().getOnlineUserByUidSync("uid_a")?.status)
      assertEquals(OnlineStatus.COUPLED, db.inLoveDao().getOnlineUserByUidSync("uid_b")?.status)
    }
  }

  @Test
  fun acceptInvite_failedPrecondition_returnsRejectedMessageAndWritesNothing() {
    listOf(
      CloudFunctionsCode.FAILED_PRECONDITION,
      CloudFunctionsCode.PERMISSION_DENIED,
      CloudFunctionsCode.NOT_FOUND
    ).forEach { code ->
      val fake = FakeCloudFunctionsClient().apply { enqueueError(CloudFunctionsException(code, null)) }
      withRepo(fake) { repo, db ->
        val (ok, message) = repo.acceptSetLoveInvite(inviteId)

        assertFalse("$code must fail", ok)
        assertTrue("$code gave: $message", message.startsWith(rejectedMessage))
        assertNothingWritten(db)
      }
    }
  }

  @Test
  fun acceptInvite_unavailable_returnsRetryMessageAndWritesNothing() {
    listOf(CloudFunctionsCode.UNAVAILABLE, CloudFunctionsCode.DEADLINE_EXCEEDED).forEach { code ->
      val fake = FakeCloudFunctionsClient().apply { enqueueError(CloudFunctionsException(code, null)) }
      withRepo(fake) { repo, db ->
        val (ok, message) = repo.acceptSetLoveInvite(inviteId)

        assertFalse("$code must fail", ok)
        assertTrue("$code gave: $message", message.startsWith(retryMessage))
        assertNothingWritten(db)
      }
    }
  }

  private fun throwing(t: Throwable) = object : CloudFunctionsClient {
    override suspend fun call(name: String, data: Map<String, Any?>?): Map<String, Any?> = throw t
  }

  @Test
  fun acceptInvite_unexpectedException_returnsRetryAndWritesNothing() {
    withRepo(throwing(java.io.IOException("net"))) { repo, db ->
      val (ok, message) = repo.acceptSetLoveInvite(inviteId)

      assertFalse(ok)
      assertTrue("gave: $message", message.startsWith(retryMessage))
      assertNothingWritten(db)
    }
  }

  @Test
  fun acceptInvite_cancellation_isRethrownAndWritesNothing() {
    withRepo(throwing(kotlinx.coroutines.CancellationException("cancelled"))) { repo, db ->
      try {
        val result = repo.acceptSetLoveInvite(inviteId)
        fail("cancellation must propagate, got $result")
      } catch (_: kotlinx.coroutines.CancellationException) {
        // expected
      }
      assertNothingWritten(db)
    }
  }

  @Test
  fun acceptInvite_noFunctions_localOnlyStillCouples() {
    withRepo(functions = null) { repo, db ->
      val (ok, _) = repo.acceptSetLoveInvite(inviteId, confirmedStartDateMillis = 1_700_000_000_000L)

      assertTrue(ok)
      val rel = db.inLoveDao().getActiveRelationshipForUser("uid_b")
      assertNotNull(rel)
      assertEquals(inviteId, rel!!.relationshipId)
      assertFalse("local-only mode skips the callable and writes the synced row, same as before", rel.pendingSync)
      assertEquals(OnlineStatus.COUPLED, db.inLoveDao().getOnlineUserByUidSync("uid_b")?.status)
    }
  }

  @Test
  fun acceptInvite_avatarPublishedDuringServerCall_isNotOverwrittenByTheStaleMe() {
    // T15/B-F1: `me` được giữ qua cuộc gọi máy chủ (tới 20 giây); AvatarPublisher đổi `file:` thành https đúng lúc đó.
    val local = "file:/data/avatars/me.jpg"
    val remote = "https://cdn.example.com/inlove_avt_uid_b?v=1"
    withRepoOf(
      create = { dao, context, scope ->
        val publishDuringCall = object : CloudFunctionsClient {
          override suspend fun call(name: String, data: Map<String, Any?>?): Map<String, Any?> {
            dao.replaceOnlineUserAvatar("uid_b", local, remote)
            return emptyMap()
          }
        }
        OnlineCoupleRepository(dao, context, scope, useFirestore = false, functions = publishDuringCall)
      },
      seed = { dao ->
        dao.replaceOnlineUserAvatar("uid_b", "", local)
        // cache của repo chưa kịp gộp `file:` ở đây, nên `me` bị giữ lại là bản cũ so với Room khi lời gọi trả về
      }
    ) { repo, db ->
      val (ok, _) = repo.acceptSetLoveInvite(inviteId, confirmedStartDateMillis = 1_700_000_000_000L)

      assertTrue(ok)
      assertEquals(OnlineStatus.COUPLED, db.inLoveDao().getOnlineUserByUidSync("uid_b")?.status)
      assertEquals(remote, db.inLoveDao().getOnlineUserByUidSync("uid_b")?.avatarUrl)
      assertEquals(remote, db.inLoveDao().getCoupleProfileSync()?.partner1ProfilePicture)
      assertEquals(remote, repo.currentUser.value.avatarUrl)
    }
  }

  @Test
  fun acceptInvite_defaultFunctions_localMode_isNullAndStillCouples() {
    // Không truyền functions + useFirestore=false: mặc định phải là null (không gọi máy chủ), vẫn ghép đôi cục bộ.
    withRepoOf({ dao, context, scope -> OnlineCoupleRepository(dao, context, scope, useFirestore = false) }) { repo, db ->
      val (ok, _) = repo.acceptSetLoveInvite(inviteId, confirmedStartDateMillis = 1_700_000_000_000L)

      assertTrue(ok)
      assertEquals(inviteId, db.inLoveDao().getActiveRelationshipForUser("uid_b")?.relationshipId)
    }
  }

  @Test
  fun acceptInvite_defaultFunctions_firestoreMode_usesRealClientSoNoFirebaseMeansRetryAndNothingWritten() {
    // useFirestore=true + không truyền functions: mặc định là client thật. Robolectric không có FirebaseApp nên gọi thất bại
    // và phải trả thông báo thử lại, KHÔNG ghi COUPLED cục bộ.
    withRepoOf({ dao, context, scope -> OnlineCoupleRepository(dao, context, scope, useFirestore = true) }) { repo, db ->
      val (ok, message) = repo.acceptSetLoveInvite(inviteId, confirmedStartDateMillis = 1_700_000_000_000L)

      assertFalse(ok)
      assertTrue("gave: $message", message.startsWith(retryMessage))
      assertNothingWritten(db)
    }
  }

  private val inviteFromMissingSender = OnlineInviteEntity(
    inviteId = inviteId, senderUid = "uid_a", senderName = "An Nhi", senderAvatar = "https://img.example/an.png",
    senderCoupleCode = "AAAA-1111", senderBirthDate = "01/02/1999", senderAge = 27, senderZodiac = "Bảo Bình",
    targetCoupleCode = "BBBB-2222", targetUid = "uid_b", status = "PENDING"
  )

  private fun repoWith(fake: FakeCloudFunctionsClient): (InLoveDao, android.content.Context, CoroutineScope) -> OnlineCoupleRepository =
    { dao, context, scope -> OnlineCoupleRepository(dao, context, scope, useFirestore = false, functions = fake) }

  @Test
  fun acceptInvite_senderRowMissing_buildsSenderFromInviteAndCouplesIt() {
    val fake = FakeCloudFunctionsClient().apply { enqueue(emptyMap()) }
    withRepoOf(repoWith(fake), senderRow = false, invite = inviteFromMissingSender) { repo, db ->
      val (ok, _) = repo.acceptSetLoveInvite(inviteId, confirmedStartDateMillis = 1_700_000_000_000L)

      assertTrue(ok)
      val sender = db.inLoveDao().getOnlineUserByUidSync("uid_a")
      assertNotNull("sender row must be created from the invite", sender)
      assertEquals(OnlineStatus.COUPLED, sender!!.status)
      assertEquals("uid_b", sender.partnerId)
      assertEquals(inviteId, sender.relationshipId)
      assertEquals("An Nhi", sender.displayName)
      assertEquals("https://img.example/an.png", sender.avatarUrl)
      assertEquals("01/02/1999", sender.birthDate)
      assertEquals("Bảo Bình", sender.zodiac)
      assertEquals("An Nhi", db.inLoveDao().getCoupleProfileSync()!!.partner2Name)
    }
  }

  @Test
  fun acceptInvite_senderRowMissing_serverUnavailable_failsAndCreatesNoSenderRow() {
    val fake = FakeCloudFunctionsClient().apply { enqueueError(CloudFunctionsException(CloudFunctionsCode.UNAVAILABLE, null)) }
    withRepoOf(repoWith(fake), senderRow = false, invite = inviteFromMissingSender) { repo, db ->
      val (ok, _) = repo.acceptSetLoveInvite(inviteId)

      assertFalse(ok)
      assertNull(db.inLoveDao().getOnlineUserByUidSync("uid_a"))
    }
  }

  private val keptTitle = "Chuyện của tụi mình"

  private fun profileWithTitle(title: String) = CoupleProfileEntity(
    partner1Name = "Be", partner1Birthday = "", partner1ProfilePicture = "",
    partner2Name = "Old", partner2Birthday = "", partner2ProfilePicture = "",
    loveTitle = title
  )

  private fun terminated(user1: String, user2: String) = OnlineRelationshipEntity(
    relationshipId = "rel_old", user1 = user1, user2 = user2, startDate = 1_600_000_000_000L,
    status = RelationshipStatus.TERMINATED
  )

  /** Chấp nhận lời mời với [profile] và [earlier] đã có sẵn trong Room; trả tiêu đề ghi vào couple_profile. */
  private fun titleAfterAccept(profile: CoupleProfileEntity?, earlier: OnlineRelationshipEntity?): String {
    var title = ""
    val fake = FakeCloudFunctionsClient().apply { enqueue(emptyMap()) }
    withRepoOf(
      repoWith(fake),
      seed = { dao ->
        if (profile != null) dao.insertCoupleProfile(profile)
        if (earlier != null) dao.insertOnlineRelationship(earlier)
      }
    ) { repo, db ->
      assertTrue(repo.acceptSetLoveInvite(inviteId, confirmedStartDateMillis = 1_700_000_000_000L).first)
      title = db.inLoveDao().getCoupleProfileSync()!!.loveTitle
    }
    return title
  }

  @Test
  fun acceptInvite_title_keptOnFirstPairing() {
    assertEquals(keptTitle, titleAfterAccept(profileWithTitle(keptTitle), earlier = null))
  }

  @Test
  fun acceptInvite_title_resetWhenEarlierRelationshipWasWithSomeoneElse() {
    assertEquals(DEFAULT_LOVE_TITLE, titleAfterAccept(profileWithTitle(keptTitle), terminated("uid_b", "uid_x")))
  }

  @Test
  fun acceptInvite_title_keptWhenEarlierRelationshipWasSamePair() {
    assertEquals(keptTitle, titleAfterAccept(profileWithTitle(keptTitle), terminated("uid_a", "uid_b")))
  }

  @Test
  fun acceptInvite_title_defaultWhenNoCoupleProfileYet() {
    assertEquals(DEFAULT_LOVE_TITLE, titleAfterAccept(profile = null, earlier = null))
  }
}
