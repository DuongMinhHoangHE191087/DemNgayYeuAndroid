import Foundation

public protocol GiftConciergeCloudTransport: Sendable {
    func fetchCloudAdvice(request: AiGiftRequest) async throws -> AiGiftAdviceBundle
}

public enum GiftConciergeError: Error, Sendable, Equatable {
    case quotaExhausted
    case unauthenticated
    case transportUnavailable(String)
}

public final class IosGiftConciergeService: Sendable {
    private let cloudTransport: (any GiftConciergeCloudTransport)?

    public init(cloudTransport: (any GiftConciergeCloudTransport)? = nil) {
        self.cloudTransport = cloudTransport
    }

    public func recommend(
        request: AiGiftRequest,
        nowEpochMs: Int64 = Int64(Date().timeIntervalSince1970 * 1000)
    ) async -> AiGiftAdviceBundle {
        if let cloudTransport {
            do {
                let cloudBundle = try await cloudTransport.fetchCloudAdvice(request: request)
                let sanitized = enforceSafetyFilters(bundle: cloudBundle, dossier: request.dossier)
                if !sanitized.recommendations.isEmpty {
                    return sanitized
                }
            } catch {
                // Fall back seamlessly to local smart engine on quota exhaustion or offline network.
            }
        }
        return generateSmartLocalAdvice(request: request, nowEpochMs: nowEpochMs)
    }

    public func generateSmartLocalAdvice(
        request: AiGiftRequest,
        nowEpochMs: Int64 = Int64(Date().timeIntervalSince1970 * 1000)
    ) -> AiGiftAdviceBundle {
        let dossier = request.dossier
        let isEn = request.isEnglish
        let avoided = collectExclusionTokens(dossier: dossier)
        let pastTitles = Set(
            (dossier.giftHistory.map { $0.title } + Array(request.excludedGiftTitles))
                .map { normalizeText($0) }
                .filter { !$0.isEmpty }
        )
        let effectiveBudget = dossier.budgetMaxVnd

        var scored: [RichGiftRecommendation] = []

        for hint in dossier.secretSurpriseNotes where !hint.isPurchased && !hint.note.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            let normNote = normalizeText(hint.note)
            if matchesAvoidedKeyword(text: normNote, avoided: avoided) { continue }
            if pastTitles.contains(normNote) { continue }
            if effectiveBudget > 0 && hint.estimatedPriceVnd > effectiveBudget { continue }
            scored.append(
                RichGiftRecommendation(
                    id: "secret_\(hint.id)",
                    title: hint.note.trimmingCharacters(in: .whitespacesAndNewlines),
                    category: isEn ? "Secret Wish" : "Điều ước bí mật",
                    priceRangeLabel: hint.estimatedPriceVnd > 0
                        ? "~\(hint.estimatedPriceVnd / 1000)k VND"
                        : (isEn ? "From secret notes" : "Từ ghi chú bí mật"),
                    estimatedMinVnd: hint.estimatedPriceVnd,
                    estimatedMaxVnd: hint.estimatedPriceVnd,
                    matchScore: 120,
                    reason: isEn
                        ? "Directly captured from your Secret Surprise Notes — guaranteed delight!"
                        : "Lấy trực tiếp từ Ghi chú bí mật bạn đã lưu — trúng đúng điều người ấy mong!",
                    preparationPlan: isEn
                        ? "Check your note details and mark purchased once ready."
                        : "Kiểm tra lại chi tiết ghi chú và đánh dấu đã mua sau khi chuẩn bị.",
                    personalizationHint: hint.occasionHint,
                    culturalCaution: nil,
                    matchedInterests: [],
                    matchedLoveLanguages: [.receivingGifts],
                    whereToFind: hint.targetUrlOrShop.isEmpty
                        ? (isEn ? "Saved shop note" : "Cửa hàng đã ghi chú")
                        : hint.targetUrlOrShop,
                    leadTimeDays: 2
                )
            )
        }

