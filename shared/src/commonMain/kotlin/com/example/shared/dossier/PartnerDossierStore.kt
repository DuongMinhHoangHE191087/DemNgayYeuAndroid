package com.example.shared.dossier

import kotlinx.coroutines.flow.StateFlow

/**
 * KMP boundary for persisting and observing the local-first Partner Sweetheart Dossier.
 */
interface PartnerDossierStore {
    val dossier: StateFlow<PartnerDossier>

    suspend fun load(scopeId: String): PartnerDossier

    suspend fun save(dossier: PartnerDossier): PartnerDossier

    suspend fun addSecretNote(note: SecretHintNote): PartnerDossier

    suspend fun markSecretNotePurchased(noteId: String, purchased: Boolean): PartnerDossier

    suspend fun removeSecretNote(noteId: String): PartnerDossier

    suspend fun addGivenGift(record: GivenGiftRecord): PartnerDossier

    suspend fun clearScope(scopeId: String)
}
