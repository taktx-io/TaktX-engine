/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.client.quarkus;

import io.taktx.client.InstanceUpdateRecord;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.ObserverMethod;
import java.util.Set;

/**
 * Utility class to check for the presence of CDI observers for specific event types and qualifiers.
 */
@ApplicationScoped
public class InstanceUpdateRecordObserverChecker {

  private final BeanManager beanManager;

  /**
   * Constructor injecting the BeanManager.
   *
   * @param beanManager the CDI BeanManager
   */
  public InstanceUpdateRecordObserverChecker(BeanManager beanManager) {
    this.beanManager = beanManager;
  }

  /**
   * Check if there are any observers for the given event type and qualifiers.
   *
   * @return true if there are observers, false otherwise
   */
  public boolean hasInstanceUpdateRecordObservers() {
    InstanceUpdateRecord eventInstance = new InstanceUpdateRecord(0, null, null);
    Set<ObserverMethod<? super InstanceUpdateRecord>> observers =
        beanManager.resolveObserverMethods(eventInstance);
    return !observers.isEmpty();
  }
}
