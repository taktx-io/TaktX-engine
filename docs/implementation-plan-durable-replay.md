# Configurable Durable Replay Protection — Implementation Plan

**Repository:** `TaktX-engine2`
**Created:** 2026-04-20
**Last updated:** 2026-04-22
**Status:** In progress

---

## Goal

Implement durable replay protection for entry commands using canonical `auditId`, with runtime-configurable behavior via `GlobalConfigurationDTO`.

This feature must support:

- replay mode configured at runtime via `GlobalConfigurationDTO`
- replay retention configured separately from mode
- production strictness remaining a **separate control** from replay mode
- safe staged rollout from compatibility mode to strict enforcement

---

## Agreed semantics

### Replay protection mode

| Mode | Meaning | Blank/missing `auditId` | Duplicate `auditId` | Intended use |
|---|---|---|---|---|
| `OFF` | Replay enforcement disabled | Allowed | Allowed | local/dev, emergency rollback |
| `COMPAT` | Enforce replay only when usable `auditId` is present | Allowed | Rejected when `auditId` is non-blank | staged rollout / compatibility |
| `STRICT` | Full replay enforcement | Rejected | Rejected | hardened production posture |

### Separate controls

- `ReplayProtectionMode` governs replay enforcement only.
- `taktx.security.production-mode` remains separate and must **not** implicitly force replay mode.
- Replay retention is configured independently via `replayProtectionRetentionMs`.

---

## File map

### Shared contract

- `taktx-shared/src/main/java/io/taktx/dto/ReplayProtectionMode.java`
- `taktx-shared/src/main/java/io/taktx/dto/GlobalConfigurationDTO.java`
- `taktx-shared/src/main/java/io/taktx/security/RuntimeConfigurationHolder.java`

### Engine runtime + enforcement

- `taktx-engine/src/main/java/io/taktx/engine/config/GlobalConfigStore.java`
- `taktx-engine/src/main/java/io/taktx/engine/license/LicenseConfigProcessor.java`
- `taktx-engine/src/main/java/io/taktx/engine/security/EngineAuthorizationService.java`
- `taktx-engine/src/main/java/io/taktx/engine/security/NonceStore.java`
- `taktx-engine/src/main/java/io/taktx/engine/pd/Stores.java`
- `taktx-engine/src/main/java/io/taktx/engine/generic/TopologyProducer.java`
- new replay-guard classes under `taktx-engine/src/main/java/io/taktx/engine/security/`

### Client/runtime config propagation

- `taktx-client/src/main/java/io/taktx/client/TaktXClient.java`
- `taktx-client/src/main/java/io/taktx/client/RuntimeConfigurationStore.java`

### Tests

- `taktx-client/src/test/java/io/taktx/client/TaktXClientGlobalConfigPublishTest.java`
- `taktx-shared/src/test/java/io/taktx/security/RuntimeConfigurationHolderTest.java`
- `taktx-engine/src/test/java/io/taktx/engine/security/EngineAuthorizationServiceTest.java`
- `taktx-engine/src/test/java/io/taktx/engine/security/EngineAuthorizationServiceMatrixTest.java`
- `taktx-engine/src/test/java/io/taktx/engine/security/ReplayProtectionProcessorTest.java`
- `taktx-engine/src/test/java/io/taktx/engine/security/ReplayProtectionRestorationIntegrationTest.java`
- `taktx-engine/src/test/java/io/taktx/engine/pi/integration/SecurityIntegrationTest.java`

---

## Workstreams and tracking

### 1. Shared runtime configuration contract

- [x] Add `ReplayProtectionMode` enum with `OFF`, `COMPAT`, `STRICT`
- [x] Add `replayProtectionMode` to `GlobalConfigurationDTO`
- [x] Add `replayProtectionRetentionMs` to `GlobalConfigurationDTO`
- [x] Keep new fields appended at the end of array-serialized DTO for compatibility
- [x] Default mode to `COMPAT`
- [x] Default retention to `600_000` ms (10 minutes)

