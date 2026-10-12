#!/usr/bin/env bash
# Test không cần máy của công cụ artifact appplugin-nextgen (2.4.0 T0b, D11-240): api_dump --module, api_parity_check, pom_check Q6/Q7, release_local.sh.
# Chạy: bash tools/tests/dg_nextgen_test.sh (hoặc qua runner device_gate_allowlist_test.sh). exit 0 = xanh. Không Gradle; fixture từ api/appplugin.api thật.
. "$(dirname "$0")/lib/dg_harness.sh" || exit 99
FX=$ROOT/tools/tests/fixtures/nextgen

# ── NP1: api_dump.sh --module appplugin-nextgen (AD_AAR/AD_OUT, gradlew giả luôn lỗi) ──
JH=${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}
ADR=$TMP/ad-root; mkdir -p "$ADR/tools" "$TMP/cls"; cp "$ROOT/tools/api_dump.sh" "$ADR/tools/"
printf '#!/bin/sh\necho gradlew-gia >&2; exit 1\n' > "$ADR/gradlew"; chmod +x "$ADR/gradlew"
printf 'package fake; public class Hello { public int one() { return 1; } }\n' > "$TMP/cls/Hello.java"
"$JH/bin/javac" -d "$TMP/cls/out" "$TMP/cls/Hello.java" && (cd "$TMP/cls/out" && zip -q -r "$TMP/cls/classes.jar" .)
printf 'khong co lop\n' > "$TMP/cls/note.txt"; zip -q "$TMP/cls/empty-classes.jar" "$TMP/cls/note.txt"
(cd "$TMP/cls" && mkdir -p f e && cp classes.jar f/ && cp empty-classes.jar e/classes.jar && (cd f && zip -q ../full.aar classes.jar) && (cd e && zip -q ../empty.aar classes.jar))
AD() { (cd "$ADR" && AD_AAR=$1 AD_OUT=$2 bash tools/api_dump.sh "${@:3}" 2>&1; echo "rc=$?"); }
n1=$(AD "$TMP/cls/full.aar" "$ADR/ng.api" --module appplugin-nextgen); n1b=$(AD "$TMP/cls/full.aar" "$ADR/ng.api" --module appplugin-nextgen --check)
n1c=$(AD "$TMP/cls/empty.aar" "$ADR/ng0.api" --module appplugin-nextgen)
check "NP1 api_dump --module appplugin-nextgen: ghi dump có fake.Hello, --check rc 0; AAR 0 lớp = lỗi rc 1 (cũ: 'module lạ' rc 2, api_dump.sh:28)" \
  "$(has "$n1" 'rc=0' && command grep -q 'class fake.Hello' "$ADR/ng.api" && has "$n1b" 'rc=0' && has "$n1c" 'rc=1' && [ ! -e "$ADR/ng0.api" ]; echo $?)" "write: $n1
check: $n1b
0 lớp: $n1c"

# ── NP2–NP6: api_parity_check.sh (PC_* trỏ fixture; nguồn fixture: AdsHelper public, AdsMobPlInters/MainPost internal) ──
PAR() { (cd "$ROOT" && PC_LEGACY=$1 PC_NG=$2 PC_SRC=$FX/src PC_NGSRC=${3:-$TMP/khong-co} bash tools/api_parity_check.sh 2>&1; echo "rc=$?"); }
sed 's/Companion Companion;/Companion CompanionX;/' "$FX/legacy.api" > "$TMP/ng-helper.api"
sed 's/AdsMobPlInters(android.content.Context/AdsMobPlInters(android.content.ContextX/' "$FX/legacy.api" > "$TMP/ng-inters.api"
p2=$(PAR "$FX/legacy.api" "$FX/nextgen.api"); p3=$(PAR "$FX/legacy.api" "$TMP/ng-helper.api"); p4=$(PAR "$FX/legacy.api" "$TMP/ng-inters.api")
p5=$(PAR "$FX/legacy.api" "$TMP/khong-co.api"); p5b=$(PAR "$TMP/khong-co.api" "$FX/nextgen.api"); p6=$(PAR "$FX/legacy.api" "$FX/nextgen-mangled.api")
p5c=$(cd "$ROOT" && PC_LEGACY=$FX/legacy.api PC_NG=$FX/nextgen.api PC_SRC=$TMP/khong-co bash tools/api_parity_check.sh 2>&1; echo "rc=$?")
check "NP2 fixture nextgen = legacy -> 'parity: PASS', rc 0" "$(has "$p2" 'parity: PASS' && has "$p2" 'rc=0'; echo $?)" "$p2"
check "NP3 khối public AdsHelper khác 1 dòng (P1) -> FAIL, rc 1, nêu tên lớp" \
  "$(has "$p3" 'parity: FAIL' && has "$p3" 'com.app.plugin.ads.AdsHelper' && has "$p3" 'rc=1'; echo $?)" "$p3"
