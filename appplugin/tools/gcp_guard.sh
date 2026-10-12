#!/usr/bin/env bash
# Vỏ bọc duy nhất được phép gọi gcloud/bq trong repo: tools/gcp_guard.sh <gcloud|bq> <args...>.
# Máy dev có thể có project mặc định là PRODUCTION (speaker-c7f50); guard bảo đảm không script/agent nào
# chạm tới nó bằng cách thừa hưởng mặc định đó. Chỉ ĐÚNG MỘT cờ project tường minh, trong danh sách cho phép.
# Từ chối: exit 3 + `gcp_guard: REFUSED <lý do>`, binary KHÔNG được chạy.
set -u

# Danh sách cứng: tools/gcp_allowed_projects.txt và GCP_GUARD_ALLOWED chỉ thu hẹp được, không mở rộng.
HARD_ALLOWED="test-d-56f09"
PROD="speaker-c7f50"

refuse() { echo "gcp_guard: REFUSED $*" >&2; exit 3; }
lower() { printf '%s' "$1" | tr '[:upper:]' '[:lower:]'; }

# 1.8.0 T3 (D23, R-3 I1): mọi argv không được chứa ký tự điều khiển (\n \r \t, < 0x20, DEL): kiểm đầu tiên, trước mọi phân tích.
for a in "$@"; do case "$a" in *[[:cntrl:]]*) refuse "argv chứa ký tự điều khiển (xuống dòng/tab/…)" ;; esac; done
[ $# -ge 1 ] || refuse "thiếu binary (dùng: gcp_guard.sh <gcloud|bin> args...)"
bin=$1; shift
case "$bin" in gcloud|bq) ;; *) refuse "binary '$bin' không phải gcloud|bq" ;; esac

# ── Danh sách cho phép hiệu lực = file ∩ danh sách cứng ∩ GCP_GUARD_ALLOWED (nếu đặt) ──────────────────
ALLOW_FILE=$(cd "$(dirname "$0")" && pwd)/gcp_allowed_projects.txt
[ -f "$ALLOW_FILE" ] || refuse "thiếu $ALLOW_FILE"
allowed=" "
while IFS= read -r line || [ -n "$line" ]; do
  line=$(printf '%s' "$line" | tr -d '[:space:]')
  case "$line" in ''|'#'*) continue ;; esac
  for h in $HARD_ALLOWED; do
    [ "$line" = "$h" ] || continue
    if [ -n "${GCP_GUARD_ALLOWED:-}" ]; then
      case " $(printf '%s' "$GCP_GUARD_ALLOWED" | tr ',' ' ') " in *" $h "*) ;; *) continue ;; esac
    fi
    allowed="$allowed$h "
  done
done < "$ALLOW_FILE"
[ "$allowed" != " " ] || refuse "danh sách project cho phép rỗng"

# ── Production không được xuất hiện ở đâu cả: argv hay env ────────────────────────────────────────────
all_args=$(lower "$*")
case "$all_args" in *"$PROD"*) refuse "chuỗi $PROD có trong argv" ;; esac
for v in CLOUDSDK_CORE_PROJECT GOOGLE_CLOUD_PROJECT GCLOUD_PROJECT CLOUDSDK_PROJECT; do
  eval "val=\${$v:-}"
  case "$(lower "$val")" in *"$PROD"*) refuse "env $v chứa $PROD" ;; esac
done
case "$(lower "${CLOUDSDK_ACTIVE_CONFIG_NAME:-}")" in *speaker*) refuse "env CLOUDSDK_ACTIVE_CONFIG_NAME chứa 'speaker' (cấu hình production)" ;; esac

