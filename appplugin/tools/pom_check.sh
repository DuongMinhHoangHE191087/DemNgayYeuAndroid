#!/usr/bin/env bash
# 2.0.0 C2b (D2): kiểm POM của 3 artifact trong repo cục bộ (publishAllPublicationsToLocalCheckRepository -> build/repo).
# Dùng: tools/pom_check.sh <repo dir> [--root <dir chứa build.gradle.kts>]. Fail-closed: thiếu POM / thiếu phiên bản -> FAIL.
#  Q1/Q2: POM appplugin không chứa nhóm game (kotlin-stdlib do AGP tự thêm vào mọi POM, không thuộc danh sách).
#  Q3: POM appplugin-max / appplugin-game phụ thuộc com.app.plugin:appplugin:<cùng bản của chính POM đó>.
#  Q4 (D26): namespace con + groupId/version chung, kiểm nguồn gradle (tools/lib/module_check.sh).
#  2.4.0 T0b (chỉ khi settings.gradle.kts include :appplugin-nextgen; include mà thiếu POM = FAIL): Q6 POM nextgen có ads-mobile-sdk, 0 play-services-ads(-lite)
#  (không tính <exclusions>), 0 phụ thuộc com.app.plugin:appplugin, 0 nhóm game, facebook có <exclusion> play-services-ads. Q7: POM -max/-game không nhắc nextgen.
set -u
GAME_RE='com\.applovin|com\.bigossp|com\.chartboost|com\.fyber|com\.unity3d|com\.mbridge|com\.vungle|com\.pangle|com\.iab\.omid'
REPO=${1:-}; SELF=$(cd "$(dirname "$0")" && pwd); ROOT=$SELF/..
[ "${2:-}" = --root ] && ROOT=${3:?--root cần thư mục}
fail=0; bad() { echo "FAIL: $*"; fail=1; }
NG=0; command grep -qF 'include(":appplugin-nextgen")' "$ROOT/settings.gradle.kts" 2>/dev/null && NG=1
[ -d "$REPO" ] || { echo "FAIL: không có repo '$REPO'"; echo "RESULT: FAIL"; exit 1; }
# 2.1.0 T0e (M35 m1): nhiều thư mục bản (build/repo cũ) -> chọn bản mới nhất (sort -V), không phụ thuộc thứ tự find.
pom() { find "$REPO/com/app/plugin/$1" -name '*.pom' 2>/dev/null | sort -V | tail -1; }
P=$(pom appplugin)
if [ -z "$P" ]; then bad "thiếu POM appplugin"; else
  n=$(command grep -cE "<groupId>($GAME_RE)" "$P"); d=$(command grep -E "<groupId>($GAME_RE)" "$P" | sort -u | wc -l | tr -d ' ')
  [ "$n" -eq 0 ] && echo "Q1 appplugin: 0 phụ thuộc nhóm game" || bad "Q1 POM appplugin có $n phụ thuộc nhóm game ($d nhóm): $(command grep -hE "<groupId>($GAME_RE)" "$P" | sed 's/.*<groupId>\([^<]*\)<.*/\1/' | sort -u | tr '\n' ' ')"
fi
# I1: GMA com.google.ads.mediation:* ngoài `facebook` (chartboost, fyber, unity...) là mạng game, không được ở POM gốc dù group không khớp regex.
if [ -n "$P" ]; then
  g=$(tr -d '\n ' < "$P" | sed 's/<\/dependency>/&\n/g' | command grep '<groupId>com\.google\.ads\.mediation</groupId>' | sed -n 's/.*<artifactId>\([^<]*\)<.*/\1/p' | command grep -vx facebook | sort -u | tr '\n' ' ')
  [ -z "$g" ] && echo "Q1 appplugin: 0 adapter GMA ngoài facebook" || bad "Q1 POM appplugin có adapter GMA game: $g"
fi
for m in appplugin-max appplugin-game; do
  M=$(pom "$m")
  if [ -z "$M" ]; then bad "thiếu POM $m"; continue; fi
  v=$(sed -n 's/^ *<version>\(.*\)<\/version>.*/\1/p' "$M" | head -1)
  dep="<dependency><groupId>com.app.plugin</groupId><artifactId>appplugin</artifactId><version>$v</version>"
  if [ -n "$v" ] && tr -d '\n ' < "$M" | command grep -q "$dep"; then
    # 2.1.0 T0e (M35 m2): phải là `api` (scope compile) để app thấy lớp lõi.
    tr -d '\n ' < "$M" | command grep -qF "$dep<scope>compile</scope>" && echo "Q3 $m: phụ thuộc com.app.plugin:appplugin:$v (scope compile)" || bad "Q3 POM $m: phụ thuộc appplugin:$v không có scope compile"
  else bad "Q3 POM $m không phụ thuộc com.app.plugin:appplugin:${v:-?} (cùng bản)"; fi
  [ "$NG" -eq 1 ] && tr -d '\n ' < "$M" | command grep -q 'appplugin-nextgen' && bad "Q7 POM $m nhắc appplugin-nextgen (chỉ phụ thuộc appplugin)"
done
if [ "$NG" -eq 1 ]; then
  N=$(pom appplugin-nextgen); if [ -z "$N" ]; then bad "thiếu POM appplugin-nextgen (settings có include)"; else
    d=$(tr -d '\n ' < "$N" | sed 's/<\/dependency>/&\n/g'); q=0; has_d() { printf '%s\n' "$d" | command grep -qE "$1"; }
    has_d '<artifactId>ads-mobile-sdk</artifactId>' || { bad "Q6 POM nextgen thiếu ads-mobile-sdk"; q=1; }
    printf '%s\n' "$d" | sed 's/<exclusions>.*<\/exclusions>//' | command grep -qE '<artifactId>play-services-ads(-lite)?</artifactId>' && { bad "Q6 POM nextgen có phụ thuộc play-services-ads(-lite)"; q=1; }
    has_d '<groupId>com.app.plugin</groupId><artifactId>appplugin</artifactId>' && { bad "Q6 POM nextgen phụ thuộc com.app.plugin:appplugin"; q=1; }
    has_d "<groupId>($GAME_RE)" && { bad "Q6 POM nextgen có phụ thuộc nhóm game"; q=1; }
    printf '%s\n' "$d" | command grep '<artifactId>facebook</artifactId>' | command grep -q '<exclusion>.*<artifactId>play-services-ads</artifactId>' || { bad "Q6 POM nextgen: facebook thiếu <exclusion> play-services-ads"; q=1; }
    [ "$q" -eq 0 ] && echo "Q6 appplugin-nextgen: ads-mobile-sdk, 0 play-services-ads, facebook có exclusion, 0 phụ thuộc appplugin"; fi
fi
bash "$SELF/lib/module_check.sh" "$ROOT" || fail=1
echo "RESULT: $([ "$fail" -eq 0 ] && echo PASS || echo FAIL)"; exit "$fail"
