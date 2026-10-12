#!/usr/bin/env bash
# 2.4.0 NG6: tools/ab_compare.py (T1–T7, fixture = sink --measure thật tách từ DOC/testing/local-sink 1.7.0, 2 lượt a1 a2), `--variant nextgen`
# của gate_args.sh (GV1) và harness_meta.sh (RG1b). Chạy: bash tools/tests/dg_ab_test.sh (hoặc qua runner device_gate_allowlist_test.sh, phần `ab`).
. "$(dirname "$0")/lib/dg_harness.sh" || exit 99
FX=$ROOT/tools/tests/fixtures/ab
ab() { python3 "$ROOT/tools/ab_compare.py" "$@" 2>&1; }
# mutb '<python>': dựng B = a1 + a2 của fixture, chạy <python> trên list `rows` của TỪNG lượt (b1, b2) rồi ghi $TMP/b{1,2}/events.jsonl.
mutb() {
  rm -rf "$TMP/b1" "$TMP/b2"; mkdir -p "$TMP/b1" "$TMP/b2"
  python3 -c 'import json, sys
for n in ("1", "2"):
    rows = [json.loads(l) for l in open(sys.argv[1] + "/a" + n + "/events.jsonl")]
'"$(printf '%s
' "$1" | sed 's/^/    /')"'
    open(sys.argv[2] + "/b" + n + "/events.jsonl", "w").write("".join(json.dumps(r) + "\n" for r in rows))' "$FX" "$TMP"
}
runb() { ab --a "$FX/a1" "$FX/a2" --b "$TMP/b1" "$TMP/b2" "$@"; }
o=$(ab --a "$FX/a1" "$FX/a2" --b "$FX/a1" "$FX/a2"); rc=$?
check "AB1 sink A = B (cùng fixture thật) -> 7 dòng T1..T7 không FAIL, RESULT: PASS, exit 0" \
  "$(has "$o" 'T1 PASS' && has "$o" 'T7 PASS' && hasnt "$o" ' FAIL' && has "$o" 'RESULT: PASS' && [ "$rc" -eq 0 ]; echo $?)" "rc=$rc $o"
mutb '[r.pop("precision_type") for r in rows if r["event_name"] == "ad_revenue"]'; o=$(runb); rc=$?
check "AB2 B thiếu khoá precision_type ở ad_revenue -> T1 FAIL nêu precision_type, RESULT: FAIL, exit 1" \
  "$(has "$o" 'T1 FAIL' && has "$o" 'precision_type' && has "$o" 'RESULT: FAIL' && [ "$rc" -eq 1 ]; echo $?)" "rc=$rc $o"
mutb 'next(r for r in rows if r["event_name"] == "ad_revenue")["precision_type"] = 4'; o=$(runb); rc=$?
check "AB3 B có precision_type=4 -> T3 FAIL (ngoài {0,1,2,3}), RESULT: FAIL" \
  "$(has "$o" 'T3 FAIL' && has "$o" "'4'" && has "$o" 'RESULT: FAIL' && [ "$rc" -eq 1 ]; echo $?)" "rc=$rc $o"
mutb 'next(r for r in rows if r["event_name"] == "ad_revenue")["value_micros"] = -1'; o=$(runb); rc=$?
check "AB4 B có value_micros=-1 -> T4 FAIL, RESULT: FAIL" \
  "$(has "$o" 'T4 FAIL' && has "$o" 'RESULT: FAIL' && [ "$rc" -eq 1 ]; echo $?)" "rc=$rc $o"
mutb 'f = [r for r in rows if r["event_name"] == "ad_show_fail"][0]
rows += [dict(f, event_id="dup-%d" % i) for i in (1, 2)]'; o=$(runb); rc=$?
check "AB5 B có ad_show_fail = A + 2 ở lượt 1 -> T5 FAIL (B <= A + 1), RESULT: FAIL" \
  "$(has "$o" 'T5 FAIL' && has "$o" 'cặp 1' && has "$o" 'RESULT: FAIL' && [ "$rc" -eq 1 ]; echo $?)" "rc=$rc $o"
mutb 'for r in rows:
    if r["event_name"] == "ad_fill" and r["ad_format"] == "interstitial":
        r.update(event_name="ad_no_fill", error_code=3, reason="no_fill"); r.pop("ad_source")'; o=$(runb); rc=$?; o2=$(runb --min-n 2); rc2=$?
check "AB6 fill thấp ở B với n < 10 -> T6 INFO, RESULT: PASS; cùng dữ liệu --min-n 2 -> T6 FAIL (chạy A/B x2 trước khi phân loại)" \
  "$(has "$o" 'T6 INFO' && has "$o" 'RESULT: PASS' && [ "$rc" -eq 0 ] && has "$o2" 'T6 FAIL' && has "$o2" 'A/B x2' && [ "$rc2" -eq 1 ]; echo $?)" "rc=$rc $o
rc2=$rc2 $o2"
o=$(ab --a "$FX/a1" "$FX/a2" --b "$FX/a1" "$TMP/khong-co"); rc=$?; o2=$(ab --a "$FX/a1" "$FX/a2" --b "$FX/a1"); rc2=$?
check "AB7 thiếu thư mục sink của B (hoặc số lượt A != B) -> FAIL nêu sink, RESULT: FAIL, exit 1 (fail-closed)" \
  "$(has "$o" 'không có sink' && has "$o" 'RESULT: FAIL' && [ "$rc" -eq 1 ] && has "$o2" 'RESULT: FAIL' && [ "$rc2" -eq 1 ]; echo $?)" "rc=$rc $o
