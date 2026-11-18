import Combine
import Foundation
import StoreKit

public enum StorePurchaseOutcome: Equatable, Sendable {
    case purchased(productId: String)
    case pending
    case userCancelled
    case failed(String)
}

public protocol StoreKitBackendProtocol: Sendable {
    func loadOffers() async throws -> [SubscriptionPlanOffer]
    func purchase(productId: String) async throws -> StorePurchaseOutcome
    func restorePurchases() async throws -> EntitlementSnapshot
    func currentEntitlement() async -> EntitlementSnapshot
}

public struct AppleStoreKit2Backend: StoreKitBackendProtocol {
    public init() {}

    public func loadOffers() async throws -> [SubscriptionPlanOffer] {
        let products = try await Product.products(for: Array(InLoveSku.allSkus))
        let fallbacks = Dictionary(
            uniqueKeysWithValues: SubscriptionPlanOffer.defaultFallbackOffers().map { ($0.productId, $0) }
        )
        let mapped = products.map { product -> SubscriptionPlanOffer in
            let isYearly = product.id == InLoveSku.yearly
            let isLifetime = product.id == InLoveSku.lifetime
            let fallback = fallbacks[product.id]
            return SubscriptionPlanOffer(
                productId: product.id,
                titleVi: product.displayName.isEmpty ? defaultTitleVi(for: product.id) : product.displayName,
                titleEn: product.displayName.isEmpty ? defaultTitleEn(for: product.id) : product.displayName,
                subtitleVi: product.description.isEmpty ? defaultSubtitleVi(for: product.id) : product.description,
                subtitleEn: product.description.isEmpty ? defaultSubtitleEn(for: product.id) : product.description,
                displayPrice: product.displayPrice,
                referencePriceVnd: fallback?.referencePriceVnd ?? 0,
                referencePriceUsd: fallback?.referencePriceUsd ?? 0,
                savingsPercent: fallback?.savingsPercent ?? 0,
                taglineVi: fallback?.taglineVi ?? "",
                taglineEn: fallback?.taglineEn ?? "",
                trialDays: isYearly ? 3 : 0,
                isLifetime: isLifetime
            )
        }
        let order = InLoveSku.orderedSkus
        let sorted = mapped.sorted {
            (order.firstIndex(of: $0.productId) ?? 99) < (order.firstIndex(of: $1.productId) ?? 99)
        }
        return sorted.isEmpty ? SubscriptionPlanOffer.defaultFallbackOffers() : sorted
    }

    public func purchase(productId: String) async throws -> StorePurchaseOutcome {
        let products = try await Product.products(for: [productId])
        guard let product = products.first else {
            return .failed("Product unavailable: \(productId)")
        }
        let result = try await product.purchase()
        switch result {
        case .success(let verification):
            switch verification {
            case .verified(let transaction):
                await transaction.finish()
                return .purchased(productId: transaction.productID)
            case .unverified(_, let error):
                return .failed("Unverified transaction: \(error.localizedDescription)")
            }
        case .pending:
            return .pending
        case .userCancelled:
            return .userCancelled
        @unknown default:
            return .failed("Unknown StoreKit purchase outcome")
        }
    }

    public func restorePurchases() async throws -> EntitlementSnapshot {
        try await AppStore.sync()
        return await currentEntitlement()
    }

    public func currentEntitlement() async -> EntitlementSnapshot {
        var activeId: String?
        var isLifetime = false
        var latestExpirationMs: Int64?

        for await result in Transaction.currentEntitlements {
            guard case .verified(let tx) = result else { continue }
            guard InLoveSku.allSkus.contains(tx.productID) else { continue }
            guard tx.revocationDate == nil else { continue }
            if tx.productID == InLoveSku.lifetime {
                activeId = tx.productID
                isLifetime = true
                latestExpirationMs = nil
                break
            }
            if let exp = tx.expirationDate {
                let expMs = Int64(exp.timeIntervalSince1970 * 1000)
                if exp > Date() && (latestExpirationMs == nil || expMs > latestExpirationMs!) {
                    activeId = tx.productID
                    latestExpirationMs = expMs
                }
            } else if activeId == nil {
                activeId = tx.productID
            }
        }

        return EntitlementSnapshot(
            isPro: activeId != nil,
            activeProductId: activeId,
            isLifetime: isLifetime,
            expirationDateEpochMs: latestExpirationMs,
            lastVerifiedEpochMs: Int64(Date().timeIntervalSince1970 * 1000)
        )
    }

