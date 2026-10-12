#!/usr/bin/env bash
# 2.3.0 SECBH: device_gate.sh --policy (L1-L4 trên sink fake mode, tools/lib/policy_legs.sh) + D18-230(b) dọn airplane mode lúc thoát cổng.
# adb giả: policy_fake_adb (dg_harness.sh); sink giả ghi lệnh start/stop + env. Chạy: bash tools/tests/dg_policy_test.sh (hoặc qua runner, phần `policy`).
. "$(dirname "$0")/lib/dg_harness.sh" || exit 99
policy_fake_adb
run_pl() {   # gọi SAU prepare_gate; FAKE_PL_*/FAKE_AIRPLANE do caller đặt trước tên hàm
  local d=$GATE_ROOT/tools/tests/fixtures/policy; mkdir -p "$d"; cp "$ROOT/tools/tests/fixtures/policy/default.json" "$d/"; rm -rf "$TMP/pl-st"; mkdir -p "$TMP/pl-st"; : > "$TMP/pl-st/log"; : > "$TMP/adb-calls"
  printf '%s\n' '#!/usr/bin/env bash' 'ST=$FAKE_PL_ST' 'case "$1" in start) [ -f "$BRAIN_POLICY_DIR/default.json" ] || exit 1; echo "start $* mode=$BRAIN_INTEGRITY_MODE dir=$BRAIN_POLICY_DIR" >> "$ST/sink"; rm -f "$ST/down";; stop) echo stop >> "$ST/sink"; : > "$ST/down";; status) echo up;; esac' > "$GATE_ROOT/tools/local_sink.sh"
  ADB_CALLS=$TMP/adb-calls FAKE_PL_ST=$TMP/pl-st FAKE_MBIN=$TMP/bin PATH="$TMP/pbin:$TMP/bin:$PATH" MG_NO_SLEEP=1 PL_WAIT=1 FAKE_ADB_FULL=1 ANDROID_SERIAL=stub-serial \
    bash "$GATE_ROOT/tools/device_gate.sh" "$@" --apk "$GATE_ROOT/apk/app-release.apk" --mapping "$GATE_ROOT/apk/mapping.txt" 2>&1
}
pl() { prepare_gate "$TMP/good.txt"; run_pl --policy; }   # một lượt --policy trong cây mới; đặt rc ở $?
printf '%s\n' '# chỉ để cổng nạp allowlist' > "$TMP/good.txt"

out=$(pl); rc=$?; calls=$(cat "$TMP/adb-calls"); sink=$(cat "$TMP/pl-st/sink" 2> /dev/null)
check "GP1 --policy: L1 applied 1 + nạp 1, L2 default(not_accepted), L3 sink dừng, L4 không extra; sink fake + thư mục policy + reverse cùng cổng trống; RESULT: PASS (cổng cũ: đối số lạ '--policy')" \
  "$(hasnt "$out" 'đối số lạ' && has "$out" 'policy L1' && has "$out" 'applied=1 (cần 1)' && has "$out" 'policy L2' && has "$out" 'policy L3' && has "$out" 'policy L4' && has "$out" 'RESULT: PASS' && [ "$rc" -eq 0 ] \
    && has "$calls" '--es policy_fake_token fake-ok' && has "$calls" '--es policy_fake_token fake-bad-device' && has "$sink" 'mode=fake dir=' && [ "$(printf '%s\n' "$sink" | grep -c '^start ')" -eq 2 ] \
    && p=$(printf '%s\n' "$sink" | sed -n '1s/.*--port \([0-9]*\).*/\1/p') && [ "${p:-0}" -ge 1024 ] && has "$calls" "reverse tcp:8089 tcp:$p" && [ "$(printf '%s\n' "$sink" | tail -1)" = stop ]; echo $?)" "rc=$rc
$out
sink: $sink"

prepare_gate "$TMP/good.txt"; g2=$(FAKE_PL_NOAPPLY=1 run_pl --policy); g2rc=$?
prepare_gate "$TMP/good.txt"; g2b=$(FAKE_PL_DUP=1 run_pl --policy); g2brc=$?
check "GP2 L1 không có dòng applied, hoặc applied 2 lần (không đúng 1) -> FAIL, không PASS" \
  "$(has "$g2" 'FAIL: L1' && hasnt "$g2" 'RESULT: PASS' && [ "$g2rc" -eq 1 ] && has "$g2b" 'FAIL: L1' && hasnt "$g2b" 'RESULT: PASS' && [ "$g2brc" -eq 1 ]; echo $?)" "$g2
$g2b"

prepare_gate "$TMP/good.txt"; g2c=$(FAKE_PL_DUPA=1 run_pl --policy); g2crc=$?
check "GP2b L1 chỉ dòng 'policy: applied' đôi (dòng 'đã nạp' đơn) -> FAIL L1 (applied=2, cần đúng 1)" \
  "$(has "$g2c" 'applied=2 (cần 1)' && has "$g2c" 'FAIL: L1' && hasnt "$g2c" 'RESULT: PASS' && [ "$g2crc" -eq 1 ]; echo $?)" "$g2c"

prepare_gate "$TMP/good.txt"; g3=$(FAKE_PL_L2_APPLIED=1 run_pl --policy); g3rc=$?
check "GP3 L2 (fake-bad-device) mà có dòng 'policy: applied' -> FAIL L2" \
  "$(has "$g3" 'FAIL: L2' && hasnt "$g3" 'RESULT: PASS' && [ "$g3rc" -eq 1 ]; echo $?)" "$g3"

