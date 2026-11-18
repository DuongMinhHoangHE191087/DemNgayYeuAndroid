import Combine
import Foundation
import InLoveIosCore

#if canImport(UIKit)
import UIKit
#endif
#if canImport(FirebaseCore)
import FirebaseCore
#endif
#if canImport(FirebaseRemoteConfig)
import FirebaseRemoteConfig
#endif
#if canImport(MonetizationBrainCore)
import MonetizationBrainCore
#endif
#if canImport(MonetizationBrainStoreKit)
import MonetizationBrainStoreKit
#endif
#if canImport(MonetizationBrainUMP)
import MonetizationBrainUMP
#endif
#if canImport(MonetizationBrainFirebase)
import MonetizationBrainFirebase
#endif
#if canImport(GoogleMobileAds)
import GoogleMobileAds
#endif
#if canImport(AppLovinSDK)
import AppLovinSDK
#endif

public struct IosSdkKillSwitch: Codable, Equatable, Sendable {
    public var ads: Bool

    public init(ads: Bool = false) {
        self.ads = ads
    }
}

public struct IosMonetizationBootstrapStatus: Equatable, Sendable {
    public let configured: Bool
    public let sdkKillAds: Bool
    public let consentGrantedForAds: Bool
    public let brainEnabled: Bool
    public let paywallKeysApplied: [String]
    public let warnings: [String]

    public init(
        configured: Bool,
        sdkKillAds: Bool,
        consentGrantedForAds: Bool,
        brainEnabled: Bool,
        paywallKeysApplied: [String],
        warnings: [String]
    ) {
        self.configured = configured
        self.sdkKillAds = sdkKillAds
        self.consentGrantedForAds = consentGrantedForAds
        self.brainEnabled = brainEnabled
        self.paywallKeysApplied = paywallKeysApplied
        self.warnings = warnings
    }
}

/// Coordinates startup order across Firebase Remote Config, UMP consent,
/// `monetization-brain-ios`, StoreKit 2 entitlements, and SacredZone ad guards.
@MainActor
public final class IosMonetizationBrainBootstrap: ObservableObject {
    public static let sdkKillIosKey = "sdk_kill_ios"

    @Published public private(set) var status = IosMonetizationBootstrapStatus(
        configured: false,
        sdkKillAds: false,
        consentGrantedForAds: true,
        brainEnabled: false,
        paywallKeysApplied: [],
        warnings: []
    )

    private let storeKit: StoreKitEntitlementManager
    private let adManager: IosAdPlacementManager
    private let defaults: UserDefaults

    public init(
        storeKit: StoreKitEntitlementManager,
        adManager: IosAdPlacementManager,
        defaults: UserDefaults = .standard
    ) {
        self.storeKit = storeKit
        self.adManager = adManager
        self.defaults = defaults
    }

    public func stablePlayerId() -> String {
        let key = "inlove.stable_player_id"
        if let existing = defaults.string(forKey: key), !existing.isEmpty {
            return existing
        }
        let fresh = UUID().uuidString.lowercased()
        defaults.set(fresh, forKey: key)
        return fresh
    }

    public func configureAll(rootViewController: AnyObject? = nil) async {
        ensureFirebaseConfiguredIfLinked()
        let consentAllowed = await requestConsentIfAvailable(rootViewController: rootViewController)
        adManager.updateConsent(consentAllowed)

        let killSwitch = fetchKillSwitchFromRemoteConfig()
        var appliedKeys: [String] = []
        var warnings: [String] = []
        var brainEnabled = false

        #if canImport(MonetizationBrainCore)
        let sink = makeDefaultEventSink()
        let remoteProvider = makeRemoteConfigProvider()
        var config = MonetizationBrainSDK.Config(
            eventSink: sink,
            playerId: stablePlayerId(),
            remoteConfig: remoteProvider
        )
        config.brainEnabled = false
        config.brainRolloutFraction = 0.05
        config.inappProducts = [InLoveSku.lifetime]
        config.subscriptionProducts = [InLoveSku.monthly, InLoveSku.yearly]
        config.removeAdsProducts = InLoveSku.allSkus
        config.childDirected = false
        #if canImport(MonetizationBrainStoreKit)
        config.storeBackend = StoreKit2Backend()
        #endif
        #if !canImport(MonetizationBrainUMP)
        config.consentHandledExternally = true
        #endif

        let sdkReport = await MonetizationBrainSDK.configure(config)
        let rcReport = await RemoteConfigDispatcher.shared.refresh(from: remoteProvider)
        appliedKeys = rcReport.appliedKeys
        warnings.append(contentsOf: sdkReport.warnings)
        brainEnabled = sdkReport.brainEnabled
        #endif

        await storeKit.refreshAll()
        let isPro = isProEffective()
        adManager.updateProStatus(isPro)

        if !killSwitch.ads && consentAllowed && !isPro {
            startMediationSdksIfLinked()
        }

        status = IosMonetizationBootstrapStatus(
            configured: true,
            sdkKillAds: killSwitch.ads,
            consentGrantedForAds: consentAllowed,
            brainEnabled: brainEnabled,
            paywallKeysApplied: appliedKeys,
            warnings: warnings
        )
    }

