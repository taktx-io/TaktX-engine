/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */
package io.taktx.engine.security;

import io.taktx.security.KeyTrustPolicy;
import io.taktx.security.OpenKeyTrustPolicy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

/**
 * CDI producer for the engine's key trust policy.
 *
 * <p>Currently always returns {@link OpenKeyTrustPolicy}. In the future, this can select an {@code
 * AnchoredKeyTrustPolicy} when {@code TAKTX_PLATFORM_PUBLIC_KEY} is configured.
 */
@ApplicationScoped
public class KeyTrustPolicyProducer {

  @Produces
  @ApplicationScoped
  public KeyTrustPolicy keyTrustPolicy() {
    return new OpenKeyTrustPolicy();
  }
}
