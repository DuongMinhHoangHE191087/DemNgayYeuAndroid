#!/usr/bin/env bash
# Tests for scripts/kmp/check-pure-move.sh. Every case runs in a throwaway git repo under mktemp.
set -u
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CHECK="$HERE/../check-pure-move.sh"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
FAILS=0
ok() { echo "PASS: $1"; }
bad() { echo "FAIL: $1"; FAILS=$((FAILS + 1)); }
N=0

# mkrepo: new repo in $R with one base commit: src/a.txt, other/x.txt, other/y.txt, .gitignore (*.log)
mkrepo() {
  N=$((N + 1)); R="$TMP/repo$N"
  mkdir -p "$R/src" "$R/other" && cd "$R" || exit 2
  git init -q . && git config user.email t@t && git config user.name t && git config core.autocrlf false
  printf 'alpha\nbeta\ngamma\n' > src/a.txt
  printf 'one\ntwo\nthree\n' > other/x.txt
  printf 'uno\ndos\ntres\n' > other/y.txt
  printf '*.log\n' > .gitignore
  git add -A && git commit -qm base || exit 2
}
# run <args...>: run the script inside $R, set OUT and RC
run() { OUT="$(cd "$R" && bash "$CHECK" "$@" 2>&1)"; RC=$?; }
idxsum() { sha256sum "$R/.git/index" | cut -d' ' -f1; }
leftovers() { ls "$R/.git" | grep -c 'pure-move' ; }

# 1. pure rename + ignored untracked file, explicit old+new pathspec -> 0; real index untouched
mkrepo; mv src/a.txt src/b.txt; echo junk > src/junk.log
before="$(idxsum)"
run --worktree HEAD -- src/a.txt src/b.txt
{ [ $RC -eq 0 ] && echo "$OUT" | grep -q '^R100'; } && ok "worktree pure rename with ignored file exits 0 and lists R100" || bad "pure rename rc=$RC: $OUT"
[ "$(idxsum)" = "$before" ] && ok "real index unchanged" || bad "real index changed"
[ "$(leftovers)" = 0 ] && ok "no temp index left behind" || bad "temp index left behind"
run --worktree HEAD -- src
[ $RC -eq 0 ] && ok "directory pathspec with ignored file exits 0" || bad "dir pathspec rc=$RC: $OUT"

# 2. changes outside the pathspec (modify, add, delete) do not matter
mkrepo; mv src/a.txt src/b.txt; echo more >> other/x.txt; echo new > other/z.txt; rm other/y.txt
run --worktree HEAD -- src/a.txt src/b.txt
[ $RC -eq 0 ] && ok "worktree_ignoresChangesOutsidePathspec" || bad "outside pathspec rc=$RC: $OUT"

# 3. --worktree without pathspec -> 2, no temp index
mkrepo; mv src/a.txt src/b.txt
run --worktree HEAD
{ [ $RC -eq 2 ] && [ "$(leftovers)" = 0 ]; } && ok "worktree_withoutPathspec_exits2" || bad "no pathspec rc=$RC: $OUT"
run --worktree HEAD --
[ $RC -eq 2 ] && ok "worktree with empty pathspec exits 2" || bad "empty pathspec rc=$RC: $OUT"
run
[ $RC -eq 2 ] && ok "missing base-ref exits 2" || bad "missing base rc=$RC: $OUT"

# 4. things that are not a pure rename -> 1
mkrepo; mv src/a.txt src/b.txt; echo extra >> src/b.txt
run --worktree HEAD -- src/a.txt src/b.txt
[ $RC -eq 1 ] && ok "rename plus one-line edit exits 1" || bad "rename+edit rc=$RC: $OUT"
mkrepo; mv src/a.txt src/b.txt; echo new > src/c.txt
run --worktree HEAD -- src/a.txt src/b.txt src/c.txt
{ [ $RC -eq 1 ] && echo "$OUT" | grep -q 'src/c.txt'; } && ok "added file exits 1 and names it" || bad "added rc=$RC: $OUT"
mkrepo; mv src/a.txt src/b.txt; rm other/y.txt
run --worktree HEAD -- src/a.txt src/b.txt other/y.txt
[ $RC -eq 1 ] && ok "deleted file exits 1" || bad "deleted rc=$RC: $OUT"
mkrepo
run --worktree HEAD -- src/a.txt
[ $RC -eq 1 ] && ok "empty diff exits 1" || bad "empty diff rc=$RC: $OUT"
mkrepo; mv src/a.txt src/b.txt
run --worktree HEAD -- src/b.txt
[ $RC -eq 1 ] && ok "new path only is seen as an add and exits 1" || bad "new only rc=$RC: $OUT"

