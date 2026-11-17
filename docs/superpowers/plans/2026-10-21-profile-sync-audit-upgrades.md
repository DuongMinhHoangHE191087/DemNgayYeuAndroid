# Profile sync audit upgrades (second pass on T0-T13, window scale sweep)

## Goal

A second-pass audit of `2026-10-21-profile-pairing-sync.md` (T0-T13, D1-D11, built, 150 suites / 1458 tests / 0 failures) plus a sweep of every screen and dialog found two defect classes: profile data that is out of sync or duplicated, and windows that ignore `AppDensity`. After this work:

1. **P1, data correctness.** "Me" has one writer (`OnlineCoupleRepository.updateMyProfile`) on every save path, including onboarding. Online "me" equals Room `partner1*` after any save. An edited name survives restore and login. Clearing a field persists blank. Placeholders ("Bạn"/"You", "Người ấy"/"Partner") are applied at display time through one helper and never stored. Unpairing clears the ex-partner. Love days are derived from the anniversary date when read. Bad dates are rejected with a localized message. **Unpairing hides, never deletes (owner rule, built as H1-H4):** every memory, anniversary and the couple profile stay in Room; the ex's rows disappear from every screen and alarm; re-pairing the same person brings them back; pairing someone new shows only the new space. **K, crash hardening:** malformed or hostile remote data, listener errors, unknown outbox rows and cancelled coroutines can no longer crash the app or silently unpair a user.
2. **P2, UI scale and tidiness.** Every `Dialog`, `AlertDialog` and `DropdownMenu` goes through `ui/components/AppWindows.kt`. Each one renders at 0.85 with the in-app locale and real 48dp touch targets, and a guard test stops new raw windows. The date picker and long dialogs scroll on small screens. Touch targets and localized descriptions are fixed, along with small Home, Pairing and Calendar items.
3. **P3, test hygiene.** No fixed sleeps, no Kotlin `assert`, no vacuous assertions. Every new behaviour has a test.

Edits go on top of the dirty working tree. Nothing is reverted, stashed, committed or pushed.

Owner requests (2026-10-21):
- "thực hiện kiểm tra rà soát nâng cấp lại tất cả các phần cho tôi".
- "sau khi huỷ ghép tôi sẽ giữu nguyên data về kỷ niệm mọi thứ chỉ ẩn đi và sau khi nếu cùng ghép đôi lại với người đó sẽ hiển thị lại và nếu ghép đôi với người mới sẽ hiển thị với người mới cho tôi và hỗ trợ việc xử lý tránh crash cho mọi mặt trận giúp tôi thực hiện xử lý cho tôi tất cả". This replaced the old default D12 (unpair clears the partner).
- "thực hiện làm tất cả cho tôi". This is the approval of this whole plan (P1, P2, P3, H, K and U28).

## Process

- Tier L (`dev-workflow`). The audit (reviewers A-E, G) is done; the findings are cited as A.A1, D.D3 and so on, from the session scratchpad `audit/*.md`. Plan by Opus 5.5 (subagent, effort high).
- **Codex: waived by the owner** (2026-10-21). The substitute gate is that Claude reviews every task diff with `git diff -- <owned files>`, then runs the task's targeted tests, then the full suite, then the LDPlayer smoke in U28.
- **Approved by the owner, all tiers, no further gate** ("thực hiện làm tất cả cho tôi", 2026-10-21). P2 depends on nothing in P1 except U1 (helpers). P3 depends on both. H (hide/restore) is built and K (crash hardening) runs after P1.
- Authority stays limited: no commit, push, merge or PR; nothing reverted or stashed; off-limits are `firebase/functions/*`, billing, ads, R2/media providers, KMP/iOS, dependency and SDK versions, and the Room schema.

## What the audit found

| Symptom | Root cause | Tasks |
|---|---|---|
| Home shows "An", the profile dialog opens with "Bạn" (G.G1, on the device DB) | Onboarding and every pre-T0 edit wrote "me" only to Room through `saveCoupleProfile`; the online row stayed `displayName='Bạn', isProfileSetup=0`. The dialog prefills from the online row. Pressing Save writes "Bạn" over the real name. | U1, U6, U8 |
| `saveCoupleProfile` is a second writer of `partner1*` and has no paired guard (A.A4, A.A13, C.C7) | The dialog echoes the VM `boy*` flows back as arguments. The guard checks only `activeRelationship`, while the dialog checks the status. | U6 |
| An edited name reverts at every restore or login (A.A1 = D.D1) | `syncOnlineUserWithAccount` copies `account.displayName` over the existing online row, and `UserAccountEntity.displayName` is never updated on a profile save. | U3, U4 |
| A signed-in card shows "Vô danh", the "Not set" badge and banner disappear, and placeholders differ per screen (G.G5, A.A6, B.B5, D.D3, D.D9, D.D10) | The VM collectors read raw `displayName`; there are seven different literals ("Bạn", "Người Thương", "Người thương", "người ấy", "Người ấy", "Anh", "Em"), some of them stored. | U1, U3, U6, U9, U10, U21, U22 |
| A birthday, zodiac or partner name cannot be cleared (A.A5, C.C3) | `ifEmpty { old }` and `ifBlank { "Người thương" }` fallbacks in `saveCoupleProfile`. | U6 |
| With no Room row, title and anniversary edits "succeed" but are lost, or store "Hôm nay" (A.A10, A.A11, C.C12) | The writers update only when the row exists, build it from UI placeholders, and toast regardless. | U7 |
| After unpair the ex-partner stays on the card (A.A9) | Nothing cleared the `girl*` flows or hid the ex's rows on COUPLED -> SINGLE. Owner rule: hide, never delete. | H1-H4 (built), U6 |
| A concurrent pairing snapshot can be undone by a profile save (A.A3) | `updateMyProfile` copies the cached `_currentUser.value`. | U3 |
| The accept path writes "InLove" as the title, other paths write "Hành Trình Yêu Thương" (A.A7) | Two title defaults. | U1, U3 |
| The day counter and the anniversary alarm show yesterday's number (A.A8, D.D5) | `loveDays` is computed once, at write time. | U2, U5, U7, U8 |
| Future, unparsable or 2-digit-year dates are accepted; the manual date dialog shows no error (A.A12, C.C2, C.C5, C.C6) | No validation in the VM; `isError` is never displayed. | U2, U7, U11 |
| Account deletion can recreate the deleted online row (A.A17) | `setCurrentUserId("")` runs after the deletes. | U4 |
| Accepting an invite on a device that never saw the sender ends with "Không tìm thấy người gửi lời mời!" (A.A2, acceptor half; static reading, not run on two phones) | `acceptSetLoveInvite` requires the sender's `online_users` row, and no code path persists a row for another uid (V21). The invite already carries the sender's name, avatar, birth date, age, zodiac and bio. | U3 |
| "Kỷ niệm mới", the Calendar "+" menu and 37 of 43 windows render about 18% too big (G.G3, E.E1, E.E3, E.E8, C.C1) | A window re-provides `LocalDensity`, `LocalContext` and `LocalConfiguration` from its own view. Only 6 sites re-apply `AppDensity`, and only the Paywall re-applies the in-app locale (E.E7). | U12-U24 |
| Wrapped 48dp controls shrink to 40.8 real dp (E.E6, E.E11, E.E12, B.B9, C.C9) | `LocalMinimumInteractiveComponentSize` stays 48 (logical) under the 0.85 density. | U12, U13-U23 |
| The date picker and long dialogs clip on small screens (E.E2, E.E4, E.E5, E.E9, E.E10) | Fixed-height content with no scroll; the M3 AlertDialog text slot does not scroll. | U12, U13, U17, U21 |
| Small Home, Pairing, Calendar and Settings defects (B.B1, B.B7, B.B8, B.B10, B.B11, C.C4, C.C8, C.C16, G.G2/B.B6, G.G4, lint) | See D18 and the task details. | U14, U15, U20-U23 |
| Flaky or weak tests (A.A18, the C test gaps) | A fixed 3 s settle ×6, Kotlin `assert`, a vacuous Save assertion, a busy-wait, missing state tests. | U3, U6, U25-U27 |

## Assumptions

Line numbers are as of 2026-10-21. Every task anchors its edits on quoted code, not on line numbers. Paths are relative to `app/src/main/java/com/example/` (main) or `app/src/test/java/com/example/` (test).

VERIFIED (read in code):

- V1. `AppDensity` (`ui/theme/Theme.kt` 85-114) is `CompositionLocalProvider(LocalDensity provides Density(base.density * UI_SCALE, base.fontScale))`. The values are `private const val UI_SCALE = 0.85f` and `val MinTouchBox = ceil(48f / UI_SCALE).dp` (57dp, pinned by `ThemeTokensTest`). The theme root is `AppDensity { MaterialTheme(...) }`.
- V2. Window sites (grep of main):
  - `Dialog(`: MainActivity 605, 639; AppPinLockScreen 371; AppModals 111, 329, 763; AccountSecurityDialogs 91, 257, 368; AuthScreen 1115; Dialogs 582, 766, 929; EditMyProfileDialog 118; MilestoneBadgeTracker 667, 1134; Material3DatePicker 515; LoveDaysCalculatorWidget 651; AnniversaryMemoriesWidget 901, 1160; GiftScreen 920; VerifyInviteDialog 88; MemoriesGridScreen 682, 1078, 1243.
  - `DropdownMenu(`: CalendarScreen 415, 1458; AuthScreen 900.
  - `AlertDialog(`: 14 sites (table in D13).
  - There is no `Popup(`, `ExposedDropdownMenu`, `BasicAlertDialog`, `DatePickerDialog` or `TimePickerDialog`. `ModalBottomSheet` appears only in NotificationsSheet 104, which already wraps `AppDensity` (pinned by `NotificationItemsTest`). There is no `spikes/` directory under `app/src/main/java`.
- V3. Already-wrapped sites that must drop their own `AppDensity` after migration:
  - Dialogs 929-933 (EditCouple);
  - Dialogs 303/321/323/350 (AddMilestone slots);
  - EditMyProfileDialog 118-119;
  - MainActivity 603-619 (Pairing host);
  - MainActivity 638-659 (Paywall, which also re-applies `*compositionLocals`; keep that provider).
- V4. M3 1.3.0 (javap on the cached aar):
  - `AlertDialog(onDismissRequest, confirmButton, modifier, dismissButton, icon, title, text, shape, containerColor, iconContentColor, titleContentColor, textContentColor, tonalElevation, properties)`;
  - `LocalMinimumInteractiveComponentSize: ProvidableCompositionLocal<Dp>`.
- V5. The AlertDialog text slot is height-bounded, because Dialogs 2123, 2533 and 2766 already run `verticalScroll` inside it without crashing. A `verticalScroll` nested in another vertical scroll throws the infinite-height `IllegalStateException`. Dialogs.kt has only `LazyRow` (400, 476), no `LazyColumn`, inside the dialogs.
- V6. MainActivity 158-188 builds `configuration` (in-app locale) and `context = object : ContextWrapper(rawContext) { getResources() = localizedResources }`. It then provides `LocalConfiguration`, `LocalContext` and `LocalActivityResultRegistryOwner`. `findActivity()` and `resolveHostActivity` unwrap `ContextWrapper`s, so re-providing this context inside a window is safe. `rememberLauncherForActivityResult` already works inside dialogs today (EditMyProfileDialog 201, Dialogs 1030), so the registry owner flows into windows.
- V7. `effectiveDisplayName` (Entities.kt 240-241) is "" when `!isProfileSetup`, blank or "Vô danh".
- V8. The VM:
  - flows default `_boyName = "Bạn"` (263) and `_girlName = "Người Thương"` (278);
  - `resetBoyCard`/`resetGirlCard` (351/360) fall back to the same literals;
  - the online collectors (675, 691) use raw `displayName`, with the blank test `name.isBlank() || name == "Bạn"`;
  - the relationship collector (699-707) handles only `rel != null`;
  - `saveCoupleProfile` (1001-1070) has the fallbacks 1016/1017/1019/1021/1022/1024 and the title "Hành Trình Yêu Thương";
  - `updateLoveTitle` (1075-1103) builds a missing row from placeholders;
  - `calculateLoveDaysFromDate` (1110-1135) returns `_loveDays.value` on failure or for a future date;
  - `setAnniversaryAndRecalculateDays` (1141-1170) saves only `if (current != null)` but always toasts;
  - `updateCoupleInfo` (1255-1280) has no caller;
  - `updateMyProfile` (1842-1872) has line 1862 `ifEmpty { "Bạn" }`;
  - notification sentences use `?: "người ấy"` (378, 721, 1683, 1713; `?:` does not catch "").
- V9. Callers:
  - `saveCoupleProfile`: only Dialogs 1505 and OnboardingScreen 279;
  - `updateLoveTitle`: LoveHome 1535 and Settings 753;
  - `setAnniversaryAndRecalculateDays`: LoveHome 1093, ReminderWiringSettingsTest 376 and ProfileSyncViewModelTest 146, both with the past date "01/01/2025".
- V10. OnlineCoupleRepository:
  - `updateMyProfile` (211-258) starts from `_currentUser.value` and mirrors `partner1Name = updatedMe.effectiveDisplayName.ifEmpty { "Bạn" }` (247), only when the row exists;
  - `acceptSetLoveInvite` (402-526) writes `partner1/2Name = p.effectiveDisplayName` and `loveTitle = ... ?: "InLove"`;
  - `confirmBreakup` (607-651) and SyncCoordinator 117-135 set me to SINGLE and never touch Room `partner2*`;
  - no code syncs `loveTitle` between devices (grep).
