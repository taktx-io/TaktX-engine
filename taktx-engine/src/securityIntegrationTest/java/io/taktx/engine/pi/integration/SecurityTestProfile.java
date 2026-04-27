/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.engine.pi.integration;

import io.quarkus.test.junit.QuarkusTestProfile;
import java.util.Map;

/**
 * Quarkus test profile for security integration tests.
 *
 * <p>Using a dedicated profile forces Quarkus to start a separate application instance for {@link
 * SecurityIntegrationTest}, so that enabling authorization and signing does not bleed into the
 * shared instance used by all other {@code @QuarkusTest} classes.
 */
public class SecurityTestProfile implements QuarkusTestProfile {

  @Override
  public Map<String, String> getConfigOverrides() {
    return Map.of();
  }

  @Override
  public String getConfigProfile() {
    return "security-test";
  }
}
