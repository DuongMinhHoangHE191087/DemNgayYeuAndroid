#!/usr/bin/env bash
# Test không cần máy của cổng thiết bị — vùng banners: rwi trong pha A, --partners của bundle_size, --banners.
# Chạy: bash tools/tests/dg_banners_test.sh (hoặc qua runner device_gate_allowlist_test.sh). exit 0 = xanh.
. "$(dirname "$0")/lib/dg_harness.sh" || exit 99

# ── 1.9.0 Task 10: rwi trong pha A, --partners của bundle_size, --banners, meta-data BRAIN_HARNESS của release gate ──
# D1: nút "Show Rewarded Interstitial" có dòng xác nhận 'showRwi call show' (cũ: show_full không biết nút, pha A không chạm nó).
prepare_daily
d1=$(run_daily); d1rc=$?
tf5_run wd_run 20 tf5_lib mg_show_full "Show Rewarded Interstitial"; d1src=$?; d1o=$(cat "$TMP/wd.out")
check "D1 pha A chạm 'Show Rewarded Interstitial' có dòng xác nhận: daily PASS với dòng tap + 'showRwi call show' tới app; mg_show_full trực tiếp rc=0 (cũ: nút lạ, rc≠0, không có dòng)" \
  "$([ "$d1rc" -eq 0 ] && has "$d1" "tap 'Show Rewarded Interstitial'" && has "$d1" "'showRwi call show' tới app" && has "$d1" 'RESULT: PASS' \
    && [ "$d1src" -eq 0 ] && has "$d1o" "tap 'Show Rewarded Interstitial'" && hasnt "$d1o" 'không biết nút'; echo $?)" "daily rc=$d1rc
$d1
show_full rc=$d1src
$d1o"

# D2: bundle_size.sh --partners <csv> ⊆ {meta,pangle,mintegral,unity,vungle,applovin}; từ lạ/lặp/rỗng FAIL TRƯỚC Gradle; csv hợp lệ đi tiếp (in partners=…) và chuyển -PbiddingPartners.
D2R=$TMP/bs-root; rm -rf "$D2R"; mkdir -p "$D2R/tools"; cp "$ROOT/tools/bundle_size.sh" "$D2R/tools/"
printf '#!/usr/bin/env bash\necho "$*" >> "$D2R/gradle-calls"\nexit 1\n' > "$D2R/gradlew"; chmod +x "$D2R/gradlew"
d2() { D2R=$D2R bash "$D2R/tools/bundle_size.sh" --variant utility "$@" 2>&1; }
d2a=$(d2 --partners foo); d2arc=$?
d2b=$(d2 --partners pangle,pangle); d2brc=$?
d2c=$(d2 --partners meta,,pangle); d2crc=$?
d2gc=no; [ ! -e "$D2R/gradle-calls" ] || d2gc=yes
d2d=$(d2 --partners pangle,mintegral,unity,vungle,applovin)
d2dargs=$(cat "$D2R/gradle-calls" 2>/dev/null)
check "D2 --partners foo FAIL trước Gradle (nêu 'foo' ngoài tập, 0 lệnh gradlew); lặp và csv có ô rỗng cũng FAIL; csv hợp lệ không bị chặn (in partners=…); nguồn chuyển -PbiddingPartners" \
  "$([ "$d2arc" -ne 0 ] && has "$d2a" "'foo'" && has "$d2a" 'ngoài tập' && has "$d2a" 'RESULT: FAIL' && [ "$d2gc" = no ] \
    && [ "$d2brc" -ne 0 ] && has "$d2b" 'lặp' && [ "$d2crc" -ne 0 ] && has "$d2c" 'rỗng' && hasnt "$d2d" 'ngoài tập' && hasnt "$d2d" 'lặp' \
    && has "$d2d" 'partners=pangle,mintegral,unity,vungle,applovin' && { [ -z "$d2dargs" ] || has "$d2dargs" '-PbiddingPartners=pangle,mintegral,unity,vungle,applovin'; } && command grep -q -- '-PbiddingPartners=' "$ROOT/tools/bundle_size.sh"; echo $?)" "foo rc=$d2arc: $d2a
