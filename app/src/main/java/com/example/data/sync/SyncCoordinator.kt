package com.example.data.sync

import android.util.Log
import com.example.data.db.InLoveDao
import com.example.data.model.AnniversaryDateEntity
import com.example.data.model.OnlineInviteEntity
import com.example.data.model.OnlineRelationshipEntity
import com.example.data.model.OnlineStatus
import com.example.data.model.RelationshipStatus
import com.example.data.model.PRIVACY_PRIVATE
import com.example.data.model.SharedMemoryEntity
import com.google.firebase.firestore.Filter
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/** One Firestore document as plain data: (doc.id, doc.data). The tests build these without a QuerySnapshot. */
internal typealias RawDocs = List<Pair<String, Map<String, Any?>>>

/**
 * Chạy một khối trong lưới an toàn: lỗi thường chỉ được ghi log và bỏ qua (một tài liệu hỏng không làm sập app),
 * còn CancellationException luôn được ném lại để coroutine huỷ đúng cách.
 */
internal inline fun guarded(what: String, block: () -> Unit) {
  try {
    block()
  } catch (e: CancellationException) {
    throw e
  } catch (e: Exception) {
    Log.w("SyncCoordinator", "$what failed, skipped", e)
  }
}

// Đọc trường an toàn từ Map: sai kiểu hoặc thiếu đều cho null (không bao giờ ném như DocumentSnapshot.getString/getLong/getBoolean).
internal fun Map<String, Any?>.optString(key: String): String? = this[key] as? String
internal fun Map<String, Any?>.optLong(key: String): Long? = (this[key] as? Number)?.toLong()
internal fun Map<String, Any?>.optBool(key: String): Boolean? = this[key] as? Boolean

private val KNOWN_RELATIONSHIP_STATUSES = setOf(
  RelationshipStatus.ACTIVE, RelationshipStatus.PENDING_BREAKUP, RelationshipStatus.TERMINATED
)

/**
 * Tài liệu `relationships/{id}` -> entity, hoặc null (bỏ qua) khi user1/user2 thiếu hoặc sai kiểu, hoặc status sai kiểu/lạ.
 * Thiếu hẳn status vẫn là TERMINATED như trước đây; các trường tuỳ chọn sai kiểu nhận đúng giá trị mặc định của trường thiếu.
 */
internal fun relationshipFromMap(id: String, data: Map<String, Any?>, now: Long = System.currentTimeMillis()): OnlineRelationshipEntity? {
  val user1 = data.optString("user1") ?: return null
  val user2 = data.optString("user2") ?: return null
  val status = when (val raw = data["status"]) {
    null -> RelationshipStatus.TERMINATED
    is String -> raw.takeIf { it in KNOWN_RELATIONSHIP_STATUSES } ?: return null
    else -> return null
  }
  return OnlineRelationshipEntity(
    relationshipId = id,
    user1 = user1,
    user2 = user2,
    startDate = data.optLong("startDate") ?: 0L,
    startDateText = data.optString("startDateText") ?: "",
    status = status,
    breakupRequestedBy = data.optString("breakupRequestedBy"),
    breakupRequestedAt = data.optLong("breakupRequestedAt"),
    createdAt = data.optLong("createdAt") ?: 0L,
    terminatedAt = data.optLong("terminatedAt"),
    updatedAt = now,
    pendingSync = false
  )
}

/** Tài liệu `invites/{id}` -> entity, hoặc null khi thiếu/sai kiểu senderUid. `invites` do client ghi được nên không tin kiểu dữ liệu. */
internal fun inviteFromMap(id: String, data: Map<String, Any?>, now: Long = System.currentTimeMillis()): OnlineInviteEntity? {
  val senderUid = data.optString("senderUid") ?: return null
  return OnlineInviteEntity(
    inviteId = id,
    senderUid = senderUid,
    senderName = data.optString("senderName") ?: "Vô danh",
    senderAvatar = data.optString("senderAvatar") ?: "",
    senderCoupleCode = data.optString("senderCoupleCode") ?: "",
    senderBirthDate = data.optString("senderBirthDate") ?: "",
    senderAge = (data.optLong("senderAge") ?: 0L).toInt(),
    senderZodiac = data.optString("senderZodiac") ?: "",
    senderBio = data.optString("senderBio") ?: "",
    targetCoupleCode = data.optString("targetCoupleCode") ?: "",
    targetUid = data.optString("targetUid"),
    proposedStartDate = data.optLong("proposedStartDate") ?: 0L,
    proposedStartDateText = data.optString("proposedStartDateText") ?: "",
    loveNote = data.optString("loveNote") ?: "",
    status = data.optString("status") ?: "PENDING",
    createdAt = data.optLong("createdAt") ?: 0L,
    updatedAt = now,
    pendingSync = false
  )
}

