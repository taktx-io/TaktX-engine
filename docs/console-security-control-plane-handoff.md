# Console Security Control-Plane Handoff

**Status:** Ready for implementation  
**Date:** 2026-06-04  
**Supersedes:** all previous security control-plane handoff documents  
**Audience:** Console backend, frontend, and integration engineers

---

## Read this first

The security architecture was completely simplified. **Throw away everything you know about the old
model.** There are no migration shims to preserve. Start fresh from this document.

Old concepts that no longer exist:

| Gone | Replace with |
|------|-------------|
| `SECURED`, `ANCHORED_SECURED` modes | `ANCHORED` |
| `REQUESTED` / `VALIDATING` / `ACTIVE` activation states | None — policy is immediately authoritative |
| desired-vs-active policy identity | Single authoritative policy |
| `requiredSigning.*`, `requiredAuthorization.*` fields | Not needed — mode is the only driver |
| capability negotiation | Not needed |
| posture convergence monitoring | Engine readiness is immediate and observable |
| `legacyGlobalSecurityConfigToNamespaceSecurityPolicy()` | Removed — publish policy directly |

---

## The model in one sentence

```
OPEN   = trust your infrastructure
ANCHORED = trust cryptographic proof
```

No intermediate states. No negotiation. Policy takes effect the moment the engine processes it.

---

## What Console owns

Console is the **policy authority**. Its responsibilities:

1. **Publish namespace security policy** — `OPEN` or `ANCHORED`, nothing else
2. **Manage the trust registry** — countersign and publish worker/engine Ed25519 keys
3. **Visualize engine readiness** — surface mismatch reasons to operators
4. **Surface security events** — show blocked commands and policy rejections

---

## API surface

### 1. Publish / clear namespace security policy

Console needs a PLATFORM-role signing key pair. The policy writer key must be published to
`taktx-signing-keys` with `KeyRole.PLATFORM` before the engine will accept mutations.

```java
// Build your policy
NamespaceSecurityPolicyDTO policy = NamespaceSecurityPolicyDTO.builder()
    .mode(SecurityMode.OPEN)        // or SecurityMode.ANCHORED
    .policyVersion(nextVersion())   // monotonically increasing long
    .build();

// Publish — TaktXClient computes policyHash, signs the record, publishes it
TaktXClient.publishNamespaceSecurityPolicy(platformWriterProperties, policy);

// Clear (tombstone) — removes the active policy, engine returns to OPEN behaviour
TaktXClient.clearNamespaceSecurityPolicy(platformWriterProperties);
```

`platformWriterProperties` must include:
```properties
bootstrap.servers=...
taktx.engine.tenant-id=...
taktx.engine.namespace=...
taktx.signing.key-id=<PLATFORM-role key ID>
taktx.signing.private-key=<base64 Ed25519 private key>
taktx.signing.public-key=<base64 Ed25519 public key>
```

The `taktx-signing-keys` topic must already contain the policy writer's public key with
`KeyRole.PLATFORM` before the engine will accept mutations:

```java
TaktXClient.publishSigningKey(
    namespaceProperties,
    policyWriterKeyId,
    policyWriterPublicKeyBase64,
    "console",
    "Ed25519",
    KeyRole.PLATFORM);
```

### 2. Countersign and publish worker / engine keys (ANCHORED only)

In ANCHORED mode every key in the trust registry must carry a platform countersignature. Console
(or its Platform Service) is responsible for producing and publishing these countersigned entries.

```java
// Compute the canonical payload
String payload = keyId + "|" + publicKeyBase64 + "|" + algorithm + "|" + owner + "|" + role;

// Sign with the platform RSA private key (SHA256withRSA)
byte[] sigBytes = rsaSign(payload.getBytes(UTF_8), platformRsaPrivateKey);
String registrationSignature = Base64.getEncoder().encodeToString(sigBytes);

// Publish
TaktXClient.publishSigningKey(
    namespaceProperties,
    keyId,
    publicKeyBase64,
    owner,
    "Ed25519",
    KeyRole.CLIENT,           // or KeyRole.ENGINE for engine keys
    registrationSignature);   // null = community mode (no countersignature required in OPEN)
```

See `scripts/generate_trust_anchor.sh` for the shell equivalent.

### 3. Revoke a key

```java
SigningKeyRegistrar.revokeKey(bootstrapServers, prefixedSigningKeysTopic, existingKeyEntry);
```

The engine rejects all future signatures from a `REVOKED` key immediately.

### 4. Read engine readiness and security events

Use a TaktXClient with `SECURITY_OBSERVER` capability to subscribe to the namespace topics:

