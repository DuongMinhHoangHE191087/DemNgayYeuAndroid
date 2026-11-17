package com.example.data.repository

import com.example.data.db.InLoveDao
import com.example.data.model.AnniversaryDateEntity
import com.example.data.model.ChecklistItemEntity
import com.example.data.model.CoupleProfileEntity
import com.example.data.media.MediaPath
import com.example.data.model.CustomReminderEntity
import com.example.data.model.GiftIdeaEntity
import com.example.data.model.GiftReminderEntity
import com.example.data.model.LoveBadgeEntity
import com.example.data.model.MilestoneEntity
import com.example.data.model.ReminderCadenceEntity
import com.example.data.model.PRIVACY_PRIVATE
import com.example.data.model.SharedMemoryEntity
import com.example.data.model.SyncOutboxEntity
import com.example.data.sync.AnniversarySyncAdapter
import com.example.data.sync.MemorySyncAdapter
import com.example.data.sync.SyncWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import java.util.UUID

class InLoveRepository(private val dao: InLoveDao, private val appContext: android.content.Context) {

  val milestones: Flow<List<MilestoneEntity>> = dao.getAllMilestones()
  val giftIdeas: Flow<List<GiftIdeaEntity>> = dao.getAllGiftIdeas()
  val checklistItems: Flow<List<ChecklistItemEntity>> = dao.getAllChecklistItems()
  val customReminders: Flow<List<CustomReminderEntity>> = dao.getAllCustomReminders()
  val reminderCadences: Flow<List<ReminderCadenceEntity>> = dao.getAllReminderCadences()
  val coupleProfile: Flow<CoupleProfileEntity?> = dao.getCoupleProfile()
  // Unpair hides relationship-scoped rows without deleting them (see RelationshipVisibility.kt).
  val sharedMemories: Flow<List<SharedMemoryEntity>> =
    dao.getAllSharedMemories().combine(dao.visibleIds()) { rows, ids -> rows.visibleIn(ids) }
  val loveBadges: Flow<List<LoveBadgeEntity>> = dao.getAllLoveBadges()
  val anniversaryDates: Flow<List<AnniversaryDateEntity>> =
    dao.getAllAnniversaryDates().combine(dao.visibleIds()) { rows, ids -> rows.visibleIn(ids) }
  val giftReminders: Flow<List<GiftReminderEntity>> = dao.getAllGiftReminders()

  private val cloudPresets = CloudPresetFetcher(dao)
  private val offlineSeeder = OfflineSeeder(dao, cloudPresets)
  private val giftGenerator = GiftSuggestionGenerator(dao)

  val presetPhotos: StateFlow<List<String>> = cloudPresets.presetPhotos
  val presetAvatars: StateFlow<List<String>> = cloudPresets.presetAvatars
  val presetWallpapers: StateFlow<List<String>> = cloudPresets.presetWallpapers

  suspend fun initializeDefaultDataIfEmpty(language: com.example.ui.util.AppLanguage = com.example.ui.util.AppLanguage.VI) =
    offlineSeeder.initializeDefaultDataIfEmpty(language)

  suspend fun ensureSeedGiftCatalog(language: com.example.ui.util.AppLanguage) = offlineSeeder.ensureSeedGiftCatalog(language)

  suspend fun seedOfflineDataIfStillEmpty(language: com.example.ui.util.AppLanguage = com.example.ui.util.AppLanguage.VI) =
    offlineSeeder.seedOfflineDataIfStillEmpty(language)

  suspend fun fetchMilestonePresetsFromFirestore() = cloudPresets.fetchMilestonePresetsFromFirestore()
  suspend fun fetchBadgeDefinitionsFromFirestore() = cloudPresets.fetchBadgeDefinitionsFromFirestore()
  suspend fun fetchChecklistTemplatesFromFirestore() = cloudPresets.fetchChecklistTemplatesFromFirestore()
  suspend fun fetchPresetAssetsFromFirestore() = cloudPresets.fetchPresetAssetsFromFirestore()
  suspend fun syncAllCloudPresets() = cloudPresets.syncAllCloudPresets()
  suspend fun fetchDynamicGiftIdeasFromFirestore() = cloudPresets.fetchDynamicGiftIdeasFromFirestore()

