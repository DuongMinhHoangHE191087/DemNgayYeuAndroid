# shellcheck shell=bash
# 1.7.0 Task 13: kịch bản đo trên một máy (APK utility release, sink native, reverse) — pha A (một phiên có quảng
# cáo), pha B (sink không tới được, rồi kill), pha C (mở lại, replay). Nạp bởi tools/device_gate.sh --measure và
# tools/daily_session.sh; mọi lệnh đi qua adb_b (tools/lib/adb_b.sh, 1.8.0 Task 2) với ANDROID_SERIAL (-s ngầm), không `devices`.
#
# Bài học DAILY d1 (common.md "Phase A script lesson"): nút Show của demo làm "hiện; chưa nạp thì nạp cho lượt sau".
# Một pha A hợp lệ phải: kiểm từng lần chạm trong logcat (`showInterstitial call show` / `showRewarded call show`,
# ≤ 3 lần), KHÔNG `am start` giữa hai lần chạm của một định dạng, chờ `onAdLoaded … pl=<placement>` mới trước lần chạm
# thứ hai, và xác nhận quảng cáo hiện (`AdActivity` trong `dumpsys window`). Phiên hợp lệ = ≥ 1 dòng FA
# `origin=app,name=ad_impression` và ≥ 1 hàng `ad_revenue` của session_id đó trong JSONL; không thì `invalid (…)`.
#
# Về app sau quảng cáo: nhánh do P10 (KB-1 của T13, DOC/testing/t170-t13-p10.log) chọn, MG_RETURN_ARM:
#   clear_top_single  am start -n <MainActivity> --activity-clear-top --activity-single-top   (mặc định)
#   clear_top         am start -n <MainActivity> --activity-clear-top
# KHÔNG -W ở đây: activity đã có nhận intent qua onNewIntent, không có lần launch mới nên API 26 không bao giờ báo
# "Displayed" và -W treo vô hạn (TF5); kết luận do thăm focus ≤ 10 s. -W chỉ dùng cho cold start (mg_start, có watchdog).
#   back              input keyevent KEYCODE_BACK (×1; ×2 đẩy demo ra launcher)
# Exit/return: 0 = hợp lệ; 4 = invalid (ui|focus|0 impressions), không tính, chạy lại; 1 = hỏng (am start, pm clear).
# MG_NO_SLEEP=1 chỉ cho test không cần máy (tools/tests/device_gate_allowlist_test.sh).

type adb_b > /dev/null 2>&1 || . "$(dirname "${BASH_SOURCE[0]}")/adb_b.sh"
type redact_stream > /dev/null 2>&1 || . "$(dirname "${BASH_SOURCE[0]}")/redact.sh" || { echo "FAIL: không nạp được redact.sh" >&2; exit 2; }   # 1.9.0 Task 0c: daily_session.sh chỉ nạp lib này

type ui_find > /dev/null 2>&1 || . "$(dirname "${BASH_SOURCE[0]}")/ui_find.sh" || { echo "FAIL: không nạp được ui_find.sh" >&2; exit 2; }   # 2.1.0 Task 0c: mg_find_text/mg_scroll

type mg_first_visible > /dev/null 2>&1 || . "$(dirname "${BASH_SOURCE[0]}")/measure_tap.sh" || { echo "FAIL: không nạp được measure_tap.sh" >&2; exit 2; }   # 2.4.0 FIX-GATE: mg_first_visible + MG_NAVTOP

MG_PKG=test.sdk.zd
MG_ACT=com.app.plugin.demo.MainActivity
MG_RETURN_ARM=${MG_RETURN_ARM:-clear_top_single}
MG_LOAD_WAIT=${MG_LOAD_WAIT:-15}
MG_TRIES=3
MG_SINK_PORT=${MG_SINK_PORT:-8089}   # 1.8.0 Task 1: cổng sink của lane (device_gate/daily_session --sink-port); pha C reverse lại đúng cổng này

