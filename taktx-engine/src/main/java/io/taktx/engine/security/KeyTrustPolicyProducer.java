/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.engine.security;

import io.taktx.engine.config.TaktConfiguration;
import io.taktx.security.AnchoredKeyTrustPolicy;
import io.taktx.security.KeyTrustPolicy;
import io.taktx.security.OpenKeyTrustPolicy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import lombok.extern.slf4j.Slf4j;

/**
 * CDI producer for the engine's key trust policy.
 *
 * <h2>Policy selection</h2>
 *
 * <table border="1">
 *   <tr><th>{@code TAKTX_PLATFORM_PUBLIC_KEY}</th><th>Policy</th><th>Mode</th></tr>
 *   <tr>
 *     <td>Not set (blank)</td>
 *     <td>{@link OpenKeyTrustPolicy}</td>
 *     <td>Community / standalone — declared role is accepted at face value.</td>
 *   </tr>
 *   <tr>
 *     <td>Set to a valid base64 RSA DER key</td>
 *     <td>{@link AnchoredKeyTrustPolicy}</td>
 *     <td>Anchored — every key in {@code taktx-signing-keys} must carry a valid platform
 *         countersignature. Both ENGINE- and CLIENT-role keys are enforced.</td>
 *   </tr>
 * </table>
 *
 * <h2>Startup warnings</h2>
 *
 * <p>When anchored mode is active but {@code TAKTX_ENGINE_KEY_REGISTRATION_SIGNATURE} is blank and
 * the engine signing source is {@code generated}, a {@code WARN} is logged at startup because the
 * engine's own key will be rejected by its own {@link AnchoredKeyTrustPolicy} (a generated key
 * cannot be pre-signed). Switch to {@code file} or {@code env} signing source and provide {@code
 * TAKTX_ENGINE_KEY_REGISTRATION_SIGNATURE}.
 */
@ApplicationScoped
@Slf4j
public class KeyTrustPolicyProducer {

  private final TaktConfiguration config;

  @Inject
  public KeyTrustPolicyProducer(TaktConfiguration config) {
    this.config = config;
  }

  @Produces
  @ApplicationScoped
  public KeyTrustPolicy keyTrustPolicy() {
    String platformKeyBase64 = config.getPlatformPublicKey();

    if (platformKeyBase64 == null || platformKeyBase64.isBlank()) {
      if (config.isSecurityProductionMode()) {
        throw new IllegalStateException(
            "taktx.security.production-mode=true requires taktx.platform.public-key "
                + "(TAKTX_PLATFORM_PUBLIC_KEY); refusing to start in community mode");
      }
      log.warn(
          "TAKTX_PLATFORM_PUBLIC_KEY not configured — operating in community mode"
              + " (OpenKeyTrustPolicy: declared key roles are accepted at face value)."
              + " This mode relies on Kafka ACLs to protect taktx-signing-keys and is intended"
              + " for local/community use, not production.");
      return new OpenKeyTrustPolicy();
    }

    try {
      byte[] keyBytes = Base64.getDecoder().decode(platformKeyBase64);
      PublicKey rootKey =
          KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(keyBytes));

      if (!config.isSecurityProductionMode()) {
        // Warn if the engine's own signing key cannot be pre-signed in anchored mode.
        String sourceType = config.getSigningIdentitySourceType();
        String engineRegistrationSig = config.getEngineKeyRegistrationSignature();
        if ("generated".equalsIgnoreCase(sourceType)
            && (engineRegistrationSig == null || engineRegistrationSig.isBlank())) {
          log.warn(
              "⚠️  Anchored mode is active (TAKTX_PLATFORM_PUBLIC_KEY is set) but"
                  + " TAKTX_SIGNING_IDENTITY_SOURCE=generated and"
                  + " TAKTX_ENGINE_KEY_REGISTRATION_SIGNATURE is blank."
                  + " The engine's own signing key will be rejected by AnchoredKeyTrustPolicy"
                  + " because a generated key changes on every restart and cannot be pre-signed."
                  + " Switch to TAKTX_SIGNING_IDENTITY_SOURCE=file or =env and provide"
                  + " TAKTX_ENGINE_KEY_REGISTRATION_SIGNATURE (see scripts/generate_trust_anchor.sh).");
        }
      }

      log.info(
          "✅ TAKTX_PLATFORM_PUBLIC_KEY configured — operating in anchored mode"
              + " (AnchoredKeyTrustPolicy: all keys on taktx-signing-keys require a valid"
              + " platform countersignature; Kafka ACLs should still restrict who may write"
              + " that topic)");
      return new AnchoredKeyTrustPolicy(rootKey);

    } catch (Exception e) {
      throw new IllegalStateException(
          "Failed to parse TAKTX_PLATFORM_PUBLIC_KEY as an RSA public key: " + e.getMessage(), e);
    }
  }
}
