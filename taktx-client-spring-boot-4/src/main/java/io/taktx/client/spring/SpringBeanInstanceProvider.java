/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client.spring;

import io.taktx.client.WorkerBeanInstanceProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * Spring implementation of WorkerBeanInstanceProvider using ApplicationContext to obtain bean
 * instances.
 */
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
