# TaktX Security Simplification Initiative

## Engine + TaktXClient Team Design & Implementation Prompt

### Background

The current security posture architecture has become too complex operationally.

We are simplifying aggressively.

Goal:

- OPEN = fastest possible execution
- ANCHORED = cryptographically verifiable execution

Nothing in between.

---

## New Security Model

```java
enum NamespaceSecurityMode {
    OPEN,
    ANCHORED
}
```

No SECURED mode.
No posture negotiation.
No capability exchange.
No per-message-type security switches.

---

## Core Semantics

### OPEN

- No TaktX message-level signature verification
- No TaktX participant identity enforcement
- No trust registry required
- No enrollment required
- Infrastructure security is the trust boundary
- Must remain standalone and backwards compatible

### ANCHORED

All external runtime ingress must:

- be signed
- originate from an approved participant identity
- originate from a non-revoked participant identity
- originate from an anchored identity

Runtime must fail closed.

---

## Remove

- SECURED mode
- requiredSigning.*
- requiredAuthorization.*
- capability negotiation
- supportedPostures
- posture convergence
- REQUESTED / VALIDATING / ACTIVE posture lifecycle

---

## Runtime Rule

OPEN => accept

ANCHORED => verify

No per-message-type security policies.

---

## Participant Identity Model

Participants own their private keys.

Lifecycle:

1. Load existing key
2. If missing, generate keypair
3. Persist locally
4. Reuse after restart

Identity churn is not acceptable.

---

## Trust Registry

Engine consumes:

- approved identities
- revoked identities
- anchored identities
- namespace scope

Suggested statuses:

- PENDING
- APPROVED
- REVOKED
- ROTATION_PENDING
- ERROR

---

## Engine Behavior

### OPEN

- No signature verification
- No trust registry required

### ANCHORED

Reject:

- missing signature
- invalid signature
- unknown key
- revoked key
- unanchored key

If engine cannot enforce ANCHORED:

- stop protected processing
- report status
- fail readiness if necessary

---

## TaktXClient

OPEN:

- send normally

ANCHORED:

- sign automatically
- fail fast without identity

No per-command signing configuration.

---

## JWT Simplification

JWT is no longer a posture mechanism.

Machine trust:
- participant signatures

JWT may remain for:
- user identity context

---

## Deliverables

### Design

Provide:

- updated security architecture
- updated engine contract
- updated TaktXClient contract
- trust registry design
- identity persistence design
- migration strategy

### Implementation

Implement:

- OPEN / ANCHORED mode model
- removal of SECURED
- removal of granular signing/auth flags
- participant identity abstraction
- trust registry consumption
- automatic client signing
- fail-fast behavior

### Testing

Required tests:

1. Default namespace = OPEN
2. OPEN start succeeds unsigned
3. OPEN completion succeeds unsigned
4. ANCHORED start fails unsigned
5. ANCHORED completion fails unsigned
6. ANCHORED start succeeds with approved identity
7. ANCHORED completion succeeds with approved identity
8. Unknown key rejected
9. Revoked key rejected
10. Invalid signature rejected
11. Identity survives restart
12. Identity rotation detected
13. Multiple engines enforce consistently
14. Security rejection does not create DLQ entries
15. Namespace isolation works
16. TaktXClient signs automatically
17. TaktXClient fails fast without identity

### Final Deliverable

Provide:

1. Proposed architecture changes
2. Implementation plan
3. Required breaking changes
4. Migration plan
5. Test plan
6. Risks and trade-offs
7. Areas that can be simplified further
