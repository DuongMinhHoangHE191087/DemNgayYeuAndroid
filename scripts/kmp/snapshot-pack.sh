#!/usr/bin/env bash
# Windows<->Mac code transfer without a commit (DEC-08). Never writes to git, never uses HEAD.
# Usage: snapshot-pack.sh <out.tar.gz>
#        snapshot-pack.sh --since <old MANIFEST.sha256> <out.tar.gz>   (only new/changed files + DELETED.txt)
# Files = tracked + untracked, minus ignored, minus *.bundle, minus files deleted from the working tree.
# MANIFEST.sha256 lines are "<sha256>  <path>", hashed after stripping \r. KMP_ROOT overrides the repo root (tests).
set -eu
export LC_ALL=C
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="${KMP_ROOT:-$(cd "$HERE/../.." && pwd)}"
SINCE=""
if [ "${1:-}" = "--since" ]; then SINCE="${2:-}"; shift 2 || true; fi
OUT="${1:-}"
[ -n "$OUT" ] || { echo "usage: snapshot-pack.sh [--since <MANIFEST.sha256>] <out.tar.gz>" >&2; exit 2; }
[ -z "$SINCE" ] || [ -f "$SINCE" ] || { echo "FAIL: old manifest not found: $SINCE" >&2; exit 2; }
case "$OUT" in /*|[A-Za-z]:*) ;; *) OUT="$PWD/$OUT" ;; esac
[ -z "$SINCE" ] || SINCE="$(cd "$(dirname "$SINCE")" && pwd)/$(basename "$SINCE")"

W="$(mktemp -d)"
trap 'rm -rf "$W"' EXIT
cd "$ROOT"

# existing files only; the pack's own output and the two meta files are never part of the snapshot
git -c core.quotepath=false ls-files -co --exclude-standard | sort -u | while IFS= read -r f; do
  case "$f" in *.bundle|MANIFEST.sha256|DELETED.txt) continue ;; esac
  [ -f "$f" ] && [ "$ROOT/$f" != "$OUT" ] && printf '%s\n' "$f"
done > "$W/files" || true

# "<sha256 of content without \r>  <path>" per file. Batch sha256sum for all, redo only files that contain \r.
# ponytail: paths with a backslash or newline are not supported (sha256sum escapes them)
hash_list() {
  tr '\n' '\0' < "$1" | xargs -0 -r sha256sum -t > "$W/raw"
  tr '\n' '\0' < "$1" | xargs -0 -r grep -lZUaF $'\r' > "$W/cr" || true
  xargs -0 -r -n1 -P8 sh -c 'printf "%s  %s\n" "$(tr -d "\r" < "$1" | sha256sum | cut -c1-64)" "$1"' _ < "$W/cr" > "$W/crhash"
  awk 'FILENAME == ARGV[1] { m[substr($0, 67)] = $0; next } { p = substr($0, 67); print (p in m) ? m[p] : $0 }' "$W/crhash" "$W/raw"
}
hash_list "$W/files" > "$W/MANIFEST.sha256"

if [ -n "$SINCE" ]; then
  tr -d '\r' < "$SINCE" | sort > "$W/old"
  sort "$W/MANIFEST.sha256" > "$W/new"
  comm -13 "$W/old" "$W/new" | cut -c67- > "$W/files"
  cut -c67- "$W/old" | sort > "$W/old.paths"
  cut -c67- "$W/new" | sort > "$W/new.paths"
  comm -23 "$W/old.paths" "$W/new.paths" > "$W/DELETED.txt"
fi

: > "$W/out.tar"
if [ -s "$W/files" ]; then tar --verbatim-files-from -cf "$W/out.tar" -C "$ROOT" -T "$W/files"; fi
meta=(MANIFEST.sha256)
[ -z "$SINCE" ] || meta+=(DELETED.txt)
if [ -s "$W/files" ]; then tar -rf "$W/out.tar" -C "$W" "${meta[@]}"; else tar -cf "$W/out.tar" -C "$W" "${meta[@]}"; fi
gzip -c "$W/out.tar" > "$OUT"
echo "packed $(wc -l < "$W/files" | tr -d ' ') file(s) -> $OUT"
