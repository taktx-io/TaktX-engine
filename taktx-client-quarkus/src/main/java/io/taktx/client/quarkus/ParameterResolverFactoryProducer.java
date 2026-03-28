/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
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
