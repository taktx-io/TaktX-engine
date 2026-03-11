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

  private SingletonBpmnTestEngine() {}

  public static BpmnTestEngine getInstance() {
    if (instance == null) {
      instance = new BpmnTestEngine(ClockProducer.FIXED_CLOCK);
      instance.init();
      // Ensure all Kafka consumers are closed when the JVM shuts down after tests complete.
      // Without this the consumer threads keep trying to reconnect to the (now-stopped)
      // dev-services broker, preventing the Gradle worker process from terminating.
      Runtime.getRuntime()
          .addShutdownHook(
              new Thread(
                  () -> {
                    if (instance != null) {
                      instance.close();
                      instance = null;
                    }
                  },
                  "singleton-bpmn-engine-shutdown"));
    }
    return instance;
  }
}
