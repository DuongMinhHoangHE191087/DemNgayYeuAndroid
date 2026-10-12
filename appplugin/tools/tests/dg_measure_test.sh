#!/usr/bin/env bash
# Test không cần máy của cổng thiết bị — vùng measure: --measure (G11), measure_check.py, reconcile/daily_session, TF4, TF5.
# Chạy: bash tools/tests/dg_measure_test.sh (hoặc qua runner device_gate_allowlist_test.sh). exit 0 = xanh.
. "$(dirname "$0")/lib/dg_harness.sh" || exit 99

# ── 1.7.0 Task 13: --measure (G11), measure_check.py, reconcile_session.sh (H6a), daily_session.sh (pha A) ──
# Fixture G11 hợp lệ: pha A (process SA, 4 hàng tới sink trước pha C), pha B (cùng process, sink không tới được,
# 2 event bền lưu đĩa), pha C (process SC, replay 2 hàng giữ session_id + ts của pha B). measure_check.py chạy bằng
# python3 hệ thống (không pydantic vẫn phải import đúng hàm dedup_key của backend).
M=$TMP/measure; mkdir -p "$M"
MK=$ROOT/tools/measure_check.py
SA=aaaaaaaa-0000-4000-8000-00000000000a; SC=cccccccc-0000-4000-8000-00000000000c
TC=1790741000000
TB=1790740890000
# $1 tên, $2 event_id, $3 session_id, $4 sequence_id, $5 client_ts_ms, $6 received_ts_ms, $7 trường thêm (", \"k\": v")
mrow() {
  printf '{"event_name": "%s", "sequence_id": %s, "client_ts_ms": %s, "client_ts_date": "2026-09-30", "user_id": "u1", "session_id": "%s", "dedup_key": "ev:%s", "event_id": "%s", "schema_version": 2, "sdk_version": "1.7.0", "package_name": "test.sdk.zd", "consent_tier": "GRANTED"%s, "received_ts_ms": %s, "is_known_event": true, "app_id": "default"}\n' \
    "$1" "$4" "$5" "$3" "$2" "$2" "${7:-}" "$6"
}
{
  mrow session_start e-a1 "$SA" 1 1790740800001 1790740810001
  mrow ad_request e-a2 "$SA" 2 1790740800002 1790740810002 ', "request_id": "r1", "ad_format": "interstitial"'
  mrow ad_fill e-a3 "$SA" 3 1790740800003 1790740810003 ', "request_id": "r1", "ad_format": "interstitial"'
  mrow ad_revenue e-a4 "$SA" 4 1790740800004 1790740810004 ', "imp_id": "imp-1", "value_micros": 0'
  mrow session_start e-c1 "$SC" 1 1790741000001 1790741005001
  mrow ad_revenue e-b1 "$SA" 6 1790740900006 1790741010001 ', "imp_id": "imp-2", "value_micros": 0'
  mrow session_end e-b2 "$SA" 7 1790740900007 1790741010002 ', "duration_ms": 1000'
} > "$M/ok.jsonl"
cat > "$M/a.log" <<EOF
09-30 04:00:00.000  4242  4242 I AppPlugin: logevent: session_id=$SA
09-30 04:00:30.000  1206  2899 V FA-SVC  : Logging event: origin=app,name=ad_impression,params=Bundle[{imp_id=imp-1, value=0.0}]
09-30 04:00:31.000  4242  4242 I AppPlugin: rev af: imp=imp-1 started=1
09-30 04:00:40.000  4242  4242 I AppPlugin: logevent stats session=$SA built=4 accepted=3 rejected=0 dropped=0 durable_stored=2 durable_replayed=0
EOF
cat > "$M/b.log" <<EOF
09-30 04:01:40.000  4242  4299 I AppPlugin: logevent durable stored ad_revenue e-b1
09-30 04:01:40.100  4242  4299 I AppPlugin: logevent durable stored session_end e-b2
09-30 04:01:45.000  4242  4242 I AppPlugin: logevent stats session=$SA built=7 accepted=4 rejected=0 dropped=0 durable_stored=4 durable_replayed=0
EOF
cat > "$M/c.log" <<EOF
09-30 04:03:20.000  5353  5353 I AppPlugin: logevent: session_id=$SC
09-30 04:03:21.000  5353  5390 I AppPlugin: logevent durable replayed 2 (round 1, 0 stay on disk)
EOF
# R-13: --t-b = ms máy chủ lúc bắt đầu pha B (I1). $2 = log pha B khác (mặc định b.log).
g11() { python3 "$MK" g11 --jsonl "$1" --log-a "$M/a.log" --log-b "${2:-$M/b.log}" --log-c "$M/c.log" --t-b "$TB" --t-c "$TC" 2>&1; }

out0=$(g11 "$M/ok.jsonl"); rc0=$?
out=$(g11 "$M/khong-co.jsonl"); rc=$?
: > "$M/empty.jsonl"
oute=$(g11 "$M/empty.jsonl"); rce=$?
# R-13 I3: (i) lấy accepted từ dòng stats của pha B (A thiếu phần đuôi: accepted=3 < 4); B không có dòng stats -> không quyết được.
command grep -v 'logevent stats' "$M/b.log" > "$M/b-nostats.log"
outn=$(g11 "$M/ok.jsonl" "$M/b-nostats.log"); rcn=$?
check "jsonl-missing: fixture G11 đủ (accepted pha B) -> PASS; JSONL không có / 0 dòng / pha B không có dòng stats -> FAIL (fail-closed), exit 1" \
  "$([ "$rc0" -eq 0 ] && has "$out0" 'RESULT: PASS' && has "$out0" 'accepted=4' && [ "$rc" -eq 1 ] && has "$out" 'FAIL: JSONL' && has "$out" 'RESULT: FAIL' \
    && [ "$rce" -eq 1 ] && has "$oute" 'FAIL: JSONL' && has "$oute" 'RESULT: FAIL' && hasnt "$oute" 'RESULT: PASS' \
    && [ "$rcn" -eq 1 ] && has "$outn" 'FAIL: (i) không quyết được' && hasnt "$outn" 'RESULT: PASS'; echo $?)" "stats B thiếu: exit=$rcn
$outn
đủ: exit=$rc0
$out0
thiếu: exit=$rc
$out
rỗng: exit=$rce
$oute"