dup rc=$d2brc: $d2b
empty rc=$d2crc: $d2c
gradle antes de csv hop le: $d2gc
valid: $d2d
gradle args: $d2dargs"

# D8 (D46): meta là partner mặc định (implementation cố định), KHÔNG đi qua --partners: FAIL trước Gradle như 'foo', một dòng nêu meta mặc định.
rm -f "$D2R/gradle-calls"
d8a=$(d2 --partners meta); d8arc=$?
d8b=$(d2 --partners pangle,meta); d8brc=$?
check "D8 --partners meta (và pangle,meta) FAIL trước Gradle, một dòng nêu 'meta' là mặc định (luôn có), 0 lệnh gradlew" \
  "$([ "$d8arc" -ne 0 ] && [ "$d8brc" -ne 0 ] && has "$d8a" 'meta (mặc định, luôn có)' && has "$d8b" 'meta (mặc định, luôn có)' && [ "$(printf '%s\n' "$d8a" | command grep -c '^FAIL')" -eq 1 ] && [ ! -e "$D2R/gradle-calls" ]; echo $?)" "meta rc=$d8arc: $d8a
pangle,meta rc=$d8brc: $d8b"

# --banners: adb giả riêng (nút theo uiautomator, mỗi lần chạm nút collapsible ghi 'showBanner call show', nút NtCl ×3 ghi 3 dòng
# 'ntcl attach children=…'). FAKE_BN_PARENT=<n>: n dòng 'already has a parent'; FAKE_BN_NTCL=1|2|0: giá trị children / không có dòng; FAKE_BN_SHOW=0:
# không có dòng showBanner; FAKE_ADVIEW=<n>: n dòng AdView trong `dumpsys activity top`.
mkdir -p "$TMP/bbin"
cat > "$TMP/bbin/adb" <<'EOF'
#!/usr/bin/env bash
echo "$*" >> "$ADB_CALLS"
CX="540 1350"; NX="540 1750"
# FAKE_BN_NTCL_AT=<k>: dòng 'ntcl attach' chỉ hiện từ lần `logcat -d` thứ k sau lần chạm NtCl (k=10: 1 lần kiểm chạm + poll thứ 9 = giây 8; B6/B7).
late() { awk -v n="$1" '/^shell input tap 540 1750$/{c=0} /^logcat -d/{c++} END{exit !(c>=n)}' "$ADB_CALLS"; }
taps() { grep -c "^shell input tap $1\$" "$ADB_CALLS"; }
case "$*" in
  install*) echo Success;;
  "shell getprop ro.build.version.sdk") echo 34;;   # 2.0.0 Task 0d: cổng đọc API vào dòng 1
  "shell wm size") echo "Physical size: 1080x2400";;
  "shell wm density") echo "Physical density: 420";;
  "shell am start -W"*) printf 'Status: ok\nTotalTime: 100\nWaitTime: 110\n';;
  "shell pidof"*) echo 4242;;
  "shell pm clear"*) echo Success;;
  "shell cat /sdcard/ui.xml") echo '<?xml version="1.0"?><hierarchy rotation="0"><node index="0" text="Show Banner Collapsible" bounds="[0,1300][1080,1400]" /><node index="1" text="NtCl show/hide x3" bounds="[0,1700][1080,1800]" /></hierarchy>';;
  "shell input tap $NX") echo "${BN_VCLK:-0}" > "$ADB_CALLS.tapclk"
    # FAKE_TERM=1 (B8c): ngắt giả — gửi TERM cho tiến trình device_gate.sh đang chạy ngay sau lần chạm NtCl
    if [ "${FAKE_TERM:-0}" = 1 ]; then p=$$; for _ in 1 2 3 4 5 6 7 8; do p=$(ps -o ppid= -p "$p" | tr -d ' '); [ -n "$p" ] || break; case "$(ps -o command= -p "$p")" in *device_gate.sh*) kill -TERM "$p"; break;; esac; done
      # 2.1.0 T0e (M27): chờ trên fifo tới khi trap TERM của adb_b kill con này (read -t 30 chỉ là lưới an toàn): TERM luôn tới khi trap đang gắn, không phụ thuộc tốc độ máy
      read -t 30 _ <> "$ADB_CALLS.fifo"; fi;;   # B9/M6: đồng hồ ảo của cổng lúc chạm NtCl
  "shell settings get system accelerometer_rotation") echo "${FAKE_ROT:-1}";;   # B8: giá trị gốc của máy giả
  "shell dumpsys window"*) echo "  mCurrentFocus=Window{5e6f u0 test.sdk.zd/com.app.plugin.demo.MainActivity}";;
  "shell dumpsys activity top"*) [ "${FAKE_ADOBF:-0}" = 0 ] || { printf 'TASK null id=2 userId=0\n  ACTIVITY com.miui.home/.launcher.Launcher 60c pid=1\n    View Hierarchy:\n      i4{111 V.E...... ........ 0,0-9,9}\nTASK %s id=%s userId=0\n  ACTIVITY test.sdk.zd/com.app.plugin.demo.MainActivity 9ef pid=2\n    View Hierarchy:\n      xi4{222 V.E...... ........ 0,0-9,9}\n      i4a{333 V.E...... ........ 0,0-9,9}\n      i4{444 V.E...... ........ 0,0-1016,200}\n' "$([ "$FAKE_ADOBF" = 2 ] && echo test.sdk.zd || echo 10744:test.sdk.zd)" "$([ "$FAKE_ADOBF" = 2 ] && echo 13 || echo 5788)"; exit 0; }
    [ -z "${FAKE_ADNG:-}" ] || { cat "$NG_FX"; exit 0; }   # N1: dump thật Samsung của APK nextgen (view banner Next-Gen ads_mobile_sdk.*)
    n=${FAKE_ADVIEW:-1}   # B9: FAKE_ADVIEW_NTCL=<n> chỉ giữa lần chạm NtCl và HOME (sau HOME về lại FAKE_ADVIEW)
    [ -z "${FAKE_ADVIEW_NTCL:-}" ] || [ "$(taps "$NX")" -eq 0 ] || grep -q KEYCODE_HOME "$ADB_CALLS" || n=$FAKE_ADVIEW_NTCL
    # FAKE_NTCL_CYCLE=1: vòng NtCl x3 của demo (15 s đồng hồ ảo từ lần chạm) đang chạy → NtCl che AdView (thật ở 5570: adview_ntcl=0 lúc đó)
    [ -z "${FAKE_NTCL_CYCLE:-}" ] || [ "$(taps "$NX")" -eq 0 ] || grep -q KEYCODE_HOME "$ADB_CALLS" || [ $(( ${BN_VCLK:-0} - $(cat "$ADB_CALLS.tapclk" 2>/dev/null || echo 0) )) -ge 15 ] || n=0
    i=0; while [ "$i" -lt "$n" ]; do i=$((i + 1)); echo "      com.google.android.gms.ads.AdView{abc$i V.E...... ......ID 0,0-1080,200 #$i}"; done;;
  "logcat -d"|"logcat -d --pid=4242")
    n=$(taps "$CX"); i=0
    while [ "$i" -lt "$n" ]; do i=$((i + 1)); [ "${FAKE_BN_SHOW:-1}" = 0 ] || echo "09-30 04:00:10.000  4242  4242 D AppPlugin: ads bn helper bn_collapsible showBanner call show"; done
    n=$(taps "$NX"); i=0; m=$((n * 3)); [ -z "${FAKE_BN_NTCL_AT:-}" ] || late "$FAKE_BN_NTCL_AT" || m=0
    [ "$n" -eq 0 ] || echo "09-30 04:00:20.000  4242  4242 I AppPluginDemo: ntcl show/hide x3 start"
    while [ "$i" -lt "$m" ]; do i=$((i + 1)); case "${FAKE_BN_NTCL:-1}" in 0) :;; *) echo "09-30 04:00:21.000  4242  4242 D AppPlugin: ntcl attach children=${FAKE_BN_NTCL:-1}";; esac; done
    i=0; while [ "$i" -lt "${FAKE_BN_PARENT:-0}" ]; do i=$((i + 1)); echo "09-30 04:00:22.000  4242  4242 E AndroidRuntime: java.lang.IllegalStateException: The specified child already has a parent. You must call removeView() on the child's parent first."; done;;
