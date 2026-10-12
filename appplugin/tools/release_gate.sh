#!/usr/bin/env bash
# Cổng G3 (DOC/65): dựng app demo như app thật rồi soi APK và mapping. Exit 1 khi bất kỳ mục nào fail.
# Dùng: tools/release_gate.sh [--variant utility|game|nextgen]   (mặc định utility; 1.6.4 Task 5: flavor utility = AdMob + Meta,
# game = đủ adapter như trước 1.6.4; 2.4.0 NG6: nextgen = AdMob Next-Gen, thay Legacy). CI chạy utility và game.
set -u
VARIANT=utility
bad_arg() { echo "FAIL: $1"; echo "RESULT: FAIL"; exit 1; }
while [ $# -gt 0 ]; do
  case $1 in
    --variant) [ $# -ge 2 ] || bad_arg "--variant phải là utility|game|nextgen (nhận '')"; VARIANT=$2; shift 2;;
    *) bad_arg "đối số lạ '$1' (chỉ nhận --variant utility|game|nextgen)";;
  esac
done
# Tên task Gradle viết hoa chữ đầu bằng case (bash 3.2 của macOS không có ${v^}).
case $VARIANT in
  utility) TASK=:app:assembleUtilityRelease;;
  game) TASK=:app:assembleGameRelease;;
  nextgen) TASK=:app:assembleNextgenRelease;;
  *) bad_arg "--variant phải là utility|game|nextgen (nhận '$VARIANT')";;
esac
cd "$(dirname "$0")/.."
# 2.1.0 SC1 (I1): BuildInfo.ID mang sha; có .git thì lấy HEAD, không (git archive) thì giữ APPPLUGIN_BUILD_SHA do lane đặt.
if sha=$(git rev-parse --short=7 HEAD 2>/dev/null) && [ -n "$sha" ]; then export APPPLUGIN_BUILD_SHA="$sha"; fi
export JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
APK=app/build/outputs/apk/$VARIANT/release/app-$VARIANT-release.apk
MAP=app/build/outputs/mapping/${VARIANT}Release/mapping.txt
mkdir -p DOC/testing
TS=$(date +%Y%m%d-%H%M%S)
LOG=DOC/testing/release-gate-$TS.log
BUILDLOG=DOC/testing/release-gate-build-$TS.log
fail=0

# Mọi đường thoát sớm đều in RESULT: FAIL để người đọc log và CI thấy cùng một kết luận.
die() {
  echo "FAIL: $1" | tee -a "$LOG"
  echo "RESULT: FAIL" | tee -a "$LOG"
  exit 1
}

# Xoá APK/mapping cũ trước khi build: build lỗi thì không bao giờ soi nhầm sản phẩm của lần trước.
rm -f "$APK" "$MAP"
echo "variant: $VARIANT apk=$APK task=$TASK" | tee "$LOG"
./gradlew "$TASK" > "$BUILDLOG" 2>&1
rc=$?
echo "build exit=$rc (log: $BUILDLOG)" | tee -a "$LOG"
if [ "$rc" -ne 0 ]; then
  tail -60 "$BUILDLOG"
  die "build lỗi exit=$rc (xem $BUILDLOG)"
fi
[ -f "$APK" ] || die "không có APK (xem $BUILDLOG)"
# 1.7.0 Task 8 (plan-lint #25): in đúng thứ vừa dựng; byte là số thông tin, không phải ngưỡng.
echo "apk sha256=$(shasum -a 256 "$APK" | cut -d' ' -f1) bytes=$(wc -c < "$APK" | tr -d ' ')" | tee -a "$LOG"

units=$(unzip -p "$APK" resources.arsc | LC_ALL=C grep -a -o "ca-app-pub-[0-9/~]*" | wc -l | tr -d ' ')
expected=$(grep -o "ca-app-pub-[0-9/~]*" app/src/main/res/values/ads_ids.xml | wc -l | tr -d ' ')
[ "$expected" -gt 0 ] || { echo "FAIL: không đọc được ads_ids.xml" | tee -a "$LOG"; fail=1; }
echo "ad units in resources.arsc=$units (need >= $expected)" | tee -a "$LOG"
[ "$units" -ge "$expected" ] || fail=1

[ -f "$MAP" ] || die "không có mapping.txt"
echo "mapping sha256=$(shasum -a 256 "$MAP" | cut -d' ' -f1)" | tee -a "$LOG"
total=$(grep -cE '^com\.app\.plugin\.[A-Za-z0-9_.$]+ -> ' "$MAP")
kept=$(grep -E '^com\.app\.plugin\.[A-Za-z0-9_.$]+ -> ' "$MAP" | awk '{ if ($1 == substr($3, 1, length($3) - 1)) n++ } END { print n + 0 }')
renamed=$((total - kept))
pct=0; [ "$total" -gt 0 ] && pct=$((renamed * 100 / total))
echo "sdk classes total=$total renamed=$renamed pct=$pct% (need >= 90)" | tee -a "$LOG"
[ "$pct" -ge 90 ] || fail=1

. tools/lib/release_gate_map.sh || die "không nạp được tools/lib/release_gate_map.sh"
check_mapping_classvalue "$MAP"
# 1.8.0 T3 (D16): không kiểu nào được tham chiếu mà vắng khỏi dex ngoài allowlist đã rà (bài học MAX 1.6.4); chỉ utility và nextgen FAIL, game chỉ in info (D23).
check_absent_classes "$APK" tools/absent_class_allowlist.txt "$([ "$VARIANT" = nextgen ] && echo utility || echo "$VARIANT")"   # nextgen: FAIL như utility (2.4.0 NG6)

# sort -V: 37.0.0 đứng sau 9.0.0 (ls sắp theo chữ thì không). -P 16 cần build-tools >= 35.
ZIPALIGN="$(ls -d "${ANDROID_HOME:-$HOME/Library/Android/sdk}"/build-tools/* 2>/dev/null | sort -V | tail -1)/zipalign"
if [ -x "$ZIPALIGN" ]; then
  zipalign_last=$("$ZIPALIGN" -c -P 16 -v 4 "$APK" 2>&1 | tail -1)
  echo "zipalign 16KB: $zipalign_last" | tee -a "$LOG"
  [ "$zipalign_last" = "Verification successful" ] || fail=1
else
  echo "FAIL: zipalign không tìm thấy (ANDROID_HOME?)" | tee -a "$LOG"
  fail=1
fi

# 1.9.0 Task 10 (D28): meta-data của harness trong manifest đã merge: utility 1 / nextgen 1 / game 0 (lib báo mọi mục, lệch → fail=1, không dừng sớm).
. tools/lib/harness_meta.sh || die "không nạp được tools/lib/harness_meta.sh"
check_harness_meta "$APK" "$VARIANT" "${ZIPALIGN%/*}/aapt2" IAP_HARNESS
check_harness_meta "$APK" "$VARIANT" "${ZIPALIGN%/*}/aapt2" BRAIN_HARNESS

echo "RESULT: $([ $fail -eq 0 ] && echo PASS || echo FAIL)" | tee -a "$LOG"
exit $fail
