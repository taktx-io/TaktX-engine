/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.client.quarkus;

import io.quarkus.arc.DefaultBean;
import io.taktx.client.DefaultResultProcessorFactory;
import io.taktx.client.ResultProcessorFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

/** Produces a default ResultProcessorFactory bean for Quarkus applications. */
@ApplicationScoped
public class ResultProcessorFactoryProducer {
  /**
   * Produces a default ResultProcessorFactory bean.
   *
   * @return a DefaultResultProcessorFactory instance
   */
  @Produces
  @ApplicationScoped
  @DefaultBean
  public ResultProcessorFactory resultProcessorFactory() {
    return new DefaultResultProcessorFactory();
  }
}
