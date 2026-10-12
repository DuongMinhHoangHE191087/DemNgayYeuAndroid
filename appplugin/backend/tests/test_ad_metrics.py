"""1.7.0 T10 — oracle Python của các view DOC/62 §2.1 (`app/ad_metrics.py`) trên fixture v2 (11 view từ 1.9.0 T9).

Fixture `ad_v2_fixture.jsonl`: app `demo`, 5 người (u1 US, u2 DE, u3 VN, u4 DEFAULT, u5 FR), gần như
mọi event rơi vào ngày địa phương UTC+7 2026-09-30; 3 cặp trùng (ad_revenue r1, ad_show fullscreen,
ad_request rD); cùng `event_id` của r1 dưới app `other`; 1 hàng v1 (không envelope); 1 `ad_show`
interstitial của Full2 (`full_2_default`, u2) — Full2 không có `ad_show_attempt` (T2). 1.9.0 T9: `segment` trên 4
`brain_decision`, `slot` trên request rA, + 3 hàng (u1 `ad_show_fail` brain từ chối; u5 `paywall_holdout_skip` và
`iap_purchase` ngoài paywall 1.99 USD). Mọi số kỳ vọng dưới đây tính tay, ghi trong docstring từng test.
"""

from __future__ import annotations

import pytest

from app import ad_metrics as am
from app import show_rate_reasons as srr
from conftest import FIXTURE_DAY, show_rate_views_rows

D = FIXTURE_DAY
FX = {(D, "EUR"): 1.1}


def _deduped(rows):
    return am.dedup(rows)


def _by(rows, *keys):
    return {tuple(r[k] for k in keys): r for r in rows}


def test_dedup_giu_mot_hang_moi_app_va_khoa(fixture_rows):
    """40 dòng -> 37: bỏ 3 bản trùng; `ev:` của r1 ở app `other` KHÔNG bị gộp với app `demo`."""
    out = _deduped(fixture_rows)
    assert len(fixture_rows) == 40
    assert len(out) == 37
    keys = {(r["app_id"], r["dedup_key"]) for r in out}
    assert len(keys) == 37
    r1 = [r for r in out if r["dedup_key"] == "ev:00000000-0000-4000-8000-000000000001"]
    assert sorted(r["app_id"] for r in r1) == ["demo", "other"]
    # Bản giữ lại là bản nhận sớm nhất (bản trùng tới sau 60 s).
    kept = next(r for r in r1 if r["app_id"] == "demo")
    assert kept["received_ts_ms"] == kept["client_ts_ms"] + 500


def test_ngay_dia_phuong_utc7():
    """1790703000000 = 2026-09-29 17:30Z = 2026-09-30 00:30 +07 -> ngày báo cáo 2026-09-30."""
    assert am.local_day(1790703000000) == "2026-09-30"
    assert am.local_day(1790650800000) == "2026-09-29"


def test_bang_country_tier_tu_seed():
    """T1 13 nước, T2 23, VN; `DEFAULT` và rỗng không có trong bảng -> OTHER ở view."""
    tiers = am.load_country_tiers()
    assert sum(1 for t in tiers.values() if t == "T1") == 13
    assert sum(1 for t in tiers.values() if t == "T2") == 23
    assert tiers.get("VN") == "VN" and tiers.get("US") == "T1" and tiers.get("JP") == "T2"
    assert "DEFAULT" not in tiers and "" not in tiers
    assert am.tier_of("DEFAULT", tiers) == "OTHER" and am.tier_of("", tiers) == "OTHER"


def test_ecpm_theo_format_va_tier(fixture_rows):
    """demo/D: interstitial T1 = (3000+2000) µ$ / 2 × 1000 / 1e6 = 2.5; interstitial OTHER (DEFAULT)
    = 1.5; rewarded VN 4000 µEUR × 1.1 = 4.4; fullscreen T1 GBP không tỉ giá -> unconverted 1,
    eCPM None; rewarded meta_direct 0 bị loại (không có hàng T1 rewarded); app other = 3.0."""
    got = _by(am.ecpm_format_tier(_deduped(fixture_rows), am.load_country_tiers(), FX),
              "app", "day", "ad_format", "tier")
    assert set(got) == {("demo", D, "interstitial", "T1"), ("demo", D, "interstitial", "OTHER"),
                        ("demo", D, "rewarded", "VN"), ("demo", D, "fullscreen", "T1"),
                        ("other", D, "interstitial", "T1")}
    t1 = got[("demo", D, "interstitial", "T1")]
    assert (t1["impressions"], t1["impressions_converted"], t1["unconverted"]) == (2, 2, 0)
    assert t1["revenue_usd"] == pytest.approx(0.005) and t1["ecpm_usd"] == pytest.approx(2.5)
    assert got[("demo", D, "interstitial", "OTHER")]["ecpm_usd"] == pytest.approx(1.5)
    assert got[("demo", D, "rewarded", "VN")]["ecpm_usd"] == pytest.approx(4.4)
    fs = got[("demo", D, "fullscreen", "T1")]
    assert (fs["impressions"], fs["unconverted"], fs["ecpm_usd"]) == (1, 1, None)
    assert got[("other", D, "interstitial", "T1")]["ecpm_usd"] == pytest.approx(3.0)


