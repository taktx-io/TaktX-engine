# TaktX Engine - High-Performance BPMN Process Automation

<!-- Build & CI -->
[![Java CI](https://github.com/taktx-io/TaktX-engine/actions/workflows/ci.yml/badge.svg)](https://github.com/taktx-io/TaktX-engine/actions/workflows/ci.yml)

<!-- Code Quality -->
![Coverage](./badges/coverage.svg)
![Engine Coverage](./badges/taktx-engine-coverage.svg)
![Client Coverage](./badges/taktx-client-coverage.svg)
![Client Quarkus Coverage](./badges/taktx-client-quarkus-coverage.svg)
![Shared Coverage](./badges/taktx-shared-coverage.svg)

<!-- Security -->

<!-- License & Version -->
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache%202.0-green.svg)](LICENSE.md)
[![Version](https://img.shields.io/badge/version-0.1.0--beta--1-orange.svg)](VERSION.txt)

- Java 23+
- Apache Kafka
- Docker (optional)

### Installation

#### Option 1: Using Docker

```bash
docker run -p 8080:8080 taktx/taktx-engine:latest
```

#### Option 2: Using Gradle

```bash
git clone https://github.com/taktx/TaktX-engine.git
cd TaktX-engine
./gradlew build
java -jar taktx-engine/build/quarkus-app/quarkus-run.jar
```

### Basic Usage

```java
// Create a TaktX client
TaktxClient client = TaktxClient.create("http://localhost:8080");

// Deploy a BPMN process
client.deployProcess(new File("path/to/process.bpmn"));

// Start a process instance
ProcessInstance instance = client.startProcess("process-key")
    .variable("orderId", "12345")
    .start();

// Complete a task
client.completeTask("task-id")
    .variable("approved", true)
    .complete();
```

## Architecture

TaktX Engine consists of several modules:

- **taktx-engine**: Core engine implementation
- **taktx-client**: Client library for process interaction
- **taktx-shared**: Shared models and utilities
- **testclient-quarkus**: Example implementation based on Quarkus ans using of the client

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
# The total partition budget across all managed topics is enforced by the active license.
# The default community budget is 60 total partitions. A license pushed via the
# taktx-configuration topic can extend this limit.
taktx.engine.topic.partitions=3
```

## Licensing

All components in this repository are licensed under the **[Apache License 2.0](LICENSE.md)**.

- **TaktX Engine** (`taktx-engine/`) — Apache License 2.0. All features including Ed25519 message signing and RS256 JWT command authorization are fully enabled. A default partition budget applies to community deployments; a license file pushed via the `taktx-configuration` topic can extend this limit.
- **Client SDKs** (`taktx-client/`, `taktx-client-quarkus/`, `taktx-client-spring/`, `taktx-shared/`) — Apache License 2.0.

For full license terms see the `LICENSE` file in each module directory.

## Documentation

For full documentation, visit our [documentation site](https://docs.taktx.io).

## Contributing

We welcome contributions! Please see [CONTRIBUTING.md](CONTRIBUTING.md) for details on how to contribute to TaktX Engine.

### Development Setup

```bash
# Clone repository
git clone https://github.com/taktx-io/TaktX-engine.git
cd TaktX-engine

# Build and run tests
./gradlew build

# Run locally
./gradlew quarkusDev
```

## Community & Support

- [GitHub Issues](https://github.com/taktx/TaktX-engine/issues): Bug reports and feature requests
- [Commercial Support](https://taktx.io/support): Enterprise support options

## License

TaktX Engine is licensed under the [Apache License 2.0](LICENSE.md).
