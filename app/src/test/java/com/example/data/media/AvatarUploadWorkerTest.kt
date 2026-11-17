package com.example.data.media

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.BackoffPolicy
import androidx.work.Configuration
import androidx.work.ListenableWorker.Result
import androidx.work.NetworkType
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.data.db.AppDatabase
import com.example.data.model.OnlineUserEntity
import com.example.data.media.AvatarPublisher.Outcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AvatarUploadWorkerTest {

  private lateinit var context: Context
  private lateinit var db: AppDatabase
  private val dao get() = db.inLoveDao()

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
  }

  @After
  fun tearDown() {
    db.close()
  }

  private fun user(uid: String, avatar: String) = OnlineUserEntity(uid = uid, coupleCode = "C-$uid", avatarUrl = avatar)

  @Test
  fun resultFor_successOutcomesAreSuccessAtAnyAttempt() {
    for (outcome in listOf(Outcome.PUBLISHED, Outcome.NOTHING_TO_DO, Outcome.NOT_CONFIGURED)) {
      for (attempt in listOf(0, 20)) {
        assertEquals("$outcome@$attempt", Result.success(), AvatarUploadWorker.resultFor(outcome, attempt))
      }
    }
  }

  @Test
  fun resultFor_unauthenticatedAndLostRace_retryUntilMaxAttempts() {
    for (outcome in listOf(Outcome.UNAUTHENTICATED, Outcome.LOST_RACE)) {
      for (attempt in 0..7) assertEquals("$outcome@$attempt", Result.retry(), AvatarUploadWorker.resultFor(outcome, attempt))
      for (attempt in 8..9) assertEquals("$outcome@$attempt", Result.success(), AvatarUploadWorker.resultFor(outcome, attempt))
    }
  }

  @Test
  fun resultForFailure_givesUpAtMaxAttempts() {
    assertEquals(Result.retry(), AvatarUploadWorker.resultForFailure(AvatarUploadWorker.MAX_ATTEMPTS - 1))
    assertEquals(Result.success(), AvatarUploadWorker.resultForFailure(AvatarUploadWorker.MAX_ATTEMPTS))
  }

  @Test
  fun watchLocalAvatarKeys_listsOnlyFileAvatarsAndFollowsChanges() = runBlocking {
    dao.insertOnlineUser(user("a", "file:/x/a.jpg"))
    dao.insertOnlineUser(user("b", "https://cdn.example.com/b"))
    dao.insertOnlineUser(user("c", ""))

    assertEquals(listOf("a|file:/x/a.jpg"), dao.watchLocalAvatarKeys("file:").first())

    dao.insertOnlineUser(user("b", "file:/x/b.jpg"))
    dao.insertOnlineUser(user("a", "https://cdn.example.com/a"))

    assertEquals(listOf("b|file:/x/b.jpg"), dao.watchLocalAvatarKeys("file:").first())
  }

  @Test
  fun request_requiresNetworkAndBacksOffExponentially() {
    val spec = AvatarUploadWorker.request().workSpec

    assertEquals(NetworkType.CONNECTED, spec.constraints.requiredNetworkType)
    assertEquals(BackoffPolicy.EXPONENTIAL, spec.backoffPolicy)
  }

  @Test
  fun watch_withFlagOff_launchesNothing() {
    val scope = CoroutineScope(Job())

    AvatarUploadWorker.watch(context, dao, scope, enabled = false)

    assertTrue(scope.coroutineContext.job.children.none())
    scope.coroutineContext.job.cancel()
  }

  @Test
  fun watch_withFlagOn_enqueuesTheUniqueWorkOnlyOnceAFileAvatarExists() = runBlocking {
    // InLoveApplication.onCreate thoát sớm dưới Robolectric nên phải tự khởi tạo WorkManager.
    WorkManagerTestInitHelper.initializeTestWorkManager(
      context, Configuration.Builder().setExecutor(SynchronousExecutor()).build()
    )
    val wm = WorkManager.getInstance(context)
    val scope = CoroutineScope(Job() + Dispatchers.IO)
    try {
      dao.insertOnlineUser(user("b", "https://cdn.example.com/b"))
      AvatarUploadWorker.watch(context, dao, scope, enabled = true)
      delay(300) // ponytail: không có mốc quan sát được cho "chưa xếp gì", chờ ngắn

      assertTrue(wm.getWorkInfosForUniqueWork(AvatarUploadWorker.UNIQUE_NAME).get().isEmpty())

      dao.insertOnlineUser(user("a", "file:/x/a.jpg"))
      withTimeout(5_000) {
        while (wm.getWorkInfosForUniqueWork(AvatarUploadWorker.UNIQUE_NAME).get().isEmpty()) delay(25)
      }

      assertEquals(1, wm.getWorkInfosForUniqueWork(AvatarUploadWorker.UNIQUE_NAME).get().size)
    } finally {
      scope.coroutineContext.job.cancelAndJoin()
    }
  }
}
