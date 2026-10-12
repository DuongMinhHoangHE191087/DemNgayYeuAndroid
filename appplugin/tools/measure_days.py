"""`python3 tools/measure_check.py --local-days <dir> [--need n]` (1.7.0 Task 13; tách khỏi measure_check.py ≤ 300 dòng).

Ngày của một phiên = ngày UTC+7 (Asia/Ho_Chi_Minh) của client_ts_ms ĐẦU TIÊN trong events.jsonl của phiên đó (plan
1.7.0 dòng 20): một phiên = một ngày, phiên vắt qua 00:00 +07 không được tính hai ngày (R-13 I4). Phiên hợp lệ =
thư mục có events.jsonl đọc được, ≥ 1 hàng ad_revenue, không có INVALID.txt. In mỗi ngày + chuỗi ngày liên tiếp dài
nhất; PASS khi chuỗi đó ≥ need.
"""

from __future__ import annotations

from collections import defaultdict
from datetime import date, datetime, timedelta, timezone
from pathlib import Path
from typing import Any, Callable

UTC7 = timezone(timedelta(hours=7))


def session_day(rows: list[dict[str, Any]]) -> date | None:
    """Ngày UTC+7 của client_ts_ms đầu tiên; None khi phiên không có ad_revenue hoặc không có ts."""
    ts = [r["ts"] for r in rows if r["ts"]]
    if not ts or not any(r["name"] == "ad_revenue" for r in rows):
        return None
    return datetime.fromtimestamp(min(ts) / 1000, UTC7).date()


def cmd_local_days(root: str, need: int, read_jsonl: Callable[[str], list[dict[str, Any]]],
                   fail: type[Exception]) -> bool:
    if not Path(root).is_dir():
        raise fail(f"không có thư mục: {root}")
    per_day: dict[date, list[str]] = defaultdict(list)
    for jf in sorted(Path(root).rglob("events.jsonl")):
        try:
            if (jf.parent / "INVALID.txt").exists():
                raise fail("INVALID.txt")
            day = session_day(read_jsonl(str(jf)))
            if day is None:
                raise fail("0 ad_revenue")
        except fail as exc:
            print(f"  bỏ {jf.parent}: {exc}")
            continue
        per_day[day].append(str(jf.parent))
    best: list[date] = []
    run: list[date] = []
    for day in sorted(per_day):
        run = run + [day] if run and day - run[-1] == timedelta(days=1) else [day]
        best = run if len(run) > len(best) else best
        print(f"{day.isoformat()} (UTC+7 của client_ts_ms đầu phiên): {len(per_day[day])} phiên: {', '.join(per_day[day])}")
    span = f" ({best[0].isoformat()}..{best[-1].isoformat()})" if best else ""
    print(f"ngày UTC+7 liên tiếp dài nhất: {len(best)}{span}; cần {need}")
    return len(best) >= need
