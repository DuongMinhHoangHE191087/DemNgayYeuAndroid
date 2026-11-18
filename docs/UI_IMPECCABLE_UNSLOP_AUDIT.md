# InLove UI Unslop & Impeccable Design Audit

This document records the Wave 2 visual, interaction, and UX-copy audit for InLove across Android Jetpack Compose (with cross-platform alignment reported for `:shared` and SwiftUI iOS), structured around the `unslop` (Damir Sokolovsky, CC BY 4.0) 9-pass cleanup and `impeccable` (`critique`, `distill`, `quieter`, `clarify`, `craft-floor`, `polish`) principles.

---

## 1. Product Direction (`unslop` 5-Line Anchor)

- **Product**: A private couple memory, anniversary countdown, and thoughtful gift-planning companion in Vietnamese and English.
- **Character**: Warm, calm, attentive, and unhurried.
- **World**: Linen stationery, keepsake photographs, handwritten greeting cards, and quiet plum ink on warm rose paper.
- **Moments**: The live Days-in-Love counter aura on Home and the curated Gift & Love-Note Concierge results on Gifts.
- **Signature**: High-contrast plum ink (`TextDarkPlum #1F0416`, `TextSubtlePlum #4A1934`) paired with rose card borders (`#FFC6DB`) and `48.dp+` rendered touch targets (`MinTouchBox` under `0.85f` `AppDensity`).

---

## 2. Critique & Before/After Audit (Android)

### A. Visual Clutter, Icon Stacking & Noise (`distill` + `quieter`)