- V11. AuthRepository:
  - `syncOnlineUserWithAccount` (1082-1124) sets `displayName = account.displayName` on an existing row;
  - `init { scope.launch { restoreSession() } }` (110-113);
  - account deletion calls `setCurrentUserId("")` at 1061, after the deletes at 1045-1056;
  - readers of `UserAccountEntity.displayName`: the PIN greeting (AppPinLockScreen 149) and the login toasts (416, 932);
  - `InLoveDao` has `getUserAccountByUid` (376) and `updateUserAccount` (385).
- V12. `AlarmNotificationScheduler` 437-454 builds alarm 400001 from `profile.loveDays` and `profile.loveTitle`.
- V13. `ProfileUtils` (ui/util):
  - `calculateLoveDays(startDateMillis)` returns 0 for a future date and counts inclusively (+1);
  - `calculateLoveDays(startDateStr)` returns 1 on blank;
  - `formatZodiacDisplay(zodiac, isEnglish)` returns "" for blank or "Chưa rõ".
  - `ProfileUtilsThreadSafetyTest` exists, so new helpers create their own `SimpleDateFormat`.
- V14. ManualDateInputDialog (LoveDaysCalculatorWidget 645-760) sets `isError` but never shows it.
- V15. `InLoveDatePickerField(value, onValueChange, label, placeholder, modifier, dialogTitle, quickPresets, helperText, singleLine, isEnglish, testTag)` is at Material3DatePicker 966. The picker Card (515) has no scroll.
- V16. Further verified sites:
  - PairingScreen 1172-1188 is a dead `EditMyProfileDialog` host (nothing calls it);
  - `CoupleCodeShare.shareCoupleCode` has no try/catch;
  - CalendarScreen 72 imports `LocalMinimumInteractiveComponentSize`, unused;
  - CalendarScreen 429-460 duplicates the MONTH/WEEK shift logic in prev and next;
  - `String.format("%02d")` with no locale appears at CalendarScreen 231 and Dialogs 2227/2255;
  - "MÁY" is at LoveHome 1057;
  - NotificationsSheet 127 is `.size(40.dp)`.
- V17. `lifecycle-runtime-compose` 2.8.7 is a dependency (app/build.gradle.kts:193), so `LifecycleResumeEffect` is available.
- V18. Test harnesses:
  - VM tests put an in-memory DB into `AppDatabase.INSTANCE` (ProfileSyncViewModelTest 40-57; fixed 3 s settle at 71-80);
  - repository tests use `OnlineCoupleRepository(db.inLoveDao(), context, scope, useFirestore = false)`;
  - OnlineCoupleAcceptInviteTest uses Kotlin `assert(` (68-70 and more);
  - source-scanning tests read paths relative to the module (`File("src/test/java/...")` in AgeGoldenTest 75);
  - Robolectric never idles with an `OutlinedTextField` inside a `Dialog`, so tests compose the internal content composables.
- V19. `ReminderScreen.kt` is dead code (A, D reviewers). It is not touched; see Questions.
- V20. The love-milestone scheduler gets its partner name from one producer, `InLoveViewModel.loveMilestoneArgs()` (378), plus the VM calls at 721, 1683 and 1713. All four are in the D16 `.ifBlank { "người ấy" }` list (U6), so a blank name cannot reach `LoveAnniversaryMilestoneScheduler` after U6 and D.D6 needs no extra task.
- V21. `OnlineUserEntity` rows are inserted only for the signed-in or guest user: `dao.insertOnlineUser` has three callers (OnlineCoupleRepository 151, AuthRepository 589 and 1106), `insertOnlineUsers` has none, and the `OnlineUserEntity(` constructors at OnlineCoupleRepository 303 and 352 are in-memory stubs. `acceptSetLoveInvite` (410-411) returns "Không tìm thấy người gửi lời mời!" when the sender row is missing, and `SyncCoordinator.handleInviteSnapshot` (145-171) stores only the invite. Every existing accept test pre-inserts `uid_a`. So on a second phone the accept fails before it reaches the Cloud Function. Static reading only; not run on two devices.

UNVERIFIED (each task that relies on one checks it first and reports BLOCKED if it is wrong):

- U-a. The resolved material3 version. BOM 2024.09.00 maps to 1.3.0, and the cache also holds 1.4.0 and 1.5.0-alpha22. U0 prints it.
- U-b. Whether Robolectric idles an `AppDialog` holding only `Text`. U12 has a fallback.
- U-c. Whether E.E4, E.E5 and E.E10 really clip on a 360x640dp screen at font 1.3. U0 checks where the dialog is reachable.
- U-d. The Calendar "tidy" intent. It is read as "merge the duplicated prev/next logic", with no visual change.
- U-e. Whether `OnlineCoupleRepository.updateMyProfile` is `suspend`. U6 reads it.
- U-f. Whether MainActivity already collects `boyName`, `boyBirthDate` and `boyAvatarUrl`. U8 reads it.
- U-g. The exact `DropdownMenu` parameters at the three call sites. U12 mirrors only what they use.
- U-h. C.C8g (dismiss only on success) is possible only if the call site can await a Boolean. U21 checks.

## Decisions

Earlier decisions D1-D11 stand. New ones:

