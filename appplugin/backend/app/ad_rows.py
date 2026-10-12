"""Hàng cơ sở chung của mọi view (`sql/views/v_events_v2.sql`) — tách khỏi `ad_metrics.py` ở 1.9.0 T9 (G8: tách trước
khi lớn thêm), nội dung không đổi: khử trùng như MERGE, ngày địa phương UTC+7, tier theo `country_tier`, trọng số
1/`sample_rate`, quy đổi USD (quảng cáo và IAP), và `_base` = hàng của `v_events_v2`. Định nghĩa đầy đủ: docstring
của `ad_metrics.py`.
"""

from __future__ import annotations

import csv
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Any, Iterable, Mapping
from zoneinfo import ZoneInfo

from .bq_tables import COUNTRY_TIER_SEED

LOCAL_TZ = ZoneInfo("Asia/Ho_Chi_Minh")
OTHER_TIER = "OTHER"
_EPOCH = datetime(1970, 1, 1, tzinfo=timezone.utc)

Rows = list[dict[str, Any]]
Fx = Mapping[tuple[str, str], float]


def dedup(rows: Iterable[Mapping[str, Any]]) -> Rows:
    """Như `sql/merge_events.sql`: một hàng mỗi `(app_id hoặc '', dedup_key)`, giữ bản nhận sớm nhất."""
    best: dict[tuple[str, str], Mapping[str, Any]] = {}
    for r in rows:
        key = r.get("dedup_key")
        if not key:
            continue
        k = (r.get("app_id") or "", key)
        cur = best.get(k)
        if cur is None or _arrival(r) < _arrival(cur):
            best[k] = r
    return [dict(r) for r in best.values()]


def _arrival(r: Mapping[str, Any]) -> tuple[int, int]:
    return (r.get("received_ts_ms") or 0, r.get("client_ts_ms") or 0)


def local_day(ts_ms: int) -> str:
    return (_EPOCH + timedelta(milliseconds=ts_ms)).astimezone(LOCAL_TZ).date().isoformat()


def load_country_tiers(path: Path = COUNTRY_TIER_SEED) -> dict[str, str]:
    with open(path, encoding="utf-8", newline="") as f:
        return {row["country"].strip().upper(): row["tier"].strip() for row in csv.DictReader(f)}


def tier_of(country: str | None, tiers: Mapping[str, str]) -> str:
    return tiers.get((country or "").upper(), OTHER_TIER)


def _weight(sample_rate: Any) -> float:
    if isinstance(sample_rate, (int, float)) and 0 < sample_rate <= 1:
        return 1.0 / sample_rate
    return 1.0


def _to_usd(micros: Any, currency: Any, day: str, fx: Fx) -> float | None:
    if micros is None:
        return None
    cur = (currency or "").upper()
    if cur == "USD":
        return micros / 1e6
    rate = fx.get((day, cur))
    return micros / 1e6 * rate if rate is not None and rate > 0 else None


def _usd(r: Mapping[str, Any], day: str, fx: Fx) -> float | None:
    return _to_usd(r.get("value_micros"), r.get("currency"), day, fx)


def _iap_usd(r: Mapping[str, Any], fx: Fx) -> float | None:
    if (r.get("phase") or "") == "trial":
        return 0.0
    return _to_usd(r.get("price_micros"), r.get("currency_code"), r["day"], fx)


def _base(rows: Iterable[Mapping[str, Any]], tiers: Mapping[str, str] | None = None,
        fx: Fx | None = None) -> Rows:
    """Hàng của `v_events_v2`: chỉ v2, thêm `day`, `app`, `w`, `country_tier`, `value_usd`, `meta_zero`."""
    tiers = load_country_tiers() if tiers is None else tiers
    fx = fx or {}
    out = []
    for r in rows:
        if r.get("schema_version") != 2:
            continue
        day = local_day(r.get("client_ts_ms") or 0)
        out.append({**r, "day": day, "app": r.get("app_id") or "", "w": _weight(r.get("sample_rate")),
                    "country_tier": tier_of(r.get("country"), tiers), "value_usd": _usd(r, day, fx),
                    "meta_zero": r.get("mediation") == "meta_direct" and not (r.get("value_micros") or 0)})
    return out
