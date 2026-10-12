"""1.8.0 T12 — cột IAP/paywall v2 (§Hợp đồng E), khử trùng `iap_purchase` theo `purchase_token` (D5/D13/D15),
`metrics.paywall_funnel` nhân `1/sample_rate` (D7), oracle của `v_arpdau_country` + `v_paywall_funnel`.

`iap_fixture_rows()` là fixture IAP/paywall dùng chung với `test_views_parity.py` (B8) và `test_ad_metrics.py`:
app `demo`, ngày địa phương UTC+7 2026-09-30, 4 người (p1 US, p2 DE, p3 VN, p4 US); mọi số kỳ vọng tính tay
ở docstring từng test.
"""

from __future__ import annotations

import json
from pathlib import Path

from app import ad_metrics as am
from app import metrics
from app.models import EventRequest
from app.normalize import ingest_row, normalize

D = "2026-09-30"
FX = {(D, "EUR"): 1.1}
_TS = 1790737201000  # 2026-09-30 03:00:01Z = 10:00:01 +07
_SCHEMA = Path(__file__).resolve().parent.parent / "schema" / "events_raw.json"
_USERS = {"p1": "US", "p2": "DE", "p3": "VN", "p4": "US"}


def _req(name, data, **top):
    body = {"sequenceId": 5, "eventName": name, "eventData": data, "ts": 1790000000000,
            "userProperty": {"user_id": "u_1", "country": "US"}}
    body.update(top)
    return EventRequest.model_validate(body)


def _row(seq, user, name, data, received_after_ms=500):
    """Hàng đúng như `/push` ghi (envelope v2, `event_id` riêng mỗi hàng)."""
    body = {"consent_tier": "GRANTED", "eventName": name, "eventData": data,
            "event_id": f"00000000-0000-4000-8000-1800000000{seq:02d}", "package_name": "test.sdk.zd",
            "schema_version": 2, "sdk_version": "1.8.0", "sequenceId": seq, "ts": _TS + seq * 1000,
            "userProperty": {"app_name": "demo", "country": _USERS[user], "platform": "android",
                             "user_id": user}}
    return ingest_row(EventRequest.model_validate(body), "demo", _TS + seq * 1000 + received_after_ms)


def _imp(seq, user, sid, variant, placement="shop"):
    data = {"placement": placement, "variant_id": variant, "product_count": 2, "paywall_version": "v2",
            "schema_version_cfg": 2}
    if sid:
        data["paywall_session_id"] = sid
    return _row(seq, user, "paywall_impression", data)


def _choice(seq, user, sid, variant, product):
    return _row(seq, user, "paywall_choice", {"paywall_session_id": sid, "placement": "shop",
                                              "variant_id": variant, "product_id": product})


def _buy(seq, user, token, order, micros, currency, phase, sid="", received_after_ms=500):
    return _row(seq, user, "iap_purchase", {
        "product_id": "pro", "order_id": order, "price_micros": micros, "currency_code": currency,
        "quantity": 1, "channel": "GOOGLE_PLAY", "is_subscription": 1, "purchase_token": token,
        "phase": phase, "offer_tag": "", "paywall_session_id": sid, "placement": "shop" if sid else "",
        "variant_id": ""}, received_after_ms)


