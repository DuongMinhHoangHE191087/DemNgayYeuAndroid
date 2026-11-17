"""KMP gate for P4 UI code: python ui_kmp_gate.py --group <name>[,<name>] [--paths f1 f2] [--exempt f1] [--root DIR].

Scans .kt files for Android/JVM-only patterns, one group per seam. Prints `file:line:group` for each
hit. Exit 0 = clean, 1 = hits left, 2 = bad arguments (unknown group, --paths entry not found).
Comment lines (starting with `//`, `/*` or `*`) are skipped.
--paths entries are files or dirs, given as a path or as a bare name resolved under --root.
--exempt entries are skipped when they equal a scanned file's path or its basename.
"""
import argparse
import re
import sys
from pathlib import Path

GROUPS = {
    "imports": r"^import (android|java|javax)\.|^import androidx\.(activity|core)\b",
    "coil": r"^import coil\.",
    "clipboard": r"ClipboardManager|ClipData",
    "pickers": r"ActivityResultContracts|rememberLauncherForActivityResult|android\.net\.Uri",
    "share": r"\bIntent\b|startActivity|createChooser|FileProvider",
    "activity": r"LocalContext|LocalActivity|findActivity|\bActivity\b|ComponentActivity|\bContext\b",
    "dates": r"SimpleDateFormat|DateTimeFormatter|Calendar\.getInstance|GregorianCalendar|java\.util\.Date"
             r"|java\.time|currentTimeMillis|nanoTime|SystemClock",
    "format": r"String\.format|\.format\(",
    "resources": r"\bR\.(string|plurals|drawable|mipmap)\b|^import com\.example\.R$"
                 r"|^import androidx\.compose\.ui\.res\.(stringResource|pluralStringResource)|fromContext",
    "locale": r"java\.util\.Locale|SharedPreferences|LocalConfiguration",
    "blur": r"\.blur\(",
    "video": r"VideoView|MediaController|AndroidView",
    "buildconfig": r"BuildConfig",
    "viewmodel": r"AndroidViewModel|getApplication",
}
PATTERNS = {g: re.compile(p) for g, p in GROUPS.items()}
DEFAULT_ROOT = "app/src/main/java/com/example/ui"


def resolve(entry, root):
    """Return .kt files for a --paths entry, or None when it matches nothing."""
    p = Path(entry)
    if not p.exists():
        hits = sorted(root.rglob(p.name)) if root.is_dir() else []
        if not hits:
            return None
        p = hits[0]
    return sorted(p.rglob("*.kt")) if p.is_dir() else [p]


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--group", required=True, help="comma-separated: " + ",".join(GROUPS))
    ap.add_argument("--paths", nargs="+")
    ap.add_argument("--exempt", nargs="+", default=[])
    ap.add_argument("--root", default=DEFAULT_ROOT)
    a = ap.parse_args(argv)

    groups = [g for g in a.group.split(",") if g]
    unknown = [g for g in groups if g not in PATTERNS]
    if unknown or not groups:
        print(f"unknown group: {','.join(unknown) or '(none)'}", file=sys.stderr)
        return 2

    root = Path(a.root)
    if a.paths:
        files = []
        for entry in a.paths:
            found = resolve(entry, root)
            if found is None:
                print(f"path not found: {entry}", file=sys.stderr)
                return 2
            files += found
    else:
        files = sorted(root.rglob("*.kt"))

    exempt = {Path(e).as_posix() for e in a.exempt} | {Path(e).name for e in a.exempt}
    hits = 0
    for f in dict.fromkeys(files):
        if f.as_posix() in exempt or f.name in exempt:
            continue
        for n, line in enumerate(f.read_text(encoding="utf-8", errors="replace").splitlines(), 1):
            s = line.strip()
            if s.startswith(("//", "/*", "*")):
                continue
            for g in groups:
                if PATTERNS[g].search(s):
                    print(f"{f.as_posix()}:{n}:{g}")
                    hits += 1
    return 1 if hits else 0


if __name__ == "__main__":
    sys.exit(main())
