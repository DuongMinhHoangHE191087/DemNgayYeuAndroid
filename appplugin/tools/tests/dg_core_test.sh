#!/usr/bin/env bash
# Test không cần máy của cổng thiết bị — vùng core: allowlist/count_r8, cổng thật với adb giả, StrictMode, AppsFlyer, LVL, G1-G3, --variant.
# Chạy: bash tools/tests/dg_core_test.sh (hoặc qua runner device_gate_allowlist_test.sh). exit 0 = xanh.
. "$(dirname "$0")/lib/dg_harness.sh" || exit 99

# ── 1. Regex hỏng / khớp mọi dòng: FAIL trước khi đếm, lý do vào cả stdout lẫn $LOG ───────────────
# 'foo|': BSD grep báo lỗi (exit 2), GNU grep nhận và khớp mọi dòng. 'Foo.bar(Foo.java:12': ngoặc
# không đóng, cả hai grep exit 2. '.*': hợp lệ trên cả hai nhưng khớp dòng rỗng (đường của GNU 'foo|').
i=0
for re in 'foo|' 'Foo.bar(Foo.java:12' '.*'; do
  i=$((i + 1)); L=$TMP/bad-$i.log
  a=$(allowlist "bad-$i.txt" '# chú thích' "$re")
  out=$(run_lib "$a" "$APPLOG" "$L"); rc=$?
  check "[$re] exit khác 0" "$([ "$rc" -ne 0 ]; echo $?)" "$out"
  check "[$re] RESULT: FAIL, không PASS" "$(has "$out" 'RESULT: FAIL' && hasnt "$out" 'RESULT: PASS'; echo $?)" "$out"
  check "[$re] lý do là allowlist regex" "$(has "$out" 'FAIL: allowlist regex'; echo $?)" "$out"
  check "[$re] không đếm (không có dòng r8 strings)" "$(hasnt "$out" 'r8 strings'; echo $?)" "$out"
  check "[$re] \$LOG có lý do và RESULT: FAIL" \
    "$(has "$(cat "$L")" 'FAIL: allowlist regex' && has "$(cat "$L")" 'RESULT: FAIL'; echo $?)" "$(cat "$L")"
done

# ── 2. Allowlist hợp lệ: 2 regex (cùng khớp 1 dòng), chú thích, dòng trống ─────────────────────────
a=$GOOD_AL
out=$(run_lib "$a" "$APPLOG" "$TMP/good.log"); rc=$?
check "hợp lệ: đếm 2 regex (không phải 1)" "$(has "$out" 'allowlist: 2 regex'; echo $?)" "$out"
check "hợp lệ: r8=2 (raw=4, allowlisted=2)" "$(has "$out" 'r8 strings fixture=2 (raw=4, allowlisted=2; need 0)'; echo $?)" "$out"
check "hợp lệ: in đúng 2 dòng r8> còn lại" "$([ "$(printf '%s\n' "$out" | grep -c '^  r8> .*NoClassDefFoundError')" -eq 2 ]; echo $?)" "$out"
check "hợp lệ: RESULT: FAIL, exit 1" "$(has "$out" 'RESULT: FAIL' && [ "$rc" -eq 1 ]; echo $?)" "$out"
check "hợp lệ: \$LOG chép dòng bị loại" "$(has "$(cat "$TMP/good.log")" '  allowlisted> 09-24 16:16:29.559'; echo $?)" "$(cat "$TMP/good.log")"
out=$(run_lib "$a" "$APPLOG_OK" "$TMP/ok.log"); rc=$?
check "hợp lệ, chỉ dòng benign: r8=0 và PASS" \
  "$(has "$out" 'r8 strings fixture=0 (raw=2, allowlisted=2; need 0)' && has "$out" 'RESULT: PASS' && [ "$rc" -eq 0 ]; echo $?)" "$out"

# ── 3. Allowlist thật của repo phải qua kiểm và loại đúng dòng benign ─────────────────────────────
# 7 regex kể từ 1.6.3 Task 3: ProviderInstaller, LVL ×2 neo lớp chủ, MIUI ×2 neo lớp chủ, Samsung cloud game ×2 neo lớp chủ; dòng
# contention đã gỡ (Task 3). Fixture này không có các dòng của mục 10/12/15 nên r8 count không đổi. Hai dòng ClassValue
# của fixture vẫn bị đếm: G1 KHÔNG allowlist ClassValue (mục 12, ca âm).
out=$(run_lib "$ROOT/tools/device_gate_allowlist.txt" "$APPLOG" "$TMP/real.log")
check "allowlist thật: 7 regex, r8=2 (raw=4, allowlisted=2; có cả biến thể API 33)" \
  "$(has "$out" 'allowlist: 7 regex' && has "$out" 'r8 strings fixture=2 (raw=4, allowlisted=2; need 0)'; echo $?)" "$out"
t7a=$TMP/t7-api33.log
grep -F 'ProviderInstallerImpl.reportRequestStats2' "$APPLOG" > "$t7a"
out=$(run_lib "$ROOT/tools/device_gate_allowlist.txt" "$t7a" "$TMP/t7a-gate.log")
check "allowlist thật, dòng API 33 (ProviderInstallerImpl.reportRequestStats2): r8=0 (raw=1, allowlisted=1), PASS" \
  "$(has "$out" 'r8 strings fixture=0 (raw=1, allowlisted=1; need 0)' && has "$out" 'RESULT: PASS'; echo $?)" "$out"
t7b=$TMP/t7-other.log
echo '09-24 16:16:29.560  6139  6214 W ProviderInstaller: Failed to report request stats: java.lang.NoSuchMethodException: com.evil.Other.reportRequestStats2 [class android.content.Context, long, long]' > "$t7b"
out=$(run_lib "$ROOT/tools/device_gate_allowlist.txt" "$t7b" "$TMP/t7b-gate.log")
check "allowlist thật, lớp khác tiền tố (com.evil.Other.reportRequestStats2): vẫn đếm r8=1, FAIL" \
  "$(has "$out" 'r8 strings fixture=1 (raw=1, allowlisted=0; need 0)' && has "$out" 'RESULT: FAIL'; echo $?)" "$out"

# ── 4. Allowlist chỉ có chú thích: không loại gì; thiếu file: FAIL ─────────────────────────────────
a=$(allowlist empty.txt '# chỉ chú thích' '')
out=$(run_lib "$a" "$APPLOG" "$TMP/empty.log")
check "rỗng: 0 regex, r8=4 (raw=4, allowlisted=0), FAIL" \
  "$(has "$out" 'allowlist: 0 regex' && has "$out" 'r8 strings fixture=4 (raw=4, allowlisted=0; need 0)' && has "$out" 'RESULT: FAIL'; echo $?)" "$out"
out=$(run_lib "$TMP/khong-co.txt" "$APPLOG" "$TMP/missing.log")
check "thiếu file allowlist: FAIL" "$(has "$out" 'FAIL: không có' && has "$out" 'RESULT: FAIL'; echo $?)" "$out"

# ── 5. Lớp chặn thứ hai trong count_r8: không có allowlist hợp lệ đã nạp thì không được đếm ─────────
# ALLOW_RE hỏng lọt qua (vd sửa cổng sau này): grep -vE exit 2 phải dừng cổng, không thành 0 dòng.
out=$( (LOG=$TMP/guard.log; fail=0; . "$LIB"; load_allowlist "$TMP/good.txt"; ALLOW_RE='foo('
        count_r8 fixture "$APPLOG"; echo "RESULT: $([ "$fail" -eq 0 ] && echo PASS || echo FAIL)") 2>&1 )
check "count_r8, ALLOW_RE hỏng sau khi nạp: FAIL vì lọc lỗi, không in số đếm" \
  "$(has "$out" 'FAIL: lọc allowlist lỗi' && has "$out" 'RESULT: FAIL' && hasnt "$out" 'r8 strings'; echo $?)" "$out"
# Quên load_allowlist: dưới set -u biến chưa đặt làm drop_allowed exit 1, trông như "lọc hết".
out=$( (LOG=$TMP/noload.log; fail=0; . "$LIB"
        count_r8 fixture "$APPLOG"; echo "RESULT: $([ "$fail" -eq 0 ] && echo PASS || echo FAIL)") 2>&1 )
check "count_r8 trước load_allowlist: FAIL, không PASS" \
  "$(has "$out" 'RESULT: FAIL' && hasnt "$out" 'RESULT: PASS'; echo $?)" "$out"

# ── 6. Cổng thật với adb giả ─────────────────────────────────────────────────────────────────────
prepare_gate "$(allowlist gate-bad.txt 'foo|')"
out=$(run_gate)
check "cổng, allowlist 'foo|': FAIL vì allowlist regex, trước mọi lệnh adb" \
  "$(has "$out" 'FAIL: allowlist regex' && has "$out" 'RESULT: FAIL' && [ ! -s "$TMP/adb-calls" ]; echo $?)" \
  "$out
adb calls: $(cat "$TMP/adb-calls")"
check "cổng, allowlist 'foo|': \$LOG của cổng có RESULT: FAIL" "$(has "$(gate_log)" 'RESULT: FAIL'; echo $?)" "$(gate_log)"
prepare_gate "$TMP/good.txt"
out=$(run_gate)
check "cổng, allowlist 2 regex: in 'allowlist: 2 regex' vào stdout và \$LOG" \
  "$(has "$out" 'allowlist: 2 regex (' && has "$(gate_log)" 'allowlist: 2 regex ('; echo $?)" "$out"
prepare_gate "$TMP/good.txt" nolib
out=$(run_gate)
check "cổng thiếu lib: FAIL, trước mọi lệnh adb" \
  "$(has "$out" 'RESULT: FAIL' && [ ! -s "$TMP/adb-calls" ]; echo $?)" \
  "$out
adb calls: $(cat "$TMP/adb-calls")"

# ── 7. Log của app sau monkey: rỗng là không có bằng chứng, cổng FAIL ────────────────────────────
# Đối chứng trước: adb giả đi hết cổng với log có dòng thì PASS, nên ca rỗng FAIL chỉ vì log rỗng.
prepare_gate "$TMP/good.txt"
out=$(FAKE_ADB_FULL=1 run_gate)
check "cổng đủ bước, log sau monkey có dòng: r8 sau monkey = 0 và PASS" \
  "$(has "$out" 'r8 strings after monkey=0' && has "$out" 'RESULT: PASS' && hasnt "$out" 'RESULT: FAIL'; echo $?)" \
  "$out
adb calls: $(cat "$TMP/adb-calls")"
prepare_gate "$TMP/good.txt"
out=$(FAKE_ADB_FULL=1 FAKE_POST_LOG=empty run_gate)
check "cổng, log của app sau monkey rỗng: FAIL vì log rỗng, không PASS" \
  "$(has "$out" 'log của app sau monkey rỗng' && has "$out" 'RESULT: FAIL' && hasnt "$out" 'RESULT: PASS'; echo $?)" \
  "$out
adb calls: $(cat "$TMP/adb-calls")"
check "cổng, log của app sau monkey rỗng: \$LOG của cổng có lý do và RESULT: FAIL" \
  "$(has "$(gate_log)" 'log của app sau monkey rỗng' && has "$(gate_log)" 'RESULT: FAIL'; echo $?)" "$(gate_log)"

# ── 8. StrictMode: đếm frame SDK sau retrace (đợt sửa cuối Phase B, mục 9) ─────────────────────────
# Bản release đổi tên và repackage lớp SDK (com.app.plugin.until.PreferenceHelper -> za3), nên đếm
# "com.app.plugin" trên log thô không thấy SDK. Cổng phải retrace bằng mapping rồi mới đếm, in cả số raw,
# và FAIL khi cần retrace mà thiếu mapping / java / r8.jar hay retrace lỗi.
# Ba vi phạm (định dạng logcat, tên R8): (1) SDK nạp prefs (za3 = PreferenceHelper); (2) super.onCreate()
# của Application (Typeface) gọi từ yb2 = AppPluginBase — có frame SDK nhưng không do SDK; (3) app demo.

# count_strict trong một subshell như cổng; $1 = mapping, $2 = app log; JAVA/R8_JAR/RETRACE qua env.
run_strict() {
  ( LOG=$TMP/strict-gate.log; fail=0; . "$LIB" || exit 99
    count_strict "cold start" "$2" "$1"
    echo "RESULT: $([ "$fail" -eq 0 ] && echo PASS || echo FAIL)"; exit "$fail" ) 2>&1
}
# Task B (1.4.0): cổng đòi MỌI vi phạm do SDK = 0, kể cả lần nạp SharedPreferences đầu (bỏ ngoại lệ DOC/65 §7 #9
# áp tạm của Task 17); số prefs first load vẫn in để theo dõi.
want='strictmode cold start: violations=3, with sdk frame=2, by sdk=1 (prefs first load=1, other=0; retraced'
out=$(JAVA=$FAKE_JAVA R8_JAR=$FAKE_R8_JAR run_strict "$STRICT_MAP" "$STRICT_LOG"); rc=$?
check "strict, retrace (java giả): 3 vi phạm, 2 có frame SDK, 1 do SDK (trừ super.onCreate)" \
  "$(has "$out" "$want"; echo $?)" "$out"
