# Security Simplification Implementation Plan

**Status:** In progress  
**Date:** 2026-06-02  
**Related docs:**
- `docs/drastic-security-simplification.md`
- `docs/drastic-security-simplification-engine.md`

---

# Checklist

## Decisions locked
- [x] Hard break from legacy posture model
- [x] Reuse `taktx-signing-keys` as the first trust-registry implementation
- [x] Add a local file-backed identity source for stable participant identity

## Execution strategy
- [x] Phase 1: Collapse shared contracts to `OPEN` / `ANCHORED`
- [ ] Phase 2: Simplify engine policy handling and enforcement
- [ ] Phase 3: Add file-backed identity persistence and client auto-signing
- [ ] Phase 4: Align trust-registry semantics on `taktx-signing-keys`
- [ ] Phase 5: Rewrite tests around the simplified model
- [ ] Phase 6: Remove legacy bridges and update docs

## Progress update — 2026-06-02
- Shared-contract hard break (A1–A5) is complete and remains the stable baseline.
- `taktx-engine` main/test source sets compile cleanly and `:taktx-engine:test` is green against the simplified contract.
- Public-client security integration tests have been rewritten away from legacy activation-state / `SECURED` assumptions and now target `OPEN` / `ANCHORED` semantics.
- Dead engine activation-monitor plumbing has been removed from the policy/status path, including the obsolete policy activation timeout configuration and participant-status reevaluation hook.
- The engine namespace-policy store now carries a single authoritative policy view; remaining Phase 2 work is focused on readiness/enforcement cleanup rather than activation rollback state.
- Engine readiness now reports concrete anchored enforceability prerequisites (stable signing source, trust anchor, published signing identity, registration signature) instead of lifecycle-style aggregate mismatch state.
- Process-instance authorization no longer treats authoritative `ANCHORED` mode as a JWT requirement; policy-driven posture now requires trusted signatures while JWT remains tied to legacy runtime gates / optional context.
- Engine process-instance security policy metadata has been flattened further: legacy JWT/authorization-shape flags were removed from `MessageSecurityPolicy`, and trigger-type helpers now drive the remaining legacy auth toggles in `EngineAuthorizationService`.
- Presented process-instance JWTs are now validated as optional context even when the legacy task-completion/entry auth gate is off, and replay protection follows presented entry JWTs instead of the legacy entry-auth toggle.
- Process-instance authorization no longer enforces the legacy runtime JWT gates at all: unsigned `OPEN` ingress is accepted, `ANCHORED`/signing-enabled ingress requires trusted signatures, and presented JWTs remain validated as optional context only.
- External `topic-meta-requested` ingress now also flows through a signed ingress envelope so mode/signature enforcement, raw-payload verification state, and authorization-stage DLQ classification match the newer message-event and signal paths.
- External message correlation and signal publication ingress now flow through signed ingress envelopes so `ANCHORED`/signing-enabled mode requires verified trusted signatures for externally published records while engine-internal subscription mutations remain allowed.
- `usertasks-response` now preserves the original process-instance trigger envelope and headers when handing user task completion into `process-instance`, so replay protection and process-instance authorization evaluate the original external ingress context instead of an engine-rewrapped DTO.
- Final `C5` audit confirmed that the current topology's live external runtime ingress set is now covered (`process-instance`, `usertasks-response`, `topic-meta-requested`, externally published `message-event`, externally published `signals`). Remaining special cases are intentional non-`C5` exceptions: engine-internal `schedule-commands`, engine-internal non-entry `process-instance` continuations, and control-plane/operator topics such as authoritative namespace policy mutation and `dlq.replay`.
- Process-instance ingress security rejections now short-circuit to `security-events` instead of creating DLQ entries; DLQ remains reserved for payload/engine-processing failures while readiness gating still fails closed.
- `taktx-shared` now has a managed `LocalPersistentSigningIdentitySource` that persists a single `identity.properties` file under `~/.taktx/signing/` by default (or `taktx.signing.local.directory` / `TAKTX_SIGNING_LOCAL_DIRECTORY`), reuses the same Ed25519 keypair across restart, and fails closed on corrupt persisted state instead of silently churning identity; `FileSigningIdentitySource` remains the externally managed mounted-file reader.
- `taktx-client` now defaults to that managed local persistent identity source when no explicit signing source is configured, still prefers explicitly configured `env`/`file` identities for managed deployments, supports explicit `local` selection, and treats restart-stable signing identity as an anchored-mode participation prerequisite so explicitly generated in-memory identities are no longer considered sufficient for `ANCHORED` runtime traffic.
- The remaining work is concentrated in finishing Phase 2 production cleanup, Phase 3/4 identity + trust-registry behavior, and broadening Phase 5 coverage beyond the current engine/integration adaptations.

