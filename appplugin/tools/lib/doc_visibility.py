"""2.3.0 ROB3: kiểu/thành viên INTEGRATION.md §9 bảo app dùng phải `public` trong nguồn Kotlin (gọi từ integration_doc_check.sh).

Tập tên = mọi `Lớp` ở bảng gói §9 ∪ mọi `Lớp.thành_viên` trong §9. Lớp: khai báo top-level (cột 0) trong src/main/java ∪
appplugin-game/src/main/java (test: IDC_SRC = danh sách thư mục, cách nhau bằng `:`) không mang internal/private. Thành viên:
>= 1 khai báo `fun|val|var|const val <tên>` không internal/private, hoặc dòng enum entry `^\\s*<TÊN>\\b[,;(]` trong thân
`enum class` của lớp, trong file khai báo lớp. Không tìm thấy khai báo = FAIL (fail-closed).
Thành viên tìm trong THÂN lớp (tới `}` cột 0 kế tiếp, R-ROB3 m1); lớp chọn theo cột gói của bảng §9 (m2). Hạn chế đã biết, đều fail-closed (m3): `@Keep` cùng dòng với `class`,
`val` ở constructor một dòng, entry enum cuối không có `,`/`;`.
"""
import os, re

HIDDEN = re.compile(r"\b(internal|private)\b")
REF = re.compile(r"(?<![\w.$])([A-Z]\w*)\.(?:(?:instance|INSTANCE)\.)?([A-Za-z_]\w*)")
CLS = r"^((?:(?:public|internal|private|open|abstract|sealed|data|enum|inline|value|fun|annotation|final)[ \t]+)*)(class|object|interface)[ \t]+%s\b"
MEM = r"^[ \t]*(?:@[\w.]+(?:\([^)\n]*\))?[ \t]+)*((?:[a-z]+[ \t]+)*)(?:fun|val|var)[ \t]+(?:<[^>\n]*>[ \t]*)?(?:\w+\.)?%s\b"


def _sources(root):
    dirs = os.environ.get("IDC_SRC") or ":".join(os.path.join(root, d) for d in ("src/main/java", "appplugin-game/src/main/java"))
    files = {}
    for d in filter(None, dirs.split(":")):
        for base, _, names in os.walk(d):
            for n in names:
                if n.endswith(".kt"):
                    p = os.path.join(base, n)
                    files[p] = open(p, encoding="utf-8", errors="replace").read()
    return files


def _pkg(text):
    m = re.search(r"^package ([\w.]+)", text, flags=re.M)
    return m.group(1) if m else None


def _loc(root, path, text, pos):
    rel = os.path.relpath(path, root) if os.path.abspath(path).startswith(root + os.sep) else path
    return "%s:%d" % (rel, text.count("\n", 0, pos) + 1)


def _member(root, decls, cls, mem):
    hidden = None
    for path, text, m in decls:
        rest = text[m.end():]; end = re.search(r"^}", rest, flags=re.M)
        body = text[m.start():m.end() + (end.start() if end else len(rest))]  # R-ROB3 m1: chỉ thân lớp này, không cả file
        for d in re.finditer(MEM % re.escape(mem), body, flags=re.M):
            if not HIDDEN.search(d.group(1)): return None
            hidden = hidden or _loc(root, path, text, m.start() + d.start())
        if "enum" in m.group(1) and re.search(r"^\s*%s\b\s*[,;(]" % re.escape(mem), rest[:end.start() if end else None], flags=re.M): return None
    return "§9: %s.%s %s" % (cls, mem, "không public trong " + hidden if hidden else "không tìm thấy khai báo trong nguồn")


def check(root, doc_text, skip_cls=(), skip_mem=()):
    m9 = re.search(r"^## 9\..*?(?=^## |\Z)", doc_text, flags=re.M | re.S); sec = m9.group(0) if m9 else ""
    spans = re.findall(r"`([^`\n]+)`", sec)
    rows = [ln.split("|") for ln in sec.splitlines() if ln.startswith("|") and ln.count("|") > 2]
    classes = [c for r in rows for c in re.findall(r"`([A-Z]\w*)`", r[1])]
    pkgs = {}  # R-ROB3 m2: cột gói của bảng §9 chọn file khai báo (ConsentManager có hai gói)
    for r in rows:
        for c in re.findall(r"`([A-Z]\w*)`", r[1]): pkgs.setdefault(c, set()).update(re.findall(r"`(com\.[\w.]+)`", r[2]))
    refs = [(c, m) for sp in spans for c, m in REF.findall(sp) if c not in skip_cls and m not in skip_mem]
    if not (classes or refs): return ["§9: không tìm thấy mục '## 9.' có tên lớp trong tài liệu (fail-closed)"]
    files, bad, decl_of = _sources(root), [], {}
    for cls in dict.fromkeys(classes + [c for c, _ in refs]):
        decl_of[cls] = []
        for pkg in sorted(pkgs.get(cls) or [None]):
            found = [(p, t, m) for p, t in files.items() if pkg is None or _pkg(t) == pkg for m in re.finditer(CLS % re.escape(cls), t, flags=re.M)]
            ok = [d for d in found if not HIDDEN.search(d[2].group(1))]; decl_of[cls] += ok
            if not found: bad.append("§9: %s không tìm thấy khai báo top-level trong nguồn%s" % (cls, " (gói %s)" % pkg if pkg else ""))
            elif not ok: bad.append("§9: %s không public trong %s" % (cls, _loc(root, found[0][0], found[0][1], found[0][2].start())))
    for cls, mem in dict.fromkeys(refs):
        msg = _member(root, decl_of[cls], cls, mem) if decl_of.get(cls) else None
        if msg: bad.append(msg)
    return bad
