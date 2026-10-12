"""1.7.0 F2-be — phân vùng `events_staging`/`events_raw` theo `received_date` (ngày UTC của giờ server nhận),
không theo `client_ts_date`; `ad_revenue` giữ `screen_name` (final review I1 + m1).

Vì sao (I1): BigQuery xoá NGAY hàng chèn vào phân vùng đã quá hạn giữ. Phân vùng theo ngày client + hạn 2 ngày
(staging) nghĩa là một bản replay của DurableEvents tới muộn ≥ 3 ngày, máy chậm giờ ~2 ngày hay `ts = 0` được trả
200 (SDK xoá bản trên đĩa) rồi biến mất trước MERGE. Ngày NHẬN luôn là hôm nay, nên hàng không bao giờ rơi vào
phân vùng đã hết hạn. Ngày báo cáo vẫn là ngày UTC+7 của `client_ts_ms` (view `v_events_v2`), không đổi.
"""

from __future__ import annotations

import json
import time
from datetime import datetime, timezone
from zoneinfo import ZoneInfo

import pytest

from app import bq_setup, bq_tables, daily_report
from app.models import EventRequest
from app.normalize import ingest_row
from test_bq_setup import _FakeClient

DAY_MS = 86_400_000
# 2026-09-21T14:13:20Z
RECEIVED_MS = 1_790_000_000_000


def _req(name: str, data: dict, ts: int, event_id: str = "00000000-0000-4000-8000-0000000f2be1") -> EventRequest:
    return EventRequest.model_validate({
        "sequenceId": 7, "eventName": name, "eventData": data, "ts": ts,
        "event_id": event_id, "schema_version": 2, "sdk_version": "1.7.0", "package_name": "test.sdk.zd",
        "consent_tier": "GRANTED",
        "userProperty": {"user_id": "u_f2be", "country": "US", "session_id": "s_f2be", "app_name": "demo"},
    })


def _ad_revenue(ts: int) -> EventRequest:
    return _req("ad_revenue", {"ad_format": "interstitial", "ad_source": "AdMob", "mediation": "admob",
                               "placement": "full_home", "currency": "USD", "value_micros": 3000,
                               "precision_type": 1, "screen_name": "Main"}, ts)


def _utc_day(ms: int) -> str:
    return datetime.fromtimestamp(ms / 1000, tz=timezone.utc).date().isoformat()


# ── (a) đặc tả bảng ──
def test_staging_va_raw_phan_vung_theo_received_date_cung_han_giu():
    specs = {s.name: s for s in bq_tables.table_specs()}
    st, raw = specs["events_staging"], specs["events_raw"]
    assert (st.partition_field, st.expiration_ms) == ("received_date", 172_800_000)
    assert (raw.partition_field, raw.expiration_ms) == ("received_date", 34_560_000_000)
    assert raw.clustering_fields == ("event_name", "user_id")


def test_schema_co_received_date_date_required_va_giu_client_ts_date():
    cols = {c["name"]: c for c in bq_tables.events_columns()}
    rd = cols.get("received_date", {})
    assert (rd.get("type"), rd.get("mode")) == ("DATE", "REQUIRED")
    assert cols["client_ts_date"]["type"] == "DATE"  # vẫn là cột thường cho báo cáo/đọc lại


# ── (b) ingest_row đặt received_date từ received_ts_ms (UTC) ──
def test_ingest_row_received_date_la_ngay_utc_cua_gio_nhan():
    late = ingest_row(_ad_revenue(RECEIVED_MS - 5 * DAY_MS), "demo", RECEIVED_MS)
    assert (late["client_ts_date"], late.get("received_date")) == ("2026-09-16", "2026-09-21")
    zero = ingest_row(_ad_revenue(0), "demo", RECEIVED_MS)
    assert (zero["client_ts_date"], zero.get("received_date")) == ("1970-01-01", "2026-09-21")
    # 23:30 UTC = 06:30 hôm sau ở UTC+7: received_date là ngày UTC, không phải ngày địa phương.
    near_midnight = 1_790_033_400_000  # 2026-09-21T23:30:00Z
    assert ingest_row(_ad_revenue(near_midnight), "demo", near_midnight).get("received_date") == "2026-09-21"