command grep -v '"e-b2"' "$M/ok.jsonl" > "$M/lost.jsonl"
out=$(g11 "$M/lost.jsonl"); rc=$?
# R-13 I1: replay đóng dấu ts lại lúc replay (ts = t_c + 1500, pha C bắt đầu t_c + 2000) -> (iv) FAIL.
# R-13 I2: event bền của pha B tới sink TRƯỚC pha C (sink còn tới được, không kill/replay) -> (ii) FAIL.
sed -e 's/"client_ts_ms": 1790740900006/"client_ts_ms": 1790741001500/' -e 's/"client_ts_ms": 1790741000001/"client_ts_ms": 1790741002000/' "$M/ok.jsonl" > "$M/restamp.jsonl"
sed 's/"received_ts_ms": 1790741010001/"received_ts_ms": 1790740999999/' "$M/ok.jsonl" > "$M/early.jsonl"
outs=$(g11 "$M/restamp.jsonl"); rcs=$?
oute=$(g11 "$M/early.jsonl"); rce=$?
check "durable-missing: event bền pha B thiếu sau pha C -> (ii) FAIL nêu event; replay đóng dấu ts lại -> (iv) FAIL; tới sink trước pha C -> (ii) FAIL" \
  "$([ "$rc" -eq 1 ] && has "$out" 'G11 (ii) FAIL' && has "$out" 'session_end e-b2' && has "$out" 'RESULT: FAIL' \
    && [ "$rcs" -eq 1 ] && has "$outs" 'G11 (iv) FAIL' && hasnt "$outs" 'RESULT: PASS' \
    && [ "$rce" -eq 1 ] && has "$oute" 'G11 (ii) FAIL' && has "$oute" 'trước pha C' && hasnt "$oute" 'RESULT: PASS'; echo $?)" "thiếu: exit=$rc
$out
đóng dấu lại: exit=$rcs
$outs
tới sớm: exit=$rce
$oute"

# Trùng thô (replay gửi lại đúng body: cùng event_id) gộp được -> PASS, ghi số trùng thô; cùng một lần phát (user,
# session, tên, sequence_id, ts) mà mang hai event_id -> dedup_key của backend không gộp -> trùng sau dedup = FAIL.
{ cat "$M/ok.jsonl"; mrow ad_revenue e-b1 "$SA" 6 1790740900006 1790741010009 ', "imp_id": "imp-2", "value_micros": 0'; } > "$M/rawdup.jsonl"
{ cat "$M/ok.jsonl"; mrow ad_revenue e-b1x "$SA" 6 1790740900006 1790741010009 ', "imp_id": "imp-2", "value_micros": 0'; } > "$M/dup.jsonl"
outr=$(g11 "$M/rawdup.jsonl"); rcr=$?
out=$(g11 "$M/dup.jsonl"); rc=$?
# R-13 m9: cột dedup_key của sink khác khoá tính lại bằng hàm backend -> FAIL (khử trùng không còn "đúng như MERGE").
sed 's/"dedup_key": "ev:e-a2"/"dedup_key": "ev:khac"/' "$M/ok.jsonl" > "$M/keydiff.jsonl"
outk=$(g11 "$M/keydiff.jsonl"); rck=$?
check "dup-after-dedup: trùng thô cùng event_id -> (iii) PASS, trùng thô=1; cùng lần phát hai event_id -> (iii) FAIL trùng sau dedup=1" \
  "$([ "$rcr" -eq 0 ] && has "$outr" 'G11 (iii) PASS' && has "$outr" 'trùng thô=1' \
    && [ "$rc" -eq 1 ] && has "$out" 'G11 (iii) FAIL' && has "$out" 'trùng sau dedup=1' && has "$out" 'RESULT: FAIL' \
    && [ "$rck" -eq 1 ] && has "$outk" 'dedup_key lệch' && hasnt "$outk" 'RESULT: PASS'; echo $?)" "cột lệch: exit=$rck
$outk
thô: exit=$rcr
$outr
trùng: exit=$rc
$out"

# H6a: FA origin=app (imp_id) = rev af started=1 = JSONL ad_revenue của session (sau dedup), hiệu tập rỗng.
cat > "$M/h.log" <<EOF
09-30 04:00:30.000  1206  2899 V FA-SVC  : Logging event: origin=app,name=ad_impression,params=Bundle[{imp_id=imp-1, value=0.0}]
09-30 04:00:30.500  1206  2899 V FA-SVC  : Logging event: origin=am,name=ad_impression(_ai),params=Bundle[{ga_event_origin(_o)=am}]
09-30 04:00:31.000  4242  4242 I AppPlugin: rev af: imp=imp-1 started=1
09-30 04:01:30.000  1206  2899 V FA-SVC  : Logging event: origin=app,name=ad_impression,params=Bundle[{imp_id=imp-2, value=0.0}]
09-30 04:01:31.000  4242  4242 I AppPlugin: rev af: imp=imp-2 started=1
EOF
out0=$(bash "$ROOT/tools/reconcile_session.sh" "$M/h.log" "$M/ok.jsonl" "$SA" 2>&1); rc0=$?
command grep -v '"e-b1"' "$M/ok.jsonl" > "$M/h6a-diff.jsonl"
out=$(bash "$ROOT/tools/reconcile_session.sh" "$M/h.log" "$M/h6a-diff.jsonl" "$SA" 2>&1); rc=$?
check "h6a-diff: 2/2/2 cùng imp_id -> PASS (origin=am ghi riêng); JSONL thiếu 1 ad_revenue -> 2/2/1, FAIL, in imp_id lệch (imp-2)" \
  "$([ "$rc0" -eq 0 ] && has "$out0" 'RESULT: PASS' && has "$out0" 'origin=am=1' \
    && [ "$rc" -eq 1 ] && has "$out" 'RESULT: FAIL' && has "$out" 'imp-2' && has "$out" 'JSONL ad_revenue=1'; echo $?)" "đủ: exit=$rc0
$out0
lệch: exit=$rc
$out"

prepare_daily
out0=$(run_daily --serial 65c9b673); rc0=$?  # R-13 I5: máy thật (Redmi) phải được nhận cho Task R #12
prepare_daily
out=$(FAKE_MG_IMP=0 run_daily); rc=$?
nz=$(printf '%s\n' "$out" | command grep -c 'invalid (0 impressions)')
check "zero-impressions-invalid: pha A đủ (serial máy thật 65c9b673) -> valid, PASS; 0 dòng FA ad_impression -> invalid (0 impressions) ×3 (chạy lại tối đa 2), RESULT: FAIL, không PASS" \
  "$([ "$rc0" -eq 0 ] && has "$out0" 'phase A: valid' && has "$out0" 'RESULT: PASS' \
    && [ "$rc" -eq 1 ] && [ "$nz" -eq 3 ] && has "$out" 'RESULT: FAIL' && hasnt "$out" 'RESULT: PASS' && hasnt "$out" 'phase A: valid'; echo $?)" "đủ: exit=$rc0
