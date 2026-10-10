package com.example.data.repository

import android.util.Log
import com.example.data.db.InLoveDao
import com.example.data.model.AnniversaryDateEntity
import com.example.data.model.ChecklistItemEntity
import com.example.data.model.CoupleProfileEntity
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
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID

class InLoveRepository(private val dao: InLoveDao, private val appContext: android.content.Context) {

  val milestones: Flow<List<MilestoneEntity>> = dao.getAllMilestones()
  val giftIdeas: Flow<List<GiftIdeaEntity>> = dao.getAllGiftIdeas()
  val checklistItems: Flow<List<ChecklistItemEntity>> = dao.getAllChecklistItems()
  val customReminders: Flow<List<CustomReminderEntity>> = dao.getAllCustomReminders()
  val reminderCadences: Flow<List<ReminderCadenceEntity>> = dao.getAllReminderCadences()
  val coupleProfile: Flow<CoupleProfileEntity?> = dao.getCoupleProfile()
  val sharedMemories: Flow<List<SharedMemoryEntity>> = dao.getAllSharedMemories()
  val loveBadges: Flow<List<LoveBadgeEntity>> = dao.getAllLoveBadges()
  val anniversaryDates: Flow<List<AnniversaryDateEntity>> = dao.getAllAnniversaryDates()
  val giftReminders: Flow<List<GiftReminderEntity>> = dao.getAllGiftReminders()

  private val _presetPhotos = MutableStateFlow<List<String>>(emptyList())
  val presetPhotos: StateFlow<List<String>> = _presetPhotos.asStateFlow()

  private val _presetAvatars = MutableStateFlow<List<String>>(emptyList())
  val presetAvatars: StateFlow<List<String>> = _presetAvatars.asStateFlow()

  private val _presetWallpapers = MutableStateFlow<List<String>>(emptyList())
  val presetWallpapers: StateFlow<List<String>> = _presetWallpapers.asStateFlow()

  suspend fun initializeDefaultDataIfEmpty(language: com.example.ui.util.AppLanguage = com.example.ui.util.AppLanguage.VI) {
    // 1. Neutral system reminder cadence preferences
    val currentCadences = dao.getAllReminderCadences().first()
    if (currentCadences.isEmpty()) {
      dao.insertReminderCadences(
        listOf(
          ReminderCadenceEntity(key = "7_days", label = "Trước 7 ngày", isEnabled = true),
          ReminderCadenceEntity(key = "3_days", label = "Trước 3 ngày", isEnabled = true),
          ReminderCadenceEntity(key = "1_day", label = "Trước 1 ngày", isEnabled = true),
          ReminderCadenceEntity(key = "exact_day", label = "00:00 Ngày lễ", isEnabled = true)
        )
      )
    }

    // 2. Dynamic Cloud Preset Sync (Anti-Decompilation: Data lives in Firestore, zero hardcoded stories in APK)
    val hasMilestones = dao.getAllMilestones().first().isNotEmpty()
    val hasGifts = dao.getAllGiftIdeas().first().isNotEmpty()
    val hasBadges = dao.getAllLoveBadges().first().isNotEmpty()

    // The gift catalog repairs itself on every start, so it runs before the cloud/offline branch below.
    ensureSeedGiftCatalog(language)

    if (!hasMilestones || !hasGifts || !hasBadges) {
      syncAllCloudPresets()
      // If offline on cold start, populate safe seed data so the app is immediately usable
      seedOfflineDataIfStillEmpty(language)
    } else {
      fetchPresetAssetsFromFirestore()
    }
  }

