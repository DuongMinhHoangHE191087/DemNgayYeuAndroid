"""Đặc tả các bảng BigQuery của backend — MỘT chỗ, tạo bằng Python client (`app/bq_setup.py`).

Vì sao không SQL DDL: bigquery-emulator nhận `CREATE TABLE … OPTIONS(partition_expiration_days)` nhưng
`get_table().time_partitioning` trả `None` (plan 1.7.0 P5), nên hạn giữ chỉ kiểm được bằng metadata khi
bảng tạo qua API. Thêm nữa: bảng từng tạo bằng DDL rồi xoá/tạo lại cùng tên làm MERGE treo trên
emulator (plan R-4) — ở đây bảng chỉ được tạo-nếu-chưa-có, không bao giờ xoá.

- `events_staging`: sink `/push` ghi vào (streaming, chấp nhận trùng), phân vùng `received_date`, hết
  hạn 2 ngày — MERGE (`sql/merge_events.sql`) phải chạy ít nhất mỗi ngày.
- Vì sao phân vùng theo ngày NHẬN (1.7.0 F2-be, final review I1): BigQuery xoá ngay hàng chèn vào phân vùng đã
  quá hạn giữ. Theo ngày client, một bản replay tới muộn ≥ 3 ngày (hay máy chậm giờ, `ts = 0`) được trả 200
  rồi mất trước MERGE. Ngày báo cáo vẫn là ngày UTC+7 của `client_ts_ms` (`v_events_v2`); `client_ts_date`
  là cột thường, lọc theo nó không còn cắt phân vùng (chi phí quét trên BigQuery thật: H5).
- `events_raw`: sau MERGE (khử trùng theo `(IFNULL(app_id,''), dedup_key)`), giữ 400 ngày, cluster
  `event_name, user_id`. Cùng schema với staging (`schema/events_raw.json`) để `INSERT ROW` được.
- `country_tier`: ISO -> T1/T2/VN (seed `sql/seed/country_tier.csv`); nước không có trong bảng
  (kể cả `DEFAULT`, rỗng) = OTHER ở view.
- `fx_daily`: tỉ giá theo ngày; không có tỉ giá = doanh thu `unconverted`, không bịa.
- `admob_report`: đặc tả của T12 (`admob_import.ADMOB_REPORT_TABLE`), nạp ở đây.
- `subscription_events` (1.8.0 T13): hàng RTDN của Play (`/rtdn`, `schema/subscription_events.json`), chỉ nối thêm,
  khử trùng `rtdn:<messageId>`; phân vùng `received_date` (400 ngày), cluster `user_id, purchase_token` (đọc theo
  người của lô `/push` cho `signals`, tra theo token).
"""

from __future__ import annotations

import json
import re
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable, Mapping

from .admob_import import ADMOB_REPORT_TABLE
from .normalize import date_utc

BACKEND_DIR = Path(__file__).resolve().parent.parent
EVENTS_SCHEMA = BACKEND_DIR / "schema" / "events_raw.json"
SUBSCRIPTION_EVENTS_SCHEMA = BACKEND_DIR / "schema" / "subscription_events.json"
SUBSCRIPTION_EVENTS_TABLE = "subscription_events"
SQL_DIR = BACKEND_DIR / "sql"
COUNTRY_TIER_SEED = SQL_DIR / "seed" / "country_tier.csv"

_DAY_MS = 86_400_000
STAGING_EXPIRATION_MS = 2 * _DAY_MS      # 172 800 000
RAW_EXPIRATION_MS = 400 * _DAY_MS        # 34 560 000 000
# Cột phân vùng của staging/raw: ngày UTC của giờ server NHẬN (không phải `client_ts_date`, xem docstring).
PARTITION_FIELD = "received_date"


# Định danh ghép vào SQL (R-10 m2): dataset/bảng/view theo luật BigQuery, project theo luật project id của
# GCP. `fullmatch` (không `$`): "ds\n" không được lọt. Sai -> từ chối, không bao giờ "làm sạch" rồi dùng.
_IDENT_RE = re.compile(r"[A-Za-z_][A-Za-z0-9_]{0,1023}")
_PROJECT_RE = re.compile(r"[a-z][a-z0-9-]{4,28}[a-z0-9]")


class InvalidIdentifier(ValueError):
    """Định danh không hợp lệ: không được ghép vào SQL."""


def require_identifier(name: str, what: str = "dataset") -> str:
    if not isinstance(name, str) or not _IDENT_RE.fullmatch(name):
        raise InvalidIdentifier(f"{what} {name!r} không phải định danh BigQuery "
                                "(^[A-Za-z_][A-Za-z0-9_]{0,1023}$)")
    return name


