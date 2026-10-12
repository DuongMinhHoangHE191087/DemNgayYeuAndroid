#!/usr/bin/env bash
# Cổng nguồn cho gói controller com.app.plugin.ads.core (plan 1.5.0, review Task 0 I1). javap thấy `internal class`
# là `public final class` (tools/api_dump.sh đầu file), nên api_facade_check.sh không phân biệt được controller
# `internal` với một lớp/hàm top-level quên `internal` — cái sau là API mới app gọi được, vòng qua façade. Ở đây đọc
# nguồn: mọi khai báo top-level (cột 0) `class|interface|object|fun|val|var|typealias` trong core/*.kt, kể cả có
# annotation đứng trước trên cùng dòng và modifier `enum|data|sealed|abstract|open|annotation|value|inline|const|…`,
# phải có `internal` hoặc `private`; thiếu là FAIL. File .java trong core là FAIL (Java không có `internal`).
# Thư mục core chưa có (trước Task 1) → PASS. Được gọi từ tools/api_facade_check.sh; chạy riêng cũng được.
# Luật R-2 (plan 1.5.0, review Task 2 m1, siết ở review Task 3 m1): controller không đọc façade `h` lúc dựng. Mỗi dòng
# được bỏ chuỗi ("…", '…') và chú thích cuối dòng (`// …`) trước khi xét. FAIL khi trong core/*.kt có
#   - field cấp thành viên (thụt 4 dấu cách: `val`/`var` của lớp controller) mà phần TRƯỚC dấu `{` đầu tiên của dòng
#     khai báo, hoặc của dòng ngay sau khi khai báo kết thúc bằng `=`, chứa `h.` — trừ getter `get() =` (tính lại mỗi
#     lần đọc). Phần sau `{` là thân lambda/`Runnable { … }`/`object : X { … }`, chạy sau, không phải lúc dựng;
#   - `init {` cấp thành viên (`^    init {`: khối khởi tạo; `sdk.init { }` trong thân hàm không tính) hoặc `by lazy`
#     (bản sao lười, đọc `h` một lần rồi giữ).
# Bản sao eager hỏng theo thứ tự initializer của AdsHelper (controller dựng trước field khai sau nó → null).
set -euo pipefail
cd "$(dirname "$0")/.."
CORE_DIR="${CORE_DIR:-src/main/java/com/app/plugin/ads/core}"
if [ ! -d "$CORE_DIR" ]; then echo "core internal check: $CORE_DIR chưa có — PASS"; exit 0; fi
fail=0
java_files=$(find "$CORE_DIR" -name '*.java' | sort)
if [ -n "$java_files" ]; then
  echo "FAIL: file Java trong gói controller (không khai báo được internal):"; echo "$java_files"; fail=1
fi
kt_files=$(find "$CORE_DIR" -name '*.kt' | sort)
if [ -n "$kt_files" ]; then
  # shellcheck disable=SC2086 # tên file trong repo không có khoảng trắng
  bad=$(awk '
    /^[ \t]/ || /^$/ { next }
    {
      line = $0
      # bỏ annotation đầu dòng: @Volatile, @Suppress("A", "B"), @file:JvmName("X")…
      # (ngoặc lồng `@A(B(c))`: phần `)` thừa bị bỏ ở vòng sau)
      while (match(line, /^(@[A-Za-z0-9_.:]+(\([^)]*\))?|\))[ \t]*/)) line = substr(line, RLENGTH + 1)
      if (line == "") next # dòng chỉ có annotation: dòng khai báo ngay sau được xét riêng
      n = split(line, w, /[ \t(<:={]+/)
      decl = 0; vis = ""
      for (i = 1; i <= n; i++) {
        if (w[i] ~ /^(internal|private|public|protected)$/) vis = w[i]
        else if (w[i] ~ /^(class|interface|object|fun|val|var|typealias)$/) { decl = 1; break }
        else if (w[i] ~ /^(enum|data|sealed|abstract|open|final|annotation|value|inline|const|suspend|lateinit|expect|actual|external|infix|operator|tailrec|inner)$/) continue
        else break
      }
      if (decl && vis != "internal" && vis != "private") print FILENAME ":" FNR ": " $0
    }
  ' $kt_files)
  if [ -n "$bad" ]; then
    echo "FAIL: khai báo top-level không internal/private trong gói controller (thành API public của app):"; echo "$bad"; fail=1
  fi
fi
if [ -n "$kt_files" ]; then
  # shellcheck disable=SC2086 # tên file trong repo không có khoảng trắng
  eager=$(awk '
    # bỏ chuỗi và ký tự ("h." trong chuỗi không phải đọc h), rồi chú thích cuối dòng
    function code(s) {
      gsub(/"([^"\\]|\\.)*"/, "\"\"", s)
      gsub(/\047([^\047\\]|\\.)\047/, "\047\047", s)
      sub(/\/\/.*$/, "", s)
      return s
    }
    # phần trước dấu { đầu tiên: phần chạy lúc dựng field; sau { là thân lambda, chạy sau
    function eagerPart(s,   i) { i = index(s, "{"); return i ? substr(s, 1, i - 1) : s }
    function readsH(s) { return eagerPart(s) ~ /(^|[^A-Za-z0-9_.])h\./ }
    FNR == 1 { pending = 0 }
    /^[ \t]*(\*|\/\*|\/\/)/ { next } # dòng chú thích/KDoc
    {
      c = code($0)
      if (pending) { if (readsH(c)) print FILENAME ":" FNR ": (tiếp dòng " pending ") " $0; pending = 0 }
      if (c ~ /^    init[ \t]*\{/) { print FILENAME ":" FNR ": init { } " $0; next }
      if (c ~ /(^|[^A-Za-z0-9_])by[ \t]+lazy/) { print FILENAME ":" FNR ": by lazy " $0; next }
      if ($0 ~ /^    ([A-Za-z@][A-Za-z0-9_()".@ ]* )?(val|var) / && c !~ /get\(\)[ \t]*=/) {
        if (readsH(c)) print FILENAME ":" FNR ": " $0
        else if (index(c, "{") == 0 && c ~ /=[ \t]*$/) pending = FNR
      }
    }
  ' $kt_files)
  if [ -n "$eager" ]; then
    echo "FAIL: controller đọc façade h lúc dựng (field-level h., init { }, by lazy — plan 1.5.0 R-2):"; echo "$eager"; fail=1
  fi
fi
echo "core internal check ($CORE_DIR): $([ $fail -eq 0 ] && echo PASS || echo FAIL)"
exit $fail
