/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client.spring;

import io.taktx.client.DefaultParameterResolverFactory;
import io.taktx.client.ParameterResolverFactory;
import io.taktx.client.ProcessInstanceResponder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Provides a default ParameterResolverFactory bean for Spring applications. */
@Configuration
public class ParameterResolverFactoryConfiguration {

  /**
   * Provides a default ParameterResolverFactory bean.
   *
   * @param processInstanceResponder the ProcessInstanceResponder to be used
   * @return a DefaultParameterResolverFactory instance
   */
  @Bean
  @ConditionalOnMissingBean
  public ParameterResolverFactory parameterResolverFactory(
      ProcessInstanceResponder processInstanceResponder) {
    return new DefaultParameterResolverFactory(processInstanceResponder);
  }
}
