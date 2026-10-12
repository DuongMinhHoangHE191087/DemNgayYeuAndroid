#!/usr/bin/env bash
# Deploy len Cloud Run. CHUA CHAY BAO GIO — xem muc "Chua xac minh" trong README.
# Chi chu du an, hoac controller SAU KHI co go bang van ban cua chu, duoc chay script nay:
# gcp_guard.sh tu choi `run deploy` neu thieu GCP_GUARD_OWNER_GO=<id dong ledger co go cua chu>,
# va tu choi moi project ngoai tools/gcp_allowed_projects.txt.
set -euo pipefail
: "${BRAIN_BQ_PROJECT:?can dat BRAIN_BQ_PROJECT}"
: "${BRAIN_SERVICE_ACCOUNT:?can dat BRAIN_SERVICE_ACCOUNT - service account it quyen (BigQuery dataEditor tren dataset, khong hon)}"
REGION="${REGION:-us-central1}"
SERVICE="${SERVICE:-brain-ingest}"
# Khoa API nam trong Secret Manager (dang "app_id:key,..."), khong bao gio o dong lenh hay env cua script.
API_KEYS_SECRET="${BRAIN_API_KEYS_SECRET:-brain-api-keys:latest}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$(dirname "$0")"
# --set-env-vars dạng phân cách gcloud ^|^ (guard 1.8.0 D19): BRAIN_FX_RATES mang dấu phẩy (USD:1,VND:…) nên không dùng ",".

"$ROOT/tools/gcp_guard.sh" gcloud --project="$BRAIN_BQ_PROJECT" run deploy "$SERVICE" \
  --region="$REGION" \
  --source=. \
  --service-account="$BRAIN_SERVICE_ACCOUNT" \
  --max-instances=3 \
  --set-env-vars="^|^BRAIN_SINK=bigquery|BRAIN_BQ_PROJECT=$BRAIN_BQ_PROJECT|BRAIN_BQ_DATASET=${BRAIN_BQ_DATASET:-monetization_brain}|BRAIN_BQ_TABLE=${BRAIN_BQ_TABLE:-events_raw}|BRAIN_FX_RATES=${BRAIN_FX_RATES:-}" \
  --set-secrets="BRAIN_API_KEYS=$API_KEYS_SECRET" \
  --no-allow-unauthenticated

echo
echo "Sau khi deploy, tro SDK vao URL nay bang cach doi"
echo "Constants.DataBucket.BASE_URL (hoac meta-data DATABUCKETS_BASE_URL trong app)."
echo "Luu y: BASE_URL phai co dau / o cuoi vi LogService dung @POST(\"push\")."
