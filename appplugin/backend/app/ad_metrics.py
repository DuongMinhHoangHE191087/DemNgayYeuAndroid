"""Oracle Python của 12 view (`sql/views/*.sql`: 7 view DOC/62 §2.1 + 2 view IAP/paywall của 1.8.0 T12 + quyết định
brain và eCPM theo mạng của 1.9.0 T9) — cùng một định nghĩa, hai cách tính.

`tests/test_views_parity.py` chạy cùng fixture qua đây và qua view trên bigquery-emulator: Δ = 0
(FLOAT64 so với dung sai 1e-9). Sửa một định nghĩa = sửa CẢ view lẫn hàm ở đây, test parity bắt lệch.

Định nghĩa chung (khớp `sql/views/v_events_v2.sql`):
- chỉ hàng `schema_version = 2`; ngày báo cáo `day` = ngày địa phương UTC+7 (`Asia/Ho_Chi_Minh`) của
  `client_ts_ms` (phán quyết plan v2: không phải `client_ts_date` UTC);
- trọng số `w` = 1/`sample_rate` khi 0 < sample_rate ≤ 1, còn lại 1 — nhân vào mọi phép ĐẾM event;
  số người khác nhau (DAU, cohort) không nhân;
- tier: `country_tier` theo `UPPER(country)`, không có trong bảng (kể cả `DEFAULT`, rỗng) = OTHER;
- tiền: `value_micros`/1e6, USD giữ nguyên, mã khác nhân `fx_daily` cùng `day`; không có tỉ giá (hoặc
  thiếu `value_micros`) = `unconverted`, không bịa; hàng `meta_direct` với `value_micros = 0` bị loại;
- `ad_format` giữ nguyên nhãn SDK gửi (vd. `fullscreen` cho placement `full_`), `imp_id` không là khoá;
- show rate loại placement `full_2_*` (Full2 không có `ad_show_attempt`, T2); các view khác giữ Full2;
- tiền IAP (D3: quy đổi ở server): `price_micros`/1e6 theo `currency_code`, như trên; `phase = 'trial'` = 0
  (D5: trial không thu tiền — bản SDK báo nhầm giá niêm yết không làm phồng ARPDAU); `iap_purchase` khử trùng
  theo `iap_purchase:tok:<purchase_token>` (bản phát lại D15 gộp một);
- phiên paywall = `paywall_session_id` của `paywall_impression` (SDK cũ không có phiên: mỗi lượt hiện là một
  phiên riêng, khoá `dedup_key`); lựa chọn/lượt mua nối vào phiên bằng `paywall_session_id`; nhánh `holdout` = phiên
  `paywall_holdout_skip` + lượt mua ngoài phiên của người bị giữ (1.9.0 T9);
- mạng quảng cáo = chữ thường của `ad_network or ad_source` (cùng luật điền cột của `normalize.py`, D11/D35).
"""

from __future__ import annotations

import json
from collections import defaultdict
from typing import Any, Callable, Iterable, Mapping

# Hàng cơ sở chung (tách ra `ad_rows.py` ở 1.9.0 T9); tên công khai giữ ở đây cho người gọi cũ (`am.dedup`, `metrics`).
from .show_rate_reasons import COLUMNS as SR_COLUMNS, organic_rate, relabel, tally
from .show_rate_views import show_rate_views
from .ad_rows import (LOCAL_TZ, OTHER_TIER, Fx, Rows, _base, _iap_usd, _weight, dedup,  # noqa: F401
                      load_country_tiers, local_day, tier_of)


def _div(num: float, den: float) -> float | None:
    return None if not den else num / den


def _user(r: Mapping[str, Any]) -> str:
    return r.get("user_id") or ""


def _dau(base: Rows, extra: Callable[[Mapping[str, Any]], tuple] = lambda r: ()) -> dict[tuple, set]:
    users: dict[tuple, set] = defaultdict(set)
    for r in base:
        if _user(r):
            users[(r["app"], r["day"], *extra(r))].add(_user(r))
    return users


