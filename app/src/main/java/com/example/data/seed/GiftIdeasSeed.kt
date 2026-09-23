package com.example.data.seed

import com.example.data.model.GiftIdeaEntity
import com.example.ui.util.AppLanguage

/**
 * Catalog quà tặng mở rộng — thay thế bộ seed 2 món trong
 * `InLoveRepository.seedOfflineDataIfStillEmpty()` (xem Task D3/D4 trong
 * `docs/superpowers/plans/2026-09-24-master-hardening-and-relaunch.md`).
 *
 * ═══ BILINGUAL MÀ KHÔNG ĐỔI SCHEMA ROOM ═══
 *
 * [GiftIdeaEntity] chỉ có field String đơn ngữ (không có `titleEn` như [com.example.data.model.LoveBadgeEntity]
 * đã có sẵn cho badge). Thêm cột mới đòi hỏi Room migration (Task A6, chưa an toàn làm mù mờ
 * không có build để verify). Giải pháp: [GiftSeed] giữ CẢ vi lẫn en, và [GiftSeed.toEntity] chọn
 * đúng bản theo [AppLanguage] tại thời điểm seed — records trong Room vẫn chỉ có 1 ngôn ngữ,
 * đúng ngôn ngữ người dùng đang chọn, không cần cột mới.
 *
 * ═══ ẢNH MINH HOẠ ═══
 *
 * Dùng Lorem Picsum (`picsum.photos/seed/...`) thay vì tự đoán ID ảnh Unsplash cụ thể — không có
 * cách nào kiểm chứng một ID ảnh Unsplash bịa ra có tồn tại/còn hợp lệ hay không tại thời điểm
 * viết file này (không có kết nối build để test), và ảnh vỡ (broken image) trên Gift Screen tệ
 * hơn nhiều so với ảnh placeholder trung tính nhưng LUÔN load được. Khi có pipeline ảnh thật
 * (Task A5 — Cloudinary ký upload), thay các URL này bằng ảnh biên tập thật.
 */
object GiftIdeasSeed {

    data class GiftSeed(
        val titleVi: String,
        val titleEn: String,
        val category: String,
        val badgeTextVi: String,
        val badgeTextEn: String,
        val tag: String,
        val descriptionVi: String,
        val descriptionEn: String,
        val imageSeed: String,
        val detailsSnippetVi: String,
        val detailsSnippetEn: String,
        val actionTextVi: String,
        val actionTextEn: String,
        val suggestedOccasion: String,
        val priceRange: String
    ) {
        fun toEntity(lang: AppLanguage): GiftIdeaEntity = GiftIdeaEntity(
            title = if (lang == AppLanguage.EN) titleEn else titleVi,
            category = category,
            badgeText = if (lang == AppLanguage.EN) badgeTextEn else badgeTextVi,
            tag = tag,
            description = if (lang == AppLanguage.EN) descriptionEn else descriptionVi,
            imageUrl = "https://picsum.photos/seed/$imageSeed/800/600",
            detailsSnippet = if (lang == AppLanguage.EN) detailsSnippetEn else detailsSnippetVi,
            actionText = if (lang == AppLanguage.EN) actionTextEn else actionTextVi,
            isAiGenerated = false,
            suggestedOccasion = suggestedOccasion,
            priceRange = priceRange
        )
    }

    /** Khoảng giá chuẩn hoá — dùng để lọc trên GiftScreen. */
    object PriceRange {
        const val UNDER_200K = "<200k"
        const val RANGE_200_500K = "200k-500k"
        const val RANGE_500K_1M = "500k-1tr"
        const val OVER_1M = ">1tr"
    }

