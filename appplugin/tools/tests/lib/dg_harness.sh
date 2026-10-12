#!/usr/bin/env bash
# Harness dùng chung cho tools/tests/dg_*_test.sh (tách từ device_gate_allowlist_test.sh, 2.0.0 Task 0a):
# adb giả, prepare_gate/run_gate, check/has/hasnt. Được source, không chạy riêng. Chạy được trên bash 3.2.
set -u
ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)
LIB=$ROOT/tools/lib/device_gate_r8.sh
TMP=$(mktemp -d "${TMPDIR:-/tmp}/device-gate-test.XXXXXX") || { echo "FAIL: mktemp"; exit 1; }
trap 'rm -rf "$TMP"' EXIT
total=0; bad=0
export PRE_FIX=$ROOT/tools/tests/fixtures/precheck   # 2.2.0 Task 0c: fixture dumpsys/pmset thật (Redmi 65c9b673 + host)

# App log giả: 2 dòng NoClassDefFoundError (R8 hỏng thật), 1 dòng ProviderInstaller benign (có trong
# allowlist thật; 1.9.0 Task 7: thêm biến thể API 33 có tiền tố ProviderInstallerImpl), 2 dòng không phải chuỗi R8.
APPLOG=$TMP/app.log
cat > "$APPLOG" <<'EOF'
09-24 15:41:38.339  4412  4412 I test.sdk.zd: Rejecting re-init on previously-failed class java.lang.Class<w60>: java.lang.NoClassDefFoundError: Failed resolution of: Ljava/lang/ClassValue;
09-24 15:41:38.340  4412  4412 W test.sdk.zd: java.lang.NoClassDefFoundError: Failed resolution of: Ljava/lang/ClassValue;
09-24 16:16:29.512  6139  6214 W ProviderInstaller: Failed to load providerinstaller module: No acceptable module com.google.android.gms.providerinstaller.dynamite found. Local version is 0 and remote version is 0.
09-24 16:16:29.559  6139  6214 W ProviderInstaller: Failed to report request stats: java.lang.NoSuchMethodException: reportRequestStats2 [class android.content.Context, long, long]
09-24 16:16:29.560  6139  6214 W ProviderInstaller: Failed to report request stats: java.lang.NoSuchMethodException: com.google.android.gms.common.security.ProviderInstallerImpl.reportRequestStats2 [class android.content.Context, long, long]
09-24 16:16:30.001  6139  6139 I test.sdk.zd: ok
EOF
# Chỉ có dòng benign: cổng phải PASS (allowlist không làm cổng đỏ oan).
APPLOG_OK=$TMP/app-ok.log
grep -F ProviderInstaller "$APPLOG" > "$APPLOG_OK"

# $1 = tên file allowlist tạm, các tham số sau = từng dòng của file.
allowlist() { local f=$TMP/$1; shift; printf '%s\n' "$@" > "$f"; echo "$f"; }

# Phần allowlist + count_r8 của cổng, đúng thứ tự trong device_gate.sh, rồi dòng RESULT cuối như cổng.
# $1 = allowlist, $2 = app log, $3 = $LOG. In stdout+stderr, exit như cổng.
run_lib() {
  (
    LOG=$3; fail=0
    . "$LIB" || exit 99
    load_allowlist "$1"
    echo "allowlist: ${ALLOW_N:-?} regex"
    count_r8 fixture "$2"
    echo "RESULT: $([ "$fail" -eq 0 ] && echo PASS || echo FAIL)" | tee -a "$LOG"
    exit "$fail"
  ) 2>&1
}