    private func defaultTitleVi(for id: String) -> String {
        switch id {
        case InLoveSku.yearly: return "Gói 1 Năm"
        case InLoveSku.monthly: return "Gói 1 Tháng"
        case InLoveSku.lifetime: return "Gói Trọn Đời"
        default: return id
        }
    }

    private func defaultTitleEn(for id: String) -> String {
        switch id {
        case InLoveSku.yearly: return "1-Year Plan"
        case InLoveSku.monthly: return "1-Month Plan"
        case InLoveSku.lifetime: return "Lifetime Plan"
        default: return id
        }
    }

    private func defaultSubtitleVi(for id: String) -> String {
        switch id {
        case InLoveSku.yearly: return "Dùng thử miễn phí 3 ngày, gia hạn hàng năm"
        case InLoveSku.monthly: return "Gia hạn linh hoạt mỗi tháng"
        case InLoveSku.lifetime: return "Thanh toán một lần, không gia hạn"
        default: return ""
        }
    }

    private func defaultSubtitleEn(for id: String) -> String {
        switch id {
        case InLoveSku.yearly: return "3-day free trial, then billed yearly"
        case InLoveSku.monthly: return "Flexible monthly billing"
        case InLoveSku.lifetime: return "One-time purchase, no renewal"
        default: return ""
        }
    }
}

@MainActor
public final class StoreKitEntitlementManager: ObservableObject {
    @Published public private(set) var offers: [SubscriptionPlanOffer] = SubscriptionPlanOffer.defaultFallbackOffers()
    @Published public private(set) var entitlement: EntitlementSnapshot = EntitlementSnapshot()
    @Published public private(set) var isLoading = false
    @Published public private(set) var statusMessage: String?

    private let backend: any StoreKitBackendProtocol
    private var updatesTask: Task<Void, Never>?

    public var isPro: Bool { entitlement.isPro }

    public init(
        backend: any StoreKitBackendProtocol = AppleStoreKit2Backend(),
        observeTransactions: Bool = true
    ) {
        self.backend = backend
        if observeTransactions {
            startTransactionObserver()
        }
    }

    deinit {
        updatesTask?.cancel()
    }

    public func refreshAll() async {
        isLoading = true
        defer { isLoading = false }
        if let loaded = try? await backend.loadOffers(), !loaded.isEmpty {
            offers = loaded
        }
        entitlement = await backend.currentEntitlement()
    }

    @discardableResult
    public func purchase(productId: String) async -> StorePurchaseOutcome {
        guard InLoveSku.allSkus.contains(productId) else {
            return .failed("Unknown SKU: \(productId)")
        }
        isLoading = true
        defer { isLoading = false }
        do {
            let outcome = try await backend.purchase(productId: productId)
            switch outcome {
            case .purchased:
                entitlement = await backend.currentEntitlement()
                statusMessage = "VIP unlocked"
            case .pending:
                statusMessage = "Purchase pending approval"
            case .userCancelled:
                statusMessage = nil
            case .failed(let message):
                statusMessage = message
            }
            return outcome
        } catch {
            statusMessage = error.localizedDescription
            return .failed(error.localizedDescription)
        }
    }

    @discardableResult
    public func restorePurchases() async -> EntitlementSnapshot {
        isLoading = true
        defer { isLoading = false }
        do {
            let restored = try await backend.restorePurchases()
            entitlement = restored
            statusMessage = restored.isPro ? "Purchases restored" : "No active purchases found"
            return restored
        } catch {
            statusMessage = error.localizedDescription
            return entitlement
        }
    }

    public func supportsFeature(_ feature: ProFeature) -> Bool {
        entitlement.isPro
    }

    public static func mapSkuToPlanKey(_ productId: String) -> String? {
        switch productId {
        case InLoveSku.monthly: return "monthly"
        case InLoveSku.yearly: return "yearly"
        case InLoveSku.lifetime: return "lifetime"
        default: return nil
        }
    }

    private func startTransactionObserver() {
        updatesTask = Task { [weak self] in
            for await result in Transaction.updates {
                if case .verified(let transaction) = result {
                    await transaction.finish()
                    guard let self else { return }
                    self.entitlement = await self.backend.currentEntitlement()
                }
            }
        }
    }
}
