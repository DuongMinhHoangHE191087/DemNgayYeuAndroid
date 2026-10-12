# shellcheck shell=bash
# 2.0.0 Task 0c (D53, E-emu-37 #17): `device_gate.sh --drc` = D-RC (harness brain, 1.9.0 Task 5s) 3 chặng + ĐỐI CHỨNG, thay driver tạm của E-emu-37.
# Nạp bởi device_gate.sh --drc SAU tools/lib/measure_gate.sh (mg_*); mọi lệnh tới máy qua adb_b; MG_NO_SLEEP=1 chỉ cho test. 0 ghi Firebase/GCP/Remote
# Config (D28): policy vào bằng extra `brain_policy` của intent, sink là sink cục bộ của lane. Mỗi chặng: pm clear -> am start -W --es brain_policy <json>
# -> chờ dòng nạp -> (chặng 1, 2) dc_taps: nút "Show Interstitial (brain)" (cùng kịch bản cho cả hai chặng) -> đếm hàng sink `brain_decision` SAU dòng nạp.
#   chặng 1: policy calibrated (backend/tests/fixtures/brain_policy_calibrated.json, rollout_fraction đặt 1.0: fixture 0.1 chỉ đưa 10 % người chơi vào nhóm brain nên chặng 1 ngẫu nhiên) -> `brain harness: armed` + brain_decision >= 1.
#   ĐỐI CHỨNG: chặng 1 = 0 hàng thì FAIL (R8): kịch bản chạm không sinh quyết định thì "0" ở chặng 2 không chứng minh gì.
#   chặng 2: {"enabled":false} (kill) -> 0 brain_decision sau dòng nạp, dù cùng kịch bản chạm.
#   chặng 3: {"rollout_fraction":0} rồi cold start KHÔNG extra -> policy được giữ (dòng nạp lại, không `armed`).
# Return: 0 PASS, 1 FAIL, 4 INVALID (UMP hết hạn ở chặng 1: môi trường). Đã in RESULT.

DC_POLICY=${DC_POLICY:-backend/tests/fixtures/brain_policy_calibrated.json}
DC_LOAD_TRIES=${DC_LOAD_TRIES:-15}   # x 2 s chờ dòng nạp

# Số hàng brain_decision sau dòng sink $1. DC_POINT khác rỗng -> chỉ hàng có decision_point đó (chặng 1: INTERSTITIAL_OPPORTUNITY, đúng cổng sản phẩm; chặng 2: mọi hàng).
# policy_version của hàng là hằng của engine ("brain_v0_skeleton"), KHÔNG phải version artifact, nên không lọc theo nó.
DC_POINT=""
dc_decisions() {
  local pat=""
  [ -z "$DC_POINT" ] || pat="\"decision_point\": \"$DC_POINT\""
  tail -n +$(($1 + 1)) "$MG_JSONL" 2> /dev/null | grep -F '"event_name": "brain_decision"' | grep -cF -- "$pat"
}

# Lý do show bị từ chối trong chặng (hàng ad_show_fail của sink sau dòng $1 + số dòng "brain declined" của logcat): in vào dòng FAIL của chặng 1 để IG phân loại.
dc_refusals() {
  local r
  r=$(tail -n +$(($1 + 1)) "$MG_JSONL" 2> /dev/null | grep -F '"event_name": "ad_show_fail"' | grep -o '"reason": "[^"]*"' | sed 's/"reason": //' | sort | uniq -c | tr -s ' \n' ' ')
  echo "ad_show_fail:${r:- không} brain_declined=$(mg_count 'showInterstitial brain declined')"
}

# dc_stage <tên> <json>: pm clear, mở với extra, chờ dòng `BrainPolicy: đã nạp '<version>'`; đặt DC_VER, DC_J0 (dòng sink ngay sau dòng nạp). return 1 nếu hỏng.
dc_stage() {
  local name=$1 json=$2 i=0 out
  DC_VER=$(printf '%s' "$json" | sed -n 's/.*"version" *: *"\([^"]*\)".*/\1/p')
  case $json in *\'*) mg_say "FAIL: $name: json có dấu nháy đơn, không truyền được qua shell của máy"; return 1;; esac
  mg_say "=== $name: pm clear, am start -W --es brain_policy (version=$DC_VER, ${#json} byte)"
  out=$(adb_b 60 shell pm clear "$MG_PKG" 2>&1 | tr -d '\r'); [ "$out" = Success ] || { mg_say "FAIL: pm clear $MG_PKG: $out"; return 1; }
  adb_b 60 logcat -c > /dev/null 2>&1
  adb_b 60 shell "am start -W -n $MG_PKG/$MG_ACT --es brain_policy '$json'" > /dev/null 2>&1 || { mg_say "FAIL: $name: am start -W lỗi/quá hạn"; return 1; }
  while [ "$i" -lt "$DC_LOAD_TRIES" ] && [ "$(mg_count "BrainPolicy: đã nạp '$DC_VER'")" -lt 1 ]; do mg_sleep 2; i=$((i + 1)); done
  [ "$(mg_count "BrainPolicy: đã nạp '$DC_VER'")" -ge 1 ] || { mg_say "FAIL: $name: không thấy dòng nạp BrainPolicy '$DC_VER' (harness không arm: khoá flag/manifest/extra?)"; return 1; }
  DC_J0=$(mg_jsonl_lines)
  mg_say "  $name: đã nạp '$DC_VER' (sink dòng $DC_J0), armed=$(mg_count 'brain harness: armed')"
}

