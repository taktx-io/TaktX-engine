/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.client.spring;

import io.taktx.client.InstanceUpdateRecord;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Utility class to check for the presence of Spring event listeners for specific event types.
 */
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
   * Note: In Spring, we cannot easily check for listeners without reflection,
   * so we assume listeners exist if the eventPublisher is available.
   * Users should configure taktx.client.instanceupdate.enabled=false if they don't need this.
   *
   * @return true if event publisher is available
   */
  public boolean hasInstanceUpdateRecordListeners() {
    return eventPublisher != null;
  }

  /**
   * Publishes an InstanceUpdateRecord event.
   *
   * @param record the InstanceUpdateRecord to publish
   */
  public void publishInstanceUpdateRecord(InstanceUpdateRecord record) {
    if (eventPublisher != null) {
      eventPublisher.publishEvent(record);
    }
  }
}

