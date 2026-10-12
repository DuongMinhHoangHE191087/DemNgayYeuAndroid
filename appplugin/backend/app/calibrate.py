"""Hiệu chỉnh `likelihood_profiles` của artifact brain từ hàng v2 đã lưu (1.9.0 T9, §Hợp đồng E, D8).

Chứng minh ĐƯỜNG ỐNG, không phải con số (D8 "Không tuyên bố"): log quyết định không mang feature quan sát (F7), nên
mỗi người được đo bằng proxy từ các hàng v2 khác, gom theo `segment` của quyết định MỚI NHẤT của người đó (bỏ người có
`segment_confidence` < `min_confidence` của base):

- `rewarded_accept` = min(1, `ad_show` / `ad_show_attempt`) định dạng rewarded (người có ≥ 1 attempt);
- `payer_propensity` = 1 nếu có `iap_purchase`, ngược lại 0 (mọi người của segment);
- `churn_tendency` = 1 nếu khoảng trống lớn nhất giữa các `session_end` (và tới mốc cuối dữ liệu) > 3 ngày (người có
  ≥ 1 `session_end`);
- `fail_rate` = prior luôn (app utility không có nguồn, F7).

`mu = (n·obs + k·prior)/(n + k)` (k = 30), ô có n < `min_support` (30) giữ nguyên prior, ô hiệu chỉnh kẹp [0.02, 0.98].
Đầu ra = TOÀN BỘ `--base` với `likelihood_profiles` thay và `version = calib-<yyyymmdd>-<sha8>`: ngày = ngày UTC+7 mới
nhất của dữ liệu (không đọc đồng hồ), sha8 = sha256 của các hàng đầu vào đã khử trùng, sắp xếp — cùng dữ liệu cho cùng
version. Bảng hỗ trợ (segment × trường: n, obs, prior, mu, used) in ra stdout và ghi cạnh FILE của `--out` (`<tên>.support.md`; `--out` là một FILE, không phải thư mục).

Dùng: `python -m app.calibrate --jsonl <file> | --bq <project.dataset.table> --base <policy.json> --out <FILE>
[--k 30] [--min-support 30] [--endpoint <bigquery-emulator>]`. JSONL nhận cả hàng sink (`/push` ghi) lẫn dòng fixture
`{app_id, received_ts_ms, body}`. BigQuery chỉ qua `bq_setup.make_client` (production bị từ chối trước khi dựng client).
"""

from __future__ import annotations

import argparse
import copy
import hashlib
import json
import os
import sys
from collections import defaultdict
from pathlib import Path
from typing import Any, Iterable, Mapping

from . import ope
from .ad_rows import dedup, local_day
from .bq_setup import SetupRefused, make_client
from .bq_tables import require_identifier, require_project
from .readers import BigQueryReader, JsonlReader

K_DEFAULT = 30
MIN_SUPPORT_DEFAULT = 30
CLAMP_LO, CLAMP_HI = 0.02, 0.98
MIN_CONFIDENCE_DEFAULT = 0.40  # = PersonalizationScenario.MIN_CONFIDENCE của SDK khi base không có `min_confidence`
CHURN_GAP_MS = 3 * 86_400_000
FIELDS = ("fail_rate", "rewarded_accept", "payer_propensity", "churn_tendency")
REWARDED = "rewarded"
INPUT_EVENTS = {"brain_decision", "brain_feedback", "experiment_exposure", "iap_purchase", "session_end",
                "ad_show_attempt", "ad_show"}

Row = dict[str, Any]
UserKey = tuple[str, str]


class CalibrateError(ValueError):
    """Đầu vào không dùng được (base sai, không có hàng): thoát mã 2 với thông báo rõ."""


def _is_input(r: Mapping[str, Any]) -> bool:
    return (r.get("schema_version") == 2 and r.get("event_name") in INPUT_EVENTS
            and (not str(r["event_name"]).startswith("ad_show") or r.get("ad_format") == REWARDED))


def _ts(r: Mapping[str, Any]) -> int:
    """`client_ts_ms` > 0, hoặc 0 (thiếu/hỏng: hàng bị bỏ và được đếm trong bảng hỗ trợ — không thành ngày 1970)."""
    v = r.get("client_ts_ms")
    return v if isinstance(v, int) and not isinstance(v, bool) and v > 0 else 0


def input_rows(rows: Iterable[Mapping[str, Any]]) -> list[Row]:
    """Hàng v2 của các event đầu vào (ad_show/attempt chỉ rewarded) có `client_ts_ms`, đã khử trùng như MERGE."""
    return dedup([dict(r) for r in rows if _is_input(r) and _ts(r)])


def _user(r: Mapping[str, Any]) -> UserKey | None:
    uid = r.get("user_id") or ""
    return (r.get("app_id") or "", uid) if uid else None


def _conf(r: Mapping[str, Any]) -> float:
    return float(r.get("segment_confidence") or 0.0)


