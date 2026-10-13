# B3 routing: ITEMS per TARGET (the lead's list; every entry is evidence to check, not truth)

Read only the section of your TARGET tag. These are items the CROSS-PART grep in `route-template.md` cannot find on its own. Source: the lead, 2026-10-13 14:30, from PROGRESS.md Nhật ký 05:18-13:36 and `_tools/ref-check.mjs` output. If `00-master.md` section 12 or `DECISIONS.md` section 2 disagrees with an entry, master wins.
Golden-vector (GV) rule: `p8-feature-parity-matrix.md` section 4 and 4.1 (lines ~267-325) are the authority; P8 ids and file names are official, other parts rename. Ranges: GV-001..049 P8, GV-050..059 P5, GV-060..069 P6, GV-200..299 P2.

## p1
- Nothing beyond the auto-collected CROSS-PART lines (F-25 project.pbxproj synchronized-group note, F-30 pure-move limitation, foundation cycles F-31/F-32, `:shared` must expose kotlinx-datetime to `:app` through `api`).
- Simulator naming: if `00-master.md` section 12 names ONE variable and its owning task, apply it here when the task is a P1 task.

## p2
1. Prose still says `D-24` / `D-25` (about 10 hits each: lines ~45, 74, 77, 103, 106, 110 ...) although the tasks are `D-24a`/`D-24b` and `D-25a`/`D-25b`. Replace each with the right half. Find them: Grep `D-2[45]` and keep hits not followed by `a`/`b`.
2. P8 section 4.1 rows that touch P2: D-05/D-09 file `GV-003-love-time-breakdown.json`; D-02 writes `GV-012-holiday-vn-fixed.json`, `GV-013-holiday-vn-lunar.json`, `GV-014-holiday-western.json` and D-11 reads them (P2 says the GV-012 `today` rows live in GV-204 under D-46: make both statements agree); GV-015 file `GV-015-holiday-refresh.json` (D-54) plus the boundary names `custom-row-same-title-collision`, `title-nfd-not-matched`, `idempotent-second-call-no-writes`; D-04/D-20 split into `GV-025-memory-sync-map.json` and `GV-026-anniversary-sync-map.json`; GV-201 (OutboxJson) and GV-202 (UrlSafety, 28 cases) are P2-owned, confirm; D-36 status.
3. D-35 keeps `GoldenWriter` and P8's generator classes (V-01, V-04, V-05, V-07, V-08) until V-15 confirms their replacement; V-15 runs `:app:compileDebugUnitTestKotlin` after D-35. P2 triage says this is done; confirm it is in the task row.
4. (UNVERIFIED lead, P4 GAP report, lead 2026-10-13 16:00) D-30 / `InLoveData` / `DataContainer` should hand out `networkMonitor()` (the `PushedNetworkMonitor`) and `keyValueStores()`. P4 U-50b and U-100a read the monitor, and P3 B-50 reads `KeyValueStores.open("inlove_auth_prefs")`. Check whether D-27 / D-30 already expose them under these names; if not, add them to the D-30 text and tell P4 and P3 with `CROSS-PART:` lines.
5. (UNVERIFIED lead, lead 2026-10-13 ~15:50) D-42 has a new reader: P4 U-124 (deps now include D-42) calls `applicationContext.inLoveDao()` in the debug `MainActivity` for the fixture login (Option F). Keep the accessor names `Context.inLoveDatabase()` / `Context.inLoveDao()` in `InLoveDatabaseAccess.kt` and the gate "`AppDatabase.getDatabase(` only in that file"; only list U-124 among the callers in the D-42 text.

## p3
1. Six hints from the cut-off Codex run r1-p3 (UNVERIFIED leads, not review results; check each in p3 and the code, and log accept/already/partial/reject):
   a. P3 defers the entitlement check for media upload; the lead's decision is "upload is NOT VIP-only in v1 (App Check + MediaQuota; M-25 `requireEntitlement` wired for future callables)". P3 must state this in its design text and a DECIDED line.
   b. P3 relies heavily on cross-part handoff for entitlement, persistence and app start; check that each handoff names the receiving task id.
   c. Blocker candidate: `OAuthProvider(providerID: "facebook.com")` against the current Firebase iOS pin (P6 notes `Package.swift:48` range vs Firebase 12.19.2); verify the API and pin from the official Firebase iOS docs.
   d. iOS media returned from cache may be purged by the OS; is there a durable save step before the app uses the file?
   e. MQ-202 needs upload resume after a process kill, but the use case persists only after the upload completes; add a persisted pending-upload record or explain why not needed.
   f. App Check start-up has two owners (P3 B-72 and P1/P5); P6 triage says B-72 owns `FirebaseApp.configure` and provider install: make P3 the single owner and expose the factory hook for P6 M-69b.
