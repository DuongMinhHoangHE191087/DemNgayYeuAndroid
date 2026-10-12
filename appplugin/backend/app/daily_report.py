"""Báo cáo ngày DOC/62 §2.1: 12 bảng từ 12 view (`sql/views`: 7 view quảng cáo/consent/retention + ARPDAU gồm IAP
và phễu paywall theo phiên của 1.8.0 T12 + quyết định brain và eCPM theo mạng của 1.9.0 T9), gọi qua
`tools/report_daily.sh --date D`.

Ngày D = ngày địa phương UTC+7 (`Asia/Ho_Chi_Minh`) của `client_ts_ms` (phán quyết plan 1.7.0 v2), đúng
cột `day` của view; retention lấy các cohort `install_day` trong [D-7, D]. Bảng rỗng ghi rõ "0 hàng"
kèm lý do một dòng, không bỏ trống im lặng.
"""

from __future__ import annotations

import argparse
import os
import re
import sys
from datetime import date, timedelta
from pathlib import Path
from typing import Any, Mapping

from .ad_metrics import VIEWS
from .bq_setup import SetupRefused, make_client, run_sql
from .bq_tables import ds_ref, require_identifier, require_project

VIEW_ORDER = tuple(VIEWS)
RETENTION_VIEW = "v_retention_d1_d7_holdout"
_TITLES = {
    "v_ecpm_format_tier": "eCPM (USD) theo định dạng × tier",
    "v_imp_per_dau_format": "Impression / DAU theo định dạng",
    "v_arpdau_ads_country": "ARPDAU quảng cáo (USD) theo nước",
    "v_fill_rate": "Fill rate (orphan = request không có kết thúc)",
    "v_show_rate": "Show rate = ad_show / ad_show_attempt ready=1",
    "v_show_rate_reasons": "Show rate tách lý do chặn (policy/brain/guard/entitlement) + organic_show_rate",
    "v_show_rate_views": "Show rate theo phiên view (banner/native_collapse/rect): view có attempt ready đã hiện",
    "v_consent_funnel_nondenied": "Phễu consent người không từ chối (không có cột tỉ lệ: nguồn = AdMob P&M)",
    "v_retention_d1_d7_holdout": "Retention D1/D7 theo cohort install_day [D-7, D] × nhóm holdout",
    "v_arpdau_country": "ARPDAU (USD) = quảng cáo + IAP theo nước (IAP khử trùng theo token, trial = 0)",
    "v_paywall_funnel": "Phễu paywall theo phiên: hiện → chọn → mua (closes = hiện mà không chọn; holdout = bị giữ)",
    "v_brain_decisions": "Quyết định brain theo điểm × chế độ × holdout × policy (declined_app_day = brain từ chối "
                         "show, tổng app × ngày lặp trên mỗi hàng — không cộng dồn)",
    "v_ecpm_network_format": "eCPM (USD) theo mạng quảng cáo × định dạng (mạng = ad_network hoặc ad_source)",
}
_DEFAULT_EMPTY = "không có event tương ứng trong ngày"
_DAY_RE = re.compile(r"^\d{4}-\d{2}-\d{2}$")


def parse_day(text: str) -> str:
    """YYYY-MM-DD hợp lệ, hoặc ValueError — giá trị này được chèn vào SQL dạng literal."""
    if not _DAY_RE.match(text or ""):
        raise ValueError(f"ngày phải dạng YYYY-MM-DD: {text!r}")
    return date.fromisoformat(text).isoformat()


def _cell(v: Any) -> str:
    if v is None:
        return "NULL"
    if isinstance(v, float):
        return f"{v:.6g}"
    return str(v)


def _table(rows: list[Mapping[str, Any]]) -> list[str]:
    cols = list(rows[0])
    out = ["| " + " | ".join(cols) + " |", "|" + "---|" * len(cols)]
    out += ["| " + " | ".join(_cell(r.get(c)) for c in cols) + " |" for r in rows]
    return out


