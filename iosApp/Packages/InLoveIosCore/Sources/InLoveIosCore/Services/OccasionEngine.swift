import Foundation

public enum LunarHolidayId: String, Codable, CaseIterable, Sendable {
    case tet = "TET"
    case ramThangGieng = "RAM_THANG_GIENG"
    case gioToHungVuong = "GIO_TO_HUNG_VUONG"
    case thatTich = "THAT_TICH"
    case vuLan = "VU_LAN"
    case trungThu = "TRUNG_THU"
}

public enum LunarCalendar2025To2040 {
    public static let table: [LunarHolidayId: [Int: String]] = [
        .tet: [
            2025: "2025-01-29", 2026: "2026-02-17", 2027: "2027-02-06", 2028: "2028-01-26",
            2029: "2029-02-13", 2030: "2030-02-02", 2031: "2031-01-23", 2032: "2032-02-11",
            2033: "2033-01-31", 2034: "2034-02-19", 2035: "2035-02-08", 2036: "2036-01-28",
            2037: "2037-02-15", 2038: "2038-02-04", 2039: "2039-01-24", 2040: "2040-02-12",
        ],
        .ramThangGieng: [
            2025: "2025-02-12", 2026: "2026-03-03", 2027: "2027-02-20", 2028: "2028-02-09",
            2029: "2029-02-27", 2030: "2030-02-16", 2031: "2031-02-06", 2032: "2032-02-25",
            2033: "2033-02-14", 2034: "2034-03-05", 2035: "2035-02-22", 2036: "2036-02-11",
            2037: "2037-03-01", 2038: "2038-02-18", 2039: "2039-02-07", 2040: "2040-02-26",
        ],
        .gioToHungVuong: [
            2025: "2025-04-07", 2026: "2026-04-26", 2027: "2027-04-16", 2028: "2028-04-04",
            2029: "2029-04-23", 2030: "2030-04-12", 2031: "2031-04-01", 2032: "2032-04-19",
            2033: "2033-04-09", 2034: "2034-04-28", 2035: "2035-04-17", 2036: "2036-04-06",
            2037: "2037-04-24", 2038: "2038-04-13", 2039: "2039-04-03", 2040: "2040-04-20",
        ],
        .thatTich: [
            2025: "2025-08-29", 2026: "2026-08-19", 2027: "2027-08-08", 2028: "2028-08-26",
            2029: "2029-08-16", 2030: "2030-08-05", 2031: "2031-08-24", 2032: "2032-08-12",
            2033: "2033-08-01", 2034: "2034-08-20", 2035: "2035-08-10", 2036: "2036-08-28",
            2037: "2037-08-17", 2038: "2038-08-07", 2039: "2039-08-26", 2040: "2040-08-14",
        ],
        .vuLan: [
            2025: "2025-09-06", 2026: "2026-08-27", 2027: "2027-08-16", 2028: "2028-09-03",
            2029: "2029-08-24", 2030: "2030-08-13", 2031: "2031-09-01", 2032: "2032-08-20",
            2033: "2033-08-09", 2034: "2034-08-28", 2035: "2035-08-18", 2036: "2036-09-05",
            2037: "2037-08-25", 2038: "2038-08-15", 2039: "2039-09-03", 2040: "2040-08-22",
        ],
        .trungThu: [
            2025: "2025-10-06", 2026: "2026-09-25", 2027: "2027-09-15", 2028: "2028-10-03",
            2029: "2029-09-22", 2030: "2030-09-12", 2031: "2031-10-01", 2032: "2032-09-19",
            2033: "2033-09-08", 2034: "2034-09-26", 2035: "2035-09-16", 2036: "2036-10-04",
            2037: "2037-09-24", 2038: "2038-09-13", 2039: "2039-10-02", 2040: "2040-09-20",
        ],
    ]

    public static func solarIsoDate(for holiday: LunarHolidayId, year: Int) -> String? {
        table[holiday]?[year]
    }
}

public struct OccasionEngine: Sendable {
    public let calculator: LoveDaysCalculator

    public init(calculator: LoveDaysCalculator = LoveDaysCalculator()) {
        self.calculator = calculator
    }