  suspend fun generateAiGiftSuggestions(partnerName: String, mutualInterests: Set<String>, occasion: String, isEnglish: Boolean = false) =
    giftGenerator.generateAiGiftSuggestions(partnerName, mutualInterests, occasion, isEnglish)

  suspend fun claimLoveBadge(badgeId: String, customNote: String = "") {
    val currentBadges = dao.getAllLoveBadges().first()
    val badge = currentBadges.find { it.id == badgeId }
    if (badge != null) {
      dao.updateLoveBadge(
        badge.copy(
          isClaimed = true,
          claimedTimestamp = System.currentTimeMillis(),
          customNote = customNote.ifEmpty { badge.customNote }
        )
      )
    }
  }

  suspend fun updateLoveBadge(badge: LoveBadgeEntity) {
    dao.updateLoveBadge(badge)
  }

  suspend fun saveCoupleProfile(profile: CoupleProfileEntity) {
    dao.insertCoupleProfile(profile.copy(id = 1, updatedAt = System.currentTimeMillis()))
  }

  suspend fun savePartnerPreferences(likesCsv: String, budgetMaxVnd: Long, occasionRegion: String) {
    dao.updatePartnerPreferences(likesCsv, budgetMaxVnd.coerceAtLeast(0), occasionRegion)
  }

  suspend fun toggleChecklistItem(item: ChecklistItemEntity) {
    dao.updateChecklistItem(item.copy(isCompleted = !item.isCompleted))
  }

  suspend fun renameChecklistItem(item: ChecklistItemEntity, text: String) {
    dao.updateChecklistItem(item.copy(text = text))
  }

  suspend fun addChecklistItem(text: String, icon: String = "check") {
    dao.insertChecklistItem(ChecklistItemEntity(text = text, iconName = icon, isCompleted = false))
  }

  suspend fun toggleGiftFavorite(item: GiftIdeaEntity) {
    dao.setGiftFavorite(item.id, !item.isFavorited)
  }

  suspend fun toggleCadence(item: ReminderCadenceEntity) {
    dao.updateReminderCadence(item.copy(isEnabled = !item.isEnabled))
  }

  suspend fun addCustomReminder(
    title: String,
    dateText: String,
    note: String,
    alarmMillis: Long? = null,
    alarmFormatted: String = ""
  ): Long {
    return dao.insertCustomReminder(
      CustomReminderEntity(
        title = title,
        dateText = dateText,
        details = note.ifBlank { "Kỷ niệm ngọt ngào" },
        daysRemainingText = "Mới tạo",
        iconType = "stars",
        alarmTimeMillis = alarmMillis,
        alarmTimeFormatted = alarmFormatted
      )
    )
  }

  suspend fun deleteCustomReminder(id: Long) {
    dao.deleteCustomReminderById(id)
  }

  // ponytail: loads every custom reminder row; a by-id query is the upgrade when the list grows.
  suspend fun hasCustomReminder(id: Long): Boolean = dao.getCustomRemindersList().any { it.id == id }

  suspend fun setCustomReminderAlarm(id: Long, millis: Long?, formatted: String) {
    dao.setCustomReminderAlarm(id, millis, formatted)
  }

  suspend fun deleteMilestone(id: Long) {
    dao.deleteMilestoneById(id)
  }

  suspend fun deleteChecklistItem(id: Long) {
    dao.deleteChecklistItemById(id)
  }

  suspend fun updateMilestone(item: MilestoneEntity) {
    dao.updateMilestone(item)
  }

