import Foundation

public enum GuideChapterId: String, Codable, CaseIterable, Identifiable, Sendable {
    case countdownAndMilestones = "COUNTDOWN_AND_MILESTONES"
    case onlineCouplePairing = "ONLINE_COUPLE_PAIRING"
    case dossierAndGiftConcierge = "DOSSIER_AND_GIFT_CONCIERGE"
    case remindersAndPrivacy = "REMINDERS_AND_PRIVACY"

    public var id: String { rawValue }
}

public enum GuideQuickAction: String, Codable, CaseIterable, Sendable {
    case openHome = "OPEN_HOME"
    case openPairing = "OPEN_PAIRING"
    case openDossier = "OPEN_DOSSIER"
    case openCalendar = "OPEN_CALENDAR"

    public func buttonTitle(isEnglish: Bool) -> String {
        switch self {
        case .openHome: return isEnglish ? "Go to Love Home" : "Về trang Đếm ngày"
        case .openPairing: return isEnglish ? "Open Pairing" : "Mở Ghép đôi"
        case .openDossier: return isEnglish ? "Open Dossier & Gifts" : "Mở Hồ sơ & Quà"
        case .openCalendar: return isEnglish ? "Open Calendar" : "Mở Lịch & Nhắc hẹn"
        }
    }
}

public struct GuideStep: Identifiable, Codable, Equatable, Sendable {
    public var id: String { "\(numberLabel)_\(titleEn)" }
    public let numberLabel: String
    public let titleVi: String
    public let titleEn: String
    public let detailVi: String
    public let detailEn: String

    public init(
        numberLabel: String,
        titleVi: String,
        titleEn: String,
        detailVi: String,
        detailEn: String
    ) {
        self.numberLabel = numberLabel
        self.titleVi = titleVi
        self.titleEn = titleEn
        self.detailVi = detailVi
        self.detailEn = detailEn
    }

    public func title(isEnglish: Bool) -> String { isEnglish ? titleEn : titleVi }
    public func detail(isEnglish: Bool) -> String { isEnglish ? detailEn : detailVi }
}

public struct GuideChapter: Identifiable, Codable, Equatable, Sendable {
    public let id: GuideChapterId
    public let shortTabVi: String
    public let shortTabEn: String
    public let titleVi: String
    public let titleEn: String
    public let summaryVi: String
    public let summaryEn: String
    public let quickAction: GuideQuickAction
    public let quickActionLabelVi: String
    public let quickActionLabelEn: String
    public let steps: [GuideStep]

    public init(
        id: GuideChapterId,
        shortTabVi: String,
        shortTabEn: String,
        titleVi: String,
        titleEn: String,
        summaryVi: String,
        summaryEn: String,
        quickAction: GuideQuickAction,
        quickActionLabelVi: String,
        quickActionLabelEn: String,
        steps: [GuideStep]
    ) {
        self.id = id
        self.shortTabVi = shortTabVi
        self.shortTabEn = shortTabEn
        self.titleVi = titleVi
        self.titleEn = titleEn
        self.summaryVi = summaryVi
        self.summaryEn = summaryEn
        self.quickAction = quickAction
        self.quickActionLabelVi = quickActionLabelVi
        self.quickActionLabelEn = quickActionLabelEn
        self.steps = steps
    }

    public func shortTab(isEnglish: Bool) -> String { isEnglish ? shortTabEn : shortTabVi }
    public func title(isEnglish: Bool) -> String { isEnglish ? titleEn : titleVi }
    public func summary(isEnglish: Bool) -> String { isEnglish ? summaryEn : summaryVi }
    public func quickActionLabel(isEnglish: Bool) -> String {
        isEnglish ? quickActionLabelEn : quickActionLabelVi
    }
}

