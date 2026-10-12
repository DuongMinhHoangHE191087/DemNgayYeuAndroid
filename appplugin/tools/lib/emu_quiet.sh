# shellcheck shell=bash
# 2.1.0 Task 0d (M04, M53, D10): emulator yên sau boot. Cần adb_b. Không thăm dịch vụ bootanim (rỗng dưới -no-boot-anim, treo tới trần).
#   emu_quiet_wait <serial>   sys.boot_completed = 1 + load1 <= EMU_QUIET_LOAD (mặc định 4) + versionName của GMS giống nhau hai lần
#                             cách EMU_QUIET_GAP s (mặc định 30; GMS tự cập nhật ~1,4 phút sau boot và giết app). Trần EMU_QUIET_MAX
#                             (mặc định 240 s): quá hạn in "invalid (contention): ..." và return 3 (không phải FAIL, không tính lượt).
emu_quiet_wait() {
  local ser=$1 max=${EMU_QUIET_MAX:-240} gap=${EMU_QUIET_GAP:-30} lim=${EMU_QUIET_LOAD:-4} t0=$SECONDS prev="" seen=0 cur load booted why=""
  while [ $((SECONDS - t0)) -lt "$max" ]; do
    booted=$(adb_b "${PROP_T:-30}" -s "$ser" shell getprop sys.boot_completed 2> /dev/null | tr -d '\r')
    load=$(sysctl -n vm.loadavg 2> /dev/null | awk '{ gsub(/[{}]/, ""); print $1 }')
    cur=$(adb_b 30 -s "$ser" shell dumpsys package com.google.android.gms 2> /dev/null | sed -n 's/^ *versionName=//p' | head -1 | tr -d '\r')
    why=""
    [ "$booted" = 1 ] || why="$why boot_completed=${booted:-?}"
    awk -v l="${load:-99}" -v m="$lim" 'BEGIN { exit !(l + 0 <= m + 0) }' || why="$why load1=${load:-?}(>$lim)"
    if [ -n "$why" ]; then seen=0
    elif [ "$seen" = 1 ] && [ "$cur" = "$prev" ]; then
      echo "quiet wait: ok boot_completed=1 load1=$load gms=${cur:-none} stable after $((SECONDS - t0))s"; return 0
    else why=" gms ${prev:-none} -> ${cur:-none}"; seen=1; fi
    echo "quiet wait: waiting,$why ($((SECONDS - t0))s of ${max}s)"
    prev=$cur; sleep "$gap"
  done
  echo "invalid (contention): emulator $ser không yên sau ${max}s,$why — not a product FAIL, not counted; retry on a quiet host"
  return 3
}