prepare_gate "$TMP/good.txt"; g4=$(FAKE_PL_L4_APPLIED=1 run_pl --policy); g4rc=$?
check "GP4 L4 (không extra: đường sản phẩm tắt) mà có 'policy: applied' -> FAIL L4" \
  "$(has "$g4" 'FAIL: L4' && hasnt "$g4" 'RESULT: PASS' && [ "$g4rc" -eq 1 ]; echo $?)" "$g4"

prepare_gate "$TMP/good.txt"; g5=$(run_pl --policy --app a.b/.Main); g5rc=$?
prepare_gate "$TMP/good.txt"; g5b=$(run_pl --policy --drc); g5brc=$?
prepare_gate "$TMP/good.txt"; g5c=$(run_pl --policy --measure); g5crc=$?
check "GP5 --policy + --app / --drc / --measure -> FAIL 'chế độ chỉ cho demo' / không đi chung một lượt, 0 lệnh adb" \
  "$(has "$g5" 'chế độ chỉ cho demo' && [ "$g5rc" -eq 1 ] && has "$g5b" 'không đi chung một lượt' && [ "$g5brc" -eq 1 ] && has "$g5c" 'không đi chung một lượt' && [ "$g5crc" -eq 1 ] && hasnt "$g5$g5b$g5c" 'RESULT: PASS'; echo $?)" "$g5
$g5b
$g5c"

prepare_gate "$TMP/good.txt"; g6=$(FAKE_PL_LEAK=1 run_pl --policy); g6rc=$?
check "GP6 token fake-ok lọt vào logcat của app -> FAIL (không bao giờ log token)" \
  "$(has "$g6" 'FAIL: token' && hasnt "$g6" 'RESULT: PASS' && [ "$g6rc" -eq 1 ]; echo $?)" "$g6"

# GP7 (D18-230 b): airplane_mode_on=1 lúc thoát -> `settings put global airplane_mode_on 0` (cả khi cổng FAIL giữa chừng: cổng cũ dừng ở 'am start -W không in TotalTime'); =0 -> không đụng.
prepare_gate "$TMP/good.txt"; a1=$(FAKE_AIRPLANE=1 run_pl --app a.b/.Main); a1rc=$?; c1=$(cat "$TMP/adb-calls")
prepare_gate "$TMP/good.txt"; a0=$(FAKE_AIRPLANE=0 run_pl --app a.b/.Main); c0=$(cat "$TMP/adb-calls")
prepare_gate "$TMP/good.txt"; a2=$(FAKE_AIRPLANE=1 run_pl --policy); c2=$(cat "$TMP/adb-calls")
check "GP7 airplane mode: bật lúc thoát (cổng FAIL giữa chừng và --policy PASS) -> 'settings put global airplane_mode_on 0' + 'svc wifi enable'; đã tắt -> 0 lệnh put" \
  "$(has "$a1" 'RESULT: FAIL' && [ "$a1rc" -eq 1 ] && has "$c1" 'shell settings put global airplane_mode_on 0' && has "$c1" 'svc wifi enable' && has "$c2" 'shell settings put global airplane_mode_on 0' && has "$a2" 'RESULT: PASS' \
    && hasnt "$c0" 'settings put global airplane_mode_on'; echo $?)" "$a1
$c1"

# PL1 (2.4.0 CARRY-T #18): regex của policy_legs.sh (L1-L4) trên log THẬT 2.3.0 (fixtures/policy/real-policy-L*.log, SECBH smoke 5570); cùng chuỗi regex đọc từ nguồn.
. "$ROOT/tools/lib/policy_legs.sh"; FXP=$ROOT/tools/tests/fixtures/policy; V=policy_harness_230
srcs=$(cat "$ROOT/tools/lib/policy_legs.sh")
n1=$(pl_n "$FXP/real-policy-L1.log" "policy: applied version=$V source=app")/$(pl_n "$FXP/real-policy-L1.log" "BrainPolicy: đã nạp '$V'")
n2=$(pl_n "$FXP/real-policy-L2.log" 'policy: applied')/$(pl_n "$FXP/real-policy-L2.log" 'policy: default \(not_accepted\)')
n3=$(pl_n "$FXP/real-policy-L3.log" 'policy: applied')/$(pl_n "$FXP/real-policy-L3.log" 'policy: (default \(nonce_|error \()')
n4=$(pl_n "$FXP/real-policy-L4.log" 'policy: (applied|default)')/$(pl_n "$FXP/real-policy-L1.log" "$PL_RE")
check "PL1 regex policy_legs trên log thật: L1 applied/nạp 1/1, L2 applied/default(not_accepted) 0/1, L3 0/1 (error), L4 applied|default 0 (dòng 'policy: off' không khớp); PL_RE khớp L1" \
  "$([ "$n1" = 1/1 ] && [ "$n2" = 0/1 ] && [ "$n3" = 0/1 ] && [ "$n4" = 0/1 ] && has "$srcs" "'policy: default \\(not_accepted\\)'" && has "$srcs" "'policy: (default \\(nonce_|error \\()'"; echo $?)" "$n1 $n2 $n3 $n4"

dg_finish
