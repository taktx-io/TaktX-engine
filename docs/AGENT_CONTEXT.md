# TaktX Engine — Agent Context Document

**Last updated:** March 22, 2026
**Purpose:** Provide a fresh agent session with complete codebase context so it can work effectively without re-exploring the repository.

---

## 1. Project Identity

| Field | Value |
|---|---|
| **Name** | TaktX Engine |
| **Description** | High-performance BPMN process automation engine built on Apache Kafka Streams |
| **Version** | `0.3.0-beta-1` (see `build.gradle.kts` `allprojects.version`) |
| **Language** | Java 21 (client/shared SDKs), Java 23 (engine) |
| **Build system** | Gradle (Kotlin DSL) with version catalog (`gradle/libs.versions.toml`) |
| **Application framework** | Quarkus 3.32.2 (engine only); SDKs are framework-agnostic |
| **Messaging** | Apache Kafka 4.1.1 (Kafka Streams for engine, plain Producer/Consumer for clients) |
| **Serialization** | Jackson (CBOR + JSON), custom Kafka serializers/deserializers |
| **License** | Engine: TaktX Business Source License 1.0; Client SDKs: Apache 2.0 |
| **Root path** | `/Users/erichendriks/IdeaProjects/TaktX-engine2` |

---

## 2. Module Structure

```
taktx (root)
├── taktx-shared       — Shared DTOs, security utilities, Kafka serdes, topic definitions
│   ├── src/main/java/io/taktx/
│   │   ├── Topics.java              — Enum of all Kafka topics (names, cleanup policies)
│   │   ├── CleanupPolicy.java       — DELETE / COMPACT enum
│   │   ├── dto/                     — ~150+ DTO classes (ProcessInstanceTriggerDTO, InstanceUpdateDTO, etc.)
│   │   ├── security/                — Ed25519Service, SigningIdentity, SigningKeysStore, RuntimeConfigurationHolder, JWT utilities
│   │   ├── serdes/                  — SigningSerializer and shared serializers
│   │   ├── topicmanagement/         — ExternalTaskTopicRequester
│   │   ├── util/                    — TaktPropertiesHelper (Kafka property builder)
│   │   └── xml/                     — BPMN XML parsing
│   └── build.gradle.kts            — Java 21, depends on jackson, kafka-clients, jjwt, caffeine
│
├── taktx-client       — Plain-Java client SDK (no framework dependency)
│   ├── src/main/java/io/taktx/client/
│   │   ├── TaktXClient.java         — Main entry point; builder pattern; start/stop lifecycle
│   │   ├── TaktXClientBuilder       — Inner class of TaktXClient; configures signing, auth, properties
│   │   ├── ProcessDefinitionDeployer.java
│   │   ├── ProcessInstanceProducer.java
│   │   ├── ProcessInstanceResponder.java
│   │   ├── ProcessDefinitionConsumer.java
│   │   ├── ProcessInstanceUpdateConsumer.java
│   │   ├── ExternalTaskTriggerTopicConsumer.java
│   │   ├── UserTaskTriggerTopicConsumer.java
│   │   ├── MessageEventSender.java
│   │   ├── SignalSender.java
│   │   ├── RuntimeConfigurationStore.java — Consumes taktx-configuration topic, updates RuntimeConfigurationHolder
│   │   ├── annotation/               — @Deployment, @JobWorker, @Variable, @AckStrategy, @ThreadingStrategy
│   │   ├── auth/                     — AuthorizationTokenProvider, OpenIdClientCredentialsTokenProvider
│   │   └── serdes/                   — Client-side serializers/deserializers
│   └── build.gradle.kts             — Java 21, depends on taktx-shared, kafka-clients, classgraph, jackson
│
├── taktx-client-quarkus — Quarkus CDI integration for taktx-client
│   ├── src/main/java/io/taktx/client/quarkus/
│   │   ├── TaktXClientProvider.java  — CDI producer for TaktXClient bean
│   │   ├── QuarkusBeanInstanceProvider.java
│   │   └── *Producer.java           — CDI producers for ParameterResolverFactory, ProcessInstanceResponder, etc.
│   └── build.gradle.kts
│
├── taktx-client-spring — Spring Boot auto-configuration for taktx-client
│   ├── src/main/java/io/taktx/client/spring/
│   │   ├── TaktXClientAutoConfiguration.java
│   │   ├── SpringBeanInstanceProvider.java
│   │   └── *Configuration.java
│   └── build.gradle.kts
│
├── taktx-engine        — Core BPMN engine (Quarkus application)
│   ├── src/main/java/io/taktx/engine/
│   │   ├── pi/                       — Process instance processing (ProcessInstanceProcessor, ScopeProcessor, Forwarder, etc.)
│   │   ├── pd/                       — Process definition management
│   │   ├── dd/                       — Definition deployment
│   │   ├── config/                   — Engine configuration (TaktConfiguration, etc.)
│   │   ├── security/                 — Engine-side auth & signing (EngineAuthorizationService, MessageSigningService, NonceStore)
│   │   ├── topicmanagement/          — DynamicTopicManager, TopicBootstrapper
│   │   ├── license/                  — LicenseManager, license enforcement
│   │   ├── feel/                     — FEEL expression evaluation (Camunda FEEL engine)
│   │   ├── generic/                  — Shared engine utilities
│   │   └── api/                      — REST API endpoints
│   └── build.gradle.kts             — Java 23, Quarkus plugin, depends on taktx-shared
│
├── docs/
│   ├── security.md                   — Full security architecture (Ed25519 signing, RS256 JWT auth, config topic)
│   ├── partition-budget.md           — Partition budget design (total budget model, enforcement)
│   └── config-simplification-plan.md — Config simplification (env vars vs config topic)
│
├── docker/                           — Docker Compose files, Prometheus/Grafana config, signing key examples
├── gradle/libs.versions.toml         — Central dependency version catalog
├── build.gradle.kts                  — Root build: JaCoCo, Spotless (Google Java Format), JReleaser
└── settings.gradle.kts               — includes: taktx-shared, taktx-client, taktx-engine, taktx-client-quarkus, taktx-client-spring
```

