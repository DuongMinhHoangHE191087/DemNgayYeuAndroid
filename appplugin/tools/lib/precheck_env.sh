# shellcheck shell=bash
# 2.2.0 Task 0c (D6-220, retro 11): tiền kiểm MÔI TRƯỜNG trước khi cài APK. Thiếu một điều kiện = `RESULT: INVALID (precheck)` (exit 4), không phải FAIL của sản phẩm.
#   precheck_env <serial>   in `precheck env net=<0|1> lock=<0|1> batt=<n> host=<awake|sleep-risk>`; return 0 khi cả bốn ổn, 1 khi thiếu. Cần adb_b đã nạp.
# Máy thật (serial không bắt đầu bằng `emulator-`): net=1 khi `dumpsys connectivity` có `ni{WIFI CONNECTED`; lock=1 khi `dumpsys window` có
# isKeyguardShowing/mShowingLockscreen/mDreamingLockscreen=true (không bao giờ nhập PIN); batt = `level:` của `dumpsys battery`, cần >= PRECHECK_BATT_MIN (30).
# Emulator: chỉ kiểm host (net=1 lock=0 batt=100 cố định, không hỏi máy). Host=awake khi `pgrep -x caffeinate` >= 1, hoặc `pmset -g` có `sleep 0` hay `sleep prevented by`.
# Định dạng dumpsys: fixture thật chụp từ Redmi 65c9b673 ở tools/tests/fixtures/precheck/.
PRECHECK_BATT_MIN=${PRECHECK_BATT_MIN:-30}
precheck_env() {
  local s=$1 net=1 lock=0 batt=100 host=sleep-risk out n
  case $s in emulator-*) ;; *)
    out=$(adb_b 30 shell dumpsys connectivity 2> /dev/null | tr -d '\r')
    n=$(printf '%s\n' "$out" | grep --text -cE 'ni\{WIFI CONNECTED'); [ "$n" -ge 1 ] && net=1 || net=0
    out=$(adb_b 30 shell dumpsys window 2> /dev/null | tr -d '\r')
    n=$(printf '%s\n' "$out" | grep --text -cE '(isKeyguardShowing|mShowingLockscreen|mDreamingLockscreen)=true'); [ "$n" -ge 1 ] && lock=1
    out=$(adb_b 30 shell dumpsys battery 2> /dev/null | tr -d '\r')
    batt=$(printf '%s\n' "$out" | sed -n 's/^ *level: *\([0-9][0-9]*\).*/\1/p' | head -1); batt=${batt:-0};;
  esac
  n=$(pgrep -x caffeinate 2> /dev/null | wc -l | tr -d ' ')
  { [ "${n:-0}" -ge 1 ] || pmset -g 2> /dev/null | grep --text -qE '^ *sleep +0( |$)|sleep prevented by'; } && host=awake
  echo "precheck env net=$net lock=$lock batt=$batt host=$host"
  [ "$net" = 1 ] && [ "$lock" = 0 ] && [ "$batt" -ge "$PRECHECK_BATT_MIN" ] && [ "$host" = awake ]
}
