# shellcheck shell=bash
# Phần của tools/device_gate.sh chạy không cần máy: die, allowlist, đếm 4 chuỗi R8, đếm StrictMode sau
# retrace, tải máy (log_load, gọi adb_b) và số đo của app. Tách ra để tools/tests/device_gate_allowlist_test.sh gọi được. Người source phải đặt LOG và fail=0.
# Chạy trên bash 3.2 (macOS) và bash của runner Linux, với grep BSD lẫn GNU.
R8_STRINGS="ClassNotFound|NoSuchMethod|NoClassDef|FATAL"
type adb_b > /dev/null 2>&1 || . "$(dirname "${BASH_SOURCE[0]}")/adb_b.sh"   # 1.8.0 Task 2: log_load qua adb_b

# Mọi đường thoát sớm đều in RESULT: FAIL để người đọc log và CI thấy cùng một kết luận.
die() {
  echo "FAIL: $1" | tee -a "$LOG"
  echo "RESULT: FAIL" | tee -a "$LOG"
  exit 1
}

# Regex allowlist phải là ERE hợp lệ và không được khớp dòng rỗng. grep -E trên input rỗng: exit 1 là
# biên dịch được, exit 2 là regex hỏng (BSD grep: 'foo|', ngoặc không đóng). GNU grep nhận 'foo|' và
# khớp mọi dòng, nên kiểm thêm dòng rỗng. Bỏ qua bước này thì grep -vE lúc lọc exit 2 và in rỗng, cổng
# đọc thành 0 dòng còn lại: PASS giả.
check_allow_re() {
  local re=$1 what=$2 err rc
  err=$(grep -E -e "$re" < /dev/null 2>&1 > /dev/null); rc=$?
  [ "$rc" -eq 1 ] || die "allowlist regex không hợp lệ ($what, grep exit=$rc${err:+: $err}): $re"
  if echo | grep -qE -e "$re"; then die "allowlist regex khớp mọi dòng ($what): $re"; fi
}

# Chỉ dòng có `#` ở cột 0 là chú thích, dòng trống bỏ qua, mỗi dòng còn lại là một ERE. Đặt ALLOW_N (số
# regex) và ALLOW_RE (các regex nối bằng |, rỗng khi không có). Thiếu file hay regex hỏng là cấu hình
# hỏng: FAIL, không đoán. Kiểm từng regex (ghép hai regex hỏng có thể ra một regex hợp lệ) rồi kiểm cả
# chuỗi ghép.
load_allowlist() {
  local file=$1 entries rc re
  [ -f "$file" ] || die "không có $file"
  entries=$(grep -v -e '^#' -e '^[[:space:]]*$' "$file"); rc=$?
  [ "$rc" -le 1 ] || die "không đọc được $file (grep exit=$rc)"
  ALLOW_N=0; ALLOW_RE=
  while IFS= read -r re; do
    [ -n "$re" ] || continue
    ALLOW_N=$((ALLOW_N + 1))
    check_allow_re "$re" "regex thứ $ALLOW_N trong $file"
    if [ -z "$ALLOW_RE" ]; then ALLOW_RE=$re; else ALLOW_RE="$ALLOW_RE|$re"; fi
  done <<< "$entries"
  [ -z "$ALLOW_RE" ] || check_allow_re "$ALLOW_RE" "$ALLOW_N regex ghép lại"
}

drop_allowed() { if [ -n "$ALLOW_RE" ]; then grep --text -vE -e "$ALLOW_RE"; else cat; fi; }
keep_allowed() { if [ -n "$ALLOW_RE" ]; then grep --text -E -e "$ALLOW_RE"; else cat > /dev/null; fi; }

