"""Test ingestion của phễu routing đa kênh (doc 50).

`web_checkout_opened` là tử số của phễu: người được dẫn ra web shop. Backend không cần
endpoint phân tích mới — `compute()` đã có revenue_by_channel khoá trên cột `channel`, nên
doanh thu WEB_SHOP từ `iap_purchase` chảy vào tự động. Ở đây chỉ cần đảm bảo ingestion
NHẬN event, giữ đúng trường, và không để lộ thứ SDK cố ý không gửi (URL checkout).
"""

from __future__ import annotations

import importlib
import json

import pytest
from fastapi.testclient import TestClient


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
        "userProperty": {"user_id": "u_1", "platform": "android", "country": "US"},
    }


def rows_in(path):
    return [json.loads(l) for l in path.read_text().splitlines() if l.strip()]


def test_ingestion_nhan_web_checkout_opened(client):
    c, path = client
    c.post("/push", json=envelope("web_checkout_opened", {
        "product_id": "coin_pack_1",
        "channel": "WEB_SHOP",
        "routing_version": "routing_v1",
        "screen_name": "shop_screen",
    }))
    rows = rows_in(path)
    assert len(rows) == 1
    r = rows[0]
    assert r["is_known_event"], "web_checkout_opened phai duoc coi la KNOWN"
    assert r["product_id"] == "coin_pack_1"
    assert r["channel"] == "WEB_SHOP"
    assert r["routing_version"] == "routing_v1"
    # Một người có thể mở checkout nhiều lần thật (rồi quay lại) — không có khoá ổn định:
    # khoá `seq:` theo sequenceId, không theo nội dung (1.7.0 T9).
    assert r["dedup_key"] == "seq:u_1:1:1724500000000"


def test_ingestion_khong_luu_url_checkout(client):
    # SDK cố ý không gửi URL (có thể nhúng session token); nếu một client nào đó vẫn gửi,
    # ingestion không được chép nó vào bất kỳ cột nào.
    c, path = client
    c.post("/push", json=envelope("web_checkout_opened", {
        "product_id": "coin_pack_1",
        "channel": "WEB_SHOP",
        "routing_version": "routing_v1",
        "url": "https://shop.example.com/checkout?token=SECRET",
    }))
    r = rows_in(path)[0]
    assert "SECRET" not in json.dumps(r), "URL checkout khong duoc roi vao kho log"


def test_iap_purchase_kenh_web_shop_van_day_du(client):
    # Vế còn lại của phễu: xác nhận phía server qua grantFromServer -> iap_purchase.
    c, path = client
    c.post("/push", json=envelope("iap_purchase", {
        "product_id": "coin_pack_1",
        "order_id": "WS-ORDER-1",
        "price_micros": 4_990_000,
        "currency_code": "USD",
        "quantity": 1,
        "channel": "WEB_SHOP",
        "is_subscription": False,
    }))
    r = rows_in(path)[0]
    assert r["channel"] == "WEB_SHOP"
    assert r["price_micros"] == 4_990_000
    # Giao dịch có khoá chống trùng là order_id — webhook retry phải an toàn.
    assert r["dedup_key"] == "iap_purchase:WS-ORDER-1"


def test_doanh_thu_web_shop_chay_vao_revenue_by_channel():
    from app import metrics
    rows = [
        {"event_name": "iap_purchase", "client_ts_date": "2026-08-25", "user_id": "u1",
         "price_micros": 4_990_000, "currency_code": "USD", "channel": "WEB_SHOP"},
        {"event_name": "iap_purchase", "client_ts_date": "2026-08-25", "user_id": "u2",
         "price_micros": 9_990_000, "currency_code": "USD", "channel": "PLAY_STORE"},
    ]
    r = metrics.compute(rows)
    by_channel = r["revenue"]["by_channel_usd"]
    assert by_channel["WEB_SHOP"] == pytest.approx(4.99)
    assert by_channel["PLAY_STORE"] == pytest.approx(9.99)