esac
exit 0
EOF
chmod +x "$TMP/bbin/adb"
prep_bn() { prepare_gate "$TMP/good.txt"; mkdir -p "$GATE_ROOT/DOC/testing/local-sink"; : > "$GATE_ROOT/DOC/testing/local-sink/events.jsonl"; }   # ngoài $( ) để giữ GATE_ROOT
run_bn() {  # biến FAKE_BN_*, FAKE_ADVIEW đi theo môi trường của lời gọi; in đầu ra của cổng, exit như cổng
  [ -p "$TMP/adb-calls.fifo" ] || mkfifo "$TMP/adb-calls.fifo"
  ADB_CALLS=$TMP/adb-calls PATH="$TMP/bbin:$TMP/bin:$PATH" MG_NO_SLEEP=1 ANDROID_SERIAL=stub-serial \
    bash "$GATE_ROOT/tools/device_gate.sh" --variant "${BN_VARIANT:-utility}" --banners --apk "$GATE_ROOT/apk/app-release.apk" --mapping "$GATE_ROOT/apk/mapping.txt" 2>&1
}
prep_bn; d0=$(run_bn); d0rc=$?
prep_bn; d3=$(FAKE_BN_PARENT=2 run_bn); d3rc=$?
check "D3 --banners: log giả sạch → PASS (parent=0, AdView=1, ntcl attach children=1, 0 monkey); 2 dòng 'The specified child already has a parent' → FAIL nêu parent=2" \
  "$([ "$d0rc" -eq 0 ] && has "$d0" 'parent=0' && has "$d0" 'RESULT: PASS' && hasnt "$d0" 'shell monkey' && [ "$d3rc" -eq 1 ] && has "$d3" 'parent=2' && has "$d3" 'RESULT: FAIL' && hasnt "$d3" 'RESULT: PASS'; echo $?)" "sạch rc=$d0rc
