/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.client.spring;

import io.taktx.client.WorkerBeanInstanceProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/** Spring implementation of WorkerBeanInstanceProvider using ApplicationContext to obtain bean instances. */
@Component
public class SpringBeanInstanceProvider implements WorkerBeanInstanceProvider {

  private final ApplicationContext applicationContext;

  /**
   * Constructor injecting the ApplicationContext.
   *
   * @param applicationContext the Spring ApplicationContext
   */
  public SpringBeanInstanceProvider(ApplicationContext applicationContext) {
    this.applicationContext = applicationContext;
  }

  /**
   * Gets an instance of the specified class using Spring's ApplicationContext.
   *
   * @param clazz the class to get an instance of
   * @return an instance of the specified class
   */
  @Override
  public <T> T getInstance(Class<T> clazz) {
    return applicationContext.getBean(clazz);
  }
}

