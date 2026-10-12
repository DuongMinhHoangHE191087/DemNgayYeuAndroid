#!/usr/bin/env bash
# Kích thước tải về (Play) của app demo: dựng AAB release rồi `bundletool build-apks` + `get-size total`
# (1.6.4 Task 5). Chỉ đo và in số, không có ngưỡng chặn ở 1.6.4. Thiếu jar/AAB/aapt2 hay bundletool lỗi:
# RESULT: FAIL, exit 1 (fail-closed — không in số đoán).
# Dùng: tools/bundle_size.sh [--variant utility|game|nextgen] [--partners <csv>]   (mặc định utility; 1.9.0 Task 10, D2: csv ⊆ {pangle,mintegral,unity,vungle,applovin} (meta là mặc định, bị từ chối), từ lạ FAIL TRƯỚC Gradle, chuyển -PbiddingPartners)
# bundletool: $BUNDLETOOL_JAR nếu đặt, không thì ĐÚNG bundletool-1.18.3.jar trong Gradle cache (AGP 9.2.1 dùng bản này;
# cache có nhiều bản, không lấy "jar tìm thấy đầu tiên"). Jar phụ thuộc ghim đúng phiên bản AGP 9.2.1 resolve
# (`./gradlew -q buildEnvironment`, cây com.android.tools.build:bundletool:1.18.3).
set -u
VARIANT=utility; PARTNERS=""; PSET=0
bad_arg() { echo "FAIL: $1"; echo "RESULT: FAIL"; exit 1; }
while [ $# -gt 0 ]; do
  case $1 in
    --variant) [ $# -ge 2 ] || bad_arg "--variant phải là utility|game|nextgen (nhận '')"; VARIANT=$2; shift 2;;
    --partners) [ $# -ge 2 ] || bad_arg "--partners cần một danh sách csv (vd pangle,unity)"; PARTNERS=$2; PSET=1; shift 2;;
    *) bad_arg "đối số lạ '$1' (chỉ nhận --variant utility|game|nextgen, --partners <csv>)";;
  esac
done
if [ "$PSET" = 1 ]; then   # D2: kiểm TRƯỚC mọi lệnh Gradle
  case $PARTNERS in ''|,*|*,|*,,*) bad_arg "--partners rỗng hoặc có ô rỗng ('$PARTNERS')";; esac
  IFS=, read -r -a PW <<< "$PARTNERS"; seen=","
  for w in "${PW[@]}"; do
    [ "$w" != meta ] || bad_arg "--partners: meta (mặc định, luôn có) không truyền qua --partners"; case ",pangle,mintegral,unity,vungle,applovin," in *",$w,"*) ;; *) bad_arg "--partners: '$w' ngoài tập {pangle,mintegral,unity,vungle,applovin}";; esac
    case $seen in *",$w,"*) bad_arg "--partners: '$w' lặp";; esac
    seen="$seen$w,"
  done
fi
# Tên task Gradle viết hoa chữ đầu bằng case (bash 3.2 của macOS không có ${v^}).
case $VARIANT in
  utility) TASK=:app:bundleUtilityRelease;;
  game) TASK=:app:bundleGameRelease;;
  nextgen) TASK=:app:bundleNextgenRelease;;
  *) bad_arg "--variant phải là utility|game|nextgen (nhận '$VARIANT')";;
esac
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
JAVA="$JAVA_HOME/bin/java"
AAB=app/build/outputs/bundle/${VARIANT}Release/app-$VARIANT-release.aab
APKS=app/build/outputs/bundle/${VARIANT}Release/app-$VARIANT-release.apks
CACHE="${GRADLE_USER_HOME:-$HOME/.gradle}/caches/modules-2/files-2.1"
mkdir -p DOC/testing
TS=$(date +%Y%m%d-%H%M%S)
LOG=DOC/testing/bundle-size-$VARIANT-$TS.log
BUILDLOG=DOC/testing/bundle-size-build-$VARIANT-$TS.log

die() {
  echo "FAIL: $1" | tee -a "$LOG"
  echo "RESULT: FAIL" | tee -a "$LOG"
  exit 1
}

# group artifact version -> $JAR = đường dẫn jar trong Gradle cache; thiếu thì cổng FAIL (gọi thẳng, không trong $(...)).
cache_jar() {
  JAR=$(find "$CACHE/$1/$2/$3" -name "$2-$3.jar" 2>/dev/null | head -1)
  [ -n "$JAR" ] || die "thiếu $1:$2:$3 trong $CACHE"
}

