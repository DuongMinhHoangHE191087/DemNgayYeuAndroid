"""Test nhập báo cáo AdMob mediation (`accounts.mediationReport:generate`).

Fixture dựng đúng schema tài liệu (header / row / footer; tiền là micros). Không có số nào ở đây
lấy từ tài khoản thật; CSV chỉ chấp nhận cột nằm trong HEADER_MAP, cột lạ là lỗi.
"""

from __future__ import annotations

import copy
import json
from pathlib import Path

import pytest

from app import admob_import as ai

FIXTURE = Path(__file__).parent / "fixtures" / "admob_mediation_report.json"
TIERS = {"US": "T1", "DE": "T1", "VN": "VN"}  # nước không có trong bảng -> OTHER


@pytest.fixture()
def doc():
    return json.loads(FIXTURE.read_text())


def _text(doc):
    return json.dumps(doc)


def test_parse_json_reads_rows_and_micros(doc):
    rows = ai.parse_report_json(_text(doc))
    assert len(rows) == 6
    r = rows[0]
    assert (r.date, r.format, r.country, r.ad_source_label) == ("2026-09-30", "INTERSTITIAL", "US", "AdMob Network")
    assert (r.ad_requests, r.matched_requests, r.impressions) == (1000, 800, 600)
    assert r.earnings_micros == 12_000_000 and r.observed_ecpm_micros == 20_000_000
    assert r.match_rate == pytest.approx(0.8)
    assert r.currency == "USD"


def test_parse_json_rejects_empty_input():
    for empty in ("", "   ", "[]"):
        with pytest.raises(ai.AdmobImportError):
            ai.parse_report_json(empty)


def test_parse_json_rejects_report_with_no_rows(doc):
    doc = [doc[0], doc[-1]]
    doc[-1] = {"footer": {"matchingRowCount": "0"}}
    with pytest.raises(ai.AdmobImportError, match="không có dòng"):
        ai.parse_report_json(_text(doc))


def test_parse_json_rejects_unknown_dimension(doc):
    bad = copy.deepcopy(doc)
    bad[1]["row"]["dimensionValues"]["PLATFORM"] = {"value": "ANDROID"}
    with pytest.raises(ai.AdmobImportError, match="PLATFORM"):
        ai.parse_report_json(_text(bad))


def test_parse_json_rejects_unknown_metric(doc):
    bad = copy.deepcopy(doc)
    bad[1]["row"]["metricValues"]["CLICKS"] = {"integerValue": "3"}
    with pytest.raises(ai.AdmobImportError, match="CLICKS"):
        ai.parse_report_json(_text(bad))


def test_parse_json_footer_count_must_match(doc):
    doc[-1]["footer"]["matchingRowCount"] = "7"
    with pytest.raises(ai.AdmobImportError, match="footer"):
        ai.parse_report_json(_text(doc))


def test_parse_json_missing_required_metric_fails(doc):
    del doc[1]["row"]["metricValues"]["IMPRESSIONS"]
    with pytest.raises(ai.AdmobImportError, match="IMPRESSIONS"):
        ai.parse_report_json(_text(doc))


def test_parse_json_accepts_missing_observed_ecpm(doc):
    del doc[1]["row"]["metricValues"]["OBSERVED_ECPM"]
    assert ai.parse_report_json(_text(doc))[0].observed_ecpm_micros is None


def test_parse_json_accepts_ndjson_stream(doc):
    text = "\n".join(json.dumps(x) for x in doc)
    assert len(ai.parse_report_json(text)) == 6


def test_parse_json_bad_date_fails(doc):
    doc[1]["row"]["dimensionValues"]["DATE"]["value"] = "2026-09-30"
    with pytest.raises(ai.AdmobImportError, match="DATE"):
        ai.parse_report_json(_text(doc))


CSV_HEADER = "DATE,APP,FORMAT,COUNTRY,AD_SOURCE,AD_REQUESTS,MATCHED_REQUESTS,MATCH_RATE,IMPRESSIONS,ESTIMATED_EARNINGS,OBSERVED_ECPM"
CSV_ROW = "20260930,ca-app-pub-0000000000000000~0000000000,INTERSTITIAL,US,5450213213286189855,1000,800,0.8,600,12000000,20000000"


