# TaktX v1.0 — Full Protobuf Migration Plan

**Status:** Complete — historical migration record and protocol-reference document, not an active tracker. PROTO-1.1 ✅ PROTO-1.2 ✅ PROTO-1.3 ✅ PROTO-1.4 ✅ PROTO-1.5 ✅ PROTO-2.1 ✅ PROTO-2.2 ✅ PROTO-2.3 ✅ PROTO-3.1 ✅ PROTO-3.2 ✅ PROTO-3.3 ✅ PROTO-4.1 ✅ PROTO-4.2 ✅ PROTO-4.3 ✅ PROTO-4.4 ✅ PROTO-4.5 ✅ PROTO-4.6 ✅ PROTO-4.7 ✅ PROTO-4.8 ✅ PROTO-4.9 ✅ PROTO-4.10 ✅ PROTO-4.11 ✅ PROTO-4.12 ✅ PROTO-5.1 ✅ PROTO-5.2 ✅ PROTO-5.3 ✅ PROTO-5.4 ✅ PROTO-6.1 ✅ PROTO-6.2 ✅ PROTO-6.3 ✅ PROTO-6.4 ✅  
**Target release:** v1.0.0 (major, beta → stable)  
**Decision context:** Replace all CBOR+Jackson serialization with `protobuf-java-lite`.  
Remove Jackson entirely from `taktx-shared` and `taktx-client`.  
Eliminate the positional-array brittleness that blocks schema evolution.

**Current use of this document:** keep as the historical implementation record for the protobuf
migration and as a reference for non-obvious protocol decisions such as field-number permanence,
header-name shortening, and the explicit rule that range-queryable Kafka Streams store keys remain
raw binary rather than protobuf. For active work, prefer the live `.proto` files under
`taktx-shared/src/main/proto/`, the current code/tests, and `scripts/check_proto_field_numbers.py`.

---

## Goals

- Every Kafka record is encoded with Protobuf.
- Zero Jackson dependency in `taktx-shared` and `taktx-client`; Jackson remains only in the Quarkus REST layer of `taktx-engine`.
- Schema evolution is safe by contract (field numbers are permanent, adding fields is always backward-compatible).
- Wire messages are smaller than or equal to the current CBOR-array format for typical payloads.
- All existing behaviour is covered by tests at unit and integration level.

---

## Governing principles

1. **Field numbers are permanent.** Once a `.proto` field number is assigned it must never be reused. Removed fields become `reserved`.
2. **Integers are `sint64` or `uint64` by default**, not `int64`, wherever values can span 0. Zigzag varint encoding is substantially smaller for small absolute values.
3. **UUIDs are `bytes` (16 raw bytes)**, never `string`. Saves ~20 bytes per UUID per message.
4. **`VariableValue` over `google.protobuf.Struct`.** Custom recursive type using `sint64` for integer variables instead of `double`. Never encode what is not there — proto default values are omitted.
5. **`protobuf-java-lite` everywhere.** The full runtime is not needed; lite is smaller (~175 KB) and GraalVM-native-friendly.
6. **Tests are acceptance criteria.** Every backlog item has an explicit testing requirement; items without it are not done.
7. **Kafka state-store KEYS are never protobuf.** Protobuf bytes are not byte-lexicographically ordered; range queries on protobuf-serialized keys would silently return wrong results. All range-queryable store keys keep their explicit big-endian binary layout. See E4.12 for the full treatment.
8. **Do not introduce new Sonar warnings; fix easy wins as you go.** Every story must leave the Sonar issue count for touched files the same or lower than before the story started. Fix obvious issues encountered while editing a file (e.g. duplicate string literals → constant, magic numbers → named constant, missing `@Override`, unnecessary `null` checks after safe API calls). Do **not** start a heavy refactoring solely to resolve a Sonar issue — if a fix requires touching more than ~5 lines unrelated to the current story, raise it as a separate issue instead.
9. **Each story is implemented in a fresh AI session.** The plan document is the single source of truth. Begin every implementation session by re-reading the relevant story and its dependencies from this file. Do not rely on context carried over from a planning session — it will be stale or truncated. Mark acceptance criteria `[x]` as each is verified; commit the updated plan alongside the implementation so progress is always visible in version control.

---

## Epic Index

| Epic | Title | Items |
|---|---|---|
| E1 | Proto Schema Design & Build Infrastructure | 5 stories |
| E2 | Variable System Replacement | 3 stories |
| E3 | Kafka Serdes Layer | 3 stories |
| E4 | Engine Migration | 12 stories |
| E5 | Client Libraries Migration | 4 stories |
| E6 | Test Hardening & Regression Guard | 4 stories |

---

## E1 — Proto Schema Design & Build Infrastructure

> This epic gates all other work. No implementation starts until E1.1 is reviewed and approved.

---

### PROTO-1.1 — Design all `.proto` schemas

**Description**  
Author all `.proto` files under `taktx-shared/src/main/proto/`. These become the canonical wire contracts for v1.0.

**Files to create**

**Important constraint: key types for range-queryable stores are NOT proto messages.**  
`FlowNodeInstanceKeyDTO`, `VariableKeyDTO`, `ProcessDefinitionKey` (as a store key), `SignalDefinitionSubscriptionKeyDTO`, and `SignalInstanceSubscriptionKeyDTO` all double as Kafka Streams state-store keys that are range-scanned. Protobuf bytes are not byte-lexicographically ordered, so these types must keep an explicit big-endian binary serialization format. They do **not** get `.proto` definitions for the key path. They appear as embedded fields inside value messages (see `common.proto`) using their proto equivalent.

| File | Covers |
|---|---|
| `variables.proto` | `VariableValue`, `VarMap`, `VarList` |
| `common.proto` | `Uuid` (bytes 16), `ProcessDefinitionKeyValue` (proto equivalent for embedding in messages), `DefinitionsKey`, `DmnDefinitionKey`, `ExecutionState` enum |
| `process_instance.proto` | `ProcessInstanceMessage`, `ScopeMessage`, `SubscriptionMessage` and all subscription sub-types (8 concrete types via `oneof`), `IoVariableMappingMessage`, `InputOutputMappingMessage` |
| `flow_node_instance.proto` | `FlowNodeInstanceEnvelope` with `oneof payload` covering all 21 concrete types; base fields shared via a `FlowNodeInstanceBase` embedded message |
| `instance_update.proto` | `InstanceUpdateEnvelope` with `oneof` for `FlowNodeInstanceUpdateMessage` and `ProcessInstanceUpdateMessage`; `CommandTrustMetadataMessage` |
| `process_instance_trigger.proto` | `ProcessInstanceTriggerEnvelope` with `oneof` for 9 trigger types incl. `StartCommandMessage`, `ContinueFlowElementTriggerMessage`, `UserTaskResponseTriggerMessage`, `ExternalTaskResponseTriggerMessage`, `SetVariableTriggerMessage`, `AbortTriggerMessage`, `EventSignalTriggerMessage`, `ExternalTaskTriggerMessage`, `StartFlowElementTriggerMessage` |
| `definitions.proto` | `ParsedDefinitionsMessage`, `ProcessMessage` and full `BaseElementEnvelope` `oneof` covering all 27 BPMN element types; `FlowElementsMessage`, `SequenceFlowMessage`, `LoopCharacteristicsMessage`, `AssignmentDefinitionMessage`, `PriorityDefinitionMessage`, `TaskScheduleMessage` |
| `dmn_definitions.proto` | `DmnDefinitionMessage`, `DmnDecisionTableMessage`, `DmnRuleMessage`, `DmnInputClauseMessage`, `DmnOutputClauseMessage`, enums for hit policy / collect operator |
| `message_event.proto` | `MessageEventEnvelope` with `oneof` for 6 message event types; `MessageEventKeyMessage` |
| `signals.proto` | `SignalEnvelope` with `oneof` for 5 signal types |
| `user_task.proto` | `UserTaskTriggerMessage`, `UserTaskResponseResultMessage` |
| `schedule.proto` | `ScheduleKeyMessage`, `MessageScheduleEnvelope`, `TaskScheduleMessage`, `TimeBucketMessage` |
| `configuration.proto` | `GlobalConfigurationMessage`, `ConfigurationEventMessage` |
| `signing_key.proto` | `SigningKeyMessage` with `KeyStatus` and `KeyRole` enums |
| `topic_meta.proto` | `TopicMetaMessage` |
| `dlq.proto` | `DlqEnvelopeMessage`, `DlqReplayCommandMessage`, `DlqReplayResultMessage`, `DlqLineageMessage`, `DlqEntryMessage`, `DlqSeverity`/`DlqReasonCode`/`DlqCaptureStage` enums |
| `process_definition.proto` | `ProcessDefinitionMessage`, `ProcessDefinitionActivationMessage`, `XmlDefinitionsMessage`, `XmlDmnDefinitionsMessage` |

**Key design decisions to capture in each file**
- Every `oneof` variant gets a comment stating the equivalent Java `TypeIdResolver` short code it replaces and the field number that is locked for that type.
- All `UUID` values use the `Uuid` message from `common.proto` (two `fixed64` fields: `high` and `low`).
- All `string` BPMN element IDs remain `string` (already short, no gain from bytes).
- FEEL expression strings remain `string`.

**Acceptance criteria**
- [x] All `.proto` files compile without warnings via `protoc`.
- [x] Every existing DTO class in `taktx-shared/src/main/java/io/taktx/dto` has a 1:1 corresponding proto message (ignoring abstract base classes which become embedded `*Base` messages or are flattened).
- [x] Design review sign-off from maintainer before PROTO-1.2 starts.
- [x] No new Sonar issues introduced in any `.proto` file or hand-authored companion classes created during this story.

**Status:** ✅ Complete  
**Dependencies:** none  
**Estimate:** 4 days

---

### PROTO-1.2 — Configure Protobuf build toolchain in `taktx-shared`

**Status:** ✅ Complete

**Description**  
Add `com.google.protobuf` Gradle plugin to `taktx-shared/build.gradle.kts`. Configure `protobuf-java-lite` as the runtime. Wire source generation into the Java compile task.

**Changes**

- `gradle/libs.versions.toml`: add `protobuf = "4.x.y"` version entry; add `protobuf-java-lite` library entry; add `protobuf-gradle-plugin` plugin entry.
- `taktx-shared/build.gradle.kts`: apply `com.google.protobuf` plugin; configure `protobuf { protoc { artifact = ... } }` and `generateProtoTasks { all().forEach { it.builtins { id("java") { option("lite") } } } }`.
- `gradle.lockfile` + `settings-gradle.lockfile`: regenerate with `./gradlew dependencies --write-locks`.
- `taktx-shared/build.gradle.kts`: remove `jackson-cbor` and `jackson-datatype-jsr310` from `dependencies` block; keep `jackson-databind` only as a `testImplementation` scope if any existing shared tests still need it (to be removed fully in PROTO-1.3).
- Add `src/main/proto/` directory to version control.
- **Note:** `TaktUUIDSerde`, `TaktCompositeUUIDSerde`, `TaktLongListSerializer`, `TaktLongListDeserializer` in `taktx-shared/src/main/java/io/taktx/util/` are **kept** — they are used as raw binary key serializers for range-queryable stores and are independent of both CBOR and protobuf. Their Jackson `extends JsonSerializer<>` inheritance is removed in PROTO-4.12.

