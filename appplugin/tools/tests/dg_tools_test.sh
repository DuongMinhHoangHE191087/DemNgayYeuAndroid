#!/usr/bin/env bash
# Test không cần máy của cổng thiết bị — vùng tools: emu_boot, sink theo lane, adb_b, che bí mật trong log.
# Chạy: bash tools/tests/dg_tools_test.sh (hoặc qua runner device_gate_allowlist_test.sh). exit 0 = xanh.
. "$(dirname "$0")/lib/dg_harness.sh" || exit 99

# quiet_host: sysctl/pgrep giả trong $TMP/qbin (không phụ thuộc máy chạy test).
mkdir -p "$TMP/qbin"
cat > "$TMP/qbin/sysctl" <<'EOF'
#!/usr/bin/env bash
case "$*" in
  *vm.loadavg*) echo "{ ${FAKE_LOAD1:-7.00} 5.00 3.00 }";;
  *vm.swapusage*) echo "vm.swapusage: total = 1024.00M  used = 5.00M  free = 1019.00M  (encrypted)";;
esac
EOF
cat > "$TMP/qbin/pgrep" <<'EOF'
#!/usr/bin/env bash
[ -z "${FAKE_GRADLE_PID:-}" ] || case "$*" in *GradleDaemon*) echo "$FAKE_GRADLE_PID"; exit 0;; esac
exit 1
EOF
cat > "$TMP/qbin/ps" <<'EOF'
#!/usr/bin/env bash
case "$*" in
  *ppid=*) echo 1;;
  *%cpu=*) echo "${FAKE_CPU:-0.0}";;
  *rss=*) echo 123456;;
  *command=*) echo "java GradleDaemon";;
esac
EOF
chmod +x "$TMP/qbin/sysctl" "$TMP/qbin/pgrep" "$TMP/qbin/ps"
out=$(PATH="$TMP/qbin:$PATH" QUIET_WAIT_MAX_SEC=0 bash -c '. "$1"; quiet_host_check' _ "$ROOT/tools/lib/quiet_host.sh" 2>&1); rc=$?
check "quiet-load: load1 = 7 và hạn chờ 0 -> exit 3 + invalid (contention)" \
  "$([ "$rc" -eq 3 ] && has "$out" 'invalid (contention)'; echo $?)" "$out
exit=$rc"
HOSTLOG=$TMP/sampler.host
(PATH="$TMP/qbin:$PATH" QUIET_SAMPLE_SEC=1 bash -c '. "$1"; quiet_host_sampler "$2"' _ "$ROOT/tools/lib/quiet_host.sh" "$HOSTLOG" >/dev/null 2>&1 & spid=$!; sleep 2; kill "$spid" 2>/dev/null; wait "$spid" 2>/dev/null)
nswap=$(command grep -c swapusage "$HOSTLOG" 2>/dev/null)
check "sampler-swap: sampler ghi >= 1 dòng swapusage vào <LOG>.host" \
  "$([ "${nswap:-0}" -ge 1 ]; echo $?)" "swapusage lines=${nswap:-0}; $(cat "$HOSTLOG" 2>/dev/null)"

# Daemon Gradle ngoài IDE: BẬN (CPU >= ngưỡng) phải bị đếm (contention, exit 3, in pid + RSS); RẢNH thì chỉ liệt kê.
out=$(PATH="$TMP/qbin:$PATH" FAKE_LOAD1=1.00 FAKE_GRADLE_PID=4242 FAKE_CPU=80.0 QUIET_WAIT_MAX_SEC=0 bash -c '. "$1"; quiet_host_check' _ "$ROOT/tools/lib/quiet_host.sh" 2>&1); rc=$?
outidle=$(PATH="$TMP/qbin:$PATH" FAKE_LOAD1=1.00 FAKE_GRADLE_PID=4242 FAKE_CPU=0.1 QUIET_WAIT_MAX_SEC=0 bash -c '. "$1"; quiet_host_check' _ "$ROOT/tools/lib/quiet_host.sh" 2>&1); rcidle=$?
check "quiet-busy-daemon: GradleDaemon bận bị đếm (exit 3, pid + rss), daemon rảnh thì không (exit 0)" \
  "$([ "$rc" -eq 3 ] && has "$out" 'gradle_outside_ide=[4242]' && has "$out" 'rss=123456' && [ "$rcidle" -eq 0 ] && has "$outidle" 'rss=123456'; echo $?)" "$out
exit=$rc
idle: exit=$rcidle $outidle"
# emu_boot.sh: cờ emulator chuẩn 1.6.4 (.superpowers/sdd/v164/t5-report.md:27) + -no-snapshot-load, in cả dòng lệnh.
EB=$TMP/emuroot; mkdir -p "$EB/tools/lib" "$TMP/ebin"
cp "$ROOT/tools/emu_boot.sh" "$EB/tools/"; cp "$ROOT/tools/lib/quiet_host.sh" "$ROOT/tools/lib/adb_b.sh" "$EB/tools/lib/"; [ ! -f "$ROOT/tools/lib/emu_quiet.sh" ] || cp "$ROOT/tools/lib/emu_quiet.sh" "$EB/tools/lib/"; export EMU_QUIET_GAP=1   # 2.1.0 T0d: quiet-wait thăm cách 1 s trong test
cat > "$TMP/ebin/emulator" <<'EOF'
#!/usr/bin/env bash
echo "$*" > "$FAKE_EMU_ARGS"
EOF
cat > "$TMP/ebin/adb" <<'EOF'
#!/usr/bin/env bash
case "$*" in *sys.boot_completed*) echo 1;; esac
exit 0
EOF
chmod +x "$TMP/ebin/emulator" "$TMP/ebin/adb"
out=$(PATH="$TMP/qbin:$PATH" FAKE_LOAD1=1.00 FAKE_EMU_ARGS=$TMP/emu-args EMULATOR_BIN=$TMP/ebin/emulator ADB_BIN=$TMP/ebin/adb QUIET_WAIT_MAX_SEC=0 bash "$EB/tools/emu_boot.sh" --avd Pixel_8_Pro --port 5570 2>&1); rc=$?
for _ in 1 2 3 4 5 6 7 8 9 10; do [ -s "$TMP/emu-args" ] && break; sleep 0.3; done
miss=""
for f in -read-only -no-window -no-audio -no-boot-anim -no-snapshot-save -no-snapshot-load '-gpu swiftshader_indirect' '-avd Pixel_8_Pro' '-port 5570'; do
  has "$(cat "$TMP/emu-args" 2>/dev/null)" "$f" || miss="$miss [$f]"
done
check "emu-flags: emu_boot.sh chạy emulator với đủ cờ 1.6.4 (-read-only -no-window -gpu swiftshader_indirect -no-snapshot-load …) và in dòng lệnh" \
  "$([ "$rc" -eq 0 ] && [ -z "$miss" ] && has "$out" 'emulator cmd:'; echo $?)" "exit=$rc missing:$miss
args: $(cat "$TMP/emu-args" 2>/dev/null)
$out"

# ── 1.9.0 Task 0d: emu_boot có hạn, neo pgrep Docker, node tối thiểu 8x8, dòng cỡ màn hình ───────────────────
# E1: adb giả treo ở wait-for-device; emu_boot phải FAIL trong ~ADB_B giây + dòng "adb_b timeout" (cũ: treo vô hạn,
# nên chạy nền và tự kill con đó sau 20 s để test không treo theo).
mkdir -p "$TMP/hbin"
printf '#!/usr/bin/env bash\ncase "$*" in *wait-for-device*) exec sleep 40;; esac\nexit 0\n' > "$TMP/hbin/adb"; chmod +x "$TMP/hbin/adb"
e1_t0=$SECONDS
PATH="$TMP/qbin:$PATH" FAKE_LOAD1=1.00 FAKE_EMU_ARGS=$TMP/emu-args1 EMULATOR_BIN=$TMP/ebin/emulator ADB_BIN=$TMP/hbin/adb ADB_B=2 QUIET_WAIT_MAX_SEC=0 \
  bash "$EB/tools/emu_boot.sh" --avd Pixel_8_Pro --port 5571 > "$TMP/e1.out" 2>&1 &
e1_pid=$!
while kill -0 "$e1_pid" 2>/dev/null && [ $((SECONDS - e1_t0)) -lt 20 ]; do sleep 0.2; done
e1_hung=0; kill -0 "$e1_pid" 2>/dev/null && { e1_hung=1; kill "$e1_pid" 2>/dev/null; }
wait "$e1_pid" 2>/dev/null; e1_rc=$?; e1_dt=$((SECONDS - e1_t0)); e1_out=$(cat "$TMP/e1.out")
check "E1 emu_boot: adb treo o wait-for-device -> FAIL trong han (<= 15 s voi ADB_B=2), in 'adb_b timeout' (cu: treo vo han)" \
  "$([ "$e1_hung" -eq 0 ] && [ "$e1_rc" -ne 0 ] && [ "$e1_dt" -le 15 ] && has "$e1_out" 'adb_b timeout'; echo $?)" "hung=$e1_hung rc=$e1_rc dt=${e1_dt}s
