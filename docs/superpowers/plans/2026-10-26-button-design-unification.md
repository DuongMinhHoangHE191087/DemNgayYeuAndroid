# Button Design Unification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the 126 ad-hoc `Button`/`OutlinedButton`/`TextButton` call sites in 18 UI files (plus 1 in `CommonComponents.kt`) with exactly two shared composables, `PrimaryGradientButton` (unchanged) and a new `SecondaryButton`, and restyle bare text links to one convention.

**Architecture:** Task 1 adds `SecondaryButton` and one non-composable `Modifier.disabledLook` helper next to `PrimaryGradientButton` in `CommonComponents.kt`. Tasks 2-4 swap call sites batch by batch, one rule per site, as decided in the tables below. The batches touch disjoint files, so they can be edited in parallel after Task 1. Task 5 runs the full suite and lint and produces the Paywall before/after pair.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Robolectric + Roborazzi 1.59.0 (`captureRoboImage`), Gradle (`:app:testDebugUnitTest`, `:app:recordRoborazziDebug`, `:app:lintDebug`).

**Spec:** `docs/superpowers/specs/2026-10-26-button-design-unification-design.md`

## Global Constraints

- No new color tokens. Both composables reuse the existing `Primary`, `CardBorder`, `TextDarkPlum`, and M3's built-in `colorScheme.error`/`colorScheme.onError`.
- No change to `PrimaryGradientButton`'s existing public signature, and no change to its body (spec section 1: "adopt as-is, zero code changes").
- Exactly two shared button composables (`PrimaryGradientButton`, `SecondaryButton`). There is no third composable for text links. `Modifier.disabledLook` is a Modifier extension, not a button composable.
- `spikes/ui-drift/**` is out of scope and is never edited.
- `OnboardingScreen.kt`: styling-only. Do not change any string, string resource, `strings.*` reference, or copy. The onboarding-audit workstream owns its text.
- No commit, push, or PR. Work stays uncommitted on `main`.
- `SecondaryButton` matches `PrimaryGradientButton`: 50dp stadium (`RoundedCornerShape(50.dp)`), 20dp horizontal / 13dp vertical padding, 18dp icon + 6dp spacer, Bold 14sp text.
- `SecondaryButton` states: `isDestructive=false` → `CardBorder` 1.2dp border, `Color.White` fill, `TextDarkPlum` text. `true/false` → `colorScheme.error` border + text, white fill. `true/true` → `colorScheme.error` fill, `colorScheme.onError` text. `filled` is a no-op unless `isDestructive`.
- Text links: `TextButton` + `ButtonDefaults.textButtonColors(contentColor = Primary)` + `Text(..., fontSize = 13.sp, fontWeight = FontWeight.Medium)`.
- Lint sits at 0 errors / 128 warnings with zero headroom (gate: <=128). No change may add a warning.
- Gradle runs in this repo must not overlap: parallel batches edit in parallel but take turns on `./gradlew`. Never kill a running Gradle task without deleting `app/build/kotlin/<task>` afterwards.

## Call-site conventions (apply to every site in Tasks 2-4)