mg_sleep() { [ "${MG_NO_SLEEP:-0}" = 1 ] || sleep "$1"; }
# Mọi dòng của kịch bản ra stdout và vào $MG_LOG (log của cổng/phiên) nếu có.
mg_say() { if [ -n "${MG_LOG:-}" ]; then echo "$*" | tee -a "$MG_LOG"; else echo "$*"; fi; }
mg_jsonl_lines() { if [ -f "$MG_JSONL" ]; then wc -l < "$MG_JSONL" | tr -d ' '; else echo 0; fi; }
mg_now_ms() { echo $(( $(date +%s) * 1000 )); }
# Số dòng logcat (đầy đủ, mọi process) khớp regex $1.
mg_count() { adb_b 30 logcat -d 2>/dev/null | grep -c -- "$1"; }
mg_logcat_fail() { mg_say "FAIL: logcat $1 (adb_b rc=$2)"; MG_CAPTURE_FAIL=1; }   # R-T2 I1: hết hạn = không có bằng chứng = FAIL
mg_focus_is() { adb_b 60 shell dumpsys window 2>/dev/null | grep mCurrentFocus | grep -qF -- "$1"; }
mg_focus() { adb_b 60 shell dumpsys window 2>/dev/null | grep mCurrentFocus | head -1 | sed 's/^ *//'; }

# Tâm bounds của node hiển thị đầu tiên có text đúng bằng $1 trong XML uiautomator (stdin): "x y", rỗng nếu không có.
mg_bounds_of() {   # $2 = ngưỡng cao tối thiểu phía trên thanh (mặc định MG_MIN_VIS); text khớp NGUYÊN (có dấu ") nên 'Show Rewarded' không khớp 'Show Rewarded Interstitial'
  tr '>' '\n' | grep -F "text=\"$1\"" | mg_first_visible "${2:-}"
}

# Mật độ điểm ảnh từ `wm density` ("Override density:" nếu có, không thì "Physical density:"); không đọc được → "?" (1.9.0 Task 0d).
mg_density() {
  local out
  out=$(adb_b 60 shell wm density 2>/dev/null | tr -d '\r')
  printf '%s\n%s\n' "$(printf '%s\n' "$out" | sed -nE 's/^Override density: *([0-9]+).*/\1/p' | head -1)" \
    "$(printf '%s\n' "$out" | sed -nE 's/^Physical density: *([0-9]+).*/\1/p' | head -1)" | sed -n '/./{p;q;}' | grep . || echo '?'
}

# Cỡ màn hình (MG_W MG_H) đọc một lần mỗi lần chạy từ `wm size` ("Override size:" nếu có, không thì "Physical size:");
# không đọc được → 1080x2400 và nói rõ đã dùng mặc định. Gọi ngoài $( ) để biến còn lại (mg_phase_a).
mg_screen_init() {
  local out sz
  out=$(adb_b 60 shell wm size 2>/dev/null | tr -d '\r')
  sz=$(printf '%s\n' "$out" | sed -nE 's/^Override size: *([0-9]+)x([0-9]+).*/\1 \2/p' | head -1)
  [ -n "$sz" ] || sz=$(printf '%s\n' "$out" | sed -nE 's/^Physical size: *([0-9]+)x([0-9]+).*/\1 \2/p' | head -1)
  if [ -n "$sz" ]; then
    MG_W=${sz% *}; MG_H=${sz#* }
    mg_say "  màn hình ${MG_W}x${MG_H} (wm size)"
    mg_say "screen size=${MG_W}x${MG_H} density=$(mg_density)"
  else
    MG_W=1080; MG_H=2400
    mg_say "  wm size không đọc được, dùng mặc định ${MG_W}x${MG_H}"
    mg_say "screen size=${MG_W}x${MG_H} density=$(mg_density)"
  fi
  mg_navtop_init   # 2.4.0 FIX-GATE: thanh điều hướng 3 nút → vùng chạm
}

# MG_FIRST_TAP_META (đường dẫn) đặt: ghi `first_tap=$(mg_first_tap_count)` một lần (meta chưa có dòng đó). Cổng iap ghi đè mg_first_tap_count.
mg_first_tap_count() { mg_jsonl_lines; }
mg_first_tap_mark() { [ -z "${MG_FIRST_TAP_META:-}" ] || grep -q '^first_tap=' "$MG_FIRST_TAP_META" 2> /dev/null || echo "first_tap=$(mg_first_tap_count)" >> "$MG_FIRST_TAP_META"; }

# Chạm $1 tới khi số dòng logcat khớp $2 tăng (≤ 3 lần). Không tới app → invalid (ui).
mg_tap_verified() {
  local lbl=$1 pat=$2 before try=0 xy frc
  before=$(mg_count "$pat")
  while [ "$try" -lt "$MG_TRIES" ]; do
    try=$((try + 1))
    xy=$(mg_find_text "$lbl"); frc=$?   # ui_find: 0 = thấy (chạm), 5 = đã chạy intent drive (không chạm), 4 = INVALID (ui)
    [ "$frc" -eq 0 ] || [ "$frc" -eq 5 ] || return 4
    mg_first_tap_mark   # D32-2: mốc "cú chạm đầu" ghi NGAY trước chạm thật (sau ui_find), không phải trước khi tìm nút
    [ "$frc" -eq 5 ] || adb_b 60 shell input tap $xy > /dev/null 2>&1
    mg_sleep 3
    if [ "$(mg_count "$pat")" -gt "$before" ]; then mg_say "  tap '$lbl' try=$try xy=$xy: '$pat' tới app"; return 0; fi
  done
  ! mg_focus_is AdActivity || mg_say "  ad shown, pattern not matched: AdActivity đang phủ màn hình, '$pat' không tới (không phải lỗi giao diện)"
  mg_say "invalid (ui): chạm '$lbl' $MG_TRIES lần, logcat không có '$pat' mới"
  return 4
}

# Về app bằng MG_RETURN_ARM, không chờ (không -W). Kết quả kiểm ở mg_back_in_app.
mg_return_to_app() {
  case $MG_RETURN_ARM in
    clear_top_single) adb_b 60 shell am start -n "$MG_PKG/$MG_ACT" --activity-clear-top --activity-single-top > /dev/null 2>&1;;
    clear_top) adb_b 60 shell am start -n "$MG_PKG/$MG_ACT" --activity-clear-top > /dev/null 2>&1;;
    back) adb_b 60 shell input keyevent KEYCODE_BACK > /dev/null 2>&1;;
    *) mg_say "FAIL: MG_RETURN_ARM lạ '$MG_RETURN_ARM'"; return 1;;
  esac
}