$e1_out"
# E2: tien kiem Docker neo ^/Applications/Docker.app/Contents/ ở device_gate.sh và daily_session.sh (pgrep giả lọc cmdline giả bằng đúng regex).
e2_pgrep() { printf '#!/usr/bin/env bash\n[ "$1" = -f ] || exit 0\nprintf "%%s\\n" "${FAKE_PS:-}" | grep -E -- "$2" | sed "s/.*/1/"\nexit 0\n' > "$TMP/bin/pgrep"; chmod +x "$TMP/bin/pgrep"; }
E2_DECOY='/x/Applications/Docker.app/Contents/MacOS/com.docker.backend'; E2_REAL='/Applications/Docker.app/Contents/MacOS/com.docker.backend'
prepare_gate "$TMP/good.txt"; e2_pgrep
e2_g0=$(FAKE_PS="$E2_DECOY" run_gate)
e2_g1=$(FAKE_PS="$E2_REAL" run_gate)
prepare_daily; e2_pgrep
e2_d0=$(FAKE_PS="$E2_DECOY" run_daily)
e2_d1=$(FAKE_PS="$E2_REAL" run_daily)
check "E2 Docker neo: tien trinh /x/Applications/Docker.app/Contents/... KHONG dem (device_gate docker=0, daily khong FAIL Docker); /Applications/Docker.app/Contents/... dem 1 (cu: dem ca /x/...)" \
  "$(has "$e2_g0" 'precheck docker=0' && has "$e2_g1" 'precheck docker=1' && hasnt "$e2_d0" 'FAIL: Docker' && has "$e2_d1" 'FAIL: Docker'; echo $?)" "gate decoy: $e2_g0
gate real: $e2_g1
daily decoy: $e2_d0
daily real: $e2_d1"
# E3/E4: mg_first_visible bo node nho hon 8x8 px, nhan node 8x8 (dac ta).
e3_o=$(bash -c '. "$1"; printf "%s\n" "<node text=\"x\" bounds=\"[0,0][2,2]\" />" | mg_first_visible' _ "$ROOT/tools/lib/measure_gate.sh" 2>&1)
check "E3 mg_first_visible: node [0,0][2,2] (nho hon 8x8) khong hop le -> rong (cu: chon '1 1')" "$([ -z "$e3_o" ]; echo $?)" "out=[$e3_o]"
e4_o=$(bash -c '. "$1"; printf "%s\n" "<node text=\"x\" bounds=\"[0,0][8,8]\" />" | mg_first_visible' _ "$ROOT/tools/lib/measure_gate.sh" 2>&1)
check "E4 mg_first_visible: node 8x8 hop le (dac ta) -> tam '4 4'" "$([ "$e4_o" = "4 4" ]; echo $?)" "out=[$e4_o]"
# E5: mg_screen_init in dung mot dong 'screen size=<w>x<h> density=<d>' (giu dong 'man hinh WxH (wm size)').
e5_o=$(ADB_CALLS=$TMP/adb-calls FAKE_WM=phys PATH="$TMP/mbin:$PATH" bash -c '. "$1"; mg_screen_init' _ "$ROOT/tools/lib/measure_gate.sh" 2>&1)
e5_n=$(printf '%s\n' "$e5_o" | command grep -c '^screen size=1080x2400 density=420$')
check "E5 cong in 1 dong 'screen size=1080x2400 density=420' (wm size/wm density), van giu dong 'man hinh 1080x2400 (wm size)' (cu: 0 dong screen size=)" \
  "$([ "$e5_n" -eq 1 ] && has "$e5_o" 'màn hình 1080x2400 (wm size)'; echo $?)" "$e5_o"

# ── 1.8.0 Task 1 (D17): sink theo lane — --sink-port/--sink-dir của cổng, local_sink --port/--dir, pha C, daily_session ──
# P1: --sink-port 18089 -> adb reverse tcp:8089 tcp:18089 (phía máy giữ 8089), JSONL đọc ở --sink-dir của lane.
prepare_gate "$TMP/good.txt"
SD1=$GATE_ROOT/sinkA
out=$(FAKE_ADB_FULL=1 FAKE_JSONL="$SD1/events.jsonl" run_gate --sink-port 18089 --sink-dir "$SD1"); rc=$?
c1=$(cat "$TMP/adb-calls"); n81=$(printf '%s\n' "$c1" | command grep -c '^reverse tcp:8089 tcp:8089$')
check "P1 --sink-port 18089: reverse tcp:8089 tcp:18089, không reverse 8089, JSONL ở --sink-dir" \
  "$(has "$c1" 'reverse tcp:8089 tcp:18089' && [ "$n81" -eq 0 ] && has "$out" "jsonl=$SD1/events.jsonl" \
    && [ "$(tail -1 "$TMP/adb-calls")" = 'reverse --remove tcp:8089' ]; echo $?)" "rc=$rc
$out
adb calls: $c1"
# P2 (đặc tả, xanh cũ): không cờ -> mặc định 8089 và DOC/testing/local-sink như 1.7.0.
prepare_gate "$TMP/good.txt"
out=$(FAKE_ADB_FULL=1 run_gate); rc=$?
check "P2 mac dinh van tcp:8089 tcp:8089 va DOC/testing/local-sink" \
  "$(has "$(cat "$TMP/adb-calls")" 'reverse tcp:8089 tcp:8089' && has "$out" 'jsonl=DOC/testing/local-sink/events.jsonl'; echo $?)" "rc=$rc
$out
adb calls: $(cat "$TMP/adb-calls")"

# P3: local_sink.sh THẬT với uvicorn giả (máy chủ python3 chỉ /health + ghi POST vào BRAIN_JSONL_PATH), hai sink song song.
SR=$TMP/sinkroot; mkdir -p "$SR/tools" "$SR/backend"; cp "$ROOT/tools/local_sink.sh" "$SR/tools/"
cat > "$TMP/fake_sink_srv.py" <<'PYEOF'
import http.server, os, sys
port = int(sys.argv[sys.argv.index('--port') + 1])
class H(http.server.BaseHTTPRequestHandler):
    def log_message(self, *a): pass
    def do_GET(self):
        self.send_response(200); self.end_headers(); self.wfile.write(b'ok')
    def do_POST(self):
        n = int(self.headers.get('Content-Length', 0)); b = self.rfile.read(n)
        with open(os.environ['BRAIN_JSONL_PATH'], 'ab') as f: f.write(b + b'\n')
        self.send_response(200); self.end_headers(); self.wfile.write(b'ok')
http.server.HTTPServer(('127.0.0.1', port), H).serve_forever()
PYEOF
cat > "$TMP/fake_py" <<'PYEOF'
#!/usr/bin/env bash
[ "$1" = -c ] && exit 0
exec python3 "$FAKE_SRV" "$@"
PYEOF
chmod +x "$TMP/fake_py"
LSD_A=$TMP/lane-A; LSD_B=$TMP/lane-B
p3() { BACKEND_PY=$TMP/fake_py FAKE_SRV=$TMP/fake_sink_srv.py bash "$SR/tools/local_sink.sh" "$@" 2>&1; }
FP="$ROOT/tools/lib/free_port.sh"
PA=$(bash "$FP"); oa=$(p3 start --port "$PA" --dir "$LSD_A"); ra=$?
PB=$(bash "$FP"); ob=$(p3 start --port "$PB" --dir "$LSD_B"); rb=$?
sta=$(p3 status --port $PA); rsa=$?; stb=$(p3 status --port $PB); rsb=$?
curl -s -m 3 -X POST --data '{"a":1}' http://127.0.0.1:$PA/e > /dev/null 2>&1
curl -s -m 3 -X POST --data '{"b":1}' http://127.0.0.1:$PB/e > /dev/null 2>&1
curl -s -m 3 -X POST --data '{"b":2}' http://127.0.0.1:$PB/e > /dev/null 2>&1
la=$(cat "$LSD_A/events.jsonl" 2>/dev/null | wc -l | tr -d ' '); lb=$(cat "$LSD_B/events.jsonl" 2>/dev/null | wc -l | tr -d ' ')
pida=$(cat "$LSD_A/sink-$PA.pid" 2>/dev/null); pidb=$(cat "$LSD_B/sink-$PB.pid" 2>/dev/null)
osa=$(p3 stop --port $PA --dir "$LSD_A"); rxa=$?
h_a=$(curl -s -o /dev/null -m 2 -w '%{http_code}' http://127.0.0.1:$PA/health 2>/dev/null)
h_b=$(curl -s -o /dev/null -m 2 -w '%{http_code}' http://127.0.0.1:$PB/health 2>/dev/null)
b_alive=1; [ -n "$pidb" ] && kill -0 "$pidb" 2>/dev/null && b_alive=0
osb=$(p3 stop --port $PB --dir "$LSD_B"); rxb=$?
for lp in $(pgrep -f "$TMP/fake_sink_srv.py"); do kill "$lp" 2>/dev/null; done   # dọn nếu stop hỏng (chỉ máy chủ giả do test này bật)
check "P3 local_sink start --port cong-trong A --dir A va --port cong-trong B --dir B song song: hai status 200, hai JSONL rieng, stop dung PID" \
  "$([ "$ra" -eq 0 ] && [ "$rb" -eq 0 ] && [ "$rsa" -eq 0 ] && [ "$rsb" -eq 0 ] && [ "$la" -eq 1 ] && [ "$lb" -eq 2 ] \
    && [ -n "$pida" ] && [ -n "$pidb" ] && [ "$pida" != "$pidb" ] && [ "$rxa" -eq 0 ] && [ "$h_a" != 200 ] && [ "$h_b" = 200 ] \
    && [ "$b_alive" -eq 0 ] && [ "$rxb" -eq 0 ] && [ ! -f "$LSD_A/sink-$PA.pid" ] && [ ! -f "$LSD_B/sink-$PB.pid" ]; echo $?)" "A start rc=$ra: $oa
B start rc=$rb: $ob
status A rc=$rsa: $sta / B rc=$rsb: $stb
jsonl A=$la B=$lb pid A=$pida B=$pidb
stop A rc=$rxa: $osa (health A=$h_a B=$h_b, B alive rc=$b_alive)
stop B rc=$rxb: $osb"

# FP1: free_port.sh in cổng trong 1024..65535, python3 bind được, không 8097/18089/18090; hai gọi song song không lỗi.
f1=$(bash "$ROOT/tools/lib/free_port.sh" 2>&1); r1=$?; f2=$(bash "$ROOT/tools/lib/free_port.sh" 2>&1); r2=$?
fpok=1; for fp in "$f1" "$f2"; do case "$fp" in ''|*[!0-9]*) ;; *) { [ "$fp" -ge 1024 ] && [ "$fp" -le 65535 ] && [ "$fp" != 8097 ] && [ "$fp" != 18089 ] && [ "$fp" != 18090 ] \
  && python3 -c 'import socket,sys;s=socket.socket();s.bind(("127.0.0.1",int(sys.argv[1])))' "$fp" 2>/dev/null; } && fpok=0 || { fpok=1; break; };; esac; done
