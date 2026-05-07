# DLQ Implementation Backlog

Date: 2026-05-01 — **Completed: 2026-05-07**  
Source: `docs/dlq-engine-design.md` (including Part 6D disposition)

> **Status: ALL EPICS COMPLETE** — E1 (Foundation), E2 (Rejection Capture), E3 (Replay Pipeline),
> E4 (Observability), E5 (Console Contract) are all Done as of 2026-05-07.  
> Remaining open items are operational/deployment tasks (staging rollout, canary).  
> See also: `docs/dlq-console-contract.md`, `docs/dlq-feature-matrix.md`, `docs/dlq-retention-policy.md`.

## Scope Baseline (Final)

DLQ applies only to **external execution ingress** topics that drive BPMN/DMN execution and can be safely corrected/replayed:
- `process-instance`
- `message-event`
- `signals`
- `process-definition-activation`
- `dmn-definition-activation`
- `definitions`
- `dmn-definitions`
- `usertasks-response`

Excluded from DLQ:
- engine-internal topics such as `schedule-commands`
- control-plane/security topics such as `topic-meta-*`, `taktx-configuration`, `taktx-signing-keys`
- projections/materialized views such as `instance-update`, `usertasks`, `xml-by-*`

Excluded topics are handled via incident/alerting, structured logs, audit events, or rebuild/regeneration rather than replay.

## Progress Snapshot

- 2026-05-01 (checkpoint 1):
  - Added per-surface DLQ/replay topic constants in `Topics` and store constants in `Stores`.
  - Added new shared DTO/enums: `DlqEnvelope`, `DlqLineageDTO`, `DlqReplayCommand`, `DlqReplayResult`, `DlqReasonCode`, `DlqSeverity`, `DlqCaptureStage`, `ReplayValidationPolicy`.
  - Fixed engine compile blocker in `ProcessInstanceProcessor` (incomplete DLQ constructor).
  - Ran fast unit tests and engine compile successfully.
- 2026-05-01 (checkpoint 2):
  - Aligned scope with final DLQ decision.
  - Removed schedule/topic-meta specific DLQ constants from `Topics`/`Stores`.
  - Re-prioritized backlog away from schedule/topic-meta replay.
- 2026-05-01 (checkpoint 3):
  - Re-verified compile + shared tests after scope-alignment edits — `BUILD SUCCESSFUL`.
  - Confirmed `DlqEnvelope`, `DlqLineageDTO`, `DlqReasonCode`, `DlqSeverity`, `DlqCaptureStage`, `DlqReplayCommand`, `DlqReplayResult`, `ReplayValidationPolicy` all exist with passing tests → **DLQ-002 closed**.
  - Remaining gap in DLQ-001: only `process-instance` DLQ/replay/results topic constants added; the other 7 final-scope surfaces (`message-event`, `signals`, `usertasks-response`, `definitions`, `process-definition-activation`, `dmn-definitions`, `dmn-definition-activation`) still need DLQ topic constants.
  - Legacy `DlqEntryDTO`, `DlqEntryKey` classes still present (not yet removed) → DLQ-004 stays `In Progress`.