def iap_fixture_rows() -> list[dict]:
    """18 hàng -> 17 sau khử trùng (hàng 6 = bản phát lại D15 của hàng 5: cùng token, `order_id` rỗng,
    `event_id` mới, tới sau 1 ngày)."""
    return [
        _imp(1, "p1", "s1", "A"), _imp(2, "p1", "s1", "A"),          # một phiên, hai lượt hiện
        _choice(3, "p1", "s1", "A", "pro"),
        _row(4, "p1", "iap_result", {"outcome": "success", "response_code": 0, "product_id": "pro",
                                     "paywall_session_id": "s1"}),
        _buy(5, "p1", "tokA", "GPA.A", 4_990_000, "USD", "base", "s1"),
        _buy(6, "p1", "tokA", "", 4_990_000, "USD", "base", "s1", received_after_ms=86_400_000),
        _imp(7, "p2", "s2", "B"), _choice(8, "p2", "s2", "B", "pro_year"),
        _buy(9, "p2", "tokB", "GPA.B", 1_990_000, "EUR", "intro", "s2"),
        _imp(10, "p2", "", "B"),                                      # SDK cũ: không có phiên
        _imp(11, "p3", "s3", "A"),                                    # đóng không chọn
        _imp(12, "p3", "s3b", "A"), _choice(13, "p3", "s3b", "A", "pro"),
        _row(14, "p3", "iap_result", {"outcome": "cancel", "response_code": 1, "product_id": "pro",
                                      "paywall_session_id": "s3b"}),
        _buy(15, "p3", "tokE", "GPA.E", 25_000_000_000, "VND", "inapp"),  # ngoài paywall, không tỉ giá
        _imp(16, "p4", "s4", "A"), _choice(17, "p4", "s4", "A", "pro"),
        _buy(18, "p4", "tokD", "GPA.D", 4_990_000, "USD", "trial", "s4"),  # trial báo nhầm giá niêm yết
    ]


def _by(rows, *keys):
    return {tuple(r[k] for k in keys): r for r in rows}


def test_B1_iap_purchase_v2_columns():
    row = normalize(_req("iap_purchase", {"product_id": "p", "order_id": "o", "price_micros": 0, "currency_code": "USD",
                                          "purchase_token": "tok", "phase": "trial", "offer_tag": "t",
                                          "paywall_session_id": "s", "placement": "shop", "variant_id": "A"}))
    missing = [k for k in ("phase", "offer_tag", "paywall_session_id") if k not in row]
    assert not missing, f"cot thieu {missing}"
    assert (row["phase"], row["offer_tag"], row["paywall_session_id"], row["placement"], row["variant_id"]) == (
        "trial", "t", "s", "shop", "A"), row
    assert row["price_micros"] == 0 and "usd" not in row, row  # D3: không có `usd` phía client
    names = {c["name"] for c in json.loads(_SCHEMA.read_text(encoding="utf-8"))}
    assert {"phase", "offer_tag", "paywall_session_id"} <= names


def test_B2_dedup_theo_purchase_token():
    """D5/D13/D15: khoá = token; bản phát lại (restore, pending->paid, cold start) có thể đổi hoặc mất
    `order_id` và mang `event_id` mới, nhưng giữ token. Không token (web shop) -> `order_id` như cũ."""
    row = normalize(_req("iap_purchase", {"order_id": "GPA.1", "purchase_token": "tok"}))
    assert row["dedup_key"] == "iap_purchase:tok:tok", row["dedup_key"]
    replay = normalize(_req("iap_purchase", {"order_id": "", "purchase_token": "tok"}, event_id="e-2",
                            sequenceId=9))
    assert replay["dedup_key"] == row["dedup_key"]
    assert normalize(_req("iap_purchase", {"order_id": "GPA.1"}))["dedup_key"] == "iap_purchase:GPA.1"
    assert normalize(_req("iap_purchase", {"order_id": "GPA.1", "purchase_token": ""}))[
        "dedup_key"] == "iap_purchase:GPA.1"


