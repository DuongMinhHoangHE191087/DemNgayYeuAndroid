"""Test ingestion.

Nguyên tắc của bộ test này: payload dùng ở đây phải là **hình dạng thật mà SDK Android gửi**,
sao từ `EventRequest.kt` — camelCase ở tầng ngoài, snake_case bên trong `userProperty`,
`timestamp` đổi tên thành `ts`. Nếu test tự bịa một payload "gọn gàng" hơn thì nó chỉ chứng
minh backend nói chuyện được với chính nó.
"""

from __future__ import annotations

import importlib
import json

import pytest
from fastapi.testclient import TestClient


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


def envelope(event_name: str, event_data: dict) -> dict:
    return {
        "sequenceId": 42,
        "eventName": event_name,
        "eventData": event_data,
        "ts": 1724500000000,
        "userProperty": {
            "user_id": "u_1", "app_name": "demo", "app_version": "1.0.0",
            "language": "vi", "country": "US", "device_info": "Pixel",
            "event_date": 20260824, "session_number": 3, "session_id": "s_1",
            "install_day": 20260801, "retention_day": 23,
            "local_hour": 15, "local_weekday": "Mon", "platform": "android", "ab_test": 4,
        },
    }


DECISION = envelope("brain_decision", {
    "decision_id": "d_1",
    "decision_point": "INTERSTITIAL_OPPORTUNITY",
    "chosen_action_id": "interstitial",
    "chosen_action_mode": "INTERSTITIAL",
    "chosen_action_probability": 0.62,
    "eligible_actions": "no_action,interstitial",
    "guardrails_applied": "",
    "segment": "CASUAL_STEADY",
    "segment_confidence": 0.41,
    "scenario": "BALANCED_HYBRID",
    "is_holdout": 0,
    "policy_version": "policy_2026_08_24_reference",
    "screen_name": "main",
})


def rows_in(path):
    return [json.loads(l) for l in path.read_text().splitlines() if l.strip()]


def test_health(client):
    c, _ = client
    assert c.get("/health").json()["ok"] is True


def test_nhan_duoc_event_dung_hinh_dang_cua_sdk(client):
    c, path = client
    assert c.post("/push", json=DECISION).status_code == 200
    rows = rows_in(path)
    assert len(rows) == 1
    r = rows[0]
    assert r["decision_id"] == "d_1"
    assert r["user_id"] == "u_1"
    assert r["is_known_event"] is True


def test_hai_truong_khong_tai_tao_duoc_phai_giu_nguyen_ven(client):
    # Đây là lý do tồn tại của cả lớp ingestion. Mất propensity hoặc eligible set thì mọi
    # estimator OPE về sau đều vô hiệu (doc 46 mục B.1) — và không cách nào tính lại được.
    c, path = client
    c.post("/push", json=DECISION)
    r = rows_in(path)[0]
    assert r["chosen_action_probability"] == pytest.approx(0.62)
    assert r["eligible_actions"] == "no_action,interstitial"


def test_thu_tu_eligible_actions_khong_bi_sap_xep_lai(client):
    c, path = client
    ev = envelope("brain_decision", {**DECISION["eventData"],
                                     "decision_id": "d_order",
                                     "eligible_actions": "interstitial,no_action"})
    c.post("/push", json=ev)
    assert rows_in(path)[-1]["eligible_actions"] == "interstitial,no_action"


def test_phan_vung_theo_dong_ho_client_khong_theo_gio_nhan(client):
    # Hàng đợi bền vững của SDK replay bản ghi khi app mở lại — có thể nhiều ngày sau.
    # Dùng giờ nhận làm mốc sẽ dồn hết lượng tồn đọng vào sai ngày.
    c, path = client
    c.post("/push", json=DECISION)
    r = rows_in(path)[0]
    assert r["client_ts_ms"] == 1724500000000
    assert r["received_ts_ms"] > r["client_ts_ms"]


def test_khoa_chong_trung_theo_decision_id(client):
    c, path = client
    c.post("/push", json=DECISION)
    c.post("/push", json=DECISION)
    rows = rows_in(path)
    assert len({r["dedup_key"] for r in rows}) == 1
    assert rows[0]["dedup_key"] == "brain_decision:d_1"