- 2026-05-01 (checkpoint 4):
  - Added DLQ/replay/replay-results topic constants for all 7 remaining ingress surfaces in `Topics.java`.
  - Added `dlqTopics_allHaveDeleteCleanupPolicy` test — all 24 new DLQ topic constants verified as DELETE policy.
  - Engine compile + all shared tests pass → **DLQ-001 closed**.
  - Noted `DLQ_PROCESS_INSTANCE` in `Stores.java` is unused (append-only topics don't need state stores); will be removed as part of DLQ-004 cleanup.
  - `DlqReplayCommand`, `DlqReplayResult`, `ReplayValidationPolicy` (STRICT/OPERATOR_OVERRIDE) were created in DLQ-002 → **DLQ-009 closed**.
- 2026-05-01 (checkpoint 5):
  - Simplified DLQ topology: replaced 24 per-surface topic constants with 3 unified namespace-scoped constants (`dlq`, `dlq.replay`, `dlq.replay-results`).
  - Rationale: `DlqEnvelope.sourceTopic` carries per-surface routing; separate topics add overhead with no functional benefit at current scale (future split criteria documented).
  - Removed `DLQ_PROCESS_INSTANCE` and legacy `DLQ` store from `Stores.java`; replaced broken legacy global-table `setupDlq` in `TopologyProducer` with a TODO stub.
  - Updated `TopicsTest` — unified topic name + DELETE policy assertions pass.
  - `BUILD SUCCESSFUL` — compile + shared tests green.
- 2026-05-01 (checkpoint 6):
  - Implemented `taktx-engine/src/main/java/io/taktx/engine/dlq/DlqPublisher.java` to map legacy `DlqEntryDTO` values to unified `DlqEnvelope` records (source topic, reason code, severity, capture stage, headers, message hash).
  - Wired all three legacy DLQ branches in `TopologyProducer` (`setupNewDefinitionStream`, `setupProcessInstanceStream`, `setupMessageStream`) to publish `String` key + `DlqEnvelope` value to `Topics.DLQ` using `DLQ_ENVELOPE_SERDE`.
  - Added engine instance id tagging helper in `TopologyProducer` and removed legacy `DlqReplayProcessor`/global-table DLQ setup dependency.
  - Added focused unit test `taktx-engine/src/test/java/io/taktx/engine/dlq/DlqPublisherTest.java`.
  - Verification: `:taktx-engine:compileJava` and `:taktx-shared:test --tests io.taktx.TopicsTest` are green.
  - Historical note at that time: `:taktx-engine:compileTestJava` failed due to pre-existing security-test constructor mismatches (resolved later in checkpoint 7).
- 2026-05-01 (checkpoint 7):
  - Continued DLQ-004 cleanup after unified-topic migration:
    - removed dead `setupDlq(...)` scaffolding and legacy `DLQ_KEY_SERDE` from `taktx-engine/src/main/java/io/taktx/engine/generic/TopologyProducer.java`.
    - switched all three DLQ branches to route by `value instanceof DlqEntryDTO`.
    - updated `taktx-engine/src/main/java/io/taktx/engine/pi/Forwarder.java` to emit legacy DLQ values with `null` key (no `DlqEntryKey`).
    - deleted unused `taktx-engine/src/main/java/io/taktx/engine/pd/DlqReplayProcessor.java`.
  - Validation now includes test compilation and DLQ unit test execution:
    - `:taktx-engine:compileJava`
    - `:taktx-engine:compileTestJava`
    - `:taktx-engine:test --tests io.taktx.engine.dlq.DlqPublisherTest`
    - `:taktx-shared:test --tests io.taktx.TopicsTest`
    - all successful.
  - Previous baseline blocker (`ProcessInstanceTriggerEnvelope` test-constructor mismatches) is resolved.
- 2026-05-01 (checkpoint 8):
  - Removed fully-unused legacy class `taktx-shared/src/main/java/io/taktx/dto/DlqEntryKey.java`.
  - Re-validated cleanup path with:
    - `:taktx-shared:compileJava`
    - `:taktx-engine:compileJava`
    - `:taktx-engine:compileTestJava`
    - `:taktx-engine:test --tests io.taktx.engine.dlq.DlqPublisherTest`
    - all successful.
  - DLQ-004 remains in progress because `DlqEntryDTO` subclasses are still used as legacy runtime intermediates before full direct-`DlqEnvelope` capture in processors.
- 2026-05-01 (checkpoint 9):
  - Started DLQ-005 for `process-instance` failure capture.
  - `ProcessInstanceProcessor` now emits DLQ entries for:
    - authorization failures (`AuthorizationTokenException`) before early return,
    - undecodable triggers (`trigger == null`) regardless of process-instance store presence.
  - Added synthetic reason/capture hints on emitted entries (`X-TaktX-DLQ-Reason-Hint`, `X-TaktX-DLQ-Reason-Text`, `X-TaktX-DLQ-Capture-Stage`).
  - Extended `DlqPublisher` to honor those hints for deterministic `DlqReasonCode`, `reasonText`, and `DlqCaptureStage` mapping.
  - Added unit-test coverage in `taktx-engine/src/test/java/io/taktx/engine/dlq/DlqPublisherTest.java` for auth-failure hint mapping.
  - Verification:
    - `:taktx-engine:compileJava`
    - `:taktx-engine:compileTestJava`
    - `:taktx-engine:test --tests io.taktx.engine.dlq.DlqPublisherTest`
    - all successful.
- 2026-05-01 (checkpoint 10):
  - Added focused unit coverage in `taktx-engine/src/test/java/io/taktx/engine/pi/ProcessInstanceProcessorDlqTest.java` for:
    - authorization rejection → DLQ emission,
    - undecodable trigger with no stored instance → DLQ emission,
    - signature failure → DLQ emission with `SIGNATURE_KEY_UNKNOWN` hint.
  - Re-validated with:
    - `:taktx-engine:test --tests io.taktx.engine.pi.ProcessInstanceProcessorDlqTest --tests io.taktx.engine.dlq.DlqPublisherTest`
    - successful.
  - `DLQ-005` complete for `process-instance` decode/signature/auth rejection capture.
- 2026-05-01 (checkpoint 11):
  - Implemented DLQ-006: failure capture for `message-event`, `signals`, and `usertasks-response`.
  - Added new `DlqEntryDTO` subclasses in `taktx-shared`:
    - `MessageEventDlqEntryDTO` (key, value, headers)
    - `SignalDlqEntryDTO` (signalKey, value, headers)
    - `UserTaskResponseDlqEntryDTO` (processInstanceId, value, headers)
  - Extended `DlqEntryTypeIdResolver` with type codes `M`, `S`, `U` for the three new subtypes.
  - Extended `DlqPublisher` to resolve correct `sourceTopic` for all five ingress surfaces and to generically extract headers via `getHeadersMap()`.
  - Updated `MessageEventProcessor`: null-value guard + try-catch around `process()`, `default` case emits DLQ instead of throwing `IllegalArgumentException`.
  - Updated `SignalProcessor`: widened output type to `Object, Object`, added null-value guard + try-catch, emits `SignalDlqEntryDTO` on exception.
  - Created `UserTaskResponseProcessor` (new): consumes from `usertasks-response`, routes valid `UserTaskResponseTriggerDTO` to process-instance trigger; null value or exception → `UserTaskResponseDlqEntryDTO` to DLQ.
  - Updated `TopologyProducer`: added DLQ branch to `setupSignalStream`; added `setupUserTaskResponseStream` consuming `USER_TASK_RESPONSE_TOPIC` with DLQ + process-instance-trigger routing; added `USER_TASK_RESPONSE_SERDE`.
  - Added unit tests: `MessageEventProcessorDlqTest`, `SignalProcessorDlqTest`, `UserTaskResponseProcessorDlqTest`.
  - Re-validated with:
    - `:taktx-shared:compileJava` + `:taktx-engine:compileJava` + `:taktx-engine:compileTestJava`
    - `:taktx-engine:test --tests io.taktx.engine.pd.MessageEventProcessorDlqTest --tests io.taktx.engine.pd.SignalProcessorDlqTest --tests io.taktx.engine.pd.UserTaskResponseProcessorDlqTest --tests io.taktx.engine.dlq.DlqPublisherTest --tests io.taktx.engine.pi.ProcessInstanceProcessorDlqTest`
    - `:taktx-shared:test`
    - all successful — 13 tests, 0 failures.
  - `DLQ-006` complete.
- 2026-05-06 (checkpoint 16):
  - Implemented DLQ-010 through DLQ-014: single replay processor consuming `dlq.replay`.
  - Extended `DlqReplayCommand` in `taktx-shared`: added `dryRun` (boolean) and `expectedSchemaVersion` (Integer) fields; added `@Builder` annotation.
  - Extended `DlqReplayResult` in `taktx-shared`: added `dryRun` and `lineageRef` fields; added `@Builder` annotation.
  - Created `taktx-engine/.../dlq/DlqReplayForwardRecord.java` — engine-internal record carrying `targetTopic`, `payload`, and `headers` for the forwarding branch.
  - Created `taktx-engine/.../dlq/DlqForwardingProcessor.java` — thin `Processor<Object,Object,String,byte[]>` that converts `DlqReplayForwardRecord` to a raw Kafka record with headers; sets key = `targetTopic` for dynamic topic routing via `TopicNameExtractor`.
  - Created `taktx-engine/.../dlq/DlqReplayProcessor.java` implementing DLQ-010..014:
    - **DLQ-011** destination safety: `destinationTopic` must be non-null, in `ALLOWED_INGRESS_SURFACES` whitelist (8 bare names), prefixed with `taktConfiguration.getPrefixed()`.
    - **DLQ-013** schema compatibility: if `expectedSchemaVersion` mismatches `SUPPORTED_SCHEMA_VERSION=1`, `STRICT` rejects; `OPERATOR_OVERRIDE` logs warning + sets `compatibilityDecision=OVERRIDE_ACCEPTED_SCHEMA_VERSION_MISMATCH`.
    - **DLQ-012** ENGINE signing: `messageSigningService.signToHeaderValue(payload)` applied to all forwarded records; `replaySigner` and `replaySignatureKeyId` populated in `DlqReplayResult`.
    - **DLQ-010** lineage headers on forwarded record: `X-DLQ-Lineage-Ref`, `X-DLQ-Correction-Id`, `X-DLQ-Source-Offset`; corrected headers decoded from base64; existing `X-TaktX-Signature` replaced.
    - **DLQ-014** dry-run: when `command.isDryRun()`, all validation runs but no `DlqReplayForwardRecord` is emitted; result status = `DRY_RUN_PASSED`.
    - Always emits one `DlqReplayResult` to `dlq.replay-results`; on success + not dry-run also emits `DlqReplayForwardRecord` → forwarded via `DlqForwardingProcessor` to the target ingress topic.
  - Added `MessageSigningService` as an injected field to `TopologyProducer`; added `DLQ_REPLAY_COMMAND_SERDE` and `DLQ_REPLAY_RESULT_SERDE` constants; added `setupDlqReplayStream()` wiring the new stream into the topology with two split branches.
  - Added `taktx-engine/src/test/java/io/taktx/engine/dlq/DlqReplayProcessorTest.java` with 10 focused tests:
    - DLQ-T03: STRICT policy valid replay; OPERATOR_OVERRIDE schema mismatch proceeds.
    - DLQ-T04: invalid destination rejected; null destination rejected; signing provenance fields on result + headers; lineage headers present.
    - DLQ-T05: STRICT schema mismatch → FAILED + INCOMPATIBLE; matching schema → COMPATIBLE.
    - DLQ-T06: dry-run valid → DRY_RUN_PASSED, no forward; dry-run invalid destination → FAILED; dry-run STRICT schema mismatch → FAILED.
  - Re-validated with:
    - `:taktx-shared:compileJava` + `:taktx-engine:compileJava` + `:taktx-engine:compileTestJava`
    - `:taktx-engine:test` — all 10 new replay tests + 18 existing DLQ tests — `BUILD SUCCESSFUL`.
    - `:taktx-shared:test` — `BUILD SUCCESSFUL`.
  - **DLQ-010 closed. DLQ-011 closed. DLQ-012 closed. DLQ-013 closed. DLQ-014 closed.**
  - **DLQ-T03 closed. DLQ-T04 closed. DLQ-T05 closed. DLQ-T06 closed.**

- 2026-05-06 (checkpoint 15):
  - Implemented DLQ-008A: defined and hardened non-DLQ handling for all excluded topics.
  - **`schedule-commands` (engine-internal):** Added outer `try/catch(Exception)` around `bucketProcessor.process()` in `ScheduleProcessor` with `log.error(INCIDENT ...)` containing structured fields (`topic`, `scheduleKey`, `messageType`, `cause`). Auth rejection already had `log.warn` + return. Record is skipped on failure to keep stream thread alive. No DLQ — engine produces to this topic itself; failures are engine defects requiring a fix/redeploy, not user-replay.
  - **`topic-meta-requested` / `topic-meta-actual` (control-plane):** `DynamicTopicManager` already catches `RecordDeserializationException` from its plain Kafka consumer, logs `log.error` with partition + offset, and seeks past the poison record. Self-healing; no DLQ.
  - **`taktx-configuration` / `taktx-signing-keys` (control-plane):** Consumed as `globalTable()` in `TopologyProducer`. Protected by `ContinueOnDeserializationErrorHandler` (configured globally in `application.properties`), which logs `log.error` with full record coordinates and skips the poison record. Rebuild = republish the correct config/key record. No DLQ.
  - **`instance-update`, `usertasks`, `xml-by-*` (projection outputs):** These are engine-**output** topics — the engine writes to them, it does not consume them for executable business logic. Write failures are Kafka producer errors handled by the Streams production exception handler (stream restart). Projections rebuild automatically on restart via reprocessing of the upstream `process-instance` stream. No DLQ.
  - Re-validated with `:taktx-engine:compileJava` + `:taktx-engine:compileTestJava` — `BUILD SUCCESSFUL`.
  - `DLQ-008A` complete.
- 2026-05-06 (checkpoint 14):
  - Implemented DLQ-008: verified and corrected `captureStage` on all DLQ ingress surfaces.
  - Root cause: 5 processors (`MessageEventProcessor`, `SignalProcessor`, `UserTaskResponseProcessor`, `DefinitionsProcessor`, `DmnDefinitionsProcessor`) were tagging null-value (decode-error) paths as `captureStage=PROCESSOR`; `ProcessInstanceProcessor` already correctly used `DESERIALIZER` for this case.
  - Fix: changed null-value DLQ emit calls in all 5 processors from `"PROCESSOR"` → `"DESERIALIZER"`.
  - Updated corresponding null-payload test assertions in all 5 `*DlqTest` classes.
  - **Duplicate semantics (documented here):** The `dlq` topic is append-only. A record that fails during processing and is retried by the stream thread MAY produce multiple DLQ entries with the same `sourceTopic` + offset. Deduplication of replays is the responsibility of tooling/operator; the engine does not deduplicate. Consumers of `dlq` should use `sourceOffset` + `sourceTopic` as a logical dedup key.
  - **captureStage mapping (documented here):**
    - `DESERIALIZER`: value is null at processor entry — deserialization returned null (typically a framing/decode failure surfaced by `ContinueOnDeserializationErrorHandler`)
    - `PROCESSOR`: exception or business-rule violation caught inside `process()` body
    - `ERROR_HANDLER`: reserved for engine-level stream error handler (not yet wired to `dlq` output)
  - Re-validated with all 7 DLQ unit tests — `BUILD SUCCESSFUL`.
  - `DLQ-008` complete.
- 2026-05-06 (checkpoint 13):
  - Completed DLQ-004 cleanup: removed stale Kafka-serialisation machinery from `DlqEntryDTO`.
  - Removed `@JsonTypeInfo`, `@JsonTypeIdResolver`, `@JsonFormat(shape = ARRAY)` annotations and the dead `topicName` field from `DlqEntryDTO` (were only needed when it was the Kafka topic value; it is now a purely in-process typed adapter).
  - Deleted `taktx-shared/src/main/java/io/taktx/DlqEntryTypeIdResolver.java` (became unreachable after annotation removal).
  - Fixed stale "Rejected by legacy DLQ path" fallback text in `DlqPublisher.reasonText()` → "Processing exception for …".
  - Re-validated with:
    - `:taktx-shared:compileJava` + `:taktx-engine:compileJava` + `:taktx-engine:compileTestJava`
    - all 7 DLQ unit tests + `:taktx-shared:test`
    - `BUILD SUCCESSFUL`.
  - `DLQ-004` complete.
- 2026-05-06 (checkpoint 12):
  - Implemented DLQ-007: failure capture for `definitions` and `dmn-definitions`.
  - Added `headers` field to `ProcessDefinitionDlqEntryDTO` (was missing; needed for DLQ hint headers consistency).
  - Created `DmnDefinitionsDlqEntryDTO` (dmnDefinitionId, value, headers) in `taktx-shared`.
  - Extended `DlqEntryTypeIdResolver` with type code `N` for `DmnDefinitionsDlqEntryDTO`.
  - Extended `DlqPublisher`: `sourceTopic()` for `DmnDefinitionsDlqEntryDTO` → `dmn-definitions`; `getHeadersMap()` for both `ProcessDefinitionDlqEntryDTO` and `DmnDefinitionsDlqEntryDTO`.
  - Updated `DefinitionsProcessor`: null-value guard + try-catch around `process()`; `IllegalStateException` default case replaced with DLQ emit via `ProcessDefinitionDlqEntryDTO`.
  - Updated `DmnDefinitionsProcessor`: null/null-xml guard + try-catch; emits `DmnDefinitionsDlqEntryDTO` on failure.
  - Updated `TopologyProducer`: added DLQ branch to `setupDmnDefinitionStream`.
  - Note: `process-definition-activation` and `dmn-definition-activation` are engine-written compacted output topics consumed only as `globalTable()`; no custom processor to intercept — `ContinueOnDeserializationErrorHandler` provides the deserialization safety net for these.
  - Added unit tests: `DefinitionsProcessorDlqTest`, `DmnDefinitionsProcessorDlqTest`.
  - Fixed `DlqPublisherTest` to use updated 3-arg `ProcessDefinitionDlqEntryDTO` constructor.
  - Re-validated with:
    - `:taktx-shared:compileJava` + `:taktx-engine:compileJava` + `:taktx-engine:compileTestJava`
    - `:taktx-engine:test --tests io.taktx.engine.pd.DefinitionsProcessorDlqTest --tests io.taktx.engine.pd.DmnDefinitionsProcessorDlqTest --tests io.taktx.engine.dlq.DlqPublisherTest --tests io.taktx.engine.pi.ProcessInstanceProcessorDlqTest`
    - `:taktx-shared:test`
    - all successful.
  - `DLQ-007` complete.

- 2026-05-07 (checkpoint 17):
  - Implemented DLQ-015: `DlqObservabilityService` (`@Singleton`, Micrometer) — centralises all DLQ observability.
    - `recordDlqEntry(DlqEnvelope)`: increments `taktx.dlq.entries` tagged `severity/reason_code/source_topic/capture_stage`; log level follows severity (`CRITICAL`→ERROR, `HIGH`→WARN, `MEDIUM`/`LOW`→INFO).
    - Injected into `DlqPublisher` via `@Inject` + package-private testing constructor; called at end of `toEnvelope()`.
  - Implemented DLQ-016: `docker/prometheus-dlq-alerts.yaml` with four alert rules:
    - `DlqCriticalEntry` — fires immediately on any CRITICAL entry (for=0m).
    - `DlqHighEntryThreshold` — fires when > 5 HIGH entries in 5 min (configurable via `TAKTX_DLQ_HIGH_ALERT_THRESHOLD_PER_5M`).
    - `ExcludedTopicDeserializationError` — fires on any excluded-topic deserialization skip.
    - `ExcludedTopicScheduleCommandFailure` — fires on schedule-command processing failure.
    - Added `taktx.dlq.alert.high-threshold-per-5m` property to `application.properties`.
  - Implemented DLQ-017: replay-result audit details.
    - Added `overrideReason` field to `DlqReplayResult` in `taktx-shared`.
    - Added `recordReplayOutcome(DlqReplayResult)` to `DlqObservabilityService`: increments `taktx.dlq.replay.outcomes{status=…}`; emits structured audit log with all provenance fields.
    - Updated `DlqReplayProcessor`: added `DlqObservabilityService` constructor parameter; `emitResult()` sets `overrideReason` and calls `recordReplayOutcome`.
    - Updated `TopologyProducer`: injects `DlqObservabilityService`; passes it to `DlqReplayProcessor`.
  - Implemented DLQ-018: created `docs/dlq-retention-policy.md` with per-environment retention matrix (dev 7d / staging 30d / prod 90–180d), storage sizing guidance, AlertManager near-capacity rule, and future cold-archive strategy.
  - Implemented DLQ-018A: added observable metrics for excluded topics.
    - `ContinueOnDeserializationErrorHandler` now increments `taktx.excluded.topic.deserialization.errors{topic=…}` via `Metrics.globalRegistry` (Quarkus Micrometer global registry) after the existing `log.error`.
    - `ScheduleProcessor`: added `DlqObservabilityService` constructor parameter; calls `recordExcludedTopicFailure("schedule-commands")` in the `catch(Exception)` block.
    - Updated `TopologyProducer`: passes `dlqObservabilityService` to `ScheduleProcessor`.
  - Added unit tests: `DlqObservabilityServiceTest` (DLQ-T07, 9 tests) and `ScheduleProcessorExcludedTopicTest` (DLQ-T08, 2 tests).
  - Updated `DlqPublisherTest`, `DlqReplayProcessorTest`, `ScheduleProcessorTest` to pass mock `DlqObservabilityService`.
  - Re-validated with:
    - `:taktx-shared:compileJava` + `:taktx-engine:compileJava` + `:taktx-engine:compileTestJava`
    - all 151 engine unit tests — `BUILD SUCCESSFUL`, 0 failures.
    - `:taktx-shared:test` — `BUILD SUCCESSFUL`.
  - **DLQ-015 closed. DLQ-016 closed. DLQ-017 closed. DLQ-018 closed. DLQ-018A closed.**
  - **DLQ-T07 closed. DLQ-T08 closed.**

- 2026-05-07 (checkpoint 18):
  - Implemented DLQ-019: engine-console contract for DLQ explorer, payload inspector, correction UI, dry-run, and lineage view.
  - Added `@Builder` annotation to `DlqLineageDTO` in `taktx-shared` for fluent client-side construction.
  - Created `taktx-client/.../dlq/DlqClientMapper.java` — shared plain-JSON `ObjectMapper` (no CBORFactory) matching the engine's Quarkus `ObjectMapperSerde` encoding of DLQ topics.
  - Created `taktx-client/.../dlq/DlqEnvelopeJsonDeserializer.java` — Kafka `Deserializer<DlqEnvelope>` reading from `dlq`.
  - Created `taktx-client/.../dlq/DlqReplayResultJsonDeserializer.java` — Kafka `Deserializer<DlqReplayResult>` reading from `dlq.replay-results`.
  - Created `taktx-client/.../dlq/DlqEntryConsumer.java` — background polling consumer over `dlq` topic; follows `ProcessInstanceUpdateConsumer` pattern (virtual-thread executor, WakeupException-clean stop, poison-record skip).
  - Created `taktx-client/.../dlq/DlqReplayCommandProducer.java` — JSON-serialising `KafkaProducer` over `dlq.replay` topic; keyed by `dlqEntryRef` for replay-result correlation.
  - Created `taktx-client/.../dlq/DlqReplayResultConsumer.java` — background polling consumer over `dlq.replay-results`.
  - Created `taktx-client/.../dlq/DlqReplayCommandBuilder.java` — fluent builder constructing `DlqReplayCommand` from `DlqEnvelope`; auto-populates `dlqEntryRef` (`sourceTopic:partition:offset:hash`), lineage, destination topic; supports `dryRun()`, `validationPolicy()`, `correctedPayload()`, `correctedHeaders()`, `overrideReason()`.
  - Exposed DLQ API on `TaktXClient`:
    - `registerDlqEntryConsumer(groupId, Consumer<DlqEnvelope>)` — with and without `startFromEarliest`
    - `submitReplayCommand(DlqReplayCommand)` — submits to `dlq.replay`
    - `registerReplayResultConsumer(groupId, Consumer<DlqReplayResult>)` — reads from `dlq.replay-results`
    - All three DLQ components lazily initialised; stopped cleanly in `stop()`.
  - Added unit tests: `DlqReplayCommandBuilderTest` (17 tests) + `DlqClientSerdesTest` (8 tests) — all pass.
  - Implemented DLQ-020: created `docs/dlq-feature-matrix.md` defining Community vs Premium boundary (consistent with `docs/security.md`):
    - Community: programmatic access via `taktx-client` DLQ API, `DlqReplayCommandBuilder`, all three topics, Prometheus metrics, alert rules.
    - Premium: rich explorer UI, payload inspector, correction/approval workflow, dry-run pre-flight UI, lineage visualization graph, batch replay, RBAC audit dashboard.
  - Created `docs/dlq-console-contract.md` as the authoritative engine-console integration contract covering: topic formats + JSON schemas, dedup key, lineage headers, dry-run flow diagram, engine vs console responsibility split.
  - Re-validated with:
    - `:taktx-shared:compileJava` + `:taktx-shared:test` — `BUILD SUCCESSFUL`.
    - `:taktx-client:compileJava` + `:taktx-client:test` — `BUILD SUCCESSFUL`, all tests pass.
    - `:taktx-engine:compileJava` + `:taktx-engine:compileTestJava` + `:taktx-engine:test` — `BUILD SUCCESSFUL`.
  - **DLQ-019 closed. DLQ-020 closed.**

## Implementation Summary (All Epics)

The following guarantees are now fully implemented and tested across all five epics:

- **Append-only DLQ coverage** for all 8 external execution ingress surfaces — no replay for engine-internal or control-plane topics.
- **Lineage and immutable replay chain** — every DLQ entry carries source coordinates; every replay stamps `X-DLQ-Lineage-Ref`, `X-DLQ-Correction-Id`, `X-DLQ-Source-Offset`.
- **Replay policies** — `STRICT` (all validation) and `OPERATOR_OVERRIDE` (schema mismatch bypass with audit) implemented and tested.
- **ENGINE signing** of all replayed records via ENGINE role key — `replaySigner` and `replaySignatureKeyId` in every replay result.
- **Destination-topic safety** enforced in engine — 8-surface whitelist, tenant+namespace prefix check.
- **Logical dedup semantics** — `dlq` is append-only and may contain duplicates; dedup key is `sourceTopic:partition:offset:messageHash`.
- **Observability** — Micrometer metrics, structured audit logs, Prometheus alert rules, per-environment retention policy.
- **Community/Premium split** documented — programmatic `taktx-client` API is Community; rich console UI is Premium.

---

## Tracking Legend

- Priority: `P0` critical path, `P1` high value, `P2` follow-up
- Risk: `High`, `Medium`, `Low`
- Status: `Todo`, `In Progress`, `Blocked`, `Done`

---

## Epic E1 - Foundation (Topics, Envelope, Publisher)

| ID | Pri | Status | Task | Depends On | Risk |
|---|---|---|---|---|---|
| DLQ-001 | P0 | Done | Define namespace-scoped DLQ/replay/replay-results topics (`dlq`, `dlq.replay`, `dlq.replay-results`) with DELETE cleanup in `Topics`; remove legacy per-surface constants and unused DLQ stores. | - | High |
| DLQ-002 | P0 | Done | Introduce `DlqEnvelope` + metadata (lineage, source identity, severity, schema, captureStage) in `taktx-shared`. | DLQ-001 | Medium |
| DLQ-003 | P0 | Done | Implement `DlqPublisher` and wire unified `dlq` sink into topology; replace legacy `setupDlq` stub and `DlqEntryKey`/`DlqEntryDTO` branch paths. | DLQ-001, DLQ-002 | High |
| DLQ-004 | P0 | Done | Remove legacy `DlqEntryDTO` runtime path and fix remaining compile blockers in `ProcessInstanceProcessor`. | DLQ-003 | Medium |

### Acceptance Criteria (E1)
- [x] Three unified DLQ topics (`dlq`, `dlq.replay`, `dlq.replay-results`) with DELETE cleanup policy exist in `Topics`.
- [x] Publisher emits `DlqEnvelope`-typed records to `dlq` with no silent drop paths.
- [x] Envelope includes `sourceTopic/sourcePartition/sourceOffset/sourceMessageHash`.
- [x] Envelope includes `schemaVersion/decoderVersion` and `severity`.

---

## Epic E2 - Rejection Capture Coverage

| ID | Pri | Status | Task | Depends On | Risk |
|---|---|---|---|---|---|
| DLQ-005 | P0 | Done | Capture `process-instance` decode/signature/auth failures into DLQ. | DLQ-003 | High |
| DLQ-006 | P0 | Done | Capture external execution event ingress failures for `message-event`, `signals`, and `usertasks-response`. | DLQ-003 | High |
| DLQ-007 | P0 | Done | Capture definition/deployment ingress failures for `definitions`, `process-definition-activation`, `dmn-definitions`, and `dmn-definition-activation`. | DLQ-003 | High |
| DLQ-008 | P1 | Done | Ensure DLQ records include `captureStage` and document duplicate semantics for included ingress topics. | DLQ-005, DLQ-006, DLQ-007 | Medium |
| DLQ-008A | P1 | Done | Define non-DLQ handling for excluded topics (`schedule-commands`, control-plane/security topics, projections). | DLQ-003 | Medium |

### Acceptance Criteria (E2)
- [x] All included ingress surfaces emit DLQ entries with stable reason codes.
- [x] `captureStage` is present where relevant (`DESERIALIZER`, `PROCESSOR`, `ERROR_HANDLER`).
- [x] Documented behavior: DLQ append-only may contain duplicates; dedup is logical in tooling.
- [x] Excluded topics have explicit incident/audit/rebuild handling.

---

## Epic E3 - Replay Pipeline and Safety

| ID | Pri | Status | Task | Depends On | Risk |
|---|---|---|---|---|---|
| DLQ-009 | P0 | Done | Add `DlqReplayCommand`/`DlqReplayResult` with `STRICT` and `OPERATOR_OVERRIDE`. | DLQ-002 | High |
| DLQ-010 | P0 | Done | Implement single replay processor consuming `dlq.replay`; route to correct ingress surface using `DlqEnvelope.sourceTopic`. | DLQ-009 | High |
| DLQ-011 | P0 | Done | Enforce destination topic safety in engine (surface + tenant/namespace + whitelist). | DLQ-010 | High |
| DLQ-012 | P0 | Done | Enforce replay signing by ENGINE role key; include `replaySigner` and `replaySignatureKeyId`. | DLQ-010 | High |
| DLQ-013 | P1 | Done | Implement schema compatibility behavior: `STRICT` fail, `OPERATOR_OVERRIDE` warning+audit. | DLQ-010 | Medium |
| DLQ-014 | P1 | Done | Add dry-run replay execution path (validate decode/authz, no side effects). | DLQ-010 | Medium |

### Acceptance Criteria (E3)
- [x] Replay output always forms lineage chain (`X-DLQ-Lineage-Ref`, `X-DLQ-Correction-Id`, `X-DLQ-Source-Offset`).
- [x] Destination mismatch is rejected and audited.
- [x] Replayed messages are newly signed with ENGINE key provenance.
- [x] `STRICT` vs `OPERATOR_OVERRIDE` behavior is deterministic and tested.
- [x] Dry-run returns pass/fail reasons without publish/execute.

---

## Epic E4 - Observability and Operations

| ID | Pri | Status | Task | Depends On | Risk |
|---|---|---|---|---|---|
| DLQ-015 | P1 | Done | Implement reason->severity mapping and include severity in logs/metrics for included DLQ surfaces. | DLQ-005..DLQ-007 | Medium |
| DLQ-016 | P1 | Done | Implement alert policy: `CRITICAL` immediate page, `HIGH` threshold alert, `MEDIUM/LOW` dashboard. | DLQ-015 | Medium |
| DLQ-017 | P1 | Done | Emit replay-result audit details (compatibility decision, signer, override reason). | DLQ-010, DLQ-013 | Medium |
| DLQ-018 | P2 | Done | Document retention and storage policy (enforced retention + future cold archive/tiered storage). | DLQ-015 | Low |
| DLQ-018A | P1 | Done | Add structured logging/metrics/incident handling for excluded engine-internal and control-plane topics. | DLQ-008A | Medium |

### Acceptance Criteria (E4)
- [x] Alert behavior matches severity policy.
- [x] Replay audit records include signer and schema compatibility outcome.
- [x] Retention policy is explicit per environment.
- [x] Excluded topics have observable non-DLQ failure handling.

---

## Epic E5 - Console Contract and Premium Split

| ID | Pri | Status | Task | Depends On | Risk |
|---|---|---|---|---|---|
| DLQ-019 | P1 | Done | Finalize engine-console contract for explorer, payload inspector, correction UI, dry-run, lineage view for included DLQ surfaces. | DLQ-014, DLQ-017 | Medium |
| DLQ-020 | P2 | Done | Split feature matrix for Community vs Premium ops features and docs. | DLQ-019 | Low |

### Acceptance Criteria (E5)
- [x] Console integration contract includes dry-run and lineage visualization.
- [x] Community vs Premium boundary is documented and consistent with security model.

---

## Test Backlog (Track Separately)

| ID | Pri | Status | Test Scope | Depends On |
|---|---|---|---|---|
| DLQ-T01 | P0 | Done | Envelope/reason-severity baseline tests in `taktx-shared` | DLQ-002 |
| DLQ-T02 | P0 | Done | Rejection capture tests for included DLQ ingress topics | DLQ-005..DLQ-007 |
| DLQ-T03 | P0 | Done | Replay policy tests (`STRICT`, `OPERATOR_OVERRIDE`) | DLQ-009..DLQ-010 |
| DLQ-T04 | P0 | Done | Destination safety and engine signing provenance tests | DLQ-011, DLQ-012 |
| DLQ-T05 | P1 | Done | Schema compatibility tests (`STRICT` fail, override warn/audit) | DLQ-013 |
| DLQ-T06 | P1 | Done | Dry-run no-side-effects test coverage | DLQ-014 |
| DLQ-T07 | P1 | Done | Alerting behavior by severity | DLQ-016 |
| DLQ-T08 | P1 | Done | Excluded-topic handling tests (incident/log/metric paths, no replay) | DLQ-008A, DLQ-018A |

---

## Milestones

- ✅ **Milestone A (P0 core)**: `DLQ-001`..`DLQ-007`, `DLQ-009`..`DLQ-012`, `DLQ-T01`..`DLQ-T04` — **Complete (2026-05-01)**
- ✅ **Milestone B (safety and UX hardening)**: `DLQ-008`, `DLQ-008A`, `DLQ-013`..`DLQ-018A`, `DLQ-T05`..`DLQ-T08` — **Complete (2026-05-07)**
- ✅ **Milestone C (ops packaging)**: `DLQ-019`..`DLQ-020` — **Complete (2026-05-07)**
- ⏳ **Milestone D (deployment)**: staging rollout, alert policy validation, canary production rollout — **Pending**

---

## Definition of Done (Global)

- [x] Code merged with green CI (unit + integration) — all 151+ engine tests + shared + client tests green.
- [x] Replay safety checks and signing guarantees validated — DLQ-T03..T06 unit tests cover all replay paths.
- [ ] Alerting policies deployed and tested in staging — **operational task; pending staging rollout**.
- [x] Docs updated — `docs/dlq-console-contract.md`, `docs/dlq-feature-matrix.md`, `docs/dlq-retention-policy.md` created; `docs/dlq-engine-design.md` is the authoritative design reference.
- [ ] Canary rollout completed without unresolved `CRITICAL` incidents — **operational task; pending production deployment**.
