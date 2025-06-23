/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.client;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ScanResult;
import io.taktx.client.annotation.TaktDeployment;
import java.util.Set;
import java.util.stream.Collectors;

public class AnnotationScanner {

  private AnnotationScanner() {
    // Static helper class
  }

  public static Set<Class<?>> findClassesWithAnnotatedMethods(Class<?> annotation) {
    try (ScanResult scanResult = new ClassGraph().enableAllInfo().scan()) {
      return scanResult.getClassesWithMethodAnnotation(annotation.getName()).loadClasses().stream()
          .filter(c -> !isProxyClass(c))
          .collect(Collectors.toSet());
    }
  }

  public static Set<TaktDeployment> findTaktDeployments() {
    try (ScanResult scanResult = new ClassGraph().enableAllInfo().scan()) {
      return scanResult
          .getClassesWithAnnotation(TaktDeployment.class.getName())
          .loadClasses()
          .stream()
          .filter(c -> !isProxyClass(c))
          .map(c -> c.getAnnotation(TaktDeployment.class))
          .collect(Collectors.toSet());
    }
  }

  /**
   * Determines if a class is a proxy class by checking various indicators.
   *
   * @param clazz The class to check
   * @return true if the class is a proxy, false otherwise
   */
  private static boolean isProxyClass(Class<?> clazz) {
    // Check if it's a JDK dynamic proxy
    if (java.lang.reflect.Proxy.isProxyClass(clazz)) {
      return true;
    }

    // Check for common proxy indicators
    if (clazz.isSynthetic()) {
      return true;
    }

    // Check for proxy-specific interfaces (used by various frameworks)
    for (Class<?> iface : clazz.getInterfaces()) {
      String ifaceName = iface.getName();
      if (ifaceName.contains("EnhancerBySpringCGLIB")
          || ifaceName.contains("ByteBuddy")
          || ifaceName.contains("javassist")
          || ifaceName.contains("$$")) {
        return true;
      }
    }

    // Check for Quarkus/ArC specific proxy indicators
    String className = clazz.getName();
    return className.contains("$$")
        || className.endsWith("_ClientProxy")
        || className.endsWith("_Subclass")
        || className.contains("_HibernateProxy_");
  }
}
