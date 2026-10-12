#!/usr/bin/env bash
# Cổng G1/G2 (DOC/65) trên một máy: cài APK release, cold start, 4 chuỗi R8, StrictMode, monkey,
# crash Java + native, ANR.
# Dùng: tools/device_gate.sh [--variant utility|game] [--serial <s>] --apk <apk> --mapping <mapping.txt>   (1.8.0 Task 16: --serial thay cho env ANDROID_SERIAL)
# (variant mặc định utility — 1.6.4 Task 5: mọi cổng thiết bị chạy APK utility, AdMob + Meta; game chỉ build + release gate).
# 1.7.0 Task 8 (plan-lint #23): --apk và --mapping BẮT BUỘC (bản đóng băng trong scratch của lane); thiếu, hoặc file không
# có, hoặc biến thể lạ: FAIL trước mọi lệnh adb. Cổng không đọc/ghi app/build/outputs dùng chung.
# Tiền kiểm (cũng trước adb): Docker không chạy (pgrep -f Docker.app = 0) và sink cục bộ chạy (tools/local_sink.sh status).
# `reverse tcp:8089 tcp:<sink-port>` (mặc định 8089; --sink-port/--sink-dir 1.8.0 Task 1) sau install, `reverse --remove tcp:8089` lúc thoát; dòng 1 của log in sha256 + bytes
# của APK và sha256 của mapping (plan-lint #25); sampler bộ nhớ chạy nền vào <LOG>.host (plan-lint #26).
# 1.8.0 Task 2 (D11, D17): mọi lệnh tới máy qua adb_b (tools/lib/adb_b.sh, có hạn); dòng `precheck docker=<n> sink=<up|down>` ngay sau dòng 1 của log
# (Docker ≠ 0 hoặc sink down → FAIL trước lệnh đầu tiên); utility mà JSONL của sink tăng 0 dòng → FAIL (R-8: telemetry phải tới sink).
# 1.8.0 Task 16 (G12): --iap nạp tools/lib/iap_gate.sh và chạy 12 script billing của harness (§Hợp đồng B) + tools/iap_check.py THAY
# cold start/monkey; cùng tiền kiểm, --apk/--mapping, --sink-port/--sink-dir; log <LOG%.log>-iap/ giữ bằng chứng từng script.
# 1.9.0 Task 10: --banners nạp tools/lib/banners_gate.sh (banner collapsible, xoay, NtCl show/hide x3, HOME/resume; Task R #10) THAY cold start/monkey.
# 2.0.0 Task 0d: --consent nạp tools/lib/consent_gate.sh (pm clear, máy ấm #29, force_eea, "Do not consent", cửa sổ DENIED 60 s: 0 hàng/0 POST,
# Privacy options -> Consent, flush 45 s) THAY cold start/monkey; dòng 1 có `api=<ro.build.version.sdk>` (getprop lỗi: INVALID (api), exit 4).
# 2.0.0 Task 0c: --drc nạp tools/lib/drc_legs.sh (harness brain: calibrated, kill, rollout_fraction 0 + đối chứng chặng 1 >= 1) THAY cold start/monkey; --measure thêm chặng rwi skip/gap (rwi_legs.sh). 2.3.0 SECBH: --policy (policy_legs.sh) chạy client policy trên sink fake mode; cleanup tắt airplane mode (net_restore.sh).
# 1.7.0 Task 13: --measure nạp tools/lib/measure_gate.sh và chạy kịch bản đo G11 (pha A/B/C + tools/measure_check.py,
# H6a thông tin) THAY cold start/monkey; cùng tiền kiểm (Docker, sink, --apk/--mapping) và cùng dòng 1.
set -u
. "$(dirname "$0")/lib/gate_args.sh" || { echo "FAIL: không nạp được tools/lib/gate_args.sh"; echo "RESULT: FAIL"; exit 1; }   # 2.0.0 Task 0d: parse đối số
export PATH="$PATH:${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools"
cd "$(dirname "$0")/.."
. tools/lib/adb_b.sh || { echo "FAIL: không nạp được tools/lib/adb_b.sh"; echo "RESULT: FAIL"; exit 1; }
. tools/lib/precheck_env.sh || { echo "FAIL: không nạp được tools/lib/precheck_env.sh"; echo "RESULT: FAIL"; exit 1; }   # 2.2.0 Task 0c
. tools/lib/redact.sh || { echo "FAIL: không nạp được tools/lib/redact.sh"; echo "RESULT: FAIL"; exit 1; }   # 1.9.0 Task 0c: che bí mật lúc chụp logcat
. tools/lib/net_restore.sh || { echo "FAIL: không nạp được tools/lib/net_restore.sh"; echo "RESULT: FAIL"; exit 1; }   # 2.3.0 SECBH (D18-230 b): airplane mode tắt lúc thoát
# Tiền kiểm trước mọi lệnh adb: Docker chạy làm số đo nhiễu (1.7.0 plan-lint #24), không có sink thì telemetry mất.
docker_n=$(pgrep -f '^/Applications/Docker.app/Contents/' | wc -l | tr -d ' ')
docker_n=${docker_n:-0}
sink_state=up; sink_out="không dùng (--app)"   # 2.2.0 T0e: --app không --sink-port: không sink, không reverse, không kiểm tăng dòng
[ "$NOSINK" != 1 ] || sink_state=off
[ "$NOSINK" = 1 ] || sink_out=$(bash tools/local_sink.sh status --port "$SINK_PORT" 2>&1) || sink_state=down
PRECHECK="precheck docker=$docker_n sink=$sink_state"
[ "$docker_n" -eq 0 ] || { echo "$PRECHECK"; bad_arg "Docker đang chạy (pgrep -f ^/Applications/Docker.app/Contents/ = $docker_n): dừng Docker Desktop trước cổng thiết bị"; }
[ "$sink_state" != down ] || { echo "$PRECHECK"; bad_arg "sink cục bộ không chạy (tools/local_sink.sh start --port $SINK_PORT --dir <d>): $sink_out"; }
SINK_JSONL=$SINK_DIR/events.jsonl
export MG_SINK_PORT=$SINK_PORT
sink_lines() { if [ -f "$SINK_JSONL" ]; then wc -l < "$SINK_JSONL" | tr -d ' '; else echo 0; fi; }
SINK_BEFORE=$(sink_lines)
SEEDS=${SEEDS:-1 2 3}
EVENTS=${EVENTS:-3000}
MONKEY_MAX=${MONKEY_MAX:-$((EVENTS / 10 + 300))}   # monkey là lệnh dài duy nhất: throttle 50 ms x EVENTS + dư
# Dòng benign đã chứng minh (bản debug không R8 in y hệt), một ERE mỗi dòng, `#` ở cột 0 là chú thích.
ALLOW=tools/device_gate_allowlist.txt
mkdir -p DOC/testing
TS=$(date +%Y%m%d-%H%M%S)
LOG=DOC/testing/device-gate-$ANDROID_SERIAL-$TS.log
export ADB_B_LOG="$LOG"   # adb_b ghi "FAIL: adb_b timeout" vào log; hết hạn là FAIL (R-T2 I1), không phải bằng chứng rỗng
APPLOG=DOC/testing/device-gate-app-$ANDROID_SERIAL-$TS-cold1.log
APPLOG_POST=DOC/testing/device-gate-app-$ANDROID_SERIAL-$TS-post.log
APPLOG2=DOC/testing/device-gate-app-$ANDROID_SERIAL-$TS-cold2.log
CRASHLOG=DOC/testing/device-gate-app-$ANDROID_SERIAL-$TS-crash.log
: > "$CRASHLOG"
# 1.6.2 Task 2 (review 1.6.1 T3 n1; review plan Fable §4): log thô của app và buffer crash ở lại DOC/testing (không
# commit) — số đo sau cổng còn bằng chứng, kể cả khi cổng die() giữa chừng. Không còn file /tmp; trap EXIT chỉ dỡ reverse và sampler (1.7.0 Task 8).
fail=0
fatal=0; native=0; anr=0
# Sampler bộ nhớ nền suốt cổng (#26) và reverse (dỡ ở EXIT: lệnh cuối cùng tới máy, kể cả khi die()).
. tools/lib/quiet_host.sh 2>/dev/null || quiet_host_sampler() { :; }
. tools/lib/invalid_classes.sh 2>/dev/null || invalid_class() { :; }   # 2.1.0 Task 0d (§E): lớp INVALID môi trường, một bộ phân loại cho mọi cổng
FULLLOG=DOC/testing/device-gate-app-$ANDROID_SERIAL-$TS-full.log; : > "$FULLLOG"   # logcat đầy đủ (mọi process): dòng ActivityManager Killing không nằm trong log --pid
SAMPLER_PID=""; REVERSE_ON=""
cleanup() {
  net_restore   # cả khi cổng FAIL: emulator không bị bỏ lại ở airplane mode (đặt trước reverse --remove để đó vẫn là lệnh cuối)
  [ -z "$SAMPLER_PID" ] || { kill "$SAMPLER_PID" 2>/dev/null; wait "$SAMPLER_PID" 2>/dev/null; }
  [ -z "$REVERSE_ON" ] || adb_b 60 reverse --remove tcp:8089 > /dev/null 2>&1
  # --iap (R-16 m4): dừng sink của script 12 và sink của lane (cổng dừng sink lane khi dựng sink mới; hết lượt không để sink nào chạy)
  [ "$IAP" != 1 ] || { [ -z "${IAP_SINK_ROT:-}" ] || bash tools/local_sink.sh stop --port "$SINK_PORT" --dir "$IAP_SINK_ROT" > /dev/null 2>&1
    bash tools/local_sink.sh stop --port "$SINK_PORT" --dir "$SINK_DIR" > /dev/null 2>&1; }
}
trap cleanup EXIT
quiet_host_sampler "$LOG.host" > /dev/null 2>&1 &
SAMPLER_PID=$!

