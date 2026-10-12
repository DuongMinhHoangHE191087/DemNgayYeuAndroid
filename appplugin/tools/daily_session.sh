#!/usr/bin/env bash
# Phiên đo hằng ngày (1.7.0 Task 13, RC-1): chỉ pha A của tools/lib/measure_gate.sh — một phiên hợp lệ có quảng cáo
# (≥ 1 FA origin=app ad_impression và ≥ 1 ad_revenue của session_id trong JSONL), invalid thì chạy lại tối đa 2 lần.
# Dùng: tools/daily_session.sh [--serial <s>] [--apk <apk đóng băng>] [--sink-port <p>] [--sink-dir <d>]   (1.8.0 Task 1)   (serial mặc định emulator-5570)
# Chạy một lần mỗi ngày UTC+7 (sau tools/emu_boot.sh, khoá emugate, 0 Gradle), sink đã chạy (tools/local_sink.sh start).
# Máy thật (Task R #12, vd Redmi 65c9b673) được nhận: khoá `real` là việc của lane, như device_gate.sh (R-13 I5).
# --apk: cài bản đóng băng trước (emulator boot -read-only không giữ app); thiếu thì app phải có sẵn trên máy.
# Ra: DOC/testing/daily/<ngày UTC+7>/<serial>-<YYYYmmddTHHMM>+07/ gồm events.jsonl + logcat.txt + logcat-app.txt của
# lượt hợp lệ, a<n>.* của mọi lượt, session.txt (serial, session_id, apk sha256, ngày UTC+7 của client_ts_ms), h6a.txt
# (tools/reconcile_session.sh, thông tin), session.log; không lượt nào hợp lệ → INVALID.txt (measure_check --local-days bỏ).
# Tiền kiểm trước mọi lệnh adb: Docker không chạy, sink chạy. Exit 0 = phiên hợp lệ + FATAL/ANR 0.
set -u
SERIAL=emulator-5570
APK=""
SINK_PORT=8089
SINK_DIR=${SINK_DIR:-DOC/testing/local-sink}
bad_arg() { echo "FAIL: $1"; echo "RESULT: FAIL"; exit 1; }
while [ $# -gt 0 ]; do
  case $1 in
    --serial) [ $# -ge 2 ] || bad_arg "--serial cần một serial"; SERIAL=$2; shift 2;;
    --apk) [ $# -ge 2 ] || bad_arg "--apk cần một đường dẫn"; APK=$2; shift 2;;
    --sink-port) [ $# -ge 2 ] || bad_arg "--sink-port cần một số cổng (1024..65535)"; SINK_PORT=$2; shift 2;;
    --sink-dir) [ $# -ge 2 ] || bad_arg "--sink-dir cần một đường dẫn"; SINK_DIR=$2; SINK_DIR_ARG=1; shift 2;;
    *) bad_arg "đối số lạ '$1' (chỉ nhận --serial <s>, --apk <apk>, --sink-port <p>, --sink-dir <d>)";;
  esac