def _revenue(base: Rows, key: Callable[[Mapping[str, Any]], tuple]) -> dict[tuple, list[float]]:
    """[impressions, impressions_converted, unconverted, revenue_usd] (có trọng số) theo `key`."""
    acc: dict[tuple, list[float]] = defaultdict(lambda: [0.0, 0.0, 0.0, 0.0])
    for r in base:
        if r["event_name"] != "ad_revenue" or r["meta_zero"]:
            continue
        a = acc[key(r)]
        a[0] += r["w"]
        if r["value_usd"] is None:
            a[2] += r["w"]
        else:
            a[1] += r["w"]
            a[3] += r["w"] * r["value_usd"]
    return acc


def ecpm_format_tier(rows: Iterable[Mapping[str, Any]], tiers: Mapping[str, str] | None = None,
        fx: Fx | None = None) -> Rows:
    acc = _revenue(_base(rows, tiers, fx),
                   lambda r: (r["app"], r["day"], r.get("ad_format") or "", r["country_tier"]))
    return [{"app": k[0], "day": k[1], "ad_format": k[2], "tier": k[3], "impressions": a[0],
             "impressions_converted": a[1], "unconverted": a[2], "revenue_usd": a[3],
             "ecpm_usd": None if not a[1] else a[3] / a[1] * 1000} for k, a in acc.items()]


def imp_per_dau_format(rows: Iterable[Mapping[str, Any]], tiers: Mapping[str, str] | None = None,
        fx: Fx | None = None) -> Rows:
    base = _base(rows, tiers, fx)
    dau = _dau(base)
    imps: dict[tuple, float] = defaultdict(float)
    for r in base:
        if r["event_name"] == "ad_show":
            imps[(r["app"], r["day"], r.get("ad_format") or "")] += r["w"]
    out = []
    for (app, day, fmt), n in imps.items():
        d = len(dau.get((app, day), ()))
        out.append({"app": app, "day": day, "ad_format": fmt, "impressions": n, "dau": d,
                    "imp_per_dau": _div(n, d)})
    return out


def arpdau_ads_country(rows: Iterable[Mapping[str, Any]], tiers: Mapping[str, str] | None = None,
        fx: Fx | None = None) -> Rows:
    base = _base(rows, tiers, fx)
    users = _dau(base, lambda r: (r.get("country") or "", r["country_tier"]))
    rev = _revenue(base, lambda r: (r["app"], r["day"], r.get("country") or ""))
    out = []
    for (app, day, country, tier), us in users.items():
        a = rev.get((app, day, country), [0.0, 0.0, 0.0, 0.0])
        out.append({"app": app, "day": day, "country": country, "tier": tier, "dau": len(us),
                    "revenue_usd": a[3], "unconverted": a[2], "arpdau_usd": _div(a[3], len(us))})
    return out


def fill_rate(rows: Iterable[Mapping[str, Any]], tiers: Mapping[str, str] | None = None,
        fx: Fx | None = None) -> Rows:
    base = [r for r in _base(rows, tiers, fx) if r.get("request_id")]
    reqs: dict[tuple, dict[str, Any]] = {}
    ends: dict[tuple, set] = defaultdict(set)
    for r in base:
        k = (r["app"], r["request_id"])
        if r["event_name"] == "ad_request":
            cur = reqs.setdefault(k, {"day": r["day"], "fmt": r.get("ad_format") or "", "w": r["w"]})
            cur.update(day=min(cur["day"], r["day"]), fmt=min(cur["fmt"], r.get("ad_format") or ""),
                       w=max(cur["w"], r["w"]))
        elif r["event_name"] in ("ad_fill", "ad_no_fill"):
            ends[k].add(r["event_name"])
    acc: dict[tuple, list[float]] = defaultdict(lambda: [0.0, 0.0, 0.0, 0.0])
    for (app, rid), q in reqs.items():
        e = ends.get((app, rid), set())
        a = acc[(app, q["day"], q["fmt"])]
        a[0] += q["w"]
        a[1] += q["w"] if "ad_fill" in e else 0.0
        a[2] += q["w"] if "ad_fill" not in e and "ad_no_fill" in e else 0.0
        a[3] += q["w"] if not e else 0.0
    return [{"app": k[0], "day": k[1], "ad_format": k[2], "requests": a[0], "fills": a[1],
             "no_fills": a[2], "orphans": a[3], "fill_rate": _div(a[1], a[0])} for k, a in acc.items()]