**Acceptance criteria**
- [x] `./gradlew :taktx-shared:build` succeeds and generates Java sources from all `.proto` files in `build/generated/source/proto/`.
- [x] No `jackson-cbor` or `jackson-datatype-jsr310` appear in `taktx-shared` or `taktx-client` compile classpath.
- [x] Dependency lock file is committed and passes `--strict` lock mode.

**Status:** ✅ Complete  
**Dependencies:** PROTO-1.1  
**Estimate:** 0.5 day

---

### PROTO-1.3 — Remove Jackson from `taktx-shared` and `taktx-client`

**Status:** ✅ Complete (2026-05-19)

**Description**  
Complete Jackson removal from the two public library modules. This includes removing Jackson-only annotations and infrastructure (`@JsonFormat`, `@JsonTypeInfo`, `@JsonTypeIdResolver`, `@JsonInclude`, `@JsonIgnore`, shared JSON serdes, object-mapper wiring, and the old resolver classes), switching client/shared serialization to protobuf-backed implementations from E2/E3, and removing Quarkus `@RegisterForReflection` usage that was only needed for the old Jackson path.

Public `io.taktx.dto.*` types remain in place as the compatibility API surface for now, but they are no longer Jackson-backed and no Jackson runtime dependency remains in either module.

**Scope**
- Delete `taktx-shared/src/main/java/io/taktx/serdes/JsonSerializer.java`
- Delete `taktx-shared/src/main/java/io/taktx/serdes/JsonDeserializer.java`
- Delete `taktx-shared/src/main/java/io/taktx/serdes/FaultTolerantJsonDeserializer.java`
- Delete `taktx-shared/src/main/java/io/taktx/serdes/SigningSerializer.java` (replaced in E3)
- Delete all 8 `*TypeIdResolver.java` files in `taktx-shared/src/main/java/io/taktx/`
- Keep the public `io.taktx.dto.*` compatibility types for now, but remove all Jackson coupling from them and from the surrounding serializers/deserializers
- Remove all Jackson `api`/`implementation` declarations from `taktx-shared/build.gradle.kts` (except `jackson-databind` which stays as `testImplementation` only until tests are updated in E6)
- Remove `jackson-cbor`, `jackson-datatype-jsr310` from `taktx-client/build.gradle.kts`
- Remove `@RegisterForReflection` annotations (Quarkus-specific — proto-generated classes do not need them; reflection is registered differently in E4.11)
- **Do NOT delete** `taktx-shared/src/main/java/io/taktx/util/Takt*Serializer.java`, `Takt*Deserializer.java`, `Takt*Serde.java` — these are raw binary key serializers required for range queries. Their Jackson inheritance is removed in PROTO-4.12 instead.

**Note:** This item should be done as a single commit after E2 and E3 are complete so the build does not break mid-way.

**Acceptance criteria**
- [x] `./gradlew :taktx-shared:build :taktx-client:build` succeeds.
- [x] `grep -r "CBORFactory\|JsonFormat\|JsonTypeInfo\|TypeIdResolver\|ObjectMapper" taktx-shared/src/main taktx-client/src/main` returns zero results.
- [x] No Jackson runtime JARs appear in `taktx-shared` or `taktx-client` runtime classpaths.

**Dependencies:** E2 (complete), E3 (complete)  
**Estimate:** 1 day

---

### PROTO-1.4 — Remove `@JsonFormat(shape=ARRAY)` workaround documentation

**Status:** ✅ Complete (2026-05-19)

**Description**  
Delete the old inline notes that existed only to explain legacy CBOR positional-array field ordering. Replace them with brief protobuf-era references to the relevant `.proto` message and field numbers.

**Acceptance criteria**
- [x] Search for the old inline CBOR positional-array workaround notes in source files returns zero results.
- [x] Each remaining serialization-structure comment now points to the relevant `.proto` file and field number.

**Dependencies:** PROTO-1.3  
**Estimate:** 0.5 day

---

### PROTO-1.5 — Shorten Kafka record header names

**Status:** ✅ Complete (2026-05-19)

**Description**  
Kafka record headers carry their name as raw UTF-8 bytes on every message. The old `X-`-prefixed names are an HTTP convention with no meaning in the Kafka context. Replacing them with concise names saves bytes on every record that carries them.

| Old name | New name | Bytes saved per record |
|---|---|---|
| `X-TaktX-Signature` (18 B) | `tx-sig` (6 B) | 12 |
| `X-TaktX-Authorization` (22 B) | `tx-auth` (7 B) | 15 |
| `X-DLQ-Reason-Hint` (24 B) | `dlq-hint` (8 B) | 16 |
| `X-DLQ-Reason-Text` (24 B) | `dlq-text` (8 B) | 16 |
| `X-DLQ-Capture-Stage` (26 B) | `dlq-stage` (9 B) | 17 |
| `X-DLQ-Lineage-Ref` (18 B) | `dlq-lin` (7 B) | 11 |
| `X-DLQ-Correction-Id` (20 B) | `dlq-cid` (7 B) | 13 |
| `X-DLQ-Source-Offset` (20 B) | `dlq-off` (7 B) | 13 |

**Scope** — all changes are already implemented as of the v1.0 planning phase. This story tracks that they are verified complete:

- `Constants.java`: `HEADER_ENGINE_SIGNATURE = "tx-sig"`, `HEADER_AUTHORIZATION = "tx-auth"`.
- `DlqHeaders.java`: `REASON_HINT = "dlq-hint"`, `REASON_TEXT = "dlq-text"`, `CAPTURE_STAGE = "dlq-stage"`.
- `DlqReplayProcessor.java`: `HEADER_DLQ_LINEAGE_REF = "dlq-lin"`, `HEADER_DLQ_CORRECTION_ID = "dlq-cid"`, `HEADER_DLQ_SOURCE_OFFSET = "dlq-off"`. Local duplicate of the engine-signature constant replaced with `Constants.HEADER_ENGINE_SIGNATURE` reference.
- All error-message strings that embedded the literal header name are rewritten to use `Constants.HEADER_ENGINE_SIGNATURE` via string concatenation — so the message stays accurate when the constant value changes.
- `TopicMetaRequestIngressProcessor.reasonCodeForAuthorizationFailure()` lowercase prefix match updated from `"missing required x-taktx-signature header"` to `"missing required tx-sig header"`.
- All test files: literal header name strings (`headers.add(...)`, `lastHeader(...)`, `containsKey(...)`, `hasMessageContaining(...)`) updated to the new values.

**Acceptance criteria**
- [x] `grep -r '"X-TaktX-\|"X-DLQ-' src/` returns zero results in `taktx-shared`, `taktx-engine`, `taktx-client`. Verified on 2026-05-19 after cleaning remaining source comments/Javadocs.
- [x] All unit tests pass: `./gradlew :taktx-shared:test :taktx-engine:test :taktx-client:test`. Verified on 2026-05-19 after fixing protobuf-era test-fixture compatibility code and adding the missing JJWT runtime dependencies to `taktx-engine`.
- [x] Security integration test passes: `./gradlew :taktx-engine:securityIntegrationTest`. Verified on 2026-05-19.

**External coordination note (non-blocking for repository completion)**
- Consumer applications (DLQ console, monitoring) must use the new header names before a v1.0 rollout.

**Note for external consumers:** Any monitoring dashboard, DLQ console, or log-parsing rule that filters on legacy `X-TaktX-*` or `X-DLQ-*` header names must be updated before deploying v1.0. This is expected given the major version bump.

**Dependencies:** none (standalone rename, already complete)  
**Estimate:** 0 days (already done)

---

> Replaces `VariablesDTO` (`Map<String, JsonNode>`) and all `JsonNode` variable usages with the `VariableValue` proto-based variable type.

---

### PROTO-2.1 — Implement `VariableValue` proto type and `Variables` helper

**Status:** ✅ Complete

**Description**  
`variables.proto` defines:

```protobuf
syntax = "proto3";
package io.taktx.proto;

message VariableValue {
  oneof kind {
    string    string_val  = 1;
    sint64    long_val    = 2;
    double    double_val  = 3;
    bool      bool_val    = 4;
    bytes     bytes_val   = 5;   // opaque caller-managed blob
    VarMap    map_val     = 6;
    VarList   list_val    = 7;
    NullValue null_val    = 8;
  }
}

enum NullValue { NULL_VALUE = 0; }
message VarMap  { map<string, VariableValue> fields = 1; }
message VarList { repeated VariableValue     values = 1; }
```

Additionally, author a **non-generated** helper class `io.taktx.variables.Variables` in `taktx-shared/src/main/java/` providing:

```java
public final class Variables {
    public static VariableValue of(long value) { ... }
    public static VariableValue of(String value) { ... }
    public static VariableValue of(double value) { ... }
    public static VariableValue of(boolean value) { ... }
    public static VariableValue nullValue() { ... }
    public static VariableValue of(Object javaValue) { ... }  // converts Map, List, primitives
    public static Map<String, VariableValue> map(String k1, Object v1, ...) { ... }  // overloads up to 5 pairs
    public static Object toJavaObject(VariableValue value) { ... }  // for FEEL/DMN adapters
    public static Map<String, Object> toJavaMap(VarMap varMap) { ... }
}
```

**Acceptance criteria**
- [x] Unit test: `Variables.of(100L)` → serializes to ≤ 3 bytes (field tag + varint).
- [x] Unit test: `Variables.of("hello")` round-trips correctly.
- [x] Unit test: nested `VarMap` containing `VarList` containing `sint64` round-trips correctly.
- [x] Unit test: `Variables.toJavaObject(Variables.of(42L))` returns `Long 42`.
- [x] Unit test: `Variables.of(Object)` handles `java.util.Map<String,Object>`, `java.util.List<Object>`, `String`, `Long`, `Double`, `Boolean`, `null`.
- [x] Size assertion test: a map of 3 typical variables (`{"amount": 100, "name": "Alice", "active": true}`) encodes in ≤ 50 bytes (revised from 40: proto map-entry overhead accounts for ~46 bytes actual for these key lengths).
- [x] No new Sonar issues in `Variables.java` or `variables.proto`.

**Status:** ✅ Complete  
**Dependencies:** PROTO-1.1, PROTO-1.2  
**Estimate:** 1.5 days

---

### PROTO-2.2 — Replace `VariablesDTO` references in the engine model layer

**Status:** ✅ Complete

**Description**  
The engine's internal `VariableScope` model and 35 files that reference `VariablesDTO`/`JsonNode` must be updated to use `Map<String, VariableValue>` (or a thin `VariableScope` wrapper around it).

Key files:
- `engine/pi/model/VariableScope` (and all subclasses) — replace `Map<String, JsonNode>` internals with `Map<String, VariableValue>`
- All scope merge / propagation logic in `engine/pi/scope/`
- I/O mapping evaluation in task processors
- `ActivityInstanceDTO` → `ActivityInstanceMessage` field `inputElement`/`outputElement` become `VariableValue`

