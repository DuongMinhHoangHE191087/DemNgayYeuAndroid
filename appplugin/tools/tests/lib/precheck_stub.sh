# shellcheck shell=bash
# Thay tools/lib/precheck_env.sh trong cây tạm của test (mặc định): ca không liên quan tới precheck không phụ thuộc máy/host chạy test.
# Ca kiểm precheck thật đặt PRECHECK_REAL=1 (prepare_gate chép bản thật) + adb giả trả fixture thật ở tools/tests/fixtures/precheck/.
precheck_env() { echo "precheck env net=1 lock=0 batt=100 host=awake"; return 0; }
