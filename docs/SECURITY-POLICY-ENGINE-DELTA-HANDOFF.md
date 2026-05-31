# Namespace Security Policy — Engine + Client Delta Handoff

**Status:** Follow-up delta handoff for upstream engine/client review  
**Date:** 2026-05-29  
**Companion docs:** `docs/SECURITY-POLICY-ENGINE-REQUIREMENTS.md`, `TaktX-engine2/docs/console-security-control-plane-handoff.md`, `docs/SECURITY-POLICY-IMPLEMENTATION-PLAN.md`, `docs/SECURITY-CONTROL-PLANE-DELIVERY-BACKLOG.md`

## 1. Purpose

The original upstream engine/client requirements in `docs/SECURITY-POLICY-ENGINE-REQUIREMENTS.md`
and the current-state upstream handoff in `TaktX-engine2/docs/console-security-control-plane-handoff.md`
remain the primary detailed references.

This document is a **delta handoff** capturing the additional/clarified requirements discovered during
Console-side adoption planning after the original upstream work was completed.

It exists so the engine team does **not** need to re-read or reinterpret the entire original
requirements set just to understand the changes requested from the recent Console-side learning.

## 2. Relationship to the original upstream requirements

This document does **not** replace the original engine/client requirements.

Use the documents as follows:

- `docs/SECURITY-POLICY-ENGINE-REQUIREMENTS.md`
  - full upstream engine/runtime/shared/client requirements
  - original architecture and semantics
- `TaktX-engine2/docs/console-security-control-plane-handoff.md`
  - what is already implemented upstream
  - what Console is supposed to integrate against
- `docs/SECURITY-POLICY-ENGINE-DELTA-HANDOFF.md` *(this document)*
  - changes/clarifications needed after Console-side modeling and UX review
  - narrowed follow-up asks for upstream engine/client maintainers

## 3. Why this delta exists

During Console-side adoption and the first real posture walkthroughs, several issues became clear:

1. The first slice needs a **simpler operator-facing posture model** than the raw upstream internals
   alone provide.
2. Participant-reported status/capabilities are very useful for **preflight feasibility**, but the
   distinction between:
   - supported capability
   - current readiness
   - activation authority
   needs to be sharper.
3. Failed authoritative mutation (for example, trusted-writer unavailable) must be surfaced as a
   **first-class blocker**, not just as a low-level bridge error.
4. Console needs a stable engine/client contract that lets it expose:
   - `requestedPosture`
   - `effectivePosture`
   - `requestStatus`
   - `protectedRuntimeAllowed`
   - `targetModeFeasibility`
   - `blockingIssues`
   without accidentally implying that participant status is itself trust authority.

The goal of this delta is to keep the strong upstream safety semantics while making downstream
Console adoption clearer and less error-prone.

## 4. The slice-1 operator model Console is now targeting

Console is now standardizing on this simplified first-slice posture model:

- `requestedPosture`
- `effectivePosture`
- `requestStatus`
- `protectedRuntimeAllowed`
- `blockingIssues`
- `participantSummary`
- `targetModeFeasibility`

### Important implication for engine/client work

The engine/client contract does **not** need to move lifecycle authority away from Platform Service,
create a new persisted posture authority, or make status authoritative.

However, the engine/client contract **does** need to expose enough stable semantics so Console can
compute or present those fields correctly.

## 5. Delta requirements for engine + client

## 5.1 Separate capability support from readiness explicitly

The upstream contract should make the following distinction unmistakable:

- **capability support**
  - what a participant can support in principle
  - for example: supports `SECURED`, supports `ANCHORED_SECURED`, supports trust-anchor validation,
    supports protected runtime participation
- **current readiness**
  - whether the participant is currently `READY` for the exact **active** canonical policy identity
- **activation authority**
  - still owned by Platform Service / control plane, never by participants

### Required clarification

The contract and DTO semantics should make it easy for downstream code to represent:

- “this participant could support `SECURED` in general”
- “this participant is not currently ready for the active `SECURED` policy identity”
- “that fact does not itself activate anything”

### Expected outcome

Console can safely use participant capability/status for **advisory target-mode feasibility** without
turning capability or readiness into trust authority.

## 5.2 Support advisory target-mode feasibility inputs

Console now intends to show advisory preflight feasibility for target postures:

- `OPEN`
- `SECURED`
- `ANCHORED_SECURED`

To do that safely, upstream engine/client contracts should expose enough structured information for
Console to answer:

- can this namespace likely switch to `SECURED` right now?
- can this namespace likely switch to `ANCHORED_SECURED` right now?
- which required participants/capabilities are currently missing?

### Required contract support

The shared/client surface should support capability/status inputs such as:

- participant supported modes/capabilities
- readiness freshness / TTL / incarnation
- trust-anchor support availability
- protected runtime participation capability
- observed active policy identity
- authoritative writer capability/availability where exposed by the namespace-local bridge path

### Important rule

This feasibility is **advisory only**.

It may block or warn the operator before a change is attempted, but it must **not** itself become:

- activation proof
- runtime trust proof
- participant authority over policy activation