def test_B3_arpdau_country_gom_iap_trial_bang_0(fixture_rows):
    """Chỉ IAP — US: dau 2 (p1, p4), IAP 4.99 (hàng phát lại của tokA gộp; trial p4 = 0 dù báo 4.99), 2 lượt
    mua; DE: 1.99 EUR × 1.1 = 2.189; VN: 25 000 VND không tỉ giá -> `iap_unconverted` 1, doanh thu 0.
    QC + IAP: vế quảng cáo = `v_arpdau_ads_country` từng (app, ngày, nước), tổng = QC + IAP."""
    tiers = am.load_country_tiers()
    got = _by(am.VIEWS["v_arpdau_country"](am.dedup(iap_fixture_rows()), tiers, FX), "app", "day", "country")
    assert set(got) == {("demo", D, "US"), ("demo", D, "DE"), ("demo", D, "VN")}
    us, de, vn = got[("demo", D, "US")], got[("demo", D, "DE")], got[("demo", D, "VN")]
    assert (us["dau"], us["iap_purchases"], us["iap_unconverted"], us["tier"]) == (2, 2, 0, "T1")
    assert abs(us["iap_revenue_usd"] - 4.99) < 1e-9, us
    assert abs(us["arpdau_usd"] - 2.495) < 1e-9 and us["ad_revenue_usd"] == 0, us
    assert abs(de["iap_revenue_usd"] - 2.189) < 1e-9 and abs(de["revenue_usd"] - 2.189) < 1e-9, de
    assert (vn["iap_revenue_usd"], vn["iap_unconverted"], vn["arpdau_usd"]) == (0, 1, 0), vn

    rows = am.dedup(fixture_rows + iap_fixture_rows())
    ads = _by(am.arpdau_ads_country(rows, tiers, FX), "app", "day", "country")
    both = _by(am.VIEWS["v_arpdau_country"](rows, tiers, FX), "app", "day", "country")
    assert set(both) == set(ads)
    for k, r in both.items():
        assert abs(r["ad_revenue_usd"] - ads[k]["revenue_usd"]) < 1e-9, k
        assert r["ad_unconverted"] == ads[k]["unconverted"] and r["dau"] == ads[k]["dau"], k
        assert abs(r["revenue_usd"] - r["ad_revenue_usd"] - r["iap_revenue_usd"]) < 1e-9, k
    assert abs(both[("demo", D, "US")]["iap_revenue_usd"] - 4.99) < 1e-9


def test_B4_paywall_funnel_theo_phien():
    """shop/A: 5 lượt hiện, 4 phiên (s1 hiện 2 lần), chọn 3 (s1, s3b, s4), đóng 1 (s3), mua 2 (s1, s4 trial),
    doanh thu 4.99; shop/B: 2 phiên (s2 + lượt hiện SDK cũ không phiên), chọn 1, mua 1, 2.189."""
    got = _by(am.VIEWS["v_paywall_funnel"](am.dedup(iap_fixture_rows()), am.load_country_tiers(), FX),
              "app", "day", "placement", "variant_id")
    assert set(got) == {("demo", D, "shop", "A"), ("demo", D, "shop", "B")}
    a, b = got[("demo", D, "shop", "A")], got[("demo", D, "shop", "B")]
    assert (a["impressions"], a["sessions"], a["choices"], a["closes"], a["purchases"]) == (5, 4, 3, 1, 2), a
    assert abs(a["revenue_usd"] - 4.99) < 1e-9 and a["conversion_rate"] == 0.5 and a["unconverted"] == 0, a
    assert (b["impressions"], b["sessions"], b["choices"], b["closes"], b["purchases"]) == (2, 2, 1, 1, 1), b
    assert abs(b["revenue_usd"] - 2.189) < 1e-9 and b["conversion_rate"] == 0.5, b


def test_B5_paywall_funnel_nhan_sample_rate():
    """D7: event đếm nhân 1/sample_rate như view quảng cáo; số người không nhân."""
    def rows(sr):
        r = []
        for i in range(10):
            d = {"event_name": "paywall_impression", "user_id": f"u{i}", "placement": "shop", "variant_id": "a"}
            if sr:
                d["sample_rate"] = sr
            r.append(d)
        return r
    got = metrics.paywall_funnel(rows(0.5))["placements"]["shop"]["a"]["treatment"]
    assert got["events"] == 20, "sample_rate bi bo qua"
    assert got["users"] == 10
    assert metrics.paywall_funnel(rows(None))["placements"]["shop"]["a"]["treatment"]["events"] == 10
    for bad in (0, 1.5, -1, "0.5"):
        assert metrics.paywall_funnel(rows(bad))["placements"]["shop"]["a"]["treatment"]["events"] == 10, bad


