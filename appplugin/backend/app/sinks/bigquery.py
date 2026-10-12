"""Sink BigQuery — ghi vào bảng STAGING (`events_staging`).

Client được tạo ở `config.build_bq_client` (import `google.cloud.bigquery` lười ở đó): backend
phải chạy được (và test được) trên máy không cài SDK của Google. Staging chấp nhận trùng; MERGE
theo `dedup_key` (T10) mới là chỗ khử trùng, nên sink không phải nơi "sửa" dữ liệu.
"""

from __future__ import annotations

import logging
from typing import Any

from ..bq_bounds import CALL_TIMEOUT_S, request_retry

log = logging.getLogger("brain.sink.bigquery")

# `/health` là probe: MỘT lần gọi, tối đa 5 s. Không thử lại (`retry=None`) vì hạ tầng tự gọi probe lại;
# thử lại bên trong chỉ làm câu trả lời trễ hơn hạn của probe (1.7.0 F2-be2: DEFAULT_RETRY từng kéo tới ~575 s).
_PING_TIMEOUT_S = 5.0


class BigQuerySink:
    def __init__(self, client: Any, project: str, dataset: str, table: str) -> None:
        self._client = client
        self._dataset_id = f"{project}.{dataset}"
        self._table_id = f"{project}.{dataset}.{table}"

    def write(self, rows: list[dict[str, Any]]) -> None:
        if not rows:
            return
        # `insertId` cho BigQuery cơ hội tự loại bản ghi trùng trong một cửa sổ ngắn.
        # Đây là chống trùng NỖ LỰC TỐT NHẤT, không phải bảo đảm — MERGE mới là bảo đảm.
        errors = self._client.insert_rows_json(
            self._table_id,
            rows,
            row_ids=[r.get("dedup_key") or None for r in rows],
            # Tổng ≤ ~20 s (xem `bq_bounds`): BigQuery sập thì ném ra -> 5xx trước readTimeout 30 s của SDK.
            retry=request_retry(),
            timeout=CALL_TIMEOUT_S,
        )
        if errors:
            # Ném ra để endpoint trả 5xx và client GIỮ LẠI bản ghi trong hàng đợi.
            raise RuntimeError(f"BigQuery insert failed: {errors}")

    def ping(self) -> bool:
        """Sẵn sàng = đọc được dataset. Lỗi được ghi log ở đây, không trả ra ngoài."""
        try:
            self._client.get_dataset(self._dataset_id, retry=None, timeout=_PING_TIMEOUT_S)
        except Exception as exc:  # noqa: BLE001 — mọi lỗi đều là "chưa sẵn sàng"
            log.warning("bigquery ping that bai: %s", type(exc).__name__)
            return False
        return True

    def kind(self) -> str:
        return "bigquery"

    def health(self) -> str:
        return f"bigquery:{self._table_id}"
