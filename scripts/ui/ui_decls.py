"""Mechanical Kotlin top-level declaration tooling for the UI split (plan task U-13).

  snapshot <dir> <out.json>          record every top-level declaration (KDoc + annotations included) with a content hash
  check <dir> <snap.json>            exit 1 when the declaration multiset changed (lost, added or body changed)
  move <src.kt> <dest.kt> <Sym>...   cut declarations verbatim from src, append to dest, copy ALL src imports, private -> internal
  sizes <dir> --max N [--allow a.kt,b.kt] [--only a.kt,b.kt]   exit 1 when a file has more than N lines

Standard library only. The scanner is string/comment aware (nested block comments, raw strings, ${} templates, char
literals). ponytail: a top-level declaration is assumed to start at column 0; a continuation line at column 0 that is
not `}`/`)`/`]`/`.`/else/`?:`/`&&`/`||` would be read as a new declaration.
"""
import argparse
import hashlib
import json
import re
import sys
from collections import Counter
from pathlib import Path

MODS = {
    "public", "private", "internal", "protected", "inline", "noinline", "crossinline", "suspend", "data", "sealed",
    "open", "abstract", "final", "enum", "annotation", "value", "inner", "override", "operator", "infix", "tailrec",
    "external", "expect", "actual", "lateinit", "const", "vararg",
}
KINDS = {"fun", "class", "object", "interface", "typealias", "val", "var"}
ANN_ONLY = re.compile(r"^(?:@[\w.:]+(?:\([^()]*(?:\([^()]*\)[^()]*)*\))?\s*)*(?:@[\w.:]+\(.*)?$")
_LEAD = r"^((?:@[\w.:]+(?:\([^)]*\))?\s+)*(?:(?!private\b|internal\b)[a-z]+\s+)*?)"
VIS = re.compile(_LEAD + r"(?:private|internal)\b\s*")
PRIVATE = re.compile(_LEAD + r"private\b\s*")


def read_text(path):
    data = Path(path).read_bytes().decode("utf-8-sig")
    return data.replace("\r\n", "\n"), ("\r\n" if "\r\n" in data else "\n")


def write_text(path, text, eol):
    Path(path).write_bytes(text.replace("\n", eol).encode("utf-8"))


def clean_starts(lines):
    """For each line: True when it starts outside any string/comment/bracket (a top-level position)."""
    stack, tdepth, depth, out = ["C"], [], 0, []
    for line in lines:
        out.append(len(stack) == 1 and depth == 0)
        i, n = 0, len(line)
        while i < n:
            mode, c = stack[-1], line[i]
            if mode in "CT":
                if line.startswith("//", i):
                    break
                if line.startswith("/*", i):
                    stack.append("B")
                    i += 2
                    continue
                if line.startswith('"""', i):
                    stack.append("R")
                    i += 3
                    continue
                if c == '"':
                    stack.append("S")
                elif c == "'":
                    if line[i + 1:i + 2] == "\\":
                        j = line.find("'", i + 3)
                        i = j if j > 0 else n
                    else:
                        i += 2
                elif c in "([{":
                    if mode == "C":
                        depth += 1
                    else:
                        tdepth[-1] += 1
                elif c in ")]}":
                    if mode == "T" and c == "}" and tdepth[-1] == 0:
                        stack.pop()
                        tdepth.pop()
                    elif mode == "C":
                        depth -= 1
                    else:
                        tdepth[-1] -= 1
            elif mode == "B":
                if line.startswith("*/", i):
                    stack.pop()
                    i += 2
                    continue
                if line.startswith("/*", i):
                    stack.append("B")
                    i += 2
                    continue
            else:  # S (string) or R (raw string)
                if mode == "S" and c == "\\":
                    i += 2
                    continue
                if mode == "R" and line.startswith('"""', i):
                    stack.pop()
                    i += 3
                    continue
                if mode == "S" and c == '"':
                    stack.pop()
                elif line.startswith("${", i):
                    stack.append("T")
                    tdepth.append(0)
                    i += 2
                    continue
            i += 1
        if stack[-1] == "S":  # plain strings never span lines
            stack.pop()
    return out