- **C1, rule application:** apply the spec's rules in order 1→5; the first match wins. A dialog's Cancel/Close that is a `TextButton` today is rule 2 (`SecondaryButton`), not rule 5. Rule 5 covers only links that are not a dialog's dismiss/confirm action.
- **C2, sizing:** every `PrimaryGradientButton`/`SecondaryButton` call gets `.heightIn(min = MinTouchBox)` in its modifier. Reason: the spec assumes the 13dp padding clears 48dp, but under `UI_SCALE = 0.85` the intrinsic height is about 50 layout dp ≈ 42.5 real dp, below the floor. Drop `.height(x)`/`.size(x)`, `shape`, `colors`, `contentPadding`, `elevation`, and `border`. Keep layout modifiers (`weight`, `fillMaxWidth`, `align`, `padding`).
- **C3, test tags:** pass an existing tag through the `testTag =` parameter, never as `Modifier.testTag(...)` in `modifier`. A site without a tag keeps the default tag.
- **C4, labels and icons:** `text =` is the exact string expression the site uses today (emojis included). If the site shows an `ImageVector`, pass it as `icon =`. A site whose content is richer than one icon plus one `Text` (two lines, a badge, a counter) is **stopped and reported**, not flattened.
- **C5, disabled:** neither composable has `enabled`. For a site with `enabled = <expr>`, hoist `val <name>Enabled = <expr>`, pass `modifier = ….disabledLook(<name>Enabled)`, and wrap the click as `onClick = { if (<name>Enabled) <old onClick body> }`.
- **C6, loading** (marked `loading` in the tables): `Box(<site layout modifiers>, contentAlignment = Alignment.Center) { PrimaryGradientButton(text = if (loading) "" else <label>, icon = if (loading) null else <icon>, modifier = Modifier.fillMaxWidth().heightIn(min = MinTouchBox).disabledLook(<enabled>), testTag = <tag>, onClick = { if (<enabled>) … }); if (loading) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp)) }`. The site's `<enabled>` expression must already include the loading flag; add it if it doesn't. For content-hugging sites (no `fillMaxWidth` today), drop `.fillMaxWidth()` from the inner button; a width change while loading is accepted.
- **C7, lambda labels:** `return@Button`/`return@OutlinedButton`/`return@TextButton` inside a moved `onClick` become `return@PrimaryGradientButton`/`return@SecondaryButton` (known: AuthScreen ~524/~584, AppPinLockScreen ~315).
- **C8, links (rule 5):** keep `TextButton`, its `modifier`/tag/`enabled`, and any icon or spinner. Set `colors = ButtonDefaults.textButtonColors(contentColor = Primary)`. The `Text` gets `fontSize = 13.sp, fontWeight = FontWeight.Medium` and no `color =`. An icon inside drops its `tint` so it inherits the content color.
- **C9, cleanup:** remove imports made unused (`ButtonDefaults`, `OutlinedButton`, `Button`, `RoundedCornerShape`, …) so no new warning appears.
- Line numbers are from 2026-10-26 and other sessions may shift them. Locate each site by its tag or label; the line is only a hint.

## Review Focus

1. **Tapping a disabled CTA** (Paywall with `canPurchase=false`, blank pairing search, invalid PIN): nothing fires and the node reports disabled. Pinned in Task 1 (`disabledLook_*`) and Task 3 (`paywallCta_disabled_clickDoesNothing`).
2. **Double tap while loading** (login, register, forgot-password send, search, password update): the second tap is ignored. C6 requires the loading flag inside the enabled expression. Pinned in Task 4 (`pairingTab_searchDisabledWhileQueryBlank` stays green) plus a per-site check step in Tasks 3 and 4.
3. **Long Vietnamese labels in weighted rows on a 320dp screen** ("Đồng ý Set Love ❤️", "Lưu & Tính Ngày"): both buttons stay visible and at least 48dp tall, wrapping rather than clipping. Pinned in Task 1 (`secondaryButton_longLabelsInNarrowRow_bothVisible`).
4. **Touch target after dropping explicit heights** (Onboarding 56dp, Auth 50dp, Paywall): at least 48 real dp. Pinned in Task 1 (`secondaryButton_meetsTouchTarget`) and by existing `assertHeightIsAtLeast(48.dp)` tests (EditCoupleDialogTest, EditProfileDialogTargetsTest, PaywallFooterTest).
5. **Icon-only search button** (Dialogs ~1757): it must keep its "Search"/"Tìm" accessibility label after moving to `PrimaryGradientButton`, whose icon has `contentDescription = null`. Pinned in Task 4 (`pairingTab_searchHasContentDescription`).

---

### Task 1: `SecondaryButton` + `Modifier.disabledLook`

**Files:**
- Modify: `app/src/main/java/com/example/ui/components/CommonComponents.kt` (insert after `PrimaryGradientButton`, which ends at line 465 before `animateDialogEntrance`)
- Create: `app/src/test/java/com/example/ui/components/SecondaryButtonTest.kt`
- Golden: `app/src/test/screenshots/secondary_button_states.png` (new)

**Interfaces:**
- Consumes: `Modifier.pressClickable(onClick, enabled = true, role = Role.Button, onClickLabel)` (`PressScale.kt:31`); `MinTouchBox` (`Theme.kt:114`); `CardBorder`, `TextDarkPlum` (`Color.kt:61-62`).
- Produces:
  - `@Composable fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, isDestructive: Boolean = false, filled: Boolean = false, testTag: String = "secondary_btn")`
  - `fun Modifier.disabledLook(enabled: Boolean): Modifier`. Enabled returns `this`. Disabled returns `this.alpha(0.5f).semantics { disabled() }`.