$d0
parent=2 rc=$d3rc
$d3"
prep_bn; d4=$(FAKE_ADVIEW=2 run_bn); d4rc=$?
check "D4 dumpsys activity top giả 2 AdView → FAIL nêu adview=2 (mỗi host đúng 1), không PASS" \
  "$([ "$d4rc" -eq 1 ] && has "$d4" 'adview=2' && has "$d4" 'RESULT: FAIL' && hasnt "$d4" 'RESULT: PASS'; echo $?)" "rc=$d4rc
$d4"
# D4m (F3-t1): release R8 đổi tên AdView -> 'i4'; cổng đọc tên từ mapping. Dump giả như Redmi: task launcher có i4{ (ngoài app), app có xi4{ và i4a{ (decoy) + đúng 1 i4{.
prep_bn; printf 'com.google.android.gms.ads.AdView -> i4:\n' >> "$GATE_ROOT/apk/mapping.txt"; d4m1=$(FAKE_ADOBF=1 run_bn); d4m1rc=$?
prep_bn; d4m2=$(FAKE_ADOBF=1 run_bn); d4m2rc=$?
prep_bn; printf 'com.google.android.gms.ads.AdView -> i4:\n' >> "$GATE_ROOT/apk/mapping.txt"; d4m3=$(run_bn); d4m3rc=$?
check "D4m AdView sau R8: mapping 'AdView -> i4:' + dump i4{ (decoy xi4{, i4a{, i4{ của launcher) → PASS adview=1/1 nêu 'after R8: i4'; thiếu dòng mapping → lùi về '.AdView{' nên adview=0/0 FAIL, nêu 'after R8: none'; mapping có + tên rõ '.AdView{' vẫn PASS" \
  "$([ "$d4m1rc" -eq 0 ] && has "$d4m1" 'adview=1/1' && has "$d4m1" 'AdView name after R8: i4' && has "$d4m1" 'RESULT: PASS' && [ "$d4m2rc" -eq 1 ] && has "$d4m2" 'adview=0/0' && has "$d4m2" 'AdView name after R8: none' && hasnt "$d4m2" 'RESULT: PASS' && [ "$d4m3rc" -eq 0 ] && has "$d4m3" 'adview=1/1'; echo $?)" "có mapping rc=$d4m1rc
