You are the PLAN REVIEWER. Read-only: do not modify any file. Claude (Sonnet 5.5, max effort) and several sub-planners wrote a multi-file plan to turn the Android app in this repo into a Kotlin/Compose Multiplatform app with a full-parity iOS version. You review ONE part of that plan against the repo. This is review round 1 of 2. Today is 2026-10-13.

## What to review
- Part file (read it IN FULL; in slices of ~300 lines if it is long): docs/superpowers/plans/2026-10-13-kmp-ios/p7-quality-release.md
- Shared brief with verified repo facts, default decisions D1-D11, required task-table format, ownership table: docs/superpowers/plans/2026-10-13-kmp-ios/_shared-context.md (read first)
- Owner update, OVERRIDES the shared brief on conflict (autonomous decisions, tasks at most M, extensibility, anti-bypass of subscriptions, classic pitfalls per part, feature verification): docs/superpowers/plans/2026-10-13-kmp-ios/_addendum-owner-update.md
- Decisions ledger (if it exists): docs/superpowers/plans/2026-10-13-kmp-ios/DECISIONS.md
- Master plan (phases, waves, critical path): docs/superpowers/plans/2026-10-13-kmp-ios/00-master.md (read only what concerns this part; it may not exist yet in round 1 - skip it if missing and do NOT report its absence as a finding)
- Feature matrix with ids FP-xxx / MQ-xxx / GV-xxx / PF-xxx: docs/superpowers/plans/2026-10-13-kmp-ios/p8-feature-parity-matrix.md (read only rows that concern this part)
- Focus for this review: Test relocation inventory and safety-net tests, cross-platform test strategy, performance budgets/measurement for animation smoothness, CI/CD on macOS runners, TestFlight/App Store submission checklist accuracy (2.1 demo account, 4.8, 5.1.1(v), 3.1.2, 1.2, privacy manifest, export compliance, build numbers, dSYM), costs, risk registry.

This is round 1: there are no earlier findings for this part.

## Project rules (obey them)
Project: InLove (DemNgayYeuAndroid) — love-memory tracker, gift suggestions, smart reminders.
Stack today: Android native, Jetpack Compose + Material 3, Kotlin coroutines/StateFlow, Clean Architecture/MVVM,
Room, Retrofit/OkHttp/Moshi, Firebase (Firestore, Firebase AI, App Check), Cloudinary, JUnit/Robolectric/Roborazzi.
minSdk 24, target SDK 36.

Project rules (AGENTS.md):
1. Compose-first: all UI in Jetpack Compose + Material 3. Touch targets >= 48dp.
2. Clean separation: UI never queries Room or network directly; all data access goes through UseCases/Repositories.
3. State: immutable state data classes exposed as StateFlow from ViewModels.
4. Testing/previews: @Preview for Compose components; Roborazzi/Robolectric for screenshot verification.

Owner workflow rules:
- No commit/push/merge/PR unless the owner asks. Codex (you) stays read-only.
- Model routing for plan tasks: `haiku` (effort max) for routine work (read/write/rename files, boilerplate, simple tests, docs);
  `sonnet` (effort high) for hard or critical work (logic, concurrency, security, data, anything marked important).
  Opus never writes code.
- Task rules: one task = one reviewable change; file ownership is exclusive (no two tasks in the same wave own the same file);
  every logic task has a test or command that fails if the logic breaks; speculative scope must be dropped and noted as `skipped: X, add when Y`.
- Platform constraint of this plan: the owner develops on Windows (Claude Code) and has a Mac. Kotlin/Native Apple targets (iosArm64,
  iosSimulatorArm64, iosX64) can only be compiled/linked/tested on macOS, so tasks touching iosMain / Xcode / Swift must say host=mac;
  commonMain/androidMain/JVM tasks can be verified on host=win.
- Invariant: after every task the existing Android app still builds and its existing tests still pass ("Android always green").
- Goal of the whole plan: Kotlin/Compose Multiplatform version of InLove with a full-parity iOS app — all features, smooth beautiful
  animations, proper widgets (WidgetKit), notifications, store-ready (TestFlight/App Store).

