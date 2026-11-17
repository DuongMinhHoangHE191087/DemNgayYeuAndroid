#!/usr/bin/env bash
# Checks a Mac for the InLove KMP/iOS toolchain. Exit 0 = ready (WARN lines allowed), 1 = at least one FAIL.
# Thresholds come from toolchain.env. KMP_ROOT overrides the repo root (used by tests).
# Every system command is resolved through PATH so tests can fake them.
set -u
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="${KMP_ROOT:-$(cd "$HERE/../.." && pwd)}"
# shellcheck source=toolchain.env
. "$HERE/toolchain.env"
rc=0
fail() { echo "FAIL: $1: $2"; rc=1; }
warn() { echo "WARN: $1: $2"; }
ok() { echo "OK: $1"; }
ver_gt() { [ "$1" != "$2" ] && [ "$(printf '%s\n%s\n' "$1" "$2" | sort -V | tail -1)" = "$1" ]; }
ver_ge() { [ "$1" = "$2" ] || ver_gt "$1" "$2"; }

# arm64 + macOS
[ "$(uname -m 2>/dev/null)" = "arm64" ] && ok "arm64" || fail arch "Apple Silicon (arm64) Mac required; Intel and Rosetta shells are not supported"
[ "$(uname -s 2>/dev/null)" = "Darwin" ] && ok "darwin" || fail macos "run this on macOS"
mac="$(sw_vers -productVersion 2>/dev/null || true)"
if [ -n "$mac" ] && ver_ge "$mac" "$MIN_MACOS"; then ok "macOS $mac"; else fail macos "macOS $MIN_MACOS or newer required (found: ${mac:-none}); update in System Settings"; fi

# Xcode: 26.4.x ok, above 26.4 warn, from XCODE_BLOCK_FROM fail
xc="$(xcodebuild -version 2>/dev/null | sed -n 's/^Xcode //p' | head -1)"
if [ -z "$xc" ]; then
  fail xcode "xcodebuild not found; install Xcode $XCODE_RECOMMENDED"
else
  mm="$(echo "$xc" | cut -d. -f1,2)"
  if ver_ge "$xc" "$XCODE_BLOCK_FROM"; then
    fail xcode "Xcode $xc is not supported (blocked from $XCODE_BLOCK_FROM); install Xcode $XCODE_RECOMMENDED"
  elif ver_gt "$mm" "$XCODE_WARN_ABOVE"; then
    warn xcode "Xcode $xc is newer than tested (recommended $XCODE_RECOMMENDED)"
  else
    ok "Xcode $xc"
  fi
fi
sel="$(xcode-select -p 2>/dev/null || true)"
case "$sel" in *Xcode*.app*) ok "xcode-select $sel" ;; *) fail xcode-select "run: sudo xcode-select -s /Applications/Xcode.app/Contents/Developer (current: ${sel:-none})" ;; esac

# JDK 21
jv="$(java -version 2>&1 | sed -n 's/.*version "\([0-9]*\).*/\1/p' | head -1)"
[ "$jv" = "21" ] && ok "jdk 21" || fail jdk "JDK 21 required (found: ${jv:-none}); install a JDK 21 and set JAVA_HOME"

# Gradle wrapper via sh: gradlew is mode 100644 in git, and a clean env catches leaked variables
if (cd "$ROOT" && env -i PATH=/usr/bin:/bin sh ./gradlew --version >/dev/null 2>&1); then
  ok "gradlew --version"
else
  fail gradlew "'env -i PATH=/usr/bin:/bin sh ./gradlew --version' failed; run it by hand and read the error"
fi

# Android SDK
sdk="${ANDROID_HOME:-}"
[ -z "$sdk" ] && [ -f "$ROOT/local.properties" ] && sdk="$(sed -n 's/^sdk\.dir=//p' "$ROOT/local.properties" | tr -d '\r' | head -1)"
if [ -n "$sdk" ] && [ -d "$sdk" ]; then ok "android sdk $sdk"; else fail android-sdk "set ANDROID_HOME or sdk.dir in local.properties to an existing SDK dir"; fi

# Kotlin/Native cache (created by the first iOS build)
[ -d "$HOME/.konan" ] && ok "~/.konan" || warn konan "~/.konan missing; the first iOS build downloads Kotlin/Native (needs network)"

# Gradle heap >= 4g
jargs="$(sed -n 's/^org\.gradle\.jvmargs=//p' "$ROOT/gradle.properties" 2>/dev/null | head -1)"
mx="$(echo "$jargs" | sed -n 's/.*-Xmx\([0-9]*\)\([gGmM]\).*/\1\2/p')"
case "$mx" in
  *[gG]) mb=$(( ${mx%?} * 1024 )) ;;
  *[mM]) mb=${mx%?} ;;
  *) mb=0 ;;
esac
[ "$mb" -ge 4096 ] && ok "xmx $mx" || fail xmx "org.gradle.jvmargs in gradle.properties needs -Xmx4g or more (found: ${mx:-none})"

# iOS Simulator
if xcrun simctl list devices available 2>/dev/null | grep -q 'iPhone'; then
  ok "simulator"
else
  fail simulator "no iPhone simulator; Xcode > Settings > Components > install an iOS Simulator runtime"
fi

# Script sandboxing must be off once the Xcode project exists (Gradle runs from a Run Script phase)
pbx="$(find "$ROOT" -name project.pbxproj -path '*.xcodeproj/*' -not -path '*/build/*' -not -path '*/Pods/*' 2>/dev/null | head -1)"
if [ -n "$pbx" ]; then
  if grep -q 'ENABLE_USER_SCRIPT_SANDBOXING = YES' "$pbx" || ! grep -q 'ENABLE_USER_SCRIPT_SANDBOXING = NO' "$pbx"; then
    fail sandboxing "set ENABLE_USER_SCRIPT_SANDBOXING = NO in Build Settings ($pbx)"
  else
    ok "sandboxing off"
  fi
fi

[ $rc -eq 0 ] && echo "mac-doctor: ready" || echo "mac-doctor: FAILED"
exit $rc
