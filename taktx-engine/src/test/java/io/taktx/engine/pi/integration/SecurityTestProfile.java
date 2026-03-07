/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
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
    // Actual security config values (RSA key, Ed25519 key) are injected at runtime by
    // SecurityTestConfigResource.start() because they require key generation.
    // We only declare the feature flags here so the profile is distinguishable.
    return Map.of(
        "taktx.security.authorization.enabled", "true",
        "taktx.security.signing.enabled", "true");
  }

  @Override
  public String getConfigProfile() {
    return "security-test";
  }
}
