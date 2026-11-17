package com.example.data.sync

import android.content.Context
import android.util.Log
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
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.IOException
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
  },
  private val cloudContent: () -> Boolean = { com.example.domain.DataResidencyPolicy.cloudContent }
) : CoroutineWorker(context, params) {

  // Local-only: an old scheduled run must not touch the network or the outbox.
  override suspend fun doWork(): Result =
    if (!cloudContent()) Result.success() else drainLock.withLock { drain() }

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
        // Parked: the relationship is over, so the server would deny the write forever. Keep the row (it comes back
        // into play only if the data is ever re-keyed) but don't count it as a failure or retry it.
        if (!pushOne(entry)) continue
        if (epoch != com.example.data.db.AccountDataVault.epoch.get()) return@withContext Result.retry()
        dao.deleteOutboxEntry(entry.id)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        anyFailure = true
        if (epoch == com.example.data.db.AccountDataVault.epoch.get()) {
          dao.markOutboxAttemptFailed(entry.id, e.localizedMessage ?: e.toString())
        }
      }
    }
    if (anyFailure) Result.retry() else Result.success()
  }

  // ponytail: dòng không bao giờ đẩy được (payload JSON hỏng hoặc rỗng, thiếu relationshipId, loại lạ, quan hệ đã kết thúc)
  // được "đỗ": trả false + Log.w, KHÔNG markOutboxAttemptFailed, không tính là lỗi nên không retry mãi. Dòng luôn được giữ lại
  // trong outbox, không bao giờ tự xoá, để khỏi mất dữ liệu. Chỉ lỗi thật khi gọi push (mạng, quyền) mới ném lỗi và retry.
  // Ngưỡng: outbox phình to vì dòng đỗ thì thêm dọn theo tuổi.
  private suspend fun pushOne(entry: SyncOutboxEntity): Boolean {
    suspend fun ended(relationshipId: String) =
      dao.getOnlineRelationshipSync(relationshipId)?.status?.let { it != com.example.data.model.RelationshipStatus.ACTIVE } == true
    fun park(why: String): Boolean {
      Log.w("SyncWorker", "outbox ${entry.id} (${entry.entityType}) parked: $why")
      return false
    }
    // Chỉ bọc phần giải mã payload; lỗi IOException của push phải vẫn là lỗi thật để retry.
    fun <T : Any> decode(adapter: JsonAdapter<T>): T? =
      try { adapter.fromJson(entry.payloadJson) } catch (e: JsonDataException) { null } catch (e: IOException) { null }
    when (entry.entityType) {
      MemorySyncAdapter.entityType -> {
        val entity = decode(moshiAdapterFor<SharedMemoryEntity>()) ?: return park("memory payload invalid")
        val relationshipId = entity.relationshipId ?: return park("memory has no relationshipId")
        if (ended(relationshipId)) return false
        val path = "${MemorySyncAdapter.collectionPath(relationshipId)}/${entity.syncId}"
        push(path, MemorySyncAdapter.toFirestoreMap(entity))
      }
      AnniversarySyncAdapter.entityType -> {
        val entity = decode(moshiAdapterFor<AnniversaryDateEntity>()) ?: return park("anniversary payload invalid")
        val relationshipId = entity.relationshipId ?: return park("anniversary has no relationshipId")
        if (ended(relationshipId)) return false
        val path = "${AnniversarySyncAdapter.collectionPath(relationshipId)}/${entity.syncId}"
        push(path, AnniversarySyncAdapter.toFirestoreMap(entity))
      }
      else -> return park("unknown entityType '${entry.entityType}'")
    }
    return true
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
      // Chế độ local-only: huỷ cả việc đã lên lịch từ bản cũ (KEEP sống mãi), rồi thoát.
      if (!com.example.domain.DataResidencyPolicy.cloudContent) {
        WorkManager.getInstance(context).run {
          cancelUniqueWork(UNIQUE_PERIODIC_NAME)
          cancelUniqueWork(UNIQUE_IMMEDIATE_NAME)
        }
        return
      }
      val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
      val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
        .setConstraints(constraints)
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
        .build()
      WorkManager.getInstance(context)
        .enqueueUniquePeriodicWork(UNIQUE_PERIODIC_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun enqueueImmediate(context: Context) {
      if (!com.example.domain.DataResidencyPolicy.cloudContent) return
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
  private val fakeFirestorePush: (suspend (String, Map<String, Any?>) -> Unit)? = null,
  private val cloudContent: () -> Boolean = { com.example.domain.DataResidencyPolicy.cloudContent }
) : androidx.work.WorkerFactory() {
  override fun createWorker(
    appContext: Context,
    workerClassName: String,
    workerParameters: WorkerParameters
  ): androidx.work.ListenableWorker? {
    return if (workerClassName == SyncWorker::class.java.name) {
      if (fakeFirestorePush != null) SyncWorker(appContext, workerParameters, dao, fakeFirestorePush, cloudContent)
      else SyncWorker(appContext, workerParameters, dao, cloudContent = cloudContent)
    } else null
  }
}
