# shellcheck shell=bash
# 1.8.0 Task 2 (D11, retro 1.7.0 TF5): mọi lệnh gọi tới adb trong 4 file measure_gate.sh, device_gate_r8.sh, device_gate.sh,
# daily_session.sh đi qua đây (tools/emu_boot.sh chưa: ngoài phạm vi Task 2, mang sang 1.8.x/1.9.0).
# Dùng: adb_b <giây> <đối số của adb…>   (vd: adb_b 60 shell pidof test.sdk.zd). Chạy adb ở nền, quá <giây> thì kill ĐÚNG
# tiến trình con đó (không pkill), in một dòng "FAIL: adb_b timeout <giây>s: <đối số>" ra stderr (và vào $ADB_B_LOG nếu có)
# rồi return 124. Không thử lại (never retry). Không đụng `timeout` (macOS không có), chạy được trên bash 3.2.
# Mức mặc định của tools: 60 s mỗi lệnh; `uiautomator dump` và `logcat -d` 30 s; install 120 s; monkey theo EVENTS.
# INT/TERM (Ctrl-C) trong lúc chờ: kill con rồi thoát 130/143; job nền của bash không tương tác bỏ qua SIGINT nên
# không có trap này thì adb mồ côi sống tiếp (R-TF5 m2). Trap chỉ có hiệu lực trong lúc một lệnh chạy, xong thì gỡ.
ADB_B_PID=""
adb_b_reap() {
  [ -n "$ADB_B_PID" ] || return 0
  kill "$ADB_B_PID" 2>/dev/null
  sleep 0.2
  ! kill -0 "$ADB_B_PID" 2>/dev/null || kill -9 "$ADB_B_PID" 2>/dev/null
  wait "$ADB_B_PID" 2>/dev/null
  ADB_B_PID=""
}
adb_b() {
  local max=$1 end rc msg tick=0.01 n=0; shift
  case $max in ''|*[!0-9]*) echo "FAIL: adb_b: <giây> phải là số nguyên dương (nhận '$max'): $*" >&2; return 2;; esac
  end=$((SECONDS + max + 1))
  trap 'adb_b_reap; exit 130' INT
  trap 'adb_b_reap; exit 143' TERM
  adb "$@" &
  ADB_B_PID=$!
  while kill -0 "$ADB_B_PID" 2>/dev/null; do
    if [ "$SECONDS" -ge "$end" ]; then
      adb_b_reap; trap - INT TERM
      msg="FAIL: adb_b timeout ${max}s: $*"
      echo "$msg" >&2
      [ -z "${ADB_B_LOG:-}" ] || echo "$msg" >> "$ADB_B_LOG"
      return 124
    fi
    sleep "$tick"; n=$((n + 1))
    [ "$n" -lt 30 ] || tick=0.1   # lệnh nhanh (đa số) chỉ mất ~10 ms chờ; lệnh chậm thăm thưa dần
  done
  wait "$ADB_B_PID"; rc=$?
  ADB_B_PID=""; trap - INT TERM
  return "$rc"
}
