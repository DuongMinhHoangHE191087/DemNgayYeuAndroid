# Profile and pairing sync (one linked profile, compact couple dialog)

## Goal

Make "me" and "partner" one linked profile everywhere and shrink the pairing UI to the app's scale. After this work:

1. The top-right avatar, Home, Settings, Reminder, `PairingScreen`, `VerifyInviteDialog` and both profile dialogs show the same name and avatar through one shared `ProfileAvatar`. No screen invents its own stock photo.
2. The "Cặp đôi" dialog (`EditCoupleDialog`) renders at the app density like `EditMyProfileDialog`, with tighter spacing, real 48dp touch targets and one "Đóng".
3. The three data layers (Room `CoupleProfileEntity`, ViewModel `boy*`/`girl*` flows, online `OnlineUserEntity`) stop overwriting each other: one slot rule, one Room writer for "me", online values win while signed in or paired, nothing fabricated is persisted.
4. Every concern above is covered by a runnable test or a named LDPlayer step.

Edits go on top of the dirty working tree. Nothing is reverted, committed or pushed.

Owner request (2026-10-21, with a screenshot of the "Ghép đôi" tab): "Hiện tại phần chỉnh sửa profile của tôi ở góc phí trên bên phải và các phần setting hay các trang nào hiện tại ảnh mặc định và các phần đó thực sự không liên kết với nhau hiện tại tôi và hiện tại phần ghép tôi đang bị to quá không đúng với các scale ở bên ngoài trnag thực hiện tối ưu hiển thị và nhìn sao cho gọn gàng nhất giúp tôi đọc rà soát tránh việc bất đồng bộ các thông tin và chỉnh sửa ở nhiều nơi ... không dùng codex để reivew nữa mà lên kế hoạch để thực hiện và test sao cho đồng bộ nhất cho tôi".

## Process

- Tier L (`dev-workflow`). Analysis: `superpowers:brainstorming`. Plan drafted by Opus 5.5 (subagent, effort high), then every claim re-checked by Claude against the code. The corrections found in that check are folded in below and marked "amended".
- **Codex: skipped by owner waiver** (2026-10-21, "không dùng codex để review nữa"). Substitute gate: Claude reviews each task diff with `git diff -- <files>`, the tests below, and the LDPlayer smoke in T13.
- No code is written until the owner replies `approve`.

## What the owner sees and why

| Symptom | Root cause |
|---|---|
| "Ghép đôi" popup is too big next to the rest of the app | `EditCoupleDialog` is not wrapped in `AppDensity`. A `Dialog` window resets `LocalDensity`, so it renders about 18% larger than `EditMyProfileDialog`, which does wrap. On top of that: oversized avatars and code text, cards nested in cards, a full-height "Đóng", and no footer "Đóng" at all in the COUPLED and incoming-invite states. |
| Default photos differ per screen | Five different fallbacks (see A7). The stock Unsplash photo is also written to every new account at registration, copied to the online user and pushed to Firestore, so the partner sees it too. |
| Top-right profile, Settings and pages do not feel linked | Settings shows the account name (not the profile name) and a Person icon, and its row is not clickable. The Home "me" avatar is a Person icon and not clickable. The Reminder header is a hardcoded stock couple photo. |
| Information goes out of sync / is edited in many places | Gender-based slot assignment in the repository vs role-based reading in the ViewModel; a Room collector that overwrites online values on every Room write; two writers for "me" (repository and ViewModel); partner and anniversary editable while paired although the relationship overrides them; fabricated birthdays, ages and anniversaries persisted by the dialog and by onboarding. |

## Assumptions

Line numbers are as of 2026-10-21. Every task anchors its edits on quoted code, not on line numbers.

VERIFIED (read in code):

