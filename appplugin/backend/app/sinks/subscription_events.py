"""Bảng `subscription_events` (1.8.0 T13): ghi hàng RTDN, đọc cho `signals`, tra `iap_purchase` theo token.

Hai bản như sink sự kiện — JSONL (dev, `tools/local_sink.sh`, test) và BigQuery — cùng ba thao tác:

- `write(row) -> bool`: chỉ trả khi đã BỀN (doc 46 C.1; ném = `/rtdn` 5xx, Pub/Sub gửi lại). `False` = `dedup_key`
  (`rtdn:<messageId>`) đã có: Pub/Sub giao lại cùng tin, 2xx mà không ghi thêm.
- `recent(app_id, user_id, since_ms)`: hàng của MỘT người của MỘT app nhận từ `since_ms` (giờ server) — nguồn `signals`.
- `purchase_for_token(token)`: hàng `iap_purchase` ĐẦU TIÊN (theo giờ nhận) của token -> `user_id`, `app_id`,
  `product_id`, giá. Lấy hàng đầu chứ không phải mới nhất: token là bí mật của người mua, app thật báo nó ngay lúc mua;
  một client khác gửi lại cùng token sau đó không chiếm được `signals` của người mua (`/reconcile/purchases` báo token
  xuyên tài khoản).
"""

from __future__ import annotations

import json
import os
import threading
from pathlib import Path
from typing import Any

from ..bq_bounds import CALL_TIMEOUT_S, request_retry
from ..bq_tables import SUBSCRIPTION_EVENTS_TABLE, ds_ref
from ..normalize import date_utc
from ..readers import JsonlReader

# `signals` đọc trên đường `/push` SAU khi sink đã ghi (≤ ~20 s, `bq_bounds`): một lần gọi, không thử lại, hạn ngắn,
# để tổng vẫn dưới readTimeout 30 s của SDK. Lỗi/quá hạn -> `signals` rỗng (xem `rtdn.push_signals`).
SIGNALS_CALL_TIMEOUT_S = 3.0
_PURCHASE_FIELDS = ("user_id", "app_id", "product_id", "price_micros", "currency_code")


def _purchase_info(row: dict[str, Any] | None) -> dict[str, Any] | None:
    return None if row is None else {k: row.get(k) for k in _PURCHASE_FIELDS}


class JsonlSubscriptionStore:
    """`subscription_events.jsonl` cạnh file sự kiện của `JsonlSink`; quét file mỗi lần đọc (bảng nhỏ, chỉ dev)."""

    FILE_NAME = "subscription_events.jsonl"

    def __init__(self, events_path: str | Path) -> None:
        self._events = JsonlReader(events_path)
        self.path = Path(events_path).parent / self.FILE_NAME
        self.path.parent.mkdir(parents=True, exist_ok=True)
        self._rows = JsonlReader(self.path)
        self._lock = threading.Lock()

    def write(self, row: dict[str, Any]) -> bool:
        payload = json.dumps(row, ensure_ascii=False) + "\n"
        with self._lock:
            if any(r.get("dedup_key") == row["dedup_key"] for r in self._rows.read()):
                return False
            with open(self.path, "a", encoding="utf-8") as f:
                f.write(payload)
                f.flush()
                os.fsync(f.fileno())
        return True

    def recent(self, app_id: str, user_id: str, since_ms: int) -> list[dict[str, Any]]:
        return [r for r in self._rows.read()
                if r.get("app_id") == app_id and r.get("user_id") == user_id
                and int(r.get("received_ts_ms") or 0) >= since_ms]

    def purchase_for_token(self, token: str) -> dict[str, Any] | None:
        first: dict[str, Any] | None = None
        for r in self._events.read():
            if r.get("event_name") != "iap_purchase" or r.get("purchase_token") != token:
                continue
            if first is None or int(r.get("received_ts_ms") or 0) < int(first.get("received_ts_ms") or 0):
                first = r
        return _purchase_info(first)


class BigQuerySubscriptionStore:
    """Bảng `subscription_events` + tra token trên `events_staging` ∪ `events_raw` (MERGE chạy theo ngày: lượt mua hôm
    nay mới ở staging). Mọi lời gọi có hạn (`bq_bounds`); client do `config.build_bq_client` tạo."""

    def __init__(self, client: Any, project: str, dataset: str, staging_table: str, raw_table: str) -> None:
        from google.cloud import bigquery  # noqa: PLC0415

        self._bq = bigquery
        self._client = client
        ds = ds_ref(project, dataset)
        self._table = f"{ds}.{SUBSCRIPTION_EVENTS_TABLE}"
        self._staging = f"{ds}.{staging_table}"
        self._raw = f"{ds}.{raw_table}"

    def _query(self, sql: str, params: list[Any], *, fast: bool = False) -> list[dict[str, Any]]:
        retry = None if fast else request_retry()
        timeout = SIGNALS_CALL_TIMEOUT_S if fast else CALL_TIMEOUT_S
        job = self._client.query(sql, job_config=self._bq.QueryJobConfig(query_parameters=params),
                                 retry=retry, timeout=timeout, job_retry=None)
        return [dict(r) for r in job.result(retry=retry, timeout=timeout, job_retry=None)]

    def write(self, row: dict[str, Any]) -> bool:
        key = self._bq.ScalarQueryParameter("k", "STRING", row["dedup_key"])
        if self._query(f"SELECT 1 AS x FROM `{self._table}` WHERE dedup_key = @k LIMIT 1", [key]):
            return False
        # `row_ids` = khoá chống trùng nỗ lực tốt nhất cho hai lần giao gần nhau; `recent` chỉ lấy mới nhất mỗi
        # sản phẩm nên một hàng trùng lọt qua cũng không đổi `signals`.
        errors = self._client.insert_rows_json(self._table, [row], row_ids=[row["dedup_key"]],
                                               retry=request_retry(), timeout=CALL_TIMEOUT_S)
        if errors:
            raise RuntimeError(f"BigQuery insert subscription_events failed: {errors}")
        return True

    def recent(self, app_id: str, user_id: str, since_ms: int) -> list[dict[str, Any]]:
        p = self._bq.ScalarQueryParameter
        sql = (f"SELECT kind, product_id, state, event_ms, received_ts_ms FROM `{self._table}` "
               "WHERE app_id = @a AND user_id = @u AND received_ts_ms >= @since AND received_date >= @since_date")
        return self._query(sql, [p("a", "STRING", app_id), p("u", "STRING", user_id), p("since", "INT64", since_ms),
                                 p("since_date", "DATE", date_utc(since_ms))], fast=True)

    def purchase_for_token(self, token: str) -> dict[str, Any] | None:
        cols = "user_id, app_id, product_id, price_micros, currency_code, received_ts_ms"
        where = "event_name = 'iap_purchase' AND purchase_token = @t"
        sql = (f"SELECT {cols} FROM `{self._staging}` WHERE {where} UNION ALL "
               f"SELECT {cols} FROM `{self._raw}` WHERE {where} ORDER BY received_ts_ms ASC LIMIT 1")
        rows = self._query(sql, [self._bq.ScalarQueryParameter("t", "STRING", token)])
        return _purchase_info(rows[0]) if rows else None


def build_subscription_store(settings: Any, bq_client: Any = None) -> Any:
    if settings.sink == "bigquery":
        return BigQuerySubscriptionStore(bq_client, settings.bq_project, settings.bq_dataset,
                                         settings.bq_staging_table, settings.bq_table)
    return JsonlSubscriptionStore(settings.jsonl_path)
