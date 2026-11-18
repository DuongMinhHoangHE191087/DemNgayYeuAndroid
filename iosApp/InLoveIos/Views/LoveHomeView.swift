import InLoveIosCore
import SwiftUI

public struct LoveHomeView: View {
    @EnvironmentObject private var env: AppEnvironment
    @State private var pulseScale: CGFloat = 1.0
    @State private var ringOpacity: Double = 0.45
    @State private var reflectionIndex: Int = 0

    private static let reflectionsVi: [String] = [
        "Điều dịu dàng nhất là nhớ một thói quen nhỏ của người thương mà không cần nhắc.",
        "Hạnh phúc đôi khi chỉ là cùng nhau đi qua một ngày bình thường thật yên ả.",
        "Một lời hỏi thăm giữa ngày bận rộn có thể làm cả buổi tối ấm lại.",
        "Lưu lại một điều nhỏ người ấy từng thích hôm nay để dành cho dịp đặc biệt tới.",
        "Cảm ơn vì đã luôn kiên nhẫn lắng nghe nhau suốt chặng đường vừa qua.",
        "Tình yêu bền lâu được dệt từ những buổi hẹn nhỏ nhưng trọn vẹn sự hiện diện."
    ]

    private static let reflectionsEn: [String] = [
        "Remembering a small habit without being asked is the quietest form of care.",
        "Some of the best days together are ordinary evenings spent unhurried.",
        "A short check-in during a busy afternoon can warm the whole evening.",
        "Jot down one small wish they mentioned today for your next occasion.",
        "Thank you for staying patient and gentle with each other along the way.",
        "Lasting love grows in quiet dates where both of you are truly present."
    ]

    public init() {}

