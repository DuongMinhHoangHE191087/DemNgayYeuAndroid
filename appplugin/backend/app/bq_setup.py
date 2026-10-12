"""Dựng dataset BigQuery của backend: bảng (`bq_tables`), seed `country_tier`, view (`sql/views/*.sql`).

Thay script shell `bq mk` cũ (1.7.0 T10). Chạy từ `backend/`:

    # stack cục bộ (bigquery-emulator của docker-compose.yml)
    python -m app.bq_setup --project local-test --endpoint http://127.0.0.1:19050
    # BigQuery thật: chỉ project trong tools/gcp_allowed_projects.txt, chỉ chủ dự án (RUNBOOK §5)
    GCP_GUARD_OWNER_GO=<id ledger> python -m app.bq_setup --project test-d-56f09

Ranh giới (cùng luật với `tools/gcp_guard.sh`, vì lệnh này đi bằng Python client chứ không qua `bq`):
production `speaker-c7f50` không bao giờ qua (tên project hay env gcloud); endpoint emulator chỉ đi với
`local-test` và ngược lại; project thật phải nằm trong danh sách cứng ∩ file cho phép VÀ có
`GCP_GUARD_OWNER_GO`. Bảng chỉ tạo-nếu-chưa-có, không bao giờ xoá/tạo lại (plan R-4); hạn giữ kiểm lại
từ metadata sau khi tạo.
"""

from __future__ import annotations

import argparse
import csv
import os
import sys
from pathlib import Path
from typing import Any, Mapping

from .bq_tables import (BACKEND_DIR, COUNTRY_TIER_SEED, SQL_DIR, InvalidIdentifier, ds_ref,
                        require_identifier, table_specs, to_bq_table)
from .config import LOCAL_TEST_PROJECT

PROD_PROJECT = "speaker-c7f50"
# Danh sách cứng — file chỉ THU HẸP được (như HARD_ALLOWED của gcp_guard.sh).
HARD_ALLOWED = frozenset({"test-d-56f09"})
ALLOWED_FILE = BACKEND_DIR.parent / "tools" / "gcp_allowed_projects.txt"
_GCLOUD_ENV = ("CLOUDSDK_CORE_PROJECT", "GOOGLE_CLOUD_PROJECT", "GCLOUD_PROJECT", "CLOUDSDK_PROJECT")
DS_TOKEN = "__DS__"
BASE_VIEW = "v_events_v2"
QUERY_TIMEOUT_S = 60


class SetupRefused(RuntimeError):
    """Đích không được phép — không gọi BigQuery."""


class SetupError(RuntimeError):
    """Đã gọi nhưng kết quả sai (vd. hạn giữ trong metadata khác đặc tả)."""


def _allowed_projects() -> frozenset[str]:
    if not ALLOWED_FILE.is_file():
        return frozenset()
    lines = (ln.strip() for ln in ALLOWED_FILE.read_text(encoding="utf-8").splitlines())
    return frozenset(ln for ln in lines if ln and not ln.startswith("#")) & HARD_ALLOWED


def check_target(project: str, endpoint: str, env: Mapping[str, str]) -> str:
    """'emulator' hoặc 'bigquery'; mọi đích khác -> SetupRefused (trước khi dựng client)."""
    if PROD_PROJECT in project.lower():
        raise SetupRefused(f"project production {PROD_PROJECT} bị cấm")
    for name in _GCLOUD_ENV:
        if PROD_PROJECT in env.get(name, "").lower():
            raise SetupRefused(f"env {name} chứa {PROD_PROJECT}")
    if endpoint or project == LOCAL_TEST_PROJECT:
        if not (endpoint and project == LOCAL_TEST_PROJECT):
            raise SetupRefused(f"endpoint emulator chỉ đi với project {LOCAL_TEST_PROJECT} (và ngược lại)")
        return "emulator"
    if project not in _allowed_projects():
        raise SetupRefused(f"project {project!r} không nằm trong {ALLOWED_FILE.name} ∩ danh sách cứng")
    if not env.get("GCP_GUARD_OWNER_GO"):
        raise SetupRefused("ghi vào BigQuery thật cần GCP_GUARD_OWNER_GO=<id dòng ledger có go của chủ>")
    return "bigquery"


def make_client(project: str, endpoint: str = "", env: Mapping[str, str] | None = None) -> Any:
    kind = check_target(project, endpoint, os.environ if env is None else env)
    from google.cloud import bigquery  # noqa: PLC0415

    if kind == "emulator":
        from google.api_core.client_options import ClientOptions  # noqa: PLC0415
        from google.auth.credentials import AnonymousCredentials  # noqa: PLC0415

        return bigquery.Client(project=project, client_options=ClientOptions(api_endpoint=endpoint),
                               credentials=AnonymousCredentials())
    return bigquery.Client(project=project)


def render_sql(text: str, project: str, dataset: str) -> str:
    """Bỏ dòng chú thích `--` (tài liệu ở file, không gửi đi) rồi thay `__DS__` = `<project>.<dataset>`."""
    body = "\n".join(ln for ln in text.splitlines() if not ln.lstrip().startswith("--"))
    return body.replace(DS_TOKEN, ds_ref(project, dataset)).strip() + "\n"


def run_sql(client: Any, sql: str, timeout: float = QUERY_TIMEOUT_S) -> list[Any]:
    return list(client.query(sql).result(timeout=timeout))


def _get_or_none(get: Any, ref: str) -> Any:
    """`get(ref)`, hoặc None khi chưa có. Đọc trước rồi mới tạo: bigquery-emulator trả 500 (không phải
    409) cho `create_*` trên đối tượng đã có, nên `exists_ok=True` không đủ — client thử lại mãi."""
    from google.api_core.exceptions import NotFound  # noqa: PLC0415

    try:
        return get(ref)
    except NotFound:
        return None


