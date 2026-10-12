"""1.8.0 T13 — `POST /rtdn` (Pub/Sub push, RTDN của Play) + `signals` trong phản hồi `/push` (§Hợp đồng C, D9).

Env + `importlib.reload(app.config, app.main)` như `test_auth.py`; Q1/Q2/Q5 chỉ đi qua HTTP (không import tên
mới ở đầu file) để trên mã cũ chúng đỏ bằng assert. Fixture `fixtures/rtdn/*.json` là envelope Pub/Sub push đúng
như Pub/Sub gửi (`message.data` = base64 của `DeveloperNotification`); `push_iap_purchase.json` là `iap_purchase`
của người sở hữu token fixture (`u_rtdn`, `pro_sub`). Số `notificationType` đã kiểm với tài liệu RTDN (DRY §5).
"""

from __future__ import annotations

import base64
import importlib
import json
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

TOKEN = "rtdn-local-secret-0123456789"
AUTH = {"Authorization": f"Bearer {TOKEN}"}
FIX = Path(__file__).parent / "fixtures" / "rtdn"
SUB_TOKEN = "rtdn-fixture-sub-token"
BASE_MS = 1790737200000
DAY_MS = 86_400_000
# notificationType -> trạng thái lưu (§C dùng GRACE; 20 không phải trạng thái của `signals`).
EXPECTED = {1: "RECOVERED", 2: "RENEWED", 3: "CANCELED", 4: "PURCHASED", 5: "ON_HOLD", 6: "GRACE",
            7: "RESTARTED", 10: "PAUSED", 12: "REVOKED", 13: "EXPIRED", 20: "PENDING_PURCHASE_CANCELED"}
SIGNAL_KEYS = {"v", "type", "product_id", "state", "event_ms"}
_ENV = ("BRAIN_API_KEY", "BRAIN_API_KEYS", "BRAIN_BQ_ENDPOINT", "BRAIN_BQ_PROJECT", "BRAIN_RTDN_TOKEN",
        "BRAIN_PLAY_CREDENTIALS", "BRAIN_PLAY_PACKAGE",
        "BRAIN_RATE_APP_RPS", "BRAIN_RATE_APP_BURST", "BRAIN_RATE_USER_RPS", "BRAIN_RATE_USER_BURST")


def _mk(tmp_path, monkeypatch, token=TOKEN):
    path = tmp_path / "d" / "events.jsonl"
    monkeypatch.setenv("BRAIN_SINK", "jsonl")
    monkeypatch.setenv("BRAIN_JSONL_PATH", str(path))
    for k in _ENV:
        monkeypatch.delenv(k, raising=False)
    monkeypatch.setenv("BRAIN_API_KEYS", "demo:key-demo,other:key-other")
    if token:
        monkeypatch.setenv("BRAIN_RTDN_TOKEN", token)
    import app.config
    import app.main

    importlib.reload(app.config)
    importlib.reload(app.main)
    return TestClient(app.main.app, raise_server_exceptions=False), path


def _fx(name):
    return json.loads((FIX / name).read_text(encoding="utf-8"))


def _envelope(note, message_id):
    data = base64.b64encode(json.dumps(note).encode("utf-8")).decode("ascii")
    return {"message": {"attributes": {}, "data": data, "messageId": message_id},
            "subscription": "projects/local-test/subscriptions/rtdn-push"}


def _sub(ntype, token, ms, **extra):
    return {"version": "1.0", "packageName": "test.sdk.zd", "eventTimeMillis": str(ms),
            "subscriptionNotification": {"version": "1.0", "notificationType": ntype, "purchaseToken": token,
                                         **extra}}


def _decode(envelope):
    return json.loads(base64.b64decode(envelope["message"]["data"]))


def _sub_rows(path):
    p = path.parent / "subscription_events.jsonl"
    return [json.loads(l) for l in p.read_text().splitlines() if l.strip()] if p.exists() else []


def _buy(c, user, token, product, micros=4_990_000, key="key-demo"):
    body = {"sequenceId": 1, "eventName": "iap_purchase", "ts": BASE_MS - 60_000,
            "eventData": {"product_id": product, "order_id": f"GPA.{token}", "price_micros": micros,
                          "currency_code": "USD", "purchase_token": token, "is_subscription": 1},
            "userProperty": {"user_id": user, "country": "US"}}
    assert c.post("/push", json=body, headers={"X-API-KEY": key}).status_code == 200