# die, allowlist, đếm 4 chuỗi R8 (count_r8) và StrictMode sau retrace (count_strict) nằm ở lib để test được không cần máy
# (tools/tests/device_gate_allowlist_test.sh). Thiếu lib thì các hàm đó không có và cổng chạy tiếp
# mà không đếm gì, nên FAIL ngay.
. tools/lib/device_gate_r8.sh || {
  echo "FAIL: không nạp được tools/lib/device_gate_r8.sh" | tee -a "$LOG"
  echo "RESULT: FAIL" | tee -a "$LOG"
  exit 1
}
load_allowlist "$ALLOW"

# Crash Java (FATAL EXCEPTION có "Process: $PKG"), crash native (tombstone ">>> $PKG <<<") và am_anr
# của $PKG, chỉ của app, không tính process khác. Đếm sau MỖI chặng rồi xoá buffer: buffer crash/events
# nhỏ, để tới cuối thì sự kiện của seed đầu có thể đã bị xoay mất.
count_crashes() {
  local crash events f n a rc
  crash=$(adb_b 30 logcat -d -b crash); rc=$?
  [ "$rc" -eq 0 ] || { echo "FAIL: logcat crash (adb_b rc=$rc)" | tee -a "$LOG"; fail=1; }
  events=$(adb_b 30 logcat -d -b events); rc=$?
  [ "$rc" -eq 0 ] || { echo "FAIL: logcat events (adb_b rc=$rc)" | tee -a "$LOG"; fail=1; }
  adb_b 60 logcat -c -b crash; adb_b 60 logcat -c -b events
  [ -z "$crash" ] || echo "$crash" | redact_stream >> "$CRASHLOG"
  f=$(echo "$crash" | grep --text -A5 "FATAL EXCEPTION" | grep --text -cF "Process: $PKG")
  n=$(echo "$crash" | grep --text -cF ">>> $PKG <<<")
  a=$(echo "$events" | grep --text am_anr | grep --text -cF "$PKG")
  fatal=$((fatal + f)); native=$((native + n)); anr=$((anr + a))
  echo "  [$1] FATAL=$f native=$n am_anr=$a" | tee -a "$LOG"
}

