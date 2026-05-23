# Namespace Security Policy — Engine + Client Handoff Requirements

**Status:** Proposed handoff requirements/design for the engine repo  
**Date:** 2026-05-23  
**Companion docs:** `docs/SECURITY-POLICY-IMPLEMENTATION-PLAN.md`, `docs/ARCHITECTURE.md`

## 1. Purpose

This document is the direct requirements/design handoff for the engine team's repo, which owns both:

- engine runtime changes
- shared `TaktXClient` changes

It gives the engine team a concrete, reviewable target for moving TaktX security from implicit
runtime flags to an explicit namespace-level security policy model.

The intended delivery order is:

1. engine repo implements the required engine + client changes described here
2. engine team publishes a release containing those changes
3. Console repo upgrades to that release and implements its dependent integration work

This document should therefore be usable 1-to-1 as the upstream requirements/design doc for the
engine repo.

The goal is not to make the default deployment heavier.

The required default posture remains:
- lightweight
- unsecured by default
- runnable in `COMMUNITY_OPEN` mode without extra security bootstrap material

## 1.0 Context and reasons for this change

This change is being driven by an architectural/operational problem in the current model:

- too much of the security posture is currently **implicit**
- the active posture is spread across separate flags, runtime assumptions, and participant-local
  behavior
- operators can see pieces of the posture, but not a single explicit authoritative security state
- compatibility across engine/ingester/client participants is difficult to reason about ahead of time
- failures and mismatches are not modeled clearly enough as first-class policy outcomes

In practice, this creates several concrete problems:

1. **Security posture is hard to understand operationally**
   - a namespace may appear “secured” only because a few booleans are enabled
   - but it is not obvious whether all required participants actually support and enforce the same
     posture

2. **Compatibility is too implicit**
   - deployment capabilities and runtime requirements are not clearly separated
   - participants may claim compatibility while actually differing in code level, trust material,
     configuration, or enforcement behavior

3. **Activation boundaries are blurry**
   - without explicit desired-vs-active policy identity and a single activation authority, it is too
     easy for different participants to behave as though a policy is active at different times

4. **Ops needs better failure semantics**
   - configuration mismatch, readiness mismatch, downgrade, and drift should be explicit security
     outcomes, not side effects hidden inside runtime behavior

5. **Console and engine need a cleaner contract**
   - the Console repo should not infer engine/client behavior from scattered flags
   - the engine repo should expose a clear shared contract and client surface that downstream repos
     can integrate against safely

The architectural response to those problems is this redesign:

- move from independent runtime flags to an explicit namespace-level security policy
- separate **deployment capabilities** from **runtime policy requirements**
- make policy identity explicit via version + digest
- make activation state explicit via `REQUESTED` / `VALIDATING` / `ACTIVE`
- make readiness and mismatch reasons explicit
- keep status as telemetry only, not a trust source
- keep runtime enforcement fail-closed where policy requires it

Just as importantly, this redesign is **not** intended to make the default experience heavier.

The design must still preserve:

- a sensible lightweight bare-engine path
- default `COMMUNITY_OPEN` behavior when no explicit secured policy is active
- the ability to run community/unsecured deployments without trust anchors or extra bootstrap
  material

So the point of this change is not “security everywhere by default.”

The point is:

- explicit policy instead of implicit posture
- explicit compatibility instead of assumptions
- explicit activation instead of accidental partial rollout
- explicit incidents/mismatches instead of hidden failures
- a releasable engine+client contract that the Console repo can depend on

## 1.1 Shared terminology for these requirements

The terms below are used consistently throughout this document and the companion Console
implementation plan.

| Term | Meaning in these requirements |
|---|---|
| **Canonical policy identity** | The exact identity of a namespace policy, consisting of at least `policyVersion` plus a canonical `policyHash` / digest of the effective policy content. |
| **Desired policy identity** | The requested policy identity for a namespace, consisting of `desiredPolicyVersion` + `desiredPolicyHash`. |
| **Active policy identity** | The currently authoritative policy identity for a namespace, consisting of `activePolicyVersion` + `activePolicyHash`. |
| **Desired policy** | The policy configuration requested by operators / control plane for a namespace. |
| **Active policy** | The policy currently authoritative for protected BPMN/runtime behavior in a namespace. |
| **Activation authority** | The single component allowed to transition a namespace policy from `REQUESTED` / `VALIDATING` to `ACTIVE`. Participants may report readiness, but must not individually decide that a policy is active. |
| **`REQUESTED`** | A policy has been submitted but is not yet authoritative for protected data-plane behavior. |
| **`VALIDATING`** | Required participants are being checked for convergence and capability satisfaction against the requested policy. |
| **`ACTIVE`** | The policy has passed validation and is authoritative for protected data-plane behavior. |
| **Participant `READY`** | The participant has observed the exact active canonical policy identity and can satisfy the role-relevant requirements of that policy. |
| **Participant `NOT READY`** | The participant cannot satisfy the active policy, has drifted, or cannot prove convergence on the active canonical policy identity. |
| **Control-plane traffic** | Policy/status/security-event/key/trust-material and similar traffic needed for convergence, observability, and recovery. |
| **Protected data-plane traffic** | Policy-governed BPMN/runtime messages whose handling depends on the active policy and which must be gated by `ACTIVE` + `READY`. |
| **Required participants for activation** | The participants whose readiness is required before a stricter policy may become `ACTIVE`. In the first slice this means engine nodes assigned to the namespace, ingesters assigned to the namespace, and required control-plane participants. It does not include ephemeral clients, workers, or transient job handlers. |
| **False compatibility** | A participant reports compatibility with a requested policy version but is actually stale, misconfigured, or enforcing different content/behavior. |
| **Post-activation drift** | A participant that previously converged on the active policy later diverges from its canonical identity or can no longer satisfy required runtime checks. |

