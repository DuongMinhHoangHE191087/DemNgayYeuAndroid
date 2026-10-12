"""1.7.0 T9 — `dedup_key` trên MỌI hàng: khoá tự nhiên -> `ev:<event_id>` (v2) -> `seq:<user>:<seq>:<ts>`.

MERGE của T10 khử trùng theo cột này, nên hàng `dedup_key` NULL không được còn. Khoá `seq:` không
bịa từ NỘI DUNG (hai event giống hệt nhưng khác `sequenceId` vẫn là hai khoá): SDK gắn
`sequenceId`/`ts` lúc dựng và gửi lại đúng body cũ, nên cùng khoá = cùng một lần phát.
Env + reload như `test_ingest.py`.
"""

from __future__ import annotations

import importlib
import json

from fastapi.testclient import TestClient

_ENV = ("BRAIN_API_KEY", "BRAIN_API_KEYS", "BRAIN_BQ_ENDPOINT", "BRAIN_BQ_PROJECT",
        "BRAIN_RATE_APP_RPS", "BRAIN_RATE_APP_BURST", "BRAIN_RATE_USER_RPS", "BRAIN_RATE_USER_BURST")


def _mk(tmp_path, monkeypatch):
    path = tmp_path / "d" / "events.jsonl"
    monkeypatch.setenv("BRAIN_SINK", "jsonl")
    monkeypatch.setenv("BRAIN_JSONL_PATH", str(path))
    for k in _ENV:
        monkeypatch.delenv(k, raising=False)
    import app.config
    import app.main

    importlib.reload(app.config)
    importlib.reload(app.main)
    return TestClient(app.main.app), path


def _ev(name, data=None, seq=5, ts=1790000000000, user="u_1", **top):
    body = {"sequenceId": seq, "eventName": name, "eventData": data or {}, "ts": ts,
            "userProperty": {"user_id": user, "country": "US"}}
    body.update(top)
    return body


def _keys(path):
    return [json.loads(l).get("dedup_key") for l in path.read_text().splitlines() if l.strip()]


def test_dedup_ev_event_id_cho_ad_revenue_v2(tmp_path, monkeypatch):
    c, path = _mk(tmp_path, monkeypatch)
    c.post("/push", json=_ev("ad_revenue", {"value_micros": 1234567, "currency": "USD"},
                             event_id="e1", schema_version=2))
    assert _keys(path) == ["ev:e1"]


def test_dedup_seq_cho_v1_khong_event_id(tmp_path, monkeypatch):
    c, path = _mk(tmp_path, monkeypatch)
    c.post("/push", json=_ev("behavior"))
    assert _keys(path) == ["seq:u_1:5:1790000000000"]


def test_thu_tu_uu_tien_tu_nhien_roi_ev_roi_seq(tmp_path, monkeypatch):
    c, path = _mk(tmp_path, monkeypatch)
    c.post("/push", json=[
        _ev("iap_purchase", {"order_id": "GPA.1"}, seq=1, event_id="e-iap", schema_version=2),
        _ev("iap_purchase", {}, seq=2, event_id="e-no-order", schema_version=2),
        _ev("brain_comfort", {"comfort_value": 0.7}, seq=3, event_id="", schema_version=2),
        _ev("brain_comfort", {"comfort_value": 0.7}, seq=4, event_id="x" * 300, schema_version=2),
    ])
    assert _keys(path) == ["iap_purchase:GPA.1", "ev:e-no-order",
                           "seq:u_1:3:1790000000000", "seq:u_1:4:1790000000000"]


def test_moi_hang_deu_co_dedup_key(tmp_path, monkeypatch):
    c, path = _mk(tmp_path, monkeypatch)
    c.post("/push", json=[
        _ev("brain_comfort", {"comfort_value": 0.7}, seq=1),
        _ev("offerwall_impression", {"provider_id": "revu"}, seq=2),
        _ev("paywall_impression", {"placement": "shop"}, seq=3),
        _ev("experiment_exposure", {"experiment_id": "x"}, seq=4),
        _ev("tinh_nang_tuong_lai", {"a": 1}, seq=5),
        _ev("ad_click", {"placement": "full_x"}, seq=6, event_id="e6", schema_version=2),
    ])
    keys = _keys(path)
    assert len(keys) == 6 and all(isinstance(k, str) and k for k in keys), keys


def test_gui_lai_cung_body_cung_khoa_khac_seq_khac_khoa(tmp_path, monkeypatch):
    c, path = _mk(tmp_path, monkeypatch)
    replay = _ev("offerwall_impression", {"provider_id": "revu"}, seq=7)
    c.post("/push", json=replay)
    c.post("/push", json=replay)
    c.post("/push", json=_ev("offerwall_impression", {"provider_id": "revu"}, seq=8))
    k1, k2, k3 = _keys(path)
    assert k1 == k2 and k1 is not None and k3 != k1, (k1, k2, k3)
