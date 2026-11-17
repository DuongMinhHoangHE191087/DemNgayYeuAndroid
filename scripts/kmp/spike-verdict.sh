#!/usr/bin/env bash
# Usage: spike-verdict.sh <dir>
# Reads only line-anchored `RESULT: S<n> PASS|WARN|FAIL` lines (and `RESULT: S8-FALLBACK ...`) from
# <dir>/p0-*.md, skipping p0-decision.md. Worst result per spike wins; a missing spike counts as FAIL.
# Rule (p1 section 4.16): GO if S1..S8 are all PASS/WARN; CONDITIONAL-GO if only S8 fails and
# S8-FALLBACK is PASS; otherwise STOP. Prints `VERDICT: <x>`; exit 0 only for GO or CONDITIONAL-GO.
set -u
DIR="${1:-}"
if [ -z "$DIR" ] || [ ! -d "$DIR" ]; then echo "usage: spike-verdict.sh <dir>" >&2; exit 2; fi

declare -A R   # spike id -> worst result rank: 0 PASS, 1 WARN, 2 FAIL (unset = missing)
for f in "$DIR"/p0-*.md; do
  [ -f "$f" ] || continue
  [ "$(basename "$f")" = "p0-decision.md" ] && continue
  while read -r id res; do
    case "$res" in PASS) r=0 ;; WARN) r=1 ;; *) r=2 ;; esac
    [ "${R[$id]:-$r}" -gt "$r" ] && r="${R[$id]}"
    R[$id]=$r
  done < <(tr -d '\r' < "$f" | sed -nE 's/^RESULT: (S[1-8](-FALLBACK)?) (PASS|WARN|FAIL)([[:space:]].*)?$/\1 \3/p')
done

bad=()   # spikes S1..S8 that are FAIL or missing
for n in 1 2 3 4 5 6 7 8; do
  r="${R[S$n]:-2}"
  [ "$r" -eq 2 ] && bad+=("S$n")
  [ "$r" -eq 1 ] && echo "WARN: S$n"
done

if [ ${#bad[@]} -eq 0 ]; then v=GO
elif [ "${bad[*]}" = "S8" ] && [ "${R[S8-FALLBACK]:-2}" -eq 0 ]; then v=CONDITIONAL-GO
else v=STOP; [ ${#bad[@]} -gt 0 ] && echo "FAIL: ${bad[*]}"
fi
echo "VERDICT: $v"
[ "$v" != STOP ]
