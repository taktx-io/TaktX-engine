/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.client.quarkus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.taktx.client.InstanceUpdateRecord;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.ObserverMethod;
import java.util.Collections;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InstanceUpdateRecordObserverCheckerTest {

  @Mock private BeanManager beanManager;

  private InstanceUpdateRecordObserverChecker checker;

  @BeforeEach
  void setUp() {
    checker = new InstanceUpdateRecordObserverChecker(beanManager);
  }

  @Test
  void testHasInstanceUpdateRecordObservers_withObservers() {
    // Given
    @SuppressWarnings("unchecked")
    ObserverMethod<? super InstanceUpdateRecord> observerMethod = mock(ObserverMethod.class);
    Set<ObserverMethod<? super InstanceUpdateRecord>> observers = Set.of(observerMethod);

    when(beanManager.resolveObserverMethods(any(InstanceUpdateRecord.class))).thenReturn(observers);

    // When
    boolean result = checker.hasInstanceUpdateRecordObservers();

    // Then
    assertThat(result).isTrue();
    verify(beanManager).resolveObserverMethods(any(InstanceUpdateRecord.class));
  }

  @Test
  void testHasInstanceUpdateRecordObservers_withoutObservers() {
    // Given
    Set<ObserverMethod<? super InstanceUpdateRecord>> observers = Collections.emptySet();
    when(beanManager.resolveObserverMethods(any(InstanceUpdateRecord.class))).thenReturn(observers);

    // When
    boolean result = checker.hasInstanceUpdateRecordObservers();

    // Then
    assertThat(result).isFalse();
    verify(beanManager).resolveObserverMethods(any(InstanceUpdateRecord.class));
  }

  @Test
  void testHasInstanceUpdateRecordObservers_withMultipleObservers() {
    // Given
    @SuppressWarnings("unchecked")
    ObserverMethod<? super InstanceUpdateRecord> observer1 = mock(ObserverMethod.class);
    @SuppressWarnings("unchecked")
    ObserverMethod<? super InstanceUpdateRecord> observer2 = mock(ObserverMethod.class);
    Set<ObserverMethod<? super InstanceUpdateRecord>> observers = Set.of(observer1, observer2);

    when(beanManager.resolveObserverMethods(any(InstanceUpdateRecord.class))).thenReturn(observers);

    // When
    boolean result = checker.hasInstanceUpdateRecordObservers();

    // Then
    assertThat(result).isTrue();
  }
}