  suspend fun addMilestone(item: MilestoneEntity): Long {
    return dao.insertMilestone(item)
  }

  suspend fun toggleMilestoneNotification(item: MilestoneEntity) {
    dao.updateMilestone(item.copy(notificationEnabled = !item.notificationEnabled))
  }

  suspend fun addSharedMemory(
    title: String,
    dateText: String,
    photoUri: String,
    note: String = "",
    location: String = "",
    anniversaryTitle: String = "Kỷ Niệm Ngày Yêu",
    authorId: String = "",
    authorName: String = "Bạn",
    mediaType: String = "IMAGE",
    videoUri: String? = null,
    cloudinaryPublicId: String? = null,
    fileSizeFormatted: String = "",
    durationSeconds: Int = 0,
    privacyLevel: String = "COUPLE_ONLY",
    relationshipId: String? = null,
    // Ảnh/video đã tải lên dùng chính mã này làm memoryId phía máy chủ, nên syncId phải khớp với publicId.
    syncId: String = UUID.randomUUID().toString(),
  ) {
    val now = System.currentTimeMillis()
    val memory = SharedMemoryEntity(
      title = title,
      dateText = dateText,
      // Lưu đường dẫn tương đối "media/<tên>" để không vỡ khi filesDir đổi.
      photoUri = MediaPath.toStored(appContext.filesDir, photoUri).orEmpty(),
      note = note,
      location = location,
      isFavorite = false,
      anniversaryTitle = anniversaryTitle,
      createdAt = now,
      relationshipId = relationshipId,
      authorId = authorId,
      authorName = authorName,
      mediaType = mediaType,
      videoUri = MediaPath.toStored(appContext.filesDir, videoUri),
      cloudinaryPublicId = cloudinaryPublicId,
      fileSizeFormatted = fileSizeFormatted,
      durationSeconds = durationSeconds,
      privacyLevel = privacyLevel,
      syncId = syncId,
      updatedAt = now,
      pendingSync = privacyLevel != PRIVACY_PRIVATE
    )
    if (privacyLevel == PRIVACY_PRIVATE) {
      // Riêng tư: chỉ lưu ở máy này, không vào sync_outbox nên không bao giờ rời máy.
      dao.insertSharedMemory(memory)
      return
    }
    dao.insertSharedMemoryWithOutbox(memory, outboxEntryFor(MemorySyncAdapter.entityType, memory.syncId, memory))
    SyncWorker.enqueueImmediate(appContext)
  }

  suspend fun updateSharedMemory(memory: SharedMemoryEntity) {
    val previous = dao.getAllSharedMemories().first().firstOrNull { it.id == memory.id }
    // Riêng tư là một chiều: ảnh đã chia sẻ có thể đã nằm trên Cloudinary công khai, nên không cho quay lại chia sẻ.
    val privacy = if (previous?.privacyLevel == PRIVACY_PRIVATE) PRIVACY_PRIVATE else memory.privacyLevel
    val local = ensureSyncId(memory).copy(
      privacyLevel = privacy,
      updatedAt = System.currentTimeMillis(),
      pendingSync = privacy != PRIVACY_PRIVATE
    )
    when {
      privacy != PRIVACY_PRIVATE -> {
        dao.updateSharedMemoryWithOutbox(local, outboxEntryFor(MemorySyncAdapter.entityType, local.syncId, local))
        SyncWorker.enqueueImmediate(appContext)
      }
      previous != null && previous.privacyLevel != PRIVACY_PRIVATE -> {
        // Chia sẻ → riêng tư: gửi tombstone (đã bỏ nội dung) để bạn đời không còn thấy; bản đầy đủ vẫn ở máy này.
        // Máy chủ giải phóng tệp trên đám mây khi publicId bị xoá, nên máy này không còn giữ publicId nữa.
        val privateLocal = local.copy(cloudinaryPublicId = null)
        val tombstone = privateLocal.copy(privacyLevel = previous.privacyLevel, deleted = true)
        dao.updateSharedMemoryWithOutbox(privateLocal, outboxEntryFor(MemorySyncAdapter.entityType, local.syncId, tombstone))
        SyncWorker.enqueueImmediate(appContext)
      }
      else -> dao.updateSharedMemory(local) // riêng tư → riêng tư: chỉ ở máy này
    }
  }

