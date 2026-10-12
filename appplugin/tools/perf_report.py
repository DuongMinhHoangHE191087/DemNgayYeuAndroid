#!/usr/bin/env python3
"""2.1.0 Task 0f (PERF, plan §D): bảng hiệu năng từ JSONL của tools/perf_gate.sh (+ sink) và so với tools/perf-thresholds.json.

Dùng: tools/perf_report.py --jsonl <perf.jsonl> [--jsonl ...] [--sink-dir <dir|events.jsonl>] [--thresholds <json>]
        --device <tên> [--min-runs N]
Chỉ số: trung vị + p90 (nội suy tuyến tính, kiểu numpy) mỗi chỉ số. Từ lượt đo: oncreate_ms, initplugin_total_ms, total_ms, wait_ms,
prefs_waited_ms, by_sdk, first_* (lượt pm clear), pss_*_kb. Từ sink: fill_ms.<format> (ad_request -> ad_fill/ad_no_fill ghép theo
request_id; fill không có request thì bỏ + đếm), fill_rate = fill/(fill+no_fill), fill_per_request = fill/request,
show_ms.<format> (ad_show_attempt ready=1 -> ad_show cùng session + placement), ttfr_ms (consent_choice, không có thì session_start
-> ad_request đầu của session). Sự kiện khử trùng theo event_id.
Ngưỡng: {"schema_version":1,"provisional":bool,"devices":{<device>:{<metric>:{"med","p90","max","min","kind":"hard|info"}}}}.
`hard` vượt -> FAIL (exit 1); `info` vượt -> chỉ in. Thiếu dữ liệu / JSON hỏng / ngưỡng hỏng = FAIL (không bỏ qua, #11).
"""
import argparse
import json
import os
import sys


class DataError(Exception):
    pass


def percentile(vals, p):
    s = sorted(vals)
    if not s:
        raise DataError("percentile của tập rỗng")
    pos = (len(s) - 1) * p / 100.0
    lo = int(pos)
    hi = min(lo + 1, len(s) - 1)
    return s[lo] + (s[hi] - s[lo]) * (pos - lo)


def stats(vals):
    return {"n": len(vals), "med": percentile(vals, 50), "p90": percentile(vals, 90), "max": max(vals), "min": min(vals)}


def rate(num, den):
    v = num / den
    return {"n": den, "med": v, "p90": v, "max": v, "min": v}


def read_jsonl(path):
    """Từng dòng là một JSON object; một dòng hỏng = DataError (không bỏ qua)."""
    out = []
    try:
        fh = open(path, encoding="utf-8")
    except OSError as e:
        raise DataError("không đọc được %s: %s" % (path, e))
    with fh:
        for i, line in enumerate(fh, 1):
            if not line.strip():
                continue
            try:
                obj = json.loads(line)
            except ValueError as e:
                raise DataError("%s:%d JSON hỏng: %s" % (path, i, e))
            if not isinstance(obj, dict):
                raise DataError("%s:%d không phải object" % (path, i))
            out.append(obj)
    return out


def num(v):
    return isinstance(v, (int, float)) and not isinstance(v, bool)


def run_metrics(paths):
    m = {}
    nruns = 0

    def add(name, v):
        if num(v):
            m.setdefault(name, []).append(v)

    for p in paths:
        for o in read_jsonl(p):
            if ("run" in o or o.get("first_run") is True) and "oncreate_ms" in o:
                if not num(o["oncreate_ms"]):
                    raise DataError("%s: lượt thiếu oncreate_ms" % p)
                pre, nruns = ("first_", nruns) if o.get("first_run") else ("", nruns + 1)
                add(pre + "oncreate_ms", o["oncreate_ms"])
                add(pre + "total_ms", o.get("total_ms"))
                add(pre + "initplugin_total_ms", (o.get("initplugin") or {}).get("total"))
                if not pre:
                    add("wait_ms", o.get("wait_ms"))
                    add("prefs_waited_ms", o.get("prefs_waited_ms"))
                add(pre + "by_sdk", o.get("by_sdk"))
            elif o.get("meminfo"):
                for key, name in (("pss_kb", "pss"), ("java_heap_kb", "java_heap"), ("native_heap_kb", "native_heap")):
                    for ph, v in (o.get(key) or {}).items():
                        add("%s_%s_kb" % (name, ph), v)
    if nruns == 0:
        raise DataError("không có lượt đo nào trong --jsonl")
    return {k: stats(v) for k, v in m.items()}, nruns


