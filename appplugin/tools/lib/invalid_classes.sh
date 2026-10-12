# shellcheck shell=bash
# 2.1.0 Task 0d (§E, D30, D32-2, M06, M48, M52): MỘT bộ phân loại cho mọi cổng thiết bị. Lớp INVALID là lỗi môi trường, không phải FAIL của
# sản phẩm, không tính lượt, chạy lại <= 1 (giữ cả hai log). FATAL/ANR/native của app trong cùng cửa sổ thắng mọi lớp.
#   [INVALID_PIDS="<pid>..."] invalid_class <pkg> <logfile>...   in một dòng: `FAIL` (có crash/ANR/native của <pkg>), `(package update)`, `(gms persistent)`,
#                                      `(billing unavailable)` hoặc rỗng (không có lớp). Luôn return 0. Lớp `(api)`/`(cold guest)`/`(ui)`/
#                                      `(precheck)` (2.2.0 T0c: precheck_env, Wi-Fi/keyguard/pin/host)/`(contention)` do chính cổng phát (getprop, form UMP, ui_find, emu_boot rc 3), không đọc từ log.
invalid_class() {
  local pkg=$1 txt kill pids; shift
  txt=$(cat "$@" 2> /dev/null)
  pids=$(echo ${INVALID_PIDS:-} | tr ' ' '|')   # INVALID_PIDS (tuỳ chọn): pid của app cổng đã ghi; không đặt = mọi pid của <pkg>
  kill="Killing (${pids:-[0-9]+}):$pkg[/ ]"
  # here-string + grep không -q: không có `printf | grep -q` (grep thoát sớm làm printf in "Broken pipe" khi SIGPIPE bị bỏ qua, 2.2.0 T0d)
  if { grep --text -A5 'FATAL EXCEPTION' <<< "$txt" | grep --text -F "Process: $pkg" > /dev/null; } \
    || grep --text -F ">>> $pkg <<<" <<< "$txt" > /dev/null \
    || { grep --text am_anr <<< "$txt" | grep --text -F "$pkg" > /dev/null; }; then echo FAIL; return 0; fi
  if grep --text -E "$kill.*killDueToPackageUpdate" <<< "$txt" > /dev/null; then echo '(package update)'
  elif grep --text -E "$kill.*dying proc com\.google\.android\.gms\.persistent" <<< "$txt" > /dev/null; then echo '(gms persistent)'
  elif grep --text -F 'IapHelper unavailable: 3' <<< "$txt" > /dev/null; then echo '(billing unavailable)'
  fi
  return 0
}