# Cổng thật trong một cây tạm ($GATE_ROOT), adb giả ghi lại mọi lệnh: install in Success, còn lại in
# rỗng (nên cổng dừng ở "am start -W không in TotalTime", sau dòng "allowlist: N regex").
# FAKE_ADB_FULL=1: adb giả đi hết cổng (TotalTime, pid 4242, monkey không crash, buffer crash/events
# rỗng, app log có một dòng); FAKE_POST_LOG=empty: app log đọc sau monkey rỗng (app không chạy) — áp
# cho cả chặng cold start 2 (1.6.1 Task 3), vì cổng die() ở "log sau monkey rỗng" trước khi tới chặng 2.
# Chặng cold start 2 (sau "shell pm clear") có dòng "appsflyer started after consent" mặc định để
# count_af_started không tự làm cổng giả FAIL ở mọi ca không liên quan tới af_started; FAKE_AF_STARTED=no
# bỏ đúng dòng đó (giữ log không rỗng) để kiểm riêng nhánh count_af_started (review I2b). FAKE_COLD2_LOG
# thêm nội dung vào log chặng 2 (như FAKE_COLD_LOG với chặng 1) để kiểm count_strict có thật sự chạy ở
# chặng 2 không (review I2a) — cổng giả trước bản vá review chỉ gắn cờ StrictMode bằng $FAKE_COLD_LOG ở
# chặng 1, không có cách nào bơm StrictMode riêng cho chặng 2.
# prepare_gate $1 = allowlist, $2 = "nolib" để bỏ lib. Gọi thẳng (không trong $(...)) để giữ GATE_ROOT.
n_gate=0
prepare_gate() {
  n_gate=$((n_gate + 1))
  GATE_ROOT=$TMP/gate-$n_gate
  mkdir -p "$GATE_ROOT/tools/lib" "$TMP/bin"
  cp "$ROOT/tools/device_gate.sh" "$GATE_ROOT/tools/"
  [ "${2:-}" = nolib ] || cp "$LIB" "$GATE_ROOT/tools/lib/"
  cp "$ROOT/tools/lib/redact.sh" "$GATE_ROOT/tools/lib/"   # 1.9.0 Task 0c: mọi chỗ chụp logcat đi qua redact_stream
  cp "$ROOT/tools/lib/adb_b.sh" "$GATE_ROOT/tools/lib/"   # 1.8.0 Task 2: mọi lệnh adb của cổng đi qua adb_b
  cp "$ROOT/tools/lib/gate_args.sh" "$GATE_ROOT/tools/lib/"   # 2.0.0 Task 0d: parse đối số nằm ở lib
  if [ "${PRECHECK_REAL:-}" = 1 ]; then cp "$ROOT/tools/lib/precheck_env.sh" "$GATE_ROOT/tools/lib/"; else cp "$ROOT/tools/tests/lib/precheck_stub.sh" "$GATE_ROOT/tools/lib/precheck_env.sh"; fi   # 2.2.0 Task 0c
  cp "$1" "$GATE_ROOT/tools/device_gate_allowlist.txt"
  # 1.7.0 Task 8: cổng bắt buộc --apk/--mapping (bản đóng băng), tiền kiểm Docker + sink, sampler quiet_host.
  mkdir -p "$GATE_ROOT/apk"
  printf 'fake apk %s\n' "$n_gate" > "$GATE_ROOT/apk/app-release.apk"
  printf '# fake mapping\n' > "$GATE_ROOT/apk/mapping.txt"
  [ ! -f "$ROOT/tools/lib/quiet_host.sh" ] || cp "$ROOT/tools/lib/quiet_host.sh" "$GATE_ROOT/tools/lib/"
  cat > "$GATE_ROOT/tools/local_sink.sh" <<'SINKEOF'
#!/usr/bin/env bash
case "$1" in status) [ "${FAKE_SINK:-up}" = up ] && { echo "sink up"; exit 0; }; echo "sink down"; exit 1;; esac
exit 0
SINKEOF
  cat > "$TMP/bin/pgrep" <<'PGEOF'
#!/usr/bin/env bash
[ "${FAKE_DOCKER:-0}" = 1 ] && case "$*" in *Docker.app*) echo 4321;; esac
exit 0
PGEOF
  chmod +x "$TMP/bin/pgrep"
  # 2.2.0 Task 0c: pmset giả trả fixture host thật; FAKE_HOST=sleep bỏ cụm `(sleep prevented by ...)` -> máy ngủ được.
  cat > "$TMP/bin/pmset" <<'PMEOF'
#!/usr/bin/env bash
if [ "${FAKE_HOST:-awake}" = sleep ]; then sed 's/ (sleep prevented by.*)//' "$PRE_FIX/host-pmset.txt"; else cat "$PRE_FIX/host-pmset.txt"; fi
PMEOF
  chmod +x "$TMP/bin/pmset"
  # 1.7.0 Task 13: --measure nạp lib đo + measure_check.py (import dedup_key của backend thật qua symlink).
  [ ! -f "$ROOT/tools/lib/measure_gate.sh" ] || cp "$ROOT/tools/lib/measure_gate.sh" "$GATE_ROOT/tools/lib/"
  cp "$ROOT/tools/lib/ui_find.sh" "$GATE_ROOT/tools/lib/"   # 2.1.0 Task 0c: measure_gate.sh nạp ui_find.sh
  [ ! -f "$ROOT/tools/lib/banners_gate.sh" ] || cp "$ROOT/tools/lib/banners_gate.sh" "$GATE_ROOT/tools/lib/"   # 1.9.0 Task 10
  [ ! -f "$ROOT/tools/lib/banners_ntcl.sh" ] || cp "$ROOT/tools/lib/banners_ntcl.sh" "$GATE_ROOT/tools/lib/"   # 2.0.0 T0b
  [ ! -f "$ROOT/tools/lib/consent_gate.sh" ] || cp "$ROOT/tools/lib/consent_gate.sh" "$GATE_ROOT/tools/lib/"   # 2.0.0 Task 0d
  [ ! -f "$ROOT/tools/lib/consent_miui.sh" ] || cp "$ROOT/tools/lib/consent_miui.sh" "$GATE_ROOT/tools/lib/"   # 2.1.0 Task 0b
  for f in measure_tap.sh rwi_legs.sh drc_legs.sh policy_legs.sh net_restore.sh free_port.sh; do [ ! -f "$ROOT/tools/lib/$f" ] || cp "$ROOT/tools/lib/$f" "$GATE_ROOT/tools/lib/"; done   # 2.0.0 Task 0c; 2.3.0 SECBH
  for f in measure_check.py measure_days.py reconcile_session.sh daily_session.sh; do [ ! -f "$ROOT/tools/$f" ] || cp "$ROOT/tools/$f" "$GATE_ROOT/tools/"; done
  ln -sfn "$ROOT/backend" "$GATE_ROOT/backend"   # 2.2.0 T0d: -n, cây thử dùng lại không tạo $ROOT/backend/backend
  cat > "$TMP/bin/adb" <<'EOF'