2. P8 section 4.1: B-20 renames `GV-018-pin-legacy`/`GV-020-pin-v2` to `GV-018-pin-v2.json` and `GV-020-pin-legacy.json`; B-49's command only calls `:shared:testAndroidHostTest`: confirm the same five files (GV-017..GV-021) also run in `:shared:iosSimulatorArm64Test`, else add it.
3. From P2/P6 (auto-collected too): B-23..B-29 catch `VaultStorageException` before `_authState` flips; B-63/B-62/B-64 listener errors go to `onError` (test `listen_errorCallsOnErrorNotOnSnapshot`, BRIDGE-LISTENERS gate); B-24 vs D-26 ordering on `InLoveApplication.kt`; classify every new callable in `src/entitlement/gatePolicy.js`; AuthRepository demo fixtures that default to USER_VIP plus `updateUserSubscription` must go.
4. (UNVERIFIED leads, P4 GAP report, lead 2026-10-13 16:00) P4 reads three P3 contracts that the P3 text may leave open. Check each in p3 and the code, then state the missing facts in the task row (or add `CROSS-PART: p4 ...`):
   a. B-82 `MediaPicker`: suspend vs callback, the type of `PickedMedia.kind`, the exception names, the camera-denied model, and video cover `null` (P4 U-101c `IosMediaPickers : MediaPickers` wraps `cloud.mediaPicker`; B-83 is the Swift PHPicker gate).
   b. B-65 `IosCloudServices`: field names (including `mediaPicker`) and the source of `memoryMediaRepository` (P4 U-100a `buildIosAppDependencies(scope, cloud: IosCloudServices, reminderScheduler, entitlementVip)`).
   c. B-46 `blockSender` / `reportSender`: return type or failure mechanism, and the maximum reason length (P4 U-107 sends reason codes `spam`, `harassment`, `fake_account`, `other`, at most 20 characters; read `firestore.rules` of B-17 to confirm the server limit).
5. (Already applied by the lead 2026-10-13 ~15:50, do NOT undo; only check that the other p3 text agrees) Debug fixture login, Option F: B-27 note (each `DebugFixtures` function takes `dao: InLoveDao` and uses only public members), new row B-27b (P2; deps B-27, U-124, B-43; deletes the delegating members from `AuthRepository`, moves `CloudTestFixture` to `src/debug`, adds `noFixtureFunctionsOutsideDebugSourceSet` to `NoHardcodedCredentialsTest`), B-50 depends on B-27b, and P4 U-124 provides `LocalDebugFixtureLogin` from the debug `MainActivity` using `debugFixtureLogin(authRepo, dao)`. Look at p3 L27 (assumption 11), L150 (inventory row) and any sentence that still says `src/release` keeps empty failing functions after B-27b.

## p4
1. Dangling reference: `N-07` at about `p4-ui-compose.md:454`; P5 only has `N-07a`. Point to the right id, or add `CROSS-PART: p5` if the meaning differs.
2. From P2: U-27 must depend on D-05 and D-53; the U-26 `legacyLenient` label is wrong (both legacy parsers use `isLenient=false`); decide the Thai Buddhist-year stored text and `parseDate` years above 2400.
3. From P6: U-47/U-46 file ownership for M-54.
4. `### 6.8` (16 rows U-100a..U-123) comes from the GAP-closure agent (agent `a574af047f84d0ff1`); if it exists, do not undo it, and check that section 9.1 no longer lists those 16 as GAP.

