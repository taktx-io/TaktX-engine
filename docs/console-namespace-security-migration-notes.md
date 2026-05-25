# Console namespace-security migration notes

This document captures the migration and compatibility guidance for Console-side adoption of the
engine-repo namespace-security-policy contract.

For the current confidence and validation strategy behind upcoming Console security-control-plane
features, also see `docs/security-control-plane-integration-test-plan.md`.

> Target release for Console refresh: `0.8.0-beta`.
>
> After the local release is published, pin the Console-side dependency refresh to the exact
> `0.8.0-beta` artifacts listed below.

## Exact version to consume

For the namespace-security-policy handoff, Console should refresh to the `0.8.0-beta` line.

### Core artifacts

- `io.taktx:taktx-client:0.8.0-beta`
- `io.taktx:taktx-shared:0.8.0-beta`

### Framework wrappers (only if used by the consuming application)

- `io.taktx:taktx-client-quarkus:0.8.0-beta`
- `io.taktx:taktx-client-spring-boot-3:0.8.0-beta`
- `io.taktx:taktx-client-spring-boot-4:0.8.0-beta`

### Engine compatibility target

- TaktX engine release line: `0.8.0-beta`

## Scope

These notes apply to Console integrations that depend on:

- `NamespaceSecurityPolicyDTO`
- desired-vs-active policy identity handling
- participant-status observability
- security-event observability
- official `TaktXClient` control-plane helpers for authoritative policy publication/clear

## Supported control-plane surface

Console and related runtime integrations should use the supported upstream client surface rather than
publishing bespoke Kafka records for equivalent semantics.

### Preferred APIs

- `TaktXClient.publishNamespaceSecurityPolicy(...)`
- `TaktXClient.clearNamespaceSecurityPolicy()`
- `TaktXClient.requestExternalTaskTopic(...)`

Framework integrations in this repo now route worker-topic requests through the supported
`TaktXClient` method instead of directly wiring a lower-level topic-request publisher.

## Migration notes

### 1. Replace legacy booleans with explicit policy payloads

Console should stop reasoning about namespace security from scattered legacy flags alone and instead
produce/consume explicit `NamespaceSecurityPolicyDTO` payloads.

Important distinctions:

- `desiredPolicyVersion` / `desiredPolicyHash` describe the requested target
- `activePolicyVersion` / `activePolicyHash` describe the authoritative enforced target
- `activationState` determines whether the requested policy is merely pending or actually enforced

Do not treat a requested policy as authoritative until it is `ACTIVE`.

### 2. Treat status and security events as observability, not authority

Participant status and security events help Console diagnose convergence, drift, rejection, and
break-glass actions, but they do **not** establish trust on their own.

Console should:

- display them for diagnosis and audit
- avoid using them as a substitute for authoritative policy identity
- avoid inferring trust solely from signed/verified-looking telemetry

### 3. Authoritative policy writes now require an explicit signing identity

The authoritative namespace-security-policy writer path is no longer an unsigned or fallback writer
path.

Compatibility impact:

- `publishNamespaceSecurityPolicy(...)` requires an explicit configured authoritative signing identity
- `clearNamespaceSecurityPolicy()` uses the same trusted-writer path and signs the tombstone/clear
- arbitrary Kafka writes to the compacted security-policy topic are not a supported mutation path

### 4. Protected runtime work is gated by ACTIVE + READY posture

When an authoritative secured policy is active, engine/client participants do not continue protected
runtime traffic unless they are locally `READY` for the exact active policy identity.

Console should expect:

- pending `REQUESTED` / `VALIDATING` policy does not immediately flip protected runtime enforcement
- protected runtime publication/consumption can be blocked until readiness is achieved
- control-plane observability/status traffic continues during convergence and recovery

### 5. Anchored mode requires trust-anchor distribution

If the namespace policy requires anchored trust, the participating runtime must have the platform
trust anchor configured. Missing trust anchor now fails closed for protected runtime participation.

### 6. JWT/signing requirements are policy-driven

When the authoritative active policy requires JWT authorization and/or signatures, those checks are
applied from the policy contract itself even if older global flags are absent or disabled.

Console-side rollout should therefore assume that policy-required security behavior is enforced by
active policy, not only by legacy runtime toggles.

## Compatibility constraints for rollout

Before Console enables secured policy modes in a namespace, ensure that the deployed runtime/client
population is compatible with the target policy requirements:

- authoritative policy publisher has the required platform signing identity
- clients/workers that must emit protected traffic have the required signing/JWT configuration
- anchored namespaces have the platform trust anchor distributed to required participants
- consumers/operators understand that desired policy and active policy can temporarily diverge during
  validation and rollback

## Validation planning reference

For the broader integration-test strategy that is meant to give enough confidence for upcoming
Console security-control-plane features, use:

- `docs/security-control-plane-integration-test-plan.md`

That plan distinguishes the already-green focused public-client dogfood coverage from the remaining
single-engine and deferred multi-engine follow-up scenarios.

## Remaining release-gate item

This document is intentionally version-agnostic until the actual engine/client release is published.
After publication, update Console dependency pins to the exact released version documented in the
release notes and release-gate checklist.