# mg_back_in_app "<chữ>": về app rồi thăm focus MainActivity mỗi 1 s, tối đa 10 lần (API 26 chậm không bị coi là hỏng);
# không thấy → invalid (focus) (return 4); return_to_app hỏng → 1.
mg_back_in_app() {
  local i=0
  mg_return_to_app || return 1
  while [ "$i" -lt 10 ]; do
    mg_sleep 1; i=$((i + 1))
    if mg_focus_is "$MG_ACT"; then mg_say "  show_full '$1': về MainActivity ($MG_RETURN_ARM)"; return 0; fi
  done
  mg_say "invalid (focus): sau show_full '$1' + $MG_RETURN_ARM focus = $(mg_focus)"
  return 4
}

# 2.0.0 Task 0c: quảng cáo toàn màn hình hiện = focus AdActivity, hoặc hộp "Viewing full screen" (ImmersiveModeConfirmation, API 37 lần đầu) đè lên nó (smoke 5570).
mg_ad_focus() { mg_focus_is AdActivity || mg_focus_is ImmersiveModeConfirmation; }

# show_full "<chữ>": lần chạm 1 (nạp; đã nạp sẵn thì hiện luôn) → chờ onAdLoaded MỚI (thăm 1 s, ≤ MG_LOAD_WAIT s) →
# lần chạm 2 (hiện) → chờ 10 s → AdActivity? (không → ghi "not shown") → về app bằng MG_RETURN_ARM → focus phải là
# MainActivity, không → invalid (focus). Không am start nào giữa hai lần chạm.
mg_show_full() {
  local lbl=$1 pat pl fmt loaded0 fill0 i=0
  case $lbl in
    "Show Interstitial") pat="showInterstitial call show"; pl=full_default; fmt=interstitial;;
    "Show Rewarded") pat="showRewarded call show"; pl=gift_default; fmt=rewarded;;
    "Show Rewarded Interstitial") pat="showRwi call show"; pl=rwi_default; fmt=rewarded_interstitial;;   # 1.9.0 Task 10
    "Interstitial → Full2") pat="showInterstitial call show"; pl=full_default; fmt=interstitial;;   # 2.4.0 DEMO-RES: cha của chuỗi Full2
    "Rewarded → Full2") pat="showRewarded call show"; pl=gift_default; fmt=rewarded;;
    *) mg_say "FAIL: show_full không biết nút '$lbl'"; return 1;;
  esac
  loaded0=$(mg_count "onAdLoaded: pl=$pl .*format=$fmt")
  fill0=$(grep -c "\"event_name\": \"ad_fill\"" "$MG_JSONL" 2>/dev/null)
  mg_tap_verified "$lbl" "$pat" || return 4
  # Đã nạp sẵn (vd pha B: SDK nạp lại sau lần đóng trước) thì lần chạm 1 hiện luôn: thăm focus ≤ 3 s trước khi coi là
  # "chỉ nạp", để lần chạm 2 không rơi vào quảng cáo đang hiện (bấm nhầm quảng cáo = mất focus).
  while [ "$i" -lt 3 ] && ! mg_ad_focus; do mg_sleep 1; i=$((i + 1)); done
  i=0
  if mg_ad_focus; then
    mg_say "  show_full '$lbl': hiện ngay ở lần chạm 1 (đã nạp sẵn)"
  else
    while [ "$i" -lt "$MG_LOAD_WAIT" ] && [ "$(mg_count "onAdLoaded: pl=$pl .*format=$fmt")" -le "$loaded0" ]; do
      mg_sleep 1; i=$((i + 1))
    done
    if [ "$(mg_count "onAdLoaded: pl=$pl .*format=$fmt")" -gt "$loaded0" ]; then
      mg_say "  show_full '$lbl': onAdLoaded pl=$pl mới sau ${i}s; JSONL ad_fill $fill0 -> $(grep -c "\"event_name\": \"ad_fill\"" "$MG_JSONL" 2>/dev/null)"
    else
      mg_say "  show_full '$lbl': chưa thấy onAdLoaded pl=$pl sau ${MG_LOAD_WAIT}s (ghi, chạm tiếp)"
    fi
    mg_sleep 6
    mg_tap_verified "$lbl" "$pat" || return 4
  fi
  mg_sleep 10
  if mg_ad_focus; then mg_say "  show_full '$lbl': shown (ad focus)"; else mg_say "  show_full '$lbl': not shown ($(mg_focus))"; fi
  mg_back_in_app "$lbl"; return $?
}