$out0
0 impression: exit=$rc invalid lines=$nz
$out"

prepare_daily
out=$(FAKE_MG_FOCUS=lost run_daily); rc=$?
check "focus-lost-invalid: mCurrentFocus vẫn là AdActivity sau show_full -> invalid (focus), RESULT: FAIL, không PASS" \
  "$([ "$rc" -eq 1 ] && has "$out" 'invalid (focus)' && has "$out" 'RESULT: FAIL' && hasnt "$out" 'RESULT: PASS' && hasnt "$out" 'phase A: valid'; echo $?)" "exit=$rc
$out
adb calls (cuối): $(tail -8 "$TMP/adb-calls")"

# R-13 (CEO: bài học DAILY d1 bắt buộc có test) — "trông hợp lệ mà không hiện quảng cáo" phải bị bắt:
# (a) có FA ad_impression nhưng 0 ad_revenue của session trong JSONL -> invalid; (b) chạm không tới app (logcat không
# có "call show" mới) -> invalid (ui), không coi là đã chạm.
prepare_daily
out=$(FAKE_MG_REV=0 run_daily); rc=$?
nz=$(printf '%s\n' "$out" | command grep -c 'invalid (0 impressions)')
check "phase-a-no-revenue-invalid: FA ad_impression có nhưng 0 ad_revenue của session -> invalid (0 impressions) ×3, RESULT: FAIL, không valid" \
  "$([ "$rc" -eq 1 ] && [ "$nz" -eq 3 ] && has "$out" 'JSONL ad_revenue=0' && has "$out" 'RESULT: FAIL' && hasnt "$out" 'RESULT: PASS' \
    && hasnt "$out" 'phase A: valid'; echo $?)" "exit=$rc invalid lines=$nz
$out"

prepare_daily
out=$(FAKE_MG_DROP=1 run_daily); rc=$?
ntap=$(command grep -c '^shell input tap 540 1350$' "$TMP/adb-calls")
check "tap-not-delivered-invalid: logcat không có 'call show' mới sau 3 lần chạm -> invalid (ui), RESULT: FAIL, không valid" \
  "$([ "$rc" -eq 1 ] && has "$out" "invalid (ui): chạm 'Show Interstitial' 3 lần" && [ "$ntap" -eq 9 ] && has "$out" 'RESULT: FAIL' \
    && hasnt "$out" 'RESULT: PASS' && hasnt "$out" 'phase A: valid'; echo $?)" "exit=$rc taps=$ntap
$out"

# R-13 I4: --local-days — một phiên = một ngày: ngày UTC+7 của client_ts_ms ĐẦU TIÊN của phiên (không đếm hai lần phiên
# vắt qua 00:00 +07). 23:59 D1 / 00:01 D2 / 00:01 D3 -> 3 PASS; D1 / D3 / D4 -> 2 FAIL; phiên vắt D1->D2 + D3 -> 1 FAIL.
LD=$TMP/localdays
ldrow() { # $1 thư mục, $2 client_ts_ms
  mkdir -p "$1"; printf '{"event_name": "ad_revenue", "sequence_id": 1, "client_ts_ms": %s, "user_id": "u1", "session_id": "s", "event_id": "e-%s", "received_ts_ms": %s, "app_id": "default"}\n' "$2" "$2" "$2" >> "$1/events.jsonl"
}
ldrow "$LD/ok/s1" 1790787540000; ldrow "$LD/ok/s2" 1790787660000; ldrow "$LD/ok/s3" 1790874060000
ldrow "$LD/gap/s1" 1790787540000; ldrow "$LD/gap/s3" 1790874060000; ldrow "$LD/gap/s4" 1790960460000
ldrow "$LD/straddle/s1" 1790787590000; ldrow "$LD/straddle/s1" 1790787610000; ldrow "$LD/straddle/s2" 1790874060000
outo=$(python3 "$MK" --local-days "$LD/ok" 2>&1); rco=$?
outg=$(python3 "$MK" --local-days "$LD/gap" 2>&1); rcg=$?
outs=$(python3 "$MK" --local-days "$LD/straddle" 2>&1); rcs=$?
check "local-days: 23:59/00:01/00:01 -> 3 ngày PASS; D1/D3/D4 -> 2 FAIL; phiên vắt 00:00 +07 tính 1 ngày (ngày của ts đầu) -> 1 FAIL" \
  "$([ "$rco" -eq 0 ] && has "$outo" 'liên tiếp dài nhất: 3 (2026-09-30..2026-10-02)' \
    && [ "$rcg" -eq 1 ] && has "$outg" 'liên tiếp dài nhất: 2' \
    && [ "$rcs" -eq 1 ] && has "$outs" 'liên tiếp dài nhất: 1' && hasnt "$outs" '2026-10-01 (UTC+7'; echo $?)" "ok: exit=$rco
$outo
gap: exit=$rcg
$outg
straddle: exit=$rcs
$outs"

# device_gate.sh --measure: kịch bản đo chạy THAY monkey (pha A, B, C rồi measure_check G11), không một lệnh monkey.
prepare_gate "$TMP/good.txt"
mkdir -p "$GATE_ROOT/DOC/testing/local-sink"; : > "$GATE_ROOT/DOC/testing/local-sink/events.jsonl"
out=$(ADB_CALLS=$TMP/adb-calls FAKE_JSONL=$GATE_ROOT/DOC/testing/local-sink/events.jsonl PATH="$TMP/mbin:$TMP/bin:$PATH" MG_NO_SLEEP=1 \
  ANDROID_SERIAL=stub-serial bash "$GATE_ROOT/tools/device_gate.sh" --variant utility --measure \
  --apk "$GATE_ROOT/apk/app-release.apk" --mapping "$GATE_ROOT/apk/mapping.txt" 2>&1); rc=$?
