# Console Security Control-Plane Handoff

**Status:** Ready for Console implementation handoff  
**Date:** 2026-05-26  
**Branch reality:** the implementation described here exists on the current `0.8.0-beta` branch line; treat external publication of Maven artifacts / releases as a separate release-management step unless independently confirmed.  
**Audience:** Console backend, frontend, and integration engineers

---

## 1. Purpose

This document is the **authoritative starting point for the Console team** to adopt the new namespace
security control plane.

It consolidates the current state of the work in this repository so Console implementers do not need to
reconstruct it from multiple internal trackers.

Use this document for:

- the current contract and semantics
- what is already implemented upstream in this repository
- what Console should build on top of that contract
- migration guidance from legacy namespace security booleans
- recommended validation scenarios and known limitations

For deeper rationale and background, the detailed design/reference documents remain:

- `docs/SECURITY-POLICY-ENGINE-REQUIREMENTS.md`
- `docs/SECURITY-POLICY-IMPLEMENTATION-PLAN.md`
- `docs/console-namespace-security-migration-notes.md`

---

## 2. Executive summary

The upstream engine/client/shared work for namespace-level security policy is now in place on this
branch.

Console should now treat namespace security as an **explicit policy-driven control plane**, not as a set
of loosely related legacy booleans.

### What changed upstream

The engine/client/shared modules now provide:

- explicit namespace security policy via `NamespaceSecurityPolicyDTO`
- explicit desired-vs-active policy identity
- explicit activation state: `REQUESTED`, `VALIDATING`, `ACTIVE`
- public observability over namespace-local control-plane topics
- participant posture reporting via a capability-based participant model
- public `TaktXClient` helpers for authoritative policy publication and policy clear/tombstone
- fail-closed protected runtime gating when policy requires JWT, signatures, and/or trust anchors
- namespace-scoped security events and posture snapshots that Console can display directly

### What Console should do next

Console should now implement a security-control-plane slice that:

1. authors namespace policy as `NamespaceSecurityPolicyDTO`
2. publishes that policy through the supported `TaktXClient` surface
3. consumes policy/status/event observability through the supported public client surface
4. updates Ops UX to show **desired policy**, **active policy**, **activation state**, **participant readiness**, and **security events**
5. stops treating legacy namespace booleans as the long-term authority source

### Important boundary

The current branch is implementation-ready for handoff, but this document does **not** claim that a
public release has already been published unless release management confirms that separately.

---

## 3. Current upstream state in this repository

### 3.1 Implemented contract surface

The following core contract and client pieces are already present in this repository:

#### Shared DTOs / enums

- `io.taktx.dto.NamespaceSecurityPolicyDTO`
- `io.taktx.dto.SecurityMode`
- `io.taktx.dto.SecurityActivationState`
- `io.taktx.dto.SecurityParticipantDescriptor`
- `io.taktx.dto.ParticipantStatusDTO`
- `io.taktx.dto.ParticipantCapability`
- `io.taktx.dto.ParticipantKind`
- `io.taktx.dto.ParticipantEffectiveState`
- `io.taktx.dto.StatusVerificationLevel`
- `io.taktx.dto.SecurityEventDTO`
- `io.taktx.dto.SecurityEventType`

#### Public client facets relevant to Console

- `TaktXClient.security()`
- `TaktXClient.observability()`
- `TaktXClient.runtime()`
- `TaktXClient.workers()`
- `TaktXClient.dlq()`

#### Policy mutation helpers

- `SecurityClient.publishNamespaceSecurityPolicy(...)`
- `SecurityClient.clearNamespaceSecurityPolicy()`

#### Public observability helpers

