# shellcheck shell=bash
# 1.7.0 Task 8 (plan-lint #26): host yên trước mỗi lần boot emulator + lấy mẫu bộ nhớ suốt cổng.
#   quiet_host_check          Docker 0, emulator khác 0, Gradle ngoài IDE 0, load1 <= QUIET_LOAD_MAX (mặc định 4).
#                             Chờ có hạn (thăm QUIET_POLL_SEC=30 s, tối đa QUIET_WAIT_MAX_SEC=900 s). Quá hạn: in
#                             "invalid (contention): ..." và return 3 (không phải FAIL của sản phẩm, không tính lượt).
#   quiet_host_sampler <log>  ghi vm_stat (Pages free, Pages occupied by compressor) + sysctl vm.swapusage mỗi
#                             QUIET_SAMPLE_SEC (mặc định 10) giây, chạy nền tới khi bị kill.
# Gradle daemon của Android Studio (cha là "Android Studio") và daemon rảnh (CPU < QUIET_DAEMON_CPU_MAX=5 %) được liệt kê
# theo pid, không đếm và không kill; chỉ daemon đang chạy việc mới là contention.

_qh_count() { pgrep -f "$1" 2>/dev/null | wc -l | tr -d ' '; }

# In "<số> <pid...>" của GradleDaemon không thuộc IDE; số = 0 khi sạch.
_qh_gradle_outside_ide() {
  local pid ppid pcmd cpu rss n=0 list=""
  for pid in $(pgrep -f GradleDaemon 2>/dev/null); do
    ppid=$(ps -o ppid= -p "$pid" 2>/dev/null | tr -d ' ')
    pcmd=""
    [ -z "$ppid" ] || pcmd=$(ps -o command= -p "$ppid" 2>/dev/null)
    cpu=$(ps -o %cpu= -p "$pid" 2>/dev/null | tr -d ' ')
    rss=$(ps -o rss= -p "$pid" 2>/dev/null | tr -d ' ')
    case "$pcmd" in
      *"Android Studio"*) echo "  ide-owned GradleDaemon pid=$pid (không đếm, không kill)" >&2;;
      *) if awk -v c="${cpu:-0}" -v m="${QUIET_DAEMON_CPU_MAX:-5}" 'BEGIN { exit !(c + 0 < m + 0) }'; then
           echo "  idle GradleDaemon pid=$pid cpu=${cpu}% rss=${rss}KB (không đếm, không kill)" >&2
         else echo "  busy GradleDaemon pid=$pid cpu=${cpu}% rss=${rss}KB (đếm)" >&2; n=$((n + 1)); list="$list $pid"; fi;;
    esac
  done
  echo "$n$list"
}

quiet_host_check() {
  local max=${QUIET_WAIT_MAX_SEC:-900} poll=${QUIET_POLL_SEC:-30} limit=${QUIET_LOAD_MAX:-4}
  local waited=0 docker qemu gradle load reasons gr
  while :; do
    docker=$(_qh_count Docker.app)
    qemu=$(_qh_count qemu-system)
    gr=$(_qh_gradle_outside_ide); gradle=${gr%% *}
    load=$(sysctl -n vm.loadavg 2>/dev/null | awk '{ gsub(/[{}]/, ""); print $1 }')
    reasons=""
    [ "${docker:-0}" -eq 0 ] || reasons="$reasons docker=$docker"
    [ "${qemu:-0}" -eq 0 ] || reasons="$reasons qemu=$qemu"
    [ "${gradle:-0}" -eq 0 ] || reasons="$reasons gradle_outside_ide=[${gr#* }]"
    if [ -z "$load" ]; then reasons="$reasons load1=unreadable"
    elif ! awk -v l="$load" -v m="$limit" 'BEGIN { exit !(l + 0 <= m + 0) }'; then reasons="$reasons load1=$load(>$limit)"
    fi
    if [ -z "$reasons" ]; then
      echo "quiet host: ok docker=0 qemu=0 gradle_outside_ide=0 load1=$load (waited ${waited}s)"
      return 0
    fi
    if [ "$waited" -ge "$max" ]; then
      echo "invalid (contention):$reasons after ${waited}s (limit ${max}s) — not a product FAIL, not counted; retry when the host is quiet"
      return 3
    fi
    echo "quiet host: waiting,$reasons (waited ${waited}s of ${max}s)"
    sleep "$poll"
    waited=$((waited + poll))
  done
}

quiet_host_sampler() {
  local out=$1 every=${QUIET_SAMPLE_SEC:-10}
  while :; do
    {
      echo "--- $(date '+%Y-%m-%d %H:%M:%S')"
      vm_stat 2>/dev/null | grep -E 'Pages free|Pages occupied by compressor'
      sysctl vm.swapusage 2>/dev/null
    } >> "$out"
    sleep "$every"
  done
}
