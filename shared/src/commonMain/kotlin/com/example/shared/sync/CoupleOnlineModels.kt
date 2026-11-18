package com.example.shared.sync

import com.example.shared.dossier.GivenGiftRecord
import com.example.shared.dossier.PartnerDossier
import com.example.shared.dossier.SecretHintNote
import com.example.shared.dossier.completenessPercent
import kotlinx.serialization.Serializable

/**
 * Pure KMP representation of the couple's online pairing and cloud-sync lifecycle.
 * Labels are concise, emoji-free, and free of internal storage terms.
 */
@Serializable
enum class OnlineSyncPhase(
    val statusLabelVi: String,
    val statusLabelEn: String,
) {
    GUEST_LOCAL(
        statusLabelVi = "Lưu riêng trên thiết bị này",
        statusLabelEn = "Saved locally on this device",
    ),
    SIGNED_IN_SOLO(
        statusLabelVi = "Đã đăng nhập · Sẵn sàng ghép đôi",
        statusLabelEn = "Signed in · Ready to pair",
    ),
    INVITE_OUTGOING(
        statusLabelVi = "Đã gửi lời mời ghép đôi",
        statusLabelEn = "Pairing invite sent",
    ),
    INVITE_INCOMING(
        statusLabelVi = "Có lời mời ghép đôi mới",
        statusLabelEn = "Incoming pairing invite",
    ),
    COUPLED_SYNCED(
        statusLabelVi = "Hai bạn đã đồng bộ trực tuyến",
        statusLabelEn = "Both devices synced",
    ),
    COUPLED_OFFLINE_QUEUE(
        statusLabelVi = "Ngoại tuyến · Tự đồng bộ khi có mạng",
        statusLabelEn = "Offline · Will sync when reconnected",
    ),
    BREAKUP_PENDING(
        statusLabelVi = "Đang chờ xác nhận hủy ghép đôi",
        statusLabelEn = "Unpair confirmation pending",
    ),
    ;

    fun statusLabel(isEnglish: Boolean): String = if (isEnglish) statusLabelEn else statusLabelVi
}

/**
 * Resolves the canonical [OnlineSyncPhase] from account, pairing, and network connectivity flags.
 */
object OnlineSyncPhaseResolver {
    fun resolve(
        isSignedIn: Boolean,
        isCoupled: Boolean,
        isBreakupPending: Boolean,
        hasOutgoingInvite: Boolean,
        hasIncomingInvite: Boolean,
        isOnline: Boolean,
    ): OnlineSyncPhase = when {
        !isSignedIn -> OnlineSyncPhase.GUEST_LOCAL
        isBreakupPending -> OnlineSyncPhase.BREAKUP_PENDING
        isCoupled -> if (isOnline) OnlineSyncPhase.COUPLED_SYNCED else OnlineSyncPhase.COUPLED_OFFLINE_QUEUE
        hasIncomingInvite -> OnlineSyncPhase.INVITE_INCOMING
        hasOutgoingInvite -> OnlineSyncPhase.INVITE_OUTGOING
        else -> OnlineSyncPhase.SIGNED_IN_SOLO
    }
}

/**
 * Pure cross-platform parser normalizing couple pairing codes from links, query parameters, or raw text.
 */
object CoupleCodeParser {
    private val codeParamRegex = Regex("""(?:^|[?&])code=([a-zA-Z0-9_-]+)""", RegexOption.IGNORE_CASE)
    private val pairPathRegex = Regex("""/pair/([a-zA-Z0-9_-]+)""", RegexOption.IGNORE_CASE)

    fun extractCode(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""

        codeParamRegex.find(trimmed)?.let { match ->
            return normalizeAsciiCode(match.groupValues[1])
        }
        pairPathRegex.find(trimmed)?.let { match ->
            return normalizeAsciiCode(match.groupValues[1])
        }
        if (trimmed.contains("://")) {
            return ""
        }
        return normalizeAsciiCode(trimmed)
    }

