#!/usr/bin/env bash
# Test không cần máy của cổng thiết bị — vùng release: --apk/--mapping, tiền kiểm Docker + sink, cổng lớp vắng, CI ghim, meta-data release gate (D7).
# Chạy: bash tools/tests/dg_release_test.sh (hoặc qua runner device_gate_allowlist_test.sh). exit 0 = xanh.
. "$(dirname "$0")/lib/dg_harness.sh" || exit 99

# ── 1.7.0 Task 8: --apk/--mapping bắt buộc, tiền kiểm Docker + sink, adb reverse, dòng 1 in sha256/bytes, quiet_host ──
prepare_gate "$TMP/good.txt"
out=$(FAKE_SINK=down run_gate); rc=$?
check "sink-missing: sink không chạy -> FAIL nêu sink, RESULT: FAIL, exit 1, 0 lệnh adb" \
  "$(has "$out" 'FAIL: sink' && has "$out" 'RESULT: FAIL' && [ "$rc" -eq 1 ] && [ ! -s "$TMP/adb-calls" ]; echo $?)" "$out
exit=$rc; adb calls: $(cat "$TMP/adb-calls")"
prepare_gate "$TMP/good.txt"
out=$(FAKE_DOCKER=1 run_gate); rc=$?
check "docker-running: Docker chạy -> FAIL nêu Docker, RESULT: FAIL, exit 1, 0 lệnh adb" \
  "$(has "$out" 'FAIL: Docker' && has "$out" 'RESULT: FAIL' && [ "$rc" -eq 1 ] && [ ! -s "$TMP/adb-calls" ]; echo $?)" "$out
exit=$rc; adb calls: $(cat "$TMP/adb-calls")"
prepare_gate "$TMP/good.txt"
out=$(FAKE_ADB_FULL=1 run_gate)
check "reverse-on: adb reverse tcp:8089 tcp:8089 có trong \$ADB_CALLS" \
  "$(has "$(cat "$TMP/adb-calls")" 'reverse tcp:8089 tcp:8089'; echo $?)" "$out
adb calls: $(cat "$TMP/adb-calls")"
check "reverse-off: adb reverse --remove tcp:8089 là lệnh adb cuối cùng" \
  "$([ "$(tail -1 "$TMP/adb-calls")" = 'reverse --remove tcp:8089' ]; echo $?)" "$(tail -3 "$TMP/adb-calls")"
prepare_gate "$TMP/good.txt"
out=$(RAW_ARGS=1 run_gate); rc=$?
check "apk-missing: thiếu --apk -> FAIL nêu --apk, RESULT: FAIL, exit 1, 0 lệnh adb" \
  "$(has "$out" 'FAIL: --apk' && has "$out" 'RESULT: FAIL' && [ "$rc" -eq 1 ] && [ ! -s "$TMP/adb-calls" ]; echo $?)" "$out
exit=$rc; adb calls: $(cat "$TMP/adb-calls")"
prepare_gate "$TMP/good.txt"
out=$(run_gate --apk "$TMP/khong-co.apk" --mapping "$GATE_ROOT/apk/mapping.txt"); rc=$?
check "apk-nofile: --apk trỏ file không có -> FAIL, RESULT: FAIL, exit 1, 0 lệnh adb" \
  "$(has "$out" 'FAIL: --apk' && has "$out" 'RESULT: FAIL' && [ "$rc" -eq 1 ] && [ ! -s "$TMP/adb-calls" ]; echo $?)" "$out
exit=$rc; adb calls: $(cat "$TMP/adb-calls")"
prepare_gate "$TMP/good.txt"
out=$(FAKE_ADB_FULL=1 run_gate)
line1=$(gate_log | head -1)
want_sha=$(shasum -a 256 "$GATE_ROOT/apk/app-release.apk" | cut -d' ' -f1)
want_bytes=$(wc -c < "$GATE_ROOT/apk/app-release.apk" | tr -d ' ')
want_msha=$(shasum -a 256 "$GATE_ROOT/apk/mapping.txt" | cut -d' ' -f1)
check "line1-sha-bytes: dòng 1 của \$LOG có sha256=<64 hex> + bytes=<wc -c> của APK và mapping sha256" \
  "$(has "$line1" "apk=$GATE_ROOT/apk/app-release.apk sha256=$want_sha bytes=$want_bytes" && has "$line1" "mapping sha256=$want_msha"; echo $?)" "$line1"
