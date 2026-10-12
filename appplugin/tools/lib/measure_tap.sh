# shellcheck shell=bash
# 2.4.0 FIX-GATE (D25-240): điểm chạm chỉ nằm trong phần HIỂN THỊ của cửa sổ, trên thanh điều hướng 3 nút (Samsung: nút cuối của demo chỉ
# hở một sợi cạnh thanh, tâm sợi đó = nút HOME → mất app, lượt đo INVALID). Nạp bởi measure_gate.sh (mg_first_visible dùng ở ui_find/consent).
# MG_NAVTOP = y đỉnh vùng không chạm được ở đáy (InsetsSource type=tappableElement / ITYPE_BOTTOM_TAPPABLE_ELEMENT trong `dumpsys activity top`:
# rỗng [0,0][0,0] khi điều hướng cử chỉ → 0; không đọc được → 0 = hành vi cũ). Đọc một lần mỗi lần chạy (mg_screen_init gọi mg_navtop_init).
MG_NAVTOP=${MG_NAVTOP:-0}
MG_MIN_VIS=${MG_MIN_VIS:-48}   # px: node bị thanh cắt phải còn ≥ ngần này phía trên thanh mới được chọn (không thì coi như chưa thấy → kéo)
MG_MIN_BAND=${MG_MIN_BAND:-16}   # px: ngưỡng của NÚT CUỐI (danh sách hết cuộn mà nút vẫn chỉ hở dải này; Samsung: 27 px): ui_find chạm tâm dải đó, không bao giờ dưới đỉnh thanh

MG_GESTURE_MARGIN=${MG_GESTURE_MARGIN:-48}   # px: cử chỉ vuốt-lên-từ-đáy bắt đầu trong dải mandatorySystemGestures; chạm cách đỉnh dải ≥ ngần này (emu37: tap y=2938/2992 = startRecentsAnimation)
# Đỉnh (y) của inset đáy đầu tiên thuộc loại $1 (ERE) trong dump (stdin) có frame khác rỗng nằm nửa dưới màn hình; rỗng nếu không có.
mg_inset_top() {
  sed -nE "s/.*type=($1) .*frame=\\[[0-9]+,([0-9]+)\\]\\[[0-9]+,([0-9]+)\\] visible=true.*/\\2 \\3/p" \
    | awk -v h="${MG_H:-0}" '$2 > $1 && $1 > 0 && $1 * 2 > h { print $1; exit }'
}
# 3 nút: tappableElement. Cử chỉ (tappableElement rỗng): dải mandatorySystemGestures (rồi systemGestures, navigationBars) trừ MG_GESTURE_MARGIN.
mg_navtop_init() {
  local d t g
  d=$(adb_b 60 shell dumpsys activity top 2> /dev/null | tr -d '\r'); MG_NAVTOP=0
  t=$(printf '%s\n' "$d" | mg_inset_top 'tappableElement|ITYPE_BOTTOM_TAPPABLE_ELEMENT')
  if [ -n "$t" ]; then MG_NAVTOP=$t; mg_say "  thanh điều hướng 3 nút: vùng chạm được kết thúc ở y=$MG_NAVTOP (node bị cắt chỉ chạm phần trên, còn >= ${MG_MIN_VIS}px)"; return 0; fi
  for g in 'mandatorySystemGestures|ITYPE_BOTTOM_MANDATORY_GESTURES' 'systemGestures|ITYPE_[A-Z_]*GESTURES' 'navigationBars|ITYPE_NAVIGATION_BAR'; do
    t=$(printf '%s\n' "$d" | mg_inset_top "$g")
    [ -z "$t" ] || { MG_NAVTOP=$((t - MG_GESTURE_MARGIN)); mg_say "  vùng chạm kết thúc ở y=$MG_NAVTOP (cử chỉ: dải đáy từ y=$t, lề ${MG_GESTURE_MARGIN}px)"; return 0; }
  done
}

# Tâm node HIỂN THỊ đầu tiên trong các dòng node (stdin, đã lọc): bỏ node bounds [0,0][0,0] / diện tích 0 (UMP có node ẩn cùng chữ đứng trước
# node thật; 1.8.0 TF-1), node nhỏ hơn 8x8 px (vết, 1.9.0 Task 0d); với MG_NAVTOP > 0: cắt đáy tại MG_NAVTOP, bỏ node còn < MG_MIN_VIS px
# phía trên thanh ($1 = ngưỡng khác MG_MIN_VIS, vd MG_MIN_BAND), chạm tâm phần còn lại. In "x y", rỗng nếu không có node hiển thị.
mg_first_visible() {
  sed -nE 's/.*bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]".*/\1 \2 \3 \4/p' \
    | awk -v nav="${MG_NAVTOP:-0}" -v mv="${1:-${MG_MIN_VIS:-48}}" '$3 - $1 >= 8 {
        b = $4; if (nav > 0 && b > nav) { b = nav; if (b - $2 < mv) next }
        if (b - $2 >= 8) { printf "%d %d\n", ($1 + $3) / 2, ($2 + b) / 2; exit } }'
}
