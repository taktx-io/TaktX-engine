# Security Simplification Implementation Plan

**Status:** Draft / Ready for execution  
**Date:** 2026-06-01  
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
- [ ] Phase 1: Collapse shared contracts to `OPEN` / `ANCHORED`
- [ ] Phase 2: Simplify engine policy handling and enforcement
- [ ] Phase 3: Add file-backed identity persistence and client auto-signing
- [ ] Phase 4: Align trust-registry semantics on `taktx-signing-keys`
- [ ] Phase 5: Rewrite tests around the simplified model
- [ ] Phase 6: Remove legacy bridges and update docs

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
- [ ] Remove `SECURED`
- [ ] Replace/remove `ANCHORED_SECURED` with `ANCHORED`
- [ ] Remove `MISCONFIGURED_SECURITY` if possible; prefer status/events over fake modes

**Acceptance criteria**
- `SecurityMode` expresses only the final runtime model
- No production code depends on `SECURED` or `ANCHORED_SECURED`

---

#### A2 — Simplify `NamespaceSecurityPolicyDTO`
**Files**
- `taktx-shared/src/main/java/io/taktx/dto/NamespaceSecurityPolicyDTO.java`

**Work**
- [ ] Remove `requiredSigning`
- [ ] Remove `requiredAuthorization`
- [ ] Remove `activationState`
- [ ] Remove desired-vs-active policy lifecycle fields if not needed
- [ ] Keep only the authoritative policy identity, e.g.:
  - [ ] `mode`
  - [ ] optional `policyVersion`
  - [ ] optional `policyHash`
- [ ] Remove break-glass metadata unless explicitly retained as an operational policy feature

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
- [ ] Remove normalization of `requiredSigning`
- [ ] Remove normalization of `requiredAuthorization`
- [ ] Remove activation-state validation rules
- [ ] Recompute canonical hash from the reduced authoritative policy shape only
- [ ] Validate only the remaining contract fields

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
- [ ] Replace `SecurityModeMessage` with `OPEN` / `ANCHORED`
- [ ] Remove `RequiredSigningMessage`
- [ ] Remove `RequiredAuthorizationMessage`
- [ ] Remove `SecurityActivationStateMessage`
- [ ] Simplify `NamespaceSecurityPolicyMessage` to the hard-break contract
- [ ] Regenerate protobuf artifacts and fix mapping tests

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
- [ ] Remove assumptions about `SECURED` / `ANCHORED_SECURED`
- [ ] Supported modes become `OPEN` plus optional `ANCHORED`
- [ ] Keep readiness/mismatch reasons as observability only

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
- [ ] Remove `REQUESTED` / `VALIDATING` / `ACTIVE`
- [ ] Remove convergence and rollback logic
- [ ] Make the latest valid policy immediately authoritative
- [ ] Keep rejection only for malformed/unauthorized policy mutation

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
- [ ] Support `OPEN`
- [ ] Support `ANCHORED` only when enforcement prerequisites are met
- [ ] Remove readiness checks based on policy activation identity mismatch
- [ ] Keep fail-closed readiness behavior for `ANCHORED`

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
- [ ] Remove policy dependence on `requiredSigning.*`
- [ ] Remove policy dependence on `requiredAuthorization.*`
- [ ] Remove JWT as a posture mechanism
- [ ] Keep JWT only as optional business/user context
- [ ] Implement uniform rule:
  - [ ] `OPEN`: accept unsigned ingress
  - [ ] `ANCHORED`: require verified trusted signature
- [ ] Ensure unknown / revoked / unanchored signers are rejected cleanly

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
- [ ] Split security rejection from processing failure handling
- [ ] Do not emit DLQ entries for:
  - [ ] missing signature in `ANCHORED`
  - [ ] invalid signature
  - [ ] unknown key
  - [ ] revoked key
  - [ ] unanchored key
- [ ] Emit security events instead

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
- [ ] Verify coverage for:
  - [ ] process start
  - [ ] message correlation
  - [ ] signal publication
  - [ ] external task completion
  - [ ] user task completion
  - [ ] future external runtime commands
- [ ] Remove special-case posture handling where possible

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
- new local persistence class(es)

**Work**
- [ ] Add a source that:
  - [ ] loads an existing identity if present
  - [ ] generates a new keypair if missing
  - [ ] persists key material locally
  - [ ] persists stable key id locally
  - [ ] reuses the same identity across restart
- [ ] Decide file layout and configuration properties
- [ ] Keep current mounted-file source behavior for externally managed identities

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
- [ ] Prefer the local file-backed identity source when no explicit source is configured
- [ ] Keep env/file overrides for managed deployments
- [ ] Reject ephemeral in-memory identity for `ANCHORED`

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

- [ ] A1
- [ ] A2
- [ ] A3
- [ ] A4
- [ ] A5

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
| A1 | Replace `SecurityMode` with `OPEN` / `ANCHORED` | Todo |  | Hard break |
| A2 | Simplify `NamespaceSecurityPolicyDTO` | Todo | A1 | Remove granular flags |
| A3 | Rewrite `NamespaceSecurityPolicySupport` | Todo | A2 | Canonical hash simplification |
| A4 | Simplify proto + mapper | Todo | A2,A3 | Regenerate protobufs |
| A5 | Simplify participant supported modes | Todo | A1 | Remove posture ladder |
| C1 | Remove activation workflow | Todo | A2,A4 | Immediate authority |
| C2 | Simplify engine readiness | Todo | C1 | Fail-closed in anchored |
| C3 | Rewrite ingress enforcement | Todo | C1,C2 | `OPEN => accept`, `ANCHORED => verify` |
| C4 | Prevent security rejection DLQ | Todo | C3 | Emit events instead |
| C5 | Apply same rule to all ingress | Todo | C3 | Uniform external ingress |
| D1 | Add managed local file-backed identity source | Todo |  | Persistent identity |
| D2 | Make persistent identity default | Todo | D1 | Stable restarts |
| D3 | Simplify client policy interpretation | Todo | A2,C1,D2 | Mode-only client logic |
| D4 | Auto-sign all anchored client traffic | Todo | D3 | No caller choice |
| D5 | Detect and surface rotation | Todo | D1,D2 | Explicit churn detection |
| E1 | Define trust-registry semantics in `SigningKeyDTO` | Todo |  | Reuse `taktx-signing-keys` |
| E2 | Align trust policy with `ANCHORED` | Todo | A1,C3 | Anchored = countersigned |
| E3 | Define explicit rotation lifecycle | Todo | D5,E1 | Revocation path |
| F1 | Update shared tests | Todo | Phase 1 | Hard-break contract tests |
| F2 | Update client tests | Todo | Phase 3 | Auto-sign + persistence |
| F3 | Update engine tests | Todo | Phase 2 | No lifecycle tests |
| F4 | Rewrite integration security tests | Todo | Phases 2-4 | Prompt coverage |
| G1 | Remove legacy global-config bridge | Todo | Phases 2-3 | One authoritative model |
| G2 | Update docs | Todo | final behavior | Simplified story |
| G3 | Add migration notes | Todo | final behavior | Breaking change guidance |

---

# Low-token prompt pattern for future implementation slices

Use prompts like this for future sessions:

> Implement tasks A1–A4 from `docs/security-simplification-implementation-plan.md`. Use the fixed context pack unless compile or test failures require expansion. After edits, run only targeted module tests for changed code and summarize remaining breakages.

This keeps implementation bounded and avoids re-deriving the architecture every time.