def test_ecpm_khong_ti_gia_thi_unconverted_khong_bia(fixture_rows):
    """Không nạp EUR: rewarded VN -> impressions 1, converted 0, unconverted 1, eCPM None."""
    got = _by(am.ecpm_format_tier(_deduped(fixture_rows), am.load_country_tiers(), {}),
              "app", "day", "ad_format", "tier")
    assert len(got) == 5
    vn = got[("demo", D, "rewarded", "VN")]
    assert (vn["impressions_converted"], vn["unconverted"], vn["ecpm_usd"]) == (0, 1, None)


def test_impression_tren_dau_theo_format(fixture_rows):
    """demo/D: DAU = 5 (u1..u5); interstitial 3/5 = 0.6 (gồm 1 impression Full2 — impression thật);
    rewarded 1/5 (ts 00:30 +07); fullscreen
    1/5 (bản trùng đã bỏ; `imp_id` rỗng không dùng làm khoá). Ngày 2026-09-29 không có ad_show."""
    got = _by(am.imp_per_dau_format(_deduped(fixture_rows)), "app", "day", "ad_format")
    assert set(got) == {("demo", D, "interstitial"), ("demo", D, "rewarded"), ("demo", D, "fullscreen")}
    assert got[("demo", D, "interstitial")]["dau"] == 5
    assert got[("demo", D, "interstitial")]["imp_per_dau"] == pytest.approx(0.6)
    assert got[("demo", D, "rewarded")]["impressions"] == 1
    assert got[("demo", D, "fullscreen")]["imp_per_dau"] == pytest.approx(0.2)


def test_arpdau_ads_theo_nuoc(fixture_rows):
    """demo/D mỗi nước 1 DAU: US 0.003 (+1 unconverted GBP), DE 0.002, VN 0.0044, DEFAULT (OTHER)
    0.0015, FR 0 (meta 0 bị loại). demo/2026-09-29: US DAU 1, doanh thu 0."""
    got = _by(am.arpdau_ads_country(_deduped(fixture_rows), am.load_country_tiers(), FX),
              "app", "day", "country")
    assert len(got) == 7  # 5 nước ngày D + US ngày trước + US app other
    us = got[("demo", D, "US")]
    assert (us["tier"], us["dau"], us["unconverted"]) == ("T1", 1, 1)
    assert us["arpdau_usd"] == pytest.approx(0.003)
    assert got[("demo", D, "VN")]["arpdau_usd"] == pytest.approx(0.0044)
    assert got[("demo", D, "DEFAULT")]["tier"] == "OTHER"
    assert got[("demo", D, "FR")]["revenue_usd"] == 0
    assert got[("demo", "2026-09-29", "US")]["arpdau_usd"] == 0


def test_fill_rate_dem_orphan(fixture_rows):
    """interstitial: rA fill, rB no_fill (timeout), rC không kết thúc -> 3 request, 1 fill,
    1 no_fill, 1 orphan, fill 1/3. rewarded: rD (đã bỏ bản trùng) fill -> 1/1."""
    got = _by(am.fill_rate(_deduped(fixture_rows)), "app", "day", "ad_format")
    assert set(got) == {("demo", D, "interstitial"), ("demo", D, "rewarded")}
    i = got[("demo", D, "interstitial")]
    assert (i["requests"], i["fills"], i["no_fills"], i["orphans"]) == (3, 1, 1, 1)
    assert i["fill_rate"] == pytest.approx(1 / 3)
    r = got[("demo", D, "rewarded")]
    assert (r["requests"], r["fills"], r["orphans"], r["fill_rate"]) == (1, 1, 0, 1.0)