        for arch in GiftCatalogData.archetypes {
            let title = isEn ? arch.titleEn : arch.titleVi
            let haystack = normalizeText(
                "\(arch.titleVi) \(arch.titleEn) \(arch.allergensAndTabooKeywords.joined(separator: " "))"
            )
            if matchesAvoidedKeyword(text: haystack, avoided: avoided) { continue }
            if pastTitles.contains(normalizeText(arch.titleVi)) || pastTitles.contains(normalizeText(arch.titleEn)) {
                continue
            }
            if effectiveBudget > 0 && arch.estimatedPriceVnd > effectiveBudget { continue }
            if let days = request.daysUntilOccasion, days <= 1 && arch.minLeadDays > 2 { continue }

            var score = 20
            let matchedInterests = arch.interestKeys.filter { dossier.interestCategories.contains($0) }
            let matchedLanguages = arch.loveLanguages.filter { dossier.loveLanguages.contains($0) }
            score += matchedInterests.count * 12
            score += matchedLanguages.count * 10

            var personalHint = ""
            if !dossier.sizes.ringSize.isEmpty && arch.id == "arch_engraved_silver_ring" {
                score += 12
                personalHint = isEn ? "Ring size: \(dossier.sizes.ringSize)" : "Ni nhẫn: \(dossier.sizes.ringSize)"
            } else if !dossier.sizes.shirtSize.isEmpty && arch.id == "arch_silk_scarf_or_apparel" {
                score += 10
                personalHint = isEn ? "Shirt size: \(dossier.sizes.shirtSize)" : "Size áo: \(dossier.sizes.shirtSize)"
            } else if !dossier.sizes.favoriteScents.isEmpty && arch.id == "arch_scented_candle_botanical" {
                score += 10
                let scent = dossier.sizes.favoriteScents.joined(separator: ", ")
                personalHint = isEn ? "Preferred scent: \(scent)" : "Mùi hương ưa thích: \(scent)"
            }

            scored.append(
                RichGiftRecommendation(
                    id: arch.id,
                    title: title,
                    category: isEn ? arch.categoryEn : arch.categoryVi,
                    priceRangeLabel: isEn ? arch.priceLabelEn : arch.priceLabelVi,
                    estimatedMinVnd: arch.estimatedPriceVnd,
                    estimatedMaxVnd: arch.estimatedPriceVnd,
                    matchScore: score,
                    reason: isEn ? arch.reasonTemplateEn : arch.reasonTemplateVi,
                    preparationPlan: isEn ? arch.prepEn : arch.prepVi,
                    personalizationHint: personalHint,
                    culturalCaution: nil,
                    matchedInterests: matchedInterests,
                    matchedLoveLanguages: matchedLanguages,
                    whereToFind: isEn ? arch.whereToFindEn : arch.whereToFindVi,
                    leadTimeDays: arch.minLeadDays
                )
            )
        }

        let limit = min(max(request.maxRecommendations, 1), 10)
        let top = Array(scored.sorted { $0.matchScore > $1.matchScore }.prefix(limit))

        var tabooWarnings: [String] = []
        if !dossier.foodAndCare.allergies.isEmpty {
            let list = dossier.foodAndCare.allergies.joined(separator: ", ")
            tabooWarnings.append(isEn ? "Filtered allergies: \(list)" : "Đã lọc dị ứng: \(list)")
        }
        if !dossier.foodAndCare.dislikesOrTaboos.isEmpty {
            let list = dossier.foodAndCare.dislikesOrTaboos.joined(separator: ", ")
            tabooWarnings.append(isEn ? "Avoided dislikes/taboos: \(list)" : "Đã tránh điều không thích: \(list)")
        }

        let prepTimeline = buildPrepTimeline(daysUntil: request.daysUntilOccasion, isEnglish: isEn)
        let notes = buildLoveNoteDrafts(request: request)
        let summaryHeadline = isEn
            ? "\(top.count) tailored picks for \(request.occasionTitle) (filtered by preferences & taboos)"
            : "\(top.count) gợi ý chuẩn gu cho \(request.occasionTitle) (đã lọc dị ứng & trùng lặp)"

