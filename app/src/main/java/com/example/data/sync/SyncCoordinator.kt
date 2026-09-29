package com.example.data.sync

import android.util.Log
import com.example.data.db.InLoveDao
import com.example.data.model.AnniversaryDateEntity
import com.example.data.model.OnlineInviteEntity
import com.example.data.model.OnlineRelationshipEntity
import com.example.data.model.OnlineStatus
import com.example.data.model.RelationshipStatus
import com.example.data.model.SharedMemoryEntity
import com.google.firebase.firestore.Filter
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

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
  private val scope: CoroutineScope
) {
  private var identityRegistrations: List<ListenerRegistration> = emptyList()
  private var contentRegistrations: List<ListenerRegistration> = emptyList()
  private var activeRelationshipId: String? = null
  private var onRelationshipChanged: suspend () -> Unit = {}

  fun start(uid: String, onRelationshipChanged: suspend () -> Unit = {}) {
    stop()
    this.onRelationshipChanged = onRelationshipChanged
    val fs = firestore ?: return

    // A single Filter.or query, not two separate whereEqualTo("user1"/"user2") listeners:
    // two independent listeners racing on different fields would each see the OTHER's rows
    // as an empty result and could tear down the content tier the other one just started.
    val relationshipsListener = fs.collection("relationships")
      .where(Filter.or(Filter.equalTo("user1", uid), Filter.equalTo("user2", uid)))
      .addSnapshotListener { snapshot, _ -> handleRelationshipSnapshot(snapshot, uid) }
    val invitesListener = fs.collection("invites")
      .whereEqualTo("targetUid", uid)
      .addSnapshotListener { snapshot, _ -> handleInviteSnapshot(snapshot) }

    identityRegistrations = listOf(relationshipsListener, invitesListener)
  }

  fun stop() {
    identityRegistrations.forEach { it.remove() }
    identityRegistrations = emptyList()
    stopContentListeners()
    onRelationshipChanged = {}
  }

  private fun stopContentListeners() {
    contentRegistrations.forEach { it.remove() }
    contentRegistrations = emptyList()
    activeRelationshipId = null
  }

  private fun handleRelationshipSnapshot(snapshot: com.google.firebase.firestore.QuerySnapshot?, uid: String) {
    val fs = firestore ?: return
    // One query now returns every relationship doc involving me, so "the" active one is
    // simply the ACTIVE doc among these results (at most one — Task 11's accept flow always
    // issues a fresh id per pairing and never reuses a terminated one).
    val activeDoc = snapshot?.documents?.firstOrNull { it.getString("status") == RelationshipStatus.ACTIVE }
    scope.launch {
      snapshot?.documents?.forEach { doc ->
        val rel = OnlineRelationshipEntity(
          relationshipId = doc.id,
          user1 = doc.getString("user1") ?: return@forEach,
          user2 = doc.getString("user2") ?: return@forEach,
          startDate = doc.getLong("startDate") ?: 0L,
          startDateText = doc.getString("startDateText") ?: "",
          status = doc.getString("status") ?: RelationshipStatus.TERMINATED,
          breakupRequestedBy = doc.getString("breakupRequestedBy"),
          breakupRequestedAt = doc.getLong("breakupRequestedAt"),
          createdAt = doc.getLong("createdAt") ?: 0L,
          terminatedAt = doc.getLong("terminatedAt"),
          updatedAt = System.currentTimeMillis(),
          pendingSync = false
        )
        dao.insertOnlineRelationship(rel)
      }

      // Keep MY OWN OnlineUserEntity row in sync with what the identity tier just learned —
      // this is the piece that makes a remote pairing/breakup event reach the local
      // read-cache OnlineCoupleRepository.refreshState() actually reads.
      val me = dao.getOnlineUserByUidSync(uid)
      if (me != null) {
        val updatedMe = when {
          activeDoc != null -> {
            val partnerUid = if (activeDoc.getString("user1") == uid) activeDoc.getString("user2") else activeDoc.getString("user1")
            me.copy(status = OnlineStatus.COUPLED, partnerId = partnerUid, relationshipId = activeDoc.id)
          }
          me.relationshipId != null -> me.copy(status = OnlineStatus.SINGLE, partnerId = null, relationshipId = null)
          else -> null
        }
        if (updatedMe != null && updatedMe != me) {
          dao.updateOnlineUser(updatedMe)
          onRelationshipChanged()
        }
      }

      val activeId = activeDoc?.id
      if (activeId != activeRelationshipId) {
        stopContentListeners()
        if (activeId != null) startContentListeners(fs, activeId)
      }
    }
  }

  private fun handleInviteSnapshot(snapshot: com.google.firebase.firestore.QuerySnapshot?) {
    scope.launch {
      snapshot?.documents?.forEach { doc ->
        val invite = OnlineInviteEntity(
          inviteId = doc.id,
          senderUid = doc.getString("senderUid") ?: return@forEach,
          senderName = doc.getString("senderName") ?: "Vô danh",
          senderAvatar = doc.getString("senderAvatar") ?: "",
          senderCoupleCode = doc.getString("senderCoupleCode") ?: "",
          senderBirthDate = doc.getString("senderBirthDate") ?: "",
          senderAge = (doc.getLong("senderAge") ?: 0L).toInt(),
          senderZodiac = doc.getString("senderZodiac") ?: "",
          senderBio = doc.getString("senderBio") ?: "",
          targetCoupleCode = doc.getString("targetCoupleCode") ?: "",
          targetUid = doc.getString("targetUid"),
          proposedStartDate = doc.getLong("proposedStartDate") ?: 0L,
          proposedStartDateText = doc.getString("proposedStartDateText") ?: "",
          loveNote = doc.getString("loveNote") ?: "",
          status = doc.getString("status") ?: "PENDING",
          createdAt = doc.getLong("createdAt") ?: 0L,
          updatedAt = System.currentTimeMillis(),
          pendingSync = false
        )
        dao.insertOnlineInvite(invite)
      }
    }
  }

  private fun startContentListeners(fs: FirebaseFirestore, relationshipId: String) {
    activeRelationshipId = relationshipId
    val memoriesListener = fs.collection(MemorySyncAdapter.collectionPath(relationshipId))
      .addSnapshotListener { snapshot, _ ->
        scope.launch {
          snapshot?.documents?.forEach { doc ->
            MemorySyncAdapter.fromFirestoreDoc(doc)?.let { applyRemoteMemory(it) }
          }
        }
      }
    val anniversariesListener = fs.collection(AnniversarySyncAdapter.collectionPath(relationshipId))
      .addSnapshotListener { snapshot, _ ->
        scope.launch {
          snapshot?.documents?.forEach { doc ->
            AnniversarySyncAdapter.fromFirestoreDoc(doc)?.let { applyRemoteAnniversary(it) }
          }
        }
      }
    contentRegistrations = listOf(memoriesListener, anniversariesListener)
  }

  /** Last-write-wins merge: a local pending edit only loses to a STRICTLY newer remote update. */
  suspend fun applyRemoteMemory(remote: SharedMemoryEntity) {
    val local = dao.getSharedMemoryBySyncId(remote.syncId)
    if (local != null && local.pendingSync && local.updatedAt >= remote.updatedAt) {
      Log.d("SyncCoordinator", "keeping local pending memory ${remote.syncId}, remote is not newer")
      return
    }
    if (local != null) {
      dao.updateSharedMemory(remote.copy(id = local.id, pendingSync = false))
    } else {
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
    } else {
      dao.insertAnniversaryDate(remote.copy(pendingSync = false))
    }
  }
}