check "strict, vi phạm do SDK là lần nạp prefs đầu (getSharedPreferences): FAIL, không còn trừ (§7 #9)" \
  "$(has "$out" 'strictmode gate cold start: by sdk=1 (need 0' && has "$out" 'FAIL: StrictMode: 1 vi phạm do SDK (prefs first load=1, other=0' && has "$out" 'RESULT: FAIL' && [ "$rc" -eq 1 ]; echo $?)" "$out"
check "strict: vẫn in số raw trước retrace (tên R8: raw chỉ thấy frame demo)" \
  "$(has "$out" 'strictmode frames com.app.plugin=3 (raw, before retrace; demo=3)'; echo $?)" "$out"
check "strict: \$LOG có stack đã retrace của vi phạm do SDK" \
  "$(has "$(cat "$TMP/strict-gate.log")" '  strict> 	at com.app.plugin.until.PreferenceHelper.'; echo $?)" "$(cat "$TMP/strict-gate.log")"
want_other='strictmode cold start: violations=4, with sdk frame=3, by sdk=2 (prefs first load=1, other=1; retraced'
out=$(JAVA=$FAKE_JAVA R8_JAR=$FAKE_R8_JAR run_strict "$STRICT_MAP" "$STRICT_LOG_OTHER"); rc=$?
check "strict, thêm vi phạm do SDK không phải lần nạp prefs đầu (File.exists): FAIL, đếm cả hai" \
  "$(has "$out" "$want_other" && has "$out" 'FAIL: StrictMode: 2 vi phạm do SDK (prefs first load=1, other=1' && has "$out" 'RESULT: FAIL' && [ "$rc" -eq 1 ]; echo $?)" "$out"
check "strict: \$LOG đánh dấu stack do SDK theo loại (prefs / other)" \
  "$(has "$(cat "$TMP/strict-gate.log")" '  strict> [other] StrictMode policy violation' && has "$(cat "$TMP/strict-gate.log")" '  strict> [prefs first load] StrictMode policy violation'; echo $?)" "$(cat "$TMP/strict-gate.log")"
want_clean='strictmode cold start: violations=2, with sdk frame=1, by sdk=0 (prefs first load=0, other=0; retraced'
out=$(JAVA=$FAKE_JAVA R8_JAR=$FAKE_R8_JAR run_strict "$STRICT_MAP" "$STRICT_LOG_CLEAN"); rc=$?
check "strict, không vi phạm nào do SDK (chỉ super.onCreate và app demo): by sdk=0, PASS" \
  "$(has "$out" "$want_clean" && has "$out" 'strictmode gate cold start: by sdk=0 (need 0' && has "$out" 'RESULT: PASS' && hasnt "$out" 'FAIL:' && [ "$rc" -eq 0 ]; echo $?)" "$out"
out=$(JAVA=$FAKE_JAVA R8_JAR=$FAKE_R8_JAR run_strict "$TMP/khong-co-mapping.txt" "$STRICT_LOG"); rc=$?
check "strict, cần retrace mà thiếu mapping: FAIL, không in số sau retrace" \
  "$(has "$out" 'FAIL: StrictMode cần retrace nhưng không có mapping' && has "$out" 'RESULT: FAIL' && hasnt "$out" 'by sdk=' && [ "$rc" -eq 1 ]; echo $?)" "$out"
out=$(JAVA=$TMP/khong-co-java R8_JAR=$FAKE_R8_JAR run_strict "$STRICT_MAP" "$STRICT_LOG"); rc=$?
check "strict, cần retrace mà không có java: FAIL, message nhắc RETRACE=off" \
  "$(has "$out" 'FAIL: StrictMode cần retrace nhưng không có java/r8.jar' && has "$out" 'RETRACE=off' && has "$out" 'RESULT: FAIL'; echo $?)" "$out"
out=$(JAVA=$FAKE_JAVA R8_JAR=$TMP/khong-co-r8.jar run_strict "$STRICT_MAP" "$STRICT_LOG"); rc=$?
check "strict, cần retrace mà không có r8.jar: FAIL" \
  "$(has "$out" 'FAIL: StrictMode cần retrace nhưng không có java/r8.jar' && has "$out" 'RESULT: FAIL'; echo $?)" "$out"
out=$(FAKE_RETRACE_FAIL=1 JAVA=$FAKE_JAVA R8_JAR=$FAKE_R8_JAR run_strict "$STRICT_MAP" "$STRICT_LOG"); rc=$?
check "strict, retrace lỗi (exit 3): FAIL kèm lỗi của retrace" \
  "$(has "$out" 'FAIL: retrace StrictMode lỗi (exit=3' && has "$out" 'retrace boom' && has "$out" 'RESULT: FAIL'; echo $?)" "$out"
out=$(JAVA=$FAKE_JAVA R8_JAR=$FAKE_R8_JAR run_strict "$TMP/khong-co-mapping.txt" "$APPLOG_OK"); rc=$?
check "strict, không có dòng StrictMode: 0 vi phạm, không cần mapping, PASS" \
  "$(has "$out" 'strictmode cold start: violations=0' && has "$out" 'RESULT: PASS'; echo $?)" "$out"
out=$(RETRACE=off JAVA=$FAKE_JAVA R8_JAR=$FAKE_R8_JAR run_strict "$TMP/khong-co-mapping.txt" "$STRICT_LOG"); rc=$?
check "strict, RETRACE=off: chỉ số raw, ghi rõ là chưa retrace, không FAIL" \
  "$(has "$out" 'RETRACE=off' && has "$out" 'RESULT: PASS' && hasnt "$out" 'by sdk='; echo $?)" "$out"
# Retrace thật của R8 (JDK + r8.jar của Android Studio) khi máy có: cùng số với java giả.
AS="/Applications/Android Studio.app/Contents"
if [ -x "$AS/jbr/Contents/Home/bin/java" ] && [ -f "$AS/plugins/android/lib/r8.jar" ]; then
  out=$(JAVA= R8_JAR= run_strict "$STRICT_MAP" "$STRICT_LOG"); rc=$?
  check "strict, retrace thật (java + r8.jar của Android Studio): cùng số, cùng FAIL" \
    "$(has "$out" "$want" && has "$out" 'RESULT: FAIL'; echo $?)" "$out"
else
  echo "skip   - strict, retrace thật: máy không có Android Studio (java giả đã phủ phần logic)"
fi
# Cổng thật với adb giả: log cold start có StrictMode.
prepare_gate "$TMP/good.txt"
rm -f "$GATE_ROOT/apk/mapping.txt"
out=$(FAKE_ADB_FULL=1 FAKE_COLD_LOG=$STRICT_LOG FAKE_JAVA=$FAKE_JAVA FAKE_R8_JAR=$FAKE_R8_JAR run_gate)
check "cổng, có StrictMode mà thiếu mapping: FAIL (fail closed), không PASS" \
  "$(has "$out" 'FAIL: --mapping' && has "$out" 'RESULT: FAIL' && hasnt "$out" 'RESULT: PASS' && [ ! -s "$TMP/adb-calls" ]; echo $?)" "$out"
prepare_gate "$TMP/good.txt"
cp "$STRICT_MAP" "$GATE_ROOT/apk/mapping.txt"
out=$(FAKE_ADB_FULL=1 FAKE_COLD_LOG=$STRICT_LOG_CLEAN FAKE_JAVA=$FAKE_JAVA FAKE_R8_JAR=$FAKE_R8_JAR run_gate)
check "cổng, có mapping, không vi phạm do SDK: in số sau retrace vào stdout và \$LOG, PASS" \
  "$(has "$out" "$want_clean" && has "$(gate_log)" "$want_clean" && has "$out" 'RESULT: PASS'; echo $?)" "$out"
prepare_gate "$TMP/good.txt"
cp "$STRICT_MAP" "$GATE_ROOT/apk/mapping.txt"
out=$(FAKE_ADB_FULL=1 FAKE_COLD_LOG=$STRICT_LOG FAKE_JAVA=$FAKE_JAVA FAKE_R8_JAR=$FAKE_R8_JAR run_gate)
check "cổng, vi phạm do SDK là lần nạp prefs đầu: RESULT: FAIL, lý do vào \$LOG" \
  "$(has "$out" "$want" && has "$(gate_log)" 'FAIL: StrictMode: 1 vi phạm do SDK' && has "$out" 'RESULT: FAIL' && hasnt "$out" 'RESULT: PASS'; echo $?)" "$out"
prepare_gate "$TMP/good.txt"
cp "$STRICT_MAP" "$GATE_ROOT/apk/mapping.txt"
out=$(FAKE_ADB_FULL=1 FAKE_COLD_LOG=$STRICT_LOG_OTHER FAKE_JAVA=$FAKE_JAVA FAKE_R8_JAR=$FAKE_R8_JAR run_gate)
check "cổng, thêm vi phạm do SDK ngoài lần nạp prefs đầu: RESULT: FAIL" \
  "$(has "$out" "$want_other" && has "$out" 'RESULT: FAIL' && hasnt "$out" 'RESULT: PASS'; echo $?)" "$out"

# ── 9. count_af_started (1.6.1 Task 3): chặng cold start 2 (non-EEA) đo được đường af.init không ──
# $1 = app log; AF_STARTED_MIN qua env. count_af_started trong subshell như cổng.
AF_LOG_YES=$TMP/af-yes.log
cat > "$AF_LOG_YES" <<'LOGEOF'
09-24 21:00:00.000  5252  5252 I test.sdk.zd: appsflyer started after consent, tier=EEA, start=activity
09-24 21:00:00.100  5252  5252 I test.sdk.zd: appsflyer launch request failed: invalid app id
LOGEOF
AF_LOG_NO=$TMP/af-no.log
cat > "$AF_LOG_NO" <<'LOGEOF'
09-24 21:00:00.000  5252  5252 I test.sdk.zd: ok
LOGEOF
run_af_started() {
  ( LOG=$TMP/af-gate.log; fail=0; . "$LIB" || exit 99
    count_af_started "cold start 2" "$1"
    echo "RESULT: $([ "$fail" -eq 0 ] && echo PASS || echo FAIL)"; exit "$fail" ) 2>&1
}
out=$(run_af_started "$AF_LOG_YES"); rc=$?
check "af started, 1 dòng 'appsflyer started after consent': n=1, PASS" \
  "$(has "$out" 'appsflyer started cold start 2: n=1 (need >= 1' && has "$out" 'RESULT: PASS' && [ "$rc" -eq 0 ]; echo $?)" "$out"
out=$(run_af_started "$AF_LOG_NO"); rc=$?
check "af started, không có dòng: FAIL: AppsFlyer không start, rc=1" \
  "$(has "$out" 'FAIL: AppsFlyer không start' && has "$out" 'RESULT: FAIL' && [ "$rc" -eq 1 ]; echo $?)" "$out"
out=$(AF_STARTED_MIN=0 run_af_started "$AF_LOG_NO"); rc=$?
check "af started, không có dòng nhưng AF_STARTED_MIN=0: PASS" \
  "$(has "$out" 'appsflyer started cold start 2: n=0 (need >= 0' && has "$out" 'RESULT: PASS' && [ "$rc" -eq 0 ]; echo $?)" "$out"

