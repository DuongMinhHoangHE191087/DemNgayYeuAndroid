"""1.7.0 T9 — giới hạn tốc độ (token bucket trong process) theo app và theo user.

Env + reload như `test_ingest.py`; không import tên mới ở đầu file.
"""

from __future__ import annotations

import importlib
import json

from fastapi.testclient import TestClient

_ENV = ("BRAIN_API_KEY", "BRAIN_API_KEYS", "BRAIN_BQ_ENDPOINT", "BRAIN_BQ_PROJECT",
        "BRAIN_RATE_APP_RPS", "BRAIN_RATE_APP_BURST", "BRAIN_RATE_USER_RPS", "BRAIN_RATE_USER_BURST")


def _mk(tmp_path, monkeypatch, **env):
    path = tmp_path / "d" / "events.jsonl"
    monkeypatch.setenv("BRAIN_SINK", "jsonl")
    monkeypatch.setenv("BRAIN_JSONL_PATH", str(path))
    for k in _ENV:
        monkeypatch.delenv(k, raising=False)
    for k, v in env.items():
        monkeypatch.setenv(k, v)
    import app.config
    import app.main

    importlib.reload(app.config)
    importlib.reload(app.main)
    return TestClient(app.main.app), path


def _ev(user="u_1", seq=5):
    return {"sequenceId": seq, "eventName": "behavior", "eventData": {}, "ts": 1790000000000,
            "userProperty": {"user_id": user, "country": "US"}}


def _rows(path):
    return [json.loads(l) for l in path.read_text().splitlines() if l.strip()] if path.exists() else []


def test_vuot_han_muc_app_429_co_retry_after_va_khong_ghi(tmp_path, monkeypatch):
    c, path = _mk(tmp_path, monkeypatch, BRAIN_RATE_APP_RPS="1", BRAIN_RATE_APP_BURST="2")
    resp = [c.post("/push", json=_ev(seq=i)) for i in range(3)]
    third = resp[2]
    assert [r.status_code for r in resp] == [200, 200, 429]
    assert int(third.headers.get("Retry-After", "0")) >= 1, dict(third.headers)
    body = third.json()
    assert body.get("ok") is False and body.get("error", {}).get("code") == "rate_limited", body
    assert len(_rows(path)) == 2


def test_retry_after_la_giay_nguyen_lam_tron_len(tmp_path, monkeypatch):
    # 0,5 token/giây, vừa tiêu token duy nhất -> cần ~2 giây cho token kế tiếp.
    c, _ = _mk(tmp_path, monkeypatch, BRAIN_RATE_APP_RPS="0.5", BRAIN_RATE_APP_BURST="1")
    c.post("/push", json=_ev())
    second = c.post("/push", json=_ev(seq=6))
    assert second.status_code == 429 and second.headers.get("Retry-After") == "2", (
        second.status_code, dict(second.headers))


def test_han_muc_theo_user(tmp_path, monkeypatch):
    c, path = _mk(tmp_path, monkeypatch, BRAIN_RATE_USER_RPS="1", BRAIN_RATE_USER_BURST="1")
    codes = [c.post("/push", json=_ev("u_1", 1)).status_code,
             c.post("/push", json=_ev("u_1", 2)).status_code,
             c.post("/push", json=_ev("u_2", 3)).status_code]
    assert codes == [200, 429, 200]
    assert [r["user_id"] for r in _rows(path)] == ["u_1", "u_2"]


def test_han_muc_tach_theo_app(tmp_path, monkeypatch):
    c, _ = _mk(tmp_path, monkeypatch, BRAIN_API_KEYS="a:ka,b:kb",
               BRAIN_RATE_APP_RPS="1", BRAIN_RATE_APP_BURST="1")
    codes = [c.post("/push", json=_ev(seq=1), headers={"X-API-KEY": "ka"}).status_code,
             c.post("/push", json=_ev(seq=2), headers={"X-API-KEY": "ka"}).status_code,
             c.post("/push", json=_ev(seq=3), headers={"X-API-KEY": "kb"}).status_code]
    assert codes == [200, 429, 200]
