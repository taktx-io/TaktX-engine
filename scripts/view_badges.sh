#!/usr/bin/env bash
#
# TaktX - A high-performance BPMN engine
# Copyright (c) 2025 Eric Hendriks All rights reserved.
# This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
# Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
# For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
#

# View coverage badges locally
# Usage: ./scripts/view_badges.sh

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
BADGES_DIR="$PROJECT_ROOT/badges"

echo "📊 TaktX Coverage Badges"
echo "========================"
echo ""

if [ -f "$BADGES_DIR/coverage-summary.json" ]; then
    echo "Coverage Summary:"
    cat "$BADGES_DIR/coverage-summary.json" | python3 -m json.tool
    echo ""
else
    echo "⚠️  No coverage summary found. Run './gradlew testWithBadges' first."
    echo ""
fi

echo "Badge Files:"
ls -lh "$BADGES_DIR"/*.svg 2>/dev/null || echo "No badge files found"
echo ""

echo "To view badges in browser:"
echo "  open $BADGES_DIR/coverage.svg"
echo "  open $BADGES_DIR/taktx-engine-coverage.svg"
echo "  open $BADGES_DIR/taktx-client-coverage.svg"
echo "  open $BADGES_DIR/taktx-client-quarkus-coverage.svg"
echo "  open $BADGES_DIR/taktx-shared-coverage.svg"
echo ""

# Ask if user wants to open badges
read -p "Open all badges in browser? (y/N) " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    for badge in "$BADGES_DIR"/*.svg; do
        if [ -f "$badge" ]; then
            open "$badge"
            sleep 0.2  # Small delay to avoid overwhelming the browser
        fi
    done
    echo "✅ Opened all badges"
fi

