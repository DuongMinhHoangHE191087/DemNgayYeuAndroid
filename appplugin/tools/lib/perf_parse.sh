# shellcheck shell=bash
# 2.1.0 Task 0f (PERF): phân tích số đo cold start và `dumpsys meminfo`, dùng cho tools/perf_gate.sh và tools/tests/perf_test.sh.
# Cùng regex với dòng `metrics cold start` của tools/lib/device_gate_r8.sh (log_key_lines): `onCreate main-thread ms=N`,
# `initPlugin ms=manager=N meta=N helper+gate=N logevent=N total=N` (dòng logcat gốc: `initPlugin ms: manager=…`),
# `prefs warmup waited=N`, `timeouts=N` (dòng logcat gốc: đếm dòng `prefs warmup timeout`), `TotalTime ms=N` / `TotalTime: N`.
# Không có dòng onCreate -> return 1, không in gì (lượt INVALID). BSD sed/grep (macOS), bash 3.2.

# $1 = văn bản, $2 = ERE của khoá (không kèm "="): in số đầu tiên sau "<khoá>=", rỗng nếu không có.
perf_num() { printf '%s\n' "$1" | grep --text -oE -e "$2=[0-9]+" | head -1 | sed 's/.*=//'; }

# $1 = văn bản (log app hoặc dòng metrics), $2 = tên trường của initPlugin (manager|meta|helper\+gate|logevent|total).
perf_init_field() { perf_num "$(printf '%s\n' "$1" | grep --text -E 'initPlugin ms[:=]' | head -1)" "$2"; }

# $1 = số thứ tự lượt, $2 = đầu ra của am start -W (TotalTime/WaitTime), $3 = log app (hoặc dòng metrics), $4 = by_sdk.
# In MỘT dòng JSON; return 1 (không in) khi thiếu dòng onCreate.
perf_run_json() {
  local k=$1 am=$2 text=$3 bysdk=${4:-0} on tt wt w to f
  on=$(perf_num "$text" 'onCreate main-thread ms'); [ -n "$on" ] || return 1
  tt=$(printf '%s\n%s\n' "$am" "$text" | grep --text -oE 'TotalTime(: | ms=)[0-9]+' | head -1 | sed 's/.*[: =]//')
  wt=$(printf '%s\n' "$am" | grep --text -oE 'WaitTime: [0-9]+' | head -1 | sed 's/.*: //')
  w=$(perf_num "$text" 'prefs warmup waited'); to=$(perf_num "$text" 'timeouts')
  [ -n "$to" ] || to=$(printf '%s\n' "$text" | grep --text -c 'prefs warmup timeout')
  printf '{"run":%s,"total_ms":%s,"wait_ms":%s,"oncreate_ms":%s,"initplugin":{' "$k" "${tt:-null}" "${wt:-null}" "$on"
  for f in manager meta 'helper\+gate' logevent total; do
    printf '"%s":%s,' "$(printf '%s' "$f" | tr -d '\\' | tr '+' '_')" "$(perf_init_field "$text" "$f" | grep . || echo null)"
  done | sed 's/,$//'
  printf '},"prefs_waited_ms":%s,"prefs_timeouts":%s,"by_sdk":%s}\n' "${w:-null}" "${to:-0}" "$bysdk"
}

# $1 = file chứa đầu ra `dumpsys meminfo <pkg>`. In "pss_kb=N java_kb=N native_kb=N"; thiếu TOTAL PSS -> return 1.
# Nhãn (đo trên emulator API 37, fixture R10): "TOTAL PSS:   N   TOTAL RSS: …" (API 29+) hoặc "TOTAL   N   …" (API 26–28);
# "Java Heap:   N" và "Native Heap:   N" trong khối App Summary (PSS, kB).
perf_meminfo_kv() {
  local f=$1 pss java nat
  pss=$(grep --text -oE 'TOTAL PSS: +[0-9]+' "$f" | head -1 | sed 's/.*: *//')
  [ -n "$pss" ] || pss=$(grep --text -E '^ *TOTAL +[0-9]+' "$f" | head -1 | awk '{print $2}')
  [ -n "$pss" ] || return 1
  java=$(grep --text -E '^ *Java Heap: +[0-9]+' "$f" | head -1 | awk '{print $3}')
  nat=$(grep --text -E '^ *Native Heap: +[0-9]+' "$f" | head -1 | awk '{print $3}')
  echo "pss_kb=$pss java_kb=${java:-null} native_kb=${nat:-null}"
}
