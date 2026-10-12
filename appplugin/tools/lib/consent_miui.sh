# shellcheck shell=bash
# 2.1.0 Task 0b (D31, M45, M49, §E): chạm form UMP bền trên MIUI — nạp bởi tools/lib/consent_gate.sh (dùng mg_*, cg_form_xy, adb_b).
# Chạm theo bounds của node từ dump (vắng node: Consent theo toạ độ fixture 0.72 H, thang 72/71/73 % H; không còn chạm mù 0.70 H);
# MỌI chạm form được xác minh bằng dòng của app ≤ 10 s, thử lại ≤ 2 (tổng ≤ 3 chạm), hết thì FAIL; quảng cáo rwi còn che (focus AdActivity) thì `back` trước.
CM_VERIFY_S=10
CM_FIXTURE_PCT="72 71 73"   # % chiều cao: Consent trên Redmi 1080x2400 nằm ở y 1666-1776 (69,4-74,0 %)

# Đóng quảng cáo còn che app: focus AdActivity/ImmersiveModeConfirmation -> KEYCODE_BACK, ≤ 2 lần, kiểm lại focus sau mỗi lần.
cm_dismiss_ad() {
  local i=0
  while [ "$i" -lt 2 ] && mg_ad_focus; do
    i=$((i + 1)); mg_say "  quảng cáo còn che app ($(mg_focus)): back $i/2"
    adb_b 60 shell input keyevent KEYCODE_BACK > /dev/null 2>&1; mg_sleep 3
  done
}

# Về MainActivity sau một quảng cáo: đóng rwi còn che rồi cách cũ (mg_back_in_app).
cm_back_in_app() { cm_dismiss_ad; mg_back_in_app "$1"; }

# Form có mặt sau pm clear: ≤ 3 dump; vắng thì chờ lại đúng 1 lần (15 s) rồi dump lại (UMP/WebView MIUI dựng node trễ). return 0 có, 1 vắng.
cm_form_present() {
  cg_form_xy "Do not consent" > /dev/null && return 0
  mg_say "  form UMP vắng: chờ lại 1/1"; mg_sleep 15
  cg_form_xy "Do not consent" > /dev/null
}

# Đợi ≤ CM_VERIFY_S s (thăm 2 s) tới khi số dòng logcat khớp $1 vượt $2.
cm_wait_line() {
  local i=0
  while [ "$i" -lt $((CM_VERIFY_S / 2)) ]; do
    [ "$(mg_count "$1")" -gt "$2" ] && return 0
    mg_sleep 2; i=$((i + 1))
  done
  [ "$(mg_count "$1")" -gt "$2" ]
}

# Chạm nút $1 ("Consent" | "Do not consent") của form có xác minh: tâm node từ dump, vắng node thì (Consent) toạ độ fixture; thử ≤ 3 chạm.
cm_tap_form() {
  local lbl=$1 pat k=0 xy pct before
  case $lbl in Consent) pat='flushed [1-9][0-9]* held after DENIED';; *) pat='consent tier=DENIED';; esac
  before=$(mg_count "$pat")
  for pct in $CM_FIXTURE_PCT; do
    k=$((k + 1))
    if xy=$(cg_form_xy "$lbl"); then mg_say "  tap form '$lbl' xy=$xy (node) $k/3"
    elif [ "$lbl" = Consent ]; then xy="$((MG_W * 50 / 100)) $((MG_H * pct / 100))"; mg_say "  consent tap fallback $k/3 xy=$xy (không có node, toạ độ fixture)"
    else mg_say "FAIL: không thấy nút '$lbl' của form UMP"; return 1; fi
    adb_b 60 shell input tap $xy > /dev/null 2>&1
    if cm_wait_line "$pat" "$before"; then mg_say "  tap form '$lbl' verified ($pat) $k/3"; mg_sleep 3; return 0; fi
    mg_say "  tap form '$lbl' $k/3: không có '$pat' trong ${CM_VERIFY_S}s"
  done
  mg_say "FAIL: chạm '$lbl' 3 lần, app không báo '$pat'"; return 1
}