#!/usr/bin/env bash
echo "$*" >> "$ADB_CALLS"
case "$1" in install) echo Success; exit 0;; esac
case "$*" in "shell getprop ro.build.version.sdk") [ "${FAKE_GETPROP:-}" = fail ] && exit 1; echo "${FAKE_API:-34}"; exit 0;; esac   # 2.0.0 Task 0d: dòng 1 của cổng có api=
# 2.2.0 Task 0c: precheck_env thật (PRECHECK_REAL=1) đọc dumpsys của Redmi thật (fixture); FAKE_NET=down, FAKE_KEYGUARD=true, FAKE_BATT=<n> đổi đúng một giá trị.
case "$*" in
  "shell dumpsys connectivity") if [ "${FAKE_NET:-up}" = down ]; then sed 's/WIFI CONNECTED/WIFI DISCONNECTED/' "$PRE_FIX/redmi-connectivity.txt"; else cat "$PRE_FIX/redmi-connectivity.txt"; fi; exit 0;;
  "shell dumpsys window") sed "s/isKeyguardShowing=false/isKeyguardShowing=${FAKE_KEYGUARD:-false}/" "$PRE_FIX/redmi-keyguard.txt"; exit 0;;
  "shell dumpsys battery") sed "s/level: 100/level: ${FAKE_BATT:-100}/" "$PRE_FIX/redmi-battery.txt"; exit 0;;
esac
[ "${FAKE_ADB_FULL:-}" = 1 ] || exit 0
case "$*" in
  "shell pm clear"*) echo "${FAKE_PM_CLEAR:-Success}";;
  "shell cat /proc/loadavg") [ "${FAKE_LOADAVG:-}" = empty ] || echo "0.50 0.40 0.30 1/100 4242";;
  "logcat -c -b events")
    # B11 (R-T2 I1): lần đầu treo quá hạn 60 s của adb_b; lệnh xoá buffer không kiểm rc, chỉ dòng "FAIL: adb_b timeout" trong log bắt được
    if [ "${FAKE_CLEAR_HANG:-}" = 1 ] && [ ! -e "$ADB_CALLS.hung2" ]; then : > "$ADB_CALLS.hung2"; exec sleep 65; fi;;
  "logcat -d -b crash")
    # B10 (R-T2 I1): lần đầu treo quá hạn 30 s của adb_b (một lần, để cổng chạy tiếp và kết luận từ log)
    if [ "${FAKE_CRASH_HANG:-}" = 1 ] && [ ! -e "$ADB_CALLS.hung" ]; then : > "$ADB_CALLS.hung"; exec sleep 35; fi
    if [ "${FAKE_CRASH_POST:-}" = 1 ] && [ "$(grep -c '^logcat -d --pid' "$ADB_CALLS")" -ge 2 ] && ! grep -q '^shell pm clear' "$ADB_CALLS"; then
      echo "E AndroidRuntime: FATAL EXCEPTION: pool-3"; echo "E AndroidRuntime: Process: test.sdk.zd, PID: 4242"
    fi;;
  "shell am start -W"*) # chặng 2 (sau pm clear) in TotalTime khác chặng 1: đấu chéo $start_out/$start2 phải đỏ (1.6.3 fix round 1)
    if grep -q '^shell pm clear' "$ADB_CALLS"; then echo "TotalTime: 200"; else echo "TotalTime: 100"; fi; echo "WaitTime: 110";;
  "shell pidof"*) echo 4242;;
  "shell monkey"*) echo "// Monkey finished"
    # 1.8.0 Task 2 (D17, R-8): app đang chạy gửi telemetry tới sink -> JSONL của sink tăng dòng. FAKE_SINK_ROWS=0: sink không nhận gì (B4).
    [ "${FAKE_SINK_ROWS:-1}" = 0 ] || { j=${FAKE_JSONL:-DOC/testing/local-sink/events.jsonl}; mkdir -p "$(dirname "$j")"; echo '{"event_name": "ad_impression"}' >> "$j"; };;
  "logcat -d --pid="*)
    if grep -q "^shell pm clear" "$ADB_CALLS"; then
      if [ "${FAKE_POST_LOG:-}" != empty ]; then
        [ "${FAKE_AF_STARTED:-yes}" = no ] || echo "09-24 19:00:02.000  4242  4242 I test.sdk.zd: appsflyer started after consent, tier=NONE, start=activity"
        { [ "${FAKE_ARMED:-yes}" = no ] || [ "${FAKE_ARMED:-yes}" = cold2no ]; } || echo "09-24 19:00:02.001  4242  4242 I AppPluginDemo: strictmode armed: detectDiskReads detectDiskWrites penaltyLog"
        echo "09-24 19:00:02.002  4242  4242 I AppPlugin: telemetry off: test"
        [ -z "${FAKE_COLD2_LOG:-}" ] || cat "$FAKE_COLD2_LOG"
      fi
    elif grep -q "^shell monkey" "$ADB_CALLS"; then
      [ "${FAKE_POST_LOG:-}" = empty ] || echo "09-24 19:00:01.000  4242  4242 I test.sdk.zd: after monkey"
    else
      echo "09-24 19:00:00.000  4242  4242 I test.sdk.zd: cold start"
      [ "${FAKE_ARMED:-yes}" = no ] || echo "09-24 19:00:00.001  4242  4242 I AppPluginDemo: strictmode armed: detectDiskReads detectDiskWrites penaltyLog"
      if [ "${FAKE_DECOY:-}" = 1 ]; then
        echo "09-24 19:00:00.0005  4242  4242 D Other: main-thread ms=99 optional=77 initPlugin ms: total=99 other"
      fi
      echo "09-24 19:00:00.002  4242  4242 D AppPlugin: prefs warmup waited=3 ms (timeout=250 ms)"
      echo "09-24 19:00:00.003  4242  4242 D AppPlugin: AppPluginBase.onCreate main-thread ms=31"
      [ "${FAKE_INITPLUGIN:-yes}" = no ] || printf '%s\r\n' "09-24 19:00:00.0035  4242  4242 D AppPlugin: initPlugin ms: manager=5 meta=8 helper+gate=2 logevent=10 total=25"
      echo "09-24 19:00:00.004  4242  4243 D AppPlugin: prefs warmup loaded: base=12 ms, optional=9 ms"
      [ -z "${FAKE_COLD_LOG:-}" ] || cat "$FAKE_COLD_LOG"
    fi;;
