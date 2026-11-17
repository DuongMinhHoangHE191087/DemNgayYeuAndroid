package com.example.data.repository

import com.example.data.db.InLoveDao
import com.example.data.model.AnniversaryDateEntity
import com.example.data.model.ChecklistItemEntity
import com.example.data.model.LoveBadgeEntity
import com.example.data.model.MilestoneEntity
import com.example.data.model.ReminderCadenceEntity
import kotlinx.coroutines.flow.first

class OfflineSeeder(
  private val dao: InLoveDao,
  private val fetcher: CloudPresetFetcher,
  private val today: () -> java.time.LocalDate = { java.time.LocalDate.now() },
) {

  suspend fun initializeDefaultDataIfEmpty(language: com.example.ui.util.AppLanguage = com.example.ui.util.AppLanguage.VI) {
    // 1. Neutral system reminder cadence preferences
    val currentCadences = dao.getAllReminderCadences().first()
    if (currentCadences.isEmpty()) {
      dao.insertReminderCadences(
        listOf(
          ReminderCadenceEntity(key = "7_days", label = "Trước 7 ngày", isEnabled = true),
          ReminderCadenceEntity(key = "3_days", label = "Trước 3 ngày", isEnabled = true),
          ReminderCadenceEntity(key = "1_day", label = "Trước 1 ngày", isEnabled = true),
          ReminderCadenceEntity(key = "exact_day", label = "09:00 Ngày lễ", isEnabled = true)
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
      fetcher.syncAllCloudPresets()
      // If offline on cold start, populate safe seed data so the app is immediately usable
      seedOfflineDataIfStillEmpty(language)
    } else {
      fetcher.fetchPresetAssetsFromFirestore()
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
    com.example.data.seed.HolidayDates.refreshExisting(dao, today())
  }

  /**
   * Builds the holiday rows for the current language: Vietnamese holidays for VI, Western for EN. Fixed-date
   * holidays repeat every year (isAnnual = true). Lunar and rule-based holidays are one-off rows dated to their
   * next verified occurrence (isAnnual = false); HolidayDates.refreshExisting moves them on once they pass. A
   * lunar holiday with no verified year ahead is left out rather than given a guessed date.
   */
  private fun buildHolidayAnniversaries(language: com.example.ui.util.AppLanguage): List<AnniversaryDateEntity> {
    val today = today()
    val formatter = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")
    val now = System.currentTimeMillis()

    fun toEntity(title: String, date: java.time.LocalDate, notificationEnabled: Boolean, isAnnual: Boolean = true): AnniversaryDateEntity {
      val daysRemaining = java.time.temporal.ChronoUnit.DAYS.between(today, date).toInt()
      return AnniversaryDateEntity(
        title = title,
        dateText = date.format(formatter),
        type = "CUSTOM",
        isAnnual = isAnnual,
        notificationEnabled = notificationEnabled,
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
        toEntity("${h.emoji} ${h.titleVi}", nextFixedOccurrence(h.month, h.day), h.isCoupleRelevant)
      }
      val lunar = com.example.data.seed.VietnameseHolidays.lunarHolidays.mapNotNull { h ->
        com.example.data.seed.HolidayDates.nextLunar(h, today)
          ?.let { toEntity("${h.emoji} ${h.titleVi}", it, h.isCoupleRelevant, isAnnual = false) }
      }
      fixed + lunar
    } else {
      val fixed = com.example.data.seed.WesternHolidays.fixedHolidays.map { h ->
        toEntity("${h.emoji} ${h.titleEn}", nextFixedOccurrence(h.month, h.day), h.isCoupleRelevant)
      }
      val ruleBased = com.example.data.seed.WesternHolidays.ruleBasedHolidays.map { h ->
        toEntity("${h.emoji} ${h.titleEn}", com.example.data.seed.HolidayDates.nextRuleBased(h, today), h.isCoupleRelevant, isAnnual = false)
      }
      fixed + ruleBased
    }
  }
}
