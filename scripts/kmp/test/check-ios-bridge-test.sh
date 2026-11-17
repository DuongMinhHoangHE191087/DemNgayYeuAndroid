#!/usr/bin/env bash
# Tests for scripts/kmp/check-ios-bridge.sh against fixtures/ios-bridge/{good,bad}.
set -u
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CHECK="$HERE/../check-ios-bridge.sh"
FIX="$HERE/fixtures/ios-bridge"
FAILS=0
ok() { echo "PASS: $1"; }
bad() { echo "FAIL: $1"; FAILS=$((FAILS + 1)); }

out="$(bash "$CHECK" "$FIX/good" 2>&1)"; rc=$?
[ $rc -eq 0 ] && ok "good/ exits 0" || bad "good/ rc=$rc: $out"

out="$(bash "$CHECK" "$FIX/does-not-exist" 2>&1)"; rc=$?
{ [ $rc -eq 0 ] && echo "$out" | grep -q 'no bridge dir'; } && ok "missing dir exits 0 and says no bridge dir" || bad "missing dir rc=$rc: $out"

# file -> tag the checker must report for it
for pair in missing-throws-fun:missing-throws missing-throws-ctor:missing-throws \
            missing-throws-empty-reason:missing-throws value-class:value-class kotlin-result:kotlin-result \
            default-param:default-param default-param-multiline:default-param \
            generic-interface:generic-interface run-blocking:run-blocking; do
  name="${pair%%:*}"; tag="${pair##*:}"
  out="$(bash "$CHECK" "$FIX/bad/$name.kt" 2>&1)"; rc=$?
  { [ $rc -eq 1 ] && echo "$out" | grep -q "$name.kt" && echo "$out" | grep -q "\[$tag\]"; } \
    && ok "bad/$name.kt exits 1 with [$tag]" || bad "bad/$name.kt rc=$rc: $out"
done

out="$(bash "$CHECK" "$FIX/bad" 2>&1)"; rc=$?
[ $rc -eq 1 ] && ok "whole bad/ dir exits 1" || bad "bad/ rc=$rc"

# default mode: must not fail on the current tree (bridge dir may not exist yet)
out="$(bash "$CHECK" 2>&1)"; rc=$?
[ $rc -eq 0 ] && ok "default mode on the repo exits 0" || bad "default rc=$rc: $out"

[ $FAILS -eq 0 ] && echo "ALL PASS" || { echo "$FAILS FAILED"; exit 1; }