# ── 10. Allowlist AppsFlyer LVL tuỳ chọn (1.6.1 Task 3, review controller): bằng chứng — AAR
# af-android-sdk-6.16.2 (`unzip -l classes.jar | grep -i lvl`) KHÔNG có gói `com.appsflyer.lvl.*`
# (module LVL là artifact riêng, build này không khai báo). `AFf1lSDK$2` của AppsFlyer dò lớp đó lúc
# init, ART log "Rejecting re-init on previously-failed class" ở mức I, không crash — không phải R8
# strip (lớp chưa từng có trong input) mà là AppsFlyer tự bắt module tuỳ chọn vắng mặt. Regex khớp cả
# hai dạng logcat: JVM descriptor `Lcom/appsflyer/lvl/AppsFlyerLVL...;` và dạng chấm
# `com.appsflyer.lvl.AppsFlyerLVL...` trong `ClassNotFoundException`. Bằng chứng: DOC/testing/t161-t3-gate-red-api28.log.
# Dùng đúng allowlist thật của repo (tools/device_gate_allowlist.txt) để không lệch với cổng thật.
LVL_LOG=$TMP/lvl.log
cat > "$LVL_LOG" <<'LOGEOF'
09-28 21:45:47.232  4542  4542 I test.sdk.zd: Rejecting re-init on previously-failed class java.lang.Class<com.appsflyer.internal.AFf1lSDK$2>: java.lang.NoClassDefFoundError: Failed resolution of: Lcom/appsflyer/lvl/AppsFlyerLVL$resultListener;
09-28 21:45:47.232  4542  4542 I test.sdk.zd: Caused by: java.lang.ClassNotFoundException: Didn't find class "com.appsflyer.lvl.AppsFlyerLVL$resultListener" on path: DexPathList[[zip file "/data/app/test.sdk.zd/base.apk"]]
LOGEOF
# Lớp AppsFlyer KHÁC (không phải LVL): phải vẫn bị đếm — allowlist không được rộng quá gói appsflyer.
OTHER_AF_LOG=$TMP/other-af.log
cat > "$OTHER_AF_LOG" <<'LOGEOF'
09-28 21:50:00.000  4542  4542 W test.sdk.zd: java.lang.NoClassDefFoundError: Failed resolution of: Lcom/appsflyer/internal/AFc1oSDK;
LOGEOF
# Lớp của chính SDK (com.app.plugin.*) thiếu: không bao giờ được allowlist — đó là lỗi R8 thật.
APP_PLUGIN_LOG=$TMP/app-plugin.log
cat > "$APP_PLUGIN_LOG" <<'LOGEOF'
09-28 21:51:00.000  4542  4542 W test.sdk.zd: java.lang.ClassNotFoundException: Didn't find class "com.app.plugin.until.PreferenceHelper" on path: DexPathList[[zip file "/data/app/test.sdk.zd/base.apk"]]
LOGEOF
out=$(run_lib "$ROOT/tools/device_gate_allowlist.txt" "$LVL_LOG" "$TMP/lvl-gate.log")
check "(a) LVL: cả 2 dòng (NoClassDef dạng /, ClassNotFound dạng .) đều allowlisted, r8=0, PASS" \
  "$(has "$out" 'r8 strings fixture=0 (raw=2, allowlisted=2; need 0)' && has "$out" 'RESULT: PASS'; echo $?)" "$out"
out=$(run_lib "$ROOT/tools/device_gate_allowlist.txt" "$OTHER_AF_LOG" "$TMP/other-af-gate.log")
check "(b) NoClassDefFoundError của lớp appsflyer KHÁC (không phải lvl): vẫn bị đếm, FAIL" \
  "$(has "$out" 'r8 strings fixture=1 (raw=1, allowlisted=0; need 0)' && has "$out" 'RESULT: FAIL'; echo $?)" "$out"
out=$(run_lib "$ROOT/tools/device_gate_allowlist.txt" "$APP_PLUGIN_LOG" "$TMP/app-plugin-gate.log")
check "(c) ClassNotFoundException của lớp com.app.plugin.* (SDK mình): vẫn bị đếm, FAIL" \
  "$(has "$out" 'r8 strings fixture=1 (raw=1, allowlisted=0; need 0)' && has "$out" 'RESULT: FAIL'; echo $?)" "$out"

# 1.6.3 Task 3 (review cuối 1.6.2 M3): LVL neo lớp chủ AFf1lSDK$2 và đúng lớp thiếu AppsFlyerLVL$resultListener.
lvl_case() { # $1 = tên fixture, $2.. = nội dung các dòng
  local f=$TMP/lvl-$1.log; shift; printf '%s\n' "$@" > "$f"; run_lib "$ROOT/tools/device_gate_allowlist.txt" "$f" "$TMP/lvl-neg.log"
}
LVL_PRE='09-29 10:00:00.000  4542  4542 I test.sdk.zd: Rejecting re-init on previously-failed class java.lang.Class<'
LVL_MISS=': java.lang.NoClassDefFoundError: Failed resolution of: Lcom/appsflyer/lvl/AppsFlyerLVL$resultListener;'
out=$(lvl_case d "${LVL_PRE}com.appsflyer.internal.AFf1lSDK\$3>${LVL_MISS}")
check "(d) lớp chủ AFf1lSDK\$3 (không phải \$2) thiếu LVL: vẫn bị đếm, FAIL" \
  "$(has "$out" 'r8 strings fixture=1 (raw=1, allowlisted=0; need 0)' && has "$out" 'RESULT: FAIL'; echo $?)" "$out"
out=$(lvl_case e "${LVL_PRE}com.app.plugin.ads.Foo>${LVL_MISS}" \
  '09-29 10:00:00.001  4542  4542 I test.sdk.zd: Caused by: java.lang.ClassNotFoundException: Didn'"'"'t find class "com.appsflyer.lvl.AppsFlyerLVL$resultListener" on path: DexPathList[[]]')
check "(e) lớp chủ com.app.plugin.ads.Foo thiếu LVL: Rejecting vẫn đếm (Caused by được loại), FAIL" \
  "$(has "$out" 'r8 strings fixture=1 (raw=2, allowlisted=1; need 0)' && has "$out" 'RESULT: FAIL'; echo $?)" "$out"
out=$(lvl_case f "${LVL_PRE}com.appsflyer.internal.AFf1lSDK\$2>: java.lang.NoClassDefFoundError: Failed resolution of: Lcom/appsflyer/lvl/AppsFlyerLVL\$resultListenerImpl;")
check "(f) lớp thiếu AppsFlyerLVL\$resultListenerImpl; chủ AFf1lSDK\$2: FAIL (ghim dấu ;)" \
  "$(has "$out" 'r8 strings fixture=1 (raw=1, allowlisted=0; need 0)' && has "$out" 'RESULT: FAIL'; echo $?)" "$out"
out=$(lvl_case g '09-29 10:00:00.001  4542  4542 I test.sdk.zd: Caused by: java.lang.ClassNotFoundException: Didn'"'"'t find class "com.appsflyer.lvl.AppsFlyerLVL$resultListenerImpl" on path: DexPathList[[]]')
check "(g) Caused by … resultListenerImpl\" on path: FAIL (ghim dấu \")" \
  "$(has "$out" 'r8 strings fixture=1 (raw=1, allowlisted=0; need 0)' && has "$out" 'RESULT: FAIL'; echo $?)" "$out"
out=$(lvl_case i "${LVL_PRE}com.appsflyer.internal.AFf1lSDK\$20>${LVL_MISS}")
check "(i) lớp chủ AFf1lSDK\$20 (chỉ khác hậu tố): FAIL (ghim > sau \$2)" \
  "$(has "$out" 'r8 strings fixture=1 (raw=1, allowlisted=0; need 0)' && has "$out" 'RESULT: FAIL'; echo $?)" "$out"
out=$(lvl_case j "${LVL_PRE}com.app.plugin.x.AFf1lSDK\$2>${LVL_MISS}")
check "(j) lớp chủ com.app.plugin.x.AFf1lSDK\$2 (sai gói): FAIL (ghim tiền tố Class<com.appsflyer.internal.)" \
  "$(has "$out" 'r8 strings fixture=1 (raw=1, allowlisted=0; need 0)' && has "$out" 'RESULT: FAIL'; echo $?)" "$out"
out=$(lvl_case k "${LVL_PRE}com.appsflyer.internal.AFf1lSDK\$2\$1>${LVL_MISS}")
check "(k) lớp chủ AFf1lSDK\$2\$1 (lớp lồng): FAIL (ghim > ngay sau \$2)" \
  "$(has "$out" 'r8 strings fixture=1 (raw=1, allowlisted=0; need 0)' && has "$out" 'RESULT: FAIL'; echo $?)" "$out"
# (h) chính sách, không phụ thuộc cách viết: mọi regex không-chú-thích nhắc lvl (không phân biệt hoa thường) phải là đúng 2 dạng neo.
lvl_bad=$(grep -v '^#' "$ROOT/tools/device_gate_allowlist.txt" | command grep -i 'lvl' \
  | command grep -vF -e 'Rejecting re-init on previously-failed class java\.lang\.Class<com\.appsflyer\.internal\.AFf1lSDK\$2>: ' -e 'Caused by: java\.lang\.ClassNotFoundException: Didn'"'"'t find class "com\.appsflyer\.lvl\.AppsFlyerLVL\$resultListener" on path' || true)
check "(h) chính sách: mọi regex nhắc lvl (mọi cách viết) neo AFf1lSDK\$2> hoặc là dạng Caused by đúng tên lớp" \
  "$([ -z "$lvl_bad" ]; echo $?)" "$lvl_bad"

# ── 11. Chặng 2 phải THẬT SỰ được nối vào cổng, không chỉ tồn tại trong file (review I2) ───────────
# M3/M4 (xoá lời gọi count_strict/count_af_started của chặng 2 trong tools/device_gate.sh) sống sót
# qua mọi check ở trên vì không check nào của cổng giả bơm StrictMode hay tắt dòng af-started RIÊNG
# cho chặng 2. Ba check dưới đây kiểm đúng việc nối dây đó, không kiểm lại logic count_strict/
# count_af_started (đã có ở mục 8/9).
# (a) StrictMode do SDK bơm riêng cho chặng 2 (chặng 1 sạch) phải làm chặng 2 báo by sdk=1 và cổng FAIL.
prepare_gate "$TMP/good.txt"
cp "$STRICT_MAP" "$GATE_ROOT/apk/mapping.txt"
out=$(FAKE_ADB_FULL=1 FAKE_COLD_LOG=$STRICT_LOG_CLEAN FAKE_COLD2_LOG=$STRICT_LOG \
      FAKE_JAVA=$FAKE_JAVA FAKE_R8_JAR=$FAKE_R8_JAR run_gate)
check "(I2a) chặng 2 có StrictMode do SDK (chặng 1 sạch): by sdk=1, RESULT: FAIL" \
  "$(has "$out" 'strictmode gate cold start 2 (non-EEA, appsflyer): by sdk=1' && has "$out" 'RESULT: FAIL'; echo $?)" "$out"
# (b) Log chặng 2 không rỗng (không trùng với check "log rỗng" ở mục 7) nhưng thiếu đúng dòng
# "appsflyer started after consent": count_af_started của chặng 2 phải bắt được, không được PASS giả.
prepare_gate "$TMP/good.txt"
out=$(FAKE_ADB_FULL=1 FAKE_AF_STARTED=no FAKE_COLD2_LOG=$APPLOG_OK run_gate)
check "(I2b) chặng 2 thiếu dòng af started (log không rỗng): FAIL: AppsFlyer không start, RESULT: FAIL" \
  "$(has "$out" 'FAIL: AppsFlyer không start trong chặng cold start 2' && has "$out" 'RESULT: FAIL'; echo $?)" "$out"
# (c) "am start -W" đầu tiên SAU "pm clear" (giữa hai lệnh chỉ có 3 lệnh logcat -c benign) phải KHÔNG
# có --ez force_eea (bắt cả M2: thêm force_eea vào chặng 2 làm chặng 2 hết còn là "non-EEA").
prepare_gate "$TMP/good.txt"
out=$(FAKE_ADB_FULL=1 run_gate)
start2_call=$(awk '/^shell pm clear /{f=1} f && /^shell am start -W/{print; exit}' "$TMP/adb-calls")
check "(I2c) am start -W đầu tiên sau pm clear không có force_eea" \
  "$([ "$start2_call" = "shell am start -W -n test.sdk.zd/com.app.plugin.demo.MainActivity" ]; echo $?)" \
  "am start -W sau pm clear: [$start2_call]
adb calls:
$(cat "$TMP/adb-calls")"

# ── 12. 1.6.1 G1: dòng lạ trên API 28/26 sau khi AppsFlyer chạy trong cổng (Task R) ────────────────
# Dòng lấy nguyên văn từ DOC/testing/t161-r-emu26-gate.log và t161-r-emu28-gate.log (2026-09-28).
# (a) AppsFlyer dò Xiaomi GetApps referrer (`com.miui.referrer.api.*`): không có trong AAR 6.16.2 lẫn
# releaseRuntimeClasspath; AFj1sSDK dò bằng Class.forName trong try/catch trước khi dùng. Phải allowlisted.
MIUI_LOG=$TMP/miui.log
cat > "$MIUI_LOG" <<'LOGEOF'
09-28 22:54:40.760  3596  3596 I zygote64: Rejecting re-init on previously-failed class java.lang.Class<com.appsflyer.internal.AFj1sSDK$2>: java.lang.NoClassDefFoundError: Failed resolution of: Lcom/miui/referrer/api/GetAppsReferrerStateListener;
09-28 22:54:40.760  3596  3596 I zygote64: Caused by: java.lang.ClassNotFoundException: Didn't find class "com.miui.referrer.api.GetAppsReferrerStateListener" on path: DexPathList[[zip file "/data/app/test.sdk.zd-2eKFBspA2DLH6NWRTMp29g==/base.apk"],nativeLibraryDirectories=[/data/app/test.sdk.zd-2eKFBspA2DLH6NWRTMp29g==/lib/arm64, /data/app/test.sdk.zd-2eKFBspA2DLH6NWRTMp29g==/base.apk!/lib/arm64-v8a, /system/lib64, /vendor/lib64]]
LOGEOF
# (b) Cảnh báo tranh chấp khoá của ART: không phải lỗi nạp lớp, chỉ chứa chữ ClassNotFoundException vì
# frame của luồng giữ khoá là constructor của exception. Từ 1.6.2 Task 3 KHÔNG còn allowlist: 0 dòng khớp trong mọi
# log cổng sau fix ClassValue; nó quay lại = có lớp thiếu thật đang giữ khoá, phải bị đếm và cổng FAIL.
CONT_LOG=$TMP/contention.log
cat > "$CONT_LOG" <<'LOGEOF'
09-28 22:37:09.441  3271  4438 W test.sdk.zd: Long monitor contention with owner queue-1-4 (4539) at void java.lang.ClassNotFoundException.<init>(java.lang.String)(ClassNotFoundException.java:82) waiters=0 in java.lang.Class lc.l() for 147ms
LOGEOF
out=$(run_lib "$ROOT/tools/device_gate_allowlist.txt" "$MIUI_LOG" "$TMP/miui-gate.log")
check "(G1a) MIUI referrer: 2 dòng (NoClassDef dạng /, ClassNotFound dạng .) allowlisted, r8=0, PASS" \
  "$(has "$out" 'r8 strings fixture=0 (raw=2, allowlisted=2; need 0)' && has "$out" 'RESULT: PASS'; echo $?)" "$out"
