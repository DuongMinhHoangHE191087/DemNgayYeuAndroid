package com.example.shared.sync.fakes

import com.example.shared.dossier.PartnerDossier
import com.example.shared.sync.CoupleOnlineSyncGateway
import com.example.shared.sync.CoupleOnlineSyncSnapshot
import com.example.shared.sync.DossierMergePolicy

/**
 * In-memory fake implementation of [CoupleOnlineSyncGateway] for common KMP tests.
 */
class FakeCoupleOnlineSyncGateway(
    var snapshot: CoupleOnlineSyncSnapshot = CoupleOnlineSyncSnapshot(),
    var remoteDossier: PartnerDossier? = null,
) : CoupleOnlineSyncGateway {

    override suspend fun currentSyncSnapshot(): CoupleOnlineSyncSnapshot = snapshot

    override suspend fun syncPrivateDossier(localDossier: PartnerDossier): PartnerDossier {
        val remote = remoteDossier
        val merged = if (remote == null) {
            localDossier
        } else {
            DossierMergePolicy.merge(local = localDossier, remote = remote)
        }
        remoteDossier = merged
        return merged
    }
}
