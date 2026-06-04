# TaktX Security — Operator Guide

**Audience:** Platform engineers and operators deploying TaktX Engine and integrating worker services.

This guide walks through concrete scenarios: what to configure, what happens at runtime, what the engine accepts or rejects, and how to move between modes.

---

## The model in one paragraph

TaktX has exactly two namespace security modes:

```
OPEN     — trust your infrastructure (Kafka ACLs, SASL, mTLS, network)
ANCHORED — trust cryptographic proof (every external ingress must carry a
           verified Ed25519 signature from a key in the trust registry)
```

The mode is authoritative and immediate. There is no negotiation, no activation lifecycle, no per-message-type signing matrix. The engine enforces one rule:

```
OPEN     → accept
ANCHORED → verify
```

---

## Scenario 1 — OPEN mode (default)

### What you configure

Nothing beyond standard Kafka connectivity. No signing keys, no platform key, no trust registry.

```properties
# Minimum viable engine deployment
bootstrap.servers=kafka:9092
taktx.engine.tenant-id=acme
taktx.engine.namespace=production
```

### What the engine does

- Accepts all external runtime ingress (process starts, worker responses, signals, message events, user task completions) without signature verification.
- Publishes its own internal signing key to `taktx-signing-keys` for engine-internal records (schedule commands, sub-process triggers). These are always engine-signed for integrity; no operator action needed.
- Reports `READY` readiness for data-plane work.
- Security events: none for accepted commands.

### What workers need

Nothing. Workers can call `startProcess`, complete tasks, and publish signals without any signing configuration.

```java
// No signing configured — works fine in OPEN mode
TaktXClient client = TaktXClient.newClientBuilder()
    .withProperties(props)
    .build();
client.runtime().startProcess("order-fulfillment", vars);
```

### What the engine rejects

Nothing security-related. Authorization failures (wrong JWT claims, replay protection) still apply if those gates are individually active.

### Readiness signal

```
effectiveState=READY  readyForDataPlane=true  supportedModes=[OPEN]
```

---

## Scenario 2 — ANCHORED mode, prerequisites met

This is the production configuration. Every external message must be signed by a key in the trust registry, and every key must carry a platform countersignature.

### Prerequisites

| Component | Requirement |
|-----------|-------------|
| Engine | `TAKTX_PLATFORM_PUBLIC_KEY` — RSA public key (base64 DER) |
| Engine | Stable signing identity (`TAKTX_SIGNING_IDENTITY_SOURCE=env` or `file`, with key published and countersigned) |
| Engine | `TAKTX_ENGINE_KEY_REGISTRATION_SIGNATURE` — platform countersignature over engine's Ed25519 key |
| Workers | Ed25519 signing key, published to `taktx-signing-keys` with platform countersignature |
| Namespace | Authoritative `ANCHORED` policy published by a PLATFORM-role key writer |

### Engine startup sequence (ANCHORED, fully configured)

```
1. Engine starts, loads signing identity from env/file.
2. Engine publishes its public key + registration signature to taktx-signing-keys.
   (ACTIVE status, ENGINE role, countersigned)
3. Engine waits for own key to appear in the signing-keys KTable.
4. Engine reads namespace security policy → mode=ANCHORED.
5. EngineSecurityReadinessEvaluator checks:
     - hasPlatformTrustAnchorConfigured()     → ✓ TAKTX_PLATFORM_PUBLIC_KEY set
     - hasStableSigningSourceConfigured()     → ✓ env/file source, not generated
     - messageSigningService.isPublicKeyPublished() → ✓ key in KTable
     - hasEngineKeyRegistrationSignatureConfigured() → ✓ countersigned
6. Engine reports READY for data-plane work.
```

### What the engine verifies on every external ingress

For every inbound process start, worker response, signal, message event, or user task completion the engine checks:

1. `tx-sig` header present
2. Key ID resolves in the `taktx-signing-keys` KTable
3. Key status is not `REVOKED`
4. Key carries a valid platform countersignature (`registrationSignature`)
5. Ed25519 signature over the payload is cryptographically valid

Any failure → record silently dropped, `DATA_PLANE_BLOCKED` security event emitted, no DLQ entry.

