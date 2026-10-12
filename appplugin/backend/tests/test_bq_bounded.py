"""1.7.0 F2-be2 — mọi lời gọi BigQuery trên đường request có tổng thời gian bị chặn (E-BE #16, concern 1).

Mặc định của google-cloud-bigquery (`DEFAULT_RETRY`, hạn ~600 s) thử lại lỗi transport gần 10 phút: `/health` từng
trả 503 sau ~575 s khi emulator tắt, và `/push` giữ một thread worker lâu như vậy. Ở đây dùng `bigquery.Client`
THẬT với session HTTP luôn ném `requests.ConnectionError` (lỗi transport mà DEFAULT_RETRY thử lại), để đo đúng
logic thử lại của thư viện chứ không phải của một client giả. Lời gọi chạy trong thread daemon: code cũ không
trả về trong giới hạn -> assert đỏ thay vì treo cả phiên test.
"""

from __future__ import annotations

import threading
import time

import requests
from fastapi.testclient import TestClient
from google.api_core.client_options import ClientOptions
from google.auth.credentials import AnonymousCredentials
from google.cloud import bigquery

import app.main
from app import bq_bounds
from app.config import Settings
from app.readers import BigQueryReader
from app.sinks import bigquery as bq_sink

# SDK: `LogEventTransport.kt:117-118` connectTimeout(30 s) + readTimeout(30 s). `/push` phải trả lời (5xx)
# trước khi SDK tự bỏ cuộc, nếu không thread worker bị giữ cho một phản hồi không ai đọc.
SDK_READ_TIMEOUT_S = 30.0
_NOT_DONE = object()


class _DownSession(requests.Session):
    """BigQuery không tới được: mọi request HTTP ném lỗi kết nối; ghi lại `timeout` của từng lần gọi."""

    def __init__(self) -> None:
        super().__init__()
        self.timeouts: list = []

    def request(self, method, url, **kwargs):  # noqa: D102
        self.timeouts.append(kwargs.get("timeout"))
        raise requests.exceptions.ConnectionError("bigquery down")


def _down_client() -> tuple[bigquery.Client, _DownSession]:
    session = _DownSession()
    client = bigquery.Client(project="demo-proj", credentials=AnonymousCredentials(), _http=session,
                             client_options=ClientOptions(api_endpoint="http://127.0.0.1:9"))
    return client, session


def _spy(client, name: str) -> list[dict]:
    """Ghi kwargs của `client.<name>` rồi gọi bản thật."""
    calls: list[dict] = []
    real = getattr(client, name)

    def wrapper(*args, **kwargs):
        calls.append(kwargs)
        return real(*args, **kwargs)

    setattr(client, name, wrapper)
    return calls


def _run_within(fn, limit_s: float):
    """(kết quả hoặc _NOT_DONE, giây đã chạy). Thread daemon: code cũ thử lại 10 phút không giữ phiên test."""
    box: list = []
    t0 = time.monotonic()
    th = threading.Thread(target=lambda: box.append(fn()), daemon=True)
    th.start()
    th.join(limit_s)
    return (box[0] if box else _NOT_DONE), time.monotonic() - t0


def _deadline(retry) -> float | None:
    return None if retry is None else retry.timeout


# ── (a) /health: ping một lần, chặn bởi _PING_TIMEOUT_S ──
def test_ping_bigquery_sap_tra_false_trong_gioi_han_mot_lan_goi():
    # Ghim trần: kết nối bị từ chối trả lỗi ngay nên giới hạn thời gian bên dưới không thấy một hằng 600 s;
    # endpoint "nuốt" gói SYN thì chính hằng này là thời gian /health bị giữ (R-F2be2 m2).
    assert bq_sink._PING_TIMEOUT_S <= 5.0
    client, session = _down_client()
    calls = _spy(client, "get_dataset")
    sink = bq_sink.BigQuerySink(client, "demo-proj", "ds", "events_staging")
    limit = 2 * bq_sink._PING_TIMEOUT_S
    got, elapsed = _run_within(sink.ping, limit)
    assert got is False, f"ping không trả lời trong {limit} s (thử lại theo DEFAULT_RETRY)"
    assert elapsed < limit
    assert len(calls) == 1 and "retry" in calls[0], calls
    assert calls[0]["retry"] is None and calls[0]["timeout"] == bq_sink._PING_TIMEOUT_S, calls
    assert session.timeouts == [bq_sink._PING_TIMEOUT_S]  # đúng một lần gọi HTTP