FULL2_PREFIX = "full_2_"


def show_rate(rows: Iterable[Mapping[str, Any]], tiers: Mapping[str, str] | None = None,
        fx: Fx | None = None) -> Rows:
    acc: dict[tuple, list[float]] = defaultdict(lambda: [0.0, 0.0, 0.0])
    for r in _base(rows, tiers, fx):
        if r["event_name"] not in ("ad_show_attempt", "ad_show"):
            continue
        if (r.get("placement") or "").startswith(FULL2_PREFIX):
            continue  # Full2 không phát ad_show_attempt (T2): loại khỏi cả tử lẫn mẫu (R-2 m4)
        a = acc[(r["app"], r["day"], r.get("ad_format") or "")]
        if r["event_name"] == "ad_show":
            a[2] += r["w"]
        else:
            a[0] += r["w"]
            a[1] += r["w"] if r.get("ready") == 1 else 0.0
    return [{"app": k[0], "day": k[1], "ad_format": k[2], "attempts": a[0], "attempts_ready": a[1],
             "shows": a[2], "show_rate": _div(a[2], a[1])} for k, a in acc.items()]


def _json_field(raw: Any, name: str) -> Any:
    if isinstance(raw, str):
        try:
            raw = json.loads(raw)
        except ValueError:
            return None
    return raw.get(name) if isinstance(raw, dict) else None


def consent_funnel_nondenied(rows: Iterable[Mapping[str, Any]], tiers: Mapping[str, str] | None = None,
        fx: Fx | None = None) -> Rows:
    """Chỉ phễu của người KHÔNG từ chối (Q2: sau DENIED không gửi gì) — cố ý không có cột tỉ lệ."""
    acc: dict[tuple, dict[str, Any]] = {}
    for r in _base(rows, tiers, fx):
        if r["event_name"] not in ("consent_form_shown", "consent_choice"):
            continue
        a = acc.setdefault((r["app"], r["day"], r.get("form_kind") or ""), {
            "forms_shown": 0.0, "choices": 0.0, "granted": 0.0, "partial": 0.0, "other_tier": 0.0,
            "with_gpp": 0.0, "lat": []})
        if r["event_name"] == "consent_form_shown":
            a["forms_shown"] += r["w"]
            continue
        a["choices"] += r["w"]
        tier = _json_field(r.get("raw_event_data"), "tier")
        a[{"GRANTED": "granted", "PARTIAL": "partial"}.get(tier, "other_tier")] += r["w"]
        a["with_gpp"] += r["w"] if r.get("gpp_sections") else 0.0
        if r.get("latency_ms") is not None and r["latency_ms"] >= 0:
            a["lat"].append(r["latency_ms"])
    out = []
    for (app, day, kind), a in acc.items():
        lat = a.pop("lat")
        out.append({"app": app, "day": day, "form_kind": kind, **a,
                    "avg_latency_ms": sum(lat) / len(lat) if lat else None,
                    "max_latency_ms": max(lat) if lat else None})
    return out


