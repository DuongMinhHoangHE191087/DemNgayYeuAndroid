# MASTER_REFACTOR_CHECKLIST.md — InLove Full KMP, AI, UI, Monetization & iOS Refactor

> Bảng theo dõi tổng thể mọi hạng mục bàn giao qua Wave 1, Wave 2 & Wave 3.
> **Cập nhật xác minh cuối cùng (Wave 3 — `#109`, `#103`, `#98`)**: Sau lần sửa cuối trên Windows, toàn bộ cổng kiểm chứng tự động (`run-all-gates.mjs` **6/6**, `schema-check.sh`, `check-ios-bridge.sh`, `:shared:compileCommonMainKotlinMetadata :shared:testAndroidHostTest` **33/33**, `:app:testDebugUnitTest` **1,420/1,420**, `:app:assembleDebug` **51,742,593 bytes**, `:app:lintDebug` **0** errors / **138** warnings, `git diff --check`) đều **PASS (exit 0)**, kèm kiểm thử thị giác Roborazzi Native Skia `Pixel8` cho đủ 8 màn hình `wave3_01..08.png` (`emulator-5554` giữ nguyên không can thiệp trong Wave 3 do có tiến trình ngoài `com.duongminhhoang.yokaicards` đang chạy). Các mục chạy trực tiếp trên macOS (`swift test`, `:shared:iosSimulatorArm64Test`, Xcode iOS build) giữ `[ ]` cho tới khi CI macOS (`macos-14`) thực thi.

---

## 1. `:shared` KMP Domain Brain, Wave 2–3 Polish & Unit Tests (`@arch-shared`)

- [x] `shared/src/commonMain/kotlin/com/example/shared/dossier/PartnerDossierModels.kt`: `PartnerDossier`, `LoveLanguage`, `PartnerSizes`, `PartnerFoodAndCare`, `SecretHintNote`, `GivenGiftRecord`, 14 khóa sở thích chuẩn, `completenessPercent()`, `missingProfileHints()`, `PartnerDossierJson`
- [x] `shared/src/commonMain/kotlin/com/example/shared/dossier/PartnerDossierStore.kt` + `fakes/FakePartnerDossierStore.kt`
- [x] `shared/src/commonMain/kotlin/com/example/shared/calendar/LunarCalendar2025To2040.kt`: Bảng âm lịch 2025–2040 khớp `docs/research/data/lunar-2025-2040.json` (Rằm Giêng 2027 = `2027-02-20`, Thất Tịch 7/7 ÂL)
- [x] `shared/src/commonMain/kotlin/com/example/shared/calendar/AnniversaryBehaviorEngine.kt` + `OccasionProfilesCatalog.kt`: 3-axis `OccasionProfile`, 20 cột mốc ngày (7..3650) theo chuẩn `Day 1 = startDate`, Monthiversary, 5 `PreparationStage`, `NextOccasionReadiness`
- [x] `shared/src/commonMain/kotlin/com/example/shared/ai/AiGiftAdvisorGateway.kt` + `fakes/FakeAiGiftAdvisorGateway.kt`
- [x] `shared/src/commonMain/kotlin/com/example/shared/ai/GiftArchetypeCatalog.kt` + `GiftArchetypeExtraCatalog.kt` + `SmartRuleGiftAdvisor.kt`: 29 mẫu quà song ngữ, lọc dị ứng/kiêng kỵ/trùng lặp lịch sử, soạn lời chúc VI/EN theo `RelationshipTone`
- [x] `shared/src/commonMain/kotlin/com/example/shared/ai/AiPromptBuilder.kt`: Prompt & JSON schema cho Gemini không rò rỉ PII
- [x] `shared/src/commonMain/kotlin/com/example/shared/monetization/MonetizationPolicy.kt` (`360L`): `ProTierCatalog` (`vip_monthly` `39.000₫`/`$2.99`, `vip_yearly` `199.000₫`/`$17.99` tiết kiệm `57%` + dùng thử `3` ngày, `vip_lifetime` `399.000₫`/`$39.99`, `ProPlanSpec` taglines + `planFor(sku)`, `ALL_SKUS` giữ nguyên chuẩn `ARCH-04`), `ProFeature`, `SacredZone`, `AdPlacementPolicy`
- [x] `shared/src/commonMain/kotlin/com/example/shared/sync/CoupleOnlineModels.kt` (`188L`) + `fakes/FakeCoupleOnlineSyncGateway.kt` (`28L`) (`Wave 3`): `OnlineSyncPhase` (7 trạng thái không emoji/jargon), `OnlineSyncPhaseResolver`, `CoupleCodeParser.extractCode`, `DossierMergePolicy.merge` (scalar theo `updatedAtEpochMs` mới hơn + union-merge `secretSurpriseNotes` & `giftHistory` theo `id`), `CoupleOnlineSyncGateway`
- [x] `shared/src/commonMain/kotlin/com/example/shared/guide/InLoveGuideCatalog.kt` (`204L`, `Wave 3`): `GuideChapterId`, `GuideQuickAction`, `GuideStep`, `GuideChapter`, 4 chương × 3 bước súc tích (`10–14` từ/bước, không emoji/jargon)
- [x] `shared/src/commonMain/kotlin/com/example/shared/widget/WidgetSnapshotContract.kt`: `CoupleWidgetSnapshot` + redaction + JSON codec
- [x] Wave 2 `unslop` + `impeccable` (`clarify`, `distill`) trên domain copy của `:shared`
- [x] Unit tests KMP thuần trong `shared/src/commonTest/kotlin/com/example/shared/` (`LunarCalendar2025To2040Test`, `AnniversaryBehaviorEngineTest`, `PartnerDossierTest`, `SmartRuleGiftAdvisorTest`, `MonetizationPolicyTest`, `WidgetSnapshotContractTest`, `sync/CoupleOnlineModelsTest`, `guide/InLoveGuideCatalogTest` — `:shared:testAndroidHostTest` **33/33 PASS**, `0` failures, `0` skipped)

