# Namespace Security Policy — Console Implementation Plan

**Status:** Proposed — implementation planning for the Console repo  
**Date:** 2026-05-23  
**Companion docs:** `docs/ARCHITECTURE.md`, `docs/SECURITY-POLICY-ENGINE-REQUIREMENTS.md`

## 1. Purpose

This document turns the proposed Security, Verifiability & Compatibility redesign into a
trackable delivery plan for the active Console repository:
- `backend/platform-service`
- `backend/ingesters/inmemory`
- `frontend/taktx-console`

It is intentionally scoped to the Console repo.

Engine work is **not** implemented here and is tracked separately in
`docs/SECURITY-POLICY-ENGINE-REQUIREMENTS.md`.

That companion document is intended to be the direct engine+client handoff for the engine team's
repo. The Console repo should consume a release produced from that upstream work rather than
recreating those semantics locally.

## 1.1 Shared terminology for this plan

The terms below are used consistently throughout this document and its companion engine
requirements doc.

| Term | Meaning in this plan |
|---|---|
| **Canonical policy identity** | The exact identity of the desired/active namespace policy, consisting of at least `policyVersion` plus a canonical `policyHash` / digest of the effective policy content. |
| **Desired policy identity** | The requested policy identity for a namespace, consisting of `desiredPolicyVersion` + `desiredPolicyHash`. |
| **Active policy identity** | The currently authoritative policy identity for a namespace, consisting of `activePolicyVersion` + `activePolicyHash`. |
| **Desired policy** | The policy configuration the operator is requesting for a namespace. |
| **Active policy** | The namespace policy currently authoritative for protected runtime behavior. Until a requested policy becomes `ACTIVE`, the previous active policy remains authoritative. |
| **Activation authority** | The single component allowed to transition a namespace policy from `REQUESTED` / `VALIDATING` to `ACTIVE`. Participants may report readiness, but must not individually decide activation. |
| **`REQUESTED`** | A policy change has been submitted but is not yet authoritative for protected data-plane behavior. |
| **`VALIDATING`** | The system is checking whether required participants can converge on and satisfy the requested policy. Protected data-plane behavior still follows the previously active policy. |
| **`ACTIVE`** | The requested policy has passed validation and is now authoritative for protected data-plane behavior. |
| **Participant `READY`** | A participant has observed the exact active canonical policy identity and can satisfy the role-relevant requirements of that policy. |
| **Participant `NOT READY`** | A participant cannot currently satisfy the active policy, has drifted from it, or cannot prove convergence on the active canonical policy identity. |
| **Control-plane traffic** | Policy/config/key/status/security-event and similar traffic needed for convergence, observability, and recovery. This traffic must remain available during `REQUESTED` / `VALIDATING`. |
| **Protected data-plane traffic** | Policy-governed BPMN/runtime message flows whose handling depends on the active security posture. This traffic is gated by `ACTIVE` + `READY`. |
| **False compatibility** | A participant reports compatibility with a policy version but, in reality, is stale, misconfigured, or enforcing different policy content/behavior. |
| **Post-activation drift** | A participant previously converged on the active policy but later diverged from the active canonical policy identity or can no longer satisfy its required checks. |

## 1.2 Architecture decisions now fixed for implementation

The architecture team has fixed the following decisions for the first slice. Console planning and
integration should treat them as settled inputs.

1. **Namespace-local topic naming is approved**:
   - `<tenant>.<namespace>.taktx-security-policy`
   - `<tenant>.<namespace>.taktx-participant-status`
   - `<tenant>.<namespace>.taktx-security-events`
2. **These are control-plane topics**, not BPMN/runtime protected data-plane topics.
3. **Control-plane topics do not participate in normal DLQ semantics.**
4. **Platform Service is the sole activation authority** for the first slice.
5. **Participants may report readiness but may never independently transition a policy to `ACTIVE`.**
6. **`policyHash` is SHA-256 over canonical requested effective policy content only**, excluding
   activation-state wrappers and unrelated metadata.
7. **Canonicalization contract is fixed**:
   - UTF-8 encoding
   - deterministic field order
   - explicit booleans always present
   - omit null/unknown fields
   - lowercase enum serialization
   - stable nested-object ordering
   - SHA-256 digest
   - lowercase hexadecimal output
8. **All participants must use the same canonicalization algorithm implementation or a
   compatibility-certified equivalent.**
9. **Minimal first-slice telemetry vocabulary is fixed**:
   - verification level: `UNVERIFIED_STATUS`, `LOCALLY_VERIFIED_STATUS`
   - effective state: `READY`, `NOT_READY`, `MISMATCH`, `STALE`
   - activation state: `REQUESTED`, `VALIDATING`, `ACTIVE`
10. **Mismatch reasons should carry machine code, human-readable message, and optional structured
    metadata.**
11. **Authoritative mutation uses a trusted-writer + ACL baseline**, with defense in depth requiring
    unauthorized policy messages to be ignored even if broker ACLs are wrong.
