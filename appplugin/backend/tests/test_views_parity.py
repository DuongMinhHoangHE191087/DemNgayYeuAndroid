"""1.7.0 T10 (integration) — mọi view SQL (`ad_metrics.VIEWS`, 9 từ 1.8.0 T12, 11 từ 1.9.0 T9: `v_brain_decisions`,
`v_ecpm_network_format`, nhánh holdout của `v_paywall_funnel`) trên bigquery-emulator = oracle `app/ad_metrics.py`,
Δ = 0.

Cùng fixture v2 (quảng cáo + IAP/paywall của `test_iap_v2.iap_fixture_rows`) qua hai đường: staging -> MERGE -> view, và `ad_metrics.dedup` -> hàm oracle. So từng hàng;
số thực (FLOAT64) so với dung sai tuyệt đối 1e-9 (thứ tự cộng khác nhau), mọi thứ khác bằng tuyệt đối.
"""

from __future__ import annotations

import datetime as dt

import pytest

from app import ad_metrics as am
from app import bq_setup
from conftest import (FIXTURE_DAY, FIXTURE_FX, emulator_dataset, load_and_merge, load_fixture_rows,
                      show_rate_views_rows)
from test_iap_v2 import iap_fixture_rows

pytestmark = pytest.mark.integration

TOL = 1e-9
FX = {(r["date"], r["currency"]): r["usd_per_unit"] for r in FIXTURE_FX}


def _rows() -> list[dict]:
    return load_fixture_rows() + iap_fixture_rows() + show_rate_views_rows()


@pytest.fixture(scope="module")
def stack():
    client, dataset = emulator_dataset("t10parity")
    assert client.insert_rows_json(f"local-test.{dataset}.fx_daily", FIXTURE_FX) == []
    load_and_merge(client, dataset, _rows(), runs=1)
    return client, dataset


def _norm(v):
    if isinstance(v, dt.date):
        return v.isoformat()
    return v


def _same(a, b) -> bool:
    if isinstance(a, (int, float)) and isinstance(b, (int, float)) and not isinstance(a, bool):
        return abs(float(a) - float(b)) <= TOL
    return a == b


def _key(row: dict) -> str:
    # `is_holdout` (INT64) là khoá của `v_brain_decisions`: hai hàng chỉ khác nó không được gộp làm một.
    return repr([(k, row[k]) for k in sorted(row) if isinstance(row[k], str) or k in ("install_day", "is_holdout")])


@pytest.mark.parametrize("view", list(am.VIEWS))
def test_view_bang_oracle(stack, view):
    client, dataset = stack
    sql_rows = [{k: _norm(v) for k, v in r.items()}
                for r in bq_setup.run_sql(client, f"SELECT * FROM `local-test.{dataset}.{view}`")]
    oracle = am.VIEWS[view](am.dedup(_rows()), am.load_country_tiers(), FX)
    assert oracle, f"oracle {view} rỗng trên fixture"
    assert len(sql_rows) == len(oracle), (sql_rows, oracle)
    by_key = {_key(r): r for r in oracle}
    for got in sql_rows:
        want = by_key.get(_key(got))
        assert want is not None, f"{view}: hàng SQL không có trong oracle: {got}"
        assert set(got) == set(want), f"{view}: cột lệch {sorted(set(got) ^ set(want))}"
        bad = {k: (got[k], want[k]) for k in want if not _same(got[k], want[k])}
        assert not bad, f"{view} {_key(got)}: {bad}"


def test_show_rate_sql_bo_full_2(stack):
    """R-2 m4: `ad_show` Full2 (`full_2_default`, không có attempt) không vào tử số của view SQL."""
    client, dataset = stack
    rows = bq_setup.run_sql(client, f"SELECT shows, attempts_ready FROM `local-test.{dataset}.v_show_rate` "
                                    "WHERE app = 'demo' AND ad_format = 'interstitial'")
    assert [(r["shows"], r["attempts_ready"]) for r in rows] == [(2, 2)]


def test_B6_sql_view_show_rate_theo_phien_view_banner_1_0(stack):
    """2.2.0 BE3 (chạy ở E-BE, Docker): sink emu37 -> banner 6 khoá phiên-view, 6 hiện = 1.0 (`v_show_rate` 6/34)."""
    client, dataset = stack
    got = bq_setup.run_sql(client, f"SELECT views, views_shown, view_show_rate FROM `local-test.{dataset}.v_show_rate_views` "
                                   "WHERE app = 'default' AND ad_format = 'banner'")
    assert [(r["views"], r["views_shown"], r["view_show_rate"]) for r in got] == [(6, 6, 1.0)]


def test_B7_sql_fail_fullscreen_lay_nhan_attempt(stack):
    """2.2.0 BE3 (E-BE): cột nhãn của `v_show_rate_reasons` không còn `fullscreen` cho fail có attempt cùng phiên/placement
    — SQL = oracle: `test_view_bang_oracle[v_show_rate_reasons]` so từng hàng; ở đây khẳng định riêng một nhãn."""
    client, dataset = stack
    want = {r["ad_format"] for r in am.VIEWS["v_show_rate_reasons"](am.dedup(_rows()), am.load_country_tiers(), FX)}
    got = {r["ad_format"] for r in bq_setup.run_sql(client, f"SELECT ad_format FROM `local-test.{dataset}.v_show_rate_reasons`")}
    assert got == want


def test_B8_iap_gui_lai_cung_token_qua_merge_tinh_mot_lan(stack):
    """1.8.0 T12 (D13/D15): bản phát lại `iap_purchase` (cùng token, `order_id` rỗng, `event_id` mới) bị MERGE
    gộp theo `iap_purchase:tok:<token>`; trial báo nhầm giá niêm yết = 0. US: 4.99 USD, 2 lượt mua; phễu shop/A
    4 phiên, 2 mua, 4.99."""
    client, dataset = stack
    ds = f"local-test.{dataset}"
    keys = bq_setup.run_sql(client, f"SELECT dedup_key, COUNT(*) AS n FROM `{ds}.events_raw` "
                                    "WHERE event_name = 'iap_purchase' GROUP BY dedup_key")
    assert {r["dedup_key"]: r["n"] for r in keys} == {
        "iap_purchase:tok:tokA": 1, "iap_purchase:tok:tokB": 1, "iap_purchase:tok:tokD": 1,
        "iap_purchase:tok:tokE": 1, "iap_purchase:tok:tokH": 1}  # tokH: u5 FR ngoài paywall (1.9.0 T9, nhánh holdout)
    us = bq_setup.run_sql(client, f"SELECT iap_revenue_usd, iap_purchases FROM `{ds}.v_arpdau_country` "
                                  f"WHERE app = 'demo' AND country = 'US' AND day = DATE '{FIXTURE_DAY}'")
    assert [(round(r["iap_revenue_usd"], 9), r["iap_purchases"]) for r in us] == [(4.99, 2)]
    a = bq_setup.run_sql(client, f"SELECT sessions, purchases, revenue_usd FROM `{ds}.v_paywall_funnel` "
                                 f"WHERE app = 'demo' AND day = DATE '{FIXTURE_DAY}' AND placement = 'shop' "
                                 "AND variant_id = 'A'")
    assert [(r["sessions"], r["purchases"], round(r["revenue_usd"], 9)) for r in a] == [(4, 2, 4.99)]