# 4 chuỗi R8 trong một app log, trừ dòng khớp allowlist. Điều 7, không giấu gì: in raw/allowlisted,
# chép dòng bị loại vào $LOG, còn dòng nào thì in chúng (tối đa 40) và cổng FAIL.
count_r8() {
  local label=$1 file=$2 note=${3:-} raw residual rc err left
  # Chưa nạp allowlist thì drop_allowed chết vì biến chưa đặt với exit 1, đọc thành "0 dòng còn lại".
  [ -n "${ALLOW_RE+set}" ] || die "count_r8 gọi trước load_allowlist"
  raw=$(grep --text -cE "$R8_STRINGS" "$file")
  case "$raw" in ''|*[!0-9]*) echo "FAIL: không đếm được chuỗi R8 trong $file" | tee -a "$LOG"; fail=1; return;; esac
  # Exit của lần lọc là exit của drop_allowed: 0/1 là lọc xong, 2 là grep lỗi. Khi lỗi stdout rỗng,
  # thành "0 dòng còn lại" và PASS giả, nên dừng cổng (chạy lại để lấy lỗi của grep vào $LOG).
  residual=$(grep --text -E "$R8_STRINGS" "$file" | drop_allowed); rc=$?
  if [ "$rc" -gt 1 ]; then
    err=$(echo | drop_allowed 2>&1 > /dev/null)
    die "lọc allowlist lỗi trên $file (grep exit=$rc${err:+: $err})"
  fi
  left=$(printf '%s' "$residual" | grep --text -c '')
  echo "r8 strings $label=$left (raw=$raw, allowlisted=$((raw - left)); need 0$note)" | tee -a "$LOG"
  [ "$raw" -eq "$left" ] || grep --text -E "$R8_STRINGS" "$file" | keep_allowed | head -40 | sed 's/^/  allowlisted> /' >> "$LOG"
  [ "$left" -eq 0 ] && return
  printf '%s\n' "$residual" | head -40 | sed 's/^/  r8> /' | tee -a "$LOG"
  fail=1
}

# ── StrictMode lúc cold start (G2): đếm frame SDK SAU RETRACE ─────────────────────────────────────────
# Bản release đổi tên và repackage lớp SDK (đo 2026-09-24: 217/221, vd com.app.plugin.X -> za3), nên đếm
# "com.app.plugin" trên log thô chỉ thấy lớp giữ tên và frame của app demo. Retrace bằng mapping của chính
# bản build (R8 `com.android.tools.r8.retrace.Retrace`) rồi mới đếm; số raw vẫn in để so với lần đo cũ.
# RETRACE=required (mặc định): có dòng StrictMode mà thiếu mapping, java hay r8.jar, hoặc retrace lỗi, thì
# cổng FAIL (fail closed, không đoán). RETRACE=off: chỉ in số raw, ghi rõ vào $LOG.
# JAVA / R8_JAR đặt được qua env; mặc định là JDK và r8.jar của Android Studio, rồi $JAVA_HOME / java trên
# PATH và R8 trong cache Gradle (runner Linux: artifact r8, hoặc builder-<AGP>.jar có sẵn R8).
AS_CONTENTS="/Applications/Android Studio.app/Contents"

# Đặt RETRACE_JAVA và RETRACE_JAR; trả 1 khi thiếu một trong hai.
find_retrace_tools() {
  local j
  RETRACE_JAVA=${JAVA:-}
  if [ -z "$RETRACE_JAVA" ]; then
    for j in "$AS_CONTENTS/jbr/Contents/Home/bin/java" "${JAVA_HOME:+$JAVA_HOME/bin/java}"; do
      if [ -n "$j" ] && [ -x "$j" ]; then RETRACE_JAVA=$j; break; fi
    done
    [ -n "$RETRACE_JAVA" ] || RETRACE_JAVA=$(command -v java 2>/dev/null)
  fi
  RETRACE_JAR=${R8_JAR:-}
  if [ -z "$RETRACE_JAR" ]; then
    if [ -f "$AS_CONTENTS/plugins/android/lib/r8.jar" ]; then
      RETRACE_JAR="$AS_CONTENTS/plugins/android/lib/r8.jar"
    else
      # R8 trong cache Gradle: artifact r8 riêng, hoặc gói sẵn trong builder của AGP (AGP 8.x).
      local g=${GRADLE_USER_HOME:-$HOME/.gradle}/caches/modules-2/files-2.1
      RETRACE_JAR=$( { ls -t "$g"/com.android.tools/r8/*/*/r8-*.jar
                       ls -t "$g"/com.android.tools.build/builder/*/*/builder-*.jar; } 2>/dev/null \
        | grep -v -e '-sources\.jar$' | head -1)
    fi
  fi
  [ -n "$RETRACE_JAVA" ] && [ -x "$RETRACE_JAVA" ] && [ -n "$RETRACE_JAR" ] && [ -f "$RETRACE_JAR" ]
}

