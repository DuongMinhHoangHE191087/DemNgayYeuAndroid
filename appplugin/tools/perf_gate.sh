#!/usr/bin/env bash
# 2.1.0 Task 0f (PERF, plan §D): đo hiệu năng trên MỘT máy, APK đóng băng. KHÔNG phán ngưỡng (tools/perf_report.py làm việc đó):
# RESULT chỉ PASS | INVALID (<lý do>) (exit 0 | 4); FAIL (exit 1) khi cấu hình hỏng hoặc có FATAL/ANR/native trong cửa sổ đo.
# Dùng: tools/perf_gate.sh --serial <s> --apk <apk> --mapping <mapping.txt> --sink-port <p> --sink-dir <d> --runs N(>=5) --out <perf.jsonl>
# Đầu ra: <out> (JSONL), <out%.*>.log (log gate, dòng 1 có sha APK + api= + model), <out%.*>-app/ (log app mỗi lượt).
# JSONL: dòng 1 {"header":true,...}; mỗi lượt {"run":k,"total_ms","wait_ms","oncreate_ms","initplugin":{manager,meta,helper_gate,logevent,total},
#   "prefs_waited_ms","prefs_timeouts","by_sdk"} (am force-stop -> am start -W không force_eea; chờ dòng `AppPluginBase.onCreate main-thread ms=`
#   <= PERF_WAIT_MAX s, không có = INVALID); sau lượt cuối: meminfo lúc settle (10 s), một vòng show (Interstitial, Rewarded, rwi — hàm
#   mg_show_full của measure_gate.sh), meminfo sau show, chạm "Show Banner Only" + chờ 20 s (fill_ms.banner, 2.2.0 PERF2); rồi lượt first-run (`pm clear` trước) {"first_run":true,...}.
#   Một lượt mồi KHÔNG tính (sau cài mới) để N lượt cold cùng điều kiện; first-run là lượt riêng.
# Tiền kiểm `precheck docker=<n> sink=<up|down>` như device_gate.sh (Docker ≠ 0 hoặc sink down -> FAIL trước mọi lệnh adb); lệnh tới máy qua adb_b; bash 3.2.
set -u
SERIAL=""; APK=""; MAP=""; SINK_PORT=""; SINK_DIR=""; RUNS=""; OUT=""
bad() { echo "FAIL: $1"; echo "RESULT: FAIL"; exit 1; }
while [ $# -gt 0 ]; do
  case $1 in
    --serial|--apk|--mapping|--sink-port|--sink-dir|--runs|--out)
      [ $# -ge 2 ] || bad "$1 cần một giá trị"
      case $1 in --serial) SERIAL=$2;; --apk) APK=$2;; --mapping) MAP=$2;; --sink-port) SINK_PORT=$2;;
        --sink-dir) SINK_DIR=$2;; --runs) RUNS=$2;; --out) OUT=$2;; esac; shift 2;;
    *) bad "đối số lạ '$1' (chỉ nhận --serial --apk --mapping --sink-port --sink-dir --runs --out)";;
  esac
