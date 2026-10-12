#!/usr/bin/env bash
# Test không cần máy của tools/release_local.sh (2.4.0 NOCI, D33): gradlew/gh/git giả trên bản chép tạm, 0 Gradle thật.
# Chạy: bash tools/tests/dg_release_local_test.sh (hoặc qua runner device_gate_allowlist_test.sh). exit 0 = xanh.
. "$(dirname "$0")/lib/dg_harness.sh" || exit 99
S=$ROOT/tools/release_local.sh

mkfix() { # $1 = tên cây; dựng cây giả sdkVersion 9.9.9 + CHANGELOG có mục 9.9.9; stub gradlew/pom_check/git/gh
  local d=$TMP/$1; mkdir -p "$d/tools" "$d/bin" "$d/appplugin-max" "$d/appplugin-game" "$d/appplugin-nextgen"
  cp "$S" "$d/tools/release_local.sh"
  printf 'val sdkVersion = "9.9.9"\n' > "$d/build.gradle.kts"
  printf '# Changelog\n\n## [9.9.9] - 2026-01-01\n\n### Added\n- thu nghiem\n\n## [9.9.8] - 2025-12-31\n- cu\n' > "$d/CHANGELOG.md"
  printf '#!/bin/sh\necho "gradlew $*" >> "$STUB_LOG"\nmkdir -p build/outputs/aar appplugin-max/build/outputs/aar appplugin-game/build/outputs/aar appplugin-nextgen/build/outputs/aar\nfor m in "" appplugin-max appplugin-game appplugin-nextgen; do n=${m:-appplugin}; p=${m:+$m/}; echo "aar $n" > "${p}build/outputs/aar/$n-release.aar"; done\n' > "$d/gradlew"
  printf 'exit 0\n' > "$d/tools/pom_check.sh"
  printf '#!/bin/sh\ncase "$1" in\n tag) echo "$FAKE_TAG";;\n status) echo "$FAKE_DIRTY";;\n ls-remote) [ -n "$FAKE_NOREMOTE" ] && exit 2;;\nesac\nexit 0\n' > "$d/bin/git"
  printf '#!/bin/sh\necho "gh $*" >> "$STUB_LOG"\n[ "$1 $2" = "release view" ] && [ -n "${FAKE_REL:-}" ] && { echo "$FAKE_REL" | tr , "\\n"; exit 0; }\n[ "$1 $2" = "release view" ] && exit 1\nexit 0\n' > "$d/bin/gh"
  chmod +x "$d/gradlew" "$d/bin/git" "$d/bin/gh"
}
rl() { # $1 = cây, còn lại = tham số; ra out, rc; env sạch credential trừ khi test đặt
  local d=$TMP/$1; shift; : > "$TMP/stub.log"
  out=$(cd "$d" && env -u GITHUB_TOKEN -u GITHUB_ACTOR PATH="$d/bin:$PATH" STUB_LOG="$TMP/stub.log" FAKE_TAG="${FAKE_TAG:-}" FAKE_DIRTY="${FAKE_DIRTY:-}" FAKE_NOREMOTE="${FAKE_NOREMOTE:-}" $RL_ENV bash tools/release_local.sh "$@" 2>&1); rc=$?
}

mkfix t1
FAKE_TAG=v9.9.8 RL_ENV= rl t1 --skip-tests
check "RL1 tag HEAD v9.9.8 != sdkVersion 9.9.9 -> FAIL nêu tag, exit != 0, 0 gradlew, 0 gh" \
  "$([ "$rc" -ne 0 ] && has "$out" 'release_local: tag FAIL' && [ ! -s "$TMP/stub.log" ]; echo $?)" "rc=$rc
$out"
FAKE_TAG= RL_ENV= rl t1 --skip-tests
check "RL1b HEAD không có tag -> FAIL nêu tag" "$([ "$rc" -ne 0 ] && has "$out" 'release_local: tag FAIL'; echo $?)" "rc=$rc
$out"

