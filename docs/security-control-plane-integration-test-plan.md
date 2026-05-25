# Security Control-Plane Integration Test Plan

**Status:** In progress — wave 1 implementation started  
**Date:** 2026-05-25  
**Audience:** Engine, client, and upcoming Console implementers  
**Primary goal:** build enough confidence that upcoming Console features can safely rely on the
namespace security control plane through the official `TaktXClient` contract.

**Related docs:**
- `docs/SECURITY-POLICY-ENGINE-REQUIREMENTS.md`
- `docs/console-namespace-security-migration-notes.md`
- `docs/security-client-dogfood-refactor-plan.md`
- `docs/security-client-dogfood-multi-engine-follow-up.md`

---

## Progress tracker

### Current implementation wave — 2026-05-25

- [x] keep the focused public-client dogfood seed green
- [x] add explicit `COMMUNITY_OPEN` policy publish / reflect / clear coverage in
  the split public-client dogfood classes
- [x] add explicit public-client signing-required **happy-path** coverage showing a signed client can
  start runtime without JWT when policy requires client-command signing
- [x] add anchored-mode mismatch / fail-closed public assertions
- [x] add secured worker negative-path coverage through public APIs only
- [x] add explicit `COMMUNITY_OPEN` worker success coverage through public APIs only
- [x] add anchored trust-mismatch participant-status assertions by aligning the fixed engine clock
  near wall-clock time before awaiting public participant-status republish
- [x] add richer recent-event posture assertions for anchored trust mismatch
- [x] extract shared dogfood support and split the monolithic dogfood class into
  scenario-oriented test classes
- [x] add same-logical-actor cross-namespace runtime isolation coverage through public client
  instances bound to different namespaces
- [x] add explicit assertion that control-plane mismatch visibility does not require a DLQ entry for
  the anchored trust-mismatch path

### Newly discovered constraint during wave 1

- [x] documented that an explicit **unsigned** public-client negative-path start scenario is not yet
  stable in the current in-JVM dogfood harness because client command signing is still influenced by
  global `SigningServiceHolder` registration once signing is active in-process
- [ ] follow up with either a public-client-safe way to disable command signing per client instance or
  a test harness shape that isolates signed vs unsigned clients across JVM/process boundaries

---

## 1. Why this plan exists

Before expanding Console features around namespace security policy, we want confidence in the
public control-plane contract, not just confidence in internal engine mechanics.

That means proving, through focused integration tests, that a real caller using the official
`TaktXClient` can safely:

- publish authoritative namespace security policy
- observe effective policy identity, participant status, mismatch reasons, and security events
- drive protected runtime operations affected by that policy
- distinguish namespace-local behavior from cross-namespace noise
- eventually rely on the same observable behavior across more than one engine node

This plan intentionally separates:

1. **public-client dogfood tests** — confidence for Console-style control-plane features
2. **low-level security mechanics tests** — confidence in engine internals, signing, replay,
   dedup, and protocol behavior

Both matter, but they answer different questions.

---

## 2. Current branch reality

The current branch is already in a much better place than the original starting point.

### 2.1 What is already green

The following are already implemented and passing on branch:

- focused public-client-only dogfood suite:
  - `taktx-engine/src/securityIntegrationTest/java/io/taktx/engine/pi/integration/PublicClientDogfoodIntegrationTestSupport.java`
  - `taktx-engine/src/securityIntegrationTest/java/io/taktx/engine/pi/integration/PublicClientOpenModeDogfoodIntegrationTest.java`
  - `taktx-engine/src/securityIntegrationTest/java/io/taktx/engine/pi/integration/PublicClientSecuredModeDogfoodIntegrationTest.java`
  - `taktx-engine/src/securityIntegrationTest/java/io/taktx/engine/pi/integration/PublicClientObservabilityDogfoodIntegrationTest.java`
- broader engine security integration suite:
  - `:taktx-engine:securityIntegrationTest`

### 2.2 Public client capabilities now available

The client refactor provides the public surface needed for Console-style tests:

- `security()`
- `observability()`
- `runtime()`
- `workers()`
- `dlq()`