## p5
1. Dangling references: `N-07` at `p5-native-features.md:298` and `:329`; P5 only has `N-07a`. Fix the text (or add the missing row) and tell P4 which id is right.
2. `N-41` is requested by P8 (`p8-feature-parity-matrix.md:57`, `:590`): `DeepLinkTarget` with destinations `calendar` and `home`, unit test `T+:DeepLinkTargetTest`, manual MQ-002. P5 has no N-41. Add a task (next free id) or map to an existing task, then give P8 the final id with `CROSS-PART: p8 ...`.
3. P8 section 4.1 renames: N-04b/N-08b write/read `GV-004-milestone-selection.json` (was `GV-003-love-milestones.json`), `GV-009-anniversary-text-parse.json` (was `GV-010-date-text-parse.json`), `GV-010-occurrence-date.json` (was `GV-009-occurrence-dates.json`); `GV-011-occurrence-zones.json` slug is free; `GV-050-reminder-plan.json` stays as an alias of GV-028 (N-06, N-08b); GV-051 (N-04c) and GV-052 (N-31a) stay P5-owned.
4. From P2 (auto-collected too): N-31a and N-19 read the D-32 snapshot shape (4.8), N-35 file protection for `widget/` and `thumbs/`, N-19/N-19b expose the reload coalescer D-44 calls; P5 text about GV-205 is obsolete (GV-205 was dropped; GV-052 stays P5-owned).
5. From P6: `project.pbxproj` sequencing for the Gateways folder and local package (M-95).

## p6
1. P8 section 4.1: confirm `GV-060-plan-catalog` (M-10) and `GV-061-entitlement-resolve` (M-11) inside 060..069; GV-062 and GV-064 are generated by M-49b.
2. From P2: a tamper test through TrustEvaluator, EntitlementStore and server `resolveEntitlement` (tampered Room `isVip`, `role`, `subscriptionTier` must not grant paid access).
3. Open ids that P6 itself was still adding at 14:30 (`M-93`..`M-99`): re-run `_tools/ref-check.mjs`; any `M-` id still dangling in p6 is a P6 bug to fix first.
4. (UNVERIFIED lead, P4 GAP report, lead 2026-10-13 16:00) M-69a / M-69b: say exactly how Swift hands `IosMonetization` and `IosBannerContent` to `MainViewController` (P4 U-100a and U-101 read them) and the member names the UI reads, `billing` and `entitlement.isVip`. Name the task and file that creates the `IosMonetization` type; if M-69a/b already do, copy the exact names into the row text.

## p7
1. Grep p7 for every `GV-nnn` name and make it match P8 section 4 / 4.1 official names and ranges.
2. From P6: privacy label inputs for SDK analytics events (iOS v1 sends none; skipped list).
3. Simulator naming: Q-41 forbids hardcoding; apply the single variable chosen in `00-master.md` section 12 (P3/P5 say "iPhone 16" `IOS_SIM_DEST`, P4 says "iPhone 17" `IOS_SIM_DEVICE`).
4. (Optional, UNVERIFIED lead, P4 GAP report) Q-47 `docs/release/MODERATION_RESPONSE.md`: the public moderation contact address needs a named placement in the app and in ASC. P4 U-107 (report and block dialog, `InviteSafetyDialog.kt`) is the natural place; name it so MQ-610 (P8) can check it. If U-107 does not show the address, add `CROSS-PART: p4 U-107 ...`.

## p8
1. Section 4.1 still says "Master pass chọn một hướng": record the decision (P8 ids and file names official, other parts rename) and mark each row DECIDED once the owning part's x1 fragment says accept.
2. From P2: GV-012 `today` rows live in GV-204 (18 cases); GV-016 output is `rankedIds` + `matchesAnyInterestIds` + `fitsOccasionIds`; GV-202 has 28 cases; GV-025 gains `receive-missing-keys-use-defaults`; the lines that name generators D-35 deletes are obsolete (about L265, L269, L270, L280, L284, L290, L293, L294).
3. From P6: wording of MQ-109, FP-807 (real UIKitView spike) and DEC-06 / FP-813 (no SDK analytics on iOS in v1).
4. Once P5 chooses the id for the `DeepLinkTarget` task, replace the dangling `N-41` at `:57` and `:590`.
5. (UNVERIFIED lead, P4 GAP report, lead 2026-10-13 16:00) FP-626 (block and report the invite sender) must list `U-107` among its deps: P4 U-107 is the UI attach point (`InviteSafetyDialog.kt` + `PairingInviteSection.kt`, incoming-invite cards). FP-622 cites `VerifyInviteDialog.kt:67`, which P4 says is unreachable from those cards; check in the code and fix the attach-point text. MQ-610 should then name the incoming-invite card.
6. (UNVERIFIED lead, P4 A66) MQ-206 / PF-012 expect a relative avatar path, but P4 stores the absolute `file://` URI and rebases it on read (U-101d / U-103, `rebaseContainerUrl` after the container UUID changes). Make the P8 wording (FP-105, FP-625, MQ-206, PF-012) agree with P4's rule.
