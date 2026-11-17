"""Wave tooling for moving app/ui files to shared/ (plan task U-74). Standard library only.

  closure --wave N [--apply]          ui files wave N needs that are not in a wave <= N yet (exit 2 when one is `stay`)
  check --wave N [--moved] | --all    forbidden imports, `internal`, references outside wave <= N / shared
  publicize --wave N                  drop the `internal` modifier in the wave files
  snapshot-tree --out FILE            write the id of a tree object of the whole work tree (real index untouched)
  move --wave N                       git mv the wave to shared/, then prove it is a pure move (rollback + exit 1 if not)

waves.json: {"0": [...], "1": [...], "stay": [...], "others": [...]} with repo-relative paths in app/ form.
Exit codes: 0 ok, 1 check violation or move failure, 2 stay conflict, 3 usage or data error.
ponytail: references are found by top-level names (imports, wildcard/same package by identifier token), not by full
Kotlin name resolution; a name declared in two files of one package counts as a reference to both.
"""
import argparse
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import ui_decls  # noqa: E402

APP = "app/src/main/java/com/example/"
SHARED = "shared/src/commonMain/kotlin/com/example/"
FORBIDDEN = ("android", "androidx.activity", "androidx.core", "coil", "java", "javax")
IDENT = re.compile(r"[A-Za-z_]\w*")
IMPORT = re.compile(r"^import\s+([\w.]+?)(\.\*)?(?:\s+as\s+\w+)?$")


class Fail(Exception):
    def __init__(self, code, msg):
        super().__init__(msg)
        self.code, self.msg = code, msg


class GitError(RuntimeError):
    pass


def git(root, *args, env=None):
    r = subprocess.run(["git", *args], cwd=root, env=env, capture_output=True, text=True, encoding="utf-8", errors="replace")
    if r.returncode:
        raise GitError(f"git {' '.join(args)} failed: {r.stderr.strip()}")
    return r.stdout


def code_only(text):
    """Blank comments, strings and char literals (newlines/offsets kept); string templates stay code."""
    out, n, i = list(text), len(text), 0
    stack, depth = ["C"], []

    def mask(a, b):
        for k in range(a, min(b, n)):
            if out[k] not in "\r\n":
                out[k] = " "
    while i < n:
        m, c = stack[-1], text[i]
        if m in "CT":
            if text.startswith("//", i):
                j = text.find("\n", i)
                j = n if j < 0 else j
                mask(i, j)
                i = j
            elif text.startswith("/*", i):
                stack.append("B")
                mask(i, i + 2)
                i += 2
            elif text.startswith('"""', i):
                stack.append("R")
                mask(i, i + 3)
                i += 3
            elif c == '"':
                stack.append("S")
                mask(i, i + 1)
                i += 1
            elif c == "'":
                if text[i + 1:i + 2] == "\\":
                    j = text.find("'", i + 3)
                else:
                    j = i + 2 if text[i + 2:i + 3] == "'" else -1
                if j < 0:
                    i += 1
                else:
                    mask(i, j + 1)
                    i = j + 1
            else:
                if m == "T":
                    if c == "{":
                        depth[-1] += 1
                    elif c == "}":
                        if depth[-1] == 0:
                            stack.pop()
                            depth.pop()
                        else:
                            depth[-1] -= 1
                i += 1
        elif m == "B":
            if text.startswith("*/", i):
                stack.pop()
                mask(i, i + 2)
                i += 2
            elif text.startswith("/*", i):
                stack.append("B")
                mask(i, i + 2)
                i += 2
            else:
                mask(i, i + 1)
                i += 1
        else:  # S or R
            if m == "S" and c == "\\":
                mask(i, i + 2)
                i += 2
            elif m == "S" and c == '"':
                stack.pop()
                mask(i, i + 1)
                i += 1
            elif m == "S" and c == "\n":
                stack.pop()
                i += 1
            elif m == "R" and text.startswith('"""', i):
                stack.pop()
                mask(i, i + 3)
                i += 3
            elif c == "$" and text.startswith("{", i + 1):
                stack.append("T")
                depth.append(0)
                i += 2
            elif c == "$" and IDENT.match(text, i + 1):
                i = IDENT.match(text, i + 1).end()
            else:
                mask(i, i + 1)
                i += 1
    return "".join(out)


def _clean(line):
    return re.sub(r"\s*//.*$", "", line).strip().rstrip(";").strip()