It also provides the participant model required to represent Console-like callers without legacy
product-role coupling:

- `ParticipantKind`
- `ParticipantCapability`
- `componentType`
- `SecurityParticipantDescriptor`

### 2.3 What the focused dogfood suite already proves

The split public-client dogfood classes already prove the public client can, in a focused
single-engine setup:

- operate with default open behavior when no policy is present
- publish and observe secured policy
- publish and publicly observe anchored trust-mismatch / fail-closed posture
- reject unauthorized authoritative policy mutation
- reject unauthorized start under secured policy
- accept authorized runtime under secured policy
- drive worker completion through public client APIs
- observe policy and recent security events through public topics
- prove namespace-scoped observability behavior
- prove same-logical-actor cross-namespace runtime behavior where open-namespace start acceptance
  does not imply equivalent permission in a secured namespace

### 2.4 What it does not yet prove

The focused dogfood suite does **not** yet prove all of the originally requested matrix:

- a full public-client command-signing matrix for secured start behavior, especially an explicit
  unsigned negative path in a harness that does not inherit in-process signing state
- broader posture assertions beyond the currently covered anchored mismatch / DLQ-non-involvement
  path
- multi-engine consistency through public-client-only assertions

---

## 3. Testing strategy: two suites, two purposes

### 3.1 Suite A — public-client control-plane dogfood

**Purpose:** give enough confidence that Console and similar clients can depend on the public
security control-plane contract.

**Rules:**

- use `TaktXClient` only
- use public facets only
- no internal CDI/store assertions
- prefer client-visible outcomes over internal implementation details
- collect diagnostics from public control-plane surfaces on failure

This suite is the primary confidence gate for upcoming Console security features.

### 3.2 Suite B — low-level security mechanics

**Purpose:** keep validating signing, JWT behavior, replay protection, dedup, restoration, and
other protocol/internal mechanics that are still important but are not themselves Console-level
proof.

This suite should continue to include classes like:

- `SecurityIntegrationTest`
- `ReplayProtectionRestorationIntegrationTest`
- `PhaseOneDedupIntegrationTest`

These tests remain valuable, but they should not be mistaken for proof that the public control-plane
contract is ergonomic or sufficient for Console.

---

## 4. Requirements vs current coverage

The original integration-test prompt is still the right target, but the current codebase supports it
better than before. The table below captures the practical status.

| Requirement area | Current status | Notes |
|---|---|---|
| Default open behavior through public client | Covered | Default open and explicit `COMMUNITY_OPEN` publication / clear are covered in `PublicClientOpenModeDogfoodIntegrationTest` |
| Policy publication and reflection | Partially covered | `COMMUNITY_OPEN`, `COMMUNITY_SECURED`, and anchored mismatch visibility are covered; anchored happy-path / trust-anchor-distributed coverage still missing |
| Command enforcement | Partially covered | JWT negative/positive path covered; signing-required happy path covered; explicit unsigned negative path remains blocked by current in-process signing registration behavior |
| Worker behavior via public client | Covered | explicit `COMMUNITY_OPEN` worker success, secured happy path, and secured unsigned-worker negative path are covered through `PublicClientOpenModeDogfoodIntegrationTest` and `PublicClientSecuredModeDogfoodIntegrationTest` |
| Unauthorized/random client behavior | Mostly covered | rogue policy mutation and namespace-scoped observability covered; runtime bypass matrix can be tightened |
| Console-grade observability | Partially covered | public posture APIs exist; recent-event visibility and anchored participant-status mismatch assertions are covered; broader participant/mismatch/DLQ assertions still needed |
| Namespace isolation | Covered | `PublicClientObservabilityDogfoodIntegrationTest` now covers both observability isolation and same-logical-actor cross-namespace runtime behavior, proving commands allowed in the open namespace do not imply permission in the secured namespace |
| Multi-engine consistency | Deferred | tracked in `docs/security-client-dogfood-multi-engine-follow-up.md` |

---

## 5. Proposed class structure

This section turns the earlier high-level dogfood concept into a concrete, maintainable class
layout.

## 5.1 Current split dogfood layout