esac
exit 0
EOF
  chmod +x "$TMP/bin/adb"
  : > "$TMP/adb-calls"
}
run_gate() {
  local dflt=()
  case " $* " in *" --apk "*) ;; *) [ -n "${RAW_ARGS:-}" ] || dflt=(--apk "$GATE_ROOT/apk/app-release.apk" --mapping "$GATE_ROOT/apk/mapping.txt");; esac
  ADB_CALLS=$TMP/adb-calls PATH="$TMP/bin:$PATH" ANDROID_SERIAL=stub-serial SEEDS=1 EVENTS=1 \
    FAKE_ADB_FULL="${FAKE_ADB_FULL:-}" FAKE_POST_LOG="${FAKE_POST_LOG:-}" FAKE_COLD_LOG="${FAKE_COLD_LOG:-}" \
    FAKE_COLD2_LOG="${FAKE_COLD2_LOG:-}" FAKE_AF_STARTED="${FAKE_AF_STARTED:-}" \
    FAKE_PM_CLEAR="${FAKE_PM_CLEAR:-}" FAKE_ARMED="${FAKE_ARMED:-}" FAKE_DECOY="${FAKE_DECOY:-}" FAKE_INITPLUGIN="${FAKE_INITPLUGIN:-}" FAKE_LOADAVG="${FAKE_LOADAVG:-}" FAKE_CRASH_POST="${FAKE_CRASH_POST:-}" \
    FAKE_SINK="${FAKE_SINK:-}" FAKE_DOCKER="${FAKE_DOCKER:-}" FAKE_SINK_ROWS="${FAKE_SINK_ROWS:-}" FAKE_JSONL="${FAKE_JSONL:-}" \
    FAKE_API="${FAKE_API:-}" FAKE_GETPROP="${FAKE_GETPROP:-}" \
    STRICT_ARMED_MIN="${STRICT_ARMED_MIN:-}" \
    JAVA="${FAKE_JAVA:-}" R8_JAR="${FAKE_R8_JAR:-}" \
    bash "$GATE_ROOT/tools/device_gate.sh" "$@" ${dflt[@]+"${dflt[@]}"} 2>&1
}
gate_log() { cat "$GATE_ROOT"/DOC/testing/device-gate-stub-serial-*.log 2>/dev/null; }

# 2.3.0 SECBH: adb giả bọc $TMP/bin/adb ($FAKE_MBIN): `am start -W ... --es policy_fake_token <t>` ghi dòng `policy:` vào logcat theo token + sink
# ($FAKE_PL_ST/down = sink dừng); FAKE_PL_NOAPPLY|DUP|DUPA(chỉ dòng applied)|L2_APPLIED|L4_APPLIED|LEAK đổi một hành vi; FAKE_AIRPLANE=1 -> `settings get global airplane_mode_on` in 1 tới khi ghi 0.
policy_fake_adb() {
  mkdir -p "$TMP/pbin"
  cat > "$TMP/pbin/adb" <<'EOF'
#!/usr/bin/env bash
ST=$FAKE_PL_ST; L=$ST/log; ap() { echo "09-30 06:00:00.000  4242  4242 I AppPlugin: $1" >> "$L"; }
applied() { ap "policy: applied version=policy_harness_230 source=app"; ap "BrainPolicy: đã nạp 'policy_harness_230'"; }
case "$*" in
  "shell am start -W"*) echo "$*" >> "$ADB_CALLS"; tok=$(printf '%s' "$*" | sed -n 's/.*policy_fake_token \([^ ]*\).*/\1/p')
    [ -z "$tok" ] || ap "adbd    : adbd service requested 'shell,v2,raw:am start -W --es policy_fake_token $tok'"   # logcat thật có dòng này: không được tính là rò token
    if [ -z "$tok" ]; then [ "${FAKE_PL_L4_APPLIED:-}" != 1 ] || applied
    elif [ -e "$ST/down" ]; then ap "policy: default (nonce_error)"
    elif [ "$tok" = fake-ok ]; then [ "${FAKE_PL_NOAPPLY:-}" = 1 ] || applied; [ "${FAKE_PL_DUP:-}" != 1 ] || applied; [ "${FAKE_PL_DUPA:-}" != 1 ] || ap "policy: applied version=policy_harness_230 source=app"; [ "${FAKE_PL_LEAK:-}" != 1 ] || ap "token=$tok"
    else ap "policy: default (not_accepted)"; [ "${FAKE_PL_L2_APPLIED:-}" != 1 ] || applied; fi
    printf 'Status: ok\nTotalTime: 100\n';;
  "logcat -c") echo "$*" >> "$ADB_CALLS"; : > "$L";;
  "logcat -d"|"logcat -d --pid="*) echo "$*" >> "$ADB_CALLS"; cat "$L";;
  "shell settings get global airplane_mode_on") echo "$*" >> "$ADB_CALLS"; [ -e "$ST/air0" ] && echo 0 || echo "${FAKE_AIRPLANE:-0}";;
  "shell settings put global airplane_mode_on 0") echo "$*" >> "$ADB_CALLS"; : > "$ST/air0";;
  *) exec "$FAKE_MBIN/adb" "$@";;