def _push(c, users=("u_rtdn",), key="key-demo"):
    body = [{"sequenceId": i, "eventName": "behavior", "eventData": {}, "ts": BASE_MS,
             "userProperty": {"user_id": u, "country": "US"}} for i, u in enumerate(users)]
    r = c.post("/push", json=body if len(body) > 1 else body[0], headers={"X-API-KEY": key})
    assert r.status_code == 200, r.text
    return r.json()


def _seed_fixture_owner(c):
    assert c.post("/push", json=_fx("push_iap_purchase.json"), headers={"X-API-KEY": "key-demo"}).status_code == 200


def test_Q1_rtdn_thieu_bearer_401(tmp_path, monkeypatch):
    c, path = _mk(tmp_path, monkeypatch)
    env = _fx("sub_06_in_grace_period.json")
    for headers in ({}, {"Authorization": "Bearer sai-token-khong-khop-0000"}, {"Authorization": TOKEN},
                    {"Authorization": f"Basic {TOKEN}"}, {"X-API-KEY": "key-demo"}):
        r = c.post("/rtdn", json=env, headers=headers)
        assert r.status_code == 401, (headers.keys(), r.status_code)
    assert _sub_rows(path) == []
    # Không đặt BRAIN_RTDN_TOKEN: fail-closed 503 (không bao giờ mở /rtdn không xác thực như dev của /push).
    c2, path2 = _mk(tmp_path / "b", monkeypatch, token="")
    r = c2.post("/rtdn", json=env, headers=AUTH)
    assert r.status_code == 503 and r.json()["error"]["code"] == "not_configured", r.text
    assert _sub_rows(path2) == []


def test_Q2_envelope_hop_le_2xx_sau_ghi(tmp_path, monkeypatch):
    c, path = _mk(tmp_path, monkeypatch)
    _seed_fixture_owner(c)
    r = c.post("/rtdn", json=_fx("sub_06_in_grace_period.json"), headers=AUTH)
    assert r.status_code == 200, r.status_code
    assert r.json()["ok"] is True and r.json()["stored"] is True, r.json()
    rows = _sub_rows(path)
    assert len(rows) == 1, rows
    row = rows[0]
    assert (row["state"], row["notification_type"], row["kind"]) == ("GRACE", 6, "subscription"), row
    assert (row["user_id"], row["app_id"], row["product_id"]) == ("u_rtdn", "demo", "pro_sub"), row
    assert row["dedup_key"] == "rtdn:rtdn-fx-06" and row["event_ms"] == BASE_MS + 6 * 60_000, row
    schema = Path(__file__).resolve().parent.parent / "schema" / "subscription_events.json"
    assert sorted(row) == sorted(c["name"] for c in json.loads(schema.read_text(encoding="utf-8"))), row

    # Ghi bền hỏng -> 5xx (Pub/Sub gửi lại), KHÔNG bao giờ 2xx trước khi ghi xong.
    def boom(row):
        raise OSError("disk full")

    monkeypatch.setattr(c.app.state.rtdn_store, "write", boom)
    r = c.post("/rtdn", json=_fx("sub_05_on_hold.json"), headers=AUTH)
    assert r.status_code == 500, r.status_code
    assert len(_sub_rows(path)) == 1


def test_Q3_muoi_mot_fixture_dung_trang_thai_va_signal(tmp_path, monkeypatch):
    """11 fixture (1,2,3,4,5,6,7,10,12,13,20) -> 11 hàng `subscription_events`; `/push` của chủ token trả đúng
    một signal: trạng thái-signal mới nhất theo `eventTimeMillis` (20 mới hơn nhưng không phải trạng thái §C)."""
    c, path = _mk(tmp_path, monkeypatch)
    _seed_fixture_owner(c)
    files = sorted(FIX.glob("sub_*.json"))
    assert len(files) == 11
    got_types = []
    for f in files:
        env = json.loads(f.read_text(encoding="utf-8"))
        note = _decode(env)
        got_types.append(note["subscriptionNotification"]["notificationType"])
        r = c.post("/rtdn", json=env, headers=AUTH)
        assert r.status_code == 200 and r.json()["state"] == EXPECTED[got_types[-1]], (f.name, r.text)
    assert got_types == sorted(EXPECTED)
    rows = _sub_rows(path)
    assert {r["notification_type"]: r["state"] for r in rows} == EXPECTED
    assert len({r["dedup_key"] for r in rows}) == 11
    assert all(r["user_id"] == "u_rtdn" and r["product_id"] == "pro_sub" for r in rows)
    assert _push(c)["signals"] == [{"v": 1, "type": "sub_state", "product_id": "pro_sub", "state": "EXPIRED",
                                    "event_ms": BASE_MS + 10 * 60_000}]