def skip_balanced(t, open_c, close_c):
    d = 0
    for j, ch in enumerate(t):
        d += ch == open_c
        d -= ch == close_c
        if d == 0:
            return t[j + 1:]
    return ""


def describe(header):
    t = header.lstrip()
    while t.startswith("@"):
        t = t[re.match(r"@(?:\w+:)?[\w.]+", t).end():]
        if t.startswith("("):
            t = skip_balanced(t, "(", ")")
        t = t.lstrip()
    while True:
        m = re.match(r"(\w+)\s*", t)
        if m and m.group(1) in MODS:
            t = t[m.end():]
        else:
            break
    m = re.match(r"(\w+)\s*", t)
    if not m:
        return "unknown", t.strip()
    kind, rest = m.group(1), t[m.end():]
    if kind == "fun" and rest.startswith("interface "):
        kind, rest = "interface", rest[len("interface "):]
    if kind not in KINDS:
        return "unknown", kind
    if kind in ("fun", "val", "var"):
        if rest.startswith("<"):
            rest = skip_balanced(rest, "<", ">").lstrip()
        head = re.split(r"\(|:|=| by ", rest)[0] if kind != "fun" else rest.split("(")[0]
        return kind, head.rsplit(".", 1)[-1].strip().strip("`")
    nm = re.match(r"`?(\w+)", rest)
    return kind, nm.group(1) if nm else ""


class Item:
    def __init__(self, lines, start, header, end):
        self.start, self.header, self.end = start, header, end
        self.lines = lines[start:end]
        self.kind, self.name = describe(lines[header])

    def digest(self):
        out = []
        for k, ln in enumerate(self.lines):
            s = ln.strip()
            if k == self.header - self.start:
                s = VIS.sub(r"\1", s)
            out.append(s)
        return hashlib.sha256("\n".join(out).encode("utf-8")).hexdigest()


def parse(text):
    lines = text.split("\n")
    states = clean_starts(lines)
    bounds, raw, imports, package, prefix = [], [], [], None, None
    for i, ln in enumerate(lines):
        if not states[i] or not ln or ln[0] in " \t":
            continue
        if ln.startswith(("package ", "import ")):
            bounds.append(i)
            prefix = None
            if ln.startswith("import "):
                imports.append(i)
            else:
                package = i
            continue
        if ln[0] in "})]." or ln.startswith(("else", "?:", "&&", "||")):
            continue
        if ln.startswith(("//", "/*")) or (ln[0] == "@" and ANN_ONLY.match(ln)):
            if prefix is None:
                prefix = i
                bounds.append(i)
            continue
        if prefix is None:
            bounds.append(i)
        raw.append((i if prefix is None else prefix, i))
        prefix = None
    items = []
    for start, header in raw:
        end = next((b for b in bounds if b > start), len(lines))
        while end > header + 1 and not lines[end - 1].strip():
            end -= 1
        items.append(Item(lines, start, header, end))
    return lines, items, imports, package


def decls_in(directory):
    root = Path(directory)
    out = []
    for p in sorted(root.rglob("*.kt")):
        _, items, _, _ = parse(read_text(p)[0])
        out += [{"file": p.relative_to(root).as_posix(), "kind": i.kind, "name": i.name, "hash": i.digest()} for i in items]
    return out


def key(d):
    return (d["kind"], d["name"], d["hash"])


def cmd_snapshot(a):
    Path(a.out).write_text(json.dumps({"decls": decls_in(a.dir)}, indent=1), encoding="utf-8")
    return 0


def cmd_check(a):
    snap = Counter(key(d) for d in json.loads(Path(a.snap).read_text(encoding="utf-8"))["decls"])
    cur = Counter(key(d) for d in decls_in(a.dir))
    lost, added = snap - cur, cur - snap
    for k, n in lost.items():
        print(f"MISSING/CHANGED x{n}: {k[0]} {k[1]} [{k[2][:10]}]")
    for k, n in added.items():
        print(f"ADDED/CHANGED x{n}: {k[0]} {k[1]} [{k[2][:10]}]")
    return 1 if lost or added else 0