calls=$(cat "$TMP/adb-calls")
# R-13 I2: reverse --remove không có hiệu lực (reverse --list vẫn còn tcp:8089) -> pha B FAIL, lượt đo không PASS.
prepare_gate "$TMP/good.txt"
mkdir -p "$GATE_ROOT/DOC/testing/local-sink"; : > "$GATE_ROOT/DOC/testing/local-sink/events.jsonl"
outr=$(ADB_CALLS=$TMP/adb-calls FAKE_JSONL=$GATE_ROOT/DOC/testing/local-sink/events.jsonl PATH="$TMP/mbin:$TMP/bin:$PATH" MG_NO_SLEEP=1 \
  FAKE_MG_REVERSE_STUCK=1 ANDROID_SERIAL=stub-serial bash "$GATE_ROOT/tools/device_gate.sh" --variant utility --measure \
  --apk "$GATE_ROOT/apk/app-release.apk" --mapping "$GATE_ROOT/apk/mapping.txt" 2>&1); rcr=$?
check "measure-arg: --measure được nhận, chạy pha A/B/C (reverse --remove kiểm bằng reverse --list, force-stop) + G11, không monkey; reverse còn -> FAIL" \
  "$(hasnt "$out" 'đối số lạ' && has "$out" 'measure: pha A' && has "$out" 'measure: pha B' && has "$out" 'measure: pha C' \
    && has "$out" 'G11 jsonl=' && has "$out" 'RESULT:' && has "$calls" 'reverse --remove tcp:8089' && has "$calls" 'reverse --list' \
    && has "$calls" 'shell am force-stop test.sdk.zd' && hasnt "$calls" 'shell monkey' \
    && [ "$rcr" -eq 1 ] && has "$outr" 'FAIL: reverse --remove' && hasnt "$outr" 'RESULT: PASS'; echo $?)" "exit=$rc
$out
adb calls: $calls
reverse còn: exit=$rcr
$outr"

prepare_gate "$TMP/good.txt"
out=$(FAKE_DOCKER=1 run_gate --measure); rc=$?
check "docker-running (--measure): Docker chạy -> FAIL nêu Docker trước pha đo, RESULT: FAIL, exit 1, 0 lệnh adb" \
  "$(has "$out" 'FAIL: Docker' && has "$out" 'RESULT: FAIL' && [ "$rc" -eq 1 ] && [ ! -s "$TMP/adb-calls" ]; echo $?)" "$out
exit=$rc; adb calls: $(cat "$TMP/adb-calls")"

# ── TF4: mg_find_text cuộn chậm theo cỡ màn hình, tìm cả hai chiều (fling trên Redmi 1080x2400 bỏ qua nút) ──
# find_run <FAKE_VP_START> <FAKE_VP_AT> <FAKE_WM>: chạy mg_screen_init + mg_find_text "Show Interstitial" với adb giả
# có danh sách cuộn 4 khung; in "rc=… swipes=… down=… up=…" (số swipe theo hướng) rồi log kịch bản và lệnh swipe.
find_run() {
  local o rc
  rm -f "$TMP/adb-calls" "$TMP/adb-calls.pos"; : > "$TMP/adb-calls"
  o=$(ADB_CALLS=$TMP/adb-calls FAKE_VP_START=$1 FAKE_VP_AT=$2 FAKE_VP_TEXT="Show Interstitial" FAKE_WM=$3 \
      PATH="$TMP/mbin:$PATH" MG_NO_SLEEP=1 bash -c 'set -u; . "$1"; mg_screen_init; xy=$(mg_find_text "Show Interstitial"); rc=$?; echo "xy=$xy"; exit $rc' _ "$ROOT/tools/lib/measure_gate.sh" 2>&1)
  rc=$?
  FIND_RC=$rc
  FIND_N=$(command grep -c '^shell input swipe' "$TMP/adb-calls")
  FIND_OUT="$o
$(command grep '^shell input swipe' "$TMP/adb-calls")"
}
find_run 0 1 phys
check "find-slow-next-viewport: máy 1080x2400, nút ở khung 2 -> thấy sau ĐÚNG 1 kéo chậm 'swipe 540 1680 540 960 600' (fling cũ bỏ qua khung 2)" \
  "$([ "$FIND_RC" -eq 0 ] && has "$FIND_OUT" 'xy=540 1350' && [ "$FIND_N" -eq 1 ] && has "$FIND_OUT" 'shell input swipe 540 1680 540 960 600'; echo $?)" "rc=$FIND_RC swipes=$FIND_N
$FIND_OUT"
find_run 3 0 phys
check "find-scrolled-past: danh sách đã ở cuối, nút ở khung trên -> thấy nhờ kéo lên 'swipe 540 960 540 1680 600' (6 xuống rồi 3 lên = 9 swipe)" \
  "$([ "$FIND_RC" -eq 0 ] && has "$FIND_OUT" 'xy=540 1350' && [ "$FIND_N" -eq 9 ] && has "$FIND_OUT" 'shell input swipe 540 960 540 1680 600'; echo $?)" "rc=$FIND_RC swipes=$FIND_N
$FIND_OUT"
find_run 0 1 bad
check "find-wm-fallback: wm size không đọc được -> in dòng mặc định 1080x2400 và vẫn tìm được (kéo 540 1680 540 960 600)" \
  "$([ "$FIND_RC" -eq 0 ] && has "$FIND_OUT" 'wm size không đọc được, dùng mặc định 1080x2400' && has "$FIND_OUT" 'xy=540 1350' \
    && has "$FIND_OUT" 'shell input swipe 540 1680 540 960 600'; echo $?)" "rc=$FIND_RC swipes=$FIND_N
$FIND_OUT"
find_run 0 9 phys
nd=$(command grep -c '^shell input swipe 540 1680 540 960 600$' "$TMP/adb-calls")
nu=$(command grep -c '^shell input swipe 540 960 540 1680 600$' "$TMP/adb-calls")
check "find-absent: nút không có -> return 4 sau ĐÚNG 6 kéo xuống + 12 kéo lên (18 swipe)" \
  "$([ "$FIND_RC" -eq 4 ] && [ "$FIND_N" -eq 18 ] && [ "$nd" -eq 6 ] && [ "$nu" -eq 12 ]; echo $?)" "rc=$FIND_RC swipes=$FIND_N down=$nd up=$nu"
find_run 0 1 override
check "find-wm-override: có 'Override size: 720x1600' -> dùng Override (kéo 360 1120 360 640 600), không dùng Physical" \
  "$([ "$FIND_RC" -eq 0 ] && has "$FIND_OUT" 'màn hình 720x1600' && has "$FIND_OUT" 'shell input swipe 360 1120 360 640 600'; echo $?)" "rc=$FIND_RC swipes=$FIND_N
$FIND_OUT"

