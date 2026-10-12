# shellcheck shell=bash
# 1.9.0 Task 10 (Task R #10): kịch bản --banners trên một máy (APK utility release): banner collapsible mở ×3, xoay ×2, NtCl
# show/hide ×3 (nút "NtCl show/hide x3" của demo), HOME rồi về app. Nạp bởi tools/device_gate.sh --banners SAU tools/lib/measure_gate.sh
# (dùng mg_*: mg_find_text, mg_tap_verified, mg_save_logs, mg_count_crashes...); mọi lệnh tới máy qua adb_b. MG_NO_SLEEP=1 chỉ cho test. BN_NTCL_WAIT = GIÂY THẬT chờ ntcl attach (mặc định 30, đo bằng $SECONDS; banners_ntcl.sh).
# Bằng chứng (fail-closed, mục nào vắng cũng FAIL): 0 dòng 'The specified child already has a parent'; `showBanner call show` >= 1;
# MỌI dòng `ntcl attach children=N` có N = 1 và >= 1 dòng; `dumpsys activity top` có đúng 1 view AdView (host = MainActivity), sau
# lúc mở, sau NtCl x3 và sau HOME/resume; FATAL/ANR/native 0. Chevron của banner collapsible không bấm được (không bấm vào creative): ghi "chưa đo".

