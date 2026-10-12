# shellcheck shell=bash
# Nạp bởi tools/lib/banners_gate.sh (cần mg_sleep, adb_b). Không chạy riêng.
# 2.0.0 T0b (D55, R-T0b I1/m3/m4): BN_NTCL_WAIT = GIÂY THẬT (mặc định 30, [estimate]) chờ dòng 'ntcl attach children=1' (đo bằng $SECONDS; MG_NO_SLEEP=1 dùng đồng hồ ảo
# BN_VCLK cộng theo mỗi lần ngủ, chỉ cho test); hết hạn = FAIL. Thấy rồi vẫn chờ tới t0+18 s (vòng x3 của demo 15 s: show 0/6/12, hide 3/9/15, +3 s đệm; t0 = lúc vào hàm, sau lần chạm >= 3 s) rồi mới lấy mẫu AdView.
bn_clk() { if [ "${MG_NO_SLEEP:-0}" = 1 ]; then echo "${BN_VCLK:-0}"; else echo "$SECONDS"; fi; }
bn_nap() { mg_sleep "$1"; [ "${MG_NO_SLEEP:-0}" != 1 ] || { BN_VCLK=$(( ${BN_VCLK:-0} + $1 )); export BN_VCLK; }; }
bn_wait_ntcl() {
  local t0 n; t0=$(bn_clk)
  while [ $(( $(bn_clk) - t0 )) -lt "${BN_NTCL_WAIT:-30}" ]; do
    adb_b 30 logcat -d 2>/dev/null | grep -qE 'ntcl attach children=1([^0-9]|$)' && { n=$(( 18 - ($(bn_clk) - t0) )); [ "$n" -le 0 ] || bn_nap "$n"; return 0; }
    bn_nap 1
  done; return 1
}
bn_rot_restore() { [ -z "${BN_ROT0:-}" ] || adb_b 60 shell settings put system accelerometer_rotation "$BN_ROT0" > /dev/null 2>&1; BN_ROT0=; }
