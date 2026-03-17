# TaktX Client Spring

![Coverage](../badges/taktx-client-spring-coverage.svg)

This module provides Spring Boot auto-configuration for the plain Java `taktx-client`.
A singleton `TaktXClient` bean is created and started automatically when `taktx.client.enabled=true`.

## Installation

### Maven

```xml
<dependency>
  <groupId>io.taktx</groupId>
  <artifactId>taktx-client-spring</artifactId>
  <version>0.0.9-alpha-3-SNAPSHOT</version>
</dependency>
```

### Gradle

```kotlin
implementation("io.taktx:taktx-client-spring:0.0.9-alpha-3-SNAPSHOT")
```

## Required configuration

```properties
bootstrap.servers=localhost:9092
kafka.bootstrap.servers=localhost:9092
taktx.engine.tenant-id=acme
taktx.engine.namespace=default
```

Optional:

```properties
taktx.client.enabled=true
taktx.client.instanceupdate.enabled=true
taktx.client.groupId.instanceupdate=my-instance-update-group
taktx.engine.topic.partitions=3
taktx.engine.topic.replicationFactor=1
```

## Usage

```java
import io.taktx.client.TaktXClient;
import org.springframework.stereotype.Service;

@Service
public class ProcessService {
  private final TaktXClient taktxClient;

  public ProcessService(TaktXClient taktxClient) {
    this.taktxClient = taktxClient;
  }
}
```

## Worker signing

If the Spring app is acting as a worker and should sign responses:

```bash
export TAKTX_SIGNING_PRIVATE_KEY=<base64-pkcs8-ed25519-private-key>
export TAKTX_SIGNING_PUBLIC_KEY=<base64-x509-ed25519-public-key>
export TAKTX_SIGNING_KEY_ID=my-worker-key-1
```

Or use file-backed signing with mounted secrets:

```bash
export TAKTX_SIGNING_IDENTITY_SOURCE=file
export TAKTX_SIGNING_FILE_KEY_ID_PATH=/opt/taktx/signing/worker/key-id
export TAKTX_SIGNING_FILE_PRIVATE_KEY_PATH=/opt/taktx/signing/worker/private-key.b64
export TAKTX_SIGNING_FILE_PUBLIC_KEY_PATH=/opt/taktx/signing/worker/public-key.b64
```

The client will sign worker responses and auto-publish the worker public key during startup.

## Consuming signed engine records

If this client should reject unsigned inbound records locally, set:

```properties
taktx.security.signing.enabled=true
```

This is a local consumer rule, not an engine runtime toggle.
Engine signing is enabled/disabled through the `taktx-configuration` topic.

The underlying `TaktXClient` now watches that configuration topic as well, so already-running Spring
clients adapt when `signingEnabled` changes at runtime without requiring an application restart.

## Platform / ingester note

If a Spring-based ingester/platform component publishes JWT verification keys:

- publish the RSA public key under the JWT `kid`
- use the algorithm-aware `publishSigningKey(..., "RSA")` overload
- stop assuming there is any configured engine `signingKeyId`