- [ ] **Step 1: Write the failing tests** in `SecondaryButtonTest.kt`, using PaywallFooterTest's runner setup (`@RunWith(RobolectricTestRunner::class)`, `@GraphicsMode(GraphicsMode.Mode.NATIVE)`, `@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])`, content inside `MyApplicationTheme { }` so `AppDensity` applies):
  - `secondaryButton_click_firesOnce`: performClick on tag `"sb"` → counter == 1.
  - `secondaryButton_defaultTag_isSecondaryBtn`: no `testTag` arg → `onNodeWithTag("secondary_btn").assertExists()`.
  - `secondaryButton_meetsTouchTarget`: `modifier = Modifier.heightIn(min = MinTouchBox)` → `assertHeightIsAtLeast(48.dp)`.
  - `disabledLook_false_marksNodeDisabled`: `SecondaryButton(modifier = Modifier.disabledLook(false), …)` → `assertIsNotEnabled()`; with `true` → `assertIsEnabled()`.
  - `secondaryButton_longLabelsInNarrowRow_bothVisible`: `Row(Modifier.width(320.dp))` with two `SecondaryButton(modifier = Modifier.weight(1f).heightIn(min = MinTouchBox))` labelled `"Đồng ý Set Love ❤️"` and `"Lưu & Tính Ngày"` → each `assertIsDisplayed()` and `assertHeightIsAtLeast(48.dp)`.
  - `secondaryButton_states_screenshot`: a `Column` holding `PrimaryGradientButton("Lưu", …)`, `SecondaryButton("Hủy")`, `SecondaryButton("Xóa", isDestructive = true)`, `SecondaryButton("Xóa vĩnh viễn", isDestructive = true, filled = true)`, `SecondaryButton("Filled no-op", filled = true)`, plus `SecondaryButton("Có icon", icon = Icons.Default.Favorite)` → `onRoot().captureRoboImage(filePath = "src/test/screenshots/secondary_button_states.png")`.

- [ ] **Step 2: Run, expect a compile failure** (`SecondaryButton`/`disabledLook` unresolved)

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.ui.components.SecondaryButtonTest"`
Expected: FAIL, `Unresolved reference: SecondaryButton`.

- [ ] **Step 3: Implement** both symbols in `CommonComponents.kt`. `SecondaryButton` mirrors `PrimaryGradientButton`'s chain with no shadow: `modifier.clip(RoundedCornerShape(50.dp)).background(fill).then(if (border) Modifier.border(1.2.dp, borderColor, RoundedCornerShape(50.dp)) else Modifier).pressClickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 13.dp).testTag(testTag)`, with the same `Row`/`Icon(18.dp)`/`Spacer(6.dp)`/`Text(Bold, 14.sp)` content tinted with the state's content color. Use `val solid = isDestructive && filled`. `PrimaryGradientButton` is not touched.

- [ ] **Step 4: Record the golden, then run green**

Run: `./gradlew :app:recordRoborazziDebug --tests "com.example.ui.components.SecondaryButtonTest"` then `./gradlew :app:testDebugUnitTest --tests "com.example.ui.components.SecondaryButtonTest"`
Expected: 6 tests, 0 failures. Open `secondary_button_states.png` and check it: neutral = pink-grey outline with dark plum text, destructive = red outline, filled = solid red with white text, and the `filled`-only row is identical to the neutral row.

- [ ] **Step 5: Build gate** (Tasks 2-4 must not start until this passes)

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

---

### Task 2: Batch 1 — low-risk screens (20 sites)

**Files (modify):** `ui/screens/AppPinLockScreen.kt`, `ui/screens/OnboardingScreen.kt`, `ui/components/VerifyInviteDialog.kt`, `ui/components/Material3DatePicker.kt`, `ui/components/MilestoneBadgeTracker.kt`, `ui/components/LoveDaysCalculatorWidget.kt` (all under `app/src/main/java/com/example/`; resolve the exact folder with Glob).
**Tests:** `DatePickerFlowTest`, `MyProfileBirthdayTest`, `BadgeRewardNoteTest`, `ManualDateInputTest`, `SecondaryButtonTest`.

**Interfaces:** Consumes Task 1's `SecondaryButton`, `Modifier.disabledLook`, and the unchanged `PrimaryGradientButton(text, onClick, modifier, icon, testTag)`. Produces nothing for other tasks.

