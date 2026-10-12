"""Nhập báo cáo AdMob mediation và đối chiếu với `ad_revenue` của ta.

Nguồn chính: luồng JSON của `accounts.mediationReport:generate` (phần tử `header` / `row` / `footer`),
dimension `APP, FORMAT, COUNTRY, AD_SOURCE, DATE`, metric `AD_REQUESTS, MATCHED_REQUESTS, MATCH_RATE,
IMPRESSIONS, ESTIMATED_EARNINGS` và `OBSERVED_ECPM` (hai metric tiền tính bằng micros). CSV đi qua
`HEADER_MAP`: cột nằm ngoài bản đồ là LỖI, không đoán nghĩa. `HEADER_MAP` hiện chỉ chứa tên enum của API;
tên cột của bản xuất từ giao diện AdMob chỉ được thêm vào sau khi chủ dự án đưa một file xuất thật (P9).

Không có FX ở đây: đối chiếu chỉ chạy khi cùng đơn vị tiền. Quốc gia -> tier do bảng `tiers` truyền vào
(một định nghĩa ở server, không phải ở module này).
"""

from __future__ import annotations

import csv
import io
import json
import math
import re
from dataclasses import dataclass
from datetime import datetime
from typing import Any, Iterable, Mapping

OTHER_TIER = "OTHER"
CAVEAT = (
    "AdMob gán quốc gia theo IP, còn `country` của ta lấy từ SIM/mạng của máy: so sánh chỉ có nghĩa "
    "ở mức tier và cộng dồn theo ngày, không so từng quốc gia. Chênh lệch nhỏ theo ngày là bình thường."
)

DIMENSIONS = {
    "DATE": "date", "APP": "app", "FORMAT": "format", "COUNTRY": "country", "AD_SOURCE": "ad_source",
}
METRICS = {
    "AD_REQUESTS": "ad_requests", "MATCHED_REQUESTS": "matched_requests", "MATCH_RATE": "match_rate",
    "IMPRESSIONS": "impressions", "ESTIMATED_EARNINGS": "earnings_micros",
    "OBSERVED_ECPM": "observed_ecpm_micros",
}
HEADER_MAP = {**DIMENSIONS, **METRICS}
# Metric có thể vắng ở dòng không có impression; các metric còn lại bắt buộc.
_OPTIONAL_METRICS = {"MATCH_RATE", "OBSERVED_ECPM"}
_METRIC_KIND = {
    "AD_REQUESTS": "integerValue", "MATCHED_REQUESTS": "integerValue", "IMPRESSIONS": "integerValue",
    "MATCH_RATE": "doubleValue", "ESTIMATED_EARNINGS": "microsValue", "OBSERVED_ECPM": "microsValue",
}

# Đặc tả bảng `admob_report` cho T10 nạp vào bq_tables.py (không sửa file chung ở đây).
ADMOB_REPORT_TABLE: dict[str, Any] = {
    "name": "admob_report",
    "partition_field": "date",
    "clustering_fields": ["format", "country"],
    "columns": [
        {"name": "date", "type": "DATE", "mode": "REQUIRED"},
        {"name": "app", "type": "STRING", "mode": "REQUIRED"},
        {"name": "format", "type": "STRING", "mode": "REQUIRED"},
        {"name": "country", "type": "STRING", "mode": "REQUIRED"},
        {"name": "ad_source", "type": "STRING", "mode": "REQUIRED"},
        {"name": "ad_source_label", "type": "STRING", "mode": "NULLABLE"},
        {"name": "ad_requests", "type": "INT64", "mode": "REQUIRED"},
        {"name": "matched_requests", "type": "INT64", "mode": "REQUIRED"},
        {"name": "match_rate", "type": "FLOAT64", "mode": "NULLABLE"},
        {"name": "impressions", "type": "INT64", "mode": "REQUIRED"},
        {"name": "earnings_micros", "type": "INT64", "mode": "REQUIRED"},
        {"name": "observed_ecpm_micros", "type": "INT64", "mode": "NULLABLE"},
        {"name": "currency", "type": "STRING", "mode": "REQUIRED"},
    ],
}