# am start -W (tuỳ chọn thêm ở $@) qua adb_b (MG_AMSTART_MAX, mặc định 60 s); không có TotalTime → hỏng (return 1).
# Quá hạn: in phần đã đọc (kể cả TotalTime, R-TF5 m1) rồi FAIL.
mg_start() {
  local out tmp rc max=${MG_AMSTART_MAX:-60}
  tmp=$(mktemp "${TMPDIR:-/tmp}/mg-start.XXXXXX") || { mg_say "FAIL: mktemp"; return 1; }
  adb_b "$max" shell am start -W -n "$MG_PKG/$MG_ACT" "$@" > "$tmp" 2>&1; rc=$?
  out=$(tr -d '\r' < "$tmp" | grep -E 'Status|TotalTime|WaitTime|Error'); rm -f "$tmp"
  mg_say "  am start -W $*: $(echo "$out" | tr '\n' ' ')"
  [ "$rc" -ne 124 ] || { mg_say "FAIL: am start -W quá ${max}s"; return 1; }
  echo "$out" | grep -q TotalTime || { mg_say "FAIL: am start -W không in TotalTime"; return 1; }
}

# Lưu logcat đầy đủ ($1.log) và của pid app ($1-app.log); pid ghi vào MG_PID.
mg_save_logs() {
  local mg_rc
  MG_PID=$(adb_b 60 shell pidof "$MG_PKG" 2>/dev/null | tr -d '\r')
  adb_b 30 logcat -d 2>/dev/null | redact_stream > "$1.log"; mg_rc=${PIPESTATUS[0]}; [ "$mg_rc" -eq 0 ] || mg_logcat_fail full "$mg_rc"
  if [ -n "$MG_PID" ]; then adb_b 30 logcat -d --pid="$MG_PID" 2>/dev/null | redact_stream > "$1-app.log"; mg_rc=${PIPESTATUS[0]}; [ "$mg_rc" -eq 0 ] || mg_logcat_fail app "$mg_rc"; else : > "$1-app.log"; fi
}