def render(day: str, tables: Mapping[str, list[Mapping[str, Any]]], meta: Mapping[str, Any]) -> str:
    missing = [v for v in VIEW_ORDER if v not in tables]
    if missing:
        raise ValueError(f"thiếu bảng: {', '.join(missing)}")
    versions = ", ".join(f"schema_version={'NULL' if k is None else k}: {n}"
                         for k, n in sorted(meta["schema_versions"].items(), key=lambda kv: str(kv[0])))
    lines = [f"# Báo cáo ngày {day}", "",
             "Ngày = ngày địa phương UTC+7 của `client_ts_ms`; view chỉ đọc hàng `schema_version = 2`.", "",
             f"- Nguồn: {meta['source']}",
             f"- Số hàng `events_raw` trong ngày: {meta['rows_total']}",
             f"- Phân bố schema_version: {versions or '(không có hàng)'}", ""]
    reasons = meta.get("empty_reasons", {})
    for view in VIEW_ORDER:
        rows = list(tables[view])
        lines += [f"## {view}", "", _TITLES[view], ""]
        if rows:
            lines += [f"{len(rows)} hàng", ""] + _table(rows)
        else:
            lines.append(f"0 hàng — {reasons.get(view, _DEFAULT_EMPTY)}")
        lines.append("")
    return "\n".join(lines)


def _yyyymmdd(d: date) -> int:
    return int(d.strftime("%Y%m%d"))


def fetch(client: Any, project: str, dataset: str, day: str) -> tuple[dict[str, list], dict[str, Any]]:
    ds = ds_ref(project, dataset)  # kiểm định danh TRƯỚC mọi SQL (R-10 m2)
    d = date.fromisoformat(parse_day(day))
    tables: dict[str, list] = {}
    for view in VIEW_ORDER:
        if view == RETENTION_VIEW:
            where = f"install_day BETWEEN {_yyyymmdd(d - timedelta(days=7))} AND {_yyyymmdd(d)}"
            order = "app, install_day, holdout_group"
        else:
            where, order = f"day = DATE '{d.isoformat()}'", "1, 2, 3"
        rows = run_sql(client, f"SELECT * FROM `{ds}.{require_identifier(view, 'view')}` WHERE {where} ORDER BY {order}")
        tables[view] = [dict(r.items()) for r in rows]
    # Ngày UTC+7 D nằm trong ngày client UTC D-1..D: lọc thô theo `client_ts_date` rồi đúng ngày địa phương.
    # Không lọc theo `received_date` (cột phân vùng từ F2-be): hàng replay tới muộn của ngày D vẫn phải được
    # đếm. Cái giá: truy vấn này không cắt phân vùng (quét trên BigQuery thật = việc của H5).
    dist = run_sql(client, (
        f"SELECT schema_version, COUNT(*) AS n FROM `{ds}.events_raw` "
        f"WHERE client_ts_date BETWEEN DATE '{(d - timedelta(days=1)).isoformat()}' AND DATE '{d.isoformat()}' "
        f"AND DATE(TIMESTAMP_MILLIS(client_ts_ms), 'Asia/Ho_Chi_Minh') = DATE '{d.isoformat()}' "
        "GROUP BY schema_version"))
    versions = {r["schema_version"]: r["n"] for r in dist}
    return tables, {"source": f"BigQuery {ds} (view sql/views, oracle app/ad_metrics.py)",
                    "rows_total": sum(versions.values()), "schema_versions": versions}


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description="Báo cáo ngày 11 bảng DOC/62 §2.1 (+ IAP/paywall 1.8.0, brain/mạng 1.9.0)")
    ap.add_argument("--date", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--project", default=os.getenv("BRAIN_BQ_PROJECT", ""))
    ap.add_argument("--dataset", default=os.getenv("BRAIN_BQ_DATASET", "monetization_brain"))
    ap.add_argument("--endpoint", default=os.getenv("BRAIN_BQ_ENDPOINT", ""))
    args = ap.parse_args(argv)
    try:
        day = parse_day(args.date)
        require_project(args.project)
        require_identifier(args.dataset)
        client = make_client(args.project, args.endpoint)
    except (ValueError, SetupRefused) as exc:
        print(f"daily_report: REFUSED {exc}", file=sys.stderr)
        return 3
    tables, meta = fetch(client, args.project, args.dataset, day)
    out = Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(render(day, tables, meta), encoding="utf-8")
    counts = " ".join(f"{v}={len(tables[v])}" for v in VIEW_ORDER)
    print(f"daily_report: OK {out} rows_total={meta['rows_total']} {counts}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
