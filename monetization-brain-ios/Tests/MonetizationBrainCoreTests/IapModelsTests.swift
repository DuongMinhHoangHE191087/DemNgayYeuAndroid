import XCTest
@testable import MonetizationBrainCore

final class IapModelsTests: XCTestCase {
    func test_iapProductTypeIsSubscriptionTrueForAutoRenewableAndNonRenewing() {
        XCTAssertTrue(IapProductType.autoRenewable.isSubscription)
        XCTAssertTrue(IapProductType.nonRenewing.isSubscription)
        XCTAssertFalse(IapProductType.consumable.isSubscription)
        XCTAssertFalse(IapProductType.nonConsumable.isSubscription)
    }

    func test_iapProductStoresCoreFields() {
        let product = IapProduct(
            productId: "gem_pack_1",
            type: .consumable,
            formattedPrice: "$4.99",
            priceMicros: 4_990_000,
            currencyCode: "USD",
            title: "Gem Pack",
            description: "100 gems"
        )
        XCTAssertEqual(product.productId, "gem_pack_1")
        XCTAssertEqual(product.type, .consumable)
        XCTAssertEqual(product.formattedPrice, "$4.99")
        XCTAssertEqual(product.priceMicros, 4_990_000)
        XCTAssertEqual(product.currencyCode, "USD")
        XCTAssertEqual(product.title, "Gem Pack")
        XCTAssertEqual(product.description, "100 gems")
    }

    func test_iapProductTitleAndDescriptionDefaultToEmpty() {
        let product = IapProduct(
            productId: "gem_pack_1", type: .consumable, formattedPrice: "$4.99",
            priceMicros: 4_990_000, currencyCode: "USD"
        )
        XCTAssertEqual(product.title, "")
        XCTAssertEqual(product.description, "")
    }

    func test_iapPurchaseStoresCoreFieldsAndDefaultsChannelToAppStore() {
        let purchase = IapPurchase(
            productId: "gem_pack_1", orderId: "order_1", purchaseToken: "jws...",
            purchaseTimeMs: 1_700_000_000_000, state: .purchased, isFinished: false,
            isAutoRenewing: false, quantity: 1, priceMicros: 4_990_000, currencyCode: "USD"
        )
        XCTAssertEqual(purchase.productId, "gem_pack_1")
        XCTAssertEqual(purchase.channel, .appStore)
    }

    func test_iapResultCasesAreEquatable() {
        let purchase = IapPurchase(
            productId: "p1", orderId: "o1", purchaseToken: "t1", purchaseTimeMs: 0,
            state: .purchased, isFinished: true, isAutoRenewing: false, quantity: 1,
            priceMicros: 0, currencyCode: "USD"
        )
        XCTAssertEqual(IapResult.success(purchase), IapResult.success(purchase))
        XCTAssertEqual(IapResult.userCanceled, IapResult.userCanceled)
        XCTAssertEqual(IapResult.alreadyOwned, IapResult.alreadyOwned)
        XCTAssertEqual(IapResult.failed(code: 1, message: "x"), IapResult.failed(code: 1, message: "x"))
        XCTAssertNotEqual(IapResult.userCanceled, IapResult.alreadyOwned)
    }

    func test_purchaseChannelRawValuesMatchAndroidEnumNames() {
        XCTAssertEqual(PurchaseChannel.appStore.rawValue, "APP_STORE")
        XCTAssertEqual(PurchaseChannel.webShop.rawValue, "WEB_SHOP")
    }
}