---

## 2. Backend Cloud Functions, Firestore Rules, Remote Config & Seed/Sync (`@backend-monetization`)

- [x] `firebase/functions/src/aiAdvisor.js` + wiring `index.js`, `src/config.js`, `src/entitlement/gatePolicy.js` (`generateGiftAdvice` với AppCheck, rate limit, `tiered` gate: Free 2/tháng, VIP 60/tháng, Gemini 2.5 Flash + server smart fallback)
- [x] `firestore.rules` (`379L`) + `firebase/functions/test/privateDossierRules.test.js` (`71L`, `Wave 3`): rule owner-only `/users/{userId}/privateDossier/{docId}` (`docId == 'current'`, `isOwner(userId)`, `allow list: if false`, `payloadJson <= 32768`, `schemaVersion == 1`, zero partner access) + 3 rule-contract tests (`firebase/functions/test/**/*.test.js` **773/773 PASS**)
- [x] Firebase Remote Config & cấu hình quảng cáo release (`Wave 2`):
  - [x] `firebase/remoteconfig.template.json` (đầy đủ 15 JSON parameters cho Android `:appplugin` & iOS `monetization-brain-ios`) + khai báo trong `firebase.json`
  - [x] `app/src/main/res/values/ads_ids.xml` (`flagTest=0`, fallback chuỗi rỗng an toàn cho release, không chứa ID thử nghiệm trong `main`)
  - [x] `docs/MONETIZATION_AND_FIREBASE_SDK_ANALYSIS.md` (`146L`): tài liệu kiến trúc Auth, App Check, Firestore, Cloud Functions, `:appplugin` & `monetization-brain-ios` + Section 7 (Wave 3) phân tích unit economics giá `39.000₫` / `199.000₫` / `399.000₫` & đồng bộ online
