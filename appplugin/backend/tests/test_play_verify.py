"""Test xác thực giao dịch Play Billing phía server (doc 45 mục E.1, `play_verify.py`).

Vế quyết định của `compute()` được khoá bằng `StubVerifier` — không cần credential thật.
`HttpPlayVerifier` được test phần dựng URL / đọc đáp án bằng cách tiêm `fetch` và thay
google-auth bằng module giả; phần trao đổi credential thật chỉ xác thực khi deploy với
service account (extra `playverify`).
"""

from __future__ import annotations

import importlib
import sys
import types

import pytest
from fastapi.testclient import TestClient

from app import play_verify
from app.play_verify import HttpPlayVerifier, PlayReceipt, StubVerifier, compute


# ---------------------------------------------------------------------------
# Hàng iap_purchase chuẩn (đúng hình normalize.py sinh ra) và biên nhận Google.
# ---------------------------------------------------------------------------

def purchase_row(user="u_1", order_id="GPA.1111-2222", product_id="gems_100",
                 channel="PLAY_STORE", token="tok_abc", is_subscription=0):
    return {
        "event_name": "iap_purchase",
        "user_id": user,
        "order_id": order_id,
        "product_id": product_id,
        "channel": channel,
        "purchase_token": token,
        "is_subscription": is_subscription,
    }


def receipt(order_id="GPA.1111-2222", state=play_verify.PURCHASED):
    return PlayReceipt(order_id=order_id, purchase_state=state,
                       consumption_state=0, acknowledgement_state=1)


def test_hang_khop_google_verdict_clean():
    stub = StubVerifier({"tok_abc": receipt()})
    result = compute([purchase_row()], stub)
    assert result["verdict"] == "clean"
    assert result["total_purchase_rows"] == 1
    assert result["play_rows_checked_against_google"] == 1
    assert result["counts"]["verified"] == 1
    assert stub.calls == [("gems_100", "tok_abc", False)]


def test_token_google_khong_biet_la_invalid():
    stub = StubVerifier({})  # Google không biết token này
    result = compute([purchase_row()], stub)
    assert result["verdict"] == "issues_found"
    assert result["counts"]["invalid_token"] == 1


def test_giao_dich_da_hoan_tien():
    stub = StubVerifier({"tok_abc": receipt(state=play_verify.CANCELED)})
    result = compute([purchase_row()], stub)
    assert result["verdict"] == "issues_found"
    assert result["counts"]["canceled_or_refunded"] == 1


def test_order_mismatch_keo_theo_order_id_cua_google():
    # Token thật nhưng là biên nhận của ĐƠN KHÁC gắn vào đơn này.
    stub = StubVerifier({"tok_abc": receipt(order_id="GPA.9999-0000")})
    result = compute([purchase_row(order_id="GPA.1111-2222")], stub)
    assert result["verdict"] == "issues_found"
    assert result["counts"]["order_mismatch"] == 1
    assert result["examples"]["order_mismatch"][0]["google_order_id"] == "GPA.9999-0000"


def test_pending_chua_tin_nhung_khong_la_van_de():
    # purchaseState=2 (chờ thanh toán) không cấp quyền lợi, nhưng không phải gian lận.
    stub = StubVerifier({"tok_abc": receipt(state=play_verify.PENDING)})
    result = compute([purchase_row()], stub)
    assert result["counts"]["pending"] == 1
    assert result["verdict"] == "clean"


def test_web_shop_di_duong_xac_thuc_khac():
    # Kênh web-shop xác thực qua server studio gọi grantFromServer, không qua module này.
    stub = StubVerifier({})
    result = compute([purchase_row(channel="WEB_SHOP", token="")], stub)
    assert result["counts"]["skipped_non_play"] == 1
    assert result["play_rows_checked_against_google"] == 0
    assert stub.calls == []  # KHÔNG hỏi Google về hàng không phải Play Store


def test_hang_play_thieu_token_la_do_phu():
    # Bản build SDK cũ chưa gửi purchase_token — không kết tội, chỉ đo độ phủ.
    stub = StubVerifier({})
    result = compute([purchase_row(token="")], stub)
    assert result["counts"]["unverifiable_no_token"] == 1
    assert stub.calls == []


def test_event_khac_iap_bị_bo_qua():
    result = compute([{"event_name": "ad_revenue", "user_id": "u_1"}], StubVerifier({}))
    assert result["total_purchase_rows"] == 0
    assert result["verdict"] == "no_data"


def test_subscription_duoc_dinh_tuyen_dung():
    stub = StubVerifier({"tok_sub": receipt(order_id="GPA.5555-6666")})
    result = compute([purchase_row(order_id="GPA.5555-6666", token="tok_sub",
                                   is_subscription=1)], stub)
    assert result["counts"]["verified"] == 1
    # is_subscription phải tới tai verifier — nó quyết định nhánh products/subscriptions.
    assert stub.calls == [("gems_100", "tok_sub", True)]


def test_vi_du_bi_chan_o_5():
    rows = [purchase_row(user=f"u_{i}", token=f"tok_{i}") for i in range(10)]
    result = compute(rows, StubVerifier({}))
    assert result["counts"]["invalid_token"] == 10
    assert len(result["examples"]["invalid_token"]) == 5


# ---------------------------------------------------------------------------
# HttpPlayVerifier — cấu hình và adapter (không cần credential thật).
# ---------------------------------------------------------------------------

