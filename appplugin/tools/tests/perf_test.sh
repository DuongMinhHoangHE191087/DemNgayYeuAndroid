#!/usr/bin/env bash
# 2.1.0 Task 0f: test không cần máy cho tools/perf_gate.sh, tools/lib/perf_parse.sh, tools/perf_report.py (15 kiểm R1–R15).
# Fixture: tools/tests/fixtures/perf/ (2 log gate thật 2.0.0 Redmi/Samsung, sink Redmi thu nhỏ, meminfo chụp thật ở smoke 5570 API 37).
# Chạy: bash tools/tests/perf_test.sh (exit 0 = xanh).
. "$(dirname "$0")/lib/dg_harness.sh" || exit 99
FX=$ROOT/tools/tests/fixtures/perf
TH=$ROOT/tools/perf-thresholds.json
. "$ROOT/tools/lib/perf_parse.sh"
rep() { python3 "$ROOT/tools/perf_report.py" "$@" 2>&1; }

# JSONL lượt từ các dòng `metrics cold start` của log gate (cùng regex với device_gate_r8.sh).
runs_from_gate() { # $1 = log gate
  local k=0 line
  while IFS= read -r line; do k=$((k + 1)); perf_run_json "$k" "" "$line" 0; done < <(grep --text '^metrics cold start' "$1")
}
run_line() { printf '{"run":%s,"total_ms":900,"wait_ms":null,"oncreate_ms":%s,"initplugin":{"total":10},"prefs_waited_ms":0,"prefs_timeouts":0,"by_sdk":0}\n' "$1" "$2"; }
# Sự kiện sink giả: ev <tên> <ts> <session> <raw JSON>
EN=0
ev() { EN=$((EN + 1)); printf '{"event_name":"%s","client_ts_ms":%s,"session_id":"%s","event_id":"e%s","raw_event_data":%s}\n' "$1" "$2" "$3" "$EN" "$4"; }

runs_from_gate "$FX/gate-redmi.log" > "$TMP/redmi.jsonl"; runs_from_gate "$FX/gate-samsung.log" > "$TMP/sam.jsonl"
{ cat "$TMP/redmi.jsonl"; run_line 3 200; } > "$TMP/redmi3.jsonl"
o1=$(rep --jsonl "$TMP/redmi.jsonl" --thresholds "$TH" --device redmi); o1s=$(rep --jsonl "$TMP/sam.jsonl" --thresholds "$TH" --device samsung)
o1b=$(rep --jsonl "$TMP/redmi3.jsonl" --thresholds "$TH" --device redmi)
check "R1 fixture Redmi/Samsung: oncreate_ms med 25,5 / initplugin 24,5 (n=2, khớp perf-baseline); thêm lượt ngoại lai 200 thì trung vị 29 (không phải trung bình)" \
  "$(has "$o1" '| oncreate_ms | 2 | 25.5 |' && has "$o1" '| initplugin_total_ms | 2 | 24.5 |' && has "$o1" '| total_ms | 2 | 454 |' \
     && has "$o1s" '| oncreate_ms | 2 | 19.5 |' && has "$o1s" '| initplugin_total_ms | 2 | 19 |' && has "$o1b" '| oncreate_ms | 3 | 29 |'; echo $?)" "$o1
$o1s
$o1b"

printf '%s\n' "$(run_line 1 20)" '{"run":2,"total_ms":9' > "$TMP/bad.jsonl"
{ ev session_start 1000 s '{}'; ev ad_request 1100 s '{"request_id":"a"}'; } > "$TMP/good-sink.jsonl"
{ cat "$TMP/good-sink.jsonl"; echo '{"event_name":"ad_fill","client_ts_ms":1200,"session_id":"s","event_id":"x","raw_event_data":"{khong"}'; } > "$TMP/bad-sink.jsonl"
o2=$(rep --jsonl "$TMP/bad.jsonl" --thresholds "$TH" --device redmi); rc2=$?
o2b=$(rep --jsonl "$TMP/redmi.jsonl" --sink-dir "$TMP/bad-sink.jsonl" --thresholds "$TH" --device redmi); rc2b=$?
check "R2 JSONL hỏng (dòng cắt cụt; raw_event_data của sink hỏng) -> FAIL rc 1, không bỏ qua" \
  "$([ "$rc2" -eq 1 ] && has "$o2" 'JSON hỏng' && has "$o2" 'RESULT: FAIL' && [ "$rc2b" -eq 1 ] && has "$o2b" 'raw_event_data hỏng'; echo $?)" "$o2
