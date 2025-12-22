# TaktX Client Spring

![Coverage](../badges/taktx-client-spring-coverage.svg)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache%202.0-green.svg)](LICENSE)

This module provides Spring Boot convenience beans for the TaktX plain Java client.

It provides auto-configuration that creates a `io.taktx.client.TaktXClient` bean using Spring Boot configuration properties:

- `taktx.namespace` - the namespace used for topics
- `kafka.bootstrap.servers` - bootstrap servers for Kafka client

## Installation

### Maven

```xml
<dependency>
    <groupId>io.taktx</groupId>
    <artifactId>taktx-client-spring3</artifactId>
    <version>0.0.9-alpha-3-SNAPSHOT</version>
</dependency>
```

### Gradle

```kotlin
implementation("io.taktx:taktx-client-spring3:0.0.9-alpha-3-SNAPSHOT")
```

## Configuration

Add to your `application.properties` or `application.yml`:

```properties
taktx.namespace=your-namespace
kafka.bootstrap.servers=localhost:9092
```

Or in YAML format:

```yaml
taktx:
  namespace: your-namespace
kafka:
  bootstrap:
    servers: localhost:9092
```

## Usage

Inject the client in your Spring application:

```java
@Component
public class ProcessService {
    
    @Autowired
    private TaktXClient taktxClient;

    public void processExample() {
        ProcessInstance instance = taktxClient.startProcess("process-key")
            .variable("data", "value")
            .start();
    }
}
```

## Custom Configuration

You can customize the TaktXClient by providing your own bean:

```java
@Configuration
public class TaktXConfiguration {
    
    @Bean
    public TaktXClient customTaktXClient(TaktPropertiesHelper propertiesHelper) {
        return TaktXClient.newClientBuilder()
            .withProperties(propertiesHelper.getProperties())
            .withCustomConfiguration()
            .build();
    }
}
```

## License

Licensed under the Apache License 2.0. See [LICENSE](LICENSE) for details.