| File:line | Now | Label / tag | Rule → target | Extras |
|---|---|---|---|---|
| AppPinLock:313 | Button, FB blue | "Mở khóa bằng Facebook" / `btn_pin_use_facebook` | 2 → Secondary | C5 `!isBusy`; C7; brand blue dropped |
| AppPinLock:338 | TextButton | "Quên PIN?…" / `btn_pin_use_password` | 5 → link | |
| AppPinLock:350 | TextButton grey 12sp | "Đăng xuất tài khoản khác" | 5 → link | stays a link (not a dialog dismiss) |
| AppPinLock:419 | Outlined | "Đóng" | 2 → Secondary | |
| AppPinLock:426 | Button | "Mở khóa" | 1 → Primary | |
| Onboarding:195 | TextButton | `strings.onboardingBtnSkip` | 5 → link | only color/size/weight change; the string ref is untouched |
| Onboarding:270 | transparent Button + gradient Box, 56dp | `strings.onboardingBtnStart`/`Next` | 1 → Primary | drop 56dp per C2; the icon moves from after the text to before it (component layout); strings untouched |
| VerifyInvite:363 | Outlined red | "Decline/Từ chối" / `btn_verify_reject` | 2 → Secondary | declining an invite is not destructive |
| VerifyInvite:374 | Button, weight 1.3f | "Accept/Đồng ý Set Love ❤️" / `btn_verify_accept` | 1 → Primary | icon Favorite |
| DatePicker:1014 | Outlined | `resolvedDismiss` / `date_picker_dismiss_btn` | 2 → Secondary | |
| DatePicker:1025 | Button, weight 1.3f | confirm / `date_picker_confirm_btn` | 1 → Primary | C5 `DatePickerRules.isSelectable(...)`; keep icon |
| BadgeTracker:596 | Button full width | "Mở Bộ Sưu Tập Huy Hiệu…" / `open_badge_tracker_button` | 1 → Primary | |
| BadgeTracker:1374 | Button align End | "Lưu ghi chú/Save Note" | 1 → Primary | |
| BadgeTracker:1418 | Button | "Bão Tim 🎉" / `celebrate_badge_button` | 1 → Primary | |
| BadgeTracker:1445 | Outlined | "Chia Sẻ/Share" / `share_badge_button` | 2 → Secondary | |
| BadgeTracker:1487 | Button full width | "Đang Đếm Ngược…" | 1 → Primary | C4 stop if it has more than one Text |
| LoveDaysCalc:494 | Button | "Đồng bộ/Sync" / `btn_sync_days_to_counter` | 1 → Primary | icon Sync |
| LoveDaysCalc:524 | Outlined | "Nhập ngày/Edit Date" / `btn_manual_anniversary_input` | 2 → Secondary | icon DateRange |
| LoveDaysCalc:766 | TextButton (dialog) | "Hủy/Cancel" | 2 → Secondary | C1 |
| LoveDaysCalc:770 | Button (dialog) | "Lưu & Tính Ngày" | 1 → Primary | |

- [ ] **Step 1:** Swap every row above per its rule and the conventions C1-C9. In `OnboardingScreen.kt` the diff must contain no change to a string literal or a `strings.`/`R.string` reference. Check with `git diff -U0 -- '*OnboardingScreen.kt' | grep '^[-+]' | grep -E 'strings\.|R\.string|"'`: every hit must appear unchanged on both the `-` and `+` sides.
- [ ] **Step 2:** `grep -rnE "\b(Button|OutlinedButton|TextButton)\(" <the 6 files>` → only the rule-5 `TextButton`s (AppPinLock 338/350, Onboarding 195) remain.
- [ ] **Step 3: Run batch tests**

Run: `./gradlew :app:testDebugUnitTest --tests "*DatePickerFlowTest" --tests "*MyProfileBirthdayTest" --tests "*BadgeRewardNoteTest" --tests "*ManualDateInputTest" --tests "*SecondaryButtonTest"`
Expected: 0 failures. If a test asserted the old color/shape or found a node by M3 `Button` semantics, update the assertion to the new styling (spec Testing bullet 2); never weaken an enabled/disabled or click assertion.

---

### Task 3: Batch 2 — feature screens (53 sites)

**Files (modify):** `AuthScreen.kt`, `PairingScreen.kt`, `GiftScreen.kt`, `PaywallScreen.kt`, `AnniversaryMemoriesWidget.kt`, `LoveHomeScreen.kt`, `MemoriesGridScreen.kt`.
**Tests:** `PaywallFooterTest` (+ golden `paywall_footer.png`), `GiftBudgetRegionControlsTest`, `GiftScreenTargetsTest`, `QuoteRotationTest`, `HomePartnerBannerTest`, `MemoryFilterTest`.

**Interfaces:** Same as Task 2.