- **D12. Unpair hides, never deletes (owner rule, supersedes the old "clear partner2*" default). BUILT as H1-H4.**
  - Nothing is cleared. `couple_profile`, `shared_memories` and `anniversary_dates` keep every row. `clearPartnerAfterUnpair` is NOT built.
  - Visibility (no schema change). A row with a `relationshipId` is visible when that id is in `visibleRelationshipIds(online_relationships)`: every ACTIVE/PENDING_BREAKUP relationship, plus every TERMINATED one whose `{user1, user2}` equals the pair of a current relationship. No current relationship (single) hides every scoped row. Rows with a null `relationshipId` (solo, private) are always visible. Server ids are fresh per pairing (`acceptCoupleInvite`), so "same person" is decided by the uid pair, never by the id.
  - Applied in: the `InLoveRepository` flows `sharedMemories` / `anniversaryDates` and `getAnniversaryDatesList()`; the alarm paths (`ReminderAlarmReceiver`, the `AlarmNotificationScheduler` schedule loop); the VM Room collector (a signed-in user whose relationships are all TERMINATED shows no partner card, `anniversaryDate = "Hôm nay"`, `loveDays = 1`); `SyncWorker` parks outbox rows of a non-ACTIVE relationship (kept, not pushed, not retried, not a failure).
  - Ceiling: rows restored by a same-partner re-pair stay local, because the server accepts writes only for an ACTIVE relationship and the parked outbox rows are not re-keyed to the new relationship id (`ponytail:` re-key the parked rows on re-pair if the owner wants those memories on the partner's phone). `couple_profile.loveTitle` is not hidden.
  - K-series (below) and H5 cover the remaining surfaces.
- **D13. One file of window wrappers, `ui/components/AppWindows.kt`.**
  - `@Composable internal fun AppWindowLocals(context, configuration, content)` provides, in order: `LocalContext`, `LocalConfiguration`, then `AppDensity`, then `LocalMinimumInteractiveComponentSize provides MinTouchBox`.
  - `AppDialog(onDismissRequest, properties = DialogProperties(), content)` captures `LocalContext.current` and `LocalConfiguration.current` at the call site, then calls `Dialog(...) { AppWindowLocals(ctx, cfg, content) }`.
  - `AppAlertDialog(...)` takes the full M3 1.3.0 parameter list with the M3 defaults, plus a required `scrollableText: Boolean`. M3 owns the AlertDialog window, so the wrapper wraps each non-null slot (icon, title, text, confirm, dismiss) in `AppWindowLocals`. When `scrollableText` is true, the text goes in `Column(Modifier.verticalScroll(rememberScrollState()))`. `ponytail:` the frame padding and 280dp minimum width stay at window density; if it looks off, rebuild on `BasicAlertDialog`.
  - `AppDropdownMenu(expanded, onDismissRequest, modifier, offset, ..., content: ColumnScope.() -> Unit)` calls `DropdownMenu(...) { val scope = this; AppWindowLocals(ctx, cfg) { scope.content() } }`. A provider emits no layout node, so items stay in the menu Column.
  - `Theme.kt`: `private const val UI_SCALE` becomes `internal const val UI_SCALE`. No other change; pages are not affected.
  - `ModalBottomSheet` is not migrated (NotificationsSheet keeps its `AppDensity`; only E.E11 is fixed).
  - AlertDialog sites and `scrollableText`:

| Site | Text slot today | scrollableText |
|---|---|---|
| Dialogs 197 AddReminder | Column, no scroll | true |
| Dialogs 297 AddMilestone | `AddMilestoneFormBody` scrolls (387); slot `AppDensity` wraps at 303/321/323/350 are removed | false |
| Dialogs 533 AddChecklist | one OutlinedTextField | true |
| Dialogs 1937 DeleteConfirmation | Column, has icon | true |
| Dialogs 2085 SetAlarm | verticalScroll 2123 | false |
| Dialogs 2369 EditLoveTitle | Column | true |
| Dialogs 2511 AddAnniversary | verticalScroll 2533 | false |
| Dialogs 2744 AddGiftReminder | verticalScroll 2766 | false |
| GiftScreen 161 | Text | true |
| GiftScreen 1692 | Column of fields | true |
| PairingScreen 670 | Text | true |
| SettingsScreen 653 | Text | true |
| SettingsScreen 691 | Column | true |
| AuthScreen 968 | Text | true |

  Rule for the migrating task: if the text lambda contains `verticalScroll`, `LazyColumn` or a composable that does (read it), use `false`, otherwise `true`.

- **D14. Love days are derived when read.**
  - New pure `ProfileUtils.loveDaysOn(anniversaryDate: String, nowMillis: Long = System.currentTimeMillis()): Int?` returns null for blank, unparsable or future input. It parses the four VM patterns ("dd/MM/yyyy", "d/M/yyyy", "dd-MM-yyyy", "yyyy-MM-dd"; non-lenient, new formatter per call) and returns `calculateLoveDays(parsedMillis)`, so the values equal today's `calculateLoveDaysFromDate` for past dates.
  - VM `calculateLoveDaysFromDate` delegates to it (`?: _loveDays.value`, unchanged contract).
  - The Room collector sets `_loveDays = loveDaysOn(profile.anniversaryDate) ?: profile.loveDays` on the unpaired path.
  - New VM `fun refreshLoveDays()` re-runs the relationship collector's day expression (extracted to a private fun) when paired, and `loveDaysOn(_anniversaryDate.value)` otherwise.
  - MainActivity calls it from `LifecycleResumeEffect(Unit) { viewModel.refreshLoveDays(); onPauseOrDispose {} }`.
  - The alarm text uses `loveDaysOn(profile.anniversaryDate, triggerAtMillis) ?: profile.loveDays`, with the trigger computed first.
  - `ponytail:` there is no midnight tick while the app stays in the foreground; add a `combine` with a day tick if the owner sees it.
- **D15. One path for the me-fields.**
  - `saveCoupleProfile` drops the `boy*` parameters and keeps `partner1*` from the existing row ("" when there is none). New signature: `saveCoupleProfile(girl, girlBirth, girlAvatar, girlA, girlZod, title, days, anniversary)`.
  - It refuses with a localized toast when `isPairedNow()`, a new private `activeRelationship.value != null || currentOnlineUser.value.status == COUPLED`. EditCoupleDialog's `isCoupled` (Dialogs 1011) uses the same predicate (C.C7).
  - Delete `updateCoupleInfo`.
  - New VM `completeOnboardingProfile(me, partner, title)` is a single coroutine: create or update the row (`partner2Name = partner`, title, `partner1*` untouched), then run the same call the VM's `updateMyProfile` makes (`onlineRepo.updateMyProfile(me, "", "", currentGender, currentBio)`) when `me` is not blank, so the repository mirror fills `partner1*`. No "Anh"/"Em" defaults are stored.
  - New pure `OnlineUserEntity.withCardFallback(name, birth, avatar)` in `ui/util/OnlineProfileRules.kt` is display-only. When `effectiveDisplayName` is blank and `name` is not, it returns `copy(displayName = name, isProfileSetup = true)`. Birth and avatar fall back when blank. MainActivity passes `currentOnlineUser.withCardFallback(boyName, boyBirthDate, boyAvatarUrl)` to `EditMyProfileDialog` and uses its `avatarUrl` for the top bar. The dialog prefill uses `currentUser.effectiveDisplayName`.
  - Why this over "sync after save": the dialog's me-fields are only an echo of the VM flows, and `updateMyProfile` already mirrors into Room. Removing the echo leaves one writer and no second sync to keep correct. Legacy rows (G.G1) heal on the first dialog Save, which now pre-fills the card's real name.
  - `OnlineCoupleRepository.updateMyProfile` re-reads `dao.getOnlineUserByUidSync(uid) ?: _currentUser.value` first (A.A3). When the profile is set up and the names differ, it also does `dao.getUserAccountByUid(uid)?.let { dao.updateUserAccount(it.copy(displayName = finalName)) }` (A.A1). Ceiling: the in-memory auth account stays stale until the next restore; the PIN greeting reads the DB.
  - `AuthRepository.syncOnlineUserWithAccount` keeps the existing name: `displayName = if (existing.effectiveDisplayName.isNotBlank()) existing.displayName else account.displayName`.
- **D16. One placeholder set, applied at display time.**
  - Add `displayMeName(name, isEnglish) = name.ifBlank { if (isEnglish) "You" else "Bạn" }`, `displayPartnerName(name, isEnglish) = name.ifBlank { if (isEnglish) "Partner" else "Người ấy" }` and `const val DEFAULT_LOVE_TITLE = "Hành Trình Yêu Thương"` to OnlineProfileRules.kt.
  - Stored and flow values are "" for "unknown":
    - the VM flows default to "" (263, 278), as do `resetBoyCard`/`resetGirlCard` and 1862;
    - the collectors use `effectiveDisplayName` with the blank test `isBlank()`;
    - the repository mirror (247) drops `.ifEmpty { "Bạn" }`;
    - the accept path keeps `effectiveDisplayName` ("", consistent) and uses `DEFAULT_LOVE_TITLE`.
  - Display sites call the helpers. Notification sentences turn `?: "người ấy"` into `.ifBlank { "người ấy" }`, keeping the lowercase in-sentence form (ReminderTexts already does this).
  - A blank title still means `DEFAULT_LOVE_TITLE`. Only names, birthdays and zodiacs may be cleared.
  - Out of scope: the data defaults in Entities 122/181 and GiftSuggestionGenerator 22; legacy stored "Bạn"/"Người thương" values, which display the same as the placeholder anyway.
  - No `strings.xml` edits anywhere in this plan. Text is localized with the existing `isEnglish` / `tr()` ternaries.
- **D17. Date validation.**
  - New pure `ProfileUtils.normalizeAnniversaryInput(text: String, nowMillis: Long = now): String?`. It accepts `d{1,2}[/-]d{1,2}[/-]d{4}` or `yyyy-M-d`, non-lenient, with year >= 1900 and the date not after today (date-only compare). It returns "dd/MM/yyyy", or null for blank or invalid input.
  - The VM `setAnniversaryAndRecalculateDays` and the anniversary of `saveCoupleProfile` (when not blank) use it; on null they toast `tr("Ngày không hợp lệ (dd/MM/yyyy, không ở tương lai)", "Invalid date (dd/MM/yyyy, not in the future)")` and return.
  - ManualDateInputDialog uses it for `isError` with a localized `supportingText`.
  - The profile birthday becomes `InLoveDatePickerField` (blank allowed).
- **D18. Small UI decisions.**
  - Home zodiac pills go through `formatZodiacDisplay` (B.B1).
  - Names get `maxLines = 1, overflow = Ellipsis` (B.B7).
  - The partner birthday shows only when not blank (B.B8).
  - The avatar and rows get `onClickLabel`/`Role.Button` (B.B11).
  - `ProfileAvatar` gets a placeholder and a tinted error painter (B.B10).
  - "MÁY" becomes "Ngoại tuyến" (G.G4).
  - CoupleCodeShare gets `runCatching` plus a toast (C.C16).
  - `String.format(Locale.ROOT, "%02d", ...)` everywhere.
  - Calendar prev/next uses one local `shift(sign)` lambda.
  - The two Home age chips use `ProfileUtils.calculateAge(birth).takeIf { it > 0 } ?: storedAge` where the birthday is in scope (A.A16 "only if trivial"; the DAY_OF_YEAR fix is not done).

## Execution rules (apply to every task)

- **Gradle.** One Gradle process at a time. In a PARALLEL wave the subagents edit files and write tests but do not run Gradle. The main session then runs `:app:compileDebugKotlin` once, then each task's test command in turn, and sends failures back to the owning task. SOLO tasks may run Gradle and work test-first.
- **Dirty tree.** The working tree is dirty with other sessions' work (R2 media, billing, KMP docs). Use `Edit` anchored on quoted code. Never `git checkout` / `restore` / `stash` / `reset`, never rewrite a whole file, never commit or push. If an anchor moved or the plan looks wrong, stop and report BLOCKED.
- **Ownership.** A task touches only its owned files, and ownership is exclusive within a wave. Hot files (InLoveViewModel, Dialogs, MainActivity, LoveHomeScreen, OnlineCoupleRepository, AuthRepository) are owned by at most one task per wave. No task edits `strings.xml`.
- **Off-limits.** `firebase/functions/*`, `billing/*`, ads, R2/media providers, KMP/iOS, dependency or SDK versions, the Room schema (no migration).
- **Reports.** Subagent report: 10 lines max (`STATUS: DONE|BLOCKED`, files changed, tail of the test output, blockers).
- **Models.** Pass `model` and `effort` explicitly: haiku with effort max for mechanical work, sonnet with effort high for logic. Escalation:
  1. haiku fails twice, then sonnet;
  2. sonnet fails twice, then an Opus advisor (read-only, concrete suggestions);
  3. still failing, stop and ask the owner.
  Record the attempts in the Execution log.
- **Commands** (repo root, PowerShell): `.\gradlew.bat :app:compileDebugKotlin`, `.\gradlew.bat :app:testDebugUnitTest --tests "<fqcn>"`, `.\gradlew.bat :app:testDebugUnitTest`, `.\gradlew.bat :app:assembleDebug`, `.\gradlew.bat :app:lintDebug`.

## Tasks

| id | tier / wave / mode | files (owned) | change | test or command | depends on | model |
|---|---|---|---|---|---|---|
| U0 | P1 / 0 / SOLO | none (Execution log only) | Baseline: full suite, lint, assembleDebug, material3 version, small-screen screenshots | full suite; `:app:lintDebug`; `:app:dependencies --configuration debugRuntimeClasspath` | - | haiku, max |
| U1 | P1 / 1 / PARALLEL | main `ui/util/OnlineProfileRules.kt`; test `ui/util/OnlineProfileRulesTest.kt` | D15 `withCardFallback`, D16 `displayMeName`, `displayPartnerName`, `DEFAULT_LOVE_TITLE` | `--tests "com.example.ui.util.OnlineProfileRulesTest"` | U0 | haiku, max |
| U2 | P1 / 1 / PARALLEL | main `ui/util/ProfileUtils.kt`; test `ui/util/ProfileUtilsDatesTest.kt` (new) | D14 `loveDaysOn`, D17 `normalizeAnniversaryInput` | `--tests "com.example.ui.util.ProfileUtilsDatesTest"`, `ProfileUtilsThreadSafetyTest` | U0 | sonnet, high |
| U3 | P1 / 2 / PARALLEL | main `data/repository/OnlineCoupleRepository.kt`; test `data/repository/OnlineCoupleProfileSlotTest.kt`, `data/repository/OnlineCoupleAcceptInviteTest.kt` | A.A3 re-read; A.A1 account name sync; mirror drops "Bạn"; A.A7 title default; A.A2 acceptor half (missing sender row built from the invite); replace Kotlin `assert` | both test classes, `OnlineCoupleRepositoryPairingTest` | U1 | sonnet, high |
| U4 | P1 / 2 / PARALLEL | main `data/repository/AuthRepository.kt`; test `data/repository/AuthRepositoryTest.kt` | A.A1 keep edited name; A.A17 order; `internal val restoreJob` | `--tests "com.example.data.repository.AuthRepositoryTest"` | U0 | sonnet, high |
| U5 | P1 / 2 / PARALLEL | main `alarm/AlarmNotificationScheduler.kt` | D14 alarm 400001 days from `loveDaysOn` at the trigger time | compile; `--tests "com.example.alarm.*"` | U2 | haiku, max |
| U6 | P1 / 3 / SOLO, test-first | main `ui/viewmodel/InLoveViewModel.kt`, `ui/screens/Dialogs.kt` (only 1011 `isCoupled` and the Save call 1503-1519), `ui/screens/OnboardingScreen.kt` (only the finish block 270-296); test `ui/viewmodel/ProfileSyncViewModelTest.kt` | D15, D16 VM side, A.A5/C.C3 clearing, C.C7, settle fix (D12 is already built as H1-H4: keep the `relationshipEnded` / `ownsGirl` / `ownsDate` hunks and `resetGirlCard`'s `takeIf`; build no `clearPartnerAfterUnpair`) | `ProfileSyncViewModelTest`, `EditCoupleDialogTest`, `ReminderWiringSettingsTest` | U1, U3, U4 | sonnet, high |
| U7 | P1 / 4 / SOLO, test-first | main `ui/viewmodel/InLoveViewModel.kt`; test `ui/viewmodel/ProfileSyncViewModelTest.kt` | D14 VM side, D17 VM side, A.A10/A.A11 no-row creation, toast after write | same as U6 | U2, U6 | sonnet, high |
| U8 | P1 / 5 / PARALLEL | main `MainActivity.kt`, `ui/screens/EditMyProfileDialog.kt` (prefill line only); test `ui/screens/EditProfileDialogTargetsTest.kt` | D15 `withCardFallback` for the dialog and top bar; prefill `effectiveDisplayName`; `LifecycleResumeEffect` refresh | `EditProfileDialogTargetsTest`, `InLoveTopBarTest` | U1, U7 | haiku, max |
| U9 | P1 / 5 / PARALLEL | main `ui/screens/LoveHomeScreen.kt` | D16 display helpers at 746, 860, 1009, 1086-1087, 1098/1100 | compile; `QuoteRotationTest` | U1, U6 | haiku, max |
| U10 | P1 / 5 / PARALLEL | main `ui/components/MilestoneBadgeTracker.kt` | D16 helpers at 426-427, 650-651, 1121-1122 (wrap at collect) | compile | U1, U6 | haiku, max |
| U11 | P1 / 5 / PARALLEL | main `ui/components/LoveDaysCalculatorWidget.kt`; test `ui/components/ManualDateInputTest.kt` (new) | C.C2, C.C6, D17 in ManualDateInputDialog | `--tests "com.example.ui.components.ManualDateInputTest"` | U2 | haiku, max |
| U12 | P2 / 6 / SOLO, test-first | main `ui/components/AppWindows.kt` (new), `ui/theme/Theme.kt` (`UI_SCALE` visibility only); test `ui/components/AppWindowsTest.kt` (new) | D13 wrappers | `--tests "com.example.ui.components.AppWindowsTest"`, `ThemeTokensTest`, `NotificationItemsTest` | U0 | sonnet, high |
| U13 | P2 / 7 / PARALLEL | `ui/screens/AppPinLockScreen.kt`, `ui/screens/AccountSecurityDialogs.kt`, `ui/screens/AppModals.kt` | migrate 7 Dialogs; E.E4/E.E10 scroll | compile | U12 | haiku, max |
| U14 | P2 / 7 / PARALLEL | `ui/screens/AuthScreen.kt`, `ui/screens/SettingsScreen.kt` | migrate 1 Dialog, 1 DropdownMenu, 3 AlertDialogs; B.B7, B.B11 on the Settings row | compile | U12 | haiku, max |
| U15 | P2 / 7 / PARALLEL | `ui/screens/CalendarScreen.kt` | migrate 2 DropdownMenus; drop the unused import; `shift(sign)` tidy; `Locale.ROOT` | compile; `--tests "com.example.ui.screens.*Calendar*"` | U12 | haiku, max |
| U16 | P2 / 7 / PARALLEL | `ui/components/MilestoneBadgeTracker.kt`, `ui/components/AnniversaryMemoriesWidget.kt`, `ui/screens/GiftScreen.kt`, `ui/screens/MemoriesGridScreen.kt`, `ui/screens/VerifyInviteDialog.kt` | migrate 9 Dialogs and 2 AlertDialogs; 48dp/32dp closes to `MinTouchBox`; B.B9 localized descriptions in VerifyInvite | compile | U10, U12 | haiku, max |
| U17 | P2 / 7 / PARALLEL | `ui/components/Material3DatePicker.kt`; test `DatePickerTest` (existing file) | migrate; E.E2 scroll + `heightIn(max)`; C.C9 field icon to `MinTouchBox`; nav buttons (E.E12) | `DatePickerTest` | U12 | sonnet, high |
| U18 | P2 / 7 / PARALLEL | `ui/components/LoveDaysCalculatorWidget.kt`, `ui/components/NotificationsSheet.kt` | migrate ManualDateInputDialog; C.C9 banner buttons `heightIn(min = MinTouchBox)`; E.E11 | compile; `ManualDateInputTest`, `NotificationItemsTest` | U11, U12 | haiku, max |
| U19 | P2 / 7 / PARALLEL | `ui/screens/EditMyProfileDialog.kt`; test `EditProfileDialogTargetsTest` | migrate, drop `AppDensity`; B.B3 `InLoveDatePickerField`; B.B9 targets and description | `EditProfileDialogTargetsTest` | U8, U12 | haiku, max |
| U20 | P2 / 7 / PARALLEL | `ui/components/CommonComponents.kt`; test `ui/components/ProfileAvatarTest.kt` | B.B10 placeholder and tinted error | `ProfileAvatarTest`, `InLoveTopBarTest` | U0 | haiku, max |
| U21 | P2 / 8 / PARALLEL | `ui/screens/Dialogs.kt`; test `ui/screens/EditCoupleDialogTest.kt` | migrate 3 Dialogs and 8 AlertDialogs (D13 table); drop `AppDensity` (929-933, 303/321/323/350); C.C4; C.C8 a-f; placeholders 1250/1657/2736/2788; `Locale.ROOT` 2227/2255; E.E5 only if U0 saw clipping | `EditCoupleDialogTest`, `AddMilestoneExamplesTest` | U6, U12 | sonnet, high |
| U22 | P2 / 8 / PARALLEL | `MainActivity.kt`, `ui/screens/PairingScreen.kt`, `ui/util/CoupleCodeShare.kt` | migrate the 2 MainActivity hosts and 1 Pairing AlertDialog; delete the dead host (G.G2/B.B6); placeholders 502/591; B.B9 descriptions; C.C16 | compile | U8, U12 | haiku, max |
| U23 | P2 / 8 / PARALLEL | `ui/screens/LoveHomeScreen.kt` | B.B1, B.B7, B.B8, B.B11, G.G4, A.A16 age chips | compile; `QuoteRotationTest` | U9 | haiku, max |
| U24 | P2 / 9 / SOLO | test `ui/components/DensityWindowGuardTest.kt` (new) | Raw-window guard plus an `AppDensity` allow-list | `--tests "com.example.ui.components.DensityWindowGuardTest"` | U13-U23 | haiku, max |
| U25 | P3 / 10 / PARALLEL | test `ui/screens/EditCoupleDialogTest.kt` | C test gaps | that class | U21 | sonnet, high |
| U26 | P3 / 10 / PARALLEL | test `ui/viewmodel/ProfileSyncViewModelTest.kt` | remaining A.A18 transitions | that class | U7 | sonnet, high |
| U27 | P3 / 10 / PARALLEL | test `data/repository/OnlineCoupleProfileSlotTest.kt` | thicker accept test | that class | U3 | haiku, max |
| H1-H4 | H / DONE before this plan ran | `data/repository/RelationshipVisibility.kt` (new), `data/db/InLoveDao.kt` (`observeRelationships`), `data/repository/InLoveRepository.kt`, `alarm/ReminderAlarmReceiver.kt`, `alarm/AlarmNotificationScheduler.kt`, `ui/viewmodel/InLoveViewModel.kt` (Room collector), `data/sync/SyncWorker.kt` (`pushOne` parks); test `data/repository/RelationshipVisibilityTest.kt` | D12 hide/restore | full suite was green (1458 + 3) | - | sonnet, high |
| H5 | H / wave K0 / PARALLEL | read-only audit first, then the owner of each file found: `alarm/LoveAnniversaryMilestoneScheduler.kt`, `ui/components/MilestoneBadgeTracker.kt`, `ui/components/AnniversaryMemoriesWidget.kt`, home widgets, the VM memory / anniversary creation scoping | Find ex-data leaks outside the repository flows (stale milestone alarms, badges, widgets) and hide them with `visibleIn` / `visibleIds`; add a Room-backed `InLoveRepository` test (same-partner restore, new-partner isolation); the `SyncWorker` parked-row test moved to K2 (K2 owns `SyncWorkerTest`) | `--tests` for each new test | H1-H4 | sonnet, high |
| K1 | K / wave K1 / PARALLEL | main `data/sync/SyncCoordinator.kt`, `data/sync/MemorySyncAdapter.kt`, `data/sync/AnniversarySyncAdapter.kt`, `di/AppServiceLocator.kt` (scope handler only); test `data/sync/SyncCoordinatorTest.kt` | Listener errors and cache-only empty snapshots never unpair locally; every document conversion and every `scope.launch` body is guarded (rethrow `CancellationException`); wrong-typed fields skip one document instead of crashing; snapshot handling serialized | `--tests "com.example.data.sync.*"` | H1-H4 | sonnet, high |
| K2 | K / wave K1 / PARALLEL | main `data/sync/SyncWorker.kt`; test `data/sync/SyncWorkerTest.kt` (or the existing worker test) | Rows that can never be pushed (undecodable or null payload, null `relationshipId`, unknown `entityType`) are parked, not retried forever; `CancellationException` is rethrown; one bad row does not block the rest | `--tests "com.example.data.sync.*"` | H1-H4 | sonnet, high |
| K3 | K / wave K2 / SOLO | `data/cloud/*`, `data/repository/RemoteAccountCleanup.kt` and any Cloud Function call site found by `getHttpsCallable` / `.call(` | Every callable call is wrapped: a failure becomes a localized message or a retry, never a crash | targeted test or compile | K1 | sonnet, high |
| K4 | K / wave K3 / SOLO | `data/repository/*`, `ui/viewmodel/InLoveViewModel.kt`, `ui/screens/*` (read-only scan first) | Scan for `!!`, `.first()`, `.single()`, unsafe casts, `toInt()` on user text, date parsing, blank image URLs, `@Update` on a missing row; fix each hit with the smallest guard and a test where the logic branches. Runs after U7 so it never collides with the VM tasks | compile + targeted tests | U7, K1-K3 | sonnet, high |
| U28 | final / 11 / SOLO | none (Execution log only) | Verification wave | see Verification | all approved | sonnet, high |

Totals:
- P1: 12 tasks (U0-U11) in waves 0-5 (6 waves).
- P2: 13 tasks (U12-U24) in waves 6-9 (4 waves).
- P3: 3 tasks (U25-U27) in wave 10.
- H: H1-H4 done, H5 open. K: K1-K4.
- Final: U28 in wave 11.

Hot-file sequence:

| File | Tasks, in wave order |
|---|---|
| InLoveViewModel | U6 (w3), U7 (w4) |
| Dialogs | U6 (w3, two anchored regions), U21 (w8) |
| MainActivity | U8 (w5), U22 (w8) |
| LoveHomeScreen | U9 (w5), U23 (w8) |
| OnlineCoupleRepository | U3 (w2) |
| AuthRepository | U4 (w2) |
| MilestoneBadgeTracker | U10 (w5), U16 (w7) |
| EditMyProfileDialog | U8 (w5), U19 (w7) |
| LoveDaysCalculatorWidget | U11 (w5), U18 (w7) |
| ProfileSyncViewModelTest | U6, U7, U26 |
| EditCoupleDialogTest | U21, U25 |

## Task details

Each block is handed to a subagent together with the Execution rules.

### U0 - baseline (haiku)

Change no file except the Execution log. Run, in order:
1. `.\gradlew.bat :app:testDebugUnitTest`, and record the totals and failing FQCNs (expected 150 suites / 1458 tests; a rotating flake is possible).
2. `.\gradlew.bat :app:lintDebug`, and record errors and warnings (expected 0 / 128).
3. `.\gradlew.bat :app:dependencies --configuration debugRuntimeClasspath`, and record the resolved `androidx.compose.material3:material3` version. If it is not 1.3.x, stop and report: D13 mirrors 1.3.0.
4. `.\gradlew.bat :app:assembleDebug`.
5. Small-screen check on LDPlayer (adb steps in U28; `install -r`, then `shell wm size 720x1280`, `shell wm density 320`, `shell settings put system font_scale 1.3`). Screenshot, where reachable, the SetPin dialog (Settings -> PIN), the date picker (couple dialog -> birthday calendar icon), AddAnniversary (Calendar "+" -> anniversary) and the Settings delete-account AlertDialog. Record for each one whether its buttons are clipped.
6. Reset: `shell wm size reset`, `shell wm density reset`, `shell settings put system font_scale 1.0`.

### U1 - profile display helpers (haiku)

In OnlineProfileRules.kt add `withCardFallback`, `displayMeName`, `displayPartnerName` and `DEFAULT_LOVE_TITLE` exactly as in D15/D16. Tests (pure JUnit):
- the G.G1 row (`displayName="Bạn", isProfileSetup=false`) with name "An" gives `effectiveDisplayName == "An"`;
- a set-up row is not overridden;
- blank `name` leaves the row unchanged;
- birth and avatar fall back only when blank;
- both name helpers in vi and en.

### U2 - date helpers (sonnet, test-first)

Read VM `calculateLoveDaysFromDate` (1110-1135) and `ProfileUtils.calculateLoveDays` first. Add `loveDaysOn` (D14) and `normalizeAnniversaryInput` (D17), each with its own `SimpleDateFormat` and `isLenient = false`. Tests, with a fixed `nowMillis`:
- all four patterns give the same value as `calculateLoveDays(parseDateToMillis(..))`;
- today gives 1;
- tomorrow, "", "abc", "31/02/2024", "14/02/23" and year 1899 give null;
- "1/2/2020" normalizes to "01/02/2020" and "2020-02-01" to "01/02/2020".

### U3 - repository writer fixes (sonnet, test-first)

In `updateMyProfile`:
- start from `dao.getOnlineUserByUidSync(_currentUser.value.uid) ?: _currentUser.value`;
- the Room mirror writes `partner1Name = updatedMe.effectiveDisplayName` (drop `.ifEmpty { "Bạn" }`; still only when the row exists);
- when `isProfileSetup` holds and the name changed, update `UserAccountEntity.displayName` via `getUserAccountByUid` / `updateUserAccount`.

In `acceptSetLoveInvite`, use `DEFAULT_LOVE_TITLE` instead of "InLove".

In `acceptSetLoveInvite` (A.A2, acceptor half, client-only, V21): replace the early return "Không tìm thấy người gửi lời mời!" with an in-memory sender built from the invite when `dao.getOnlineUserByUidSync(incoming.senderUid)` is null: `OnlineUserEntity(uid = incoming.senderUid, displayName = incoming.senderName, coupleCode = incoming.senderCoupleCode, avatarUrl = incoming.senderAvatar, birthDate = incoming.senderBirthDate, age = incoming.senderAge, zodiac = incoming.senderZodiac, bio = incoming.senderBio, isProfileSetup = incoming.senderName.isNotBlank() && incoming.senderName != "Vô danh")`. Then write the COUPLED sender with `dao.insertOnlineUser(updatedSender)` (REPLACE) instead of `dao.updateOnlineUser(updatedSender)` (a no-op on a missing row). Nothing is written before the server call succeeds, exactly as now, and an existing sender row is replaced with the same values as today. `rejectSetLoveInvite` (542-545) already tolerates a missing sender row.

Replace every Kotlin `assert(` in OnlineCoupleAcceptInviteTest with `assertTrue`/`assertNull`/`assertNotEquals`/`assertEquals` (A.A18).

New tests in OnlineCoupleProfileSlotTest:
- a status written to Room after the cache was loaded survives `updateMyProfile`;
- the account row's `displayName` follows a profile save;
- a blank name mirrors "";
- the accept title is `DEFAULT_LOVE_TITLE`.

New test in OnlineCoupleAcceptInviteTest (A.A2): a variant of `withRepoOf` that does not pre-insert `uid_a`, with an invite carrying `senderName = "An Nhi"`, an avatar URL, `senderBirthDate = "01/02/1999"` and a zodiac. After `acceptSetLoveInvite` with a successful fake callable: the result is `true`; `uid_a` exists with `COUPLED`, `partnerId = "uid_b"`, `relationshipId = inviteId`, and the invite's name, avatar, birth date and zodiac; `couple_profile.partner2Name == "An Nhi"`. A second case: the callable fails with `UNAVAILABLE`, the result is `false`, and no `uid_a` row exists.

### U4 - auth fixes (sonnet, test-first)

- `syncOnlineUserWithAccount`, existing-row branch: keep the edited name (D15 last bullet).
- Account deletion: move `onlineRepo.setCurrentUserId("")` before the first Room delete; leave the rest of the order unchanged.
- Change `init { scope.launch { restoreSession() } }` to `internal val restoreJob = scope.launch { restoreSession() }`, so tests can `join()`.
- Tests in AuthRepositoryTest: an edited, set-up name survives a second `syncOnlineUserWithAccount`; a non-set-up row still takes the account name; after deletion no online row exists for the uid.

### U5 - anniversary alarm (haiku)

In the 400001 block, compute `triggerAtMillis` first, then use `ProfileUtils.loveDaysOn(profile.anniversaryDate, triggerAtMillis) ?: profile.loveDays` in the message. No other change. If an alarm test already builds this message, extend it; otherwise compile only (the helper is tested in U2).

### U6 - one writer for "me", placeholders (sonnet, test-first)

Execution note: U6 is large, so the owner of the task runs it as two sequential sub-steps, each with its tests green before the next starts. (a) D16 placeholders and collectors. (b) D15 `saveCoupleProfile`, `completeOnboardingProfile` and the two call sites (Dialogs, Onboarding). The old step (c), "D12 unpair", is gone: D12 is hide/restore and is already built (H1-H4). U6 must keep the H hunks in the Room couple collector (`relationshipEnded`, `ownsGirl`, `ownsDate`, the `_loveDays = 1` / "Hôm nay" reset) and the `takeIf` at the top of `resetGirlCard()`. Ownership and wave are unchanged.

InLoveViewModel:
- D16: the flow defaults, `resetBoyCard`/`resetGirlCard` and 1862 become ""; the collectors 675/691 use `effectiveDisplayName` with the blank test `isBlank()`; the notification sentences use `.ifBlank { "người ấy" }`.
- D15: new `saveCoupleProfile` signature (drop `boy*`; keep `partner1*` from the row; `isPairedNow()` guard with a `tr()` toast; no `ifEmpty`/`ifBlank` fallbacks for the partner name, birthday or zodiac; title blank -> `DEFAULT_LOVE_TITLE`; anniversary not blank -> `normalizeAnniversaryInput`, or toast and return).
- Delete `updateCoupleInfo`.
- Add `completeOnboardingProfile`. Read whether `onlineRepo.updateMyProfile` is suspend (U-e); if it is not, call it after the row write inside the same launch.
- D12 is NOT done here. Do not add `clearPartnerAfterUnpair` or any code that clears `partner2*` on unpair.

Dialogs.kt, two anchored regions only:
- 1011: `isCoupled` uses the same predicate as `isPairedNow()`, computed from the flows the dialog already collects, or a new public VM `isPaired: StateFlow<Boolean>` if the status flow is not collected there;
- 1503-1519: the new `saveCoupleProfile` call.

OnboardingScreen 270-296: call `completeOnboardingProfile(boyNameInput.trim(), girlNameInput.trim(), "Bên Nhau Trọn Đời")` when either name is not blank; no "Anh"/"Em".

ProfileSyncViewModelTest:
- replace the fixed 3 s settle with `vm.authRepo.restoreJob.join()` (through `runBlocking`) plus the existing `await`;
- new tests:
  - a guest onboarding with "An"/"Binh" gives an online name of "An" and Room `partner1Name` of "An";
  - while COUPLED `saveCoupleProfile` changes nothing;
  - clearing the partner birthday persists "";
  - a signed-in user whose only relationship is TERMINATED shows an empty partner card and `loveDays == 1` / "Hôm nay", while Room still holds `partner2*` (hidden, not cleared);
  - re-pairing the same partner (a new ACTIVE relationship with the same uid pair) brings the partner card and anniversary back;
  - a signed-in "Vô danh" row gives `boyName == ""`.

### U7 - derived love days and safe date writes (sonnet, test-first)

- D14: `calculateLoveDaysFromDate` delegates to `loveDaysOn`; the Room collector derives `_loveDays`; add `refreshLoveDays()`.
- D17 and A.A10/A.A12: `setAnniversaryAndRecalculateDays` normalizes (null -> toast, return; still refused while paired). With no row, it creates one with blank names, the normalized date and the derived days. It toasts only after the write.
- A.A11: `updateLoveTitle` with no row creates `CoupleProfileEntity(id = 1, loveTitle = title)` with every other field at the entity default or "" (no "Hôm nay", no names). It toasts after the write. It is not gated while paired (loveTitle has no sync, V10).
- Tests:
  - an anniversary 10 days ago stored with `loveDays = 3` shows 11;
  - a future or invalid date leaves Room unchanged and toasts;
  - with no row, set anniversary or set title persists a row and stores no placeholder;
  - `refreshLoveDays` after changing the stored date updates the flow.

### U8 - linked dialog prefill and top bar (haiku)

MainActivity:
- collect `boyName`, `boyBirthDate` and `boyAvatarUrl` if they are not already collected (U-f);
- pass `currentOnlineUser.withCardFallback(...)` to `EditMyProfileDialog` (621-636) and use its `avatarUrl` for `InLoveTopBar` (327-338);
- add the D14 `LifecycleResumeEffect` inside the content.

EditMyProfileDialog 177-179: the name prefill becomes `currentUser.effectiveDisplayName`.

Test in EditProfileDialogTargetsTest: composing the content with a `withCardFallback` user shows "An" in the name field.

### U9 - Home name placeholders (haiku)

Keep the raw `boyName`/`girlName` for the blank checks (439, 762). Add `val meLabel = displayMeName(boyName, isEnglish)` and `partnerLabel` next to the collects. Use them at 746, 860, 1009, 1086-1087 (widget arguments), 1098/1100, and in the `loveQuotes` key or texts if they print names.

### U10 - Milestone names (haiku)

At each collect pair, use `val boyName = displayMeName(viewModel.boyName.collectAsState().value, isEnglish)` (and the partner equivalent), with `isEnglish` derived from the `appLanguage` already in scope. The text sites 715, 1250 and 1445 are then unchanged.

### U11 - manual date dialog validation (haiku)

In ManualDateInputDialog, confirm runs `normalizeAnniversaryInput(dateText)`; null sets `isError = true`, otherwise it calls `onConfirm(normalized)`. Pass `isError` and a `supportingText` (`if (isEnglish) "Use dd/MM/yyyy, not in the future" else "Nhập dd/MM/yyyy, không ở tương lai"`) to the field; read how the dialog gets `isEnglish`, or add a parameter with a default. If the dialog body is private, extract an `internal` content composable without a Dialog window (V18) for the test. Test: "14/02/23" shows the error and does not call `onConfirm`; "1/2/2020" calls it with "01/02/2020".

### U12 - AppWindows.kt (sonnet, test-first)

Write D13 exactly. `AppAlertDialog` mirrors the parameter order and defaults of M3 1.3.0 (`AlertDialogDefaults.shape`, `.containerColor`, `.iconContentColor`, `.titleContentColor`, `.textContentColor`, `.TonalElevation`, `DialogProperties()`). `AppDropdownMenu` exposes only the parameters the three call sites use (U-g; read them). `Theme.kt`: `private` -> `internal` on `UI_SCALE`.

Tests in AppWindowsTest (Robolectric, `createComposeRule`):
- under `AppWindowLocals(ctx, cfg)` the density equals `outer * UI_SCALE` (delta 1e-4), `LocalContext.current === ctx`, `LocalConfiguration.current === cfg` and `LocalMinimumInteractiveComponentSize.current == MinTouchBox`;
- `AppDialog { Text }` exposes the outer `LocalContext` and the scaled density. Drop this test if it does not idle in 10 s (U-b) and report it.

### U13 - U20 - mechanical migration (haiku)

For each owned file:
- `Dialog(` -> `AppDialog(`, `AlertDialog(` -> `AppAlertDialog(` (choose `scrollableText` by the D13 rule), `DropdownMenu(` -> `AppDropdownMenu(`;
- remove the now-unused imports;
- delete any `AppDensity { }` that now sits inside a wrapper;
- keep every other parameter (`modifier = Modifier.animateDialogEntrance()`, `containerColor`, `shape`, `icon`, `properties`).

Extras per task:

- **U13.** Add `verticalScroll(rememberScrollState())` to the root Column of SetPinDialog (AccountSecurityDialogs 257) and of the PIN fallback (AppPinLockScreen 371), only if that Column holds no Lazy list and no scroll.
- **U14.** Settings name and email get `maxLines = 1, overflow = TextOverflow.Ellipsis`; the account row gets `onClickLabel` plus a chevron icon (B.B11). The AuthScreen `DropdownMenuItem` (~906) gets `modifier = Modifier.heightIn(min = MinTouchBox)`: inside `AppDropdownMenu` the window runs at `AppDensity`, so M3's inner `sizeIn(minHeight = 48.dp)` would shrink to 40.8 real dp; the caller's outer `heightIn(min)` wins.
- **U15.** Merge prev/next into `val shift: (Int) -> Unit` with identical behaviour; use `Locale.ROOT` at 231; remove the import at 72. The 4 CalendarScreen `DropdownMenuItem`s (~416, 421, 1460, 1480) get `modifier = Modifier.heightIn(min = MinTouchBox)` for the same reason as U14.
- **U16.** The explicit `.size(48.dp)` close buttons (MilestoneBadgeTracker 694, 1177) and the 32dp closes (VerifyInviteDialog ~88, MemoriesGridScreen ~682) become `Modifier.size(MinTouchBox)` with the visual icon size unchanged. The VerifyInvite avatar description is localized.
- **U17.** The picker Card content goes in `Column(Modifier.heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.9f).dp).verticalScroll(rememberScrollState()))`. If the year grid is a Lazy grid, it must keep its fixed 260dp height (V15/E.E2), or report BLOCKED. Nav buttons become `MinTouchBox`. The field's calendar `IconButton` gets `Modifier.size(MinTouchBox)`. Day cells stay 36dp (enlarging them breaks the grid; noted in Out of scope). DatePickerTest stays green.
- **U18.** NotificationsSheet 127 becomes `.size(MinTouchBox)` with an inner 40dp circle; the widget banner buttons get `heightIn(min = MinTouchBox)`.
- **U19.** The birthday field (533-547) becomes `InLoveDatePickerField(value, onValueChange, label, isEnglish = isEnglish, testTag = "edit_profile_birthday")`, blank allowed. B.B9: the pencil and cancel buttons get a `MinTouchBox` box around the unchanged visual; the photo action buttons get `heightIn(min = MinTouchBox)`; the cancel description becomes `if (isEnglish) "Close" else "Đóng"`. Extend EditProfileDialogTargetsTest with these targets.
- **U20.** `ProfileAvatar`'s `AsyncImage` gets `placeholder` equal to the tinted Person painter and a tinted `error`. ProfileAvatarTest stays green.

### U21 - Dialogs.kt sweep (sonnet)

- Migrate the 3 Dialogs and 8 AlertDialogs using the D13 table.
- Remove `AppDensity` at 929-933 and at the AddMilestone slots 303/321/323/350.
- C.C4: the COUPLED card at 1657-1658 uses `displayPartnerName(partner?.effectiveDisplayName.orEmpty().ifBlank { girlName }, isEnglish)` and the same avatar fallback as the summary at 1233; "Mã:" becomes `if (isEnglish) "Code:" else "Mã:"` (also at 1568).
- Placeholders: 1250 uses `displayPartnerName`; 2736 and 2788 use the D16 helper or a localized hint.
- `Locale.ROOT` at 2227/2255.
- C.C8, smallest diff:
  - a. `ImeAction.Search` with `keyboardActions` calling the existing search lambda;
  - b. the paste chip pastes `ProfileUtils.extractCoupleCode(clip).ifBlank { clip }`;
  - c. the Search button gets `enabled = !isSearching && query.isNotBlank()`;
  - d. `selectedPartner = null` in `onValueChange`;
  - e. hide the search and send cards while `outgoingInvite != null`;
  - f. localize the two errors at 1819/1821;
  - g. only per U-h; otherwise leave it and log it.
- E.E5: only if U0 recorded clipping; then put the icon Row in `horizontalScroll`.
- Tests in EditCoupleDialogTest: Search is disabled for a blank query; editing the query clears the result card.

### U22 - MainActivity, Pairing, share (haiku)

- MainActivity: the Pairing host (603-619) becomes `AppDialog(properties = DialogProperties(usePlatformDefaultWidth = false)) { PairingScreen(...) }` (no `AppDensity`). The Paywall host (638-659) becomes `AppDialog` and keeps its `CompositionLocalProvider(*compositionLocals)`, dropping only `AppDensity`.
- PairingScreen: migrate the AlertDialog at 670 (`scrollableText = true`); delete 1172-1188 plus the now-unused `showEditProfileDialog` and `dynamicPresetAvatars` vals; 502/591 use `displayPartnerName`; localize the avatar descriptions (~230, 491, 900).
- CoupleCodeShare: wrap `startActivity` in `runCatching { ... }.onFailure { Toast(...) }` with a localized text.

### U23 - Home tidy (haiku)

- B.B1: zodiac at 792-806 and 933 uses `ProfileUtils.formatZodiacDisplay(..., isEnglish)`, shown only when not blank.
- B.B7: names get ellipsis.
- B.B8: the partner birthday at ~972 shows only when not blank.
- B.B11: the me-avatar `clickable` gets `onClickLabel` and `role = Role.Button`.
- G.G4: "MÁY" becomes "Ngoại tuyến".
- A.A16: per D18, only where the birthday is already in scope.

### U24 - DensityWindowGuardTest (haiku)

Plain JUnit, no Robolectric. Walk `File("src/main/java")` for `*.kt`, skipping `ui/components/AppWindows.kt` and lines whose trimmed start is `//` or `*`. Fail with `file:line` on:
- calls matching `(?<![\w.])(androidx\.compose\.[\w.]+\.)?(Dialog|AlertDialog|BasicAlertDialog|DropdownMenu|ExposedDropdownMenu|Popup)\s*\(`;
- imports matching `^import androidx\.compose\.(material3|ui\.window)\.(Dialog|AlertDialog|BasicAlertDialog|DropdownMenu|ExposedDropdownMenu|Popup)\b`, aliases included.

Allow-list `AppDensity` usages to Theme.kt, AppWindows.kt and NotificationsSheet.kt (catches double scaling). One self-check test feeds the matcher bad and good sample lines (`AppDialog(`, `EditCoupleDialog(`, `DropdownMenuItem(` and `DialogProperties(` must not match).

### U25 - U27 - test hygiene (P3)

- **U25 (EditCoupleDialogTest):**
  - replace the fixed 3 s `settle` with the existing `await`;
  - replace the vacuous `assertCountEquals(0)` on Save with a test that composes `EditCoupleDialogContent` in the COUPLED state and asserts no `btn_save_couple_offline`;
  - add tests for the incoming-invite and outgoing-invite states, and for a non-blank partner birthday cleared and saved (C.C3).
- **U26 (ProfileSyncViewModelTest):** a cold start signed in with an existing Room row; a guest -> sign-in card switch; a sign-out resets the cards to "".
- **U27 (OnlineCoupleProfileSlotTest):** accept with a male "me"; assert birthday, age, zodiac, avatar, and that `likesCsv`/`budgetMaxVnd` survive.

### H5 - ex-data leaks outside the repository flows (sonnet, high)

Audit first (read-only), then fix only what leaks. The rule is in `data/repository/RelationshipVisibility.kt`: a row with `relationshipId == null` is always visible; a scoped row is visible only through `visibleIn(visibleIds)`.
- `alarm/LoveAnniversaryMilestoneScheduler.kt`: while single (or with no current relationship) no milestone alarm of the ex may be scheduled and the stale ones are cancelled; with the same partner back they return.
- `ui/components/MilestoneBadgeTracker.kt` / `love_badges`, `ui/components/AnniversaryMemoriesWidget.kt`, every home widget that reads memories, anniversaries or `couple_profile`: no ex name, date or count while single or paired with someone new.
- VM creation paths (`InLoveViewModel` memory and anniversary creation, about lines 1440-1458 and 1611): a row created while paired carries the CURRENT relationship id; one created while single carries null, never an ex id.
- `OnlineCoupleRepository` accept-invite `couple_profile` write: it overwrites the partner fields, so a new partner never inherits the ex's name.
Add `data/repository/InLoveRepositoryVisibilityTest.kt` (Room in-memory, Robolectric; copy the setup of the nearest existing `InLoveRepository` test): memories and anniversaries inserted under R1 (A+B) are hidden when only a TERMINATED R1 exists, reappear when a new ACTIVE R2 with the same pair exists, and stay hidden when R3 with C is the current one. Report each leak found with its fix, or say there is none.

### K1 - SyncCoordinator crash-proofing (sonnet, high)

Files: main `data/sync/SyncCoordinator.kt`, `data/sync/MemorySyncAdapter.kt`, `data/sync/AnniversarySyncAdapter.kt`, `di/AppServiceLocator.kt` (scope construction only, about lines 147-149); tests `data/sync/SyncCoordinatorTest.kt` plus a new `data/sync/SyncAdapterReceiveTest.kt`.

Facts to confirm in the code before editing: `DocumentSnapshot.getString/getLong/getBoolean` throw `RuntimeException` on a wrong-typed field; snapshot callbacks run on the main thread; on a listener error the callback gets `snapshot == null` and the listener is dead; `invites` is client-writable (a planted wrong-typed invite would crash-loop its target); the listener lambdas ignore `error`; the coordinator scope has no exception handler; each snapshot is applied in its own `scope.launch` on `Dispatchers.IO`, so two snapshots can be applied out of order.

Required:
1. Relationship listener: `error != null || snapshot == null` -> log and return, no state change. `snapshot.metadata.isFromCache && snapshot.isEmpty` -> return too (an empty cache means "unknown", never "no relationship": it must not flip a COUPLED user to SINGLE or mark anything TERMINATED). The same null/error guard for the invites listener and both content listeners.
2. Pure conversion functions on `Map<String, Any?>` (from `doc.data`, with `doc.id` passed separately), `internal`: `relationshipFromMap`, `inviteFromMap` (top level next to the coordinator), `MemorySyncAdapter.fromFirestoreMap`, `AnniversarySyncAdapter.fromFirestoreMap`. Safe casts only (`as? String`, `(as? Number)?.toLong()`, `as? Boolean`), never `getString/getLong/getBoolean`. `fromFirestoreDoc(doc)` delegates to them. A missing OR wrong-typed optional field takes exactly the default a missing key takes today (the defaults `GiftAndSyncGoldenTest` pins as `memoryReceiveDefaults` / `anniversaryReceiveDefaults`, which are only a data table there: the real function is not exercised, so the new test below is its guard); whatever makes a conversion return null today keeps returning null; a relationship with a missing or wrong-typed `user1`, `user2` or unknown `status` is skipped.
3. A guard helper (`internal inline fun guarded(what: String, block: () -> Unit)`: catches `Exception`, rethrows `CancellationException`, `Log.w`). Every listener callback body and every `scope.launch` body runs inside it. One bad document is skipped and the rest of the snapshot is still applied.
4. Order: convert on the callback thread (cheap, guarded), then hand the plain values to ONE sequential consumer (a `Channel` drained by one coroutine that `start()` launches and `stop()` cancels), so snapshot N is applied before N+1. The `isLive(gen)` fence stays. `activeRelationshipId` and every content-listener start/stop happen only inside the consumer. Keep a seam that takes plain data (a list of `id to map`) so tests never need a `QuerySnapshot`.
5. `AppServiceLocator`: add `CoroutineExceptionHandler { _, t -> Log.e("InLove", "uncaught in app scope", t) }` to the app scope. Cancellation never reaches it.

Tests:
- `relationshipFromMap` / `inviteFromMap`: well-typed input gives the entity the old code built; status as a number, a missing `user1`, a list where a string is expected give null (or the default for an optional field) and never throw.
- `fromFirestoreMap`, both adapters: `mapOf("syncId" to "mem-recv", "title" to "Tieu de")` gives the golden defaults (createdAt = now); `"durationSeconds" to "abc"` and `"createdAt" to true` fall back to the defaults.
- The guard: a throwing block does not escape; `CancellationException` does.
- Order and no-flip: two relationship snapshots (ACTIVE, then TERMINATED) leave Room with the second; an error snapshot and an empty cache-only snapshot change nothing for a COUPLED user.

### K2 - SyncWorker poison rows (sonnet, high)

Files: main `data/sync/SyncWorker.kt`; test `data/sync/SyncWorkerTest.kt` (find the existing worker test with `Grep SyncWorkerFactory app/src/test` and extend it; if none fits, create `SyncWorkerTest.kt` with the same setup).

Required:
- `pushOne` treats a row that can never be pushed as parked: return false, `Log.w`, no `markOutboxAttemptFailed`, not a failure, row kept. Cases: payload that is not valid JSON for the entity (Moshi throws `JsonDataException` / `IOException`), payload decoding to null, null `relationshipId`, unknown `entityType`. Today these `error(...)` and loop as `Result.retry()` forever.
- Catch `CancellationException` first and rethrow it (today `catch (e: Exception)` swallows it and marks the row failed).
- One bad row never blocks the rest of the batch.
- Rewrite the ponytail comment above `pushOne` to say what is parked and that rows are kept, never deleted.
Tests (fake `push` records calls): (1) invalid JSON memory row -> `Result.success()`, row still in the outbox, `push` not called; (2) memory with a null `relationshipId`, same; (3) unknown `entityType`, same; (4) a good row after a bad row is pushed and deleted; (5) a row whose relationship is TERMINATED (and one PENDING_BREAKUP) is parked: kept, not pushed, `Result.success()`; the same row is pushed once the relationship is ACTIVE; (6) a failing push still gives `Result.retry()` and bumps `attemptCount` (only if not already covered).

### K3 - Cloud Function call sites (sonnet, high)

Find every callable use with `Grep "getHttpsCallable|CloudFunctionsClient|executeCallable|\.call\(" app/src/main`. `AndroidCloudFunctionsClient.call()` already maps `FirebaseFunctionsException` to `CloudFunctionsException` and times out. For every CALLER (`RemoteAccountCleanup`, `OnlineCoupleRepository` accept / reject / breakup / confirm, any ViewModel or screen that triggers them): a failure (`CloudFunctionsException`, `IOException`, any other `Exception`) ends in a boolean / result or a localized toast, never an exception escaping into a coroutine that has no handler; `CancellationException` is rethrown. Do not change what is sent to the server. Add a test per call site that no existing test covers (fake callable that throws). Report a table: file:line -> how a failure is handled -> change made.

### K4 - crash scan (sonnet, high; runs after U7)

Scan `data/repository/*`, `data/sync/*`, `alarm/*`, `ui/viewmodel/InLoveViewModel.kt`, `ui/screens/*`, `ui/components/*` for: `!!`; `.first()` / `.single()` / `.last()` on lists or flows that can be empty; unsafe `as` casts (`as Activity`, `as ComponentActivity`, map values); `toInt()` / `toLong()` / `toFloat()` / `toDouble()` on user text; date parsing without a guard (`SimpleDateFormat.parse`, `LocalDate.parse`); `Uri.parse` / `Color.parseColor` on remote text; indexing or `substring` with computed bounds; division by a value that can be 0; `requireNotNull` / `checkNotNull` / `error()` on remote data; blank image URLs given to `AsyncImage`; a `@Insert` without a conflict strategy (constraint crash); an `@Update` on a row that may be missing (silent data loss). Fix each hit that user input or remote data can reach with the smallest guard (`toIntOrNull`, `getOrNull`, `runCatching`, `?:`), and add a test where the logic branches. A hit that cannot be reached is left alone and listed as "safe because ...". Also check: account-deletion ordering (U4), the profile save vs pairing snapshot race (U3), process-death restoration of `rememberSaveable` state. Put the full table in the Execution log; the report to the main session stays at 10 lines.

## Verification (U28, sonnet, high)

Run in order and paste the tails into the Execution log:

1. `.\gradlew.bat :app:compileDebugKotlin`.
2. Every targeted test class named in the tasks table.
3. `.\gradlew.bat :app:testDebugUnitTest`. Compare it with the U0 baseline (150 suites / 1458 tests plus the new tests). For each failure, re-run that single class twice with `--tests "<fqcn>"`. If it passes both times it is a flake: log it and do not fix it. If it fails again it is a regression: send it back to the owning task.
4. `.\gradlew.bat :app:assembleDebug`.
5. `.\gradlew.bat :app:lintDebug`, which must be no worse than 0 errors / 128 warnings (or the U0 numbers).
6. Grep gate: `rg -n "\"Người Thương\"|\"Người thương\"|ifEmpty \{ \"Bạn\" \}" app/src/main/java/com/example/ui app/src/main/java/com/example/data/repository` hits only data defaults or hint texts listed in D16.
7. LDPlayer smoke (`$adb = "F:\LDPlayer\LDPlayer9\adb.exe"`, device `-s emulator-5554`, package `com.aistudio.inlove.kmrv`; uiautomator dump does not work, and swipe and DPAD do not scroll). Steps:
   1. `install -r app\build\outputs\apk\debug\app-debug.apk`, then `logcat -c`, then `shell monkey -p com.aistudio.inlove.kmrv -c android.intent.category.LAUNCHER 1`, then `shell pidof com.aistudio.inlove.kmrv` (non-empty).
   2. Screenshot with `shell screencap -p /sdcard/s.png` and `pull /sdcard/s.png <scratchpad>\NN.png`. Never redirect binary in PowerShell. Tap with `shell input tap X Y`, reading the coordinates from the last screenshot.
   3. Then visit, one screenshot each:
      1. **Home**: the me-card name and no "Chưa rõ" pill.
      2. **The G1 legacy-row profile dialog**: the top-right avatar opens with name "An", the scale matches Home, and the birthday has a calendar icon.
      3. **Pairing**: the "Cặp đôi" dialog, both tabs.
      4. **The Calendar "+" menu**: menu text the same size as the page.
      5. **Memories add dialog and a Gift dialog.**
      6. **The date picker**: opened from the couple dialog; same scale as its parent; buttons visible.
      7. **Settings**: language dialog and logout AlertDialog in the in-app language (switch the app to English and re-check one dialog).
   4. Repeat the date picker and the Settings AlertDialog at small screen (U0 step 5 commands, then reset).
   5. `logcat -d | Select-String "FATAL|AppPluginBase onCreate failed|NoClassDefFoundError"` must be empty.
8. `git diff --stat` covers only owned files (other sessions' files are expected, so compare with `git status` taken at U0).

## Risks

| Risk | Mitigation |
|---|---|
| Nested-scroll crash (a `verticalScroll` text slot around content that already scrolls) | The D13 table and the read-the-lambda rule; U28 opens AddMilestone, SetAlarm, AddAnniversary and AddGiftReminder on the device. |
| Double scaling (0.7225) where an old `AppDensity` stays inside a wrapper | The V3 list, the U24 `AppDensity` allow-list and the smoke screenshots. |
| The `MinTouchBox` override grows M3 buttons, checkboxes and menu items in dialogs to 48.4 real dp and shifts tight rows (E.E5, button rows) | Windows only, not pages; U28 small-screen pass; if a row breaks, set `LocalMinimumInteractiveComponentSize` back for that row and log it. |
| In-app locale loss if a wrapper captures the context inside the window instead of at the call site | `AppWindowLocals` takes the captured values; AppWindowsTest asserts identity; the English pass in the smoke. |
| Hot-file merge conflicts with the dirty tree and with sequenced tasks | Exclusive ownership per wave, quoted anchors, BLOCKED on drift, `git diff -- <file>` review after each task. |
| Robolectric flakiness (Dialog idling, the restore race) | Content composables tested without windows; `restoreJob.join()` instead of sleeps; the U28 re-run rule. |
| The `saveCoupleProfile` signature change breaks an unseen caller | V9 grep (two callers); U6 compiles. |
| Unpair hides data on a status blip | Hiding is a view over `online_relationships`; nothing is deleted, so a corrected snapshot brings every row back. K1 stops listener errors and cache-only empty snapshots from marking a relationship TERMINATED or removing it. |
| A remote document with a wrong-typed field crashes the app (the `invites` collection is client-writable) | K1 guards every conversion and every launch body; one bad document is skipped and logged. |
| Parked outbox rows pile up after a breakup | They are kept on purpose (nothing is lost) and cost one `getOnlineRelationshipSync` per run; delete them only if the owner asks. |

## Out of scope (skipped: X, add when Y)

- A.A2 + D.D2, the INVITER half: the acceptor's profile never reaches the inviter's device, and the partner profile is not kept in sync after pairing. Follow-up: a per-uid profile block on `relationships/{id}`, written by each member and copied into the partner row by the SyncCoordinator listener. Needs `firebase/functions` and rules, which are off-limits here. The ACCEPTOR half (the sender row built from the invite) is in U3.
- D.D4 (Reminder fake cards, dead screen), D.D7 (memory `authorName` snapshot), D.D8 (anniversary tag embeds the date): add when the owner reports them.
- D.D6 (notification partner-name source): covered by V20 and D16, no extra task.
- A.A14, A.A15 (column-targeted updates): add if a lost-column bug is seen.
- C.C10-C.C15, C.C17, E.E13, E.E14: low; add when touched for another reason.
- Midnight tick for love days (D14 ceiling). Date-picker day cells under 48dp. AlertDialog frame density (D13 ceiling). The `calculateAge` DAY_OF_YEAR fix.
- `ReminderScreen.kt`: dead code, not touched; see Questions.

## Questions for the owner

1. RESOLVED by the owner (2026-10-21): after unpair keep everything and only hide; same partner restores; a new partner sees only the new space (D12, H1-H4).
2. A.A2: the ACCEPTOR half (build the sender row from the invite) is in U3 by default. The INVITER half (the acceptor's profile reaching the inviter's phone) needs Cloud Functions, Firestore rules and a deploy, which only the owner can do. Plan that follow-up now, or later?
3. `ReminderScreen.kt` is dead code with hard-coded cards. Delete it in a later task?

## Execution log

Baseline (2026-10-21, before any U / K / H5 task):
- `:app:assembleDebug` and `:app:lintDebug` finished with EXIT=0. Lint: 0 errors / 128 warnings (`app/build/reports/lint-results-debug.txt`).
- material3 = 1.3.0 (D13 mirrors it).
- Unit suite before this plan: 150 suites / 1458 tests / 0 failures, plus 3 `RelationshipVisibilityTest` tests since H1-H4 (last full run green). A rotating flaky set exists (e.g. a Compose timeout in `ChecklistTest`).
- Authorization: owner message "thực hiện làm tất cả cho tôi" (2026-10-21): no further approval gate. Codex waived. No commit, no push.

| task | attempt | model / effort | result | notes |
|---|---|---|---|---|
| U1 | 1 | haiku / max | DONE | `OnlineProfileRules.kt`: `withCardFallback`, `displayMeName`, `displayPartnerName`, `DEFAULT_LOVE_TITLE`. Targeted tests green. |
| U2 | 1 | sonnet / high | DONE | `ProfileUtils.loveDaysOn`, `normalizeAnniversaryInput`; `ProfileUtilsDatesTest` (5 tests) green. |
| K1 | 1 | sonnet / high | DONE | `SyncCoordinator`: identity-tier listeners always on, content listeners for the one ACTIVE relationship, plain-data seams, one sequential consumer, generation + vault-epoch fence, scope `CoroutineExceptionHandler`; adapters `fromFirestoreMap`. `SyncCoordinatorTest` (7) + `SyncAdapterReceiveTest` (14) green. |
| K2 | 1 | sonnet / high | DONE | `SyncWorker.pushOne` parks invalid / unknown-type / null-relationship / non-ACTIVE rows (kept, no failure count); real push failures still retry. `SyncWorkerTest` (12) green. |
| H5 | 1 | sonnet / high | DONE | Hide/restore finish: `cancelMilestones`, VM `loveEnded` flow (4-flow combine, toast guard), `InLoveRepositoryVisibilityTest` (4) green. Wave-1 targeted run: compile OK, 32 suites / 363 tests / 0 failures. Findings routed to U3 (accept title), U5 (400001 when ended), U6 (`ownsDate` with only a PENDING relationship). |
| U3 | 1 | sonnet / high | DONE | `OnlineCoupleRepository`: `updateMyProfile` re-reads Room, syncs the login account name, mirrors `partner1Name` without the "Bạn" fallback; `acceptSetLoveInvite` builds a missing sender from the invite and keeps/resets the title (`DEFAULT_LOVE_TITLE`). Main fix by me: one named-argument slip in the test helper (`block = block`). |
| U4 | 1 | sonnet / high | DONE | `AuthRepository`: `restoreJob` (joinable), `syncOnlineUserWithAccount` keeps an edited name, `deleteCurrentAccount` clears the current uid before the first Room delete. 3 new tests. |
| U5 | 1 | haiku / max | DONE | `AlarmNotificationScheduler`: 400001 message counts love days at fire time (`loveDaysOn`), and the alarm is cancelled once the couple has ended. 1 new test in `AndroidReminderSchedulerTest`. |
| wave 2 verify | - | main | GREEN | `:app:compileDebugKotlin` OK; `AuthRepositoryTest`, `OnlineCoupleProfileSlotTest`, `OnlineCoupleAcceptInviteTest`, `OnlineCoupleRepositoryPairingTest`, `AccountDeletionOrderTest`, `com.example.alarm.*`: 22 suites / 320 tests / 0 failures. |
| U6a | 1 | sonnet / high | DONE | `InLoveViewModel` (D16): `_boyName`/`_girlName` default "", card resets to "", collectors use `effectiveDisplayName`, in-sentence names `.ifBlank { "người ấy" }`, Room collector combines `observeRelationships()` and feeds the love date only when the table is empty (`ownsDate`). `ProfileSyncViewModelTest` 5 -> 9 tests (+ placeholder name, ended hides ex, same-partner restore, PENDING_BREAKUP does not feed the date); `newViewModel()` waits on `restoreJob`. Main re-run: compile OK; `ProfileSyncViewModelTest` 9, `EditCoupleDialogTest` 4, `ReminderWiringSettingsTest` 13, 0 failures. |
| U6b | 1 | sonnet / high | DONE (1 test routed to U7) | D15: `saveCoupleProfile` drops `boy*` and refuses while paired, `updateCoupleInfo` deleted, `completeOnboardingProfile(me, partner, title)`; `Dialogs.kt` + `OnboardingScreen.kt` follow. Main run: compile OK; `EditCoupleDialogTest` 4/0, `ReminderWiringSettingsTest` 13/0, `ProfileSyncViewModelTest` 16/17: the one failure `endedRelationship_hidesExCardAndDate_butKeepsRoomRow` (`expected:<[]> but was:<[Partner]>`) is NOT a U6b regression but a real race in the Room collector: a Room pass before the account is restored (`signedIn` false) feeds the ex partner onto the girl card and nothing clears it later. Fix (`ownsGirl ... && !ended` + `resetGirlCard()` in the ended block) and a deterministic regression test are in U7. |
| U7 | 1 | sonnet / high | DONE | `InLoveViewModel`: ex-card race fixed (`ownsGirl ... && !signedIn && !ended`; `resetGirlCard(room)` got an optional `room` param, the ended block calls `resetGirlCard(null)` because the default re-fed "Partner" from Room for a signed-out VM); love days from `loveDaysOn` (guest row, `calculateLoveDaysFromDate`, `relationshipLoveDays`); public `refreshLoveDays()` (MainActivity calls it on resume, U8); `currentOrNewCoupleRow()`; `updateLoveTitle` (blank -> `DEFAULT_LOVE_TITLE`, write first, then flow, then toast, allowed while paired); `setAnniversaryAndRecalculateDays` (refused while paired, `normalizeAnniversaryInput` null -> toast, flows/hearts/toast only after the write). `ProfileSyncViewModelTest` 17 -> 32 tests. Deviation: the midnight-clock test was dropped (`ShadowSystemClock.advanceBy` does not move `System.currentTimeMillis()`), so `refreshLoveDays_guestRow_keepsTheDateDays` only pins "same day stays the same"; the rollover is covered by the `loveDaysOn(date, nowMillis)` tests in `ProfileUtilsDatesTest`. Main check: source mtime 23:13:53, JUnit XML 23:15:40 (after the last edit): `ProfileSyncViewModelTest` 32/0, `ReminderWiringSettingsTest` 13/0, `EditCoupleDialogTest` 4/0, `OnlineCoupleProfileSlotTest` 7/0 (0 failures, 0 errors). |
| U8 | 1 | haiku / max | DONE | `MainActivity`: `LifecycleResumeEffect` calls `refreshLoveDays()` on resume; the profile dialog and top bar use `withCardFallback`. `EditMyProfileDialog` prefill uses `effectiveDisplayName`. Beyond the plan row: the Home avatar now renders through `ProfileAvatar` and the zodiac pill was touched. Wave 5 compile + targeted tests green (`EditProfileDialogTargetsTest`, `InLoveTopBarTest`). |
| U9 | 1 | haiku / max | DONE | `LoveHomeScreen`: D16 display helpers (`displayMeName`, `displayPartnerName`, `DEFAULT_LOVE_TITLE`) at the plan's lines. The agent listed one candidate for U23. `QuoteRotationTest` green. |
| U10 | 1 | haiku / max | DONE | `MilestoneBadgeTracker`: D16 helpers wrapped at collect. Follow-up by me: stale expectations in `BadgeRewardNoteTest` and `QuoteRotationTest` updated (reruns 10/0 and 6/0). |
| U11 | 1 | haiku / max | DONE | `ManualDateInputDialog` (`LoveDaysCalculatorWidget`): C.C2, C.C6 and D17 (`normalizeAnniversaryInput`); new `ManualDateInputTest`. Deviations: the error text has its own tag `text_anniversary_error`; an `isVietnamese` parameter; whitespace re-indent from the Dialog split. Main fix: the preset chips pass `isError = false`. |
| U12 | 1 | sonnet / high | DONE | `ui/components/AppWindows.kt` (`AppWindowLocals`, `AppDialog`, `AppAlertDialog` with a REQUIRED `scrollableText`, `AppDropdownMenu`) and `Theme.kt` (`UI_SCALE` visibility only). Written with no Gradle; main run: `AppWindowsTest` 5/0, `ThemeTokensTest` 2/0, `NotificationItemsTest` 6/0. |
| U13 | 1 | haiku / max | DONE | `AppPinLockScreen`, `AccountSecurityDialogs`, `AppModals`: 7 Dialogs -> `AppDialog`; E.E4 / E.E10 scroll. Beyond the plan: a third `verticalScroll` (LanguageSelectionDialog). Diff-reviewed. |
| U14 | 1 | haiku / max | DONE | `AuthScreen` (1 Dialog) and `SettingsScreen` (1 DropdownMenu, 3 AlertDialogs) -> wrappers; B.B7 / B.B11 on the Settings row. Diff-reviewed. |
| U15 | 1 | haiku / max | DONE | `CalendarScreen`: 2 DropdownMenus -> `AppDropdownMenu`; `shift(sign)` lambda; `Locale.ROOT`. Two imports (`LocalMinimumInteractiveComponentSize`, `CompositionLocalProvider`) are left unused (warnings only). |
| U16 / U16b | 1 | haiku / max | DONE | U16: `MilestoneBadgeTracker`, `AnniversaryMemoriesWidget`, `VerifyInviteDialog` (B.B9 localized descriptions). U16b (split off because of the file count; the plan's U16 row names them): `GiftScreen`, `MemoriesGridScreen`. Dialogs / AlertDialogs -> wrappers; 48dp / 32dp closes -> `MinTouchBox`. The `MemoriesGridScreen` diff also holds a peer's unrelated `Uri.fromFile(java.io.File(it.path))` hunk. |
| U17 | 1 | sonnet / high | DONE | `Material3DatePicker`: wrapper, E.E2 scroll + `heightIn(max)`, C.C9 field icon -> `MinTouchBox`, E.E12 nav buttons. NOT added: the `pickerOnly` / `yearRange` / `minMillis` / `maxDateMillis` params that the peer plan `2026-10-21-local-first-media-profile-ux.md` (T10, "depends on T5, U17 (or FP5)") expects; that plan has to add them itself. |
| U18 | 1 | haiku / max | DONE | `LoveDaysCalculatorWidget` (ManualDateInputDialog -> wrapper) and `NotificationsSheet` (keeps its own `AppDensity`: ModalBottomSheet; C.C9 banner buttons `heightIn(min = MinTouchBox)`; E.E11). The sheet header is taller now (57dp touch box). |
| U19 | 1 | haiku / max | DONE | `EditMyProfileDialog`: wrapper, own `AppDensity` dropped, B.B3 birthday via `InLoveDatePickerField`, B.B9 targets and description. Deviations: the field keeps the tag `input_my_profile_birth` (peer plan T11 expects `edit_profile_birthday`; it also expects the `pickerOnly` param, see U17); the English cancel button and the header close share the description "Close"; the birthday field colour changes from the dialog pink to the shared date-field `Primary`. |
| U20 | 1 | haiku / max | DONE | `ProfileAvatar` (`CommonComponents`): `SubcomposeAsyncImage` with loading / error slots drawing a tinted `AvatarFallbackIcon`; tag `profile_avatar_placeholder` only on the blank-url branch. `ProfileAvatarTest` 5 tests. |
| U21 | 1 | sonnet / high | DONE | `Dialogs.kt`: 3 Dialogs and 8 AlertDialogs -> wrappers (D13 table), `AppDensity` removed, C.C4, C.C8 a-f, placeholders, `Locale.ROOT`; partner search guarded (exception and stale-result guards). Deviations: the search button's `enabled` handling differs from the plan row (disabled while the query is blank, pinned by `EditCoupleDialogTest`); no "clears on edit" test; C.C8-g and E.E5 left. |
| U22 | 1 | haiku / max | DONE | `MainActivity` (2 hosts) and `PairingScreen` (1 AlertDialog) -> wrappers; dead host deleted (G.G2 / B.B6); `CoupleCodeShare` `runCatching` + toast (C.C16). Extension: further exact-size controls (44 / 48 / 50 / 52dp) -> `heightIn(min = MinTouchBox)`. Main fix: the 28dp clear-search X at `PairingScreen.kt:788` -> `Modifier.size(MinTouchBox)`. Exempt small pills: the paste chip, the interest pill. |
| U23 | 1 | haiku / max | DONE | `LoveHomeScreen` bottom cards: B.B1, B.B7, B.B8, B.B11, G.G4, A.A16 (age chips use `ProfileUtils.calculateAge(birth).takeIf { it > 0 } ?: storedAge`). Extension: the offline-partner zodiac through `formatZodiacDisplay`, and an overflow hedge on the pill rows (to be checked in the LDPlayer smoke). |
| U24 | 1 | main (sonnet) | DONE | `DensityWindowGuardTest` (3 tests) written by the main session: the brief would have been longer than the 60-line file. Skips `AppWindows.kt` by name; comment lines (`//`, `*`, `/*`) are ignored. Pre-scan: 0 raw windows; `AppDensity` only in `Theme.kt`, `AppWindows.kt`, `NotificationsSheet.kt`. Not matched: `DatePickerDialog`-named windows (`InLoveDatePickerDialog` is the app's own wrapper; check its scale in the smoke). |
| K3 | 1 | sonnet / high | DONE | Callers of the Cloud Functions client hardened; `AndroidCloudFunctionsClient` is unchanged (`CloudFunctionsErrorMappingTest.otherExceptionsPropagateUnchanged` pins it). Accept-invite site in `OnlineCoupleRepository`: `CancellationException` rethrown before the catch-all. `FirebaseRemoteAccountCleanup(functions, currentUid)` now goes through `CloudFunctionsClient` (20 s timeout + error mapping): recent-login -> `RecentLoginRequiredException`, any other failure -> a Vietnamese `IllegalStateException`. `AuthRepository.deleteCurrentAccount`: cancellation rethrown. Tests (K3 run): `RemoteAccountCleanupTest` 7, `OnlineCoupleAcceptInviteTest` 15, `AccountDeletionOrderTest` 4, `AuthRepositoryTest` 20, `CloudFunctionsErrorMappingTest` 12, all 0 failures. |
| wave 7 + 8 verify | - | main | GREEN | `:app:compileDebugKotlin` after waves 7 and 8: BUILD SUCCESSFUL in 1m 56s. `:app:testDebugUnitTest --tests "com.example.ui.*"` (2026-10-22): 37 suites / 223 tests / 0 failures / 0 errors, incl. `DensityWindowGuardTest` 3/0, `AppWindowsTest` 5/0, `EditCoupleDialogTest` 6/0, `EditProfileDialogTargetsTest` 6/0, `ProfileAvatarTest` 5/0, `ProfileSyncViewModelTest` 32/0. |
| U25 | 1 | sonnet / high | DONE | `EditCoupleDialogTest` 6 -> 10: clearing the stored partner birthday saves it blank (zodiac "" and age 0, name kept); a coupled user sees the read-only summary and no save button; an incoming invite shows the request card and hides search; an outgoing invite shows "Waiting for response..." + "Cancel request" + the own code and hides search. The fixed 3 s settle loop is gone: the helper awaits `restoreJob`, then polls with a 10 s deadline. Deviation: one extra `signedInUser` helper. |
| U26 | 1 | sonnet / high | DONE | `ProfileSyncViewModelTest` 32 -> 35: guest then sign-in (boy name from the online user, blank fields kept from the guest card, Room row untouched); signed-in never-paired user with a Room row (girl card stays empty, row kept); sign-out after pairing resets both cards. Quirks deliberately NOT asserted: a VM-only sign-out does not reset the love date or title (the data vault is not switched in a VM-only test), and an in-session guest to sign-in does not reset a Room-fed girl card. |
| U27 | 1 | sonnet / high | DONE | `OnlineCoupleProfileSlotTest` 7 -> 8: a MALE "me" is still partner1 and the sender is partner2 after accept; profile fields, `likesCsv`, `budgetMaxVnd`, `occasionRegion` and `loveTitle` of the existing row survive the REPLACE-insert. |
| wave 10 verify | - | main | GREEN | `:app:testDebugUnitTest --tests EditCoupleDialogTest --tests ProfileSyncViewModelTest --tests OnlineCoupleProfileSlotTest` (2026-10-22): BUILD SUCCESSFUL in 52s, GRADLE-EXIT=0; 10 / 35 / 8 tests, 0 failures, 0 errors, 0 skipped. |
| K4 | 1 | main (sonnet) | DONE | Crash-pattern sweep over `src/main/java` (excluding `monetization/*`, off-limits): `!!` (0 hits in scope), `.first()` / `.single()` (21 hits, all Room flows or guarded by `isNotEmpty()`), casts (platform contract only), `toInt()` on text (bounded by `\d{1,2}` / `\d{4}` regexes), date parsing (non-lenient + try/catch, or validated and clamped), `lateinit` (0), `String.format` / `SimpleDateFormat` (constant formats, never shared across threads), `startActivity` sites, both broadcast receivers, `PendingIntent` flags (all `FLAG_IMMUTABLE`), `@Update` on a missing row (a silent no-op: a correctness matter, not a crash), `insertOutboxEntry` (autoGenerate PK, safe). **One fix:** `MilestoneBadgeTracker.kt:894` divided by `badge.targetDays` unguarded; with `targetDays == 0` and 0 love days the result is NaN, which reaches `fillMaxWidth(progress)` and `"${(progress * 100).toInt()}%"` -> `targetDays.coerceAtLeast(1)` (CRLF kept; one token, no new branch, so no new test). **Findings for the owning sessions, NOT edited (untracked peer files / off-limits):** (1) `platform/AndroidPermissionService.openAppSettings` calls `startActivity` with no `ActivityNotFoundException` fallback (rare OEM crash; fix: try/catch and fall back to `ACTION_APPLICATION_DETAILS_SETTINGS`). (2) `data/repository/CloudPresetFetcher.kt:83` reads `targetDays` from a remote doc unbounded (`?: 100` only covers null; suggest `.coerceAtLeast(1)`), and its six `catch (e: Exception)` blocks swallow `CancellationException` (suggest the K3 rethrow; no crash today because the callers are wrapped in `withTimeoutOrNull`). (3) `ReminderAlarmReceiver.onReceive` SNOOZE / ALARM branches run synchronously with no try/catch (`showNotification` not audited); the async branches already `finish()` in `finally`. |
| U28 | 1 | main (sonnet) | DONE | Final verification, 2026-10-22. Compile green; legacy-placeholder grep gate: 5 hits, all allow-listed data defaults or language-branched fallbacks. Slice A (`com.example.ui.*`, `com.example.golden.*`): 57 suites / 338 tests / 0 failures. **Slice B hang:** the first data/domain slice never finished; `jcmd Thread.print` on the test executor showed `runBlocking` in `OnlineCoupleRepositoryPairingTest.updateMyProfile_blankAvatar_clears` joining every child of the injected scope, while a peer's uncommitted `OnlineCoupleRepository.mergeAvatarFromDao()` (local-first plan T21) starts a collector that never completes. Test-only fix (a disclosed exception to "a task touches only its owned files"): line 160 `scope.coroutineContext.job.children.forEach { it.join() }` became `repo.refreshState()` (it queues behind the init refresh on the same `stateMutex`); the class passes (6 tests). **Kotlin IC incident:** a killed run left a corrupt incremental cache (`ArrayIndexOutOfBoundsException` in `CompressedAppendableFile`); fixed by deleting only `app/build/kotlin/compileDebugUnitTestKotlin` while no Gradle was live. **FULL `:app:testDebugUnitTest` (13:40:55-13:42:38, not cached, summed suite time 84.7 s): 176 suites / 1708 tests / 0 failures / 0 errors / 0 skipped** (150 / 1458 before this plan; the rest comes from this plan's new tests and the peer's waves L1-L3). K3's five classes in that run: 7 / 15 / 4 / 20 / 12, all 0 failures. **SECOND FULL run** (`--rerun`, started 13:55:08, suite timestamps 13:55:12-13:56:38, summed suite time 86.6 s, `:app:testDebugUnitTest` executed, not UP-TO-DATE): **176 suites / 1708 tests / 0 failures / 0 errors / 0 skipped** again, so two consecutive green runs on the same tree. `:app:assembleDebug`: OK (APK 45,162,141 bytes, 13:45:48). **`:app:lintDebug`: 6 errors / 129 warnings (U0 baseline 0 / 128).** All 6 errors are `[FullBackupContent]` in the PEER's uncommitted wave-L1 files `res/xml/backup_rules.xml` (:5, :6) and `res/xml/data_extraction_rules.xml` (:6, :7, :11, :12): their new `<include domain="file" path="media">` makes the `<exclude>` lines for the DB and the prefs redundant. Sent to the owning session (b2) with the one-line fix (delete those `<exclude>` lines); NOT edited here. b2 applied it at about 14:00 (read-only check here: no `<exclude>` left, one `<include domain="file" path="media">` per section; its own `BackupRulesTest` was updated to match), so the lint rerun was needed. **FINAL combined run on the final tree** (`:app:assembleDebug :app:lintDebug :app:testDebugUnitTest --rerun --continue`, started 14:04:00, `BUILD SUCCESSFUL in 5m 18s`, 27 tasks executed / 76 up-to-date; suite timestamps 14:05:35-14:07:08 local, summed suite time 94.1 s, the test task executed, not UP-TO-DATE; copy of the XMLs in the session scratchpad `results4-FULL`): **176 suites / 1708 tests / 0 failures / 0 errors / 0 skipped**, now including b2's changed `BackupRulesTest` and its `AvatarSizing.kt` KTX edit (first compile of those lines: clean). **`:app:lintDebug`: 0 errors / 127 warnings** (U0 baseline 0 / 128): the 6 `FullBackupContent` errors are gone, b2's KTX edit cut the two `UseKtx` warnings (28 -> 26), `ExifInterface` x2 stay by b2's decision, and the 2 `IconDipSize` are the existing launcher `ic_launcher*.webp` files. `:app:assembleDebug`: OK, APK 45,162,141 bytes at 14:04:35 (SHA256 829CC332...20F5), copied to the scratchpad (`app-debug-final.apk`) and installed from that copy so a peer's Gradle cannot replace it mid-install; the compiled `res/xml/backup_rules.xml` inside it is the new 360-byte include-only file. The +1 warning: no U-task file gained a lint construct (the `.edit()` / `Uri.parse` counts in `AuthRepository`, `AlarmNotificationScheduler` and `MemoriesGridScreen` are equal at HEAD and in the working tree); the only new file with warnings is the peer's untracked `data/media/AvatarSizing.kt` (ExifInterface x2, UseKtx x2). **LDPlayer smoke: DONE** (2026-10-22 about 14:09-14:26 on emulator-5554, released to this session by thai-ki-05; values found and restored: no `wm size` override, density 480, font_scale 1.0, vi-VN; `com.thaiky.thai_ky` never touched). The final APK was installed from `app-debug-final.apk`; the app process (pid 29246) stayed the same for the whole smoke, even across the 320 dp size change. Main log: no `FATAL EXCEPTION`, `AppPluginBase onCreate failed`, `NoClassDefFoundError` or `ANR`. `logcat -b crash` holds only two native SIGSEGVs of the platform `cmd` binary (pid 841 and 1083, preceded by "IPCThreadState::self() during shutdown"; the adb shell `settings` / `wm` / `pm` helper on this Android 9 image, intermittent, not reproduced on demand), none from `com.aistudio.inlove.kmrv`. Covered, all at the app scale (every Dialog / menu window renders 0.85-scaled and keeps the in-app locale): Home (me card without the "Chưa rõ" pill, partner card with the "Ngoại tuyến" pill, 1391 love days, copy toast), `EditMyProfileDialog` in VI and EN, `InLoveDatePickerDialog` (day grid and year grid), the Pairing screen (code, copy / link / share icons, the system share chooser, link toast), the Calendar "+" `AppDropdownMenu`, the AddAnniversary and AddMilestone dialogs, the Memories empty state and its create dialog, the Gift inline preferences panel, the Notifications `ModalBottomSheet`, Settings as guest (rename-journey dialog and the guide dialog), the VI -> EN -> VI language switch (toasts visible, restored to VI), and a 320 dp pass (`wm size 960x1706`) over Settings, the profile dialog (fits, tight), Home (pill rows fit, the partner zodiac ellipsizes by design) and the date picker. Every dialog was cancelled, so no owner data changed; one mis-tap toggled the Memories "Yêu thích" view filter and it was reset to "Tất cả" (UI state, not data). **NOT reachable on this emulator (disclosed, not claimed):** the signed-in Settings row and the logout `AlertDialog`, the sign-out love-date reset, anything that needs scrolling (`input swipe` does not scroll here) or the keyboard, AddGiftReminder, SetPin / PIN fallback, and billing (no Google Play). Emulator end state: `wm size reset` (no override), density 480, font_scale 1.0, language VI, `thai_ky` installed; thai-ki-05 and b2 were told it is free. **The verified state is time-stamped:** it is the 14:04-14:09 tree; b2's wave L4 edits (Material3DatePicker.kt, MemoriesGridScreen.kt, new BirthdayField.kt and tests) started about 14:18 and are not covered by these numbers. **Post-merge confirmation (2026-10-22 16:43-16:46, after b2 reported its local-first plan finished, waves L4-L8 merged into the tree):** `:app:assembleDebug :app:lintDebug --rerun :app:testDebugUnitTest --rerun --continue` = `BUILD SUCCESSFUL in 2m 29s` (103 tasks: 2 executed, the `lintDebug` wrapper and `testDebugUnitTest`; 101 up-to-date; no other Gradle was live). The test task ran fresh (all 182 result XMLs stamped 16:46:18): **182 suites / 1746 tests / 0 failures / 0 errors / 0 skipped**, which closes the time-stamp caveat above for the unit suite (b2 reported the same totals from its own 16:36-16:40 run). Lint analysis and `assembleDebug` were UP-TO-DATE, i.e. identical inputs to b2's earlier run, so their outputs describe this tree: `lint-results-debug.txt` (16:33:52) = **0 errors / 128 warnings** (+1 vs the 127 above, not traced to a file; equal to the U0 baseline 128), APK 45,162,437 bytes at 16:28:25 (SHA256 05390B99...00BB). The device smoke was NOT redone on this final tree: `emulator-5554` was held by `com.thaiky.thai_ky` (foreground), so it was left alone; the last clean smoke remains the 14:09-14:26 one. |