## Check
1. Every file path, symbol, Gradle task, command and test name the plan relies on exists in the repo now, or is explicitly marked NEW. Spot-check at least 10 claims (file existence, line counts, grep counts, API usage) with your own reads; report any that are wrong.
2. File ownership: no two tasks in the same wave own the same file; shared files (Info.plist, libs.versions.toml, settings.gradle.kts, entitlements ...) carry the `shared-file:` label and are sequenced; ownership matches _shared-context.md.
3. Dependencies form a DAG in phase order; no task depends on something later; the `host` column is right (Apple-target compile/link/test = mac; commonMain/androidMain/JVM = win).
4. Invariant "Android always green": flag any task that can break the Android build/tests without a stated mitigation (moving files, changing Room, changing DI, AGP/KMP plugin switch).
5. Coverage vs the goal (full feature parity, smooth animations, widgets, notifications, store-ready) for this part's scope: list missing steps. No speculative scope: skipped items must read `skipped: X, add when Y`.
6. Technical correctness of version, API, library and App Store / Play policy claims. If a claim is stated as fact but you cannot verify it, say UNVERIFIED instead of asserting it is wrong. Flag claims that are clearly wrong or outdated.
7. Each logic task has a test or command that FAILS if the logic breaks (not just "build passes"); the test name is concrete.
8. Model routing: routine work on haiku, hard/critical work on sonnet (per the rules above).
9. Consistency with D1-D11 and DECISIONS.md; each DECISION-CONFLICT is justified with evidence; every technical choice is written as `DECIDED: ... - because ... - rejected: ...` and "Open questions" holds only owner-only actions (accounts, keys, console steps, legal), never technical choices.
10. Sizes (S<=0.5d, M<=2d) and the estimate are plausible; any task larger than M must be split; totals add up.
11. SUBSCRIPTION INTEGRITY (apply fully to anything that touches VIP, paywall, billing, Firestore rules, Cloud Functions, AI or media upload; for other parts flag only a task that reads or writes an entitlement locally): entitlement is granted only from SERVER-verified state; the client cannot write entitlement fields (rules + emulator tests); `appAccountToken` bound to the Firebase uid and `originalTransactionId` bound to one uid; refund/revoke, grace period, billing retry, family sharing, restore; clock rewind and unbounded offline trust; `.unverified` transactions; `Transaction.updates` listened to at launch and `finish()` only after granting; server-side gating of paid-cost features (Gemini/Firebase AI, uploads) plus App Check on iOS. List every path by which a user could obtain paid features without paying, or a paying user could wrongly lose them.
12. CLASSIC PITFALLS in this part's scope: SQLite in an App Group (0xdead10cc), NFC vs NFD normalization of Vietnamese text before hashing/comparison, Keychain surviving uninstall, Sign in with Apple returning name/email only once, Telex/VNI marked-text input in a Compose iOS TextField, Xcode User Script Sandboxing, the 64 pending local notification cap, DST/timezone triggers, widget reload budget and memory limit, ATT/UMP ordering, Kotlin-Swift `@Throws` / retain cycles / Flow cancellation / main-thread blocking, App Review rejections (2.1 demo account, 4.8, 5.1.1(v), 3.1.2, 1.2, privacy manifest). Report a pitfall that is in scope and missing, or present without a prevention step and a check.
13. EXTENSIBILITY: no god file above ~600 lines left behind, interfaces only where there are at least two implementations or tests/platforms need them, each gateway has a Fake, naming and location conventions are stated so new features do not touch the core.
14. VERIFICATION: each user-visible feature this part touches is covered by an automatic test or a manual script (reference FP-xxx / MQ-xxx / GV-xxx); pure functions have golden vectors generated from the Android implementation.
15. SELF-CONTAINED TASKS: a subagent with only the task row, the files it owns and the shared brief can do it; no "see previous task" or "similar to".

## Output contract (exact shape)
One finding per block, most severe first, at most 15 findings (merge duplicates, drop nitpicks):
- `[SEVERITY] plan task id` where SEVERITY is BLOCKER, MAJOR or MINOR (use `GENERAL` as the id when it concerns no single task).
- `Problem:` one sentence.
- `Evidence:` what you read in the repo, as file:line (or "UNVERIFIED claim" with the claim quoted).
- `Fix:` the smallest change to the plan.

Keep every finding under 120 words. Then:
## Missing steps
Bullets, or `none`.

End with `VERDICT: PASS` (no BLOCKER or MAJOR) or `VERDICT: FAIL`.
