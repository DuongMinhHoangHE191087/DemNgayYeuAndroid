# Pha 1 owner-free plan (exec 6-14), 2026-10-09

Source: `docs/superpowers/plans/2026-10-08-release-execution-plan.md` §4, Pha 1 steps 6-14.
Planner: Opus, read-only. No Gradle was run while writing this plan, so every "passes" claim below is UNVERIFIED until W2 runs.

## Goal

Close the owner-free Pha 1 items. A code read shows that the uncommitted worktree already implements most of them: exec 6, 7, 8, 9, 10, 12 and 14. The real work left is:
- exec 11: the VIP banner in `SettingsScreen.kt` still uses raw hex colors. Swap them for theme tokens and keep every hex value the same.
- exec 14: confirm that the untracked `12.json` really matches the shipped v12 schema.

After that, one Gradle and npm gate run proves the whole set. The plan adds no new abstractions, no schema change and no behavior change.

## Assumptions

| # | Claim | Status | Evidence |
|---|---|---|---|
| A1 | The gift seed catalog is upserted by remoteId on every start. Legacy rows with a blank remoteId are adopted, and they keep their id and favorite flag. | VERIFIED | `InLoveRepository.kt:73`, `:88-102` |
| A2 | Tests already cover exec 6: a reseed keeps 20 rows and every favorite, and adopting a legacy row keeps its id. | VERIFIED | `CloudEnrichmentDataTest.kt:115-128`, `:131-143`; `PresetDedupeTest.kt:36-49`, `:52-71` |
| A3 | `SyncWorker` drains the whole queue. A row with a null `relationshipId` throws and is kept with `markOutboxAttemptFailed`, never dropped. Any failure returns `Result.retry()`. | VERIFIED | `SyncWorker.kt:53`, `:57-69`, `:80-81`, `:88-89`; `InLoveDao.kt:160-161`, `:171-172` |
| A4 | Tests already cover exec 7: a poison row is kept, 25 writes drain in one run, a failing row does not hold back healthy rows, and a scope switch acknowledges no row. | VERIFIED | `SyncWorkerTest.kt:56-106`, `:109-127`, `:130-153`, `:156-178` |
| A5 | The privacy gate and the media upload live in use cases. The UI only maps `privacyLevel` to `wantsShare` and outcomes to toasts. | VERIFIED | `MemoryMediaUseCases.kt:14`, `:39-55`, `:66-71`; `InLoveViewModel.kt:57-58`, `:1306-1373` |
| A6 | No `ui/` file calls Cloudinary. The only upload path is `FirebaseMemoryMediaRepository.postToCloudinary`. | VERIFIED | grep "Cloudinary" in `ui/`: comments only (`MainActivity.kt:506`, the privacy UI in `MemoriesGridScreen.kt`); `FirebaseMemoryMediaRepository.kt:53`, `:70`; `AppServiceLocator.kt:115-118` |
| A7 | `MemoryPrivacyTest` covers the use cases with 11 tests: private, unpaired, signed-out, invalid media, upload failure, publicId binding, text-only, store failure, cancellation, and resolving for the own couple vs another couple. | VERIFIED | `app/src/test/java/com/example/data/repository/MemoryPrivacyTest.kt` |
| A8 | Production password recovery is Firebase email only. The OTP branch runs only when `isTestMode` is set, and production never sets it. | VERIFIED | `AuthRepository.kt:66`, `:650-687` (`:669` is `sendPasswordResetEmail`); `InLoveViewModel.kt:344`; `AuthScreen.kt:1163-1169` |
| A9 | `resetPasswordWithOtp` and `resetPasswordWithSecurityAnswer` are deleted, and a reflection test enforces that. | VERIFIED | `AuthRepositoryTest.kt:146-151` |
| A10 | The default security answer [value omitted under R5] is reachable only through the debug test fixture. | VERIFIED | `AuthRepository.kt:139-141` ([value omitted under R5] returns early), `:168` |
| A11 | `upgradeSubscription` has no definition or caller left. | VERIFIED | grep `upgradeSubscription` in `app/src`: 0 matches |
| A12 | No `purple_` or `teal_` colors remain. The only match is the test's own filter string. | VERIFIED | grep in `app/src`: only `app/src/test/java/com/example/ui/theme/ThemeResourcesTest.kt:12` |
| A13 | The VIP banner still uses 7 raw hex values. | VERIFIED | `SettingsScreen.kt:701`, `:705`, `:721`, `:737`, `:756` |
| A14 | `#FFF0F5` already exists as the token `SurfaceContainerLow`. The other five hex values (`#FFF8E1`, `#FFD54F`, `#FFB300`, `#B78103`, `#F48FB1`) have no token. | VERIFIED | `ui/theme/Color.kt:10`; `Color.kt:6-63` grep |
| A15 | `SettingsScreen.kt` imports theme tokens one by one, not with a wildcard. | VERIFIED | `SettingsScreen.kt:95-103` |
| A16 | No Roborazzi or screenshot test covers `SettingsScreen`. The only screenshot test is the greeting one. | VERIFIED | `GreetingScreenshotTest.kt:30` is the only `captureRoboImage` |
| A17 | The duplicate Cloudinary services are deleted, and nothing references them. | VERIFIED | git status shows `D` for all three files; grep `CloudinaryMediaService\|CloudinaryStorageService\|ICloudinaryMediaService` in `app/src`: 0 matches |
| A18 | `Migration12To13Test` is no longer ignored. | VERIFIED | grep `@Ignore` in `app/src/test`: 0 matches; `Migration12To13Test.kt:24` |
| A19 | `MigrationTestHelper` can load the schemas: they ship as debug assets, and Android resources are on for unit tests. | VERIFIED | `app/build.gradle.kts:107-111`, `:122` |
| A20 | `12.json` exists, but it is untracked and only `13.json` is in git. | VERIFIED | `git status --short`: `?? app/schemas/.../12.json`; `git ls-files app/schemas` lists only `13.json` |
| A21 | `12.json` (identityHash `3e9fa155bb1c54d94705f6af3bf5a755`) matches the schema of the last v12 commit. Version 12 was introduced in `0c0723e` and replaced in `322ec07`, so `322ec07^` is the last v12 state. | UNVERIFIED | provenance unknown; T2 checks it |
| A22 | The existing tests pass on the current worktree: CloudEnrichmentDataTest, PresetDedupeTest, SyncWorkerTest, MemoryPrivacyTest, AuthRepositoryTest, ThemeResourcesTest and Migration12To13Test. | UNVERIFIED | no Gradle run; no result XML in `app/build/test-results` |
| A23 | `:app:compileDebugKotlin` passes after the Cloudinary deletions and the `upgradeSubscription` removal. | UNVERIFIED | no Gradle run |
| A24 | `npm --prefix scripts run test:rules` runs: it needs the Firebase CLI and the Firestore emulator. | UNVERIFIED | `scripts/package.json:10` |
| A25 | `npm --prefix firebase/functions test` has test files to run. | UNVERIFIED | `firebase/functions/package.json:10` (`node --test "test/**/*.test.js"`) |
| A26 | `:app:assembleRelease` fails on missing owner inputs: keystore, STORE_PASSWORD, KEY_PASSWORD and the AdMob release IDs. | UNVERIFIED | release plan §4 Pha 0 step 3 |

