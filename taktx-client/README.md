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

If engine-side command authorization is enabled, the same start/abort APIs can attach JWTs either:

- explicitly per call
- automatically via a configured `AuthorizationTokenProvider`

## Worker signing

Workers still use explicit key material.
If a worker should sign responses, provide:

```bash
export TAKTX_SIGNING_PRIVATE_KEY=<base64-pkcs8-ed25519-private-key>
export TAKTX_SIGNING_PUBLIC_KEY=<base64-x509-ed25519-public-key>
export TAKTX_SIGNING_KEY_ID=worker-billing-2026-001

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

JWT attachment is currently used for entry commands only:

- `startProcess(...)`
- `abortElementInstance(...)`

Worker responses are **not** sent with JWTs by default. They use Ed25519 signing when worker signing is configured and runtime `signingEnabled=true`.

## Inspecting trust metadata on instance updates

`InstanceUpdateDTO` now exposes two trust/provenance fields:

- `getCurrentTrustMetadata()` — trust data for the command currently being processed
- `getOriginTrustMetadata()` — trust data for the original command that started the chain

This is useful when engine-internal work is triggered by an earlier external command. For example:

- a worker response can produce `current=worker`, `origin=worker`
- a later timer wake-up caused by that response can produce `current=engine`, `origin=worker`

That makes it easier for console/logging consumers to show both the current actor and the original
initiator without conflating the two.

### Automatic service-account JWT retrieval

Machine-to-machine callers can configure a client-wide token provider so start/abort commands automatically obtain a JWT when no explicit token is passed.

#### Explicit provider injection

```java
AuthorizationTokenProvider tokenProvider = request -> fetchJwtFor(request.scope());

TaktXClient client =
    TaktXClient.newClientBuilder()
        .withProperties(props)
        .withAuthorizationTokenProvider(tokenProvider)
        .build();
```

The request object includes command context such as:

- command scope (`START_PROCESS`, `ABORT_PROCESS_INSTANCE`)
- process definition id/version for start commands
- process instance id / element path for abort commands

#### Built-in OpenID client-credentials provider

The client can also auto-configure a built-in OpenID/OAuth2 client-credentials provider from properties.

Required properties:

| Property | Meaning |
|---|---|
| `taktx.authorization.token-provider` | Set to `openid-client-credentials` |
| `taktx.authorization.openid.token-endpoint` | OpenID/OAuth2 token endpoint URL |
| `taktx.authorization.openid.client-id` | Service-account / client ID |
| `taktx.authorization.openid.client-secret` | Service-account / client secret |

Optional properties:

| Property | Meaning |
|---|---|
| `taktx.authorization.openid.scope` | Optional OAuth2 scope string |
| `taktx.authorization.openid.audience` | Optional audience parameter for providers that support it |
| `taktx.authorization.openid.client-auth-method` | `client_secret_basic` (default) or `client_secret_post` |
| `taktx.authorization.openid.connect-timeout-ms` | HTTP connect timeout in milliseconds |
| `taktx.authorization.openid.request-timeout-ms` | HTTP request timeout in milliseconds |
| `taktx.authorization.openid.refresh-skew-ms` | Refresh token this many milliseconds before expiry |

Example:

```properties
taktx.authorization.token-provider=openid-client-credentials
taktx.authorization.openid.token-endpoint=https://issuer.example.com/oauth/token
taktx.authorization.openid.client-id=taktx-service-account
taktx.authorization.openid.client-secret=super-secret
taktx.authorization.openid.scope=taktx.start taktx.abort
taktx.authorization.openid.audience=taktx-engine
taktx.authorization.openid.client-auth-method=client_secret_basic
```

When configured, `TaktXClient` automatically:

- fetches an access token using the client-credentials grant
- caches it until close to expiry
- refreshes it automatically for later start/abort commands

### JWT requirements for engine authorization

If engine-side `engineRequiresAuthorization=true` is active in the configuration topic, JWT is required for start/abort entry commands. The JWT must:

- have a `kid` header matching a published RSA public key in `taktx-signing-keys`
- include a valid `exp`
- include a unique `auditId`
- include matching action/command claims

The engine uses the JWT `kid` only to look up the RSA public key from `taktx-signing-keys`, then verifies the JWT signature with that key.

Engine behaviour is now fixed:

- expired JWTs are always rejected
- replayed `auditId` values are always rejected

### Non-entry process-instance commands

Other `ProcessInstanceTriggerDTO` subclasses do not automatically require JWTs.

- if engine runtime `signingEnabled=true`, signed worker/internal commands are verified and unsigned ones are rejected
- if engine runtime `signingEnabled=false`, signed and unsigned non-entry commands are both accepted

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
