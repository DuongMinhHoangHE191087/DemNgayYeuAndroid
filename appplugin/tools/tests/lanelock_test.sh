#!/usr/bin/env bash
# lanelock_test.sh (2.0.0 T0g L7-L9, 2.2.0 T0b F1-F5, 2.2.0 T0f F6): chay tren state dir tam (LANELOCK_DIR), khong bao gio dung LOCKS song.
# Dung: bash tools/tests/lanelock_test.sh [duong-dan-lanelock.sh]   (mac dinh: tools/lanelock.sh)
set -u
SRC=${1:-$(cd "$(dirname "$0")/.." && pwd)/lanelock.sh}
[ -f "$SRC" ] || { echo "FAIL: khong co $SRC"; exit 1; }
T=$(mktemp -d "${TMPDIR:-/tmp}/lanelock-test.XXXXXX") || exit 1
BG=""; trap 'for p in $BG; do kill "$p" 2>/dev/null; done; rm -rf "$T"' EXIT
total=0; bad=0
check() { total=$((total + 1)); if [ "$2" -eq 0 ]; then echo "ok     - $1"; else bad=$((bad + 1)); echo "FAIL   - $1"; printf '%s\n' "${3:-}" | head -8 | sed 's/^/         /'; fi; }
fresh() { for p in $BG; do kill "$p" 2>/dev/null; done; BG=""; rm -rf "$T/l"; mkdir -p "$T/l"; export LANELOCK_DIR=$T/l LANELOCK_POLL=0.2; }
ll() { bash "$SRC" "$@"; }
# bg <out> <args>: acquire nen, ghi pid vao $BG; waitfor <cmd...>: poll toi da ~10 s
bg() { o=$1; shift; bash "$SRC" "$@" >"$o" 2>&1 & BG="$BG $!"; disown $!; }
waitfor() { i=0; while ! "$@" 2>/dev/null && [ $i -lt 100 ]; do sleep 0.1; i=$((i + 1)); done; "$@" 2>/dev/null; }
tickets() { ls "$T/l/queue/$1" 2>/dev/null | wc -l | tr -d ' '; }
has() { command grep -q "$1" "$2" 2>/dev/null; }

fresh; ll acquire emugate X emulator-5570 >/dev/null
o7=$(ll release emugate X wrong 2>&1); r7=$?
h7=$(ls "$T/l/emugate" | tr '\n' ' ')
check "L7 giu X@emulator-5570, release emugate X wrong -> rc 1, in 'held: X@emulator-5570', khoa van con" \
  "$([ "$r7" -eq 1 ] && printf '%s\n' "$o7" | command grep -qF 'held: X@emulator-5570' && [ "$h7" = 'X@emulator-5570 ' ]; echo $?)" "rc=$r7 $o7 left=[$h7]"

fresh; ll acquire emugate X emulator-5570 >/dev/null
o8=$(ll release emugate X emulator-5570 2>&1); r8=$?
check "L8 release dung khoa (X emulator-5570) -> rc 0, 'UNLOCK emugate X@emulator-5570', kind rong" \
  "$([ "$r8" -eq 0 ] && printf '%s\n' "$o8" | command grep -qF 'UNLOCK emugate X@emulator-5570' && [ -z "$(ls "$T/l/emugate")" ]; echo $?)" "rc=$r8 $o8"

fresh
o9=$(ll release "" X emulator-5570 2>&1); r9=$?
o9b=$(ll release nosuchkind X 2>&1); r9b=$?
check "L9 kind trong (va kind la) -> rc 1, khong tao/xoa gi them khoa" \
  "$([ "$r9" -eq 1 ] && [ "$r9b" -eq 1 ] && [ "$(find "$T/l" -type f | wc -l | tr -d " ")" -eq 0 ]; echo $?)" "empty rc=$r9 $o9 / unknown rc=$r9b $o9b"

# F1: H giu emugate; A, B, C xep ve (theo thu tu, ve da ghi truoc khi nha); nha H -> cap dung thu tu A, B, C (poll 2 s, lech pha 0.6 s, nha H ngay sau poll cua A:
# neu khong co FIFO thi B/C thang truoc A)
fresh; export LANELOCK_POLL=2; ll acquire emugate H 5570 >/dev/null; : > "$T/order"
for w in A B C; do
  ( bash "$SRC" acquire emugate $w 5572 >/dev/null 2>&1 && echo $w >> "$T/order"; bash "$SRC" release emugate $w 5572 >/dev/null 2>&1 ) &
  BG="$BG $!"; disown $!; waitfor test "$(tickets emugate)" -ge $(( $(printf '%s' "$w" | tr ABC 123) )); sleep 0.6
done
sleep 0.4; ll release emugate H 5570 >/dev/null
waitfor test "$(wc -l < "$T/order" | tr -d ' ')" -ge 3; export LANELOCK_POLL=0.2
check "F1 3 lane A,B,C xep ve khi H giu -> cap dung thu tu A B C" \
  "$([ "$(tr '\n' ' ' < "$T/order")" = 'A B C ' ]; echo $?)" "order=[$(tr '\n' ' ' < "$T/order")]"