def retention_d1_d7_holdout(rows: Iterable[Mapping[str, Any]], tiers: Mapping[str, str] | None = None,
        fx: Fx | None = None) -> Rows:
    """Cohort theo `install_day` (yyyyMMdd), D1/D7 theo `retention_day`; nhóm như
    `metrics.compare_holdout`: có quyết định `is_holdout` 1 -> holdout, 0 -> treatment, cả hai ->
    'conflicting' (báo riêng), chưa có quyết định -> 'unassigned'."""
    base = [r for r in _base(rows, tiers, fx) if _user(r)]
    seen: dict[tuple, set] = defaultdict(set)
    users: dict[tuple, dict[str, int]] = {}
    for r in base:
        k = (r["app"], _user(r))
        if r["event_name"] == "brain_decision":
            seen[k].add(int(r.get("is_holdout") or 0))
        if (r.get("install_day") or 0) > 0:
            u = users.setdefault(k, {"install_day": r["install_day"], "d1": 0, "d7": 0})
            u["install_day"] = min(u["install_day"], r["install_day"])
            u["d1"] |= int(r.get("retention_day") == 1)
            u["d7"] |= int(r.get("retention_day") == 7)
    acc: dict[tuple, list[int]] = defaultdict(lambda: [0, 0, 0])
    for (app, uid), u in users.items():
        s = seen.get((app, uid))
        grp = ("unassigned" if not s else "conflicting" if len(s) > 1
               else "holdout" if 1 in s else "treatment")
        a = acc[(app, u["install_day"], grp)]
        a[0] += 1
        a[1] += u["d1"]
        a[2] += u["d7"]
    return [{"app": k[0], "install_day": k[1], "holdout_group": k[2], "cohort_users": a[0],
             "d1_users": a[1], "d7_users": a[2], "d1_rate": _div(a[1], a[0]),
             "d7_rate": _div(a[2], a[0])} for k, a in acc.items()]


def _iap(base: Rows, fx: Fx, key: Callable[[Mapping[str, Any]], tuple | None]) -> dict[tuple, list[float]]:
    """[purchases, unconverted, revenue_usd] (có trọng số) của `iap_purchase` theo `key` (None = bỏ hàng)."""
    acc: dict[tuple, list[float]] = defaultdict(lambda: [0.0, 0.0, 0.0])
    for r in base:
        k = key(r) if r["event_name"] == "iap_purchase" else None
        if k is None:
            continue
        usd = _iap_usd(r, fx)
        a = acc[k]
        a[0] += r["w"]
        if usd is None:
            a[1] += r["w"]
        else:
            a[2] += r["w"] * usd
    return acc


def arpdau_country(rows: Iterable[Mapping[str, Any]], tiers: Mapping[str, str] | None = None,
        fx: Fx | None = None) -> Rows:
    """ARPDAU (USD) = (quảng cáo + IAP) / DAU theo ngày × nước; vế quảng cáo đúng như `v_arpdau_ads_country`."""
    fx = fx or {}
    base = _base(rows, tiers, fx)
    users = _dau(base, lambda r: (r.get("country") or "", r["country_tier"]))
    ads = _revenue(base, lambda r: (r["app"], r["day"], r.get("country") or ""))
    iap = _iap(base, fx, lambda r: (r["app"], r["day"], r.get("country") or ""))
    out = []
    for (app, day, country, tier), us in users.items():
        a = ads.get((app, day, country), [0.0, 0.0, 0.0, 0.0])
        i = iap.get((app, day, country), [0.0, 0.0, 0.0])
        out.append({"app": app, "day": day, "country": country, "tier": tier, "dau": len(us),
                    "ad_revenue_usd": a[3], "iap_revenue_usd": i[2], "revenue_usd": a[3] + i[2],
                    "ad_unconverted": a[2], "iap_purchases": i[0], "iap_unconverted": i[1],
                    "arpdau_usd": _div(a[3] + i[2], len(us))})
    return out


def _session_key(r: Mapping[str, Any]) -> tuple[str, str]:
    sid = r.get("paywall_session_id") or ""
    return (r["app"], f"s:{sid}" if sid else f"d:{r.get('dedup_key') or ''}")