# 5. B-49 case: baseline taken while sources exist and destinations do not
mkrepo; mkdir -p old; printf 'm1a\nm1b\nm1c\n' > old/m1.txt; printf 'm2a\nm2b\nm2c\n' > old/m2.txt
git add old && git commit -qm old; echo m1d >> old/m1.txt; printf 'u1\nu2\nu3\n' > old/untracked.txt
# snapshot <outfile> <paths...>: the exact recipe B-49/B-51 use (temp index, git add -A, git write-tree)
snapshot() {
  local out="$1"; shift; rm -f "$out"
  ( export GIT_INDEX_FILE="$R/.git/snap.idx"; trap 'rm -f "$GIT_INDEX_FILE"' EXIT
    cd "$R" || exit 1
    git add -A -- "$@" || exit 1
    tree="$(git write-tree)" || exit 1
    [ -n "$tree" ] || exit 1
    printf %s "$tree" > "$out" )
}
BASE="$R/.git/B.base"
before="$(idxsum)"
snapshot "$BASE" old; rc=$?
{ [ $rc -eq 0 ] && [ -f "$BASE" ] && [ "$(cd "$R" && git cat-file -t "$(cat "$BASE")")" = tree ]; } && ok "baseline from source paths only is a tree" || bad "baseline rc=$rc"
[ "$(idxsum)" = "$before" ] && ok "baseline leaves real index alone" || bad "baseline changed real index"
snapshot "$R/.git/B2.base" old dst 2> /dev/null; rc=$?
{ [ $rc -ne 0 ] && [ ! -f "$R/.git/B2.base" ]; } && ok "baseline with missing destination fails and writes no file" || bad "baseline src+dst rc=$rc"
mkdir dst && mv old/* dst/
run --worktree "$(cat "$BASE")" -- old dst
{ [ $RC -eq 0 ] && [ "$(echo "$OUT" | grep -c '^R100')" = 3 ]; } && ok "worktree_preMoveBaseline_sourceExistsDestinationMissing" || bad "after move rc=$RC: $OUT"
[ -z "${GIT_INDEX_FILE-}" ] && [ "$(leftovers)" = 0 ] && ok "GIT_INDEX_FILE unset and no temp index after run" || bad "GIT_INDEX_FILE leaked"
echo x >> dst/m2.txt
run --worktree "$(cat "$BASE")" -- old dst
[ $RC -eq 1 ] && ok "one edited char in a moved file exits 1" || bad "edit after move rc=$RC: $OUT"
run --worktree 0000000000000000000000000000000000000000 -- old dst
{ [ $RC -ne 0 ] && [ $RC -ne 127 ] && [ -z "${GIT_INDEX_FILE-}" ] && [ "$(leftovers)" = 0 ]; } && ok "git failure (bad base-ref) stops with nonzero and cleans up" || bad "bad ref rc=$RC: $OUT"

# 6. default mode (commits) works on a repo with a commit
mkrepo; git mv src/a.txt src/b.txt && git commit -qm move
run HEAD~1 -- src/a.txt src/b.txt
{ [ $RC -eq 0 ] && echo "$OUT" | grep -q '^R100'; } && ok "default mode pure rename commit exits 0" || bad "default pure rc=$RC: $OUT"
run HEAD~1
[ $RC -eq 0 ] && ok "default mode without pathspec exits 0" || bad "default no pathspec rc=$RC: $OUT"
echo extra >> src/b.txt; git commit -qam edit
run HEAD~2 -- src/a.txt src/b.txt
[ $RC -eq 1 ] && ok "default mode rename plus edit exits 1" || bad "default edit rc=$RC: $OUT"
run HEAD -- src/a.txt
[ $RC -eq 1 ] && ok "default mode empty diff exits 1" || bad "default empty rc=$RC: $OUT"

[ $FAILS -eq 0 ] && echo "ALL PASS" || { echo "$FAILS FAILED"; exit 1; }