out=$(run_lib "$ROOT/tools/device_gate_allowlist.txt" "$CONT_LOG" "$TMP/contention-gate.log")
check "(G1b) Long monitor contention, chủ khoá ở ClassNotFoundException.<init>: bị đếm, FAIL (đã gỡ khỏi allowlist)" \
  "$(has "$out" 'r8 strings fixture=1 (raw=1, allowlisted=0; need 0)' && has "$out" 'RESULT: FAIL'; echo $?)" "$out"
check "(G1c) allowlist thật không còn dòng nhắc monitor contention (chính sách header)" \
  "$([ "$(grep -v '^#' "$ROOT/tools/device_gate_allowlist.txt" | grep -ci 'monitor contention')" -eq 0 ]; echo $?)" \
  "$(grep -v '^#' "$ROOT/tools/device_gate_allowlist.txt" | grep -i 'monitor contention')"
# Ca âm: regex mới không được nuốt lỗi R8 thật. Mỗi dòng dưới đây phải vẫn bị đếm (r8=1, FAIL).
# - lớp của chính SDK vắng (dạng chấm và dạng descriptor);
# - lớp com.miui KHÁC gói referrer, và AFj1sSDK$2 của AppsFlyer thiếu một lớp KHÔNG phải MIUI referrer;
# - "Long monitor contention" mà chủ khoá KHÔNG ở ClassNotFoundException.<init> (có chữ NoClassDefFoundError);
# - lớp chủ KHÁC AFj1sSDK$2 thiếu listener MIUI (regex MIUI neo lớp chủ, 1.6.2 Task 3);
# - đúng chủ AFj1sSDK$2 nhưng tên có hậu tố sau listener (…ListenerImpl;): ghim dấu `;` cuối regex (1.6.2 F2);
# - ClassValue nguyên văn 2026-09-28 (API 28 và 26): G1 KHÔNG allowlist — lớp gộp của R8 (lc chứa outline
#   API của ClassValue.get từ kotlinx, i70 = ClassValueReferences), cùng họ lỗi 109926d đã sửa; xem
#   .superpowers/sdd/v161/g1-report.md.
i=0
while IFS= read -r line; do
  i=$((i + 1)); f=$TMP/g1-neg-$i.log
  printf '%s\n' "$line" > "$f"
  out=$(run_lib "$ROOT/tools/device_gate_allowlist.txt" "$f" "$TMP/g1-neg-$i-gate.log")
  check "(G1 âm $i) vẫn bị đếm, FAIL: ${line:0:110}" \
    "$(has "$out" 'r8 strings fixture=1 (raw=1, allowlisted=0; need 0)' && has "$out" 'RESULT: FAIL'; echo $?)" "$out"
done <<'LOGEOF'
09-28 23:00:00.000  4242  4242 W test.sdk.zd: java.lang.ClassNotFoundException: Didn't find class "com.app.plugin.x.Y" on path: DexPathList[[zip file "/data/app/test.sdk.zd/base.apk"]]
09-28 23:00:00.001  4242  4242 W test.sdk.zd: java.lang.NoClassDefFoundError: Failed resolution of: Lcom/app/plugin/Foo;
09-28 23:00:00.002  4242  4242 I zygote64: Rejecting re-init on previously-failed class java.lang.Class<com.miui.other.Bar>: java.lang.NoClassDefFoundError: Failed resolution of: Lcom/miui/other/Bar;
09-28 23:00:00.003  4242  4242 I zygote64: Rejecting re-init on previously-failed class java.lang.Class<com.appsflyer.internal.AFj1sSDK$2>: java.lang.NoClassDefFoundError: Failed resolution of: Lcom/app/plugin/Foo;
09-28 23:00:00.004  4242  4243 W test.sdk.zd: Long monitor contention with owner main (4242) at void java.lang.NoClassDefFoundError.<init>(java.lang.String)(NoClassDefFoundError.java:59) waiters=0 in java.lang.Class com.app.plugin.Foo.bar() for 120ms
09-28 23:00:00.005  4242  4242 I zygote64: Rejecting re-init on previously-failed class java.lang.Class<com.app.plugin.Foo>: java.lang.NoClassDefFoundError: Failed resolution of: Lcom/miui/referrer/api/GetAppsReferrerStateListener;
09-28 23:00:00.006  4242  4242 I zygote64: Rejecting re-init on previously-failed class java.lang.Class<com.appsflyer.internal.AFj1sSDK$3>: java.lang.NoClassDefFoundError: Failed resolution of: Lcom/miui/referrer/api/GetAppsReferrerStateListener;
09-28 23:00:00.007  4242  4242 I zygote64: Rejecting re-init on previously-failed class java.lang.Class<com.appsflyer.internal.AFj1sSDK$2>: java.lang.NoClassDefFoundError: Failed resolution of: Lcom/miui/referrer/api/GetAppsReferrerStateListenerImpl;
09-28 22:37:08.927  3271  4539 I test.sdk.zd: Rejecting re-init on previously-failed class java.lang.Class<i70>: java.lang.NoClassDefFoundError: Failed resolution of: Ljava/lang/ClassValue;
09-28 22:37:08.927  3271  4539 I test.sdk.zd: Caused by: java.lang.ClassNotFoundException: Didn't find class "java.lang.ClassValue" on path: DexPathList[[zip file "/data/app/test.sdk.zd-vBc5vIE16qFd5-a3A1Y8Dw==/base.apk"],nativeLibraryDirectories=[/data/app/test.sdk.zd-vBc5vIE16qFd5-a3A1Y8Dw==/lib/arm64, /data/app/test.sdk.zd-vBc5vIE16qFd5-a3A1Y8Dw==/base.apk!/lib/arm64-v8a, /system/lib64]]
09-28 22:55:35.480  3596  3991 I zygote64: Rejecting re-init on previously-failed class java.lang.Class<i70>: java.lang.NoClassDefFoundError: Failed resolution of: Ljava/lang/ClassValue;
09-28 22:55:35.480  3596  3991 I zygote64: Caused by: java.lang.ClassNotFoundException: Didn't find class "java.lang.ClassValue" on path: DexPathList[[zip file "/data/app/test.sdk.zd-2eKFBspA2DLH6NWRTMp29g==/base.apk"],nativeLibraryDirectories=[/data/app/test.sdk.zd-2eKFBspA2DLH6NWRTMp29g==/lib/arm64, /data/app/test.sdk.zd-2eKFBspA2DLH6NWRTMp29g==/base.apk!/lib/arm64-v8a, /system/lib64, /vendor/lib64]]
LOGEOF
# Dòng "Long monitor contention" không có chuỗi R8 nào: count_r8 không đếm (raw=0), regex mới không đổi gì.
f=$TMP/g1-cont-plain.log
printf '%s\n' '09-28 23:00:00.006  4242  4243 W test.sdk.zd: Long monitor contention with owner main (4242) at void android.os.MessageQueue.nativePollOnce(long, int)(MessageQueue.java:-2) waiters=0 in void com.app.plugin.Foo.bar() for 120ms' > "$f"
out=$(run_lib "$ROOT/tools/device_gate_allowlist.txt" "$f" "$TMP/g1-cont-plain-gate.log")
re=$( (LOG=/dev/null; fail=0; . "$LIB"; load_allowlist "$ROOT/tools/device_gate_allowlist.txt"; printf '%s' "$ALLOW_RE") )
check "(G1 âm) Long monitor contention không nhắc ClassNotFoundException: raw=0, allowlist thật không khớp" \
  "$(has "$out" 'r8 strings fixture=0 (raw=0, allowlisted=0; need 0)' && [ -n "$re" ] && ! grep -qE -e "$re" "$f"; echo $?)" "$out
ALLOW_RE=$re"

# ── 13. 1.6.2 Task 2: cổng giữ bằng chứng (log thô, dòng số đo, canary, pm clear, crash trước chặng 2, tải) ──
prepare_gate "$TMP/good.txt"
out=$(FAKE_ADB_FULL=1 run_gate)
D=$GATE_ROOT/DOC/testing
ok=0
for k in cold1 post cold2; do
  f=$(ls "$D"/device-gate-app-stub-serial-*-$k.log 2>/dev/null | head -1)
  [ -n "$f" ] && [ -s "$f" ] || ok=1
done
ls "$D"/device-gate-app-stub-serial-*-crash.log > /dev/null 2>&1 || ok=1
check "(T2a) sau khi cổng thoát còn 3 log thô không rỗng + file crash" "$ok" "$out
$(ls "$D")"
check "(T2b) \$LOG có key[cold start]> strictmode armed và dòng metrics cold start" \
  "$(has "$(gate_log)" '  key[cold start]> ' && grep -q 'key\[cold start\]> .*strictmode armed' <<< "$(gate_log)" \
    && has "$(gate_log)" 'metrics cold start: prefs warmup waited=3 ms, timeouts=0, onCreate main-thread ms=31, optional prefs ms=9'; echo $?)" "$(gate_log)"
check "(T2e) chặng 2 nối dây strictmode armed: n=1 và PASS" \
  "$(has "$out" 'strictmode armed cold start 2: n=1' && has "$out" 'RESULT: PASS'; echo $?)" "$out"
t2f_default=$(gate_log)
check "(T2h) 3 dòng load và dòng gate log" \
  "$([ "$(gate_log | grep -c '^load .*host=.*device=0.50')" -eq 3 ] && has "$out" 'gate log: DOC/testing/device-gate-stub-serial-'; echo $?)" "$out
$(gate_log)"
prepare_gate "$TMP/good.txt"
out=$(FAKE_ADB_FULL=1 FAKE_ARMED=no run_gate)
check "(T2c) không có strictmode armed: FAIL StrictMode chưa bật + RESULT: FAIL" \
  "$(has "$out" 'FAIL: StrictMode chưa bật trong chặng cold start' && has "$out" 'RESULT: FAIL'; echo $?)" "$out"
prepare_gate "$TMP/good.txt"
out=$(FAKE_ADB_FULL=1 FAKE_ARMED=no STRICT_ARMED_MIN=0 run_gate)
check "(T2d) STRICT_ARMED_MIN=0 bỏ kiểm canary: n=0 (need >= 0) và PASS" \
  "$(has "$out" 'strictmode armed cold start: n=0 (need >= 0)' && has "$out" 'RESULT: PASS'; echo $?)" "$out"
prepare_gate "$TMP/good.txt"
out=$(FAKE_ADB_FULL=1 FAKE_PM_CLEAR=Error run_gate)
check "(T2f) pm clear: ca mặc định ghi Success vào \$LOG; Error thì FAIL + RESULT: FAIL" \
  "$(has "$t2f_default" 'pm clear test.sdk.zd: Success' && has "$out" 'FAIL: pm clear test.sdk.zd không Success: Error' \
    && has "$out" 'RESULT: FAIL'; echo $?)" "$out
default: $t2f_default"
prepare_gate "$TMP/good.txt"
out=$(FAKE_ADB_FULL=1 FAKE_CRASH_POST=1 run_gate)
check "(T2g) crash giữa seed cuối và chặng 2 được đếm: [post-monkey] FATAL=1 + FAIL" \
  "$(has "$out" '[post-monkey] FATAL=1' && has "$out" 'RESULT: FAIL'; echo $?)" "$out"

# ── 14. Review R-B (1.6.2 T2 I1/m1/m2/m4) ─────────────────────────────────────────────────────────────
prepare_gate "$TMP/good.txt"
out=$(FAKE_ADB_FULL=1 FAKE_ARMED=cold2no run_gate)
check "(I1) chỉ chặng 2 thiếu strictmode armed: FAIL chặng cold start 2, RESULT: FAIL, chặng 1 vẫn n=1" \
  "$(has "$out" 'FAIL: StrictMode chưa bật trong chặng cold start 2' && has "$out" 'strictmode armed cold start: n=1' \
    && has "$out" 'RESULT: FAIL'; echo $?)" "$out"
