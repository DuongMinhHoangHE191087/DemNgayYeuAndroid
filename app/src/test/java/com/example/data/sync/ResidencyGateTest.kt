package com.example.data.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.data.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

/** Chế độ LOCAL_ONLY (mặc định production): không listener nội dung, không đẩy outbox, huỷ việc cũ. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ResidencyGateTest {

  private lateinit var ctx: Context

  @Before
  fun setUp() {
    ctx = ApplicationProvider.getApplicationContext()
    // InLoveApplication.onCreate thoát sớm dưới Robolectric nên phải tự khởi tạo WorkManager.
    WorkManagerTestInitHelper.initializeTestWorkManager(
      ctx, Configuration.Builder().setExecutor(SynchronousExecutor()).build()
    )
  }

  @Test
  fun coordinator_contentSyncDefaultsToFalse() {
    val db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
    val coordinator = SyncCoordinator(db.inLoveDao(), null, CoroutineScope(SupervisorJob()))
    assertEquals(false, coordinator.contentSync)
    db.close()
  }

  @Test
  fun enqueueImmediate_isNoOp_whenDormant() {
    SyncWorker.enqueueImmediate(ctx)
    val infos = WorkManager.getInstance(ctx).getWorkInfosForUniqueWork("sync_outbox_immediate").get()
    assertTrue("no immediate work may be scheduled while local-only", infos.isEmpty())
  }

  @Test
  fun enqueuePeriodic_cancelsExistingPeriodicWork_whenDormant() {
    val wm = WorkManager.getInstance(ctx)
    // Có ràng buộc mạng để việc giả lập này nằm yên ở ENQUEUED, không bị chạy ngay với factory mặc định.
    val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
      .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
      .build()
    wm.enqueueUniquePeriodicWork("sync_outbox_periodic", ExistingPeriodicWorkPolicy.KEEP, request).result.get()

    SyncWorker.enqueuePeriodic(ctx)

    val infos = wm.getWorkInfosForUniqueWork("sync_outbox_periodic").get()
    assertTrue(infos.isNotEmpty() && infos.all { it.state == WorkInfo.State.CANCELLED })
  }
}
