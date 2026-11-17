#!/usr/bin/env bash
# Tests for scripts/kmp/ios-release-guard.sh (env-var driven; touches nothing).
set -u
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
GUARD="$HERE/../ios-release-guard.sh"
FAILS=0
ok() { echo "PASS: $1"; }
bad() { echo "FAIL: $1"; FAILS=$((FAILS + 1)); }

GOOD_APP='ca-app-pub-1234567890123456~1234567890'
GOOD_BANNER='ca-app-pub-1234567890123456/1234567890'
TEST_APP='ca-app-pub-3940256099942544~1458002511'
TEST_BANNER='ca-app-pub-3940256099942544/2435281174'
GOOD_URL='https://example.com'

# run <CONFIGURATION> <APP> <BANNER> <URL> [INTERSTITIAL] [AOA]; prints "<rc>|<output>"
run() {
  local out rc
  out="$(env -i PATH="$PATH" CONFIGURATION="$1" ADMOB_APP_ID="$2" ADMOB_BANNER_ID="$3" LEGAL_BASE_URL="$4" \
    ADMOB_INTERSTITIAL_ID="${5-}" ADMOB_AOA_ID="${6-}" bash "$GUARD" 2>&1)"; rc=$?
  echo "$rc|$out"
}
# expect <name> <want-rc> <want-var-or-empty> <result>
expect() {
  local rc="${4%%|*}" out="${4#*|}"
  if [ "$rc" -ne "$2" ]; then bad "$1 (rc=$rc want $2): $out"; return; fi
  if [ -n "$3" ] && ! echo "$out" | grep -q "ios-release-guard: $3"; then bad "$1 (missing 'ios-release-guard: $3'): $out"; return; fi
  ok "$1"
}

expect "Release + test ids exits 1" 1 ADMOB_APP_ID "$(run Release "$TEST_APP" "$TEST_BANNER" "$GOOD_URL")"
expect "Release + test banner only exits 1" 1 ADMOB_BANNER_ID "$(run Release "$GOOD_APP" "$TEST_BANNER" "$GOOD_URL")"
expect "Release + empty ids exits 1" 1 ADMOB_APP_ID "$(run Release "" "" "$GOOD_URL")"
expect "Release + empty banner exits 1" 1 ADMOB_BANNER_ID "$(run Release "$GOOD_APP" "" "$GOOD_URL")"
expect "Release + malformed app id exits 1" 1 ADMOB_APP_ID "$(run Release 'ca-app-pub-123~456' "$GOOD_BANNER" "$GOOD_URL")"
expect "Release + banner shaped like app id exits 1" 1 ADMOB_BANNER_ID "$(run Release "$GOOD_APP" "$GOOD_APP" "$GOOD_URL")"
expect "Release + http LEGAL_BASE_URL exits 1" 1 LEGAL_BASE_URL "$(run Release "$GOOD_APP" "$GOOD_BANNER" 'http://x')"
expect "Release + empty LEGAL_BASE_URL exits 1" 1 LEGAL_BASE_URL "$(run Release "$GOOD_APP" "$GOOD_BANNER" '')"
expect "Release + good ids exits 0" 0 "" "$(run Release "$GOOD_APP" "$GOOD_BANNER" "$GOOD_URL")"
expect "Debug + test ids exits 0" 0 "" "$(run Debug "$TEST_APP" "$TEST_BANNER" 'http://x')"
expect "Release + empty interstitial/AOA ignored (DEC-255)" 0 "" "$(run Release "$GOOD_APP" "$GOOD_BANNER" "$GOOD_URL" '' '')"
expect "Release + test interstitial/AOA ignored (DEC-255)" 0 "" "$(run Release "$GOOD_APP" "$GOOD_BANNER" "$GOOD_URL" "$TEST_BANNER" "$TEST_BANNER")"

[ $FAILS -eq 0 ] && echo "ALL PASS" || { echo "$FAILS FAILED"; exit 1; }
