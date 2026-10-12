"""1.7.0 T9 — `/health` là kiểm sẵn sàng thật (sink ghi được), không phải "process còn sống".

Không gọi GCP: client BigQuery được thay bằng bản giả trước khi reload, và
`GOOGLE_APPLICATION_CREDENTIALS` trỏ vào file không tồn tại để một client thật (nếu lọt) không
thể lấy credential của máy. Env + reload như `test_ingest.py`.
"""

from __future__ import annotations

import importlib
import os
import stat

from fastapi.testclient import TestClient

_ENV = ("BRAIN_API_KEY", "BRAIN_API_KEYS", "BRAIN_BQ_ENDPOINT", "BRAIN_BQ_PROJECT", "BRAIN_GIT_SHA",
        "BRAIN_RATE_APP_RPS", "BRAIN_RATE_APP_BURST", "BRAIN_RATE_USER_RPS", "BRAIN_RATE_USER_BURST")


class _FakeBQ:
    fail = False

    def __init__(self, project=None, **kwargs):
        self.project = project
        self.kwargs = kwargs

    def get_dataset(self, ref, retry=None, timeout=None):
        if _FakeBQ.fail:
            raise RuntimeError("dataset khong toi duoc")
        return {"ref": ref}

    def insert_rows_json(self, table, rows, row_ids=None, retry=None, timeout=None):
        return []


def _mk(tmp_path, monkeypatch, sink="jsonl", **env):
    path = tmp_path / "d" / "events.jsonl"
    monkeypatch.setenv("BRAIN_SINK", sink)
    monkeypatch.setenv("BRAIN_JSONL_PATH", str(path))
    monkeypatch.setenv("GOOGLE_APPLICATION_CREDENTIALS", str(tmp_path / "khong-co.json"))
    for k in _ENV:
        monkeypatch.delenv(k, raising=False)
    for k, v in env.items():
        monkeypatch.setenv(k, v)
    if sink == "bigquery":
        from google.cloud import bigquery

        monkeypatch.setattr(bigquery, "Client", _FakeBQ)
    import app.config
    import app.main

    importlib.reload(app.config)
    importlib.reload(app.main)
    return TestClient(app.main.app), path


def test_health_503_khi_thu_muc_jsonl_khong_ghi_duoc(tmp_path, monkeypatch):
    c, path = _mk(tmp_path, monkeypatch)
    os.chmod(path.parent, stat.S_IRUSR | stat.S_IXUSR)
    try:
        resp = c.get("/health")
    finally:
        os.chmod(path.parent, stat.S_IRWXU)
    assert resp.status_code == 503, resp.text
    assert resp.json().get("ok") is False


def test_health_200_co_version_tu_env(tmp_path, monkeypatch):
    c, _ = _mk(tmp_path, monkeypatch, BRAIN_GIT_SHA="abc1234")
    resp = c.get("/health")
    body = resp.json()
    assert resp.status_code == 200 and body.get("ok") is True and body.get("version") == "abc1234", body


def test_health_bigquery_ping_get_dataset(tmp_path, monkeypatch):
    monkeypatch.setattr(_FakeBQ, "fail", True)
    c, _ = _mk(tmp_path, monkeypatch, sink="bigquery", BRAIN_BQ_PROJECT="demo-proj")
    down = c.get("/health").status_code
    monkeypatch.setattr(_FakeBQ, "fail", False)
    up = c.get("/health").status_code
    assert (down, up) == (503, 200)


def test_health_khong_can_khoa_va_bao_auth(tmp_path, monkeypatch):
    dev, _ = _mk(tmp_path, monkeypatch)
    auth_dev = dev.get("/health").json().get("auth")
    keyed, _ = _mk(tmp_path, monkeypatch, BRAIN_API_KEYS="a:ka")
    resp = keyed.get("/health")
    assert (auth_dev, resp.status_code, resp.json().get("auth")) == (False, 200, True), resp.text