/**
 * Two listener tiers, both scoped to the login lifetime (started/stopped together):
 *  - identity tier: relationships + invites involving me, always on while logged in — this
 *    is how "who is my partner" is resolved without trusting a client-writable field on
 *    someone else's user document.
 *  - content tier: memories + anniversaries of the active relationship, started the moment
 *    the identity tier yields a relationship with status == ACTIVE, stopped the moment it
 *    stops being ACTIVE (terminated) or on logout.
 *
 * `OnlineUserEntity.partnerId`/`relationshipId`/`status` remain a LOCAL read-cache (the spec's
 * "drop partnerId" intent is honored where it actually matters — no Firestore rule anywhere
 * trusts this field; see the design-doc amendment). This class is what keeps that cache
 * correct from the REMOTE side: it only ever writes to the row matching the uid passed into
 * `start()` (never another user's row — that data came from a Firestore READ of a relationship
 * doc I am legitimately a member of, not a cross-user write), and calls `onRelationshipChanged`
 * after doing so, so `OnlineCoupleRepository`'s existing `refreshState()`-based UI state picks
 * it up — without this callback, only the device that itself performed a local pairing/breakup
 * action would ever see its own UI update; the OTHER device's UI would stay stale.
 */
class SyncCoordinator(
  private val dao: InLoveDao,
  private val firestore: FirebaseFirestore?,
  private val scope: CoroutineScope,
  // Mặc định theo chính sách lưu trữ: LOCAL_ONLY thì không bao giờ mở listener nội dung.
  internal val contentSync: Boolean = com.example.domain.DataResidencyPolicy.cloudContent
) {
  private var identityRegistrations: List<ListenerRegistration> = emptyList()
  @Volatile private var contentRegistrations: List<ListenerRegistration> = emptyList()
  @Volatile private var activeRelationshipId: String? = null
  private var onRelationshipChanged: suspend () -> Unit = {}

  // Mọi snapshot (đã đổi sang dữ liệu thuần trên luồng callback) được xếp vào một hàng đợi và MỘT coroutine duy nhất xử lý
  // lần lượt, nên snapshot N luôn được áp dụng trước N+1 (trước đây mỗi snapshot một scope.launch trên IO nên có thể đảo thứ tự).
  private var inbox: Channel<suspend () -> Unit>? = null
  private var consumer: Job? = null

  // Session fence: every start()/stop() bumps [generation], and every account scope switch bumps
  // AccountDataVault.epoch. Listener callbacks and the coroutines they launch capture both and
  // drop their writes once either moved, so a late snapshot of the previous account can never
  // land in the next account's tables.
  @Volatile private var generation = 0L
  private var epochAtStart = 0L
  private fun isLive(gen: Long) = gen == generation && epochAtStart == com.example.data.db.AccountDataVault.epoch.get()

  fun start(uid: String, onRelationshipChanged: suspend () -> Unit = {}) {
    stop()
    val gen = generation
    epochAtStart = com.example.data.db.AccountDataVault.epoch.get()
    this.onRelationshipChanged = onRelationshipChanged
    val queue = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    inbox = queue
    consumer = scope.launch {
      for (job in queue) guarded("apply snapshot") { job() }
    }
    val fs = firestore ?: return

    // A single Filter.or query, not two separate whereEqualTo("user1"/"user2") listeners:
    // two independent listeners racing on different fields would each see the OTHER's rows
    // as an empty result and could tear down the content tier the other one just started.
    val relationshipsListener = fs.collection("relationships")
      .where(Filter.or(Filter.equalTo("user1", uid), Filter.equalTo("user2", uid)))
      .addSnapshotListener { snapshot, error ->
        guarded("relationships snapshot") {
          handleRelationshipDocs(uid, docsOf("relationships", snapshot, error), snapshot?.metadata?.isFromCache == true, gen)
        }
      }
    val invitesListener = fs.collection("invites")
      .whereEqualTo("targetUid", uid)
      .addSnapshotListener { snapshot, error ->
        guarded("invites snapshot") { handleInviteDocs(docsOf("invites", snapshot, error), gen) }
      }

    identityRegistrations = listOf(relationshipsListener, invitesListener)
  }

  fun stop() {
    generation++
    identityRegistrations.forEach { it.remove() }
    identityRegistrations = emptyList()
    consumer?.cancel()
    consumer = null
    inbox?.close()
    inbox = null
    stopContentListeners()
    onRelationshipChanged = {}
  }

  /** Test seam: suspends until everything queued so far has been applied by the consumer. */
  internal suspend fun awaitApplied() {
    val queue = inbox ?: return
    val done = CompletableDeferred<Unit>()
    val marker: suspend () -> Unit = { done.complete(Unit) }
    queue.send(marker)
    done.await()
  }

  private fun enqueue(gen: Long, work: suspend () -> Unit) {
    val job: suspend () -> Unit = { if (isLive(gen)) work() }
    inbox?.trySend(job)
  }

  // Lỗi hoặc snapshot null => null (không đổi gì; listener coi như đã chết). Đọc cả snapshot trong try: một snapshot hỏng bị bỏ nguyên cả cái.
  private fun docsOf(what: String, snapshot: QuerySnapshot?, error: Exception?): RawDocs? {
    if (error != null || snapshot == null) {
      Log.w("SyncCoordinator", "$what listener error, snapshot ignored", error)
      return null
    }
    return snapshot.documents.map { it.id to (it.data ?: emptyMap<String, Any?>()) }
  }

  private fun stopContentListeners() {
    contentRegistrations.forEach { it.remove() }
    contentRegistrations = emptyList()
    activeRelationshipId = null
  }

  /**
   * Seam on plain data. [docs] == null means the listener reported an error / null snapshot (listener is dead):
   * nothing changes. An EMPTY snapshot served only from the local cache means "unknown", never "no relationship":
   * it must not flip a COUPLED user to SINGLE.
   */
  internal fun handleRelationshipDocs(uid: String, docs: RawDocs?, fromCache: Boolean = false, gen: Long = generation) {
    if (!isLive(gen)) return
    if (docs == null) return
    if (fromCache && docs.isEmpty()) return
    // One query now returns every relationship doc involving me, so "the" active one is
    // simply the ACTIVE doc among these results (at most one — Task 11's accept flow always
    // issues a fresh id per pairing and never reuses a terminated one).
    val rels = docs.mapNotNull { (id, data) -> relationshipFromMap(id, data) }
    enqueue(gen) { applyRelationships(uid, gen, rels) }
  }

  private suspend fun applyRelationships(uid: String, gen: Long, rels: List<OnlineRelationshipEntity>) {
    for (rel in rels) {
      if (!isLive(gen)) return
      guarded("insert relationship ${rel.relationshipId}") { dao.insertOnlineRelationship(rel) }
    }

    // Keep MY OWN OnlineUserEntity row in sync with what the identity tier just learned —
    // this is the piece that makes a remote pairing/breakup event reach the local
    // read-cache OnlineCoupleRepository.refreshState() actually reads.
    if (!isLive(gen)) return
    val active = rels.firstOrNull { it.status == RelationshipStatus.ACTIVE }
    guarded("update my user row") {
      val me = dao.getOnlineUserByUidSync(uid)
      if (me != null) {
        val updatedMe = when {
          active != null -> {
            val partnerUid = if (active.user1 == uid) active.user2 else active.user1
            me.copy(status = OnlineStatus.COUPLED, partnerId = partnerUid, relationshipId = active.relationshipId)
          }
          me.relationshipId != null -> me.copy(status = OnlineStatus.SINGLE, partnerId = null, relationshipId = null)
          else -> null
        }
        if (updatedMe != null && updatedMe != me && isLive(gen)) {
          dao.updateOnlineUser(updatedMe)
          onRelationshipChanged()
        }
      }
    }

    val activeId = active?.relationshipId
    if (activeId != activeRelationshipId) {
      stopContentListeners()
      val fs = firestore
      if (contentSync && activeId != null && fs != null && isLive(gen)) startContentListeners(fs, activeId, gen)
    }
  }

  internal fun handleInviteDocs(docs: RawDocs?, gen: Long = generation) {
    if (!isLive(gen) || docs == null) return
    val invites = docs.mapNotNull { (id, data) -> inviteFromMap(id, data) }
    enqueue(gen) {
      for (invite in invites) {
        if (!isLive(gen)) break
        guarded("insert invite ${invite.inviteId}") { dao.insertOnlineInvite(invite) }
      }
    }
  }

  private fun startContentListeners(fs: FirebaseFirestore, relationshipId: String, gen: Long) {
    val memoriesListener = fs.collection(MemorySyncAdapter.collectionPath(relationshipId))
      .addSnapshotListener { snapshot, error ->
        guarded("memories snapshot") { handleMemoryDocs(relationshipId, docsOf("memories", snapshot, error), gen) }
      }
    val anniversariesListener = fs.collection(AnniversarySyncAdapter.collectionPath(relationshipId))
      .addSnapshotListener { snapshot, error ->
        guarded("anniversaries snapshot") { handleAnniversaryDocs(relationshipId, docsOf("anniversaries", snapshot, error), gen) }
      }
    contentRegistrations = listOf(memoriesListener, anniversariesListener)
    activeRelationshipId = relationshipId
    // stop() may have run while we were registering: drop what we just added so no listener outlives its session.
    if (!isLive(gen)) stopContentListeners()
  }

  internal fun handleMemoryDocs(relationshipId: String, docs: RawDocs?, gen: Long = generation) {
    if (!isLive(gen) || docs == null) return
    // Only accept docs that really belong to the relationship this listener serves.
    val memories = docs.mapNotNull { (_, data) -> MemorySyncAdapter.fromFirestoreMap(data)?.takeIf { it.relationshipId == relationshipId } }
    enqueue(gen) {
      for (memory in memories) {
        if (!isLive(gen)) break
        guarded("apply memory ${memory.syncId}") { applyRemoteMemory(memory) }
      }
    }
  }

  internal fun handleAnniversaryDocs(relationshipId: String, docs: RawDocs?, gen: Long = generation) {
    if (!isLive(gen) || docs == null) return
    val anniversaries = docs.mapNotNull { (_, data) -> AnniversarySyncAdapter.fromFirestoreMap(data)?.takeIf { it.relationshipId == relationshipId } }
    enqueue(gen) {
      for (anniversary in anniversaries) {
        if (!isLive(gen)) break
        guarded("apply anniversary ${anniversary.syncId}") { applyRemoteAnniversary(anniversary) }
      }
    }
  }

  /** Last-write-wins merge: a local pending edit only loses to a STRICTLY newer remote update. */
  suspend fun applyRemoteMemory(remote: SharedMemoryEntity) {
    val local = dao.getSharedMemoryBySyncId(remote.syncId)
    // Kỷ niệm riêng tư chỉ ở máy này: bản từ đám mây (kể cả tombstone do chính máy này gửi khi đổi sang riêng tư) không được ghi đè.
    if (local != null && local.privacyLevel == PRIVACY_PRIVATE) return
    if (local != null && local.pendingSync && local.updatedAt >= remote.updatedAt) {
      Log.d("SyncCoordinator", "keeping local pending memory ${remote.syncId}, remote is not newer")
      return
    }
    if (local != null) {
      // Máy nhận không bao giờ có URI hay publicId cục bộ của người kia: giữ ảnh/video đã có trên máy này.
      dao.updateSharedMemory(
        remote.copy(
          id = local.id,
          pendingSync = false,
          photoUri = remote.photoUri.ifBlank { local.photoUri },
          videoUri = remote.videoUri ?: local.videoUri,
          cloudinaryPublicId = remote.cloudinaryPublicId ?: local.cloudinaryPublicId
        )
      )
    } else if (!remote.deleted) {
      // A tombstone this device never had a local row for (e.g. deleted on the other device
      // before this one ever synced it down) needs no local row: inserting one would just be
      // dead weight, since getAllSharedMemories already filters deleted = 0.
      dao.insertSharedMemory(remote.copy(pendingSync = false))
    }
  }

  /** Last-write-wins merge: a local pending edit only loses to a STRICTLY newer remote update. */
  suspend fun applyRemoteAnniversary(remote: AnniversaryDateEntity) {
    val local = dao.getAnniversaryDateBySyncId(remote.syncId)
    if (local != null && local.pendingSync && local.updatedAt >= remote.updatedAt) {
      Log.d("SyncCoordinator", "keeping local pending anniversary ${remote.syncId}, remote is not newer")
      return
    }
    if (local != null) {
      dao.updateAnniversaryDate(remote.copy(id = local.id, pendingSync = false))
    } else if (!remote.deleted) {
      dao.insertAnniversaryDate(remote.copy(pendingSync = false))
    }
  }
}
