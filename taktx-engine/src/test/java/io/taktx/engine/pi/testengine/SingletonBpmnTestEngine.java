/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
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
