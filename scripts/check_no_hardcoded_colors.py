#!/usr/bin/env python3
"""Fail CI if Java source constructs colors directly instead of using resources."""

from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
SOURCE_ROOT = ROOT / "app" / "src"
PATTERN = re.compile(r"\bColor\s*\.\s*(?:rgb|parseColor)\s*\(")

# Explicit exceptions are (repository-relative Java path, 1-based line number).
# Keep this empty unless a narrowly scoped exception is reviewed and approved.
EXCEPTIONS = set()

violations = []
for source in sorted(SOURCE_ROOT.rglob("*.java")):
    relative = source.relative_to(ROOT).as_posix()
    for line_number, line in enumerate(source.read_text(encoding="utf-8").splitlines(), 1):
        if PATTERN.search(line) and (relative, line_number) not in EXCEPTIONS:
            violations.append(f"{relative}:{line_number}: {line.strip()}")

if violations:
    print("Direct Java color constructors are forbidden; use UiResources.color(..., R.color.*):", file=sys.stderr)
    print("\n".join(violations), file=sys.stderr)
    sys.exit(1)

print("PASS: no Color.rgb(...) or Color.parseColor(...) calls in Java source.")
