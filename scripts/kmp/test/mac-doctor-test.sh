#!/usr/bin/env bash
# Tests for scripts/kmp/mac-doctor.sh. System commands are faked via PATH so it runs anywhere.
set -u
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DOCTOR="$HERE/../mac-doctor.sh"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
FAILS=0
ok() { echo "PASS: $1"; }
bad() { echo "FAIL: $1"; FAILS=$((FAILS + 1)); }

BIN="$TMP/bin"; ROOT="$TMP/root"; H="$TMP/home"
mkdir -p "$BIN" "$ROOT" "$H/.konan" "$TMP/sdk"
fake() { printf '#!/bin/sh\n%s\n' "$2" > "$BIN/$1"; chmod +x "$BIN/$1"; }
fake uname 'if [ "$1" = "-m" ]; then echo arm64; else echo Darwin; fi'
fake sw_vers 'echo 26.3'
fake xcodebuild 'echo "Xcode $FAKE_XCODE"; echo "Build version 17A1"'
fake xcode-select 'echo /Applications/Xcode.app/Contents/Developer'
fake java 'echo "openjdk version \"${FAKE_JDK:-21.0.5}\" 2024-10-15" >&2'
fake xcrun 'echo "-- iOS 26.4 --"; echo "    iPhone 17 (ABC) (Shutdown)"'
printf '#!/bin/sh\necho "Gradle 9.0"\n' > "$ROOT/gradlew"
echo 'org.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8' > "$ROOT/gradle.properties"
echo "sdk.dir=$TMP/sdk" > "$ROOT/local.properties"

run() { # run <xcode-version>; extra env via caller
  KMP_ROOT="$ROOT" HOME="$H" ANDROID_HOME= FAKE_XCODE="$1" PATH="$BIN:$PATH" bash "$DOCTOR" 2>&1
}

out="$(run 26.4.1)"; rc=$?
[ $rc -eq 0 ] && ! echo "$out" | grep -q 'WARN: xcode' && ok "Xcode 26.4.1 exits 0, no warning" || bad "26.4.1 rc=$rc: $out"

out="$(run 26.5)"; rc=$?
{ [ $rc -eq 0 ] && echo "$out" | grep -q 'WARN: xcode'; } && ok "Xcode 26.5 exits 0 with warning" || bad "26.5 rc=$rc: $out"

out="$(run 27.0)"; rc=$?
{ [ $rc -eq 1 ] && echo "$out" | grep -q 'FAIL: xcode'; } && ok "Xcode 27.0 exits 1" || bad "27.0 rc=$rc: $out"

echo 'org.gradle.jvmargs=-Xmx3072m' > "$ROOT/gradle.properties"
out="$(run 26.4.1)"; rc=$?
{ [ $rc -eq 1 ] && echo "$out" | grep -q 'FAIL: xmx'; } && ok "-Xmx3072m fails" || bad "xmx rc=$rc: $out"
echo 'org.gradle.jvmargs=-Xmx4g' > "$ROOT/gradle.properties"

out="$(FAKE_JDK=17.0.2 run 26.4.1)"; rc=$?
{ [ $rc -eq 1 ] && echo "$out" | grep -q 'FAIL: jdk'; } && ok "JDK 17 fails" || bad "jdk rc=$rc: $out"

mkdir -p "$ROOT/iosApp/iosApp.xcodeproj"
echo 'ENABLE_USER_SCRIPT_SANDBOXING = YES;' > "$ROOT/iosApp/iosApp.xcodeproj/project.pbxproj"
out="$(run 26.4.1)"; rc=$?
{ [ $rc -eq 1 ] && echo "$out" | grep -q 'FAIL: sandboxing'; } && ok "sandboxing YES fails" || bad "sandbox rc=$rc: $out"
echo 'ENABLE_USER_SCRIPT_SANDBOXING = NO;' > "$ROOT/iosApp/iosApp.xcodeproj/project.pbxproj"
out="$(run 26.4.1)"; rc=$?
[ $rc -eq 0 ] && ok "sandboxing NO passes" || bad "sandbox NO rc=$rc: $out"

[ $FAILS -eq 0 ] && echo "ALL PASS" || { echo "$FAILS FAILED"; exit 1; }