# Pha A (một lượt). $1 = tiền tố file. Đặt MG_SID. Return 0 hợp lệ, 4 invalid, 1 hỏng.
mg_phase_a() {
  local pre=$1 j0 clear fa rev
  MG_SID=""
  [ -n "${MG_W:-}" ] || mg_screen_init
  j0=$(mg_jsonl_lines)
  mg_say "measure: pha A ($pre): pm clear, FA verbose, am start -W không force_eea, Show Interstitial, Show Rewarded, Show Rewarded Interstitial, HOME"
  clear=$(adb_b 60 shell pm clear "$MG_PKG" 2>&1 | tr -d '\r')
  [ "$clear" = Success ] || { mg_say "FAIL: pm clear $MG_PKG: $clear"; return 1; }
  adb_b 60 shell setprop log.tag.FA VERBOSE > /dev/null 2>&1
  adb_b 60 shell setprop log.tag.FA-SVC VERBOSE > /dev/null 2>&1
  adb_b 60 logcat -c > /dev/null 2>&1
  mg_start || return 1
  mg_sleep 20; mg_shade_reset
  if ! mg_focus_is "$MG_ACT"; then
    mg_save_logs "$pre"; mg_say "invalid (focus): sau cold start focus = $(mg_focus)"; return 4
  fi
  mg_show_full "Show Interstitial" || { local r=$?; mg_save_logs "$pre"; return $r; }
  mg_show_full "Show Rewarded" || { local r=$?; mg_save_logs "$pre"; return $r; }
  mg_sleep 31   # 2.0.0 Task 0c: gap chung 30 s (policy_min_gap) kể từ lúc Rewarded đóng; không chờ thì rwi bị chặn (smoke 5570, API 37)
  mg_show_full "Show Rewarded Interstitial" || { local r=$?; mg_save_logs "$pre"; return $r; }   # 1.9.0 Task 10 (R #11: pha A gồm rwi)
  adb_b 60 shell input keyevent KEYCODE_HOME > /dev/null 2>&1
  mg_sleep 30
  mg_save_logs "$pre"
  tail -n +$((j0 + 1)) "$MG_JSONL" > "$pre.jsonl" 2>/dev/null
  MG_SID=$(grep -o 'logevent: session_id=[^ ]*' "$pre.log" | tail -1 | sed 's/.*=//' | tr -d '\r')
  fa=$(grep -c 'origin=app,name=ad_impression' "$pre.log")
  rev=0
  [ -z "$MG_SID" ] || rev=$(grep -F "\"session_id\": \"$MG_SID\"" "$pre.jsonl" | grep -cF '"event_name": "ad_revenue"')
  if [ "$fa" -ge 1 ] && [ "$rev" -ge 1 ]; then
    mg_say "phase A: valid session_id=$MG_SID FA origin=app ad_impression=$fa JSONL ad_revenue=$rev (pid $MG_PID)"
    return 0
  fi
  mg_say "invalid (0 impressions): session_id=${MG_SID:-<không có>} FA origin=app ad_impression=$fa JSONL ad_revenue=$rev"
  return 4
}

# Pha A tới khi hợp lệ: 1 lượt + chạy lại tối đa 2. Đặt MG_A (tiền tố lượt hợp lệ). Return 0 / 1.
mg_phase_a_valid() {
  local pre=$1 n=0 rc
  MG_A=""
  while [ "$n" -lt 3 ]; do
    n=$((n + 1)); [ "$n" -le 1 ] || mg_shade_reset   # 2.4.0 FIX-GATE 4: rèm không che lượt chạy lại
    mg_phase_a "$pre-a$n"; rc=$?
    [ "$rc" -eq 1 ] && return 1
    [ "$rc" -eq 0 ] && { MG_A=$pre-a$n; return 0; }
  done
  mg_say "FAIL: pha A invalid 3 lần (1 lượt + 2 lần chạy lại)"
  return 1
}

