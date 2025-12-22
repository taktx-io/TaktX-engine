/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.client.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.taktx.client.InstanceUpdateRecord;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class InstanceUpdateRecordEventCheckerTest {

  private ApplicationEventPublisher eventPublisher;
  private InstanceUpdateRecordEventChecker checker;

  @BeforeEach
  void setUp() {
    eventPublisher = mock(ApplicationEventPublisher.class);
    checker = new InstanceUpdateRecordEventChecker(eventPublisher);
  }

  @Test
  void testHasInstanceUpdateRecordListeners_withPublisher() {
    // When
    boolean result = checker.hasInstanceUpdateRecordListeners();

    // Then
    assertThat(result).isTrue();
  }

  @Test
  void testHasInstanceUpdateRecordListeners_withoutPublisher() {
    // Given
    checker = new InstanceUpdateRecordEventChecker(null);

    // When
    boolean result = checker.hasInstanceUpdateRecordListeners();

    // Then
    assertThat(result).isFalse();
  }

  @Test
  void testPublishInstanceUpdateRecord() {
    // Given
    InstanceUpdateRecord record = new InstanceUpdateRecord(100L, UUID.randomUUID(), null, 1, 200L);

    // When
    checker.publishInstanceUpdateRecord(record);

    // Then
    verify(eventPublisher).publishEvent(record);
  }

  @Test
  void testPublishInstanceUpdateRecord_withoutPublisher() {
    // Given
    checker = new InstanceUpdateRecordEventChecker(null);
    InstanceUpdateRecord record = new InstanceUpdateRecord(100L, UUID.randomUUID(), null, 1, 200L);

    // When/Then - should not throw exception
    checker.publishInstanceUpdateRecord(record);
  }
}

