# Docker Compose signing files

This directory is bind-mounted into containers when using `docker/docker-compose-full.yaml`.

---

## Community mode (default)

No platform root key is required. Key roles are accepted at face value (`OpenKeyTrustPolicy`).
Security relies on Kafka ACLs preventing unauthorized writes to `taktx-signing-keys`.
Use this mode for local/community development only — not for production.

### Engine key files

Create these files before starting Compose:

- `docker/signing/engine/key-id`          — arbitrary unique string, e.g. `engine-prod-1`
- `docker/signing/engine/private-key.b64` — base64 DER Ed25519 PKCS#8 private key
- `docker/signing/engine/public-key.b64`  — base64 DER Ed25519 X.509 public key

Generate with openssl:

```bash
# Generate via a temporary PEM — works on macOS and Linux
openssl genpkey -algorithm Ed25519 -out /tmp/taktx-engine-key.pem

# Private key (PKCS#8 DER, base64)
openssl pkey -in /tmp/taktx-engine-key.pem -outform DER \
  | base64 | tr -d '\n' > docker/signing/engine/private-key.b64

# Public key (X.509 DER, base64)
openssl pkey -in /tmp/taktx-engine-key.pem -pubout -outform DER \
  | base64 | tr -d '\n' > docker/signing/engine/public-key.b64

echo "engine-prod-1" > docker/signing/engine/key-id

rm /tmp/taktx-engine-key.pem
```

### Worker key files

For each worker container, create a separate directory with the same filenames:

- `docker/signing/worker-example/key-id`
- `docker/signing/worker-example/private-key.b64`
- `docker/signing/worker-example/public-key.b64`

### Live key rotation (without restart)

Write updates atomically:

```bash
# Write to a temp file, then move atomically
cp new-private-key.b64 docker/signing/engine/private-key.b64.tmp
mv docker/signing/engine/private-key.b64.tmp docker/signing/engine/private-key.b64
```

Set `TAKTX_SIGNING_FILE_REFRESH_INTERVAL_MS` to control how often the process re-checks the
files (default: `1000` ms).

---

## Anchored mode (root trust chain)

Anchored mode activates `AnchoredKeyTrustPolicy` on the engine. **Every** key published to
`taktx-signing-keys` — both ENGINE-role (engine) and CLIENT-role (worker) keys — must carry
a valid RSA countersignature from the platform root private key. Without it, the engine rejects
all commands signed by that key.

Anchored mode is the recommended production posture, but Kafka ACLs should still restrict who may
write `taktx-signing-keys`.

Enabled by setting `TAKTX_PLATFORM_PUBLIC_KEY` on the engine container.

### Step 1 — Generate the platform root key pair (run once per deployment)

```bash
scripts/generate_trust_anchor.sh --init
```

This creates:
- `docker/signing/platform-private.pem` — **keep secret, never commit or mount into containers**
- `docker/signing/platform-public.b64`  — value of `TAKTX_PLATFORM_PUBLIC_KEY`

### Step 2 — Generate engine key files (if not already done)

See the community mode section above. The engine must use `TAKTX_SIGNING_IDENTITY_SOURCE=file`
(or `=env`) in anchored mode — the `generated` source is incompatible because the key changes
on every restart and cannot be pre-signed.

### Step 3 — Sign the engine key

```bash
scripts/generate_trust_anchor.sh --sign \
  --key-dir docker/signing/engine \
  --owner engine \
  --role ENGINE
```

Copy the printed `TAKTX_ENGINE_KEY_REGISTRATION_SIGNATURE=...` value into
`docker-compose-full.yaml` → `x-taktx-anchored-engine-trust-env.TAKTX_ENGINE_KEY_REGISTRATION_SIGNATURE`.

### Step 4 — Sign each worker key

```bash
scripts/generate_trust_anchor.sh --sign \
  --key-dir docker/signing/worker-example \
  --owner worker-example \
  --role CLIENT
```

Copy the printed `TAKTX_SIGNING_REGISTRATION_SIGNATURE=...` value into the worker service's
environment block (or `x-taktx-anchored-worker-trust-env`).

### Step 5 — Enable anchored mode in docker-compose-full.yaml

1. Set `TAKTX_PLATFORM_PUBLIC_KEY` in `x-taktx-anchored-engine-trust-env` (from Step 1).
2. Set `TAKTX_ENGINE_KEY_REGISTRATION_SIGNATURE` (from Step 3).
3. Uncomment `<<: *taktx-anchored-engine-trust-env` in the `taktx` service block.
4. For each worker: set `TAKTX_SIGNING_REGISTRATION_SIGNATURE` and uncomment
   `<<: *taktx-anchored-worker-trust-env`.

### Re-signing after key rotation

If you rotate an engine or worker key (new Ed25519 key files), re-run the `--sign` command
for that key and update the `REGISTRATION_SIGNATURE` environment variable. No other changes
are needed.

### Canonical payload format

The registration signature is `SHA256withRSA` (PKCS#1 v1.5) over the pipe-delimited UTF-8 string:

```
keyId|publicKeyBase64|algorithm|owner|role
```

This is exactly what `io.taktx.security.SigningKeyRegistrar.computeCanonicalPayload()` produces.
The `algorithm` field is always `Ed25519` for engine and worker keys.

---

## Environment variable summary

| Variable | Set on | Purpose |
|---|---|---|
| `TAKTX_PLATFORM_PUBLIC_KEY` | Engine | Activates anchored mode. Base64 DER RSA public key. |
| `TAKTX_ENGINE_KEY_REGISTRATION_SIGNATURE` | Engine | Countersignature for the engine's own Ed25519 key. |
| `TAKTX_SIGNING_REGISTRATION_SIGNATURE` | Worker | Countersignature for the worker's Ed25519 key. |
| `TAKTX_SIGNING_IDENTITY_SOURCE` | Engine + Worker | `file` or `env` (required in anchored mode). |
