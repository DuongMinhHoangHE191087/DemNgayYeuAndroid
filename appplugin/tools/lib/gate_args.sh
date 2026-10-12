# shellcheck shell=bash
# 2.0.0 Task 0d (D8): phân tích đối số của tools/device_gate.sh, chuyển NGUYÊN VĂN ra lib (0 đổi hành vi) để device_gate.sh không lớn thêm.
# Được `source` bởi device_gate.sh (dùng chung "$@": shift ở đây là shift của cổng) TRƯỚC `cd` về gốc repo, vì đường dẫn tương đối
# của --apk/--mapping/--sink-dir tính theo thư mục người gọi. 0 đổi hành vi so với bản trong device_gate.sh trừ cờ --consent. Đặt VARIANT, APK, MAP, cờ kịch bản, SINK_PORT, SINK_DIR, ANDROID_SERIAL; sai thì FAIL + exit 1.
VARIANT=utility
APK=""; MAP=""; APP=""; PKG=test.sdk.zd; ACT=com.app.plugin.demo.MainActivity; EEA_ARGS="--ez force_eea true"; MEASURE=0; IAP=0; BANNERS=0; CONSENT=0; DRC=0; POLICY=0; RESUME=0; NOSINK=0; UI_DRIVE_INTENT=0; export UI_DRIVE_INTENT
SINK_PORT=8089   # 1.8.0 Task 1 (D17): cổng + thư mục sink theo lane; reverse tcp:8089 tcp:$SINK_PORT
SINK_DIR=${SINK_DIR:-DOC/testing/local-sink}
bad_arg() { echo "FAIL: $1"; echo "RESULT: FAIL"; exit 1; }
while [ $# -gt 0 ]; do
  case $1 in
    --variant) [ $# -ge 2 ] || bad_arg "--variant phải là utility|game (nhận '')"; VARIANT=$2; shift 2;;
    --apk) [ $# -ge 2 ] || bad_arg "--apk cần một đường dẫn"; APK=$2; shift 2;;
    --mapping) [ $# -ge 2 ] || bad_arg "--mapping cần một đường dẫn"; MAP=$2; shift 2;;
    --app) [ $# -ge 2 ] || bad_arg "--app cần <pkg>/<activity>"; APP=$2; shift 2;;   # 2.2.0 Task 0c (D6-220): cổng cơ bản cho app bất kỳ
    --measure) MEASURE=1; shift;;
    --iap) IAP=1; shift;;
    --banners) BANNERS=1; shift;;
    --drc) DRC=1; shift;;   # 2.0.0 Task 0c: harness brain 3 chặng + đối chứng (tools/lib/drc_legs.sh)
    --policy) POLICY=1; shift;;   # 2.3.0 SECBH: client policy trên sink fake mode (tools/lib/policy_legs.sh), sink riêng cổng free_port.sh
    --resume) RESUME=1; shift;;   # 2.4.0 DEMO-RES: nút "App open resume" của demo, HOME -> mở lại (tools/lib/measure_gate.sh mg_resume_open)
    --consent) CONSENT=1; shift;;   # 2.0.0 Task 0d: DENIED -> GRANTED (tools/lib/consent_gate.sh)
    --drive-intent) UI_DRIVE_INTENT=1; shift;;   # 2.1.0 Task 0c: nút không thấy → am start ... --es drive <action> (tools/lib/ui_find.sh)
    --serial) [ $# -ge 2 ] || bad_arg "--serial cần một serial (vd emulator-5580)"; ANDROID_SERIAL=$2; export ANDROID_SERIAL; shift 2;;
    --sink-port) [ $# -ge 2 ] || bad_arg "--sink-port cần một số cổng (1024..65535)"; SINK_PORT=$2; SINK_PORT_ARG=1; shift 2;;
    --sink-dir) [ $# -ge 2 ] || bad_arg "--sink-dir cần một đường dẫn"; SINK_DIR=$2; SINK_DIR_ARG=1; shift 2;;
    *) bad_arg "đối số lạ '$1' (chỉ nhận --variant utility|game, --apk <apk>, --mapping <mapping.txt>, --app <pkg>/<activity>, --measure, --iap, --banners, --consent, --drc, --policy, --resume, --drive-intent, --serial <s>, --sink-port <p>, --sink-dir <d>)";;
  esac