def test_Q4_trung_messageId_test_notification_va_envelope_hong(tmp_path, monkeypatch):
    c, path = _mk(tmp_path, monkeypatch)
    _seed_fixture_owner(c)
    env = _fx("sub_02_renewed.json")
    first, again = (c.post("/rtdn", json=env, headers=AUTH) for _ in range(2))
    assert (first.status_code, again.status_code) == (200, 200)
    assert (first.json()["stored"], again.json()["stored"]) == (True, False)
    assert len(_sub_rows(path)) == 1
    r = c.post("/rtdn", json=_fx("test.json"), headers=AUTH)
    assert r.status_code == 200 and r.json()["stored"] is False and r.json()["kind"] == "test", r.text
    good = _envelope(_sub(3, SUB_TOKEN, BASE_MS), "m-bad")
    bad_bodies = [
        {"subscription": "s"},
        {"message": {"messageId": "m-1"}},
        {"message": {"data": "@@khong-phai-base64@@", "messageId": "m-2"}},
        {"message": {"data": base64.b64encode(b"not json").decode(), "messageId": "m-3"}},
        {"message": {**good["message"], "messageId": ""}},
        _envelope({"version": "1.0", "eventTimeMillis": "1", "subscriptionNotification": {"purchaseToken": "t"}}, "m-4"),
        _envelope({"version": "1.0", "eventTimeMillis": "x", "subscriptionNotification": {
            "notificationType": 3, "purchaseToken": "t"}}, "m-5"),
        _envelope({"version": "1.0", "eventTimeMillis": "1", "subscriptionNotification": {
            "notificationType": 3, "purchaseToken": ""}}, "m-6"),
    ]
    for body in bad_bodies:
        assert c.post("/rtdn", json=body, headers=AUTH).status_code == 400, body
    assert c.post("/rtdn", content=b"{not json", headers={**AUTH, "Content-Type": "application/json"}).status_code == 400
    big = {"message": {"data": "A" * (70 * 1024), "messageId": "m-big"}}
    assert c.post("/rtdn", json=big, headers=AUTH).status_code == 413
    assert len(_sub_rows(path)) == 1


def test_Q5_push_tra_signals(tmp_path, monkeypatch):
    c, _ = _mk(tmp_path, monkeypatch)
    _seed_fixture_owner(c)
    rtdn = c.post("/rtdn", json=_fx("sub_06_in_grace_period.json"), headers=AUTH)
    body = _push(c)
    assert "signals" in body, body
    assert rtdn.status_code == 200, rtdn.status_code
    assert (body["ok"], body["accepted"], body["rejected"]) == (True, 1, 0), body
    assert body["signals"] == [{"v": 1, "type": "sub_state", "product_id": "pro_sub", "state": "GRACE",
                                "event_ms": BASE_MS + 6 * 60_000}], body
    assert _push(c, users=("u_khac",))["signals"] == []