def test_parse_csv_maps_headers_to_the_same_rows(doc):
    csv_rows = ai.parse_report_csv(CSV_HEADER + "\n" + CSV_ROW + "\n", currency="USD")
    from_json = ai.parse_report_json(_text(doc))[0]
    got = csv_rows[0]
    for f in ("date", "format", "country", "ad_requests", "matched_requests", "impressions",
              "earnings_micros", "observed_ecpm_micros"):
        assert getattr(got, f) == getattr(from_json, f), f
    assert got.currency == "USD"


def test_parse_csv_unknown_header_is_error():
    with pytest.raises(ai.AdmobImportError, match="Clicks"):
        ai.parse_report_csv(CSV_HEADER + ",Clicks\n" + CSV_ROW + ",3\n", currency="USD")


def test_parse_csv_missing_header_is_error():
    header = CSV_HEADER.replace(",IMPRESSIONS", "")
    with pytest.raises(ai.AdmobImportError, match="IMPRESSIONS"):
        ai.parse_report_csv(header + "\n" + CSV_ROW + "\n", currency="USD")


def test_parse_csv_empty_file_fails():
    for empty in ("", CSV_HEADER + "\n"):
        with pytest.raises(ai.AdmobImportError):
            ai.parse_report_csv(empty, currency="USD")


def test_header_map_targets_are_known_fields():
    assert set(ai.HEADER_MAP.values()) == set(ai.DIMENSIONS.values()) | set(ai.METRICS.values())


def test_match_summary_merges_countries_into_tiers(doc):
    s = ai.match_summary(ai.parse_report_json(_text(doc)), TIERS)
    g = {(x["format"], x["tier"]): x for x in s["groups"]}
    t1 = g[("INTERSTITIAL", "T1")]  # US + DE (20260930) + US Meta (20260929)
    assert (t1["ad_requests"], t1["matched_requests"], t1["impressions"]) == (1700, 1200, 880)
    assert t1["earnings_micros"] == 16_600_000
    assert t1["match_rate"] == pytest.approx(1200 / 1700)
    assert t1["observed_ecpm_micros"] == round(16_600_000 * 1000 / 880)
    assert g[("REWARDED", "OTHER")]["impressions"] == 40  # BR không có trong bảng -> OTHER
    assert ("INTERSTITIAL", "VN") in g and ("BANNER", "T1") in g


def test_match_summary_rate_is_weighted_not_mean_of_rates(doc):
    s = ai.match_summary(ai.parse_report_json(_text(doc)), TIERS)
    t1 = {(x["format"], x["tier"]): x for x in s["groups"]}[("INTERSTITIAL", "T1")]
    assert t1["match_rate"] != pytest.approx((0.8 + 0.6 + 0.5) / 3)


def test_match_summary_compares_with_our_ad_revenue_by_tier_and_day(doc):
    ours = [{"date": "2026-09-30", "tier": "T1", "value_micros": 14_000_000, "currency": "USD"}]
    s = ai.match_summary(ai.parse_report_json(_text(doc)), TIERS, ours=ours)
    v = {(x["date"], x["tier"]): x for x in s["vs_ours"]}[("2026-09-30", "T1")]
    assert v["admob_earnings_micros"] == 15_900_000  # 12,0 + 3,0 + 0,9 triệu micros
    assert v["ours_value_micros"] == 14_000_000
    assert v["delta_micros"] == -1_900_000
    assert v["delta_ratio"] == pytest.approx(-1_900_000 / 15_900_000)


def test_match_summary_states_sim_vs_ip_caveat(doc):
    s = ai.match_summary(ai.parse_report_json(_text(doc)), TIERS)
    assert "SIM" in s["caveat"] and "IP" in s["caveat"]


def test_match_summary_currency_mismatch_is_error(doc):
    ours = [{"date": "2026-09-30", "tier": "T1", "value_micros": 1, "currency": "EUR"}]
    with pytest.raises(ai.AdmobImportError, match="currency"):
        ai.match_summary(ai.parse_report_json(_text(doc)), TIERS, ours=ours)


def test_match_summary_empty_rows_fails():
    with pytest.raises(ai.AdmobImportError):
        ai.match_summary([], TIERS)


