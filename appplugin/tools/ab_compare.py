#!/usr/bin/env python3
"""2.4.0 NG6: so sánh telemetry A (Legacy/utility) với B (Next-Gen) từ sink --measure (JSONL). Python 3.12 stdlib.
Dùng: tools/ab_compare.py --a <sink>... --b <sink>... [--min-n 10]; <sink> = thư mục có events.jsonl hoặc file .jsonl; xen kẽ A B A B
(lượt i của A ghép lượt i của B). Thiếu/rỗng/JSON hỏng/số lượt A != B = FAIL (fail-closed). T1..T7: plan 2.4.0 Hợp đồng C.
Ra "Tn PASS|FAIL|INFO" rồi "RESULT: PASS|FAIL". FAIL do no-fill: chạy A/B x2 trước khi phân loại (metrics-220 #11)."""
import argparse
import json
import os
import statistics
import sys
from collections import Counter, defaultdict

# EventNames.kt:19-21,43-48. ad_show_fail_reason không còn phát từ schema v2 (lý do nằm ở trường `reason` của ad_show_fail).
EVENTS = ("ad_request", "ad_fill", "ad_no_fill", "ad_show", "ad_revenue", "ad_show_fail", "ad_show_fail_reason", "ad_click")
SERVER_KEYS = {"received_ts_ms", "received_date", "is_known_event", "raw_event_data", "app_id"}  # do sink thêm, không phải SDK
PRECISION, FILL_DROP_PP, REQUIRED, STOCHASTIC = (0, 1, 2, 3), 10, ("ad_request", "ad_fill", "ad_show"), ("ad_no_fill", "ad_click", "ad_show_fail", "ad_revenue"); SHOW_FULL_FORMATS = ("interstitial", "rewarded", "rewarded_interstitial")  # T1: REQUIRED cả hai phía, STOCHASTIC được vắng một phía (info)


class SinkError(Exception):
    pass

def load_run(path):
    """Đọc một lượt: list hàng thuộc EVENTS, bỏ trùng event_id. Lỗi đầu vào -> SinkError."""
    f = os.path.join(path, "events.jsonl") if os.path.isdir(path) else path
    if not os.path.isfile(f):
        raise SinkError("không có sink %s" % path)
    rows, seen = [], set()
    with open(f, encoding="utf-8") as fh:
        for n, line in enumerate(fh, 1):
            if not line.strip():
                continue
            try:
                row = json.loads(line)
            except ValueError:
                raise SinkError("%s:%d JSON hỏng" % (f, n))
            if not isinstance(row, dict) or row.get("event_name") not in EVENTS:
                continue
            eid = row.get("event_id")
            if eid is not None and eid in seen:
                continue
            seen.add(eid)
            rows.append(row)
    if not rows:
        raise SinkError("sink %s không có dòng nào của %s" % (path, ", ".join(EVENTS)))
    return rows


def flat(runs):
    return [r for run in runs for r in run]


def by_event(rows, name):
    return [r for r in rows if r["event_name"] == name]


def is_int(v):
    return isinstance(v, int) and not isinstance(v, bool)


def keysets(rows):
    out = defaultdict(set)
    for r in rows:
        out[r["event_name"]] |= set(r) - SERVER_KEYS
    return out


def t1(a, b):
    ka, kb = keysets(a), keysets(b)
    bad = ["event %s bắt buộc ở cả hai phía, thiếu ở %s" % (e, "A" if e not in ka else "B") for e in REQUIRED if e not in ka or e not in kb]
    bad += ["event %s chỉ có ở %s" % (e, "A" if e in ka else "B") for e in sorted(set(ka) ^ set(kb)) if e not in STOCHASTIC and e not in REQUIRED]
    bad += ["%s: B thiếu %s, B thừa %s" % (e, sorted(ka[e] - kb[e]), sorted(kb[e] - ka[e])) for e in sorted(set(ka) & set(kb)) if ka[e] != kb[e]]
    info = ["info: %s chỉ có ở %s (ngẫu nhiên, không FAIL)" % (e, "A" if e in ka else "B") for e in sorted(set(ka) ^ set(kb)) if e in STOCHASTIC]
    return ("FAIL", "; ".join(bad)) if bad else ("PASS", "%d event, khoá giống nhau (Δ 0)%s" % (len(set(ka) & set(kb)), "".join("; " + x for x in info)))


