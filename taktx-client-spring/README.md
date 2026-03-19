# TaktX Client Spring

![Coverage](../badges/taktx-client-spring-coverage.svg)

This module provides Spring Boot auto-configuration for the plain Java `taktx-client`.
A singleton `TaktXClient` bean is created and started automatically when `taktx.client.enabled=true`.

In normal Spring Boot usage, you do **not** need to manually call:

- `TaktXClient.newClientBuilder()`
- `client.start()`
- `client.deployTaktDeploymentAnnotatedClasses()`

The auto-configuration does that for you.

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

### Initialization notes

When `taktx.client.enabled=true` (the default), Spring auto-configuration:

1. builds the underlying `TaktXClient` from Spring application properties
2. calls `start()`
3. deploys `@Deployment`-annotated resources
4. auto-registers discovered external-task consumers
5. optionally registers instance-update forwarding when enabled

So for the standard case, injecting `TaktXClient` is enough.

## Authorized commands / service accounts

The Spring wrapper passes your Spring application properties into the plain `TaktXClient` builder.
That means the property-based service-account / OpenID client-credentials support from `taktx-client` works here too.

For start/abort command authorization, configure the same OpenID properties as in the plain client:

```properties
taktx.authorization.token-provider=openid-client-credentials
taktx.authorization.openid.token-endpoint=https://issuer.example.com/oauth/token
taktx.authorization.openid.client-id=taktx-service-account
taktx.authorization.openid.client-secret=super-secret
taktx.authorization.openid.scope=taktx.start taktx.abort
taktx.authorization.openid.audience=taktx-engine
taktx.authorization.openid.client-auth-method=client_secret_basic
```

With that configuration in place, injected `TaktXClient` instances will automatically fetch and attach JWTs for entry commands such as:

- `startProcess(...)`
- `abortElementInstance(...)`

Non-entry worker/internal commands are still handled separately from JWTs, following the engine signing rules.

### Custom `AuthorizationTokenProvider`

The Spring wrapper currently does **not** auto-wire a Spring bean of type `AuthorizationTokenProvider` into the generated `TaktXClient`.

If you need a custom provider implementation instead of the built-in property-driven OpenID support, disable the auto-configured client and create your own bean explicitly:

```properties
taktx.client.enabled=false
```

Then build your own `TaktXClient` with:

- `withProperties(...)`
- `withAuthorizationTokenProvider(...)`

This custom path is only needed for bespoke provider logic. For standard OpenID client-credentials flows, the Spring wrapper already has everything needed.

## Worker signing

If the Spring app is acting as a worker and should sign responses:

```bash
export TAKTX_SIGNING_PRIVATE_KEY=<base64-pkcs8-ed25519-private-key>
export TAKTX_SIGNING_PUBLIC_KEY=<base64-x509-ed25519-public-key>
export TAKTX_SIGNING_KEY_ID=my-worker-key-1

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
