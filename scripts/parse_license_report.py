#!/usr/bin/env python3
"""
parse_license_report.py
Parses the Gradle dependency license report JSON and flags copyleft licenses.
Usage: python3 scripts/parse_license_report.py path/to/index.json
"""

import sys
import json
import csv
from pathlib import Path

COPYLEFT_PATTERNS = [
    "gpl", "lgpl", "agpl", "affero", "epl", "mozilla public", "mpl", "cddl", "cpl"
]

def is_copyleft(license_name):
    """Check if a license name matches common copyleft patterns."""
    if not license_name:
        return False
    ln = license_name.lower()
    return any(p in ln for p in COPYLEFT_PATTERNS)

def normalize_artifact(entry):
    """Extract artifact coordinates from a dependency entry."""
    if "moduleName" in entry:
        return entry["moduleName"]
    if "name" in entry:
        group = entry.get("group", "")
        name = entry.get("name", "")
        version = entry.get("version", "")
        if group and name and version:
            return f"{group}:{name}:{version}"
        elif name and version:
            return f"{name}:{version}"
        elif name:
            return name
    return str(entry)

def extract_licenses(entry):
    """Extract license names from a dependency entry."""
    licenses = []

    # Check for direct moduleLicense field
    mod_lic = entry.get("moduleLicense")
    if mod_lic and mod_lic.strip():
        licenses.append(mod_lic.strip())
        return licenses

    # Check for nested license structures
    lic_objs = entry.get("moduleLicenses") or entry.get("licenses") or entry.get("license") or []

    if isinstance(lic_objs, dict):
        lic_objs = [lic_objs]

    for l in lic_objs:
        if isinstance(l, dict):
            name = l.get("moduleLicense") or l.get("name") or l.get("license") or l.get("id")
        else:
            name = str(l)
        if name:
            licenses.append(name.strip())

    return licenses if licenses else ["<UNKNOWN>"]

def main():
    if len(sys.argv) < 2:
        print("Usage: python3 scripts/parse_license_report.py path/to/index.json")
        sys.exit(2)

    json_path = Path(sys.argv[1])
    if not json_path.exists():
        print(f"File not found: {json_path}")
        sys.exit(2)

    data = json.loads(json_path.read_text(encoding="utf-8"))

    # Plugin JSON has "dependencies" key
    entries = data.get("dependencies") or []
    if not isinstance(entries, list):
        print(f"Unexpected JSON structure. Expected 'dependencies' list, got: {list(data.keys())}")
        sys.exit(1)

    rows = []
    copyleft_rows = []
    unknown_rows = []

    for e in entries:
        artifact = normalize_artifact(e)
        licenses = extract_licenses(e)
        copyleft = any(is_copyleft(ln) for ln in licenses)
        unknown = "<UNKNOWN>" in licenses

        rows.append((artifact, "; ".join(licenses), "YES" if copyleft else "NO"))

        if copyleft:
            copyleft_rows.append((artifact, licenses))
        if unknown:
            unknown_rows.append(artifact)

    out_dir = json_path.parent
    out_csv = out_dir / "per-artifact-license-map.csv"

    with out_csv.open("w", newline="", encoding="utf-8") as fh:
        writer = csv.writer(fh)
        writer.writerow(["artifact", "licenses", "copyleft"])
        for r in rows:
            writer.writerow(r)

    print(f"\n✓ Wrote per-artifact license map to: {out_csv}")
    print(f"✓ Total dependencies analyzed: {len(rows)}")

    if copyleft_rows:
        print(f"\n⚠️  FLAGGED {len(copyleft_rows)} COPYLEFT DEPENDENCIES:")
        for art, lic in copyleft_rows:
            print(f"   • {art}")
            print(f"     Licenses: {', '.join(lic)}")
    else:
        print("\n✓ No copyleft licenses detected by heuristic patterns.")

    if unknown_rows:
        print(f"\n⚠️  {len(unknown_rows)} dependencies with UNKNOWN licenses:")
        for art in unknown_rows[:10]:  # Show first 10
            print(f"   • {art}")
        if len(unknown_rows) > 10:
            print(f"   ... and {len(unknown_rows) - 10} more")
        print("\n   → Manual review recommended for these dependencies.")

if __name__ == "__main__":
    main()

