# TaktX Engine — RBAC Enforcement & Message Signing Design

**Date:** March 7, 2026  
**Status:** Ready for implementation  
**Scope:** TaktX Client library (`taktx-client`) and TaktX Engine  
**Prerequisite:** Platform Service and Ingester changes are complete. This document covers what remains.

---

## Introduction

### Background

TaktX is a BPMN process engine that executes business processes. It receives commands — start a process, cancel an instance — and emits execution events. In a production deployment these commands and events flow through Apache Kafka. The TaktX Console provides a user interface for managing and monitoring process execution, backed by a Platform Service (BFF) and one or more Ingesters that bridge the Console to the engine.

Until now, access control in TaktX has been enforced at the Console layer only. The Platform Service authenticates users via Keycloak, checks role-based process permissions, and decides whether to allow an action. Once that decision is made and the command is handed to TaktXClient, **the engine accepts it unconditionally**. The engine has no knowledge of who triggered a command, whether they were permitted to do so, or whether the command came from a legitimate source at all.

### The Problem

This creates a trust boundary gap. The ingester is the gatekeeper — but it is not the last line of defence. Anyone who can reach the Kafka cluster directly can bypass the ingester entirely and send arbitrary commands to the engine:

- A developer with direct Kafka access in a staging environment
- A misconfigured service with overly broad Kafka credentials
- A compromised ingester process whose Kafka principal is still valid
- A customer deployment where Kafka ACLs were never configured

In all of these cases the engine currently has no way to distinguish a legitimate command — one that a real authorised user triggered through the Console — from an injected one.

Beyond external threats, there is an audit trail problem. When a process instance is started or cancelled, there is currently no way to trace that action back to the specific user who initiated it at the engine level. The engine knows *what* happened but not *who* authorised it.

### What This Document Covers

This document describes a signing mechanism that extends the Console's RBAC enforcement all the way to the engine, closing the trust gap between the ingester and the engine. The approach:

1. **The Platform Service signs a short-lived JWT** (authorization token) whenever a user performs an action — start, cancel, etc. The token cryptographically encodes who, what, which process, which version, and a unique audit ID.

2. **The Ingester validates the token** before calling TaktXClient, then **forwards the raw token** into the Kafka message as a header. No new token is generated — the Platform Service signature travels intact.

3. **The Engine validates the token** on every consumed command. An invalid or missing token causes the command to be rejected. The `auditId` from the token is attached to all execution events for traceability.

4. **The Engine signs its own internally-generated commands** (timer starts, message starts, call activities) using its own RSA key pair and the same JWT format, with a different `iss` (issuer) claim. The consumer uses the issuer to select the correct public key — one validation path, two key sources.

5. **Everything is opt-in and feature-flagged.** Deployments without the Console, or in early stages of adopting this security posture, continue to work unchanged with `taktx.security.envelope-validation.enabled=false`.

### What Problems This Solves

| Problem | Solution |
|---|---|
| Anyone with Kafka access can send arbitrary commands | Engine rejects commands without a valid signed token |
| A compromised ingester can issue unauthorised commands | Engine validates Platform Service signature — ingester cannot forge it |
| No audit trail linking engine events to user actions | `auditId` claim flows from token into all execution events |
| Engine-internal commands (timer start, message start, call activity) are unsigned and forgeable | Engine signs its own commands with its own key pair using the same token format |
| Execution events can be injected by anyone with Kafka access | Engine-signed events allow consumers to verify origin (same mechanism, different direction) |
| Hard dependency on Kafka ACLs being correctly configured | Token validation is a code-level control that works regardless of infrastructure configuration |

### Relationship to the Console RBAC Implementation

The Console-side RBAC implementation (Platform Service, Ingester, Frontend) is **already complete**. This document covers only what remains: changes to the `taktx-client` library and the TaktX Engine. The two-line change in the Ingester that forwards the token to TaktXClient is also described here as it is the connector between the two systems.

---

## 0. Threat Model — Is Engine-Level Signing Necessary?

### The central question

If Kafka ACLs are properly enforced — only the ingester can write to command topics, only the engine can write to event topics — is there still a purpose to signing tokens all the way to the engine level?

**Short answer: it depends on how much you trust your Kafka ACL configuration to be correct, complete, and present in every deployment.**

### What Kafka ACLs give you

When ACLs are correctly configured:
- External attackers without a valid Kafka principal are blocked at the network/auth level before they can produce a single message
- Topic separation between ingester-writable and engine-writable topics is enforced by the broker

This is a strong control. If you have it, it covers the majority of external threat scenarios.

### What Kafka ACLs do NOT cover

| Threat | Kafka ACLs | Engine signing |
|---|---|---|
| External attacker, no Kafka access | ✅ blocked | — |
| External attacker with Kafka access (stolen credentials) | ❌ | ✅ |
| **Compromised ingester process** | ❌ ingester principal is legitimate | ✅ ingester cannot forge Platform Service token |
| Kafka admin / insider with broker access | ❌ can override ACLs | ✅ still needs Platform Service private key |
| Misconfigured ACLs (common in dev/early deployments) | ❌ | ✅ code-level control, works regardless |
| Customer deployment without ACLs enabled | ❌ | ✅ |
| Compromised engine process | ❌ | ❌ engine can bypass its own code |

The critical scenario Kafka ACLs cannot address is a **compromised ingester**. The ingester's Kafka principal is legitimate by definition — ACLs cannot distinguish a healthy ingester from a taken-over one. Engine-level token validation catches this because the ingester cannot forge a token signed by the Platform Service private key, which never leaves the Platform Service.

### The practical reality for a distributed product

TaktX is deployed at customer sites with varying levels of operational maturity. Some customers will have properly configured Kafka ACLs. Many will not — especially in early deployments, development environments, and proof-of-concept installations. The signed token is a **code-level control** that is always present regardless of infrastructure configuration.

Additionally, the `auditId` audit trail has value completely independent of security — it links every engine execution event back to the specific user action that caused it. This is useful for debugging, compliance, and support, regardless of whether ACLs are in place.

### Conclusion

**Kafka ACLs and engine-level signing are complementary, not alternatives.**

- Kafka ACLs are the strongest external boundary and should always be configured in production.
- Engine signing provides defence-in-depth that covers the compromised-ingester scenario, insider threats, and deployments where ACLs are absent or misconfigured.
- Neither alone is sufficient for a security-conscious product deployed across many customers.
- The `auditId` audit trail is valuable regardless.

