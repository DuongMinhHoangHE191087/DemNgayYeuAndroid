"""Test phễu paywall — nửa phân tích của holdout native (doc 49 mục E.2/E.3).

Trọng tâm là những chỗ phép đo dễ nói dối một cách im lặng:

- nhóm đối chứng rỗng thì KHÔNG được cho ra "lift vô hạn";
- `counterfactual_variant_id` phải ghép đúng biến thể — ghép sai là so sánh A với B';
- chuyển đổi đo cấp USER vì impression không mang product_id — không được gán bừa;
- tiền chưa quy đổi tỉ giá không được âm thầm biến mất.
"""

from __future__ import annotations

import importlib
import json

import pytest
from fastapi.testclient import TestClient


def row(**kw):
    base = {"client_ts_date": "2026-08-25", "user_id": "u1", "event_name": "paywall_impression"}
    base.update(kw)
    return base


def impression(user, placement="shop", variant="control"):
    return row(user_id=user, event_name="paywall_impression",
               placement=placement, variant_id=variant,
               product_count=2, paywall_version="pw_v1")


def skip(user, placement="shop", cf_variant="control"):
    return row(user_id=user, event_name="paywall_holdout_skip",
               placement=placement, counterfactual_variant_id=cf_variant,
               paywall_version="pw_v1")


def iap(user, price_micros=4_990_000, currency="USD"):
    return row(user_id=user, event_name="iap_purchase",
               price_micros=price_micros, currency_code=currency,
               channel="PLAY_STORE", is_subscription=0)


# ═══ Ingestion: hai event mới phải được nhận và giữ đúng trường ═══

@pytest.fixture()
def client(tmp_path, monkeypatch):
    monkeypatch.setenv("BRAIN_SINK", "jsonl")
    monkeypatch.setenv("BRAIN_JSONL_PATH", str(tmp_path / "events.jsonl"))
    monkeypatch.delenv("BRAIN_API_KEY", raising=False)
    import app.config
    import app.main

    importlib.reload(app.config)
    importlib.reload(app.main)
    return TestClient(app.main.app), tmp_path / "events.jsonl"


def envelope(event_name: str, event_data: dict) -> dict:
    # Đúng hình dạng SDK gửi: camelCase tầng ngoài, snake_case trong userProperty.
    return {
        "sequenceId": 1,
        "eventName": event_name,
        "eventData": event_data,
        "ts": 1724500000000,
        "userProperty": {"user_id": "u_1", "platform": "android"},
    }


def rows_in(path):
    return [json.loads(l) for l in path.read_text().splitlines() if l.strip()]


def test_ingestion_nhan_va_phang_hai_event_paywall(client):
    c, path = client
    c.post("/push", json=envelope("paywall_impression", {
        "placement": "shop", "variant_id": "control", "product_count": 2,
        "paywall_version": "pw_v1", "screen_name": "shop_screen",
    }))
    c.post("/push", json=envelope("paywall_holdout_skip", {
        "placement": "shop", "counterfactual_variant_id": "reordered",
        "paywall_version": "pw_v1", "screen_name": "shop_screen",
    }))
    rows = rows_in(path)
    assert all(r["is_known_event"] for r in rows), "event paywall phai duoc coi la KNOWN"
    imp, skp = rows
    assert imp["variant_id"] == "control"
    assert imp["placement"] == "shop"
    assert imp["product_count"] == 2
    assert skp["counterfactual_variant_id"] == "reordered"
    # Không có khoá ổn định (một người có thể thấy paywall nhiều lần thật) -> khoá `seq:`
    # theo sequenceId, không theo nội dung (1.7.0 T9).
    assert imp["dedup_key"] == "seq:u_1:1:1724500000000"
    assert skp["dedup_key"] == "seq:u_1:1:1724500000000"


# ═══ Phễu: câu hỏi 1 — biến thể nào bán ═══

def test_phung_theo_bien_the_va_chuyen_doi_cap_user():
    from app import metrics
    rows = [
        impression("u1", variant="control"),
        impression("u1", variant="control"),   # cùng người, nhiều lượt hiện
        impression("u2", variant="control"),
        impression("u3", variant="reordered"),
        iap("u1"),                              # u1 mua -> control có 1 buyer
    ]
    r = metrics.paywall_funnel(rows)
    c = r["placements"]["shop"]["control"]["treatment"]
    assert c["users"] == 2
    assert c["events"] == 3
    assert c["buyers"] == 1
    assert c["conversion_rate"] == pytest.approx(0.5)
    assert c["revenue_usd"] == pytest.approx(4.99)
    rr = r["placements"]["shop"]["reordered"]["treatment"]
    assert rr["buyers"] == 0
    assert rr["conversion_rate"] == 0.0


def test_user_mua_nhieu_placement_xuat_hien_trong_moi_nhom():
    # Overlap thật của phép đo — không được tự ý khử.
    from app import metrics
    rows = [impression("u1", placement="shop"), impression("u1", placement="no_ads"), iap("u1")]
    r = metrics.paywall_funnel(rows)
    assert r["placements"]["shop"]["control"]["treatment"]["buyers"] == 1
    assert r["placements"]["no_ads"]["control"]["treatment"]["buyers"] == 1


# ═══ Phễu: câu hỏi 2 — có paywall so với KHÔNG có paywall ═══

