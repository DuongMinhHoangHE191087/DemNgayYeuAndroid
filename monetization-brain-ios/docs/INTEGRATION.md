# Integrating MonetizationBrainIOS

Written for: an iOS engineer wiring this SDK into a game for the first time.

The README is the engineering record of how this port was built. This is the other document —
what a host app has to do, in the order it has to do it, and what each step is actually for.

---

## 1. What this SDK is, in one paragraph

It decides **whether** to show an interstitial, offer a rewarded ad, surface an IAP offer, or help
a stuck player — and it records enough about each decision to evaluate it later. It does **not**
load or display an ad, draw a paywall, or grant an item. Your app owns every pixel and every
grant; the SDK owns the judgement and the guardrails. Consequently, adopting it is mostly a matter
of asking it before you act and telling it what happened afterwards.

---

## 2. Install

```swift
.package(url: "https://github.com/dungduong2899/monetization-brain-ios.git", from: "0.1.0")
```

Four products, take only what you need:

| Product | Needs | For |
|---|---|---|
| `MonetizationBrainCore` | nothing | the brain, IAP, paywall, offerwall, CRM — everything below unless noted |
| `MonetizationBrainStoreKit` | StoreKit 2 | the `StoreBackend` that talks to the App Store |
| `MonetizationBrainUMP` | Google UMP | the GDPR/ATT consent flow |
| `MonetizationBrainFirebase` | Firebase | the Remote Config-backed provider |

`MonetizationBrainCore` imports none of those. If you have your own consent flow, your own config
backend or your own store, conform to the protocol instead and skip the target — that is what they
are for.

**iOS 15+.** Note that resolving this package resolves Firebase, because SwiftPM resolves the whole
graph. If your app already uses Firebase this costs nothing; if it does not, see the README section
on that trade-off.

---

## 3. Start-up, in order

The order is not stylistic. Each step reads state the previous one wrote.

```swift
// 1. Firebase first — the provider below reaches for it at init.
FirebaseApp.configure()
let remoteConfig = FirebaseRemoteConfigProvider()

// 2. Consent BEFORE the brain: the brain reads the tier to decide what it may do.
//    `tier` arrives on the MAIN ACTOR, always — on success, on a UMP fetch error, and when the
//    form fails to present. Dismiss your splash screen straight from here; no DispatchQueue.main
//    hop is needed, and the closure is typed `@MainActor` so the compiler holds you to it.
ConsentManager.request(from: rootViewController) { tier in
    Task { await startSDK(remoteConfig: remoteConfig) }
}

func startSDK(remoteConfig: RemoteConfigProvider) async {
    // 3. One call wires everything, in the one safe order.
    var config = MonetizationBrainSDK.Config(
        eventSink: myEventSink,               // where events go; EventQueue if you want durability
        playerId: myStablePlayerId,
        remoteConfig: remoteConfig
    )
    config.brainEnabled = true
    config.brainRolloutFraction = 0.05        // stage it; raise remotely, not in a release
    config.inappProducts = ["gem_pack_1", "gem_pack_2"]
    config.subscriptionProducts = ["vip_monthly"]
    config.removeAdsProducts = ["vip_monthly", "remove_ads"]
    config.storeBackend = StoreKit2Backend()
    config.childDirected = false              // your LEGAL declaration; nil means "not declared"

    let report = await MonetizationBrainSDK.configure(config)
    print(report.describe())                  // read the warnings, they are the point

    // 4. Remote config AFTER configure, so the artifact's values win over the compiled ones.
    let applied = await RemoteConfigDispatcher.shared.refresh(from: remoteConfig)
    print(applied.describe())
}
```

**Read `report.warnings`.** They exist for the failures that produce no symptom: a rollout fraction
of zero (the brain controls nobody), subscriptions declared without `removeAdsProducts` (paying
subscribers still see ads), products declared with no `storeBackend` (nothing can be bought),
consent still unknown.

---

## 4. The five decision points

Ask before you act. Every one is `async` and every one is safe to call before the SDK is
configured — it returns the conservative answer rather than trapping.

```swift
if await AdsBrain.shared.shouldShowInterstitial() { showInterstitial() }
if await AdsBrain.shared.shouldOfferRewarded() { offerRewarded() }
if await AdsBrain.shared.shouldOfferIap(decisionPoint: .shopOpen) { showShop() }

// When rewarded and an IAP offer are both possible, let the brain pick one:
switch await AdsBrain.shared.chooseCrossModal(decisionPoint: .levelFail).action {
case .rewardedAd: offerRewarded()
case .iapOffer:   showOffer()
default:          break
}

// When a player is stuck, choose between a free gift and quietly easing the difficulty:
switch await AdsBrain.shared.chooseAssist(decisionPoint: .levelFail).action {
case .freeAction:     giveGift()
case .gameplayConfig: easeDifficulty()
default:              break
}
```

If you never call these, the SDK changes nothing about your game.

---

## 5. Tell it what happened

