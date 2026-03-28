/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

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