def test_health_bigquery_sap_tra_503_nhanh(monkeypatch):
    client, _ = _down_client()
    monkeypatch.setattr(app.main, "build_bq_client", lambda settings: client)
    api = TestClient(app.main.create_app(Settings(sink="bigquery", bq_project="demo-proj")),
                     raise_server_exceptions=False)
    limit = 2 * bq_sink._PING_TIMEOUT_S
    got, elapsed = _run_within(lambda: api.get("/health").status_code, limit)
    assert got == 503, f"/health không trả 503 trong {limit} s: {got!r}"


# ── (b) /push: insert có retry với hạn + timeout mỗi lần gọi; BigQuery sập -> 5xx trước readTimeout của SDK ──
def test_push_bigquery_sap_tra_5xx_truoc_read_timeout_cua_sdk(monkeypatch):
    # Trần thật (tệ nhất: lần thử cuối bắt đầu ngay trước hạn và chờ trọn timeout của nó) < readTimeout của SDK.
    assert bq_bounds.RETRY_DEADLINE_S + bq_bounds.CALL_TIMEOUT_S < SDK_READ_TIMEOUT_S
    # Rồi rút hạn thử lại còn 1 s để test không tốn ~10 s thật trong CI: vẫn là DEFAULT_RETRY thật của thư viện
    # với lỗi transport thật; assert bên dưới chứng minh hạn đi đúng từ `bq_bounds` vào lời gọi (R-F2be2 m3).
    monkeypatch.setattr(bq_bounds, "RETRY_DEADLINE_S", 1.0)
    client, session = _down_client()
    calls = _spy(client, "insert_rows_json")
    monkeypatch.setattr(app.main, "build_bq_client", lambda settings: client)
    api = TestClient(app.main.create_app(Settings(sink="bigquery", bq_project="demo-proj")),
                     raise_server_exceptions=False)
    body = {"sequenceId": 1, "eventName": "ad_show", "eventData": {}, "ts": 1790000000000,
            "userProperty": {"user_id": "u1"}}
    got, elapsed = _run_within(lambda: api.post("/push", json=body).status_code, SDK_READ_TIMEOUT_S)
    assert got is not _NOT_DONE, f"/push không trả lời trong {SDK_READ_TIMEOUT_S} s (readTimeout của SDK)"
    assert 500 <= got < 600, got
    assert len(calls) == 1 and calls[0].get("retry") is not None and calls[0].get("timeout"), calls
    deadline, per_call = _deadline(calls[0]["retry"]), calls[0]["timeout"]
    assert (deadline, per_call) == (1.0, bq_bounds.CALL_TIMEOUT_S), (deadline, per_call)
    assert elapsed < 2 * (deadline + per_call), elapsed
    assert session.timeouts and all(t == per_call for t in session.timeouts)


# ── Reader của các endpoint /metrics: query + result đều có hạn, không job_retry (mặc định 40 phút) ──
class _Job:
    def __init__(self, record):
        self._record = record

    def result(self, **kwargs):
        self._record.append(("result", kwargs))
        return []


class _RecordingClient:
    def __init__(self):
        self.calls: list = []

    def query(self, sql, **kwargs):
        self.calls.append(("query", kwargs))
        return _Job(self.calls)


def test_reader_query_va_result_co_han():
    c = _RecordingClient()
    assert list(BigQueryReader("demo-proj", "ds", "events_raw", client=c).read("2026-09-01", "2026-09-02")) == []
    (qn, q), (rn, r) = c.calls
    assert (qn, rn) == ("query", "result")
    for kw in (q, r):
        assert kw.get("retry") is not None and _deadline(kw["retry"]) is not None, kw
        assert kw.get("timeout") and "job_retry" in kw and kw["job_retry"] is None, kw
        assert _deadline(kw["retry"]) + kw["timeout"] <= 2 * SDK_READ_TIMEOUT_S, kw
