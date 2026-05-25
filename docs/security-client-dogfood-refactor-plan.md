# Client Dogfood Enablement — Implementation Tracker

**Status:** In progress — tracker updated with recorded DOG-01/DOG-02 completion and DOG-03 enforcer-activation progress  
**Date:** 2026-05-25  
**Companion docs:** `docs/SECURITY-POLICY-ENGINE-REQUIREMENTS.md`, `docs/SECURITY-POLICY-IMPLEMENTATION-PLAN.md`, `docs/ARCHITECTURE.md`

## 1. Goal

Make the public `TaktXClient` fully capable of driving and observing namespace security-policy behavior
end to end, so the planned focused integration suite can validate the system using only official client
APIs.

This remains a breaking redesign. Backward compatibility with the legacy participant-role model is not a
goal for this branch.

## Progress snapshot — 2026-05-25

Recorded progress currently implemented and verified on the branch:

- shared participant status now uses `ParticipantKind` + `ParticipantCapability` + optional
  `componentType`
- legacy active shared-contract role relevance has been replaced by capability relevance
- engine activation now converges on `ENFORCER` participants instead of product-role labels
- engine and client participant status publication now emit the simplified capability model
- the following verification has been run successfully after these changes:
  - `:taktx-shared:test`
  - `:taktx-engine:test`
  - `:taktx-client:test`
  - combined run of `:taktx-shared:test :taktx-engine:test :taktx-client:test`

## 2. Agreed semantic cleanup

The legacy `ParticipantRole` model is no longer treated as a security permission model.

The current role values have mostly been used as a mixed signal for:

- participant-status publication
- activation/readiness aggregation
- observability and reporting

They must **not** remain the thing that decides whether a component may:

- start processes
- complete work
- publish authoritative policy

Those decisions must instead come from:

- namespace policy
- JWT / signature validation
- authoritative identity
- local client-side gating

### Fixed decisions

1. `worker` is not a special security role.
2. `ingester` is not a special security role.
3. A single component may need to present multiple security responsibilities at once.
4. Product topology labels move out of the core protocol contract.
5. Authorization is credential/policy-based, not label-based.
6. Activation must be based on capability semantics, not product names.

## 3. Target participant model

### 3.1 `ParticipantKind`

Broad identity class for diagnostics and UI:

- `ENGINE`
- `CLIENT`

### 3.2 `ParticipantCapability`

Security and observability responsibilities; multiple values are allowed on a single participant:

- `ENFORCER`
- `AUTHORITATIVE_POLICY_PUBLISHER`
- `PROTECTED_RUNTIME_PARTICIPANT`
- `SECURITY_OBSERVER`

### 3.3 `componentType`

Optional free-form component label for human meaning without product-specific protocol roles, for
example:

- `engine`
- `console`
- `ingester`
- `worker-service`
- `admin-tool`
- `generic-client`

### 3.4 Descriptor shape

The shared participant identity should converge on a descriptor equivalent to:

```java
public record SecurityParticipantDescriptor(
    String participantId,
    ParticipantKind kind,
    Set<ParticipantCapability> capabilities,
    String componentType
) {}
```

### 3.5 Examples

#### Console

- `kind = CLIENT`
- `capabilities = [AUTHORITATIVE_POLICY_PUBLISHER, SECURITY_OBSERVER]`
- `componentType = "console"`

#### Ingester

- `kind = CLIENT`
- `capabilities = [PROTECTED_RUNTIME_PARTICIPANT]`
- `componentType = "ingester"`

#### Worker service

- `kind = CLIENT`
- `capabilities = [PROTECTED_RUNTIME_PARTICIPANT]`
- `componentType = "worker-service"`

#### Engine node

- `kind = ENGINE`
- `capabilities = [ENFORCER, SECURITY_OBSERVER]`
- `componentType = "engine"`

#### Mixed app

- `kind = CLIENT`
- `capabilities = [AUTHORITATIVE_POLICY_PUBLISHER, SECURITY_OBSERVER, PROTECTED_RUNTIME_PARTICIPANT]`
- `componentType = "admin-console"`

## 4. Activation model

### Rule

Only participants with `ENFORCER` are activation-blocking for engine-side policy activation.

### Consequences