done
for v in SERIAL APK MAP SINK_PORT SINK_DIR RUNS OUT; do [ -n "${!v}" ] || bad "thiếu --$(echo "$v" | tr 'A-Z_' 'a-z-')"; done
case $RUNS in *[!0-9]*) bad "--runs phải là số nguyên (nhận '$RUNS')";; esac
[ "$RUNS" -ge 5 ] || bad "--runs phải >= 5 (nhận $RUNS): n < 5 thì trung vị/p90 không đáng tin"
case $SINK_PORT in *[!0-9]*) bad "--sink-port phải là số (nhận '$SINK_PORT')";; esac
[ -f "$APK" ] || bad "--apk trỏ file không có: $APK"
[ -f "$MAP" ] || bad "--mapping trỏ file không có: $MAP"
for v in APK MAP SINK_DIR OUT; do case ${!v} in /*) ;; *) eval "$v=\$PWD/\${$v}";; esac; done
export ANDROID_SERIAL=$SERIAL
export PATH="$PATH:${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools"
cd "$(dirname "$0")/.." || exit 1
. tools/lib/adb_b.sh && . tools/lib/precheck_env.sh && . tools/lib/redact.sh && . tools/lib/perf_parse.sh && . tools/lib/measure_gate.sh || bad "không nạp được tools/lib"
mkdir -p "$(dirname "$OUT")"
LOG=${OUT%.*}.log; ADIR=${OUT%.*}-app; mkdir -p "$ADIR"; : > "$OUT"; : > "$LOG"
export ADB_B_LOG="$LOG"
fail=0
. tools/lib/device_gate_r8.sh || bad "không nạp được tools/lib/device_gate_r8.sh"   # count_strict (cần LOG, fail)
PKG=$MG_PKG; ACT=$MG_ACT
WAIT_MAX=${PERF_WAIT_MAX:-30}; SETTLE=${PERF_SETTLE:-8}; SINK_JSONL=$SINK_DIR/events.jsonl
MG_LOG=$LOG; MG_JSONL=$SINK_JSONL
say() { echo "$*" | tee -a "$LOG"; }
SAMPLER_PID=""; REVERSE_ON=""
cleanup() {
  [ -z "$SAMPLER_PID" ] || { kill "$SAMPLER_PID" 2>/dev/null; wait "$SAMPLER_PID" 2>/dev/null; }
  [ -z "$REVERSE_ON" ] || adb_b 60 reverse --remove tcp:8089 > /dev/null 2>&1
}
trap cleanup EXIT
. tools/lib/quiet_host.sh 2>/dev/null && { quiet_host_sampler "$LOG.host" > /dev/null 2>&1 & SAMPLER_PID=$!; }
invalid() { say "INVALID: $1"; say "RESULT: INVALID ($2)"; exit 4; }
emit() { printf '%s\n' "$1" >> "$OUT"; }

# Tiền kiểm như tools/device_gate.sh, TRƯỚC mọi lệnh adb (D12): Docker chạy làm số đo nhiễu; không có sink thì telemetry mất.
docker_n=$(pgrep -f '^/Applications/Docker.app/Contents/' | wc -l | tr -d ' '); docker_n=${docker_n:-0}
sink_state=up; sink_out=$(bash tools/local_sink.sh status --port "$SINK_PORT" 2>&1) || sink_state=down
say "precheck docker=$docker_n sink=$sink_state"
[ "$docker_n" -eq 0 ] || { say "FAIL: Docker đang chạy (pgrep -f ^/Applications/Docker.app/Contents/ = $docker_n): dừng Docker Desktop trước cổng thiết bị"; say "RESULT: FAIL"; exit 1; }
[ "$sink_state" = up ] || { say "FAIL: sink cục bộ không chạy (tools/local_sink.sh start --port $SINK_PORT --dir <d>): $sink_out"; say "RESULT: FAIL"; exit 1; }
pre=$(precheck_env "$SERIAL"); pre_rc=$?; say "$pre"   # 2.2.0 Task 0c: Wi-Fi/keyguard/pin + host, trước mọi lệnh cài
[ "$pre_rc" -eq 0 ] || invalid "môi trường thiếu ($pre)" precheck
API=$(adb_b 30 shell getprop ro.build.version.sdk 2>/dev/null | tr -d '\r\n'); case $API in ''|*[!0-9]*) invalid "getprop ro.build.version.sdk lỗi ('$API')" api;; esac
MODEL=$(adb_b 30 shell getprop ro.product.model 2>/dev/null | tr -d '\r\n')
APK_SHA=$(shasum -a 256 "$APK" | cut -d' ' -f1); MAP_SHA=$(shasum -a 256 "$MAP" | cut -d' ' -f1)
say "perf log: $LOG; apk=$APK sha256=$APK_SHA mapping sha256=$MAP_SHA api=$API model=$MODEL serial=$SERIAL runs=$RUNS"
emit "{\"header\":true,\"apk_sha256\":\"$APK_SHA\",\"api\":$API,\"model\":\"$MODEL\",\"serial\":\"$SERIAL\",\"runs\":$RUNS,\"host_load1\":\"$(uptime | sed 's/.*load averages*: *//;s/[ ,].*//')\"}"
adb_b 60 uninstall "$PKG" > /dev/null 2>&1
io=$(adb_b 120 install -r "$APK" 2>&1); say "$io"; echo "$io" | grep -q '^Success' || bad "cài APK lỗi"
adb_b 60 reverse tcp:8089 "tcp:$SINK_PORT" > /dev/null 2>&1 && REVERSE_ON=1 || bad "adb reverse tcp:8089 tcp:$SINK_PORT lỗi"
adb_b 60 logcat -G 16M > /dev/null 2>&1 || say "warn: adb logcat -G 16M không áp được"
adb_b 60 logcat -c; adb_b 60 logcat -c -b crash; adb_b 60 logcat -c -b events