# Pha B: sink không tới được → mở ấm → Show Interstitial → HOME → chờ 5 s → force-stop (event chưa gửi).
# Đặt MG_T_B (ms máy chủ lúc bắt đầu, cho G11 (iv)). reverse --remove phải có hiệu lực (reverse --list không còn
# tcp:8089), không thì pha B hỏng (return 1): sink còn tới được thì "0 mất sau kill" không được thử (R-13 I2).
mg_phase_b() {
  local pre=$1 r=0
  mg_say "measure: pha B ($pre): reverse --remove, am start -W (ấm), Show Interstitial, HOME, force-stop"
  adb_b 60 logcat -c > /dev/null 2>&1
  MG_T_B=$(mg_now_ms)
  adb_b 60 reverse --remove tcp:8089 > /dev/null 2>&1
  if [ "$(adb_b 60 reverse --list 2>/dev/null | grep -c 'tcp:8089')" -ne 0 ]; then
    mg_say "FAIL: reverse --remove tcp:8089 không có hiệu lực (reverse --list còn tcp:8089)"
    return 1
  fi
  mg_start || return 1
  mg_sleep 5
  mg_show_full "Show Interstitial" || r=$?
  adb_b 60 shell input keyevent KEYCODE_HOME > /dev/null 2>&1
  mg_sleep 5
  mg_save_logs "$pre"
  adb_b 60 shell am force-stop "$MG_PKG" > /dev/null 2>&1
  mg_say "  pha B: durable stored=$(grep -c 'logevent durable stored' "$pre.log") (pid $MG_PID, đã force-stop)"
  return "$r"
}

# Pha C: reverse lại → cold start → chờ 40 s (replay) → HOME → chờ 20 s. Đặt MG_T_C (ms máy chủ lúc bắt đầu).
mg_phase_c() {
  local pre=$1
  mg_say "measure: pha C ($pre): reverse tcp:8089 tcp:${MG_SINK_PORT:-8089}, am start -W (cold), chờ replay 40 s, HOME"
  adb_b 60 logcat -c > /dev/null 2>&1
  MG_T_C=$(mg_now_ms)
  adb_b 60 reverse tcp:8089 "tcp:${MG_SINK_PORT:-8089}" > /dev/null 2>&1 || { mg_say "FAIL: adb reverse tcp:8089 tcp:${MG_SINK_PORT:-8089}"; return 1; }
  mg_start || return 1
  mg_sleep 40
  adb_b 60 shell input keyevent KEYCODE_HOME > /dev/null 2>&1
  mg_sleep 20
  mg_save_logs "$pre"
  mg_say "  pha C: durable replayed lines=$(grep -c 'logevent durable replayed' "$pre.log") (pid $MG_PID)"
}

# Crash Java/native + ANR của app sau mỗi pha (như count_crashes của device_gate.sh), cộng dồn MG_FATAL/MG_NATIVE/MG_ANR.
MG_FATAL=0; MG_NATIVE=0; MG_ANR=0
mg_count_crashes() {
  local crash events f n a
  crash=$(adb_b 30 logcat -d -b crash 2>/dev/null) || mg_logcat_fail crash $?
  events=$(adb_b 30 logcat -d -b events 2>/dev/null) || mg_logcat_fail events $?
  adb_b 60 logcat -c -b crash > /dev/null 2>&1; adb_b 60 logcat -c -b events > /dev/null 2>&1
  [ -z "$crash" ] || [ -z "${MG_CRASHLOG:-}" ] || echo "$crash" | redact_stream >> "$MG_CRASHLOG"
  f=$(echo "$crash" | grep --text -A5 "FATAL EXCEPTION" | grep --text -cF "Process: $MG_PKG")
  n=$(echo "$crash" | grep --text -cF ">>> $MG_PKG <<<")
  a=$(echo "$events" | grep --text am_anr | grep --text -cF "$MG_PKG")
  MG_FATAL=$((MG_FATAL + f)); MG_NATIVE=$((MG_NATIVE + n)); MG_ANR=$((MG_ANR + a))
  mg_say "  [$1] FATAL=$f native=$n am_anr=$a"
}