# Fail-closed: bước nào tới máy hỏng thì cổng FAIL, không để các bộ đếm = 0 thành PASS giả.
# 2.0.0 Task 0d (A1/A2): API của máy vào dòng 1; getprop lỗi hoặc không phải số thì không có bằng chứng về máy -> INVALID (api), trước mọi lệnh cài.
NET_ARM=1; API=$(adb_b 30 shell getprop ro.build.version.sdk 2>/dev/null); api_rc=$?; API=$(printf '%s' "$API" | tr -d '\r\n')
case $API in ''|*[!0-9]*) api_rc=1;; esac
[ "$api_rc" -eq 0 ] || { echo "INVALID (api): getprop ro.build.version.sdk lỗi (rc=$api_rc, '$API')" | tee -a "$LOG"; echo "RESULT: INVALID (api)" | tee -a "$LOG"; exit 4; }
PRE_LINE=$(precheck_env "$ANDROID_SERIAL"); PRE_RC=$?   # 2.2.0 Task 0c: Wi-Fi/keyguard/pin (máy thật) + host không ngủ, TRƯỚC uninstall/install
# Dòng đường dẫn log in TRƯỚC lệnh install: cài lỗi vẫn thấy log nằm đâu (review 1.6.2 T2 m4).
# Biến thể + APK nằm ngay dòng đầu (1.6.4 Task 5), cùng dòng "gate log:" để dòng đó vẫn là dòng đầu (m4).
APK_SHA=$(shasum -a 256 "$APK" | cut -d' ' -f1)
APK_BYTES=$(wc -c < "$APK" | tr -d ' ')
MAP_SHA=none; [ -z "$MAP" ] || MAP_SHA=$(shasum -a 256 "$MAP" | cut -d' ' -f1)
echo "gate log: $LOG; app logs: $APPLOG $APPLOG_POST $APPLOG2 $CRASHLOG; variant: $VARIANT$([ "${STACK:-}" = nextgen ] && echo " (stack: nextgen)") apk=$APK sha256=$APK_SHA bytes=$APK_BYTES; mapping sha256=$MAP_SHA api=$API" | tee "$LOG"
echo "$PRECHECK" | tee -a "$LOG"; echo "$PRE_LINE" | tee -a "$LOG"
[ "$PRE_RC" -eq 0 ] || { echo "invalid (precheck): thiếu điều kiện môi trường (net=1 lock=0 batt>=$PRECHECK_BATT_MIN host=awake), không phải FAIL của sản phẩm" | tee -a "$LOG"; echo "RESULT: INVALID (precheck)" | tee -a "$LOG"; exit 4; }
[ -n "$MAP" ] || { echo "by-sdk: không mapping (INFO: StrictMode không retrace, frame SDK không phán)" | tee -a "$LOG"; RETRACE=off; }
adb_b 60 uninstall $PKG > /dev/null 2>&1
install_out=$(adb_b 120 install -r "$APK" 2>&1)
rc=$?
echo "$install_out" | tee -a "$LOG"
{ [ "$rc" -eq 0 ] && echo "$install_out" | grep -q '^Success'; } || die "cài APK lỗi (install exit=$rc)"
# Sink cục bộ nhận telemetry của demo `utility` (manifest trỏ 127.0.0.1:8089): emulator/máy thấy cổng 8089 của Mac qua reverse.
[ "$NOSINK" = 1 ] || { adb_b 60 reverse tcp:8089 "tcp:$SINK_PORT" > /dev/null 2>&1 && REVERSE_ON=1 || die "adb reverse tcp:8089 tcp:$SINK_PORT lỗi"; }
echo "sink: $sink_out; jsonl=$SINK_JSONL lines before=$SINK_BEFORE" | tee -a "$LOG"
echo "allowlist: $ALLOW_N regex ($ALLOW) = ${ALLOW_RE:-<rỗng>}" | tee -a "$LOG"
# Buffer mặc định (emulator 2M) có thể xoay mất log của app trước khi đọc tới.
adb_b 60 logcat -G 16M > /dev/null 2>&1 || echo "warn: adb logcat -G 16M không áp được" | tee -a "$LOG"
adb_b 60 logcat -c; adb_b 60 logcat -c -b crash; adb_b 60 logcat -c -b events
if [ "$MEASURE" = 1 ]; then
  . tools/lib/measure_gate.sh || die "không nạp được tools/lib/measure_gate.sh"
  MG_LOG=$LOG; MG_CRASHLOG=$CRASHLOG
  measure_run "${LOG%.log}-measure" "$SINK_JSONL" "$SINK_BEFORE"; rc=$?
  [ "$rc" -eq 0 ] || exit "$rc"
  mg_full2_all || rc=1   # 2.4.0 DEMO-RES (D8-240): 3 nút Full2 + quét crash/ANR, SAU mọi chặng, dòng riêng; hàng cũ #10/#11 giữ ngưỡng
  mg_say "RESULT: $([ "$rc" -eq 0 ] && echo PASS || echo FAIL) (kèm Full2)"
  exit "$rc"