def raw_of(e, path):
    r = e.get("raw_event_data", {})
    if isinstance(r, str):
        try:
            r = json.loads(r) if r else {}
        except ValueError as ex:
            raise DataError("%s: raw_event_data hỏng (event_id=%s): %s" % (path, e.get("event_id"), ex))
    if not isinstance(r, dict):
        raise DataError("%s: raw_event_data không phải object (event_id=%s)" % (path, e.get("event_id")))
    return r


def load_sink(path):
    if os.path.isdir(path):
        path = os.path.join(path, "events.jsonl")
    seen, evs = set(), []
    for e in read_jsonl(path):
        if "event_name" not in e or not num(e.get("client_ts_ms")):
            raise DataError("%s: sự kiện thiếu event_name/client_ts_ms" % path)
        eid = e.get("event_id")
        if eid is not None and eid in seen:
            continue
        seen.add(eid)
        e["_raw"] = raw_of(e, path)
        evs.append(e)
    evs.sort(key=lambda e: e["client_ts_ms"])
    return evs


def sink_metrics(evs):
    m, info = {}, {}
    reqs, answered = {}, set()
    fills = no_fills = orphans = 0
    consent, start, req_ts, pend = {}, {}, {}, {}

    def add(name, v):
        m.setdefault(name, []).append(v)

    for e in evs:
        n, r, s = e["event_name"], e["_raw"], e.get("session_id")
        if n == "ad_request":
            reqs[r.get("request_id")] = e
        elif n in ("ad_fill", "ad_no_fill"):
            rq = reqs.get(r.get("request_id"))
            if rq is None:
                orphans += 1
                continue
            if r["request_id"] in answered:
                continue
            answered.add(r["request_id"])
            if n == "ad_no_fill":
                no_fills += 1
                continue
            fills += 1
            if num(r.get("latency_ms")):
                fmt = rq["_raw"].get("ad_format", "?")
                add("fill_ms." + fmt, r["latency_ms"])
                add("fill_ms.all", r["latency_ms"])
        elif n == "ad_show_attempt" and str(r.get("ready")) in ("1", "True", "true"):
            pend[(s, r.get("placement"))] = e["client_ts_ms"]
        elif n == "ad_show" and (s, r.get("placement")) in pend:
            d = e["client_ts_ms"] - pend.pop((s, r.get("placement")))
            add("show_ms." + str(r.get("ad_format", "?")), d)
            add("show_ms.all", d)
        if n == "consent_choice":
            consent.setdefault(s, e["client_ts_ms"])
        elif n == "session_start":
            start.setdefault(s, e["client_ts_ms"])
        elif n == "ad_request":
            req_ts.setdefault(s, []).append(e["client_ts_ms"])
    for s, ts in req_ts.items():  # mốc = consent_choice đầu của session, không có thì session_start; ad_request đầu tiên từ mốc đó
        base = consent.get(s, start.get(s))
        after = [t for t in ts if base is not None and t >= base]
        if after:
            add("ttfr_ms", min(after) - base)
    out = {k: stats(v) for k, v in m.items()}
    if fills + no_fills:
        out["fill_rate"] = rate(fills, fills + no_fills)
    if reqs:
        out["fill_per_request"] = rate(fills, len(reqs))
    info = {"requests": len(reqs), "fills": fills, "no_fills": no_fills, "orphan_responses": orphans,
            "requests_unanswered": len(reqs) - len(answered)}
    return out, info


def load_thresholds(path, device):
    try:
        with open(path, encoding="utf-8") as fh:
            t = json.load(fh)
    except (OSError, ValueError) as e:
        raise DataError("ngưỡng %s hỏng/vắng: %s" % (path, e))
    if t.get("schema_version") != 1 or not isinstance(t.get("devices"), dict):
        raise DataError("ngưỡng %s: cần schema_version 1 và khoá devices" % path)
    dev = t["devices"].get(device)
    if not isinstance(dev, dict):
        return {}, bool(t.get("provisional")), False
    for metric, rule in dev.items():
        if not isinstance(rule, dict) or rule.get("kind") not in ("hard", "info"):
            raise DataError("ngưỡng %s/%s: thiếu kind hard|info" % (device, metric))
        for k in ("med", "p90", "max", "min"):
            if k in rule and not num(rule[k]):
                raise DataError("ngưỡng %s/%s.%s không phải số" % (device, metric, k))
        mn = rule.get("min_n")  # 2.4.0 CARRY-T #5: min_n phải là số nguyên >= 1 (bool/chuỗi/0 -> FAIL, không bỏ qua im lặng)
        if "min_n" in rule and (isinstance(mn, bool) or not isinstance(mn, int) or mn < 1):
            raise DataError("ngưỡng %s/%s.min_n phải là số nguyên >= 1 (nhận %r)" % (device, metric, mn))
    return dev, bool(t.get("provisional")), True


