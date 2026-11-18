import InLoveIosCore
import SwiftUI

public struct CalendarMilestonesView: View {
    @EnvironmentObject private var env: AppEnvironment
    @State private var filter: CalendarFilter = .all
    @State private var expandedOccasionId: String?

    enum CalendarFilter: CaseIterable {
        case all
        case milestones
        case holidays

        func title(isEnglish: Bool) -> String {
            switch self {
            case .all: return isEnglish ? "All" : "Tất cả"
            case .milestones: return isEnglish ? "Milestones" : "Cột mốc"
            case .holidays: return isEnglish ? "Holidays" : "Ngày lễ"
            }
        }
    }

    public init() {}

    public var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: InLoveMetrics.sectionGap) {
                    filterSegmentedRow
                    if filter != .holidays {
                        milestonesSection
                    }
                    if filter != .milestones {
                        occasionsSection
                    }
                    lunar2027PreviewNote
                }
                .padding(.horizontal, InLoveMetrics.screenMargin)
                .padding(.vertical, 12)
            }
            .background(InLoveColors.softBlush.ignoresSafeArea())
            .navigationTitle(env.isEnglish ? "Love Calendar" : "Lịch Kỷ Niệm")
        }
    }

    private var filterSegmentedRow: some View {
        HStack(spacing: 8) {
            ForEach(CalendarFilter.allCases, id: \.self) { item in
                Button {
                    filter = item
                } label: {
                    Text(item.title(isEnglish: env.isEnglish))
                        .font(.subheadline.weight(.medium))
                        .foregroundStyle(filter == item ? InLoveColors.pearl : InLoveColors.deepPlum)
                        .frame(maxWidth: .infinity, minHeight: InLoveMetrics.minTapTarget)
                        .background(
                            Capsule().fill(filter == item ? InLoveColors.rose : InLoveColors.pearl)
                        )
                        .overlay(
                            Capsule().stroke(InLoveColors.cardBorder, lineWidth: filter == item ? 0 : 1.0)
                        )
                }
                .inLoveTapTarget()
            }
        }
    }

    private var milestonesSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(env.isEnglish ? "Upcoming milestones" : "Cột mốc sắp tới")
                .font(InLoveTypography.serifHeadline)
                .foregroundStyle(InLoveColors.deepPlum)

            if env.upcomingMilestones.isEmpty {
                emptySectionCard(
                    env.isEnglish
                        ? "Set your love start date in Settings to track 100, 365, and 1,000-day milestones."
                        : "Cập nhật ngày bắt đầu yêu trong Cài đặt để theo dõi các mốc 100, 365 và 1.000 ngày."
                )
            } else {
                ForEach(Array(env.upcomingMilestones.prefix(8))) { milestone in
                    HStack(spacing: 12) {
                        Image(systemName: milestone.isMonthiversary ? "calendar" : "heart")
                            .font(.body)
                            .foregroundStyle(InLoveColors.rose)
                            .frame(width: 40, height: 40)
                            .background(Circle().fill(InLoveColors.surfaceContainerLow))

                        VStack(alignment: .leading, spacing: 4) {
                            Text(cleanMilestoneTitle(milestone.localizedTitle(isEnglish: env.isEnglish)))
                                .font(.subheadline.weight(.semibold))
                                .foregroundStyle(InLoveColors.deepPlum)
                            Text(InLoveDateFormatting.displayDate(iso: milestone.targetDateIso, isEnglish: env.isEnglish))
                                .font(InLoveTypography.metadataCaption)
                                .foregroundStyle(InLoveColors.subtlePlum)
                        }
                        Spacer()
                        Text(
                            milestone.daysRemaining == 0
                                ? (env.isEnglish ? "Today" : "Hôm nay")
                                : (env.isEnglish ? "\(milestone.daysRemaining)d left" : "Còn \(milestone.daysRemaining) ngày")
                        )
                        .font(.caption.weight(.semibold))
                        .monospacedDigit()
                        .foregroundStyle(InLoveColors.rose)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 6)
                        .background(Capsule().fill(InLoveColors.surfaceContainerLow))
                    }
                    .inLoveCard()
                }
            }
        }
    }

    private var occasionsSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(env.isEnglish ? "Holidays & preparation" : "Dịp lễ & thời gian chuẩn bị")
                .font(InLoveTypography.serifHeadline)
                .foregroundStyle(InLoveColors.deepPlum)

            if env.upcomingOccasions.isEmpty {
                emptySectionCard(
                    env.isEnglish
                        ? "No upcoming holidays match your current region window."
                        : "Chưa có dịp lễ nào sắp diễn ra trong khoảng thời gian này."
                )
            } else {
                ForEach(env.upcomingOccasions) { item in
                    let expanded = expandedOccasionId == item.id
                    VStack(alignment: .leading, spacing: 8) {
                        Button {
                            withAnimation(.easeOut(duration: 0.2)) {
                                expandedOccasionId = expanded ? nil : item.id
                            }
                        } label: {
                            HStack {
                                VStack(alignment: .leading, spacing: 4) {
                                    Text(item.localizedTitle(isEnglish: env.isEnglish))
                                        .font(.subheadline.weight(.semibold))
                                        .foregroundStyle(InLoveColors.deepPlum)
                                    let formattedDate = InLoveDateFormatting.displayDate(iso: item.targetDateIso, isEnglish: env.isEnglish)
                                    Text("\(formattedDate) · \(item.stage.badgeTitle(isEnglish: env.isEnglish))")
                                        .font(InLoveTypography.metadataCaption)
                                        .foregroundStyle(InLoveColors.subtlePlum)
                                }
                                Spacer()
                                VStack(alignment: .trailing, spacing: 4) {
                                    Text(
                                        item.daysRemaining == 0
                                            ? (env.isEnglish ? "Today" : "Hôm nay")
                                            : (env.isEnglish ? "\(item.daysRemaining)d" : "\(item.daysRemaining) ngày")
                                    )
                                    .font(.caption.weight(.semibold))
                                    .monospacedDigit()
                                    .foregroundStyle(InLoveColors.rose)
                                    Text(env.isEnglish ? "Prep \(item.recommendedLeadDays)d ahead" : "Chuẩn bị trước \(item.recommendedLeadDays) ngày")
                                        .font(.caption2)
                                        .foregroundStyle(InLoveColors.subtlePlum)
                                }
                            }
                            .frame(minHeight: InLoveMetrics.minTapTarget)
                        }
                        .inLoveTapTarget()

                        if expanded {
                            Divider()
                            if item.bookingAdvised {
                                Label(
                                    env.isEnglish ? "Advance reservation recommended" : "Nên đặt bàn hoặc quà sớm trước ngày lễ",
                                    systemImage: "calendar.badge.clock"
                                )
                                .font(.caption.weight(.medium))
                                .foregroundStyle(InLoveColors.rose)
                            }
                            ForEach(item.localizedChecklist(isEnglish: env.isEnglish), id: \.self) { step in
                                Label(step, systemImage: "checkmark.circle")
                                    .font(.caption)
                                    .foregroundStyle(InLoveColors.deepPlum)
                            }
                            ForEach(item.localizedTaboos(isEnglish: env.isEnglish), id: \.self) { taboo in
                                Label(taboo, systemImage: "exclamationmark.circle")
                                    .font(.caption)
                                    .foregroundStyle(InLoveColors.warningAmber)
                            }
                        }
                    }
                    .inLoveCard()
                }
            }
        }
    }

    private var lunar2027PreviewNote: some View {
        let ram2027Iso = LunarCalendar2025To2040.solarIsoDate(for: .ramThangGieng, year: 2027) ?? "2027-02-20"
        let thatTich2027Iso = LunarCalendar2025To2040.solarIsoDate(for: .thatTich, year: 2027) ?? "2027-08-08"
        let ram2027 = InLoveDateFormatting.displayDate(iso: ram2027Iso, isEnglish: env.isEnglish)
        let thatTich2027 = InLoveDateFormatting.displayDate(iso: thatTich2027Iso, isEnglish: env.isEnglish)
        return VStack(alignment: .leading, spacing: 4) {
            Text(env.isEnglish ? "Lunar calendar reference (2025–2040)" : "Lịch âm đối chiếu (2025–2040)")
                .font(InLoveTypography.serifHeadline)
                .foregroundStyle(InLoveColors.deepPlum)
            Text(
                env.isEnglish
                    ? "Qixi (7/7 Lunar) 2027: \(thatTich2027) · First Full Moon 2027: \(ram2027)"
                    : "Thất Tịch 7/7 ÂL 2027: \(thatTich2027) · Rằm tháng Giêng 2027: \(ram2027)"
            )
            .font(InLoveTypography.metadataCaption)
            .foregroundStyle(InLoveColors.subtlePlum)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .inLoveCard(background: InLoveColors.surfaceContainerLow)
    }

    private func emptySectionCard(_ message: String) -> some View {
        Text(message)
            .font(.subheadline)
            .foregroundStyle(InLoveColors.subtlePlum)
            .frame(maxWidth: .infinity, alignment: .leading)
            .inLoveCard()
    }

    private func cleanMilestoneTitle(_ raw: String) -> String {
        let filtered = raw.unicodeScalars.filter { scalar in
            let v = scalar.value
            let isEmojiRange = (0x1F300...0x1FAFF).contains(v) || (0x2600...0x27BF).contains(v) || v == 0xFE0F || v == 0x200D
            return !isEmojiRange
        }
        return String(String.UnicodeScalarView(filtered)).trimmingCharacters(in: .whitespacesAndNewlines)
    }
}
