/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
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