# Đếm trên stack đã retrace (stdin, không còn tiền tố logcat): tổng vi phạm; vi phạm có frame SDK
# (com.app.plugin.*, trừ .demo); vi phạm DO SDK — frame app đầu tiên (từ trên xuống) là SDK và không phải
# lời gọi super.onCreate() (frame ngay trên là android.app.Application.onCreate: việc đĩa của framework);
# số frame SDK; trong số do SDK, số vi phạm là lần nạp SharedPreferences đầu (stack có SharedPreferencesImpl
# hay getSharedPreferences; đếm riêng để theo dõi, vẫn tính vào cổng từ Task B — PrefsWarmup nạp sẵn prefs trên
# luồng nền). In "tổng có-SDK do-SDK frame-SDK do-SDK-prefs"; stack của vi phạm do SDK (đánh dấu
# [prefs first load] / [other]) ghi vào file dump.
STRICT_AWK='
function finish() {
  total++; if (any) withsdk++
  if (first != "sdk") return
  if (miui) { envn++; printf "[env MIUI] %s", buf > dump; return }   # 2.4.0 CARRY-T #19 (D34-230): khung com.miui.server.* nằm TRÊN khung SDK đầu tiên = môi trường, không phải mã SDK
  bysdk++; kind = "[other] "
  if (buf ~ /SharedPreferencesImpl|getSharedPreferences/) { prefs++; kind = "[prefs first load] " }
  printf "%s%s", kind, buf > dump
}
/StrictMode policy violation/ { if (inv) finish(); inv = 1; first = ""; prev = ""; any = 0; miui = 0; buf = $0 "\n"; next }
inv { buf = buf $0 "\n" }
inv && /^[ \t]*at / {
  f = $0; sub(/^[ \t]*at /, "", f)
  if (first == "" && f ~ /^com\.miui\.server\./) miui = 1
  if (first == "" && f ~ /^com\.app\.plugin\./) {
    if (f ~ /^com\.app\.plugin\.demo\./) first = "demo"
    else if (prev ~ /^android\.app\.Application\.onCreate\(/) first = "super"
    else first = "sdk"
  }
  if (f ~ /^com\.app\.plugin\./ && f !~ /^com\.app\.plugin\.demo\./) { frames++; any = 1 }
  prev = f
}
END { if (inv) finish(); printf "%d %d %d %d %d %d\n", total, withsdk, bysdk, frames, prefs, envn }
'

# $1 = nhãn, $2 = app log (logcat), $3 = mapping.txt. FAIL (fail=1) khi cần retrace mà không làm được.
count_strict() {
  local label=$1 file=$2 map=$3 raw demo work rc err counts total withsdk bysdk frames prefs other envn
  raw=$(grep --text -A14 "StrictMode" "$file" | grep --text -c "com.app.plugin")
  demo=$(grep --text -A14 "StrictMode" "$file" | grep --text -c "com.app.plugin.demo")
  echo "strictmode frames com.app.plugin=$raw (raw, before retrace; demo=$demo)" | tee -a "$LOG"
  work=$(mktemp -d "${TMPDIR:-/tmp}/device-gate-strict.XXXXXX") || {
    echo "FAIL: mktemp cho retrace StrictMode" | tee -a "$LOG"; fail=1; return; }
  # Chỉ dòng tag StrictMode, bỏ tiền tố logcat (ngày giờ, pid, tid, mức) trước dấu " StrictMode: " đầu tiên.
  awk '{ i = index($0, " StrictMode: "); if (i) print substr($0, i + 13) }' "$file" > "$work/in.txt"
  if [ ! -s "$work/in.txt" ]; then
    echo "strictmode $label: violations=0 (no StrictMode line; retrace not needed)" | tee -a "$LOG"
    rm -rf "$work"; return
  fi
  if [ "${RETRACE:-required}" = off ]; then
    echo "warn: RETRACE=off — StrictMode chưa retrace, chỉ có số raw ở trên (lớp SDK bị R8 đổi tên)" | tee -a "$LOG"
    rm -rf "$work"; return
  fi
  if [ ! -f "$map" ]; then
    echo "FAIL: StrictMode cần retrace nhưng không có mapping $map (RETRACE=off để chỉ in số raw)" | tee -a "$LOG"
    fail=1; rm -rf "$work"; return
  fi
  if ! find_retrace_tools; then
    echo "FAIL: StrictMode cần retrace nhưng không có java/r8.jar (java='$RETRACE_JAVA', r8.jar='$RETRACE_JAR'; đặt JAVA, R8_JAR, hoặc RETRACE=off để chỉ in số raw)" | tee -a "$LOG"
    fail=1; rm -rf "$work"; return
  fi
  "$RETRACE_JAVA" -cp "$RETRACE_JAR" com.android.tools.r8.retrace.Retrace "$map" "$work/in.txt" \
    > "$work/out.txt" 2> "$work/err.txt"; rc=$?
  if [ "$rc" -ne 0 ] || [ ! -s "$work/out.txt" ]; then
    err=$(head -5 "$work/err.txt")
    echo "FAIL: retrace StrictMode lỗi (exit=$rc${err:+: $err})" | tee -a "$LOG"
    fail=1; rm -rf "$work"; return
  fi
  counts=$(awk -v dump="$work/sdk.txt" "$STRICT_AWK" "$work/out.txt")
  read -r total withsdk bysdk frames prefs envn <<< "$counts"
  case "$total$withsdk$bysdk$frames$prefs$envn" in ''|*[!0-9]*)
    echo "FAIL: không đếm được StrictMode sau retrace ($counts)" | tee -a "$LOG"
    fail=1; rm -rf "$work"; return;;
  esac
  other=$((bysdk - prefs))
  echo "strictmode $label: violations=$total, with sdk frame=$withsdk, by sdk=$bysdk (prefs first load=$prefs," \
    "other=$other; retraced with $map; by sdk = first app frame is SDK, not Application.super.onCreate)," \
    "sdk frames=$frames" | tee -a "$LOG"
  echo "strictmode gate $label: by sdk=$bysdk (need 0; 1.4.0: mọi vi phạm do SDK phải = 0, kể cả lần nạp" \
    "SharedPreferences đầu: prefs first load=$prefs, other=$other)" | tee -a "$LOG"
  [ "$envn" -eq 0 ] || echo "by-sdk: $bysdk (+$envn env MIUI)" | tee -a "$LOG"
  [ ! -s "$work/sdk.txt" ] || head -400 "$work/sdk.txt" | sed 's/^/  strict> /' >> "$LOG"
  if [ "$bysdk" -gt 0 ]; then
    echo "FAIL: StrictMode: $bysdk vi phạm do SDK (prefs first load=$prefs, other=$other; cần 0 — 1.4.0: mọi" \
      "vi phạm do SDK phải = 0; stack trong $LOG)" | tee -a "$LOG"
    fail=1
  fi
  rm -rf "$work"
}

