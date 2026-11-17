#!/usr/bin/env bash
# Tests for scripts/kmp/snapshot-pack.sh and snapshot-verify.sh (F-31). Uses a throwaway git repo in a temp dir.
set -u
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PACK="$HERE/../snapshot-pack.sh"
VERIFY="$HERE/../snapshot-verify.sh"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
FAILS=0
ok() { echo "PASS: $1"; }
bad() { echo "FAIL: $1"; FAILS=$((FAILS + 1)); }
export SNAPSHOT_SKIP_TOOLCHAIN=1

R="$TMP/repo"; mkdir -p "$R/sub" "$R/build"
cd "$R" || exit 1
git init -q . && git config user.email t@t && git config user.name t && git config core.autocrlf false
printf 'ign.log\nbuild/\n' > .gitignore
echo one > a.txt; echo two > b.txt; echo three > sub/c.txt; echo four > "sp ace.txt"; printf 'crlf\r\nline\r\n' > crlf.txt
git add -A && git commit -qm init
echo changed >> a.txt          # tracked, modified
echo fresh > new.txt           # untracked
echo log > ign.log; echo obj > build/x.o; echo bun > x.bundle   # ignored / bundle
rm b.txt                       # tracked, deleted from working tree

S1="$TMP/s1.tar.gz"
KMP_ROOT="$R" bash "$PACK" "$S1" >/dev/null 2>&1; rc=$?
{ [ $rc -eq 0 ] && [ -s "$S1" ]; } && ok "pack exits 0 and writes the tar" || { bad "pack rc=$rc"; echo "ALL ABORTED"; exit 1; }

L="$TMP/s1.list"; tar -tzf "$S1" > "$L"
miss=""
for f in a.txt new.txt sub/c.txt "sp ace.txt" crlf.txt .gitignore MANIFEST.sha256; do grep -qxF "$f" "$L" || miss="$miss $f"; done
[ -z "$miss" ] && ok "tar has tracked, untracked and manifest files" || bad "missing from tar:$miss"
leak=""
for f in ign.log build/x.o x.bundle b.txt; do grep -qxF "$f" "$L" && leak="$leak $f"; done
grep -q '^\.git/' "$L" && leak="$leak .git/"
[ -z "$leak" ] && ok "ignored, bundle, deleted files and .git are not in the tar" || bad "leaked into tar:$leak"

D1="$TMP/d1"; mkdir "$D1"; tar -xzf "$S1" -C "$D1"
grep -q ' b.txt$' "$D1/MANIFEST.sha256" && bad "manifest lists deleted file" || ok "manifest omits deleted file"
bash "$VERIFY" "$D1" >/dev/null 2>&1; rc=$?; [ $rc -eq 0 ] && ok "verify dir exits 0" || bad "verify dir rc=$rc"
bash "$VERIFY" "$S1" >/dev/null 2>&1; rc=$?; [ $rc -eq 0 ] && ok "verify tar exits 0" || bad "verify tar rc=$rc"

# negative cases on a fresh copy each time; verify must exit 1 and name the file
neg() { # neg <label> <expected-name> <mutation command run inside the copy>
  local d="$TMP/neg"; rm -rf "$d"; cp -R "$D1" "$d"
  (cd "$d" && eval "$3")
  local out; out="$(bash "$VERIFY" "$d" 2>&1)"; local rc=$?
  { [ $rc -eq 1 ] && echo "$out" | grep -qF "$2"; } && ok "$1 -> exit 1" || bad "$1 rc=$rc out=$out"
}
neg "one changed byte" a.txt 'printf X >> a.txt'
neg "deleted file" sub/c.txt 'rm sub/c.txt'
neg "extra file" stranger.txt 'echo s > stranger.txt'

# CRLF vs LF content hashes the same (the manifest strips \r)
d="$TMP/crlf"; rm -rf "$d"; cp -R "$D1" "$d"; printf 'crlf\nline\n' > "$d/crlf.txt"
bash "$VERIFY" "$d" >/dev/null 2>&1; rc=$?; [ $rc -eq 0 ] && ok "CRLF vs LF is not a difference" || bad "crlf rc=$rc"

# --since: only changed/new files plus DELETED.txt; --apply then verify
cd "$R" || exit 1
echo more >> sub/c.txt; rm new.txt; echo n2 > n2.txt
S2="$TMP/s2.tar.gz"
KMP_ROOT="$R" bash "$PACK" --since "$D1/MANIFEST.sha256" "$S2" >/dev/null 2>&1; rc=$?
[ $rc -eq 0 ] && ok "pack --since exits 0" || bad "pack --since rc=$rc"
tar -tzf "$S2" | sort | tr '\n' ' ' > "$TMP/s2.list"
want="DELETED.txt MANIFEST.sha256 n2.txt sub/c.txt "
[ "$(cat "$TMP/s2.list")" = "$want" ] && ok "--since holds only changed files, DELETED.txt and the manifest" || bad "--since list: $(cat "$TMP/s2.list")"
tar -xzOf "$S2" DELETED.txt | tr -d '\r' | grep -qxF new.txt && ok "DELETED.txt names the removed file" || bad "DELETED.txt lacks new.txt"

D3="$TMP/d3"; cp -R "$D1" "$D3"
KMP_ROOT="$D3" bash "$VERIFY" --apply "$S2" >/dev/null 2>&1; rc=$?
{ [ $rc -eq 0 ] && [ ! -e "$D3/new.txt" ] && [ -f "$D3/n2.txt" ]; } && ok "--apply updates the tree and verifies (exit 0)" || bad "--apply rc=$rc"
bash "$VERIFY" "$D3" >/dev/null 2>&1; rc=$?; [ $rc -eq 0 ] && ok "verify after --apply exits 0" || bad "verify after apply rc=$rc"

[ $FAILS -eq 0 ] && echo "ALL PASS" || { echo "$FAILS FAILED"; exit 1; }