---

## 3. Kafka Topic Architecture

All topic names are prefixed: `<tenantId>.<namespace>.<topicName>` (via `TaktPropertiesHelper.getPrefixedTopicName()`).

### Initial fixed topics (always 1 partition, created at bootstrap)

| Enum constant | Topic name | Cleanup |
|---|---|---|
| `TOPIC_META_REQUESTED_TOPIC` | `topic-meta-requested` | COMPACT |
| `TOPIC_META_ACTUAL_TOPIC` | `topic-meta-actual` | COMPACT |
| `CONFIGURATION_TOPIC` | `taktx-configuration` | COMPACT |
| `SIGNING_KEYS_TOPIC` | `taktx-signing-keys` | COMPACT |

### Managed fixed topics (partition count = `taktx.engine.topic.partitions`, default 3)

| Enum constant | Topic name | Cleanup |
|---|---|---|
| `XML_BY_PROCESS_DEFINITION_ID` | `xml-by-process-definition-id` | COMPACT |
| `PROCESS_DEFINITION_ACTIVATION_TOPIC` | `process-definition-activation` | COMPACT |
| `MESSAGE_EVENT_TOPIC` | `message-event` | DELETE |
| `SCHEDULE_COMMANDS` | `schedule-commands` | DELETE |
| `INSTANCE_UPDATE_TOPIC` | `instance-update` | DELETE |
| `PROCESS_INSTANCE_TRIGGER_TOPIC` | `process-instance` | DELETE |
| `PROCESS_DEFINITIONS_TRIGGER_TOPIC` | `definitions` | DELETE |
| `SIGNAL_TOPIC` | `signals` | COMPACT |
| `USER_TASK_TRIGGER_TOPIC` | `usertasks` | COMPACT |
| `USER_TASK_RESPONSE_TOPIC` | `usertasks-response` | DELETE |

### Dynamic worker topics

| Pattern | Cleanup |
|---|---|
| `external-task-trigger-<externalTaskId>` | Worker-specified (default DELETE) |

---

## 4. TaktXClient Lifecycle & Architecture

### Builder pattern

