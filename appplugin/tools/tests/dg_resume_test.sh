#!/usr/bin/env bash
# Test không cần máy của cổng thiết bị — vùng 2.4.0 DEMO-RES (D8-240, OWNER 12): --resume (app open) và mg_show_full2/mg_full2_all (Full2).
# Chạy: bash tools/tests/dg_resume_test.sh (hoặc qua runner device_gate_allowlist_test.sh, PARTS có `resume`). exit 0 = xanh.
. "$(dirname "$0")/lib/dg_harness.sh" || exit 99
# adb giả qbin: "App open resume: OFF|ON" (540 1350, đảo), "Interstitial → Full2" (540 1950: lẻ nạp, chẵn hiện cha + Full2); FAKE_Q: noopen|offopen|nofull2|noparent|fatal
mkdir -p "$TMP/qbin"
cat > "$TMP/qbin/adb" <<'EOF'
#!/usr/bin/env bash
echo "$*" >> "$ADB_CALLS"; ST=$FAKE_Q_ST; J=$ST/events.jsonl; r=$(cat "$ST/res"); n() { grep -c "^shell input tap $1\$" "$ADB_CALLS"; }; row() { printf '{"event_name": "%s", "placement": "%s", "ad_format": "%s"}\n' "${3:-ad_show}" "$1" "$2" >> "$J"; }
case "$*" in
  "shell getprop ro.build.version.sdk") echo 34;; "shell wm size") echo "Physical size: 1080x2400";; "shell pm clear"*) echo Success;; "shell am start -W"*) echo "TotalTime: 100"; row full_resume interstitial ad_fill;;
  "logcat -d -b crash") [ "${FAKE_Q:-}" != capfail ] || exit 1; [ "${FAKE_Q:-}" != fatal ] || printf 'E AndroidRuntime: FATAL EXCEPTION: main\nE AndroidRuntime: Process: test.sdk.zd, PID: 1\n';;
  "shell am start -n"*) { { [ "$r" = ON ] && [ "${FAKE_Q:-}" != noopen ]; } || { [ "$r" = OFF ] && [ "${FAKE_Q:-}" = offopen ]; }; } && row full_resume interstitial;;
  "shell cat /sdcard/ui.xml") echo "<hierarchy><node text=\"App open resume: $r\" bounds=\"[0,1300][1080,1400]\" /><node text=\"Interstitial → Full2\" bounds=\"[0,1900][1080,2000]\" /></hierarchy>";;
  "shell input tap 540 1350") [ "$r" = ON ] && echo OFF > "$ST/res" || echo ON > "$ST/res";;
  "shell input tap 540 1950") echo "05:00 D AppPlugin: ads full helper full_default showInterstitial call show" >> "$ST/log"
    if [ $(( $(n "540 1950") % 2 )) -eq 1 ]; then echo "05:00 D AppPlugin: AdsHelper onAdLoaded: pl=full_default opl=full_default format=interstitial platform=admob" >> "$ST/log"
    else [ "${FAKE_Q:-}" = noparent ] || row full_default interstitial; [ "${FAKE_Q:-}" = nofull2 ] || row full_2_default interstitial; fi;;
  "shell dumpsys window"*) w=MainActivity; [ "$(grep -E '^shell (input tap|am start|input keyevent)' "$ADB_CALLS" | tail -1)" = "shell input tap 540 1950" ] && [ $(( $(n "540 1950") % 2 )) -eq 0 ] && w=AdActivity
    echo "  mCurrentFocus=Window{1 u0 test.sdk.zd/com.app.plugin.demo.$w}";; "logcat -d") cat "$ST/log" 2> /dev/null;;