prepare_gate "$TMP/good.txt"
out=$(FAKE_ADB_FULL=1 FAKE_DECOY=1 run_gate)
check "(m1) số đo neo đúng dòng: decoy optional=77/main-thread ms=99 không lọt vào metrics" \
  "$(has "$out" 'metrics cold start: prefs warmup waited=3 ms, timeouts=0, onCreate main-thread ms=31, optional prefs ms=9'; echo $?)" "$out"
prepare_gate "$TMP/good.txt"
out=$(FAKE_ADB_FULL=1 FAKE_LOADAVG=empty run_gate)
check "(m2) /proc/loadavg không đọc được: device=<unreadable>" \
  "$(has "$out" 'device=<unreadable>' && hasnt "$out" 'device= '; echo $?)" "$out"
prepare_gate "$TMP/good.txt"
out=$(FAKE_ADB_FULL=1 run_gate)
first=$(gate_log | head -1)
check "(m4) dòng gate log: là dòng đầu của \$LOG, trước adb install" \
  "$([[ "$first" == 'gate log: DOC/testing/device-gate-stub-serial-'* ]]; echo $?)" "$first"

# ── 14b. 1.6.3 Task 2: metrics có initPlugin ms và TotalTime ──────────────────────────────────────────
prepare_gate "$TMP/good.txt"
out=$(FAKE_ADB_FULL=1 run_gate)
check "(T3a) metrics cold start: có initPlugin ms=… và TotalTime ms=100" \
  "$(has "$out" 'metrics cold start: prefs warmup waited=3 ms, timeouts=0, onCreate main-thread ms=31, optional prefs ms=9, initPlugin ms=manager=5 meta=8 helper+gate=2 logevent=10 total=25, TotalTime ms=100'; echo $?)" "$out"
check "(T3c) key[cold start]> chép dòng initPlugin ms: manager=5" \
  "$(gate_log | command grep -qF '  key[cold start]> ' && gate_log | command grep 'key\[cold start\]> ' | command grep -qF 'initPlugin ms: manager=5'; echo $?)" "$(gate_log | head -40)"
line2=$(printf '%s\n' "$out" | command grep '^metrics cold start 2:')
check "(T3d) dòng metrics cold start 2: có TotalTime ms=200 (của chặng 2, không phải 100 của chặng 1)" \
  "$(has "$line2" 'TotalTime ms=200' && hasnt "$line2" 'TotalTime ms=100'; echo $?)" "$line2"
check "(T3f) key[cold start 2]> chép dòng telemetry off" \
  "$(gate_log | command grep 'key\[cold start 2\]> ' | command grep -qF 'telemetry off: test'; echo $?)" "$(gate_log | command grep 'key\[' | head -20)"
prepare_gate "$TMP/good.txt"
out=$(FAKE_ADB_FULL=1 FAKE_INITPLUGIN=no run_gate)
check "(T3b) thiếu dòng initPlugin: initPlugin ms=none, TotalTime ms=100, vẫn RESULT: PASS" \
  "$(has "$out" 'initPlugin ms=none, TotalTime ms=100' && has "$out" 'RESULT: PASS'; echo $?)" "$out"
prepare_gate "$TMP/good.txt"
out=$(FAKE_ADB_FULL=1 FAKE_DECOY=1 run_gate)
check "(T3e) decoy D Other: initPlugin ms không được lấy" \
  "$(has "$out" 'initPlugin ms=manager=5 meta=8 helper+gate=2 logevent=10 total=25'; echo $?)" "$out"

# ── 15. 1.6.2 G3: AppsFlyer dò SDK Samsung cloud game (tuỳ chọn) trên API 26 ─────────────────────────
# 6 dòng (3 cặp) nguyên văn từ DOC/testing/device-gate-app-emulator-5580-20260929-152838-post.log (dòng 761, 780,
# 803, 822, 845, 864; Task R, APK b2824c54…). com.samsung.android.game.cloudgame.* không có trong AAR 6.16.2,
# releaseRuntimeClasspath lẫn mapping; AFe1hSDK dò CloudDevSdk bằng Class.forName trong try/catch Throwable và chỉ
# tạo AFe1hSDK$AFa1vSDK (implements CloudDevCallback) khi lớp có. Phải allowlisted.
SAMSUNG_LOG=$TMP/samsung.log
cat > "$SAMSUNG_LOG" <<'LOGEOF'
09-29 15:38:44.123  4193  4193 I zygote64: Rejecting re-init on previously-failed class java.lang.Class<com.appsflyer.internal.AFe1hSDK$AFa1vSDK>: java.lang.NoClassDefFoundError: Failed resolution of: Lcom/samsung/android/game/cloudgame/dev/sdk/CloudDevCallback;
09-29 15:38:44.123  4193  4193 I zygote64: Caused by: java.lang.ClassNotFoundException: Didn't find class "com.samsung.android.game.cloudgame.dev.sdk.CloudDevCallback" on path: DexPathList[[zip file "/data/app/test.sdk.zd-wrD7mpfzclX0qs73yM1KCw==/base.apk"],nativeLibraryDirectories=[/data/app/test.sdk.zd-wrD7mpfzclX0qs73yM1KCw==/lib/arm64, /data/app/test.sdk.zd-wrD7mpfzclX0qs73yM1KCw==/base.apk!/lib/arm64-v8a, /system/lib64, /vendor/lib64]]
09-29 15:38:44.123  4193  4193 I zygote64: Rejecting re-init on previously-failed class java.lang.Class<com.appsflyer.internal.AFe1hSDK$AFa1vSDK>: java.lang.NoClassDefFoundError: Failed resolution of: Lcom/samsung/android/game/cloudgame/dev/sdk/CloudDevCallback;
09-29 15:38:44.123  4193  4193 I zygote64: Caused by: java.lang.ClassNotFoundException: Didn't find class "com.samsung.android.game.cloudgame.dev.sdk.CloudDevCallback" on path: DexPathList[[zip file "/data/app/test.sdk.zd-wrD7mpfzclX0qs73yM1KCw==/base.apk"],nativeLibraryDirectories=[/data/app/test.sdk.zd-wrD7mpfzclX0qs73yM1KCw==/lib/arm64, /data/app/test.sdk.zd-wrD7mpfzclX0qs73yM1KCw==/base.apk!/lib/arm64-v8a, /system/lib64, /vendor/lib64]]
09-29 15:38:44.127  4193  4193 I zygote64: Rejecting re-init on previously-failed class java.lang.Class<com.appsflyer.internal.AFe1hSDK$AFa1vSDK>: java.lang.NoClassDefFoundError: Failed resolution of: Lcom/samsung/android/game/cloudgame/dev/sdk/CloudDevCallback;
09-29 15:38:44.127  4193  4193 I zygote64: Caused by: java.lang.ClassNotFoundException: Didn't find class "com.samsung.android.game.cloudgame.dev.sdk.CloudDevCallback" on path: DexPathList[[zip file "/data/app/test.sdk.zd-wrD7mpfzclX0qs73yM1KCw==/base.apk"],nativeLibraryDirectories=[/data/app/test.sdk.zd-wrD7mpfzclX0qs73yM1KCw==/lib/arm64, /data/app/test.sdk.zd-wrD7mpfzclX0qs73yM1KCw==/base.apk!/lib/arm64-v8a, /system/lib64, /vendor/lib64]]
LOGEOF
out=$(run_lib "$ROOT/tools/device_gate_allowlist.txt" "$SAMSUNG_LOG" "$TMP/samsung-gate.log")
check "(G3a) Samsung cloud game: 6 dòng nguyên văn API 26 (NoClassDef dạng /, ClassNotFound dạng .) allowlisted, r8=0, PASS" \
  "$(has "$out" 'r8 strings fixture=0 (raw=6, allowlisted=6; need 0)' && has "$out" 'RESULT: PASS'; echo $?)" "$out"
# Neo lớp chủ: mọi regex allowlist nhắc cloudgame ở dạng "Rejecting re-init" phải mang đúng AFe1hSDK$AFa1vSDK>.
samsung_re=$(grep -v '^#' "$ROOT/tools/device_gate_allowlist.txt" | grep -i 'cloudgame')
check "(G3b) allowlist thật: đúng 2 regex cloudgame; dòng Rejecting re-init neo AFe1hSDK\$AFa1vSDK>, dòng Caused by neo tên lớp + on path" \
  "$([ "$(printf '%s\n' "$samsung_re" | grep -c '')" -eq 2 ] \
    && [ "$(printf '%s\n' "$samsung_re" | grep -F 'Rejecting re-init' | grep -cF 'AFe1hSDK\$AFa1vSDK>')" -eq 1 ] \
    && [ "$(printf '%s\n' "$samsung_re" | grep -F 'Caused by' | grep -cF 'CloudDevCallback" on path')" -eq 1 ]; echo $?)" \
  "$samsung_re"
# Ca âm: regex Samsung không được nuốt lỗi R8 thật. Mỗi dòng dưới đây phải vẫn bị đếm (r8=1, FAIL).
# - cùng lớp Samsung nhưng lớp chủ KHÁC: AFe1hSDK$AFa1zSDK, chính AFe1hSDK, lớp lồng AFe1hSDK$AFa1vSDK$1, lớp com.app.plugin;
# - đúng lớp chủ AFe1hSDK$AFa1vSDK nhưng thiếu lớp của chính SDK (strip thật) — và strip thật dạng ClassNotFound;
# - lớp anh em cùng gói Samsung (CloudDevSdk) ở cả hai dạng, và tên có tiền tố CloudDevCallback (CloudDevCallbackImpl).
i=0
while IFS= read -r line; do
  i=$((i + 1)); f=$TMP/g3-neg-$i.log
  printf '%s\n' "$line" > "$f"
  out=$(run_lib "$ROOT/tools/device_gate_allowlist.txt" "$f" "$TMP/g3-neg-$i-gate.log")
  check "(G3 âm $i) vẫn bị đếm, FAIL: ${line:0:120}" \
    "$(has "$out" 'r8 strings fixture=1 (raw=1, allowlisted=0; need 0)' && has "$out" 'RESULT: FAIL'; echo $?)" "$out"
done <<'LOGEOF'
09-29 23:00:00.000  4242  4242 I zygote64: Rejecting re-init on previously-failed class java.lang.Class<com.appsflyer.internal.AFe1hSDK$AFa1zSDK>: java.lang.NoClassDefFoundError: Failed resolution of: Lcom/samsung/android/game/cloudgame/dev/sdk/CloudDevCallback;
09-29 23:00:00.001  4242  4242 I zygote64: Rejecting re-init on previously-failed class java.lang.Class<com.appsflyer.internal.AFe1hSDK>: java.lang.NoClassDefFoundError: Failed resolution of: Lcom/samsung/android/game/cloudgame/dev/sdk/CloudDevCallback;
09-29 23:00:00.002  4242  4242 I zygote64: Rejecting re-init on previously-failed class java.lang.Class<com.appsflyer.internal.AFe1hSDK$AFa1vSDK$1>: java.lang.NoClassDefFoundError: Failed resolution of: Lcom/samsung/android/game/cloudgame/dev/sdk/CloudDevCallback;
09-29 23:00:00.003  4242  4242 I zygote64: Rejecting re-init on previously-failed class java.lang.Class<com.app.plugin.ads.Foo>: java.lang.NoClassDefFoundError: Failed resolution of: Lcom/samsung/android/game/cloudgame/dev/sdk/CloudDevCallback;
09-29 23:00:00.004  4242  4242 I zygote64: Rejecting re-init on previously-failed class java.lang.Class<com.appsflyer.internal.AFe1hSDK$AFa1vSDK>: java.lang.NoClassDefFoundError: Failed resolution of: Lcom/app/plugin/Foo;
09-29 23:00:00.005  4242  4242 W test.sdk.zd: java.lang.ClassNotFoundException: Didn't find class "com.app.plugin.ads.Foo" on path: DexPathList[[zip file "/data/app/test.sdk.zd/base.apk"]]
09-29 23:00:00.006  4242  4242 I zygote64: Rejecting re-init on previously-failed class java.lang.Class<com.appsflyer.internal.AFe1hSDK$AFa1vSDK>: java.lang.NoClassDefFoundError: Failed resolution of: Lcom/samsung/android/game/cloudgame/dev/sdk/CloudDevSdk;
09-29 23:00:00.007  4242  4242 I zygote64: Caused by: java.lang.ClassNotFoundException: Didn't find class "com.samsung.android.game.cloudgame.dev.sdk.CloudDevSdk" on path: DexPathList[[zip file "/data/app/test.sdk.zd/base.apk"]]
09-29 23:00:00.008  4242  4242 I zygote64: Caused by: java.lang.ClassNotFoundException: Didn't find class "com.samsung.android.game.cloudgame.dev.sdk.CloudDevCallbackImpl" on path: DexPathList[[zip file "/data/app/test.sdk.zd/base.apk"]]
09-29 23:00:00.009  4242  4242 I zygote64: Rejecting re-init on previously-failed class java.lang.Class<com.appsflyer.internal.AFe1hSDK$AFa1vSDK>: java.lang.NoClassDefFoundError: Failed resolution of: Lcom/samsung/android/game/cloudgame/dev/sdk/CloudDevCallbackImpl;
LOGEOF
# Cặp của lớp chủ com.app.plugin thiếu CloudDevCallback: dòng Caused by (không mang lớp chủ) được loại, nhưng dòng
# Rejecting re-init vẫn bị đếm nên cổng vẫn FAIL — regex Caused by không mở đường cho lớp chủ lạ.
f=$TMP/g3-pair-sdk-owner.log
cat > "$f" <<'LOGEOF'
09-29 23:00:01.000  4242  4242 I zygote64: Rejecting re-init on previously-failed class java.lang.Class<com.app.plugin.ads.Foo>: java.lang.NoClassDefFoundError: Failed resolution of: Lcom/samsung/android/game/cloudgame/dev/sdk/CloudDevCallback;
09-29 23:00:01.000  4242  4242 I zygote64: Caused by: java.lang.ClassNotFoundException: Didn't find class "com.samsung.android.game.cloudgame.dev.sdk.CloudDevCallback" on path: DexPathList[[zip file "/data/app/test.sdk.zd/base.apk"]]
LOGEOF
out=$(run_lib "$ROOT/tools/device_gate_allowlist.txt" "$f" "$TMP/g3-pair-gate.log")
check "(G3c) cặp lớp chủ com.app.plugin thiếu CloudDevCallback: r8=1 (raw=2, allowlisted=1), FAIL" \
  "$(has "$out" 'r8 strings fixture=1 (raw=2, allowlisted=1; need 0)' && has "$out" 'RESULT: FAIL' \
    && has "$out" 'r8> 09-29 23:00:01.000  4242  4242 I zygote64: Rejecting re-init on previously-failed class java.lang.Class<com.app.plugin.ads.Foo>'; echo $?)" "$out"