$d4m1
thiếu mapping rc=$d4m2rc
$d4m2
mapping + tên rõ rc=$d4m3rc
$d4m3"
# D4n (F3-t2): API 28/26 in dòng 'TASK <pkg> id=N userId=0' (không có 'uid:' phía trước); API 29+ giữ 'TASK <uid>:<pkg> …'. Cả hai phải đếm đúng 1 i4{ của app.
prep_bn; printf 'com.google.android.gms.ads.AdView -> i4:\n' >> "$GATE_ROOT/apk/mapping.txt"; d4n1=$(FAKE_ADOBF=2 run_bn); d4n1rc=$?
check "D4n dòng TASK không có uid (API 28/26) + mapping i4 → PASS adview=1/1 (launcher i4{, decoy xi4{ i4a{ bị loại); dạng 'TASK <uid>:<pkg>' (API 29+) vẫn adview=1/1" \
  "$([ "$d4n1rc" -eq 0 ] && has "$d4n1" 'adview=1/1' && has "$d4n1" 'RESULT: PASS' && [ "$d4m1rc" -eq 0 ] && has "$d4m1" 'adview=1/1'; echo $?)" "API 28 rc=$d4n1rc
$d4n1
API 29+ rc=$d4m1rc
$d4m1"
prep_bn; d5=$(FAKE_BN_NTCL=2 run_bn); d5rc=$?
check "D5 'ntcl attach children=2' → FAIL nêu ntcl_bad=3 (mọi dòng attach phải children=1), không PASS" \
  "$([ "$d5rc" -eq 1 ] && has "$d5" 'ntcl_bad=3' && has "$d5" 'RESULT: FAIL' && hasnt "$d5" 'RESULT: PASS'; echo $?)" "rc=$d5rc
$d5"
prep_bn; d6a=$(FAKE_BN_NTCL=0 run_bn); d6arc=$?
prep_bn; d6b=$(FAKE_BN_SHOW=0 run_bn); d6brc=$?
prep_bn; d6c=$(FAKE_ADVIEW=0 run_bn); d6crc=$?
check "D6 fail-closed: thiếu dòng 'ntcl attach children=1', hoặc thiếu 'showBanner call show', hoặc 0 AdView → mỗi ca FAIL (thiếu bằng chứng không phải PASS)" \
  "$([ "$d6arc" -ne 0 ] && has "$d6a" 'RESULT: FAIL' && hasnt "$d6a" 'RESULT: PASS' && [ "$d6brc" -ne 0 ] && hasnt "$d6b" 'RESULT: PASS' && [ "$d6crc" -ne 0 ] && has "$d6c" 'adview=0' && hasnt "$d6c" 'RESULT: PASS'; echo $?)" "ntcl vắng rc=$d6arc
$d6a
showBanner vắng rc=$d6brc
$d6b
AdView 0 rc=$d6crc
$d6c"

# ── 2.0.0 Task 0b: fixture dump thật (B1–B5), chờ ntcl có hạn (B6/B7, D55), khôi phục xoay (B8), AdView sau NtCl (B9) ──
FXD=$ROOT/tools/tests/fixtures/dumps
bnfx() {  # $1 = hậu tố fixture (tên file không gồm -top{1,2}.txt) → in "<top1> <top2>" = số AdView trong khối TASK của app
  ( MAP=$FXD/mapping-adview.txt; MG_PKG=test.sdk.zd; FIX=$FXD/device-gate-$1-banners
    adb_b() { shift; cat "$FIX-top$FXN.txt"; }
    . "$ROOT/tools/lib/banners_gate.sh"
    FXN=1; a=$(bn_adview "$TMP/fx.out"); FXN=2; b=$(bn_adview "$TMP/fx.out"); echo "$a $b" )
}
# B1–B5 (đặc tả, F3-t1/t2 đã xanh): mapping thật 'AdView -> i4' + dump thật của API 37/33/28/26/Redmi (hai dạng dòng TASK) → đúng 1 AdView ở top1 và top2.
for b in "B1 API 37|emulator-5570-20261001-164056" "B2 API 33|emulator-5590-20261001-180620" "B3 API 28|emulator-5580-20261001-181620" "B4 API 26|emulator-5586-20261001-182453" "B5 Redmi|65c9b673-20261001-174631"; do
  bo=$(bnfx "${b#*|}")
  check "${b%%|*} bn_adview trên dump thật (mapping AdView -> i4): top1 và top2 đều = 1 AdView của app (launcher i4{ và task khác bị loại)" "$([ "$bo" = "1 1" ]; echo $?)" "kết quả: '$bo'"