- `SecurityObservabilityClient.getObservedPolicySnapshot()`
- `SecurityObservabilityClient.getParticipantStatusSnapshot()`
- `SecurityObservabilityClient.getRecentSecurityEvents()`
- `SecurityObservabilityClient.getPostureSnapshot()`
- `SecurityObservabilityClient.awaitObservedPolicy(...)`
- `SecurityObservabilityClient.awaitParticipantStatusSnapshot(...)`
- `SecurityObservabilityClient.awaitPostureSnapshot(...)`
- `SecurityObservabilityClient.awaitSecurityEvent(...)`
- `SecurityObservabilityClient.registerNamespaceSecurityPolicyConsumer(...)`
- `SecurityObservabilityClient.registerParticipantStatusConsumer(...)`
- `SecurityObservabilityClient.registerSecurityEventConsumer(...)`

### 3.2 Implemented behavior upstream

The current branch already implements the following behavior:

- explicit namespace-local policy publication on a compacted control-plane topic
- signed authoritative policy mutation / clear path
- authoritative-policy consumer-side rejection of untrusted mutation attempts
- public observation of current and authoritative/effective policy posture
- public participant-status observation keyed by participant instance ID
- public recent security-event observation
- combined console-grade posture snapshots assembled from public topics only
- protected runtime fail-closed behavior when the active policy requires JWT, signatures, or trust anchors
- anchored-mode mismatch visibility, including participant mismatch state and `DATA_PLANE_BLOCKED` / `READINESS_MISMATCH` style eventing
- namespace isolation in public observability and runtime-facing dogfood tests

### 3.3 Validation already done upstream

This repository has already exercised the control-plane/client slice with focused public-client dogfood
coverage and broader engine security integration coverage.

In practical terms, the branch already proves that a real caller using the public client surface can:

- publish namespace policy
- clear namespace policy
- observe policy posture publicly
- observe participant status publicly
- observe security events publicly
- see fail-closed mismatch posture for anchored trust problems
- drive runtime behavior through policy-aware public client APIs

### 3.4 Known remaining gap upstream

The main intentionally deferred validation gap is **multi-engine clustered public-client verification**.

The current branch is strong on the single-engine public-client control-plane slice, but it is not yet
claiming completed public-only validation across multiple real engine nodes sharing the same namespace.

Console should not block handoff on that gap, but should be aware of it when planning rollout and CI.

---

## 4. Control-plane model Console should assume

## 4.1 Namespace-local topics

The fixed first-slice namespace-local control-plane topics are:

- `<tenant>.<namespace>.taktx-security-policy`
- `<tenant>.<namespace>.taktx-participant-status`
- `<tenant>.<namespace>.taktx-security-events`

These are **control-plane topics**, not protected BPMN/runtime data-plane topics.

### Important consequence

These topics do **not** participate in normal DLQ semantics.

Console should not model control-plane mismatch visibility as a DLQ workflow. Instead:

- policy rejection is surfaced through security events / activation outcome
- readiness problems are surfaced through participant status and security events
- protected runtime blocking is surfaced through readiness/mismatch state plus eventing

---

## 4.2 Security modes

The operator-facing policy modes are:

- `COMMUNITY_OPEN`
- `COMMUNITY_SECURED`
- `ANCHORED_SECURED`
- `MISCONFIGURED_SECURITY`

### Meaning

- `COMMUNITY_OPEN`
  - lightweight/default posture
  - no secured policy requirements are active
  - intended to preserve existing easy-start behavior
- `COMMUNITY_SECURED`
  - secured posture without anchored trust-anchor requirement
  - policy may require JWT and/or signing depending on policy fields
- `ANCHORED_SECURED`
  - secured posture with trust-anchor requirement
  - missing trust-anchor configuration must fail closed for protected runtime participation
- `MISCONFIGURED_SECURITY`
  - operator-visible invalid/problem posture, not a desirable target mode for normal rollout

---

## 4.3 Activation states

The policy lifecycle state is explicit:

- `REQUESTED`
- `VALIDATING`
- `ACTIVE`

### Console interpretation rules

- `REQUESTED`
  - the policy has been submitted
  - it is not yet authoritative for protected runtime behavior
- `VALIDATING`
  - the system is checking whether required participants can converge on and satisfy the requested policy
  - protected runtime behavior still follows the previously authoritative active posture
- `ACTIVE`
  - the policy is authoritative for protected runtime behavior

