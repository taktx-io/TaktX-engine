/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.client.quarkus;

import io.taktx.client.TaktXClient;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Produces;
import org.mockito.Mockito;

/** Test-only producer that provides a no-op mock TaktXClient for Quarkus tests. */
@ApplicationScoped
public class TestTaktXClientProducer {

  @Produces
  @Alternative
  @Priority(1)
  public TaktXClient taktClient() {
    // Return a Mockito mock with default answers; no real consumers will be started.
    return Mockito.mock(TaktXClient.class);
  }
}
