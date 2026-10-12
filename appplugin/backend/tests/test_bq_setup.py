"""1.7.0 T10 — `app/bq_setup.py` (thay script shell `bq mk` cũ) + `app/bq_tables.py`, không mạng.

Client BigQuery là bản giả ghi lại lời gọi. Ranh giới an toàn: project production không bao giờ
qua; project thật chỉ khi nằm trong danh sách cho phép VÀ có `GCP_GUARD_OWNER_GO`; emulator chỉ
với `local-test`. Bảng tạo qua API (không SQL DDL), không bao giờ xoá hay tạo lại (plan R-4).
"""

from __future__ import annotations

from pathlib import Path

import pytest
from google.api_core.exceptions import NotFound

from app import bq_setup, bq_tables
from app.config import Settings

EMU = "http://127.0.0.1:19050"
MERGE_SQL = Path(__file__).resolve().parent.parent / "sql" / "merge_events.sql"


class _Job:
    def __init__(self, rows):
        self._rows = rows

    def result(self, timeout=None):
        return self._rows


class _FakeClient:
    def __init__(self, project="local-test", existing_rows=0, bad_expiration=False):
        self.project = project
        self.tables: dict[str, object] = {}
        self.calls: list[tuple] = []
        self.sql: list[str] = []
        self.inserted: dict[str, list] = {}
        self._existing_rows = existing_rows
        self._bad_expiration = bad_expiration

    def get_dataset(self, ref):
        raise NotFound(f"dataset {ref}")

    def create_dataset(self, ds, exists_ok=False):
        self.calls.append(("create_dataset", str(ds), exists_ok))

    def create_table(self, table, exists_ok=False):
        self.calls.append(("create_table", table.table_id, exists_ok))
        self.tables[table.table_id] = table
        return table

    def get_table(self, ref):
        name = str(ref).split(".")[-1]
        if name not in self.tables:
            raise NotFound(f"table {ref}")
        table = self.tables[name]
        if self._bad_expiration and table.time_partitioning is not None:
            table.time_partitioning.expiration_ms = None
        return table

    def query(self, sql, job_config=None):
        self.sql.append(sql)
        if "COUNT(*)" in sql:
            return _Job([{"n": self._existing_rows}])
        return _Job([])

    def insert_rows_json(self, table, rows, row_ids=None):
        self.inserted.setdefault(str(table), []).extend(rows)
        return []

    def delete_table(self, *a, **k):  # không bao giờ được gọi
        raise AssertionError("bq_setup không được xoá bảng")

    def delete_dataset(self, *a, **k):
        raise AssertionError("bq_setup không được xoá dataset")


def test_tu_choi_project_production_moi_dang():
    for proj in ("speaker-c7f50", "SPEAKER-C7F50"):
        with pytest.raises(bq_setup.SetupRefused):
            bq_setup.check_target(proj, "", {"GCP_GUARD_OWNER_GO": "x"})
    with pytest.raises(bq_setup.SetupRefused):
        bq_setup.check_target("test-d-56f09", "", {"GCP_GUARD_OWNER_GO": "x",
                                                    "CLOUDSDK_CORE_PROJECT": "speaker-c7f50"})


def test_tu_choi_project_ngoai_danh_sach_cho_phep():
    for proj in ("other-proj", "test-d-56f09x", ""):
        with pytest.raises(bq_setup.SetupRefused):
            bq_setup.check_target(proj, "", {"GCP_GUARD_OWNER_GO": "x"})


def test_emulator_chi_di_voi_local_test():
    assert bq_setup.check_target("local-test", EMU, {}) == "emulator"
    with pytest.raises(bq_setup.SetupRefused):
        bq_setup.check_target("test-d-56f09", EMU, {"GCP_GUARD_OWNER_GO": "x"})
    with pytest.raises(bq_setup.SetupRefused):
        bq_setup.check_target("local-test", "", {})


def test_bigquery_that_can_loi_dong_y_cua_chu():
    with pytest.raises(bq_setup.SetupRefused):
        bq_setup.check_target("test-d-56f09", "", {})
    assert bq_setup.check_target("test-d-56f09", "", {"GCP_GUARD_OWNER_GO": "L-12"}) == "bigquery"


def test_tao_bang_qua_api_voi_han_giu_va_kiem_metadata():
    c = _FakeClient()
    names = bq_setup.ensure_tables(c, "local-test", "ds")
    assert names == ["events_staging", "events_raw", "country_tier", "fx_daily", "admob_report",
                     "subscription_events"]  # 1.8.0 T13
    assert all(call[2] is True for call in c.calls if call[0] == "create_table")
    st, raw = c.tables["events_staging"], c.tables["events_raw"]
    assert (st.time_partitioning.field, st.time_partitioning.expiration_ms) == ("received_date", 172_800_000)
    assert (raw.time_partitioning.field, raw.time_partitioning.expiration_ms) == ("received_date",
                                                                                 34_560_000_000)
    assert raw.clustering_fields == ["event_name", "user_id"]
    assert [f.name for f in raw.schema] == [f.name for f in st.schema]
    assert len(raw.schema) == len(bq_tables.events_columns()) == 114  # T12 1.8.0: +7 cột IAP/paywall; T9 1.9.0: slot
    assert c.tables["admob_report"].time_partitioning.field == "date"
    assert c.tables["country_tier"].time_partitioning is None