$o2b"

mkdir -p "$TMP/th"
printf '{"schema_version":1,"devices":{"x":{"oncreate_ms":{"med":10,"kind":"%s"}}}}\n' hard > "$TMP/th/hard.json"
printf '{"schema_version":1,"devices":{"x":{"oncreate_ms":{"med":10,"kind":"%s"}}}}\n' info > "$TMP/th/info.json"
o3=$(rep --jsonl "$TMP/redmi.jsonl" --thresholds "$TMP/th/hard.json" --device x); rc3=$?
o3i=$(rep --jsonl "$TMP/redmi.jsonl" --thresholds "$TMP/th/info.json" --device x); rc3i=$?
check "R3 vượt hard -> FAIL rc 1; vượt info -> chỉ in (rc 0, RESULT PASS)" \
  "$([ "$rc3" -eq 1 ] && has "$o3" 'FAIL (med)' && has "$o3" 'RESULT: FAIL' && [ "$rc3i" -eq 0 ] && has "$o3i" 'info: vượt (med)' && has "$o3i" 'RESULT: PASS'; echo $?)" "$o3
$o3i"

{ for v in 10 20 30 40 50; do run_line "$v" "$v"; done; } > "$TMP/p90.jsonl"
{ run_line 1 1; run_line 2 2; } > "$TMP/p90b.jsonl"
o4=$(rep --jsonl "$TMP/p90.jsonl" --thresholds "$TH" --device none); o4b=$(rep --jsonl "$TMP/p90b.jsonl" --thresholds "$TH" --device none)
check "R4 p90 nội suy tuyến tính: [10..50] -> 46; [1,2] -> 1,9" \
  "$(has "$o4" '| oncreate_ms | 5 | 30 | 46 | 50 |' && has "$o4b" '| oncreate_ms | 2 | 1.5 | 1.9 | 2 |'; echo $?)" "$o4
$o4b"

{ ev session_start 1000 s '{}'; ev ad_request 1100 s '{"request_id":"r1","ad_format":"opened"}'; ev ad_request 1110 s '{"request_id":"r2","ad_format":"opened"}'
  ev ad_fill 1200 s '{"request_id":"r1","latency_ms":100}'; ev ad_fill 1250 s '{"request_id":"rX","latency_ms":999}'
  ev ad_no_fill 1300 s '{"request_id":"r2","latency_ms":190}'; ev ad_fill 1350 s '{"request_id":"r1","latency_ms":5}'; } > "$TMP/pair.jsonl"
o5=$(rep --jsonl "$TMP/redmi.jsonl" --sink-dir "$TMP/pair.jsonl" --thresholds "$TH" --device none)
o5f=$(rep --jsonl "$TMP/redmi.jsonl" --sink-dir "$FX/sink-redmi.jsonl" --thresholds "$TH" --device none)
check "R5 ghép request_id: fill không có request bị bỏ + đếm (orphan 1), fill trùng request bị bỏ; sink thật thu nhỏ: 19 request, 18 fill" \
  "$(has "$o5" 'orphan_responses=1' && has "$o5" 'fills=1, no_fills=1' && has "$o5" '| fill_ms.opened | 1 | 100 |' && has "$o5f" 'requests=19, fills=18, no_fills=0, orphan_responses=0'; echo $?)" "$o5
$o5f"

{ ev ad_request 10 s '{"request_id":"a","ad_format":"x"}'; ev ad_request 11 s '{"request_id":"b","ad_format":"x"}'; ev ad_request 12 s '{"request_id":"c","ad_format":"x"}'
  ev ad_fill 20 s '{"request_id":"a","latency_ms":10}'; ev ad_no_fill 21 s '{"request_id":"b","latency_ms":10}'; } > "$TMP/rate.jsonl"
