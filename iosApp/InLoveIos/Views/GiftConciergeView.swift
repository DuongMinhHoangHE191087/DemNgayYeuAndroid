import InLoveIosCore
import SwiftUI

public struct GiftConciergeView: View {
    @EnvironmentObject private var env: AppEnvironment
    @State private var showDossierEditor = false
    @State private var selectedRecommendation: RichGiftRecommendation?
    @State private var copiedNoteId: String?

    public init() {}

    public var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: InLoveMetrics.sectionGap) {
                    dossierSummaryCard
                    conciergeControlsCard
                    if let bundle = env.adviceBundle {
                        if !bundle.tabooWarnings.isEmpty {
                            tabooAlertsBanner(bundle.tabooWarnings)
                        }
                        if bundle.recommendations.isEmpty {
                            emptyRecommendationsCard
                        } else {
                            recommendationsSection(bundle)
                        }
                        loveNotesSection(bundle)
                        prepTimelineCard(bundle.prepTimeline)
                    } else {
                        emptyRecommendationsCard
                    }
                    if !env.isPro && env.remainingFreeAiRuns == 0 {
                        softUpsellCard
                    }
                }
                .padding(.horizontal, InLoveMetrics.screenMargin)
                .padding(.vertical, 12)
            }
            .background(InLoveColors.softBlush.ignoresSafeArea())
            .navigationTitle(env.isEnglish ? "Gifts & Notes" : "Quà Tặng & Lời Chúc")
            .sheet(isPresented: $showDossierEditor) {
                PartnerDossierView()
                    .environmentObject(env)
            }
            .sheet(item: $selectedRecommendation) { rec in
                GiftDetailSheet(recommendation: rec, isEnglish: env.isEnglish)
            }
            .onAppear {
                if env.openDossierRequested {
                    env.openDossierRequested = false
                    showDossierEditor = true
                }
            }
            .onChange(of: env.openDossierRequested) { _, requested in
                if requested {
                    env.openDossierRequested = false
                    showDossierEditor = true
                }
            }
        }
    }

    private var dossierSummaryCard: some View {
        let percent = env.bridge.completenessPercent(for: env.dossier)
        let targetName = env.dossier.nickname.isEmpty ? env.partner2Name : env.dossier.nickname
        return VStack(alignment: .leading, spacing: 10) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(env.isEnglish ? "Partner preferences" : "Hồ sơ sở thích")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(InLoveColors.rose)
                    Text(targetName)
                        .font(InLoveTypography.serifHeadline)
                        .foregroundStyle(InLoveColors.deepPlum)
                }
                Spacer()
                Text("\(percent)%")
                    .font(InLoveTypography.metricNumber)
                    .foregroundStyle(InLoveColors.rose)
            }

            ProgressView(value: Double(percent), total: 100.0)
                .tint(InLoveColors.rose)

            Button(env.isEnglish ? "Edit preferences & sizes" : "Chỉnh sở thích, size & kiêng kỵ") {
                showDossierEditor = true
            }
            .buttonStyle(InLoveSecondaryButtonStyle())
            .inLoveTapTarget()
        }
        .inLoveCard()
    }

    private var conciergeControlsCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text(env.isEnglish ? "Gift & note tone" : "Giọng điệu lời chúc & gợi ý")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(InLoveColors.deepPlum)
                Spacer()
                Text(
                    env.isPro
                        ? "InLove Pro"
                        : (env.isEnglish ? "\(env.remainingFreeAiRuns) free left this month" : "Còn \(env.remainingFreeAiRuns) lượt tháng này")
                )
                .font(.caption.weight(.medium))
                .foregroundStyle(InLoveColors.subtlePlum)
            }

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(RelationshipTone.allCases, id: \.self) { tone in
                        Button {
                            env.selectedTone = tone
                        } label: {
                            Text(tone.displayTitle(isEnglish: env.isEnglish))
                                .font(.caption.weight(.semibold))
                                .foregroundStyle(env.selectedTone == tone ? InLoveColors.pearl : InLoveColors.deepPlum)
                                .padding(.horizontal, 14)
                                .frame(minHeight: InLoveMetrics.minTapTarget)
                                .background(
                                    Capsule().fill(env.selectedTone == tone ? InLoveColors.rose : InLoveColors.surfaceContainerLow)
                                )
                        }
                        .inLoveTapTarget()
                    }
                }
            }

            Button(env.isEnglish ? "Refresh gift & love-note ideas" : "Gợi ý quà & lời chúc phù hợp") {
                Task {
                    let occId = env.nextReadiness?.occasionId ?? "valentine"
                    let occTitle = env.nextReadiness?.localizedTitle(isEnglish: env.isEnglish)
                        ?? (env.isEnglish ? "Love Anniversary" : "Kỷ niệm tình yêu")
                    await env.generateAdvice(forOccasionId: occId, occasionTitle: occTitle)
                }
            }
            .buttonStyle(InLovePrimaryButtonStyle())
            .inLoveTapTarget()
        }
        .inLoveCard()
    }

    private var emptyRecommendationsCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(env.isEnglish ? "No tailored suggestions yet" : "Chưa có gợi ý phù hợp")
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(InLoveColors.deepPlum)
            Text(
                env.isEnglish
                    ? "Add at least one interest or raise your budget limit in the partner profile to see gift picks."
                    : "Hãy chọn thêm sở thích hoặc điều chỉnh mức ngân sách trong hồ sơ để nhận gợi ý sát gu hơn."
            )
            .font(.caption)
            .foregroundStyle(InLoveColors.subtlePlum)
            Button(env.isEnglish ? "Open partner profile" : "Mở hồ sơ người thương") {
                showDossierEditor = true
            }
            .buttonStyle(InLoveSecondaryButtonStyle())
            .inLoveTapTarget()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .inLoveCard()
    }

    private func tabooAlertsBanner(_ warnings: [String]) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            ForEach(warnings, id: \.self) { warning in
                Label(warning, systemImage: "exclamationmark.shield")
                    .font(.caption.weight(.medium))
                    .foregroundStyle(InLoveColors.warningAmber)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .inLoveCard(background: InLoveColors.vipGoldContainer, borderColor: InLoveColors.vipGoldBorder)
    }

    private func recommendationsSection(_ bundle: AiGiftAdviceBundle) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(bundle.summaryHeadline)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(InLoveColors.deepPlum)

            ForEach(bundle.recommendations) { rec in
                VStack(alignment: .leading, spacing: 8) {
                    Button {
                        selectedRecommendation = rec
                    } label: {
                        VStack(alignment: .leading, spacing: 8) {
                            HStack(alignment: .top) {
                                VStack(alignment: .leading, spacing: 4) {
                                    Text(rec.category)
                                        .font(.caption.weight(.semibold))
                                        .foregroundStyle(InLoveColors.rose)
                                    Text(rec.title)
                                        .font(InLoveTypography.serifHeadline)
                                        .foregroundStyle(InLoveColors.deepPlum)
                                        .multilineTextAlignment(.leading)
                                }
                                Spacer()
                                Text(rec.priceRangeLabel)
                                    .font(.caption.weight(.semibold))
                                    .monospacedDigit()
                                    .foregroundStyle(InLoveColors.deepPlum)
                                    .padding(.horizontal, 10)
                                    .padding(.vertical, 5)
                                    .background(Capsule().fill(InLoveColors.surfaceContainerLow))
                            }

                            Text(rec.reason)
                                .font(.subheadline)
                                .foregroundStyle(InLoveColors.subtlePlum)
                                .multilineTextAlignment(.leading)

                            Text(rec.preparationPlan)
                                .font(.caption)
                                .foregroundStyle(InLoveColors.deepPlum)
                                .multilineTextAlignment(.leading)

                            if !rec.personalizationHint.isEmpty {
                                Text(rec.personalizationHint)
                                    .font(.caption.weight(.medium))
                                    .foregroundStyle(InLoveColors.rose)
                            }
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    .buttonStyle(.plain)

                    HStack {
                        ShareLink(
                            item: "\(rec.title) — \(rec.reason)\n\(rec.preparationPlan)"
                        ) {
                            Label(env.isEnglish ? "Share" : "Chia sẻ", systemImage: "square.and.arrow.up")
                                .font(.caption.weight(.semibold))
                                .foregroundStyle(InLoveColors.rose)
                                .frame(minWidth: 48, minHeight: 48)
                        }
                        Spacer()
                        Button(env.isEnglish ? "Details" : "Chi tiết") {
                            selectedRecommendation = rec
                        }
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(InLoveColors.subtlePlum)
                        .inLoveTapTarget()
                    }
                }
                .inLoveCard()
            }
        }
    }

    private func loveNotesSection(_ bundle: AiGiftAdviceBundle) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(env.isEnglish ? "Love notes you can send" : "Lời chúc gợi ý")
                .font(.headline.weight(.semibold))
                .foregroundStyle(InLoveColors.deepPlum)

            ForEach(bundle.loveNotes) { note in
                VStack(alignment: .leading, spacing: 8) {
                    Text(note.title)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(InLoveColors.deepPlum)
                    Text(note.message)
                        .font(.subheadline)
                        .foregroundStyle(InLoveColors.subtlePlum)
                    HStack(spacing: 12) {
                        Button {
                            #if os(iOS)
                            UIPasteboard.general.string = note.message
                            #endif
                            copiedNoteId = note.id
                        } label: {
                            Label(
                                copiedNoteId == note.id
                                    ? (env.isEnglish ? "Copied" : "Đã chép")
                                    : (env.isEnglish ? "Copy note" : "Chép lời chúc"),
                                systemImage: copiedNoteId == note.id ? "checkmark" : "doc.on.doc"
                            )
                        }
                        .buttonStyle(InLoveSecondaryButtonStyle())
                        .inLoveTapTarget()

                        ShareLink(item: note.message) {
                            Label(env.isEnglish ? "Send" : "Gửi", systemImage: "paperplane")
                                .font(.subheadline.weight(.medium))
                                .foregroundStyle(InLoveColors.rose)
                                .frame(minWidth: 48, minHeight: 48)
                        }
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .inLoveCard()
            }
        }
    }

    private func prepTimelineCard(_ steps: [String]) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(env.isEnglish ? "Preparation timeline" : "Lộ trình chuẩn bị")
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(InLoveColors.rose)
            ForEach(steps, id: \.self) { step in
                Label(step, systemImage: "checkmark.circle")
                    .font(.caption)
                    .foregroundStyle(InLoveColors.deepPlum)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .inLoveCard()
    }

    private var softUpsellCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(env.isEnglish ? "Monthly free suggestions used" : "Đã dùng hết lượt miễn phí tháng này")
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(InLoveColors.deepPlum)
            Text(
                env.isEnglish
                    ? "Start a 3-day free trial with InLove Pro for full gift & note coaching."
                    : "Dùng thử 3 ngày miễn phí gói năm InLove Pro để xem đầy đủ gợi ý quà và lời chúc."
            )
            .font(.caption)
            .foregroundStyle(InLoveColors.subtlePlum)
            Button(env.isEnglish ? "View InLove Pro" : "Xem gói InLove Pro") {
                env.presentPaywall(for: .aiGiftConciergeUnlimited)
            }
            .buttonStyle(InLovePrimaryButtonStyle())
            .inLoveTapTarget()
        }
        .inLoveCard(background: InLoveColors.surfaceContainerLow, borderColor: InLoveColors.rose)
    }
}

