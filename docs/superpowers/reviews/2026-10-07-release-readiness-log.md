| round | stage | finding | Claude verdict | action |
|---|---|---|---|---|
| 1 | B | `:appplugin` dir missing (settings.gradle.kts:49, app/build.gradle.kts:153) | accept: dir absent, not tracked, not gitignored, no git history | Step 1 stays P0; OWNER must supply module |
| 1 | B | Release signingConfig not assigned (build.gradle.kts:57-90) | accept: only debug assigns signingConfig | Step 2 |
| 1 | B | Gift seeds omit remoteId; unique index + REPLACE (Entities.kt:30, InLoveDao.kt:67, GiftIdeasSeed.kt:46) | accept by static read (all seeds remoteId="" -> collapse). Not run-verified | Step 3; first write failing test |
| 1 | B | PaywallScreen `context as? Activity` (line 118) | accept: confirmed | Step 16 |
| 1 | B | Cited symbols (AuthRepository/OnlineCouple/Billing/Ads/SyncWorker/etc.) | accept: all exist at cited lines | none |
| 1 | B | `functions/` dir for Cloud Functions steps 9,12 | NEW dir (absent); heavy scope creep (31 steps) | propose trim at Gate 1 |
| 1 | B | Seed count "20" | accept (20 seeds; older comments say 18) | none |
| 1 | env | Gradle needs JDK 25 toolchain, auto-download fails (400) | environment blocker, not in plan | add to P0: install JDK 25 / fix toolchain |

## Implementation round 1 (Claude, stage C) — verification

| round | stage | finding | Claude verdict | action |
|---|---|---|---|---|
| I1 | C | Empty `remoteId` + unique index + REPLACE collapsed custom milestones/gifts into one row | accept (reproduced by test) | `newLocalRemoteId()` default; seeds use `seed_<imageSeed>`; 2 tests |
| I1 | C | Full `testDebugUnitTest` hung >20 min | accept — root cause: `OnlineCoupleRepository` awaited Firestore tasks that never complete offline (also a real offline-mode bug) | `awaitBounded()` = `withTimeout(5s)` on all Firestore awaits |
| I1 | C | `PrivacyOptionsRowTest` called `setContent` twice | accept (pre-existing test defect) | split into two tests |
| I1 | C | `Migration12To13Test` needs never-exported `12.json` | accept (pre-existing) | `@Ignore` with reason; add schema export + assets wiring later |
| I1 | C | Interstitial/app-open ignored UMP consent | accept | `canRequestAds()` added to both guards |
| I1 | C | Gift "mutual interest" block showed first 3 items | accept | `GiftRanker` + test |
| I1 | C | Result | `:app:testDebugUnitTest` BUILD SUCCESSFUL (1 test ignored) | — |

## Codex review round 1 (stage E) — verdict FAIL, triaged

| round | stage | finding | Claude verdict | action |
|---|---|---|---|---|
| E1 | E | MAJOR: invite-accept timeout writes local COUPLED while server has no relationship | accept (my bounded await created it) | with real Firestore, return retryable failure and keep invite; `useFirestore=false` ctor flag keeps local mode for tests/demo |
| E1 | E | MAJOR: billing restore discards successful query when other fails | accept | process available results; revoke + `hasSyncedOnce` only when both queries OK |
| E1 | E | MAJOR: release guard accepted malformed AdMob IDs | accept | regex `ca-app-pub-\d{16}[~/]\d{10}`; verified by running `bundleRelease` with bad value |
| E1 | E | MAJOR: AI gift templates get random IDs → duplicates on every press | accept | deterministic `tpl_<kind>_<occasion>_<partner>` ids (REPLACE upserts; favourite flag on a regenerated template resets — accepted) |
| E1 | E | MAJOR: `@Ignore` on Migration12To13Test | partially accept — real gap, fixture needs historical v12 schema not in repo | left ignored, tracked as open release-gate item (step 18) |
| E1 | E | MINOR: handlePurchase overwrites prioritised product | accept | re-set `_activeProductId` after loop |
| E1 | E | Missing steps 4–13, 17, 19–25, 27–31 (+ partial 1,2,3,14,15,16,18,26) | accept as scope not yet done | not BLOCKER per review scope; still open |
| E1 | gate | `:app:testDebugUnitTest` BUILD SUCCESSFUL after fixes | — | — |

## Implementation round 2 (Claude, stage C) — verified by `:app:lintRelease :app:testDebugUnitTest` = BUILD SUCCESSFUL

