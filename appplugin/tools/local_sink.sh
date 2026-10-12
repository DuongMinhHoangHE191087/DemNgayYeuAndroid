#!/usr/bin/env bash
# 1.7.0 Task 8: sink telemetry cục bộ cho demo `utility` (127.0.0.1:8089), không Docker.
# Dùng: tools/local_sink.sh start|stop|status [--port <p>] [--dir <d>]   (1.8.0 Task 1: sink theo lane; mặc định 8089 và
#   DOC/testing/local-sink như 1.7.0. Hai lane KHÔNG bao giờ chung một --dir: hai sink ghi chung một events.jsonl là trộn số
#   từng máy. <p> phải trong 1024..65535.)
#   start  uvicorn app.main:app --host 127.0.0.1 --port <p> (BRAIN_SINK=jsonl, BRAIN_JSONL_PATH=<d>/events.jsonl,
#          BRAIN_API_KEY=demo-utility-key — khoá đơn, khớp DATABUCKETS_API_KEY của app/src/utility/AndroidManifest.xml)
#          chạy `<py> -m uvicorn` (không cần `uvicorn` trên PATH), PID vào <d>/sink-<p>.pid, log <d>/sink-<p>.log.
#   stop   (không --dir: dùng thư mục của lần start --dir cùng cổng, T0g M1) kill đúng PID đã ghi (chỉ khi tiến trình đó là uvicorn app.main).
#          1.8.0 Task 16: BRAIN_RTDN_TOKEN (>= 16 ký tự, T13 /rtdn) xuất cho sink; mặc định token demo cục bộ demo-rtdn-local-token
#          (env BRAIN_RTDN_TOKEN của người gọi thắng); tools/lib/iap_gate.sh gửi fixture RTDN với đúng token này.
#   status /health = 200 -> "sink up" exit 0, ngược lại exit 1.
# Python: $BACKEND_PY hoặc backend/.venv/bin/python; thiếu -> FAIL. <d> mặc định $SINK_DIR hoặc DOC/testing/local-sink.
set -u
ROOT=$(cd "$(dirname "$0")/.." && pwd)
PORT=8089
PORT_MIN=1024; PORT_MAX=65535
HOST=127.0.0.1
API_KEY=demo-utility-key
RTDN_TOKEN_DEFAULT=demo-rtdn-local-token   # >= 16 ký tự (backend từ chối ngắn hơn khi khởi động)
die() { echo "FAIL: $1"; exit 1; }

cmd=${1:-}; [ $# -eq 0 ] || shift
DEF_DIR=${SINK_DIR:-$ROOT/DOC/testing/local-sink}; DIR=$DEF_DIR; DIR_GIVEN=0
while [ $# -gt 0 ]; do
  case $1 in
    --dir) [ $# -ge 2 ] || die "--dir cần một đường dẫn"; DIR=$2; DIR_GIVEN=1; shift 2;;
    --port) [ $# -ge 2 ] || die "--port cần một số cổng ($PORT_MIN..$PORT_MAX)"; PORT=$2; shift 2;;
    *) die "đối số lạ '$1'";;
  esac
done
case $DIR in /*) ;; *) DIR=$PWD/$DIR;; esac
case $PORT in ''|*[!0-9]*) die "--port phải là số trong $PORT_MIN..$PORT_MAX (nhận '$PORT')";; esac
{ [ "$PORT" -ge "$PORT_MIN" ] && [ "$PORT" -le "$PORT_MAX" ]; } || die "--port phải trong $PORT_MIN..$PORT_MAX (nhận '$PORT')"
case $DEF_DIR in /*) ;; *) DEF_DIR=$PWD/$DEF_DIR;; esac
DIRPTR=$DEF_DIR/sink-$PORT.dir   # 2.0.0 T0g (M1): start --dir <d> ghi con trỏ ở thư mục mặc định; stop --port <p> không --dir đọc lại
[ "$DIR_GIVEN" -eq 1 ] || [ "$cmd" != stop ] || { [ ! -s "$DIRPTR" ] || DIR=$(cat "$DIRPTR"); }
PIDF=$DIR/sink-$PORT.pid; LOGF=$DIR/sink-$PORT.log

health_code() { curl -s -o /dev/null -m 3 -w '%{http_code}' "http://$HOST:$PORT/health" 2>/dev/null; }

case $cmd in
  status)
    if [ "$(health_code)" = 200 ]; then echo "sink up (http://$HOST:$PORT/health 200)"; exit 0; fi
    echo "sink down (http://$HOST:$PORT/health)"; exit 1;;
  start)
    PY=${BACKEND_PY:-$ROOT/backend/.venv/bin/python}
    [ -x "$PY" ] || die "không có Python của backend ($PY); đặt BACKEND_PY (venv có fastapi + uvicorn)"
    "$PY" -c 'import uvicorn, fastapi' 2>/dev/null || die "$PY thiếu uvicorn/fastapi"
    [ "$(health_code)" != 200 ] || die "cổng $PORT đã có sink chạy (dừng trước bằng 'local_sink.sh stop')"
    [ "$DIR" != "$DEF_DIR" ] || rm -f "$DIRPTR"   # con trỏ cũ không được chỉ stop sang thư mục khác (M5)
    mkdir -p "$DIR"
    cd "$ROOT/backend" || die "không vào được $ROOT/backend"
    BRAIN_SINK=jsonl BRAIN_JSONL_PATH="$DIR/events.jsonl" BRAIN_API_KEY="$API_KEY" BRAIN_RTDN_TOKEN="${BRAIN_RTDN_TOKEN:-$RTDN_TOKEN_DEFAULT}" \
      nohup "$PY" -m uvicorn app.main:app --host "$HOST" --port "$PORT" > "$LOGF" 2>&1 < /dev/null &
    echo $! > "$PIDF"
    [ "$DIR" = "$DEF_DIR" ] || { mkdir -p "$DEF_DIR" && echo "$DIR" > "$DIRPTR"; }
    for _ in $(seq 1 30); do
      [ "$(health_code)" = 200 ] && { echo "sink started pid=$(cat "$PIDF") jsonl=$DIR/events.jsonl"; exit 0; }
      sleep 0.5
    done
    tail -5 "$LOGF" 2>/dev/null
    die "sink không lên /health sau 15 s (xem $LOGF)";;
  stop)
    # Sink 1.7.0 ghi <d>/sink.pid (không theo cổng): chỉ cổng 8089 mặc định còn nhận tên cũ.
    [ -f "$PIDF" ] || { [ "$PORT" = 8089 ] && [ -f "$DIR/sink.pid" ] && PIDF=$DIR/sink.pid; }
    [ -f "$PIDF" ] || { echo "sink: không có $PIDF (không có gì để dừng)"; exit 0; }
    pid=$(cat "$PIDF")
    if ps -o command= -p "$pid" 2>/dev/null | grep -q 'uvicorn app.main'; then
      kill "$pid"
      for _ in $(seq 1 20); do kill -0 "$pid" 2>/dev/null || break; sleep 0.25; done
      kill -0 "$pid" 2>/dev/null && die "pid $pid không thoát sau SIGTERM"
      echo "sink stopped pid=$pid"
    else
      echo "sink: pid $pid không phải uvicorn app.main (đã dừng hoặc pid cũ), không kill"
    fi
    rm -f "$PIDF" "$DIRPTR";;
  *) die "dùng: local_sink.sh start|stop|status [--port <p>] [--dir <d>]";;
esac
