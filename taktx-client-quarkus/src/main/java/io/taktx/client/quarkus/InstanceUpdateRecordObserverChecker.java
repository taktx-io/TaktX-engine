/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
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
    InstanceUpdateRecord eventInstance = new InstanceUpdateRecord(0, null, null, 0, 0);
    Set<ObserverMethod<? super InstanceUpdateRecord>> observers =
        beanManager.resolveObserverMethods(eventInstance);
    return !observers.isEmpty();
  }
}