def test_table_spec_columns_match_row_fields():
    names = [c["name"] for c in ai.ADMOB_REPORT_TABLE["columns"]]
    assert ai.ADMOB_REPORT_TABLE["name"] == "admob_report"
    assert {"date", "app", "format", "country", "ad_source", "ad_requests", "matched_requests",
            "impressions", "earnings_micros", "observed_ecpm_micros", "currency"} <= set(names)
    assert ai.ADMOB_REPORT_TABLE["partition_field"] == "date"


# ── Review R-12 ────────────────────────────────────────────────────────────────────────────────────────
def test_parse_csv_row_with_extra_cell_is_error_naming_the_row():
    with pytest.raises(ai.AdmobImportError, match="dòng 2"):
        ai.parse_report_csv(CSV_HEADER + "\n" + CSV_ROW + "\n" + CSV_ROW + ",999999\n", currency="USD")


def test_parse_csv_short_row_is_error_naming_the_row():
    short = ",".join(CSV_ROW.split(",")[:-2])
    with pytest.raises(ai.AdmobImportError, match="dòng 2"):
        ai.parse_report_csv(CSV_HEADER + "\n" + CSV_ROW + "\n" + short + "\n", currency="USD")


def test_parse_csv_duplicate_header_is_error():
    with pytest.raises(ai.AdmobImportError, match="IMPRESSIONS"):
        ai.parse_report_csv(CSV_HEADER + ",IMPRESSIONS\n" + CSV_ROW + ",7\n", currency="USD")


def test_parse_json_dimension_without_value_is_error(doc):
    del doc[1]["row"]["dimensionValues"]["APP"]["value"]
    with pytest.raises(ai.AdmobImportError, match="APP"):
        ai.parse_report_json(_text(doc))


def test_parse_json_dimension_not_an_object_is_domain_error(doc):
    doc[1]["row"]["dimensionValues"]["APP"] = "x"
    with pytest.raises(ai.AdmobImportError, match="APP"):
        ai.parse_report_json(_text(doc))


@pytest.mark.parametrize("bad", ["NaN", "Infinity", "-0.5", "1.5"])
def test_parse_csv_match_rate_must_be_a_finite_ratio(bad):
    row = CSV_ROW.replace(",0.8,", f",{bad},")
    with pytest.raises(ai.AdmobImportError, match="MATCH_RATE"):
        ai.parse_report_csv(CSV_HEADER + "\n" + row + "\n", currency="USD")


def test_parse_json_negative_count_is_error(doc):
    doc[1]["row"]["metricValues"]["AD_REQUESTS"]["integerValue"] = "-1"
    with pytest.raises(ai.AdmobImportError, match="AD_REQUESTS"):
        ai.parse_report_json(_text(doc))


def test_parse_json_footer_count_not_a_number_is_domain_error(doc):
    doc[-1]["footer"]["matchingRowCount"] = "x"
    with pytest.raises(ai.AdmobImportError, match="footer"):
        ai.parse_report_json(_text(doc))


def test_match_summary_ours_row_missing_field_is_domain_error(doc):
    with pytest.raises(ai.AdmobImportError, match="tier"):
        ai.match_summary(ai.parse_report_json(_text(doc)), TIERS, ours=[{"date": "2026-09-30", "value_micros": 1, "currency": "USD"}])


# ═══ 1.8.0 T14 — carry R-12 (m2, m3, m1 còn sót) ═══

def test_parse_json_multiple_headers_is_error(doc):
    doc.insert(1, {"header": {"localizationSettings": {"currencyCode": "EUR"}}})
    with pytest.raises(ai.AdmobImportError, match="header"):
        ai.parse_report_json(_text(doc))


def test_parse_json_matched_requests_above_ad_requests_is_error(doc):
    metrics = doc[1]["row"]["metricValues"]
    metrics["MATCHED_REQUESTS"]["integerValue"] = str(int(metrics["AD_REQUESTS"]["integerValue"]) + 1)
    with pytest.raises(ai.AdmobImportError, match="MATCHED_REQUESTS"):
        ai.parse_report_json(_text(doc))


def test_parse_json_non_object_header_or_footer_is_domain_error(doc):
    for element, idx in (("header", 0), ("footer", -1)):
        broken = copy.deepcopy(doc)
        broken[idx] = {element: "x"}
        with pytest.raises(ai.AdmobImportError, match=element):
            ai.parse_report_json(_text(broken))
