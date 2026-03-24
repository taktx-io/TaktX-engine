#!/usr/bin/env bash
#
# TaktX - A high-performance BPMN engine
# Copyright (c) 2025 Eric Hendriks All rights reserved.
# This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
# Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
# For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
#

# =============================================================================
# generate_trust_anchor.sh — TaktX root-trust-chain key management utility
# =============================================================================
#
# This script manages the platform root RSA key pair and generates the
# registration signatures required for TaktX anchored mode.
#
# BACKGROUND
# ----------
# When TAKTX_PLATFORM_PUBLIC_KEY is set on the engine, AnchoredKeyTrustPolicy
# is activated. Every key published to taktx-signing-keys (both ENGINE-role and
# CLIENT/worker-role) must carry a cryptographic countersignature produced by
# the platform root private key. Without a valid countersignature, the engine
# rejects all commands signed by that key.
#
# MODES
# -----
#   --init        Generate a new platform RSA 2048 root key pair.
#                 Output:
#                   docker/signing/platform-private.pem  (keep secret!)
#                   docker/signing/platform-public.b64   (= TAKTX_PLATFORM_PUBLIC_KEY)
#
#   --sign        Sign a key directory (engine or worker) to produce a
#                 registration signature environment-variable value.
#                 Required flags: --key-dir, --owner, --role
#                 Output: prints the env-var name and base64 signature to stdout.
#
#   --show-pubkey Print TAKTX_PLATFORM_PUBLIC_KEY value from an existing
#                 platform-public.b64 file.
#
# USAGE EXAMPLES
# --------------
#   # 1. Generate the platform root key pair (run once per deployment)
#   scripts/generate_trust_anchor.sh --init
#
#   # 2. Create engine signing key files (if not yet done)
#   KEY_ID="engine-prod-1"
#   openssl genpkey -algorithm Ed25519 -outform DER | base64 > docker/signing/engine/private-key.b64
#   openssl pkey -in <(base64 -d docker/signing/engine/private-key.b64) -pubout -outform DER | base64 > docker/signing/engine/public-key.b64
#   echo "$KEY_ID" > docker/signing/engine/key-id
#
#   # 3. Sign the engine key
#   scripts/generate_trust_anchor.sh --sign \
#     --key-dir docker/signing/engine \
#     --owner engine \
#     --role ENGINE
#   # → copy the printed TAKTX_ENGINE_KEY_REGISTRATION_SIGNATURE value into
#   #   docker-compose-full.yaml (x-taktx-anchored-engine-trust-env block)
#
#   # 4. Create a worker signing key and sign it
#   mkdir -p docker/signing/worker-billing
#   KEY_ID="worker-billing-1"
#   openssl genpkey -algorithm Ed25519 -outform DER | base64 > docker/signing/worker-billing/private-key.b64
#   openssl pkey -in <(base64 -d docker/signing/worker-billing/private-key.b64) -pubout -outform DER | base64 > docker/signing/worker-billing/public-key.b64
#   echo "$KEY_ID" > docker/signing/worker-billing/key-id
#
#   scripts/generate_trust_anchor.sh --sign \
#     --key-dir docker/signing/worker-billing \
#     --owner worker-billing \
#     --role CLIENT
#   # → copy the printed TAKTX_SIGNING_REGISTRATION_SIGNATURE value into the
#   #   worker service environment block
#
# CANONICAL PAYLOAD FORMAT
# ------------------------
# The registration signature is SHA256withRSA (PKCS#1 v1.5) over the pipe-delimited
# UTF-8 string:
#
#   keyId|publicKeyBase64|algorithm|owner|role
#
# This matches exactly what io.taktx.security.SigningKeyRegistrar.computeCanonicalPayload()
# produces in Java. The algorithm field is always "Ed25519" for engine and worker keys.
#
# SECURITY NOTES
# --------------
# * docker/signing/platform-private.pem MUST remain secret — never commit it to version
#   control, never mount it into containers. It is only needed when generating signatures.
# * TAKTX_PLATFORM_PUBLIC_KEY (from platform-public.b64) is not secret and is safe to
#   embed in Docker Compose files, Kubernetes ConfigMaps, etc.
# * Rotate the platform root key by repeating --init and re-signing all engine/worker keys.
# * All key files are written with mode 0600 to limit read access.
#
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
SIGNING_DIR="$REPO_ROOT/docker/signing"
PLATFORM_PRIVATE_PEM="$SIGNING_DIR/platform-private.pem"
PLATFORM_PUBLIC_B64="$SIGNING_DIR/platform-public.b64"