esac
EOF
  chmod +x "$TMP/pbin/adb"
}

# Kiểm một điều kiện; sai thì in đầu ra của ca để đọc được lý do trong log đỏ.
check() { # $1 = mô tả, $2 = exit của phép kiểm, $3 = đầu ra của ca
  total=$((total + 1))
  if [ "$2" -eq 0 ]; then echo "ok     - $1"; else bad=$((bad + 1)); echo "NOT OK - $1"; printf '%s\n' "$3" | sed 's/^/         | /'; fi
}
has() { printf '%s\n' "$1" | grep -qF -- "$2"; }
hasnt() { ! has "$1" "$2"; }

# Fixture dùng chung nhiều vùng (chuyển từ các mục gốc 2, 8, 13 và TF5).
GOOD_AL=$(allowlist good.txt '# chỉ # ở cột 0 là chú thích' '' \
  'ProviderInstaller: Failed to report request stats: java\.lang\.NoSuchMethodException: (com\.google\.android\.gms\.common\.security\.ProviderInstallerImpl\.)?reportRequestStats2' \
  'reportRequestStats2 \[class android\.content\.Context')
STRICT_LOG=$TMP/strict.log
cat > "$STRICT_LOG" <<'LOGEOF'
09-24 20:00:00.001  4242  4242 D StrictMode: StrictMode policy violation; ~duration=40 ms: android.os.strictmode.DiskReadViolation
09-24 20:00:00.001  4242  4242 D StrictMode: 	at android.os.StrictMode$AndroidBlockGuardPolicy.onReadFromDisk(StrictMode.java:1722)
09-24 20:00:00.001  4242  4242 D StrictMode: 	at android.content.ContextWrapper.getSharedPreferences(ContextWrapper.java:232)
09-24 20:00:00.001  4242  4242 D StrictMode: 	at za3.a(SourceFile:1)
09-24 20:00:00.001  4242  4242 D StrictMode: 	at yb2.onCreate(SourceFile:4)
09-24 20:00:00.001  4242  4242 D StrictMode: 	at com.app.plugin.demo.DemoApplication.onCreate(SourceFile:1)
09-24 20:00:00.002  4242  4242 D StrictMode: StrictMode policy violation; ~duration=54 ms: android.os.strictmode.DiskReadViolation
09-24 20:00:00.002  4242  4242 D StrictMode: 	at android.os.StrictMode$AndroidBlockGuardPolicy.onReadFromDisk(StrictMode.java:1722)
09-24 20:00:00.002  4242  4242 D StrictMode: 	at android.graphics.Typeface.setFlipFonts(Typeface.java:2015)
09-24 20:00:00.002  4242  4242 D StrictMode: 	at android.app.Application.onCreate(Application.java:275)
09-24 20:00:00.002  4242  4242 D StrictMode: 	at yb2.onCreate(SourceFile:1)
09-24 20:00:00.002  4242  4242 D StrictMode: 	at com.app.plugin.demo.DemoApplication.onCreate(SourceFile:1)
09-24 20:00:00.003  4242  4242 D StrictMode: StrictMode policy violation; ~duration=5 ms: android.os.strictmode.DiskReadViolation
09-24 20:00:00.003  4242  4242 D StrictMode: 	at android.os.StrictMode$AndroidBlockGuardPolicy.onReadFromDisk(StrictMode.java:1722)
09-24 20:00:00.003  4242  4242 D StrictMode: 	at java.io.File.exists(File.java:829)
09-24 20:00:00.003  4242  4242 D StrictMode: 	at com.app.plugin.demo.MainActivity.onCreate(SourceFile:3)
09-24 20:00:01.000  4242  4242 I test.sdk.zd: ok
LOGEOF
STRICT_MAP=$TMP/mapping.txt
cat > "$STRICT_MAP" <<'MAPEOF'
# compiler: R8
com.app.plugin.until.PreferenceHelper -> za3:
    1:3:void init(android.content.Context):12:14 -> a
com.app.plugin.app.AppPluginBase -> yb2:
    1:10:void onCreate():45:54 -> onCreate
com.app.plugin.demo.DemoApplication -> com.app.plugin.demo.DemoApplication:
    1:5:void onCreate():29:33 -> onCreate
com.app.plugin.demo.MainActivity -> com.app.plugin.demo.MainActivity:
    1:5:void onCreate(android.os.Bundle):10:14 -> onCreate
com.app.plugin.brain.DecisionLogQueue -> zq1:
    1:4:int size():30:33 -> b
