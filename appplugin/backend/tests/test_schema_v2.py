"""1.7.0 T9 — cột v2 (add-only, NULLABLE), `raw_event_data` là chuỗi JSON, sink BigQuery chỉ trỏ
emulator khi project = `local-test`, và ghi vào `events_staging`.

Không gọi GCP: `bigquery.Client` được thay bằng bản giả trước khi reload, và
`GOOGLE_APPLICATION_CREDENTIALS` trỏ vào file không tồn tại. Env + reload như `test_ingest.py`.
"""

from __future__ import annotations

import importlib
import json
from pathlib import Path

from fastapi.testclient import TestClient

_ENV = ("BRAIN_API_KEY", "BRAIN_API_KEYS", "BRAIN_BQ_ENDPOINT", "BRAIN_BQ_PROJECT",
        "BRAIN_RATE_APP_RPS", "BRAIN_RATE_APP_BURST", "BRAIN_RATE_USER_RPS", "BRAIN_RATE_USER_BURST")
_SCHEMA = Path(__file__).resolve().parent.parent / "schema" / "events_raw.json"
_V2_COLUMNS = {
    "event_id", "schema_version", "sdk_version", "package_name", "app_id", "consent_tier",
    "request_id", "imp_id", "mediation", "ad_source", "ad_unit", "ad_format", "placement",
    "value_micros", "precision_type", "currency", "error_code", "latency_ms", "reason", "ready",
    "is_action", "gdpr_applies", "gpp_sections", "form_kind", "duration_ms", "ads_shown_json",
    "screens", "fg_index", "sample_rate",
}


class _FakeBQ:
    made: list = []

    def __init__(self, project=None, **kwargs):
        self.project = project
        self.kwargs = kwargs
        self.inserted: list = []
        _FakeBQ.made.append(self)

    def get_dataset(self, ref, retry=None, timeout=None):
        return {"ref": ref}

    def insert_rows_json(self, table, rows, row_ids=None, retry=None, timeout=None):
        self.inserted.append((table, rows))
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
    monkeypatch.setattr(_FakeBQ, "made", [])
    import app.config
    import app.main

    importlib.reload(app.config)
    importlib.reload(app.main)
    return TestClient(app.main.app), path


def _ev(name, data, seq=5, **top):
    body = {"sequenceId": seq, "eventName": name, "eventData": data, "ts": 1790000000000,
            "userProperty": {"user_id": "u_1", "country": "US", "session_id": "s_1"}}
    body.update(top)
    return body


def _v2(name, data, seq=5, event_id="e1"):
    return _ev(name, data, seq, event_id=event_id, schema_version=2, sdk_version="1.7.0",
               package_name="test.sdk.zd", consent_tier="GRANTED")


def _rows(path):
    return [json.loads(l) for l in path.read_text().splitlines() if l.strip()] if path.exists() else []


def test_value_micros_int_va_truong_envelope(tmp_path, monkeypatch):
    c, path = _mk(tmp_path, monkeypatch)
    c.post("/push", json=_v2("ad_revenue", {"value_micros": 1234567, "currency": "USD"}))
    r = _rows(path)[0]
    got = {k: r.get(k) for k in ("value_micros", "schema_version", "event_id", "sdk_version",
                                 "package_name", "consent_tier", "currency")}
    assert got == {"value_micros": 1234567, "schema_version": 2, "event_id": "e1",
                   "sdk_version": "1.7.0", "package_name": "test.sdk.zd",
                   "consent_tier": "GRANTED", "currency": "USD"}, r
    assert isinstance(r["value_micros"], int)


def test_so_sai_kieu_thanh_null_khong_thanh_0(tmp_path, monkeypatch):
    c, path = _mk(tmp_path, monkeypatch)
    c.post("/push", json=[
        _v2("ad_revenue", {"value_micros": "42", "precision_type": 3}, seq=1, event_id="a"),
        _v2("ad_revenue", {"value_micros": "abc", "precision_type": "x"}, seq=2, event_id="b"),
        _v2("ad_revenue", {"value_micros": True}, seq=3, event_id="c"),
    ])
    got = [(r.get("value_micros"), r.get("precision_type")) for r in _rows(path)]
    assert got == [(42, 3), (None, None), (None, None)], got


def test_raw_event_data_la_chuoi_json(tmp_path, monkeypatch):
    c, path = _mk(tmp_path, monkeypatch)
    c.post("/push", json=_ev("tinh_nang_tuong_lai", {"placement": "full_x", "n": 1}))
    raw = _rows(path)[0].get("raw_event_data")
    assert isinstance(raw, str) and json.loads(raw) == {"placement": "full_x", "n": 1}, raw


