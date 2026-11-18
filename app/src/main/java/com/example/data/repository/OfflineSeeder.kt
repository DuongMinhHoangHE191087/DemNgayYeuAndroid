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
            secondaryTag = "100 ngày",
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
            secondaryTag = "1 năm",
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
      dao.insertLoveBadges(buildSeedBadges())
    }

    if (dao.getAllChecklistItems().first().isEmpty()) {
      dao.insertChecklistItems(buildSeedChecklist(language))
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

  private fun buildSeedBadges(): List<LoveBadgeEntity> {
    val now = System.currentTimeMillis()
    return listOf(
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
        claimedTimestamp = now
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
        rewardQuoteEn = "Love is having someone walk through simple days together."
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
        rewardQuoteEn = "100 days marking our forever story."
      ),
      LoveBadgeEntity(
        id = "badge_seed_4",
        targetDays = 200,
        titleVi = "Nhịp Đập Chung Đôi",
        titleEn = "Two Hundred Beats",
        descVi = "200 ngày thấu hiểu từng thói quen nhỏ và quan tâm dịu dàng.",
        descEn = "200 days of learning each other's rhythms and daily care.",
        tier = "GOLD",
        iconType = "heart_pulse",
        rewardQuoteVi = "Hạnh phúc là khi bình yên nằm ngay trong tin nhắn buổi sáng.",
        rewardQuoteEn = "Peace arrives with every morning check-in from you."
      ),
      LoveBadgeEntity(
        id = "badge_seed_5",
        targetDays = 365,
        titleVi = "Một Năm Trọn Vẹn",
        titleEn = "First Year Together",
        descVi = "Đi cùng nhau trọn vẹn bốn mùa xuân hạ thu đông đầu tiên.",
        descEn = "Four seasons shared together from our very first chapter.",
        tier = "PLATINUM",
        iconType = "trophy_heart",
        rewardQuoteVi = "Một năm trôi qua, điều quý giá nhất vẫn là còn nguyên vẹn nụ cười cạnh nhau.",
        rewardQuoteEn = "A full year around the sun, still smiling side by side."
      ),
      LoveBadgeEntity(
        id = "badge_seed_6",
        targetDays = 500,
        titleVi = "Năm Trăm Ngày Thương",
        titleEn = "Five Hundred Moments",
        descVi = "500 ngày lưu giữ biết bao bức ảnh, lời hẹn và kỷ niệm chung.",
        descEn = "500 days filled with shared snapshots, plans, and quiet warmth.",
        tier = "PLATINUM",
        iconType = "sparkles_heart",
        rewardQuoteVi = "Thời gian không làm tình cảm cũ đi mà khiến ta thương nhau sâu hơn.",
        rewardQuoteEn = "Time deepens every small promise we keep together."
      ),
      LoveBadgeEntity(
        id = "badge_seed_7",
        targetDays = 730,
        titleVi = "Hai Năm Bền Chặt",
        titleEn = "Two Years Strong",
        descVi = "Hai năm đồng hành qua những bước ngoặt và trưởng thành cùng nhau.",
        descEn = "Two years growing together through quiet days and big milestones.",
        tier = "DIAMOND",
        iconType = "diamond_heart",
        rewardQuoteVi = "Cùng nhau đi qua giông gió nhỏ để thấy rõ mình hợp nhau đến nhường nào.",
        rewardQuoteEn = "Two years of steady partnership built on patience and warmth."
      ),
      LoveBadgeEntity(
        id = "badge_seed_8",
        targetDays = 1000,
        titleVi = "Ngàn Ngày Gắn Bó",
        titleEn = "Thousand-Day Bond",
        descVi = "Cột mốc 1.000 ngày đáng nhớ đánh dấu sự tin tưởng và đồng điệu.",
        descEn = "A 1,000-day milestone celebrating deep trust and shared dreams.",
        tier = "DIAMOND",
        iconType = "infinity_heart",
        rewardQuoteVi = "Một ngàn ngày thức dậy và biết rằng luôn có người đợi mình.",
        rewardQuoteEn = "One thousand mornings knowing home is wherever you are."
      ),
      LoveBadgeEntity(
        id = "badge_seed_9",
        targetDays = 1461,
        titleVi = "Bốn Năm Đồng Hành",
        titleEn = "Four Years Side by Side",
        descVi = "Bốn năm trọn vẹn kể cả ngày nhuận, tình cảm ngày càng chín chắn.",
        descEn = "Four full years including leap day, calmer and closer than ever.",
        tier = "ROYAL",
        iconType = "crown_gem",
        rewardQuoteVi = "Không cần lời hứa hoa mỹ, chỉ cần mỗi năm đều có mặt trong cuộc đời nhau.",
        rewardQuoteEn = "Year after year, choosing each other remains effortless."
      ),
      LoveBadgeEntity(
        id = "badge_seed_10",
        targetDays = 1825,
        titleVi = "Năm Năm Vẹn Nguyên",
        titleEn = "Five-Year Milestone",
        descVi = "Nửa thập kỷ viết chung câu chuyện tình yêu thật đẹp và đáng trân trọng.",
        descEn = "Half a decade of writing a timeless love story side by side.",
        tier = "ETERNAL",
        iconType = "eternal_ring",
        rewardQuoteVi = "Năm năm chỉ là khởi đầu đẹp cho chặng đường dài phía trước.",
        rewardQuoteEn = "Five years down, and our favorite chapters are still ahead."
      )
    )
  }

  private fun buildSeedChecklist(language: com.example.ui.util.AppLanguage): List<ChecklistItemEntity> {
    val isEn = language == com.example.ui.util.AppLanguage.EN
    return listOf(
      ChecklistItemEntity(
        id = 1,
        text = if (isEn) "Watch a cozy movie together with buttered popcorn" else "Cùng nhau xem phim và ăn bắp rang bơ",
        iconName = "movie",
        isCompleted = false
      ),
      ChecklistItemEntity(
        id = 2,
        text = if (isEn) "Take a sunset check-in photo together" else "Cùng chụp một bức ảnh check-in dưới hoàng hôn",
        iconName = "camera",
        isCompleted = false
      ),
      ChecklistItemEntity(
        id = 3,
        text = if (isEn) "Cook a warm homemade dinner together" else "Nấu một bữa tối ấm cúng cùng nhau",
        iconName = "restaurant",
        isCompleted = false
      ),
      ChecklistItemEntity(
        id = 4,
        text = if (isEn) "Write a short heartfelt note for each other" else "Viết một lá thư tay hoặc ghi chú nhỏ cho nhau",
        iconName = "mail",
        isCompleted = false
      ),
      ChecklistItemEntity(
        id = 5,
        text = if (isEn) "Have a phone-free weekend coffee date" else "Hẹn hò cà phê sáng cuối tuần không dùng điện thoại",
        iconName = "coffee",
        isCompleted = false
      ),
      ChecklistItemEntity(
        id = 6,
        text = if (isEn) "Update partner dossier likes for tailored gift ideas" else "Cập nhật hồ sơ sở thích để gợi ý quà chuẩn hơn",
        iconName = "gift",
        isCompleted = false
      ),
      ChecklistItemEntity(
        id = 7,
        text = if (isEn) "Curate a shared playlist of meaningful songs" else "Tạo chung một playlist những bài hát kỷ niệm",
        iconName = "music",
        isCompleted = false
      ),
      ChecklistItemEntity(
        id = 8,
        text = if (isEn) "Plan ahead for your next special anniversary" else "Lên kế hoạch sớm cho cột mốc kỷ niệm sắp tới",
        iconName = "calendar",
        isCompleted = false
      )
    )
  }
}