  suspend fun deleteSharedMemory(id: Long) {
    val existing = dao.getAllSharedMemories().first().firstOrNull { it.id == id } ?: return
    if (existing.privacyLevel == PRIVACY_PRIVATE) {
      dao.softDeleteSharedMemoryById(id, System.currentTimeMillis()) // riêng tư: không có gì để xoá trên đám mây
      cleanupFiles(existing)
      return
    }
    val tombstone = ensureSyncId(existing).copy(deleted = true, updatedAt = System.currentTimeMillis(), pendingSync = true)
    dao.deleteSharedMemoryWithOutbox(
      id, tombstone.updatedAt, outboxEntryFor(MemorySyncAdapter.entityType, tombstone.syncId, tombstone)
    )
    SyncWorker.enqueueImmediate(appContext)
    cleanupFiles(existing)
  }

  suspend fun toggleMemoryFavorite(memory: SharedMemoryEntity) {
    updateSharedMemory(memory.copy(isFavorite = !memory.isFavorite))
  }

  // Anniversary Dates CRUD
  suspend fun addAnniversaryDate(
    title: String,
    dateText: String,
    type: String = "LOVE",
    description: String = "",
    isAnnual: Boolean = true,
    notificationEnabled: Boolean = true,
    reminderDaysBefore: Int = 3,
    daysRemaining: Int = 0,
    relationshipId: String? = null
  ): Long {
    val now = System.currentTimeMillis()
    val item = AnniversaryDateEntity(
      title = title,
      dateText = dateText,
      type = type,
      description = description,
      isAnnual = isAnnual,
      notificationEnabled = notificationEnabled,
      reminderDaysBefore = reminderDaysBefore,
      daysRemaining = daysRemaining,
      createdAt = now,
      relationshipId = relationshipId,
      syncId = UUID.randomUUID().toString(),
      updatedAt = now,
      pendingSync = true
    )
    val newId = dao.insertAnniversaryDateWithOutbox(
      item, outboxEntryFor(AnniversarySyncAdapter.entityType, item.syncId, item)
    )
    SyncWorker.enqueueImmediate(appContext)
    return newId
  }

  suspend fun updateAnniversaryDate(item: AnniversaryDateEntity) {
    val withSync = ensureSyncId(item).copy(updatedAt = System.currentTimeMillis(), pendingSync = true)
    dao.updateAnniversaryDateWithOutbox(
      withSync, outboxEntryFor(AnniversarySyncAdapter.entityType, withSync.syncId, withSync)
    )
    SyncWorker.enqueueImmediate(appContext)
  }

  suspend fun deleteAnniversaryDate(id: Long) {
    val existing = dao.getAllAnniversaryDates().first().firstOrNull { it.id == id } ?: return
    val tombstone = ensureSyncId(existing).copy(deleted = true, updatedAt = System.currentTimeMillis(), pendingSync = true)
    dao.deleteAnniversaryDateWithOutbox(
      id, tombstone.updatedAt, outboxEntryFor(AnniversarySyncAdapter.entityType, tombstone.syncId, tombstone)
    )
    SyncWorker.enqueueImmediate(appContext)
  }

  suspend fun toggleAnniversaryNotification(item: AnniversaryDateEntity) {
    updateAnniversaryDate(item.copy(notificationEnabled = !item.notificationEnabled))
  }

  private fun ensureSyncId(memory: SharedMemoryEntity): SharedMemoryEntity =
    if (memory.syncId.isBlank()) memory.copy(syncId = UUID.randomUUID().toString()) else memory

