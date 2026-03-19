# TaktX Client Quarkus

![Coverage](../badges/taktx-client-quarkus-coverage.svg)

This module provides Quarkus convenience wiring for the plain Java `taktx-client`.
It produces a singleton `TaktXClient` from MicroProfile/Quarkus configuration and starts it at application startup.

In other words: when you inject `TaktXClient`, you do **not** need to manually call:

- `TaktXClient.newClientBuilder()`
- `client.start()`
- `client.deployTaktDeploymentAnnotatedClasses()`

The Quarkus wrapper does that for you during application startup.

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

### Initialization notes

At startup, the Quarkus wrapper:

1. copies all available Quarkus / MicroProfile configuration into the plain client properties
2. builds the underlying `TaktXClient`
3. calls `start()`
4. deploys `@Deployment`-annotated resources
5. auto-registers discovered external-task consumers

So if you only need the standard client behavior, injecting `TaktXClient` is enough.

## Authorized commands / service accounts

This wrapper passes your application properties straight into the underlying plain `TaktXClient` builder.
That means the property-based service-account / OpenID client-credentials support from `taktx-client` works here too.

For start/abort command authorization, you can configure the same properties as in the plain client:

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

The Quarkus wrapper currently does **not** auto-wire a CDI bean of type `AuthorizationTokenProvider` into the generated `TaktXClient`.

If you need a custom provider implementation instead of the built-in property-driven OpenID support, disable the wrapper-managed client and create your own `TaktXClient` explicitly:

```properties
taktx.client.enabled=false
```

Then construct the client yourself with:

- `withProperties(...)`
- `withAuthorizationTokenProvider(...)`

This is only needed for custom provider logic. For standard OpenID client-credentials flows, the Quarkus wrapper already has everything needed.

## Worker signing

If this Quarkus app is acting as a worker and should sign responses, provide worker key material:

```bash
export TAKTX_SIGNING_PRIVATE_KEY=<base64-pkcs8-ed25519-private-key>
export TAKTX_SIGNING_PUBLIC_KEY=<base64-x509-ed25519-public-key>
export TAKTX_SIGNING_KEY_ID=my-worker-key-1

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