---

# Goals

Implement the simplified security architecture with these runtime semantics:

```text
OPEN => accept
ANCHORED => verify
```

No `SECURED` mode.  
No posture negotiation.  
No capability exchange for policy activation.  
No per-message-type signing or authorization switches.

---

# Repo-efficient execution model

To save context and avoid repeated rescans, implementation should work from a **fixed context pack** and only expand when compile/test failures require it.

## Context pack: keep pinned during implementation

### Shared contract pack
- `taktx-shared/src/main/java/io/taktx/dto/SecurityMode.java`
- `taktx-shared/src/main/java/io/taktx/dto/NamespaceSecurityPolicyDTO.java`
- `taktx-shared/src/main/java/io/taktx/dto/ParticipantStatusDTO.java`
- `taktx-shared/src/main/java/io/taktx/dto/SigningKeyDTO.java`
- `taktx-shared/src/main/java/io/taktx/security/NamespaceSecurityPolicySupport.java`
- `taktx-shared/src/main/java/io/taktx/security/ParticipantStatusSupport.java`
- `taktx-shared/src/main/java/io/taktx/security/NamespaceSecurityPolicyControlPlaneContract.java`
- `taktx-shared/src/main/java/io/taktx/security/NamespaceSecurityPolicyCapabilityRelevance.java`
- `taktx-shared/src/main/java/io/taktx/serdes/NamespaceSecurityPolicyProtoMapper.java`
- `taktx-shared/src/main/proto/io/taktx/proto/security_policy.proto`

### Engine enforcement pack
- `taktx-engine/src/main/java/io/taktx/engine/security/EngineAuthorizationService.java`
- `taktx-engine/src/main/java/io/taktx/engine/security/VerificationCore.java`
- `taktx-engine/src/main/java/io/taktx/engine/security/KeyTrustPolicyProducer.java`
- `taktx-engine/src/main/java/io/taktx/engine/security/EngineSecurityReadinessEvaluator.java`
- `taktx-engine/src/main/java/io/taktx/engine/security/MessageSigningService.java`
- `taktx-engine/src/main/java/io/taktx/engine/security/ProtectedDataPlaneParticipationGuard.java`
- `taktx-engine/src/main/java/io/taktx/engine/security/NamespaceSecurityPolicyActivationService.java`
- `taktx-engine/src/main/java/io/taktx/engine/config/NamespaceSecurityPolicyStore.java`
- `taktx-engine/src/main/java/io/taktx/engine/config/NamespaceSecurityPolicyProcessor.java`
- `taktx-engine/src/main/java/io/taktx/engine/config/ParticipantStatusStore.java`
- `taktx-engine/src/main/java/io/taktx/engine/pi/ProcessInstanceTriggerEnvelopeDeserializer.java`
- `taktx-engine/src/main/java/io/taktx/engine/pi/ProcessInstanceProcessor.java`

### Client pack
- `taktx-client/src/main/java/io/taktx/client/TaktXClient.java`
- `taktx-client/src/main/java/io/taktx/client/SecurityClient.java`
- `taktx-client/src/main/java/io/taktx/client/ClientProtectedDataPlaneParticipationGuard.java`
- `taktx-client/src/main/java/io/taktx/client/SimplifiedSecurityPostureSnapshot.java`
- `taktx-client/src/main/java/io/taktx/client/SecurityPostureSnapshot.java`
- `taktx-client/src/main/java/io/taktx/client/ObservedPolicySnapshot.java`

