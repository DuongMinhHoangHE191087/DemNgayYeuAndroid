#!/usr/bin/env bash
# Cổng phụ G8 (plan 1.5.0 tách AdsHelper; plan 1.6.0 Task 0 dời baseline về 1.5.0 và siết luật khối mới): mặt ngoài
# public/protected của MỌI lớp có từ baseline không đổi. So api/appplugin.api hiện tại với bản ở BASELINE (mặc định
# 82c22cd = v2.3.0 (2.4.0 T0a); trước đó v2.1.0, G1 (2.1.0), commit C3b (2.0.0), rồi v1.5.0 = 44e72d8, tag publish 2026-09-29; dump y hệt a76f77e), sau khi lọc ở cả hai bên:
#   - khối lớp không public (header không bắt đầu bằng `public`, vd `final class …AdsHelper$PendingFull2`): bỏ;
#   - dòng `access$…`: accessor tổng hợp Kotlin sinh cho lớp ẩn danh — chính các lớp `$N` mà api_dump.sh đã loại;
#   - dòng thành viên `internal` (tên mangle `…$appplugin(`, kể cả cầu tham số mặc định `…$appplugin$default(`):
#     không phải API của app.
# Sau lọc: mỗi khối lớp của baseline phải có trong bản hiện tại, y hệt từng dòng; khối cũ mất hay đổi là FAIL. Khối MỚI
# ở bất kỳ gói nào chỉ được phép khi nguồn khai báo lớp đó `internal`/`private`: tên đơn (sau dấu chấm cuối; lớp lồng
# `Outer$Inner` xét theo `Outer`) phải khớp `^(internal|private) (data |enum |sealed |abstract |open |value |fun )?
# (class|object|interface) <Tên>\b` trong các file *.kt ngay trong thư mục của đúng gói lớp đó
# (src/main/java/<gói, . → />/*.kt, không đệ quy; trong repo gói = thư mục, 186/186 file tại 8f04bba) — lớp `internal`
# trùng tên đơn ở gói khác (vd `until.AdState` với `billing.AdState` mới) không được tính (review Task 0 I1). javap thấy
# `internal class` là public (tools/api_dump.sh đầu file), nên phải đọc nguồn. Không khớp (lớp public mới, hay lớp
# facade `…Kt` của hàm top-level — hàm top-level `internal` không bị mangle) là FAIL. Biến
# NEW_PKG (1.5.0: chỉ cho khối mới trong com.app.plugin.ads.core) không còn dùng. Không thay api_dump.sh --check: dump
# vẫn phải khớp từng dòng và đi cùng commit; đây là lớp đọc diff giúp reviewer. Cuối script gọi
# tools/core_internal_check.sh (đọc nguồn core/*.kt: khai báo top-level phải internal/private, luật R-2).
# 2.1.0 T0g: --module <root|appplugin-game> (mặc định root; baseline riêng: BASELINE_ROOT mặc định d03bdb3 (commit dump 2.5.0, 2.5.0 DOC250), BASELINE_GAME 82c22cd = v2.3.0 (2.4.0 T0a));
# module game không có dump + BASELINE_GAME rỗng = bỏ qua PASS; có dump mà BASELINE_GAME rỗng = FAIL rc 2;
# core_internal_check chỉ chạy ở root. BASELINE (env) vẫn thắng cả hai. BASELINE chỉ dời khi chủ dự án duyệt đổi API hoặc khi một tag mới được chọn làm mốc: đổi giá trị mặc định dưới đây
# và ghi CHANGELOG (v1.5.0 từ 1.6.3 Task 5; 2.0.0 C3b; 2.1.0 G1-2: G1, D27 theo sau D18; 2.3.0 T0a: v2.2.0; 2.4.0 T0a: 82c22cd = v2.3.0; 2.4.2 DOC242: BASELINE_ROOT = 8f9539c, API chỉ thêm, D5-SC; 2.5.0 DOC250: BASELINE_ROOT = d03bdb3, +1 dòng public AdsHelper.setAutoShowOpenAdOnForeground, D1-250).
set -euo pipefail
cd "$(dirname "$0")/.."
MODULE=root; [ "${1:-}" != --module ] || MODULE=${2:-}
case $MODULE in
  root) BASELINE="${BASELINE:-${BASELINE_ROOT:-d03bdb3}}"; API=api/appplugin.api; SRCM=src/main/java;;
  appplugin-game) BASELINE="${BASELINE:-${BASELINE_GAME:-82c22cd}}"; API=api/appplugin-game.api; SRCM=appplugin-game/src/main/java;;
  *) echo "dùng: api_facade_check.sh [--module root|appplugin-game] (nhận '$MODULE')"; exit 2;;
