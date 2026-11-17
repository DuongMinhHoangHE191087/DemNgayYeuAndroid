"""Tests for ui_kmp_gate.py: python -m unittest discover -s scripts/ui -p "test_ui_kmp_gate.py"."""
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

GATE = Path(__file__).resolve().parent / "ui_kmp_gate.py"

# One line per group, containing exactly one forbidden pattern of that group.
SAMPLES = {
    "imports": "import android.os.Bundle",
    "coil": "import coil.compose.AsyncImage",
    "clipboard": "val c: ClipData? = null",
    "pickers": "val x = ActivityResultContracts.GetContent()",
    "share": "startActivity(x)",
    "activity": "val c = LocalContext.current",
    "dates": 'val s = SimpleDateFormat("yyyy")',
    "format": 'val s = String.format("%d", 1)',
    "resources": "val s = R.string.app_name",
    "locale": "val l = java.util.Locale.US",
    "blur": "Modifier.blur(4.dp)",
    "video": "AndroidView(factory = {})",
    "buildconfig": "val d = BuildConfig.DEBUG",
    "viewmodel": "class A : AndroidViewModel(app)",
}
CLEAN = "package x\n\nimport kotlinx.datetime.LocalDate\n\nfun f(): Int = 1\n"


def run(*args):
    p = subprocess.run([sys.executable, str(GATE), *map(str, args)], capture_output=True, text=True, encoding="utf-8")
    return p.returncode, p.stdout


class UiKmpGateTest(unittest.TestCase):
    def setUp(self):
        self._tmp = tempfile.TemporaryDirectory()
        self.root = Path(self._tmp.name)

    def tearDown(self):
        self._tmp.cleanup()

    def write(self, name, text, sub=""):
        d = self.root / sub
        d.mkdir(parents=True, exist_ok=True)
        (d / name).write_text(text, encoding="utf-8")
        return d / name

    def test_each_group_flags_sample(self):
        for group, line in SAMPLES.items():
            with self.subTest(group=group):
                f = self.write(f"{group}.kt", f"package x\n{line}\n")
                code, out = run("--group", group, "--paths", f)
                self.assertEqual(code, 1, out)
                self.assertIn(f"{f.as_posix()}:2:{group}", out)

    def test_activity_and_androidx_imports(self):
        f = self.write("a.kt", "import androidx.activity.compose.setContent\nimport androidx.core.net.toUri\n")
        code, out = run("--group", "imports", "--paths", f)
        self.assertEqual(code, 1)
        self.assertEqual(out.count(":imports"), 2, out)

    def test_group_isolation(self):
        # A blur sample must not trip an unrelated group, and a clean group list stays 0.
        f = self.write("b.kt", f"package x\n{SAMPLES['blur']}\n")
        self.assertEqual(run("--group", "coil,viewmodel", "--paths", f)[0], 0)
        code, out = run("--group", "coil,blur", "--paths", f)
        self.assertEqual(code, 1)
        self.assertIn(":blur", out)

    def test_comments_ignored(self):
        f = self.write("c.kt", "// import android.os.Bundle\n  * ClipData here\n/* x */\n")
        self.assertEqual(run("--group", "imports,clipboard", "--paths", f)[0], 0)

    def test_clean_file_passes(self):
        f = self.write("clean.kt", CLEAN)
        code, out = run("--group", ",".join(SAMPLES), "--paths", f)
        self.assertEqual((code, out), (0, ""))

    def test_unknown_group_and_missing_path_are_errors(self):
        f = self.write("clean.kt", CLEAN)
        self.assertEqual(run("--group", "nope", "--paths", f)[0], 2)
        self.assertEqual(run("--group", "imports", "--paths", self.root / "missing.kt")[0], 2)

    def test_exempt_and_paths_scope(self):
        bad = self.write("Bad.kt", "import android.os.Bundle\n", "ui")
        self.write("Other.kt", "import android.os.Bundle\n", "ui/sub")
        self.write("Good.kt", CLEAN, "ui")
        root = self.root / "ui"
        # whole root: both bad files flagged
        code, out = run("--group", "imports", "--root", root)
        self.assertEqual(code, 1)
        self.assertEqual(out.count(":imports"), 2, out)
        # --paths by basename resolves under root and limits the scan
        code, out = run("--group", "imports", "--root", root, "--paths", "Good.kt")
        self.assertEqual((code, out), (0, ""))
        code, out = run("--group", "imports", "--root", root, "--paths", "Other.kt")
        self.assertEqual(code, 1)
        self.assertNotIn("Bad.kt", out)
        # --exempt removes a file from the scan (by basename)
        code, out = run("--group", "imports", "--root", root, "--exempt", "Bad.kt")
        self.assertEqual(code, 1)
        self.assertNotIn("Bad.kt", out)
        self.assertIn("Other.kt", out)
        code, _ = run("--group", "imports", "--root", root, "--exempt", "Bad.kt", "Other.kt")
        self.assertEqual(code, 0)
        # exempt also works against an explicit --paths file
        self.assertEqual(run("--group", "imports", "--paths", bad, "--exempt", "Bad.kt")[0], 0)


if __name__ == "__main__":
    unittest.main()
