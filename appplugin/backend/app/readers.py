"""Đọc lại bản ghi đã lưu.

Cùng một hàm `metrics.compute()` chạy trên cả hai nguồn — cố ý không viết lại phép tính
bằng SQL cho BigQuery. Hai bản cài đặt của cùng một định nghĩa chỉ số sẽ trôi khỏi nhau,
và khi hai dashboard hiện hai con số khác nhau thì không ai biết bên nào đúng.

Đánh đổi đã biết: đọc hàng thô về rồi tính bằng Python không co giãn được lâu. Ở khối lượng
của mốc 2 thì ổn; khi log lớn lên phải chuyển phép gộp vào SQL — và lúc đó phải chuyển
`metrics.compute()` thành bộ test đối chiếu cho bản SQL, đừng bỏ nó đi.
"""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any, Iterator

from .bq_bounds import CALL_TIMEOUT_S, QUERY_WAIT_S, request_retry


class JsonlReader:
    def __init__(self, path: str | Path) -> None:
        self.path = Path(path)

    def read(self, date_from: str | None = None, date_to: str | None = None) -> Iterator[dict[str, Any]]:
        if not self.path.exists():
            return iter(())
        return self._iter(date_from, date_to)

    def _iter(self, date_from: str | None, date_to: str | None) -> Iterator[dict[str, Any]]:
        with open(self.path, encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if not line:
                    continue
                try:
                    row = json.loads(line)
                except json.JSONDecodeError:
                    # Một dòng hỏng không được làm hỏng cả báo cáo. Bỏ qua và đi tiếp —
                    # ném lỗi ở đây sẽ khiến dashboard trắng vì một byte lỗi.
                    continue
                d = row.get("client_ts_date") or ""
                if date_from and d < date_from:
                    continue
                if date_to and d > date_to:
                    continue
                yield row


class BigQueryReader:
    """Import lười, cùng lý do với `BigQuerySink`.

    `client` do `config.build_bq_client` tạo (dùng chung với sink, và là nơi duy nhất quyết định
    emulator hay BigQuery thật) — không tự tạo client ở đây.
    """

    def __init__(self, project: str, dataset: str, table: str, client: Any = None) -> None:
        from google.cloud import bigquery  # noqa: PLC0415

        self._bq = bigquery
        self._client = client if client is not None else bigquery.Client(project=project)
        self._table_id = f"{project}.{dataset}.{table}"

    def read(self, date_from: str | None = None, date_to: str | None = None) -> Iterator[dict[str, Any]]:
        # Lọc theo ngày CLIENT (`client_ts_date`), đúng định nghĩa ngày của dashboard. Từ 1.7.0 F2-be
        # cột phân vùng là `received_date`, nên lọc này không còn cắt phân vùng: không lọc theo ngày
        # nhận vì sẽ bỏ sót hàng replay tới muộn. Chi phí quét trên BigQuery thật: H5.
        where, params = [], []
        if date_from:
            where.append("client_ts_date >= @date_from")
            params.append(self._bq.ScalarQueryParameter("date_from", "DATE", date_from))
        if date_to:
            where.append("client_ts_date <= @date_to")
            params.append(self._bq.ScalarQueryParameter("date_to", "DATE", date_to))
        clause = (" WHERE " + " AND ".join(where)) if where else ""
        # Đường request (`/metrics/*`): mọi lời gọi có hạn (`bq_bounds`); không `job_retry` (mặc định chạy lại
        # job tới 40 phút) — lỗi thì endpoint trả 5xx, người gọi thử lại.
        job = self._client.query(
            f"SELECT * FROM `{self._table_id}`{clause}",
            job_config=self._bq.QueryJobConfig(query_parameters=params),
            retry=request_retry(), timeout=CALL_TIMEOUT_S, job_retry=None,
        )
        for row in job.result(retry=request_retry(), timeout=QUERY_WAIT_S, job_retry=None):
            d = dict(row)
            if d.get("client_ts_date") is not None:
                d["client_ts_date"] = str(d["client_ts_date"])
            yield d
