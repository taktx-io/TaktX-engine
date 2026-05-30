# Simplified Security Posture — Implementation Backlog

**Status:** Active implementation tracker  
**Date:** 2026-05-30  
**Primary input:** `docs/SECURITY-POLICY-ENGINE-DELTA-HANDOFF.md`

## 1. Purpose

This document tracks the upstream engine/client/shared work needed to deliver the simplified
operator-facing namespace security posture model requested in
`docs/SECURITY-POLICY-ENGINE-DELTA-HANDOFF.md`.

It is intentionally task-oriented and should be updated as implementation progresses.

## 2. Delivery strategy

The work is being delivered in additive slices so we can improve operator-facing clarity without
changing the established trust/authority model.

### Fixed constraints

- Platform Service remains the activation authority.
- Participant status remains telemetry/observability, not trust.
- Protected runtime behavior continues to follow the active authoritative posture only.
- Advisory feasibility must not become activation proof.

## 3. Status legend

- `[ ]` not started
- `[~]` in progress
- `[x]` completed
- `[-]` deferred / intentionally not part of current slice

## 4. Workstreams

## SSP-01 — Simplified client-side posture read model

**Goal:** expose a safer operator-facing posture view that separates requested posture from active /
effective posture and does not imply that requested secure posture is already active.

### Tasks

- [x] Add explicit requested-vs-active helpers to `taktx-client` policy snapshots.
- [x] Add a simplified posture snapshot containing:
  - `requestedPosture`
  - `effectivePosture`
  - `requestStatus`
  - `protectedRuntimeAllowed`
  - `blockingIssues`
  - `participantSummary`
- [x] Add public observability helpers to fetch and await the simplified posture snapshot.
- [x] Add focused tests proving:
  - requested `SECURED` with no authoritative active policy still yields effective `OPEN`
  - protected runtime remains allowed under effective `OPEN`
  - participant mismatch/event inputs surface as blocking issues
  - existing public posture helpers continue to work

### Acceptance criteria

- Console can consume a public client snapshot that clearly distinguishes requested posture from
  effective posture.
- The simplified read model does not treat requested secure posture as active runtime posture.
- The simplified read model remains additive and does not break existing public APIs.

## SSP-02 — Explicit capability-support vs readiness contract

**Goal:** make support-in-principle distinct from current readiness for the active policy identity.

### Tasks

- [x] Extend the shared participant-status contract with explicit support fields, separate from
      current readiness.
- [x] Preserve coarse participant role/capability semantics while adding richer support metadata.
- [x] Update protobuf/schema mappers and validation rules.
- [x] Add shared tests for support-vs-readiness normalization and serialization.

### Acceptance criteria

- Downstream code can represent:
  - can support `SECURED`
  - can support `ANCHORED_SECURED`
  - supports trust-anchor validation
  - supports protected-runtime participation
  - is / is not currently ready for the active identity
- None of that support data becomes activation authority or trust proof.

## SSP-03 — Stable blocker vocabulary and advisory feasibility helpers

**Goal:** support operator-facing blockers and advisory target-mode feasibility without string parsing.

### Tasks

- [ ] Standardize shared blocker/event codes for failed authoritative mutation and readiness blockers.
- [ ] Add client-side advisory `targetModeFeasibility` helpers for `OPEN`, `SECURED`, and
      `ANCHORED_SECURED`.
- [ ] Distinguish writer unavailability/unconfiguration from generic mutation rejection.
- [ ] Add tests for the canonical failure scenario:
  1. effective posture `OPEN`
  2. requested `OPEN -> SECURED`
  3. authoritative mutation fails
  4. effective posture remains `OPEN`
  5. protected runtime remains allowed
  6. blocker reason is explicit and machine-readable

### Acceptance criteria

- Console can render blocker reasons without parsing ambiguous free-text strings.
- Feasibility remains advisory only and is clearly separate from activation authority.

## 5. Current implementation note

Implementation is starting with **SSP-01** in `taktx-client` because it provides immediate operator
value with the smallest contract risk.

### SSP-01 progress update

- 2026-05-30: initial `taktx-client` implementation added and validated with
  `:taktx-client:test --tests io.taktx.client.SecurityObservabilityClientTest`
- 2026-05-30: `SSP-02` completed by adding explicit participant `supportedModes` support metadata,
  normalizing it from coarse capabilities when omitted, and validating it with focused shared,
  client, and engine tests.

### Initial SSP-01 target files

- `taktx-client/src/main/java/io/taktx/client/ObservedPolicySnapshot.java`
- `taktx-client/src/main/java/io/taktx/client/SecurityObservabilityClient.java`
- `taktx-client/src/main/java/io/taktx/client/SecurityPostureSnapshot.java` *(if helper wiring is needed)*
- new simplified read-model classes in `taktx-client/src/main/java/io/taktx/client/`
- `taktx-client/src/test/java/io/taktx/client/SecurityObservabilityClientTest.java`

## 6. Known gap preserved during SSP-01

The current upstream control-plane surface may still lose the exact requested identity after certain
failed activation paths because rollback can restore the previous active policy as the current policy.
SSP-01 improves the read model, but fully preserving failed-request identity across rollback remains a
follow-up concern for later slices if the current public contract is still insufficient.

## 7. Done definition

This backlog item is complete only when:

- code is merged
- tests covering the stated acceptance criteria pass
- the document is updated to reflect the final status of each task