def test_show_rate_chi_tinh_ready_1_va_nhan_sample_rate(fixture_rows):
    """interstitial: 3 attempt (2 ready) / 2 show -> 1.0 — `ad_show` của Full2 (`full_2_default`) bị
    loại vì Full2 không phát `ad_show_attempt` (R-2 m4); rewarded 1/1; fullscreen: 1 attempt ready
    với sample_rate 0.5 -> trọng số 2, 1 show -> 0.5."""
    got = _by(am.show_rate(_deduped(fixture_rows)), "app", "day", "ad_format")
    assert len(got) == 3
    i = got[("demo", D, "interstitial")]
    assert (i["attempts"], i["attempts_ready"], i["shows"], i["show_rate"]) == (3, 2, 2, 1.0)
    fs = got[("demo", D, "fullscreen")]
    assert (fs["attempts_ready"], fs["shows"]) == (2, 1)
    assert fs["show_rate"] == pytest.approx(0.5)


def test_consent_funnel_khong_co_cot_rate(fixture_rows):
    """initial: 1 form, 1 choice GRANTED, latency 4200; privacy_options: 1 form, 1 PARTIAL, GPP
    '2_7', latency 1800. Không có cột nào tên chứa 'rate' (tỉ lệ đồng ý đọc từ AdMob P&M)."""
    got = _by(am.consent_funnel_nondenied(_deduped(fixture_rows)), "app", "day", "form_kind")
    assert set(got) == {("demo", D, "initial"), ("demo", D, "privacy_options")}
    ini = got[("demo", D, "initial")]
    assert (ini["forms_shown"], ini["choices"], ini["granted"], ini["partial"]) == (1, 1, 1, 0)
    assert ini["avg_latency_ms"] == pytest.approx(4200)
    po = got[("demo", D, "privacy_options")]
    assert (po["partial"], po["with_gpp"], po["max_latency_ms"]) == (1, 1, 1800)
    assert not any("rate" in k for k in ini)


def test_retention_d1_d7_theo_nhom_holdout(fixture_rows):
    """Nhóm như `metrics.compare_holdout`: u1 treatment (cohort 20260929, D1 có), u2 holdout,
    u3 mâu thuẫn -> 'conflicting', u4/u5 chưa có quyết định -> 'unassigned' (u4 cohort 20260923
    có D7). App other: u1 unassigned, D1 có."""
    got = _by(am.retention_d1_d7_holdout(_deduped(fixture_rows)), "app", "install_day", "holdout_group")
    assert len(got) == 6
    assert got[("demo", 20260929, "treatment")]["d1_rate"] == 1.0
    assert got[("demo", 20260929, "holdout")]["d1_users"] == 0
    assert got[("demo", 20260930, "conflicting")]["cohort_users"] == 1
    u4 = got[("demo", 20260923, "unassigned")]
    assert (u4["cohort_users"], u4["d1_users"], u4["d7_users"]) == (1, 0, 1)
    assert got[("demo", 20260930, "unassigned")]["cohort_users"] == 1
    assert got[("other", 20260929, "unassigned")]["d1_users"] == 1


def test_hang_schema_v1_khong_vao_view_nao(fixture_rows):
    """Hàng v1 (ad_revenue 5.0 USD của u1, không envelope) không làm đổi bất kỳ view nào."""
    from test_iap_v2 import iap_fixture_rows  # 1.8.0 T12: hai view IAP/paywall cần hàng IAP để không rỗng

    rows = _deduped(fixture_rows + iap_fixture_rows() + show_rate_views_rows())
    v2_only = [r for r in rows if r.get("schema_version") == 2]
    assert len(v2_only) == len(rows) - 1
    tiers = am.load_country_tiers()
    assert set(am.VIEWS) == {
        "v_ecpm_format_tier", "v_imp_per_dau_format", "v_arpdau_ads_country", "v_fill_rate",
        "v_show_rate", "v_consent_funnel_nondenied", "v_retention_d1_d7_holdout",
        "v_arpdau_country", "v_paywall_funnel", "v_brain_decisions", "v_ecpm_network_format",
        "v_show_rate_reasons", "v_show_rate_views"}
    for name, fn in am.VIEWS.items():
        assert fn(rows, tiers, FX) == fn(v2_only, tiers, FX), name
        assert fn(rows, tiers, FX), f"{name} rỗng trên fixture"


# ── 1.9.0 T9: v_brain_decisions, v_ecpm_network_format, nhánh holdout của v_paywall_funnel, cột `slot` ──
_TS = 1790737201000  # 2026-09-30 10:00:01 +07
_DAY_MS = 86_400_000
_DECLINED = "show_full_brain_declined"