This half is not optional. Decisions with no outcome cannot be learned from, and the guardrails
that protect players read these counters.

```swift
AdsBrain.shared.onInterstitialShown()          // sync — call when the ad is actually on screen
AdsBrain.shared.onAdSkipped()
AdsBrain.shared.onIapOfferShown()
AdsBrain.shared.onAssistShown()

await AdsBrain.shared.onRewardedOffered(accepted: true)
await AdsBrain.shared.onAssistUsed(used: true)
await AdsBrain.shared.onLevelFail()
await AdsBrain.shared.onLevelWin()
await AdsBrain.shared.onPlayerContinued()
await AdsBrain.shared.onPurchase(usd: 4.99)    // IapHelper does this for you; call it only for
                                               // purchases made outside IapHelper
```

Sessions are handled for you by default (`Config.observeAppLifecycle`): foreground and background
start and end sessions, with Android's same 30-minute gap rule. Call `onSessionStart()` /
`onSessionEnd()` yourself only if you opted out.

---

## 6. Selling things

```swift
_ = await IapHelper.shared.queryProducts()
let result = await IapHelper.shared.purchase("gem_pack_1")
if case .success = result { /* grant the item */ }
```

`IapHelper` persists purchases, prevents double-grants, emits `iap_purchase`, tells the brain, and
forwards revenue to attribution. **You still grant the item** — the SDK never touches your economy.

Ads on or off for this player:

```swift
if !Entitlements.shared.hasRemoveAds() { showBanner() }
```

That is the one place to ask. Android's teardown found the opposite pattern — purchase state and
the ad flag kept separately — and a subscriber who still saw ads.

**Attribution** (optional, ~15 lines): conform to `AttributionBridge` and pass it in
`IapHelper.Configuration`. Send the amount and currency verbatim; do not convert. There is a worked
AppsFlyer example on the protocol.

---

## 7. Remote config

Eight console keys, all `_ios`-suffixed, each read by one subsystem:

`brain_policy_ios` · `paywall_config_ios` · `purchase_routing_ios` · `subscription_lifecycle_ios` ·
`battle_pass_ios` · `recurring_grants_ios` · `offerwall_config_ios` · `crm_bridge_ios`

Call `RemoteConfigDispatcher.shared.refresh(from:)` at start-up (and on foreground, if you like).
A key you never set changes nothing; a malformed artifact costs only its own subsystem's update and
comes back in the report. `RemoteConfigDispatcher.keys` is public so you can seed console defaults
without retyping the strings.

---

## 8. Checking it works

```swift
print(MonetizationBrainSDK.lastReport()?.describe() ?? "not configured")

let check = AdsBrain.shared.selfCheckReport()               // is it wired correctly?
print("configured=\(check.isConfigured) enabled=\(check.isEnabled) consent=\(check.consentTier)")
check.issues.forEach { print("  ⚠ \($0)") }

if let state = AdsBrain.shared.currentState() {
    print(BrainInspector.format(
        BrainInspector.shared.snapshot(engine: DecisionEngine.shared, state: state)
    ))                                                      // what is it deciding, right now?
}
```

Those answer different questions: `selfCheckReport` audits the wiring once, `BrainInspector` shows
this player's belief, scenario, guardrail blocks and last 30 decisions. Attach the second to bug
reports.

---

## 9. Things that will bite you if nobody says them

- **`playerId` must be stable for the same player.** Holdout membership, rollout membership and the
  learned belief are all keyed by it. A new id per launch means the brain relearns from nothing
  every time and your experiment measures noise.
- **Roll out from ~5%, not 100%.** `brainRolloutFraction` is a staged rollout, and the artifact can
  raise it without a release.
- **The holdout group is the measurement.** ~5% of players deliberately get no personalization.
  Removing it does not make more money; it makes the money unattributable.
- **Nothing here is fraud-resistant.** `Entitlements` trusts what the store reports on-device. Fine
  for "does this player see ads". **Never** use it to grant anything with cash value until
  server-side receipt verification exists.
- **Battle-pass and recurring-grant timing is device-local wall clock.** Moving the clock forward
  farms rewards. Cosmetics and soft currency only.
- **Events are at-least-once.** A killed process can re-send up to 25 already-delivered events; the
  backend dedupes on `dedup_key`.
- **`EventSink.send` returns once the event is on DISK, not once it is delivered.** `EventQueue`
  persists, then attempts the network in the background. This is what keeps a decision point off
  the network: awaiting delivery made `shouldShowInterstitial()` inherit the sink's latency 1:1 —
  measured at 2.09 s against a 2.0 s sink, with a 30 s default request timeout behind it. If you
  need the attempts settled — before backgrounding, or in a test — call `await queue.quiesce()`.
  Durability is unaffected: the record is on disk before `send` returns, and `flush()` retries it.
- **Consent tiers are not equal.** The CRM bridge requires explicit `.granted`; personalization only
  requires "not denied". Guardrails always run — protecting a player never needs their permission.