### Identity source pack
- `taktx-shared/src/main/java/io/taktx/security/SigningIdentitySource.java`
- `taktx-shared/src/main/java/io/taktx/security/FileSigningIdentitySource.java`
- `taktx-shared/src/main/java/io/taktx/security/EnvironmentWorkerSigningIdentitySource.java`
- `taktx-shared/src/main/java/io/taktx/security/GeneratedSigningIdentitySource.java`

### Test pack
- `taktx-shared/src/test/java/io/taktx/security/NamespaceSecurityPolicySupportTest.java`
- `taktx-shared/src/test/java/io/taktx/serdes/NamespaceSecurityPolicyProtoMapperTest.java`
- `taktx-client/src/test/java/io/taktx/client/TaktXClientNamespaceSecurityPolicyTest.java`
- `taktx-engine/src/test/java/io/taktx/engine/security/EngineSecurityReadinessEvaluatorTest.java`
- `taktx-engine/src/test/java/io/taktx/engine/security/NamespaceSecurityPolicyActivationServiceTest.java`
- `taktx-engine/src/test/java/io/taktx/engine/config/NamespaceSecurityPolicyProcessorTest.java`
- `taktx-engine/src/securityIntegrationTest/java/io/taktx/engine/pi/integration/PublicClientSecuredModeDogfoodIntegrationTest.java`
- `taktx-engine/src/securityIntegrationTest/java/io/taktx/engine/pi/integration/PublicClientObservabilityDogfoodIntegrationTest.java`
- `taktx-engine/src/securityIntegrationTest/java/io/taktx/engine/pi/integration/PublicClientOpenModeDogfoodIntegrationTest.java`

## Anti-rescan rule

Only expand beyond the context pack when one of these is true:
- compile errors point outside the pack
- a directly referenced symbol resolves outside the pack
- a failing test requires inspection outside the pack

---

# Phase plan

## Phase 1 — Collapse shared contracts

### Objective
Make the simplified model impossible to bypass in code by removing old modes and policy shape from the shared type system.

### Tasks

#### A1 — Replace `SecurityMode` with final modes
**Files**
- `taktx-shared/src/main/java/io/taktx/dto/SecurityMode.java`

**Work**
- [x] Remove `SECURED`
- [x] Replace/remove `ANCHORED_SECURED` with `ANCHORED`
- [x] Remove `MISCONFIGURED_SECURITY` if possible; prefer status/events over fake modes

**Acceptance criteria**
- `SecurityMode` expresses only the final runtime model
- No production code depends on `SECURED` or `ANCHORED_SECURED`

---

#### A2 — Simplify `NamespaceSecurityPolicyDTO`
**Files**
- `taktx-shared/src/main/java/io/taktx/dto/NamespaceSecurityPolicyDTO.java`

**Work**
- [x] Remove `requiredSigning`
- [x] Remove `requiredAuthorization`
- [x] Remove `activationState`
- [x] Remove desired-vs-active policy lifecycle fields if not needed
- [x] Keep only the authoritative policy identity, e.g.:
  - [x] `mode`
  - [x] optional `policyVersion`
  - [x] optional `policyHash`
- [x] Remove break-glass metadata unless explicitly retained as an operational policy feature

**Acceptance criteria**
- The DTO describes a single authoritative namespace mode
- No lifecycle, negotiation, or granular per-command requirements remain

**Depends on**
- A1

---

#### A3 — Rewrite `NamespaceSecurityPolicySupport`
**Files**
- `taktx-shared/src/main/java/io/taktx/security/NamespaceSecurityPolicySupport.java`

**Work**
- [x] Remove normalization of `requiredSigning`
- [x] Remove normalization of `requiredAuthorization`
- [x] Remove activation-state validation rules
- [x] Recompute canonical hash from the reduced authoritative policy shape only
- [x] Validate only the remaining contract fields

**Acceptance criteria**
- Validation is mode-centric only
- Canonical policy hashing is simple and stable

**Depends on**
- A2

---

#### A4 — Simplify protobuf and mapper
**Files**
- `taktx-shared/src/main/proto/io/taktx/proto/security_policy.proto`
- `taktx-shared/src/main/java/io/taktx/serdes/NamespaceSecurityPolicyProtoMapper.java`

