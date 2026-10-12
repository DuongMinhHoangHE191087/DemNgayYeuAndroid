#!/usr/bin/env bash
# Dump API public và protected của AAR (javap -protected) vào api/appplugin.api; --check so với bản đã commit (cổng
# G8, DOC/65). Có `protected` vì app kế thừa được lớp mở của SDK (AdsBase, AdsPlBase…): đổi thành viên protected
# (vd abstract `eventTag`) làm hỏng lớp con ngoài SDK mà dump `-public` không thấy (review E M5).
# Ghi chú: dump là chữ ký JVM (javap), không có metadata Kotlin. Bắt được: thêm/bớt/đổi chữ ký lớp và thành viên
# public, thành viên đổi sang `internal` (tên bị mangle `…$appplugin`), hàm thành/thôi `suspend` (tham số
# `kotlin.coroutines.Continuation` thêm vào chữ ký), thêm/bỏ tham số mặc định (hàm tổng hợp `…$default`). KHÔNG bắt
# được: visibility Kotlin của lớp (public → `internal`: lớp vẫn public trong bytecode), nullability (`List<String>`
# → `List<String>?`), giá trị mặc định của tham số (đổi `= 0` thành `= 1`) — các đổi đó phải review bằng mắt (hoặc
# dùng binary-compatibility-validator đọc metadata, Phase C).
# Đổi API có chủ ý: chạy `tools/api_dump.sh` rồi commit api/appplugin.api cùng thay đổi (review đọc diff).
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
# Thứ tự lớp không được phụ thuộc locale (sort của glibc trên runner Linux bỏ qua `$` và `.` ở lượt so
# đầu, macOS thì không): cùng mã phải ra cùng dump ở CI và trên máy.
export LC_ALL=C
tmp=$(mktemp -d "${TMPDIR:-/tmp}/api-dump.XXXXXX")
trap 'rm -rf "$tmp"' EXIT
# 2.1.0 T0g: --module <root|appplugin-game|appplugin-max>; 2.4.0 T0b: thêm appplugin-nextgen (AAR/dump riêng, như root: 0 lớp là lỗi); 0 lớp: không tạo dump, --check rc 0 chỉ khi cũng vắng file. Hook test: AD_AAR, AD_OUT.
module=root; check=0
while [ $# -gt 0 ]; do
  case $1 in --check) check=1;; --module) module=${2:-}; shift;; *) echo "dùng: api_dump.sh [--check] [--module m]"; exit 2;; esac; shift
done
case $module in
  root) task=:assembleRelease; aar=build/outputs/aar/appplugin-release.aar; out=api/appplugin.api;;
  appplugin-game|appplugin-max|appplugin-nextgen) task=:$module:assembleRelease; aar=$module/build/outputs/aar/$module-release.aar; out=api/$module.api;;
  *) echo "::error::api_dump: module lạ '$module' (root|appplugin-game|appplugin-max|appplugin-nextgen)"; exit 2;;
esac
out=${AD_OUT:-$out}
if [ -n "${AD_AAR:-}" ]; then aar=$AD_AAR; else
  rm -f "$aar" # xoá AAR cũ trước khi build: build lỗi thì không bao giờ dump nhầm sản phẩm của lần trước
  ./gradlew $task -q > "$tmp/build.log" 2>&1 || { tail -60 "$tmp/build.log"; echo "::error::api_dump: $task lỗi"; exit 1; }
fi
unzip -q -o "$aar" classes.jar -d "$tmp"
# Bỏ lớp ẩn danh ($1, $2…) và lớp tổng hợp ($$…): tên của chúng đổi theo thứ tự lambda, không phải API.
classes=$(unzip -Z1 "$tmp/classes.jar" | grep '\.class$' | grep -vE '\$[0-9]+|\$\$' | sed 's/\.class$//; s#/#.#g' | sort || true)
if [ -z "$classes" ]; then
  { [ "$module" != root ] && [ "$module" != appplugin-nextgen ]; } || { echo "::error::api_dump: classes.jar không có lớp nào"; exit 1; }
  [ ! -f "$out" ] || { echo "::error::api_dump: $module 0 lớp mà còn $out"; exit 1; }
  echo "$module: 0 lớp, không có dump (ok)"; exit 0
fi
mkdir -p "$(dirname "$out")"
# shellcheck disable=SC2086 # tách từ có chủ ý: mỗi tên lớp là một đối số của javap
"$JAVA_HOME/bin/javap" -protected -cp "$tmp/classes.jar" $classes | grep -vE '^\s*$|^Compiled from' > "$tmp/new.api"
if [ "$check" -eq 1 ]; then
  [ -f "$out" ] || { echo "::error::không có $out; chạy tools/api_dump.sh rồi commit"; exit 1; }
  if diff -u "$out" "$tmp/new.api" > "$tmp/api-diff.txt"; then
    echo "API unchanged ($(wc -l < "$out" | tr -d ' ') lines)"
  else
    cat "$tmp/api-diff.txt"
    echo "::error::public API changed; run tools/api_dump.sh --module $module and commit $out"
    exit 1
  fi
else
  cp "$tmp/new.api" "$out"
  echo "wrote $out ($(wc -l < "$out" | tr -d ' ') lines)"
fi