def networks(rows):
    out = defaultdict(set)
    for r in by_event(rows, "ad_revenue"):
        out[r.get("ad_format")].add(str(r.get("ad_network") or "").strip().lower())
    return out


def t2(a, b):
    na, nb = networks(a), networks(b)
    bad = ["%s: ad_network rỗng/unknown ở B" % f for f, v in sorted(nb.items(), key=str) if v & {"", "unknown"}]
    bad += ["%s: A=%s B=%s" % (f, sorted(na.get(f, ())), sorted(nb.get(f, ()))) for f in sorted(set(na) | set(nb), key=str) if na.get(f) != nb.get(f)]
    return ("FAIL", "; ".join(bad)) if bad else ("PASS", "%d format, tập ad_network A = B" % len(nb))


def modes(values):
    c = Counter(values)
    return {v for v, n in c.items() if n == max(c.values())}


def t3(a, b):
    rb = by_event(b, "ad_revenue")
    if not rb:
        return "FAIL", "B không có ad_revenue"
    bad = [r.get("precision_type") for r in rb if not (is_int(r.get("precision_type")) and r["precision_type"] in PRECISION)]
    if bad:
        return "FAIL", "%d/%d event B có precision_type ngoài {0,1,2,3}: %s" % (len(bad), len(rb), sorted(set(map(str, bad))))
    msg, small = [], []
    for f in sorted({r.get("ad_format") for r in rb}, key=str):
        va = [r["precision_type"] for r in by_event(a, "ad_revenue") if r.get("ad_format") == f and is_int(r.get("precision_type"))]
        vb = [r["precision_type"] for r in rb if r.get("ad_format") == f]
        if min(len(va), len(vb)) < 3:
            small.append("%s n=%d/%d" % (f, len(va), len(vb)))
        elif modes(va) != modes(vb):
            msg.append("%s: mode A=%s B=%s" % (f, sorted(modes(va)), sorted(modes(vb))))
    if msg:
        return "FAIL", "; ".join(msg)
    return "PASS", "100%% trong {0..3}%s" % (("; info n<3 (không so mode): " + ", ".join(small)) if small else "")


def t4(a, b):
    rb = by_event(b, "ad_revenue")
    bad = [r.get("value_micros") for r in rb if not (is_int(r.get("value_micros")) and r["value_micros"] >= 0)]
    if not rb or bad:
        return "FAIL", "B: %s" % ("không có ad_revenue" if not rb else "%d/%d value_micros không phải số nguyên >= 0: %s" % (len(bad), len(rb), bad[:3]))
    sa = sum(r["value_micros"] for r in by_event(a, "ad_revenue") if is_int(r.get("value_micros")))
    sb = sum(r["value_micros"] for r in rb)
    if sa == 0:
        return ("FAIL", "tổng A = 0 nhưng tổng B = %d" % sb) if sb else ("PASS", "tổng A = tổng B = 0 (test ad)")
    ma = statistics.median(r["value_micros"] for r in by_event(a, "ad_revenue") if is_int(r.get("value_micros")))
    ratio = statistics.median(r["value_micros"] for r in rb) / ma if ma else float("inf")
    return "INFO", "tỉ lệ trung vị B/A = %.2f (%s [0,5; 2])" % (ratio, "trong" if 0.5 <= ratio <= 2 else "NGOÀI")


