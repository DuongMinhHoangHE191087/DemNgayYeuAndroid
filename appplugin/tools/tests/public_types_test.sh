#!/usr/bin/env bash
# Test không cần máy cho tools/public_types.sh (cổng H9 + baseline kiểu public) trên cây nguồn giả: P1..P9, PT8..PT10 (2.1.0 T0g: ba module, baseline `<module>:<FQN>`).
# Chạy: bash tools/tests/public_types_test.sh   (exit 0 = xanh). Bash 3.2. Script thiếu = FAIL (fail-closed).
set -u
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
PT=$ROOT/tools/public_types.sh
TMP=$(mktemp -d "${TMPDIR:-/tmp}/public-types-test.XXXXXX") || { echo "FAIL: mktemp"; exit 1; }
trap 'rm -rf "$TMP"' EXIT
total=0; bad=0
check() { total=$((total + 1)); if [ "$2" -eq 0 ]; then echo "ok     - $1"; else bad=$((bad + 1)); echo "FAIL   - $1"; printf '%s\n' "${3:-}" | head -12 | sed 's/^/         /'; fi; }
has() { printf '%s\n' "$1" | command grep -qF -- "$2"; }

# Cây giả: 19 kiểu top-level, 12 internal/private + 7 public = 63.2 %; kiểu lồng, chú thích, typealias không đếm.
SRC=$TMP/src/main/java; mkdir -p "$SRC/a" "$SRC/b"
cat > "$SRC/a/A.kt" <<'KT'
package com.fake.a

// class Commented
class Pub1
data class Pub2(val x: Int)
sealed interface Pub3
enum class Pub4 { X }
object Pub5 {
    class Nested
    internal class NestedInternal
}
typealias Alias = Pub1
internal class Int1
internal object Int2
private class Priv1
internal data class Int3(val a: Int)
internal fun interface Int4
KT
cat > "$SRC/b/B.kt" <<'KT'
package com.fake.b

abstract class Pub6
fun interface Pub7
internal enum class Int5 { A }
private interface Priv2
internal annotation class Int6
internal value class Int7(val v: Int)
private data class Priv3(val a: Int)
internal sealed class Int8
internal open class Int9
KT
run() { PT_SRC=$1 PT_BASE=$2 bash "$PT" "${@:3}" 2>&1; }

B1=$TMP/base1.txt
o_w=$(run "$SRC" "$B1"); r_w=$?
o1=$(run "$SRC" "$B1" --check); r1=$?
n_b=$(wc -l < "$B1" 2>/dev/null | tr -d ' ')
check "P1 baseline khop: --check rc 0, 'pct=63.2' (total=19 internal=12), baseline 7 dong da sap xep, co com.fake.b.Pub7" \
  "$([ "$r_w" -eq 0 ] && [ "$r1" -eq 0 ] && has "$o1" 'pct=63.2' && has "$o1" 'total=19 internal=12' && [ "$n_b" = 7 ] && [ "$(LC_ALL=C sort "$B1")" = "$(cat "$B1")" ] && command grep -qx 'root:com.fake.b.Pub7' "$B1"; echo $?)" "write rc=$r_w: $o_w
check rc=$r1: $o1 (baseline lines=$n_b)"

S2=$TMP/s2; cp -R "$TMP/src" "$S2"; printf 'class Extra\n' >> "$S2/main/java/a/A.kt"
o2=$(run "$S2/main/java" "$B1" --check); r2=$?
check "P2 them 1 lop public ngoai baseline -> --check rc 1, nhac com.fake.a.Extra" \
  "$([ "$r2" -eq 1 ] && has "$o2" 'com.fake.a.Extra'; echo $?)" "rc=$r2: $o2"

S3=$TMP/s3; cp -R "$TMP/src" "$S3"; sed -i.bak '/^fun interface Pub7/d' "$S3/main/java/b/B.kt"
o3=$(run "$S3/main/java" "$B1" --check); r3=$?
check "P3 bot 1 lop public (nguon khong con khai) -> --check rc 1, nhac com.fake.b.Pub7" \
  "$([ "$r3" -eq 1 ] && has "$o3" 'com.fake.b.Pub7'; echo $?)" "rc=$r3: $o3"

o4=$(run "$SRC" "$B1" --min-pct 70); r4=$?
o4b=$(run "$SRC" "$B1" --min-pct 60); r4b=$?
check "P4 --min-pct 70 o 63.2 -> rc 1; --min-pct 60 -> rc 0" \
  "$([ "$r4" -eq 1 ] && [ "$r4b" -eq 0 ] && has "$o4" 'pct=63.2'; echo $?)" "70: rc=$r4 $o4
60: rc=$r4b $o4b"

B5=$TMP/base5.txt; { cat "$B1"; echo root:com.fake.a.Ghost; } | LC_ALL=C sort > "$B5"
o5=$(run "$SRC" "$B5" --check); r5=$?
check "P5 baseline khai com.fake.a.Ghost ma nguon vang -> --check rc 1, nhac Ghost" \
  "$([ "$r5" -eq 1 ] && has "$o5" 'com.fake.a.Ghost'; echo $?)" "rc=$r5: $o5"