esac; exit 0
EOF
chmod +x "$TMP/qbin/adb"
SL=$TMP/sleeps; export SL
q_run() { : > "$SL"; rm -rf "$TMP/q"; mkdir -p "$TMP/q"; : > "$TMP/q/events.jsonl"; echo OFF > "$TMP/q/res"; : > "$TMP/adb-calls"; ADB_CALLS="$TMP/adb-calls" FAKE_Q_ST="$TMP/q" PATH="$TMP/qbin:$PATH" MG_NO_SLEEP=1 ANDROID_SERIAL=stub-serial bash -c 'set -u; . "$1"; MG_JSONL=$2; shift 2; mg_sleep() { echo "$1" >> "$SL"; }; "$@"; echo "rc=$?"' _ "$ROOT/tools/lib/measure_gate.sh" "$TMP/q/events.jsonl" "$@" 2>&1; }   # $@ = hàm + đối số
prepare_gate "$TMP/good.txt"; g1=$(run_gate --resume); g5=$(run_gate --resume --policy); g5rc=$?   # GR1 (hôm nay: đối số lạ) + GR5
check "GR1 --resume được nhận (không 'đối số lạ'); GR5 --resume + --policy -> FAIL loại trừ 'không đi chung một lượt'" \
  "$(hasnt "$g1" 'đối số lạ' && has "$g1" 'RESULT:' && has "$g5" 'không đi chung một lượt' && [ "$g5rc" -eq 1 ] && hasnt "$g5" 'RESULT: PASS'; echo $?)" "$g1 | $g5"
rs() { q_run mg_resume_open x "$TMP/q/events.jsonl"; }; q2=$(rs); q3=$(FAKE_Q=noopen rs); q4=$(FAKE_Q=offopen rs)
check "GR2 ON +1 ad_show full_resume, OFF +0 -> 2 dòng nguyên văn + PASS; GR3 ON +0 -> FAIL; GR4 OFF +1 -> FAIL" \
  "$(has "$q2" 'resume_open ON: shows=1 (cần 1)' && has "$q2" 'resume_open OFF: shows=0 (cần 0)' && has "$q2" 'RESULT: PASS' && has "$q3" 'resume_open ON: shows=0 (cần 1)' && has "$q3" 'RESULT: FAIL' \
    && has "$q4" 'resume_open OFF: shows=1 (cần 0)' && has "$q4" 'RESULT: FAIL' && hasnt "$q3$q4" 'RESULT: PASS'; echo $?)" "$q2 | $q3 | $q4"
f1=$(q_run mg_show_full2 'Interstitial → Full2'); f2=$(FAKE_Q=nofull2 q_run mg_show_full2 'Interstitial → Full2')
check "FD1 cha +1 và full_2_* +1 -> show_full2 'Interstitial → Full2': parent=1 full2=1 (cần 1/1), rc 0; FD2 full2 +0 -> FAIL, rc 1" \
  "$(has "$f1" "show_full2 'Interstitial → Full2': parent=1 full2=1 (cần 1/1)" && has "$f1" 'rc=0' && has "$f2" 'full2=0 (cần 1/1)' && has "$f2" 'FAIL: show_full2' && has "$f2" 'rc=1'; echo $?)" "$f1 | $f2"
f3=$(FAKE_Q=noparent q_run mg_show_full2 'Interstitial → Full2'); rf=$(FAKE_Q=fatal rs); a1=$(q_run mg_full2_all); a2=$(FAKE_Q=fatal q_run mg_full2_all); rs >/dev/null; gap=$(grep -cx 20 "$SL")
check "FD3 cha +0 (full2 +1) -> parent=0 FAIL; R5 resume có FATAL -> RESULT: FAIL dù ON 1/OFF 0; R6 khoảng cách ON->OFF = sleep 20 (MG_RESUME_GAP) đúng 1 lần" \
  "$(has "$f3" 'parent=0 full2=1 (cần 1/1)' && has "$f3" 'rc=1' && has "$rf" 'resume_open OFF: shows=0' && has "$rf" 'RESULT: FAIL' && [ "$gap" -eq 1 ]; echo $?)" "$f3 | $rf | gap=$gap"
check "F1 FATAL trong chặng Full2 -> 'FAIL: crash/ANR trong Full2'; không FATAL -> không có dòng đó" "$(has "$a2" 'FAIL: crash/ANR trong Full2' && hasnt "$a1" 'crash/ANR'; echo $?)" "$a2 | $a1"
ac=$(FAKE_Q=capfail q_run mg_full2_all); rc2=$(FAKE_Q=capfail rs)
check "F2 đọc buffer crash lỗi (không bằng chứng) -> 'FAIL: crash/ANR trong Full2' và resume RESULT: FAIL dù ON 1/OFF 0; F3 một chặng Full2 hỏng -> mg_full2_all rc=1" \
  "$(has "$ac" 'FAIL: logcat crash' && has "$ac" 'FAIL: crash/ANR trong Full2' && has "$rc2" 'resume_open OFF: shows=0' && has "$rc2" 'RESULT: FAIL' && hasnt "$rc2" 'RESULT: PASS' && has "$a1" 'rc=1'; echo $?)" "$ac | $rc2 | $a1"