def test_B5b_paywall_funnel_tien_nhan_sample_rate():
    """R-12 m4: tiền (đã quy đổi và chưa quy đổi) của lượt mua có `sample_rate` nhân 1/sample_rate như số event.
    u0 thấy paywall rồi mua 4.99 USD với sample_rate 0.5 -> 9.98; u1 mua bằng VND (không tỉ giá) -> 2 event."""
    rows = [{"event_name": "paywall_impression", "user_id": u, "placement": "shop", "variant_id": "a"}
            for u in ("u0", "u1")]
    rows += [{"event_name": "iap_purchase", "user_id": "u0", "price_micros": 4_990_000, "currency_code": "USD",
              "sample_rate": 0.5},
             {"event_name": "iap_purchase", "user_id": "u1", "price_micros": 25_000_000_000, "currency_code": "VND",
              "sample_rate": 0.5}]
    got = metrics.paywall_funnel(rows)["placements"]["shop"]["a"]["treatment"]
    assert abs(got["revenue_usd"] - 9.98) < 1e-9, got
    assert got["unconverted_events"] == 2, got
    assert (got["users"], got["buyers"]) == (2, 2), got


def test_B5c_paywall_funnel_holdout_nhan_sample_rate():
    """R-12 m4: lượt bỏ qua của nhóm holdout nhân 1/sample_rate (10 lượt, sample_rate 0.5 -> 20); số người không."""
    rows = [{"event_name": "paywall_holdout_skip", "user_id": f"h{i}", "placement": "shop",
             "counterfactual_variant_id": "a", "sample_rate": 0.5} for i in range(10)]
    got = metrics.paywall_funnel(rows)["placements"]["shop"]["a"]["holdout"]
    assert (got["events"], got["users"]) == (20, 10), got


def test_B6_iap_result_va_paywall_v2_luu_cot():
    row = normalize(_req("iap_result", {"outcome": "cancel", "response_code": 1, "product_id": "p",
                                        "paywall_session_id": "s"}))
    assert row.get("outcome") == "cancel", f"outcome {row.get('outcome')}"
    assert (row["response_code"], row["product_id"], row["paywall_session_id"]) == (1, "p", "s"), row
    choice = normalize(_req("paywall_choice", {"paywall_session_id": "s", "placement": "shop", "variant_id": "A",
                                               "product_id": "p"}))
    assert (choice["paywall_session_id"], choice["placement"], choice["variant_id"], choice["product_id"]) == (
        "s", "shop", "A", "p"), choice
    skip = normalize(_req("paywall_skip", {"placement": "shop", "reason": "session_cap"}))
    assert (skip["placement"], skip["reason"]) == ("shop", "session_cap"), skip
    imp = normalize(_req("paywall_impression", {"placement": "shop", "variant_id": "A", "paywall_session_id": "s",
                                                "schema_version_cfg": 2}))
    assert (imp["paywall_session_id"], imp["schema_version_cfg"]) == ("s", 2), imp
    hold = normalize(_req("paywall_holdout_skip", {"placement": "shop", "paywall_session_id": "s"}))
    assert hold["paywall_session_id"] == "s", hold
    sub = normalize(_req("subscription_state", {"product_id": "p", "from_state": "ACTIVE", "to_state": "LAPSED",
                                                "source": "server"}))
    assert sub["source"] == "server", sub
    names = {c["name"] for c in json.loads(_SCHEMA.read_text(encoding="utf-8"))}
    for r in (row, choice, skip, imp, hold, sub):
        assert sorted(set(r) - names) == [], r["event_name"]
    for name in ("iap_result", "paywall_skip", "paywall_choice"):
        assert ingest_row(_req(name, {}), "demo", 1)["is_known_event"] is True, name
