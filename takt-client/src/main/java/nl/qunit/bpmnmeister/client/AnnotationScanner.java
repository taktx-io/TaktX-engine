package nl.qunit.bpmnmeister.client;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ScanResult;
import java.util.Set;
import java.util.stream.Collectors;

public class AnnotationScanner {

  public static Set<Class<?>> findAnnotatedClasses(Class<?> annotation) {
    try (ScanResult scanResult = new ClassGraph().enableAllInfo().scan()) {
      return scanResult.getClassesWithAnnotation(annotation.getName()).loadClasses().stream()
          .collect(Collectors.toSet());
    }
  }
}
