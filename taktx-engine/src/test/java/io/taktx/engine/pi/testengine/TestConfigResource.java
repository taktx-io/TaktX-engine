/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.pi.testengine;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.util.Map;

public class TestConfigResource implements QuarkusTestResourceLifecycleManager {

  @Override
  public Map<String, String> start() {
    // Set the property before the application starts
    return Map.of(
        "taktx.test", "true",
        "kafka.devservices.auto-create-topics", "false");
  }

  @Override
  public void stop() {
    // Cleanup logic if needed
  }
}
