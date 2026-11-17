"""Tests for ui_waves.py. Every test builds its OWN temp git repo; the real repo is only read (real waves.json)."""
import contextlib
import io
import json
import os
import re
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import ui_waves  # noqa: E402

CHECK = HERE.parent / "kmp" / "check-pure-move.sh"
APP, SHARED = ui_waves.APP, ui_waves.SHARED
TMPROOT = Path(tempfile.gettempdir()).resolve()

A_SRC = "package com.example.ui.util\n\nfun aFun() = 1\n"
B_SRC = "package com.example.ui.theme\n\nimport com.example.ui.util.aFun\n\nfun bFun() = aFun()\n"
A, B = "ui/util/A.kt", "ui/theme/B.kt"


def plain(pkg, name):
    return f"package com.example.ui.{pkg}\n\nfun {name}() = 1\n"


def blank(s):
    return re.sub(r"[^\r\n]", " ", s)


class Base(unittest.TestCase):
    def setUp(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.base = Path(tmp.name).resolve()
        self.root = self.base / "repo"
        self.root.mkdir()
        env = {k: v for k, v in os.environ.items() if k not in ("GIT_DIR", "GIT_INDEX_FILE", "GIT_WORK_TREE")}
        env["GIT_CEILING_DIRECTORIES"] = str(self.base)
        patch = mock.patch.dict(os.environ, env, clear=True)
        patch.start()
        self.addCleanup(patch.stop)
        self.git("init", "-q")
        for k, v in (("user.name", "t"), ("user.email", "t@example.com"), ("core.autocrlf", "false"), ("commit.gpgsign", "false")):
            self.git("config", k, v)

    def git(self, *args):
        self.assertIn(TMPROOT, self.root.parents, "refusing to run git outside the system temp dir")
        r = subprocess.run(["git", *args], cwd=self.root, capture_output=True, text=True, encoding="utf-8")
        self.assertEqual(r.returncode, 0, f"git {args}: {r.stderr}")
        return r.stdout

    def put(self, rel, text, side=APP, eol="\n"):
        p = self.root / side / rel
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_bytes(text.replace("\n", eol).encode("utf-8"))
        return p

    def set_waves(self, groups):
        data = {k: [APP + r for r in v] for k, v in groups.items()}
        p = self.root / "scripts" / "ui" / "waves.json"
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_bytes((json.dumps(data, indent=2) + "\n").encode("ascii"))
        return p

    def waves(self):
        return json.loads((self.root / "scripts" / "ui" / "waves.json").read_text(encoding="utf-8"))

    def cli(self, *argv):
        out, err = io.StringIO(), io.StringIO()
        with contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
            code = ui_waves.main(list(argv), root=self.root)
        return code, out.getvalue(), err.getvalue()

    def bash(self):
        b = ui_waves.find_bash()
        self.assertIsNotNone(b, "bash not found: install Git for Windows or set UI_WAVES_BASH")
        return b

    def base_tree(self, wave=0):
        gd = Path(self.git("rev-parse", "--absolute-git-dir").strip())
        return (gd / f"ui-wave-{wave}.base").read_text(encoding="ascii").strip()

    def pure_check(self, tree, rels):
        paths = [APP + r for r in rels] + [SHARED + r for r in rels]
        return subprocess.run([self.bash(), CHECK.as_posix(), "--worktree", tree, "--", *paths], cwd=self.root,
                              capture_output=True, text=True, encoding="utf-8")

    def move0(self, rels, tracked=True):
        for r in rels:
            self.put(r, plain("util", "f" + Path(r).stem))
        self.set_waves({"0": rels})
        if tracked:
            self.git("add", "-A")
        self.bash()
        code, out, err = self.cli("move", "--wave", "0")
        self.assertEqual(code, 0, out + err)


class CodeOnlyTest(unittest.TestCase):
    def test_code_only_masks_and_keeps_templates(self):
        src = "\n".join([
            'val a = "strword // yy" // commentword',
            "/* blockword /* nestedword */ stillblock */ val b = '\"' + '\\n'",
            'val c = """rawword "q" // zed""" + "t${internal} $name \\" endword"',
            "val d = 1",
        ]) + "\n"
        out = ui_waves.code_only(src)
        self.assertEqual(len(out), len(src))
        self.assertEqual([i for i, c in enumerate(out) if c == "\n"], [i for i, c in enumerate(src) if c == "\n"])
        for w in ("strword", "commentword", "blockword", "nestedword", "stillblock", "rawword", "endword", "zed"):
            self.assertNotIn(w, out)
        self.assertIn("val b", out)
        self.assertIn("val d = 1", out)
        self.assertIn("${internal}", out)
        self.assertIn("$name", out)
        self.assertEqual(out.count("internal"), 1)

    def test_code_only_masks_literals_but_keeps_the_code_after_them(self):
        src = "\n".join([
            "val a = 'x' + afterplain",
            "val b = '\"' + afterquote",
            "val c = '\\\"' + afterescaped",
            'val d = "x" + afterstring',
            'val e = "unterminated',
            "val f = visiblenext",
        ]) + "\n"
        want = "\n".join([
            "val a = " + blank("'x'") + " + afterplain",
            "val b = " + blank("'\"'") + " + afterquote",
            "val c = " + blank("'\\\"'") + " + afterescaped",
            "val d = " + blank('"x"') + " + afterstring",
            "val e = " + blank('"unterminated'),
            "val f = visiblenext",
        ]) + "\n"
        self.assertEqual(ui_waves.code_only(src), want)

    def test_code_only_raw_strings_and_nested_template_braces(self):
        src = 'val r = """a "keepword" b"""\nval t = "${ m { 1 } + visiblecode } tailword"\nval u = "${a} tail2"\nval z = zcode\n'
        want = "".join([
            "val r = " + blank('"""a "keepword" b"""') + "\n",
            "val t = " + blank('"') + "${ m { 1 } + visiblecode }" + blank(' tailword"') + "\n",
            "val u = " + blank('"') + "${a}" + blank(' tail2"') + "\n",
            "val z = zcode\n",
        ])
        self.assertEqual(ui_waves.code_only(src), want)

    def test_code_only_keeps_line_breaks_inside_masked_regions(self):
        for eol in ("\n", "\r\n"):
            src = eol.join(["val a = 1 /* one", "two */ val b = 2", 'val c = """r1', 'r2""" + 3', "val d = 4"]) + eol
            want = eol.join(["val a = 1 " + blank("/* one"), blank("two */") + " val b = 2", "val c = " + blank('"""r1'),
                             blank('r2"""') + " + 3", "val d = 4"]) + eol
            self.assertEqual(ui_waves.code_only(src), want, repr(eol))

    def test_code_only_survives_a_trailing_backslash_in_a_string(self):
        self.assertEqual(ui_waves.code_only('val s = "abc\\'), "val s = " + " " * 5)


class WavesTest(Base):
    def test_closure_lists_unmoved_dependency(self):
        self.put(A, A_SRC)
        self.put(B, B_SRC)
        self.set_waves({"1": [B]})
        self.assertEqual(self.cli("closure", "--wave", "1"), (0, APP + A + "\n", ""))

    def test_closure_apply_adds_to_wave(self):
        self.put(A, A_SRC)
        self.put(B, B_SRC)
        self.set_waves({"1": [B], "2": [A], "others": []})
        code, out, err = self.cli("closure", "--wave", "1", "--apply")
        self.assertEqual(code, 0, err)
        self.assertEqual(out, APP + A + "\n")
        w = self.waves()
        self.assertEqual(w["1"], [APP + B, APP + A])
        self.assertEqual(w["2"], [])
        self.assertEqual(list(w), ["1", "2", "others"])
        self.assertTrue((self.root / "scripts/ui/waves.json").read_bytes().endswith(b"\n"))

    def test_closure_apply_exits_2_on_stay_file(self):
        self.put(A, A_SRC)
        self.put(B, B_SRC)
        p = self.set_waves({"1": [B], "stay": [A]})
        before = p.read_bytes()
        for extra in ([], ["--apply"]):
            code, out, err = self.cli("closure", "--wave", "1", *extra)
            self.assertEqual(code, 2)
            self.assertEqual(out, APP + A + "\n")
            self.assertIn("stay: " + APP + A, err)
            self.assertEqual(p.read_bytes(), before)

    def test_closure_transitive_and_stay_through_chain(self):
        self.put("ui/x/X.kt", "package com.example.ui.x\n\nimport com.example.ui.theme.bFun\n\nfun xFun() = bFun()\n")
        self.put(B, B_SRC)
        self.put(A, A_SRC)
        self.set_waves({"1": ["ui/x/X.kt"]})
        self.assertEqual(self.cli("closure", "--wave", "1")[1].split(), sorted([APP + B, APP + A]))
        p = self.set_waves({"1": ["ui/x/X.kt"], "stay": [A]})
        before = p.read_bytes()
        code, out, err = self.cli("closure", "--wave", "1", "--apply")
        self.assertEqual(code, 2)
        self.assertEqual(set(out.split()), {APP + A, APP + B})
        self.assertIn("stay: " + APP + A, err)
        self.assertEqual(p.read_bytes(), before)

    def test_closure_ignores_earlier_waves_and_shared_only(self):
        for rel, pkg in (("ui/w/W.kt", "w"), ("ui/d/D.kt", "d"), ("ui/l/L.kt", "l")):
            self.put(rel, plain(pkg, pkg + "Fn"))
        self.put("ui/s/S.kt", plain("s", "sFn"), side=SHARED)
        self.put("ui/x/X.kt", "package com.example.ui.x\n\nimport com.example.ui.w.wFn\nimport com.example.ui.s.sFn\n"
                 "import com.example.ui.d.dFn as dd\nimport com.example.ui.l.lFn\n\nfun x() = 1\n")
        self.set_waves({"0": ["ui/w/W.kt"], "1": ["ui/x/X.kt"], "2": ["ui/l/L.kt"]})
        code, out, err = self.cli("closure", "--wave", "1")
        self.assertEqual((code, out.split()), (0, [APP + "ui/d/D.kt", APP + "ui/l/L.kt"]), err)

    def test_closure_wildcard_and_same_package(self):
        self.put("ui/util/A.kt", A_SRC)
        self.put("ui/util/U.kt", plain("util", "unusedFun"))
        self.put("ui/screens/Z.kt", plain("screens", "zName"))
        self.put("ui/screens/Q.kt", plain("screens", "qName"))
        self.put("ui/screens/X.kt", "package com.example.ui.screens\n\nimport android.os.Bundle\nimport com.example.data.Repo\n"
                 "import com.example.ui.util.*\n\n// unusedFun qName\nfun x() = aFun() + zName()\n")
        self.set_waves({"1": ["ui/screens/X.kt"]})
        code, out, err = self.cli("closure", "--wave", "1")
        self.assertEqual((code, out.split()), (0, [APP + "ui/screens/Z.kt", APP + "ui/util/A.kt"]), err)

    def test_closure_ignores_private_declaration(self):
        self.put("ui/util/H.kt", "package com.example.ui.util\n\nprivate fun hidden() = 1\n\nfun shown() = 2\n")
        head = "package com.example.ui.screens\n\nimport com.example.ui.util.*\nimport com.example.ui.util.hidden\n\n"
        self.put("ui/screens/X.kt", head + "fun x() = hidden()\n")
        self.set_waves({"1": ["ui/screens/X.kt"]})
        self.assertEqual(self.cli("closure", "--wave", "1"), (0, "", ""))
        self.put("ui/screens/X.kt", head + "fun x() = shown()\n")
        self.assertEqual(self.cli("closure", "--wave", "1")[1], APP + "ui/util/H.kt\n")

    def test_closure_empty_after_move(self):
        self.put("ui/x/X.kt", "package com.example.ui.x\n\nimport com.example.ui.theme.bFun\n\nfun xFun() = bFun()\n")
        self.put(B, B_SRC)
        self.put(A, A_SRC)
        self.set_waves({"1": ["ui/x/X.kt", B, A]})
        self.git("add", "-A")
        self.bash()
        code, out, err = self.cli("move", "--wave", "1")
        self.assertEqual(code, 0, out + err)
        self.assertEqual(self.cli("closure", "--wave", "1"), (0, "", ""))

    def test_check_flags_android_import(self):
        self.put(A, "package com.example.ui.util\n\nimport android.content.Context\n\nfun a(c: Context) = c\n")
        self.set_waves({"1": [A]})
        code, out, err = self.cli("check", "--wave", "1")
        self.assertEqual(code, 1)
        self.assertEqual(out, f"{APP}{A}:3: forbidden import android.content.Context\n")
        self.put(A, A_SRC)
        self.assertEqual(self.cli("check", "--wave", "1"), (0, "OK\n", ""))

    def test_check_forbidden_imports_and_buildconfig(self):
        imps = ["android.content.Context", "androidx.activity.compose.setContent", "androidx.core.content.ContextCompat",
                "coil.compose.AsyncImage", "java.util.UUID", "javax.inject.Inject", "com.example.R", "com.example.R.string",
                "com.example.BuildConfig"]
        ok = ["coil3.compose.AsyncImage", "androidx.compose.ui.Modifier", "kotlin.math.max", "com.example.Resources"]
        src = "package com.example.ui.util\n\n" + "".join(f"import {i}\n" for i in imps + ok) + "\nval v = BuildConfig.DEBUG // BuildConfig\n"
        self.put(A, src)
        self.set_waves({"1": [A]})
        code, out, err = self.cli("check", "--wave", "1")
        self.assertEqual(code, 1)
        lines = out.splitlines()
        self.assertEqual(len(lines), len(imps) + 1, out)
        for i in imps:
            self.assertIn(i, out)
        for i in ok:
            self.assertNotIn(i, out)
        self.assertTrue(lines[-1].endswith("BuildConfig reference"))

    def test_check_flags_internal_modifier(self):
        self.put(A, "package com.example.ui.util\n\ninternal fun a() = 1\n")
        self.set_waves({"1": [A]})
        self.assertEqual(self.cli("check", "--wave", "1"), (1, f"{APP}{A}:3: internal modifier\n", ""))
        self.put(A, 'package com.example.ui.util\n\n// internal\nval internalState = "internal"\n/* internal */\nval s = "${internalState}"\n')
        self.assertEqual(self.cli("check", "--wave", "1"), (0, "OK\n", ""))

    def test_check_flags_reference_outside_wave(self):
        self.put(A, A_SRC)
        self.put(B, B_SRC)
        self.set_waves({"1": [B], "others": [A]})
        code, out, err = self.cli("check", "--wave", "1")
        self.assertEqual(code, 1)
        self.assertIn(f"{APP}{B}:3:", out)
        self.assertIn(APP + A, out)
        self.set_waves({"0": [A], "1": [B]})
        self.assertEqual(self.cli("check", "--wave", "1")[0], 0)
        self.set_waves({"1": [B], "stay": [A]})
        self.assertEqual(self.cli("check", "--wave", "1")[0], 1)
        (self.root / APP / A).unlink()
        self.put(A, A_SRC, side=SHARED)
        self.set_waves({"1": [B]})
        self.assertEqual(self.cli("check", "--wave", "1"), (0, "OK\n", ""))

    def test_check_moved_reads_shared_path(self):
        self.put(A, "package com.example.ui.util\n\nimport android.os.Bundle\n\nfun a(b: Bundle) = b\n", side=SHARED)
        self.set_waves({"0": [A]})
        code, out, err = self.cli("check", "--wave", "0")
        self.assertEqual((code, out), (3, ""))
        self.assertIn("missing file: " + APP + A, err)
        code, out, err = self.cli("check", "--wave", "0", "--moved")
        self.assertEqual(code, 1)
        self.assertEqual(out, f"{SHARED}{A}:3: forbidden import android.os.Bundle\n")
        self.put(A, A_SRC, side=SHARED)
        self.assertEqual(self.cli("check", "--wave", "0", "--moved"), (0, "OK\n", ""))

    def test_check_all_partition_and_moved_rules(self):
        names = {"ui/a/A1.kt": "a1", "ui/a/B1.kt": "b1", "ui/a/C1.kt": "c1", "ui/a/D1.kt": "d1"}
        for rel, n in names.items():
            self.put(rel, plain("a", n))
        self.set_waves({"1": ["ui/a/A1.kt", "ui/a/B1.kt"], "stay": ["ui/a/B1.kt"], "others": ["ui/a/D1.kt"]})
        code, out, err = self.cli("check", "--all")
        self.assertEqual(code, 1)
        self.assertIn(f"{APP}ui/a/C1.kt:0: not in any group", out)
        self.assertIn(f"{APP}ui/a/B1.kt:0: in multiple groups", out)
        self.assertNotIn("A1.kt", out)
        self.assertNotIn("D1.kt", out)
        self.set_waves({"1": ["ui/a/A1.kt", "ui/a/B1.kt"], "stay": ["ui/a/C1.kt"], "others": ["ui/a/D1.kt"]})
        self.assertEqual(self.cli("check", "--all"), (0, "OK\n", ""))
        code, out, err = self.cli("check", "--all", "--moved")
        self.assertEqual(code, 1)
        self.assertIn(f"{SHARED}ui/a/A1.kt:0: not under shared", out)
        self.put("ui/a/A1.kt", plain("a", "a1"), side=SHARED)
        code, out, err = self.cli("check", "--all", "--moved")
        self.assertEqual(code, 1)
        self.assertIn(f"{APP}ui/a/A1.kt:0: still under app", out)

    def test_unknown_wave_and_missing_file_and_usage_errors(self):
        self.put(A, A_SRC)
        self.set_waves({"1": [A, "ui/util/Gone.kt"], "stay": ["ui/util/AlsoGone.kt"], "others": []})
        code, out, err = self.cli("closure", "--wave", "9")
        self.assertEqual(code, 3)
        self.assertIn("wave 9 not found in waves.json", err)
        code, out, err = self.cli("closure", "--wave", "1")
        self.assertEqual(code, 3)
        self.assertIn("missing file: " + APP + "ui/util/Gone.kt", err)
        for argv in (["closure"], ["bogus"], ["closure", "--wave", "-1"], ["closure", "--wave", "x"], ["check"],
                     ["check", "--all", "--wave", "1"], []):
            self.assertEqual(self.cli(*argv)[0], 3, argv)
        (self.root / "scripts/ui/waves.json").write_text('{"1": "nope"}\n', encoding="utf-8")
        self.assertEqual(self.cli("closure", "--wave", "1")[0], 3)

    def test_publicize_removes_internal(self):
        self.put(A, "package com.example.ui.util\n\ninternal fun a() = 1\n\n@Composable internal fun b() = 2\n")
        self.set_waves({"1": [A]})
        code, out, err = self.cli("publicize", "--wave", "1")
        self.assertEqual((code, out), (0, "publicized: " + APP + A + "\n"), err)
        self.assertEqual((self.root / APP / A).read_text(encoding="utf-8"),
                         "package com.example.ui.util\n\nfun a() = 1\n\n@Composable fun b() = 2\n")
        self.assertEqual(self.cli("publicize", "--wave", "1"), (0, "", ""))

    def test_publicize_keeps_comments_identifiers_and_crlf(self):
        src = '// internal note\nval s = "internal"\nval internalState = 1\ninternal val z = 2\n'
        p = self.put(A, src, eol="\r\n")
        self.set_waves({"1": [A]})
        self.assertEqual(self.cli("publicize", "--wave", "1")[0], 0)
        self.assertEqual(p.read_bytes(), src.replace("internal val z", "val z").replace("\n", "\r\n").encode())
        before = p.read_bytes()
        self.put("ui/util/Same.kt", "package com.example.ui.util\n\nval q = 1\n")
        self.set_waves({"1": [A, "ui/util/Same.kt"]})
        mtime = (self.root / APP / "ui/util/Same.kt").stat().st_mtime_ns
        self.cli("publicize", "--wave", "1")
        self.assertEqual((self.root / APP / "ui/util/Same.kt").stat().st_mtime_ns, mtime)
        self.assertEqual(p.read_bytes(), before)

    def test_snapshot_tree_leaves_real_index_untouched(self):
        self.put("a.txt", "one\n", side="")
        self.git("add", "-A")
        self.git("commit", "-qm", "init")
        self.put("a.txt", "two\n", side="")
        self.put("staged.txt", "s\n", side="")
        self.git("add", "staged.txt")
        self.put("loose.txt", "l\n", side="")

        def state():
            return (self.git("status", "--porcelain"), self.git("diff", "--cached", "--name-only"),
                    sorted(p.name for p in (self.root / ".git").iterdir()))
        before = state()
        snap = self.base / "snap.txt"
        code, out, err = self.cli("snapshot-tree", "--out", str(snap))
        self.assertEqual(code, 0, err)
        self.assertEqual(state(), before)
        raw = snap.read_bytes().decode("ascii")
        self.assertRegex(raw, r"^[0-9a-f]{40,64}\n$")
        self.assertEqual(self.git("cat-file", "-t", raw.strip()).strip(), "tree")
        names = self.git("ls-tree", "-r", "--name-only", raw.strip()).split()
        self.assertIn("loose.txt", names)
        self.assertIn("staged.txt", names)
        self.assertEqual(self.git("show", f"{raw.strip()}:a.txt"), "two\n")

    def test_snapshot_tree_excludes_ignored_files(self):
        self.put(".gitignore", "ignored.txt\n", side="")
        self.put("ignored.txt", "x\n", side="")
        self.put("kept.txt", "y\n", side="")
        self.assertEqual(self.cli("snapshot-tree", "--out", "t.txt")[0], 0)
        names = self.git("ls-tree", "-r", "--name-only", (self.root / "t.txt").read_text().strip()).split()
        self.assertIn("kept.txt", names)
        self.assertNotIn("ignored.txt", names)

    def test_deps_ignore_declarations_of_unknown_kind(self):
        self.put("ui/theme/X.kt", "package com.example.ui.theme\n\ncontext(Foo) fun withCtx() = 1\n")
        self.put("ui/theme/Y.kt", "package com.example.ui.theme\n\nfun yFun(context: Int) = context\n")
        self.set_waves({"1": ["ui/theme/Y.kt"]})
        self.assertEqual(self.cli("closure", "--wave", "1"), (0, "", ""))
        self.assertEqual(self.cli("check", "--wave", "1"), (0, "OK\n", ""))

    def test_check_reports_the_first_line_that_references_a_file(self):
        self.put("ui/p/P.kt", "package com.example.ui.p\n\nfun pOne() = 1\n\nfun pTwo() = 2\n")
        self.put("ui/p/R.kt", "package com.example.ui.p\n\nval a = pTwo()\n\nval b = pOne()\n\nval c = pTwo()\n")
        self.set_waves({"1": ["ui/p/R.kt"], "others": ["ui/p/P.kt"]})
        self.assertEqual(self.cli("check", "--wave", "1"),
                         (1, f"{APP}ui/p/R.kt:3: reference to {APP}ui/p/P.kt which is not in wave <= 1 or shared\n", ""))

    def test_deps_keep_the_first_import_line_of_a_file(self):
        self.put("ui/p/P.kt", "package com.example.ui.p\n\nfun pOne() = 1\n\nfun pTwo() = 2\n")
        self.put("ui/r/R.kt", "package com.example.ui.r\n\nimport com.example.ui.p.pOne\nimport com.example.ui.p.pTwo\n\n"
                 "fun r() = pOne() + pTwo()\n")
        self.set_waves({"1": ["ui/r/R.kt"], "others": ["ui/p/P.kt"]})
        self.assertEqual(self.cli("check", "--wave", "1"),
                         (1, f"{APP}ui/r/R.kt:3: reference to {APP}ui/p/P.kt which is not in wave <= 1 or shared\n", ""))

    def test_deps_resolve_an_import_by_its_longest_package(self):
        self.put("ui/theme/Palette.kt", "package com.example.ui.theme\n\nobject Palette { val primary = 1 }\n")
        self.put("ui/Shadow.kt", "package com.example.ui\n\nval theme = 1\n")
        self.put("ui/x/X.kt", "package com.example.ui.x\n\nimport com.example.ui.theme.Palette\n\nfun x() = Palette\n")
        self.put("ui/y/Y.kt", "package com.example.ui.y\n\nimport com.example.ui.theme.Palette.primary\n\nfun y() = primary\n")
        for rel in ("ui/x/X.kt", "ui/y/Y.kt"):
            self.set_waves({"1": [rel]})
            self.assertEqual(self.cli("closure", "--wave", "1"), (0, APP + "ui/theme/Palette.kt\n", ""), rel)

    def test_closure_terminates_on_a_dependency_cycle(self):
        self.put(A, "package com.example.ui.util\n\nimport com.example.ui.theme.bFun\n\nfun aFun() = bFun()\n")
        self.put(B, "package com.example.ui.theme\n\nimport com.example.ui.util.aFun\n\nfun bFun() = aFun()\n")
        self.put("ui/x/X.kt", "package com.example.ui.x\n\nimport com.example.ui.util.aFun\n\nfun xFun() = aFun()\n")
        self.set_waves({"1": ["ui/x/X.kt"]})
        calls, real = [], ui_waves.Ctx.deps

        def guarded(ctx, rel, moved=False):
            calls.append(rel)
            if len(calls) > 20:
                raise AssertionError("closure does not terminate on a dependency cycle")
            return real(ctx, rel, moved)
        with mock.patch.object(ui_waves.Ctx, "deps", guarded):
            result = self.cli("closure", "--wave", "1")
        self.assertEqual(result, (0, APP + B + "\n" + APP + A + "\n", ""))
        self.assertEqual(len(calls), 3)

    def test_closure_apply_without_additions_leaves_waves_json_alone(self):
        self.put(A, A_SRC)
        p = self.root / "scripts" / "ui" / "waves.json"
        p.parent.mkdir(parents=True)
        p.write_bytes(('{"1":["' + APP + A + '"]}\n').encode("ascii"))
        before = p.read_bytes()
        self.assertEqual(self.cli("closure", "--wave", "1", "--apply"), (0, "", ""))
        self.assertEqual(p.read_bytes(), before)

    def test_check_matches_whole_words_only(self):
        src = ("package com.example.ui.util\n\nimport com.example.BuildConfigurator\nimport com.example.ui.util.MyBuildConfig\n\n"
               "val myinternal = 1\nval internally = 2\nval MyBuildConfig = 3\nval BuildConfigs = 4\nval notBuildConfigX = 5\n")
        self.put(A, src)
        self.set_waves({"1": [A]})
        self.assertEqual(self.cli("check", "--wave", "1"), (0, "OK\n", ""))

    def test_check_lists_findings_in_line_order(self):
        self.put("ui/p/P.kt", "package com.example.ui.p\n\nfun pFun() = 1\n")
        self.put("ui/r/R.kt", "package com.example.ui.r\n\nimport com.example.ui.p.pFun\n\nfun r() = pFun()\n\n// filler\n"
                 "internal fun s() = 1\n")
        self.set_waves({"1": ["ui/r/R.kt"], "others": ["ui/p/P.kt"]})
        code, out, err = self.cli("check", "--wave", "1")
        self.assertEqual(code, 1, err)
        self.assertEqual(out.splitlines(), [
            f"{APP}ui/r/R.kt:3: reference to {APP}ui/p/P.kt which is not in wave <= 1 or shared",
            f"{APP}ui/r/R.kt:8: internal modifier",
        ])

    def test_check_all_prints_each_finding_once(self):
        self.put(A, "package com.example.ui.util\n\ninternal fun a() = 1\n")
        self.set_waves({"1": [A], "2": [A]})
        code, out, err = self.cli("check", "--all")
        self.assertEqual(code, 1, err)
        self.assertEqual(out.splitlines(), [f"{APP}{A}:3: internal modifier", f"{APP}{A}:0: in multiple groups: 1, 2"])

    def test_check_all_checks_every_numbered_wave(self):
        self.put(A, "package com.example.ui.util\n\nimport android.os.Bundle\n")
        self.put(B, "package com.example.ui.theme\n\nimport java.util.UUID\n")
        self.put("ui/z/Z.kt", plain("z", "zFn"))
        self.set_waves({"1": [A], "2": [B], "others": ["ui/z/Z.kt"]})
        code, out, err = self.cli("check", "--all")
        self.assertEqual((code, err), (1, ""))
        self.assertEqual(out.splitlines(), [f"{APP}{A}:3: forbidden import android.os.Bundle",
                                            f"{APP}{B}:3: forbidden import java.util.UUID"])

    def test_check_all_reports_a_shared_only_file_that_is_in_no_group(self):
        self.put(A, A_SRC)
        self.put("ui/s/S.kt", plain("s", "sFn"), side=SHARED)
        self.set_waves({"1": [A]})
        self.assertEqual(self.cli("check", "--all"), (1, f"{SHARED}ui/s/S.kt:0: not in any group\n", ""))

    def test_check_reads_the_app_copy_unless_moved(self):
        bad = "package com.example.ui.util\n\nimport android.os.Bundle\n\nfun a(b: Bundle) = b\n"
        self.set_waves({"1": [A]})
        self.put(A, bad)
        self.put(A, A_SRC, side=SHARED)
        self.assertEqual(self.cli("check", "--wave", "1"), (1, f"{APP}{A}:3: forbidden import android.os.Bundle\n", ""))
        self.assertEqual(self.cli("check", "--wave", "1", "--moved"), (0, "OK\n", ""))
        self.put(A, A_SRC)
        self.put(A, bad, side=SHARED)
        self.assertEqual(self.cli("check", "--wave", "1"), (0, "OK\n", ""))
        self.assertEqual(self.cli("check", "--wave", "1", "--moved"),
                         (1, f"{SHARED}{A}:3: forbidden import android.os.Bundle\n", ""))
        (self.root / SHARED / A).unlink()
        code, out, err = self.cli("check", "--wave", "1", "--moved")
        self.assertEqual((code, out), (3, ""))
        self.assertIn("missing file: " + SHARED + A, err)

    def test_waves_json_is_validated(self):
        self.put(A, A_SRC)
        self.put("data/X.kt", plain("data", "xFn"))
        self.put("ui/X.java", "package com.example.ui\n\nclass X\n")
        for groups, msg in (
            ({"1": [A], "x": []}, "bad waves.json: unexpected key 'x'"),
            ({"1": [A], "01": []}, "bad waves.json: unexpected key '01'"),
            ({"1": ["data/X.kt"]}, "must be a list of"),
            ({"1": ["ui/X.java"]}, "must be a list of"),
        ):
            self.set_waves(groups)
            code, out, err = self.cli("check", "--wave", "1")
            self.assertEqual((code, out), (3, ""), groups)
            self.assertIn(msg, err, groups)
        p = self.root / "scripts" / "ui" / "waves.json"
        for text, msg in (("[]\n", "top level must be an object"), ('{"1": "nope"}\n', "must be a list of"),
                          ('{"1": [3]}\n', "must be a list of"), ("{", "cannot read")):
            p.write_text(text, encoding="utf-8")
            code, out, err = self.cli("check", "--all")
            self.assertEqual((code, out), (3, ""), text)
            self.assertIn(msg, err, text)

    def test_wave_number_accepts_ascii_digits_only(self):
        self.put(A, A_SRC)
        self.set_waves({"3": [A]})
        self.assertEqual(self.cli("closure", "--wave", "3"), (0, "", ""))
        code, out, err = self.cli("closure", "--wave", chr(0xFF13))
        self.assertEqual((code, out), (3, ""))
        self.assertIn("usage error", err)

    def test_find_bash_honours_the_env_override(self):
        fake, missing = self.base / "fake-bash", self.base / "missing-bash"
        fake.write_text("", encoding="ascii")
        with mock.patch.dict(os.environ, {"UI_WAVES_BASH": str(fake)}):
            self.assertEqual(ui_waves.find_bash(), str(fake))
        with mock.patch.dict(os.environ, {"UI_WAVES_BASH": str(missing)}):
            self.assertNotEqual(ui_waves.find_bash(), str(missing))

    def test_os_errors_become_exit_3(self):
        d = self.base / "adir"
        d.mkdir()
        code, out, err = self.cli("snapshot-tree", "--out", str(d))
        self.assertEqual((code, out), (3, ""))
        self.assertTrue(err.startswith("error: "), err)


class MoveTest(Base):
    def test_move_writes_tree_id_base(self):
        self.move0([A])
        raw = (Path(self.git("rev-parse", "--absolute-git-dir").strip()) / "ui-wave-0.base").read_bytes().decode("ascii")
        self.assertRegex(raw, r"^[0-9a-f]{40,64}\n$")
        self.assertNotIn("\t", raw)
        self.assertEqual(self.git("cat-file", "-t", raw.strip()).strip(), "tree")
        self.assertTrue((self.root / SHARED / A).is_file())
        self.assertFalse((self.root / APP / A).exists())

    def test_move_is_pure_rename_checked_right_after(self):
        rels = [A, "ui/util/E.kt"]
        self.move0(rels)
        r = self.pure_check(self.base_tree(), rels)
        self.assertEqual(r.returncode, 0, r.stdout + r.stderr)
        lines = r.stdout.splitlines()
        self.assertEqual(sum(l.startswith("R100") for l in lines), 2, r.stdout)
        self.assertTrue(all(l.startswith(("R100", "pure move OK")) for l in lines), r.stdout)

    def test_pure_check_red_after_extra_edit_or_new_file(self):
        self.move0([A])
        tree = self.base_tree()
        dst = self.root / SHARED / A
        good = dst.read_bytes()
        dst.write_bytes(good + b"// extra\n")
        self.assertEqual(self.pure_check(tree, [A]).returncode, 1)
        dst.write_bytes(good)
        self.assertEqual(self.pure_check(tree, [A]).returncode, 0)
        self.put(A, "package com.example.ui.util\n\nfun fresh() = 2\n")
        self.assertEqual(self.pure_check(tree, [A]).returncode, 1)

    def test_move_passes_wave_pathspec(self):
        self.put("ui/util/E.kt", plain("util", "eFn"))
        self.set_waves({"0": [A], "1": ["ui/util/E.kt"]})
        self.put(A, A_SRC)
        self.git("add", "-A")
        self.bash()
        calls, real = [], subprocess.run

        def spy(cmd, *a, **k):
            if "--worktree" in cmd:
                calls.append(list(cmd))
            return real(cmd, *a, **k)
        with mock.patch.object(ui_waves.subprocess, "run", spy):
            code, out, err = self.cli("move", "--wave", "0")
        self.assertEqual(code, 0, out + err)
        self.assertEqual(len(calls), 1)
        cmd = calls[0]
        spec = cmd[cmd.index("--") + 1:]
        self.assertEqual(sorted(spec), sorted([APP + A, SHARED + A]))
        self.assertTrue(cmd[1].endswith("kmp/check-pure-move.sh"))
        (self.root / "scripts/ui/waves.json").write_text("{}\n", encoding="utf-8")
        self.put("unrelated.txt", "x\n", side="")
        r = self.pure_check(self.base_tree(), [A])
        self.assertEqual(r.returncode, 0, r.stdout + r.stderr)

    def test_move_handles_untracked_source_file(self):
        self.move0([A], tracked=False)
        r = self.pure_check(self.base_tree(), [A])
        self.assertEqual(r.returncode, 0, r.stdout + r.stderr)
        self.assertIn("R100", r.stdout)
        self.assertTrue((self.root / SHARED / A).is_file())

    def test_move_rolls_back_when_check_fails(self):
        self.put(A, A_SRC)
        self.put("ui/util/E.kt", plain("util", "eFn"))
        self.set_waves({"0": [A, "ui/util/E.kt"]})
        self.git("add", "--", APP + A)
        self.git("commit", "-qm", "init")
        self.bash()
        real = ui_waves.git_mv

        def fake(root, src, dst):
            real(root, src, dst)
            if dst.startswith(SHARED):
                p = Path(root) / dst
                p.write_bytes(p.read_bytes() + b"// edit\n")
        with mock.patch.object(ui_waves, "git_mv", fake):
            code, out, err = self.cli("move", "--wave", "0")
        self.assertEqual(code, 1, out + err)
        self.assertIn("NOT A PURE MOVE", out + err)
        for r in (A, "ui/util/E.kt"):
            self.assertTrue((self.root / APP / r).is_file(), r)
        self.assertFalse((self.root / "shared").exists())
        tracked = self.git("ls-files").split()
        self.assertIn(APP + A, tracked)
        self.assertNotIn(APP + "ui/util/E.kt", tracked)
        self.assertFalse([t for t in tracked if t.startswith("shared/")])

    def test_move_validates_before_changing_anything(self):
        self.put(A, A_SRC)
        self.put(A, A_SRC, side=SHARED)
        self.set_waves({"0": [A]})
        code, out, err = self.cli("move", "--wave", "0")
        self.assertEqual(code, 3)
        self.assertIn("destination exists", err)
        self.assertTrue((self.root / APP / A).is_file())
        (self.root / SHARED / A).unlink()
        (self.root / APP / A).unlink()
        self.assertEqual(self.cli("move", "--wave", "0")[0], 3)

    def test_wave0_precedes_wave1_and_holds_profile_utils(self):
        text = (HERE / "waves.json").read_text(encoding="utf-8")
        real = json.loads(text)
        self.assertEqual(list(real)[:2], ["0", "1"])
        want = [APP + "ui/util/ProfileUtils.kt", APP + "ui/util/TextFormat.kt"]
        self.assertEqual(sorted(real["0"]), want)
        self.assertFalse(set(want) & set(real["1"]))
        self.put("ui/util/ProfileUtils.kt", "package com.example.ui.util\n\nfun profileFn() = textFn()\n")
        self.put("ui/util/TextFormat.kt", "package com.example.ui.util\n\nfun textFn() = 1\n")
        p = self.root / "scripts/ui/waves.json"
        p.parent.mkdir(parents=True)
        p.write_bytes(text.encode("utf-8"))
        self.git("add", "-A")
        self.assertEqual(self.cli("closure", "--wave", "0"), (0, "", ""))
        self.bash()
        code, out, err = self.cli("move", "--wave", "0")
        self.assertEqual(code, 0, out + err)
        self.assertEqual(self.git("cat-file", "-t", self.base_tree(0)).strip(), "tree")
        self.assertTrue((self.root / SHARED / "ui/util/TextFormat.kt").is_file())

    def test_move_without_bash_exits_3_and_rolls_back(self):
        self.put(A, A_SRC)
        self.set_waves({"0": [A]})
        self.git("add", "-A")
        with mock.patch.object(ui_waves, "find_bash", lambda: None):
            code, out, err = self.cli("move", "--wave", "0")
        self.assertEqual(code, 3, out + err)
        self.assertIn("bash or scripts/kmp/check-pure-move.sh not found", err)
        self.assertTrue((self.root / APP / A).is_file())
        self.assertFalse((self.root / "shared").exists())

    def test_move_failing_while_moving_exits_1_and_rolls_back(self):
        self.put(A, A_SRC)
        self.put("ui/util/E.kt", plain("util", "eFn"))
        self.set_waves({"0": [A, "ui/util/E.kt"]})
        self.git("add", "--", APP + A)
        self.git("commit", "-qm", "init")
        real = ui_waves.git_mv

        def fake(root, src, dst):
            if dst == SHARED + "ui/util/E.kt":
                raise RuntimeError("boom")
            real(root, src, dst)
        with mock.patch.object(ui_waves, "git_mv", fake):
            code, out, err = self.cli("move", "--wave", "0")
        self.assertEqual(code, 1, out + err)
        self.assertIn("RuntimeError: boom", err)
        for r in (A, "ui/util/E.kt"):
            self.assertTrue((self.root / APP / r).is_file(), r)
        self.assertFalse((self.root / "shared").exists())
        tracked = self.git("ls-files").split()
        self.assertIn(APP + A, tracked)
        self.assertNotIn(APP + "ui/util/E.kt", tracked)

    def test_move_failing_while_checking_exits_3_and_rolls_back(self):
        self.put(A, A_SRC)
        self.set_waves({"0": [A]})
        self.git("add", "-A")
        self.bash()
        real = subprocess.run

        def fake(cmd, *a, **k):
            if "--worktree" in cmd:
                raise OSError("spawn failed")
            return real(cmd, *a, **k)
        with mock.patch.object(ui_waves.subprocess, "run", fake):
            code, out, err = self.cli("move", "--wave", "0")
        self.assertEqual(code, 3, out + err)
        self.assertIn("OSError: spawn failed", err)
        self.assertTrue((self.root / APP / A).is_file())
        self.assertFalse((self.root / "shared").exists())


if __name__ == "__main__":
    unittest.main()
