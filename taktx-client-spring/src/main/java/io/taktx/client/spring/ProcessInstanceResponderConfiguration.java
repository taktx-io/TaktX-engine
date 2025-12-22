/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.client.spring;

import io.taktx.client.ProcessInstanceResponder;
import io.taktx.util.TaktPropertiesHelper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Provides a default ProcessInstanceResponder bean for Spring applications. */
@Configuration
public class ProcessInstanceResponderConfiguration {

  /**
   * Provides a default ProcessInstanceResponder bean.
   *
   * @param taktPropertiesHelper the TaktPropertiesHelper to be used
   * @return a ProcessInstanceResponder instance
   */
  @Bean
  @ConditionalOnMissingBean
  public ProcessInstanceResponder processInstanceResponder(
      TaktPropertiesHelper taktPropertiesHelper) {
    return new ProcessInstanceResponder(taktPropertiesHelper);
  }
}

