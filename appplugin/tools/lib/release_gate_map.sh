# shellcheck shell=bash
# Phần soi mapping của tools/release_gate.sh chạy không cần build, tách ra để chạy được trên một mapping có
# sẵn (vd mapping trước fix). Người source phải đặt LOG và fail=0.

# 1.6.1 G2: mapping release không được có chữ ClassValue. kotlinx.serialization (chartboost-sdk, vungle-ads)
# có lớp con của java.lang.ClassValue (API 34); R8 từng gộp chúng vào lớp dùng lúc khởi động (109926d) rồi
# outline ClassValue.get vào lớp tổng hợp dùng chung của Unity Ads (G2), cả hai làm ART API < 34 in
# ClassNotFoundException/NoClassDefFoundError java.lang.ClassValue. Rule -assumevalues cho
# CachingKt.useClassValue trong consumer-rules.pro xoá hẳn nhánh đó; kotlinx đổi tên trường thì rule bị bỏ
# qua câm, và chỉ mapping mới nói thật. Đếm mọi dòng (tên lớp, trường, outline), không chỉ một dạng.
check_mapping_classvalue() {
  local map=$1 n rc
  n=$(grep -c 'ClassValue' "$map"); rc=$?
  if [ "$rc" -gt 1 ]; then
    echo "FAIL: không đọc được $map (grep exit=$rc)" | tee -a "$LOG"; fail=1; return
  fi
  echo "ClassValue lines in mapping=$n (need 0; consumer-rules.pro -assumevalues CachingKt.useClassValue)" | tee -a "$LOG"
  [ "$n" -eq 0 ] && return
  grep -n 'ClassValue' "$map" | head -20 | sed 's/^/  classvalue> /' | tee -a "$LOG"
  fail=1
}

# 1.8.0 T3 (D16, D23): cổng lớp vắng dạng cố định. Kiểu được THAM CHIẾU trong classes*.dex (mọi `L…;` ở dòng không phải
# const-string của `dexdump -d -n`: field/method/type/superclass/interface/catch) trừ kiểu được ĐỊNH NGHĨA trong dex, trừ
# tiền tố android. java. javax. kotlin. dalvik., phải ⊆ allowlist (mỗi dòng một kiểu đầy đủ dạng chấm + `# chủ: …`).
# `usage.txt`/`missing_rules.txt` của R8 không nói được điều này (usage.txt = mã đã bị bỏ; missing_rules.txt chỉ có khi build lỗi).
# Giới hạn có chủ ý: dexdump chạy không `-a`, kiểu chỉ xuất hiện trong annotation không được quét.
# check_absent_classes <apk> <allowlist> [variant]: variant mặc định utility = cổng FAIL, in `absent-class sdk=<n> allow=<k>`
# (n = kiểu vắng NGOÀI allowlist, cần 0). Variant khác (game, D23) chỉ THÔNG TIN: in `absent-class <variant> info=<n> allow=<k>`,
# không bao giờ đổi fail (allowlist đo trên utility). Người source đặt LOG và fail=0. Công cụ = build-tools mới nhất
# (KHÔNG có biến ép). Đầu vào hỏng (thiếu dexdump/APK/allowlist, allowlist rỗng) = FAIL ở utility, không bao giờ "0".
_ac_fail() { # $1 = variant, $2 = lời
  if [ "$1" = utility ]; then echo "FAIL: absent-class: $2" | tee -a "$LOG"; fail=1
  else echo "absent-class $1 info=unavailable ($2)" | tee -a "$LOG"; fi
}
check_absent_classes() {
  local apk=$1 allow=$2 var=${3:-utility} dd tmp d n=0 k=0 c bt
  bt=$(ls -d "${ANDROID_HOME:-$HOME/Library/Android/sdk}"/build-tools/* 2>/dev/null | sort -V | tail -1)
  dd=$bt/dexdump
  [ -x "$dd" ] || { _ac_fail "$var" "không có dexdump ($dd)"; return; }
  [ -f "$apk" ] || { _ac_fail "$var" "không có APK $apk"; return; }
  [ -f "$allow" ] || { _ac_fail "$var" "không có allowlist $allow"; return; }
  tmp=$(mktemp -d "${TMPDIR:-/tmp}/absent-class.XXXXXX") || { _ac_fail "$var" "mktemp"; return; }
  sed -e 's/#.*//' -e 's/[[:space:]]//g' "$allow" | LC_ALL=C command grep -v '^$' | LC_ALL=C sort -u > "$tmp/allow"
  if [ ! -s "$tmp/allow" ]; then _ac_fail "$var" "allowlist rỗng ($allow)"; rm -rf "$tmp"; return; fi
  unzip -o -q "$apk" 'classes*.dex' -d "$tmp" 2>/dev/null
  c=0
  for d in "$tmp"/classes*.dex; do
    [ -f "$d" ] || continue
    c=$((c + 1))
    "$dd" -d -n "$d" >> "$tmp/dump.txt" 2>/dev/null || { _ac_fail "$var" "dexdump lỗi trên $(basename "$d")"; rm -rf "$tmp"; return; }
  done
  if [ "$c" -eq 0 ] || [ ! -s "$tmp/dump.txt" ]; then
    _ac_fail "$var" "không đọc được classes*.dex trong $apk"; rm -rf "$tmp"; return
  fi
  LC_ALL=C command grep 'Class descriptor  :' "$tmp/dump.txt" | LC_ALL=C command grep -oE "'L[^;]+;'" | tr -d "'" | LC_ALL=C sort -u > "$tmp/def"
  LC_ALL=C command grep -v 'const-string' "$tmp/dump.txt" | LC_ALL=C command grep -oE 'L[A-Za-z0-9_$/.-]+;' | LC_ALL=C sort -u > "$tmp/ref"
  if [ ! -s "$tmp/def" ] || [ ! -s "$tmp/ref" ]; then
    _ac_fail "$var" "không rút được kiểu định nghĩa/tham chiếu"; rm -rf "$tmp"; return
  fi
  LC_ALL=C comm -13 "$tmp/def" "$tmp/ref" | sed -e 's/^L//' -e 's/;$//' -e 's#/#.#g' \
    | LC_ALL=C command grep -vE '^(android|java|javax|kotlin|dalvik)\.' | LC_ALL=C sort -u > "$tmp/absent"
  LC_ALL=C comm -23 "$tmp/absent" "$tmp/allow" > "$tmp/outside"
  n=$(wc -l < "$tmp/outside" | tr -d ' ')
  k=$(( $(wc -l < "$tmp/absent" | tr -d ' ') - n ))
  if [ "$var" != utility ]; then
    echo "absent-class $var info=$n allow=$k (chỉ thông tin, D23: chỉ utility bị chặn)" | tee -a "$LOG"
    rm -rf "$tmp"; return
  fi
  echo "absent-class sdk=$n allow=$k (need sdk=0; allowlist=$(basename "$allow"))" | tee -a "$LOG"
  if [ "$n" -gt 0 ]; then
    head -20 "$tmp/outside" | sed 's/^/  absent> /' | tee -a "$LOG"
    fail=1
  fi
  rm -rf "$tmp"
}
