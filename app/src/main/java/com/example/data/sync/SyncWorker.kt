package com.example.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.BackoffPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkRequest
import com.example.data.db.InLoveDao
import com.example.data.model.AnniversaryDateEntity
import com.example.data.model.SharedMemoryEntity
import com.example.data.model.SyncOutboxEntity
import com.google.firebase.firestore.FirebaseFirestore
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Drains sync_outbox: for each pending entry, decodes the entity snapshot (payloadJson),
 * maps it to a Firestore document via the matching EntitySyncAdapter, and pushes it. One
 * generic worker for every syncable entity type (dispatched by SyncOutboxEntity.entityType)
 * instead of a worker per feature.
 */
class SyncWorker(
  context: Context,
  params: WorkerParameters,
  private val dao: InLoveDao,
  private val push: suspend (path: String, data: Map<String, Any?>) -> Unit = { path, data ->
    FirebaseFirestore.getInstance().document(path).set(data).await()
  }
) : CoroutineWorker(context, params) {

  override suspend fun doWork(): Result = drainLock.withLock { drain() }

  private suspend fun drain(): Result = withContext(Dispatchers.IO) {
    // Session fence: outbox rows belong to whichever account scope is live. If the scope switches
    // while this batch is in flight, stop without acknowledging anything by numeric id.
    val epoch = com.example.data.db.AccountDataVault.epoch.get()
    // Whole queue in one run, healthy rows first (attemptCount ASC). A failed row keeps its count and sinks behind them.
    // ponytail: the queue snapshot is held in memory for one run; a very large outbox needs keyset paging by id.
    val pending = dao.getPendingOutboxEntries(limit = Int.MAX_VALUE)
    if (pending.isEmpty()) return@withContext Result.success()

    var anyFailure = false
    for (entry in pending) {
      if (epoch != com.example.data.db.AccountDataVault.epoch.get()) return@withContext Result.retry()
      try {
        pushOne(entry)
        if (epoch != com.example.data.db.AccountDataVault.epoch.get()) return@withContext Result.retry()
        dao.deleteOutboxEntry(entry.id)
      } catch (e: Exception) {
        anyFailure = true
        if (epoch == com.example.data.db.AccountDataVault.epoch.get()) {
          dao.markOutboxAttemptFailed(entry.id, e.localizedMessage ?: e.toString())
        }
      }
    }
    if (anyFailure) Result.retry() else Result.success()
  }

  // ponytail: dòng không đẩy được (payload rỗng, thiếu relationshipId, loại lạ) bị ném lỗi và giữ lại trong outbox.
  // attemptCount tăng nên nó xếp sau dòng mới; không tự xoá để khỏi mất dữ liệu. Ngưỡng: outbox phình to thì thêm dọn theo số lần thử.
  private suspend fun pushOne(entry: SyncOutboxEntity) {
    when (entry.entityType) {
      MemorySyncAdapter.entityType -> {
        val entity = moshiAdapterFor<SharedMemoryEntity>().fromJson(entry.payloadJson)
          ?: error("outbox ${entry.id}: memory payload is null")
        val relationshipId = entity.relationshipId
          ?: error("outbox ${entry.id}: memory has no relationshipId")
        val path = "${MemorySyncAdapter.collectionPath(relationshipId)}/${entity.syncId}"
        push(path, MemorySyncAdapter.toFirestoreMap(entity))
      }
      AnniversarySyncAdapter.entityType -> {
        val entity = moshiAdapterFor<AnniversaryDateEntity>().fromJson(entry.payloadJson)
          ?: error("outbox ${entry.id}: anniversary payload is null")
        val relationshipId = entity.relationshipId
          ?: error("outbox ${entry.id}: anniversary has no relationshipId")
        val path = "${AnniversarySyncAdapter.collectionPath(relationshipId)}/${entity.syncId}"
        push(path, AnniversarySyncAdapter.toFirestoreMap(entity))
      }
      else -> error("outbox ${entry.id}: unknown entityType '${entry.entityType}'")
    }
  }

  companion object {
    // internal, not private: an inline function's body is copied into every call site, so a
    // public/internal inline fun cannot reference a private member — this is what the compiler
    // flagged ("Public-API inline function cannot access non-public-API property") once this
    // file actually got compiled. moshiAdapterFor is called from InLoveRepository and tests in
    // this same module, so internal (not a full public leak of the Moshi instance) is correct.
    internal val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    internal inline fun <reified T> moshiAdapterFor(): JsonAdapter<T> = moshi.adapter(T::class.java)

    // ponytail: một lock cho cả tiến trình. Hai lần chạy cùng đọc một outbox có thể đẩy bản UPSERT cũ sau tombstone; nhiều tiến trình thì cần đánh dấu dòng đang xử lý trong DB.
    private val drainLock = Mutex()

    private const val UNIQUE_PERIODIC_NAME = "sync_outbox_periodic"
    private const val UNIQUE_IMMEDIATE_NAME = "sync_outbox_immediate"

    fun enqueuePeriodic(context: Context) {
      val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
      val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
        .setConstraints(constraints)
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
        .build()
      WorkManager.getInstance(context)
        .enqueueUniquePeriodicWork(UNIQUE_PERIODIC_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun enqueueImmediate(context: Context) {
      val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
      val request = OneTimeWorkRequestBuilder<SyncWorker>()
        .setConstraints(constraints)
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
        .build()
      WorkManager.getInstance(context)
        .enqueueUniqueWork(UNIQUE_IMMEDIATE_NAME, ExistingWorkPolicy.REPLACE, request)
    }
  }
}

class SyncWorkerFactory(
  private val dao: InLoveDao,
  private val fakeFirestorePush: (suspend (String, Map<String, Any?>) -> Unit)? = null
) : androidx.work.WorkerFactory() {
  override fun createWorker(
    appContext: Context,
    workerClassName: String,
    workerParameters: WorkerParameters
  ): androidx.work.ListenableWorker? {
    return if (workerClassName == SyncWorker::class.java.name) {
      if (fakeFirestorePush != null) SyncWorker(appContext, workerParameters, dao, fakeFirestorePush)
      else SyncWorker(appContext, workerParameters, dao)
    } else null
  }
}
