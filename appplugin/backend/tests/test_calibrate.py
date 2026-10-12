"""1.9.0 T9 — `app/calibrate.py` (§Hợp đồng E, D8): artifact hiệu chỉnh = `--base` với `likelihood_profiles` thay.

Chứng minh ĐƯỜNG ỐNG, không chứng minh số (D8 "Không tuyên bố"): hàng tổng hợp dưới đây chỉ để kiểm phép co về
prior (k = 30), ngưỡng hỗ trợ (n < 30 giữ prior), kẹp [0.02, 0.98], `fail_rate` = prior (F7), version tất định và
cây khoá = `DOC/policy_artifact_example.json`. Không mạng, không BigQuery.
"""

from __future__ import annotations

import copy
import importlib
import json
import os
import random
import re
import subprocess
import sys
from pathlib import Path

import pytest

from conftest import FIXTURE_V2

BACKEND = Path(__file__).resolve().parents[1]
EXAMPLE = BACKEND.parent / "DOC" / "policy_artifact_example.json"
CALIBRATED = Path(__file__).parent / "fixtures" / "brain_policy_calibrated.json"
_TS = 1790737201000  # 2026-09-30 10:00:01 +07


def _cal():
    """Import trong thân test: trên mã cũ (chưa có module) mỗi test đỏ bằng lỗi, không phải lỗi thu thập."""
    return importlib.import_module("app.calibrate")


def _base() -> dict:
    return json.loads(EXAMPLE.read_text(encoding="utf-8"))


def _row(name: str, user: str, seq: int, **cols) -> dict:
    return {"event_name": name, "schema_version": 2, "app_id": "demo", "user_id": user,
            "client_ts_ms": _TS + seq * 1000, "dedup_key": f"ev:{user}:{name}:{seq}", **cols}


def _segment_users(n: int, segment: str = "CASUAL_STEADY", conf: float = 0.9, prefix: str = "u") -> list[dict]:
    return [_row("brain_decision", f"{prefix}{i}", i, decision_id=f"d-{prefix}{i}", segment=segment,
                 segment_confidence=conf, is_holdout=0, chosen_action_probability=0.5, eligible_actions="a,b",
                 chosen_action_id="a") for i in range(n)]


def _payers(n: int, prefix: str = "u") -> list[dict]:
    return [_row("iap_purchase", f"{prefix}{i}", 10_000 + i, purchase_token=f"t{prefix}{i}") for i in range(n)]


def _key_tree(v):
    if isinstance(v, dict):
        return {k: _key_tree(x) for k, x in v.items()}
    return type(v).__name__ if not isinstance(v, (int, float)) or isinstance(v, bool) else "number"


def _run_cli(*args: str) -> subprocess.CompletedProcess:
    return subprocess.run([sys.executable, "-m", "app.calibrate", *args], cwd=BACKEND, capture_output=True,
                          text=True, timeout=60, env={**os.environ, "PYTHONPATH": str(BACKEND)})


def test_C1_cay_khoa_bang_artifact_mau(tmp_path):
    """Đầu ra của lệnh trên fixture v2 có cây khoá (đệ quy, cả kiểu lá) đúng bằng `DOC/policy_artifact_example.json`."""
    out = tmp_path / "p.json"
    r = _run_cli("--jsonl", str(FIXTURE_V2), "--base", str(EXAMPLE), "--out", str(out))
    assert r.returncode == 0, r.stderr
    got = json.loads(out.read_text(encoding="utf-8"))
    assert _key_tree(got) == _key_tree(_base())
    assert len(got) == 14 and list(got) == list(_base())


def test_C2_co_ve_prior_k_30():
    """40 người CASUAL_STEADY đều có `iap_purchase`: obs = 1.0, n = 40 -> mu = (40·1 + 30·0.35)/70 = 0.72142857."""
    art, support = _cal().calibrate(_segment_users(40) + _payers(40), _base(), k=30, min_support=30)
    mu = art["likelihood_profiles"]["CASUAL_STEADY"]["payer_propensity"]
    assert mu == pytest.approx((40 * 1.0 + 30 * 0.35) / 70)
    cell = next(s for s in support if s["segment"] == "CASUAL_STEADY" and s["field"] == "payer_propensity")
    assert (cell["n"], cell["obs"], cell["used"]) == (40, 1.0, "calibrated")


