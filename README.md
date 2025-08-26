# TaktX Engine - High-Performance BPMN Process Automation

[![Java CI with Gradle](https://github.com/taktx-io/TaktX-engine/.github/workflows/ci.yml/badge.svg)](https://github.com/taktx/TaktX-engine/actions/workflows/ci.yml)
[![codecov](https://codecov.io/gh/taktx-io/TaktX-engine/branch/main/graph/badge.svg)](https://codecov.io/gh/taktx-io/TaktX-engine)
[![License](https://img.shields.io/badge/License-BSL%201.1-blue.svg)](LICENSE.md)

## Overview

TaktX Engine is a high-performance BPMN process automation engine built on modern streaming architecture. 
Leveraging Apache Kafka and Quarkus, it provides a scalable, event-driven platform for running business 
processes with high throughput and reliability.


### Key Features

- **Event-Driven Architecture**: Built on Apache Kafka streams for maximum scalability
- **BPMN 2.0 Support**: Complete implementation of the BPMN 2.0 standard
- **Stateless Processing**: Horizontal scaling without sticky sessions
- **High Throughput**: Optimized for processing thousands of tasks per second
- **Fault Tolerance**: Built-in resilience with at-least-once processing guarantees
- **Developer-Friendly API**: Simple client library for easy integration

## Quick Start

### Prerequisites

- Java 21+
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
taktx.engine.tenant=tenant
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
