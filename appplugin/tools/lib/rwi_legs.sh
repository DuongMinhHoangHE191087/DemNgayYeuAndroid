# shellcheck shell=bash
# 2.0.0 Task 0c (D53, #43): hai chặng rewarded interstitial của --measure mà trước đây "chưa đo (không có driver)". Nạp bởi measure_gate.sh
# (measure_run) SAU G11/H6a nên hàng của chặng không vào G11; mọi lệnh tới máy qua adb_b + mg_*; MG_NO_SLEEP=1 chỉ cho test.
# chặng skip: chạm "Show Rewarded Interstitial" -> intro (đếm ngược 5 s) -> NGAY chạm chữ "Skip"/"Bỏ qua" (D33) -> sink có đúng 1 hàng
#   ad_show_fail reason=rwi_intro_skipped placement=rwi_default và 0 hàng ad_show của rwi_default trong chặng.
# chặng gap: "Show Interstitial" (mg_show_full) rồi NGAY "Show Rewarded Interstitial" trong min-gap -> đúng 1 hàng reason=policy_min_gap placement=rwi_default.
# Return: 0 PASS, 1 FAIL, 4 invalid (giao diện: không tới được nút/chữ, không tính). Hàng sink chậm: thăm tối đa 12 x 5 s.

RW_LABEL="Show Rewarded Interstitial"
RW_PL=rwi_default

# Đếm hàng sink sau dòng $1: event_name=$2, reason=$3 (rỗng = bất kỳ), placement rwi_default.
rw_rows() {
  tail -n +$(($1 + 1)) "$MG_JSONL" 2> /dev/null | "${MEASURE_PY:-python3}" -c '
import json, sys
ev, rs, pl = sys.argv[1:4]
n = 0
for ln in sys.stdin:
    try:
        r = json.loads(ln)
    except ValueError:
        continue
    if r.get("event_name") == ev and r.get("placement") == pl and (not rs or r.get("reason") == rs):
        n += 1
print(n)' "$2" "$3" "$RW_PL"
}

# Chờ hàng reason $3 của ad_show_fail tới sink (>= 1, ≤ 12 lần cách 5 s) rồi thêm một nhịp để hàng thừa kịp tới; in số hàng cuối.
rw_wait_reason() {
  local i=0
  while [ "$i" -lt 12 ] && [ "$(rw_rows "$1" ad_show_fail "$2")" -lt 1 ]; do mg_sleep 5; i=$((i + 1)); done
  mg_sleep 5
  rw_rows "$1" ad_show_fail "$2"
}

# Chữ đầu tiên của $1.. hiển thị trong dump hiện tại: in "x y"; return 1 nếu không có.
rw_text_xy() {
  local xml t xy
  adb_b 30 shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
  xml=$(adb_b 60 shell cat /sdcard/ui.xml 2> /dev/null)
  for t in "$@"; do
    xy=$(printf '%s' "$xml" | mg_bounds_of "$t")
    [ -z "$xy" ] || { echo "$xy"; return 0; }
  done
  return 1
}

# Chặng skip. $1 = tiền tố file (logcat của chặng lưu ở $1-skip.log). Intro chỉ sống 5 s: sau lần chạm rwi KHÔNG ngủ, dump ngay (≤ 4 lần) rồi
# chạm chữ bỏ qua. Quảng cáo chưa nạp (`show refused: show_not_loaded`, SDK nạp lại): chờ onAdLoaded MỚI (≤ 30 s) rồi chạm lại, tối đa 3 lượt.
rw_leg_skip() {
  local j0 xy sk="" try attempt=0 l0 i skipped shown missed=0
  j0=$(mg_jsonl_lines)
  mg_back_in_app "rwi skip" || return $?
  while [ "$attempt" -lt 3 ] && [ -z "$sk" ]; do
    attempt=$((attempt + 1)); try=0
    l0=$(mg_count "onAdLoaded: pl=$RW_PL .*format=rewarded_interstitial")
    xy=$(mg_find_text "$RW_LABEL") || { mg_say "invalid (ui): không thấy nút '$RW_LABEL' (chặng skip)"; return 4; }
    adb_b 60 shell input tap $xy > /dev/null 2>&1
    mg_say "  rwi skip: tap '$RW_LABEL' xy=$xy (lượt $attempt)"
    while [ "$try" -lt 4 ] && [ -z "$sk" ]; do try=$((try + 1)); sk=$(rw_text_xy "Skip" "Bỏ qua") || sk=""; done
    [ -z "$sk" ] || break
    missed=1
    mg_say "  rwi skip: không thấy 'Skip'/'Bỏ qua' (intro không hiện: chưa nạp, hoặc dump chậm hơn 5 s đếm ngược); về app, chờ onAdLoaded mới"
    mg_back_in_app "rwi skip" > /dev/null; i=0
    while [ "$i" -lt 30 ] && [ "$(mg_count "onAdLoaded: pl=$RW_PL .*format=rewarded_interstitial")" -le "$l0" ]; do mg_sleep 1; i=$((i + 1)); done
  done
  if [ -z "$sk" ]; then mg_say "invalid (intro missed): không thấy chữ 'Skip'/'Bỏ qua' của intro sau $attempt lượt chạm"; mg_save_logs "$1-skip"; return 4; fi
  adb_b 60 shell input tap $sk > /dev/null 2>&1
  mg_say "  rwi skip: tap skip xy=$sk"
  mg_sleep 3
  skipped=$(rw_wait_reason "$j0" rwi_intro_skipped); shown=$(rw_rows "$j0" ad_show "")
  mg_save_logs "$1-skip"
  mg_say "rwi skip: rwi_intro_skipped=$skipped (cần 1) ad_show rwi=$shown (cần 0)"
  [ "$skipped" -eq 1 ] && [ "$shown" -eq 0 ] && return 0
  if [ "$missed" -eq 1 ] && [ "$shown" -gt 0 ]; then mg_say "invalid (intro missed): một lượt chạm trước lỡ cửa sổ intro 5 s (ad_show $RW_PL=$shown từ lượt đó): không tính, chạy lại"; return 4; fi
  mg_say "FAIL: chặng skip: rwi_intro_skipped=$skipped (cần 1), ad_show $RW_PL=$shown (cần 0)"; return 1
}

