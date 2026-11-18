package com.example.data.dossier

import android.content.Context
import com.example.data.db.AccountDataVault
import com.example.data.db.InLoveDao
import com.example.shared.dossier.GivenGiftRecord
import com.example.shared.dossier.PartnerDossier
import com.example.shared.dossier.PartnerDossierJson
import com.example.shared.dossier.PartnerInterestCatalog
import com.example.shared.dossier.SecretHintNote
import com.example.shared.dossier.effectiveLikesCsv
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * Atomic per-UID/per-scope JSON persistence for [PartnerDossier] under `filesDir/dossier/`.
 *
 * Avoids touching pinned Room v12/v13/v14 entity schemas while synchronizing summary preference fields
 * (`likesCsv`, `budgetMaxVnd`, `occasionRegion`) back to `InLoveDao.updatePartnerPreferences`.
 */
class LocalJsonPartnerDossierRepository(
    private val dossierDir: File,
    private val dao: InLoveDao? = null,
    private val activeScopeProvider: () -> String = { AccountDataVault.GUEST },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : PartnerDossierRepository {

    constructor(
        context: Context,
        dao: InLoveDao? = null,
        clock: () -> Long = { System.currentTimeMillis() },
    ) : this(
        dossierDir = File(context.applicationContext.filesDir, DOSSIER_DIR_NAME),
        dao = dao,
        activeScopeProvider = { readActiveScope(context.applicationContext) },
        clock = clock,
    )

    private val mutex = Mutex()
    private val _dossier = MutableStateFlow(PartnerDossier())
    override val dossier: StateFlow<PartnerDossier> = _dossier.asStateFlow()

    @Volatile
    private var loadedScopeId: String? = null

    override suspend fun load(scopeId: String): PartnerDossier = mutex.withLock {
        withContext(ioDispatcher) {
            loadLocked(scopeId)
        }
    }

    override suspend fun loadCurrentScope(): PartnerDossier =
        load(activeScopeProvider())

    override suspend fun save(dossier: PartnerDossier): PartnerDossier = mutex.withLock {
        withContext(ioDispatcher) {
            saveLocked(dossier)
        }
    }

    override suspend fun updateDossier(transform: (PartnerDossier) -> PartnerDossier): PartnerDossier =
        mutex.withLock {
            withContext(ioDispatcher) {
                val current = ensureCurrentScopeLoadedLocked()
                val updated = transform(current).copy(
                    scopeId = current.scopeId,
                    updatedAtEpochMs = clock(),
                )
                saveLocked(updated)
            }
        }

    override suspend fun addSecretNote(note: SecretHintNote): PartnerDossier =
        updateDossier { current ->
            val sanitizedNote = note.copy(
                createdAtEpochMs = if (note.createdAtEpochMs > 0L) note.createdAtEpochMs else clock(),
            )
            current.copy(
                secretSurpriseNotes = listOf(sanitizedNote) +
                    current.secretSurpriseNotes.filterNot { it.id == sanitizedNote.id },
            )
        }

    override suspend fun markSecretNotePurchased(noteId: String, purchased: Boolean): PartnerDossier =
        updateDossier { current ->
            current.copy(
                secretSurpriseNotes = current.secretSurpriseNotes.map { item ->
                    if (item.id == noteId) item.copy(isPurchased = purchased) else item
                },
            )
        }

    override suspend fun removeSecretNote(noteId: String): PartnerDossier =
        updateDossier { current ->
            current.copy(
                secretSurpriseNotes = current.secretSurpriseNotes.filterNot { it.id == noteId },
            )
        }

    override suspend fun addGivenGift(record: GivenGiftRecord): PartnerDossier =
        updateDossier { current ->
            current.copy(
                giftHistory = listOf(record) + current.giftHistory.filterNot { it.id == record.id },
            )
        }

    override suspend fun syncWithRoomIfEmpty(scopeId: String): PartnerDossier = mutex.withLock {
        withContext(ioDispatcher) {
            loadLocked(scopeId)
        }
    }

    override suspend fun syncPreferencesFromRoom(
        likesCsv: String,
        budgetMaxVnd: Long,
        occasionRegion: String,
    ): PartnerDossier = updateDossier { current ->
        val normalizedCategories = PartnerInterestCatalog.normalizeInterestsCsv(likesCsv)
        current.copy(
            interestCategories = normalizedCategories.ifEmpty { current.interestCategories },
            freeformLikes = likesCsv.trim(),
            budgetMaxVnd = budgetMaxVnd.coerceAtLeast(0L),
            occasionRegion = occasionRegion.trim().uppercase().ifBlank { "VN" },
        )
    }

    override suspend fun clearScope(scopeId: String) = mutex.withLock {
        withContext(ioDispatcher) {
            val resolved = normalizeScope(scopeId)
            val target = fileForScope(resolved)
            target.delete()
            File(dossierDir, "${target.name}.tmp").delete()
            File(dossierDir, "${target.name}.bak").delete()
            if (loadedScopeId == resolved || _dossier.value.scopeId == resolved) {
                loadedScopeId = resolved
                _dossier.value = PartnerDossier(scopeId = resolved)
            }
        }
    }

    private suspend fun ensureCurrentScopeLoadedLocked(): PartnerDossier {
        val activeScope = normalizeScope(activeScopeProvider())
        if (loadedScopeId == activeScope) {
            return _dossier.value
        }
        return loadLocked(activeScope)
    }

    private suspend fun loadLocked(scopeId: String): PartnerDossier {
        val resolved = normalizeScope(scopeId)
        val file = fileForScope(resolved)
        if (file.isFile) {
            val decoded = runCatching {
                PartnerDossierJson.decodeOrNull(file.readText(Charsets.UTF_8))
            }.getOrNull()
            if (decoded != null) {
                val normalized = decoded.copy(scopeId = resolved)
                loadedScopeId = resolved
                _dossier.value = normalized
                return normalized
            }
        }

        val seeded = seedFromRoomProfileLocked(resolved)
        if (seeded != null) {
            writeAtomicFile(file, PartnerDossierJson.encode(seeded))
            loadedScopeId = resolved
            _dossier.value = seeded
            return seeded
        }

        val empty = PartnerDossier(scopeId = resolved)
        loadedScopeId = resolved
        _dossier.value = empty
        return empty
    }

    private suspend fun saveLocked(dossier: PartnerDossier): PartnerDossier {
        val resolved = normalizeScope(dossier.scopeId.ifBlank { activeScopeProvider() })
        val normalized = dossier.copy(
            scopeId = resolved,
            budgetMaxVnd = dossier.budgetMaxVnd.coerceAtLeast(0L),
            occasionRegion = dossier.occasionRegion.trim().uppercase().ifBlank { "VN" },
        )
        writeAtomicFile(fileForScope(resolved), PartnerDossierJson.encode(normalized))
        loadedScopeId = resolved
        _dossier.value = normalized
        syncToRoomSafely(normalized)
        return normalized
    }

    private suspend fun seedFromRoomProfileLocked(scopeId: String): PartnerDossier? {
        val profile = runCatching { dao?.getCoupleProfileSync() }.getOrNull() ?: return null
        val nickname = profile.partner2Name
            .trim()
            .takeIf { it.isNotEmpty() && !it.equals("Người ấy", ignoreCase = true) && !it.equals("Partner", ignoreCase = true) }
            .orEmpty()
        val hasAnyData = nickname.isNotEmpty() ||
            profile.likesCsv.isNotBlank() ||
            profile.budgetMaxVnd > 0L ||
            profile.occasionRegion.isNotBlank()
        if (!hasAnyData) return null

        return PartnerDossier(
            scopeId = scopeId,
            nickname = nickname,
            interestCategories = PartnerInterestCatalog.normalizeInterestsCsv(profile.likesCsv),
            freeformLikes = profile.likesCsv.trim(),
            budgetMaxVnd = profile.budgetMaxVnd.coerceAtLeast(0L),
            occasionRegion = profile.occasionRegion.trim().uppercase().ifBlank { "VN" },
            updatedAtEpochMs = clock(),
        )
    }

    private suspend fun syncToRoomSafely(dossier: PartnerDossier) {
        val safeDao = dao ?: return
        runCatching {
            safeDao.updatePartnerPreferences(
                likes = dossier.effectiveLikesCsv(),
                budget = dossier.budgetMaxVnd.coerceAtLeast(0L),
                region = dossier.occasionRegion.ifBlank { "VN" },
            )
        }
    }

    private fun writeAtomicFile(target: File, jsonText: String) {
        if (!dossierDir.exists()) {
            dossierDir.mkdirs()
        }
        val tmp = File(dossierDir, "${target.name}.tmp")
        val bak = File(dossierDir, "${target.name}.bak")
        tmp.delete()
        FileOutputStream(tmp).use { fos ->
            fos.write(jsonText.toByteArray(Charsets.UTF_8))
            fos.fd.sync()
        }
        if (tmp.renameTo(target)) return

        // Windows / some Android filesystems disallow renameTo over an existing file.
        bak.delete()
        if (target.exists() && !target.renameTo(bak)) {
            target.delete()
        }
        if (tmp.renameTo(target)) {
            bak.delete()
        } else {
            if (bak.exists()) bak.renameTo(target)
            error("Unable to atomically persist PartnerDossier to ${target.name}")
        }
    }

    private fun fileForScope(scopeId: String): File =
        File(dossierDir, "${sha256Prefix(normalizeScope(scopeId))}.json")

    private fun normalizeScope(raw: String): String =
        raw.trim().ifBlank { AccountDataVault.GUEST }

    private fun sha256Prefix(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(32)
    }

    companion object {
        private const val DOSSIER_DIR_NAME = "dossier"
        private const val SCOPE_PREFS = "inlove_account_scope"
        private const val KEY_SCOPE = "scope"

        private fun readActiveScope(context: Context): String =
            context.getSharedPreferences(SCOPE_PREFS, Context.MODE_PRIVATE)
                .getString(KEY_SCOPE, null)
                ?.takeIf { it.isNotBlank() }
                ?: AccountDataVault.GUEST
    }
}
