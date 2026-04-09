# TaktX Client — Spring Boot 4

![Coverage](../badges/taktx-client-spring-boot-4-coverage.svg)

Spring Boot 4 auto-configuration wrapper for [`taktx-client`](../taktx-client/README.md). A singleton `TaktXClient` bean is created, started, and wired automatically from your Spring application properties.

In normal Spring Boot usage you do **not** need to call:

- `TaktXClient.newClientBuilder()`
- `client.start()`
- `client.deployTaktDeploymentAnnotatedClasses()`

The auto-configuration does all of that for you.

> **Spring Boot 4 / Spring Framework 7** — this module targets Spring Boot 4.x (Spring Framework 7.x)
> and requires Java 21+. For Spring Boot 3.x users see
> [`taktx-client-spring-boot-3`](../taktx-client-spring-boot-3/README.md).

---

## Contents

1. [Installation](#installation)
2. [Minimum configuration](#minimum-configuration)
3. [Quick start](#quick-start)
4. [What auto-configuration does](#what-auto-configuration-does)
5. [External task workers](#external-task-workers)
6. [User task workers](#user-task-workers)
7. [Instance update events](#instance-update-events)
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
  <artifactId>taktx-client-spring-boot-4</artifactId>
  <version>0.4.0-beta-3</version>
</dependency>
```

### Gradle

```kotlin
implementation("io.taktx:taktx-client-spring-boot-4:0.4.0-beta-3")
```

---

## Minimum configuration

```properties
bootstrap.servers=localhost:9092
kafka.bootstrap.servers=localhost:9092
taktx.engine.tenant-id=acme
taktx.engine.namespace=default
```

---

## Quick start

Inject `TaktXClient` wherever you need it:

```java
@Service
public class ProcessService {

    private final TaktXClient taktxClient;

    public ProcessService(TaktXClient taktxClient) {
        this.taktxClient = taktxClient;
    }

    public UUID startInvoice(String invoiceId) {
        return taktxClient.startProcess("invoice-process", -1,
            VariablesDTO.of("invoiceId", invoiceId), null);
    }
}
```

The client is ready by the time Spring's `@PostConstruct` phase completes — `start()` has already been called.

---

## What auto-configuration does

When `taktx.client.enabled=true` (the default), `TaktXClientAutoConfiguration`:

1. Builds a `TaktXClient` from all Spring application properties.
2. Calls `client.start()` — initialises the `SigningKeysStore`, `RuntimeConfigurationStore`, and starts all background consumers.
3. Calls `client.deployTaktDeploymentAnnotatedClasses()` — deploys any classes annotated with `@Deployment`.
4. Scans for `@ExternalTask`-annotated workers and auto-registers them.
5. If `taktx.client.instanceupdate.enabled=true` and a `groupId` is configured, registers an instance-update consumer that publishes `InstanceUpdateRecord` objects as Spring application events.

Auto-configuration classes are registered via
`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
(the Spring Boot 4 mechanism — `spring.factories` is no longer used for this purpose).
The main auto-configuration class is annotated with `@AutoConfiguration` instead of
`@Configuration` for correct ordering and condition evaluation.

To disable the auto-configured client (e.g. to build your own with extra options):

```properties
taktx.client.enabled=false
```

---

## External task workers

Annotate worker classes with `@ExternalTask`. They are discovered and registered automatically:

```java
@Component
@ExternalTask("invoice-payment")
public class InvoicePaymentWorker {

    @Execute
    public ExternalTaskResult process(ExternalTaskTriggerDTO trigger) {
        // inject Spring beans in the constructor as normal
        String invoiceId = trigger.getVariables().get("invoiceId");
        // … do work …
        return ExternalTaskResult.complete(VariablesDTO.of("paid", true));
    }
}
```

Spring beans are injected into worker instances through the `SpringBeanInstanceProvider`.

---

## User task workers

```java
@Service
public class UserTaskHandler {

    private final TaktXClient taktxClient;

    public UserTaskHandler(TaktXClient taktxClient) {
        this.taktxClient = taktxClient;
    }

    @PostConstruct
    public void register() {
        taktxClient.registerUserTaskConsumer(triggers -> {
            for (UserTaskTriggerDTO trigger : triggers) {
                UserTaskInstanceResponder r = taktxClient.respondToUserTask(trigger);
                // … surface to your UI, wait for input …
                r.complete(VariablesDTO.of("approved", true));
            }
        }, "my-user-task-group");
    }
}
```

---

## Instance update events

Enable instance update forwarding in `application.properties`:

```properties
taktx.client.instanceupdate.enabled=true
taktx.client.groupId.instanceupdate=my-monitor-group
```

Then listen for the Spring application event:

```java
@Component
public class InstanceUpdateListener {

    @EventListener
    public void onUpdate(InstanceUpdateRecord record) {
        System.out.printf("Instance %s changed status to %s%n",
            record.getUpdate().getProcessInstanceId(),
            record.getUpdate().getStatus());
    }
}
```

---

## Worker signing

Signing configuration flows through Spring application properties into the underlying `TaktXClient` builder automatically. No extra Spring wiring is needed.

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

The Spring wrapper does **not** auto-wire a Spring bean of type `AuthorizationTokenProvider` into the generated `TaktXClient`. For custom token-retrieval logic, disable the auto-configured client and construct your own:

```properties
taktx.client.enabled=false
```

```java
@Configuration
public class TaktXConfig {

    @Bean
    public TaktXClient taktxClient(TaktPropertiesHelper helper) {
        TaktXClient client = TaktXClient.newClientBuilder()
            .withProperties(helper.getTaktProperties())
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

This is a **local client rule**, not an engine toggle. Engine signing is controlled through the `taktx-configuration` Kafka topic. The client watches that topic at runtime — already-running Spring apps adapt when `signingEnabled` changes without a restart.

---

## Publishing signing keys and configuration

Use the injected `TaktXClient` bean for runtime operations:

```java
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
| `taktx.client.enabled` | `true` | Set to `false` to skip auto-configuration |
| `taktx.client.instanceupdate.enabled` | `false` | Enable instance-update event publishing |
| `taktx.client.groupId.instanceupdate` | — | Consumer group ID for instance updates |
| `taktx.engine.topic.partitions` | `3` | Default external-task topic partition count |
| `taktx.engine.topic.replicationFactor` | `1` | Default replication factor |

### Worker signing

| Property | Env var | Default | Description |
|---|---|---|---|
| `taktx.signing.identity-source` | `TAKTX_SIGNING_IDENTITY_SOURCE` | auto | `env`, `file`, or `generated` |
| `taktx.signing.key-id` | `TAKTX_SIGNING_KEY_ID` | — | Key ID |
| `taktx.signing.private-key` | `TAKTX_SIGNING_PRIVATE_KEY` | — | Base64 PKCS#8 DER Ed25519 private key |
| `taktx.signing.public-key` | `TAKTX_SIGNING_PUBLIC_KEY` | — | Base64 X.509 DER Ed25519 public key |
| `taktx.signing.owner` | `TAKTX_SIGNING_OWNER` | app name | Owner label published with the key |
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

