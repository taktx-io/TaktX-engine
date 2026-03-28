#!/usr/bin/env python3
"""
Generate coverage badges from JaCoCo XML reports.
Creates SVG badges for each module and an overall project badge.
"""

import xml.etree.ElementTree as ET
import sys
from pathlib import Path
from typing import Dict, Tuple


def get_color(coverage: float) -> str:
    """Determine badge color based on coverage percentage."""
    if coverage >= 90:
        return "brightgreen"
    elif coverage >= 80:
        return "green"
    elif coverage >= 70:
        return "yellowgreen"
    elif coverage >= 60:
        return "yellow"
    elif coverage >= 50:
        return "orange"
    else:
        return "red"


def parse_jacoco_xml(xml_path: Path) -> Tuple[float, int, int]:
    """
    Parse JaCoCo XML report and extract coverage percentage.
    Returns (coverage_percentage, covered_lines, total_lines)
    """
    try:
        tree = ET.parse(xml_path)
        root = tree.getroot()

        # Find all counter elements (LINE type gives us line coverage)
        covered = 0
        missed = 0

        for counter in root.findall(".//counter[@type='LINE']"):
            covered += int(counter.get('covered', 0))
            missed += int(counter.get('missed', 0))

        total = covered + missed
        if total == 0:
            return 0.0, 0, 0

        coverage = (covered / total) * 100
        return round(coverage, 2), covered, total

    except Exception as e:
        print(f"Error parsing {xml_path}: {e}", file=sys.stderr)
        return 0.0, 0, 0


def create_badge_svg(label: str, value: str, color: str) -> str:
    """Create an SVG badge."""
    # Calculate widths
    label_width = len(label) * 6 + 10
    value_width = len(value) * 6 + 10
    total_width = label_width + value_width

    svg = f'''<svg xmlns="http://www.w3.org/2000/svg" width="{total_width}" height="20">
    <linearGradient id="b" x2="0" y2="100%">
        <stop offset="0" stop-color="#bbb" stop-opacity=".1"/>
        <stop offset="1" stop-opacity=".1"/>
    </linearGradient>
    <clipPath id="a">
        <rect width="{total_width}" height="20" rx="3" fill="#fff"/>
    </clipPath>
    <g clip-path="url(#a)">
        <path fill="#555" d="M0 0h{label_width}v20H0z"/>
        <path fill="#{get_color_hex(color)}" d="M{label_width} 0h{value_width}v20H{label_width}z"/>
        <path fill="url(#b)" d="M0 0h{total_width}v20H0z"/>
    </g>
    <g fill="#fff" text-anchor="middle" font-family="DejaVu Sans,Verdana,Geneva,sans-serif" font-size="11">
        <text x="{label_width/2}" y="15" fill="#010101" fill-opacity=".3">{label}</text>
        <text x="{label_width/2}" y="14">{label}</text>
        <text x="{label_width + value_width/2}" y="15" fill="#010101" fill-opacity=".3">{value}</text>
        <text x="{label_width + value_width/2}" y="14">{value}</text>
    </g>
</svg>'''
    return svg


def get_color_hex(color: str) -> str:
    """Convert color name to hex."""
    colors = {
        "brightgreen": "4c1",
        "green": "97ca00",
        "yellowgreen": "a4a61d",
        "yellow": "dfb317",
        "orange": "fe7d37",
        "red": "e05d44",
    }
    return colors.get(color, "9f9f9f")


def main():
    """Main function to generate all badges."""
    project_root = Path(__file__).parent.parent
    badges_dir = project_root / "badges"
    badges_dir.mkdir(exist_ok=True)

    modules = {
        "taktx-engine": project_root / "taktx-engine/build/reports/jacoco/test/jacocoTestReport.xml",
        "taktx-client": project_root / "taktx-client/build/reports/jacoco/test/jacocoTestReport.xml",
        "taktx-client-quarkus": project_root / "taktx-client-quarkus/build/reports/jacoco/test/jacocoTestReport.xml",
        "taktx-client-spring": project_root / "taktx-client-spring/build/reports/jacoco/test/jacocoTestReport.xml",
        "taktx-shared": project_root / "taktx-shared/build/reports/jacoco/test/jacocoTestReport.xml",
    }

    coverage_results = {}
    total_covered = 0
    total_lines = 0

    print("Generating coverage badges...")

    for module_name, xml_path in modules.items():
        if not xml_path.exists():
            print(f"⚠️  Warning: {xml_path} not found, skipping {module_name}")
            continue

        coverage, covered, total = parse_jacoco_xml(xml_path)
        coverage_results[module_name] = coverage
        total_covered += covered
        total_lines += total

        # Generate badge for this module
        badge_svg = create_badge_svg(
            label=f"{module_name} coverage",
            value=f"{coverage:.1f}%",
            color=get_color(coverage)
        )

        badge_path = badges_dir / f"{module_name}-coverage.svg"
        badge_path.write_text(badge_svg)
        print(f"✓ Created {badge_path.name} - {coverage:.1f}%")

    # Generate overall project badge
    if total_lines > 0:
        overall_coverage = (total_covered / total_lines) * 100
        overall_badge_svg = create_badge_svg(
            label="coverage",
            value=f"{overall_coverage:.1f}%",
            color=get_color(overall_coverage)
        )

        overall_badge_path = badges_dir / "coverage.svg"
        overall_badge_path.write_text(overall_badge_svg)
        print(f"✓ Created {overall_badge_path.name} - {overall_coverage:.1f}%")

        # Create a summary JSON file
        import json
        summary = {
            "overall": {
                "coverage": round(overall_coverage, 2),
                "covered": total_covered,
                "total": total_lines
            },
            "modules": {}
        }

        for module_name, coverage in coverage_results.items():
            summary["modules"][module_name] = {
                "coverage": coverage
            }

        summary_path = badges_dir / "coverage-summary.json"
        summary_path.write_text(json.dumps(summary, indent=2))
        print(f"✓ Created {summary_path.name}")

    print("\n✅ Badge generation complete!")
    print(f"📂 Badges saved to: {badges_dir}")


if __name__ == "__main__":
    main()