def _v2(name, seq, **cols):
    return {"event_name": name, "schema_version": 2, "app_id": "demo", "client_ts_ms": _TS + seq * 1000,
            "dedup_key": f"ev:{name}:{seq}", **cols}


def test_V1_brain_decisions_tren_fixture(fixture_rows):
    """`v_brain_decisions` có trong `VIEWS`; demo/D interstitial explore p1: is_holdout 0 = d1 + d3 = 2, is_holdout 1 =
    d2 + d4 = 2; declined_app_day = 1 (`ad_show_fail` u1 reason brain từ chối, grain app × ngày, D38) lặp trên cả hai
    hàng của ngày."""
    assert "v_brain_decisions" in am.VIEWS
    got = _by(am.VIEWS["v_brain_decisions"](_deduped(fixture_rows), am.load_country_tiers(), FX),
              "app", "day", "decision_point", "chosen_action_mode", "is_holdout", "policy_version")
    assert set(got) == {("demo", D, "interstitial", "explore", 0, "p1"),
                        ("demo", D, "interstitial", "explore", 1, "p1")}
    for r in got.values():
        assert (r["decisions"], r["declined_app_day"]) == (2, 1), r


def test_V2_brain_decisions_declined_theo_app_ngay_va_trong_so():
    """decisions nhân 1/sample_rate (0.5 -> 2); declined chỉ reason brain từ chối (reason khác bỏ), nối theo
    (app, ngày): ngày D+1 chỉ có declined -> không hàng; declined của app `other` không vào hàng `demo`."""
    rows = [_v2("brain_decision", 1, decision_point="rewarded", chosen_action_mode="exploit", is_holdout=0,
                policy_version="calib-x", sample_rate=0.5),
            _v2("ad_show_fail", 2, reason=_DECLINED), _v2("ad_show_fail", 3, reason=_DECLINED),
            _v2("ad_show_fail", 4, reason="not_ready"),
            {**_v2("ad_show_fail", 5, reason=_DECLINED), "client_ts_ms": _TS + _DAY_MS},
            {**_v2("ad_show_fail", 6, reason=_DECLINED), "app_id": "other"}]
    got = am.VIEWS["v_brain_decisions"](rows, am.load_country_tiers(), {})
    assert got == [{"app": "demo", "day": D, "decision_point": "rewarded", "chosen_action_mode": "exploit",
                    "is_holdout": 0, "policy_version": "calib-x", "decisions": 2.0, "declined_app_day": 2.0}]


def test_V3_ecpm_network_format_tren_fixture(fixture_rows):
    """ad_network = nhãn mạng (chữ thường): demo/D admob interstitial = (3000+2000+1500) µ$ -> 0.0065 USD / 3 × 1000
    = 2.1667; rewarded 4000 µEUR × 1.1 = 4.4 (meta_direct 0 bị loại: không có hàng `meta`); fullscreen GBP không tỉ giá
    -> doanh thu 0, eCPM None; app other 3.0."""
    got = _by(am.VIEWS["v_ecpm_network_format"](_deduped(fixture_rows), am.load_country_tiers(), FX),
              "app", "day", "ad_network", "ad_format")
    assert set(got) == {("demo", D, "admob", "interstitial"), ("demo", D, "admob", "rewarded"),
                        ("demo", D, "admob", "fullscreen"), ("other", D, "admob", "interstitial")}
    i = got[("demo", D, "admob", "interstitial")]
    assert i["impressions"] == 3 and i["revenue_usd"] == pytest.approx(0.0065)
    assert i["ecpm_usd"] == pytest.approx(6.5 / 3)
    assert got[("demo", D, "admob", "rewarded")]["ecpm_usd"] == pytest.approx(4.4)
    fs = got[("demo", D, "admob", "fullscreen")]
    assert (fs["impressions"], fs["revenue_usd"], fs["ecpm_usd"]) == (1, 0, None)
    assert got[("other", D, "admob", "interstitial")]["ecpm_usd"] == pytest.approx(3.0)


