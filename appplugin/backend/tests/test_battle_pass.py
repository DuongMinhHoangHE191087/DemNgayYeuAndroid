"""Test ingestion battle pass (doc 23 mục C.3, doc 19 góc nhìn gaming).

Hai event — `battle_pass_tier` (tiến độ) và `battle_pass_claim` (chuyển đổi) —
phải được nhận, flatten đúng trường, và KHÔNG đặt dedup_key: SDK chỉ phát
khi bậc thật sự tăng / mỗi claim một lần, dedup nằm ở nguồn.
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


def test_ingestion_nhan_va_phang_battle_pass_tier(client):
    c, path = client
    c.post("/push", json=envelope("battle_pass_tier", {
        "season_id": "season_3", "from_tier": 4, "to_tier": 6,
        "is_premium": 1, "battle_pass_version": "bp_v2", "screen_name": "home",
    }))
    rows = [r for r in rows_in(path) if r["event_name"] == "battle_pass_tier"]
    assert len(rows) == 1
    r = rows[0]
    assert r["season_id"] == "season_3"
    assert r["from_tier"] == 4
    assert r["to_tier"] == 6
    assert r["is_premium"] == 1
    assert r["battle_pass_version"] == "bp_v2"
    assert r.get("raw_event_data") is None


def test_ingestion_nhan_va_phang_battle_pass_claim(client):
    c, path = client
    c.post("/push", json=envelope("battle_pass_claim", {
        "season_id": "season_3", "tier": 7, "track": "PREMIUM",
        "reward_count": 2, "is_premium": 1, "battle_pass_version": "bp_v2",
    }))
    rows = [r for r in rows_in(path) if r["event_name"] == "battle_pass_claim"]
    assert len(rows) == 1
    r = rows[0]
    assert r["season_id"] == "season_3"
    assert r["tier"] == 7
    assert r["track"] == "PREMIUM"
    assert r["reward_count"] == 2
    assert r["is_premium"] == 1


def test_hai_mua_lien_tiep_khong_bi_gop_lam_trung(client):
    # Cùng một người, cùng một bậc 1, hai mùa khác nhau — cả hai là sự kiện thật
    # (người chơi mới của mùa nào cũng bắt đầu từ bậc 1). Không dedup theo (user, tier).
    c, path = client
    for season in ("season_1", "season_2"):
        c.post("/push", json=envelope("battle_pass_claim", {
            "season_id": season, "tier": 1, "track": "FREE",
            "reward_count": 1, "is_premium": 0, "battle_pass_version": "bp_v1",
        }))
    rows = [r for r in rows_in(path) if r["event_name"] == "battle_pass_claim"]
    assert len(rows) == 2
    assert {r["season_id"] for r in rows} == {"season_1", "season_2"}


def test_funnel_premium_do_duoc_tu_claim(client):
    # Câu hỏi kinh doanh: bao nhiêu người MUA pass rồi claim track premium?
    # Tử số là claim PREMIUM, mẫu số là entitlement — cả hai phải tách được theo mùa.
    c, path = client
    c.post("/push", json=envelope("battle_pass_claim", {
        "season_id": "s1", "tier": 1, "track": "PREMIUM",
        "reward_count": 1, "is_premium": 1, "battle_pass_version": "v",
    }))
    c.post("/push", json=envelope("battle_pass_claim", {
        "season_id": "s1", "tier": 1, "track": "FREE",
        "reward_count": 1, "is_premium": 0, "battle_pass_version": "v",
    }))
    rows = [r for r in rows_in(path) if r["event_name"] == "battle_pass_claim"]
    premium_claims = [r for r in rows if r["track"] == "PREMIUM"]
    assert len(premium_claims) == 1
    assert premium_claims[0]["is_premium"] == 1