- [x] `app/src/main/java/com/example/monetization/MonetizationGates.kt` + `AppPluginMonetizationFacade.kt` + sửa `ads/AdsEligibility.kt`, `ads/AdsManager.kt`, `ads/AdsManagerImpl.kt` theo `AdPlacementPolicy` & `SacredZone`
- [x] `app/src/main/java/com/example/data/dossier/`:
  - [x] `PartnerDossierRepository.kt` + `LocalJsonPartnerDossierRepository.kt` (atomic JSON per scopeId, không sửa Room schema v14)
  - [x] `CloudPartnerDossierSync.kt` (`281L`) + `fakes/FakePartnerDossierCloudGateway.kt` (`46L`) (`Wave 3`): pull + merge qua `DossierMergePolicy`, push debounce `/users/{uid}/privateDossier/current`, fallback guest/offline an toàn, nối vào `AppServiceLocator.kt` (`359L`) & `PartnerDossierController.kt` (`121L`) (`CloudPartnerDossierSyncTest.kt` `268L`, 6 tests PASS)
- [x] Online Couple Sync Polish (`Wave 3`):
  - [x] `OnlineCoupleRepository.kt`: làm sạch emoji trong `AVAILABLE_INTERESTS` giữ nguyên interest keys
  - [x] `PairingAndAccountController.kt` (`175L`): chuẩn hóa link/mã qua `CoupleCodeParser.extractCode`
  - [x] `InLoveViewModel.kt` (`1,334L <= 1,550`): lộ `onlineSyncPhase: StateFlow<OnlineSyncPhase>` & tự đồng bộ lại `onlineRepo.refreshState()` + `cloudPartnerDossierSync.syncNow()` khi `NetworkMonitor.isOnline` chuyển `false -> true`
- [x] `app/src/main/java/com/example/data/ai/HybridAiGiftAdvisorRepository.kt` (Cloud Function online -> `SmartRuleGiftAdvisor` fallback offline/guest)
- [x] Dữ liệu seed, lịch âm & sửa lỗi âm thanh thông báo:
  - [x] `VietnameseHolidays.kt`: sửa Rằm Giêng 2027 -> `2027-02-20`, mở rộng tới 2040, thêm Lễ Thất Tịch 7/7 ÂL
  - [x] `OfflineSeeder.kt`: `notificationEnabled = holiday.isCoupleRelevant`; **10 huy hiệu cột mốc** (`7..1825` ngày) & **8 mục chuẩn bị** (`id = 1..8`)
  - [x] `GiftIdeasSeed.kt`: **20 món quà chọn lọc** (`0` từ cấm, khớp đủ khóa sở thích cá nhân hóa)
  - [x] `LoveAnniversaryMilestoneScheduler.kt` & `BootCompletedReceiver.kt`: khớp `start + (N - 1)` của `LoveMilestoneRules`, re-arm trên `BOOT_COMPLETED`
  - [x] `ReminderAlarmReceiver.kt`: tách kênh yên lặng (`love_anniversaries_silent_channel` / `love_reminders_silent_channel`) khi tắt âm thông báo (`ReminderSoundSettingTest` **3/3 PASS**)

---

## 3. Android Typography, Alignment Grid, Subtext Distillation & 60fps Modular UI (`@ui-design-refactor`)

- [x] `app/build.gradle.kts`: khai báo `implementation(project(":shared"))`
- [x] Bundled Static OFL Fonts & Design System (`Wave 3`):
  - [x] `app/src/main/res/font/`: 7 font TrueType tĩnh (`lora_regular.ttf`, `lora_semibold.ttf`, `lora_bold.ttf`, `be_vietnam_pro_regular.ttf`, `be_vietnam_pro_medium.ttf`, `be_vietnam_pro_semibold.ttf`, `be_vietnam_pro_bold.ttf`) + `docs/FONT_LICENSES_OFL.txt`
  - [x] `DESIGN.md` (`86L`), `Type.kt` (`66L`), `InLoveTokens.kt` (`201L`), `InLoveInputs.kt`, `InLoveCards.kt`, `InLoveModalScaffold.kt`: hệ chữ `Lora` (`DisplaySerif`) + `Be Vietnam Pro` (`BodySans`), số thẳng hàng `tnum`, và lưới căn lề `InLoveLayoutGrid` (`16.dp` màn hình / `16.dp` card / `32.dp` keyline trong card)
  - [x] Sửa đồng bộ lề `16.dp` & bỏ vòng tròn icon trang trí đầu mục ở `CommonComponents.kt` (`633L`), `LoveDaysCalculatorWidget.kt` (`727L`), `CalendarScreen.kt` (`1,494L`), `GiftScreen.kt` (`540L`), thêm `contentType` cho `LazyColumn`
