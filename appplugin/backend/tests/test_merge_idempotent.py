"""1.7.0 T10 (integration) — MERGE staging -> raw trên bigquery-emulator: idempotent, khoá theo app.

Fixture: 40 dòng staging = 37 cặp (app_id, dedup_key) khác nhau (3 cặp trùng; +3 hàng 1.9.0 T9) — trong đó `ev:` của r1
xuất hiện ở hai app. Hạn giữ phân vùng chỉ kiểm được qua metadata (emulator không thi hành).
"""

from __future__ import annotations

import pytest

from app import bq_setup
from conftest import emulator_dataset, load_and_merge, load_fixture_rows

pytestmark = pytest.mark.integration

R1_KEY = "ev:00000000-0000-4000-8000-000000000001"


@pytest.fixture(scope="module")
def merged():
    client, dataset = emulator_dataset("t10merge")
    counts = load_and_merge(client, dataset, load_fixture_rows(), runs=2)
    return client, dataset, counts


def test_merge_hai_lan_bang_so_khoa_va_lan_hai_them_0(merged):
    _, _, counts = merged
    assert counts == [(37, 37), (37, 37)]


def test_hai_app_cung_event_id_deu_con_sau_merge(merged):
    """Phán quyết R-9: cùng `ev:<id>` dưới hai app là hai hàng, không gộp."""
    client, dataset, _ = merged
    rows = bq_setup.run_sql(client, f"SELECT app_id FROM `local-test.{dataset}.events_raw` "
                                    f"WHERE dedup_key = '{R1_KEY}' ORDER BY app_id")
    assert [r["app_id"] for r in rows] == ["demo", "other"]


def test_han_giu_phan_vung_doc_tu_metadata(merged):
    client, dataset, _ = merged
    raw = client.get_table(f"local-test.{dataset}.events_raw")
    st = client.get_table(f"local-test.{dataset}.events_staging")
    assert raw.time_partitioning.expiration_ms == 34_560_000_000   # 400 ngày
    assert st.time_partitioning.expiration_ms == 172_800_000       # 2 ngày
    assert raw.clustering_fields == ["event_name", "user_id"]
