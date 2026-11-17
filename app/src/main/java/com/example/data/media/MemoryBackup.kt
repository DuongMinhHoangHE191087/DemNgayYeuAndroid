package com.example.data.media

import android.util.Log
import com.example.data.db.InLoveDao
import com.example.data.model.CoupleProfileEntity
import com.example.data.model.OnlineRelationshipEntity
import com.example.data.model.OnlineUserEntity
import com.example.data.model.SharedMemoryEntity
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import java.io.File
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

// Lớp riêng cho file manifest (không dùng thẳng entity Room) để đổi entity không âm thầm đổi định dạng file.
// Không bao giờ ghi mật khẩu, token, PIN hay e-mail.
@JsonClass(generateAdapter = true)
data class MemoryBackupFile(
  val version: Int = 1,
  val savedAt: Long = 0,
  val memories: List<MemoryBackupEntry> = emptyList()
)

@JsonClass(generateAdapter = true)
data class MemoryBackupEntry(
  val syncId: String,
  val title: String = "",
  val dateText: String = "",
  val note: String = "",
  val photoPath: String = "",
  val videoPath: String? = null,
  val location: String = "",
  val isFavorite: Boolean = false,
  val anniversaryTitle: String = "",
  val createdAt: Long = 0,
  val updatedAt: Long = 0,
  val mediaType: String = "IMAGE",
  val durationSeconds: Int = 0,
  val fileSizeFormatted: String = "",
  val privacyLevel: String = "COUPLE_ONLY",
  val relationshipId: String? = null,
  val authorId: String = "",
  val authorName: String = "",
  val partnerName: String = ""
)

/**
 * Sao lưu manifest kỷ niệm vào filesDir/media (nằm trong allow-list Auto Backup) và khôi phục idempotent.
 * [run] = restoreOnce() xong mới chạy collector, nên collector không bao giờ ghi đè manifest từ bảng rỗng.
 */