The engine signing feature is **opt-in** (`taktx.security.envelope-validation.enabled=false` by default) precisely because it requires infrastructure (engine key pairs) that not every deployment will have from day one. But it should be the recommended posture for any production deployment.

### What the token proves

The token is RS256-signed by the Platform Service private key — a key that never leaves the Platform Service. A valid signature proves:
1. The Platform Service issued it — meaning a real user's permission was checked at that moment
2. It has not been tampered with since issuance
3. It was issued for a specific action, process definition, version, namespace, and user

### What the token does NOT prove on its own

A valid Platform Service token does not prove that the *caller of TaktXClient* is the legitimate ingester — only that someone with a valid token produced the message. The nonce check (`auditId`) and Kafka ACLs together close this gap.

### Threat table

| Threat | Primary Control | Secondary Control |
|---|---|---|
| External attacker, no Kafka access | Kafka ACLs | — |
| External attacker with Kafka access | Engine rejects (no valid token) | `auditId` nonce check |
| Compromised ingester | Engine rejects (cannot forge Platform Service token) | — |
| Insider / Kafka admin | Engine rejects (no Platform Service private key) | Kafka ACLs (procedural) |
| Replay of captured valid token | `auditId` nonce check | 5-minute TTL |
| Forged token (self-signed) | RS256 signature verification | — |
| Wrong action/definition in token | Claim-to-command matching | — |
| No ACLs configured (dev/early deployment) | Engine rejects (no valid token) | — |

---


## 0b. Alternative: Bottom-Up Signing (Engine Signs Events)

### The idea

Instead of only the Platform Service signing tokens (top-down), each engine instance generates its own RSA key pair and signs its outgoing execution events. Consumers of those events — the ingester, Platform Service, or any monitoring component — verify the engine's signature before trusting the event.

### Two distinct use cases — only one makes sense

**Use case A — Mutual authentication (engine proves it is a legitimate engine to the ingester)**  
This does not map to the Kafka architecture. The ingester and engine do not have a direct request/response relationship — the ingester publishes commands to a topic and the engine consumes them asynchronously. There is no handshake opportunity. Kafka ACLs already serve this purpose at the infrastructure level. This use case does not apply here.

**Use case B — Engine signs execution events (bottom-up)**  
This is meaningful. Currently the event path is completely unsigned:

```
Engine → Kafka events topic → Ingester / Platform Service / Frontend
```

Anyone who can produce to the events topic can:
- Inject fake process completion events
- Fabricate state transitions
- Forge audit trail entries
- Manipulate what the Console shows users

Top-down signing (section 0) secures the *command* path. Bottom-up signing secures the *event* path. Together they close both directions.

### How it would work

1. Each engine instance generates an RSA key pair at startup.
2. It publishes its public key to a registration topic (e.g. `taktx-engine-registry`, compacted, keyed by engine instance ID).
3. The ingester/Platform Service consumes the registry topic and maintains a map of `engineId → PublicKey`.
4. Every execution event carries an `engineId` claim and a signature in a Kafka header (`X-TaktX-Engine-Signature`).
5. The consumer verifies the signature using the registered public key for that engine ID.

### The bootstrapping problem (same as top-down, reversed)

The engine registration topic has the same bootstrapping issue: an attacker who can write to it can register a fake engine key. The mitigation is the same — Kafka ACLs restrict who can write to the registry topic (only engine principals), and optionally the operator pre-registers known engine public keys out-of-band.

### Why it is not in scope for the initial implementation

| Concern | Assessment |
|---|---|
| Does it solve a real problem? | Yes — fake event injection is a real attack vector |
| Is it urgent? | No — requires Kafka write access, which means command ACLs have already failed |
| Complexity added | Medium — N key pairs (one per engine instance), per-engine rotation, registry consumer on ingester side |
| Prerequisite | Top-down signing should be working and stable first |

**Recommendation:** Implement top-down signing (commands) first. Document the event path as a known gap. Add bottom-up signing (events) as a follow-on phase — and note that once engine key pairs exist (section 0c), signing events uses the **exact same mechanism**: `iss: "taktx-engine:<id>"` in the token, validated by the event consumer using the engine key registry. No additional infrastructure is needed beyond what section 0c requires.

### Known gap (to be addressed in a future phase)

> The execution event path (`Engine → events topic → consumers`) is currently unsigned. An attacker with write access to the events topic can inject arbitrary events. Mitigation today: Kafka ACLs restricting `WRITE` on events topics to engine principals only. Full mitigation: engine key pairs (section 0c) — the same tokens used for internal commands are also used to sign events.

---

## 0c. Engine-Originated Commands — Unified Token Model via `iss` Claim

### The insight

The Platform Service token already carries an `iss` (issuer) claim — currently always `"taktx-platform-service"`. Every JWT consumer is already required to validate `iss` as part of standard JWT processing.

The engine can issue its own tokens for internally-generated commands using **exactly the same JWT format**, but with a different issuer:

```
iss: "taktx-engine:<engineInstanceId>"
```

The consuming engine reads `iss` first, selects the correct public key from its key registry, then validates the signature. One token format. One validation path. Two issuers. No separate header, no separate mechanism.

This cleanly resolves the engine-originated command problem: internally-generated commands carry an engine-signed token; externally-originated commands carry a Platform Service-signed token. The consumer doesn't need to know which type it has before it starts — it reads `iss`, looks up the key, validates. If the key lookup fails or the signature is invalid, the command is rejected regardless of claimed type.

### Token format for engine-issued tokens

Same structure as Platform Service tokens (section 2), with these differences:

```json
{
  "iss":    "taktx-engine:a3f8e1c2-9b44-4d7e-bc21-0f6a834e5d91",
  "sub":    "taktx-engine:a3f8e1c2-9b44-4d7e-bc21-0f6a834e5d91",
  "iat":    1234567890,
  "exp":    1234568190,
  "action": "START",
  "processDefinitionId": "order-process",
  "version": 1,
  "namespaceId": "0486b3c8-...",
  "auditId": "unique-uuid-per-command",
  "trigger": "TIMER_START"
}
```

Key differences from Platform Service tokens:

| Claim | Platform Service token | Engine token |
|---|---|---|
| `iss` | `"taktx-platform-service"` | `"taktx-engine:<engineInstanceId>"` |
| `sub` | Keycloak user ID | `"taktx-engine:<engineInstanceId>"` |
| `trigger` | absent | Reason for internal command: `TIMER_START`, `MESSAGE_START`, `CALL_ACTIVITY`, `TIMER_ADVANCE`, `MESSAGE_CORRELATION`, etc. |

The `action`, `processDefinitionId`, `version`, `namespaceId`, and `auditId` claims are identical in structure — the engine token is a first-class token, not a special case.

