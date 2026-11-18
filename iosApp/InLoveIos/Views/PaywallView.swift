import InLoveIosCore
import SwiftUI

public struct PaywallView: View {
    @EnvironmentObject private var env: AppEnvironment

    public init() {}

    public var body: some View {
        PaywallContentView(storeKit: env.storeKit)
    }
}

private struct PaywallContentView: View {
    @EnvironmentObject private var env: AppEnvironment
    @ObservedObject var storeKit: StoreKitEntitlementManager
    @Environment(\.dismiss) private var dismiss
    @State private var selectedSku: String = InLoveSku.yearly

    private var selectedOffer: SubscriptionPlanOffer? {
        storeKit.offers.first(where: { $0.productId == selectedSku }) ?? storeKit.offers.first
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: InLoveMetrics.sectionGap) {
                    heroCard
                    benefitsMatrixCard
                    plansSection
                    purchaseCtaSection
                    legalAndRestoreFooter
                }
                .padding(.horizontal, InLoveMetrics.screenMargin)
                .padding(.vertical, 12)
            }
            .background(InLoveColors.softBlush.ignoresSafeArea())
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        dismiss()
                    } label: {
                        Image(systemName: "xmark")
                            .font(.body.weight(.semibold))
                            .foregroundStyle(InLoveColors.subtlePlum)
                    }
                    .inLoveTapTarget()
                    .accessibilityLabel(env.isEnglish ? "Close" : "Đóng")
                }
            }
            .onAppear {
                env.adManager.enterSacredZone(.paywall)
                Task {
                    await storeKit.refreshAll()
                    env.refreshComputedState()
                }
            }
            .onDisappear {
                env.adManager.exitSacredZone(.paywall)
            }
        }
    }

    private var heroCard: some View {
        VStack(spacing: 8) {
            Image(systemName: "crown")
                .font(.system(size: 30, weight: .medium))
                .foregroundStyle(InLoveColors.vipGoldText)
            Text("InLove Pro")
                .font(InLoveTypography.paywallDisplay)
                .foregroundStyle(InLoveColors.deepPlum)
            Text(
                env.isEnglish
                    ? "Ad-free couple space with early reminders and full gift coaching."
                    : "Không gian đôi bạn yên tĩnh, nhắc hẹn sớm và lưu trọn gu người thương."
            )
            .font(.subheadline)
            .foregroundStyle(InLoveColors.subtlePlum)
            .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .inLoveCard()
    }

    private var benefitsMatrixCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(env.isEnglish ? "Included with Pro" : "Quyền lợi thành viên Pro")
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(InLoveColors.rose)

            ForEach(ProFeature.allCases, id: \.self) { feature in
                let highlighted = env.paywallHighlightFeature == feature
                HStack(spacing: 10) {
                    Image(systemName: "checkmark.circle.fill")
                        .foregroundStyle(InLoveColors.emeraldCheck)
                    Text(feature.localizedTitle(isEnglish: env.isEnglish))
                        .font(.subheadline.weight(highlighted ? .semibold : .regular))
                        .foregroundStyle(InLoveColors.deepPlum)
                }
                .padding(.vertical, 2)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .inLoveCard()
    }

    private var plansSection: some View {
        VStack(spacing: 10) {
            ForEach(storeKit.offers) { offer in
                let selected = offer.productId == selectedSku
                Button {
                    selectedSku = offer.productId
                } label: {
                    HStack(spacing: 12) {
                        VStack(alignment: .leading, spacing: 4) {
                            HStack(spacing: 8) {
                                Text(env.isEnglish ? offer.titleEn : offer.titleVi)
                                    .font(InLoveTypography.serifHeadline)
                                    .foregroundStyle(InLoveColors.deepPlum)
                                if offer.savingsPercent > 0 {
                                    Text(
                                        env.isEnglish
                                            ? "Save \(offer.savingsPercent)% · \(offer.trialDays)d free"
                                            : "Tiết kiệm \(offer.savingsPercent)% · \(offer.trialDays) ngày miễn phí"
                                    )
                                    .font(.caption2.weight(.semibold))
                                    .foregroundStyle(InLoveColors.pearl)
                                    .padding(.horizontal, 8)
                                    .padding(.vertical, 3)
                                    .background(Capsule().fill(InLoveColors.rose))
                                } else if offer.trialDays > 0 {
                                    Text(env.isEnglish ? "3-day trial" : "Dùng thử 3 ngày")
                                        .font(.caption2.weight(.semibold))
                                        .foregroundStyle(InLoveColors.pearl)
                                        .padding(.horizontal, 8)
                                        .padding(.vertical, 3)
                                        .background(Capsule().fill(InLoveColors.rose))
                                } else if offer.isLifetime {
                                    Text(env.isEnglish ? "One-time" : "Mua 1 lần")
                                        .font(.caption2.weight(.semibold))
                                        .foregroundStyle(InLoveColors.vipGoldText)
                                        .padding(.horizontal, 8)
                                        .padding(.vertical, 3)
                                        .background(Capsule().fill(InLoveColors.vipGoldContainer))
                                }
                            }
                            let detailText: String = {
                                if offer.savingsPercent > 0 || offer.isLifetime {
                                    return env.isEnglish ? offer.subtitleEn : offer.subtitleVi
                                }
                                let tagline = env.isEnglish ? offer.taglineEn : offer.taglineVi
                                return tagline.isEmpty ? (env.isEnglish ? offer.subtitleEn : offer.subtitleVi) : tagline
                            }()
                            Text(detailText)
                                .font(.caption)
                                .foregroundStyle(InLoveColors.subtlePlum)
                                .multilineTextAlignment(.leading)
                        }
                        Spacer()
                        Text(offer.displayPrice)
                            .font(InLoveTypography.metricNumber)
                            .foregroundStyle(InLoveColors.rose)
                    }
                    .frame(minHeight: 56)
                    .inLoveCard(
                        background: selected ? InLoveColors.surfaceContainerLow : InLoveColors.pearl,
                        borderColor: selected ? InLoveColors.rose : InLoveColors.cardBorder,
                        borderWidth: selected ? 1.5 : 1.0
                    )
                }
                .buttonStyle(.plain)
            }
        }
    }

    private var purchaseCtaSection: some View {
        VStack(spacing: 8) {
            Button {
                Task {
                    let outcome = await storeKit.purchase(productId: selectedSku)
                    env.refreshComputedState()
                    if case .purchased = outcome {
                        dismiss()
                    }
                }
            } label: {
                if storeKit.isLoading {
                    ProgressView().tint(InLoveColors.pearl)
                } else {
                    Text(ctaTitle)
                }
            }
            .buttonStyle(InLovePrimaryButtonStyle())
            .disabled(storeKit.isLoading)
            .inLoveTapTarget()

            if let status = storeKit.statusMessage, !status.isEmpty {
                Text(status)
                    .font(.caption.weight(.medium))
                    .foregroundStyle(InLoveColors.rose)
            }
        }
    }

    private var ctaTitle: String {
        if selectedSku == InLoveSku.yearly {
            return env.isEnglish ? "Start 3-day free trial" : "Bắt đầu 3 ngày dùng thử miễn phí"
        }
        if selectedSku == InLoveSku.lifetime {
            return env.isEnglish ? "Purchase lifetime plan" : "Mua gói trọn đời"
        }
        return env.isEnglish ? "Subscribe monthly" : "Đăng ký gói tháng"
    }

    private var legalAndRestoreFooter: some View {
        VStack(spacing: 8) {
            Button {
                Task {
                    await storeKit.restorePurchases()
                    env.refreshComputedState()
                    if storeKit.isPro { dismiss() }
                }
            } label: {
                Text(env.isEnglish ? "Restore purchases" : "Khôi phục gói mua")
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(InLoveColors.deepPlum)
            }
            .inLoveTapTarget()

            Text(
                env.isEnglish
                    ? "Payment is charged to your Apple ID account at confirmation. Subscriptions renew automatically unless cancelled at least 24 hours before the end of the current period in App Store Settings."
                    : "Thanh toán được tính qua tài khoản Apple ID của bạn. Gói định kỳ tự động gia hạn trừ khi bạn hủy ít nhất 24 giờ trước khi kết thúc chu kỳ trong Cài đặt App Store."
            )
            .font(.caption2)
            .foregroundStyle(InLoveColors.subtlePlum)
            .multilineTextAlignment(.center)

            HStack(spacing: 16) {
                if let termsUrl = URL(string: "https://inloveapp.com/terms") {
                    Link(env.isEnglish ? "Terms of Service" : "Điều khoản dịch vụ", destination: termsUrl)
                        .font(.caption.weight(.medium))
                        .foregroundStyle(InLoveColors.rose)
                        .inLoveTapTarget()
                }
                if let privacyUrl = URL(string: "https://inloveapp.com/privacy") {
                    Link(env.isEnglish ? "Privacy Policy" : "Chính sách bảo mật", destination: privacyUrl)
                        .font(.caption.weight(.medium))
                        .foregroundStyle(InLoveColors.rose)
                        .inLoveTapTarget()
                }
            }
        }
        .padding(.top, 4)
    }
}