def test_V4_ad_network_coalesce_khop_normalize():
    """normalize giữ nhãn như SDK gửi (D35: không đổi hoa/thường lúc lưu): `ad_network` có thì dùng, không thì
    `ad_source`; view gộp theo chữ thường của COALESCE đó — oracle và SQL cùng một biểu thức."""
    from pathlib import Path

    from app.models import EventRequest
    from app.normalize import normalize

    def rev(seq, **d):
        body = {"sequenceId": seq, "eventName": "ad_revenue", "ts": _TS, "userProperty": {"user_id": "u"},
                "eventData": {"ad_format": "banner", "value_micros": 1000, "currency": "USD", **d}}
        return {**normalize(EventRequest.model_validate(body)), "schema_version": 2, "app_id": "demo"}

    rows = [rev(1, ad_network="Pangle", ad_source="x"), rev(2, ad_source="AdMob"), rev(3, ad_source="admob"), rev(4)]
    assert [r["ad_network"] for r in rows] == ["Pangle", "AdMob", "admob", ""]
    got = _by(am.VIEWS["v_ecpm_network_format"](rows, am.load_country_tiers(), {}), "ad_network")
    assert {k[0]: v["impressions"] for k, v in got.items()} == {"pangle": 1, "admob": 2, "": 1}
    sql = (Path(am.__file__).resolve().parents[1] / "sql" / "views" / "v_ecpm_network_format.sql").read_text()
    assert "LOWER(COALESCE(NULLIF(ad_network, ''), NULLIF(ad_source, ''), ''))" in sql


def test_V5_paywall_funnel_co_nhanh_holdout(fixture_rows):
    """u5 bị giữ (paywall_holdout_skip shop) rồi mua 1.99 USD ngoài paywall: hàng shop/holdout = 1 phiên, 0 lượt hiện,
    1 lượt mua, 1.99, chuyển đổi 1.0; nhánh treatment A/B không đổi (như B4)."""
    from test_iap_v2 import iap_fixture_rows

    got = _by(am.VIEWS["v_paywall_funnel"](_deduped(fixture_rows + iap_fixture_rows()), am.load_country_tiers(), FX),
              "app", "day", "placement", "variant_id")
    assert set(got) == {("demo", D, "shop", "A"), ("demo", D, "shop", "B"), ("demo", D, "shop", "holdout")}
    h = got[("demo", D, "shop", "holdout")]
    assert (h["impressions"], h["sessions"], h["choices"], h["closes"], h["purchases"]) == (0, 1, 0, 0, 1), h
    assert h["revenue_usd"] == pytest.approx(1.99) and h["conversion_rate"] == 1.0 and h["unconverted"] == 0
    a = got[("demo", D, "shop", "A")]
    assert (a["impressions"], a["sessions"], a["choices"], a["closes"], a["purchases"]) == (5, 4, 3, 1, 2), a


def test_V6_holdout_chi_tinh_luot_mua_ngoai_phien_cua_nguoi_bi_giu():
    """h1 bị giữ ở shop (sample_rate 0.5 -> 2 phiên): mua ngoài phiên 1 USD -> tính; mua có `paywall_session_id`
    -> không; n1 không bị giữ -> không; h1 mua ngày D+1 -> hàng D+1 (0 phiên, chuyển đổi None)."""
    def buy(seq, user, **c):
        return _v2("iap_purchase", seq, user_id=user, price_micros=1_000_000, currency_code="USD", phase="inapp",
                   **c)

    rows = [_v2("paywall_holdout_skip", 1, user_id="h1", placement="shop", sample_rate=0.5),
            buy(2, "h1"), buy(3, "h1", paywall_session_id="sX"), buy(4, "n1"),
            {**buy(5, "h1"), "client_ts_ms": _TS + _DAY_MS}]
    got = _by(am.VIEWS["v_paywall_funnel"](rows, am.load_country_tiers(), {}), "day", "placement", "variant_id")
    assert set(got) == {(D, "shop", "holdout"), ("2026-10-01", "shop", "holdout")}
    d0, d1 = got[(D, "shop", "holdout")], got[("2026-10-01", "shop", "holdout")]
    assert (d0["sessions"], d0["purchases"], d0["revenue_usd"], d0["conversion_rate"]) == (2, 1, 1.0, 0.5), d0
    assert (d1["sessions"], d1["purchases"], d1["conversion_rate"]) == (0, 1, None), d1
    # Biến thể thật tên `holdout` (lượt hiện) là hàng riêng, không gộp vào nhánh holdout (như UNION ALL của view).
    real = _v2("paywall_impression", 6, user_id="r1", placement="shop", variant_id="holdout", paywall_session_id="sR")
    both = [r for r in am.VIEWS["v_paywall_funnel"](rows + [real], am.load_country_tiers(), {})
            if (r["day"], r["placement"], r["variant_id"]) == (D, "shop", "holdout")]
    assert sorted((r["impressions"], r["sessions"]) for r in both) == [(0, 2), (1, 1)], both


