#!/usr/bin/env python3
"""Nạp JSONL vào stack cục bộ: `events_staging` của bigquery-emulator -> `sql/merge_events.sql` (×N).

Dùng cho cửa sổ E-BE (plan 1.7.0): cổng thiết bị ghi JSONL bằng sink native (không Docker), sau đó
lane Docker nạp vào đây, chạy MERGE, rồi view/báo cáo. Chạy bằng python của backend (có
google-cloud-bigquery), từ gốc repo:

    backend/.venv/bin/python tools/stack_load.py --endpoint http://127.0.0.1:19050 --merge-runs 2 <file.jsonl>...

Nhận hai dạng dòng: hàng sink (`JsonlSink`, đã normalize) và dòng fixture `{app_id, received_ts_ms, body}`
(body = JSON SDK gửi, dựng lại bằng đúng `normalize.ingest_row` của `/push`).
Sink của cổng G12 (`g12-*/events.jsonl` cạnh file đã cho) được nạp kèm để `v_paywall_funnel` có hàng (2.1.0 BE1). CHỈ emulator `local-test`:
không có đường nào tới BigQuery thật (dựng bảng thật là `app.bq_setup` với lời đồng ý của chủ).
In sau mỗi lượt MERGE: `raw=<COUNT(*)> distinct=<số (app_id, dedup_key)>` — G11 (iii) cần hai số bằng nhau.
Mọi lệnh gọi emulator nên bọc `timeout 90` ở tầng process (MERGE treo không tự nổ, plan R-4).
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any, Iterable

BACKEND = Path(__file__).resolve().parent.parent / "backend"
if str(BACKEND) not in sys.path:
    sys.path.insert(0, str(BACKEND))

from app.bq_setup import SetupRefused, make_client, render_sql, run_sql  # noqa: E402
from app.bq_tables import SQL_DIR, InvalidIdentifier, ds_ref, staging_rows  # noqa: E402
from app.config import LOCAL_TEST_PROJECT  # noqa: E402

_BATCH = 500


def read_jsonl(paths: Iterable[Path | str]) -> list[dict[str, Any]]:
    from app.models import EventRequest  # noqa: PLC0415
    from app.normalize import ingest_row  # noqa: PLC0415

    rows: list[dict[str, Any]] = []
    for path in paths:
        for n, line in enumerate(Path(path).read_text(encoding="utf-8").splitlines(), 1):
            if not line.strip():
                continue
            item = json.loads(line)
            if "body" in item and "event_name" not in item:
                req = EventRequest.model_validate(item["body"])
                rows.append(ingest_row(req, item.get("app_id"), int(item["received_ts_ms"])))
            elif "event_name" in item:
                rows.append(item)
            else:
                raise ValueError(f"{path}:{n}: không phải hàng sink cũng không phải dòng fixture")
    return rows


def with_g12(paths: Iterable[Path | str]) -> list[Path]:
    """Thêm `g12-*/events.jsonl` cạnh mỗi file đã cho (cổng G12 ghi paywall_*/iap_* vào thư mục con của sink)."""
    out: list[Path] = []
    seen: set[Path] = set()   # R-BE1 m3 + 2.2.0 BE3: đường tương đối và tuyệt đối cùng file chỉ nạp một lần
    for p in map(Path, paths):
        if p.resolve() not in seen and not seen.add(p.resolve()):
            out.append(p)
    for p in list(out):
        out += [g for g in sorted(p.parent.glob("g12-*/events.jsonl")) if g.resolve() not in seen and not seen.add(g.resolve())]
    return out


def rows_for_staging(rows: Iterable[dict[str, Any]]) -> list[dict[str, Any]]:
    return staging_rows(rows)


def _counts(client: Any, ds: str) -> tuple[int, int]:
    got = run_sql(client, f"SELECT COUNT(*) AS n, COUNT(DISTINCT CONCAT(IFNULL(app_id, ''), '|', dedup_key)) "
                          f"AS k FROM `{ds}.events_raw`")[0]
    return int(got["n"]), int(got["k"])


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description="JSONL -> events_staging -> MERGE (chỉ emulator)")
    ap.add_argument("--project", default=LOCAL_TEST_PROJECT)
    ap.add_argument("--dataset", default="monetization_brain")
    ap.add_argument("--endpoint", default="http://127.0.0.1:19050")
    ap.add_argument("--merge-runs", type=int, default=1)
    ap.add_argument("jsonl", nargs="+")
    args = ap.parse_args(argv)
    if args.project != LOCAL_TEST_PROJECT or not args.endpoint:
        ap.error(f"stack_load chỉ nạp vào emulator: --project {LOCAL_TEST_PROJECT} + --endpoint")
    try:
        ds = ds_ref(args.project, args.dataset)  # định danh hợp lệ TRƯỚC khi dựng client (R-10 m2)
        client = make_client(args.project, args.endpoint)
    except (SetupRefused, InvalidIdentifier) as exc:
        ap.error(str(exc))
    rows = rows_for_staging(read_jsonl(with_g12(args.jsonl)))
    for i in range(0, len(rows), _BATCH):
        errors = client.insert_rows_json(f"{ds}.events_staging", rows[i:i + _BATCH])
        if errors:
            print(f"stack_load: FAIL insert {errors[:3]}", file=sys.stderr)
            return 1
    print(f"stack_load: staging +{len(rows)}")
    merge = render_sql((SQL_DIR / "merge_events.sql").read_text(encoding="utf-8"), args.project, args.dataset)
    for run in range(1, args.merge_runs + 1):
        run_sql(client, merge)
        raw, distinct = _counts(client, ds)
        print(f"stack_load: merge#{run} raw={raw} distinct={distinct}")
        if raw != distinct:
            print("stack_load: FAIL raw != distinct (còn hàng trùng sau MERGE)", file=sys.stderr)
            return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
