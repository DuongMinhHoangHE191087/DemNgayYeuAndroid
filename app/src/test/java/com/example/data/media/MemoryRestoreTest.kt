package com.example.data.media

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.db.InLoveDao
import com.example.data.model.SharedMemoryEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MemoryRestoreTest {

  private lateinit var db: AppDatabase
  private lateinit var dao: InLoveDao
  private lateinit var filesDir: File
  private lateinit var noBackupDir: File
  private lateinit var marker: File
  private lateinit var manifest: File

  @Before
  fun setUp() {
    val ctx = ApplicationProvider.getApplicationContext<Context>()
    db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
    dao = db.inLoveDao()
    filesDir = ctx.filesDir
    noBackupDir = ctx.noBackupFilesDir
    marker = File(noBackupDir, MemoryBackup.MARKER)
    manifest = File(File(filesDir, MediaPath.DIR), MediaPath.BACKUP_MANIFEST)
    File(filesDir, MediaPath.DIR).deleteRecursively()
    marker.delete()
  }

  @After
  fun tearDown() {
    db.close()
    File(filesDir, MediaPath.DIR).deleteRecursively()
    marker.delete()
  }

  private fun mediaFile(name: String) {
    File(filesDir, "${MediaPath.DIR}/$name").apply { parentFile!!.mkdirs() }.writeBytes(byteArrayOf(1, 2, 3))
  }

  private fun entry(
    syncId: String,
    photo: String = "media/$syncId.jpg",
    video: String? = null,
    title: String = "t-$syncId",
    updatedAt: Long = 777L
  ) = MemoryBackupEntry(
    syncId = syncId, title = title, dateText = "2026-01-01", photoPath = photo, videoPath = video,
    updatedAt = updatedAt, createdAt = 50L, relationshipId = "r1", authorName = "Minh"
  )

  private fun writeManifest(vararg entries: MemoryBackupEntry) =
    MemoryBackup.writeManifest(filesDir, MemoryBackupFile(1, 1L, entries.toList()))

  private fun backup(d: InLoveDao = dao) = MemoryBackup(filesDir, noBackupDir, d, debounceMs = 50)

  private fun rowCount(): Int =
    db.query("SELECT COUNT(*) FROM shared_memories", null).use { it.moveToFirst(); it.getInt(0) }

  // Proxy quanh dao thật: gọi hook trước mỗi lệnh để test chèn lỗi / quan sát thứ tự.
  private fun wrap(real: InLoveDao, before: (String, Array<Any?>) -> Unit): InLoveDao =
    Proxy.newProxyInstance(InLoveDao::class.java.classLoader, arrayOf(InLoveDao::class.java)) { _, method, args ->
      val a: Array<Any?> = args ?: emptyArray()
      before(method.name, a)
      try {
        method.invoke(real, *a)
      } catch (e: InvocationTargetException) {
        throw e.targetException
      }
    } as InLoveDao

  @Test
  fun restore_insertsRowWithLocalOnlyProperties() = runBlocking {
    mediaFile("s1.jpg")
    writeManifest(entry("s1"))

    assertTrue(backup().restoreOnce())

    val row = dao.getSharedMemoryBySyncId("s1")!!
    assertTrue(row.id > 0)
    assertEquals("t-s1", row.title)
    assertEquals("media/s1.jpg", row.photoUri)
    assertEquals(777L, row.updatedAt)
    assertEquals("r1", row.relationshipId)
    assertFalse(row.pendingSync)
    assertFalse(row.deleted)
    assertFalse(row.isCloudinaryStored)
    assertNull(row.cloudinaryUrl)
    assertNull(row.cloudinaryPublicId)
    assertTrue(dao.getPendingOutboxEntries(100).isEmpty())
    assertTrue(marker.isFile)
  }

  @Test
  fun restore_keepsHttpsPresetCover_withoutAFileOnDisk() = runBlocking {
    // T15/A-1: ảnh bìa preset là URL https, không có file trong media/. Khôi phục phải giữ nó chứ không bỏ qua cả kỷ niệm.
    val preset = "https://res.example.com/presets/cover1.jpg"
    writeManifest(entry("p1", photo = preset))

    assertTrue(backup().restoreOnce())

    assertEquals(preset, dao.getSharedMemoryBySyncId("p1")!!.photoUri)
  }

  @Test
  fun restore_skipsEntryWhoseFileIsMissing_orPathIsNotOwnedRelative() = runBlocking {
    mediaFile("ok.jpg")
    mediaFile("abs.jpg")
    val absolute = File(filesDir, "media/abs.jpg").absolutePath
    writeManifest(
      entry("missing", photo = "media/nope.jpg"),
      entry("abs", photo = absolute),
      entry("escape", photo = "media/../x.jpg"),
      entry("badvideo", photo = "media/ok.jpg", video = "media/nope.mp4"),
      // Giá trị https không còn bị bỏ qua (ảnh bìa preset): xem restore_keepsHttpsPresetCover_withoutAFileOnDisk (T15/A-1).
      entry("ok", photo = "media/ok.jpg")
    )

    assertTrue(backup().restoreOnce())

    assertEquals(1, rowCount())
    assertNotNull(dao.getSharedMemoryBySyncId("ok"))
    assertTrue(marker.isFile)
  }

  @Test
  fun restore_doesNothing_whenMarkerExists() = runBlocking {
    mediaFile("s1.jpg")
    writeManifest(entry("s1"))
    noBackupDir.mkdirs()
    marker.writeText("1")

    assertTrue(backup().restoreOnce())

    assertEquals(0, rowCount())
  }

  @Test
  fun restore_secondRunInsertsNothingNew() = runBlocking {
    mediaFile("s1.jpg")
    writeManifest(entry("s1"))
    assertTrue(backup().restoreOnce())
    marker.delete()

    assertTrue(backup().restoreOnce())

    assertEquals(1, rowCount())
  }

  @Test
  fun restore_liveOrSoftDeletedRowWithSameSyncId_blocksTheInsert() = runBlocking {
    listOf("live", "gone", "new").forEach { mediaFile("$it.jpg") }
    dao.insertSharedMemory(
      SharedMemoryEntity(title = "orig", dateText = "d", photoUri = "media/live.jpg", syncId = "live")
    )
    dao.insertSharedMemory(
      SharedMemoryEntity(title = "orig", dateText = "d", photoUri = "media/gone.jpg", syncId = "gone", deleted = true)
    )
    writeManifest(entry("live"), entry("gone"), entry("new"))

    assertTrue(backup().restoreOnce())

    assertEquals(3, rowCount())
    assertEquals("orig", dao.getSharedMemoryBySyncId("live")!!.title)
    assertEquals("orig", dao.getSharedMemoryBySyncId("gone")!!.title)
    assertEquals("t-new", dao.getSharedMemoryBySyncId("new")!!.title)
  }

  @Test
  fun restore_throwingEntry_leavesNoMarker_andRunDoesNotStartTheCollector() = runBlocking {
    mediaFile("boom.jpg")
    mediaFile("fine.jpg")
    writeManifest(entry("boom", title = "boom"), entry("fine"))
    val throwing = wrap(dao) { name, args ->
      if (name == "insertSharedMemory" && (args[0] as SharedMemoryEntity).title == "boom") {
        throw IllegalStateException("insert failed")
      }
    }

    assertFalse(backup(throwing).restoreOnce())
    assertFalse(marker.exists())
    assertNotNull(dao.getSharedMemoryBySyncId("fine"))
    assertNull(dao.getSharedMemoryBySyncId("boom"))

    // run() quay về ngay và manifest còn nguyên (collector mà chạy sẽ ghi lại savedAt mới).
    withTimeout(10_000) { backup(throwing).run() }
    val back = MemoryBackup.decode(manifest.readText())!!
    assertEquals(1L, back.savedAt)
    assertEquals(listOf("boom", "fine"), back.memories.map { it.syncId })
  }

  @Test
  fun restore_writesTheMarkerOnlyAfterTheLoop() = runBlocking {
    mediaFile("a.jpg")
    mediaFile("b.jpg")
    writeManifest(entry("a"), entry("b"))
    var markerSeenDuringLoop = false
    val observing = wrap(dao) { name, _ ->
      if (name == "insertSharedMemory" && marker.exists()) markerSeenDuringLoop = true
    }

    assertTrue(backup(observing).restoreOnce())

    assertFalse(markerSeenDuringLoop)
    assertTrue(marker.isFile)
    assertEquals(2, rowCount())
  }

  @Test
  fun restore_garbageManifest_isIgnored_andMarkerIsStillWritten() = runBlocking {
    manifest.parentFile!!.mkdirs()
    manifest.writeText("not json {{")

    assertTrue(backup().restoreOnce())

    assertEquals(0, rowCount())
    assertTrue(marker.isFile)
  }

  @Test
  fun restore_unreadableManifest_ioError_returnsFalse_noMarker_thenRetrySucceeds() = runBlocking {
    mediaFile("s1.jpg")
    writeManifest(entry("s1"))

    val failing = MemoryBackup(
      filesDir, noBackupDir, dao, debounceMs = 50,
      readText = { throw java.io.IOException("đọc lỗi") }
    )
    assertFalse(failing.restoreOnce())
    assertFalse(marker.exists())
    assertTrue(manifest.isFile)
    assertEquals(0, rowCount())

    assertTrue(backup().restoreOnce())
    assertTrue(marker.isFile)
    assertEquals(1, rowCount())
  }

  @Test
  fun restore_missingManifest_writesMarker() = runBlocking {
    assertTrue(backup().restoreOnce())
    assertTrue(marker.isFile)
  }

  @Test
  fun collector_neverRunsBeforeTheRestore() = runBlocking {
    mediaFile("s1.jpg")
    writeManifest(entry("s1"))
    val job = CoroutineScope(Dispatchers.IO).launch { backup().run() }

    // Bảng ban đầu rỗng: nếu collector chạy trước restore thì manifest đã bị xoá. Chờ tới khi nó ghi lại (savedAt != 1).
    withTimeout(15_000) {
      while ((manifest.takeIf { it.isFile }?.let { MemoryBackup.decode(it.readText()) }?.savedAt ?: 1L) == 1L) delay(50)
    }
    job.cancelAndJoin()

    val back = MemoryBackup.decode(manifest.readText())!!
    assertEquals(listOf("s1"), back.memories.map { it.syncId })
    assertNotNull(dao.getSharedMemoryBySyncId("s1"))
  }
}