12. **Migration posture is fixed**:
    - `GlobalConfigurationDTO` remains operational for legacy behavior
    - `NamespaceSecurityPolicyDTO` is introduced in parallel
    - explicit `ACTIVE` namespace policy overrides legacy configuration
    - absent `ACTIVE` namespace policy preserves current/default `COMMUNITY_OPEN` behavior
13. **Status expiration semantics are required in the first slice** via `participantInstanceId`,
    `startedAt`, `lastSeenAt`, and `statusExpiresAt`; expired status must not participate in
    activation readiness decisions.
14. **Break-glass downgrade rules are fixed conceptually now**: privileged actor, explicit reason,
    audit/security event, visible transition state, high-severity classification.
15. **Activation timeout semantics are required**: policies stuck in `VALIDATING` beyond the
    configured timeout must fail activation, emit an event, preserve the previous `ACTIVE` policy,
    and never partially activate.

## 2. Verified current state in this repo

The following points are verified from the current codebase and should be treated as starting facts,
not assumptions:

1. Namespace security is currently modeled as four independent booleans on
   `backend/platform-service/src/main/java/io/taktx/console/platform/topology/entity/Namespace.java`:
   - `signingEnabled`
   - `engineRequiresAuthorization`
   - `engineRequiresExternalTaskAuthorization`
   - `engineRequiresUserTaskAuthorization`
2. Platform Service currently pushes namespace runtime config to an ingester over HTTP via
   `NamespaceConfigPublisher.publishToNamespace()`.
3. The ingester currently accepts that payload at `POST /internal/config` and forwards it to the
   engine through `TaktXClient.publishGlobalConfig(GlobalConfigurationDTO)`.
4. The frontend Ops pages currently expose these settings as raw toggles in:
   - `frontend/taktx-console/app/ops/infrastructure/namespaces/page.tsx`
   - `frontend/taktx-console/app/ops/infrastructure/namespaces/new/page.tsx`
   - `frontend/taktx-console/app/ops/infrastructure/namespaces/[id]/edit/page.tsx`
5. `docs/ARCHITECTURE.md` still describes this area as opt-in runtime feature flags.
6. `docs/ARCHITECTURE.md` also states that Platform Service currently has no Kafka connection.

These facts matter because they constrain how the first slice can be rolled out safely.

## 3. Target outcome

The target architecture for the Console repo is:

- namespace-owned **explicit security policy**
- one **canonical policy object** per namespace
- deployment capabilities validated against that policy
- an explicit **effective security mode**
- explicit participant readiness / mismatch reporting
- explicit policy versioning
- Ops UX centered on security posture, not raw unrelated booleans

### 3.1 Official security modes

```java
enum SecurityMode {
  COMMUNITY_OPEN,
  COMMUNITY_SECURED,
  ANCHORED_SECURED,
  MISCONFIGURED_SECURITY
}
```

### 3.2 Default posture

The sensible default must remain lightweight and runnable out of the box:

- newly created namespaces default to `COMMUNITY_OPEN`
- community / unsecured operation remains supported
- no trust anchors, signing, or authorization should be required unless explicitly enabled by policy
- a bare engine with no Console/control-plane integration must still run with sensible
  `COMMUNITY_OPEN` semantics

### 3.3 Fixed delivery decisions for the first slice

These decisions are already chosen for this plan:

1. **Policy stays on the namespace** — not in a separate aggregate.
2. **Default mode is `COMMUNITY_OPEN`**.
3. **No degraded active mode in the first slice** — if required capabilities are missing, the policy
   activation is rejected rather than partially activated.
4. **Participant status is observability only** — it must never establish trust.
5. **Enforcement stays in runtime validation paths** — ingress validation, signature checks, JWT
   validation, trust-anchor validation.
6. **Canonical policy convergence is required** — all required participants for a namespace must
   converge on the same explicit policy identity before a stricter policy becomes active.
7. **Protected data-plane participation is gated** — participants may continue consuming
   control-plane messages for convergence and observability, but must not publish, consume, or
   process protected BPMN/runtime data-plane messages under a requested policy unless that policy is
   `ACTIVE` and the participant is `READY` for the exact active policy identity.
8. **Control-plane mutation is protected** — control-plane traffic must not imply that arbitrary
   Kafka writers can overwrite authoritative policy/configuration. Mutation paths must be explicitly
   authenticated/authorized, and in secured modes should be integrity-protected.
9. **Use official client APIs** — required control-plane publication/consumption capabilities should
   be added to `TaktXClient` and then used by Console/runtime code instead of custom ad-hoc Kafka
   publishers for the same semantics.
10. **Activation authority is explicit** — only one component may mark a requested policy `ACTIVE`;
    participants may report readiness but must not individually decide activation.
11. **Namespace-local control-plane topics are fixed** — policy, participant status, and security
    events use namespace-local topics and are not DLQ-managed runtime data-plane topics.

### 3.4 Canonical policy identity and convergence

For the first slice, a namespace must have exactly one authoritative policy identity at a time.

That identity should include at least:

- `desiredPolicyVersion`
- `desiredPolicyHash`
- `activePolicyVersion`
- `activePolicyHash`
- `activationState`

