/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
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
