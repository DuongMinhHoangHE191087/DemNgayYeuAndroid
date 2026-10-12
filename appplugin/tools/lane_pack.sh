#!/usr/bin/env bash
# Gói đọc cho một lane: in ra stdout, cắt theo tiêu đề markdown của plan (không cắt tay):
#   Global Constraints (gồm Khung bước chung KB) + §Hợp đồng A–E mà task nhắc + mục "## Task <id>"
#   + hàng lane trong "Lane và ma trận quyền" + mục "Quyết định CEO (...)" cuối plan.
# Dùng: bash tools/lane_pack.sh <task-id>     (id số, chữ hoặc có "-": 5, R, T0a, PERF-B, IG-G, E5).
#   PLAN=<đường dẫn> để đổi plan; mặc định = plan *-sdk-*-plan.md mới nhất theo tên.
# id lạ: một dòng lỗi trên stderr, exit 1. Chạy được trên bash 3.2 (awk BSD).
set -u
ROOT=$(cd "$(dirname "$0")/.." && pwd)
PLAN=${PLAN:-$(ls "$ROOT"/docs/superpowers/plans/*-sdk-*-plan.md 2>/dev/null | sort | tail -1)}
id=${1:-}
[ -n "$id" ] || { echo "lane_pack: thiếu task-id (dùng: lane_pack.sh <id: 5|R|T0a|PERF-B…>)" >&2; exit 1; }
[ -f "$PLAN" ] || { echo "lane_pack: không thấy plan $PLAN" >&2; exit 1; }
case $id in *[!0-9A-Za-z-]*) echo "lane_pack: task-id không hợp lệ: $id" >&2; exit 1;; esac

# Mục cấp ## có tiêu đề khớp regex $1 (đến tiêu đề ## kế tiếp, kể cả các ### bên trong).
section() { awk -v pat="$1" '/^## /{p=($0 ~ pat)} p' "$PLAN"; }

task=$(section "^## (Task )?$id([: ]|\$)")
[ -n "$task" ] || { echo "lane_pack: không có '## Task $id' trong plan" >&2; exit 1; }

case $id in [0-9]*) rid=T$id;; *) rid=$id;; esac
echo "<!-- lane_pack task=$id plan=$(basename "$PLAN") -->"
section '^## Global Constraints'
echo
# Chữ cái hợp đồng mà task nhắc: "Hợp đồng A", "Hợp đồng D, E", "hợp đồng A–E".
letters=$(printf '%s\n' "$task" | awk '
  { s=$0
    while (match(s, /[Hh]ợp đồng [A-E]([,–-]+ ?[A-E])*/)) {
      seg=substr(s, RSTART, RLENGTH); s=substr(s, RSTART+RLENGTH)
      gsub(/[Hh]ợp đồng /, "", seg)
      if (seg ~ /–|-/) { a=substr(seg,1,1); b=substr(seg,length(seg),1)
        for (c=a; c<=b; c=sprintf("%c", index("ABCDE", c)+65)) out=out c; }
      else { gsub(/[^A-E]/, "", seg); out=out seg } } }
  END { print out }' | fold -w1 | sort -u | tr -d '\n')
if [ -n "$letters" ]; then
  echo "## Hợp đồng (trích: $letters)"
  echo
  awk -v L="$letters" '
    /^## /{ inC=($0 ~ /^## Hợp đồng/); p=0; next }
    /^### /{ p=0; if (inC) { k=substr($0,5,1); p=(index(L,k)>0 && substr($0,6,1)==".") } }
    p' "$PLAN"
  echo
fi
printf '%s\n' "$task"
echo
echo "## Hàng lane (Lane và ma trận quyền)"
echo
section '^## Lane và ma trận quyền' | awk -v rid="$rid" '
  /^\| Lane \|/ || /^\|---/ { print; next }
  /^\| / { split($0, c, "|"); if (c[2] ~ ("[( ,]" rid "[,) →]")) print }'
echo
section '^## Quyết định CEO [(]'