The focused public-client seed has now been split into:

- `taktx-engine/src/securityIntegrationTest/java/io/taktx/engine/pi/integration/PublicClientDogfoodIntegrationTestSupport.java`
- `taktx-engine/src/securityIntegrationTest/java/io/taktx/engine/pi/integration/PublicClientOpenModeDogfoodIntegrationTest.java`
- `taktx-engine/src/securityIntegrationTest/java/io/taktx/engine/pi/integration/PublicClientSecuredModeDogfoodIntegrationTest.java`
- `taktx-engine/src/securityIntegrationTest/java/io/taktx/engine/pi/integration/PublicClientObservabilityDogfoodIntegrationTest.java`

This keeps the public-client slice focused while making the scenario groups easier to extend.

## 5.2 Recommended public-client class breakdown

### `PublicClientOpenModeDogfoodIntegrationTest`

**Purpose:** prove that policy lifecycle behavior is correctly visible and driveable through public
client APIs.

**Target scenarios:**

- no explicit policy => default open behavior
- explicit `COMMUNITY_OPEN` policy publish + reflection
- authoritative policy clear / tombstone returns namespace to open/default posture
- open-mode worker completion through public client APIs

### `PublicClientSecuredModeDogfoodIntegrationTest`

**Purpose:** prove start-command enforcement from the public client point of view.

**Target scenarios:**

- secured mode rejects missing JWT when JWT is required
- secured mode accepts valid JWT
- secured mode accepts correctly signed start when signing is required
- secured worker completion succeeds with valid auth/signing
- secured unsigned worker remains blocked from consuming protected work

### `PublicClientObservabilityDogfoodIntegrationTest`

**Purpose:** prove the data Console needs is actually available from public control-plane topics.

**Target scenarios:**

- posture snapshot exposes effective mode, version, and hash
- participant-status snapshots expose kind, capabilities, component type, and readiness/mismatch state
- security events expose rejection / mismatch / policy lifecycle events
- mismatched security state is visible publicly
- mismatch visibility does not masquerade as a DLQ-only concern
- control-plane observability remains available during convergence and recovery
- same-logical-actor cross-namespace observation/runtime isolation is currently covered here until a
  dedicated namespace-isolation class becomes worthwhile

### `PublicClientNamespaceIsolationIntegrationTest`

**Purpose:** prove namespace-local control-plane behavior is isolated for both observation and
runtime action.

**Target scenarios:**

- namespace A secured, namespace B open
- same logical test actor drives both namespaces through public client instances
- policy, posture, and event observations remain namespace-scoped
- commands allowed in one namespace do not imply permission in the other

### `PublicClientMultiEngineIntegrationTest`

**Purpose:** prove clustered-engine consistency through public-client-only assertions.

**Status:** deferred follow-up, not part of the current single-engine confidence gate.

**Reference:** `docs/security-client-dogfood-multi-engine-follow-up.md`

---

## 6. Reusable test fixtures to build next

To keep the suite small and stable, shared support code should do most of the setup work.

## 6.1 `PublicClientSecurityIntegrationSupport`

Recommended responsibilities:

- resolve Kafka bootstrap configuration for the security test profile
- allocate unique tenant/namespace names where practical
- publish trusted control-plane signing keys
- wait for control-plane/signing-key readiness before policy assertions
- build standard participant descriptors for publisher, observer, runtime, worker, and mixed clients
- create started `TaktXClient` instances with automatic cleanup
- publish policy and await observed public posture
- collect public diagnostics bundle on assertion failure

## 6.2 `PublicClientNamespaceFactory`

Recommended responsibilities:

- generate unique namespace names per test or per test class
- provide conventional namespace pairs such as `secured` / `open` / `isolated`
- keep topic names deterministic enough for diagnostics while still avoiding cross-test leakage

## 6.3 `PublicClientPolicyFixtures`

Recommended responsibilities:

- build `COMMUNITY_OPEN` policy payloads
- build `COMMUNITY_SECURED` policy payloads
- build anchored policy payloads
- vary required JWT/signing/trust-anchor fields in a compact way

## 6.4 `PublicClientObservabilityAssertions`