# ── TF5: về app không -W (API 26 treo), focus thăm ≤ 10 s, cold start có watchdog ────────────────────────────
tf5_run FAKE_API=26 wd_run 8 tf5_lib mg_return_to_app; rc=$?
check "return-no-wait: am start về app (fake treo mãi nếu có -W ... --activity-single-top, như API 26) -> mg_return_to_app quay về < 8 s, lệnh không có -W" \
  "$([ "$rc" -eq 0 ] && ! command grep -q '^shell am start -W' "$TMP/adb-calls" && command grep -q '^shell am start -n test.sdk.zd/com.app.plugin.demo.MainActivity --activity-clear-top --activity-single-top$' "$TMP/adb-calls"; echo $?)" "rc=$rc (124 = treo)
calls: $(cat "$TMP/adb-calls")"
tf5_run FAKE_API=26 wd_run 8 tf5_lib mg_back_in_app "Show Interstitial"; rc=$?
o=$(cat "$TMP/wd.out")
check "return-verify: fake treo -W, nhưng không -W -> mg_back_in_app xác nhận focus MainActivity, return 0" \
  "$([ "$rc" -eq 0 ] && has "$o" "về MainActivity (clear_top_single)"; echo $?)" "rc=$rc
$o"
tf5_run FAKE_FOCUS_AFTER=5 wd_run 20 tf5_lib mg_back_in_app "Show Interstitial"; rc=$?
o=$(cat "$TMP/wd.out")
check "focus-slow: focus về app sau 5 lần thăm (API 26 chậm) -> vẫn valid (thăm ≤ 10 lần)" \
  "$([ "$rc" -eq 0 ] && has "$o" "về MainActivity"; echo $?)" "rc=$rc
$o"
tf5_run FAKE_MG_FOCUS=lost wd_run 20 tf5_lib mg_back_in_app "Show Interstitial"; rc=$?
o=$(cat "$TMP/wd.out"); nf=$(command grep -c '^shell dumpsys window' "$TMP/adb-calls")
check "focus-never: focus không về app -> invalid (focus), return 4, thăm đúng 10 lần (+1 lần in focus)" \
  "$([ "$rc" -eq 4 ] && has "$o" 'invalid (focus)' && [ "$nf" -eq 11 ]; echo $?)" "rc=$rc dumpsys=$nf
$o"
SECONDS=0
tf5_run FAKE_AM_HANG=cold MG_AMSTART_MAX=2 wd_run 15 tf5_lib mg_start; rc=$?
o=$(cat "$TMP/wd.out")
check "cold-start-watchdog: am start -W cold treo -> mg_start kill con adb sau MG_AMSTART_MAX=2 s, in FAIL, return 1 (không treo cổng)" \
  "$([ "$rc" -eq 1 ] && has "$o" 'FAIL: am start -W quá 2s' && [ "$SECONDS" -lt 10 ]; echo $?)" "rc=$rc t=${SECONDS}s
$o"


# ── 2.0.0 Task 0c (D53): chặng rwi skip + gap của --measure (tools/lib/rwi_legs.sh) ─────────────────────────────────
# adb giả `rbin`: nút Show Interstitial (540 1350, hai lần chạm: nạp rồi hiện), Show Rewarded Interstitial (540 1950), chữ "Skip" (540 1100) chỉ có
# khi intro đang mở. Chạm rwi: nếu Interstitial đã chạm >= 2 lần (đã hiện) -> `show refused: policy_min_gap` + hàng sink; ngược lại -> `showRwi call show`
# + intro. Chạm Skip -> `show refused: rwi_intro_skipped` + hàng sink. FAKE_RW_FAULT: noskiprow | nogaprow (thiếu hàng sink) | adshow (rò ad_show của rwi).
mkdir -p "$TMP/rbin"
cat > "$TMP/rbin/adb" <<'EOF'
#!/usr/bin/env bash
echo "$*" >> "$ADB_CALLS"
ST=$FAKE_RW_ST; J=$ST/events.jsonl
taps() { grep -c "^shell input tap $1\$" "$ADB_CALLS"; }
lg() { echo "09-30 05:00:00.000  4242  4242 D AppPlugin: $*" >> "$ST/log"; }
sinkrow() { printf '{"event_name": "%s", "placement": "rwi_default", "reason": "%s"}\n' "$1" "$2" >> "$J"; }
case "$*" in
  "shell getprop ro.build.version.sdk") echo 34;;
  "shell wm size") echo "Physical size: 1080x2400";;
  "shell wm density") echo "Physical density: 420";;
  "shell uiautomator dump"*) ;;
  "shell cat /sdcard/ui.xml")
    sk=""; [ -e "$ST/intro" ] && sk='<node text="Skip" bounds="[0,1050][1080,1150]" />'
    echo "<hierarchy><node text=\"Show Interstitial\" bounds=\"[0,1300][1080,1400]\" /><node text=\"Show Rewarded Interstitial\" bounds=\"[0,1900][1080,2000]\" />$sk</hierarchy>";;
  "shell input tap 540 1350")
    n=$(taps "540 1350"); lg "ads full helper full_default showInterstitial call show"
    [ $((n % 2)) -eq 1 ] && lg "AdsHelper onAdLoaded: pl=full_default opl=full_default format=interstitial platform=admob";;
  "shell input tap 540 1950")
    if [ "$(taps "540 1350")" -ge 2 ]; then lg "ads rwi helper rwi_default show refused: policy_min_gap"; [ "${FAKE_RW_FAULT:-}" = nogaprow ] || sinkrow ad_show_fail policy_min_gap
    else lg "ads rwi helper rwi_default showRwi call show"; touch "$ST/intro"; fi;;
  "shell am start -n test.sdk.zd/com.app.plugin.demo.MainActivity --activity-clear-top --activity-single-top --es drive show_rwi")   # FIX-GATE 4/5: chặng gap drive qua intent (cờ bắt buộc)
    lg "AppPluginDemo: drive=intent action=show_rwi"; if [ "$(taps "540 1350")" -ge 2 ]; then lg "ads rwi helper rwi_default show refused: policy_min_gap"; [ "${FAKE_RW_FAULT:-}" = nogaprow ] || sinkrow ad_show_fail policy_min_gap; fi;;
  "shell input tap 540 1100")
    rm -f "$ST/intro"; lg "ads rwi helper rwi_default show refused: rwi_intro_skipped"
    [ "${FAKE_RW_FAULT:-}" = noskiprow ] || sinkrow ad_show_fail rwi_intro_skipped
    [ "${FAKE_RW_FAULT:-}" != adshow ] || sinkrow ad_show ""
    [ -z "${FAKE_RW_PRE:-}" ] || sinkrow ad_show_fail policy_min_gap;;   # T0e X4: hàng gap có TRƯỚC chặng gap (chặng skip) không được tính
  "shell dumpsys window"*)
    last=$(grep -E '^shell (input tap|am start|input keyevent)' "$ADB_CALLS" | tail -1)
    case "$last" in
      "shell input tap 540 1350") [ $(( $(taps "540 1350") % 2 )) -eq 0 ] && echo "  mCurrentFocus=Window{1 u0 com.google.android.gms.ads.AdActivity}" || echo "  mCurrentFocus=Window{2 u0 test.sdk.zd/com.app.plugin.demo.MainActivity}";;
      *) echo "  mCurrentFocus=Window{2 u0 test.sdk.zd/com.app.plugin.demo.MainActivity}";;
    esac;;
  "logcat -d") cat "$ST/log" 2>/dev/null; echo "09-30 05:00:01.000  4242  4242 D AppPlugin: AdsHelper onAdLoaded: pl=rwi_default opl=rwi_default format=rewarded_interstitial platform=admob";;
