# shellcheck shell=bash
# 2.1.0 Task 0c (M46, §E): tìm chữ trên giao diện demo bền trên One UI (Samsung) — rèm thông báo giành focus và che nút.
# Dùng chung cho measure_gate (mg_tap_verified), iap_gate (iap_tap_label), consent/rwi/drc/banners qua mg_find_text. Cần adb_b, mg_say,
# mg_sleep, mg_bounds_of, mg_focus_is, mg_focus, MG_PKG, MG_ACT (measure_gate.sh nạp lib này; hàm được gọi lúc chạy nên thứ tự nạp không quan trọng).
#
# ui_find "<chữ>": ① `cmd statusbar collapse` (thu rèm) ② dump ③ nếu cửa sổ focus ≠ demo thì ĐÚNG MỘT `KEYCODE_BACK`, dump lại
# ④ không thấy → kéo CHẬM ≤ 6 xuống rồi ≤ 12 lên (dump sau mỗi bước) ⑤ vẫn không thấy: nếu UI_DRIVE_INTENT=1 (--drive-intent) và nhãn
# có hành động → `am start -n <pkg>/<act> --es drive <action>` (demo gọi đúng hàm click của nút, log `drive=intent action=<a>`),
# không bật/không có hành động → `INVALID (ui)`.
# Trả: 0 + "x y" (thấy: caller chạm) | 5 (đã chạy qua intent: caller KHÔNG chạm, tự kiểm bằng logcat) | 4 = INVALID (ui).

# Hành động drive của demo (MainActivity.runDrive) theo nhãn nút; nhãn khác = không có hành động.
ui_drive_action() {
  case $1 in
    "Show Interstitial") echo show_interstitial;;
    "Show Rewarded") echo show_rewarded;;
    "Show Rewarded Interstitial") echo show_rwi;;   # 2.4.0 FIX-GATE 4: rw_leg_gap drive thẳng (ui_find ≈ 46 s > policy_min_gap 30 s)
    *) return 1;;
  esac
}

# Kéo CHẬM (600 ms: gần như không fling, trượt thêm ≈ 125 px trên Redmi, nhỏ hơn nhiều phần chồng giữa hai khung) 30 % chiều cao màn hình: $1 = down (ngón đi lên, thấy phần dưới của danh sách) hoặc up.
mg_scroll() {
  local w=${MG_W:-1080} h=${MG_H:-2400} x y1 y2
  x=$((w / 2)); y1=$((h * 70 / 100)); y2=$((h * 40 / 100))
  if [ "$1" = down ]; then adb_b 60 shell input swipe "$x" "$y1" "$x" "$y2" 600 > /dev/null 2>&1
  else adb_b 60 shell input swipe "$x" "$y2" "$x" "$y1" 600 > /dev/null 2>&1; fi
  mg_sleep 1
}

# $1 = chữ; xoá ui.xml cũ (dump hỏng vì hoạt ảnh = file cũ của màn hình khác, chạm toạ độ cũ), dump (thêm ≤ 2 lần nếu không ra file), rồi in HAI dòng:
# "x y" chặt (MG_MIN_VIS) và "x y" lỏng (MG_MIN_BAND, nút cuối chỉ hở dải trên thanh điều hướng); dòng rỗng nếu không thấy.
# mg_drive <action>: `am start --es drive` với --activity-clear-top --activity-single-top (MainActivity launch mode standard, task đang ở trước: không cờ thì chỉ đưa task lên, "Activity not started",
# onNewIntent/runDrive không chạy, SDK không được gọi; 2.4.0 FIX-GATE 5). Ghi đầu ra của am; chờ ≤ 5 s dòng demo `drive=intent action=<a>` MỚI. return 0 thấy, 1 không (intent không tới).
mg_drive() {
  local c0 i=0 out pat="drive=intent action=$1"
  c0=$(mg_count "$pat")
  out=$(adb_b 60 shell am start -n "$MG_PKG/$MG_ACT" --activity-clear-top --activity-single-top --es drive "$1" 2>&1 | tr -d '\r' | tr '\n' ' ')
  mg_say "  drive $1: am start: ${out:-<rỗng>}" >&2
  while [ "$i" -lt 5 ] && [ "$(mg_count "$pat")" -le "$c0" ]; do mg_sleep 1; i=$((i + 1)); done
  [ "$(mg_count "$pat")" -gt "$c0" ]
}
# Đóng rèm thông báo trước khi chạy lại một pha (rèm giành focus làm lượt sau INVALID): thu rèm; focus vẫn NotificationShade thì một KEYCODE_BACK.
mg_shade_reset() {
  adb_b 30 shell cmd statusbar collapse > /dev/null 2>&1; mg_sleep 1
  ! mg_focus_is NotificationShade || { adb_b 60 shell input keyevent KEYCODE_BACK > /dev/null 2>&1; mg_sleep 1; }
}
ui_dump_xy() {
  local xml i=0
  while [ "$i" -lt 3 ]; do
    i=$((i + 1)); [ "$i" -le 1 ] || mg_sleep 1; adb_b 30 shell rm -f /sdcard/ui.xml > /dev/null 2>&1; adb_b 30 shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
    xml=$(adb_b 60 shell cat /sdcard/ui.xml 2> /dev/null); [ -z "$xml" ] || break
  done
  printf '%s\n%s\n' "$(printf '%s' "$xml" | mg_bounds_of "$1")" "$(printf '%s' "$xml" | mg_bounds_of "$1" "$MG_MIN_BAND")"
}
ui_look() { local o; o=$(ui_dump_xy "$1"); xy=$(printf '%s\n' "$o" | sed -n 1p); fb=$(printf '%s\n' "$o" | sed -n 2p); }   # đặt xy, fb của ui_find