def test_staging_rows_bu_received_date_cho_hang_sink_cu():
    """Hàng JSONL ghi trước bản sửa (có `received_ts_ms`, chưa có `received_date`) vẫn nạp được vào staging."""
    old = {"event_name": "ad_show", "dedup_key": "ev:1", "client_ts_ms": 1, "received_ts_ms": RECEIVED_MS}
    assert bq_tables.staging_rows([old])[0].get("received_date") == "2026-09-21"


# ── (c) bq_setup: bảng có sẵn theo đặc tả cũ (client_ts_date) phải bị phát hiện ──
def test_bq_setup_bao_loi_bang_phan_vung_client_ts_date():
    c = _FakeClient()
    events = tuple(bq_tables.events_columns())
    for name, exp in (("events_staging", bq_tables.STAGING_EXPIRATION_MS),
                      ("events_raw", bq_tables.RAW_EXPIRATION_MS)):
        old = bq_tables.TableSpec(name, events, "client_ts_date", exp)
        c.tables[name] = bq_tables.to_bq_table(old, "local-test", "ds")
    err = None
    try:
        bq_setup.ensure_tables(c, "local-test", "ds")
    except bq_setup.SetupError as exc:
        err = exc
    assert err is not None, "bảng phân vùng client_ts_date phải làm bq_setup FAIL"
    assert "client_ts_date" in str(err) and "received_date" in str(err)


# ── (d) m1: ad_revenue giữ screen_name ──
def test_ad_revenue_giu_screen_name_trong_raw_event_data():
    row = ingest_row(_ad_revenue(RECEIVED_MS), "demo", RECEIVED_MS)
    assert row.get("raw_event_data") is not None, "ad_revenue mất eventData gốc"
    assert json.loads(row["raw_event_data"])["screen_name"] == "Main"
    assert (row["value_micros"], row["ad_revenue"]) == (3000, 0.003)  # cột v1/v2 không đổi


# ── (e) integration: hàng ngày client cũ + ngày nhận hôm nay sống qua staging -> MERGE -> raw, vào báo cáo ──
@pytest.mark.integration
def test_hang_ngay_client_cu_song_qua_merge_va_vao_bao_cao_ngay_client():
    from conftest import emulator_dataset, load_and_merge

    client, dataset = emulator_dataset("f2berecv")
    ds = f"local-test.{dataset}"
    for name in ("events_staging", "events_raw"):
        tp = client.get_table(f"{ds}.{name}").time_partitioning
        assert tp.field == "received_date", (name, tp.field)
    received_ms = int(time.time() * 1000)
    client_ms = received_ms - 10 * DAY_MS  # quá hạn staging 2 ngày nếu phân vùng theo ngày client
    row = ingest_row(_ad_revenue(client_ms), "demo", received_ms)
    assert load_and_merge(client, dataset, [row], runs=1) == [(1, 1)]
    got = bq_setup.run_sql(client, (
        "SELECT client_ts_date, received_date, JSON_VALUE(raw_event_data, '$.screen_name') AS screen "
        f"FROM `{ds}.events_raw`"))
    assert [(str(r["client_ts_date"]), str(r["received_date"]), r["screen"]) for r in got] == [
        (_utc_day(client_ms), _utc_day(received_ms), "Main")]
    day = datetime.fromtimestamp(client_ms / 1000, tz=ZoneInfo("Asia/Ho_Chi_Minh")).date().isoformat()
    tables, meta = daily_report.fetch(client, "local-test", dataset, day)
    assert (meta["rows_total"], meta["schema_versions"]) == (1, {2: 1})
    ecpm = tables["v_ecpm_format_tier"]
    assert [(r["ad_format"], r["impressions"]) for r in ecpm] == [("interstitial", 1)]