def test_S1_cot_slot_duoc_chuan_hoa(fixture_rows):
    """T15 carry: `slot` (khoá slot mạng của request, `AdEventLog.requestData`) thành cột STRING add-only, không chỉ
    nằm trong `raw_event_data`; event không gửi `slot` không có giá trị bịa."""
    import json
    from pathlib import Path

    from app.models import EventRequest
    from app.normalize import normalize

    schema = json.loads((Path(am.__file__).resolve().parents[1] / "schema" / "events_raw.json").read_text())
    assert {"name": "slot", "type": "STRING"} == {k: v for c in schema if c["name"] == "slot" for k, v in c.items()
                                                  if k in ("name", "type")}
    body = {"sequenceId": 1, "eventName": "ad_request", "ts": _TS, "userProperty": {"user_id": "u"},
            "eventData": {"ad_format": "banner", "placement": "bn_home", "slot": "bn_home_slot"}}
    assert normalize(EventRequest.model_validate(body)).get("slot") == "bn_home_slot"
    ra = next(r for r in fixture_rows if r["event_name"] == "ad_request" and r.get("request_id") == "rA")
    assert ra["slot"] == "p"
    assert all(r.get("slot") is None for r in fixture_rows if r["event_name"] == "ad_revenue")


# ── 2.1.0 BE1: v_show_rate_reasons (tách lý do chặn; `v_show_rate` giữ nguyên schema) ──
_COLS = ("blocked_policy", "blocked_brain", "blocked_guard", "blocked_entitlement", "not_ready", "config", "other")


def _reasons(rows):
    return am.VIEWS["v_show_rate_reasons"](am.dedup(rows), am.load_country_tiers(), {})


def _show(seq, fmt="rewarded_interstitial", placement="rwi_default", **cols):
    return _v2("ad_show", seq, ad_format=fmt, placement=placement, **cols)


def _attempt(seq, ready=1, fmt="rewarded_interstitial", placement="rwi_default"):
    return _v2("ad_show_attempt", seq, ad_format=fmt, placement=placement, ready=ready)


def _fail(seq, reason, fmt="rewarded_interstitial", placement="rwi_default"):
    return _v2("ad_show_fail", seq, ad_format=fmt, placement=placement, reason=reason)


def test_B1_organic_show_rate_loai_chan_policy():
    """D34 rwi: 10 show + 8 `policy_min_gap` + 9 `rwi_intro_skipped` (27 attempt ready), 5 `show_not_loaded` (ready 0).
    Mẫu số = 27 - 17 = 10 -> organic_show_rate 1.0 (chia attempts_ready thô: 10/27); `v_show_rate` không có cột này."""
    rows = ([_attempt(i) for i in range(1, 11)] + [_show(100 + i) for i in range(10)]
            + [_attempt(200 + i) for i in range(17)]
            + [_fail(300 + i, "policy_min_gap") for i in range(8)]
            + [_fail(400 + i, "rwi_intro_skipped") for i in range(9)]
            + [_attempt(500 + i, ready=0) for i in range(5)] + [_fail(600 + i, "show_not_loaded") for i in range(5)])
    (got,) = _reasons(rows)
    assert (got["attempts_ready"], got["shows"], got["blocked_policy"], got["not_ready"]) == (27, 10, 17, 5)
    assert got["organic_show_rate"] == 1.0
    assert "organic_show_rate" not in am.show_rate(am.dedup(rows))[0]


def test_B2_moi_lop_reason_vao_dung_cot():
    """Bảng §F: policy / brain / guard / entitlement / not_ready / config (cả `not_plm_cf_%`); mỗi reason đúng 1 cột."""
    want = {"blocked_policy": ("policy_min_gap", "rwi_intro_skipped"), "blocked_brain": ("show_full_brain_declined",),
            "blocked_guard": ("ad_showing", "ad_full_showing", "show_full_when_flag_showing", "open_flag_resume"),
            "blocked_entitlement": ("remove_ad", "show_full_when_remove_ads"), "not_ready": ("show_not_loaded",),
            "config": ("not_contain_placement", "not_plm_cf_fb_full_1", "not_plm_cf_fb_reward_1")}
    seq = iter(range(1, 100))
    rows = [_fail(next(seq), reason) for reasons in want.values() for reason in reasons]
    (got,) = _reasons(rows)
    for col in _COLS:
        assert got[col] == len(want.get(col, ())), (col, got)