### What workers need

```bash
# Generate worker key
openssl genpkey -algorithm Ed25519 -out worker.pem
openssl pkey -in worker.pem -outform DER | base64 -w0 > private.b64
openssl pkey -in worker.pem -pubout -outform DER | base64 -w0 > public.b64

# Platform operator countersigns the key
PAYLOAD="worker-billing-1|$(cat public.b64)|Ed25519|billing-service|CLIENT"
REGISTRATION_SIG=$(printf '%s' "$PAYLOAD" | openssl dgst -sha256 -sign platform-private.pem | base64 -w0)

# Publish to trust registry
export TAKTX_SIGNING_KEY_ID=worker-billing-1
export TAKTX_SIGNING_PRIVATE_KEY=$(cat private.b64)
export TAKTX_SIGNING_PUBLIC_KEY=$(cat public.b64)
export TAKTX_SIGNING_REGISTRATION_SIGNATURE=$REGISTRATION_SIG
```

The TaktXClient auto-publishes the key on `client.start()` and auto-signs every outbound message — callers need no per-operation signing configuration.

### Readiness signal

```
effectiveState=READY  readyForDataPlane=true  supportedModes=[OPEN, ANCHORED]
```

---

## Scenario 3 — ANCHORED mode, prerequisites partially missing

### Sub-scenario 3a — No platform public key (`TAKTX_PLATFORM_PUBLIC_KEY` absent)

The engine starts, sees the ANCHORED namespace policy, but cannot enforce countersignature verification.

**Engine behavior:** fails closed immediately.

```
effectiveState=MISMATCH  readyForDataPlane=false
mismatchReasons:
  - code=TRUST_ANCHOR_MISSING
    message="Namespace requires anchored trust but no platform public key is configured"
```

Every inbound data-plane record is rejected with `DATA_PLANE_BLOCKED / TRUST_ANCHOR_MISSING`. No process instances start. No DLQ entries.

**Fix:** Set `TAKTX_PLATFORM_PUBLIC_KEY` and restart the engine. The engine will re-evaluate readiness and begin accepting signed traffic once all prerequisites are met.

### Sub-scenario 3b — Generated signing source (not stable)

The engine has a platform key but uses `TAKTX_SIGNING_IDENTITY_SOURCE=generated` (or the source is unset, which defaults to generated).

**Engine behavior:** fails closed.

```
effectiveState=MISMATCH  readyForDataPlane=false
mismatchReasons:
  - code=TRUST_ANCHOR_MISSING              (if platform key also missing)
  - code=ENGINE_STABLE_SIGNING_SOURCE_REQUIRED
    message="Namespace requires anchored trust but the engine is not configured
             with a stable signing identity source (env/file)"
```

A generated key changes on every restart. The engine cannot be pre-approved in the trust registry. Switch to `env` or `file` source.

### Sub-scenario 3c — Engine key not yet published/countersigned

The engine has a stable signing source and a platform key, but the engine key has not been countersigned and published yet.

```
effectiveState=MISMATCH  readyForDataPlane=false
mismatchReasons:
  - code=ENGINE_SIGNING_UNAVAILABLE
  - code=ENGINE_KEY_REGISTRATION_SIGNATURE_MISSING
```

**Fix:** Run `scripts/generate_trust_anchor.sh` to countersign the engine key, set `TAKTX_ENGINE_KEY_REGISTRATION_SIGNATURE`, restart.

### Sub-scenario 3d — Unknown or revoked worker key

The engine is READY. A worker sends a signed message with a key ID that is not in `taktx-signing-keys`, or whose status is `REVOKED`.

**Engine behavior:** drops the record silently.

```
WARN  Rejected process-instance ingress  signerKeyId=unknown-key-999
      reason="Unknown Ed25519 keyId unknown-key-999 — signer not found in taktx-signing-keys KTable"
Security event: eventType=DATA_PLANE_BLOCKED  code=SIGNATURE_MISSING
```

No process instance is created. No DLQ entry. The worker must publish a countersigned key before sending commands.

---

## Scenario 4 — Starting without signing, adding it later (OPEN → ANCHORED migration)