check "FP1 free_port.sh: hai lần gọi in số 1024..65535 (không 8097/18089/18090), python3 bind được" \
  "$([ "$r1" -eq 0 ] && [ "$r2" -eq 0 ] && [ "$fpok" -eq 0 ]; echo $?)" "f1=$f1 rc=$r1 f2=$f2 rc=$r2"

# P4: --sink-port ngoài 1024..65535 (hoặc không phải số): FAIL trước mọi lệnh adb, thông báo nhắc dải.
p4=0; p4out=""
for badp in 80 70000 abc; do
  prepare_gate "$TMP/good.txt"
  o=$(run_gate --sink-port "$badp"); r=$?
  p4out="$p4out
[$badp] rc=$r calls=$(cat "$TMP/adb-calls" 2>/dev/null): $o"
  { has "$o" '1024..65535' && has "$o" 'RESULT: FAIL' && [ "$r" -eq 1 ] && [ ! -s "$TMP/adb-calls" ]; } || p4=1
done
check "P4 --sink-port ngoai 1024..65535: FAIL truoc moi lenh adb, thong bao nhac '1024..65535'" "$p4" "$p4out"

# P5: --measure --sink-port 18089: pha C (và pha A) reverse đúng cổng lane, không reverse 8089 -> 8089.
prepare_gate "$TMP/good.txt"
SD5=$GATE_ROOT/sinkP5; mkdir -p "$SD5"; : > "$SD5/events.jsonl"
out=$(ADB_CALLS=$TMP/adb-calls FAKE_JSONL=$SD5/events.jsonl PATH="$TMP/mbin:$TMP/bin:$PATH" MG_NO_SLEEP=1 \
  ANDROID_SERIAL=stub-serial bash "$GATE_ROOT/tools/device_gate.sh" --variant utility --measure --sink-port 18089 --sink-dir "$SD5" \
  --apk "$GATE_ROOT/apk/app-release.apk" --mapping "$GATE_ROOT/apk/mapping.txt" 2>&1); rc=$?
c5=$(cat "$TMP/adb-calls"); nrev=$(printf '%s\n' "$c5" | command grep -c '^reverse tcp:8089 tcp:18089$'); nold=$(printf '%s\n' "$c5" | command grep -c '^reverse tcp:8089 tcp:8089$')
check "P5 measure pha C reverse lai dung cong lane" \
  "$(has "$out" 'measure: pha C' && [ "$nrev" -eq 2 ] && [ "$nold" -eq 0 ] && has "$out" "jsonl=$SD5/events.jsonl"; echo $?)" "rc=$rc reverse 18089=$nrev reverse 8089=$nold
$out
adb calls: $c5"

# P6: daily_session --sink-port/--sink-dir truyền xuống (reverse + JSONL của lane), cổng lạ bị chặn trước adb.
prepare_daily
SD6=$GATE_ROOT/sinkP6; mkdir -p "$SD6"; : > "$SD6/events.jsonl"
out=$(ADB_CALLS=$TMP/adb-calls FAKE_JSONL=$SD6/events.jsonl PATH="$TMP/mbin:$TMP/bin:$PATH" MG_NO_SLEEP=1 FAKE_MG_IMP=1 FAKE_MG_FOCUS=ok FAKE_MG_REV=1 FAKE_MG_DROP=0 \
  bash "$GATE_ROOT/tools/daily_session.sh" --serial stub-serial --sink-port 18089 --sink-dir "$SD6" 2>&1); rc=$?
c6=$(cat "$TMP/adb-calls"); n6=$(printf '%s\n' "$c6" | command grep -c '^reverse tcp:8089 tcp:18089$'); o6=$(printf '%s\n' "$c6" | command grep -c '^reverse tcp:8089 tcp:8089$')
prepare_daily
outb=$(ADB_CALLS=$TMP/adb-calls PATH="$TMP/mbin:$TMP/bin:$PATH" bash "$GATE_ROOT/tools/daily_session.sh" --serial stub-serial --sink-port 80 2>&1); rcb=$?
check "P6 daily_session --sink-port/--sink-dir truyen xuong" \
  "$([ "$rc" -eq 0 ] && [ "$n6" -eq 1 ] && [ "$o6" -eq 0 ] && has "$out" "jsonl=$SD6/events.jsonl" \
    && [ "$rcb" -eq 1 ] && has "$outb" '1024..65535' && [ ! -s "$TMP/adb-calls" ]; echo $?)" "rc=$rc reverse 18089=$n6 reverse 8089=$o6
$out
bad port: rc=$rcb $outb"
# ── 1.8.0 Task 2 (D11, D17): adb có hạn (adb_b), cổng cứng (JSONL +0, precheck), mg_start in TotalTime đã đọc, Ctrl-C ──
# adb treo giả: ghi PID của chính nó vào $HANG_PID rồi `exec sleep 29` (một tiến trình, kill đúng PID là đủ).
mkdir -p "$TMP/hbin"
cat > "$TMP/hbin/adb" <<'HANGEOF'
#!/usr/bin/env bash
echo $$ > "$HANG_PID"
exec sleep 29
HANGEOF
chmod +x "$TMP/hbin/adb"
# B1 (đặc tả, adb_b mới): adb_b 2 với adb giả treo -> exit 124 trong <= 4 s, đúng một dòng FAIL, không còn tiến trình con.
rm -f "$TMP/hang.pid"; SECONDS=0
b1=$(HANG_PID=$TMP/hang.pid PATH="$TMP/hbin:$PATH" bash -c '. "$1"; adb_b 2 shell hang' _ "$ROOT/tools/lib/adb_b.sh" 2>&1); b1rc=$?; b1t=$SECONDS
b1pid=$(cat "$TMP/hang.pid" 2>/dev/null); b1alive=1; [ -z "$b1pid" ] || ! kill -0 "$b1pid" 2>/dev/null || b1alive=0
check "B1 adb_b 2 voi adb gia treo: exit 124 <= 4 s, mot dong FAIL, khong con tien trinh" \
  "$([ "$b1rc" -eq 124 ] && [ "$b1t" -le 4 ] && [ -n "$b1pid" ] && [ "$b1alive" -eq 1 ] && has "$b1" 'adb_b timeout 2s: shell hang' && [ "$(printf '%s\n' "$b1" | wc -l | tr -d ' ')" -eq 1 ]; echo $?)" "rc=$b1rc t=${b1t}s pid=$b1pid alive=$b1alive out=$b1"

# B4: sink giả không nhận dòng nào (FAKE_SINK_ROWS=0) trên utility -> JSONL +0 -> RESULT: FAIL dù mọi chặng khác xanh.
prepare_gate "$TMP/good.txt"
out=$(FAKE_ADB_FULL=1 FAKE_SINK_ROWS=0 run_gate); rc=$?
check "B4 JSONL +0 tren utility: RESULT FAIL, exit 1, log ghi '(+0)' va ly do" \
  "$(has "$out" '(+0)' && has "$out" 'FAIL: JSONL của sink tăng 0 dòng' && has "$out" 'RESULT: FAIL' && hasnt "$out" 'RESULT: PASS' && [ "$rc" -eq 1 ]; echo $?)" "rc=$rc
$out"

# B5: cổng xanh in `precheck docker=0 sink=up` vào $LOG, ngay sau dòng 1 (dòng gate log) và trước lệnh install của log.
prepare_gate "$TMP/good.txt"
out=$(FAKE_ADB_FULL=1 run_gate); rc=$?
b5=$(gate_log | sed -n 2p)
check "B5 dong precheck docker=0 sink=up co trong log (dong 2, sau dong gate log)" \
  "$([ "$b5" = 'precheck docker=0 sink=up' ] && has "$out" 'RESULT: PASS'; echo $?)" "dong 2 = $b5
$(gate_log | head -4)"

# B6: Docker chạy -> chữ MỚI `precheck docker=1` + FAIL trước mọi lệnh adb (kiểm Docker cũ xanh sai lý do: chỉ so 'FAIL: Docker').
prepare_gate "$TMP/good.txt"
out=$(FAKE_DOCKER=1 run_gate); rc=$?
check "B6 precheck docker=1: FAIL truoc install, log co chu 'precheck docker=1'" \
  "$(has "$out" 'precheck docker=1' && has "$out" 'FAIL: Docker' && has "$out" 'RESULT: FAIL' && [ "$rc" -eq 1 ] && [ ! -s "$TMP/adb-calls" ]; echo $?)" "rc=$rc calls=$(cat "$TMP/adb-calls")
$out"

# B7: mg_start: adb đã in TotalTime rồi treo -> quá hạn vẫn in TotalTime đã đọc, rồi FAIL (R-TF5 m1).
SECONDS=0
tf5_run FAKE_AM_HANG=after MG_AMSTART_MAX=2 wd_run 15 tf5_lib mg_start; rc=$?
o=$(cat "$TMP/wd.out")
check "B7 mg_start qua han van in TotalTime da doc, roi FAIL (return 1, khong treo)" \
  "$([ "$rc" -eq 1 ] && has "$o" 'TotalTime: 777' && has "$o" 'FAIL: am start -W quá 2s' && [ "$SECONDS" -lt 10 ]; echo $?)" "rc=$rc t=${SECONDS}s
