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

  override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
    val pending = dao.getPendingOutboxEntries(limit = 20)
    if (pending.isEmpty()) return@withContext Result.success()

    var anyFailure = false
    for (entry in pending) {
      try {
        pushOne(entry)
        dao.deleteOutboxEntry(entry.id)
      } catch (e: Exception) {
        anyFailure = true
        dao.markOutboxAttemptFailed(entry.id, e.localizedMessage ?: e.toString())
      }
    }
    if (anyFailure) Result.retry() else Result.success()
  }

  private suspend fun pushOne(entry: SyncOutboxEntity) {
    when (entry.entityType) {
      MemorySyncAdapter.entityType -> {
        val entity = moshiAdapterFor<SharedMemoryEntity>().fromJson(entry.payloadJson) ?: return
        val relationshipId = entity.relationshipId ?: return
        val path = "${MemorySyncAdapter.collectionPath(relationshipId)}/${entity.syncId}"
        push(path, MemorySyncAdapter.toFirestoreMap(entity))
      }
      AnniversarySyncAdapter.entityType -> {
        val entity = moshiAdapterFor<AnniversaryDateEntity>().fromJson(entry.payloadJson) ?: return
        val relationshipId = entity.relationshipId ?: return
        val path = "${AnniversarySyncAdapter.collectionPath(relationshipId)}/${entity.syncId}"
        push(path, AnniversarySyncAdapter.toFirestoreMap(entity))
      }
      else -> {
        // Unknown entity type (should not happen — every producer uses a known adapter's
        // entityType). Drop it rather than retry forever.
      }
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
