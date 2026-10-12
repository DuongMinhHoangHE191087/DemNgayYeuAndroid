# shellcheck shell=bash
# 1.8.0 Task 16 (D8, D11, D17): cổng G12 — 12 script của harness billing (§Hợp đồng B) chạy tuần tự trên MỘT máy, APK utility
# release, sink native + reverse. Nạp bởi tools/device_gate.sh --iap; mọi lệnh tới máy đi qua adb_b (không có lệnh trần nào ở file này, Z8), tái dùng tap/dump/focus của tools/lib/measure_gate.sh. Mỗi script: (pm clear khi cần) → am start -W với
# `--es appplugin_iap_script <tên>` → chờ log `iap harness: <tên> done` ≤ 60 s → hành động riêng của script (chạm show, force-stop, fixture RTDN, HOME + mở lại, consent) → dựng lại bằng chứng (JSONL của riêng script, logcat, meta) → tools/iap_check.py.
# Exit: 0 = cả 12 PASS; 1 = có FAIL; 4 = không FAIL nhưng có script `invalid` (giao diện/focus không tới được: không tính, chạy lại).
# pm clear trước mọi script trừ reopen (nối tiếp restore_owned, cùng cài đặt) và khi có tiền đề: restore_store_error và revoke cần
# một cài đặt ĐANG có quyền lợi — script reopen chạy port Play thật nên onStart restore của nó (store trống) thu hồi quyền lợi harness (đo 2026-10-01) — nên mỗi script đó chạy sau pm clear + restore_owned (bước mồi, cùng cài đặt) + force-stop, không pm clear giữa
# mồi và script. kill_mid_purchase → restore_unacked: cùng cài đặt, không pm clear. Script 10/12 chung token fixture RTDN: pm clear trước mỗi script, và #12 chạy trên sink MỚI (hàng iap_purchase ĐẦU TIÊN của token thắng; messageId fixture bị khử trùng). Script 11 cần
# guest ấm (#29): khởi chạy thử --ez force_eea true hai lần, form UMP phải hiện, rồi pm clear. "ack 1" đếm dòng ack first=true.

type adb_b > /dev/null 2>&1 || . "$(dirname "${BASH_SOURCE[0]}")/adb_b.sh"
type mg_start > /dev/null 2>&1 || . "$(dirname "${BASH_SOURCE[0]}")/measure_gate.sh"
type invalid_class > /dev/null 2>&1 || . "$(dirname "${BASH_SOURCE[0]}")/invalid_classes.sh"   # 2.1.0 T0d: lớp INVALID môi trường (§E)

IAP_SCRIPTS="buy_sub buy_cheap cancel pending_then_paid kill_mid_purchase restore_owned reopen restore_store_error revoke grace denied messages_off"
IAP_NOCLEAR=" reopen "
IAP_PRIME=" restore_store_error revoke "   # cần quyền lợi sẵn có: pm clear + restore_owned + force-stop trước script
IAP_DONE_TRIES=${IAP_DONE_TRIES:-30}   # x 2 s = 60 s chờ dòng `done`
IAP_RTDN_TOKEN=${BRAIN_RTDN_TOKEN:-demo-rtdn-local-token}   # cùng giá trị mặc định với tools/local_sink.sh
IAP_FIXTURE=backend/tests/fixtures/rtdn/sub_06_in_grace_period.json   # token rtdn-fixture-sub-token = token của script 10/12 (T8)

iap_lines() { if [ -f "$IAP_JSONL" ]; then wc -l < "$IAP_JSONL" | tr -d ' '; else echo 0; fi; }

# Chờ dòng logcat `iap harness: <tên> done` (thăm 2 s). 1 = không thấy trong IAP_DONE_TRIES lần.
iap_wait_done() {
  local i=0
  while [ "$i" -lt "$IAP_DONE_TRIES" ]; do
    adb_b 30 logcat -d 2> /dev/null | grep -qF "iap harness: $1 done" && return 0
    i=$((i + 1)); mg_sleep 2
  done
  mg_say "FAIL: không thấy 'iap harness: $1 done' sau $((IAP_DONE_TRIES * 2)) s (harness không arm hoặc script treo)"
  return 1
}