### 2. Runtime propagation and visibility

- [x] Expose replay settings from `RuntimeConfigurationHolder`
- [x] Include replay settings in engine config-update logs
- [x] Include replay settings in client config-update/startup logs
- [ ] Verify mixed old/new config reader compatibility explicitly in tests

### 3. Replay enforcement design refactor

- [x] Split JWT validation into reusable claim-validation helpers in `EngineAuthorizationService`
- [x] Keep `NonceStore` only as fallback for direct/non-topology authorization paths
- [x] Define canonical replay key shape as `<tenant>:<namespace>:<issuer>:<auditId>`
- [x] Define retention-expiry behavior in durable state (`replayProtectionRetentionMs` with lazy check + scheduled cleanup)

### 4. Durable topology and state store

- [x] Add replay store name to `Stores`
- [x] Add durable replay state store to the topology
- [x] Re-key protected entry commands by canonical replay identity hint before replay checking
- [x] Add replay guard processor before `ProcessInstanceProcessor`
- [x] Ensure replay survives restart with fresh local state via Kafka Streams state/changelog restore
- [ ] Add explicit reassignment/failover coverage beyond single-instance restart restore

### 5. Runtime toggle semantics

- [x] `OFF`: no replay checks, no shadow recording
- [x] `COMPAT`: reject duplicates for non-blank `auditId`
- [x] `STRICT`: require non-blank `auditId` and reject duplicates
- [ ] Document eventual-consistency behavior when toggling modes at runtime
- [ ] Decide whether mode changes should emit operator warnings on downgrade

### 6. Testing

- [x] Add/extend tests for config publishing defaults and replay fields
- [x] Add/extend tests for runtime-holder defaults and replay getters
- [x] Add unit tests for replay mode enforcement semantics
- [x] Add integration tests for duplicate rejection after restart with fresh local state restore
- [x] Add focused integration coverage for replayed JWT duplicate rejection on `process-instance`
- [ ] Add integration tests for `OFF → COMPAT → STRICT` rollout
- [ ] Add integration tests for `STRICT → OFF → STRICT` toggle semantics

### 7. Documentation

- [ ] Update `docs/security.md`
- [ ] Update `docs/security-hardening-backlog.md`
- [ ] Record final rollout guidance: deploy code first, then switch to `COMPAT`, then `STRICT`

---

## Risks and mitigations

### Risk: runtime toggles are eventually consistent

Config changes propagate through Kafka and are observed asynchronously.

**Mitigation:**

- document toggles as rollout controls, not high-frequency switches
- prefer monotonic rollout (`OFF` → `COMPAT` → `STRICT`)

### Risk: entry commands are keyed by process instance UUID, not replay identity

The same JWT can be replayed with a fresh process instance UUID.

**Mitigation:**

- re-key entry commands by canonical replay identity before replay checking

### Risk: `STRICT` breaks lagging JWT issuers

Some producers may still omit or mispopulate `auditId`.

**Mitigation:**

- ship `COMPAT` first
- move to `STRICT` only after compatibility validation

---

## Recommended rollout order

1. Ship shared contract + runtime visibility
2. Ship durable replay topology and tests
3. Enable `COMPAT` in non-production
4. Observe replay metrics/logging
5. Enable `STRICT` where issuer compliance is confirmed

---

## Current implementation slice

This implementation now delivers:

- new replay mode enum
- new runtime config fields with defaults
- runtime-holder accessors
- engine/client logging for replay settings
- replay routing hints on `ProcessInstanceTriggerEnvelope`
- durable replay store and replay guard processor in the process-instance topology
- `OFF` / `COMPAT` / `STRICT` enforcement for JWT-bearing entry commands
- focused unit + integration coverage for duplicate rejection, retention expiry, and changelog-backed restart restore

Still pending:

- explicit multi-node reassignment/failover restoration coverage beyond the restart-with-fresh-state test
- broader rollout-toggle integration scenarios
- documentation updates in `docs/security.md` and the main hardening backlog