check "NP4 khối AdsMobPlInters (internal trong nguồn) khác (P2) -> PASS, ghi 'cho phép, internal'" \
  "$(has "$p4" 'parity: PASS' && has "$p4" 'AdsMobPlInters (cho phép, internal)'; echo $?)" "$p4"
check "NP5 thiếu dump nextgen / legacy / nguồn gốc (P3) -> FAIL, rc 1 (fail-closed)" \
  "$(has "$p5" 'parity: FAIL' && has "$p5" 'rc=1' && has "$p5b" 'parity: FAIL' && has "$p5c" 'parity: FAIL' && has "$p5c" 'rc=1'; echo $?)" "$p5
$p5b
$p5c"
{ cat "$FX/legacy.api"; printf 'public final class com.app.plugin.ads.NewThing {\n  public com.app.plugin.ads.NewThing();\n}\n'; } > "$TMP/ng-new.api"
mkdir -p "$TMP/ngsrc/com/app/plugin/ads"; printf 'package com.app.plugin.ads\n\ninternal class NewThing\n' > "$TMP/ngsrc/com/app/plugin/ads/NewThing.kt"
p7=$(PAR "$FX/legacy.api" "$TMP/ng-new.api"); p7b=$(PAR "$FX/legacy.api" "$TMP/ng-new.api" "$TMP/ngsrc")
check "NP7 khối public MỚI ở nextgen: lớp không internal trong nguồn -> FAIL rc 1 + 'khối public mới'; có 'internal class' ở nguồn nextgen -> PASS" \
  "$(has "$p7" 'FAIL: khối public mới ở nextgen' && has "$p7" 'rc=1' && has "$p7b" 'parity: PASS' && has "$p7b" 'new: com.app.plugin.ads.NewThing (cho phép, internal)'; echo $?)" "$p7
$p7b"
check "NP6 dump nextgen mang \$appplugin_nextgen( thay \$appplugin( -> PASS (chuẩn hoá D7-240)" \
  "$(command grep -q 'appplugin_nextgen(' "$FX/nextgen-mangled.api" && has "$p6" 'parity: PASS'; echo $?)" "$p6"