esac
exit 0
EOF
chmod +x "$TMP/rbin/adb"
# Chạy rwi_legs_run với adb giả; in đầu ra của chặng rồi "rc=<n>". Đối số = env (vd FAKE_RW_FAULT=noskiprow).
rw_run() {
  rm -rf "$TMP/rw"; mkdir -p "$TMP/rw"; : > "$TMP/rw/events.jsonl"; : > "$TMP/adb-calls"
  (
    export ADB_CALLS="$TMP/adb-calls" FAKE_RW_ST="$TMP/rw" PATH="$TMP/rbin:$PATH" MG_NO_SLEEP=1 ANDROID_SERIAL=stub-serial
    while [ "$#" -gt 0 ]; do export "$1"; shift; done
    bash -c 'set -u; . "$1"; . "$2"; MG_JSONL=$3; mg_screen_init; rwi_legs_run "$(dirname "$3")/x"; echo "rc=$?"' _ "$ROOT/tools/lib/measure_gate.sh" "$ROOT/tools/lib/rwi_legs.sh" "$TMP/rw/events.jsonl" 2>&1
  )
}
r1=$(rw_run); r1calls=$(cat "$TMP/adb-calls")
check "R1 chặng skip: Show Rewarded Interstitial -> chạm 'Skip' (540 1100) -> sink có rwi_intro_skipped 1, ad_show rwi 0 -> PASS (cổng cũ không có chặng)" \
  "$(has "$r1" 'rwi skip: rwi_intro_skipped=1 (cần 1) ad_show rwi=0 (cần 0)' && has "$r1calls" 'shell input tap 540 1100' && hasnt "$r1" 'FAIL: chặng skip'; echo $?)" "$r1
$r1calls"
r2=$(rw_run FAKE_RW_FAULT=noskiprow); r2b=$(rw_run FAKE_RW_FAULT=adshow)
check "R2 chặng skip: thiếu hàng rwi_intro_skipped -> FAIL (=0); có ad_show của rwi trong chặng -> FAIL; không PASS" \
  "$(has "$r2" 'FAIL: chặng skip: rwi_intro_skipped=0 (cần 1)' && has "$r2" 'rc=1' && has "$r2b" 'FAIL: chặng skip: rwi_intro_skipped=1 (cần 1), ad_show rwi_default=1 (cần 0)' && has "$r2b" 'rc=1'; echo $?)" "$r2
$r2b"
check "R3 (RG-GAP, D30) chặng gap: Show Interstitial (hiện) rồi rwi trong min-gap qua intent --es drive show_rwi (0 uiautomator dump sau đó) -> policy_min_gap 1, 0 ad_show -> PASS; Interstitial chạm TRƯỚC" \
  "$(has "$r1" 'rwi gap: policy_min_gap=1 (cần 1) ad_show rwi=0 (cần 0)' && has "$r1" 'rc=0' && dl=$(printf '%s\n' "$r1calls" | grep -n 'es drive show_rwi$' | head -1 | cut -d: -f1) && [ -n "$dl" ] && [ "$(printf '%s\n' "$r1calls" | grep -n '^shell input tap 540 1350$' | head -1 | cut -d: -f1)" -lt "$dl" ] && ! printf '%s\n' "$r1calls" | tail -n +"$dl" | grep -q 'uiautomator dump'; echo $?)" "$r1
$r1calls"
r4=$(rw_run FAKE_RW_FAULT=nogaprow)
check "R4 chặng gap: thiếu hàng policy_min_gap của rwi -> FAIL nêu = 0, rc 1 (bản đúng R3 PASS)" \
  "$(has "$r4" 'FAIL: chặng gap: policy_min_gap=0 (cần 1)' && has "$r4" 'rc=1' && has "$r4" 'rwi skip: rwi_intro_skipped=1'; echo $?)" "$r4"

# ── 2.1.0 Task T0e (M19, M31): X4 phạm vi cửa sổ chặng gap; L1 chặng 1 (lượt rwi đầy đủ của pha A) đủ intro/ad_show/reward/dismiss ──
x4=$(rw_run FAKE_RW_PRE=1)
check "T0e X4 hàng policy_min_gap rwi_default có TRƯỚC chặng gap (1 hàng, ghi ở chặng skip) -> chặng gap vẫn đếm đúng 1, rc 0 (đột biến j0=0 đếm cả file = 2 -> FAIL). Đặc tả: xanh trên mã cũ" \
  "$(has "$x4" 'rwi gap: policy_min_gap=1 (cần 1) ad_show rwi=0 (cần 0)' && has "$x4" 'rc=0'; echo $?)" "$x4"
mkdir -p "$TMP/rwa"; RWA="$TMP/rwa/a1"
l1_fix() {   # $1 = số dòng reward trong log pha A
  { echo "09-30 05:00:00.000  4242  4242 D AppPlugin: ads rwi helper rwi_default showRwi call show"
    [ "$1" -eq 0 ] || echo "09-30 05:00:08.000  4242  4242 D AppPlugin: ads rwi admob30 rwi_default rwi_default OnUserEarnedRewardListener"
    echo "09-30 05:00:10.000  4242  4242 D AppPlugin: ads rwi admob30 rwi_default rwi_default ondissmiss"; } > "$RWA.log"
  printf '{"event_name": "ad_show", "placement": "rwi_default"}\n' > "$RWA.jsonl"
}
l1_fix 1; l1g=$(rw_run MG_A="$RWA"); l1_fix 0; l1b=$(rw_run MG_A="$RWA")
check "T0e L1 chặng 1 rwi (pha A): đủ intro 1 / ad_show 1 / reward 1 / dismiss 1 -> PASS; log thiếu OnUserEarnedRewardListener -> FAIL nêu reward=0, rc 1 (cũ: không kiểm, rc 0)" \
  "$(has "$l1g" 'rwi chặng 1 (pha A): intro=1 ad_show=1 reward=1 dismiss=1' && has "$l1g" 'rc=0' && has "$l1b" 'FAIL: chặng 1 rwi: mốc ≠ 1 (intro=1 ad_show=1 reward=0 dismiss=1)' && has "$l1b" 'rc=1'; echo $?)" "$l1g
