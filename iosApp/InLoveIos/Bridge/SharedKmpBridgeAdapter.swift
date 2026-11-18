import Foundation
import InLoveIosCore

#if canImport(Shared)
import Shared
#endif

/// Adapter bridging `:shared` KMP `IosInLoveFacade` classes (`Shared.framework`)
/// with clean fallback to `InLoveIosCore` so previews, SwiftPM builds, and Xcode targets all run seamlessly.
public final class SharedKmpBridgeAdapter: Sendable {
    private let calculator = LoveDaysCalculator()
    private let occasionEngine = OccasionEngine()
    private let localConcierge = IosGiftConciergeService()

    #if canImport(Shared)
    private let kmpCalculator = IosLoveCalculatorFacade()
    private let kmpOccasions = IosOccasionEngineFacade()
    private let kmpDossier = IosPartnerDossierFacade()
    private let kmpConcierge = IosGiftConciergeFacade()
    private let kmpMonetization = IosMonetizationPolicyFacade()
    private let kmpWidgetCodec = IosWidgetSnapshotCodec()
    private let kmpOnlineSync = IosOnlineSyncFacade()
    private let kmpGuide = IosGuideFacade()
    #endif

    public init() {}

    public func loveDaysBetween(startDateIso: String, todayIso: String) -> Int {
        #if canImport(Shared)
        if let days = try? kmpCalculator.loveDaysBetween(startDateIso: startDateIso, todayIso: todayIso) {
            return Int(days)
        }
        #endif
        let ref = calculator.parseDate(todayIso) ?? Date()
        return calculator.loveDays(startDateText: startDateIso, referenceDate: ref)
    }

    public func nextOccasionReadiness(
        todayIso: String,
        loveStartDateIso: String,
        partnerBirthdayIso: String,
        region: String,
        includeMonthiversary: Bool
    ) -> NextOccasionReadiness? {
        #if canImport(Shared)
        if let json = try? kmpOccasions.nextOccasionReadinessJson(
            todayIso: todayIso,
            loveStartDateIso: loveStartDateIso,
            partnerBirthdayIso: partnerBirthdayIso,
            region: region,
            includeMonthiversary: includeMonthiversary
        ), !json.isEmpty,
           let decoded = try? JSONDecoder().decode(NextOccasionReadiness.self, from: Data(json.utf8)) {
            return decoded
        }
        #endif
        let today = calculator.parseDate(todayIso) ?? Date()
        let start = calculator.parseDate(loveStartDateIso)
        return occasionEngine.nextOccasionReadiness(referenceDate: today, startDate: start, region: region)
    }

    public func upcomingOccasions(
        todayIso: String,
        loveStartDateIso: String,
        partnerBirthdayIso: String,
        region: String,
        withinDays: Int,
        includeMonthiversary: Bool
    ) -> [NextOccasionReadiness] {
        #if canImport(Shared)
        if let json = try? kmpOccasions.upcomingOccasionsJson(
            todayIso: todayIso,
            loveStartDateIso: loveStartDateIso,
            partnerBirthdayIso: partnerBirthdayIso,
            region: region,
            withinDays: Int32(withinDays),
            includeMonthiversary: includeMonthiversary
        ), let decoded = try? JSONDecoder().decode([NextOccasionReadiness].self, from: Data(json.utf8)) {
            return decoded
        }
        #endif
        let today = calculator.parseDate(todayIso) ?? Date()
        let start = calculator.parseDate(loveStartDateIso)
        return occasionEngine.upcomingOccasions(referenceDate: today, startDate: start, region: region)
    }

    public func upcomingMilestones(startDateIso: String, todayIso: String, includeMonthiversaries: Bool) -> [LoveMilestoneInfo] {
        guard let start = calculator.parseDate(startDateIso) else { return [] }
        let ref = calculator.parseDate(todayIso) ?? Date()
        return calculator.upcomingMilestones(startDate: start, referenceDate: ref, includeMonthiversaries: includeMonthiversaries)
    }

    public func completenessPercent(for dossier: PartnerDossier) -> Int {
        #if canImport(Shared)
        if let json = try? PartnerDossierCodec.encode(dossier) {
            return Int(kmpDossier.completenessPercent(dossierJson: json))
        }
        #endif
        return dossier.completenessPercent()
    }

    public func missingHints(for dossier: PartnerDossier, isEnglish: Bool) -> [String] {
        #if canImport(Shared)
        if let json = try? PartnerDossierCodec.encode(dossier) {
            let hintsJson = kmpDossier.missingProfileHintsJson(dossierJson: json, isEnglish: isEnglish)
            if let decoded = try? JSONDecoder().decode([String].self, from: Data(hintsJson.utf8)) {
                return decoded
            }
        }
        #endif
        return dossier.missingProfileHints(isEnglish: isEnglish)
    }