public enum InLoveGuideCatalog {
    public static let chapters: [GuideChapter] = [
        GuideChapter(
            id: .countdownAndMilestones,
            shortTabVi: "Đếm ngày",
            shortTabEn: "Days",
            titleVi: "Đếm Ngày Yêu & Cột Mốc",
            titleEn: "Love Days & Milestones",
            summaryVi: "Theo dõi từng ngày bên nhau từ mốc khởi đầu hai bạn đã chọn.",
            summaryEn: "Follow every day together from the start date you both chose.",
            quickAction: .openHome,
            quickActionLabelVi: "Về trang Đếm ngày",
            quickActionLabelEn: "Go to Love Home",
            steps: [
                GuideStep(
                    numberLabel: "01",
                    titleVi: "Chọn ngày bắt đầu yêu",
                    titleEn: "Set your love start date",
                    detailVi: "Chạm bộ đếm ở trang chính để chọn ngày kỷ niệm đầu tiên.",
                    detailEn: "Tap the counter on Home to set your very first day together."
                ),
                GuideStep(
                    numberLabel: "02",
                    titleVi: "Theo dõi mốc ngày tròn",
                    titleEn: "Follow upcoming day milestones",
                    detailVi: "Ứng dụng tính sẵn mốc 100, 365, 520, 1.000 ngày và tròn tháng.",
                    detailEn: "Track 100, 365, 520, and 1,000-day milestones plus monthly anniversaries automatically."
                ),
                GuideStep(
                    numberLabel: "03",
                    titleVi: "Lưu khoảnh khắc đáng nhớ",
                    titleEn: "Save meaningful moments",
                    detailVi: "Lưu ảnh kỷ niệm và câu chuyện nhỏ của hai bạn.",
                    detailEn: "Capture couple photos and short notes along your shared timeline."
                ),
            ]
        ),
        GuideChapter(
            id: .onlineCouplePairing,
            shortTabVi: "Ghép đôi",
            shortTabEn: "Pairing",
            titleVi: "Ghép Đôi & Đồng Bộ Hai Máy",
            titleEn: "Couple Pairing & Sync",
            summaryVi: "Kết nối một-một để hai bạn cùng chung ngày kỷ niệm và lời nhắn.",
            summaryEn: "Link one-to-one so both phones share your dates and warm notes.",
            quickAction: .openPairing,
            quickActionLabelVi: "Mở Ghép đôi",
            quickActionLabelEn: "Open Pairing",
            steps: [
                GuideStep(
                    numberLabel: "01",
                    titleVi: "Đăng nhập nhận mã riêng",
                    titleEn: "Sign in for your code",
                    detailVi: "Mỗi tài khoản có một mã riêng để chia sẻ với người ấy.",
                    detailEn: "Each account receives a personal code ready to share with your partner."
                ),
                GuideStep(
                    numberLabel: "02",
                    titleVi: "Dán mã hoặc liên kết mời",
                    titleEn: "Paste a code or link",
                    detailVi: "Dán mã hoặc liên kết mời để gửi yêu cầu kết đôi.",
                    detailEn: "Paste your partner code or invite link to send a pairing request."
                ),
                GuideStep(
                    numberLabel: "03",
                    titleVi: "Đồng bộ khi có mạng",
                    titleEn: "Auto-sync on reconnect",
                    detailVi: "Mọi cập nhật ngoại tuyến tự đồng bộ lại khi kết nối mạng.",
                    detailEn: "Offline updates stay safe on your phone and sync automatically once online."
                ),
            ]
        ),
        GuideChapter(
            id: .dossierAndGiftConcierge,
            shortTabVi: "Quà & Hồ sơ",
            shortTabEn: "Gifts",
            titleVi: "Hồ Sơ Người Thương & Quà Tặng",
            titleEn: "Partner Dossier & Gifts",
            summaryVi: "Lưu gu thẩm mỹ, số đo và món quà người ấy từng nhắc thích.",
            summaryEn: "Keep tastes, sizes, and small gift hints in one private space.",
            quickAction: .openDossier,
            quickActionLabelVi: "Mở Hồ sơ & Quà",
            quickActionLabelEn: "Open Dossier & Gifts",
            steps: [
                GuideStep(
                    numberLabel: "01",
                    titleVi: "Lưu sở thích và cỡ đồ",
                    titleEn: "Save interests and sizes",
                    detailVi: "Lưu sở thích, cỡ đồ, mùi hương và điều người ấy tránh.",
                    detailEn: "Record love languages, scents, clothing sizes, and allergies to avoid misses."
                ),
                GuideStep(
                    numberLabel: "02",
                    titleVi: "Ghi chú bí mật bất ngờ",
                    titleEn: "Jot secret surprise hints",
                    detailVi: "Ghi chú bí mật chỉ hiển thị với riêng bạn khi chuẩn bị quà.",
                    detailEn: "Secret notes stay visible only to you for thoughtful surprise gifting."
                ),
                GuideStep(
                    numberLabel: "03",
                    titleVi: "Nhận gợi ý quà theo dịp",
                    titleEn: "Get occasion gift ideas",
                    detailVi: "Xem gợi ý quà và lời chúc vừa ngân sách của bạn.",
                    detailEn: "Browse tailored gift picks and love notes matched to your budget."
                ),
            ]
        ),
        GuideChapter(
            id: .remindersAndPrivacy,
            shortTabVi: "Nhắc hẹn",
            shortTabEn: "Alerts",
            titleVi: "Lịch Âm, Nhắc Hẹn & Riêng Tư",
            titleEn: "Lunar Calendar & Privacy",
            summaryVi: "Đặt nhắc dịp lễ Việt Nam, sinh nhật và khóa mã PIN riêng tư.",
            summaryEn: "Stay ahead of Vietnamese holidays, birthdays, and private PIN lock.",
            quickAction: .openCalendar,
            quickActionLabelVi: "Mở Lịch & Nhắc hẹn",
            quickActionLabelEn: "Open Calendar",
            steps: [
                GuideStep(
                    numberLabel: "01",
                    titleVi: "Lịch âm dương ngày lễ Việt",
                    titleEn: "Lunar and solar holidays",
                    detailVi: "Theo dõi Valentine, 8/3, 20/10, Thất Tịch và Rằm tháng Giêng chuẩn xác.",
                    detailEn: "Track Valentine, March 8, October 20, That Tich, and Lunar dates accurately."
                ),
                GuideStep(
                    numberLabel: "02",
                    titleVi: "Tùy chỉnh ngày nhắc trước",
                    titleEn: "Set early reminder timing",
                    detailVi: "Chọn số ngày báo trước và tắt bật âm chuông trong Cài đặt.",
                    detailEn: "Choose advance notice days and toggle chime sound inside Settings."
                ),
                GuideStep(
                    numberLabel: "03",
                    titleVi: "Khóa mã PIN riêng tư",
                    titleEn: "Lock private space with PIN",
                    detailVi: "Bật mã PIN để giữ riêng ảnh kỷ niệm và ghi chú bí mật.",
                    detailEn: "Enable a personal PIN lock to keep memories and secret notes private."
                ),
            ]
        ),
    ]

    public static func chapter(for id: GuideChapterId) -> GuideChapter {
        chapters.first(where: { $0.id == id }) ?? chapters[0]
    }
}