Recommended responsibilities:

- await no policy
- await effective policy version/hash/mode
- await participant mismatch visibility
- await specific security events
- assert posture snapshot fields needed by Console

## 6.5 `PublicClientRuntimeFixtures`

Recommended responsibilities:

- deploy small BPMN models for open and secured paths
- register process-instance update consumers
- start process and await completion/failure from public updates only

## 6.6 `PublicClientWorkerFixtures`

Recommended responsibilities:

- request external-task topic through `workers()` / supported public APIs
- register worker consumers
- await external-task trigger
- complete task with or without explicit auth/signing depending on scenario

## 6.7 `PublicClientSecurityDiagnostics`

Every public-client failure should be able to emit:

- observed policy snapshot
- posture snapshot
- participant-status snapshot
- recent security events
- process instance result/update trace if applicable
- DLQ entries seen, if the scenario expects DLQ inspection

The diagnostics must stay public-client-based rather than reaching into internal beans/stores.

---

## 7. Prioritized backlog

## 7.1 Must-have before expanding Console security-control-plane features

These are the items most directly tied to confidence for upcoming Console work.

1. Keep the current focused public-client dogfood suite green
2. ✅ Add explicit `COMMUNITY_OPEN` publish/reflect scenario
3. ✅ Add anchored-without-anchor mismatch scenario with public posture assertions
4. ◐ Add explicit public-client signing enforcement scenario for secured starts
5. ✅ Add explicit secured worker negative-path scenario
6. ◐ Add explicit posture assertions for participant statuses, mismatch reasons, and recent events
7. ✅ Add explicit assertion that control-plane mismatch visibility does not require or imply a DLQ entry
8. Introduce cleaner unique namespace support for dogfood scenarios

## 7.2 Should-have before secured production rollout

1. Multi-engine public-client consistency suite
2. Distinct engine-node participant-status assertions visible publicly
3. Repeated start/completion consistency under multiple engines
4. Anchored-mode happy-path scenario with real trust-anchor distribution
5. Failure diagnostics that clearly identify which engine node lagged or diverged

## 7.3 Regression hardening / follow-on quality

1. ✅ Split the current monolithic dogfood class into clearer scenario-oriented classes
2. ✅ Add same-logical-actor cross-namespace scenarios
3. Add restart/re-observation stability cases for public posture consumers
4. Add more test-friendly public diagnostics helpers where needed
5. Regularly run the public dogfood suite together with the broader `securityIntegrationTest` task

---

## 8. Public-client API friction points discovered so far

These are not blockers, but they are the main sources of test awkwardness that are worth recording.

1. No first-class public helper for waiting until control-plane readiness has caught up after trusted
   signing-key publication
2. No single public diagnostics snapshot/export helper that bundles policy, posture,
   participant-status, and event context in one call
3. Worker readiness still benefits from explicit topic-existence handling and helper plumbing
4. DLQ public APIs are usable, but currently less assertion-friendly than `SecurityObservabilityClient`
5. Empty/default posture assertions (`no policy`, `no recent events`, `no participant mismatch`) are
   still more manual than ideal in integration tests
6. Explicit unsigned public-client command tests are currently awkward in the same JVM because
   command signing registration is process-global once a signed client/engine activates it
7. In the current `securityIntegrationTest` harness, engine participant statuses are timestamped by a
   fixed engine test clock while public clients evaluate staleness with real wall-clock time; that
   makes participant-status snapshots appear expired by default even when recent security events
   correctly expose the same mismatch. The dogfood suite can mitigate this by aligning the shared
   fixed clock near wall-clock time and awaiting the next participant-status republish, but that is
   still more awkward than a first-class public/client test helper
8. Client-local protected-runtime mismatches for unsigned secured workers are currently most stable as
   public runtime/worker API behavior (`cannot consume protected work`, `process remains active`) rather
   than as observer-visible security events, because this dogfood path does not presently emit a
   client-originated `DATA_PLANE_BLOCKED` event through the public observability stream

These should be tracked during test writing rather than solved prematurely, but any recurring pain
should become a small client-API follow-up rather than duplicated helper logic in every test class.