| Surface / File | Before (Slop / Clutter) | After (Craft & Restraint) |
|---|---|---|
| `GiftCategoryFilterRow` in `ui/screens/gift/GiftScreenSections.kt` | Unselected category pills rendered **icon-only** without text labels ("mystery chips"), while selected chips stacked both icon and text. | Every chip always displays a clear text label in `MinTouchBox` height with `InLoveRadii.InputSoft` corners and drops decorative chip icons. |
| `GiftVipProposalBanner` in `ui/screens/gift/GiftScreenSections.kt` | Heavy 3-stop magenta gradient card holding an empty textless `Star` badge and 3 icon-only boxes (`LocalFlorist`, `DinnerDining`, `Mail`). | Calm `SoftBlushCard` with `1.dp` `CardBorder`, clear title + concrete 1-line description, and solid `Primary` CTA (`btn_view_vip_proposal` keeping exact `"Xem gói VIP"` / `"View VIP plan"` test contract). |
| `GiftAmbientBannerCard`, `GiftChecklistCard`, `GiftRemindersCard` in `ui/screens/gift/GiftScreenSections.kt` | `Timer` icon beside percentage and `36.dp` circular colored icon halos on every collapsible section header. | Clean left-aligned title + count typography paired with crisp `MinTouchBox` Add/Expand controls. |
| `PartnerDossierCard.kt` | `Favorite` icon on section header, `WarningAmber` / `Lock` inside status pills, `"💡 "` emoji prefix on hints, `EditNote` inside primary CTA. | Text-and-tint pills only, emoji-free actionable hint copy, clean `Update Sweetheart Dossier` / `Cập Nhật Hồ Sơ Người Thương` CTA. |
| `AiGiftConciergeSection.kt` | Double `AutoAwesome` sparkle icons (header + CTA), `WarningAmber` icon, `"✨ "` and `"⚠️ "` emoji prefixes in gift cards, `"Thông minh"` / `"Smart Local"` hype pill. | Zero sparkle icons or emojis; factual `"Gợi ý chọn lọc"` / `"Curated"` and `"Riêng hai bạn"` / `"Tailored"` source labels, semantic warning borders without emoji noise. |
| `NextOccasionReadinessCard.kt` | Circular gradient icon halo next to headline, `AutoAwesome` / `Schedule` icons inside status pills, icon stacking on CTA buttons. | Left-aligned typographic headline + days-remaining metadata, clean text-role pill, and `MinTouchBox` dual action buttons. |
| `MilestoneEnrichmentBadges.kt` | `AutoAwesome` header icon, emojis (`💞`, `📅`, `🌌`) inside milestone/Monthiversary/Thất Tịch pills, icons inside `MilestoneLeadTimeHint`. | Pure typographic pills using interpunct separators (`520 ngày · còn Xn`, `Thất Tịch 7/7 ÂL · YYYY-MM-DD`) and quiet bordered lead-time chips. |
| `LoveHomeScreen.kt` | Loud orange `#FFF3E0` / `#BF360C` `"Hồ sơ của bạn đang là \"Vô danh\""` warning with `AutoAwesome` sparkle and `!`, `"Vô danh"` red chip on profile card, double icons (`VolunteerActivism` + `Rounded.Favorite`) around `daysInLove`, circular `VolunteerActivism` halo in Next Anniversary card, double icons (`Favorite` + `Send`) on quote send button, `FontStyle.Italic` on quote body, and 6 emoji-heavy reflections. | Calm white/rose setup prompt (`"Thêm tên và ngày sinh của bạn"` / `"Add your name and birthday"`), soft `"Chưa đặt tên"` / `"Not set"` pill, single-focus counter & milestone actions with `MinTouchBox`, normal reading typography, and **12 natural, emoji-free Vietnamese & English couple reflections** verified by updated `QuoteRotationTest.kt`. |
| `SettingsScreen.kt` | Repeated `36.dp` circular pastel icon halos around every switch/clickable row, unused `subtitle` strings silently dropped on rows, `👑` / `✨` emojis on VIP card, hard-coded `Color.Gray` (`#757575`) in deletion dialog and account email, short `"Không quảng cáo"` VIP subtitle. | Clean aligned `20.dp` line icons without circular halos, `MinTouchBox` (`48.dp+` effective) enforced on `SettingSwitchRow`, `SettingClickableRow`, and `LanguageToggleButton`, secondary `subtitle` copy visibly rendered in `OnSurfaceVariant`, emoji-free VIP card (`"Bỏ toàn bộ quảng cáo"` / `"Ad-free experience"`), and `OnSurfaceVariant` contrast tokens. |
| `ProBenefitsMatrixSection.kt` | `28.dp` pink circles behind every Pro benefit icon and `AutoAwesome` sparkle for concierge benefit. | Calm `18.dp` semantic line icons (`CardGiftcard` instead of sparkle) aligned with `TextDarkPlum` titles and `TextSubtlePlum` descriptions. |

---

### B. Technical & Database Jargon Removal (`ARCH-05` + `clarify`)

All user-visible UI strings and ViewModel toasts were audited and scrubbed of internal storage/backend terms (`cơ sở dữ liệu`, `from the database`, `Firestore`, `Room`):

- `GiftScreen.kt`: `"from the database"` / `"khỏi cơ sở dữ liệu"` -> natural `"Remove reminder '...' from your gift list?"` / `"Bạn có muốn xóa lời nhắc '...' khỏi danh sách quà không?"`.
- `CalendarScreen.kt`: `"from the database"` / `"khỏi cơ sở dữ liệu"` -> `"Remove anniversary '...' from your calendar?"` / `"Bạn có chắc chắn muốn xóa ngày kỷ niệm '...' khỏi lịch không?"`.
- `AppModals.kt`: `"Firestore"` in Pairing guide -> `"Kết nối hai thiết bị để cùng chung một không gian kỷ niệm riêng tư:"` / `"Link two devices so you both share one private couple space:"`.
- `InLoveViewModel.kt`: `"từ Firestore"`, `"Cloud Firestore"`, `"cơ sở dữ liệu Room"` in sync/reminder toasts -> plain human feedback (`"Đã đồng bộ và đặt ... thông báo cột mốc"`, `"Đã đồng bộ dữ liệu mới nhất"`, `"Đã lưu lời nhắc quà tặng"`).

---

### C. Cross-Platform Alignment (`:shared` & iOS Reported Highlights)

