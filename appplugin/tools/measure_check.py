#!/usr/bin/env python3
"""Ngưỡng G11, đối soát H6a và ngày UTC+7 của phiên đo, đọc JSONL của sink + logcat (1.7.0 Task 13).

    python3 tools/measure_check.py g11 --jsonl J --log-a A --log-b B --log-c C --t-b <ms> --t-c <ms>
    python3 tools/measure_check.py h6a --applog L --jsonl J --session <session_id>
    python3 tools/measure_check.py --local-days DOC/testing/daily [--need 3]   (tools/measure_days.py)

--t-b / --t-c: ms máy chủ lúc bắt đầu pha B / pha C. --local-days: một phiên (thư mục có events.jsonl, ≥ 1 ad_revenue,
không INVALID.txt) = MỘT ngày = ngày UTC+7 của client_ts_ms ĐẦU TIÊN của phiên; in chuỗi ngày liên tiếp dài nhất.

Fail-closed: file thiếu / 0 dòng / không parse được / thiếu dòng log bắt buộc = FAIL (exit 1). Khử trùng bằng ĐÚNG
`dedup_key` của backend (`backend/app/normalize.py`, khoá `(app_id, dedup_key)` như MERGE); không có pydantic (CI job
build) thì chỉ `app.models` được thay bằng module rỗng để import được `normalize`. Log = logcat đầy đủ của pha: dòng
FA `origin=app,name=ad_impression` do process GMS in, không nằm trong `logcat --pid` của app.
"""

from __future__ import annotations

import json
import re
import sys
import types
from collections import Counter, defaultdict
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Callable, NamedTuple

BACKEND = Path(__file__).resolve().parent.parent / "backend"
ENVELOPE = ("event_id", "schema_version", "sdk_version", "package_name", "consent_tier")
TERMINAL = ("ad_fill", "ad_no_fill")
RE_SESSION = re.compile(r"logevent: session_id=(\S+)")
RE_STATS = re.compile(r"logevent stats session=(\S+)(.*)")
RE_STORED = re.compile(r"logevent durable stored (\S+) (\S+)")
FA_APP, FA_AM = "origin=app,name=ad_impression", "origin=am,name=ad_impression"
RE_FA_IMP = re.compile(r"\bimp_id=([^,}\s]+)")
RE_AF = re.compile(r"rev af: imp=(\S+) started=([01])\b")
Row = dict[str, Any]


class CheckFail(Exception):
    """Đầu vào không đủ để kết luận: luôn FAIL."""


def load_dedup_key() -> Callable[[Any], str]:
    if str(BACKEND) not in sys.path:
        sys.path.insert(0, str(BACKEND))
    try:
        try:
            import pydantic  # noqa: F401, PLC0415
        except ImportError:
            import app  # noqa: F401, PLC0415  (backend/app, __init__ rỗng)
            stub = types.ModuleType("app.models")
            stub.EventRequest = object  # chỉ để `from .models import EventRequest` của normalize chạy được
            sys.modules["app.models"] = stub
        from app.normalize import dedup_key  # noqa: PLC0415
    except ImportError as exc:
        raise CheckFail(f"không import được dedup_key của backend ({BACKEND}): {exc}") from exc
    return dedup_key


_User = NamedTuple("_User", [("user_id", str), ("session_id", str)])
# Đủ thuộc tính `dedup_key` đọc, tên trường như `EventRequest`.
_Req = NamedTuple("_Req", [("eventName", str), ("eventData", dict), ("sequenceId", int), ("ts", int),
                           ("userProperty", _User), ("model_extra", dict)])


def _int(v: Any) -> int:
    return int(v) if isinstance(v, int) or (isinstance(v, str) and v.strip().lstrip("-").isdigit()) else 0


