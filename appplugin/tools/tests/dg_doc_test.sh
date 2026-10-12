#!/usr/bin/env bash
# 2.3.0 ROB3: kiểm visibility INTEGRATION.md §9 (tools/lib/doc_visibility.py qua tools/integration_doc_check.sh) + hàng §13
# của bản [Unreleased] (UNRELEASED_AS). Nguồn tạm = bản sao src/main/java (IDC_SRC), sửa bằng python3 (không `sed -i`, #61).
# Chạy: bash tools/tests/dg_doc_test.sh (hoặc qua runner device_gate_allowlist_test.sh, phần `doc`). exit 0 = xanh.
. "$(dirname "$0")/lib/dg_harness.sh" || exit 99
IGDOC=$ROOT/docs/integration/INTEGRATION.md
MASK=com/app/plugin/ads/RemoveAdsMask.kt

# $1 = file, $2 = chuỗi cũ (phải có đúng 1 lần), $3 = chuỗi mới.
pyedit() {
  python3 - "$@" <<'PY'
import sys
p, a, b = sys.argv[1:4]; s = open(p, encoding="utf-8").read()
if s.count(a) != 1: sys.exit("pyedit: '%s' xuất hiện %d lần trong %s" % (a, s.count(a), p))
open(p, "w", encoding="utf-8").write(s.replace(a, b))
PY
}
# Bản sao nguồn mới ở $TMP/$1 (in đường dẫn).
src_copy() { rm -rf "${TMP:?}/$1"; mkdir -p "$TMP/$1"; cp -R "$ROOT/src/main/java/." "$TMP/$1/"; echo "$TMP/$1"; }
# $1 = IDC_SRC, $2 = tài liệu (mặc định tài liệu thật). In đầu ra + dòng rc=<n>.
vis() { local out rc; out=$(IDC_SRC=$1 bash "$ROOT/tools/integration_doc_check.sh" "${2:-$IGDOC}" 2>&1); rc=$?; printf '%s\nrc=%s\n' "$out" "$rc"; }

# DC11: nguồn cũ (RemoveAdsMask internal) + tài liệu cũ (bảng gói §9 còn AdsBrain) -> 2 FAIL lớp có file:dòng.
s11=$(src_copy dc11); pyedit "$s11/$MASK" 'public object RemoveAdsMask' 'internal object RemoveAdsMask'
cp "$IGDOC" "$TMP/dc11.md"; pyedit "$TMP/dc11.md" '| `LogEventServer` |' $'| `AdsBrain` | `com.app.plugin.brain` |\n| `LogEventServer` |'
dc11=$(vis "$s11" "$TMP/dc11.md")
check "DC11 nguồn/tài liệu cũ -> FAIL §9 RemoveAdsMask (RemoveAdsMask.kt:13) và AdsBrain (AdsBrain.kt:43), rc=1" \
  "$(has "$dc11" 'FAIL: §9: RemoveAdsMask không public trong' && has "$dc11" 'RemoveAdsMask.kt:13' && has "$dc11" 'FAIL: §9: AdsBrain không public trong' \
     && has "$dc11" 'brain/AdsBrain.kt:43' && has "$dc11" 'rc=1'; echo $?)" "$dc11"

# DC11b: enum entry là thành viên (UserSegment.HIP ở SegmentEnums.kt, không có fun/val) -> PASS, 0 dòng §9.
s11b=$(src_copy dc11b); dc11b=$(vis "$s11b")
check "DC11b tài liệu thật nhắc UserSegment.HIP (enum entry) -> PASS, 0 FAIL §9" \
  "$(grep -qF 'UserSegment.HIP' "$IGDOC" && has "$dc11b" 'integration_doc_check: PASS' && hasnt "$dc11b" '§9:' && has "$dc11b" 'rc=0'; echo $?)" "$dc11b"

# DC12: thành viên const đổi internal (dump không thấy: const không mangle) -> FAIL thành viên có file:dòng.
s12=$(src_copy dc12); pyedit "$s12/$MASK" 'public const val ALL: Int' 'internal const val ALL: Int'
dc12=$(vis "$s12")
check "DC12 RemoveAdsMask.ALL internal -> FAIL '§9: RemoveAdsMask.ALL không public trong …RemoveAdsMask.kt:25'" \
  "$(has "$dc12" 'FAIL: §9: RemoveAdsMask.ALL không public trong' && has "$dc12" 'RemoveAdsMask.kt:25' && has "$dc12" 'rc=1'; echo $?)" "$dc12"

# DC12b: lớp private (không chỉ internal) -> FAIL lớp.
s12b=$(src_copy dc12b); pyedit "$s12b/$MASK" 'public object RemoveAdsMask' 'private object RemoveAdsMask'
dc12b=$(vis "$s12b")
check "DC12b private object RemoveAdsMask -> FAIL '§9: RemoveAdsMask không public trong …RemoveAdsMask.kt:13'" \
  "$(has "$dc12b" 'FAIL: §9: RemoveAdsMask không public trong' && has "$dc12b" 'RemoveAdsMask.kt:13' && has "$dc12b" 'rc=1'; echo $?)" "$dc12b"

