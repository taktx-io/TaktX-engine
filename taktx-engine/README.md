# TaktX Engine

[![License: Apache 2.0](https://img.shields.io/badge/License-Apache%202.0-green.svg)](LICENSE)
![Coverage](../badges/taktx-engine-coverage.svg)

The core BPMN 2.0 process execution engine. Built on Quarkus and Apache Kafka Streams, it uses
RocksDB-backed state stores for durable process state with no external database dependency.

## Features

- Full BPMN 2.0 execution (service tasks, user tasks, gateways, timers, message events, signals, and more)
- Kafka Streams topology — horizontally scalable, fault-tolerant, replayable from the Kafka log
- Ed25519 message signing — cryptographic authentication of every worker interaction
- RS256 JWT command authorization — token-based, fine-grained command control
- Multi-tenant and multi-namespace support via topic prefixing
- Native image support (GraalVM/Mandrel)

## Quick Start

### Docker (recommended)

```bash
docker run -p 8080:8080 \
  -e TAKTX_ENGINE_TENANT_ID=acme \
  -e TAKTX_ENGINE_NAMESPACE=default \
  -e KAFKA_BOOTSTRAP_SERVERS=host.docker.internal:9092 \
  ghcr.io/taktx-io/taktx-engine:latest
```

### From source

```bash
# Requires a running Kafka broker
./gradlew :taktx-engine:quarkusDev
```

## Building Docker Images

### Multi-platform Build (Recommended)

Build for both `linux/amd64` and `linux/arm64` (Apple Silicon):

```bash
# Using the convenience script
Upda../scripts/build-docker-multiarch.sh 0.5.0-beta

# Or using docker buildx directly
docker buildx build \
  --platform linux/amd64,linux/arm64 \
  --build-arg VERSION=0.5.0-beta \
  -f taktx-engine/Dockerfile.jvm \
  -t ghcr.io/taktx-io/taktx-engine:0.5.0-beta \
  -t ghcr.io/taktx-io/taktx-engine:latest \
  --push \
  .
```

### Local Development Build

```bash
docker buildx build \
  --platform linux/amd64 \
  --build-arg VERSION=dev \
  -f taktx-engine/Dockerfile.jvm \
  -t ghcr.io/taktx-io/taktx-engine:dev \
  --load \
  .
```

### Build features

- **Multi-stage build**: Optimised layer caching and minimal runtime image
- **BuildKit cache mounts**: Fast rebuilds by caching Gradle dependencies
- **Multi-platform**: Single command builds for AMD64 and ARM64

## Native Executable

```bash
# Build native executable for the current platform (macOS)
QUARKUS_NATIVE_ADDITIONAL_BUILD_ARGS="--initialize-at-run-time=scala.util.Random$" \
  quarkus build --native --no-tests

# Build native executable for Linux (cross-compile in container)
QUARKUS_NATIVE_ADDITIONAL_BUILD_ARGS="--initialize-at-run-time=scala.util.Random$" \
  quarkus build --native --no-tests -Dquarkus.native.container-build=true
```

## License

Licensed under the [Apache License 2.0](LICENSE).
