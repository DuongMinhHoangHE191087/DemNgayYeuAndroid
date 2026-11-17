#!/usr/bin/env bash
# Tests for scripts/kmp/spike-verdict.sh using synthetic fixture directories.
set -u
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
V="$HERE/../spike-verdict.sh"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
FAILS=0
ok() { echo "PASS: $1"; }
bad() { echo "FAIL: $1"; FAILS=$((FAILS + 1)); }

# mk <dir> <line>...: spread the RESULT lines over two p0-*.md files; p0-decision.md and
# non-anchored lines must be ignored.
mk() {
  local d="$TMP/$1"; shift
  mkdir -p "$d"
  { echo "# notes"; echo "RESULT: S1 FAIL"; } > "$d/p0-decision.md"
  printf 'text RESULT: S1 FAIL is not at a line start\n  RESULT: S2 FAIL\n' > "$d/p0-notes.md"
  local i=0 l
  for l in "$@"; do i=$((i + 1)); echo "$l" >> "$d/p0-spike$((i % 2)).md"; done
}
allpass() { for n in 1 2 3 4 5 6 7 8; do echo "RESULT: S$n PASS"; done; }

# check <name> <dir> <expected rc> <expected VERDICT> [extra exact output line]
check() {
  local out rc
  out="$(bash "$V" "$TMP/$2" 2>&1)"; rc=$?
  if [ $rc -eq "$3" ] && echo "$out" | grep -qx "VERDICT: $4" && { [ -z "${5:-}" ] || echo "$out" | grep -qx "$5"; }; then
    ok "$1"
  else
    bad "$1 (rc=$rc): $out"
  fi
}

mapfile -t P < <(allpass)

mk go "${P[@]}"
check "all PASS -> GO" go 0 GO

mk warn "${P[@]/S7 PASS/S7 WARN}"
check "S7 WARN -> GO plus WARN: S7" warn 0 GO "WARN: S7"

mk worst "${P[@]}" "RESULT: S3 WARN" "RESULT: S3 FAIL"
check "worst result per spike wins (S3 FAIL) -> STOP" worst 1 STOP

mk cond "${P[@]/S8 PASS/S8 FAIL}" "RESULT: S8-FALLBACK PASS"
check "S8 FAIL + S8-FALLBACK PASS -> CONDITIONAL-GO" cond 0 CONDITIONAL-GO

mk nofb "${P[@]/S8 PASS/S8 FAIL}"
check "S8 FAIL without fallback -> STOP" nofb 1 STOP

mk s4 "${P[@]/S4 PASS/S4 FAIL}" "RESULT: S8-FALLBACK PASS"
check "S4 FAIL -> STOP" s4 1 STOP

mk s6 "${P[@]/S6 PASS/}"
check "missing S6 line -> STOP" s6 1 STOP

mk both "${P[@]/S8 PASS/S8 FAIL}" "RESULT: S8-FALLBACK PASS" "RESULT: S4 FAIL"
check "S8 FAIL + fallback but S4 FAIL -> STOP" both 1 STOP

[ $FAILS -eq 0 ] && echo "ALL OK" || echo "$FAILS FAILED"
exit $FAILS