**Progress update (2026-05-18)**
- ✅ `VariableScope` now stores `Map<String, VariableValue>` internally and the Kafka variable state store value type has been migrated to `VariableValue`.
- ✅ Core engine processors now operate on proto-backed values internally, including I/O mapping, activity iteration handling, multi-instance processing, DMN result propagation, FEEL-based script/business-rule/gateway/task metadata evaluation, timer/message/signal resolution, and call activity target resolution.
- ✅ `ActivityInstance.inputElement` / `outputElement` are now stored as `VariableValue` in the engine model.
- ✅ The legacy engine-local bridge was removed from `taktx-engine`; remaining DTO/Jackson conversion is now isolated in shared helpers used only at module boundaries.
- ✅ Verified on 2026-05-18: `./gradlew :taktx-engine:compileJava :taktx-engine:compileTestJava --console=plain` passes.
- ✅ Verified on 2026-05-18: focused regression tests pass for `IoMappingProcessorTest`, `ActivityInstanceProcessorTest`, `CallActivityInstanceProcessorTest`, `SubProcessInstanceProcessorTest`, `SubscriptionsTest`, `FeelExpressionHandlerImplTest`, and `DmnEvaluatorImplTest`.
- ✅ Verified on 2026-05-18: additional impacted processor tests pass for `MultiInstanceProcessorTest`, `ThrowEventInstanceProcessorTest`, `GatewayInstanceProcessorTest`, `GatewayFeelNullHandlingTest`, `UserTaskFeelNullHandlingSimpleTest`, and `UserTaskFeelNullHandlingTest`.

**Acceptance criteria**
- [x] `grep -r "VariablesDTO\|JsonNode" taktx-engine/src/main/java` returns zero results.
- [x] All existing unit tests in `taktx-engine/src/test` that cover variable propagation and I/O mapping still pass.

**Dependencies:** PROTO-2.1  
**Estimate:** 2 days

---

### PROTO-2.3 — FEEL engine adapter: `VariableValue` ↔ FEEL context

**Status:** ✅ Complete

**Description**  
`FeelExpressionHandlerImpl` must be rewritten to work without `JsonNode` and `ObjectMapper`. The FEEL engine (`camunda-feel`) takes and returns Scala `Object` values. The adapter layer converts:

- `VariableValue` → plain Java `Object` (via `Variables.toJavaObject()`) when building the FEEL context.
- FEEL result `Object` → `VariableValue` (via `Variables.of(Object)`) when returning the expression result .

Replace the `ObjectMapper`-based conversions:
- `objectMapper.treeToValue(variables.get(name), Object.class)` → `Variables.toJavaObject(scope.get(name))`
- `objectMapper.valueToTree(expressionResult)` → `Variables.of(expressionResult)`

Remove the `ObjectMapper` constructor parameter and field from `FeelExpressionHandlerImpl`.

**Current note (2026-05-18)**
- `FeelExpressionHandlerImpl` already evaluates expressions internally as `VariableValue` and uses `Variables.toJavaObject(...)` for FEEL context construction.
- The engine-facing FEEL/DMN APIs now return `VariableValue` directly; legacy `JsonNode` engine-main adapters were removed during PROTO-2.2 cleanup.
- `FeelExpressionHandlerImpl` no longer carries the unused `ObjectMapper` constructor dependency.
- Verified on 2026-05-18: `FeelExpressionHandlerImplTest` covers string, integer arithmetic, map, range, and array access with `VariableValue` assertions.
- Verified on 2026-05-18: the existing script-task BPMN integration test `ScriptTaskTest` passes.

**Acceptance criteria**
- [x] Unit test: `FeelExpressionHandlerImpl.processFeelExpression("= amount + 10", scope)` where `amount = 90L` returns a `VariableValue` with `long_val = 100`.
- [x] Unit test: FEEL expression returning a string returns `VariableValue` with `string_val`.
- [x] Unit test: FEEL expression returning a map returns `VariableValue` with `map_val`.
- [x] Integration test in the existing BPMN test suite for a process with a Script Task using FEEL expressions still passes.
- [x] No `ObjectMapper` reference inside `FeelExpressionHandlerImpl`.

**Dependencies:** PROTO-2.1, PROTO-2.2  
**Estimate:** 1 day

---

## E3 — Kafka Serdes Layer

> Replaces all Jackson-based Kafka serializers and deserializers with Protobuf-native implementations.

---

### PROTO-3.1 — Implement `ProtoSerializer` and `ProtoDeserializer`

**Status:** ✅ Complete

**Description**  
Create generic Kafka `Serializer<T extends MessageLite>` and `Deserializer<T extends MessageLite>` in `taktx-shared/src/main/java/io/taktx/serdes/`.

```java
public class ProtoSerializer<T extends MessageLite> implements Serializer<T> {
    public byte[] serialize(String topic, T data) {
        return data == null ? null : data.toByteArray();
    }
}

public abstract class ProtoDeserializer<T extends MessageLite>
    implements Deserializer<T> {
    protected abstract Parser<T> parser();
    public T deserialize(String topic, byte[] data) {
        return data == null ? null : parser().parseFrom(data);
    }
}
```

Create concrete subclasses for each top-level envelope type (e.g., `ProcessInstanceTriggerDeserializer`, `InstanceUpdateDeserializer`, etc.).

**Progress update (2026-05-18)**
- ✅ Added generic shared-module `ProtoSerializer` / `ProtoDeserializer` implementations using `MessageLite#toByteArray()` and `Parser#parseFrom(...)`.
- ✅ Added concrete shared deserializers for the current top-level proto message families needed by the migration path, including process-instance triggers, instance updates, flow-node instances, definitions, message events, signals, schedules, topic metadata, DLQ messages, signing keys, DMN definitions, and process definitions.
- ✅ Verified on 2026-05-18: new `ProtoSerdesTest` round-trips 19 top-level proto message families and covers tombstone (`null`) serializer/deserializer behaviour.
- ✅ Verified on 2026-05-18: corrupt bytes currently surface as a `SerializationException` with `InvalidProtocolBufferException` cause, which is the raw decode failure signal that PROTO-3.2 will convert into `DeserializationResult.failure(...)`.
- ✅ Verified on 2026-05-18: `./gradlew :taktx-shared:test --tests io.taktx.serdes.ProtoSerdesTest --console=plain` passes.
- ✅ Verified on 2026-05-18: `./gradlew :taktx-shared:test --console=plain` passes.

**Acceptance criteria**
- [x] Unit test: `ProtoSerializer` + `ProtoDeserializer` round-trip for each of the 10 top-level envelope types.
- [x] Unit test: `null` input serializes to `null`; `null` input deserializes to `null`.
- [x] Unit test: corrupted bytes surface a `SerializationException` with `InvalidProtocolBufferException` cause in the raw `ProtoDeserializer`; wrapping into `DeserializationResult.failure(...)` is covered in PROTO-3.2.
- [x] No new Sonar issues in `ProtoSerializer.java` or `ProtoDeserializer.java`.

**Dependencies:** PROTO-1.2  
**Estimate:** 0.5 day

---

### PROTO-3.2 — Implement `FaultTolerantProtoDeserializer` with Ed25519 signing support

**Status:** ✅ Complete

**Description**  
Port the `FaultTolerantJsonDeserializer` logic (body decode + optional Ed25519 header verification) to a proto-based equivalent `FaultTolerantProtoDeserializer<T extends MessageLite>`. The signing verification already works on raw `byte[]` so the Ed25519 path (`tryVerifySignature`, `resolvePublicKey`, `EngineSigningKeysHolder`) is unchanged. Only the body decode line changes:

```java
// Before:
T value = OBJECT_MAPPER.readValue(data, clazz);
// After:
T value = parser().parseFrom(data);
```

Retain the `DeserializationResult<T>` wrapper type with `success`, `failure`, and `bodyDecodedWithError` states — these are protocol-level, not format-level.

**Progress update (2026-05-18)**
- ✅ Added shared-module `FaultTolerantProtoDeserializer<T extends MessageLite>` with protobuf parsing via `Parser#parseFrom(...)` and the same Ed25519 verification/configuration flow as the existing JSON variant.
- ✅ Added focused unit coverage in `taktx-shared/src/test/java/io/taktx/serdes/FaultTolerantProtoDeserializerTest.java` for valid signed payloads, corrupt bytes, invalid signatures, required-signature-without-header, and runtime signing flag toggling.
- ✅ Verified on 2026-05-18: `./gradlew :taktx-shared:test --tests io.taktx.serdes.FaultTolerantProtoDeserializerTest --console=plain` passes.
- ✅ Verified on 2026-05-18: `./gradlew :taktx-shared:test --console=plain` passes after the new deserializer landed.
- ✅ Wired worker-facing external-task and user-task topics to protobuf DTO serdes in `TopologyProducer`, shared DTO↔proto mappers/serdes, and the client fault-tolerant worker-trigger deserializers.
- ✅ Updated the raw engine test-fixture external-task consumer to the protobuf DTO deserializer path.
- ✅ Added focused round-trip coverage in `WorkerTriggerProtoSerdesTest` and client wrapper coverage in `FaultTolerantWorkerTriggerProtoDeserializerTest`.
- ✅ Verified on 2026-05-18: `./gradlew :taktx-client:test --tests io.taktx.client.serdes.FaultTolerantWorkerTriggerProtoDeserializerTest --tests io.taktx.client.serdes.MiscSerdesTest --tests io.taktx.client.serdes.ExternalTaskTriggerDeserializerTest --console=plain` passes.
- ✅ Verified on 2026-05-18: `./gradlew :taktx-engine:compileJava :taktx-engine:compileTestFixturesJava :taktx-engine:compileSecurityIntegrationTestJava --console=plain` passes with the new worker-trigger serdes in place.
- ✅ Cleared the previously unrelated legacy-topic blockers (`definitions` wire format, runtime-configuration/signing-key timestamp parsing, and process-instance/topic-meta compatibility) that were masking worker-trigger verification.
- ✅ Re-enabled `timerContinuationAfterWorkerResponse_isAttributedToEngine()` in `SecurityIntegrationTest` and aligned it with the current engine trust model (`ENGINE_SIGNED` for engine-authored timer continuations, including in-suite execution).
- ✅ Verified on 2026-05-18: `./gradlew :taktx-engine:securityIntegrationTest --tests "io.taktx.engine.pi.integration.SecurityIntegrationTest" --console=plain` passes with 14 tests, 0 skipped, 0 failures, 0 errors.

**Acceptance criteria**
- [x] Unit test: valid proto bytes → `DeserializationResult.success(value)`.
- [x] Unit test: corrupt bytes → `DeserializationResult.failure(message)` with `null` value.
- [x] Unit test: valid bytes + invalid signature → `DeserializationResult.bodyDecodedWithError(value, errorMsg)`.
- [x] Unit test: `SIGNING_REQUIRED_CONFIG = true` + no signature header → `bodyDecodedWithError`.
- [x] All existing `SecurityIntegrationTest` cases pass against the new deserializer.

**Dependencies:** PROTO-3.1  
**Estimate:** 0.5 day

---

### PROTO-3.3 — Implement `ProtoSigningSerializer` (replace `SigningSerializer`)

**Status:** ✅ Complete

**Description**  
Port `SigningSerializer` to use `message.toByteArray()` instead of Jackson's `OBJECT_MAPPER.writeValueAsBytes(data)`. The signing logic (Ed25519 key lookup, header stamping) is unchanged.