### Consumer validation logic

```java
// Step 1: read issuer — determines which key to use
String issuer = extractIssuerUnchecked(rawToken); // parse without verification

// Step 2: select public key by issuer
PublicKey verificationKey;
if ("taktx-platform-service".equals(issuer)) {
    verificationKey = publicKeyProvider.getPlatformKey();
} else if (issuer != null && issuer.startsWith("taktx-engine:")) {
    String engineId = issuer.substring("taktx-engine:".length());
    verificationKey = engineKeyRegistry.getKey(engineId);
    if (verificationKey == null) {
        throw new AuthorizationTokenException(
            "Unknown engine issuer: " + engineId + " — not in registry");
    }
} else {
    throw new AuthorizationTokenException("Unknown token issuer: " + issuer);
}

// Step 3: validate signature, expiry, and claims — same code path for both
Claims claims = Jwts.parser()
    .verifyWith(verificationKey)
    .build()
    .parseSignedClaims(rawToken)
    .getPayload();

// Step 4: match claims to command — same logic for both
validateClaimsMatchCommand(claims, command);
```

The attacker cannot fake the issuer to choose a weaker key — the signature over the full token payload (including `iss`) must verify against the key selected by `iss`. If they claim `iss: "taktx-engine:some-id"` and there is no registered key for that ID, validation fails. If they register a fake engine key, the registry bootstrapping controls apply (see below).

### Engine key registry

The engine publishes its public key to `taktx-engine-registry` (compacted topic, keyed by `engineInstanceId`) on startup. All engine instances consume this topic and maintain a local `Map<engineInstanceId, PublicKey>`.

This is the same bootstrapping challenge as the Platform Service public key (section 3). The same solution applies:
- **Operator-supplied env var** (`TAKTX_ENGINE_PUBLIC_KEYS`) takes precedence — a JSON map of `engineInstanceId → Base64 public key` set at deployment time
- **Registry topic** used for rotation and new instance registration, accepted only with a rotation proof signed by the current trusted key for that engine ID

### Impact on sections 0b and 0c

This approach **unifies** bottom-up event signing (section 0b) and internal command signing (section 0c) under the same mechanism:

- Engine-signed **commands** → `iss: "taktx-engine:<id>"`, validated by command consumer
- Engine-signed **events** → `iss: "taktx-engine:<id>"`, validated by event consumer (ingester/Platform Service)

The same `engineKeyRegistry` and `PublicKeyProvider` serve both. No separate header, no parallel mechanism.

### Revised summary

| Command / event source | Token issuer | Key used for validation |
|---|---|---|
| User action via ingester | `taktx-platform-service` | Platform Service public key (env var) |
| Engine internal (timer, correlation, subprocess) | `taktx-engine:<id>` | Engine instance public key (registry) |
| Engine internal (timed start, message start, call activity) | `taktx-engine:<id>` | Engine instance public key (registry) |
| Engine execution event | `taktx-engine:<id>` | Engine instance public key (registry) |
| Attacker — no registered key | Any | Key lookup fails → rejected |
| Attacker — fake platform token | `taktx-platform-service` | Signature fails (no private key) → rejected |

### Open questions for the engine team

1. Does the engine already have a stable `engineInstanceId`? If not, a UUID generated at first startup and persisted is sufficient.
2. Is the engine key pair persisted across restarts? Recommended: yes — avoids all peers needing to re-consume the registry on every restart.
3. Is there a preference for the `trigger` claim naming, or should it be a different field name?

---

## 1. Context and Goals

### What already exists (console repo — complete)

The TaktX Console has a full RBAC enforcement stack:

1. **Platform Service (BFF)** — validates user identity (Keycloak JWT), checks role-based process permissions, and signs short-lived **authorization tokens** (JWTs) that cryptographically prove a user was permitted to perform a specific action at a specific point in time.
2. **Ingester** — validates every incoming REST call and WebSocket connection carries a valid signed token from the Platform Service.
3. **TaktXClient call sites** — `taktClient.startProcess(...)` and `taktClient.abortElementInstance(...)` are called inside the ingester after token validation. The signed token is **available in scope at those call sites** but is not yet passed through to the client/engine.

### What this document covers

Extending the authorization token all the way to the engine so that:

- The engine can **optionally** verify that every command it consumes was authorized by the Platform Service.
- Deployments **without** the Console/Platform Service continue to work unchanged (feature-flagged, opt-in).
- The audit trail (`auditId`) from the token is emitted in engine execution events for traceability.

---

## 1b. Should the Token Carry Permission or Role Information?

**Short answer: no. The current design is correct and complete.**

### The token already is a permission statement

The claims `action`, `processDefinitionId`, `version`, and `namespaceId` together form a precise, scoped authorization assertion:

> *"The Platform Service has verified that user `sub` is permitted to perform `action` on `processDefinitionId` v`version` in namespace `namespaceId`."*

The engine uses this for claim-to-command matching (section 6). It does not need to know *why* the permission was granted — only that it was, and that the assertion is cryptographically genuine.

### Adding roles or permission rules would be redundant

You might consider embedding the matched role or permission rule:

```json
{
  "matchedRole":       "process-operator",
  "matchedPermission": "START on order-process v*"
}
```

This is redundant because the token is already a **derived assertion** — the Platform Service evaluated the full RBAC model, found a matching rule, and distilled the result into the token itself. The engine re-checking the raw rules would be doing the same work twice, against data that the engine should not own.

### Embedding roles or permissions would be actively harmful

- **Increased attack surface** — a captured token reveals role names and permission patterns, which are useful for reconnaissance even if the token cannot be forged.
- **Couples the engine to the RBAC model** — the engine is a general-purpose BPMN execution engine. Teaching it about `ProcessPermission.Action` or role structures is a console-layer concern. Keeping the engine ignorant of the permission model preserves clean separation and allows the engine to be used outside the Console context.
- **Larger token** — Kafka message headers have a practical size limit. JWT overhead should be kept minimal.

### The one genuine hardening worth considering: instance binding on CANCEL

The current CANCEL token binds to `action=CANCEL` and `namespaceId` but not to a specific `processInstanceId`. The nonce check (`auditId`) prevents exact replays, but a captured CANCEL token could theoretically be submitted for a *different* instance within the TTL window — the nonce is different so it would pass.

Adding `processInstanceId` to the CANCEL token closes this:

```json
{
  "action":             "CANCEL",
  "namespaceId":        "0486b3c8-...",
  "processInstanceId":  "7f3a1b2c-...",
  "auditId":            "..."
}
```