def _row(item: Row, where: str) -> Row:
    """Hàng sink (`JsonlSink`, đã normalize) hoặc dòng fixture `{app_id, received_ts_ms, body}` như stack_load.py."""
    if "event_name" in item:
        data, raw = dict(item), item.get("raw_event_data")
        try:
            extra = json.loads(raw) if isinstance(raw, str) and raw else {}
        except ValueError as exc:
            raise CheckFail(f"{where}: raw_event_data không parse được") from exc
        data.update(extra if isinstance(extra, dict) else {})
        name, seq, ts, up, env = item["event_name"], item.get("sequence_id"), item.get("client_ts_ms"), item, item
    elif isinstance(item.get("body"), dict) and "eventName" in item["body"]:
        env = item["body"]
        data, up = env.get("eventData") or {}, env.get("userProperty") or {}
        name, seq, ts = env["eventName"], env.get("sequenceId"), env.get("ts")
    else:
        raise CheckFail(f"JSONL dòng không phải hàng sink cũng không phải dòng fixture: {where}")
    user = _User(str(up.get("user_id") or ""), str(up.get("session_id") or ""))
    req = _Req(str(name), data, _int(seq), _int(ts), user, {k: env[k] for k in ENVELOPE if k in env})
    return {"req": req, "name": req.eventName, "app_id": str(item.get("app_id") or ""), "user_id": user.user_id,
            "session_id": user.session_id, "seq": req.sequenceId, "ts": req.ts, "ts_date": item.get("client_ts_date"),
            "event_id": str(req.model_extra.get("event_id") or ""), "received": _int(item.get("received_ts_ms")),
            "request_id": str(data.get("request_id") or ""), "imp_id": str(data.get("imp_id") or ""),
            "col_key": item.get("dedup_key")}


def read_jsonl(path: str) -> list[Row]:
    if not Path(path).is_file():
        raise CheckFail(f"JSONL không có: {path}")
    rows: list[Row] = []
    for n, line in enumerate(Path(path).read_text(encoding="utf-8").splitlines(), 1):
        if not line.strip():
            continue
        try:
            item = json.loads(line)
        except ValueError:
            item = None
        if not isinstance(item, dict):
            raise CheckFail(f"JSONL dòng không parse được thành object: {path}:{n}")
        rows.append(_row(item, f"{path}:{n}"))
    if not rows:
        raise CheckFail(f"JSONL 0 dòng: {path}")
    return rows


def read_log(path: str, what: str) -> str:
    text = Path(path).read_text(encoding="utf-8", errors="replace") if Path(path).is_file() else ""
    if not text.strip():
        raise CheckFail(f"log {what} không có hoặc rỗng: {path}")
    return text


def keyed(rows: list[Row]) -> tuple[dict[tuple[str, str], Row], int]:
    """Một hàng mỗi `(app_id, dedup_key)` như MERGE; trả thêm số trùng thô. Cột `dedup_key` của sink phải trùng khoá tính lại."""
    dedup_key = load_dedup_key()
    kept: dict[tuple[str, str], Row] = {}
    for r in rows:
        key = dedup_key(r["req"])
        if r["col_key"] is not None and r["col_key"] != key:
            raise CheckFail(f"dedup_key lệch: sink ghi {r['col_key']}, hàm backend tính {key} ({r['name']} {r['event_id']})")
        kept.setdefault((r["app_id"], key), r)
    return kept, len(rows) - len(kept)


def _last(regex: re.Pattern[str], text: str) -> str | None:
    return (regex.findall(text) or [None])[-1]


def _stat(text: str, sid: str, field: str) -> int | None:
    vals = [_int(m) for s, rest in RE_STATS.findall(text) if s == sid for m in re.findall(rf"\b{field}=(\d+)", rest)]
    return max(vals) if vals else None


class Report:
    failed = False

    def line(self, tag: str, ok: bool, msg: str) -> None:
        self.failed |= not ok
        print(f"G11 {tag} {'PASS' if ok else 'FAIL'}: {msg}")


def _check_i(rep: Report, rows: list[Row], lb: str, sid_a: str, t_c: int) -> None:
    """accepted CHỈ từ dòng stats của pha B (cùng process, in sau khi đuôi pha A đã nhận; dòng pha A thiếu đuôi)."""
    n = _stat(lb, sid_a, "accepted")
    if n is None:
        raise CheckFail(f"(i) không quyết được: pha B không có dòng 'logevent stats session={sid_a}' "
                        "(process đổi hoặc không ON_STOP) → lượt đo invalid, chạy lại")
    ids = {r["event_id"] for r in rows if r["session_id"] == sid_a and r["received"] < t_c and r["event_id"]}
    rep.line("(i)", n >= 1 and len(ids) == n,
             f"session {sid_a}: accepted={n} (SDK), event_id distinct trong JSONL trước pha C={len(ids)}")