fi
if [ "$RESUME" = 1 ]; then   # 2.4.0 DEMO-RES (OWNER 12): app open resume ON/OFF trên APK utility (mg_resume_open)
  [ "$VARIANT" = utility ] || die "--resume chỉ chạy trên APK utility (sink 8089)"
  . tools/lib/measure_gate.sh || die "không nạp được tools/lib/measure_gate.sh"
  MG_LOG=$LOG; MG_CRASHLOG=$CRASHLOG
  mg_resume_open "${LOG%.log}-resume" "$SINK_JSONL"
  exit $?
fi
if [ "$BANNERS" = 1 ]; then
  . tools/lib/measure_gate.sh || die "không nạp được tools/lib/measure_gate.sh"
  . tools/lib/banners_gate.sh || die "không nạp được tools/lib/banners_gate.sh"
  MG_LOG=$LOG; MG_CRASHLOG=$CRASHLOG
  banners_run "${LOG%.log}-banners" "$SINK_JSONL" "$SINK_BEFORE"
  exit $?
fi
if [ "$CONSENT" = 1 ]; then
  [ "$VARIANT" = utility ] || die "--consent chỉ chạy trên APK utility"
  . tools/lib/measure_gate.sh || die "không nạp được tools/lib/measure_gate.sh"
  . tools/lib/consent_gate.sh || die "không nạp được tools/lib/consent_gate.sh"
  MG_LOG=$LOG; MG_CRASHLOG=$CRASHLOG
  consent_run "${LOG%.log}-consent" "$SINK_JSONL" "$SINK_BEFORE" "$SINK_PORT" "$SINK_DIR"
  exit $?
