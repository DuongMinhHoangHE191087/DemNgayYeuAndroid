# AGENTS.md - InLove (DemNgayYeuAndroid)

## Project Context
- **Name**: InLove (Ứng dụng theo dõi kỷ niệm tình yêu, gợi ý quà tặng lãng mạn và trung tâm nhắc hẹn thông minh)
- **Platforms & Modules**:
  - **`:shared` (Kotlin Multiplatform)**: Pure domain brain in `shared/src/commonMain/kotlin/com/example/shared/` (`kotlinx-datetime`, `kotlinx-serialization-json`, `kotlinx-coroutines-core`) + Swift bridge in `shared/src/iosMain/kotlin/com/example/ios/`.
  - **`:app` (Android Native)**: Jetpack Compose & Material 3 (`DESIGN.md`), Clean Architecture / MVVM (`ui/designsystem/`, `ui/dialogs/`, `ui/screens/`, `ui/viewmodel/controllers/`), Room Database (v12/v13/v14 schemas pinned), Retrofit/OkHttp/Moshi, Firebase (Auth, Firestore, AI, Functions, App Check), Cloudinary, Google Play Billing (`VipProductIds`), `:appplugin` monetization facade (`AppPluginMonetizationFacade`), Target SDK 36 / Min SDK 24.
  - **`iosApp/` (iOS Native)**: SwiftUI views & theme (`iosApp/InLoveIos/`), WidgetKit (`iosApp/InLoveWidget/`), StoreKit 2 (`iosApp/StoreKit/InLove.storekit`), and SwiftPM core logic package (`iosApp/Packages/InLoveIosCore/`).
  - **`firebase/functions/`**: Node 22 Cloud Functions (`index.js`, `src/aiAdvisor.js`, `src/entitlement/gatePolicy.js`, `src/rateLimit.js`) enforcing App Check, per-UID rate limits, and entitlement gates.
  - **Testing**: `node:test` QA & Cloud Functions suites, `commonTest` / `androidHostTest`, JUnit + Robolectric + Roborazzi on Android, and `swift test` for `InLoveIosCore`.

<!-- SKILLS_INDEX_START -->
## Available Skills & Triggers

