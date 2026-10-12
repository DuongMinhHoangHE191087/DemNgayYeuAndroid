"""1.7.0 T10 — báo cáo ngày (`app/daily_report.py`, gọi qua `tools/report_daily.sh`). Không mạng."""

from __future__ import annotations

import pytest

from app import daily_report as dr

_VIEWS = ("v_ecpm_format_tier", "v_imp_per_dau_format", "v_arpdau_ads_country", "v_fill_rate",
          "v_show_rate", "v_consent_funnel_nondenied", "v_retention_d1_d7_holdout",
          "v_arpdau_country", "v_paywall_funnel", "v_brain_decisions", "v_ecpm_network_format",
          "v_show_rate_reasons", "v_show_rate_views")


def _meta():
    return {"source": "bigquery-emulator local-test.monetization_brain", "rows_total": 33,
            "schema_versions": {2: 32, None: 1}}


def test_bao_cao_du_7_bang_nguon_so_hang_va_phan_bo_schema():
    # Tên giữ từ 1.7.0 (đổi tên làm lệch số test suy ra #27); từ 1.9.0 T9 là đủ 11 bảng.
    tables = {v: [{"app": "demo", "n": 1}] for v in _VIEWS}
    md = dr.render("2026-09-30", tables, _meta())
    assert md.startswith("# Báo cáo ngày 2026-09-30")
    for v in _VIEWS:
        assert f"## {v}" in md
    assert md.count("| app | n |") == len(_VIEWS) == 13
    assert "bigquery-emulator local-test.monetization_brain" in md
    assert "33" in md and "schema_version=2: 32" in md and "schema_version=NULL: 1" in md


def test_bang_rong_ghi_ro_0_hang():
    tables = {v: [] for v in _VIEWS}
    md = dr.render("2026-09-30", tables, _meta())
    assert md.count("0 hàng") == len(_VIEWS)


def test_B7_bao_cao_9_bang_moi_bang_co_tieu_de():
    """Tên giữ từ 1.8.0 T12 (#27); 1.9.0 T9: 11 bảng = 9 + `v_brain_decisions` + `v_ecpm_network_format`; view không
    có tiêu đề là `KeyError`."""
    assert len(dr.VIEW_ORDER) == 13, len(dr.VIEW_ORDER)
    assert set(dr.VIEW_ORDER) == set(_VIEWS)
    md = dr.render("2026-09-30", {v: [] for v in dr.VIEW_ORDER}, _meta())
    for v in ("v_arpdau_country", "v_paywall_funnel", "v_brain_decisions", "v_ecpm_network_format"):
        assert f"## {v}" in md and dr._TITLES[v] in md


def test_thieu_view_la_loi_khong_im_lang():
    with pytest.raises(ValueError):
        dr.render("2026-09-30", {"v_fill_rate": []}, _meta())


def test_ngay_sai_dinh_dang_bi_tu_choi():
    assert dr.parse_day("2026-09-30") == "2026-09-30"
    for bad in ("2026-9-30", "2026-09-30' OR 1=1 --", "", "2026-13-01"):
        with pytest.raises(ValueError):
            dr.parse_day(bad)


class _NoCallClient:
    def query(self, sql, job_config=None):  # không được gọi khi định danh xấu
        raise AssertionError(f"không được gửi SQL: {sql}")


def test_fetch_dataset_xau_bi_tu_choi_truoc_khi_gui_sql():
    from app.bq_tables import InvalidIdentifier

    for bad in ("ds; DROP TABLE x", "`ds`", "other-proj.ds"):
        with pytest.raises(InvalidIdentifier):
            dr.fetch(_NoCallClient(), "local-test", bad, "2026-09-30")


def test_main_dataset_xau_rc3(monkeypatch, tmp_path):
    def _no_client(*a, **k):
        raise AssertionError("không được dựng client khi định danh xấu")

    monkeypatch.setattr(dr, "make_client", _no_client)
    rc = dr.main(["--date", "2026-09-30", "--out", str(tmp_path / "r.md"), "--project", "local-test",
                  "--endpoint", "http://127.0.0.1:19050", "--dataset", "x`; DROP TABLE y; --"])
    assert rc == 3
    assert not (tmp_path / "r.md").exists()


def test_report_daily_sh_tu_choi_dataset_xau(tmp_path):
    import os
    import subprocess
    import sys
    from pathlib import Path

    script = Path(__file__).resolve().parents[2] / "tools" / "report_daily.sh"
    env = {**os.environ, "BACKEND_PY": sys.executable, "BRAIN_BQ_DATASET": "ds; DROP TABLE x",
           "BRAIN_BQ_PROJECT": "local-test", "BRAIN_BQ_ENDPOINT": ""}
    r = subprocess.run(["bash", str(script), "--date", "2026-09-30", "--out", str(tmp_path / "r.md")],
                       env=env, capture_output=True, text=True, timeout=60)
    assert r.returncode == 2, (r.returncode, r.stderr)
    assert "BRAIN_BQ_DATASET" in r.stderr
    assert not (tmp_path / "r.md").exists()


def test_main_project_xau_rc3_khong_dung_client(monkeypatch, tmp_path):
    """R-FBE m1: project cũng được kiểm trước `make_client`, như bq_setup/stack_load."""
    def _no_client(*a, **k):
        raise AssertionError("không được dựng client khi project xấu")

    monkeypatch.setattr(dr, "make_client", _no_client)
    rc = dr.main(["--date", "2026-09-30", "--out", str(tmp_path / "r.md"), "--project", "local-test`; x",
                  "--endpoint", "http://127.0.0.1:19050"])
    assert rc == 3