$l1b"

# ── 2.1.0 Task 0c: ui_find bền trên One UI (rèm/keyguard giành focus che nút) + intent drive; fixture tools/tests/fixtures/ui/ ──
# adb giả: FAKE_SHADE=1 rèm mở tới khi `cmd statusbar collapse`; =back chỉ KEYCODE_BACK đóng được; =stuck không gì đóng được (keyguard thật
# của Samsung, đo 2026-10-03). Rèm mở: dump = samsung-shade.xml, focus = NotificationShade; đóng: dump = demo-list-<khung>, focus = demo.
# Swipe CHẬM (rèm đóng) = +1 khung (tối đa 3). `am start --es drive` ghi vào adb-calls.
FX=$ROOT/tools/tests/fixtures/ui
mkdir -p "$TMP/ubin"
cat > "$TMP/ubin/adb" <<'EOF'
#!/usr/bin/env bash
echo "$*" >> "$ADB_CALLS"
st=$ADB_CALLS.shade; ps=$ADB_CALLS.pos; [ -f "$st" ] || echo "${FAKE_SHADE:-1}" > "$st"
case "$*" in
  "shell cmd statusbar collapse") [ "$(cat "$st")" = 1 ] && echo 0 > "$st";;
  "shell input keyevent KEYCODE_BACK") [ "$(cat "$st")" = back ] && echo 0 > "$st";;
  "shell input swipe "*) if [ "$(cat "$st")" = 0 ]; then p=$(cat "$ps" 2>/dev/null || echo 1); [ "$p" -lt 3 ] && echo $((p + 1)) > "$ps"; fi;;
  "shell cat /sdcard/ui.xml") if [ "$(cat "$st")" = 0 ]; then cat "$FX/demo-list-$(cat "$ps" 2>/dev/null || echo 1).xml"; else cat "$FX/samsung-shade.xml"; fi;;
  "shell dumpsys window"*) if [ "$(cat "$st")" = 0 ]; then echo "  mCurrentFocus=Window{5e6f u0 test.sdk.zd/com.app.plugin.demo.MainActivity}"; else sed 's/^/  /' "$FX/samsung-shade-focus.txt"; fi;;
  "shell wm size") echo "Physical size: 1080x2340";;
  "logcat -d") ! grep -q 'es drive show_interstitial' "$ADB_CALLS" || echo "I AppPluginDemo: drive=intent action=show_interstitial";;   # FIX-GATE 5: demo log của intent đã tới
esac
exit 0
EOF
chmod +x "$TMP/ubin/adb"
# ui_run <FAKE_SHADE> <UI_DRIVE_INTENT> <lệnh...>: chạy lệnh (sau mg_screen_init) với adb giả; UI_OUT = đầu ra + "rc=…", UI_CALLS = lệnh adb.
ui_run() {
  local sh=$1 dr=$2; shift 2
  rm -f "$TMP/adb-calls" "$TMP/adb-calls.shade" "$TMP/adb-calls.pos"; : > "$TMP/adb-calls"
  UI_OUT=$(ADB_CALLS=$TMP/adb-calls FX=$FX FAKE_SHADE=$sh UI_DRIVE_INTENT=$dr PATH="$TMP/ubin:$PATH" MG_NO_SLEEP=1 \
    bash -c 'set -u; f=$1; shift; . "$f"; mg_screen_init > /dev/null; "$@"; echo "rc=$?"' _ "$ROOT/tools/lib/measure_gate.sh" "$@" 2>&1)
  UI_CALLS=$(cat "$TMP/adb-calls")
}
line_of() { printf '%s\n' "$UI_CALLS" | command grep -n -m1 -- "$1" | cut -d: -f1; }   # số dòng đầu khớp (rỗng = không có)
DRV='shell am start -n test.sdk.zd/com.app.plugin.demo.MainActivity --activity-clear-top --activity-single-top --es drive show_interstitial'

ui_run 1 0 mg_find_text "Privacy options"
c1=$(line_of '^shell cmd statusbar collapse$'); d1=$(line_of '^shell cat /sdcard/ui.xml$')
check "S1 rèm focus (dump Samsung NotificationShade): ui_find gọi 'cmd statusbar collapse' TRƯỚC dump đầu rồi thấy 'Privacy options' (rc 0, không kéo)" \
  "$([ -n "$c1" ] && [ "$c1" -lt "$d1" ] && has "$UI_OUT" 'rc=0' && has "$UI_OUT" '540 468'; echo $?)" "collapse@${c1:-none} dump@${d1:-none}
$UI_OUT
$UI_CALLS"
ui_run 1 0 mg_find_text "Load Rect"
n2=$(printf '%s\n' "$UI_CALLS" | command grep -c '^shell input swipe')
ui_run back 0 mg_find_text "Privacy options"; nb=$(printf '%s\n' "$UI_CALLS" | command grep -c '^shell input keyevent KEYCODE_BACK$')
ui_run 1 0 mg_find_text "Load Rect"
check "S2 chuỗi 3 khung: rèm → thu rèm → chữ chỉ có ở khung 3 ('Load Rect') thấy sau ĐÚNG 2 kéo chậm, rc 0; focus ≠ demo mà collapse không đủ → ĐÚNG 1 KEYCODE_BACK rồi thấy" \
  "$([ "$n2" -eq 2 ] && has "$UI_OUT" 'rc=0' && [ "$nb" -eq 1 ]; echo $?)" "swipes=$n2 back=$nb