# DC13 (2.4.0 T0a tổng quát): phiên bản = UNRELEASED_AS của checker; [Unreleased] có "Phải làm khi nâng cấp" mà §13 thiếu hàng bản đó -> FAIL (cũ: ghi cứng 2.3.0).
UV=$(sed -n 's/^UNRELEASED_AS = "\([0-9.]*\)".*/\1/p' "$ROOT/tools/integration_doc_check.sh")
cp "$IGDOC" "$TMP/dc13.md"; python3 -c 'import re,sys;p,v=sys.argv[1:3];s=open(p,encoding="utf-8").read();open(p,"w",encoding="utf-8").write(re.sub(r"^\| "+re.escape(v)+" .*\n","",s,flags=re.M))' "$TMP/dc13.md" "$UV"  # xoá MỌI hàng của bản UNRELEASED_AS
dc13=$(vis "$s11b" "$TMP/dc13.md")
check "DC13 §13 thiếu hàng $UV (UNRELEASED_AS) khi [Unreleased] có '### Phải làm khi nâng cấp' -> FAIL 'bảng nâng cấp thiếu hàng $UV'" \
  "$([ -n "$UV" ] && PAT="^## \[(Unreleased|${UV//./\\.})\]" awk '$0 ~ ENVIRON["PAT"]{u=1;next} /^## \[/{u=0} u&&/^### Phải làm khi nâng cấp/{f=1} END{exit !f}' "$ROOT/CHANGELOG.md" \
     && has "$dc13" "FAIL: bảng nâng cấp thiếu hàng $UV" && has "$dc13" 'rc=1'; echo $?)" "$dc13"

# DC14: không có heading `## 9.` (đổi tên trong bản sao) -> FAIL fail-closed, không PASS với 0 tên được kiểm (#11).
cp "$IGDOC" "$TMP/dc14.md"; pyedit "$TMP/dc14.md" '## 9. API theo thứ tự gọi' '## 9 API theo thứ tự gọi'
dc14=$(vis "$s11" "$TMP/dc14.md")
check "DC14 heading '## 9.' vắng (nguồn có RemoveAdsMask internal) -> FAIL '§9: không tìm thấy mục', rc=1" \
  "$(has "$dc14" 'FAIL: §9: không tìm thấy mục' && has "$dc14" 'rc=1'; echo $?)" "$dc14"
# 2.3.0 IG2 DC14b/DC15/DC16: bản thật không FAIL luật mới; bản sao thay $2 -> $3 (mọi lần) FAIL đúng luật $4, rc=1.
dcn() { python3 -c 'import sys;a,o,x,y=sys.argv[1:];open(o,"w",encoding="utf-8").write(open(a,encoding="utf-8").read().replace(x,y))' "$IGDOC" "$TMP/$1.md" "$2" "$3"; local r m; r=$(vis "$s11b"); m=$(vis "$s11b" "$TMP/$1.md"); check "$1 tài liệu thật PASS, bản sao bỏ '$2' -> FAIL '$4', rc=1" "$(hasnt "$r" "$4" && has "$m" "FAIL: $4" && has "$m" 'rc=1'; echo $?)" "$r$m"; }
dcn dc14b exclusiveContent exclusiveConten7 'thiếu mục bắt buộc: exclusiveContent'
dcn dc14c $'filter { includeGroup("com.app.plugin") }\n' $'filter { includeGroup("x") }\n' 'thiếu mục bắt buộc: exclusiveContent'  # I-1: chỉ khối mã mất includeGroup, văn xuôi còn
dcn dc15 license.properties license.prop3rties 'thiếu mục bắt buộc: license.properties'
dcn dc16 com.app.plugin.BRAIN_POLICY_URL com.app.plugin.BRAIN_POLICY_UR7 'khoá meta-data com.app.plugin.BRAIN_POLICY_URL'
# DC17 (R-ROB3 m1): ALL internal + object thứ hai cùng file có `public const val ALL` -> FAIL (cũ PASS). DC18 (m2): ConsentManager hợp nhất internal -> FAIL dù bản ads.consent public.
s17=$(src_copy dc17); pyedit "$s17/$MASK" 'public const val ALL: Int' 'internal const val ALL: Int'; printf '\ninternal object OtherMask {\n    public const val ALL: Int = 1\n}\n' >> "$s17/$MASK"; d17=$(vis "$s17")
check "DC17 RemoveAdsMask.ALL internal, OtherMask.ALL public cùng file -> FAIL '§9: RemoveAdsMask.ALL không public trong', rc=1" "$(has "$d17" 'FAIL: §9: RemoveAdsMask.ALL không public trong' && has "$d17" 'rc=1'; echo $?)" "$d17"
s18=$(src_copy dc18); pyedit "$s18/com/app/plugin/consent/ConsentManager.kt" 'public object ConsentManager' 'internal object ConsentManager'; d18=$(vis "$s18")
check "DC18 com.app.plugin.consent.ConsentManager internal -> FAIL '§9: ConsentManager không public trong …consent/ConsentManager.kt', rc=1" "$(has "$d18" 'FAIL: §9: ConsentManager không public trong' && has "$d18" 'consent/ConsentManager.kt' && has "$d18" 'rc=1'; echo $?)" "$d18"

# DC19/DC20 (2.4.0 IG3): §3 phải có hàng bảng cho đủ 4 artifactId, và câu "`appplugin-max` + `appplugin-nextgen`: không hỗ trợ".
dcn dc19 '| `com.app.plugin:appplugin-nextgen` | app chọn' '| nextgen | app chọn' '§3: thiếu hàng bảng artifact appplugin-nextgen'
dcn dc20 '`appplugin-max` + `appplugin-nextgen`: không hỗ trợ' '`appplugin-max` + `appplugin-nextgen`: hỗ trợ' '§3: thiếu câu "`appplugin-max` + `appplugin-nextgen`: không hỗ trợ"'

dcn dc19a '| `com.app.plugin:appplugin` | mọi app' '| appplugin | mọi app' '§3: thiếu hàng bảng artifact appplugin'
dcn dc19g '| `com.app.plugin:appplugin-game` | app game' '| game | app game' '§3: thiếu hàng bảng artifact appplugin-game'
dcn dc19m '| `com.app.plugin:appplugin-max` | app dùng' '| max | app dùng' '§3: thiếu hàng bảng artifact appplugin-max'

dg_finish