# 2.4.0 FIX-GATE 2 (Samsung 1080x2340, nav 3 nút top=2205 từ dump thật): nút cuối chỉ hở dải 27 px; dump hỏng để lại ui.xml cũ; 'Show Rewarded' ≠ 'Show Rewarded Interstitial'.
mkdir -p "$TMP/sbin" "$TMP/sb"; cat > "$TMP/sbin/adb" <<'EOF'
#!/usr/bin/env bash
echo "$*" >> "$ADB_CALLS"; U=$SB/ui
case "$*" in
  "shell wm size") echo "Physical size: ${FAKE_WM:-1080x2340}";; "shell dumpsys activity top"*) cat "$NG_FX";;
  "shell rm -f /sdcard/ui.xml") rm -f "$U";;
  "shell uiautomator dump"*) c=$(cat "$SB/cnt" 2>/dev/null || echo 0); echo $((c + 1)) > "$SB/cnt"; { [ "${FAKE_STALE:-0}" = 1 ] && [ "$c" -eq 0 ]; } || printf '<hierarchy>%s</hierarchy>\n' "$GOOD" > "$U";;
  "shell am start -n"*"es drive show_rwi") echo "Starting: Intent { cmp=test.sdk.zd/.MainActivity }"; [ "${FAKE_G:-}" != ok ] || echo '{"event_name": "ad_show_fail", "placement": "rwi_default", "reason": "policy_min_gap"}' >> "$GP/events.jsonl";;
  "logcat -d") grep -q 'es drive show_rwi' "$ADB_CALLS" || exit 0   # FIX-GATE 5: FAKE_G = ok | nodrive | norefuse (demo log / SDK từ chối theo gap)
    [ "${FAKE_G:-}" = nodrive ] || echo "I AppPluginDemo: drive=intent action=show_rwi"; [ "${FAKE_G:-}" != ok ] || echo "D AppPlugin: ads rwi helper rwi_default show refused: policy_min_gap";;
  "shell cat /sdcard/ui.xml") cat "$U" 2>/dev/null;; "shell dumpsys window"*) echo "  mCurrentFocus=Window{5e6f u0 ${FAKE_FOCUS:-test.sdk.zd/com.app.plugin.demo.MainActivity}}";;
esac
exit 0
EOF
chmod +x "$TMP/sbin/adb"
nd() { n() { printf '<node text="%s" bounds="[32,%s][1048,%s]" />' "$1" "$2" "$3"; }; }; nd   # n <chữ> <y1> <y2>
sb_run() {  # $1 = nhãn tìm; $2 = XML tốt; $3 = ui.xml cũ (còn lại sau dump hỏng, FAKE_STALE=1)
  : > "$TMP/adb-calls"; rm -f "$TMP/sb/cnt"; printf '<hierarchy>%s</hierarchy>\n' "$3" > "$TMP/sb/ui"
  ADB_CALLS=$TMP/adb-calls SB=$TMP/sb GOOD=$2 NG_FX=${NG_FX:-$ROOT/tools/tests/fixtures/dumps/device-gate-R5CX10P9EKZ-20261006-ng-banners-top1.txt} PATH="$TMP/sbin:$PATH" MG_NO_SLEEP=1 \
    bash -c 'set -u; . "$1"; mg_screen_init > /dev/null; xy=$(mg_find_text "$2"); rc=$?; echo "xy=$xy rc=$rc swipes=$(grep -c "^shell input swipe" "$ADB_CALLS")"' _ "$ROOT/tools/lib/measure_gate.sh" "$1" 2>&1 | tail -1; }
