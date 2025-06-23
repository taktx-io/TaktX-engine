/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.client;

import jakarta.enterprise.inject.spi.CDI;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class InstanceProvider {

  private static Environment environment;

  static {
    // Detect environment at runtime
    try {
      Class.forName("io.quarkus.runtime.Quarkus");
      environment = Environment.QUARKUS;
    } catch (ClassNotFoundException e1) {
      try {
        Class.forName("org.springframework.context.ApplicationContext");
        environment = Environment.SPRING;
      } catch (ClassNotFoundException e2) {
        environment = Environment.PLAIN_JAVA;
      }
    }
  }

  public static <T> T getInstance(Class<T> clazz) {
    return switch (environment) {
      case QUARKUS -> getQuarkusBean(clazz);
      case SPRING -> getSpringBean(clazz);
      default -> createNewInstance(clazz);
    };
  }

  private static <T> T getQuarkusBean(Class<T> clazz) {
    try {
      // Check if CDI is available before trying to use it
      log.info("Getting quarkus bean for {}", clazz.getName());
      return CDI.current().select(clazz).get();
    } catch (IllegalStateException e) {
      // CDI container is not available yet - fallback to creating a new instance
      log.warn(
          "CDI container returning error {} for {}. Creating new instance instead.",
          e.getMessage(),
          clazz.getName());
      return createNewInstance(clazz);
    }
  }

  private static <T> T getSpringBean(Class<T> clazz) {
    try {
      // Spring context should be statically available in the app
      Class<?> contextHolderClass =
          Class.forName("org.springframework.context.ApplicationContextHolder");
      Object applicationContext =
          contextHolderClass.getMethod("getApplicationContext").invoke(null);
      return clazz.cast(
          applicationContext
              .getClass()
              .getMethod("getBean", Class.class)
              .invoke(applicationContext, clazz));
    } catch (Exception e) {
      throw new IllegalStateException("Failed to get Spring bean for " + clazz, e);
    }
  }

  private static <T> T createNewInstance(Class<T> clazz) {
    try {
      return clazz.getDeclaredConstructor().newInstance();
    } catch (Exception e) {
      throw new IllegalStateException("Failed to create a new instance of " + clazz, e);
    }
  }

  // Enum to identify the environment
  public enum Environment {
    QUARKUS,
    SPRING,
    PLAIN_JAVA
  }
}
