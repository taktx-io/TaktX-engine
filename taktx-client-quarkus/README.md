# TaktX Client Quarkus

![Coverage](../badges/taktx-client-quarkus-coverage.svg)

This module provides Quarkus convenience wiring for the plain Java `taktx-client`.
It produces a singleton `TaktXClient` from MicroProfile/Quarkus configuration and starts it at application startup.

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
taktx.client.groupId.instanceupdate=my-instance-update-group
taktx.engine.topic.partitions=3
taktx.engine.topic.replicationFactor=1
```

## Usage

```java
import io.taktx.client.TaktXClient;
import jakarta.inject.Inject;

class MyBean {
  @Inject TaktXClient taktxClient;
}
```

## Worker signing

If this Quarkus app is acting as a worker and should sign responses, provide worker key material:

```bash
export TAKTX_SIGNING_PRIVATE_KEY=<base64-pkcs8-ed25519-private-key>
export TAKTX_SIGNING_PUBLIC_KEY=<base64-x509-ed25519-public-key>
export TAKTX_SIGNING_KEY_ID=my-worker-key-1
```

Or switch to file-backed signing for mounted secrets:

```bash
export TAKTX_SIGNING_IDENTITY_SOURCE=file
export TAKTX_SIGNING_FILE_KEY_ID_PATH=/opt/taktx/signing/worker/key-id
export TAKTX_SIGNING_FILE_PRIVATE_KEY_PATH=/opt/taktx/signing/worker/private-key.b64
export TAKTX_SIGNING_FILE_PUBLIC_KEY_PATH=/opt/taktx/signing/worker/public-key.b64
```

The client will then:

- sign outgoing worker responses
- auto-publish the worker public key on startup

## Consuming signed engine records

If you want locally strict rejection of unsigned inbound records, add:

```properties
taktx.security.signing.enabled=true
```

This is a local consumer-side enforcement flag.
It does **not** mean the engine is enabled for signing; engine signing is controlled at runtime via the `taktx-configuration` topic.

The underlying `TaktXClient` now watches the configuration topic too, so when the runtime engine
config flips `signingEnabled`, already-running consumers adapt without a Quarkus restart.

## Platform / ingester note

If you use the plain `TaktXClient` APIs from a Quarkus service to publish JWT verification keys:

- publish the RSA public key under the same JWT `kid`
- use the algorithm-aware overload with `"RSA"`
- do not expect any fixed engine `signingKeyId`
