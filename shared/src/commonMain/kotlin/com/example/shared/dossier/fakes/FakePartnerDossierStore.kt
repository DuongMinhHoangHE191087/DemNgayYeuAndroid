package com.example.shared.dossier.fakes

import com.example.shared.dossier.GivenGiftRecord
import com.example.shared.dossier.PartnerDossier
import com.example.shared.dossier.PartnerDossierStore
import com.example.shared.dossier.SecretHintNote
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory fake implementation of [PartnerDossierStore] for KMP tests and previews.
 */
class FakePartnerDossierStore(
    initial: PartnerDossier = PartnerDossier(),
) : PartnerDossierStore {

    private val byScope = mutableMapOf(initial.scopeId to initial)
    private val _dossier = MutableStateFlow(initial)
    override val dossier: StateFlow<PartnerDossier> = _dossier.asStateFlow()

    override suspend fun load(scopeId: String): PartnerDossier {
        val loaded = byScope[scopeId] ?: PartnerDossier(scopeId = scopeId)
        byScope[scopeId] = loaded
        _dossier.value = loaded
        return loaded
    }

    override suspend fun save(dossier: PartnerDossier): PartnerDossier {
        byScope[dossier.scopeId] = dossier
        _dossier.value = dossier
        return dossier
    }

    override suspend fun addSecretNote(note: SecretHintNote): PartnerDossier {
        val current = _dossier.value
        val updated = current.copy(
            secretSurpriseNotes = listOf(note) + current.secretSurpriseNotes.filterNot { it.id == note.id },
        )
        return save(updated)
    }

    override suspend fun markSecretNotePurchased(noteId: String, purchased: Boolean): PartnerDossier {
        val current = _dossier.value
        val updated = current.copy(
            secretSurpriseNotes = current.secretSurpriseNotes.map { item ->
                if (item.id == noteId) item.copy(isPurchased = purchased) else item
            },
        )
        return save(updated)
    }

    override suspend fun removeSecretNote(noteId: String): PartnerDossier {
        val current = _dossier.value
        val updated = current.copy(
            secretSurpriseNotes = current.secretSurpriseNotes.filterNot { it.id == noteId },
        )
        return save(updated)
    }

    override suspend fun addGivenGift(record: GivenGiftRecord): PartnerDossier {
        val current = _dossier.value
        val updated = current.copy(
            giftHistory = listOf(record) + current.giftHistory.filterNot { it.id == record.id },
        )
        return save(updated)
    }

    override suspend fun clearScope(scopeId: String) {
        byScope.remove(scopeId)
        if (_dossier.value.scopeId == scopeId) {
            _dossier.value = PartnerDossier(scopeId = scopeId)
        }
    }
}