# ══ Khớp dạng cố định (không phân tích cú pháp): argv = <tool> <cờ project dạng "="> <tiền tố lệnh> <đuôi> ═══════════
# Đuôi chỉ gồm cờ dạng --tên=giá-trị (hoặc cờ bool) trong danh sách của đúng lệnh đó, và số tham số vị trí trong khoảng
# cho phép. Cờ lạ, giá trị tách rời, "--", cờ nào chen trước tiền tố: đều REFUSED (guard không biết cờ nào ăn giá trị).
ORIG=("$@")
for a in "$@"; do [ "$a" = "--" ] && refuse "dấu '--' bị cấm"; done
case "$bin" in gcloud) PROJ_FLAG=--project ;; bq) PROJ_FLAG=--project_id ;; esac
[ $# -ge 1 ] || refuse "thiếu cờ project"
case "$1" in "$PROJ_FLAG"=*) project=${1#*=} ;; *) refuse "token đầu sau $bin phải là $PROJ_FLAG=<id> (dạng '=')" ;; esac
case "$allowed" in *" $project "*) ;; *) refuse "project '$project' không nằm trong danh sách cho phép" ;; esac
shift

if [ "$bin" = gcloud ]; then
  PREFIXES="run deploy|run services describe|run services list|run services update-traffic|run services logs read|run revisions list|secrets versions add|secrets versions disable|logging read"
else
  # Không có `query` (SQL tự do không kiểm chứng được) và, từ 1.7.0 T10, không có `mk`: bảng/dataset dựng bằng
  # `backend/app/bq_setup.py` (Python client, cùng danh sách cho phép + GCP_GUARD_OWNER_GO), không qua bq.
  PREFIXES="ls|show"
fi
# spec <tiền tố>: S_MIN/S_MAX = số tham số vị trí; S_VAL = cờ nhận giá trị (--tên=v); S_BOOL = cờ bool.
# Rút từ deploy_cloudrun.sh và RUNBOOK; thêm gì phải sửa ở đây có chủ ý.
spec() {
  S_MIN=0; S_MAX=0; S_VAL=""; S_BOOL=""
  case "$bin $1" in
    "gcloud run deploy") S_MIN=1; S_MAX=1; S_VAL="--region --source --service-account --max-instances --set-env-vars --set-secrets"; S_BOOL="--no-allow-unauthenticated" ;;
    "gcloud run services describe") S_MIN=1; S_MAX=1; S_VAL="--region --format" ;;
    "gcloud run services list") S_VAL="--region --format" ;;
    "gcloud run services update-traffic") S_MIN=1; S_MAX=1; S_VAL="--region --to-revisions" ;;
    "gcloud run services logs read") S_MIN=1; S_MAX=1; S_VAL="--region --limit" ;;
    "gcloud run revisions list") S_VAL="--service --region" ;;
    "gcloud secrets versions add") S_MIN=1; S_MAX=1; S_VAL="--data-file" ;;
    "gcloud secrets versions disable") S_MIN=1; S_MAX=1; S_VAL="--secret" ;;
    "gcloud logging read") S_MIN=1; S_MAX=1; S_VAL="--limit" ;;   # cố ý KHÔNG có --organization/--folder/--billing-account/--bucket/--view
    "bq ls") S_MAX=1 ;;
    "bq show") S_MIN=1; S_MAX=1 ;;
    *) return 1 ;;
  esac
}
in_list() { case " $2 " in *" $1 "*) return 0 ;; esac; return 1; }
ENV_KEYS="BRAIN_SINK BRAIN_BQ_PROJECT BRAIN_BQ_DATASET BRAIN_BQ_TABLE BRAIN_FX_RATES"  # khoá deploy_cloudrun.sh được đặt

prefix=""
IFS='|' read -r -a _cands <<< "$PREFIXES"
for cand in "${_cands[@]}"; do
  set -- "$@"; ok=1; i=1
  for w in $cand; do
    eval "tok=\${$i:-}"
    [ "$tok" = "$w" ] || { ok=0; break; }
    i=$((i + 1))
  done
  if [ "$ok" = 1 ]; then prefix=$cand; break; fi
