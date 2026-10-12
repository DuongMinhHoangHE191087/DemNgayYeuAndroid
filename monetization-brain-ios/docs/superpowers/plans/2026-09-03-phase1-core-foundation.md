# Phase 1: Core Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stand up `MonetizationBrainIOS`, a Swift Package with consent, event pipeline, remote-config abstraction, holdout bucketing, and a public `AdsBrain` facade whose decision points return safe baselines — the plumbing every later phase (bandit engine, IAP, paywall, ...) builds on.

**Architecture:** Two SPM library products in one package. `MonetizationBrainCore` has zero third-party dependencies: consent state, a remote-config protocol with a static in-memory implementation, a durable on-disk event queue that POSTs to a configurable HTTP endpoint, deterministic FNV-1a holdout bucketing (verified byte-for-byte against the Android SDK), and the `AdsBrain` facade. `MonetizationBrainUMP` is a separate, optional product that wraps the Google User Messaging Platform iOS SDK for CMP consent — an app that doesn't want it never pulls it in.

**Tech Stack:** Swift Package Manager, Swift tools version 5.10 (Swift 5 language mode — deliberately not Swift 6 strict concurrency, to keep this foundation phase's singletons/locks simple), iOS 15+, XCTest, Foundation (`URLSession`, `FileManager`), Google User Messaging Platform iOS SDK via SPM (`MonetizationBrainUMP` target only).

**Source spec:** `docs/superpowers/specs/2026-09-03-core-foundation-design.md`.

**Local repo:** `/Users/dungdm/Documents/SDK/monetization-brain-ios` (all commands below assume this as the working directory).

---

## Task 0: Package scaffold

**Files:**
- Create: `Package.swift`
- Create: `Sources/MonetizationBrainCore/MonetizationBrainCore.swift`
- Create: `Tests/MonetizationBrainCoreTests/MonetizationBrainCoreTests.swift`

- [ ] **Step 1: Write `Package.swift`**

```swift
// swift-tools-version: 5.10
import PackageDescription

let package = Package(
    name: "MonetizationBrainIOS",
    platforms: [
        .iOS(.v15)
    ],
    products: [
        .library(
            name: "MonetizationBrainCore",
            targets: ["MonetizationBrainCore"]
        )
    ],
    targets: [
        .target(
            name: "MonetizationBrainCore",
            dependencies: []
        ),
        .testTarget(
            name: "MonetizationBrainCoreTests",
            dependencies: ["MonetizationBrainCore"]
        )
    ]
)
```

- [ ] **Step 2: Write `Sources/MonetizationBrainCore/MonetizationBrainCore.swift`**

```swift
/// Package-level identity. Bump `version` when Phase 1 ships as a tagged release.
public enum MonetizationBrainSDK {
    public static let version = "0.1.0-phase1"
}
```

- [ ] **Step 3: Write `Tests/MonetizationBrainCoreTests/MonetizationBrainCoreTests.swift`**

```swift
import XCTest
@testable import MonetizationBrainCore

final class MonetizationBrainCoreTests: XCTestCase {
    func test_versionIsSet() {
        XCTAssertFalse(MonetizationBrainSDK.version.isEmpty)
    }
}
```

- [ ] **Step 4: Run the test**

Run: `swift test`
Expected: `Executed 1 test, with 0 failures`

- [ ] **Step 5: Commit**

```bash
git add Package.swift Sources Tests
git commit -m "chore: scaffold Swift package"
```

---

## Task 1: Consent (`ConsentTier`, `ConsentState`)

Direct port of Android's `com.app.plugin.consent.ConsentState`. Pure logic, no platform dependency, so the policy — what the SDK is and isn't allowed to do at each consent tier — is testable without any UI.

**Files:**
- Create: `Sources/MonetizationBrainCore/Consent/ConsentTier.swift`
- Create: `Sources/MonetizationBrainCore/Consent/ConsentState.swift`
- Test: `Tests/MonetizationBrainCoreTests/ConsentStateTests.swift`

- [ ] **Step 1: Write the failing test**

```swift
import XCTest
@testable import MonetizationBrainCore

final class ConsentStateTests: XCTestCase {
    override func setUp() {
        super.setUp()
        ConsentState.shared.resetForTest()
    }

    override func tearDown() {
        ConsentState.shared.resetForTest()
        super.tearDown()
    }

    func test_startsAtUnknown() {
        XCTAssertEqual(ConsentState.shared.tier(), .unknown)
    }

    func test_unknownAllowsProfilingAndPersonalization() {
        XCTAssertTrue(ConsentState.shared.allowsProfiling())
        XCTAssertTrue(ConsentState.shared.allowsPersonalization())
    }

    func test_deniedBlocksProfilingAndPersonalization() {
        ConsentState.shared.setTier(.denied)
        XCTAssertFalse(ConsentState.shared.allowsProfiling())
        XCTAssertFalse(ConsentState.shared.allowsPersonalization())
    }

    func test_partialBlocksProfilingButAllowsPersonalization() {
        ConsentState.shared.setTier(.partial)
        XCTAssertFalse(ConsentState.shared.allowsProfiling())
        XCTAssertTrue(ConsentState.shared.allowsPersonalization())
    }

    func test_grantedAllowsProfilingAndPersonalization() {
        ConsentState.shared.setTier(.granted)
        XCTAssertTrue(ConsentState.shared.allowsProfiling())
        XCTAssertTrue(ConsentState.shared.allowsPersonalization())
    }

    func test_guardrailsAndDecisionLoggingAlwaysAllowedRegardlessOfTier() {
        for tier: ConsentTier in [.unknown, .denied, .partial, .granted] {
            ConsentState.shared.setTier(tier)
            XCTAssertTrue(ConsentState.shared.allowsGuardrails())
            XCTAssertTrue(ConsentState.shared.allowsDecisionLogging())
        }
    }

    func test_settingSameTierDoesNotNotifyListeners() {
        var notifications: [ConsentTier] = []
        ConsentState.shared.onChange { notifications.append($0) }
        ConsentState.shared.setTier(.unknown) // already .unknown
        XCTAssertTrue(notifications.isEmpty)
    }

    func test_settingDifferentTierNotifiesAllListenersInOrder() {
        var firstSeen: [ConsentTier] = []
        var secondSeen: [ConsentTier] = []
        ConsentState.shared.onChange { firstSeen.append($0) }
        ConsentState.shared.onChange { secondSeen.append($0) }

        ConsentState.shared.setTier(.granted)

        XCTAssertEqual(firstSeen, [.granted])
        XCTAssertEqual(secondSeen, [.granted])
    }

    func test_childDirectedAndUnderAgeDefaultToNil() {
        XCTAssertNil(ConsentState.shared.childDirected)
        XCTAssertNil(ConsentState.shared.underAgeOfConsent)
    }

    func test_settersUpdateChildDirectedAndUnderAge() {
        ConsentState.shared.setChildDirected(true)
        ConsentState.shared.setUnderAgeOfConsent(false)
        XCTAssertEqual(ConsentState.shared.childDirected, true)
        XCTAssertEqual(ConsentState.shared.underAgeOfConsent, false)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `swift test --filter ConsentStateTests`
Expected: FAIL — `cannot find type 'ConsentTier' in scope` (neither file exists yet)

- [ ] **Step 3: Write `Sources/MonetizationBrainCore/Consent/ConsentTier.swift`**

```swift
/// Consent tiers used to decide how much personalization the SDK is allowed to do.
///
/// `.unknown` is the most important case here. A single boolean cannot distinguish "the user
/// declined" from "nobody has asked yet" — collapsing those two forces a choice between two bad
/// outcomes: treat everyone as declined (a studio outside a consent-mandatory region loses
/// personalization the moment it upgrades the SDK), or treat everyone as granted (which does
/// exactly what consent regulation forbids). The SDK never invents a refusal nobody gave.
public enum ConsentTier: Sendable, Equatable {
    /// Consent has not been configured yet. Behavior stays as if consent were granted, but
    /// `AdsBrain.selfCheckReport()` warns about it.
    case unknown

    /// The user declined, or consent is required and hasn't been obtained.
    case denied

    /// Partially granted. Treated conservatively — see `ConsentState.allowsProfiling()` —
    /// because there is no per-feature masking yet.
    case partial

    /// Fully granted.
    case granted
}
```

- [ ] **Step 4: Write `Sources/MonetizationBrainCore/Consent/ConsentState.swift`**

```swift
import Foundation

/// Current consent tier and the permissions it unlocks.
///
/// Kept separate from any UMP/consent-flow wrapper (see `MonetizationBrainUMP.ConsentManager`)
/// so the actual policy — what the SDK is and isn't allowed to do — is testable with pure
/// Swift, no platform APIs involved.
///
/// Listener closures here are non-throwing by type (`(ConsentTier) -> Void`), which is the
/// Swift-native way to get the same guarantee Android's `try/catch`-per-listener gives: a
/// well-behaved listener cannot interrupt notification of the others. (A listener that traps —
/// force-unwraps `nil`, for example — is not something either language can recover from, so
/// this is not a regression from the Android original.)
public final class ConsentState: @unchecked Sendable {
    public static let shared = ConsentState()

    private let lock = NSLock()
    private var current: ConsentTier = .unknown
    private var listeners: [(ConsentTier) -> Void] = []
    private var _childDirected: Bool?
    private var _underAgeOfConsent: Bool?

    private init() {}

    /// Whether the app targets children (COPPA). `nil` means the host app hasn't declared this;
    /// the SDK never asserts that legal claim on the app's behalf.
    public var childDirected: Bool? {
        lock.lock()
        defer { lock.unlock() }
        return _childDirected
    }

    /// Whether the user is under the age of consent (GDPR). `nil` means not declared.
    public var underAgeOfConsent: Bool? {
        lock.lock()
        defer { lock.unlock() }
        return _underAgeOfConsent
    }

    public func setChildDirected(_ value: Bool?) {
        lock.lock()
        _childDirected = value
        lock.unlock()
    }

    public func setUnderAgeOfConsent(_ value: Bool?) {
        lock.lock()
        _underAgeOfConsent = value
        lock.unlock()
    }

    public func tier() -> ConsentTier {
        lock.lock()
        defer { lock.unlock() }
        return current
    }

    /// Changes the consent tier. This is a first-class event: listeners must be notified so
    /// cached profiles can be invalidated immediately when consent is withdrawn. A no-op call
    /// (setting the tier it already is) notifies nobody.
    public func setTier(_ tier: ConsentTier) {
        lock.lock()
        guard tier != current else {
            lock.unlock()
            return
        }
        current = tier
        let listenersSnapshot = listeners
        lock.unlock()

        for listener in listenersSnapshot {
            listener(tier)
        }
    }

    public func onChange(_ listener: @escaping (ConsentTier) -> Void) {
        lock.lock()
        listeners.append(listener)
        lock.unlock()
    }

    // MARK: - Permissions

    /// Allowed to accumulate a behavioral profile (update belief state)?
    ///
    /// `.partial` deliberately returns `false`: partial consent should only use features that
    /// were actually consented to, but there's no per-feature masking yet. When we can't tell
    /// which feature is allowed, the conservative choice is to not accumulate anything.
    public func allowsProfiling() -> Bool {
        let t = tier()
        return t == .unknown || t == .granted
    }

    /// Allowed to use an EXISTING profile for personalization?
    public func allowsPersonalization() -> Bool {
        tier() != .denied
    }

    /// Guardrails always run, at every tier. They only protect the player — no interpretation
    /// of consent regulation makes protecting a user require that user's permission first.
    public func allowsGuardrails() -> Bool { true }

    /// Decision logs are still written when consent is denied — the log contains a
    /// pseudonymous id, the chosen action, and propensity; no PII.
    public func allowsDecisionLogging() -> Bool { true }

    /// Test-only: reset all state. Not exposed outside the package.
    func resetForTest() {
        lock.lock()
        current = .unknown
        _childDirected = nil
        _underAgeOfConsent = nil
        listeners.removeAll()
        lock.unlock()
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `swift test --filter ConsentStateTests`
Expected: PASS — all 10 tests green

- [ ] **Step 6: Commit**

```bash
git add Sources/MonetizationBrainCore/Consent Tests/MonetizationBrainCoreTests/ConsentStateTests.swift
git commit -m "feat: port ConsentState/ConsentTier from Android"
```

---

## Task 2: Remote config (`RemoteConfigProvider`, `StaticRemoteConfigProvider`)

**Files:**
- Create: `Sources/MonetizationBrainCore/Config/RemoteConfigProvider.swift`
- Create: `Sources/MonetizationBrainCore/Config/StaticRemoteConfigProvider.swift`
- Test: `Tests/MonetizationBrainCoreTests/StaticRemoteConfigProviderTests.swift`

- [ ] **Step 1: Write the failing test**

```swift
import XCTest
@testable import MonetizationBrainCore

final class StaticRemoteConfigProviderTests: XCTestCase {
    func test_returnsNilForMissingKey() {
        let provider = StaticRemoteConfigProvider()
        XCTAssertNil(provider.string(forKey: "missing"))
    }

    func test_returnsSeededValue() {
        let provider = StaticRemoteConfigProvider(values: ["flag": "on"])
        XCTAssertEqual(provider.string(forKey: "flag"), "on")
    }

    func test_updateReplacesValues() {
        let provider = StaticRemoteConfigProvider(values: ["flag": "on"])
        provider.update(["flag": "off", "other": "1"])
        XCTAssertEqual(provider.string(forKey: "flag"), "off")
        XCTAssertEqual(provider.string(forKey: "other"), "1")
    }

    func test_updateWithEmptyValuesKeepsCurrentValues() {
        let provider = StaticRemoteConfigProvider(values: ["flag": "on"])
        provider.update([:])
        XCTAssertEqual(provider.string(forKey: "flag"), "on")
    }

    func test_fetchAndActivateIsNoOp() async {
        let provider = StaticRemoteConfigProvider(values: ["flag": "on"])
        await provider.fetchAndActivate()
        XCTAssertEqual(provider.string(forKey: "flag"), "on")
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `swift test --filter StaticRemoteConfigProviderTests`
Expected: FAIL — `cannot find 'StaticRemoteConfigProvider' in scope`

- [ ] **Step 3: Write `Sources/MonetizationBrainCore/Config/RemoteConfigProvider.swift`**

```swift
/// Abstraction over "where remote-controlled configuration values come from."
///
/// Phase 1 ships only `StaticRemoteConfigProvider`. A real Firebase Remote Config-backed
/// implementation is a later, optional addition — this protocol exists so
/// `MonetizationBrainCore` never has to import Firebase to read a config value, matching the
/// Android finding that `com.app.plugin.brain` never imports Firebase directly either.
public protocol RemoteConfigProvider: Sendable {
    /// Returns the string value for `key`, or `nil` if the key is absent.
    func string(forKey key: String) -> String?

    /// Fetches the latest values and activates them. `StaticRemoteConfigProvider` treats this
    /// as a no-op since it has no remote source to fetch from.
    func fetchAndActivate() async
}
```

- [ ] **Step 4: Write `Sources/MonetizationBrainCore/Config/StaticRemoteConfigProvider.swift`**

```swift
import Foundation

/// An in-memory `RemoteConfigProvider` an app seeds itself (from its own JSON/plist/whatever).
/// The only concrete `RemoteConfigProvider` implementation Phase 1 ships.
public final class StaticRemoteConfigProvider: RemoteConfigProvider, @unchecked Sendable {
    private let lock = NSLock()
    private var values: [String: String]

    public init(values: [String: String] = [:]) {
        self.values = values
    }

    public func string(forKey key: String) -> String? {
        lock.lock()
        defer { lock.unlock() }
        return values[key]
    }

    /// Replaces the entire value set. Empty input keeps the current values rather than clearing
    /// them — a malformed config payload should never silently disable everything.
    public func update(_ newValues: [String: String]) {
        guard !newValues.isEmpty else { return }
        lock.lock()
        values = newValues
        lock.unlock()
    }

    public func fetchAndActivate() async {
        // No remote source — nothing to fetch.
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `swift test --filter StaticRemoteConfigProviderTests`
Expected: PASS — all 5 tests green

- [ ] **Step 6: Commit**

```bash
git add Sources/MonetizationBrainCore/Config Tests/MonetizationBrainCoreTests/StaticRemoteConfigProviderTests.swift
git commit -m "feat: add RemoteConfigProvider protocol and static implementation"
```

---

## Task 3: Event model (`SdkEvent`, `SdkEventValue`)

**Files:**
- Create: `Sources/MonetizationBrainCore/Events/SdkEvent.swift`
- Test: `Tests/MonetizationBrainCoreTests/SdkEventTests.swift`

- [ ] **Step 1: Write the failing test**

```swift
import XCTest
@testable import MonetizationBrainCore

final class SdkEventTests: XCTestCase {
    func test_encodesAndDecodesRoundTrip() throws {
        let event = SdkEvent(
            name: "debug_ping",
            timestampMs: 1_725_000_000_000,
            payload: [
                "count": .int(3),
                "ratio": .double(0.5),
                "label": .string("phase1"),
                "flag": .bool(true)
            ],
            dedupKey: "abc-123"
        )

        let data = try JSONEncoder().encode(event)
        let decoded = try JSONDecoder().decode(SdkEvent.self, from: data)

        XCTAssertEqual(decoded, event)
    }

    func test_dedupKeyDefaultsToNil() {
        let event = SdkEvent(name: "debug_ping")
        XCTAssertNil(event.dedupKey)
    }

    func test_payloadDefaultsToEmpty() {
        let event = SdkEvent(name: "debug_ping")
        XCTAssertTrue(event.payload.isEmpty)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `swift test --filter SdkEventTests`
Expected: FAIL — `cannot find 'SdkEvent' in scope`

- [ ] **Step 3: Write `Sources/MonetizationBrainCore/Events/SdkEvent.swift`**

```swift
import Foundation

/// A single value in an `SdkEvent` payload. Mirrors the small set of JSON-safe types the
/// backend contract (Android doc 46) actually needs — not a general-purpose JSON type.
public enum SdkEventValue: Codable, Equatable, Sendable {
    case string(String)
    case int(Int)
    case double(Double)
    case bool(Bool)

    public init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        if let value = try? container.decode(Bool.self) {
            self = .bool(value)
        } else if let value = try? container.decode(Int.self) {
            self = .int(value)
        } else if let value = try? container.decode(Double.self) {
            self = .double(value)
        } else if let value = try? container.decode(String.self) {
            self = .string(value)
        } else {
            throw DecodingError.dataCorruptedError(
                in: container,
                debugDescription: "SdkEventValue must be a string, int, double, or bool"
            )
        }
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()
        switch self {
        case .string(let value): try container.encode(value)
        case .int(let value): try container.encode(value)
        case .double(let value): try container.encode(value)
        case .bool(let value): try container.encode(value)
        }
    }
}

/// Envelope for one event this SDK reports. Shaped to match the backend ingestion contract
/// (Android doc 46), which is explicitly independent of client stack — events this SDK sends
/// land in the same pipeline the Android SDK already uses.
public struct SdkEvent: Codable, Equatable, Sendable {
    public let name: String
    public let timestampMs: Int64
    public let payload: [String: SdkEventValue]

    /// Optional idempotency key. `nil` means "dedup at the source" (the SDK guarantees it
    /// won't emit this event twice) — matching how several Android events, e.g.
    /// `experiment_exposure`, deliberately omit a dedup key rather than invent one.
    public let dedupKey: String?

    public init(
        name: String,
        timestampMs: Int64 = Int64(Date().timeIntervalSince1970 * 1000),
        payload: [String: SdkEventValue] = [:],
        dedupKey: String? = nil
    ) {
        self.name = name
        self.timestampMs = timestampMs
        self.payload = payload
        self.dedupKey = dedupKey
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `swift test --filter SdkEventTests`
Expected: PASS — all 3 tests green

- [ ] **Step 5: Commit**

```bash
git add Sources/MonetizationBrainCore/Events/SdkEvent.swift Tests/MonetizationBrainCoreTests/SdkEventTests.swift
git commit -m "feat: add SdkEvent envelope"
```

---

## Task 4: Network sending (`EventSender`, `URLSessionEventSender`)

**Files:**
- Create: `Sources/MonetizationBrainCore/Events/EventSender.swift`
- Create: `Sources/MonetizationBrainCore/Events/URLSessionEventSender.swift`
- Test: `Tests/MonetizationBrainCoreTests/URLSessionEventSenderTests.swift`

- [ ] **Step 1: Write the failing test**

```swift
import XCTest
@testable import MonetizationBrainCore

final class URLSessionEventSenderTests: XCTestCase {
    private func makeSession() -> URLSession {
        let config = URLSessionConfiguration.ephemeral
        config.protocolClasses = [StubURLProtocol.self]
        return URLSession(configuration: config)
    }

    override func tearDown() {
        StubURLProtocol.stub = nil
        super.tearDown()
    }

    func test_sendSucceedsOn2xxResponse() async throws {
        StubURLProtocol.stub = { request in
            let response = HTTPURLResponse(url: request.url!, statusCode: 200, httpVersion: nil, headerFields: nil)!
            return (response, Data())
        }
        let sender = URLSessionEventSender(endpoint: URL(string: "https://example.com/events")!, session: makeSession())
        try await sender.send(SdkEvent(name: "debug_ping"))
    }

    func test_sendThrowsOnNon2xxResponse() async {
        StubURLProtocol.stub = { request in
            let response = HTTPURLResponse(url: request.url!, statusCode: 500, httpVersion: nil, headerFields: nil)!
            return (response, Data())
        }
        let sender = URLSessionEventSender(endpoint: URL(string: "https://example.com/events")!, session: makeSession())

        do {
            try await sender.send(SdkEvent(name: "debug_ping"))
            XCTFail("Expected send to throw")
        } catch EventSenderError.httpError(let statusCode) {
            XCTAssertEqual(statusCode, 500)
        } catch {
            XCTFail("Expected EventSenderError.httpError, got \(error)")
        }
    }

    func test_sendPostsEventBodyAsJson() async throws {
        var capturedBody: Data?
        StubURLProtocol.stub = { request in
            capturedBody = request.bodyDataForTesting
            let response = HTTPURLResponse(url: request.url!, statusCode: 200, httpVersion: nil, headerFields: nil)!
            return (response, Data())
        }
        let sender = URLSessionEventSender(endpoint: URL(string: "https://example.com/events")!, session: makeSession())
        try await sender.send(SdkEvent(name: "debug_ping", timestampMs: 42, payload: ["k": .string("v")]))

        let body = try XCTUnwrap(capturedBody)
        let decoded = try JSONDecoder().decode(SdkEvent.self, from: body)
        XCTAssertEqual(decoded.name, "debug_ping")
        XCTAssertEqual(decoded.timestampMs, 42)
    }
}

/// Minimal `URLProtocol` stub. The loading system moves a POST's `httpBody` into
/// `httpBodyStream` before handing the request to `startLoading`, so the body is read back out
/// of the stream via `bodyDataForTesting` rather than `request.httpBody` (which is `nil` by
/// then).
private extension URLRequest {
    var bodyDataForTesting: Data? {
        guard let stream = httpBodyStream else { return httpBody }
        stream.open()
        defer { stream.close() }
        var data = Data()
        let bufferSize = 4096
        var buffer = [UInt8](repeating: 0, count: bufferSize)
        while stream.hasBytesAvailable {
            let read = stream.read(&buffer, maxLength: bufferSize)
            if read <= 0 { break }
            data.append(buffer, count: read)
        }
        return data
    }
}

private final class StubURLProtocol: URLProtocol {
    static var stub: ((URLRequest) -> (HTTPURLResponse, Data))?

    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }

    override func startLoading() {
        guard let stub = StubURLProtocol.stub else {
            client?.urlProtocol(self, didFailWithError: URLError(.badServerResponse))
            return
        }
        let (response, data) = stub(request)
        client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: data)
        client?.urlProtocolDidFinishLoading(self)
    }

    override func stopLoading() {}
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `swift test --filter URLSessionEventSenderTests`
Expected: FAIL — `cannot find 'URLSessionEventSender' in scope`

- [ ] **Step 3: Write `Sources/MonetizationBrainCore/Events/EventSender.swift`**

```swift
/// Sends one `SdkEvent` over the network. A protocol so `EventQueue` (Task 5) can be tested
/// against a fake instead of real network calls.
public protocol EventSender: Sendable {
    /// Attempts delivery. Throws on any failure (network, non-2xx status, etc.) — `EventQueue`
    /// treats a throw as "keep this event queued and retry later."
    func send(_ event: SdkEvent) async throws
}

public enum EventSenderError: Error, Sendable, Equatable {
    case invalidEndpoint
    case httpError(statusCode: Int)
    case transport(String)
}
```

- [ ] **Step 4: Write `Sources/MonetizationBrainCore/Events/URLSessionEventSender.swift`**

```swift
import Foundation

/// `EventSender` that POSTs the event as JSON to a configured endpoint.
///
/// Only a 2xx response counts as success, matching the backend contract's own rule (Android
/// doc 46): the endpoint returns 2xx only once the event is durably written, so anything else
/// — including a timeout — must be treated as "not delivered, keep it queued."
public final class URLSessionEventSender: EventSender, @unchecked Sendable {
    private let endpoint: URL
    private let session: URLSession
    private let encoder: JSONEncoder

    public init(endpoint: URL, session: URLSession = .shared) {
        self.endpoint = endpoint
        self.session = session
        self.encoder = JSONEncoder()
    }

    public func send(_ event: SdkEvent) async throws {
        var request = URLRequest(url: endpoint)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try encoder.encode(event)

        let (_, response) = try await session.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse else {
            throw EventSenderError.transport("Response was not an HTTP response")
        }
        guard (200..<300).contains(httpResponse.statusCode) else {
            throw EventSenderError.httpError(statusCode: httpResponse.statusCode)
        }
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `swift test --filter URLSessionEventSenderTests`
Expected: PASS — all 3 tests green

- [ ] **Step 6: Commit**

```bash
git add Sources/MonetizationBrainCore/Events/EventSender.swift Sources/MonetizationBrainCore/Events/URLSessionEventSender.swift Tests/MonetizationBrainCoreTests/URLSessionEventSenderTests.swift
git commit -m "feat: add EventSender protocol and URLSession-backed implementation"
```

---

## Task 5: Durable queue (`EventSink`, `EventQueue`)

Mirrors Android's `DecisionLogQueue` + `DecisionLogger`, collapsed into one type since Phase 1 has no decision-specific schema yet — future phases (IAP, paywall, brain decisions) publish generic `SdkEvent`s straight through `EventSink`. Persist-then-send, remove-by-content-match (not index, since new events can be enqueued between a send attempt and its removal), write-to-temp-then-atomic-replace, bounded by `maxRecords` trimming the oldest first.

**Files:**
- Create: `Sources/MonetizationBrainCore/Events/EventSink.swift`
- Create: `Sources/MonetizationBrainCore/Events/EventQueue.swift`
- Test: `Tests/MonetizationBrainCoreTests/EventQueueTests.swift`

- [ ] **Step 1: Write the failing test**

```swift
import XCTest
@testable import MonetizationBrainCore

final class EventQueueTests: XCTestCase {
    private var directory: URL!

    override func setUp() {
        super.setUp()
        directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
    }

    override func tearDown() {
        try? FileManager.default.removeItem(at: directory)
        super.tearDown()
    }

    func test_sendPersistsAndDeliversOnSuccess() async {
        let sender = FakeEventSender()
        let queue = EventQueue(directory: directory, sender: sender)

        await queue.send(SdkEvent(name: "debug_ping"))

        let queuedCount = await queue.queuedCount()
        let sent = await sender.sentEvents
        XCTAssertEqual(queuedCount, 0)
        XCTAssertEqual(sent.map(\.name), ["debug_ping"])
    }

    func test_sendKeepsEventQueuedOnSenderFailure() async {
        let sender = FakeEventSender(shouldFail: true)
        let queue = EventQueue(directory: directory, sender: sender)

        await queue.send(SdkEvent(name: "debug_ping"))

        let queuedCount = await queue.queuedCount()
        XCTAssertEqual(queuedCount, 1)
    }

    func test_flushRetriesQueuedEventsAndRemovesOnSuccess() async {
        let sender = FakeEventSender(shouldFail: true)
        let queue = EventQueue(directory: directory, sender: sender)
        await queue.send(SdkEvent(name: "debug_ping"))
        let queuedBeforeFlush = await queue.queuedCount()
        XCTAssertEqual(queuedBeforeFlush, 1)

        await sender.setShouldFail(false)
        await queue.flush()

        let queuedCount = await queue.queuedCount()
        let sent = await sender.sentEvents
        XCTAssertEqual(queuedCount, 0)
        XCTAssertEqual(sent.map(\.name), ["debug_ping"])
    }

    func test_queueSurvivesAcrossInstancesPointingAtSameDirectory() async {
        let failingSender = FakeEventSender(shouldFail: true)
        let queueBeforeRestart = EventQueue(directory: directory, sender: failingSender)
        await queueBeforeRestart.send(SdkEvent(name: "debug_ping"))
        let queuedBeforeRestart = await queueBeforeRestart.queuedCount()
        XCTAssertEqual(queuedBeforeRestart, 1)

        let succeedingSender = FakeEventSender()
        let queueAfterRestart = EventQueue(directory: directory, sender: succeedingSender)
        let queuedAfterRestart = await queueAfterRestart.queuedCount()
        XCTAssertEqual(queuedAfterRestart, 1)

        await queueAfterRestart.flush()

        let queuedAfterFlush = await queueAfterRestart.queuedCount()
        let sent = await succeedingSender.sentEvents
        XCTAssertEqual(queuedAfterFlush, 0)
        XCTAssertEqual(sent.map(\.name), ["debug_ping"])
    }

    func test_maxRecordsTrimsOldestFirst() async {
        let sender = FakeEventSender(shouldFail: true)
        let queue = EventQueue(directory: directory, maxRecords: 2, sender: sender)

        await queue.send(SdkEvent(name: "a"))
        await queue.send(SdkEvent(name: "b"))
        await queue.send(SdkEvent(name: "c"))

        let names = await queue.queuedEventNamesForTest()
        XCTAssertEqual(names, ["b", "c"])
    }
}

private actor FakeEventSender: EventSender {
    private(set) var sentEvents: [SdkEvent] = []
    private var shouldFail: Bool

    init(shouldFail: Bool = false) {
        self.shouldFail = shouldFail
    }

    func setShouldFail(_ value: Bool) {
        shouldFail = value
    }

    func send(_ event: SdkEvent) async throws {
        if shouldFail {
            throw EventSenderError.transport("forced failure")
        }
        sentEvents.append(event)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `swift test --filter EventQueueTests`
Expected: FAIL — `cannot find 'EventQueue' in scope`

- [ ] **Step 3: Write `Sources/MonetizationBrainCore/Events/EventSink.swift`**

```swift
/// What future event producers (IAP, paywall, brain decisions) publish through. `EventQueue`
/// is the only implementation Phase 1 ships.
public protocol EventSink: Sendable {
    func send(_ event: SdkEvent) async
}
```

- [ ] **Step 4: Write `Sources/MonetizationBrainCore/Events/EventQueue.swift`**

```swift
import Foundation

/// Durable event queue: every event is written to disk before a send is attempted, and only
/// removed once delivery is confirmed. Survives a killed process and a lost network.
///
/// Design carried over from Android's `DecisionLogQueue`/`DecisionLogger` on purpose:
/// - Persist-then-send: a process killed right after `send(_:)` returns still has the event
///   queued on disk.
/// - Remove-by-content-match, not by index: new events can be enqueued between a send attempt
///   and its removal, so removal must match the exact record rather than trust a stale index.
/// - Write-to-temp-then-atomic-replace: a process killed mid-write leaves the last good file
///   intact — a duplicate send is recoverable (the backend contract tolerates duplicates), a
///   lost queue is not.
/// - Bounded by `maxRecords`, trimming the OLDEST records first: new data reflects the current
///   policy, and an unbounded queue on a long-offline device would grow forever.
///
/// Simplification vs. Android: Android's `enqueue()` appends in O(1) and only rewrites the
/// whole file when trimming; this queue always does read-modify-write, trading some throughput
/// for a single, easier-to-reason-about code path. Revisit only if event volume ever makes this
/// a measured bottleneck.
public actor EventQueue: EventSink {
    private let directory: URL
    private let fileName: String
    private let maxRecords: Int
    private let sender: EventSender
    private let encoder = JSONEncoder()
    private let decoder = JSONDecoder()

    public init(
        directory: URL,
        fileName: String = "monetization_brain_event_queue.jsonl",
        maxRecords: Int = 5000,
        sender: EventSender
    ) {
        self.directory = directory
        self.fileName = fileName
        self.maxRecords = maxRecords
        self.sender = sender
    }

    private var fileURL: URL { directory.appendingPathComponent(fileName) }
    private var tmpURL: URL { directory.appendingPathComponent(fileName + ".tmp") }

    /// Enqueues `event` (persists to disk immediately), then attempts to send it right away. A
    /// send failure leaves it queued for the next `flush()`.
    public func send(_ event: SdkEvent) async {
        guard let line = encodeLine(event) else { return }
        enqueueLine(line)
        await attemptSend(event: event, line: line)
    }

    /// Retries every event still in the queue. Sequential by design — the goal is durability,
    /// not throughput; a batched ingestion endpoint doesn't exist yet.
    public func flush() async {
        for line in peekAllLines() {
            guard let event = decodeLine(line) else {
                // Corrupt record — cannot be retried, drop it rather than block the queue.
                removeLine(line)
                continue
            }
            await attemptSend(event: event, line: line)
        }
    }

    /// Number of events currently queued (persisted, not yet confirmed delivered).
    public func queuedCount() -> Int {
        peekAllLines().count
    }

    /// Test-only: names of events currently queued, in queue order.
    func queuedEventNamesForTest() -> [String] {
        peekAllLines().compactMap { decodeLine($0)?.name }
    }

    // MARK: - Send

    private func attemptSend(event: SdkEvent, line: String) async {
        do {
            try await sender.send(event)
            removeLine(line)
        } catch {
            // Left queued for the next flush(). A send failure must never propagate to the
            // caller — logging is never allowed to interrupt the app's own flow.
        }
    }

    // MARK: - Encoding

    private func encodeLine(_ event: SdkEvent) -> String? {
        guard let data = try? encoder.encode(event),
              let json = String(data: data, encoding: .utf8) else { return nil }
        // Compact JSONEncoder output never contains a raw newline, but guard anyway since this
        // is a one-record-per-line file format.
        return json.replacingOccurrences(of: "\n", with: "\\n")
    }

    private func decodeLine(_ line: String) -> SdkEvent? {
        guard let data = line.data(using: .utf8) else { return nil }
        return try? decoder.decode(SdkEvent.self, from: data)
    }

    // MARK: - Disk I/O

    private func peekAllLines() -> [String] {
        guard let data = try? Data(contentsOf: fileURL),
              let contents = String(data: data, encoding: .utf8) else { return [] }
        return contents.split(separator: "\n", omittingEmptySubsequences: true).map(String.init)
    }

    /// Removes the FIRST line equal to `line`, matched by content, not index.
    private func removeLine(_ line: String) {
        var remaining = peekAllLines()
        if let index = remaining.firstIndex(of: line) {
            remaining.remove(at: index)
        }
        writeAll(remaining)
    }

    private func enqueueLine(_ line: String) {
        var lines = peekAllLines()
        lines.append(line)
        if lines.count > maxRecords {
            lines = Array(lines.suffix(maxRecords))
        }
        writeAll(lines)
    }

    private func writeAll(_ lines: [String]) {
        do {
            if !FileManager.default.fileExists(atPath: directory.path) {
                try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
            }
            let content = lines.isEmpty ? "" : lines.joined(separator: "\n") + "\n"
            try Data(content.utf8).write(to: tmpURL, options: .atomic)
            if FileManager.default.fileExists(atPath: fileURL.path) {
                _ = try FileManager.default.replaceItemAt(fileURL, withItemAt: tmpURL)
            } else {
                try FileManager.default.moveItem(at: tmpURL, to: fileURL)
            }
        } catch {
            // Best-effort: on failure the previous file is left as-is rather than risking a
            // half-written queue. Removal failing here means a duplicate send later, never
            // lost data — the backend contract tolerates duplicates.
            try? FileManager.default.removeItem(at: tmpURL)
        }
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `swift test --filter EventQueueTests`
Expected: PASS — all 5 tests green

- [ ] **Step 6: Commit**

```bash
git add Sources/MonetizationBrainCore/Events/EventSink.swift Sources/MonetizationBrainCore/Events/EventQueue.swift Tests/MonetizationBrainCoreTests/EventQueueTests.swift
git commit -m "feat: add durable EventQueue"
```

---

## Task 6: Holdout bucketing (`HoldoutManager`)

Ported for real, not stubbed — pure deterministic FNV-1a hashing, and every later phase (paywall holdout, brain rollout, CRM `isHoldout`, experiment exposure) shares this one bucketing function. `ExposureRecord`/`exposureRecord()` from Android are intentionally NOT ported here — they belong with the `experiment_exposure` event, out of scope for Phase 1 (see spec §3 non-goals).

**Files:**
- Create: `Sources/MonetizationBrainCore/Brain/HoldoutManager.swift`
- Test: `Tests/MonetizationBrainCoreTests/HoldoutManagerParityTests.swift`

- [ ] **Step 1: Write the failing test**

```swift
import XCTest
@testable import MonetizationBrainCore

/// Locks `HoldoutManager.bucketWith` to the exact FNV-1a vectors Android's
/// `HoldoutParityVectorsTest` and the backend's `test_reconcile.py` also assert. The server-side
/// reconciliation job reimplements this hash in Python to re-derive bucket membership from
/// (salt, user_id) — changing this algorithm without changing all three implementations makes
/// every already-collected exposure row look "corrupt" the moment the server reconciles it.
final class HoldoutManagerParityTests: XCTestCase {
    override func tearDown() {
        HoldoutManager.salt = "brain_holdout_v1"
        HoldoutManager.holdoutFraction = 0.05
        super.tearDown()
    }

    func test_crossLanguageHashVectors() {
        XCTAssertEqual(HoldoutManager.bucketWith("brain_holdout_v1", "user_1"), 6924)
        XCTAssertEqual(HoldoutManager.bucketWith("brain_holdout_v1", "user_2"), 9781)
        XCTAssertEqual(HoldoutManager.bucketWith("brain_holdout_v1", "player-abc-123"), 5350)
        XCTAssertEqual(HoldoutManager.bucketWith("brain_holdout_v2", "user_1"), 735)
        XCTAssertEqual(HoldoutManager.bucketWith("brain_holdout_v1|rollout", "user_1"), 7683)
        XCTAssertEqual(HoldoutManager.bucketWith("paywall_variant", "user_1"), 7074)
    }

    func test_bucketIsStableAcrossCalls() {
        HoldoutManager.salt = "brain_holdout_v1"
        let first = HoldoutManager.bucket("stable_user")
        let second = HoldoutManager.bucket("stable_user")
        XCTAssertEqual(first, second)
    }

    func test_isHoldoutDisabledWhenFractionIsZero() {
        HoldoutManager.holdoutFraction = 0.0
        XCTAssertFalse(HoldoutManager.isHoldout("user_1"))
    }

    func test_isHoldoutAlwaysTrueWhenFractionIsOne() {
        HoldoutManager.holdoutFraction = 1.0
        XCTAssertTrue(HoldoutManager.isHoldout("user_1"))
    }

    func test_isInRolloutIndependentNamespaceNotPhaseShiftOfHoldoutBucket() {
        // With holdout at 100% and rollout at 100%, both must be true simultaneously — proof
        // rollout uses its own namespace rather than a phase-shift of the holdout bucket (a
        // phase-shift would correlate the two and make this combination impossible).
        HoldoutManager.holdoutFraction = 1.0
        XCTAssertTrue(HoldoutManager.isHoldout("user_1"))
        XCTAssertTrue(HoldoutManager.isInRollout("user_1", fraction: 1.0))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `swift test --filter HoldoutManagerParityTests`
Expected: FAIL — `cannot find 'HoldoutManager' in scope`

- [ ] **Step 3: Write `Sources/MonetizationBrainCore/Brain/HoldoutManager.swift`**

```swift
import Foundation

/// Deterministic, hash-based bucketing shared by holdout (control group) and staged rollout.
///
/// The same `playerId` always lands in the same bucket across sessions and reinstalls — no
/// storage needed, and no drift versus random-per-call bucketing, which would put a player in
/// treatment on one session and control on the next and corrupt the measurement.
///
/// Uses FNV-1a 32-bit rather than Swift's `String.hashValue`, which is randomized per process
/// launch and therefore cannot agree with the server-side bucket-reconciliation job or with the
/// Android SDK. `bucketWith(_:_:)` is verified byte-for-byte against Android's
/// `HoldoutManager.bucketWith` and the backend's Python reimplementation — see
/// `HoldoutManagerParityTests`.
public enum HoldoutManager {
    private static let bucketCount = 10_000
    private static let fnvOffsetBasis: Int32 = -0x7ee3623b // 2166136261 as signed 32-bit
    private static let fnvPrime: Int32 = 0x0100_0193 // 16777619

    /// Fraction of players held back as control. 0.05 = 5%. 0.0 disables holdout entirely.
    public static var holdoutFraction: Double = 0.05

    /// Hash salt. Changing this re-buckets the entire population — only do it at the start of
    /// a new measurement cycle; changing it mid-cycle breaks comparability of data already
    /// collected, since some players jump groups.
    public static var salt: String = "brain_holdout_v1"

    /// Stable bucket in `[0, 10000)` for `playerId` under the current `salt`.
    public static func bucket(_ playerId: String) -> Int {
        bucketWith(salt, playerId)
    }

    /// Bucket under an explicit `namespace`. Two different namespaces produce two
    /// STATISTICALLY INDEPENDENT groupings over the same player set.
    ///
    /// Do not derive one namespace's bucket from another's by phase-shifting
    /// (`(bucket + n) % 10000`) — that's a deterministic function of the same bucket, so the two
    /// groupings stay fully correlated. With a 10% holdout and a 50% rollout, that bug would put
    /// every holdout player outside the rollout, making it impossible to run both experiments at
    /// once. This is why `isInRollout` below uses `"\(salt)|rollout"` as its own namespace.
    public static func bucketWith(_ namespace: String, _ playerId: String) -> Int {
        var hash = fnvOffsetBasis
        let input = "\(namespace)|\(playerId)"
        for unit in input.utf16 {
            hash ^= Int32(unit)
            hash = hash &* fnvPrime
        }
        let unsigned = UInt32(bitPattern: hash)
        return Int(unsigned % UInt32(bucketCount))
    }

    /// Is this player in the control (holdout) group?
    public static func isHoldout(_ playerId: String) -> Bool {
        if holdoutFraction <= 0.0 { return false }
        if holdoutFraction >= 1.0 { return true }
        return bucket(playerId) < Int(holdoutFraction * Double(bucketCount))
    }

    /// Staged rollout support: is this player in the `fraction` of the population currently
    /// getting the brain enabled? Uses a separate namespace from holdout — see the warning on
    /// `bucketWith` above for why.
    public static func isInRollout(_ playerId: String, fraction: Double) -> Bool {
        if fraction >= 1.0 { return true }
        if fraction <= 0.0 { return false }
        return bucketWith("\(salt)|rollout", playerId) < Int(fraction * Double(bucketCount))
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `swift test --filter HoldoutManagerParityTests`
Expected: PASS — all 5 tests green, including the exact cross-language hash vectors

- [ ] **Step 5: Commit**

```bash
git add Sources/MonetizationBrainCore/Brain/HoldoutManager.swift Tests/MonetizationBrainCoreTests/HoldoutManagerParityTests.swift
git commit -m "feat: port HoldoutManager with cross-platform hash parity"
```

---

## Task 7: Decision vocabulary (`DecisionPoint`, `ActionMode`, `CrossModalDecision`, `AssistDecision`)

Raw string values are kept identical to the Android enum constant names (`SCREAMING_SNAKE_CASE`) so a value logged in an `SdkEvent` payload matches what the backend already accepts from the Android SDK.

**Files:**
- Create: `Sources/MonetizationBrainCore/Brain/DecisionPoint.swift`
- Create: `Sources/MonetizationBrainCore/Brain/ActionMode.swift`
- Create: `Sources/MonetizationBrainCore/Brain/DecisionModels.swift`
- Test: `Tests/MonetizationBrainCoreTests/DecisionModelsTests.swift`

- [ ] **Step 1: Write the failing test**

```swift
import XCTest
@testable import MonetizationBrainCore

final class DecisionModelsTests: XCTestCase {
    func test_decisionPointRawValuesMatchAndroidEventContract() {
        XCTAssertEqual(DecisionPoint.sessionStart.rawValue, "SESSION_START")
        XCTAssertEqual(DecisionPoint.levelFail.rawValue, "LEVEL_FAIL")
        XCTAssertEqual(DecisionPoint.levelFailStreak.rawValue, "LEVEL_FAIL_STREAK")
        XCTAssertEqual(DecisionPoint.levelComplete.rawValue, "LEVEL_COMPLETE")
        XCTAssertEqual(DecisionPoint.currencyLow.rawValue, "CURRENCY_LOW")
        XCTAssertEqual(DecisionPoint.shopOpen.rawValue, "SHOP_OPEN")
        XCTAssertEqual(DecisionPoint.rewardedOpportunity.rawValue, "REWARDED_OPPORTUNITY")
        XCTAssertEqual(DecisionPoint.interstitialOpportunity.rawValue, "INTERSTITIAL_OPPORTUNITY")
        XCTAssertEqual(DecisionPoint.returnAfterInactivity.rawValue, "RETURN_AFTER_INACTIVITY")
    }

    func test_actionModeRawValuesMatchAndroidEventContract() {
        XCTAssertEqual(ActionMode.noAction.rawValue, "NO_ACTION")
        XCTAssertEqual(ActionMode.freeAction.rawValue, "FREE_ACTION")
        XCTAssertEqual(ActionMode.rewardedAd.rawValue, "REWARDED_AD")
        XCTAssertEqual(ActionMode.interstitial.rawValue, "INTERSTITIAL")
        XCTAssertEqual(ActionMode.iapOffer.rawValue, "IAP_OFFER")
        XCTAssertEqual(ActionMode.gameplayConfig.rawValue, "GAMEPLAY_CONFIG")
    }

    func test_crossModalDecisionEquality() {
        let a = CrossModalDecision(action: .noAction, isHoldout: false)
        let b = CrossModalDecision(action: .noAction, isHoldout: false)
        XCTAssertEqual(a, b)
    }

    func test_assistDecisionEquality() {
        let a = AssistDecision(action: .freeAction, isHoldout: true)
        let b = AssistDecision(action: .freeAction, isHoldout: true)
        XCTAssertEqual(a, b)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `swift test --filter DecisionModelsTests`
Expected: FAIL — `cannot find 'DecisionPoint' in scope`

- [ ] **Step 3: Write `Sources/MonetizationBrainCore/Brain/DecisionPoint.swift`**

```swift
/// Minimal set of decision points (Android doc 06 §2). Extend only when a matching
/// instrumentation event exists — don't add a decision point speculatively.
public enum DecisionPoint: String, Sendable, CaseIterable {
    case sessionStart = "SESSION_START"
    case levelFail = "LEVEL_FAIL"
    case levelFailStreak = "LEVEL_FAIL_STREAK"
    case levelComplete = "LEVEL_COMPLETE"
    case currencyLow = "CURRENCY_LOW"
    case shopOpen = "SHOP_OPEN"
    case rewardedOpportunity = "REWARDED_OPPORTUNITY"
    case interstitialOpportunity = "INTERSTITIAL_OPPORTUNITY"
    case returnAfterInactivity = "RETURN_AFTER_INACTIVITY"
}
```

- [ ] **Step 4: Write `Sources/MonetizationBrainCore/Brain/ActionMode.swift`**

```swift
/// Minimal action space (Android doc 01 §1). `.gameplayConfig` is reserved for dynamic
/// difficulty adjustment (doc 24 §C), not implemented until a later phase.
public enum ActionMode: String, Sendable, CaseIterable {
    case noAction = "NO_ACTION"
    case freeAction = "FREE_ACTION"
    case rewardedAd = "REWARDED_AD"
    case interstitial = "INTERSTITIAL"
    case iapOffer = "IAP_OFFER"
    case gameplayConfig = "GAMEPLAY_CONFIG"
}
```

- [ ] **Step 5: Write `Sources/MonetizationBrainCore/Brain/DecisionModels.swift`**

```swift
/// Result of `AdsBrain.chooseCrossModal(...)`.
public struct CrossModalDecision: Sendable, Equatable {
    public let action: ActionMode
    public let isHoldout: Bool

    public init(action: ActionMode, isHoldout: Bool) {
        self.action = action
        self.isHoldout = isHoldout
    }
}

/// Result of `AdsBrain.chooseAssist(...)`.
public struct AssistDecision: Sendable, Equatable {
    public let action: ActionMode
    public let isHoldout: Bool

    public init(action: ActionMode, isHoldout: Bool) {
        self.action = action
        self.isHoldout = isHoldout
    }
}
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `swift test --filter DecisionModelsTests`
Expected: PASS — all 4 tests green

- [ ] **Step 7: Commit**

```bash
git add Sources/MonetizationBrainCore/Brain/DecisionPoint.swift Sources/MonetizationBrainCore/Brain/ActionMode.swift Sources/MonetizationBrainCore/Brain/DecisionModels.swift Tests/MonetizationBrainCoreTests/DecisionModelsTests.swift
git commit -m "feat: port DecisionPoint/ActionMode vocabulary from Android"
```

---

## Task 8: Guardrail skeleton (`GuardrailEngine`, `AllowAllGuardrailEngine`)

Shaped like Android's `GuardrailEngine` but with no real rules yet — those arrive with the bandit engine in a later phase.

**Files:**
- Create: `Sources/MonetizationBrainCore/Brain/GuardrailEngine.swift`
- Test: `Tests/MonetizationBrainCoreTests/GuardrailEngineTests.swift`

- [ ] **Step 1: Write the failing test**

```swift
import XCTest
@testable import MonetizationBrainCore

final class GuardrailEngineTests: XCTestCase {
    func test_allowAllGuardrailEngineAlwaysAllows() {
        let engine = AllowAllGuardrailEngine()
        for mode in ActionMode.allCases {
            XCTAssertTrue(engine.allows(action: mode, playerId: "user_1"))
        }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `swift test --filter GuardrailEngineTests`
Expected: FAIL — `cannot find 'AllowAllGuardrailEngine' in scope`

- [ ] **Step 3: Write `Sources/MonetizationBrainCore/Brain/GuardrailEngine.swift`**

```swift
/// Shaped like Android's `GuardrailEngine` (kill-switch check, rollout/holdout check) but with
/// no real rules yet — those arrive with the bandit engine in a later phase. Phase 1 ships only
/// `AllowAllGuardrailEngine`.
public protocol GuardrailEngine: Sendable {
    /// Whether `action` is currently allowed for `playerId`.
    func allows(action: ActionMode, playerId: String) -> Bool
}

/// Phase 1's only `GuardrailEngine`: always allows. There are no rules to enforce yet.
public struct AllowAllGuardrailEngine: GuardrailEngine {
    public init() {}

    public func allows(action: ActionMode, playerId: String) -> Bool { true }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `swift test --filter GuardrailEngineTests`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add Sources/MonetizationBrainCore/Brain/GuardrailEngine.swift Tests/MonetizationBrainCoreTests/GuardrailEngineTests.swift
git commit -m "feat: add GuardrailEngine skeleton"
```

---

## Task 9: Diagnostics (`SelfCheckReport`)

**Files:**
- Create: `Sources/MonetizationBrainCore/Brain/SelfCheckReport.swift`

No standalone test — `SelfCheckReport` is a plain data type exercised through `AdsBrainTests` in Task 10, where its actual production is tested.

- [ ] **Step 1: Write `Sources/MonetizationBrainCore/Brain/SelfCheckReport.swift`**

```swift
/// Diagnostic snapshot `AdsBrain.selfCheckReport()` returns. Mirrors Android's running habit of
/// surfacing misconfiguration where a developer will actually read it, instead of failing
/// silently in production.
public struct SelfCheckReport: Sendable, Equatable {
    public let isConfigured: Bool
    public let isEnabled: Bool
    public let consentTier: ConsentTier
    public let issues: [String]

    public init(isConfigured: Bool, isEnabled: Bool, consentTier: ConsentTier, issues: [String]) {
        self.isConfigured = isConfigured
        self.isEnabled = isEnabled
        self.consentTier = consentTier
        self.issues = issues
    }
}
```

- [ ] **Step 2: Verify the package still builds**

Run: `swift build`
Expected: exits with no errors (no test needed — no behavior to verify yet)

- [ ] **Step 3: Commit**

```bash
git add Sources/MonetizationBrainCore/Brain/SelfCheckReport.swift
git commit -m "feat: add SelfCheckReport model"
```

---

## Task 10: `AdsBrain` facade

Single public entry point. Every decision-point method in Phase 1 returns the safe baseline — no guardrail rules and no bandit exist yet — so integrating against this facade now means call sites, holdout membership, and event wiring are already correct when real decisioning lands later. `enabled` defaults to `false`, matching Android doc 38's "off by default" rule.

**Files:**
- Create: `Sources/MonetizationBrainCore/Brain/AdsBrain.swift`
- Test: `Tests/MonetizationBrainCoreTests/AdsBrainTests.swift`

- [ ] **Step 1: Write the failing test**

```swift
import XCTest
@testable import MonetizationBrainCore

final class AdsBrainTests: XCTestCase {
    override func setUp() {
        super.setUp()
        AdsBrain.shared.resetForTest()
        ConsentState.shared.resetForTest()
    }

    override func tearDown() {
        AdsBrain.shared.resetForTest()
        ConsentState.shared.resetForTest()
        super.tearDown()
    }

    private func makeConfiguration() -> AdsBrain.Configuration {
        AdsBrain.Configuration(
            remoteConfig: StaticRemoteConfigProvider(),
            eventSink: EventQueue(
                directory: FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString),
                sender: NoOpEventSender()
            )
        )
    }

    func test_enabledDefaultsToFalse() {
        XCTAssertFalse(AdsBrain.shared.enabled)
    }

    func test_isConfiguredFalseBeforeConfigure() {
        XCTAssertFalse(AdsBrain.shared.isConfigured())
    }

    func test_isConfiguredTrueAfterConfigure() {
        AdsBrain.shared.configure(makeConfiguration())
        XCTAssertTrue(AdsBrain.shared.isConfigured())
    }

    func test_shouldOfferIapAlwaysReturnsFalseInPhase1() {
        XCTAssertFalse(AdsBrain.shared.shouldOfferIap())
        XCTAssertFalse(AdsBrain.shared.shouldOfferIap(decisionPoint: .currencyLow))
    }

    func test_chooseCrossModalAlwaysReturnsBaselineNotHoldout() {
        let decision = AdsBrain.shared.chooseCrossModal(decisionPoint: .levelFail, baseline: .interstitial)
        XCTAssertEqual(decision.action, .interstitial)
        XCTAssertFalse(decision.isHoldout)
    }

    func test_chooseAssistAlwaysReturnsBaselineNotHoldout() {
        let decision = AdsBrain.shared.chooseAssist(decisionPoint: .levelFailStreak, baseline: .freeAction)
        XCTAssertEqual(decision.action, .freeAction)
        XCTAssertFalse(decision.isHoldout)
    }

    func test_selfCheckReportListsIssuesWhenUnconfiguredAndDisabled() {
        let report = AdsBrain.shared.selfCheckReport()
        XCTAssertFalse(report.isConfigured)
        XCTAssertFalse(report.isEnabled)
        XCTAssertEqual(report.consentTier, .unknown)
        XCTAssertTrue(report.issues.contains("AdsBrain.configure(_:) has not been called yet."))
        XCTAssertTrue(report.issues.contains("AdsBrain.enabled is false — all decision points return their baseline."))
    }

    func test_selfCheckReportHasNoConfigurationIssueOnceConfiguredAndEnabled() {
        AdsBrain.shared.configure(makeConfiguration())
        AdsBrain.shared.enabled = true
        ConsentState.shared.setTier(.granted)

        let report = AdsBrain.shared.selfCheckReport()
        XCTAssertTrue(report.isConfigured)
        XCTAssertTrue(report.isEnabled)
        XCTAssertFalse(report.issues.contains("AdsBrain.configure(_:) has not been called yet."))
        XCTAssertFalse(report.issues.contains("AdsBrain.enabled is false — all decision points return their baseline."))
    }
}

private struct NoOpEventSender: EventSender {
    func send(_ event: SdkEvent) async throws {}
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `swift test --filter AdsBrainTests`
Expected: FAIL — `cannot find 'AdsBrain' in scope`

- [ ] **Step 3: Write `Sources/MonetizationBrainCore/Brain/AdsBrain.swift`**

```swift
import Foundation

/// Single public entry point. Every decision-point method in Phase 1 returns the safe baseline
/// — no guardrail rules and no bandit exist yet, so integrating against this facade now means
/// call sites, holdout membership, and event wiring are already correct when real decisioning
/// lands in a later phase.
public final class AdsBrain: @unchecked Sendable {
    public static let shared = AdsBrain()

    public struct Configuration: Sendable {
        public let remoteConfig: RemoteConfigProvider
        public let eventSink: EventSink
        public let guardrailEngine: GuardrailEngine

        public init(
            remoteConfig: RemoteConfigProvider,
            eventSink: EventSink,
            guardrailEngine: GuardrailEngine = AllowAllGuardrailEngine()
        ) {
            self.remoteConfig = remoteConfig
            self.eventSink = eventSink
            self.guardrailEngine = guardrailEngine
        }
    }

    private let lock = NSLock()
    private var configuration: Configuration?
    private var _enabled = false

    private init() {}

    /// Wires the facade up. Does not itself turn decisioning on — `enabled` stays `false` until
    /// the host app opts in, matching the Android SDK's "off by default" rule: brain features
    /// never turn themselves on.
    public func configure(_ configuration: Configuration) {
        lock.lock()
        self.configuration = configuration
        lock.unlock()
    }

    /// Whether decisioning is turned on. Defaults to `false`. Phase 1 has no real decisioning
    /// to gate, but every later phase's guardrail/bandit checks read this flag first.
    public var enabled: Bool {
        get {
            lock.lock()
            defer { lock.unlock() }
            return _enabled
        }
        set {
            lock.lock()
            _enabled = newValue
            lock.unlock()
        }
    }

    /// `true` once `configure(_:)` has been called.
    public func isConfigured() -> Bool {
        lock.lock()
        defer { lock.unlock() }
        return configuration != nil
    }

    /// Diagnostic snapshot — call this from a debug screen or a startup log, not to gate
    /// behavior. Reflects misconfiguration honestly rather than staying silent about it.
    public func selfCheckReport() -> SelfCheckReport {
        var issues: [String] = []
        let configured = isConfigured()
        if !configured {
            issues.append("AdsBrain.configure(_:) has not been called yet.")
        }
        if !enabled {
            issues.append("AdsBrain.enabled is false — all decision points return their baseline.")
        }
        if ConsentState.shared.tier() == .unknown {
            issues.append("Consent has not been configured — treated as granted by default.")
        }
        return SelfCheckReport(
            isConfigured: configured,
            isEnabled: enabled,
            consentTier: ConsentState.shared.tier(),
            issues: issues
        )
    }

    // MARK: - Decision points (Phase 1: always return the baseline)

    /// Always returns `false` in Phase 1 — no guardrail/bandit exists to base a `true` on yet.
    public func shouldOfferIap(decisionPoint: DecisionPoint = .shopOpen) -> Bool {
        false
    }

    /// Always returns `baseline` with `isHoldout` always `false` in Phase 1 — no decision is
    /// actually being made yet, so nothing is being held out from.
    public func chooseCrossModal(
        decisionPoint: DecisionPoint,
        allowRewarded: Bool = true,
        allowIapOffer: Bool = true,
        baseline: ActionMode = .noAction
    ) -> CrossModalDecision {
        CrossModalDecision(action: baseline, isHoldout: false)
    }

    /// Always returns `baseline` with `isHoldout` always `false` in Phase 1.
    public func chooseAssist(
        decisionPoint: DecisionPoint,
        allowFreeGift: Bool = true,
        allowDifficultyTuning: Bool = true,
        baseline: ActionMode = .noAction
    ) -> AssistDecision {
        AssistDecision(action: baseline, isHoldout: false)
    }

    /// Test-only: clears configuration and resets `enabled` to `false`.
    func resetForTest() {
        lock.lock()
        configuration = nil
        _enabled = false
        lock.unlock()
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `swift test --filter AdsBrainTests`
Expected: PASS — all 8 tests green

- [ ] **Step 5: Commit**

```bash
git add Sources/MonetizationBrainCore/Brain/AdsBrain.swift Tests/MonetizationBrainCoreTests/AdsBrainTests.swift
git commit -m "feat: add AdsBrain facade with baseline-only decision points"
```

---

## Task 11: `MonetizationBrainUMP` target (`ConsentManager`)

Wraps the Google User Messaging Platform iOS SDK. Only translates UMP's result into `ConsentTier` — the actual policy lives in `ConsentState` (Task 1), which stays testable without any platform dependency. Not auto-invoked by `AdsBrain.configure(_:)`: UMP needs a presented view controller and can show a full-screen dialog, so the host app calls it when it chooses to, not at launch.

This target has no automated test in Phase 1: exercising it needs a real UMP network round-trip and a presented `UIViewController`, neither of which is mockable through the public UMP SDK. Treat it as manual-QA-on-device territory until a later phase revisits it — that gap is called out explicitly rather than left implicit.

**Files:**
- Modify: `Package.swift`
- Create: `Sources/MonetizationBrainUMP/ConsentManager.swift`

- [ ] **Step 1: Add the UMP dependency and target to `Package.swift`**

Replace the whole file with:

```swift
// swift-tools-version: 5.10
import PackageDescription

let package = Package(
    name: "MonetizationBrainIOS",
    platforms: [
        .iOS(.v15)
    ],
    products: [
        .library(
            name: "MonetizationBrainCore",
            targets: ["MonetizationBrainCore"]
        ),
        .library(
            name: "MonetizationBrainUMP",
            targets: ["MonetizationBrainUMP"]
        )
    ],
    dependencies: [
        .package(
            url: "https://github.com/googleads/swift-package-manager-google-user-messaging-platform.git",
            from: "3.1.0"
        )
    ],
    targets: [
        .target(
            name: "MonetizationBrainCore",
            dependencies: []
        ),
        .testTarget(
            name: "MonetizationBrainCoreTests",
            dependencies: ["MonetizationBrainCore"]
        ),
        .target(
            name: "MonetizationBrainUMP",
            dependencies: [
                "MonetizationBrainCore",
                .product(name: "GoogleUserMessagingPlatform", package: "swift-package-manager-google-user-messaging-platform")
            ]
        )
    ]
)
```

- [ ] **Step 2: Write `Sources/MonetizationBrainUMP/ConsentManager.swift`**

```swift
import UIKit
import GoogleUserMessagingPlatform
import MonetizationBrainCore

/// Thin wrapper around Google's User Messaging Platform iOS SDK — the certified CMP Google
/// requires for serving ads to EEA/UK users. This layer only runs the UMP flow and translates
/// its result into `ConsentTier`; it does not decide policy itself. What "granted" is allowed
/// to do lives in `ConsentState`, which is testable with no platform dependency at all.
///
/// Not called automatically by `AdsBrain.configure(_:)`: the UMP flow needs a presented view
/// controller and can show a full-screen dialog. Auto-running it at launch would pop the form
/// at a time the host app doesn't control — typically over its own splash screen. The host app
/// calls `request(from:)` itself, when it chooses to.
public enum ConsentManager {
    /// Test device identifiers that force the EEA consent form to show during development.
    public static var debugTestDeviceIdentifiers: [String] = []

    /// Forces the SDK to simulate being in the EEA for debugging. Only takes effect on devices
    /// listed in `debugTestDeviceIdentifiers`.
    public static var debugForceEea: Bool = false

    /// Whether ads can be requested yet. Returns `true` when UMP hasn't been asked at all —
    /// deliberately: a host app that hasn't configured consent (usually outside the EEA)
    /// should not silently lose all ads just from upgrading the SDK.
    /// `AdsBrain.selfCheckReport()` is where an unconfigured consent flow gets surfaced, not a
    /// silent production outage.
    public static var canRequestAds: Bool {
        ConsentInformation.shared.canRequestAds
    }

    /// Whether the "privacy options" entry point is required to be shown somewhere in the host
    /// app's UI (a GDPR requirement).
    public static var isPrivacyOptionsRequired: Bool {
        ConsentInformation.shared.privacyOptionsRequirementStatus == .required
    }

    /// Runs the consent flow: updates consent info, shows the form if required, then translates
    /// the result into `ConsentState`. `onDone` is always called exactly once, even on error —
    /// not calling it back is the surest way to leave the host app's splash screen hanging.
    @MainActor
    public static func request(
        from viewController: UIViewController,
        onDone: @escaping (ConsentTier) -> Void = { _ in }
    ) {
        let parameters = RequestParameters()
        if let underAge = ConsentState.shared.underAgeOfConsent {
            parameters.isTaggedForUnderAgeOfConsent = underAge
        }
        if !debugTestDeviceIdentifiers.isEmpty {
            let debugSettings = DebugSettings()
            debugSettings.testDeviceIdentifiers = debugTestDeviceIdentifiers
            if debugForceEea {
                debugSettings.geography = .EEA
            }
            parameters.debugSettings = debugSettings
        }

        ConsentInformation.shared.requestConsentInfoUpdate(with: parameters) { requestError in
            if requestError != nil {
                // Failing to fetch consent info does NOT mean the user declined. Keep the
                // current tier instead of inferring a refusal nobody gave.
                onDone(ConsentState.shared.tier())
                return
            }

            Task { @MainActor in
                do {
                    try await ConsentForm.loadAndPresentIfRequired(from: viewController)
                } catch {
                    // Presenting the form failed; fall through and translate whatever
                    // consentStatus UMP already has.
                }
                finish(onDone: onDone)
            }
        }
    }

    /// Re-opens the form so the user can change their choice. GDPR requires withdrawing
    /// consent to be as easy as giving it.
    @MainActor
    public static func showPrivacyOptions(
        from viewController: UIViewController,
        onDone: @escaping (ConsentTier) -> Void = { _ in }
    ) {
        ConsentForm.presentPrivacyOptionsForm(from: viewController) { _ in
            finish(onDone: onDone)
        }
    }

    /// Clears consent state. Development use only.
    public static func reset() {
        ConsentInformation.shared.reset()
        ConsentState.shared.setTier(.unknown)
    }

    @MainActor
    private static func finish(onDone: (ConsentTier) -> Void) {
        let tier = translate(ConsentInformation.shared.consentStatus)
        ConsentState.shared.setTier(tier)
        onDone(tier)
    }

    /// `.notRequired` (outside the EEA) maps to `.granted`, not `.unknown`: there is no consent
    /// obligation there at all, which is a DEFINITE "allowed" answer — different from "nobody
    /// has asked."
    ///
    /// `.obtained` maps to `.granted` with a known limitation: UMP only reports that the consent
    /// flow completed, not which purposes the user actually ticked. Reading the TCF string to
    /// get `.partial` right is not implemented yet.
    private static func translate(_ status: ConsentStatus) -> ConsentTier {
        switch status {
        case .notRequired:
            return .granted
        case .obtained:
            return ConsentInformation.shared.canRequestAds ? .granted : .denied
        case .required:
            return .denied
        case .unknown:
            return .unknown
        @unknown default:
            return .unknown
        }
    }
}
```

- [ ] **Step 3: Build the new target**

Run: `swift build --target MonetizationBrainUMP`
Expected: builds successfully. This step resolves the UMP package from GitHub over the network — if this sandbox/CI environment has no network access to github.com, this step will fail on package resolution rather than a code error; re-run on a machine with network access (or in Xcode) to confirm before treating this task as done. This is the one build in the whole plan with an external, network-dependent dependency — flagged here rather than silently assumed to work.

- [ ] **Step 4: Run the full Core test suite to confirm nothing broke**

Run: `swift test`
Expected: all `MonetizationBrainCoreTests` still PASS (the UMP target has no test target, so this doesn't add test count)

- [ ] **Step 5: Commit**

```bash
git add Package.swift Sources/MonetizationBrainUMP
git commit -m "feat: add MonetizationBrainUMP target wrapping Google UMP iOS SDK"
```

---

## Task 12: Final integration pass

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Run the entire test suite**

Run: `swift test`
Expected: every test across `ConsentStateTests`, `StaticRemoteConfigProviderTests`, `SdkEventTests`, `URLSessionEventSenderTests`, `EventQueueTests`, `HoldoutManagerParityTests`, `DecisionModelsTests`, `GuardrailEngineTests`, `AdsBrainTests`, and `MonetizationBrainCoreTests` passes — 0 failures.

- [ ] **Step 2: Update `README.md` status section**

Find the `## Status` section (currently reads "Pre-release. Not yet added as a dependency anywhere.") and replace it with:

```markdown
## Status

Phase 1 ("Core Foundation") implemented — consent, event pipeline, remote-config abstraction,
holdout bucketing, and the `AdsBrain` facade with baseline-only decision points. See
[`docs/superpowers/specs/2026-09-03-core-foundation-design.md`](docs/superpowers/specs/2026-09-03-core-foundation-design.md)
for scope and
[`docs/superpowers/plans/2026-09-03-phase1-core-foundation.md`](docs/superpowers/plans/2026-09-03-phase1-core-foundation.md)
for the implementation plan. No real bandit/decisioning intelligence yet — every decision point
returns its safe baseline. Not yet added as a dependency in any app.
```

- [ ] **Step 3: Commit and push everything**

```bash
git add README.md
git commit -m "docs: mark Phase 1 core foundation complete"
git push origin main
```

- [ ] **Step 4: Verify the push landed**

Run: `git log --oneline -5 && git status`
Expected: `git status` reports `nothing to commit, working tree clean` and the last 5 commits show the Phase 1 work, ending with the docs commit just pushed.
