#!/usr/bin/env bash
# Runner của test không cần máy cho cổng thiết bị (allowlist, adb giả). Từ 2.0.0 Task 0a test nằm ở
# tools/tests/dg_{core,measure,iap,banners,release,tools,resume}_test.sh (harness chung: tools/tests/lib/dg_harness.sh);
# file này chạy từng vùng, cộng tổng, và FAIL khi một vùng đỏ, thoát khác 0, thiếu dòng kết, hoặc có file
# dg_*_test.sh không được chạy. CLI giữ nguyên: bash tools/tests/device_gate_allowlist_test.sh (exit 0 = xanh).
set -u
DIR=$(cd "$(dirname "$0")" && pwd)
PARTS="core measure iap banners release tools doc policy nextgen carry resume ab release_local"
total=0; bad=0; broken=0; ran=0
for part in $PARTS; do
  f=dg_${part}_test.sh; ran=$((ran + 1))
  out=$(bash "$DIR/$f" 2>&1); rc=$?
  printf '%s\n' "$out"
  last=$(printf '%s\n' "$out" | tail -1)
  case "$last" in
    "PASS "*/*) total=$((total + ${last#*/}));;
    "FAIL "*/*) total=$((total + ${last#*/})); bad=$((bad + $(echo "${last#FAIL }" | cut -d/ -f1)));;
    *) echo "FAIL: $f không in dòng kết PASS|FAIL n/m"; broken=$((broken + 1));;
  esac
  [ "$rc" -eq 0 ] || { echo "FAIL: $f exit $rc"; broken=$((broken + 1)); }
  echo "== $f: ${last:-<rỗng>} (exit $rc)"
done
files=$(ls "$DIR"/dg_*_test.sh 2>/dev/null | wc -l | tr -d ' ')
[ "$files" -eq "$ran" ] || { echo "FAIL: $files file dg_*_test.sh nhưng chỉ chạy $ran"; broken=$((broken + 1)); }
echo
if [ "$bad" -eq 0 ] && [ "$broken" -eq 0 ]; then echo "PASS $total/$total"; exit 0; fi
echo "FAIL $((bad + broken))/$total"
exit 1
