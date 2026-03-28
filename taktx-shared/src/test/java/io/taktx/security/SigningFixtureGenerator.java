/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.security;

import io.taktx.dto.InstanceUpdateDTO;
import io.taktx.dto.ProcessInstanceUpdateDTO;
import io.taktx.dto.ScopeDTO;
import io.taktx.serdes.JsonSerializer;
import io.taktx.serdes.SigningSerializer;
import java.security.KeyPair;
import java.util.Base64;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.Test;

/**
 * One-shot generator: run this test once, copy the printed values into {@code
 * SigningRoundTripTest.regression_liveCapturedBytes_signatureVerifies()}.
 *
 * <p>It produces a deterministically-signed fixture using our own signing stack so the regression
 * test verifies real bytes, not fabricated ones.
 */
class SigningFixtureGenerator {

  private static final String TOPIC = "default.instance-update";
  private static final String KEY_ID = "engine-key-1";

  @Test
  void generateFixture() {
    KeyPair kp = SigningKeyGenerator.generate();
    String privateKeyBase64 = SigningKeyGenerator.encodePrivateKey(kp.getPrivate());
    String publicKeyBase64 = SigningKeyGenerator.encodePublicKey(kp.getPublic());

    SigningServiceHolder.set(
        payload -> {
          try {
            byte[] sig = Ed25519Service.sign(payload, privateKeyBase64);
            return KEY_ID + "." + Base64.getEncoder().encodeToString(sig);
          } catch (Exception e) {
            return null;
          }
        });

    try {
      // Build a realistic ProcessInstanceUpdateDTO
      ScopeDTO scope = new ScopeDTO();
      InstanceUpdateDTO dto =
          new ProcessInstanceUpdateDTO(null, null, null, null, scope, null, null, null);

      // Serialize + sign exactly as the engine does
      RecordHeaders headers = new RecordHeaders();
      byte[] bytes;
      try (JsonSerializer<InstanceUpdateDTO> delegate =
          new JsonSerializer<>(InstanceUpdateDTO.class) {}) {
        SigningSerializer<InstanceUpdateDTO> signer = new SigningSerializer<>(delegate);
        bytes = signer.serialize(TOPIC, headers, dto);
      }

      // Extract the header value
      byte[] headerBytes = headers.lastHeader("X-TaktX-Signature").value();
      String headerValue = new String(headerBytes, java.nio.charset.StandardCharsets.UTF_8);

      // Verify before printing so we know the fixture is self-consistent
      int dot = headerValue.indexOf('.');
      byte[] sigBytes = Base64.getDecoder().decode(headerValue.substring(dot + 1));
      boolean ok = Ed25519Service.verify(bytes, sigBytes, publicKeyBase64);
      System.out.println("=== SELF-CHECK: " + (ok ? "PASS" : "FAIL") + " ===");

      // Print copy-paste ready Java literals
      System.out.println(
          "\n// ── paste into SigningRoundTripTest.regression_liveCapturedBytes_signatureVerifies ──");
      System.out.println("byte[] liveBytes = " + toJavaByteArrayLiteral(bytes) + ";");
      System.out.println("String liveHeaderValue = \"" + headerValue + "\";");
      System.out.println("String livePublicKey = \"" + publicKeyBase64 + "\";");
    } finally {
      SigningServiceHolder.clear();
    }
  }

  private static String toJavaByteArrayLiteral(byte[] bytes) {
    StringBuilder sb = new StringBuilder("{");
    for (int i = 0; i < bytes.length; i++) {
      if (i > 0) sb.append(", ");
      if (i % 16 == 0) sb.append("\n      ");
      sb.append(bytes[i]);
    }
    sb.append("\n    }");
    return sb.toString();
  }
}