**Progress update (2026-05-18)**
- ✅ Added shared-module `ProtoSigningSerializer<T>` that maps logical values to `MessageLite`, serializes via `message.toByteArray()`, and stamps `tx-sig` using the existing `SigningServiceHolder` flow.
- ✅ Switched the already-migrated protobuf worker-trigger topics in `TopologyProducer` from the legacy generic signing decorator to `ProtoSigningSerializer<>(WorkerTriggerProtoMapper::toProto)` while leaving legacy CBOR/Jackson topics unchanged.
- ✅ Added focused unit coverage in `taktx-shared/src/test/java/io/taktx/serdes/ProtoSigningSerializerTest.java` for exact proto bytes, verifiable `tx-sig`, and tombstone signing semantics.
- ✅ Updated `taktx-client/src/test/java/io/taktx/client/serdes/FaultTolerantWorkerTriggerProtoDeserializerTest.java` so the client-side verification path now consumes records produced by `ProtoSigningSerializer`.
- ✅ Verified on 2026-05-18: `./gradlew :taktx-shared:test :taktx-client:test --console=plain` passes.
- ✅ Verified on 2026-05-18: `./gradlew :taktx-engine:securityIntegrationTest --tests "io.taktx.engine.pi.integration.SecurityIntegrationTest" --console=plain` passes with 14 tests, 0 skipped, 0 failures, 0 errors.

**Acceptance criteria**
- [x] Unit test: serialized bytes are `message.toByteArray()` — verified by parsing back.
- [x] Unit test: `tx-sig` header is present and verifiable when signing is enabled.
- [x] Integration test: engine produces signed records that are verified by `FaultTolerantProtoDeserializer`.

**Dependencies:** PROTO-3.1  
**Estimate:** 0.5 day

---

## E4 — Engine Migration

> Migrates `taktx-engine` (244 source files) to the new proto types, removes all Jackson Kafka serialization, and replaces Quarkus `ObjectMapperSerde` state-store usages.

---

### PROTO-4.1 — Migrate `TopologyProducer` Serde registrations

**Status:** ✅ Complete

**Description**  
`TopologyProducer.java` registers Serdes for every Kafka Streams state store and stream/table. Replace every `ObjectMapperSerde<T>` and `JsonSerializer<T>` / `JsonDeserializer<T>` reference with `ProtoSerializer<T>` / `ProtoDeserializer<T>` counterparts.

Key stores to migrate (based on existing imports):
- `ProcessInstanceDTO` state store → `ProcessInstanceMessageSerde`
- `FlowNodeInstanceDTO` state store → `FlowNodeInstanceEnvelopeSerde`
- `ProcessDefinitionDTO` state store → `ProcessDefinitionMessageSerde`
- `DmnDefinitionDTO` state store → `DmnDefinitionMessageSerde`
- `VariableKeyDTO` / `VariablesDTO` stores → use new proto Serde for **values**; the key Serde (`TaktUUIDSerde` + `TaktLongListSerializer`) is **unchanged** (see PROTO-4.12)
- All other stores listed in `Stores.java`

**Rule for every store:** the **value** Serde becomes `ProtoSerializer`/`ProtoDeserializer`. The **key** Serde stays as the existing raw binary Serde if that store is ever range-scanned; only use proto for key Serdes of stores accessed by exact key lookup only.

**Progress update (2026-05-19)**
- ✅ Replaced the last `TopologyProducer` `ObjectMapperSerde` registrations with protobuf-backed equivalents for `DefinitionMessageSubscriptions`, `CorrelationMessageSubscriptions`, `DmnDefinitionKey`, `ProcessInstanceDTO`, and the two internal version-by-hash stores.
- ✅ Added the missing protobuf surface needed to close the gap cleanly: `DmnDefinitionKeyMessage`, `HashVersionMapMessage`, and store-wrapper messages for definition/correlation message subscriptions.
- ✅ Added shared DTO↔proto mapping/deserialization support for `ProcessInstanceDTO` and `DmnDefinitionKey`, plus client-side DMN key deserialization for the canonical protobuf format.
- ✅ Removed the dead `START_COMMAND_SERDE` branch from `TopologyProducer` and verified `grep "ObjectMapperSerde" taktx-engine/src/main/java` now returns zero results.
- ✅ Verified locally with `./gradlew :taktx-shared:test --tests "*ProcessInstanceProtoMapperTest*" --tests "*ProtoSerdesTest*"`, `./gradlew :taktx-client:test --tests "*DmnDefinitionKeyDeserializerTest*"`, focused engine serde/unit tests, and `./gradlew :taktx-engine:build`.

**Acceptance criteria**
- [x] `grep "ObjectMapperSerde" taktx-engine/src/main/java` returns zero results.
- [x] `./gradlew :taktx-engine:build` succeeds.
- [x] All Kafka Streams unit tests using `TopologyTestDriver` pass.
- [x] Sonar issue delta for `TopologyProducer.java` is zero or negative (easy wins fixed, no new issues added).

**Dependencies:** PROTO-3.1, PROTO-3.2, E4.2–E4.11 must be complete before this compiles  
**Estimate:** 1 day

---

### PROTO-4.2 — Migrate `ProcessInstanceTrigger` family

**Description**  
Replace `ProcessInstanceTriggerDTO` hierarchy (9 concrete types, currently dispatched by `ProcessInstanceTriggerTypeIdResolver`) with `ProcessInstanceTriggerEnvelope` proto message. Update:

**Progress update (2026-05-18)**
- ✅ Added shared DTO↔proto mapper coverage for the active process-instance trigger DTO family (`StartCommandDTO`, `ContinueFlowElementTriggerDTO`, `ExternalTaskResponseTriggerDTO`, `StartFlowElementTriggerDTO`, `SetVariableTriggerDTO`, `AbortTriggerDTO`, `UserTaskResponseTriggerDTO`, `EventSignalTriggerDTO`) with lossless round-trips for trust metadata, business metadata, variable payloads, IO mappings, and event-signal subtype payloads.
- ✅ Switched client-side `ProcessInstanceTriggerSerializer`, engine-side `ProcessInstanceTriggerEnvelopeSerializer` / `ProcessInstanceTriggerEnvelopeDeserializer`, and `TopologyProducer` process-instance-trigger Serdes from Jackson/CBOR to protobuf bytes.
- ✅ Updated the raw engine test-fixture process-instance-trigger consumer to the protobuf DTO deserializer path.
- ✅ Migrated the remaining schedule-command value path to protobuf DTO serdes (`MessageScheduleEnvelope` + `SchedulableMessageEnvelope`), so scheduled process-instance triggers and scheduled external-task triggers no longer depend on Jackson trigger polymorphism.
- ✅ Deleted `ProcessInstanceTriggerTypeIdResolver.java` and the now-unused `MessageSchedulerTypeIdResolver.java` after the schedule path stopped referencing them.
- ✅ Verified on 2026-05-18: `./gradlew :taktx-shared:test --tests io.taktx.serdes.ProcessInstanceTriggerProtoMapperTest --console=plain` passes.
- ✅ Verified on 2026-05-18: `./gradlew :taktx-shared:test --tests io.taktx.serdes.MessageScheduleProtoMapperTest --tests io.taktx.serdes.ProtoSerdesTest --console=plain` passes.
- ✅ Verified on 2026-05-18: `./gradlew :taktx-client:test --tests io.taktx.client.ProcessInstanceProducerTest --tests io.taktx.client.serdes.ProcessDefinitionKeyDeserializerTest --console=plain` passes.
- ✅ Verified on 2026-05-18: `./gradlew :taktx-engine:test --tests io.taktx.engine.pd.ScheduleCommandDeserializerTest --tests io.taktx.engine.pd.ScheduleProcessorTest --tests io.taktx.engine.pi.ProcessInstanceTriggerEnvelopeDeserializerTest --tests io.taktx.engine.security.ProcessInstanceResponseDedupProcessorTest --tests io.taktx.engine.security.ReplayProtectionProcessorTest --console=plain` passes.
- ✅ Verified on 2026-05-18: `./gradlew :taktx-engine:securityIntegrationTest --tests io.taktx.engine.pi.integration.SecurityIntegrationTest.validJwt_commandAccepted_processInstanceStarted --tests io.taktx.engine.pi.integration.SecurityIntegrationTest.workerEd25519SignedResponse_processCompletes --console=plain` passes.

- `ProcessInstanceTriggerEnvelopeSerializer` / `ProcessInstanceTriggerEnvelopeDeserializer`
- `ProcessInstanceProcessor` and all methods that switch on trigger type
- `StartCommandMessage` construction at all call sites in the engine
- `ContinueFlowElementTriggerMessage`, `UserTaskResponseTriggerMessage`, `ExternalTaskResponseTriggerMessage`, `SetVariableTriggerMessage`, `AbortTriggerMessage`, `EventSignalTriggerMessage`, `ExternalTaskTriggerMessage`, `StartFlowElementTriggerMessage`

Delete `ProcessInstanceTriggerTypeIdResolver.java`.

**Acceptance criteria**
- [x] Unit test: each of the active process-instance trigger types serializes and round-trips through `ProcessInstanceTriggerEnvelope.parseFrom(envelope.toByteArray())`; scheduled external-task trigger payload coverage now lives in `MessageScheduleEnvelope` round-trip tests.
- [x] Existing trigger-envelope / dedup / replay-protection engine tests pass.
- [x] Integration test: full BPMN process start-to-end executes successfully.

**Dependencies:** PROTO-3.1  
**Estimate:** 1.5 days

---

### PROTO-4.3 — Migrate `InstanceUpdate` family

**Description**  
Replace `InstanceUpdateDTO` + `FlowNodeInstanceUpdateDTO` + `ProcessInstanceUpdateDTO` with `InstanceUpdateEnvelope` proto. Update `InstanceUpdateTypeIdResolver` dispatch → proto `oneof` switch. Update all emitters in the stream processor layer that produce instance-update records.

**Progress update (2026-05-18)**
- ✅ Added shared DTO↔proto mapping and DTO deserialization for `FlowNodeInstanceUpdateDTO` and `ProcessInstanceUpdateDTO`, including trust metadata, scope/subscription payloads, variables, business metadata, and the existing `FlowNodeInstanceDTO` bridge payload.
- ✅ Switched engine `TopologyProducer.INSTANCE_UPDATE_SERDE`, client-facing `InstanceUpdateDeserializer`, and the raw engine test-fixture instance-update consumer to the protobuf-backed serde/deserializer path.
- ✅ Deleted the now-unused `InstanceUpdateTypeIdResolver.java` and removed the obsolete Jackson array-format annotation from `InstanceUpdateDTO` after all instance-update topic paths stopped depending on Jackson polymorphism.
- ✅ Verified on 2026-05-18: `./gradlew :taktx-shared:test --tests io.taktx.serdes.InstanceUpdateProtoMapperTest --tests io.taktx.serdes.ProtoSerdesTest --console=plain` passes.
- ✅ Verified on 2026-05-18: `./gradlew :taktx-client:test --tests io.taktx.client.serdes.SigningRoundTripTest --tests io.taktx.client.serdes.InstanceUpdateDeserializerTest --console=plain` passes.
- ✅ Verified on 2026-05-18: `./gradlew :taktx-client-quarkus:test --tests '*TaktXClientProvider*' :taktx-engine:securityIntegrationTest --tests io.taktx.engine.pi.integration.SecurityIntegrationTest.validJwt_commandAccepted_processInstanceStarted --tests io.taktx.engine.pi.integration.SecurityIntegrationTest.workerEd25519SignedResponse_processCompletes --console=plain` passes, covering raw instance-update consumption, `tx-sig` verification, and trust-metadata propagation in the observability/client path.