def _check_ii(rep: Report, rows: list[Row], stored: list[tuple[str, str]], t_c: int) -> None:
    """Mỗi event bền pha B có trong JSONL và CHỈ tới sink từ pha C (tới sớm = sink còn tới được, kill/replay không được thử)."""
    if not stored:
        rep.line("(ii)", False, "pha B không có dòng 'logevent durable stored <name> <event_id>' (fail-closed)")
        return
    lost = [f"{name} {eid}" for name, eid in stored if eid not in {r["event_id"] for r in rows}]
    early = [f"{n} {e}" for n, e in stored if any(r["event_id"] == e and r["received"] < t_c for r in rows)]
    rep.line("(ii)", not lost and not early, f"{len(stored)} event bền pha B, mất={len(lost)}" + (f": {', '.join(lost)}" if lost else "")
             + (f"; pha B: {', '.join(early)} tới sink trước pha C (sink còn tới được, kill/replay không được thử)" if early else ""))


def _check_iii(rep: Report, kept: dict[tuple[str, str], Row], raw_dup: int) -> None:
    """Cùng một lần phát (app, user, session, tên, sequence_id, ts) còn > 1 hàng sau dedup = trùng."""
    same: dict[tuple[Any, ...], list[str]] = defaultdict(list)
    for (_, key), r in kept.items():
        same[(r["app_id"], r["user_id"], r["session_id"], r["name"], r["seq"], r["ts"])].append(key)
    dups = {k: v for k, v in same.items() if len(v) > 1}
    n = sum(len(v) - 1 for v in dups.values())
    detail = "; ".join(f"{k[3]} seq={k[4]} ts={k[5]} -> {v}" for k, v in list(dups.items())[:5])
    rep.line("(iii)", n == 0, f"dedup_key distinct={len(kept)}, trùng sau dedup={n}, trùng thô={raw_dup} (ghi)"
             + (f": {detail}" if detail else ""))


def _check_iv(rep: Report, rows: list[Row], stored: list[tuple[str, str]], sid_b: str, t_b: int, t_c: int) -> None:
    """Replay giữ đúng body pha B: session_id pha B, ts lúc phát trong [t_b - 5 s, t_c) (đồng hồ máy chủ; 5 s = lệch máy),
    client_ts_date = ngày UTC của ts. Replay đóng dấu ts lại lúc replay (>= t_c) = FAIL."""
    eids = {eid for _, eid in stored}
    replay = [r for r in rows if r["event_id"] in eids]
    utc = lambda ts: datetime.fromtimestamp(ts / 1000, timezone.utc).strftime("%Y-%m-%d")  # noqa: E731
    bad = [f"{r['name']} {r['event_id']} session={r['session_id']} ts={r['ts']}" for r in replay
           if r["session_id"] != sid_b or not t_b - 5000 <= r["ts"] < t_c
           or (r["ts_date"] is not None and r["ts_date"] != utc(r["ts"]))]
    rep.line("(iv)", bool(replay) and not bad,
             f"{len(replay)} hàng replay giữ session pha B ({sid_b}) và ts lúc phát trong [t_b - 5 s, t_c) = [{t_b - 5000}, {t_c})"
             + (f"; sai: {', '.join(bad)}" if bad else "") + ("" if replay else "; 0 hàng replay"))


def cmd_g11(args: dict[str, str]) -> bool:
    rows = read_jsonl(args["--jsonl"])
    la, lb, lc = read_log(args["--log-a"], "pha A"), read_log(args["--log-b"], "pha B"), read_log(args["--log-c"], "pha C")
    t_b, t_c = _int(args["--t-b"]), _int(args["--t-c"])
    if not 0 < t_b <= t_c:
        raise CheckFail(f"--t-b/--t-c phải là epoch ms, t_b <= t_c: {args['--t-b']} {args['--t-c']}")
    sid_a, sid_c = _last(RE_SESSION, la), _last(RE_SESSION, lc)
    if not sid_a or not sid_c:
        raise CheckFail("thiếu dòng 'logevent: session_id=' ở log pha A hoặc pha C")
    sid_b = _last(RE_SESSION, lb) or sid_a  # pha B mở ấm: cùng process (cùng session) với pha A
    stored = RE_STORED.findall(lb)
    kept, raw_dup = keyed(rows)
    rep = Report()
    print(f"G11 jsonl={args['--jsonl']} rows={len(rows)} session A={sid_a} B={sid_b} C={sid_c} t_b={t_b} t_c={t_c}")
    _check_i(rep, rows, lb, sid_a, t_c)
    _check_ii(rep, rows, stored, t_c)
    _check_iii(rep, kept, raw_dup)
    _check_iv(rep, rows, stored, sid_b, t_b, t_c)
    ends = Counter(r["request_id"] for r in kept.values() if r["name"] in TERMINAL and r["request_id"])
    many = sorted(k for k, v in ends.items() if v > 1)
    rep.line("(v)", not many, f"{len(ends)} request_id có kết thúc, > 1 kết thúc: {len(many)}" + (f": {many[:5]}" if many else ""))
    a, b = _stat(la, sid_b, "built"), _stat(lb, sid_b, "built")
    if a is None or b is None:
        print("G11 (vi) INFO: không đủ dòng 'logevent stats … built=' ở pha A và B để tính best-effort mất (ghi)")
    else:
        print(f"G11 (vi) INFO: pha B dựng {b - a} event, bền {len(stored)}, best-effort mất={max(b - a - len(stored), 0)} (ghi)")
    return not rep.failed