def test_counterfactual_ghep_dung_bien_the():
    # Người holdout của biến thể nào phải so với người ĐÃ THẤY biến thể đó — ghép sai là
    # so sánh A với B', kết luận vô nghĩa.
    from app import metrics
    rows = (
        [impression(f"t{i}", variant="a") for i in range(10)] +    # 10 người thấy 'a'
        [skip(f"h{i}", cf_variant="a") for i in range(5)] +        # 5 người bị giữ khỏi 'a'
        [impression(f"t2_{i}", variant="b") for i in range(4)] +
        [skip(f"h2_{i}", cf_variant="b") for i in range(3)]
    )
    r = metrics.paywall_funnel(rows)
    a = r["placements"]["shop"]["a"]
    b = r["placements"]["shop"]["b"]
    assert a["treatment"]["users"] == 10 and a["holdout"]["users"] == 5
    assert b["treatment"]["users"] == 4 and b["holdout"]["users"] == 3
    assert a["holdout"]["events"] == 5


def test_lift_am_khi_holdout_mua_nhieu_hon():
    from app import metrics
    rows = (
        [impression(f"t{i}") for i in range(4)] +
        [skip(f"h{i}") for i in range(4)] +
        [iap("t0")] + [iap("h0"), iap("h1")]
    )
    v = metrics.paywall_funnel(rows)["placements"]["shop"]["control"]
    assert v["treatment"]["conversion_rate"] == pytest.approx(0.25)
    assert v["holdout"]["conversion_rate"] == pytest.approx(0.5)
    assert v["lift_pp"] == pytest.approx(-25.0)


# ═══ Những chỗ dễ nói dối ═══

def test_nhom_holdout_rong_khong_cho_ra_lift_vo_han():
    from app import metrics
    r = metrics.paywall_funnel([impression("u1"), iap("u1")])
    v = r["placements"]["shop"]["control"]
    assert v["holdout"]["users"] == 0
    assert v["lift_pp"] is None, "khong duoc chia cho nhom holdout rong"
    assert r["sufficient_data"] is False
    assert "RỖNG" in r["warning"]


def test_mau_qua_nho_phai_tu_canh_bao():
    from app import metrics
    rows = [impression("t1"), skip("h1"), iap("t1")]
    r = metrics.paywall_funnel(rows)
    assert r["sufficient_data"] is False
    assert "nhiễu" in r["warning"]


def test_mau_du_lon_thi_khong_canh_bao():
    from app import metrics
    n = metrics.MIN_USERS_PER_GROUP
    rows = ([impression(f"t{i}") for i in range(n)] +
            [skip(f"h{i}") for i in range(n)])
    r = metrics.paywall_funnel(rows)
    assert r["sufficient_data"] is True
    assert r["warning"] is None


def test_user_vua_treatment_vua_holdout_la_mau_thuan_phai_bao_len():
    # Hash tất định nên điều này không được phép xảy ra; thấy nó là muối băm bị đổi giữa
    # chừng hoặc user_id bị dùng lại.
    from app import metrics
    r = metrics.paywall_funnel([impression("u1"), skip("u1")])
    assert r["conflicting_users"] == 1


def test_tien_chua_quy_doi_khong_am_thang_bien_mat():
    from app import metrics
    rows = [impression("u1"), iap("u1", price_micros=99_000_000_000, currency="VND")]
    v = metrics.paywall_funnel(rows)["placements"]["shop"]["control"]["treatment"]
    assert v["buyers"] == 1, "nguoi mua van phai duoc dem vao tu so"
    assert v["revenue_usd"] == 0.0
    assert v["unconverted_events"] == 1, "tien chua quy doi phai duoc bao cao rieng"


def test_quy_doi_dung_khi_co_ti_gia():
    from app import metrics
    rows = [impression("u1"), iap("u1", price_micros=1_000_000, currency="EUR")]
    v = metrics.paywall_funnel(rows, fx={"eur": 1.08})["placements"]["shop"]["control"]["treatment"]
    assert v["revenue_usd"] == pytest.approx(1.08)


def test_rong_hoan_toan_khong_crash():
    from app import metrics
    r = metrics.paywall_funnel([])
    assert r["placements"] == {}
    assert r["sufficient_data"] is False


# ═══ Vòng khép kín qua endpoint ═══

def test_endpoint_paywall_qua_vong_khep_kin(client):
    c, _ = client
    for i in range(3):
        env = envelope("paywall_impression", {
            "placement": "shop", "variant_id": "control", "product_count": 2,
            "paywall_version": "pw_v1",
        })
        env["userProperty"]["user_id"] = f"t{i}"
        c.post("/push", json=env)
    env = envelope("paywall_holdout_skip", {
        "placement": "shop", "counterfactual_variant_id": "control",
        "paywall_version": "pw_v1",
    })
    env["userProperty"]["user_id"] = "h0"
    c.post("/push", json=env)
    env = envelope("iap_purchase", {
        "product_id": "coin_small", "order_id": "GPA.1", "price_micros": 4_990_000,
        "currency_code": "USD", "quantity": 1, "channel": "PLAY_STORE", "is_subscription": 0,
    })
    env["userProperty"]["user_id"] = "t0"
    c.post("/push", json=env)

    m = c.get("/metrics/paywall").json()
    v = m["placements"]["shop"]["control"]
    assert v["treatment"]["users"] == 3
    assert v["treatment"]["buyers"] == 1
    assert v["holdout"]["users"] == 1
    assert m["total_holdout_users"] == 1
    assert m["window"] == {"date_from": None, "date_to": None}


def test_endpoint_paywall_cung_bi_kiem_tra_api_key(tmp_path, monkeypatch):
    monkeypatch.setenv("BRAIN_SINK", "jsonl")
    monkeypatch.setenv("BRAIN_JSONL_PATH", str(tmp_path / "e.jsonl"))
    monkeypatch.setenv("BRAIN_API_KEY", "secret")
    import app.config
    import app.main

    importlib.reload(app.config)
    importlib.reload(app.main)
    c = TestClient(app.main.app)
    assert c.get("/metrics/paywall").status_code == 401
