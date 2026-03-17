# TaktX Client

![Coverage](../badges/taktx-client-coverage.svg)

The plain Java `taktx-client` library is used by:

- worker applications consuming external-task and user-task triggers
- platform-side components sending authorized commands
- bootstrap/ops utilities publishing signing keys or licenses

## Installation

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

## Required client properties

At minimum, provide:

```properties
bootstrap.servers=localhost:9092
kafka.bootstrap.servers=localhost:9092
taktx.engine.tenant-id=acme
taktx.engine.namespace=default
```

`tenant-id` and `namespace` are used to derive all topic names.

## Basic usage

```java
Properties props = new Properties();
props.put("bootstrap.servers", "localhost:9092");
props.put("kafka.bootstrap.servers", "localhost:9092");
props.put("taktx.engine.tenant-id", "acme");
props.put("taktx.engine.namespace", "default");

TaktXClient client = TaktXClient.newClientBuilder().withProperties(props).build();
client.start();

UUID instanceId = client.startProcess("invoice-process", -1, VariablesDTO.empty(), null);
```

## Worker signing

Workers still use explicit key material.
If a worker should sign responses, provide:

```bash
export TAKTX_SIGNING_PRIVATE_KEY=<base64-pkcs8-ed25519-private-key>
export TAKTX_SIGNING_PUBLIC_KEY=<base64-x509-ed25519-public-key>
export TAKTX_SIGNING_KEY_ID=worker-billing-2026-001
```

Or use mounted files:

```bash
export TAKTX_SIGNING_IDENTITY_SOURCE=file
export TAKTX_SIGNING_FILE_KEY_ID_PATH=/opt/taktx/signing/worker/key-id
export TAKTX_SIGNING_FILE_PRIVATE_KEY_PATH=/opt/taktx/signing/worker/private-key.b64
export TAKTX_SIGNING_FILE_PUBLIC_KEY_PATH=/opt/taktx/signing/worker/public-key.b64
```

When present:

- the client signs outgoing worker responses
- `client.start()` automatically publishes the worker public key to `taktx-signing-keys`

If you manage publication separately, the public key may be omitted and only private key + key ID are required.

## Swapping key sources by environment

The client builder now accepts a pluggable signing identity source:

```java
TaktXClient client =
    TaktXClient.newClientBuilder()
        .withProperties(props)
        .withSigningIdentitySource(new EnvironmentWorkerSigningIdentitySource())
        .build();
```

Built-in options currently include:

- `EnvironmentWorkerSigningIdentitySource` — reads current process env/system properties
- `FileSigningIdentitySource` — reads mounted files and picks up later file replacements
- `StaticSigningIdentitySource` — explicit injected/test identity
- `GeneratedSigningIdentitySource` — generated once and then kept stable

This separates *where key material comes from* from the actual signing/publishing flow, so
environment-specific implementations can be introduced without changing the client itself.

## Publishing signing keys

### Worker / Ed25519

```java
client.publishSigningKey("worker-billing-2026-001", workerPublicKeyBase64, "worker");
```

### Platform / Ingester / RSA

Publish the RSA public key under the **same `kid`** that will appear in issued JWTs:

```java
TaktXClient.publishSigningKey(
    props,
    "platform-key-2026-03",
    rsaPublicKeyBase64,
    "platform",
    "RSA");
```

This is important: the engine resolves JWT validation keys by JWT `kid`, not by a fixed key name such as `platform`.

## Sending authorized commands

Platform-facing callers can attach a JWT directly when starting or aborting:

```java
String jwt = obtainJwtFromPlatformService();
client.startProcess("invoice-process", -1, VariablesDTO.empty(), jwt);
```

### JWT requirements for engine authorization

If engine-side `authorizationEnabled=true` is active in the configuration topic, the JWT must:

- have a `kid` header matching a published RSA public key in `taktx-signing-keys`
- include a valid `exp`
- include a unique `auditId`
- include matching action/command claims

Engine behaviour is now fixed:

- expired JWTs are always rejected
- replayed `auditId` values are always rejected

## Verifying engine-signed inbound records

The client automatically initializes a `SigningKeysStore` on `start()` and uses it to verify signed inbound records when a matching deserializer supports signature validation.

If you want your consumer to reject **unsigned** inbound records as well, set a local property:

```properties
taktx.security.signing.enabled=true
```

This is a **client-local strict verification flag**. It is not driven automatically from the engine configuration topic.

The client now also watches `taktx-configuration` at runtime. When the latest engine config toggles
`signingEnabled`, already-running client consumers react without needing a restart. The local
property above remains a manual override that can force strict rejection even when runtime config is
currently off.

## Publishing licenses

Platform/ops utilities can publish license text directly:

```java
client.publishLicense(licenseText);
```

## What changed for platform / ingester users

Compared to the old engine model:

1. do **not** configure or expect an engine `signingKeyId`
2. do **not** configure engine signing private/public keys
3. publish your RSA public key under the JWT `kid`
4. always issue JWTs with `exp` and unique `auditId`
5. treat engine signing/authorization enablement as runtime config-topic state, not env vars
