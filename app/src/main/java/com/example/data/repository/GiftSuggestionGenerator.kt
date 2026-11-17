package com.example.data.repository

import android.util.Log
import com.example.data.db.InLoveDao
import com.example.data.model.GiftIdeaEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GiftSuggestionGenerator(private val dao: InLoveDao) {

  /**
   * Builds template-based gift suggestions from the couple's mutual interests and occasion (no AI model involved).
   * ponytail: name kept for existing callers; rows still carry isAiGenerated=true so the personalized chip filters them.
   */
  suspend fun generateAiGiftSuggestions(
    partnerName: String,
    mutualInterests: Set<String>,
    occasion: String,
    isEnglish: Boolean = false
  ): Result<List<GiftIdeaEntity>> = withContext(Dispatchers.IO) {
    try {
      val generated = mutableListOf<GiftIdeaEntity>()
      val cleanPartner = partnerName.ifBlank { "Người thương" }
      val shownPartner = partnerName.ifBlank { "Your Sweetheart" }
      fun pick(vi: String, en: String) = if (isEnglish) en else vi

      if (mutualInterests.contains("coffee")) {
        generated.add(
          GiftIdeaEntity(
            title = pick("Set Bình Giữ Nhiệt Khắc Tên & Cà Phê Đặc Sản Cho $cleanPartner", "Custom-Engraved Thermos & Specialty Coffee Set for $shownPartner"),
            category = "Trải nghiệm & Thưởng thức",
            badgeText = pick("Hợp Sở Thích ☕", "Matches Your Interests ☕"),
            tag = pick("Cà phê & Gắn kết", "Coffee & Connection"),
            description = pick("Dành riêng cho hai bạn yêu thích nhâm nhi cà phê sáng cùng nhau. Bình khắc ngày kỷ niệm giúp giữ trọn hương vị ấm áp.", "Made for two people who love sipping morning coffee together. A flask engraved with your anniversary date keeps every sip warm."),
            imageUrl = "https://images.unsplash.com/photo-1514432324607-a09d9b4aefdd?q=80&w=800&auto=format&fit=crop",
            detailsSnippet = pick("Tặng kèm 2 gói cà phê Arabica Cầu Đất nguyên chất", "Includes 2 packs of pure Arabica coffee from Cau Dat"),
            actionText = pick("Xem mẫu khắc tên đôi", "See couple engraving designs"),
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
            title = pick("Chuyến Dã Ngoại Glamping Hoàng Hôn Cùng $cleanPartner", "Sunset Glamping Getaway with $shownPartner"),
            category = "Địa điểm hẹn hò",
            badgeText = pick("Trải Nghiệm Lãng Mạn ✈️", "Romantic Experience ✈️"),
            tag = pick("Du lịch đôi", "Couples' Travel"),
            description = pick("Trải nghiệm cắm trại cao cấp giữa thiên nhiên thoáng đãng, nướng BBQ và ngắm bầu trời đêm lãng mạn chỉ có 2 người.", "A premium camping experience in open nature: a BBQ dinner and a romantic night sky just for the two of you."),
            imageUrl = "https://images.unsplash.com/photo-1506744038136-46273834b3fb?q=80&w=800&auto=format&fit=crop",
            detailsSnippet = pick("Lều Mông Cổ cao cấp, tiệc nướng BBQ thơm lừng", "Luxury Mongolian-style tent and a mouth-watering BBQ feast"),
            actionText = pick("Lên lịch trình hẹn hò", "Plan your date itinerary"),
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
            title = pick("Rạp Phim Mini Tại Gia & Danh Sách Tình Ca Dành Riêng $cleanPartner", "Home Mini Cinema & a Love-Song Playlist Made for $shownPartner"),
            category = "Công nghệ & Cảm xúc",
            badgeText = pick("Ý Tưởng Ấm Cúng 🎬", "Cozy Idea 🎬"),
            tag = pick("Chill tại nhà", "Chill at home"),
            description = pick("Biến phòng ngủ thành rạp chiếu phim riêng tư lãng mạn. Cùng nhau xem lại những bộ phim tình cảm hai bạn yêu thích.", "Turn your bedroom into a private, romantic movie theater and rewatch the love stories you both adore."),
            imageUrl = "https://images.unsplash.com/photo-1517604931442-7e0c8ed2963c?q=80&w=800&auto=format&fit=crop",
            detailsSnippet = pick("Kèm bỏng ngô vị phô mai & nến thơm tinh dầu hoa oải hương", "With cheese popcorn and a lavender essential-oil candle"),
            actionText = pick("Xem danh sách phim gợi ý", "See movie suggestions"),
            isAiGenerated = true,
            targetInterests = "cinema,music",
            remoteId = "tpl_cinema_${occasion}_$cleanPartner",
            suggestedOccasion = occasion
          )
        )
      }

      // Universal romantic personalized gift based on occasion
      val occasionText = occasion.ifBlank { pick("Dịp kỷ niệm đặc biệt", "A special occasion") }
      generated.add(
        GiftIdeaEntity(
          title = pick("Cuốn Sách '100 Điều Tuyệt Vời Nhất Về $cleanPartner'", "The Book '100 Wonderful Things About $shownPartner'"),
          category = "Kỷ vật Handmade",
          badgeText = pick("Dành Riêng Cho $cleanPartner ✨", "Just for $shownPartner ✨"),
          tag = occasionText,
          description = pick("Món quà tinh thần chạm tới đáy tim được cá nhân hóa từng trang viết tay những kỷ niệm ngọt ngào nhất của hai bạn.", "A gift from the heart, personalized page by page with handwritten memories of your sweetest moments together."),
          imageUrl = "https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c?q=80&w=800&auto=format&fit=crop",
          detailsSnippet = pick("Tặng kèm ảnh chụp Instax polaroid và hộp nơ nhung", "With Instax polaroid photos and a velvet-bow gift box"),
          actionText = pick("Bắt đầu soạn thảo lời yêu", "Start writing your love note"),
          isAiGenerated = true,
          targetInterests = mutualInterests.joinToString(","),
          remoteId = "tpl_book_${occasion}_$cleanPartner",
          suggestedOccasion = occasion
        )
      )

      dao.deleteUnfavoritedTemplates()
      val stored = generated.map { item -> dao.upsertGiftIdeaByRemoteId(item); dao.getGiftIdeaByRemoteId(item.remoteId) ?: item }
      Result.success(stored)
    } catch (e: Exception) {
      Log.e("InLoveRepository", "Error generating template gift suggestions:${e.message}", e)
      Result.failure(e)
    }
  }
}