# ── 16. 1.6.4 Task 5: --variant utility|game (mặc định utility) chọn APK và mapping của flavor ────────
# Từ 1.6.4 mọi cổng thiết bị chạy APK utility (AdMob + Meta); game chỉ build + release gate. Biến thể lạ phải
# FAIL trước mọi lệnh adb, không được rơi về một đường APK không tồn tại rồi báo lỗi cài đặt.
prepare_gate "$TMP/good.txt"
out=$(run_gate)
check "(V1) mặc định utility: \$LOG có variant: utility apk=…, adb install -r APK utility" \
  "$(has "$(gate_log)" "variant: utility apk=$GATE_ROOT/apk/app-release.apk" \
    && has "$(cat "$TMP/adb-calls")" "install -r $GATE_ROOT/apk/app-release.apk"; echo $?)" "$out
adb calls: $(cat "$TMP/adb-calls")"
prepare_gate "$TMP/good.txt"
out=$(run_gate --variant game)
check "(V2) --variant game: adb install -r APK game, \$LOG có variant: game" \
  "$(has "$(gate_log)" "variant: game apk=$GATE_ROOT/apk/app-release.apk" \
    && has "$(cat "$TMP/adb-calls")" "install -r $GATE_ROOT/apk/app-release.apk"; echo $?)" "$out
adb calls: $(cat "$TMP/adb-calls")"
prepare_gate "$TMP/good.txt"
out=$(run_gate --variant foo); rc=$?
check "(V3) --variant foo: FAIL nêu biến thể, RESULT: FAIL, exit 1, 0 lệnh adb" \
  "$(has "$out" "FAIL: --variant phải là utility|game (nhận 'foo')" && has "$out" 'RESULT: FAIL' && hasnt "$out" 'RESULT: PASS' \
    && [ "$rc" -eq 1 ] && [ ! -s "$TMP/adb-calls" ]; echo $?)" "$out
exit=$rc; adb calls: $(cat "$TMP/adb-calls")"
prepare_gate "$TMP/good.txt"
out=$(run_gate --bogus); rc=$?
check "(V4) đối số lạ --bogus: FAIL nêu đối số, RESULT: FAIL, exit 1, 0 lệnh adb" \
  "$(has "$out" "FAIL: đối số lạ '--bogus'" && has "$out" 'RESULT: FAIL' && hasnt "$out" 'RESULT: PASS' \
    && [ "$rc" -eq 1 ] && [ ! -s "$TMP/adb-calls" ]; echo $?)" "$out
exit=$rc; adb calls: $(cat "$TMP/adb-calls")"

# ── 9. --consent và dòng 1 có api= (2.0.0 Task 0d: C1–C6, C3a–c, A1, A2; 2.1.0 Task 0b: F3a–b, U1–U4) ───────────────────────────────────
# adb giả cho kịch bản consent: form UMP (Do not consent), nút của demo (Privacy options, Show Rewarded Interstitial), form Consent,
# sink giả = file JSONL + log kiểu uvicorn. FAKE_TT = TotalTime; FAKE_DENIED_POST=1: app gửi 1 hàng + 1 POST trong cửa sổ DENIED;
# FAKE_NOFLUSH=1: chạm Consent không đẩy hàng nào; FAKE_NOFLUSHLINE=1: hàng có đẩy nhưng logcat không có dòng "flushed N held after DENIED"; FAKE_RESTART=1|pkg|crash|gone: process đổi pid (hoặc mất) sau khi chạm rwi, pkg = logcat có killDueToPackageUpdate, crash = có FATAL; FAKE_TT_LATE: TotalTime lần mở thứ 2; FAKE_FORM=no: form không hiện; FAKE_NOSINKLOG=1: không có log sink (không đếm được POST).
mkdir -p "$TMP/cbin"
cat > "$TMP/cbin/adb" <<'EOF'
#!/usr/bin/env bash
echo "$*" >> "$ADB_CALLS"
D=$FAKE_SINKDIR
CXY=${FAKE_CXY:-540 1350}   # toạ độ chạm làm form Consent ăn (GRANTED); FAKE_NONODE=1: form Consent không có node (thang chạm 0.70/0.72/0.68 H của 1080x2400)
taps() { grep -c "^shell input tap $1\$" "$ADB_CALLS"; }
adcover() { [ "${FAKE_ADFOCUS:-}" = 1 ] && [ "$(taps "540 1516")" -gt 0 ] && ! grep -q '^shell input keyevent KEYCODE_BACK$' "$ADB_CALLS"; }   # 2.1.0 U2: rwi còn che tới khi có back
sink() { echo '{"event_name": "ad_show"}' >> "$D/events.jsonl"; [ "$2" = 0 ] || echo '127.0.0.1:50000 - "POST /push HTTP/1.1" 200 OK' >> "$D/sink-8089.log"; }
case "$*" in
  install*) echo Success;;
  "shell getprop ro.build.version.sdk") echo "${FAKE_API:-37}";;
  "shell pm clear"*) echo Success;;
  "shell am start -W"*) tt=${FAKE_TT:-300}; [ -z "${FAKE_TT_LATE:-}" ] || [ "$(grep -c '^shell am start -W' "$ADB_CALLS")" -lt 2 ] || tt=$FAKE_TT_LATE   # FAKE_TT_LATE: lần mở thứ 2 (lần đo) lạnh
    printf 'Status: ok\nTotalTime: %s\nWaitTime: 5\n' "$tt";;
  "shell pidof"*) if [ -n "${FAKE_RESTART:-}" ] && [ "$(taps "540 1516")" -gt 0 ]; then [ "$FAKE_RESTART" = gone ] || echo 4343; else echo 4242; fi;;
  "logcat -d -b crash") [ "${FAKE_RESTART:-}" = crash ] && { echo "E AndroidRuntime: FATAL EXCEPTION: main"; echo "E AndroidRuntime: Process: test.sdk.zd, PID: 4242"; };;
  "shell cat /sdcard/ui.xml")
    if adcover; then cat "$FAKE_UIDIR/redmi-rwi-adactivity.xml"; exit 0; fi
    cn='<node text="Consent" bounds="[100,1310][980,1390]" />'; [ "${FAKE_NONODE:-}" = 1 ] && cn=""
    f='<node text="Do not consent" bounds="[100,1450][980,1534]" />'; [ "${FAKE_FORM:-yes}" = no ] && f=""
    echo "<hierarchy>$f<node text=\"Privacy options\" bounds=\"[0,240][1080,316]\" /><node text=\"Show Rewarded Interstitial\" bounds=\"[0,1480][1080,1552]\" />$cn</hierarchy>";;
  "shell dumpsys window"*) if adcover; then echo "  mCurrentFocus=Window{15ef u0 test.sdk.zd/com.google.android.gms.ads.AdActivity}"; else echo "  mCurrentFocus=Window{5e6f u0 test.sdk.zd/com.app.plugin.demo.MainActivity}"; fi;;
  "shell input tap 540 1516") [ "${FAKE_DENIED_POST:-}" = 1 ] && sink 1 1;;
  "shell input tap $CXY") [ "${FAKE_NOFLUSH:-}" = 1 ] || { sink 1 1; sink 2 1; sink 3 0; };;
  "logcat -d"|"logcat -d --pid=4242")
    n=$(taps "540 1516"); [ "$n" -eq 0 ] || echo "09-24 19:00:10.000  4242  4242 D AppPlugin: ads rwi helper rwi_default showRwi call show"
    [ "$(taps "540 1492")" -eq 0 ] || [ "${FAKE_NODENIED:-}" = 1 ] || echo "09-24 19:00:12.000  4242  4242 D AppPlugin: consent tier=DENIED (ump=OBTAINED, tcf=DENIED)"
    [ "$(taps "$CXY")" -eq 0 ] || echo "09-24 19:00:20.000  4242  4242 D AppPlugin: consent tier=GRANTED"
    [ "$(taps "$CXY")" -eq 0 ] || [ "${FAKE_NOFLUSH:-}" = 1 ] || [ "${FAKE_NOFLUSHLINE:-}" = 1 ] || echo "09-24 19:00:21.000  4242  4242 D AppPlugin: telemetry consent gate: flushed 3 held after DENIED (tier=GRANTED)"
    # kill do cập nhật gói: pkg/crash = của pid 4242 (app); 1 = của pid KHÁC (9999), không được tính là nguyên nhân của pid 4242
    if [ "$*" = "logcat -d" ]; then
      case "${FAKE_RESTART:-}" in pkg|crash) kp=4242;; 1) kp=9999;; *) kp="";; esac
      [ -z "$kp" ] || echo "10-02 06:43:32.972   641   662 I ActivityManager: Killing $kp:test.sdk.zd/u0a231 (adj 0): stop com.google.android.gms due to killDueToPackageUpdate"
    fi;;
esac
exit 0
EOF
chmod +x "$TMP/cbin/adb"
# prepare_gate rồi chạy `device_gate.sh --consent` trong cây tạm; sink = $GATE_ROOT/sinkc (JSONL rỗng + log uvicorn trừ FAKE_NOSINKLOG=1).
run_consent() {
  mkdir -p "$GATE_ROOT/sinkc"; : > "$GATE_ROOT/sinkc/events.jsonl"
  [ "${FAKE_NOSINKLOG:-}" = 1 ] || printf '%s\n' 'INFO:     Application startup complete.' '127.0.0.1:49904 - "GET /health HTTP/1.1" 200 OK' > "$GATE_ROOT/sinkc/sink-8089.log"
  ADB_CALLS=$TMP/adb-calls FAKE_SINKDIR=$GATE_ROOT/sinkc PATH="$TMP/cbin:$TMP/bin:$PATH" ANDROID_SERIAL=stub-serial MG_NO_SLEEP=1 \
    FAKE_TT="${FAKE_TT:-}" FAKE_DENIED_POST="${FAKE_DENIED_POST:-}" FAKE_NOFLUSH="${FAKE_NOFLUSH:-}" FAKE_NOFLUSHLINE="${FAKE_NOFLUSHLINE:-}" FAKE_FORM="${FAKE_FORM:-}" FAKE_RESTART="${FAKE_RESTART:-}" FAKE_TT_LATE="${FAKE_TT_LATE:-}" FAKE_GETPROP="${FAKE_GETPROP:-}" FAKE_CXY="${FAKE_CXY:-}" FAKE_NONODE="${FAKE_NONODE:-}" FAKE_NODENIED="${FAKE_NODENIED:-}" FAKE_ADFOCUS="${FAKE_ADFOCUS:-}" FAKE_UIDIR="$ROOT/tools/tests/fixtures/ui" \
    bash "$GATE_ROOT/tools/device_gate.sh" --consent --sink-dir "$GATE_ROOT/sinkc" \
      --apk "$GATE_ROOT/apk/app-release.apk" --mapping "$GATE_ROOT/apk/mapping.txt" 2>&1
}
prepare_gate "$TMP/good.txt"
out=$(FAKE_TT=9000 run_consent); rc=$?
c1a=$([ "$(grep -c '^shell am start -W' "$TMP/adb-calls")" -eq 3 ] && hasnt "$(cat "$TMP/adb-calls")" 'input tap'; echo $?)
prepare_gate "$TMP/good.txt"
outf=$(FAKE_FORM=no run_consent); rcf=$?
check "(C1) máy lạnh: TotalTime 9000 ms ≥ 5000 cả 3 lần chạy thử, hoặc TotalTime 300 ms nhưng form UMP không hiện: INVALID (cold guest), exit 4, không chạm form, không PASS" \
  "$(has "$out" 'RESULT: INVALID (cold guest)' && hasnt "$out" 'RESULT: PASS' && [ "$rc" -eq 4 ] && [ "$c1a" -eq 0 ] \
    && has "$outf" 'RESULT: INVALID (cold guest)' && hasnt "$outf" 'RESULT: PASS' && [ "$rcf" -eq 4 ] \
    && [ "$(grep -c '^shell am start -W' "$TMP/adb-calls")" -eq 3 ] && hasnt "$(cat "$TMP/adb-calls")" 'input tap'; echo $?)" "$out