## 1.3 Architecture decisions now fixed for implementation

The architecture team has now fixed the following decisions for the first slice. Engine-repo
implementation should treat them as settled requirements rather than open questions.

1. **Topic naming is namespace-local**
   - `<tenant>.<namespace>.taktx-security-policy`
   - `<tenant>.<namespace>.taktx-participant-status`
   - `<tenant>.<namespace>.taktx-security-events`
2. **These topics are control-plane topics**, not BPMN/runtime protected data-plane topics.
3. **Control-plane topics do not participate in normal DLQ semantics.** Configuration/policy/status/
   event mismatches must be surfaced through validation rejection, readiness degradation, metrics,
   and security/configuration events instead.
4. **Platform Service is the sole activation authority** for the first slice.
5. **Participants may report readiness but may never independently transition a policy to `ACTIVE`.**
6. **`policyHash` is computed over requested effective policy content only**, excluding activation
   state, desired/active wrapper identity fields, timestamps, publisher identity, and unrelated
   metadata.
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
10. **Mismatch reasons should contain machine code, human-readable message, and optional structured
    metadata.**
11. **Authoritative mutation security baseline is fixed**:
    - trusted writer path only
    - Kafka ACL enforcement required
    - participant status remains non-authoritative telemetry
    - security events remain append-only and auditable
12. **Defense in depth is required**: authoritative policy consumers must ignore policy messages from
    unauthorized principals even if broker ACLs are misconfigured.
13. **Migration posture is fixed**:
    - `GlobalConfigurationDTO` remains operational for legacy behavior
    - `NamespaceSecurityPolicyDTO` is introduced in parallel
    - explicit `ACTIVE` namespace policy overrides legacy configuration
    - absent `ACTIVE` namespace policy preserves current/default `COMMUNITY_OPEN` behavior
14. **Status expiration semantics are required in the first slice**:
    - `participantInstanceId`
    - `startedAt`
    - `lastSeenAt`
    - `statusExpiresAt`
    - expired status must not participate in activation readiness decisions
15. **Break-glass downgrade semantics are fixed conceptually now**:
    - privileged actor
    - explicit reason
    - security/audit event
    - visible transition state
    - high-severity operational event classification
16. **Activation timeout semantics are required**:
    - a policy stuck in `VALIDATING` beyond the configured timeout must fail activation
    - emit a security/configuration event
    - preserve the previous `ACTIVE` policy
    - never partially activate

## 1.2 Handoff posture and ownership

This document intentionally treats the engine repo as the **upstream dependency** for the Console
repo.

### Engine repo ownership assumed by this document

The engine repo is assumed to own:

- engine runtime behavior
- shared DTO/client contracts required by engine/runtime interaction
- `TaktXClient` support for official control-plane operations
- tests proving the engine/client slice is releasable

### Console repo dependency posture

The Console repo should not implement bespoke replacements for the engine/client semantics described
here. Instead, it should wait for an engine/client release that satisfies this document and then
integrate against that release.

### Practical release expectation

The engine team's first goal is not just code merged in their repo; it is a releasable engine/client
artifact that the Console repo can adopt.

## 2. Verified current-state constraints from the Console repo

The following inputs are verified from the current Console repository and should be treated as
starting constraints for engine coordination:

1. Namespace security is currently represented in the Console repo as separate booleans:
   - `signingEnabled`
   - `engineRequiresAuthorization`
   - `engineRequiresExternalTaskAuthorization`
   - `engineRequiresUserTaskAuthorization`
2. Platform Service currently has no verified Kafka connection in this repo.
3. Current Console -> runtime delivery is:
   - Platform Service HTTP push to ingester
   - ingester publishes `GlobalConfigurationDTO` to the engine via `taktx-configuration`
4. Current architecture docs still describe this area as runtime flags rather than explicit policy.

These constraints matter because the first engine-compatible slice must be able to coexist with the
current topology during rollout.

They also matter because the Console repo currently depends on supported `TaktXClient` behavior for
several runtime interactions already. Any new official control-plane semantics needed for namespace
security policy should be delivered in the same upstream repo rather than recreated separately in the
Console repo.