def paywall_session_funnel(rows: Iterable[Mapping[str, Any]], tiers: Mapping[str, str] | None = None,
        fx: Fx | None = None) -> Rows:
    """Phễu theo PHIÊN × (placement, biến thể): hiện -> chọn -> mua; `closes` = phiên hiện mà không chọn
    (`paywall_close` suy ở server, §Hợp đồng E). Số phiên nhân trọng số lớn nhất của các lượt hiện trong phiên."""
    fx = fx or {}
    base = _base(rows, tiers, fx)
    sessions: dict[tuple[str, str], dict[str, Any]] = {}
    chose: set[tuple[str, str]] = set()
    for r in base:
        if r["event_name"] == "paywall_impression":
            s = sessions.setdefault(_session_key(r), {"day": r["day"], "placement": r.get("placement") or "",
                                                      "variant_id": r.get("variant_id") or "", "w": 0.0, "n": 0.0})
            s.update(day=min(s["day"], r["day"]), placement=min(s["placement"], r.get("placement") or ""),
                     variant_id=min(s["variant_id"], r.get("variant_id") or ""), w=max(s["w"], r["w"]),
                     n=s["n"] + r["w"])
        elif r["event_name"] == "paywall_choice" and r.get("paywall_session_id"):
            chose.add(_session_key(r))
    buys = _iap(base, fx, lambda r: _session_key(r) if r.get("paywall_session_id") else None)
    acc: dict[tuple, list[float]] = defaultdict(lambda: [0.0] * 7)
    for sk, s in sessions.items():
        a = acc[(sk[0], s["day"], s["placement"], s["variant_id"])]
        a[0] += s["n"]
        a[1] += s["w"]
        a[2 if sk in chose else 3] += s["w"]
        if sk in buys:
            a[4] += s["w"]
            a[5] += buys[sk][2]
            a[6] += buys[sk][1]
    # Hai nhánh hai bộ cộng riêng (như UNION ALL của view): biến thể thật tên `holdout` không gộp vào nhánh holdout.
    return [{"app": k[0], "day": k[1], "placement": k[2], "variant_id": k[3], "impressions": a[0],
             "sessions": a[1], "choices": a[2], "closes": a[3], "purchases": a[4], "revenue_usd": a[5],
             "unconverted": a[6], "conversion_rate": _div(a[4], a[1])}
            for arm in (acc, _holdout_arm(base, fx)) for k, a in arm.items()]


HOLDOUT = "holdout"
BRAIN_DECLINED = "show_full_brain_declined"


def show_rate_reasons(rows: Iterable[Mapping[str, Any]], tiers: Mapping[str, str] | None = None,
        fx: Fx | None = None) -> Rows:
    acc: dict[tuple, dict[str, float]] = defaultdict(lambda: dict.fromkeys(SR_COLUMNS, 0.0))
    for key, col, w in filter(None, map(tally, relabel(_base(rows, tiers, fx)))):
        acc[key][col] += w
    return [{"app": k[0], "day": k[1], "ad_format": k[2], **a, "organic_show_rate": organic_rate(a)} for k, a in acc.items()]


def _holdout_arm(base: Rows, fx: Fx) -> dict[tuple, list[float]]:
    """Hàng `variant_id = 'holdout'` (như `sql/views/v_paywall_funnel.sql`): phiên = `paywall_holdout_skip`, khoá như
    lượt hiện; lượt mua = `iap_purchase` không có `paywall_session_id` của người từng bị giữ (theo app), vào ngày của
    lượt mua × placement MIN người đó bị giữ. Không có lượt hiện/chọn/đóng."""
    skips: dict[tuple[str, str], dict[str, Any]] = {}
    held: dict[tuple[str, str], str] = {}
    acc: dict[tuple, list[float]] = defaultdict(lambda: [0.0] * 7)
    for r in base:
        if r["event_name"] != "paywall_holdout_skip":
            continue
        pl = r.get("placement") or ""
        s = skips.setdefault(_session_key(r), {"day": r["day"], "placement": pl, "w": 0.0})
        s.update(day=min(s["day"], r["day"]), placement=min(s["placement"], pl), w=max(s["w"], r["w"]))
        if _user(r):
            held[(r["app"], _user(r))] = min(held.get((r["app"], _user(r)), pl), pl)
    for (app, _), s in skips.items():
        acc[(app, s["day"], s["placement"], HOLDOUT)][1] += s["w"]

    def key(r: Mapping[str, Any]) -> tuple | None:
        pl = held.get((r["app"], _user(r)))
        return None if pl is None or r.get("paywall_session_id") else (r["app"], r["day"], pl)

    for (app, day, pl), b in _iap(base, fx, key).items():
        a = acc[(app, day, pl, HOLDOUT)]
        a[4], a[5], a[6] = a[4] + b[0], a[5] + b[2], a[6] + b[1]
    return acc