def test_schema_co_cot_v2_nullable(tmp_path, monkeypatch):
    cols = json.loads(_SCHEMA.read_text(encoding="utf-8"))
    by_name = {c["name"]: c for c in cols}
    assert len(by_name) == len(cols), "ten cot trung"
    missing = sorted(_V2_COLUMNS - set(by_name))
    assert missing == [], missing
    assert by_name["value_micros"]["type"] == "INT64"
    assert all(by_name[n].get("mode", "NULLABLE") == "NULLABLE" for n in _V2_COLUMNS)


def test_hang_v2_lam_phang_va_moi_cot_nam_trong_schema(tmp_path, monkeypatch):
    names = {c["name"] for c in json.loads(_SCHEMA.read_text(encoding="utf-8"))}
    c, path = _mk(tmp_path, monkeypatch)
    c.post("/push", json=[
        _v2("ad_request", {"ad_format": "interstitial", "mediation": "admob", "placement": "full_home",
                           "ad_unit": "ca-app-pub-x/1", "request_id": "rq1", "screen_name": "main",
                           "is_sdk_init": 0}, seq=1, event_id="a"),
        _v2("ad_no_fill", {"request_id": "rq1", "latency_ms": 812, "error_code": 3,
                           "reason": "no fill"}, seq=2, event_id="b"),
        _v2("consent_choice", {"tier": "GRANTED", "gdpr_applies": 1, "gpp_sections": "2_7",
                               "latency_ms": 900, "form_kind": "initial"}, seq=3, event_id="c"),
        _v2("session_end", {"duration_ms": 61000, "ads_shown_json": "{\"interstitial\":2}",
                            "screens": 3, "fg_index": 1, "sample_rate": 0.5}, seq=4, event_id="d"),
    ])
    rows = _rows(path)
    extra = sorted({k for r in rows for k in r} - names)
    assert extra == [], extra
    req, nofill, consent, end = rows
    assert (req.get("request_id"), req.get("ad_unit"), req.get("is_known_event")) == (
        "rq1", "ca-app-pub-x/1", True), req
    assert (nofill.get("latency_ms"), nofill.get("error_code")) == (812, "3"), nofill
    assert (consent.get("gdpr_applies"), consent.get("gpp_sections"), consent.get("form_kind")) == (
        1, "2_7", "initial"), consent
    assert (end.get("duration_ms"), end.get("ads_shown_json"), end.get("screens"),
            end.get("sample_rate")) == (61000, "{\"interstitial\":2}", 3, 0.5), end


def test_ad_revenue_v2_van_vao_north_star(tmp_path, monkeypatch):
    c, _ = _mk(tmp_path, monkeypatch)
    c.post("/push", json=_v2("ad_revenue", {"ad_format": "interstitial", "mediation": "admob",
                                            "value_micros": 20000, "currency": "USD"}))
    iaa = c.get("/metrics/northstar").json()["revenue"]["iaa_usd"]
    assert abs(iaa - 0.02) < 1e-9, iaa


def test_endpoint_emulator_bi_tu_choi_khi_project_khac_local_test(tmp_path, monkeypatch):
    errors = []
    for env in ({"BRAIN_BQ_PROJECT": "prod-like-project", "BRAIN_BQ_ENDPOINT": "http://127.0.0.1:19050"},
                {"BRAIN_BQ_PROJECT": "local-test"}):
        err = None
        try:
            _mk(tmp_path, monkeypatch, sink="bigquery", **env)
        except Exception as exc:  # noqa: BLE001 — test ghi lại mọi loại lỗi khởi động
            err = exc
        errors.append((env, err, len(_FakeBQ.made)))
    assert all(e is not None and "local-test" in str(e) and made == 0 for _, e, made in errors), errors


def test_emulator_local_test_dung_anonymous_va_ghi_staging(tmp_path, monkeypatch):
    c, _ = _mk(tmp_path, monkeypatch, sink="bigquery", BRAIN_BQ_PROJECT="local-test",
               BRAIN_BQ_ENDPOINT="http://127.0.0.1:19050")
    from google.auth.credentials import AnonymousCredentials

    assert _FakeBQ.made, "khong tao client"
    kw = _FakeBQ.made[0].kwargs
    opts = kw.get("client_options")
    assert getattr(opts, "api_endpoint", None) == "http://127.0.0.1:19050", kw
    assert isinstance(kw.get("credentials"), AnonymousCredentials), kw
    assert c.post("/push", json=_v2("ad_click", {"placement": "full_x"})).status_code == 200
    tables = [t for cl in _FakeBQ.made for t, _ in cl.inserted]
    assert tables == ["local-test.monetization_brain.events_staging"], tables
