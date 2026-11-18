import XCTest
@testable import InLoveIosCore

final class PartnerDossierAndConciergeTests: XCTestCase {
    func testPartnerDossierJsonRoundtripAndCompleteness() async throws {
        let original = PartnerDossier(
            scopeId: "couple_123",
            nickname: "Bé Mèo",
            loveLanguages: [.qualityTime, .wordsOfAffirmation],
            interestCategories: ["coffee", "books", "flowers_scents"],
            freeformLikes: "Matcha ít đá, gấu bông nhỏ",
            sizes: PartnerSizes(
                shirtSize: "S",
                pantsOrDressSize: "M",
                shoeSize: "37",
                ringSize: "11",
                favoriteColors: ["Hồng phấn", "Kem"],
                favoriteScents: ["Trà trắng"]
            ),
            foodAndCare: PartnerFoodAndCare(
                favoriteFoods: ["Bún chả", "Mochi"],
                goToDrinks: "Matcha latte ít đường",
                allergies: ["Phấn hoa", "Đậu phộng"],
                dislikesOrTaboos: ["Mùi nồng"]
            ),
            secretSurpriseNotes: [
                SecretHintNote(
                    id: "hint_1",
                    note: "Máy ảnh film Kodak",
                    occasionHint: "Sinh nhật",
                    estimatedPriceVnd: 950_000,
                    targetUrlOrShop: "Tiệm ảnh Quận 1",
                    isPurchased: false,
                    createdAtEpochMs: 1_700_000_000_000
                )
            ],
            giftHistory: [
                GivenGiftRecord(
                    id: "given_1",
                    title: "Album Ảnh Kỷ Niệm In Thủ Công",
                    occasion: "100 ngày",
                    givenDateIso: "2026-08-01",
                    priceVnd: 350_000,
                    partnerReactionRating: 5,
                    notes: "Rất thích"
                )
            ],
            budgetMaxVnd: 1_200_000,
            occasionRegion: "VN",
            updatedAtEpochMs: 1_700_000_123_000
        )

        let json = try PartnerDossierCodec.encode(original)
        let decoded = try PartnerDossierCodec.decode(json)
        XCTAssertEqual(decoded, original)
        XCTAssertEqual(decoded.completenessPercent(), 100)
        XCTAssertTrue(decoded.missingProfileHints(isEnglish: false).isEmpty)

        let tempDir = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString, isDirectory: true)
        let store = FilePartnerDossierStore(directoryURL: tempDir)
        try await store.saveDossier(original)
        let reloaded = await store.loadDossier(scopeId: "couple_123")
        XCTAssertEqual(reloaded, original)
    }

    func testGiftConciergeExcludesAllergiesPastGiftsAndRespectsBudget() async {
        let dossier = PartnerDossier(
            scopeId: "test",
            nickname: "Linh",
            loveLanguages: [.qualityTime, .receivingGifts],
            interestCategories: ["coffee", "flowers_scents", "skincare"],
            sizes: PartnerSizes(ringSize: "10", favoriteScents: ["Vanille"]),
            foodAndCare: PartnerFoodAndCare(
                goToDrinks: "Latte",
                allergies: ["caffeine", "pollen"],
                dislikesOrTaboos: ["scent"]
            ),
            secretSurpriseNotes: [
                SecretHintNote(
                    id: "h1",
                    note: "Khăn choàng cashmere",
                    occasionHint: "Noel",
                    estimatedPriceVnd: 550_000
                )
            ],
            giftHistory: [
                GivenGiftRecord(
                    id: "g1",
                    title: "Gentle Fragrance-Free Skincare Set",
                    occasion: "Birthday",
                    givenDateIso: "2026-06-01"
                )
            ],
            budgetMaxVnd: 800_000,
            occasionRegion: "VN"
        )

        let service = IosGiftConciergeService()
        let request = AiGiftRequest(
            dossier: dossier,
            occasionId: "christmas_eve",
            occasionTitle: "Christmas Eve",
            daysUntilOccasion: 5,
            loveDays: 240,
            tone: .sweet,
            isEnglish: true,
            maxRecommendations: 5
        )
        let bundle = await service.recommend(request: request, nowEpochMs: 1_700_000_000_000)

        XCTAssertEqual(bundle.source, .smartLocalEngine)
        XCTAssertFalse(bundle.recommendations.isEmpty)
        XCTAssertEqual(bundle.recommendations.first?.id, "secret_h1")
        XCTAssertFalse(bundle.tabooWarnings.isEmpty)
        XCTAssertFalse(bundle.loveNotes.isEmpty)

        for rec in bundle.recommendations {
            let text = "\(rec.title) \(rec.category)".lowercased()
            XCTAssertFalse(text.contains("coffee"), "Coffee item should be excluded due to caffeine allergy")
            XCTAssertFalse(text.contains("rose bouquet"), "Floral bouquet should be excluded due to pollen allergy")
            XCTAssertFalse(text.contains("candle"), "Scented candle should be excluded due to scent taboo")
            XCTAssertNotEqual(rec.title, "Gentle Fragrance-Free Skincare Set")
            XCTAssertLessThanOrEqual(rec.estimatedMinVnd, 800_000)
        }
    }
}
