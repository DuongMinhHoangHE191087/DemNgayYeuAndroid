"""Tests for ui_decls.py: every case builds a sample Kotlin dir in a temp dir and drives the CLI."""
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parent / "ui_decls.py"

SRC = '''package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.util.Formatter as Fmt

/** Doc for Alpha { an unbalanced brace in KDoc */
@Composable
private fun Alpha(name: String) {
    val s = "}${name}{"
    // a } brace in a comment
    /* and { in a block comment */
    var open by remember { mutableStateOf(false) }
    if (open) { Text(s) }
    private val nested = 1
}

private const val LIMIT = 5

@Composable
fun Beta() {
    val raw = """
        } { not code
    """
    val ch = '}'
    Fmt.go()
    Box { }
}

internal class Gamma(val x: Int) {
    private fun hidden() = x
}
'''


def run(*args):
    return subprocess.run([sys.executable, str(SCRIPT), *map(str, args)], capture_output=True, text=True, encoding="utf-8")


class UiDeclsTest(unittest.TestCase):
    def setUp(self):
        self._tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self._tmp.cleanup)
        self.dir = Path(self._tmp.name) / "ui"
        self.dir.mkdir()
        self.snap = Path(self._tmp.name) / "snap.json"

    def put(self, name, text):
        p = self.dir / name
        p.write_text(text, encoding="utf-8", newline="\n")
        return p

    def snapshot(self):
        r = run("snapshot", self.dir, self.snap)
        self.assertEqual(r.returncode, 0, r.stderr)

    def test_snapshot_check_detects_body_change(self):
        src = self.put("Src.kt", SRC)
        self.snapshot()
        names = {(d["kind"], d["name"]) for d in json.loads(self.snap.read_text(encoding="utf-8"))["decls"]}
        self.assertEqual(names, {("fun", "Alpha"), ("val", "LIMIT"), ("fun", "Beta"), ("class", "Gamma")})
        self.assertEqual(run("check", self.dir, self.snap).returncode, 0)
        # private -> internal and re-indentation are NOT changes
        src.write_text(SRC.replace("private fun Alpha", "internal fun Alpha").replace("    Fmt.go()", "Fmt.go()"), encoding="utf-8", newline="\n")
        self.assertEqual(run("check", self.dir, self.snap).returncode, 0)
        # body change IS a change
        src.write_text(SRC.replace("Fmt.go()", "Fmt.stop()"), encoding="utf-8", newline="\n")
        self.assertEqual(run("check", self.dir, self.snap).returncode, 1)
        # lost declaration
        src.write_text(SRC.replace("private const val LIMIT = 5\n", ""), encoding="utf-8", newline="\n")
        self.assertEqual(run("check", self.dir, self.snap).returncode, 1)
        # added (duplicated) declaration
        src.write_text(SRC + "\nprivate const val LIMIT = 5\n", encoding="utf-8", newline="\n")
        self.assertEqual(run("check", self.dir, self.snap).returncode, 1)

    def test_move_preserves_decl_multiset(self):
        src = self.put("Src.kt", SRC)
        dest = self.dir / "Dest.kt"
        self.snapshot()
        r = run("move", src, dest, "Alpha", "Beta")
        self.assertEqual(r.returncode, 0, r.stderr)
        self.assertEqual(run("check", self.dir, self.snap).returncode, 0)
        s, d = src.read_text(encoding="utf-8"), dest.read_text(encoding="utf-8")
        self.assertIn("class Gamma", s)
        self.assertIn("LIMIT", s)
        self.assertNotIn("Alpha", s)
        self.assertNotIn("Beta", s)
        self.assertIn("fun Alpha", d)
        self.assertIn("fun Beta", d)
        self.assertTrue(d.startswith("package com.example.ui.screens"))
        self.assertIn('"}${name}{"', d)  # string/comment aware matching kept the whole body
        self.assertIn("Box { }\n}", d)
        # unknown symbol is an error
        self.assertNotEqual(run("move", src, dest, "Nope").returncode, 0)

    def test_move_copies_all_imports_and_relaxes_private(self):
        src = self.put("Src.kt", SRC)
        dest = self.put("Dest.kt", "package com.example.ui.screens\n\nimport androidx.compose.runtime.Composable\nimport com.example.Own\n\nprivate fun Existing() {}\n")
        self.assertEqual(run("move", src, dest, "Alpha").returncode, 0)
        d = dest.read_text(encoding="utf-8")
        s = src.read_text(encoding="utf-8")
        imports = [ln for ln in SRC.splitlines() if ln.startswith("import ")]
        dest_imports = [ln for ln in d.splitlines() if ln.startswith("import ")]
        for ln in imports:  # also star, alias and imports only the remaining code uses (Fmt in Beta)
            self.assertIn(ln, dest_imports)
        self.assertIn("import androidx.compose.foundation.layout.*", dest_imports)
        self.assertIn("import com.example.util.Formatter as Fmt", dest_imports)
        self.assertEqual(len(dest_imports), len(set(dest_imports)))
        self.assertEqual(dest_imports[:2], ["import androidx.compose.runtime.Composable", "import com.example.Own"])
        self.assertEqual([ln for ln in s.splitlines() if ln.startswith("import ")], imports)  # src keeps its imports
        # top-level private -> internal in both files; nested private untouched
        self.assertIn("internal fun Alpha", d)
        self.assertIn("internal fun Existing", d)
        self.assertNotIn("private fun Alpha", d)
        self.assertIn("    private val nested = 1", d)
        self.assertIn("internal const val LIMIT = 5", s)
        self.assertIn("    private fun hidden() = x", s)

    def test_move_delegated_state_keeps_getvalue_setvalue(self):
        src = self.put("Src.kt", SRC)
        dest = self.dir / "Dest.kt"
        self.assertEqual(run("move", src, dest, "Alpha").returncode, 0)
        d = dest.read_text(encoding="utf-8")
        body = d.split("fun Alpha", 1)[1]
        self.assertNotIn("getValue", body)  # body never spells the operators
        self.assertIn("import androidx.compose.runtime.getValue", d)
        self.assertIn("import androidx.compose.runtime.setValue", d)
        self.assertIn("var open by remember { mutableStateOf(false) }", d)

    def test_sizes_flags_long_file(self):
        self.put("Long.kt", "val a = 1\n" * 50)
        self.put("Short.kt", "val b = 1\n" * 5)
        self.assertEqual(run("sizes", self.dir, "--max", "10").returncode, 1)
        self.assertEqual(run("sizes", self.dir, "--max", "50").returncode, 0)
        self.assertEqual(run("sizes", self.dir, "--max", "10", "--allow", "Long.kt").returncode, 0)

    def test_sizes_only_limits_scope(self):
        self.put("Long.kt", "val a = 1\n" * 50)
        self.put("Short.kt", "val b = 1\n" * 5)
        self.assertEqual(run("sizes", self.dir, "--max", "10", "--only", "Short.kt").returncode, 0)
        self.assertEqual(run("sizes", self.dir, "--max", "10", "--only", "Short.kt,Long.kt").returncode, 1)
        self.assertEqual(run("sizes", self.dir, "--max", "10", "--only", "Long.kt").returncode, 1)


if __name__ == "__main__":
    unittest.main()
