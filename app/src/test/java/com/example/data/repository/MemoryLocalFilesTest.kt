package com.example.data.repository

import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.model.PRIVACY_PRIVATE
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

// Ảnh kỷ niệm lưu dạng tương đối "media/<tên>" và file bị xoá cùng kỷ niệm (trừ khi hàng khác còn dùng).
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MemoryLocalFilesTest {

  private lateinit var db: AppDatabase
  private lateinit var repo: InLoveRepository
  private lateinit var mediaDir: File
  private val created = mutableListOf<File>()

  @Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
      .allowMainThreadQueries().build()
    repo = InLoveRepository(db.inLoveDao(), context)
    mediaDir = File(context.filesDir, "media").also { it.mkdirs() }
  }

  @After
  fun tearDown() {
    created.forEach { it.delete() }
    db.close()
  }

  private fun mediaFile(name: String): File =
    File(mediaDir, name).also {
      it.writeText("x")
      created += it
    }

  private suspend fun rows() = db.inLoveDao().getAllSharedMemories().first()

  @Test
  fun add_absolutePathInsideMedia_isStoredRelative() = runBlocking {
    val f = mediaFile("t8a_add.jpg")
    repo.addSharedMemory(title = "A", dateText = "2026-06-01", photoUri = f.absolutePath)
    assertEquals("media/t8a_add.jpg", rows().single().photoUri)
  }

  @Test
  fun add_fileUriForVideo_isStoredRelative() = runBlocking {
    val photo = mediaFile("t8a_poster.jpg")
    val video = mediaFile("t8a_clip.mp4")
    repo.addSharedMemory(
      title = "A", dateText = "2026-06-01",
      photoUri = Uri.fromFile(photo).toString(), videoUri = Uri.fromFile(video).toString(),
    )
    val saved = rows().single()
    assertEquals("media/t8a_poster.jpg", saved.photoUri)
    assertEquals("media/t8a_clip.mp4", saved.videoUri)
  }

  @Test
  fun add_httpsPhoto_isStoredUnchanged() = runBlocking {
    repo.addSharedMemory(title = "A", dateText = "2026-06-01", photoUri = "https://example.com/a.jpg")
    assertEquals("https://example.com/a.jpg", rows().single().photoUri)
  }

  @Test
  fun edit_keepsRelativeValue() = runBlocking {
    val f = mediaFile("t8a_edit.jpg")
    repo.addSharedMemory(title = "A", dateText = "2026-06-01", photoUri = f.absolutePath)
    repo.updateSharedMemory(rows().single().copy(title = "B"))
    val saved = rows().single()
    assertEquals("B", saved.title)
    assertEquals("media/t8a_edit.jpg", saved.photoUri)
    assertTrue(f.exists())
  }

  @Test
  fun delete_private_removesFile() = runBlocking {
    val f = mediaFile("t8a_private.jpg")
    repo.addSharedMemory(title = "A", dateText = "2026-06-01", photoUri = f.absolutePath, privacyLevel = PRIVACY_PRIVATE)
    repo.deleteSharedMemory(rows().single().id)
    assertFalse(f.exists())
  }

  @Test
  fun delete_coupleOnly_removesFile_andStillEnqueuesTombstone() = runBlocking {
    val f = mediaFile("t8a_shared.jpg")
    repo.addSharedMemory(title = "A", dateText = "2026-06-01", photoUri = f.absolutePath)
    val saved = rows().single()
    repo.deleteSharedMemory(saved.id)
    assertFalse(f.exists())
    val outbox = db.inLoveDao().getPendingOutboxEntries().single { it.syncId == saved.syncId }
    assertEquals("DELETE", outbox.operation)
  }

  @Test
  fun delete_keepsFileUntilSecondRowSharingItIsDeleted() = runBlocking {
    val f = mediaFile("t8a_shared_file.jpg")
    repo.addSharedMemory(title = "A", dateText = "2026-06-01", photoUri = f.absolutePath)
    repo.addSharedMemory(title = "B", dateText = "2026-06-02", photoUri = f.absolutePath)
    val (first, second) = rows().map { it.id }
    repo.deleteSharedMemory(first)
    assertTrue("Còn một hàng dùng file nên phải giữ lại", f.exists())
    repo.deleteSharedMemory(second)
    assertFalse(f.exists())
  }
}