def test_bang_da_co_thi_khong_tao_lai():
    """Chạy lần hai: đọc thấy bảng rồi thì không gọi create (emulator trả 500 cho create trùng)."""
    c = _FakeClient()
    bq_setup.ensure_tables(c, "local-test", "ds")
    first = len([x for x in c.calls if x[0] == "create_table"])
    bq_setup.ensure_tables(c, "local-test", "ds")
    assert len([x for x in c.calls if x[0] == "create_table"]) == first == 6


def test_cot_thieu_tren_dataset_cu_that_bai_ro_rang(monkeypatch, capsys):
    """R-12 m3: bảng dựng bởi bản trước (thiếu 7 cột v2 của T12 + `slot` 1.9.0) -> SetupError nêu tên bảng + cột thiếu, dừng
    TRƯỚC view (view mới tham chiếu cột thiếu); không ALTER, không xoá/tạo lại (R-4); `main` rc 1."""
    old_cols = [c for c in bq_tables.events_columns()
                if c["name"] not in ("phase", "offer_tag", "paywall_session_id", "outcome", "response_code",
                                     "schema_version_cfg", "source", "slot")]  # slot: 1.9.0 T9
    assert len(old_cols) == 106

    def old_dataset():
        c = _FakeClient()
        for spec in bq_tables.table_specs():
            if spec.name in ("events_staging", "events_raw"):
                spec = bq_tables.TableSpec(spec.name, tuple(old_cols), spec.partition_field, spec.expiration_ms,
                                           spec.clustering_fields)
            c.tables[spec.name] = bq_tables.to_bq_table(spec, "local-test", "ds")
        return c

    c = old_dataset()
    with pytest.raises(bq_setup.SetupError) as err:
        bq_setup.setup(c, "local-test", "ds")
    msg = str(err.value)
    for name in ("events_staging", "events_raw", "phase", "source", "schema_version_cfg", "slot", "ALTER TABLE"):
        assert name in msg, (name, msg)
    assert [x for x in c.calls if x[0] == "create_table"] == []
    assert not any("VIEW" in s or "ALTER" in s.upper() for s in c.sql), c.sql
    c2 = old_dataset()
    monkeypatch.setattr(bq_setup, "make_client", lambda *a, **k: c2)
    assert bq_setup.main(["--project", "local-test", "--endpoint", EMU, "--dataset", "ds"]) == 1
    assert "bq_setup: FAIL" in capsys.readouterr().err


def test_metadata_han_giu_sai_thi_dung_lai():
    with pytest.raises(bq_setup.SetupError):
        bq_setup.ensure_tables(_FakeClient(bad_expiration=True), "local-test", "ds")


def test_view_tao_theo_thu_tu_phu_thuoc_va_thay_dataset():
    c = _FakeClient()
    names = bq_setup.ensure_views(c, "local-test", "ds")
    assert len(names) == 14 and names[0] == "v_events_v2"
    assert set(names[1:]) == {
        "v_ecpm_format_tier", "v_imp_per_dau_format", "v_arpdau_ads_country", "v_fill_rate",
        "v_show_rate", "v_consent_funnel_nondenied", "v_retention_d1_d7_holdout",
        "v_arpdau_country", "v_paywall_funnel", "v_brain_decisions", "v_ecpm_network_format",
        "v_show_rate_reasons", "v_show_rate_views"}  # +2: 1.9.0 T9, +1: 2.1.0 BE1, +1: 2.2.0 BE3
    for name, sql in zip(names, c.sql):
        assert sql.startswith(f"CREATE OR REPLACE VIEW `local-test.ds.{name}` AS")
        assert "__DS__" not in sql


def test_khong_bao_gio_xoa_hay_ddl_bang():
    c = _FakeClient()
    bq_setup.setup(c, "local-test", "ds")
    assert sum("CREATE OR REPLACE VIEW" in s for s in c.sql) == 14
    joined = "\n".join(c.sql).upper()
    for word in ("DROP ", "DELETE ", "TRUNCATE ", "CREATE TABLE", "CREATE OR REPLACE TABLE"):
        assert word not in joined, word


