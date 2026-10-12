#!/usr/bin/env bash
# 1.7.0 Task 8 (plan-lint #26): boot emulator chỉ qua đây. Dùng: tools/emu_boot.sh --avd <a> --port <p>
# quiet_host_check trước; exit 3 = invalid (contention): host bận, không phải FAIL của sản phẩm, không tính lượt.
# In "emu pid=<n>" để lane kill đúng PID của mình (không pkill -f).
# 2.1.0 T0d: EMU_BOOT_MAX/EMU_PROP_T không phải số -> rc 2; sau boot_completed chờ emu_quiet_wait (load + GMS ổn định, trần 240 s -> rc 3 contention).
set -u
ROOT=$(cd "$(dirname "$0")/.." && pwd)
AVD=""; PORT=""
die() { echo "FAIL: $1"; exit 1; }
while [ $# -gt 0 ]; do
  case $1 in
    --avd) [ $# -ge 2 ] || die "--avd cần tên AVD"; AVD=$2; shift 2;;
    --port) [ $# -ge 2 ] || die "--port cần số cổng"; PORT=$2; shift 2;;
    *) die "đối số lạ '$1' (chỉ nhận --avd <a> --port <p>)";;
  esac
done
[ -n "$AVD" ] || die "thiếu --avd"
case $PORT in ''|*[!0-9]*) die "thiếu/sai --port ('$PORT')";; esac
SDK=${ANDROID_HOME:-$HOME/Library/Android/sdk}
EMU=${EMULATOR_BIN:-$SDK/emulator/emulator}
ADB=${ADB_BIN:-$SDK/platform-tools/adb}
SERIAL=emulator-$PORT
. "$ROOT/tools/lib/quiet_host.sh" || die "không nạp được tools/lib/quiet_host.sh"
. "$ROOT/tools/lib/adb_b.sh" || die "không nạp được tools/lib/adb_b.sh"
PATH="$(dirname "$ADB"):$PATH"   # adb_b gọi `adb` trong PATH: dùng đúng ADB_BIN
WAIT_S=${ADB_B:-180}   # 1.9.0 Task 0d: wait-for-device có hạn; 2.0.0 T0g (M2): trần cứng 180 s dù ADB_B lớn hơn
case $WAIT_S in ''|*[!0-9]*) WAIT_S=180;; esac; [ "$WAIT_S" -le 180 ] || WAIT_S=180
BOOT_MAX=${EMU_BOOT_MAX:-360}; PROP_T=${EMU_PROP_T:-30}   # 2.0.0 T0g (M4): getprop treo cũng có hạn tổng (360 s)
for v in EMU_BOOT_MAX EMU_PROP_T; do eval "x=\${$v:-}"; case ${x:-0} in *[!0-9]*) echo "FAIL: $v phải là số nguyên (có '$x')"; exit 2;; esac; done
. "$ROOT/tools/lib/emu_quiet.sh" || die "không nạp được tools/lib/emu_quiet.sh"
quiet_host_check; rc=$?
[ "$rc" -eq 0 ] || exit "$rc"
[ -x "$EMU" ] || die "không có emulator ($EMU)"
LOGDIR=$ROOT/DOC/testing; mkdir -p "$LOGDIR"
# Cờ chuẩn của các lượt thiết bị 1.6.4 (.superpowers/sdd/v164/t5-report.md:27, anr37-report.md:23): headless, guest
# lạnh mỗi lần (-read-only, -no-snapshot-load/-save), GPU phần mềm swiftshader_indirect để TotalTime/ANR so được.
EMU_ARGS=(-avd "$AVD" -read-only -no-window -no-audio -no-boot-anim -no-snapshot-load -no-snapshot-save -gpu swiftshader_indirect -port "$PORT")
echo "emulator cmd: $EMU ${EMU_ARGS[*]}"
nohup "$EMU" "${EMU_ARGS[@]}" > "$LOGDIR/emu-$PORT.log" 2>&1 < /dev/null &
EPID=$!
echo "emu pid=$EPID avd=$AVD serial=$SERIAL"
adb_b "$WAIT_S" -s "$SERIAL" wait-for-device || die "wait-for-device lỗi hoặc quá ${WAIT_S}s"
BOOT_END=$((SECONDS + BOOT_MAX))
while [ "$SECONDS" -lt "$BOOT_END" ]; do
  [ "$(adb_b "$PROP_T" -s "$SERIAL" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ] && { echo "boot completed $SERIAL"; emu_quiet_wait "$SERIAL"; exit $?; }
  kill -0 "$EPID" 2>/dev/null || die "emulator thoát trước khi boot xong (xem $LOGDIR/emu-$PORT.log)"
  sleep 2
done
die "sys.boot_completed không lên sau ${BOOT_MAX} s"