fi
if [ "$DRC" = 1 ]; then
  [ "$VARIANT" = utility ] || die "--drc chỉ chạy trên APK utility (khoá manifest BRAIN_HARNESS)"
  . tools/lib/measure_gate.sh || die "không nạp được tools/lib/measure_gate.sh"
  . tools/lib/drc_legs.sh || die "không nạp được tools/lib/drc_legs.sh"
  MG_LOG=$LOG; MG_CRASHLOG=$CRASHLOG
  drc_run "${LOG%.log}-drc" "$SINK_JSONL" "$SINK_BEFORE"
  exit $?
fi
if [ "$POLICY" = 1 ]; then   # 2.3.0 SECBH: client policy trên sink fake mode (tools/lib/policy_legs.sh)
  [ "$VARIANT" = utility ] || die "--policy chỉ chạy trên APK utility (khoá manifest BRAIN_POLICY_URL)"
  . tools/lib/measure_gate.sh || die "không nạp được tools/lib/measure_gate.sh"
  . tools/lib/policy_legs.sh || die "không nạp được tools/lib/policy_legs.sh"
  MG_LOG=$LOG; MG_CRASHLOG=$CRASHLOG
  policy_run "${LOG%.log}-policy"
  exit $?
fi
if [ "$IAP" = 1 ]; then
  [ "$VARIANT" = utility ] || die "--iap chỉ chạy trên APK utility (harness có khoá manifest riêng)"
  . tools/lib/iap_gate.sh || die "không nạp được tools/lib/iap_gate.sh"
  MG_LOG=$LOG; MG_CRASHLOG=$CRASHLOG
  iap_run "${LOG%.log}-iap" "$SINK_JSONL" "$SINK_PORT" "$SINK_DIR"
  exit $?
fi
log_load "cold start"
adb_b 60 shell am force-stop $PKG
start_out=$(adb_b 60 shell am start -W -n $PKG/$ACT $EEA_ARGS | grep -E "TotalTime|WaitTime")
echo "$start_out" | tee -a "$LOG"
echo "$start_out" | grep -q "TotalTime" || die "am start -W không in TotalTime"
PID=$(adb_b 60 shell pidof $PKG | tr -d '\r')
[ -n "$PID" ] || die "không thấy process $PKG sau am start"
echo "app pid=$PID" | tee -a "$LOG"
adb_b 60 shell sleep 20

# Chỉ log của process app: log khởi động của app hệ thống (GMS, Messages...) không làm cổng đỏ.
adb_b 30 logcat -d --pid="$PID" | redact_stream > "$APPLOG"; [ "${PIPESTATUS[0]}" -eq 0 ] || { echo "FAIL: logcat" | tee -a "$LOG"; fail=1; }
[ -s "$APPLOG" ] || { echo "FAIL: log của app rỗng ($APPLOG)" | tee -a "$LOG"; fail=1; }
count_r8 "after cold start" "$APPLOG"
# StrictMode (G2): số raw (như trước) rồi số sau retrace bằng $MAP — lớp SDK bị R8 đổi tên và repackage,
# nên chỉ số sau retrace nói được frame nào của SDK. Có StrictMode mà không retrace được thì FAIL.
count_strict "cold start" "$APPLOG" "$MAP"
count_strict_armed "cold start" "$APPLOG"; log_key_lines "cold start" "$APPLOG" "$(printf '%s\n' "$start_out" | grep -o 'TotalTime: [0-9]*' | sed 's/.*: //')"
count_crashes coldstart