RWI="Show Rewarded Interstitial"; GD="$(n "$RWI" 1100 1235)$(n 'Show Rewarded' 1300 1435)"; OLD=$(n "$RWI" 1550 1650)
l1=$(sb_run 'App open resume: OFF' "$(n 'App open resume: OFF' 2178 2313)" ""); l2=$(FAKE_STALE=1 sb_run "$RWI" "$GD" "$OLD"); l3=$(FAKE_STALE=1 sb_run 'Show Rewarded' "$GD" "$OLD")
check "LS1 nút cuối chỉ hở 27 px (2178..2313, thanh 2205): cuộn không đổi dump → chạm tâm dải y=2191 (< 2205) sau 1 kéo, rc 0 (cũ: INVALID rc 4 sau 18 kéo); LS2 dump hỏng để ui.xml cũ: tìm 'Show Rewarded Interstitial' ra y=1167 của màn mới (cũ: y=1600 của file cũ); LS3 'Show Rewarded' chỉ khớp nguyên chữ → y=1367 (không nhầm nút rwi y=1167)" \
  "$([ "$l1" = 'xy=540 2191 rc=0 swipes=1' ] && [ "$l2" = 'xy=540 1167 rc=0 swipes=0' ] && [ "$l3" = 'xy=540 1367 rc=0 swipes=0' ]; echo $?)" "LS1: $l1
LS2: $l2
LS3: $l3"

# 2.4.0 FIX-GATE 4 (D30): vùng cử chỉ đáy (emu37: tap y=2938/2992 = startRecentsAnimation), rèm/launcher giành focus. Dump inset thật của 5570 (API 37, cử chỉ, 1080x1920, mandatorySystemGestures top=1836 -> MG_NAVTOP=1788).
GF=$ROOT/tools/tests/fixtures/dumps/insets-gesture-nav-5570-api37.txt; BTN='Show Interstitial'; GW=1344x2992
g1=$(NG_FX=$GF FAKE_WM=$GW sb_run "$BTN" "$(n "$BTN" 2884 2992)" ""); g2=$(NG_FX=$GF FAKE_WM=$GW sb_run "$BTN" "$(n "$BTN" 2700 2800)" ""); g3=$(NG_FX=$GF FAKE_WM=$GW sb_run "$BTN" "$(n "$BTN" 2600 2700)" "")
check "GN1 cử chỉ thật emu37 1344x2992 (mandatorySystemGestures đáy top 2896, lề 48 → vùng chạm tới y=2848; khung đỉnh [0,0][..,187] và tappable rỗng bị bỏ qua): nút [2884,2992] KHÔNG chạm (rc 4 INVALID ui, cũ: tâm y=2938 = startRecentsAnimation); nút cắt ngang [2700,2800] chạm y=2750; nút [2600,2700] ở trên chạm y=2650" \
  "$([ "$g1" = 'xy= rc=4 swipes=18' ] && [ "$g2" = 'xy=540 2750 rc=0 swipes=0' ] && [ "$g3" = 'xy=540 2650 rc=0 swipes=0' ]; echo $?)" "$g1 | $g2 | $g3"
sh_run() {  # $1 = focus giả; $2.. = lệnh (sau mg_screen_init); in "rc=<n>" + số lệnh collapse/BACK/swipe + dòng log cuối
  : > "$TMP/adb-calls"; f=$1; shift; printf '<hierarchy>%s</hierarchy>\n' "$(n "$BTN" 1300 1435)" > "$TMP/sb/ui"
  o=$(ADB_CALLS=$TMP/adb-calls SB=$TMP/sb GOOD=$(n "$BTN" 1300 1435) FAKE_FOCUS=$f NG_FX=$GF PATH="$TMP/sbin:$PATH" MG_NO_SLEEP=1 bash -c 'set -u; . "$1"; [ -z "${PRE:-}" ] || . "$PRE"; shift; "$@"; echo "rc=$?"' _ "$ROOT/tools/lib/measure_gate.sh" "$@" 2>&1)
  echo "$(printf '%s\n' "$o" | tail -1) collapse=$(grep -c '^shell cmd statusbar collapse$' "$TMP/adb-calls") back=$(grep -c 'KEYCODE_BACK$' "$TMP/adb-calls") swipes=$(grep -c '^shell input swipe' "$TMP/adb-calls")"; printf "%s" "$o" > "$TMP/sh.out"; }