def test_B3_reason_la_hoac_rong_vao_other():
    (got,) = _reasons([_fail(1, "reason_moi_chua_biet"), _v2("ad_show_fail", 2, ad_format="rewarded_interstitial"),
                       _fail(3, "ad_showing")])
    assert (got["other"], got["blocked_guard"]) == (2, 1)
    assert sum(got[c] for c in _COLS) == 3


def test_B4_full2_bi_loai_nhu_v_show_rate():
    """Placement `full_2_*` (show + fail) không vào; cùng luật `v_show_rate`."""
    rows = [_show(1, "interstitial", "full_2_default"), _fail(2, "ad_showing", "interstitial", "full_2_default"),
            _attempt(3, 1, "interstitial", "full_1"), _show(4, "interstitial", "full_1")]
    (got,) = _reasons(rows)
    assert (got["ad_format"], got["attempts_ready"], got["shows"], got["blocked_guard"]) == ("interstitial", 1, 1, 0)
    assert got["organic_show_rate"] == 1.0


def test_B5_sql_va_oracle_cung_cot_cung_reason():
    """Parity tĩnh (không emulator; parity động nằm ở `test_views_parity`): 13 view có file SQL, cột (`AS`) của SQL
    = khoá hàng oracle, mọi reason oracle phân lớp có trong SQL đúng lớp."""
    import re
    from pathlib import Path

    views = Path(__file__).resolve().parents[1] / "sql" / "views"
    assert {p.stem for p in views.glob("v_*.sql")} - {"v_events_v2"} == set(am.VIEWS) and len(am.VIEWS) == 13
    sql = (views / "v_show_rate_reasons.sql").read_text(encoding="utf-8")
    sql_cols = {"app", "day", *re.findall(r"\bAS (\w+)", sql)} - {"rc"}  # `rc` = cột trung gian, không ra ngoài
    (row,) = _reasons([_attempt(1), _show(2)])
    assert sql_cols == set(row), (sorted(sql_cols), sorted(row))
    whens = [ln for ln in sql.splitlines() if ln.strip().startswith("WHEN")]
    for col, reasons in srr.REASONS.items():
        for reason in reasons.split():
            assert [ln for ln in whens if f"'{reason}'" in ln and f"THEN '{col}'" in ln], (reason, col)
    assert "'not_plm_cf_'" in sql


# ── 2.2.0 BE3: v_show_rate_views (mẫu số theo phiên view), nhãn fail theo attempt, cột kill/harness ──
def _views(rows):
    return am.VIEWS["v_show_rate_views"](am.dedup(rows), am.load_country_tiers(), {})


def _sess(row, session="s1"):
    return {**row, "session_id": session}


def test_B6_view_show_rate_theo_phien_view_tren_sink_emu37():
    """Sink thật emu37: banner 34 attempt ready / 6 show / 6 khoá (session, placement) -> view_show_rate 1.0
    (cũ: `v_show_rate` banner 6/34 = 0.18, view mới không có); native_collapse 2 khoá, 2 show -> 1.0."""
    rows = show_rate_views_rows()
    got = {r["ad_format"]: r for r in _views(rows)}
    assert set(got) == {"banner", "native_collapse"}
    assert (got["banner"]["views"], got["banner"]["views_shown"], got["banner"]["view_show_rate"]) == (6, 6, 1.0)
    assert (got["native_collapse"]["views"], got["native_collapse"]["view_show_rate"]) == (2, 1.0)
    old = {r["ad_format"]: r for r in am.VIEWS["v_show_rate"](am.dedup(rows), am.load_country_tiers(), {})}
    assert (old["banner"]["attempts_ready"], old["banner"]["shows"]) == (34, 6)


def test_B6b_khoa_phien_view_khong_dung_screen_name_va_chi_dem_attempt_ready():
    """Một khoá = (session_id, placement): nhiều attempt ready cùng khoá = 1 view; khoá chỉ có attempt ready=0 không
    vào mẫu số; khoá có show nhưng không có attempt ready không vào; hai phiên = hai view."""
    rows = [_sess(_attempt(1, 1, "banner", "bn_default"), "a"), _sess(_attempt(2, 1, "banner", "bn_default"), "a"),
            _sess(_show(3, "banner", "bn_default"), "a"),
            _sess(_attempt(4, 0, "banner", "bn_default"), "b"),
            _sess(_attempt(5, 1, "banner", "bn_default"), "c"),
            _sess(_show(6, "banner", "bn_other"), "d"),
            _sess(_attempt(7, 1, "interstitial", "full_1"), "e")]
    (got,) = _views(rows)
    assert (got["ad_format"], got["views"], got["views_shown"], got["view_show_rate"]) == ("banner", 2, 1, 0.5)