# Chặng gap: Interstitial vừa hiện thì rwi bị chặn bởi gap chung (không có `call show` của rwi, có `show refused: policy_min_gap`).
rw_leg_gap() {   # $1 = tiền tố file ($1-gap.log)
  local j0 gap shown r pat c0 i=0
  j0=$(mg_jsonl_lines)
  mg_show_full "Show Interstitial" || { r=$?; mg_say "invalid: chặng gap không hiện được Interstitial (rc=$r)"; return "$r"; }
  # 2.4.0 FIX-GATE 4 (D30): KHÔNG tìm nút (ui_find ≈ 46 s trên Samsung > policy_min_gap 30 s: rwi hiện thật, AdActivity phủ màn hình) mà drive qua intent; chờ ≤ 5 s dòng bị chặn.
  pat="ads rwi helper $RW_PL show refused: policy_min_gap"; c0=$(mg_count "$pat")
  # FIX-GATE 5: am start có clear-top/single-top (mg_drive) + kiểm dòng demo `drive=intent`; không tới = invalid (4), tới mà SDK không từ chối theo gap = FAIL; logcat chặng luôn được lưu
  mg_drive show_rwi || { mg_save_logs "$1-gap"; mg_say "invalid (drive): intent không tới (không có 'drive=intent action=show_rwi'; APK không có cờ test, hoặc onNewIntent không chạy)"; return 4; }
  while [ "$i" -lt 5 ] && [ "$(mg_count "$pat")" -le "$c0" ]; do mg_sleep 1; i=$((i + 1)); done
  [ "$(mg_count "$pat")" -gt "$c0" ] || { mg_save_logs "$1-gap"; mg_say "FAIL: chặng gap: rwi không bị chặn bởi gap (logcat không có 'show refused: policy_min_gap'; demo nhận drive nhưng SDK không từ chối theo gap)"; return 1; }
  gap=$(rw_wait_reason "$j0" policy_min_gap); shown=$(rw_rows "$j0" ad_show "")
  mg_save_logs "$1-gap"
  mg_say "rwi gap: policy_min_gap=$gap (cần 1) ad_show rwi=$shown (cần 0)"
  [ "$gap" -eq 1 ] && [ "$shown" -eq 0 ] && return 0
  mg_say "FAIL: chặng gap: policy_min_gap=$gap (cần 1), ad_show $RW_PL=$shown (cần 0)"; return 1
}

# 2.1.0 T0e (M31): chặng 1 = lượt rwi đầy đủ của pha A ($1 = MG_A): intro (`showRwi call show`), ad_show (sink $1.jsonl), reward, dismiss: mỗi mốc đúng 1.
rw_leg_full() {
  local a=$1 pat="ads rwi admob[0-9]* $RW_PL $RW_PL" intro shown rew dis
  intro=$(grep -c "ads rwi helper $RW_PL showRwi call show" "$a.log" 2> /dev/null); rew=$(grep -cE "$pat OnUserEarnedRewardListener" "$a.log" 2> /dev/null)
  dis=$(grep -cE "$pat ondissmiss" "$a.log" 2> /dev/null); shown=$(MG_JSONL=$a.jsonl rw_rows 0 ad_show "")
  mg_say "rwi chặng 1 (pha A): intro=$intro ad_show=$shown reward=$rew dismiss=$dis (mỗi mốc cần 1)"
  [ "$intro$shown$rew$dis" = 1111 ] || { mg_say "FAIL: chặng 1 rwi: mốc ≠ 1 (intro=$intro ad_show=$shown reward=$rew dismiss=$dis)"; return 1; }
}

# $1 = tiền tố file. Return 0 cả hai PASS; else 1 (invalid cũng làm lượt không PASS: in rõ ở dòng "invalid").
rwi_legs_run() {
  local fail=0
  mg_say "rwi legs: skip (Show Rewarded Interstitial rồi Skip/Bỏ qua) + gap (Show Interstitial rồi rwi trong min-gap); jsonl=$MG_JSONL"
  [ -z "${MG_A:-}" ] || rw_leg_full "$MG_A" || fail=1
  rw_leg_skip "$1" || fail=1
  rw_leg_gap "$1" || fail=1
  return "$fail"
}
