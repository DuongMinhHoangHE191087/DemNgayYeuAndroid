package com.example.data.repository

import android.util.Log
import com.example.data.db.InLoveDao
import com.example.data.model.ChecklistItemEntity
import com.example.data.model.GiftIdeaEntity
import com.example.data.model.LoveBadgeEntity
import com.example.data.model.MilestoneEntity
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class CloudPresetFetcher(private val dao: InLoveDao) {

  private val _presetPhotos = MutableStateFlow<List<String>>(emptyList())
  val presetPhotos: StateFlow<List<String>> = _presetPhotos.asStateFlow()

  private val _presetAvatars = MutableStateFlow<List<String>>(emptyList())
  val presetAvatars: StateFlow<List<String>> = _presetAvatars.asStateFlow()

  private val _presetWallpapers = MutableStateFlow<List<String>>(emptyList())
  val presetWallpapers: StateFlow<List<String>> = _presetWallpapers.asStateFlow()

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
}