    val all: List<GiftSeed> = listOf(
        GiftSeed(
            titleVi = "Bó Hoa Hồng Sáp Kèm Thiệp Thư Tay",
            titleEn = "Everlasting Rose Bouquet with Handwritten Card",
            category = "Lãng Mạn",
            badgeTextVi = "Được yêu thích nhất 💖",
            badgeTextEn = "Most loved 💖",
            tag = "Kỷ niệm",
            descriptionVi = "Món quà tinh tế, vĩnh cửu cùng lời nhắn gửi chân thành từ tận đáy lòng.",
            descriptionEn = "An elegant, everlasting gift paired with a heartfelt handwritten note.",
            imageSeed = "gift-rose-bouquet",
            detailsSnippetVi = "Hương hoa hồng dịu nhẹ, lưu giữ trọn vẹn theo thời gian",
            detailsSnippetEn = "Gentle rose fragrance, preserved beautifully over time",
            actionTextVi = "Xem gợi ý chi tiết",
            actionTextEn = "See details",
            suggestedOccasion = "Valentine",
            priceRange = PriceRange.RANGE_200_500K
        ),
        GiftSeed(
            titleVi = "Bữa Tối Nến Lãng Mạn Tự Nấu",
            titleEn = "Candlelit Home-Cooked Dinner",
            category = "Trải Nghiệm",
            badgeTextVi = "Ấm áp & Riêng tư ✨",
            badgeTextEn = "Warm & Intimate ✨",
            tag = "Hẹn hò",
            descriptionVi = "Chuẩn bị món ăn người ấy yêu thích với ánh nến lung linh và giai điệu acoustic.",
            descriptionEn = "Cook their favorite dish under candlelight with soft acoustic music playing.",
            imageSeed = "gift-candlelit-dinner",
            detailsSnippetVi = "Không gian chỉ có hai bạn, lắng đọng từng cảm xúc",
            detailsSnippetEn = "Just the two of you, savoring every moment",
            actionTextVi = "Lên thực đơn yêu thương",
            actionTextEn = "Plan the menu",
            suggestedOccasion = "Kỷ niệm ngày yêu",
            priceRange = PriceRange.UNDER_200K
        ),
        GiftSeed(
            titleVi = "Album Ảnh Kỷ Niệm In Thủ Công",
            titleEn = "Handcrafted Memory Photo Album",
            category = "Ý Nghĩa",
            badgeTextVi = "Lưu giữ mãi mãi 📖",
            badgeTextEn = "Keep forever 📖",
            tag = "Kỷ niệm",
            descriptionVi = "Tuyển chọn những khoảnh khắc đẹp nhất, in thành cuốn album bìa da thật.",
            descriptionEn = "Curate your best moments into a genuine leather-bound album.",
            imageSeed = "gift-photo-album",
            detailsSnippetVi = "Mỗi trang là một câu chuyện của hai bạn",
            detailsSnippetEn = "Every page tells a chapter of your story",
            actionTextVi = "Chọn ảnh để in",
            actionTextEn = "Pick photos to print",
            suggestedOccasion = "Kỷ niệm ngày yêu",
            priceRange = PriceRange.RANGE_200_500K
        ),
        GiftSeed(
            titleVi = "Vòng Tay Khắc Tên Đôi",
            titleEn = "Couple's Engraved Bracelet Set",
            category = "Trang Sức",
            badgeTextVi = "Bán chạy 🔥",
            badgeTextEn = "Bestseller 🔥",
            tag = "Đôi",
            descriptionVi = "Cặp vòng tay bạc khắc tên hoặc ngày đặc biệt, đeo cùng nhau mỗi ngày.",
            descriptionEn = "A matching silver bracelet pair engraved with names or a special date.",
            imageSeed = "gift-couple-bracelet",
            detailsSnippetVi = "Chất liệu bạc 925 không gây dị ứng",
            detailsSnippetEn = "Hypoallergenic 925 sterling silver",
            actionTextVi = "Khắc tên ngay",
            actionTextEn = "Engrave now",
            suggestedOccasion = "20/10",
            priceRange = PriceRange.RANGE_200_500K
        ),
        GiftSeed(
            titleVi = "Chuyến Dã Ngoại Cắm Trại Cuối Tuần",
            titleEn = "Weekend Camping Getaway",
            category = "Trải Nghiệm",
            badgeTextVi = "Gắn kết sâu sắc 🏕️",
            badgeTextEn = "Deepen your bond 🏕️",
            tag = "Phiêu lưu",
            descriptionVi = "Rời xa phố thị, cùng nhau ngắm sao và nướng marshmallow bên đống lửa trại.",
            descriptionEn = "Escape the city, stargaze together and roast marshmallows by the campfire.",
            imageSeed = "gift-camping-trip",
            detailsSnippetVi = "Phù hợp cặp đôi yêu thiên nhiên",
            detailsSnippetEn = "Perfect for nature-loving couples",
            actionTextVi = "Lên kế hoạch chuyến đi",
            actionTextEn = "Plan the trip",
            suggestedOccasion = "Kỷ niệm ngày yêu",
            priceRange = PriceRange.RANGE_500K_1M
        ),
        GiftSeed(
            titleVi = "Nước Hoa Đôi Cặp",
            titleEn = "His & Hers Perfume Set",
            category = "Làm Đẹp",
            badgeTextVi = "Sang trọng 💎",
            badgeTextEn = "Elegant 💎",
            tag = "Đôi",
            descriptionVi = "Bộ đôi hương thơm hoà quyện, mang dấu ấn riêng của cả hai người.",
            descriptionEn = "A matched fragrance duo that blends into a scent uniquely yours.",
            imageSeed = "gift-perfume-set",
            detailsSnippetVi = "Lưu hương trên 8 giờ",
            detailsSnippetEn = "Long-lasting scent, 8+ hours",
            actionTextVi = "Chọn mùi hương",
            actionTextEn = "Choose a scent",
            suggestedOccasion = "Sinh nhật",
            priceRange = PriceRange.OVER_1M
        ),
        GiftSeed(
            titleVi = "Bộ Board Game Cho Cặp Đôi",
            titleEn = "Couple's Board Game Night Set",
            category = "Trải Nghiệm",
            badgeTextVi = "Vui nhộn 🎲",
            badgeTextEn = "Fun night in 🎲",
            tag = "Tại nhà",
            descriptionVi = "Buổi tối cuối tuần thêm rộn ràng tiếng cười với những ván cờ đối kháng ngọt ngào.",
            descriptionEn = "Turn a quiet weekend night into laughter-filled friendly competition.",
            imageSeed = "gift-board-game",
            detailsSnippetVi = "Dễ chơi, không cần kinh nghiệm",
            detailsSnippetEn = "Easy to learn, no experience needed",
            actionTextVi = "Xem luật chơi",
            actionTextEn = "See the rules",
            suggestedOccasion = "Kỷ niệm ngày yêu",
            priceRange = PriceRange.UNDER_200K
        ),
        GiftSeed(
            titleVi = "Đồng Hồ Đôi Phong Cách Tối Giản",
            titleEn = "Minimalist Couple Watch Set",
            category = "Phụ Kiện",
            badgeTextVi = "Thanh lịch ⌚",
            badgeTextEn = "Timeless elegance ⌚",
            tag = "Đôi",
            descriptionVi = "Thiết kế tối giản, sang trọng, hợp cả đi làm lẫn đi chơi.",
            descriptionEn = "Sleek minimalist design that fits both work and weekend outings.",
            imageSeed = "gift-couple-watch",
            detailsSnippetVi = "Chống nước, bảo hành 2 năm",
            detailsSnippetEn = "Water-resistant, 2-year warranty",
            actionTextVi = "Xem bộ sưu tập",
            actionTextEn = "View collection",
            suggestedOccasion = "Kỷ niệm ngày yêu",
            priceRange = PriceRange.OVER_1M
        ),
        GiftSeed(
            titleVi = "Đèn Ngủ Khắc Ảnh 3D",
            titleEn = "3D Photo Crystal Night Lamp",
            category = "Trang Trí",
            badgeTextVi = "Độc đáo ✨",
            badgeTextEn = "One of a kind ✨",
            tag = "Kỷ niệm",
            descriptionVi = "Bức ảnh yêu thích được khắc laser 3D vào khối pha lê, phát sáng dịu nhẹ mỗi tối.",
            descriptionEn = "Your favorite photo laser-engraved into crystal, glowing softly every night.",
            imageSeed = "gift-photo-lamp",
            detailsSnippetVi = "Gửi ảnh, nhận sản phẩm sau 3-5 ngày",
            detailsSnippetEn = "Upload a photo, receive it in 3-5 days",
            actionTextVi = "Tải ảnh lên",
            actionTextEn = "Upload photo",
            suggestedOccasion = "Kỷ niệm ngày yêu",
            priceRange = PriceRange.RANGE_200_500K
        ),
        GiftSeed(
            titleVi = "Vé Xem Phim Suất Chiếu Sớm",
            titleEn = "Early Movie Premiere Tickets",
            category = "Trải Nghiệm",
            badgeTextVi = "Giản dị ngọt ngào 🎬",
            badgeTextEn = "Simple & sweet 🎬",
            tag = "Hẹn hò",
            descriptionVi = "Cùng nhau xem bộ phim đang chờ đợi, tay trong tay giữa rạp tối.",
            descriptionEn = "Watch the movie you've both been waiting for, hand in hand.",
            imageSeed = "gift-movie-tickets",
            detailsSnippetVi = "Kèm combo bắp nước",
            detailsSnippetEn = "Popcorn combo included",
            actionTextVi = "Đặt vé ngay",
            actionTextEn = "Book tickets",
            suggestedOccasion = "Hẹn hò cuối tuần",
            priceRange = PriceRange.UNDER_200K
        ),
        GiftSeed(
            titleVi = "Sổ Tay Viết Cho Nhau Mỗi Ngày",
            titleEn = "Daily Love Notes Journal",
            category = "Ý Nghĩa",
            badgeTextVi = "Chữa lành 🧡",
            badgeTextEn = "Heartfelt 🧡",
            tag = "Tại nhà",
            descriptionVi = "Cuốn sổ hai người thay nhau viết một câu mỗi ngày — nhỏ bé nhưng bền lâu.",
            descriptionEn = "A shared journal where you each write one line a day — small but lasting.",
            imageSeed = "gift-love-journal",
            detailsSnippetVi = "365 trang cho 365 ngày yêu thương",
            detailsSnippetEn = "365 pages for 365 days of love",
            actionTextVi = "Bắt đầu viết",
            actionTextEn = "Start writing",
            suggestedOccasion = "Kỷ niệm ngày yêu",
            priceRange = PriceRange.UNDER_200K
        ),
        GiftSeed(
            titleVi = "Gấu Bông Ôm Khổng Lồ",
            titleEn = "Giant Hug Teddy Bear",
            category = "Dễ Thương",
            badgeTextVi = "Được yêu thích 🧸",
            badgeTextEn = "Fan favorite 🧸",
            tag = "Dễ thương",
            descriptionVi = "Ôm ấm mỗi khi nhớ người ấy — mềm mại như một cái ôm thật sự.",
            descriptionEn = "A warm hug whenever you miss them — soft as a real embrace.",
            imageSeed = "gift-teddy-bear",
            detailsSnippetVi = "Cao 1m2, bông siêu mềm an toàn",
            detailsSnippetEn = "1.2m tall, ultra-soft safe filling",
            actionTextVi = "Chọn màu yêu thích",
            actionTextEn = "Choose a color",
            suggestedOccasion = "Sinh nhật",
            priceRange = PriceRange.RANGE_500K_1M
        ),
        GiftSeed(
            titleVi = "Lớp Học Nấu Ăn Cho Cặp Đôi",
            titleEn = "Couple's Cooking Class",
            category = "Trải Nghiệm",
            badgeTextVi = "Học điều mới cùng nhau 👩‍🍳",
            badgeTextEn = "Learn something new together 👩‍🍳",
            tag = "Phiêu lưu",
            descriptionVi = "Cùng đầu bếp chuyên nghiệp học làm một món ăn mới, vừa vui vừa ý nghĩa.",
            descriptionEn = "Learn a new dish together with a professional chef — fun and meaningful.",
            imageSeed = "gift-cooking-class",
            detailsSnippetVi = "Bao gồm nguyên liệu và dụng cụ",
            detailsSnippetEn = "Ingredients and tools included",
            actionTextVi = "Đăng ký lớp học",
            actionTextEn = "Book the class",
            suggestedOccasion = "Kỷ niệm ngày yêu",
            priceRange = PriceRange.RANGE_500K_1M
        ),
        GiftSeed(
            titleVi = "Tranh Chân Dung Đôi Vẽ Tay",
            titleEn = "Custom Hand-Drawn Couple Portrait",
            category = "Ý Nghĩa",
            badgeTextVi = "Nghệ thuật riêng biệt 🎨",
            badgeTextEn = "One-of-a-kind art 🎨",
            tag = "Kỷ niệm",
            descriptionVi = "Chân dung hai bạn được hoạ sĩ vẽ tay từ ảnh yêu thích, đóng khung sẵn sàng treo tường.",
            descriptionEn = "A hand-drawn portrait of you two from your favorite photo, framed and ready to hang.",
            imageSeed = "gift-couple-portrait",
            detailsSnippetVi = "Thời gian hoàn thành 5-7 ngày",
            detailsSnippetEn = "Completed within 5-7 days",
            actionTextVi = "Gửi ảnh mẫu",
            actionTextEn = "Send a reference photo",
            suggestedOccasion = "Kỷ niệm ngày yêu",
            priceRange = PriceRange.RANGE_500K_1M
        ),
        GiftSeed(
            titleVi = "Voucher Spa Đôi Thư Giãn",
            titleEn = "Couple's Spa Retreat Voucher",
            category = "Thư Giãn",
            badgeTextVi = "Xả stress 💆",
            badgeTextEn = "Unwind together 💆",
            tag = "Thư giãn",
            descriptionVi = "Một buổi chiều thư giãn trọn vẹn, tạm gác lại mọi lo toan thường ngày.",
            descriptionEn = "A full afternoon of relaxation, leaving everyday worries behind.",
            imageSeed = "gift-couple-spa",
            detailsSnippetVi = "Áp dụng tại hệ thống spa liên kết",
            detailsSnippetEn = "Valid at partner spa locations",
            actionTextVi = "Đặt lịch spa",
            actionTextEn = "Book a session",
            suggestedOccasion = "8/3",
            priceRange = PriceRange.OVER_1M
        ),
        GiftSeed(
            titleVi = "Cây Cảnh Mini Để Bàn Kèm Lời Chúc",
            titleEn = "Mini Desk Plant with a Note",
            category = "Trang Trí",
            badgeTextVi = "Ngân sách nhỏ 🌱",
            badgeTextEn = "Budget-friendly 🌱",
            tag = "Nhẹ nhàng",
            descriptionVi = "Món quà nhỏ xinh, dễ chăm, nhắc người ấy nhớ đến bạn mỗi ngày làm việc.",
            descriptionEn = "A small, easy-care gift that reminds them of you every workday.",
            imageSeed = "gift-desk-plant",
            detailsSnippetVi = "Kèm thiệp viết tay theo yêu cầu",
            detailsSnippetEn = "Handwritten card included on request",
            actionTextVi = "Viết lời chúc",
            actionTextEn = "Write a note",
            suggestedOccasion = "Bất kỳ dịp nào",
            priceRange = PriceRange.UNDER_200K
        ),
        GiftSeed(
            titleVi = "Chuyến Du Lịch Ngắn Ngày Hai Người",
            titleEn = "Short Weekend Trip for Two",
            category = "Trải Nghiệm",
            badgeTextVi = "Đáng nhớ nhất 🧳",
            badgeTextEn = "Most memorable 🧳",
            tag = "Phiêu lưu",
            descriptionVi = "Rời khỏi guồng quay quen thuộc, khám phá một nơi mới cùng nhau.",
            descriptionEn = "Step away from routine and explore somewhere new together.",
            imageSeed = "gift-weekend-trip",
            detailsSnippetVi = "Gợi ý theo ngân sách và điểm đến yêu thích",
            detailsSnippetEn = "Suggested by budget and preferred destination",
            actionTextVi = "Lên lịch trình",
            actionTextEn = "Plan the itinerary",
            suggestedOccasion = "Kỷ niệm ngày yêu",
            priceRange = PriceRange.OVER_1M
        ),
        GiftSeed(
            titleVi = "Bộ Cốc Đôi In Tên Theo Yêu Cầu",
            titleEn = "Personalized Couple Mug Set",
            category = "Tại Nhà",
            badgeTextVi = "Ấm áp mỗi sáng ☕",
            badgeTextEn = "Warm every morning ☕",
            tag = "Đôi",
            descriptionVi = "Cùng nhâm nhi cà phê mỗi sáng với cặp cốc in tên hoặc hình ảnh của hai bạn.",
            descriptionEn = "Sip coffee together each morning with mugs personalized with your names or photo.",
            imageSeed = "gift-couple-mugs",
            detailsSnippetVi = "An toàn cho lò vi sóng và máy rửa chén",
            detailsSnippetEn = "Microwave and dishwasher safe",
            actionTextVi = "Tuỳ chỉnh ngay",
            actionTextEn = "Customize now",
            suggestedOccasion = "Giáng Sinh",
            priceRange = PriceRange.UNDER_200K
        ),
        GiftSeed(
            titleVi = "Trải Nghiệm Khinh Khí Cầu Ngắm Bình Minh",
            titleEn = "Sunrise Hot Air Balloon Experience",
            category = "Trải Nghiệm",
            badgeTextVi = "Đặc biệt nhất 🎈",
            badgeTextEn = "Extra special 🎈",
            tag = "Phiêu lưu",
            descriptionVi = "Ngắm bình minh từ trên cao — một kỷ niệm khó quên cho dịp thật đặc biệt.",
            descriptionEn = "Watch the sunrise from above — an unforgettable memory for a big occasion.",
            imageSeed = "gift-hot-air-balloon",
            detailsSnippetVi = "Cần đặt trước tối thiểu 7 ngày",
            detailsSnippetEn = "Requires booking at least 7 days ahead",
            actionTextVi = "Kiểm tra lịch trống",
            actionTextEn = "Check availability",
            suggestedOccasion = "Kỷ niệm 1 năm",
            priceRange = PriceRange.OVER_1M
        ),
        GiftSeed(
            titleVi = "Playlist Nhạc Riêng Tặng Người Ấy",
            titleEn = "Custom Playlist Made For Them",
            category = "Ý Nghĩa",
            badgeTextVi = "Miễn phí, đầy tâm huyết 🎵",
            badgeTextEn = "Free & heartfelt 🎵",
            tag = "Nhẹ nhàng",
            descriptionVi = "Tổng hợp những bài hát gắn với kỷ niệm hai người, kèm lý do chọn từng bài.",
            descriptionEn = "A curated playlist of songs tied to your memories, with a note on why each was picked.",
            imageSeed = "gift-playlist",
            detailsSnippetVi = "Không tốn chi phí, chỉ cần sự chân thành",
            detailsSnippetEn = "Costs nothing but thoughtfulness",
            actionTextVi = "Bắt đầu tạo playlist",
            actionTextEn = "Start the playlist",
            suggestedOccasion = "Bất kỳ dịp nào",
            priceRange = PriceRange.UNDER_200K
        )
    )

    /** Lọc theo dịp (khớp [GiftSeed.suggestedOccasion]) — tiện cho Task D4 khi seed theo mùa lễ. */
    fun forOccasion(occasion: String): List<GiftSeed> = all.filter { it.suggestedOccasion == occasion }
}