## 5.3 Preserve active-policy-only runtime semantics as a hard rule

This was already intended upstream, but Console adoption makes it important to restate as a hard
acceptance rule:

- requested posture must not switch protected runtime behavior early
- protected runtime behavior must always follow the **effective active posture**
- if there is no active secured posture, runtime remains effectively `OPEN`

### Why this is called out again

Console-side integration found that downstream bridge/ingester layers can accidentally drift into a
model where “observed requested secure policy” starts affecting behavior too early unless the
contract is interpreted very strictly.

### Requested engine/client confirmation

Please confirm that the supported upstream behavior remains:

- exact active identity drives protected runtime behavior
- requested-but-not-active policy remains control-plane / observability state only
- participant readiness is evaluated relative to active identity, not merely requested identity

## 5.4 Provide structured blocker/event semantics for failed authoritative mutation

The first realistic Console scenario exposed that mutation failure must be treated as a first-class
operational posture outcome.

Example:

- namespace starts in `OPEN`
- operator requests `OPEN -> SECURED`
- namespace-local trusted authoritative writer is unavailable
- publish fails
- effective posture must remain `OPEN`
- Console must show a blocker and actionable reason

### Required engine/client support

Upstream should ensure the shared/client/event vocabulary supports clear structured reasons for:

- authoritative mutation rejected
- trusted authoritative publisher unavailable/unconfigured
- trust-anchor requirement missing
- participant capability mismatch
- participant readiness mismatch
- stale/expired required participants

### Important note

Console may still derive `requestStatus` itself, but it needs stable upstream reason/event semantics
so those states are not reconstructed from ambiguous strings.

## 5.5 Keep status non-authoritative even when capability reporting expands

Because Console now wants richer capability reporting for feasibility, the upstream contract should
explicitly preserve the rule that:

- participant status is telemetry / observability
- capability reporting is observability / preflight input
- neither one overrides desired policy or becomes activation authority

This is especially important once richer participant capability data exists, because the richer the
status is, the easier it becomes for downstream code to accidentally treat it as trust proof.

## 5.6 Make the observability surface sufficient for the simplified Console read model

Console does **not** need the engine/client surface to expose a literal prebuilt UI model.

It does need the engine/client surface to expose enough stable information so Platform Service and
frontend can reliably derive:

- requested posture vs effective posture divergence
- active identity convergence
- participant capability support vs current readiness
- participant staleness / expiry
- clear blocker/event reasons
- protected runtime allowed vs blocked state under the active posture

If any part of that is currently underspecified in the shared/client surface, it should be tightened
here.

## 5.7 Cross-team proposal: keep `OPEN` fully unsigned

Recent posture-model review surfaced a possible simplification worth explicit platform-team review:

- `OPEN` should mean **no runtime signing model at all**
- self-generated signing keys should **not** be introduced as part of `OPEN`
- signing identity/public-key publication should begin only once the namespace is operating in or
  preparing for `SECURED` / `ANCHORED_SECURED`

### Why this alternative is being suggested

This appears cleaner from an operator and contract-semantics perspective than a model where keys are
always generated/published but only become enforced in stricter modes.

Potential benefits:

- keeps `OPEN` semantics extremely clear: unsigned operation, no crypto-readiness interpretation
- avoids UI/operator confusion where a published self-generated key in `OPEN` could be misread as a
  trust signal or as evidence of secure readiness
- preserves a sharper distinction between:
  - infrastructure/open operation
  - community secured operation
  - anchored secured operation
- reduces the chance that downstream systems accidentally start treating key presence in `OPEN` as a
  meaningful posture indicator

### Trade-off acknowledged

This may give up some implementation uniformity and some warm-up convenience for `OPEN -> SECURED`
transitions, because participants would not already be publishing keys before secured posture becomes
relevant.

However, from the Console-side review, the semantic clarity may be worth that cost.

### Requested platform-team evaluation

Please evaluate whether the overall namespace-security model should standardize on the following
posture ladder instead:

- `OPEN`
  - no signing
  - no signing-key publication requirement
  - no signing readiness interpretation
- `SECURED`
  - signing and/or authorization may be required by policy
  - community/open trust semantics
- `ANCHORED_SECURED`
  - signing and/or authorization required under anchored trust semantics
  - stable countersigned keys required where applicable

### Review questions for platform + engine alignment

1. Would making `OPEN` explicitly unsigned simplify the engine/client/shared contracts overall, or
   would it create more migration and bootstrap complexity than it removes?
2. Are there current code paths that already assume signing identity exists independently of posture,
   such that this change would be disproportionately expensive?
3. Would this simplification make readiness semantics easier to keep aligned with the active posture,
   especially for Console and ingester/control-plane adopters?
4. If upstream prefers the current/alternative direction instead, what problem is solved by keeping
   signing identity present in `OPEN` that cannot be solved just as well when entering `SECURED`?

### Initial platform-team feedback

Initial platform feedback is directionally supportive of the simplification:

