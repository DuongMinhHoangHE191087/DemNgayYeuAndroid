#!/usr/bin/env bash
# Proves a change is a pure move: every changed path is an exact rename (R100), nothing added, deleted or edited.
# Usage: check-pure-move.sh [--worktree] <base-ref> [-- <pathspec>...]
#   default    : git diff --name-status -M100% <base-ref> HEAD   (needs commits)
#   --worktree : no commit needed (DEC-08). Temp index = <base-ref> + `git add -A -- <pathspec>`, compared with <base-ref>.
#                Only paths inside <pathspec> count; pass old AND new paths (new path alone looks like an add).
#                `-- <pathspec>` is mandatory here so parallel tasks never see each other's changes.
# Exit: 0 pure move, 1 not pure or empty diff, 2 usage error, 3 a git command failed.
set -u
usage() { echo "usage: check-pure-move.sh [--worktree] <base-ref> [-- <pathspec>...]" >&2; exit 2; }

wt=0; base=""; paths=()
while [ $# -gt 0 ]; do
  case "$1" in
    --worktree) wt=1; shift ;;
    --) shift; paths=("$@"); break ;;
    -*) usage ;;
    *) [ -z "$base" ] || usage; base="$1"; shift ;;
  esac
done
[ -n "$base" ] || usage
if [ $wt -eq 1 ] && [ ${#paths[@]} -eq 0 ]; then
  echo "error: --worktree requires -- <pathspec> (never git add -A on the whole tree)" >&2; exit 2
fi

G=(git -c core.quotepath=off)
if [ $wt -eq 1 ]; then
  gd="$(git rev-parse --absolute-git-dir)" || { echo "error: not a git repo" >&2; exit 3; }
  idx="$gd/pure-move.$$.idx"
  trap 'rm -f "$idx"; unset GIT_INDEX_FILE' EXIT
  export GIT_INDEX_FILE="$idx"
  "${G[@]}" read-tree "$base" || { echo "error: git read-tree $base failed" >&2; exit 3; }
  "${G[@]}" add -A -- "${paths[@]}" || { echo "error: git add -A failed (pathspec matches nothing?)" >&2; exit 3; }
  out="$("${G[@]}" diff --cached -M100% --name-status "$base" -- "${paths[@]}")" || { echo "error: git diff failed" >&2; exit 3; }
else
  if [ ${#paths[@]} -gt 0 ]; then
    out="$("${G[@]}" diff --name-status -M100% "$base" HEAD -- "${paths[@]}")" || { echo "error: git diff failed" >&2; exit 3; }
  else
    out="$("${G[@]}" diff --name-status -M100% "$base" HEAD)" || { echo "error: git diff failed" >&2; exit 3; }
  fi
fi

if [ -z "$out" ]; then echo "EMPTY DIFF: no file moved (a task that moves nothing must not call this script)"; exit 1; fi
echo "$out"
notpure="$(echo "$out" | grep -v '^R100' || true)"
if [ -n "$notpure" ]; then
  echo "NOT A PURE MOVE:"; echo "$notpure" | sed 's/^/  /'
  exit 1
fi
echo "pure move OK ($(echo "$out" | wc -l | tr -d ' ') R100)"
