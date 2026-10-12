"""Test job đối soát bucket thí nghiệm (doc 57 mục B.2 quyết định 2, `reconcile.py`).

Trọng tâm là khoá parity chéo ngôn ngữ: `fnv1a_bucket` ở đây phải ra ĐÚNG kết quả
của `HoldoutManager.bucketWith` (Kotlin). Bộ vector cứng trong test này trùng từng số
với `HoldoutParityVectorsTest` phía SDK — nếu một bên đổi thuật toán, bên kia bắt được.
"""

from __future__ import annotations

import importlib
import json

import pytest
from fastapi.testclient import TestClient

from app import reconcile

SALT = "brain_holdout_v1"


# ---------------------------------------------------------------------------
# Khoá parity chéo ngôn ngữ — giá trị đã xác nhận bằng test Kotlin chạy thật.
# ---------------------------------------------------------------------------

PARITY_VECTORS = [
    (SALT, "user_1", 6924),
    (SALT, "user_2", 9781),
    (SALT, "player-abc-123", 5350),
    ("brain_holdout_v2", "user_1", 735),
    (SALT + "|rollout", "user_1", 7683),
    ("paywall_variant", "user_1", 7074),
]


def test_fnv1a_trung_khop_voi_sdk_kotlin():
    for namespace, player_id, expected in PARITY_VECTORS:
        assert reconcile.fnv1a_bucket(namespace, player_id) == expected, (namespace, player_id)


def test_fnv1a_bucket_luon_trong_khoang():
    for i in range(2000):
        b = reconcile.fnv1a_bucket(SALT, f"user_{i}")
        assert 0 <= b < reconcile.BUCKET_COUNT


# ---------------------------------------------------------------------------
# Tiện ích xây hàng exposure nhất quán — bucket và cờ suy từ chính hàm băm,
# đúng như SDK làm lúc exposure, nên đây là dữ liệu "sạch" chuẩn.
# ---------------------------------------------------------------------------

def exposure_row(user, salt=SALT, holdout_fraction=0.2, rollout_fraction=0.5,
                 corrupt_bucket=None, override_holdout=None, override_rollout=None):
    bucket = reconcile.fnv1a_bucket(salt, user)
    rollout_bucket = reconcile.fnv1a_bucket(f"{salt}|rollout", user)
    return {
        "event_name": "experiment_exposure",
        "user_id": user,
        "holdout_bucket": corrupt_bucket if corrupt_bucket is not None else bucket,
        "is_holdout": override_holdout if override_holdout is not None
        else (1 if bucket < int(holdout_fraction * reconcile.BUCKET_COUNT) else 0),
        "in_rollout": override_rollout if override_rollout is not None
        else (1 if rollout_bucket < int(rollout_fraction * reconcile.BUCKET_COUNT) else 0),
        "salt_version": salt,
    }


def clean_rows(n=50):
    return [exposure_row(f"player_{i}") for i in range(n)]


def test_du_lieu_sach_verdict_clean():
    result = reconcile.compute(clean_rows())
    assert result["verdict"] == "clean"
    assert result["total_exposure_rows"] == 50
    assert result["rows_without_required_fields"] == 0
    g = result["by_salt_version"][SALT]
    assert g["bucket_mismatches"] == 0
    assert g["holdout"]["monotonicity_violations"] == 0
    assert g["rollout"]["monotonicity_violations"] == 0
    # Không có fraction → lớp 3 không chạy.
    assert result["flag_checks"] is None


def test_lech_bucket_la_hong_du_lieu():
    rows = clean_rows()
    rows[7] = exposure_row("player_7", corrupt_bucket=rows[7]["holdout_bucket"] + 1)
    result = reconcile.compute(rows)
    assert result["verdict"] == "corruption_detected"
    g = result["by_salt_version"][SALT]
    assert g["bucket_mismatches"] == 1
    assert g["bucket_mismatch_examples"][0]["user_id"] == "player_7"


def test_co_tu_mau_thuan_phat_hien_khong_can_fraction():
    # Bucket vẫn đúng (lớp 1 sạch) nhưng một người nhóm holdout bị khai cờ 0:
    # cờ không còn là hàm bậc thang của bucket — lớp 2 phải bắt được.
    victim = next(f"player_{i}" for i in range(50)
                  if reconcile.fnv1a_bucket(SALT, f"player_{i}") < 2000)
    rows = clean_rows()
    for i, r in enumerate(rows):
        if r["user_id"] == victim:
            rows[i] = exposure_row(victim, override_holdout=0)
    result = reconcile.compute(rows)
    assert result["verdict"] == "inconsistent"
    g = result["by_salt_version"][SALT]
    assert g["bucket_mismatches"] == 0
    assert g["holdout"]["monotonicity_violations"] >= 1
    assert g["rollout"]["monotonicity_violations"] == 0