```java
TaktXClient client = TaktXClient.newClientBuilder()
    .withProperties(properties)                          // required: bootstrap.servers, tenant-id, namespace
    .withSigningIdentitySource(signingIdentitySource)     // optional: env/file/generated (default: auto-detect)
    .withAuthorizationTokenProvider(tokenProvider)        // optional: OpenID Connect client credentials
    .withTaktParameterResolverFactory(factory)            // optional: custom parameter injection
    .withResultProcessorFactory(factory)                  // optional: custom result processing
    .build();
```

### Startup sequence (`client.start()`)

1. **`initRuntimeConfigurationStore()`** — subscribes to `taktx-configuration` topic, waits up to 10s for initial state, populates `RuntimeConfigurationHolder` (signingEnabled, engineRequiresAuthorization)
2. **`refreshWorkerSigningFunctionRegistration()`** — registers signing function in `SigningServiceHolder` if signing is enabled and identity is available
3. **`initSigningKeysStore()`** — subscribes to `taktx-signing-keys` topic, waits up to 10s, populates `SigningKeysStoreHolder`
4. **`processDefinitionConsumer.subscribeToDefinitionRecords()`** — starts consuming process definitions
5. **`xmlByProcessDefinitionIdConsumer.subscribeToTopic()`** — starts consuming XML definitions
6. **`publishWorkerSigningKeyIfConfigured()`** — publishes the worker's Ed25519 public key to `taktx-signing-keys`

### Shutdown (`client.stop()`)

Stops all consumers, clears signing/config stores, resets state.

### Key internal components

| Component | Responsibility |
|---|---|
| `ProcessDefinitionDeployer` | Deploys BPMN XML to the engine via Kafka |
| `ProcessInstanceProducer` | Sends start/abort/setVariable commands to `process-instance` topic |
| `ProcessInstanceResponder` | Creates responders for external tasks and user tasks |
| `ExternalTaskTriggerTopicConsumer` | Consumes external task triggers from dynamic worker topics |
| `UserTaskTriggerTopicConsumer` | Consumes user task triggers from `usertasks` topic |
| `ProcessInstanceUpdateConsumer` | Consumes instance updates from `instance-update` topic |
| `MessageEventSender` | Sends BPMN message events to `message-event` topic |
| `SignalSender` | Sends BPMN signals to `signals` topic |
| `RuntimeConfigurationStore` | Maintains in-memory view of `taktx-configuration` topic |
| `ExternalTaskTopicRequester` | Requests creation of worker topics via `topic-meta-requested` |

---

## 5. Security Architecture

### Two independent mechanisms

1. **RS256 JWT command authorization** (for `StartCommandDTO` and `AbortTriggerDTO`)
   - Controlled by `GlobalConfigurationDTO.engineRequiresAuthorization`
   - JWT `kid` resolves public key from `taktx-signing-keys` KTable
   - Expiry and nonce replay always enforced

2. **Ed25519 message signing** (for worker responses, engine outbound events)
   - Controlled by `GlobalConfigurationDTO.signingEnabled`
   - Signing identity sources: `generated` (default), `env`, `file`
   - Signature header format: `<keyId>:<base64-signature>`

### Worker signing identity resolution order (in `TaktXClientBuilder.resolveSigningIdentitySource`)

1. Explicitly provided `SigningIdentitySource` via builder
2. `taktx.signing.identity-source` property / `TAKTX_SIGNING_IDENTITY_SOURCE` env var → `env`, `file`, or `generated`
3. Auto-detect: try `EnvironmentWorkerSigningIdentitySource` (checks env vars) → fallback to `GeneratedSigningIdentitySource`

### Signing key publication

Workers auto-publish their public key to `taktx-signing-keys` on `start()`. The key ID is tracked to avoid duplicate publications.

### Runtime configuration updates

The `RuntimeConfigurationStore` watches the `taktx-configuration` topic and calls `refreshWorkerSigningFunctionRegistration()` whenever the config changes, enabling/disabling signing dynamically without restart.

---

## 6. Key Shared DTOs (in `taktx-shared/src/main/java/io/taktx/dto/`)

