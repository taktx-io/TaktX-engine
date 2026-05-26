/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class WorkerSigningContextTest {

  @AfterEach
  void clearSystemProperties() {
    System.clearProperty("taktx.signing.private-key");
    System.clearProperty("taktx.signing.public-key");
    System.clearProperty("taktx.signing.key-id");
  }

  @Test
  void of_requiresPrivateKey() {
    assertThatThrownBy(() -> WorkerSigningContext.of("   ", "public", "key-id"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("privateKeyBase64");
  }

  @Test
  void of_requiresKeyId() {
    assertThatThrownBy(() -> WorkerSigningContext.of("private", "public", "   "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("keyId");
  }

  @Test
  void of_withoutPublicKey_keepsPublicationOptional() {
    WorkerSigningContext context = WorkerSigningContext.of("private-123", "worker-key");

    assertThat(context.getPrivateKeyBase64()).isEqualTo("private-123");
    assertThat(context.getPublicKeyBase64()).isNull();
    assertThat(context.getKeyId()).isEqualTo("worker-key");
  }

  @Test
  void fromEnvironment_returnsNullWhenNoPrivateKeyConfigured() {
    assertThat(WorkerSigningContext.fromEnvironment()).isNull();
  }

  @Test
  void fromEnvironment_readsSystemPropertiesFallback() {
    System.setProperty("taktx.signing.private-key", "private-123");
    System.setProperty("taktx.signing.public-key", "public-123");
    System.setProperty("taktx.signing.key-id", "worker-key");

    WorkerSigningContext context = WorkerSigningContext.fromEnvironment();

    assertThat(context)
        .isNotNull()
        .extracting(
            WorkerSigningContext::getPrivateKeyBase64,
            WorkerSigningContext::getPublicKeyBase64,
            WorkerSigningContext::getKeyId)
        .containsExactly("private-123", "public-123", "worker-key");
  }

  @Test
  void fromEnvironment_requiresKeyIdWhenPrivateKeyPresent() {
    System.setProperty("taktx.signing.private-key", "private-123");

    assertThatThrownBy(WorkerSigningContext::fromEnvironment)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("TAKTX_SIGNING_PRIVATE_KEY")
        .hasMessageContaining("TAKTX_SIGNING_KEY_ID");
  }
}