done
# B6 (D55): dòng 'ntcl attach children=1' chỉ xuất hiện ở giây 8 sau NtCl (poll thứ 9) → chờ có hạn thấy và đếm (cũ: đọc log 1 lần ngay sau NtCl → ntcl_ok=0).
prep_bn; b6=$(FAKE_BN_NTCL_AT=10 run_bn); b6rc=$?
check "B6 'ntcl attach children=1' xuất hiện ở giây 8 → chờ có hạn thấy, ntcl_ok=3 ntcl_bad=0, PASS (cũ: ntcl_ok=0)" \
  "$([ "$b6rc" -eq 0 ] && has "$b6" 'ntcl_ok=3 ntcl_bad=0' && has "$b6" 'RESULT: PASS'; echo $?)" "rc=$b6rc
$b6"
# B7: không có dòng attach trong hạn (≤ 30 lần chờ 1 s [estimate]) → hết hạn = FAIL, nêu 'hết hạn 30 s'; số lần poll bị chặn.
prep_bn; b7=$(FAKE_BN_NTCL_AT=99 run_bn); b7rc=$?
b7n=$(command grep -c '^logcat -d$' "$TMP/adb-calls")
check "B7 không có 'ntcl attach children=1' trong hạn → FAIL nêu 'hết hạn 30 s' (B7), không PASS, số lần logcat -d bị chặn (< 40)" \
  "$([ "$b7rc" -eq 1 ] && has "$b7" 'hết hạn 30 s' && has "$b7" 'no-fill: ntcl attach not seen within 30 s (D55: one re-run allowed)' && has "$b7" 'ntcl_ok=0' && hasnt "$b7" 'RESULT: PASS' && [ "$b7n" -lt 40 ]; echo $?)" "rc=$b7rc logcat-d=$b7n
$b7"
# B8: accelerometer_rotation gốc = 1 (và = 0): cổng đặt 0 để xoay → lệnh xoay cuối, SAU lần đặt user_rotation cuối, phải đưa về đúng giá trị gốc (cũ: không khôi phục).
lastn() { command grep -n "^shell settings put system $1 " "$TMP/adb-calls" | tail -1 | cut -d: -f1; }
prep_bn; b8=$(FAKE_ROT=1 run_bn); b8rc=$?
b8last=$(command grep '^shell settings put system accelerometer_rotation ' "$TMP/adb-calls" | tail -1)
b8n=$(command grep -c '^shell settings put system accelerometer_rotation 1$' "$TMP/adb-calls"); b8u=$(lastn user_rotation); b8a=$(lastn accelerometer_rotation)
prep_bn; b8z=$(FAKE_ROT=0 run_bn); b8zrc=$?
b8zn=$(command grep -c '^shell settings put system accelerometer_rotation 1$' "$TMP/adb-calls"); b8zu=$(lastn user_rotation); b8za=$(lastn accelerometer_rotation)
prep_bn; b8t=$(FAKE_ROT=1 FAKE_TERM=1 run_bn); b8trc=$?
b8tu=$(lastn user_rotation); b8ta=$(lastn accelerometer_rotation); b8tl=$(command grep '^shell settings put system accelerometer_rotation ' "$TMP/adb-calls" | tail -1)
check "B8 gốc=1: lệnh xoay cuối là 'accelerometer_rotation 1' (đúng 1 lần, sau user_rotation cuối); gốc=0: 0 lần đặt 1 và lệnh xoay cuối sau user_rotation cuối; bị ngắt TERM giữa chừng (trap EXIT) vẫn khôi phục (cũ: không khôi phục)" \
  "$([ "$b8rc" -eq 0 ] && [ "$b8last" = 'shell settings put system accelerometer_rotation 1' ] && [ "$b8n" -eq 1 ] && [ "$b8a" -gt "$b8u" ] \
    && [ "$b8zrc" -eq 0 ] && [ "$b8zn" -eq 0 ] && [ "$b8za" -gt "$b8zu" ] \
    && [ "$b8trc" -ne 0 ] && hasnt "$b8t" 'RESULT: PASS' && [ "$b8tl" = 'shell settings put system accelerometer_rotation 1' ] && [ "$b8ta" -gt "$b8tu" ]; echo $?)" "gốc 1 rc=$b8rc last='$b8last' n=$b8n acc@$b8a user@$b8u