class Info:
    def __init__(self, path):
        self.text = ui_decls.read_text(path)[0]
        lines, items, imps, pkg = ui_decls.parse(self.text)
        self.package = _clean(lines[pkg])[len("package"):].strip() if pkg is not None else ""
        self.imports = []  # (line number, path, wildcard)
        for k in imps:
            m = IMPORT.match(_clean(lines[k]))
            if m:
                self.imports.append((k + 1, m.group(1), bool(m.group(2))))
        self.names = {it.name for it in items if it.kind != "unknown" and it.name
                      and not ui_decls.PRIVATE.match(lines[it.header])}
        code = code_only(self.text).split("\n")
        for k in imps + ([pkg] if pkg is not None else []):
            code[k] = ""
        self.code_lines = code
        self.first = {}  # identifier -> first line number
        for no, ln in enumerate(code, 1):
            for m in IDENT.finditer(ln):
                self.first.setdefault(m.group(), no)


class Ctx:
    def __init__(self, root, data):
        self.root, self.data, self.cache, self._index = Path(root), data, {}, None
        self.universe = set()
        for side in (APP, SHARED):
            base = self.root / side
            if (base / "ui").is_dir():
                self.universe |= {p.relative_to(base).as_posix() for p in (base / "ui").rglob("*.kt")}

    def has_app(self, rel):
        return (self.root / APP / rel).is_file()

    def path(self, rel, moved=False):
        for side in ((SHARED,) if moved else (APP, SHARED)):
            if (self.root / side / rel).is_file():
                return self.root / side / rel
        return None

    def shown(self, rel, moved=False):
        return (SHARED if moved or not self.has_app(rel) else APP) + rel

    def info(self, rel, moved=False):
        p = self.path(rel, moved)
        if p not in self.cache:
            self.cache[p] = Info(p)
        return self.cache[p]

    def numbered(self):
        return sorted(int(k) for k in self.data if k.isdigit())

    def wave(self, n):
        if str(n) not in self.data:
            raise Fail(3, f"wave {n} not found in waves.json")
        return [e[len(APP):] for e in self.data[str(n)]]

    def upto(self, n):
        return {e[len(APP):] for k in self.numbered() if k <= n for e in self.data[str(k)]}

    def index(self):
        if self._index is None:
            by_pkg, declared = {}, {}
            for rel in sorted(self.universe):
                f = self.info(rel)
                by_pkg.setdefault(f.package, {})[rel] = f.names
                for nm in f.names:
                    declared.setdefault((f.package, nm), set()).add(rel)
            self._index = by_pkg, declared
        return self._index

    def deps(self, rel, moved=False):
        """ui files directly referenced by rel: {file rel: first line}."""
        f, (by_pkg, declared) = self.info(rel, moved), self.index()
        out, pkgs = {}, {f.package}

        def add(g, line):
            if g != rel:
                out[g] = min(line, out.get(g, line))
        for line, path, wild in f.imports:
            if not path.startswith("com.example."):
                continue
            if wild:
                pkgs.add(path)
                continue
            parts = path.split(".")
            for k in range(len(parts) - 1, 0, -1):
                hit = declared.get((".".join(parts[:k]), parts[k]))
                if hit:
                    for g in hit:
                        add(g, line)
                    break
        for pkg in pkgs:
            for g, names in by_pkg.get(pkg, {}).items():
                lines = [f.first[nm] for nm in names if nm in f.first]
                if lines:
                    add(g, min(lines))
        return out

    def need(self, rel, moved=False):
        p = self.path(rel, moved)
        if p is None:
            raise Fail(3, "missing file: " + (SHARED if moved else APP) + rel)
        return p


def load(root):
    p = Path(root) / "scripts" / "ui" / "waves.json"
    try:
        data = json.loads(p.read_text(encoding="utf-8"))
    except (OSError, ValueError) as e:
        raise Fail(3, f"cannot read {p}: {e}")
    if not isinstance(data, dict):
        raise Fail(3, "bad waves.json: top level must be an object")
    for k, v in data.items():
        if not (k in ("stay", "others") or (k.isascii() and k.isdigit() and str(int(k)) == k)):
            raise Fail(3, f"bad waves.json: unexpected key {k!r}")
        if not isinstance(v, list) or not all(isinstance(e, str) and e.startswith(APP + "ui/") and e.endswith(".kt") for e in v):
            raise Fail(3, f"bad waves.json: {k!r} must be a list of {APP}ui/... .kt paths")
    return data


def save(root, data):
    (Path(root) / "scripts" / "ui" / "waves.json").write_bytes((json.dumps(data, indent=2) + "\n").encode("ascii"))


