package com.flomaestro.takt.xml;

import com.flomaestro.bpmn.TExtensionElements;
import java.util.Optional;

public class ExtensionElementHelper {
  private ExtensionElementHelper() {
    throw new UnsupportedOperationException("Utility class");
  }

  public static <T> Optional<T> extractExtensionElement(
      TExtensionElements extensionElements, Class<T> clazz) {
    return extensionElements.getAny().stream()
        .filter(clazz::isInstance)
        .findFirst()
        .map(clazz::cast);
  }
}