# ── Helpers ───────────────────────────────────────────────────────────────────

usage() {
  echo "Usage:"
  echo "  $0 --init"
  echo "  $0 --sign --key-dir <dir> --owner <owner> --role <ENGINE|CLIENT|PLATFORM>"
  echo "  $0 --show-pubkey"
  echo ""
  echo "Run '$0' with no arguments for full documentation."
  exit 1
}

require_tool() {
  if ! command -v "$1" &>/dev/null; then
    echo "ERROR: '$1' is required but not installed." >&2
    exit 1
  fi
}

require_tool openssl
require_tool base64

# ── --init mode ───────────────────────────────────────────────────────────────

init_platform_key() {
  echo "==> Generating platform RSA 2048 root key pair..."

  if [[ -f "$PLATFORM_PRIVATE_PEM" ]]; then
    echo "WARNING: $PLATFORM_PRIVATE_PEM already exists."
    read -r -p "Overwrite? This will invalidate ALL existing registration signatures. [y/N] " confirm
    if [[ "$confirm" != "y" && "$confirm" != "Y" ]]; then
      echo "Aborted."
      exit 0
    fi
  fi

  mkdir -p "$SIGNING_DIR"

  # Generate RSA 2048 private key (PKCS#8 PEM format)
  openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 \
    -out "$PLATFORM_PRIVATE_PEM" 2>/dev/null
  chmod 0600 "$PLATFORM_PRIVATE_PEM"

  # Export the public key as base64-encoded DER (X.509 SubjectPublicKeyInfo)
  # This is the format expected by TAKTX_PLATFORM_PUBLIC_KEY and Java's
  # KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(bytes))
  openssl rsa -in "$PLATFORM_PRIVATE_PEM" -pubout -outform DER 2>/dev/null \
    | base64 | tr -d '\n' > "$PLATFORM_PUBLIC_B64"
  chmod 0600 "$PLATFORM_PUBLIC_B64"

  echo ""
  echo "✅ Platform root key generated:"
  echo "   Private key : $PLATFORM_PRIVATE_PEM  (KEEP SECRET — never commit or mount)"
  echo "   Public key  : $PLATFORM_PUBLIC_B64"
  echo ""
  echo "Set this as TAKTX_PLATFORM_PUBLIC_KEY on the engine:"
  echo ""
  echo "  TAKTX_PLATFORM_PUBLIC_KEY=$(cat "$PLATFORM_PUBLIC_B64")"
  echo ""
  echo "Next step: sign each engine and worker key with --sign."
}

# ── --sign mode ───────────────────────────────────────────────────────────────