The engine would then verify `claims.getProcessInstanceId()` matches the command's target instance. This is a low-cost addition that tightens the binding without adding RBAC complexity. It is marked as a future hardening item in the checklist (section 10) since it requires a coordinated change across the Platform Service token signer, the ingester, and the engine consumer.

---

## 2. The Authorization Token — Exact Format

The Platform Service signs tokens using **RS256** (RSA 2048-bit, SHA-256).  
Library: `io.jsonwebtoken:jjwt-api` version `0.12.5`.

### JWT Header
```json
{
  "alg": "RS256"
}
```

### JWT Payload (claims)
```json
{
  "sub":                 "<userId>",
  "iss":                 "taktx-platform-service",
  "iat":                 1234567890,
  "exp":                 1234568190,
  "action":              "START",
  "namespaceId":         "0486b3c8-b68e-466b-8261-9aae55e17c2b",
  "processDefinitionId": "order-process",
  "version":             3,
  "auditId":             "a3f8e1c2-9b44-4d7e-bc21-0f6a834e5d91"
}
```

**Field definitions:**

| Claim | Type | Description |
|---|---|---|
| `sub` | string | User ID (Keycloak subject claim) |
| `iss` | string | Always `taktx-platform-service` |
| `iat` | epoch seconds | Token issued-at |
| `exp` | epoch seconds | Token expiry — always `iat + 300` (5 minutes) |
| `action` | string | One of: `START`, `CANCEL`, `VIEW`, `MODIFY` |
| `namespaceId` | UUID string | Namespace the action is scoped to |
| `processDefinitionId` | string | Exact process definition ID, or `"*"` for read tokens |
| `version` | integer | Concrete version (≥1), or `-1` for read tokens |
| `auditId` | UUID string | Unique per token — use this in engine execution events |

### Token lifetime

5 minutes. The ingester validates the token before calling `TaktXClient`. The token is then forwarded into the Kafka message. The engine should validate `exp` on consumption. If the Kafka consumer lag is large and the token has expired by the time the engine processes the message, the engine should **log a warning** but whether to reject or accept is configurable (see section 6).

---

## 3. Public Key Distribution — Configuration Topic

### The engine team's architectural decision

The engine uses a **configuration topic** — a compacted Kafka topic that all engine instances consume. Platform-level configuration (public signing key, license, and other parameters) is published to this topic by the **ingester** whenever it changes. Engine instances consume the topic at startup and on change, eliminating any HTTP dependency on the Platform Service at runtime.

> **Important:** The Platform Service has no Kafka connection. It is a pure HTTP service. The ingester is the component that bridges the two worlds — it fetches the public key from the Platform Service via HTTP (`GET /api/public-key`) and publishes it to the configuration topic on Kafka. The engine never talks to the Platform Service directly.

