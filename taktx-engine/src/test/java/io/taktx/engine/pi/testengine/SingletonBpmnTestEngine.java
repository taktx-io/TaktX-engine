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
  private static boolean shutdownHookRegistered = false;

  private SingletonBpmnTestEngine() {}

  public static BpmnTestEngine getInstance() {
    if (instance == null) {
      instance = new BpmnTestEngine(ClockProducer.FIXED_CLOCK);
      instance.init();
      // Register the shutdown hook only once, not on every re-initialisation after a profile
      // switch.  The hook closes whatever instance is current at JVM exit.
      if (!shutdownHookRegistered) {
        shutdownHookRegistered = true;
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
    }
    return instance;
  }

  /**
   * Closes the singleton engine and nulls the reference so that the next call to {@link
   * #getInstance()} creates a fresh instance against the current Kafka broker.
   *
   * <p>Must be called before a Quarkus profile switch (and therefore a Kafka dev-services restart)
   * so that the existing consumer threads stop trying to reconnect to the old broker address.
   * {@link io.taktx.engine.pi.integration.SecurityTestConfigResource} calls this from its {@code
   * start()} method, which runs before the Quarkus application restarts for the security profile.
   */
  public static void closeIfRunning() {
    if (instance != null) {
      instance.close();
      instance = null;
    }
  }
}