`policyVersion` alone is not enough to prove participants are operating against the exact same
policy content, and it is not enough to represent `REQUESTED` / `VALIDATING` cleanly.

### 3.5 Required first-slice behavior for unsatisfied policy changes

When a policy change is requested that not all required participants can satisfy:

- the requested policy enters validation
- activation is rejected
- the previous active policy remains in force
- blocking participants and mismatch reasons are surfaced explicitly
- a security/configuration event is emitted

The first slice must not rely on participant self-report as proof that compatibility is real.
Self-report is an input to observability and validation, not a trust source.

### 3.5a Activation authority

Policy activation authority must be explicit.

Only one component may transition a namespace policy from `REQUESTED` / `VALIDATING` to `ACTIVE`.
Participants may report readiness, but must not individually decide that a policy is active.

For the first Console-oriented slice, Platform Service is the policy controller and sole activation
authority.

### 3.6 Control plane vs protected data plane

The redesigned model must explicitly distinguish between:

- **control-plane traffic** — policy/config/key/status/security-event distribution used for
  convergence, readiness, and observability
- **protected data-plane traffic** — policy-governed BPMN/runtime message flows such as protected
  commands, responses, and other runtime messages whose handling depends on the active security
  posture

The approved control-plane topics are:

- `<tenant>.<namespace>.taktx-security-policy`
- `<tenant>.<namespace>.taktx-participant-status`
- `<tenant>.<namespace>.taktx-security-events`

These topics are control-plane only and do not participate in normal DLQ semantics.

For the first slice:

- control-plane traffic must remain available even while a new policy is `REQUESTED` or
  `VALIDATING`
- authoritative control-plane mutation must still be restricted to trusted writers; “available” does
  not mean “open to arbitrary producers”
- protected data-plane participation must stay governed by the previously active policy until the new
  policy becomes `ACTIVE`
- if there is no previously active secured policy, the default remains `COMMUNITY_OPEN`
- once a policy is `ACTIVE`, only `READY` participants for that exact active policy identity may
  process, acknowledge as successfully handled, commit/mark successful, or publish derived protected
  data-plane work

### 3.7 Control-plane mutation security

The architecture must not rely on unsecured arbitrary Kafka writes for authoritative policy or
configuration changes.

For the first slice, the plan should assume:

- authoritative control-plane changes are accepted only from explicitly trusted writer paths
- Kafka ACLs / broker-side authorization remain a baseline requirement for write access to
  authoritative control-plane topics
- authoritative policy consumers must ignore policy messages from unauthorized principals even if
  broker ACLs are misconfigured
- in secured modes, authoritative control-plane messages should also be integrity-protected and, once
  the engine/client contract supports it, verifiable as coming from an authorized control-plane
  publisher
- participant status remains non-authoritative even if it is widely writable/readable; it must never
  override desired policy

This is important because keeping control-plane traffic available for convergence must not be
interpreted as allowing arbitrary producers to overwrite namespace policy or trust material.

### 3.8 Required participant scope for activation

For the first slice, “required participants” should be defined sharply to avoid blocking activation
on ephemeral actors.

Required for activation:

- engine nodes assigned to the namespace
- ingesters assigned to the namespace
- control-plane participants required for policy operation

Not required for activation:

- external clients
- workers
- transient job handlers

Those non-required participants should instead be validated at use time.

## 4. Scope

### In scope

- Replace the raw namespace security booleans with an explicit namespace policy model.
- Add effective-mode derivation and mismatch vocabulary to Platform Service DTOs.
- Add policy validation and versioning on namespace create/update flows.
- Introduce a Console-side publication abstraction for namespace security policy.
- Update ingester-side config handling to consume explicit policy data instead of isolated flags.
- Add Console-facing posture endpoints / DTOs for readiness and incidents.
- Replace raw toggle-based Ops UX with mode-first posture UX.
- Add tests and rollout verification for every step.
- Produce the engine-team requirements doc needed to complete end-to-end delivery.
- Consume a released engine/client version implementing the companion handoff doc before merging
  Console-side integration that depends on those semantics.

### Out of scope

- Implementing engine behavior in this repo.
- Assuming a direct Platform Service Kafka writer exists before it is verified.
- Building degraded or partially active policy states in the first slice.
- Making participant status authoritative for trust decisions.
- Introducing service mesh, sidecars, or mandatory mTLS everywhere.

## 5. Delivery principles

- **Do not assume**: if an interface, topic name, engine DTO, or transport path is not verified, it
  must be explicitly confirmed before code is merged.
- **Default safe and simple**: `COMMUNITY_OPEN` remains the default behavior.
- **No implicit security state**: desired policy, effective mode, readiness, and incidents must each
  be represented explicitly.
- **No partial activation in the first slice**: invalid or incompatible policy changes are rejected.
- **Single canonical policy per namespace**: all required participants must converge on the same
  explicit policy identity (`policyVersion` + canonical policy digest).
- **Desired and active policy identities are distinct during transitions**: the requested identity
  must be represented explicitly before activation.
- **Status is diagnostic, not authoritative**: reported compatibility reduces uncertainty but does
  not replace runtime enforcement.
