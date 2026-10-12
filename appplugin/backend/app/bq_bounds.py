"""Giới hạn thời gian cho lời gọi BigQuery trên đường request (1.7.0 F2-be2, E-BE #16 concern 1).

Mặc định của google-cloud-bigquery là `DEFAULT_RETRY` (thử lại lỗi transport tới ~600 s) và `timeout=None`:
khi BigQuery không tới được, `/health` từng trả 503 sau ~575 s và `/push` giữ một thread worker gần 10 phút.
SDK thì bỏ cuộc sau readTimeout 30 s (`LogEventTransport.kt:117-118`: connectTimeout 30 s, readTimeout 30 s)
rồi gửi lại (5xx được thử lại; `dedup_key` làm bản trùng vô hại). Nên `/push` phải trả 5xx trước 30 s.

Tổng thời gian tệ nhất của một lời gọi ≈ `RETRY_DEADLINE_S + CALL_TIMEOUT_S` (lần thử cuối có thể bắt đầu
ngay trước hạn và chờ trọn timeout của nó) = 20 s < 30 s. `/health` không thử lại (`sinks/bigquery.py`).

Chỉ đường request dùng các giới hạn này. Công cụ batch/offline giữ mặc định của thư viện: `bq_setup.py`,
`daily_report.py` (`tools/report_daily.sh`), `tools/stack_load.py`, `sql/merge_events.sql` (chạy qua `stack_load.py`).
"""

from __future__ import annotations

from typing import Any

# Một lần gọi HTTP (connect + read của `requests`).
CALL_TIMEOUT_S = 10.0
# Tổng thời gian thử lại lỗi tạm thời (transport, 5xx, rate limit) của một lời gọi.
RETRY_DEADLINE_S = 10.0
# Chờ một query của reader (`/metrics/*`) chạy xong.
QUERY_WAIT_S = 30.0


def request_retry() -> Any:
    """`DEFAULT_RETRY` của thư viện (cùng điều kiện thử lại) với hạn `RETRY_DEADLINE_S` (import lười)."""
    from google.cloud.bigquery.retry import DEFAULT_RETRY  # noqa: PLC0415

    return DEFAULT_RETRY.with_timeout(RETRY_DEADLINE_S)