    private fun normalizeAsciiCode(candidate: String): String = buildString(candidate.length) {
        for (ch in candidate) {
            when (ch) {
                in 'a'..'z' -> append((ch.code - 32).toChar())
                in 'A'..'Z', in '0'..'9', '-', '_' -> append(ch)
            }
        }
    }
}

/**
 * Deterministic offline-to-cloud merge policy for [PartnerDossier].
 * Scalar preferences follow whichever snapshot has the newer [PartnerDossier.updatedAtEpochMs],
 * while [PartnerDossier.secretSurpriseNotes] and [PartnerDossier.giftHistory] are union-merged by id.
 */
object DossierMergePolicy {
    fun merge(local: PartnerDossier, remote: PartnerDossier): PartnerDossier {
        val remoteWins = remote.updatedAtEpochMs > local.updatedAtEpochMs ||
            (remote.updatedAtEpochMs == local.updatedAtEpochMs &&
                local.completenessPercent() == 0 &&
                remote.completenessPercent() > 0)
        val primary = if (remoteWins) remote else local
        val secondary = if (remoteWins) local else remote

        val mergedNotes = mergeSecretNotes(primary.secretSurpriseNotes, secondary.secretSurpriseNotes)
        val mergedHistory = mergeGiftHistory(primary.giftHistory, secondary.giftHistory)

        return primary.copy(
            scopeId = primary.scopeId.ifBlank { secondary.scopeId },
            secretSurpriseNotes = mergedNotes,
            giftHistory = mergedHistory,
            updatedAtEpochMs = maxOf(local.updatedAtEpochMs, remote.updatedAtEpochMs),
        )
    }

    private fun mergeSecretNotes(
        primary: List<SecretHintNote>,
        secondary: List<SecretHintNote>,
    ): List<SecretHintNote> {
        val byKey = linkedMapOf<String, SecretHintNote>()
        for (item in secondary) {
            byKey[noteMergeKey(item)] = item
        }
        for (item in primary) {
            byKey[noteMergeKey(item)] = item
        }
        return byKey.values.sortedWith(
            compareByDescending<SecretHintNote> { it.createdAtEpochMs }.thenBy { it.id },
        )
    }

    private fun mergeGiftHistory(
        primary: List<GivenGiftRecord>,
        secondary: List<GivenGiftRecord>,
    ): List<GivenGiftRecord> {
        val byKey = linkedMapOf<String, GivenGiftRecord>()
        for (item in secondary) {
            byKey[giftMergeKey(item)] = item
        }
        for (item in primary) {
            byKey[giftMergeKey(item)] = item
        }
        return byKey.values.sortedWith(
            compareByDescending<GivenGiftRecord> { it.givenDateIso }.thenBy { it.id },
        )
    }

    private fun noteMergeKey(note: SecretHintNote): String =
        note.id.ifBlank { "${note.createdAtEpochMs}:${note.note}" }

    private fun giftMergeKey(record: GivenGiftRecord): String =
        record.id.ifBlank { "${record.givenDateIso}:${record.title}" }
}

@Serializable
data class CoupleOnlineSyncSnapshot(
    val phase: OnlineSyncPhase = OnlineSyncPhase.GUEST_LOCAL,
    val selfUid: String = "",
    val selfCoupleCode: String = "",
    val partnerUid: String = "",
    val partnerName: String = "",
    val relationshipId: String = "",
    val loveStartDateIso: String = "",
    val hasPendingChanges: Boolean = false,
    val lastSyncedAtEpochMs: Long = 0L,
)

/**
 * Pure KMP contract for reading online pairing state and merging private partner dossiers.
 */
interface CoupleOnlineSyncGateway {
    suspend fun currentSyncSnapshot(): CoupleOnlineSyncSnapshot
    suspend fun syncPrivateDossier(localDossier: PartnerDossier): PartnerDossier
}
