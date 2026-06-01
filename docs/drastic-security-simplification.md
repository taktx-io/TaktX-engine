# ADR-001: Simplified TaktX Trust Architecture

**Status:** Accepted
**Date:** 2026-06-01
**Authors:** TaktX Core Team

---

# Context

TaktX originally evolved toward a highly flexible security posture model.

The design included:

* OPEN
* SECURED
* ANCHORED_SECURED

Additional concepts included:

* participant capability reporting
* posture negotiation
* desired posture
* active posture
* effective posture
* posture convergence
* per-message-type signing requirements
* per-message-type authorization requirements
* activation workflows

While technically sound, the resulting operational model became increasingly difficult to understand and operate.

A recurring observation was:

> The security architecture had become more complex than the business value it delivered.

The platform's strategic goals are:

1. Brutal performance
2. Verifiable trust
3. Operational simplicity

The previous design optimized for flexibility at the expense of simplicity.

This ADR records the decision to simplify the model.

---

# Decision

TaktX will support only two namespace security modes:

```text
OPEN
ANCHORED
```

The SECURED posture is removed.

---

# Security Philosophy

The platform intentionally offers a trade-off.

## OPEN

OPEN is optimized for maximum performance and minimum operational overhead.

Characteristics:

* No message-level signature verification
* No participant identity verification
* No trust registry
* No enrollment process
* No runtime cryptographic validation

Trust is provided by:

* Kafka ACLs
* SASL
* mTLS
* Network controls
* Infrastructure security

OPEN is intended for:

* development
* testing
* community usage
* trusted internal deployments
* maximum throughput deployments

---

## ANCHORED

ANCHORED is optimized for verifiable trust.

Characteristics:

* All external runtime ingress is signed
* All signatures are verified
* All participants possess approved identities
* Revocation is supported
* Runtime fails closed

Trust is provided by:

* Participant identity
* Signature verification
* Anchored trust material
* Revocation information

ANCHORED is intended for:

* regulated environments
* financial institutions
* critical systems
* compliance-sensitive deployments
* environments requiring cryptographic proof

---

# Why SECURED Was Removed

SECURED represented a middle ground:

```text
OPEN
SECURED
ANCHORED_SECURED
```

In practice it created ambiguity:

Questions repeatedly arose:

* Is a self-generated key sufficient?
* Is a configured key sufficient?
* Is registration sufficient?
* Is approval required?
* Is anchoring required?

The result was a security model that was difficult to explain.

The business value of SECURED did not justify its complexity.

The platform now follows a simpler principle:

```text
OPEN = trust infrastructure

ANCHORED = trust cryptographic proof
```

No intermediate trust level exists.

---

# Runtime Trust Model

A namespace has exactly one active mode.

```java
enum NamespaceSecurityMode {
    OPEN,
    ANCHORED
}
```

The mode is authoritative.

Participants do not negotiate it.

Participants either comply or they do not participate.

---

# Removal of Capability Negotiation

The previous design allowed participants to report:

* supported postures
* capabilities
* readiness

The platform would then attempt to determine whether a posture could become active.

This is removed.

Reason:

The namespace policy already defines the required behavior.

Examples:

Namespace = ANCHORED

Engine cannot verify signatures.

Result:

```text
Engine does not participate.
```

Worker lacks approved identity.

Result:

```text
Worker does not participate.
```

No negotiation is required.

---

# Mode Is Policy

The following principle applies:

```text
Mode is policy.

Identity registry is access control.

Runtime enforcement is truth.

Status is observability.
```

This principle guides the entire design.

---

# External Runtime Ingress

ANCHORED verification applies uniformly.

The platform intentionally avoids per-message-type controls.

The following are treated identically:

* process start
* message correlation
* signal publication
* user task completion
* job completion
* process modification
* future external commands

The rule is:

```text
OPEN => accept

ANCHORED => verify
```

No exceptions.

---

# Participant Identity Ownership

Participants own their private keys.

The platform never owns participant private keys.

Reasons:

* reduced attack surface
* simpler compliance story
* simpler architecture
* industry-standard model

Examples:

* TLS certificates
* SSH keys
* SPIFFE identities

all follow the same principle.

---

# Identity Lifecycle

Participants:

1. Load existing private key
2. Generate keypair if missing
3. Persist key locally
4. Reuse key across restarts

Identity churn is considered undesirable.

Changing identity should be treated as key rotation.

---

# Trust Registry

The trust registry contains:

* approved participant identities
* revoked participant identities
* anchoring information

The trust registry contains public information only.

Private keys never enter the trust registry.

---

# Revocation

Revocation is a first-class capability.

When a participant is revoked:

* future signed messages are rejected
* runtime fails closed
* security events are emitted

Revocation never requires redistribution of private keys.

---

# Engine Responsibilities

The engine remains intentionally simple.

The engine understands:

```text
OPEN
```

or

```text
ANCHORED
```

Nothing more.

The engine does not participate in:

* posture negotiation
* capability exchange
* activation workflows

The engine only enforces policy.

---

# Client Responsibilities

TaktXClient becomes the primary integration surface.

Responsibilities:

* participant identity management
* message signing
* enrollment support
* signature generation

Behavior:

OPEN:

```text
send
```

ANCHORED:

```text
sign
verify eligibility
send
```

The caller should not need to decide which messages are signed.

---

# JWT Simplification

JWT is no longer a security posture mechanism.

Machine trust is provided through signatures.

JWT remains useful for:

* user identity
* business context
* audit trails

Example:

User task completion:

```text
JWT -> who approved

Signature -> which system submitted
```

These concerns remain separate.

---

# Standalone Operation

A critical requirement is preserved.

TaktX must continue to operate without:

* Console
* Platform Service
* Vault

Example:

```text
Engine
Kafka
```

This deployment remains valid.

In this scenario:

```text
Namespace mode = OPEN
```

No additional infrastructure is required.

This preserves TaktX's simplicity and community adoption story.

---

# Future Vault Integration

Vault is considered an implementation detail.

The architecture is designed to support:

```text
LocalPlatformSigner
VaultPlatformSigner
CloudKMSPlatformSigner
```

through a common abstraction.

Vault is not required for the first implementation.

---

# Strategic Outcome

This decision reduces:

* operator complexity
* implementation complexity
* documentation complexity
* testing complexity

while preserving the business value that matters.

The resulting story becomes:

```text
OPEN

Maximum performance.
Trust your infrastructure.


ANCHORED

Verifiable trust.
Trust cryptographic proof.
```

This aligns directly with the long-term positioning of TaktX:

**Brutal Performance. Verifiable Trust.**