def test_C3_o_duoi_30_giu_prior():
    """29 người (< min_support 30): ô giữ đúng prior, `used = prior`; đổi `--min-support 20` thì ô được hiệu chỉnh."""
    cal = _cal()
    rows = _segment_users(29) + _payers(29)
    art, support = cal.calibrate(rows, _base(), k=30, min_support=30)
    assert art["likelihood_profiles"]["CASUAL_STEADY"] == _base()["likelihood_profiles"]["CASUAL_STEADY"]
    cell = next(s for s in support if s["segment"] == "CASUAL_STEADY" and s["field"] == "payer_propensity")
    assert (cell["n"], cell["used"]) == (29, "prior")
    art20, _ = cal.calibrate(rows, _base(), k=30, min_support=20)
    assert art20["likelihood_profiles"]["CASUAL_STEADY"]["payer_propensity"] == pytest.approx(
        (29 + 30 * 0.35) / 59)


def test_C4_kep_002_098():
    """1000 người đều trả tiền -> (1000 + 10.5)/1030 = 0.981 -> 0.98; không ai trả -> 10.5/1030 = 0.0102 -> 0.02."""
    cal = _cal()
    hi, _ = cal.calibrate(_segment_users(1000) + _payers(1000), _base(), k=30, min_support=30)
    assert hi["likelihood_profiles"]["CASUAL_STEADY"]["payer_propensity"] == 0.98
    lo, _ = cal.calibrate(_segment_users(1000), _base(), k=30, min_support=30)
    assert lo["likelihood_profiles"]["CASUAL_STEADY"]["payer_propensity"] == 0.02


def test_C5_version_tat_dinh_theo_sha8_dau_vao():
    """`calib-<yyyymmdd>-<sha8>`: ngày = ngày UTC+7 mới nhất của dữ liệu (không đọc đồng hồ); cùng hàng khác thứ tự
    -> cùng version; đổi một hàng -> sha khác."""
    cal = _cal()
    rows = _segment_users(5) + _payers(3)
    v1 = cal.calibrate(rows, _base())[0]["version"]
    shuffled = rows[:]
    random.Random(7).shuffle(shuffled)
    assert cal.calibrate(shuffled, _base())[0]["version"] == v1
    assert re.fullmatch(r"calib-20260930-[0-9a-f]{8}", v1), v1
    changed = copy.deepcopy(rows)
    changed[0]["segment_confidence"] = 0.95
    assert cal.calibrate(changed, _base())[0]["version"] != v1


def test_C6_fail_rate_luon_la_prior():
    """F7: không có nguồn `fail_rate` ở app utility — kể cả 200 người đủ hỗ trợ, `fail_rate` = prior từng segment."""
    rows = _segment_users(200) + _payers(50)
    art, support = _cal().calibrate(rows, _base(), k=30, min_support=30)
    for seg, prof in _base()["likelihood_profiles"].items():
        assert art["likelihood_profiles"][seg]["fail_rate"] == prof["fail_rate"], seg
    assert all(s["used"] == "prior" for s in support if s["field"] == "fail_rate")


def test_C7_chep_enabled_va_experiment_tu_base():
    """`enabled`, `experiment.*` và mọi khối khác chép nguyên từ base; chỉ `version` và `likelihood_profiles` đổi."""
    base = _base()
    base["enabled"] = False
    base["experiment"].update(rollout_fraction=0.0, holdout_fraction=0.2)
    art, _ = _cal().calibrate(_segment_users(40) + _payers(40), base, k=30, min_support=30)
    assert art["enabled"] is False
    assert art["experiment"] == base["experiment"]
    assert {k: v for k, v in art.items() if k not in ("version", "likelihood_profiles")} == {
        k: v for k, v in base.items() if k not in ("version", "likelihood_profiles")}
    assert base["enabled"] is False and base["version"] == "policy_2026_08_24_reference"  # base không bị sửa


