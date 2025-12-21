#!/bin/bash
#
# TaktX - A high-performance BPMN engine
# Copyright (c) 2025 Eric Hendriks All rights reserved.
# This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
# Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
# For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
#

set -e

# TaktX Engine - Multi-platform Docker Build Script
# Builds Docker images for linux/amd64 and linux/arm64

VERSION=${1:-dev}
PUSH=${2:-false}

echo "🐳 Building TaktX Engine Docker image (multi-platform)"
echo "   Version: $VERSION"
echo "   Platforms: linux/amd64, linux/arm64"

# Calculate license change date (4 years from today)
if [[ "$OSTYPE" == "darwin"* ]]; then
    # macOS
    CHANGE_DATE=$(date -v +4y +%Y-%m-%d)
else
    # Linux
    CHANGE_DATE=$(date -d "+4 years" +%Y-%m-%d)
fi

echo "   License Change Date: $CHANGE_DATE"

# Ensure we're in the project root
cd "$(dirname "$0")/.."

# Build command
BUILD_CMD="docker buildx build \
  --platform linux/amd64,linux/arm64 \
  --build-arg VERSION=$VERSION \
  --build-arg CHANGE_DATE=$CHANGE_DATE \
  -f taktx-engine/Dockerfile.jvm \
  -t ghcr.io/taktx-io/taktx-engine:$VERSION"

# Add latest tag if not dev version
if [ "$VERSION" != "dev" ]; then
    BUILD_CMD="$BUILD_CMD -t ghcr.io/taktx-io/taktx-engine:latest"
fi

# Add push flag if requested
if [ "$PUSH" = "true" ] || [ "$PUSH" = "yes" ] || [ "$PUSH" = "1" ]; then
    echo "   Push: enabled"
    BUILD_CMD="$BUILD_CMD --push"
else
    echo "   Push: disabled (use 'true' as second argument to enable)"
    BUILD_CMD="$BUILD_CMD --load"
fi

BUILD_CMD="$BUILD_CMD ."

echo ""
echo "🔨 Executing build..."
echo ""

# Execute the build
eval $BUILD_CMD

echo ""
echo "✅ Build complete!"
echo ""
echo "To run the image locally:"
echo "  docker run -p 8080:8080 ghcr.io/taktx-io/taktx-engine:$VERSION"
echo ""
echo "To push to registry:"
echo "  $0 $VERSION true"