- [x] Chưng cất thông tin 3 tầng (Subtext Distillation) & `unslop` + `impeccable` (`docs/UI_IMPECCABLE_UNSLOP_AUDIT.md` `95L`):
  - [x] `PartnerDossierCard.kt` (`132L`): tiêu đề gọn + `%` `tnum`, bỏ dòng coaching dư (`"Chọn cách người ấy..."`) & thanh lặp
  - [x] `NextOccasionReadinessCard.kt` (`180L`): tên dịp `Lora`, metadata `dd/MM/yyyy · Còn X ngày` + pill giai đoạn, ẩn filler trước ngày lễ (`daysRemaining > 0`)
  - [x] `AiGiftConciergeSection.kt` (`385L`) & `GiftScreen.kt`: lược bỏ helper text lặp, giữ gợi ý 1 câu súc tích
  - [x] `MemoryPrivacyNotice.kt` (`112L`) & `SettingsScreen.kt` (`1,058L`): thu gọn tường chữ quyền riêng tư mặc định kèm nút mở/đóng `btn_toggle_privacy_details`
  - [x] `PairingScreen.kt` (`1,146L`): gắn `pairing_sync_phase_banner` từ `onlineSyncPhase`, chuẩn hóa mã qua `CoupleCodeParser.extractCode`, hiển thị chip `AVAILABLE_INTERESTS` không emoji
  - [x] `PaywallScreen.kt` (`726L`) & `ProBenefitsMatrixSection.kt`: tiêu đề `Lora`, gắn tagline gói từ `ProTierCatalog.planFor(...)` (`Tiết kiệm 57% · Dùng thử 3 ngày miễn phí`) trong khi giữ nguyên giá thật từ Google Play
- [x] Phân tách SOLID & tối ưu 60fps (`Wave 3`):
  - [x] Rút gọn `LoveHomeScreen.kt` xuống **`409L`** (`<= 550` ratchet) bằng 5 thành phần trong `ui/screens/home/` (`HomeDaysCounterHero.kt` `176L`, `HomeCoupleProfileCard.kt` `359L`, `HomeCoupleReflectionsCard.kt` `232L`, `HomeStatusBanners.kt` `206L`, `HomeNextMilestoneCard.kt` `195L`), đọc state animation trong `Canvas` DrawScope & `Modifier.graphicsLayer`
  - [x] Tách `InLoveUserGuideDialog.kt` (`267L <= 550`, 4 chương × 3 bước từ `:shared` `InLoveGuideCatalog` + `GuideQuickAction`) khỏi `AppModals.kt` (`644L <= 680` ratchet), đấu nối tại `MainActivity.kt` (`640L <= 665`)
  - [x] Tách `Dialogs.kt` sang `ui/dialogs/` (9 file `<= 550`) & 6 controllers trong `ui/viewmodel/controllers/`, giữ `ARCH-01..ARCH-05` xanh 100%
- [x] Sửa toàn bộ 5 lỗi phát hiện từ live `LDPlayer9` Run #1 (`#65` -> `#67`, `#81`): P0 cuộn dọc 4 tab chính (`heightOffsetLimit`), bố cục Paywall first-fold, Calendar `FlowRow`/filter chips/thumbnail fallback/emoji đầu dòng, unslop + `.clip(...)`, và trường chọn ngày bắt đầu yêu ở Onboarding bước 5 (`onboarding_start_date_field`)
- [x] Toàn bộ `:app:testDebugUnitTest` **1,420/1,420 PASS** (`0` failures, `0` skipped — gồm `MainTabsScrollTest` **10/10**, `CloudPartnerDossierSyncTest`, `PaywallFooterTest` `4/4`, `NoAiClaimStringsTest` `1/1`, `ReminderSoundSettingTest` `3/3`, `InLoveDesignSystemTest` `3/3`, `ControllersUnitTest` `5/5`, toàn bộ `*GoldenTest`)

---

## 4. iOS Bridge, Swift Core Package, SwiftUI App & WidgetKit (`@ios-app`)

