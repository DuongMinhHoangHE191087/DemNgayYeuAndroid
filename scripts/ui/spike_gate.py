"""Gate for P4 UI spike result files: python spike_gate.py <file.md>. Exit 0 = pass, 1 = fail.

Reads the single `RESULT:` line and the `METRIC <name> <value> <op> <threshold>` lines, and checks
them against spike_requirements.json (keyed by file name without `.md`). The file can never loosen
a threshold or op: both must equal the table. A fallback only excuses the metrics it lists.
"""
import json
import operator
import re
import sys
from pathlib import Path

OPS = {"<=": operator.le, ">=": operator.ge, "<": operator.lt, ">": operator.gt, "==": operator.eq}
TABLE_PATH = Path(__file__).resolve().parent / "spike_requirements.json"
METRIC_RE = re.compile(r"^METRIC\s+(\S+)\s+(\S+)\s+(\S+)\s+(\S+)\s*$")


def check(path, table):
    """Return a list of error strings; empty means the gate passes."""
    key = Path(path).name.removesuffix(".md")
    if key not in table:
        return [f"{key}: not in spike_requirements.json"]
    req, fallbacks = table[key]["metrics"], table[key].get("fallbacks", {})
    lines = Path(path).read_text(encoding="utf-8").splitlines()

    results = [l[len("RESULT:"):].strip() for l in lines if l.startswith("RESULT:")]
    if len(results) != 1:
        return [f"expected exactly one RESULT line, found {len(results)}"]
    result = results[0]
    if result in ("PASS", "FAIL"):
        excused = set()
    elif result.startswith("FALLBACK:") and result[len("FALLBACK:"):] in fallbacks:
        excused = set(fallbacks[result[len("FALLBACK:"):]])
    else:
        return [f"RESULT '{result}' is not PASS or a FALLBACK known for {key}"]
    if result == "FAIL":
        return ["RESULT: FAIL"]

    errors, seen = [], {}
    for l in lines:
        if not l.startswith("METRIC"):
            continue
        m = METRIC_RE.match(l)
        if not m:
            errors.append(f"malformed METRIC line: {l}")
            continue
        name, value, op, thr = m.groups()
        if name in seen:
            errors.append(f"duplicate METRIC {name}")
        seen[name] = (value, op, thr)
    errors += [f"missing METRIC {n}" for n in req if n not in seen]
    errors += [f"extra METRIC {n}" for n in seen if n not in req]

    for name, (op, thr) in req.items():
        if name not in seen:
            continue
        value, f_op, f_thr = seen[name]
        try:
            thr_ok = float(f_thr) == float(thr)
        except ValueError:
            thr_ok = False
        if f_op != op or not thr_ok:
            errors.append(f"{name}: op/threshold '{f_op} {f_thr}' differs from table '{op} {thr}'")
            continue
        if name in excused:
            continue
        try:
            ok = OPS[op](float(value), float(thr))
        except ValueError:  # n/a or non-numeric
            ok = False
        if not ok:
            errors.append(f"{name}: value {value} violates {op} {thr}")
    return errors


def main(argv):
    if len(argv) != 2:
        print("usage: spike_gate.py <file.md>", file=sys.stderr)
        return 1
    try:
        table = json.loads(TABLE_PATH.read_text(encoding="utf-8"))
        errors = check(argv[1], table)
    except OSError as e:
        errors = [str(e)]
    for e in errors:
        print(f"spike_gate: {e}", file=sys.stderr)
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