def require_project(project: str) -> str:
    if not isinstance(project, str) or not _PROJECT_RE.fullmatch(project):
        raise InvalidIdentifier(f"project {project!r} không phải project id GCP hợp lệ")
    return project


def ds_ref(project: str, dataset: str) -> str:
    """`<project>.<dataset>` đã kiểm — chỗ DUY NHẤT dựng tiền tố dùng trong SQL và tham chiếu bảng."""
    return f"{require_project(project)}.{require_identifier(dataset)}"


@dataclass(frozen=True)
class TableSpec:
    name: str
    columns: tuple[dict[str, Any], ...]
    partition_field: str | None = None
    expiration_ms: int | None = None
    clustering_fields: tuple[str, ...] = ()


def events_columns() -> list[dict[str, Any]]:
    """113 cột của `schema/events_raw.json` (T9 + `received_date` của F2-be + 7 cột IAP/paywall v2 của 1.8.0 T12,
    thêm ở CUỐI), dùng chung cho staging và raw."""
    return json.loads(EVENTS_SCHEMA.read_text(encoding="utf-8"))


def subscription_event_columns() -> list[dict[str, Any]]:
    """17 cột của `schema/subscription_events.json` (1.8.0 T13)."""
    return json.loads(SUBSCRIPTION_EVENTS_SCHEMA.read_text(encoding="utf-8"))


def staging_rows(rows: Iterable[Mapping[str, Any]]) -> list[dict[str, Any]]:
    """Hàng để `insert_rows_json` vào staging: chỉ giữ cột có trong schema (trường lạ của sink cũ không
    làm hỏng cả lô); `raw_event_data` gửi dạng chuỗi JSON (BigQuery streaming nhận chuỗi cho cột JSON)."""
    names = {c["name"] for c in events_columns()}
    out = []
    for r in rows:
        row = {k: v for k, v in r.items() if k in names}
        if row.get("received_date") is None and row.get("received_ts_ms") is not None:
            # Hàng JSONL ghi trước F2-be chưa có cột phân vùng (REQUIRED): suy từ giờ nhận, như `ingest_row`.
            row["received_date"] = date_utc(int(row["received_ts_ms"]))
        raw = row.get("raw_event_data")
        if raw is not None and not isinstance(raw, str):
            row["raw_event_data"] = json.dumps(raw, ensure_ascii=False)
        out.append(row)
    return out


def table_specs() -> tuple[TableSpec, ...]:
    events = tuple(events_columns())
    return (
        TableSpec("events_staging", events, PARTITION_FIELD, STAGING_EXPIRATION_MS),
        TableSpec("events_raw", events, PARTITION_FIELD, RAW_EXPIRATION_MS, ("event_name", "user_id")),
        TableSpec("country_tier", (
            {"name": "country", "type": "STRING", "mode": "REQUIRED"},
            {"name": "tier", "type": "STRING", "mode": "REQUIRED"},
        )),
        TableSpec("fx_daily", (
            {"name": "date", "type": "DATE", "mode": "REQUIRED"},
            {"name": "currency", "type": "STRING", "mode": "REQUIRED"},
            # 1 đơn vị `currency` = `usd_per_unit` USD. Mỗi (date, currency) một dòng.
            {"name": "usd_per_unit", "type": "FLOAT64", "mode": "REQUIRED"},
        )),
        TableSpec(ADMOB_REPORT_TABLE["name"], tuple(ADMOB_REPORT_TABLE["columns"]),
                  ADMOB_REPORT_TABLE["partition_field"], None,
                  tuple(ADMOB_REPORT_TABLE["clustering_fields"])),
        TableSpec(SUBSCRIPTION_EVENTS_TABLE, tuple(subscription_event_columns()), PARTITION_FIELD,
                  RAW_EXPIRATION_MS, ("user_id", "purchase_token")),
    )


def to_bq_table(spec: TableSpec, project: str, dataset: str) -> Any:
    """`bigquery.Table` từ đặc tả (import lười: backend chạy được khi chưa cài SDK Google)."""
    from google.cloud import bigquery  # noqa: PLC0415

    schema = [bigquery.SchemaField(c["name"], c["type"], mode=c.get("mode", "NULLABLE"))
              for c in spec.columns]
    table = bigquery.Table(f"{project}.{dataset}.{spec.name}", schema=schema)
    if spec.partition_field:
        table.time_partitioning = bigquery.TimePartitioning(
            type_=bigquery.TimePartitioningType.DAY, field=spec.partition_field,
            expiration_ms=spec.expiration_ms)
    if spec.clustering_fields:
        table.clustering_fields = list(spec.clustering_fields)
    return table