- engine nodes must converge and become `READY`
- clients still publish status for observability
- protected clients self-gate locally before protected runtime participation
- `SECURITY_OBSERVER` does not block activation
- `AUTHORITATIVE_POLICY_PUBLISHER` does not block activation merely by existing

### Semantic split

- **Engine responsibility:** “Can I safely enforce this namespace policy?”
- **Client responsibility:** “Can I safely participate in protected runtime operations under this
  policy?”

## 5. Public client target shape

`TaktXClient` remains the root entry point, but should move toward a builder plus focused facets.

### Builder direction

The builder should accept the participant descriptor directly and allow multiple capabilities per client
instance.

### Facets

- `security()`
- `observability()`
- `runtime()`
- `workers()`
- `dlq()`

Expected responsibilities:

- `security()`
  - publish namespace policy
  - clear namespace policy
- `observability()`
  - observe effective policy
  - observe participant status
  - observe security events
  - expose posture snapshot helpers
- `runtime()`
  - start process
  - complete external task
  - complete user task
- `workers()`
  - register worker listeners/subscriptions
- `dlq()`
  - observe and replay DLQ entries as supported

## 6. Definition of done

This initiative is done when all of the following are true:

- [x] the shared contract no longer encodes product-specific participant roles
- [ ] a single client instance can advertise multiple capabilities
- [ ] authorization logic is no longer derived from product-role labels
- [x] engine activation depends only on `ENFORCER` participants
- [ ] the public client can publish policy and observe policy/status/events
- [ ] the public client surface is organized around facets instead of one growing flat facade
- [ ] framework wrappers configure the new participant descriptor cleanly
- [ ] unit tests cover shared, engine, client, and wrapper behavior under the new model
- [ ] a focused public-client-only integration suite is implemented and stable

## 7. Trackable workstreams

## DOG-01 — Shared contract refactor

**Goal:** replace the legacy single-role participant model with `ParticipantKind` +
`ParticipantCapability` + optional `componentType`.

**Primary files:**

- `taktx-shared/src/main/java/io/taktx/dto/ParticipantStatusDTO.java`
- `taktx-shared/src/main/java/io/taktx/dto/ParticipantKind.java`
- `taktx-shared/src/main/java/io/taktx/dto/ParticipantCapability.java`
- `taktx-shared/src/main/java/io/taktx/security/ParticipantStatusSupport.java`
- `taktx-shared/src/main/java/io/taktx/serdes/ParticipantStatusProtoMapper.java`
- `taktx-shared/src/main/proto/io/taktx/proto/security_observability.proto`

**Checklist:**

- [x] remove `ParticipantRole` from active shared-contract usage
- [x] define `ParticipantKind`
- [x] define `ParticipantCapability`
- [ ] introduce the shared participant descriptor shape
- [x] update `ParticipantStatusDTO` to carry kind, capabilities, and `componentType`
- [x] update protobuf schema and mapper logic
- [x] update validation/normalization for empty or invalid capability sets
- [x] remove remaining production references to legacy product-role names in shared code

**Unit-test gate:**

- [x] round-trip proto serialization tests
- [x] DTO mapper tests
- [x] validation tests for missing kind / invalid capability sets / blank optional component labels
- [ ] tests proving no shared callers still depend on `ENGINE | INGESTER | CONSOLE | CLIENT`

**Complete when:**

- [x] `taktx-shared:test` passes with the new participant model only

## DOG-02 — Capability-based policy relevance

**Goal:** make policy relevance capability-driven instead of role-driven.

**Primary files:**

- `taktx-shared/src/main/java/io/taktx/security/NamespaceSecurityPolicyCapabilityRelevance.java`
- `taktx-shared/src/main/java/io/taktx/security/CapabilityRelevantPolicyElement.java`

**Checklist:**

- [x] remove role-relevance types from active design
- [x] map relevant policy behavior from capabilities
- [x] ensure worker-like behavior is represented through `PROTECTED_RUNTIME_PARTICIPANT`
- [x] support mixed-capability clients without requiring multiple identities

**Unit-test gate:**

- [ ] capability relevance tests for publisher-only clients
- [ ] capability relevance tests for observer-only clients
- [x] capability relevance tests for protected runtime clients
- [x] mixed-capability tests

**Complete when:**

- [x] shared relevance rules are expressed in capability terms only