o6=$(rep --jsonl "$TMP/redmi.jsonl" --sink-dir "$TMP/rate.jsonl" --thresholds "$TH" --device none)
check "R6 fill rate hai mẫu số: fill/(fill+no_fill) = 0,5 (mẫu số 2); fill/request = 0,33 (mẫu số 3); sink thật: 18/19 = 0,95" \
  "$(has "$o6" '| fill_rate | 2 | 0.5 |' && has "$o6" '| fill_per_request | 3 | 0.33 |' && has "$o5f" '| fill_per_request | 19 | 0.95 |' && has "$o5f" '| fill_rate | 18 | 1 |'; echo $?)" "$o6
$o5f"

{ ev ad_show_attempt 100 s1 '{"placement":"A","ready":0}'; ev ad_show_attempt 200 s1 '{"placement":"A","ready":1}'; ev ad_show 250 s2 '{"placement":"A","ad_format":"interstitial"}'
  ev ad_show 290 s1 '{"placement":"A","ad_format":"interstitial"}'; ev ad_show 300 s1 '{"placement":"B","ad_format":"interstitial"}'; } > "$TMP/show.jsonl"
o7=$(rep --jsonl "$TMP/redmi.jsonl" --sink-dir "$TMP/show.jsonl" --thresholds "$TH" --device none)
o7f=$(rep --jsonl "$TMP/redmi.jsonl" --sink-dir "$FX/sink-redmi.jsonl" --thresholds "$TH" --device none)
check "R7 show latency: attempt ready=1 -> ad_show cùng session + placement (khác session/placement không ghép): 90 ms; sink thật interstitial n=2 med 139,5" \
  "$(has "$o7" '| show_ms.interstitial | 1 | 90 |' && has "$o7f" '| show_ms.interstitial | 2 | 139.5 |'; echo $?)" "$o7
$o7f"

{ ev session_start 1000 X '{}'; ev ad_request 4000 X '{"request_id":"a"}'; ev consent_choice 5000 X '{}'; ev ad_request 7500 X '{"request_id":"b"}'
  ev session_start 100 Y '{}'; ev ad_request 900 Y '{"request_id":"c"}'; } > "$TMP/ttfr.jsonl"
o8=$(rep --jsonl "$TMP/redmi.jsonl" --sink-dir "$TMP/ttfr.jsonl" --thresholds "$TH" --device none)
check "R8 time-to-first-request từ consent_choice (X: 7500-5000=2500, bỏ request trước consent), không có consent thì từ session_start (Y: 800)" \
  "$(has "$o8" '| ttfr_ms | 2 | 1650 |'; echo $?)" "$o8"

# R9: perf_gate với adb giả; logcat không có dòng onCreate -> INVALID (rc 4), không PASS.
GR=$TMP/gate; mkdir -p "$GR/tools/lib" "$TMP/bin"
cp "$ROOT/tools/perf_gate.sh" "$GR/tools/"; cp "$ROOT/tools/tests/lib/precheck_stub.sh" "$GR/tools/lib/precheck_env.sh"; cp "$ROOT/tools/lib/"{adb_b,redact,perf_parse,measure_gate,measure_tap,ui_find,device_gate_r8,quiet_host}.sh "$GR/tools/lib/"
printf '#!/usr/bin/env bash\necho "sink up (stub)"\n' > "$GR/tools/local_sink.sh"
printf '#!/usr/bin/env bash\n[ -z "${FAKE_DOCKER:-}" ] || { echo 999; exit 0; }\nexit 1\n' > "$TMP/bin/pgrep"
cat > "$TMP/bin/adb" <<'FAKE'
#!/usr/bin/env bash
case "$*" in
  *"getprop ro.build.version.sdk"*) echo 34;;
  *"getprop ro.product.model"*) echo FakeModel;;
  install*) echo Success;;
  *"am start -W"*) printf 'TotalTime: 500\r\nWaitTime: 510\r\n';;
  *pidof*) echo 4242;;
  "logcat -d"*) echo "09-24 19:00:00.001  4242  4242 I other: nothing to see";;