- **codegraph** (file:///d:/DMHoang/Project_GitHub/DemNgayYeuAndroid/.agents/skills/codegraph/SKILL.md)
  - (triggers: codegraph, code intelligence, ast, symbol, callers, callees, impact analysis)
- **brainstorming** (file:///d:/DMHoang/Project_GitHub/DemNgayYeuAndroid/.agents/skills/brainstorming/SKILL.md)
  - (triggers: brainstorm, brainstorming, explore idea, design spec, feature design)
- **test-driven-development** (file:///d:/DMHoang/Project_GitHub/DemNgayYeuAndroid/.agents/skills/test-driven-development/SKILL.md)
  - (triggers: tdd, test first, red green refactor, failing test)
- **requesting-code-review** (file:///d:/DMHoang/Project_GitHub/DemNgayYeuAndroid/.agents/skills/requesting-code-review/SKILL.md)
  - (triggers: request code review, pre-merge review, pr review)
- **receiving-code-review** (file:///d:/DMHoang/Project_GitHub/DemNgayYeuAndroid/.agents/skills/receiving-code-review/SKILL.md)
  - (triggers: receive review, address review comments, review feedback)
- **systematic-debugging** (file:///d:/DMHoang/Project_GitHub/DemNgayYeuAndroid/.agents/skills/systematic-debugging/SKILL.md)
  - (triggers: systematic debug, root cause analysis, 5 whys, bug investigation)
- **writing-plans** (file:///d:/DMHoang/Project_GitHub/DemNgayYeuAndroid/.agents/skills/writing-plans/SKILL.md)
  - (triggers: write plan, implementation plan, work breakdown)
- **executing-plans** (file:///d:/DMHoang/Project_GitHub/DemNgayYeuAndroid/.agents/skills/executing-plans/SKILL.md)
  - (triggers: execute plan, implementation execution, plan runner)
- **android-clean-architecture** (file:///d:/DMHoang/Project_GitHub/DemNgayYeuAndroid/.agents/skills/android-clean-architecture/SKILL.md)
  - (triggers: android architecture, usecase, repository, room, data layer, domain layer, viewModel, hilt, kmp)
- **compose-multiplatform-patterns** (file:///d:/DMHoang/Project_GitHub/DemNgayYeuAndroid/.agents/skills/compose-multiplatform-patterns/SKILL.md)
  - (triggers: jetpack compose, compose ui, composable, preview, layout, modifier, theme)
- **mobile-design** (file:///d:/DMHoang/Project_GitHub/DemNgayYeuAndroid/.agents/skills/mobile-design/SKILL.md)
  - (triggers: mobile design, touch target, UI, UX, material3, thumb zone, mobile animation, responsiveness)
- **kotlin-coroutines-flows** (file:///d:/DMHoang/Project_GitHub/DemNgayYeuAndroid/.agents/skills/kotlin-coroutines-flows/SKILL.md)
  - (triggers: coroutine, flow, stateflow, sharedflow, suspend, async, lifecycleScope, viewModelScope)
- **kotlin-testing** (file:///d:/DMHoang/Project_GitHub/DemNgayYeuAndroid/.agents/skills/kotlin-testing/SKILL.md)
  - (triggers: unit test, junit, roborazzi, robolectric, compose test, mockk, testing)
- **kotlin-patterns** (file:///d:/DMHoang/Project_GitHub/DemNgayYeuAndroid/.agents/skills/kotlin-patterns/SKILL.md)
  - (triggers: kotlin patterns, idiom, extension function, sealed class, data class)
- **clean-code** (file:///d:/DMHoang/Project_GitHub/DemNgayYeuAndroid/.agents/skills/clean-code/SKILL.md)
  - (triggers: refactor, clean code, code quality, naming, code style)
- **app-builder** (file:///d:/DMHoang/Project_GitHub/DemNgayYeuAndroid/.agents/skills/app-builder/SKILL.md)
  - (triggers: app build, feature implementation, new screen, setup, scaffold)
- **tdd-master-workflow** (file:///d:/DMHoang/Project_GitHub/DemNgayYeuAndroid/.agents/skills/tdd-master-workflow/SKILL.md)
  - (triggers: tdd, test driven, red green refactor)
- **common-session-retrospective** (file:///d:/DMHoang/Project_GitHub/DemNgayYeuAndroid/.agents/skills/common/common-session-retrospective/SKILL.md)
  - (triggers: retrospective, learn, skill gap, session review, post-mortem)
- **common-mobile-ux-core** (file:///d:/DMHoang/Project_GitHub/DemNgayYeuAndroid/.agents/skills/common/common-mobile-ux-core/SKILL.md)
  - (triggers: mobile ux, touch interaction, accessibility, mobile layout)
- **common-code-review** (file:///d:/DMHoang/Project_GitHub/DemNgayYeuAndroid/.agents/skills/common/common-code-review/SKILL.md)
  - (triggers: code review, audit, security review, PR check)
- **impeccable** (file:///d:/DMHoang/Project_GitHub/DemNgayYeuAndroid/.agents/skills/impeccable/SKILL.md)
  - (triggers: impeccable, ui design, polish, critique, audit, layout, typeset, colorize, animate, contrast, beautify, redesign)
- **android-ads-monetization** (file:///d:/DMHoang/Project_GitHub/DemNgayYeuAndroid/.agents/skills/android-ads-monetization/SKILL.md)
  - (triggers: ads, admob, banner, interstitial, app open ads, monetization, gắn quảng cáo)
- **android-billing-subscription** (file:///d:/DMHoang/Project_GitHub/DemNgayYeuAndroid/.agents/skills/android-billing-subscription/SKILL.md)
  - (triggers: billing, in-app purchase, subscription, paywall, vip, gói đăng ký)
<!-- SKILLS_INDEX_END -->

## Refactored Architecture & KMP Structure

### 1. `:shared` Pure KMP Domain Brain (`shared/src/commonMain/kotlin/com/example/shared/`)
- **`dossier/`**: `PartnerDossierModels.kt` (`PartnerDossier`, `LoveLanguage`, `PartnerSizes`, `PartnerFoodAndCare`, `SecretHintNote`, `GivenGiftRecord`), `PartnerDossierStore.kt`, `fakes/FakePartnerDossierStore.kt`.
- **`calendar/`**: `LunarCalendar2025To2040.kt` (verified 2025–2040 solar/lunar holiday dates, e.g. Rằm tháng Giêng 2027 = `2027-02-20`, Lễ Thất Tịch 7/7 ÂL), `AnniversaryBehaviorEngine.kt` (`NextOccasionReadiness`, `PreparationStage`, milestone progression `[7..3650]`, Day 1 = `startDate` so Day `N` = `startDate + (N - 1)` days), `OccasionProfilesCatalog.kt`.
- **`ai/`**: `AiGiftAdvisorGateway.kt`, `SmartRuleGiftAdvisor.kt`, `AiPromptBuilder.kt`, `GiftArchetypeCatalog.kt`, `GiftArchetypeExtraCatalog.kt`, `fakes/FakeAiGiftAdvisorGateway.kt`.
- **`monetization/`**: `MonetizationPolicy.kt` (`ProTierCatalog` with canonical SKUs `vip_monthly`, `vip_yearly`, `vip_lifetime`, `ProFeature`, `SacredZone`, `AdPlacementPolicy` enforcing 60s warmup, 120s interstitial cooldown, max 3 interstitials/session, and zero ads in sacred zones or for Pro users).
- **`sync/`**: `CoupleOnlineModels.kt` (`OnlineSyncPhase`, `OnlineSyncPhaseResolver`, `CoupleCodeParser`, `DossierMergePolicy`, `CoupleOnlineSyncGateway`) + `fakes/FakeCoupleOnlineSyncGateway.kt`.
- **`guide/`**: `InLoveGuideCatalog.kt` (`GuideChapterId`, `GuideQuickAction`, `GuideStep`, `GuideChapter`, 4-chapter concise guide).
- **`widget/`**: `WidgetSnapshotContract.kt` (`CoupleWidgetSnapshot`, `CoupleWidgetSnapshotJson`).

### 2. Android Data, Monetization & Design System (`app/src/main/java/com/example/`)
- **Repositories & Facade**:
  - `data/dossier/`: Local-first `PartnerDossier` JSON persistence (`PartnerDossierRepository` / `LocalJsonPartnerDossierRepository`) + owner-only cloud sync (`CloudPartnerDossierSync`, `fakes/FakePartnerDossierCloudGateway`) synced cleanly without modifying Room schema (`Entities.kt` / `AppDatabase.kt`).
  - `data/ai/`: `HybridAiGiftAdvisorRepository` implementing `:shared`'s `AiGiftAdvisorGateway` (Cloud Functions `generateGiftAdvice` with offline `SmartRuleGiftAdvisor` fallback).
  - `monetization/`: `AppPluginMonetizationFacade` wrapping `:appplugin` (`MonetizationSdk`, `AdsHelper`, `ConsentManager`) and `:shared`'s `AdPlacementPolicy` / `SacredZone`.
- **Design System (`ui/designsystem/` & `res/font/`)**:
  - `InLoveTokens.kt`, `InLoveInputs.kt` (`inLoveOutlinedTextFieldColors()`, `InLoveTextField`), `InLoveCards.kt`, `InLoveModalScaffold.kt`.
  - Bundled static OFL fonts in `app/src/main/res/font/`: `Lora` (`lora_regular.ttf`, `lora_semibold.ttf`, `lora_bold.ttf`) + `Be Vietnam Pro` (`be_vietnam_pro_regular.ttf`, `be_vietnam_pro_medium.ttf`, `be_vietnam_pro_semibold.ttf`, `be_vietnam_pro_bold.ttf`) — attribution in `docs/FONT_LICENSES_OFL.txt`.
  - Contrast tokens per `DESIGN.md`: `TextDarkPlum` (`#1F0416`) and `TextSubtlePlum` (`#4A1934`) on `#FFFFFF` / `#FFF5F8` surfaces (minimum 48.dp touch targets).
- **UI Decomposition**:
  - `ui/dialogs/` (thin `Dialogs.kt` facade), `ui/screens/gift/`, `ui/screens/home/`, `ui/screens/calendar/`, `ui/screens/guide/`, `ui/screens/paywall/`, `ui/viewmodel/controllers/`.

### 3. iOS SwiftUI & `InLoveIosCore` (`iosApp/` & `shared/src/iosMain/kotlin/com/example/ios/`)
- **Kotlin → Swift Bridge**: `IosInLoveFacade.kt` must satisfy `scripts/kmp/check-ios-bridge.sh` (every public `fun`/constructor needs `@Throws(Exception::class)` or `// no-throw: <reason>`; no default parameter values, `value class`, `kotlin.Result`, generic interfaces, or `runBlocking`).
- **Swift Core Package (`iosApp/Packages/InLoveIosCore/`)**: Pure Swift 5.10 models & services (`PartnerDossierModels`, `OccasionModels`, `GiftConciergeModels`, `SubscriptionModels`, `CoupleWidgetSnapshot`, `LoveDaysCalculator`, `PartnerDossierStore`, `IosGiftConciergeService`, `StoreKitEntitlementManager`, `IosAdPlacementManager`) testable via `swift test` on macOS/CI.
- **SwiftUI App & WidgetKit**: Modular views in `iosApp/InLoveIos/`, build config in `iosApp/Config/Base.xcconfig`, StoreKit 2 catalog in `iosApp/StoreKit/InLove.storekit`, and WidgetKit timeline in `iosApp/InLoveWidget/`.

## Development & Architecture Guardrails
1. **ARCH-01 (Clean Layer Boundaries)**:
   - `app/src/main/java/com/example/ui/**` MUST NOT import `androidx.room.*` or `com.google.firebase.firestore.*`.
   - `shared/src/commonMain/**` MUST NOT import `android.*`, `java/javax`, `com.app.plugin.*`, `com.google.firebase.*`, or Moshi (enforced by `check-architecture.mjs` and `GG-01` in `docs/qa/grep-gates.json`).
2. **ARCH-02 (SOLID File-Size Budgets & Ratchet)**:
   - Every `.kt` / `.swift` file in new/refactored packages (`shared/src/**`, `ui/designsystem/**`, `ui/dialogs/**`, `ui/viewmodel/controllers/**`, `ui/screens/gift/**`, `ui/screens/home/**`, `ui/screens/calendar/**`, `ui/screens/guide/**`, `ui/screens/paywall/**`, `data/dossier/**`, `data/ai/**`, `monetization/**`, `iosApp/**`) MUST stay **<= 550 lines**.
   - Refactored caps: `Dialogs.kt <= 350`, `LoveHomeScreen.kt <= 550`, `GiftScreen.kt <= 650`, `AppModals.kt <= 680`, `InLoveViewModel.kt <= 1550`. Remaining legacy giant files are ratcheted at their baseline and must never grow larger.
3. **ARCH-03 (Design System & Contrast)**:
   - Never call `OutlinedTextFieldDefaults.colors(` outside `ui/designsystem/` — always use `inLoveOutlinedTextFieldColors()`.
   - Never use `Color.Gray` or `Color.LightGray` inside `ui/designsystem/`, `ui/dialogs/`, `ui/screens/gift/`, `ui/screens/home/`, or `ui/screens/calendar/`.
4. **ARCH-04 (Cross-Platform Monetization SKU Parity)**:
   - Keep the exact SKU set `["vip_lifetime", "vip_monthly", "vip_yearly"]` identical across `VipProductIds.kt`, `MonetizationPolicy.kt`, `iosApp/StoreKit/InLove.storekit`, and `iosApp/Packages/InLoveIosCore`.
5. **ARCH-05 (No Internal/Database Jargon in UI Copy)**:
   - User-facing string literals in `app/src/main/java/com/example/ui/` and `iosApp/InLoveIos/` MUST NOT leak technical or storage terms (`cơ sở dữ liệu`, `from the database`, `in the database`, `SQLite`, `Room DB`, `SharedPreferences`, `Firestore`) — keep copy warm, natural, and user-centered.
6. **Fakes & State Separation**:
   - Every interface in `shared/src/commonMain` ending in `Gateway`, `Api`, `Invoker`, `Storage`, `Store`, `Clock`, or `Engine` MUST have a matching `Fake<Name>` in `fakes/`, `testing/`, or `shared/src/commonTest` (or a reasoned entry in `docs/qa/fakes.json`).
   - State is exposed as immutable data classes via `StateFlow` from ViewModels; Room v12/v13/v14 schema hashes in `scripts/kmp/schema-baseline.sha256` must remain untouched unless deliberately migrating DB versions.
7. **Workflow & CI Safety**:
   - `.github/workflows/*.yml` must pass `scripts/qa/check-workflows.mjs` (actions pinned to `vX.Y.Z` or 40-hex SHA, `kmp-ios` included in `pull_request`/`push` branches, no `secrets.` in `pull_request`, `timeout-minutes` set on `macos-*` jobs).

## Mandatory Verification Gates (Before Completing Any Session)
Run all gates locally (and verify CI `.github/workflows/ci.yml`):
1. **All Node QA, Architecture & Cloud Functions Gates**:
   `node scripts/qa/run-all-gates.mjs`
   (Runs `check-workflows.mjs`, `check-grep-gates.mjs`, `check-fakes.mjs`, `check-architecture.mjs`, `scripts/qa/test/*.test.mjs`, and `firebase/functions/test/**/*.test.js`).
2. **Room Schema Baseline Check**:
   `bash scripts/kmp/schema-check.sh`
3. **Kotlin → Swift iOS Bridge Check**:
   `bash scripts/kmp/check-ios-bridge.sh`
4. **Android & KMP Shared Compile / Tests / Lint** (run Gradle sequentially on Windows low-RAM hosts, never in parallel):
   `./gradlew :shared:compileCommonMainKotlinMetadata :shared:testAndroidHostTest :app:testDebugUnitTest :app:lintDebug`
5. **iOS Core & KMP Simulator Tests (macOS)**:
   `swift test --package-path iosApp/Packages/InLoveIosCore && ./gradlew :shared:iosSimulatorArm64Test`