$o"

# B8 (đặc tả): Ctrl-C (SIGINT thật; preexec_fn đặt SIGINT mặc định cho bash dù test chạy từ job nền, nơi SIGINT bị bỏ qua) khi adb_b đang chờ
# -> bash thoát 130 và adb nền bị kill.
rm -f "$TMP/hang8.pid"
b8=$(HANG_PID=$TMP/hang8.pid PATH="$TMP/hbin:$PATH" ROOT_LIB="$ROOT/tools/lib/adb_b.sh" python3 -c '
import os, signal, subprocess, time
p = subprocess.Popen(["bash", "-c", ". \"$ROOT_LIB\"; adb_b 30 shell hang"], start_new_session=True,
                     preexec_fn=lambda: signal.signal(signal.SIGINT, signal.SIG_DFL), stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
for _ in range(100):
    if os.path.exists(os.environ["HANG_PID"]) and open(os.environ["HANG_PID"]).read().strip(): break
    time.sleep(0.05)
time.sleep(0.3)
os.kill(p.pid, signal.SIGINT)
try: rc = p.wait(timeout=10)
except subprocess.TimeoutExpired: p.kill(); rc = "timeout"
time.sleep(0.5)
pid = int(open(os.environ["HANG_PID"]).read().strip())
try: os.kill(pid, 0); alive = 1
except OSError: alive = 0
print("rc=%s alive=%s" % (rc, alive))
' 2>&1)
check "B8 Ctrl-C: khong con adb nen (bash thoat 130, tien trinh adb gia da bi kill)" \
  "$([ "$b8" = 'rc=130 alive=0' ]; echo $?)" "$b8"

# B9: nguồn: không còn dòng `adb <lệnh>` trần (ngoài tools/lib/adb_b.sh) trong tools/lib/*.sh, device_gate.sh, daily_session.sh.
b9files=$(ls "$ROOT"/tools/lib/*.sh | command grep -v '/adb_b\.sh$'; echo "$ROOT/tools/device_gate.sh"; echo "$ROOT/tools/daily_session.sh")
b9=$(command grep -nE '(^|[^_a-z])adb |"?\$\{?ADB\}?"? ' $b9files | command grep -v adb_b)
check "B9 nguon: 0 dong 'adb ' tran ngoai adb_b.sh trong tools/lib + device_gate.sh + daily_session.sh" \
  "$([ -z "$b9" ]; echo $?)" "$b9"

# B10 (R-T2 I1): `logcat -d -b crash` treo quá hạn 30 s của adb_b (bound cố định, không có env chỉnh): cổng phải FAIL và log ghi
# "FAIL: adb_b timeout", không được đếm bằng chứng rỗng thành 0 crash rồi PASS. Fake treo đúng một lần (~31 s).
prepare_gate "$TMP/good.txt"; rm -f "$TMP/adb-calls.hung"
out=$(FAKE_ADB_FULL=1 FAKE_CRASH_HANG=1 run_gate); rc=$?
check "B10 logcat -d -b crash treo qua han: RESULT FAIL, log co 'FAIL: adb_b timeout'" \
  "$(gate_log | command grep -q '^FAIL: adb_b timeout' && has "$out" 'FAIL: logcat crash (adb_b rc=124)' && has "$out" 'RESULT: FAIL' && hasnt "$out" 'RESULT: PASS' && [ "$rc" -eq 1 ]; echo $?)" "rc=$rc
$out"

# B11 (R-T2 I1): `logcat -c -b events` treo quá hạn 60 s (không có kiểm rc riêng): chỉ kiểm dòng "FAIL: adb_b timeout" trong log mới làm cổng FAIL.
prepare_gate "$TMP/good.txt"; rm -f "$TMP/adb-calls.hung2"
out=$(FAKE_ADB_FULL=1 FAKE_CLEAR_HANG=1 run_gate); rc=$?
check "B11 logcat -c -b events treo qua han: RESULT FAIL, log co 'FAIL: adb_b timeout'" \
  "$(gate_log | command grep -q '^FAIL: adb_b timeout' && has "$out" 'RESULT: FAIL' && hasnt "$out" 'RESULT: PASS' && [ "$rc" -eq 1 ]; echo $?)" "rc=$rc
$out"

# ── 1.9.0 Task 0c: che bí mật trong log lúc chụp (plan-lint #40, 6 mẫu). Cổng thật + adb giả in dòng chứa bí mật vào log cold start 1.
X_LOG=$TMP/x-cold.log
cat > "$X_LOG" <<'XEOF'
09-24 19:00:00.010  4242  4242 I FIS: token eyJhbGciOiJFUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.c2lnbmF0dXJlLXNpZw refresh=eyJhbGciOiJSUzI1NiIsImtpZCI6IjEifQ.eyJleHAiOjE3MDAwMDAwMDB9.c2Vjb25kLXNpZw ok
09-24 19:00:00.011  4242  4242 D Net: key=AIzaSyA1234567890abcdefghijklmnopqrstuv9 end
09-24 19:00:00.012  4242  4242 D Auth: access ya29.a0AfH6SMBxyz_1234-abcdEFGH done
09-24 19:00:00.013  4242  4242 D Key: -----BEGIN PRIVATE KEY-----
MIIEvQIBADANBgkqhkiG9w0BAQEFAASC
-----END PRIVATE KEY-----
09-24 19:00:00.014  4242  4242 D Http: Authorization: Bearer abcDEF123.ghi-jkl_mno/pq== sent
I Finsky  : [603] myn.b(930): Billing preferred account via installer for test.sdk.zd: [KpgcWE8CfZMjAJsr9KiXgobN7pBdgNumLMheYJUvr2g]
09-24 19:00:00.015  4242  4242 I test.sdk.zd: ordinary line stays as is
XEOF
prepare_gate "$TMP/good.txt"
FAKE_ADB_FULL=1 FAKE_COLD_LOG=$X_LOG run_gate > /dev/null
x_cold=$(cat "$GATE_ROOT"/DOC/testing/device-gate-app-stub-serial-*-cold1.log 2>/dev/null)
check "X1 log app cua gate khong con JWT eyJ... (Firebase Installations), thay <redacted-fis-token>" \
  "$(hasnt "$x_cold" 'eyJhbGci' && hasnt "$x_cold" 'eyJleHAi' && hasnt "$x_cold" 'eyJhbGciOiJSUzI1' && has "$x_cold" 'FIS: token <redacted-fis-token> refresh=<redacted-fis-token> ok' && has "$x_cold" '<redacted-fis-token>'; echo $?)" "$x_cold"
check "X2 khong con khoa AIza..., thay <redacted-api-key>" \
  "$(hasnt "$x_cold" 'AIzaSy' && has "$x_cold" '<redacted-api-key>'; echo $?)" "$x_cold"
check "X3 khong con ya29., thay <redacted-oauth-token>" \
  "$(hasnt "$x_cold" 'ya29.a0Af' && has "$x_cold" '<redacted-oauth-token>'; echo $?)" "$x_cold"
check "X4 khong con khoi PEM (dong dau + than khoa), thay <redacted-pem>" \
  "$(hasnt "$x_cold" '-----BEGIN' && hasnt "$x_cold" 'MIIEvQIB' && has "$x_cold" '<redacted-pem>'; echo $?)" "$x_cold"
check "X5 khong con Bearer <token>, thay <redacted-bearer>" \
  "$(hasnt "$x_cold" 'abcDEF123' && has "$x_cold" '<redacted-bearer>'; echo $?)" "$x_cold"
check "X6 (dac ta) dong thuong khong doi, dong co bi mat chi mat phan bi mat: dong ordinary + cold start con nguyen, dong FIS giu 'FIS: token ... ok', dong Finsky giu 'for test.sdk.zd:'" \
  "$(has "$x_cold" '09-24 19:00:00.015  4242  4242 I test.sdk.zd: ordinary line stays as is' && has "$x_cold" 'I test.sdk.zd: cold start' && printf '%s\n' "$x_cold" | command grep -q 'I FIS: token .* ok$' && has "$x_cold" 'Billing preferred account via installer for test.sdk.zd:'; echo $?)" "$x_cold"
x_files="$ROOT/tools/device_gate.sh $ROOT/tools/lib/measure_gate.sh $ROOT/tools/lib/iap_gate.sh"
x_src=$(command grep -hE 'logcat -d|CRASHLOG' $x_files)
# moi file: so dong CODE (khong phai chu thich) vua chup logcat -d / ghi CRASHLOG vua di qua "| redact_stream" (device 6 (T0d: +2 chup full-log), measure 3, iap 4 = 13), va 0 dong logcat -d ghi thang ra file
x_n() { command grep -E 'logcat -d|CRASHLOG' "$1" | command grep -v '^ *#' | command grep -c '| redact_stream'; }
x_raw=$(command grep -hE 'logcat -d.*>>? *"' $x_files | command grep -vc '| redact_stream')
x_per="$(x_n "$ROOT/tools/device_gate.sh")/$(x_n "$ROOT/tools/lib/measure_gate.sh")/$(x_n "$ROOT/tools/lib/iap_gate.sh")"
check "X7 13 cho chup di qua redact_stream, theo tung file device_gate/measure_gate/iap_gate = 6/3/4 (khong dem chu thich), 0 dong logcat -d ghi thang ra file" \
  "$([ "$x_per" = 6/3/4 ] && [ "$x_raw" -eq 0 ]; echo $?)" "per-file=$x_per raw_file_sites=$x_raw
$x_src"
check "X8 dong 'preferred account via installer for <pkg>: [...]' mat phan base64, thanh [<redacted-account-hash>]" \
  "$(hasnt "$x_cold" 'KpgcWE8CfZMj' && has "$x_cold" 'for test.sdk.zd: [<redacted-account-hash>]'; echo $?)" "$x_cold"

x9=$(printf '%s\n' 'note -----BE''GIN CERTIFICATE' 'E AndroidRuntime: FATAL EXCEPTION: main' 'E AndroidRuntime: Process: test.sdk.zd, PID: 4242' 'I am_anr: test.sdk.zd' 'W x: NoClassDefFoundError abcdefghijklmnopqrstuvwxyz' | bash -c '. "$1"; redact_stream' _ "$ROOT/tools/lib/redact.sh")
check "X9 BEGIN khong co END: chi che tu BEGIN toi het dong do; FATAL/Process/am_anr/NoClassDefFoundError o dong sau con nguyen, so dong giu 5" \
  "$(has "$x9" 'note <redacted-pem>' && has "$x9" 'FATAL EXCEPTION: main' && has "$x9" 'Process: test.sdk.zd' && has "$x9" 'I am_anr: test.sdk.zd' && has "$x9" 'NoClassDefFoundError abcdefghijklmnopqrstuvwxyz' && [ "$(printf '%s\n' "$x9" | wc -l | tr -d ' ')" -eq 5 ]; echo $?)" "$x9"
x10=$(printf '%s\n' 'pem=-----BE''GIN CERTIFICATE-----MIIabcdef-----END CERTIFICATE----- tail' 'E AndroidRuntime: FATAL EXCEPTION: main' 'I test.sdk.zd: ok' | bash -c '. "$1"; redact_stream' _ "$ROOT/tools/lib/redact.sh")
check "X10 BEGIN va END cung mot dong: chi dong do bi che (giu 'pem=' va ' tail'), cac dong sau con nguyen, so dong giu 3" \
  "$(has "$x10" 'pem=<redacted-pem> tail' && has "$x10" 'FATAL EXCEPTION: main' && has "$x10" 'I test.sdk.zd: ok' && [ "$(printf '%s\n' "$x10" | wc -l | tr -d ' ')" -eq 3 ]; echo $?)" "$x10"

x_red() { bash -c '. "$1"; redact_stream' _ "$ROOT/tools/lib/redact.sh"; }
x11=$(printf '%s\n' '-----BE''GIN A-----' 'MIIBbase64base64base64' 'FATAL EXCEPTION: main' 'MIIBbase64base64base64' '-----EN''D A-----' | x_red)
check "X11 khoi PEM co dong FATAL chen giua: dong 1/2 bi che, FATAL o dong 3 nguyen van (ket thuc khoi), dong 4 (ngoai khoi) in nguyen, dong 5 (END le) bi che, 5 dong" \
  "$([ "$(printf '%s\n' "$x11" | sed -n 1p)" = '<redacted-pem>' ] && [ "$(printf '%s\n' "$x11" | sed -n 2p)" = '<redacted-pem>' ] && [ "$(printf '%s\n' "$x11" | sed -n 3p)" = 'FATAL EXCEPTION: main' ] && [ "$(printf '%s\n' "$x11" | sed -n 4p)" = 'MIIBbase64base64base64' ] && [ "$(printf '%s\n' "$x11" | sed -n 5p)" = '<redacted-pem>' ] && [ "$(printf '%s\n' "$x11" | wc -l | tr -d ' ')" -eq 5 ]; echo $?)" "$x11"
x12=$(printf '%s\n' 'a -----BE''GIN RSA KEY----- xx' 'FATAL EXCEPTION: main' 'Bearer x eyJhbGciOiJFUzI1NiIsInR5cCI6IkpXVCJ9.abc' '-----BE''GIN X-----' 'MIIB' '-----EN''D X-----' 'tail line' | x_red)
check "X12 BEGIN le o dong 1 roi khoi that o dong 4..6: FATAL o dong 2 va 'tail line' o dong 7 con nguyen, 7 dong" \
  "$([ "$(printf '%s\n' "$x12" | sed -n 2p)" = 'FATAL EXCEPTION: main' ] && [ "$(printf '%s\n' "$x12" | sed -n 7p)" = 'tail line' ] && [ "$(printf '%s\n' "$x12" | sed -n 1p)" = 'a <redacted-pem>' ] && [ "$(printf '%s\n' "$x12" | wc -l | tr -d ' ')" -eq 7 ]; echo $?)" "$x12"


# ── 2.0.0 Task 0g: minor công cụ M1..M4, X13 ─────────────────────────────────────────────────────────────────
# M1: stop --port <p> KHONG --dir tim dung sink da start bang --dir <d> (cu: khong thay pid o thu muc mac dinh, sink song tiep).
M1D=$TMP/m1-lane
PM1=$(bash "$FP")   # cổng trống (không cố định) để hai runner song song không đụng
m1o=$(p3 start --port $PM1 --dir "$M1D"); m1r=$?
m1s=$(p3 stop --port $PM1); m1x=$?
m1h=$(curl -s -o /dev/null -m 2 -w '%{http_code}' http://127.0.0.1:$PM1/health 2>/dev/null)
for lp in $(pgrep -f "$TMP/fake_sink_srv.py"); do kill "$lp" 2>/dev/null; done
check "M1 local_sink start --port $PM1 --dir D roi stop --port $PM1 (khong --dir): rc 0, sink tat (health != 200), pid file D go (cu: khong dung, sink song)" \
  "$([ "$m1r" -eq 0 ] && [ "$m1x" -eq 0 ] && [ "$m1h" != 200 ] && [ ! -f "$M1D/sink-$PM1.pid" ] && has "$m1s" 'sink stopped'; echo $?)" "start rc=$m1r: $m1o
stop rc=$m1x: $m1s (health=$m1h)"

# M2: ADB_B=600 khong nang tran wait-for-device qua 180 s. adb_b gia ghi doi so dau cua tung loi goi.
M2=$TMP/m2root; mkdir -p "$M2/tools/lib"; cp "$ROOT/tools/emu_boot.sh" "$M2/tools/"; cp "$ROOT/tools/lib/quiet_host.sh" "$M2/tools/lib/"; [ ! -f "$ROOT/tools/lib/emu_quiet.sh" ] || cp "$ROOT/tools/lib/emu_quiet.sh" "$M2/tools/lib/"
cat > "$M2/tools/lib/adb_b.sh" <<'EOF'
adb_b() { echo "T=$1 $*" >> "$M2_LOG"; case "$*" in *boot_completed*) echo 1;; esac; return 0; }
EOF
m2o=$(M2_LOG=$TMP/m2.log PATH="$TMP/qbin:$PATH" FAKE_LOAD1=1.00 FAKE_EMU_ARGS=$TMP/emu-args2 EMULATOR_BIN=$TMP/ebin/emulator ADB_BIN=$TMP/ebin/adb ADB_B=600 QUIET_WAIT_MAX_SEC=0 \
  bash "$M2/tools/emu_boot.sh" --avd Pixel_8_Pro --port 5572 2>&1); m2r=$?
m2w=$(command grep -F 'wait-for-device' "$TMP/m2.log" 2>/dev/null | head -1)
check "M2 emu_boot ADB_B=600: wait-for-device van <= 180 s (cu: nang tran len 600)" \
  "$([ "$m2r" -eq 0 ] && has "$m2w" 'T=180 '; echo $?)" "rc=$m2r wait: $m2w
$m2o"

# M3: wm density co ca Override va Physical -> dong 'screen size=... density=' lay Override.
mkdir -p "$TMP/m3bin"
cat > "$TMP/m3bin/adb" <<'EOF'
#!/usr/bin/env bash
case "$*" in
  *"wm density"*) printf 'Physical density: 420\r\nOverride density: 560\r\n';;
  *"wm size"*) printf 'Physical size: 1080x2400\r\n';;
esac
exit 0
EOF
chmod +x "$TMP/m3bin/adb"
m3o=$(PATH="$TMP/m3bin:$PATH" bash -c '. "$1"; . "$2"; mg_say() { echo "$*"; }; mg_screen_init' _ "$ROOT/tools/lib/adb_b.sh" "$ROOT/tools/lib/measure_gate.sh" 2>&1)
check "M3 wm density co 'Override density: 560' va 'Physical density: 420' -> in 'screen size=1080x2400 density=560' (dac ta, Override thang)" \
  "$(has "$m3o" 'screen size=1080x2400 density=560'; echo $?)" "$m3o"

# M4: getprop sys.boot_completed treo -> emu_boot FAIL trong han (EMU_BOOT_MAX=4 s, EMU_PROP_T=1 s; cu: bo qua bien, 30 s moi lan, toi 180 vong).
printf '#!/usr/bin/env bash\ncase "$*" in *sys.boot_completed*) exec sleep 40;; esac\nexit 0\n' > "$TMP/hbin/adb4"; chmod +x "$TMP/hbin/adb4"
mkdir -p "$TMP/m4bin"; cp "$TMP/hbin/adb4" "$TMP/m4bin/adb"
printf '#!/usr/bin/env bash\nexec sleep 30\n' > "$TMP/m4bin/emulator"; chmod +x "$TMP/m4bin/emulator"   # emulator giả SỐNG (không thoát) để chỉ có hạn getprop quyết định
m4_t0=$SECONDS
PATH="$TMP/qbin:$PATH" FAKE_LOAD1=1.00 FAKE_EMU_ARGS=$TMP/emu-args4 EMULATOR_BIN=$TMP/m4bin/emulator ADB_BIN=$TMP/m4bin/adb EMU_BOOT_MAX=4 EMU_PROP_T=1 QUIET_WAIT_MAX_SEC=0 \
  bash "$EB/tools/emu_boot.sh" --avd Pixel_8_Pro --port 5573 > "$TMP/m4.out" 2>&1 &
m4_pid=$!
while kill -0 "$m4_pid" 2>/dev/null && [ $((SECONDS - m4_t0)) -lt 25 ]; do sleep 0.2; done
m4_hung=0; kill -0 "$m4_pid" 2>/dev/null && { m4_hung=1; kill "$m4_pid" 2>/dev/null; }
wait "$m4_pid" 2>/dev/null; m4_rc=$?; m4_dt=$((SECONDS - m4_t0)); m4_out=$(cat "$TMP/m4.out")
m4_emu=$(sed -n 's/^emu pid=\([0-9]*\) .*/\1/p' "$TMP/m4.out" | head -1); [ -z "$m4_emu" ] || kill "$m4_emu" 2>/dev/null   # dọn đúng emulator giả của test này
check "M4 emu_boot: getprop sys.boot_completed treo -> FAIL trong han (<= 20 s voi EMU_BOOT_MAX=4), rc != 0, nhac sys.boot_completed" \
  "$([ "$m4_hung" -eq 0 ] && [ "$m4_rc" -ne 0 ] && [ "$m4_dt" -le 20 ] && has "$m4_out" 'sys.boot_completed'; echo $?)" "hung=$m4_hung rc=$m4_rc dt=${m4_dt}s
$m4_out"

# X13: trong khoi PEM, dong KHONG co dau ':' (ke ca dong ngan, vd phan dem cuoi) bi che; dong co ':' ket thuc khoi va in nguyen.
x13=$(printf '%s\n' '-----BE''GIN PRIVATE KEY-----' 'MIIBshort' 'AQAB==' 'E Tag: after the block' 'AQAB==' | x_red)
check "X13 khoi PEM: 'MIIBshort' va 'AQAB==' (khong co ':') o dong 2/3 bi che; dong co ':' o dong 4 nguyen; dong 5 ngoai khoi nguyen; 5 dong" \
  "$([ "$(printf '%s\n' "$x13" | sed -n 2p)" = '<redacted-pem>' ] && [ "$(printf '%s\n' "$x13" | sed -n 3p)" = '<redacted-pem>' ] && [ "$(printf '%s\n' "$x13" | sed -n 4p)" = 'E Tag: after the block' ] && [ "$(printf '%s\n' "$x13" | sed -n 5p)" = 'AQAB==' ] && [ "$(printf '%s\n' "$x13" | wc -l | tr -d ' ')" -eq 5 ]; echo $?)" "$x13"

x14=$(printf '%s\n' '-----BE''GIN PRIVATE KEY-----' 'Process died unexpectedly here' 'MIIBshort' '-----BE''GIN X-----' 'FATAL EXCEPTION main' 'am_anr [0,123,com.x]' | x_red)
check "X14 (R-T0c-2 m1) trong khoi PEM: dong chu 'Process died unexpectedly here' (khong co ':') in nguyen va ket thuc khoi (dong sau 'MIIBshort' nguyen); 'FATAL EXCEPTION main' va 'am_anr [...]' khong co ':' trong khoi cung nguyen; 6 dong" \
  "$([ "$(printf '%s\n' "$x14" | sed -n 2p)" = 'Process died unexpectedly here' ] && [ "$(printf '%s\n' "$x14" | sed -n 3p)" = 'MIIBshort' ] && [ "$(printf '%s\n' "$x14" | sed -n 5p)" = 'FATAL EXCEPTION main' ] && [ "$(printf '%s\n' "$x14" | sed -n 6p)" = 'am_anr [0,123,com.x]' ] && [ "$(printf '%s\n' "$x14" | wc -l | tr -d ' ')" -eq 6 ]; echo $?)" "$x14"

# M5: start --dir D, sink do chet, start trong thu muc mac dinh, stop --port: con tro cu khong duoc chi ve D (cu: stop khong dung, /health van 200).
M5D=$TMP/m5-lane
PM5=$(bash "$FP")
p3 start --port $PM5 --dir "$M5D" > /dev/null; kill "$(cat "$M5D/sink-$PM5.pid" 2>/dev/null)" 2>/dev/null; sleep 1
m5s=$(p3 start --port $PM5); m5r=$?
m5x=$(p3 stop --port $PM5); m5xr=$?
m5h=$(curl -s -o /dev/null -m 2 -w '%{http_code}' http://127.0.0.1:$PM5/health 2>/dev/null)
for lp in $(pgrep -f "$TMP/fake_sink_srv.py"); do kill "$lp" 2>/dev/null; done
check "M5 con tro cu: start --dir D, sink chet, start (mac dinh) roi stop --port: rc 0, sink mac dinh tat (health != 200), khong con sink-$PM5.dir" \
  "$([ "$m5r" -eq 0 ] && [ "$m5xr" -eq 0 ] && [ "$m5h" != 200 ] && [ ! -e "$SR/DOC/testing/local-sink/sink-$PM5.dir" ]; echo $?)" "start rc=$m5r: $m5s
stop rc=$m5xr: $m5x (health=$m5h)"

# ── 2.1.0 Task 0d (M02, M04, M53, D10): emu_boot — kiểm env, quiet-wait (boot_completed + load1 + GMS versionName ổn định) ──
# adb giả: dumpsys package in versionName; FAKE_GMS_CHURN=1 tăng số bản mỗi lần đọc (GMS tự cập nhật liên tục).
mkdir -p "$TMP/qe"; cp "$TMP/ebin/emulator" "$TMP/qe/emulator"
cat > "$TMP/qe/adb" <<'EOF'
#!/usr/bin/env bash
case "$*" in
  *sys.boot_completed*) echo 1;;
  *"dumpsys package com.google.android.gms"*)
    n=$(cat "$FAKE_GMS_N" 2>/dev/null || echo 0)
    [ "${FAKE_GMS_CHURN:-0}" != 1 ] || { n=$((n + 1)); echo "$n" > "$FAKE_GMS_N"; }
    echo "    versionName=24.$n.00 (190400-1)";;
esac
exit 0
EOF
chmod +x "$TMP/qe/adb"
q_boot() { PATH="$TMP/qbin:$PATH" FAKE_LOAD1=${FAKE_LOAD1:-1.00} FAKE_EMU_ARGS=$TMP/emu-argsq FAKE_GMS_N=$TMP/gms-n EMULATOR_BIN=$TMP/qe/emulator ADB_BIN=$TMP/qe/adb QUIET_WAIT_MAX_SEC=0 bash "$EB/tools/emu_boot.sh" --avd Pixel_8_Pro --port 5574 2>&1; }
t_e1=$(EMU_BOOT_MAX=abc q_boot); t_e1rc=$?
check "T0d E1 EMU_BOOT_MAX=abc -> rc 2 + lý do nêu EMU_BOOT_MAX, không boot emulator (cũ: lỗi số học / chạy tiếp)" \
  "$([ "$t_e1rc" -eq 2 ] && has "$t_e1" 'EMU_BOOT_MAX' && hasnt "$t_e1" 'emulator cmd:'; echo $?)" "rc=$t_e1rc
$t_e1"
rm -f "$TMP/gms-n"
t_e2=$(q_boot); t_e2rc=$?
check "T0d E2 (đặc tả) quiet-wait: boot_completed + load1 thấp + GMS versionName giống nhau hai lần -> in 'quiet wait: ok', rc 0" \
  "$([ "$t_e2rc" -eq 0 ] && has "$t_e2" 'quiet wait: ok'; echo $?)" "rc=$t_e2rc
$t_e2"
rm -f "$TMP/gms-n"
t_e3t0=$SECONDS
t_e3=$(FAKE_GMS_CHURN=1 EMU_QUIET_MAX=4 q_boot); t_e3rc=$?; t_e3dt=$((SECONDS - t_e3t0))
check "T0d E3 GMS đổi bản liên tục -> rc 3 + invalid (contention) khi chạm trần EMU_QUIET_MAX (4 s trong test; mặc định 240 s), không treo" \
  "$([ "$t_e3rc" -eq 3 ] && has "$t_e3" 'invalid (contention)' && [ "$t_e3dt" -le 25 ]; echo $?)" "rc=$t_e3rc dt=${t_e3dt}s
$t_e3"
t_e4=$(grep -rl "init.svc.boot""anim" "$ROOT/tools" --exclude-dir=tests 2>/dev/null | tr '\n' ' ')
check "T0d E4 (đặc tả, census) tools/ (ngoài tests) không thăm init.svc.bootanim: rỗng dưới -no-boot-anim, treo tới trần" \
  "$([ -z "$t_e4" ]; echo $?)" "found in: $t_e4"

# 2.1.0 FC1: api_facade_check — BuildSha chỉ là NGOẠI LỆ cộng thêm: `A || { B && C; }`. Khối mới BuildSha có khai báo internal trong nguồn (A đúng) mà gradle/build-sha.gradle
# thiếu `internal object BuildSha {` (C sai) vẫn phải qua (bản cũ `(A || B) && C` FAIL); không có khai báo (A sai, C sai) vẫn FAIL.
fc="$TMP/fc"; mkdir -p "$fc/tools" "$fc/gradle" "$fc/api" "$fc/src/com/x"; cp "$ROOT/tools/api_facade_check.sh" "$fc/tools/"; echo '// không có mẫu' > "$fc/gradle/build-sha.gradle"
printf 'public final class com.x.Keep {\n}\n' > "$fc/api/appplugin-game.api"; printf 'public final class com.x.Keep {\n}\npublic final class com.x.BuildSha {\n}\n' > "$fc/api/curr.api"
git -C "$fc" init -q; git -C "$fc" add api/appplugin-game.api; git -C "$fc" -c user.name=t -c user.email=t@t commit -qm base
fc_run() { (cd "$fc" && BASELINE=HEAD CURR_API=api/curr.api SRC_DIR=src bash tools/api_facade_check.sh --module appplugin-game 2>&1; echo "rc=$?"); }
fc_no=$(fc_run); echo 'internal object BuildSha { }' > "$fc/src/com/x/B.kt"; fc_yes=$(fc_run)
check "FC1 facade: BuildSha mới + khai báo internal trong nguồn qua dù build-sha.gradle thiếu mẫu (A || {B && C}); thiếu khai báo thì FAIL" \
  "$(has "$fc_yes" 'new: com.x.BuildSha (cho phép' && has "$fc_yes" 'rc=0' && has "$fc_no" 'FAIL: lớp public mới' && has "$fc_no" 'rc=1'; echo $?)" "yes: $fc_yes
no: $fc_no"

# ── 2.2.0 Task 0c (D6-220, retro 11): precheck_env (Wi-Fi/keyguard/pin máy thật + host) và `device_gate.sh --app <pkg>/<activity>` ──────────────
# Fixture THẬT: dumpsys connectivity/window/battery từ Redmi 65c9b673 + pmset host (tools/tests/fixtures/precheck/); adb giả của harness phát lại, mỗi ca đổi đúng một giá trị.
p_inst() { grep -c '^install' "$TMP/adb-calls"; }
p1=$(PRECHECK_REAL=1 prepare_gate "$TMP/good.txt"; FAKE_NET=down run_gate); p1rc=$?; p1i=$(p_inst)
check "PC1 máy thật không có Wi-Fi CONNECTED -> 'net=0', RESULT: INVALID (precheck), exit 4, 0 lệnh install (cũ: cài APK rồi chạy)" \
  "$(has "$p1" 'precheck env net=0 lock=0 batt=100 host=awake' && has "$p1" 'RESULT: INVALID (precheck)' && [ "$p1rc" -eq 4 ] && [ "$p1i" -eq 0 ]; echo $?)" "rc=$p1rc installs=$p1i
$p1"
p2=$(PRECHECK_REAL=1 prepare_gate "$TMP/good.txt"; FAKE_KEYGUARD=true run_gate); p2rc=$?; p2i=$(p_inst)
check "PC2 keyguard đang hiện -> 'lock=1', INVALID (precheck), exit 4, 0 install" \
  "$(has "$p2" 'precheck env net=1 lock=1' && has "$p2" 'RESULT: INVALID (precheck)' && [ "$p2rc" -eq 4 ] && [ "$p2i" -eq 0 ]; echo $?)" "rc=$p2rc installs=$p2i
$p2"
p3=$(PRECHECK_REAL=1 prepare_gate "$TMP/good.txt"; FAKE_BATT=12 run_gate); p3rc=$?; p3i=$(p_inst)
check "PC3 pin 12 % (< 30) -> 'batt=12', INVALID (precheck), exit 4, 0 install" \
  "$(has "$p3" 'precheck env net=1 lock=0 batt=12' && has "$p3" 'RESULT: INVALID (precheck)' && [ "$p3rc" -eq 4 ] && [ "$p3i" -eq 0 ]; echo $?)" "rc=$p3rc installs=$p3i
$p3"
p4=$(PRECHECK_REAL=1 prepare_gate "$TMP/good.txt"; FAKE_HOST=sleep run_gate); p4rc=$?; p4i=$(p_inst)
check "PC4 host ngủ được (pmset không có 'sleep prevented by', không caffeinate) -> 'host=sleep-risk', INVALID (precheck), exit 4, 0 install" \
  "$(has "$p4" 'host=sleep-risk' && has "$p4" 'RESULT: INVALID (precheck)' && [ "$p4rc" -eq 4 ] && [ "$p4i" -eq 0 ]; echo $?)" "rc=$p4rc installs=$p4i
$p4"
p5=$(PRECHECK_REAL=1 prepare_gate "$TMP/good.txt"; FAKE_NET=down FAKE_BATT=5 run_gate --serial emulator-5570); p5rc=$?; p5i=$(p_inst)
check "PC5 emulator chỉ kiểm host: net/pin xấu của 'máy' bị bỏ qua -> 'net=1 lock=0 batt=100 host=awake', không INVALID, có install" \
  "$(has "$p5" 'precheck env net=1 lock=0 batt=100 host=awake' && hasnt "$p5" 'INVALID' && [ "$p5i" -ge 1 ]; echo $?)" "rc=$p5rc installs=$p5i
$p5"
a_calls() { cat "$TMP/adb-calls"; }
prepare_gate "$TMP/good.txt"; : > "$TMP/adb-calls"; run_gate --app com.example.x/.Main > /dev/null; a1=$(a_calls)
check "AP1 --app com.example.x/.Main -> 'am start -W -n com.example.x/.Main' (không force_eea, không test.sdk.zd) (cũ: đối số lạ)" \
  "$(printf '%s\n' "$a1" | grep -qx 'shell am start -W -n com.example.x/.Main' && hasnt "$a1" force_eea && hasnt "$a1" 'test.sdk.zd'; echo $?)" "$a1"
prepare_gate "$TMP/good.txt"; : > "$TMP/adb-calls"; a2=$(run_gate --app com.example.x/.Main --measure); a2rc=$?; a2c=$(a_calls)
check "AP2 --app + --measure -> FAIL 'chế độ chỉ cho demo', exit 1, 0 lệnh adb" \
  "$(has "$a2" 'chế độ chỉ cho demo' && [ "$a2rc" -eq 1 ] && [ -z "$a2c" ]; echo $?)" "rc=$a2rc calls=$a2c
$a2"
prepare_gate "$TMP/good.txt"; : > "$TMP/adb-calls"; a3=$(run_gate --app com.example.x); a3rc=$?; a3c=$(a_calls)
check "AP3 --app thiếu '/' -> FAIL '<pkg>/<activity>' trước mọi lệnh adb, exit 1" \
  "$(has "$a3" '<pkg>/<activity>' && [ "$a3rc" -eq 1 ] && [ -z "$a3c" ]; echo $?)" "rc=$a3rc calls=$a3c
$a3"
prepare_gate "$TMP/good.txt"; : > "$TMP/adb-calls"; run_gate > /dev/null; a4=$(a_calls)
check "AP4 (đặc tả) không --app = test.sdk.zd/com.app.plugin.demo.MainActivity + --ez force_eea true" \
  "$(printf '%s\n' "$a4" | grep -qx 'shell am start -W -n test.sdk.zd/com.app.plugin.demo.MainActivity --ez force_eea true'; echo $?)" "$a4"
prepare_gate "$TMP/good.txt"; : > "$TMP/adb-calls"; a5=$(run_gate --apk "$GATE_ROOT/apk/app-release.apk" --app com.example.x/.Main)
check "AP5 --app KHÔNG --mapping qua được parse + dòng 'by-sdk: không mapping' (cũ: FAIL --mapping bắt buộc)" \
  "$(has "$a5" 'by-sdk: không mapping' && hasnt "$a5" 'bắt buộc'; echo $?)" "$a5"

# T0e (IG-V): --app bỏ mặc định các kiểm chỉ của demo (canary StrictMode, af_app_id, sink 8089 + sink growth) = dòng info; kiểm bắt buộc giữ.
app_fx() { FAKE_ADB_FULL=1 FAKE_ARMED=no FAKE_AF_STARTED=no FAKE_SINK_ROWS=0 "$@"; }
prepare_gate "$TMP/good.txt"; a6=$(FAKE_SINK=down app_fx run_gate --app com.example.x/.Main); a6rc=$?
check "AP6 --app không sink, không canary, không af_app_id -> PASS; dòng info 'sink: không dùng (--app)', n=0 (need >= 0) (cũ: FAIL canary/af/sink)" \
  "$(has "$a6" 'RESULT: PASS' && [ "$a6rc" -eq 0 ] && has "$a6" 'sink: không dùng (--app)' && has "$a6" '(need >= 0)' && hasnt "$a6" 'sink=down' && hasnt "$a6" 'FAIL:'; echo $?)" "rc=$a6rc
$a6"
prepare_gate "$TMP/good.txt"; : > "$TMP/adb-calls"; a7=$(app_fx run_gate --app com.example.x/.Main --sink-port 8123); a7rc=$?
check "AP7 --app --sink-port: sink được kiểm (reverse tcp:8089 tcp:8123, growth 0 dòng -> FAIL telemetry) " \
  "$(has "$a7" 'FAIL: JSONL của sink tăng 0 dòng' && [ "$a7rc" -eq 1 ] && has "$(a_calls)" 'reverse tcp:8089 tcp:8123'; echo $?)" "rc=$a7rc
$a7"
prepare_gate "$TMP/good.txt"; a7b=$(FAKE_SINK=down FAKE_ADB_FULL=1 run_gate --app com.example.x/.Main --sink-port 8123)
check "AP7b --app --sink-port mà sink không chạy -> FAIL tiền kiểm 'sink cục bộ không chạy'" \
  "$(has "$a7b" 'sink cục bộ không chạy'; echo $?)" "$a7b"
prepare_gate "$TMP/good.txt"; a8=$(FAKE_ADB_FULL=1 FAKE_ARMED=no run_gate); a8rc=$?
check "AP8 chế độ gói mặc định vẫn FAIL khi canary StrictMode thiếu (không đổi)" \
  "$(has "$a8" 'FAIL: StrictMode chưa bật' && [ "$a8rc" -eq 1 ]; echo $?)" "rc=$a8rc
$a8"
prepare_gate "$TMP/good.txt"; a9=$(FAKE_ADB_FULL=1 FAKE_SINK=down FAKE_CRASH_POST=1 app_fx run_gate --app test.sdk.zd/.Main)
check "AP9 --app vẫn giữ kiểm bắt buộc: FATAL của app -> FAIL" \
  "$(has "$a9" 'RESULT: FAIL' || has "$a9" 'RESULT: INVALID'; echo $?)" "$a9"

# ── 2.2.0 Task 0d: nợ công cụ (N10, N11, N17, N20; D15-220) ───────────────────────────────────────────────────────────────
# BP1: invalid_class dưới `trap '' PIPE` (nohup/nền) với log 2 MB mà dòng đầu là `>>> pkg <<<`: grep -q thoát sớm, `printf | grep -q` cũ in "printf: write error: Broken pipe" ra stderr.
bp_log="$TMP/bp1.log"; { echo '>>> test.sdk.zd <<<'; head -c 2097152 /dev/zero | tr '\0' 'x' | fold -w 200; } > "$bp_log"
bp_out=$(bash -c "trap '' PIPE; . '$ROOT/tools/lib/invalid_classes.sh'; invalid_class test.sdk.zd '$bp_log'" 2> "$TMP/bp1.err"); bp_err=$(wc -c < "$TMP/bp1.err" | tr -d ' ')
check "BP1 invalid_class: log 2 MB dòng đầu '>>> pkg <<<' dưới trap '' PIPE -> FAIL, stderr 0 byte (cũ: line 13: printf: write error: Broken pipe)" \
  "$([ "$bp_out" = FAIL ] && [ "$bp_err" = 0 ]; echo $?)" "out=$bp_out stderr=${bp_err}B: $(cat "$TMP/bp1.err")"
# SY1: dùng lại cây thử (cùng n_gate) không tạo $ROOT/backend/backend (ln -s vào symlink thư mục cũ đặt liên kết bên trong backend thật).
prepare_gate "$TMP/good.txt"; n_gate=$((n_gate - 1)); prepare_gate "$TMP/good.txt"
check "SY1 prepare_gate hai lần cùng n_gate -> không có backend/backend, backend vẫn là liên kết tới $ROOT/backend (cũ: ln -s tạo liên kết lồng)" \
  "$([ ! -e "$ROOT/backend/backend" ] && [ ! -L "$ROOT/backend/backend" ] && [ "$(readlink "$GATE_ROOT/backend")" = "$ROOT/backend" ]; echo $?)" "$(ls -la "$ROOT/backend/backend" 2>&1 | head -1)"
# FP1: ngoại lệ BuildSha chỉ cho gói com.app.plugin (cũ: tên đơn BuildSha ở gói nào cũng được miễn khi build-sha.gradle có mẫu).
printf '// mẫu\ninternal object BuildSha {\n' > "$fc/gradle/build-sha.gradle"; rm -f "$fc/src/com/x/B.kt"
printf 'public final class com.x.Keep {\n}\npublic final class com.x.BuildSha {\n}\n' > "$fc/api/curr.api"; fp_other=$(fc_run)
printf 'public final class com.x.Keep {\n}\npublic final class com.app.plugin.BuildSha {\n}\n' > "$fc/api/curr.api"; fp_own=$(fc_run)
check "FP1 facade: 'internal object BuildSha' trong build-sha.gradle chỉ miễn com.app.plugin.BuildSha; com.x.BuildSha vẫn FAIL (cũ: miễn mọi gói)" \
  "$(has "$fp_other" 'FAIL: lớp public mới' && has "$fp_other" 'rc=1' && has "$fp_own" 'new: com.app.plugin.BuildSha (cho phép' && has "$fp_own" 'rc=0'; echo $?)" "other: $fp_other
own: $fp_own"
# FS1/FS2: tools/facade-allow.txt (cột 1 = FQN, rồi dấu + lý do). Lớp public mới ngoài tệp = FAIL; lớp trong tệp (cả khối lồng Outer$Inner) = PASS + 'new: <lớp> (allow)'.
printf 'public final class com.x.Keep {\n}\npublic final class com.x.Seg {\n}\npublic final class com.x.Seg$Inner {\n}\n' > "$fc/api/curr.api"
printf '# chú thích\n\ncom.x.Other  seg  lý do khác\n' > "$fc/tools/facade-allow.txt"; fs_no=$(fc_run)
printf '# chú thích\n\ncom.x.Seg  seg  lý do: kiểu public có chủ đích\n' > "$fc/tools/facade-allow.txt"; fs_yes=$(fc_run)
check "FS1 facade: lớp public mới ngoài facade-allow.txt (tệp chỉ có com.x.Other) -> FAIL rc=1" \
  "$(has "$fs_no" 'FAIL: lớp public mới' && has "$fs_no" 'com.x.Seg' && hasnt "$fs_no" '(allow)' && has "$fs_no" 'rc=1'; echo $?)" "$fs_no"
check "FS2 facade: com.x.Seg trong facade-allow.txt (và khối lồng Seg\$Inner) -> PASS rc=0 + 'new: com.x.Seg (allow)'" \
  "$(has "$fs_yes" 'new: com.x.Seg (allow)' && has "$fs_yes" 'new: com.x.Seg$Inner (allow)' && hasnt "$fs_yes" 'FAIL' && has "$fs_yes" 'rc=0'; echo $?)" "$fs_yes"

# 2.2.0 IG: tools/integration_doc_check.sh kiểm INTEGRATION.md với dump API, khoá RC, mục bắt buộc.
IGDOC=$ROOT/docs/integration/INTEGRATION.md
draft() { cp "$IGDOC" "$TMP/draft.md"; printf '\n%s\n' "$1" >> "$TMP/draft.md"; bash "$ROOT/tools/integration_doc_check.sh" "$TMP/draft.md" 2>&1; }
dc1=$(draft 'Hằng cũ: `AdsHelper.REMOVE_ADS_ALL`'); dc1rc=$?
check "DC1 tài liệu nhắc AdsHelper.REMOVE_ADS_ALL -> FAIL 'không có trong api/appplugin.api' (cũ: không có checker)" \
  "$(has "$dc1" 'REMOVE_ADS_ALL không có trong api/appplugin.api'; echo $?)" "$dc1"
dc2=$(draft 'Khoá lạ: `seg_foo`')
check "DC2 khoá RC lạ seg_foo -> FAIL 'khoá RC seg_foo không có trong src/main'" \
  "$(has "$dc2" 'khoá RC seg_foo không có trong src/main'; echo $?)" "$dc2"
dc3=$(bash "$ROOT/tools/integration_doc_check.sh" 2>&1); dc3rc=$?
check "DC3 tài liệu thật PASS, có dòng tools:replace backup-rules của AppsFlyer" \
  "$([ "$dc3rc" -eq 0 ] && has "$dc3" 'integration_doc_check: PASS' && grep -qF 'tools:replace="android:dataExtractionRules,android:fullBackupContent"' "$IGDOC"; echo $?)" "rc=$dc3rc
$dc3"

# 2.2.0 IG fix 1: khoá meta-data, phiên bản plugin, tập resource app-contract, minSdk sống (không nằm trong khối TODO).
nodoc() { sed "s/$1/__gone__/g" "$IGDOC" > "$TMP/draft.md"; bash "$ROOT/tools/integration_doc_check.sh" "$TMP/draft.md" 2>&1; }
dc4=$(nodoc DATABUCKETS_BASE_URL)
check "DC4 bỏ DATABUCKETS_BASE_URL khỏi tài liệu -> FAIL 'khoá meta-data DATABUCKETS_BASE_URL'" \
  "$(has "$dc4" 'khoá meta-data DATABUCKETS_BASE_URL'; echo $?)" "$dc4"
dc5=$(sed 's/google-services") version "4.4.2"/google-services") version "9.9.9"/' "$IGDOC" > "$TMP/draft.md"; bash "$ROOT/tools/integration_doc_check.sh" "$TMP/draft.md" 2>&1)
check "DC5 plugin google-services sai phiên bản -> FAIL 'plugin com.google.gms.google-services'" \
  "$(has "$dc5" 'plugin com.google.gms.google-services'; echo $?)" "$dc5"
dc6=$(sed "s/@integer\/ads_admob_firebase_linked/__gone__/g; s/ads_admob_firebase_linked/__gone__/g" "$IGDOC" > "$TMP/draft.md"; bash "$ROOT/tools/integration_doc_check.sh" "$TMP/draft.md" 2>&1)
check "DC6 bỏ ads_admob_firebase_linked khỏi tài liệu -> FAIL 'resource app-contract ads_admob_firebase_linked'" \
  "$(has "$dc6" 'resource app-contract ads_admob_firebase_linked'; echo $?)" "$dc6"
dc7=$(draft 'SDK minSdk 99')
check "DC7 dòng 'SDK minSdk 99' ngoài khối TODO -> FAIL 'SDK minSdk: tài liệu 99'" \
  "$(has "$dc7" 'SDK minSdk: tài liệu 99'; echo $?)" "$dc7"

dc8=$(draft 'Cần AGP 9.9.9 trở lên.')
check "DC8 dòng 'AGP 9.9.9' bất kỳ trong tài liệu ≠ settings.gradle.kts -> FAIL 'AGP: tài liệu 9.9.9'" \
  "$(has "$dc8" 'AGP: tài liệu 9.9.9'; echo $?)" "$dc8"
dc9=$(draft 'Dùng Kotlin 2.2.10.')
check "DC9 số phiên bản Kotlin không có nguồn trong repo -> FAIL 'phiên bản Kotlin'" \
  "$(has "$dc9" 'phiên bản Kotlin'; echo $?)" "$dc9"

dc10=$(sed 's/`seg_weight_bounce`, //' "$IGDOC" > "$TMP/draft.md"; bash "$ROOT/tools/integration_doc_check.sh" "$TMP/draft.md" 2>&1)
check "DC10 khoá RC seg_weight_bounce (đọc ở src/main) vắng trong tài liệu -> FAIL 'khoá RC seg_weight_bounce'" \
  "$(has "$dc10" 'khoá RC seg_weight_bounce'; echo $?)" "$dc10"

dg_finish