echo "variant: $VARIANT aab=$AAB task=$TASK partners=${PARTNERS:-none}" | tee "$LOG"
[ -x "$JAVA" ] || die "không có java ($JAVA)"
if [ -n "${BUNDLETOOL_JAR:-}" ]; then
  [ -f "$BUNDLETOOL_JAR" ] || die "BUNDLETOOL_JAR không tồn tại: $BUNDLETOOL_JAR"
  BT=$BUNDLETOOL_JAR
else
  cache_jar com.android.tools.build bundletool 1.18.3
  BT=$JAR
fi
CP=$BT
while read -r g a v; do
  [ -n "$g" ] || continue
  cache_jar "$g" "$a" "$v"
  CP="$CP:$JAR"
done <<'EOF'
com.android.tools.build aapt2-proto 9.2.1-15009934
com.google.auto.value auto-value-annotations 1.6.2
com.google.errorprone error_prone_annotations 2.28.0
com.google.guava guava 33.3.1-jre
com.google.guava failureaccess 1.0.2
com.google.protobuf protobuf-java 3.25.5
com.google.protobuf protobuf-java-util 3.25.5
com.google.code.gson gson 2.11.0
com.google.dagger dagger 2.28.3
javax.inject javax.inject 1
org.bitbucket.b_c jose4j 0.9.5
org.slf4j slf4j-api 1.7.30
com.android.tools.build apksig 9.2.1
com.android.tools.build apkzlib 9.2.1
com.android.tools.build builder-model 9.2.1
com.android.tools common 32.2.1
com.android.tools sdk-common 32.2.1
com.android.tools annotations 32.2.1
com.android.tools.ddms ddmlib 32.2.1
org.jetbrains.kotlin kotlin-stdlib 2.3.0
EOF
bt() { "$JAVA" -cp "$CP" com.android.tools.build.bundletool.BundleToolMain "$@"; }
btver=$(bt version 2>&1) || die "bundletool version lỗi: $btver"
echo "bundletool $btver ($BT)" | tee -a "$LOG"

# sort -V: 37.0.0 đứng sau 9.0.0 (ls sắp theo chữ thì không).
AAPT2="$(ls -d "${ANDROID_HOME:-$HOME/Library/Android/sdk}"/build-tools/* 2>/dev/null | sort -V | tail -1)/aapt2"
[ -x "$AAPT2" ] || die "aapt2 không tìm thấy (ANDROID_HOME?)"
echo "aapt2: $AAPT2" | tee -a "$LOG"

# Xoá AAB/APKS cũ trước khi build: build lỗi thì không bao giờ đo nhầm sản phẩm của lần trước.
rm -f "$AAB" "$APKS"
./gradlew "$TASK" ${PARTNERS:+-PbiddingPartners=$PARTNERS} > "$BUILDLOG" 2>&1
rc=$?
echo "build exit=$rc (log: $BUILDLOG)" | tee -a "$LOG"
if [ "$rc" -ne 0 ]; then
  tail -60 "$BUILDLOG"
  die "build lỗi exit=$rc (xem $BUILDLOG)"
fi
[ -f "$AAB" ] || die "không có AAB $AAB (xem $BUILDLOG)"
echo "aab bytes=$(wc -c < "$AAB" | tr -d ' ')" | tee -a "$LOG"
echo "aab sha256=$(shasum -a 256 "$AAB" | cut -d' ' -f1)" | tee -a "$LOG"

out=$(bt build-apks --bundle="$AAB" --output="$APKS" --aapt2="$AAPT2" --overwrite 2>&1) \
  || die "bundletool build-apks lỗi: $out"
[ -f "$APKS" ] || die "bundletool build-apks không tạo $APKS"
size=$(bt get-size total --apks="$APKS" 2>&1) || die "bundletool get-size lỗi: $size"
echo "$size" | tee -a "$LOG"
# Đầu ra: dòng tiêu đề MIN,MAX rồi một dòng <min>,<max> (byte).
row=$(printf '%s\n' "$size" | sed -n '2p' | tr -d '\r')
min=${row%%,*}; max=${row#*,}
case "$min$max" in
  ''|*[!0-9]*) die "không đọc được MIN,MAX từ get-size: '$row'";;
esac
echo "download size $VARIANT: min=$min max=$max" | tee -a "$LOG"
echo "RESULT: PASS" | tee -a "$LOG"
