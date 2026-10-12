#!/usr/bin/env bash
# 2.0.0 C2b Q4 (D26): AGP 9.2.1 không báo lỗi namespace trùng cho thư viện không có resource → kiểm nguồn gradle.
# Dùng: module_check.sh <root>. namespace con đúng, publish dùng chung groupId com.app.plugin + sdkVersion gốc (extra), 3 module.
R=${1:?root}; fail=0; bad() { echo "FAIL: Q4 $*"; fail=1; }
for m in game max; do
  f=$R/appplugin-$m/build.gradle.kts
  [ -f "$f" ] || { bad "thiếu $f"; continue; }
  command grep -qE "^\s*namespace\s*=\s*\"com\.app\.plugin\.$m\"" "$f" || bad "$f phải khai namespace com.app.plugin.$m"
  command grep -qF 'apply(from = "../gradle/publish.gradle")' "$f" || bad "$f không dùng gradle/publish.gradle chung"
done
command grep -qF 'apply(from = "gradle/publish.gradle")' "$R/build.gradle.kts" || bad "build.gradle.kts gốc không dùng gradle/publish.gradle chung"
p=$R/gradle/publish.gradle
command grep -qF "groupId = 'com.app.plugin'" "$p" 2>/dev/null || bad "publish.gradle thiếu groupId com.app.plugin"
command grep -qF 'version = rootProject.ext.sdkVersion' "$p" 2>/dev/null || bad "publish.gradle thiếu version = sdkVersion gốc"
command grep -qE '^val sdkVersion = "[0-9]' "$R/build.gradle.kts" || bad "gốc thiếu val sdkVersion"
# 2.1.0 T0e (M35 m5, Q5): -max khai đúng 13 group:artifact = 9 của applovinMaxCoords (gốc, dùng qua forEach api) + 4 api() tường minh.
cm=$(sed -n '/applovinMaxCoords"\] = listOf(/,/^)/p' "$R/build.gradle.kts" | command grep -cE '^\s*"[^":]+:[^":]+:[^"]+",')
am=$(command grep -cE '^\s*api\("[^":]+:[^":]+:[^"]+"\)' "$R/appplugin-max/build.gradle.kts" 2>/dev/null)
command grep -qF 'applovinMaxCoords' "$R/appplugin-max/build.gradle.kts" 2>/dev/null || bad "Q5 appplugin-max không dùng applovinMaxCoords"
[ $((cm + ${am:-0})) -eq 13 ] && echo "Q5: appplugin-max khai 13 ($cm applovinMaxCoords + $am api)" || bad "Q5 appplugin-max khai $((cm + ${am:-0})) group:artifact ($cm + $am), cần 13"
# 2.4.0 T0b (hợp đồng A): chỉ khi module nextgen tồn tại. namespace = com.app.plugin (mã chung dùng com.app.plugin.R/BuildConfig); Q6: mọi phụ thuộc
# api/implementation của gốc, trừ tập GMA (play-services-ads, facebook:6.21.0.4), có nguyên dòng ở nextgen, neo đầu dòng `api(`/`implementation(` (cùng bản).
n=$R/appplugin-nextgen/build.gradle.kts
if [ -f "$n" ]; then
  command grep -qE '^\s*namespace\s*=\s*"com\.app\.plugin"' "$n" || bad "$n phải khai namespace com.app.plugin"
  command grep -qF 'apply(from = "../gradle/publish.gradle")' "$n" || bad "$n không dùng gradle/publish.gradle chung"
  miss=$(command grep -E '^\s*(api|implementation)\(' "$R/build.gradle.kts" | command grep -vE 'play-services-ads|facebook:6\.21\.0\.4' | sed 's/^[[:space:]]*//' | while IFS= read -r l; do awk -v l="$l" '{ sub(/^[ \t]+/, "") } index($0, l) == 1 { f = 1 } END { exit !f }' "$n" || echo "$l"; done | tr '\n' ' ')
  [ -z "$miss" ] || bad "Q6 nextgen thiếu phụ thuộc của gốc: $miss"
fi
[ "$fail" -eq 0 ] && echo "Q4: namespace + groupId + sdkVersion chung OK"; exit "$fail"
