"""Test ingestion experiment_exposure — bản ghi phân nhóm thí nghiệm (doc 23 Phase 4 mục 3).

Mỗi phiên một hàng cho MỌI player trong không gian thí nghiệm, kể cả nhóm đối chứng và
người ngoài rollout. Đây là mẫu số chung của cả hai phép đo incrementality: thiếu nó thì
join với dữ liệu UA/attribution không có hàng membership cho những player không bao giờ
thấy paywall — phép đo chỉ còn phủ được tập đã tự chọn lọc vào phễu mua.
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


def exposure(user, bucket=1234, holdout=0, rollout=1, salt="brain_holdout_v1",
             experiment="monetization_brain"):
    return envelope("experiment_exposure", {
        "experiment_id": experiment,
        "holdout_bucket": bucket,
        "is_holdout": holdout,
        "in_rollout": rollout,
        "salt_version": salt,
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


def test_ingestion_nhan_va_phang_experiment_exposure(client):
    c, path = client
    c.post("/push", json=exposure("u_1", bucket=777, holdout=1, rollout=0))
    rows = [r for r in rows_in(path) if r["event_name"] == "experiment_exposure"]
    assert len(rows) == 1
    r = rows[0]
    assert r["experiment_id"] == "monetization_brain"
    assert r["holdout_bucket"] == 777
    assert r["is_holdout"] == 1
    assert r["in_rollout"] == 0
    assert r["salt_version"] == "brain_holdout_v1"
    assert r.get("raw_event_data") is None
    assert r["is_known_event"] is True


def test_exposure_khong_co_khoa_tu_nhien_dedup_theo_seq(client):
    # Mỗi phiên là một lần phơi nhiễm thật, và đây là hàng duy nhất ghi bucket + salt tại
    # thời điểm đó — gộp các phiên bằng khoá tự chế thì mất dấu chu kỳ đo khi đổi muối.
    # 1.7.0 T9: khoá `seq:` theo sequenceId (không theo nội dung) — hai phiên là hai khoá.
    c, path = client
    c.post("/push", json=exposure("u_1"))
    c.post("/push", json={**exposure("u_1"), "sequenceId": 2})
    rows = [r for r in rows_in(path) if r["event_name"] == "experiment_exposure"]
    assert len(rows) == 2
    assert [r["dedup_key"] for r in rows] == ["seq:u_1:1:1724500000000", "seq:u_1:2:1724500000000"]


def test_moi_player_trong_khong_gian_thi_nghiem_deu_co_hang_membership(client):
    # Điểm khác mọi event hành vi: nhóm đối chứng và người ngoài rollout KHÔNG LÀM GÌ
    # vẫn phải có hàng — đây chính là lý do event này tồn tại.
    c, path = client
    c.post("/push", json=exposure("u_treatment", holdout=0, rollout=1))
    c.post("/push", json=exposure("u_holdout", holdout=1, rollout=1))
    c.post("/push", json=exposure("u_ngoai_rollout", holdout=0, rollout=0))
    rows = [r for r in rows_in(path) if r["event_name"] == "experiment_exposure"]
    assert len(rows) == 3
    assert {r["user_id"] for r in rows} == {"u_treatment", "u_holdout", "u_ngoai_rollout"}


def test_doi_muoi_ghi_nhan_duoc_qua_salt_version(client):
    # Đổi muối = phân nhóm lại toàn bộ dân số. Hàng thiếu/khác salt_version thì không biết
    # thuộc chu kỳ đo nào — join nhầm chu kỳ cho ra tăng trưởng giả.
    c, path = client
    c.post("/push", json=exposure("u_1", salt="brain_holdout_v1", bucket=100))
    c.post("/push", json=exposure("u_1", salt="brain_holdout_v2", bucket=9876))
    rows = [r for r in rows_in(path) if r["event_name"] == "experiment_exposure"]
    assert [(r["salt_version"], r["holdout_bucket"]) for r in rows] == [
        ("brain_holdout_v1", 100), ("brain_holdout_v2", 9876)
    ]


def test_thieu_truong_thi_mac_dinh_an_toan(client):
    # Thà hàng có giá trị mặc định còn hơn mất hàng membership: 0/"" là "chưa biết",
    # không phải "không thuộc nhóm nào".
    c, path = client
    c.post("/push", json=envelope("experiment_exposure", {"experiment_id": "monetization_brain"}))
    rows = [r for r in rows_in(path) if r["event_name"] == "experiment_exposure"]
    assert len(rows) == 1
    r = rows[0]
    assert r["holdout_bucket"] == 0
    assert r["is_holdout"] == 0
    assert r["in_rollout"] == 0
    assert r["salt_version"] == ""