- **Control plane stays live; protected data plane is gated**: participants may continue receiving
  convergence/observability traffic while protected runtime traffic remains blocked until `ACTIVE` +
  `READY`.
- **Authoritative control-plane mutation is protected**: availability of control-plane traffic must
  not create an unauthenticated/unauthorized settings-manipulation path.
- **Prefer official client support over ad-hoc publishers**: when control-plane manipulation is
  needed, extend and use `TaktXClient` instead of rolling custom publication logic with duplicate
  semantics.
- **Required participants are sharply scoped**: transient workers/clients do not block activation;
  they are validated at use time.
- **Backwards-aware rollout**: migration from the current booleans must be deterministic.
- **Test every epic**: no step is done without concrete verification evidence.

## 6. Delivery milestones

- **M0** — contract confirmation and migration rules
- **M1** — namespace policy domain model and persistence
- **M2** — Platform Service API, validation, and publication seams
- **M3** — ingester-side policy handling and posture reporting
- **M4** — Ops UI posture redesign
- **M5** — compatibility, migration, and verification hardening
- **M6** — engine coordination and end-to-end release gate

---

## EPIC SP-00 — Contracts, verified seams, and rollout guardrails

**Goal:** remove ambiguity before code changes begin so the first implementation slice does not bake
in assumptions that later have to be unwound.

### Tasks
- [ ] **SP-00.1** Confirm the authoritative wire contract for namespace security policy, participant
      status, and security events.
- [ ] **SP-00.2** Confirm whether the first Console slice will:
  - publish policy through the current Platform Service -> ingester bridge, or
  - add a new direct admin-service publication path.
- [ ] **SP-00.3** Record the exact migration mapping from the four current booleans to an initial
      `SecurityMode` + policy payload.
- [ ] **SP-00.4** Confirm the shared naming contract for control-plane topics and version fields with
      the engine team before merging policy-publication code.
- [ ] **SP-00.5** Decide whether the Platform Service response model will expose both:
  - desired policy
  - observed posture
  as separate objects.
- [ ] **SP-00.6** Define the canonical policy identity format used for convergence checks, including:
  - `policyVersion`
  - canonical `policyHash` / digest
- [ ] **SP-00.7** Define which participants are required for activation of each secured mode and how
      the namespace-level readiness decision is aggregated.
- [ ] **SP-00.8** Define, per participant role, which message classes/topics are:
  - control-plane
  - protected data-plane
  so gating rules are explicit and testable.
- [ ] **SP-00.9** Define the required security properties for authoritative control-plane mutation,
      including baseline broker authorization and any additional integrity/authentication
      requirements for secured modes.
- [ ] **SP-00.10** Confirm which control-plane operations must be supported by `TaktXClient` so the
      Console/runtime implementation does not need custom Kafka publishers for policy semantics.
- [ ] **SP-00.11** Confirm the first-slice activation authority and record that choice explicitly.
- [ ] **SP-00.12** Define participant incarnation / TTL fields for posture reporting, including at
      least `participantInstanceId`, `startedAt`, `lastSeenAt`, and `statusExpiresAt`.
- [ ] **SP-00.13** Resolve control-plane topic naming as a hard implementation blocker.

### Notes
- Current verified constraint: Platform Service currently has no Kafka connection.
- Therefore, a direct “Ops/admin service writes topic” path must be proven, not presumed.
- The first slice may keep the current HTTP bridge if that is the only verified transport.
- Topic naming is a hard blocker for implementation; recommended pattern is namespace-local:
  - `<tenant>.<namespace>.taktx-security-policy`
  - `<tenant>.<namespace>.taktx-participant-status`
  - `<tenant>.<namespace>.taktx-security-events`

### Testing / verification
- Architecture review notes attached to the delivery item.
- One written contract example for each payload:
  - security policy
  - participant status
  - security event
- One written example showing the same `policyVersion` with different payload content and how the
  canonical policy digest prevents ambiguity.
- One migration table covering all current boolean combinations that can exist in production.
- One written matrix showing, by participant role, which message flows remain available during
  `REQUESTED` / `VALIDATING` and which are gated until `ACTIVE` + `READY`.
- One written control-plane security model showing who is allowed to mutate authoritative policy,
  how that authority is enforced, and how arbitrary Kafka writers are excluded.
- One written desired-vs-active identity example showing how the same namespace moves through
  `REQUESTED` / `VALIDATING` / `ACTIVE`.

### Acceptance criteria
- No unresolved contract ambiguity remains around policy shape, versioning, or transport.
- The team can point to a written and reviewed mapping from current namespace flags to initial
  namespace policy.
- The chosen publication path is explicitly verified against the current repo constraints.
- The team has explicitly defined how canonical policy convergence is established and how activation
  is blocked when required participants do not converge.
- The team has explicitly defined control-plane vs protected data-plane behavior for each relevant
  participant role.
- The team has explicitly defined how authoritative control-plane mutation is secured.
- The team has identified the required `TaktXClient` extensions needed for control-plane support.
- The activation authority, topic naming, and participant TTL/incarnation semantics are explicit.

---