def test_Q6_signals_toi_da_5_trong_7_ngay_moi_nhat_theo_san_pham(tmp_path, monkeypatch):
    import app.rtdn

    c, _ = _mk(tmp_path, monkeypatch)
    now = BASE_MS + 30 * DAY_MS
    monkeypatch.setattr(app.rtdn, "_now_ms", lambda: now)
    # Cửa sổ 7 ngày (giờ server nhận): p_old nhận 8 ngày trước -> không còn trong signals.
    _buy(c, "u_win", "tok-old", "p_old")
    _buy(c, "u_win", "tok-new", "p_new")
    monkeypatch.setattr(app.rtdn, "_now_ms", lambda: now - 8 * DAY_MS)
    assert c.post("/rtdn", json=_envelope(_sub(6, "tok-old", now - 100), "w-old"), headers=AUTH).status_code == 200
    monkeypatch.setattr(app.rtdn, "_now_ms", lambda: now - 7 * DAY_MS + 60_000)
    assert c.post("/rtdn", json=_envelope(_sub(5, "tok-new", now - 200), "w-new"), headers=AUTH).status_code == 200
    monkeypatch.setattr(app.rtdn, "_now_ms", lambda: now)
    assert [s["product_id"] for s in _push(c, users=("u_win",))["signals"]] == ["p_new"]

    # Trần 5: sáu sản phẩm -> 5 mới nhất, tăng dần theo event_ms; mỗi sản phẩm chỉ trạng thái mới nhất.
    for i in range(6):
        _buy(c, "u_cap", f"tok-{i}", f"p{i}")
        ok = c.post("/rtdn", json=_envelope(_sub(2, f"tok-{i}", now - 1000 + i), f"c-{i}"), headers=AUTH)
        assert ok.status_code == 200
    assert c.post("/rtdn", json=_envelope(_sub(5, "tok-5", now), "c-5b"), headers=AUTH).status_code == 200
    raw = c.post("/push", json={"sequenceId": 1, "eventName": "behavior", "eventData": {}, "ts": BASE_MS,
                                "userProperty": {"user_id": "u_cap"}}, headers={"X-API-KEY": "key-demo"})
    signals = raw.json()["signals"]
    assert [(s["product_id"], s["state"]) for s in signals] == [
        ("p1", "RENEWED"), ("p2", "RENEWED"), ("p3", "RENEWED"), ("p4", "RENEWED"), ("p5", "ON_HOLD")], signals
    assert all(set(s) == SIGNAL_KEYS and s["v"] == 1 and s["type"] == "sub_state" for s in signals)
    assert len(raw.content) < 4096


def test_Q7_voided_la_hang_hoan_tien_am_khong_signal(tmp_path, monkeypatch):
    c, path = _mk(tmp_path, monkeypatch)
    _seed_fixture_owner(c)
    r = c.post("/rtdn", json=_fx("voided.json"), headers=AUTH)
    assert r.status_code == 200 and r.json()["state"] == "VOIDED", r.text
    row = _sub_rows(path)[0]
    assert (row["kind"], row["price_micros"], row["currency_code"], row["order_id"]) == (
        "voided", -4_990_000, "USD", "GPA.fixture-1"), row
    assert (row["user_id"], row["product_id"]) == ("u_rtdn", "pro_sub"), row
    assert _push(c)["signals"] == []
    # Token chưa từng thấy: vẫn lưu (không mất), số tiền không bịa.
    note = {"version": "1.0", "packageName": "test.sdk.zd", "eventTimeMillis": str(BASE_MS),
            "voidedPurchaseNotification": {"purchaseToken": "tok-la", "orderId": "GPA.x", "productType": 2,
                                           "refundType": 1}}
    assert c.post("/rtdn", json=_envelope(note, "v-la"), headers=AUTH).status_code == 200
    la = _sub_rows(path)[1]
    assert (la["user_id"], la["price_micros"], la["state"]) == (None, None, "VOIDED"), la


def test_Q8_signals_chi_cho_user_cua_lo_cung_app(tmp_path, monkeypatch):
    c, path = _mk(tmp_path, monkeypatch)
    _seed_fixture_owner(c)
    assert c.post("/rtdn", json=_fx("sub_06_in_grace_period.json"), headers=AUTH).status_code == 200
    assert _push(c, users=("u_2",))["signals"] == []
    assert _push(c, key="key-other")["signals"] == []                 # cùng user_id, app khác (khoá khác)
    assert _push(c, users=("u_rtdn", "u_2"))["signals"] == []         # lô nhiều user: không đoán
    assert len(_push(c)["signals"]) == 1
    # Token chưa có `iap_purchase`: lưu với user_id rỗng, 2xx, không signal cho ai.
    assert c.post("/rtdn", json=_envelope(_sub(5, "tok-chua-biet", BASE_MS), "q8-x"), headers=AUTH).status_code == 200
    assert _sub_rows(path)[-1]["user_id"] is None
    # Đọc signals hỏng: /push vẫn 200 (đã ghi bền), signals rỗng — không biến ghi thành công thành 5xx.
    def boom(*a, **k):
        raise RuntimeError("store down")

    c.app.state.signals_cache.invalidate("demo", "u_rtdn")           # T13b: mục đã cache thì không chạm store nữa
    monkeypatch.setattr(c.app.state.rtdn_store, "recent", boom)
    body = _push(c)
    assert (body["accepted"], body["signals"]) == (1, [])


