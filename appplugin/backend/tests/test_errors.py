"""1.7.0 T9 — lỗi có cấu trúc, giới hạn kích thước (413), log JSON mỗi request không lộ khoá.

Env + reload như `test_ingest.py`; không import tên mới ở đầu file.
"""

from __future__ import annotations

import importlib
import json
import logging

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


def _ev(data=None, seq=5, user="u_1"):
    return {"sequenceId": seq, "eventName": "behavior", "eventData": data or {},
            "ts": 1790000000000, "userProperty": {"user_id": user, "country": "US"}}


def _rows(path):
    return [json.loads(l) for l in path.read_text().splitlines() if l.strip()] if path.exists() else []


def _error(resp) -> dict:
    assert "application/json" in resp.headers.get("content-type", ""), (resp.status_code, resp.text)
    body = resp.json()
    assert body.get("ok") is False, body
    assert set(body.get("error", {})) >= {"code", "message"}, body
    assert body.get("request_id"), body
    return body


def _json_logs(caplog) -> list[dict]:
    out = []
    for rec in caplog.records:
        try:
            doc = json.loads(rec.getMessage())
        except ValueError:
            continue
        if isinstance(doc, dict) and "request_id" in doc:
            out.append(doc)
    return out


def test_loi_401_co_cau_truc(tmp_path, monkeypatch):
    c, _ = _mk(tmp_path, monkeypatch, BRAIN_API_KEY="k")
    body = _error(c.post("/push", json=_ev()))
    assert body["error"]["code"] == "unauthorized", body


def test_json_hong_400_co_cau_truc(tmp_path, monkeypatch):
    c, _ = _mk(tmp_path, monkeypatch)
    resp = c.post("/push", content=b"{khong phai json", headers={"Content-Type": "application/json"})
    assert resp.status_code == 400
    assert _error(resp)["error"]["code"] == "malformed_json"


def test_request_id_trong_header_khop_body(tmp_path, monkeypatch):
    c, _ = _mk(tmp_path, monkeypatch, BRAIN_API_KEY="k")
    resp = c.post("/push", json=_ev())
    rid = resp.headers.get("x-request-id")
    body = resp.json()
    assert rid and body.get("request_id") == rid, (rid, body)


def test_loi_5xx_co_cau_truc_khong_lo_chi_tiet(tmp_path, monkeypatch):
    import app.main

    c, _ = _mk(tmp_path, monkeypatch)

    def boom(rows):
        raise RuntimeError("chi-tiet-noi-bo /srv/data")

    monkeypatch.setattr(app.main.sink, "write", boom)
    resp = TestClient(app.main.app, raise_server_exceptions=False).post("/push", json=_ev())
    assert resp.status_code == 500
    body = _error(resp)
    assert body["error"]["code"] == "internal" and "chi-tiet-noi-bo" not in resp.text, body


def test_body_qua_256kb_413(tmp_path, monkeypatch):
    c, path = _mk(tmp_path, monkeypatch)
    resp = c.post("/push", json=_ev({"x": "a" * (300 * 1024)}))
    assert resp.status_code == 413
    assert _error(resp)["error"]["code"] == "payload_too_large"
    assert _rows(path) == []


def test_qua_500_event_mot_lo_413(tmp_path, monkeypatch):
    c, path = _mk(tmp_path, monkeypatch)
    ok = c.post("/push", json=[_ev(seq=i) for i in range(500)])
    too_many = c.post("/push", json=[_ev(seq=i) for i in range(501)])
    assert (ok.status_code, too_many.status_code) == (200, 413)
    assert len(_rows(path)) == 500


def test_log_request_json_du_truong_khong_lo_khoa(tmp_path, monkeypatch, caplog):
    c, _ = _mk(tmp_path, monkeypatch, BRAIN_API_KEYS="a:secret-k")
    with caplog.at_level(logging.INFO):
        c.post("/push", json=[_ev(seq=1), {"khong_co_eventName": 1}],
               headers={"X-API-KEY": "secret-k"})
    logs = _json_logs(caplog)
    assert len(logs) == 1, [r.getMessage() for r in caplog.records]
    doc = logs[0]
    assert {k: doc.get(k) for k in ("severity", "app_id", "accepted", "rejected", "status")} == {
        "severity": "INFO", "app_id": "a", "accepted": 1, "rejected": 1, "status": 200}, doc
    assert all("secret-k" not in r.getMessage() for r in caplog.records)


def test_log_401_khong_in_khoa_sai(tmp_path, monkeypatch, caplog):
    c, _ = _mk(tmp_path, monkeypatch, BRAIN_API_KEYS="a:ka")
    with caplog.at_level(logging.DEBUG):
        resp = c.post("/push", json=_ev(), headers={"X-API-KEY": "doan-mo-khoa-123"})
    logs = _json_logs(caplog)
    assert resp.status_code == 401 and [d.get("status") for d in logs] == [401], logs
    assert logs[0].get("severity") == "WARNING" and logs[0].get("app_id") is None, logs
    assert all("doan-mo-khoa-123" not in r.getMessage() for r in caplog.records)