def test_C8_base_thieu_version_la_loi(tmp_path):
    """Base không có `version` (SDK từ chối artifact như vậy, DOC/35) -> thoát khác 0, thông báo nêu `version`, không
    ghi file; nguồn BigQuery production bị từ chối trước khi dựng client."""
    base = _base()
    del base["version"]
    bad = tmp_path / "base.json"
    bad.write_text(json.dumps(base), encoding="utf-8")
    out = tmp_path / "p.json"
    r = _run_cli("--jsonl", str(FIXTURE_V2), "--base", str(bad), "--out", str(out))
    assert r.returncode != 0
    assert "version" in r.stderr
    assert not out.exists()
    r = _run_cli("--bq", "speaker-c7f50.ds.events_raw", "--base", str(EXAMPLE), "--out", str(out))
    assert r.returncode != 0 and "speaker-c7f50" in r.stderr
    assert not out.exists()


def test_C9_jsonl_dau_cuoi(tmp_path, capsys):
    """JSONL hàng sink (như `/push` ghi) lẫn dòng fixture: 35 người tổng hợp trả tiền + u1, u2 của fixture
    (CASUAL_STEADY, không trả tiền; u3 bị bỏ vì quyết định mới nhất có độ tin 0.3) -> n = 37, mu = (35 + 30·0.35)/67;
    ghi artifact + bảng hỗ trợ cạnh `--out`, in bảng ra stdout."""
    rows = _segment_users(35, prefix="s") + _payers(35, prefix="s")
    src = tmp_path / "events.jsonl"
    src.write_text("".join(json.dumps(r) + "\n" for r in rows) + FIXTURE_V2.read_text(encoding="utf-8"),
                   encoding="utf-8")
    out = tmp_path / "nested" / "p.json"
    assert _cal().main(["--jsonl", str(src), "--base", str(EXAMPLE), "--out", str(out)]) == 0
    art = json.loads(out.read_text(encoding="utf-8"))
    assert art["likelihood_profiles"]["CASUAL_STEADY"]["payer_propensity"] == pytest.approx((35 + 30 * 0.35) / 67)
    table = out.with_name("p.support.md").read_text(encoding="utf-8")
    printed = capsys.readouterr().out
    assert "| segment | field | n | obs | prior | mu | used |" in table
    assert "| CASUAL_STEADY | payer_propensity | 37 |" in table and table.strip() in printed
    # u3: quyết định mới nhất LOW_SKILL_HIGH_CHURN_RISK độ tin 0.3 < 0.4 -> không vào ô nào (bỏ lọc thì n = 1).
    assert "| LOW_SKILL_HIGH_CHURN_RISK | payer_propensity | 0 |" in table
    assert "dropped low confidence (< 0.4): 1;" in table


def test_C10_fixture_bang_dau_ra_cua_lenh(tmp_path):
    """`brain_policy_calibrated.json` (T5s đọc qua `PolicyArtifactContractTest`) = đúng byte đầu ra của lệnh trên
    `ad_v2_fixture.jsonl` với base `DOC/policy_artifact_example.json`."""
    out = tmp_path / "p.json"
    r = _run_cli("--jsonl", str(FIXTURE_V2), "--base", str(EXAMPLE), "--out", str(out))
    assert r.returncode == 0, r.stderr
    assert out.read_bytes() == CALIBRATED.read_bytes()
    assert json.loads(CALIBRATED.read_text(encoding="utf-8"))["version"].startswith("calib-20260930-")


def test_C11_loc_min_confidence_cua_base(tmp_path):
    """40 người CASUAL_STEADY trả tiền độ tin 0.3 (< min_confidence 0.4 của base) bị bỏ: ô giữ prior, n = 0; 40 người
    độ tin đúng 0.4 (biên, >=) được tính; base `min_confidence` 0.2 -> cả 80. Người bị bỏ và hàng thiếu `client_ts_ms`
    được đếm trong bảng hỗ trợ, không biến mất im lặng."""
    cal = _cal()
    rows = (_segment_users(40, conf=0.3, prefix="lo") + _payers(40, prefix="lo")
            + _segment_users(40, conf=0.4, prefix="ok") + _payers(40, prefix="ok"))
    art, support = cal.calibrate(rows, _base(), k=30, min_support=30)
    cell = next(s for s in support if s["segment"] == "CASUAL_STEADY" and s["field"] == "payer_propensity")
    assert (cell["n"], cell["obs"]) == (40, 1.0), cell
    lo_only, sup_lo = cal.calibrate(_segment_users(40, conf=0.3) + _payers(40), _base(), k=30, min_support=30)
    assert lo_only["likelihood_profiles"] == _base()["likelihood_profiles"]
    assert all(s["n"] == 0 for s in sup_lo if s["segment"] == "CASUAL_STEADY")
    loose = _base()
    loose["min_confidence"] = 0.2
    assert next(s for s in cal.calibrate(rows, loose)[1]
                if s["segment"] == "CASUAL_STEADY" and s["field"] == "payer_propensity")["n"] == 80
    no_ts = {**_payers(1, prefix="z")[0], "client_ts_ms": None}
    unknown = _row("brain_decision", "x0", 1, decision_id="dx", segment="NOT_A_TYPE", segment_confidence=0.9)
    table = cal.support_table(art, support, rows + [no_ts, unknown], _base())
    assert ("users kept: 40; dropped low confidence (< 0.4): 40; dropped unknown segment: 1; "
            "rows without client_ts_ms dropped: 1") in table