- **`:shared` (`@arch-shared`, board #44)**: Stripped redundant emojis from milestone/monthiversary titles (`AnniversaryBehaviorEngine`), removed hype/exclamation marks (`SmartRuleGiftAdvisor`), replaced third-party store slang in gift archetypes, and unified InLove Pro AI quota wording (`"tối đa 60 lượt/tháng"` in `MonetizationPolicy`).
- **iOS SwiftUI & Widget (`@ios-app`, board #45)**: Added `IosMonetizationBrainBootstrap.swift` and applied `distill` / `quieter` / `clarify` across `InLoveIosTheme.swift`, all SwiftUI views, and `InLoveWidget.swift` (stripped uppercase kickers, sparkles, exclamation marks, emojis, `"..."` placeholders, and `ARCH-05` DB jargon while adding 6 emoji-free daily reflections aligned with Android).

---

## 3. Preserved Test & Architecture Guardrails

- **Design System & Contrast (`ARCH-03`)**: Zero `Color.Gray` / `Color.LightGray` in `ui/designsystem/`, `ui/dialogs/`, `ui/screens/gift/`, `ui/screens/home/`, `ui/screens/calendar/`; all text fields outside `ui/designsystem/` use `inLoveOutlinedTextFieldColors()`.
- **File Budgets (`ARCH-02`)**: All new/decomposed UI files remain `<= 475` lines (strict cap `550`), `GiftScreen.kt` is `<= 550` lines (cap `650`), `InLoveViewModel.kt` is `<= 1,300` lines (cap `1,550`), and `LoveHomeScreen.kt`, `CalendarScreen.kt`, `SettingsScreen.kt`, `AppModals.kt`, `PaywallScreen.kt` stay strictly within their legacy ratchets.
- **Test Contracts Preserved**: `btn_view_vip_proposal` exact CTA text (`GiftScreenTargetsTest`), `PartnerPreferencesEditor` selected-only region chip & `"Partner preferences saved ❤️"` (`GiftBudgetRegionControlsTest`), `PaywallFooter` compliance tags (`PaywallFooterTest`), `NoAiClaimStringsTest`, and `QuoteRotationTest` updated cleanly in tandem for the 12 emoji-free reflections.

---

## 4. Live LDPlayer9 Walkthrough Bugfix Sprint

| # | Live Issue Observed | Root Cause & Fix Applied |
|---|---|---|
| 1 | **P0 — Vertical scroll blocked (`0 px` movement) across all 4 main tabs in `MainActivity` `Scaffold` while `PaywallScreen` (`AppDialog`) scrolled** | `InLoveTopBar` (`CommonComponents.kt`) had `if (state.heightOffsetLimit == 0f) state.heightOffsetLimit = -1f` while `rememberTopAppBarState()` initializes `heightOffsetLimit = -Float.MAX_VALUE`, so `TopAppBarDefaults.exitUntilCollapsedScrollBehavior` on `MainActivity` `Scaffold.nestedScroll` consumed 100% of upward drag deltas (`available.y < 0`) whenever `latest == null`. Made `scrollBehavior: TopAppBarScrollBehavior? = null` optional (forcing `heightOffsetLimit = 0f` when `latest == null`), removed `topBarState`/`topBarScroll`/`nestedScroll` from `MainActivity.kt`, verified `HomeAmbientHearts.kt` uses Compose `rememberInfiniteTransition`, and added Robolectric regression suite `MainTabsScrollTest.kt`. |
| 2 | **Paywall first-fold clipping (`Quyền lợi` 4th row cut off, plans offscreen)** | Compacted `ProBenefitsMatrixSection.kt` (`12.dp/10.dp` card padding, `6.dp` spacing, `16.dp` icons, `12.5.sp` title, single-line `11.sp` subtitles with ellipsis) and tightened `PaywallScreen.kt` hero (`56.dp -> 44.dp`, `21.sp` title, `13.sp` subtitle, `6–10.dp` vertical gaps) so all 6 benefits and the first plan card appear cleanly above the sticky footer. |
| 3 | **Calendar milestone strip right-edge clipping, icon-only unselected filter pills, blank photo boxes, seed emojis** | Switched `SpecialMilestonesHighlightStrip` (`MilestoneEnrichmentBadges.kt`) from horizontal `Row` to wrapping `FlowRow`; updated `CalendarScreen.kt` filter chips to always render text labels in `MinTouchBox` height (replacing `AutoAwesome` with `Star`), replaced blank/offline `MilestoneCard` image boxes with `SubcomposeAsyncImage` + `FavoriteBorder` placeholder, and stripped leading seed emojis + `"🔔"`/`"🔕"` from `AnniversaryDateRow` at UI render time without changing `VietnameseHolidays.kt`. |
| 4 | **Unslop residuals & rectangular focus/press clipping** | Removed `AutoAwesome` icon from `btn_trigger_ai_gift_suggestions` (`GiftScreen.kt`), removed duplicate `%` pill from `PartnerDossierCard.kt` (`InLoveProgressBar` retains `%`), added `.clip(...)` before `.clickable(...)` in `PartnerCard.kt` and `LoveHomeScreen.kt` (`home_my_avatar`, `btn_center_heart`, `love_circle_days_counter`, home banners), and cleaned unpinned decorative emojis from toasts in `MainActivity.kt`, `LoveHomeScreen.kt`, and `InLoveViewModel.kt`. |
| 5 | **Onboarding Step 5 lacked Couple Start Date input mentioned in subtitle** | Added optional `loveStartDateInput` (`InLoveDatePickerField`, tag `onboarding_start_date_field`) inside `OnboardingStepStart` (`OnboardingScreen.kt`) and extended `CoupleProfileController.completeOnboardingProfile` / `InLoveViewModel.completeOnboardingProfile` with `anniversaryDate: String = ""` so Step 5 saves names and start date atomically and populates Home day count immediately. |

---

## 5. Wave 3 Visual Architecture, Typography, Distillation & 60fps Audit

### A. Bundled Typography & Tabular Numerals (`Lora` + `Be Vietnam Pro` + `"tnum"`)
- **OFL Font Families (`ui/theme/Type.kt` & `ui/designsystem/InLoveTokens.kt`)**: Replaced `FontFamily.Serif` / `FontFamily.SansSerif` placeholders with bundled `InLoveFonts.DisplaySerif` (`lora_regular`, `lora_semibold`, `lora_bold`) for editorial/section headings & love-day hero display and `InLoveFonts.BodySans` (`be_vietnam_pro_regular`, `be_vietnam_pro_medium`, `be_vietnam_pro_semibold`, `be_vietnam_pro_bold`) across `Typography` and `InLoveTypography` (`DisplayHero`, `ChapterHeadline`, `EditorialQuote`, `TabularMetric`).
- **Tabular Figures (`"tnum"`)**: Applied `fontFeatureSettings = "tnum"` on numeric counters, countdown chips, percentages, guide step indices (`01`..`03`), and paywall prices (`HomeDaysCounterHero`, `HomeNextMilestoneCard`, `HomeCoupleReflectionsCard`, `PartnerDossierCard`, `NextOccasionReadinessCard`, `InLoveUserGuideDialog`, `PaywallScreen`) so live numbers never jitter horizontally.

### B. Layout Alignment Grid & Header Decluttering (`16.dp` Outer / `32.dp` Inner)
- **Canonical Keyline (`InLoveLayoutGrid`)**: Standardized `ScreenHorizontal = 16.dp`, `CardPadding = 16.dp`, `CardVerticalGap = 12.dp`, and `ItemGap = 8.dp` in `InLoveTokens.kt` and documented in `DESIGN.md`.
- **Left-Edge Alignment & Halo Removal**: Aligned `InLoveTopBar` horizontal padding from `20.dp -> 16.dp` (`CommonComponents.kt`) and `LoveHomeScreen` outer padding to `16.dp`; removed decorative icon circles beside major section headers (`"Bộ Đếm Ngày Yêu"` in `LoveDaysCalculatorWidget.kt`, `"Ngày Kỷ Niệm Của Hai Bạn"` in `CalendarScreen.kt`, `"Ý Tưởng Chuẩn Bị Quà"` in `GiftScreen.kt`, and `"Sở thích"` in `PairingScreen.kt`), shifting titles flush left in `InLoveFonts.DisplaySerif`.

### C. 3-Tier Subtext Distillation & Shared Domain Consumption
- **`PartnerDossierCard.kt`**: Collapsed to a single-row title + tabular percentage + slim 7dp progress bar + action button (removed duplicate label row, instructional subtitle, and `topHint` paragraph).
- **`NextOccasionReadinessCard.kt`**: Showcases `Lora` occasion headline + formatted date & countdown pill (`"tnum"`), removing `summary` and pink `firstStep` helper card when `daysRemaining > 0`.
- **`AiGiftConciergeSection.kt` & `GiftScreen.kt`**: Removed redundant tone-row sub-label (`"Giọng điệu lời chúc & gu quà"`) and tightened `mutual_interests_gift_card` empty prompt to one calm sentence.
- **`MemoryPrivacyNotice.kt` & `SettingsScreen.kt`**: Added `initiallyExpanded: Boolean = true` (preserving all `MemoryPrivacyNoticeTest` contracts) with `"Xem chi tiết"` / `"Show details"` `AnimatedVisibility` toggle (`btn_toggle_privacy_details`); `SettingsScreen` mounts `MemoryPrivacyNotice(initiallyExpanded = false)` with `16.dp` card padding.
- **`PairingScreen.kt`**: Consumes `viewModel.onlineSyncPhase` (`OnlineSyncPhase.statusLabel(...)` banner `pairing_sync_phase_banner`), normalizes invite codes via `:shared`'s `CoupleCodeParser.extractCode(...)`, renders emoji-free relationship pills and `AVAILABLE_INTERESTS` chip labels directly, and upgrades `Color.Gray` / `AutoAwesome` to `TextSubtlePlum` and `PhotoLibrary`.
- **`PaywallScreen.kt`**: Consumes `:shared`'s `ProTierCatalog.planFor(...)` for canonical badges and taglines (Yearly anchor badge `"Tiết kiệm 57% · 3 ngày miễn phí"` / `"Save 57% · 3-Day Free Trial"`) paired with `Lora` hero display while preserving live Google Play Billing `formattedPrice` authority and `PaywallFooterTest` contracts.

### D. 60fps Animation Discipline, Home Decomposition, Guide Chapterization & Lazy Scroll
- **`LoveHomeScreen.kt` Split (`690 -> <= 550` lines)**: Extracted `HomeDaysCounterHero.kt`, `HomeCoupleProfileCard.kt`, `HomeCoupleReflectionsCard.kt`, `HomeStatusBanners.kt`, and `HomeNextMilestoneCard.kt` under `ui/screens/home/`. Counter aura sweep and couple-card pulse/ripple scale/alpha states are read strictly inside `Canvas` `DrawScope` and `Modifier.graphicsLayer { ... }` (zero per-frame recomposition).
- **4-Chapter Guide Extraction (`AppModals.kt` -> `InLoveUserGuideDialog.kt`)**: Extracted `InLoveUserGuideDialog` driven by `:shared`'s `InLoveGuideCatalog` (`4` chapters, tabular `01`..`03` step numbers, `guide_quick_action` deep-link callbacks wired through `MainActivity.kt`), shrinking `AppModals.kt` to `644` lines (`<= 650`).
- **List Performance (`contentType`)**: Added typed `contentType` keys on `LazyColumn` slots in `CalendarScreen.kt` and `GiftScreen.kt`, and updated `MainTabsScrollTest.kt` for Wave 3 visual & interaction verification (`wave3_01`..`wave3_08`).