## EPIC SP-01 — Namespace policy domain model and persistence

**Goal:** make namespace security policy explicit in the Platform Service data model while keeping
`COMMUNITY_OPEN` as the default.

### Tasks
- [ ] **SP-01.1** Introduce `SecurityMode` in `backend/platform-service`.
- [ ] **SP-01.2** Add an explicit namespace-owned security policy model to `Namespace`.
- [ ] **SP-01.3** Add `policyVersion` to the namespace-owned policy state.
- [ ] **SP-01.3a** Add a canonical `policyHash` / digest derived from the explicit policy content.
- [ ] **SP-01.3b** Add distinct desired-vs-active identity fields and activation state.
- [ ] **SP-01.4** Represent required capabilities explicitly, including at least:
  - required signing dimensions
  - required authorization dimensions
  - trust-anchor requirement
- [ ] **SP-01.5** Add server-side derivation logic for:
  - desired mode label
  - effective mode label
  - `MISCONFIGURED_SECURITY` when policy cannot be satisfied
- [ ] **SP-01.6** Add a deterministic migration from existing booleans to the new policy fields.
- [ ] **SP-01.7** Ensure newly created namespaces default to `COMMUNITY_OPEN` with no hidden
      requirements enabled.

### Implementation notes
- Keep the policy on `Namespace`.
- Prefer explicit persisted fields or an embeddable mapped to explicit columns in the first slice.
  Do not assume JSON/JSONB storage unless it is explicitly verified and chosen.
- `effectiveSecurityMode` should be derived from desired policy plus observed compatibility, not used
  as an independent operator-controlled input.
- The model should distinguish between:
  - desired canonical policy
  - active canonical policy identity
  - observed participant posture against that policy
- The model should also carry participant status TTL/incarnation information in whatever posture
  projection is exposed.

### Testing / verification
- Unit tests for security-mode derivation.
- Unit tests for canonical policy digest stability.
- Unit tests for migration from every supported current boolean combination.
- Persistence tests proving a namespace can be created, loaded, updated, and migrated with the new
  policy fields.

### Acceptance criteria
- A namespace can store an explicit security policy with `COMMUNITY_OPEN` as the default.
- Existing namespace rows can be mapped deterministically into the new model.
- `MISCONFIGURED_SECURITY` is derived consistently from policy + capability mismatch, not hand-set.
- The namespace model can distinguish policy version from exact policy content identity.

---

## EPIC SP-02 — Platform Service API, validation, and activation rules

**Goal:** expose and validate the new namespace security policy through stable Platform Service APIs.

### Tasks
- [ ] **SP-02.1** Replace raw create/update request booleans with a policy-first request model.
- [ ] **SP-02.2** Add response DTOs that explicitly separate:
  - desired policy
  - effective security mode
  - readiness summary
  - mismatch reasons
  - current policy version
- [ ] **SP-02.2a** Include canonical policy identity in the response model, including `policyHash`.
- [ ] **SP-02.2b** Include desired-vs-active identity fields and `activationState` in the response
      model.
- [ ] **SP-02.3** Add validation rules for each security mode.
- [ ] **SP-02.4** Reject invalid partial states instead of persisting them.
- [x] **SP-02.5** Implement synchronous first-slice activation lifecycle:
  - `REQUESTED`
  - `VALIDATING`
  - `ACTIVE` or rejection
- [x] **SP-02.5a** Require all role-relevant participants for the namespace to converge on the same
      canonical policy identity before a stricter policy becomes `ACTIVE`.
- [x] **SP-02.5b** Keep the previously active policy in force when convergence/compatibility checks
      fail.
- [x] **SP-02.5c** Keep protected data-plane behavior governed by the previously active policy until
      the new policy becomes `ACTIVE`.
- [x] **SP-02.5d** If there is no previous secured policy, continue operating under
      `COMMUNITY_OPEN` semantics until a stricter policy successfully activates.
- [x] **SP-02.5e** Define timeout / rollback behavior for failed or stalled activation.
- [ ] **SP-02.5f** Define break-glass downgrade rules for `ANCHORED_SECURED -> COMMUNITY_SECURED`
      or `COMMUNITY_OPEN`, including privileged role, reason, and audit/security event.
- [ ] **SP-02.6** Increment `policyVersion` only for accepted policy changes.
- [ ] **SP-02.7** Preserve a compatibility window for existing UI callers only if proven necessary;
      otherwise migrate the frontend in the same change.
- [ ] **SP-02.8** Ensure license validation still applies where required and is reported in policy
      validation errors rather than surfacing as ambiguous toggle failures.

### Notes
- The first slice should reject incompatible changes instead of activating degraded or excluded
  participant states.
- This keeps edge cases down and makes rollout behavior easier to reason about.
- Reported participant compatibility must not be treated as sufficient proof on its own; runtime
  enforcement remains authoritative after activation.
- Participants should not begin protected data-plane behavior for a requested policy merely because
  they have observed it; `ACTIVE` + `READY` is required.
- Activation authority should be singular and explicit; participants report readiness but do not mark
  policy `ACTIVE` themselves.

