/*
 *
 *  * TaktX - A high-performance BPMN engine
 *  * Copyright (c) 2025 TaktX B.V. All rights reserved.
 *  * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 *  * Free use is permitted with up to 3 Kafka partitions. See LICENSE file for details.
 *  * For commercial use or more partitions and features, contact [info@taktx.io] or [https://www.taktx.io/contact].
 *
 */

package com.flomaestro.engine.feel;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.camunda.feel.api.FeelEngineApi;
import org.camunda.feel.api.FeelEngineBuilder;

@ApplicationScoped
public class FeelEngineProvider {
  public static final FeelEngineApi FEEL_ENGINE_API = FeelEngineBuilder.forJava().build();

  @Produces
  public FeelEngineApi getFeelEngineApi() {
    return FEEL_ENGINE_API;
  }
}
