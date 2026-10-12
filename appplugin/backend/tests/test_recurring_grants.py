"""Test ingestion cầu nối VIP ↔ virtual currency (doc 19, doc 23 mục C.3).

`recurring_grant_claim` đo tần suất dùng đặc quyền — tín hiệu GIỮ subscription
mạnh hơn cả lý do mua. Không dedup_key: SDK chặn trùng theo chu kỳ ở nguồn,
và cùng một dòng phát hôm nay và tháng sau đều là sự kiện thật.
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


def claim(user, product="vip_pass", reward="gem", amount=500, hours=24):
    return envelope("recurring_grant_claim", {
        "product_id": product, "reward_type": reward, "reward_amount": amount,
        "period_hours": hours, "recurring_grants_version": "rg_v1",
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


def test_ingestion_nhan_va_phang_recurring_grant_claim(client):
    c, path = client
    c.post("/push", json=claim("u_1"))
    rows = [r for r in rows_in(path) if r["event_name"] == "recurring_grant_claim"]
    assert len(rows) == 1
    r = rows[0]
    assert r["product_id"] == "vip_pass"
    assert r["reward_type"] == "gem"
    assert r["reward_amount"] == 500
    assert r["period_hours"] == 24
    assert r["recurring_grants_version"] == "rg_v1"
    assert r.get("raw_event_data") is None


def test_hai_ngay_lien_tiep_la_hai_su_kien_that(client):
    # Người VIP nhận gem hôm nay và ngày mai — cả hai là hành vi thật cần đếm
    # (đo retention đặc quyền). Không dedup theo (user, product, reward_type).
    c, path = client
    c.post("/push", json=claim("u_1"))
    c.post("/push", json=claim("u_1"))
    rows = [r for r in rows_in(path) if r["event_name"] == "recurring_grant_claim"]
    assert len(rows) == 2


def test_hai_dong_phat_khac_chu_ky_tach_duoc(client):
    # Cùng một sản phẩm VIP có gem ngày và vé tuần — phân tích phải tách được
    # chu kỳ để đo dòng nào kéo retention.
    c, path = client
    c.post("/push", json=claim("u_1", reward="gem", amount=500, hours=24))
    c.post("/push", json=claim("u_1", reward="ticket", amount=1, hours=168))
    rows = [r for r in rows_in(path) if r["event_name"] == "recurring_grant_claim"]
    assert {(r["reward_type"], r["period_hours"]) for r in rows} == {("gem", 24), ("ticket", 168)}