esac
exit 0
FAKE
chmod +x "$TMP/bin/pgrep" "$TMP/bin/adb"; : > "$TMP/fake.apk"; : > "$TMP/fake-map.txt"; mkdir -p "$TMP/sinkdir"
o9=$(PATH="$TMP/bin:$PATH" PERF_WAIT_MAX=1 PERF_SETTLE=0 bash "$GR/tools/perf_gate.sh" --serial s --apk "$TMP/fake.apk" --mapping "$TMP/fake-map.txt" \
  --sink-port 8111 --sink-dir "$TMP/sinkdir" --runs 5 --out "$TMP/out/perf.jsonl" 2>&1); rc9=$?
o9d=$(FAKE_DOCKER=1 PATH="$TMP/bin:$PATH" bash "$GR/tools/perf_gate.sh" --serial s --apk "$TMP/fake.apk" --mapping "$TMP/fake-map.txt" --sink-port 8111 --sink-dir "$TMP/sinkdir" --runs 5 --out "$TMP/o3.jsonl" 2>&1)
check "R9 perf_gate: lượt không có dòng AppPluginBase.onCreate -> INVALID rc 4 (không PASS, không ghi lượt giả); Docker chạy -> FAIL trước adb (precheck docker=1); --runs 3 -> FAIL rc 1; perf_run_json thiếu onCreate return 1" \
  "$([ "$rc9" -eq 4 ] && has "$o9" 'RESULT: INVALID (run)' && has "$o9" 'không có dòng AppPluginBase.onCreate' && hasnt "$o9" 'RESULT: PASS' \
     && [ "$(grep -c '"run"' "$TMP/out/perf.jsonl")" -eq 0 ] && ! perf_run_json 1 "TotalTime: 5" "no oncreate here" 0 > /dev/null \
     && has "$o9d" 'precheck docker=1 sink=up' && has "$o9d" 'Docker đang chạy' && has "$o9" 'precheck docker=0 sink=up' \
     && ! bash "$ROOT/tools/perf_gate.sh" --serial s --apk "$TMP/fake.apk" --mapping "$TMP/fake-map.txt" --sink-port 8111 --sink-dir "$TMP/sinkdir" --runs 3 --out "$TMP/o2.jsonl" > /dev/null 2>&1; echo $?)" "rc=$rc9
$o9"

# R10: meminfo thật (Samsung API 36) -> pss_kb đúng.
M10=$(perf_meminfo_kv "$FX/meminfo-api36.txt" 2>&1); rc10=$?
check "R10 meminfo API 36 (chụp thật trên Samsung R5CX10P9EKZ; thêm mẫu TỔNG HỢP dạng API 26-28 'TOTAL <n>'): TOTAL PSS / Java Heap / Native Heap đúng; không có TOTAL PSS -> return 1" \
  "$([ "$rc10" -eq 0 ] && has "$M10" 'pss_kb=145513 java_kb=20576 native_kb=48352' \
     && printf '        TOTAL   5000   4000\n      Java Heap:  700\n    Native Heap:  800\n' > "$TMP/old.txt" && has "$(perf_meminfo_kv "$TMP/old.txt")" 'pss_kb=5000 java_kb=700 native_kb=800' && ! perf_meminfo_kv "$FX/gate-redmi.log" > /dev/null; echo $?)" "$M10"
# R11: lượt first-run đúng dạng perf_gate.sh phát (không có khoá "run") phải được đọc thành first_*.
{ run_line 1 20; printf '{"first_run":true,"total_ms":700,"wait_ms":null,"oncreate_ms":55,"initplugin":{"total":40},"prefs_waited_ms":0,"by_sdk":2}\n'; } > "$TMP/first.jsonl"
o11=$(rep --jsonl "$TMP/first.jsonl" --thresholds "$TH" --device redmi)
check "R11 first-run dạng perf_gate (không khoá run): có hàng first_oncreate_ms / first_by_sdk = 2; ngưỡng redmi first_by_sdk max 1 (D16) -> FAIL (không phải 'thiếu dữ liệu')" \
  "$(has "$o11" '| first_oncreate_ms | 1 | 55 |' && has "$o11" '| first_by_sdk | 1 | 2 |' && has "$o11" 'FAIL: first_by_sdk' && hasnt "$o11" 'thiếu dữ liệu'; echo $?)" "$o11"

