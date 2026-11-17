package com.example.data.sync

import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import com.example.data.db.AccountDataVault
import com.example.data.model.OnlineUserEntity
import com.example.data.sync.ProfileProjection.Remote
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Đồng bộ hồ sơ cá nhân với Firestore users/{uid}: kéo đúng 1 lần mỗi phiên rồi đẩy mỗi khi bản chiếu đổi (debounce).
 * Không biết gì về Firestore/Room: mọi thứ vào qua lambda (nối dây ở AppServiceLocator, task P15).
 * Chính sách xung đột và ý nghĩa của prefs `synced_<uid>` theo plan §3.2 (B-03, r3).
 * [push] nhận bản chiếu 6 trường + schemaVersion; người nối dây tự thêm `updatedAt = serverTimestamp()` và dùng `set` ghi đè toàn bộ.
 * Mọi lỗi im lặng: chỉ log từ cố định và tên lớp exception, KHÔNG BAO GIỜ log giá trị hồ sơ.
 */
class ProfileSync(
  private val observeRow: (String) -> Flow<OnlineUserEntity?>,
  private val prefs: SharedPreferences,
  private val scope: CoroutineScope,
  private val fetch: suspend (String) -> Map<String, Any?>?,
  private val push: (uid: String, data: Map<String, Any?>, onDone: (PushOutcome) -> Unit) -> Unit,
  private val signedInUid: () -> String?
) {

  /** Kết quả một lần ghi do SDK báo về: ACKED = server đã nhận, DENIED = rules từ chối, FAILED = lỗi khác (offline...). */
  enum class PushOutcome { ACKED, DENIED, FAILED }

  /** Chỉ những gì collector cần: cờ đã thiết lập hồ sơ và bản chiếu (so sánh bằng equals để bỏ lần phát lại). */
  private data class Snap(val setUp: Boolean, val p: ProfileProjection)

  // Rào phiên giống SyncCoordinator: start()/stop() tăng generation, đổi tài khoản tăng AccountDataVault.epoch.
  @Volatile private var generation = 0L
  @Volatile private var epochAtStart = 0L
  @Volatile private var pendingKey: String? = null
  @Volatile private var denied = false
  private var job: Job? = null
  private var jobUid: String? = null

  private fun isLive(gen: Long) = gen == generation && epochAtStart == AccountDataVault.epoch.get()
  private fun live(gen: Long, uid: String) = isLive(gen) && signedInUid() == uid

  /** [apply] ghi bản từ xa vào dòng cục bộ; trả false khi key cục bộ đã khác [expectedLocalKey] hoặc đã đổi tài khoản. */
  fun start(uid: String, apply: suspend (uid: String, p: ProfileProjection, expectedLocalKey: String) -> Boolean) {
    if (job?.isActive == true && jobUid == uid) return
    stop()
    val gen = ++generation
    epochAtStart = AccountDataVault.epoch.get()
    pendingKey = null
    denied = false
    jobUid = uid
    job = scope.launch { run(uid, gen, apply) }
  }

  /** Dừng phiên. `denied = true` để không lần đẩy mới nào được xếp hàng; `pendingKey` giữ nguyên để ACKED muộn vẫn ghi được. */
  fun stop() {
    generation++
    denied = true
    job?.cancel()
    job = null
    jobUid = null
  }

  private suspend fun run(
    uid: String,
    gen: Long,
    apply: suspend (String, ProfileProjection, String) -> Boolean
  ) {
    val row = withTimeoutOrNull(ROW_WAIT_MS) { observeRow(uid).filterNotNull().first() } ?: return
    if (!live(gen, uid)) return
    val startKey = ProfileProjection.of(row).key()
    val remote = fetchRemote(uid)
    if (!live(gen, uid)) return
    val pullOk = pull(uid, gen, row, startKey, remote, apply)
    if (!live(gen, uid)) return
    collectPushes(uid, gen, startKey, pullOk)
  }

  private suspend fun fetchRemote(uid: String): Remote? = try {
    withTimeout(FETCH_TIMEOUT_MS) { ProfileProjection.fromRemote(fetch(uid)) }
  } catch (e: TimeoutCancellationException) {
    Log.w(TAG, "pull failed: timeout")
    null
  } catch (e: CancellationException) {
    throw e
  } catch (e: Exception) {
    Log.w(TAG, "pull failed: ${e.javaClass.simpleName}")
    null
  }

  /**
   * Chính sách xung đột §3.2: "cục bộ bẩn thắng, còn lại từ xa thắng". Trả [pullOk]: lần kéo cho kết quả dùng được.
   * Chỉ ghi `synced` ở đây; việc đẩy do collector làm (khi `synced` khác key cục bộ).
   */
  private suspend fun pull(
    uid: String,
    gen: Long,
    row: OnlineUserEntity,
    startKey: String,
    remote: Remote?,
    apply: suspend (String, ProfileProjection, String) -> Boolean
  ): Boolean = when (remote) {
    null, Remote.Invalid -> false
    Remote.Absent -> {
      Log.i(TAG, "pull absent")
      // Hồ sơ đã thiết lập: ép đẩy bản cục bộ (phủ cả doc bị xoá trên server). Dòng hạt giống: không đẩy, synced giữ nguyên.
      if (row.isProfileSetup) setSynced(uid, "")
      true
    }
    is Remote.Legacy -> {
      Log.i(TAG, "pull legacy")
      // B-08: cục bộ nguyên sơ thì nhận bản cũ (KHÔNG đặt synced, collector đẩy ngay bản schema 1); không thì đẩy cục bộ.
      if (ProfileProjection.isPristine(row)) {
        runApply(uid, gen, remote.p, startKey, apply)
      } else {
        if (row.isProfileSetup) setSynced(uid, "")
        true
      }
    }
    is Remote.Valid -> pullValid(uid, gen, row, startKey, remote.p, apply)
  }

  private suspend fun pullValid(
    uid: String,
    gen: Long,
    row: OnlineUserEntity,
    startKey: String,
    p: ProfileProjection,
    apply: suspend (String, ProfileProjection, String) -> Boolean
  ): Boolean {
    // ponytail: (1) máy chưa từng đồng bộ (synced == null): một sửa đổi làm lúc lần kéo đầu lỗi hoặc đang đua bị bản server đè
    // ở lần kéo thành công kế tiếp; (2) hai hồ sơ thật khác nhau ở lần đồng bộ đầu tiên thì từ xa thắng.
    // Nâng cấp = cờ "đã sửa" trong prefs hoặc hộp thoại chọn.
    val synced = prefs.getString(syncedKey(uid), null)
    val remoteKey = p.key()
    val keepLocal = synced != null && synced != startKey ||
      // F3: hồ sơ thật trên máy này không bị một doc trống kiểu hạt giống đè.
      synced == null && row.isProfileSetup && !ProfileProjection.isPristine(row) && ProfileProjection.isPristine(p)
    if (remoteKey == startKey) {
      setSynced(uid, remoteKey)
      return true
    }
    if (keepLocal) {
      setSynced(uid, "")
      return true
    }
    if (!live(gen, uid)) return false
    // Ghi synced TRƯỚC (chống echo: collector thấy key == synced); apply hỏng/huỷ thì trả synced về giá trị cũ.
    setSynced(uid, remoteKey)
    var ok = false
    try {
      ok = runApply(uid, gen, p, startKey, apply)
    } finally {
      if (!ok) setSynced(uid, synced)
    }
    return ok
  }

  private suspend fun runApply(
    uid: String,
    gen: Long,
    p: ProfileProjection,
    startKey: String,
    apply: suspend (String, ProfileProjection, String) -> Boolean
  ): Boolean {
    if (!live(gen, uid)) return false
    return try {
      apply(uid, p, startKey).also { if (it) Log.i(TAG, "pull applied") }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.w(TAG, "pull failed: ${e.javaClass.simpleName}")
      false
    }
  }

  @OptIn(FlowPreview::class)
  private suspend fun collectPushes(uid: String, gen: Long, startKey: String, pullOk: Boolean) {
    observeRow(uid)
      .filterNotNull()
      .map { Snap(it.isProfileSetup, ProfileProjection.of(it)) }
      .distinctUntilChanged()
      .debounce(DEBOUNCE_MS)
      .collect { maybePush(uid, gen, startKey, pullOk, it) }
  }

  private fun maybePush(uid: String, gen: Long, startKey: String, pullOk: Boolean, snap: Snap) {
    if (!isLive(gen) || signedInUid() != uid || denied) return
    val key = snap.p.key()
    val synced = prefs.getString(syncedKey(uid), null)
    // Phủ định của cổng pushAllowed: dòng hạt giống, hoặc máy chưa từng đồng bộ mà lần kéo đầu chưa xong, không đẩy.
    if (!snap.setUp || (synced == null && !pullOk)) return
    if (key == synced || key == pendingKey || (!pullOk && key == startKey)) return
    pendingKey = key
    setSynced(uid, "") // dấu bẩn bền NGAY khi xếp hàng; chỉ ACKED mới đổi thành key (B-03)
    Log.i(TAG, "pushed")
    try {
      push(uid, snap.p.toRemoteMap()) { outcome -> onPushed(uid, gen, key, outcome) }
    } catch (e: Exception) {
      Log.w(TAG, "push failed: ${e.javaClass.simpleName}")
    }
  }

  private fun onPushed(uid: String, gen: Long, key: String, outcome: PushOutcome) {
    when (outcome) {
      // ACKED muộn (kể cả sau stop()) vẫn ghi cho uid của lần đẩy đó; key cũ khi đã có key mới hơn thì giữ "".
      PushOutcome.ACKED -> if (pendingKey == key) {
        setSynced(uid, key)
        Log.i(TAG, "push acked")
      }
      // synced giữ "" nên phiên sau thử lại một lần.
      PushOutcome.DENIED -> {
        if (gen == generation) denied = true
        Log.w(TAG, "push denied")
      }
      PushOutcome.FAILED -> Log.w(TAG, "push failed")
    }
  }

  private fun setSynced(uid: String, value: String?) {
    prefs.edit { if (value == null) remove(syncedKey(uid)) else putString(syncedKey(uid), value) }
  }

  companion object {
    private const val TAG = "ProfileSync"

    /** Prefs riêng, chỉ chứa key SHA-256 hex của bản chiếu (hoặc "" = bẩn), không PII. */
    const val PREFS = "inlove_profile_sync"

    // ponytail: hằng cố định, thành tham số khi cần chỉnh theo máy.
    const val ROW_WAIT_MS = 10_000L
    const val FETCH_TIMEOUT_MS = 8_000L
    const val DEBOUNCE_MS = 1_500L

    fun syncedKey(uid: String) = "synced_$uid"
  }
}