$UI_OUT"
ui_run stuck 1 mg_find_text "Show Interstitial"
nd=$(printf '%s\n' "$UI_CALLS" | command grep -c '^shell input swipe 540 1638 540 936 600$'); nu=$(printf '%s\n' "$UI_CALLS" | command grep -c '^shell input swipe 540 936 540 1638 600$')
check "S3 rèm kẹt (focus ≠ demo sau 1 BACK) + --drive-intent: 0 kéo (FIX-GATE 4: kéo trên launcher/rèm mở rèm; cũ 6+12) → 'am start … --es drive show_interstitial' + log 'drive=intent', rc 5 (không chạm, không INVALID)" \
  "$([ "$nd" -eq 0 ] && [ "$nu" -eq 0 ] && has "$UI_CALLS" "$DRV" && has "$UI_OUT" 'drive=intent action=show_interstitial' && has "$UI_OUT" 'rc=5' && hasnt "$UI_OUT" 'INVALID (ui)'; echo $?)" "down=$nd up=$nu
$UI_OUT"
ui_run stuck 0 mg_find_text "Show Interstitial"
check "S4 không thấy, không cờ → 'INVALID (ui)', rc 4, KHÔNG am start drive" \
  "$(has "$UI_OUT" 'INVALID (ui)' && has "$UI_OUT" 'rc=4' && ! printf '%s\n' "$UI_CALLS" | command grep -q 'es drive'; echo $?)" "$UI_OUT"
ui_run 1 0 bash -c 'set -u; . "$1"; iap_tap_label "Privacy options"; echo "iap=$?"' _ "$ROOT/tools/lib/iap_gate.sh"
i5a=$UI_OUT; c5=$(line_of '^shell cmd statusbar collapse$'); t5=$(printf '%s\n' "$UI_CALLS" | command grep -c '^shell input tap ')
ui_run stuck 1 bash -c 'set -u; . "$1"; iap_tap_label "Show Interstitial"; echo "iap=$?"' _ "$ROOT/tools/lib/iap_gate.sh"
t5b=$(printf '%s\n' "$UI_CALLS" | command grep -c '^shell input tap '); i5b=$UI_OUT; d5b=$(printf '%s\n' "$UI_CALLS" | command grep -c 'es drive show_interstitial')
ui_run stuck 0 bash -c 'set -u; . "$1"; iap_tap_label "Show Interstitial"; echo "iap=$?"' _ "$ROOT/tools/lib/iap_gate.sh"
check "S5 iap_gate dùng cùng ui_find: iap_tap_label thu rèm rồi chạm (1 tap); stuck + intent → am start drive, 0 tap, iap=0; stuck không cờ → iap=4 (INVALID (ui))" \
  "$([ -n "$c5" ] && [ "$t5" -eq 1 ] && has "$i5a" 'iap=0' && [ "$t5b" -eq 0 ] && [ "$d5b" -eq 1 ] && has "$i5b" 'iap=0' && has "$UI_OUT" 'iap=4' && has "$UI_OUT" 'INVALID (ui)'; echo $?)" "$i5a
tap=$t5 tap(stuck+intent)=$t5b drive=$d5b
$i5b
$UI_OUT"

# ── 2.4.0 FIX-GATE (D25): chạm trong vùng hiển thị trên thanh điều hướng 3 nút (Samsung 1080x2340, tappableElement top=2205, dump thật) ──
# nav_run <nav 3b|gesture|none> <ui straddle|sliver|none>: mg_screen_init + mg_find_text "Show Interstitial"; in "xy=<x y> rc=<n> swipes=<n>".
mkdir -p "$TMP/nbin"; cat > "$TMP/nbin/adb" <<'EOF'
#!/usr/bin/env bash
echo "$*" >> "$ADB_CALLS"; sw=$(grep -c '^shell input swipe' "$ADB_CALLS")
case "$*" in
  "shell wm size") echo "Physical size: 1080x2340";;
  "shell dumpsys activity top"*) case $FAKE_NAV in 3b) cat "$NG_FX";; gesture) echo "  InsetsSource id=1 type=tappableElement frame=[0,0][0,0] visible=true flags= sideHint=NONE";; esac;;
  "shell cat /sdcard/ui.xml") b='[32,1500][1048,1635]'
    if [ "$sw" -eq 0 ]; then case $FAKE_UI in straddle) b='[32,2100][1048,2235]';; sliver) b='[32,2190][1048,2325]';; esac; fi
    echo "<hierarchy><node text=\"Show Interstitial\" bounds=\"$b\" /></hierarchy>";;
  "shell dumpsys window"*) echo "  mCurrentFocus=Window{5e6f u0 test.sdk.zd/com.app.plugin.demo.MainActivity}";;
esac
exit 0
EOF
chmod +x "$TMP/nbin/adb"
nav_run() { : > "$TMP/adb-calls"; ADB_CALLS=$TMP/adb-calls FAKE_NAV=$1 FAKE_UI=$2 NG_FX=$ROOT/tools/tests/fixtures/dumps/device-gate-R5CX10P9EKZ-20261006-ng-banners-top1.txt PATH="$TMP/nbin:$PATH" MG_NO_SLEEP=1 \
  bash -c 'set -u; . "$1"; mg_screen_init > /dev/null; xy=$(mg_find_text "Show Interstitial"); rc=$?; echo "xy=$xy rc=$rc swipes=$(grep -c "^shell input swipe" "$ADB_CALLS")"' _ "$ROOT/tools/lib/measure_gate.sh" 2>&1 | tail -1; }
n1=$(nav_run 3b straddle); n2=$(nav_run 3b sliver); n3=$(nav_run gesture sliver); n4=$(nav_run gesture straddle); n5=$(nav_run none sliver)
check "NAV1 3 nút (top=2205): nút cắt ngang thanh [2100,2235] chạm tâm phần hiển thị y=2152 (< 2205), rc 0, 0 kéo (cũ: y=2167 trong vùng thanh); nút chỉ hở sợi [2190,2325] (15 px) KHÔNG được chọn → kéo 1 lần rồi chạm nút ở y=1567 (cũ: chạm sợi y=2257 = nút HOME)" \
  "$([ "$n1" = 'xy=540 2152 rc=0 swipes=0' ] && [ "$n2" = 'xy=540 1567 rc=0 swipes=1' ]; echo $?)" "straddle: $n1
sliver: $n2"
check "NAV2 điều hướng cử chỉ (tappableElement rỗng) hoặc không đọc được inset → hành vi cũ không đổi: sợi chạm tâm y=2257, nút cắt ngang y=2167, 0 kéo" \
  "$([ "$n3" = 'xy=540 2257 rc=0 swipes=0' ] && [ "$n4" = 'xy=540 2167 rc=0 swipes=0' ] && [ "$n5" = "$n3" ]; echo $?)" "gesture sliver: $n3
gesture straddle: $n4
no inset sliver: $n5"

dg_finish
