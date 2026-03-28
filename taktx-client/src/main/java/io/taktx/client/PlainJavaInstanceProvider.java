/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
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