Delete `InstanceUpdateTypeIdResolver.java`.

**Acceptance criteria**
- [x] Unit test: `FlowNodeInstanceUpdateMessage` and `ProcessInstanceUpdateMessage` round-trip.
- [x] Unit test: trust metadata fields (`currentTrustMetadata`, `originTrustMetadata`) round-trip.
- [x] Integration test: instance-update records consumed by the observability/DLQ pipeline are correctly deserialized.

**Dependencies:** PROTO-4.2  
**Estimate:** 1 day

---

### PROTO-4.4 — Migrate `FlowNodeInstance` family

**Description**  
Replace `FlowNodeInstanceDTO` hierarchy (21 concrete types, dispatched by `FlowNodeInstanceTypeIdResolver`) with `FlowNodeInstanceEnvelope` proto. Each concrete subtype's fields are included directly in the respective `oneof` case message (flatten inheritance; shared base fields appear in a `FlowNodeInstanceBase` embedded message included in each case).

**Progress update (2026-05-18)**
- ✅ Added shared DTO↔proto mapping and DTO deserialization for all 21 concrete `FlowNodeInstanceDTO` variants, including nested scope/subscription payloads, activity input/output `VariableValue` fields, receive-task correlation/message-event data, external-task schedule-key envelopes, and event/gateway/task specializations.
- ✅ Switched engine `TopologyProducer.FLOW_NODE_INSTANCE_SERDE` and the embedded `FlowNodeInstance` payload inside `InstanceUpdateProtoMapper` from Jackson/CBOR bytes to `FlowNodeInstanceEnvelope` protobuf bytes.
- ✅ Closed schema fidelity gaps before wiring the serde by preserving polymorphic `ScheduleKeyDTO` values with `ScheduleKeyEnvelope`, adding `ParallelGatewayInstanceDTO.triggeredFlows`, and replacing raw `InstanceUpdate.flow_node_instance` bytes with a typed `FlowNodeInstanceEnvelope` field.
- ✅ Deleted the now-unused `FlowNodeInstanceTypeIdResolver.java` after all active flow-node topic/state-store paths stopped depending on Jackson polymorphism.
- ✅ Verified on 2026-05-18: `./gradlew :taktx-shared:test --tests io.taktx.serdes.FlowNodeInstanceProtoMapperTest --tests io.taktx.serdes.InstanceUpdateProtoMapperTest --tests io.taktx.serdes.ProtoSerdesTest --console=plain` passes.
- ✅ Verified on 2026-05-18: `./gradlew :taktx-engine:quarkusIntTest --tests io.taktx.engine.pi.integration.ExternalTaskTest --tests io.taktx.engine.pi.integration.UserTaskTest --tests io.taktx.engine.pi.integration.GatewayTest --tests io.taktx.engine.pi.integration.BoundaryEventsTest --tests io.taktx.engine.pi.integration.VariablesTest --console=plain` passes.
- ✅ Verified on 2026-05-18: `./gradlew :taktx-engine:securityIntegrationTest --tests io.taktx.engine.pi.integration.SecurityIntegrationTest.validJwt_commandAccepted_processInstanceStarted --tests io.taktx.engine.pi.integration.SecurityIntegrationTest.workerEd25519SignedResponse_processCompletes --console=plain` passes.

Delete `FlowNodeInstanceTypeIdResolver.java`.

**Acceptance criteria**
- [x] Unit test: each of the 21 concrete instance types round-trips through the envelope.
- [x] Unit test: `ActivityInstanceMessage.inputElement` (a `VariableValue`) round-trips.
- [x] Integration test: service task, user task, exclusive gateway, parallel gateway, inclusive gateway, sub-process, and boundary event all execute correctly end-to-end.

**Dependencies:** PROTO-2.1, PROTO-4.3  
**Estimate:** 2 days

---

### PROTO-4.5 — Migrate `DefinitionsTrigger` / BPMN element family

**Description**  
Replace `DefinitionsTriggerDTO` + `ParsedDefinitionsDTO` + the full `BaseElementDTO` hierarchy (27 BPMN element types, dispatched by `BaseElementTypeIdResolver`) with proto definitions from `definitions.proto`. Update `DefinitionsProcessor`, `DefinitionMapper`, `DefinitionsCache`.

Delete `BaseElementTypeIdResolver.java` and `DefinitionsTriggerTypeIdResolver.java`.

**Implementation notes (2026-05-18)**
- `definitions.proto` now stores concrete `FlowElementsDTO` values and event definitions via `BaseElementEnvelope`, enabling lossless round-trips for the BPMN element hierarchy.
- Added shared `DefinitionsProtoMapper`, `DefinitionsTriggerDtoDeserializer`, and `ProcessDefinitionDtoDeserializer` in `taktx-shared`.
- Switched engine `DefinitionsTriggerDTO` and `ProcessDefinitionDTO` Kafka serdes in `taktx-engine/src/main/java/io/taktx/engine/generic/TopologyProducer.java` from CBOR/Jackson to protobuf.
- Switched client BPMN deployment/consumption serdes (`XmlDefinitionSerializer`, `ProcessDefinitionDeserializer`) to the protobuf path.
- Removed Jackson type-id wiring from `BaseElementDTO` / `DefinitionsTriggerDTO` and deleted `BaseElementTypeIdResolver.java` / `DefinitionsTriggerTypeIdResolver.java`.
- Hardened `InstanceUpdateProtoMapper` against null output-sequence-flow ids uncovered by the expanded BPMN integration slice.
- Added `DefinitionsProtoAcceptanceTest` in `taktx-engine/src/integrationTest/java/io/taktx/engine/pi/integration/` to verify end-to-end deployment/execution across representative supported BPMN element families and redeploy/version-bump continuity.
- Added dedicated versioned BPMN fixtures `proto45-versioned-process-v1.bpmn` / `proto45-versioned-process-v2.bpmn` for the continuity acceptance scenario.

- ✅ Verified on 2026-05-18: `./gradlew :taktx-shared:test --tests io.taktx.serdes.DefinitionsProtoMapperTest --tests io.taktx.serdes.ProtoSerdesTest --console=plain` passes.
- ✅ Verified on 2026-05-18: `./gradlew :taktx-client:test --tests io.taktx.client.serdes.XmlDefinitionSerializerTest --tests io.taktx.client.serdes.ProcessDefinitionDeserializerTest --tests io.taktx.client.serdes.DefinitionsWireFormatCompatibilityTest --console=plain` passes.
- ✅ Verified on 2026-05-18: `./gradlew :taktx-engine:quarkusIntTest --tests io.taktx.engine.pi.integration.TaskTest --tests io.taktx.engine.pi.integration.ExternalTaskTest --tests io.taktx.engine.pi.integration.UserTaskTest --tests io.taktx.engine.pi.integration.GatewayTest --tests io.taktx.engine.pi.integration.BoundaryEventsTest --tests io.taktx.engine.pi.integration.BusinessRuleTaskTest --tests io.taktx.engine.pi.integration.ScriptTaskTest --tests io.taktx.engine.pi.integration.EventSubprocessTest --tests io.taktx.engine.pi.integration.IntermediateEventsTest --tests io.taktx.engine.pi.integration.ErrorsTest --tests io.taktx.engine.pi.integration.EscalationsTest --tests io.taktx.engine.pi.integration.SignalsTest --console=plain` passes.
- ✅ Verified on 2026-05-18: `./gradlew :taktx-engine:quarkusIntTest --tests io.taktx.engine.pi.integration.DefinitionsProtoAcceptanceTest --console=plain` passes.

**Acceptance criteria**
- [x] Unit test: a `ParsedDefinitionsMessage` containing a process with all 27 element types round-trips without data loss.
- [x] Unit test: `ServiceTaskMessage`, `UserTaskMessage`, `SubProcessMessage`, `CallActivityMessage` field coverage.
- [x] Integration test: deploy a BPMN with all supported element types; execute a process instance from start to end.
- [x] Integration test: redeploy a BPMN (version bump) and existing instances continue correctly.

**Dependencies:** PROTO-2.1, PROTO-4.4  
**Estimate:** 2 days

---

### PROTO-4.6 — Migrate `MessageEvent` family

**Status:** ✅ Complete

**Description**  
Replace `MessageEventDTO` hierarchy (6 types, dispatched by `MessageEventTypeIdResolver`) with `MessageEventEnvelope` proto. Update `MessageEventProcessor`, `CorrelationMessageSubscriptions`, `DefinitionMessageSubscriptions`.

Delete `MessageEventTypeIdResolver.java`.

**Implementation notes (2026-05-18)**
- ✅ Added shared DTO↔proto mapping and DTO deserialization for all 6 `MessageEventDTO` variants plus `MessageEventKeyDTO` via `MessageEventProtoMapper`, `MessageEventDtoDeserializer`, and `MessageEventKeyDtoDeserializer` in `taktx-shared`.
- ✅ Switched engine `TopologyProducer.MESSAGE_EVENT_SERDE` / `MESSAGE_EVENT_KEY_SERDE`, client `MessageEventSerializer` / `MessageEventKeySerializer`, and the raw engine test-fixture message-event consumers from Jackson/CBOR to protobuf bytes.
- ✅ Removed obsolete Jackson polymorphic wiring from `MessageEventDTO`, deleted `MessageEventTypeIdResolver.java`, and dropped the no-longer-needed `MessageEventDTO` base-type allowance from the engine CBOR `ObjectMapper` provider.
- ✅ Added focused round-trip coverage in `taktx-shared/src/test/java/io/taktx/serdes/MessageEventProtoMapperTest.java` for all 6 message-event variants and the protobuf key path.
- ✅ Updated client serializer coverage so `MessageEventSerializerTest` / `MessageEventKeySerializerTest` now assert parseable protobuf bytes rather than legacy `JsonSerializer` internals.
- ✅ Verified on 2026-05-18: `./gradlew :taktx-shared:test --tests io.taktx.serdes.MessageEventProtoMapperTest :taktx-client:test --tests io.taktx.client.serdes.MessageEventSerializerTest --tests io.taktx.client.serdes.MessageEventKeySerializerTest --console=plain` passes.
- ✅ Verified on 2026-05-18: `./gradlew :taktx-engine:test --tests io.taktx.engine.pd.MessageEventProcessorDlqTest :taktx-engine:quarkusIntTest --tests io.taktx.engine.pi.integration.ProcessInstanceProcessorTest.testMessageStartEvent --tests io.taktx.engine.pi.integration.ProcessInstanceProcessorTest.testReceiveTask --tests io.taktx.engine.pi.integration.IntermediateEventsTest.testMessageIntermediateCatch --console=plain` passes.

**Acceptance criteria**
- [x] Unit test: each of the 6 message event types round-trips.
- [x] Integration test: message correlation (start event and intermediate catch event) works end-to-end.

**Dependencies:** PROTO-4.5  
**Estimate:** 0.5 day

---

### PROTO-4.7 — Migrate `Signal` family

**Status:** ✅ Complete

**Description**  
Replace `SignalDTO` hierarchy (5 types, dispatched by `SignalTypeIdResolver`) with `SignalEnvelope` proto. Update `SignalProcessor`.

Delete `SignalTypeIdResolver.java`.

