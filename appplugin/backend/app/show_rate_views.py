"""Oracle của `v_show_rate_views` (2.2.0 BE3): show rate theo PHIÊN VIEW cho định dạng không phát attempt mỗi lượt hiện.

Banner / native_collapse / rect có nhiều `ad_show_attempt` ready mỗi lần một view còn sống (emu37: banner 34 attempt
ready cho 6 show), nên `v_show_rate` (show / attempt) cho 0.18 dù mọi view đều hiện. Đơn vị đúng = khoá
`(app, session_id, placement)` có >= 1 attempt ready; `views_shown` = số khoá có >= 1 `ad_show`. Khoá KHÔNG gồm
`screen_name` (`ad_show` không mang). Ngày/trọng số của khoá = ngày sớm nhất / trọng số lớn nhất của các attempt ready.
Cùng định nghĩa với `sql/views/v_show_rate_views.sql`.
"""

from __future__ import annotations

from collections import defaultdict
from typing import Any, Iterable, Mapping

from .ad_rows import Fx, Rows, _base

VIEW_FORMATS = ("banner", "native_collapse", "rect")


def show_rate_views(rows: Iterable[Mapping[str, Any]], tiers: Mapping[str, str] | None = None,
        fx: Fx | None = None) -> Rows:
    units: dict[tuple, dict[str, Any]] = defaultdict(lambda: {"day": None, "w": 0.0, "ready": False, "shown": False})
    for r in _base(rows, tiers, fx):
        fmt = r.get("ad_format") or ""
        if r["event_name"] not in ("ad_show_attempt", "ad_show") or fmt not in VIEW_FORMATS:
            continue
        u = units[(r["app"], r.get("session_id") or "", r.get("placement") or "", fmt)]
        if r["event_name"] == "ad_show":
            u["shown"] = True
        elif r.get("ready") == 1:
            u.update(ready=True, day=min(u["day"] or r["day"], r["day"]), w=max(u["w"], r["w"]))
    acc: dict[tuple, list[float]] = defaultdict(lambda: [0.0, 0.0])
    for (app, _sid, _pl, fmt), u in units.items():
        if u["ready"]:
            a = acc[(app, u["day"], fmt)]
            a[0] += u["w"]
            a[1] += u["w"] if u["shown"] else 0.0
    return [{"app": k[0], "day": k[1], "ad_format": k[2], "views": a[0], "views_shown": a[1],
             "view_show_rate": None if not a[0] else a[1] / a[0]} for k, a in acc.items()]