ui_find() {
  local n=0 xy fb fbp="" dir=down backed=0 act nf=0
  adb_b 30 shell cmd statusbar collapse > /dev/null 2>&1; mg_sleep 1
  while :; do
    ui_look "$1"
    if [ "$backed" = 0 ] && ! mg_focus_is "$MG_PKG"; then
      backed=1; mg_say "  focus không phải demo ($(mg_focus)): một KEYCODE_BACK" >&2
      adb_b 60 shell input keyevent KEYCODE_BACK > /dev/null 2>&1; mg_sleep 2
      # 2.4.0 FIX-GATE 4: vẫn không phải demo (launcher/rèm) thì dừng: kéo trên launcher mở rèm thông báo (emu37d, 18 kéo)
      mg_focus_is "$MG_PKG" || { mg_say "  sau KEYCODE_BACK focus vẫn không phải demo: $(mg_focus) (không kéo)" >&2; nf=1; break; }
      ui_look "$1"
    fi
    [ -z "$xy" ] || { echo "$xy"; return 0; }
    if [ -n "$fb" ] && [ "$fb" = "$fbp" ]; then mg_say "  '$1': nút cuối chỉ hở dải trên thanh điều hướng, cuộn không đổi dump: chạm tâm dải ($fb)" >&2; echo "$fb"; return 0; fi
    fbp=$fb
    if [ "$dir" = down ] && [ "$n" -ge 6 ]; then dir=up; n=0; fi
    if [ "$dir" = up ] && [ "$n" -ge 12 ]; then break; fi
    mg_scroll "$dir"
    n=$((n + 1))
  done
  if [ "${UI_DRIVE_INTENT:-0}" = 1 ] && act=$(ui_drive_action "$1"); then
    mg_say "  không thấy '$1' sau 6 kéo xuống + 12 kéo lên: drive=intent action=$act" >&2
    mg_drive "$act" || { mg_say "invalid (drive): intent không tới (không có 'drive=intent action=$act' trong logcat)" >&2; return 4; }
    mg_sleep 3
    return 5
  fi
  [ -z "${MG_LOG:-}" ] || { adb_b 60 shell cat /sdcard/ui.xml 2> /dev/null; mg_focus; } | redact_stream > "$MG_LOG.ui-fail.txt" 2> /dev/null   # bằng chứng INVALID (ui): dump cuối + focus
  [ "$nf" = 0 ] || { mg_say "INVALID (ui): sau KEYCODE_BACK focus vẫn không phải demo: $(mg_focus)" >&2; return 4; }
  mg_say "INVALID (ui): không thấy '$1' sau 6 kéo xuống + 12 kéo lên" >&2
  return 4
}

# Tên cũ giữ cho mọi caller: in "x y" hoặc return 4|5 như ui_find.
mg_find_text() { ui_find "$@"; }
