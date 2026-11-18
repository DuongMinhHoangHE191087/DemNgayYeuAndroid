import Foundation

public struct LoveDaysCalculator: Sendable {
    public static let milestoneDays: [Int] = [
        7, 14, 22, 30, 50, 52, 99, 100, 200, 300,
        365, 500, 520, 730, 1000, 1095, 1314, 1825, 2000, 3650
    ]

    public let timeZone: TimeZone

    public init(timeZone: TimeZone = TimeZone(identifier: "Asia/Ho_Chi_Minh") ?? .current) {
        self.timeZone = timeZone
    }

    private var gregorian: Calendar {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = timeZone
        cal.locale = Locale(identifier: "en_US_POSIX")
        return cal
    }

    public func parseDate(_ text: String) -> Date? {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return nil }
        for format in ["yyyy-MM-dd", "dd/MM/yyyy", "d/M/yyyy", "dd-MM-yyyy"] {
            let formatter = DateFormatter()
            formatter.calendar = gregorian
            formatter.locale = Locale(identifier: "en_US_POSIX")
            formatter.timeZone = timeZone
            formatter.isLenient = false
            formatter.dateFormat = format
            if let date = formatter.date(from: trimmed) {
                let year = gregorian.component(.year, from: date)
                if year >= 1900 {
                    return gregorian.startOfDay(for: date)
                }
            }
        }
        return nil
    }

    public func formatIso(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.calendar = gregorian
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = timeZone
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter.string(from: date)
    }

    /// Day 1 = startDate, so same-day returns 1 and future startDate returns 0.
    public func loveDays(from startDate: Date, to referenceDate: Date = Date()) -> Int {
        let start = gregorian.startOfDay(for: startDate)
        let ref = gregorian.startOfDay(for: referenceDate)
        if ref < start { return 0 }
        let delta = gregorian.dateComponents([.day], from: start, to: ref).day ?? 0
        return max(1, delta + 1)
    }

    /// Day 1 = startDate, fallback = 1 when blank/unparsable (matching GV-001-love-days).
    public func loveDays(startDateText: String, referenceDate: Date = Date()) -> Int {
        guard let parsed = parseDate(startDateText) else { return 1 }
        return loveDays(from: parsed, to: referenceDate)
    }

    /// Day N falls on startDate + (N - 1) days.
    public func dateForMilestone(days: Int, from startDate: Date) -> Date {
        let start = gregorian.startOfDay(for: startDate)
        let offset = max(0, days - 1)
        return gregorian.date(byAdding: .day, value: offset, to: start) ?? start
    }

    public func daysRemaining(until targetDate: Date, from referenceDate: Date = Date()) -> Int {
        let start = gregorian.startOfDay(for: referenceDate)
        let target = gregorian.startOfDay(for: targetDate)
        return gregorian.dateComponents([.day], from: start, to: target).day ?? 0
    }

    public func upcomingMilestones(
        startDate: Date,
        referenceDate: Date = Date(),
        includeMonthiversaries: Bool = true
    ) -> [LoveMilestoneInfo] {
        let refDay = gregorian.startOfDay(for: referenceDate)
        let startDay = gregorian.startOfDay(for: startDate)
        var results: [LoveMilestoneInfo] = []

        for days in Self.milestoneDays {
            let target = dateForMilestone(days: days, from: startDay)
            let remaining = daysRemaining(until: target, from: refDay)
            guard remaining >= 0 else { continue }
            results.append(
                LoveMilestoneInfo(
                    value: days,
                    titleVi: milestoneTitleVi(days: days),
                    titleEn: milestoneTitleEn(days: days),
                    targetDateIso: formatIso(target),
                    daysRemaining: remaining,
                    isMonthiversary: false
                )
            )
        }

        if includeMonthiversaries {
            let startComponents = gregorian.dateComponents([.year, .month, .day], from: startDay)
            let monthsElapsed = max(0, gregorian.dateComponents([.month], from: startDay, to: refDay).month ?? 0)
            for m in max(1, monthsElapsed)...(monthsElapsed + 6) {
                guard let candidate = clampedMonthlyDate(startComponents: startComponents, monthsToAdd: m) else { continue }
                let remaining = daysRemaining(until: candidate, from: refDay)
                guard remaining >= 0 else { continue }
                results.append(
                    LoveMilestoneInfo(
                        value: m,
                        titleVi: "Kỷ niệm \(m) tháng bên nhau",
                        titleEn: "\(m)-Month Anniversary",
                        targetDateIso: formatIso(candidate),
                        daysRemaining: remaining,
                        isMonthiversary: true
                    )
                )
            }
        }

        return results.sorted { lhs, rhs in
            if lhs.daysRemaining != rhs.daysRemaining {
                return lhs.daysRemaining < rhs.daysRemaining
            }
            return lhs.value < rhs.value
        }
    }

    private func clampedMonthlyDate(startComponents: DateComponents, monthsToAdd: Int) -> Date? {
        guard let y = startComponents.year, let m = startComponents.month, let d = startComponents.day else {
            return nil
        }
        var base = DateComponents()
        base.year = y
        base.month = m
        base.day = 1
        guard let firstOfStartMonth = gregorian.date(from: base),
              let targetMonthDate = gregorian.date(byAdding: .month, value: monthsToAdd, to: firstOfStartMonth),
              let dayRange = gregorian.range(of: .day, in: .month, for: targetMonthDate) else {
            return nil
        }
        var targetComps = gregorian.dateComponents([.year, .month], from: targetMonthDate)
        targetComps.day = min(d, dayRange.count)
        return gregorian.date(from: targetComps).map { gregorian.startOfDay(for: $0) }
    }

    private func milestoneTitleVi(days: Int) -> String {
        switch days {
        case 7: return "7 Ngày Đầu Ngọt Ngào"
        case 14: return "14 Ngày Hẹn Hò"
        case 22: return "22 Ngày Gần Nhau"
        case 30: return "1 Tháng Yêu Nhau (30 Ngày)"
        case 50: return "50 Ngày Gắn Kết"
        case 52: return "52 Ngày Yêu Thương"
        case 99: return "99 Ngày Dài Lâu"
        case 100: return "Bách Nhật Yêu (100 Ngày)"
        case 200: return "200 Ngày Nồng Nàn"
        case 300: return "300 Ngày Chung Đôi"
        case 365: return "Tròn 1 Năm Yêu (365 Ngày)"
        case 500: return "500 Ngày Tri Kỷ"
        case 520: return "520 Ngày 'Anh Yêu Em'"
        case 730: return "Tròn 2 Năm Yêu (730 Ngày)"
        case 1000: return "1000 Ngày Trọn Vẹn"
        case 1095: return "Tròn 3 Năm Yêu (1095 Ngày)"
        case 1314: return "1314 Ngày Trọn Đời Trọn Kiếp"
        case 1825: return "5 Năm Chung Bước (1825 Ngày)"
        case 2000: return "2000 Ngày Hạnh Phúc"
        case 3650: return "10 Năm Vàng Son (3650 Ngày)"
        default: return "\(days) Ngày Yêu Nhau"
        }
    }

    private func milestoneTitleEn(days: Int) -> String {
        switch days {
        case 7: return "7 Sweet Days"
        case 14: return "14 Days Dating"
        case 22: return "22 Days Closer"
        case 30: return "1 Month Together (30 Days)"
        case 50: return "50 Days Connected"
        case 52: return "52 Days in Love"
        case 99: return "99 Days Eternal"
        case 100: return "100 Days in Love"
        case 200: return "200 Days of Passion"
        case 300: return "300 Days Together"
        case 365: return "1 Year Anniversary (365 Days)"
        case 500: return "500 Days Soulmates"
        case 520: return "520 Days 'I Love You'"
        case 730: return "2 Years Together (730 Days)"
        case 1000: return "1000 Days Full of Love"
        case 1095: return "3 Years Together (1095 Days)"
        case 1314: return "1314 Days Forever"
        case 1825: return "5 Years Together (1825 Days)"
        case 2000: return "2000 Days of Happiness"
        case 3650: return "10 Golden Years (3650 Days)"
        default: return "\(days) Days in Love"
        }
    }
}
