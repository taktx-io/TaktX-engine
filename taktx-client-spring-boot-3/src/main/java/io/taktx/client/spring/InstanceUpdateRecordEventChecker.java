/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client.spring;

import io.taktx.client.InstanceUpdateRecord;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/** Utility class to check for the presence of Spring event listeners for specific event types. */
@Component
public class InstanceUpdateRecordEventChecker {

  private final ApplicationEventPublisher eventPublisher;

  /**
   * Constructor injecting the ApplicationEventPublisher.
   *
   * @param eventPublisher the Spring ApplicationEventPublisher
   */
  public InstanceUpdateRecordEventChecker(ApplicationEventPublisher eventPublisher) {
    this.eventPublisher = eventPublisher;
  }

  /**
   * Check if there are any listeners for InstanceUpdateRecord events.
   *
   * <p>Note: In Spring, we cannot easily check for listeners without reflection, so we assume
   * listeners exist if the eventPublisher is available. Users should configure
   * taktx.client.instanceupdate.enabled=false if they don't need this.
   *
   * @return true if event publisher is available
   */
  public boolean hasInstanceUpdateRecordListeners() {
    return eventPublisher != null;
  }

  /**
   * Publishes an InstanceUpdateRecord event.
   *
   * @param instanceUpdateRecord the InstanceUpdateRecord to publish
   */
  public void publishInstanceUpdateRecord(InstanceUpdateRecord instanceUpdateRecord) {
    if (eventPublisher != null) {
      eventPublisher.publishEvent(instanceUpdateRecord);
    }
  }
}
