# shellcheck shell=bash
# 1.9.0 Task 10 (D28): đếm meta-data com.app.plugin.<tên>_HARNESS trong manifest đã merge của APK (aapt2 dump xmltree): utility 1, game 0.
# Nạp bởi tools/release_gate.sh (dùng $LOG, đặt fail=1 khi lệch); aapt2 vắng hoặc lỗi cũng FAIL (không bao giờ "0 dòng" thành PASS).
# $1 = apk, $2 = variant, $3 = aapt2, $4 = tên meta-data (IAP_HARNESS | BRAIN_HARNESS).
check_harness_meta() {
  local out rc n want=0
  case $2 in utility|nextgen) want=1;; esac   # 2.4.0 NG6: nextgen giữ khoá harness như utility
  [ -x "$3" ] || { echo "FAIL: aapt2 không chạy được ($3): không đếm được $4" | tee -a "$LOG"; fail=1; return 0; }
  out=$("$3" dump xmltree --file AndroidManifest.xml "$1" 2>&1); rc=$?
  [ "$rc" -eq 0 ] || { echo "FAIL: aapt2 dump xmltree exit=$rc: $out" | tee -a "$LOG"; fail=1; return 0; }
  n=$(printf '%s\n' "$out" | grep -c "com.app.plugin.$4\"")
  echo "harness meta-data com.app.plugin.$4=$n (need $want)" | tee -a "$LOG"
  [ "$n" -eq "$want" ] || fail=1
}