def test_B7_fail_fullscreen_lay_nhan_attempt_gan_nhat():
    """`ad_show_fail` nhãn `fullscreen` sau attempt `interstitial` cùng (session, placement) -> cột interstitial
    (cũ: hàng fullscreen); attempt SAU fail không được dùng."""
    rows = [_sess(_attempt(1, 1, "interstitial", "full_1")),
            _sess(_fail(2, "policy_min_gap", "fullscreen", "full_1")),
            _sess(_attempt(3, 1, "rewarded", "full_1"))]
    got = {r["ad_format"]: r for r in _reasons(rows)}
    assert set(got) == {"interstitial", "rewarded"}
    assert got["interstitial"]["blocked_policy"] == 1 and got["rewarded"]["blocked_policy"] == 0


def test_B8_kill_switch_vao_blocked_kill():
    (got,) = _reasons([_fail(1, "show_blocked_kill_switch")])
    assert (got["blocked_kill"], got["other"]) == (1, 0)


def test_B9_show_when_act_null_vao_harness():
    (got,) = _reasons([_fail(1, "show_when_act_null")])
    assert (got["harness"], got["other"]) == (1, 0)


def test_B10_seg_guardrail_vao_blocked_policy():
    (got,) = _reasons([_fail(1, "seg_guardrail")])
    assert (got["blocked_policy"], got["other"]) == (1, 0)


def test_B11_parity_hai_chieu_sql_oracle_reason_va_view():
    """Hai chiều (B5 chỉ đi oracle -> SQL): mọi reason trong `WHEN` của SQL phải có trong bảng oracle đúng lớp, cột
    `not_plm_cf_` theo tiền tố ở cả hai bên, và mỗi file view có hàm oracle (13)."""
    import re
    from pathlib import Path

    views = Path(__file__).resolve().parents[1] / "sql" / "views"
    sql = (views / "v_show_rate_reasons.sql").read_text(encoding="utf-8")
    oracle = {x: c for c, xs in srr.REASONS.items() for x in xs.split()}
    for ln in (x for x in sql.splitlines() if x.strip().startswith("WHEN") and " THEN '" in x):
        col = re.search(r"THEN '(\w+)'", ln).group(1)
        for reason in re.findall(r"'([a-z_0-9]+)'", ln.split(" THEN ")[0]):
            if reason != "not_plm_cf_":
                assert oracle.get(reason) == col, (reason, col, oracle.get(reason))
    assert "STARTS_WITH(reason, 'not_plm_cf_')" in sql and srr.column_of("ad_show_fail", "not_plm_cf_x", "p") == "config"
    assert {p.stem for p in views.glob("v_*.sql")} - {"v_events_v2"} == set(am.VIEWS) and len(am.VIEWS) == 13
    aliases = set(re.findall(r"\bAS (\w+)", (views / "v_show_rate_views.sql").read_text(encoding="utf-8")))
    helpers = {"w", "shown", "sid", "pl"}  # cột trung gian của truy vấn con
    assert {"app", "day", "ad_format"} | aliases - helpers == set(_views(show_rate_views_rows())[0])


def test_B13_fail_khong_co_attempt_giu_nhan():
    """`not_contain_placement` lúc parse (không attempt) giữ `fullscreen`; attempt khác placement không cho mượn nhãn."""
    rows = [_sess(_attempt(1, 1, "interstitial", "full_x")), _sess(_fail(2, "not_contain_placement", "fullscreen", "full_y"))]
    got = {r["ad_format"]: r for r in _reasons(rows)}
    assert got["fullscreen"]["config"] == 1 and got["interstitial"]["config"] == 0


def test_B14_organic_tru_blocked_kill_khong_tru_harness():
    """D21-220: 4 attempt ready, 2 show; 1 bị kill switch chặn -> mẫu số 3 (2/3); thêm 1 `show_when_act_null` (harness)
    không đổi mẫu số (vẫn 2/3, harness = lỗi hiển thị thật); thiếu kill thì 2/4."""
    base = [_attempt(i) for i in range(1, 5)] + [_show(10), _show(11)]
    assert _reasons(base)[0]["organic_show_rate"] == 0.5
    (got,) = _reasons(base + [_fail(20, "show_blocked_kill_switch")])
    assert got["organic_show_rate"] == 2 / 3
    (got,) = _reasons(base + [_fail(20, "show_blocked_kill_switch"), _fail(21, "show_when_act_null")])
    assert (got["organic_show_rate"], got["harness"]) == (2 / 3, 1)
