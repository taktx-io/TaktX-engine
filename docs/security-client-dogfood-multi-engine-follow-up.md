# Multi-Engine Public Client Dogfood Follow-Up

**Status:** Planned future follow-up  
**Date:** 2026-05-25  
**Related epic:** `docs/security-client-dogfood-refactor-plan.md`

## 1. Purpose

This document captures the deferred test requirements for validating **multi-engine consistency**
after the client dogfood refactor epic.

The refactor epic is considered complete with the focused single-engine public-client-only suite now
green on branch. The remaining multi-engine scenario is intentionally split out because it requires
additional clustered-engine bootstrap infrastructure beyond the scope of the focused dogfood suite.

## 2. Why this is a separate follow-up

The completed dogfood suite proves the public `TaktXClient` can:

- publish namespace security policy
- observe policy, participant status, and security events
- exercise secured/open runtime behavior
- drive worker completion through public APIs only
- assert console-grade observability and namespace isolation

What it does **not** yet prove is clustered convergence across **multiple real engine nodes** sharing
the same tenant/namespace and Kafka topics.

That follow-up requires test infrastructure that is materially different from the current focused
suite, including at least one additional engine process/container with independently observable
participant identity and readiness.

## 3. Required test environment

A future implementation should use:

- real Kafka
- at least two real engine instances bound to the same tenant/namespace
- public `TaktXClient` APIs only for policy publication, observability, runtime commands, and worker
  interactions
- no internal hooks for state assertions in the dogfood scenario itself
- polling/timeouts instead of fixed sleeps
- strong diagnostics on failure, including participant snapshots and security events from the
  affected namespace

### Additional infrastructure requirements

The follow-up test harness must be able to:

1. start two engine instances concurrently against the same Kafka cluster
2. assign each engine a distinct engine signing identity / participant instance identity
3. wait for both engines to publish participant status
4. wait for both engines to converge on the same policy identity
5. collect observability data from public topics only
6. tear down both engines cleanly without leaking broker connections or state directories

## 4. Minimum scenario set

### Scenario A — both enforcers converge to the same active policy

Given:

- two engine instances in the same namespace
- a public client with authoritative policy publisher capability

Verify:

- both engines publish participant statuses visible through public observability APIs
- both engines converge on the requested policy version/hash
- activation becomes active only when the enforcer set is converged
- posture snapshots and participant statuses reflect both engines consistently

### Scenario B — one engine lags or mismatches during activation

Given:

- two engine instances where one engine is delayed, stale, or intentionally mismatched

Verify:

- policy activation remains validating or fails closed according to the existing activation rules
- public observability surfaces the blocking participant(s)
- mismatch / timeout diagnostics are visible through public posture and security-event streams

### Scenario C — post-activation drift is visible for clustered engines

Given:

- two engines have already converged on an active policy
- one engine later drifts, goes stale, or reports a different observed policy hash/version

Verify:

- public security-event streams expose readiness mismatch / drift signals
- posture snapshots reflect which engine is drifting
- the active policy identity is not silently rewritten by the drifting node

### Scenario D — public runtime still works after clustered convergence

Given:

- both engines converge on a secured namespace policy
- a protected runtime client and worker use only public `TaktXClient` APIs

Verify:

- secured start / completion behavior still works end to end
- public observability remains consistent while more than one engine is active

## 5. Public-only assertion rules

The follow-up suite must continue to enforce the same dogfood rules as the focused epic:

- use public `TaktXClient` entry points only:
  - `security()`
  - `observability()`
  - `runtime()`
  - `workers()`
- do not inspect private CDI beans, internal stores, or implementation-specific state as test
  assertions
- prefer public participant status snapshots, posture snapshots, and security events for readiness /
  mismatch diagnosis

## 6. Diagnostics required on failure

When a multi-engine scenario fails, the test should capture enough data to explain *which* engine did
not converge and *how* it diverged.

At minimum, emit or persist:

- observed policy snapshot
- posture snapshot
- participant status snapshot keyed by participant instance id
- recent security events
- engine participant ids / component types / instance ids seen through the public topics
- relevant policy version/hash values under test

## 7. Suggested implementation options

Any of the following would be acceptable if they preserve the public-only assertion contract:

1. **Two real engine containers** started under Testcontainers against a shared Kafka broker
2. **Two real Quarkus engine processes** launched as separate JVMs against a shared Kafka broker
3. **A dedicated clustered security integration harness** in a separate source set if startup
   isolation becomes too complex for the current `securityIntegrationTest` layout

## 8. Recommended acceptance gate

This follow-up is done when all of the following are true:

- a multi-engine test starts at least two real engines concurrently
- public observability can distinguish both engine participants
- the suite proves policy convergence or fail-closed behavior using public client APIs only
- the suite is stable enough to run in CI without fixed sleeps
- failures produce actionable public-topic diagnostics

## 9. Non-goals for this follow-up

This follow-up does **not** need to reopen the completed client dogfood refactor scope around:

- client facet design
- wrapper descriptor migration
- public observability API shape
- single-engine secured/open behavior

Those were completed and verified by the finished epic.