    public static let coupleOccasionProfiles: [OccasionProfile] = [
        OccasionProfile(
            id: "valentine",
            titleVi: "Lễ Tình Nhân Valentine",
            titleEn: "Valentine's Day",
            coupleRelevance: .core,
            giftExpectation: .expected,
            defaultReminderOn: true,
            recommendedLeadDays: [21, 14, 7, 3, 1, 0],
            bookingAdvised: true,
            culturalTaboosVi: ["Tránh hoa cúc trắng hoặc số bông chẵn ở một số văn hóa"],
            culturalTaboosEn: ["Avoid white chrysanthemums or culturally sensitive symbols"],
            prepChecklistVi: ["Đặt bàn tối Valentine sớm trước 2-3 tuần", "Chọn chocolate/hoa an toàn dị ứng"],
            prepChecklistEn: ["Book a dinner spot 2-3 weeks early", "Verify chocolates/flowers are allergy-safe"]
        ),
        OccasionProfile(
            id: "women_day_0803",
            titleVi: "Quốc Tế Phụ Nữ 8/3",
            titleEn: "International Women's Day",
            coupleRelevance: .core,
            giftExpectation: .expected,
            defaultReminderOn: true,
            recommendedLeadDays: [14, 7, 3, 1, 0],
            bookingAdvised: true,
            culturalTaboosVi: ["Tránh hoa cúc vàng/trắng tang lễ"],
            culturalTaboosEn: ["Avoid funeral-associated flowers"],
            prepChecklistVi: ["Đặt hoa hoặc quà trước 5-7 ngày", "Kèm thiệp tay bày tỏ sự trân trọng"],
            prepChecklistEn: ["Order flowers or gift 5-7 days ahead", "Include an appreciation note"]
        ),
        OccasionProfile(
            id: "white_day",
            titleVi: "Valentine Trắng 14/3",
            titleEn: "White Day",
            coupleRelevance: .relevant,
            giftExpectation: .expected,
            defaultReminderOn: false,
            recommendedLeadDays: [14, 7, 3, 1, 0],
            bookingAdvised: true,
            culturalTaboosVi: ["Dịp đáp lễ tinh tế — ưu tiên sự chân thành hơn giá tiền"],
            culturalTaboosEn: ["Reciprocation day — prioritize thoughtfulness over price"],
            prepChecklistVi: ["Chuẩn bị quà ngọt hoặc hẹn hò đáp lễ sau 14/2"],
            prepChecklistEn: ["Prepare a sweet reciprocation gift or cozy date"]
        ),
        OccasionProfile(
            id: "that_tich",
            titleVi: "Lễ Thất Tịch (7/7 ÂL)",
            titleEn: "Thất Tịch Romance Day",
            coupleRelevance: .core,
            giftExpectation: .small,
            defaultReminderOn: true,
            recommendedLeadDays: [7, 3, 1, 0],
            bookingAdvised: false,
            culturalTaboosVi: ["Kiểm tra dị ứng đậu đỏ/sữa trước khi mua chè đậu đỏ đôi"],
            culturalTaboosEn: ["Check bean or dairy allergies before buying sweet treats"],
            prepChecklistVi: ["Rủ người thương ăn chè đậu đỏ hoặc hẹn hò ngắm trăng tối 7/7 ÂL"],
            prepChecklistEn: ["Share red-bean dessert or an evening stargazing date"]
        ),
        OccasionProfile(
            id: "vn_women_day_2010",
            titleVi: "Phụ Nữ Việt Nam 20/10",
            titleEn: "Vietnamese Women's Day",
            coupleRelevance: .core,
            giftExpectation: .expected,
            defaultReminderOn: true,
            recommendedLeadDays: [14, 7, 3, 1, 0],
            bookingAdvised: true,
            culturalTaboosVi: ["Tránh đặt hoa quá sát ngày 20/10 gây trễ giờ"],
            culturalTaboosEn: ["Avoid last-minute flower orders on peak day"],
            prepChecklistVi: ["Chọn quà hợp sở thích làm đẹp/hoa/hẹn hò", "Đặt lịch hẹn tối"],
            prepChecklistEn: ["Pick a gift aligned with beauty, flowers, or dining", "Reserve a dinner table"]
        ),
        OccasionProfile(
            id: "christmas_eve",
            titleVi: "Đêm Giáng Sinh 24/12",
            titleEn: "Christmas Eve",
            coupleRelevance: .core,
            giftExpectation: .expected,
            defaultReminderOn: true,
            recommendedLeadDays: [21, 14, 7, 3, 1, 0],
            bookingAdvised: true,
            culturalTaboosVi: ["Giữ không khí ấm áp, tôn trọng niềm tin cá nhân của đối phương"],
            culturalTaboosEn: ["Keep tone warm and respectful of personal beliefs"],
            prepChecklistVi: ["Chọn quà mùa đông (khăn choàng, nến thơm)", "Đặt chỗ ăn tối trước lễ 2 tuần"],
            prepChecklistEn: ["Pick cozy seasonal gift (scarf, scented candle)", "Book holiday dinner 2 weeks ahead"]
        ),
        OccasionProfile(
            id: "trung_thu",
            titleVi: "Tết Trung Thu (15/8 ÂL)",
            titleEn: "Mid-Autumn Festival",
            coupleRelevance: .relevant,
            giftExpectation: .small,
            defaultReminderOn: true,
            recommendedLeadDays: [14, 7, 3, 1, 0],
            bookingAdvised: false,
            culturalTaboosVi: ["Lưu ý nhân bánh dị ứng (đậu phộng, trứng muối, sữa)"],
            culturalTaboosEn: ["Check mooncake fillings for nut/egg/dairy allergies"],
            prepChecklistVi: ["Chọn hộp bánh trung thu nhỏ xinh hoặc trà thưởng trăng"],
            prepChecklistEn: ["Pick artisan mooncakes or tea set"]
        ),
        OccasionProfile(
            id: "ram_thang_gieng",
            titleVi: "Rằm Tháng Giêng (15/1 ÂL)",
            titleEn: "First Full Moon Festival",
            coupleRelevance: .none,
            giftExpectation: .none,
            defaultReminderOn: false,
            recommendedLeadDays: [3, 1],
            bookingAdvised: false,
            culturalTaboosVi: [],
            culturalTaboosEn: [],
            prepChecklistVi: ["Ngày lễ gia đình truyền thống — không cần quà cặp đôi"],
            prepChecklistEn: ["Traditional family observance — no couple gift expected"]
        ),
    ]