This is the most common production path: start open for development, harden to ANCHORED for production.

### Phase 1 — Deploy OPEN

Deploy the engine and workers with no signing configuration. Everything works. No security prerequisites.

### Phase 2 — Prepare signing infrastructure (zero-downtime)

Before publishing the ANCHORED policy, complete all prerequisites while the engine is still in OPEN mode:

```
1. Generate a platform RSA keypair (once, kept offline).
   openssl genrsa -out platform-private.pem 4096
   openssl rsa -in platform-private.pem -pubout -outform DER | base64 -w0 > platform-public.b64

2. For each engine instance:
   a. Generate a stable Ed25519 key (env or file source).
   b. Countersign it with the platform key.
   c. Set TAKTX_PLATFORM_PUBLIC_KEY and TAKTX_ENGINE_KEY_REGISTRATION_SIGNATURE.
   d. Restart the engine. It publishes its countersigned key to taktx-signing-keys.

3. For each worker:
   a. Generate an Ed25519 key.
   b. Countersign it with the platform key.
   c. Configure TAKTX_SIGNING_KEY_ID, TAKTX_SIGNING_PRIVATE_KEY,
      TAKTX_SIGNING_PUBLIC_KEY, TAKTX_SIGNING_REGISTRATION_SIGNATURE.
   d. Redeploy. TaktXClient publishes the countersigned key to taktx-signing-keys
      on start(). The client begins auto-signing all outbound messages.

   → In OPEN mode the engine accepts both signed and unsigned messages,
     so signed workers coexist safely with the current unsigned state.
```

### Phase 3 — Flip to ANCHORED

When all engines and workers are configured and have published their countersigned keys:

```java
// Published by a PLATFORM-role key writer (e.g. Console)
TaktXClient.publishNamespaceSecurityPolicy(
    platformWriterProperties,
    NamespaceSecurityPolicyDTO.builder()
        .mode(SecurityMode.ANCHORED)
        .policyVersion(nextVersion)
        .build());
```

The engine processes the policy change immediately. From that moment:
- All external ingress without a valid countersigned signature is rejected.
- Workers that completed Phase 2 continue without interruption.
- Workers that skipped Phase 2 are immediately blocked.

### Rollback

To return to OPEN, publish an OPEN policy. The engine reverts to accepting unsigned traffic immediately. No restart required.

```java
TaktXClient.publishNamespaceSecurityPolicy(
    platformWriterProperties,
    NamespaceSecurityPolicyDTO.builder()
        .mode(SecurityMode.OPEN)
        .policyVersion(nextVersion)
        .build());
```

---

## Scenario 5 — Starting with ANCHORED from day one

New deployment, greenfield, highest-security posture from the start.

### Order of operations

```
1. Generate platform keypair (offline, secure storage).
2. Generate and countersign all engine keys.
3. Set all engine environment variables:
      TAKTX_PLATFORM_PUBLIC_KEY
      TAKTX_SIGNING_IDENTITY_SOURCE=env  (or file)
      TAKTX_SIGNING_KEY_ID
      TAKTX_SIGNING_PRIVATE_KEY
      TAKTX_SIGNING_PUBLIC_KEY
      TAKTX_ENGINE_KEY_REGISTRATION_SIGNATURE
4. Start engine. It publishes its key and reports READY.
5. Configure all workers with countersigned keys.
   Workers publish keys on start() before sending any commands.
6. Publish ANCHORED namespace policy.
   Engine enforces immediately — only countersigned keys accepted.
```

The engine will hold MISMATCH readiness (rejecting data-plane work) until Step 4 completes. If the policy is published before workers have their keys, those workers are blocked until their keys appear in the trust registry.

---

## Scenario 6 — Worker key rotation (ANCHORED mode)

### Expected rotation (e.g. mounted file, live rotation)

```
1. Update the mounted key files (new key ID, new key material, countersigned).
2. FileSigningIdentitySource detects the change within the refresh interval.
3. TaktXClient publishes the new key (ACTIVE) to taktx-signing-keys.
4. TaktXClient retires the old key (TRUSTED) to taktx-signing-keys.
   → In-flight messages signed with the old key remain valid during the drain window.
5. TaktXClient emits SIGNING_IDENTITY_ROTATED (INFO) observability event.
6. After the drain window, the platform operator revokes the old key:
   SigningKeyRegistrar.revokeKey(bootstrapServers, topic, oldKeyEntry);
```