## 3. Required design decisions

The following decisions are fixed for this requirements document:

1. **Namespace-owned policy** — security policy remains tied to each namespace.
2. **Official modes** — security posture must be expressible as explicit modes.
3. **Default mode is `COMMUNITY_OPEN`**.
4. **Simplest first slice** — incompatible policy activation should be rejected instead of activating
   degraded or excluded states.
5. **Status is not trust** — participant status is observability only.
6. **Enforcement remains runtime-local** — ingress checks, signature checks, JWT checks, and
   trust-anchor checks remain the real enforcement points.
7. **Canonical policy convergence is required** — required participants for a namespace must converge
   on the same explicit policy identity before a stricter policy becomes active.
8. **Protected data-plane participation is gated** — participants may continue consuming
   control-plane messages needed for convergence and observability, but must not publish, consume, or
   process protected BPMN/runtime data-plane messages under a requested policy unless that policy is
   `ACTIVE` and the participant is `READY` for the exact active policy identity.
9. **Policy activation authority is explicit** — only one component may mark a requested policy
   `ACTIVE`; participants may report readiness but must not individually decide that a policy is
   active.

## 4. Required security modes

The engine-side model must recognize the following effective modes:

```java
enum SecurityMode {
  COMMUNITY_OPEN,
  COMMUNITY_SECURED,
  ANCHORED_SECURED,
  MISCONFIGURED_SECURITY
}
```

### Mode intent

| Mode | Meaning |
|---|---|
| `COMMUNITY_OPEN` | Minimal trust posture; Kafka ACLs / network trust only; no mandatory signing/auth by default |
| `COMMUNITY_SECURED` | Runtime signing and/or authorization enabled with community trust semantics |
| `ANCHORED_SECURED` | Runtime signing/auth enabled with explicit root-anchored trust |
| `MISCONFIGURED_SECURITY` | Current runtime policy cannot be satisfied by available participant capabilities |

### Default requirement

If no explicit security policy has been activated yet, the system must behave as `COMMUNITY_OPEN`.

That means:
- no mandatory trust-anchor bootstrap
- no mandatory signing/auth requirement solely because the software was upgraded
- existing lightweight community-style setups remain runnable

That includes a bare engine deployment with no Console/control-plane integration: it must still run
with sensible `COMMUNITY_OPEN` semantics rather than failing because policy inputs are absent.

## 4.1 Canonical policy identity

For the first slice, participants must not rely on `policyVersion` alone to prove they are operating
against the same policy content.

The shared contract should include a canonical policy identity made up of at least:

- `policyVersion`
- canonical `policyHash` / digest of the effective policy payload

The policy digest exists to detect `same version, different content` ambiguity.

For the first slice, canonical hashing is defined over the requested effective policy content only.
It must exclude transition-state wrappers and non-functional metadata such as `activationState`,
timestamps, publisher identity, and desired/active identity wrapper fields.

Canonicalization rules are:

- UTF-8 encoding
- deterministic field order
- explicit booleans always present
- omit null/unknown fields
- lowercase enum serialization
- stable nested-object ordering
- SHA-256 digest
- lowercase hexadecimal output

For the first slice, the contract should distinguish between:

- `desiredPolicyVersion`
- `desiredPolicyHash`
- `activePolicyVersion`
- `activePolicyHash`
- `activationState`

This avoids ambiguity during `REQUESTED` / `VALIDATING`, where a requested policy identity must
exist before activation while the previous active policy still governs protected behavior.

## 4.2 Control plane vs protected data plane

The shared model must explicitly distinguish between:

- **control-plane traffic** — policy, status, security events, keys, trust material, and other
  convergence/observability records
- **protected data-plane traffic** — policy-governed BPMN/runtime message flows whose handling is
  supposed to change with the active security posture

Control-plane availability does **not** mean arbitrary producers may overwrite authoritative desired
state. The shared design must distinguish between:

- control-plane **consumption/availability** for convergence and observability
- authoritative control-plane **mutation** of policy/configuration/trust material

For the first slice:

- control-plane traffic must remain consumable while a policy is `REQUESTED` or `VALIDATING`
- the approved control-plane topics are:
  - `<tenant>.<namespace>.taktx-security-policy`
  - `<tenant>.<namespace>.taktx-participant-status`
  - `<tenant>.<namespace>.taktx-security-events`
- these topics are control-plane only and are not BPMN/runtime protected data-plane topics
- these topics do not participate in normal DLQ semantics
- protected data-plane behavior must continue following the previously active policy until the new
  policy becomes `ACTIVE`
- if there is no previously active secured policy, the default remains `COMMUNITY_OPEN`
- once a policy is `ACTIVE`, only `READY` participants for the exact active policy identity may
  process, acknowledge as successfully handled, commit/mark successful, or publish derived protected
  data-plane output for that namespace

Each participant role must document which message classes/topics are control-plane and which are
protected data-plane for that role.

## 4.3 Control-plane mutation security