    public func preparationStage(daysRemaining: Int, profile: OccasionProfile) -> PreparationStage {
        let maxLead = profile.recommendedLeadDays.max() ?? 7
        if daysRemaining <= 0 { return .todayCelebration }
        if daysRemaining <= 3 { return .urgentLastMinute }
        if daysRemaining <= 7 { return .actionNeeded }
        if daysRemaining <= 21 && (profile.bookingAdvised || profile.giftExpectation == .major || daysRemaining <= maxLead) {
            return .planAheadBooking
        }
        return .relaxed
    }

    public func upcomingOccasions(
        referenceDate: Date = Date(),
        startDate: Date? = nil,
        region: String = "VN"
    ) -> [NextOccasionReadiness] {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = calculator.timeZone
        let refDay = cal.startOfDay(for: referenceDate)
        let refYear = cal.component(.year, from: refDay)

        var items: [NextOccasionReadiness] = []

        func appendFixed(_ id: String, month: Int, day: Int) {
            guard let profile = Self.coupleOccasionProfiles.first(where: { $0.id == id }) else { return }
            if profile.coupleRelevance == .none { return }
            for year in refYear...(refYear + 1) {
                var comps = DateComponents()
                comps.year = year
                comps.month = month
                comps.day = day
                guard let date = cal.date(from: comps) else { continue }
                let remaining = calculator.daysRemaining(until: date, from: refDay)
                guard remaining >= 0 else { continue }
                items.append(buildReadiness(profile: profile, date: date, daysRemaining: remaining))
                break
            }
        }

        func appendLunar(_ id: String, lunarId: LunarHolidayId) {
            guard let profile = Self.coupleOccasionProfiles.first(where: { $0.id == id }) else { return }
            for year in refYear...(refYear + 1) {
                guard let iso = LunarCalendar2025To2040.solarIsoDate(for: lunarId, year: year),
                      let date = calculator.parseDate(iso) else { continue }
                let remaining = calculator.daysRemaining(until: date, from: refDay)
                guard remaining >= 0 else { continue }
                items.append(buildReadiness(profile: profile, date: date, daysRemaining: remaining))
                break
            }
        }

        appendFixed("valentine", month: 2, day: 14)
        appendFixed("women_day_0803", month: 3, day: 8)
        appendFixed("white_day", month: 3, day: 14)
        appendFixed("vn_women_day_2010", month: 10, day: 20)
        appendFixed("christmas_eve", month: 12, day: 24)
        if region.uppercased() != "INTL" {
            appendLunar("that_tich", lunarId: .thatTich)
            appendLunar("trung_thu", lunarId: .trungThu)
        }

        if let startDate, let nextMs = calculator.upcomingMilestones(startDate: startDate, referenceDate: refDay).first,
           let msDate = calculator.parseDate(nextMs.targetDateIso) {
            let msProfile = OccasionProfile(
                id: "milestone_\(nextMs.value)",
                titleVi: nextMs.titleVi,
                titleEn: nextMs.titleEn,
                coupleRelevance: .core,
                giftExpectation: .expected,
                defaultReminderOn: true,
                recommendedLeadDays: [14, 7, 3, 1, 0],
                bookingAdvised: true,
                culturalTaboosVi: [],
                culturalTaboosEn: [],
                prepChecklistVi: ["Chụp ảnh kỷ niệm và lưu khoảnh khắc cột mốc"],
                prepChecklistEn: ["Capture a memory photo for your couple milestone"]
            )
            items.append(buildReadiness(profile: msProfile, date: msDate, daysRemaining: nextMs.daysRemaining, isMilestone: true))
        }

        return items.sorted { lhs, rhs in
            if lhs.daysRemaining != rhs.daysRemaining {
                return lhs.daysRemaining < rhs.daysRemaining
            }
            return lhs.coupleRelevance > rhs.coupleRelevance
        }
    }

