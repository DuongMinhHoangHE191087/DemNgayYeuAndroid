package com.example.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.model.PRIVACY_PRIVATE
import com.example.data.model.SharedMemoryEntity
import com.example.data.sync.SyncCoordinator
import com.example.data.sync.SyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InLoveRepositorySyncTest {

  private lateinit var db: AppDatabase
  private lateinit var repo: InLoveRepository

  @Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
      .allowMainThreadQueries().build()
    repo = InLoveRepository(db.inLoveDao(), context)
  }

  @After
  fun tearDown() {
    db.close()
  }

  @Test
  fun addSharedMemory_assignsSyncId_andEnqueuesOutboxEntry() = runBlocking {
    repo.addSharedMemory(title = "Đi biển", dateText = "2026-06-01", photoUri = "file:///beach.jpg")

    val saved = db.inLoveDao().getAllSharedMemories().first().first()
    assert(saved.syncId.isNotBlank()) { "addSharedMemory must assign a syncId" }
    assert(saved.pendingSync) { "a freshly created memory is pending push" }

    val outbox = db.inLoveDao().getPendingOutboxEntries()
    assert(outbox.size == 1)
    assert(outbox.first().entityType == "memory")
    assert(outbox.first().syncId == saved.syncId)
  }

  @Test
  fun addSharedMemory_private_staysLocal_noOutbox() = runBlocking {
    repo.addSharedMemory(title = "Riêng tư", dateText = "2026-06-01", photoUri = "file:///secret.jpg", privacyLevel = PRIVACY_PRIVATE)

    val saved = db.inLoveDao().getAllSharedMemories().first().single()
    assert(saved.privacyLevel == PRIVACY_PRIVATE)
    assert(!saved.pendingSync) { "a private memory is never pending push" }
    assert(db.inLoveDao().getPendingOutboxEntries().isEmpty()) { "a private memory must not enter the outbox" }
  }

  @Test
  fun updateSharedMemory_sharedToPrivate_sendsStrippedTombstone_keepsLocalContent() = runBlocking {
    repo.addSharedMemory(title = "Đi biển", dateText = "2026-06-01", photoUri = "file:///beach.jpg", note = "bí mật")
    val shared = db.inLoveDao().getAllSharedMemories().first().single()

    repo.updateSharedMemory(shared.copy(privacyLevel = PRIVACY_PRIVATE))

    val outbox = db.inLoveDao().getPendingOutboxEntries().single { it.syncId == shared.syncId }
    assert(outbox.operation == "DELETE") { "switching to private must tombstone the remote copy" }
    val payload = SyncWorker.moshiAdapterFor<SharedMemoryEntity>().fromJson(outbox.payloadJson)!!
    assert(payload.deleted)
    assert(payload.title.isEmpty() && payload.note.isEmpty() && payload.photoUri.isEmpty()) { "tombstone must not carry content" }
    assert(payload.privacyLevel == "COUPLE_ONLY") { "tombstone keeps the shared privacy level, not PRIVATE" }

    val local = db.inLoveDao().getSharedMemoryBySyncId(shared.syncId)!!
    assert(local.privacyLevel == PRIVACY_PRIVATE)
    assert(local.title == "Đi biển" && local.note == "bí mật") { "the owner's device keeps the full memory" }
    assert(!local.deleted)
  }

  @Test
  fun updateSharedMemory_sharedToPrivate_supersedesPendingUpsert() = runBlocking {
    repo.addSharedMemory(title = "Đi biển", dateText = "2026-06-01", photoUri = "file:///beach.jpg")
    val shared = db.inLoveDao().getAllSharedMemories().first().single()

    repo.updateSharedMemory(shared.copy(privacyLevel = PRIVACY_PRIVATE))

    val forThisMemory = db.inLoveDao().getPendingOutboxEntries().filter { it.syncId == shared.syncId }
    assert(forThisMemory.size == 1) { "the stale UPSERT must be superseded, not replayed after the tombstone" }
    assert(forThisMemory.single().operation == "DELETE")
  }

  @Test
  fun deleteSharedMemory_private_isLocalOnly() = runBlocking {
    repo.addSharedMemory(title = "Riêng tư", dateText = "2026-06-01", photoUri = "file:///secret.jpg", privacyLevel = PRIVACY_PRIVATE)
    val saved = db.inLoveDao().getAllSharedMemories().first().single()

    repo.deleteSharedMemory(saved.id)

    assert(db.inLoveDao().getAllSharedMemories().first().isEmpty()) { "the private memory is deleted locally" }
    assert(db.inLoveDao().getPendingOutboxEntries().isEmpty()) { "a private delete must not reach the outbox" }
  }

  @Test
  fun updateSharedMemory_privateCannotBecomeShared() = runBlocking {
    repo.addSharedMemory(title = "Riêng tư", dateText = "2026-06-01", photoUri = "file:///secret.jpg", privacyLevel = PRIVACY_PRIVATE)
    val saved = db.inLoveDao().getAllSharedMemories().first().single()

    repo.updateSharedMemory(saved.copy(privacyLevel = "COUPLE_ONLY", title = "Đổi tên"))

    val local = db.inLoveDao().getAllSharedMemories().first().single()
    assert(local.privacyLevel == PRIVACY_PRIVATE) { "private is one-way: media may already sit on a public URL" }
    assert(local.title == "Đổi tên") { "other edits still save" }
    assert(db.inLoveDao().getPendingOutboxEntries().isEmpty()) { "a private memory never reaches the outbox" }
  }

  @Test
  fun applyRemoteMemory_doesNotOverwriteOrHidePrivateLocalRow() = runBlocking {
    repo.addSharedMemory(title = "Riêng tư", dateText = "2026-06-01", photoUri = "file:///secret.jpg", privacyLevel = PRIVACY_PRIVATE)
    val local = db.inLoveDao().getAllSharedMemories().first().single()
    val coordinator = SyncCoordinator(db.inLoveDao(), null, CoroutineScope(SupervisorJob()))

    // Tombstone trở về từ đám mây, mới hơn bản local: vẫn không được chạm vào bản riêng tư.
    coordinator.applyRemoteMemory(local.copy(title = "", deleted = true, updatedAt = local.updatedAt + 1_000, pendingSync = false))

    val after = db.inLoveDao().getSharedMemoryBySyncId(local.syncId)!!
    assert(after.title == "Riêng tư" && !after.deleted) { "a remote copy must never overwrite or hide a private memory" }
  }

  @Test
  fun addSharedMemory_usesGivenSyncId_forUploadedMemory() = runBlocking {
    repo.addSharedMemory(
      title = "Có ảnh", dateText = "2026-06-01", photoUri = "file:///beach.jpg",
      syncId = "mem-1", cloudinaryPublicId = "inlove_mem_mem-1",
    )

    val saved = db.inLoveDao().getAllSharedMemories().first().single()
    assert(saved.syncId == "mem-1") { "the syncId is the upload's memoryId, so it must be kept as given" }
    assert(saved.cloudinaryPublicId == "inlove_mem_mem-1")
  }

  @Test
  fun updateSharedMemory_sharedToPrivate_clearsLocalCloudinaryPublicId() = runBlocking {
    repo.addSharedMemory(
      title = "Đi biển", dateText = "2026-06-01", photoUri = "file:///beach.jpg",
      syncId = "mem-2", cloudinaryPublicId = "inlove_mem_mem-2",
    )
    val shared = db.inLoveDao().getAllSharedMemories().first().single()

    repo.updateSharedMemory(shared.copy(privacyLevel = PRIVACY_PRIVATE))

    val local = db.inLoveDao().getSharedMemoryBySyncId("mem-2")!!
    assert(local.cloudinaryPublicId == null) { "the tombstone releases the cloud asset, so this device must not still claim it" }
    assert(local.photoUri == "file:///beach.jpg") { "the owner's device keeps its own copy" }
  }
}
