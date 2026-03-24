/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */
package io.taktx.security;

import io.taktx.dto.KeyRole;
import io.taktx.dto.SigningKeyDTO;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Base64;

/**
 * Trust policy for deployments with a platform root key (<em>anchored mode</em>).
 *
 * <p>Activated automatically by the engine's {@code KeyTrustPolicyProducer} CDI bean when {@code
 * TAKTX_PLATFORM_PUBLIC_KEY} is set. In all other deployments, {@link OpenKeyTrustPolicy} is used
 * instead (community / standalone mode).
 *
 * <h2>Anchored mode vs community mode</h2>
 *
 * <table border="1">
 *   <tr><th>Mode</th><th>Platform key set?</th><th>Policy</th><th>Enforcement</th></tr>
 *   <tr>
 *     <td>Community</td><td>No</td><td>{@link OpenKeyTrustPolicy}</td>
 *     <td>Honor-system: declared role accepted at face value. Security relies on Kafka ACLs.</td>
 *   </tr>
 *   <tr>
 *     <td>Anchored</td><td>Yes</td><td>{@link AnchoredKeyTrustPolicy}</td>
 *     <td>Cryptographic: every key must carry a valid platform countersignature regardless of
 *         role.</td>
 *   </tr>
 * </table>
 *
 * <h2>Enforcement rules</h2>
 *
 * <p>In anchored mode, a key is trusted if and only if ALL of the following are true:
 *
 * <ol>
 *   <li>Key is not {@code null}
 *   <li>Key status is not {@link SigningKeyDTO.KeyStatus#REVOKED}
 *   <li>{@link SigningKeyDTO#getRegistrationSignature()} is non-null and non-blank
 *   <li>The registration signature verifies with {@code SHA256withRSA} against {@link
 *       SigningKeyRegistrar#computeCanonicalPayload(SigningKeyDTO)} using the platform root key
 *   <li>The key's declared role satisfies the required role via {@link OpenKeyTrustPolicy}
 * </ol>
 *
 * <p>This applies to <strong>all</strong> key roles — both {@link KeyRole#ENGINE} and {@link
 * KeyRole#CLIENT} (worker) keys. There is no partial enforcement: a key without a valid
 * countersignature is rejected, period.
 *
 * <h2>Canonical payload and signature format</h2>
 *
 * <p>The {@link SigningKeyDTO#getRegistrationSignature()} value is the base64-encoded result of an
 * RSA/SHA-256 (PKCS#1 v1.5) signature produced by the platform's root private key over the
 * pipe-delimited UTF-8 canonical payload:
 *
 * <pre>{@code keyId|publicKeyBase64|algorithm|owner|role}</pre>
 *
 * <p>Shell equivalent (operator workflow):
 *
 * <pre>{@code
 * # 1. Compute the canonical payload
 * PAYLOAD=$(printf '%s|%s|%s|%s|%s' \
 *   "$KEY_ID" "$PUBLIC_KEY_BASE64" "$ALGORITHM" "$OWNER" "$ROLE")
 *
 * # 2. Sign with RSA SHA-256 (PKCS#1 v1.5) and base64-encode
 * REGISTRATION_SIGNATURE=$(printf '%s' "$PAYLOAD" \
 *   | openssl dgst -sha256 -sign platform-private.pem \
 *   | base64)
 * }</pre>
 *
 * <p>See {@code scripts/generate_trust_anchor.sh} for the complete operator workflow, including key
 * generation, signing, and environment-variable export for both engine and worker containers.
 *
 * <h2>Error handling</h2>
 *
 * <p>Any exception during signature verification (bad base64, wrong key type, JCA error) causes the
 * key to be treated as untrusted — no exception is propagated. This prevents a malformed signature
 * from crashing Kafka Streams processing threads.
 *
 * <h2>Thread safety</h2>
 *
 * <p>Instances are immutable after construction and safe for concurrent use across all Kafka
 * Streams stream-processing threads.
 */
public class AnchoredKeyTrustPolicy implements KeyTrustPolicy {

  private final PublicKey rootPublicKey;
  private final OpenKeyTrustPolicy rolePolicy = new OpenKeyTrustPolicy();

  /**
   * Constructs an anchored trust policy using the given RSA root public key.
   *
   * @param rootPublicKey the platform RSA public key corresponding to {@code
   *     TAKTX_PLATFORM_PUBLIC_KEY}; used to verify {@link SigningKeyDTO#getRegistrationSignature()}
   *     on every key lookup
   * @throws IllegalArgumentException if {@code rootPublicKey} is {@code null}
   */
  public AnchoredKeyTrustPolicy(PublicKey rootPublicKey) {
    if (rootPublicKey == null) {
      throw new IllegalArgumentException("rootPublicKey must not be null");
    }
    this.rootPublicKey = rootPublicKey;
  }

  /**
   * {@inheritDoc}
   *
   * <p>In anchored mode, a missing or invalid {@code registrationSignature} on the key entry causes
   * immediate rejection — the role-level check is never reached.
   */
  @Override
  public boolean isTrustedForRole(SigningKeyDTO key, KeyRole requiredRole) {
    if (key == null) return false;
    if (key.getStatus() == SigningKeyDTO.KeyStatus.REVOKED) return false;

    String sig = key.getRegistrationSignature();
    if (sig == null || sig.isBlank()) {
      // No countersignature — reject in anchored mode regardless of role.
      return false;
    }

    if (!verifyRegistrationSignature(key, sig)) {
      return false;
    }

    // Countersignature is valid; delegate role-level check.
    return rolePolicy.isTrustedForRole(key, requiredRole);
  }

  /**
   * Verifies the RSA/SHA-256 registration signature over the canonical payload.
   *
   * @param key the signing key whose fields form the canonical payload
   * @param registrationSignatureBase64 the base64-encoded RSA signature to verify
   * @return {@code true} if the signature is valid, {@code false} on any failure
   */
  private boolean verifyRegistrationSignature(
      SigningKeyDTO key, String registrationSignatureBase64) {
    try {
      byte[] canonical = SigningKeyRegistrar.computeCanonicalPayload(key);
      byte[] sigBytes = Base64.getDecoder().decode(registrationSignatureBase64);
      Signature sig = Signature.getInstance("SHA256withRSA");
      sig.initVerify(rootPublicKey);
      sig.update(canonical);
      return sig.verify(sigBytes);
    } catch (Exception e) {
      // Treat any JCA or base64 error as an invalid signature.
      return false;
    }
  }
}