def t5(a_runs, b_runs):
    bad = []
    for i, (ra, rb) in enumerate(zip(a_runs, b_runs), 1):
        fa, fb = len(by_event(ra, "ad_show_fail")), len(by_event(rb, "ad_show_fail"))
        if fb > fa + 1:
            bad.append("cặp %d: ad_show_fail B=%d > A=%d + 1" % (i, fb, fa))
    nore = [r for r in by_event(flat(b_runs), "ad_show_fail") if not (r.get("reason") or r.get("ad_show_fail_reason"))]
    bad += ["%d ad_show_fail ở B không có lý do (reason/ad_show_fail_reason)" % len(nore)] if nore else []
    return ("FAIL", "; ".join(bad)) if bad else ("PASS", "%d cặp, B <= A + 1, mọi ad_show_fail của B có lý do" % len(a_runs))


def fills(rows):
    out = defaultdict(lambda: [0, 0])
    for r in rows:
        if r["event_name"] == "ad_fill":
            out[r.get("ad_format")][0] += 1
        elif r["event_name"] == "ad_no_fill":
            out[r.get("ad_format")][1] += 1
    return out


def t6(a, b, min_n):
    fa, fb = fills(a), fills(b)
    bad, info = [], []
    for f in sorted(set(fa) | set(fb), key=str):
        (xa, ya), (xb, yb) = fa.get(f, [0, 0]), fb.get(f, [0, 0])
        na, nb = xa + ya, xb + yb
        if na < min_n or nb < min_n:
            info.append("%s n=%d/%d" % (f, na, nb))
        elif 100.0 * xb / nb < 100.0 * xa / na - FILL_DROP_PP:
            bad.append("%s fill A=%.0f%% B=%.0f%%" % (f, 100.0 * xa / na, 100.0 * xb / nb))
    if bad:
        return "FAIL", "; ".join(bad) + " (do no-fill: chạy A/B x2 trước khi phân loại, metrics-220 #11)"
    return ("INFO" if info else "PASS"), "fill B >= A - %d pp khi n >= %d%s" % (FILL_DROP_PP, min_n, ("; n nhỏ (info): " + ", ".join(info)) if info else "")


def t7(a, b):
    sa, sb = (Counter(r.get("ad_format") for r in by_event(x, "ad_show")) for x in (a, b))
    base = [f for f in SHOW_FULL_FORMATS if sa[f] > 0]
    miss = [f for f in base if sb[f] == 0]
    if not base:
        return "FAIL", "A không có ad_show nào của %s (không có gì để so)" % list(SHOW_FULL_FORMATS)
    return ("FAIL", "B không có ad_show %s" % miss) if miss else ("PASS", "%d/%d format full ở B như A: %s" % (len(base), len(base), {f: sb[f] for f in base}))


def main(argv=None):
    p = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    p.add_argument("--a", nargs="+", required=True, metavar="SINK")
    p.add_argument("--b", nargs="+", required=True, metavar="SINK")
    p.add_argument("--min-n", type=int, default=10)
    args = p.parse_args(argv)
    try:
        if len(args.a) != len(args.b):
            raise SinkError("số lượt A (%d) != số lượt B (%d): ghép cặp được là xen kẽ A B A B" % (len(args.a), len(args.b)))
        a_runs, b_runs = [load_run(s) for s in args.a], [load_run(s) for s in args.b]
    except SinkError as e:
        print("FAIL: %s" % e)
        print("RESULT: FAIL")
        return 1
    a, b = flat(a_runs), flat(b_runs)
    ca, cb = (Counter(r["event_name"] for r in x) for x in (a, b))
    print("events A/B: " + " ".join("%s=%d/%d" % (e, ca[e], cb[e]) for e in EVENTS))
    results = [t1(a, b), t2(a, b), t3(a, b), t4(a, b), t5(a_runs, b_runs), t6(a, b, args.min_n), t7(a, b)]
    for n, (st, msg) in enumerate(results, 1):
        print("T%d %s %s" % (n, st, msg))
    ok = all(st != "FAIL" for st, _ in results)
    print("RESULT: %s" % ("PASS" if ok else "FAIL"))
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