def test_event_khong_co_khoa_on_dinh_thi_dedup_key_theo_seq(client):
    # Bịa khoá từ nội dung sẽ gộp nhầm hai lần `brain_comfort` thật sự giống nhau. 1.7.0 T9:
    # mọi hàng có khoá — không khoá tự nhiên thì `seq:<user>:<sequenceId>:<ts>` (SDK gắn lúc
    # dựng), không phải nội dung.
    c, path = client
    c.post("/push", json=envelope("brain_comfort", {
        "comfort_value": 0.72, "comfort_coverage": 0.4,
        "components_missing": "a;b;c", "policy_aggressiveness": "X=2",
    }))
    assert rows_in(path)[-1]["dedup_key"] == "seq:u_1:42:1724500000000"


def test_comfort_luon_kem_coverage(client):
    c, path = client
    c.post("/push", json=envelope("brain_comfort", {
        "comfort_value": 0.72, "comfort_coverage": 0.4,
        "components_missing": "organic;refund;retention", "policy_aggressiveness": "",
    }))
    r = rows_in(path)[-1]
    assert r["comfort_coverage"] == pytest.approx(0.4)
    assert r["components_missing"] == "organic;refund;retention"


def test_gia_iap_giu_nguyen_ban_khong_quy_doi(client):
    # Client cố ý gửi nguyên bản của store; quy đổi USD thuộc về lớp phân tích, theo tỉ giá
    # đúng ngày giao dịch (doc 45 mục D.1).
    c, path = client
    c.post("/push", json=envelope("iap_purchase", {
        "product_id": "premium_monthly", "order_id": "GPA.1", "price_micros": 99000000000,
        "currency_code": "VND", "quantity": 1, "channel": "PLAY_STORE", "is_subscription": 1,
    }))
    r = rows_in(path)[-1]
    assert r["price_micros"] == 99000000000
    assert r["currency_code"] == "VND"
    assert "price_usd" not in r


def test_purchase_token_chay_trong_log_pipeline(client):
    # Biên nhận Play Billing phải tới được kho log — đó là đầu vào bắt buộc của xác thực
    # giao dịch phía server (doc 45 mục E.1). Thiếu trường này thì khoảng trống "quyền lợi
    # tin vào những gì Play trả về trên máy" không bao giờ đóng được.
    c, path = client
    c.post("/push", json=envelope("iap_purchase", {
        "product_id": "remove_ads", "order_id": "GPA.7", "price_micros": 1990000,
        "currency_code": "USD", "quantity": 1, "channel": "PLAY_STORE", "is_subscription": 0,
        "purchase_token": "opaque.play.token.abc",
    }))
    r = rows_in(path)[-1]
    assert r["purchase_token"] == "opaque.play.token.abc"
    # Kênh web-shop không có token Play: trường rỗng chứ không vắng mặt.
    c.post("/push", json=envelope("iap_purchase", {
        "product_id": "gems_500", "order_id": "WEB-1", "price_micros": 4990000,
        "currency_code": "USD", "quantity": 1, "channel": "WEB_SHOP", "is_subscription": 0,
    }))
    r = rows_in(path)[-1]
    assert r["purchase_token"] == ""


def test_event_la_van_duoc_luu_nguyen_ven(client):
    c, path = client
    c.post("/push", json=envelope("tinh_nang_tuong_lai", {"gi_do": 1}))
    r = rows_in(path)[-1]
    assert r["is_known_event"] is False
    # 1.7.0 T9: chuỗi JSON (cột JSON của BigQuery nhận chuỗi qua insert_rows_json).
    assert json.loads(r["raw_event_data"]) == {"gi_do": 1}


def test_mot_event_hong_khong_lam_hong_ca_lo(client):
    c, path = client
    resp = c.post("/push", json=[DECISION, {"khong_co_eventName": True}])
    assert resp.status_code == 200
    assert resp.json() == {"ok": True, "accepted": 1, "rejected": 1, "signals": []}  # 1.8.0 T13: §C
    assert len(rows_in(path)) == 1


def test_json_hong_tra_400_de_client_bo_di(client):
    # 400 chứ không 500: gửi lại bao nhiêu lần cũng hỏng, để client kẹt trong hàng đợi
    # mãi mãi là tệ hơn.
    c, _ = client
    assert c.post("/push", content=b"{khong phai json",
                  headers={"Content-Type": "application/json"}).status_code == 400


def test_loi_ghi_phai_tra_5xx_de_client_giu_ban_ghi(client, monkeypatch):
    # Ràng buộc số một (doc 46 mục C.1). Trả 2xx khi chưa ghi được là ra lệnh cho client
    # xoá vĩnh viễn bản ghi đó.
    import app.main

    def no_ghi_duoc(rows):
        raise RuntimeError("o dia day")

    monkeypatch.setattr(app.main.sink, "write", no_ghi_duoc)
    c, _ = client
    with pytest.raises(RuntimeError):
        c.post("/push", json=DECISION)


