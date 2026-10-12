#!/usr/bin/env bash
# 2.4.0 CARRY-T (minors-230 #19, #30, #35): StrictMode khung MIUI (SM1/SM2), net_restore chỉ bật dữ liệu trên emulator (NR1/NR2), sink +0 info (SK1).
# adb giả: ghi lệnh vào $ADB_CALLS. Chạy: bash tools/tests/dg_carry_test.sh (hoặc qua runner device_gate_allowlist_test.sh, phần `carry`).
. "$(dirname "$0")/lib/dg_harness.sh" || exit 99
# SM: count_strict như cổng (java giả retrace bằng mapping của harness). Khung com.miui.server.* TRÊN khung SDK đầu tiên (stack thật E-real #13) vs DƯỚI nó.
sm_log() { # $1 = dòng miui chèn trước (above) hay sau (below) khung SDK za3.a
  local m='com.miui.server.AccessController.haveAccessControlPassword(AccessController.java:90)' h='09-24 20:00:00.001  4242  4242 D StrictMode:'
  { echo "$h StrictMode policy violation; ~duration=9 ms: android.os.strictmode.DiskReadViolation"
    echo "$h 	at android.os.StrictMode\$AndroidBlockGuardPolicy.onReadFromDisk(StrictMode.java:1722)"
    [ "$1" != above ] || echo "$h 	at $m"
    echo "$h 	at za3.a(SourceFile:1)"
    [ "$1" != below ] || echo "$h 	at $m"
    echo "$h 	at yb2.onCreate(SourceFile:4)"
    echo "$h 	at com.app.plugin.demo.DemoApplication.onCreate(SourceFile:1)"; }
}
sm_run() {
  sm_log "$1" > "$TMP/sm-$1.log"
  ( LOG=$TMP/sm.log; fail=0; JAVA=$FAKE_JAVA R8_JAR=$FAKE_R8_JAR; . "$LIB" || exit 99
    count_strict "cold start" "$TMP/sm-$1.log" "$STRICT_MAP"; echo "RESULT: $([ "$fail" -eq 0 ] && echo PASS || echo FAIL)"; exit "$fail" ) 2>&1
}
o=$(sm_run above); rc=$?
check "SM1 vi phạm có khung com.miui.server.* trên khung SDK đầu tiên -> 'by-sdk: 0 (+1 env MIUI)', by sdk=0, PASS (cũ: by-sdk 1, FAIL)" \
  "$(has "$o" 'by-sdk: 0 (+1 env MIUI)' && has "$o" 'by sdk=0' && has "$o" 'RESULT: PASS' && [ "$rc" -eq 0 ]; echo $?)" "$o"
o=$(sm_run below); rc=$?
check "SM2 khung MIUI nằm DƯỚI khung SDK (không che SDK) -> vẫn by sdk=1, không dòng env MIUI, FAIL" \
  "$(has "$o" 'by sdk=1' && hasnt "$o" 'env MIUI' && has "$o" 'RESULT: FAIL' && [ "$rc" -eq 1 ]; echo $?)" "$o"
{ sm_log above; sm_log none; } > "$TMP/sm-two.log"   # SM3: hai vi phạm trong một log; cờ MIUI của vi phạm 1 không được rò sang vi phạm 2
o=$( ( LOG=$TMP/sm.log; fail=0; JAVA=$FAKE_JAVA R8_JAR=$FAKE_R8_JAR; . "$LIB" || exit 99
  count_strict "cold start" "$TMP/sm-two.log" "$STRICT_MAP"; echo "RESULT: $([ "$fail" -eq 0 ] && echo PASS || echo FAIL)"; exit "$fail" ) 2>&1 ); rc=$?
check "SM3 hai vi phạm (1: MIUI trên khung SDK, 2: không MIUI) -> by sdk=1, 'by-sdk: 1 (+1 env MIUI)', FAIL (cờ miui reset mỗi vi phạm)" \
  "$(has "$o" 'by sdk=1' && has "$o" 'by-sdk: 1 (+1 env MIUI)' && has "$o" 'RESULT: FAIL' && [ "$rc" -eq 1 ]; echo $?)" "$o"
# NR: net_restore thật + adb_b thật + adb giả (airplane_mode_on=1); serial qua ANDROID_SERIAL như cổng.
mkdir -p "$TMP/nbin"
printf '%s\n' '#!/usr/bin/env bash' 'echo "$*" >> "$ADB_CALLS"' 'case "$*" in *"settings get global airplane_mode_on"*) echo 1;; esac' > "$TMP/nbin/adb"; chmod +x "$TMP/nbin/adb"
nr() { : > "$TMP/nr-calls"; ADB_CALLS=$TMP/nr-calls ANDROID_SERIAL=$1 NET_ARM=1 PATH="$TMP/nbin:$PATH" bash -c '. "$1"; . "$2"; net_restore' _ "$ROOT/tools/lib/adb_b.sh" "$ROOT/tools/lib/net_restore.sh" 2>&1; cat "$TMP/nr-calls"; }
o=$(nr 65c9b673)
check "NR1 serial máy thật 65c9b673 (airplane 1): tắt airplane + bật wifi, 0 lệnh 'svc data enable'" \
  "$(has "$o" 'settings put global airplane_mode_on 0' && has "$o" 'svc wifi enable' && hasnt "$o" 'svc data enable'; echo $?)" "$o"
o=$(nr emulator-5570)
check "NR2 emulator-5570 (airplane 1): có 'svc data enable' và 'svc wifi enable'" \
  "$(has "$o" 'svc data enable' && has "$o" 'svc wifi enable'; echo $?)" "$o"
# SK1: APK game (không phải utility) mà sink +0 -> không FAIL, in đúng dòng INFO.
printf '%s\n' '# chỉ để cổng nạp allowlist' > "$TMP/good.txt"; prepare_gate "$TMP/good.txt"
o=$(FAKE_ADB_FULL=1 FAKE_SINK_ROWS=0 run_gate --variant game); rc=$?
check "SK1 --variant game, sink +0 -> 'INFO: sink +0 (không hàng mới)', không 'FAIL: JSONL của sink'" \
  "$(has "$o" 'INFO: sink +0 (không hàng mới)' && hasnt "$o" 'FAIL: JSONL của sink'; echo $?)" "rc=$rc
$o"
dg_finish
