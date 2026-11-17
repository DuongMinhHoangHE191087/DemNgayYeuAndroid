"""Tests for spike_gate.py: each case writes a sample spike file in a temp dir and checks the exit code."""
import json
import re
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

HERE = Path(__file__).resolve().parent
GATE = HERE / "spike_gate.py"
TABLE = json.loads((HERE / "spike_requirements.json").read_text(encoding="utf-8"))
PLAN = HERE.parent.parent / "docs/superpowers/plans/2026-10-13-kmp-ios/p4-ui-compose.md"

U01 = "p4-u01-lovehome-perf"
U09 = "p4-u09-vietnamese-ime"
U122 = "p4-u122-paywall-manual"


def fmt(n):
    return repr(n) if isinstance(n, float) else str(n)


def good_value(op, thr):
    return thr  # equal to the threshold satisfies <=, >= and ==


def bad_value(op, thr):
    return thr + 1 if op in ("<=", "<", "==") else thr - 1


def metric_line(name, value, op, thr):
    return f"METRIC {name} {fmt(value)} {op} {fmt(thr)}"


def metric_lines(key, values=None, ops=None, thrs=None):
    """Valid METRIC lines for `key`; override per-name value / op / threshold."""
    values, ops, thrs = values or {}, ops or {}, thrs or {}
    out = []
    for name, (op, thr) in TABLE[key]["metrics"].items():
        out.append(metric_line(name, values.get(name, good_value(op, thr)),
                               ops.get(name, op), thrs.get(name, thr)))
    return out


class GateCase(unittest.TestCase):
    def setUp(self):
        self._tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self._tmp.cleanup)
        self.dir = Path(self._tmp.name)

    def run_gate(self, key, lines):
        path = self.dir / f"{key}.md"
        path.write_text("\n".join(["# spike", ""] + lines) + "\n", encoding="utf-8", newline="\n")
        return subprocess.run([sys.executable, str(GATE), str(path)],
                              capture_output=True, text=True).returncode

    def assertExit(self, expected, key, lines):
        code = self.run_gate(key, lines)
        self.assertEqual(code, expected, f"{key}: {lines}")

    def test_fail_only_exit1(self):
        self.assertExit(1, U01, ["RESULT: FAIL"])
        # exactly one RESULT line is required
        self.assertExit(1, U09, metric_lines(U09))
        self.assertExit(1, U09, ["RESULT: PASS", "RESULT: PASS"] + metric_lines(U09))

    def test_pass_without_metrics_exit1(self):
        self.assertExit(1, U01, ["RESULT: PASS"])

    def test_missing_required_metric_exit1(self):
        self.assertExit(1, U09, ["RESULT: PASS"] + metric_lines(U09)[:-1])

    def test_extra_metric_exit1(self):
        self.assertExit(1, U09, ["RESULT: PASS"] + metric_lines(U09) + ["METRIC bogus 1 >= 1"])

    def test_duplicate_metric_exit1(self):
        m = metric_lines(U09)
        self.assertExit(1, U09, ["RESULT: PASS"] + m + [m[0]])

    def test_altered_threshold_exit1(self):
        name = "p95_frame_ms_60hz"
        self.assertExit(1, U01, ["RESULT: PASS"] + metric_lines(U01, thrs={name: 99.0}))

    def test_altered_op_exit1(self):
        name = "p95_frame_ms_60hz"
        self.assertExit(1, U01, ["RESULT: PASS"] + metric_lines(U01, ops={name: "<"}))

    def test_pass_with_violated_metric_exit1(self):
        op, thr = TABLE[U01]["metrics"]["p95_frame_ms_60hz"]
        self.assertExit(1, U01, ["RESULT: PASS"] + metric_lines(
            U01, values={"p95_frame_ms_60hz": bad_value(op, thr)}))

    def test_unverified_or_unknown_result_exit1(self):
        for result in ("UNVERIFIED", "MAYBE", "PASS UNVERIFIED", "FALLBACK:"):
            with self.subTest(result=result):
                self.assertExit(1, U09, [f"RESULT: {result}"] + metric_lines(U09))

    def test_file_not_in_table_exit1(self):
        self.assertExit(1, "p4-not-in-table", ["RESULT: PASS", "METRIC x 1 >= 1"])

    def test_unknown_fallback_name_exit1(self):
        self.assertExit(1, U01, ["RESULT: FALLBACK:made-up-name"] + metric_lines(U01))
        # a fallback that exists only under another key is unknown here
        self.assertExit(1, U01, ["RESULT: FALLBACK:static-string-table"] + metric_lines(U01))

    def test_fallback_not_excusing_that_metric_exit1(self):
        op, thr = TABLE[U01]["metrics"]["p95_frame_ms_60hz"]
        # device-not-available excuses only p95_frame_ms_120hz, not the 60 Hz metric
        self.assertExit(1, U01, ["RESULT: FALLBACK:device-not-available"] + metric_lines(
            U01, values={"p95_frame_ms_60hz": bad_value(op, thr)}))

    def test_na_value_under_pass_exit1(self):
        self.assertExit(1, U01, ["RESULT: PASS"] + metric_lines(U01, values={"p95_frame_ms_120hz": "n/a"}))

    def test_paywall_vip_before_confirm_never_excused_exit1(self):
        bad = {"vip_before_server_confirm": 1}
        for result in ("PASS", "FAIL", "FALLBACK:device-not-available"):
            with self.subTest(result=result):
                self.assertExit(1, U122, [f"RESULT: {result}"] + metric_lines(U122, values=bad))

    def test_valid_pass_and_valid_fallback_exit0(self):
        self.assertExit(0, U01, ["RESULT: PASS"] + metric_lines(U01))
        # device-not-available may leave the 120 Hz metric n/a or violated
        op, thr = TABLE[U01]["metrics"]["p95_frame_ms_120hz"]
        for v in ("n/a", bad_value(op, thr)):
            with self.subTest(value=v):
                self.assertExit(0, U01, ["RESULT: FALLBACK:device-not-available"] + metric_lines(
                    U01, values={"p95_frame_ms_120hz": v}))
        # animation-ladder excuses the four frame/jank/hitch metrics
        four = ["p95_frame_ms_60hz", "p95_frame_ms_120hz", "jank_pct_30s", "hitch_ms_per_s"]
        vals = {n: bad_value(*TABLE[U01]["metrics"][n]) for n in four}
        self.assertExit(0, U01, ["RESULT: FALLBACK:animation-ladder"] + metric_lines(U01, values=vals))

    def test_every_spike_file_named_in_plan_is_in_table(self):
        named = set(re.findall(r"docs/superpowers/spikes/(p4-[A-Za-z0-9_-]+)\.md", PLAN.read_text(encoding="utf-8")))
        self.assertTrue(named)
        self.assertEqual(sorted(named - set(TABLE)), [])
        self.assertEqual(len([k for k in TABLE if k.startswith("p4-")]), 17)


if __name__ == "__main__":
    unittest.main()
