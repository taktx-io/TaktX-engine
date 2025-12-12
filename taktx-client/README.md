# TAKT Client

![Coverage](https://raw.githubusercontent.com/taktx-io/TaktX-engine/main/badges/taktx-client-coverage.svg)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache%202.0-green.svg)](LICENSE)

The TaktX Client library provides a simple Java API for interacting with the TaktX BPMN Engine.

## Installation

Add the following dependency to your project:

### Maven

```xml
<dependency>
    <groupId>io.taktx</groupId>
    <artifactId>taktx-client</artifactId>
    <version>0.0.9-alpha-3-SNAPSHOT</version>
</dependency>
```

### Gradle

```kotlin
implementation("io.taktx:taktx-client:0.0.9-alpha-3-SNAPSHOT")
```

## Usage

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

## License

Licensed under the Apache License 2.0. See [LICENSE](LICENSE) for details.