  /**
   * Cài đủ 20 ý tưởng quà mỗi lần khởi động và không xoá dòng nào. Dòng cũ không có remoteId
   * (REPLACE từng gộp chúng thành một dòng) được nhận lại theo ảnh hoặc tiêu đề để giữ id và trạng thái yêu thích.
   */
  suspend fun ensureSeedGiftCatalog(language: com.example.ui.util.AppLanguage) {
    val rows = dao.getAllGiftIdeas().first()
    val takenRemoteIds = rows.map { it.remoteId }.toSet()
    com.example.data.seed.GiftIdeasSeed.all.forEach { seed ->
      val entity = seed.toEntity(language)
      val legacy = rows.firstOrNull {
        it.remoteId.isBlank() && com.example.domain.content.GiftCatalog.seedRemoteIdFor(it) == seed.remoteId
      }
      if (legacy != null && seed.remoteId !in takenRemoteIds) {
        dao.updateGiftIdea(entity.copy(id = legacy.id, isFavorited = legacy.isFavorited))
      } else {
        dao.upsertGiftIdeaByRemoteId(entity)
      }
    }
  }

  /**
   * Fetch milestone presets dynamically from Cloud Firestore.
   */
  suspend fun fetchMilestonePresetsFromFirestore(): Result<List<MilestoneEntity>> = withContext(Dispatchers.IO) {
    try {
      val firestore = FirebaseFirestore.getInstance()
      val snapshot = firestore.collection("milestone_presets").get().await()
      if (!snapshot.isEmpty) {
        val milestones = snapshot.documents.mapNotNull { doc ->
          val title = doc.getString("title") ?: return@mapNotNull null
          val subtitle = doc.getString("subtitle") ?: ""
          val categoryTag = doc.getString("categoryTag") ?: "Cột Mốc"
          val secondaryTag = doc.getString("secondaryTag") ?: ""
          val daysRemaining = doc.getLong("daysRemaining")?.toInt() ?: 0
          val progressPercent = doc.getDouble("progressPercent")?.toFloat()
          val isImportant = doc.getBoolean("isImportant") ?: false
          val notificationEnabled = doc.getBoolean("notificationEnabled") ?: true
          val imageUrl = doc.getString("imageUrl") ?: ""
          MilestoneEntity(
            remoteId = doc.id,
            title = title,
            dateText = doc.getString("dateText") ?: "",
            subtitle = subtitle,
            categoryTag = categoryTag,
            secondaryTag = secondaryTag,
            imageUrl = imageUrl,
            daysRemaining = daysRemaining,
            isPast = false,
            progressPercent = progressPercent,
            isImportant = isImportant,
            notificationEnabled = notificationEnabled
          )
        }
        milestones.forEach { dao.upsertMilestoneByRemoteId(it) }
        return@withContext Result.success(milestones)
      }
      Result.success(emptyList())
    } catch (e: Exception) {
      Log.w("InLoveRepository", "Firestore milestone presets error or offline: ${e.message}")
      Result.failure(e)
    }
  }

  /**
   * Fetch badge definitions dynamically from Cloud Firestore.
   */
  suspend fun fetchBadgeDefinitionsFromFirestore(): Result<List<LoveBadgeEntity>> = withContext(Dispatchers.IO) {
    try {
      val firestore = FirebaseFirestore.getInstance()
      val snapshot = firestore.collection("badge_definitions").get().await()
      if (!snapshot.isEmpty) {
        val existingBadges = dao.getAllLoveBadges().first().associateBy { it.id }
        val badges = snapshot.documents.mapNotNull { doc ->
          val id = doc.getString("id") ?: doc.id
          val targetDays = doc.getLong("targetDays")?.toInt() ?: 100
          val titleVi = doc.getString("titleVi") ?: ""
          val titleEn = doc.getString("titleEn") ?: ""
          val descVi = doc.getString("descVi") ?: ""
          val descEn = doc.getString("descEn") ?: ""
          val tier = doc.getString("tier") ?: "BRONZE"
          val iconType = doc.getString("iconType") ?: "sprout_heart"
          val rewardQuoteVi = doc.getString("rewardQuoteVi") ?: ""
          val rewardQuoteEn = doc.getString("rewardQuoteEn") ?: ""

          val local = existingBadges[id]
          LoveBadgeEntity(
            id = id,
            targetDays = targetDays,
            titleVi = titleVi,
            titleEn = titleEn,
            descVi = descVi,
            descEn = descEn,
            tier = tier,
            iconType = iconType,
            rewardQuoteVi = rewardQuoteVi,
            rewardQuoteEn = rewardQuoteEn,
            isClaimed = local?.isClaimed ?: false,
            claimedTimestamp = local?.claimedTimestamp,
            customNote = local?.customNote ?: ""
          )
        }
        dao.insertLoveBadges(badges)
        return@withContext Result.success(badges)
      }
      Result.success(emptyList())
    } catch (e: Exception) {
      Log.w("InLoveRepository", "Firestore badge definitions error or offline: ${e.message}")
      Result.failure(e)
    }
  }

