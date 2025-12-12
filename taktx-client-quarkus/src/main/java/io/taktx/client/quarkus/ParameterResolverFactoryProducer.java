/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.client.quarkus;

import io.quarkus.arc.DefaultBean;
import io.taktx.client.DefaultParameterResolverFactory;
import io.taktx.client.ParameterResolverFactory;
import io.taktx.client.ProcessInstanceResponder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

/** Produces a default ParameterResolverFactory bean for Quarkus applications. */
@ApplicationScoped
public class ParameterResolverFactoryProducer {

  private final ProcessInstanceResponder processInstanceResponder;

  /**
   * Constructor injecting the ProcessInstanceResponder.
   *
   * @param processInstanceResponder the ProcessInstanceResponder to be used
   */
  public ParameterResolverFactoryProducer(ProcessInstanceResponder processInstanceResponder) {
    this.processInstanceResponder = processInstanceResponder;
  }

  /**
   * Produces a default ParameterResolverFactory bean.
   *
   * @return a DefaultParameterResolverFactory instance
   */
  @Produces
  @ApplicationScoped
  @DefaultBean
  public ParameterResolverFactory produceParameterResolverFactory() {
    return new DefaultParameterResolverFactory(processInstanceResponder);
  }
}