def test_thieu_package_thi_valueerror():
    # Thiếu tên gói thì URL androidpublisher không dựng được — nổ ngay lúc dựng adapter.
    with pytest.raises(ValueError, match="BRAIN_PLAY_PACKAGE"):
        HttpPlayVerifier("", "creds.json")


def test_thieu_google_auth_bao_loi_ro_rang():
    try:
        import google.oauth2.service_account  # noqa: F401
        pytest.skip("google-auth đã cài — nhánh ImportError không chạm được")
    except ImportError:
        pass
    with pytest.raises(RuntimeError, match="playverify"):
        HttpPlayVerifier("com.example.app", "creds.json")


@pytest.fixture()
def fake_google_auth(monkeypatch):
    """Thay google-auth bằng module giả — đủ để dựng adapter mà không cài extra."""
    captured: dict = {}

    class _Creds:
        token = "fake-access-token"

        @staticmethod
        def from_service_account_file(path, scopes=None):
            captured["path"] = path
            captured["scopes"] = scopes
            return _Creds()

        def refresh(self, transport):
            captured["refreshed"] = True

    sa = types.ModuleType("google.oauth2.service_account")
    sa.Credentials = _Creds
    oauth2 = types.ModuleType("google.oauth2")
    oauth2.service_account = sa
    google = types.ModuleType("google")
    google.oauth2 = oauth2
    monkeypatch.setitem(sys.modules, "google", google)
    monkeypatch.setitem(sys.modules, "google.oauth2", oauth2)
    monkeypatch.setitem(sys.modules, "google.oauth2.service_account", sa)
    return captured


def test_http_verifier_dung_url_products_va_doc_dap_an(fake_google_auth):
    calls = []

    def fetch(url, bearer):
        calls.append((url, bearer))
        return 200, {"orderId": "GPA.1111-2222", "purchaseState": 0,
                     "consumptionState": 1, "acknowledgementState": 1}

    v = HttpPlayVerifier("com.example.app", "creds.json", fetch=fetch)
    r = v.lookup("gems_100", "tok_abc", is_subscription=False)
    assert calls[0][0] == (
        "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/"
        "com.example.app/purchases/products/gems_100/tokens/tok_abc"
    )
    assert calls[0][1] == "fake-access-token"
    assert fake_google_auth["refreshed"] is True
    assert "androidpublisher" in fake_google_auth["scopes"][0]
    assert r == PlayReceipt("GPA.1111-2222", 0, 1, 1)


def test_http_verifier_subscription_di_nhanh_subscriptions(fake_google_auth):
    calls = []
    v = HttpPlayVerifier("com.example.app", "creds.json",
                         fetch=lambda url, bearer: calls.append(url) or (
                             200, {"orderId": "GPA.5555-6666", "purchaseState": 0}))
    v.lookup("vip_monthly", "tok_sub", is_subscription=True)
    assert "/purchases/subscriptions/vip_monthly/tokens/tok_sub" in calls[0]


def test_http_verifier_404_tra_none(fake_google_auth):
    v = HttpPlayVerifier("com.example.app", "creds.json",
                         fetch=lambda url, bearer: (404, None))
    assert v.lookup("gems_100", "tok_gia", is_subscription=False) is None


# ---------------------------------------------------------------------------
# Endpoint — chưa cấu hình thì 503 nói thẳng, KHÔNG trả kết quả giả.
# ---------------------------------------------------------------------------

@pytest.fixture()
def client(tmp_path, monkeypatch):
    monkeypatch.setenv("BRAIN_SINK", "jsonl")
    monkeypatch.setenv("BRAIN_JSONL_PATH", str(tmp_path / "events.jsonl"))
    monkeypatch.delenv("BRAIN_API_KEY", raising=False)
    monkeypatch.delenv("BRAIN_PLAY_CREDENTIALS", raising=False)
    monkeypatch.delenv("BRAIN_PLAY_PACKAGE", raising=False)
    import app.config
    import app.main

    importlib.reload(app.config)
    importlib.reload(app.main)
    return TestClient(app.main.app)


def envelope(event_name: str, event_data: dict) -> dict:
    return {
        "sequenceId": 1,
        "eventName": event_name,
        "eventData": event_data,
        "ts": 1724500000000,
        "userProperty": {"user_id": "u_1", "platform": "android"},
    }


def test_endpoint_503_khi_chua_cau_hinh(client):
    resp = client.get("/verify/purchases")
    assert resp.status_code == 503
    assert "BRAIN_PLAY_CREDENTIALS" in resp.json()["detail"]


def test_endpoint_xac_thuc_tren_log_that(client, monkeypatch):
    import app.main

    client.post("/push", json=envelope("iap_purchase", {
        "product_id": "gems_100",
        "order_id": "GPA.3333-4444",
        "price_micros": 990000,
        "currency_code": "USD",
        "quantity": 1,
        "channel": "PLAY_STORE",
        "is_subscription": 0,
        "purchase_token": "tok_live_1",
    }))
    # 1.7.0 T9: `create_app(settings)` giữ verifier ở `app.state`.
    monkeypatch.setattr(app.main.app.state, "verifier",
                        StubVerifier({"tok_live_1": receipt(order_id="GPA.3333-4444")}))
    resp = client.get("/verify/purchases")
    assert resp.status_code == 200
    body = resp.json()
    assert body["verdict"] == "clean"
    assert body["counts"]["verified"] == 1
    assert body["window"] == {"date_from": None, "date_to": None}