- A1. `AppDensity` (`ui/theme/Theme.kt` ~102, private `UI_SCALE = 0.85f`, `MinTouchBox = ceil(48f / UI_SCALE).dp` = 57dp) is applied by `MyApplicationTheme`, but every `Dialog` window resets `LocalDensity`. `EditMyProfileDialog` (EditMyProfileDialog.kt:107-122) is `Dialog { AppDensity { EditMyProfileDialogContent(...) } }`. `EditCoupleDialog` (Dialogs.kt:924-1867) has no such wrapper.
- A2. Under `AppDensity` an M3 `IconButton` (48dp) renders at about 40.8dp, so every touch target needs an explicit `MinTouchBox` (pattern: `ProfileAvatarButton`, CommonComponents.kt:260).
- A3. Profile data lives in three layers. Room `CoupleProfileEntity` is one row (id = 1; entity defaults loveTitle "InLove", loveDays 0, anniversaryDate ""); the ViewModel reads `partner1*` as "me" and `partner2*` as the partner (InLoveViewModel.kt:350-361, 635-642). Online `OnlineUserEntity` is `currentOnlineUser` / `partnerOnlineUser` (repository `_currentUser` / `_partnerUser`). The top bar already reads `currentOnlineUser.avatarUrl` (MainActivity.kt:329) and `onProfileClick = { viewModel.openEditProfileDialog() }` (335).
- A4. The ViewModel Room collector (InLoveViewModel.kt:623-650) overwrites all `boy*`, `girl*`, `loveTitle`, `loveDays` and `anniversary` flows on every Room emission, and resets cards when the row disappears. The online collectors (653-684) use `profileUidOf` / `isProfileChanged` / `profileFieldAfter` (ui/util/OnlineProfileRules.kt) and skip guests. The relationship collector (686-694) sets `_anniversaryDate` and `_loveDays` from the relationship when it exists.
- A5. Slot mismatch (static analysis). `OnlineCoupleRepository.acceptSetLoveInvite` sets `p1 = if (isMeMale) updatedMe else updatedSender` (507-509), and `updateMyProfile` mirrors by gender (243-261) but only when COUPLED. New accounts default to gender "MALE" (Entities.kt:226, AuthRepository.kt ~581 and ~1095). A FEMALE account therefore gets her own data in `partner2*`, which the ViewModel shows as the partner. No existing repository test pins the slots (grep of `app/src/test` finds `partner1Name` / `partner2Name` only in seeds and Room round-trips).
- A6. Any Room save while signed in or paired (`saveCoupleProfile`, `updateLoveTitle`, `setAnniversaryAndRecalculateDays`) makes the Room collector replace the online values in the `boy*`, `girl*` and anniversary flows until the next online change. Room `partner2*` is only an accept-time snapshot.
- A7. Default avatars: `COUPLE_AVATAR_URL` (CommonComponents.kt:92, used only at 275); Unsplash `photo-1534528741775` at EditMyProfileDialog.kt:297, Dialogs.kt:1515/1606/1796, PairingScreen.kt:248/513-514/925, VerifyInviteDialog.kt:166, and persisted at registration (AuthRepository.kt:557, copied to the online user at ~580 and ~1094); Unsplash `photo-1517841905240` (Dialogs.kt:1212, LoveHomeScreen.kt:997, display fallbacks only, never persisted); a hardcoded googleusercontent couple photo (ReminderScreen.kt:429-441); Person icon only (SettingsScreen.kt:413-425, LoveHomeScreen.kt:736).
- A8. Fabricated values are persisted: partner name "Người thương" (Dialogs.kt ~954, ViewModel fallback 1008), partner birthday "20/11/2004" (~955), love title "Hành Trình Yêu Thương" (~958, also "InLove" at repository accept 523), anniversary = today (~959), my birthday "15/10/2004" (EditMyProfileDialog.kt:179; the hint at 537 stays), onboarding `saveCoupleProfile` (OnboardingScreen.kt:279-293: 01/01/2000, 01/01/2002, ages 25 and 23, zodiacs, `days = 1`, anniversary 14/02/2024). `ProfileUtils.calculateAge("")` = 0, `calculateZodiac("")` = "Chưa rõ", `calculateLoveDays("")` = 1.
- A9. Duplicates: copy/share helpers in Dialogs.kt (~998-1016, Vietnamese only) and PairingScreen.kt (~155-173, bilingual); `saveCoupleProfile` fires hearts and a toast (InLoveViewModel.kt:1054-1055) and the dialog fires them again; two pairing UIs exist (dialog tab 1 and full-screen `PairingScreen`; merging them is out of scope).
- A10. The only main-code readers and writers of `partner1*` / `partner2*` are the ViewModel (350-361, 635-642, 1022-1029, 1068-1075, 1851-1853), `OnlineCoupleRepository` (247-259, 513-520) and `Entities.kt`.
- A11. `effectiveDisplayName` (Entities.kt:240-241) is "" when `!isProfileSetup`, blank or "Vô danh". `updateMyProfile` with a blank name stores "Vô danh" and `isProfileSetup = false`, so the Room mirror needs a "Bạn" fallback. The dialog does not validate blank names.
- A12. Account switch order: the vault `enterScope` runs before `syncOnlineUserWithAccount` (AuthRepository.kt 406-407, 924-925, 607-613), so the stale-fallback window is effectively closed (low risk).
- A13. "No Room row" is a real state (GiftScreen `profileExists = coupleProfile != null`), so the mirror must never create a row.
- A14. `InLoveViewModel.onlineRepo` is a public val the ViewModel constructs itself (line 63, built at ~392), so tests can call `viewModel.onlineRepo.setCurrentUserId(...)`.
- A15. No test pins the stock URLs (only unrelated Unsplash URLs in `MemorySyncAdapterTest`). The custom-avatar test in `AuthRepositoryTest` (282-292) must stay untouched. `updateCoupleInfo` (InLoveViewModel.kt:1238) has no caller. `spikes/ui-drift/` holds a throwaway copy of `CommonComponents.kt`; it is not part of the app and is not touched.
- A16. Test patterns exist: `internal` content composables so Robolectric composes without a `Dialog` window (Robolectric never idles with an `OutlinedTextField` inside a `Dialog`); `MyApplicationTheme { Content }` plus `assertWidthIsAtLeast(48.dp)` / `assertHeightIsAtLeast(48.dp)` (`EditProfileDialogTargetsTest`, `InLoveTopBarTest`); VM-driving tests inject an in-memory DB into `AppDatabase.INSTANCE` (`GiftBudgetRegionControlsTest` 50-110); repository tests use `OnlineCoupleRepository(db.inLoveDao(), context, scope, useFirestore = false)` and `withRepoOf` (`OnlineCoupleAcceptInviteTest` 40-65, `functions = null`).

UNVERIFIED:

