# TaktX Client — Quarkus

![Coverage](../badges/taktx-client-quarkus-coverage.svg)

Quarkus / CDI wiring for [`taktx-client`](../taktx-client/README.md). A singleton `TaktXClient` CDI bean is produced from MicroProfile/Quarkus configuration and started automatically at application startup.

In normal Quarkus usage you do **not** need to call:

- `TaktXClient.newClientBuilder()`
- `client.start()`
- `client.deployTaktDeploymentAnnotatedClasses()`

The Quarkus wrapper does all of that for you.

---

## Contents

1. [Installation](#installation)
2. [Minimum configuration](#minimum-configuration)
3. [Quick start](#quick-start)
4. [What the Quarkus wrapper does](#what-the-quarkus-wrapper-does)
5. [External task workers](#external-task-workers)
6. [User task workers](#user-task-workers)
7. [Instance update CDI events](#instance-update-cdi-events)
8. [Worker signing](#worker-signing)
9. [Root trust chain — anchored mode](#root-trust-chain--anchored-mode)
10. [Command authorization (JWT)](#command-authorization-jwt)
11. [Custom `AuthorizationTokenProvider`](#custom-authorizationtokenprovider)
12. [Verifying inbound signed records](#verifying-inbound-signed-records)
13. [Publishing signing keys and configuration](#publishing-signing-keys-and-configuration)
14. [Trust metadata on instance updates](#trust-metadata-on-instance-updates)
15. [Configuration reference](#configuration-reference)

---

## Installation

### Maven

```xml
<dependency>
  <groupId>io.taktx</groupId>
  <artifactId>taktx-client-quarkus</artifactId>
  <version>0.4.0-beta-4</version>
</dependency>
```

### Gradle

```kotlin
implementation("io.taktx:taktx-client-quarkus:0.4.0-beta-4")
```

---

## Minimum configuration

```properties
bootstrap.servers=localhost:9092
kafka.bootstrap.servers=localhost:9092
taktx.engine.tenant-id=acme
taktx.engine.namespace=default
taktx.client.groupId.instanceupdate=my-instance-update-group
```

---

## Quick start

Inject `TaktXClient` with CDI:

```java
import io.taktx.client.TaktXClient;
import jakarta.inject.Inject;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ProcessService {

    @Inject
    TaktXClient taktxClient;

    public UUID startInvoice(String invoiceId) {
        return taktxClient.startProcess("invoice-process", -1,
            VariablesDTO.of("invoiceId", invoiceId), null);
    }
}
```

The client is started before your CDI beans receive their `@PostConstruct` calls.

---

## What the Quarkus wrapper does

`TaktXClientProvider` is an `@ApplicationScoped @Startup` CDI bean. At startup it:

1. Copies **all** MicroProfile Config values into a `Properties` object.
2. Builds a `TaktXClient` from those properties.
3. Calls `client.start()` — initialises the `SigningKeysStore`, `RuntimeConfigurationStore`, and all background consumers.
4. Calls `client.deployTaktDeploymentAnnotatedClasses()` — deploys any `@Deployment`-annotated classes.
5. Scans for `@ExternalTask`-annotated beans and auto-registers them.
6. If any CDI observer for `InstanceUpdateRecord` exists, registers an instance-update consumer that fires CDI events.

To skip initialisation in test mode:

```properties
taktx.client.enabled=false
```

---

## External task workers

Annotate CDI beans with `@ExternalTask`. They are discovered and registered automatically:

```java
@ApplicationScoped
@ExternalTask("invoice-payment")
public class InvoicePaymentWorker {

    @Inject
    InvoiceService invoiceService;  // inject other CDI beans normally

    @Execute
    public ExternalTaskResult process(ExternalTaskTriggerDTO trigger) {
        String invoiceId = trigger.getVariables().get("invoiceId");
        invoiceService.markPaid(invoiceId);
        return ExternalTaskResult.complete(VariablesDTO.of("paid", true));
    }
}
```

CDI beans are resolved through `QuarkusBeanInstanceProvider`.

---

## User task workers

```java
@ApplicationScoped
public class UserTaskHandler {

    @Inject
    TaktXClient taktxClient;

    public void register() {
        taktxClient.registerUserTaskConsumer(triggers -> {
            for (UserTaskTriggerDTO trigger : triggers) {
                UserTaskInstanceResponder r = taktxClient.respondToUserTask(trigger);
                // … surface to your UI, wait for human input …
                r.complete(VariablesDTO.of("approved", true));
            }
        }, "my-user-task-group");
    }
}
```

---

## Instance update CDI events

Any CDI observer for `InstanceUpdateRecord` will receive updates automatically when the Quarkus wrapper is active:

```java
@ApplicationScoped
public class InstanceMonitor {

    public void onUpdate(@Observes InstanceUpdateRecord record) {
        System.out.printf("Instance %s → %s%n",
            record.getUpdate().getProcessInstanceId(),
            record.getUpdate().getStatus());
    }
}
```

The consumer group is configured via:

```properties
taktx.client.groupId.instanceupdate=my-instance-update-group
```

---

## Worker signing

Signing configuration flows through MicroProfile Config into the underlying `TaktXClient` builder automatically.

### Source 1 — Environment variables

```bash
export TAKTX_SIGNING_KEY_ID=billing-worker-2026-001
export TAKTX_SIGNING_PRIVATE_KEY=<base64 PKCS#8 DER Ed25519 private key>
export TAKTX_SIGNING_PUBLIC_KEY=<base64 X.509 DER Ed25519 public key>
```

Or in `application.properties`:

```properties
taktx.signing.key-id=billing-worker-2026-001
taktx.signing.private-key=<base64 private key>
taktx.signing.public-key=<base64 public key>
```

### Source 2 — Mounted key files (live rotation)

```properties
taktx.signing.identity-source=file
taktx.signing.file.key-id-path=/opt/taktx/signing/worker/key-id
taktx.signing.file.private-key-path=/opt/taktx/signing/worker/private-key.b64
taktx.signing.file.public-key-path=/opt/taktx/signing/worker/public-key.b64
taktx.signing.file.refresh-interval-ms=1000
```

### Source 3 — Generated (development only)

```properties
taktx.signing.identity-source=generated
```

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

When a signing identity is available, `start()` automatically publishes the public key to `taktx-signing-keys` and signs worker responses. Workers adapt when `signingEnabled` changes in the runtime configuration topic — no restart needed.

---

## Root trust chain — anchored mode

When the engine runs with `TAKTX_PLATFORM_PUBLIC_KEY` set, every key in `taktx-signing-keys` must carry a countersignature from the platform root RSA key. Supply the worker's registration signature:

```properties
taktx.signing.registration-signature=<base64 RSA/SHA-256 countersignature>
```

Or via environment variable:

```bash
export TAKTX_SIGNING_REGISTRATION_SIGNATURE=<base64 RSA/SHA-256 countersignature>
```

### Generating the registration signature

```bash
scripts/generate_trust_anchor.sh --sign \
  --key-dir /path/to/worker/keys \
  --owner billing-worker \
  --role CLIENT
# Prints: TAKTX_SIGNING_REGISTRATION_SIGNATURE=<value>
```

See [docs/security.md](../docs/security.md) for the complete anchored mode reference.

---

## Command authorization (JWT)

When `engineRequiresAuthorization=true`, entry commands need a JWT.

### Built-in OpenID client-credentials provider

```properties
taktx.authorization.token-provider=openid-client-credentials
taktx.authorization.openid.token-endpoint=https://issuer.example.com/oauth/token
taktx.authorization.openid.client-id=taktx-service-account
taktx.authorization.openid.client-secret=super-secret
taktx.authorization.openid.scope=taktx.start taktx.abort taktx.set_variable
taktx.authorization.openid.audience=taktx-engine
taktx.authorization.openid.client-auth-method=client_secret_basic
```

The token is fetched, cached, and refreshed automatically. `startProcess`, `abortElementInstance`, and `setVariable` attach it without any extra code.

| Property | Description | Default |
|---|---|---|
| `taktx.authorization.openid.token-endpoint` | OAuth2 token endpoint URL | — |
| `taktx.authorization.openid.client-id` | Client ID | — |
| `taktx.authorization.openid.client-secret` | Client secret | — |
| `taktx.authorization.openid.scope` | Optional scope | — |
| `taktx.authorization.openid.audience` | Optional audience | — |
| `taktx.authorization.openid.client-auth-method` | `client_secret_basic` or `client_secret_post` | `client_secret_basic` |
| `taktx.authorization.openid.connect-timeout-ms` | HTTP connect timeout | `3000` |
| `taktx.authorization.openid.request-timeout-ms` | HTTP request timeout | `5000` |
| `taktx.authorization.openid.refresh-skew-ms` | Refresh before expiry (ms) | `30000` |

### Attaching a JWT explicitly

```java
taktxClient.startProcess("invoice-process", -1, VariablesDTO.empty(), jwt);
taktxClient.abortElementInstance(instanceId, List.of(), jwt);
taktxClient.setVariable(instanceId, List.of(), VariablesDTO.of("approved", true), jwt);
```

---

## Custom `AuthorizationTokenProvider`

The Quarkus wrapper does **not** auto-wire a CDI bean of type `AuthorizationTokenProvider` into the generated `TaktXClient`. For custom token-retrieval logic, disable the wrapper and create your own client:

```properties
taktx.client.enabled=false
```

```java
@ApplicationScoped
public class TaktXClientFactory {

    @Produces
    @ApplicationScoped
    public TaktXClient taktxClient(@ConfigProperty(name="bootstrap.servers") String bootstrapServers) {
        Properties props = new Properties();
        props.put("bootstrap.servers", bootstrapServers);
        // ... other props

        TaktXClient client = TaktXClient.newClientBuilder()
            .withProperties(props)
            .withAuthorizationTokenProvider(request -> myVault.fetchToken(request.getScope()))
            .build();
        client.start();
        return client;
    }
}
```

---

## Verifying inbound signed records

To reject unsigned inbound records at the client side:

```properties
taktx.security.signing.enabled=true
```

This is a **local client rule**, not an engine toggle. Engine signing is controlled through the `taktx-configuration` Kafka topic. The client watches that topic at runtime — already-running Quarkus apps adapt when `signingEnabled` changes without a restart.

---

## Publishing signing keys and configuration

Use the injected `TaktXClient` bean for runtime operations:

```java
@Inject TaktXClient taktxClient;

// Publish a JWT verification RSA key (platform/ingester)
taktxClient.publishSigningKey("platform-key-2026-03", rsaPublicKeyBase64, "platform", "RSA");

// Enable signing and authorization on the running cluster
taktxClient.publishGlobalConfig(GlobalConfigurationDTO.builder()
    .signingEnabled(true)
    .engineRequiresAuthorization(true)
    .build());

// Publish a license
taktxClient.publishLicense(licenseText);
```

Or use static overloads without a running client:

```java
TaktXClient.publishSigningKey(props, "platform-key-2026-03", rsaPublicKeyBase64, "platform", "RSA");
TaktXClient.publishGlobalConfig(props, cfg);
```

---

## Trust metadata on instance updates

`InstanceUpdateRecord.getUpdate()` exposes two provenance fields:

```java
TrustMetadata current = update.getCurrentTrustMetadata();  // actor for current step
TrustMetadata origin  = update.getOriginTrustMetadata();   // original command initiator
```

| Scenario | `current` | `origin` |
|---|---|---|
| External JWT start | JWT | JWT |
| Worker response | worker | worker |
| Timer continuation after worker | engine | worker |
| Engine follow-up after JWT start | engine | JWT |

---

## Configuration reference

### Core

| Property | Default | Description |
|---|---|---|
| `bootstrap.servers` | — | Kafka bootstrap servers |
| `kafka.bootstrap.servers` | — | Kafka bootstrap servers (alias) |
| `taktx.engine.tenant-id` | — | Tenant prefix |
| `taktx.engine.namespace` | — | Namespace prefix |
| `taktx.client.enabled` | `true` | Set to `false` to skip startup (test mode) |
| `taktx.client.groupId.instanceupdate` | — | Consumer group ID for instance update CDI events |
| `taktx.engine.topic.partitions` | `3` | Default external-task topic partition count |
| `taktx.engine.topic.replicationFactor` | `1` | Default replication factor |

### Worker signing

| Property | Env var | Default | Description |
|---|---|---|---|
| `taktx.signing.identity-source` | `TAKTX_SIGNING_IDENTITY_SOURCE` | auto | `env`, `file`, or `generated` |
| `taktx.signing.key-id` | `TAKTX_SIGNING_KEY_ID` | — | Key ID |
| `taktx.signing.private-key` | `TAKTX_SIGNING_PRIVATE_KEY` | — | Base64 PKCS#8 DER Ed25519 private key |
| `taktx.signing.public-key` | `TAKTX_SIGNING_PUBLIC_KEY` | — | Base64 X.509 DER Ed25519 public key |
| `taktx.signing.owner` | `TAKTX_SIGNING_OWNER` | `quarkus.application.name` | Owner label published with the key |
| `taktx.signing.file.key-id-path` | `TAKTX_SIGNING_FILE_KEY_ID_PATH` | — | Path to `key-id` file |
| `taktx.signing.file.private-key-path` | `TAKTX_SIGNING_FILE_PRIVATE_KEY_PATH` | — | Path to `private-key.b64` file |
| `taktx.signing.file.public-key-path` | `TAKTX_SIGNING_FILE_PUBLIC_KEY_PATH` | — | Path to `public-key.b64` file |
| `taktx.signing.file.refresh-interval-ms` | `TAKTX_SIGNING_FILE_REFRESH_INTERVAL_MS` | `1000` | File poll interval in ms |
| `taktx.signing.registration-signature` | `TAKTX_SIGNING_REGISTRATION_SIGNATURE` | — | Platform countersignature (anchored mode) |

### Authorization

| Property | Default | Description |
|---|---|---|
| `taktx.authorization.token-provider` | — | `openid-client-credentials` to enable built-in OIDC provider |
| `taktx.authorization.openid.token-endpoint` | — | OAuth2 token endpoint |
| `taktx.authorization.openid.client-id` | — | Client ID |
| `taktx.authorization.openid.client-secret` | — | Client secret |
| `taktx.authorization.openid.scope` | — | Scope |
| `taktx.authorization.openid.audience` | — | Audience |
| `taktx.authorization.openid.client-auth-method` | `client_secret_basic` | Auth method |
| `taktx.authorization.openid.connect-timeout-ms` | `3000` | Connect timeout |
| `taktx.authorization.openid.request-timeout-ms` | `5000` | Request timeout |
| `taktx.authorization.openid.refresh-skew-ms` | `30000` | Refresh skew before expiry |

### Security

| Property | Default | Description |
|---|---|---|
| `taktx.security.signing.enabled` | `false` | Reject unsigned inbound records locally |