private struct GiftDetailSheet: View {
    let recommendation: RichGiftRecommendation
    let isEnglish: Bool
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    Text(recommendation.title)
                        .font(.title2.weight(.semibold))
                        .foregroundStyle(InLoveColors.deepPlum)
                    Text("\(recommendation.category) • \(recommendation.priceRangeLabel)")
                        .font(.subheadline.weight(.medium))
                        .foregroundStyle(InLoveColors.rose)
                    detailBlock(title: isEnglish ? "Why it fits" : "Vì sao phù hợp", body: recommendation.reason)
                    detailBlock(title: isEnglish ? "Preparation" : "Cách chuẩn bị", body: recommendation.preparationPlan)
                    if !recommendation.whereToFind.isEmpty {
                        detailBlock(title: isEnglish ? "Where to find" : "Gợi ý nơi mua", body: recommendation.whereToFind)
                    }
                    if let caution = recommendation.culturalCaution, !caution.isEmpty {
                        detailBlock(title: isEnglish ? "Cultural note" : "Lưu ý văn hóa", body: caution)
                    }
                }
                .padding(16)
            }
            .background(InLoveColors.softBlush.ignoresSafeArea())
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button(isEnglish ? "Done" : "Xong") { dismiss() }
                        .inLoveTapTarget()
                }
            }
        }
    }

    private func detailBlock(title: String, body: String) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title)
                .font(.caption.weight(.semibold))
                .foregroundStyle(InLoveColors.rose)
            Text(body)
                .font(.body)
                .foregroundStyle(InLoveColors.deepPlum)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .inLoveCard()
    }
}