| File:line | Now | Label / tag | Rule → target | Extras |
|---|---|---|---|---|
| Auth:225 | TextButton + ArrowBack | "Quay lại chế độ Khách (Offline)" | 2 → Secondary | icon ArrowBack |
| Auth:505 | TextButton | "Quên mật khẩu?" / `btn_forgot_password` | 5 → link | |
| Auth:520 | Button h50 | "Đăng Nhập" / `btn_submit_login` | 1 → Primary | C5 + C6 (`isLoggingIn`); C7; icon Favorite |
| Auth:582 | Outlined FB | "Tiếp tục với Facebook" / `btn_login_facebook` | 2 → Secondary | C5 `!isLoggingIn`; C7; brand blue dropped |
| Auth:632, 654 | Button (debug testers A/B) | tester labels | 2 → Secondary | C5 `!isLoggingIn` |
| Auth:978 | TextButton (dialog confirm) | "Giữ cho tài khoản mới" / `btn_guest_data_keep` | 1 → Primary | |
| Auth:984 | TextButton (dialog dismiss) | "Bắt đầu mới" / `btn_guest_data_fresh` | 2 → Secondary | guest data is kept, so not destructive |
| Auth:992 | Button | "Đăng Ký Tài Khoản" | 1 → Primary | C5 `canRegister` + C6 (`isRegistering`) |
| Auth:1191 | Outlined | "Close/Đóng" | 2 → Secondary | |
| Auth:1194 | Button | "Send link/Gửi liên kết" / `btn_forgot_send` | 1 → Primary | C5 + C6 (`isSending`) |
| Pairing:313 | Outlined | reject invite / `reject_invite_button` | 2 → Secondary | |
| Pairing:323 | Button | accept / `accept_invite_button` | 1 → Primary | |
| Pairing:590 | Button | "Nhịp tim" | 1 → Primary | |
| Pairing:605 | Outlined | "Memories/Kỷ niệm" | 2 → Secondary | |
| Pairing:628 | TextButton red, full width | "End Set Love/Hủy Set Love" / `request_breakup_button` | 3 → Secondary(isDestructive) | |
| Pairing:655 | Outlined | "Rút lại/Từ chối" / `reject_breakup_button` | 2 → Secondary | |
| Pairing:660 | Button | "Hủy ngay/Xác nhận" / `confirm_breakup_button` | 4 → Secondary(isDestructive, filled) | |
| Pairing:681 | Button (dialog confirm) | "Send Request/Gửi yêu cầu" | 4 → Secondary(isDestructive, filled) | breakup request |
| Pairing:683 | Outlined (dialog dismiss) | "Cancel/Hủy bỏ" | 2 → Secondary | |
| Pairing:733 | Outlined | "Cancel invite/Hủy lời mời" / `cancel_sent_invite_button` | 2 → Secondary | withdrawing an invite is reversible |
| Pairing:840 | Button | "Tìm/Search" / `btn_perform_search` | 1 → Primary | C6 (`isSearching`); add `!isSearching` to an enabled guard (C5) |
| Pairing:997 | Button | "Send invite" / `send_set_love_to_target_button` | 1 → Primary | |
| Gift:170 | TextButton (dialog confirm) | "Share/Chia sẻ" / `btn_confirm_share_gift` | 1 → Primary | |
| Gift:183 | TextButton (dialog dismiss) | "Cancel" | 2 → Secondary | |
| Gift:346 | Button | "More ideas/Gợi ý thêm" / `btn_trigger_ai_gift_suggestions` | 1 → Primary | |
| Gift:1295 | TextButton toggle | "Likes & budget" / `btn_toggle_partner_preferences` | 5 → link | |
| Gift:1371 | Button | "Save/Lưu" / `btn_save_partner_preferences` | 1 → Primary | C5 `profileExists` |
| Paywall:525 | transparent Button + gradient Box, 16dp shape | `ctaText` / `paywall_cta` | 1 → Primary, `fillMaxWidth` | C5 `canPurchase`; 16dp rect → 50dp stadium (before/after, see Step 1) |
| Paywall:565 | TextButton | retry prices / `paywall_retry_prices` | 5 → link | |
| Paywall:587 | TextButton + spinner | restore | 5 → link | keep the `isRestoring` spinner |
| Paywall:608 | TextButton + icon | manage subscription | 5 → link | |
| Anniv:471 | TextButton | "Xem tất cả" / `btn_view_full_memories` | 5 → link | |
| Anniv:833 | Button, empty-state | "Chọn ảnh/Choose photos" | 1 → Primary | icon AddPhotoAlternate |
| Anniv:1112 | Button | "Lưu Vào Kỷ Niệm ❤️" / `btn_confirm_save_anniversary_photo` | 1 → Primary | |
| Anniv:1298 | Outlined (delete confirm) | "Cancel" | 2 → Secondary | |
| Anniv:1306 | Button red | "Xác nhận xóa" | 4 → Secondary(isDestructive, filled) | |
| Anniv:1321 | Outlined red | "Xóa" | 3 → Secondary(isDestructive) | |
| Anniv:1331 | Button | "Đóng/Close" | 2 → Secondary | |
| LoveHome:1085 | Button | `btnGiftIdeas` / `btn_next_anniversary_gifts` | 1 → Primary | |
| LoveHome:1113 | Button | `btnAnniversaryCalendar` / `btn_next_anniversary_calendar` | 2 → Secondary | |
| LoveHome:1144 | Button | celebrate / `btn_celebrate_milestone_hearts` | 1 → Primary | |
| LoveHome:1308 | Button | shuffle / `btn_shuffle_quote` | 2 → Secondary | |
| LoveHome:1339 | Button | send / `btn_send_quote` | 1 → Primary | |
| MemGrid:998 | Outlined | "Cancel" | 2 → Secondary | C5 `!isSaving` |
| MemGrid:1016 | Button, weight 1.2f | "Save/Lưu" / `btn_save_memory_submit` | 1 → Primary | C5 `canSave` (has no spinner; none added); icon Favorite |
| MemGrid:1192 | Cancel (edit dialog) | "Cancel" | 2 → Secondary | |
| MemGrid:1195 | Button | "Save Changes" | 1 → Primary | |
| MemGrid:1497 | Cancel (delete confirm) | "Cancel" | 2 → Secondary | |
| MemGrid:1505 | Button | "Confirm Delete" | 4 → Secondary(isDestructive, filled) | |
| MemGrid:1527 | Outlined red, weight 1f | "Delete/Xóa" | 3 → Secondary(isDestructive) | icon Delete |
| MemGrid:1539 | Button purple, weight 1f | "Edit/Sửa" | 2 → Secondary | icon Edit; non-destructive alternative |
| MemGrid:1552 | Button pink, weight 1f | "Got It/`strings.btnGotIt`" | 1 → Primary | the only button a partner sees |