## DOG-03 — Engine activation and participant-status refactor

**Goal:** base activation and posture accounting on `ENFORCER` rather than product labels.

**Primary files:**

- `taktx-engine/src/main/java/io/taktx/engine/security/NamespaceSecurityPolicyActivationService.java`
- `taktx-engine/src/main/java/io/taktx/engine/config/ParticipantStatusStore.java`
- `taktx-engine/src/main/java/io/taktx/engine/config/ParticipantStatusProcessor.java`
- `taktx-engine/src/main/java/io/taktx/engine/security/EngineSecurityReadinessEvaluator.java`
- `taktx-engine/src/main/java/io/taktx/engine/security/ParticipantStatusPublisher.java`

**Checklist:**

- [x] remove hard-coded activation dependence on `ENGINE`, `INGESTER`, and `CONSOLE`
- [x] make activation depend on `ENFORCER` participants only
- [x] preserve observability of non-enforcer participants
- [x] update engine participant-status publication to emit the new descriptor
- [x] update grouping and mismatch reasoning to avoid product-role assumptions

**Unit-test gate:**

- [x] all enforcers ready => active
- [x] enforcer missing or not ready => not active
- [x] observer-only clients do not block activation
- [x] authoritative publishers do not block activation by existing
- [ ] protected runtime clients do not block activation unless they are also enforcers

**Complete when:**

- [x] `taktx-engine:test` passes for activation/posture logic under the new semantics

## DOG-04 — Client participant descriptor and builder support

**Goal:** allow a single `TaktXClient` instance to advertise multiple responsibilities explicitly.

**Primary files:**

- `taktx-client/src/main/java/io/taktx/client/TaktXClientBuilder.java`
- `taktx-client/src/main/java/io/taktx/client/TaktXClient.java`
- related client participant-profile / descriptor classes

**Checklist:**

- [ ] add participant descriptor input to the builder
- [ ] allow multiple capabilities for one client instance
- [ ] validate inconsistent participant configuration
- [ ] remove singular-role assumptions from client construction
- [ ] adapt protected runtime guards to the new descriptor model

**Unit-test gate:**

- [ ] builder validation tests
- [ ] multi-capability participant tests
- [ ] mixed publisher + observer + runtime participant tests
- [ ] invalid descriptor tests

**Complete when:**

- [ ] `taktx-client:test` passes for builder/descriptor coverage

## DOG-05 — Public observability APIs

**Goal:** expose public APIs for effective policy, participant status, and security events.

**Primary files:**

- `taktx-client/src/main/java/io/taktx/client/SecurityObservabilityClient.java`
- `taktx-client/src/main/java/io/taktx/client/ObservedPolicySnapshot.java`
- `taktx-client/src/main/java/io/taktx/client/NamespaceSecurityPolicyConsumer.java`
- `taktx-client/src/main/java/io/taktx/client/ParticipantStatusConsumer.java`
- `taktx-client/src/main/java/io/taktx/client/SecurityEventConsumer.java`

**Checklist:**

- [ ] add public API to observe effective namespace security policy
- [ ] add public API to observe participant statuses
- [ ] add public API to observe security events
- [ ] add polling/snapshot helpers suitable for integration tests
- [ ] ensure all posture helpers are built on public topics/streams only

**Unit-test gate:**

- [ ] policy observation consumer tests
- [ ] participant-status consumer tests
- [ ] security-event consumer tests
- [ ] empty-stream/default posture tests
- [ ] snapshot assembly tests

**Complete when:**

- [ ] the public client can observe every posture stream needed by the focused dogfood suite

## DOG-06 — Facet the public client surface

**Goal:** keep the public contract extensible without growing one flat top-level API forever.

**Primary files:**

- `taktx-client/src/main/java/io/taktx/client/TaktXClient.java`
- new facet interfaces/classes under `taktx-client/src/main/java/io/taktx/client/`

**Checklist:**

- [ ] introduce `security()`
- [ ] introduce `observability()`
- [ ] introduce `runtime()`
- [ ] introduce `workers()`
- [ ] introduce `dlq()`
- [ ] keep lifecycle on the root client
- [ ] preserve public naming consistency for dogfood tests and wrappers

**Unit-test gate:**