**Work**
- [x] Replace `SecurityModeMessage` with `OPEN` / `ANCHORED`
- [x] Remove `RequiredSigningMessage`
- [x] Remove `RequiredAuthorizationMessage`
- [x] Remove `SecurityActivationStateMessage`
- [x] Simplify `NamespaceSecurityPolicyMessage` to the hard-break contract
- [x] Regenerate protobuf artifacts and fix mapping tests

**Acceptance criteria**
- Serialized policy records match the simplified architecture
- No old posture fields survive on the wire

**Depends on**
- A2
- A3

---

#### A5 — Simplify participant-mode support model
**Files**
- `taktx-shared/src/main/java/io/taktx/security/ParticipantStatusSupport.java`
- `taktx-shared/src/main/java/io/taktx/dto/ParticipantStatusDTO.java`

**Work**
- [x] Remove assumptions about `SECURED` / `ANCHORED_SECURED`
- [x] Supported modes become `OPEN` plus optional `ANCHORED`
- [x] Keep readiness/mismatch reasons as observability only

**Acceptance criteria**
- Participant status no longer encodes the old posture ladder

**Depends on**
- A1

---

## Phase 2 — Simplify engine policy handling and enforcement

### Objective
Make namespace mode immediately authoritative and enforce a single ingress rule.

### Tasks

#### C1 — Remove activation workflow
**Files**
- `taktx-engine/src/main/java/io/taktx/engine/security/NamespaceSecurityPolicyActivationService.java`
- `taktx-engine/src/main/java/io/taktx/engine/config/NamespaceSecurityPolicyStore.java`
- `taktx-engine/src/main/java/io/taktx/engine/config/NamespaceSecurityPolicyProcessor.java`

**Work**
- [x] Remove `REQUESTED` / `VALIDATING` / `ACTIVE`
- [x] Remove convergence and rollback logic
- [x] Make the latest valid policy immediately authoritative
- [x] Keep rejection only for malformed/unauthorized policy mutation

**Acceptance criteria**
- The engine stores exactly one authoritative policy
- No activation lifecycle remains

**Depends on**
- A2
- A4

---

#### C2 — Simplify engine readiness
**Files**
- `taktx-engine/src/main/java/io/taktx/engine/security/EngineSecurityReadinessEvaluator.java`
- `taktx-engine/src/main/java/io/taktx/engine/security/ProtectedDataPlaneParticipationGuard.java`

**Work**
- [x] Support `OPEN`
- [x] Support `ANCHORED` only when enforcement prerequisites are met
- [x] Remove readiness checks based on policy activation identity mismatch
- [x] Keep fail-closed readiness behavior for `ANCHORED`

**Acceptance criteria**
- Readiness reflects enforceability, not negotiation

**Depends on**
- C1

---

#### C3 — Rewrite ingress enforcement rule
**Files**
- `taktx-engine/src/main/java/io/taktx/engine/security/EngineAuthorizationService.java`
- `taktx-engine/src/main/java/io/taktx/engine/security/VerificationCore.java`
- `taktx-engine/src/main/java/io/taktx/engine/pi/ProcessInstanceTriggerEnvelopeDeserializer.java`

**Work**
- [x] Remove policy dependence on `requiredSigning.*`
- [x] Remove policy dependence on `requiredAuthorization.*`
- [x] Remove JWT as a posture mechanism
- [x] Keep JWT only as optional business/user context
- [x] Implement uniform rule:
  - [x] `OPEN`: process-instance ingress accepts unsigned records when no legacy runtime gate is active
  - [x] `ANCHORED`: process-instance ingress requires a verified trusted signature
- [x] Ensure unknown / revoked / unanchored signers are rejected cleanly

**Remaining for completion**
- [x] Validate presented JWTs as optional process-instance context even when legacy auth gates are off
- [x] Remove the remaining legacy runtime JWT gates so namespace mode becomes the only process-instance ingress posture driver
- [x] Apply the same mode-only rule consistently beyond process-instance ingress

**Acceptance criteria**
- Enforcement is driven solely by namespace mode
- No per-message-type signing/authorization matrix remains

