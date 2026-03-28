/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
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
