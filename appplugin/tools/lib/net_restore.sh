# shellcheck shell=bash
# 2.3.0 SECBH (D18-230 b): cổng để emulator ở airplane mode (E-IM3b: chặng kế INVALID "network unreachable"). net_restore chạy ở cleanup
# (trap EXIT của device_gate.sh, cả khi cổng FAIL): airplane_mode_on=1 -> 0 + phát lại radio. Không đổi gì khi đã tắt. Cần adb_b; chỉ chạy sau khi cổng đã chạm máy (NET_ARM=1, đặt ở dòng getprop đầu tiên).
net_restore() {
  [ "${NET_ARM:-}" = 1 ] || return 0
  local a; a=$(adb_b 30 shell settings get global airplane_mode_on 2> /dev/null | tr -d '\r\n')
  [ "$a" = 1 ] || return 0
  adb_b 30 shell settings put global airplane_mode_on 0 > /dev/null 2>&1
  adb_b 30 shell cmd connectivity airplane-mode disable > /dev/null 2>&1
  adb_b 30 shell am broadcast -a android.intent.action.AIRPLANE_MODE --ez state false > /dev/null 2>&1
  adb_b 30 shell svc wifi enable > /dev/null 2>&1
  case "${ANDROID_SERIAL:-}" in   # 2.4.0 CARRY-T #30: dữ liệu di động chỉ bật trên emulator, không đụng SIM của máy thật
    emulator-*) adb_b 30 shell svc data enable > /dev/null 2>&1; echo "net_restore: airplane_mode_on 1 -> 0 (wifi/data enable)";;
    *) echo "net_restore: airplane_mode_on 1 -> 0 (wifi enable; máy thật: không bật dữ liệu di động)";;
  esac
}
