#!/usr/bin/env bash
# 2.4.0 T0b (hợp đồng A, P22): "façade riêng" của artifact appplugin-nextgen — so api/appplugin-nextgen.api với api/appplugin.api.
# Tách khối như api_facade_check.sh:44-55 (chỉ khối `public`; bỏ dòng `access$…` và thành viên internal đã mangle, kể cả tên mangle
# của nextgen `$appplugin_nextgen(` — D7-240, regex `\$appplugin(_nextgen)?(\$default)?\(`). Luật:
#  P1: khối public của legacy mà lớp KHÔNG internal/private trong nguồn gốc → phải có y hệt từng dòng trong nextgen.
#  P2: khối khác/mới/mất chỉ được khi lớp `internal`/`private` trong nguồn (gốc HOẶC override nextgen; regex như api_facade_check.sh:71,
#      file *.kt ngay trong thư mục gói); khối mới của nextgen cũng chỉ được khi lớp internal/private.
#  P3: thiếu file dump hoặc thiếu thư mục nguồn gốc = FAIL (fail-closed). Thư mục override nextgen vắng (NG0: 0 override) được phép:
#      vắng chỉ làm luật P2 chặt hơn, không bao giờ nới.
# In `parity: PASS|FAIL (…số khối…)`. Hook test: PC_LEGACY, PC_NG, PC_SRC, PC_NGSRC.
set -euo pipefail
cd "$(dirname "$0")/.."
LEG=${PC_LEGACY:-api/appplugin.api}; NG=${PC_NG:-api/appplugin-nextgen.api}
SRC=${PC_SRC:-src/main/java}; NGSRC=${PC_NGSRC:-appplugin-nextgen/src/main/java}
die() { echo "FAIL: $*"; echo "parity: FAIL"; exit 1; }
[ -f "$LEG" ] || die "thiếu dump legacy $LEG"
[ -f "$NG" ] || die "thiếu dump nextgen $NG (chạy tools/api_dump.sh --module appplugin-nextgen)"
[ -d "$SRC" ] || die "thiếu nguồn gốc $SRC"
tmp=$(mktemp -d "${TMPDIR:-/tmp}/api-parity.XXXXXX"); trap 'rm -rf "$tmp"' EXIT
split_blocks() { # $1 = dump, $2 = thư mục ra: một file mỗi khối lớp public (tên = token sau class/interface/enum)
  mkdir -p "$2"
  awk -v dir="$2" '
    /^[^ {}].*(class|interface|enum) [A-Za-z0-9_.$]+/ {
      out = ""
      if ($1 == "public") { for (i = 1; i < NF; i++) if ($i == "class" || $i == "interface" || $i == "enum") { name = $(i + 1); sub(/<.*/, "", name); out = dir "/" name ".txt"; break } }
    }
    { if (out != "" && $0 !~ /access\$/ && $0 !~ /\$appplugin(_nextgen)?(\$default)?\(/) print > out }
    /^}/ { if (out != "") close(out); out = "" }
  ' "$1"
}
is_internal() { # $1 = FQN của khối: lớp ngoài cùng khai internal/private trong nguồn gốc hoặc override nextgen
  local simple="${1##*.}" d; simple="${simple%%\$*}"
  for d in "$SRC" "$NGSRC"; do
    command grep -qE "^(internal|private) (data |enum |sealed |abstract |open |value |fun )?(class|object|interface) ${simple}\b" "$d/$(printf '%s' "${1%.*}" | tr . /)"/*.kt 2>/dev/null && return 0
  done
  return 1
}
split_blocks "$LEG" "$tmp/leg"; split_blocks "$NG" "$tmp/ng"
nl=$(ls "$tmp/leg" | wc -l | tr -d ' '); nn=$(ls "$tmp/ng" | wc -l | tr -d ' ')
[ "$nl" -gt 0 ] && [ "$nn" -gt 0 ] || die "dump không có khối public nào (legacy $nl, nextgen $nn)"
fail=0; same=0; allow=0
for f in "$tmp"/leg/*.txt; do
  name=$(basename "$f" .txt); rm -f "$tmp/d.txt"
  if [ -f "$tmp/ng/$name.txt" ] && diff -u "$f" "$tmp/ng/$name.txt" > "$tmp/d.txt"; then same=$((same + 1)); continue; fi
  if is_internal "$name"; then echo "diff: $name (cho phép, internal)"; allow=$((allow + 1))
  else echo "FAIL: khối public khác/mất ở nextgen: $name"; cat "$tmp/d.txt" 2>/dev/null || true; fail=1; fi
done
for f in "$tmp"/ng/*.txt; do
  name=$(basename "$f" .txt)
  [ -f "$tmp/leg/$name.txt" ] && continue
  if is_internal "$name"; then echo "new: $name (cho phép, internal)"; allow=$((allow + 1))
  else echo "FAIL: khối public mới ở nextgen không internal/private trong nguồn: $name"; fail=1; fi
done
echo "parity: $([ $fail -eq 0 ] && echo PASS || echo FAIL) (legacy $nl khối, nextgen $nn khối, $same y hệt, $allow khác biệt internal)"
exit $fail