MAPEOF
# Thêm một vi phạm do SDK KHÔNG phải lần nạp prefs đầu (File.exists của zq1 = DecisionLogQueue): loại "other".
STRICT_LOG_OTHER=$TMP/strict-other.log
{ grep -v 'test.sdk.zd: ok' "$STRICT_LOG"; cat <<'LOGEOF'
09-24 20:00:00.004  4242  4242 D StrictMode: StrictMode policy violation; ~duration=7 ms: android.os.strictmode.DiskReadViolation
09-24 20:00:00.004  4242  4242 D StrictMode: 	at android.os.StrictMode$AndroidBlockGuardPolicy.onReadFromDisk(StrictMode.java:1722)
09-24 20:00:00.004  4242  4242 D StrictMode: 	at java.io.File.exists(File.java:829)
09-24 20:00:00.004  4242  4242 D StrictMode: 	at zq1.b(SourceFile:2)
09-24 20:00:00.004  4242  4242 D StrictMode: 	at yb2.onCreate(SourceFile:6)
09-24 20:00:00.004  4242  4242 D StrictMode: 	at com.app.plugin.demo.DemoApplication.onCreate(SourceFile:1)
09-24 20:00:01.000  4242  4242 I test.sdk.zd: ok
LOGEOF
} > "$STRICT_LOG_OTHER"
# Sau ấm hoá prefs (Task B): chỉ còn vi phạm (2) super.onCreate() và (3) app demo, không vi phạm nào do SDK.
STRICT_LOG_CLEAN=$TMP/strict-clean.log
awk '/StrictMode policy violation/ { n++ } n != 1' "$STRICT_LOG" > "$STRICT_LOG_CLEAN"
# java giả (không cần Android Studio, chạy được trên runner Linux): nhận đúng dòng lệnh
# `java -cp JAR com.android.tools.r8.retrace.Retrace MAP IN` và đổi "at <tên R8>." thành "at <tên gốc>.".
# FAKE_RETRACE_FAIL=1: exit 3 như retrace lỗi.
FAKE_JAVA=$TMP/fake-java
FAKE_R8_JAR=$TMP/fake-r8.jar
: > "$FAKE_R8_JAR"
cat > "$FAKE_JAVA" <<'JAVAEOF'
#!/usr/bin/env bash
[ "${FAKE_RETRACE_FAIL:-}" = 1 ] && { echo "retrace boom" >&2; exit 3; }
[ "$1" = -cp ] && [ "$3" = com.android.tools.r8.retrace.Retrace ] || { echo "fake-java: sai dòng lệnh: $*" >&2; exit 2; }
awk 'NR == FNR { if ($0 !~ /^[ #]/ && $2 == "->") { o = $3; sub(/:$/, "", o); m[o] = $1 }; next }
     { for (k in m) { i = index($0, "at " k "."); if (i) $0 = substr($0, 1, i + 2) m[k] substr($0, i + 3 + length(k)) } print }' "$4" "$5"