exit=$rc; form=no: $outf
exit=$rcf; adb calls: $(cat "$TMP/adb-calls")"
prepare_gate "$TMP/good.txt"
out=$(run_consent); rc=$?
check "(C2) máy ấm, sink không nhận gì khi DENIED: DENIED rows=0 post=0, GRANTED đẩy hàng, RESULT: PASS, exit 0, force_eea + pm clear" \
  "$(has "$out" 'DENIED window rows=0 post=0' && has "$out" 'flushed 3 held after DENIED' && has "$out" 'GRANTED flush window' && has "$out" 'RESULT: PASS' && [ "$rc" -eq 0 ] \
    && has "$(cat "$TMP/adb-calls")" '--ez force_eea true' && has "$(cat "$TMP/adb-calls")" 'shell pm clear' \
    && has "$(gate_log)" 'RESULT: PASS'; echo $?)" "$out
exit=$rc"
prepare_gate "$TMP/good.txt"
out=$(FAKE_DENIED_POST=1 run_consent); rc=$?
check "(C3) POST > 0 trong cửa sổ DENIED: FAIL nêu DENIED rows=1 post=1, không PASS, exit 1" \
  "$(has "$out" 'DENIED window rows=1 post=1' && has "$out" 'RESULT: FAIL' && hasnt "$out" 'RESULT: PASS' && [ "$rc" -eq 1 ]; echo $?)" "$out
exit=$rc"
prepare_gate "$TMP/good.txt"
out=$(FAKE_RESTART=pkg run_consent); rc=$?
check "(C3a) pid đổi trong cửa sổ DENIED + logcat có killDueToPackageUpdate của app: INVALID (process restart: package update), exit 4, không PASS" \
  "$(has "$out" 'RESULT: INVALID (process restart: package update)' && hasnt "$out" 'RESULT: PASS' && [ "$rc" -eq 4 ]; echo $?)" "$out
exit=$rc"
prepare_gate "$TMP/good.txt"
out=$(FAKE_RESTART=1 run_consent); rc=$?
prepare_gate "$TMP/good.txt"
outg=$(FAKE_RESTART=gone run_consent); rcg=$?
check "(C3b) pid đổi hoặc mất trong cửa sổ DENIED mà KHÔNG có dòng killDueToPackageUpdate: FAIL (process gone), không INVALID, exit 1" \
  "$(has "$out" 'FAIL: process gone' && has "$out" 'RESULT: FAIL' && hasnt "$out" 'INVALID' && [ "$rc" -eq 1 ] \
    && has "$outg" 'FAIL: process gone' && has "$outg" 'RESULT: FAIL' && hasnt "$outg" 'INVALID' && [ "$rcg" -eq 1 ]; echo $?)" "$out
exit=$rc; gone: $outg
exit=$rcg"
prepare_gate "$TMP/good.txt"
out=$(FAKE_RESTART=crash run_consent); rc=$?
check "(C3c) pid đổi trong cửa sổ DENIED kèm FATAL của app (kể cả khi có dòng kill gói): FAIL nêu crash, không INVALID, exit 1" \
  "$(has "$out" 'kèm crash FATAL=1' && has "$out" 'RESULT: FAIL' && hasnt "$out" 'INVALID' && [ "$rc" -eq 1 ]; echo $?)" "$out
exit=$rc"
prepare_gate "$TMP/good.txt"
out=$(FAKE_TT_LATE=9000 run_consent); rc=$?
check "(C6) lần chạy thử ấm nhưng lần đo TotalTime 9000 ms ≥ 5000: INVALID (cold guest), exit 4, không chạm Do not consent, không PASS" \
  "$(has "$out" 'RESULT: INVALID (cold guest)' && has "$out" 'lần đo TotalTime=9000' && hasnt "$out" 'RESULT: PASS' && [ "$rc" -eq 4 ] && hasnt "$(cat "$TMP/adb-calls")" 'input tap'; echo $?)" "$out
exit=$rc"
prepare_gate "$TMP/good.txt"
out=$(FAKE_NOFLUSH=1 run_consent); rc=$?
prepare_gate "$TMP/good.txt"
outl=$(FAKE_NOFLUSHLINE=1 run_consent); rcl=$?
check "(C4) sau GRANTED sink không nhận hàng nào: FAIL vì không flush; hàng có tăng nhưng thiếu dòng 'flushed N held after DENIED': cũng FAIL; không PASS, exit 1" \
  "$(has "$out" 'FAIL: sau GRANTED' && has "$out" 'RESULT: FAIL' && hasnt "$out" 'RESULT: PASS' && [ "$rc" -eq 1 ] \
    && has "$outl" "FAIL: không có 'flushed N held after DENIED'" && has "$outl" 'RESULT: FAIL' && hasnt "$outl" 'RESULT: PASS' && [ "$rcl" -eq 1 ]; echo $?)" "$out
exit=$rc; noflushline: $outl
exit=$rcl"
prepare_gate "$TMP/good.txt"
out=$(FAKE_NOSINKLOG=1 run_consent); rc=$?
check "(C5) log của sink không đọc được: FAIL (không đếm được POST nên 0 không phải bằng chứng), không PASS, exit 1" \
  "$(has "$out" 'FAIL: log của sink không đọc được' && has "$out" 'RESULT: FAIL' && hasnt "$out" 'RESULT: PASS' && [ "$rc" -eq 1 ]; echo $?)" "$out
exit=$rc"
prepare_gate "$TMP/good.txt"
mkdir -p "$TMP/u1bin"; cat > "$TMP/u1bin/adb" <<'EOF'
#!/usr/bin/env bash
echo "$*" >> "$ADB_CALLS"
case "$*" in
  "shell cat /sdcard/ui.xml") cat "$FAKE_UI";;
  "logcat -d") grep -q "^shell input tap 540 1721$" "$ADB_CALLS" && echo "D AppPlugin: telemetry consent gate: flushed 3 held after DENIED (tier=GRANTED)";;
esac
exit 0
EOF
chmod +x "$TMP/u1bin/adb"
out=$(FAKE_NONODE=1 FAKE_CXY="540 1704" run_consent); rc=$?
check "(F3a) form Consent không có node, chạm 0.72 H (fixture) không ăn, chạm 0.71 H ăn: PASS, log có 'fallback 1/3 xy=540 1728', 'fallback 2/3 xy=540 1704', 'tap form 'Consent' verified', không có 'fallback 3/3'; không bao giờ chạm 0.70 H (540 1680)" \
  "$(has "$out" 'consent tap fallback 1/3 xy=540 1728' && has "$out" 'consent tap fallback 2/3 xy=540 1704' && has "$out" "tap form 'Consent' verified" && hasnt "$out" 'fallback 3/3' \
    && has "$out" 'flushed 3 held after DENIED' && has "$out" 'RESULT: PASS' && [ "$rc" -eq 0 ] \
    && [ "$(grep -c '^shell input tap 540 1728$' "$TMP/adb-calls")" -eq 1 ] && [ "$(grep -c '^shell input tap 540 1704$' "$TMP/adb-calls")" -eq 1 ] && [ "$(grep -c '^shell input tap 540 1680$' "$TMP/adb-calls")" -eq 0 ]; echo $?)" "$out
exit=$rc"
prepare_gate "$TMP/good.txt"
out=$(FAKE_NONODE=1 FAKE_CXY="540 9999" run_consent); rc=$?
check "(F3b) form Consent không có node, cả 3 lần chạm fixture (0.72/0.71/0.73 H) đều không ăn: FAIL (không PASS), đúng 3 lần chạm, báo 'chạm 'Consent' 3 lần' và thông báo flush cũ" \
  "$(has "$out" 'fallback 3/3 xy=540 1752' && hasnt "$out" "'Consent' verified" && has "$out" "FAIL: chạm 'Consent' 3 lần" && has "$out" 'FAIL: sau GRANTED' && has "$out" "FAIL: không có 'flushed N held after DENIED'" && has "$out" 'RESULT: FAIL' && hasnt "$out" 'RESULT: PASS' && [ "$rc" -eq 1 ] \
    && [ "$(grep -c '^shell input tap 540 \(1728\|1704\|1752\)$' "$TMP/adb-calls")" -eq 3 ]; echo $?)" "$out
exit=$rc"
prepare_gate "$TMP/good.txt"
# U3: chạm "Do not consent" (node, tâm 540 1492) nhưng app không báo `consent tier=DENIED`: thử lại ≤ 2 rồi FAIL, đúng 3 chạm, không đi tiếp tới rwi
out=$(FAKE_NODENIED=1 run_consent); rc=$?
check "(U3) log thiếu 'consent tier=DENIED' sau chạm 'Do not consent': thử lại ≤ 2 (tổng 3 chạm), FAIL, không chạm rwi, không PASS" \
  "$(has "$out" "tap form 'Do not consent' 2/3: không có" && has "$out" "FAIL: chạm 'Do not consent' 3 lần" && has "$out" 'RESULT: FAIL' && hasnt "$out" 'RESULT: PASS' && [ "$rc" -eq 1 ] \
    && [ "$(grep -c '^shell input tap 540 1492$' "$TMP/adb-calls")" -eq 3 ] && [ "$(grep -c '^shell input tap 540 1516$' "$TMP/adb-calls")" -eq 0 ]; echo $?)" "$out
exit=$rc"
prepare_gate "$TMP/good.txt"
# U4: form vắng sau pm clear (TotalTime ấm): mỗi lần chạy thử chờ lại đúng 1 lần rồi INVALID (cold guest); không chạm form
out=$(FAKE_FORM=no run_consent); rc=$?
check "(U4) form UMP vắng sau pm clear: chờ lại 1/1 mỗi lần chạy thử (3 lần), không 'chờ lại 2/', INVALID (cold guest), exit 4, không chạm form" \
  "$(has "$out" 'form UMP vắng: chờ lại 1/1' && hasnt "$out" 'chờ lại 2/' && [ "$(printf '%s\n' "$out" | grep -c 'chờ lại 1/1')" -eq 3 ] && has "$out" 'RESULT: INVALID (cold guest)' && hasnt "$out" 'RESULT: PASS' && [ "$rc" -eq 4 ] && hasnt "$(cat "$TMP/adb-calls")" 'input tap'; echo $?)" "$out
exit=$rc"
prepare_gate "$TMP/good.txt"
# U2: rwi (AdActivity) còn che sau khi chạm rwi: back TRƯỚC khi chạm 'Privacy options'; dump che = dump Redmi thật; focus về MainActivity rồi PASS
out=$(FAKE_ADFOCUS=1 run_consent); rc=$?
bk=$(grep -n '^shell input keyevent KEYCODE_BACK$' "$TMP/adb-calls" | head -1 | cut -d: -f1); po=$(grep -n '^shell input tap 540 278$' "$TMP/adb-calls" | head -1 | cut -d: -f1)
check "(U2) focus AdActivity sau rwi: KEYCODE_BACK được gửi (≤ 2) trước lần chạm 'Privacy options' (540 278), rồi PASS" \
  "$(has "$out" 'quảng cáo còn che app' && has "$out" 'back 1/2' && hasnt "$out" 'back 3/' && [ -n "$bk" ] && [ -n "$po" ] && [ "$bk" -lt "$po" ] && has "$out" 'RESULT: PASS' && [ "$rc" -eq 0 ]; echo $?)" "bk=$bk po=$po
