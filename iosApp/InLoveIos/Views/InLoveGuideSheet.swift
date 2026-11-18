import InLoveIosCore
import SwiftUI

public struct InLoveGuideSheet: View {
    @EnvironmentObject private var env: AppEnvironment
    @Environment(\.dismiss) private var dismiss

    @State private var selectedChapterId: GuideChapterId = .countdownAndMilestones

    public init() {}

    private var chapters: [GuideChapter] {
        env.bridge.guideChapters()
    }

    private var activeChapter: GuideChapter {
        chapters.first(where: { $0.id == selectedChapterId }) ?? chapters[0]
    }

    public var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: InLoveMetrics.sectionGap) {
                    chapterSelectorStrip
                    chapterOverviewCard
                    ForEach(activeChapter.steps) { step in
                        stepCard(step)
                    }
                    Button(activeChapter.quickActionLabel(isEnglish: env.isEnglish)) {
                        dismiss()
                        env.performGuideQuickAction(activeChapter.quickAction)
                    }
                    .buttonStyle(InLovePrimaryButtonStyle())
                    .inLoveTapTarget()
                }
                .padding(.horizontal, InLoveMetrics.screenMargin)
                .padding(.vertical, 12)
            }
            .background(InLoveColors.softBlush.ignoresSafeArea())
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .principal) {
                    Text(env.isEnglish ? "InLove Guide" : "Hướng Dẫn Sử Dụng")
                        .font(InLoveTypography.serifHeadline)
                        .foregroundStyle(InLoveColors.deepPlum)
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button(env.isEnglish ? "Close" : "Đóng") {
                        dismiss()
                    }
                    .foregroundStyle(InLoveColors.rose)
                    .inLoveTapTarget()
                }
            }
        }
    }

    private var chapterSelectorStrip: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(Array(chapters.enumerated()), id: \.element.id) { idx, chapter in
                    let active = chapter.id == activeChapter.id
                    Button {
                        selectedChapterId = chapter.id
                    } label: {
                        HStack(spacing: 6) {
                            Text(String(format: "%02d", idx + 1))
                                .font(.caption.weight(.bold))
                                .monospacedDigit()
                            Text(chapter.shortTab(isEnglish: env.isEnglish))
                                .font(.caption.weight(.semibold))
                                .lineLimit(1)
                        }
                        .foregroundStyle(active ? InLoveColors.pearl : InLoveColors.deepPlum)
                        .padding(.horizontal, 14)
                        .frame(minHeight: InLoveMetrics.minTapTarget)
                        .background(
                            Capsule().fill(active ? InLoveColors.rose : InLoveColors.pearl)
                        )
                        .overlay(
                            Capsule().stroke(InLoveColors.cardBorder, lineWidth: active ? 0 : 1.0)
                        )
                    }
                    .inLoveTapTarget()
                }
            }
        }
    }

    private var chapterOverviewCard: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(activeChapter.title(isEnglish: env.isEnglish))
                .font(InLoveTypography.serifTitle)
                .foregroundStyle(InLoveColors.deepPlum)
            Text(activeChapter.summary(isEnglish: env.isEnglish))
                .font(.subheadline)
                .foregroundStyle(InLoveColors.subtlePlum)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .inLoveCard(background: InLoveColors.surfaceContainerLow)
    }

    private func stepCard(_ step: GuideStep) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .firstTextBaseline, spacing: 10) {
                Text(step.numberLabel)
                    .font(.caption.weight(.bold))
                    .monospacedDigit()
                    .foregroundStyle(InLoveColors.rose)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(Capsule().fill(InLoveColors.surfaceContainerLow))

                Text(step.title(isEnglish: env.isEnglish))
                    .font(InLoveTypography.serifHeadline)
                    .foregroundStyle(InLoveColors.deepPlum)
            }

            Text(step.detail(isEnglish: env.isEnglish))
                .font(.subheadline)
                .foregroundStyle(InLoveColors.subtlePlum)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .inLoveCard()
    }
}
