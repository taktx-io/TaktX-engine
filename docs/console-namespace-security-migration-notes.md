# Console namespace-security migration notes

**Date:** 2026-06-04  
**Status:** Clean break — start fresh, no backward compatibility required

---

## This is a hard break. Start clean.

The security architecture was completely rewritten. If your Console code references any of the
following, **delete it** rather than trying to adapt it:

```
SecurityMode.SECURED
SecurityMode.ANCHORED_SECURED
SecurityMode.MISCONFIGURED_SECURITY
NamespaceSecurityPolicyDTO.requiredSigning
NamespaceSecurityPolicyDTO.requiredAuthorization
NamespaceSecurityPolicyDTO.activationState
SecurityActivationState (REQUESTED / VALIDATING / ACTIVE)
legacyGlobalSecurityConfigToNamespaceSecurityPolicy()
supportedPostures
posture negotiation / posture convergence
INTEGRITY_PROTECTION_REQUIRED_IN_SECURED_MODES (renamed → _IN_ANCHORED_MODE)
```

There are no shim methods. The old mode names, the activation lifecycle, the per-message-type
signing flags, and the capability-negotiation surface do not exist.

---

## New model

```
OPEN     = accept unsigned traffic, trust infrastructure
ANCHORED = reject anything without a valid countersigned Ed25519 signature
```

One field. Two values. No lifecycle.

---

## Minimum Console implementation

```java
// Publish OPEN policy
TaktXClient.publishNamespaceSecurityPolicy(
    writerProps,
    NamespaceSecurityPolicyDTO.builder()
        .mode(SecurityMode.OPEN)
        .policyVersion(version)
        .build());

// Publish ANCHORED policy
TaktXClient.publishNamespaceSecurityPolicy(
    writerProps,
    NamespaceSecurityPolicyDTO.builder()
        .mode(SecurityMode.ANCHORED)
        .policyVersion(version)
        .build());

// Clear (return to OPEN behaviour, removes policy record)
TaktXClient.clearNamespaceSecurityPolicy(writerProps);
```

`policyVersion` is a monotonically increasing `long`. `policyHash` is computed automatically.

---

## Key change checklist

- [ ] Replace any `SECURED` / `ANCHORED_SECURED` UI with a binary OPEN / ANCHORED toggle
- [ ] Remove activation state machine (REQUESTED / VALIDATING / ACTIVE) — policy is immediate
- [ ] Remove `requiredSigning.*` and `requiredAuthorization.*` field editing
- [ ] Remove capability negotiation / posture convergence polling
- [ ] Implement trust registry management (countersign + publish worker keys for ANCHORED)
- [ ] Surface engine readiness codes (`TRUST_ANCHOR_MISSING` etc.) in the operator UI
- [ ] Surface `taktx-security-events` events in the operator UI

---

## For everything else

See `docs/console-security-control-plane-handoff.md` — the complete implementation reference.
