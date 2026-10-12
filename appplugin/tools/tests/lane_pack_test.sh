#!/usr/bin/env bash
# Test tools/lane_pack.sh trên plan 1.8.0 thật (PLAN ghim) + plan mới nhất (LP11–13): cắt theo tiêu đề, gói đủ 5 phần, id lạ FAIL một dòng.
# Chạy: bash tools/tests/lane_pack_test.sh   (exit 0 = xanh). Chạy được trên bash 3.2.
set -u
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
LP=$ROOT/tools/lane_pack.sh
P18=$ROOT/docs/superpowers/plans/2026-09-30-sdk-1.8.0-plan.md
total=0; bad=0
check() { # $1 = tên, $2 = 0 nếu đạt, $3 = chi tiết khi hỏng
  total=$((total+1))
  if [ "$2" = 0 ]; then echo "ok   $1"; else bad=$((bad+1)); echo "FAIL $1"; echo "${3:-}"; fi
}
cnt() { printf '%s\n' "$1" | command grep -c "$2"; }

o5=$(PLAN=$P18 bash "$LP" 5 2>&1); rc5=$?
check "task 5: exit 0 và đúng một mục '## Task 5'" "$([ $rc5 -eq 0 ] && [ "$(cnt "$o5" '^## Task 5')" = 1 ]; echo $?)" "rc=$rc5"
check "task 5: không lẫn mục Task 4/6/50" "$([ "$(cnt "$o5" '^## Task [0-9R]')" = 1 ]; echo $?)"
check "task 5: có Global Constraints + KB + D1–D12 + hàng lane LA" "$([ "$(cnt "$o5" '^## Global Constraints')" = 1 ] && [ "$(cnt "$o5" '^### Khung bước chung')" = 1 ] && [ "$(cnt "$o5" '^## Quyết định CEO [(]')" = 1 ] && [ "$(cnt "$o5" '^| LA (T4 → T5)')" = 1 ]; echo $?)"
o4=$(PLAN=$P18 bash "$LP" 4 2>&1)
check "task 4: trích §Hợp đồng A (task nhắc A) và không có B" "$([ "$(cnt "$o4" '^### A\. ')" = 1 ] && [ "$(cnt "$o4" '^### B\. ')" = 0 ]; echo $?)"
o9=$(PLAN=$P18 bash "$LP" 9 2>&1)
check "task 9: trích §Hợp đồng D và E (task nhắc 'D, E')" "$([ "$(cnt "$o9" '^### D\. ')" = 1 ] && [ "$(cnt "$o9" '^### E\. ')" = 1 ]; echo $?)"
o1=$(PLAN=$P18 bash "$LP" 1 2>&1)
check "task 1: hàng LT khớp T1 nhưng không khớp T16 riêng (LT có cả T1 và T16 nên đúng 1 hàng)" "$([ "$(cnt "$o1" '^| LT ')" = 1 ] && [ "$(cnt "$o1" '^| LA ')" = 0 ]; echo $?)"
oR=$(PLAN=$P18 bash "$LP" R 2>&1); rcR=$?
check "task R: exit 0, mục '## Task R'" "$([ $rcR -eq 0 ] && [ "$(cnt "$oR" '^## Task R')" = 1 ]; echo $?)"
o99=$(PLAN=$P18 bash "$LP" 99 2>&1 >/dev/null); rc99=$?
check "task 99: exit != 0 và đúng một dòng lỗi" "$([ $rc99 -ne 0 ] && [ "$(printf '%s\n' "$o99" | wc -l | tr -d ' ')" = 1 ]; echo $?)" "rc=$rc99 out=$o99"
o0=$(PLAN=$P18 bash "$LP" 2>&1 >/dev/null); rc0=$?
check "không đối số: exit != 0, một dòng" "$([ $rc0 -ne 0 ] && [ "$(printf '%s\n' "$o0" | wc -l | tr -d ' ')" = 1 ]; echo $?)"
oX=$(PLAN=$P18 bash "$LP" '5;x' 2>&1 >/dev/null); rcX=$?
check "id có ký tự lạ: exit != 0" "$([ $rcX -ne 0 ]; echo $?)"

oT=$(bash "$LP" T0a 2>&1); rcT=$?
check "LP11 id chữ T0a: exit 0 và mục '## Task T0a'" "$([ $rcT -eq 0 ] && [ "$(cnt "$oT" '^## Task T0a')" = 1 ]; echo $?)" "rc=$rcT $(printf '%s\n' "$oT" | head -1)"
newest=$(ls "$ROOT"/docs/superpowers/plans/*-sdk-*-plan.md | sort | tail -1 | xargs basename)
oD=$(env -u PLAN bash "$LP" R 2>&1)
check "LP12 không PLAN: dùng plan mới nhất ($newest)" "$([ "$(cnt "$oD" "plan=$newest")" = 1 ]; echo $?)" "$(printf '%s\n' "$oD" | head -1)"
okh=0
for h in PERF-B IG-G E5; do
  oH=$(PLAN=$ROOT/docs/superpowers/plans/2026-10-03-sdk-2.1.0-plan.md bash "$LP" "$h" 2>&1); [ "$(cnt "$oH" "^## $h")" = 1 ] || okh=1
done
check "LP13 tiêu đề '## PERF-B', '## IG-G', '## E5' (không chữ Task)" "$okh"

oM=$(PLAN=$ROOT/docs/superpowers/plans/2026-10-04-sdk-2.2.0-plan.md bash "$LP" MIN24 2>&1)
check "LP14 id chữ MIN24: in hàng lane 'LA (MIN24, LIC, PSH, SEG' (rid không thêm T)" "$([ "$(cnt "$oM" '^| LA (MIN24, LIC, PSH, SEG')" = 1 ]; echo $?)" "$(printf '%s\n' "$oM" | command grep '^| ' | head -3)"

echo
if [ "$bad" -eq 0 ]; then echo "PASS $total/$total"; exit 0; fi
echo "FAIL $bad/$total"
exit 1