# Sinh quyết định qua đường sản phẩm: nút demo "Show Interstitial (brain)" (isDeltaTime mặc định -> InterstitialController hỏi AdsBrain.shouldShowInterstitial ->
# engine.decide -> brain_decision, kể cả khi brain từ chối; CHỈ khi quảng cáo đã nạp: chưa nạp thì dừng ở "show not loaded", trước brain, rồi SDK nạp lại).
# Chỉ đường này tôn trọng kill-switch/rollout của artifact. KHÔNG dùng Brain Playground (engine.decide thẳng, bỏ qua kill; công cụ dev) và KHÔNG dùng nút
# "Show Interstitial" (isDeltaTime=false: brain không được hỏi, --measure/--iap cần đúng hành vi đó). Lặp tối đa 3 lượt tới khi có hàng quyết định hoặc
# quảng cáo đã hiện (`full_default ... onshowed`, DC_SHOWN): để "0" của chặng 2 không rỗng vì quảng cáo chưa nạp. Mỗi lượt chờ ≤ 15 s; cuối đợi sink 10 s.
DC_BTN="Show Interstitial (brain)"
dc_taps() {
  local a=0 i xy
  DC_SHOWN=0
  while [ "$a" -lt 3 ] && [ "$(dc_decisions "$DC_J0")" -lt 1 ] && [ "$DC_SHOWN" -lt 1 ]; do
    a=$((a + 1)); i=0
    xy=$(mg_find_text "$DC_BTN") || { mg_say "invalid (ui): không thấy nút '$DC_BTN' (APK demo cũ hơn 2.0.0?)"; return 4; }
    adb_b 60 shell input tap $xy > /dev/null 2>&1
    mg_say "  tap '$DC_BTN' xy=$xy (lượt $a)"
    while [ "$i" -lt 15 ] && [ "$(dc_decisions "$DC_J0")" -lt 1 ] && [ "$(mg_count 'full_default full_default onshowed')" -lt 1 ]; do mg_sleep 1; i=$((i + 1)); done
    mg_say "  [thông tin] chạm → hàng brain_decision: ~${i} s (thăm 1 s, trần 15 s; lượt $a; so với '0' của chặng 2)"   # 2.1.0 T0e (M20)
    mg_sleep 3
    DC_SHOWN=$(mg_count "full_default full_default onshowed")
    mg_back_in_app "drc brain" > /dev/null || return $?
  done
  mg_sleep 10
  mg_say "  dc_taps: $a lượt '$DC_BTN', quảng cáo đã hiện=$DC_SHOWN, brain_decision=$(dc_decisions "$DC_J0")"
}