**Depends on**
- C1
- C2

---

#### C4 — Prevent security rejections from reaching DLQ
**Files**
- `taktx-engine/src/main/java/io/taktx/engine/pi/ProcessInstanceProcessor.java`

**Work**
- [x] Split security rejection from processing failure handling
- [x] Do not emit DLQ entries for:
  - [x] missing signature in `ANCHORED`
  - [x] invalid signature
  - [x] unknown key
  - [x] revoked key
  - [x] unanchored key
- [x] Emit security events instead

**Acceptance criteria**
- Security rejection does not create DLQ entries
- Payload/engine-processing failures still use DLQ appropriately

**Depends on**
- C3

---

#### C5 — Apply the same rule to all external ingress
**Files**
- `EngineAuthorizationService` plus any topic-specific security entry points

**Work**
- [x] Verify coverage for the current topology's external runtime ingress set:
  - [x] process start
  - [x] topic metadata request
  - [x] message correlation
  - [x] signal publication
  - [x] external task completion
  - [x] user task completion
- [x] Remove special-case posture handling where possible

**Intentional non-C5 exceptions**
- `schedule-commands` remains engine-internal (`ENGINE`-signed only) and is deferred to later internal-only hardening work
- engine-internal non-entry `process-instance` continuations (`ContinueFlowElementTriggerDTO`, `StartFlowElementTriggerDTO`, `EventSignalTriggerDTO`) remain trusted internal traffic
- control-plane / operator topics (`taktx-security-policy`, `taktx-configuration`, `taktx-signing-keys`, `dlq.replay`, etc.) are outside external runtime ingress scope

**Acceptance criteria**
- All external runtime ingress follows the same enforcement model

**Depends on**
- C3

---

## Phase 3 — Add file-backed identity persistence and client auto-signing

### Objective
Make stable participant identity the default and ensure the client signs automatically in `ANCHORED` mode.

### Tasks

#### D1 — Add managed local file-backed identity source
**Files**
- `taktx-shared/src/main/java/io/taktx/security/SigningIdentitySource.java`
- `taktx-shared/src/main/java/io/taktx/security/FileSigningIdentitySource.java`
- `taktx-shared/src/main/java/io/taktx/security/LocalPersistentSigningIdentitySource.java`

**Work**
- [x] Add a source that:
  - [x] loads an existing identity if present
  - [x] generates a new keypair if missing
  - [x] persists key material locally
  - [x] persists stable key id locally
  - [x] reuses the same identity across restart
- [x] Decide file layout and configuration properties
  - Managed local identity is stored as a single `identity.properties` file inside a local directory
  - Default directory: `~/.taktx/signing/`
  - Override directory: `taktx.signing.local.directory` / `TAKTX_SIGNING_LOCAL_DIRECTORY`
  - Override key-id prefix: `taktx.signing.local.key-id-prefix` / `TAKTX_SIGNING_LOCAL_KEY_ID_PREFIX`
- [x] Keep current mounted-file source behavior for externally managed identities

**Acceptance criteria**
- Local identity survives restart
- No accidental identity churn on normal restart

**Depends on**
- none

---

#### D2 — Make persistent local identity the client default
**Files**
- `taktx-client/src/main/java/io/taktx/client/TaktXClient.java`
- client builder/configuration path

**Work**
- [x] Prefer the local file-backed identity source when no explicit source is configured
- [x] Keep env/file overrides for managed deployments
- [x] Reject ephemeral in-memory identity for `ANCHORED`

**Acceptance criteria**
- Default client behavior produces restart-stable identity
- `ANCHORED` does not rely on ephemeral identity

**Depends on**
- D1

---

#### D3 — Simplify client policy interpretation
**Files**
- `taktx-client/src/main/java/io/taktx/client/TaktXClient.java`
- `taktx-client/src/main/java/io/taktx/client/ClientProtectedDataPlaneParticipationGuard.java`

**Work**
- [ ] Remove checks based on `requiredSigning` / `requiredAuthorization`
- [ ] Replace protected-posture logic with `ANCHORED` mode logic
- [ ] In `OPEN`, allow unsigned operation
- [ ] In `ANCHORED`, fail fast when no usable identity exists

