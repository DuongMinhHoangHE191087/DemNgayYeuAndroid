#!/usr/bin/env bash
# Runs one Codex plan review (read-only, xhigh) and keeps only the final reply.
# usage: bash run-codex.sh <round 1|2> <key>      (start with run_in_background; at most ~4 at a time)
# prompt in : docs/superpowers/reviews/2026-10-13-kmp-ios/prompts/r<round>-<key>.prompt.md   (build-prompts.sh)
# raw out   : .../r<round>-<key>.raw.md      reply only: .../r<round>-<key>.md
set -uo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/../../../../.."   # repo root
R=${1:?round}; K=${2:?key}
D=docs/superpowers/reviews/2026-10-13-kmp-ios
bash ~/.claude/skills/codex-flow/scripts/codex.sh "$D/prompts/r$R-$K.prompt.md" --fresh --model gpt-6.1-sol --effort xhigh > "$D/r$R-$K.raw.md" 2>&1
code=$?
# marker is "Turn completed." or "Turn completion inferred ..." depending on how the turn ended
awk 'f{print} /\[codex\] Turn complet/{f=1}' "$D/r$R-$K.raw.md" > "$D/r$R-$K.md"
[ -s "$D/r$R-$K.md" ] || { echo "(no completion marker found; raw tail follows)"; tail -n 200 "$D/r$R-$K.raw.md"; } > "$D/r$R-$K.md"
echo "exit=$code reply_lines=$(wc -l < "$D/r$R-$K.md") verdict=$(grep -o 'VERDICT: [A-Z]*' "$D/r$R-$K.md" | tail -1)"
exit $code