- [ ] **Step 1: Capture the Paywall "before" image first, before any edit to `PaywallScreen.kt`**

Run: `./gradlew :app:recordRoborazziDebug --tests "*PaywallFooterTest"`, then copy `app/src/test/screenshots/paywall_footer.png` to `app/build/reports/button-unification/paywall_footer_before.png` (create the folder; `build/` keeps it out of git).
Expected: the copied file exists and shows the 16dp-rounded CTA.

- [ ] **Step 2: Add the Review Focus #1 test** to `PaywallFooterTest`: `paywallCta_disabled_clickDoesNothing`. Render with `canPurchase = false` and a counting `onPurchase`, `performClick()` on `paywall_cta`, then `assertEquals(0, purchases)` and `assertIsNotEnabled()`. Run it (`--tests "*PaywallFooterTest"`). Expected: PASS on today's code (an M3 Button with `enabled=false`). It is a guard that must stay green after the swap.
- [ ] **Step 3:** Swap every row above per its rule and C1-C9. For each C6 row, read the site's enabled expression and confirm it contains the loading flag (`isLoggingIn`, `isRegistering`, `isSending`, `isSearching`). Add the flag where it is missing (Review Focus #2).
- [ ] **Step 4:** `grep -rnE "\b(Button|OutlinedButton|TextButton)\(" <the 7 files>` → only the rule-5 rows remain (Auth 505, Gift 1295, Paywall 565/587/608, Anniv 471).
- [ ] **Step 5: Re-record the Paywall golden and copy the "after" image**

Run: `./gradlew :app:recordRoborazziDebug --tests "*PaywallFooterTest"`, then copy `paywall_footer.png` to `app/build/reports/button-unification/paywall_footer_after.png`.
Expected: the only visible diff against `_before` is the CTA shape (stadium), its gradient, and the restyled links.

- [ ] **Step 6: Run batch tests**

Run: `./gradlew :app:testDebugUnitTest --tests "*PaywallFooterTest" --tests "*GiftBudgetRegionControlsTest" --tests "*GiftScreenTargetsTest" --tests "*QuoteRotationTest" --tests "*HomePartnerBannerTest" --tests "*MemoryFilterTest"`
Expected: 0 failures, with the same update rule as Task 2 Step 3.

---

### Task 4: Batch 3 — shared dialogs (53 sites)

