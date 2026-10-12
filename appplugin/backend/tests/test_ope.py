"""Off-policy evaluation phía server (doc 28 mục H.1, doc 46 mục B.1).

Hai thứ được khoá ở đây:

  1. Số học của IPS/SNIPS — tính tay trên một bộ dữ liệu nhỏ, không tin công thức tự chạy.
  2. Hợp đồng `reward_value`: "vắng mặt" (bản ghi cũ, hoặc feedback chưa chốt reward) phải
     bị loại khỏi ước lượng, còn 0.0 — reward hợp lệ khi người chơi bỏ game sau quảng cáo —
     phải được tính. Nhầm hai thứ này thì IPS lệch theo hướng không bao giờ báo lỗi.
"""

from __future__ import annotations

import importlib

import pytest
from fastapi.testclient import TestClient

from app import ope


# ---------------------------------------------------------------------------
# Hàng chuẩn (đúng hình normalize.py sinh ra).
# ---------------------------------------------------------------------------

def decision_row(decision_id, chosen="inter", propensity=0.5,
                 eligible="no_action,inter", is_holdout=0):
    return {
        "event_name": "brain_decision",
        "user_id": "u_1",
        "decision_id": decision_id,
        "chosen_action_id": chosen,
        "chosen_action_probability": propensity,
        "eligible_actions": eligible,
        "is_holdout": is_holdout,
    }


def feedback_row(decision_id, reward=0.6):
    row = {
        "event_name": "brain_feedback",
        "user_id": "u_1",
        "decision_id": decision_id,
        "outcome_event": "outcome_reward",
        "outcome_timestamp_ms": 1724500000000,
    }
    # reward=None nghĩa là SDK KHÔNG gửi trường reward_value — phải giữ nguyên sự vắng mặt.
    if reward is not None:
        row["reward_value"] = reward
    return row


# ---------------------------------------------------------------------------
# Số học IPS/SNIPS — tính tay.
# ---------------------------------------------------------------------------

def test_ips_va_snips_khop_tinh_tay():
    # uniform: cả hai hàng đều có π_e = 1/2, propensity 0.5 → w = 1.
    #   IPS  = (1.0 + 0.6)/2 = 0.8;  SNIPS = 1.6/2.0 = 0.8.
    # always_no_action: hàng inter có π_e = 0 → w = 0; hàng no_action có π_e = 1 → w = 2.
    #   IPS = (0 + 2*0.6)/2 = 0.6;  SNIPS = 1.2/2.0 = 0.6.
    rows = [
        decision_row("d1", chosen="inter"), feedback_row("d1", reward=1.0),
        decision_row("d2", chosen="no_action"), feedback_row("d2", reward=0.6),
    ]
    result = ope.compute(rows)
    assert result["policies"]["uniform"]["ips"] == pytest.approx(0.8)
    assert result["policies"]["uniform"]["snips"] == pytest.approx(0.8)
    assert result["policies"]["always_no_action"]["ips"] == pytest.approx(0.6)
    assert result["policies"]["always_no_action"]["snips"] == pytest.approx(0.6)
    assert result["policies"]["uniform"]["weights_clipped"] == 0


def test_chon_hanh_dong_ngoai_tap_ung_vien_dong_gop_khong():
    # Bản ghi lỗi phía client: chosen không nằm trong eligible. π_e(uniform) = 0 → hàng
    # này không được tính vào reward, nhưng vẫn nằm trong mẫu số của IPS.
    rows = [
        decision_row("d1", chosen="khong_co_trong_danh_sach"),
        feedback_row("d1", reward=1.0),
    ]
    result = ope.compute(rows)
    assert result["policies"]["uniform"]["ips"] == pytest.approx(0.0)
    assert result["policies"]["uniform"]["snips"] is None  # Σw = 0


