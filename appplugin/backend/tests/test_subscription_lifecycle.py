"""Test ingestion vòng đời subscription (doc 23 mục C.3, doc 19 B.7/B.8).

Một event `subscription_state` với cặp from/to phủ toàn bộ taxonomy doc 19.
Phép đo dễ nói dối ở đây là dedup: SDK chỉ phát khi trạng thái thật sự đổi,
nên backend KHÔNG được đặt dedup_key theo product_id — cùng cặp chuyển đổi
xảy ra lại sau nhiều tháng là chuyển đổi thật, không phải bản ghi trùng.
"""

from __future__ import annotations

import importlib
import json

import pytest
from fastapi.testclient import TestClient


def envelope(event_name: str, event_data: dict, user: str = "u_1") -> dict:
    # Đúng hình dạng SDK gửi: camelCase tầng ngoài, snake_case trong userProperty.
    return {
        "sequenceId": 1,
        "eventName": event_name,
        "eventData": event_data,
        "ts": 1724500000000,
        "userProperty": {"user_id": user, "platform": "android"},
    }


def transition(user, from_state, to_state, product_id="vip_monthly"):
    return envelope("subscription_state", {
        "product_id": product_id, "from_state": from_state, "to_state": to_state,
        "lifecycle_version": "sub_lifecycle_v1", "screen_name": "home",
    }, user=user)


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


def rows_in(path):
    return [json.loads(l) for l in path.read_text().splitlines() if l.strip()]


# ═══ Ingestion ═══

def test_ingestion_nhan_va_phang_event_subscription_state(client):
    c, path = client
    c.post("/push", json=transition("u_1", "NONE", "ACTIVE"))
    rows = [r for r in rows_in(path) if r["event_name"] == "subscription_state"]
    assert len(rows) == 1
    r = rows[0]
    assert r["product_id"] == "vip_monthly"
    assert r["from_state"] == "NONE"
    assert r["to_state"] == "ACTIVE"
    assert r["lifecycle_version"] == "sub_lifecycle_v1"
    # raw_event_data không được giữ khi đã có nhánh flatten riêng.
    assert r.get("raw_event_data") is None


def test_cap_chuyen_doi_grace_va_hoi_phuc_deu_luu_du(client):
    c, path = client
    # Vào grace → hồi phục thanh toán: hai chuyển đổi, hai hàng riêng.
    c.post("/push", json=transition("u_1", "ACTIVE", "IN_GRACE_PERIOD"))
    c.post("/push", json=transition("u_1", "IN_GRACE_PERIOD", "ACTIVE"))
    rows = [r for r in rows_in(path) if r["event_name"] == "subscription_state"]
    pairs = [(r["from_state"], r["to_state"]) for r in rows]
    assert ("ACTIVE", "IN_GRACE_PERIOD") in pairs
    assert ("IN_GRACE_PERIOD", "ACTIVE") in pairs


def test_hai_lan_churn_cung_mot_san_pham_khong_bi_gop_lam_trung(client):
    # SDK dedup ở nguồn (chỉ phát khi trạng thái đổi), nên backend KHÔNG đặt
    # dedup_key: cùng một người có thể ACTIVE→LAPSED rồi mua lại rồi lại churn
    # sau nhiều tháng — mỗi lần là chuyển đổi thật, không phải bản ghi trùng.
    c, path = client
    c.post("/push", json=transition("u_1", "CANCELLED", "LAPSED"))
    c.post("/push", json=transition("u_1", "LAPSED", "ACTIVE"))   # win-back
    c.post("/push", json=transition("u_1", "ACTIVE", "LAPSED"))   # churn lần hai
    rows = [r for r in rows_in(path) if r["event_name"] == "subscription_state"]
    assert len(rows) == 3
    lapseds = [r for r in rows if r["to_state"] == "LAPSED"]
    assert len(lapseds) == 2


def test_event_chua_biet_van_va_raw_va_van_khong_mat(client):
    # Phòng thủ: nếu SDK đời cũ gửi trường lạ trong event đã biết, flatten nhánh
    # riêng không được nuốt mất dữ liệu — ở đây kiểm tra event hoàn toàn lạ.
    c, path = client
    c.post("/push", json=envelope("event_ma_tuong_lai", {"a": 1}))
    rows = rows_in(path)
    assert any(r["event_name"] == "event_ma_tuong_lai" and r.get("raw_event_data")
               for r in rows)