# ── PQ1–PQ4: pom_check.sh Q6/Q7 trên repo + gốc giả (settings fixture có include; gốc thật cho build.gradle.kts) ──
pomv() { printf '<project>\n  <artifactId>%s</artifactId>\n  <version>2.4.0</version>\n  <dependencies>%s</dependencies>\n</project>\n' "$1" "$2"; }
DEPC='<dependency><groupId>androidx.core</groupId><artifactId>core-ktx</artifactId><version>1.0</version></dependency>'
DEPA='<dependency><groupId>com.app.plugin</groupId><artifactId>appplugin</artifactId><version>2.4.0</version><scope>compile</scope></dependency>'
mkrepo() { # $1 = thư mục repo, $2 = file POM nextgen (rỗng = không có), $3 = phụ thuộc thêm của POM -max
  local m; for m in appplugin appplugin-max appplugin-game appplugin-nextgen; do mkdir -p "$1/com/app/plugin/$m/2.4.0"; done
  pomv appplugin "$DEPC" > "$1/com/app/plugin/appplugin/2.4.0/a.pom"
  pomv appplugin-max "$DEPA${3:-}" > "$1/com/app/plugin/appplugin-max/2.4.0/m.pom"; pomv appplugin-game "$DEPA" > "$1/com/app/plugin/appplugin-game/2.4.0/g.pom"
  if [ -n "$2" ]; then cp "$2" "$1/com/app/plugin/appplugin-nextgen/2.4.0/n.pom"; else rmdir "$1/com/app/plugin/appplugin-nextgen/2.4.0"; fi
}
mkroot() { # $1 = gốc giả, $2 = settings, $3 = 'ng' tạo appplugin-nextgen/build.gradle.kts (đủ phụ thuộc gốc), 'ngthieu' thiếu một dòng
  mkdir -p "$1/gradle" "$1/appplugin-max" "$1/appplugin-game" "$1/appplugin-nextgen"; cp "$2" "$1/settings.gradle.kts"
  cp "$ROOT/build.gradle.kts" "$1/"; cp "$ROOT/gradle/publish.gradle" "$1/gradle/"
  cp "$ROOT/appplugin-max/build.gradle.kts" "$1/appplugin-max/"; cp "$ROOT/appplugin-game/build.gradle.kts" "$1/appplugin-game/"
  [ -n "${3:-}" ] || { rmdir "$1/appplugin-nextgen"; return 0; }
  { printf 'android {\n    namespace = "com.app.plugin"\n}\n'; command grep -E '^\s*(api|implementation)\(' "$ROOT/build.gradle.kts" | command grep -vE 'play-services-ads|facebook:6\.21\.0\.4'
    echo '    api("com.google.android.libraries.ads.mobile.sdk:ads-mobile-sdk:1.5.0")'; echo 'apply(from = "../gradle/publish.gradle")'; } > "$1/appplugin-nextgen/build.gradle.kts"
  if [ "$3" = ngthieu ]; then command grep -v 'retrofit2:retrofit:' "$1/appplugin-nextgen/build.gradle.kts" > "$1/ng.tmp"; mv "$1/ng.tmp" "$1/appplugin-nextgen/build.gradle.kts"; fi
}
PQ() { (cd "$ROOT" && bash tools/pom_check.sh "$1" --root "$2" 2>&1; echo "rc=$?"); }
command grep -v nextgen "$FX/settings.gradle.kts" > "$TMP/settings-no.kts"
mkroot "$TMP/r-ng" "$FX/settings.gradle.kts" ng; mkroot "$TMP/r-no" "$TMP/settings-no.kts"; mkroot "$TMP/r-thieu" "$FX/settings.gradle.kts" ngthieu
mkrepo "$TMP/repo-gms" "$FX/pom-gms.pom"; mkrepo "$TMP/repo-good" "$FX/pom-good.pom"; mkrepo "$TMP/repo-none" ""; mkrepo "$TMP/repo-core" "$FX/pom-core.pom"
tr -d '\n' < "$FX/pom-good.pom" | sed 's/<exclusions>.*<\/exclusions>//' > "$TMP/pom-noexcl.pom"; mkrepo "$TMP/repo-noexcl" "$TMP/pom-noexcl.pom"
mkrepo "$TMP/repo-q7" "$FX/pom-good.pom" '<dependency><groupId>com.app.plugin</groupId><artifactId>appplugin-nextgen</artifactId><version>2.4.0</version></dependency>'
q1=$(PQ "$TMP/repo-gms" "$TMP/r-ng"); q1g=$(PQ "$TMP/repo-good" "$TMP/r-ng"); q1n=$(PQ "$TMP/repo-none" "$TMP/r-ng"); q1t=$(PQ "$TMP/repo-good" "$TMP/r-thieu")
q2=$(PQ "$TMP/repo-none" "$TMP/r-no"); q3=$(PQ "$TMP/repo-core" "$TMP/r-ng"); q3b=$(PQ "$TMP/repo-q7" "$TMP/r-ng"); q4=$(PQ "$TMP/repo-noexcl" "$TMP/r-ng")
check "PQ1 settings có include + POM nextgen có play-services-ads -> FAIL Q6; POM đúng -> PASS; include thiếu POM -> FAIL; build nextgen thiếu phụ thuộc gốc -> FAIL Q6" \
  "$(has "$q1" 'FAIL: Q6 POM nextgen có phụ thuộc play-services-ads' && has "$q1" 'RESULT: FAIL' && has "$q1g" 'RESULT: PASS' && has "$q1n" 'thiếu POM appplugin-nextgen' && has "$q1t" 'FAIL: Q4 Q6 nextgen thiếu'; echo $?)" "$q1
$q1g
$q1n
$q1t"
check "PQ2 settings không include nextgen, không POM nextgen -> bỏ qua Q6/Q7, RESULT PASS như cũ" \
  "$(has "$q2" 'RESULT: PASS' && hasnt "$q2" 'Q6'; echo $?)" "$q2"
