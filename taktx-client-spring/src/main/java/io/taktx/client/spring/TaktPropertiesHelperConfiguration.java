/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.client.spring;

import io.taktx.util.TaktPropertiesHelper;
import java.util.Properties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;

/** Provides a TaktPropertiesHelper bean for Spring applications. */
@Configuration
public class TaktPropertiesHelperConfiguration {

  /**
   * Provides a TaktPropertiesHelper bean from Spring Environment.
   *
   * @param environment the Spring Environment
   * @return a TaktPropertiesHelper instance
   */
  @Bean
  @ConditionalOnMissingBean
  public TaktPropertiesHelper taktPropertiesHelper(ConfigurableEnvironment environment) {
    Properties properties = new Properties();

    // Extract all properties from Spring Environment
    for (PropertySource<?> propertySource : environment.getPropertySources()) {
      if (propertySource instanceof EnumerablePropertySource) {
        EnumerablePropertySource<?> enumerablePropertySource =
            (EnumerablePropertySource<?>) propertySource;
        for (String name : enumerablePropertySource.getPropertyNames()) {
          Object value = environment.getProperty(name);
          if (value != null) {
            properties.setProperty(name, value.toString());
          }
        }
      }
    }

    return new TaktPropertiesHelper(properties);
  }
}