sign_key() {
  local key_dir="$1"
  local owner="$2"
  local role="$3"

  # Validate role
  if [[ "$role" != "ENGINE" && "$role" != "CLIENT" && "$role" != "PLATFORM" ]]; then
    echo "ERROR: --role must be ENGINE, CLIENT, or PLATFORM" >&2
    exit 1
  fi

  # Resolve env-var name by role
  local env_var_name
  if [[ "$role" == "ENGINE" ]]; then
    env_var_name="TAKTX_ENGINE_KEY_REGISTRATION_SIGNATURE"
  else
    env_var_name="TAKTX_SIGNING_REGISTRATION_SIGNATURE"
  fi

  # Validate key directory
  if [[ ! -d "$key_dir" ]]; then
    echo "ERROR: Key directory '$key_dir' does not exist." >&2
    exit 1
  fi

  local key_id_file="$key_dir/key-id"
  local pub_key_file="$key_dir/public-key.b64"

  if [[ ! -f "$key_id_file" ]]; then
    echo "ERROR: '$key_id_file' not found. Generate the Ed25519 key files first." >&2
    exit 1
  fi
  if [[ ! -f "$pub_key_file" ]]; then
    echo "ERROR: '$pub_key_file' not found. Generate the Ed25519 key files first." >&2
    exit 1
  fi

  if [[ ! -f "$PLATFORM_PRIVATE_PEM" ]]; then
    echo "ERROR: Platform private key not found at '$PLATFORM_PRIVATE_PEM'." >&2
    echo "       Run: $0 --init" >&2
    exit 1
  fi

  local key_id
  key_id=$(tr -d '[:space:]' < "$key_id_file")

  local pub_key_b64
  pub_key_b64=$(tr -d '[:space:]' < "$pub_key_file")

  local algorithm="Ed25519"

  # Build the canonical payload — must exactly match
  # io.taktx.security.SigningKeyRegistrar.computeCanonicalPayload():
  #   keyId|publicKeyBase64|algorithm|owner|role
  local canonical_payload
  canonical_payload=$(printf '%s|%s|%s|%s|%s' \
    "$key_id" "$pub_key_b64" "$algorithm" "$owner" "$role")

  echo "==> Signing key registration..."
  echo "    Key ID    : $key_id"
  echo "    Owner     : $owner"
  echo "    Role      : $role"
  echo "    Algorithm : $algorithm"
  echo "    Canonical : $canonical_payload" | cut -c1-120
  echo ""

  # Sign with SHA256withRSA (PKCS#1 v1.5) — matches Java Signature.getInstance("SHA256withRSA")
  local registration_signature
  registration_signature=$(printf '%s' "$canonical_payload" \
    | openssl dgst -sha256 -sign "$PLATFORM_PRIVATE_PEM" \
    | base64 | tr -d '\n')

  echo "✅ Registration signature generated."
  echo ""
  echo "Add the following environment variable to the container / service:"
  echo ""
  echo "  $env_var_name=$registration_signature"
  echo ""
  echo "For Docker Compose (docker-compose-full.yaml), paste it into the"
  if [[ "$role" == "ENGINE" ]]; then
    echo "x-taktx-anchored-engine-trust-env block."
  else
    echo "x-taktx-anchored-worker-trust-env block (or the worker's environment section)."
  fi
}

# ── --show-pubkey mode ────────────────────────────────────────────────────────

show_pubkey() {
  if [[ ! -f "$PLATFORM_PUBLIC_B64" ]]; then
    echo "ERROR: '$PLATFORM_PUBLIC_B64' not found. Run: $0 --init" >&2
    exit 1
  fi
  echo "TAKTX_PLATFORM_PUBLIC_KEY=$(cat "$PLATFORM_PUBLIC_B64")"
}

# ── Argument parsing ──────────────────────────────────────────────────────────

if [[ $# -eq 0 ]]; then
  # Print full usage from the header block
  head -n 90 "$0" | tail -n +2 | sed 's/^# \?//'
  exit 0
fi

MODE=""
KEY_DIR=""
OWNER=""
ROLE=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --init)       MODE="init"; shift ;;
    --sign)       MODE="sign"; shift ;;
    --show-pubkey) MODE="show-pubkey"; shift ;;
    --key-dir)    KEY_DIR="$2"; shift 2 ;;
    --owner)      OWNER="$2"; shift 2 ;;
    --role)       ROLE="$2"; shift 2 ;;
    *) echo "Unknown option: $1" >&2; usage ;;
  esac
done

case "$MODE" in
  init)
    init_platform_key
    ;;
  sign)
    if [[ -z "$KEY_DIR" || -z "$OWNER" || -z "$ROLE" ]]; then
      echo "ERROR: --sign requires --key-dir, --owner, and --role." >&2
      usage
    fi
    sign_key "$KEY_DIR" "$OWNER" "$ROLE"
    ;;
  show-pubkey)
    show_pubkey
    ;;
  *)
    usage
    ;;
esac

