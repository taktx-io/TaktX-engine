# Takt BPMN Engine

![Coverage](../badges/taktx-engine-coverage.svg)

The TaktX Engine is the core BPMN process automation engine with **87% test coverage**, including comprehensive integration tests with Testcontainers.

## Building Docker Image

### Multi-platform Build (Recommended)

Build for both linux/amd64 and linux/arm64 (Apple Silicon):

```bash
# Using the convenience script
../scripts/build-docker-multiarch.sh 1.0.0

# Or using docker buildx directly
docker buildx build \
  --platform linux/amd64,linux/arm64 \
  --build-arg VERSION=1.0.0 \
  --build-arg CHANGE_DATE=$(date -d "+4 years" +%Y-%m-%d) \
  -f taktx-engine/Dockerfile.jvm \
  -t ghcr.io/taktx-io/taktx-engine:1.0.0 \
  -t ghcr.io/taktx-io/taktx-engine:latest \
  --push \
  .
```

### Local Development Build

Build for your current platform only (faster):

```bash
docker buildx build \
  --platform linux/amd64 \
  --build-arg VERSION=dev \
  --build-arg CHANGE_DATE=$(date -d "+4 years" +%Y-%m-%d) \
  -f taktx-engine/Dockerfile.jvm \
  -t ghcr.io/taktx-io/taktx-engine:dev \
  --load \
  .
```

### Features

- **Multi-stage build**: Optimized layer caching and minimal runtime image
- **BuildKit cache mounts**: Fast rebuilds by caching Gradle dependencies
- **Multi-platform**: Single command builds for AMD64 and ARM64
- **Self-contained**: No need for local Java/Gradle installation


## Build native executable on mac for mac
QUARKUS_NATIVE_ADDITIONAL_BUILD_ARGS="--initialize-at-run-time=scala.util.Random$" quarkus build --native --no-tests
## Build native executable on mac for Linux
QUARKUS_NATIVE_ADDITIONAL_BUILD_ARGS="--initialize-at-run-time=scala.util.Random$" quarkus build --native --no-tests -Dquarkus.native.container-build=true

## Running container

```bash
docker run -d \
    -e "injectedhost=host.docker.internal" \
    -e "injectedport=8081" \
    -e "quarkus.profile=dockerlocal" \
    -e "namespace=[namespace]" \
    -p 8081:8080 \
    -it ghcr.io/qunit/bpmnmeister:1.0.0
```