# Chờ sink hết nhận dòng mới (2 lần đo liền nhau bằng nhau, cách 5 s; tối đa 9 lần).
iap_settle() {
  local i=0 prev=-1 cur
  while [ "$i" -lt 9 ]; do
    cur=$(iap_lines); [ "$cur" != "$prev" ] || return 0
    prev=$cur; i=$((i + 1)); mg_sleep 5
  done
}

# Chạm chữ $1 trong giao diện (mg_find_text kéo chậm tìm cả hai chiều). 4 = không thấy.
iap_tap_label() {
  local xy frc   # ui_find: 0 = thấy (chạm), 5 = đã chạy intent drive (không chạm), 4 = INVALID (ui)
  xy=$(mg_find_text "$1"); frc=$?
  [ "$frc" -eq 0 ] || [ "$frc" -eq 5 ] || return 4
  [ "$frc" -eq 5 ] || { adb_b 60 shell input tap $xy > /dev/null 2>&1; mg_say "  tap '$1' xy=$xy"; }
  mg_sleep 3
}

# Nút của form UMP (WebView, hiện sau ≈ 10-15 s, nút nằm sẵn trong khung nhìn): thăm dump ≤ 12 lần cách 3 s, khớp `text` hoặc
# `content-desc`, KHÔNG kéo (kéo làm cuộn form). Bỏ node bounds 0/diện tích 0 (node ẩn cùng chữ, TF-1): không bao giờ chạm (0,0). In "x y"; 4 = không thấy (dump cuối lưu ở $IAP_DIR/ui-fail.xml để đọc lý do).
iap_form_xy() {
  local i=0 xml xy
  while [ "$i" -lt 12 ]; do
    adb_b 30 shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
    xml=$(adb_b 60 shell cat /sdcard/ui.xml 2> /dev/null)
    xy=$(printf '%s' "$xml" | tr '>' '\n' | grep -F -e "text=\"$1\"" -e "content-desc=\"$1\"" | mg_first_visible)
    [ -z "$xy" ] || { echo "$xy"; return 0; }
    i=$((i + 1)); mg_sleep 3
  done
  printf '%s' "$xml" > "$IAP_DIR/ui-fail.xml"
  return 4
}
# TF-2: API 28/26, form UMP dựng lại sau "Privacy options" báo MỌI node bounds 0 → không có node hiển thị. Riêng nút "Consent": chạm toạ độ
# tương đối (0.50 W, 0.70 H) của `wm size` (1080x2400 → 540 1680), kiểm bằng logcat `consent tier=GRANTED` mới; không tới → invalid (ui).
iap_tap_consent_fallback() {
  local x y before
  [ -n "${MG_W:-}" ] || mg_screen_init
  x=$((MG_W * 50 / 100)); y=$((MG_H * 70 / 100))
  before=$(mg_count "consent tier=GRANTED")
  mg_say "consent tap fallback $x,$y (no visible node)"
  adb_b 60 shell input tap "$x" "$y" > /dev/null 2>&1
  mg_sleep 3
  if [ "$(mg_count "consent tier=GRANTED")" -gt "$before" ]; then mg_say "  tap form 'Consent' fallback xy=$x $y: 'consent tier=GRANTED' tới app"; return 0; fi
  mg_say "invalid (ui): chạm fallback $x,$y của 'Consent', logcat không có 'consent tier=GRANTED' mới"
  return 4
}
iap_tap_form() {
  local xy
  xy=$(iap_form_xy "$1") || { [ "$1" != Consent ] || { iap_tap_consent_fallback; return $?; }; mg_say "invalid (ui): không thấy nút '$1' hiển thị (bounds > 0) của form (dump: $IAP_DIR/ui-fail.xml)"; return 4; }
  adb_b 60 shell input tap $xy > /dev/null 2>&1
  mg_say "  tap form '$1' xy=$xy"
  mg_sleep 3
}

