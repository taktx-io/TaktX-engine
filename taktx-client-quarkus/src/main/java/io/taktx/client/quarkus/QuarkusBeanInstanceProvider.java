/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client.quarkus;

import io.taktx.client.WorkerBeanInstanceProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.CDI;

/** Quarkus implementation of WorkerBeanInstanceProvider using CDI to obtain bean instances. */
@ApplicationScoped
public class QuarkusBeanInstanceProvider implements WorkerBeanInstanceProvider {

  /**
   * Gets an instance of the specified class using CDI.
   *
   * @param clazz the class to get an instance of
   * @return an instance of the specified class
   */
  @Override
  public <T> T getInstance(Class<T> clazz) {
    Instance<T> select = CDI.current().select(clazz);
    return select.get();
  }
}