### Testing / verification
- Resource tests for create/update validation.
- Tests covering mode transitions:
  - `COMMUNITY_OPEN -> COMMUNITY_SECURED`
  - `COMMUNITY_SECURED -> ANCHORED_SECURED`
  - `ANCHORED_SECURED -> COMMUNITY_SECURED`
  - invalid partial policy rejected
- Tests proving activation is rejected when any required participant reports a different canonical
  policy identity.
- Tests proving the previous active policy remains in force after rejected activation.
- Tests proving protected data-plane behavior remains governed by the previous active policy while a
  new stricter policy is only `REQUESTED` / `VALIDATING`.
- Tests proving activation timeout / rollback behavior is deterministic and preserves previous active
  policy.
- Tests proving rejected changes do not increment `policyVersion`.

### Acceptance criteria
- Namespace APIs are policy-first and no longer depend on operators understanding unrelated booleans.
- Invalid policy combinations are rejected before persistence.
- A successful policy update produces a stable versioned result that the UI can display directly.
- A stricter policy does not become active unless all required participants converge on the same
  canonical policy identity.
- Protected data-plane behavior does not switch early; it changes only when the new policy is
  actually `ACTIVE` and the participant is `READY`.
- Desired-vs-active policy identity is explicit in the API and transition model.
- Break-glass downgrade behavior is concrete, privileged, reasoned, and audited.

---

## EPIC SP-03 — Policy publication seam and control-plane bridge

**Goal:** publish explicit namespace security policy from the Console repo without assuming an
unverified transport.

### Tasks
- [ ] **SP-03.1** Introduce a `SecurityPolicyPublisher` abstraction in `backend/platform-service`.
- [ ] **SP-03.2** Implement the verified first-slice transport selected in SP-00.
- [ ] **SP-03.3** Update the current `NamespaceConfigPublisher` path so it can publish policy-shaped
      payloads rather than isolated security flags.
- [ ] **SP-03.3a** Ensure the published policy payload includes canonical policy identity material so
      participants can prove they observed the same policy content, not only the same version.
- [ ] **SP-03.3b** Prefer new official `TaktXClient` control-plane methods for policy publication
      once available; avoid rolling a second custom Kafka publication path with the same semantics.
- [ ] **SP-03.4** Preserve backward compatibility where needed for currently supported engines until
      the engine team delivers the new requirements.
- [ ] **SP-03.5** Ensure policy publication is retried or recoverable after ingester reconnect using
      the same operational reliability standard as the current key/license/config push flows.
- [ ] **SP-03.6** Prevent raw participant status from being treated as authoritative when policy is
      published or read back.
- [ ] **SP-03.7** Ensure authoritative control-plane publication uses only trusted writer paths and
      does not treat arbitrary Kafka message injection as a valid configuration mechanism.

### Notes
- The architecture target says the policy topic is authoritative desired state.
- The current repo does not yet prove that Platform Service can write Kafka directly.
- The implementation must therefore keep the authority decision at the Platform Service/API layer
  while using only a transport path that is actually verified.
- If a new direct topic-manipulation path is introduced later, it should arrive via supported
  `TaktXClient` APIs and explicit writer authorization rules, not by duplicating bespoke Kafka logic
  in the Console repo.

### Testing / verification
- Publication tests for the selected transport path.
- Reconnect / retry tests mirroring current namespace config push behavior.
- Contract tests ensuring the published payload contains policy version and explicit requirements.
- Contract tests ensuring the published payload contains canonical policy identity material.
- Tests proving only trusted/authorized writer paths can perform authoritative control-plane
  mutation.

### Acceptance criteria
- Console-side code can publish explicit policy payloads without relying on unverified infrastructure.
- The publication path is deterministic, testable, and compatible with the current runtime topology.
- Policy publication payloads contain explicit security requirements, not implicit booleans only.
- Participants have enough contract data to detect `same version, different content` mismatches.
- The design does not create an unauthenticated/unauthorized control-plane overwrite path.
- Where runtime client support is required, `TaktXClient` is the chosen abstraction rather than a
  bespoke duplicate publisher.

---

## EPIC SP-04 — Ingester-side policy handling and posture reporting

**Goal:** replace local implicit flag handling with explicit policy evaluation and posture reporting in
`backend/ingesters/inmemory`.

### Tasks
- [ ] **SP-04.1** Replace the current ingester-side flag cache with a policy-aware in-memory holder.
- [ ] **SP-04.2** Make the ingester compute explicit local capability satisfaction against namespace
      policy.
- [ ] **SP-04.3** Add explicit readiness state and mismatch-reason generation for the ingester
      participant.
- [ ] **SP-04.3a** Make readiness/posture reporting include the observed canonical policy identity.
- [ ] **SP-04.3b** Add participant incarnation / TTL fields to posture reporting.
- [ ] **SP-04.4** Add a clear separation between:
  - local enforcement inputs
  - posture/status reporting outputs
- [ ] **SP-04.4a** Explicitly gate any protected data-plane participation owned by the ingester so it
      follows the active policy only when the ingester is `READY` for that exact policy identity.
