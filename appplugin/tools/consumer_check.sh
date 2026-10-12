#!/usr/bin/env bash
# 2.4.0 NGPUB (D5-240): dựng dự án tiêu thụ (tools/tests/fixtures/consumer) trên repo cục bộ build/repo, in cây `releaseRuntimeClasspath` đã giải và kiểm.
# Dùng: tools/consumer_check.sh [--combo 1|2|3] [--repo DIR]. Mặc định chạy tổ hợp 1 và 2. Cần: publishAllPublicationsToLocalCheckRepository trước; Gradle --offline (CC_OFFLINE=0 bỏ --offline, cho CI online).
#  1 legacy: appplugin + -game + -max (như 2.3.0). 2 nextgen + -game + luật thay thế: 0 com.app.plugin:appplugin:, 0 play-services-ads(-lite).
#  3 như 2 không có luật thay thế: kỳ vọng FAIL "trùng appplugin + appplugin-nextgen" (chứng minh luật cần thiết), rc 1.
# Fail-closed: thiếu repo / thiếu artifact / Gradle lỗi = FAIL. Env: CC_GRADLEW (mặc định gradlew gốc), CC_VER (mặc định sdkVersion), CC_FX, CC_OFFLINE (mặc định 1); cần JAVA_HOME + ANDROID_HOME (compileSdk 36).
set -u
SELF=$(cd "$(dirname "$0")" && pwd); ROOT=$SELF/..
GW=${CC_GRADLEW:-$ROOT/gradlew}; FX=${CC_FX:-$SELF/tests/fixtures/consumer}; REPO=$ROOT/build/repo; COMBOS="1 2"
VER=${CC_VER:-$(sed -n 's/^val sdkVersion = "\(.*\)".*/\1/p' "$ROOT/build.gradle.kts" | head -1)}
while [ $# -gt 0 ]; do case $1 in --combo) COMBOS=${2:?}; shift 2;; --repo) REPO=${2:?}; shift 2;; *) echo "FAIL: tham số lạ $1"; exit 2;; esac; done
[ -d "$REPO" ] || { echo "FAIL: không có repo '$REPO' (chạy publishAllPublicationsToLocalCheckRepository trước)"; echo "RESULT: FAIL"; exit 1; }
[ -n "$VER" ] || { echo "FAIL: không đọc được sdkVersion"; echo "RESULT: FAIL"; exit 1; }
REPO=$(cd "$REPO" && pwd); OFF=--offline; [ "${CC_OFFLINE:-1}" = 1 ] || OFF=; T=; trap 'rm -rf "${T:-}"' EXIT INT TERM
fail=0; bad() { echo "FAIL: $*"; fail=1; }
# Chuẩn hoá cây: bỏ tiền tố cây + (*)/(c), "a:b:1 -> c:d:2" lấy vế phải (đã thay thế), "a:b:1 -> 2" thành a:b:2. Mỗi dòng một toạ độ.
norm() { awk '{ l=$0; sub(/^[| +\\-]*/,"",l); sub(/ \([*cn]\)$/,"",l); n=index(l," -> ");
  if (n) { r=substr(l,n+4); if (index(r,":")==0) { split(l,a,":"); r=a[1] ":" a[2] ":" r }; l=r }; print l }'; }
for c in $COMBOS; do
  case $c in 1) NEED="appplugin appplugin-max appplugin-game";; 2|3) NEED="appplugin-nextgen appplugin-game";; *) bad "tổ hợp lạ $c"; continue;; esac
  echo "== tổ hợp $c (phiên bản $VER) =="; ok=1
  for a in $NEED; do [ -d "$REPO/com/app/plugin/$a/$VER" ] || { bad "tổ hợp $c: repo thiếu com.app.plugin/$a/$VER"; ok=0; }; done
  [ "$ok" -eq 1 ] || continue
  T=$(mktemp -d "${TMPDIR:-/tmp}/consumer-check.XXXXXX") || { bad "mktemp"; continue; }; cp "$FX/settings.gradle.kts" "$FX/build.gradle.kts" "$T/"
  out=$("$GW" -p "$T" $OFF -q -Prepo="$REPO" -Pver="$VER" -Pcombo="$c" dependencies --configuration releaseRuntimeClasspath 2>&1); rc=$?; rm -rf "$T"; T=
  if [ "$rc" -ne 0 ] || printf '%s\n' "$out" | command grep -q 'FAILED$'; then bad "tổ hợp $c: Gradle lỗi rc=$rc ($(printf '%s\n' "$out" | command grep -E 'FAILED|Could not|What went wrong' | head -3 | tr '\n' ' '))"; continue; fi
  tree=$(printf '%s\n' "$out" | norm); printf '%s\n' "$tree" | command grep -E '^com\.app\.plugin:' | sort -u | sed 's/^/  /'
  has() { printf '%s\n' "$tree" | command grep -qE "$1"; }
  case $c in
    1) for a in $NEED; do has "^com\.app\.plugin:$a:$VER\$" || bad "tổ hợp 1: thiếu $a:$VER"; done
       has '^com\.app\.plugin:appplugin-nextgen:' && bad "tổ hợp 1 (legacy) có appplugin-nextgen";;
    2) for a in $NEED; do has "^com\.app\.plugin:$a:$VER\$" || bad "tổ hợp 2: thiếu $a:$VER"; done
       has '^com\.app\.plugin:appplugin:' && bad "tổ hợp 2: còn com.app.plugin:appplugin: (luật thay thế không ăn)"
       has '^com\.google\.android\.gms:play-services-ads(-lite)?:' && bad "tổ hợp 2: còn play-services-ads(-lite)";;
    3) if has '^com\.app\.plugin:appplugin:' && has '^com\.app\.plugin:appplugin-nextgen:'; then bad "trùng appplugin + appplugin-nextgen"
       else echo "PASS: tổ hợp 3 không trùng (luật thay thế có thể không cần)"; fi;;
  esac
done
echo "RESULT: $([ "$fail" -eq 0 ] && echo PASS || echo FAIL)"; exit "$fail"
