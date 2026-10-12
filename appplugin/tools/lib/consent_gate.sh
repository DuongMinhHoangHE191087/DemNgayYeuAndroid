# shellcheck shell=bash
# 2.0.0 Task 0d (E-emu-37 #13): kịch bản --consent trên một máy (APK utility release), thay driver tạm của 1.8.0/1.9.0. Nạp bởi
# tools/device_gate.sh --consent SAU tools/lib/measure_gate.sh (dùng mg_*); mọi lệnh tới máy qua adb_b. MG_NO_SLEEP=1 chỉ cho test.
# Quy trình: pm clear -> chạy thử <= 3 lần với `--ez force_eea true` tới khi TotalTime < 5000 ms VÀ form UMP hiện (#29; không thì
# `RESULT: INVALID (cold guest)`, exit 4, không tính) -> lần đo: mở lại, chạm "Do not consent" (DENIED), chạm "Show Rewarded Interstitial",
# cửa sổ DENIED ~60 s đếm hàng JSONL và dòng POST của sink lane (<dir>/sink-<port>.log): PHẢI 0 hàng và 0 POST (R-P1, Q2) ->
# Privacy options -> Consent (GRANTED) -> cửa sổ flush 45 s: JSONL PHẢI tăng (các hàng bị giữ được đẩy đi).
# Fail-closed (#11): log sink không đọc được, thiếu nút, không flush, FATAL/ANR/native > 0 đều FAIL; "0 POST" chỉ có nghĩa khi đọc được log.
# Process đổi pid/mất trong cửa sổ DENIED: có crash -> FAIL; có dòng `Killing <pid>:<pkg> ... killDueToPackageUpdate` của chính pid đó -> `RESULT: INVALID (process restart: package update)`;
# còn lại -> FAIL (process gone). Return: 0 PASS, 1 FAIL, 4 INVALID.

CG_TT_MAX=5000   # ms: TotalTime của máy ấm (#29)
CG_DENIED_S=60   # s: độ dài tối thiểu của cửa sổ DENIED
. tools/lib/consent_miui.sh || return 1   # 2.1.0 Task 0b: chạm form có xác minh (cm_*)

# Số dòng POST trong log sink $1; không đọc được thì return 1 (không bao giờ coi là 0).
cg_posts() {
  local n
  [ -r "$1" ] || return 1
  n=$(grep -c -- '"POST ' "$1" 2>/dev/null)
  echo "${n:-0}"
}

# `am start -W --ez force_eea true`: đặt CG_TT (TotalTime ms); return 1 nếu quá hạn hoặc không có TotalTime.
cg_start() {
  local out rc
  out=$(adb_b 60 shell am start -W -n "$MG_PKG/$MG_ACT" --ez force_eea true 2>&1); rc=$?
  out=$(printf '%s\n' "$out" | tr -d '\r' | grep -E 'Status|TotalTime|WaitTime|Error')
  mg_say "  am start -W --ez force_eea true: $(echo "$out" | tr '\n' ' ')"
  [ "$rc" -ne 124 ] || { mg_say "FAIL: am start -W quá hạn"; return 1; }
  CG_TT=$(printf '%s\n' "$out" | sed -n 's/^TotalTime: *\([0-9][0-9]*\).*/\1/p' | head -1)
  [ -n "$CG_TT" ] || { mg_say "FAIL: am start -W không in TotalTime"; return 1; }
}

# Tâm node hiển thị của form UMP có chữ (hoặc content-desc) $1: ≤ 3 lần dump. In "x y" hoặc return 4.
cg_form_xy() {
  local i=0 xy
  while [ "$i" -lt 3 ]; do
    adb_b 30 shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
    xy=$(adb_b 60 shell cat /sdcard/ui.xml 2> /dev/null | tr '>' '\n' | grep -F -e "text=\"$1\"" -e "content-desc=\"$1\"" | mg_first_visible)
    [ -z "$xy" ] || { echo "$xy"; return 0; }
    i=$((i + 1)); mg_sleep 3
  done
  return 4
}

