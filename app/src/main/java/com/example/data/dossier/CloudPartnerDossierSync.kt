package com.example.data.dossier

import android.util.Log
import com.example.shared.dossier.PartnerDossier
import com.example.shared.dossier.PartnerDossierJson
import com.example.shared.dossier.completenessPercent
import com.example.shared.sync.DossierMergePolicy
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

data class PartnerDossierRemoteDocument(
    val payloadJson: String,
    val schemaVersion: Int,
    val updatedAtEpochMs: Long,
)

sealed interface RemoteDossierFetchResult {
    data object Absent : RemoteDossierFetchResult
    data class Found(val document: PartnerDossierRemoteDocument) : RemoteDossierFetchResult
    data object Unavailable : RemoteDossierFetchResult
}

/**
 * Contract for owner-only private dossier backup at `/users/{uid}/privateDossier/current`.
 */
interface PartnerDossierCloudGateway {
    suspend fun fetchPrivateDossier(uid: String): RemoteDossierFetchResult
    suspend fun pushPrivateDossier(uid: String, document: PartnerDossierRemoteDocument): Boolean
    suspend fun deletePrivateDossier(uid: String): Boolean
}

/**
 * Firestore implementation storing `/users/{uid}/privateDossier/current`.
 * Never throws out to callers or logs PII/notes; returns [RemoteDossierFetchResult.Unavailable] when offline/unconfigured.
 */