  /**
   * Fetch checklist templates dynamically from Cloud Firestore.
   */
  suspend fun fetchChecklistTemplatesFromFirestore(): Result<List<ChecklistItemEntity>> = withContext(Dispatchers.IO) {
    try {
      val firestore = FirebaseFirestore.getInstance()
      val snapshot = firestore.collection("checklist_templates").get().await()
      if (!snapshot.isEmpty) {
        val checklists = snapshot.documents.mapIndexedNotNull { index, doc ->
          val text = doc.getString("text") ?: return@mapIndexedNotNull null
          val iconName = doc.getString("iconName") ?: "check"
          ChecklistItemEntity(
            id = (index + 1).toLong(),
            text = text,
            iconName = iconName,
            isCompleted = false
          )
        }
        dao.insertChecklistItems(checklists)
        return@withContext Result.success(checklists)
      }
      Result.success(emptyList())
    } catch (e: Exception) {
      Log.w("InLoveRepository", "Firestore checklist templates error or offline: ${e.message}")
      Result.failure(e)
    }
  }

  /**
   * Fetch preset photo and avatar assets dynamically from Cloud Firestore.
   */
  suspend fun fetchPresetAssetsFromFirestore(): Result<Pair<List<String>, List<String>>> = withContext(Dispatchers.IO) {
    try {
      val result = kotlinx.coroutines.withTimeoutOrNull(2000L) {
        val firestore = FirebaseFirestore.getInstance()
        val photosDoc = firestore.collection("preset_assets").document("photos").get().await()
        val avatarsDoc = firestore.collection("preset_assets").document("avatars").get().await()
        val wallpapersDoc = firestore.collection("preset_assets").document("wallpapers").get().await()

        val photos = (photosDoc.get("urls") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
        val avatars = (avatarsDoc.get("urls") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
        val wallpapers = (wallpapersDoc.get("urls") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()

        if (photos.isNotEmpty()) _presetPhotos.value = photos
        if (avatars.isNotEmpty()) _presetAvatars.value = avatars
        if (wallpapers.isNotEmpty()) _presetWallpapers.value = wallpapers

        photos to avatars
      }
      if (result != null) Result.success(result) else Result.failure(Exception("Preset assets fetch timeout"))
    } catch (e: Exception) {
      Log.w("InLoveRepository", "Firestore preset assets notice: ${e.message}")
      Result.failure(e)
    }
  }

  /**
   * Synchronize all cloud presets into local Room cache.
   */
  suspend fun syncAllCloudPresets(): Boolean = withContext(Dispatchers.IO) {
    try {
      kotlinx.coroutines.withTimeoutOrNull(2000L) {
        fetchDynamicGiftIdeasFromFirestore()
        fetchMilestonePresetsFromFirestore()
        fetchBadgeDefinitionsFromFirestore()
        fetchChecklistTemplatesFromFirestore()
        fetchPresetAssetsFromFirestore()
      }
      true
    } catch (e: Exception) {
      Log.w("InLoveRepository", "syncAllCloudPresets error: ${e.message}")
      false
    }
  }

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

  suspend fun addChecklistItem(text: String, icon: String = "check") {
    dao.insertChecklistItem(ChecklistItemEntity(text = text, iconName = icon, isCompleted = false))
  }

  suspend fun toggleGiftFavorite(item: GiftIdeaEntity) {
    dao.updateGiftIdea(item.copy(isFavorited = !item.isFavorited))
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
      photoUri = photoUri,
      note = note,
      location = location,
      isFavorite = false,
      anniversaryTitle = anniversaryTitle,
      createdAt = now,
      relationshipId = relationshipId,
      authorId = authorId,
      authorName = authorName,
      mediaType = mediaType,
      videoUri = videoUri,
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
      return
    }
    val tombstone = ensureSyncId(existing).copy(deleted = true, updatedAt = System.currentTimeMillis(), pendingSync = true)
    dao.deleteSharedMemoryWithOutbox(
      id, tombstone.updatedAt, outboxEntryFor(MemorySyncAdapter.entityType, tombstone.syncId, tombstone)
    )
    SyncWorker.enqueueImmediate(appContext)
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
      notificationEnabled = true,
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

  suspend fun getAnniversaryDatesList(): List<AnniversaryDateEntity> = dao.getAnniversaryDatesList()
  suspend fun getMilestonesList(): List<MilestoneEntity> = dao.getMilestonesList()
  suspend fun getCoupleProfileSync(): CoupleProfileEntity? = dao.getCoupleProfileSync()
  suspend fun getCustomRemindersList(): List<CustomReminderEntity> = dao.getCustomRemindersList()
  suspend fun getGiftRemindersList(): List<GiftReminderEntity> = dao.getGiftRemindersList()

  /**
   * Fetch dynamic gift ideas from Firebase Firestore to allow updating recommendations without app releases.
   */
  suspend fun fetchDynamicGiftIdeasFromFirestore(): Result<List<GiftIdeaEntity>> = withContext(Dispatchers.IO) {
    try {
      val firestore = FirebaseFirestore.getInstance()
      val snapshot = firestore.collection("gift_ideas").get().await()
      if (!snapshot.isEmpty) {
        val ideas = snapshot.documents.mapNotNull { doc ->
          val title = doc.getString("title") ?: return@mapNotNull null
          val category = doc.getString("category") ?: "Quà lãng mạn"
          val badgeText = doc.getString("badgeText") ?: "Gợi ý chọn lọc"
          val tag = doc.getString("tag") ?: "Ý nghĩa"
          val desc = doc.getString("description") ?: ""
          val img = doc.getString("imageUrl") ?: ""
          val detail = doc.getString("detailsSnippet") ?: ""
          val action = doc.getString("actionText") ?: ""
          val isAi = doc.getBoolean("isAiGenerated") ?: false
          val targetInterests = doc.getString("targetInterests") ?: ""
          val occasion = doc.getString("suggestedOccasion") ?: ""
          val price = doc.getString("priceRange") ?: ""
          GiftIdeaEntity(
            remoteId = doc.id,
            title = title,
            category = category,
            badgeText = badgeText,
            tag = tag,
            description = desc,
            imageUrl = img,
            isFavorited = false,
            detailsSnippet = detail,
            actionText = action,
            isAiGenerated = isAi,
            targetInterests = targetInterests,
            suggestedOccasion = occasion,
            priceRange = price
          )
        }
        ideas.forEach { dao.upsertGiftIdeaByRemoteId(it) }
        return@withContext Result.success(ideas)
      }
      Result.success(emptyList())
    } catch (e: Exception) {
      Log.w("InLoveRepository", "Firestore gift ideas error or offline: ${e.message}")
      Result.failure(e)
    }
  }

  /**
   * Builds template-based gift suggestions from the couple's mutual interests and occasion (no AI model involved).
   * ponytail: name kept for existing callers; rows still carry isAiGenerated=true so the personalized chip filters them.
   */
  suspend fun generateAiGiftSuggestions(
    partnerName: String,
    mutualInterests: Set<String>,
    occasion: String
  ): Result<List<GiftIdeaEntity>> = withContext(Dispatchers.IO) {
    try {
      val generated = mutableListOf<GiftIdeaEntity>()
      val cleanPartner = partnerName.ifBlank { "Người thương" }

      if (mutualInterests.contains("coffee")) {
        generated.add(
          GiftIdeaEntity(
            title = "Set Bình Giữ Nhiệt Khắc Tên & Cà Phê Đặc Sản Cho $cleanPartner",
            category = "Trải nghiệm & Thưởng thức",
            badgeText = "Hợp Sở Thích ☕",
            tag = "Cà phê & Gắn kết",
            description = "Dành riêng cho hai bạn yêu thích nhâm nhi cà phê sáng cùng nhau. Bình khắc ngày kỷ niệm giúp giữ trọn hương vị ấm áp.",
            imageUrl = "https://images.unsplash.com/photo-1514432324607-a09d9b4aefdd?q=80&w=800&auto=format&fit=crop",
            detailsSnippet = "Tặng kèm 2 gói cà phê Arabica Cầu Đất nguyên chất",
            actionText = "Xem mẫu khắc tên đôi",
            isAiGenerated = true,
            targetInterests = "coffee",
            remoteId = "tpl_coffee_${occasion}_$cleanPartner",
            suggestedOccasion = occasion
          )
        )
      }

      if (mutualInterests.contains("travel")) {
        generated.add(
          GiftIdeaEntity(
            title = "Chuyến Dã Ngoại Glamping Hoàng Hôn Cùng $cleanPartner",
            category = "Địa điểm hẹn hò",
            badgeText = "Trải Nghiệm Lãng Mạn ✈️",
            tag = "Du lịch đôi",
            description = "Trải nghiệm cắm trại cao cấp giữa thiên nhiên thoáng đãng, nướng BBQ và ngắm bầu trời đêm lãng mạn chỉ có 2 người.",
            imageUrl = "https://images.unsplash.com/photo-1506744038136-46273834b3fb?q=80&w=800&auto=format&fit=crop",
            detailsSnippet = "Lều Mông Cổ cao cấp, tiệc nướng BBQ thơm lừng",
            actionText = "Lên lịch trình hẹn hò",
            isAiGenerated = true,
            targetInterests = "travel",
            remoteId = "tpl_travel_${occasion}_$cleanPartner",
            suggestedOccasion = occasion
          )
        )
      }

      if (mutualInterests.contains("cinema") || mutualInterests.contains("music")) {
        generated.add(
          GiftIdeaEntity(
            title = "Rạp Phim Mini Tại Gia & Danh Sách Tình Ca Dành Riêng $cleanPartner",
            category = "Công nghệ & Cảm xúc",
            badgeText = "Ý Tưởng Ấm Cúng 🎬",
            tag = "Chill tại nhà",
            description = "Biến phòng ngủ thành rạp chiếu phim riêng tư lãng mạn. Cùng nhau xem lại những bộ phim tình cảm hai bạn yêu thích.",
            imageUrl = "https://images.unsplash.com/photo-1517604931442-7e0c8ed2963c?q=80&w=800&auto=format&fit=crop",
            detailsSnippet = "Kèm bỏng ngô vị phô mai & nến thơm tinh dầu hoa oải hương",
            actionText = "Xem danh sách phim gợi ý",
            isAiGenerated = true,
            targetInterests = "cinema,music",
            remoteId = "tpl_cinema_${occasion}_$cleanPartner",
            suggestedOccasion = occasion
          )
        )
      }

      // Universal romantic personalized gift based on occasion
      val occasionText = occasion.ifBlank { "Dịp kỷ niệm đặc biệt" }
      generated.add(
        GiftIdeaEntity(
          title = "Cuốn Sách '100 Điều Tuyệt Vời Nhất Về $cleanPartner'",
          category = "Kỷ vật Handmade",
          badgeText = "Dành Riêng Cho $cleanPartner ✨",
          tag = occasionText,
          description = "Món quà tinh thần chạm tới đáy tim được cá nhân hóa từng trang viết tay những kỷ niệm ngọt ngào nhất của hai bạn.",
          imageUrl = "https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c?q=80&w=800&auto=format&fit=crop",
          detailsSnippet = "Tặng kèm ảnh chụp Instax polaroid và hộp nơ nhung",
          actionText = "Bắt đầu soạn thảo lời yêu",
          isAiGenerated = true,
          targetInterests = mutualInterests.joinToString(","),
          remoteId = "tpl_book_${occasion}_$cleanPartner",
          suggestedOccasion = occasion
        )
      )

      val stored = generated.map { item -> dao.upsertGiftIdeaByRemoteId(item); dao.getGiftIdeaByRemoteId(item.remoteId) ?: item }
      Result.success(stored)
    } catch (e: Exception) {
      Log.e("InLoveRepository", "Error generating template gift suggestions:${e.message}", e)
      Result.failure(e)
    }
  }

  suspend fun seedOfflineDataIfStillEmpty(language: com.example.ui.util.AppLanguage = com.example.ui.util.AppLanguage.VI) {
    if (dao.getAllMilestones().first().isEmpty()) {
      dao.insertMilestones(
        listOf(
          MilestoneEntity(
            id = 1,
            title = "100 Ngày Bên Nhau",
            dateText = "Cột mốc đáng nhớ",
            subtitle = "Tròn 100 ngày đong đầy yêu thương",
            categoryTag = "Cột Mốc",
            secondaryTag = "100 Days",
            daysRemaining = 100,
            progressPercent = 0.3f,
            imageUrl = "",
            isImportant = true,
            notificationEnabled = true
          ),
          MilestoneEntity(
            id = 2,
            title = "1 Năm Yêu Nhau",
            dateText = "Kỷ niệm 1 năm",
            subtitle = "365 ngày cùng nhau vượt qua mọi khoảnh khắc",
            categoryTag = "Kỷ Niệm",
            secondaryTag = "1 Year",
            daysRemaining = 365,
            progressPercent = 0.1f,
            imageUrl = "",
            isImportant = true,
            notificationEnabled = true
          ),
          MilestoneEntity(
            id = 3,
            title = "Lễ Tình Nhân Valentine",
            dateText = "14 Tháng 2",
            subtitle = "Ngày ngọt ngào dành riêng cho hai ta",
            categoryTag = "Ngày Lễ",
            secondaryTag = "Valentine",
            daysRemaining = 14,
            progressPercent = 0.8f,
            imageUrl = "",
            isImportant = false,
            notificationEnabled = true
          )
        )
      )
    }

    if (dao.getAllLoveBadges().first().isEmpty()) {
      dao.insertLoveBadges(
        listOf(
          LoveBadgeEntity(
            id = "badge_seed_1",
            targetDays = 7,
            titleVi = "Mầm Tình Yêu",
            titleEn = "Love Sprout",
            descVi = "Cột mốc 7 ngày đầu tiên bên nhau tràn ngập bỡ ngỡ ngọt ngào.",
            descEn = "First 7 days together filled with sweet wonders.",
            tier = "BRONZE",
            iconType = "sprout_heart",
            rewardQuoteVi = "Mỗi hành trình vạn dặm đều bắt đầu từ một cái nắm tay.",
            rewardQuoteEn = "Every journey begins with holding hands.",
            isClaimed = true,
            claimedTimestamp = System.currentTimeMillis()
          ),
          LoveBadgeEntity(
            id = "badge_seed_2",
            targetDays = 30,
            titleVi = "Gắn Kết Ngọt Ngào",
            titleEn = "Sweet Connection",
            descVi = "1 tháng bên nhau chia sẻ những thói quen và câu chuyện nhỏ.",
            descEn = "1 month together sharing little daily stories.",
            tier = "SILVER",
            iconType = "star_heart",
            rewardQuoteVi = "Tình yêu là khi có ai đó cùng ta đi qua những ngày bình dị.",
            rewardQuoteEn = "Love is having someone walk through simple days together.",
            isClaimed = false
          ),
          LoveBadgeEntity(
            id = "badge_seed_3",
            targetDays = 100,
            titleVi = "Vàng Son Đượm Nồng",
            titleEn = "Golden Romance",
            descVi = "100 ngày kỷ niệm ngọt ngào và bền chặt.",
            descEn = "100 days of sweet and steady love.",
            tier = "GOLD",
            iconType = "crown_heart",
            rewardQuoteVi = "Trăm năm là cõi người ta, trăm ngày là cõi đôi ta bên nhau.",
            rewardQuoteEn = "100 days marking our forever story.",
            isClaimed = false
          )
        )
      )
    }

    if (dao.getAllChecklistItems().first().isEmpty()) {
      dao.insertChecklistItems(
        listOf(
          ChecklistItemEntity(id = 1, text = "Cùng nhau xem phim và ăn bắp rang bơ", iconName = "movie", isCompleted = false),
          ChecklistItemEntity(id = 2, text = "Cùng chụp một bức ảnh check-in dưới hoàng hôn", iconName = "camera", isCompleted = false),
          ChecklistItemEntity(id = 3, text = "Nấu một bữa tối ấm cúng cùng nhau", iconName = "restaurant", isCompleted = false)
        )
      )
    }

    // Vietnamese/Western holiday calendars: same "data file existed, nothing ever called it"
    // gap as GiftIdeasSeed above. Only seeded once (guarded like every other block here) so a
    // user who deletes a seeded holiday doesn't get it silently re-added on the next cold start.
    if (dao.getAllAnniversaryDates().first().isEmpty()) {
      dao.insertAnniversaryDates(buildHolidayAnniversaries(language))
    }
    // Rows seeded by an older build keep their old date until this refresh moves them on.
    com.example.data.seed.HolidayDates.refreshExisting(dao, java.time.LocalDate.now())
  }

  /**
   * Builds the holiday rows for the current language: Vietnamese holidays for VI, Western for EN. Fixed-date
   * holidays repeat every year (isAnnual = true). Lunar and rule-based holidays are one-off rows dated to their
   * next verified occurrence (isAnnual = false); HolidayDates.refreshExisting moves them on once they pass. A
   * lunar holiday with no verified year ahead is left out rather than given a guessed date.
   */
  private fun buildHolidayAnniversaries(language: com.example.ui.util.AppLanguage): List<AnniversaryDateEntity> {
    val today = java.time.LocalDate.now()
    val formatter = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")
    val now = System.currentTimeMillis()

    fun toEntity(title: String, date: java.time.LocalDate, isAnnual: Boolean = true): AnniversaryDateEntity {
      val daysRemaining = java.time.temporal.ChronoUnit.DAYS.between(today, date).toInt()
      return AnniversaryDateEntity(
        title = title,
        dateText = date.format(formatter),
        type = "CUSTOM",
        isAnnual = isAnnual,
        daysRemaining = daysRemaining,
        updatedAt = now
      )
    }

    fun nextFixedOccurrence(month: Int, day: Int): java.time.LocalDate {
      var date = java.time.LocalDate.of(today.year, month, day)
      if (date.isBefore(today)) date = date.plusYears(1)
      return date
    }

    return if (language == com.example.ui.util.AppLanguage.VI) {
      val fixed = com.example.data.seed.VietnameseHolidays.fixedHolidays.map { h ->
        toEntity("${h.emoji} ${h.titleVi}", nextFixedOccurrence(h.month, h.day))
      }
      val lunar = com.example.data.seed.VietnameseHolidays.lunarHolidays.mapNotNull { h ->
        com.example.data.seed.HolidayDates.nextLunar(h, today)
          ?.let { toEntity("${h.emoji} ${h.titleVi}", it, isAnnual = false) }
      }
      fixed + lunar
    } else {
      val fixed = com.example.data.seed.WesternHolidays.fixedHolidays.map { h ->
        toEntity("${h.emoji} ${h.titleEn}", nextFixedOccurrence(h.month, h.day))
      }
      val ruleBased = com.example.data.seed.WesternHolidays.ruleBasedHolidays.map { h ->
        toEntity("${h.emoji} ${h.titleEn}", com.example.data.seed.HolidayDates.nextRuleBased(h, today), isAnnual = false)
      }
      fixed + ruleBased
    }
  }
}