# Chặng cold start 2 (1.6.1 Task 3): AppsFlyer có chạy trong cửa sổ đo không. 0 = G2 không đo đường af.init
# (APK không có af_app_id, hoặc cổng analytics không mở). $1 = nhãn, $2 = app log; AF_STARTED_MIN (mặc định 1; 0 = tắt).
count_af_started() {
  local label=$1 file=$2 n min=${AF_STARTED_MIN:-1}
  n=$(grep --text -c "appsflyer started after consent" "$file")
  echo "appsflyer started $label: n=$n (need >= $min; đường af.init chỉ được đo khi n >= 1)" | tee -a "$LOG"
  [ "$n" -ge "$min" ] || { echo "FAIL: AppsFlyer không start trong chặng $label (n=$n < $min): G2 không đo đường af.init — APK thiếu af_app_id? hoặc UMP không NOT_REQUIRED (máy ở EEA?)? đặt AF_STARTED_MIN=0 để bỏ kiểm" | tee -a "$LOG"; fail=1; }
}

# 1.6.2 Task 2 (bài học 1.6.1 Task R: am_anr giả khi host quá tải): tải host (uptime) và máy (/proc/loadavg) trước chặng.
log_load() {
  local dev
  dev=$(adb_b 60 shell cat /proc/loadavg 2>/dev/null | tr -d '\r')
  [ -n "$dev" ] || dev="<unreadable>"
  echo "load $1: host=$(uptime | sed 's/.*load averages*: //') device=$dev" | tee -a "$LOG"
}