    public func generateLocalAdvice(request: AiGiftRequest, nowEpochMs: Int64) -> AiGiftAdviceBundle {
        #if canImport(Shared)
        if let dossierJson = try? PartnerDossierCodec.encode(request.dossier),
           let bundleJson = try? kmpConcierge.generateLocalAdviceJson(
            dossierJson: dossierJson,
            occasionId: request.occasionId,
            occasionTitle: request.occasionTitle,
            daysUntilOccasion: Int32(request.daysUntilOccasion ?? -1),
            loveDays: Int32(request.loveDays),
            toneName: request.tone.rawValue,
            isEnglish: request.isEnglish,
            maxRecommendations: Int32(request.maxRecommendations),
            nowEpochMs: nowEpochMs
           ),
           let decoded = try? JSONDecoder().decode(AiGiftAdviceBundle.self, from: Data(bundleJson.utf8)) {
            return decoded
        }
        #endif
        return localConcierge.generateSmartLocalAdvice(request: request, nowEpochMs: nowEpochMs)
    }

    public func isSacredCelebrationToday(todayIso: String, loveStartDateIso: String, partnerBirthdayIso: String) -> Bool {
        #if canImport(Shared)
        return kmpOccasions.isSacredCelebrationToday(
            todayIso: todayIso,
            loveStartDateIso: loveStartDateIso,
            partnerBirthdayIso: partnerBirthdayIso
        )
        #else
        if let readiness = nextOccasionReadiness(
            todayIso: todayIso,
            loveStartDateIso: loveStartDateIso,
            partnerBirthdayIso: partnerBirthdayIso,
            region: "VN",
            includeMonthiversary: false
        ) {
            return readiness.stage == .todayCelebration && readiness.coupleRelevance == .core
        }
        return false
        #endif
    }

    public func encodeWidgetSnapshot(_ snapshot: CoupleWidgetSnapshot) -> String? {
        #if canImport(Shared)
        return kmpWidgetCodec.encodeSnapshotJson(
            version: Int32(snapshot.version),
            coupleTitle: snapshot.coupleTitle,
            partner1Name: snapshot.partner1Name,
            partner2Name: snapshot.partner2Name,
            loveDays: Int32(snapshot.loveDays),
            startDateIso: snapshot.startDateIso,
            nextOccasionTitle: snapshot.nextOccasionTitle,
            nextOccasionDaysRemaining: Int32(snapshot.nextOccasionDaysRemaining ?? 0),
            hasNextOccasion: snapshot.nextOccasionDaysRemaining != nil,
            redacted: snapshot.redacted,
            updatedAtEpochMs: snapshot.updatedAtEpochMs
        )
        #else
        return try? WidgetSnapshotStorage.encodeJSON(snapshot)
        #endif
    }

    public func buildCloudFunctionPayload(for request: AiGiftRequest) -> [String: Any] {
        [
            "language": request.isEnglish ? "en" : "vi",
            "occasion": request.occasionTitle,
            "occasionId": request.occasionId,
            "daysUntil": request.daysUntilOccasion ?? 7,
            "loveDays": request.loveDays,
            "budgetMaxVnd": request.dossier.budgetMaxVnd,
            "tone": request.tone.rawValue,
            "region": request.dossier.occasionRegion,
            "interestCategories": Array(request.dossier.interestCategories),
            "loveLanguages": request.dossier.loveLanguages.map(\.rawValue),
            "allergies": request.dossier.foodAndCare.allergies,
            "dislikes": request.dossier.foodAndCare.dislikesOrTaboos,
            "favoriteFoods": request.dossier.foodAndCare.favoriteFoods,
            "goToDrinks": request.dossier.foodAndCare.goToDrinks,
            "secretNotes": request.dossier.secretSurpriseNotes.filter { !$0.isPurchased }.map(\.note),
            "pastGiftTitles": request.dossier.giftHistory.map(\.title),
        ]
    }

    public func extractCoupleCode(_ raw: String) -> String {
        #if canImport(Shared)
        return kmpOnlineSync.extractCoupleCode(raw: raw)
        #else
        return CoupleCodeParser.extractCode(raw)
        #endif
    }

    public func mergeDossier(local: PartnerDossier, remote: PartnerDossier) -> PartnerDossier {
        #if canImport(Shared)
        if let localJson = try? PartnerDossierCodec.encode(local),
           let remoteJson = try? PartnerDossierCodec.encode(remote) {
            let mergedJson = kmpOnlineSync.mergeDossierJson(localJson: localJson, remoteJson: remoteJson)
            if let merged = try? PartnerDossierCodec.decode(mergedJson) {
                return merged
            }
        }
        #endif
        return DossierMergePolicy.merge(local: local, remote: remote)
    }

    public func guideChapters() -> [GuideChapter] {
        #if canImport(Shared)
        let json = kmpGuide.allChaptersJson()
        if let data = json.data(using: .utf8),
           let decoded = try? JSONDecoder().decode([GuideChapter].self, from: data),
           !decoded.isEmpty {
            return decoded
        }
        #endif
        return InLoveGuideCatalog.chapters
    }
}