check "PQ3 POM nextgen phụ thuộc com.app.plugin:appplugin -> FAIL Q6; POM -max nhắc appplugin-nextgen -> FAIL Q7" \
  "$(has "$q3" 'FAIL: Q6 POM nextgen phụ thuộc com.app.plugin:appplugin' && has "$q3b" 'FAIL: Q7 POM appplugin-max'; echo $?)" "$q3
$q3b"
check "PQ4 POM nextgen khai facebook không có <exclusion> play-services-ads -> FAIL Q6" \
  "$(has "$q4" 'FAIL: Q6 POM nextgen: facebook thiếu <exclusion> play-services-ads' && has "$q4" 'rc=1'; echo $?)" "$q4"

# ── RY1: tools/release_local.sh publish 4 artifact (D33: thay release.yml) ──
Y=$ROOT/tools/release_local.sh
npub=$(command grep -c 'publishReleasePublicationToGitHubPackagesRepository' "$Y")
naar=$(command grep -oE 'build/release/appplugin(-[a-z]+)?-\$VERSION\.aar' "$Y" | sort -u | wc -l | tr -d ' ')
check "RY1 release_local.sh: 4 publish task, 4 .aar trong gh release create, 4 cp, unit+lint có :appplugin-nextgen (cũ: 3)" \
  "$([ "$npub" -eq 4 ] && [ "$naar" -eq 4 ] && [ "$(command grep -c '^cp .*-release.aar' "$Y")" -eq 4 ] && command grep -q 'appplugin-nextgen:testDebugUnitTest :appplugin-nextgen:lintDebug' "$Y"; echo $?)" "publish=$npub aar=$naar"
# ── CC1–CC5: tools/consumer_check.sh trên cây phụ thuộc đóng hộp (gradlew giả in cây có sẵn, không Gradle; chạy thật CC2/CC5 ở lane/E2 trên build/repo) ──
CCR=$TMP/ccrepo; for m in appplugin appplugin-max appplugin-game appplugin-nextgen; do mkdir -p "$CCR/com/app/plugin/$m/2.4.0"; done
printf '#!/bin/sh\necho "$*" >> "$GW_LOG"\n[ "${FAKE_RC:-0}" = 0 ] || { echo "> Could not resolve com.app.plugin:x:2.4.0"; exit "$FAKE_RC"; }\ncat "$FAKE_TREE"\n' > "$TMP/gw"; chmod +x "$TMP/gw"
CCT=com.app.plugin; NGT="+--- $CCT:appplugin-nextgen:2.4.0\n\\\\--- $CCT:appplugin-game:2.4.0\n     +--- $CCT:appplugin:2.4.0 -> $CCT:appplugin-nextgen:2.4.0 (*)\n"
printf "$NGT" > "$TMP/t2"; { cat "$TMP/t2"; echo '+--- com.google.android.gms:play-services-ads:25.4.0'; } > "$TMP/t2gms"
printf "+--- $CCT:appplugin:2.4.0\n+--- $CCT:appplugin-max:2.4.0\n\\\\--- $CCT:appplugin-game:2.4.0\n     +--- $CCT:appplugin:2.4.0 (*)\n" > "$TMP/t1"
printf "+--- $CCT:appplugin:2.4.0\n\\\\--- $CCT:appplugin-game:2.4.0\n" > "$TMP/t1nomax"
printf "+--- $CCT:appplugin-nextgen:2.4.0\n\\\\--- $CCT:appplugin-game:2.4.0\n     +--- $CCT:appplugin:2.4.0 (*)\n" > "$TMP/t3"
CC() { (cd "$ROOT" && GW_LOG=$TMP/gw.log CC_GRADLEW=$TMP/gw CC_VER=2.4.0 FAKE_TREE=$1 FAKE_RC=${4:-0} bash tools/consumer_check.sh --repo "${3:-$CCR}" --combo "$2" 2>&1; echo "rc=$?"); }
CF=$ROOT/tools/tests/fixtures/consumer; : > "$TMP/gw.log"
c1=$(CC "$TMP/t2" 2); c1l=$(cat "$TMP/gw.log")
check "CC1 consumer_check.sh + fixture dự án tiêu thụ có (<= 90 dòng), tổ hợp 2 tổng hợp -> RESULT: PASS rc 0, gọi Gradle --offline -Pcombo=2 releaseRuntimeClasspath, fixture mang luật thay thế D5-240 (cũ: No such file)" \
  "$([ -f "$ROOT/tools/consumer_check.sh" ] && [ "$(wc -l < "$ROOT/tools/consumer_check.sh")" -le 90 ] && has "$c1" 'RESULT: PASS' && has "$c1" 'rc=0' && has "$c1l" '--offline' && has "$c1l" '-Pcombo=2' && has "$c1l" 'releaseRuntimeClasspath' && command grep -qF 'substitute(module("com.app.plugin:appplugin")).using(module("com.app.plugin:appplugin-nextgen:$ver"))' "$CF/build.gradle.kts" && [ -f "$CF/settings.gradle.kts" ]; echo $?)" "$c1