mkfix t2; printf '# Changelog\n\n## [9.9.8] - 2025-12-31\n- cu\n' > "$TMP/t2/CHANGELOG.md"
FAKE_TAG=v9.9.9 RL_ENV= rl t2 --skip-tests
check "RL2 CHANGELOG thiếu mục [9.9.9] -> FAIL nêu notes, exit != 0, 0 gradlew" \
  "$([ "$rc" -ne 0 ] && has "$out" 'release_local: notes FAIL' && [ ! -s "$TMP/stub.log" ]; echo $?)" "rc=$rc
$out"

mkfix t3
FAKE_TAG= RL_ENV= rl t3 --dry-run --skip-tests
names_ok=0; for n in appplugin appplugin-max appplugin-game appplugin-nextgen; do has "$out" "build/release/$n-9.9.9.aar" || names_ok=1; [ -s "$TMP/t3/build/release/$n-9.9.9.aar" ] || names_ok=1; done
check "RL3 --dry-run --skip-tests: exit 0, 4 AAR + SHA256SUMS (4 dòng), in 'gh release create v9.9.9' kèm notes, notes đúng mục, 0 lệnh gh thật, 0 unit/lint" \
  "$([ "$rc" -eq 0 ] && [ "$names_ok" -eq 0 ] && has "$out" 'gh release create v9.9.9' && has "$out" '--notes-file build/release-notes.md' \
     && [ "$(wc -l < "$TMP/t3/build/release/SHA256SUMS" | tr -d ' ')" -eq 4 ] && command grep -q 'thu nghiem' "$TMP/t3/build/release-notes.md" \
     && ! command grep -q 'cu$' "$TMP/t3/build/release-notes.md" && ! command grep -q '^gh ' "$TMP/stub.log" && ! command grep -q testDebugUnitTest "$TMP/stub.log" \
     && command grep -q 'publishAllPublicationsToLocalCheckRepository' "$TMP/stub.log" && has "$out" 'release_local: aar appplugin-9.9.9.aar'; echo $?)" "rc=$rc names_ok=$names_ok
$out"

FAKE_TAG=v9.9.9 RL_ENV= rl t3
check "RL3b có đủ tag, không --skip-tests: chạy unit+lint 2 module, gh release create thật (stub) kèm 4 AAR, exit 0" \
  "$([ "$rc" -eq 0 ] && command grep -q ':appplugin-nextgen:lintDebug' "$TMP/stub.log" && [ "$(command grep -c '^gh release create v9.9.9 .*nextgen-9.9.9.aar' "$TMP/stub.log")" -eq 1 ]; echo $?)" "rc=$rc
$(cat "$TMP/stub.log")"
ALL4="appplugin-9.9.9.aar,appplugin-max-9.9.9.aar,appplugin-game-9.9.9.aar,appplugin-nextgen-9.9.9.aar,SHA256SUMS"
FAKE_TAG=v9.9.9 RL_ENV="FAKE_REL=$ALL4" rl t3 --skip-tests
check "RL6 release đã có đủ 4 AAR -> in dòng bỏ qua, exit 0, không gh release create/upload" \
  "$([ "$rc" -eq 0 ] && has "$out" 'release_local: gh release v9.9.9 đã có đủ 4 AAR, bỏ qua tạo' && ! command grep -q 'gh release create\|upload' "$TMP/stub.log"; echo $?)" "rc=$rc
$out"
FAKE_TAG=v9.9.9 RL_ENV="FAKE_REL=appplugin-9.9.9.aar,appplugin-max-9.9.9.aar" rl t3 --skip-tests
check "RL7 release có nhưng thiếu asset -> FAIL 'Release v9.9.9 thiếu asset:' kèm tên thiếu, không create/upload (không --clobber)" \
  "$([ "$rc" -ne 0 ] && has "$out" 'Release v9.9.9 thiếu asset: appplugin-game-9.9.9.aar appplugin-nextgen-9.9.9.aar' && ! command grep -q 'gh release create\|upload' "$TMP/stub.log"; echo $?)" "rc=$rc
