# shellcheck shell=bash
# 2.3.0 SECBH (hợp đồng C): `device_gate.sh --policy` chạy client policy (SECBC) trên stack cục bộ fake mode, 4 chặng; mẫu tools/lib/drc_legs.sh.
# Nạp bởi device_gate.sh --policy SAU tools/lib/measure_gate.sh (mg_*); mọi lệnh tới máy qua adb_b; MG_NO_SLEEP=1 chỉ cho test. 0 ghi Firebase/GCP/RC.
# Sink riêng: cổng trống (tools/lib/free_port.sh), BRAIN_INTEGRITY_MODE=fake BRAIN_POLICY_DIR=<d> (<d>/default.json = fixture, version policy_harness_230),
# `reverse tcp:8089 tcp:<p>` (adb_b) (manifest demo trỏ 127.0.0.1:8089). Mỗi chặng: pm clear -> am start -W [--es policy_fake_token <t>] -> chờ dòng `policy:` -> đọc logcat.
# Dòng đọc (SDK ghi bằng Log.i, luật 26): `policy: applied version=<v> source=app` | `policy: default (<lý do>)` | `policy: rejected (<..>)` | `policy: error (<Lớp>)`
# và `BrainPolicy: đã nạp '<v>'`.
#   L1 fake-ok          -> đúng 1 `policy: applied version=policy_harness_230 source=app` + 1 `BrainPolicy: đã nạp 'policy_harness_230'`
#   L2 fake-bad-device  -> `policy: default (not_accepted)`, 0 dòng applied
#   L3 sink dừng        -> `policy: default (nonce_…)` hoặc `policy: error (…)`, 0 applied (FATAL 0)
#   L4 không extra      -> 0 dòng `policy: applied|default` (đường sản phẩm tắt: không có INTEGRITY_CLOUD_PROJECT)
# Mọi chặng: StrictMode by-sdk 0, FATAL/ANR/native 0, token không lọt vào logcat (trừ dòng `adbd service requested` của chính lệnh am start của cổng). Return 0 PASS, 1 FAIL. Đã in RESULT.
PL_VER=policy_harness_230
PL_FIX=${PL_FIX:-tools/tests/fixtures/policy/default.json}
PL_WAIT=${PL_WAIT:-15}   # x 2 s chờ dòng `policy:` đầu tiên của chặng
PL_RE='policy: (applied|default|rejected|error)'

pl_sink() {   # start|stop trên cổng PL_PORT, thư mục PL_DIR
  if [ "$1" = start ]; then BRAIN_INTEGRITY_MODE=fake BRAIN_POLICY_DIR="$PL_DIR" bash tools/local_sink.sh start --port "$PL_PORT" --dir "$PL_DIR" > /dev/null 2>&1
  else bash tools/local_sink.sh stop --port "$PL_PORT" --dir "$PL_DIR" > /dev/null 2>&1; fi
}
pl_n() { grep -cE -- "$2" "$1" 2> /dev/null; }   # số dòng khớp ERE $2 trong file $1

# pl_leg <tên> <token|""> : pm clear, mở (có extra khi có token), chờ dòng `policy:`, lưu log vào PL_F=<pre>-<tên>.log; return 1 nếu hỏng.
pl_leg() {
  local n=$1 tok=$2 i=0 out
  out=$(adb_b 60 shell pm clear "$MG_PKG" 2>&1 | tr -d '\r'); [ "$out" = Success ] || { mg_say "FAIL: $n: pm clear $MG_PKG: $out"; return 1; }
  adb_b 60 logcat -c > /dev/null 2>&1
  if [ -n "$tok" ]; then mg_start --es policy_fake_token "$tok" || return 1; else mg_start || return 1; fi
  if [ -n "$tok" ]; then   # chờ dòng đầu tiên; chặng không extra không có gì để chờ: đứng 15 s rồi đo 0
    while [ "$i" -lt "$PL_WAIT" ] && [ "$(adb_b 30 logcat -d 2> /dev/null | grep -cE -- "$PL_RE")" -lt 1 ]; do mg_sleep 2; i=$((i + 1)); done
  else mg_sleep 15; fi
  mg_sleep 2; PL_F=$PL_PRE-$n.log
  mg_save_logs "$PL_PRE-$n"
  count_strict "$n" "$PL_PRE-$n-app.log" "$MAP"
  mg_count_crashes "$n"
  [ "$(grep -v 'adbd service requested' "$PL_F" | grep -cE 'fake-ok|fake-bad-device')" -eq 0 ] || { mg_say "FAIL: token lọt vào logcat của app ($n, $PL_F): không bao giờ log token"; return 1; }
}

