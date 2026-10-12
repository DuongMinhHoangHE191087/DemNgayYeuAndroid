"""Test đối soát giao dịch IAP — biên nhận dùng lại (doc 45 mục E.1, `purchase_reconcile.py`).

Dedup ingestion khoá theo order_id KHÔNG kèm user_id (doc 46 mục C.2) — cố ý để trùng cùng
một người (replay sau kill-app) được bỏ qua, còn biên nhận xuyên tài khoản thì lớp đối soát
này phải bắt được. Subscription gia hạn là ngoại lệ hợp lệ duy nhất của token-nhiều-đơn.
"""

from __future__ import annotations

import importlib

import pytest
from fastapi.testclient import TestClient

from app import purchase_reconcile


def purchase(user, order, token="", channel="PLAY_STORE", is_sub=0):
    return {
        "event_name": "iap_purchase",
        "user_id": user,
        "order_id": order,
        "purchase_token": token,
        "channel": channel,
        "is_subscription": is_sub,
        "product_id": "p",
        "price_micros": 1990000,
        "currency_code": "USD",
        "quantity": 1,
    }


def test_du_lieu_sach_verdict_clean():
    rows = [
        purchase("u_1", "GPA.1", token="tok_a"),
        purchase("u_2", "GPA.2", token="tok_b"),
        purchase("u_3", "WEB-1", token="", channel="WEB_SHOP"),
    ]
    result = purchase_reconcile.compute(rows)
    assert result["verdict"] == "clean"
    assert result["suspect_counts"] == {
        "token_across_users": 0,
        "order_across_users": 0,
        "token_across_orders_not_subscription": 0,
    }


def test_token_xuyen_tai_khoan_la_nghi_van():
    rows = [
        purchase("u_1", "GPA.1", token="tok_chia_se"),
        purchase("u_2", "GPA.9", token="tok_chia_se"),
    ]
    result = purchase_reconcile.compute(rows)
    assert result["verdict"] == "suspects_found"
    s = result["suspects"]["token_across_users"][0]
    assert s["purchase_token"] == "tok_chia_se"
    assert s["user_ids"] == ["u_1", "u_2"]


def test_order_xuyen_tai_khoan_phu_ca_web_shop():
    rows = [
        purchase("u_1", "WEB-77", channel="WEB_SHOP"),
        purchase("u_2", "WEB-77", channel="WEB_SHOP"),
    ]
    result = purchase_reconcile.compute(rows)
    assert result["suspect_counts"]["order_across_users"] == 1
    assert result["suspects"]["order_across_users"][0]["order_id"] == "WEB-77"


def test_subscription_gia_han_token_nhieu_don_la_hop_le():
    # Play gia hạn định kỳ: token giữ nguyên, order_id mới mỗi chu kỳ. KHÔNG được báo nghi vấn.
    rows = [
        purchase("u_1", "GPA.3333-1", token="tok_sub", is_sub=1),
        purchase("u_1", "GPA.3333-2", token="tok_sub", is_sub=1),
    ]
    result = purchase_reconcile.compute(rows)
    assert result["suspect_counts"]["token_across_orders_not_subscription"] == 0
    assert result["verdict"] == "clean"


def test_token_nhieu_don_ngoai_subscription_la_nghi_van():
    rows = [
        purchase("u_1", "GPA.1", token="tok_x"),
        purchase("u_1", "GPA.2", token="tok_x"),
        # Cùng token nhưng của subscription thì không đổi kết luận phi-subscription.
        purchase("u_1", "GPA.3", token="tok_x", is_sub=1),
    ]
    result = purchase_reconcile.compute(rows)
    assert result["suspect_counts"]["token_across_orders_not_subscription"] == 1
    s = result["suspects"]["token_across_orders_not_subscription"][0]
    assert s["order_count"] == 3


def test_hang_play_thieu_token_la_do_phu_khong_phai_nghi_van():
    rows = [purchase("u_1", "GPA.1", token="")]  # bản build SDK cũ chưa gửi token
    result = purchase_reconcile.compute(rows)
    assert result["play_rows_missing_token"] == 1
    assert result["verdict"] == "clean"


def test_khong_co_du_lieu():
    result = purchase_reconcile.compute([{"event_name": "brain_decision"}])
    assert result["verdict"] == "no_data"
    assert result["total_purchase_rows"] == 0


# ---------------------------------------------------------------------------
# Endpoint — cùng fixture pattern với test_reconcile.
# ---------------------------------------------------------------------------

@pytest.fixture()
def client(tmp_path, monkeypatch):
    monkeypatch.setenv("BRAIN_SINK", "jsonl")
    monkeypatch.setenv("BRAIN_JSONL_PATH", str(tmp_path / "events.jsonl"))
    monkeypatch.delenv("BRAIN_API_KEY", raising=False)
    import app.config
    import app.main

    importlib.reload(app.config)
    importlib.reload(app.main)
    return TestClient(app.main.app)


def envelope(user, order, token, channel="PLAY_STORE"):
    return {
        "sequenceId": 1,
        "eventName": "iap_purchase",
        "eventData": {
            "product_id": "remove_ads", "order_id": order, "price_micros": 1990000,
            "currency_code": "USD", "quantity": 1, "channel": channel,
            "is_subscription": 0, "purchase_token": token,
        },
        "ts": 1724500000000,
        "userProperty": {"user_id": user, "platform": "android"},
    }


def test_endpoint_doi_soat_giao_dich(client):
    client.post("/push", json=envelope("u_1", "GPA.1", "tok_a"))
    client.post("/push", json=envelope("u_2", "GPA.2", "tok_a"))  # token xuyên tài khoản
    resp = client.get("/reconcile/purchases")
    assert resp.status_code == 200
    body = resp.json()
    assert body["verdict"] == "suspects_found"
    assert body["suspect_counts"]["token_across_users"] == 1
    assert body["window"] == {"date_from": None, "date_to": None}