    public var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: InLoveMetrics.sectionGap) {
                    headerBanner
                    coupleAvatarsAndCounterCard
                    if env.isCelebrationDay {
                        sacredCelebrationBanner
                    }
                    if let readiness = env.nextReadiness {
                        nextOccasionReadinessCard(readiness)
                    }
                    quickActionsRow
                    coupleQuoteCard
                }
                .padding(.horizontal, InLoveMetrics.screenMargin)
                .padding(.vertical, 12)
            }
            .background(InLoveColors.softBlush.ignoresSafeArea())
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button {
                        env.showGuideSheet = true
                    } label: {
                        Image(systemName: "book")
                            .font(.body.weight(.medium))
                            .foregroundStyle(InLoveColors.deepPlum)
                    }
                    .inLoveTapTarget()
                    .accessibilityLabel(env.isEnglish ? "Open InLove guide" : "Mở hướng dẫn sử dụng")
                }
                ToolbarItem(placement: .principal) {
                    Text(env.coupleTitle.isEmpty ? "InLove" : env.coupleTitle)
                        .font(InLoveTypography.serifHeadline)
                        .foregroundStyle(InLoveColors.deepPlum)
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        env.presentPaywall()
                    } label: {
                        Text(env.isPro ? "InLove Pro" : (env.isEnglish ? "InLove Pro" : "Gói Pro"))
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(env.isPro ? InLoveColors.vipGoldText : InLoveColors.rose)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 6)
                            .background(
                                Capsule().fill(env.isPro ? InLoveColors.vipGoldContainer : InLoveColors.surfaceContainerLow)
                            )
                    }
                    .inLoveTapTarget()
                }
            }
        }
    }

    private var headerBanner: some View {
        HStack(alignment: .center) {
            VStack(alignment: .leading, spacing: 4) {
                Text("\(env.partner1Name) & \(env.partner2Name)")
                    .font(InLoveTypography.serifTitle)
                    .foregroundStyle(InLoveColors.deepPlum)
                Text(env.isEnglish ? "Together since \(formatDisplayDate(env.startDateIso))" : "Bên nhau từ \(formatDisplayDate(env.startDateIso))")
                    .font(InLoveTypography.metadataCaption)
                    .foregroundStyle(InLoveColors.subtlePlum)
            }
            Spacer()
            Button {
                env.isEnglish.toggle()
            } label: {
                Text(env.isEnglish ? "English" : "Tiếng Việt")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(InLoveColors.deepPlum)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 8)
                    .background(Capsule().fill(InLoveColors.pearl))
                    .overlay(Capsule().stroke(InLoveColors.cardBorder, lineWidth: 1.0))
            }
            .inLoveTapTarget()
            .accessibilityLabel(env.isEnglish ? "Switch language" : "Đổi ngôn ngữ")
        }
    }

    private var coupleAvatarsAndCounterCard: some View {
        VStack(spacing: 16) {
            HStack(spacing: 20) {
                avatarBadge(name: env.partner1Name, symbol: "person", tint: InLoveColors.rose)
                ZStack {
                    Circle()
                        .stroke(InLoveColors.rose.opacity(ringOpacity), lineWidth: 1.5)
                        .frame(width: 64, height: 64)
                        .scaleEffect(pulseScale)
                    Image(systemName: "heart.fill")
                        .font(.system(size: 28, weight: .semibold))
                        .foregroundStyle(InLoveColors.rose)
                }
                .onAppear {
                    withAnimation(.easeOut(duration: 1.8).repeatForever(autoreverses: false)) {
                        pulseScale = 1.55
                        ringOpacity = 0.0
                    }
                }
                avatarBadge(name: env.partner2Name, symbol: "person", tint: InLoveColors.secondaryRose)
            }

            VStack(spacing: 4) {
                Text("\(env.loveDays)")
                    .font(InLoveTypography.heroDisplay)
                    .monospacedDigit()
                    .foregroundStyle(InLoveColors.deepPlum)
                    .contentTransition(.numericText())
                Text(env.isEnglish ? "days in love" : "ngày yêu nhau")
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(InLoveColors.subtlePlum)
            }
        }
        .frame(maxWidth: .infinity)
        .inLoveCard()
    }

    private func avatarBadge(name: String, symbol: String, tint: Color) -> some View {
        VStack(spacing: 6) {
            Circle()
                .fill(InLoveColors.surfaceContainerLow)
                .frame(width: 56, height: 56)
                .overlay(
                    Image(systemName: symbol)
                        .font(.title3)
                        .foregroundStyle(tint)
                )
                .overlay(Circle().stroke(InLoveColors.cardBorder, lineWidth: 1.0))
            Text(name)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(InLoveColors.deepPlum)
                .lineLimit(1)
        }
        .frame(minWidth: 80)
    }

    private var sacredCelebrationBanner: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(env.isEnglish ? "Special celebration day" : "Hôm nay là ngày kỷ niệm")
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(InLoveColors.deepPlum)
            Text(env.isEnglish ? "Quiet ad-free space enabled for the two of you today." : "Không gian hôm nay được giữ yên tĩnh, không quảng cáo làm phiền.")
                .font(.caption)
                .foregroundStyle(InLoveColors.subtlePlum)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .inLoveCard(background: InLoveColors.surfaceContainerLow, borderColor: InLoveColors.rose)
    }

    private func nextOccasionReadinessCard(_ readiness: NextOccasionReadiness) -> some View {
        let formattedDate = formatDisplayDate(readiness.targetDateIso)
        let countdownText = readiness.daysRemaining == 0
            ? (env.isEnglish ? "Today" : "Hôm nay")
            : (env.isEnglish ? "\(readiness.daysRemaining) days left" : "Còn \(readiness.daysRemaining) ngày")

        return VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .top, spacing: 8) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(readiness.localizedTitle(isEnglish: env.isEnglish))
                        .font(InLoveTypography.serifHeadline)
                        .foregroundStyle(InLoveColors.deepPlum)
                    Text("\(formattedDate) · \(countdownText)")
                        .font(InLoveTypography.metadataCaption)
                        .foregroundStyle(InLoveColors.subtlePlum)
                }
                Spacer()
                Text(stageLabel(readiness.stage))
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(InLoveColors.rose)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 4)
                    .background(Capsule().fill(InLoveColors.surfaceContainerLow))
            }

            if readiness.daysRemaining == 0 {
                Text(readiness.localizedActionSummary(isEnglish: env.isEnglish))
                    .font(.subheadline)
                    .foregroundStyle(InLoveColors.subtlePlum)
            }

            HStack(spacing: 12) {
                Button(env.isEnglish ? "Find a gift" : "Chọn quà") {
                    env.selectedTab = .gifts
                }
                .buttonStyle(InLovePrimaryButtonStyle())
                .inLoveTapTarget()

                Button(env.isEnglish ? "View checklist" : "Xem lịch") {
                    env.selectedTab = .calendar
                }
                .buttonStyle(InLoveSecondaryButtonStyle())
                .inLoveTapTarget()
            }
        }
        .inLoveCard()
    }

    private var quickActionsRow: some View {
        HStack(spacing: 8) {
            quickActionButton(
                title: env.isEnglish ? "Dossier" : "Hồ sơ",
                icon: "heart.text.clipboard"
            ) {
                env.selectedTab = .gifts
            }
            quickActionButton(
                title: env.isEnglish ? "Milestones" : "Cột mốc",
                icon: "calendar"
            ) {
                env.selectedTab = .calendar
            }
            quickActionButton(
                title: env.isEnglish ? "Memories" : "Kỷ niệm",
                icon: "photo"
            ) {
                env.selectedTab = .memories
            }
            quickActionButton(
                title: env.isEnglish ? "Settings" : "Cài đặt",
                icon: "gearshape"
            ) {
                env.selectedTab = .settings
            }
        }
    }

    private func quickActionButton(title: String, icon: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            VStack(spacing: 6) {
                Image(systemName: icon)
                    .font(.body)
                    .foregroundStyle(InLoveColors.rose)
                Text(title)
                    .font(.caption.weight(.medium))
                    .foregroundStyle(InLoveColors.deepPlum)
                    .lineLimit(1)
            }
            .frame(maxWidth: .infinity, minHeight: 60)
            .padding(.horizontal, 4)
            .background(
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .fill(InLoveColors.pearl)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .stroke(InLoveColors.cardBorder, lineWidth: 1.0)
            )
        }
        .inLoveTapTarget()
    }

    private var coupleQuoteCard: some View {
        let items = env.isEnglish ? Self.reflectionsEn : Self.reflectionsVi
        let current = items[reflectionIndex % items.count]
        return VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text(env.isEnglish ? "Daily reflection" : "Lời nhắn cho hôm nay")
                    .font(InLoveTypography.serifHeadline)
                    .foregroundStyle(InLoveColors.rose)
                Spacer()
                Button(env.isEnglish ? "Next" : "Đổi câu") {
                    reflectionIndex = (reflectionIndex + 1) % items.count
                }
                .font(.caption.weight(.semibold))
                .foregroundStyle(InLoveColors.subtlePlum)
                .inLoveTapTarget()
            }
            Text(current)
                .font(InLoveTypography.serifReflection)
                .foregroundStyle(InLoveColors.deepPlum)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .inLoveCard()
    }

    private func formatDisplayDate(_ isoDate: String) -> String {
        let parts = isoDate.split(separator: "-")
        guard parts.count == 3 else { return isoDate }
        return "\(parts[2])/\(parts[1])/\(parts[0])"
    }

    private func stageLabel(_ stage: PreparationStage) -> String {
        switch stage {
        case .todayCelebration:
            return env.isEnglish ? "Today" : "Hôm nay"
        case .urgentLastMinute:
            return env.isEnglish ? "Final days" : "Cần chốt sớm"
        case .actionNeeded:
            return env.isEnglish ? "This week" : "Tuần này"
        case .planAheadBooking:
            return env.isEnglish ? "Plan ahead" : "Đặt trước"
        case .relaxed:
            return env.isEnglish ? "Upcoming" : "Sắp tới"
        }
    }
}