JAVAEOF
chmod +x "$FAKE_JAVA"
# adb giả cho pha A (--measure và daily_session.sh): nút theo uiautomator, mỗi tap của một định dạng ghi dòng
# "call show"; tap lẻ = nạp (onAdLoaded + ad_fill), tap chẵn = hiện (AdActivity, FA origin=app, ad_revenue vào
# JSONL). FAKE_MG_IMP=0: không có dòng FA ad_impression; FAKE_MG_FOCUS=lost: am start/BACK không đưa focus về app.
mkdir -p "$TMP/mbin"
cat > "$TMP/mbin/adb" <<'EOF'
#!/usr/bin/env bash
echo "$*" >> "$ADB_CALLS"
SA=aaaaaaaa-0000-4000-8000-00000000000a
IX="540 1350"; RX="540 1750"; WX="540 1950"   # WX = Show Rewarded Interstitial (1.9.0 Task 10)
# 1.8.0 Task 2: máy giả có API (FAKE_API, mặc định 34; 26 = `am start -W ... --activity-single-top` không bao giờ trả, TF5) và màn hình
# (FAKE_WM: phys = 1080x2400, override = 720x1600 hiệu lực, bad = wm size lỗi nên lib dùng 1080x2400): swipe ra ngoài màn hình không cuộn.
case "${FAKE_WM:-phys}" in override) SW=720; SH=1600;; *) SW=1080; SH=2400;; esac
taps() { grep -c "^shell input tap $1\$" "$ADB_CALLS"; }
row() { printf '{"event_name": "%s", "sequence_id": %s, "client_ts_ms": 1790740800%03d, "user_id": "u1", "session_id": "%s", "event_id": "%s-%s", "schema_version": 2, "received_ts_ms": 1790740810000, "app_id": "default"}\n' "$1" "$2" "$2" "$SA" "$1" "$2" >> "$FAKE_JSONL"; }
applog() {
  local i n
  echo "09-30 04:00:00.000  4242  4242 I AppPlugin: logevent: session_id=$SA"
  n=$(taps "$IX"); i=0
  while [ $i -lt "$n" ]; do i=$((i + 1))
    [ "${FAKE_MG_DROP:-0}" = 1 ] || echo "09-30 04:00:10.000  4242  4242 D AppPlugin: ads full helper full_default showInterstitial call show"
    [ $((i % 2)) -eq 1 ] && echo "09-30 04:00:11.000  4242  4242 D AppPlugin: AdsHelper onAdLoaded: pl=full_default opl=full_default format=interstitial platform=admob"
    [ $((i % 2)) -eq 0 ] && [ "${FAKE_MG_IMP:-1}" = 1 ] && echo "09-30 04:00:12.000  1206  2899 V FA-SVC  : Logging event: origin=app,name=ad_impression,params=Bundle[{value=0.0, ad_format=interstitial}]"
  done
  n=$(taps "$RX"); i=0
  while [ $i -lt "$n" ]; do i=$((i + 1))
    [ "${FAKE_MG_DROP:-0}" = 1 ] || echo "09-30 04:00:20.000  4242  4242 D AppPlugin: ads gift helper gift_default showRewarded call show"
    [ $((i % 2)) -eq 1 ] && echo "09-30 04:00:21.000  4242  4242 D AppPlugin: AdsHelper onAdLoaded: pl=gift_default opl=gift_default format=rewarded platform=admob"
    [ $((i % 2)) -eq 0 ] && [ "${FAKE_MG_IMP:-1}" = 1 ] && echo "09-30 04:00:22.000  1206  2899 V FA-SVC  : Logging event: origin=app,name=ad_impression,params=Bundle[{value=0.0, ad_format=rewarded}]"
  done
  n=$(taps "$WX"); i=0
  while [ $i -lt "$n" ]; do i=$((i + 1))
    [ "${FAKE_MG_DROP:-0}" = 1 ] || echo "09-30 04:00:30.000  4242  4242 D AppPlugin: ads rwi helper rwi_default showRwi call show"
    [ $((i % 2)) -eq 1 ] && echo "09-30 04:00:31.000  4242  4242 D AppPlugin: AdsHelper onAdLoaded: pl=rwi_default opl=rwi_default format=rewarded_interstitial platform=admob"
    [ $((i % 2)) -eq 0 ] && [ "${FAKE_MG_IMP:-1}" = 1 ] && echo "09-30 04:00:32.000  1206  2899 V FA-SVC  : Logging event: origin=app,name=ad_impression,params=Bundle[{value=0.0, ad_format=rewarded_interstitial}]"
  done
  return 0
}
focus() {
  local last c
  last=$(grep -E '^shell (input tap|am start|input keyevent)' "$ADB_CALLS" | tail -1)
  case "$last" in
    "shell input tap "*) c=${last#shell input tap }; [ $(( $(taps "$c") % 2 )) -eq 0 ] && c=ad || c=app;;
    *"--activity-clear-top"*|*KEYCODE_BACK*) [ "${FAKE_MG_FOCUS:-ok}" = lost ] && c=ad || c=app
      # FAKE_FOCUS_AFTER=k: focus chỉ về app từ lần thăm dumpsys thứ k sau lệnh về app (mô hình API 26 chậm)
      if [ -n "${FAKE_FOCUS_AFTER:-}" ]; then
        [ "$(awk '/^shell (am start|input keyevent)/{n=0} /^shell dumpsys window/{n++} END{print n+0}' "$ADB_CALLS")" -lt "$FAKE_FOCUS_AFTER" ] && c=ad
      fi;;
    *KEYCODE_HOME*) c=home;;
    *) c=app;;
  esac
  case $c in
    ad) echo "  mCurrentFocus=Window{1a2b u0 com.google.android.gms.ads.AdActivity}";;
    home) echo "  mCurrentFocus=Window{3c4d u0 com.google.android.apps.nexuslauncher/.NexusLauncherActivity}";;
    *) echo "  mCurrentFocus=Window{5e6f u0 test.sdk.zd/com.app.plugin.demo.MainActivity}";;
  esac
}
# FAKE_VP_AT=<khung>: danh sách cuộn được, nút FAKE_VP_TEXT chỉ hiện ở khung số đó (FAKE_VP_START mặc định 0, 4 khung
# 0..3). Swipe ngón đi lên (y1 > y2) = xuống dưới (+), ngón đi xuống = lên (-); có thời lượng >= 300 ms kéo chậm = 1 khung,
# không có hoặc < 300 ms là fling = 2 khung.
vp_swipe() {
  local pos f=$ADB_CALLS.pos step=2 d
  pos=$(cat "$f" 2>/dev/null || echo "${FAKE_VP_START:-0}")
  [ -n "$3" ] && [ "$3" -ge 300 ] && step=1
  d=1; [ "$1" -lt "$2" ] && d=-1
  pos=$((pos + d * step)); [ "$pos" -lt 0 ] && pos=0; [ "$pos" -gt 3 ] && pos=3
  echo "$pos" > "$f"
}
vp_xml() {
  local pos row=""
  pos=$(cat "$ADB_CALLS.pos" 2>/dev/null || echo "${FAKE_VP_START:-0}")
  [ "$pos" = "$FAKE_VP_AT" ] && row="<node index=\"1\" text=\"$FAKE_VP_TEXT\" bounds=\"[0,1300][1080,1400]\" />"
  echo "<?xml version=\"1.0\"?><hierarchy rotation=\"0\"><node index=\"0\" text=\"Filler\" bounds=\"[0,100][1080,200]\" />$row</hierarchy>"
}
case "$*" in
  install*) echo Success;;
  "reverse --list") [ "${FAKE_MG_REVERSE_STUCK:-0}" = 1 ] && echo "$ANDROID_SERIAL tcp:8089 tcp:8089";;
  "shell getprop ro.build.version.sdk") echo "${FAKE_API:-34}";;
  "shell am start -W"*"--activity-single-top"*) { [ "${FAKE_AM_HANG:-}" = single ] || [ "${FAKE_API:-34}" = 26 ]; } && exec sleep 30; printf 'Status: ok\n';;
  "shell am start -W"*) [ "${FAKE_AM_HANG:-}" = cold ] && exec sleep 30
    [ "${FAKE_AM_HANG:-}" = after ] && { printf 'Status: ok\nTotalTime: 777\nWaitTime: 780\n'; exec sleep 30; }
    printf 'Status: ok\nTotalTime: 100\nWaitTime: 110\n';;
  "shell pidof"*) echo 4242;;
  "shell pm clear"*) echo Success;;
  "shell wm density") echo "Physical density: 420";;
  "shell wm size") case "${FAKE_WM:-phys}" in
    phys) echo "Physical size: 1080x2400";;
    override) printf 'Physical size: 1080x2400\nOverride size: 720x1600\n';;
    *) echo "Error: no such service";;
  esac;;
  "shell input swipe "*) [ -n "${FAKE_VP_AT:-}" ] && [ "$4" -lt "$SW" ] && [ "$6" -lt "$SW" ] && [ "$5" -lt "$SH" ] && [ "$7" -lt "$SH" ] && vp_swipe $5 $7 $8;;
  "shell cat /sdcard/ui.xml") if [ -n "${FAKE_VP_AT:-}" ]; then vp_xml; else echo '<?xml version="1.0"?><hierarchy rotation="0"><node index="0" text="Privacy options" bounds="[0,100][1080,200]" /><node index="1" text="Show Interstitial" bounds="[0,1300][1080,1400]" /><node index="2" text="Show Rewarded" bounds="[0,1700][1080,1800]" /><node index="3" text="Show Rewarded Interstitial" bounds="[0,1900][1080,2000]" /></hierarchy>'; fi;;
  "shell dumpsys window"*) focus;;
  "shell input tap $IX") [ $(( $(taps "$IX") % 2 )) -eq 1 ] && row ad_fill "$(taps "$IX")" || { [ "${FAKE_MG_REV:-1}" = 1 ] && row ad_revenue "$(taps "$IX")"; };;
  "shell input tap $RX") [ $(( $(taps "$RX") % 2 )) -eq 1 ] && row ad_fill "5$(taps "$RX")" || { [ "${FAKE_MG_REV:-1}" = 1 ] && row ad_revenue "5$(taps "$RX")"; };;
  "shell input tap $WX") [ $(( $(taps "$WX") % 2 )) -eq 1 ] && row ad_fill "7$(taps "$WX")" || { [ "${FAKE_MG_REV:-1}" = 1 ] && row ad_revenue "7$(taps "$WX")"; };;
  "logcat -d"|"logcat -d --pid=4242") applog;;