    public func refreshRemoteConfig() async {
        let killSwitch = fetchKillSwitchFromRemoteConfig()
        var appliedKeys = status.paywallKeysApplied

        #if canImport(MonetizationBrainCore)
        let provider = makeRemoteConfigProvider()
        let rcReport = await RemoteConfigDispatcher.shared.refresh(from: provider)
        appliedKeys = rcReport.appliedKeys
        #endif

        adManager.updateProStatus(isProEffective())
        status = IosMonetizationBootstrapStatus(
            configured: status.configured,
            sdkKillAds: killSwitch.ads,
            consentGrantedForAds: status.consentGrantedForAds,
            brainEnabled: status.brainEnabled,
            paywallKeysApplied: appliedKeys,
            warnings: status.warnings
        )
    }

    public func canShowBanner(inZone zone: SacredZone? = nil) -> Bool {
        if let zone, adManager.activeSacredZones.contains(zone) { return false }
        if status.sdkKillAds || isProEffective() { return false }
        return adManager.shouldShowBanner()
    }

    public func canShowInterstitial(nowEpochMs: Int64 = Int64(Date().timeIntervalSince1970 * 1000)) async -> Bool {
        if status.sdkKillAds || isProEffective() { return false }
        guard adManager.evaluateInterstitial(nowEpochMs: nowEpochMs) == .allowed else {
            return false
        }
        #if canImport(MonetizationBrainCore)
        if Entitlements.shared.hasRemoveAds() { return false }
        return await AdsBrain.shared.shouldShowInterstitial()
        #else
        return true
        #endif
    }

    public func notifyInterstitialShown(nowEpochMs: Int64 = Int64(Date().timeIntervalSince1970 * 1000)) {
        adManager.recordInterstitialShown(nowEpochMs: nowEpochMs)
        #if canImport(MonetizationBrainCore)
        AdsBrain.shared.onInterstitialShown()
        #endif
    }

    public func isProEffective() -> Bool {
        #if canImport(MonetizationBrainCore)
        return storeKit.isPro || Entitlements.shared.hasRemoveAds()
        #else
        return storeKit.isPro
        #endif
    }

    private func ensureFirebaseConfiguredIfLinked() {
        #if canImport(FirebaseCore)
        if FirebaseApp.app() == nil {
            FirebaseApp.configure()
        }
        #endif
    }

    private func requestConsentIfAvailable(rootViewController: AnyObject?) async -> Bool {
        #if canImport(MonetizationBrainUMP) && canImport(UIKit)
        guard let vc = rootViewController as? UIViewController else {
            return ConsentManager.canRequestAds
        }
        return await withCheckedContinuation { continuation in
            ConsentManager.request(from: vc) { _ in
                continuation.resume(returning: ConsentManager.canRequestAds)
            }
        }
        #else
        return true
        #endif
    }

    private func fetchKillSwitchFromRemoteConfig() -> IosSdkKillSwitch {
        #if canImport(FirebaseRemoteConfig)
        let raw = RemoteConfig.remoteConfig().configValue(forKey: Self.sdkKillIosKey).stringValue
        if !raw.isEmpty,
           let data = raw.data(using: .utf8),
           let parsed = try? JSONDecoder().decode(IosSdkKillSwitch.self, from: data) {
            return parsed
        }
        #endif
        return IosSdkKillSwitch(ads: false)
    }

    private func startMediationSdksIfLinked() {
        #if canImport(GoogleMobileAds)
        MobileAds.shared.start(completionHandler: nil)
        #endif
        #if canImport(AppLovinSDK)
        if let sdkKey = Bundle.main.object(forInfoDictionaryKey: "AppLovinSdkKey") as? String, !sdkKey.isEmpty {
            let initConfig = ALSdkInitializationConfiguration(sdkKey: sdkKey) { _ in }
            ALSdk.shared().initialize(with: initConfig) { _ in }
        }
        #endif
    }

    #if canImport(MonetizationBrainCore)
    private func makeDefaultEventSink() -> EventSink {
        if let appSupport = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first {
            let queueDir = appSupport.appendingPathComponent("MonetizationBrainEvents", isDirectory: true)
            try? FileManager.default.createDirectory(at: queueDir, withIntermediateDirectories: true)
        }
        return NoopEventSink()
    }

    private func makeRemoteConfigProvider() -> RemoteConfigProvider {
        #if canImport(MonetizationBrainFirebase) && canImport(FirebaseCore)
        if FirebaseApp.app() != nil {
            return FirebaseRemoteConfigProvider()
        }
        #endif
        return StaticRemoteConfigProvider()
    }
    #endif
}

#if canImport(MonetizationBrainCore)
private struct NoopEventSink: EventSink {
    func send(_ event: SdkEvent) async {}
}
#endif