# $1 = tiền tố file, $2 = JSONL của sink, $3 = số dòng JSONL trước cổng. In "RESULT: PASS|FAIL"; return 0/1.
drc_run() {
  local pre=$1 fail=0 json n1 n2 rc
  MG_JSONL=$2
  mg_say "drc: 3 chặng harness brain + đối chứng; policy=$DC_POLICY; 0 ghi Firebase/Remote Config (D28); jsonl=$MG_JSONL"
  json=$("${MEASURE_PY:-python3}" -c 'import json,sys; d = json.load(open(sys.argv[1])); d.setdefault("experiment", {})["rollout_fraction"] = 1.0; print(json.dumps(d, separators=(",", ":")))' "$DC_POLICY" 2> /dev/null)
  [ -n "$json" ] || { mg_say "FAIL: không đọc được policy $DC_POLICY"; mg_say "RESULT: FAIL"; return 1; }
  mg_screen_init
  # Khởi chạy thử ×2 (#29, guest ấm): lần mở đầu của máy mới UMP hết hạn 5 s (`info update timed out`) nên tier UNKNOWN, 0 quảng cáo, 0 hàng sink (smoke 5570).
  for n1 in 1 2; do mg_start || { mg_say "RESULT: FAIL"; return 1; }; mg_sleep 10; adb_b 60 shell am force-stop "$MG_PKG" > /dev/null 2>&1; done
  # chặng 1
  DC_POINT=INTERSTITIAL_OPPORTUNITY
  dc_stage "chặng 1 (calibrated)" "$json" || { mg_save_logs "$pre-s1"; mg_say "RESULT: FAIL"; return 1; }
  [ "$(mg_count 'brain harness: armed')" -ge 1 ] || { mg_say "FAIL: chặng 1: không thấy 'brain harness: armed'"; fail=1; }
  dc_taps; rc=$?; [ "$rc" -eq 0 ] || { mg_say "invalid/FAIL: chặng 1: kịch bản chạm không tới app (rc=$rc)"; fail=1; }
  mg_save_logs "$pre-s1"
  n1=$(dc_decisions "$DC_J0")
  mg_say "drc chặng 1: brain_decision=$n1 (cần >= 1, decision_point=$DC_POINT)"
  if [ "$n1" -lt 1 ] && [ "$(mg_count 'ConsentManager: info update timed out')" -ge 1 ]; then
    mg_say "invalid (cold guest): chặng 1 UMP info update timed out (tier UNKNOWN, quảng cáo không nạp): môi trường, không tính, chạy lại"; mg_say "RESULT: INVALID (consent info update timed out)"; return 4
  fi
  [ "$n1" -ge 1 ] || { mg_say "FAIL: đối chứng: chặng 1 brain_decision=0 (INTERSTITIAL_OPPORTUNITY): kịch bản chạm không sinh quyết định ở cổng sản phẩm nên '0' của chặng 2 không có giá trị; $(dc_refusals "$DC_J0")"; fail=1; }
  DC_POINT=""   # chặng 2: nghiêm — mọi brain_decision đều là lỗi
  # chặng 2
  if dc_stage "chặng 2 (kill)" '{"version":"kill-r17","enabled":false}'; then
    dc_taps || mg_say "  chặng 2: chạm show rc=$?"
    mg_save_logs "$pre-s2"
    n2=$(dc_decisions "$DC_J0")
    mg_say "drc chặng 2: brain_decision sau dòng nạp = $n2 (cần 0), quảng cáo đã hiện=$DC_SHOWN (cần >= 1: luật tĩnh vẫn hiện, nên 0 không rỗng)"
    [ "$DC_SHOWN" -ge 1 ] || { mg_say "FAIL: chặng 2: quảng cáo không hiện (chưa nạp?): 0 brain_decision không chứng minh kill-switch"; fail=1; }
    [ "$n2" -eq 0 ] || { mg_say "FAIL: chặng 2: kill-switch mà vẫn có $n2 hàng brain_decision sau dòng nạp"; fail=1; }
  else mg_save_logs "$pre-s2"; fail=1; fi
  # chặng 3
  if dc_stage "chặng 3 (rollout_fraction 0)" '{"version":"roll0-r17","rollout_fraction":0}'; then
    adb_b 60 shell am force-stop "$MG_PKG" > /dev/null 2>&1; mg_sleep 3
    adb_b 60 logcat -c > /dev/null 2>&1
    mg_start || fail=1
    mg_sleep 30
    mg_save_logs "$pre-s3"
    n1=$(grep -c "BrainPolicy: đã nạp 'roll0-r17'" "$pre-s3.log" 2> /dev/null); n2=$(grep -c 'brain harness: armed' "$pre-s3.log" 2> /dev/null)
    mg_say "drc chặng 3: cold start không extra: đã nạp roll0-r17=$n1 (cần >= 1), armed=$n2 (cần 0)"
    { [ "$n1" -ge 1 ] && [ "$n2" -eq 0 ]; } || { mg_say "FAIL: chặng 3: policy không được giữ qua cold start (hoặc harness arm lại không extra)"; fail=1; }
  else fail=1; fi
  mg_count_crashes drc
  mg_say "FATAL=$MG_FATAL am_anr=$MG_ANR native=$MG_NATIVE (need 0/0/0); Firebase/Remote Config writes by this gate: 0"
  { [ "$MG_FATAL" -eq 0 ] && [ "$MG_ANR" -eq 0 ] && [ "$MG_NATIVE" -eq 0 ]; } || fail=1
  { [ "${MG_CAPTURE_FAIL:-0}" -eq 0 ] && ! grep -q '^FAIL: adb_b timeout' "${MG_LOG:-/dev/null}"; } || fail=1
  mg_say "RESULT: $([ "$fail" -eq 0 ] && echo PASS || echo FAIL)"
  return "$fail"
}
