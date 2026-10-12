#!/bin/bash
# Lane lock for 1.6.2 parallel lanes. Encodes plan rules:
#  - no Gradle while any emulator device_gate runs (emugate)
#  - Gradle cap: 3 with no emulator, 1 with one non-gate emulator (M6, 1.6.3)
#  - emugate needs 0 Gradle and no other emugate; one emulator per port
#  - one real device at a time
#  - docker (1.7.0): mutually exclusive with emugate and real, both ways; one docker holder
#  - reserve (1.9.0 T0b): a window <HH:MM>-<HH:MM> (may cross midnight) during which only the reserving
#    lane may acquire gradle/emugate; others are refused at once (rc 1, "RESERVED <kind> by <lane> <window>")
# release needs the exact <lane> [port|serial] given to acquire (wrong key: rc 1, prints 'held: <keys>'); unknown kind: rc 1.
# Usage: lanelock.sh acquire|release gradle|emugate|emu|real|docker <lane> [port|serial]
#        lanelock.sh status
#        lanelock.sh reserve gradle|emugate <lane> <HH:MM>-<HH:MM> | unreserve gradle|emugate <lane>
#        LANELOCK_NOW=HH:MM overrides the clock (tests). Unknown op: rc 2, nothing created.
# acquire blocks (polls every ${LANELOCK_POLL:-20} s) until allowed; prints "LOCK <kind> <lane> <arg>".
# 2.2.0 T0b (D7-220): state dir = $LANELOCK_DIR (default <repo>/.superpowers/sdd/locks).
#  - FIFO tickets (gradle|emugate|real|boot|cpu): queue/<kind>/<%08d>@<lane>@<arg> holds the waiter pid; a grant needs the
#    old predicate AND a ticket within the first <cap - held> live tickets (boot: tickets of the same port). Dead-pid tickets are swept.
#  - Limits: cpu has no preference over a waiting boot (boot only waits for HELD cpu; by design, rule 15). Ticket pid reuse is
#    guarded by the ps start time; status sweeps under the mutex.
#  - boot = emulator boot window, exclusive per port, waits for held cpu; cpu = runner/pytest/review, shared cap 2, waits while boot held.
set -u
L="${LANELOCK_DIR:-$(cd "$(dirname "$0")/.." && pwd)/.superpowers/sdd/locks}"; op=${1:-}
SLEEP=${LANELOCK_POLL:-20}
for k in gradle emugate emu real docker devwait boot cpu queue/gradle queue/emugate queue/real queue/boot queue/cpu; do mkdir -p "$L/$k"; done
mutex_on(){ until mkdir "$L/.mutex" 2>/dev/null; do sleep 1; done; }
mutex_off(){ rmdir "$L/.mutex"; }
n(){ ls "$L/$1" 2>/dev/null | wc -l | tr -d ' '; }
# ticket = "<pid>:<ps lstart>" (start time guards against pid reuse); an empty file younger than 2 s is a ticket being written: kept.
mtime(){ local v; v=$(stat -c %Y "$1" 2>/dev/null) || v=$(stat -f %m "$1" 2>/dev/null) || v=0; case $v in ''|*[!0-9]*) v=0;; esac; echo "$v"; }  # GNU first (on GNU, -f %m is a valid fs query), numeric guard
alive(){ c=$(cat "$1" 2>/dev/null)
  [ -n "$c" ] || { [ $(( $(date +%s) - $(mtime "$1") )) -lt 2 ]; return; }
  [ "$(ps -o lstart= -p "${c%%:*}" 2>/dev/null)" = "${c#*:}" ]; }
sweep(){ for f in "$L"/queue/*/*; do [ -f "$f" ] || continue; alive "$f" && continue
    b=${f##*/}; echo "stale ticket ${b#*@}"; rm -f "$f"; done; }
if [ "$op" = status ]; then mutex_on; sweep; mutex_off; for k in gradle emugate emu real docker devwait boot cpu; do echo "$k: $(ls $L/$k | tr '\n' ' ')"; done; 
  # L10 (1.8.0 T0): orphan report only, never releases. An emugate held > 5 min with no qemu-system running.
  if [ "$(ls "$L/emugate" 2>/dev/null | wc -l | tr -d ' ')" -gt 0 ] && [ "$(pgrep -f qemu-system | wc -l | tr -d ' ')" -eq 0 ]; then
    now=$(date +%s)
    for h in "$L"/emugate/*; do
      [ -f "$h/since" ] || continue
      [ $((now - $(mtime "$h/since"))) -gt 300 ] && echo "orphan emugate $(basename "$h") (held > 5 min, no qemu-system; not released)"
    done
  fi
  for r in "$L"/reserve-*; do [ -f "$r" ] || continue; f=$(basename "$r"); k=${f#reserve-}; echo "reserve: ${k%%-*} ${k#*-} $(cat "$r")"; done
  exit 0; fi
case $op in acquire|release|reserve|unreserve) ;; *) echo "unknown op: $op" >&2; exit 2;; esac
tmin(){ echo $((10#${1%:*} * 60 + 10#${1#*:})); }
tok='([01][0-9]|2[0-3]):[0-5][0-9]'
if [ "$op" = reserve ] || [ "$op" = unreserve ]; then
  case ${2:-} in gradle|emugate) ;; *) echo "reserve: kind must be gradle|emugate" >&2; exit 2;; esac
  case ${3:-} in ''|*[!A-Za-z0-9_.]*) echo "reserve: bad lane" >&2; exit 2;; esac
  rf="$L/reserve-$2-$3"
  if [ "$op" = unreserve ]; then rm -f "$rf"; echo "UNRESERVE $2 $3"; exit 0; fi
  w=${4:-}
  echo "$w" | grep -Eq "^$tok-$tok\$" && [ "${w%-*}" != "${w#*-}" ] || { echo "reserve: bad window '$w' (HH:MM-HH:MM)" >&2; exit 2; }
  echo "$w" > "$rf"; echo "RESERVE $2 $3 $w"; exit 0
fi
# reserved window (D10): refuse a non-reserving lane at once, before any devwait/lock side effect
if [ "$op" = acquire ]; then
  case ${2:-} in gradle|emugate)
    now=$(tmin "${LANELOCK_NOW:-$(date +%H:%M)}")
    for r in "$L"/reserve-"$2"-*; do [ -f "$r" ] || continue
      rl=${r##*/reserve-$2-}; [ "$rl" = "$3" ] && continue
      w=$(cat "$r"); a=$(tmin "${w%-*}"); b=$(tmin "${w#*-}")
      if [ "$a" -lt "$b" ]; then [ "$now" -ge "$a" ] && [ "$now" -lt "$b" ] && in=1 || in=0
      else [ "$now" -ge "$a" ] || [ "$now" -lt "$b" ] && in=1 || in=0; fi
      [ "$in" = 1 ] && { echo "RESERVED $2 by $rl $w"; exit 1; }
    done;;
  esac