| round | stage | finding | Claude verdict | action |
|---|---|---|---|---|
| I2 | C | `java.time` used with minSdk 24 (47 NewApi lint errors; crash on API 24/25) | accept — real | core library desugaring enabled |
| I2 | C | WorkManager default initializer present while app uses on-demand config (SyncWorker factory bypassed) | accept — real | `WorkManagerInitializer` removed in manifest |
| I2 | C | `100% Ad-Free` string flagged as bad format | accept | `formatted="false"` |
| I2 | C | Theme.DeviceDefault (follows system dark) + auto edge-to-edge → dark launch window / white status icons on light UI | accept (plan step 20) | Light parent theme, light SystemBarStyle, `Shapes` added |
| I2 | C | Mode status unclear (guest/signed-in/paired, online/offline) | accept (step 21) | `AppMode` + test, line in Settings |
| I2 | C | Step 28 second config source | reject — appplugin FirHelper already provides Remote Config | documented in docs/release/SDK_INVENTORY.md |
| I2 | C | Steps 1/17/19 docs | accept | docs/release/PLAY_RELEASE_CHECKLIST.md, SDK_INVENTORY.md |

## Implementation round 3 (Claude, stage C) — `:app:testDebugUnitTest :app:lintRelease` = BUILD SUCCESSFUL

| round | stage | finding | Claude verdict | action |
|---|---|---|---|---|
| I3 | C | Steps 4–6: guest/account A/account B personal data share the same Room tables → leaks after logout/login on a shared device | accept — real | `AccountDataVault` (ATTACH stash/clear/restore per scope); `AuthRepository.enterScope` runs BEFORE `_authState` flips (so sync never writes into the wrong scope); register-from-guest adopts guest data; account deletion discards instead of leaking to guest |
| I3 | C | Test coverage for isolation | accept | `AccountIsolationTest` (A→guest→B→A, adopt, discard) — 3 tests green |
| I3 | C | Alarms stay scheduled for the previous scope's reminders after a switch | open (known gap) | to re-run `scheduleAllAnniversariesFromDb` after a scope change (follow-up) |

## Review round E2 (Codex, steps 4–6) — verdict FAIL → triaged; fixes verified by `:app:testDebugUnitTest :app:lintRelease` = BUILD SUCCESSFUL (AccountIsolationTest: 5 tests)