for s in $SEEDS; do
  log_load "seed=$s"
  MLOG=DOC/testing/device-gate-monkey-$ANDROID_SERIAL-$TS-seed$s.log
  adb_b "$MONKEY_MAX" shell monkey -p $PKG --throttle 50 --pct-syskeys 0 --ignore-crashes --ignore-timeouts -s "$s" "$EVENTS" > "$MLOG" 2>&1
  rc=$?
  n=$(grep -cE "// (CRASH|NOT RESPONDING): $PKG( |$)" "$MLOG")
  echo "monkey seed=$s exit=$rc crash/anr lines=$n ($MLOG)" | tee -a "$LOG"
  [ "$rc" -eq 0 ] || fail=1
  [ "$n" -eq 0 ] || fail=1
  count_crashes "seed=$s"
done

# Lần hai, sau monkey: đường code monkey chạm tới cũng không được có 4 chuỗi R8. Gồm pid ban đầu và
# pid hiện tại nếu app đã được dựng lại.
pids=$PID
for p in $(adb_b 60 shell pidof $PKG | tr -d '\r'); do [ "$p" = "$PID" ] || pids="$pids $p"; done
: > "$APPLOG_POST"
for p in $pids; do
  adb_b 30 logcat -d --pid="$p" | redact_stream >> "$APPLOG_POST"; [ "${PIPESTATUS[0]}" -eq 0 ] || { echo "FAIL: logcat pid=$p" | tee -a "$LOG"; fail=1; }
done
# Log rỗng: app không chạy hay logcat không đọc được, nên "0 chuỗi R8" không phải bằng chứng. Dừng
# cổng, nhưng chép buffer crash đã gom vào $LOG trước.
[ -s "$APPLOG_POST" ] || { cat "$CRASHLOG" >> "$LOG"; die "log của app sau monkey rỗng (pid: $pids)"; }
count_r8 "after monkey" "$APPLOG_POST" ", pid: $pids"

# Chặng "cold start 2 (non-EEA)" (1.6.1 Task 3): chặng 1 ở trên luôn mở app với --ez force_eea true,
# tức form UMP mở và chưa được trả lời, nên cổng analytics của SDK không mở trong 20 s đo và af.init
# không bao giờ chạy trong cửa sổ đó — G2 mù với đường AppsFlyer. Chặng này pm clear rồi mở lại KHÔNG
# force_eea: UMP trả NOT_REQUIRED ngay NẾU máy không ở EEA/UK/CH (dev base VN, ngoài phạm vi UMP bắt
# form) — máy ở EEA/UK thì form vẫn mở, đường AppsFlyer coi như "chặng 1" (xem FAIL hint của
# count_af_started), fail-closed chứ không PASS giả. Đặt chặng này SAU monkey để số SAU ALLOWLIST của
# chặng 1 + monkey không đổi so với 1.6.0 (raw có thể tăng do AppsFlyer start trong monkey — xem allowlist LVL).
# m8 (review 1.6.1 T3): crash/ANR giữa lần đếm của seed cuối và chặng 2 được đếm, không bị xoá mất.
count_crashes post-monkey
# m1 (review 1.6.1 T3): kết quả pm clear vào $LOG; không Success thì chặng 2 không phải cài mới → FAIL.
log_load "cold start 2"
clear_out=$(adb_b 60 shell pm clear $PKG 2>&1 | tr -d '\r')
echo "pm clear $PKG: $clear_out" | tee -a "$LOG"
[ "$clear_out" = Success ] || die "pm clear $PKG không Success: $clear_out"
adb_b 30 logcat -d | redact_stream >> "$FULLLOG"   # trước khi xoá buffer của chặng 2
adb_b 60 logcat -c
start2=$(adb_b 60 shell am start -W -n $PKG/$ACT | grep -E "TotalTime|WaitTime")
echo "[cold start 2, non-EEA] $start2" | tee -a "$LOG"
echo "$start2" | grep -q "TotalTime" || die "am start -W (cold start 2, non-EEA) không in TotalTime"
PID2=$(adb_b 60 shell pidof $PKG | tr -d '\r')
[ -n "$PID2" ] || die "không thấy process $PKG sau cold start 2 (non-EEA)"
echo "app pid (cold start 2)=$PID2" | tee -a "$LOG"
adb_b 60 shell sleep 20
adb_b 30 logcat -d --pid="$PID2" | redact_stream > "$APPLOG2"; [ "${PIPESTATUS[0]}" -eq 0 ] || { echo "FAIL: logcat cold start 2 (non-EEA)" | tee -a "$LOG"; fail=1; }
[ -s "$APPLOG2" ] || { echo "FAIL: log của app sau cold start 2 (non-EEA) rỗng ($APPLOG2)" | tee -a "$LOG"; fail=1; }
count_r8 "after cold start 2 (non-EEA)" "$APPLOG2"
count_strict "cold start 2 (non-EEA, appsflyer)" "$APPLOG2" "$MAP"
count_strict_armed "cold start 2" "$APPLOG2"; log_key_lines "cold start 2" "$APPLOG2" "$(printf '%s\n' "$start2" | grep -o 'TotalTime: [0-9]*' | sed 's/.*: //')"
count_af_started "cold start 2" "$APPLOG2"
count_crashes coldstart2

