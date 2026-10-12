"""1.7.0 T10 — `tools/stack_load.py`: JSONL (sink hoặc fixture) -> hàng staging. Không mạng."""

from __future__ import annotations

import importlib.util
import json
import sys
from pathlib import Path

import pytest

from conftest import FIXTURE_V2

_PATH = Path(__file__).resolve().parents[2] / "tools" / "stack_load.py"
_spec = importlib.util.spec_from_file_location("stack_load", _PATH)
stack_load = importlib.util.module_from_spec(_spec)
# Không để lại tools/__pycache__ trong cây (tools/ không có .gitignore cho bytecode).
_prev, sys.dont_write_bytecode = sys.dont_write_bytecode, True
try:
    _spec.loader.exec_module(stack_load)
finally:
    sys.dont_write_bytecode = _prev


def test_hang_staging_chi_giu_cot_schema_va_raw_la_chuoi():
    rows = stack_load.rows_for_staging([
        {"event_name": "x", "dedup_key": "k", "app_id": "demo", "khong_co_trong_schema": 1,
         "raw_event_data": {"a": 1}},
    ])
    assert rows == [{"event_name": "x", "dedup_key": "k", "app_id": "demo",
                     "raw_event_data": json.dumps({"a": 1}, ensure_ascii=False)}]


def test_doc_ca_dong_fixture_lan_dong_sink(tmp_path):
    sink_line = {"event_name": "ad_show", "dedup_key": "ev:1", "app_id": "demo", "client_ts_ms": 1}
    f = tmp_path / "mix.jsonl"
    first_fixture_line = FIXTURE_V2.read_text().splitlines()[0]
    f.write_text(first_fixture_line + "\n\n" + json.dumps(sink_line) + "\n")
    rows = stack_load.read_jsonl([f])
    assert len(rows) == 2
    assert rows[0]["app_id"] == "demo" and rows[0]["dedup_key"].startswith("ev:")
    assert rows[0]["event_name"] == "ad_revenue" and rows[0]["received_ts_ms"] > 0
    assert rows[1] == sink_line


def test_chi_nap_vao_emulator_local_test():
    for project, endpoint in (("test-d-56f09", ""), ("local-test", ""), ("other", "http://x")):
        with pytest.raises(SystemExit):
            stack_load.main(["--project", project, "--endpoint", endpoint, str(FIXTURE_V2)])


def test_dataset_xau_bi_tu_choi(monkeypatch):
    def _no_client(*a, **k):
        raise AssertionError("không được dựng client khi định danh xấu")

    monkeypatch.setattr(stack_load, "make_client", _no_client)
    for bad in ("ds; DROP TABLE x", "`ds`", "other-proj.ds"):
        with pytest.raises(SystemExit):
            stack_load.main(["--dataset", bad, str(FIXTURE_V2)])


def test_B6_nap_ca_sink_g12_va_v_paywall_funnel_co_hang(tmp_path):
    """Sink 2.0.0: `events.jsonl` + `g12-*/events.jsonl` cạnh nó (cổng G12 ghi paywall_*); nạp cả hai thì
    `v_paywall_funnel` có hàng (cũ: chỉ events.jsonl -> không có phiên paywall)."""
    from app import ad_metrics as am

    def line(name, seq, **kw):
        return {"event_name": name, "sequence_id": seq, "client_ts_ms": 1790939830672 + seq, "schema_version": 2,
                "dedup_key": f"ev:{name}:{seq}", "app_id": "default", "received_ts_ms": 1790939832000, **kw}

    g12 = tmp_path / "g12-emulator-5570-20261002-181708"
    g12.mkdir()
    (tmp_path / "events.jsonl").write_text(json.dumps(line("ad_show", 1, ad_format="interstitial")) + "\n")
    (g12 / "events.jsonl").write_text("\n".join(json.dumps(x) for x in (
        line("paywall_impression", 3, paywall_session_id="s1", placement="g12_paywall", variant_id="g12_v1"),
        line("paywall_choice", 4, paywall_session_id="s1", placement="g12_paywall", variant_id="g12_v1"))) + "\n")
    paths = stack_load.with_g12([tmp_path / "events.jsonl"])
    assert [p.parent.name for p in paths] == [tmp_path.name, g12.name]
    rows = am.dedup(stack_load.read_jsonl(paths))
    got = am.VIEWS["v_paywall_funnel"](rows, {}, {})
    assert [(r["placement"], r["sessions"], r["choices"]) for r in got] == [("g12_paywall", 1, 1)]
    assert stack_load.with_g12(paths) == paths  # idempotent: không nạp hai lần


def test_B12_with_g12_khong_nap_doi_cung_file_duong_tuong_doi_va_tuyet_doi(tmp_path, monkeypatch):
    """2.2.0 BE3: cùng một file cho hai lần (tương đối + tuyệt đối) hoặc kèm sink g12 đã nêu -> mỗi file một lần."""
    g12 = tmp_path / "g12-x"
    g12.mkdir()
    (g12 / "events.jsonl").write_text("{}\n")
    (tmp_path / "events.jsonl").write_text("{}\n")
    monkeypatch.chdir(tmp_path)
    paths = stack_load.with_g12(["events.jsonl", str(tmp_path / "events.jsonl"), str(g12 / "events.jsonl")])
    assert [p.resolve() for p in paths] == [(tmp_path / "events.jsonl").resolve(), (g12 / "events.jsonl").resolve()]