- [ ] **SP-04.5** Emit or expose security incidents for config rejection, policy mismatch, and
      readiness degradation.
- [ ] **SP-04.5a** Treat post-activation drift as a security posture incident: if a participant later
      diverges from the active canonical policy or fails applicable checks, it becomes `NOT READY`.
- [ ] **SP-04.6** Keep DLQ boundaries unchanged: posture/config mismatches are not DLQ events.
- [ ] **SP-04.7** Preserve current lightweight defaults so an unsecured community setup still starts
      and runs without additional configuration.

### Notes
- Full participant coverage depends on the engine team.
- The Console repo slice should still implement posture reporting for the participants it owns or can
  observe directly.
- Status output must never become a trust source.

### Testing / verification
- Unit tests for policy-to-readiness evaluation.
- Tests proving missing required capabilities produce explicit mismatch reasons.
- Tests proving `same policyVersion, different policyHash` is detected as a mismatch.
- Tests proving status/reporting failures do not alter trust decisions.
- Tests proving stale participant status expires and does not continue to count as current posture.
- Tests proving `COMMUNITY_OPEN` still works with minimal configuration.
- Tests proving control-plane handling remains available during convergence while protected
  data-plane participation is gated.

### Acceptance criteria
- Ingester-side code evaluates explicit policy, not unrelated booleans.
- Readiness and mismatch reasons are explicit and inspectable.
- Community-mode defaults continue to work without mandatory security setup.
- Post-activation divergence from the active policy identity is surfaced explicitly and does not rely
  on status as proof of trust.
- Protected data-plane behavior is gated on `ACTIVE` + `READY`, while control-plane convergence
  remains possible.

---

## EPIC SP-05 — Ops UI redesign around security posture

**Goal:** make security posture understandable to operators without requiring them to reason about raw
feature flags.

### Tasks
- [ ] **SP-05.1** Replace raw security toggles in namespace create/edit flows with a primary mode
      selector:
  - `Community`
  - `Community secured`
  - `Anchored secured`
- [ ] **SP-05.2** Show contextual advanced requirements only when needed.
- [ ] **SP-05.3** Display effective security mode separately from desired policy.
- [ ] **SP-05.4** Display participant readiness summary and mismatch reasons.
- [ ] **SP-05.5** Display current `policyVersion`.
- [ ] **SP-05.5a** Display canonical policy identity detail sufficient to diagnose policy drift, at
      least in advanced/operator views.
- [ ] **SP-05.5b** Display desired-vs-active policy identity and activation state.
- [ ] **SP-05.6** Add a security incidents view or panel for namespace posture problems.
- [ ] **SP-05.7** Make `Community` the visible default for new namespaces.
- [ ] **SP-05.8** Remove or de-emphasize raw unrelated booleans from the namespace list page.

### Notes
- The UI should describe the posture in operator language.
- It should not expose low-level booleans without context as the primary control surface.

### Testing / verification
- Frontend unit tests for mode-label mapping and derived posture rendering.
- Component tests for create/edit forms.
- End-to-end checks covering:
  - default new namespace shows `Community`
  - mode change updates contextual requirements
  - mismatch reasons are shown when backend reports them
  - policy drift / false-compatibility is shown when participants report a different canonical
    policy identity or later fail applicable checks

### Acceptance criteria
- Operators can understand the namespace security posture from the page without interpreting several
  unrelated booleans.
- New namespaces clearly default to `Community`.
- Readiness and mismatch information is visible in the UI when present.
- Operators can distinguish requested policy, active policy, and observed participant drift.

---

## EPIC SP-06 — Compatibility, migration, and verification hardening

**Goal:** prove the redesign is safe to roll out and does not silently break existing community-mode
or pre-policy namespaces.

### Tasks
- [ ] **SP-06.1** Add migration tests for existing namespaces created under the old boolean model.
- [ ] **SP-06.2** Add compatibility tests proving a namespace with the default policy still behaves as
      the current lightweight unsecured default.
- [ ] **SP-06.3** Add negative tests proving invalid policy combinations fail closed.
- [ ] **SP-06.4** Add tests proving status messages and incidents do not influence trust decisions.
- [ ] **SP-06.4a** Add tests proving false participant compatibility claims do not weaken runtime
      enforcement.
- [ ] **SP-06.4b** Add tests proving post-activation drift yields `NOT READY` posture and incident
      reporting.
- [ ] **SP-06.4c** Add tests proving requested-but-not-active secured policy does not prematurely
      change protected data-plane behavior.
- [ ] **SP-06.4d** Add tests proving stale participant status / expired heartbeat does not count as
      current readiness.
- [ ] **SP-06.4e** Add tests proving break-glass downgrade requires privileged role, reason, and
      audit event.
- [ ] **SP-06.5** Add rollback notes and release notes for operators.
- [ ] **SP-06.6** Document known gaps that remain blocked on engine delivery.

### Testing / verification
- Backend integration tests.
- Ingester integration tests.
- Frontend form and rendering tests.
- Manual verification checklist for namespace create/update, posture display, and default community
  behavior.