**Implementation notes (2026-05-18)**
- ✅ Added shared DTO↔proto mapping and DTO deserialization for all 5 `SignalDTO` variants via `SignalProtoMapper` and `SignalDtoDeserializer` in `taktx-shared`.
- ✅ Switched engine `TopologyProducer.SIGNAL_SERDE`, client `SignalSerializer`, and the raw engine test-fixture signal consumer from Jackson/CBOR to protobuf bytes.
- ✅ Removed obsolete Jackson polymorphic wiring from `SignalDTO` and deleted `SignalTypeIdResolver.java` after all active signal topic paths stopped depending on it.
- ✅ Added focused round-trip coverage in `taktx-shared/src/test/java/io/taktx/serdes/SignalProtoMapperTest.java` for all 5 signal variants.
- ✅ Added client serializer coverage in `taktx-client/src/test/java/io/taktx/client/serdes/SignalSerializerTest.java` to assert parseable protobuf signal envelope bytes.
- ✅ Hardened `SignalsTest` with a new broadcast scenario that starts two instances waiting on the same signal and verifies both complete after a single broadcast.
- ✅ Verified on 2026-05-18: `./gradlew :taktx-shared:test --tests io.taktx.serdes.SignalProtoMapperTest :taktx-client:test --tests io.taktx.client.serdes.SignalSerializerTest :taktx-engine:test --tests io.taktx.engine.pd.SignalProcessorDlqTest --console=plain` passes.
- ✅ Verified on 2026-05-18: `./gradlew :taktx-engine:quarkusIntTest --tests io.taktx.engine.pi.integration.SignalsTest --console=plain` passes.

**Acceptance criteria**
- [x] Unit test: each of the 5 signal types round-trips.
- [x] Integration test: broadcast signal caught by all active subscriptions works end-to-end.

**Dependencies:** PROTO-4.5  
**Estimate:** 0.5 day

---

### PROTO-4.8 — Migrate `UserTask` and `ExternalTask` families

**Status:** ✅ Complete

**Description**  
Replace `UserTaskTriggerDTO`, `UserTaskResponseTriggerDTO`, `UserTaskResponseResultDTO`, `ExternalTaskTriggerDTO`, `ExternalTaskResponseTriggerDTO`, `ExternalTaskResponseResultDTO` with proto equivalents. Update `UserTaskResponseProcessor`.

**Implementation notes (2026-05-18)**
- ✅ Audited the repo before editing: worker-facing protobuf serdes for `UserTaskTriggerDTO` / `ExternalTaskTriggerDTO` and process-instance-trigger envelope handling for `UserTaskResponseTriggerDTO` / `ExternalTaskResponseTriggerDTO` were already complete from PROTO-4.2, so the remaining gaps were limited to response publishing, the `usertasks-response` ingress serde, legacy alias deserializers, and acceptance coverage.
- ✅ Switched client worker-response emission (`ProcessInstanceResponder`, `TaktXClient`) from the legacy generic `SigningSerializer` path to `ProtoSigningSerializer<>(ProcessInstanceTriggerProtoMapper::toProto)` so user-task and external-task completions are signed over protobuf bytes.
- ✅ Removed the remaining direct Jackson object-to-variable conversion from `UserTaskInstanceResponder` / `ExternalTaskInstanceResponder`; they now build `VariablesDTO` via existing shared helpers while preserving the current public API until PROTO-5.1.
- ✅ Replaced `TopologyProducer.USER_TASK_RESPONSE_SERDE` with a protobuf-backed serde and added shared `UserTaskResponseTriggerProtoDeserializer` so the `usertasks-response` ingress path no longer depends on `ObjectMapperSerde<UserTaskResponseTriggerDTO>`.
- ✅ Initially converted the worker-trigger client aliases to protobuf-backed delegates, then removed those compatibility aliases in the breaking-release cleanup and kept the canonical `UserTaskTriggerDeserializer` / `ExternalTaskTriggerDeserializer` classes only.
- ✅ Added focused protobuf coverage in `UserTaskResponseTriggerProtoDeserializerTest`, `UserTaskTriggerDeserializerTest`, and enhanced `ExternalTaskTriggerDeserializerTest`.
- ✅ Verified on 2026-05-18: `./gradlew :taktx-shared:test --tests io.taktx.serdes.WorkerTriggerProtoSerdesTest --tests io.taktx.serdes.ProcessInstanceTriggerProtoMapperTest --tests io.taktx.serdes.UserTaskResponseTriggerProtoDeserializerTest --console=plain` passes.
- ✅ Verified on 2026-05-18: `./gradlew :taktx-client:test --tests io.taktx.client.serdes.ExternalTaskTriggerDeserializerTest --tests io.taktx.client.serdes.UserTaskTriggerDeserializerTest --tests io.taktx.client.serdes.MiscSerdesTest --console=plain` passes.
- ✅ Verified on 2026-05-18: `./gradlew :taktx-engine:test --tests io.taktx.engine.pd.UserTaskResponseProcessorDlqTest --tests io.taktx.engine.pi.ProcessInstanceTriggerEnvelopeDeserializerTest --console=plain` passes.
- ✅ Verified on 2026-05-18: `./gradlew :taktx-engine:quarkusIntTest --tests io.taktx.engine.pi.integration.UserTaskTest --tests io.taktx.engine.pi.integration.ExternalTaskTest --console=plain` passes.

**Acceptance criteria**
- [x] Unit test: `UserTaskTriggerMessage` round-trips including `AssignmentDefinitionMessage`, `PriorityDefinitionMessage`, `TaskScheduleMessage`.
- [x] Unit test: `UserTaskResponseTriggerMessage` with variables round-trips.
- [x] Integration test: user task claim + complete cycle works end-to-end.
- [x] Integration test: external task fetch + complete with output variables works end-to-end.

**Dependencies:** PROTO-4.5  
**Estimate:** 1 day

---

### PROTO-4.9 — Migrate `MessageScheduler` / `Schedule` family

**Status:** ✅ Complete

**Description**  
Replace `MessageScheduleDTO` hierarchy (dispatched by `MessageSchedulerTypeIdResolver`), `ScheduleKeyDTO` hierarchy (dispatched by `ScheduleKeyTypeIdResolver`), `TaskScheduleDTO`, `TimeBucket` with proto equivalents. Update `MessageSchedulerFactory`, `ScheduleProcessor`, `ScheduleCommandDeserializer`.

Delete `MessageSchedulerTypeIdResolver.java` and `ScheduleKeyTypeIdResolver.java`.

**Implementation notes (2026-05-19)**
- ✅ Audited the schedule family before editing: `MessageSchedulerFactory`, `ScheduleProcessor`, and `ScheduleCommandDeserializer` were already on the protobuf schedule-value path from earlier migration work; the confirmed remaining legacy gap was `TopologyProducer.SCHEDULE_KEY_SERDE`, which still used `ObjectMapperSerde<ScheduleKeyDTO>` and the Jackson-only `ScheduleKeyTypeIdResolver`.
- ✅ Added shared `ScheduleKeyProtoMapper` + `ScheduleKeyDtoDeserializer` for `ScheduleKeyEnvelope` protobuf bytes covering both `DefinitionScheduleKeyDTO` and `InstanceScheduleKeyDTO`, and switched `TopologyProducer.SCHEDULE_KEY_SERDE` to the protobuf path.
- ✅ Deleted `ScheduleKeyTypeIdResolver.java` and removed the now-unused Jackson schedule annotations from `ScheduleKeyDTO`, `MessageScheduleDTO`, `TaskScheduleDTO`, and `TimeBucket`.
- ✅ Added focused shared round-trip coverage in `taktx-shared/src/test/java/io/taktx/serdes/ScheduleKeyProtoMapperTest.java` alongside the existing `MessageScheduleProtoMapperTest` schedule-family coverage.
- ✅ Verified on 2026-05-19: `./gradlew :taktx-shared:test --tests io.taktx.serdes.MessageScheduleProtoMapperTest --tests io.taktx.serdes.ScheduleKeyProtoMapperTest --console=plain` passes.
- ✅ Verified on 2026-05-19: `./gradlew :taktx-engine:test --tests io.taktx.engine.pd.ScheduleCommandDeserializerTest --tests io.taktx.engine.pd.ScheduleProcessorTest --tests io.taktx.engine.pd.ScheduleProcessorExcludedTopicTest --console=plain` passes.
- ✅ Verified on 2026-05-19: `./gradlew :taktx-engine:quarkusIntTest --tests io.taktx.engine.pi.integration.BoundaryEventsTest.testBoundaryTimerTriggered --tests io.taktx.engine.pi.integration.BoundaryEventsTest.testBoundaryTimerNonInterrupting --console=plain` passes.

**Acceptance criteria**
- [x] Unit test: each schedule message type round-trips.
- [x] Integration test: timer boundary event fires at correct time.
- [x] Integration test: recurring message schedule re-fires the expected number of times.

**Dependencies:** PROTO-4.6  
**Estimate:** 1 day

---

### PROTO-4.10 — Migrate configuration, signing key, topic meta, and DLQ families

**Status:** ✅ Complete

**Description**  
Replace the remaining lower-frequency families:
- `GlobalConfigurationDTO` / `ConfigurationEventDTO` → `GlobalConfigurationMessage` / `ConfigurationEventMessage`
- `SigningKeyDTO` → `SigningKeyMessage`
- `TopicMetaDTO` → `TopicMetaMessage`
- `DlqEnvelope`, `DlqReplayCommand`, `DlqReplayResult`, `DlqEntryDTO`, `DlqLineageDTO` → proto equivalents
- `ProcessDefinitionDTO`, `ProcessDefinitionActivationDTO`, `DmnDefinitionDTO`, `DmnDefinitionActivationDTO`, `XmlDefinitionsDTO`, `XmlDmnDefinitionsDTO` → proto equivalents

**Implementation notes (2026-05-19)**
- ✅ Audited the remaining PROTO-4.10 surfaces before editing and migrated only the still-live Jackson/CBOR paths: configuration topic consumers, signing-key publication/consumption, topic-meta request serdes, DLQ envelope/replay command/replay result serdes, DMN/XML definition deployment paths, and the remaining `TopologyProducer` value serdes for these families.
- ✅ Added shared DTO↔proto mappers plus protobuf-backed DTO deserializers for configuration, signing key, topic meta, DLQ, DMN definition/XML DMN definition, and reused the existing `DefinitionsProtoMapper` for process definitions.
- ✅ Updated the proto contracts where the audit found wire-model gaps: missing enum values and `optional` scalar presence for configuration timestamps, signing-key timestamps, DLQ lineage/source coordinates, replay schema version, and DMN enum coverage.
- ✅ Initially preserved a few public compatibility entry points while switching their implementations to protobuf; those legacy aliases were removed later in the breaking-release cleanup once canonical deserializer names were in place.
- ✅ Closed the last live signing-key consumer gap by migrating `SigningKeysStore` from Jackson/CBOR to protobuf after integration logs exposed failed key-table reads.
- ✅ Hardened the DLQ replay forwarding path so replayed records now preserve corrected Kafka key bytes all the way to the dynamic target topic sink; this was verified with a real malformed-BPMN capture → replay → deploy → start-process cycle.
- ✅ Verified test commands:
  - `./gradlew :taktx-shared:test --tests io.taktx.serdes.Proto410MapperTest --tests io.taktx.serdes.DefinitionsProtoMapperTest --tests io.taktx.serdes.ExternalTaskMetaSerdesTest --console=plain`
  - `./gradlew :taktx-client:test --tests io.taktx.client.dlq.DlqClientSerdesTest --tests io.taktx.client.serdes.DefinitionsWireFormatCompatibilityTest --tests io.taktx.client.serdes.MiscSerdesTest --tests io.taktx.client.DmnDefinitionDeployerTest --console=plain`
  - `./gradlew :taktx-engine:test --tests io.taktx.engine.license.LicensePushTest --tests io.taktx.engine.topicmanagement.TopicMetaRequestIngressProcessorTest --tests io.taktx.engine.dlq.DlqReplayProcessorTest --tests io.taktx.engine.pd.DmnDefinitionsProcessorDlqTest --console=plain`
  - `./gradlew :taktx-engine:securityIntegrationTest --tests io.taktx.engine.pi.integration.SecurityIntegrationTest --console=plain`
  - `./gradlew :taktx-engine:securityIntegrationTest --tests io.taktx.engine.topicmanagement.PhaseOneDedupIntegrationTest --console=plain`
  - `./gradlew :taktx-engine:quarkusIntTest --tests io.taktx.engine.pi.integration.DlqReplayEndToEndIntegrationTest --tests io.taktx.engine.pi.integration.DefinitionsProtoAcceptanceTest --console=plain`