| DTO | Purpose |
|---|---|
| `ProcessInstanceTriggerDTO` | Base type for all process instance commands (start, abort, continue, external task response, etc.) |
| `StartCommandDTO` | Starts a new process instance |
| `AbortTriggerDTO` | Aborts a process instance or element |
| `ExternalTaskTriggerDTO` | Trigger sent to workers for external task execution |
| `ExternalTaskResponseTriggerDTO` | Worker response to an external task |
| `UserTaskTriggerDTO` | User task trigger |
| `UserTaskResponseTriggerDTO` | User task completion response |
| `MessageEventDTO` | BPMN message correlation event |
| `SignalDTO` | BPMN signal event |
| `InstanceUpdateDTO` | Engine-emitted instance state updates |
| `ProcessDefinitionDTO` | Deployed process definition metadata |
| `ParsedDefinitionsDTO` | Result of deploying a BPMN file |
| `ConfigurationEventDTO` | Runtime configuration update envelope |
| `GlobalConfigurationDTO` | Cluster-wide runtime config (signingEnabled, engineRequiresAuthorization, rbacEnabled) |
| `VariablesDTO` | Process variables container |
| `CommandTrustMetadataDTO` | Trust provenance (current + origin) on commands |
| `SigningKeyDTO` | Public key record in `taktx-signing-keys` topic |
| `TopicMetaDTO` | Topic metadata for dynamic topic management |

---

## 7. Engine Architecture Highlights

### Kafka Streams topology (in `taktx-engine`)

- Engine is a Quarkus application using Kafka Streams
- Stateful processors with KTable/state stores for process instance state, signal subscriptions, schedules
- Co-partitioned topics: all managed fixed topics must have the same partition count

### Key engine packages

| Package | Contents |
|---|---|
| `engine.pi` | Process instance processing: `ProcessInstanceProcessor`, `ScopeProcessor`, `Forwarder`, DTO mappers |
| `engine.pi.processor` | Individual BPMN element processors (tasks, gateways, events) |
| `engine.pi.scope` | Scope management (subprocess, multi-instance) |
| `engine.pd` | Process definition management |
| `engine.dd` | Definition deployment handling |
| `engine.config` | Engine configuration (`TaktConfiguration`) |
| `engine.security` | `EngineAuthorizationService`, `MessageSigningService`, `NonceStore`, `GeneratedEngineSigningIdentitySource` |
| `engine.topicmanagement` | `DynamicTopicManager` (dynamic topic creation/reconciliation), `TopicBootstrapper` |
| `engine.license` | License enforcement (License3j-based) |
| `engine.feel` | FEEL expression evaluation |
| `engine.api` | REST API endpoints |

---

## 8. Configuration Model

### Required properties (passed to TaktXClient)

| Property | Env var equivalent | Purpose |
|---|---|---|
| `bootstrap.servers` | `KAFKA_BOOTSTRAP_SERVERS` | Kafka connection |
| `taktx.engine.tenant-id` | `TAKTX_ENGINE_TENANT_ID` | Tenant prefix for topic names |
| `taktx.engine.namespace` | `TAKTX_ENGINE_NAMESPACE` | Namespace prefix for topic names |

### Runtime configuration (via `taktx-configuration` Kafka topic)

| Field | Default | Purpose |
|---|---|---|
| `signingEnabled` | `false` | Enable Ed25519 message signing |
| `engineRequiresAuthorization` | `false` | Enable RS256 JWT command authorization |
| `rbacEnabled` | `false` | Reserved for RBAC |
| `trustedKeyIds` | `[]` | Reserved compatibility surface |

### Worker signing properties

| Property | Env var | Purpose |
|---|---|---|
| `taktx.signing.identity-source` | `TAKTX_SIGNING_IDENTITY_SOURCE` | `env`, `file`, or `generated` |
| `taktx.signing.key-id` | `TAKTX_SIGNING_KEY_ID` | Worker key ID (for `env` source) |
| `taktx.signing.owner` | `TAKTX_SIGNING_OWNER` | Human-readable owner label |
| `taktx.signing.public-key` | `TAKTX_SIGNING_PUBLIC_KEY` | Worker Ed25519 public key (for `env` source) |