# Một lượt show theo §B: chạm nút (kiểm bằng logcat), chờ, về app. Show bị remove-ads chặn KHÔNG in `call show`
# (controller chặn trước) mà in `ads helper <placement> show blocked: <lý do>` (AdsHelper.reportDisabledShow): mẫu theo script.
iap_show() {
  mg_tap_verified "$1" "$2" || return 4
  mg_sleep 5
  mg_back_in_app "$1" || return $?
}
mg_first_tap_count() { echo $(($(iap_lines) - IAP_B0)); }   # hook của mg_tap_verified (MG_FIRST_TAP_META): hàng sink lúc cú chạm đầu
iap_shows() {
  local pi="showInterstitial call show" pr="showRewarded call show" pw=""
  case $1 in
    buy_sub) pi="ads helper full_default show blocked"; pr="ads helper gift_default show blocked";;
    buy_cheap) pi="ads helper full_default show blocked"; pw="ads helper rwi_default show blocked";;   # 2.0.0 Task 0c (D53): rwi cũng bị chặn
  esac
  iap_show "Show Interstitial" "$pi" && iap_show "Show Rewarded" "$pr" && { [ -z "$pw" ] || iap_show "Show Rewarded Interstitial" "$pw"; }
}

# 2.0.0 Task 0c: buy_cheap chạm cả rwi -> đúng 1 hàng ad_show_fail reason=remove_ad placement=rwi_default sau mốc show_from ($1 = tiền tố script).
iap_rwi_blocked() {
  local from n
  from=$(sed -n 's/^show_from=//p' "$1.meta" | tail -1)
  n=$(tail -n +$((${from:-0} + 1)) "$1.jsonl" 2> /dev/null | grep -F '"event_name": "ad_show_fail"' | grep -F '"placement": "rwi_default"' | grep -cF '"reason": "remove_ad"')
  mg_say "  buy_cheap rwi: ad_show_fail reason=remove_ad placement=rwi_default = $n (cần 1)"
  [ "$n" -eq 1 ] || { mg_say "FAIL: buy_cheap: ad_show_fail reason=remove_ad placement=rwi_default = $n (cần đúng 1)"; return 1; }
}

# Fixture RTDN IN_GRACE_PERIOD vào /rtdn của sink (bearer demo); cần 200 + stored=true.
iap_rtdn() {
  local body="$IAP_DIR/rtdn-response.json" code
  code=$(curl -sS -m 10 -o "$body" -w '%{http_code}' -X POST "http://127.0.0.1:$IAP_PORT/rtdn" \
    -H "Authorization: Bearer $IAP_RTDN_TOKEN" -H 'Content-Type: application/json' --data "@$IAP_FIXTURE") || code=000
  mg_say "  rtdn POST /rtdn -> $code $(head -c 160 "$body" 2> /dev/null)"
  [ "$code" = 200 ] && grep -q '"stored": *true' "$body"
}

# HOME rồi mở lại (một foreground). 4 = focus không về app. $2 = "push": chờ ≤ 20 s một /push (JSONL của sink tăng) sau HOME rồi mới
# mở lại, vì nhãn server tới SDK trong phản hồi /push SAU RTDN (R-16 m3); không thấy push chỉ ghi log, kết luận là của iap_check.
iap_fg() {
  local l0 i=0
  l0=$(iap_lines)
  adb_b 60 shell input keyevent KEYCODE_HOME > /dev/null 2>&1
  mg_sleep 3
  if [ "${2:-}" = push ]; then
    while [ "$i" -lt 10 ] && [ "$(iap_lines)" = "$l0" ]; do mg_sleep 2; i=$((i + 1)); done
    mg_say "  $1: sau HOME sink $l0 -> $(iap_lines) dòng (push $([ "$(iap_lines)" != "$l0" ] && echo có || echo chưa thấy))"
  fi
  mg_back_in_app "$1" || return $?
  mg_sleep 10
}

