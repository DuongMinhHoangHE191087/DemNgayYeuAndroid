#!/usr/bin/env bash
# G9 (DOC/65): phân tích hprof Android bằng shark 2.14 (Gradle cache) — xem tools/g9/README.md.
# Dùng: bash tools/g9_heap.sh <file.hprof> [lớp Activity] [--exp tên=token,...]...
#   G9      : HeapAnalyzer của shark, đường ngắn nhất tới mỗi Activity đã huỷ (appDefaults).
#   G9Roots : chỉ mục tham chiếu mạnh riêng, phân loại đường, chủ WebView, thí nghiệm cắt (--exp truyền thẳng).
set -euo pipefail
if [ $# -lt 1 ] || [ ! -f "$1" ]; then echo "usage: $0 <file.hprof> [activity class] [--exp name=tokens]..."; exit 2; fi
HPROF=$1; ACT=${2:-android.app.Activity}; shift; [ $# -gt 0 ] && shift
JAVA_HOME=${JAVA_HOME:-"/Applications/Android Studio.app/Contents/jbr/Contents/Home"}
M2=${GRADLE_USER_HOME:-$HOME/.gradle}/caches/modules-2/files-2.1
jar() { find "$M2/$1" -name "$2" 2>/dev/null | sort | tail -1; }
CP=""
for a in shark shark-graph shark-hprof shark-android shark-log; do
  j=$(jar "com.squareup.leakcanary/$a/2.14" "$a-2.14.jar"); [ -n "$j" ] || { echo "thiếu $a-2.14.jar trong $M2"; exit 1; }; CP="$CP:$j"
done
OKIO=$(jar com.squareup.okio/okio-jvm/3.9.1 'okio-jvm-3.9.1.jar'); KSTD=$(find "$M2/org.jetbrains.kotlin/kotlin-stdlib" -name 'kotlin-stdlib-2.*.jar' ! -name '*-sources.jar' 2>/dev/null | sort | tail -1)
[ -n "$OKIO" ] && [ -n "$KSTD" ] || { echo "thiếu okio-jvm 3.9.1 hoặc kotlin-stdlib trong $M2"; exit 1; }
CP="${CP#:}:$OKIO:$KSTD"
SRC=$(cd "$(dirname "$0")/g9" && pwd); OUT=${G9_OUT:-${TMPDIR:-/tmp}/g9-classes}; mkdir -p "$OUT"
"$JAVA_HOME/bin/javac" -nowarn -d "$OUT" -cp "$CP" "$SRC/G9.java" "$SRC/G9Roots.java"
echo "######## G9 (shark HeapAnalyzer) $HPROF $ACT"
"$JAVA_HOME/bin/java" -Xmx4g -cp "$OUT:$CP" G9 "$HPROF" "$ACT" appDefaults 2>/dev/null
echo; echo "######## G9Roots (chỉ mục riêng, cắt) $HPROF $ACT"
"$JAVA_HOME/bin/java" -Xmx6g -cp "$OUT:$CP" G9Roots "$HPROF" "$ACT" "$@"