pl_body() {
  local f=0 a l fail=0   # `fail` do count_strict ghi (dynamic scope) — khai ở đây, không ở policy_run
  PL_PORT=$(bash tools/lib/free_port.sh) || { mg_say "FAIL: không chọn được cổng trống (tools/lib/free_port.sh)"; return 1; }
  PL_DIR=$PWD/DOC/testing/policy-sink-$PL_PORT
  mkdir -p "$PL_DIR" && cp "$PL_FIX" "$PL_DIR/default.json" || { mg_say "FAIL: không chép fixture $PL_FIX vào $PL_DIR"; return 1; }
  pl_sink start || { mg_say "FAIL: sink fake mode không lên (cổng $PL_PORT, $PL_DIR; log sink-$PL_PORT.log)"; return 1; }
  adb_b 60 reverse tcp:8089 "tcp:$PL_PORT" > /dev/null 2>&1 && REVERSE_ON=1 || { mg_say "FAIL: adb reverse tcp:8089 tcp:$PL_PORT lỗi"; return 1; }
  mg_say "policy: sink fake cổng $PL_PORT dir=$PL_DIR; 4 chặng L1-L4; 0 ghi Firebase/Remote Config"
  if pl_leg L1 fake-ok; then
    a=$(pl_n "$PL_F" "policy: applied version=$PL_VER source=app"); l=$(pl_n "$PL_F" "BrainPolicy: đã nạp '$PL_VER'")
    mg_say "policy L1 (fake-ok): applied=$a (cần 1), BrainPolicy nạp=$l (cần 1)"
    { [ "$a" -eq 1 ] && [ "$l" -eq 1 ]; } || { mg_say "FAIL: L1: cần đúng 1 'policy: applied version=$PL_VER source=app' và 1 dòng nạp (nhận $a/$l)"; f=1; }
  else f=1; fi
  if pl_leg L2 fake-bad-device; then
    a=$(pl_n "$PL_F" 'policy: applied'); l=$(pl_n "$PL_F" 'policy: default \(not_accepted\)')
    mg_say "policy L2 (fake-bad-device): default(not_accepted)=$l (cần >= 1), applied=$a (cần 0)"
    { [ "$a" -eq 0 ] && [ "$l" -ge 1 ]; } || { mg_say "FAIL: L2: token bị từ chối mà có $a dòng applied / $l dòng default(not_accepted)"; f=1; }
  else f=1; fi
  pl_sink stop
  if pl_leg L3 fake-ok; then
    a=$(pl_n "$PL_F" 'policy: applied'); l=$(pl_n "$PL_F" 'policy: (default \(nonce_|error \()')
    mg_say "policy L3 (sink dừng): default(nonce_)|error=$l (cần >= 1), applied=$a (cần 0)"
    { [ "$a" -eq 0 ] && [ "$l" -ge 1 ]; } || { mg_say "FAIL: L3: sink dừng mà có $a dòng applied / $l dòng default(nonce_)|error"; f=1; }
  else f=1; fi
  pl_sink start || { mg_say "FAIL: L4: sink không bật lại"; return 1; }
  if pl_leg L4 ""; then
    a=$(pl_n "$PL_F" 'policy: (applied|default)'); mg_say "policy L4 (không extra): policy: applied|default=$a (cần 0)"
    [ "$a" -eq 0 ] || { mg_say "FAIL: L4: không extra mà đường policy vẫn chạy ($a dòng applied|default)"; f=1; }
  else f=1; fi
  mg_say "FATAL=$MG_FATAL am_anr=$MG_ANR native=$MG_NATIVE (need 0/0/0); Firebase/Remote Config writes by this gate: 0"
  { [ "$MG_FATAL" -eq 0 ] && [ "$MG_ANR" -eq 0 ] && [ "$MG_NATIVE" -eq 0 ] && [ "$fail" -eq 0 ]; } || f=1
  { [ "${MG_CAPTURE_FAIL:-0}" -eq 0 ] && ! grep -q '^FAIL: adb_b timeout' "${MG_LOG:-/dev/null}"; } || f=1
  return "$f"
}

# $1 = tiền tố file, mapping = $MAP. Sink luôn dừng lúc ra (mọi đường). In "RESULT: PASS|FAIL"; return 0/1.
policy_run() {
  local rc; PL_PRE=$1
  pl_body; rc=$?
  [ -z "${PL_PORT:-}" ] || pl_sink stop
  mg_say "RESULT: $([ "$rc" -eq 0 ] && echo PASS || echo FAIL)"
  return "$rc"
}