**Acceptance criteria**
- [x] Unit test: each of the above types round-trips.
- [x] Integration test: signing key publish + rotate + revoke lifecycle works.
- [x] Integration test: DLQ capture → replay cycle works end-to-end.
- [x] Integration test: BPMN deploy → activate → process isolation between versions works.

**Dependencies:** PROTO-4.7, PROTO-4.8, PROTO-4.9  
**Estimate:** 1.5 days

---

### PROTO-4.12 — Harden range-query key serializers; remove Jackson dependency from util layer

**Status:** ✅ Complete

**Description**  
The five Kafka Streams state stores that are range-scanned use compound keys whose byte representation must be byte-lexicographically ordered for `store.range(startKey, endKey)` to return correct results. Protobuf encoding is NOT byte-lexicographically ordered and must never be used for these keys.

The current `TaktUUIDSerializer`, `TaktLongListSerializer`, and `TaktCompositeUUIDSerializer` in `taktx-shared/.../util/` serve a dual role: they extend `JsonSerializer<T>` (used when the key appears embedded inside a CBOR value) **and** implement `Serializer<T>` (used as the raw Kafka key format for range scans). After the migration, the Jackson side disappears; only the Kafka `Serializer<T>` / `Deserializer<T>` role survives.

**Actions per key type**

| Store | Key type | Action |
|---|---|---|
| `flowNodeInstanceStore` | `FlowNodeInstanceKeyDTO` (UUID + List\<Long\>) | Remove `extends JsonSerializer<UUID>` from `TaktUUIDSerializer`; remove `extends JsonSerializer<List<Long>>` from `TaktLongListSerializer`. Binary layout `[16B UUID big-endian | 4B count | 8B×n longs big-endian]` stays **identical**. |
| `variablesStore` | `VariableKeyDTO` (FlowNodeInstanceKeyDTO + String variableName) | Write `VariableKeySerializer` composing the FlowNodeInstanceKey bytes above + `2B length | UTF-8 string bytes`. Document the layout in a Javadoc constant. |
| `processDefinitionStore` | `ProcessDefinitionKey` (String id + int version) | **Fix latent fragility:** current serialization relies on CBOR array byte order for the version integer. Replace with explicit `ProcessDefinitionKeySerializer`: `2B string length (big-endian) | UTF-8 id bytes | 4B big-endian int version`. This is properly byte-lexicographic and removes the CBOR implicit contract. |
| `instanceSignalSubscriptionStore` | `SignalInstanceSubscriptionKeyDTO` (byte\[\] hash + UUID + List\<Long\>) | Write `SignalInstanceSubscriptionKeySerializer`: `32B hash (fixed) | 16B UUID | 4B count | 8B×n longs`. |
| `definitionSignalSubscriptionStore` | `SignalDefinitionSubscriptionKeyDTO` (byte\[\] hash + ProcessDefinitionKey + String elementId) | Write `SignalDefinitionSubscriptionKeySerializer`: `32B hash | 2B id length | UTF-8 id | 4B version | 2B elementId length | UTF-8 elementId`. |

> **Note:** `ScheduleKeyDTO` stores use `store.all()`, not `store.range()`. Byte ordering does not matter for them; they can use proto or the existing CBOR serializer at the implementer's discretion.

**Binary layout specifications** must be written as Javadoc `@implSpec` comments directly above each serializer's `serialize()` method, describing field order, width, and endianness. This becomes the permanent contract.

**Progress update (2026-05-19)**
- ✅ Removed the Jackson `JsonSerializer` inheritance from the util-layer binary key helpers in `taktx-shared` while preserving the existing UUID/long-list byte layouts used by range-scanned stores.
- ✅ Added explicit binary serializer/deserializer/serde implementations for `FlowNodeInstanceKeyDTO`, `VariableKeyDTO`, `ProcessDefinitionKey`, `SignalInstanceSubscriptionKeyDTO`, and `SignalDefinitionSubscriptionKeyDTO`, each documented with `@implSpec` layout contracts.
- ✅ Switched the range-scanned engine stores in `TopologyProducer` to the new key Serdes and aligned `SignalProcessor` / `ProcessInstanceProcessor` range-bound logic with the new binary contracts.
- ✅ Finalized client/test-engine compatibility on the canonical `ProcessDefinitionKeyDeserializer`, which reads the explicit binary key bytes without any legacy CBOR fallback.
- ✅ Verified on 2026-05-19:
  - `grep -r "extends JsonSerializer" taktx-shared/src/main/java/io/taktx/util` returns zero results.
  - `./gradlew :taktx-shared:test --tests "*RangeKeySerializerTest*" :taktx-client:test --tests "*ProcessDefinitionKeyDeserializerTest*" :taktx-engine:test --tests "*SignalSubscriptionKeySerdeTest" --tests "*ProcessDefinitionActivationProcessorTest" :taktx-engine:quarkusIntTest --tests "*SignalsTest" --tests "*DefinitionsProtoAcceptanceTest" --tests "*ProcessInstanceProcessorTest" --tests "*BusinessRuleTaskTest" --console=plain` passes.

**Acceptance criteria**
- [x] `grep -r "extends JsonSerializer" taktx-shared/src/main/java/io/taktx/util` returns zero results.
- [x] Unit test for `ProcessDefinitionKeySerializer`: assert `bytes(key("proc", 1)) < bytes(key("proc", 2)) < bytes(key("proc", 100))`.
- [x] Unit test for `FlowNodeInstanceKey`: assert `bytes(key(X, [1,2,3])) < bytes(key(X, [1,2,4]))` and `bytes(key(X, [...])) < bytes(key(Y, [...]))` when `X < Y` (UUID byte order).
- [x] Unit test for `SignalInstanceSubscriptionKeySerializer`: assert range from `(hash, MIN_UUID, [])` to `(hash, MAX_UUID, [MAX_LONG])` covers all keys with that hash prefix and no keys with a different hash.
- [x] Unit test for `VariableKeySerializer`: round-trip serialization/deserialization preserves all fields.
- [x] All range query integration tests pass end-to-end (signal fan-out, process definition version scan, flow node instance retrieval, variable scope resolution).

**Dependencies:** PROTO-1.2 (Jackson removed from build, so the `extends JsonSerializer` compilation dependency is gone)  
**Estimate:** 1.5 days

---

### PROTO-4.11 — Remove Quarkus native reflection config for old DTOs; add for proto classes

**Description**  
The current DTOs each declare `@RegisterForReflection`. Proto-lite generated classes do not use reflection; they are fully self-contained. However, Quarkus native compilation needs help for any class loaded reflectively.

- Remove all `@RegisterForReflection` from deleted DTO classes (already done in PROTO-1.3).
- Add a `@RegisterForReflection(targets = { ProcessInstanceTriggerEnvelope.class, ... })` aggregate class for all top-level proto types, or use a `quarkus-extension.yaml` descriptor as appropriate.
- Verify `Dockerfile.linux-native` build succeeds without reflection errors.

**Acceptance criteria**
- [x] Native build (`./gradlew :taktx-engine:quarkusBuild -Dquarkus.package.type=native`) succeeds.
- [x] Engine starts and processes a BPMN process in native mode (smoke test via the existing `task-single.bpmn` integration path).

**Status:** ✅ Complete  
**Dependencies:** PROTO-1.3, PROTO-4.10  
**Estimate:** 0.5 day

---

## E5 — Client Libraries Migration

---

### PROTO-5.1 — Migrate `taktx-client` public API

**Description**  
`taktx-client` is a Maven Central public library. Its API surface that changes:

- `DefaultParameterResolverFactory`: remove `CBORFactory`/`ObjectMapper`; deserialize inbound records using `ProtoDeserializer`.
- `UserTaskInstanceResponder`: replace `ObjectMapper.writeValueAsBytes(dto)` with `message.toByteArray()`.
- `VariableParameterResolver` and any `@ParameterResolver` implementations: replace `JsonNode` with `VariableValue`; update `VariableParameterResolverTest`.
- Update all `VariablesDTO.of(...)` call sites to `Variables.map(...)`.

**Acceptance criteria**
- [x] `./gradlew :taktx-client:build` succeeds with no direct Jackson dependency declared by `taktx-client` (remaining transitive Jackson cleanup stays tracked under PROTO-1.3).
- [x] Unit test: `UserTaskInstanceResponder.complete(...)` produces a binary payload parseable as `ProcessInstanceTriggerEnvelope` containing `userTaskResponse`.
- [x] Unit test: `VariableParameterResolver` resolves a `VariableValue` from an inbound record.
- [x] All `taktx-client/src/main/java` source files compile without Jackson imports.
- [x] No new build-breaking or analyzer issues were introduced across the `taktx-client` module; trivial issues in touched files were resolved opportunistically.

**Status:** ✅ Complete

**Dependencies:** E3, E4.8  
**Estimate:** 1.5 days

---

### PROTO-5.2 — Migrate `taktx-client-quarkus`

**Description**  
Update `taktx-client-quarkus` extension — CDI bean wiring for serializers/deserializers; remove Jackson producer beans that provided `ObjectMapper` for the client.

**Acceptance criteria**
- [x] `./gradlew :taktx-client-quarkus:build` succeeds.
- [x] Integration test in `taktx-client-quarkus` demonstrates a user task being completed and the engine receiving it correctly.

**Dependencies:** PROTO-5.1  
**Estimate:** 0.5 day

---

### PROTO-5.3 — Migrate `taktx-client-spring-boot-3`

**Description**  
Update Spring Boot 3 auto-configuration; replace `ObjectMapper` Kafka config bean with proto Serde wiring; update `InstanceUpdateRecordEventChecker` and `SpringBeanInstanceProvider`.

**Acceptance criteria**
- [x] `./gradlew :taktx-client-spring-boot-3:build` succeeds.
- [x] Integration test in `taktx-client-spring-boot-3` demonstrates a user task being completed.

**Dependencies:** PROTO-5.1  
**Estimate:** 0.5 day

---

### PROTO-5.4 — Migrate `taktx-client-spring-boot-4`

**Description**  
Same as PROTO-5.3 for Spring Boot 4 / Spring Framework 7.