The architecture must not create a path where anyone able to write random Kafka records can
overwrite authoritative namespace policy or trust material.

Required first-slice assumptions/requirements:

- authoritative control-plane writes must be restricted to explicitly trusted writers
- broker-side authorization / ACLs remain a baseline requirement for authoritative control-plane
  topics
- authoritative policy consumers must ignore policy messages from unauthorized principals even if
  broker ACLs are misconfigured
- in secured modes, authoritative control-plane messages should also be integrity-protected and,
  where supported by the shared client/runtime contract, verifiable as originating from an authorized
  control-plane publisher
- participant status remains non-authoritative even if its write/read posture is looser than the
  desired-policy path

In other words: keep control-plane traffic available for convergence, but do not make authoritative
control-plane mutation unauthenticated or arbitrary.

## 4.4 Required client support

If TaktX needs new control-plane manipulation capabilities for the redesigned policy model, those
capabilities should be added to `TaktXClient` (or the official shared runtime client surface) and
then used by Console/ingester/runtime code.

The goal is to avoid duplicating bespoke Kafka publishers/consumers for official control-plane
semantics in multiple repos.

For this handoff, `TaktXClient` changes are not optional follow-up work; they are part of the same
upstream delivery expected from the engine repo.

## 4.5 Policy activation authority

Policy activation authority must be explicit.

Only one component may transition a namespace policy from `REQUESTED` / `VALIDATING` to `ACTIVE`.
Participants may report readiness, but must not individually decide that a policy is active.

For the first slice, **Platform Service is the activation authority** and acts as the policy
controller.

Participants may report readiness, but they may never independently transition a policy to `ACTIVE`.
The rest of this document assumes Platform Service is authoritative and all participants are
subordinate to that authority for activation.

## 5. Namespace security policy requirements

The engine must be able to consume an explicit namespace security policy model containing at least:

```json
{
  "mode": "ANCHORED_SECURED",
  "activationState": "REQUESTED",
  "desiredPolicyVersion": 42,
  "desiredPolicyHash": "abc123",
  "activePolicyVersion": 41,
  "activePolicyHash": "def456",
  "requiredSigning": {
    "engineOutbound": true,
    "clientCommands": true,
    "workerResponses": true
  },
  "requiredAuthorization": {
    "startCommands": true,
    "externalTaskCompletion": true,
    "userTaskCompletion": true
  },
  "trustAnchorRequired": true,
  "policyVersion": 42
}
```

### Required policy semantics

1. `mode` is the operator-facing desired posture.
2. `requiredSigning` enumerates explicit runtime signing requirements; these must not be inferred from
   unrelated flags.
3. `requiredAuthorization` enumerates explicit runtime authorization requirements.
4. `trustAnchorRequired` explicitly indicates whether anchored trust is required.
5. `policyVersion` must monotonically identify the desired policy generation.
6. The effective policy contract should support a canonical `policyHash` / digest so participants can
   prove they observed the same policy content, not only the same version number.
7. The contract should distinguish requested/desire-state identity from active identity during policy
   transitions.

### Validation rules

The engine-compatible contract must reject invalid partial states.

Examples:
- anchored mode without trust-anchor requirement -> reject
- policy that requires a capability the participant cannot ever satisfy -> reject activation or
  report mismatch according to the agreed lifecycle
- policy payload missing a required version field -> reject

## 6. Participant capability model requirements

Each participant must evaluate:

```text
effectiveState = deploymentCapabilities + namespacePolicy
```

### Participant set

The target model currently includes:
- `ENGINE`
- `WORKER`
- `CLIENT`
- `INGESTER`
- `CONSOLE`

### Required participants for activation

For the first slice, the following are required for activation of stricter secured modes:

- engine nodes assigned to the namespace
- ingesters assigned to the namespace
- control-plane participants required for policy operation

The following are **not** required for activation and should instead be validated at use time:

- external clients
- workers
- transient job handlers

### Required behavior

Each participant must be able to answer:
- `participantId`
- `participantInstanceId`
- `startedAt`
- `lastSeenAt`
- `statusExpiresAt`
- which capabilities it has from deployment/runtime bootstrap
- which namespace policy version it has observed
- which canonical policy digest it has observed
- whether it is ready for the protected data plane under that policy
- why it is not ready, if it is not ready

### First-slice simplification

For the first slice, incompatible policy activation should be rejected rather than activating a
partially degraded state.

This is the chosen approach to reduce edge cases.

### Required convergence rule

For secured modes, a stricter policy may become active only if all required participants for the
namespace have converged on the same canonical policy identity and can satisfy their role-relevant
requirements.

If any required participant:
- observes a different canonical policy identity
- cannot satisfy the required policy
- or cannot prove readiness for its role-relevant subset

then activation must be rejected and the previously active policy must remain in force.

Participants must not begin protected data-plane behavior for the requested policy merely because
they have observed it; `ACTIVE` + `READY` is required.

## 7. Control-plane requirements