def test_kep_trong_so_khi_propensity_qua_nho():
    # propensity 0.01 → w = 100, phải kẹp về 10. Không kẹp thì một hàng duy nhất
    # chi phối toàn bộ ước lượng.
    rows = [decision_row("d1", propensity=0.01), feedback_row("d1", reward=1.0)]
    result = ope.compute(rows)
    pol = result["policies"]["uniform"]
    assert pol["weights_clipped"] == 1
    assert pol["ips"] == pytest.approx(10.0 * 1.0)  # w kẹp × reward


# ---------------------------------------------------------------------------
# Hợp đồng reward_value: vắng mặt ≠ 0.0.
# ---------------------------------------------------------------------------

def test_feedback_thieu_reward_khong_duoc_tinh():
    # Bản ghi SDK cũ (chưa gửi reward_value) phải bị loại, không được hiểu là reward 0.
    rows = [
        decision_row("d1"), feedback_row("d1", reward=None),
        decision_row("d2"), feedback_row("d2", reward=0.8),
    ]
    result = ope.compute(rows)
    assert result["counts"]["decisions_without_reward"] == 1
    assert result["counts"]["matched_decisions"] == 1
    assert result["counts"]["feedback_with_reward"] == 1


def test_reward_bang_khong_la_gia_tri_hop_le():
    # Quảng cáo hiện xong người chơi rời game — reward 0.00 theo RewardModel. Nếu nhầm
    # 0.0 với "thiếu", mọi hàng tệ nhất lại biến mất khỏi ước lượng một cách im lặng.
    rows = [decision_row("d1"), feedback_row("d1", reward=0.0)]
    result = ope.compute(rows)
    assert result["counts"]["matched_decisions"] == 1
    assert result["policies"]["uniform"]["ips"] == pytest.approx(0.0)


# ---------------------------------------------------------------------------
# Vệ sinh dữ liệu.
# ---------------------------------------------------------------------------

def test_propensity_bang_khong_la_hong_khong_la_ket_qua():
    rows = [decision_row("d1", propensity=0.0), feedback_row("d1", reward=1.0)]
    result = ope.compute(rows)
    assert result["counts"]["unusable_propensity"] == 1
    assert result["counts"]["matched_decisions"] == 0


def test_eligible_rong_bao_unparseable():
    rows = [decision_row("d1", eligible=""), feedback_row("d1", reward=1.0)]
    result = ope.compute(rows)
    assert result["counts"]["unparseable_eligible"] == 1
    assert result["counts"]["matched_decisions"] == 0


def test_hang_holdout_duoc_tinh_vao_uoc_luong():
    # Holdout tất định nên propensity log đúng 1.0 — mẫu hợp lệ của policy ghi log.
    rows = [decision_row("d1", chosen="no_action", propensity=1.0, is_holdout=1),
            feedback_row("d1", reward=0.6)]
    result = ope.compute(rows)
    assert result["counts"]["holdout_rows"] == 1
    assert result["counts"]["matched_decisions"] == 1
    # always_no_action: π_e = 1, w = 1/1 = 1 → IPS = SNIPS = 0.6.
    assert result["policies"]["always_no_action"]["snips"] == pytest.approx(0.6)


def test_event_khac_lien_quan_bi_bo_qua():
    rows = [{"event_name": "ad_revenue", "user_id": "u_1"},
            {"event_name": "iap_purchase", "user_id": "u_1"}]
    result = ope.compute(rows)
    assert result["verdict"] == "no_data"
    assert result["counts"]["decision_rows"] == 0


def test_feedback_truoc_decision_van_khop():
    # Hàng đợi replay có thể đưa feedback tới TRƯỚC decision cùng lô — ghép theo id,
    # không theo thứ tự.
    rows = [feedback_row("d1", reward=1.0), decision_row("d1")]
    result = ope.compute(rows)
    assert result["counts"]["matched_decisions"] == 1


# ---------------------------------------------------------------------------
# Verdict theo lượng dữ liệu.
# ---------------------------------------------------------------------------

def test_verdict_ok_khi_du_ban_ghi():
    rows = []
    for i in range(5):  # đúng ngưỡng _MIN_MATCHED
        rows.append(decision_row(f"d{i}"))
        rows.append(feedback_row(f"d{i}", reward=0.5))
    result = ope.compute(rows)
    assert result["verdict"] == "ok"