### Critical UI/logic rule

Console must **not** treat the requested policy as enforced merely because it exists.

Desired policy and active policy may diverge temporarily during:

- normal rollout
- failed activation
- timeout
- break-glass downgrade
- post-activation drift handling

---

## 4.4 Desired vs active identity

Console should model **two identities**:

- desired policy identity
  - `desiredPolicyVersion`
  - `desiredPolicyHash`
- active policy identity
  - `activePolicyVersion`
  - `activePolicyHash`

This distinction is central to the new control plane.

### Why this matters

`policyVersion` alone is not sufficient because it does not distinguish:

- requested vs authoritative state
- same version number with different canonical content
- partially rolled or stale views of policy state

Console should prominently show both desired and active identity when they differ.

---

## 4.5 Activation authority

For the first slice, **Platform Service is the sole activation authority**.

Participants may:

- consume policy
- publish readiness / posture status
- publish security events

Participants may **not** independently decide that a requested policy is active.

That means Console backend logic should own the authoritative policy lifecycle transitions and should
not infer activation solely from participant self-report.

---

## 4.6 Status is observability, not trust

Participant status is operationally important, but it is **not an authority source**.

Console should use status to:

- diagnose convergence
- explain why activation is blocked
- identify drift or stale participants
- present operator-facing posture

Console should **not** use participant status alone to:

- establish trust
- declare a policy authoritative
- bypass trusted-writer or canonical-identity checks

---

## 5. Data contracts Console will consume

## 5.1 `NamespaceSecurityPolicyDTO`

The upstream policy DTO includes:

- `mode`
- `activationState`
- `desiredPolicyVersion`
- `desiredPolicyHash`
- `activePolicyVersion`
- `activePolicyHash`
- `requiredSigning`
- `requiredAuthorization`
- `trustAnchorRequired`
- `breakGlassActor`
- `breakGlassReason`
- legacy aliases `policyVersion` and `policyHash`

### Important migration note

The DTO is intentionally additive for the migration slice. `GlobalConfigurationDTO` still exists for
legacy behavior, but Console should treat explicit namespace policy as the long-term authority model.

---

## 5.2 `SecurityParticipantDescriptor`

Console-side client instances should identify themselves with a participant descriptor containing:

- `participantId`
- `kind`
- `capabilities`
- `componentType`

### Recommended Console descriptor

For a Console-side authoritative publisher / observer:

- `kind = CLIENT`
- `capabilities = [AUTHORITATIVE_POLICY_PUBLISHER, SECURITY_OBSERVER]`
- `componentType = "console"`

If a single Console-side component also participates in protected runtime traffic, it may carry
additional capabilities on the same descriptor.

---

## 5.3 `ParticipantStatusDTO`

Public posture status contains:

- participant identity / instance identity
- `participantKind`
- `componentType`
- `capabilities`
- timestamps / TTL data (`startedAt`, `lastSeenAt`, `statusExpiresAt`)
- verification level
- effective state
- readiness flag for protected data plane
- observed policy version/hash
- mismatch reasons

### Console expectations

- status is keyed by `participantInstanceId`
- expired status should naturally fall out of readiness interpretation
- multiple participant entries may exist for the same logical participant ID across restarts/incarnations
- not every participant needs to block activation; that depends on capability semantics

---

## 5.4 Capability semantics

The capability vocabulary is:

- `ENFORCER`
- `AUTHORITATIVE_POLICY_PUBLISHER`
- `PROTECTED_RUNTIME_PARTICIPANT`
- `SECURITY_OBSERVER`

### Meaning for Console

- `ENFORCER`
  - activation-relevant engine/runtime enforcer
  - the first-slice activation model is centered here
- `AUTHORITATIVE_POLICY_PUBLISHER`
  - allowed role for trusted policy mutation through the public client surface
- `PROTECTED_RUNTIME_PARTICIPANT`
  - publishes/consumes policy-governed runtime traffic and must self-gate locally
- `SECURITY_OBSERVER`
  - consumes security observability for diagnostics/UI, but does not by itself block activation

