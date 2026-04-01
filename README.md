# TaktX Engine — High-Performance BPMN Process Automation

<!-- Build & CI -->
[![Java CI](https://github.com/taktx-io/TaktX-engine/actions/workflows/ci.yml/badge.svg)](https://github.com/taktx-io/TaktX-engine/actions/workflows/ci.yml)

<!-- Code Quality -->
![Coverage](./badges/coverage.svg)
![Engine Coverage](./badges/taktx-engine-coverage.svg)
![Client Coverage](./badges/taktx-client-coverage.svg)
![Client Quarkus Coverage](./badges/taktx-client-quarkus-coverage.svg)
![Client Spring Coverage](./badges/taktx-client-spring-coverage.svg)
![Shared Coverage](./badges/taktx-shared-coverage.svg)

<!-- License & Version -->
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache%202.0-green.svg)](LICENSE)
[![Version](https://img.shields.io/badge/version-0.4.0--beta--1)](VERSION.txt)

TaktX Engine is an open-source, high-performance **BPMN 2.0 process automation engine** built on Apache Kafka. Unlike traditional workflow engines that rely on a central database, TaktX uses Kafka topics and RocksDB-backed state stores as its persistence layer — giving you the throughput, fault-tolerance, and horizontal scalability of a streaming platform with the full expressiveness of BPMN 2.0.

Key differentiators from alternatives (Camunda, Zeebe, Flowable):
- **No dedicated database** — state lives in Kafka-backed stores, fully replayable from the Kafka log
- **Ed25519 message signing** — every worker interaction can be cryptographically authenticated
- **RS256 JWT command authorization** — fine-grained, token-based command control out of the box
- **Single deployable JAR / OCI image** — no separate engine cluster to operate

### Prerequisites

- Java 23+
- Apache Kafka 3.x
- Docker (optional, for containerised deployment)

### Installation

#### Option 1: Using Docker

```bash
docker run -p 8080:8080 ghcr.io/taktx-io/taktx-engine:latest
```

#### Option 2: Build from source

```bash
git clone https://github.com/taktx-io/TaktX-engine.git
cd TaktX-engine
./gradlew build
java -jar taktx-engine/build/quarkus-app/quarkus-run.jar
```

### Basic Usage

Add the client dependency to your project:

```kotlin
// Gradle
implementation("io.taktx:taktx-client:0.4.0-beta-1")

// Or for Spring Boot auto-configuration:
implementation("io.taktx:taktx-client-spring:0.4.0-beta-1")

// Or for Quarkus / CDI auto-configuration:
implementation("io.taktx:taktx-client-quarkus:0.4.0-beta-1")
```

Then connect to the engine via Kafka:

```java
Properties props = new Properties();
props.put("bootstrap.servers", "localhost:9092");
props.put("taktx.engine.tenant-id", "acme");
props.put("taktx.engine.namespace", "default");

TaktXClient client = TaktXClient.newClientBuilder()
    .withProperties(props)
    .build();

client.start();

// Deploy a BPMN process definition
client.deployProcess(new File("path/to/process.bpmn"));

// Start a process instance
UUID instanceId = client.startProcess("invoice-process",
    VariablesDTO.of("orderId", "12345", "amount", 1200));

// Register an external task worker
client.subscribeExternalTask("approve-invoice", job -> {
    boolean approved = /* your logic */ true;
    job.complete(VariablesDTO.of("approved", approved));
});

client.stop(); // Always call on shutdown
```

## Architecture

TaktX Engine is structured as a multi-module Gradle project:

| Module | Description |
|---|---|
| **taktx-engine** | Core BPMN execution engine (Quarkus/Kafka Streams) |
| **taktx-client** | Framework-agnostic Java client library |
| **taktx-client-spring** | Spring Boot auto-configuration wrapper |
| **taktx-client-quarkus** | Quarkus / CDI auto-configuration wrapper |
| **taktx-shared** | Shared DTOs, BPMN models, and utilities |

## Test Coverage

TaktX maintains comprehensive test coverage across all modules:

| Module | Coverage | Status |
|--------|----------|--------|
| **Overall Project** | ![Coverage](./badges/coverage.svg) | 67.5% - Good |
| **taktx-engine** | ![Engine](./badges/taktx-engine-coverage.svg) | 87.0% - Excellent |
| **taktx-client** | ![Client](./badges/taktx-client-coverage.svg) | 20.8% - Improving |
| **taktx-client-quarkus** | ![Quarkus](./badges/taktx-client-quarkus-coverage.svg) | 55.4% - Acceptable |
| **taktx-shared** | ![Shared](./badges/taktx-shared-coverage.svg) | 2.1% - Work in Progress |

Coverage badges are automatically generated from JaCoCo test reports and include both unit tests and integration tests with Testcontainers.

## Configuration

TaktX Engine can be configured using application properties or environment variables:

```properties
# Core Engine Configuration
taktx.engine.keyvaluestoretype=inmemory
taktx.engine.host=localhost
# Both tenant-id and namespace are required. All topic names are derived from them:
#   regular topics:  <tenantId>.<namespace>.<topic>
#   changelog topics: <tenantId>.<namespace>.taktx-engine-...-changelog
# Both sets share the same ACL prefix, so a single wildcard rule covers the whole tenant.
taktx.engine.tenant-id=my-tenant
taktx.engine.namespace=default
# Partition count for all fixed managed topics.
taktx.engine.topic.partitions=3
```

## Licensing

All components in this repository are licensed under the **[Apache License 2.0](LICENSE)**.

For full license terms see the `LICENSE` file in each module directory.

## Documentation

For full documentation, visit our [documentation site](https://taktx.io/documentation).

## Contributing

We welcome contributions! Please see [CONTRIBUTING.md](CONTRIBUTING.md) for details on how to contribute to TaktX Engine.

### Development Setup

```bash
# Clone repository
git clone https://github.com/taktx-io/TaktX-engine.git
cd TaktX-engine

# Build and run tests
./gradlew build

# Run locally (requires a running Kafka broker)
./gradlew :taktx-engine:quarkusDev
```

## Community & Support

- [GitHub Issues](https://github.com/taktx-io/TaktX-engine/issues): Bug reports and feature requests
- [Commercial Support](https://taktx.io/support): Enterprise support options

## License

TaktX Engine is licensed under the [Apache License 2.0](LICENSE).