This is a well-established pattern (similar to how Confluent Schema Registry distributes schemas, or how Kafka's internal `__consumer_offsets` topic works). It is **better** than a direct HTTP fetch approach because:
- Engine instances have no network dependency on the Platform Service
- Key rotation propagates automatically to all engine instances without restart
- The same mechanism distributes the license and other future parameters consistently

### The bootstrapping trust problem — and the attack it enables

You cannot sign the public key distribution message with the key being distributed — that is circular. But the solution is not simply "trust the config topic via Kafka ACLs." Here is why.

**The attack:** An attacker who can write to the `taktx-platform-config` topic can:
1. Generate their own RSA key pair
2. Publish their own public key as `signing.public-key` to the config topic
3. Sign their own JWTs with their own private key
4. The engine loads the attacker's public key, validates their forged tokens, and executes arbitrary commands

This attack works regardless of the nonce check or claim matching — both of those controls operate *after* the public key is trusted. If the public key is wrong, the entire trust chain is broken.

**Why Kafka ACLs alone are insufficient:** Kafka ACLs are an infrastructure control enforced outside the engine code. They offer strong protection in a properly configured production cluster, but provide nothing in local development, misconfigured clusters, or bare Kafka deployments without SASL/ACL setup. The engine code itself has no way to verify that the public key it received is legitimate if it trusts the config topic unconditionally.

### The correct root of trust: operator-supplied key

The only channel an attacker writing to Kafka cannot tamper with is a value **injected by the operator at deployment time**, outside of Kafka entirely. This is the `TAKTX_PLATFORM_PUBLIC_KEY` environment variable.

**The key design decision:**

- **`TAKTX_PLATFORM_PUBLIC_KEY` is not a fallback — it is the secure root of trust.**  
  When set, the engine uses it as the authoritative public key and ignores the config topic value for key establishment. An attacker who can write to the config topic cannot override a value the operator set directly.

- **The config topic is used for key *rotation*, not key *establishment*.**  
  A new key arriving on the config topic is only accepted if it is accompanied by a token signed by the *currently trusted* key — i.e. the Platform Service proves it controls the current private key before the engine trusts the new public key. This closes the bootstrapping attack entirely.

**Practical deployment posture:**

| Environment | How public key is trusted |
|---|---|
| Production | `TAKTX_PLATFORM_PUBLIC_KEY` set by operator at deploy time (e.g. Kubernetes secret). Config topic used for rotation only — rotation is accepted only when signed by current key. |
| Docker Compose (with Platform Service) | `TAKTX_PLATFORM_PUBLIC_KEY` set from Platform Service's own `/api/public-key` response at first deployment. Operator copies it once. |
| Local dev / no ACLs | `TAKTX_PLATFORM_PUBLIC_KEY` set in `.env`. Config topic not used for key establishment. |
| Bare engine (no Console) | Envelope validation disabled (`taktx.security.envelope-validation.enabled=false`). Config topic not consumed for security purposes. |

> **Summary:** Configuration topic messages are unsigned. Their integrity for key *rotation* is guaranteed by requiring the rotation message to be signed by the currently trusted key (self-validating rotation). Their integrity for initial key *establishment* is guaranteed by the operator supplying the key out-of-band via environment variable. Kafka ACLs are an additional hardening layer but are not the primary trust mechanism in the code.

### Configuration topic specification

**Topic name:** `taktx-platform-config` (suggested — engine team to decide final name)  
**Cleanup policy:** `compact` — only the latest value per key is retained  
**Retention:** indefinite (compaction ensures the engine always has the latest value on startup)

**Message format:**

```
Key   (string): the configuration key name
Value (string): the configuration value, UTF-8 encoded
```

**Keys published by the Platform Service:**

| Key | Value format | Description |
|---|---|---|
| `signing.public-key` | Base64-encoded DER (X.509), no PEM headers | RSA 2048-bit public key for token verification |
| `license` | License blob (format defined by license system) | Current license |

Additional keys can be added in future without changing the consumer contract.

### When the ingester publishes

The ingester is responsible for bridging the Platform Service (HTTP) and the engine (Kafka):

- **On startup** — the ingester fetches the current public key from `GET /api/public-key` on the Platform Service and publishes it as `signing.public-key` to the config topic. This ensures any engine instance that was down during a previous change catches up correctly.
- **On periodic refresh** — the ingester should re-fetch and re-publish the public key on a schedule (e.g. every hour) so that after a Platform Service key rotation the engine picks up the new key without requiring an ingester restart. The interval should be configurable.
- **On license change** — the ingester fetches the updated license from the Platform Service and publishes it to the config topic. (Exact mechanism TBD when license distribution is designed.)

The ingester already fetches `GET /api/public-key` at startup via `PublicKeyFetcher.java`. Publishing to the config topic is an addional step after that fetch succeeds.

### Engine consumer behaviour

The engine distinguishes between **key establishment** (first-time trust) and **key rotation** (replacing a trusted key with a new one):

```java
// Pseudocode — engine startup
// Step 1: establish root of trust from operator-supplied env var
if (TAKTX_PLATFORM_PUBLIC_KEY is set) {
    publicKeyProvider.loadFromEnv();  // this is the root of trust
} else if (envelope-validation is enabled) {
    throw new RuntimeException(
        "TAKTX_PLATFORM_PUBLIC_KEY must be set when envelope validation is enabled. " +
        "Refusing to trust config topic without an operator-established root of trust.");
}

// Step 2: consume config topic for license and other non-security config
// Also watch for key rotation messages (see below)
KafkaConsumer<String, String> configConsumer = createConsumer("taktx-platform-config");
// ... consume to end-of-partition, apply license etc.
```

**Key rotation via config topic:**  
When a new `signing.public-key` record arrives on the config topic, the engine does NOT simply swap the key. Instead, the rotation message must carry a `rotation-proof` header: a JWT signed by the *current* trusted private key, containing the fingerprint of the new public key. The engine verifies this proof before trusting the new key.

```
Config topic rotation message:
  Key:    signing.public-key
  Value:  <new Base64 public key>
  Header: X-TaktX-Rotation-Proof: <JWT signed by current private key>
                                    claims: { newKeyFingerprint: "sha256:..." }
```

If no rotation proof is present, or if the proof is invalid, the engine ignores the new key value and logs a security warning. This means an attacker who can write to the config topic cannot replace the trusted key — they would need the current private key to produce a valid rotation proof, and the private key never leaves the Platform Service.

```java
// Apply loaded config
for (ConsumerRecord<String, String> r : records) {
    if ("signing.public-key".equals(r.key())) {
        String rotationProof = headerValue(r, "X-TaktX-Rotation-Proof");
        if (publicKeyProvider.isReady()) {
            // Key already established — require signed rotation proof
            if (!tokenValidator.isValidRotationProof(rotationProof, r.value())) {
                log.warn("⚠️ Rejected key rotation: invalid or missing rotation proof");
                continue; // ignore
            }
        }
        // Either first-time load from topic (env var not set, validation disabled)
        // or valid rotation — apply
        publicKeyProvider.loadFromBase64(r.value());
    }
    if ("license".equals(r.key())) {
        licenseManager.apply(r.value());
    }
}
```

### Parsing the public key

```java
String base64Key = configValue.trim();
byte[] keyBytes = Base64.getDecoder().decode(base64Key);
X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
KeyFactory keyFactory = KeyFactory.getInstance("RSA");
PublicKey publicKey = keyFactory.generatePublic(spec);
```

### Primary path: operator-supplied environment variable

When envelope validation is enabled, **the operator must supply the public key via environment variable**. This is the root of trust — it cannot be tampered with by anyone who can write to Kafka.

```properties
# Required when taktx.security.envelope-validation.enabled=true
TAKTX_PLATFORM_PUBLIC_KEY=MIIBIjAN...
```

The value is obtained from the Platform Service's `GET /api/public-key` endpoint once by the operator at deployment time and injected as a secret (Kubernetes Secret, Docker Compose environment, etc.).

For deployments without envelope validation (bare engine, tests, local dev without Console), this variable is not required and the config topic is consumed for non-security config only.

### Ingester — what needs to be added (console repo)

The Platform Service already exposes `GET /api/public-key` and the ingester already fetches it at startup via `PublicKeyFetcher.java`. The ingester needs one addition: after fetching the public key, **publish it to the configuration topic**.

1. On startup, after `PublicKeyFetcher` successfully loads the public key, publish `signing.public-key` to the config topic.
2. On periodic refresh (configurable interval, suggested 1 hour), re-fetch from `GET /api/public-key` and re-publish if the key has changed (compare fingerprints).

The Platform Service itself requires **no changes** for this — it already exposes the key correctly via HTTP.

### Kafka ACLs for the configuration topic

| Principal | Permission | Topic |
|---|---|---|
| Ingester | `WRITE` | `taktx-platform-config` |
| Engine instance(s) | `READ` | `taktx-platform-config` |
| All others | DENY | `taktx-platform-config` |

---

## 4. TaktX Client API Changes

### Current API (relevant methods)

```java
// Start a process instance
UUID startProcess(String processDefinitionId, int version, VariablesDTO variables);

// Abort/cancel a process instance
void abortElementInstance(UUID processInstanceId);
```

### Required API change

Add an optional `signedEnvelope` parameter to both methods:

```java
// New overloads — backward compatible
UUID startProcess(String processDefinitionId, int version, VariablesDTO variables,
                  @Nullable String signedEnvelope);

void abortElementInstance(UUID processInstanceId, @Nullable String signedEnvelope);
```

**Backward compatibility:** Keep the existing two-arg / three-arg signatures as convenience overloads that delegate with `signedEnvelope = null`. Do not break existing call sites.

### Where in the console repo the call sites are

**`DefinitionResource.java`** (ingester, `startProcess`):
```java
// Current:
UUID instanceId = taktClient.startProcess(processDefinitionId, version, variablesDTO);

// After change — authToken is the validated START token already in scope:
UUID instanceId = taktClient.startProcess(processDefinitionId, version, variablesDTO, authToken);
```

**`InstanceResource.java`** (ingester, `abortElementInstance`):
```java
// Current:
taktClient.abortElementInstance(instanceId);

// After change — tokenClaims.getOriginalToken() or pass authToken from method param:
taktClient.abortElementInstance(instanceId, authToken);
```

The `authToken` string is the raw compact JWT string already validated by `AuthorizationTokenValidator` at the top of both methods. **No new token is generated** — the same token that was validated by the ingester is forwarded into the Kafka message.

---

## 5. Kafka Message Envelope

The signed token should travel as a **Kafka message header**, not in the message body/payload. This keeps the payload schema unchanged and makes the envelope easy to strip/inspect at the consumer.

### Header name

```
X-TaktX-Authorization
```

(Same name as the HTTP header used between Platform Service and Ingester — consistent naming.)

### Header value

The raw compact JWT string (UTF-8 bytes):
```
eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ1c2VyMSIs...
```

### TaktXClient implementation sketch

```java
// In the Kafka producer send path:
ProducerRecord<String, CommandPayload> record = new ProducerRecord<>(topic, key, payload);
if (signedEnvelope != null && !signedEnvelope.isBlank()) {
    record.headers().add(
        "X-TaktX-Authorization",
        signedEnvelope.getBytes(StandardCharsets.UTF_8)
    );
}
producer.send(record);
```

---

## 6. Engine — Envelope Validation

### Feature flag

```properties
# Default: false — existing deployments unaffected
taktx.security.envelope-validation.enabled=false
```

When `false`: the engine ignores the `X-TaktX-Authorization` header entirely. Behavior identical to today.  
When `true`: the engine validates the envelope on every consumed command.

### Startup behavior (when enabled)

On startup, the engine fetches/loads the Platform Service public key (see section 3). If key loading fails, startup fails with a clear error message.

### Validation logic per consumed command

```java
String rawToken = record.headers().lastHeader("X-TaktX-Authorization") != null
    ? new String(record.headers().lastHeader("X-TaktX-Authorization").value(), UTF_8)
    : null;

if (envelopeValidationEnabled) {
    if (rawToken == null || rawToken.isBlank()) {
        log.error("Rejected command {}: missing authorization envelope", commandId);
        // Move to dead-letter topic or skip — do NOT execute
        return;
    }
    try {
        TokenClaims claims = tokenValidator.validateToken(rawToken);
        validateClaimsMatchCommand(claims, command);
        log.info("✅ Command {} authorized: user={}, audit={}",
            commandId, claims.getUserId(), claims.getAuditId());
    } catch (AuthorizationTokenException e) {
        log.error("Rejected command {}: {}", commandId, e.getMessage());
        // Move to dead-letter topic or skip — do NOT execute
        return;
    }
}
```

### Claim-to-command matching

For `START` commands:
- `claims.getAction()` must be `"START"`
- `claims.getProcessDefinitionId()` must equal the command's `processDefinitionId`
- `claims.getVersion()` must equal the command's `version`

For `CANCEL`/`ABORT` commands:
- `claims.getAction()` must be `"CANCEL"`
- No process definition check needed (instance ID is not in the token — the ingester already validated this)

### Expired tokens

Tokens have a 5-minute TTL. If the Kafka consumer lag is high and a token has expired by the time the engine processes it, the engine should:
- **Log a warning** (not an error) and include the `auditId` and lag time.
- Whether to reject or accept is controlled by a second config flag:

```properties
# Default: warn-only — allows expired tokens through with a warning
taktx.security.envelope-validation.reject-expired=false
```

Setting `reject-expired=true` provides stricter security at the cost of rejecting commands when consumer lag exceeds 5 minutes.

### Single-use enforcement (auditId nonce tracking)

This is the control that closes the **replay attack** gap described in section 0. Each token carries a unique `auditId` UUID. The engine maintains an in-memory set of recently seen `auditId` values. If the same `auditId` appears on a second Kafka message, the command is rejected.

**Implementation:**

```java
// In-memory nonce store — entries expire after TOKEN_LIFETIME_MINUTES + small buffer
private final Cache<String, Boolean> seenAuditIds = Caffeine.newBuilder()
    .expireAfterWrite(10, TimeUnit.MINUTES)  // 5min TTL + 5min buffer for clock skew
    .maximumSize(100_000)
    .build();

// During validation:
String auditId = claims.getAuditId();
if (seenAuditIds.getIfPresent(auditId) != null) {
    log.error("Rejected command {}: auditId {} already used (replay attempt)", commandId, auditId);
    // move to dead-letter topic or skip
    return;
}
seenAuditIds.put(auditId, Boolean.TRUE);
```

**Properties:**
- The set only needs to retain entries for 10 minutes (5-minute token TTL + 5-minute buffer for clock skew). Expired tokens cannot be replayed anyway, so entries older than the TTL can be evicted.
- Memory footprint: 100,000 entries × ~100 bytes ≈ 10 MB — negligible.
- Multi-engine deployments: each engine instance has its own nonce store. A token could theoretically be replayed once per engine partition. Kafka partition assignment means the same command key is always routed to the same consumer, so in practice this is not exploitable. If stronger guarantees are needed in future, a shared Redis nonce store can replace the local cache — but this is not required now.

**Config flag:**
```properties
# Default: true when envelope validation is enabled — strongly recommended
taktx.security.envelope-validation.nonce-check.enabled=true
```

### Audit log

Regardless of whether validation is enabled, if an `X-TaktX-Authorization` header is present:
- Extract the `auditId` claim (even without full signature verification, treat it as informational).
- Include `auditId` in engine execution events / audit log entries for traceability.

---

## 7. Token Validator — Reusable Implementation

The ingester already has a complete, tested implementation. **Copy or extract to shared module:**

### `AuthorizationTokenValidator.java`

```java
@ApplicationScoped
public class AuthorizationTokenValidator {

    @Inject PublicKeyProvider publicKeyProvider;       // Platform Service key
    @Inject EngineKeyRegistry engineKeyRegistry;       // engine instance keys

    public TokenClaims validateToken(String token) throws AuthorizationTokenException {
        if (token == null || token.isBlank()) {
            throw new AuthorizationTokenException("Authorization token is required");
        }
        try {
            // Step 1: read issuer without verifying — determines which key to use
            PublicKey verificationKey = resolveKey(token);

            // Step 2: full validation with the correct key
            Claims claims = Jwts.parser()
                .verifyWith(verificationKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

            TokenClaims result = new TokenClaims();
            result.setUserId(claims.getSubject());
            result.setIssuer(claims.getIssuer());
            result.setAction(claims.get("action", String.class));
            result.setNamespaceId(UUID.fromString(claims.get("namespaceId", String.class)));
            result.setProcessDefinitionId(claims.get("processDefinitionId", String.class));
            result.setVersion(claims.get("version", Integer.class));
            result.setAuditId(claims.get("auditId", String.class));
            result.setTrigger(claims.get("trigger", String.class)); // null for platform tokens
            result.setIssuedAt(claims.getIssuedAt().getTime());
            result.setExpiresAt(claims.getExpiration().getTime());
            return result;

        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            throw new AuthorizationTokenException("Authorization token has expired", e);
        } catch (io.jsonwebtoken.SignatureException e) {
            throw new AuthorizationTokenException("Authorization token signature is invalid", e);
        } catch (Exception e) {
            throw new AuthorizationTokenException(
                "Failed to validate authorization token: " + e.getMessage(), e);
        }
    }

    private PublicKey resolveKey(String token) throws AuthorizationTokenException {
        // Parse the header+payload without signature verification to extract iss
        String issuer = Jwts.parser().unsecured().build()
            .parseUnsecuredClaims(unsignedForm(token))
            .getPayload()
            .getIssuer();

        if ("taktx-platform-service".equals(issuer)) {
            return publicKeyProvider.getPlatformKey();
        }
        if (issuer != null && issuer.startsWith("taktx-engine:")) {
            String engineId = issuer.substring("taktx-engine:".length());
            PublicKey key = engineKeyRegistry.getKey(engineId);
            if (key == null) throw new AuthorizationTokenException(
                "Unknown engine issuer: " + engineId + " — not in registry");
            return key;
        }
        throw new AuthorizationTokenException("Unknown token issuer: " + issuer);
    }

    /** Strip the signature to get an unsigned JWT for iss extraction. */
    private String unsignedForm(String token) {
        int lastDot = token.lastIndexOf('.');
        return token.substring(0, lastDot + 1);
    }
}
```

### `PublicKeyProvider.java`

The engine's root of trust is the operator-supplied environment variable. The config topic is used only for rotation once the root is established.

```java
@ApplicationScoped
public class PublicKeyProvider {

    // Primary: operator must supply this when envelope validation is enabled.
    // This is the root of trust — cannot be tampered with via Kafka.
    @ConfigProperty(name = "taktx.platform.public-key")
    Optional<String> publicKeyBase64Env;

    @ConfigProperty(name = "taktx.security.envelope-validation.enabled")
    boolean envelopeValidationEnabled;

    @Getter private volatile PublicKey publicKey;
    private volatile boolean establishedFromEnv = false;

    void onStart(@Observes StartupEvent ev) {
        if (!envelopeValidationEnabled) return;

        if (publicKeyBase64Env.isPresent()) {
            loadFromBase64(publicKeyBase64Env.get().trim());
            establishedFromEnv = true;
            log.info("✅ Root of trust established from TAKTX_PLATFORM_PUBLIC_KEY");
        } else {
            throw new RuntimeException(
                "taktx.security.envelope-validation.enabled=true but " +
                "TAKTX_PLATFORM_PUBLIC_KEY is not set. " +
                "The engine cannot safely trust the config topic without an " +
                "operator-established root of trust. " +
                "Set TAKTX_PLATFORM_PUBLIC_KEY to the Base64 DER value from " +
                "GET /api/public-key on the Platform Service.");
        }
    }

    /**
     * Called by the config topic consumer when a rotation message arrives.
     * Only accepted if a valid rotation proof (signed by the current trusted key) is present.
     * Never called unconditionally — the consumer must verify the proof first.
     */
    public void rotateKey(String newBase64) {
        loadFromBase64(newBase64);
        log.info("✅ Public key rotated via config topic (rotation proof verified)");
    }

    public void loadFromBase64(String base64) {
        try {
            this.publicKey = parsePublicKey(base64.trim());
        } catch (Exception e) {
            throw new RuntimeException("Invalid public key", e);
        }
    }

    public boolean isReady() { return publicKey != null; }

    private PublicKey parsePublicKey(String base64) throws Exception {
        byte[] keyBytes = Base64.getDecoder().decode(base64);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
        return KeyFactory.getInstance("RSA").generatePublic(spec);
    }
}
```

### Required dependency

```
io.jsonwebtoken:jjwt-api:0.12.5
io.jsonwebtoken:jjwt-impl:0.12.5   (runtime)
io.jsonwebtoken:jjwt-jackson:0.12.5 (runtime)
```

---

## 8. Configuration Reference

### Engine (new properties)

```properties
# Master switch — default false, backward compatible
taktx.security.envelope-validation.enabled=false

# If true, reject commands with expired tokens (default: warn-only)
taktx.security.envelope-validation.reject-expired=false

# Nonce check — reject replayed auditIds (default: true when validation enabled)
taktx.security.envelope-validation.nonce-check.enabled=true

# Fallback: supply public key directly when no Platform Service / config topic present
# (base64-encoded DER, no PEM headers — same format as config topic value)
# TAKTX_PLATFORM_PUBLIC_KEY=MIIBIjAN...

# Config topic name (default shown — engine team to confirm)
taktx.platform.config-topic=taktx-platform-config
```

### Docker Compose additions (when enabled)

```yaml
taktx-engine:
  environment:
    TAKTX_SECURITY_ENVELOPE_VALIDATION_ENABLED: "true"
    # Required — obtain once from: GET http://taktx-platform-service:8080/api/public-key
    # This is the root of trust. Set it as a secret, not hardcoded in docker-compose.yaml.
    TAKTX_PLATFORM_PUBLIC_KEY: "${TAKTX_PLATFORM_PUBLIC_KEY}"
```

### Ingester (new properties — console repo)

```properties
# Config topic to publish signing key and license to
taktx.platform.config-topic=taktx-platform-config

# How often to re-fetch the public key from Platform Service and re-publish if changed
# (handles key rotation without ingester restart)
taktx.platform.public-key-refresh-interval=1h
```

---

## 9. End-to-End Flow (when enabled)

```
Platform Service (HTTP — no Kafka connection)
  ├─ Generates / loads RSA key pair on startup
  └─ Exposes GET /api/public-key (unauthenticated, Base64 DER)
                    │
                    │  (1) Operator reads key once at deploy time → sets TAKTX_PLATFORM_PUBLIC_KEY
                    │      on engine (Kubernetes Secret / .env). This is the root of trust.
                    │
                    │  (2) HTTP fetch on startup + periodic refresh (ingester only)
                    ▼
             Ingester (PublicKeyFetcher.java)
               ├─ Fetches public key via GET /api/public-key
               └─ Publishes "signing.public-key" → taktx-platform-config (for rotation only)
                                                          │
                                             ┌────────────┘
                                             ▼
                                    TaktX Engine (startup)
                                      ├─ Loads root of trust from TAKTX_PLATFORM_PUBLIC_KEY (env var)
                                      ├─ Consumes taktx-platform-config for license + non-security config
                                      ├─ On "signing.public-key" record: only accepted if rotation
                                      │   proof header is signed by currently trusted key
                                      └─ Continues consuming topic for live rotation

──────────────────────────────────────────────────────────────────

User (browser)
  │
  ▼
Platform Service (BFF)
  ├─ Validates Keycloak JWT
  ├─ Checks process permission (role + definition + version)
  ├─ Signs authorization token (RS256, 5min TTL)
  │    claims: sub, action=START, namespaceId, processDefinitionId, version, auditId
  └─ Calls ingester REST endpoint with token in X-TaktX-Authorization header
       │
       ▼
  Ingester (DefinitionResource / InstanceResource)
     ├─ Validates token signature and claims against request parameters
     ├─ authToken now in scope
     └─ taktClient.startProcess(defId, version, vars, authToken)
                                                            │
                                                            ▼
                                                   TaktXClient
                                                     ├─ Builds Kafka ProducerRecord
                                                     ├─ Adds header: X-TaktX-Authorization = authToken
                                                     └─ Sends to Kafka command topic
                                                                │
                                                                ▼
                                                       TaktX Engine (Kafka consumer)
                                                         ├─ Reads X-TaktX-Authorization header
                                                         ├─ [if enabled] Validates RS256 signature
                                                         │    using key from TAKTX_PLATFORM_PUBLIC_KEY
                                                         ├─ [if enabled] Validates claims + nonce
                                                         ├─ Extracts auditId for execution events
                                                         └─ Executes command
```

---

## 10. Implementation Checklist

### TaktX Client library

- [ ] Add `signedEnvelope` parameter to `startProcess` (optional / nullable, backward-compatible overload)
- [ ] Add `signedEnvelope` parameter to `abortElementInstance` (optional / nullable, backward-compatible overload)
- [ ] If `signedEnvelope != null`, add Kafka header `X-TaktX-Authorization` to the `ProducerRecord`
- [ ] Existing no-envelope overloads delegate with `null` — no behavior change

### TaktX Engine

- [ ] Add `taktx.security.envelope-validation.enabled` config flag (default `false`)
- [ ] Add `taktx.security.envelope-validation.reject-expired` config flag (default `false`)
- [ ] Add `taktx.security.envelope-validation.nonce-check.enabled` config flag (default `true`)
- [ ] Add `PublicKeyProvider` — on startup, load public key from `TAKTX_PLATFORM_PUBLIC_KEY` env var (required when validation enabled); fail fast with clear error if not set
- [ ] Add config topic consumer — on startup, consume `taktx-platform-config` to end-of-partition for license and non-security config; on `signing.public-key` record, require valid `X-TaktX-Rotation-Proof` header signed by currently trusted key before applying rotation
- [ ] Add `AuthorizationTokenValidator` — validates JWT RS256 signature, expiry, and extracts claims using `PublicKeyProvider`
- [ ] Add `NonceStore` — in-memory Caffeine cache keyed by `auditId`, 10-minute expiry, 100K max entries
- [ ] In command consumer: extract `X-TaktX-Authorization` header on every consumed record
- [ ] [when enabled] Validate envelope; reject (dead-letter or skip) if invalid/missing
- [ ] [when enabled] Match claims to command: action, processDefinitionId, version
- [ ] [when enabled + nonce-check] Reject if `auditId` already in NonceStore; otherwise record it
- [ ] [always, when header present] Attach `auditId` to execution events / audit log

### Ingester — console repo (config topic publishing + 2-line call site change)

- [ ] After `PublicKeyFetcher` loads the public key on startup, publish `signing.public-key` to `taktx-platform-config` topic
- [ ] On periodic refresh (configurable, default 1 hour), re-fetch from `GET /api/public-key` and re-publish if fingerprint has changed
- [ ] `DefinitionResource.java`: pass `authToken` as 4th arg to `taktClient.startProcess(...)`
- [ ] `InstanceResource.java`: pass `authToken` as 2nd arg to `taktClient.abortElementInstance(...)`

> **Platform Service requires no changes** — it already exposes `GET /api/public-key` correctly and has no Kafka connection.

### Open design question — engine team to confirm

- [ ] Does the engine already have a stable `engineInstanceId`? If not, a UUID generated at first startup and persisted is sufficient
- [ ] Is the engine key pair persisted across restarts? Recommended: yes — avoids all peers re-consuming the registry on every restart
- [ ] Confirm preference for the `trigger` claim field name (e.g. `trigger`, `reason`, `origin`)

### Future hardening (not required for initial implementation)

- [ ] Add `processInstanceId` to CANCEL tokens (Platform Service token signer + ingester + engine claim matching) — closes the theoretical window where a captured CANCEL token is used against a different instance within its TTL

- [ ] Grant ingester Kafka principal `WRITE` on `taktx-platform-config` topic and command topics
- [ ] Grant engine Kafka principal `READ` on `taktx-platform-config` topic and command topics
- [ ] Deny all other principals `WRITE` on command topics and `taktx-platform-config`
- [ ] Document the required ACL configuration in deployment guide

---

## 11. Security Properties Achieved

| Property | Before | After (enabled) |
|---|---|---|
| Commands can only be issued by authorized users | ✅ (ingester enforces) | ✅ (engine also enforces on user-originated commands) |
| A compromised ingester cannot forge permissions | ❌ | ✅ (token signed by Platform Service, not ingester) |
| A direct Kafka producer bypass is rejected | ❌ | ✅ (no valid signed token = rejected on user command topic) |
| A captured valid token can be replayed | ❌ | ✅ (`auditId` nonce check rejects replays within TTL) |
| Wrong action/definition escalation | ❌ | ✅ (claim-to-command matching) |
| Audit trail in engine events | ❌ | ✅ (`auditId` from token) |
| Rogue TaktXClient with Kafka access | ⚠️ | ✅ (nonce check) + ✅ (Kafka ACLs — deployment layer) |
| Existing bare-engine deployments break | N/A | ✅ (feature-flagged, default off) |
| **Engine-internal commands authenticated** | ❌ | ✅ Engine signs its own tokens with `iss: "taktx-engine:<id>"` — same validation path, different key (section 0c) |
| **Fake event injection** (write to events topic) | ❌ | ✅ Same engine token mechanism covers events — `iss` determines key, signature proves origin (section 0b+0c unified) |













































