package com.example.data.media

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker.Result
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.cloud.AndroidCloudFunctionsClient
import com.example.data.db.AppDatabase
import com.example.data.db.InLoveDao
import com.example.domain.DataResidencyPolicy
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** Đẩy avatar `file:` lên R2 khi có mạng. Constructor thường nên WorkerFactory mặc định dựng được. */
class AvatarUploadWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

  override suspend fun doWork(): Result {
    val publisher = AvatarPublisher(
      dao = AppDatabase.getDatabase(applicationContext).inLoveDao(),
      uploader = AvatarUploader(AndroidCloudFunctionsClient(), OkHttpMediaUploader()),
      avatarsDir = File(applicationContext.filesDir, "avatars"),
      cacheDir = applicationContext.cacheDir,
    )
    return try {
      resultFor(publisher.publishPending(), runAttemptCount)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      resultForFailure(runAttemptCount)
    }
  }

  companion object {
    const val UNIQUE_NAME = "avatar_upload"
    const val MAX_ATTEMPTS = 8

    internal fun resultFor(outcome: AvatarPublisher.Outcome, attempt: Int): Result = when (outcome) {
      AvatarPublisher.Outcome.PUBLISHED,
      AvatarPublisher.Outcome.NOTHING_TO_DO,
      AvatarPublisher.Outcome.NOT_CONFIGURED -> Result.success()
      AvatarPublisher.Outcome.UNAUTHENTICATED,
      AvatarPublisher.Outcome.LOST_RACE -> resultForFailure(attempt)
    }

    /** Hết MAX_ATTEMPTS thì thôi, không retry vô hạn. */
    internal fun resultForFailure(attempt: Int): Result = if (attempt >= MAX_ATTEMPTS) Result.success() else Result.retry()

    fun request(): OneTimeWorkRequest = OneTimeWorkRequestBuilder<AvatarUploadWorker>()
      .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
      .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
      .build()

    fun enqueue(context: Context) {
      WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.KEEP, request())
    }

    /** Cờ tắt thì không làm gì; bật thì mỗi lần Room có avatar `file:` (mới/đổi) sẽ xếp một công việc (KEEP nên không chồng). */
    fun watch(
      context: Context,
      dao: InLoveDao,
      scope: CoroutineScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, t -> Log.e("AvatarUploadWorker", "watch failed", t) },
      ),
      enabled: Boolean = DataResidencyPolicy.avatarsOnR2,
    ) {
      if (!enabled) return
      scope.launch {
        dao.watchLocalAvatarKeys("file:").distinctUntilChanged().collect { keys -> if (keys.isNotEmpty()) enqueue(context) }
      }
    }
  }
}