    public func nextOccasionReadiness(
        referenceDate: Date = Date(),
        startDate: Date? = nil,
        region: String = "VN"
    ) -> NextOccasionReadiness? {
        upcomingOccasions(referenceDate: referenceDate, startDate: startDate, region: region)
            .first { $0.coupleRelevance >= .relevant }
    }

    public func buildReadiness(
        profile: OccasionProfile,
        date: Date,
        daysRemaining: Int,
        isMilestone: Bool = false
    ) -> NextOccasionReadiness {
        let stage = preparationStage(daysRemaining: daysRemaining, profile: profile)
        let recommendedLead = profile.recommendedLeadDays.first { $0 >= daysRemaining }
            ?? profile.recommendedLeadDays.max()
            ?? 7

        let (headVi, headEn, actVi, actEn): (String, String, String, String)
        switch stage {
        case .todayCelebration:
            headVi = "Hôm nay là \(profile.titleVi)!"
            headEn = "Today is \(profile.titleEn)!"
            actVi = "Tận hưởng khoảnh khắc đặc biệt bên người thương — không quảng cáo làm phiền."
            actEn = "Celebrate together today — ad-free sacred moment."
        case .urgentLastMinute:
            headVi = "Chỉ còn \(daysRemaining) ngày tới \(profile.titleVi)"
            headEn = "\(daysRemaining) days left until \(profile.titleEn)"
            actVi = "Ưu tiên quà có sẵn, thiệp viết tay hoặc đặt bàn ngay hôm nay."
            actEn = "Pick ready-to-buy gifts, handwritten notes, or reserve tonight."
        case .actionNeeded:
            headVi = "Còn \(daysRemaining) ngày tới \(profile.titleVi)"
            headEn = "\(daysRemaining) days until \(profile.titleEn)"
            actVi = "Thời điểm đẹp để chốt quà và lịch hẹn trước khi quá tải."
            actEn = "Great window to finalize your gift and date plan."
        case .planAheadBooking:
            headVi = "Còn \(daysRemaining) ngày — Nên đặt sớm cho \(profile.titleVi)"
            headEn = "\(daysRemaining) days away — Plan ahead for \(profile.titleEn)"
            actVi = profile.bookingAdvised ? "Nên đặt chỗ nhà hàng hoặc quà cá nhân hóa từ bây giờ." : "Thong thả chọn quà đúng sở thích từ Hồ sơ người thương."
            actEn = profile.bookingAdvised ? "Book dining or custom gifts early to get the best slot." : "Browse tailored gift ideas from Partner Dossier."
        case .relaxed:
            headVi = "Còn \(daysRemaining) ngày tới \(profile.titleVi)"
            headEn = "\(daysRemaining) days until \(profile.titleEn)"
            actVi = "Lưu dần gợi ý bí mật khi người ấy nhắc tới điều mình thích."
            actEn = "Save secret hints whenever they mention something they love."
        }

        return NextOccasionReadiness(
            occasionId: profile.id,
            titleVi: profile.titleVi,
            titleEn: profile.titleEn,
            targetDateIso: calculator.formatIso(date),
            daysRemaining: daysRemaining,
            stage: stage,
            coupleRelevance: profile.coupleRelevance,
            giftExpectation: profile.giftExpectation,
            bookingAdvised: profile.bookingAdvised,
            recommendedLeadDays: recommendedLead,
            headlineVi: headVi,
            headlineEn: headEn,
            actionSummaryVi: actVi,
            actionSummaryEn: actEn,
            prepChecklistVi: profile.prepChecklistVi,
            prepChecklistEn: profile.prepChecklistEn,
            culturalTaboosVi: profile.culturalTaboosVi,
            culturalTaboosEn: profile.culturalTaboosEn,
            isMilestone: isMilestone
        )
    }
}