h1=$(sh_run "NotificationShade" mg_shade_reset); h2=$(sh_run "test.sdk.zd/com.app.plugin.demo.MainActivity" mg_shade_reset)
echo 'mg_phase_a() { echo "PA $1" >> "$ADB_CALLS"; [ "$1" = x-a1 ] && return 4; return 0; }' > "$TMP/pa.sh"
PRE=$TMP/pa.sh sh_run "NotificationShade" mg_phase_a_valid x > /dev/null; h3=$(grep -E '^PA |statusbar collapse' "$TMP/adb-calls" | tr '\n' ',')
h4=$(sh_run "com.android.launcher3/.NexusLauncherActivity" mg_find_text "$BTN"); h4o=$(cat "$TMP/sh.out")
h5=$(sh_run "test.sdk.zd/com.google.android.gms.ads.AdActivity" mg_tap_verified "$BTN" "showInterstitial call show"); h5o=$(cat "$TMP/sh.out")
check "SH1 focus NotificationShade: mg_shade_reset → 1 collapse + 1 BACK; focus demo → 1 collapse, 0 BACK; SH2 mg_phase_a_valid chạy lại lượt A: collapse nằm GIỮA PA x-a1 (invalid) và PA x-a2; SH3 focus launcher: sau 1 BACK dừng INVALID (ui) nêu focus, 0 swipe (cũ: 18 kéo mở rèm); SH4 AdActivity phủ + chữ vẫn thấy: ghi 'ad shown, pattern not matched', rc 4" \
  "$([ "${h1#*collapse=}" = '1 back=1 swipes=0' ] && [ "${h2#*collapse=}" = '1 back=0 swipes=0' ] && has "$h3" 'PA x-a1,shell cmd statusbar collapse,PA x-a2,' && has "$h4o" 'INVALID (ui): sau KEYCODE_BACK focus vẫn không phải demo' && [ "${h4#*swipes=}" = 0 ] && has "$h5o" 'ad shown, pattern not matched' && has "$h5o" 'rc=4'; echo $?)" "$h1 | $h2 | $h3 | $h4 | $h4o | $h5o"

# 2.4.0 FIX-GATE 5: gap-leg drive phải mang --activity-clear-top --activity-single-top (không cờ: task chỉ được đưa lên, runDrive không chạy), kiểm dòng drive=intent, luôn lưu -gap.log.
echo 'mg_show_full() { return 0; }; MG_JSONL=$GP/events.jsonl; . "$RWL"' > "$TMP/gp.sh"
gp() {  # $1 = FAKE_G; in "rc=.. gaplog=yes|no"; đầu ra đầy đủ ở $TMP/sh.out
  GP=$TMP/gp; export GP; rm -rf "$GP"; mkdir -p "$GP"; : > "$GP/events.jsonl"
  r=$(RWL=$ROOT/tools/lib/rwi_legs.sh FAKE_G=$1 PRE=$TMP/gp.sh sh_run "test.sdk.zd/com.app.plugin.demo.MainActivity" rw_leg_gap "$GP/x"); echo "${r%% *} gaplog=$([ -f "$GP/x-gap.log" ] && echo yes || echo no)"; }
gk=$(gp ok); gko=$(cat "$TMP/sh.out"); gkc=$(grep -c -- '--activity-clear-top --activity-single-top --es drive show_rwi$' "$TMP/adb-calls"); gn=$(gp nodrive); gno=$(cat "$TMP/sh.out"); gr=$(gp norefuse); gro=$(cat "$TMP/sh.out")
check "GP1 drive show_rwi mang cả hai cờ clear-top/single-top (cũ: không cờ), thấy drive=intent + từ chối theo gap → PASS rc 0, -gap.log lưu; GP2 không có dòng drive=intent → 'invalid (drive): intent không tới' rc 4, -gap.log lưu; GP3 có drive nhưng SDK không từ chối → FAIL rc 1 nêu 'demo nhận drive nhưng SDK không từ chối theo gap', -gap.log lưu" \
  "$([ "$gk" = 'rc=0 gaplog=yes' ] && [ "$gkc" -eq 1 ] && has "$gko" 'rwi gap: policy_min_gap=1' && [ "$gn" = 'rc=4 gaplog=yes' ] && has "$gno" 'invalid (drive): intent không tới' && [ "$gr" = 'rc=1 gaplog=yes' ] && has "$gro" 'demo nhận drive nhưng SDK không từ chối theo gap'; echo $?)" "$gk calls=$gkc | $gn | $gr
$gko
$gno
$gro"

dg_finish