  private fun ensureSyncId(item: AnniversaryDateEntity): AnniversaryDateEntity =
    if (item.syncId.isBlank()) item.copy(syncId = UUID.randomUUID().toString()) else item

  private fun outboxEntryFor(entityType: String, syncId: String, memory: SharedMemoryEntity): SyncOutboxEntity {
    // Tombstone chỉ mang khoá để xoá đúng bản ghi: không đẩy nội dung, ảnh hay link media lên đám mây.
    val payload = if (memory.deleted) memory.stripForTombstone() else memory
    return SyncOutboxEntity(
      entityType = entityType,
      syncId = syncId,
      operation = if (memory.deleted) "DELETE" else "UPSERT",
      payloadJson = SyncWorker.moshiAdapterFor<SharedMemoryEntity>().toJson(payload)
    )
  }

  private fun SharedMemoryEntity.stripForTombstone(): SharedMemoryEntity = copy(
    title = "", dateText = "", photoUri = "", note = "", location = "", anniversaryTitle = "",
    authorName = "", fileSizeFormatted = "", videoUri = null, cloudinaryPublicId = null, cloudinaryUrl = null,
    isFavorite = false, durationSeconds = 0, deleted = true
  )

  private fun outboxEntryFor(entityType: String, syncId: String, item: AnniversaryDateEntity): SyncOutboxEntity =
    SyncOutboxEntity(
      entityType = entityType,
      syncId = syncId,
      operation = if (item.deleted) "DELETE" else "UPSERT",
      payloadJson = SyncWorker.moshiAdapterFor<AnniversaryDateEntity>().toJson(item)
    )

  // Gift Reminders CRUD
  suspend fun addGiftReminder(
    title: String,
    recipient: String,
    occasion: String,
    dueDateText: String,
    estimatedBudget: String,
    notes: String,
    alarmTimeMillis: Long? = null,
    alarmTimeFormatted: String = ""
  ): Long {
    return dao.insertGiftReminder(
      GiftReminderEntity(
        title = title,
        recipient = recipient,
        occasion = occasion,
        dueDateText = dueDateText,
        estimatedBudget = estimatedBudget,
        notes = notes,
        isCompleted = false,
        alarmTimeMillis = alarmTimeMillis,
        alarmTimeFormatted = alarmTimeFormatted,
        createdAt = System.currentTimeMillis()
      )
    )
  }

  suspend fun updateGiftReminder(item: GiftReminderEntity) {
    dao.updateGiftReminder(item)
  }

  suspend fun toggleGiftReminderCompleted(item: GiftReminderEntity) {
    dao.updateGiftReminder(item.copy(isCompleted = !item.isCompleted))
  }

  suspend fun deleteGiftReminder(id: Long) {
    dao.deleteGiftReminderById(id)
  }

  suspend fun getAnniversaryDatesList(): List<AnniversaryDateEntity> = dao.visibleAnniversaryDatesList()
  suspend fun getMilestonesList(): List<MilestoneEntity> = dao.getMilestonesList()
  suspend fun getCoupleProfileSync(): CoupleProfileEntity? = dao.getCoupleProfileSync()
  suspend fun getCustomRemindersList(): List<CustomReminderEntity> = dao.getCustomRemindersList()
  suspend fun getGiftRemindersList(): List<GiftReminderEntity> = dao.getGiftRemindersList()

  // Hàng đã soft-delete nên không còn trong getAllSharedMemories; file còn hàng khác dùng thì giữ lại.
  private suspend fun cleanupFiles(m: SharedMemoryEntity) = withContext(Dispatchers.IO) {
    val live = dao.getAllSharedMemories().first().flatMap { listOf(it.photoUri, it.videoUri) }
    MediaPath.deleteUnreferenced(appContext.filesDir, listOf(m.photoUri, m.videoUri), live)
  }
}