class AdmobImportError(ValueError):
    """Báo cáo sai hình dạng: dừng, không đoán."""


@dataclass(frozen=True)
class ReportRow:
    date: str  # YYYY-MM-DD
    app: str
    format: str
    country: str
    ad_source: str
    ad_source_label: str | None
    ad_requests: int
    matched_requests: int
    match_rate: float | None
    impressions: int
    earnings_micros: int
    observed_ecpm_micros: int | None
    currency: str


def _date(raw: Any) -> str:
    s = str(raw).strip()
    if not re.fullmatch(r"\d{8}", s):
        raise AdmobImportError(f"DATE phải là YYYYMMDD, nhận {raw!r}")
    try:
        return datetime.strptime(s, "%Y%m%d").date().isoformat()
    except ValueError as exc:
        raise AdmobImportError(f"DATE không hợp lệ: {raw!r}") from exc


def _int(raw: Any, name: str) -> int:
    try:
        v = int(str(raw).strip())
    except ValueError as exc:
        raise AdmobImportError(f"{name} phải là số nguyên, nhận {raw!r}") from exc
    if v < 0:
        raise AdmobImportError(f"{name} âm: {v}")
    return v


def _float(raw: Any, name: str) -> float:
    try:
        v = float(str(raw).strip())
    except ValueError as exc:
        raise AdmobImportError(f"{name} phải là số, nhận {raw!r}") from exc
    if not math.isfinite(v):
        raise AdmobImportError(f"{name} không hữu hạn: {raw!r}")
    return v


def _text(raw: Any, name: str) -> str:
    if raw is None:
        raise AdmobImportError(f"{name} thiếu giá trị")
    s = str(raw).strip()
    if not s:
        raise AdmobImportError(f"{name} rỗng")
    return s


def _ratio(raw: Any) -> float | None:
    if raw is None:
        return None
    v = _float(raw, "MATCH_RATE")
    if not 0.0 <= v <= 1.0:
        raise AdmobImportError(f"MATCH_RATE ngoài [0, 1]: {raw!r}")
    return v


def _build(dims: Mapping[str, Any], mets: Mapping[str, Any], labels: Mapping[str, str | None], currency: str) -> ReportRow:
    optional_int = lambda k: None if mets.get(k) is None else _int(mets[k], k)  # noqa: E731
    ad_requests = _int(mets["AD_REQUESTS"], "AD_REQUESTS")
    matched_requests = _int(mets["MATCHED_REQUESTS"], "MATCHED_REQUESTS")
    if matched_requests > ad_requests:
        raise AdmobImportError(f"MATCHED_REQUESTS {matched_requests} lớn hơn AD_REQUESTS {ad_requests}")
    return ReportRow(
        date=_date(dims["DATE"]),
        app=_text(dims["APP"], "APP"),
        format=_text(dims["FORMAT"], "FORMAT"),
        country=_text(dims["COUNTRY"], "COUNTRY"),
        ad_source=_text(dims["AD_SOURCE"], "AD_SOURCE"),
        ad_source_label=labels.get("AD_SOURCE"),
        ad_requests=ad_requests,
        matched_requests=matched_requests,
        match_rate=_ratio(mets.get("MATCH_RATE")),
        impressions=_int(mets["IMPRESSIONS"], "IMPRESSIONS"),
        earnings_micros=_int(mets["ESTIMATED_EARNINGS"], "ESTIMATED_EARNINGS"),
        observed_ecpm_micros=optional_int("OBSERVED_ECPM"),
        currency=currency,
    )


def _stream_items(text: str) -> list[dict[str, Any]]:
    if not text or not text.strip():
        raise AdmobImportError("báo cáo rỗng")
    try:
        data = json.loads(text)
    except json.JSONDecodeError:
        try:
            data = [json.loads(line) for line in text.splitlines() if line.strip()]
        except json.JSONDecodeError as exc:
            raise AdmobImportError(f"JSON hỏng: {exc}") from exc
    items = data if isinstance(data, list) else [data]
    if not items or not all(isinstance(i, dict) and len(i) == 1 for i in items):
        raise AdmobImportError("mỗi phần tử của luồng phải là {header|row|footer: ...}")
    return items