# Logcat đầy đủ của script + buffer crash/events nối cuối (FATAL/native/am_anr do iap_check đếm); lỗi/hết hạn = 1.
iap_save_log() {
  local rc=0 r
  adb_b 30 logcat -d 2> /dev/null | redact_stream > "$1.log"; r=${PIPESTATUS[0]}; [ "$r" -eq 0 ] || { mg_logcat_fail "script $1" "$r"; rc=1; }
  adb_b 30 logcat -d -b crash 2> /dev/null | redact_stream >> "$1.log"; r=${PIPESTATUS[0]}; [ "$r" -eq 0 ] || { mg_logcat_fail "crash $1" "$r"; rc=1; }
  adb_b 30 logcat -d -b events 2> /dev/null | grep --text am_anr | redact_stream >> "$1.log"
  return $rc
}

# StrictMode by-sdk 0 mỗi script (Hợp đồng B, R-16 I1): count_strict của device_gate_r8.sh (retrace bằng --mapping). 0 = đạt, 1 = FAIL.
iap_strict() {
  local saved=${fail:-0} rc=0
  fail=0; count_strict "iap $1" "$2.log" "${MAP:-}"
  [ "$fail" -eq 0 ] || rc=1
  fail=$saved
  [ "$rc" -eq 0 ] || mg_say "FAIL: $1: StrictMode do SDK > 0 (hoặc không retrace được): stack trong log cổng"
  return $rc
}

# Hành động riêng theo script SAU dòng `done`. 0 ok, 4 invalid, 1 hỏng.
iap_after() {
  local name=$1 pre=$2 lines r
  case $name in
    buy_sub | buy_cheap | revoke)
      iap_settle   # mốc: ad_show_fail khởi động của demo nằm trước mốc này, iap_check chỉ đếm hàng sau nó
      echo "show_from=$(($(iap_lines) - IAP_B0))" >> "$pre.meta"
      MG_FIRST_TAP_META="$pre.meta" iap_shows "$name";;
    kill_mid_purchase)
      adb_b 60 shell am force-stop "$MG_PKG" > /dev/null 2>&1
      mg_start --es appplugin_iap_script restore_unacked || return 1
      iap_wait_done restore_unacked || return 1;;
    grace | messages_off)
      iap_settle
      iap_rtdn || { mg_say "FAIL: /rtdn không nhận fixture (token/bearer/sink)"; return 1; }
      iap_fg "$name fg1" push || return $?
      adb_b 30 logcat -d 2> /dev/null | redact_stream > "$pre.fg1"; r=${PIPESTATUS[0]}; [ "$r" -eq 0 ] || { mg_logcat_fail "fg1 $name" "$r"; return 1; }
      lines=$(wc -l < "$pre.fg1" | tr -d ' '); echo "fg1_lines=$lines" >> "$pre.meta"
      [ "$name" = grace ] || return 0
      iap_fg "$name fg2";;
  esac
}

# Script 11 trước `done`: chạm "Do not consent" (form EEA), ghi mốc DENIED; sau `done` chạm Privacy options → Consent, ghi mốc GRANTED.
iap_denied_pre() {
  mg_sleep 8
  iap_tap_form "Do not consent" || return $?
  echo "denied_at=$(($(iap_lines) - $1))" >> "$2.meta"
}
iap_denied_post() {
  iap_settle
  echo "granted_at=$(($(iap_lines) - $1))" >> "$2.meta"
  iap_tap_label "Privacy options" || return $?
  mg_sleep 5
  iap_tap_form "Consent" || return $?
  mg_sleep 5
}