---

## 5.5 Participant effective state

Current vocabulary:

- `READY`
- `NOT_READY`
- `MISMATCH`
- `STALE`

Console should present these as explicit posture states, not as hidden implementation details.

---

## 5.6 Security events

`SecurityEventDTO` provides append-only security/configuration events with:

- `eventType`
- `severity`
- `occurredAtMs`
- namespace / participant identity
- desired and active policy identity fields
- machine-readable `code`
- human-readable `message`
- structured `metadata`

The currently relevant event-type vocabulary includes:

- `POLICY_CHANGE`
- `POLICY_REJECTION`
- `READINESS_MISMATCH`
- `POLICY_DOWNGRADE`
- `TRUST_ANCHOR_PROBLEM`
- `ACTIVATION_TIMEOUT`
- `ACTIVATION_ROLLBACK`
- `CONTROL_PLANE_MUTATION_REJECTED`
- `DATA_PLANE_BLOCKED`

### Example event codes Console should be ready to surface

- `READINESS_MISMATCH`
- `TRUST_ANCHOR_MISSING`
- `BREAK_GLASS_DOWNGRADE`
- `BREAK_GLASS_DOWNGRADE_REJECTED`

Do not hardcode a tiny allow-list in the UI. Preserve room for additional codes.

---

## 5.7 `ObservedPolicySnapshot` and `SecurityPostureSnapshot`

The public client already assembles Console-grade posture views from public topics only.

### `ObservedPolicySnapshot`

This provides:

- `currentPolicy`
- `authoritativePolicy`
- helper methods for effective mode / version / hash

### `SecurityPostureSnapshot`

This provides a combined view of:

- observed policy
- effective mode / version / hash
- participant statuses
- flattened mismatch reasons
- recent security events

### Important Console nuance

`mismatchReasons()` is flattened from the current participant-status snapshot.

That means:

- if participant mismatch statuses have not arrived yet, mismatch reasons may still be empty
- once participant mismatch statuses are visible, mismatch reasons become visible too

Console should therefore treat posture as eventually consistent and avoid assuming that an empty
mismatch list is the only valid intermediate state during convergence.

---

## 6. Public client usage model for Console

## 6.1 Policy mutation

Console should use the supported client surface for authoritative mutation:

- `client.security().publishNamespaceSecurityPolicy(policy)`
- `client.security().clearNamespaceSecurityPolicy()`

### Important rule

Do **not** replace these semantics with bespoke direct Kafka publishing for the same authoritative
policy path.

The supported writer path already enforces the authoritative control-plane signing requirements.

---

## 6.2 Observability

Console should use the public observability facet for policy/status/event reads:

- `client.observability().getObservedPolicySnapshot()`
- `client.observability().getParticipantStatusSnapshot()`
- `client.observability().getRecentSecurityEvents()`
- `client.observability().getPostureSnapshot()`

For integration tests and startup convergence waits, use the `await...` helpers instead of fixed
sleeps.

---

## 6.3 Protected runtime behavior

Console-integrated clients that also perform protected runtime operations should use the public
runtime/worker facets and expect policy-based fail-closed gating.

Important behavior already present upstream:

- when policy requires JWT, missing JWT fails closed
- when policy requires signatures, missing required signature fails closed
- when policy requires anchored trust, missing trust anchor fails closed
- protected runtime traffic is not considered allowed merely because a policy is requested; the
  authoritative active posture still matters

---

## 7. Console implementation guidance

## 7.1 Backend / Platform Service

Console backend should implement the following core responsibilities.

### A. Replace legacy booleans as the authority model

Current legacy namespace flags are useful migration inputs, but they should no longer be the long-term
source of truth for secured namespace posture.

Move toward a backend-owned namespace policy object that can produce `NamespaceSecurityPolicyDTO`.

### B. Own activation lifecycle explicitly

Platform Service should own the policy lifecycle as activation authority:

- submit requested policy
- track `REQUESTED` / `VALIDATING` / `ACTIVE`
- preserve previous active posture until a stricter new policy is truly active
- handle activation timeout / rollback / rejection semantics visibly