gốc 0 rc=$b8zrc n1=$b8zn acc@$b8za user@$b8zu
TERM rc=$b8trc last='$b8tl' acc@$b8ta user@$b8tu
$b8t"
# B9: (a) sau NtCl x3 (trước HOME) có 2 AdView, còn lại 1 → FAIL nêu adview_ntcl=2 (cũ: không lấy mẫu giữa chừng); (b) mô hình vòng 15 s: NtCl che AdView tới 15 s kể từ lần chạm,
# attach thấy ở giây 8 → mẫu phải lấy sau t0+18 s (đồng hồ ảo) → PASS adview_ntcl=1; bỏ chờ sau attach (M6) thì mẫu = 0 → FAIL.
prep_bn; b9=$(FAKE_ADVIEW_NTCL=2 run_bn); b9rc=$?
prep_bn; b9b=$(FAKE_NTCL_CYCLE=1 FAKE_BN_NTCL_AT=10 run_bn); b9brc=$?
check "B9 AdView sau NtCl x3: 2 view → FAIL adview_ntcl=2; mô hình vòng 15 s + attach giây 8 → mẫu sau 18 s: PASS adview_ntcl=1 (bỏ chờ sau attach: adview_ntcl=0 FAIL)" \
  "$([ "$b9rc" -eq 1 ] && has "$b9" 'adview_ntcl=2' && has "$b9" 'adview=1/1' && hasnt "$b9" 'RESULT: PASS' \
    && [ "$b9brc" -eq 0 ] && has "$b9b" 'adview_ntcl=1' && has "$b9b" 'RESULT: PASS'; echo $?)" "rc=$b9rc
$b9
cycle rc=$b9brc
$b9b"

# ── 2.4.0 FIX-GATE (D25): --variant nextgen đếm view banner Next-Gen (ads_mobile_sdk.*, dump thật Samsung của APK nextgen) ──
NG_FX=$FXD/device-gate-R5CX10P9EKZ-20261006-ng-banners-top1.txt; export NG_FX
ngc() { ( MG_PKG=test.sdk.zd; STACK=$1; X=${2:-}; adb_b() { { cat "$NG_FX"; [ -z "$X" ] || echo "      com.google.android.gms.ads.AdView{abc V.E...... ......ID 0,0-1080,200 #1}"; }; }; . "$ROOT/tools/lib/banners_gate.sh"; bn_adview "$TMP/fx.out"; ) ; }
ng1=$(ngc nextgen); ng2=$(ngc utility); ng3=$(ngc nextgen x)
prep_bn; n1=$(FAKE_ADNG=1 BN_VARIANT=nextgen run_bn); n1rc=$?
prep_bn; n2=$(FAKE_ADNG=1 run_bn); n2rc=$?
check "N1 --variant nextgen --banners: dump có 4 view ads_mobile_sdk.* (1 cây = 1 banner) → adview=1/1 adview_ntcl=1, PASS (cũ: adview=0/0 FAIL); đếm trực tiếp nextgen=1, utility=0 (lớp Next-Gen chỉ tính ở stack nextgen), nextgen + 1 gms AdView = 2 (đếm cả hai); --variant utility vẫn FAIL adview=0/0" \
  "$([ "$n1rc" -eq 0 ] && has "$n1" 'adview=1/1' && has "$n1" 'adview_ntcl=1' && has "$n1" 'RESULT: PASS' && [ "$ng1" = 1 ] && [ "$ng2" = 0 ] && [ "$ng3" = 2 ] && [ "$n2rc" -eq 1 ] && has "$n2" 'adview=0/0'; echo $?)" "nextgen rc=$n1rc
$n1
đếm: nextgen=$ng1 utility=$ng2 nextgen+gms=$ng3
utility rc=$n2rc: $n2"

dg_finish
