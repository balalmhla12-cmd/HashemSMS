#!/usr/bin/env python3
"""Check WCAG 2.1 AA contrast for HashemSMS semantic text/background pairs."""
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
PAIRS = [
    ("on_primary", "primary"),
    ("on_fab", "fab"),
    ("on_chip_selected", "chip_selected"),
    ("on_surface", "unread_accent"),
    ("on_surface", "surface"),
    ("on_surface_variant", "surface"),
    ("on_surface_subtle", "surface"),
    ("on_primary_container", "primary_container"),
    ("conversation_text", "bubble_in"),
    ("conversation_text", "bubble_out"),
    ("on_error", "error"),
    ("text", "card_surface"),
    ("timestamp", "surface"),
]

def luminance(hex_color):
    rgb = [int(hex_color[i:i + 2], 16) / 255 for i in (1, 3, 5)]
    linear = [v / 12.92 if v <= 0.04045 else ((v + 0.055) / 1.055) ** 2.4 for v in rgb]
    return 0.2126 * linear[0] + 0.7152 * linear[1] + 0.0722 * linear[2]

def contrast(foreground, background):
    a, b = sorted((luminance(foreground), luminance(background)), reverse=True)
    return (a + 0.05) / (b + 0.05)

failed = []
for resource_dir in ("values", "values-night"):
    xml_path = ROOT / "app/src/main/res" / resource_dir / "colors.xml"
    colors = {node.get("name"): (node.text or "").strip() for node in ET.parse(xml_path).getroot().findall("color")}
    for foreground, background in PAIRS:
        ratio = contrast(colors[foreground], colors[background])
        print(f"{resource_dir}: {foreground} on {background} = {ratio:.2f}:1")
        if ratio < 4.5:
            failed.append((resource_dir, foreground, background, ratio))
if failed:
    for directory, foreground, background, ratio in failed:
        print(f"FAIL {directory}: {foreground} on {background} = {ratio:.2f}:1 (requires >= 4.5:1)", file=sys.stderr)
    sys.exit(1)
print("PASS: all tested text/background pairs meet WCAG AA contrast (>= 4.5:1).")