done
case $SINK_PORT in ''|*[!0-9]*) bad_arg "--sink-port phải là số trong 1024..65535 (nhận '$SINK_PORT')";; esac
{ [ "$SINK_PORT" -ge 1024 ] && [ "$SINK_PORT" -le 65535 ]; } || bad_arg "--sink-port phải trong 1024..65535 (nhận '$SINK_PORT')"
[ "$((MEASURE + IAP + BANNERS + CONSENT + DRC + POLICY + RESUME))" -le 1 ] || bad_arg "--measure, --iap, --banners, --consent, --drc, --policy và --resume không đi chung một lượt"
if [ -n "$APP" ]; then   # PKG/ACT thay giá trị demo; force_eea chỉ cho gói mặc định; --mapping tuỳ chọn (không retrace)
  case $APP in ?*/?*) PKG=${APP%%/*}; ACT=${APP#*/};; *) bad_arg "--app phải có dạng <pkg>/<activity> (nhận '$APP')";; esac
  [ "$((MEASURE + IAP + BANNERS + CONSENT + DRC + POLICY + RESUME))" -eq 0 ] || bad_arg "--app + --measure/--iap/--banners/--consent/--drc/--policy/--resume: chế độ chỉ cho demo"
  EEA_ARGS=""
  [ -n "${SINK_PORT_ARG:-}${SINK_DIR_ARG:-}" ] || NOSINK=1   # 2.2.0 T0e: app thứ ba không có sink 8089; kiểm demo (canary StrictMode, af_app_id) mặc định 0 = dòng info, env vẫn ghi đè
  export STRICT_ARMED_MIN=${STRICT_ARMED_MIN:-0} AF_STARTED_MIN=${AF_STARTED_MIN:-0}
fi
[ "$POLICY" != 1 ] || NOSINK=1   # --policy tự dựng sink (cổng trống) nên không đòi sink 8089
STACK=$VARIANT
case $VARIANT in
  utility|game) ;;
  nextgen) VARIANT=utility;;   # 2.4.0 NG6 (P25): APK nextgen chạy cổng như utility (harness, sink 8089, mọi chế độ); STACK giữ tên để người đọc log phân biệt
  *) bad_arg "--variant phải là utility|game (nhận '$VARIANT')";;
esac
if [ -z "${ANDROID_SERIAL:-}" ]; then
  echo "FAIL: đặt ANDROID_SERIAL (không dùng R5CX10P9EKZ, 65c9b673)"
  echo "RESULT: FAIL"
  exit 1
fi
[ -n "$APK" ] || bad_arg "--apk <apk> bắt buộc (bản đóng băng, không đọc app/build/outputs)"
[ -f "$APK" ] || bad_arg "--apk trỏ file không có: $APK"
# Mapping của chính bản APK đó: StrictMode được retrace trước khi đếm frame SDK (count_strict).
[ -n "$MAP" ] || [ -n "$APP" ] || bad_arg "--mapping <mapping.txt> bắt buộc (mapping của đúng APK trên; tuỳ chọn khi có --app)"
[ -z "$MAP" ] || [ -f "$MAP" ] || bad_arg "--mapping trỏ file không có: $MAP"
case $APK in /*) ;; *) APK=$PWD/$APK;; esac
[ -z "$MAP" ] || case $MAP in /*) ;; *) MAP=$PWD/$MAP;; esac
[ -z "${SINK_DIR_ARG:-}" ] || case $SINK_DIR in /*) ;; *) SINK_DIR=$PWD/$SINK_DIR;; esac