---

## 9. Product behaviors that the next tests should pin down

The next round of public-client tests should explicitly resolve the following semantics.

### 9.1 Start-command security matrix

For secured runtime, the test suite should make it unambiguous whether a command is accepted because
of:

- JWT only
- signature only
- both JWT and signature
- local client readiness under the active policy

### 9.2 Anchored misconfiguration visibility

The suite should pin down the exact public contract for anchored misconfiguration:

- effective mode
- activation state
- mismatch reasons
- recent security-event visibility
- runtime fail-closed behavior

**Current observed branch contract:** the first public-client dogfood scenario now pins down that an
authoritative anchored policy without an available trust anchor is still observed publicly as
`ANCHORED_SECURED`, while recent security events expose `READINESS_MISMATCH` and
`DATA_PLANE_BLOCKED` / `TRUST_ANCHOR_MISSING`, and protected runtime remains fail-closed without
implying a DLQ entry. Engine participant-status mismatch snapshots can also be asserted publicly in
this harness when the shared fixed engine clock is aligned near wall-clock time before awaiting the
next participant-status republish; without that alignment, status snapshots still appear stale and
Console-facing assertions should lean on public policy + event visibility.

### 9.3 Mismatch vs DLQ semantics

Console needs to know whether an issue is a control-plane posture problem or a replay/rejection DLQ
problem. The public-client suite should therefore make the distinction visible and reliable.

### 9.3 Worker negative-path semantics

The suite should also pin down what a protected worker failure looks like through public client APIs.

**Current observed branch contract:** under active `COMMUNITY_SECURED` policy requiring signed worker
responses / authorized external-task completion, a client started with signing explicitly disabled is
blocked before it can consume the external-task trigger through the public worker facet, and the
process remains active/incomplete. This path is currently asserted most reliably through public policy
observation plus worker/runtime behavior rather than observer-visible mismatch events.

### 9.4 Namespace isolation semantics for mixed client populations

The suite should explicitly prove that a client population observing one namespace cannot accidentally
infer or drive policy state in another namespace.

---

## 10. Recommended execution order

To maximize confidence quickly without overbuilding the suite, use this order.

1. keep the current focused dogfood suite as the stable seed
2. add explicit `COMMUNITY_OPEN` and anchored-mismatch policy lifecycle scenarios
3. add explicit command-signing enforcement scenarios
4. add explicit worker negative-path scenarios
5. strengthen observability/posture assertions for Console-facing fields
6. tighten namespace-isolation runtime coverage
7. implement multi-engine follow-up separately

This order is deliberate: it prioritizes the features most likely to unblock and de-risk upcoming
Console security-control-plane work before clustered-engine infrastructure is added.

---

## 11. Practical validation gate

The following should be treated as the working confidence gate for Console-facing control-plane work.

### Minimum branch gate

- `:taktx-engine:securityIntegrationTest --tests io.taktx.engine.pi.integration.PublicClientOpenModeDogfoodIntegrationTest --tests io.taktx.engine.pi.integration.PublicClientSecuredModeDogfoodIntegrationTest --tests io.taktx.engine.pi.integration.PublicClientObservabilityDogfoodIntegrationTest`
- `:taktx-engine:securityIntegrationTest`

### Preferred gate after the next expansion wave

- targeted public-client dogfood classes
- full `:taktx-engine:securityIntegrationTest`
- relevant `:taktx-client:test` coverage when client helpers or public APIs change

The public-client classes are the main confidence signal for Console control-plane features; the full
security integration task remains the broad hardening signal for engine behavior as a whole.

---

## 12. Recommended next implementation step

The next engineering session should **not** start by designing a brand-new test harness from
scratch.

Instead, it should:

1. keep the split public-client dogfood classes green as the proven seed
2. continue extracting assertion/diagnostics helpers from the shared support base when duplication appears
 3. add the highest-value missing single-engine public-client scenarios first
 4. leave the already-documented multi-engine coverage in its separate follow-up track

That gives the shortest path to the actual reason this work exists: enough confidence to safely and
reliably build the Console security control-plane features on top of the official public client
contract.