# R12/R13 (2.2.0 PERF2): chặng banner. Fixture THẬT = perf_gate.sh --runs 5 trên emulator 5570 (sha + lệnh ở đầu real-banner-gate.log).
BG=$FX/real-banner-gate.log; BS=$FX/real-banner-sink.jsonl
o12=$(rep --jsonl "$TMP/redmi.jsonl" --sink-dir "$BS" --thresholds "$TH" --device redmi)
check "R12 perf_gate chạm 'Show Banner Only' (mã + log gate thật); sink thật có ad_request -> ad_fill banner ghép request_id" \
  "$(grep -q 'mg_tap_verified "Show Banner Only"' "$ROOT/tools/perf_gate.sh" && grep -q "tap 'Show Banner Only'" "$BG" && has "$o12" '| fill_ms.banner |'; echo $?)" "$o12"
kinds=$(python3 -c "import json;d=json.load(open('$TH'))['devices'];print(' '.join('%s=%s' % (k, d[k].get('fill_ms.banner',{}).get('kind')) for k in ('redmi','emu37','emu33','emu28','emu26')))")
check "R13 perf_report in fill_ms.banner kind info (redmi; emu37/33/28/26 cũng info, chưa hard trước PERF2-AB)" \
  "$(has "$o12" '| info |' && [ "$kinds" = 'redmi=info emu37=info emu33=info emu28=info emu26=info' ]; echo $?)" "$o12
$kinds"
o14=$(rep --jsonl "$TMP/redmi.jsonl" --sink-dir "$TMP/rate.jsonl" --thresholds "$TH" --device redmi)
check "R14 luật info không có dữ liệu (fill_ms.banner, sink không có banner) vẫn in hàng 'info (không có dữ liệu)'" \
  "$(has "$o14" '| fill_ms.banner | 0 |' && has "$o14" 'info (không có dữ liệu)'; echo $?)" "$o14"
{ for i in 1 2 3 4; do ev ad_request "$i" s "{\"request_id\":\"b$i\",\"ad_format\":\"banner\"}"; ev ad_fill "$i" s "{\"request_id\":\"b$i\",\"latency_ms\":100}"; done; } > "$TMP/ban4.jsonl"
for mn in 10 3; do printf '{"schema_version":1,"devices":{"x":{"fill_ms.banner":{"med":99999,"kind":"hard","min_n":%s}}}}\n' "$mn" > "$TMP/th/mn$mn.json"; done
o15=$(rep --jsonl "$TMP/redmi.jsonl" --sink-dir "$TMP/ban4.jsonl" --thresholds "$TMP/th/mn10.json" --device x); rc15=$?
o15b=$(rep --jsonl "$TMP/redmi.jsonl" --sink-dir "$TMP/ban4.jsonl" --thresholds "$TMP/th/mn3.json" --device x); rc15b=$?
check "R15 hard min_n 10 với n=4 -> FAIL (n=4 < min_n=10) rc 1; min_n 3 với n=4 -> OK rc 0" \
  "$([ "$rc15" -eq 1 ] && has "$o15" 'FAIL (n=4 < min_n=10)' && [ "$rc15b" -eq 0 ] && has "$o15b" '| OK |'; echo $?)" "$o15
$o15b"

# PF1 (2.4.0 CARRY-T #5): load_thresholds kiểm min_n (số nguyên >= 1): 0 / "x" / true -> FAIL rc 1 nêu min_n; 3 -> OK (R15 giữ).
pf=""; pfrc=0
for mnv in 0 '"x"' true; do
  printf '{"schema_version":1,"devices":{"x":{"fill_ms.banner":{"med":99999,"kind":"hard","min_n":%s}}}}\n' "$mnv" > "$TMP/th/pf.json"
  o=$(rep --jsonl "$TMP/redmi.jsonl" --sink-dir "$TMP/ban4.jsonl" --thresholds "$TMP/th/pf.json" --device x); rc=$?
  { [ "$rc" -eq 1 ] && has "$o" 'min_n phải là số nguyên >= 1' && has "$o" 'RESULT: FAIL'; } || pfrc=1; pf="$pf$o"
done
check "PF1 min_n 0 / \"x\" / true trong ngưỡng -> FAIL rc 1 'min_n phải là số nguyên >= 1' (cũ: bỏ qua im lặng, 0/\"x\" không có lỗi)" "$pfrc" "$pf"

dg_finish