def relax(text):
    lines, items, _, _ = parse(text)
    for it in items:
        lines[it.header] = PRIVATE.sub(r"\1internal ", lines[it.header])
    return "\n".join(lines)


def cmd_move(a):
    if Path(a.src).resolve() == Path(a.dest).resolve():
        print("src and dest are the same file", file=sys.stderr)
        return 2
    text, src_eol = read_text(a.src)
    lines, items, imports, package = parse(text)
    wanted = [it for it in items if it.name in a.symbols]
    absent = set(a.symbols) - {it.name for it in wanted}
    if absent:
        print("symbols not found in src: " + ", ".join(sorted(absent)), file=sys.stderr)
        return 2
    chunks = ["\n".join(it.lines) for it in wanted]
    # cut (with the blank lines that follow) and keep one separator at each seam
    kept, i = [], 0
    for it in sorted(wanted, key=lambda x: x.start):
        kept += lines[i:it.start]
        i = it.end
        while i < len(lines) and not lines[i].strip():
            i += 1
        if kept and kept[-1].strip() and i < len(lines):
            kept.append("")
    kept += lines[i:]
    write_text(a.src, relax("\n".join(kept).rstrip("\n") + "\n"), src_eol)
    # dest: merge ALL src imports (no filtering by use), keep dest order first
    src_imports = [lines[k].rstrip() for k in imports]
    if Path(a.dest).exists():
        dtext, d_eol = read_text(a.dest)
    else:
        dtext, d_eol = (lines[package].rstrip() + "\n" if package is not None else ""), src_eol
    dlines, _, d_imp, d_pkg = parse(dtext)
    merged = [dlines[k].rstrip() for k in d_imp]
    merged += [s for s in src_imports if s not in merged]
    if d_imp:
        drop = {k for k in range(d_imp[0], d_imp[-1] + 1) if k in d_imp or not dlines[k].strip()}
        pos = d_imp[0]
        rest = [ln for k, ln in enumerate(dlines) if k not in drop]
        dlines = rest[:pos] + merged + rest[pos:]
    elif merged:
        pos = d_pkg + 1 if d_pkg is not None else 0
        tail = [""] if pos < len(dlines) and dlines[pos].strip() else []
        dlines = dlines[:pos] + ([""] if d_pkg is not None else []) + merged + tail + dlines[pos:]
    body = "\n".join(dlines).rstrip("\n")
    out = body + ("\n\n" if body else "") + "\n\n".join(chunks) + "\n"
    write_text(a.dest, relax(out), d_eol)
    print(f"moved {len(chunks)} declaration(s) -> {a.dest}")
    return 0


def cmd_sizes(a):
    allow = set(filter(None, (a.allow or "").split(",")))
    only = set(filter(None, (a.only or "").split(",")))
    bad = 0
    for p in sorted(Path(a.dir).rglob("*.kt")):
        if p.name in allow or (only and p.name not in only):
            continue
        n = len(read_text(p)[0].splitlines())
        if n > a.max:
            print(f"TOO LONG {n} > {a.max}: {p.as_posix()}")
            bad = 1
    return bad


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    sub = ap.add_subparsers(dest="cmd", required=True)
    s = sub.add_parser("snapshot")
    s.add_argument("dir")
    s.add_argument("out")
    s.set_defaults(fn=cmd_snapshot)
    c = sub.add_parser("check")
    c.add_argument("dir")
    c.add_argument("snap")
    c.set_defaults(fn=cmd_check)
    m = sub.add_parser("move")
    m.add_argument("src")
    m.add_argument("dest")
    m.add_argument("symbols", nargs="+")
    m.set_defaults(fn=cmd_move)
    z = sub.add_parser("sizes")
    z.add_argument("dir")
    z.add_argument("--max", type=int, required=True)
    z.add_argument("--allow")
    z.add_argument("--only")
    z.set_defaults(fn=cmd_sizes)
    a = ap.parse_args(argv)
    return a.fn(a)


if __name__ == "__main__":
    sys.exit(main())