**Acceptance criteria**
- Client mode handling is simple and authoritative

**Depends on**
- A2
- C1
- D2

---

#### D4 — Auto-sign all client-originated runtime ingress in `ANCHORED`
**Files**
- `taktx-client/src/main/java/io/taktx/client/TaktXClient.java`
- relevant producers/responders

**Work**
- [ ] Start commands sign automatically
- [ ] Message events sign automatically
- [ ] Signals sign automatically
- [ ] Worker completions sign automatically
- [ ] Public key publication to `taktx-signing-keys` occurs automatically when needed

**Acceptance criteria**
- Callers do not choose signing per command type
- `ANCHORED` is automatic at the client edge

**Depends on**
- D3

---

#### D5 — Detect and surface identity rotation
**Files**
- client signing/publishing and observability paths

**Work**
- [ ] Detect key changes versus restart reuse
- [ ] Republish trust-registry entry on rotation
- [ ] Emit observability signal for unexpected identity churn

**Acceptance criteria**
- Identity rotation is explicit and testable

**Depends on**
- D1
- D2

---

## Phase 4 — Align trust registry semantics on `taktx-signing-keys`

### Objective
Use the existing key topic as the initial approved/revoked/anchored registry without introducing a second registry surface.

### Tasks

#### E1 — Define trust-registry semantics in `SigningKeyDTO`
**Files**
- `taktx-shared/src/main/java/io/taktx/dto/SigningKeyDTO.java`

**Work**
- [ ] Clarify how approval is represented
- [ ] Clarify how revocation is represented
- [ ] Clarify how anchoring is represented
- [ ] Decide whether current statuses are enough for first implementation

**Recommended first implementation**
- `ACTIVE` = approved and accepted
- `TRUSTED` = optional overlap during rotation only
- `REVOKED` = reject immediately
- anchored identity = valid `registrationSignature`

**Acceptance criteria**
- Registry semantics are explicit and testable

**Depends on**
- none

---

#### E2 — Align trust policy selection with mode semantics
**Files**
- `taktx-shared/src/main/java/io/taktx/security/OpenKeyTrustPolicy.java`
- `taktx-shared/src/main/java/io/taktx/security/AnchoredKeyTrustPolicy.java`
- `taktx-engine/src/main/java/io/taktx/engine/security/KeyTrustPolicyProducer.java`

**Work**
- [ ] Ensure `OPEN` uses infrastructure/community trust
- [ ] Ensure `ANCHORED` requires valid anchored trust material
- [ ] Keep countersignature validation as the anchored identity proof

**Acceptance criteria**
- Trust policy behavior matches the ADR story exactly

**Depends on**
- A1
- C3

---

#### E3 — Define explicit rotation lifecycle
**Files**
- registrar/store usage sites and related docs/tests

**Work**
- [ ] Publish new key
- [ ] Allow overlap only if intentionally needed
- [ ] Revoke old key
- [ ] Ensure future messages from revoked key are rejected

**Acceptance criteria**
- Rotation behavior is explicit and not accidental

**Depends on**
- D5
- E1

---

## Phase 5 — Rewrite tests

### Objective
Make the test suite enforce the simplified model and prevent regressions.

### Tasks

#### F1 — Shared contract tests
**Files**
- `taktx-shared/src/test/java/io/taktx/security/NamespaceSecurityPolicySupportTest.java`
- `taktx-shared/src/test/java/io/taktx/serdes/NamespaceSecurityPolicyProtoMapperTest.java`

**Work**
- [ ] Remove tests for `SECURED`
- [ ] Remove tests for activation lifecycle
- [ ] Add tests for `OPEN` / `ANCHORED` only

**Acceptance criteria**
- Shared tests encode only the new contract

**Depends on**
- Phase 1

---

#### F2 — Client tests
**Files**
- `taktx-client/src/test/java/io/taktx/client/TaktXClientNamespaceSecurityPolicyTest.java`

**Work**
- [ ] `OPEN` allows unsigned operation
- [ ] `ANCHORED` fails fast without identity
- [ ] `ANCHORED` auto-signs without caller choice
- [ ] Local file-backed identity survives restart
- [ ] Identity rotation is detected

