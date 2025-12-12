# TaktX Client Quarkus

![Coverage](https://raw.githubusercontent.com/taktx-io/TaktX-engine/main/badges/taktx-client-quarkus-coverage.svg)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache%202.0-green.svg)](LICENSE)

This module provides Quarkus convenience beans for the TaktX plain Java client.

It exposes a CDI producer `io.taktx.client.quarkus.QuarkusTaktXClientProducer` that creates a
`io.taktx.client.TaktXClient` using MicroProfile Config properties:

- `taktx.namespace` - the namespace used for topics
- `kafka.bootstrap.servers` - bootstrap servers for Kafka client

## Installation

### Maven

```xml
<dependency>
    <groupId>io.taktx</groupId>
    <artifactId>taktx-client-quarkus</artifactId>
    <version>0.0.9-alpha-3-SNAPSHOT</version>
</dependency>
```

### Gradle

```kotlin
implementation("io.taktx:taktx-client-quarkus:0.0.9-alpha-3-SNAPSHOT")
```

## Configuration

Add to your `application.properties`:

```properties
taktx.namespace=your-namespace
kafka.bootstrap.servers=localhost:9092
```

## Usage

Inject the client in your Quarkus application:

```java
@Inject
TaktXClient taktxClient;

public void processExample() {
    ProcessInstance instance = taktxClient.startProcess("process-key")
        .variable("data", "value")
        .start();
}
```

## License

Licensed under the Apache License 2.0. See [LICENSE](LICENSE) for details.

---

## Implementation
package io.taktx.client.quarkus;

import io.taktx.client.TaktXClient;
import io.taktx.util.TaktPropertiesHelper;
import java.util.Properties;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * A minimal CDI producer that creates a TaktXClient.Builder using MicroProfile config.
 * This allows Quarkus applications to inject a ready-to-use TaktXClient.
 */
@ApplicationScoped
public class QuarkusTaktXClientProducer {

  @ConfigProperty(name = "taktx.namespace")
  String namespace;

  @ConfigProperty(name = "kafka.bootstrap.servers")
  String kafkaBootstrap;

  @Produces
  public TaktXClient produceTaktXClient() {
    Properties props = new Properties();
    props.put("bootstrap.servers", kafkaBootstrap);

    TaktXClient.TaktXClientBuilder builder = TaktXClient.newClientBuilder().withNamespace(namespace).withKafkaProperties(props);
    return builder.build();
  }
}