        return AiGiftAdviceBundle(
            source: .smartLocalEngine,
            occasionId: request.occasionId,
            occasionTitle: request.occasionTitle,
            summaryHeadline: summaryHeadline,
            recommendations: top,
            loveNotes: notes,
            tabooWarnings: tabooWarnings,
            prepTimeline: prepTimeline,
            usedFreeAllowance: false,
            remainingFreeAllowance: nil,
            generatedAtEpochMs: nowEpochMs
        )
    }

    private func enforceSafetyFilters(bundle: AiGiftAdviceBundle, dossier: PartnerDossier) -> AiGiftAdviceBundle {
        let avoided = collectExclusionTokens(dossier: dossier)
        let safeRecs = bundle.recommendations.filter { rec in
            let text = normalizeText("\(rec.title) \(rec.category) \(rec.reason) \(rec.whereToFind)")
            return !matchesAvoidedKeyword(text: text, avoided: avoided)
        }
        return AiGiftAdviceBundle(
            source: bundle.source,
            occasionId: bundle.occasionId,
            occasionTitle: bundle.occasionTitle,
            summaryHeadline: bundle.summaryHeadline,
            recommendations: safeRecs,
            loveNotes: bundle.loveNotes,
            tabooWarnings: bundle.tabooWarnings,
            prepTimeline: bundle.prepTimeline,
            usedFreeAllowance: bundle.usedFreeAllowance,
            remainingFreeAllowance: bundle.remainingFreeAllowance,
            generatedAtEpochMs: bundle.generatedAtEpochMs
        )
    }

    private func collectExclusionTokens(dossier: PartnerDossier) -> Set<String> {
        let aliases: [String: [String]] = [
            "caffeine": ["coffee", "caffeine", "ca phe", "espresso"],
            "ca phe": ["coffee", "caffeine", "ca phe", "espresso"],
            "coffee": ["coffee", "caffeine", "ca phe", "espresso"],
            "nuts": ["nuts", "peanut", "dau phong", "almond"],
            "dau phong": ["nuts", "peanut", "dau phong"],
            "dairy": ["dairy", "milk", "sua", "cream"],
            "sua": ["dairy", "milk", "sua"],
            "scent": ["scent", "fragrance", "perfume", "nuoc hoa", "nen"],
            "nuoc hoa": ["scent", "fragrance", "perfume", "nuoc hoa", "nen"],
            "pollen": ["pollen", "phan hoa", "hoa", "flower"],
            "phan hoa": ["pollen", "phan hoa", "hoa", "flower"],
            "hoa": ["pollen", "phan hoa", "hoa", "flower"],
        ]
        var out = Set<String>()
        for entry in dossier.foodAndCare.allergies + dossier.foodAndCare.dislikesOrTaboos {
            let norm = normalizeText(entry)
            if norm.isEmpty { continue }
            out.insert(norm)
            for word in norm.components(separatedBy: CharacterSet(charactersIn: ",;/ ")) where word.count >= 2 {
                out.insert(word)
                if let list = aliases[word] { out.formUnion(list) }
            }
            for (k, list) in aliases where norm.contains(k) {
                out.formUnion(list)
            }
        }
        return out
    }

    private func matchesAvoidedKeyword(text: String, avoided: Set<String>) -> Bool {
        avoided.contains { keyword in
            keyword.count >= 2 && text.contains(keyword)
        }
    }

    private func normalizeText(_ input: String) -> String {
        input
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .folding(options: [.diacriticInsensitive, .caseInsensitive], locale: Locale(identifier: "en_US_POSIX"))
            .replacingOccurrences(of: "đ", with: "d")
            .replacingOccurrences(of: "Đ", with: "d")
            .lowercased()
    }

    private func buildLoveNoteDrafts(request: AiGiftRequest) -> [LoveNoteDraft] {
        let name = request.dossier.nickname.isEmpty
            ? (request.isEnglish ? "my love" : "em yêu")
            : request.dossier.nickname
        let occasion = request.occasionTitle
        if request.isEnglish {
            return [
                LoveNoteDraft(
                    id: "note_sweet_0",
                    tone: .sweet,
                    title: "Sweet Note",
                    message: "Happy \(occasion), \(name)! Every small moment with you still makes my heart smile.",
                    occasionLabel: occasion
                ),
                LoveNoteDraft(
                    id: "note_deep_1",
                    tone: .deeplyRomantic,
                    title: "Deeply Romantic",
                    message: "Dearest \(name), on \(occasion) thank you for being my safest home and brightest tomorrow.",
                    occasionLabel: occasion
                ),
                LoveNoteDraft(
                    id: "note_playful_2",
                    tone: .playful,
                    title: "Playful Note",
                    message: "Happy \(occasion), \(name)! No refund policy on my affection — still picking you every single day.",
                    occasionLabel: occasion
                ),
            ]
        }
        return [
            LoveNoteDraft(
                id: "note_sweet_0",
                tone: .sweet,
                title: "Lời chúc ngọt ngào",
                message: "Chúc \(name) \(occasion) thật ngọt ngào! Mỗi khoảnh khắc bình yên bên nhau đều là món quà đẹp nhất.",
                occasionLabel: occasion
            ),
            LoveNoteDraft(
                id: "note_deep_1",
                tone: .deeplyRomantic,
                title: "Lời chúc sâu lắng",
                message: "\(name) thương mến, nhân \(occasion), cảm ơn vì đã luôn là bến bình yên và điều dịu dàng nhất.",
                occasionLabel: occasion
            ),
            LoveNoteDraft(
                id: "note_playful_2",
                tone: .playful,
                title: "Lời chúc dí dỏm",
                message: "\(occasion) vui vẻ nha \(name)! Hợp đồng thương nhau không cho hoàn trả đâu, tụi mình gia hạn trọn đời nhé!",
                occasionLabel: occasion
            ),
        ]
    }

    private func buildPrepTimeline(daysUntil: Int?, isEnglish: Bool) -> [String] {
        guard let days = daysUntil else {
            return isEnglish
                ? ["Review Partner Dossier and prepare a thoughtful handwritten note."]
                : ["Xem lại Hồ sơ người thương và chuẩn bị một tấm thiệp viết tay."]
        }
        if days <= 1 {
            return isEnglish
                ? ["Today: Pick ready-to-buy gifts and write a personal card."]
                : ["Hôm nay: Chọn quà có sẵn trong ngày và viết thiệp tay chân thành."]
        }
        if days <= 7 {
            return isEnglish
                ? ["This week (\(days)d): Finalize gift order & confirm date reservation."]
                : ["Tuần này (\(days) ngày): Chốt đơn quà & xác nhận đặt chỗ hẹn hò."]
        }
        return isEnglish
            ? ["Early planning (\(days)d): Book dining or custom gifts ahead."]
            : ["Chuẩn bị sớm (\(days) ngày): Đặt chỗ đẹp & chọn quà đúng sở thích."]
    }
}