def ensure_tables(client: Any, project: str, dataset: str) -> list[str]:
    """Tạo-nếu-chưa-có mọi bảng; kiểm hạn giữ phân vùng và đủ cột, đọc lại từ metadata (fail-closed).

    Bảng dựng bởi bản trước có thể thiếu cột thêm sau (1.8.0 T12: 7 cột v2 của `events_*`). Không tự ALTER và không
    bao giờ xoá/tạo lại (R-4): dừng TRƯỚC view (view mới tham chiếu cột thiếu — emulator treo, BigQuery 400) với
    thông báo nêu từng bảng, từng cột và câu `ALTER TABLE … ADD COLUMN` chủ dự án chạy (R-12 m3, RUNBOOK §5).
    """
    ds, names = ds_ref(project, dataset), []
    missing: dict[str, list[dict[str, Any]]] = {}
    for spec in table_specs():
        ref = f"{ds}.{require_identifier(spec.name, 'bảng')}"
        if _get_or_none(client.get_table, ref) is None:
            client.create_table(to_bq_table(spec, project, dataset), exists_ok=True)
        got = client.get_table(ref)
        if spec.partition_field:
            tp = got.time_partitioning
            actual = None if tp is None else (tp.field, tp.expiration_ms)
            if actual != (spec.partition_field, spec.expiration_ms):
                raise SetupError(f"{spec.name}: phân vùng/hạn giữ {actual} != "
                                 f"{(spec.partition_field, spec.expiration_ms)}")
        have = {f.name for f in (got.schema or ())}
        lacking = [c for c in spec.columns if c["name"] not in have]
        if lacking:
            missing[spec.name] = lacking
        names.append(spec.name)
    if missing:
        raise SetupError(_missing_columns_message(ds, missing))
    return names


def _missing_columns_message(ds: str, missing: dict[str, list[dict[str, Any]]]) -> str:
    parts = [f"{table}: thiếu {len(cols)} cột ({', '.join(c['name'] for c in cols)})"
             for table, cols in missing.items()]
    alters = [f"ALTER TABLE `{ds}.{table}` " + ", ".join(
        f"ADD COLUMN IF NOT EXISTS {c['name']} {c['type']}" for c in cols) + ";"
        for table, cols in missing.items()]
    return ("bảng có trước bản này thiếu cột — " + "; ".join(parts) + ". bq_setup không sửa bảng (R-4); "
            "chủ dự án thêm cột (chỉ thêm, RUNBOOK §5) rồi chạy lại: " + " ".join(alters))


def seed_country_tier(client: Any, project: str, dataset: str) -> int:
    """Nạp seed khi bảng rỗng (không DML, không ghi đè). Trả số dòng đã nạp."""
    table = f"{ds_ref(project, dataset)}.country_tier"
    if run_sql(client, f"SELECT COUNT(*) AS n FROM `{table}`")[0]["n"]:
        return 0
    with open(COUNTRY_TIER_SEED, encoding="utf-8", newline="") as f:
        rows = [{"country": r["country"].strip().upper(), "tier": r["tier"].strip()}
                for r in csv.DictReader(f)]
    errors = client.insert_rows_json(table, rows)
    if errors:
        raise SetupError(f"seed country_tier lỗi: {errors}")
    return len(rows)


def view_files() -> list[Path]:
    """`sql/views/*.sql`, view cơ sở trước (các view khác đọc từ nó)."""
    files = sorted((SQL_DIR / "views").glob("*.sql"))
    return sorted(files, key=lambda p: (p.stem != BASE_VIEW, p.stem))


def ensure_views(client: Any, project: str, dataset: str) -> list[str]:
    ds, names = ds_ref(project, dataset), []
    for path in view_files():
        view = require_identifier(path.stem, "view")
        body = render_sql(path.read_text(encoding="utf-8"), project, dataset)
        run_sql(client, f"CREATE OR REPLACE VIEW `{ds}.{view}` AS\n{body}")
        names.append(path.stem)
    return names


def setup(client: Any, project: str, dataset: str) -> dict[str, Any]:
    from google.cloud import bigquery  # noqa: PLC0415

    ref = ds_ref(project, dataset)  # kiểm định danh TRƯỚC mọi lời gọi
    if _get_or_none(client.get_dataset, ref) is None:
        client.create_dataset(bigquery.Dataset(ref), exists_ok=True)
    return {"tables": ensure_tables(client, project, dataset),
            "seeded": seed_country_tier(client, project, dataset),
            "views": ensure_views(client, project, dataset)}


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--project", default=os.getenv("BRAIN_BQ_PROJECT", ""))
    ap.add_argument("--dataset", default=os.getenv("BRAIN_BQ_DATASET", "monetization_brain"))
    ap.add_argument("--endpoint", default=os.getenv("BRAIN_BQ_ENDPOINT", ""))
    args = ap.parse_args(argv)
    try:
        ds_ref(args.project, args.dataset)  # định danh hợp lệ TRƯỚC khi dựng client (R-10 m2)
        client = make_client(args.project, args.endpoint)
        out = setup(client, args.project, args.dataset)
    except (SetupRefused, InvalidIdentifier) as exc:
        print(f"bq_setup: REFUSED {exc}", file=sys.stderr)
        return 3
    except SetupError as exc:
        print(f"bq_setup: FAIL {exc}", file=sys.stderr)
        return 1
    print(f"bq_setup: OK {args.project}.{args.dataset} tables={len(out['tables'])} "
          f"seeded={out['seeded']} views={len(out['views'])}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
