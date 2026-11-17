#!/usr/bin/env bash
# Tests for scripts/kmp/android-green.sh (dry-run only: never starts Gradle).
set -u
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
GATE="$HERE/../android-green.sh"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
FAILS=0
ok() { echo "PASS: $1"; }
bad() { echo "FAIL: $1"; FAILS=$((FAILS + 1)); }

mkdir -p "$TMP/good" "$TMP/mod"
for n in 12 13 14; do
  git show "main:app/schemas/com.example.data.db.AppDatabase/$n.json" > "$TMP/good/$n.json" || exit 2
  cp "$TMP/good/$n.json" "$TMP/mod/$n.json"
done
printf ' ' >> "$TMP/mod/14.json"

out="$(GATE_DRY_RUN=1 SCHEMA_DIR="$TMP/good" bash "$GATE" 2>&1)"; rc=$?
{ [ $rc -eq 0 ] && echo "$out" | grep -q 'testDebugUnitTest'; } && ok "dry-run prints testDebugUnitTest" || bad "dry-run rc=$rc: $out"

out="$(GATE_DRY_RUN=1 SCHEMA_DIR="$TMP/good" bash "$GATE" fast 2>&1)"; rc=$?
{ [ $rc -eq 0 ] && echo "$out" | grep -q 'assembleDebug' && ! echo "$out" | grep -q 'testDebugUnitTest'; } && ok "fast omits testDebugUnitTest" || bad "fast rc=$rc: $out"

out="$(GATE_DRY_RUN=1 SCHEMA_DIR="$TMP/mod" bash "$GATE" 2>&1)"; rc=$?
{ [ $rc -eq 1 ] && echo "$out" | grep -q '14.json'; } && ok "tampered schema fails the gate even in dry-run" || bad "negative rc=$rc: $out"

[ $FAILS -eq 0 ] && echo "ALL PASS" || { echo "$FAILS FAILED"; exit 1; }