import InLoveIosCore
import SwiftUI

public struct PartnerDossierView: View {
    @EnvironmentObject private var env: AppEnvironment
    @Environment(\.dismiss) private var dismiss

    @State private var draft: PartnerDossier = PartnerDossier()
    @State private var budgetText: String = ""
    @State private var colorsText: String = ""
    @State private var scentsText: String = ""
    @State private var foodsText: String = ""
    @State private var allergiesText: String = ""
    @State private var taboosText: String = ""
    @State private var newNoteText: String = ""
    @State private var newNoteShop: String = ""

    public init() {}

    public var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: InLoveMetrics.sectionGap) {
                    dossierProgressHeader
                    basicInfoCard
                    loveLanguagesCard
                    interestsCard
                    sizesAndAestheticsCard
                    foodAndTaboosCard
                    secretNotesCard
                    Button(env.isEnglish ? "Save partner profile" : "Lưu hồ sơ người thương") {
                        saveChanges()
                    }
                    .buttonStyle(InLovePrimaryButtonStyle())
                    .inLoveTapTarget()
                }
                .padding(.horizontal, InLoveMetrics.screenMargin)
                .padding(.vertical, 12)
            }
            .background(InLoveColors.softBlush.ignoresSafeArea())
            .navigationTitle(env.isEnglish ? "Partner Profile" : "Hồ Sơ Người Thương")
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button(env.isEnglish ? "Close" : "Đóng") { dismiss() }
                        .inLoveTapTarget()
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button(env.isEnglish ? "Save" : "Lưu") { saveChanges() }
                        .fontWeight(.semibold)
                        .inLoveTapTarget()
                }
            }
            .onAppear { loadFromEnvironment() }
        }
    }

    private var dossierProgressHeader: some View {
        let percent = env.bridge.completenessPercent(for: draft)
        return VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text(draft.nickname.isEmpty ? env.partner2Name : draft.nickname)
                    .font(InLoveTypography.serifHeadline)
                    .foregroundStyle(InLoveColors.deepPlum)
                Spacer()
                Text("\(percent)%")
                    .font(InLoveTypography.metricNumber)
                    .foregroundStyle(InLoveColors.rose)
            }
            ProgressView(value: Double(percent), total: 100.0)
                .tint(InLoveColors.rose)
        }
        .inLoveCard()
    }

    private var basicInfoCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(env.isEnglish ? "Profile & budget" : "Thông tin & ngân sách")
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(InLoveColors.rose)

            TextField(env.isEnglish ? "Sweetheart nickname" : "Biệt danh người thương", text: $draft.nickname)
                .textFieldStyle(.roundedBorder)
                .frame(minHeight: InLoveMetrics.minTapTarget)

            TextField(env.isEnglish ? "Target max budget (VND)" : "Ngân sách tối đa (VND)", text: $budgetText)
                .keyboardType(.numberPad)
                .textFieldStyle(.roundedBorder)
                .frame(minHeight: InLoveMetrics.minTapTarget)

            HStack(spacing: 8) {
                regionChip("VN", title: "Việt Nam")
                regionChip("INTL", title: env.isEnglish ? "International" : "Quốc tế")
            }

            TextField(env.isEnglish ? "Daily likes and small habits" : "Sở thích nhỏ hằng ngày", text: $draft.freeformLikes, axis: .vertical)
                .textFieldStyle(.roundedBorder)
                .frame(minHeight: InLoveMetrics.minTapTarget)
        }
        .inLoveCard()
    }

    private func regionChip(_ code: String, title: String) -> some View {
        let selected = draft.occasionRegion.uppercased() == code
        return Button {
            draft.occasionRegion = code
        } label: {
            Text(title)
                .font(.subheadline.weight(.medium))
                .foregroundStyle(selected ? InLoveColors.pearl : InLoveColors.deepPlum)
                .frame(maxWidth: .infinity, minHeight: InLoveMetrics.minTapTarget)
                .background(Capsule().fill(selected ? InLoveColors.rose : InLoveColors.surfaceContainerLow))
        }
        .inLoveTapTarget()
    }

    private var loveLanguagesCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(env.isEnglish ? "Love languages" : "Ngôn ngữ tình yêu")
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(InLoveColors.rose)

            LazyVGrid(columns: [GridItem(.adaptive(minimum: 150), spacing: 8)], spacing: 8) {
                ForEach(LoveLanguage.allCases, id: \.self) { lang in
                    let active = draft.loveLanguages.contains(lang)
                    Button {
                        if active { draft.loveLanguages.remove(lang) } else { draft.loveLanguages.insert(lang) }
                    } label: {
                        Text(lang.displayTitle(isEnglish: env.isEnglish))
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(active ? InLoveColors.pearl : InLoveColors.deepPlum)
                            .frame(maxWidth: .infinity, minHeight: InLoveMetrics.minTapTarget)
                            .padding(.horizontal, 8)
                            .background(
                                RoundedRectangle(cornerRadius: 14, style: .continuous)
                                    .fill(active ? InLoveColors.rose : InLoveColors.surfaceContainerLow)
                            )
                    }
                    .inLoveTapTarget()
                }
            }
        }
        .inLoveCard()
    }

    private var interestsCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(env.isEnglish ? "Interest categories" : "Nhóm sở thích")
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(InLoveColors.rose)

            LazyVGrid(columns: [GridItem(.adaptive(minimum: 130), spacing: 8)], spacing: 8) {
                ForEach(PartnerInterestCategory.allCases, id: \.self) { cat in
                    let active = draft.interestCategories.contains(cat.rawValue)
                    Button {
                        if active { draft.interestCategories.remove(cat.rawValue) }
                        else { draft.interestCategories.insert(cat.rawValue) }
                    } label: {
                        Text(cat.displayTitle(isEnglish: env.isEnglish))
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(active ? InLoveColors.pearl : InLoveColors.deepPlum)
                            .frame(maxWidth: .infinity, minHeight: InLoveMetrics.minTapTarget)
                            .padding(.horizontal, 6)
                            .background(
                                RoundedRectangle(cornerRadius: 14, style: .continuous)
                                    .fill(active ? InLoveColors.rose : InLoveColors.surfaceContainerLow)
                            )
                    }
                    .inLoveTapTarget()
                }
            }
        }
        .inLoveCard()
    }

    private var sizesAndAestheticsCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(env.isEnglish ? "Sizes, colors & scents" : "Size đồ, màu sắc & mùi hương")
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(InLoveColors.rose)

            HStack(spacing: 8) {
                TextField(env.isEnglish ? "Shirt size" : "Size áo", text: $draft.sizes.shirtSize)
                    .textFieldStyle(.roundedBorder)
                    .frame(minHeight: InLoveMetrics.minTapTarget)
                TextField(env.isEnglish ? "Shoe size" : "Size giày", text: $draft.sizes.shoeSize)
                    .textFieldStyle(.roundedBorder)
                    .frame(minHeight: InLoveMetrics.minTapTarget)
            }
            HStack(spacing: 8) {
                TextField(env.isEnglish ? "Pants or dress" : "Size quần/váy", text: $draft.sizes.pantsOrDressSize)
                    .textFieldStyle(.roundedBorder)
                    .frame(minHeight: InLoveMetrics.minTapTarget)
                TextField(env.isEnglish ? "Ring size" : "Ni nhẫn", text: $draft.sizes.ringSize)
                    .textFieldStyle(.roundedBorder)
                    .frame(minHeight: InLoveMetrics.minTapTarget)
            }
            TextField(env.isEnglish ? "Favorite colors (comma separated)" : "Màu yêu thích (cách dấu phẩy)", text: $colorsText)
                .textFieldStyle(.roundedBorder)
                .frame(minHeight: InLoveMetrics.minTapTarget)
            TextField(env.isEnglish ? "Favorite scents (comma separated)" : "Mùi hương ưa thích (cách dấu phẩy)", text: $scentsText)
                .textFieldStyle(.roundedBorder)
                .frame(minHeight: InLoveMetrics.minTapTarget)
        }
        .inLoveCard()
    }

    private var foodAndTaboosCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(env.isEnglish ? "Food, allergies & taboos" : "Ẩm thực, dị ứng & điều tránh")
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(InLoveColors.rose)

            TextField(env.isEnglish ? "Go-to drink" : "Đồ uống yêu thích", text: $draft.foodAndCare.goToDrinks)
                .textFieldStyle(.roundedBorder)
                .frame(minHeight: InLoveMetrics.minTapTarget)
            TextField(env.isEnglish ? "Favorite foods (comma separated)" : "Món ăn thích (cách dấu phẩy)", text: $foodsText)
                .textFieldStyle(.roundedBorder)
                .frame(minHeight: InLoveMetrics.minTapTarget)
            TextField(env.isEnglish ? "Allergies to exclude (comma separated)" : "Dị ứng cần tránh (cách dấu phẩy)", text: $allergiesText)
                .textFieldStyle(.roundedBorder)
                .frame(minHeight: InLoveMetrics.minTapTarget)
            TextField(env.isEnglish ? "Dislikes or taboos (comma separated)" : "Điều không thích / kiêng kỵ", text: $taboosText)
                .textFieldStyle(.roundedBorder)
                .frame(minHeight: InLoveMetrics.minTapTarget)
        }
        .inLoveCard()
    }

    private var secretNotesCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text(env.isEnglish ? "Secret surprise notes" : "Ghi chú bí mật")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(InLoveColors.rose)
                Spacer()
                Text(
                    env.isPro
                        ? (env.isEnglish ? "Unlimited" : "Không giới hạn")
                        : "\(draft.secretSurpriseNotes.count)/\(MonetizationLimits.freeSecretNotesLimit)"
                )
                .font(.caption.weight(.semibold))
                .monospacedDigit()
                .foregroundStyle(InLoveColors.subtlePlum)
            }

            HStack(spacing: 8) {
                TextField(env.isEnglish ? "Gift hint they mentioned" : "Món người ấy từng nhắc thích", text: $newNoteText)
                    .textFieldStyle(.roundedBorder)
                    .frame(minHeight: InLoveMetrics.minTapTarget)
                Button(env.isEnglish ? "Add" : "Thêm") {
                    Task {
                        let added = await env.addSecretNote(note: newNoteText, occasionHint: "", estimatedPriceVnd: 0, shop: newNoteShop)
                        if added {
                            draft = env.dossier
                            newNoteText = ""
                            newNoteShop = ""
                        }
                    }
                }
                .buttonStyle(InLoveSecondaryButtonStyle())
                .inLoveTapTarget()
            }

            if draft.secretSurpriseNotes.isEmpty {
                Text(env.isEnglish ? "No secret notes yet." : "Chưa có ghi chú bí mật.")
                    .font(.caption)
                    .foregroundStyle(InLoveColors.subtlePlum)
            } else {
                ForEach(draft.secretSurpriseNotes) { note in
                    HStack {
                        Button {
                            if let idx = draft.secretSurpriseNotes.firstIndex(where: { $0.id == note.id }) {
                                draft.secretSurpriseNotes[idx].isPurchased.toggle()
                            }
                        } label: {
                            Image(systemName: note.isPurchased ? "checkmark.circle.fill" : "circle")
                                .foregroundStyle(note.isPurchased ? InLoveColors.emeraldCheck : InLoveColors.rose)
                        }
                        .inLoveTapTarget()

                        Text(note.note)
                            .strikethrough(note.isPurchased)
                            .font(.subheadline)
                            .foregroundStyle(InLoveColors.deepPlum)
                        Spacer()
                        Button(role: .destructive) {
                            draft.secretSurpriseNotes.removeAll { $0.id == note.id }
                        } label: {
                            Image(systemName: "trash")
                                .foregroundStyle(InLoveColors.urgentCrimson)
                        }
                        .inLoveTapTarget()
                    }
                }
            }
        }
        .inLoveCard()
    }

    private func loadFromEnvironment() {
        draft = env.dossier
        budgetText = draft.budgetMaxVnd > 0 ? "\(draft.budgetMaxVnd)" : ""
        colorsText = draft.sizes.favoriteColors.joined(separator: ", ")
        scentsText = draft.sizes.favoriteScents.joined(separator: ", ")
        foodsText = draft.foodAndCare.favoriteFoods.joined(separator: ", ")
        allergiesText = draft.foodAndCare.allergies.joined(separator: ", ")
        taboosText = draft.foodAndCare.dislikesOrTaboos.joined(separator: ", ")
    }

    private func saveChanges() {
        draft.budgetMaxVnd = Int64(budgetText.filter(\.isNumber)) ?? 0
        draft.sizes.favoriteColors = splitCommaList(colorsText)
        draft.sizes.favoriteScents = splitCommaList(scentsText)
        draft.foodAndCare.favoriteFoods = splitCommaList(foodsText)
        draft.foodAndCare.allergies = splitCommaList(allergiesText)
        draft.foodAndCare.dislikesOrTaboos = splitCommaList(taboosText)
        Task {
            await env.saveDossier(draft)
            dismiss()
        }
    }

    private func splitCommaList(_ raw: String) -> [String] {
        raw.split(separator: ",")
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
    }
}
