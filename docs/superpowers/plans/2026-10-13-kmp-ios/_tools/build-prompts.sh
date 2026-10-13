#!/usr/bin/env bash
# Builds one Codex plan-review prompt per plan part (+ master).
# usage: bash build-prompts.sh <round 1|2> [key ...]     (no keys = all)
# output: docs/superpowers/reviews/2026-10-13-kmp-ios/prompts/r<round>-<key>.prompt.md
set -euo pipefail
shopt -u patsub_replacement 2>/dev/null || true
cd "$(dirname "${BASH_SOURCE[0]}")/../../../../.."   # repo root
ROUND=${1:?round 1 or 2}; shift || true
T=docs/superpowers/plans/2026-10-13-kmp-ios
REV=docs/superpowers/reviews/2026-10-13-kmp-ios
OUT=$REV/prompts
mkdir -p "$OUT"
tpl=$(cat "$T/_tools/review-template.md")
rules=$(cat "$T/_tools/rules.md")

declare -A FOCUS
FOCUS[p1-foundation-build]="Gradle/KMP/AGP 9 module split feasibility, version compatibility claims (Kotlin, Compose Multiplatform, AGP, Gradle, Xcode), Xcode embedding (User Script Sandboxing, -ObjC, Konan cache), includeBuild(appplugin) still working, migration order that keeps Android green, Windows<->Mac workflow, the P0 spike with a PASS/FAIL gate that includes Vietnamese Telex/VNI input on a real device."
FOCUS[p2-data-domain]="Room KMP migration without data loss for existing Android users (identity hash), java.time/Context/SharedPreferences replacements, DST/leap-day/timezone correctness, sync (WorkManager) on iOS, WidgetSnapshot JSON facade instead of opening SQLite in the App Group (0xdead10cc), DI choice, test relocation, golden vectors from Android."
FOCUS[p3-backend-auth-media]="Firebase-on-iOS strategy choice and evidence, Sign in with Apple (guideline 4.8, name/email only first time, token revoke) and in-app account deletion (5.1.1(v)), Keychain SecureStore and first-launch cleanup, NFC normalization before hashing, PIN/lockout compatibility, media pipeline (PHPicker, upload, video), App Check on iOS and Play Integrity counterpart, firestore.rules/Functions changes that stop the client writing entitlement fields, gateway interfaces."
FOCUS[p4-ui-compose]="Compose Multiplatform UI port completeness and order, giant-file splitting before moving, Android-only API replacement table (verify counts with grep), animation parity and 60/120Hz smoothness, navigation and iOS back-swipe vs pager, Telex/VNI IME in TextField, keyboard/insets/safe area, resources/localization (vi/en), material-icons availability, accessibility (VoiceOver, Reduce Motion), screenshot strategy, VIP gates in UI mirror server rules."
FOCUS[p5-native-features]="Local notification parity within the 64-pending limit (rolling schedule, DST/timezone), WidgetKit catalog and data flow (App Group JSON snapshot, timelines and reload budget, lock-screen/StandBy/interactive, widget memory), permissions flow, deep links/universal links, the Info.plist/entitlements/PrivacyInfo table, Swift code layout in iosApp, sensitive content on lock screen."
FOCUS[p6-monetization-ads-billing]="SUBSCRIPTION INTEGRITY above all: server-verified entitlements (App Store Server API + Notifications V2, Google Play Developer API/RTDN), appAccountToken-uid binding, originalTransactionId uniqueness, refund/revoke/grace/retry/family sharing/restore, clock tamper, unverified transactions, Transaction.updates, rules tests. Also gap analysis of appplugin vs monetization-brain-ios (actual ad DISPLAY on iOS), gateway interfaces, UMP+ATT order, SKAdNetwork, AdMob banner via UIKitView, privacy labels input, analytics event parity, kill switch offline."
FOCUS[p7-quality-release]="Test relocation inventory and safety-net tests, cross-platform test strategy, performance budgets/measurement for animation smoothness, CI/CD on macOS runners, TestFlight/App Store submission checklist accuracy (2.1 demo account, 4.8, 5.1.1(v), 3.1.2, 1.2, privacy manifest, export compliance, build numbers, dSYM), costs, risk registry."
FOCUS[p8-feature-parity-matrix]="Completeness of the FP-xxx feature inventory against the Android source (spot-check at least 15 user-visible features from screens, dialogs and ViewModel actions and look for ones missing), correctness of the VIP gate map (verify in code where each gate is enforced: client only vs server), realism of the manual QA scripts including bypass attempts (clock rewind, long offline, another account with the same Apple ID), golden-vector coverage for pure functions, pitfall list accuracy."
FOCUS[00-master]="CROSS-CUTTING: phase/wave ordering and critical path across all parts, unresolved or inconsistent decisions between parts (DECISION-CONFLICT reconciliation), duplicated or conflicting file ownership across parts, the shared-file contention plan, platform-interface ledger consistency, estimate/timeline realism (sum of task sizes vs stated weeks), gate/decision points, open questions that are really owner-only actions, whether every FP-xxx feature has an owning task, and anything the parts jointly miss."

KEYS=("$@"); [ ${#KEYS[@]} -gt 0 ] || KEYS=("${!FOCUS[@]}")

for key in "${KEYS[@]}"; do
  if [ "$ROUND" = 1 ]; then
    prior="This is round 1: there are no earlier findings for this part."
  else
    prior="This is round 2. Round-1 raw findings: $REV/r1-$key.md. Claude's dispositions (accept/reject + reason, one row per finding, the finding text starts with [${key%%-*}]): docs/superpowers/reviews/2026-10-13-kmp-ios-log.md. Do the following in addition to the checks: (a) for each accepted BLOCKER/MAJOR confirm the plan really changed, otherwise report it again; (b) challenge a rejected finding only with NEW evidence; (c) hunt for problems introduced by the round-1 edits and for what round 1 missed."
  fi
  p="$tpl"
  p="${p//\{\{ROUND\}\}/$ROUND}"
  p="${p//\{\{PART_PATH\}\}/$T/$key.md}"
  p="${p//\{\{FOCUS\}\}/${FOCUS[$key]}}"
  p="${p//\{\{PRIOR\}\}/$prior}"
  p="${p//\{\{RULES\}\}/$rules}"
  printf '%s\n' "$p" > "$OUT/r$ROUND-$key.prompt.md"
done
ls -la "$OUT" | tail -n +2
