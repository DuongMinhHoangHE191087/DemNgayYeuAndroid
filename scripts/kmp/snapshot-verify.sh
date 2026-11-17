#!/usr/bin/env bash
# Verifies a snapshot made by snapshot-pack.sh. Exit 0 = tree matches MANIFEST.sha256 (and toolchain is OK), 1 = problem.
# Usage: snapshot-verify.sh <tar.gz | dir>
#        snapshot-verify.sh --apply <--since tar.gz | dir>   apply onto the current tree (KMP_ROOT or repo root),
#                                                             delete files listed in DELETED.txt, then verify
# Missing, extra and changed files are printed by name. SNAPSHOT_SKIP_TOOLCHAIN=1 skips JDK/SDK/gradlew (tests on Windows).
set -u
export LC_ALL=C
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APPLY=0
if [ "${1:-}" = "--apply" ]; then APPLY=1; shift; fi
SRC="${1:-}"
[ -n "$SRC" ] && [ -e "$SRC" ] || { echo "usage: snapshot-verify.sh [--apply] <tar.gz|dir>" >&2; exit 2; }
W="$(mktemp -d)"
trap 'rm -rf "$W"' EXIT

if [ -d "$SRC" ]; then STAGE="$(cd "$SRC" && pwd)"; else STAGE="$W/stage"; mkdir "$STAGE"; tar -xzf "$SRC" -C "$STAGE" || { echo "FAIL: cannot extract $SRC"; exit 1; }; fi
[ -f "$STAGE/MANIFEST.sha256" ] || { echo "FAIL: MANIFEST.sha256 missing in $SRC"; exit 1; }

if [ "$APPLY" = 1 ]; then
  TREE="${KMP_ROOT:-$(cd "$HERE/../.." && pwd)}"
  if [ "$STAGE" != "$TREE" ]; then cp -R "$STAGE/." "$TREE/" || { echo "FAIL: cannot copy onto $TREE"; exit 1; }; fi
  if [ -f "$STAGE/DELETED.txt" ]; then
    while IFS= read -r f; do
      f="${f%$'\r'}"
      [ -n "$f" ] || continue
      case "$f" in /*|..|../*|*/../*|*/..) echo "FAIL: unsafe path in DELETED.txt: $f"; exit 1 ;; esac
      rm -f "$TREE/$f"
    done < "$STAGE/DELETED.txt"
    rm -f "$TREE/DELETED.txt"
  fi
else
  TREE="$STAGE"
fi

# files in the tree: git view when the tree is a git checkout (ignores build output), plain find otherwise
cd "$TREE" || exit 1
if [ -e .git ]; then
  git -c core.quotepath=false ls-files -co --exclude-standard | sort -u
else
  find . -type f -not -path './.git/*' | sed 's|^\./||' | sort
fi | while IFS= read -r f; do
  case "$f" in *.bundle|MANIFEST.sha256|DELETED.txt) continue ;; esac
  [ -f "$f" ] && printf '%s\n' "$f"
done > "$W/files"
# "<sha256 of content without \r>  <path>": batch sha256sum for all, redo only files that contain \r
# ponytail: paths with a backslash or newline are not supported (sha256sum escapes them)
tr '\n' '\0' < "$W/files" | xargs -0 -r sha256sum -t > "$W/raw"
tr '\n' '\0' < "$W/files" | xargs -0 -r grep -lZUaF $'\r' > "$W/cr"
xargs -0 -r -n1 -P8 sh -c 'printf "%s  %s\n" "$(tr -d "\r" < "$1" | sha256sum | cut -c1-64)" "$1"' _ < "$W/cr" > "$W/crhash"
awk 'FILENAME == ARGV[1] { m[substr($0, 67)] = $0; next } { p = substr($0, 67); print (p in m) ? m[p] : $0 }' "$W/crhash" "$W/raw" > "$W/cur"

tr -d '\r' < "$STAGE/MANIFEST.sha256" > "$W/want"
awk '
  FILENAME == ARGV[1] { want[substr($0, 67)] = substr($0, 1, 64); next }
  { have[substr($0, 67)] = substr($0, 1, 64) }
  END {
    for (p in want) { if (!(p in have)) print "MISSING " p; else if (want[p] != have[p]) print "DIFFERENT " p }
    for (p in have) if (!(p in want)) print "EXTRA " p
  }' "$W/want" "$W/cur" | sort > "$W/diff"
if [ -s "$W/diff" ]; then
  cat "$W/diff"
  echo "FAIL: tree differs from MANIFEST.sha256 ($(wc -l < "$W/diff" | tr -d ' ') file(s))"
  exit 1
fi
echo "OK: $(wc -l < "$W/want" | tr -d ' ') file(s) match MANIFEST.sha256"

[ "${SNAPSHOT_SKIP_TOOLCHAIN:-}" = "1" ] && exit 0

rc=0
jv="$(java -version 2>&1 | sed -n 's/.*version "\([0-9]*\).*/\1/p' | head -1)"
[ "$jv" = "21" ] && echo "OK: jdk 21" || { echo "FAIL: jdk: JDK 21 required (found: ${jv:-none})"; rc=1; }
sdk="${ANDROID_HOME:-}"
[ -z "$sdk" ] && [ -f "$TREE/local.properties" ] && sdk="$(sed -n 's/^sdk\.dir=//p' "$TREE/local.properties" | tr -d '\r' | head -1)"
if [ -n "$sdk" ] && [ -d "$sdk" ]; then echo "OK: android sdk $sdk"; else echo "FAIL: android-sdk: set ANDROID_HOME or sdk.dir in local.properties"; rc=1; fi
# via sh: gradlew is mode 100644 in git
if sh ./gradlew --version >/dev/null 2>&1; then echo "OK: gradlew --version"; else echo "FAIL: gradlew: 'sh ./gradlew --version' failed"; rc=1; fi
exit $rc