The engine-side implementation must support three logically separate control-plane concerns:

1. authoritative namespace security policy
2. participant posture / readiness status
3. append-only security events

### 7.1 Security policy stream

The engine must consume the authoritative desired namespace security policy from a compacted
control-plane stream.

#### Required properties
- authoritative desired state
- compacted
- versioned by desired/active policy identity fields
- not derived from participant status

#### Important note
The exact topic naming must be finalized jointly with the Console team. This is a hard blocker for
implementation, not a later clean-up item.

This document intentionally does **not** hard-code the final topic name because the supplied proposal
contains both:
- `<tenant>.<namespace>.taktx-security-policy`
- `taktx-security-policy`

That naming ambiguity must be resolved before implementation.

**Recommended naming pattern:**

- `<tenant>.<namespace>.taktx-security-policy`
- `<tenant>.<namespace>.taktx-participant-status`
- `<tenant>.<namespace>.taktx-security-events`

Namespace-local topic naming fits the existing deployment model better and should be treated as the
default recommendation unless a different pattern is deliberately chosen.

### 7.2 Participant status stream

The engine must publish or consume participant status as an observability stream only.

Required payload fields include at least:

```json
{
  "participantId": "engine-2",
  "participantInstanceId": "engine-2-pod-7f8c4d",
  "role": "ENGINE",
  "namespace": "bank.payments",
  "startedAt": 1716450000000,
  "lastSeenAt": 1716450060000,
  "statusExpiresAt": 1716450120000,
  "statusVerificationLevel": "UNVERIFIED_STATUS",
  "effectiveState": "MISMATCH",
  "readyForDataPlane": false,
  "observedPolicyVersion": 42,
  "observedPolicyHash": "abc123",
  "mismatchReasons": [
    {
      "code": "TRUST_ANCHOR_MISSING",
      "message": "Namespace requires anchored trust but no platform public key is configured"
    }
  ]
}
```

#### Required properties
- compacted or otherwise latest-state oriented
- heartbeat + state update capable
- not used to establish trust
- mismatch reasons must include machine code, human-readable message, and may include optional
  structured metadata
- readiness must be interpreted as permission to participate in protected data-plane work only for
  the exact active canonical policy identity
- stale status must naturally expire and stop being treated as current posture after `statusExpiresAt`
- expired status must not participate in activation readiness decisions
- status verification level may improve telemetry quality, but even verified/signed status remains
  telemetry rather than trust
- first-slice verification-level vocabulary is fixed to:
  - `UNVERIFIED_STATUS`
  - `LOCALLY_VERIFIED_STATUS`
- first-slice effective-state vocabulary is fixed to:
  - `READY`
  - `NOT_READY`
  - `MISMATCH`
  - `STALE`

### 7.3 Security events stream

The engine-compatible model must support append-only security events for:
- policy changes
- policy rejection
- readiness mismatch
- downgrade / privileged transition
- trust-anchor problems
- other security incidents

In anchored mode, signed events are preferred if supported by the final shared contract.

Break-glass downgrade operations should be treated as high-severity operational/security events.

## 8. Runtime transition requirements

Policy changes are versioned and must affect new ingress according to the active version.

### Required transition behavior

| Transition | Required behavior |
|---|---|
| `COMMUNITY_OPEN -> COMMUNITY_SECURED` | allowed when validated |
| `COMMUNITY_SECURED -> ANCHORED_SECURED` | allowed when validated |
| `ANCHORED_SECURED -> COMMUNITY_SECURED` | allowed only as privileged break-glass downgrade with reason + audit |
| invalid partial states | rejected |
| required participants do not converge on same canonical policy identity | rejected |

### Activation lifecycle requirement

The required lifecycle for the first slice is:

```text
REQUESTED -> VALIDATING -> ACTIVE
```

If validation fails, the policy change must be rejected.

If validation fails because not all required participants can satisfy the requested policy, the
previous active policy must remain authoritative.

If a policy remains in `VALIDATING` beyond the configured activation timeout, activation must fail,
the previous active policy must remain authoritative, a security/configuration event must be emitted,
and the system must never partially activate the requested policy.

That includes protected data-plane behavior: participants must continue following the previous active
policy for policy-governed runtime traffic until the requested policy actually becomes `ACTIVE`.

### Required failure / rollback semantics

The first slice must define explicit fail-closed behavior for at least:

- activation timeout
- participant disappearance during validation
- policy publication failure
- out-of-order policy messages
- compacted-topic tombstone handling
- duplicate policy message handling
- rollback to the previous active policy after failed activation

### First-slice simplification

Do **not** implement a partially active degraded state in the first slice unless the teams later make
an explicit decision to do so.

The simpler requirement is:
- validate
- activate if valid
- reject if incompatible

## 9. Readiness and enforcement requirements

The engine must keep a strict separation between posture reporting and trust enforcement.

### Status is observability only

Participant status must never be used as proof that a message is trusted.