# Cổng --measure (device_gate.sh đã cài APK, bật reverse, xoá logcat): pha A hợp lệ → B → C → measure_check G11
# (i)–(vi) → H6a của phiên A (+B, cùng process; thông tin) → FATAL/ANR 0. $1 = tiền tố file, $2 = JSONL của sink,
# $3 = số dòng JSONL trước cổng. In "RESULT: PASS|FAIL"; return 0/1.
measure_run() {
  local pre=$1 fail=0 rc
  MG_JSONL=$2
  mg_say "measure: G11 + H6a (thay monkey); về app sau quảng cáo = $MG_RETURN_ARM; jsonl=$MG_JSONL"
  if ! mg_phase_a_valid "$pre"; then
    mg_count_crashes "pha A"
    mg_say "RESULT: FAIL"; return 1
  fi
  mg_count_crashes "pha A"
  mg_phase_b "$pre-b"; rc=$?
  mg_count_crashes "pha B"
  [ "$rc" -ne 1 ] || { mg_say "RESULT: FAIL"; return 1; }
  [ "$rc" -eq 0 ] || { mg_say "invalid (pha B, rc=$rc): lượt đo không tính"; fail=1; }
  mg_phase_c "$pre-c" || fail=1
  mg_count_crashes "pha C"
  tail -n +$(($3 + 1)) "$MG_JSONL" > "$pre.jsonl" 2>/dev/null
  cat "$MG_A.log" "$pre-b.log" > "$pre-ab.log" 2>/dev/null
  "${MEASURE_PY:-python3}" tools/measure_check.py g11 --jsonl "$pre.jsonl" --log-a "$MG_A.log" --log-b "$pre-b.log" \
    --log-c "$pre-c.log" --t-b "${MG_T_B:-0}" --t-c "${MG_T_C:-0}" > "$pre-g11.out" 2>&1 || fail=1
  while IFS= read -r l; do mg_say "$l"; done < "$pre-g11.out"
  bash tools/reconcile_session.sh "$pre-ab.log" "$pre.jsonl" "$MG_SID" > "$pre-h6a.out" 2>&1
  while IFS= read -r l; do mg_say "H6a (thông tin) $l"; done < "$pre-h6a.out"
  # 2.0.0 Task 0c (D53): chặng rwi skip + gap SAU G11/H6a (hàng của chặng không vào G11); hỏng/invalid thì lượt không PASS
  . "$(dirname "${BASH_SOURCE[0]}")/rwi_legs.sh" && rwi_legs_run "$pre-rwi" || { mg_say "FAIL: chặng rwi (skip/gap) không PASS hoặc invalid"; fail=1; }
  mg_count_crashes "chặng rwi"
  mg_say "sink jsonl lines before=$3 after=$(mg_jsonl_lines)"
  mg_say "FATAL=$MG_FATAL am_anr=$MG_ANR native=$MG_NATIVE (need 0/0/0)"
  [ "$MG_FATAL" -eq 0 ] && [ "$MG_ANR" -eq 0 ] && [ "$MG_NATIVE" -eq 0 ] || fail=1
  [ "${MG_CAPTURE_FAIL:-0}" -eq 0 ] && ! grep -q '^FAIL: adb_b timeout' "${MG_LOG:-/dev/null}" || fail=1
  mg_say "RESULT: $([ "$fail" -eq 0 ] && echo PASS || echo FAIL)"
  return "$fail"
}