**Acceptance criteria**
- [x] `./gradlew :taktx-client-spring-boot-4:build` succeeds.
- [x] Integration test demonstrates a user task being completed.

**Dependencies:** PROTO-5.1  
**Estimate:** 0.5 day

---

## E6 — Test Hardening & Regression Guard

---

### PROTO-6.1 — Golden-bytes round-trip test suite (`taktx-shared`)

**Status:** ✅ Complete (2026-05-19)

**Description**  
For each top-level proto envelope type, create a test that:
1. Constructs a fully-populated instance (all fields set, no defaults).
2. Serializes to bytes.
3. Asserts the byte count is within expected bounds (catches accidental size regressions).
4. Parses back and asserts structural equality.
5. Stores the serialized bytes as a golden file (`src/test/resources/golden/*.bin`).
6. On CI, a separate test reads each golden file and asserts it still parses to the expected type (backward parse compatibility guard).

**Acceptance criteria**
- [x] Golden files exist for: `ProcessInstanceTriggerEnvelope`, `InstanceUpdateEnvelope`, `FlowNodeInstanceEnvelope`, `ProcessInstanceMessage`, `ParsedDefinitionsMessage`, `MessageEventEnvelope`, `SignalEnvelope`, `UserTaskTriggerMessage`, `DlqEnvelope` (plan label `DlqEnvelopeMessage`), `GlobalConfigurationMessage`, `SigningKeyMessage`.
- [x] Each golden file test fails immediately if a field number is changed (proto parsing would silently misroute the value to the wrong field).
- [x] CI job runs golden tests in a separate task (`goldenTest`) that can be run independently.

**Implementation notes (2026-05-19)**
- ✅ Added `taktx-shared:goldenTest` as a dedicated source set / Gradle task with committed `.bin` fixtures under `taktx-shared/src/test/resources/golden/`.
- ✅ Added reusable rich fixture builders in `taktx-shared/src/test/java/io/taktx/serdes/GoldenFixtureSamples.java` and a read-only compatibility suite in `taktx-shared/src/goldenTest/java/io/taktx/serdes/ProtoGoldenCompatibilityTest.java`.
- ✅ The golden suite compares current serialized bytes byte-for-byte with the committed fixtures, enforces size bounds, and reparses each fixture to the expected protobuf type.
- ✅ Verified on 2026-05-19: `./gradlew :taktx-shared:goldenTest --console=plain` passes.

**Dependencies:** E3 complete  
**Estimate:** 1.5 days

---

### PROTO-6.2 — Proto field-number stability lint rule

**Status:** ✅ Complete (2026-05-19)

**Description**  
Add a `check_proto_field_numbers.py` script (similar to the existing `check_headers.py`) that:
- Parses all `.proto` files.
- Asserts field numbers are never reused within a message.
- Asserts `reserved` entries exist for any field name that appears in a `reserved` statement.
- Runs as part of the Spotless check or as a separate `protoCheck` Gradle task.

**Acceptance criteria**
- [x] CI fails if a `.proto` file has a duplicate field number or a `reserved` omission.
- [x] Script is documented in `CONTRIBUTING.md`.

**Implementation notes (2026-05-19)**
- ✅ Added `scripts/check_proto_field_numbers.py` to lint all repository `.proto` files for duplicate field numbers, duplicate field names, reserved-name/number collisions, and overlapping `reserved` ranges.
- ✅ Added a root `protoCheck` Gradle verification task and wired it into the standard `check` lifecycle.
- ✅ Documented `./gradlew protoCheck` in `CONTRIBUTING.md`.
- ✅ Verified on 2026-05-19: `./gradlew protoCheck --console=plain` passes.

**Dependencies:** PROTO-1.1  
**Estimate:** 0.5 day

---

### PROTO-6.3 — `VariableValue` encoding size benchmarks

**Status:** ✅ Complete (2026-05-19)

**Description**  
Add a JMH or simple assertion-based size benchmark in `taktx-shared/src/test` that compares:
- Current CBOR-array encoding of representative `VariablesDTO` payloads (using saved golden CBOR bytes).
- New `VarMap` proto encoding of equivalent data.

The purpose is not to gate the build but to document the size characteristics of the variable payload migration and catch future regressions. This story is intentionally scoped to `VariablesDTO`/`VarMap`: other top-level wire families are already covered by the golden-byte and size-bound tests from PROTO-6.1.

**Acceptance criteria**
- [x] Benchmark/assertion for: 5 numeric variables, 5 string variables, 1 nested object variable, 1 list variable.
- [x] Saved legacy CBOR fixtures are compared against the current `VarMap` protobuf encoding, and the benchmark guards against any further size regression beyond the measured baseline deltas (+25 B numeric, +26 B string, +19 B nested object, +17 B list for the current fixtures).
- [x] Results printed to build log for visibility.

**Implementation notes (2026-05-19)**
- ✅ Added `taktx-shared/src/test/resources/legacy-cbor/*.cbor` fixtures for the representative legacy `VariablesDTO` payloads, reproduced from the actual pre-protobuf `@JsonFormat(shape = ARRAY)` Jackson+CBOR serializer.
- ✅ Added `taktx-shared/src/test/java/io/taktx/variables/VariablesEncodingBenchmarkTest.java` to compare legacy CBOR fixture sizes with the current `VarMap` protobuf encoding and to guard against further size growth.
- ✅ Added a dedicated `:taktx-shared:variableSizeBenchmark` task with standard-stream logging enabled so the measured sizes show up directly in the build log.
- ✅ Extended the same benchmark task with an exploratory protobuf payload-size report for commonly used message families plus an absent-vs-explicit value check. Verified on 2026-05-19:
  - full fixtures: `process-instance-trigger=321 B`, `instance-update=619 B`, `flow-node-instance=205 B`, `process-instance=519 B`, `user-task-trigger=166 B`
  - sparse current protobuf fixtures are dramatically smaller because absent fields are omitted (`trigger=34 B`, `process-instance=32 B`, `user-task=47 B`)
  - explicit presence still costs bytes even for empty/default values (`VarMap absent=0 B`, `null=2 B`, `empty string=2 B`, `false=2 B`, `zero long=2 B`)
- ✅ Verified on 2026-05-19: `./gradlew :taktx-shared:variableSizeBenchmark --console=plain` prints:
  - `five-numeric-variables`: legacy 54 B, proto 79 B, delta +25 B
  - `five-string-variables`: legacy 75 B, proto 101 B, delta +26 B
  - `nested-object-variable`: legacy 42 B, proto 61 B, delta +19 B
  - `list-variable`: legacy 20 B, proto 37 B, delta +17 B

**Dependencies:** PROTO-2.1  
**Estimate:** 0.5 day

---

### PROTO-6.4 — Full end-to-end integration test pass

**Status:** ✅ Complete (2026-05-19)

**Description**  
Run the full test suite via the canonical root verification task (`./gradlew runAllTests`) after all epics are complete. This task intentionally wraps all subproject `check` tasks plus `:taktx-engine:quarkusIntTest`, so it is the repository's single end-to-end protobuf migration exit command.

Scope of the full suite:
- `taktx-shared`: 31 tests
- `taktx-engine`: unit tests + `securityIntegrationTest` suite + `quarkusIntTest`
- `taktx-client`: 110 test files
- `taktx-client-quarkus`, `taktx-client-spring-boot-3`, `taktx-client-spring-boot-4`: integration tests

**Acceptance criteria**
- [x] `./gradlew runAllTests` is the canonical green end-to-end verification command for the repository.
- [x] `./gradlew :taktx-engine:securityIntegrationTest` passes (Ed25519 signing with proto payloads).
- [x] Coverage inputs remain aligned with the committed JaCoCo badge baseline in `badges/coverage-summary.json` and the Sonar import paths in `sonar-project.properties`.
- [x] Sonar quality gate remains a manual/external release check; the repository-side configuration is ready via `sonar-project.properties`.

**Implementation notes (2026-05-19)**
- ✅ Standardised the final repository gate on `./gradlew runAllTests` and aligned CI to use that root task instead of spelling out `check :taktx-engine:quarkusIntTest` separately.
- ✅ Kept `:taktx-engine:securityIntegrationTest` explicit in the plan because it remains the dedicated Ed25519/proto security regression suite.
- ✅ Fixed a remaining local end-to-end instability by switching Quarkus Kafka Dev Services in the `%test` and `%security-test` profiles to random host ports, avoiding collisions with local Kafka/Redpanda instances already bound to `9092`/`9093` while preserving profile isolation.
- ✅ Coverage and Sonar wiring remain unchanged and ready for release verification:
  - `badges/coverage-summary.json` still provides the current JaCoCo baseline summary
  - `sonar-project.properties` still imports the canonical JaCoCo XML reports from all six Java modules
- ✅ Manual release gate retained: Sonar quality-gate evaluation is still performed outside repository automation.

**Dependencies:** All preceding epics  
**Estimate:** 1 day (buffer for fixing edge cases)

---

## Dependency graph (simplified)

```
E1.1 ──► E1.2 ──► E1.3 ──► E1.4
           │
           ├──► E2.1 ──► E2.2 ──► E2.3
           │     │
           │     └──────────────────────────────────┐
           │                                         │
           ├──► E3.1 ──► E3.2                        │
           │     │        │                          │
           │     └──► E3.3                           │
           │                                         │
           ├──► E4.12  (can start as soon as         │
           │            PROTO-1.2 removes Jackson)   │
           │                                         │
           └──► E6.2                                 │
                                                     │
E3.1 ──► E4.2 ──► E4.3 ──► E4.4 ──► E4.5 ──► E4.6 ──► E4.7
          │                  │         │
          │                  └─────────┴──► E4.8 ──► E4.9 ──► E4.10 ──► E4.11
          │
          └──► E4.1 (after all E4.x complete, incl. E4.12)

E2.1 ──────────────────────────────────────────────► E5.1 ──► E5.2
E4.8 ──────────────────────────────────────────────►          E5.3
                                                               E5.4

E3 complete ──► E6.1
E2.1 ──────────► E6.3
All complete ──► E6.4
```

---

## Total effort summary

| Epic | Stories | Estimate |
|---|---|---|
| E1 — Schema & Build | 5 | 6 days |
| E2 — Variable System | 3 | 4.5 days |
| E3 — Kafka Serdes | 3 | 1.5 days |
| E4 — Engine Migration | 12 | 13.5 days |
| E5 — Client Libraries | 4 | 3 days |
| E6 — Test Hardening | 4 | 3.5 days |
| **Total** | **31** | **~32 days (6–7 weeks)** |

Single-developer estimate assuming full-time focus and no unrelated interruptions. E1.1 design review is the only hard synchronisation point. E4.12 (key serializers) can be parallelised with E2/E3 work after E1.2 completes.

---

## Items explicitly out of scope

- Migration guides or backward-compatible release paths (full version break, no compatibility required).
- Avro Schema Registry integration (decided against in favour of self-contained `.proto` files).
- Per-message compression (Kafka LZ4 batch compression is a producer config change, not a code change — do it alongside this work as `compression.type=lz4` in the Kafka producer properties).
- gRPC transport (this migration is wire-format only; Kafka remains the transport layer).
- Renaming the `taktx-shared` or `taktx-client` Maven artifact IDs (that is a separate release decision).
- Changing the binary layout of range-query store keys beyond what is specified in PROTO-4.12. The big-endian raw byte format is deliberately preserved; only the Jackson dependency is removed.