def cmd_closure(a, root):
    data = load(root)
    ctx = Ctx(root, data)
    queue, done, result = ctx.wave(a.wave), ctx.upto(a.wave), set()
    for rel in queue:
        ctx.need(rel)
    while queue:
        for g in ctx.deps(queue.pop()):
            if g not in done and g not in result and ctx.has_app(g):
                result.add(g)
                queue.append(g)
    paths = [APP + r for r in sorted(result)]
    for p in paths:
        print(p)
    stay = set(data.get("stay", []))
    bad = [p for p in paths if p in stay]
    for p in bad:
        print("stay: " + p, file=sys.stderr)
    if bad:
        return 2
    if a.apply and paths:
        for k in data:
            data[k] = [e for e in data[k] if e not in paths]
        data[str(a.wave)] += paths
        save(root, data)
    return 0


def check_file(ctx, rel, n, moved, allowed):
    f, found = ctx.info(rel, moved), []
    for line, path, _ in f.imports:
        if any(path == p or path.startswith(p + ".") for p in FORBIDDEN) or path == "com.example.R" \
                or path.startswith("com.example.R."):
            found.append((line, f"forbidden import {path}"))
        elif "BuildConfig" in path.split("."):
            found.append((line, f"forbidden import {path}"))
    for no, ln in enumerate(f.code_lines, 1):
        if re.search(r"\binternal\b", ln):
            found.append((no, "internal modifier"))
        if re.search(r"\bBuildConfig\b", ln):
            found.append((no, "BuildConfig reference"))
    for g, line in ctx.deps(rel, moved).items():
        if g not in allowed and ctx.has_app(g):
            found.append((line, f"reference to {APP + g} which is not in wave <= {n} or shared"))
    return [f"{ctx.shown(rel, moved)}:{line}: {msg}" for line, msg in sorted(found)]


def cmd_check(a, root):
    data = load(root)
    ctx = Ctx(root, data)
    waves = ctx.numbered() if a.all else [a.wave]
    out = []
    for n in waves:
        allowed = ctx.upto(n)
        for rel in ctx.wave(n):
            if a.all and a.moved:
                if not (root / SHARED / rel).is_file():
                    out.append(f"{SHARED + rel}:0: not under shared")
                if ctx.has_app(rel):
                    out.append(f"{APP + rel}:0: still under app")
                if not (root / SHARED / rel).is_file():
                    continue
            if not a.moved and not ctx.has_app(rel):
                raise Fail(3, "missing file: " + APP + rel)
            ctx.need(rel, a.moved)
            out +=check_file(ctx, rel, n, a.moved, allowed)
    if a.all:
        groups = {}
        for k, v in data.items():
            for e in v:
                groups.setdefault(e[len(APP):], []).append(k)
        for rel in sorted(ctx.universe):
            g = groups.get(rel, [])
            if not g:
                out.append(f"{ctx.shown(rel, a.moved)}:0: not in any group")
            elif len(g) > 1:
                out.append(f"{ctx.shown(rel, a.moved)}:0: in multiple groups: {', '.join(g)}")
    out = list(dict.fromkeys(out))
    print("\n".join(out) if out else "OK")
    return 1 if out else 0


def cmd_publicize(a, root):
    ctx = Ctx(root, load(root))
    for rel in ctx.wave(a.wave):
        p = ctx.need(rel)
        text, eol = ui_decls.read_text(p)
        spans = [m.span() for m in re.finditer(r"\binternal\b", code_only(text))]
        for s, e in reversed(spans):
            while e < len(text) and text[e] in " \t":
                e += 1
            text = text[:s] + text[e:]
        if spans:
            ui_decls.write_text(p, text, eol)
            print("publicized: " + APP + rel)
    return 0


def snapshot_tree(root):
    gd = Path(git(root, "rev-parse", "--absolute-git-dir").strip())
    fd, tmp = tempfile.mkstemp(prefix="ui-snap-", suffix=".idx", dir=gd)
    os.close(fd)
    os.unlink(tmp)  # git must create the index itself
    env = dict(os.environ, GIT_INDEX_FILE=tmp)
    try:
        git(root, "add", "-A", env=env)
        return git(root, "write-tree", env=env).strip()
    finally:
        for p in (tmp, tmp + ".lock"):
            Path(p).unlink(missing_ok=True)


def cmd_snapshot(a, root):
    out = Path(a.out) if Path(a.out).is_absolute() else Path(root) / a.out
    tree = snapshot_tree(root)
    out.write_text(tree + "\n", encoding="ascii", newline="\n")
    return 0


def git_mv(root, src, dst):
    (Path(root) / dst).parent.mkdir(parents=True, exist_ok=True)
    git(root, "mv", "--", src, dst)


def find_bash():
    env = os.environ.get("UI_WAVES_BASH")
    if env and Path(env).is_file():
        return env
    if os.name == "nt":
        g = shutil.which("git")
        if g:
            gp = Path(g).resolve().parent
            for d in (gp, gp.parent / "bin", gp.parent.parent / "bin", gp.parent.parent.parent / "bin"):
                if (d / "bash.exe").is_file():
                    return str(d / "bash.exe")
        w = shutil.which("bash")
        if w and "system32" not in w.lower():
            return w
        fallback = Path(r"C:\Program Files\Git\bin\bash.exe")
        return str(fallback) if fallback.is_file() else None
    return shutil.which("bash")