Reported compatibility is therefore diagnostic only. A participant may report compatibility with a
requested policy version and still be wrong, stale, or misconfigured.

Therefore, status must never be used to justify early protected data-plane participation under a
policy that is not yet `ACTIVE`.

### Enforcement remains in runtime checks

Actual protection must still come from runtime validation paths such as:
- ingress validation
- signature verification
- JWT validation
- trust-anchor validation
- claim-to-runtime-context matching where applicable

### Required failure behavior

If a participant cannot satisfy the active policy:
- protected operations fail closed
- participant is not ready for protected data-plane work
- a security incident / mismatch event is produced

If a participant later drifts from the active canonical policy identity or fails the runtime checks
required by that policy, it must transition to `NOT READY` regardless of what it previously
reported.

Once `NOT READY`, it must stop protected data-plane participation for that namespace while still
being allowed to consume whatever control-plane traffic is required for recovery and observability.

Participants are not required to pause Kafka consumption in one particular implementation-specific
way. The requirement is that they must not process, acknowledge as successfully handled, commit/mark
successful, or publish derived protected data-plane side effects unless `READY` for the exact active
policy identity.

## 10. DLQ boundary requirement

Configuration mismatches and security posture mismatches are **not** DLQ events.

They must instead be surfaced as:
- config validation rejection
- readiness degradation / non-readiness
- security incident events
- metrics / alerts / audits

DLQ remains reserved for:
- ingress that was accepted into normal processing and later failed processing or validation

## 11. Backward compatibility requirements

The engine-side design must preserve a safe rollout path from the current flag-based model.

### Required compatibility behavior

1. Existing unsecured / community deployments must continue to run by default.
2. Upgrading software alone must not force security bootstrap material.
3. Current flag-based environments must have a deterministic migration path into namespace policy.
4. If policy support is only partially wired, the system must fail clearly rather than silently
   accepting the wrong posture.
5. Mixed-version or mixed-capability participant clusters must not silently activate a stricter
   policy unless all required participants converge on the same canonical policy identity.
6. Participants must not publish, consume, or process protected BPMN/runtime data-plane traffic under
   a requested policy before that policy is `ACTIVE` for the namespace and the participant is
   `READY` for that exact active identity.
7. Bare-engine/community deployments must remain runnable without requiring control-plane bootstrap.
8. Authoritative control-plane mutation must be explicitly protected; availability of control-plane
   traffic must not imply open write access to desired policy.
9. Official control-plane publication/consumption semantics should be provided through `TaktXClient`
   rather than duplicated ad-hoc publishers.
10. Activation authority, desired-vs-active identity semantics, and namespace-local topic naming must
    be explicitly resolved before implementation starts.
11. Break-glass downgrade behavior must be privileged, reasoned, and audited.

## 12. Required engine work breakdown

The work breakdown below is intended to be directly trackable in the engine repo. It should be read
as engine+client work, not engine-only work.

## ENG-SP-01 — Shared policy model and parsing

**Goal:** add engine support for the explicit namespace security policy contract.

### Tasks
- [ ] Add shared parsing/validation support for `SecurityMode`.
- [ ] Add shared parsing/validation support for required signing requirements.
- [ ] Add shared parsing/validation support for required authorization requirements.
- [ ] Add policy-version handling.
- [ ] Add canonical policy digest handling.
- [ ] Reject invalid partial policy payloads.
- [ ] Document or encode which policy elements are relevant to each participant role so protected
      data-plane gating can be applied consistently.
- [ ] Define the client/runtime contract for authoritative control-plane mutation, including the
      security properties expected of writers.
- [ ] Define desired-vs-active identity fields and activation-state semantics.
- [ ] Define activation authority handoff/ownership explicitly.

### Acceptance criteria
- Engine code can parse and validate explicit namespace security policy.
- Invalid policy payloads are rejected explicitly.
- Missing or malformed `policyVersion` is not silently ignored.
- The engine can distinguish `same version` from `same policy content`.
- The shared contract for authoritative control-plane mutation is explicit rather than implicit.

## ENG-SP-02 — Policy consumption and activation

**Goal:** consume the authoritative policy stream and activate only validated policy versions.

### Tasks
- [ ] Consume the authoritative namespace policy stream.
- [x] Implement `REQUESTED -> VALIDATING -> ACTIVE` behavior.
- [x] Reject incompatible policies instead of activating degraded states.
- [x] Reject activation when required participants have not converged on the same canonical policy
      identity.
- [x] Preserve `COMMUNITY_OPEN` as the effective default when no explicit policy is active.
- [x] Keep protected data-plane behavior governed by the previous active policy until the new policy
      becomes `ACTIVE`.
- [x] Preserve sensible bare-engine behavior when no external control-plane publisher is present.
- [x] Implement explicit timeout / rollback behavior for failed or stalled activation.

