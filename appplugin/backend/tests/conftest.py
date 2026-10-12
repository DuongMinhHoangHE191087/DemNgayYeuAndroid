"""Tiện ích dùng chung cho test T10: fixture event v2 -> hàng đúng như `/push` ghi xuống sink."""

from __future__ import annotations

import json
from pathlib import Path

import pytest

FIXTURE_V2 = Path(__file__).parent / "fixtures" / "ad_v2_fixture.jsonl"
# Ngày địa phương UTC+7 của gần như mọi event trong fixture (xem `ad_v2_fixture.jsonl`).
FIXTURE_DAY = "2026-09-30"


def load_fixture_rows() -> list[dict]:
    """Mỗi dòng fixture = {app_id, received_ts_ms, body}; body là JSON SDK gửi. Có 3 cặp trùng."""
    from app.models import EventRequest
    from app.normalize import ingest_row

    rows = []
    for line in FIXTURE_V2.read_text(encoding="utf-8").splitlines():
        if not line.strip():
            continue
        item = json.loads(line)
        req = EventRequest.model_validate(item["body"])
        rows.append(ingest_row(req, item["app_id"], item["received_ts_ms"]))
    return rows


SHOW_RATE_VIEWS_FIXTURE = Path(__file__).parent / "fixtures" / "show_rate_views" / "emu37-banner.jsonl"


def show_rate_views_rows() -> list[dict]:
    """2.2.0 BE3: sink thật của emulator (banner 34 attempt ready / 6 show / 6 khoá phiên-view); dòng `#` = nguồn + sha."""
    return [json.loads(ln) for ln in SHOW_RATE_VIEWS_FIXTURE.read_text(encoding="utf-8").splitlines()
            if ln.strip() and not ln.startswith("#")]


@pytest.fixture()
def fixture_rows() -> list[dict]:
    return load_fixture_rows()


# ── Integration (bigquery-emulator của docker-compose.yml; chạy `pytest -m integration` dưới khoá docker) ──
IT_ENDPOINT_ENV = "BRAIN_IT_BQ_ENDPOINT"
FIXTURE_FX = [{"date": FIXTURE_DAY, "currency": "EUR", "usd_per_unit": 1.1}]


def emulator_dataset(prefix: str):
    """(client, dataset) mới trên emulator: dataset riêng mỗi module, bảng tạo qua API, không xoá gì
    (plan R-4: không drop/tạo lại cùng tên; container được `docker compose down -v` sau phiên)."""
    import os
    import uuid

    from app import bq_setup

    endpoint = os.getenv(IT_ENDPOINT_ENV, "")
    if not endpoint:
        pytest.fail(f"integration cần {IT_ENDPOINT_ENV}=http://127.0.0.1:19050 (docker compose up)")
    client = bq_setup.make_client("local-test", endpoint, env={})
    dataset = f"{prefix}_{uuid.uuid4().hex[:8]}"
    bq_setup.setup(client, "local-test", dataset)
    return client, dataset


def load_and_merge(client, dataset: str, rows: list[dict], runs: int) -> list[tuple[int, int]]:
    """rows -> events_staging -> merge_events.sql ×runs; trả [(COUNT(*), số (app, dedup_key))] mỗi lượt."""
    from app import bq_setup, bq_tables

    ds = f"local-test.{dataset}"
    assert client.insert_rows_json(f"{ds}.events_staging", bq_tables.staging_rows(rows)) == []
    merge = bq_setup.render_sql((bq_tables.SQL_DIR / "merge_events.sql").read_text(), "local-test", dataset)
    counts = []
    for _ in range(runs):
        bq_setup.run_sql(client, merge)
        got = bq_setup.run_sql(client, (
            "SELECT COUNT(*) AS n, COUNT(DISTINCT CONCAT(IFNULL(app_id, ''), '|', dedup_key)) AS k "
            f"FROM `{ds}.events_raw`"))[0]
        counts.append((int(got["n"]), int(got["k"])))
    return counts