**Acceptance criteria**
- Client behavior is directly covered

**Depends on**
- Phase 3

---

#### F3 — Engine unit tests
**Files**
- `taktx-engine/src/test/java/io/taktx/engine/security/EngineSecurityReadinessEvaluatorTest.java`
- `taktx-engine/src/test/java/io/taktx/engine/security/NamespaceSecurityPolicyActivationServiceTest.java`
- `taktx-engine/src/test/java/io/taktx/engine/config/NamespaceSecurityPolicyProcessorTest.java`

**Work**
- [ ] Rewrite readiness tests for `OPEN` / `ANCHORED`
- [ ] Delete or replace activation-service tests if the service is removed
- [ ] Validate immediate authoritative policy application
- [ ] Validate fail-closed anchored readiness

**Acceptance criteria**
- Engine unit tests no longer encode convergence/activation logic

**Depends on**
- Phase 2

---

#### F4 — Security integration tests
**Files**
- `taktx-engine/src/securityIntegrationTest/java/io/taktx/engine/pi/integration/PublicClientSecuredModeDogfoodIntegrationTest.java`
- `taktx-engine/src/securityIntegrationTest/java/io/taktx/engine/pi/integration/PublicClientObservabilityDogfoodIntegrationTest.java`
- related helpers

**Required coverage**
- [ ] Default namespace = `OPEN`
- [ ] `OPEN` start succeeds unsigned
- [ ] `OPEN` completion succeeds unsigned
- [ ] `ANCHORED` start fails unsigned
- [ ] `ANCHORED` completion fails unsigned
- [ ] `ANCHORED` start succeeds with approved identity
- [ ] `ANCHORED` completion succeeds with approved identity
- [ ] Unknown key rejected
- [ ] Revoked key rejected
- [ ] Invalid signature rejected
- [ ] Identity survives restart
- [ ] Identity rotation detected
- [ ] Multiple engines enforce consistently
- [ ] Security rejection does not create DLQ entries
- [ ] Namespace isolation works
- [ ] `TaktXClient` signs automatically
- [ ] `TaktXClient` fails fast without identity

**Acceptance criteria**
- The design prompt is traceably covered by integration tests

**Depends on**
- Phases 2–4

---

## Phase 6 — Remove legacy bridges and update docs

### Objective
Prevent drift back toward the old model and make migration obvious.

### Tasks

#### G1 — Remove legacy global-config bridge
**Files**
- `taktx-client/src/main/java/io/taktx/client/TaktXClient.java`

**Work**
- [ ] Remove or deprecate `legacyGlobalSecurityConfigToNamespaceSecurityPolicy`
- [ ] Stop treating legacy global security flags as posture drivers
- [ ] Decide whether old flags are deleted or retained temporarily as deprecated no-ops

**Acceptance criteria**
- Namespace policy is the only authoritative security model

**Depends on**
- Phase 2
- Phase 3

---

#### G2 — Update documentation
**Files**
- `README.md`
- `taktx-client/README.md`
- `SECURITY.md`
- relevant files under `docs/`

**Work**
- [ ] Replace `SECURED` / `ANCHORED_SECURED` language
- [ ] Remove posture negotiation language
- [ ] Document `taktx-signing-keys` as the first trust registry
- [ ] Document local file-backed identity default
- [ ] Document that security rejections do not produce DLQ entries

**Acceptance criteria**
- Docs match actual runtime behavior
- Operators can explain the model simply

**Depends on**
- final code behavior

---

#### G3 — Add breaking-change migration notes
**Files**
- `CHANGELOG.md`
- optional new migration doc

**Work**
- [ ] Describe hard break clearly
- [ ] List removed APIs/fields/modes
- [ ] Describe migration path from old posture model
- [ ] Provide `OPEN` and `ANCHORED` examples

**Acceptance criteria**
- Existing adopters understand what changed and how to migrate

**Depends on**
- final code behavior

---

# Recommended execution order

## Best first slice

Start with this bounded slice:

- [x] A1
- [x] A2
- [x] A3
- [x] A4
- [x] A5