# Script 11 (#29): khởi chạy thử hai lần với force_eea, form UMP phải hiện (không thì `invalid (cold guest)`); rồi pm clear ở iap_one.
iap_warm_consent() {
  local i out
  out=$(adb_b 60 shell pm clear "$MG_PKG" 2>&1 | tr -d '\r'); [ "$out" = Success ] || { mg_say "FAIL: pm clear $MG_PKG: $out"; return 1; }
  for i in 1 2; do
    mg_start --ez force_eea true || return 1
    mg_sleep 10
    adb_b 60 shell am force-stop "$MG_PKG" > /dev/null 2>&1
  done
  mg_start --ez force_eea true || return 1
  mg_sleep 10
  iap_form_xy "Do not consent" > /dev/null || { mg_say "invalid (cold guest): form UMP không hiện sau hai lần chạy thử (dump: $IAP_DIR/ui-fail.xml)"; return 4; }
  adb_b 60 shell am force-stop "$MG_PKG" > /dev/null 2>&1
}

# Bước mồi: quyền lợi harness (restore_owned) trên cài đặt vừa pm clear, rồi force-stop để script chính arm ở process mới.
iap_prime() {
  mg_say "  mồi: restore_owned trước $1"
  mg_start --es appplugin_iap_script restore_owned || return 1
  iap_wait_done restore_owned || return 1
  adb_b 60 shell am force-stop "$MG_PKG" > /dev/null 2>&1
}

# Script 12 cần sink mới: dừng sink hiện tại (thư mục $1) và dựng sink ở thư mục riêng của script, cùng cổng (tunnel reverse của cổng giữ nguyên).
iap_sink_fresh() {
  local d="$IAP_DIR/sink12"
  bash tools/local_sink.sh stop --port "$IAP_PORT" --dir "${IAP_SINK_ROT:-$1}" || return 1
  mkdir -p "$d" && bash tools/local_sink.sh start --port "$IAP_PORT" --dir "$d" || { mg_say "FAIL: không dựng được sink mới cho script 12"; return 1; }
  IAP_SINK_ROT=$d; IAP_JSONL=$d/events.jsonl
  mg_say "  sink mới cho script 12: $IAP_JSONL"
}

# 1.8.0 TF-1: JSONL của lượt G12 nằm ở thư mục MỚI <sink-dir>/g12-<serial>-<giờ> do cổng dựng/dừng (sink của lane dừng, cùng cổng): khử
# trùng messageId/token sớm nhất của lượt G11/G12 trước trong sink chung không làm script 10 nhận stored:false.
iap_sink_run() {
  local d n=0 base="$IAP_SINK_DIR/g12-${ANDROID_SERIAL:-device}-$(date +%Y%m%d-%H%M%S)"
  d=$base
  while ! mkdir -p "$IAP_SINK_DIR" || ! mkdir "$d" 2> /dev/null; do n=$((n + 1)); d=$base-$n; [ "$n" -lt 100 ] || { mg_say "FAIL: không tạo được thư mục sink G12 dưới $IAP_SINK_DIR"; return 1; }; done
  bash tools/local_sink.sh stop --port "$IAP_PORT" --dir "$IAP_SINK_DIR" || { mg_say "FAIL: không dừng được sink của lane"; return 1; }
  bash tools/local_sink.sh start --port "$IAP_PORT" --dir "$d" || { mg_say "FAIL: không dựng được sink G12 ($d)"; return 1; }
  IAP_SINK_ROT=$d; IAP_JSONL=$d/events.jsonl
  mg_say "sink G12 riêng: $IAP_JSONL"
}