done
[ -n "$prefix" ] || refuse "lệnh không nằm trong danh sách cho phép của $bin (tiền tố phải đứng ngay sau cờ project)"
spec "$prefix" || refuse "thiếu đặc tả cho '$prefix'"
nw=$(printf '%s' "$prefix" | wc -w | tr -d ' ')
shift "$nw"

npos=0
for a in "$@"; do
  case "$a" in
    -*)
      name=${a%%=*}
      if [ "$name" != "$a" ]; then
        val=${a#*=}
        in_list "$name" "$S_VAL" || refuse "cờ '$name' không nằm trong danh sách của '$prefix' (chỉ dạng --tên=giá-trị)"
        [ -n "$val" ] || refuse "cờ $name rỗng"
        case "$name" in
          --service-account) case "$(lower "$val")" in *"@$(lower "$project").iam.gserviceaccount.com") ;; *) refuse "--service-account phải là SA của $project" ;; esac ;;
          --source) [ "$val" = "." ] || refuse "--source chỉ nhận '.'" ;;
          --set-env-vars) # 1.8.0 T14 (R-11): chỉ KEY=VALUE với KEY thuộc danh sách khoá; BRAIN_BQ_PROJECT phải là project cho phép
            # 1.8.0 T3 (D19): dạng phân cách của gcloud `^|^K=V|K=V` (chỉ '|', chỉ cờ này) để giá trị mang dấu phẩy
            # (BRAIN_FX_RATES=USD:1,VND:…). Sau tiền tố không được còn '^' nào; mọi dạng '^' khác bị từ chối.
            sep=,; body=$val
            case "$val" in
              '^|^'*) sep='|'; body=${val#'^|^'}
                case "$body" in *'^'*) refuse "--set-env-vars: giá trị dạng ^|^ không được chứa '^'" ;; esac ;;
              *'^'*) refuse "--set-env-vars: '^' chỉ được dùng làm tiền tố ^|^" ;;
            esac
            [ -n "$body" ] || refuse "--set-env-vars: không có KEY=VALUE nào"
            case "$body" in "$sep"*|*"$sep"|*"$sep$sep"*) refuse "--set-env-vars: có đoạn rỗng (đầu/cuối/giữa dấu phân cách)" ;; esac
            IFS="$sep" read -r -a _kv <<< "$body"
            for kv in "${_kv[@]}"; do
              case "$kv" in *=*) ;; *) refuse "--set-env-vars: '$kv' không phải KEY=VALUE" ;; esac
              case "${kv%%=*}" in ''|[!A-Z]*|*[!A-Z0-9_]*) refuse "--set-env-vars: khoá '${kv%%=*}' không khớp ^[A-Z][A-Z0-9_]*\$" ;; esac
              in_list "${kv%%=*}" "$ENV_KEYS" || refuse "--set-env-vars: khoá '${kv%%=*}' không nằm trong danh sách khoá"
              case "$kv" in BRAIN_BQ_PROJECT=*) [ "$(lower "${kv#*=}")" = "$(lower "$project")" ] || refuse "--set-env-vars: BRAIN_BQ_PROJECT phải là $project" ;; esac
            done ;;
        esac
      else
        in_list "$name" "$S_BOOL" || refuse "cờ '$a' không nằm trong danh sách của '$prefix' (giá trị phải dạng --tên=giá-trị)"
      fi ;;
    *)
      npos=$((npos + 1))
      if [ "$bin" = bq ]; then
        # Mọi tham số vị trí của bq: hoặc <project>:dataset[.table] đúng project cho phép, hoặc tên trần (không ':').
        # Mọi thứ có ':' mà không đúng dạng (kể cả "other-proj:") bị từ chối.
        case "$a" in
          *:*) printf '%s\n' "$a" | grep -qE "^$project:[A-Za-z0-9_]+(\.[A-Za-z0-9_]+)?\$" || refuse "tham số bq '$a' có ':' nhưng không đúng dạng $project:dataset[.table]" ;;
          *..*) refuse "tham số bq '$a' chứa '..'" ;;
          *)
            # Tên trần = MỘT định danh (bq đọc "X.Y" không có ':' là project X, dataset Y).
            printf '%s\n' "$a" | grep -qE '^[A-Za-z0-9_]+$' \
              || refuse "tham số bq '$a' phải là một định danh trần hoặc $project:dataset[.table]" ;;
        esac
      fi
      case "$prefix" in "run "*) case "$a" in */*) refuse "tham số của gcloud run không được chứa '/' ($a)" ;; esac ;; esac ;;
  esac
done
[ "$npos" -ge "$S_MIN" ] || refuse "'$prefix' cần ít nhất $S_MIN tham số vị trí (SQL/đối tượng), thấy $npos"
[ "$npos" -le "$S_MAX" ] || refuse "'$prefix' nhận tối đa $S_MAX tham số vị trí, thấy $npos"

# ── Tham chiếu tài nguyên trỏ sang project khác thắng cờ project: mọi tham chiếu phải là project cho phép ─────
lproj=$(lower "$project")
ref_must_be_project() { # $1 = nhãn, $2 = giá trị bắt được
  [ "$2" = "$lproj" ] || refuse "tham chiếu $1 tới project '$2' (chỉ '$project')"
}
for a in "${ORIG[@]}"; do
  la=$(lower "$a" | tr -d '`')
  for m in $(printf '%s\n' "$la" | grep -oE 'projects/[^/[:space:]"]+'); do ref_must_be_project "projects/" "${m#projects/}"; done
  for m in $(printf '%s\n' "$la" | grep -oE '(gcr\.io|pkg\.dev)/[a-z0-9.-]+'); do ref_must_be_project "image" "${m#*/}"; done
  for m in $(printf '%s\n' "$la" | grep -oE '@[a-z0-9-]+\.iam\.gserviceaccount\.com'); do m=${m#@}; ref_must_be_project "service account" "${m%%.iam.*}"; done
  case "$la" in *developer.gserviceaccount.com*) refuse "service account mặc định của project khác" ;; esac
  if [ "$bin" = bq ]; then
    for m in $(printf '%s\n' "$la" | grep -oE '[a-z][a-z0-9.-]*:[a-z_]'); do ref_must_be_project "bq proj:dataset" "${m%%:*}"; done
    for m in $(printf '%s\n' "$la" | grep -oE '[a-z][a-z0-9-]*\.[a-z0-9_]+\.[a-z0-9_]+'); do ref_must_be_project "bq proj.dataset.table" "${m%%.*}"; done
  fi