def _json_row(row: Mapping[str, Any], currency: str, idx: int) -> ReportRow:
    dvals, mvals = row.get("dimensionValues"), row.get("metricValues")
    if not isinstance(dvals, dict) or not isinstance(mvals, dict):
        raise AdmobImportError(f"dòng {idx}: thiếu dimensionValues/metricValues")
    unknown = sorted((set(dvals) - set(DIMENSIONS)) | (set(mvals) - set(METRICS)))
    if unknown:
        raise AdmobImportError(f"dòng {idx}: cột lạ {', '.join(unknown)}")
    missing = sorted((set(DIMENSIONS) - set(dvals)) | (set(METRICS) - _OPTIONAL_METRICS - set(mvals)))
    if missing:
        raise AdmobImportError(f"dòng {idx}: thiếu {', '.join(missing)}")
    not_obj = sorted(k for k, v in dvals.items() if not isinstance(v, dict))
    if not_obj or not all(isinstance(v, dict) for v in mvals.values()):
        raise AdmobImportError(f"dòng {idx}: giá trị của {', '.join(not_obj) or 'metric'} phải là object")
    dims = {k: v.get("value") for k, v in dvals.items()}
    labels = {k: v.get("displayLabel") for k, v in dvals.items()}
    mets = {}
    for k, v in mvals.items():
        if _METRIC_KIND[k] not in v:
            raise AdmobImportError(f"dòng {idx}: {k} phải có {_METRIC_KIND[k]}")
        mets[k] = v[_METRIC_KIND[k]]
    return _build(dims, mets, labels, currency)


def parse_report_json(text: str) -> list[ReportRow]:
    """Luồng JSON của mediationReport:generate (mảng, hoặc mỗi phần tử một dòng)."""
    items = _stream_items(text)
    headers = [i["header"] for i in items if "header" in i]
    if not headers:
        raise AdmobImportError("thiếu phần tử header")
    if len(headers) > 1:
        raise AdmobImportError(f"chỉ được một phần tử header, thấy {len(headers)}")
    header = headers[0]
    if not isinstance(header, dict):
        raise AdmobImportError("header phải là object")
    settings = header.get("localizationSettings") or {}
    currency = settings.get("currencyCode") if isinstance(settings, dict) else None
    if not currency:
        raise AdmobImportError("header thiếu localizationSettings.currencyCode")
    bad = sorted({k for i in items for k in i} - {"header", "row", "footer"})
    if bad:
        raise AdmobImportError(f"phần tử lạ: {', '.join(bad)}")
    rows = [_json_row(i["row"], currency, n) for n, i in enumerate((i for i in items if "row" in i), 1)]
    if not rows:
        raise AdmobImportError("báo cáo không có dòng dữ liệu")
    footer = next((i["footer"] for i in items if "footer" in i), None)
    if footer is not None:
        if not isinstance(footer, dict):
            raise AdmobImportError("footer phải là object")
        try:
            declared = int(footer.get("matchingRowCount", len(rows)))
        except (TypeError, ValueError) as exc:
            raise AdmobImportError(f"footer.matchingRowCount không phải số: {footer.get('matchingRowCount')!r}") from exc
        if declared != len(rows):
            raise AdmobImportError(f"footer.matchingRowCount={declared} nhưng có {len(rows)} dòng")
    return rows