class FirestorePartnerDossierCloudGateway(
    private val firestoreProvider: () -> FirebaseFirestore?,
) : PartnerDossierCloudGateway {

    override suspend fun fetchPrivateDossier(uid: String): RemoteDossierFetchResult {
        if (uid.isBlank()) return RemoteDossierFetchResult.Unavailable
        val fs = firestoreProvider() ?: return RemoteDossierFetchResult.Unavailable
        return try {
            withTimeout(FIRESTORE_TIMEOUT_MS) {
                val snap = fs.collection(COLLECTION_USERS)
                    .document(uid)
                    .collection(SUBCOLLECTION_PRIVATE_DOSSIER)
                    .document(DOC_CURRENT)
                    .get(Source.SERVER)
                    .await()
                if (!snap.exists()) return@withTimeout RemoteDossierFetchResult.Absent
                val payloadJson = snap.getString(FIELD_PAYLOAD_JSON) ?: return@withTimeout RemoteDossierFetchResult.Unavailable
                val schemaVersion = (snap.getLong(FIELD_SCHEMA_VERSION) ?: 0L).toInt()
                if (schemaVersion != SCHEMA_VERSION) return@withTimeout RemoteDossierFetchResult.Unavailable
                val updatedAtEpochMs = snap.getLong(FIELD_UPDATED_AT_EPOCH_MS) ?: 0L
                RemoteDossierFetchResult.Found(
                    PartnerDossierRemoteDocument(
                        payloadJson = payloadJson,
                        schemaVersion = schemaVersion,
                        updatedAtEpochMs = updatedAtEpochMs,
                    )
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "fetch failed: ${e.javaClass.simpleName}")
            RemoteDossierFetchResult.Unavailable
        }
    }

    override suspend fun pushPrivateDossier(uid: String, document: PartnerDossierRemoteDocument): Boolean {
        if (uid.isBlank() || document.schemaVersion != SCHEMA_VERSION) return false
        if (document.payloadJson.toByteArray(Charsets.UTF_8).size > MAX_PAYLOAD_BYTES) {
            Log.w(TAG, "push skipped: payload exceeds $MAX_PAYLOAD_BYTES bytes")
            return false
        }
        val fs = firestoreProvider() ?: return false
        return try {
            withTimeout(FIRESTORE_TIMEOUT_MS) {
                fs.collection(COLLECTION_USERS)
                    .document(uid)
                    .collection(SUBCOLLECTION_PRIVATE_DOSSIER)
                    .document(DOC_CURRENT)
                    .set(
                        mapOf(
                            FIELD_PAYLOAD_JSON to document.payloadJson,
                            FIELD_SCHEMA_VERSION to document.schemaVersion,
                            FIELD_UPDATED_AT_EPOCH_MS to document.updatedAtEpochMs,
                            FIELD_UPDATED_AT to FieldValue.serverTimestamp(),
                        )
                    )
                    .await()
                true
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "push failed: ${e.javaClass.simpleName}")
            false
        }
    }

    override suspend fun deletePrivateDossier(uid: String): Boolean {
        if (uid.isBlank()) return false
        val fs = firestoreProvider() ?: return false
        return try {
            withTimeout(FIRESTORE_TIMEOUT_MS) {
                fs.collection(COLLECTION_USERS)
                    .document(uid)
                    .collection(SUBCOLLECTION_PRIVATE_DOSSIER)
                    .document(DOC_CURRENT)
                    .delete()
                    .await()
                true
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "delete failed: ${e.javaClass.simpleName}")
            false
        }
    }

    companion object {
        private const val TAG = "CloudPartnerDossier"
        private const val COLLECTION_USERS = "users"
        private const val SUBCOLLECTION_PRIVATE_DOSSIER = "privateDossier"
        private const val DOC_CURRENT = "current"
        private const val FIELD_PAYLOAD_JSON = "payloadJson"
        private const val FIELD_SCHEMA_VERSION = "schemaVersion"
        private const val FIELD_UPDATED_AT_EPOCH_MS = "updatedAtEpochMs"
        private const val FIELD_UPDATED_AT = "updatedAt"
        private const val FIRESTORE_TIMEOUT_MS = 5_000L
        const val SCHEMA_VERSION = 1
        const val MAX_PAYLOAD_BYTES = 32_768
    }
}

/**
 * Coordinates owner-only offline-to-cloud synchronization of [PartnerDossier]:
 *  - Pulls `/users/{uid}/privateDossier/current` and merges deterministically via `:shared`'s [DossierMergePolicy].
 *  - Debounces local dossier pushes when signed-in & online, queuing dirty state cleanly offline/guest.
 */
class CloudPartnerDossierSync(
    private val localStore: PartnerDossierRepository,
    private val gateway: PartnerDossierCloudGateway,
    private val signedInUidProvider: () -> String?,
    private val isOnlineProvider: () -> Boolean,
    private val scope: CoroutineScope,
    private val debounceMs: Long = DEFAULT_DEBOUNCE_MS,
) {
    private val syncMutex = Mutex()
    private var debounceJob: Job? = null

    @Volatile
    private var pendingPush: Boolean = false

    fun hasPendingPush(): Boolean = pendingPush

    fun schedulePush(dossier: PartnerDossier) {
        val uid = signedInUidProvider()?.trim().orEmpty()
        if (uid.isEmpty()) return

        if (!isOnlineProvider()) {
            pendingPush = true
            return
        }

        pendingPush = true
        debounceJob?.cancel()
        debounceJob = scope.launch {
            if (debounceMs > 0L) delay(debounceMs)
            syncMutex.withLock {
                val liveUid = signedInUidProvider()?.trim().orEmpty()
                if (liveUid != uid || !isOnlineProvider()) return@withLock
                pushLocked(liveUid, dossier)
            }
        }
    }

    suspend fun syncNow(): Boolean {
        val uid = signedInUidProvider()?.trim().orEmpty()
        if (uid.isEmpty()) return false
        if (!isOnlineProvider()) {
            pendingPush = true
            return false
        }

        return syncMutex.withLock {
            val liveUid = signedInUidProvider()?.trim().orEmpty()
            if (liveUid.isEmpty() || !isOnlineProvider()) return@withLock false

            val local = localStore.loadCurrentScope()
            when (val fetchResult = gateway.fetchPrivateDossier(liveUid)) {
                RemoteDossierFetchResult.Unavailable -> false
                RemoteDossierFetchResult.Absent -> {
                    if (hasMeaningfulContent(local) || pendingPush) {
                        pushLocked(liveUid, local)
                    } else {
                        pendingPush = false
                        true
                    }
                }
                is RemoteDossierFetchResult.Found -> mergeAndPersistLocked(liveUid, local, fetchResult.document)
            }
        }
    }

    fun stop() {
        debounceJob?.cancel()
        debounceJob = null
        pendingPush = false
    }

    private suspend fun mergeAndPersistLocked(
        uid: String,
        local: PartnerDossier,
        remoteDoc: PartnerDossierRemoteDocument,
    ): Boolean {
        val remoteDecoded = PartnerDossierJson.decodeOrNull(remoteDoc.payloadJson) ?: return false
        val remoteNormalized = remoteDecoded.copy(
            scopeId = local.scopeId,
            updatedAtEpochMs = maxOf(remoteDecoded.updatedAtEpochMs, remoteDoc.updatedAtEpochMs),
        )
        val merged = DossierMergePolicy.merge(local, remoteNormalized)

        if (merged != local) {
            localStore.save(merged)
        }

        val needsRemoteUpdate = pendingPush ||
            merged.updatedAtEpochMs > remoteNormalized.updatedAtEpochMs ||
            merged.copy(updatedAtEpochMs = 0L) != remoteNormalized.copy(updatedAtEpochMs = 0L)

        return if (needsRemoteUpdate) {
            pushLocked(uid, merged)
        } else {
            pendingPush = false
            true
        }
    }

    private suspend fun pushLocked(uid: String, dossier: PartnerDossier): Boolean {
        val portable = dossier.copy(scopeId = uid)
        val encoded = PartnerDossierJson.encode(portable)
        if (encoded.toByteArray(Charsets.UTF_8).size > FirestorePartnerDossierCloudGateway.MAX_PAYLOAD_BYTES) {
            pendingPush = false
            return false
        }
        val doc = PartnerDossierRemoteDocument(
            payloadJson = encoded,
            schemaVersion = FirestorePartnerDossierCloudGateway.SCHEMA_VERSION,
            updatedAtEpochMs = portable.updatedAtEpochMs,
        )
        val ok = gateway.pushPrivateDossier(uid, doc)
        pendingPush = !ok
        return ok
    }

    private fun hasMeaningfulContent(dossier: PartnerDossier): Boolean =
        dossier.completenessPercent() > 0 ||
            dossier.secretSurpriseNotes.isNotEmpty() ||
            dossier.giftHistory.isNotEmpty() ||
            dossier.updatedAtEpochMs > 0L

    companion object {
        const val DEFAULT_DEBOUNCE_MS = 800L
    }
}
