/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */

package io.taktx.engine.feel;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.camunda.feel.api.FeelEngineApi;
import org.camunda.feel.api.FeelEngineBuilder;

@ApplicationScoped
public class FeelEngineProvider {
  private FeelEngineApi feelEngineApi;

  @Produces
  @ApplicationScoped
  public synchronized FeelEngineApi getFeelEngineApi() {
    if (feelEngineApi == null) {
      feelEngineApi = FeelEngineBuilder.forJava().build();
    }
    return feelEngineApi;
  }
}