def test_Q9_subscription_source_fixture_va_http_503(tmp_path, monkeypatch):
    from app.config import Settings
    from app.subscription_source import (FixtureSubscriptionSource, HttpSubscriptionsV2, SourceNotConfigured,
                                         build_subscription_source)

    assert isinstance(build_subscription_source(Settings()), FixtureSubscriptionSource)
    with pytest.raises(SourceNotConfigured):
        HttpSubscriptionsV2("test.sdk.zd", "")
    http = HttpSubscriptionsV2("test.sdk.zd", "/khong/ton/tai/sa.json")
    with pytest.raises(SourceNotConfigured):
        http.lookup("test.sdk.zd", "tok")
    c, path = _mk(tmp_path, monkeypatch)
    # Không có `iap_purchase` cho token: sản phẩm hỏi SubscriptionSource (fixture cục bộ).
    c.app.state.subscription_source = FixtureSubscriptionSource({"tok-src": "pro_src"})
    assert c.post("/rtdn", json=_envelope(_sub(4, "tok-src", BASE_MS), "s-1"), headers=AUTH).status_code == 200
    assert _sub_rows(path)[-1]["product_id"] == "pro_src"
    # `subscriptionId` (trường cũ của RTDN) có thì dùng, không cần hỏi nguồn.
    env = _envelope(_sub(4, "tok-legacy", BASE_MS, subscriptionId="pro_legacy"), "s-2")
    assert c.post("/rtdn", json=env, headers=AUTH).status_code == 200
    assert _sub_rows(path)[-1]["product_id"] == "pro_legacy"
    # subscriptionsv2 thật là H7b: cần tra mà nguồn là HttpSubscriptionsV2 -> 503, không ghi, Pub/Sub gửi lại.
    c.app.state.subscription_source = http
    r = c.post("/rtdn", json=_envelope(_sub(6, "tok-khong-ai-biet", BASE_MS), "s-3"), headers=AUTH)
    assert r.status_code == 503 and r.json()["error"]["code"] == "not_configured", r.text
    assert len(_sub_rows(path)) == 2


@pytest.mark.integration
def test_rtdn_bigquery_emulator_11_fixture_toi_signals():
    """Task R #17 trên bigquery-emulator (khoá `docker`): `bq_setup` tạo `subscription_events`; `iap_purchase` của chủ
    token vào staging; 11 fixture + voided -> 12 hàng (tin trùng không thêm); `/push` của chủ token trả signal EXPIRED."""
    import os

    import app.main
    from app import bq_setup
    from app.config import Settings

    from conftest import emulator_dataset

    client, dataset = emulator_dataset("t13rtdn")
    settings = Settings(sink="bigquery", bq_project="local-test", bq_dataset=dataset,
                        bq_endpoint=os.environ["BRAIN_IT_BQ_ENDPOINT"], api_keys="demo:key-demo", rtdn_token=TOKEN)
    c = TestClient(app.main.create_app(settings))
    _seed_fixture_owner(c)
    for f in sorted(FIX.glob("sub_*.json")) + [FIX / "voided.json", FIX / "test.json"]:
        r = c.post("/rtdn", json=json.loads(f.read_text(encoding="utf-8")), headers=AUTH)
        assert r.status_code == 200, (f.name, r.text)
    again = c.post("/rtdn", json=_fx("sub_06_in_grace_period.json"), headers=AUTH)
    assert (again.status_code, again.json()["stored"]) == (200, False), again.text
    got = bq_setup.run_sql(client, (
        "SELECT notification_type, state, user_id, product_id, price_micros FROM "
        f"`local-test.{dataset}.subscription_events` ORDER BY received_ts_ms"))
    assert len(got) == 12, got
    assert {r["notification_type"]: r["state"] for r in got if r["state"] != "VOIDED"} == EXPECTED
    assert all(r["user_id"] == "u_rtdn" and r["product_id"] == "pro_sub" for r in got), got
    assert [r["price_micros"] for r in got if r["state"] == "VOIDED"] == [-4_990_000]
    assert _push(c)["signals"] == [{"v": 1, "type": "sub_state", "product_id": "pro_sub", "state": "EXPIRED",
                                    "event_ms": BASE_MS + 10 * 60_000}]


# ---- 1.8.0 Task 13b: cache signals theo (app_id, user_id) 60 s (R-13 m1) + hai tính chất bảo mật (R-13 m4) ----
def _count_recent(c, monkeypatch):
    calls = []
    real = c.app.state.rtdn_store.recent

    def counting(app_id, user_id, since_ms):
        calls.append((app_id, user_id))
        return real(app_id, user_id, since_ms)

    monkeypatch.setattr(c.app.state.rtdn_store, "recent", counting)
    return calls


