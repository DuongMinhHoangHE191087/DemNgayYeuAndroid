"""Test ingestion offerwall — lớp adapter đa vendor (doc 20, doc 23 mục C.4).

`offerwall_impression` là mẫu số (lượt mở tường), `offerwall_completed` là tử số
(offer được xác nhận). Ghép hai event này mới đo được tỉ lệ chuyển đổi theo từng
provider — thứ quyết định nên giữ vendor nào khi fill rate chỉ 5–15%.
"""

from __future__ import annotations

import importlib
import json

import pytest
from fastapi.testclient import TestClient


def envelope(event_name: str, event_data: dict, user: str = "u_1") -> dict:
    return {
        "sequenceId": 1,
        "eventName": event_name,
        "eventData": event_data,
        "ts": 1724500000000,
        "userProperty": {"user_id": user, "platform": "android"},
    }


def impression(user, provider="revu", version="ow_v1"):
    return envelope("offerwall_impression", {
        "provider_id": provider, "offerwall_version": version,
        "screen_name": "home",
    }, user=user)


def completed(user, provider="revu", currency=120, tx="tx_001", version="ow_v1"):
    return envelope("offerwall_completed", {
        "provider_id": provider, "currency_earned": currency,
        "transaction_id": tx, "offerwall_version": version,
        "screen_name": "home",
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


def test_ingestion_nhan_va_phang_offerwall_impression(client):
    c, path = client
    c.post("/push", json=impression("u_1"))
    rows = [r for r in rows_in(path) if r["event_name"] == "offerwall_impression"]
    assert len(rows) == 1
    r = rows[0]
    assert r["provider_id"] == "revu"
    assert r["offerwall_version"] == "ow_v1"
    assert r.get("raw_event_data") is None
    assert r["is_known_event"] is True


def test_completion_phang_va_co_dedup_key(client):
    c, path = client
    c.post("/push", json=completed("u_1"))
    rows = [r for r in rows_in(path) if r["event_name"] == "offerwall_completed"]
    assert len(rows) == 1
    r = rows[0]
    assert r["provider_id"] == "revu"
    assert r["currency_earned"] == 120
    assert r["transaction_id"] == "tx_001"
    assert r["offerwall_version"] == "ow_v1"
    # SDK chặn trùng ở nguồn, nhưng hàng đợi replay sau kill-app cần lớp dedup thứ hai.
    assert r["dedup_key"] == "offerwall_completed:revu:tx_001"


def test_impression_khong_co_khoa_tu_nhien_dedup_theo_seq(client):
    # Mở tường nhiều lần là hành vi thật — không có id ổn định nào để làm khoá
    # mà không gộp nhầm hai lượt mở thật sự. 1.7.0 T9: khoá `seq:` theo sequenceId SDK gắn
    # lúc dựng, nên hai lượt mở thật (hai sequenceId) vẫn là hai khoá.
    c, path = client
    c.post("/push", json=impression("u_1"))
    c.post("/push", json={**impression("u_1"), "sequenceId": 2})
    rows = [r for r in rows_in(path) if r["event_name"] == "offerwall_impression"]
    assert len(rows) == 2
    assert [r["dedup_key"] for r in rows] == ["seq:u_1:1:1724500000000", "seq:u_1:2:1724500000000"]


def test_completion_khong_co_tx_thi_roi_ve_seq(client):
    # Không có transaction_id thì không bịa khoá từ nội dung: rơi về `seq:` (1.7.0 T9).
    c, path = client
    c.post("/push", json=completed("u_1", tx=""))
    rows = [r for r in rows_in(path) if r["event_name"] == "offerwall_completed"]
    assert len(rows) == 1
    assert rows[0]["dedup_key"] == "seq:u_1:1:1724500000000"


def test_cung_tx_o_hai_provider_la_hai_giao_dich_khac(client):
    # transaction id chỉ duy nhất TRONG MỘT vendor — khoá phải ghép cả provider,
    # nếu không BigQuery MERGE sẽ gộp nhầm giao dịch của adjoe vào revu.
    c, path = client
    c.post("/push", json=completed("u_1", provider="revu", tx="tx_001"))
    c.post("/push", json=completed("u_1", provider="adjoe", tx="tx_001"))
    rows = [r for r in rows_in(path) if r["event_name"] == "offerwall_completed"]
    keys = {r["dedup_key"] for r in rows}
    assert keys == {"offerwall_completed:revu:tx_001", "offerwall_completed:adjoe:tx_001"}