esac
SRC_DIR="${SRC_DIR:-$SRCM}" # nguồn Kotlin để xét khai báo internal/private của khối mới
CURR_API="${CURR_API:-$API}" # đổi được để thử đột biến trên bản chép của dump
if [ "$MODULE" != root ] && [ ! -f "$CURR_API" ]; then echo "facade check $MODULE: không có $CURR_API (module 0 lớp) — bỏ qua PASS"; exit 0; fi
if [ -z "$BASELINE" ]; then echo "FAIL: $MODULE có dump mà BASELINE_GAME rỗng (G1-2 đặt = sha G1)"; exit 2; fi
tmp=$(mktemp -d "${TMPDIR:-/tmp}/api-facade.XXXXXX"); trap 'rm -rf "$tmp"' EXIT
if ! git cat-file -e "$BASELINE:$API" 2>/dev/null; then
  echo "FAIL: không đọc được $BASELINE:$API (clone nông? cần fetch-depth 0; hoặc commit baseline không còn"
  echo "      trong lịch sử — đặt BASELINE=<commit/tag chứa dump 1.5.0>)"
  exit 2
fi
git show "$BASELINE:$API" > "$tmp/base.api"
cp "$CURR_API" "$tmp/curr.api"
split_blocks() { # $1 = dump, $2 = thư mục ra: một file mỗi khối lớp public (tên = token sau class/interface),
  # đã lọc access$ và $appplugin / $appplugin$default
  mkdir -p "$2"
  awk -v dir="$2" '
    /^[^ {}].*(class|interface|enum) [A-Za-z0-9_.$]+/ {
      out = ""
      if ($1 == "public") { for (i = 1; i < NF; i++) if ($i == "class" || $i == "interface" || $i == "enum") { name = $(i + 1); sub(/<.*/, "", name); out = dir "/" name ".txt"; break } }
    }
    { if (out != "" && $0 !~ /access\$/ && $0 !~ /\$appplugin(\$default)?\(/) print > out }
    /^}/ { if (out != "") close(out); out = "" }
  ' "$1"
}
split_blocks "$tmp/base.api" "$tmp/base"
split_blocks "$tmp/curr.api" "$tmp/curr"
fail=0
for f in "$tmp"/base/*.txt; do
  name=$(basename "$f" .txt)
  if [ ! -f "$tmp/curr/$name.txt" ]; then echo "FAIL: lớp public mất khỏi dump: $name"; fail=1; continue; fi
  if ! diff -u "$f" "$tmp/curr/$name.txt" > "$tmp/d.txt"; then echo "FAIL: mặt ngoài đổi: $name"; cat "$tmp/d.txt"; fail=1; fi
done
for f in "$tmp"/curr/*.txt; do
  name=$(basename "$f" .txt)
  [ -f "$tmp/base/$name.txt" ] && continue
  simple="${name##*.}"; simple="${simple%%\$*}" # tên đơn của lớp ngoài cùng: com.x.Outer$Inner → Outer
  pkg_dir="$SRC_DIR/$(printf '%s' "${name%.*}" | tr . /)" # chỉ xét file của đúng gói (gói = thư mục trong repo)
  # 2.1.0 SC1-2: BuildSha sinh lúc build (nguồn ở build/, mẫu trong gradle/build-sha.gradle) nên ngoại lệ cộng thêm: nhận BuildSha có mẫu `internal object BuildSha {` (A || { B && C }).
  # 2.2.0 T0d (FP1): ngoại lệ BuildSha chỉ cho gói com.app.plugin; (D15-220) tools/facade-allow.txt: kiểu public CÓ CHỦ ĐÍCH (cột 1 = FQN của lớp ngoài cùng).
  if grep -qE "^(internal|private) (data |enum |sealed |abstract |open |value |fun )?(class|object|interface) ${simple}\b" "$pkg_dir"/*.kt 2>/dev/null || { [ "$simple" = BuildSha ] && [ "${name%.*}" = com.app.plugin ] && grep -qF 'internal object BuildSha {' gradle/build-sha.gradle 2>/dev/null; }; then
    echo "new: $name (cho phép, internal)"
  elif [ -f tools/facade-allow.txt ] && awk -v want="${name%%\$*}" '{ sub(/#.*/, "") } $1 == want { f = 1 } END { exit !f }' tools/facade-allow.txt; then
    echo "new: $name (allow)"
  else
    echo "FAIL: lớp public mới không có khai báo internal/private trong nguồn: $name"; fail=1
  fi
done
if [ "$MODULE" = root ] && ! bash tools/core_internal_check.sh; then fail=1; fi
echo "facade check $MODULE vs $BASELINE: $([ $fail -eq 0 ] && echo PASS || echo FAIL)"
exit $fail