**Files (modify):** `Dialogs.kt`, `AppModals.kt`, `AccountSecurityDialogs.kt`, `EditMyProfileDialog.kt`, `SettingsScreen.kt`, `CommonComponents.kt` (line 214 only. Do not touch Task 1's code or `PrimaryGradientButton`).
**Tests:** `EditCoupleDialogTest`, `ChecklistTest`, `GiftReminderCrudTest`, `EditProfileDialogTargetsTest`, `MyProfileBirthdayTest`, `AddMilestoneExamplesTest`, `AppWindowsTest`, `PartnerCardTest`, `InLoveTopBarTest` (+ goldens `topbar_expanded.png`, `topbar_collapsed.png`).

**Interfaces:** Same as Task 2.

Spec note: the spec asks to "re-verify `CommonComponents.kt`'s own existing (non-spike) `PrimaryGradientButton` caller". No such caller exists; the only callers are in `spikes/`. The 53rd Batch 3 site is the "See all" `TextButton` at `CommonComponents.kt:214`.

| File:line | Now | Label / tag | Rule → target | Extras |
|---|---|---|---|---|
| Dialogs:260 / 269 | Save / Cancel | `btn_save_reminder` / – | 1 / 2 | |
| Dialogs:327 / 352 | Create / Cancel | `btn_submit_milestone` / – | 1 / 2 | |
| Dialogs:557 / 566 | Add / Cancel | `btn_save_checklist_task` / – | 1 / 2 | |
| Dialogs:748 | Button | "Got it" | 1 → Primary | only action |
| Dialogs:912 | Button full width | "Got it" | 1 → Primary | only action |
| Dialogs:1332 / 1349 | Camera / Gallery | `btn_partner_camera` / `btn_partner_gallery` | 2 / 2 | |
| Dialogs:1366 | Delete photo | `btn_partner_remove` | 3 → Secondary(isDestructive) | |
| Dialogs:1424 | Save | `btn_save_couple_offline` | 1 → Primary | |
| Dialogs:1504 / 1511 | Decline / "Accept ❤️" | – | 2 / 1 | |
| Dialogs:1543 | Cancel request | – | 2 → Secondary | |
| Dialogs:1757 | Button icon-only, `size(MinTouchBox)` | Search icon / `dialog_btn_search` | 1 → Primary(text = "", icon = Search) | C5 `searchQuery.isNotBlank() && !isSearching`; C6 spinner; replace `size(...)` with `heightIn(min = MinTouchBox)` (renders about 64dp wide); add `semantics { contentDescription = if (isEnglish) "Search" else "Tìm" }` to the modifier |
| Dialogs:1818 | Button | "Send request ❤️" | 1 → Primary | |
| Dialogs:1843 | Close | `btn_close_pairing_tab` | 2 → Secondary | |
| Dialogs:1936 | Button 0xFFE53935 | Delete / `btn_confirm_delete` | 4 → Secondary(isDestructive, filled) | |
| Dialogs:1957 | Cancel | `btn_cancel_delete` | 2 → Secondary | |
| Dialogs:2239 / 2263 | Set Alarm / Close | `btn_confirm_schedule_alarm` / `btn_cancel_schedule_alarm` | 1 / 2 | |
| Dialogs:2378 / 2390 | Save / Cancel | – | 1 / 2 | |
| Dialogs:2634 / 2645 | Save / Cancel | `btn_save_anniversary_date` / `btn_cancel_anniversary_date` | 1 / 2 | |
| Dialogs:2780 / 2791 | Save / Cancel | `btn_save_gift_reminder` / – | 1 / 2 | |
| AppModals:202 | TextButton (dialog dismiss) | `btnCancel` | 2 → Secondary | C1 |
| AppModals:215 | Button | `btnConfirm` / `btn_confirm_language` | 1 → Primary | |
| AppModals:575 | Button | `btnGotIt` / `btn_close_guide` | 1 → Primary | |
| AppModals:827 | Button full width | "Tải ảnh từ thư viện máy" / `btn_upload_wallpaper_device` | 2 → Secondary | icon AddPhotoAlternate; the Apply button below is the modal's one primary |
| AppModals:932 | Button full width | `btnApplyWallpaper` / `btn_apply_wallpaper` | 1 → Primary | |
| AccSec:214 / 222 | Hủy / "Cập Nhật" | – | 2 / 1 | 222: C5 + C6 (`isSubmitting`) |
| AccSec:332 / 340 | Hủy / "Lưu PIN" | – | 2 / 1 | 340: C5 `isValid` |
| AccSec:449 | Button | "Đóng" | 1 → Primary | only action |
| EditProfile:371 / 388 | Camera / Gallery | `btn_photo_camera` / `btn_photo_library` | 2 / 2 | |
| EditProfile:405 | Delete photo | `btn_photo_remove` | 3 → Secondary(isDestructive) | |
| EditProfile:556 | Save | `btn_save_my_profile` | 1 → Primary | |
| Settings:204 / 207 | TextButton legal links | privacy / terms | 5 / 5 | |
| Settings:482 | Button | "Sign In to Sync" | 1 → Primary | |
| Settings:595 | Outlined | "Đăng xuất" / `btn_logout` | 3 → Secondary(isDestructive) | |
| Settings:618 | Outlined | "Xóa tài khoản" / `btn_delete_account` | 3 → Secondary(isDestructive) | |
| Settings:688 / 699 | "Sign Out" / Cancel (dialog) | – | 4 / 2 | |
| Settings:736 | TextButton | account-deletion web link | 5 → link | |
| Settings:747 / 758 | "Xóa Vĩnh Viễn" / Cancel | – | 4 / 2 | |
| CommonComponents:214 | TextButton Primary 13sp Bold | "See all" / `topbar_see_all` | 5 → link | Bold → Medium |

(Settings:541 and `MemoryPhotoReorderStrip.kt` matches are comments, not sites.)

- [ ] **Step 1: Add the Review Focus #5 test** to `EditCoupleDialogTest`: `pairingTab_searchHasContentDescription`. Call `launch()` and `openPairingTab()`, then `onNodeWithTag("dialog_btn_search").assert(hasContentDescription("Search").or(hasContentDescription("Tìm")))`. Run it (`--tests "*EditCoupleDialogTest"`). Expected: PASS today. It must stay green after the swap.
- [ ] **Step 2:** Swap every row above per its rule and C1-C9 (Review Focus #2: the AccSec:222 and Dialogs:1757 enabled expressions include the loading flag).
- [ ] **Step 3:** `grep -rnE "\b(Button|OutlinedButton|TextButton)\(" <the 5 files + CommonComponents.kt>` → only the rule-5 rows remain (Settings 204/207/736, CommonComponents 214) plus no hit inside `PrimaryGradientButton`/`SecondaryButton`.
- [ ] **Step 4: Re-record the top-bar goldens**

Run: `./gradlew :app:recordRoborazziDebug --tests "*InLoveTopBarTest"`
Expected: `topbar_expanded.png`/`topbar_collapsed.png` differ only in the "See all" weight (Bold → Medium).

- [ ] **Step 5: Run batch tests**

Run: `./gradlew :app:testDebugUnitTest --tests "*EditCoupleDialogTest" --tests "*ChecklistTest" --tests "*GiftReminderCrudTest" --tests "*EditProfileDialogTargetsTest" --tests "*MyProfileBirthdayTest" --tests "*AddMilestoneExamplesTest" --tests "*AppWindowsTest" --tests "*PartnerCardTest" --tests "*InLoveTopBarTest"`
Expected: 0 failures, with the same update rule as Task 2 Step 3. `pairingTab_searchDisabledWhileQueryBlank`, `pairingTab_searchEnabledAfterTyping`, and `pairingTab_targetsMeetMinTouchBox` must pass unmodified.

---

### Task 5: Final verification

**Files:** none modified (goldens only if Step 1 shows an intended diff not recorded yet).

**Interfaces:** Consumes Tasks 1-4.

- [ ] **Step 1: Whole-repo leftover scan**

Run: `grep -rnE "\b(Button|OutlinedButton|ElevatedButton|FilledTonalButton)\(" app/src/main/java/com/example/ui`
Expected: no hits in the 18 rollout files. List any hit in another file in the report without changing it (out of scope).

- [ ] **Step 2: Full suite**

Run: `./gradlew test`
Expected: `BUILD SUCCESSFUL`, 0 failures. Record the suite and test counts. A failing screenshot test means a golden needs re-recording only if the diff is an intended shape/color change. Otherwise it is a bug.

- [ ] **Step 3: Lint**

Run: `./gradlew :app:lintDebug`
Expected: 0 errors, warnings <= 128 (zero headroom). Any new warning is fixed before reporting.

- [ ] **Step 4: Build**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Report** test/lint counts, the Paywall before/after pair (`app/build/reports/button-unification/paywall_footer_before.png` and `_after.png`), every C4 stop, and every test assertion changed under the "update to the new styling" rule. Work stays uncommitted.