## Tasks

| id | files (owned) | change | test or command | depends on | model |
|---|---|---|---|---|---|
| T1 | `app/src/main/java/com/example/ui/theme/Color.kt`, `app/src/main/java/com/example/ui/screens/SettingsScreen.kt` | exec 11. Append 5 tokens to `Color.kt` after `:63`, keeping each hex value: `VipGold = Color(0xFFFFB300)`, `VipGoldContainer = Color(0xFFFFF8E1)`, `VipGoldBorder = Color(0xFFFFD54F)`, `VipGoldText = Color(0xFFB78103)`, `UpsellBorderPink = Color(0xFFF48FB1)`. In `SettingsScreen.kt:701-756`, replace the 7 hex values with those tokens; use `SurfaceContainerLow` for `#FFF0F5`. Add the explicit imports next to `:95-103`. Touch no other lines. | `grep -n "Color(0x" SettingsScreen.kt` shows no hit at lines 695-757; `.\gradlew.bat :app:compileDebugKotlin` | none | haiku, effort max |
| T2 | `app/schemas/com.example.data.db.AppDatabase/12.json` (written only if it mismatches) | exec 14 provenance. Read the entities and AppDatabase at `322ec07^` with `git show 322ec07^:app/src/main/java/com/example/data/model/Entities.kt` and `git show 322ec07^:app/src/main/java/com/example/data/db/AppDatabase.kt`. Compare their tables and columns, including NOT NULL and default values, with `12.json`. If they differ, regenerate the file: make a temporary `git worktree add` at `322ec07^`, run `:app:kspDebugKotlin` there, copy the exported `12.json` back, then remove the worktree. Change no test or migration code. | Report "match" or the diff found. Then `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.data.db.Migration12To13Test"` | none | sonnet, effort high |
| T3 | none (verify only) | exec 6, 7, 8, 9, 10, 12 and 14: one filtered run of the existing tests, then the compile check. Fix nothing in this task. A failure becomes a new task, routed to sonnet with a root-cause read. | `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.CloudEnrichmentDataTest" --tests "com.example.data.repository.PresetDedupeTest" --tests "*SyncWorkerTest" --tests "com.example.data.repository.MemoryPrivacyTest" --tests "com.example.data.repository.AuthRepositoryTest" --tests "com.example.ui.theme.ThemeResourcesTest" --tests "com.example.data.db.Migration12To13Test"`, then `.\gradlew.bat :app:compileDebugKotlin`, then grep `upgradeSubscription\|CloudinaryMediaService\|CloudinaryStorageService\|ICloudinaryMediaService` in `app/src` (0 hits) | T1, T2 | haiku, effort max |
| T4 | none (gate) | Run the full verification list below in order and record the output. | see Verification | T3 | haiku, effort max |