mg_rows() { tail -n +$(($1 + 1)) "$MG_JSONL" 2> /dev/null | grep -F "\"event_name\": \"$2\"" | grep -F "\"placement\": \"$3" | grep -cF "\"ad_format\": \"$4"; }   # 2.4.0 DEMO-RES: hàng sink sau dòng $1: event_name $2, placement bắt đầu $3, ad_format bắt đầu $4 ("" = bất kỳ)
mg_show_full2() {   # "<nhãn>" (D8-240): app mới → nút Full2 (cha: mg_show_full, về app đóng cha) → chờ ≤ 15 s ad_show full_2_*; return 0/1/4
  local lbl=$1 ppl j0 p=1 f rc=0 i=0
  case $lbl in "Interstitial → Full2") ppl=full_default;; "Rewarded → Full2") ppl=gift_default;; "Show NtFull Full Screen (Full2)") ppl="";; *) mg_say "FAIL: show_full2 không biết nút '$lbl'"; return 1;; esac
  [ -n "${MG_W:-}" ] || mg_screen_init
  adb_b 60 shell am force-stop "$MG_PKG" > /dev/null 2>&1; mg_start || return 1
  mg_sleep 20; j0=$(mg_jsonl_lines)
  if [ -n "$ppl" ]; then mg_show_full "$lbl" || rc=$?; else mg_tap_verified "$lbl" showFull2 || rc=$?   # rc 4 (focus: Full2 che app): hàng sink quyết định
    while [ "$i" -lt 15 ] && [ "$(mg_rows "$j0" ad_fill full_2_ "")" -lt 1 ]; do mg_sleep 1; i=$((i + 1)); done; i=0; [ "$(mg_rows "$j0" ad_show full_2_ "")" -ge 1 ] || mg_tap_verified "$lbl" showFull2 || rc=$?; fi
  [ "$rc" -ne 1 ] || return 1; while [ "$i" -lt 15 ] && [ "$(mg_rows "$j0" ad_show full_2_ "")" -lt 1 ]; do mg_sleep 1; i=$((i + 1)); done
  mg_sleep 3; f=$(mg_rows "$j0" ad_show full_2_ ""); [ -z "$ppl" ] || p=$(mg_rows "$j0" ad_show "$ppl" "")
  mg_say "show_full2 '$lbl': parent=$p full2=$f (cần 1/1)"; [ "$p" -eq 1 ] && [ "$f" -eq 1 ] && return 0
  mg_say "FAIL: show_full2 '$lbl': parent=$p full2=$f (cần 1/1)"; return 1
}
mg_resume_leg() {   # $1 = nút (đổi ON/OFF) → HOME → am start → chờ ≤ MG_RESUME_WAIT s ad_show placement full_resume (KB-1: không phải ad_format app_open); MG_RS = số hàng
  local xy j0 i=0
  xy=$(mg_find_text "$1") || return 4
  adb_b 60 shell input tap $xy > /dev/null 2>&1; mg_sleep 2; j0=$(mg_jsonl_lines)
  adb_b 60 shell input keyevent KEYCODE_HOME > /dev/null 2>&1; mg_sleep 3; adb_b 60 shell am start -n "$MG_PKG/$MG_ACT" > /dev/null 2>&1
  while [ "$i" -lt "${MG_RESUME_WAIT:-10}" ] && [ "$(mg_rows "$j0" ad_show full_resume "")" -lt 1 ]; do mg_sleep 1; i=$((i + 1)); done
  mg_sleep 3; MG_RS=$(mg_rows "$j0" ad_show full_resume ""); [ "$MG_RS" -eq 0 ] || mg_back_in_app "app open"   # grep -c = 0 trả rc 1 (không phải lỗi); có quảng cáo thì về app để lượt sau thấy nút
}
mg_resume_open() {   # --resume ($1 = tiền tố, $2 = JSONL sink): pm clear, mở app, ON → HOME → mở lại: +1; chờ MG_RESUME_GAP s (> openAdDeltaTime 15) → OFF → +0
  local on off i=0 j; MG_JSONL=$2; j=$(mg_jsonl_lines); [ -n "${MG_W:-}" ] || mg_screen_init
  [ "$(adb_b 60 shell pm clear "$MG_PKG" 2>&1 | tr -d '\r')" = Success ] || { mg_say "FAIL: pm clear $MG_PKG"; mg_say "RESULT: FAIL"; return 1; }
  mg_start || { mg_say "RESULT: FAIL"; return 1; }; while [ "$i" -lt 20 ] && [ "$(mg_rows "$j" ad_fill full_resume "")" -lt 1 ]; do mg_sleep 1; i=$((i + 1)); done   # nạp xong open ad (m1)
  mg_resume_leg "App open resume: OFF" || { mg_say "RESULT: INVALID ui"; return 4; }
  on=$MG_RS; mg_say "resume_open ON: shows=$on (cần 1)"; mg_sleep "${MG_RESUME_GAP:-20}"
  mg_resume_leg "App open resume: ON" || { mg_say "RESULT: INVALID ui"; return 4; }
  off=$MG_RS; mg_say "resume_open OFF: shows=$off (cần 0)"; mg_count_crashes "resume"
  [ "$on" -eq 1 ] && [ "$off" -eq 0 ] && [ "$MG_FATAL$MG_ANR$MG_NATIVE${MG_CAPTURE_FAIL:-0}" = 0000 ] && { mg_say "RESULT: PASS"; return 0; }
  mg_say "RESULT: FAIL"; return 1
}
mg_full2_all() { local l rc=0   # 3 nút Full2 rồi quét crash/ANR/native (measure_run đã xoá buffer trước chặng này); return 0 / 1
  for l in "Interstitial → Full2" "Rewarded → Full2" "Show NtFull Full Screen (Full2)"; do mg_show_full2 "$l" || rc=1; done; mg_count_crashes "full2"
  [ "$MG_FATAL$MG_ANR$MG_NATIVE${MG_CAPTURE_FAIL:-0}" = 0000 ] || { mg_say "FAIL: crash/ANR trong Full2"; rc=1; }; return "$rc"
}
