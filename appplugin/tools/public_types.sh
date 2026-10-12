#!/usr/bin/env bash
# 2.0.0 Task 0g (hợp đồng A, D3): cổng H9 + baseline kiểu public. Đếm kiểu TOP-LEVEL (class/object/interface ở cột 0, kể cả
# enum/data/sealed/fun/annotation/value) của `src/main/java` (root), `appplugin-game/src/main/java`, `appplugin-max/src/main/java`
# (2.1.0 T0g: thư mục vắng = bỏ qua; pct cộng mọi module; baseline dòng `<module>:<gói>.<Tên>`, module = root|appplugin-game|appplugin-max);
# internal = modifier có `internal` hoặc `private`.
# In: "top-level types total=<T> internal=<I> pct=<P>"  (P = I/T, 1 chữ số thập phân; mốc 2.0.0: 266/168 = 63.2).
# Dùng: tools/public_types.sh [--check] [--min-pct N]
#   (mặc định)   in số liệu và GHI tools/public-types.txt (CHỈ chế độ này ghi; --check/--min-pct chỉ đọc) (<gói>.<Tên> của kiểu public, LC_ALL=C sort).
#   --check      không ghi; rc 1 nếu tập public lệch baseline (kiểu public MỚI chưa vào baseline, hoặc mục baseline mà nguồn
#                không còn khai) và in phần lệch; baseline vắng = rc 1.
#   --min-pct N  rc 1 khi I/T < N % (so số nguyên, không làm tròn; N rỗng/không phải số = rc 1; C3 dùng N=70).
# 0 file .kt tổng = rc 1 (fail-closed). Test đổi nguồn/baseline bằng PT_SRC (root), PT_GAME, PT_MAX (khi có PT_SRC: mặc định bỏ qua), PT_BASE.
set -u
ROOT=$(cd "$(dirname "$0")/.." && pwd)
BASE=${PT_BASE:-$ROOT/tools/public-types.txt}
if [ -n "${PT_SRC:-}" ]; then SRC=$PT_SRC; GAME=${PT_GAME:-}; MAX=${PT_MAX:-}
else SRC=$ROOT/src/main/java; GAME=$ROOT/appplugin-game/src/main/java; MAX=$ROOT/appplugin-max/src/main/java; fi
CHECK=0; MIN=""; HAVE_MIN=0
die() { echo "FAIL: $1"; exit 1; }
while [ $# -gt 0 ]; do
  case $1 in
    --check) CHECK=1; shift;;
    --min-pct) [ $# -ge 2 ] || die "--min-pct cần một số"; MIN=$2; HAVE_MIN=1; shift 2;;
    *) die "dùng: public_types.sh [--check] [--min-pct N] (nhận '$1')";;
  esac
done
[ "$HAVE_MIN" -eq 0 ] || printf '%s\n' "$MIN" | command grep -Eq '^[0-9]+(\.[0-9]+)?$' || die "--min-pct phải là số (nhận '$MIN')"
TMPD=$(mktemp -d "${TMPDIR:-/tmp}/public-types.XXXXXX") || die "mktemp"
trap 'rm -rf "$TMPD"' EXIT
for m in "root:$SRC" "appplugin-game:$GAME" "appplugin-max:$MAX"; do
  [ -n "${m#*:}" ] && [ -d "${m#*:}" ] || continue
  find "${m#*:}" -name '*.kt' -print0 | xargs -0 -r awk -v mod="${m%%:*}" '
  FNR == 1 { pkg = "" }
  /^package[ \t]/ { pkg = $2; sub(/;.*/, "", pkg); next }
  /^((public|internal|private|open|abstract|sealed|data|enum|inline|value|fun|annotation)[ \t]+)*(class|object|interface)[ \t]+[A-Za-z_]/ {
    internal = 0
    for (i = 1; i <= NF; i++) {
      if ($i == "internal" || $i == "private") internal = 1
      if ($i == "class" || $i == "object" || $i == "interface") { n = $(i + 1); sub(/[^A-Za-z0-9_].*/, "", n); break }
    }
    print (internal ? "I " : "P ") mod ":" pkg "." n
  }' >> "$TMPD/all.txt" 2>/dev/null
done
[ -f "$TMPD/all.txt" ] || : > "$TMPD/all.txt"
T=$(wc -l < "$TMPD/all.txt" | tr -d ' ')
[ "$T" -gt 0 ] || die "0 kiểu top-level (không có file .kt ở root/appplugin-game/appplugin-max?)"
I=$(command grep -c '^I ' "$TMPD/all.txt")
PCT=$(awk -v i="$I" -v t="$T" 'BEGIN { printf "%.1f", 100 * i / t }')
echo "top-level types total=$T internal=$I pct=$PCT"
sed -n 's/^P //p' "$TMPD/all.txt" | LC_ALL=C sort -u > "$TMPD/pub.txt"
rc=0
if [ "$CHECK" -eq 1 ]; then
  [ -f "$BASE" ] || die "không có baseline $BASE"
  if ! diff "$BASE" "$TMPD/pub.txt" > "$TMPD/diff.txt"; then
    sed -n 's/^> /FAIL: kiểu public MỚI ngoài baseline: /p; s/^< /FAIL: baseline khai nhưng nguồn không còn: /p' "$TMPD/diff.txt"; rc=1
  fi
elif [ "$HAVE_MIN" -eq 0 ]; then
  cp "$TMPD/pub.txt" "$BASE" && echo "wrote $BASE ($(wc -l < "$BASE" | tr -d ' ') public)"
fi
if [ "$HAVE_MIN" -eq 1 ] && ! awk -v i="$I" -v t="$T" -v m="$MIN" 'BEGIN { exit !(100 * i >= m * t) }'; then
  echo "FAIL: pct=$PCT < --min-pct $MIN"; rc=1
fi
exit "$rc"