def test_verdict_too_few_matches_duoi_nguong():
    rows = []
    for i in range(4):
        rows.append(decision_row(f"d{i}"))
        rows.append(feedback_row(f"d{i}", reward=0.5))
    result = ope.compute(rows)
    assert result["verdict"] == "too_few_matches"
    assert result["min_matched"] == 5


# ---------------------------------------------------------------------------
# Endpoint — toàn đường ống /push → normalize → compute.
# ---------------------------------------------------------------------------

@pytest.fixture()
def client(tmp_path, monkeypatch):
    monkeypatch.setenv("BRAIN_SINK", "jsonl")
    monkeypatch.setenv("BRAIN_JSONL_PATH", str(tmp_path / "events.jsonl"))
    monkeypatch.delenv("BRAIN_API_KEY", raising=False)
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


def test_endpoint_toan_duong_ong_push_toi_ope(client):
    for i in range(5):
        client.post("/push", json=envelope("brain_decision", {
            "decision_id": f"d_{i}",
            "decision_point": "interstitial_opportunity",
            "chosen_action_id": "inter" if i % 2 else "no_action",
            "chosen_action_mode": "INTERSTITIAL" if i % 2 else "NO_ACTION",
            "chosen_action_probability": 0.5,
            "eligible_actions": "no_action,inter",
            "policy_version": "v1",
            "is_holdout": 0,
        }))
        client.post("/push", json=envelope("brain_feedback", {
            "decision_id": f"d_{i}",
            "outcome_event": "outcome_reward",
            "outcome_timestamp_ms": 1724500010000,
            "reward_value": 0.6,
        }))
    resp = client.get("/ope/policies")
    assert resp.status_code == 200
    body = resp.json()
    assert body["verdict"] == "ok"
    assert body["counts"]["matched_decisions"] == 5
    assert body["window"] == {"date_from": None, "date_to": None}
    assert "uniform" in body["policies"]
    assert "always_no_action" in body["policies"]


def test_endpoint_feedback_khong_reward_value_bi_loai_khoi_uoc_luong(client):
    # Khoá hợp đồng nullable QUA normalize: push không kèm reward_value (hành vi của bản
    # SDK cũ) thì hàng feedback vẫn được lưu, nhưng KHÔNG được tính là có reward.
    client.post("/push", json=envelope("brain_decision", {
        "decision_id": "d_old",
        "chosen_action_id": "inter",
        "chosen_action_probability": 0.5,
        "eligible_actions": "no_action,inter",
        "is_holdout": 0,
    }))
    client.post("/push", json=envelope("brain_feedback", {
        "decision_id": "d_old",
        "outcome_event": "outcome_reward",
        "outcome_timestamp_ms": 1724500010000,
    }))
    resp = client.get("/ope/policies")
    body = resp.json()
    assert body["counts"]["feedback_rows"] == 1
    assert body["counts"]["feedback_with_reward"] == 0
    assert body["counts"]["decisions_without_reward"] == 1
    assert body["verdict"] == "too_few_matches"


def test_endpoint_reward_bang_khong_van_di_het_duong_ong(client):
    client.post("/push", json=envelope("brain_decision", {
        "decision_id": "d_zero",
        "chosen_action_id": "inter",
        "chosen_action_probability": 0.5,
        "eligible_actions": "no_action,inter",
        "is_holdout": 0,
    }))
    client.post("/push", json=envelope("brain_feedback", {
        "decision_id": "d_zero",
        "outcome_event": "outcome_reward",
        "outcome_timestamp_ms": 1724500010000,
        "reward_value": 0.0,
    }))
    body = client.get("/ope/policies").json()
    # 0.0 không được rơi vào decisions_without_reward — đó là reward hợp lệ.
    assert body["counts"]["feedback_with_reward"] == 1
    assert body["counts"]["matched_decisions"] == 1