# F2: ve cua pid chet bi don, khong chan hang doi
fresh; mkdir -p "$T/l/queue/emugate"; ( exit 0 ) & dead=$!; wait $dead; echo $dead > "$T/l/queue/emugate/00000001@ghost@x"
os=$(ll status 2>&1); gone=$([ -z "$(ls "$T/l/queue/emugate")" ]; echo $?)
echo "$$:Thu Jan  1 00:00:00 1970" > "$T/l/queue/emugate/00000002@reused@x"; : > "$T/l/queue/emugate/00000003@fresh@x"
oa=$(ll acquire emugate Z 5570 2>&1); kept=$([ -f "$T/l/queue/emugate/00000003@fresh@x" ]; echo $?)
check "F2 ve pid chet / pid dung lai (start-time lech) -> bi don, ve rong < 2 s duoc giu; status in 'stale ticket ghost@x' + xoa; acquire khong bi ve chet chan" \
  "$(printf '%s\n' "$os" | command grep -qF 'stale ticket ghost@x' && [ "$gone" -eq 0 ] && printf '%s\n' "$oa" | command grep -qF 'stale ticket reused@x' && [ "$kept" -eq 0 ] && printf '%s\n' "$oa" | command grep -qF 'LOCK emugate Z@5570'; echo $?)" "status=$os acq=$oa"

# F3: cpu bi loai tru khi boot dang giu; nha boot -> cpu duoc cap; cpu khong biet kind cu
fresh; ll acquire boot L1 5570 >/dev/null
bg "$T/cpu.out" acquire cpu R1; sleep 0.8
held=$(! has LOCK "$T/cpu.out"; echo $?)
ll release boot L1 5570 >/dev/null; waitfor has 'LOCK cpu R1@x' "$T/cpu.out"
fresh; ll acquire cpu R1 >/dev/null; ll acquire cpu R2 >/dev/null; bg "$T/c3.out" acquire cpu R3; sleep 0.8
check "F3 cpu cho khi boot giu, duoc cap khi boot nha; cpu chung: lane thu 3 cho (cap 2)" \
  "$([ "$held" -eq 0 ] && [ -d "$T/l/cpu/R2@x" ] && ! has LOCK "$T/c3.out"; echo $?)" "cpu.out=$(cat "$T/cpu.out") cpu=$(ls "$T/l/cpu")"

# F4: boot cho cpu dang giu roi duoc cap
fresh; ll acquire cpu R1 >/dev/null
bg "$T/boot.out" acquire boot L1 5570; sleep 0.8
w4=$(! has LOCK "$T/boot.out"; echo $?)
ll release cpu R1 >/dev/null; waitfor has 'LOCK boot L1@5570' "$T/boot.out"
check "F4 boot cho cpu dang giu, nha cpu -> boot duoc cap" \
  "$([ "$w4" -eq 0 ] && has 'LOCK boot L1@5570' "$T/boot.out"; echo $?)" "$(cat "$T/boot.out")"

# F5: wrapper 3 dong chuyen tiep rc + LOCK va dung state dir cua no
fresh; unset LANELOCK_DIR; W=$T/w/.superpowers/sdd/locks; mkdir -p "$W" "$T/w/tools"; cp "$SRC" "$T/w/tools/lanelock.sh"
printf '%s\n' '#!/bin/bash' 'L="$(cd "$(dirname "$0")" && pwd)"' 'LANELOCK_DIR=$L exec bash "$L/../../../tools/lanelock.sh" "$@"' > "$W/lanelock.sh"
o5=$(bash "$W/lanelock.sh" acquire cpu W 2>&1); r5=$?; bash "$W/lanelock.sh" acquire nosuch W >/dev/null 2>&1; r5b=$?
check "F5 wrapper: LOCK cpu W@x rc 0, state o thu muc wrapper, kind la rc 1" \
  "$([ "$r5" -eq 0 ] && [ "$r5b" -eq 1 ] && [ "$o5" = 'LOCK cpu W@x' ] && [ -d "$W/cpu/W@x" ]; echo $?)" "rc=$r5 $o5 rc2=$r5b"

# F6: nhanh GNU stat (shim tren PATH giong GNU that, doc lap host: -f %m = mount point "/" rc 0, -c %Y = epoch mtime qua perl; con lai -> stat that)
RS=$(command -v stat); mkdir -p "$T/shim"
printf '%s\n' '#!/bin/bash' '[ "$1" = -c ] && [ "$2" = %Y ] && exec perl -e '"'"'print((stat($ARGV[0]))[9])'"'"' "$3"' '[ "$1" = -f ] && [ "$2" = %m ] && { echo /; exit 0; }' 'exec "$RS" "$@"' > "$T/shim/stat"; chmod +x "$T/shim/stat"
fresh; mkdir -p "$T/l/queue/emugate"; : > "$T/l/queue/emugate/00000001@young@x"; : > "$T/l/queue/emugate/00000002@old@x"; touch -t 200001010000 "$T/l/queue/emugate/00000002@old@x"
o6=$(RS=$RS PATH="$T/shim:$PATH" ll status 2>&1)
check "F6 stat kieu GNU: ve rong < 2 s duoc giu, ve rong cu bi don" \
  "$([ -f "$T/l/queue/emugate/00000001@young@x" ] && [ ! -e "$T/l/queue/emugate/00000002@old@x" ] && printf '%s\n' "$o6" | command grep -qF 'stale ticket old@x'; echo $?)" "$o6"

echo
if [ "$bad" -eq 0 ]; then echo "PASS $total/$total"; exit 0; fi
echo "FAIL $bad/$total"; exit 1
