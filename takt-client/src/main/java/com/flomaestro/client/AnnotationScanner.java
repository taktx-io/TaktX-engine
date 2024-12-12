package com.flomaestro.client;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ScanResult;
import java.util.HashSet;
import java.util.Set;

public class AnnotationScanner {

  public static Set<Class<?>> findAnnotatedClasses(Class<?> annotation) {
    try (ScanResult scanResult = new ClassGraph().enableAllInfo().scan()) {
      return new HashSet<>(scanResult.getClassesWithAnnotation(annotation.getName()).loadClasses());
    }
  }
}
