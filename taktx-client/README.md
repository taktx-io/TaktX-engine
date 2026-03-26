# TaktX Client

![Coverage](../badges/taktx-client-coverage.svg)

The plain Java `taktx-client` library is the core client for interacting with a TaktX BPMN engine over Kafka. It is framework-agnostic and works in any JVM environment. Framework-specific wrappers with zero-boilerplate auto-configuration are available:

- [`taktx-client-spring`](../taktx-client-spring/README.md) — Spring Boot auto-configuration
- [`taktx-client-quarkus`](../taktx-client-quarkus/README.md) — Quarkus / CDI wiring

---

## Contents

1. [Installation](#installation)
2. [Minimum configuration](#minimum-configuration)
3. [Quick start](#quick-start)
4. [Process lifecycle](#process-lifecycle)
5. [Deploying process definitions](#deploying-process-definitions)
6. [External task workers](#external-task-workers)
7. [User task workers](#user-task-workers)
8. [Instance update consumers](#instance-update-consumers)
9. [Message events and signals](#message-events-and-signals)
10. [Worker signing](#worker-signing)
11. [Root trust chain — anchored mode](#root-trust-chain--anchored-mode)
12. [Command authorization (JWT)](#command-authorization-jwt)
13. [Publishing signing keys](#publishing-signing-keys)
14. [Verifying inbound signed records](#verifying-inbound-signed-records)
15. [Publishing runtime configuration](#publishing-runtime-configuration)
16. [Trust metadata on instance updates](#trust-metadata-on-instance-updates)
17. [Configuration reference](#configuration-reference)

---

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

---

## Minimum configuration

```properties
bootstrap.servers=localhost:9092
kafka.bootstrap.servers=localhost:9092
taktx.engine.tenant-id=acme
taktx.engine.namespace=default
```

`tenant-id` and `namespace` are combined as a prefix for all topic names:
`<tenant-id>.<namespace>.taktx-signing-keys`, etc.

---

## Quick start

```java
Properties props = new Properties();
props.put("bootstrap.servers", "localhost:9092");
props.put("kafka.bootstrap.servers", "localhost:9092");
props.put("taktx.engine.tenant-id", "acme");
props.put("taktx.engine.namespace", "default");

TaktXClient client = TaktXClient.newClientBuilder()
    .withProperties(props)
    .build();

client.start();   // initialises stores, publishes worker key if configured, starts consumers

UUID instanceId = client.startProcess("invoice-process", -1, VariablesDTO.empty(), null);
```

`client.stop()` releases all consumers and background threads. Always call it on shutdown.

---

## Process lifecycle

### Starting a process instance

```java
// Latest version
UUID id = client.startProcess("invoice-process", VariablesDTO.of("amount", 1200));

// Specific version
UUID id = client.startProcess("invoice-process", 3, VariablesDTO.of("amount", 1200));

// With an explicit JWT (when engineRequiresAuthorization=true)
UUID id = client.startProcess("invoice-process", -1, VariablesDTO.empty(), jwt);
```

### Aborting a process instance

```java
client.abortElementInstance(instanceId, elementPath);
```

---

## Deploying process definitions

### From a classpath resource

Annotate a class with `@Deployment`:

```java
@Deployment(resources = {"processes/invoice.bpmn", "processes/approval.bpmn"})
public class InvoiceDeployment {}
```

`client.deployTaktDeploymentAnnotatedClasses()` is called automatically by the framework wrappers, or explicitly by your code after `client.start()`.

### Programmatically

```java
try (InputStream is = getClass().getResourceAsStream("/processes/invoice.bpmn")) {
    ParsedDefinitionsDTO result = client.deployProcessDefinition(is);
}
```

---

## External task workers

### Annotation-based (recommended)

```java
@ExternalTask("invoice-payment")
public class InvoicePaymentWorker {

    @Execute
    public ExternalTaskResult process(ExternalTaskTriggerDTO trigger) {
        String invoiceId = trigger.getVariables().get("invoiceId");
        // … do work …
        return ExternalTaskResult.complete(VariablesDTO.of("paid", true));
    }
}
```

Register all workers with:

```java
client.registerExternalTaskConsumer(
    new AnnotationScanningExternalTaskTriggerConsumer(...),
    "my-worker-group");
```

### Imperative

```java
client.registerExternalTaskConsumer(triggers -> {
    for (ExternalTaskTriggerDTO trigger : triggers) {
        ExternalTaskInstanceResponder responder = client.respondToExternalTask(trigger);
        try {
            // … do work …
            responder.complete(VariablesDTO.of("result", "ok"));
        } catch (Exception e) {
            responder.fail("Processing error: " + e.getMessage());
        }
    }
}, "my-worker-group");
```

### Requesting a task topic

The engine manages topic creation for external tasks. Request a topic before registering:

```java
String topicName = client.requestExternalTaskTopic("invoice-payment");
// or with explicit settings:
String topicName = client.requestExternalTaskTopic("invoice-payment", 3, CleanupPolicy.COMPACT, (short) 1);
```

---

## User task workers

```java
client.registerUserTaskConsumer(triggers -> {
    for (UserTaskTriggerDTO trigger : triggers) {
        // Present to a human via your UI …
        UserTaskInstanceResponder responder = client.respondToUserTask(trigger);
        responder.complete(VariablesDTO.of("approved", true));
    }
}, "my-user-task-group");
```

---

## Instance update consumers

```java
client.registerInstanceUpdateConsumer("my-monitor-group", records -> {
    for (InstanceUpdateRecord record : records) {
        System.out.printf("Instance %s → %s%n",
            record.getUpdate().getProcessInstanceId(),
            record.getUpdate().getStatus());
    }
});
```

`InstanceUpdateRecord` carries both `currentTrustMetadata` and `originTrustMetadata` — see [Trust metadata on instance updates](#trust-metadata-on-instance-updates).

---

## Message events and signals

```java
// Correlate a BPMN message event
client.sendMessage(MessageEventDTO.builder()
    .messageName("PaymentReceived")
    .correlationKey("INV-001")
    .variables(VariablesDTO.of("amount", 1200))
    .build());

// Broadcast a signal
client.sendSignal("GlobalShutdown");
```

---

## Worker signing

Workers can sign their responses with an Ed25519 key. The engine verifies signatures when `signingEnabled=true` in the runtime configuration topic.

### Source 1 — Environment variables (default)

```bash
export TAKTX_SIGNING_KEY_ID=billing-worker-2026-001
export TAKTX_SIGNING_PRIVATE_KEY=<base64 PKCS#8 DER Ed25519 private key>
export TAKTX_SIGNING_PUBLIC_KEY=<base64 X.509 DER Ed25519 public key>
```

Or as properties:

```properties
taktx.signing.key-id=billing-worker-2026-001
taktx.signing.private-key=<base64 private key>
taktx.signing.public-key=<base64 public key>
```

### Source 2 — Mounted key files (live rotation)

```bash
export TAKTX_SIGNING_IDENTITY_SOURCE=file
export TAKTX_SIGNING_FILE_KEY_ID_PATH=/opt/taktx/signing/worker/key-id
export TAKTX_SIGNING_FILE_PRIVATE_KEY_PATH=/opt/taktx/signing/worker/private-key.b64
export TAKTX_SIGNING_FILE_PUBLIC_KEY_PATH=/opt/taktx/signing/worker/public-key.b64
# Optional: how often to re-check files (default 1000 ms)
export TAKTX_SIGNING_FILE_REFRESH_INTERVAL_MS=1000
```

Write key updates atomically: `cp new-key.b64 private-key.b64.tmp && mv private-key.b64.tmp private-key.b64`.

### Source 3 — Generated (development / testing)

```bash
export TAKTX_SIGNING_IDENTITY_SOURCE=generated
```

Generates a stable in-memory key for the lifetime of the process. Key is lost on restart.

### Pluggable source via builder

```java
TaktXClient client = TaktXClient.newClientBuilder()
    .withProperties(props)
    .withSigningIdentitySource(new FileSigningIdentitySource(keyIdPath, privatePath, publicPath))
    .build();
```

Built-in sources:

| Class | Description |
|---|---|
| `EnvironmentWorkerSigningIdentitySource` | Reads env vars / system properties (default) |
| `FileSigningIdentitySource` | Reads and hot-reloads mounted files |
| `GeneratedSigningIdentitySource` | Generates once and holds stable |
| `StaticSigningIdentitySource` | Explicit injection for tests |

### Generating Ed25519 key files

```bash
openssl genpkey -algorithm Ed25519 -out /tmp/worker-key.pem

openssl pkey -in /tmp/worker-key.pem -outform DER \
  | base64 | tr -d '\n' > private-key.b64

openssl pkey -in /tmp/worker-key.pem -pubout -outform DER \
  | base64 | tr -d '\n' > public-key.b64

echo "billing-worker-2026-001" > key-id
rm /tmp/worker-key.pem
```

### Behaviour on `start()`

When a signing identity is available, `client.start()` automatically publishes the worker's public key to `taktx-signing-keys`. The client re-publishes whenever the key changes (live rotation). If `TAKTX_SIGNING_PUBLIC_KEY` is absent, only private-key signing is active and no auto-publication occurs.

---

## Root trust chain — anchored mode

When the engine is configured with `TAKTX_PLATFORM_PUBLIC_KEY`, it operates in **anchored mode**: every key in `taktx-signing-keys` must carry a countersignature from the platform root RSA private key. Workers must supply their registration signature.

### Setting the registration signature

```bash
export TAKTX_SIGNING_REGISTRATION_SIGNATURE=<base64 RSA/SHA-256 countersignature>
```

Or as a property:

```properties
taktx.signing.registration-signature=<base64 countersignature>
```

Or via the builder:

```java
TaktXClient client = TaktXClient.newClientBuilder()
    .withProperties(props)
    .withSigningRegistrationSignature(registrationSignature)
    .build();
```

### Generating the signature

```bash
scripts/generate_trust_anchor.sh --sign \
  --key-dir /path/to/worker/keys \
  --owner billing-worker \
  --role CLIENT
# Prints: TAKTX_SIGNING_REGISTRATION_SIGNATURE=<value>
```

The canonical payload format is: `keyId|publicKeyBase64|Ed25519|owner|CLIENT`

See [docs/security.md](../docs/security.md) for the complete anchored mode reference.

---

## Command authorization (JWT)

When `engineRequiresAuthorization=true` in the engine runtime configuration, entry commands (`startProcess`, `abortElementInstance`) require a JWT.

### Attaching a JWT explicitly

```java
String jwt = myAuthService.getToken();
client.startProcess("invoice-process", -1, VariablesDTO.empty(), jwt);
```

### Automatic JWT via OpenID client-credentials

Configure in properties and the client fetches, caches, and refreshes tokens automatically:

```properties
taktx.authorization.token-provider=openid-client-credentials
taktx.authorization.openid.token-endpoint=https://issuer.example.com/oauth/token
taktx.authorization.openid.client-id=taktx-service-account
taktx.authorization.openid.client-secret=super-secret
taktx.authorization.openid.scope=taktx.start taktx.abort
taktx.authorization.openid.audience=taktx-engine
taktx.authorization.openid.client-auth-method=client_secret_basic
```

| Property | Description | Default |
|---|---|---|
| `taktx.authorization.token-provider` | Set to `openid-client-credentials` to enable | — |
| `taktx.authorization.openid.token-endpoint` | OAuth2 token endpoint URL | — |
| `taktx.authorization.openid.client-id` | Client ID | — |
| `taktx.authorization.openid.client-secret` | Client secret | — |
| `taktx.authorization.openid.scope` | Optional scope string | — |
| `taktx.authorization.openid.audience` | Optional audience parameter | — |
| `taktx.authorization.openid.client-auth-method` | `client_secret_basic` or `client_secret_post` | `client_secret_basic` |
| `taktx.authorization.openid.connect-timeout-ms` | HTTP connect timeout | `3000` |
| `taktx.authorization.openid.request-timeout-ms` | HTTP request timeout | `5000` |
| `taktx.authorization.openid.refresh-skew-ms` | Refresh tokens this many ms before expiry | `30000` |

### Custom `AuthorizationTokenProvider`

```java
TaktXClient client = TaktXClient.newClientBuilder()
    .withProperties(props)
    .withAuthorizationTokenProvider(request -> myVault.fetchToken(request.getScope()))
    .build();
```

### JWT requirements

| Claim / header | Requirement |
|---|---|
| `kid` header | Must match a published RSA key in `taktx-signing-keys` |
| `exp` | Must be in the future — always enforced |
| `auditId` | Must be unique — always enforced (replay protection) |
| Algorithm | RS256 |

---

## Publishing signing keys

### Ed25519 worker or engine key

```java
// Community mode (no countersignature needed)
client.publishSigningKey("billing-worker-1", publicKeyBase64, "billing-worker");

// With explicit role
client.publishSigningKey("billing-worker-1", publicKeyBase64, "billing-worker",
    "Ed25519", KeyRole.CLIENT);

// Anchored mode (countersignature required)
client.publishSigningKey("billing-worker-1", publicKeyBase64, "billing-worker",
    "Ed25519", KeyRole.CLIENT, registrationSignature);
```

### RSA JWT verification key (platform / ingester)

```java
// Community mode
TaktXClient.publishSigningKey(props,
    "platform-key-2026-03",  // must match JWT kid exactly
    rsaPublicKeyBase64,
    "platform",
    "RSA");

// Anchored mode
TaktXClient.publishSigningKey(props,
    "platform-key-2026-03",
    rsaPublicKeyBase64,
    "platform",
    "RSA",
    KeyRole.PLATFORM,
    platformKeyRegistrationSignature);
```

The static overload does not require a running client — useful in bootstrap and test code.

---

## Verifying inbound signed records

When `client.start()` is called, a `SigningKeysStore` is initialised that reads `taktx-signing-keys` to end-of-topic before consuming any trigger topics. This ensures the engine's public key is present before the first trigger record arrives.

To additionally enforce that **unsigned** inbound records are rejected client-side:

```properties
taktx.security.signing.enabled=true
```

This is a **local client-side flag** — it does not toggle engine signing, which is controlled via the `taktx-configuration` topic.

The client watches the configuration topic at runtime. When `signingEnabled` changes in the engine's published config, already-running consumers adapt without a restart.

---

## Publishing runtime configuration

```java
// Enable signing and authorization
client.publishGlobalConfig(GlobalConfigurationDTO.builder()
    .signingEnabled(true)
    .engineRequiresAuthorization(true)
    .build());

// Static overload — no running client needed
TaktXClient.publishGlobalConfig(props, GlobalConfigurationDTO.builder()
    .signingEnabled(true)
    .build());
```

### Publishing a license

```java
client.publishLicense(licenseText);  // raw License3j-signed text
```

---

## Trust metadata on instance updates

`InstanceUpdateRecord.getUpdate()` exposes:

```java
TrustMetadata current = update.getCurrentTrustMetadata();  // who acted now
TrustMetadata origin  = update.getOriginTrustMetadata();   // who started the chain
```

| Scenario | `current` | `origin` |
|---|---|---|
| External JWT start | JWT | JWT |
| Worker external task response | worker | worker |
| Timer continuation after worker response | engine | worker |
| Engine follow-up after JWT start | engine | JWT |

The legacy `getCommandTrustMetadata()` still works and resolves to `currentTrustMetadata`.

---

## Configuration reference

### Required

| Property | Env var | Description |
|---|---|---|
| `bootstrap.servers` | — | Kafka bootstrap servers |
| `kafka.bootstrap.servers` | — | Kafka bootstrap servers (alias) |
| `taktx.engine.tenant-id` | `TAKTX_ENGINE_TENANT_ID` | Tenant prefix for topic names |
| `taktx.engine.namespace` | `TAKTX_ENGINE_NAMESPACE` | Namespace prefix for topic names |

### Signing identity

| Property | Env var | Values | Default |
|---|---|---|---|
| `taktx.signing.identity-source` | `TAKTX_SIGNING_IDENTITY_SOURCE` | `env`, `file`, `generated` | auto-detected |
| `taktx.signing.key-id` | `TAKTX_SIGNING_KEY_ID` | — | — |
| `taktx.signing.private-key` | `TAKTX_SIGNING_PRIVATE_KEY` | base64 PKCS#8 DER | — |
| `taktx.signing.public-key` | `TAKTX_SIGNING_PUBLIC_KEY` | base64 X.509 DER | — |
| `taktx.signing.owner` | `TAKTX_SIGNING_OWNER` | human-readable label | app name or key ID |
| `taktx.signing.file.key-id-path` | `TAKTX_SIGNING_FILE_KEY_ID_PATH` | file path | — |
| `taktx.signing.file.private-key-path` | `TAKTX_SIGNING_FILE_PRIVATE_KEY_PATH` | file path | — |
| `taktx.signing.file.public-key-path` | `TAKTX_SIGNING_FILE_PUBLIC_KEY_PATH` | file path | — |
| `taktx.signing.file.refresh-interval-ms` | `TAKTX_SIGNING_FILE_REFRESH_INTERVAL_MS` | milliseconds | `1000` |
| `taktx.signing.registration-signature` | `TAKTX_SIGNING_REGISTRATION_SIGNATURE` | base64 RSA/SHA-256 | — |

### Authorization

| Property | Env var | Description |
|---|---|---|
| `taktx.authorization.token-provider` | — | Set to `openid-client-credentials` to enable built-in provider |
| `taktx.authorization.openid.token-endpoint` | — | OAuth2 token endpoint |
| `taktx.authorization.openid.client-id` | — | OAuth2 client ID |
| `taktx.authorization.openid.client-secret` | — | OAuth2 client secret |
| `taktx.authorization.openid.scope` | — | Optional scope |
| `taktx.authorization.openid.audience` | — | Optional audience |
| `taktx.authorization.openid.client-auth-method` | — | `client_secret_basic` (default) or `client_secret_post` |
| `taktx.authorization.openid.connect-timeout-ms` | — | HTTP connect timeout (default `3000`) |
| `taktx.authorization.openid.request-timeout-ms` | — | HTTP request timeout (default `5000`) |
| `taktx.authorization.openid.refresh-skew-ms` | — | Token refresh skew in ms (default `30000`) |

### Topic / engine

| Property | Env var | Default | Description |
|---|---|---|---|
| `taktx.engine.topic.partitions` | `TAKTX_TOPIC_PARTITIONS` | `3` | Default partition count for managed topics |
| `taktx.engine.topic.replicationFactor` | — | `1` | Default replication factor |
| `taktx.client.enabled` | — | `true` | Set to `false` to skip client startup (framework wrappers) |
| `taktx.security.signing.enabled` | — | `false` | Reject unsigned inbound records client-side |
