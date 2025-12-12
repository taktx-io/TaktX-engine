# TaktX Engine - High-Performance BPMN Process Automation

<!-- Build & CI -->
[![Java CI](https://github.com/taktx-io/TaktX-engine/actions/workflows/ci.yml/badge.svg)](https://github.com/taktx-io/TaktX-engine/actions/workflows/ci.yml)
[![License Compliance](https://github.com/taktx-io/TaktX-engine/actions/workflows/license-compliance.yml/badge.svg)](https://github.com/taktx-io/TaktX-engine/actions/workflows/license-compliance.yml)

<!-- Code Quality -->
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=taktx-io_TaktX-engine&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=taktx-io_TaktX-engine)
[![Code Coverage](https://codecov.io/gh/taktx-io/TaktX-engine/branch/main/graph/badge.svg)](https://codecov.io/gh/taktx-io/TaktX-engine)
[![Maintainability Rating](https://sonarcloud.io/api/project_badges/measure?project=taktx-io_TaktX-engine&metric=sqale_rating)](https://sonarcloud.io/summary/new_code?id=taktx-io_TaktX-engine)
[![Reliability Rating](https://sonarcloud.io/api/project_badges/measure?project=taktx-io_TaktX-engine&metric=reliability_rating)](https://sonarcloud.io/summary/new_code?id=taktx-io_TaktX-engine)

<!-- Security -->
[![Security Rating](https://sonarcloud.io/api/project_badges/measure?project=taktx-io_TaktX-engine&metric=security_rating)](https://sonarcloud.io/summary/new_code?id=taktx-io_TaktX-engine)
[![Vulnerabilities](https://sonarcloud.io/api/project_badges/measure?project=taktx-io_TaktX-engine&metric=vulnerabilities)](https://sonarcloud.io/summary/new_code?id=taktx-io_TaktX-engine)
[![Known Vulnerabilities](https://snyk.io/test/github/taktx-io/TaktX-engine/badge.svg)](https://snyk.io/test/github/taktx-io/TaktX-engine)

<!-- License & Version -->
[![License: BSL 1.0](https://img.shields.io/badge/License-BSL%201.0-blue.svg)](LICENSE.md)
[![SDK License: Apache 2.0](https://img.shields.io/badge/SDK%20License-Apache%202.0-green.svg)](taktx-client/LICENSE)
[![Version](https://img.shields.io/badge/version-0.0.9--alpha--3-orange.svg)](VERSION.txt)

<!-- Stats -->
[![Lines of Code](https://sonarcloud.io/api/project_badges/measure?project=taktx-io_TaktX-engine&metric=ncloc)](https://sonarcloud.io/summary/new_code?id=taktx-io_TaktX-engine)
[![Technical Debt](https://sonarcloud.io/api/project_badges/measure?project=taktx-io_TaktX-engine&metric=sqale_index)](https://sonarcloud.io/summary/new_code?id=taktx-io_TaktX-engine)
[![Code Smells](https://sonarcloud.io/api/project_badges/measure?project=taktx-io_TaktX-engine&metric=code_smells)](https://sonarcloud.io/summary/new_code?id=taktx-io_TaktX-engine)
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

## Configuration

TaktX Engine can be configured using application properties or environment variables:

```properties
# Core Engine Configuration
taktx.engine.keyvaluestoretype=inmemory
taktx.engine.host=localhost
taktx.engine.namespace=namespace
taktx.engine.topic.partitions=3  # Free version limit
```

## Licensing

TaktX Engine is available under the Business Source License 1.0:

- **Free Version**: Limited to 3 Kafka partitions
- **Commercial License**: Unlimited partitions and enterprise support
- **Open Source Transition**: Converts to Apache 2.0 after 4 years

For commercial licensing, please contact [info@taktx.io](mailto:info@taktx.io).

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

TaktX Engine is licensed under the [TaktX Business Source License 1.0](LICENSE.md).