- U1. The runtime effect of the gender-slot mismatch for a real FEMALE account (static analysis only; T1's test proves the new rule, not the old symptom).
- U2. The final visual result (no Roborazzi goldens exist for these dialogs). Covered by targets-size tests plus LDPlayer screenshots, not pixel diffs.
- U3. Whether a ViewModel test can complete under Robolectric for a signed-in uid (the ViewModel's repository uses Firestore by default). T7 has a fallback: keep the pure-rule tests and report the gap.

## Data map: who stores, edits and shows what

| Datum | Stored in | Single writer after this plan | Display source after this plan |
|---|---|---|---|
| Me: name, birthday, avatar | `OnlineUserEntity` (Room + Firestore), mirrored to Room `partner1*`, ViewModel `boy*` flows | `OnlineCoupleRepository.updateMyProfile` (also mirrors Room `partner1*` when a row exists). Edited only in `EditMyProfileDialog`, reachable from the top-right avatar, the Home "me" avatar and the Settings account row. | `currentOnlineUser` through `ProfileAvatar` on every screen; `boy*` flows follow it (online collector when signed in, direct update for guests). |
| Partner: name, birthday, avatar | paired: `partnerOnlineUser`; otherwise Room `partner2*` | paired: the partner's own device (read-only here); not paired: `EditCoupleDialog` "Hồ sơ" tab via `saveCoupleProfile` | paired: online partner; not paired: `girl*` flows from Room. |
| Love title | Room `loveTitle` only | `updateLoveTitle` (dialog, Home editor, Settings all call it) | `loveTitle` flow. |
| Anniversary / love days | Room; overridden by `activeRelationship` while paired | not paired: `EditCoupleDialog` save and the Home widget; paired: the relationship (dialog shows it read-only, the widget edit is refused with a toast) | `anniversary` / `loveDays` flows. |

## Decisions

- D1. Role-based slots. Room `partner1*` is always "me" and `partner2*` is always the partner, whatever the gender. This matches how the ViewModel reads them. In `acceptSetLoveInvite`: `p1 = updatedMe`, `p2 = updatedSender`.
- D2. One Room writer for "me": `OnlineCoupleRepository.updateMyProfile`. It copies the profile into `partner1*` (name, birthday, age, zodiac, avatar) whenever a Room row exists, never creates a row, and never touches `partner2*`, title, days or anniversary. Name fallback is `effectiveDisplayName.ifEmpty { "Bạn" }` (A11). The ViewModel stops writing Room for "me".
- D3. Ownership guard in the ViewModel Room collector (absolute guard, not a field-level resolver, because it is the smaller diff). Room writes `boy*` only when `roomOwnsCard(currentOnlineUser.value.uid)`, `girl*` only when `roomOwnsCard(partnerOnlineUser.value?.uid)`, and `anniversary` / `loveDays` only when `activeRelationship.value == null`. `loveTitle` is always applied. The guard covers the "row disappeared" reset branch too.
- D4. Guests have no online collector, so the ViewModel `updateMyProfile` sets the `boy*` flows directly from the refreshed `currentOnlineUser.value` (age and zodiac included) when `roomOwnsCard(...)` is true. Signed-in users get the same values from the online collector.
- D5. Display-time normalization: `displayAvatarUrl(url)` returns "" for blank URLs and for the legacy stock photo (`photo-1534528741775`). One shared `ProfileAvatar` shows a Person placeholder (tag `profile_avatar_placeholder`) for those, and an `AsyncImage` with a Person fallback painter otherwise. Only that one persisted legacy URL is normalized.
- D6. Registration stops persisting the stock photo (AuthRepository.kt:557 becomes `avatarUrl = ""`). No Firestore or Room data migration.
- D7. While paired, the relationship owns the dates and the partner owns their profile. The dialog shows partner info and the anniversary read-only; `setAnniversaryAndRecalculateDays` refuses with the toast `tr("Ngày yêu đang đồng bộ theo ghép đôi, không đổi được ở đây", "Your love date syncs from pairing and can't be changed here")`.
- D8. Blank stays blank. No fabricated birthday, age, zodiac, anniversary or `days` is written by the dialog, `EditMyProfileDialog` or onboarding. Blank fields show placeholders.
- D9. Pairing tab compaction. `AppDensity` wrapper, `MinTouchBox` on every target, smaller avatars, code text and paddings, one full-width "Đóng"/"Close" in every pairing-tab state.
- D10. One copy/share helper (`ui/util/CoupleCodeShare.kt`, bilingual). Callers keep their own toast.
- D11. One edit entry for "me": top-right avatar, Home "me" avatar and the Settings account row all call `viewModel.openEditProfileDialog()`.

Defaults the owner can override with `change:` (decided without asking, per the owner's standing preference): Reminder header shows the partner avatar only (not both); the Home widget date edit is refused with a toast while paired (not greyed out); blank fields stay blank with placeholders; only `photo-1534528741775` is normalized; new strings stay inline `if (isEnglish)` (not `strings.xml`); no data migration (Room rows already swapped by earlier versions are not repaired).

## Execution rules (apply to every task)

- One Gradle process at a time. In a PARALLEL wave the subagents edit files and write tests but do not run Gradle; the main session then runs the wave's check commands one after another and sends failures back to the owning task. SOLO tasks may run Gradle and work test-first (red, then green).
- The working tree is dirty (R2 media, billing, KMP/iOS docs, many tests). Use `Edit` anchored on quoted code. Never `git checkout` / `restore` / `stash` / `reset`, never rewrite a whole file, never commit or push. If an anchor has moved or the plan looks wrong, stop and report BLOCKED.
- A task touches only the files listed for it. Subagent report: 10 lines max (`STATUS: DONE|BLOCKED`, files changed, tail of the test output, blockers).
- Pass `model` and `effort` explicitly. Escalation per the global rules: haiku fails twice, then sonnet; sonnet fails twice, then an Opus advisor (read-only); still failing, stop and ask the owner. Record attempts in the Execution log at the bottom.
- Commands (run from the repo root, PowerShell): `.\gradlew.bat :app:compileDebugKotlin`, `.\gradlew.bat :app:testDebugUnitTest --tests "<fqcn>"`, `.\gradlew.bat :app:testDebugUnitTest`, `.\gradlew.bat :app:assembleDebug`.

## Tasks

Paths are relative to `app/src/main/java/com/example/` (main) or `app/src/test/java/com/example/` (test).

| id | wave / mode | files (owned) | change | test or command | depends on | model |
|---|---|---|---|---|---|---|
| T0 | 0 / SOLO | none | Record the baseline: run the full unit suite before any edit and note the failing test FQCNs in the Execution log. Expected: the AcceptInvite race test and 2 Compose idle-timeout tests. | `.\gradlew.bat :app:testDebugUnitTest` | - | haiku, effort max |
| T1 | 1 / SOLO, test-first | main `data/repository/OnlineCoupleRepository.kt`; test `data/repository/OnlineCoupleProfileSlotTest.kt` (new) | D1 + D2 (amended: mirror only when a row exists, never create one; name fallback "Bạn"). | `--tests "com.example.data.repository.OnlineCoupleProfileSlotTest"`, then `OnlineCoupleAcceptInviteTest` and `OnlineCoupleRepositoryPairingTest` | T0 | sonnet, effort high |
| T2 | 2 / PARALLEL | main `ui/util/OnlineProfileRules.kt`; test `ui/util/OnlineProfileRulesTest.kt` (exists) | Add `displayAvatarUrl` and `roomOwnsCard` (D3, D5) with tests. | `--tests "com.example.ui.util.OnlineProfileRulesTest"` | - | sonnet, effort high |
| T3 | 2 / PARALLEL | main `ui/screens/Dialogs.kt` (EditCoupleDialog layout and density only); test `ui/screens/EditCoupleDialogTest.kt` (new) | D9: `AppDensity` wrapper, `internal fun EditCoupleDialogContent`, compact pairing tab, real touch targets, one "Đóng". No data logic. | `--tests "com.example.ui.screens.EditCoupleDialogTest"`, `EditProfileDialogTargetsTest` | - | sonnet, effort high |
| T4 | 2 / PARALLEL | main `data/repository/AuthRepository.kt` (line 557 only); test `data/repository/AuthRepositoryTest.kt` | D6: registration persists `avatarUrl = ""`; one test. | `--tests "com.example.data.repository.AuthRepositoryTest"` | - | haiku, effort max |
| T5 | 2 / PARALLEL | main `ui/util/CoupleCodeShare.kt` (new) | D10: `copyCoupleCode(context, text)` and `shareCoupleCode(context, code, isEnglish)`. | `:app:compileDebugKotlin` | - | haiku, effort max |
| T6 | 3 / SOLO | main `ui/components/CommonComponents.kt`; test `ui/components/ProfileAvatarTest.kt` (new) | D5: shared `ProfileAvatar`; `ProfileAvatarButton` uses it; delete `COUPLE_AVATAR_URL`. | `--tests "com.example.ui.components.ProfileAvatarTest"`, `InLoveTopBarTest` | T2 | haiku, effort max |
| T7 | 3 / SOLO, test-first | main `ui/viewmodel/InLoveViewModel.kt`; test `ui/viewmodel/ProfileSyncViewModelTest.kt` (new) | D3, D4, D7 (anniversary refusal), toast rewording (amended: guest-only flow update, not removal). | `--tests "com.example.ui.viewmodel.ProfileSyncViewModelTest"`, `ReminderWiringSettingsTest` | T1, T2 | sonnet, effort high |
| T8 | 4 / PARALLEL | main `ui/screens/PairingScreen.kt`, `ui/screens/VerifyInviteDialog.kt` | Stock avatars to `ProfileAvatar`; helpers to T5. | `:app:compileDebugKotlin` | T5, T6 | haiku, effort max |
| T9 | 4 / PARALLEL | main `ui/screens/LoveHomeScreen.kt`, `ui/screens/ReminderScreen.kt` | Home "me" avatar linked and clickable, partner avatars to `ProfileAvatar`, hide the age chip at age 0; Reminder header avatar linked. | `:app:compileDebugKotlin` | T6 | haiku, effort max |
| T10 | 4 / PARALLEL | main `ui/screens/SettingsScreen.kt` | Account header shows the profile avatar and name and opens the profile dialog. | `:app:compileDebugKotlin` | T6 | haiku, effort max |
| T11 | 4 / PARALLEL | main `ui/screens/EditMyProfileDialog.kt`, `ui/screens/OnboardingScreen.kt`; test `ui/screens/EditProfileDialogTargetsTest.kt` | D8 for my birthday and onboarding; dialog avatar to `ProfileAvatar`; one new test. | `--tests "com.example.ui.screens.EditProfileDialogTargetsTest"` | T6 | haiku, effort max |
| T12 | 4 / PARALLEL | main `ui/screens/Dialogs.kt`; test `ui/screens/EditCoupleDialogTest.kt` | D7, D8, D10, D5 inside `EditCoupleDialog`; stateless read-only COUPLED summary with its own test. | `--tests "com.example.ui.screens.EditCoupleDialogTest"` | T3, T5, T6, T7 | sonnet, effort high |
| T13 | 5 / SOLO | none | Full suite vs the T0 baseline, `:app:assembleDebug`, grep gate, LDPlayer smoke. | see Verification | all | sonnet, effort high |

Wave order: 0, 1, 2, 3, 4, 5. In waves 2 and 4 the file sets are disjoint. T3 and T12 both own `Dialogs.kt` and `EditCoupleDialogTest.kt` but sit in different waves, so T12 always starts from T3's result. After wave 4, run one `:app:compileDebugKotlin`, then each task's test command in turn.

## Task details

Each block is the full text to hand to a subagent, together with the Execution rules above.

### T0 - baseline (haiku)

Change no file except the Execution log at the bottom of this plan. Run `.\gradlew.bat :app:testDebugUnitTest`. Copy the failing test FQCNs and the totals into the log. Report in 10 lines.

### T1 - role-based slots and one Room writer for "me" (sonnet, test-first)

Files: `OnlineCoupleRepository.kt` (main), new `OnlineCoupleProfileSlotTest.kt` (test, package `com.example.data.repository`).

Write the tests first and watch them fail. Use the setup of `OnlineCoupleAcceptInviteTest.kt` lines 40-65 (`OnlineCoupleRepository(db.inLoveDao(), context, scope, useFirestore = false)`, `withRepoOf`, `functions = null`).

1. `acceptSetLoveInvite` (about lines 505-532). Replace
   ```kotlin
   val isMeMale = updatedMe.gender == "MALE"
   val p1 = if (isMeMale) updatedMe else updatedSender
   val p2 = if (!isMeMale) updatedMe else updatedSender
   ```
   with `val p1 = updatedMe` and `val p2 = updatedSender`. Delete `isMeMale` if nothing else uses it.
2. `updateMyProfile` (about lines 241-263). Replace the block that starts with `// If coupled, sync current user's part into CoupleProfileEntity` with: if `dao.getCoupleProfileSync()` is non-null, `dao.insertCoupleProfile(currentProfile.copy(partner1Name = updatedMe.effectiveDisplayName.ifEmpty { "Bạn" }, partner1Birthday = updatedMe.birthDate, partner1Age = updatedMe.age, partner1Zodiac = updatedMe.zodiac, partner1ProfilePicture = updatedMe.avatarUrl))`. No status condition, no gender branch, no row creation, `partner2*`, `loveTitle`, `loveDays`, `anniversaryDate`, likes, budget and region stay untouched. Keep `refreshState()` and the return value.

Tests (each uses its own users and an in-memory DB):
- a FEMALE `uid_b` accepts an invite from MALE `uid_a`: Room `partner1Name` is `uid_b`'s name and `partner2Name` is `uid_a`'s name;
- a coupled FEMALE user calls `updateMyProfile`: only `partner1*` change and `partner2*` stay;
- not coupled, row exists: `updateMyProfile` changes `partner1*` only, `loveTitle`, `loveDays`, `anniversaryDate` are unchanged;
- no row: `updateMyProfile` leaves `getCoupleProfileSync()` null;
- blank name: `partner1Name == "Bạn"`.

Done when the new test class is green and `OnlineCoupleAcceptInviteTest` and `OnlineCoupleRepositoryPairingTest` show no new failure compared with the T0 baseline (the AcceptInvite race test already fails at HEAD). If an existing assertion pinned the old gender slots, update only that assertion and say so in the report.

### T2 - ownership and avatar rules (sonnet)

Files: `ui/util/OnlineProfileRules.kt`, `ui/util/OnlineProfileRulesTest.kt` (exists, add cases).

Add next to `profileUidOf`:
```kotlin
/** Registration used to persist one stock photo for every account; treat it as "no photo". */
internal fun displayAvatarUrl(url: String?): String =
  url?.takeIf { it.isNotBlank() && !it.contains("photo-1534528741775") }.orEmpty()

/** Room owns a card only when no online profile feeds it (guest, signed out, no partner). */
internal fun roomOwnsCard(onlineUid: String?): Boolean = profileUidOf(onlineUid) == null
```
Tests: `displayAvatarUrl` for null, "", "  ", the stock URL with `?w=200`, the stock URL with `?q=80&w=600&auto=format&fit=crop`, an https URL, a `content://` URI, a `file:///` path. `roomOwnsCard` for null, "", `OnlineCoupleRepository.GUEST_UID` (all true) and "uid_1" (false).

### T3 - EditCoupleDialog at the app scale (sonnet)

Files: `ui/screens/Dialogs.kt` (only `EditCoupleDialog`, layout and density, no data logic), new `ui/screens/EditCoupleDialogTest.kt`.

1. Make `EditCoupleDialog(viewModel, onDismiss)` the same shape as `EditMyProfileDialog` (EditMyProfileDialog.kt:107-122): `Dialog(<existing args and properties>) { AppDensity { EditCoupleDialogContent(viewModel, onDismiss) } }`. Move the existing state, launchers and Card into `internal fun EditCoupleDialogContent(viewModel: InLoveViewModel, onDismiss: () -> Unit)` so Robolectric can compose it without a dialog window.
2. Header: icon 32dp, title 17sp, close button `Box(Modifier.size(MinTouchBox))` with tag `btn_close_edit_couple`.
3. "Ghép đôi" tab: `Arrangement.spacedBy(10.dp)`, card padding 12dp, avatars 44dp, code text 17sp; copy and share icon buttons `Modifier.size(MinTouchBox)`; the "Dán mã" chip `heightIn(min = MinTouchBox)` with tag `dialog_btn_paste`; the clear button `size(MinTouchBox)` with an 18dp icon and tag `dialog_btn_clear`; the search button `size(MinTouchBox)` (keeps `dialog_btn_search`).
4. "Hồ sơ" tab: the 68dp partner avatar `Box` becomes the clickable photo target and carries the tag `btn_partner_pencil`; the camera, gallery, remove and the 22dp close controls get `heightIn(min = MinTouchBox)`.
5. Footer: delete the old "Đóng" `OutlinedButton` (about lines 1851-1859, shown only when not COUPLED and no incoming invite). Add one full-width "Đóng"/"Close" button (`heightIn(min = MinTouchBox)`, tag `btn_close_pairing_tab`) at the bottom of the pairing tab for every state (incoming invite, outgoing invite, COUPLED, default).
6. Keep every existing tag: `edit_couple_dialog`, `btn_partner_pencil`, `btn_partner_camera`, `btn_partner_gallery`, `btn_partner_remove`, `input_girl_name`, `input_anniversary_date`, `btn_save_couple_offline`, `dialog_incoming_invite_card`, `dialog_search_input`, `dialog_btn_search`.

Test (harness of `GiftBudgetRegionControlsTest.kt` 50-110: in-memory DB into `AppDatabase.INSTANCE`, build the ViewModel): compose `MyApplicationTheme { EditCoupleDialogContent(viewModel) {} }`. On the pairing tab assert `btn_close_edit_couple`, `dialog_btn_search` and `btn_close_pairing_tab` are at least 48dp wide and tall; type into `dialog_search_input` and assert `dialog_btn_clear` the same way (and `dialog_btn_paste` while the field is empty). On the "Hồ sơ" tab assert `btn_save_couple_offline` is at least 48dp tall. If a check cannot complete under Robolectric, keep the checks that do and report the rest.

### T4 - stop persisting the stock photo at registration (haiku)

Files: `data/repository/AuthRepository.kt` (line 557 only), `data/repository/AuthRepositoryTest.kt`.

Before editing, grep main code for logic that treats a blank `avatarUrl` as "profile incomplete" (`avatarUrl.isBlank`, `avatarUrl.isEmpty`, `isProfileSetup`). If any gate would now flag new accounts, stop and report BLOCKED. Otherwise change `avatarUrl = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?q=80&w=600&auto=format&fit=crop"` to `avatarUrl = ""`. Add one test to `AuthRepositoryTest`: after registering, the account row and the online user both have a blank avatar. Do not touch the custom-avatar test at lines 282-292.

### T5 - one copy/share helper (haiku)

File: new `ui/util/CoupleCodeShare.kt`.

```kotlin
internal fun copyCoupleCode(context: Context, text: String)            // ClipboardManager primary clip, label "InLove code"
internal fun shareCoupleCode(context: Context, code: String, isEnglish: Boolean) // ACTION_SEND text/plain chooser
```
Copy the share texts for both languages from `PairingScreen.kt` lines 163-173. No toast inside (callers show their own). Do not touch Dialogs.kt or PairingScreen.kt here.

### T6 - shared ProfileAvatar (haiku)

Files: `ui/components/CommonComponents.kt`, new `ui/components/ProfileAvatarTest.kt`.

Add `@Composable fun ProfileAvatar(url: String?, contentDescription: String?, modifier: Modifier = Modifier)`: a circle-clipped `Box` with the pink background already used by neighbouring components. `val shown = displayAvatarUrl(url)`; if `shown` is empty draw a Person `Icon` with tag `profile_avatar_placeholder`, otherwise an `AsyncImage` (`ContentScale.Crop`, `error = rememberVectorPainter(Icons.Default.Person)`). Change `ProfileAvatarButton` (line ~260; keep `Box(size(MinTouchBox))`, tag `topbar_profile_avatar`, the 36dp image size) to use it, and update its doc comment (it no longer shows "the default couple image"). Delete `COUPLE_AVATAR_URL` (single use). Use only colors and icons already imported in the file.

Tests: blank URL and the stock URL show `profile_avatar_placeholder`; an https URL does not. Run `InLoveTopBarTest` too (it asserts `topbar_profile_avatar` is at least 48dp).

### T7 - ViewModel ownership guard (sonnet, test-first)

Files: `ui/viewmodel/InLoveViewModel.kt`, new `ui/viewmodel/ProfileSyncViewModelTest.kt`.

(a) Room collector (about lines 623-650). Wrap the assignments: `boy*` only if `roomOwnsCard(currentOnlineUser.value.uid)`; `girl*` only if `roomOwnsCard(partnerOnlineUser.value?.uid)`; `_anniversaryDate` and `_loveDays` only if `activeRelationship.value == null`; `_loveTitle` always. Apply the same guard in the "profile became null" branch (`resetBoyCard` / `resetGirlCard` and the defaults).

(b) `updateMyProfile` (about lines 1825-1862). Replace the block guarded by `if (relationshipStatus.value != com.example.data.model.OnlineStatus.COUPLED) { ... repository.saveCoupleProfile(...) }` (it writes the flows and Room itself) with a guest-only flow update; the repository now mirrors Room (T1):
```kotlin
if (roomOwnsCard(currentOnlineUser.value.uid)) {   // no online collector feeds boy* for guests
  val me = currentOnlineUser.value                 // repository.updateMyProfile already refreshed it
  _boyName.value = me.effectiveDisplayName.ifEmpty { "Bạn" }
  _boyBirthDate.value = me.birthDate
  _boyAge.value = me.age
  _boyZodiac.value = me.zodiac
  _boyAvatarUrl.value = me.avatarUrl
}
```
Check the real flow types in the file and match them. Keep the toast, the stale-local-avatar delete, closing the dialog and `triggerFloatingHearts()`.

(c) `setAnniversaryAndRecalculateDays` (about lines 1128-1153): at the top, if `activeRelationship.value != null`, `showToast(tr("Ngày yêu đang đồng bộ theo ghép đôi, không đổi được ở đây", "Your love date syncs from pairing and can't be changed here"))` and return before touching any flow.

(d) `saveCoupleProfile` toast (line ~1055): `tr("Đã lưu hồ sơ 2 bạn thành công! ❤️", "Your couple profile was saved! ❤️")`.

Tests (harness of `GiftBudgetRegionControlsTest.kt` 50-110):
- guest with a Room row: `updateMyProfile("Smoke A", "01/01/2000", "", "FEMALE", "")` gives Room `partner1Name == "Smoke A"` and `boyName == "Smoke A"`, `boyAge > 0`;
- guest without a row: the flows update and `getCoupleProfileSync()` stays null;
- signed-in clobber test: `viewModel.onlineRepo.setCurrentUserId("uid_me")`, `onlineRepo.updateMyProfile("Online A", ...)`, wait for `boyName == "Online A"`, then `repository.saveCoupleProfile(row.copy(partner1Name = "Room Stale"))` and assert `boyName` is still "Online A". Show this test red before the guard.

If a ViewModel test cannot complete under Robolectric (U3), keep the pure-rule tests from T2, say so, and rely on the LDPlayer steps for the guest paths. Rerun `ReminderWiringSettingsTest` (it exercises `setAnniversaryAndRecalculateDays`, line ~376).

### T8 - Pairing screens (haiku)

Files: `ui/screens/PairingScreen.kt`, `ui/screens/VerifyInviteDialog.kt`.

Replace the stock-photo `AsyncImage` blocks (PairingScreen.kt ~246-249, ~513-514, ~925; VerifyInviteDialog.kt:166) with `ProfileAvatar(<the same url expression without the ifEmpty fallback>, <the same contentDescription>, <the same size/shape modifier>)`. Replace the local copy and share helpers (~155-173) with `copyCoupleCode` / `shareCoupleCode`; keep each caller's own toast.

### T9 - Home and Reminder (haiku)

Files: `ui/screens/LoveHomeScreen.kt`, `ui/screens/ReminderScreen.kt`.

- Home "me" avatar (about 717-743): `ProfileAvatar(currentOnlineUser.avatarUrl.ifEmpty { boyAvatarUrl }, ...)`; make the outer `Box` clickable (target at least `MinTouchBox`) calling `viewModel.openEditProfileDialog()`, tag `home_my_avatar`.
- Partner avatar (about 887-904 and 996-997): `ProfileAvatar(girlAvatarUrl, ...)`, dropping the Unsplash fallback.
- Hide the age chip (about 938-950) when both `boyAge` and `girlAge` are 0.
- Reminder header (about 429-441): replace the hardcoded googleusercontent photo with `ProfileAvatar(girlAvatarUrl, contentDescription = girl)` at 40dp.

### T10 - Settings account row (haiku)

File: `ui/screens/SettingsScreen.kt` (account header, about 409-446).

Show `ProfileAvatar(currentUser.avatarUrl)` at 44dp, the name `currentUser.effectiveDisplayName.ifBlank { currentAccount.displayName }`, and make the row `heightIn(min = MinTouchBox)` and clickable (`viewModel.openEditProfileDialog()`, tag `settings_profile_row`). Keep the email and the verified icon.

### T11 - My profile dialog and onboarding (haiku)

Files: `ui/screens/EditMyProfileDialog.kt`, `ui/screens/OnboardingScreen.kt`, `ui/screens/EditProfileDialogTargetsTest.kt`.

- EditMyProfileDialog.kt line ~179: `mutableStateOf(currentUser.birthDate.ifEmpty { "15/10/2004" })` becomes `mutableStateOf(currentUser.birthDate)`. Keep the hint `Text("15/10/2004")` (~537). The avatar block (~293-300) becomes `ProfileAvatar(avatarUrl, "Ảnh đại diện")`.
- OnboardingScreen.kt `saveCoupleProfile(...)` call (~279-293): `boyBirth = ""`, `boyA = 0`, `boyZod = ""`, `girlBirth = ""`, `girlA = 0`, `girlZod = ""`, `days = 0`, `anniversary = ""`. Keep names and title.
- New test in `EditProfileDialogTargetsTest`: with a blank `birthDate`, clicking `btn_save_my_profile` saves "" (not "15/10/2004").

### T12 - EditCoupleDialog data behaviour (sonnet)

Files: `ui/screens/Dialogs.kt`, `ui/screens/EditCoupleDialogTest.kt`. Starts from T3's result.

- Use `ProfileAvatar` at about lines 1212 (partner photo), 1515 (invite sender), 1606 (partner), 1796 (search result); drop the Unsplash fallbacks.
- Delete the local clipboard and share helpers (~998-1016); call `copyCoupleCode` / `shareCoupleCode` and keep the dialog's own Vietnamese toast.
- Drop the fabricated initial values (about 954, 955, 958, 959): seed from the raw `girl*`, `loveTitle`, `anniversary` flows. `girlZodiac` is "" when the birthday is blank (not "Chưa rõ").
- Delete the dialog's own duplicate `triggerFloatingHearts()` and `showToast(...)` after saving (the ViewModel already does both); keep `onDismiss()`.
- COUPLED state: extract a stateless `internal fun CoupledProfileSummary(partnerName, partnerBirthday, partnerAvatarUrl, anniversary, isEnglish)` showing the partner avatar, name and birthday as read-only text, the anniversary as read-only text and the hint "Thông tin người ấy do người ấy tự cập nhật và được đồng bộ tự động." / "Your partner updates their own info; it syncs automatically." In the "Hồ sơ" tab, when `relationshipStatus == COUPLED`, show it instead of the editable partner and anniversary cards, with only "Đóng" in the footer (no `btn_save_couple_offline`).
- Tests: (1) guest, not coupled, Room has `partner2Birthday = ""` and `anniversaryDate = ""`; click `btn_save_couple_offline`; Room still has "" for both and `partner1Name` is unchanged; (2) `CoupledProfileSummary` shows the three values and the hint, and has no node tagged `btn_save_couple_offline`.

### T13 - verification (sonnet)

See Verification below. Report the numbers and the screenshots checked.

## Verification and test strategy

| Owner concern | Check | Where |
|---|---|---|
| "Ghép đôi" too big, not at the app scale | The content is wrapped in `AppDensity` (by construction); copy, share, paste, clear, search, close and save targets are at least 48dp; LDPlayer screenshot compared with "Hồ sơ của tôi" | `EditCoupleDialogTest`, T13 smoke |
| Default images differ / not linked | `displayAvatarUrl` and `roomOwnsCard` rules; placeholder rendering; registration stores a blank avatar; grep gate: `rg "photo-1534528741775|photo-1517841905240|COUPLE_AVATAR_URL" app/src/main` returns only `ui/util/OnlineProfileRules.kt` | `OnlineProfileRulesTest`, `ProfileAvatarTest`, `AuthRepositoryTest`, T13 |
| Top-right profile = Settings = pages | One dialog entry from the top bar, Home "me" avatar and Settings row; edit the name once and see it on Home, Settings and the top bar | `InLoveTopBarTest` (existing), T13 smoke |
| Layers overwrite each other | Slot rule, single Room writer, ownership guard, signed-in clobber test | `OnlineCoupleProfileSlotTest`, `ProfileSyncViewModelTest` |
| Edited in many places / fabricated data | Paired state is read-only; blank stays blank; anniversary refused while paired | `EditCoupleDialogTest`, `EditProfileDialogTargetsTest`, `ProfileSyncViewModelTest`, T13 smoke |
| No regressions | Full suite vs the T0 baseline; `:app:assembleDebug`; crash markers on LDPlayer | T13 |

T13 steps:

1. `.\gradlew.bat :app:testDebugUnitTest`: the only failures are the T0 baseline ones (AcceptInvite race, 2 Compose idle timeouts). Any new failure is a regression and goes back to its owning task.
2. `.\gradlew.bat :app:assembleDebug`, then the grep gate above.
3. LDPlayer 9 smoke (`F:\LDPlayer\LDPlayer9\adb.exe -s emulator-5554`, package `com.aistudio.inlove.kmrv`): `install -r app\build\outputs\apk\debug\app-debug.apk`, `logcat -c`, `shell monkey -p com.aistudio.inlove.kmrv -c android.intent.category.LAUNCHER 1`. `uiautomator dump` does not work on this emulator, so drive it with `screencap` plus `input tap` and read the screenshots.
   - the top-bar avatar and the Home "me" avatar show the same placeholder (no stock photo);
   - tap the Home "me" avatar: "Hồ sơ của tôi" opens with an empty birthday;
   - set the name to "Smoke A": Home and the top bar update;
   - the top-right avatar opens the same dialog; the Settings account row shows the avatar and name and opens it;
   - open "Cặp đôi": its scale matches "Hồ sơ của tôi"; on the "Ghép đôi" tab the footer "Đóng" is present and paste, search and copy are full-size;
   - the Reminder header shows the linked avatar, not the hardcoded photo;
   - after every step: `pidof com.aistudio.inlove.kmrv` is non-empty and `logcat -d` filtered for `FATAL|AppPluginBase onCreate failed|NoClassDefFoundError` is empty.
   - Not covered: the COUPLED state and any two-device pairing (one emulator; covered only by the `CoupledProfileSummary` test and review).
4. Report: what changed, test output, the baseline comparison, what was skipped, and the log of attempts. No commit or push.

## Codex

Skipped by owner waiver on 2026-10-21 ("không dùng codex để review nữa"). The planner's output was instead re-verified by Claude against the code; the amendments that came out of that check (T1 never creates a row and uses a "Bạn" fallback, T7 keeps a guest-only flow update, the `ReminderWiringSettingsTest` rerun, the clobber-test recipe, the toast rewording) are already in the tasks above.

## Risks

- Hot spots with uncommitted work: `OnlineCoupleRepository.kt`, `AuthRepository.kt`, `AuthRepositoryTest.kt`, `InLoveViewModel.kt`, `Dialogs.kt`. Edit-only, anchored on quoted code; stop if an anchor has moved.
- `AppDensity` shrinks built-in sizes: any target left without an explicit `MinTouchBox` drops under 48dp. The tests in T3, T6 and T12 cover the controls changed here, not the rest of the app.
- Room rows already swapped by earlier versions (FEMALE accounts) stay swapped until the next `updateMyProfile` or accept. The ownership guard hides them while signed in or paired, but they can resurface after a breakup (the partner slot then shows the old snapshot). No repair is planned.
- The legacy stock photo stays in Firestore for existing users, so older app versions on the partner's phone still show it. The new app hides it at display time.
- Guard plus a blank online field keeps the previous card value. That is intended (it hides legacy fake onboarding data) but means clearing a field online does not clear it on the card.
- Onboarding no longer fabricates ages and dates; Home must cope with blank values. T9 hides the age chip at age 0 and T13 includes a fresh-install pass.
- A Compose or ViewModel test that drives a `Dialog` or a signed-in repository can hit the Robolectric idle-timeout pattern; a new timeout counts as a regression, and T7 and T3 have fallbacks.
- Wave 4 tasks have disjoint files but share one Gradle build, so compile and test checks run sequentially after all of them are authored.

## Out of scope (skipped: X, add when Y)

- Cleaning legacy stock URLs out of Firestore and Room: add when the owner wants old data repaired.
- Merging `PairingScreen` with the "Ghép đôi" tab: add when the duplicate pairing UI becomes a maintenance problem.
- Sharing the love title through Firestore: add when both phones must show the same title.
- One-time repair of gender-swapped Room rows, and refreshing the Room `partner2*` snapshot while coupled: add when the post-breakup display matters.
- A test for the anniversary-refusal toast (needs a paired relationship state): add when a paired-state test fixture exists.
- Greying out the date field in `LoveDaysCalculatorWidget` while paired: add if the toast proves confusing.
- Moving the hardcoded Vietnamese search errors (Dialogs.kt ~1761, ~1763) and the new inline strings to `strings.xml`: add with the next localization pass.
- Syncing `UserAccountEntity.displayName` with the profile name, and unifying the "Người Thương" / "Người thương" defaults: add when account and profile names must be one field.
- Roborazzi golden images for the dialogs: add if a visual regression slips through the size tests.
- Non-avatar Unsplash images (AppModals 663-718, GiftSuggestionGenerator, ReminderScreen.kt:833, Dialogs.kt:272, LoveHomeScreen.kt:129, InLoveViewModel.kt:227) and `updateCoupleInfo` (no caller): unrelated to profile sync.

## Execution log

| task | attempt | model | result | notes |
|---|---|---|---|---|
| T0 | 1 | main session | DONE | `:app:testDebugUnitTest` on the untouched tree (HEAD 1517f2a + 238 dirty files): BUILD SUCCESSFUL in 14m02s, 146 suites, 1435 tests, 0 failures, 0 errors, 0 skipped. Any failure in later runs is new (or a known flaky: AcceptInvite race, Compose idle timeouts) and is re-run alone before blaming a task. |
| T1 | 1 | sonnet / high | DONE | `OnlineCoupleProfileSlotTest` (5 tests): red 4 failed, 1 passed (noRow guard passes at HEAD by design), then green. `OnlineCoupleAcceptInviteTest` and `OnlineCoupleRepositoryPairingTest` green. Main-session diff review: two hunks only (`updateMyProfile` mirror, `acceptSetLoveInvite` p1/p2). No existing assertion changed. |
| T2 | 1 | sonnet / high | DONE | `displayAvatarUrl` + `roomOwnsCard` added; `OnlineProfileRulesTest` 11/11 green. |
| T3 | 1 | sonnet / high | DONE | `EditCoupleDialog` = `Dialog { AppDensity { EditCoupleDialogContent } }`; compact pairing tab; every target `MinTouchBox`; one "Đóng" (`btn_close_pairing_tab`) in every pairing state. New tags `btn_close_edit_couple`, `dialog_btn_paste`, `dialog_btn_clear`, `btn_close_pairing_tab`; `btn_partner_pencil` moved to the 68dp avatar box. `EditCoupleDialogTest` 2/2 and `EditProfileDialogTargetsTest` 2/2 green. Body of `EditCoupleDialogContent` is indented 2 spaces too deep on purpose (keeps T12's diff small); dedent after T12. |
| T4 | 1 | haiku / max | DONE | Registration persists `avatarUrl = ""`; no gate treats a blank avatar as "needs setup"; `AuthRepositoryTest` 17/17 green. |
| T5 | 1 | haiku / max | DONE | `ui/util/CoupleCodeShare.kt` (label "Couple Code", the label the old helpers used). Compiles. |
| wave 2 check | - | main session | DONE | `:app:compileDebugKotlin` BUILD SUCCESSFUL (1m30s); the four test classes above in one run, BUILD SUCCESSFUL (1m06s). |
| T6 | 3 | haiku / max | DONE | `ProfileAvatar` (Person placeholder tag `profile_avatar_placeholder`) added; `ProfileAvatarButton` uses it; `COUPLE_AVATAR_URL` deleted (no other user in `app/src`). `ProfileAvatarTest` 4/4, `InLoveTopBarTest` 8/8 (checked in the JUnit XML). Diff reviewed: matches the brief. |
| T7 | 1 | sonnet / high | DONE | `InLoveViewModel`: Room collector guards (`roomOwnsCard` for boy*/girl*, `activeRelationship == null` for love date, title always; same in the null-profile branch), `updateMyProfile` guest-only flow update (no second Room write), `setAnniversaryAndRecalculateDays` refused while paired (toast), `saveCoupleProfile` toast reworded. `ProfileSyncViewModelTest` 5/5 (checked in the JUnit XML): red before the guard for signed-in clobber, paired refusal and paired date; the two guest tests green before and after by design. `ReminderWiringSettingsTest` 13/13. Caveat: no completion signal for the VM's session restore, so `newViewModel()` idles the looper a fixed 3s (a 1.5s settle failed `guestWithoutRow` once); if it flakes in the full suite, rerun the class alone. |
| T8 | 1 | haiku / max | DONE | `PairingScreen.kt`: 3 avatars -> `ProfileAvatar` (stock fallbacks removed), local `copyToClipboard(text)` -> `copyCoupleCode`, `shareCoupleLink` deleted -> `shareCoupleCode` (share text identical to the old helper). `VerifyInviteDialog.kt`: sender avatar -> `ProfileAvatar`. Diff reviewed against the brief: matches. |
| T9 | 1 | haiku / max | DONE | `LoveHomeScreen.kt`: me-avatar outer Box clickable (`home_my_avatar` -> `openEditProfileDialog()`), 3 avatars -> `ProfileAvatar` (stock `photo-1517841905240` fallback removed), coupled-branch age chip / zodiac pill hidden when age is 0 / zodiac blank (the "$girlAge t" vs "${age}t" spacing is now one format). `ReminderScreen.kt`: header = `ProfileAvatar(girlAvatarUrl)` (hard-coded lh3 URL removed; the old 2dp shadow is gone). `QuoteRotationTest` 6/6 composes the real Home screen. |
| T10 | 1 | haiku / max | DONE | `SettingsScreen.kt` profile row: `ProfileAvatar` 44dp + `effectiveDisplayName.ifBlank { account name }`, whole row clickable (`settings_profile_row`) -> `openEditProfileDialog()`, `heightIn(min = MinTouchBox)`. |
| T11 | 1 | haiku / max | DONE | `EditMyProfileDialog.kt`: blank birthday stays blank, avatar -> `ProfileAvatar`; main-session fix: the haiku version rendered a " • " age/zodiac pill for a blank birthday, now hidden. `OnboardingScreen.kt`: `saveCoupleProfile` with blank birthdays/avatars/zodiacs, age 0, days 0, blank anniversary. `EditProfileDialogTargetsTest` 3/3 (new `blankBirthDate_staysBlankWhenSaved`). |
| T12 | 1 | sonnet / high | DONE | `Dialogs.kt`: `CoupledProfileSummary` (read-only partner info + anniversary, no Save while COUPLED), raw seeds (no invented birthday / anniversary), blank zodiac for a blank birthday, `ProfileAvatar` on 4 sites, one `copyToClipboard` / `shareCoupleCode`. `EditCoupleDialogTest` 4/4 (incl. `profileTab_save_doesNotInventBirthdayOrAnniversary`). |
| wave 4 check | - | main session | DONE | `:app:compileDebugKotlin` BUILD SUCCESSFUL (1m; one old warning at `SettingsScreen.kt:526`, not in the diff). One run of EditProfileDialogTargetsTest 3 + EditCoupleDialogTest 4 + ProfileAvatarTest 4 + InLoveTopBarTest 8 + QuoteRotationTest 6 = 25 tests, 0 failures (BUILD SUCCESSFUL 1m54s, JUnit XML checked). Grep gate: `photo-1534528741775` only in `OnlineProfileRules.kt` under `app/src/main`; no `COUPLE_AVATAR_URL`, no `shareCoupleLink`. |
| T13 | 1 | main session | DONE | `:app:assembleDebug` BUILD SUCCESSFUL (34s). Full `:app:testDebugUnitTest --continue` BUILD SUCCESSFUL in 11m 7s: 150 suites, 1458 tests, 0 failures, 0 errors, 0 skipped (T0 baseline 146 / 1435 / 0: +4 suites, +23 tests; the formerly flaky classes passed this run). Grep gate: `photo-1534528741775`, `photo-1517841905240`, `COUPLE_AVATAR_URL`, `shareCoupleLink` only at `OnlineProfileRules.kt:11`. Two cosmetic edits came after the suite had started (`Dialogs.kt` "Dán mã" chip = 48dp+ touch Box with a smaller chip inside; `LoveDaysCalculatorWidget.kt` blank anniversary shows the hint "Chọn ngày bắt đầu yêu" / "Pick your start date"): `EditCoupleDialogTest` 4 + `QuoteRotationTest` 6 + `EditProfileDialogTargetsTest` 3 re-run, 0 failures (BUILD SUCCESSFUL 1m09s, JUnit XML checked), then `assembleDebug` again (14s) + `adb install -r` + device check of both. LDPlayer smoke (pid alive, crash grep and crash buffer empty at every step): top-bar and Home avatars show the same placeholder; Home me-avatar opens "Hồ sơ của tôi" with a blank birthday and no " • "; renaming updates Home, top bar and dialog; full-screen "Ghép đôi" page tidy; "Cặp đôi" dialog tabs "Hồ sơ" / "Ghép đôi" fit the app scale; "Dán mã" chip sits inside the field and a tap shows the "Bộ nhớ tạm trống" toast; fresh-install onboarding ("An" / "Binh") gives "0 ngày yêu nhau" with blank birthday / anniversary and the hint on Home. NOT testable on the device: signed-in Settings profile row (guest sees only "Khách · trên máy"), COUPLED state and two-device pairing (no Google Play; covered by the `CoupledProfileSummary` test, compile and diff review), `ReminderScreen` header (dead code: imported in `MainActivity.kt` but never called), scroll-dependent screens (swipe/DPAD do not scroll on LDPlayer). No Codex review (owner waiver); nothing committed. |