done
case $SINK_PORT in ''|*[!0-9]*) bad_arg "--sink-port phải là số trong 1024..65535 (nhận '$SINK_PORT')";; esac
{ [ "$SINK_PORT" -ge 1024 ] && [ "$SINK_PORT" -le 65535 ]; } || bad_arg "--sink-port phải trong 1024..65535 (nhận '$SINK_PORT')"
[ -z "${SINK_DIR_ARG:-}" ] || case $SINK_DIR in /*) ;; *) SINK_DIR=$PWD/$SINK_DIR;; esac
if [ -n "$APK" ]; then
  [ -f "$APK" ] || bad_arg "--apk trỏ file không có: $APK"
  case $APK in /*) ;; *) APK=$PWD/$APK;; esac
fi
export ANDROID_SERIAL=$SERIAL
export PATH="$PATH:${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools"
cd "$(dirname "$0")/.." || bad_arg "không vào được gốc repo"
docker_n=$(pgrep -f '^/Applications/Docker.app/Contents/' | wc -l | tr -d ' ')
[ "${docker_n:-0}" -eq 0 ] || bad_arg "Docker đang chạy (pgrep -f ^/Applications/Docker.app/Contents/ = $docker_n): dừng Docker Desktop trước phiên đo"
sink_out=$(bash tools/local_sink.sh status --port "$SINK_PORT" 2>&1) || bad_arg "sink cục bộ không chạy (tools/local_sink.sh start --port $SINK_PORT --dir <d>): $sink_out"
export MG_SINK_PORT=$SINK_PORT
. tools/lib/measure_gate.sh || bad_arg "không nạp được tools/lib/measure_gate.sh"

DAY=$(TZ=Asia/Ho_Chi_Minh date +%F)
OUT=DOC/testing/daily/$DAY/$SERIAL-$(TZ=Asia/Ho_Chi_Minh date +%Y%m%dT%H%M)+07
mkdir -p "$OUT"
MG_LOG=$OUT/session.log
export ADB_B_LOG="$MG_LOG"   # adb_b ghi "FAIL: adb_b timeout" vào session.log (R-T2 I1)
MG_JSONL=$SINK_DIR/events.jsonl
MG_CRASHLOG=$OUT/crash.log
REVERSE_ON=""
cleanup() { [ -z "$REVERSE_ON" ] || adb_b 60 reverse --remove tcp:8089 > /dev/null 2>&1; }
trap cleanup EXIT
mg_say "daily session: serial=$SERIAL out=$OUT sink: $sink_out; jsonl=$MG_JSONL; return arm=$MG_RETURN_ARM"
if [ -n "$APK" ]; then
  sha=$(shasum -a 256 "$APK" | cut -d' ' -f1)
  mg_say "apk=$APK sha256=$sha bytes=$(wc -c < "$APK" | tr -d ' ')"
  install_out=$(adb_b 120 install -r "$APK" 2>&1)
  echo "$install_out" | grep -q '^Success' || { mg_say "FAIL: cài APK (install): $install_out"; mg_say "RESULT: FAIL"; exit 1; }
else
  sha="(không --apk: app có sẵn trên máy)"
fi
adb_b 60 reverse tcp:8089 "tcp:$SINK_PORT" > /dev/null 2>&1 && REVERSE_ON=1 || { mg_say "FAIL: adb reverse tcp:8089 tcp:$SINK_PORT"; mg_say "RESULT: FAIL"; exit 1; }
# Buffer mặc định (emulator boot -read-only: 2M) có thể xoay mất dòng `logevent: session_id=` trước khi lưu (R-13 m5).
adb_b 60 logcat -G 16M > /dev/null 2>&1 || mg_say "warn: adb logcat -G 16M không áp được"
adb_b 60 logcat -c -b crash > /dev/null 2>&1; adb_b 60 logcat -c -b events > /dev/null 2>&1

fail=0
if mg_phase_a_valid "$OUT/a"; then
  cp "$MG_A.jsonl" "$OUT/events.jsonl"
  cp "$MG_A.log" "$OUT/logcat.txt"
  cp "$MG_A-app.log" "$OUT/logcat-app.txt"
  days=$("${MEASURE_PY:-python3}" -c 'import json,sys,datetime as d
tz=d.timezone(d.timedelta(hours=7)); s=set()
for l in open(sys.argv[1]):
    l=l.strip()
    if l: s.add(d.datetime.fromtimestamp(json.loads(l).get("client_ts_ms",0)/1000,tz).date().isoformat())
print(" ".join(sorted(s)))' "$OUT/events.jsonl" 2>&1)
  {
    echo "serial=$SERIAL"; echo "session_id=$MG_SID"; echo "apk_sha256=$sha"
    echo "utc7_date_of_client_ts_ms=$days"; echo "jsonl_rows=$(wc -l < "$OUT/events.jsonl" | tr -d ' ')"
    echo "ad_revenue=$(grep -cF '"event_name": "ad_revenue"' "$OUT/events.jsonl")"
  } > "$OUT/session.txt"
  while IFS= read -r l; do mg_say "  $l"; done < "$OUT/session.txt"
  bash tools/reconcile_session.sh "$OUT/logcat.txt" "$OUT/events.jsonl" "$MG_SID" > "$OUT/h6a.txt" 2>&1
  while IFS= read -r l; do mg_say "H6a (thông tin) $l"; done < "$OUT/h6a.txt"
else
  fail=1
  echo "không lượt pha A nào hợp lệ (xem session.log)" > "$OUT/INVALID.txt"
fi
mg_count_crashes "phiên"
mg_say "FATAL=$MG_FATAL am_anr=$MG_ANR native=$MG_NATIVE (need 0/0/0)"
[ "$MG_FATAL" -eq 0 ] && [ "$MG_ANR" -eq 0 ] && [ "$MG_NATIVE" -eq 0 ] || fail=1
[ "${MG_CAPTURE_FAIL:-0}" -eq 0 ] && ! grep -q '^FAIL: adb_b timeout' "$MG_LOG" || fail=1
mg_say "RESULT: $([ "$fail" -eq 0 ] && echo PASS || echo FAIL)"
exit "$fail"
