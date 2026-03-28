/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
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
