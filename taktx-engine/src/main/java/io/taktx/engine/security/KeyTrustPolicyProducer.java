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
 * <p>Policy selection is startup-static and keyed solely on {@code TAKTX_PLATFORM_PUBLIC_KEY}:
 *
 * <ul>
 *   <li><b>Not set</b> → {@link OpenKeyTrustPolicy} (OPEN mode — declared role is accepted at face
 *       value; relies on Kafka ACLs).
 *   <li><b>Set to a valid base64 RSA DER key</b> → {@link AnchoredKeyTrustPolicy} (ANCHORED mode —
 *       every key in {@code taktx-signing-keys} must carry a valid platform countersignature).
 * </ul>
 *
 * <p>When ANCHORED mode is detected (platform key present), this producer also validates that the
 * remaining anchored prerequisites are configured and fails fast if they are not — refusing to
 * start is preferable to starting silently in a misconfigured state.
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
      log.warn(
          "TAKTX_PLATFORM_PUBLIC_KEY not configured — operating in OPEN mode"
              + " (OpenKeyTrustPolicy: declared key roles are accepted at face value)."
              + " This mode relies on Kafka ACLs to protect taktx-signing-keys and is intended"
              + " for local/community use, not production.");
      return new OpenKeyTrustPolicy();
    }

    try {
      byte[] keyBytes = Base64.getDecoder().decode(platformKeyBase64);
      PublicKey rootKey =
          KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(keyBytes));

      // Anchored intent detected — fail fast on missing prerequisites.
      // Mode is startup-static; a running anchored engine is always fully configured.
      String sourceType = config.getSigningIdentitySourceType();
      if ("generated".equalsIgnoreCase(sourceType) || sourceType == null || sourceType.isBlank()) {
        throw new IllegalStateException(
            "ANCHORED mode requires TAKTX_SIGNING_IDENTITY_SOURCE=file or =env; "
                + "generated engine signing keys change on every restart and cannot be pre-signed. "
                + "Set TAKTX_SIGNING_IDENTITY_SOURCE and provide TAKTX_ENGINE_KEY_REGISTRATION_SIGNATURE.");
      }
      if (config.getEngineKeyRegistrationSignature() == null
          || config.getEngineKeyRegistrationSignature().isBlank()) {
        throw new IllegalStateException(
            "ANCHORED mode requires TAKTX_ENGINE_KEY_REGISTRATION_SIGNATURE "
                + "(the engine's own signing key must be countersigned by the platform root key). "
                + "Generate this value with scripts/generate_trust_anchor.sh --sign.");
      }

      log.info(
          "✅ TAKTX_PLATFORM_PUBLIC_KEY configured — ANCHORED mode active"
              + " (AnchoredKeyTrustPolicy: all keys on taktx-signing-keys require a valid"
              + " platform countersignature)");
      return new AnchoredKeyTrustPolicy(rootKey);

    } catch (IllegalStateException e) {
      throw e; // pass through our own fail-fast errors
    } catch (Exception e) {
      throw new IllegalStateException(
          "Failed to parse TAKTX_PLATFORM_PUBLIC_KEY as an RSA public key: " + e.getMessage(), e);
    }
  }
}