class MemoryBackup(
  private val filesDir: File,
  private val noBackupDir: File,
  private val dao: InLoveDao,
  private val debounceMs: Long = 2_000,
  private val clock: () -> Long = System::currentTimeMillis,
  private val readText: (File) -> String = File::readText
) {

  suspend fun run() {
    if (restoreOnce()) collect()
  }

  /** true = bảng live đã là nguồn sự thật, collector được phép chạy. */
  suspend fun restoreOnce(): Boolean {
    val marker = File(noBackupDir, MARKER)
    if (marker.exists()) return true
    val manifest = File(File(filesDir, MediaPath.DIR), MediaPath.BACKUP_MANIFEST)
    // Lỗi I/O tạm thời (khác JSON hỏng): không ghi marker, lần mở sau đọc lại.
    val backup = if (manifest.isFile) {
      val text = try {
        readText(manifest)
      } catch (e: IOException) {
        // ponytail: manifest hỏng vĩnh viễn (không đọc được) bị thử lại mỗi lần mở và collector không
        // bao giờ chạy, nên không có bản sao lưu mới; nâng cấp = đổi tên manifest sau N lần lỗi liên tiếp.
        Log.w(TAG, "manifest đọc lỗi, thử lại lần sau")
        return false
      }
      decode(text)
    } else null
    if (backup == null) {
      if (manifest.isFile) Log.w(TAG, "manifest không đọc được, bỏ qua")
      writeMarker(marker)
      return true
    }
    var inserted = 0
    var skipped = 0
    var failed = 0
    for (e in backup.memories) {
      // Mỗi entry một try/catch: một dòng hỏng không chặn các dòng còn lại.
      try {
        if (!isRestorable(e) || dao.getSharedMemoryBySyncId(e.syncId) != null) {
          skipped++
        } else {
          dao.insertSharedMemory(e.toEntity())
          inserted++
        }
      } catch (ce: CancellationException) {
        throw ce
      } catch (t: Exception) {
        failed++
      }
    }
    Log.i(TAG, "restore: inserted=$inserted skipped=$skipped failed=$failed")
    // Có entry lỗi thì KHÔNG ghi marker và không chạy collector: lần mở sau thử lại, entry đã chèn bị bỏ qua theo syncId.
    if (failed > 0) return false
    writeMarker(marker)
    return true
  }

  @OptIn(FlowPreview::class)
  suspend fun collect() {
    combine(dao.getAllSharedMemories(), dao.getCoupleProfile(), dao.observeRelationships()) { m, p, r ->
      Triple(m, p, r)
    }.debounce(debounceMs).collect { (memories, profile, relationships) ->
      try {
        if (memories.isEmpty()) {
          deleteManifest(filesDir)
        } else {
          val file = snapshot(
            memories, profile, relationships,
            dao.getAllOnlineUsersListSync(), dao.getCurrentOnlineUserSync(), clock()
          )
          // Bảng có thể còn đường dẫn tuyệt đối cũ: manifest luôn ghi dạng tương đối.
          writeManifest(filesDir, file.copy(memories = file.memories.map {
            it.copy(
              photoPath = MediaPath.toStored(filesDir, it.photoPath).orEmpty(),
              videoPath = MediaPath.toStored(filesDir, it.videoPath)
            )
          }))
        }
      } catch (ce: CancellationException) {
        throw ce
      } catch (t: Exception) {
        Log.w(TAG, "ghi manifest lỗi: ${t.message}")
      }
    }
  }

  private fun writeMarker(marker: File) {
    runCatching {
      marker.parentFile?.mkdirs()
      marker.writeText("1")
    }
  }

  // Ảnh preset là URL https chứ không phải file trên máy: không có gì để kiểm, giữ nguyên khi khôi phục (T15/A-1).
  private fun isRemote(path: String?): Boolean = path != null && path.startsWith("https://", ignoreCase = true)

  // Mọi đường dẫn media cục bộ không rỗng phải là dạng tương đối thuộc filesDir/media và file còn tồn tại.
  private fun isRestorable(e: MemoryBackupEntry): Boolean =
    e.syncId.isNotBlank() &&
      listOf(e.photoPath, e.videoPath).filter { !it.isNullOrBlank() }.all {
        isRemote(it) || (MediaPath.ownedFile(filesDir, it)?.isFile == true && MediaPath.toStored(filesDir, it) == it)
      }

  private fun MemoryBackupEntry.toEntity() = SharedMemoryEntity(
    title = title, dateText = dateText, note = note, photoUri = photoPath, location = location,
    isFavorite = isFavorite, anniversaryTitle = anniversaryTitle, createdAt = createdAt,
    relationshipId = relationshipId, authorId = authorId, authorName = authorName,
    mediaType = mediaType, videoUri = videoPath,
    // Entity mặc định isCloudinaryStored = true, ở đây file nằm trên máy nên phải false.
    isCloudinaryStored = false,
    fileSizeFormatted = fileSizeFormatted, durationSeconds = durationSeconds,
    privacyLevel = privacyLevel, syncId = syncId, updatedAt = updatedAt,
    deleted = false, pendingSync = false
  )

  companion object {
    private const val TAG = "MemoryBackup"
    const val MARKER = "memories_restore_done"

    private val adapter by lazy { Moshi.Builder().build().adapter(MemoryBackupFile::class.java) }

    /** Dựng nội dung manifest từ dữ liệu live; hàm thuần để test. */
    internal fun snapshot(
      memories: List<SharedMemoryEntity>,
      profile: CoupleProfileEntity?,
      relationships: List<OnlineRelationshipEntity>,
      users: List<OnlineUserEntity>,
      me: OnlineUserEntity?,
      nowMs: Long
    ): MemoryBackupFile {
      val fallback = profile?.partner2Name.orEmpty()
      fun partnerOf(relationshipId: String?): String {
        val rel = relationships.firstOrNull { it.relationshipId == relationshipId } ?: return fallback
        if (me == null) return fallback
        val other = if (rel.user1 == me.uid) rel.user2 else rel.user1
        return users.firstOrNull { it.uid == other && !it.isCurrentUser }
          ?.displayName?.takeIf { it.isNotBlank() } ?: fallback
      }
      return MemoryBackupFile(
        version = 1,
        savedAt = nowMs,
        memories = memories.map {
          MemoryBackupEntry(
            syncId = it.syncId.ifBlank { "legacy_${it.createdAt}" },
            title = it.title, dateText = it.dateText, note = it.note,
            photoPath = it.photoUri, videoPath = it.videoUri, location = it.location,
            isFavorite = it.isFavorite, anniversaryTitle = it.anniversaryTitle,
            createdAt = it.createdAt, updatedAt = it.updatedAt, mediaType = it.mediaType,
            durationSeconds = it.durationSeconds, fileSizeFormatted = it.fileSizeFormatted,
            privacyLevel = it.privacyLevel, relationshipId = it.relationshipId,
            authorId = it.authorId, authorName = it.authorName,
            partnerName = partnerOf(it.relationshipId)
          )
        }
      )
    }

    /** null khi JSON hỏng; không bao giờ ném lỗi. */
    internal fun decode(json: String): MemoryBackupFile? =
      try { adapter.fromJson(json) } catch (_: Exception) { null }

    // Ghi ra .tmp rồi đổi tên: máy bị tắt giữa chừng không để lại manifest dở.
    internal fun writeManifest(filesDir: File, file: MemoryBackupFile) {
      val dir = File(filesDir, MediaPath.DIR).apply { mkdirs() }
      val target = File(dir, MediaPath.BACKUP_MANIFEST)
      val tmp = File(dir, MediaPath.BACKUP_MANIFEST + ".tmp")
      tmp.writeText(adapter.toJson(file))
      // renameTo thay file cũ nguyên tử trên Android; trên Windows (test) phải xoá đích trước.
      if (!tmp.renameTo(target)) {
        target.delete()
        check(tmp.renameTo(target)) { "không đổi tên được manifest" }
      }
    }

    /** Danh sách rỗng thì xoá manifest, để kỷ niệm đã xoá không còn để lại chữ nào. */
    internal fun deleteManifest(filesDir: File) {
      File(File(filesDir, MediaPath.DIR), MediaPath.BACKUP_MANIFEST).delete()
    }
  }
}