def cmd_h6a(args: dict[str, str]) -> bool:
    text, rows, sid = read_log(args["--applog"], "app"), read_jsonl(args["--jsonl"]), args["--session"]
    fa_lines = [ln for ln in text.splitlines() if FA_APP in ln]
    fa = [m.group(1) for ln in fa_lines if (m := RE_FA_IMP.search(ln))]
    am = sum(1 for ln in text.splitlines() if FA_AM in ln)
    af_all = RE_AF.findall(text)
    af = [imp for imp, started in af_all if started == "1"]
    rev = [r["imp_id"] for r in keyed(rows)[0].values() if r["name"] == "ad_revenue" and r["session_id"] == sid]
    print(f"H6a session={sid}: (1) FA origin=app ad_impression có imp_id={len(fa)} (không imp_id: {len(fa_lines) - len(fa)}) "
          f"(2) rev af started=1={len(af)} (started=0: {len(af_all) - len(af)}) (3) JSONL ad_revenue={len(rev)}; "
          f"origin=am={am} (ghi, không chặn)")
    sets = {"FA": set(fa), "rev af": set(af), "JSONL": set(rev)}
    diff = False
    for name, got in sets.items():
        others = set().union(*(v for k, v in sets.items() if k != name))
        if got ^ others:
            diff = True
            print(f"  {name}: chỉ ở {name}={sorted(got - others)} thiếu so với nguồn khác={sorted(others - got)}")
    if rev.count(""):
        print(f"  JSONL: {rev.count('')} hàng ad_revenue imp_id rỗng")
    return len(fa) == len(af) == len(rev) >= 1 and not diff and not rev.count("") and len(fa_lines) == len(fa)


def _opts(argv: list[str], names: tuple[str, ...]) -> dict[str, str]:
    if len(argv) % 2 or any(k not in names for k in argv[::2]):
        raise CheckFail(f"đối số lạ hoặc thiếu giá trị: {' '.join(argv)} (nhận {' '.join(names)})")
    out = dict(zip(argv[::2], argv[1::2]))
    if [n for n in names if n not in out and n != "--need"]:
        raise CheckFail(f"thiếu một trong {' '.join(names)}")
    return out


def main(argv: list[str]) -> int:
    try:
        if argv[:1] == ["g11"]:
            ok = cmd_g11(_opts(argv[1:], ("--jsonl", "--log-a", "--log-b", "--log-c", "--t-b", "--t-c")))
        elif argv[:1] == ["h6a"]:
            ok = cmd_h6a(_opts(argv[1:], ("--applog", "--jsonl", "--session")))
        elif argv[:1] == ["--local-days"] and len(argv) >= 2:
            from measure_days import cmd_local_days  # noqa: PLC0415  (cùng thư mục tools/)
            if (need := _int(_opts(argv[2:], ("--need",)).get("--need", "3"))) < 1:
                raise CheckFail("--need phải là số nguyên >= 1")
            ok = cmd_local_days(argv[1], need, read_jsonl, CheckFail)
        else:
            raise CheckFail("dùng: measure_check.py g11 … | h6a … | --local-days <dir> [--need n] (một phiên = ngày UTC+7 của "
                            "client_ts_ms đầu tiên)")
    except Exception as exc:  # noqa: BLE001  fail-closed: kể cả lỗi không lường trước, không bao giờ thành PASS
        print(f"FAIL: {exc}" if isinstance(exc, CheckFail) else f"FAIL: lỗi không mong đợi {type(exc).__name__}: {exc}")
        ok = False
    print(f"RESULT: {'PASS' if ok else 'FAIL'}")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