def test_seed_country_tier_chi_khi_bang_rong():
    c = _FakeClient(existing_rows=0)
    assert bq_setup.seed_country_tier(c, "local-test", "ds") == 37
    rows = c.inserted["local-test.ds.country_tier"]
    assert {"country": "VN", "tier": "VN"} in rows
    assert bq_setup.seed_country_tier(_FakeClient(existing_rows=37), "local-test", "ds") == 0


def test_merge_script_temp_table_va_khoa_theo_app():
    """R-9: khử trùng theo (IFNULL(app_id,''), dedup_key); nguồn MERGE là TEMP TABLE, không subquery."""
    sql = bq_setup.render_sql(MERGE_SQL.read_text(), "local-test", "ds")
    flat = " ".join(sql.split())
    assert "CREATE TEMP TABLE d AS" in flat
    assert "PARTITION BY IFNULL(app_id, ''), dedup_key" in flat
    assert "ON IFNULL(r.app_id, '') = IFNULL(d.app_id, '') AND r.dedup_key = d.dedup_key" in flat
    assert "USING d" in flat and "USING (" not in flat
    assert "`local-test.ds.events_raw`" in flat and "`local-test.ds.events_staging`" in flat


def test_sink_bigquery_can_project_khac_rong():
    """R-9 minor: `BRAIN_SINK=bigquery` với project rỗng là cấu hình sai, dừng lúc khởi động."""
    with pytest.raises(ValueError):
        Settings(sink="bigquery", bq_project="").validate()
    Settings(sink="jsonl", bq_project="").validate()


# ── R-10 m2: mọi định danh ghép vào SQL phải là định danh BigQuery hợp lệ, kiểm TRƯỚC mọi lời gọi ──
BAD_DATASETS = ("ds; DROP TABLE x", "ds`", "`ds`", "other-proj.ds", "ds--", "1ds", "", "a" * 1025, "ds x",
                "ds\n", "other_proj.ds", "a.b")


def test_dataset_xau_bi_tu_choi_truoc_moi_loi_goi():
    for bad in BAD_DATASETS:
        c = _FakeClient()
        with pytest.raises(bq_tables.InvalidIdentifier):
            bq_setup.setup(c, "local-test", bad)
        assert (c.calls, c.sql, c.inserted) == ([], [], {}), bad


def test_render_sql_tu_choi_dataset_va_project_xau():
    for bad in BAD_DATASETS:
        with pytest.raises(bq_tables.InvalidIdentifier):
            bq_setup.render_sql("SELECT 1 FROM `__DS__.t`", "local-test", bad)
    for bad_proj in ("local-test`; DROP", "other.proj", "Local-Test", ""):
        with pytest.raises(bq_tables.InvalidIdentifier):
            bq_setup.render_sql("SELECT 1 FROM `__DS__.t`", bad_proj, "ds")


def test_dinh_danh_hop_le_van_qua():
    for ok in ("monetization_brain", "_x", "t10merge_ab12cd34", "a" * 1024):
        assert bq_tables.require_identifier(ok) == ok
    assert bq_setup.render_sql("SELECT 1 FROM `__DS__.t`", "test-d-56f09", "ds").startswith(
        "SELECT 1 FROM `test-d-56f09.ds.t`")


def test_main_dataset_xau_rc3_khong_dung_client(monkeypatch):
    def _no_client(*a, **k):
        raise AssertionError("không được dựng client khi định danh xấu")

    monkeypatch.setattr(bq_setup, "make_client", _no_client)
    rc = bq_setup.main(["--project", "local-test", "--endpoint", EMU, "--dataset", "ds; DROP TABLE x"])
    assert rc == 3


# ── R-FBE m2: reader/sink của server dựng định danh từ env — cấu hình xấu thì không khởi động ──
def test_settings_bigquery_tu_choi_dinh_danh_xau():
    for kw in ({"bq_dataset": "ds; DROP TABLE x"}, {"bq_table": "t`x"}, {"bq_staging_table": "a.b"},
               {"bq_project": "demo-proj`; DROP"}):
        with pytest.raises(ValueError):
            Settings(**{"sink": "bigquery", "bq_project": "demo-proj", **kw}).validate()
    Settings(sink="bigquery", bq_project="demo-proj").validate()
    Settings(sink="jsonl", bq_dataset="khong dung toi").validate()  # jsonl không đọc các tên này


def test_from_env_dataset_xau_khong_khoi_dong(monkeypatch):
    monkeypatch.setenv("BRAIN_SINK", "bigquery")
    monkeypatch.setenv("BRAIN_BQ_PROJECT", "demo-proj")
    monkeypatch.setenv("BRAIN_BQ_DATASET", "x`; DROP TABLE y; --")
    monkeypatch.delenv("BRAIN_BQ_ENDPOINT", raising=False)
    with pytest.raises(ValueError, match="BRAIN_BQ_DATASET"):
        Settings.from_env()
