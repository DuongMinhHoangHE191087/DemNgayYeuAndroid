#!/usr/bin/env bash
# Báo cáo ngày DOC/62 §2.1 (11 bảng: 7 view quảng cáo + ARPDAU gồm IAP + phễu paywall, 1.8.0 T12; + quyết định brain
# và eCPM theo mạng, 1.9.0 T9) -> DOC/reports/<ngày>.md,
# đọc view trên stack cục bộ (bigquery-emulator).
# Dùng: tools/report_daily.sh --date YYYY-MM-DD [--out <file.md>]   (mặc định DOC/reports/<ngày>.md)
# Ngày = ngày địa phương UTC+7 của client_ts_ms (plan 1.7.0 v2). Mặc định emulator local-test của
# backend/docker-compose.yml; BigQuery thật chỉ qua app.bq_setup.check_target (danh sách cho phép + go của chủ).
# Python backend: BACKEND_PY hoặc backend/.venv/bin/python. Bọc `timeout 90` ở tầng process khi có lệnh timeout.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
PY=${BACKEND_PY:-$ROOT/backend/.venv/bin/python}
[ -x "$PY" ] || { echo "report_daily: thiếu python backend ($PY); đặt BACKEND_PY" >&2; exit 2; }
day=""; out=""
while [ $# -gt 0 ]; do
  case "$1" in
    --date) day=${2:-}; shift 2 ;;
    --date=*) day=${1#*=}; shift ;;
    --out) out=${2:-}; shift 2 ;;
    *) echo "report_daily: tham số lạ '$1' (dùng: --date YYYY-MM-DD [--out f.md])" >&2; exit 2 ;;
  esac
done
case "$day" in [0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]) ;; *) echo "report_daily: cần --date YYYY-MM-DD" >&2; exit 2 ;; esac
export BRAIN_BQ_PROJECT=${BRAIN_BQ_PROJECT:-local-test}
# Định danh ghép vào SQL phải hợp lệ trước khi gọi Python (R-10 m2); Python kiểm lại lần nữa.
ds=${BRAIN_BQ_DATASET:-monetization_brain}
if ! [[ "$ds" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]] || [ "${#ds}" -gt 1024 ]; then
  echo "report_daily: BRAIN_BQ_DATASET '$ds' không phải định danh BigQuery" >&2; exit 2
fi
if ! [[ "$BRAIN_BQ_PROJECT" =~ ^[a-z][a-z0-9-]{4,28}[a-z0-9]$ ]]; then
  echo "report_daily: BRAIN_BQ_PROJECT '$BRAIN_BQ_PROJECT' không phải project id hợp lệ" >&2; exit 2
fi
export BRAIN_BQ_ENDPOINT=${BRAIN_BQ_ENDPOINT-http://127.0.0.1:19050}
out=${out:-$ROOT/DOC/reports/$day.md}
case "$out" in /*) ;; *) out="$PWD/$out" ;; esac
cd "$ROOT/backend"
if command -v timeout >/dev/null 2>&1; then
  exec timeout 90 "$PY" -m app.daily_report --date "$day" --out "$out"
fi
exec "$PY" -m app.daily_report --date "$day" --out "$out"
