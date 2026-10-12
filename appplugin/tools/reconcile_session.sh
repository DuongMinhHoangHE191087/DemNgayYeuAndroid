#!/usr/bin/env bash
# H6a (1.7.0 Task 13, Q3): đối soát số impression của MỘT phiên từ 3 nguồn, theo imp_id.
# Dùng: tools/reconcile_session.sh <applog> <jsonl> <session_id>
#   (1) FA `origin=app,name=ad_impression` có imp_id (logcat đầy đủ: dòng FA-SVC do process GMS in),
#   (2) `rev af: imp=<id> started=1` (AppsFlyer), (3) JSONL `ad_revenue` của session_id sau dedup (dedup_key của backend).
# In 3 số + hiệu tập theo imp_id; `origin=am` ghi riêng, không chặn. PASS = 3 số bằng nhau (>= 1), hiệu tập rỗng.
# Exit 0 = PASS, 1 = FAIL (fail-closed: thiếu file / 0 dòng / không parse được = FAIL).
set -u
if [ $# -ne 3 ]; then
  echo "FAIL: dùng: tools/reconcile_session.sh <applog> <jsonl> <session_id>"
  echo "RESULT: FAIL"
  exit 1
fi
exec "${MEASURE_PY:-python3}" "$(dirname "$0")/measure_check.py" h6a --applog "$1" --jsonl "$2" --session "$3"