### C. Publish authoritative policy through supported client APIs

Use the supported authoritative writer path with explicit signing identity.

### D. Consume policy/status/event observability

Backend should be able to:

- show desired vs active posture
- identify blocking participants
- display mismatch reasons and security events
- distinguish transient convergence from hard rejection

### E. Preserve break-glass metadata

If Console exposes privileged downgrade flows, require and preserve:

- explicit actor
- explicit reason
- event/audit visibility

---

## 7.2 Frontend / Ops UX

The frontend should move from raw flag editing to posture-centric security UX.

### Recommended UI model

For each namespace, display:

- desired mode
- active mode
- activation state
- desired version/hash
- active version/hash
- participant readiness summary
- blocking mismatches
- recent security events
- whether protected data plane is currently blocked for specific reasons

### UX behaviors to prioritize

- make `REQUESTED` vs `ACTIVE` visually obvious
- show why activation is blocked, not just that it is blocked
- show participant kind / component type / capability context in mismatch views
- show trust-anchor problems as fail-closed posture, not as a silent validation quirk
- avoid implying that DLQ is the mechanism for control-plane mismatch diagnosis

### UX anti-patterns to avoid

- “secured” indicator based only on desired mode
- hiding desired/active divergence
- hiding participant mismatch details behind generic failure banners
- assuming policy clear means “error”; policy tombstone/clear is a valid lifecycle operation

---

## 7.3 Suggested Console-side domain model

Console should strongly consider maintaining a UI/domain aggregate roughly shaped as:

- `requestedPolicy`
- `authoritativePolicy`
- `effectiveMode`
- `activationState`
- `participantStatuses`
- `flattenedMismatchReasons`
- `recentSecurityEvents`
- `lastObservedAt`

This mirrors the posture shape already available through the public client and reduces transformation
noise between backend/API/UI layers.

---

## 8. Migration guidance from legacy flags

## 8.1 Migration principle

Treat legacy namespace booleans as a **bridge**, not as the final control-plane contract.

The migration goal is:

- legacy flags remain operational during transition
- explicit `ACTIVE` namespace policy overrides legacy behavior
- absence of an active explicit namespace policy preserves the lightweight/default open behavior

---

## 8.2 Practical migration rules

### If no active namespace policy exists

Console should expect default/open behavior to remain in effect.

### If a secured namespace policy becomes active

Console should expect policy-required enforcement to come from the active policy itself, even if legacy
flags are absent or do not fully describe the same posture.

### If anchored security is requested

Console must ensure trust-anchor distribution is part of rollout planning.

Missing platform trust anchor is not a soft warning; it is a fail-closed protected-runtime condition.

---

## 8.3 Compatibility caveat for rollout

Console should assume there can be temporary periods where:

- desired policy exists
- active policy is still older or open
- some participants are converged
- others are mismatched or stale

That is normal during convergence and should be visible operationally.

---

## 9. Observability semantics Console must respect

## 9.1 Eventually consistent posture

Public policy/status/event observation is topic-driven and eventually consistent.

Console-side tests and UI logic should therefore tolerate transitional combinations such as:

- policy visible before all participant statuses
- readiness mismatch event visible before participant mismatch details
- participant mismatch details visible before all expected recent events are consumed

Use polling/await semantics rather than immediate fixed assertions.

---

## 9.2 No control-plane DLQ implication

A control-plane mismatch or fail-closed posture does **not** imply a DLQ entry.

This is especially important for anchored trust-anchor mismatch scenarios.

Console should represent these as:

- participant mismatch / not-ready posture
- `READINESS_MISMATCH` and/or `DATA_PLANE_BLOCKED` events
- blocked protected runtime operations

not as “check the DLQ.”

---

## 9.3 Status expiry matters

Participant status has TTL/incarnation semantics.

Console should not treat a stale or expired participant snapshot as a permanently blocking truth.

Where possible, surface:

- last seen time
- expiry time
- whether the participant is effectively stale

---

