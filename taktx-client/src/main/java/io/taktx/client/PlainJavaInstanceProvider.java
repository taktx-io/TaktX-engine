/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.client;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A utility class to provide instances of classes based on the runtime environment (Quarkus,
 * Spring, or plain Java).
 */
public class PlainJavaInstanceProvider implements WorkerBeanInstanceProvider {
  private final Map<Class<?>, Object> instanceCache = new ConcurrentHashMap<>();

  @Override
  public <T> T getInstance(Class<T> clazz) {
    try {
      T instance = (T) instanceCache.get(clazz);
      if (instance == null) {
        instance = clazz.getDeclaredConstructor().newInstance();
        instanceCache.put(clazz, instance);
      }
      return instance;
    } catch (Exception e) {
      throw new IllegalStateException("Failed to create a new instance of " + clazz, e);
    }
  }
}