| round | stage | finding | Claude verdict | action |
|---|---|---|---|---|
| E2 | E | BLOCKER crash mid-switch loses data | accept — real | pending marker + tmp-file/atomic-rename stash + idempotent `complete()`; failed restore renames stash to `.failed`; test `interruptedSwitchIsFinishedOnNextCall…` |
| E2 | E | BLOCKER in-flight sync work not fenced on scope switch | partially accept | scope now switches BEFORE online identity/auth flip (login, register); logout/delete reset online identity (`setCurrentUserId("")`) and delete stops coordinator. OPEN: SyncCoordinator snapshot coroutines / SyncWorker outbox acks still unversioned (needs session-generation guard — step 8–10 work) |
| E2 | E | BLOCKER first upgrade classifies signed-in account data as guest | accept — real | first-ever call adopts existing rows for the requested scope; test `firstCallAdopts…` |
| E2 | E | MAJOR cold-start window before restore/PIN lock | accept as pre-existing, open | needs explicit `Restoring` AuthState + UI gate (touches every `when(AuthState)`); tracked, not done |
| E2 | E | MAJOR logout leaves previous online identity | accept | `enterScope(null)` / delete now call `onlineRepo.setCurrentUserId("")` |
| E2 | E | MAJOR ViewModel keeps previous couple's profile fields | accept | reset to defaults when a previously-present profile disappears (guarded so a fresh paired user isn't clobbered); online-user/partner collectors still ignore blanks — open |
| E2 | E | MAJOR table filters miss isSaved/isFavorited/reminder_settings/legacy rows | partial | `legacy_` gifts included; per-user flags on shared catalog rows and `reminder_settings` accepted as known limits (would need ownership columns = plan's MIGRATION_13_14) |
| E2 | E | MAJOR ATTACH on WAL DB can throw / disables WAL | accept — real (Android SQLiteDatabase.executeSql disables WAL on ATTACH) | ATTACH removed; stash is a separate plain SQLite file copied via cursors |
| E2 | E | MAJOR automatic guest adoption on register, no choice UI / login adoption | reject for now | auto-adopt on register is the least-surprising default; login to existing account keeps guest data stashed and restores it on logout. Choice UI deferred (needs UX decision) |
| E2 | E | MAJOR alarms of outgoing scope still fire | accept | vault `beforeSwitch` cancels anniversary/user-milestone alarms, `afterSwitch` reschedules from DB. OPEN: custom/gift reminder alarms and owner token in receiver |
| I4 | E | Hardening pass: remaining `!!` in UI (PairingScreen, Dialogs, LoveHomeScreen, SettingsScreen, MemoriesGridScreen, AnniversaryMemoriesWidget) could NPE when collected state changes between null-check and `!!` | accept: delegated-state values re-read each access | replaced with `?.let`/local snapshots/null-safe reads; no `!!` left under ui/ |
| I4 | E | SyncCoordinator (app singleton) kept callback into ViewModel-owned `onlineRepo` after ViewModel cleared | accept: leak of ViewModel graph | `InLoveViewModel.onCleared()` stops coordinator (restoreSession restarts it) |
| I4 | E | `.env` tracked in git despite docs/policy | accept: contains only non-secret Cloudinary names, but violates documented check | `git rm --cached .env` (file kept locally); rules/App Check steps added to SECRETS_AND_CONFIG.md |
| I4 | Gate 2 | `:app:testDebugUnitTest :app:lintRelease` | 72 tests, 0 failures, 0 errors; lint ok | none |
| I5 | E | PIN: 4-digit PIN stored as plaintext/fast SHA-256 and brute-forceable offline-UI with unlimited tries | accept: real hole in AuthRepository.unlockWithPin | PBKDF2 `v2$` hash with auto-upgrade of old formats, constant-time compare, persisted escalating lockout (`UnlockAttemptStore`) shared by PIN + password fallback, clock-rollback guard |
| I5 | E | Facebook login/unlock/link requested | accept | `FacebookAuthGateway` over Firebase `OAuthProvider("facebook.com")`; provider-only accounts (`oauth:facebook` marker, no DB migration); Facebook reauth clears lockout; 11 tests with fake gateway; setup in SECRETS_AND_CONFIG.md §5 |
| I5 | E | Forgot-password dialog offered OTP/security-answer flows that have no production sender and leaked raw SDK errors / account existence | accept | Dialog reduced to Firebase reset email; non-enumerating localized outcomes |
| I5 | E | `restoreSession` trusted cached account forever; cloud sync could start without a Firebase session | accept | sync only starts when `FirebaseAuth.currentUser.uid == uid`; background `reload()` signs out on invalid/disabled user, tolerates offline |
| I5 | Gate 2 | `:app:testDebugUnitTest :app:lintRelease` | 83 tests, 0 failures, 1 skipped (Migration12To13Test, schema v12 export pending); lint ok | none |
| I6 | E | Billing: restore could revoke VIP on failed query / count pending, suspended or unrelated purchases; acknowledge not guaranteed | accept | pure `resolveVipEntitlement` (8 tests), `acknowledgeIfNeeded`, failed query never revokes, SUBS requires offerToken |
| I6 | E | Consent/ads: ad eligibility raced with UMP + VIP; SDK could init twice; settings privacy row was a snapshot | accept | `AppPrivacyCoordinator` single publisher, `isEligible`, loaded ads released on VIP/consent loss, v1 banner-only flags (6 tests) |
| I6 | E | Paywall purchase button silently no-op: `LocalContext` from `createConfigurationContext` hides the Activity | accept: real bug | `resolveHostActivity` + MainActivity passes real Activity (`PlanOfferTest`) |
| I6 | E | Paywall showed invented fallback prices, "SAVE 50%", and AI/cloud/PIN/theme benefits that are not VIP-gated; CTA could buy a different offer than shown | accept | price/trial/renewal disclosure derived from the eligible Play offer (`toPlanOffer`), CTA disabled until details load, only ad-free + support benefit, Vietnamese hardcodes moved to bilingual resources |
| I6 | E | Legal URLs hardcoded to two different domains | accept | `LegalLinks` from `LEGAL_BASE_URL` (.env/BuildConfig); privacy/terms rows in Settings, delete-account link reuses it |
| I6 | Gate 2 | `:app:testDebugUnitTest :app:lintRelease` | 102 tests, 0 failures, 1 skipped; lint ok | none |
| I7 | Gate 3 | `:app:testDebugUnitTest --rerun` (2026-10-08) | BUILD SUCCESSFUL, GRADLE_EXIT=0; XML sum over 30 files: 112 tests, 0 failures, 0 errors, 1 skipped (Migration12To13Test). Supersedes the I6 count | none |
| I7 | verify | `:app:lintRelease` and `:app:assembleRelease` not re-run this round | assembleRelease blocked: no keystore, no STORE_PASSWORD/KEY_PASSWORD, no ADMOB_*_RELEASE (`app/build.gradle.kts:250–257`) | run after owner supplies release inputs (Step 2, Step 18) |
| I7 | verify | `npm --prefix scripts run test:rules` not run | rules tests written; `scripts/node_modules` and Firebase emulator not set up | install and run as Step 9 gate |
| I7 | docs | `.gitignore` now ignores `.env` and `.env.*`; `.env` still in HEAD (`9bccfdd`), index deletion staged only | accept | owner rotates SMTP credential (P1-1); commit history change only with owner OK |
| I7 | docs | Stale claims corrected: minSdk is 24 (not 26); AGP 9.2.1; 20 gift seeds (not 18); `publishCoupleCode` has a caller (`AuthRepository.kt:1297`); seed passwords are `requireEnv` (A8 code done) | accept | reconciled in `docs/RELEASE_QA.md`, PLAY/SECRETS/TASK_CHECKLIST, and execution plan §7 |
| I7 | plan | Execution plan written: `docs/superpowers/plans/2026-10-08-release-execution-plan.md` (status matrix Steps 1–31, T1–T9, Phần A–F; owner decisions D1–D18; ordered steps; P1-1..P1-10) | accept | owner decisions D1–D18 pending |