done

# ── Lệnh cần GCP_GUARD_OWNER_GO (lời đồng ý bằng văn bản của chủ, Q4) ────────────────────────────────────
danger=""
case "$prefix" in "run deploy"|"run services update-traffic"|"secrets versions add"|"secrets versions disable") danger="deploy/traffic/secret" ;; esac
case "$all_args" in *"drop "*|*"truncate "*|*"delete "*) danger="xoá (SQL)" ;; esac
if [ -n "$danger" ] && [ -z "${GCP_GUARD_OWNER_GO:-}" ]; then
  refuse "lệnh $danger cần GCP_GUARD_OWNER_GO=<id dòng ledger có lời đồng ý của chủ>"
fi

echo "gcp_guard: ALLOWED $bin $prefix project=$project" >&2
# 1.8.0 T3 (G1): BIGQUERYRC=/dev/null — bq không đọc ~/.bigqueryrc (project/dataset mặc định có thể là production).
exec env -u CLOUDSDK_CORE_PROJECT -u GOOGLE_CLOUD_PROJECT -u GCLOUD_PROJECT -u CLOUDSDK_PROJECT \
  CLOUDSDK_CORE_DISABLE_PROMPTS=1 BIGQUERYRC=/dev/null "$bin" "${ORIG[@]}"
