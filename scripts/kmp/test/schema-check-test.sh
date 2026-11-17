#!/usr/bin/env bash
# Tests for scripts/kmp/schema-check.sh. Fixtures come from `git show main:` (never the working tree).
set -u
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CHECK="$HERE/../schema-check.sh"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
FAILS=0
ok() { echo "PASS: $1"; }
bad() { echo "FAIL: $1"; FAILS=$((FAILS + 1)); }

fresh() { # fresh <dir>: copy the three schemas from main into <dir>
  mkdir -p "$1"
  for n in 12 13 14; do
    git show "main:app/schemas/com.example.data.db.AppDatabase/$n.json" > "$1/$n.json" || { echo "cannot read main:$n.json"; exit 2; }
  done
}

fresh "$TMP/good"
out="$(SCHEMA_DIR="$TMP/good" bash "$CHECK" 2>&1)"; rc=$?
[ $rc -eq 0 ] && ok "pristine fixture exits 0" || bad "pristine fixture rc=$rc: $out"

fresh "$TMP/mod"; printf ' ' >> "$TMP/mod/14.json"
out="$(SCHEMA_DIR="$TMP/mod" bash "$CHECK" 2>&1)"; rc=$?
{ [ $rc -eq 1 ] && echo "$out" | grep -q '14.json'; } && ok "modified 14.json exits 1 and names it" || bad "modified rc=$rc: $out"

fresh "$TMP/miss"; rm "$TMP/miss/13.json"
out="$(SCHEMA_DIR="$TMP/miss" bash "$CHECK" 2>&1)"; rc=$?
{ [ $rc -eq 1 ] && echo "$out" | grep -q '13.json'; } && ok "missing 13.json exits 1 and names it" || bad "missing rc=$rc: $out"

fresh "$TMP/crlf"
for n in 12 13 14; do sed 's/$/\r/' "$TMP/crlf/$n.json" > "$TMP/crlf/$n.tmp" && mv "$TMP/crlf/$n.tmp" "$TMP/crlf/$n.json"; done
out="$(SCHEMA_DIR="$TMP/crlf" bash "$CHECK" 2>&1)"; rc=$?
[ $rc -eq 0 ] && ok "CRLF copy exits 0" || bad "CRLF rc=$rc: $out"

[ $FAILS -eq 0 ] && echo "ALL PASS" || { echo "$FAILS FAILED"; exit 1; }