def test_C12_quyet_dinh_moi_nhat_thang():
    """Mỗi người: quyết định cũ HIGH_SKILL_LOW_TOLERANCE, mới hơn CASUAL_STEADY (đưa vào TRƯỚC trong danh sách — thứ tự
    đọc không quyết định) -> chỉ tính ở CASUAL_STEADY: n = 40 ở đó, 0 ở segment cũ."""
    old = _segment_users(40, segment="HIGH_SKILL_LOW_TOLERANCE")
    new = [{**r, "client_ts_ms": r["client_ts_ms"] + 3_600_000, "segment": "CASUAL_STEADY",
            "decision_id": r["decision_id"] + "-new", "dedup_key": r["dedup_key"] + "-new"} for r in old]
    _, support = _cal().calibrate(new + old + _payers(40), _base(), k=30, min_support=30)
    n = {(s["segment"], s["field"]): s["n"] for s in support}
    assert n[("CASUAL_STEADY", "payer_propensity")] == 40
    assert n[("HIGH_SKILL_LOW_TOLERANCE", "payer_propensity")] == 0


def test_C13_rewarded_accept_va_churn_tren_hang_biet_truoc():
    """40 người CASUAL_STEADY. rewarded: mỗi người 2 attempt rewarded; 20 người 2 show (1.0), 20 người 1 show (0.5) ->
    obs 0.75, mu = (40·0.75 + 30·0.5)/70 = 0.642857; attempt interstitial (không có show) bị bỏ (chỉ rewarded).
    churn: mốc cuối dữ liệu = T + 5 ngày; 10 người phiên T và T+5d (khoảng 5d > 3d) + 5 người chỉ phiên T (tới mốc cuối
    5d) = 1, 25 người phiên T+4d và T+5d = 0 -> obs 15/40 = 0.375, mu = (15 + 30·0.35)/70 = 0.364286."""
    day = 86_400_000
    users = _segment_users(40)
    ev = []
    for i in range(40):
        u = f"u{i}"
        ev += [_row("ad_show_attempt", u, 100 + j, ad_format="rewarded") for j in range(2)]
        ev += [_row("ad_show", u, 200 + j, ad_format="rewarded") for j in range(2 if i < 20 else 1)]
        ev.append(_row("ad_show_attempt", u, 300, ad_format="interstitial"))
        gaps = [0, 5 * day] if i < 10 else [0] if i < 15 else [4 * day, 5 * day]
        ev += [{**_row("session_end", u, 400 + j), "client_ts_ms": _TS + g} for j, g in enumerate(gaps)]
    art, support = _cal().calibrate(users + ev, _base(), k=30, min_support=30)
    cell = {s["field"]: s for s in support if s["segment"] == "CASUAL_STEADY"}
    assert (cell["rewarded_accept"]["n"], cell["rewarded_accept"]["obs"]) == (40, 0.75)
    assert (cell["churn_tendency"]["n"], cell["churn_tendency"]["obs"]) == (40, 0.375)
    prof = art["likelihood_profiles"]["CASUAL_STEADY"]
    assert prof["rewarded_accept"] == pytest.approx((40 * 0.75 + 30 * 0.5) / 70)
    assert prof["churn_tendency"] == pytest.approx((40 * 0.375 + 30 * 0.35) / 70)
    assert art["version"].startswith("calib-20261005-")  # ngày dữ liệu mới nhất = T + 5 ngày (UTC+7)