- [x] `shared/src/iosMain/kotlin/com/example/ios/IosInLoveFacade.kt` (`533L <= 550`): biên dịch/kiểm tra tĩnh tuân thủ 100% `scripts/kmp/check-ios-bridge.sh` (`ios-bridge OK`), bổ sung `proPlansJson()`, `IosOnlineSyncFacade`, và `IosGuideFacade` (`#98`)
- [x] `iosApp/Packages/InLoveIosCore/`: `Package.swift`, Models (`SubscriptionModels.swift` `172L`), Services (`StoreKitEntitlementManager.swift` `256L`, `IosOnlineCoupleCoordinator.swift` `506L` với `OnlineSyncPhase`, `CoupleCodeParser`, `DossierMergePolicy`, offline queue, reconnect auto-merge), và test suites trong `Tests/InLoveIosCoreTests/` (`OnlineSyncAndPricingTests.swift` `195L`, đạt `ARCH-02` / `ARCH-04` SKU parity)
- [x] `iosApp/StoreKit/InLove.storekit`: cập nhật bảng giá tham chiếu (`$2.99` monthly / `$17.99` yearly `57%` + `P3D` free trial / `$39.99` lifetime) & bản địa hóa song ngữ `vi` + `en_US`, giữ nguyên `["vip_lifetime", "vip_monthly", "vip_yearly"]`
- [x] `iosApp/InLoveIos/Monetization/IosMonetizationBrainBootstrap.swift` (`Wave 2`, nối `AppEnvironment.swift` `480L` & `InLoveIosApp.swift`) + `Services/FirebaseCoupleCloudAdapter.swift` (`87L`, `/users/{uid}/privateDossier/current` + `NWPathMonitor` reconnect resync, `Wave 3`)
- [x] `iosApp/InLoveIos/` (`Theme/InLoveIosTheme.swift` `165L` với **New York Serif** + `16pt`/`32pt` grid; `Bridge/SharedKmpBridgeAdapter.swift` `229L`; `LoveHomeView.swift` `328L`, `PartnerDossierView.swift` `328L`, `GiftConciergeView.swift` `383L`, `PaywallView.swift` `250L`, `SettingsView.swift` `349L`, `MemoriesGridView.swift` `274L`, `CalendarMilestonesView.swift` `228L`, `CouplePairingSheet.swift` `355L`, `InLoveGuideModels.swift` `248L`, `InLoveGuideSheet.swift` `126L`) — toàn bộ file Swift `<= 506L` (`<= 550`), `0` jargon `ARCH-05`
- [x] `iosApp/InLoveWidget/InLoveWidget.swift` đọc `CoupleWidgetSnapshot` (giữ nguyên `Info.plist`, `InLoveWidget.entitlements`, `PrivacyInfo.xcprivacy`)
- [ ] macOS CI execution: `swift test --package-path iosApp/Packages/InLoveIosCore` (chờ chạy trên runner `macos-14`)
- [ ] macOS CI execution: `./gradlew :shared:iosSimulatorArm64Test` & Xcode iOS build (chờ chạy trên runner `macos-14`)

---

## 5. QA Scripts, CI Workflows, Skills & Strategic Docs (`@qa-ci-architect` & `@arch-shared`)

- [x] `.github/workflows/ci.yml`: pass `node scripts/qa/check-workflows.mjs`
- [x] Cài đặt skill `heydamir/unslop` cục bộ (`~/.agents/skills/unslop` & `~/.claude/skills/unslop`)
- [x] `scripts/qa/check-architecture.mjs` + `scripts/qa/test/check-architecture.test.mjs` + `AGENTS.md`: mở rộng **`ARCH-01..ARCH-05`** (thêm strict `<= 550` cho `ui/screens/guide/` & `ui/screens/paywall/`, siết ratchet `LoveHomeScreen.kt <= 550` & `AppModals.kt <= 680`, cấm jargon DB/nội bộ trong UI string; `qa-unit-tests` **90/90 PASS**)
- [x] `scripts/qa/run-all-gates.mjs` + `docs/qa/grep-gates.json`
- [x] Tài liệu chiến lược & đối chiếu:
  - [x] `docs/USER_BEHAVIOR_AND_MONETIZATION_STRATEGY.md`
  - [x] `docs/ARCHITECTURE_AND_SOLID_GUIDE.md`
  - [x] `docs/UI_IMPECCABLE_UNSLOP_AUDIT.md`
  - [x] `docs/MONETIZATION_AND_FIREBASE_SDK_ANALYSIS.md`
  - [x] `docs/FONT_LICENSES_OFL.txt`
  - [x] `docs/MASTER_REFACTOR_CHECKLIST.md`