$out"
FAKE_TAG= RL_ENV= rl t3 --dry-run --skip-tests
check "RL8 --dry-run không --publish không cần GITHUB_*: exit 0, 5 asset (có SHA256SUMS) trong lệnh in" \
  "$([ "$rc" -eq 0 ] && has "$out" 'build/release/SHA256SUMS'; echo $?)" "rc=$rc"

FAKE_TAG=v9.9.9 FAKE_NOREMOTE=1 RL_ENV= rl t3 --skip-tests
check "RL9 tag chưa có trên origin -> FAIL 'tag v9.9.9 chưa push lên origin', 0 gh release create; --dry-run vẫn exit 0 và in lệnh kiểm" \
  "$([ "$rc" -ne 0 ] && has "$out" 'tag v9.9.9 chưa push lên origin' && ! command grep -q 'gh release create' "$TMP/stub.log"; echo $?)" "rc=$rc
$out"
FAKE_TAG= FAKE_NOREMOTE=1 RL_ENV= rl t3 --dry-run --skip-tests
check "RL9b --dry-run, tag vắng trên origin -> exit 0, in 'git ls-remote --exit-code --tags origin', lệnh gh có --verify-tag" \
  "$([ "$rc" -eq 0 ] && has "$out" 'git ls-remote --exit-code --tags origin' && has "$out" '--verify-tag'; echo $?)" "rc=$rc
$out"
FAKE_TAG=v9.9.9 FAKE_DIRTY=" M x" RL_ENV= rl t3 --skip-tests
check "RL10 cây bẩn -> FAIL 'cây làm việc bẩn', 0 gradlew; --dry-run vẫn chạy được" \
  "$([ "$rc" -ne 0 ] && has "$out" 'cây làm việc bẩn' && [ ! -s "$TMP/stub.log" ]; echo $?)" "rc=$rc
$out"
FAKE_TAG= FAKE_DIRTY=" M x" RL_ENV= rl t3 --dry-run --skip-tests
check "RL10b --dry-run với cây bẩn -> exit 0" "$([ "$rc" -eq 0 ]; echo $?)" "rc=$rc
$out"

FAKE_TAG=v9.9.9 RL_ENV= rl t3 --publish --skip-tests
check "RL4 --publish thiếu GITHUB_TOKEN -> FAIL nêu GITHUB_TOKEN, exit != 0, 0 gradlew, không in token" \
  "$([ "$rc" -ne 0 ] && has "$out" 'release_local: credentials FAIL' && has "$out" 'GITHUB_TOKEN' && [ ! -s "$TMP/stub.log" ]; echo $?)" "rc=$rc
$out"
FAKE_TAG=v9.9.9 RL_ENV="GITHUB_ACTOR=dummy-actor GITHUB_TOKEN=dummy-token-value" rl t3 --publish --skip-tests
check "RL4b --publish có credential giả: 4 task publish...GitHubPackages chạy, 'dummy-token-value' không xuất hiện trong output, log stub, cây tạm" \
  "$([ "$rc" -eq 0 ] && [ "$(command grep -o 'publishReleasePublicationToGitHubPackagesRepository' "$TMP/stub.log" | wc -l | tr -d ' ')" -eq 4 ] \
     && ! has "$out" 'dummy-token-value' && ! command grep -rq 'dummy-token-value' "$TMP/stub.log" "$TMP/t3"; echo $?)" "rc=$rc
$out"

check "RL5 release_local.sh: bash -n sạch, <= 160 dòng, không sed -i, set -euo pipefail" \
  "$(bash -n "$S" && [ "$(wc -l < "$S" | tr -d ' ')" -le 160 ] && ! command grep -q 'sed -i' "$S" && command grep -q '^set -euo pipefail' "$S"; echo $?)" "lines=$(wc -l < "$S")"

dg_finish