# 1.6.2 Task 2 (review 1.6.1 T3 m9): StrictMode của app demo đã bật chưa. 0 dòng "strictmode armed" = chặng đó không đo
# G2 ("by sdk=0" vì không bắn, không phải vì sạch). STRICT_ARMED_MIN (mặc định 1; 0 = bỏ kiểm, ghi lý do vào log lane).
count_strict_armed() {
  local label=$1 file=$2 n min=${STRICT_ARMED_MIN:-1}
  n=$(grep --text -c "strictmode armed" "$file")
  echo "strictmode armed $label: n=$n (need >= $min)" | tee -a "$LOG"
  [ "$n" -ge "$min" ] || { echo "FAIL: StrictMode chưa bật trong chặng $label (n=$n < $min): by sdk=0 của chặng này không phải bằng chứng G2 — APK không bật flagTest, hoặc APK chưa có canary? đặt STRICT_ARMED_MIN=0 để bỏ kiểm" | tee -a "$LOG"; fail=1; }
}

# 1.6.2 Task 2 (review 1.6.1 T3 n1): chép dòng số đo của app vào $LOG và in một dòng tóm tắt. Chỉ ghi, không FAIL.
KEY_LINES="prefs warmup|AppPluginBase.onCreate main-thread ms=|initPlugin ms:|appsflyer started after consent|strictmode armed|telemetry off"
log_key_lines() { # $1 = chặng, $2 = log thô, $3 = TotalTime ms của `am start -W` (rỗng = none)
  local label=$1 file=$2 total=${3:-} waited timeouts ms optional init
  grep --text -E "$KEY_LINES" "$file" | head -20 | sed "s/^/  key[$label]> /" >> "$LOG"
  waited=$(grep --text -o 'prefs warmup waited=[0-9]*' "$file" | head -1 | sed 's/.*=//')
  timeouts=$(grep --text -c 'prefs warmup timeout' "$file")
  ms=$(grep --text 'AppPluginBase.onCreate main-thread ms=' "$file" | grep -o 'main-thread ms=[0-9]*' | head -1 | sed 's/.*=//')
  # Dòng "prefs warmup loaded: base=… ms, optional=… ms" của luồng warm-up (1.6.2 Task 1): số quyết Task 1b.
  optional=$(grep --text 'prefs warmup loaded:' "$file" | grep -o 'optional=[0-9]*' | head -1 | sed 's/.*=//')
  # 1.6.3 Task 2: dòng `initPlugin ms: …` của Task 1 (tag AppPlugin, không lấy của tag khác) và TotalTime của am start -W.
  init=$(grep --text 'D AppPlugin: initPlugin ms: ' "$file" | head -1 | sed 's/.*initPlugin ms: //' | tr -d '\r')
  echo "metrics $label: prefs warmup waited=${waited:-none} ms, timeouts=$timeouts, onCreate main-thread ms=${ms:-none}, optional prefs ms=${optional:-none}, initPlugin ms=${init:-none}, TotalTime ms=${total:-none}" | tee -a "$LOG"
}