```java
TaktXClient observer = TaktXClient.newClientBuilder()
    .withProperties(namespaceProperties)
    .withParticipantDescriptor(new SecurityParticipantDescriptor(
        "console.namespace.observer",
        ParticipantKind.CLIENT,
        Set.of(ParticipantCapability.SECURITY_OBSERVER),
        "console"))
    .build();
observer.start();

// Current posture snapshot (blocking poll-style)
SecurityPostureSnapshot posture = observer.observability().getPostureSnapshot();

// Wait for a specific posture condition
observer.observability().awaitPostureSnapshot(
    snapshot -> snapshot.hasEffectivePolicy() && snapshot.effectiveMode() == SecurityMode.ANCHORED,
    Duration.ofSeconds(30));

// Recent security events (last N, configurable)
List<SecurityEventDTO> events = observer.observability().getRecentSecurityEvents();

// Subscribe to live updates
observer.observability().registerPostureSnapshotConsumer(snapshot -> {
    // called on every change
});
```

---

## Engine readiness codes — what to surface to operators

When the engine is in ANCHORED mode and a prerequisite is missing, it reports MISMATCH.
Console should display the mismatch reason codes:

| Code | Operator action |
|------|----------------|
| `TRUST_ANCHOR_MISSING` | Set `TAKTX_PLATFORM_PUBLIC_KEY` on the engine and restart |
| `ENGINE_STABLE_SIGNING_SOURCE_REQUIRED` | Switch engine to `env` or `file` signing source |
| `ENGINE_SIGNING_UNAVAILABLE` | Engine key not yet published — wait or check connectivity |
| `ENGINE_KEY_REGISTRATION_SIGNATURE_MISSING` | Countersign the engine key, set env var, restart |

---

## Security event codes — what to surface

| Code | Severity | Meaning |
|------|----------|---------|
| `SIGNATURE_MISSING` | WARNING | Inbound command had no `tx-sig` in ANCHORED mode |
| `DATA_PLANE_BLOCKED` | WARNING | Engine not ready for data-plane work |
| `SIGNING_IDENTITY_ROTATED` | INFO | Worker key rotated as expected |
| `UNEXPECTED_SIGNING_IDENTITY_CHURN` | WARNING | Worker key changed unexpectedly |
| `CONTROL_PLANE_MUTATION_REJECTED` | ERROR | Policy write rejected (wrong role / invalid signature) |

Security rejections **never produce DLQ entries**. They go to `taktx-security-events` only.

---

## Concrete scenarios

For detailed walk-throughs of OPEN → ANCHORED migration, greenfield ANCHORED deployment, key
rotation, and error states, see:

**`docs/security-operator-guide.md`** — the primary reference for all deployment scenarios

**`docs/drastic-security-simplification.md`** — architectural decision record (the why)

---

## What a minimum viable Console security UI looks like

```
Namespace: production
Mode: ANCHORED  (policy version 42)

Engine readiness: READY ✓
  └ Trust anchor:   ✓ configured
  └ Signing source: ✓ stable (env)
  └ Key published:  ✓ countersigned

Participants (3)
  ├ engine@host-1    READY   ✓
  ├ billing-worker   READY   ✓
  └ fulfillment-svc  MISMATCH ✗  → key not in trust registry

Recent security events (last 24h)
  23:55 DATA_PLANE_BLOCKED  fulfillment-svc  SIGNATURE_MISSING
  23:54 DATA_PLANE_BLOCKED  fulfillment-svc  SIGNATURE_MISSING
```

---

## Namespace isolation

Each namespace has its own independent security policy. A policy published to
`acme.production.taktx-security-policy` has no effect on `acme.staging.taktx-security-policy`.
Workers and engines reading a different namespace are unaffected.

---

## Topic reference

All topics are prefixed `<tenantId>.<namespace>.`:

| Topic | Purpose |
|-------|---------|
| `taktx-security-policy` | Console writes policy here (compacted, PLATFORM-signed) |
| `taktx-signing-keys` | Trust registry (compacted, key entries per key ID) |
| `taktx-security-events` | Engine writes rejection and readiness events here |
| `taktx-participant-status` | Engine writes its readiness status here every 30 s |

---

## Dependencies

```xml
<!-- Core -->
<dependency>
  <groupId>io.taktx</groupId>
  <artifactId>taktx-client</artifactId>
  <version>${taktx.version}</version>
</dependency>
<dependency>
  <groupId>io.taktx</groupId>
  <artifactId>taktx-shared</artifactId>
  <version>${taktx.version}</version>
</dependency>

<!-- Framework wrappers (pick one if applicable) -->
<dependency>
  <groupId>io.taktx</groupId>
  <artifactId>taktx-client-quarkus</artifactId>
  <version>${taktx.version}</version>
</dependency>
<!-- or taktx-client-spring-boot-3 / taktx-client-spring-boot-4 -->
```