---

## 9. Build & Development

### Key commands

```bash
./gradlew build                    # Full build + tests
./gradlew taktx-engine:quarkusDev  # Run engine in dev mode
./gradlew spotlessApply            # Format code (Google Java Format)
./gradlew test                     # Run all tests
```

### Code style

- Google Java Format (enforced by Spotless plugin)
- Lombok used in shared and engine modules
- MapStruct used in engine for DTO mapping

### Testing

- JUnit 5 + AssertJ + Mockito
- Awaitility for async assertions
- JaCoCo for coverage
- Engine tests use `taktx-client` as test dependency

### Dependency versions (from `gradle/libs.versions.toml`)

| Dependency | Version |
|---|---|
| Quarkus | 3.32.2 |
| Jackson | 2.21.1 |
| Kafka | 4.1.1 |
| Lombok | 1.18.42 |
| MapStruct | 1.6.3 |
| Camunda FEEL | 1.21.0 |
| JJWT | 0.13.0 |
| Caffeine | 3.2.3 |
| Spring Boot | 3.5.10 |
| Spring Framework | 6.2.15 |
| JUnit | 5.10.1 |
| ClassGraph | 4.8.184 |

---

## 10. Licensing & Partition Budget

### License tiers

| Tier | Total partition budget | Notes |
|---|---|---|
| Community | 60 | Free: 10 fixed × 3 + ~26 worker partitions |
| Standard | 180 | Commercial |
| Enterprise | Unlimited | Commercial |

### Budget formula

```
total = 4 (initial fixed topics × 1 partition)
      + 10 × taktx.engine.topic.partitions (managed fixed topics)
      + Σ worker topic partitions
```

### Enforcement

- `TopicBootstrapper`: halt if fixed cost exceeds budget (engine cannot start)
- `DynamicTopicManager.scanRequest()`: reject worker topic if budget exceeded (no halt)
- `DynamicTopicManager.adaptToExternalChanges()`: warn on mismatch (no halt)

---

## 11. Key Patterns & Conventions

1. **Topic naming**: All topics are prefixed via `TaktPropertiesHelper.getPrefixedTopicName()` → `<tenantId>.<namespace>.<topicName>`
2. **Signing flow**: `SigningSerializer` wraps any value serializer to add Ed25519 signatures transparently
3. **Trust metadata**: Commands carry `currentTrustMetadata` (who signed this command) and `originTrustMetadata` (who initiated the chain)
4. **Virtual threads**: `TaktXClient` uses `Executors.newVirtualThreadPerTaskExecutor()` for consumer threads
5. **Compacted topics for state**: Configuration, signing keys, process definitions, and topic metadata use COMPACT cleanup
6. **Builder firstNonBlank pattern**: `TaktXClientBuilder.firstNonBlank()` checks property → system property → env var in order
7. **Framework integration**: `taktx-client-quarkus` and `taktx-client-spring` are thin CDI/Spring wrappers around the plain `taktx-client`
8. **Annotation-driven workers**: `@JobWorker` annotation on methods, `@Variable` for parameter injection, `@Deployment` for auto-deploy; scanned by `AnnotationScanner` using ClassGraph
9. **Graceful degradation**: If `taktx-configuration` or `taktx-signing-keys` topics are unavailable, the client logs a warning and continues with defaults (signing disabled, no authorization)

---

## 12. Files Most Likely to Be Edited

| File | Why |
|---|---|
| `taktx-client/.../TaktXClient.java` | Main client API — any new feature or API change |
| `taktx-shared/.../dto/*.java` | DTO changes for new features |
| `taktx-shared/.../Topics.java` | Adding new Kafka topics |
| `taktx-shared/.../security/*.java` | Security infrastructure changes |
| `taktx-engine/.../pi/ProcessInstanceProcessor.java` | Core BPMN execution logic |
| `taktx-engine/.../topicmanagement/DynamicTopicManager.java` | Topic management / partition budget |
| `taktx-engine/.../security/*.java` | Engine-side auth/signing changes |
| `taktx-engine/.../config/*.java` | Engine configuration changes |
| `taktx-client/.../annotation/*.java` | Worker annotation changes |
| `gradle/libs.versions.toml` | Dependency version updates |
| `build.gradle.kts` (root + subprojects) | Build configuration |

