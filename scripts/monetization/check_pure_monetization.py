"""Fail when pure-Kotlin monetization code imports Android, JVM-only or test-framework APIs (M-29).

Usage: python scripts/monetization/check_pure_monetization.py [root]
Prints file:line for each forbidden import and exits 1; exits 0 when clean.
Missing directories or files are skipped, not treated as errors.
"""
import re
import sys
from pathlib import Path

FORBIDDEN = re.compile(
    r"^\s*import\s+(android|java|javax|com\.android|com\.google\.android|androidx"
    r"|com\.app\.plugin|org\.junit|org\.robolectric|io\.mockk)\."
)
SUFFIXES = (".kt", ".java")
MAIN = Path("app/src/main/java/com/example")
TEST = Path("app/src/test/java/com/example")


def sources(base, skip_dirs=()):
    """Return .kt/.java files under base, skipping direct child folders named in skip_dirs."""
    return [
        p for p in base.rglob("*")
        if p.suffix in SUFFIXES and p.is_file() and p.relative_to(base).parts[0] not in skip_dirs
    ]


def targets(root):
    main, test = root / MAIN, root / TEST
    files = []
    for sub in ("consent", "ads", "billing", "entitlement", "testing"):
        files += sources(main / "monetization" / sub)
    files += sources(test / "monetization", skip_dirs={"android"})
    files += [main / "billing" / f"{name}.kt" for name in ("PlanOffer", "PurchaseEvent", "VipProductIds")]
    files.append(test / "billing" / "PlanOfferTest.kt")
    return [f for f in files if f.is_file()]


def violations(root):
    for path in targets(root):
        with path.open(encoding="utf-8", errors="replace") as f:
            for lineno, line in enumerate(f, 1):
                if FORBIDDEN.match(line):
                    yield path, lineno, line.strip()


def main(argv):
    root = Path(argv[1]) if len(argv) > 1 else Path(__file__).resolve().parents[2]
    found = list(violations(root))
    for path, lineno, text in found:
        print(f"{path.relative_to(root).as_posix()}:{lineno}: {text}")
    if found:
        return 1
    print("OK: no forbidden imports")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
