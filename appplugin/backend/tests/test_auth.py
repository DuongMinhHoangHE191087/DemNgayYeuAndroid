"""1.7.0 T9 — khoá theo app.

Mọi test đi qua env + `importlib.reload(app.config, app.main)` như `test_ingest.py`: không import
tên mới ở đầu file, để trên mã cũ test đỏ bằng assert chứ không bằng lỗi thu thập.
"""

from __future__ import annotations

import hmac
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


def _ev(name="behavior", data=None, **top):
    body = {"sequenceId": 5, "eventName": name, "eventData": data or {}, "ts": 1790000000000,
            "userProperty": {"user_id": "u_1", "country": "US", "session_id": "s_1"}}
    body.update(top)
    return body


def _rows(path):
    return [json.loads(l) for l in path.read_text().splitlines() if l.strip()] if path.exists() else []


def test_nhieu_khoa_thieu_hoac_sai_khoa_401(tmp_path, monkeypatch):
    c, path = _mk(tmp_path, monkeypatch, BRAIN_API_KEYS="a:ka,b:kb")
    missing = c.post("/push", json=_ev())
    wrong = c.post("/push", json=_ev(), headers={"X-API-KEY": "kc"})
    assert (missing.status_code, wrong.status_code) == (401, 401)
    assert _rows(path) == []


def test_khoa_nao_ghi_app_id_cua_khoa_do(tmp_path, monkeypatch):
    c, path = _mk(tmp_path, monkeypatch, BRAIN_API_KEYS="a:ka,b:kb")
    assert c.post("/push", json=_ev(), headers={"X-API-KEY": "kb"}).status_code == 200
    assert c.post("/push", json=_ev(), headers={"X-API-KEY": "ka"}).status_code == 200
    assert [r.get("app_id") for r in _rows(path)] == ["b", "a"]


def test_client_khong_tu_khai_duoc_app_id(tmp_path, monkeypatch):
    # `package_name`/`app_id` trong body là lời client tự khai; chỉ khoá quyết định app_id.
    c, path = _mk(tmp_path, monkeypatch, BRAIN_API_KEYS="a:ka,b:kb")
    body = _ev("behavior", {"app_id": "a"}, app_id="a", package_name="com.app.a")
    c.post("/push", json=body, headers={"X-API-KEY": "kb"})
    rows = _rows(path)
    assert [r.get("app_id") for r in rows] == ["b"], rows


def test_mot_brain_api_key_la_app_default(tmp_path, monkeypatch):
    # Tương thích `tools/local_sink.sh` (T8): một khoá = một app `default`.
    c, path = _mk(tmp_path, monkeypatch, BRAIN_API_KEY="k1")
    assert c.post("/push", json=_ev()).status_code == 401
    assert c.post("/push", json=_ev(), headers={"X-API-KEY": "k1"}).status_code == 200
    assert [r.get("app_id") for r in _rows(path)] == ["default"]


def test_ca_hai_bien_thi_brain_api_keys_thang(tmp_path, monkeypatch, caplog):
    with caplog.at_level(logging.INFO):
        c, _ = _mk(tmp_path, monkeypatch, BRAIN_API_KEY="old-secret-1",
                   BRAIN_API_KEYS="a:new-secret-2")
    assert c.post("/push", json=_ev(), headers={"X-API-KEY": "old-secret-1"}).status_code == 401
    assert c.post("/push", json=_ev(), headers={"X-API-KEY": "new-secret-2"}).status_code == 200
    notes = [r.getMessage() for r in caplog.records if "BRAIN_API_KEYS" in r.getMessage()]
    assert len(notes) == 1, [r.getMessage() for r in caplog.records]
    assert "secret" not in notes[0], notes[0]


def test_so_khop_compare_digest_moi_khoa_khong_thoat_som(tmp_path, monkeypatch):
    calls: list[int] = []
    real = hmac.compare_digest

    def counting(a, b):
        calls.append(1)
        return real(a, b)

    monkeypatch.setattr(hmac, "compare_digest", counting)
    c, _ = _mk(tmp_path, monkeypatch, BRAIN_API_KEYS="a:ka,b:kb,c:kc")
    calls.clear()
    first = c.post("/push", json=_ev(), headers={"X-API-KEY": "ka"})
    n_first = len(calls)
    calls.clear()
    none = c.post("/push", json=_ev())
    assert (first.status_code, n_first) == (200, 3), (first.status_code, n_first)
    assert (none.status_code, len(calls)) == (401, 3), (none.status_code, len(calls))


def test_brain_api_keys_hong_thi_khong_khoi_dong(tmp_path, monkeypatch):
    errors = []
    for bad in ("a-khong-co-hai-cham", "a:", ":k", "a:k1,a:k2", "a:k,b:k", " , "):
        err = None
        try:
            _mk(tmp_path, monkeypatch, BRAIN_API_KEYS=bad)
        except Exception as exc:  # noqa: BLE001 — test ghi lại mọi loại lỗi khởi động
            err = exc
        errors.append((bad, err))
    assert all(e is not None and "BRAIN_API_KEYS" in str(e) for _, e in errors), errors
    # Thông báo lỗi không được in lại khoá.
    assert all("k1" not in str(e) and "k2" not in str(e) for _, e in errors if e)