---

## 13. OPEN ISSUE — Security Model Redesign (Trust Chain & Call-Activity Authorization)

### 13.1 Immediate Bug: Engine-originated entry commands are rejected

When the engine itself produces a `StartCommandDTO` or `AbortTriggerDTO` (e.g. call activity child start, signal-triggered start, message-triggered start, timer-triggered start, child abort), the command is rejected by `EngineAuthorizationService.authorize()` because:

1. `requiresJwtAuthorization()` (line 321–323 of `EngineAuthorizationService.java`) unconditionally returns `true` for **all** `StartCommandDTO` and `AbortTriggerDTO` instances — regardless of origin.
2. Engine-originated commands do not carry a JWT (`X-TaktX-Authorization` header is absent).
3. The engine signs these commands with Ed25519 via `SigningSerializer`, but the authorization logic checks JWT **first** for entry commands and never falls through to the Ed25519 path.

**Error produced:**
```
AuthorizationTokenException: "Missing required X-TaktX-Authorization header on command StartCommandDTO"
```

**Affected code paths (all produce `StartCommandDTO` without JWT):**

| Origin | File | Method |
|---|---|---|
| Call activity child start | `Forwarder.java` (line 350) | `forwardNewStartCommands()` |
| Signal-triggered start | `SignalProcessor.java` (line ~153) | signal event → new `StartCommandDTO` |
| Message-triggered start | `MessageEventProcessor.java` (line ~167) | message event → new `StartCommandDTO` |
| Timer-triggered start | `ProcessDefinitionActivationProcessor.java` (line ~264) | timer → new `StartCommandDTO` |

All these go through `TopologyProducer`'s branch back to `process-instance-trigger` topic using `PROCESS_INSTANCE_TRIGGER_SERDE` (which wraps with `SigningSerializer`), so they arrive Ed25519-signed but without JWT.

### 13.2 Fundamental Flaw: No root of trust for key registration

The `taktx-signing-keys` compacted topic is the source of truth for **both** RSA public keys (used for JWT `kid` lookup) and Ed25519 public keys (used for signature verification). However:

- **Anyone with Kafka producer access can write to `taktx-signing-keys`.**
- A malicious actor can register their own key with `owner="engine"` and sign arbitrary commands that pass Ed25519 verification.
- A malicious actor can overwrite the platform's RSA key entry and issue their own JWTs.
- There is **no countersigning, no root authority, no chain of trust** anchoring who may register keys.

This means **both** Ed25519 signing **and** JWT validation are fundamentally bypassed if any untrusted party has Kafka write access.

### 13.3 The four command flows

| # | Flow | Auth mechanism | Notes |
|---|---|---|---|
| 1 | Frontend → Platform → TaktXClient → engine | **RS256 JWT** (`X-TaktX-Authorization`) | Platform signs JWT with RSA private key; engine validates via `kid` → KTable lookup |
| 2 | External clients starting/aborting | **RS256 JWT** | Client obtains JWT from Platform (or OIDC); same validation path |
| 3 | Worker responses (`ExternalTaskResponder`) | **Ed25519 only** (`X-TaktX-Signature`) | Worker signs response; engine verifies via key lookup in KTable |
| 4 | Engine-originated entry commands (call activity, signal, message, timer starts/aborts) | **Ed25519 only** (via `SigningSerializer`) | Engine signs with its own Ed25519 key; **currently rejected** because `requiresJwtAuthorization()` demands JWT |

### 13.4 Proposed Solution: Three-Tier Trust Model

#### Tier 1 — Bootstrap trust anchor

- **`TAKTX_PLATFORM_PUBLIC_KEY`** environment variable becomes the **root of trust**.
- Contains the RSA (or Ed25519) public key of the Platform Service / deployment authority.
- Already referenced in `config-simplification-plan.md` as "optional deployment trust anchor" but **never wired into validation**.
- When configured: all key registrations MUST be countersigned. When absent: community/standalone mode — behaves as today (open registration).

#### Tier 2 — Countersigned key registration