$c1l"
c2=$(CC "$TMP/t2gms" 2)
check "CC2 tổ hợp 2: cây sạch PASS (dòng 'appplugin -> appplugin-nextgen' tính là nextgen, không phải appplugin); cây còn play-services-ads -> FAIL rc 1" \
  "$(has "$c1" 'com.app.plugin:appplugin-nextgen:2.4.0' && hasnt "$c1" 'FAIL' && has "$c2" 'FAIL: tổ hợp 2: còn play-services-ads' && has "$c2" 'rc=1'; echo $?)" "$c2"
c3=$(CC "$TMP/t3" 3); c3s=$(CC "$TMP/t2" 3)
check "CC3 tổ hợp 3 (không luật thay thế, cây còn appplugin + nextgen) -> in 'FAIL: trùng appplugin + appplugin-nextgen' rc 1 (kỳ vọng); cây đã thay thế -> không FAIL" \
  "$(has "$c3" 'FAIL: trùng appplugin + appplugin-nextgen' && has "$c3" 'rc=1' && hasnt "$c3s" 'FAIL' && has "$c3s" 'rc=0'; echo $?)" "$c3
$c3s"
: > "$TMP/gw.log"; c4=$(CC "$TMP/t2" 2 "$TMP/khong-co"); c4n=$(wc -c < "$TMP/gw.log" | tr -d ' '); mkdir -p "$TMP/ccrepo-thieu"; c4b=$(CC "$TMP/t2" 2 "$TMP/ccrepo-thieu"); c4g=$(CC "$TMP/t2" 2 "$CCR" 1)
check "CC4 fail-closed: thiếu build/repo -> FAIL rc 1 và không gọi Gradle; repo thiếu artifact -> FAIL; Gradle lỗi (rc 1) -> FAIL, không PASS" \
  "$(has "$c4" 'FAIL: không có repo' && has "$c4" 'rc=1' && [ "$c4n" = 0 ] && has "$c4b" 'FAIL: tổ hợp 2: repo thiếu' && has "$c4b" 'rc=1' && has "$c4g" 'FAIL: tổ hợp 2: Gradle lỗi' && has "$c4g" 'rc=1'; echo $?)" "$c4
$c4b
$c4g"
c5=$(CC "$TMP/t1" 1); c5b=$(CC "$TMP/t1nomax" 1); c5c=$(CC "$TMP/t2" 1)
check "CC5 tổ hợp 1 legacy (appplugin, -game, -max như 2.3.0) -> PASS; thiếu -max -> FAIL; có nextgen -> FAIL" \
  "$(has "$c5" 'RESULT: PASS' && has "$c5b" 'FAIL: tổ hợp 1: thiếu appplugin-max:2.4.0' && has "$c5c" 'FAIL: tổ hợp 1 (legacy) có appplugin-nextgen'; echo $?)" "$c5
$c5b"
: > "$TMP/gw.log"; c6=$(CC_OFFLINE=0 CC "$TMP/t2" 2); c6l=$(cat "$TMP/gw.log"); : > "$TMP/gw.log"; CC "$TMP/t2" 2 > /dev/null; c6d=$(cat "$TMP/gw.log")
check "CC6 CC_OFFLINE=0 (CI online) bỏ --offline khỏi lệnh Gradle, vẫn PASS; mặc định giữ --offline" \
  "$(has "$c6" 'RESULT: PASS' && hasnt "$c6l" '--offline' && has "$c6l" '-Pcombo=2' && has "$c6d" '--offline'; echo $?)" "$c6l
$c6d"
dg_finish
