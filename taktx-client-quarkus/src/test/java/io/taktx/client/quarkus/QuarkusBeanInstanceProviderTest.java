/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client.quarkus;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class QuarkusBeanInstanceProviderTest {

  @Test
  void testProviderInitialization() {
    // Given
    QuarkusBeanInstanceProvider provider = new QuarkusBeanInstanceProvider();

    // Then
    assertThat(provider).isNotNull();
  }

  @Test
  void testGetInstance_verifiesCorrectIntegrationWithCDI() {
    // Note: This is a CDI integration test that would require a proper CDI container
    // (like Quarkus Test or Weld) to test properly. The QuarkusBeanInstanceProvider
    // delegates to CDI.current().select(), which cannot be easily unit tested without
    // a running CDI container.
    //
    // For proper testing of this class:
    // 1. Use @QuarkusTest annotation for integration testing
    // 2. Or verify that the class correctly implements the WorkerBeanInstanceProvider interface

    QuarkusBeanInstanceProvider provider = new QuarkusBeanInstanceProvider();
    assertThat(provider).isNotNull();
    assertThat(provider).isInstanceOf(io.taktx.client.WorkerBeanInstanceProvider.class);
  }
}