- Add a `registrationSignature` field to `SigningKeyDTO` — an RSA signature over the canonical key payload (`keyId + publicKeyBase64 + algorithm + owner + permissions`), produced by the Platform's private key.
- On key lookup (both `PublicKeyProvider.getKey()` for JWT and `EngineAuthorizationService` for Ed25519), verify the `registrationSignature` against the bootstrap root key **before** trusting any key.
- This closes the open-registration hole: even with Kafka write access, you cannot register a trusted key without the Platform's private key.

**Changes to `SigningKeyDTO`:**
```java
public class SigningKeyDTO {
    // ...existing fields...
    private String registrationSignature;  // RSA sig by platform key over canonical payload
    private Set<KeyPermission> permissions; // what this key is allowed to do
}
```

#### Tier 3 — Key-level permissions

Add a `KeyPermission` enum and a `permissions` set on `SigningKeyDTO`:

```java
public enum KeyPermission {
    ENTRY_COMMAND,  // may produce StartCommandDTO / AbortTriggerDTO without JWT
    RESPONSE,       // may produce ExternalTaskResponseTriggerDTO / UserTaskResponseTriggerDTO
    ALL             // unrestricted (engine key, platform key)
}
```

- **Engine key**: registered with `permissions = {ALL}` (or at minimum `{ENTRY_COMMAND}`)
- **Worker keys**: registered with `permissions = {RESPONSE}`
- **Platform RSA key**: registered with `permissions = {ALL}` (or just used for JWT, not Ed25519)

**Authorization logic rewrite** in `EngineAuthorizationService.authorize()`:

Replace the current type-based check:
```java
// CURRENT (broken):
if (requiresJwtAuthorization(trigger)) { ... require JWT ... }
```

With a permission-aware check:
```java
// PROPOSED:
boolean isEntryCommand = trigger instanceof StartCommandDTO || trigger instanceof AbortTriggerDTO;
if (isEntryCommand) {
    if (authHeader != null) {
        return authorizeViaJwt(authHeader, trigger);  // external client path
    }
    if (sigHeader != null && triggerEnvelope.signatureVerified()) {
        SigningKeyDTO signerKey = lookupSigningKey(triggerEnvelope.signatureKeyId());
        if (signerKey != null && signerKey.hasPermission(KeyPermission.ENTRY_COMMAND)) {
            return authorizeViaEd25519(sigHeader, triggerEnvelope);  // engine-originated path
        }
    }
    throw new AuthorizationTokenException("Entry command requires JWT or authorized Ed25519 signer");
}
```

This means:
- External clients MUST still provide JWT for start/abort commands.
- The engine (whose key has `ENTRY_COMMAND` permission) can start/abort via Ed25519 alone.
- A worker key (with only `RESPONSE` permission) **cannot** produce entry commands even if it signs them.
- The permission is anchored to the countersigned key registration — it cannot be spoofed.

### 13.5 Implementation Steps

| Step | Files to modify | Description |
|---|---|---|
| **1. Add `KeyPermission` enum** | `taktx-shared/.../dto/KeyPermission.java` (new) | `ENTRY_COMMAND`, `RESPONSE`, `ALL` |
| **2. Extend `SigningKeyDTO`** | `taktx-shared/.../dto/SigningKeyDTO.java` | Add `registrationSignature` (String), `permissions` (Set\<KeyPermission\>), `hasPermission()` helper |
| **3. Extend `SigningKeyRegistrar`** | `taktx-shared/.../security/SigningKeyRegistrar.java` | Add overloads accepting `registrationSignature` and `permissions`; add static method to compute canonical payload for signing |
| **4. Activate bootstrap root key** | `taktx-engine/.../security/PublicKeyProvider.java`, new `BootstrapTrustAnchor` class | Read `TAKTX_PLATFORM_PUBLIC_KEY` env var; verify `registrationSignature` on every key lookup; reject keys with invalid/missing signature when anchor is configured |
| **5. Rewrite authorization logic** | `taktx-engine/.../security/EngineAuthorizationService.java` | Replace `requiresJwtAuthorization()` with permission-aware logic per §13.4 |
| **6. Countersign engine key** | `taktx-engine/.../security/MessageSigningService.java` | Engine key publication must include `registrationSignature` and `permissions={ALL}`. Requires either: (a) platform pre-signs the engine's key registration blob (provided via env var), or (b) platform pre-registers the engine's key before engine starts |
| **7. Update worker key publication** | `taktx-shared/.../security/SigningKeyRegistrar.java`, `taktx-client/.../TaktXClient.java` | Worker keys published with `permissions={RESPONSE}` and countersignature (if root anchor is active) |
| **8. Update tests** | Various test files | Unit tests for new permission checks, countersignature verification, backward compatibility (no root anchor configured) |
| **9. Update documentation** | `docs/security.md` | Complete rewrite reflecting three-tier model |

