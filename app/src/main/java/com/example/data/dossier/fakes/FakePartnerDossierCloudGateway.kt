package com.example.data.dossier.fakes

import com.example.data.dossier.PartnerDossierCloudGateway
import com.example.data.dossier.PartnerDossierRemoteDocument
import com.example.data.dossier.RemoteDossierFetchResult
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory fake for [PartnerDossierCloudGateway] used in JVM & Robolectric unit tests.
 */
class FakePartnerDossierCloudGateway : PartnerDossierCloudGateway {
    private val docs = ConcurrentHashMap<String, PartnerDossierRemoteDocument>()

    var fetchUnavailable: Boolean = false
    var pushShouldFail: Boolean = false
    var fetchCount: Int = 0
        private set
    var pushCount: Int = 0
        private set

    fun seedRemote(uid: String, document: PartnerDossierRemoteDocument) {
        docs[uid] = document
    }

    fun currentRemote(uid: String): PartnerDossierRemoteDocument? = docs[uid]

    override suspend fun fetchPrivateDossier(uid: String): RemoteDossierFetchResult {
        fetchCount++
        if (fetchUnavailable || uid.isBlank()) return RemoteDossierFetchResult.Unavailable
        val doc = docs[uid] ?: return RemoteDossierFetchResult.Absent
        return RemoteDossierFetchResult.Found(doc)
    }

    override suspend fun pushPrivateDossier(uid: String, document: PartnerDossierRemoteDocument): Boolean {
        pushCount++
        if (pushShouldFail || uid.isBlank()) return false
        docs[uid] = document
        return true
    }

    override suspend fun deletePrivateDossier(uid: String): Boolean {
        docs.remove(uid)
        return true
    }
}
