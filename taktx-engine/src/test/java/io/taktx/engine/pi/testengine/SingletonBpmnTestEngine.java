/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.pi.testengine;

import io.taktx.engine.generic.ClockProducer;

public class SingletonBpmnTestEngine {
  private static BpmnTestEngine instance;

  private SingletonBpmnTestEngine() {
    // Initialize your resource here
  }

  public static BpmnTestEngine getInstance() {
    if (instance == null) {
      instance = new BpmnTestEngine(ClockProducer.FIXED_CLOCK);
      instance.init();
    }
    return instance;
  }

  // Add methods to access the resource
}