def _latest_decisions(rows: list[Row]) -> dict[UserKey, Row]:
    """Quyết định mới nhất mỗi người theo (client_ts_ms, decision_id); quyết định cũ hơn (segment khác) không tính."""
    latest: dict[UserKey, Row] = {}
    for r in rows:
        u = _user(r)
        if r["event_name"] != "brain_decision" or u is None:
            continue
        cur = latest.get(u)
        if cur is None or (_ts(r), r.get("decision_id") or "") > (_ts(cur), cur.get("decision_id") or ""):
            latest[u] = r
    return latest


def _latest_segments(rows: list[Row], min_conf: float) -> dict[UserKey, str]:
    """Segment của quyết định mới nhất mỗi người; người có quyết định mới nhất dưới ngưỡng tin cậy bị bỏ."""
    return {u: r.get("segment") or "" for u, r in _latest_decisions(rows).items() if _conf(r) >= min_conf}


def _min_conf(base: Mapping[str, Any]) -> float:
    return float(base.get("min_confidence", MIN_CONFIDENCE_DEFAULT))


def _observations(rows: list[Row], users: Iterable[UserKey]) -> dict[str, dict[UserKey, float]]:
    """Quan sát proxy mỗi người theo trường (người không có nguồn của trường đó không có quan sát)."""
    attempts: dict[UserKey, int] = defaultdict(int)
    shows: dict[UserKey, int] = defaultdict(int)
    payers: set[UserKey] = set()
    sessions: dict[UserKey, list[int]] = defaultdict(list)
    for r in rows:
        u = _user(r)
        if u is None:
            continue
        name = r["event_name"]
        if name == "ad_show_attempt":
            attempts[u] += 1
        elif name == "ad_show":
            shows[u] += 1
        elif name == "iap_purchase":
            payers.add(u)
        elif name == "session_end":
            sessions[u].append(_ts(r))
    end = max((_ts(r) for r in rows), default=0)
    obs: dict[str, dict[UserKey, float]] = {f: {} for f in FIELDS[1:]}
    for u in users:
        if attempts[u]:
            obs["rewarded_accept"][u] = min(1.0, shows[u] / attempts[u])
        obs["payer_propensity"][u] = 1.0 if u in payers else 0.0
        if sessions[u]:
            ts = sorted(sessions[u]) + [end]
            obs["churn_tendency"][u] = 1.0 if max(b - a for a, b in zip(ts, ts[1:])) > CHURN_GAP_MS else 0.0
    return obs


def _shrink(n: int, obs: float, prior: float, k: float) -> float:
    return min(CLAMP_HI, max(CLAMP_LO, (n * obs + k * prior) / (n + k)))


def _check_base(base: Any) -> dict[str, dict[str, float]]:
    if not isinstance(base, dict) or not isinstance(base.get("version"), str) or not base["version"]:
        raise CalibrateError("--base thiếu 'version' (SDK từ chối artifact không có version, DOC/35)")
    profiles = base.get("likelihood_profiles")
    if not isinstance(profiles, dict) or not profiles:
        raise CalibrateError("--base thiếu 'likelihood_profiles' (prior của phép co)")
    for seg, prof in profiles.items():
        if not isinstance(prof, dict) or any(not isinstance(prof.get(f), (int, float)) for f in FIELDS):
            raise CalibrateError(f"--base likelihood_profiles.{seg} phải có đủ số {', '.join(FIELDS)}")
    return profiles


def version_of(rows: list[Row]) -> str:
    """`calib-<yyyymmdd>-<sha8>`: ngày UTC+7 mới nhất của dữ liệu; sha của các hàng đã sắp xếp (thứ tự đọc
    không đổi kết quả)."""
    lines = sorted(json.dumps(r, sort_keys=True, ensure_ascii=False, default=str) for r in rows)
    sha8 = hashlib.sha256("\n".join(lines).encode("utf-8")).hexdigest()[:8]
    day = local_day(max(_ts(r) for r in rows)).replace("-", "")
    return f"calib-{day}-{sha8}"


def calibrate(rows: Iterable[Mapping[str, Any]], base: Mapping[str, Any], k: float = K_DEFAULT,
              min_support: int = MIN_SUPPORT_DEFAULT) -> tuple[dict[str, Any], list[dict[str, Any]]]:
    """(artifact, bảng hỗ trợ). Không sửa `base`; mọi khoá ngoài `version`/`likelihood_profiles` chép nguyên."""
    priors = _check_base(base)
    data = input_rows(rows)
    if not data:
        raise CalibrateError("không có hàng v2 đầu vào (brain_decision, iap_purchase, session_end, ...)")
    segments = _latest_segments(data, _min_conf(base))
    obs = _observations(data, segments)
    profiles: dict[str, dict[str, Any]] = {}
    support: list[dict[str, Any]] = []
    for seg, prior in priors.items():
        members = [u for u, s in segments.items() if s == seg]
        profiles[seg] = dict(prior)
        for field in FIELDS:
            vals = [] if field == "fail_rate" else [obs[field][u] for u in members if u in obs[field]]
            n, mean = len(vals), (sum(vals) / len(vals) if vals else None)
            used = "calibrated" if field != "fail_rate" and n >= min_support else "prior"
            if used == "calibrated":
                profiles[seg][field] = _shrink(n, mean, float(prior[field]), k)
            support.append({"segment": seg, "field": field, "n": n, "obs": mean, "prior": prior[field],
                            "mu": profiles[seg][field], "used": used})
    out = copy.deepcopy(dict(base))
    out["version"] = version_of(data)
    out["likelihood_profiles"] = profiles
    return out, support