BN_PARENT_RE='The specified child already has a parent'
bn_count() { local n; n=$(grep -cE -- "$2" "$1" 2>/dev/null); echo "${n:-0}"; }
# Số view AdView của activity trên cùng (dumpsys activity top); lưu bản dump vào $1 làm bằng chứng.
# Tên AdView sau R8 đọc từ mapping của cổng ($MAP): hierarchy hiện 'i4{' chứ không phải '.AdView{'. Đặt BN_OBF (tên hoặc 'none') và BN_ADRE.
bn_adre() {
  BN_OBF=none; [ -f "${MAP:-}" ] && BN_OBF=$(command grep -m1 '^com\.google\.android\.gms\.ads\.AdView -> ' "$MAP" | sed -E 's/.* -> ([^:]+):.*/\1/')
  [ -n "$BN_OBF" ] || BN_OBF=none
  BN_ADRE='\.AdView\{'
  [ "$BN_OBF" = none ] || BN_ADRE="(\.AdView|(^|[^A-Za-z0-9_.\$])$(printf %s "$BN_OBF" | sed 's/[.[\*^$()+?{|]/\\&/g'))\{"
}
# Chỉ đếm trong khối TASK của app (${MG_PKG}); dump không có dòng TASK thì đếm cả dump. Bản dump đầy đủ lưu vào $1.
# 2.4.0 FIX-GATE (D25): STACK=nextgen (--variant nextgen) đếm THÊM view gốc của banner Next-Gen: lớp nằm trong gói ads_mobile_sdk (SDK tự làm rối, mapping
# của app không đổi tên; Samsung: o53 > h41 > r31 > zn3 = MỘT banner) hoặc com.google.android.libraries.ads.mobile.sdk; mỗi cây con = 1 view (theo thụt lề).
# Lớp gms.ads.AdView của stack cũ vẫn được đếm ở mọi stack (utility/legacy dưới nextgen): tổng hai loại.
bn_ngroots() { awk '{ ind = match($0, /[^ ]/) }
  $0 ~ /^ *(ads_mobile_sdk|com[.]google[.]android[.]libraries[.]ads[.]mobile[.]sdk)[A-Za-z0-9_.$]*[{]/ { if (!inr || ind <= ri) { n++; inr = 1; ri = ind }; next }
  inr && ind <= ri { inr = 0 } END { print n + 0 }'; }
bn_adview() {
  local t n m=0; [ -n "${BN_ADRE:-}" ] || bn_adre
  t=$(adb_b 60 shell dumpsys activity top 2>/dev/null | tr -d '\r' | tee "$1" | awk -v p="${MG_PKG:-} " '/^TASK /{on = (index($0, "TASK " p) == 1 || index($0, ":" p) > 0); next} BEGIN{on=1} on')
  n=$(printf '%s\n' "$t" | grep -cE -- "$BN_ADRE")
  [ "${STACK:-}" != nextgen ] || m=$(printf '%s\n' "$t" | bn_ngroots)
  echo $((n + m)); return 0
}
. "${BASH_SOURCE[0]%/*}/banners_ntcl.sh" || return 1   # 2.0.0 T0b (D8): chờ ntcl có hạn, đồng hồ, khôi phục xoay (tách để giữ trần dòng)
bn_rotate() { adb_b 60 shell settings put system accelerometer_rotation 0 > /dev/null 2>&1; adb_b 60 shell settings put system user_rotation "$1" > /dev/null 2>&1; mg_sleep 3; }

# $1 = tiền tố file, $2 = JSONL của sink, $3 = số dòng JSONL trước cổng. In "RESULT: PASS|FAIL"; return 0/1.
banners_run() {
  local pre=$1 fail=0 i parent show att_ok att_all adv1 adv2 adv3 clear rot0
  MG_JSONL=$2
  bn_adre; mg_say "banners: AdView name after R8: $BN_OBF"
  mg_say "banners: pm clear, am start -W, Show Banner Collapsible x3, xoay x2, NtCl show/hide x3, HOME, về app; jsonl=$MG_JSONL"
  clear=$(adb_b 60 shell pm clear "$MG_PKG" 2>&1 | tr -d '\r')
  [ "$clear" = Success ] || { mg_say "FAIL: pm clear $MG_PKG: $clear"; mg_say "RESULT: FAIL"; return 1; }
  adb_b 60 logcat -c > /dev/null 2>&1
  mg_start || { mg_say "RESULT: FAIL"; return 1; }
  mg_sleep 20; mg_shade_reset
  mg_focus_is "$MG_ACT" || { mg_say "FAIL: sau cold start focus = $(mg_focus)"; mg_say "RESULT: FAIL"; return 1; }
  mg_screen_init
  for i in 1 2 3; do
    mg_tap_verified "Show Banner Collapsible" "showBanner call show" || { mg_say "FAIL: không chạm được 'Show Banner Collapsible' lần $i"; fail=1; break; }
    mg_sleep 8
  done
  adv1=$(bn_adview "$pre-top1.txt")
  mg_say "banners: chevron collapse: chưa đo (không bấm vào creative, R10); mở lại x3 = $i lần chạm"
  rot0=$(adb_b 30 shell settings get system accelerometer_rotation 2>/dev/null | tr -d '\r'); case "$rot0" in 0|1) BN_ROT0=$rot0;; *) BN_ROT0=;; esac; trap 'bn_rot_restore; ! type cleanup > /dev/null 2>&1 || cleanup' EXIT   # B8: giá trị gốc; không đọc được thì không ép về 1
  for i in 1 2; do bn_rotate 1; bn_rotate 0; mg_say "  xoay lượt $i: focus = $(mg_focus)"; done
  mg_tap_verified "NtCl show/hide x3" "ntcl show/hide x3 start" || { mg_say "FAIL: không chạm được 'NtCl show/hide x3'"; fail=1; }
  bn_wait_ntcl || { mg_say "no-fill: ntcl attach not seen within ${BN_NTCL_WAIT:-30} s (D55: one re-run allowed)"; mg_say "FAIL: hết hạn ${BN_NTCL_WAIT:-30} s chờ 'ntcl attach children=1' (B7)"; fail=1; }
  adv3=$(bn_adview "$pre-top3.txt")   # B9: AdView sau NtCl x3
  adb_b 60 shell input keyevent KEYCODE_HOME > /dev/null 2>&1
  mg_sleep 5
  mg_back_in_app "banners" || { mg_say "FAIL: không về được MainActivity sau HOME"; fail=1; }
  mg_sleep 3
  adv2=$(bn_adview "$pre-top2.txt")
  mg_save_logs "$pre"
  parent=$(bn_count "$pre.log" "$BN_PARENT_RE")
  show=$(bn_count "$pre.log" 'showBanner call show')
  att_all=$(bn_count "$pre.log" 'ntcl attach children=[0-9]+')
  att_ok=$(bn_count "$pre.log" 'ntcl attach children=1([^0-9]|$)')
  mg_say "banners: parent=$parent show=$show ntcl_ok=$att_ok ntcl_bad=$((att_all - att_ok)) adview=$adv1/$adv2 adview_ntcl=$adv3 (need parent=0 show>=1 ntcl_ok>=1 ntcl_bad=0 adview=1/1 adview_ntcl=1)"
  { [ "$parent" -eq 0 ] && [ "$show" -ge 1 ] && [ "$att_ok" -ge 1 ] && [ "$att_all" -eq "$att_ok" ] && [ "$adv1" -eq 1 ] && [ "$adv2" -eq 1 ] && [ "$adv3" -eq 1 ]; } || fail=1
  bn_rot_restore   # B8: khôi phục xoay sau cổng
  mg_count_crashes banners
  mg_say "FATAL=$MG_FATAL am_anr=$MG_ANR native=$MG_NATIVE (need 0/0/0)"
  [ "$MG_FATAL" -eq 0 ] && [ "$MG_ANR" -eq 0 ] && [ "$MG_NATIVE" -eq 0 ] || fail=1
  [ "${MG_CAPTURE_FAIL:-0}" -eq 0 ] && ! grep -q '^FAIL: adb_b timeout' "${MG_LOG:-/dev/null}" || fail=1
  mg_say "RESULT: $([ "$fail" -eq 0 ] && echo PASS || echo FAIL)"
  return "$fail"
}