### Why
- It collapses the type system early
- It exposes all legacy references immediately via compilation
- It avoids repeated rework later

## Then implement in this order
1. Phase 1 — shared contracts
2. Phase 2 — engine enforcement
3. Phase 3 — client identity + auto-signing
4. Phase 4 — trust-registry semantics
5. Phase 5 — tests
6. Phase 6 — docs and migration

---

# Suggested task board

| ID | Task | Status | Depends on | Notes |
|---|---|---|---|---|
| A1 | Replace `SecurityMode` with `OPEN` / `ANCHORED` | Done |  | Hard break |
| A2 | Simplify `NamespaceSecurityPolicyDTO` | Done | A1 | Remove granular flags |
| A3 | Rewrite `NamespaceSecurityPolicySupport` | Done | A2 | Canonical hash simplification |
| A4 | Simplify proto + mapper | Done | A2,A3 | Regenerate protobufs |
| A5 | Simplify participant supported modes | Done | A1 | Remove posture ladder |
| C1 | Remove activation workflow | Done | A2,A4 | Engine policy store/processor now keep only one authoritative policy; rejected mutations remain the only control-plane failure path |
| C2 | Simplify engine readiness | Done | C1 | Readiness now reflects concrete anchored enforceability prerequisites and remains fail-closed for protected runtime work |
| C3 | Rewrite ingress enforcement | In progress | C1,C2 | Process-instance ingress is now signature/mode driven with JWT retained as optional context only, and the mode-only rule now extends across the current external runtime ingress set; remaining work is final message-policy cleanup |
| C4 | Prevent security rejection DLQ | Done | C3 | Process-instance ingress now emits security events for authorization/readiness rejection while reserving DLQ for decode/processing failures |
| C5 | Apply same rule to all ingress | Done | C3 | Current external runtime ingress (`process-instance`, `usertasks-response`, `topic-meta-requested`, externally published `message-event`, externally published `signals`) now preserves/enforces the same mode/signature model; remaining special cases are intentional internal/control-plane exceptions |
| D1 | Add managed local file-backed identity source | Done |  | Managed local `identity.properties` persistence added in `taktx-shared`; mounted-file source remains externally managed |
| D2 | Make persistent identity default | Done | D1 | Client now defaults to managed local identity, keeps explicit env/file overrides, and requires restart-stable signing source for anchored participation |
| D3 | Simplify client policy interpretation | Todo | A2,C1,D2 | Mode-only client logic |
| D4 | Auto-sign all anchored client traffic | Todo | D3 | No caller choice |
| D5 | Detect and surface rotation | Todo | D1,D2 | Explicit churn detection |
| E1 | Define trust-registry semantics in `SigningKeyDTO` | Todo |  | Reuse `taktx-signing-keys` |
| E2 | Align trust policy with `ANCHORED` | Todo | A1,C3 | Anchored = countersigned |
| E3 | Define explicit rotation lifecycle | Todo | D5,E1 | Revocation path |
| F1 | Update shared tests | Todo | Phase 1 | Hard-break contract tests |
| F2 | Update client tests | Todo | Phase 3 | Auto-sign + persistence |
| F3 | Update engine tests | In progress | Phase 2 | Engine unit suites compile/pass against the simplified contract; legacy activation assertions still need final cleanup |
| F4 | Rewrite integration security tests | In progress | Phases 2-4 | Public-client dogfood tests now target `OPEN` / `ANCHORED`; remaining coverage gaps are identity rotation / approved anchored success paths |
| G1 | Remove legacy global-config bridge | Todo | Phases 2-3 | One authoritative model |
| G2 | Update docs | Todo | final behavior | Simplified story |
| G3 | Add migration notes | Todo | final behavior | Breaking change guidance |

---

# Low-token prompt pattern for future implementation slices

Use prompts like this for future sessions:

> Implement tasks A1–A4 from `docs/security-simplification-implementation-plan.md`. Use the fixed context pack unless compile or test failures require expansion. After edits, run only targeted module tests for changed code and summarize remaining breakages.

This keeps implementation bounded and avoids re-deriving the architecture every time.