# $1 = tiền tố file, $2 = JSONL của sink, $3 = số dòng JSONL trước cổng, $4 = cổng sink, $5 = thư mục sink. In "RESULT: PASS|FAIL|INVALID (…)".
consent_run() {
  local pre=$1 sinklog=$5/sink-$4.log fail=0 i form warm=0 clear t1 p1 t1e p1e g xy pid0 pid1 w0 secs fl
  MG_JSONL=$2
  mg_say "consent: pm clear, chạy thử <= 3 (#29), force_eea, Do not consent, Show Rewarded Interstitial, cửa sổ DENIED, Privacy options -> Consent, flush; jsonl=$MG_JSONL sink log=$sinklog"
  mg_screen_init
  clear=$(adb_b 60 shell pm clear "$MG_PKG" 2>&1 | tr -d '\r')
  [ "$clear" = Success ] || { mg_say "FAIL: pm clear $MG_PKG: $clear"; mg_say "RESULT: FAIL"; return 1; }
  for i in 1 2 3; do
    cg_start || { mg_say "RESULT: FAIL"; return 1; }
    mg_sleep 10
    form=0; cm_form_present && form=1
    mg_say "precondition #29 chạy thử $i: TotalTime=$CG_TT ms, form UMP=$form (cần < $CG_TT_MAX và form hiện)"
    adb_b 60 shell am force-stop "$MG_PKG" > /dev/null 2>&1
    if [ "$CG_TT" -lt "$CG_TT_MAX" ] && [ "$form" = 1 ]; then warm=1; break; fi
  done
  [ "$warm" = 1 ] || { mg_say "invalid (cold guest): 3 lần chạy thử không có TotalTime < $CG_TT_MAX ms cùng form UMP"; mg_say "RESULT: INVALID (cold guest)"; return 4; }
  adb_b 60 logcat -c > /dev/null 2>&1
  cg_start || { mg_say "RESULT: FAIL"; return 1; }
  [ "$CG_TT" -lt "$CG_TT_MAX" ] || { mg_say "invalid (cold guest): lần đo TotalTime=$CG_TT ms (cần < $CG_TT_MAX, #29)"; mg_say "RESULT: INVALID (cold guest)"; return 4; }
  pid0=$(adb_b 60 shell pidof "$MG_PKG" 2>/dev/null | tr -d '\r')
  [ -n "$pid0" ] || { mg_say "FAIL: không thấy process $MG_PKG sau am start"; mg_say "RESULT: FAIL"; return 1; }
  mg_sleep 20
  cm_tap_form "Do not consent" || { mg_say "RESULT: FAIL"; return 1; }
  t1=$(mg_jsonl_lines); p1=$(cg_posts "$sinklog") || { mg_say "FAIL: log của sink không đọc được ($sinklog): không đếm được POST"; mg_say "RESULT: FAIL"; return 1; }
  w0=$SECONDS   # cửa sổ DENIED tính từ lúc chạm "Do not consent"
  mg_say "DENIED at jsonl=$t1 POST=$p1"
  mg_sleep 8
  mg_tap_verified "Show Rewarded Interstitial" "showRwi call show" || { mg_say "FAIL: không chạm được 'Show Rewarded Interstitial'"; fail=1; }
  mg_sleep 30
  cm_back_in_app "rwi" || { mg_say "FAIL: không về được MainActivity sau rwi"; fail=1; }
  secs=$((SECONDS - w0)); [ "$secs" -ge "$CG_DENIED_S" ] || mg_sleep $((CG_DENIED_S - secs))
  secs=$((SECONDS - w0))
  t1e=$(mg_jsonl_lines); p1e=$(cg_posts "$sinklog") || { mg_say "FAIL: log của sink không đọc được ($sinklog)"; mg_say "RESULT: FAIL"; return 1; }
  pid1=$(adb_b 60 shell pidof "$MG_PKG" 2>/dev/null | tr -d '\r')
  # Pid đổi: đếm crash TRƯỚC (FATAL/native/ANR > 0 = FAIL, không bao giờ INVALID). INVALID chỉ khi logcat có dòng kill do cập nhật gói của chính app
  # (vd Play Services tự cập nhật: lần mở lại không có force_eea, UMP trả NOT_REQUIRED, tier tự GRANTED, hàng bị giữ đi ra); còn lại = FAIL (process gone).
  if [ "$pid1" != "$pid0" ]; then
    mg_save_logs "$pre"; mg_count_crashes consent
    if [ "$MG_FATAL" -ne 0 ] || [ "$MG_NATIVE" -ne 0 ] || [ "$MG_ANR" -ne 0 ]; then
      mg_say "FAIL: process đổi pid $pid0 -> '$pid1' trong cửa sổ DENIED kèm crash FATAL=$MG_FATAL native=$MG_NATIVE am_anr=$MG_ANR"; mg_say "RESULT: FAIL"; return 1
    fi
    if grep -qE "Killing $pid0:$MG_PKG.*killDueToPackageUpdate" "$pre.log" 2>/dev/null; then
      mg_say "invalid (process restart: package update): pid $pid0 -> '$pid1' trong cửa sổ DENIED"; mg_say "RESULT: INVALID (process restart: package update)"; return 4
    fi
    mg_say "FAIL: process gone: pid $pid0 -> '$pid1' trong cửa sổ DENIED, không có dòng killDueToPackageUpdate"; mg_say "RESULT: FAIL"; return 1
  fi
  mg_say "consent: DENIED window rows=$((t1e - t1)) post=$((p1e - p1)) in ${secs}s (need 0/0 in >= ${CG_DENIED_S}s; jsonl $t1 -> $t1e, POST $p1 -> $p1e)"
  { [ "$t1e" -eq "$t1" ] && [ "$p1e" -eq "$p1" ]; } || { mg_say "FAIL: hàng/POST tới sink trong cửa sổ DENIED (R-P1)"; fail=1; }
  cm_dismiss_ad
  xy=$(mg_find_text "Privacy options") || { mg_say "FAIL: không thấy nút 'Privacy options'"; mg_say "RESULT: FAIL"; return 1; }
  adb_b 60 shell input tap $xy > /dev/null 2>&1
  mg_say "  tap 'Privacy options' xy=$xy"
  mg_sleep 5
  cm_tap_form "Consent" || fail=1
  mg_sleep 45
  g=$(mg_jsonl_lines)
  mg_say "consent: GRANTED flush window 45 s: jsonl $t1e -> $g (+$((g - t1e)); need > 0)"
  [ "$g" -gt "$t1e" ] || { mg_say "FAIL: sau GRANTED JSONL của sink không tăng: các hàng bị giữ không được đẩy đi"; fail=1; }
  mg_save_logs "$pre"
  fl=$(grep -m1 'flushed [1-9][0-9]* held after DENIED' "$pre-app.log" 2>/dev/null | sed 's/^.* AppPlugin: //')
  mg_say "consent: ${fl:-<không có dòng 'flushed N held after DENIED'>}"
  [ -n "$fl" ] || { mg_say "FAIL: không có 'flushed N held after DENIED' (tier DENIED chưa từng giữ hàng)"; fail=1; }
  mg_count_crashes consent
  mg_say "FATAL=$MG_FATAL am_anr=$MG_ANR native=$MG_NATIVE (need 0/0/0)"
  { [ "$MG_FATAL" -eq 0 ] && [ "$MG_ANR" -eq 0 ] && [ "$MG_NATIVE" -eq 0 ]; } || fail=1
  { [ "${MG_CAPTURE_FAIL:-0}" -eq 0 ] && ! grep -q '^FAIL: adb_b timeout' "${MG_LOG:-/dev/null}"; } || fail=1
  mg_say "RESULT: $([ "$fail" -eq 0 ] && echo PASS || echo FAIL)"
  return "$fail"
}
