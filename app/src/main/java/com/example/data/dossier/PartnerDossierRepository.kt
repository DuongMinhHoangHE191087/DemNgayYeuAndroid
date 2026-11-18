package com.example.data.dossier

import com.example.shared.dossier.PartnerDossier
import com.example.shared.dossier.PartnerDossierStore

/**
 * Android repository contract extending `:shared`'s [PartnerDossierStore] with scope-aware loading
 * and non-destructive syncing of summary preference fields to Room `CoupleProfileEntity`.
 */
interface PartnerDossierRepository : PartnerDossierStore {
    suspend fun loadCurrentScope(): PartnerDossier

    suspend fun updateDossier(transform: (PartnerDossier) -> PartnerDossier): PartnerDossier

    suspend fun syncWithRoomIfEmpty(scopeId: String): PartnerDossier

    suspend fun syncPreferencesFromRoom(
        likesCsv: String,
        budgetMaxVnd: Long,
        occasionRegion: String,
    ): PartnerDossier
}