def rollback(root, entries, created):
    for e in reversed(entries):
        if e["moved"]:
            try:
                git_mv(root, e["dst"], e["src"])
            except Exception:
                pass
    for e in entries:
        if e["untracked"]:
            try:
                git(root, "rm", "--cached", "-q", "-f", "--", e["src"])
            except GitError:
                pass
    for d in sorted(created, key=lambda p: len(p.parts), reverse=True):
        try:
            d.rmdir()
        except OSError:
            pass


def cmd_move(a, root):
    root = Path(root)
    ctx = Ctx(root, load(root))
    rels = ctx.wave(a.wave)
    pairs = [(APP + r, SHARED + r) for r in rels]
    for s, d in pairs:
        if not (root / s).is_file():
            raise Fail(3, "missing file: " + s)
        if (root / d).exists():
            raise Fail(3, "destination exists: " + d)
    tree = snapshot_tree(root)
    gd = Path(git(root, "rev-parse", "--absolute-git-dir").strip())
    (gd / f"ui-wave-{a.wave}.base").write_text(tree + "\n", encoding="ascii", newline="\n")
    entries, created, stage, code, err = [], [], "mv", 1, ""
    try:
        for s, d in pairs:
            e = {"src": s, "dst": d, "untracked": not git(root, "ls-files", "--", s).strip(), "moved": False}
            entries.append(e)
            p = (root / d).parent
            while not p.exists():
                created.append(p)
                p = p.parent
            git(root, "add", "--", s)
            git_mv(root, s, d)
            e["moved"] = True
        stage = "check"
        script = Path(__file__).resolve().parents[1] / "kmp" / "check-pure-move.sh"
        bash = find_bash()
        if bash is None or not script.is_file():
            code, err = 3, "bash or scripts/kmp/check-pure-move.sh not found"
        else:
            r = subprocess.run([bash, script.as_posix(), "--worktree", tree, "--", *[p for pr in pairs for p in pr]],
                               cwd=root, capture_output=True, text=True, encoding="utf-8", errors="replace")
            if r.returncode == 0:
                print(f"moved {len(pairs)} file(s) of wave {a.wave}")
                print(r.stdout.rstrip("\n"))
                return 0
            code, err = 1, f"pure-move check failed (exit {r.returncode}), rolled back\n{r.stdout}{r.stderr}"
    except (Exception, KeyboardInterrupt) as ex:
        code, err = (3 if stage == "check" else 1), f"{type(ex).__name__}: {ex}"
    rollback(root, entries, created)
    print(err.rstrip("\n"), file=sys.stderr)
    return code


class _Parser(argparse.ArgumentParser):
    def error(self, message):
        raise Fail(3, f"usage error: {message}")


def _wave(s):
    if not (s.isascii() and s.isdigit()):
        raise argparse.ArgumentTypeError(f"--wave must be an integer >= 0, got {s!r}")
    return int(s)


def build_parser():
    ap = _Parser(description=__doc__.split("\n")[0])
    sub = ap.add_subparsers(dest="cmd", required=True)
    c = sub.add_parser("closure")
    c.add_argument("--wave", type=_wave, required=True)
    c.add_argument("--apply", action="store_true")
    c.set_defaults(fn=cmd_closure)
    k = sub.add_parser("check")
    g = k.add_mutually_exclusive_group(required=True)
    g.add_argument("--wave", type=_wave)
    g.add_argument("--all", action="store_true")
    k.add_argument("--moved", action="store_true")
    k.set_defaults(fn=cmd_check)
    for name, fn in (("publicize", cmd_publicize), ("move", cmd_move)):
        p = sub.add_parser(name)
        p.add_argument("--wave", type=_wave, required=True)
        p.set_defaults(fn=fn)
    s = sub.add_parser("snapshot-tree")
    s.add_argument("--out", required=True)
    s.set_defaults(fn=cmd_snapshot)
    return ap


def main(argv=None, root=None):
    try:
        a = build_parser().parse_args(argv)
        root = Path(root) if root else Path(git(None, "rev-parse", "--show-toplevel").strip())
        return a.fn(a, root)
    except Fail as e:
        print(e.msg, file=sys.stderr)
        return e.code
    except (GitError, OSError) as e:
        print(f"error: {e}", file=sys.stderr)
        return 3
    except SystemExit as e:
        return e.code if isinstance(e.code, int) else 0


if __name__ == "__main__":
    sys.exit(main())
