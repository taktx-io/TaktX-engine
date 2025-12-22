/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.client.spring;

import io.taktx.client.DefaultResultProcessorFactory;
import io.taktx.client.ResultProcessorFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Provides a default ResultProcessorFactory bean for Spring applications. */
@Configuration
public class ResultProcessorFactoryConfiguration {

  /**
   * Provides a default ResultProcessorFactory bean.
   *
   * @return a DefaultResultProcessorFactory instance
   */
  @Bean
  @ConditionalOnMissingBean
  public ResultProcessorFactory resultProcessorFactory() {
    return new DefaultResultProcessorFactory();
  }
}