# ── 1.8.0 T3 (G2, D16): cổng lớp vắng dạng cố định trên fixture dex nhỏ ─────────────────────────────────────────
# tools/tests/fixtures/absent_class.apk = classes.dex có com.app.plugin.Main (tham chiếu com.app.plugin.Missing, KHÔNG định nghĩa,
# và com.app.plugin.Defined, có định nghĩa); nguồn Main ở absent_class.Main.java.txt. Dựng bằng javac + d8 (build-tools 37.0.0).
AC_APK=$ROOT/tools/tests/fixtures/absent_class.apk
AC_MAP=$ROOT/tools/lib/release_gate_map.sh
ac_run() { # $1 = apk, $2 = allowlist, $3 = variant (mặc định utility). Đặt out, rc (như cổng: fail=1 -> rc 1)
  out=$( (LOG=$TMP/ac.log; fail=0; . "$AC_MAP" || exit 99; check_absent_classes "$1" "$2" ${3:-}; exit "$fail") 2>&1 ); rc=$?
}
printf '# rỗng\n' > "$TMP/ac-empty.txt"
printf 'com.app.plugin.Missing  # chủ: fixture\n' > "$TMP/ac-one.txt"
printf 'com.app.plugin.Missing  # chủ: fixture\ncom.app.plugin.Missing$Inner  # chủ: fixture\ncom.app.plugin.MissingIface  # chủ: fixture\n' > "$TMP/ac-ok.txt"
ac_run "$AC_APK" "$TMP/ac-one.txt"
check "G2 release_gate_map: dex tham chiếu Missing (trường+invoke), Missing\$Inner[] (new-array), MissingIface (implements) không định nghĩa; allowlist chỉ có Missing -> FAIL sdk=2 allow=1, Defined/java.* không bị kể" \
  "$([ "$rc" -eq 1 ] && has "$out" 'absent-class sdk=2 allow=1' && has "$out" 'absent> com.app.plugin.Missing$Inner' && has "$out" 'absent> com.app.plugin.MissingIface' && ! has "$out" 'Defined' && ! has "$out" 'java.lang'; echo $?)" "rc=$rc
$out"
ac_run "$AC_APK" "$TMP/ac-ok.txt"
check "G2 release_gate_map: thêm cả ba vào allowlist -> PASS sdk=0 allow=3" \
  "$([ "$rc" -eq 0 ] && has "$out" 'absent-class sdk=0 allow=3'; echo $?)" "rc=$rc
$out"
ac_run "$AC_APK" "$TMP/khong-co.txt"
check "G2 release_gate_map: thiếu allowlist -> FAIL (fail-closed, không PASS rỗng)" \
  "$([ "$rc" -eq 1 ] && has "$out" 'không có allowlist'; echo $?)" "rc=$rc
$out"
ac_run "$AC_APK" "$TMP/ac-empty.txt"
check "G2 release_gate_map: allowlist rỗng (chỉ comment) -> FAIL (fail-closed)" \
  "$([ "$rc" -eq 1 ] && has "$out" 'allowlist rỗng'; echo $?)" "rc=$rc
$out"
ac_run "$TMP/khong-co.apk" "$TMP/ac-ok.txt"
check "G2 release_gate_map: thiếu APK -> FAIL (fail-closed)" \
  "$([ "$rc" -eq 1 ] && has "$out" 'không có APK'; echo $?)" "rc=$rc
$out"
ac_run "$AC_APK" "$TMP/ac-one.txt" game
check "G4 release_gate_map: variant game với kiểu vắng ngoài allowlist -> PASS, chỉ in 'absent-class game info=2' (D23)" \
  "$([ "$rc" -eq 0 ] && has "$out" 'absent-class game info=2' && ! has "$out" 'FAIL'; echo $?)" "rc=$rc
$out"
ac_run "$AC_APK" "$TMP/khong-co.txt" game
check "G4 release_gate_map: variant game thiếu allowlist -> vẫn không FAIL (info=unavailable)" \
  "$([ "$rc" -eq 0 ] && has "$out" 'absent-class game info=unavailable'; echo $?)" "rc=$rc
$out"
ac_run "$AC_APK" "$TMP/ac-one.txt" utility
check "G4 release_gate_map: variant utility với kiểu vắng ngoài allowlist -> FAIL" \
  "$([ "$rc" -eq 1 ] && has "$out" 'absent-class sdk=2'; echo $?)" "rc=$rc
$out"
out=$( (DEXDUMP=$TMP/khong-co-dexdump; export DEXDUMP; LOG=$TMP/ac.log; fail=0; . "$AC_MAP" || exit 99; check_absent_classes "$AC_APK" "$TMP/ac-ok.txt"; exit "$fail") 2>&1 ); rc=$?
check "G2 release_gate_map: biến DEXDUMP bị bỏ qua (chỉ dùng build-tools đã phân giải) -> vẫn PASS sdk=0" \
  "$([ "$rc" -eq 0 ] && has "$out" 'absent-class sdk=0 allow=3'; echo $?)" "rc=$rc
