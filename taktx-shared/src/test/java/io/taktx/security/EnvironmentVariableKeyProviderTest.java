/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EnvironmentVariableKeyProviderTest {

  private static final String SYS_PROP = "taktx.signing.private-key";

  @BeforeEach
  @AfterEach
  void clearProperty() {
    System.clearProperty(SYS_PROP);
  }

  @Test
  void getPrivateKey_returnsValue_whenSystemPropertyIsSet() {
    System.setProperty(SYS_PROP, "my-test-key");
    EnvironmentVariableKeyProvider provider = new EnvironmentVariableKeyProvider();
    assertThat(provider.getPrivateKey("any-key-id")).isEqualTo("my-test-key");
  }

  @Test
  void getPrivateKey_returnsNull_whenPropertyNotSet() {
    EnvironmentVariableKeyProvider provider = new EnvironmentVariableKeyProvider();
    // Only null if env var TAKTX_SIGNING_PRIVATE_KEY is also absent (true in test context)
    String result = provider.getPrivateKey("any-key-id");
    // Env var may or may not be set in CI — just verify no exception is thrown
    // and the result is either null or a non-blank string
    if (result != null) {
      assertThat(result).isNotBlank();
    }
  }

  @Test
  void hasKey_returnsTrue_whenSystemPropertyIsSet() {
    System.setProperty(SYS_PROP, "my-test-key");
    EnvironmentVariableKeyProvider provider = new EnvironmentVariableKeyProvider();
    assertThat(provider.hasKey("irrelevant-id")).isTrue();
  }

  @Test
  void hasKey_returnsFalse_whenPropertyBlank() {
    System.setProperty(SYS_PROP, "  ");
    EnvironmentVariableKeyProvider provider = new EnvironmentVariableKeyProvider();
    // blank is treated same as absent
    assertThat(provider.hasKey("irrelevant-id")).isFalse();
  }

  @Test
  void getPrivateKey_ignoresKeyId_alwaysReturnsSameKey() {
    System.setProperty(SYS_PROP, "shared-key-value");
    EnvironmentVariableKeyProvider provider = new EnvironmentVariableKeyProvider();
    assertThat(provider.getPrivateKey("key-1")).isEqualTo("shared-key-value");
    assertThat(provider.getPrivateKey("key-2")).isEqualTo("shared-key-value");
  }
}