### Acceptance criteria
- Rollout preserves a working default `COMMUNITY_OPEN` path.
- Existing namespaces are migrated or interpreted deterministically.
- Negative cases fail clearly and are observable.
- False-compatibility and post-activation drift scenarios are explicitly covered by tests and do not
  create silent trust gaps.
- Protected data-plane gating semantics are explicit and verified during transition states.

---

## EPIC SP-07 — Engine coordination and merge gate

**Goal:** avoid merging a Console-side slice that looks complete in isolation but is not yet valid
end-to-end.

### Tasks
- [ ] **SP-07.1** Hand off `docs/SECURITY-POLICY-ENGINE-REQUIREMENTS.md` to the engine team.
- [ ] **SP-07.2** Confirm the exact shared DTO/topic/version contract with the engine team.
- [ ] **SP-07.3** Verify which parts can merge independently and which must remain on a dedicated
      branch until engine support exists.
- [ ] **SP-07.4** Add an explicit release gate requiring matched engine capability verification before
      production rollout of secured modes.
- [ ] **SP-07.5** Upgrade the Console repo only against a published engine/client release that
      satisfies the companion handoff doc.

### Acceptance criteria
- Console and engine teams agree on the shared contract.
- Merge and rollout gates are explicit.
- No secured-mode rollout is treated as production-ready without end-to-end verification.
- Console-side dependent integration targets a published engine/client release rather than
  unpublished bespoke patches.

---

## 7. Migration guidance from current flags

The migration must be deterministic and conservative.

### Initial mapping rule

Use the current namespace booleans only to derive the initial desired policy for migrated namespaces.

Suggested first-slice mapping:

| Current namespace flags | Initial desired mode | Notes |
|---|---|---|
| all current security booleans `false` | `COMMUNITY_OPEN` | default, unsecured, lightweight |
| signing or authorization booleans enabled, but no trust-anchor requirement represented | `COMMUNITY_SECURED` | preserve secured intent without inventing anchoring |
| any future verified trust-anchor requirement exists | `ANCHORED_SECURED` | only when anchored data is explicitly present |

### Migration rule constraints

- Do not invent trust anchors during migration.
- Do not silently map an existing namespace to `MISCONFIGURED_SECURITY` during migration without a
  concrete mismatch signal.
- `MISCONFIGURED_SECURITY` should be derived from actual incompatibility after migration, not used as
  a default migration target.
- When the new policy model is introduced, canonical `policyHash` values must be derived
  deterministically from the migrated policy content.

## 8. Test strategy summary

Every epic above must carry its own tests. In addition, the overall slice should satisfy this
minimum verification matrix:

### Backend / Platform Service
- policy validation tests
- policy version increment tests
- canonical policy digest tests
- migration tests from current namespace rows
- publication contract tests

### Ingester
- explicit policy evaluation tests
- readiness / mismatch tests
- policy drift / false-compatibility tests
- control-plane vs protected data-plane gating tests
- default community-mode startup tests
- incident emission / exposure tests

### Frontend
- mode selector tests
- default community-mode UX tests
- posture rendering tests
- mismatch / incident display tests

### End-to-end / rollout verification
- create namespace in default `Community` mode
- verify a bare engine with no Console/control-plane integration still behaves sensibly in
  `COMMUNITY_OPEN`
- migrate an old namespace with all flags off -> `COMMUNITY_OPEN`
- reject invalid policy combination
- verify status is visible but not treated as trust
- verify a missing capability yields mismatch reason and non-ready posture
- verify a stricter policy is not activated when required participants do not converge on the same
  canonical policy identity
- verify post-activation drift results in `NOT READY` posture and incident reporting
- verify control-plane convergence remains possible while protected data-plane behavior stays on the
  previous active policy until the new policy is `ACTIVE`
- verify authoritative control-plane mutation cannot be performed by arbitrary/untrusted Kafka
  writers
- verify desired-vs-active policy identities are visible during `REQUESTED` / `VALIDATING`
- verify stale participant status expires and no longer counts toward readiness

## 9. Definition of done

This plan is complete when:

- namespace security is represented as explicit policy rather than implicit booleans
- `COMMUNITY_OPEN` is the default for new namespaces and remains easy to run
- invalid or incompatible secured policies are rejected rather than partially activated
- Ops UI shows mode, policy version, readiness, mismatch reasons, and incidents
- the Console repo has tests proving the above behavior
- engine-team requirements are agreed and tracked separately
- canonical policy convergence and false-compatibility scenarios are explicitly handled and tested
- control-plane vs protected data-plane gating is explicitly defined and verified
- bare-engine `COMMUNITY_OPEN` defaults remain intact
- authoritative control-plane mutation is explicitly protected
- required control-plane functionality is supported through `TaktXClient` rather than duplicated
  bespoke publishers

## 9.1 Future extension deliberately outside the first slice

### SP-VH-01 — Verifiable process instance update chain

If end-to-end verifiability remains a strategic direction, track a separate future epic for
verifiable process-instance update chaining / hash chaining.

This should stay out of the first namespace-security-policy implementation slice, but it should be
named explicitly now so it remains visible as a future extension.












