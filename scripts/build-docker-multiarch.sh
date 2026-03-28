#!/bin/bash
#
# TaktX - A high-performance BPMN engine
# Copyright (c) 2025 Eric Hendriks
# Licensed under the Apache License, Version 2.0
#

set -e

# TaktX Engine - Multi-platform Docker Build Script
# Builds Docker images for linux/amd64 and linux/arm64

VERSION=${1:-dev}
PUSH=${2:-false}

echo "🐳 Building TaktX Engine Docker image (multi-platform)"
echo "   Version: $VERSION"
echo "   Platforms: linux/amd64, linux/arm64"

# Ensure we're in the project root
cd "$(dirname "$0")/.."

# Build command
BUILD_CMD="docker buildx build \
  --platform linux/amd64,linux/arm64 \
  --build-arg VERSION=$VERSION \
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

