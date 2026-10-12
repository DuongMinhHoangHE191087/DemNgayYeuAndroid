"""Phân lớp lý do cho `v_show_rate_reasons` (2.1.0 BE1; D34). Hàm thuần, không I/O.

Bảng lớp khớp từng dòng `WHEN` của `sql/views/v_show_rate_reasons.sql` (test parity tĩnh B5 so hai bên).
"""

from __future__ import annotations

from typing import Any, Iterable, Mapping

FULL2_PREFIX = "full_2_"  # như `v_show_rate`: Full2 không phát ad_show_attempt, loại cả show lẫn fail
REASONS = {"blocked_policy": "policy_min_gap rwi_intro_skipped seg_guardrail", "blocked_brain": "show_full_brain_declined",
           "blocked_guard": "ad_showing ad_full_showing show_full_when_flag_showing open_flag_resume",
           "blocked_entitlement": "remove_ad show_full_when_remove_ads", "blocked_kill": "show_blocked_kill_switch",
           "harness": "show_when_act_null", "not_ready": "show_not_loaded",
           "config": "not_contain_placement"}  # `not_plm_cf_%` cũng là config; reason khác/rỗng = other
BLOCKED = tuple(REASONS)[:5]  # chặn có chủ đích (gồm kill switch, D21-220): trừ khỏi mẫu số organic; harness thì không
COLUMNS = ("attempts_ready", "shows", *REASONS, "other")
_BY_REASON = {x: c for c, xs in REASONS.items() for x in xs.split()}


def column_of(event_name: str, reason: str | None, placement: str | None) -> str | None:
    """Cột đếm của một event; None = không vào view (event khác, hoặc placement Full2)."""
    if (placement or "").startswith(FULL2_PREFIX):
        return None
    if event_name in ("ad_show", "ad_show_attempt"):
        return "shows" if event_name == "ad_show" else "attempts_ready"
    if event_name != "ad_show_fail":
        return None
    why = reason or ""
    return _BY_REASON.get(why, "config" if why.startswith("not_plm_cf_") else "other")


def relabel(rows: Iterable[Mapping[str, Any]]) -> list[dict[str, Any]]:
    """2.2.0 BE3: nhãn `ad_format` của `ad_show_fail` = nhãn `ad_show_attempt` gần nhất (không sau) cùng
    (app, session_id, placement); không có attempt thì giữ nhãn gốc. Thứ tự (client_ts_ms, sequence_id, event_name)."""
    last: dict[tuple, str] = {}
    out = []
    for r in sorted(rows, key=lambda r: (r.get("client_ts_ms") or 0, r.get("sequence_id") or 0, r["event_name"])):
        k = (r["app"], r.get("session_id") or "", r.get("placement") or "")
        if r["event_name"] == "ad_show_attempt" and r.get("ad_format") is not None:
            last[k] = r["ad_format"]
        out.append({**r, "ad_format": last.get(k, r.get("ad_format"))} if r["event_name"] == "ad_show_fail" else r)
    return out


def tally(r: Mapping[str, Any]) -> tuple[tuple, str, float] | None:
    """(khoá app × ngày × định dạng, cột, trọng số) của một hàng đã qua `_base`; attempt chỉ tính ready = 1
    (như `v_show_rate`); None = bỏ."""
    col = column_of(r["event_name"], r.get("reason"), r.get("placement"))
    key = (r["app"], r["day"], r.get("ad_format") or "")
    return (key, col, r["w"] * (col != "attempts_ready" or r.get("ready") == 1)) if col else None


def organic_rate(acc: Mapping[str, float]) -> float | None:
    den = acc["attempts_ready"] - sum(acc[c] for c in BLOCKED)
    return None if not den else acc["shows"] / den