# Một script. $1 = số thứ tự, $2 = tên. 0 PASS, 1 FAIL, 4 invalid.
iap_one() {
  local n=$1 name=$2 pre out b0 rc=0 extra="" cls
  pre=$(printf '%s/%02d-%s' "$IAP_DIR" "$n" "$name")
  mg_say "--- script $n/12: $name"
  trap 'exit 130' INT; trap 'exit 143' TERM   # adb_b gỡ trap sau mỗi lệnh: đặt lại để kill/Ctrl-C chạy trap EXIT (dọn sink, R-16 m4)
  adb_b 60 shell am force-stop "$MG_PKG" > /dev/null 2>&1
  if [ "$name" = denied ]; then iap_warm_consent || return $?; fi
  if [ "$name" = messages_off ]; then iap_sink_fresh "$IAP_SINK_DIR" || return 1; fi
  case "$IAP_NOCLEAR" in
    *" $name "*) ;;
    *) out=$(adb_b 60 shell pm clear "$MG_PKG" 2>&1 | tr -d '\r'); [ "$out" = Success ] || { mg_say "FAIL: pm clear $MG_PKG: $out"; return 1; };;
  esac
  case "$IAP_PRIME" in *" $name "*) iap_prime "$name" || return 1;; esac
  adb_b 60 logcat -c
  : > "$pre.meta"; b0=$(iap_lines); IAP_B0=$b0
  [ "$name" != denied ] || extra="--ez force_eea true"
  mg_start --es appplugin_iap_script "$name" $extra || return 1
  if [ "$name" = denied ]; then iap_denied_pre "$b0" "$pre" || return $?; fi
  iap_wait_done "$name" || return 1
  iap_after "$name" "$pre" || rc=$?
  [ "$rc" -eq 0 ] || return "$rc"
  if [ "$name" = denied ]; then iap_denied_post "$b0" "$pre" || return $?; fi
  iap_settle
  iap_save_log "$pre" || return 1
  if [ -f "$IAP_JSONL" ]; then tail -n +$((b0 + 1)) "$IAP_JSONL" > "$pre.jsonl"; fi
  iap_strict "$name" "$pre" || rc=1
  [ "$name" != buy_cheap ] || iap_rwi_blocked "$pre" || rc=1
  python3 tools/iap_check.py "$name" --jsonl "$pre.jsonl" --applog "$pre.log" --meta "$pre.meta" | tee -a "${MG_LOG:-/dev/null}"
  if [ "${PIPESTATUS[0]}" -ne 0 ]; then   # §E: lượt hỏng + lớp môi trường trong log (FATAL/ANR/native thắng) = invalid, không tính; StrictMode/rwi hỏng vẫn FAIL
    cls=$(invalid_class "$MG_PKG" "$pre.log"); case $cls in '' | FAIL) rc=1;; *) [ "$rc" -ne 0 ] || { mg_say "invalid $cls: $name"; return 4; }; rc=1;; esac
  fi
  return "$rc"
}

# $1 = tiền tố thư mục bằng chứng, $2 = JSONL của sink, $3 = cổng sink, $4 = thư mục sink (để dựng sink mới cho script 12).
# In từng script và `RESULT:` cuối (vào $MG_LOG).
iap_run() {
  local n=0 name rc pass=0 failed="" invalid="" ic icls=""
  IAP_DIR=$1; IAP_JSONL=$2; IAP_PORT=$3; IAP_SINK_DIR=$4; IAP_SINK_ROT=""
  mkdir -p "$IAP_DIR" || { mg_say "FAIL: không tạo được $IAP_DIR"; mg_say "RESULT: FAIL"; return 1; }
  iap_sink_run || { mg_say "RESULT: FAIL"; return 1; }
  mg_screen_init
  for name in $IAP_SCRIPTS; do
    n=$((n + 1)); rc=0
    iap_one "$n" "$name" || rc=$?
    case $rc in
      0) pass=$((pass + 1));;
      4) ic=$(grep '^invalid' "${MG_LOG:-/dev/null}" 2> /dev/null | tail -1); invalid="$invalid $name(${ic:0:120})"; icls=${icls:-$(sed -E 's/^invalid (\([^)]*\)).*/\1/' <<< "$ic")}; mg_say "  => $name: invalid (không tính, chạy lại)";;
      *) failed="$failed $name"; mg_say "  => $name: FAIL";;
    esac
  done
  [ -z "$IAP_SINK_ROT" ] || bash tools/local_sink.sh stop --port "$IAP_PORT" --dir "$IAP_SINK_ROT"
  mg_say "iap: $pass/12 PASS; FAIL:${failed:- không}; invalid:${invalid:- không}"
  if [ -n "$failed" ]; then mg_say "RESULT: FAIL"; return 1; fi
  if [ -n "$invalid" ]; then mg_say "RESULT: INVALID ${icls:-(ui)}"; return 4; fi
  mg_say "RESULT: PASS"
}
