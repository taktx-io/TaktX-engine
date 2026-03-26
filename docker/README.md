# TaktX Docker Compose

The setup is split into composable layers so you can start simple and add complexity only when needed.

---

## Layers at a glance

| Level | File | Signing | Setup needed |
|-------|------|---------|--------------|
| 1 — Minimal | `docker-compose.yaml` *(base)* | In-memory generated | None |
| 2 — Monitoring | `+ docker-compose.monitoring.yml` | ← same | None |
| 3 — File signing | `+ docker-compose.file-signing.yml` | Persistent file key | Generate key files |
| 4 — Anchored | `+ docker-compose.anchored.yml` | File key + root trust | Generate + sign keys |

---

## Level 1 — Minimal (zero setup)

Just Kafka and the TaktX engine. The engine generates a fresh Ed25519 key in memory on every start.

```sh
docker compose up
```

> The signing key is lost on restart. Workers that cache the engine's public key will need to re-fetch it on reconnect. Fine for local development.

---

## Level 2 — With monitoring

Adds **kafka-ui** (`:8085`), **Prometheus** (`:9090`) and **Grafana** (`:3000`).

```sh
docker compose \
  -f docker-compose.yaml \
  -f docker-compose.monitoring.yml \
  up
```

---

## Level 3 — Persistent file-based signing

The engine loads its Ed25519 key from files mounted at `/opt/taktx/signing/engine/`.
The key survives container restarts, so workers stay in sync.

### Generate key files (once)

```sh
# Generate a temporary PEM, then export both keys as base64 DER — works on macOS and Linux
openssl genpkey -algorithm Ed25519 -out /tmp/taktx-engine-key.pem

openssl pkey -in /tmp/taktx-engine-key.pem -outform DER \
  | base64 | tr -d '\n' > docker/signing/engine/private-key.b64

openssl pkey -in /tmp/taktx-engine-key.pem -pubout -outform DER \
  | base64 | tr -d '\n' > docker/signing/engine/public-key.b64

echo "engine-local-1" > docker/signing/engine/key-id

rm /tmp/taktx-engine-key.pem
```

### Start

```sh
docker compose \
  -f docker-compose.yaml \
  -f docker-compose.monitoring.yml \
  -f docker-compose.file-signing.yml \
  up
```

> **Live key rotation** — write updates atomically (`cp + mv`) while the engine is running.
> The refresh interval is controlled by `TAKTX_SIGNING_FILE_REFRESH_INTERVAL_MS` (default: 1000 ms).

---

## Level 4 — Anchored trust chain (root of trust)

The engine's key must be registered against a platform root CA key.
Workers without a valid registration signature are rejected.

**Requires Level 3 (file signing) to be applied as well.**

### Setup

```sh
# 1. Generate the platform root RSA key pair (once per deployment, keep the private key safe)
#    Output: docker/signing/platform-private.pem  ← never commit this
#            docker/signing/platform-public.b64   ← value for TAKTX_PLATFORM_PUBLIC_KEY
scripts/generate_trust_anchor.sh --init

# 2. Generate engine Ed25519 key files (see Level 3 above if not done yet)

# 3. Sign the engine key against the platform root
scripts/generate_trust_anchor.sh \
  --sign \
  --key-dir docker/signing/engine \
  --owner acme-engine \
  --role ENGINE

# 4. Paste the printed values into docker-compose.anchored.yml:
#    TAKTX_PLATFORM_PUBLIC_KEY              ← from docker/signing/platform-public.b64
#    TAKTX_ENGINE_KEY_REGISTRATION_SIGNATURE ← printed by --sign above
```

### Start

```sh
docker compose \
  -f docker-compose.yaml \
  -f docker-compose.monitoring.yml \
  -f docker-compose.file-signing.yml \
  -f docker-compose.anchored.yml \
  up
```

---

## Building from source vs. pulling an image

The base `docker-compose.yaml` builds the engine from your local source using `Dockerfile.jvm`.

To use a pre-built image instead, edit the `taktx` service in `docker-compose.yaml`:

```yaml
# Comment out the build block:
# build:
#   context: ..
#   dockerfile: taktx-engine/Dockerfile.jvm

# Uncomment one of:
image: ghcr.io/taktx-io/taktx-engine:latest
# image: ghcr.io/taktx-io/taktx-engine:0.0.8-alpha-7
```

To use the native image, change `Dockerfile.jvm` → `taktx-engine/Dockerfile.linux-native`.

