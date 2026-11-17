#!/usr/bin/env bash
# Lints the Kotlin->Swift bridge (plan section 4.10). Usage: check-ios-bridge.sh [dir-or-file]
# Default dir: shared/src/iosMain/kotlin/com/example/ios/ (absent -> prints "no bridge dir", exit 0).
# Exit 1 and print "<file>:<line>: [tag] <line text>" when, in a public declaration:
#   missing-throws    fun / primary or secondary constructor without @Throws, @HiddenFromObjC or a "// no-throw: <reason>"
#                     comment on the same line (annotations may sit on the lines right above). `override fun` is exempt.
#   value-class, kotlin-result, default-param (also in multi-line parameter lists), generic-interface
#   run-blocking      runBlocking in the scanned path; in default mode also anywhere in shared commonMain and iosMain
# @HiddenFromObjC declarations are never exported, so default-param is not checked on them.
# Line based on purpose (ponytail: one declaration per line, no real Kotlin parser; swap in a PSI/detekt rule if the bridge grows).
set -u
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"

read -r -d '' AWK <<'EOF'
function rule(tag, msg) { printf "%s:%d: [%s] %s\n", FILENAME, FNR, tag, msg; bad = 1 }
function hid(s) { return s ~ /@([A-Za-z.]+\.)?HiddenFromObjC($|[^A-Za-z0-9_])/ }
function hidden(s) { return s ~ /@([A-Za-z.]+\.)?(Throws|HiddenFromObjC)($|[^A-Za-z0-9_])/ }
function nonpublic(s) { return s ~ /(^|[^A-Za-z0-9_])(private|internal|protected)[[:space:]]/ }
# scan parameter text; "=" at paren depth >= 1 (not ==, !=, <=, >=, =>) is a default value; stops when the signature closes
function scan(text,   i, c, p, n) {
  for (i = 1; insig && i <= length(text); i++) {
    c = substr(text, i, 1)
    if (c == "(") { depth++; opened = 1 }
    else if (c == ")") { depth--; if (opened && depth <= 0) insig = 0 }
    else if (c == "=" && depth >= 1) {
      p = substr(text, i - 1, 1); n = substr(text, i + 1, 1)
      if (p !~ /[=!<>]/ && n != "=" && n != ">") dflt = 1
    }
  }
}
function chk() { if (dflt) { rule("default-param", t); dflt = 0 } if (insig && !opened) insig = 0 }
function sig(text) { depth = 0; opened = 0; insig = 1; dflt = 0; scan(text); chk() }
FNR == 1 { pend = 0; pendH = 0; insig = 0 }
{
  t = $0; sub(/^[[:space:]]+/, "", t)
  if (t == "" || t ~ /^(\/\/|\/\*|\*)/) next
  nt = ($0 ~ /\/\/[[:space:]]*no-throw:[[:space:]]*[^[:space:]]/)
  code = $0; sub(/[[:space:]]\/\/.*$/, "", code)
  if (code ~ /(^|[^A-Za-z0-9_])runBlocking($|[^A-Za-z0-9_])/) rule("run-blocking", t)
  if (rbonly) next
  if (!nonpublic(code)) {
    if (code ~ /(^|[^A-Za-z0-9_])value[[:space:]]+class[[:space:]]/) rule("value-class", t)
    if (code ~ /kotlin\.Result|(^|[^A-Za-z0-9_.])Result</) rule("kotlin-result", t)
    if (code ~ /(^|[^A-Za-z0-9_])interface[[:space:]]+[A-Za-z0-9_]+[[:space:]]*</) rule("generic-interface", t)
  }
  if (insig) { scan(code); chk(); pend = 0; pendH = 0; next }
  ok = (hidden(code) || pend || nt)
  hh = (hid(code) || pendH)
  decl = 0
  c2 = code; gsub(/fun[[:space:]]+interface/, "interface", c2)
  if (match(c2, /(^|[^A-Za-z0-9_.])fun[[:space:]]+/)) {
    decl = 1; pre = substr(c2, 1, RSTART); post = substr(c2, RSTART + RLENGTH)
    if (pre !~ /(^|[^A-Za-z0-9_])override[[:space:]]/ && !nonpublic(pre)) {
      if (!ok) rule("missing-throws", t)
      if (!hh) sig(post)
    }
  } else if (match(code, /(^|[[:space:]])class[[:space:]]+[A-Za-z0-9_]+/)) {
    decl = 1; pre = substr(code, 1, RSTART); rest = substr(code, RSTART + RLENGTH)
    if (pre !~ /(^|[^A-Za-z0-9_])(value|enum|sealed|annotation)[[:space:]]/ && !nonpublic(pre)) {
      sub(/^[[:space:]]*<[^>]*>/, "", rest)
      if (rest !~ /^[[:space:]]*(@[A-Za-z.]+(\([^)]*\))?[[:space:]]*)*(private|internal|protected)[[:space:]]+constructor/ &&
          rest ~ /^[[:space:]]*(@[A-Za-z.]+(\([^)]*\))?[[:space:]]*)*(public[[:space:]]+)?(constructor[[:space:]]*)?\(/) {
        if (!ok) rule("missing-throws", t)
        gsub(/@[A-Za-z.]+(\([^)]*\))?/, "", rest)
        if (!hh) sig(rest)
      }
    }
  } else if (t ~ /^(@[A-Za-z.]+(\([^)]*\))?[[:space:]]+)*(public[[:space:]]+)?constructor[[:space:]]*\(/) {
    decl = 1
    if (!ok) rule("missing-throws", t)
    post = code; sub(/^.*constructor/, "", post)
    if (!hh) sig(post)
  }
  if (decl) { pend = 0; pendH = 0 }
  else if (t ~ /^@/) { pend = (pend || hidden(code)); pendH = (pendH || hid(code)) }
  else { pend = 0; pendH = 0 }
}
END { exit bad ? 1 : 0 }
EOF

if [ $# -ge 1 ]; then
  target="$1"; default=0
else
  target="$ROOT/shared/src/iosMain/kotlin/com/example/ios"; default=1
fi
rc=0
if [ -e "$target" ]; then
  mapfile -t files < <(find "$target" -name '*.kt' | sort)
  if [ ${#files[@]} -gt 0 ]; then awk -v rbonly=0 "$AWK" "${files[@]}" || rc=1; fi
else
  echo "no bridge dir"
fi
if [ $default -eq 1 ]; then # runBlocking must not appear anywhere in commonMain or iosMain
  roots=()
  for d in "$ROOT/shared/src/commonMain" "$ROOT/shared/src/iosMain"; do [ -d "$d" ] && roots+=("$d"); done
  if [ ${#roots[@]} -gt 0 ]; then
    mapfile -t other < <(find "${roots[@]}" -name '*.kt' -not -path "$target/*" | sort)
    if [ ${#other[@]} -gt 0 ]; then awk -v rbonly=1 "$AWK" "${other[@]}" || rc=1; fi
  fi
fi
[ $rc -eq 0 ] && [ -e "$target" ] && echo "ios-bridge OK"
exit $rc
