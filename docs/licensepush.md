## Engine team request — L6c: `TaktXClient.publishLicense()`

### Context

Platform Service now pushes the active license to every ingester via
`POST /internal/license` (plain-text License3j signed body). The ingester caches it
in-memory and has a TODO stub waiting for this deliverable:

```java
// in LicenseResource.java (ingester):
// TODO (L6c): once TaktXClient.publishLicense() is available from the engine team,
// forward the license to the engine here:
//   taktXClient.publishLicense(licenseText);
```

The license must reach all engine nodes for all namespaces so they can enforce
`maxKafkaPartitions`, `maxWorkers`, and any other limit flags at runtime — without
requiring an engine restart when a new license is uploaded.

---

### What we need

**Add `publishLicense(String licenseText)` to `TaktXClient`.**

The method should publish the raw License3j-signed license text to the
**`taktx-configuration`** compacted topic (`Topics.CONFIGURATION_TOPIC`), which already
exists in the `Topics` enum and already has a corresponding `Stores.GLOBAL_CONFIGURATION`
KTable in the engine.

#### Suggested signature

```java
/**
 * Publishes the active license to the {@code taktx-configuration} compacted topic.
 *
 * <p>All engine nodes consume this topic as a global KTable
 * ({@code Stores.GLOBAL_CONFIGURATION}). On receiving a record with key {@code "license"},
 * the engine should parse the License3j payload and update its in-memory license state
 * immediately — no restart required.
 *
 * <p>Called by the ingester whenever Platform Service pushes a new or updated license.
 *
 * @param licenseText raw License3j-signed license file content (UTF-8 plain text)
 */
public void publishLicense(String licenseText) { ... }
```

#### Kafka record spec

| Field | Value |
|---|---|
| Topic | `<namespace>.taktx-configuration` (same prefix logic as `publishSigningKey`) |
| Key | `"license"` (string) — compaction retains only the latest record per key |
| Value | `licenseText` as UTF-8 bytes (plain text, no additional encoding) |
| Cleanup policy | COMPACT (already set on the topic) |

Use the same `taktPropertiesHelper`-based producer pattern as `publishSigningKey` /
`SigningKeyRegistrar` so that auth/TLS properties flow through automatically.

---

### What the engine should do on consume

The `GLOBAL_CONFIGURATION` KTable already backs the `taktx-configuration` topic.
When a record with key `"license"` arrives, the engine should:

1. Parse the License3j payload (signature already verified by Platform Service before
   the ingester forwards it — the engine can verify independently if desired, but it is
   not required for beta).
2. Extract the relevant limit fields:
    - `maxKafkaPartitions` (Integer) — enforce on topic/partition creation
    - `maxWorkers` (Integer) — enforce on external task consumer registration
    - `eventSigning` (Boolean) — already enforced via `taktx.security.signing.enabled`
    - `licenseType` (String) — for logging/diagnostics
3. Update the engine's in-memory license state atomically.
4. Log the update: `"License updated from configuration topic: type={} maxPartitions={} maxWorkers={}"`.

The engine does **not** need to re-verify the License3j signature for beta — Platform
Service and the ingester have already validated it before publishing. Full independent
verification can be added post-beta.

---

### License3j field reference

The fields the engine needs to read (all present in the signed license):

```
licenseType=COMMUNITY          # FREE | STANDARD | ENTERPRISE
maxKafkaPartitions=3           # Integer, null = unlimited
maxWorkers=50                  # Integer, null = unlimited
eventSigning=false             # Boolean
customPermissions=false        # Boolean (not enforced by engine, informational)
runwayStorageTier=inmemory     # String: inmemory | persisted | persisted-scalable
deploymentModel=SELF_MANAGED   # String: SELF_MANAGED | SAAS
expiryDate=<epoch-millis>      # Long, null = no expiry
```

Parsing is simple line-by-line `key=value` within the License3j plain-text section
(between `-----BEGIN LICENSE-----` and `-----END LICENSE-----`). The engine already
has the License3j dependency transitively through `taktx-shared`.

---

### Acceptance criteria

- [ ] `TaktXClient.publishLicense(String licenseText)` exists and publishes to
  `<namespace>.taktx-configuration` with key `"license"`
- [ ] Engine's `GLOBAL_CONFIGURATION` KTable processor handles key `"license"`:
  updates `maxKafkaPartitions`, `maxWorkers` in-memory on each record
- [ ] Existing `taktx-configuration` topic and `GLOBAL_CONFIGURATION` store are reused
  — no new topic or store needed
- [ ] Publishing the same license twice is idempotent (compaction ensures one record)
- [ ] Unit test: publish a license record, verify engine state reflects new limits

---

### Integration point (our side)

Once `TaktXClient.publishLicense()` is available, we will uncomment the following in
the ingester's `LicenseResource.java`:

```java
// Remove the TODO and inject TaktXClient:
@Inject TaktXClient taktXClient;

// In receiveLicense():
taktXClient.publishLicense(licenseText);
```

The ingester already receives the license via `POST /internal/license` from Platform
Service on startup and on every license upload — so no further changes on our side
are needed.