echo "FATAL=$fatal am_anr=$anr native=$native (need 0/0/0)" | tee -a "$LOG"
{ [ "$fatal" -eq 0 ] && [ "$anr" -eq 0 ] && [ "$native" -eq 0 ]; } || fail=1
cat "$CRASHLOG" >> "$LOG"

SINK_AFTER=$(sink_lines)
echo "sink jsonl lines before=$SINK_BEFORE after=$SINK_AFTER (+$((SINK_AFTER - SINK_BEFORE)))" | tee -a "$LOG"
# R-8 (1.8.0 Task 2): utility gửi telemetry tới sink cục bộ; JSONL không tăng dòng nào = app không gửi được (sink/reverse/consent
# hỏng) và mọi số đo dựa vào sink là giả. 0 dòng mới → FAIL.
if [ "$VARIANT" = utility ] && [ "$NOSINK" != 1 ] && [ "$SINK_AFTER" -le "$SINK_BEFORE" ]; then
  echo "FAIL: JSONL của sink tăng 0 dòng ($SINK_JSONL before=$SINK_BEFORE after=$SINK_AFTER): telemetry không tới sink" | tee -a "$LOG"; fail=1
elif [ "$NOSINK" != 1 ] && [ "$SINK_AFTER" -le "$SINK_BEFORE" ]; then   # 2.4.0 CARRY-T #35: không phải utility (vd. game dưới monkey) mà sink +0: không FAIL, nhưng nói rõ
  echo "INFO: sink +0 (không hàng mới)" | tee -a "$LOG"
fi
grep -q '^FAIL: adb_b timeout' "$LOG" && fail=1
# 2.1.0 Task 0d (§E): lượt hỏng mà log có lớp môi trường (killDueToPackageUpdate của pid app, GMS persistent, IapHelper unavailable: 3) và không có
# FATAL/ANR/native (invalid_class in FAIL trước mọi lớp; am_anr nằm ở buffer events, đã đếm vào $anr) -> INVALID, không tính, chạy lại <= 1.
adb_b 30 logcat -d | redact_stream >> "$FULLLOG"
cls=$(INVALID_PIDS="$PID ${PID2:-}" invalid_class "$PKG" "$FULLLOG" "$CRASHLOG")   # Killing <pid>:<pkg> chỉ tính với pid app đã ghi (chặng 1/2)
if [ "$fail" -ne 0 ] && [ "$anr" -eq 0 ] && [ -n "$cls" ] && [ "$cls" != FAIL ]; then
  echo "invalid $cls: lỗi môi trường trong cửa sổ cổng, không phải FAIL của sản phẩm (log: $FULLLOG)" | tee -a "$LOG"; echo "RESULT: INVALID $cls" | tee -a "$LOG"; exit 4
fi
echo "RESULT: $([ $fail -eq 0 ] && echo PASS || echo FAIL)" | tee -a "$LOG"
exit $fail