rc2=$rc2 $o2"
mutb 'rows[:] = [r for r in rows if not (r["event_name"] == "ad_show" and r["ad_format"] == "rewarded")]'; o=$(runb); rc=$?
check "AB8 B không có ad_show rewarded (A có) -> T7 FAIL nêu rewarded, RESULT: FAIL" \
  "$(has "$o" 'T7 FAIL' && has "$o" 'rewarded' && has "$o" 'RESULT: FAIL' && [ "$rc" -eq 1 ]; echo $?)" "rc=$rc $o"
mutb 'next(r for r in rows if r["event_name"] == "ad_revenue")["ad_network"] = "unknown"'; o=$(runb); rc=$?
mutb 'next(r for r in rows if r["event_name"] == "ad_revenue")["ad_network"] = "meta"'; o2=$(runb); rc2=$?
check "AB9 B có ad_network unknown (hoặc tập khác A: meta) -> T2 FAIL, RESULT: FAIL" \
  "$(has "$o" 'T2 FAIL' && has "$o" 'unknown' && [ "$rc" -eq 1 ] && has "$o2" 'T2 FAIL' && has "$o2" 'meta' && [ "$rc2" -eq 1 ]; echo $?)" "rc=$rc $o
rc2=$rc2 $o2"
mutb 'next(r for r in rows if r["event_name"] == "ad_revenue")["value_micros"] = 5'; o=$(runb); rc=$?
check "AB10 tổng value_micros A = 0 mà B > 0 -> T4 FAIL nêu 'tổng A = 0', RESULT: FAIL" \
  "$(has "$o" 'T4 FAIL' && has "$o" 'tổng A = 0' && has "$o" 'RESULT: FAIL' && [ "$rc" -eq 1 ]; echo $?)" "rc=$rc $o"
o=$(ab --a "$FX/real/a" --b "$FX/real/b"); rc=$?
check "AB11 cặp A/B thật (utility vs nextgen, emulator 5570, lượt 3 của NG6b) -> T1..T7 không FAIL, RESULT: PASS" \
  "$(has "$o" 'T1 PASS' && hasnt "$o" ' FAIL' && has "$o" 'RESULT: PASS' && [ "$rc" -eq 0 ]; echo $?)" "rc=$rc $o"
mutb 'rows[:] = [r for r in rows if r["event_name"] != "ad_click"]'; o=$(runb); rc=$?
mutb 'rows[:] = [r for r in rows if r["event_name"] != "ad_show"]'; o2=$(runb); rc2=$?
check "AB12 ad_click chỉ có ở A -> T1 PASS kèm 'info: ad_click', RESULT: PASS; B thiếu ad_show (bắt buộc) -> T1 FAIL nêu ad_show" \
  "$(has "$o" 'T1 PASS' && has "$o" 'info: ad_click' && has "$o" 'RESULT: PASS' && [ "$rc" -eq 0 ] && has "$o2" 'T1 FAIL' && has "$o2" 'ad_show' && [ "$rc2" -eq 1 ]; echo $?)" "rc=$rc $o
rc2=$rc2 $o2"
# GV1: gate_args.sh nhận --variant nextgen như utility (P25): không FAIL, VARIANT=utility cho mọi nhánh của device_gate, STACK=nextgen ghi lại.
o=$( (set -- --variant nextgen --apk "$ROOT/tools/ab_compare.py" --mapping "$ROOT/tools/ab_compare.py"; ANDROID_SERIAL=emulator-5570; . "$ROOT/tools/lib/gate_args.sh"; echo "VARIANT=$VARIANT STACK=$STACK") 2>&1 ); rc=$?
check "GV1 gate_args.sh --variant nextgen -> nhận (không FAIL), VARIANT=utility STACK=nextgen" \
  "$(has "$o" 'VARIANT=utility STACK=nextgen' && hasnt "$o" 'FAIL' && [ "$rc" -eq 0 ]; echo $?)" "rc=$rc $o"
# RG1b: harness_meta.sh đếm nextgen như utility (cần 1); game cần 0.
printf '#!/bin/sh\necho "        A: android:name(0x01010003)=\\"com.app.plugin.IAP_HARNESS\\" (Raw: \\"com.app.plugin.IAP_HARNESS\\")"\n' > "$TMP/aapt2"; chmod +x "$TMP/aapt2"
hm() { ( LOG=$TMP/hm.log; fail=0; . "$ROOT/tools/lib/harness_meta.sh"; check_harness_meta x.apk "$1" "$TMP/aapt2" IAP_HARNESS; exit "$fail" ) 2>&1; }
o=$(hm nextgen); rc=$?; o2=$(hm game); rc2=$?
check "RG1b harness meta nextgen: 1 dòng -> need 1 PASS; game: 1 dòng -> need 0 FAIL" \
  "$(has "$o" '=1 (need 1)' && [ "$rc" -eq 0 ] && has "$o2" '=1 (need 0)' && [ "$rc2" -eq 1 ]; echo $?)" "nextgen rc=$rc $o
game rc=$rc2 $o2"
dg_finish