def test_kiem_tra_co_chinh_xac_khi_co_fraction():
    # user_1: bucket holdout 6924, bucket rollout 7683 (vector parity).
    # Log cờ ĐÚNG với fraction thật (0.2 / 0.5) rồi hỏi với fraction SAI (0.8):
    # hàng bucket 6924 phải được mong đợi là holdout dưới ngưỡng 8000 → lệch is_holdout.
    rows = [exposure_row("user_1", holdout_fraction=0.2, rollout_fraction=0.5)]
    result = reconcile.compute(rows, holdout_fraction=0.8, rollout_fraction=0.5)
    assert result["flag_checks"]["is_holdout_mismatches"] == 1
    assert result["flag_checks"]["in_rollout_mismatches"] == 0
    assert result["verdict"] == "inconsistent"
    # Fraction đúng phải sạch hoàn toàn.
    result2 = reconcile.compute(rows, holdout_fraction=0.2, rollout_fraction=0.5)
    assert result2["flag_checks"]["is_holdout_mismatches"] == 0
    assert result2["flag_checks"]["in_rollout_mismatches"] == 0
    assert result2["verdict"] == "clean"


def test_nguong_ngam_dinh_dung_bien_duoi():
    rows = clean_rows(300)
    result = reconcile.compute(rows)
    g = result["by_salt_version"][SALT]
    # Với fraction thật 0.2, ngưỡng ngầm định không được vượt 2000 (cận dưới: bucket
    # cờ-1 cao nhất quan sát được + 1), và fraction ngầm định ≤ fraction thật.
    assert g["holdout"]["implied_threshold"] <= 2000
    assert g["holdout"]["implied_fraction"] <= 0.2


def test_hang_thieu_truong_khong_lam_sup_va_bach_rieng():
    rows = clean_rows(3) + [
        {"event_name": "experiment_exposure", "user_id": "", "salt_version": SALT,
         "holdout_bucket": 100, "is_holdout": 0, "in_rollout": 1},
        {"event_name": "experiment_exposure", "user_id": "x", "salt_version": "",
         "holdout_bucket": 100, "is_holdout": 0, "in_rollout": 1},
        {"event_name": "brain_decision", "user_id": "y"},  # event khác — bỏ qua hẳn
    ]
    result = reconcile.compute(rows)
    assert result["rows_without_required_fields"] == 2
    assert result["checkable_rows"] == 3
    assert result["verdict"] == "clean"


def test_khong_co_du_lieu():
    result = reconcile.compute([])
    assert result["verdict"] == "no_data"
    assert result["by_salt_version"] == {}


# ---------------------------------------------------------------------------
# Endpoint — cùng pattern fixture với test_experiment_exposure.
# ---------------------------------------------------------------------------

def envelope(user, bucket, is_holdout, in_rollout, salt=SALT):
    return {
        "sequenceId": 1,
        "eventName": "experiment_exposure",
        "eventData": {
            "experiment_id": "monetization_brain",
            "holdout_bucket": bucket,
            "is_holdout": is_holdout,
            "in_rollout": in_rollout,
            "salt_version": salt,
        },
        "ts": 1724500000000,
        "userProperty": {"user_id": user, "platform": "android"},
    }


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


def test_endpoint_doi_soat_tren_du_lieu_that(client):
    # SDK thật gửi bucket suy từ hàm băm — push ba hàng nhất quán rồi đối soát.
    for user in ("u_1", "u_2", "u_3"):
        b = reconcile.fnv1a_bucket(SALT, user)
        rb = reconcile.fnv1a_bucket(f"{SALT}|rollout", user)
        resp = client.post("/push", json=envelope(
            user, bucket=b, is_holdout=1 if b < 2000 else 0,
            in_rollout=1 if rb < 5000 else 0))
        assert resp.status_code == 200
    resp = client.get("/reconcile/buckets",
                      params={"holdout_fraction": 0.2, "rollout_fraction": 0.5})
    assert resp.status_code == 200
    body = resp.json()
    assert body["verdict"] == "clean"
    assert body["total_exposure_rows"] == 3
    assert body["window"] == {"date_from": None, "date_to": None}
    assert body["flag_checks"]["is_holdout_mismatches"] == 0


def test_endpoint_phat_hien_hong_qua_http(client):
    user = "u_1"
    b = reconcile.fnv1a_bucket(SALT, user)
    client.post("/push", json=envelope(user, bucket=b + 1, is_holdout=0, in_rollout=1))
    resp = client.get("/reconcile/buckets")
    assert resp.status_code == 200
    assert resp.json()["verdict"] == "corruption_detected"