mkdir -p "$TMP/empty/src"
o6=$(run "$TMP/empty/src" "$B1" --check); r6=$?
o6b=$(run "$TMP/khong-co-thu-muc" "$B1" --check); r6b=$?
check "P6 0 file .kt (va thu muc vang) -> rc 1 (fail-closed), khong PASS rong" \
  "$([ "$r6" -eq 1 ] && [ "$r6b" -eq 1 ]; echo $?)" "empty rc=$r6: $o6
missing rc=$r6b: $o6b"

o7=$(run "$SRC" "$B1" --check --min-pct ""); r7=$?
o7b=$(run "$SRC" "$B1" --check --min-pct abc); r7b=$?
check "P7 --min-pct rong hoac khong phai so -> rc 1 kem thong bao (cu: rong tat cong, rc 0)" \
  "$([ "$r7" -eq 1 ] && [ "$r7b" -eq 1 ] && has "$o7" 'min-pct'; echo $?)" "empty rc=$r7: $o7
abc rc=$r7b: $o7b"

B8=$TMP/base8.txt; { cat "$B1"; echo root:com.fake.a.Ghost; } | LC_ALL=C sort > "$B8"; cp "$B8" "$TMP/base8.orig"
run "$SRC" "$B8" --min-pct 70 > /dev/null; run "$SRC" "$B8" --check > /dev/null; run "$SRC" "$B8" --check --min-pct 60 > /dev/null
check "P8 --check va --min-pct chi doc: baseline (co dong Ghost) byte-identical sau ca ba lenh; chi che do mac dinh moi ghi" \
  "$(cmp -s "$B8" "$TMP/base8.orig"; echo $?)" "baseline changed"

# ── 2.1.0 T0g (hợp đồng A): quét thêm appplugin-game / appplugin-max, baseline một file, pct cộng mọi module ──
GSRC=$TMP/game/src/main/java; mkdir -p "$GSRC/g"
cat > "$GSRC/g/G.kt" <<'KT'
package com.fake.g

class GamePub
internal class GameInt1
internal object GameInt2
KT
o9=$(PT_GAME=$GSRC run "$SRC" "$B1" --check); r9=$?
check "PT8 lop public moi o appplugin-game (nam ngoai baseline) -> --check rc 1, nhac appplugin-game:com.fake.g.GamePub (cu: khong quet module con)" \
  "$([ "$r9" -eq 1 ] && has "$o9" 'appplugin-game:com.fake.g.GamePub'; echo $?)" "rc=$r9: $o9"

B9=$TMP/base9.txt
o9w=$(PT_GAME=$GSRC run "$SRC" "$B9"); r9w=$?
o9c=$(PT_GAME=$GSRC run "$SRC" "$B9" --check); r9c=$?
check "PT9 pct cong hai cay: total=22 internal=14 pct=63.6; baseline 8 dong, co appplugin-game:com.fake.g.GamePub; --check rc 0" \
  "$([ "$r9w" -eq 0 ] && [ "$r9c" -eq 0 ] && has "$o9c" 'total=22 internal=14 pct=63.6' && [ "$(wc -l < "$B9" | tr -d ' ')" = 8 ] && command grep -qx 'appplugin-game:com.fake.g.GamePub' "$B9"; echo $?)" "write: $o9w
check: $o9c"

o10=$(PT_GAME=$TMP/khong-co-game PT_MAX=$TMP/khong-co-max run "$SRC" "$B1" --check); r10=$?
check "PT10 thu muc module vang = bo qua: --check rc 0, total=19 (khong rc 1, khong dem them)" \
  "$([ "$r10" -eq 0 ] && has "$o10" 'total=19 internal=12'; echo $?)" "rc=$r10: $o10"

# ── 2.1.0 T0e (M01): biên ngưỡng. 7/10 = đúng 70,0 % -> --min-pct 70 rc 0 (so `>=`); 6/10 -> rc 1. Đặc tả (xanh trên mã cũ), đột biến `>=`->`>` đỏ. ──
BSRC=$TMP/bound/src/main/java; mkdir -p "$BSRC/z"
{ echo 'package com.fake.z'; for i in 1 2 3; do echo "class BPub$i"; done; for i in 1 2 3 4 5 6 7; do echo "internal class BInt$i"; done; } > "$BSRC/z/Z.kt"
o11=$(run "$BSRC" "$TMP/khong-ghi.txt" --min-pct 70); r11=$?
{ echo 'package com.fake.z'; for i in 1 2 3 4; do echo "class BPub$i"; done; for i in 1 2 3 4 5 6; do echo "internal class BInt$i"; done; } > "$BSRC/z/Z.kt"
o11b=$(run "$BSRC" "$TMP/khong-ghi.txt" --min-pct 70); r11b=$?
check "P9 (M01) bien nguong: 7/10 internal (pct=70.0) + --min-pct 70 -> rc 0; 6/10 (60.0) -> rc 1 nhac pct (dot bien >= thanh > do)" \
  "$([ "$r11" -eq 0 ] && has "$o11" 'pct=70.0' && [ "$r11b" -eq 1 ] && has "$o11b" 'pct=60.0 < --min-pct 70'; echo $?)" "7/10 rc=$r11: $o11
6/10 rc=$r11b: $o11b"

echo
if [ "$bad" -eq 0 ]; then echo "PASS $total/$total"; exit 0; fi
echo "FAIL $bad/$total"; exit 1