- standardizing `OPEN` as fully unsigned is semantically stronger than the current model
- it would simplify Console/readiness/runtime posture semantics
- it would likely make readiness reporting easier to keep honest because signing identity lifecycle
  would no longer appear partially decoupled from active posture in the steady-state `OPEN` case

However, platform also called out an important implementation constraint:

- current engine startup and client bootstrap behavior still assume signing identity may exist
  independently of active posture
- adopting fully unsigned `OPEN` therefore requires upstream changes rather than being only a UI or
  documentation clarification

### Architectural direction suggested by platform feedback

If platform still wants pre-warmed signing material for `OPEN -> SECURED` transition convenience,
that should likely be modeled as **transition/bootstrap behavior**, not as part of the steady-state
`OPEN` posture contract.

In other words, the likely cleaner split is:

- steady-state `OPEN`
  - unsigned
  - no posture-level implication that signing identity is active or required
- optional transition/bootstrap preparation
  - pre-provision keys if desired for an upcoming secured transition
  - treat that as preparatory/runtime bootstrap state rather than as the meaning of `OPEN`

### Proposed follow-up: separate steady-state posture from transition/bootstrap preparation

The next cross-team design step should be to make an explicit architectural decision on whether
signing identity lifecycle belongs to:

- the **steady-state posture contract**, or
- a separate **transition/bootstrap preparation** model used only when moving toward `SECURED` /
  `ANCHORED_SECURED`

#### Proposed working direction

Use the following split unless a stronger counterargument emerges during upstream review:

- `OPEN` remains a steady-state unsigned posture
- signing identity readiness is not reported as part of normal `OPEN` posture
- if pre-warmed keys are operationally useful, expose that as preparation/bootstrap state rather than
  as evidence that `OPEN` itself has a signing model

#### Concrete design questions for the follow-up

1. Should engine/client startup continue to initialize signing identity unconditionally, or should
   signing bootstrap become posture-aware?
2. If pre-provisioning remains desirable, where should that state live:
   - internal bootstrap/runtime state only
   - participant status metadata
   - a separate transition/preflight surface
3. How should Console distinguish:
   - current active posture semantics
   - advisory readiness for a future secured transition
   without implying that secure runtime behavior is already active?
4. Do any existing public/shared DTOs need additive fields or clearer semantics so bootstrap
   preparation can be surfaced without overloading `OPEN`?

#### Expected outcome of the follow-up

This follow-up should result in one of:

- confirmation that `OPEN` becomes explicitly unsigned and signing bootstrap moves behind a separate
  preparation model, **or**
- an agreed alternative that preserves pre-warmed signing while still preventing readiness and UI
  semantics from implying that `OPEN` itself is a signed posture

Until that follow-up is resolved, downstream consumers should avoid treating signing-key presence in
default/open scenarios as equivalent to active secure posture.

## 6. Non-goals / what this delta is *not* asking for

This delta is **not** asking upstream to:

- move activation authority away from Platform Service
- make participant status authoritative
- introduce a direct Platform-Service-to-Kafka mutation path
- redesign namespace-local control-plane topic names
- make `OPEN` heavier or require trust material by default
- reintroduce legacy raw security booleans as the authority model
- treat target-mode feasibility as a replacement for validation or runtime enforcement

## 7. Concrete upstream review questions

The engine/client maintainers should explicitly answer these questions for this delta:

1. Which existing DTOs/clients already expose participant supported modes/capabilities versus
   current readiness, and where is the separation still too implicit?
2. Does the current public observability surface expose enough structured reason codes for Console to
   render blockers without parsing raw error strings?
3. Is authoritative-writer availability/unavailability already expressible as capability/status/event
   data, or is a small shared contract addition still needed?
4. Is there any place where downstream adopters could reasonably misread requested posture as active
   posture from the current public surface?
5. Are any additional helper APIs or shared enums needed so Console can implement advisory
   `targetModeFeasibility` without duplicating contract logic locally?

## 8. Acceptance criteria for this delta handoff

This delta is satisfied when the engine/client maintainers can point to one of the following for
all items above:

- already implemented behavior/contract that satisfies the requirement, **or**
- a small agreed upstream follow-up change with a clear owner and target path

Concretely, the delta handoff is complete when:

- capability support vs readiness vs activation authority is explicit in the contract
- Console can safely derive advisory target-mode feasibility from upstream capability/status data
- structured blocker/event semantics exist for failed authoritative mutation and related posture
  blockers
- active-policy-only runtime semantics remain explicit and confirmed
- richer capability reporting still preserves the rule that status is observability, not trust

## 9. Canonical scenario to validate this delta

The first scenario that should remain easy to explain after these changes is:

1. engine and ingester start in default `OPEN`
2. operator requests `OPEN -> SECURED`
3. authoritative publish fails because trusted writer capability is unavailable
4. requested posture remains `SECURED`
5. effective posture remains `OPEN`
6. protected runtime remains allowed under `OPEN`
7. Console can show:
   - request blocked/failed
   - trusted-writer blocker reason
   - participant summary
   - no false implication that secure posture is active

If the shared/client semantics are still too ambiguous for Console to render that cleanly, this
delta handoff is not yet satisfied.