### Acceptance criteria
- New policy versions are validated before activation.
- Invalid or incompatible policies do not silently become active.
- Default behavior remains lightweight and unsecured when no policy has been activated.
- The previous active policy remains in force when convergence or capability checks fail.
- Protected data-plane behavior does not switch early during `REQUESTED` / `VALIDATING`.
- Bare-engine deployments remain functional under `COMMUNITY_OPEN` without extra control-plane
  bootstrap dependencies.

## ENG-SP-03 — Capability evaluation and readiness

**Goal:** make participant capability satisfaction explicit and observable.

### Tasks
- [x] Compute participant readiness from deployment capabilities + namespace policy.
- [x] Emit explicit mismatch reasons when requirements are not met.
- [x] Include observed canonical policy identity in readiness reporting.
- [ ] Keep observed posture separate from trust decisions.
- [ ] Ensure readiness is the gate for protected data-plane participation under the active policy.
- [x] Include participant incarnation / TTL fields in status reporting.
- [ ] Optionally include status verification-level telemetry without treating it as trust.

### Acceptance criteria
- Participants can report readiness and mismatch reasons against a specific policy version.
- `MISCONFIGURED_SECURITY` can be derived from real incompatibility.
- Status output is never used as a trust shortcut.
- Participants can detect and report `same version, different content` policy mismatches.
- Participants distinguish between control-plane consumption and protected data-plane readiness.

## ENG-SP-04 — Enforcement alignment

**Goal:** ensure runtime enforcement follows the explicit policy model.

### Tasks
- [ ] Map signing requirements to actual signature enforcement points.
- [ ] Map authorization requirements to actual JWT / auth enforcement points.
- [ ] Map trust-anchor requirements to explicit trust-anchor validation.
- [ ] Fail closed when required checks cannot be satisfied.
- [x] Treat false compatibility claims and post-activation drift as enforcement-relevant non-ready
      conditions, not as reasons to weaken checks.
- [ ] Prevent protected data-plane participation when policy is not `ACTIVE` or participant is not
      `READY` for the exact active identity.
- [ ] Ensure authoritative control-plane updates are accepted only from trusted/authorized writer
      paths and never from arbitrary message injection.

### Acceptance criteria
- Runtime enforcement matches the active policy requirements.
- Required checks fail closed when missing.
- Posture status does not replace runtime enforcement.
- A participant that previously reported compatibility but later fails policy-required checks becomes
  `NOT READY` and does not continue protected work silently.
- Control-plane recovery/observability traffic can still flow while protected data-plane work is
  blocked.
- The design does not rely on unauthenticated/unauthorized arbitrary Kafka writes for desired-policy
  mutation.

## ENG-SP-05 — Status and security events

**Goal:** provide explicit observability for security posture without changing trust semantics.

### Tasks
- [x] Emit participant status updates with observed policy version and mismatch reasons.
- [x] Emit append-only security events for policy changes, rejection, and incidents.
- [ ] Ensure anchored-mode event signing is supported if the final shared contract requires it.
- [x] Emit explicit events for convergence failure and post-activation drift.
- [x] Emit explicit events when protected data-plane participation is blocked because policy is not
      yet `ACTIVE` or participant is not `READY`.
- [x] Emit explicit events when authoritative control-plane mutation is rejected for security reasons.
- [x] Emit explicit events for activation timeout, rollback, and break-glass downgrade.

### Acceptance criteria
- Ops can distinguish policy activation, mismatch, and incident events.
- Status and events provide diagnosis value without becoming an authority source.
- False-compatibility and drift scenarios are visible as explicit events, not silent state changes.
- Transition-state blocking of protected data-plane work is observable and diagnosable.
- Unauthorized control-plane mutation attempts are observable.

## ENG-SP-05a — Official client support

**Goal:** ensure control-plane manipulation uses supported shared client APIs instead of duplicated
custom Kafka logic.

### Tasks
- [x] Add the required official `TaktXClient` methods for control-plane publication/consumption used
      by the policy model.
- [x] Document the expected security properties of those methods.
- [ ] Update runtime/ingester integrations to use those methods rather than bespoke duplicate
      publishers where equivalent semantics are needed.
- [x] Add the official shared DTO/contract support needed for desired-vs-active policy identities.

### Acceptance criteria
- Required control-plane operations have an official supported client surface.
- The supported client surface includes both namespace security policy publication and compacted-topic
  tombstone/clear semantics.
- Engine/runtime integrations do not require parallel bespoke publishers for the same control-plane
  semantics.

## ENG-SP-05b — Release packaging for Console consumption

**Goal:** produce a releasable engine-repo artifact that the Console repo can adopt without relying
on unpublished or bespoke patches.

### Tasks
- [ ] Publish an engine/client release containing the required shared DTO and `TaktXClient` changes.
- [ ] Document the exact released version(s) the Console repo should consume.
- [ ] Document any migration notes or compatibility constraints relevant to Console integration.
- [ ] Ensure release notes clearly identify which namespace-security-policy capabilities are included.

### Acceptance criteria
- A concrete released engine/client version exists for Console adoption.
- The release includes the required official `TaktXClient` support and shared contract changes.
- Console maintainers can point to a single released upstream dependency rather than a set of
  unpublished commits.

