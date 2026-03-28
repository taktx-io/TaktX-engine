/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client;

/** Implementatios provide instances of classes based on the runtime environment */
public interface WorkerBeanInstanceProvider {

  /**
   * Get an instance of the specified class based on the runtime environment.
   *
   * @param clazz the class to get an instance of
   * @param <T> the type of the class
   * @return an instance of the specified class
   */
  <T> T getInstance(Class<T> clazz);
}