esac
exit 0
EOF
chmod +x "$TMP/mbin/adb"
# daily_session.sh trong một cây tạm (tools thật + sink giả + backend thật qua symlink), adb giả ở trên, không ngủ.
prepare_daily() {
  prepare_gate "$TMP/good.txt"
  mkdir -p "$GATE_ROOT/DOC/testing/local-sink"
  : > "$GATE_ROOT/DOC/testing/local-sink/events.jsonl"
  : > "$TMP/adb-calls"
}
run_daily() {
  ADB_CALLS=$TMP/adb-calls FAKE_JSONL=$GATE_ROOT/DOC/testing/local-sink/events.jsonl PATH="$TMP/mbin:$TMP/bin:$PATH" \
    MG_NO_SLEEP=1 FAKE_MG_IMP="${FAKE_MG_IMP:-1}" FAKE_MG_FOCUS="${FAKE_MG_FOCUS:-ok}" FAKE_DOCKER="${FAKE_DOCKER:-}" \
    FAKE_MG_REV="${FAKE_MG_REV:-1}" FAKE_MG_DROP="${FAKE_MG_DROP:-0}" \
    bash "$GATE_ROOT/tools/daily_session.sh" --serial stub-serial "$@" 2>&1
}
# wd_run <giây> <lệnh…>: chạy nền, kill đúng con đó nếu quá hạn; đầu ra vào $TMP/wd.out; return 124 nếu bị kill.
wd_run() {
  local secs=$1 pid t=0; shift
  "$@" > "$TMP/wd.out" 2>&1 &
  pid=$!
  while kill -0 "$pid" 2>/dev/null; do
    if [ "$t" -ge $((secs * 5)) ]; then kill "$pid" 2>/dev/null; wait "$pid" 2>/dev/null; return 124; fi
    sleep 0.2; t=$((t + 1))
  done
  wait "$pid"
}
# tf5_run <env…> -- <hàm lib> [đối số]: gọi một hàm của measure_gate.sh với adb giả (danh sách cuộn tắt).
tf5_run() {
  : > "$TMP/adb-calls"
  (
    export ADB_CALLS="$TMP/adb-calls" PATH="$TMP/mbin:$PATH" MG_NO_SLEEP=1
    while [ "$#" -gt 0 ] && [ "${1#*=}" != "$1" ]; do export "$1"; shift; done
    "$@"
  )
}
tf5_lib() { bash -c '. "$1"; shift; "$@"' _ "$ROOT/tools/lib/measure_gate.sh" "$@"; }

# Dòng kết của mỗi file dg_*: PASS n/n (exit 0) hoặc FAIL bad/n (exit 1); runner cộng các n.
dg_finish() {
  echo
  if [ "$bad" -eq 0 ]; then echo "PASS $total/$total"; exit 0; fi
  echo "FAIL $bad/$total"
  exit 1
}