$out"
prepare_gate "$TMP/good.txt"
# U1 (dump thật Redmi, fixtures/ui/redmi-ump-form.xml): node 'Consent' bounds y 1666-1776 -> chạm TÂM 540 1721 (không 0.70 H = 1680); verify bằng dòng flush
u1=$( cd "$ROOT" && ADB_CALLS=$TMP/u1-calls FAKE_UI="$ROOT/tools/tests/fixtures/ui/redmi-ump-form.xml" PATH="$TMP/u1bin:$PATH" MG_NO_SLEEP=1 bash -c '
  . tools/lib/adb_b.sh; . tools/lib/measure_gate.sh; . tools/lib/consent_gate.sh; MG_W=1080 MG_H=2400; MG_LOG=/dev/null
  cm_tap_form Consent; echo "rc=$?"' 2>&1 )
check "(U1) dump thật Redmi: node 'Consent' bounds [99,1666][981,1776] -> chạm tâm 540 1721 đúng 1 lần, không chạm 0.70 H (540 1680), verified" \
  "$(has "$u1" 'rc=0' && has "$u1" "tap form 'Consent' verified" && [ "$(grep -c '^shell input tap 540 1721$' "$TMP/u1-calls")" -eq 1 ] && [ "$(grep -c '^shell input tap 540 1680$' "$TMP/u1-calls")" -eq 0 ]; echo $?)" "$u1"
out=$(FAKE_API=34 run_gate); l34=$(gate_log | head -1)
prepare_gate "$TMP/good.txt"
out=$(FAKE_API=31 run_gate); l31=$(gate_log | head -1)
check "(A1) dòng 1 của cổng có api=<ro.build.version.sdk> (34 → api=34, 31 → api=31), cùng dòng với sha APK" \
  "$(has "$l34" ' api=34' && has "$l34" "sha256=" && has "$l31" ' api=31' && hasnt "$l31" 'api=34'; echo $?)" "$l34
$l31"
prepare_gate "$TMP/good.txt"
out=$(FAKE_GETPROP=fail run_gate); rc=$?
check "(A2) getprop lỗi: INVALID (api), exit 4, 0 lệnh install, không PASS" \
  "$(has "$out" 'RESULT: INVALID (api)' && hasnt "$out" 'RESULT: PASS' && [ "$rc" -eq 4 ] && hasnt "$(cat "$TMP/adb-calls")" 'install'; echo $?)" "$out
exit=$rc; adb calls: $(cat "$TMP/adb-calls")"

# ── 10. --drc: harness brain 3 chặng + đối chứng (2.0.0 Task 0c: R7, R8) ───────────────────────────────────────
# adb giả `dbin` bọc `mbin` (pha A: nút Show Interstitial/Rewarded): `am start -W ... --es brain_policy <json>` in dòng nạp `BrainPolicy: đã nạp '<version>'`
# (+ `brain harness: armed` khi KHÔNG phải lần mở lại không extra) và giữ policy qua cold start (file persist, xoá bởi pm clear); chạm nút \"Show Interstitial (brain)\" (540 1700) khi policy
# calibrated -> 1 hàng brain_decision mỗi lần chạm "Điểm ..." của Brain Playground vào sink (kill/rollout 0 thì không). FAKE_DRC_NODECISION=1: kịch bản chạm không sinh brain_decision (đối chứng đỏ).
mkdir -p "$TMP/dbin"
cat > "$TMP/dbin/adb" <<'EOF'
#!/usr/bin/env bash
ST=$FAKE_DRC_ST; MB=$FAKE_MBIN/adb
case "$*" in
  "shell am start -W"*brain_policy*)
    echo "$*" >> "$ADB_CALLS"
    v=$(printf '%s' "$*" | sed -n 's/.*"version":"\([^"]*\)".*/\1/p'); echo "$v" > "$ST/persist"; echo "$v" > "$ST/mode"
    echo "09-30 06:00:00.000  4242  4242 I AppPlugin: BrainPolicy: đã nạp '$v'" >> "$ST/log"
    echo "09-30 06:00:00.100  4242  4242 I AppPlugin: brain harness: armed version=$v" >> "$ST/log"
    printf 'Status: ok\nTotalTime: 100\n';;
  "shell am start -W"*)
    [ ! -s "$ST/persist" ] || echo "09-30 06:01:00.000  4343  4343 I AppPlugin: BrainPolicy: đã nạp '$(cat "$ST/persist")'" >> "$ST/log"
    echo "" > "$ST/mode"; exec "$MB" "$@";;
  "shell pm clear"*) rm -f "$ST/persist" "$ST/mode"; exec "$MB" "$@";;
  "logcat -c") : > "$ST/log"; exec "$MB" "$@";;
  "logcat -d") cat "$ST/log"; exec "$MB" "$@";;
  "shell cat /sdcard/ui.xml") echo '<hierarchy><node text="Show Interstitial" bounds="[0,1300][1080,1400]" /><node text="Show Interstitial (brain)" bounds="[0,1650][1080,1750]" /></hierarchy>';;
  "shell input tap 540 1700")   # nút demo đường sản phẩm: calibrated -> hàng brain_decision; kill/rollout 0 -> luật tĩnh, quảng cáo hiện (onshowed)
    echo "$*" >> "$ADB_CALLS"
    case "$(cat "$ST/mode" 2>/dev/null)" in
      calib*) dp=INTERSTITIAL_OPPORTUNITY; [ "${FAKE_DRC_REWONLY:-}" != 1 ] || dp=REWARDED_OPPORTUNITY
        [ "${FAKE_DRC_NODECISION:-}" = 1 ] || printf '{"event_name": "brain_decision", "decision_point": "%s", "policy_version": "brain_v0_skeleton"}\n' "$dp" >> "$FAKE_JSONL";;
      *) [ "${FAKE_DRC_NOSHOW:-}" = 1 ] || echo "09-30 06:00:30.000  4242  4242 D AppPlugin: ads full admob0 full_default full_default onshowed" >> "$ST/log"
         [ "${FAKE_DRC_KILLLEAK:-}" != 1 ] || printf '{"event_name": "brain_decision", "decision_point": "INTERSTITIAL_OPPORTUNITY", "policy_version": "brain_v0_skeleton"}\n' >> "$FAKE_JSONL";;
    esac;;
  *) exec "$MB" "$@";;
esac
EOF
chmod +x "$TMP/dbin/adb"
run_drc() {   # gọi SAU prepare_gate (không prepare trong $(...): n_gate mất, hai lượt dùng chung cây)
  mkdir -p "$GATE_ROOT/DOC/testing/local-sink" "$TMP/drc-st"; : > "$GATE_ROOT/DOC/testing/local-sink/events.jsonl"; rm -f "$TMP/drc-st/"*; : > "$TMP/drc-st/log"
  ADB_CALLS=$TMP/adb-calls FAKE_JSONL=$GATE_ROOT/DOC/testing/local-sink/events.jsonl FAKE_DRC_ST=$TMP/drc-st FAKE_MBIN=$TMP/mbin PATH="$TMP/dbin:$TMP/bin:$PATH" MG_NO_SLEEP=1 \
    ANDROID_SERIAL=stub-serial FAKE_DRC_NODECISION="${FAKE_DRC_NODECISION:-}" FAKE_DRC_KILLLEAK="${FAKE_DRC_KILLLEAK:-}" FAKE_DRC_NOSHOW="${FAKE_DRC_NOSHOW:-}" FAKE_DRC_REWONLY="${FAKE_DRC_REWONLY:-}" bash "$GATE_ROOT/tools/device_gate.sh" --drc \
    --apk "$GATE_ROOT/apk/app-release.apk" --mapping "$GATE_ROOT/apk/mapping.txt" 2>&1
}
prepare_gate "$TMP/good.txt"
out=$(run_drc); rc=$?
check "R7 --drc: chặng 1 calibrated armed + brain_decision >= 1; chặng 2 kill 0 sau dòng nạp; chặng 3 rollout_fraction 0 giữ qua cold start (đã nạp, không armed lại); RESULT: PASS (cổng cũ: đối số lạ)" \
  "$(hasnt "$out" 'đối số lạ' && has "$out" 'drc chặng 1: brain_decision=1 (cần >= 1, decision_point=INTERSTITIAL_OPPORTUNITY)' && has "$out" '[thông tin] chạm → hàng brain_decision: ~' && has "$out" 'drc chặng 2: brain_decision sau dòng nạp = 0 (cần 0), quảng cáo đã hiện=1' \
    && has "$out" "drc chặng 3: cold start không extra: đã nạp roll0-r17=1 (cần >= 1), armed=0 (cần 0)" && has "$out" 'Firebase/Remote Config writes by this gate: 0' \
    && has "$out" 'RESULT: PASS' && [ "$rc" -eq 0 ] && hasnt "$(cat "$TMP/adb-calls")" 'shell monkey'; echo $?)" "rc=$rc
$out"
prepare_gate "$TMP/good.txt"
out=$(FAKE_DRC_NODECISION=1 run_drc); rc=$?
prepare_gate "$TMP/good.txt"; r8a=$(FAKE_DRC_KILLLEAK=1 run_drc); r8arc=$?
prepare_gate "$TMP/good.txt"; r8b=$(FAKE_DRC_NOSHOW=1 run_drc); r8brc=$?
prepare_gate "$TMP/good.txt"; r8c=$(FAKE_DRC_REWONLY=1 run_drc); r8crc=$?
check "R8 --drc đối chứng: chặng 1 = 0 brain_decision INTERSTITIAL -> FAIL nêu lý do từ chối (không PASS); cũng FAIL: chỉ có hàng REWARDED_OPPORTUNITY ở chặng 1; kill mà còn hàng brain_decision ở chặng 2; kill mà quảng cáo không hiện (0 rỗng)" \
  "$(has "$out" 'drc chặng 1: brain_decision=0 (cần >= 1, decision_point=INTERSTITIAL_OPPORTUNITY)' && has "$out" 'FAIL: đối chứng: chặng 1 brain_decision=0' && has "$out" 'ad_show_fail:' && has "$out" 'RESULT: FAIL' && hasnt "$out" 'RESULT: PASS' && [ "$rc" -eq 1 ] \
    && has "$r8c" 'FAIL: đối chứng: chặng 1 brain_decision=0' && hasnt "$r8c" 'RESULT: PASS' && [ "$r8crc" -eq 1 ] \
    && has "$r8a" 'FAIL: chặng 2: kill-switch mà vẫn có 1 hàng brain_decision' && hasnt "$r8a" 'RESULT: PASS' && [ "$r8arc" -eq 1 ] \
    && has "$r8b" 'FAIL: chặng 2: quảng cáo không hiện' && hasnt "$r8b" 'RESULT: PASS' && [ "$r8brc" -eq 1 ]; echo $?)" "rc=$rc
$out
KILLLEAK rc=$r8arc: $(printf '%s\n' "$r8a" | tail -8)
NOSHOW rc=$r8brc: $(printf '%s\n' "$r8b" | tail -8)
REWONLY rc=$r8crc: $(printf '%s\n' "$r8c" | tail -8)"

# ── 2.1.0 Task 0d (M06, §E): gate thường — killDueToPackageUpdate của pid app = INVALID (package update); FATAL cùng cửa sổ = FAIL trước ──
# adb bọc: `logcat -d` (toàn bộ buffer) in dòng ActivityManager giết pid app; K2 thêm FATAL EXCEPTION của app. Lượt hỏng nhờ sink 0 hàng (FAKE_SINK_ROWS=0).
k_gate() {   # $1 = có FATAL không (0|1)
  prepare_gate "$TMP/good.txt"; mv "$TMP/bin/adb" "$TMP/bin/adb.fake"
  [ ! -f "$ROOT/tools/lib/invalid_classes.sh" ] || cp "$ROOT/tools/lib/invalid_classes.sh" "$GATE_ROOT/tools/lib/"
  cat > "$TMP/bin/adb" <<'KEOF'
#!/usr/bin/env bash
if [ "$*" = "logcat -d" ]; then
  echo "10-03 06:43:32.972   641   662 I ActivityManager: Killing 4242:test.sdk.zd/u0a231 (adj 0): stop com.google.android.gms due to killDueToPackageUpdate"
  [ "${FAKE_K_FATAL:-0}" != 1 ] || { echo "10-03 06:43:33.001  4242  4242 E AndroidRuntime: FATAL EXCEPTION: main"; echo "10-03 06:43:33.001  4242  4242 E AndroidRuntime: Process: test.sdk.zd, PID: 4242"; }
  exit 0
fi
exec "$(dirname "$0")/adb.fake" "$@"
KEOF
  chmod +x "$TMP/bin/adb"
  FAKE_K_FATAL=$1 FAKE_ADB_FULL=1 FAKE_SINK_ROWS=0 run_gate
}
k1=$(k_gate 0); k1rc=$?
check "T0d K1 gate thường + 'Killing <pid>:test.sdk.zd ... killDueToPackageUpdate' + lượt hỏng -> RESULT: INVALID (package update), rc 4, không FAIL (cũ: FAIL rc 1)" \
  "$([ "$k1rc" -eq 4 ] && has "$k1" 'RESULT: INVALID (package update)' && hasnt "$k1" 'RESULT: FAIL'; echo $?)" "rc=$k1rc
$k1"
k2=$(k_gate 1); k2rc=$?
check "T0d K2 cùng dòng kill + FATAL EXCEPTION của app trong cửa sổ -> RESULT: FAIL, rc 1, không INVALID (FATAL thắng mọi lớp)" \
  "$([ "$k2rc" -eq 1 ] && has "$k2" 'RESULT: FAIL' && hasnt "$k2" 'INVALID'; echo $?)" "rc=$k2rc
$k2"

dg_finish