---

## 6. Cổng Kiểm Chứng Bắt Buộc & Kiểm Thử Thị Giác (`@qa-verifier` — Wave 3 `#109` & Live LDPlayer9 `#65`/`#79`)

| Gate / Hạng mục | Lệnh / Phạm vi kiểm chứng | Trạng thái | Kết quả đã xác minh |
|---|---|---|---|
| **Gate 1** | `node scripts/qa/run-all-gates.mjs` | [x] PASS | exit `0` — **6/6 gates PASS** (`check-workflows` 99ms, `check-grep-gates` 86ms `0 match`, `check-fakes` 73ms, `check-architecture` 396ms `ARCH-01..ARCH-05` gồm `LoveHomeScreen <= 550` & `AppModals <= 680`, `qa-unit-tests` **90/90**, `functions-unit-tests` **773/773**) |
| **Gate 2** | `bash.exe scripts/kmp/schema-check.sh` | [x] PASS | exit `0` — `schema-check OK` (SHA-256 Room v12/v13/v14 nguyên vẹn) |
| **Gate 3** | `bash.exe scripts/kmp/check-ios-bridge.sh` | [x] PASS | exit `0` — `ios-bridge OK` |
| **Gate 4** | `.\gradlew.bat :shared:compileCommonMainKotlinMetadata :shared:testAndroidHostTest` | [x] PASS | exit `0` — **33/33 tests PASS**, `0` failures, `0` errors, `0` skipped |
| **Gate 5** | `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug` | [x] PASS | exit `0` — **1,420/1,420 tests PASS**, `0` fail, `0` skipped (gồm `MainTabsScrollTest` **10/10**, `CloudPartnerDossierSyncTest`, `PaywallFooterTest` `4/4`, `NoAiClaimStringsTest` `1/1`, `ReminderSoundSettingTest` `3/3`, `InLoveDesignSystemTest` `3/3`, `ControllersUnitTest` `5/5`, toàn bộ `*GoldenTest`); APK `app/build/outputs/apk/debug/app-debug.apk` (**51,742,593 bytes**) |
| **Gate 6** | `.\gradlew.bat :app:lintDebug` | [x] PASS | exit `0` — chuỗi Gradle gộp Gate 4–6 `BUILD SUCCESSFUL in 15m 5s`; `app/build/reports/lint-results-debug.xml`: **0 errors**, **138 warnings**, **0 info**; `No Gradle daemons are running` |
| **Gate 7** | `git diff --check` | [x] PASS | exit `0` — không có lỗi whitespace/conflict |
| **Wave 3 Skia Visual Audit** | `:app:recordRoborazziDebug` (`POSTFIX_ARTIFACT_DIR`, `#109`) | [x] PASS | Chụp & duyệt trực quan đủ 8 ảnh Native Skia `Pixel8` (`wave3_01_home_top.png` .. `wave3_08_paywall.png`, `139,769 B` .. `566,345 B`); thư mục `app/src/test/screenshots/` giữ nguyên 5 ảnh gốc |
| **Live LDPlayer9 (Wave 2)** | `emulator-5554` (`#65`, `#79`, `#109`) | [x] PASS (ghi chú) | Wave 2 Run #1 (`0` app crashes trên 10 màn `live_00..09.png`) + Run #2 (`live_00_onboarding.png` verified); Wave 3 không đụng `emulator-5554` (`no install`, `no quitall`) do `com.duongminhhoang.yokaicards` đang chạy đồng thời |
| **macOS CI** | `swift test --package-path iosApp/Packages/InLoveIosCore && ./gradlew :shared:iosSimulatorArm64Test` | [ ] Pending macOS | Chưa chạy được trên Windows host; chờ runner `macos-14` (`ios-kmp-and-swift`) |