### 13.6 Open Design Decisions

1. **How does the engine get its key countersigned?**
   - **Option A**: Platform pre-signs the engine's key registration blob and provides it via environment variable (e.g. `TAKTX_ENGINE_KEY_REGISTRATION_SIGNATURE`). Engine includes this in the `SigningKeyDTO` when publishing.
   - **Option B**: Platform pre-registers the engine's key to `taktx-signing-keys` before the engine starts (e.g. during deployment/provisioning).
   - Recommendation: **Option A** — self-contained, no ordering dependency on platform startup.

2. **How do workers get their keys countersigned?**
   - Workers request countersignature from Platform Service (new API endpoint), or
   - Deployment tooling pre-computes the signature and provides it via env var / config.

3. **Backward compatibility**: When `TAKTX_PLATFORM_PUBLIC_KEY` is not configured, countersignature verification is skipped and the system behaves exactly as today. This preserves community/standalone deployments.

4. **`trustedKeyIds` in `GlobalConfigurationDTO`**: Currently an empty list, never used. Consider deprecating or repurposing as an additional allowlist layer.

### 13.7 Key File Reference for Implementation

| File | Path (relative to root) | Role in fix |
|---|---|---|
| `EngineAuthorizationService.java` | `taktx-engine/src/main/java/io/taktx/engine/security/` | Rewrite `requiresJwtAuthorization()` → permission-aware logic |
| `SigningKeyDTO.java` | `taktx-shared/src/main/java/io/taktx/dto/` | Add `registrationSignature`, `permissions`, `hasPermission()` |
| `SigningKeyRegistrar.java` | `taktx-shared/src/main/java/io/taktx/security/` | Add countersignature + permissions parameters |
| `MessageSigningService.java` | `taktx-engine/src/main/java/io/taktx/engine/security/` | Countersigned engine key publication |
| `PublicKeyProvider.java` | `taktx-engine/src/main/java/io/taktx/engine/security/` | Verify countersignature on key lookup |
| `Forwarder.java` | `taktx-engine/src/main/java/io/taktx/engine/pi/` | No code change needed — already signs via `SigningSerializer` |
| `ProcessInstanceTriggerEnvelopeDeserializer.java` | `taktx-engine/src/main/java/io/taktx/engine/pi/` | No change — signature verification already works |
| `TopologyProducer.java` | `taktx-engine/src/main/java/io/taktx/engine/generic/` | No change — already uses `SigningSerializer` for engine outbound |
| `GlobalConfigurationDTO.java` | `taktx-shared/src/main/java/io/taktx/dto/` | Potential `trustedKeyIds` deprecation |
| `CommandTrustMetadataDTO.java` | `taktx-shared/src/main/java/io/taktx/dto/` | May need new `CommandTrustVerificationResult` value |
| `SignalProcessor.java` | `taktx-engine/src/main/java/io/taktx/engine/pd/` | No change needed — already signs via topology |
| `MessageEventProcessor.java` | `taktx-engine/src/main/java/io/taktx/engine/pd/` | No change needed |
| `ProcessDefinitionActivationProcessor.java` | `taktx-engine/src/main/java/io/taktx/engine/pd/` | No change needed |
| `ProcessInstanceProducer.java` | `taktx-client/src/main/java/io/taktx/client/` | No change for JWT path; worker key publication needs permissions |
| `docs/security.md` | `docs/` | Full rewrite |
