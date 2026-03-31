# TaktX Shared

![Coverage](../badges/taktx-shared-coverage.svg)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache%202.0-green.svg)](LICENSE)

Shared library for TaktX BPM Engine containing common models, utilities, and BPMN definitions used across all TaktX modules.

## Overview

This module provides:
- **BPMN Models**: Generated JAXB classes from BPMN 2.0 schema
- **Shared DTOs**: Common data transfer objects
- **Utilities**: Helper classes and common functionality
- **Message Models**: Kafka message definitions

## Installation

### Maven

```xml
<dependency>
    <groupId>io.taktx</groupId>
    <artifactId>taktx-shared</artifactId>
    <version>0.4.0-beta-1</version>
</dependency>
```

### Gradle

```kotlin
implementation("io.taktx:taktx-shared:0.4.0-beta-1")
```

## Components

### BPMN Models
Generated from the BPMN 2.0 XSD schema, providing type-safe access to BPMN elements.

### Message Models
Kafka message definitions for communication between engine components.

### Utilities
Common helper classes used across the TaktX ecosystem.

## License

Licensed under the Apache License 2.0. See [LICENSE](LICENSE) for details.