def _fmt(v: Any) -> str:
    return "-" if v is None else f"{v:.6g}" if isinstance(v, float) else str(v)


def support_table(art: Mapping[str, Any], support: list[dict[str, Any]], raw: list[Row],
                  base: Mapping[str, Any]) -> str:
    """Markdown: tóm tắt (người giữ/bỏ, hàng thiếu `client_ts_ms`, holdout/treatment, OPE SNIPS chỉ tham khảo) + bảng
    segment × trường. Người bị bỏ không biến mất im lặng: đếm theo lý do (độ tin < min_confidence, segment lạ)."""
    rows = input_rows(raw)
    latest = _latest_decisions(rows)
    low = sum(1 for r in latest.values() if _conf(r) < _min_conf(base))
    unknown = sum(1 for s in _latest_segments(rows, _min_conf(base)).values() if s not in base["likelihood_profiles"])
    no_ts = sum(1 for r in raw if _is_input(r) and not _ts(r))
    groups: dict[int, set] = defaultdict(set)
    for r in rows:
        if r["event_name"] in ("brain_decision", "experiment_exposure") and _user(r):
            groups[int(r.get("is_holdout") or 0)].add(_user(r))
    snips = ope.compute(rows)
    lines = [f"# calibrate {art['version']}", "",
             f"- users kept: {len(latest) - low - unknown}; dropped low confidence (< {_min_conf(base)}): {low}; "
             f"dropped unknown segment: {unknown}; rows without client_ts_ms dropped: {no_ts}",
             f"- holdout users: {len(groups[1])}; treatment users: {len(groups[0])}",
             f"- OPE (info, ope.py): verdict={snips['verdict']} uniform.snips="
             f"{_fmt(snips['policies']['uniform']['snips'])}", "",
             "| segment | field | n | obs | prior | mu | used |", "|---|---|---|---|---|---|---|"]
    lines += [f"| {s['segment']} | {s['field']} | {s['n']} | {_fmt(s['obs'])} | {_fmt(s['prior'])} | "
              f"{_fmt(s['mu'])} | {s['used']} |" for s in support]
    return "\n".join(lines) + "\n"


def _from_fixture_line(r: Mapping[str, Any]) -> Row:
    """Dòng fixture `{app_id, received_ts_ms, body}` -> hàng như `/push` ghi; hàng sink giữ nguyên."""
    if "body" in r and "event_name" not in r:
        from .models import EventRequest  # noqa: PLC0415
        from .normalize import ingest_row  # noqa: PLC0415

        return ingest_row(EventRequest.model_validate(r["body"]), r.get("app_id"), int(r["received_ts_ms"]))
    return dict(r)


def _read(args: argparse.Namespace) -> list[Row]:
    if args.jsonl:
        if not Path(args.jsonl).is_file():
            raise CalibrateError(f"--jsonl không có: {args.jsonl}")
        return [_from_fixture_line(r) for r in JsonlReader(args.jsonl).read()]
    parts = args.bq.split(".")
    if len(parts) != 3:
        raise CalibrateError(f"--bq phải dạng <project>.<dataset>.<table>: {args.bq!r}")
    project, dataset, table = parts
    client = make_client(require_project(project), args.endpoint)  # production bị từ chối trước khi dựng client
    return list(BigQueryReader(project, require_identifier(dataset), require_identifier(table, "bảng"), client).read())


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description="Hiệu chỉnh likelihood_profiles (pipeline proof, D8)")
    src = ap.add_mutually_exclusive_group(required=True)
    src.add_argument("--jsonl")
    src.add_argument("--bq", help="<project>.<dataset>.<table>")
    ap.add_argument("--endpoint", default=os.getenv("BRAIN_BQ_ENDPOINT", ""))
    ap.add_argument("--base", required=True)
    ap.add_argument("--out", required=True, metavar="FILE", help="đường dẫn FILE đầu ra (không phải thư mục)")
    ap.add_argument("--k", type=float, default=K_DEFAULT)
    ap.add_argument("--min-support", type=int, default=MIN_SUPPORT_DEFAULT)
    args = ap.parse_args(argv)
    try:
        base = json.loads(Path(args.base).read_text(encoding="utf-8"))
        _check_base(base)
        rows = _read(args)
        art, support = calibrate(rows, base, args.k, args.min_support)
    except (OSError, ValueError, SetupRefused) as exc:  # CalibrateError, JSON hỏng, định danh/đích BigQuery bị từ chối
        print(f"calibrate: REFUSED {exc}", file=sys.stderr)
        return 2
    out = Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(art, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    table = support_table(art, support, rows, base)
    out.with_name(f"{out.stem}.support.md").write_text(table, encoding="utf-8")
    print(table, end="")
    print(f"calibrate: OK {out} version={art['version']}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