fi
kind=${2:-}; lane=$3; arg=${4:-x}; key="$lane@$arg"
case $kind in gradle|emugate|emu|real|docker|boot|cpu) ;; *) echo "unknown kind: '$kind' (gradle|emugate|emu|real|docker|boot|cpu)" >&2; exit 1;; esac
# 2.0.0 T0g (D9): release must name the exact key that acquire created; a wrong key is rc 1 + the held keys, nothing removed.
if [ "$op" = release ]; then
  [ -d "$L/$kind/$key" ] || { echo "release: no such hold $kind $key; held: $(ls "$L/$kind" 2>/dev/null | tr '\n' ' ')" >&2; exit 1; }
  rm -rf "$L/$kind/$key"; echo "UNLOCK $kind $key"; exit 0
fi
case $kind in emugate) mkdir -p "$L/devwait/$key"; trap 'rm -rf "$L/devwait/$key"' EXIT;; esac
tk=""
case $kind in gradle|emugate|real|boot|cpu)
  mutex_on; sweep >&2; sq=$(cat "$L/queue/seq" 2>/dev/null || echo 0); sq=$((sq + 1)); echo $sq > "$L/queue/seq"
  tk=$(printf '%08d@%s' $sq "$key"); echo "$$:$(ps -o lstart= -p $$)" > "$L/queue/$kind/$tk"; mutex_off
  trap 'rm -rf "$L/devwait/$key"; rm -f "$L/queue/$kind/$tk"' EXIT;; esac
# rank = number of live older tickets of this kind (boot: only the same port); FIFO grant needs rank < cap - held
rank(){ r=0; for f in "$L/queue/$kind"/*; do [ -f "$f" ] || continue; b=${f##*/}; [ "$b" = "$tk" ] && break
    [ "$kind" = boot ] && case $b in *"@$arg") ;; *) continue;; esac; r=$((r + 1)); done; echo $r; }
fifo(){ [ -z "$tk" ] || [ "$(rank)" -lt $(($1 - $(n $kind))) ]; }
while :; do
  mutex_on; ok=0; sweep >&2
  case $kind in
    gradle) cap=3; [ "$(n emu)" -gt 0 ] && cap=1
            # writer preference (1.7.0 13:50): a waiting emugate/real request blocks NEW gradle grants
            [ "$(n emugate)" -eq 0 ] && [ "$(n devwait)" -eq 0 ] && [ "$(n gradle)" -lt $cap ] && fifo $cap && ok=1;;
    emugate) [ "$(n gradle)" -eq 0 ] && [ "$(n emugate)" -eq 0 ] && [ "$(n emu)" -eq 0 ] && [ "$(n docker)" -eq 0 ] && fifo 1 && ok=1;;
    emu) [ "$(n emugate)" -eq 0 ] && [ "$(n gradle)" -le 1 ] && ! ls "$L/emu" | grep -q "@$arg$" && ok=1;;
    real) [ "$(n real)" -eq 0 ] && [ "$(n docker)" -eq 0 ] && fifo 1 && ok=1;;
    docker) [ "$(n docker)" -eq 0 ] && [ "$(n emugate)" -eq 0 ] && [ "$(n real)" -eq 0 ] && ok=1;;
    boot) ! ls "$L/boot" | grep -q "@$arg$" && [ "$(n cpu)" -eq 0 ] && fifo 99 && ok=1;;
    cpu) [ "$(n boot)" -eq 0 ] && fifo 2 && ok=1;;
  esac
  [ $ok = 1 ] && mkdir -p "$L/$kind/$key" && date "+%F %T" > "$L/$kind/$key/since" && rm -rf "$L/devwait/$key" && { [ -z "$tk" ] || rm -f "$L/queue/$kind/$tk"; }
  mutex_off
  [ $ok = 1 ] && { echo "LOCK $kind $key"; exit 0; }
  sleep "$SLEEP"
done