def test_T13b_1_hai_push_cung_user_trong_ttl_chi_mot_truy_van_rtdn_lam_moi(tmp_path, monkeypatch):
    c, _ = _mk(tmp_path, monkeypatch)
    calls = _count_recent(c, monkeypatch)
    _seed_fixture_owner(c)                                            # chính /push này là truy vấn thứ nhất
    assert _push(c)["signals"] == []
    assert _push(c)["signals"] == []                                  # trúng cache, kể cả danh sách rỗng
    assert len(calls) == 1, calls
    assert c.post("/rtdn", json=_fx("sub_06_in_grace_period.json"), headers=AUTH).status_code == 200
    fresh = _push(c)["signals"]
    assert [s["state"] for s in fresh] == ["GRACE"], fresh            # /rtdn xoá mục: signal mới ra ngay
    assert len(calls) == 2, calls
    assert _push(c)["signals"] == fresh and len(calls) == 2           # rồi lại trúng cache


def test_T13b_2_cache_khoa_theo_app_va_user(tmp_path, monkeypatch):
    c, _ = _mk(tmp_path, monkeypatch)
    calls = _count_recent(c, monkeypatch)
    _push(c)
    _push(c, key="key-other")                                         # cùng user_id, app khác: KHÔNG dùng chung mục
    _push(c, users=("u_2",))
    assert sorted(calls) == [("demo", "u_2"), ("demo", "u_rtdn"), ("other", "u_rtdn")], calls


def test_T13b_3_het_ttl_truy_van_lai_va_loi_doc_khong_bi_luu(tmp_path, monkeypatch):
    import app.signals_cache as sc

    c, _ = _mk(tmp_path, monkeypatch)
    calls = _count_recent(c, monkeypatch)
    clock = [1000.0]
    monkeypatch.setattr(sc.time, "monotonic", lambda: clock[0])
    _push(c)
    clock[0] += sc.SIGNALS_CACHE_TTL_S - 1
    _push(c)
    assert len(calls) == 1, calls
    clock[0] += 1                                                     # đủ TTL
    _push(c)
    assert len(calls) == 2, calls
    real = c.app.state.rtdn_store.recent

    def boom(*a, **k):
        raise RuntimeError("store down")

    c.app.state.signals_cache.invalidate("demo", "u_rtdn")
    monkeypatch.setattr(c.app.state.rtdn_store, "recent", boom)
    assert _push(c)["signals"] == []                                  # lỗi đọc -> [] và KHÔNG lưu
    monkeypatch.setattr(c.app.state.rtdn_store, "recent", lambda *a: calls.append("again") or real(*a))
    _push(c)
    assert "again" in calls, calls


def test_T13b_4_hai_iap_purchase_cung_token_nguoi_som_nhat_thang(tmp_path, monkeypatch):
    import time

    c, path = _mk(tmp_path, monkeypatch)
    _buy(c, "u_first", "tok-shared", "pro_sub")
    time.sleep(0.02)                                                  # received_ts_ms của lượt sau chắc chắn lớn hơn
    _buy(c, "u_capture", "tok-shared", "pro_sub")                     # client khác gửi lại cùng token, muộn hơn
    assert c.post("/rtdn", json=_envelope(_sub(5, "tok-shared", BASE_MS), "t13b-cap"), headers=AUTH).status_code == 200
    assert _sub_rows(path)[-1]["user_id"] == "u_first"
    assert [s["state"] for s in _push(c, users=("u_first",))["signals"]] == ["ON_HOLD"]
    assert _push(c, users=("u_capture",))["signals"] == []


def test_T13b_5_rtdn_token_ngan_hon_16_bi_tu_choi_khi_nap_cau_hinh(monkeypatch):
    import app.config as cfg

    for k in _ENV:
        monkeypatch.delenv(k, raising=False)
    monkeypatch.setenv("BRAIN_SINK", "jsonl")
    monkeypatch.setenv("BRAIN_RTDN_TOKEN", "x" * 15)
    with pytest.raises(ValueError) as err:
        cfg.Settings.from_env()
    assert str(err.value) == "BRAIN_RTDN_TOKEN phải dài >= 16 ký tự"
    monkeypatch.setenv("BRAIN_RTDN_TOKEN", "x" * 16)
    assert cfg.Settings.from_env().rtdn_token == "x" * 16
