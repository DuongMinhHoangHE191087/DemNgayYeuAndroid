import InLoveIosCore
import SwiftUI

public struct SettingsView: View {
    @EnvironmentObject private var env: AppEnvironment
    @State private var showProfileEditor = false
    @State private var showPrivacyDisclosure = false
    @State private var copiedCode = false

    public init() {}

    public var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: InLoveMetrics.sectionGap) {
                    proStatusCard
                    guideQuickEntryCard
                    coupleProfileCard
                    languageCard
                    couplePairingCard
                    notificationsCard
                    legalLinksCard
                }
                .padding(.horizontal, InLoveMetrics.screenMargin)
                .padding(.vertical, 12)
            }
            .background(InLoveColors.softBlush.ignoresSafeArea())
            .navigationTitle(env.isEnglish ? "Settings" : "Cài Đặt")
            .sheet(isPresented: $showProfileEditor) {
                CoupleProfileEditSheet()
                    .environmentObject(env)
            }
        }
    }

    private var proStatusCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text(
                    env.isPro
                        ? (env.isEnglish ? "InLove Pro active" : "Đang bật InLove Pro")
                        : (env.isEnglish ? "InLove Pro" : "Gói thành viên InLove Pro")
                )
                .font(InLoveTypography.serifHeadline)
                .foregroundStyle(InLoveColors.deepPlum)
                Spacer()
                if env.isPro {
                    Text(env.isEnglish ? "Active" : "Đang hoạt động")
                        .font(.caption2.weight(.semibold))
                        .foregroundStyle(InLoveColors.vipGoldText)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(Capsule().fill(InLoveColors.vipGoldContainer))
                }
            }

            Text(
                env.isPro
                    ? (env.isEnglish ? "Ad-free space with early alerts and unlimited dossier hints." : "Bỏ toàn bộ quảng cáo, bật nhắc hẹn sớm và lưu không giới hạn ghi chú bí mật.")
                    : (env.isEnglish ? "Try 3 days free on Yearly plan or restore a previous purchase." : "Dùng thử 3 ngày miễn phí gói năm hoặc khôi phục gói đã mua.")
            )
            .font(.caption)
            .foregroundStyle(InLoveColors.subtlePlum)

            HStack(spacing: 10) {
                if !env.isPro {
                    Button(env.isEnglish ? "View Pro plans" : "Xem gói Pro") {
                        env.presentPaywall()
                    }
                    .buttonStyle(InLovePrimaryButtonStyle())
                    .inLoveTapTarget()
                }
                Button(env.isEnglish ? "Restore purchases" : "Khôi phục mua") {
                    Task {
                        await env.storeKit.restorePurchases()
                        env.refreshComputedState()
                    }
                }
                .buttonStyle(InLoveSecondaryButtonStyle())
                .inLoveTapTarget()
            }
        }
        .inLoveCard(
            background: env.isPro ? InLoveColors.vipGoldContainer : InLoveColors.pearl,
            borderColor: env.isPro ? InLoveColors.vipGoldBorder : InLoveColors.cardBorder
        )
    }

    private var guideQuickEntryCard: some View {
        HStack {
            VStack(alignment: .leading, spacing: 4) {
                Text(env.isEnglish ? "4-Chapter App Guide" : "Hướng dẫn sử dụng")
                    .font(InLoveTypography.serifHeadline)
                    .foregroundStyle(InLoveColors.deepPlum)
                Text(env.isEnglish ? "Days, pairing, gifts & privacy" : "Đếm ngày, ghép đôi, quà & riêng tư")
                    .font(.caption)
                    .foregroundStyle(InLoveColors.subtlePlum)
            }
            Spacer()
            Button(env.isEnglish ? "Open" : "Mở") {
                env.showGuideSheet = true
            }
            .buttonStyle(InLoveSecondaryButtonStyle())
            .inLoveTapTarget()
        }
        .inLoveCard()
    }

    private var coupleProfileCard: some View {
        let startDisplay = InLoveDateFormatting.displayDate(iso: env.startDateIso, isEnglish: env.isEnglish)
        return HStack {
            VStack(alignment: .leading, spacing: 4) {
                Text("\(env.partner1Name) & \(env.partner2Name)")
                    .font(InLoveTypography.serifHeadline)
                    .foregroundStyle(InLoveColors.deepPlum)
                Text(
                    env.isEnglish
                        ? "\(env.loveDays) days together · Since \(startDisplay)"
                        : "Đã đồng hành \(env.loveDays) ngày · Từ \(startDisplay)"
                )
                .font(InLoveTypography.metadataCaption)
                .foregroundStyle(InLoveColors.subtlePlum)
            }
            Spacer()
            Button(env.isEnglish ? "Edit" : "Chỉnh sửa") {
                showProfileEditor = true
            }
            .buttonStyle(InLoveSecondaryButtonStyle())
            .inLoveTapTarget()
        }
        .inLoveCard()
    }

    private var languageCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(env.isEnglish ? "App language" : "Ngôn ngữ ứng dụng")
                .font(InLoveTypography.serifHeadline)
                .foregroundStyle(InLoveColors.rose)
            HStack(spacing: 10) {
                langButton(title: "Tiếng Việt", active: !env.isEnglish) { env.isEnglish = false }
                langButton(title: "English", active: env.isEnglish) { env.isEnglish = true }
            }
        }
        .inLoveCard()
    }

    private func langButton(title: String, active: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(active ? InLoveColors.pearl : InLoveColors.deepPlum)
                .frame(maxWidth: .infinity, minHeight: InLoveMetrics.minTapTarget)
                .background(Capsule().fill(active ? InLoveColors.rose : InLoveColors.surfaceContainerLow))
        }
        .inLoveTapTarget()
    }

    private var couplePairingCard: some View {
        let syncState = env.pairingState
        return VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text(env.isEnglish ? "Couple pairing" : "Kết nối đôi bạn")
                    .font(InLoveTypography.serifHeadline)
                    .foregroundStyle(InLoveColors.rose)
                Spacer()
                Text(syncState.phase.statusLabel(isEnglish: env.isEnglish))
                    .font(.caption2.weight(.semibold))
                    .foregroundStyle(InLoveColors.deepPlum)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(Capsule().fill(InLoveColors.surfaceContainerLow))
            }

            HStack {
                Text("\(env.isEnglish ? "Invite code:" : "Mã mời:") \(env.coupleCode)")
                    .font(InLoveTypography.metricNumber)
                    .foregroundStyle(InLoveColors.deepPlum)
                Spacer()
                Button {
                    #if os(iOS)
                    UIPasteboard.general.string = env.coupleCode
                    #endif
                    copiedCode = true
                } label: {
                    Label(copiedCode ? (env.isEnglish ? "Copied" : "Đã chép") : (env.isEnglish ? "Copy" : "Chép"), systemImage: "doc.on.doc")
                }
                .buttonStyle(InLoveSecondaryButtonStyle())
                .inLoveTapTarget()

                if let shareUrl = URL(string: CoupleCodeParser.buildInviteLink(code: env.coupleCode)) {
                    ShareLink(item: shareUrl) {
                        Image(systemName: "square.and.arrow.up")
                            .foregroundStyle(InLoveColors.rose)
                            .frame(minWidth: InLoveMetrics.minTapTarget, minHeight: InLoveMetrics.minTapTarget)
                    }
                }
            }

            if let partner = syncState.partnerName ?? env.pairedPartnerName {
                Label(partner, systemImage: "checkmark.circle.fill")
                    .font(.caption.weight(.medium))
                    .foregroundStyle(InLoveColors.emeraldCheck)
            }

            Button(env.isEnglish ? "Manage pairing & sync" : "Quản lý ghép đôi & đồng bộ") {
                env.showCouplePairingSheet = true
            }
            .buttonStyle(InLoveSecondaryButtonStyle())
            .frame(maxWidth: .infinity)
            .inLoveTapTarget()
        }
        .inLoveCard()
    }

    private var notificationsCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(env.isEnglish ? "Reminders & widget privacy" : "Thông báo & quyền riêng tư widget")
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(InLoveColors.rose)

            Toggle(env.isEnglish ? "Anniversary reminders" : "Nhắc hẹn mốc kỷ niệm", isOn: $env.notificationsEnabled)
                .tint(InLoveColors.rose)
                .frame(minHeight: InLoveMetrics.minTapTarget)

            Toggle(env.isEnglish ? "Early 14–30 day alerts (Pro)" : "Nhắc sớm 14–30 ngày (Pro)", isOn: Binding(
                get: { env.early14d30dEnabled },
                set: { newVal in
                    if newVal && !env.isPro { env.presentPaywall(for: .early14d30dReminders) }
                    else { env.early14d30dEnabled = newVal }
                }
            ))
            .tint(InLoveColors.rose)
            .frame(minHeight: InLoveMetrics.minTapTarget)

            Toggle(env.isEnglish ? "Monthly anniversary reminders (Pro)" : "Nhắc kỷ niệm tròn tháng (Pro)", isOn: Binding(
                get: { env.monthiversaryEnabled },
                set: { newVal in
                    if newVal && !env.isPro { env.presentPaywall(for: .monthiversaryCadence) }
                    else { env.monthiversaryEnabled = newVal }
                }
            ))
            .tint(InLoveColors.rose)
            .frame(minHeight: InLoveMetrics.minTapTarget)

            Toggle(env.isEnglish ? "Gentle chime sound" : "Âm thanh chuông nhẹ nhàng", isOn: $env.soundEnabled)
                .tint(InLoveColors.rose)
                .frame(minHeight: InLoveMetrics.minTapTarget)

            Toggle(env.isEnglish ? "Hide partner names on Lock Screen Widget" : "Ẩn tên riêng trên Widget màn hình khóa", isOn: $env.redactWidgetOnLockScreen)
                .tint(InLoveColors.rose)
                .frame(minHeight: InLoveMetrics.minTapTarget)
        }
        .foregroundStyle(InLoveColors.deepPlum)
        .inLoveCard()
    }

    private var legalLinksCard: some View {
        DisclosureGroup(isExpanded: $showPrivacyDisclosure) {
            VStack(alignment: .leading, spacing: 4) {
                Text(
                    env.isEnglish
                        ? "Couple photos and private partner notes stay protected on your device and in your personal encrypted backup when signed in."
                        : "Ảnh kỷ niệm và ghi chú người thương được bảo vệ riêng tư trên thiết bị và bản sao lưu cá nhân của bạn."
                )
                .font(.caption)
                .foregroundStyle(InLoveColors.subtlePlum)
                .padding(.vertical, 4)

                if let privacy = URL(string: "https://inloveapp.com/privacy") {
                    Link(env.isEnglish ? "Privacy Policy" : "Chính sách bảo mật", destination: privacy)
                        .frame(maxWidth: .infinity, minHeight: InLoveMetrics.minTapTarget, alignment: .leading)
                }
                if let terms = URL(string: "https://inloveapp.com/terms") {
                    Link(env.isEnglish ? "Terms of Service" : "Điều khoản dịch vụ", destination: terms)
                        .frame(maxWidth: .infinity, minHeight: InLoveMetrics.minTapTarget, alignment: .leading)
                }
                if let deleteUrl = URL(string: "https://inloveapp.com/delete-account") {
                    Link(env.isEnglish ? "Request account & data deletion" : "Yêu cầu xóa tài khoản & dữ liệu", destination: deleteUrl)
                        .frame(maxWidth: .infinity, minHeight: InLoveMetrics.minTapTarget, alignment: .leading)
                }
            }
            .font(.subheadline.weight(.medium))
            .foregroundStyle(InLoveColors.rose)
        } label: {
            Label(
                env.isEnglish ? "Privacy notice & legal links" : "Quyền riêng tư & điều khoản",
                systemImage: "lock.shield"
            )
            .font(.subheadline.weight(.semibold))
            .foregroundStyle(InLoveColors.deepPlum)
            .frame(minHeight: InLoveMetrics.minTapTarget, alignment: .leading)
        }
        .tint(InLoveColors.rose)
        .inLoveCard()
    }
}