def test_kiem_tra_api_key_khi_da_cau_hinh(tmp_path, monkeypatch):
    monkeypatch.setenv("BRAIN_SINK", "jsonl")
    monkeypatch.setenv("BRAIN_JSONL_PATH", str(tmp_path / "e.jsonl"))
    monkeypatch.setenv("BRAIN_API_KEY", "secret")
    import app.config
    import app.main

    importlib.reload(app.config)
    importlib.reload(app.main)
    c = TestClient(app.main.app)
    assert c.post("/push", json=DECISION).status_code == 401
    assert c.post("/push", json=DECISION, headers={"X-API-KEY": "secret"}).status_code == 200


def test_cot_phan_vung_sinh_tu_dong_ho_client(client):
    # Cột này là khoá phân vùng của bảng (`app/bq_tables.py`). Thiếu nó thì lệnh tạo bảng
    # chạy không nổi — nên nó phải được test, không chỉ được ghi trong schema.
    c, path = client
    c.post("/push", json=DECISION)
    assert rows_in(path)[0]["client_ts_date"] == "2024-08-24"


def test_dong_ho_may_sai_khong_lam_hong_ban_ghi(client):
    # Một bản ghi có timestamp vô lý vẫn đáng giữ; nó chỉ nên nằm ở phân vùng dễ nhận ra
    # thay vì làm hỏng cả lô.
    c, path = client
    for bad_ts in (0, -1, 99999999999999999):
        ev = dict(DECISION)
        ev["ts"] = bad_ts
        assert c.post("/push", json=ev).status_code == 200
        assert rows_in(path)[-1]["client_ts_date"] == "1970-01-01"


def test_nap_vao_roi_doc_ra_qua_endpoint_metrics(client, monkeypatch):
    # Vòng khép kín thật: POST /push -> đĩa -> GET /metrics/northstar.
    # Test đơn vị của metrics chạy trên hàng dựng sẵn; test này chứng minh hàng do chính
    # lớp ingestion sinh ra cũng đọc được — hai chuyện khác nhau.
    monkeypatch.setenv("BRAIN_FX_RATES", "EUR=1.08")
    c, _ = client
    c.post("/push", json=DECISION)
    c.post("/push", json=envelope("iap_purchase", {
        "product_id": "premium_monthly", "order_id": "GPA.9", "price_micros": 9990000,
        "currency_code": "EUR", "quantity": 1, "channel": "PLAY_STORE", "is_subscription": 1,
    }))
    c.post("/push", json=envelope("ad_revenue", {
        "ad_format": "interstitial", "ad_platform": "admob", "ad_network": "admob",
        "placement": "full_home", "screen_name": "main",
        "ad_revenue": 0.02, "currency_code": "USD", "ad_id": "x",
    }))
    c.post("/push", json=envelope("brain_comfort", {
        "comfort_value": 0.71, "comfort_coverage": 0.4,
        "components_missing": "organic;refund;retention", "policy_aggressiveness": "",
    }))

    m = c.get("/metrics/northstar").json()
    assert m["revenue"]["iap_usd"] == pytest.approx(10.79, abs=0.01)
    assert m["revenue"]["iaa_usd"] == pytest.approx(0.02)
    assert m["subscription"]["purchase_events"] == 1
    assert m["comfort"]["trustworthy"] is False
    assert m["policy_context"]["decisions"] == 1


def test_metrics_loc_duoc_theo_khoang_ngay(client):
    c, _ = client
    c.post("/push", json=DECISION)   # ts -> 2024-08-24
    m = c.get("/metrics/northstar", params={"date_from": "2030-01-01"}).json()
    assert m["policy_context"]["decisions"] == 0
    assert m["window"]["date_from"] == "2030-01-01"


def test_metrics_cung_bi_kiem_tra_api_key(tmp_path, monkeypatch):
    monkeypatch.setenv("BRAIN_SINK", "jsonl")
    monkeypatch.setenv("BRAIN_JSONL_PATH", str(tmp_path / "e.jsonl"))
    monkeypatch.setenv("BRAIN_API_KEY", "secret")
    import app.config
    import app.main

    importlib.reload(app.config)
    importlib.reload(app.main)
    c = TestClient(app.main.app)
    assert c.get("/metrics/northstar").status_code == 401