def evaluate(rule, obs):
    """Danh sách (khoá, giới hạn, giá trị, vượt?) cho một chỉ số."""
    res = []
    for k in ("med", "p90", "max"):
        if k in rule:
            res.append((k, rule[k], obs[k], obs[k] > rule[k]))
    if "min" in rule:
        res.append(("min", rule["min"], obs["med"], obs["med"] < rule["min"]))
    return res


def fmt(v):
    return "%g" % round(v, 2) if num(v) else "-"


def main(argv):
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--jsonl", action="append", required=True)
    ap.add_argument("--sink-dir")
    ap.add_argument("--thresholds", required=True)
    ap.add_argument("--device", required=True)
    ap.add_argument("--min-runs", type=int, default=1)
    a = ap.parse_args(argv)
    try:
        st, nruns = run_metrics(a.jsonl)
        info = None
        if a.sink_dir:
            sm, info = sink_metrics(load_sink(a.sink_dir))
            st.update(sm)
        rules, prov, found = load_thresholds(a.thresholds, a.device)
    except DataError as e:
        print("FAIL: %s" % e)
        print("RESULT: FAIL")
        return 1
    failed = []
    if nruns < a.min_runs:
        failed.append("số lượt cold %d < --min-runs %d" % (nruns, a.min_runs))
    print("## Hiệu năng %s (cold n=%d%s)" % (a.device, nruns, ", ngưỡng TẠM (provisional)" if prov else ""))
    if not found:
        print("(không có ngưỡng cho thiết bị '%s' trong %s: chỉ in số)" % (a.device, a.thresholds))
    print("\n| chỉ số | n | trung vị | p90 | max | giới hạn | loại | trạng thái |\n|---|---|---|---|---|---|---|---|")
    for name in sorted(set(st) | set(rules)):
        obs, rule = st.get(name), rules.get(name)
        if obs is None:
            if rule and rule["kind"] == "hard" and (a.sink_dir or not name.startswith(("fill", "show", "ttfr"))):
                failed.append("%s: thiếu dữ liệu cho ngưỡng hard" % name)
                print("| %s | 0 | - | - | - | %s | hard | FAIL (thiếu dữ liệu) |" % (name, json.dumps({k: v for k, v in rule.items() if k != "kind"})))
            else:  # 2.3.0 PERF2 m1: luật không có dữ liệu vẫn có hàng (không im lặng)
                print("| %s | 0 | - | - | - | - | %s | info (không có dữ liệu) |" % (name, rule["kind"] if rule else "-"))
            continue
        lim, kind, status = "-", "-", "-"
        if rule:
            kind, ev = rule["kind"], evaluate(rule, obs)
            lim = " ".join("%s<=%s" % (k, fmt(b)) if k != "min" else "min>=%s" % fmt(b) for k, b, _, _ in ev)
            over = [k for k, _, _, o in ev if o]
            if rule.get("min_n") and kind == "hard" and obs["n"] < rule["min_n"]:
                status = "FAIL (n=%d < min_n=%d)" % (obs["n"], rule["min_n"])  # 2.3.0 PERF2 m1: mẫu quá ít cho luật hard
                failed.append("%s: n=%d < min_n=%d" % (name, obs["n"], rule["min_n"]))
            elif not ev:
                status = "info (không có giới hạn số)"
            elif over and kind == "hard":
                status = "FAIL (%s)" % ",".join(over)
                failed.append("%s: %s vượt giới hạn" % (name, ",".join(over)))
            elif over:
                status = "info: vượt (%s)" % ",".join(over)
            else:
                status = "OK"
        print("| %s | %d | %s | %s | %s | %s | %s | %s |" % (name, obs["n"], fmt(obs["med"]), fmt(obs["p90"]), fmt(obs["max"]), lim, kind, status))
    if info:
        print("\nsink: " + ", ".join("%s=%s" % kv for kv in info.items()))
    for f in failed:
        print("FAIL: " + f)
    print("RESULT: %s" % ("FAIL" if failed else "PASS"))
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
