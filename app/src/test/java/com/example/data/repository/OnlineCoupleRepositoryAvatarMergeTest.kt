package com.example.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.model.OnlineStatus
import com.example.data.model.OnlineUserEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** T21/B-1: avatar do AvatarPublisher ghi thẳng vào DAO phải vào được cache _currentUser, và writer ghi cả hàng không được ghi đè lại giá trị `file:` cũ. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OnlineCoupleRepositoryAvatarMergeTest {

  private val localUrl = "file:/data/avatars/user_avatar_1.jpg"
  private val remoteUrl = "https://cdn.example.com/inlove_avt_uid_a?v=1"

  private fun me() = OnlineUserEntity(
    uid = "uid_a", displayName = "An", coupleCode = "AAAA-1111", isCurrentUser = true,
    isProfileSetup = true, status = OnlineStatus.SINGLE, avatarUrl = localUrl
  )

  @Test
  fun updateMyProfile_staleMissingFileAvatar_keepsTheHttpsAvatarRoomHolds() {
    // T15/B-F2: hộp thoại sửa hồ sơ mở với avatar file: cũ, publisher đã đổi sang https và xoá file trước khi bấm Lưu.
    withRepo { repo, db, _ ->
      db.inLoveDao().replaceOnlineUserAvatar("uid_a", localUrl, remoteUrl)

      val (ok, _) = repo.updateMyProfile("An Moi", "", localUrl, "FEMALE", "")

      assertEquals(true, ok)
      val row = db.inLoveDao().getOnlineUserByUidSync("uid_a")!!
      assertEquals("An Moi", row.displayName)
      assertEquals(remoteUrl, row.avatarUrl)
    }
  }

  @Test
  fun updateMyProfile_missingFileAvatar_isStoredAsSubmittedWhenTheLiveAvatarIsNotRemote() {
    withRepo { repo, db, _ ->
      val other = "file:/data/avatars/other_gone.jpg"

      repo.updateMyProfile("An Moi", "", other, "FEMALE", "")

      assertEquals(other, db.inLoveDao().getOnlineUserByUidSync("uid_a")!!.avatarUrl)
    }
  }

  private fun withRepo(block: suspend (OnlineCoupleRepository, AppDatabase, CoroutineScope) -> Unit) = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())
    val repo = OnlineCoupleRepository(db.inLoveDao(), context, scope, useFirestore = false, functions = null)
    db.inLoveDao().insertOnlineUser(me())
    db.inLoveDao().insertOnlineUser(OnlineUserEntity(uid = "uid_t", displayName = "Tú", coupleCode = "TTTT-1111", avatarUrl = "file:/data/avatars/t.jpg"))
    repo.setCurrentUserId("uid_a")
    try {
      block(repo, db, scope)
    } finally {
      scope.coroutineContext.job.cancelAndJoin()
      db.close()
    }
  }

  // Room báo thay đổi trên luồng nền nên phải chờ thật, không assert ngay.
  private suspend fun OnlineCoupleRepository.awaitAvatar(expected: String) {
    withTimeout(5_000) { currentUser.first { it.avatarUrl == expected } }
  }

  @Test
  fun daoOnlyAvatarSwap_showsUpInCurrentUser() {
    withRepo { repo, db, _ ->
      assertEquals(localUrl, repo.currentUser.value.avatarUrl)

      db.inLoveDao().replaceOnlineUserAvatar("uid_a", localUrl, remoteUrl)

      repo.awaitAvatar(remoteUrl)
    }
  }

  @Test
  fun daoOnlyAvatarChangeOnAnotherUid_doesNotTouchCurrentUser() {
    withRepo { repo, db, _ ->
      db.inLoveDao().replaceOnlineUserAvatar("uid_t", "file:/data/avatars/t.jpg", remoteUrl)
      delay(500) // ponytail: không có mốc quan sát được cho "không có gì xảy ra", chờ ngắn

      assertEquals(localUrl, repo.currentUser.value.avatarUrl)
      assertEquals("uid_a", repo.currentUser.value.uid)
    }
  }

  @Test
  fun wholeRowWriterAfterSwap_doesNotWriteTheOldFileUrlBack() {
    withRepo { repo, db, _ ->
      db.inLoveDao().replaceOnlineUserAvatar("uid_a", localUrl, remoteUrl)
      repo.awaitAvatar(remoteUrl)

      val (ok, _) = repo.sendSetLoveInvite("TTTT-1111")

      assertEquals(true, ok)
      val row = db.inLoveDao().getOnlineUserByUidSync("uid_a")!!
      assertEquals(OnlineStatus.PENDING_INVITE, row.status) // writer đã chạy
      assertEquals(remoteUrl, row.avatarUrl)
    }
  }

  @Test
  fun staleCacheWriters_keepTheAvatarRoomHoldsNow() {
    // T15/B-F1: cache `_currentUser` còn giữ `file:` cũ (huỷ scope để gộp không chạy), Room đã là https.
    withRepo { repo, db, scope ->
      scope.coroutineContext.job.cancelAndJoin()
      db.inLoveDao().replaceOnlineUserAvatar("uid_a", localUrl, remoteUrl)
      assertEquals("cache phải đang cũ", localUrl, repo.currentUser.value.avatarUrl)

      val (ok, _) = repo.sendSetLoveInvite("TTTT-1111")

      assertEquals(true, ok)
      assertEquals(OnlineStatus.PENDING_INVITE, db.inLoveDao().getOnlineUserByUidSync("uid_a")!!.status)
      assertEquals(remoteUrl, db.inLoveDao().getOnlineUserByUidSync("uid_a")!!.avatarUrl)
      assertEquals(remoteUrl, repo.currentUser.value.avatarUrl)

      db.inLoveDao().replaceOnlineUserAvatar("uid_a", remoteUrl, "https://cdn.example.com/inlove_avt_uid_a?v=2")
      repo.toggleInterest("cooking")
      assertEquals("coffee,travel,technology,music,cooking", db.inLoveDao().getOnlineUserByUidSync("uid_a")!!.interestsCsv)
      assertEquals("https://cdn.example.com/inlove_avt_uid_a?v=2", db.inLoveDao().getOnlineUserByUidSync("uid_a")!!.avatarUrl)
    }
  }

  @Test
  fun cancellingTheInjectedScope_stopsTheMerge() {
    withRepo { repo, db, scope ->
      db.inLoveDao().replaceOnlineUserAvatar("uid_a", localUrl, remoteUrl)
      repo.awaitAvatar(remoteUrl) // trước khi huỷ thì gộp hoạt động

      scope.coroutineContext.job.cancelAndJoin()
      db.inLoveDao().replaceOnlineUserAvatar("uid_a", remoteUrl, "https://cdn.example.com/other")
      delay(500)

      assertEquals(remoteUrl, repo.currentUser.value.avatarUrl)
    }
  }
}