$out"
check "G2 allowlist thật: không có dòng com.app.plugin.* (lớp của SDK vắng luôn là lỗi, không được cho qua)" \
  "$([ -s "$ROOT/tools/absent_class_allowlist.txt" ] && ! sed -e 's/#.*//' -e 's/[[:space:]]//g' "$ROOT/tools/absent_class_allowlist.txt" | command grep -q '^com\.app\.plugin\.'; echo $?)" "$(command grep -n 'com.app.plugin' "$ROOT/tools/absent_class_allowlist.txt")"

# ── 2.4.0 NOCI (D33, thay G3 cũ): không còn GitHub Actions; release chạy local bằng tools/release_local.sh ──────────
n_yml=$(ls "$ROOT"/.github/workflows/*.yml 2>/dev/null | wc -l | tr -d ' ')
check "G3 NOCI: .github/workflows không còn *.yml (n=$n_yml); tools/release_local.sh có và bash -n sạch" \
  "$([ "$n_yml" -eq 0 ] && [ -f "$ROOT/tools/release_local.sh" ] && bash -n "$ROOT/tools/release_local.sh"; echo $?)" "yml=$n_yml"

nl() { wc -l < "$ROOT/$1" | tr -d ' '; }
check "G3b trần file NOCI: tools/release_local.sh <= 160 ($(nl tools/release_local.sh)), tools/tests/dg_release_local_test.sh <= 120 ($(nl tools/tests/dg_release_local_test.sh))" \
  "$([ "$(nl tools/release_local.sh)" -le 160 ] && [ "$(nl tools/tests/dg_release_local_test.sh)" -le 120 ]; echo $?)" "caps"

# D7: release gate đếm meta-data com.app.plugin.BRAIN_HARNESS (và IAP_HARNESS) trong manifest của APK: utility 1 / game 0 (D28). aapt2 giả in
# FAKE_HM dòng 'A: …name…="com.app.plugin.<tên>"' (một dòng cho mỗi meta-data như aapt2 thật); lệch hoặc không đọc được → fail=1 (fail-closed).
cat > "$TMP/aapt2-fake" <<'EOF'
#!/usr/bin/env bash
i=0; while [ "$i" -lt "${FAKE_HM:-0}" ]; do i=$((i + 1)); echo "      A: http://schemas.android.com/apk/res/android:name(0x01010003)=\"com.app.plugin.$FAKE_HM_NAME\" (Raw: \"com.app.plugin.$FAKE_HM_NAME\")"; done
[ "${FAKE_HM_RC:-0}" -eq 0 ] || { echo boom >&2; exit "$FAKE_HM_RC"; }
EOF
chmod +x "$TMP/aapt2-fake"
d7() { # $1 variant, $2 số dòng, $3 tên, $4 rc aapt2, $5 đường aapt2 (mặc định giả)
  ( cd "$ROOT"; export FAKE_HM=$2 FAKE_HM_NAME=$3 FAKE_HM_RC=${4:-0}; LOG=$TMP/d7.log; : > "$LOG"; fail=0
    . tools/lib/harness_meta.sh || exit 99
    check_harness_meta "$TMP/fake.apk" "$1" "${5:-$TMP/aapt2-fake}" "$3"; echo "fail=$fail" ) 2>&1
}
d7a=$(d7 utility 1 BRAIN_HARNESS); d7b=$(d7 utility 0 BRAIN_HARNESS); d7c=$(d7 game 0 BRAIN_HARNESS); d7d=$(d7 game 1 BRAIN_HARNESS)
d7e=$(d7 utility 1 BRAIN_HARNESS 3); d7f=$(d7 utility 1 BRAIN_HARNESS 0 "$TMP/khong-co-aapt2"); d7g=$(d7 utility 1 IAP_HARNESS)
d7src=$(command grep -c 'check_harness_meta .* BRAIN_HARNESS' "$ROOT/tools/release_gate.sh")
check "D7 meta-data BRAIN_HARNESS: utility 1 → fail=0, utility 0 → FAIL, game 0 → fail=0, game 1 → FAIL, aapt2 lỗi hoặc vắng → FAIL; IAP_HARNESS đếm cùng cách; release_gate.sh gọi cho BRAIN_HARNESS" \
  "$(has "$d7a" 'BRAIN_HARNESS=1 (need 1)' && has "$d7a" 'fail=0' && has "$d7b" 'BRAIN_HARNESS=0 (need 1)' && has "$d7b" 'fail=1' && has "$d7c" 'fail=0' && has "$d7d" 'fail=1' \
    && has "$d7e" 'fail=1' && has "$d7f" 'fail=1' && has "$d7g" 'IAP_HARNESS=1 (need 1)' && has "$d7g" 'fail=0' && [ "$d7src" -ge 1 ]; echo $?)" "a: $d7a
b: $d7b
c: $d7c
d: $d7d
e: $d7e
f: $d7f
g: $d7g
src=$d7src"

# ── 2.0.0 Task 0f (D51): partner unity/applovin dựng được với R8 ──
# K1: map partner → nhóm trong app/build.gradle.kts: unity kéo theo com.unity3d.coherence (unity-ads 4.19 tham chiếu nó), applovin có com.applovin.
# K2: consumer-rules.pro có dontwarn com.amazon.privacypass (OMID attestation của adapter applovin). Chống đọc nhầm: dòng phải không phải comment.
k1=$(command grep -E '^ *"unity" to \(' "$ROOT/app/build.gradle.kts"); k1b=$(command grep -E '"applovin" to \(' "$ROOT/app/build.gradle.kts")
check "K1 app/build.gradle.kts: unity -> {com.unity3d.ads, com.unity3d.coherence}; applovin -> com.applovin" \
  "$(has "$k1" '"com.unity3d.ads", "com.unity3d.coherence"' && has "$k1b" 'listOf("com.applovin")'; echo $?)" "unity: $k1
applovin: $k1b"
k2=$(command grep -cE '^-dontwarn com\.amazon\.privacypass\.\*\*$' "$ROOT/consumer-rules.pro")
check "K2 consumer-rules.pro có dòng -dontwarn com.amazon.privacypass.** (không phải comment)" "$([ "$k2" -eq 1 ]; echo $?)" "count=$k2"

# K3 (D50): resource ads_bidding_partners chỉ được sinh khi danh sách partner khác rỗng, và là string-array thật (không phải resValue).
k3=$(cat "$ROOT/app/build.gradle.kts")
check "K3 app/build.gradle.kts: sinh ads_bidding_partners chỉ sau if (biddingPartners.isNotEmpty()) và là string-array" \
  "$(has "$k3" 'if (biddingPartners.isNotEmpty()) androidComponents.onVariants' && has "$k3" 'string-array name=' && hasnt "$k3" 'resValue("array"'; echo $?)" "$(command grep -n 'biddingPartners.isNotEmpty' "$ROOT/app/build.gradle.kts")"

# ── 2.0.0 Task C2b: pom_check.sh (Q1-Q3 POM fixture, Q4 namespace/publish chung; D2, D26) ──
mkpom() { # $1 repo, $2 artifactId, $3 version, $4.. groupId:artifactId[:version[:scope]] của phụ thuộc (scope mặc định compile = `api`; kotlin-stdlib luôn có: AGP tự thêm)
  local d=$1/com/app/plugin/$2/$3 r=$1 a=$2 v=$3; shift 3; mkdir -p "$d"
  { echo "<project><groupId>com.app.plugin</groupId><artifactId>$a</artifactId>"; echo "  <version>$v</version><dependencies>"
    echo "<dependency><groupId>org.jetbrains.kotlin</groupId><artifactId>kotlin-stdlib</artifactId><version>2.2.10</version><scope>compile</scope></dependency>"
    for x in "$@"; do IFS=: read -r g n ver sc <<< "$x"; echo "<dependency><groupId>$g</groupId><artifactId>$n</artifactId><version>${ver:-1}</version><scope>${sc:-compile}</scope></dependency>"; done
    echo "</dependencies></project>"; } > "$d/$a-$v.pom"
}
PC() { bash "$ROOT/tools/pom_check.sh" "$@" 2>&1; }
good=$TMP/pc-good; mkpom "$good" appplugin 2.0.0 com.google.android.gms:play-services-ads:25.4.0; mkpom "$good" appplugin-max 2.0.0 com.app.plugin:appplugin:2.0.0 com.applovin:applovin-sdk:13.6.2; mkpom "$good" appplugin-game 2.0.0 com.app.plugin:appplugin:2.0.0
# 2.4.0 FIX-CI1: gốc thật include :appplugin-nextgen -> pom_check Q6 đòi POM nextgen (ads-mobile-sdk + facebook có exclusion GMA; không appplugin/game)
mkdir -p "$good/com/app/plugin/appplugin-nextgen/2.0.0"
printf '%s\n' '<project><artifactId>appplugin-nextgen</artifactId><version>2.0.0</version><dependencies>' \
  '<dependency><groupId>org.jetbrains.kotlin</groupId><artifactId>kotlin-stdlib</artifactId><version>2.2.10</version><scope>compile</scope></dependency>' \
  '<dependency><groupId>com.google.android.libraries.ads.mobile.sdk</groupId><artifactId>ads-mobile-sdk</artifactId><version>1.5.0</version><scope>compile</scope></dependency>' \
  '<dependency><groupId>com.google.ads.mediation</groupId><artifactId>facebook</artifactId><version>6.21.0.4</version><scope>compile</scope><exclusions><exclusion><groupId>com.google.android.gms</groupId><artifactId>play-services-ads</artifactId></exclusion></exclusions></dependency>' \
  '</dependencies></project>' > "$good/com/app/plugin/appplugin-nextgen/2.0.0/appplugin-nextgen-2.0.0.pom"
q1a=$TMP/pc-q1a; cp -R "$good" "$q1a"; mkpom "$q1a" appplugin 2.0.0 com.applovin:applovin-sdk:13.6.2 com.applovin.mediation:google-adapter:25.4.0.0
q1b=$TMP/pc-q1b; cp -R "$good" "$q1b"; mkpom "$q1b" appplugin 2.0.0 com.bigossp:admob-mediation:5.7.0.0
q1c=$TMP/pc-q1c; cp -R "$good" "$q1c"; mkpom "$q1c" appplugin 2.0.0 com.unity3d.ads:unity-ads:4.19.0
oa=$(PC "$q1a"); ra=$?; ob=$(PC "$q1b"); rb=$?; oc=$(PC "$q1c"); rc3=$?
q1d=$TMP/pc-q1d; cp -R "$good" "$q1d"; mkpom "$q1d" appplugin 2.0.0 com.google.ads.mediation:facebook:6.21.0.4 com.google.ads.mediation:chartboost:9.12.1.1
od=$(PC "$q1d"); rd=$?
check "Q1d pom_check: POM appplugin có GMA com.google.ads.mediation:chartboost (group không khớp regex) -> FAIL nêu chartboost; chỉ facebook thì PASS" \
  "$([ "$rd" -eq 1 ] && has "$od" 'adapter GMA game: chartboost' && ! has "$(printf '%s\n' "$od" | command grep 'FAIL: Q1')" 'facebook'; echo $?)" "$od"
check "Q1 pom_check: POM appplugin có phụ thuộc nhóm game (com.applovin x2 / com.bigossp / com.unity3d) -> FAIL, RESULT: FAIL, exit 1" \
  "$([ "$ra" -eq 1 ] && [ "$rb" -eq 1 ] && [ "$rc3" -eq 1 ] && has "$oa" 'có 2 phụ thuộc nhóm game' && has "$ob" 'com.bigossp' && has "$oc" 'com.unity3d' && has "$oa" 'RESULT: FAIL'; echo $?)" "a: $oa
b: $ob
c: $oc"
o=$(PC "$good"); r=$?
check "Q2 pom_check: POM sạch (chỉ kotlin-stdlib do AGP thêm + GMA) và -max/-game đúng -> PASS, exit 0; repo vắng/POM vắng -> FAIL" \
  "$([ "$r" -eq 0 ] && has "$o" 'RESULT: PASS' && has "$o" 'Q1 appplugin: 0'; echo $?)$(PC "$TMP/khong-co-repo" >/dev/null; [ $? -eq 1 ]; echo $?)$(mkdir -p "$TMP/pc-empty"; PC "$TMP/pc-empty" >/dev/null; [ $? -eq 1 ]; echo $?)" "$o"
q3a=$TMP/pc-q3a; cp -R "$good" "$q3a"; rm -rf "$q3a/com/app/plugin/appplugin-game"; mkpom "$q3a" appplugin-game 2.0.0 com.app.plugin:appplugin:1.9.0
q3b=$TMP/pc-q3b; cp -R "$good" "$q3b"; rm -rf "$q3b/com/app/plugin/appplugin-max"; mkpom "$q3b" appplugin-max 2.0.0 com.applovin:applovin-sdk:13.6.2
oa=$(PC "$q3a"); ra=$?; ob=$(PC "$q3b"); rb=$?
check "Q3 pom_check: -game phụ thuộc appplugin khác bản, -max thiếu appplugin -> FAIL nêu artifact" \
  "$([ "$ra" -eq 1 ] && [ "$rb" -eq 1 ] && has "$oa" 'POM appplugin-game không phụ thuộc' && has "$ob" 'POM appplugin-max không phụ thuộc'; echo $?)" "a: $oa
b: $ob"
mkroot() { mkdir -p "$1/appplugin-game" "$1/appplugin-max" "$1/gradle"; cp "$ROOT/build.gradle.kts" "$1/"; cp "$ROOT/gradle/publish.gradle" "$1/gradle/"; cp "$ROOT/appplugin-game/build.gradle.kts" "$1/appplugin-game/"; cp "$ROOT/appplugin-max/build.gradle.kts" "$1/appplugin-max/"; }
r4=$TMP/q4-ok; mkroot "$r4"; r4g=$TMP/q4-game; mkroot "$r4g"; sed -i.bak 's/namespace = "com.app.plugin.game"/namespace = "com.app.plugin"/' "$r4g/appplugin-game/build.gradle.kts"
r4m=$TMP/q4-ver; mkroot "$r4m"; sed -i.bak 's/version = rootProject.ext.sdkVersion/version = "9.9.9"/' "$r4m/gradle/publish.gradle"
o4=$(PC "$good" --root "$r4"); a4=$?; o4g=$(PC "$good" --root "$r4g"); b4=$?; o4m=$(PC "$good" --root "$r4m"); c4=$?
check "Q4 (D26) namespace + publish chung: cây thật PASS; namespace -game = com.app.plugin -> FAIL; version publication lệch sdkVersion -> FAIL" \
  "$([ "$a4" -eq 0 ] && [ "$b4" -eq 1 ] && [ "$c4" -eq 1 ] && has "$o4g" 'namespace com.app.plugin.game' && has "$o4m" 'version = sdkVersion gốc'; echo $?)" "ok: $o4
game: $o4g
ver: $o4m"

# ── 2.1.0 Task T0e (M35 m1, m2, m5): PC1 chọn bản mới nhất, Q3s scope compile, Q5 -max khai đúng 13 ──
pc1a=$TMP/pc1a; cp -R "$good" "$pc1a"; mkpom "$pc1a" appplugin 1.9.0 com.applovin:applovin-sdk:13.6.2   # bản CŨ hỏng, bản mới sạch
pc1b=$TMP/pc1b; cp -R "$good" "$pc1b"; rm -rf "$pc1b/com/app/plugin/appplugin/2.0.0"; mkpom "$pc1b" appplugin 2.0.0 com.applovin:applovin-sdk:13.6.2; mkpom "$pc1b" appplugin 1.9.0   # bản MỚI hỏng, bản cũ sạch
o1a=$(PC "$pc1a"); r1a=$?; o1b=$(PC "$pc1b"); r1b=$?
check "T0e PC1 build/repo có hai bản appplugin: bản cũ 1.9.0 hỏng + 2.0.0 sạch -> PASS; 2.0.0 hỏng + 1.9.0 sạch -> FAIL nêu Q1 (chọn bản mới nhất, không phụ thuộc thứ tự find; cũ: head -1)" \
  "$([ "$r1a" -eq 0 ] && has "$o1a" 'RESULT: PASS' && [ "$r1b" -eq 1 ] && has "$o1b" 'Q1 POM appplugin có'; echo $?)" "a rc=$r1a: $o1a
b rc=$r1b: $o1b"
q3s=$TMP/pc-q3s; cp -R "$good" "$q3s"; rm -rf "$q3s/com/app/plugin/appplugin-game"; mkpom "$q3s" appplugin-game 2.0.0 com.app.plugin:appplugin:2.0.0:runtime
o3s=$(PC "$q3s"); r3s=$?
check "T0e Q3s -game phụ thuộc appplugin:2.0.0 cùng bản nhưng scope runtime -> FAIL nêu scope compile (bản đúng PASS)" \
  "$([ "$r3s" -eq 1 ] && has "$o3s" 'POM appplugin-game: phụ thuộc appplugin:2.0.0 không có scope compile' && has "$(PC "$good")" 'Q3 appplugin-game: phụ thuộc com.app.plugin:appplugin:2.0.0 (scope compile)'; echo $?)" "rc=$r3s: $o3s"
r5=$TMP/q5-bad; mkroot "$r5"; command grep -v 'chartboost:9.12.1.1' "$r5/appplugin-max/build.gradle.kts" > "$r5/m.kts"; mv "$r5/m.kts" "$r5/appplugin-max/build.gradle.kts"
o5g=$(PC "$good" --root "$r4"); o5=$(PC "$good" --root "$r5"); r5rc=$?
check "T0e Q5 -max khai đúng 13 group:artifact (9 applovinMaxCoords + 4 api): cây thật in 13; bỏ một api() -> FAIL nêu 12, cần 13" \
  "$([ "$r5rc" -eq 1 ] && has "$o5g" 'Q5: appplugin-max khai 13 (9 applovinMaxCoords + 4 api)' && has "$o5" 'Q5 appplugin-max khai 12 group:artifact (9 + 3), cần 13'; echo $?)" "ok: $o5g
bad rc=$r5rc: $o5"

# ── 2.1.0 T0g (hợp đồng A): api_dump.sh --module, 0 Gradle (gradlew giả luôn lỗi; AD_AAR = AAR dựng sẵn, AD_OUT = file ra) ──
JH=${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}
ADR=$TMP/ad-root; mkdir -p "$ADR/tools" "$ADR/src" "$TMP/ad-cls"; cp "$ROOT/tools/api_dump.sh" "$ADR/tools/"
printf '#!/bin/sh\necho gradlew-gia >&2; exit 1\n' > "$ADR/gradlew"; chmod +x "$ADR/gradlew"
printf 'package fake; public class Hello { public int one() { return 1; } }\n' > "$TMP/ad-cls/Hello.java"
"$JH/bin/javac" -d "$TMP/ad-cls/out" "$TMP/ad-cls/Hello.java" && (cd "$TMP/ad-cls/out" && zip -q -r "$TMP/ad-cls/classes.jar" .)
printf 'khong co lop\n' > "$TMP/ad-cls/note.txt"; zip -q "$TMP/ad-cls/empty-classes.jar" "$TMP/ad-cls/note.txt"
(cd "$TMP/ad-cls" && cp classes.jar full-c.jar && mkdir -p f e && cp classes.jar f/classes.jar && cp empty-classes.jar e/classes.jar \
  && (cd f && zip -q ../full.aar classes.jar) && (cd e && zip -q ../empty.aar classes.jar))
AD() { (cd "$ADR" && AD_AAR=$1 AD_OUT=$2 bash tools/api_dump.sh "${@:3}" 2>&1); }
o1=$(AD "$TMP/ad-cls/full.aar" "$ADR/g.api" --module appplugin-game); r1=$?
o1b=$(AD "$TMP/ad-cls/full.aar" "$ADR/g.api" --module appplugin-game --check); r1b=$?
o1c=$(AD "$TMP/ad-cls/empty.aar" "$ADR/m.api" --module appplugin-max); r1c=$?
o1d=$(AD "$TMP/ad-cls/empty.aar" "$ADR/m.api" --module appplugin-max --check); r1d=$?
check "AD1 api_dump --module appplugin-game: ghi dump co fake.Hello, --check cung dump rc 0; module 0 lop (-max): khong tao file, ghi va --check rc 0 (cu: doi so la -> build Gradle)" \
  "$([ "$r1" -eq 0 ] && [ "$r1b" -eq 0 ] && command grep -q 'class fake.Hello' "$ADR/g.api" && [ "$r1c" -eq 0 ] && [ "$r1d" -eq 0 ] && [ ! -e "$ADR/m.api" ]; echo $?)" "write rc=$r1: $o1
check rc=$r1b: $o1b
max write rc=$r1c: $o1c
max check rc=$r1d: $o1d"
o2=$(AD "$TMP/ad-cls/full.aar" "$ADR/vang.api" --module appplugin-game --check); r2=$?
printf 'cu\n' > "$ADR/co.api"; o2b=$(AD "$TMP/ad-cls/empty.aar" "$ADR/co.api" --module appplugin-max --check); r2b=$?
o2c=$(AD "$TMP/ad-cls/full.aar" "$ADR/vang.api" --module khong-co --check); r2c=$?
check "AD2 module co lop ma vang file dump -> --check rc 1 (fail-closed); module 0 lop ma co file dump -> rc 1; ten module la -> rc 2" \
  "$([ "$r2" -eq 1 ] && has "$o2" 'không có' && [ "$r2b" -eq 1 ] && [ "$r2c" -eq 2 ]; echo $?)" "vang: rc=$r2 $o2
0 lop co file: rc=$r2b $o2b
la: rc=$r2c $o2c"

# ── 2.1.0 G1 (hợp đồng B, F5): keep GameModule chỉ ở appplugin-game; rules gốc giữ nó thì mã game theo vào mọi APK utility ──
KEEP_GM='-keep class com.app.plugin.game.GameModule { public static final com.app.plugin.game.GameModule INSTANCE; public void install(); }'
k1r=$(command grep -c 'com\.app\.plugin\.game\.GameModule' "$ROOT/consumer-rules.pro"); k1g=$(command grep -cxF -- "$KEEP_GM" "$ROOT/appplugin-game/consumer-rules.pro")
check "G1-K1 consumer-rules.pro gốc 0 dòng GameModule (F5), appplugin-game/consumer-rules.pro có đúng 1 dòng keep" \
  "$([ "$k1r" -eq 0 ] && [ "$k1g" -eq 1 ]; echo $?)" "gốc=$k1r game=$k1g"

# ── 2.1.0 SC1 (hợp đồng G, D4): watermark dựng + rule bỏ log SDK tuỳ chọn, KHÔNG ở consumer-rules (cổng đọc dòng logD, census v210/sc1-logd-census.md) ──
w_cr="$ROOT/consumer-rules.pro"; w_sp="$ROOT/docs/integration/appplugin-strip-logs.pro"
w1a=$(command grep -cE '^-keep class com\.app\.plugin\.BuildInfo$' "$w_cr"); w1b=$(command grep -cxF -- '-keepclassmembers class com.app.plugin.BuildInfo { public static java.lang.String ID; }' "$w_cr")
w1c=$(command grep -c 'APPPLUGIN_BUILD_SHA.*providers.exec\|providers.exec' "$ROOT/build.gradle.kts"); w1d=$(command grep -cF 'apply(from = "gradle/build-sha.gradle")' "$ROOT/build.gradle.kts")
w1e=$(command grep -c 'internal object BuildSha {' "$ROOT/gradle/build-sha.gradle"); w1f=$(command grep -c 'APPPLUGIN_BUILD_SHA' "$ROOT/build.gradle.kts")
check "W1 watermark: consumer-rules giữ BuildInfo + trường ID (nguyên dòng), build.gradle.kts giải sha (APPPLUGIN_BUILD_SHA, providers.exec) và áp gradle/build-sha.gradle sinh internal object BuildSha" \
  "$([ "$w1a" -eq 1 ] && [ "$w1b" -eq 1 ] && [ "$w1c" -ge 1 ] && [ "$w1d" -eq 1 ] && [ "$w1e" -eq 1 ] && [ "$w1f" -ge 1 ]; echo $?)" "keep=$w1a members=$w1b exec=$w1c apply=$w1d obj=$w1e env=$w1f"
w2a=$(command grep -c 'assumenosideeffects.*LogUtil' "$w_cr"); w2b=$(command grep -cE '^-assumenosideeffects class com\.app\.plugin\.until\.LogUtil\$Companion \{$' "$w_sp")
w2c=$(command grep -cE '^ *public void log[DW]\(java\.lang\.String\);$' "$w_sp"); w2d=$(command grep -c 'stripSdkLogs' "$ROOT/app/build.gradle.kts")
check "W2 strip log: consumer-rules 0 dòng assumenosideeffects trên LogUtil (D4); file opt-in có rule logD+logW; demo nối file chỉ khi -PstripSdkLogs" \
  "$([ "$w2a" -eq 0 ] && [ "$w2b" -eq 1 ] && [ "$w2c" -eq 2 ] && [ "$w2d" -ge 1 ]; echo $?)" "consumer=$w2a opt-in rule=$w2b methods=$w2c app=$w2d"

# W4 (I1): release_gate.sh xuất APPPLUGIN_BUILD_SHA từ git HEAD, không có git thì giữ giá trị đặt sẵn; build.gradle.kts đọc buildSha → env → git.
w4a=$(command grep -c 'export APPPLUGIN_BUILD_SHA=' "$ROOT/tools/release_gate.sh"); w4b=$(command grep -c 'environmentVariable("APPPLUGIN_BUILD_SHA")' "$ROOT/build.gradle.kts")
check "W4 release_gate.sh xuất APPPLUGIN_BUILD_SHA (có git) / giữ giá trị đặt sẵn; build.gradle.kts đọc buildSha rồi APPPLUGIN_BUILD_SHA" \
  "$([ "$w4a" -eq 1 ] && [ "$w4b" -eq 1 ]; echo $?)" "export=$w4a gradle=$w4b"

check "AL1 allowlist thật: org.apache.http.conn.ConnectTimeoutException có đúng 1 dòng, kèm nguồn Pangle trong chú thích (D17 2.1.0)" \
  "$([ "$(command grep -cE '^org\.apache\.http\.conn\.ConnectTimeoutException  # chủ: .*Pangle' "$ROOT/tools/absent_class_allowlist.txt")" -eq 1 ] && [ "$(command grep -c '^org\.apache\.http\.conn\.ConnectTimeoutException' "$ROOT/tools/absent_class_allowlist.txt")" -eq 1 ]; echo $?)" "$(command grep -n 'ConnectTimeout' "$ROOT/tools/absent_class_allowlist.txt")"

# 2.4.0 NG6 RG1: release_gate.sh --variant nextgen dựng :app:assembleNextgenRelease (gradlew giả luôn lỗi, 0 Gradle thật)
RGR=$TMP/rg-root; mkdir -p "$RGR/tools"; cp "$ROOT/tools/release_gate.sh" "$RGR/tools/"; printf '#!/bin/sh\necho "gradle-gia $*"\nexit 1\n' > "$RGR/gradlew"; chmod +x "$RGR/gradlew"
o=$(bash "$RGR/tools/release_gate.sh" --variant nextgen 2>&1); rc=$?
check "RG1 release_gate.sh --variant nextgen -> chạy task :app:assembleNextgenRelease (không còn bị từ chối)" \
  "$(has "$o" 'task=:app:assembleNextgenRelease' && has "$o" 'gradle-gia :app:assembleNextgenRelease' && hasnt "$o" "nhận 'nextgen'" && [ "$rc" -eq 1 ]; echo $?)" "rc=$rc $o"

dg_finish