def parse_report_csv(text: str, currency: str) -> list[ReportRow]:
    """CSV qua HEADER_MAP; `currency` do người gọi khai (CSV không mang đơn vị tiền)."""
    if not currency:
        raise AdmobImportError("CSV cần currency")
    if not text or not text.strip():
        raise AdmobImportError("báo cáo rỗng")
    reader = csv.DictReader(io.StringIO(text))
    headers = [h.strip() for h in (reader.fieldnames or [])]
    dup = sorted({h for h in headers if headers.count(h) > 1})
    if dup:
        raise AdmobImportError(f"cột trùng trong header: {', '.join(dup)}")
    unknown = [h for h in headers if h not in HEADER_MAP]
    if unknown:
        raise AdmobImportError(f"cột lạ, chưa có trong HEADER_MAP: {', '.join(unknown)}")
    missing = sorted((set(DIMENSIONS) | (set(METRICS) - _OPTIONAL_METRICS)) - set(headers))
    if missing:
        raise AdmobImportError(f"thiếu cột {', '.join(missing)}")
    rows = []
    for n, rec in enumerate(reader, 1):
        if None in rec:
            raise AdmobImportError(f"dòng {n}: thừa {len(rec[None])} ô so với header")
        if any(v is None for v in rec.values()):
            raise AdmobImportError(f"dòng {n}: thiếu ô so với header")
        clean = {k.strip(): (v.strip() if v is not None else "") for k, v in rec.items() if k}
        dims = {k: clean[k] for k in DIMENSIONS}
        mets = {k: (clean.get(k) or None) for k in METRICS}
        rows.append(_build(dims, mets, {}, currency))
    if not rows:
        raise AdmobImportError("báo cáo không có dòng dữ liệu")
    return rows


def _rate(num: int, den: int) -> float | None:
    return None if den == 0 else num / den


def match_summary(rows: Iterable[ReportRow], tiers: Mapping[str, str], ours: Iterable[Mapping[str, Any]] | None = None) -> dict[str, Any]:
    """Match rate và eCPM quan sát theo `format x tier`; nếu có `ours` thì lệch doanh thu theo `ngày x tier`.

    `ours`: các dòng {date: 'YYYY-MM-DD', tier, value_micros, currency} lấy từ ad_revenue của ta.
    Tỉ lệ luôn tính từ tổng (có trọng số), không lấy trung bình các tỉ lệ.
    """
    rows = list(rows)
    if not rows:
        raise AdmobImportError("không có dòng để tổng hợp")
    currencies = {r.currency for r in rows}
    if len(currencies) != 1:
        raise AdmobImportError(f"nhiều currency trong một báo cáo: {sorted(currencies)}")
    currency = next(iter(currencies))
    groups: dict[tuple[str, str], list[int]] = {}
    per_day: dict[tuple[str, str], int] = {}
    for r in rows:
        tier = tiers.get(r.country, OTHER_TIER)
        g = groups.setdefault((r.format, tier), [0, 0, 0, 0])
        g[0] += r.ad_requests
        g[1] += r.matched_requests
        g[2] += r.impressions
        g[3] += r.earnings_micros
        per_day[(r.date, tier)] = per_day.get((r.date, tier), 0) + r.earnings_micros
    out_groups = [
        {
            "format": fmt, "tier": tier, "ad_requests": g[0], "matched_requests": g[1],
            "match_rate": _rate(g[1], g[0]), "impressions": g[2], "earnings_micros": g[3],
            "observed_ecpm_micros": None if g[2] == 0 else round(g[3] * 1000 / g[2]),
            "currency": currency,
        }
        for (fmt, tier), g in sorted(groups.items())
    ]
    result: dict[str, Any] = {"currency": currency, "groups": out_groups, "vs_ours": [], "caveat": CAVEAT}
    if ours is not None:
        mine: dict[tuple[str, str], int] = {}
        for o in ours:
            if o.get("currency") != currency:
                raise AdmobImportError(f"currency lệch: AdMob {currency}, ours {o.get('currency')} (FX không thuộc module này)")
            missing = [k for k in ("date", "tier", "value_micros") if k not in o]
            if missing:
                raise AdmobImportError(f"ours thiếu trường {', '.join(missing)}")
            key = (o["date"], o["tier"])
            mine[key] = mine.get(key, 0) + int(o["value_micros"])
        for key in sorted(set(per_day) | set(mine)):
            admob, ours_v = per_day.get(key, 0), mine.get(key, 0)
            result["vs_ours"].append({
                "date": key[0], "tier": key[1], "admob_earnings_micros": admob,
                "ours_value_micros": ours_v, "delta_micros": ours_v - admob,
                "delta_ratio": _rate(ours_v - admob, admob), "currency": currency,
            })
    return result