## 10. Recommended Console implementation sequence

1. **Backend policy model**
   - define the Console-side namespace policy aggregate
   - map legacy booleans into explicit policy generation where needed
2. **Authoritative writer integration**
   - configure trusted signing identity
   - publish/clear policy through `TaktXClient.security()`
3. **Observability integration**
   - consume posture from `TaktXClient.observability()`
   - store desired/active divergence and participant/event detail
4. **Frontend posture UX**
   - replace raw toggle-centric UI with posture-centric views
5. **Integration tests**
   - validate publication, reflection, mismatch visibility, fail-closed behavior, and namespace isolation
6. **Release adoption hardening**
   - pin exact released upstream artifacts once publication is confirmed

---

## 11. Recommended Console validation matrix

At minimum, Console should validate the following scenarios against the public client contract.

### A. Open/default behavior

- no active namespace policy => effective open posture
- explicit `COMMUNITY_OPEN` publish is reflected publicly
- policy clear/tombstone returns namespace to open/default posture

### B. Community secured behavior

- secured policy publish is visible publicly
- missing required JWT fails closed
- valid JWT succeeds where policy permits it
- required signing behavior is enforced as configured by policy

### C. Anchored secured behavior

- anchored policy without trust anchor becomes visibly mismatched / blocked
- protected runtime fails closed
- mismatch is visible via posture + security events
- control-plane mismatch does not require a DLQ entry

### D. Unauthorized mutation protection

- untrusted/random client cannot mutate authoritative namespace policy
- rejection is visible through the public control-plane signals

### E. Namespace isolation

- policy/status/event observation is namespace-local
- permission or posture in one namespace does not imply the same in another namespace

### F. Desired vs active divergence

- `REQUESTED` / `VALIDATING` state is visible
- previous active posture remains authoritative until activation succeeds
- blocking participants / mismatch reasons are visible while validation is incomplete or rejected

---

## 12. Known limitations / deferred items

## 12.1 Multi-engine public-only verification is still deferred

The main explicit follow-up area is clustered multi-engine public-client validation.

The Console team can still proceed with integration, but should keep this in mind when:

- planning rollout confidence levels
- evaluating cluster-specific convergence bugs
- designing later CI expansion

## 12.2 Release publication is a separate confirmation step

The branch currently builds at `0.8.0-beta`.

If Console is consuming published artifacts rather than a branch build, confirm the exact published
version and pin to that release.

## 12.3 Legacy compatibility still exists during migration

`GlobalConfigurationDTO` remains operational during the migration slice. That is helpful for rollout,
but Console should not keep expanding the legacy flag model once explicit namespace policy is available.

---

## 13. Handoff checklist for Console kickoff

Console can consider the upstream handoff usable when the following are true:

- [ ] backend team has reviewed the policy/activation/observability contract in this document
- [ ] frontend team understands desired-vs-active posture and participant/event UX requirements
- [ ] authoritative writer signing identity requirements are understood
- [ ] namespace policy publication will use the supported `TaktXClient` API surface
- [ ] observability integration will use public policy/status/event topics only
- [ ] control-plane mismatch handling is modeled without DLQ assumptions
- [ ] rollout planning includes trust-anchor distribution for anchored mode
- [ ] artifact/version pinning plan is aligned with actual release publication status

---

## 14. Recommended reading order

1. `docs/console-security-control-plane-handoff.md`
2. `docs/console-namespace-security-migration-notes.md`
3. `docs/SECURITY-POLICY-ENGINE-REQUIREMENTS.md`
4. `docs/SECURITY-POLICY-IMPLEMENTATION-PLAN.md`
5. `CHANGELOG.md` (`[Unreleased]` namespace-security-policy section)

---

## 15. Bottom line

The upstream engine/client/shared contract for the new namespace security control plane is now ready to
hand over to the Console team.

Console should start from the public `TaktXClient` control-plane surface and the explicit
policy/status/event contracts already present in this repository, while treating release publication and
multi-engine validation as the main remaining follow-up areas rather than as blockers to implementation
planning.