### Unexpected churn (e.g. generated key, restart-unstable source)

If the key identity changes unexpectedly (source not restart-stable), TaktXClient emits `UNEXPECTED_SIGNING_IDENTITY_CHURN` (WARNING) and the engine's readiness evaluator reports `ENGINE_STABLE_SIGNING_SOURCE_REQUIRED` in ANCHORED mode.

---

## Scenario 7 — Engine key rotation

Engine key rotation follows the same lifecycle as worker rotation, but uses ENGINE-role keys:

```
1. Update engine environment variables with new key + countersignature.
2. Restart engine (or hot-reload if supported by the identity source).
3. Engine publishes new key (ACTIVE) to taktx-signing-keys.
4. Engine retires old key (TRUSTED) automatically.
5. In-flight internal triggers (sub-process, schedule commands) signed with the
   old key continue to be accepted until the TRUSTED drain window expires.
```

The `TRUSTED` status preserves old-key acceptance. The engine never simultaneously accepts two ACTIVE engine keys.

---

## Reference — readiness codes

| Code | Meaning | Fix |
|------|---------|-----|
| `TRUST_ANCHOR_MISSING` | No `TAKTX_PLATFORM_PUBLIC_KEY` | Set platform public key, restart |
| `ENGINE_STABLE_SIGNING_SOURCE_REQUIRED` | Signing source is generated (ephemeral) | Switch to `env` or `file` source |
| `ENGINE_SIGNING_UNAVAILABLE` | Key not yet published to signing-keys KTable | Wait for publication, or check connectivity to `taktx-signing-keys` |
| `ENGINE_KEY_REGISTRATION_SIGNATURE_MISSING` | No countersignature on engine key | Run `scripts/generate_trust_anchor.sh`, set env var |
| `SIGNATURE_MISSING` | Inbound record has no `tx-sig` header | Worker not signing; check client configuration |
| `TRUST_ANCHOR_MISSING` (ingress) | Inbound signed record but no platform key to verify countersignature | Set `TAKTX_PLATFORM_PUBLIC_KEY` on engine |

---

## Reference — security event codes

| Code | Severity | When emitted |
|------|----------|-------------|
| `DATA_PLANE_BLOCKED` | WARNING | Engine MISMATCH: data-plane work rejected due to readiness failure |
| `SIGNATURE_MISSING` | WARNING | Inbound record has no `tx-sig` in ANCHORED mode |
| `SIGNING_IDENTITY_ROTATED` | INFO | Worker key changed on a source that supports live rotation |
| `UNEXPECTED_SIGNING_IDENTITY_CHURN` | WARNING | Worker key changed on a source that is not live-rotation-capable |
| `CONTROL_PLANE_MUTATION_REJECTED` | ERROR | Namespace policy write rejected (wrong role, invalid signature) |
| `INVALID_POLICY_MUTATION` | ERROR | Policy record failed authorization |

Security rejections never produce DLQ entries. DLQ is reserved for payload decode failures and engine processing errors.

---

## Decision tree

```
Is this a community/dev/test deployment?
  → OPEN. No signing configuration needed. Trust your Kafka ACLs.

Is this production / regulated / financial / compliance-sensitive?
  → ANCHORED.

Have you set TAKTX_PLATFORM_PUBLIC_KEY on the engine?
  No → engine fails closed in ANCHORED mode. Set it first.

Does each engine instance have a stable signing source (env/file) + countersignature?
  No → engine reports ENGINE_STABLE_SIGNING_SOURCE_REQUIRED. Fix before switching mode.

Do all workers have countersigned keys published to taktx-signing-keys?
  No → they are blocked the moment you publish ANCHORED policy.
       Prepare all workers in OPEN mode first, then flip.

Are you migrating an existing OPEN deployment?
  → Follow Scenario 4: prepare signing in OPEN, flip to ANCHORED atomically.
  → Rollback is instant: publish OPEN policy. No restart.
```