## ENG-SP-06 — Test and verification matrix

**Goal:** prove the engine slice is correct, compatible, and safe to roll out.

### Required tests
- [ ] parse valid policy payloads
- [ ] reject invalid partial states
- [ ] default to `COMMUNITY_OPEN` when no explicit policy is active
- [ ] activate `COMMUNITY_SECURED` after successful validation
- [ ] activate `ANCHORED_SECURED` only when trust-anchor requirements are satisfied
- [ ] reject incompatible policy activation
- [ ] reject activation when required participants observe different canonical policy identities
- [ ] emit explicit mismatch reasons
- [ ] ensure missing required signature fails closed
- [ ] ensure missing required JWT fails closed
- [ ] ensure missing required trust anchor fails closed
- [ ] ensure participant status never changes trust outcomes
- [ ] ensure false compatibility reports do not weaken runtime enforcement
- [x] ensure post-activation drift yields `NOT READY` posture and incident/event output
- [x] ensure protected data-plane behavior remains on previous active policy during `REQUESTED` /
      `VALIDATING`
- [ ] ensure control-plane traffic remains available during convergence/recovery
- [ ] ensure engine and client participants do not publish/consume/process protected runtime traffic
      unless policy is `ACTIVE` and they are `READY`
- [ ] ensure bare engine remains functional with sensible `COMMUNITY_OPEN` defaults when no external
      control plane is present
- [ ] ensure authoritative control-plane mutation is rejected when attempted from untrusted writers
- [x] ensure the required control-plane client operations are available via `TaktXClient`
- [x] ensure stale participant status expires via TTL/incarnation handling
- [x] ensure activation timeout / rollback behavior is deterministic and fail-closed
- [ ] ensure break-glass downgrade requires privileged role + reason + audit event
- [x] ensure downgrade transitions are observable / auditable

### Acceptance criteria

- Rejected/invalid policy mutation attempts emit explicit engine-side observability events even before
  full untrusted-writer authentication/authorization enforcement is completed.

- Engine-side downgrade handling requires explicit break-glass actor/reason metadata and emits audit
  events; full privileged-role verification remains part of the authoritative control-plane contract.
- Engine behavior is proven by tests for both default-community and secured-policy flows.
- Negative cases fail clearly and predictably.
- The engine slice is safe to pair with the Console-side rollout plan.

## 13. Engine-repo release gate for Console adoption

The Console repo should treat this document as satisfied only when the engine repo has delivered a
release that includes the required engine and `TaktXClient` changes.

### Required release-gate outputs

- released engine/runtime artifact(s)
- released/shared `TaktXClient` artifact(s)
- final shared DTO/contract documentation for policy, status, and security events
- release notes identifying supported namespace-security-policy capabilities

### Console adoption rule

Console-side implementation that depends on these semantics should target published engine/client
artifacts, not ad-hoc snapshots or parallel bespoke publishers.

## 14. Overall acceptance criteria

The engine-repo handoff requirements are satisfied when:

- explicit namespace security policy is supported
- `COMMUNITY_OPEN` remains the effective default with no extra bootstrap burden
- incompatible policy activation is rejected rather than partially activated
- readiness and mismatch reasons are explicit
- status is observability only and not trust
- runtime enforcement remains fail-closed where policy requires it
- security incidents and policy transitions are observable
- tests prove both lightweight community defaults and secured modes
- canonical policy convergence and false-compatibility / drift scenarios are explicitly handled and
  tested
- protected data-plane gating semantics are explicitly handled and tested for engine/client
  participants
- bare-engine defaults remain intact
- authoritative control-plane mutation is explicitly protected
- required control-plane client support exists in `TaktXClient` rather than requiring bespoke
  duplicate publishers
- a released engine/client version exists that the Console repo can consume

## 14.1 Future extension deliberately outside the first slice

### SP-VH-01 — Verifiable process instance update chain

If strategic verifiability is a long-term direction, the engine repo should track a separate future
epic for verifiable update-chain / hash-chaining semantics.

This is deliberately **not** part of the first namespace-security-policy slice, but it should remain
documented as a forward extension rather than being rediscovered later.

## 15. Explicit questions for engine review

1. What is the final agreed topic naming for the authoritative policy stream, participant status, and
   security events?
2. Can the current engine config / control-plane infrastructure carry this policy model directly, or
   is a new control-plane path required?
3. Which runtime components in the engine own capability evaluation for `ENGINE`, `WORKER`, and
   `CLIENT` roles?
4. What is the authoritative source of trust-anchor material in anchored mode?
5. What minimal shared DTO change is needed so the Console repo can publish policy without relying on
   unverified assumptions?
6. What exact canonicalization rules should define `policyHash` so all participants compute the same
   digest for the same effective policy payload?
7. Which component is the activation authority in the first slice?
8. Do you agree with the recommended namespace-local topic naming pattern, or is there a deliberate
   alternative?