private struct CoupleProfileEditSheet: View {
    @EnvironmentObject private var env: AppEnvironment
    @Environment(\.dismiss) private var dismiss

    @State private var title = ""
    @State private var p1 = ""
    @State private var p2 = ""
    @State private var startDateIso = ""
    @State private var birthdayIso = ""

    var body: some View {
        NavigationStack {
            Form {
                Section(env.isEnglish ? "Couple details" : "Thông tin cặp đôi") {
                    TextField(env.isEnglish ? "Relationship title" : "Tên kỷ niệm", text: $title)
                        .frame(minHeight: InLoveMetrics.minTapTarget)
                    TextField(env.isEnglish ? "Partner 1 name" : "Tên bạn 1", text: $p1)
                        .frame(minHeight: InLoveMetrics.minTapTarget)
                    TextField(env.isEnglish ? "Partner 2 name" : "Tên bạn 2", text: $p2)
                        .frame(minHeight: InLoveMetrics.minTapTarget)
                    TextField(env.isEnglish ? "Start date (yyyy-MM-dd)" : "Ngày bắt đầu (yyyy-MM-dd)", text: $startDateIso)
                        .frame(minHeight: InLoveMetrics.minTapTarget)
                    TextField(env.isEnglish ? "Partner birthday (yyyy-MM-dd)" : "Sinh nhật người ấy (yyyy-MM-dd)", text: $birthdayIso)
                        .frame(minHeight: InLoveMetrics.minTapTarget)
                }
            }
            .navigationTitle(env.isEnglish ? "Edit Couple" : "Chỉnh Hồ Sơ Đôi")
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button(env.isEnglish ? "Cancel" : "Hủy") { dismiss() }.inLoveTapTarget()
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button(env.isEnglish ? "Save" : "Lưu") {
                        env.coupleTitle = title
                        env.partner1Name = p1
                        env.partner2Name = p2
                        env.startDateIso = startDateIso
                        env.partnerBirthdayIso = birthdayIso
                        dismiss()
                    }
                    .inLoveTapTarget()
                }
            }
            .onAppear {
                title = env.coupleTitle
                p1 = env.partner1Name
                p2 = env.partner2Name
                startDateIso = env.startDateIso
                birthdayIso = env.partnerBirthdayIso
            }
        }
    }
}