Task count: 4. Only T1 is a planned code edit. T2 edits a file only if the schema mismatches.

Callers checked:
- No function signature changes in this plan. T1 adds top-level `val` tokens, and their only users are the new `SettingsScreen.kt` lines.
- T2 changes no code, only `12.json` if it mismatches.

## Waves

- **W1** (parallel, separate files, no Gradle in T1 until T2 finishes its read-only compare):
  - T1 owns `ui/theme/Color.kt` and `ui/screens/SettingsScreen.kt`.
  - T2 owns `app/schemas/com.example.data.db.AppDatabase/12.json`.
  - Run the Gradle commands of T1 and T2 one after the other, never at the same time, because both use the same `app/build` directory and Gradle daemon.
- **W2** (serial): T3, then T4. Neither owns any file.

## Verification commands (T4, in order)

```
.\gradlew.bat :app:testDebugUnitTest
npm --prefix scripts run test:rules
npm --prefix firebase/functions test
.\gradlew.bat :app:lintRelease
.\gradlew.bat :app:assembleRelease      # expected BLOCKED: owner inputs (keystore, STORE_PASSWORD, KEY_PASSWORD, ADMOB_*_RELEASE). Report as blocked, not failed.
git status --short
git diff --check
```

Pass bar:
- `testDebugUnitTest`: 0 failures and 0 skipped. Migration12To13Test was the 1 skipped test in the 2026-10-08 run, so the skipped count should drop to 0.
- `lintRelease`: no new errors.
- `git diff --check`: clean on the files T1 and T2 touch.

If an npm step cannot start (no emulator, no Firebase CLI, no test files), report it as an environment block with the exact error.

## Codex review

Model: `gpt-6.1-sol`, read-only (skill `codex-flow`). Codex reviews this plan, and then the T1 and T2 diff against it.

## Risks

- R1: A token swap can change rendering if a token is mistyped. The hex values are kept 1:1 and no screenshot test covers Settings (A16). Checking the T1 diff line by line against the hex list in A14 is the guard.
- R2: If `12.json` does not match the shipped v12 schema, `Migration12To13Test` passes against a fictional schema. A real v12 to v13 upgrade could then still crash. T2 exists for this reason.
- R3: `12.json` is untracked. If the owner does not commit it together with the test, CI or a clean clone fails `Migration12To13Test`. This plan does not run `git add`; flag it to the owner.
- R4: The large uncommitted worktree (about 110 entries, 69 files changed) belongs to several owner commits, including the D1 `.env` deletion. Builders must not stage or revert unrelated files.
- R5: Debug-only test-fixture credentials are hardcoded in `AuthRepository.kt:143-157`, behind `BuildConfig.DEBUG`. They are out of scope here, but they must never leave the debug gate. Do not quote them in reports.
- R6: Registration still collects and stores a security answer that no recovery path reads (`AuthScreen.kt:157`, `:917`, `:954`; `AuthRepository.kt:551-552`, `:572`). This is a data-minimization point for Data Safety (D9). It is not a blocker for D5.
- R7: Assumptions A22-A26 rest on runs nobody has done yet. If T3 fails, the "already done" findings for exec 6-14 must be re-opened, not explained away.

## Out of scope

Owner-only:

| exec | item | decision | reason |
|---|---|---|---|
| 4 | Commit `.gitignore` and `git rm --cached .env` | D1 | Needs owner consent and the SMTP rotation first. Agents may not commit. |
| 5 | Pin appplugin and prove it with a clean clone | D2 | The owner chooses the version (2.3.0 or 2.4.2) and supplies the source. |
| 13 | Guest adoption: ask once vs auto-adopt (`AuthRepository.kt:618`) | D4 | Product decision. |
| 15 | Steps 24-27 (gifts, interests, suggestions, sharing) | D1-D4 | Blocked on those decisions and on a scope decision. |

Skipped polish, each with an "add when" trigger:
- Replace the string literal `"PRIVATE"` with `PRIVACY_PRIVATE` (`MemoriesGridScreen.kt:840`, `:1242-1266`, `:1464-1465`). Add when MemoriesGridScreen is next edited, or when a privacy level is added or renamed.
- Delete the test-mode OTP branch (`AuthRepository.kt:656-666`) and rename `requestPasswordResetOtp` to `requestPasswordReset`. Add when the auth flows are next touched. Both callers are `AuthScreen.kt:1163` and the tests.
- Remove the security question from registration (R6). Add when the registration UI or the account schema changes, or when Data Safety review asks.
- Move the other hard-coded hex values in `SettingsScreen.kt` (about 70 outside the VIP banner, e.g. `:198`, `:290-319`, `:455-640`, `:932-935`) to tokens. Add when dark theme (D8) is un-deferred.
- Page the outbox by keyset instead of reading the whole queue in one snapshot (`SyncWorker.kt:52`, a ponytail ceiling). Add when the outbox regularly holds more than a few thousand rows.