- [ ] facet contract tests
- [ ] tests proving mixed-capability participants can safely use multiple facets
- [ ] tests proving policy mutation and observation are exposed through supported public APIs

**Complete when:**

- [ ] the root client is stable and the behavior surface is facet-oriented

## DOG-07 — Console-grade posture helpers

**Goal:** provide public helpers for the same posture view needed by console-grade observability.

**Primary files:**

- posture snapshot / helper models in `taktx-client`

**Checklist:**

- [ ] define a public posture snapshot including effective mode, policy version, policy hash,
      participant statuses, mismatch reasons, and recent security events
- [ ] add polling/snapshot helpers usable by integration tests without internal hooks
- [ ] ensure mismatch visibility does not masquerade as DLQ behavior

**Unit-test gate:**

- [ ] posture snapshot assembly tests
- [ ] mismatch visibility tests
- [ ] empty/default posture tests

**Complete when:**

- [ ] console-grade posture assertions are possible through public client APIs alone

## DOG-08 — Framework wrapper updates

**Goal:** make wrapper modules configure the new client contract naturally.

**Primary files:**

- `taktx-client-quarkus/src/main/java/io/taktx/client/quarkus/TaktXClientProvider.java`
- `taktx-client-spring-boot-3/src/main/java/io/taktx/client/spring/TaktXClientAutoConfiguration.java`
- `taktx-client-spring-boot-4/src/main/java/io/taktx/client/spring/TaktXClientAutoConfiguration.java`

**Checklist:**

- [ ] update wrappers to use the builder with participant descriptor input
- [ ] assign sane default client descriptors
- [ ] ensure worker wiring maps to `PROTECTED_RUNTIME_PARTICIPANT`, not a worker role
- [ ] remove wrapper dependency on legacy root-client leakage methods

**Unit-test gate:**

- [ ] Quarkus provider tests
- [ ] Spring Boot 3 auto-configuration tests
- [ ] Spring Boot 4 auto-configuration tests

**Complete when:**

- [ ] wrappers build and test against the new public client contract only

## DOG-09 — Focused public-client-only integration suite

**Goal:** validate namespace security behavior end to end using the official client contract only.

**Rules:**

- use real Kafka
- use a real engine process/container
- use public `TaktXClient` APIs only
- use no internal hooks
- use polling/timeouts instead of fixed sleeps
- capture diagnostics on failure

**Checklist:**

- [ ] create shared fixtures for Kafka, engine bootstrap, namespace generation, client creation, JWT
      / signing setup, policy helpers, observability helpers, runtime helpers, and worker helpers
- [ ] add must-have scenarios for:
  - [ ] default behavior in open mode
  - [ ] policy publication and reflection
  - [ ] command enforcement for invalid vs valid auth/signing
  - [ ] worker behavior through the public client
  - [ ] multi-engine consistency
  - [ ] unauthorized/random client behavior
  - [ ] console-grade observability
  - [ ] namespace isolation

**Acceptance gate:**

- [ ] the suite proves the public client can both drive and observe namespace-security behavior end to
      end
- [ ] the suite is small and stable enough to run regularly

## 8. Recommended implementation order

1. shared contract refactor
2. engine activation/status refactor
3. client builder + participant descriptor support
4. public observability APIs
5. client faceting cleanup
6. wrapper updates
7. focused integration suite

## 9. Suggested commit structure

- [ ] Commit 1: shared participant model replacement (`DOG-01` + tests)
- [ ] Commit 2: capability-based relevance (`DOG-02` + tests)
- [ ] Commit 3: engine activation/status refactor (`DOG-03` + tests)
- [ ] Commit 4: builder + participant descriptor support (`DOG-04` + tests)
- [ ] Commit 5: observability + posture helpers (`DOG-05` + `DOG-07` + tests)
- [ ] Commit 6: facet split (`DOG-06` + tests)
- [ ] Commit 7: wrapper updates (`DOG-08` + tests)
- [ ] Commit 8: focused integration suite (`DOG-09`)

## 10. Immediate next actions

- [x] use this document as the authoritative tracker for the redesign branch
- [x] finish `DOG-01` and `DOG-02` against the simplified participant model
- [x] verify engine activation semantics against enforcer-only readiness rules
- [ ] add public observability APIs before starting the integration suite
- [ ] keep unit tests green as the gating signal for each workstream