def brain_decisions(rows: Iterable[Mapping[str, Any]], tiers: Mapping[str, str] | None = None,
        fx: Fx | None = None) -> Rows:
    """Quyết định brain theo (ngày, điểm, chế độ, holdout, policy); `declined_app_day` = `ad_show_fail` reason brain từ
    chối ở grain (app, ngày), lặp trên mọi hàng của ngày đó theo thiết kế (D38: không SUM qua hàng); ngày chỉ có
    declined thì không có hàng."""
    dec: dict[tuple, float] = defaultdict(float)
    declined: dict[tuple, float] = defaultdict(float)
    for r in _base(rows, tiers, fx):
        if r["event_name"] == "brain_decision":
            dec[(r["app"], r["day"], r.get("decision_point") or "", r.get("chosen_action_mode") or "",
                 int(r.get("is_holdout") or 0), r.get("policy_version") or "")] += r["w"]
        elif r["event_name"] == "ad_show_fail" and r.get("reason") == BRAIN_DECLINED:
            declined[(r["app"], r["day"])] += r["w"]
    return [{"app": k[0], "day": k[1], "decision_point": k[2], "chosen_action_mode": k[3], "is_holdout": k[4],
             "policy_version": k[5], "decisions": n, "declined_app_day": declined.get(k[:2], 0.0)}
            for k, n in dec.items()]


def ecpm_network_format(rows: Iterable[Mapping[str, Any]], tiers: Mapping[str, str] | None = None,
        fx: Fx | None = None) -> Rows:
    """eCPM theo (ngày, mạng, định dạng); mạng = chữ thường của `ad_network or ad_source` (D35: lưu giữ nhãn gốc)."""
    acc = _revenue(_base(rows, tiers, fx), lambda r: (
        r["app"], r["day"], (r.get("ad_network") or r.get("ad_source") or "").lower(), r.get("ad_format") or ""))
    return [{"app": k[0], "day": k[1], "ad_network": k[2], "ad_format": k[3], "impressions": a[0],
             "revenue_usd": a[3], "ecpm_usd": None if not a[1] else a[3] / a[1] * 1000} for k, a in acc.items()]


# Tên view -> hàm oracle (cùng chữ ký `(rows, tiers, fx)`; `rows` là hàng ĐÃ khử trùng).
VIEWS: dict[str, Callable[..., Rows]] = {
    "v_ecpm_format_tier": ecpm_format_tier,
    "v_imp_per_dau_format": imp_per_dau_format,
    "v_arpdau_ads_country": arpdau_ads_country,
    "v_fill_rate": fill_rate,
    "v_show_rate": show_rate,
    "v_consent_funnel_nondenied": consent_funnel_nondenied,
    "v_retention_d1_d7_holdout": retention_d1_d7_holdout,
    "v_arpdau_country": arpdau_country,
    "v_paywall_funnel": paywall_session_funnel,
    "v_brain_decisions": brain_decisions,
    "v_ecpm_network_format": ecpm_network_format,
    "v_show_rate_reasons": show_rate_reasons,
    "v_show_rate_views": show_rate_views,
}
