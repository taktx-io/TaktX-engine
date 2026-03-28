/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ScanResult;
import io.taktx.client.annotation.Deployment;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Utility class for scanning classes and methods annotated with specific annotations using
 * ClassGraph.
 */
public class AnnotationScanner {

  /** Private constructor to prevent instantiation */
  private AnnotationScanner() {
    // Static helper class
  }

  /**
   * Finds all classes that have at least one method annotated with the specified annotation.
   *
   * @param annotation The annotation class to search for on methods
   * @return A set of classes containing methods annotated with the specified annotation
   */
  public static Set<Class<?>> findClassesWithAnnotatedMethods(Class<?> annotation) {
    try (ScanResult scanResult = new ClassGraph().enableAllInfo().scan()) {
      return scanResult.getClassesWithMethodAnnotation(annotation.getName()).loadClasses().stream()
          .filter(c -> !isProxyClass(c))
          .collect(Collectors.toSet());
    }
  }

  /**
   * Finds all classes annotated with the @TaktDeployment annotation, excluding proxy classes.
   *
   * @return A set of TaktDeployment annotations found on classes
   */
  public static Set<Deployment> findTaktDeployments() {
    try (ScanResult scanResult = new ClassGraph().enableAllInfo().scan()) {
      return scanResult.getClassesWithAnnotation(Deployment.class.getName()).loadClasses().stream()
          .filter(c -> !isProxyClass(c))
          .map(c -> c.getAnnotation(Deployment.class))
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