# $1 = nhãn lượt (số hoặc first_run). Đặt RUN_JSON; return 4 + INV_WHY khi lượt INVALID.
perf_one() {
  local lbl=$1 am pid w=0 alog=$ADIR/run-$1.log by l0
  adb_b 60 shell am force-stop "$PKG"; adb_b 60 logcat -c
  am=$(adb_b 60 shell am start -W -n "$PKG/$ACT" 2>&1 | tr -d '\r' | grep -E 'TotalTime|WaitTime')
  echo "$am" | grep -q TotalTime || { INV_WHY="lượt $lbl: am start -W không in TotalTime"; return 4; }
  say "run $lbl: $(echo "$am" | tr '\n' ' ')"
  while :; do
    pid=$(adb_b 60 shell pidof "$PKG" | tr -d '\r')
    [ -z "$pid" ] || { adb_b 30 logcat -d --pid="$pid" | redact_stream > "$alog"; grep --text -q 'AppPluginBase.onCreate main-thread ms=' "$alog" && break; }
    [ "$w" -lt "$WAIT_MAX" ] || { INV_WHY="lượt $lbl: không có dòng AppPluginBase.onCreate main-thread ms= sau ${WAIT_MAX}s (pid=${pid:-none})"; return 4; }
    sleep 1; w=$((w + 1))
  done
  sleep "$SETTLE"; adb_b 30 logcat -d --pid="$pid" | redact_stream > "$alog"
  l0=$(wc -l < "$LOG"); fail=0; count_strict "run$lbl" "$alog" "$MAP"
  if tail -n +$((l0 + 1)) "$LOG" | grep -qE '^FAIL: (retrace|StrictMode cần|không đếm|mktemp)'; then INV_WHY="lượt $lbl: không đếm được StrictMode (retrace)"; return 4; fi
  by=$(grep "^strictmode run$lbl:" "$LOG" | tail -1 | sed -nE 's/.*by sdk=([0-9]+).*/\1/p'); by=${by:-0}
  RUN_JSON=$(perf_run_json "$([ "$lbl" = first_run ] && echo 0 || echo "$lbl")" "$am" "$(cat "$alog")" "$by") || { INV_WHY="lượt $lbl: thiếu dòng onCreate"; return 4; }
  say "  $RUN_JSON"
}

# Lượt mồi sau cài mới: không tính.
adb_b 60 shell am start -W -n "$PKG/$ACT" > /dev/null 2>&1; sleep "$SETTLE"; say "lượt mồi (không tính) xong"
k=0
while [ "$k" -lt "$RUNS" ]; do
  k=$((k + 1)); perf_one "$k" || invalid "$INV_WHY" "run"
  emit "$RUN_JSON"
done

mem_kv() { # $1 = nhãn; in kv, ghi thô vào ADIR
  adb_b 60 shell dumpsys meminfo "$PKG" | tr -d '\r' > "$ADIR/meminfo-$1.txt"
  perf_meminfo_kv "$ADIR/meminfo-$1.txt"
}
sleep 10; M1=$(mem_kv settle) || invalid "dumpsys meminfo (settle) không có TOTAL PSS" meminfo; say "meminfo settle: $M1"
mg_screen_init
mg_show_full "Show Interstitial"; r=$?; [ "$r" -eq 0 ] || invalid "vòng show: Interstitial rc=$r" ui
mg_show_full "Show Rewarded"; r=$?; [ "$r" -eq 0 ] || invalid "vòng show: Rewarded rc=$r" ui
sleep 31
mg_show_full "Show Rewarded Interstitial"; r=$?; [ "$r" -eq 0 ] || invalid "vòng show: rwi rc=$r" ui
M2=$(mem_kv after_show) || invalid "dumpsys meminfo (after_show) không có TOTAL PSS" meminfo; say "meminfo after_show: $M2"
mg_tap_verified "Show Banner Only" "showBanner call show"; r=$?; [ "$r" -eq 0 ] || invalid "chặng banner rc=$r" ui   # 2.2.0 PERF2 (§D): fill_ms.banner
sleep 20
kvf() { printf '%s\n' "$1" | tr ' ' '\n' | sed -n "s/^$2=//p"; }
emit "{\"meminfo\":true,\"pss_kb\":{\"settle\":$(kvf "$M1" pss_kb),\"after_show\":$(kvf "$M2" pss_kb)},\"java_heap_kb\":{\"settle\":$(kvf "$M1" java_kb),\"after_show\":$(kvf "$M2" java_kb)},\"native_heap_kb\":{\"settle\":$(kvf "$M1" native_kb),\"after_show\":$(kvf "$M2" native_kb)}}"

cl=$(adb_b 60 shell pm clear "$PKG" 2>&1 | tr -d '\r'); say "pm clear $PKG: $cl"
[ "$cl" = Success ] || invalid "pm clear không Success: $cl" run
perf_one first_run || invalid "$INV_WHY" run
emit "$(printf '%s' "$RUN_JSON" | sed 's/^{"run":0,/{"first_run":true,/')"

crash=$(adb_b 30 logcat -d -b crash); events=$(adb_b 30 logcat -d -b events)
f=$(echo "$crash" | grep --text -A5 "FATAL EXCEPTION" | grep --text -cF "Process: $PKG"); n=$(echo "$crash" | grep --text -cF ">>> $PKG <<<")
a=$(echo "$events" | grep --text am_anr | grep --text -cF "$PKG"); say "FATAL=$f am_anr=$a native=$n (need 0/0/0)"
sink_n=$([ -f "$SINK_JSONL" ] && wc -l < "$SINK_JSONL" | tr -d ' ' || echo 0); say "sink jsonl lines=$sink_n ($SINK_JSONL)"
if [ "$((f + a + n))" -ne 0 ]; then say "RESULT: FAIL"; exit 1; fi
say "RESULT: PASS"
